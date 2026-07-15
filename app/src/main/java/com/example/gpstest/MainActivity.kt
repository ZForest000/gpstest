package com.example.gpstest

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.gpstest.data.local.AGpsFileHandlerImpl
import com.example.gpstest.data.local.AGpsSettingsStore
import com.example.gpstest.data.local.SatelliteHistoryDataStore
import com.example.gpstest.data.local.SettingsStore
import com.example.gpstest.data.source.AGpsDataSourceImpl
import com.example.gpstest.data.source.AGpsDownloaderImpl
import com.example.gpstest.data.source.GnssDataSourceImpl
import com.example.gpstest.domain.repository.AGpsRepositoryImpl
import com.example.gpstest.domain.repository.GnssRepositoryImpl
import com.example.gpstest.domain.repository.SatelliteHistoryRepositoryImpl
import com.example.gpstest.ui.screens.agps.AGpsManagerScreen
import com.example.gpstest.ui.screens.help.HelpScreen
import com.example.gpstest.ui.screens.history.HistoryScreen
import com.example.gpstest.ui.screens.satellite.SatelliteListScreen
import com.example.gpstest.ui.screens.settings.SettingsScreen
import com.example.gpstest.ui.theme.Theme
import com.example.gpstest.viewmodel.AGpsViewModel
import com.example.gpstest.viewmodel.SatelliteViewModel
import com.example.gpstest.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PermissionState {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
}

