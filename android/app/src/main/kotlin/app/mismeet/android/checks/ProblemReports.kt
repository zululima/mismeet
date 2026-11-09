package app.mismeet.android.checks

import android.content.Context
import android.os.Build
import app.mismeet.android.AppModel
import app.mismeet.android.BuildConfig
import app.mismeet.android.inEnglish
import app.mismeet.android.nostr.RelayService
import app.mismeet.android.store.Contact
import app.mismeet.android.store.PersistedState
import app.mismeet.protocol.ProblemReport
import app.mismeet.protocol.ProtocolConstants
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The text of the problem reports of spec section 8.8, and what it draws on. */
object ProblemReports {
    private val lines = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    /** Keeps [text] among the last log lines, which only a report ever reads. */
    fun record(text: String) = synchronized(lines) {
        lines.addLast("${clock.format(Date())} $text")
        while (lines.size > ProtocolConstants.REPORT_LOG_LINES) lines.removeFirst()
    }

    private fun recentLog(): List<String> = synchronized(lines) { lines.toList() }

    /** Writes each uncaught exception, with the log before it, for the next run to report. */
    fun catchCrashes(context: Context) {
        val file = crashFile(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(error.stackTraceToString().take(CRASH_LENGTH) + "\nLog before the crash\n" + recentLog().joinToString("\n"))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pendingCrash(context: Context): String? = crashFile(context).takeIf { it.exists() }?.readText()

    fun forgetCrash(context: Context) {
        crashFile(context).delete()
    }

    private fun crashFile(context: Context) = File(context.filesDir, "crash.txt")

    fun text(
        context: Context,
        model: AppModel,
        state: PersistedState,
        relayChecks: List<RelayService.RelayCheck>,
        reason: String,
        automatic: Boolean,
        crash: String?,
    ): String {
        val contacts = state.contacts
        // The developer reads reports in English, whatever language the app shows.
        val english = context.inEnglish()
        val text = buildString {
            appendLine("Mismeet problem report")
            appendLine("Reason: $reason")
            appendLine("Sent: ${if (automatic) "automatically" else "by the user"}")
            appendLine("App: Mismeet ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) ", debug build" else ""}")
            appendLine("Phone: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("This phone")
            phoneChecks(english, model, state) {}.forEach { appendLine(line(it)) }
            appendLine()
            appendLine("Relays")
            relayChecks.forEach { check ->
                val shown = relayCheck(english, check, state)
                val title = if (check.url.trimEnd('/') in PersistedState.DEFAULT_RELAYS) shown.title else "another relay"
                appendLine(line(shown.copy(title = title)))
            }
            appendLine()
            appendLine(
                "Contacts: ${contacts.size}, sharing with ${contacts.count { it.share }}, " +
                    "${contacts.count { it.sharing == Contact.SharingStatus.SHARING }} share back, " +
                    "${contacts.count { it.sharing == Contact.SharingStatus.NOT_SHARING }} do not, " +
                    "${contacts.count { it.sharing == Contact.SharingStatus.UNKNOWN }} not found yet; " +
                    "${state.requests.size} add-back requests waiting",
            )
            crash?.let {
                appendLine()
                appendLine("Crash on an earlier run")
                appendLine(it)
            }
            appendLine()
            appendLine("Log")
            recentLog().forEach { appendLine(it) }
        }
        val names = contacts.map { it.name } + state.requests.mapNotNull { it.name } + state.ownName
        return ProblemReport.clip(ProblemReport.redact(text.trimEnd(), names, PersistedState.DEFAULT_RELAYS))
    }

    private fun line(check: Check) = "- ${check.outcome}: ${check.title}: ${check.detail}"

    private const val CRASH_LENGTH = 3_000
}
