package com.linuxkernel44.slskdclient.ui.screens

import com.linuxkernel44.slskdclient.data.RemoteFile
import com.linuxkernel44.slskdclient.data.SearchResponse

/** Port of the WebUI's result filter syntax: `minbr:320 islossless -live word`. */
data class SearchFilters(
    val include: List<String> = emptyList(), val exclude: List<String> = emptyList(),
    val isCBR: Boolean = false, val isVBR: Boolean = false, val isLossless: Boolean = false, val isLossy: Boolean = false,
    val minBitDepth: Int = 0, val minBitRate: Int = 0, val minFilesInFolder: Int = 0, val minFileSize: Long = 0, val minLength: Int = 0,
)

private fun num(text: String, regex: Regex): Int = regex.find(text)?.groupValues?.get(2)?.toIntOrNull() ?: 0

fun parseFilters(text: String): SearchFilters {
    val t = text.lowercase()
    val terms = t.split(' ').filter { it.isNotBlank() && !it.contains(':') && it !in setOf("isvbr", "iscbr", "islossless", "islossy") }
    return SearchFilters(
        include = terms.filterNot { it.startsWith("-") },
        exclude = terms.filter { it.startsWith("-") && it.length > 1 }.map { it.drop(1) },
        isCBR = "iscbr" in t, isVBR = "isvbr" in t, isLossless = "islossless" in t, isLossy = "islossy" in t,
        minBitDepth = num(t, Regex("(minbd|minbitdepth):(\\d+)")),
        minBitRate = num(t, Regex("(minbr|minbitrate):(\\d+)")),
        minFilesInFolder = num(t, Regex("(minfif|minfilesinfolder):(\\d+)")),
        minFileSize = num(t, Regex("(minfs|minfilesize):(\\d+)")).toLong(),
        minLength = num(t, Regex("(minlen|minlength):(\\d+)")),
    )
}

private fun keep(f: RemoteFile, x: SearchFilters): Boolean {
    if (x.isCBR && (f.isVariableBitRate == null || f.isVariableBitRate)) return false
    if (x.isVBR && (f.isVariableBitRate == null || !f.isVariableBitRate)) return false
    val lossless = f.sampleRate != null && f.bitDepth != null
    if (x.isLossless && !lossless) return false
    if (x.isLossy && (f.sampleRate != null || f.bitDepth != null)) return false
    if ((f.bitRate ?: 0) < x.minBitRate) return false
    if ((f.bitDepth ?: 0) < x.minBitDepth) return false
    if (f.size < x.minFileSize) return false
    if ((f.length ?: 0) < x.minLength) return false
    val name = f.filename.lowercase()
    if (x.include.isNotEmpty() && !x.include.all { name.contains(it) }) return false
    if (x.exclude.any { name.contains(it) }) return false
    return true
}

fun filterResponse(r: SearchResponse, x: SearchFilters, hideLocked: Boolean): SearchResponse {
    if (r.fileCount + r.lockedFileCount < x.minFilesInFolder) return r.copy(files = emptyList(), lockedFiles = emptyList(), fileCount = 0, lockedFileCount = 0)
    val files = r.files.filter { keep(it, x) }
    val locked = if (hideLocked) emptyList() else r.lockedFiles.filter { keep(it, x) }
    return r.copy(files = files, fileCount = files.size, lockedFiles = locked, lockedFileCount = locked.size)
}
