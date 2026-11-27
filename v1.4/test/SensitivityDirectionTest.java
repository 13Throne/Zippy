import com.zippy.ear.Sensitivity;
import com.zippy.ear.WakePhrase;

/** Wake-phrase matching must loosen as Sensitivity rises, the same direction as the voice check. */
public class SensitivityDirectionTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    public static void main(String[] a) {
        String[] heard = {"hey zippy", "hey zippy add milk", "hey zippi", "hey sippy", "hey zipy", "a zippy",
                "hay zippy", "hey zip", "hey zippo", "hey tippy", "zippy add eggs", "zippi", "sippy", "hey there",
                "what time is it", "happy", "hey", ""};
        boolean monotonic = true;
        for (String h : heard) {
            boolean prev = false;
            for (int s = 0; s <= 100; s++) {
                boolean ok = WakePhrase.parse(h, s) != null;
                if (prev && !ok) { monotonic = false; System.out.println("  non-monotonic: '" + h + "' at " + s); }
                prev = ok;
            }
        }
        check(monotonic, "every phrase accepted at sensitivity S is accepted at every S' > S");
        check(WakePhrase.parse("hey zippy", 0) != null, "exact 'hey zippy' works at the strictest setting");
        check("add milk".equals(WakePhrase.parse("Hey Zippy, add milk!", 0)), "task text after the wake phrase is returned");
        check(WakePhrase.parse("hey zip", 0) == null && WakePhrase.parse("hey zip", 60) != null, "'hey zip' only on Easy (>= 60)");
        check(WakePhrase.parse("zippy add eggs", 84) == null && "add eggs".equals(WakePhrase.parse("zippy add eggs", 85)), "bare 'zippy' only at >= 85");
        check(WakePhrase.parse("what time is it", 100) == null, "non-wake speech never triggers");
        int count0 = 0, count100 = 0;
        for (String h : heard) { if (WakePhrase.parse(h, 0) != null) count0++; if (WakePhrase.parse(h, 100) != null) count100++; }
        check(count100 > count0, "Sensitivity 100 accepts more variants (" + count100 + ") than 0 (" + count0 + ")");
        // 1.3 -> 1.4 migration: the old slider meant strictness
        check(Sensitivity.migrateValue(0) == 100, "old 0 (\"Looser\") becomes 100 (easiest)");
        check(Sensitivity.migrateValue(100) == 0 && Sensitivity.migrateValue(55) == 45, "old 100 -> 0, old 55 -> 45");
        check(Sensitivity.label(100).contains("voice check off") && Sensitivity.label(0).startsWith("0"), "labels explain the number");
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
