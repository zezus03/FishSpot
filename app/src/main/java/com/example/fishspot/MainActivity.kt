package com.example.fishspot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember // Uvezen remember
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fishspot.data.AuthRepository
import com.example.fishspot.ui.theme.FishSpotTheme
import com.example.fishspot.userinterface.AddSpotScreen
import com.example.fishspot.userinterface.BotChatScreen
import com.example.fishspot.userinterface.LoginScreen
import com.example.fishspot.userinterface.MapScreen
import com.example.fishspot.userinterface.ProfileScreen
import com.example.fishspot.userinterface.RankingScreen
import com.example.fishspot.userinterface.RegisterScreen
import com.example.fishspot.userinterface.SpotsListScreen
import com.example.fishspot.viewmodel.AuthViewModel // Uvezen AuthViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            FishSpotTheme {
                val navController = rememberNavController()
                MainNavigation(navController, auth)
            }
        }
    }
}

@Composable
fun MainNavigation(navController: NavHostController, auth: FirebaseAuth) {
    val startDestination = if (auth.currentUser == null) "login" else "map"

    // Kreiramo po jednu instancu koju će ekrani deliti (sprečava stalno re-kreiranje objekata)
    val authRepository = remember { AuthRepository() }
    val authViewModel = remember { AuthViewModel() }

    NavHost(navController = navController, startDestination = startDestination) {

        // ─── LOGIN EKRAN ───
        composable("login") {
            LoginScreen(
                authRepository = authRepository,
                authViewModel = authViewModel, // Prosleđen ViewModel
                onLoginSuccess = {
                    navController.navigate("map") { popUpTo("login") { inclusive = true } }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }

        // ─── REGISTER EKRAN ───
        composable("register") {
            RegisterScreen(
                authRepository = authRepository,
                authViewModel = authViewModel, // Prosleđen ViewModel
                onRegistrationSuccess = {
                    navController.navigate("map") { popUpTo("login") { inclusive = true } }
                }
            )
        }

        // ─── MAP EKRAN ───
        composable("map") { backStackEntry ->
            val userIdToFocus by backStackEntry.savedStateHandle
                .getLiveData<String>("user_id_to_focus")
                .observeAsState()

            val spotIdToFocus by backStackEntry.savedStateHandle
                .getLiveData<String>("spot_id_to_focus")
                .observeAsState()

            MapScreen(
                focusOnUserCatch = userIdToFocus,
                focusOnSpotId = spotIdToFocus,
                onLogout = {
                    navController.navigate("login") { popUpTo(0) }
                },
                onNavigateToAddSpot = { lat, lng ->
                    navController.navigate("add_spot/$lat/$lng")
                },
                onNavigateToList = {
                    navController.navigate("spots_list")
                },
                onNavigateToRanking = {
                    navController.navigate("ranking")
                },
                onNavigateToProfile = {                      //
                    navController.navigate("profile")        // ← DODAJ OVE LINIJE (promeni "profile" u tvoj tačan route za profil ako se zove drugačije)
                },
                onNavigateToBotChat = { navController.navigate("bot_chat_screen") },
                onFocusDone = {
                    backStackEntry.savedStateHandle.remove<String>("user_id_to_focus")
                    backStackEntry.savedStateHandle.remove<String>("spot_id_to_focus")
                }
            )
        }

        // ─── SPOTS LIST EKRAN ───
        composable("spots_list") {
            SpotsListScreen(
                onNavigateBack = { navController.popBackStack() },
                onSpotSelected = { spotId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("spot_id_to_focus", spotId)
                    navController.popBackStack()
                }
            )
        }

        // ─── RANKING EKRAN ───
        composable("ranking") {
            RankingScreen(
                onNavigateBack = { navController.popBackStack() },
                onUserSelected = { userId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("user_id_to_focus", userId)
                    navController.popBackStack()
                }
            )
        }

        // ─── ADD SPOT EKRAN ───
        composable(
            route = "add_spot/{lat}/{lng}",
            arguments = listOf(
                navArgument("lat") { type = NavType.FloatType },
                navArgument("lng") { type = NavType.FloatType }
            )
        ) { backStackEntry ->
            val lat = backStackEntry.arguments?.getFloat("lat")?.toDouble() ?: 0.0
            val lng = backStackEntry.arguments?.getFloat("lng")?.toDouble() ?: 0.0

            AddSpotScreen(
                latitude = lat,
                longitude = lng,
                onSpotAdded = {
                    navController.popBackStack()
                }
            )
        }
        // ─── PROFILE EKRAN (POPRAVLJENO) ───
        composable("profile") {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable("bot_chat_screen") {
            BotChatScreen()
        }
    }
}