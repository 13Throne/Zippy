#!/usr/bin/env bash
# Synthesizes a small speaker dataset with espeak-ng + sox (16 kHz mono s16 WAV) for VoiceMatchTest.
# Layout: $OUT/<speaker>/{wake_0..9,reject_0..4,live_*}.wav
set -euo pipefail
OUT="${1:?out dir}"
command -v espeak-ng >/dev/null && command -v sox >/dev/null || { echo "need espeak-ng and sox" >&2; exit 2; }
mkdir -p "$OUT"
SPEAKERS=("en-us+m1" "en-us+m3" "en-us+f2" "en-gb+m2" "en-us+f4" "en-gb-scotland+m4")
say() { # voice speed pitch text out
  espeak-ng -v "$1" -s "$2" -p "$3" -w "$5.raw.wav" "$4"
  sox -q "$5.raw.wav" -r 16000 -c 1 -b 16 "$5.tmp.wav"; rm -f "$5.raw.wav"
}
place() { # in out lead_s total_s gain noise_amp [extra sox effects...]
  local in=$1 out=$2 lead=$3 total=$4 gain=$5 noise=$6; shift 6
  sox -q "$in" "$out.p.wav" pad "$lead" 0 "$@" vol "$gain"
  sox -q "$out.p.wav" "$out.t.wav" trim 0 "$total" pad 0 "$total" ; sox -q "$out.t.wav" "$out.c.wav" trim 0 "$total"
  sox -q -n -r 16000 -c 1 -b 16 "$out.n.wav" synth "$total" pinknoise vol "$noise"
  sox -q -m "$out.c.wav" "$out.n.wav" -b 16 "$out" ; rm -f "$out".p.wav "$out".t.wav "$out".c.wav "$out".n.wav
}
for sp in "${SPEAKERS[@]}"; do
  d="$OUT/$sp"; mkdir -p "$d"; t="$d/tmp"
  # 10 training takes like TrainActivity: 2.2 s from tap, varied speed/pitch/level/distance
  speeds=(170 170 165 175 195 145 170 170 160 170); pitches=(50 50 48 54 50 50 50 50 52 50)
  gains=(0.9 0.9 0.45 1.6 0.9 0.9 0.35 0.9 0.6 0.9); leads=(0.45 0.6 0.5 0.4 0.7 0.35 0.5 0.55 0.6 0.5)
  for i in $(seq 0 9); do
    say "$sp" "${speeds[$i]}" "${pitches[$i]}" "hey zippy" "$t"
    place "$t.tmp.wav" "$d/wake_$i.wav" "${leads[$i]}" 2.2 "${gains[$i]}" 0.004
  done
  j=0
  for txt in "hey" "zip" "what time is it" "hey zip"; do
    say "$sp" 170 50 "$txt" "$t"; place "$t.tmp.wav" "$d/reject_$j.wav" 0.5 2.2 0.9 0.004; j=$((j+1))
  done
  sox -q -n -r 16000 -c 1 -b 16 "$d/reject_4.wav" synth 2.0 pinknoise vol 0.004
  # live wake words as ListenService sees them: 1.6 s ring window ending ~0.35 s after the phrase
  n=0
  for cfg in "172 51 1.0 0.004" "168 49 0.3 0.004" "180 50 0.12 0.003" "160 52 1.8 0.006" "170 50 0.5 0.02" "175 50 0.25 0.004 reverb 50"; do
    set -- $cfg; sp_s=$1; sp_p=$2; g=$3; nz=$4; shift 4
    say "$sp" "$sp_s" "$sp_p" "hey zippy" "$t"
    dur=$(soxi -D "$t.tmp.wav"); lead=$(python3 -c "print(max(0.0, 1.6-0.35-$dur))")
    place "$t.tmp.wav" "$d/live_$n.wav" "$lead" 1.6 "$g" "$nz" "$@"; n=$((n+1))
  done
  rm -f "$t.tmp.wav"
done
echo "voices in $OUT"
