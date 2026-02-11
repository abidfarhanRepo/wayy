package com.wayy.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Ignore("Requires MapLibre, permissions, and live location on device")
    @Test
    fun mainScreen_rendersQuickActions() {
        composeRule.setContent {
            MainNavigationScreen()
        }
        composeRule.onNodeWithText("Navigate").assertExists()
    }
}
