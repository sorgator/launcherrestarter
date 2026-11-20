package com.hmdm.emuilauncherrestarter;

import org.junit.Test;

import static org.junit.Assert.*;

/** Covers the legacy protocol's cancelled, completed and interrupted installations. */
public class ExampleUnitTest {
    @Test
    public void waitsWhileInstallerStillExposesOldVersion() {
        assertEquals(RestartPolicy.Decision.WAIT_FOR_UPDATE,
                RestartPolicy.decide(true, "6.39", "6.39", 10_000));
    }
    @Test
    public void handsOffAfterVersionTransition() {
        assertEquals(RestartPolicy.Decision.READY,
                RestartPolicy.decide(true, "6.39", "6.39-suntel.3", 10_000));
    }
    @Test
    public void toleratesPackageTemporarilyAbsentDuringReplacement() {
        assertEquals(RestartPolicy.Decision.WAIT_FOR_INSTALL,
                RestartPolicy.decide(false, "6.39", null, 10_000));
    }
    @Test
    public void cancelledInstallCannotPollForever() {
        assertEquals(RestartPolicy.Decision.TIMED_OUT,
                RestartPolicy.decide(true, "6.39", "6.39", RestartPolicy.TIMEOUT_MILLIS));
        assertEquals(RestartPolicy.Decision.TIMED_OUT,
                RestartPolicy.decide(false, "6.39", null, RestartPolicy.TIMEOUT_MILLIS));
    }
    @Test
    public void completedInstallCanReturnAfterLongSystemPrompt() {
        assertEquals(RestartPolicy.Decision.READY,
                RestartPolicy.decide(true, "6.39", "6.39-suntel.3", RestartPolicy.TIMEOUT_MILLIS + 1));
    }
    @Test
    public void manualLaunchDoesNotRequireOldVersionExtra() {
        assertEquals(RestartPolicy.Decision.READY,
                RestartPolicy.decide(true, null, "6.39-suntel.3", 1000));
    }
    @Test
    public void unknownVersionDoesNotProveSuccessfulUpdate() {
        assertEquals(RestartPolicy.Decision.WAIT_FOR_UPDATE,
                RestartPolicy.decide(true, "6.39", null, 1000));
    }
}
