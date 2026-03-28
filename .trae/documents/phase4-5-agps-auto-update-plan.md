# Phase 4 & 5: A-GPS管理和自动更新实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标:** 实现A-GPS数据管理功能，包括手动导入、状态查看和自动后台更新。

**架构:** 在现有MVVM架构基础上，添加AGps模块，使用WorkManager实现后台任务，DataStore存储设置。

---

## Phase 4: A-GPS管理

### Task 4.1: 创建A-GPS数据模型

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/domain/model/AGpsStatus.kt`

- [ ] **Step 1: 创建AGpsStatus.kt**

```kotlin
package com.example.gpstest.domain.model

data class AGpsStatus(
    val timeStatus: DataStatus = DataStatus.UNKNOWN,
    val ephemerisStatus: DataStatus = DataStatus.UNKNOWN,
    val almanacStatus: DataStatus = DataStatus.UNKNOWN,
    val lastUpdateTime: Long? = null,
    val lastInjectionTime: Long? = null
)

enum class DataStatus {
    VALID,      // 有效且在有效期内
    EXPIRED,    // 已过期
    PARTIAL,    // 部分有效
    MISSING,    // 缺失
    UNKNOWN     // 未知状态
}

data class AGpsInjectionRecord(
    val id: String,
    val type: InjectionType,
    val source: InjectionSource,
    val timestamp: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

enum class InjectionType { 
    TIME, 
    EPHEMERIS, 
    ALMANAC, 
    XTRA 
}

enum class InjectionSource { 
    MANUAL, 
    AUTO_DOWNLOAD, 
    NETWORK 
}

data class AGpsSettings(
    val autoUpdateEnabled: Boolean = false,
    val updateIntervalHours: Int = 24,
    val lastAutoUpdateTime: Long? = null,
    val downloadUrl: String = DEFAULT_XTRA_URL
) {
    companion object {
        const val DEFAULT_XTRA_URL = "https://xtrapath1.izatcloud.net/xtra3grc.bin"
    }
}
```

---

### Task 4.2: 创建AGpsDataSource

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/data/source/AGpsDataSource.kt`
- 创建: `app/src/main/java/com/example/gpstest/data/source/AGpsDataSourceImpl.kt`

- [ ] **Step 1: 创建AGpsDataSource.kt接口**

```kotlin
package com.example.gpstest.data.source

import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.model.DataStatus

interface AGpsDataSource {
    suspend fun injectXtraData(data: ByteArray): Result<Unit>
    suspend fun injectTime(timeMillis: Long): Result<Unit>
    suspend fun checkStatus(): AGpsStatus
    fun isSupported(): Boolean
}
```

- [ ] **Step 2: 创建AGpsDataSourceImpl.kt**

```kotlin
package com.example.gpstest.data.source

import android.content.Context
import android.location.LocationManager
import android.os.Build
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.model.DataStatus

class AGpsDataSourceImpl(
    private val context: Context
) : AGpsDataSource {

    private val locationManager: LocationManager?
        get() = context.getSystemService(LocationManager::class.java)

    override suspend fun injectXtraData(data: ByteArray): Result<Unit> {
        return try {
            val result = locationManager?.sendExtraCommand(
                LocationManager.GPS_PROVIDER,
                "force_xtra_injection",
                null
            ) ?: false
            
            if (result) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("XTRA injection failed: command returned false"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun injectTime(timeMillis: Long): Result<Unit> {
        return try {
            val result = locationManager?.sendExtraCommand(
                LocationManager.GPS_PROVIDER,
                "force_time_injection",
                null
            ) ?: false
            
            if (result) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Time injection failed: command returned false"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkStatus(): AGpsStatus {
        return try {
            AGpsStatus(
                timeStatus = DataStatus.UNKNOWN,
                ephemerisStatus = DataStatus.UNKNOWN,
                almanacStatus = DataStatus.UNKNOWN,
                lastUpdateTime = null
            )
        } catch (e: Exception) {
            AGpsStatus()
        }
    }

    override fun isSupported(): Boolean {
        return locationManager?.getProvider(LocationManager.GPS_PROVIDER) != null
    }
}
```

---

### Task 4.3: 创建AGpsDownloader

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/data/source/AGpsDownloader.kt`

- [ ] **Step 1: 添加OkHttp依赖到build.gradle.kts**

```kotlin
// 在dependencies中添加
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

- [ ] **Step 2: 创建AGpsDownloader.kt**

```kotlin
package com.example.gpstest.data.source

import com.example.gpstest.domain.model.AGpsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

interface AGpsDownloader {
    suspend fun download(url: String): Result<ByteArray>
    fun getDefaultUrls(): List<String>
}

class AGpsDownloaderImpl : AGpsDownloader {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun download(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
            }
            
            val body = response.body ?: return@withContext Result.failure(IOException("Empty response body"))
            val data = body.bytes()
            
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getDefaultUrls(): List<String> {
        return listOf(
            "https://xtrapath1.izatcloud.net/xtra3grc.bin",
            "https://xtrapath2.izatcloud.net/xtra3grc.bin",
            "https://xtrapath3.izatcloud.net/xtra3grc.bin"
        )
    }
}
```

---

### Task 4.4: 创建AGpsFileHandler

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/data/local/AGpsFileHandler.kt`

- [ ] **Step 1: 创建AGpsFileHandler.kt**

```kotlin
package com.example.gpstest.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface AGpsFileHandler {
    suspend fun readFile(uri: Uri): Result<ByteArray>
    fun getSupportedTypes(): List<String>
}

class AGpsFileHandlerImpl(
    private val context: Context
) : AGpsFileHandler {

    override suspend fun readFile(uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException("Cannot open file: $uri"))
            
            val data = inputStream.use { it.readBytes() }
            
            if (data.isEmpty()) {
                return@withContext Result.failure(IOException("File is empty"))
            }
            
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getSupportedTypes(): List<String> {
        return listOf("bin", "xml", "txt")
    }
}
```

---

### Task 4.5: 创建AGpsRepository

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/domain/repository/AGpsRepository.kt`
- 创建: `app/src/main/java/com/example/gpstest/domain/repository/AGpsRepositoryImpl.kt`

- [ ] **Step 1: 创建AGpsRepository.kt接口**

```kotlin
package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.domain.model.AGpsStatus
import kotlinx.coroutines.flow.Flow

interface AGpsRepository {
    val status: Flow<AGpsStatus>
    val settings: Flow<AGpsSettings>
    val injectionHistory: Flow<List<AGpsInjectionRecord>>
    
    suspend fun downloadAndInject(): Result<Unit>
    suspend fun injectFromFile(filePath: String): Result<Unit>
    suspend fun injectTime(): Result<Unit>
    suspend fun refreshStatus()
    suspend fun updateSettings(settings: AGpsSettings)
    suspend fun clearHistory()
}
```

- [ ] **Step 2: 创建AGpsRepositoryImpl.kt**

```kotlin
package com.example.gpstest.domain.repository

import android.net.Uri
import com.example.gpstest.data.local.AGpsFileHandler
import com.example.gpstest.data.source.AGpsDataSource
import com.example.gpstest.data.source.AGpsDownloader
import com.example.gpstest.domain.model.*
import kotlinx.coroutines.flow.*

class AGpsRepositoryImpl(
    private val dataSource: AGpsDataSource,
    private val downloader: AGpsDownloader,
    private val fileHandler: AGpsFileHandler,
    private val settingsStore: AGpsSettingsStore
) : AGpsRepository {

    private val _status = MutableStateFlow(AGpsStatus())
    override val status: Flow<AGpsStatus> = _status.asStateFlow()

    override val settings: Flow<AGpsSettings> = settingsStore.settings

    private val _injectionHistory = MutableStateFlow<List<AGpsInjectionRecord>>(emptyList())
    override val injectionHistory: Flow<List<AGpsInjectionRecord>> = _injectionHistory.asStateFlow()

    override suspend fun downloadAndInject(): Result<Unit> {
        val currentSettings = settings.first()
        val urls = listOf(currentSettings.downloadUrl) + downloader.getDefaultUrls()
        
        for (url in urls) {
            val downloadResult = downloader.download(url)
            
            if (downloadResult.isSuccess) {
                val data = downloadResult.getOrThrow()
                val injectResult = dataSource.injectXtraData(data)
                
                if (injectResult.isSuccess) {
                    addRecord(InjectionType.XTRA, InjectionSource.AUTO_DOWNLOAD, true)
                    updateLastInjectionTime()
                    return Result.success(Unit)
                } else {
                    addRecord(InjectionType.XTRA, InjectionSource.AUTO_DOWNLOAD, false, 
                        injectResult.exceptionOrNull()?.message)
                }
            }
        }
        
        return Result.failure(Exception("All download sources failed"))
    }

    override suspend fun injectFromFile(filePath: String): Result<Unit> {
        val uri = Uri.parse(filePath)
        val readResult = fileHandler.readFile(uri)
        
        if (readResult.isFailure) {
            return Result.failure(readResult.exceptionOrNull() ?: Exception("Failed to read file"))
        }
        
        val data = readResult.getOrThrow()
        val injectResult = dataSource.injectXtraData(data)
        
        addRecord(
            InjectionType.XTRA, 
            InjectionSource.MANUAL, 
            injectResult.isSuccess,
            injectResult.exceptionOrNull()?.message
        )
        
        return injectResult
    }

    override suspend fun injectTime(): Result<Unit> {
        val result = dataSource.injectTime(System.currentTimeMillis())
        
        addRecord(
            InjectionType.TIME,
            InjectionSource.MANUAL,
            result.isSuccess,
            result.exceptionOrNull()?.message
        )
        
        return result
    }

    override suspend fun refreshStatus() {
        _status.value = dataSource.checkStatus()
    }

    override suspend fun updateSettings(settings: AGpsSettings) {
        settingsStore.updateSettings(settings)
    }

    override suspend fun clearHistory() {
        _injectionHistory.value = emptyList()
    }

    private fun addRecord(
        type: InjectionType,
        source: InjectionSource,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val record = AGpsInjectionRecord(
            id = System.currentTimeMillis().toString(),
            type = type,
            source = source,
            timestamp = System.currentTimeMillis(),
            success = success,
            errorMessage = errorMessage
        )
        
        _injectionHistory.update { listOf(record) + it.take(49) }
    }

    private fun updateLastInjectionTime() {
        _status.update { it.copy(lastInjectionTime = System.currentTimeMillis()) }
    }
}
```

---

### Task 4.6: 创建AGpsSettingsStore

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/data/local/AGpsSettingsStore.kt`

- [ ] **Step 1: 创建AGpsSettingsStore.kt**

```kotlin
package com.example.gpstest.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gpstest.domain.model.AGpsSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.agpsDataStore: DataStore<Preferences> by preferencesDataStore(name = "agps_settings")

class AGpsSettingsStore(private val context: Context) {
    
    companion object {
        private val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        private val UPDATE_INTERVAL_HOURS = intPreferencesKey("update_interval_hours")
        private val LAST_AUTO_UPDATE_TIME = longPreferencesKey("last_auto_update_time")
        private val DOWNLOAD_URL = stringPreferencesKey("download_url")
    }
    
    val settings: Flow<AGpsSettings> = context.agpsDataStore.data.map { prefs ->
        AGpsSettings(
            autoUpdateEnabled = prefs[AUTO_UPDATE_ENABLED] ?: false,
            updateIntervalHours = prefs[UPDATE_INTERVAL_HOURS] ?: 24,
            lastAutoUpdateTime = prefs[LAST_AUTO_UPDATE_TIME],
            downloadUrl = prefs[DOWNLOAD_URL] ?: AGpsSettings.DEFAULT_XTRA_URL
        )
    }
    
    suspend fun updateSettings(settings: AGpsSettings) {
        context.agpsDataStore.edit { prefs ->
            prefs[AUTO_UPDATE_ENABLED] = settings.autoUpdateEnabled
            prefs[UPDATE_INTERVAL_HOURS] = settings.updateIntervalHours
            settings.lastAutoUpdateTime?.let { prefs[LAST_AUTO_UPDATE_TIME] = it }
            prefs[DOWNLOAD_URL] = settings.downloadUrl
        }
    }
    
    suspend fun updateLastAutoUpdateTime(time: Long) {
        context.agpsDataStore.edit { prefs ->
            prefs[LAST_AUTO_UPDATE_TIME] = time
        }
    }
}
```

---

### Task 4.7: 创建AGpsViewModel

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/viewmodel/AGpsViewModel.kt`

- [ ] **Step 1: 创建AGpsViewModel.kt**

```kotlin
package com.example.gpstest.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.repository.AGpsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AGpsViewModel(
    application: Application,
    private val repository: AGpsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<AGpsUiState>(AGpsUiState.Idle)
    val uiState: StateFlow<AGpsUiState> = _uiState.asStateFlow()

    val status: StateFlow<AGpsStatus> = repository.status
    val settings: StateFlow<AGpsSettings> = repository.settings
    val injectionHistory: StateFlow<List<AGpsInjectionRecord>> = repository.injectionHistory

    private val _selectedFileUri = MutableStateFlow<Uri?>(null)
    val selectedFileUri: StateFlow<Uri?> = _selectedFileUri.asStateFlow()

    fun downloadAndInject() {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Downloading
            val result = repository.downloadAndInject()
            _uiState.value = if (result.isSuccess) {
                AGpsUiState.Success("A-GPS data updated successfully")
            } else {
                AGpsUiState.Error(result.exceptionOrNull()?.message ?: "Download failed")
            }
        }
    }

    fun injectFromFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Injecting
            val result = repository.injectFromFile(uri.toString())
            _uiState.value = if (result.isSuccess) {
                AGpsUiState.Success("File injected successfully")
            } else {
                AGpsUiState.Error(result.exceptionOrNull()?.message ?: "Injection failed")
            }
        }
    }

    fun injectTime() {
        viewModelScope.launch {
            _uiState.value = AGpsUiState.Injecting
            val result = repository.injectTime()
            _uiState.value = if (result.isSuccess) {
                AGpsUiState.Success("Time synchronized successfully")
            } else {
                AGpsUiState.Error(result.exceptionOrNull()?.message ?: "Time injection failed")
            }
        }
    }

    fun updateSettings(settings: AGpsSettings) {
        viewModelScope.launch {
            repository.updateSettings(settings)
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            repository.refreshStatus()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearMessage() {
        _uiState.value = AGpsUiState.Idle
    }
}

sealed interface AGpsUiState {
    data object Idle : AGpsUiState
    data object Downloading : AGpsUiState
    data object Injecting : AGpsUiState
    data class Success(val message: String) : AGpsUiState
    data class Error(val message: String) : AGpsUiState
}
```

---

### Task 4.8: 创建A-GPS管理界面

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/ui/screens/agps/AGpsManagerScreen.kt`
- 创建: `app/src/main/java/com/example/gpstest/ui/components/AGpsStatusCard.kt`

- [ ] **Step 1: 创建AGpsStatusCard.kt**

```kotlin
package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.model.DataStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AGpsStatusCard(
    status: AGpsStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.data_status),
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            StatusRow(
                label = stringResource(R.string.time_sync),
                status = status.timeStatus
            )
            
            StatusRow(
                label = stringResource(R.string.ephemeris),
                status = status.ephemerisStatus
            )
            
            StatusRow(
                label = stringResource(R.string.almanac),
                status = status.almanacStatus
            )
            
            status.lastUpdateTime?.let { time ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.last_update, formatTime(time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    status: DataStatus,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (status == DataStatus.VALID) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = getStatusColor(status),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = getStatusText(status),
                style = MaterialTheme.typography.bodyMedium,
                color = getStatusColor(status)
            )
        }
    }
}

