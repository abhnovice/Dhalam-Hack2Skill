import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import 'package:image_picker/image_picker.dart';
import '../features/security_tools.dart';
import '../l10n/app_language.dart';
import '../l10n/chat_language.dart';
import '../services/voice_service.dart';
import 'security_tools_screen.dart';
import 'dashboard_screen.dart';
import 'chat_link_preview.dart';

/// Private Cloud Run demo: short-lived IAM token entered for this session only.
/// No Google API key or service-account credential is shipped in the app.
class TriageScreen extends StatefulWidget {
  final SecurityJournal journal;
  final bool ios;
  final String initialUrl;
  final Future<Map<String, dynamic>> Function(Map<String, dynamic>)? analyze;

  /// Replaceable so tests never reach the speech and text-to-speech plugins.
  final VoiceService? voice;
  const TriageScreen({
    super.key,
    required this.journal,
    required this.ios,
    this.initialUrl = '',
    this.analyze,
    this.voice,
  });
  @override
  State<TriageScreen> createState() => _TriageScreenState();
}

class _TriageScreenState extends State<TriageScreen> with LanguageListener {
  final _message = TextEditingController();
  final _url = TextEditingController();
  final _endpoint = TextEditingController(
    text: const String.fromEnvironment('DHALAM_API_URL'),
  );
  final _token = TextEditingController();
  final _picker = ImagePicker();
  Uint8List? _image;
  String? _mime;
  bool _busy = false;
  final _turns = <Map<String, dynamic>>[];
  final _scroll = ScrollController();
  String? _error;
  HttpClient? _client;
  VoiceService? _voiceService;
  bool _listening = false;
  Map<String, dynamic>? _speaking;
  String _dictationBase = '';

  VoiceService get _voice =>
      _voiceService ??= widget.voice ?? DeviceVoiceService();
  ChatLanguage get _language => appLanguage.value;
  ChatStrings get _t => _language.strings;

