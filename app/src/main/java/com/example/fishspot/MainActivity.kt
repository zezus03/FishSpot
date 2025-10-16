package com.example.fishspot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fishspot.data.AuthRepository
import com.example.fishspot.ui.theme.FishSpotTheme
import com.example.fishspot.userinterface.AddSpotScreen
import com.example.fishspot.userinterface.LoginScreen
import com.example.fishspot.userinterface.MapScreen
import com.example.fishspot.userinterface.RankingScreen
import com.example.fishspot.userinterface.RegisterScreen
import com.example.fishspot.userinterface.SpotsListScreen
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                authRepository = AuthRepository(),
                onLoginSuccess = {
                    navController.navigate("map") { popUpTo("login") { inclusive = true } }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }

        composable("register") {
            RegisterScreen(
                authRepository = AuthRepository(),
                onRegistrationSuccess = {
                    navController.navigate("map") { popUpTo("login") { inclusive = true } }
                }
            )
        }

        composable("map") { backStackEntry ->
            val userIdToFocus by backStackEntry.savedStateHandle
                .getLiveData<String>("user_id_to_focus")
                .observeAsState()

            MapScreen(
                focusOnUserCatch = userIdToFocus,
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
                onFocusDone = {
                    backStackEntry.savedStateHandle.remove<String>("user_id_to_focus")
                }
            )
        }

        composable("spots_list") {
            SpotsListScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

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
    }
}