@Composable
private fun getStatusColor(status: DataStatus) = when (status) {
    DataStatus.VALID -> MaterialTheme.colorScheme.primary
    DataStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
    DataStatus.EXPIRED -> MaterialTheme.colorScheme.error
    DataStatus.MISSING -> MaterialTheme.colorScheme.error
    DataStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
}

@Composable
private fun getStatusText(status: DataStatus) = when (status) {
    DataStatus.VALID -> stringResource(R.string.status_valid)
    DataStatus.PARTIAL -> stringResource(R.string.status_partial)
    DataStatus.EXPIRED -> stringResource(R.string.status_expired)
    DataStatus.MISSING -> stringResource(R.string.status_missing)
    DataStatus.UNKNOWN -> stringResource(R.string.status_unknown)
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
```

- [ ] **Step 2: 创建AGpsManagerScreen.kt**

```kotlin
package com.example.gpstest.ui.screens.agps

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.AGpsInjectionRecord
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.ui.components.AGpsStatusCard
import com.example.gpstest.viewmodel.AGpsUiState
import com.example.gpstest.viewmodel.AGpsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AGpsManagerScreen(
    viewModel: AGpsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val status by viewModel.status.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val history by viewModel.injectionHistory.collectAsState()

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.injectFromFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agps_manager)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AGpsStatusCard(status = status)
            }

            item {
                AutoUpdateCard(
                    settings = settings,
                    onSettingsChange = { viewModel.updateSettings(it) }
                )
            }

            item {
                ManualActionsCard(
                    onDownloadClick = { viewModel.downloadAndInject() },
                    onFileClick = { 
                        fileLauncher.launch(arrayOf("*/*"))
                    },
                    onTimeClick = { viewModel.injectTime() },
                    isLoading = uiState is AGpsUiState.Downloading || uiState is AGpsUiState.Injecting
                )
            }

            if (history.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.injection_history),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(history) { record ->
                    HistoryItem(record = record)
                }
            }
        }

        uiState.let { state ->
            when (state) {
                is AGpsUiState.Success -> {
                    LaunchedEffect(state) {
                        kotlinx.coroutines.delay(2000)
                        viewModel.clearMessage()
                    }
                    Snackbar(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(state.message)
                    }
                }
                is AGpsUiState.Error -> {
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.clearMessage() }) {
                                Text(stringResource(R.string.dismiss))
                            }
                        }
                    ) {
                        Text(state.message)
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun AutoUpdateCard(
    settings: AGpsSettings,
    onSettingsChange: (AGpsSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.auto_update),
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.enable_auto_update),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.auto_update_desc, settings.updateIntervalHours),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = settings.autoUpdateEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange(settings.copy(autoUpdateEnabled = enabled))
                    }
                )
            }
        }
    }
}

