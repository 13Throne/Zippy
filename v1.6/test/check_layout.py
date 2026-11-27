#!/usr/bin/env python3
"""Static check of MainActivity: each tab builder only holds its own sections (no secrets on Home)."""
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
for word in ["Webhook", "keyField", "Send test", "Use Android speech recognition for commands", "Open Grok Bot on wake", "Check for updates", "Installed v1.6."]:
    if word not in settings: bad.append(f"Settings lacks {word}")
for word in ["Listening", "listenSwitch", "Last heard"]:
    if word not in home: bad.append(f"Home lacks {word}")
if "Look.edgeToEdge(root)" not in src: bad.append("no insets")
if "ResetConfirm.show(this)" not in voice: bad.append("Reset voice lost its confirmation")
print("\n".join(bad) if bad else "layout check: OK")
sys.exit(1 if bad else 0)
