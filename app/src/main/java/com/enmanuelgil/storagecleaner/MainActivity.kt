package com.enmanuelgil.storagecleaner

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.window.core.layout.WindowWidthSizeClass
import com.enmanuelgil.storagecleaner.ui.screens.*
import com.enmanuelgil.storagecleaner.ui.theme.*
import com.enmanuelgil.storagecleaner.viewmodel.MainViewModel

class StorageCleanerApp : Application()

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        setContent { StorageCleanerTheme { AppContent(viewModel) } }
    }

    // Al volver de Ajustes (tras dar un permiso) se recargan los datos.
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

private data class NavTab(val label: String, val icon: ImageVector)

@Composable
fun AppContent(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val isTablet = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
    val tabs = listOf(
        NavTab("Resumen", Icons.Default.PieChart),
        NavTab("Apps", Icons.Default.Apps),
        NavTab("Archivos", Icons.Default.FolderOpen),
        NavTab("Ajustes", Icons.Default.Settings),
    )
    val content: @Composable (PaddingValues) -> Unit = { pv ->
        Box(Modifier.fillMaxSize().background(BackgroundDark).padding(pv)) {
            when (tab) {
                0 -> OverviewScreen(vm, onOpenApps = { tab = 1 })
                1 -> AppsScreen(vm)
                2 -> FilesScreen(vm)
                else -> SettingsScreen()
            }
        }
    }
    if (isTablet) {
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
            Box(Modifier.fillMaxSize().statusBarsPadding()) { content(PaddingValues(0.dp)) }
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
