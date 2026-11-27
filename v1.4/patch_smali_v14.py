#!/usr/bin/env python3
"""Apply the v1.4 smali patches on top of an apktool decode already patched by v1.3/patch_smali.py."""
import re, sys, pathlib

root = pathlib.Path(sys.argv[1])
ear = root / "smali/com/zippy/ear"

def sub_once(text, old, new, what):
    n = text.count(old)
    if n != 1:
        sys.exit(f"patch '{what}': expected 1 match, found {n}")
    return text.replace(old, new)

def replace_method(text, header, body, what):
    m = re.search(re.escape(header) + r"\n.*?\.end method\n", text, re.S)
    if not m:
        sys.exit(f"patch '{what}': method not found")
    return text[:m.start()] + header + "\n" + body + ".end method\n" + text[m.end():]

# 1. ListenService is replaced by the Java version in classes2.dex
for f in ["ListenService.smali", "ListenService$Companion.smali",
          "ListenService$$ExternalSyntheticLambda0.smali", "ListenService$$ExternalSyntheticLambda1.smali",
          "ListenService$$ExternalSyntheticLambda2.smali"]:
    p = ear / f
    if not p.exists():
        sys.exit(f"expected {f}")
    p.unlink()
left = list(ear.glob("ListenService*.smali"))
if left:
    sys.exit(f"unexpected leftover ListenService smali: {left}")

# 2. Voice: training + any old callers use VoiceMatch
p = ear / "Voice.smali"
s = p.read_text()
s = replace_method(s, ".method public final fingerprint([FI)[F",
    "    .locals 1\n\n    invoke-static {p1, p2}, Lcom/zippy/ear/VoiceMatch;->fingerprint([FI)[F\n\n"
    "    move-result-object v0\n\n    return-object v0\n", "Voice.fingerprint")
s = replace_method(s, ".method public final matches([FLjava/util/List;Ljava/util/List;I)Z",
    "    .locals 1\n\n    invoke-static {p1, p2, p3, p4}, Lcom/zippy/ear/VoiceMatch;->matches([FLjava/util/List;Ljava/util/List;I)Z\n\n"
    "    move-result v0\n\n    return v0\n", "Voice.matches")
p.write_text(s)

# 3. MainActivity: live state sync, sensitivity migration/labels, voice retrain notice
p = ear / "MainActivity.smali"
s = p.read_text()
s = sub_once(s,
    "    move-object/from16 v0, p0\n\n    .line 56\n    invoke-super/range {p0 .. p1}, Landroidx/activity/ComponentActivity;->onCreate(Landroid/os/Bundle;)V\n",
    "    move-object/from16 v0, p0\n\n    .line 56\n    invoke-super/range {p0 .. p1}, Landroidx/activity/ComponentActivity;->onCreate(Landroid/os/Bundle;)V\n\n"
    "    invoke-static {v0}, Lcom/zippy/ear/ZippyUi;->onCreate(Lcom/zippy/ear/MainActivity;)V\n",
    "onCreate hook")
s = sub_once(s,
    "    invoke-static {p0, v0, v1}, Lcom/zippy/ear/Updater;->onResume(Landroid/app/Activity;Landroid/widget/Button;Landroid/widget/TextView;)V\n",
    "    invoke-static {p0, v0, v1}, Lcom/zippy/ear/Updater;->onResume(Landroid/app/Activity;Landroid/widget/Button;Landroid/widget/TextView;)V\n\n"
    "    invoke-static {p0}, Lcom/zippy/ear/ZippyUi;->onResume(Lcom/zippy/ear/MainActivity;)V\n",
    "onResume hook")
