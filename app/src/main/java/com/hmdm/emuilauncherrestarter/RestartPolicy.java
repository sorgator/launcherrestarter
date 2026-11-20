package com.hmdm.emuilauncherrestarter;

/** Bounded update handoff; no arbitrary package names or background launch policy. */
final class RestartPolicy {
    static final long TIMEOUT_MILLIS = 120_000L;
    enum Decision { WAIT_FOR_INSTALL, WAIT_FOR_UPDATE, READY, TIMED_OUT }

    static Decision decide(boolean available, String oldVersion, String currentVersion,
                           long elapsedMillis) {
        if (available && (oldVersion == null
                || currentVersion != null && !oldVersion.equals(currentVersion))) {
            return Decision.READY;
        }
        if (elapsedMillis >= TIMEOUT_MILLIS) {
            return Decision.TIMED_OUT;
        }
        return available ? Decision.WAIT_FOR_UPDATE : Decision.WAIT_FOR_INSTALL;
    }

    private RestartPolicy() { }
}
