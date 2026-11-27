import com.zippy.ear.ListenState;

/** The Listening status line can never contradict the switch or what the service is doing. */
public class ListenStateTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    public static void main(String[] a) {
        int[] engines = {ListenState.OFF, ListenState.STARTING, ListenState.LISTENING};
        String[] notes = {"Off.", "", null, "Listening for Hey Zippy.", "Loading speech on this phone.", "Sent to Zippy."};
        boolean noOffWhileOn = true, noLiveWhileOff = true;
        for (boolean wanted : new boolean[]{false, true}) for (int e : engines) for (String n : notes) {
            String t = ListenState.statusText(wanted, e, n);
            if (wanted && t.equals("Off.")) noOffWhileOn = false;
            if (!wanted && !t.equals("Off.") && !t.equals("Stopping…")) noLiveWhileOff = false;
            if (!wanted && e == ListenState.OFF && !t.equals("Off.")) noLiveWhileOff = false;
        }
        check(noOffWhileOn, "switch ON never shows 'Off.' (the 1.3 bug: stale 'Off.' note while the mic ran)");
        check(noLiveWhileOff, "switch OFF shows 'Off.' (or 'Stopping…' while the mic is released), never a listening note");
        check(ListenState.statusText(true, ListenState.LISTENING, "Off.").equals("Listening for Hey Zippy."), "stale 'Off.' note + running mic -> 'Listening for Hey Zippy.'");
        check(ListenState.statusText(true, ListenState.OFF, "Listening for Hey Zippy.").startsWith("Starting"), "switch ON but service dead -> 'Starting…' (and the screen restarts it)");
        check(!ListenState.shouldRun(false, false), "sticky restart while switch is OFF stops instead of opening the mic");
        check(!ListenState.shouldRun(true, true), "Stop action always stops");
        check(ListenState.shouldRun(true, false), "switch ON runs");
        check(ListenState.screenShouldStart(true, ListenState.OFF, true), "screen restarts a dead listener when switch is ON");
        check(!ListenState.screenShouldStart(true, ListenState.OFF, false), "...but not without mic permission");
        check(!ListenState.screenShouldStart(false, ListenState.OFF, true) && !ListenState.screenShouldStart(true, ListenState.LISTENING, true), "...and never when OFF or already running");
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