@Composable
private fun ManualActionsCard(
    onDownloadClick: () -> Unit,
    onFileClick: () -> Unit,
    onTimeClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.manual_actions),
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDownloadClick,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.download_now))
                    }
                }
                
                OutlinedButton(
                    onClick = onFileClick,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.import_file))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = onTimeClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.sync_time))
            }
        }
    }
}

@Composable
private fun HistoryItem(
    record: AGpsInjectionRecord,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (record.success) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimestamp(record.timestamp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (record.success) stringResource(R.string.success) 
                           else stringResource(R.string.failed),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (record.success) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error
                )
            }
            
            record.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
```

---

### Task 4.9: 更新导航和字符串资源

**文件:**
- 修改: `app/src/main/java/com/example/gpstest/MainActivity.kt`
- 修改: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 更新strings.xml添加A-GPS相关字符串**

```xml
<!-- A-GPS -->
<string name="agps_manager">A-GPS 管理</string>
<string name="data_status">数据状态</string>
<string name="time_sync">时间同步</string>
<string name="ephemeris">星历数据</string>
<string name="almanac">历书数据</string>
<string name="last_update">最后更新: %s</string>
<string name="status_valid">有效</string>
<string name="status_partial">部分有效</string>
<string name="status_expired">已过期</string>
<string name="status_missing">缺失</string>
<string name="status_unknown">未知</string>
<string name="auto_update">自动更新</string>
<string name="enable_auto_update">启用自动更新</string>
<string name="auto_update_desc">每 %d 小时自动下载更新</string>
<string name="manual_actions">手动操作</string>
<string name="download_now">立即下载</string>
<string name="import_file">导入文件</string>
<string name="sync_time">同步时间</string>
<string name="injection_history">注入历史</string>
<string name="success">成功</string>
<string name="failed">失败</string>
<string name="dismiss">关闭</string>
```

- [ ] **Step 2: 更新MainActivity.kt添加A-GPS导航**

在Screen sealed class中添加:
```kotlin
object AGps : Screen("agps")
```

在NavHost中添加:
```kotlin
composable(Screen.AGps.route) {
    AGpsManagerScreen(
        viewModel = agpsViewModel,
        onNavigateBack = { navController.popBackStack() }
    )
}
```

在SatelliteListScreen添加A-GPS入口按钮。

---

## Phase 5: 自动更新

### Task 5.1: 创建AGpsUpdateWorker

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/service/AGpsUpdateWorker.kt`

