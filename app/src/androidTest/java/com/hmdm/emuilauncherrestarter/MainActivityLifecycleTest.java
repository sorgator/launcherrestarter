package com.hmdm.emuilauncherrestarter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Real activity lifecycle checks; these deliberately do not simulate a launcher replacement. */
@RunWith(AndroidJUnit4.class)
public class MainActivityLifecycleTest {
    private static final long UI_TIMEOUT_MILLIS = 5_000;

    @Before
    public void requireAnIsolatedDeviceWithoutASupportedLauncher() {
        PackageManager manager = context().getPackageManager();
        assumeTrue("Lifecycle tests require the isolated AVD, not an installed MDM terminal",
                manager.getLaunchIntentForPackage("com.hmdm.launcher") == null
                        && manager.getLaunchIntentForPackage("ru.headwind.kiosk") == null);
    }

    @Test
    public void absentLauncherLeavesAVisibleWaitingScreenWithoutFinishing() {
        try (ActivityScenario<MainActivity> scenario = launch("before-update")) {
            awaitStatus(scenario, R.string.waiting_install);
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.progress).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.retry).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.open_launcher).getVisibility());
                assertTrue(activity.findViewById(R.id.helper_root).isShown());
            });
        }
    }

    @Test
    public void pausedActivityDoesNotAdvanceItsUiAndResumesItsExistingDeadline() {
        try (ActivityScenario<MainActivity> scenario = launch("before-update")) {
            awaitStatus(scenario, R.string.waiting_install);
            AtomicReference<MainActivity> reference = new AtomicReference<>();
            scenario.onActivity(reference::set);
            scenario.moveToState(Lifecycle.State.CREATED);
            onMain(() -> setStartedAt(reference.get(),
                    SystemClock.elapsedRealtime() - RestartPolicy.TIMEOUT_MILLIS));

            // Let a full polling interval pass while onPause/onStop have completed.
            SystemClock.sleep(1_250);
            assertEquals(Lifecycle.State.CREATED, scenario.getState());
            onMain(() -> {
                assertStatus(reference.get(), R.string.waiting_install);
                assertFalse(reference.get().isFinishing());
            });

            scenario.moveToState(Lifecycle.State.RESUMED);
            awaitStatus(scenario, R.string.update_timeout);
            assertRecoveryControls(scenario);
        }
    }

    @Test
    public void anotherWindowDefersPollingEvenWhileTheActivityIsResumed() {
        AtomicReference<AlertDialog> dialog = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = launch("before-update")) {
            awaitStatus(scenario, R.string.waiting_install);
            await(scenario, MainActivity::hasWindowFocus, "helper window focus");
            scenario.onActivity(activity -> {
                AlertDialog overlay = new AlertDialog.Builder(activity)
                        .setTitle("Instrumentation focus check")
                        .setPositiveButton(android.R.string.ok, null)
                        .create();
                dialog.set(overlay);
                overlay.show();
            });
            try {
                await(scenario, activity -> !activity.hasWindowFocus(), "dialog window focus");
                assertEquals(Lifecycle.State.RESUMED, scenario.getState());
                scenario.onActivity(activity -> setStartedAt(activity,
                        SystemClock.elapsedRealtime() - RestartPolicy.TIMEOUT_MILLIS));
                SystemClock.sleep(1_250);
                scenario.onActivity(activity -> {
                    assertFalse(activity.hasWindowFocus());
                    assertStatus(activity, R.string.waiting_install);
                    assertFalse(activity.isFinishing());
                });
            } finally {
                onMain(() -> dialog.get().dismiss());
            }
            awaitStatus(scenario, R.string.update_timeout);
            assertRecoveryControls(scenario);
        }
    }

    @Test
    public void repeatedSingleTopIntentRestartsWaitingAndRecreationPreservesTheDeadline() {
        try (ActivityScenario<MainActivity> scenario = launch("first-request")) {
            awaitStatus(scenario, R.string.waiting_install);
            AtomicReference<MainActivity> first = new AtomicReference<>();
            scenario.onActivity(activity -> {
                first.set(activity);
                setStartedAt(activity, SystemClock.elapsedRealtime() - RestartPolicy.TIMEOUT_MILLIS);
            });
            awaitStatus(scenario, R.string.update_timeout);

            context().startActivity(intent("second-request")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            await(scenario, activity -> "second-request".equals(activity.getIntent().getStringExtra("oldVersion"))
                    && activity.findViewById(R.id.progress).getVisibility() == View.VISIBLE,
                    "repeated intent to renew waiting");
            scenario.onActivity(activity -> {
                assertSame(first.get(), activity);
                assertEquals(View.GONE, activity.findViewById(R.id.retry).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.open_launcher).getVisibility());
            });
            awaitStatus(scenario, R.string.waiting_install);

            long originalDeadline = SystemClock.elapsedRealtime() - RestartPolicy.TIMEOUT_MILLIS / 2;
            scenario.onActivity(activity -> setStartedAt(activity, originalDeadline));
            scenario.recreate();
            awaitStatus(scenario, R.string.waiting_install);
            scenario.onActivity(activity -> {
                assertNotSame(first.get(), activity);
                assertEquals("second-request", activity.getIntent().getStringExtra("oldVersion"));
                assertEquals(originalDeadline, getStartedAt(activity));
                assertFalse(activity.isFinishing());
            });
        }
    }

    private static ActivityScenario<MainActivity> launch(String oldVersion) {
        return ActivityScenario.launch(intent(oldVersion));
    }

    private static Intent intent(String oldVersion) {
        return new Intent(context(), MainActivity.class).putExtra("oldVersion", oldVersion);
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static void awaitStatus(ActivityScenario<MainActivity> scenario, int stringResource) {
        await(scenario, activity -> activity.getString(stringResource).contentEquals(
                ((TextView) activity.findViewById(R.id.status)).getText()), "status resource " + stringResource);
    }

    private static void await(ActivityScenario<MainActivity> scenario, Condition condition, String description) {
        long deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS;
        AtomicBoolean matches = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> matches.set(condition.matches(activity)));
            if (matches.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Timed out waiting for " + description);
    }

    private static void assertStatus(MainActivity activity, int stringResource) {
        assertEquals(activity.getString(stringResource),
                ((TextView) activity.findViewById(R.id.status)).getText().toString());
    }

    private static void assertRecoveryControls(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            assertEquals(View.GONE, activity.findViewById(R.id.progress).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.retry).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.open_launcher).getVisibility());
            assertFalse(activity.isFinishing());
        });
    }

    // Advance the test deadline without making the emulator wait the production two minutes.
    private static void setStartedAt(MainActivity activity, long value) {
        try {
            Field field = MainActivity.class.getDeclaredField("startedAt");
            field.setAccessible(true);
            field.setLong(activity, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static long getStartedAt(MainActivity activity) {
        try {
            Field field = MainActivity.class.getDeclaredField("startedAt");
            field.setAccessible(true);
            return field.getLong(activity);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private interface Condition {
        boolean matches(MainActivity activity);
    }
}
