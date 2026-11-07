import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../features/security_tools.dart';
import 'security_tools_screen.dart';

/// Private Cloud Run demo: short-lived IAM token entered for this session only.
/// No Google API key or service-account credential is shipped in the app.
class TriageScreen extends StatefulWidget {
  final SecurityJournal journal;
  final bool ios;
  final String initialUrl;
  final Future<Map<String, dynamic>> Function(Map<String, dynamic>)? analyze;
  const TriageScreen({
    super.key,
    required this.journal,
    required this.ios,
    this.initialUrl = '',
    this.analyze,
  });
  @override
  State<TriageScreen> createState() => _TriageScreenState();
}

class _TriageScreenState extends State<TriageScreen> {
  final _message = TextEditingController();
  final _url = TextEditingController();
  final _endpoint = TextEditingController(
    text: const String.fromEnvironment('DHALAM_API_URL'),
  );
  final _token = TextEditingController();
  final _picker = ImagePicker();
  Uint8List? _image;
  String? _mime;
  bool _consent = false, _busy = false;
  String? _error, _submittedMessage, _submittedUrl;
  Map<String, dynamic>? _result;
  HttpClient? _client;

  @override
  void initState() {
    super.initState();
    _url.text = widget.initialUrl;
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      _recoverImage();
    }
  }

  Future<void> _recoverImage() async {
    try {
      final data = await _picker.retrieveLostData();
      if (data.files?.isNotEmpty == true) await _readImage(data.files!.first);
      if (data.exception != null && mounted) {
        setState(() => _error = 'Please select the screenshot again.');
      }
    } catch (_) {
      /* No pending picker result. */
    }
  }

  @override
  void dispose() {
    _client?.close(force: true);
    for (final controller in [_message, _url, _endpoint, _token]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _readImage(XFile file) async {
    if (await file.length() > 4 * 1024 * 1024) {
      throw const FormatException('Use an image under 4 MiB.');
    }
    final bytes = await file.readAsBytes();
    final mime = bytes.length > 3 && bytes[0] == 0xff && bytes[1] == 0xd8
        ? 'image/jpeg'
        : bytes.length > 8 && bytes[0] == 137 && bytes[1] == 80
        ? 'image/png'
        : bytes.length > 12 &&
              ascii.decode(bytes.sublist(8, 12), allowInvalid: true) == 'WEBP'
        ? 'image/webp'
        : null;
    if (mime == null) {
      throw const FormatException('Choose a JPEG, PNG or WebP screenshot.');
    }
    if (mounted) {
      setState(() {
        _image = bytes;
        _mime = mime;
        _consent = false;
        _result = null;
      });
    }
  }

  Future<void> _pick(ImageSource source) async {
    try {
      final file = await _picker.pickImage(
        source: source,
        maxWidth: 2048,
        maxHeight: 2048,
        imageQuality: 90,
        requestFullMetadata: false,
      );
      if (file != null) await _readImage(file);
    } catch (_) {
      if (mounted) {
        setState(
          () => _error =
              'Could not load the image. Choose a JPEG, PNG or WebP under 4 MiB.',
        );
      }
    }
  }

  void _edited(String _) => setState(() {
    _result = null;
    _consent = false;
  });

  Future<Map<String, dynamic>> _request(Map<String, dynamic> payload) async {
    final base = Uri.tryParse(_endpoint.text.trim());
    if (base == null ||
        base.host.isEmpty ||
        base.userInfo.isNotEmpty ||
        base.hasQuery ||
        base.hasFragment ||
        (base.scheme != 'https' &&
            !(kDebugMode &&
                base.scheme == 'http' &&
                ['localhost', '127.0.0.1', '10.0.2.2'].contains(base.host)))) {
      throw const FormatException(
        'Configure an HTTPS backend URL. Local HTTP is allowed only in debug builds.',
      );
    }
    _client = HttpClient()..connectionTimeout = const Duration(seconds: 10);
    try {
      final request = await _client!.postUrl(
        base.resolve('/api/v1/analyze-threat'),
      );
      request.followRedirects = false;
      request.headers.contentType = ContentType.json;
      if (_token.text.trim().isNotEmpty) {
        request.headers.set(
          HttpHeaders.authorizationHeader,
          'Bearer ${_token.text.trim()}',
        );
      }
      request.write(jsonEncode(payload));
      final response = await request.close().timeout(
        const Duration(seconds: 75),
      );
      if ([401, 403].contains(response.statusCode)) {
        throw const FormatException(
          'Backend access expired or denied. Update the demo access token.',
        );
      }
      if (response.statusCode != 200) {
        throw const FormatException(
          'Analysis unavailable. Check the input and retry later.',
        );
      }
      final bytes = <int>[];
      await for (final chunk in response.timeout(const Duration(seconds: 15))) {
        bytes.addAll(chunk);
        if (bytes.length > 100000) {
          throw const FormatException('Unexpected server response.');
        }
      }
      final result = jsonDecode(utf8.decode(bytes)) as Map<String, dynamic>;
      if (result['summary'] is! String ||
          result['scam_probability'] is! int ||
          result['recommended_actions'] is! List) {
        throw const FormatException('Incomplete assessment. Please retry.');
      }
      return result;
    } finally {
      _client?.close(force: true);
      _client = null;
    }
  }

  Future<void> _analyze() async {
    if (!_consent || _busy) return;
    if (_message.text.trim().isEmpty &&
        _url.text.trim().isEmpty &&
        _image == null) {
      setState(() => _error = 'Add a message, link or screenshot first.');
      return;
    }
    final message = _message.text.trim(), url = _url.text.trim();
    setState(() {
      _busy = true;
      _error = null;
      _result = null;
    });
    try {
      final result = await (widget.analyze ?? _request)({
        'consent': true,
        if (message.isNotEmpty) 'text': message,
        if (url.isNotEmpty) 'url': url,
        if (_image != null) 'image_base64': base64Encode(_image!),
        if (_mime != null) 'image_mime_type': _mime,
      });
      if (mounted) {
        setState(() {
          _result = result;
          _submittedMessage = message;
          _submittedUrl = url;
        });
      }
    } on FormatException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) {
        setState(
          () => _error =
              'Could not reach cloud analysis. Check your connection and try again. Local device checks still work.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _report() async {
    final result = _result;
    if (result == null) return;
    final report = incidentReport(
      result,
      _submittedMessage ?? '',
      _submittedUrl ?? '',
      widget.journal.report(widget.ios),
    );
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Review incident report'),
        content: SizedBox(
          width: 600,
          child: SingleChildScrollView(child: SelectableText(report)),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
          TextButton(
            onPressed: () async {
              try {
                await toolsChannel.invokeMethod<void>('shareReport', report);
              } catch (_) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text(
                        'Sharing unavailable. You can copy the report text.',
                      ),
                    ),
                  );
                }
              }
            },
            child: const Text('Share report'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final result = _result;
    return Scaffold(
      appBar: AppBar(title: const Text('Ask Dhalam')),
      body: SafeArea(
        child: Column(
          children: [
            const CaptureWarning(),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(20),
                children: [
                  Text(
                    'Pause. Check the request.',
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const Text(
                    'Paste a suspicious message, link or screenshot. AI can identify warning signs, but cannot verify a sender or guarantee safety.',
                  ),
                  ExpansionTile(
                    title: const Text('Demo connection'),
                    children: [
                      TextField(
                        controller: _endpoint,
                        enabled: !_busy,
                        decoration: const InputDecoration(
                          labelText: 'Backend HTTPS URL',
                        ),
                      ),
                      TextField(
                        controller: _token,
                        enabled: !_busy,
                        obscureText: true,
                        enableSuggestions: false,
                        autocorrect: false,
                        decoration: const InputDecoration(
                          labelText: 'Short-lived Cloud Run access token',
                        ),
                      ),
                      const Text(
                        'Connection token stays in memory for this screen. Do not enter a Google API key here.',
                      ),
                    ],
                  ),
                  TextField(
                    controller: _message,
                    enabled: !_busy,
                    onChanged: _edited,
                    maxLines: 5,
                    maxLength: 12000,
                    decoration: const InputDecoration(
                      labelText: 'Suspicious message or situation',
                      hintText: 'What is someone asking you to do?',
                    ),
                  ),
                  TextField(
                    controller: _url,
                    enabled: !_busy,
                    onChanged: _edited,
                    maxLength: 4096,
                    decoration: const InputDecoration(
                      labelText: 'Suspicious link (optional)',
                      hintText: 'https://…',
                    ),
                  ),
                  Wrap(
                    spacing: 12,
                    children: [
                      OutlinedButton.icon(
                        onPressed: _busy
                            ? null
                            : () => _pick(ImageSource.gallery),
                        icon: const Icon(Icons.image_outlined),
                        label: const Text('Choose screenshot'),
                      ),
                      OutlinedButton.icon(
                        onPressed: _busy
                            ? null
                            : () => _pick(ImageSource.camera),
                        icon: const Icon(Icons.camera_alt_outlined),
                        label: const Text('Take photo'),
                      ),
                    ],
                  ),
                  if (_image != null) ...[
                    Image.memory(
                      _image!,
                      height: 180,
                      errorBuilder: (_, error, stack) =>
                          const Text('Image preview unavailable'),
                    ),
                    TextButton(
                      onPressed: _busy
                          ? null
                          : () => setState(() {
                              _image = null;
                              _mime = null;
                              _result = null;
                              _consent = false;
                            }),
                      child: const Text('Remove image'),
                    ),
                  ],
                  const Text(
                    'Remove passwords, OTPs, account numbers and personal details first. Only these inputs go to the backend and Google for analysis. A link may also be checked with Google Safe Browsing. Device inventories stay on your phone. Dhalam does not save submissions on its server; Google service retention policies apply.',
                  ),
                  CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    value: _consent,
                    onChanged: _busy
                        ? null
                        : (value) => setState(() => _consent = value ?? false),
                    title: const Text(
                      'I consent to sending this submission for cloud analysis.',
                    ),
                  ),
                  FilledButton.icon(
                    onPressed: _consent && !_busy ? _analyze : null,
                    icon: const Icon(Icons.search),
                    label: Text(_busy ? 'Analyzing…' : 'Analyze submission'),
                  ),
                  if (_busy) const LinearProgressIndicator(),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      child: Text(
                        _error!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ),
                  if (result != null)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '${result['threat_level']} · ${result['scam_category']}',
                              style: Theme.of(context).textTheme.titleLarge,
                            ),
                            Text(
                              'AI concern score: ${result['scam_probability']}/100 — not a measured probability',
                            ),
                            Text(result['summary'] as String),
                            for (final section in {
                              'coercion_markers': 'Warning signs',
                              'evidence_extracted': 'Submitted evidence',
                              'recommended_actions': 'Next steps',
                            }.entries) ...[
                              const SizedBox(height: 12),
                              Text(
                                section.value,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              for (final item
                                  in (result[section.key] as List? ?? []))
                                Text('• $item'),
                            ],
                            const SizedBox(height: 12),
                            Text(
                              reputationLabel(
                                result['url_reputation'] as String?,
                              ),
                            ),
                            Text(
                              result['limitations'] as String? ??
                                  'AI can make mistakes; verify independently.',
                            ),
                            Text(
                              'Model: ${result['model']} · ${result['analyzed_at']}',
                            ),
                            OutlinedButton.icon(
                              onPressed: _report,
                              icon: const Icon(Icons.description_outlined),
                              label: const Text('Preview incident report'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  const SizedBox(height: 16),
                  const Text(
                    'If you suspect financial fraud, contact your bank promptly and report through 1930 or cybercrime.gov.in. Dhalam does not file a complaint for you.',
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

String reputationLabel(String? status) => switch (status) {
  'matched' => 'Google Safe Browsing flagged this URL. Avoid proceeding.',
  'no_match' => 'No Safe Browsing match. This does not mean the link is safe.',
  'unavailable' => 'URL reputation lookup unavailable; no reputation verdict.',
  'not_configured' =>
    'URL reputation is not configured. AI reviewed the URL text only.',
  _ => 'No URL reputation lookup requested.',
};

String incidentReport(
  Map<String, dynamic> result,
  String message,
  String url,
  String observations,
) =>
    '''DHALAM — PERSONAL CYBERCRIME INCIDENT NOTES
Prepared: ${DateTime.now().toUtc().toIso8601String()}
This is a user-reviewed draft, not an official complaint, forensic certification or proof of fraud.

INCIDENT DETAILS — fill in before submitting
Incident date/time and timezone:
Contact method / sender identifier:
Amount lost (if any):
Bank / transaction reference (never include PINs, passwords or OTPs):
Actions already taken:

USER-SUBMITTED CONTENT
Message: $message
URL: $url
Original screenshot: attach separately if relevant. Dhalam does not embed it in this report.

AI ASSESSMENT — may be incorrect; observations below are separate
Model: ${result['model']}
Analyzed: ${result['analyzed_at']}
Concern: ${result['threat_level']} (${result['scam_probability']}/100, uncalibrated)
${result['summary']}
Indicators: ${(result['coercion_markers'] as List? ?? []).join('; ')}
Extracted content: ${(result['evidence_extracted'] as List? ?? []).join('; ')}
Suggested actions: ${(result['recommended_actions'] as List? ?? []).join('; ')}
${reputationLabel(result['url_reputation'] as String?)}
${result['limitations'] ?? ''}

LOCAL DEVICE OBSERVATIONS
$observations

REPORTING
For suspected financial cyber fraud contact your bank and 1930 / https://cybercrime.gov.in.
Preserve original messages, transaction references and screenshots. Review and redact this draft before sharing. No complaint has been filed by Dhalam.
''';
