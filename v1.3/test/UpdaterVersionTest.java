import com.zippy.ear.Updater;

public class UpdaterVersionTest {
    static int fails = 0;
    static void cmp(String a, String b, int want) {
        int got = Integer.signum(Updater.compare(Updater.parseVersion(a), Updater.parseVersion(b)));
        System.out.println((got == want ? "PASS " : "FAIL ") + a + " vs " + b + " -> " + got);
        if (got != want) fails++;
    }
    static void bad(String a) {
        boolean ok = Updater.parseVersion(a) == null;
        System.out.println((ok ? "PASS " : "FAIL ") + "unparseable '" + a + "'");
        if (!ok) fails++;
    }
    public static void main(String[] x) {
        cmp("v1.4", "1.3", 1);
        cmp("1.4", "1.3", 1);
        cmp("V1.3", "1.3", 0);
        cmp("v1.3.0", "1.3", 0);
        cmp("v1.10", "1.9", 1);
        cmp("v1.3.1", "1.3", 1);
        cmp("v1.2", "1.3", -1);
        cmp("v2.0-beta", "1.9.9", 1);
        cmp("v4", "1.3", 1);
        bad(""); bad("latest"); bad("v"); bad(".1");
        if (fails > 0) System.exit(1);
    }
}
