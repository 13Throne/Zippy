package com.zippy.ear;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zippy 1.8: words with times from Vosk results (Recognizer.setWords(true)). Small hand parser so it
 * runs the same in desktop tests (android.jar's org.json is a stub there). Times are seconds from
 * the start of the recognizer's audio, across all its utterances.
 */
public final class VoskWords {
    public static final class Word {
        public final String text;
        public final double start, end;
        public Word(String text, double start, double end) { this.text = text; this.start = start; this.end = end; }
        @Override public String toString() { return text + "@" + start + "-" + end; }
    }

    private VoskWords() {}

    private static final Pattern OBJ = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern WORD = Pattern.compile("\"word\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern START = Pattern.compile("\"start\"\\s*:\\s*(-?[0-9.eE+-]+)");
    private static final Pattern END = Pattern.compile("\"end\"\\s*:\\s*(-?[0-9.eE+-]+)");
    private static final Pattern TEXT = Pattern.compile("\"(text|partial)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** All timed words in one Vosk result JSON (getResult / getFinalResult). */
    public static List<Word> parse(String json) {
        List<Word> out = new ArrayList<>();
        if (json == null) return out;
        int r = json.indexOf("\"result\"");
        if (r < 0) return out;
        Matcher m = OBJ.matcher(json);
        m.region(r, json.length());
        while (m.find()) {
            String o = m.group();
            Matcher w = WORD.matcher(o), s = START.matcher(o), e = END.matcher(o);
            if (w.find() && s.find() && e.find()) {
                try { out.add(new Word(w.group(1), Double.parseDouble(s.group(1)), Double.parseDouble(e.group(1)))); }
                catch (NumberFormatException ignored) { }
            }
        }
        return out;
    }

    public static List<Word> parseAll(List<String> results) {
        List<Word> out = new ArrayList<>();
        for (String j : results) out.addAll(parse(j));
        return out;
    }

    /** "text" (or "partial") of a result. */
    public static String text(String json) {
        if (json == null) return "";
        Matcher m = TEXT.matcher(json);
        return m.find() ? m.group(2).trim() : "";
    }

    public static String textOf(List<Word> ws) {
        StringBuilder b = new StringBuilder();
        for (Word w : ws) { if (b.length() > 0) b.append(' '); b.append(w.text); }
        return b.toString();
    }
}
