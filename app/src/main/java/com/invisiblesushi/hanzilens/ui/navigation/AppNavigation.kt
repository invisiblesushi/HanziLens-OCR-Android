package com.invisiblesushi.hanzilens.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.invisiblesushi.hanzilens.ui.screen.AboutScreen
import com.invisiblesushi.hanzilens.ui.screen.CameraScreen
import com.invisiblesushi.hanzilens.ui.screen.SettingsScreen
import com.invisiblesushi.hanzilens.ui.theme.AppColors
import com.invisiblesushi.hanzilens.ui.viewmodel.AppState
import com.invisiblesushi.hanzilens.ui.viewmodel.CameraViewModel
import kotlinx.coroutines.launch

object Routes {
    const val CAMERA   = "camera"
    const val SETTINGS = "settings"
    const val ABOUT    = "about"
}

private val GREEN = AppColors.Green
private val DIM   = AppColors.Dim
private val MONO  = AppColors.Mono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(viewModel: CameraViewModel = viewModel()) {
    val appState     by viewModel.appState.collectAsState()
    val loadProgress by viewModel.loadProgress.collectAsState()
    val loadingMsg   by viewModel.loadingMessage.collectAsState()

    // Show splash/loading until dictionary is ready
    if (appState != AppState.READY) {
        SplashScreen(
            progress  = loadProgress,
            message   = loadingMsg,
            isError   = appState == AppState.ERROR,
            onRetry   = viewModel::retryInit
        )
        return
    }

    val navController  = rememberNavController()
    val drawerState    = rememberDrawerState(DrawerValue.Closed)
    val scope          = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route ?: Routes.CAMERA

    var showCameraSettings by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            AppDrawer(
                currentRoute  = currentRoute,
                navController = navController,
                onClose       = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text       = routeTitle(currentRoute),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        if (currentRoute == Routes.CAMERA) {
                            IconButton(onClick = { showCameraSettings = true }) {
                                Icon(Icons.Default.PhotoCamera,
                                    contentDescription = "Camera Settings")
                            }
                        }
                        if (currentRoute == Routes.SETTINGS || currentRoute == Routes.ABOUT) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Default.Close, contentDescription = "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor       = Color.Black.copy(alpha = 0.85f),
                        titleContentColor    = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor     = Color.White
                    )
                )
            },
            containerColor = Color.Black
        ) { padding ->
            NavHost(
                navController    = navController,
                startDestination = Routes.CAMERA,
                enterTransition  = { fadeIn() },
                exitTransition   = { fadeOut() },
                modifier         = Modifier.padding(padding)
            ) {
                composable(Routes.CAMERA) {
                    CameraScreen(
                        viewModel            = viewModel,
                        showCameraSettings   = showCameraSettings,
                        onCameraSettingsDismiss = { showCameraSettings = false }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(navController = navController, viewModel = viewModel)
                }
                composable(Routes.ABOUT) {
                    AboutScreen(navController = navController)
                }
            }
        }
    }
}

@Composable
private fun AppDrawer(
    currentRoute: String,
    navController: NavController,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        modifier            = Modifier.width(260.dp),
        drawerContainerColor = Color(0xFF111111)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            Text("汉字镜", color = GREEN, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("HanziLens", color = DIM, fontSize = 13.sp, fontFamily = MONO)
        }

        HorizontalDivider(color = Color(0xFF333333))
        Spacer(Modifier.height(8.dp))

        DrawerNavItem(
            label       = "Camera",
            icon        = Icons.Default.CameraAlt,
            selected    = currentRoute == Routes.CAMERA,
            onClick     = {
                navController.navigate(Routes.CAMERA) {
                    popUpTo(Routes.CAMERA) { inclusive = true }
                }
                onClose()
            }
        )
        DrawerNavItem(
            label    = "Settings",
            icon     = Icons.Default.Settings,
            selected = currentRoute == Routes.SETTINGS,
            onClick  = {
                navController.navigate(Routes.SETTINGS)
                onClose()
            }
        )
    }
}

@Composable
private fun DrawerNavItem(
    label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit
) {
    NavigationDrawerItem(
        label  = { Text(label, color = if (selected) GREEN else Color.White) },
        icon   = { Icon(icon, contentDescription = label,
            tint = if (selected) GREEN else DIM) },
        selected = selected,
        onClick  = onClick,
        colors   = NavigationDrawerItemDefaults.colors(
            selectedContainerColor   = Color.White.copy(alpha = 0.08f),
            unselectedContainerColor = Color.Transparent
        ),
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
fun SplashScreen(progress: Float, message: String, isError: Boolean, onRetry: () -> Unit = {}) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .fillMaxSize()
            .background(if (isError) Color(0xFF0D0000) else Color.Black)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(horizontal = 40.dp)
        ) {
            // Logo
            Text(
                text       = "汉字镜",
                color      = if (isError) Color(0xFFFF4444) else GREEN,
                fontSize   = 56.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text       = "HanziLens",
                color      = DIM,
                fontSize   = 16.sp,
                fontFamily = MONO,
                modifier   = Modifier.padding(bottom = 48.dp)
            )

            if (!isError) {
                LinearProgressIndicator(
                    progress   = { progress },
                    modifier   = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    color      = GREEN,
                    trackColor = Color(0xFF333333)
                )
            }

            Text(
                text       = message,
                color      = if (isError) Color(0xFFFF4444) else Color.White,
                fontSize   = 13.sp,
                fontFamily = MONO
            )

            if (isError) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onRetry,
                    colors  = ButtonDefaults.buttonColors(containerColor = GREEN)
                ) {
                    Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Text(
                    text       = "Check logcat for details if the error persists.",
                    color      = DIM,
                    fontSize   = 11.sp,
                    fontFamily = MONO,
                    modifier   = Modifier.padding(top = 12.dp)
                )
            } else if (progress < 0.05f) {
                Text(
                    text       = "First launch: ~30–60 s to build the dictionary",
                    color      = DIM,
                    fontSize   = 11.sp,
                    fontFamily = MONO,
                    modifier   = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun routeTitle(route: String) = when (route) {
    Routes.CAMERA   -> "HanziLens"
    Routes.SETTINGS -> "Settings"
    Routes.ABOUT    -> "About"
    else            -> "HanziLens"
}
