package app.mismeet.android

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mismeet.android.ui.MapScreen
import app.mismeet.android.ui.MeScreen
import app.mismeet.protocol.ProtocolConstants
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = AppModel.get(this)
        // Test hook for debuggable builds: `adb shell am start ... --es invites "uri;Name|uri"` adds contacts
        // and shares with them without asking, and the name question waits for a launch without it.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            intent.getStringExtra("invites")?.let { invites ->
                model.testLaunch = true
                invites.split(";").forEachIndexed { index, entry ->
                    runCatching { model.addContact(entry.substringAfter('|'), entry.substringBefore('|', "Test ${index + 1}"), ask = false) }
                }
            }
        }
        if (savedInstanceState == null) openInvite(intent)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                MismeetScreen(model)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openInvite(intent)
    }

    private fun openInvite(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) intent.dataString?.let { AppModel.get(this).openInvite(it) }
    }

    override fun onResume() {
        super.onResume()
        AppModel.get(this).becameActive()
    }
}

@Composable
private fun MismeetScreen(model: AppModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // The contact the map focuses on, picked in the contact list.
    var focused by rememberSaveable { mutableStateOf<String?>(null) }
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val openedInvite by model.openedInvite.collectAsState()
    LaunchedEffect(openedInvite) {
        if (openedInvite != null) {
            tab = 0
            focused = null
        }
    }
    LaunchedEffect(Unit) {
        model.notices.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.contacts.map { it.id to it.lastEventId }) {
        while (true) {
            model.updatePlaceNames()
            delay(ProtocolConstants.FOREGROUND_FETCH_INTERVAL_SECONDS * 1000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            model.refresh()
            delay(ProtocolConstants.FOREGROUND_FETCH_INTERVAL_SECONDS * 1000)
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_map)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_me)) },
                )
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            0 -> MapScreen(model, focused, onFocus = { focused = it }, onUnfocus = { focused = null }, content)
            else -> MeScreen(model, content)
        }
    }
}
