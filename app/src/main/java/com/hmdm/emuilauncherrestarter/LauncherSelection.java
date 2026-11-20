package com.hmdm.emuilauncherrestarter;

/** Pins a handoff session to one supported package, including across package replacement. */
final class LauncherSelection {
    static String select(boolean currentAvailable, boolean legacyAvailable,
                         String legacyVersion, String oldVersion) {
        if (!currentAvailable && legacyAvailable
                && (oldVersion == null || oldVersion.equals(legacyVersion))) {
            return "ru.headwind.kiosk";
        }
        return "com.hmdm.launcher";
    }
    private LauncherSelection() { }
}
