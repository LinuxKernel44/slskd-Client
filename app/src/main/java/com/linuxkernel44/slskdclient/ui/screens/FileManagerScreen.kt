@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.linuxkernel44.slskdclient.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.linuxkernel44.slskdclient.R
import com.linuxkernel44.slskdclient.data.Conflict
import com.linuxkernel44.slskdclient.data.FmClip
import com.linuxkernel44.slskdclient.data.FmEntry
import com.linuxkernel44.slskdclient.data.FmRef
import com.linuxkernel44.slskdclient.data.FmResult
import com.linuxkernel44.slskdclient.data.FmTrashItem
import com.linuxkernel44.slskdclient.player.Track
import com.linuxkernel44.slskdclient.save.SaveItem
import com.linuxkernel44.slskdclient.save.SaveProgress
import com.linuxkernel44.slskdclient.ui.AppVm
import com.linuxkernel44.slskdclient.ui.components.Badge
import com.linuxkernel44.slskdclient.ui.components.ConfirmSheet
import com.linuxkernel44.slskdclient.ui.components.Empty
import com.linuxkernel44.slskdclient.ui.components.ErrorBanner
import com.linuxkernel44.slskdclient.ui.components.GIcon
import com.linuxkernel44.slskdclient.ui.components.GlassButton
import com.linuxkernel44.slskdclient.ui.components.GlassCard
import com.linuxkernel44.slskdclient.ui.components.GlassChip
import com.linuxkernel44.slskdclient.ui.components.GlassField
import com.linuxkernel44.slskdclient.ui.components.GlassIconButton
import com.linuxkernel44.slskdclient.ui.components.GlassLevel
import com.linuxkernel44.slskdclient.ui.components.KeyValue
import com.linuxkernel44.slskdclient.ui.components.Loading
import com.linuxkernel44.slskdclient.ui.components.MenuItem
import com.linuxkernel44.slskdclient.ui.components.MenuSheet
import com.linuxkernel44.slskdclient.ui.components.Muted
import com.linuxkernel44.slskdclient.ui.components.ProgressBar
import com.linuxkernel44.slskdclient.ui.components.PromptSheet
import com.linuxkernel44.slskdclient.ui.components.Txt
import com.linuxkernel44.slskdclient.ui.components.cardShape
import com.linuxkernel44.slskdclient.ui.components.glass
import com.linuxkernel44.slskdclient.ui.components.pagePadding
import com.linuxkernel44.slskdclient.ui.fmtBytes
import com.linuxkernel44.slskdclient.ui.isAudio
import com.linuxkernel44.slskdclient.ui.rememberPoll
import com.linuxkernel44.slskdclient.ui.theme.LocalColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import okio.source

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
private fun isImage(name: String) = name.substringAfterLast('.', "").lowercase() in IMAGE_EXT
private val EPOCH_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
private fun fmtEpoch(seconds: Long) = if (seconds <= 0) "" else EPOCH_FORMAT.format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()))
private fun natural(name: String) = name.lowercase().replace(Regex("\\d+")) { it.value.padStart(12, '0') }
private fun mimeOf(name: String) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"
private val SORTS = listOf("Name", "Date", "Size", "Type")

/** The Files tab of the Library: a full file manager over the folders slskd scans (Music, Downloads, Incomplete). */
@Composable
fun FileManagerTab(vm: AppVm) {
    val api = vm.api ?: return
    val server = rememberPoll(api) { api.filesServerAvailable() }
    val st = vm.fm
    when {
        server.value == false -> UnreachablePage(vm, server::refresh)
        st.showTrash -> TrashPage(vm)
        st.root == null -> RootsPage(vm)
        else -> FolderPage(vm)
    }
}

@Composable
private fun UnreachablePage(vm: AppVm, retry: () -> Unit) {
    val api = vm.api ?: return
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { LibraryHeader(vm) }
        item { ErrorBanner("File server not reachable at ${api.profile.filesBaseUrl}. The file manager needs the slskd-files companion (see the repo's server/ folder).", retry) }
    }
}

