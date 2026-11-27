package com.zippy.ear;

/**
 * Pure state logic shared by MainActivity (via ZippyUi) and ListenService, unit-tested on the JVM.
 * "wanted" = the user's switch (prefs "listening"); "engine" = what the service is really doing.
 */
public final class ListenState {
    public static final int OFF = 0, STARTING = 1, LISTENING = 2;

    private ListenState() {}

    /** Text for the status line under "Listening". Never says "Off." while the mic runs, or vice versa. */
    public static String statusText(boolean wanted, int engine, String note) {
        if (!wanted) {
            return engine == OFF ? "Off." : "Stopping…";
        }
        if (engine == OFF) return "Starting the microphone…";
        if (note == null || note.trim().isEmpty() || note.equals("Off.")) {
            return engine == STARTING ? "Starting the microphone…" : "Listening for Hey Zippy.";
        }
        return note;
    }

    /** Should the service keep running when the system (re)starts it? */
    public static boolean shouldRun(boolean wanted, boolean stopAction) {
        return wanted && !stopAction;
    }

    /** Should the visible screen (re)start the service? */
    public static boolean screenShouldStart(boolean wanted, int engine, boolean hasMic) {
        return wanted && engine == OFF && hasMic;
    }
}
