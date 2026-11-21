package com.donut.app.feature.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.donut.app.core.auth.User
import com.donut.app.core.ui.components.DonutMark
import com.donut.app.core.ui.components.LargeButtonHeight
import com.donut.app.core.ui.components.Wordmark

// The court lines behind the logo are barely there: ink at 5.5%
private const val COURT_ALPHA = 0.055f
// A doubles court seen from above: 23.77 m long, 10.97 m wide
private const val COURT_ASPECT = 23.77f / 10.97f
// Doubles alleys are 1.37 m of the 10.97 m width; service lines are 6.40 m from the net
private const val ALLEY = 1.37f / 10.97f
private const val SERVICE = 6.40f / 23.77f

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
    val ink = MaterialTheme.colorScheme.onSurface
    Box(modifier = modifier.fillMaxSize()) {
        // Decorative: a court on its side behind the logo
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(1f / COURT_ASPECT)
        ) { drawCourt(ink.copy(alpha = COURT_ALPHA)) }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DonutMark(size = 84.dp)
            Wordmark(fontSize = 52.sp)
            Text(
                text = "Tennis, on demand.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SignInFooter(uiState = uiState, onSignInClick = onSignInClick, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SignInFooter(uiState: LoginUiState, onSignInClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
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
        Button(
            onClick = onSignInClick,
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(LargeButtonHeight)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Sign in with Google")
            }
        }
        Text(
            text = "Singles & doubles · Elo-rated · Vancouver",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// The outline, both alleys, the service boxes and the net of a court standing on its end
private fun DrawScope.drawCourt(paint: Color) {
    val stroke = 1.5.dp.toPx()
    val width = size.width
    val height = size.height
    val alley = width * ALLEY
    val service = height * SERVICE
    val net = height / 2

    drawRect(paint, topLeft = Offset.Zero, size = Size(width, height), style = Stroke(stroke))
    drawLine(paint, Offset(alley, 0f), Offset(alley, height), stroke)
    drawLine(paint, Offset(width - alley, 0f), Offset(width - alley, height), stroke)
    drawLine(paint, Offset(alley, net - service), Offset(width - alley, net - service), stroke)
    drawLine(paint, Offset(alley, net + service), Offset(width - alley, net + service), stroke)
    drawLine(paint, Offset(width / 2, net - service), Offset(width / 2, net + service), stroke)
    drawLine(paint, Offset(-stroke * 4, net), Offset(width + stroke * 4, net), stroke * 2)
}
