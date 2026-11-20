package com.hmdm.emuilauncherrestarter;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LauncherSelectionTest {
    @Test public void prefersCurrentLauncherWhenBothExist() {
        assertEquals("com.hmdm.launcher", LauncherSelection.select(true, true, "6.39", "6.39"));
    }
    @Test public void unrelatedLegacyVersionCannotProveCurrentUpdateCompleted() {
        assertEquals("com.hmdm.launcher", LauncherSelection.select(false, true, "5.0", "6.39"));
    }
    @Test public void matchesLegacyUpdateCallerBeforeReplacement() {
        assertEquals("ru.headwind.kiosk", LauncherSelection.select(false, true, "5.0", "5.0"));
    }
    @Test public void manualLaunchSupportsLegacyFallback() {
        assertEquals("ru.headwind.kiosk", LauncherSelection.select(false, true, "5.0", null));
    }
    @Test public void absentPackagesWaitForCurrentLauncher() {
        assertEquals("com.hmdm.launcher", LauncherSelection.select(false, false, null, "6.39"));
    }
}
