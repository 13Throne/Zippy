package com.zippy.ear;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Zippy 1.8: the "Last command" timeline on Home. Times are from the wake word. Pure Java. */
public final class Timeline {
    private static final class E { final int ms; final int seq; final String text; E(int ms, int seq, String t) { this.ms = ms; this.seq = seq; text = t; } }
    private final long wakeWall;
    private final List<E> events = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private int seq;

    public Timeline(long wakeWallMs) { this.wakeWall = wakeWallMs; }

    public long wakeWall() { return wakeWall; }

    /** Something that happened `ms` after the wake word. */
    public synchronized void at(int ms, String text) { events.add(new E(Math.max(0, ms), seq++, text)); }

    /** Something that happened now (wall clock). */
    public synchronized void now(String text) { at((int) (System.currentTimeMillis() - wakeWall), text); }

    /** A line without a time, shown under the events. */
    public synchronized void note(String text) { notes.add(text); }

    public synchronized String text() {
        List<E> s = new ArrayList<>(events);
        s.sort((a, b) -> a.ms != b.ms ? Integer.compare(a.ms, b.ms) : Integer.compare(a.seq, b.seq));
        StringBuilder b = new StringBuilder();
        b.append("Wake word at ").append(new SimpleDateFormat("h:mm:ss a", Locale.US).format(new Date(wakeWall)));
        for (E e : s) b.append('\n').append(String.format(Locale.US, "+%5.2f s  ", e.ms / 1000f)).append(e.text);
        for (String n : notes) b.append('\n').append(n);
        return b.toString();
    }
}
