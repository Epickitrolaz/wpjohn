package io.wpreborn.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.wpreborn.app.BootState
import io.wpreborn.app.WorldViewModel

@Composable
fun AppRoot(vm: WorldViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Ink, Night, Color(0xFF0D1428), Night)
                )
            ),
    ) {
        when (val boot = vm.boot) {
            is BootState.Loading -> LoadingScreen()
            is BootState.Failed -> ErrorScreen(boot.message, onRetry = vm::load)
            is BootState.Ready -> {
                if (vm.runtime != null) {
                    BackHandler { vm.closeMap() }
                    MapScreen(vm)
                } else {
                    HomeScreen(vm)
                }
            }
        }

        if (vm.busy) {
            BusyOverlay(vm.busyLabel)
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun LoadingScreen() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(listOf(Periwinkle, Color(0xFF4C6BE0)))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("WP", color = Color(0xFF0A1230), fontWeight = FontWeight.Bold, fontSize = 30.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "World Provinces",
            style = MaterialTheme.typography.headlineMedium,
            color = Mist,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Preparing 4,658 provinces",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(alpha),
        )
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = Gold,
            strokeWidth = 2.5.dp,
        )
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.headlineSmall, color = Mist)
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            color = Muted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun BusyOverlay(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceNight)
                .padding(horizontal = 34.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(30.dp), color = Gold, strokeWidth = 3.dp)
            Spacer(Modifier.height(18.dp))
            Text(label, color = Mist, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
