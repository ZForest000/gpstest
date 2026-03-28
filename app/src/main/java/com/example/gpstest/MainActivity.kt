package com.example.gpstest

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gpstest.data.local.AGpsFileHandlerImpl
import com.example.gpstest.data.local.AGpsSettingsStore
import com.example.gpstest.data.local.SatelliteHistoryDataStore
import com.example.gpstest.data.source.AGpsDataSourceImpl
import com.example.gpstest.data.source.AGpsDownloaderImpl
import com.example.gpstest.data.source.GnssDataSourceImpl
import com.example.gpstest.domain.repository.AGpsRepositoryImpl
import com.example.gpstest.domain.repository.GnssRepositoryImpl
import com.example.gpstest.domain.repository.SatelliteHistoryRepositoryImpl
import com.example.gpstest.ui.screens.agps.AGpsManagerScreen
import com.example.gpstest.ui.screens.history.HistoryScreen
import com.example.gpstest.ui.screens.satellite.SatelliteListScreen
import com.example.gpstest.ui.theme.Theme
import com.example.gpstest.viewmodel.AGpsViewModel
import com.example.gpstest.viewmodel.SatelliteViewModel

class MainActivity : ComponentActivity() {

    private val satelliteViewModel: SatelliteViewModel by viewModels {
        val application = application as GpstestApplication
        val dataSource = GnssDataSourceImpl(application)
        val gnssRepository = GnssRepositoryImpl(dataSource)
        val historyDataStore = SatelliteHistoryDataStore(application)
        val historyRepository = SatelliteHistoryRepositoryImpl(historyDataStore)
        SatelliteViewModelFactory(application, gnssRepository, historyRepository)
    }

    private val agpsViewModel: AGpsViewModel by viewModels {
        val application = application as GpstestApplication
        val dataSource = AGpsDataSourceImpl(application)
        val downloader = AGpsDownloaderImpl()
        val fileHandler = AGpsFileHandlerImpl(application)
        val settingsStore = AGpsSettingsStore(application)
        val repository = AGpsRepositoryImpl(dataSource, downloader, fileHandler, settingsStore)
        AGpsViewModelFactory(application, repository)
    }

    private var hasPermission: Boolean = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            satelliteViewModel.startListening()
        } else {
            satelliteViewModel.setPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        hasPermission = checkLocationPermission()

        setContent {
            Theme {
                Surface {
                    GpsTestApp(
                        satelliteViewModel = satelliteViewModel,
                        agpsViewModel = agpsViewModel,
                        hasPermission = hasPermission,
                        onRequestPermission = { requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    )
                }
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (granted) {
            satelliteViewModel.startListening()
        }
        
        return granted
    }
}

sealed class Screen(val route: String) {
    object SatelliteList : Screen("satellite_list")
    object History : Screen("history")
    object AGps : Screen("agps")
}

@androidx.compose.runtime.Composable
fun GpsTestApp(
    satelliteViewModel: SatelliteViewModel,
    agpsViewModel: AGpsViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.SatelliteList.route
    ) {
        composable(Screen.SatelliteList.route) {
            SatelliteListScreen(
                viewModel = satelliteViewModel,
                hasPermission = hasPermission,
                onRequestPermission = onRequestPermission,
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToAGps = { navController.navigate(Screen.AGps.route) }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = satelliteViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AGps.route) {
            AGpsManagerScreen(
                viewModel = agpsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

class SatelliteViewModelFactory(
    private val application: Application,
    private val gnssRepository: com.example.gpstest.domain.repository.GnssRepository,
    private val historyRepository: com.example.gpstest.domain.repository.SatelliteHistoryRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SatelliteViewModel::class.java)) {
            return SatelliteViewModel(application, gnssRepository, historyRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class AGpsViewModelFactory(
    private val application: Application,
    private val repository: com.example.gpstest.domain.repository.AGpsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AGpsViewModel::class.java)) {
            return AGpsViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
