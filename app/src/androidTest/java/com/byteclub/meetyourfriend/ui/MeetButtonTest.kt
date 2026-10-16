package com.byteclub.meetyourfriend.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MeetButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun routesSingleDoubleAndLongPressGestures() {
        var taps = 0
        var doubles = 0
        var longPresses = 0
        composeRule.setContent {
            MeetButton(
                onTap = { taps++ },
                onDoubleTap = { doubles++ },
                onLongPress = { longPresses++ },
            )
        }
        val button = composeRule.onNodeWithContentDescription("Meet button")
        button.performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500L)
        assertEquals(1, taps)
        assertEquals(0, doubles)
        assertEquals(0, longPresses)

        button.performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertEquals(1, taps)
        assertEquals(1, doubles)
        assertEquals(0, longPresses)

        button.performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals(1, taps)
        assertEquals(1, doubles)
        assertEquals(1, longPresses)
    }
}
