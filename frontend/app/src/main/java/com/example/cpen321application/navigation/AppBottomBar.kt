package com.example.cpen321application.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.cpen321application.core.ui.components.StickerSurface
import com.example.cpen321application.core.ui.components.rememberReducedMotion

// Play first: finding a match is what the app is for
private val TABS = listOf(
    BottomNavItem.Matchmaking,
    BottomNavItem.ScheduledGame,
    BottomNavItem.Leaderboard,
    BottomNavItem.Profile
)

// The open tab's pill grows to fit its label on spring(0.6, 500)
private const val PILL_DAMPING = 0.6f
private const val PILL_STIFFNESS = 500f

/**
 * The floating pill bar: a white sticker 12 dp in from the sides. Only the open tab shows its
 * label, inside a clay pill; the others are icons that TalkBack reads by name.
 */
@Composable
fun AppBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
    ) {
        StickerSurface(modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TABS.forEach { item ->
                    NavPill(
                        item = item,
                        isSelected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigateToTab(item.route)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPill(item: BottomNavItem, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion()
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (isSelected) colors.primary else colors.surfaceContainerLowest)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .animateContentSize(if (reducedMotion) snap() else spring<IntSize>(PILL_DAMPING, PILL_STIFFNESS))
            .padding(start = 13.dp, end = if (isSelected) 16.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(if (isSelected) item.selectedIcon else item.icon),
            contentDescription = item.title,
            tint = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        if (isSelected) {
            // The icon already names the tab for TalkBack
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onPrimary,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * Opens a bottom bar tab the way tapping it does: one copy of each tab, each keeping its state
 */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
