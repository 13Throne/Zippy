#!/usr/bin/env python3
"""Apply the v1.5 patches on top of an apktool decode already patched by v1.3 and v1.4."""
import sys, pathlib

root = pathlib.Path(sys.argv[1])
ear = root / "smali/com/zippy/ear"

def sub_once(text, old, new, what):
    n = text.count(old)
    if n != 1:
        sys.exit(f"patch '{what}': expected 1 match, found {n}")
    return text.replace(old, new)

# 1. MainActivity is replaced by the tabbed Java screen in classes2.dex
gone = list(ear.glob("MainActivity*.smali"))
if not gone:
    sys.exit("MainActivity smali not found")
for f in gone:
    f.unlink()
for f in ear.rglob("*.smali"):
    if "Lcom/zippy/ear/ZippyUi;" in f.read_text():
        sys.exit(f"{f.name} still references ZippyUi")

# 2. TrainActivity: edge-to-edge insets (targetSdk 35)
p = ear / "TrainActivity.smali"
s = p.read_text()
s = sub_once(s,
    "    invoke-super {p0}, Landroidx/activity/ComponentActivity;->onResume()V\n\n",
    "    invoke-super {p0}, Landroidx/activity/ComponentActivity;->onResume()V\n\n"
    "    invoke-static {p0}, Lcom/zippy/ear/Look;->edgeToEdge(Landroid/app/Activity;)V\n\n",
    "TrainActivity insets")
p.write_text(s)

# 3. Manifest: overlay permission (background Grok Bot launch) + Android 11+ package visibility
p = root / "AndroidManifest.xml"
s = p.read_text()
s = sub_once(s,
    '    <uses-permission android:name="android.permission.VIBRATE"/>\n',
    '    <uses-permission android:name="android.permission.VIBRATE"/>\n'
    '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>\n',
    "overlay permission")
s = sub_once(s, "    <application ",
    "    <queries>\n"
    '        <package android:name="ai.x.grok.bot"/>\n'
    "        <intent>\n"
    '            <action android:name="android.intent.action.MAIN"/>\n'
    '            <category android:name="android.intent.category.LAUNCHER"/>\n'
    "        </intent>\n"
    "        <intent>\n"
    '            <action android:name="android.speech.RecognitionService"/>\n'
    "        </intent>\n"
    "        <intent>\n"
    '            <action android:name="android.intent.action.TTS_SERVICE"/>\n'
    "        </intent>\n"
    "    </queries>\n"
    "    <application ", "queries")
p.write_text(s)

# 4. Version 1.5 / code 6
p = root / "apktool.yml"
s = p.read_text()
s = sub_once(s, "versionCode: 5", "versionCode: 6", "versionCode")
s = sub_once(s, "versionName: 1.4", "versionName: 1.5", "versionName")
p.write_text(s)
p = ear / "BuildConfig.smali"
s = p.read_text()
s = sub_once(s, "VERSION_CODE:I = 0x5", "VERSION_CODE:I = 0x6", "BuildConfig code")
s = sub_once(s, 'VERSION_NAME:Ljava/lang/String; = "1.4"', 'VERSION_NAME:Ljava/lang/String; = "1.5"', "BuildConfig name")
p.write_text(s)
print("v1.5 patches applied")