s = sub_once(s,
    "    const/4 v0, 0x0\n\n    .line 317\n    iput-boolean v0, p0, Lcom/zippy/ear/MainActivity;->rendering:Z\n\n    return-void\n.end method\n",
    "    move-object v0, p0\n\n"
    "    iget-object v1, p0, Lcom/zippy/ear/MainActivity;->status:Landroid/widget/TextView;\n\n"
    "    iget-object v2, p0, Lcom/zippy/ear/MainActivity;->listenSwitch:Landroid/widget/Switch;\n\n"
    "    iget-object v3, p0, Lcom/zippy/ear/MainActivity;->voice:Landroid/widget/TextView;\n\n"
    "    iget-object v4, p0, Lcom/zippy/ear/MainActivity;->sensitivity:Landroid/widget/SeekBar;\n\n"
    "    iget-object v5, p0, Lcom/zippy/ear/MainActivity;->sensitivityLabel:Landroid/widget/TextView;\n\n"
    "    invoke-static/range {v0 .. v5}, Lcom/zippy/ear/ZippyUi;->afterRender(Lcom/zippy/ear/MainActivity;Landroid/widget/TextView;Landroid/widget/Switch;Landroid/widget/TextView;Landroid/widget/SeekBar;Landroid/widget/TextView;)V\n\n"
    "    const/4 v0, 0x0\n\n    .line 317\n    iput-boolean v0, p0, Lcom/zippy/ear/MainActivity;->rendering:Z\n\n    return-void\n.end method\n\n"
    ".method public static rerender(Lcom/zippy/ear/MainActivity;)V\n    .locals 0\n\n"
    "    invoke-direct {p0}, Lcom/zippy/ear/MainActivity;->render()V\n\n    return-void\n.end method\n\n"
    ".method protected onPause()V\n    .locals 0\n\n"
    "    invoke-static {p0}, Lcom/zippy/ear/ZippyUi;->onPause(Lcom/zippy/ear/MainActivity;)V\n\n"
    "    invoke-super {p0}, Landroidx/activity/ComponentActivity;->onPause()V\n\n    return-void\n.end method\n",
    "render hook")
s = sub_once(s, 'const-string v4, "Installed v1.3."', 'const-string v4, "Installed v1.4."', "installed label")
s = sub_once(s, '    const-string v4, "55"\n', '    const-string v4, ""\n', "sensitivity label placeholder")
p.write_text(s)

# 4. Sensitivity label while dragging
p = ear / "MainActivity$onCreate$5$1.smali"
s = p.read_text()
m = re.search(r'    new-instance v0, Ljava/lang/StringBuilder;\n\n    const-string v1, "Looser  "\n.*?invoke-virtual \{p2\}, Ljava/lang/StringBuilder;->toString\(\)Ljava/lang/String;\n\n    move-result-object p2\n', s, re.S)
if not m:
    sys.exit("patch 'sensitivity label': not found")
s = s[:m.start()] + "    invoke-static {p2}, Lcom/zippy/ear/ZippyUi;->sensitivityLabel(I)Ljava/lang/String;\n\n    move-result-object p2\n" + s[m.end():]
p.write_text(s)

# 5. TrainActivity: pause the listener's mic while training
p = ear / "TrainActivity.smali"
s = p.read_text()
if ".method protected onResume()V" in s or ".method protected onPause()V" in s:
    sys.exit("TrainActivity already has onResume/onPause")
s += ("\n.method protected onResume()V\n    .locals 1\n\n"
      "    invoke-super {p0}, Landroidx/activity/ComponentActivity;->onResume()V\n\n"
      "    const/4 v0, 0x1\n\n    invoke-static {v0}, Lcom/zippy/ear/ListenService;->holdMic(Z)V\n\n    return-void\n.end method\n"
      "\n.method protected onPause()V\n    .locals 1\n\n"
      "    const/4 v0, 0x0\n\n    invoke-static {v0}, Lcom/zippy/ear/ListenService;->holdMic(Z)V\n\n"
      "    invoke-super {p0}, Landroidx/activity/ComponentActivity;->onPause()V\n\n    return-void\n.end method\n")
p.write_text(s)

# 6. Version 1.4 / code 5
p = root / "apktool.yml"
s = p.read_text()
s = sub_once(s, "versionCode: 4", "versionCode: 5", "versionCode")
s = sub_once(s, "versionName: 1.3", "versionName: 1.4", "versionName")
p.write_text(s)
p = ear / "BuildConfig.smali"
s = p.read_text()
s = sub_once(s, "VERSION_CODE:I = 0x4", "VERSION_CODE:I = 0x5", "BuildConfig code")
s = sub_once(s, 'VERSION_NAME:Ljava/lang/String; = "1.3"', 'VERSION_NAME:Ljava/lang/String; = "1.4"', "BuildConfig name")
p.write_text(s)
print("v1.4 smali patches applied")
