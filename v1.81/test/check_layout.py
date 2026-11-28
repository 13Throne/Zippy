#!/usr/bin/env python3
"""Static check of the 1.81 MainActivity (+ PhraseActivity): tabs hold only their own sections; 1.8 and 1.81 additions present."""
import re, sys, pathlib
src = (pathlib.Path(__file__).resolve().parent.parent / "java/com/zippy/ear/MainActivity.java").read_text()
def body(name):
    m = re.search(r"private void " + name + r"\(LinearLayout page\) \{(.*?)\n    \}\n", src, re.S)
    if not m: sys.exit(f"{name} not found")
    return m.group(1)
home, voice, settings = body("buildHome"), body("buildVoice"), body("buildSettings")
bad = []
for word in ["Webhook", "Secret", "keyField", "urlField", "Train", "Reset voice", "Check for updates", "Updater", "Sensitivity", "Spinner"]:
    if word in home: bad.append(f"Home has {word}")
for word in ["Train Hey Zippy", "Reset voice", "Sensitivity", "Wake cue", "greetingField", "greetingSpinner", "confirmSpinner", "Confirmation", "previewVoice(true)", "previewVoice(false)"]:
    if word not in voice: bad.append(f"Voice lacks {word}")
for word in ["Webhook", "keyField", "Send test", "Use Android speech recognition for commands", "Open Grok Bot after sending", "Check for updates", "Installed v1.81.", "Wake word", "Change wake word", "PhraseActivity.class", "Volume-down shortcut", "volumeSwitch", "Open Accessibility settings"]:
    if word not in settings: bad.append(f"Settings lacks {word}")
for word in ["Listening", "listenSwitch", "Last heard", "Last command", "lastCommand"]:
    if word not in home: bad.append(f"Home lacks {word}")
if "Open Grok Bot on wake" in src: bad.append("old 'Open Grok Bot on wake' label still present")
if "sensitivityText(" not in voice or "Sherpa-ONNX" not in src: bad.append("Voice tab doesn't explain the Sherpa-ONNX wake word")
if "Wake word: \\u201c%s\\u201d" not in src or "KwsGuard.engineLabel" not in src: bad.append("Home summary lacks the wake-word line")
if "ListenService.LAST_COMMAND" not in src: bad.append("Last command card doesn't read the saved timeline")
if "AndroidStream.describe" not in src: bad.append("Settings doesn't show EXTRA_AUDIO_SOURCE support")
if "Look.edgeToEdge(root)" not in src: bad.append("no insets")
if "ResetConfirm.show(this)" not in voice: bad.append("Reset voice lost its confirmation")
# 1.81
for word in ["resumeButton", "ListenService.paused()"]:
    if word not in home and word not in src: bad.append(f"Home lacks {word}")
for word in ["Wake word", "Volume-down", "PhraseActivity"]:
    if word in home: bad.append(f"Home has {word}")
for word in ["Why Accessibility", "Off by default", "A single press still changes the volume"]:
    if word not in src: bad.append(f"Volume-down card doesn't explain: {word}")
if "Sensitivity.WARNING" not in src: bad.append("Sensitivity label lacks the above-80 warning")
ph = (pathlib.Path(__file__).resolve().parent.parent / "java/com/zippy/ear/PhraseActivity.java").read_text()
for word in ["1. Your wake phrase", "2. Record it in your voice", "3. Ordinary speech", "4. Tune and test", "Test it", "Re-record all takes", "Save and use this wake word", "Use Hey Zippy instead", "ListenService.holdMic(true)", "ListenService.holdMic(false)", "WakeTuner.tune", "VoiceMatch.fingerprint"]:
    if word not in ph: bad.append(f"PhraseActivity lacks {word}")
print("\n".join(bad) if bad else "layout check: OK")
sys.exit(1 if bad else 0)