// ---------------- roots ----------------

@Composable
private fun RootsPage(vm: AppVm) {
    val api = vm.api ?: return
    val roots = rememberPoll(api, intervalMs = 30000) { api.fm.roots() }
    val c = LocalColors.current
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { LibraryHeader(vm) }
        roots.error?.let { item { ErrorBanner("$it — the file server may need an update (server/install.sh).", roots::refresh) } }
        if (roots.value == null && roots.error == null) item { Loading() }
        val r = roots.value
        if (r != null) {
            items(r.roots, key = { it.id }) { root ->
                GlassCard(Modifier.fillMaxWidth().combinedClickable(onClick = { vm.fm.open(root.id) })) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            GIcon(R.drawable.ic_folder, tint = c.accentB, size = 26.dp)
                            Column(Modifier.weight(1f)) {
                                Txt(root.name, size = 17.sp, weight = FontWeight.SemiBold)
                                Muted(when (root.id) { "music" -> "Shared · Navidrome library"; "downloads" -> "Shared · slskd downloads"; else -> "Not shared · partial downloads" }, size = 12.sp)
                            }
                            GIcon(R.drawable.ic_chevron_right, tint = c.muted)
                        }
                        if (root.total > 0) {
                            ProgressBar(((root.total - root.free).toFloat() / root.total).coerceIn(0f, 1f))
                            Muted("${fmtBytes(root.free)} free of ${fmtBytes(root.total)}", size = 11.5.sp)
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth().combinedClickable(onClick = { vm.fm.showTrash = true })) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GIcon(R.drawable.ic_trash, tint = c.muted, size = 26.dp)
                        Column(Modifier.weight(1f)) {
                            Txt("Trash", size = 17.sp, weight = FontWeight.SemiBold)
                            Muted("Deleted items stay recoverable for ${r.trashDays} days", size = 12.sp)
                        }
                        GIcon(R.drawable.ic_chevron_right, tint = c.muted)
                    }
                }
            }
            item {
                GlassButton({ rescanNow(vm, r.navidrome) }, Modifier.fillMaxWidth(), height = 48.dp) {
                    GIcon(R.drawable.ic_refresh, size = 18.dp); Txt("Rescan shares now", weight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun rescanNow(vm: AppVm, navidrome: Boolean) {
    vm.run(if (navidrome) "Rescanning slskd shares and Navidrome…" else "Rescanning slskd shares…") { it.fm.rescan(slskd = true, navidrome = navidrome) }
}

// ---------------- folder ----------------

@Composable
private fun FolderPage(vm: AppVm) {
    val api = vm.api ?: return
    val st = vm.fm
    val root = st.root ?: return
    val ctx = LocalContext.current
    val c = LocalColors.current
    val listing = rememberPoll(root to st.path) { api.fm.list(root, st.rel) }
    val saveProgress by vm.saver.progress.collectAsState()
    var uploadProgress by remember { mutableStateOf<SaveProgress?>(null) }
    var selected by remember(root, st.path) { mutableStateOf(setOf<String>()) }
    var searching by remember(root, st.path) { mutableStateOf(false) }
    var query by remember(root, st.path) { mutableStateOf("") }
    var hits by remember(root, st.path) { mutableStateOf<List<FmEntry>?>(null) }
    var hitsTruncated by remember(root, st.path) { mutableStateOf(false) }
    var searchError by remember(root, st.path) { mutableStateOf<String?>(null) }

    fun refOf(e: FmEntry) = FmRef(root, e.path ?: (if (st.rel.isEmpty()) e.name else st.rel + "/" + e.name))
    val here = FmRef(root, st.rel)

    LaunchedEffect(query, searching) {
        if (!searching || query.isBlank()) { hits = null; searchError = null; return@LaunchedEffect }
        delay(400)
        try {
            val r = api.fm.search(root, st.rel, query.trim()); hits = r.entries; hitsTruncated = r.truncated; searchError = null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { searchError = vm.friendly(e) }
    }

    val shown: List<FmEntry> = remember(listing.value, hits, searching, st.sort, st.descending) {
        val base = if (searching && query.isNotBlank()) hits.orEmpty() else listing.value.orEmpty()
        val key: Comparator<FmEntry> = when (st.sort) {
            1 -> compareBy { it.mtime }
            2 -> compareBy { it.size }
            3 -> compareBy<FmEntry> { it.name.substringAfterLast('.', "").lowercase() }.thenBy { natural(it.name) }
            else -> compareBy { natural(it.name) }
        }
        base.sortedWith(compareByDescending<FmEntry> { it.dir }.then(if (st.descending) key.reversed() else key))
    }
    val selectedRefs = shown.filter { (it.path ?: (if (st.rel.isEmpty()) it.name else st.rel + "/" + it.name)) in selected }.map(::refOf)

    BackHandler {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            searching -> { searching = false; query = "" }
            st.path.isNotEmpty() -> st.path = st.path.dropLast(1)
            else -> st.open(null)
        }
    }

    fun changed(vararg roots: String) { listing.refresh(); vm.fmChanged(*roots) }

    fun report(result: FmResult, verb: String) {
        if (result.errors.isNotEmpty()) {
            val total = result.done + result.skipped + result.errors.size
            vm.say("${result.errors.size} of $total failed: ${result.errors.first().error}")
        } else if (result.skipped > 0) vm.say("$verb ${result.done}, skipped ${result.skipped}")
    }

    fun askConflict(title: String, onChoose: (Conflict) -> Unit) {
        vm.showSheet {
            MenuSheet(title, "What if an item with the same name already exists?", listOf(
                MenuItem("Keep both (rename the new one)", R.drawable.ic_copy) { onChoose(Conflict.Rename) },
                MenuItem("Replace (the old one goes to the trash)", R.drawable.ic_refresh) { onChoose(Conflict.Replace) },
                MenuItem("Skip existing", R.drawable.ic_block) { onChoose(Conflict.Skip) },
            ), { vm.closeSheet() })
        }
    }

    fun paste(clip: FmClip) = askConflict(if (clip.move) "Move here" else "Copy here") { policy ->
        vm.run(onDone = { st.clip = null; changed(root, *clip.items.map { it.root }.toTypedArray()) }) { c2 ->
            report(c2.fm.transfer(clip.move, clip.items, here, policy), if (clip.move) "Moved" else "Copied")
        }
    }

    fun delete(refs: List<FmRef>) {
        val what = if (refs.size == 1) "“${refs[0].name}”" else "${refs.size} items"
        vm.showSheet {
            ConfirmSheet("Move to trash?", "$what will go to the trash and can be restored for 30 days.", "Move to trash", true, {
                vm.run(onDone = { selected = emptySet(); changed(*refs.map { it.root }.toSet().toTypedArray()) }) { c2 -> report(c2.fm.delete(refs), "Deleted") }
            }, { vm.closeSheet() })
        }
    }

    fun rename(ref: FmRef) {
        vm.showSheet {
            PromptSheet("Rename", "New name", ref.name, "Rename", onConfirm = { name ->
                vm.run("Renamed", onDone = { selected = emptySet(); changed(ref.root) }) { c2 -> c2.fm.rename(ref, name) }
            }, onClose = { vm.closeSheet() })
        }
    }

    fun newFolder() {
        vm.showSheet {
            PromptSheet("New folder", "Folder name", "", "Create", onConfirm = { name ->
                vm.run("Folder created", onDone = { changed(root) }) { c2 -> c2.fm.mkdir(here, name) }
            }, onClose = { vm.closeSheet() })
        }
    }

    fun save(refs: List<FmRef>, entries: List<FmEntry>, asZip: Boolean) {
        vm.run {
            try {
                val items = if (asZip || entries.any { it.dir }) {
                    val name = (if (refs.size == 1) refs[0].name else st.path.lastOrNull() ?: root) + ".zip"
                    listOf(SaveItem(api.fm.zipUrl(refs), name))
                } else refs.zip(entries).map { (r, e) -> SaveItem(api.fm.fileUrl(r), e.name, size = e.size) }
                val n = vm.saver.save(items)
                vm.say("Saved $n file${if (n == 1) "" else "s"} to Downloads/Slskd Client")
            } catch (e: Exception) { vm.say(vm.friendly(e)) }
        }
    }

    fun share(ref: FmRef, view: Boolean) {
        vm.run {
            try {
                val file = vm.saver.toCache(api.fm.fileUrl(ref), ref.name)
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val mime = mimeOf(ref.name)
                val intent = if (view) Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                else Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(intent, ref.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { vm.say(vm.friendly(e)) }
        }
    }

    fun showInfo(ref: FmRef, entry: FmEntry) {
        vm.showSheet { InfoSheet(vm, ref, entry) }
    }

    fun play(list: List<FmEntry>, start: FmEntry) {
        val audio = list.filter { !it.dir && isAudio(it.name) }
        if (audio.isEmpty()) return
        vm.player.play(audio.map { Track(api.fm.fileUrl(refOf(it)), it.name.substringBeforeLast('.'), root) }, audio.indexOf(start).coerceAtLeast(0))
        vm.showPlayer = true
    }

    fun itemMenu(entry: FmEntry) {
        val ref = refOf(entry)
        vm.showSheet {
            MenuSheet(entry.name, if (entry.dir) "Folder" else fmtBytes(entry.size), buildList {
                if (entry.dir) {
                    add(MenuItem("Open", R.drawable.ic_folder) { st.open(root, ref.path.split('/')); searching = false; query = "" })
                    add(MenuItem("Play audio in folder", R.drawable.ic_play) {
                        vm.run { c2 ->
                            val audio = c2.fm.list(root, ref.path).filter { !it.dir && isAudio(it.name) }.sortedBy { natural(it.name) }
                            if (audio.isEmpty()) vm.say("No audio files directly in this folder")
                            else { vm.player.play(audio.map { Track(c2.fm.fileUrl(FmRef(root, ref.path + "/" + it.name)), it.name.substringBeforeLast('.'), entry.name) }); vm.showPlayer = true }
                        }
                    })
                } else {
                    if (isAudio(entry.name)) add(MenuItem("Play", R.drawable.ic_play) { play(shown, entry) })
                    if (isImage(entry.name)) add(MenuItem("View", R.drawable.ic_eye) { vm.showSheet { ImageSheet(vm, ref) } })
                }
                add(MenuItem("Info", R.drawable.ic_info) { showInfo(ref, entry) })
                add(MenuItem("Rename", R.drawable.ic_tag) { rename(ref) })
                add(MenuItem("Copy", R.drawable.ic_copy) { st.clip = FmClip(false, listOf(ref)); vm.say("Copied — open a folder and tap Paste") })
                add(MenuItem("Move", R.drawable.ic_swap) { st.clip = FmClip(true, listOf(ref)); vm.say("Ready to move — open a folder and tap Paste") })
                add(MenuItem(if (entry.dir) "Save to phone (ZIP)" else "Save to phone", R.drawable.ic_save) { save(listOf(ref), listOf(entry), false) })
                if (!entry.dir) {
                    add(MenuItem("Save as ZIP", R.drawable.ic_download) { save(listOf(ref), listOf(entry), true) })
                    add(MenuItem("Open with…", R.drawable.ic_link) { share(ref, true) })
                    add(MenuItem("Share…", R.drawable.ic_send) { share(ref, false) })
                }
                add(MenuItem("Delete", R.drawable.ic_trash, danger = true) { delete(listOf(ref)) })
            }, { vm.closeSheet() })
        }
    }

    fun onTap(entry: FmEntry) {
        val key = entry.path ?: (if (st.rel.isEmpty()) entry.name else st.rel + "/" + entry.name)
        when {
            selected.isNotEmpty() -> selected = if (key in selected) selected - key else selected + key
            entry.dir -> { st.open(root, refOf(entry).path.split('/')); searching = false; query = "" }
            isAudio(entry.name) -> play(shown, entry)
            isImage(entry.name) -> vm.showSheet { ImageSheet(vm, refOf(entry)) }
            else -> itemMenu(entry)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        askConflict("Upload ${uris.size} file${if (uris.size == 1) "" else "s"}") { policy ->
            vm.run(onDone = { uploadProgress = null; changed(root) }) { c2 ->
                var okCount = 0
                try {
                    uris.forEachIndexed { i, uri ->
                        val (name, size) = withContext(Dispatchers.IO) {
                            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cur ->
                                if (cur.moveToFirst()) cur.getString(0) to cur.getLong(1).takeIf { !cur.isNull(1) } else null
                            } ?: (uri.lastPathSegment.orEmpty().substringAfterLast('/') to null)
                        }
                        val fileName = name.ifBlank { "file-$i" }
                        uploadProgress = SaveProgress(fileName, i + 1, uris.size, 0f)
                        c2.fm.upload(here, fileName, size ?: -1L, policy, { ctx.contentResolver.openInputStream(uri)?.source() ?: throw IOException("Cannot read $fileName") }) { sent ->
                            uploadProgress = SaveProgress(fileName, i + 1, uris.size, if (size != null && size > 0) (sent.toFloat() / size).coerceIn(0f, 1f) else 0f)
                        }
                        okCount++
                    }
                    vm.say("Uploaded $okCount file${if (okCount == 1) "" else "s"}")
                } finally { uploadProgress = null }
            }
        }
    }

    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { LibraryHeader(vm) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(R.drawable.ic_chevron_left, "Up", {
                    if (st.path.isEmpty()) st.open(null) else st.path = st.path.dropLast(1)
                })
                Column(Modifier.weight(1f)) {
                    Txt(st.path.lastOrNull() ?: (root.replaceFirstChar { it.uppercase() }), size = 17.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    Muted("${shown.count { it.dir }} folders · ${shown.count { !it.dir }} files", size = 12.sp)
                }
                GlassIconButton(R.drawable.ic_search, "Search", { searching = !searching; if (!searching) query = "" })
                GlassIconButton(R.drawable.ic_filter, "Sort", {
                    vm.showSheet {
                        MenuSheet("Sort by", null, SORTS.mapIndexed { i, label ->
                            MenuItem(label + if (st.sort == i) (if (st.descending) "  ↓" else "  ↑") else "", R.drawable.ic_list) {
                                if (st.sort == i) st.descending = !st.descending else { st.sort = i; st.descending = i == 1 }
                            }
                        }, { vm.closeSheet() })
                    }
                })
                GlassIconButton(R.drawable.ic_more, "More", {
                    vm.showSheet {
                        MenuSheet("Folder", st.path.joinToString(" / ").ifBlank { root }, buildList {
                            add(MenuItem("New folder", R.drawable.ic_add) { newFolder() })
                            add(MenuItem("Upload files here", R.drawable.ic_upload) { picker.launch(arrayOf("*/*")) })
                            add(MenuItem("Select all", R.drawable.ic_check) { selected = shown.map { it.path ?: (if (st.rel.isEmpty()) it.name else st.rel + "/" + it.name) }.toSet() })
                            add(MenuItem("Refresh", R.drawable.ic_refresh) { listing.refresh() })
                            add(MenuItem("Folder info", R.drawable.ic_info) { showInfo(here, FmEntry(st.path.lastOrNull() ?: root, dir = true)) })
                            add(MenuItem("Save folder as ZIP", R.drawable.ic_download) {
                                if (st.path.isEmpty()) vm.say("Open a folder first") else save(listOf(here), listOf(FmEntry(here.name, dir = true)), true)
                            })
                            add(MenuItem("Trash", R.drawable.ic_trash) { st.showTrash = true })
                            add(MenuItem("Rescan shares now", R.drawable.ic_refresh) { rescanNow(vm, root == "music") })
                        }, { vm.closeSheet() })
                    }
                })
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassChip(root.replaceFirstChar { it.uppercase() }, st.path.isEmpty(), { st.path = emptyList() }, icon = R.drawable.ic_home)
                st.path.forEachIndexed { i, seg -> GlassChip(seg, i == st.path.lastIndex, { st.path = st.path.take(i + 1) }) }
            }
        }
        if (searching) item {
            GlassField(query, { query = it }, "Search in this folder and below", Modifier.fillMaxWidth(), leading = R.drawable.ic_search)
        }
        st.clip?.let { clip ->
            item {
                GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt("${if (clip.move) "Move" else "Copy"} ${clip.items.size} item${if (clip.items.size == 1) "" else "s"}", size = 14.sp, weight = FontWeight.SemiBold)
                            Muted(clip.items.first().name, size = 12.sp, maxLines = 1)
                        }
                        GlassButton({ paste(clip) }, primary = true, height = 42.dp) { Txt("Paste here", weight = FontWeight.SemiBold) }
                        GlassIconButton(R.drawable.ic_close, "Cancel", { st.clip = null }, size = 42.dp)
                    }
                }
            }
        }
        (saveProgress ?: uploadProgress)?.let { p ->
            item {
                GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Txt("${if (uploadProgress != null) "Uploading" else "Saving"} ${p.index}/${p.total}: ${p.name}", size = 13.sp, maxLines = 1)
                        ProgressBar(p.fraction.takeIf { it > 0f })
                    }
                }
            }
        }
        if (selected.isNotEmpty()) stickyHeader {
            val entries = shown.filter { (it.path ?: (if (st.rel.isEmpty()) it.name else st.rel + "/" + it.name)) in selected }
            GlassCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(R.drawable.ic_close, "Clear selection", { selected = emptySet() }, size = 40.dp)
                    Txt("${selected.size}", Modifier.padding(horizontal = 4.dp), size = 16.sp, weight = FontWeight.SemiBold)
                    Box(Modifier.weight(1f))
                    GlassIconButton(R.drawable.ic_save, "Save", { save(selectedRefs, entries, false); selected = emptySet() }, size = 40.dp)
                    GlassIconButton(R.drawable.ic_copy, "Copy", { st.clip = FmClip(false, selectedRefs); selected = emptySet(); vm.say("Copied — open a folder and tap Paste") }, size = 40.dp)
                    GlassIconButton(R.drawable.ic_swap, "Move", { st.clip = FmClip(true, selectedRefs); selected = emptySet(); vm.say("Ready to move — open a folder and tap Paste") }, size = 40.dp)
                    GlassIconButton(R.drawable.ic_trash, "Delete", { delete(selectedRefs) }, size = 40.dp, tint = c.danger)
                    GlassIconButton(R.drawable.ic_more, "More", {
                        vm.showSheet {
                            MenuSheet("${selected.size} selected", null, buildList {
                                add(MenuItem("Save as ZIP", R.drawable.ic_download) { save(selectedRefs, entries, true); selected = emptySet() })
                                if (selectedRefs.size == 1) {
                                    add(MenuItem("Rename", R.drawable.ic_tag) { rename(selectedRefs[0]) })
                                    add(MenuItem("Info", R.drawable.ic_info) { showInfo(selectedRefs[0], entries[0]) })
                                }
                                add(MenuItem("Select all", R.drawable.ic_check) { selected = shown.map { it.path ?: (if (st.rel.isEmpty()) it.name else st.rel + "/" + it.name) }.toSet() })
                            }, { vm.closeSheet() })
                        }
                    }, size = 40.dp)
                }
            }
        }
        val audio = shown.filter { !it.dir && isAudio(it.name) }
        if (!searching && audio.isNotEmpty() && selected.isEmpty()) item {
            GlassButton({ play(shown, audio.first()) }, Modifier.fillMaxWidth(), primary = true, height = 46.dp) {
                GIcon(R.drawable.ic_play, size = 18.dp); Txt("Play ${audio.size} track${if (audio.size == 1) "" else "s"}", weight = FontWeight.SemiBold)
            }
        }
        listing.error?.let { item { ErrorBanner(it, listing::refresh) } }
        searchError?.let { item { ErrorBanner(it) } }
        if (listing.value == null && listing.error == null) item { Loading() }
        if (shown.isEmpty() && listing.value != null) item {
            Empty(if (searching && query.isNotBlank()) "Nothing matches “$query”." else "This folder is empty.", if (searching) R.drawable.ic_search else R.drawable.ic_folder)
        }
        if (searching && hitsTruncated) item { Muted("Showing the first results only — refine the search.", size = 12.sp) }
        items(shown, key = { it.path ?: it.name }) { entry ->
            val key = entry.path ?: (if (st.rel.isEmpty()) entry.name else st.rel + "/" + entry.name)
            FmRow(
                entry, selected = key in selected, selecting = selected.isNotEmpty(),
                subtitle = if (entry.path != null) entry.path.substringBeforeLast('/', "").ifBlank { root } else null,
                onClick = { onTap(entry) },
                onLongClick = { selected = if (key in selected) selected - key else selected + key },
                onMore = { itemMenu(entry) },
            )
        }
    }
}

