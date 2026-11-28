#!/usr/bin/env python3
"""Zippy 1.81 resources on top of the 1.8 tree: version 1.81 / 10, custom wake phrase, volume-key service.

Usage: patch_v181.py <apktool-dir>
 - assets/kws/bpe.vocab: the KWS model's unigram piece table (BpeTokenizer spells typed phrases on the phone).
 - Manifest: PhraseActivity; VolumeKeyService (AccessibilityService, BIND_ACCESSIBILITY_SERVICE,
   res/xml/zippy_accessibility.xml: filter key events only, no window content).
 - res/values/strings.xml: the service's label and description shown in Android's Accessibility settings.
"""
import os, re, shutil, sys
d = sys.argv[1]
here = os.path.dirname(os.path.abspath(__file__))
def sub_once(s, a, b, what):
    if s.count(a) != 1: sys.exit(f"patch '{what}': expected 1 match, found {s.count(a)}")
    return s.replace(a, b)

shutil.copy(os.path.join(here, "assets", "kws", "bpe.vocab"), os.path.join(d, "assets", "kws", "bpe.vocab"))
os.makedirs(os.path.join(d, "res", "xml"), exist_ok=True)
shutil.copy(os.path.join(here, "res", "xml", "zippy_accessibility.xml"), os.path.join(d, "res", "xml", "zippy_accessibility.xml"))

sp = os.path.join(d, "res", "values", "strings.xml"); s = open(sp, encoding="utf-8").read()
s = sub_once(s, "</resources>",
    '    <string name="volume_service_label">Zippy volume shortcut</string>\n'
    "    <string name=\"volume_service_description\">Lets you double-press volume down to pause or resume Zippy\\'s listening, "
    "only while that option is on in Zippy\\'s Settings. It only looks at the volume-down key: it does not read the screen "
    "or anything you type. A single press still changes the volume.</string>\n</resources>", "strings")
open(sp, "w", encoding="utf-8").write(s)

mp = os.path.join(d, "AndroidManifest.xml"); s = open(mp, encoding="utf-8").read()
s = sub_once(s, '<activity android:exported="false" android:name="com.zippy.ear.TrainActivity"/>',
    '<activity android:exported="false" android:name="com.zippy.ear.TrainActivity"/>\n'
    '        <activity android:exported="false" android:windowSoftInputMode="adjustResize" android:name="com.zippy.ear.PhraseActivity"/>\n'
    '        <service android:exported="true" android:label="@string/volume_service_label" android:name="com.zippy.ear.VolumeKeyService" '
    'android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.accessibilityservice.AccessibilityService"/>\n'
    '            </intent-filter>\n'
    '            <meta-data android:name="android.accessibilityservice" android:resource="@xml/zippy_accessibility"/>\n'
    '        </service>', "manifest")
open(mp, "w", encoding="utf-8").write(s)

y = os.path.join(d, "apktool.yml"); s = open(y).read()
assert "versionCode: 9" in s and re.search(r"versionName: .?1\.8.?\n", s), "expects the 1.8 tree"
s = s.replace("versionCode: 9", "versionCode: 10")
s = re.sub(r"versionName: '?1\.8'?\n", "versionName: '1.81'\n", s)
open(y, "w").write(s)
bc = None
for root, _, files in os.walk(d):
    for f in files:
        if f == "BuildConfig.smali" and "com/zippy/ear" in root.replace(os.sep, "/"):
            bc = os.path.join(root, f)
t = open(bc).read()
assert "0x9" in t and '"1.8"' in t
t = re.sub(r"(VERSION_CODE:I = )0x9", r"\g<1>0xa", t).replace('"1.8"', '"1.81"')
open(bc, "w").write(t)
print("v1.81 patches applied")