class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application
        val settingsStore = SettingsStore(app)
        SettingsViewModelFactory(app, settingsStore)
    }

    private val satelliteViewModel: SatelliteViewModel by viewModels {
        val app = application
        val dataSource = GnssDataSourceImpl(app)
        val gnssRepository = GnssRepositoryImpl(dataSource)
        val settingsStore = SettingsStore(app)
        val historyDataStore = SatelliteHistoryDataStore(app, settingsStore)
        val historyRepository = SatelliteHistoryRepositoryImpl(historyDataStore)
        SatelliteViewModelFactory(app, gnssRepository, historyRepository, settingsStore)
    }

    private val agpsViewModel: AGpsViewModel by viewModels {
        val app = application
        val dataSource = AGpsDataSourceImpl(app)
        val downloader = AGpsDownloaderImpl()
        val fileHandler = AGpsFileHandlerImpl(app)
        val settingsStore = AGpsSettingsStore(app)
        val repository = AGpsRepositoryImpl(dataSource, downloader, fileHandler, settingsStore)
        AGpsViewModelFactory(app, repository)
    }

    private val _permissionState = MutableStateFlow(PermissionState.DENIED)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private var hasRequestedPermission = false

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            hasRequestedPermission = true
            if (isGranted) {
                _permissionState.value = PermissionState.GRANTED
                satelliteViewModel.startListening()
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    _permissionState.value = PermissionState.PERMANENTLY_DENIED
                } else {
                    _permissionState.value = PermissionState.DENIED
                }
                satelliteViewModel.setPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        updatePermissionState()

        setContent {
            val appSettings by settingsViewModel.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            Theme(darkTheme = appSettings.resolveDarkTheme(systemDark)) {
                Surface {
                    GpsTestApp(
                        satelliteViewModel = satelliteViewModel,
                        agpsViewModel = agpsViewModel,
                        settingsViewModel = settingsViewModel,
                        permissionStateFlow = _permissionState,
                        onRequestPermission = {
                            hasRequestedPermission = true
                            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        onOpenAppSettings = { openAppSettings() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    private fun updatePermissionState() {
        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            _permissionState.value = PermissionState.GRANTED
            satelliteViewModel.startListening()
        } else {
            satelliteViewModel.setPermissionDenied()
            if (hasRequestedPermission &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                _permissionState.value = PermissionState.PERMANENTLY_DENIED
            } else {
                _permissionState.value = PermissionState.DENIED
            }
        }
    }

    private fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        startActivity(intent)
    }
}

sealed class Screen(
    val route: String,
) {
    object SatelliteList : Screen("satellite_list")

    object SkyChart : Screen("sky_chart")

    object History : Screen("history")

    object AGps : Screen("agps")

    object Help : Screen("help")

    object Settings : Screen("settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
fun GpsTestApp(
    satelliteViewModel: SatelliteViewModel,
    agpsViewModel: AGpsViewModel,
    settingsViewModel: SettingsViewModel,
    permissionStateFlow: StateFlow<PermissionState>,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val navController = rememberNavController()
    val permissionState by permissionStateFlow.collectAsState()
    val drawerState =
        androidx.compose.material3.rememberDrawerState(
            initialValue = androidx.compose.material3.DrawerValue.Closed,
        )
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val safeNavigateBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    val navigateAndCloseDrawer: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Screen.SatelliteList.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        scope.launch {
            drawerState.close()
        }
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet {
                androidx.compose.material3.Text(
                    text = "GNSS 测试",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
                androidx.compose.material3.HorizontalDivider()
                androidx.compose.material3.NavigationDrawerItem(
                    icon = { Icon(Icons.Default.SatelliteAlt, contentDescription = null) },
                    label = { Text("卫星列表") },
                    selected = currentRoute == Screen.SatelliteList.route,
                    onClick = { navigateAndCloseDrawer(Screen.SatelliteList.route) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                androidx.compose.material3.NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                    label = { Text("天空图") },
                    selected = currentRoute == Screen.SkyChart.route,
                    onClick = { navigateAndCloseDrawer(Screen.SkyChart.route) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                androidx.compose.material3.NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                    label = { Text("A-GPS 管理") },
                    selected = currentRoute == Screen.AGps.route,
                    onClick = { navigateAndCloseDrawer(Screen.AGps.route) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                androidx.compose.material3.NavigationDrawerItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("历史记录") },
                    selected = currentRoute == Screen.History.route,
                    onClick = { navigateAndCloseDrawer(Screen.History.route) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                androidx.compose.material3.NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Help, contentDescription = null) },
                    label = { Text("帮助") },
                    selected = currentRoute == Screen.Help.route,
                    onClick = { navigateAndCloseDrawer(Screen.Help.route) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                androidx.compose.material3.NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings_title)) },
                    selected = currentRoute == Screen.Settings.route,
                    onClick = { navigateAndCloseDrawer(Screen.Settings.route) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.SatelliteList.route,
        ) {
            composable(Screen.SatelliteList.route) {
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
                SatelliteListScreen(
                    viewModel = satelliteViewModel,
                    permissionState = permissionState,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenDrawer = {
                        scope.launch {
                            drawerState.open()
                        }
                    },
                )
            }
            composable(Screen.SkyChart.route) {
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
                com.example.gpstest.ui.screens.skychart.SkyChartScreen(
                    viewModel = satelliteViewModel,
                    permissionState = permissionState,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenDrawer = {
                        scope.launch {
                            drawerState.open()
                        }
                    },
                )
            }
            composable(Screen.History.route) {
                BackHandler { safeNavigateBack() }
                HistoryScreen(
                    viewModel = satelliteViewModel,
                    onNavigateBack = safeNavigateBack,
                )
            }
            composable(Screen.AGps.route) {
                BackHandler { safeNavigateBack() }
                AGpsManagerScreen(
                    viewModel = agpsViewModel,
                    onNavigateBack = safeNavigateBack,
                )
            }
            composable(Screen.Help.route) {
                BackHandler { safeNavigateBack() }
                HelpScreen(
                    onNavigateBack = safeNavigateBack,
                )
            }
            composable(Screen.Settings.route) {
                BackHandler { safeNavigateBack() }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = safeNavigateBack,
                )
            }
        }
    }
}

class SatelliteViewModelFactory(
    private val application: Application,
    private val gnssRepository: com.example.gpstest.domain.repository.GnssRepository,
    private val historyRepository: com.example.gpstest.domain.repository.SatelliteHistoryRepository,
    private val settingsStore: SettingsStore? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SatelliteViewModel::class.java)) {
            return SatelliteViewModel(application, gnssRepository, historyRepository, settingsStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class AGpsViewModelFactory(
    private val application: Application,
    private val repository: com.example.gpstest.domain.repository.AGpsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AGpsViewModel::class.java)) {
            return AGpsViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SettingsViewModelFactory(
    private val application: Application,
    private val settingsStore: SettingsStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(application, settingsStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