@Composable
private fun FmRow(entry: FmEntry, selected: Boolean, selecting: Boolean, subtitle: String?, onClick: () -> Unit, onLongClick: () -> Unit, onMore: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().glass(cardShape(20.dp), GlassLevel.Card, tint = if (selected) c.accentA.copy(alpha = 0.35f) else Color.Unspecified)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon(
            when { selected -> R.drawable.ic_check; entry.dir -> R.drawable.ic_folder; isAudio(entry.name) -> R.drawable.ic_music; else -> R.drawable.ic_file },
            tint = when { selected -> c.accentB; entry.dir -> c.accentB; else -> c.accentA },
        )
        Column(Modifier.weight(1f)) {
            Txt(entry.name, size = 14.5.sp, weight = if (entry.dir) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2)
            val meta = listOfNotNull(subtitle, if (entry.dir) null else fmtBytes(entry.size), fmtEpoch(entry.mtime).ifBlank { null }).joinToString(" · ")
            if (meta.isNotBlank()) Muted(meta, size = 11.5.sp, maxLines = 1)
        }
        if (!selecting) GlassIconButton(R.drawable.ic_more, "Actions", onMore, size = 36.dp)
    }
}

// ---------------- sheets ----------------

@Composable
private fun ColumnScope.InfoSheet(vm: AppVm, ref: FmRef, entry: FmEntry) {
    val api = vm.api ?: return
    val info = rememberPoll(ref) { api.fm.info(ref) }
    Txt(entry.name, size = 18.sp, weight = FontWeight.SemiBold, maxLines = 3)
    Muted("${ref.root}/${ref.path}", size = 12.sp)
    if (info.value == null && info.error == null) Loading()
    info.error?.let { ErrorBanner(it, info::refresh) }
    val i = info.value ?: return
    if (i.dir) {
        KeyValue("Files", i.files.toString() + if (i.truncated) "+" else "")
        KeyValue("Folders", i.dirs.toString() + if (i.truncated) "+" else "")
        KeyValue("Size", fmtBytes(i.size) + if (i.truncated) "+" else "")
    } else {
        KeyValue("Size", fmtBytes(i.size))
        i.mime?.let { KeyValue("Type", it) }
    }
    fmtEpoch(i.mtime).takeIf { it.isNotBlank() }?.let { KeyValue("Modified", it) }
    i.audio?.let { a ->
        a.title?.let { KeyValue("Title", it) }
        a.artist?.let { KeyValue("Artist", it) }
        a.album?.let { KeyValue("Album", it) }
        a.albumArtist?.takeIf { it != a.artist }?.let { KeyValue("Album artist", it) }
        a.date?.let { KeyValue("Date", it) }
        listOfNotNull(a.disc?.let { "disc $it" }, a.track?.let { "track $it" }).takeIf { it.isNotEmpty() }?.let { KeyValue("Position", it.joinToString(" · ")) }
        a.genre?.let { KeyValue("Genre", it) }
        a.duration?.let { KeyValue("Duration", "%d:%02d".format((it / 60).toInt(), (it % 60).toInt())) }
        KeyValue("Format", listOfNotNull(a.codec?.uppercase(), a.bitDepth?.let { "$it-bit" }, a.sampleRate?.let { "${"%.1f".format(it / 1000.0)} kHz" }, a.channels?.let { if (it == 2) "stereo" else "$it ch" }).joinToString(" · "))
        a.bitrate?.let { KeyValue("Bitrate", "${it / 1000} kbps") }
    }
}