- [ ] **Step 1: 创建AGpsUpdateWorker.kt**

```kotlin
package com.example.gpstest.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.gpstest.data.local.AGpsSettingsStore
import com.example.gpstest.data.source.AGpsDownloaderImpl
import com.example.gpstest.domain.repository.AGpsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class AGpsUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: AGpsRepository,
    private val settingsStore: AGpsSettingsStore
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settings = settingsStore.settings.first()
        
        if (!settings.autoUpdateEnabled) {
            return Result.success()
        }
        
        val result = repository.downloadAndInject()
        
        return if (result.isSuccess) {
            settingsStore.updateLastAutoUpdateTime(System.currentTimeMillis())
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "agps_update_work"

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<AGpsUpdateWorker>(
                intervalHours.toLong(),
                TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
```

---

### Task 5.2: 添加WorkManager依赖

**文件:**
- 修改: `app/build.gradle.kts`

- [ ] **Step 1: 添加WorkManager和Hilt依赖**

```kotlin
// WorkManager
implementation("androidx.work:work-runtime-ktx:2.10.0")

// Hilt (for dependency injection)
implementation("com.google.dagger:hilt-android:2.52")
kapt("com.google.dagger:hilt-compiler:2.52")
implementation("androidx.hilt:hilt-work:1.2.0")
kapt("androidx.hilt:hilt-compiler:1.2.0")
```

