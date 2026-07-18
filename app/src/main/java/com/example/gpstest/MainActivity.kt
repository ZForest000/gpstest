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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MyLocation
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
import com.example.gpstest.data.local.SettingsStore
import com.example.gpstest.domain.repository.GnssRepository
import com.example.gpstest.ui.screens.agps.AGpsManagerScreen
import com.example.gpstest.ui.screens.diagnostics.ReceiverDiagnosticsScreen
import com.example.gpstest.ui.screens.help.HelpScreen
import com.example.gpstest.ui.screens.history.HistoryScreen
import com.example.gpstest.ui.screens.navigation.NavigationMessageScreen
import com.example.gpstest.ui.screens.nmea.NmeaScreen
import com.example.gpstest.ui.screens.overview.SatelliteOverviewScreen
import com.example.gpstest.ui.screens.positioning.PositioningScreen
import com.example.gpstest.ui.screens.satellite.SatelliteListScreen
import com.example.gpstest.ui.screens.settings.SettingsScreen
import com.example.gpstest.ui.theme.Theme
import com.example.gpstest.viewmodel.AGpsViewModel
import com.example.gpstest.viewmodel.NavigationMessageViewModel
import com.example.gpstest.viewmodel.NmeaViewModel
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
    private val dependencies: AppDependencies
        get() = (application as GpsTestApplication).dependencies

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(application, dependencies.appSettingsStore)
    }

    private val satelliteViewModel: SatelliteViewModel by viewModels {
        SatelliteViewModelFactory(
            application,
            dependencies.gnssRepository,
            dependencies.satelliteHistoryRepository,
            dependencies.appSettingsStore,
            dependencies.externalGpsEphemerisProvider,
        )
    }

    private val nmeaViewModel: NmeaViewModel by viewModels {
        NmeaViewModelFactory(application, dependencies.gnssRepository, dependencies.appSettingsStore)
    }

    private val navigationMessageViewModel: NavigationMessageViewModel by viewModels {
        NavigationMessageViewModelFactory(application, dependencies.gnssRepository)
    }

    private val agpsViewModel: AGpsViewModel by viewModels {
        AGpsViewModelFactory(application, dependencies.agpsRepository)
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
                nmeaViewModel.onPermissionChanged(true)
                navigationMessageViewModel.startListening()
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    _permissionState.value = PermissionState.PERMANENTLY_DENIED
                } else {
                    _permissionState.value = PermissionState.DENIED
                }
                satelliteViewModel.setPermissionDenied()
                nmeaViewModel.onPermissionChanged(false)
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
                        nmeaViewModel = nmeaViewModel,
                        navigationMessageViewModel = navigationMessageViewModel,
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
            nmeaViewModel.onPermissionChanged(true)
            navigationMessageViewModel.startListening()
        } else {
            satelliteViewModel.setPermissionDenied()
            nmeaViewModel.onPermissionChanged(false)
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
    object Overview : Screen("overview")

    object SatelliteList : Screen("satellite_list")

    object SkyChart : Screen("sky_chart")

    object Positioning : Screen("positioning")

    object ReceiverDiagnostics : Screen("receiver_diagnostics")

    object History : Screen("history")

    object Nmea : Screen("nmea")

    object NavigationMessages : Screen("navigation_messages")

    object AGps : Screen("agps")

    object Help : Screen("help")

    object Settings : Screen("settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
fun GpsTestApp(
    satelliteViewModel: SatelliteViewModel,
    nmeaViewModel: NmeaViewModel,
    navigationMessageViewModel: NavigationMessageViewModel,
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
            popUpTo(Screen.Overview.route) { saveState = true }
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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.nav_drawer_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                    androidx.compose.material3.HorizontalDivider()
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.nav_section_realtime_monitoring),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp),
                    )
                    androidx.compose.material3.NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_overview)) },
                        selected = currentRoute == Screen.Overview.route,
                        onClick = { navigateAndCloseDrawer(Screen.Overview.route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    androidx.compose.material3.NavigationDrawerItem(
                        icon = { Icon(Icons.Default.SatelliteAlt, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_satellite_list)) },
                        selected = currentRoute == Screen.SatelliteList.route,
                        onClick = { navigateAndCloseDrawer(Screen.SatelliteList.route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    androidx.compose.material3.NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_sky_chart)) },
                        selected = currentRoute == Screen.SkyChart.route,
                        onClick = { navigateAndCloseDrawer(Screen.SkyChart.route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    androidx.compose.material3.NavigationDrawerItem(
                        icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_positioning)) },
                        selected = currentRoute == Screen.Positioning.route,
                        onClick = { navigateAndCloseDrawer(Screen.Positioning.route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    androidx.compose.material3.NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Memory, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_receiver_diagnostics)) },
                        selected = currentRoute == Screen.ReceiverDiagnostics.route,
                        onClick = { navigateAndCloseDrawer(Screen.ReceiverDiagnostics.route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.nav_section_data_tools),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 8.dp),
                    )
                    androidx.compose.material3.NavigationDrawerItem(
                        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_agps)) },
                        selected = currentRoute == Screen.AGps.route,
                        onClick = { navigateAndCloseDrawer(Screen.AGps.route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    androidx.compose.material3.NavigationDrawerItem(
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_history)) },
                        selected = currentRoute == Screen.History.route,
                        onClick = { navigateAndCloseDrawer(Screen.History.route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    androidx.compose.material3.NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_nmea)) },
                        selected = currentRoute == Screen.Nmea.route,
                        onClick = { navigateAndCloseDrawer(Screen.Nmea.route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    androidx.compose.material3.NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_navigation_messages)) },
                        selected = currentRoute == Screen.NavigationMessages.route,
                        onClick = { navigateAndCloseDrawer(Screen.NavigationMessages.route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    androidx.compose.material3.NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Help, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_help)) },
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
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Overview.route,
        ) {
            composable(Screen.Overview.route) {
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
                SatelliteOverviewScreen(
                    viewModel = satelliteViewModel,
                    permissionState = permissionState,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
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
            composable(Screen.Positioning.route) {
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
                PositioningScreen(
                    viewModel = satelliteViewModel,
                    permissionState = permissionState,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
            composable(Screen.ReceiverDiagnostics.route) {
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
                ReceiverDiagnosticsScreen(
                    viewModel = satelliteViewModel,
                    permissionState = permissionState,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
            composable(Screen.History.route) {
                BackHandler { safeNavigateBack() }
                HistoryScreen(
                    viewModel = satelliteViewModel,
                    onNavigateBack = safeNavigateBack,
                )
            }
            composable(Screen.Nmea.route) {
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
                NmeaScreen(
                    viewModel = nmeaViewModel,
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
            composable(Screen.NavigationMessages.route) {
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
                NavigationMessageScreen(
                    viewModel = navigationMessageViewModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
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
    private val externalEphemerisProvider: com.example.gpstest.data.local.ExternalGpsEphemerisProvider? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SatelliteViewModel::class.java)) {
            return SatelliteViewModel(application, gnssRepository, historyRepository, settingsStore, externalEphemerisProvider) as T
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

class NmeaViewModelFactory(
    private val application: Application,
    private val gnssRepository: GnssRepository,
    private val settingsStore: SettingsStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NmeaViewModel::class.java)) {
            return NmeaViewModel(application, gnssRepository, settingsStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class NavigationMessageViewModelFactory(
    private val application: Application,
    private val gnssRepository: GnssRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NavigationMessageViewModel::class.java)) {
            return NavigationMessageViewModel(application, gnssRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
