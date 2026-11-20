package com.wumisc.skipper.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

private fun zone(): ZoneId = ZoneId.systemDefault()
private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

fun formatClock(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone()).format(clockFormatter)

fun dayLabel(millis: Long): String {
    val date = dateOf(millis)
    val today = LocalDate.now(zone())
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(dayFormatter)
    }
}

fun isToday(millis: Long): Boolean = dateOf(millis) == LocalDate.now(zone())

fun isThisWeek(millis: Long): Boolean {
    val date = dateOf(millis)
    val today = LocalDate.now(zone())
    return date.isAfter(today.minusDays(7)) || date == today.minusDays(7)
}

/** 包名最后一段在界面里够用了，也省掉「读取应用列表」这种敏感权限。 */
fun friendlyName(pkg: String): String {
    val tail = pkg.substringAfterLast('.')
    return when (tail) {
        "qiyi" -> "爱奇艺"
        "phone" -> "优酷"
        "qqlive" -> "腾讯视频"
        "activity" -> "芒果 TV"
        "bili" -> "哔哩哔哩"
        else -> tail.replaceFirstChar { it.uppercase() }
    }
}