  @override
  void initState() {
    super.initState();
    _url.text = widget.initialUrl;
    _message.text = widget.initialUrl;
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      if (kDebugMode && _endpoint.text.isEmpty) {
        // adb reverse tcp:18765 tcp:18765 connects to the loopback-only backend.
        _endpoint.text = 'http://127.0.0.1:18765';
      }
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

  void _voiceUnavailable() {
    if (!mounted) return;
    setState(() {
      _listening = false;
      _speaking = null;
      _error = _t.voiceUnavailable;
    });
    _toLatest();
  }

  Future<void> _toggleListening() async {
    if (_listening) {
      await _voice.stopListening();
      if (mounted) setState(() => _listening = false);
      return;
    }
    await _stopSpeaking();
    _dictationBase = _message.text.trim();
    setState(() {
      _listening = true;
      _error = null;
    });
    try {
      await _voice.startListening(
        locale: _language.locale,
        onResult: (words) {
          if (!mounted) return;
          _message.text = _dictationBase.isEmpty
              ? words
              : '$_dictationBase $words';
          _message.selection = TextSelection.collapsed(
            offset: _message.text.length,
          );
          setState(() {});
        },
        onDone: () {
          if (mounted && _listening) setState(() => _listening = false);
        },
      );
    } on VoiceUnavailableException {
      _voiceUnavailable();
    } catch (_) {
      _voiceUnavailable();
    }
  }

  Future<void> _stopSpeaking() async {
    if (_speaking == null) return;
    setState(() => _speaking = null);
    try {
      await _voice.stopSpeaking();
    } catch (_) {
      /* Nothing is playing. */
    }
  }

  Future<void> _toggleSpeaking(Map<String, dynamic> turn) async {
    if (identical(_speaking, turn)) return _stopSpeaking();
    if (_listening) {
      await _voice.stopListening();
      if (mounted) setState(() => _listening = false);
    }
    await _stopSpeaking();
    if (!mounted) return;
    setState(() {
      _speaking = turn;
      _error = null;
    });
    try {
      await _voice.speak(_spokenAnswer(turn), _language.locale);
    } catch (_) {
      _voiceUnavailable();
      return;
    }
    if (mounted && identical(_speaking, turn)) setState(() => _speaking = null);
  }

  String _spokenAnswer(Map<String, dynamic> turn) {
    final result = turn['result'] as Map<String, dynamic>;
    final summary = (result['summary'] as String? ?? '').split(
      RegExp(r'(?<=[.!?।])\s+'),
    );
    final actions = (result['recommended_actions'] as List? ?? []);
    return [
      _headline(result['threat_level']),
      summary.first,
      if (actions.isNotEmpty) actions.first.toString(),
    ].join('. ');
  }

  String _headline(Object? level) => level == 'HIGH' || level == 'CRITICAL'
      ? _t.urgentHeadline
      : level == 'LOW'
      ? _t.lowHeadline
      : _t.mediumHeadline;

  /// Stops dictation and speech before the shared language changes.
  Future<void> _beforeLanguageChange() async {
    if (_listening) {
      try {
        await _voice.stopListening();
      } catch (_) {
        /* Not listening any more. */
      }
    }
    await _stopSpeaking();
    if (!mounted) return;
    setState(() {
      _listening = false;
      _error = null;
    });
  }

  @override
  void dispose() {
    _voiceService?.stopListening().catchError((_) {});
    _voiceService?.stopSpeaking().catchError((_) {});
    _client?.close(force: true);
    _scroll.dispose();
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
        _error = null;
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

  void _edited(String _) => setState(() => _error = null);

  void _toLatest() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _scroll.hasClients) {
        _scroll.animateTo(
          _scroll.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

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
    if (_busy) return;
    final message = _message.text.trim();
    if (message.isEmpty && _image == null) return;
    final link = RegExp(r'https?://[^\s]+').firstMatch(message)?.group(0);
    final url = link ?? '';
    final prior = _turns
        .where((turn) => turn['result'] != null)
        .map(
          (turn) => {
            'message': turn['text'],
            'url': turn['url'],
            if (turn['image'] != null)
              'note': 'Earlier image is not included in this request.',
          },
        )
        .toList();
    final text = prior.isEmpty
        ? message
        : jsonEncode({
            'earlier_user_messages': prior,
            'latest_user_message': message,
          });
    if (text.length > 12000 || _turns.length >= 6) {
      setState(
        () => _error = 'This chat is full. Start a new chat from the top menu.',
      );
      return;
    }
    final allowed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Symbols.cloud, size: 36),
        title: Text(tr('Send for an AI check?')),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                tr(
                  'Your messages in this chat and the selected photo go to Dhalam’s backend and Google AI. Remove OTPs, passwords and account details first.',
                ),
              ),
              const SizedBox(height: 12),
              Text(
                tr(
                  'Device checks stay on your phone. Earlier photos are not sent again.',
                ),
              ),
              ExpansionTile(
                title: Text(tr('Privacy details')),
                children: [
                  Text(
                    tr(
                      'Links may also be checked with Google Safe Browsing. Dhalam does not save submissions on its server; Google service retention policies apply. Chat stays in memory until you leave or clear it.',
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(tr('Not now')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(tr('Send for check')),
          ),
        ],
      ),
    );
    if (allowed != true || !mounted) return;
    FocusScope.of(context).unfocus();
    final turn = <String, dynamic>{
      'text': message,
      'context': text,
      'url': url,
      'image': _image,
    };
    setState(() {
      _busy = true;
      _error = null;
      _turns.add(turn);
    });
    _toLatest();
    try {
      final result = await (widget.analyze ?? _request)({
        'consent': true,
        if (_language.code != 'en') 'language': _language.code,
        if (text.isNotEmpty) 'text': text,
        if (url.isNotEmpty) 'url': url,
        if (_image != null) 'image_base64': base64Encode(_image!),
        if (_mime != null) 'image_mime_type': _mime,
      });
      if (mounted) {
        setState(() {
          turn['result'] = result;
          _message.clear();
          _url.clear();
          _image = null;
          _mime = null;
        });
      }
    } on FormatException catch (e) {
      if (mounted) {
        setState(() {
          _error = e.message;
          _turns.remove(turn);
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _error =
              'I couldn’t connect. Try again in a moment. If you’re being pressured, pause before paying.';
          _turns.remove(turn);
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
      _toLatest();
    }
  }

  Future<void> _report(Map<String, dynamic> turn) async {
    final result = turn['result'] as Map<String, dynamic>;
    try {
      await widget.journal.load();
    } catch (_) {
      // The report retains its existing local observations if storage is unavailable.
    }
    if (!mounted) return;
    final report = incidentReport(
      result,
      (turn['context'] ?? turn['text']) as String,
      turn['url'] as String,
      widget.journal.report(widget.ios),
    );
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(tr('Review incident report')),
        content: SizedBox(
          width: 600,
          child: SingleChildScrollView(child: SelectableText(report)),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(tr('Close')),
          ),
          TextButton(
            onPressed: () async {
              try {
                await toolsChannel.invokeMethod<void>('shareReport', report);
              } catch (_) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        tr(
                          'Sharing unavailable. You can copy the report text.',
                        ),
                      ),
                    ),
                  );
                }
              }
            },
            child: Text(tr('Share report')),
          ),
        ],
      ),
    );
  }

  Future<void> _connection() => showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(tr('Demo connection')),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _endpoint,
              decoration: InputDecoration(labelText: tr('Backend URL')),
            ),
            TextField(
              controller: _token,
              obscureText: true,
              enableSuggestions: false,
              autocorrect: false,
              decoration: InputDecoration(
                labelText: tr('Cloud Run token (empty for local)'),
              ),
            ),
            const SizedBox(height: 12),
            Text(tr('Session only. Never enter an API key.')),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(tr('Done')),
        ),
      ],
    ),
  );

  Future<void> _details(Map<String, dynamic> turn) async {
    final result = turn['result'] as Map<String, dynamic>;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * .75,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
            children: [
              Text(
                tr('Why this advice?'),
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 16),
              Text(result['summary'] as String),
              for (final section in {
                'coercion_markers': tr('Warning signs'),
                'evidence_extracted': tr('What you shared'),
                'recommended_actions': tr('What to do'),
              }.entries) ...[
                const SizedBox(height: 16),
                Text(
                  section.value,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                for (final item in (result[section.key] as List? ?? []))
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text('• $item'),
                  ),
              ],
              const SizedBox(height: 16),
              Text(reputationLabel(result['url_reputation'] as String?)),
              const SizedBox(height: 16),
              Text(
                tr('Sources for this advice'),
                style: Theme.of(context).textTheme.titleMedium,
              ),
              SelectableText(referenceNotes(result)),
              Text(
                result['limitations'] as String? ??
                    tr('AI can be wrong. Verify independently.'),
              ),
              const SizedBox(height: 16),
              Text(
                tr('AI concern: {n}/100. Not a measured probability.', {
                  'n': result['scam_probability'],
                }),
              ),
              Text(
                tr('Model: {model} · {at}', {
                  'model': result['model'],
                  'at': result['analyzed_at'],
                }),
              ),
              OutlinedButton.icon(
                onPressed: () {
                  Navigator.pop(context);
                  _report(turn);
                },
                icon: const Icon(Symbols.description),
                label: Text(tr('Preview incident report')),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _answer(Map<String, dynamic> turn) {
    final result = turn['result'] as Map<String, dynamic>;
    final level = result['threat_level'];
    final urgent = level == 'HIGH' || level == 'CRITICAL';
    final low = level == 'LOW';
    final color = urgent
        ? const Color(0xFFB73535)
        : low
        ? const Color(0xFF246B73)
        : const Color(0xFF986000);
    final headline = _headline(level);
    final summary = result['summary'] as String? ?? '';
    final sentences = summary.split(RegExp(r'(?<=[.!?।])\s+'));
    final short = sentences.first;
    final actions = (result['recommended_actions'] as List? ?? [])
        .cast<String>();
    return Semantics(
      liveRegion: true,
      child: Container(
        margin: const EdgeInsets.only(top: 12, bottom: 24),
        padding: const EdgeInsets.all(22),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(24),
          border: Border.all(color: color.withValues(alpha: .25)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: .12),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    urgent
                        ? Symbols.front_hand_rounded
                        : low
                        ? Symbols.search_rounded
                        : Symbols.pause_rounded,
                    color: color,
                    size: 30,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Text(
                    headline,
                    style: TextStyle(
                      fontSize: 25,
                      height: 1.2,
                      fontWeight: FontWeight.w700,
                      color: color,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              short.length > 200 ? '${short.substring(0, 197)}…' : short,
              style: const TextStyle(fontSize: 18, height: 1.45),
            ),
            if (actions.isNotEmpty) ...[
              const SizedBox(height: 18),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Symbols.arrow_circle_right, color: color),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      actions.first,
                      style: const TextStyle(
                        fontSize: 18,
                        height: 1.4,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ],
            if ((result['coercion_markers'] as List? ?? []).isNotEmpty) ...[
              const SizedBox(height: 16),
              Text(
                tr('Warning signs'),
                style: Theme.of(context).textTheme.titleMedium,
              ),
              for (final marker in (result['coercion_markers'] as List).take(3))
                Card(
                  child: ListTile(
                    leading: Icon(Symbols.warning_rounded, color: color),
                    title: Text(marker.toString()),
                  ),
                ),
            ],
            if ((result['evidence_extracted'] as List? ?? []).isNotEmpty)
              ExpansionTile(
                tilePadding: EdgeInsets.zero,
                title: Text(tr('What you shared')),
                children: [
                  for (final evidence
                      in (result['evidence_extracted'] as List).take(3))
                    ListTile(
                      leading: const Icon(Symbols.format_quote),
                      title: Text(evidence.toString()),
                    ),
                ],
              ),
            const SizedBox(height: 12),
            Text(
              tr(
                low
                    ? 'This does not prove it is safe.'
                    : 'AI can be wrong. Check independently.',
              ),
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
            Wrap(
              children: [
                TextButton.icon(
                  onPressed: () => _toggleSpeaking(turn),
                  icon: Icon(
                    identical(_speaking, turn)
                        ? Symbols.stop_circle
                        : Symbols.volume_up,
                  ),
                  label: Text(
                    identical(_speaking, turn) ? _t.stopReading : _t.readAloud,
                  ),
                ),
                TextButton.icon(
                  onPressed: () => _details(turn),
                  icon: const Icon(Symbols.info),
                  label: Text(tr('Why? Show details')),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [Icon(Symbols.shield), SizedBox(width: 10), Text('Dhalam')],
        ),
        actions: [
          LanguageMenuButton(
            enabled: !_busy,
            beforeChange: _beforeLanguageChange,
          ),
          PopupMenuButton<String>(
            tooltip: tr('Chat options'),
            enabled: !_busy,
            onSelected: (choice) async {
              if (choice == 'connection') {
                await _connection();
              }
              if (choice == 'device' && context.mounted) {
                await Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => const DashboardScreen(),
                  ),
                );
              }
              if (choice == 'clear') {
                setState(() {
                  _turns.clear();
                  _message.clear();
                  _image = null;
                  _mime = null;
                  _error = null;
                });
              }
            },
            itemBuilder: (_) => [
              PopupMenuItem(value: 'clear', child: Text(tr('New chat'))),
              PopupMenuItem(value: 'device', child: Text(tr('Device checks'))),
              PopupMenuItem(
                value: 'connection',
                child: Text(tr('Demo connection')),
              ),
            ],
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            const CaptureWarning(),
            Expanded(
              child: ListView(
                controller: _scroll,
                padding: const EdgeInsets.symmetric(
                  horizontal: 20,
                  vertical: 16,
                ),
                children: [
                  if (_turns.isEmpty) ...[
                    const SizedBox(height: 20),
                    Center(
                      child: Container(
                        padding: const EdgeInsets.all(24),
                        decoration: BoxDecoration(
                          color: colors.primaryContainer,
                          borderRadius: BorderRadius.circular(32),
                        ),
                        child: Icon(
                          Symbols.forum,
                          size: 64,
                          color: colors.primary,
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text(
                      _t.emptyTitle,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontSize: 30,
                        height: 1.2,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 14),
                    Text(
                      _t.emptySubtitle,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 18,
                        height: 1.4,
                        color: colors.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 26),
                    Wrap(
                      alignment: WrapAlignment.center,
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        ActionChip(
                          avatar: const Icon(Symbols.currency_rupee, size: 20),
                          label: Text(tr('Someone wants money')),
                          onPressed: () {
                            _message.text = tr(
                              'Someone is asking me to send money. ',
                            );
                            setState(() {});
                          },
                        ),
                        ActionChip(
                          avatar: const Icon(Symbols.link, size: 20),
                          label: Text(tr('I got a link')),
                          onPressed: () {
                            _message.text = tr('Can you check this link? ');
                            setState(() {});
                          },
                        ),
                        ActionChip(
                          avatar: const Icon(Symbols.image, size: 20),
                          label: Text(tr('Check a screenshot')),
                          onPressed: () => _pick(ImageSource.gallery),
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    Text(
                      tr('No need to decide in a hurry.'),
                      textAlign: TextAlign.center,
                      style: TextStyle(color: colors.onSurfaceVariant),
                    ),
                  ],
                  for (final turn in _turns) ...[
                    Align(
                      alignment: Alignment.centerRight,
                      child: Container(
                        constraints: const BoxConstraints(maxWidth: 520),
                        margin: const EdgeInsets.only(left: 36),
                        padding: const EdgeInsets.all(18),
                        decoration: BoxDecoration(
                          color: colors.primaryContainer,
                          borderRadius: BorderRadius.circular(22),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            if ((turn['text'] as String).isNotEmpty)
                              Text(
                                turn['text'] as String,
                                style: const TextStyle(
                                  fontSize: 18,
                                  height: 1.4,
                                ),
                              ),
                            if (turn['image'] != null)
                              Padding(
                                padding: const EdgeInsets.only(top: 8),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    const Icon(Symbols.image),
                                    const SizedBox(width: 8),
                                    Text(tr('Photo shared')),
                                  ],
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                    ChatLinkPreview(text: turn['text'] as String),
                    if (turn['result'] != null) _answer(turn),
                  ],
                  if (_busy)
                    Padding(
                      padding: const EdgeInsets.all(20),
                      child: Row(
                        children: [
                          const SizedBox(
                            width: 22,
                            height: 22,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Text(
                              _t.checking,
                              style: const TextStyle(fontSize: 18),
                            ),
                          ),
                        ],
                      ),
                    ),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Text(
                        trText(_error!),
                        style: TextStyle(color: colors.error, fontSize: 18),
                      ),
                    ),
                ],
              ),
            ),
            Container(
              decoration: BoxDecoration(
                color: colors.surface,
                border: Border(top: BorderSide(color: colors.outlineVariant)),
              ),
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 8),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (_image != null)
                    Row(
                      children: [
                        ClipRRect(
                          borderRadius: BorderRadius.circular(12),
                          child: Image.memory(
                            _image!,
                            width: 52,
                            height: 52,
                            fit: BoxFit.cover,
                            errorBuilder: (_, error, stack) =>
                                const Icon(Symbols.image),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(child: Text(tr('Photo ready to check'))),
                        IconButton(
                          tooltip: tr('Remove photo'),
                          onPressed: _busy
                              ? null
                              : () => setState(() {
                                  _image = null;
                                  _mime = null;
                                }),
                          icon: const Icon(Symbols.close),
                        ),
                      ],
                    ),
                  ValueListenableBuilder<TextEditingValue>(
                    valueListenable: _message,
                    builder: (context, value, _) =>
                        ChatLinkPreview(text: value.text),
                  ),
                  TextField(
                    controller: _message,
                    enabled: !_busy,
                    onChanged: _edited,
                    minLines: 1,
                    maxLines: 3,
                    maxLength: 12000,
                    style: const TextStyle(fontSize: 18),
                    decoration: InputDecoration(
                      hintText: _listening ? _t.listening : _t.hint,
                      counterText: '',
                      filled: true,
                      fillColor: colors.surfaceContainerLowest,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(20),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      IconButton(
                        tooltip: tr('Choose screenshot'),
                        onPressed: _busy
                            ? null
                            : () => _pick(ImageSource.gallery),
                        icon: const Icon(Symbols.image),
                      ),
                      IconButton(
                        tooltip: tr('Take photo'),
                        onPressed: _busy
                            ? null
                            : () => _pick(ImageSource.camera),
                        icon: const Icon(Symbols.photo_camera),
                      ),
                      IconButton(
                        tooltip: _listening ? _t.stopDictation : _t.dictate,
                        isSelected: _listening,
                        onPressed: _busy ? null : _toggleListening,
                        icon: const Icon(Symbols.mic),
                        selectedIcon: const Icon(Symbols.stop_circle),
                      ),
                      const Spacer(),
                      FilledButton.icon(
                        onPressed:
                            !_busy &&
                                (_message.text.trim().isNotEmpty ||
                                    _image != null)
                            ? _analyze
                            : null,
                        style: FilledButton.styleFrom(
                          minimumSize: const Size(110, 48),
                        ),
                        icon: const Icon(Symbols.arrow_upward_rounded),
                        label: Text(
                          _t.check,
                          style: const TextStyle(fontSize: 18),
                        ),
                      ),
                    ],
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
  'matched' => tr('Google Safe Browsing flagged this URL. Avoid proceeding.'),
  'no_match' => tr(
    'No Safe Browsing match. This does not mean the link is safe.',
  ),
  'unavailable' => tr(
    'URL reputation lookup unavailable; no reputation verdict.',
  ),
  'not_configured' => tr(
    'URL reputation is not configured. AI reviewed the URL text only.',
  ),
  _ => tr('No URL reputation lookup requested.'),
};

String referenceNotes(Map<String, dynamic> result) {
  final sources = result['sources'] as List? ?? [];
  if (sources.isEmpty) {
    return switch (result['grounding_status']) {
      'no_match' => tr(
        'No close reference found in the small advisory collection. This does not establish safety.',
      ),
      'matched' => tr(
        'Related references were retrieved, but none was cited for this advice.',
      ),
      'not_requested' => tr(
        'No usable text was available for reference retrieval.',
      ),
      _ => tr(
        'Reference retrieval unavailable. This answer is not backed by retrieved sources.',
      ),
    };
  }
  final entries = sources
      .map(
        (source) =>
            '${source['publisher']} — ${source['title']}\n'
            '${tr('Published: {p} · Reviewed: {r}', {'p': source['published'], 'r': source['reviewed']})}\n'
            '${source['summary']}\n${source['url']}',
      )
      .join('\n\n');
  return '${tr('Official guidance about reported patterns, not proof of the same scam or attacker.')}\n\n$entries';
}

String incidentReport(
  Map<String, dynamic> result,
  String message,
  String url,
  String observations,
) => [
  tr('DHALAM — PERSONAL CYBERCRIME INCIDENT NOTES'),
  tr('Prepared: {t}', {'t': DateTime.now().toUtc().toIso8601String()}),
  tr(
    'This is a user-reviewed draft, not an official complaint, forensic certification or proof of fraud.',
  ),
  '',
  tr('INCIDENT DETAILS — fill in before submitting'),
  tr('Incident date/time and timezone:'),
  tr('Contact method / sender identifier:'),
  tr('Amount lost (if any):'),
  tr('Bank / transaction reference (never include PINs, passwords or OTPs):'),
  tr('Actions already taken:'),
  '',
  tr('USER-SUBMITTED CONTENT'),
  tr('Message: {m}', {'m': message}),
  tr('URL: {u}', {'u': url}),
  tr(
    'Original screenshot: attach separately if relevant. Dhalam does not embed it in this report.',
  ),
  '',
  tr('AI ASSESSMENT — may be incorrect; observations below are separate'),
  tr('Model: {m}', {'m': result['model']}),
  tr('Analyzed: {t}', {'t': result['analyzed_at']}),
  tr('Concern: {l} ({p}/100, uncalibrated)', {
    'l': result['threat_level'],
    'p': result['scam_probability'],
  }),
  '${result['summary']}',
  tr('Indicators: {x}', {
    'x': (result['coercion_markers'] as List? ?? []).join('; '),
  }),
  tr('Extracted content: {x}', {
    'x': (result['evidence_extracted'] as List? ?? []).join('; '),
  }),
  tr('Suggested actions: {x}', {
    'x': (result['recommended_actions'] as List? ?? []).join('; '),
  }),
  reputationLabel(result['url_reputation'] as String?),
  '${result['limitations'] ?? ''}',
  '',
  tr('REFERENCE GUIDANCE'),
  referenceNotes(result),
  '',
  tr('LOCAL DEVICE OBSERVATIONS'),
  observations,
  '',
  tr('REPORTING'),
  tr(
    'For suspected financial cyber fraud contact your bank and 1930 / https://cybercrime.gov.in.',
  ),
  tr(
    'Preserve original messages, transaction references and screenshots. Review and redact this draft before sharing. No complaint has been filed by Dhalam.',
  ),
  '',
].join('\n');
