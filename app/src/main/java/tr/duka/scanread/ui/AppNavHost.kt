package tr.duka.scanread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tr.duka.scanread.ui.about.AboutScreen
import tr.duka.scanread.ui.home.HomeRoute
import tr.duka.scanread.ui.languages.LanguagesScreen
import tr.duka.scanread.ui.onboarding.OnboardingRoute
import tr.duka.scanread.ui.settings.SettingsScreen
import tr.duka.scanread.ui.support.SupportRoute

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LANGUAGES = "languages"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val SUPPORT = "support"
}

/** The app's screens. The first-run guide is shown until it has been completed once. */
@Composable
fun AppNavHost(viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings
    if (loaded == null) {
        // Settings load in a moment; avoid flashing the wrong start screen.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = if (loaded.onboardingDone) Routes.HOME else Routes.ONBOARDING,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingRoute(
                viewModel = viewModel,
                onFinished = {
                    viewModel.completeOnboarding()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }
        composable(Routes.HOME) {
            HomeRoute(
                viewModel = viewModel,
                onOpenLanguages = { navController.navigate(Routes.LANGUAGES) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSupport = { navController.navigate(Routes.SUPPORT) },
            )
        }
        composable(Routes.LANGUAGES) { LanguagesScreen(viewModel = viewModel, onBack = back) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = back,
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenSupport = { navController.navigate(Routes.SUPPORT) },
            )
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = back) }
        composable(Routes.SUPPORT) { SupportRoute(onBack = back) }
    }
}
