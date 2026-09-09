package expo.modules.fieldagent

import java.io.File

/**
 * Bounded, on-disk outbox.
 *
 * On disk, not in memory: a queue that dies with the process is a queue that
 * lies. One line per point, `clientId \t payload`; org.json escapes every
 * control character, so neither separator can appear inside the payload.
 *
 * No android.* import here either — the whole thing is exercised on a plain JVM.
 *
 * ponytail: full rewrite on removal. At queueSize = 1000 that is ~200 KB every
 * flush, which is nothing next to one HTTP round trip. Move to SQLite only if
 * someone actually needs a five-figure queue.
 */
class Queue(private val file: File, private val maxSize: Int) {

    data class Entry(val clientId: String, val payload: String)

    private val entries = ArrayDeque<Entry>()
    private var loaded = false

    @Synchronized
    fun size(): Int {
        load()
        return entries.size
    }

    /** Returns the number of points dropped to stay under the cap. */
    @Synchronized
    fun add(entry: Entry): Int {
        require(!entry.clientId.contains('\t') && !entry.clientId.contains('\n')) {
            "clientId ne doit contenir ni tabulation ni retour a la ligne"
        }
        load()
        entries.addLast(entry)

        var dropped = 0
        while (entries.size > maxSize) {
            entries.removeFirst()
            dropped++
        }

        if (dropped > 0) rewrite() else append(entry)
        return dropped
    }

    @Synchronized
    fun peek(limit: Int): List<Entry> {
        load()
        if (limit <= 0) return emptyList()
        return entries.take(limit)
    }

    /**
     * Removal is by id, never by count. `drop(n)` deletes the wrong points as
     * soon as anything was queued while the batch was in flight.
     */
    @Synchronized
    fun remove(ids: Collection<String>): Int {
        if (ids.isEmpty()) return 0
        load()
        val wanted = ids.toHashSet()
        val before = entries.size
        entries.retainAll { it.clientId !in wanted }
        val removed = before - entries.size
        if (removed > 0) rewrite()
        return removed
    }

    @Synchronized
    fun clear() {
        load()
        entries.clear()
        rewrite()
    }

    private fun load() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        runCatching {
            val text = file.readText()
            // Only a line terminated by a newline was fully written. A process
            // killed mid-append loses that last line and nothing else.
            val lines = text.split('\n').dropLast(1)
            for (line in lines) {
                val separator = line.indexOf('\t')
                if (separator > 0 && separator < line.length - 1) {
                    entries.addLast(Entry(line.substring(0, separator), line.substring(separator + 1)))
                }
            }
        }
        while (entries.size > maxSize) entries.removeFirst()
    }

    private fun append(entry: Entry) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(entry.clientId + "\t" + entry.payload + "\n")
        }
    }

    private fun rewrite() {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.bufferedWriter().use { writer ->
                entries.forEach { writer.append(it.clientId).append('\t').append(it.payload).append('\n') }
            }
            // Rename over the original: a crash between the two leaves the old
            // queue intact rather than a half-written one.
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
        }
    }
}
