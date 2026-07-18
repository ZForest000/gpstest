package com.example.gpstest.ui.screens.satellite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.gpstest.PermissionState
import com.example.gpstest.R
import com.example.gpstest.ui.components.ErrorContent
import com.example.gpstest.ui.components.PermissionRequiredContent
import com.example.gpstest.viewmodel.SatelliteUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteScreenScaffold(
    title: String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.cd_menu),
                        )
                    }
                },
                actions = actions,
            )
        },
        modifier = modifier,
        content = content,
    )
}

@Composable
fun SatelliteStateContent(
    uiState: SatelliteUiState,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (SatelliteUiState.Success) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            SatelliteUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            SatelliteUiState.PermissionRequired -> {
                PermissionRequiredContent(
                    permissionState = permissionState,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }
            is SatelliteUiState.Success -> content(uiState)
            is SatelliteUiState.Error -> {
                ErrorContent(
                    message = uiState.message,
                    onRetry = onRetry,
                )
            }
        }
    }
}
