package com.everettpenne.skulk

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.everettpenne.skulk.data.AppIcon
import com.everettpenne.skulk.data.AppIcons
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Names of the launcher entries for this app: exactly one must exist whichever icon is picked, or the app vanishes from the launcher. */
    private fun launcherEntries() = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0).map { it.activityInfo.name.substringAfterLast('.') }

    @Test fun switchingLeavesExactlyOneLauncherEntry() {
        try {
            for (icon in AppIcon.entries) {
                AppIcons.select(context, icon)
                assertEquals(listOf(icon.alias), launcherEntries())
                assertEquals(icon, AppIcons.current(context))
            }
        } finally { AppIcons.select(context, AppIcon.Sandworm) }
        assertEquals(listOf("IconSandworm"), launcherEntries())
    }
}
