package app.mismeet.android.ui

import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.mismeet.android.AppModel
import app.mismeet.android.BuildConfig
import app.mismeet.android.R
import app.mismeet.android.checks.Check
import app.mismeet.android.checks.Outcome
import app.mismeet.android.checks.contactCheck
import app.mismeet.android.checks.phoneChecks
import app.mismeet.android.checks.relayCheck
import app.mismeet.android.location.LocationProvider
import app.mismeet.android.nostr.RelayService
import kotlinx.coroutines.launch

/**
 * What has to hold for sharing to work, checked one by one as Slack's connection test does, each
 * with a way to fix it where Android offers one. Location permissions are asked on the Me screen,
 * right after the disclosure Play demands, so their checks lead back there. Above the checks are
 * the controls that do not fit on the Me screen: sharing now and problem reports.
 */
@Composable
fun SetupChecksScreen(model: AppModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onDone)
    val state by model.state.collectAsState()
    val context = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    var relayChecks by remember { mutableStateOf<List<RelayService.RelayCheck>?>(null) }
    // Settings changed in another app show when the user comes back.
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumed++
        onPauseOrDispose {}
    }
    LaunchedEffect(round) {
        relayChecks = null
        relayChecks = model.checkRelays()
    }
    val phone = remember(state, resumed) { phoneChecks(context, model, state, onDone) }
    val foreground = remember(resumed) { LocationProvider.hasForegroundPermission(context) }
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf<Boolean?>(null) }

    report?.let { text ->
        AlertDialog(
            onDismissRequest = { if (!sending) report = null },
            title = { Text(stringResource(R.string.report_question)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.report_question_text), style = MaterialTheme.typography.bodyMedium)
                    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = !sending, onClick = {
                    sending = true
                    scope.launch {
                        sent = model.sendReport(text)
                        sending = false
                        report = null
                    }
                }) { Text(stringResource(if (sending) R.string.sending else R.string.send)) }
            },
            dismissButton = { TextButton(enabled = !sending, onClick = { report = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.check_setup), style = MaterialTheme.typography.titleLarge)
            if (state.sharingEnabled) {
                Text(stringResource(R.string.sharing), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { model.publishNow() }, enabled = foreground) { Text(stringResource(R.string.share_now)) }
                    state.lastPublishedAt?.let {
                        Text(
                            stringResource(R.string.last_shared, DateFormat.getTimeFormat(context).format(it)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Text(stringResource(R.string.problem_reports), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.send_problem_reports), modifier = Modifier.weight(1f))
                Switch(checked = state.reportsEnabled, onCheckedChange = { model.setReportsEnabled(it) })
            }
            Text(stringResource(R.string.reports_footer), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.this_phone), style = MaterialTheme.typography.titleMedium)
            phone.forEach { CheckRow(it) }
            Text(stringResource(R.string.relays), style = MaterialTheme.typography.titleMedium)
            relayChecks?.forEach { CheckRow(relayCheck(context, it, state)) }
                ?: Text(stringResource(R.string.asking_relays), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.contacts), style = MaterialTheme.typography.titleMedium)
            if (state.contacts.isEmpty()) {
                CheckRow(Check(stringResource(R.string.no_contacts), Outcome.WARNING, stringResource(R.string.no_contacts_detail)))
            }
            state.contacts.forEach { CheckRow(contactCheck(context, it)) }
            Text(stringResource(R.string.app), style = MaterialTheme.typography.titleMedium)
            CheckRow(
                Check(
                    stringResource(R.string.version),
                    Outcome.INFO,
                    stringResource(R.string.version_detail, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.RELEASE),
                ),
            )
            sent?.let {
                CheckRow(
                    if (it) {
                        Check(stringResource(R.string.report), Outcome.OK, stringResource(R.string.report_sent))
                    } else {
                        Check(stringResource(R.string.report), Outcome.PROBLEM, stringResource(R.string.report_not_sent))
                    },
                )
            }
        }
        OutlinedButton(
            onClick = {
                preparing = true
                scope.launch {
                    report = model.reportText(relayChecks)
                    preparing = false
                }
            },
            enabled = !preparing,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { Text(stringResource(if (preparing) R.string.preparing_report else R.string.send_report)) }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.done)) }
            Button(onClick = { round++ }, enabled = relayChecks != null, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.run_checks_again))
            }
        }
    }
}

@Composable
private fun CheckRow(check: Check) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val (icon, tint) = when (check.outcome) {
            Outcome.OK -> Icons.Filled.CheckCircle to Color(0xFF2E7D32)
            Outcome.WARNING -> Icons.Filled.Warning to Color(0xFFEF6C00)
            Outcome.PROBLEM -> Icons.Filled.Close to Color(0xFFC62828)
            Outcome.INFO -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
        }
        val description = when (check.outcome) {
            Outcome.OK -> R.string.outcome_ok
            Outcome.WARNING -> R.string.outcome_warning
            Outcome.PROBLEM -> R.string.outcome_problem
            Outcome.INFO -> R.string.outcome_info
        }
        Icon(icon, contentDescription = stringResource(description), tint = tint, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(check.title, style = MaterialTheme.typography.titleSmall)
            Text(check.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            check.fix?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
        }
    }
}
