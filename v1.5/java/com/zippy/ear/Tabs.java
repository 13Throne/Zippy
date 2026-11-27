package com.zippy.ear;

/** The three MainActivity tabs, and what each one is allowed to show. */
public final class Tabs {
    public enum Tab {
        HOME("home", "Home"), VOICE("voice", "Voice"), SETTINGS("settings", "Settings");
        public final String key, title;
        Tab(String key, String title) { this.key = key; this.title = title; }
    }

    /** Sections; each lives on exactly one tab. */
    public enum Section {
        LISTENING(Tab.HOME), LAST_HEARD(Tab.HOME), SUMMARY(Tab.HOME), RECENT(Tab.HOME),
        TRAINING(Tab.VOICE), SENSITIVITY(Tab.VOICE), WAKE_CUE(Tab.VOICE), TASK_WAIT(Tab.VOICE), SENT_CUE(Tab.VOICE),
        WEBHOOK(Tab.SETTINGS), COMMANDS(Tab.SETTINGS), GROK_BOT(Tab.SETTINGS), BACKGROUND(Tab.SETTINGS), UPDATES(Tab.SETTINGS);
        public final Tab tab;
        Section(Tab t) { this.tab = t; }
    }

    private Tabs() {}

    public static Tab fromKey(String k) {
        for (Tab t : Tab.values()) if (t.key.equals(k)) return t;
        return Tab.HOME;
    }

    public static boolean visible(Section s, Tab selected) { return s.tab == selected; }
}
