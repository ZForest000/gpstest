package com.example.gpstest.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.gpstest.ui.theme.GPSTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GpsCardTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun standardCardDisplaysItsContent() {
        composeTestRule.setContent {
            GPSTestTheme(darkTheme = false) {
                GpsCard(modifier = Modifier.testTag("standard-card")) {
                    Text("标准卡片内容")
                }
            }
        }

        composeTestRule.onNodeWithTag("standard-card").assertIsDisplayed()
        composeTestRule.onNodeWithText("标准卡片内容").assertIsDisplayed()
    }

    @Test
    fun compactCardDisplaysItsContent() {
        composeTestRule.setContent {
            GPSTestTheme(darkTheme = false) {
                GpsCard(
                    modifier = Modifier.testTag("compact-card"),
                    density = GpsCardDensity.COMPACT,
                ) {
                    Text("紧凑卡片内容")
                }
            }
        }

        composeTestRule.onNodeWithTag("compact-card").assertIsDisplayed()
        composeTestRule.onNodeWithText("紧凑卡片内容").assertIsDisplayed()
    }

    @Test
    fun clickableCompactCardInvokesCallbackOnce() {
        var clickCount = 0

        composeTestRule.setContent {
            GPSTestTheme(darkTheme = false) {
                GpsCard(
                    modifier = Modifier.testTag("clickable-compact-card"),
                    density = GpsCardDensity.COMPACT,
                    onClick = { clickCount++ },
                ) {
                    Text("可点击紧凑卡片")
                }
            }
        }

        composeTestRule.onNodeWithTag("clickable-compact-card").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, clickCount)
        }
    }
}
