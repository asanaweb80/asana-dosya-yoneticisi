package tr.com.asana.dosyayoneticisi

import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque

data class ScanResult(val indexed: Int, val large: Int, val oldApks: Int, val emptyFolders: Int)

/** Only scans documents within a directory tree explicitly granted by the user. */
object SafeFileScanner {
    fun walk(root: DocumentFile, limit: Int = 2000): List<Pair<String, DocumentFile>> {
        val result = mutableListOf<Pair<String, DocumentFile>>()
        val queue = ArrayDeque<Pair<String, DocumentFile>>()
        queue.add("" to root)
        while (queue.isNotEmpty() && result.size < limit) {
            val (prefix, folder) = queue.removeFirst()
            val children = try { folder.listFiles().toList() } catch (_: Exception) { emptyList() }
            for (entry in children) {
                if (result.size >= limit) break
                val path = prefix + "/" + (entry.name ?: "")
                result.add(path to entry)
                if (entry.isDirectory && queue.size < limit) queue.add(path to entry)
            }
        }
        return result
    }

    fun matches(file: DocumentFile, query: String, suffix: String, minimumBytes: Long): Boolean {
        val name = file.name ?: ""
        if (query.isNotBlank() && !name.contains(query, ignoreCase = true)) return false
        val ext = suffix.trim().removePrefix(".")
        if (ext.isNotEmpty() && !name.endsWith("." + ext, ignoreCase = true)) return false
        if (minimumBytes > 0 && (file.isDirectory || file.length() < minimumBytes)) return false
        return true
    }

    fun report(files: List<Pair<String, DocumentFile>>): ScanResult {
        val cutoff = System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L
        return ScanResult(
            indexed = files.size,
            large = files.count { !it.second.isDirectory && it.second.length() >= 100L * 1024L * 1024L },
            oldApks = files.count {
                val entry = it.second
                !entry.isDirectory && (entry.name ?: "").endsWith(".apk", ignoreCase = true) &&
                    entry.lastModified() in 1 until cutoff
            },
            emptyFolders = files.count {
                it.second.isDirectory && try { it.second.listFiles().isEmpty() } catch (_: Exception) { false }
            }
        )
    }
}
