#!/usr/bin/env python3
"""Apply the v1.3 smali patches to an apktool decode of Zippy v1.2."""
import re, sys, pathlib

root = pathlib.Path(sys.argv[1])
ear = root / "smali/com/zippy/ear"
NEW_URL = "https://api2.cursor.sh/automations/webhook/eedaf91e-bf38-5ed3-a66f-a949716538a6"

def sub_once(text, old, new, what):
    n = text.count(old)
    if n != 1:
        sys.exit(f"patch '{what}': expected 1 match, found {n}")
    return text.replace(old, new)

# 1. Webhook.sign: non-whsec_ secrets -> Stripe-style X-Zippy-Signature (no Authorization header)
p = ear / "Webhook.smali"
s = p.read_text()
m = re.search(r"(    :cond_1\n    const-string p3, \"bearer \"\n.*?)(\.end method)", s, re.S)
if not m:
    sys.exit("patch 'Webhook.sign': bearer branch not found")
s = s[:m.start(1)] + (
    "    :cond_1\n"
    "    invoke-static {p1, p2, p3}, Lcom/zippy/ear/Sig;->stripe(Ljava/net/HttpURLConnection;Ljava/lang/String;[B)V\n\n"
    "    return-void\n") + s[m.start(2):]
p.write_text(s)

# 2. MainActivity: Reset voice -> confirmation dialog; actual reset moved to resetVoiceConfirmed()
p = ear / "MainActivity.smali"
s = p.read_text()
old_reset = re.search(
    r"\.method private static final onCreate\$lambda\$7\$lambda\$6\(Lcom/zippy/ear/MainActivity;Landroid/view/View;\)V\n.*?\.end method\n",
    s, re.S)
if not old_reset or "clearPrints" not in old_reset.group(0):
    sys.exit("patch 'Reset voice': lambda not found")
new_reset = (
    ".method private static final onCreate$lambda$7$lambda$6(Lcom/zippy/ear/MainActivity;Landroid/view/View;)V\n"
    "    .locals 0\n\n"
    "    invoke-static {p0}, Lcom/zippy/ear/ResetConfirm;->show(Lcom/zippy/ear/MainActivity;)V\n\n"
    "    return-void\n"
    ".end method\n\n"
    ".method public static resetVoiceConfirmed(Lcom/zippy/ear/MainActivity;)V\n"
    "    .locals 2\n\n"
    "    sget-object v1, Lcom/zippy/ear/Store;->INSTANCE:Lcom/zippy/ear/Store;\n\n"
    "    move-object v0, p0\n\n"
    "    check-cast v0, Landroid/content/Context;\n\n"
    "    invoke-virtual {v1, v0}, Lcom/zippy/ear/Store;->clearPrints(Landroid/content/Context;)V\n\n"
    "    invoke-direct {p0}, Lcom/zippy/ear/MainActivity;->render()V\n\n"
    "    return-void\n"
    ".end method\n")
s = s[:old_reset.start()] + new_reset + s[old_reset.end():]

# 3. Helper text + URL hint
s = sub_once(s,
    'const-string v4, "Open Grok, then Automations, then zippy-spoken-order. Copy the address and the secret."',
    'const-string v4, "Grok Bot routine: Hey Zippy voice tasks. Keep the address below and type the secret you gave Grok Bot. Each task is signed with X-Zippy-Signature."',
    "helper text")
s = sub_once(s, 'const-string v4, "https://"\n', f'const-string v4, "{NEW_URL}"\n', "url hint")

# 3b. Updater: route "Check for updates" through the new Updater, finish pending installs on resume
m = re.search(r"\.method private final checkUpdate\(\)V\n.*?\.end method\n", s, re.S)
if not m or "Checking GitHub." not in m.group(0):
    sys.exit("patch 'checkUpdate': method not found")
s = s[:m.start()] + (
    ".method private final checkUpdate()V\n"
    "    .locals 2\n\n"
    "    iget-object v0, p0, Lcom/zippy/ear/MainActivity;->updateButton:Landroid/widget/Button;\n\n"
    "    iget-object v1, p0, Lcom/zippy/ear/MainActivity;->updateStatus:Landroid/widget/TextView;\n\n"
    "    invoke-static {p0, v0, v1}, Lcom/zippy/ear/Updater;->check(Landroid/app/Activity;Landroid/widget/Button;Landroid/widget/TextView;)V\n\n"
    "    return-void\n"
    ".end method\n") + s[m.end():]
s = sub_once(s,
    ".method protected onResume()V\n    .locals 2\n\n    .line 194\n    invoke-super {p0}, Landroidx/activity/ComponentActivity;->onResume()V\n",
    ".method protected onResume()V\n    .locals 2\n\n    .line 194\n    invoke-super {p0}, Landroidx/activity/ComponentActivity;->onResume()V\n\n"
    "    iget-object v0, p0, Lcom/zippy/ear/MainActivity;->updateButton:Landroid/widget/Button;\n\n"
    "    iget-object v1, p0, Lcom/zippy/ear/MainActivity;->updateStatus:Landroid/widget/TextView;\n\n"
    "    invoke-static {p0, v0, v1}, Lcom/zippy/ear/Updater;->onResume(Landroid/app/Activity;Landroid/widget/Button;Landroid/widget/TextView;)V\n",
    "onResume hook")
s = sub_once(s, 'const-string v4, "Checks 13Throne/zippy on GitHub."', 'const-string v4, "Checks GitHub (13Throne/Zippy) for a newer release and installs it right over this one."', "updates blurb")
s = sub_once(s, 'const-string v4, "Installed 1.2."', 'const-string v4, "Installed v1.3."', "installed label")
s = sub_once(s, 'const-string v3, "Check for update"', 'const-string v3, "Check for updates"', "update button")
p.write_text(s)

# 4. Store: default webhook URL when nothing saved yet
p = ear / "Store.smali"
s = p.read_text()
s = sub_once(s, '    const-string v0, "webhookUrl"\n\n    const-string v1, ""\n',
             f'    const-string v0, "webhookUrl"\n\n    const-string v1, "{NEW_URL}"\n', "default url")
# v1 is reused as the "" default for the following keys, so restore it right after the URL read
s = sub_once(s, '    .line 32\n    :goto_0\n    const-string v0, "webhookKey"\n',
             '    .line 32\n    :goto_0\n    const-string v1, ""\n\n    const-string v0, "webhookKey"\n', "restore empty default")
p.write_text(s)

# 6. BuildConfig
p = ear / "BuildConfig.smali"
s2 = p.read_text()
s2 = sub_once(s2, "VERSION_CODE:I = 0x3", "VERSION_CODE:I = 0x4", "BuildConfig code")
s2 = sub_once(s2, 'VERSION_NAME:Ljava/lang/String; = "1.2"', 'VERSION_NAME:Ljava/lang/String; = "1.3"', "BuildConfig name")
p.write_text(s2)

# 5. Version 1.3 / code 4
p = root / "apktool.yml"
s = p.read_text()
s = sub_once(s, "versionCode: 3", "versionCode: 4", "versionCode")
s = sub_once(s, "versionName: 1.2", "versionName: 1.3", "versionName")
p.write_text(s)
print("smali patches applied")