- [ ] **Step 2: 添加Hilt插件到build.gradle.kts**

```kotlin
plugins {
    id("com.google.dagger.hilt.android") version "2.52" apply false
}
```

- [ ] **Step 3: 更新Application类**

```kotlin
@HiltAndroidApp
class GpstestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
```

---

### Task 5.3: 实现设置变更监听

**文件:**
- 修改: `app/src/main/java/com/example/gpstest/viewmodel/AGpsViewModel.kt`

- [ ] **Step 1: 添加WorkManager调度逻辑**

```kotlin
fun updateSettings(settings: AGpsSettings) {
    viewModelScope.launch {
        repository.updateSettings(settings)
        
        if (settings.autoUpdateEnabled) {
            AGpsUpdateWorker.schedule(getApplication(), settings.updateIntervalHours)
        } else {
            AGpsUpdateWorker.cancel(getApplication())
        }
    }
}
```

---

### Task 5.4: 添加通知支持

**文件:**
- 修改: `app/src/main/AndroidManifest.xml`
- 创建: `app/src/main/java/com/example/gpstest/util/NotificationHelper.kt`

- [ ] **Step 1: 添加通知权限**

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 2: 创建NotificationHelper.kt**

```kotlin
package com.example.gpstest.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.gpstest.R

object NotificationHelper {
    private const val CHANNEL_ID = "agps_update"
    private const val CHANNEL_NAME = "A-GPS Update"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showUpdateNotification(context: Context, success: Boolean) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.agps_update_title))
            .setContentText(
                if (success) context.getString(R.string.agps_update_success)
                else context.getString(R.string.agps_update_failed)
            )
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }
}
```

---

## 测试和验证

### Task 6.1: 构建和测试

- [ ] **Step 1: 构建项目**
```bash
./gradlew assembleDebug
```

- [ ] **Step 2: 安装并测试A-GPS功能**
- 测试手动下载功能
- 测试文件导入功能
- 测试时间同步功能
- 测试自动更新开关

---

## 完成清单

- [ ] 所有A-GPS数据模型已创建
- [ ] AGpsDataSource实现完成
- [ ] AGpsDownloader实现完成
- [ ] AGpsFileHandler实现完成
- [ ] AGpsRepository实现完成
- [ ] AGpsViewModel实现完成
- [ ] A-GPS管理界面实现完成
- [ ] WorkManager后台任务实现完成
- [ ] 导航和字符串资源更新完成
- [ ] 构建成功
- [ ] 功能测试通过
