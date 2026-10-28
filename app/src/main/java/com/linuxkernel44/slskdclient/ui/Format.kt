package com.linuxkernel44.slskdclient.ui

fun fmtBytes(n: Long?): String {
    if (n == null) return "–"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = n.toDouble(); var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return if (i == 0 || v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}

fun fmtDuration(seconds: Number?): String {
    if (seconds == null || seconds.toDouble() <= 0) return ""
    val s = seconds.toLong(); val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
