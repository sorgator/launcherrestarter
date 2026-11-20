package com.hmdm.emuilauncherrestarter;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.time.Duration;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Android 12 lifecycle and handoff checks, without a terminal or real package installation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainActivityAndroid12Test {
    private static final String CURRENT = "com.hmdm.launcher";
    private static final String LEGACY = "ru.headwind.kiosk";
    private ActivityController<MainActivity> controller;
    private ShadowPackageManager packages;

    @Before
    public void setUp() {
        packages = shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void changedVersionHandsOffOnceAfterFocusReturns() {
        installLauncher(CURRENT, "6.39", 1);
        launch("6.39");
        advance(2);
        assertStatus(R.string.waiting_update);
        assertNull(nextStartedActivity());
        assertFalse(controller.get().isFinishing());

        // Another window can have focus even though this Activity remains resumed.
        controller.windowFocusChanged(false);
        installLauncher(CURRENT, "6.40", 2);
        advance(3);
        assertNull(nextStartedActivity());
        assertFalse(controller.get().isFinishing());

        controller.windowFocusChanged(true);
        advance(2);
        assertHandoff(CURRENT);
        advance(5);
        assertNull("A completed handoff must not be retried", nextStartedActivity());
    }

    @Test
    public void installerPauseDefersHandoffUntilTheActivityResumes() {
        installLauncher(CURRENT, "6.39", 1);
        launch("6.39");
        advance(1);
        controller.windowFocusChanged(false).pause();
        installLauncher(CURRENT, "6.40", 2);
        advance(3);
        assertNull(nextStartedActivity());
        assertFalse(controller.get().isFinishing());

        controller.resume().visible().windowFocusChanged(true);
        advance(2);
        assertHandoff(CURRENT);
        assertNull(nextStartedActivity());
    }

    @Test
    public void missingLauncherReachesVisibleBoundedRecovery() {
        launch("6.39");
        advance(1);
        assertStatus(R.string.waiting_install);
        assertNull(nextStartedActivity());

        shadowOf(Looper.getMainLooper()).idleFor(
                Duration.ofMillis(RestartPolicy.TIMEOUT_MILLIS + 1));
        assertStatus(R.string.update_timeout);
        MainActivity activity = controller.get();
        assertFalse(activity.isFinishing());
        assertEquals(View.GONE, activity.findViewById(R.id.progress).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.retry).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.open_launcher).getVisibility());
        assertNull(nextStartedActivity());
    }

    @Test
    public void temporaryCurrentPackageAbsenceDoesNotLaunchUnrelatedLegacyVersion() {
        installLauncher(CURRENT, "6.39", 1);
        installLauncher(LEGACY, "5.0", 1);
        launch("6.39");
        advance(1);
        // removePackage alone leaves Robolectric's activity-filter registry pointing at the package.
        packages.removeActivity(new ComponentName(CURRENT, CURRENT + ".LauncherActivity"));
        packages.removePackage(CURRENT);
        advance(2);
        assertStatus(R.string.waiting_install);
        assertNull("An unrelated legacy version does not prove the update completed", nextStartedActivity());

        installLauncher(CURRENT, "6.40", 2);
        advance(2);
        assertHandoff(CURRENT);
        assertNull(nextStartedActivity());
    }

    @Test
    public void recreationRetainsPinnedLegacyPackageWhenCurrentLauncherAppears() {
        installLauncher(LEGACY, "5.0", 1);
        launch("5.0");
        advance(1);
        installLauncher(CURRENT, "6.39", 1);

        // A new instance must restore its existing pin instead of preferring the new current app.
        Bundle state = new Bundle();
        controller.windowFocusChanged(false).pause().saveInstanceState(state).stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class, intent("5.0"))
                .setup(state).visible().windowFocusChanged(true);
        advance(2);
        assertStatus(R.string.waiting_update);
        assertNull(nextStartedActivity());

        installLauncher(LEGACY, "5.1", 2);
        advance(2);
        assertHandoff(LEGACY);
        assertNull(nextStartedActivity());
    }

    private void launch(String oldVersion) {
        controller = Robolectric.buildActivity(MainActivity.class, intent(oldVersion))
                .setup().visible().windowFocusChanged(true);
    }

    private static Intent intent(String oldVersion) {
        return new Intent(RuntimeEnvironment.getApplication(), MainActivity.class)
                .putExtra("oldVersion", oldVersion);
    }

    private void installLauncher(String packageName, String version, int code) {
        ApplicationInfo application = new ApplicationInfo();
        application.packageName = packageName;
        application.enabled = true;
        application.uid = CURRENT.equals(packageName) ? 12001 : 12002;
        ActivityInfo activity = new ActivityInfo();
        activity.packageName = packageName;
        activity.name = packageName + ".LauncherActivity";
        activity.applicationInfo = application;
        activity.enabled = true;
        activity.exported = true;
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.versionName = version;
        info.versionCode = code;
        info.applicationInfo = application;
        // Register the component after the package: pre-populating activities makes
        // Robolectric return early before creating the component's intent-filter map.
        packages.installPackage(info);
        packages.addOrUpdateActivity(activity);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);
        packages.addIntentFilterForActivity(new ComponentName(packageName, activity.name), filter);
    }

    private static void advance(long seconds) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds));
    }

    private Intent nextStartedActivity() {
        return shadowOf(controller.get()).getNextStartedActivity();
    }

    private void assertStatus(int stringResource) {
        MainActivity activity = controller.get();
        assertEquals(activity.getString(stringResource),
                ((TextView) activity.findViewById(R.id.status)).getText().toString());
    }

    private void assertHandoff(String packageName) {
        Intent started = nextStartedActivity();
        assertNotNull("The updated supported launcher must be opened", started);
        assertNotNull(started.getComponent());
        assertEquals(packageName, started.getComponent().getPackageName());
        assertTrue(controller.get().isFinishing());
    }
}
