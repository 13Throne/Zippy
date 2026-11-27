#!/usr/bin/env python3
"""v1.6 on top of the v1.3+v1.4+v1.5 patched decode: Liora clips in assets/voice (stored, not deflated), version 1.6/7."""
import sys, pathlib, shutil

root = pathlib.Path(sys.argv[1]); voice = pathlib.Path(sys.argv[2])
ear = root / "smali/com/zippy/ear"

def sub_once(text, old, new, what):
    n = text.count(old)
    if n != 1:
        sys.exit(f"patch '{what}': expected 1 match, found {n}")
    return text.replace(old, new)

clips = sorted(voice.glob("*.m4a"))
if len(clips) != 19:
    sys.exit(f"expected 19 clips, found {len(clips)}")
dst = root / "assets/voice"
dst.mkdir(parents=True, exist_ok=True)
for c in clips:
    shutil.copy(c, dst / c.name)

p = root / "apktool.yml"
s = p.read_text()
s = sub_once(s, "doNotCompress:\n", "doNotCompress:\n- m4a\n", "doNotCompress m4a")
s = sub_once(s, "versionCode: 6", "versionCode: 7", "versionCode")
s = sub_once(s, "versionName: 1.5", "versionName: 1.6", "versionName")
p.write_text(s)
p = ear / "BuildConfig.smali"
s = p.read_text()
s = sub_once(s, "VERSION_CODE:I = 0x6", "VERSION_CODE:I = 0x7", "BuildConfig code")
s = sub_once(s, 'VERSION_NAME:Ljava/lang/String; = "1.5"', 'VERSION_NAME:Ljava/lang/String; = "1.6"', "BuildConfig name")
p.write_text(s)
print("v1.6 patches applied")