@Composable
private fun ColumnScope.ImageSheet(vm: AppVm, ref: FmRef) {
    val api = vm.api ?: return
    Txt(ref.name, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 2)
    val state = produceState<Result<androidx.compose.ui.graphics.ImageBitmap>?>(null, ref) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                val bytes = api.callUrl(api.fm.fileUrl(ref), client = api.xferHttp).use { res ->
                    if (res.body.contentLength() > 40_000_000) throw IOException("Image too large to preview")
                    res.body.bytes()
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: throw IOException("Not an image the phone can show")
                bmp.asImageBitmap()
            }
        }
    }
    when (val r = state.value) {
        null -> Loading()
        else -> r.fold(
            { Image(it, ref.name, Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(top = 10.dp), contentScale = ContentScale.Fit) },
            { ErrorBanner(it.message ?: "Cannot show this image") },
        )
    }
}

// ---------------- trash ----------------

@Composable
private fun TrashPage(vm: AppVm) {
    val api = vm.api ?: return
    val trash = rememberPoll(api) { api.fm.trash() }
    val st = vm.fm
    BackHandler { st.showTrash = false }
    LazyColumn(contentPadding = pagePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { LibraryHeader(vm) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(R.drawable.ic_chevron_left, "Back", { st.showTrash = false })
                Column(Modifier.weight(1f)) {
                    Txt("Trash", size = 20.sp, weight = FontWeight.Bold)
                    Muted("Items are removed for good after ${trash.value?.days ?: 30} days", size = 12.sp)
                }
                if (trash.value?.items?.isNotEmpty() == true) GlassButton({
                    vm.showSheet {
                        ConfirmSheet("Empty the trash?", "Everything in the trash is deleted permanently.", "Empty trash", true, {
                            vm.run("Trash emptied", onDone = trash::refresh) { it.fm.purge() }
                        }, { vm.closeSheet() })
                    }
                }, height = 42.dp) { Txt("Empty", weight = FontWeight.Medium, color = LocalColors.current.danger) }
            }
        }
        trash.error?.let { item { ErrorBanner(it, trash::refresh) } }
        if (trash.value == null && trash.error == null) item { Loading() }
        if (trash.value?.items?.isEmpty() == true) item { Empty("The trash is empty.", R.drawable.ic_trash) }
        items(trash.value?.items.orEmpty(), key = { it.id }) { item -> TrashRow(vm, item, trash::refresh) }
    }
}

@Composable
private fun TrashRow(vm: AppVm, item: FmTrashItem, refresh: () -> Unit) {
    val c = LocalColors.current
    GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(if (item.dir) R.drawable.ic_folder else R.drawable.ic_file, tint = c.muted)
            Column(Modifier.weight(1f)) {
                Txt(item.name, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 2)
                Muted("${item.root}/${item.path.substringBeforeLast('/', "")} · ${fmtBytes(item.size)} · ${fmtEpoch(item.deleted)}", size = 11.5.sp, maxLines = 2)
            }
            GlassIconButton(R.drawable.ic_refresh, "Restore", {
                vm.run("Restored", onDone = { refresh(); vm.fmChanged(item.root) }) { it.fm.restore(listOf(item.id)) }
            }, size = 38.dp)
            GlassIconButton(R.drawable.ic_trash, "Delete forever", {
                vm.showSheet {
                    ConfirmSheet("Delete forever?", "“${item.name}” cannot be recovered.", "Delete", true, {
                        vm.run("Deleted", onDone = refresh) { it.fm.purge(listOf(item.id)) }
                    }, { vm.closeSheet() })
                }
            }, size = 38.dp, tint = c.danger)
        }
    }
}
