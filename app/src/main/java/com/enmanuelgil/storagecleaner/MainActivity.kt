package com.enmanuelgil.storagecleaner

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.enmanuelgil.storagecleaner.ui.screens.*
import com.enmanuelgil.storagecleaner.ui.theme.*
import com.enmanuelgil.storagecleaner.viewmodel.MainViewModel

class StorageCleanerApp : Application() { override fun onCreate() { super.onCreate() } }

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        setContent { StorageCleanerTheme { AppContent(viewModel) } }
    }
}

@Composable
fun AppContent(vm: MainViewModel) {
    val storage   by vm.storage.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val isCleaning by vm.isCleaning.collectAsStateWithLifecycle()
    val lastResult by vm.lastResult.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    val windowInfo = currentWindowAdaptiveInfo()
    val isTablet   = windowInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

    val tabs = listOf(
        NavTab2("Inicio",    Icons.Default.Storage),
        NavTab2("Ajustes",   Icons.Default.Settings)
    )

    val content: @Composable (PaddingValues) -> Unit = { pv ->
        Box(Modifier.fillMaxSize().background(BackgroundDark).padding(pv)) {
            when (tab) {
                0 -> DashboardScreen(storage, isLoading, isCleaning, lastResult, vm::analyze, vm::clearCache)
                1 -> SettingsScreen()
            }
        }
    }

    if (isTablet) {
        StorageCleanerTheme {
            Row(Modifier.fillMaxSize().background(BackgroundDark)) {
                NavigationRail(containerColor = SurfaceDark) {
                    Spacer(Modifier.height(16.dp))
                    tabs.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(t.icon, t.label, tint = if (tab == i) CleanBlue else TextSecondary) },
                            label = { Text(t.label, color = if (tab == i) CleanBlue else TextSecondary, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal) },
                            colors = NavigationRailItemDefaults.colors(indicatorColor = CleanBlue.copy(alpha = 0.15f))
                        )
                    }
                }
                Box(Modifier.fillMaxSize()) { content(PaddingValues(0.dp)) }
            }
        }
    } else {
        Scaffold(
            containerColor = BackgroundDark,
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(t.icon, t.label, tint = if (tab == i) CleanBlue else TextSecondary) },
                            label = { Text(t.label, color = if (tab == i) CleanBlue else TextSecondary) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = CleanBlue.copy(alpha = 0.15f))
                        )
                    }
                }
            }
        ) { pv -> content(pv) }
    }
}

data class NavTab2(val label: String, val icon: ImageVector)
