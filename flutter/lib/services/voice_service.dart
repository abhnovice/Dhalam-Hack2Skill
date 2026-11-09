import 'package:flutter_tts/flutter_tts.dart';
import 'package:speech_to_text/speech_to_text.dart';

/// Thrown when voice cannot be used; the message is safe to show to the user.
class VoiceUnavailableException implements Exception {
  const VoiceUnavailableException();
}

/// Speech input and spoken answers. The screen depends on this interface so
/// tests can substitute a fake and never touch platform plugins.
abstract class VoiceService {
  /// Starts dictation in [locale] (for example `hi-IN`). [onResult] receives the
  /// recognised words so far. Throws [VoiceUnavailableException] when
  /// permission is denied or the device has no recogniser for the language.
  Future<void> startListening({
    required String locale,
    required void Function(String words) onResult,
    required void Function() onDone,
  });
  Future<void> stopListening();

  /// Reads [text] aloud. Throws [VoiceUnavailableException] when the device has
  /// no voice for [locale].
  Future<void> speak(String text, String locale);
  Future<void> stopSpeaking();
}

class DeviceVoiceService implements VoiceService {
  final SpeechToText _stt = SpeechToText();
  final FlutterTts _tts = FlutterTts();
  bool _sttReady = false;
  void Function()? _onDone;

  Future<String?> _matchLocale(String wanted) async {
    final normalized = wanted.replaceAll('-', '_').toLowerCase();
    final language = normalized.split('_').first;
    final available = await _stt.locales();
    for (final candidate in available) {
      if (candidate.localeId.replaceAll('-', '_').toLowerCase() == normalized) {
        return candidate.localeId;
      }
    }
    for (final candidate in available) {
      final id = candidate.localeId.replaceAll('-', '_').toLowerCase();
      if (id.split('_').first == language) return candidate.localeId;
    }
    return null;
  }

  @override
  Future<void> startListening({
    required String locale,
    required void Function(String words) onResult,
    required void Function() onDone,
  }) async {
    _onDone = onDone;
    if (!_sttReady) {
      _sttReady = await _stt.initialize(
        onStatus: (status) {
          if (status == 'done' || status == 'notListening') _onDone?.call();
        },
        onError: (_) => _onDone?.call(),
      );
    }
    if (!_sttReady) throw const VoiceUnavailableException();
    final localeId = await _matchLocale(locale);
    if (localeId == null) throw const VoiceUnavailableException();
    await _stt.listen(
      onResult: (result) => onResult(result.recognizedWords),
      listenOptions: SpeechListenOptions(
        localeId: localeId,
        listenFor: const Duration(seconds: 60),
        pauseFor: const Duration(seconds: 5),
        partialResults: true,
        cancelOnError: true,
        listenMode: ListenMode.dictation,
      ),
    );
  }

  @override
  Future<void> stopListening() => _stt.stop();

  @override
  Future<void> speak(String text, String locale) async {
    final available = await _tts.isLanguageAvailable(locale);
    if (available != true && available != 1) {
      throw const VoiceUnavailableException();
    }
    await _tts.setLanguage(locale);
    await _tts.setSpeechRate(0.42);
    await _tts.awaitSpeakCompletion(true);
    await _tts.speak(text);
  }

  @override
  Future<void> stopSpeaking() => _tts.stop();
}
