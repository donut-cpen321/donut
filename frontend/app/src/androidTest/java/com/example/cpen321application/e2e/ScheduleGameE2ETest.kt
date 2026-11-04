package com.example.cpen321application.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * End-to-end test of scheduling a game: runs the whole app against the real backend at API_BASE_URL.
 *
 * Before running it:
 * - start the backend and its database (`docker compose up -d` in the repository root)
 * - add a Google account to the device (Settings → Passwords & accounts). The test signs in with it,
 *   since running the tests reinstalls the app and clears any saved session.
 *
 * The test schedules a singles game one hour from now, finds it under My games, cancels it and
 * checks it is gone, so it leaves no game behind when it passes.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleGameE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scheduleGame_thenCancelIt() {
        openGamesTab()
        val (startsAt, court) = scheduleGame()

        // Created: the app opens the new game
        waitForText("Cancel game")
        composeRule.onAllNodesWithText(startsAt).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText(court).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Singles", substring = true).onFirst().assertIsDisplayed()

        // The game is listed under My games
        composeRule.onNode(hasContentDescription("Back")).performClick()
        showMyGames()
        waitForText(startsAt)

        cancelGame(startsAt)

        // Cancelled: back on the list, and the game is no longer in it
        showMyGames()
        composeRule.waitUntil(TIMEOUT_MS) { countText(startsAt) == 0 }
    }

    private fun openGamesTab() {
        val signedIn = hasText("Games") and hasClickAction()
        val signedOut = hasText("Sign in with Google")
        composeRule.waitUntil(TIMEOUT_MS) { count(signedIn) > 0 || count(signedOut) > 0 }
        if (count(signedOut) > 0) {
            signIn(signedIn)
        }
        composeRule.onNode(signedIn).performClick()
        waitForText("Schedule")
    }

    /**
     * Taps Sign in with Google. Google signs in on its own when the device has one account that used
     * the app before; otherwise it shows its account sheet, outside the app, where UiAutomator taps
     * the first account.
     */
    private fun signIn(signedIn: SemanticsMatcher) {
        composeRule.onNodeWithText("Sign in with Google").performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val deadline = System.currentTimeMillis() + SIGN_IN_TIMEOUT_MS
        while (count(signedIn) == 0) {
            if (System.currentTimeMillis() > deadline) {
                fail("Not signed in after 30 s: add a Google account to the device (Settings → Passwords & accounts)")
            }
            val account = device.findObject(By.textContains("@").pkg(Pattern.compile("^(?!$APP_PACKAGE$).*")))
                ?: device.findObject(By.text(Pattern.compile("Continue|Continue as .*")))
            account?.click()
            Thread.sleep(POLL_MS)
        }
    }

    /**
     * Fills in the Schedule a game form with the default time (one hour from now) and the first court
     * @return the start time and the court name as the app shows them
     */
    private fun scheduleGame(): Pair<String, String> {
        composeRule.onNodeWithText("Schedule").performClick()
        composeRule.onNodeWithText("Game").performClick()
        waitForText("Schedule a game")

        composeRule.onNodeWithText("Singles").performClick()

        composeRule.onNodeWithText("Pick a date and time").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("OK").performClick()
        val startsAt = textOf(hasStartTime)

        composeRule.onNodeWithText("Pick a court").performClick()
        val courtOption = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        composeRule.waitUntil(TIMEOUT_MS) { count(courtOption) > 0 }
        val court = composeRule.onAllNodes(courtOption)[0].fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text
        composeRule.onAllNodes(courtOption)[0].performClick()

        composeRule.onNodeWithText(court).assertIsDisplayed()  // shown on the court button
        composeRule.onNode(hasText("Schedule game") and hasClickAction()).performClick()
        return startsAt to court
    }

    private fun cancelGame(startsAt: String) {
        composeRule.onNodeWithText(startsAt, useUnmergedTree = true).performClick()
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

        // A start time as formatStartsAt shows it, e.g. "Tue, Nov 3 · 4:15 PM"
        val START_TIME = Regex("""\p{L}+, \p{L}+ \d{1,2} · \d{1,2}:\d{2}.*""")

        val hasStartTime = SemanticsMatcher("shows a start time") { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { START_TIME.matches(it.text) }
        }
    }
}
