package com.welli.cpmwheel;

/** First-person option state, kept apart from FirstPerson so nothing CPM-typed loads without CPM. */
public final class FpState {
    private FpState() {}
    public static final String[] MODES = {"off", "layers", "all"};
    /** updated from the config every 2 s / on settings change, never read from disk per frame */
    public static volatile String mode = "layers";
}
