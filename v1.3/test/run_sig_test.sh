#!/usr/bin/env bash
# Verifies Sig.java's X-Zippy-Signature matches Python's hmac/hashlib for "<t>.<body>".
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac --release 11 -d "$T" "$HERE/java/com/zippy/ear/Sig.java" "$HERE/test/SigTest.java"
fail=0
check() {
  local secret="$1" t="$2" body="$3"
  local j p
  j="$(java -cp "$T" SigTest "$secret" "$t" "$body")"
  p="$(python3 -c 'import hmac,hashlib,sys; s,t,b=sys.argv[1:4]; print("t=%s,v1=%s"%(t,hmac.new(s.encode(),(t+"."+b).encode(),hashlib.sha256).hexdigest()))' "$secret" "$t" "$body")"
  if [[ "$j" == "$p" ]]; then echo "PASS $j"; else echo "FAIL java=$j python=$p"; fail=1; fi
}
check "my-zippy-secret" 1764199999 '{"text":"buy milk","source":"android"}'
check "pässwörd 🔑 long secret with spaces" 1700000000 '{"text":"café ☕ order","source":"android"}'
check "x" 0 ''
exit $fail
