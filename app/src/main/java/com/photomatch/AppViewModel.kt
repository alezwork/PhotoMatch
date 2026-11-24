package com.photomatch

import android.app.Application
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photomatch.core.NativeCore
import com.photomatch.data.MediaDetails
import com.photomatch.data.MediaItem
import com.photomatch.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Deck : Screen
    data object Similar : Screen
    data class Group(val id: Int) : Screen
    data object Timeline : Screen
    data object Trash : Screen
    data object Favorites : Screen
}

sealed interface Scope {
    val title: String
    data object All : Scope { override val title = "Вся медиатека" }
    data object Videos : Scope { override val title = "Только видео" }
    data class Month(val key: Int, override val title: String) : Scope
    data class Similar(val group: Int) : Scope { override val title = "Похожие снимки" }
}

data class MonthBucket(val key: Int, val label: String, val year: Int, val start: Int, val end: Int) {
    val count get() = end - start
}

data class SimilarGroup(val id: Int, val items: List<MediaItem>)

data class Progress(val done: Int, val total: Int) {
    val fraction get() = if (total == 0) 1f else done.toFloat() / total
    val running get() = done < total
}

private data class HistoryEntry(val item: MediaItem, val previous: Int, val deckPos: Int)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val store = NativeCore.storeOpen(app.filesDir.resolve("core").absolutePath)

    // ---- observable state -------------------------------------------------------------
    var screen by mutableStateOf<Screen>(Screen.Onboarding); private set
    var loading by mutableStateOf(false); private set
    var items by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var months by mutableStateOf<List<MonthBucket>>(emptyList()); private set
    var groups by mutableStateOf<List<SimilarGroup>>(emptyList()); private set
    var hashing by mutableStateOf(Progress(0, 0)); private set

    var scope by mutableStateOf<Scope>(Scope.All); private set
    var deck by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var deckPos by mutableIntStateOf(0); private set
    /** Bumped on every decision change so screens can recompute derived data. */
    var version by mutableIntStateOf(0); private set
    var canUndo by mutableStateOf(false); private set

    private val decisions = HashMap<Long, Int>()
    private val index = HashMap<Long, Int>()
    private var momentOf = IntArray(0)
    private var groupOf = IntArray(0)
    private var groupById: Map<Int, SimilarGroup> = emptyMap()
    private val history = ArrayDeque<HistoryEntry>()
    private var hashJob: Job? = null

    // ---- navigation ---------------------------------------------------------------------
    private val backStack = ArrayDeque<Screen>()

    fun open(s: Screen) {
        if (s == screen) return
        backStack.addLast(screen); screen = s
    }

    fun back(): Boolean {
        val prev = backStack.removeLastOrNull() ?: return false
        screen = prev
        return true
    }

    // ---- loading ------------------------------------------------------------------------
    fun onPermission(granted: Boolean) {
        if (!granted) { screen = Screen.Onboarding; return }
        if (screen == Screen.Onboarding) { screen = Screen.Home; backStack.clear() }
        reload()
    }

    fun reload() = viewModelScope.launch {
        loading = items.isEmpty()
        val all = repo.loadAll()
        class Prepared(val dec: HashMap<Long, Int>, val visible: List<MediaItem>, val idx: HashMap<Long, Int>, val moments: IntArray, val months: List<MonthBucket>)
        val prep = withContext(Dispatchers.Default) {
            val snap = NativeCore.storeDecisions(store)
            val dec = HashMap<Long, Int>(snap.size)
            for (i in snap.indices step 2) dec[snap[i]] = snap[i + 1].toInt()
            val visible = all.filter { dec[it.id] != NativeCore.DELETED }
            val idx = HashMap<Long, Int>(visible.size * 2)
            visible.forEachIndexed { i, m -> idx[m.id] = i }
            val times = LongArray(visible.size) { visible[it].time }
            Prepared(dec, visible, idx, NativeCore.moments(times, 45 * 60_000L), buildMonths(visible))
        }
        decisions.clear(); decisions.putAll(prep.dec)
        index.clear(); index.putAll(prep.idx)
        momentOf = prep.moments
        val visible = prep.visible
        items = visible
        months = prep.months
        groupOf = IntArray(visible.size) { -1 }
        rebuildGroups()
        rebuildDeck()
        loading = false
        version++
        startHashing()
    }

    private fun buildMonths(list: List<MediaItem>): List<MonthBucket> {
        val out = ArrayList<MonthBucket>()
        val zone = ZoneId.systemDefault()
        var start = 0
        var key = -1
        fun flush(end: Int) {
            if (key < 0) return
            val y = key / 100; val m = key % 100
            val name = java.time.Month.of(m).getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
            out += MonthBucket(key, name.replaceFirstChar { it.titlecase() }, y, start, end)
        }
        list.forEachIndexed { i, it ->
            val d = Instant.ofEpochMilli(it.time).atZone(zone)
            val k = d.year * 100 + d.monthValue
            if (k != key) { flush(i); key = k; start = i }
        }
        flush(list.size)
        return out
    }

    // ---- hashing / similar ---------------------------------------------------------------
    private fun startHashing() {
        hashJob?.cancel()
        val snapshot = items
        hashJob = viewModelScope.launch(Dispatchers.IO) {
            val ids = LongArray(snapshot.size) { snapshot[it].id }
            val missing = NativeCore.missingHashes(store, ids)
            val total = missing.size
            hashing = Progress(0, total)
            if (total == 0) return@launch
            val byId = HashMap<Long, MediaItem>(snapshot.size * 2).apply { snapshot.forEach { put(it.id, it) } }
            val side = NativeCore.HASH_SIDE
            val batch = 192
            val workers = 4
            var done = 0
            var sinceRegroup = 0
            for (chunkStart in 0 until total step batch * workers) {
                val parts = (0 until workers).mapNotNull { w ->
                    val s = chunkStart + w * batch
                    if (s >= total) null else s until minOf(s + batch, total)
                }
                parts.map { range ->
                    async {
                        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        val paint = MediaRepository.grayPaint()
                        val buf = ByteArray(range.count() * side * side)
                        val okIds = ArrayList<Long>(range.count())
                        var o = 0
                        for (k in range) {
                            val m = byId[missing[k]] ?: continue
                            if (repo.grayThumb(m.uri, buf, o, bmp, canvas, paint)) { okIds += m.id; o += side * side }
                        }
                        NativeCore.hashAndStore(store, okIds.toLongArray(), buf.copyOf(o))
                        bmp.recycle()
                    }
                }.awaitAll()
                done = minOf(total, chunkStart + batch * workers)
                hashing = Progress(done, total)
                sinceRegroup += batch * workers
                if (sinceRegroup >= 1500) { sinceRegroup = 0; withContext(Dispatchers.Main) { rebuildGroups() } }
            }
            withContext(Dispatchers.Main) { rebuildGroups(); version++ }
        }
    }

    private suspend fun rebuildGroups() {
        val list = items
        val ids = LongArray(list.size) { list[it].id }
        val times = LongArray(list.size) { list[it].time }
        val labels = withContext(Dispatchers.Default) {
            NativeCore.similarGroups(store, ids, times, 10, 3 * 3600_000L)
        }
        if (list !== items) return
        groupOf = labels
        val map = HashMap<Int, MutableList<MediaItem>>()
        labels.forEachIndexed { i, g -> if (g >= 0) map.getOrPut(g) { ArrayList() } += list[i] }
        val built = map.entries.sortedBy { it.key }.map { SimilarGroup(it.key, it.value) }
        groupById = built.associateBy { it.id }
        groups = built
    }

    fun groupFor(item: MediaItem): SimilarGroup? {
        val i = index[item.id] ?: return null
        val g = groupOf.getOrElse(i) { -1 }
        if (g < 0) return null
        return groupById[g]
    }

    fun sameMoment(item: MediaItem, limit: Int = 40): List<MediaItem> {
        val i = index[item.id] ?: return emptyList()
        val list = items
        val m = momentOf.getOrElse(i) { return emptyList() }
        var a = i; var b = i
        while (a > 0 && momentOf[a - 1] == m && i - a < limit) a--
        while (b < list.size - 1 && momentOf[b + 1] == m && b - i < limit) b++
        return list.subList(a, b + 1)
    }

    suspend fun details(item: MediaItem): MediaDetails = repo.details(item)
    suspend fun palette(item: MediaItem): List<Int> = repo.palette(item)

    // ---- deck -----------------------------------------------------------------------------
    fun startDeck(s: Scope) {
        scope = s
        rebuildDeck()
        open(Screen.Deck)
    }

    private fun rebuildDeck() {
        val base: List<MediaItem> = when (val s = scope) {
            Scope.All -> items
            Scope.Videos -> items.filter { it.isVideo }
            is Scope.Month -> months.firstOrNull { it.key == s.key }?.let { items.subList(it.start, it.end) } ?: emptyList()
            is Scope.Similar -> groups.firstOrNull { it.id == s.group }?.items ?: emptyList()
        }
        deck = base.filter { (decisions[it.id] ?: 0) == NativeCore.NONE }
        deckPos = 0
        history.clear(); canUndo = false
    }

    fun decide(item: MediaItem, decision: Int) {
        val prev = decisions[item.id] ?: 0
        setDecision(listOf(item), decision)
        if (deck.getOrNull(deckPos)?.id == item.id) {
            history.addLast(HistoryEntry(item, prev, deckPos))
            if (history.size > 200) history.removeFirst()
            deckPos++
            canUndo = true
        }
    }

    /** Reverts the last swipe; returns the item and the decision that was undone. */
    fun undo(): Pair<MediaItem, Int>? {
        val h = history.removeLastOrNull() ?: return null
        val undone = decisions[h.item.id] ?: 0
        setDecision(listOf(h.item), h.previous)
        deckPos = h.deckPos
        canUndo = history.isNotEmpty()
        return h.item to undone
    }

    fun setDecision(list: List<MediaItem>, decision: Int) {
        if (list.isEmpty()) return
        NativeCore.storeSet(store, LongArray(list.size) { list[it].id }, decision)
        for (m in list) if (decision == 0) decisions.remove(m.id) else decisions[m.id] = decision
        version++
    }

    fun decisionOf(item: MediaItem): Int = decisions[item.id] ?: 0

    fun itemsWith(decision: Int): List<MediaItem> = items.filter { decisions[it.id] == decision }

    fun reviewedIn(b: MonthBucket): Int {
        var n = 0
        for (i in b.start until b.end) if ((decisions[items[i].id] ?: 0) != 0) n++
        return n
    }

    /** Keep [best] from a similar group, send the rest to the bin. */
    fun keepBest(group: SimilarGroup, best: Set<Long>) {
        val (keep, nope) = group.items.partition { it.id in best }
        setDecision(keep, NativeCore.KEEP)
        setDecision(nope, NativeCore.NOPE)
    }

    data class Stats(val total: Int, val reviewed: Int, val keep: Int, val nope: Int, val fav: Int, val nopeBytes: Long)

    fun stats(): Stats {
        var k = 0; var n = 0; var f = 0; var bytes = 0L
        for (m in items) when (decisions[m.id]) {
            NativeCore.KEEP -> k++
            NativeCore.NOPE -> { n++; bytes += m.size }
            NativeCore.SUPER -> f++
        }
        return Stats(items.size, k + n + f, k, n, f, bytes)
    }

    // ---- system requests ------------------------------------------------------------------
    private var pendingTrash: List<MediaItem> = emptyList()

    fun trashRequest(): IntentSender? {
        val list = itemsWith(NativeCore.NOPE).take(2000)
        if (list.isEmpty()) return null
        pendingTrash = list
        return MediaStore.createTrashRequest(getApplication<Application>().contentResolver, list.map { it.uri }, true).intentSender
    }

    fun onTrashResult(ok: Boolean) {
        if (ok) {
            setDecision(pendingTrash, NativeCore.DELETED)
            reload()
        }
        pendingTrash = emptyList()
    }

    fun favoriteRequest(): IntentSender? {
        val list = itemsWith(NativeCore.SUPER)
        if (list.isEmpty()) return null
        return MediaStore.createFavoriteRequest(getApplication<Application>().contentResolver, list.map { it.uri }, true).intentSender
    }

    fun resetAll() {
        NativeCore.storeReset(store)
        reload()
    }

    override fun onCleared() {
        hashJob?.cancel()
        NativeCore.storeClose(store)
    }
}
