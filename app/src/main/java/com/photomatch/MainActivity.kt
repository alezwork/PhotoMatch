package com.photomatch

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.photomatch.ui.screens.DeckScreen
import com.photomatch.ui.screens.FavoritesScreen
import com.photomatch.ui.screens.GroupScreen
import com.photomatch.ui.screens.HomeScreen
import com.photomatch.ui.screens.OnboardingScreen
import com.photomatch.ui.screens.SimilarScreen
import com.photomatch.ui.screens.TimelineScreen
import com.photomatch.ui.screens.TrashScreen
import com.photomatch.ui.theme.Pm
import com.photomatch.ui.theme.PhotoMatchTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val mediaPermissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
        }

    private fun hasMediaAccess(): Boolean {
        fun g(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 34 -> g(Manifest.permission.READ_MEDIA_IMAGES) || g(Manifest.permission.READ_MEDIA_VIDEO) || g(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> g(Manifest.permission.READ_MEDIA_IMAGES) || g(Manifest.permission.READ_MEDIA_VIDEO)
            else -> g(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PhotoMatchTheme {
                val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    vm.onPermission(hasMediaAccess())
                }
                val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    vm.onTrashResult(it.resultCode == Activity.RESULT_OK)
                }
                val favLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

                LaunchedEffect(Unit) { if (hasMediaAccess()) vm.onPermission(true) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    if (vm.screen != Screen.Onboarding && !hasMediaAccess()) vm.onPermission(false)
                }

                BackHandler(enabled = vm.screen != Screen.Home && vm.screen != Screen.Onboarding) { vm.back() }

                Box(Modifier.fillMaxSize().background(Pm.Night)) {
                    AppContent(
                        vm,
                        onGrant = { permLauncher.launch(mediaPermissions) },
                        onDelete = { vm.trashRequest()?.let { trashLauncher.launch(IntentSenderRequest.Builder(it).build()) } },
                        onSyncFavorites = { vm.favoriteRequest()?.let { favLauncher.launch(IntentSenderRequest.Builder(it).build()) } },
                    )
                    // soft fade under the status bar so scrolled content never collides with the clock
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(Brush.verticalGradient(listOf(Pm.Night, Pm.Night.copy(alpha = 0.85f))))
                    )
                }
            }
        }
    }
}

private fun depth(s: Screen) = when (s) {
    Screen.Onboarding -> 0
    Screen.Home -> 1
    Screen.Deck -> 3
    is Screen.Group -> 3
    else -> 2
}

@Composable
private fun AppContent(vm: AppViewModel, onGrant: () -> Unit, onDelete: () -> Unit, onSyncFavorites: () -> Unit) {
    AnimatedContent(
        targetState = vm.screen,
        transitionSpec = {
            val forward = depth(targetState) >= depth(initialState)
            if (targetState == Screen.Deck || initialState == Screen.Deck) {
                (fadeIn(tween(320)) + scaleIn(tween(380), initialScale = if (forward) 0.92f else 1.06f)) togetherWith
                    (fadeOut(tween(200)) + scaleOut(tween(300), targetScale = if (forward) 1.04f else 0.94f))
            } else {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(360)) { it / 5 * dir } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(360)) { -it / 8 * dir } + fadeOut(tween(200)))
            }
        },
        label = "screens",
    ) { s ->
        Box(Modifier.fillMaxSize().background(Pm.Night)) {
            when (s) {
                Screen.Onboarding -> OnboardingScreen(onGrant)
                Screen.Home -> if (vm.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Pm.Mint)
                } else HomeScreen(vm)
                Screen.Deck -> DeckScreen(vm)
                Screen.Similar -> SimilarScreen(vm)
                is Screen.Group -> GroupScreen(vm, s.id)
                Screen.Timeline -> TimelineScreen(vm)
                Screen.Trash -> TrashScreen(vm, onDelete)
                Screen.Favorites -> FavoritesScreen(vm, onSyncFavorites)
            }
        }
    }
}
