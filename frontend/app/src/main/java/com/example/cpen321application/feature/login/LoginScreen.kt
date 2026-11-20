package com.example.cpen321application.feature.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cpen321application.core.auth.User
import com.example.cpen321application.core.ui.components.ButtonStyle
import com.example.cpen321application.core.ui.components.DonutButton
import com.example.cpen321application.core.ui.components.DonutMark
import com.example.cpen321application.core.ui.components.Sprinkle
import com.example.cpen321application.core.ui.components.SprinkleTone
import com.example.cpen321application.core.ui.components.Sprinkles
import com.example.cpen321application.core.ui.components.Wordmark
import com.example.cpen321application.core.ui.theme.DonutTheme
import com.example.cpen321application.core.ui.theme.sticker

// The sign-in screen's confetti, on the design's 360 × 640 frame
private const val DESIGN_HEIGHT = 640f
private val SPRINKLES = listOf(
    Sprinkle(40f, 90f, 30f, SprinkleTone.CLAY),
    Sprinkle(290f, 130f, -40f, SprinkleTone.GRASS),
    Sprinkle(70f, 470f, 60f, SprinkleTone.BALL),
    Sprinkle(270f, 420f, 15f, SprinkleTone.CLAY),
    Sprinkle(30f, 300f, -20f, SprinkleTone.BLUE),
    Sprinkle(300f, 300f, 70f, SprinkleTone.BALL),
    Sprinkle(160f, 70f, -10f, SprinkleTone.GRASS),
)
private const val LOGO_TILT = -8f
private const val SQUIGGLE_GRID = 120f
private const val SQUIGGLE_ALPHA = 0.5f

@Composable
fun LoginRoute(
    serverClientId: String,
    onLoginSuccess: (User) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LoginScreen(
        uiState = uiState,
        onSignInClick = {
            viewModel.signIn(context, serverClientId, onLoginSuccess)
        },
        modifier = modifier
    )
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Decorative: confetti and a grass squiggle, placed on the design's 360 × 640 frame
        Sprinkles(SPRINKLES, modifier = Modifier.fillMaxSize(), designHeight = DESIGN_HEIGHT)
        Squiggle(
            modifier = Modifier
                .offset(x = (-10).dp, y = 180.dp)
                .size(width = 120.dp, height = 40.dp)
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LogoSticker()
            Wordmark(fontSize = 64.sp)
            Text(
                text = "Serve up a game near you.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SignInFooter(uiState = uiState, onSignInClick = onSignInClick, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// The donut on a clay circle, tilted like a sticker, with a thicker border and a 5 dp shadow
@Composable
private fun LogoSticker() {
    val donut = DonutTheme.colors
    Box(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .size(148.dp)
            .rotate(LOGO_TILT)
            .sticker(CircleShape, donut.stickerBorder, donut.stickerShadow, offset = 5.dp, width = 2.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        DonutMark(size = 132.dp)
    }
}

// A wavy grass line at half strength
@Composable
private fun Squiggle(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.secondary.copy(alpha = SQUIGGLE_ALPHA)
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        // Drawn on the design's 120 × 40 grid
        val unit = size.width / SQUIGGLE_GRID
        val path = Path().apply {
            moveTo(2 * unit, 20 * unit)
            relativeCubicTo(15 * unit, -18 * unit, 30 * unit, 18 * unit, 45 * unit, 0f)
            relativeCubicTo(15 * unit, -18 * unit, 30 * unit, 18 * unit, 45 * unit, 0f)
            relativeCubicTo(15 * unit, -18 * unit, 20 * unit, -10 * unit, 26 * unit, -6 * unit)
        }
        drawPath(path, color = color, style = Stroke(width = 4 * unit, cap = StrokeCap.Round))
    }
}

@Composable
private fun SignInFooter(uiState: LoginUiState, onSignInClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
        DonutButton(
            onClick = onSignInClick,
            enabled = !uiState.isLoading,
            style = ButtonStyle.SECONDARY,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Sign in with Google")
            }
        }
        Text(
            text = "Find a game, meet players, climb the ladder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
