package com.zippy.ear;
public final class Store {
    public static final Store INSTANCE = new Store();
    public final Settings read(android.content.Context c) { return null; }
    public final void saveSettings(android.content.Context c, String url, String key, int sensitivity, int waitMs, int readyCue, int sentCue) {}
    public final void setListening(android.content.Context c, boolean on) {}
    public final void setStatus(android.content.Context c, String note, String heard) {}
    public final Prints prints(android.content.Context c) { return null; }
    public final void clearPrints(android.content.Context c) {}
    public final java.util.List<Order> orders(android.content.Context c) { return null; }
    public final void addOrder(android.content.Context c, String text, boolean ok, String detail) {}
    public final String whenLabel(long at) { return null; }
}
