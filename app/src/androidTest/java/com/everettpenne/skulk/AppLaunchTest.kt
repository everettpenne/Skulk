package com.everettpenne.skulk

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/**
 * The real app from launch through its tabs. It exists because a glass control that sampled a layer containing itself made the
 * renderer recurse until the process died, and that only happens with the whole app composed together. Nothing here touches the
 * network on purpose: the feed may load or show Reddit's refusal, and either is fine — the checks are on the app's own chrome.
 */
class AppLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun settle() { compose.waitForIdle(); Thread.sleep(1500); compose.waitForIdle() }

    @Test fun everyTabOpens() {
        settle()
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithContentDescription("Saved").performClick(); settle()
        compose.onNodeWithText("Nothing saved yet").assertExists()
        compose.onNodeWithContentDescription("Search").performClick(); settle()
        compose.onNodeWithText("Search Subreddits").assertExists()
        compose.onNodeWithContentDescription("Feed").performClick(); settle()
        compose.onNodeWithText("Home").assertExists()
    }

    @Test fun settingsOpensAndTheSwitchesWork() {
        settle()
        compose.onNodeWithContentDescription("Settings").performClick(); settle()
        compose.onNodeWithText("Autoplay media").assertExists()
        compose.onNodeWithText("Color coded comments").assertExists()
        compose.onNodeWithText("# of comments to load").assertExists()
        compose.onNodeWithText("Device default").assertExists()
        compose.onAllNodesWithContentDescription("Off")[0].performClick(); settle()
        compose.onNodeWithText("Autoplay media").assertExists()
    }
}
