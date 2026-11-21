package com.example.cpen321application.e2e

import android.text.format.DateFormat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.example.cpen321application.MainActivity
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.util.regex.Pattern

/**
 * End-to-end test of scheduling a game: runs the whole app against the real backend at API_BASE_URL.
 *
 * Before running it:
 * - start the backend and its database (`docker compose up -d` in the repository root)
 * - add a Google account to the device (Settings → Passwords & accounts). The test signs in with it,
 *   since running the tests reinstalls the app and clears any saved session.
 *
 * The main test schedules a singles game one hour from now, finds it under My games, cancels it and
 * checks it is gone, so it leaves no game behind when it passes. The failure tests create no game.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleGameE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scheduleGame_thenCancelIt() {
        openGamesTab()
        val (startsAt, court) = scheduleSinglesGameInOneHourAtFirstCourt()

        // Created: the app opens the new game
        waitForText("Cancel game")
        composeRule.onAllNodesWithText(startsAt).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText(court).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Singles", substring = true).onFirst().assertIsDisplayed()

        // The game is listed under My games. Its card shows the time; the day is in the header above it
        val cardTime = startsAt.substringAfter(" · ")
        composeRule.onNode(hasContentDescription("Back")).performClick()
        showMyGames()
        waitForText(cardTime)

        cancelGame(cardTime)

        // Cancelled: back on the list, and the game is no longer in it
        showMyGames()
        composeRule.waitUntil(TIMEOUT_MS) { countText(cardTime) == 0 }
    }

    // Failure scenario: the form isn't complete, so "Schedule game" can't be tapped
    @Test
    fun incompleteForm_cantBeSubmitted() {
        openGamesTab()
        openScheduleForm()
        composeRule.onNode(scheduleButton).assertIsNotEnabled()

        // A start time but no court yet
        pickDefaultStartTime()
        composeRule.onNode(scheduleButton).assertIsNotEnabled()
    }

    // Failure scenario 2a: the start time isn't in the future, so the app asks for one and sends nothing
    @Test
    fun startTimeInThePast_isRejected() {
        // Today, an hour ago (or at midnight just after it): the picker keeps the current minute.
        // After 11 PM the picker opens on tomorrow, so the test is skipped then
        val now = LocalTime.now()
        assumeTrue("the picker opens on tomorrow after 11 PM", now.hour < 23)
        val hour = maxOf(now.hour - 1, 0)

        openGamesTab()
        openScheduleForm()
        composeRule.onNodeWithText("Pick a date and time").performClick()
        composeRule.onNodeWithText("Next").performClick()
        pickHour(hour)
        composeRule.onNodeWithText("OK").performClick()
        pickFirstCourt()

        composeRule.onNode(scheduleButton).performScrollTo().performClick()

        waitForText("Pick a start time in the future.")
        composeRule.onNodeWithText("Schedule a game").assertIsDisplayed()  // still on the form
    }

    private fun openGamesTab() {
        val signedIn = hasText("Games") and hasClickAction()
        val signedOut = hasText("Sign in with Google")
        composeRule.waitUntil(TIMEOUT_MS) { count(signedIn) > 0 || count(signedOut) > 0 }
        if (count(signedOut) > 0) {
            signInWithDeviceGoogleAccount(signedIn)
        }
        composeRule.onNode(signedIn).performClick()
        waitForText("Schedule")
    }

    private fun signInWithDeviceGoogleAccount(signedIn: SemanticsMatcher) {
        composeRule.onNodeWithText("Sign in with Google").performClick()
        // Google signs in on its own when the device has one account that used the app before.
        // Otherwise it shows its account sheet, outside the app, where UiAutomator taps the first account.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val deadline = System.currentTimeMillis() + SIGN_IN_TIMEOUT_MS
        while (countWhileCovered(signedIn) == 0) {
            if (System.currentTimeMillis() > deadline) {
                fail("Not signed in after 30 s: add a Google account to the device (Settings → Passwords & accounts)")
            }
            val account = device.findObject(By.textContains("@").pkg(Pattern.compile("^(?!$APP_PACKAGE$).*")))
                ?: device.findObject(By.text(Pattern.compile("Continue|Continue as .*")))
            account?.click()
            Thread.sleep(POLL_MS)
        }
    }

    // Like count, but 0 while Google's sheet covers the app, when the app has no screen to search
    private fun countWhileCovered(matcher: SemanticsMatcher): Int =
        try {
            count(matcher)
        } catch (_: IllegalStateException) {
            0
        }

    private fun scheduleSinglesGameInOneHourAtFirstCourt(): ScheduledGame {
        openScheduleForm()
        composeRule.onNodeWithText("Singles").performClick()
        val startsAt = pickDefaultStartTime()
        val court = pickFirstCourt()
        composeRule.onNode(scheduleButton).performScrollTo().performClick()
        return ScheduledGame(startsAt, court)
    }

    // Picks an hour (0-23) on the time picker's clock, and AM or PM when the phone uses a 12-hour clock
    private fun pickHour(hour: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dialHour = if (DateFormat.is24HourFormat(context)) {
            hour
        } else {
            composeRule.onNodeWithText(if (hour < 12) "AM" else "PM").performClick()
            if (hour % 12 == 0) 12 else hour % 12
        }
        composeRule.onNode(hasContentDescription("$dialHour o'clock", substring = true)).performClick()
    }

    private fun openScheduleForm() {
        composeRule.onNodeWithText("Schedule").performClick()
        composeRule.onNodeWithText("Game").performClick()
        waitForText("Schedule a game")
    }

    // Keeps the picker's default, one hour from now, and returns it as the form shows it
    private fun pickDefaultStartTime(): String {
        composeRule.onNodeWithText("Pick a date and time").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("OK").performClick()
        return textOf(hasStartTime)
    }

    // Picks the first court in the list and returns its name
    private fun pickFirstCourt(): String {
        composeRule.onNodeWithText("Pick a court").performClick()
        val courtOption = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and
            hasAnyAncestor(isDialog())  // not the Singles and Doubles buttons on the form behind it
        composeRule.waitUntil(TIMEOUT_MS) { count(courtOption) > 0 }
        val court = composeRule.onAllNodes(courtOption)[0].fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text
        composeRule.onAllNodes(courtOption)[0].performClick()
        val selectCourt = hasText("Select court") and hasClickAction()
        composeRule.waitUntil(TIMEOUT_MS) { count(selectCourt and isEnabled()) > 0 }
        composeRule.onNode(selectCourt).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { count(selectCourt) == 0 }  // the picker closed
        composeRule.onNodeWithText(court).assertIsDisplayed()  // shown on the court button
        return court
    }

    // The start time and court name as the app shows them
    private data class ScheduledGame(val startsAt: String, val court: String)

    // cardTime: the start time as the game's card in the list shows it, e.g. "4:15 PM"
    private fun cancelGame(cardTime: String) {
        composeRule.onNodeWithText(cardTime, useUnmergedTree = true).performClick()
        waitForText("Cancel game")

        composeRule.onNode(hasText("Cancel game") and hasClickAction()).performScrollTo().performClick()
        waitForText("Cancel this game?")
        composeRule.onNode(hasText("Cancel game") and hasAnyAncestor(isDialog())).performClick()
        waitForText("Schedule")  // back on the Games list
    }

    // Opens My games and reloads it, so it shows what the backend has now
    private fun showMyGames() {
        waitForText("My games")
        composeRule.onNodeWithText("My games").performClick()
        val refresh = hasContentDescription("Refresh") and isEnabled()
        composeRule.waitUntil(TIMEOUT_MS) { count(refresh) > 0 }
        composeRule.onNode(refresh).performClick()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { countText(text) > 0 }
    }

    // Text is matched in the unmerged tree, where each Text is its own node
    private fun countText(text: String) = count(hasText(text), useUnmergedTree = true)

    private fun count(matcher: SemanticsMatcher, useUnmergedTree: Boolean = false) =
        composeRule.onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().size

    private fun textOf(matcher: SemanticsMatcher): String =
        composeRule.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SIGN_IN_TIMEOUT_MS = 30_000L
        const val POLL_MS = 500L
        const val APP_PACKAGE = "com.example.cpen321application"

        val scheduleButton = hasText("Schedule game") and hasClickAction()

        // A start time as formatStartsAt shows it, e.g. "Tue, Nov 3 · 4:15 PM"
        val START_TIME = Regex("""\p{L}+, \p{L}+ \d{1,2} · \d{1,2}:\d{2}.*""")

        val hasStartTime = SemanticsMatcher("shows a start time") { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { START_TIME.matches(it.text) }
        }
    }
}
