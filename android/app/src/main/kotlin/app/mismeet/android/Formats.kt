package app.mismeet.android

import android.content.Context
import android.content.res.Configuration
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateUtils
import app.mismeet.android.store.ShareDuration
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The language [Context.getString] answers in. */
fun Context.locale(): Locale = resources.configuration.locales[0]

/** This context in English, for problem reports and the log, which stay English whatever the app's language. */
fun Context.inEnglish(): Context =
    createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale.ENGLISH) })

/** How long ago [millis] was, such as "5 minutes ago", or a date after a week, in [context]'s language. */
fun ago(context: Context, millis: Long): String {
    val elapsed = System.currentTimeMillis() - millis
    if (elapsed < DateUtils.MINUTE_IN_MILLIS) return context.getString(R.string.just_now)
    val (count, unit) = when {
        elapsed < DateUtils.HOUR_IN_MILLIS -> elapsed / DateUtils.MINUTE_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.MINUTES
        elapsed < DateUtils.DAY_IN_MILLIS -> elapsed / DateUtils.HOUR_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.HOURS
        elapsed < DateUtils.WEEK_IN_MILLIS -> elapsed / DateUtils.DAY_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.DAYS
        else -> return DateFormat.getDateInstance(DateFormat.MEDIUM, context.locale()).format(Date(millis))
    }
    return RelativeDateTimeFormatter.getInstance(context.locale())
        .format(count.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
}

/** A measure such as "850 m", "1.4 km" or "18 km/h", with [decimals] digits after the separator of [context]'s language. */
fun measure(context: Context, value: Number, unit: MeasureUnit, decimals: Int = 0): String {
    val locale = context.locale()
    val numbers = NumberFormat.getInstance(locale).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT, numbers).format(Measure(value, unit))
}

/** When a limited share ends, such as "Until 14:30", or "Until end of day" for one that ends at local midnight. */
fun shareUntil(context: Context, endsAt: Long): String =
    if (ShareDuration.isStartOfDay(endsAt)) {
        context.getString(R.string.shared_until_end_of_day)
    } else {
        context.getString(R.string.shared_until, android.text.format.DateFormat.getTimeFormat(context).format(Date(endsAt)))
    }
