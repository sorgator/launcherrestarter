package com.hmdm.emuilauncherrestarter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/** Visible handoff across launcher replacement, including non-device-owner Android 12. */
public class MainActivity extends Activity {
    private static final String LOG_TAG = "SuntelMdmHelper";
    private static final String[] LAUNCHER_PACKAGES = {"com.hmdm.launcher", "ru.headwind.kiosk"};
    private static final String OLD_VERSION = "oldVersion";
    private static final String STARTED_AT = "startedAt";
    private static final String SELECTED_PACKAGE = "selectedPackage";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String oldLauncherVersion;
    private long startedAt;
    private String selectedPackage;
    private boolean resumed;
    private boolean launching;
    private TextView status;
    private View progress;
    private View retryButton;
    private View openButton;

    private final Runnable check = this::checkLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.helper_root);
        // Target 35 uses edge-to-edge. Keep controls clear of system bars on every SDK.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        retryButton = findViewById(R.id.retry);
        openButton = findViewById(R.id.open_launcher);
        retryButton.setOnClickListener(view -> beginWait());
        openButton.setOnClickListener(view -> openLauncher(findLauncher()));
        oldLauncherVersion = savedInstanceState == null
                ? getIntent().getStringExtra(OLD_VERSION) : savedInstanceState.getString(OLD_VERSION);
        startedAt = savedInstanceState == null ? SystemClock.elapsedRealtime()
                : savedInstanceState.getLong(STARTED_AT, SystemClock.elapsedRealtime());
        selectedPackage = savedInstanceState == null ? selectLauncherPackage()
                : savedInstanceState.getString(SELECTED_PACKAGE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        oldLauncherVersion = intent.getStringExtra(OLD_VERSION);
        selectedPackage = selectLauncherPackage();
        beginWait();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        scheduleCheck();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(check);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        handler.removeCallbacks(check);
        if (hasFocus) {
            scheduleCheck();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString(OLD_VERSION, oldLauncherVersion);
        state.putLong(STARTED_AT, startedAt);
        state.putString(SELECTED_PACKAGE, selectedPackage);
        super.onSaveInstanceState(state);
    }

    private void beginWait() {
        startedAt = SystemClock.elapsedRealtime();
        launching = false;
        progress.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        openButton.setVisibility(View.GONE);
        status.setText(R.string.waiting_update);
        scheduleCheck();
    }

    private void scheduleCheck() {
        handler.removeCallbacks(check);
        if (resumed && hasWindowFocus() && !launching) {
            handler.postDelayed(check, 1000);
        }
    }

    private void checkLauncher() {
        if (!resumed || !hasWindowFocus() || launching || isFinishing()) {
            return;
        }
        Launcher launcher = findLauncher();
        RestartPolicy.Decision decision = RestartPolicy.decide(launcher != null,
                oldLauncherVersion, launcher == null ? null : launcher.version,
                SystemClock.elapsedRealtime() - startedAt);
        switch (decision) {
            case READY:
                openLauncher(launcher);
                break;
            case TIMED_OUT:
                showRecovery(R.string.update_timeout);
                break;
            default:
                status.setText(decision == RestartPolicy.Decision.WAIT_FOR_INSTALL
                        ? R.string.waiting_install : R.string.waiting_update);
                scheduleCheck();
        }
    }

    private Launcher findLauncher() {
        return launcherForPackage(selectedPackage);
    }

    private String selectLauncherPackage() {
        // A second installed launcher must never look like a completed update of the first.
        Launcher current = launcherForPackage(LAUNCHER_PACKAGES[0]);
        Launcher legacy = launcherForPackage(LAUNCHER_PACKAGES[1]);
        return LauncherSelection.select(current != null, legacy != null,
                legacy == null ? null : legacy.version, oldLauncherVersion);
    }

    private Launcher launcherForPackage(String packageName) {
        if (!LAUNCHER_PACKAGES[0].equals(packageName) && !LAUNCHER_PACKAGES[1].equals(packageName)) {
            return null;
        }
        PackageManager manager = getPackageManager();
        try {
            Intent intent = manager.getLaunchIntentForPackage(packageName);
            if (intent != null) {
                PackageInfo info = manager.getPackageInfo(packageName, 0);
                return new Launcher(intent, info.versionName);
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Package replacement can temporarily remove the launcher from the query result.
        }
        return null;
    }

    private void openLauncher(Launcher launcher) {
        if (!resumed || !hasWindowFocus() || launching) {
            return;
        }
        if (launcher == null) {
            showRecovery(R.string.launcher_unavailable);
            return;
        }
        try {
            launching = true;
            status.setText(R.string.opening_launcher);
            launcher.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launcher.intent);
            finish();
        } catch (ActivityNotFoundException | SecurityException failure) {
            launching = false;
            Log.w(LOG_TAG, "Launcher handoff unavailable");
            showRecovery(R.string.launcher_unavailable);
        }
    }

    private void showRecovery(int message) {
        handler.removeCallbacks(check);
        progress.setVisibility(View.GONE);
        retryButton.setVisibility(View.VISIBLE);
        openButton.setVisibility(View.VISIBLE);
        status.setText(message);
    }

    private static final class Launcher {
        final Intent intent;
        final String version;
        Launcher(Intent intent, String version) {
            this.intent = intent;
            this.version = version;
        }
    }
}
