package com.example.gpstest

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.gpstest.data.source.GnssDataSourceImpl
import com.example.gpstest.domain.repository.GnssRepository
import com.example.gpstest.domain.repository.GnssRepositoryImpl
import com.example.gpstest.ui.screens.satellite.SatelliteListScreen
import com.example.gpstest.ui.theme.Theme
import com.example.gpstest.viewmodel.SatelliteViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SatelliteViewModel by viewModels {
        val application = application as GpstestApplication
        val dataSource = GnssDataSourceImpl(application)
        val repository = GnssRepositoryImpl(dataSource)
        SatelliteViewModelFactory(application, repository)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkLocationPermission()

        setContent {
            Theme {
                Surface {
                    SatelliteListScreen(
                        viewModel = viewModel,
                        onRequestPermission = { requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    )
                }
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startListening()
        }
    }
}

class SatelliteViewModelFactory(
    private val application: Application,
    private val repository: GnssRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SatelliteViewModel::class.java)) {
            return SatelliteViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
