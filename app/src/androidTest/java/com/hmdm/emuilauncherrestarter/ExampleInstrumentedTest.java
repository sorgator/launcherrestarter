package com.hmdm.emuilauncherrestarter;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void legacyCallerCanResolveExportedEntryPoint() throws Exception {
        String packageName = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        assertEquals("com.hmdm.emuilauncherrestarter", packageName);
        PackageManager manager = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager();
        assertNotNull(manager.getLaunchIntentForPackage(packageName));
        PackageInfo info = manager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
        ActivityInfo activity = info.activities[0];
        assertEquals("com.hmdm.emuilauncherrestarter.MainActivity", activity.name);
        assertTrue(activity.exported);
        assertEquals(35, info.applicationInfo.targetSdkVersion);
    }
}
