package tr.com.asana.dosyayoneticisi

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BrowseActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private var folder: DocumentFile? = null
    private val history = mutableListOf<DocumentFile>()
    private var category: String? = null
    private var query = ""
    private var sort = 0
    private var recentTab = false
    private val ink = Color.rgb(30, 35, 48)
    private val grey = Color.rgb(139, 145, 158)
    private val accent = Color.rgb(71, 108, 219)

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun round(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(15).toFloat()
    }
    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = getPreferences(MODE_PRIVATE).getString("folder_uri", null)
        if (saved != null) {
            try {
                val uri = Uri.parse(saved)
                if (contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission })
                    folder = DocumentFile.fromTreeUri(this, uri)
            } catch (_: Exception) { }
        }
        render()
    }

    private fun render() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val scroller = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
        scroller.addView(list)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        header()
        if (recentTab) recent() else { categories(); folders() }
        bottomBar()
        setContentView(root)
    }

    private fun header() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(if (recentTab) "Son kullanılanlar" else "Gözat", 32f, ink, true),
            LinearLayout.LayoutParams(0, dp(65), 1f))
        val controls = listOf("⌕", "☷", "⋮")
        controls.forEachIndexed { i, icon ->
            row.addView(label(icon, 27f, ink, true).apply {
                gravity = Gravity.CENTER
                contentDescription = listOf("Arama", "Filtre", "Menü")[i]
                setOnClickListener {
                    when (i) {
                        0 -> searchDialog()
                        1 -> AlertDialog.Builder(this@BrowseActivity).setTitle("Sıralama")
                            .setItems(arrayOf("İsim", "En yeni", "En büyük")) { _, n -> sort = n; render() }.show()
                        else -> AlertDialog.Builder(this@BrowseActivity).setItems(arrayOf("Klasör seç", "Filtreleri temizle")) { _, n ->
                            if (n == 0) pickFolder() else { query = ""; category = null; render() }
                        }.show()
                    }
                }
            }, LinearLayout.LayoutParams(dp(42), dp(48)))
        }
        list.addView(row)
    }

    private fun categories() {
        val names = listOf("Belgeler", "Görüntüler", "Videolar", "Müzik")
        val glyphs = listOf("▤", "▧", "▶", "♫")
        val pastel = listOf("#FFF3D3", "#F1E4FF", "#E1EFFF", "#FFE5E8")
        val iconColors = listOf("#E1A324", "#986DCD", "#528ADB", "#E46B79")
        for (start in listOf(0, 2)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (idx in start..start+1) {
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = round(Color.parseColor(pastel[idx]))
                    setPadding(dp(17), dp(15), dp(10), dp(12))
                    setOnClickListener { category = if (category == names[idx]) null else names[idx]; render() }
                }
                tile.addView(label(glyphs[idx], 29f, Color.parseColor(iconColors[idx]), true))
                tile.addView(label(names[idx], 16f, ink, true))
                row.addView(tile, LinearLayout.LayoutParams(0, dp(106), 1f).apply {
                    if (idx % 2 == 1) marginStart = dp(12)
                })
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
    }

    private fun folders() {
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label(category ?: "Dahili depolama", 21f, ink, true),
            LinearLayout.LayoutParams(0, dp(76), 1f))
        heading.addView(label(if (category == null) "Değiştir" else "Temizle", 13f, accent, true).apply {
            setOnClickListener { if (category == null) pickFolder() else { category = null; render() } }
        })
        list.addView(heading)
        if (history.isNotEmpty()) {
            list.addView(label("‹  Üst klasör", 15f, accent, true).apply {
                setPadding(0, dp(5), 0, dp(13))
                setOnClickListener { folder = history.removeAt(history.lastIndex); render() }
            })
        }
        val dir = folder
        if (dir == null) { list.addView(label("Klasörleri görüntülemek için Değiştir'e dokunup erişim izni verin.", 14f, grey)); return }
        val files = try {
            dir.listFiles().filter { matches(it) }.sortedWith(
                when (sort) {
                    1 -> compareByDescending<DocumentFile> { it.lastModified() }
                    2 -> compareByDescending<DocumentFile> { it.length() }
                    else -> compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name ?: "" }
                }
            )
        } catch (_: Exception) { emptyList() }
        if (files.isEmpty()) list.addView(label("Bu klasörde uygun dosya bulunamadı.", 14f, grey))
        files.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    if (file.isDirectory) { folder?.let { history.add(it) }; folder = file; category = null; render() }
                    else open(file)
                }
            }
            row.addView(label(if (file.isDirectory) "▰" else "▤", 25f, Color.rgb(218, 169, 86), true).apply {
                gravity = Gravity.CENTER; background = round(Color.rgb(255, 247, 226))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
            content.addView(label(file.name ?: "Adsız", 15f, ink, true).apply { maxLines = 1 })
            val date = if (file.lastModified() > 0)
                SimpleDateFormat("dd/MM/yyyy", Locale("tr", "TR")).format(Date(file.lastModified())) else "Tarih yok"
            val extra = if (file.isDirectory) {
                try { file.listFiles().size.toString() + " öğe" } catch (_: Exception) { "Klasör" }
            } else file.length().toString() + " B"
            content.addView(label(date + "  |  " + extra, 12f, grey))
            row.addView(content, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label("›", 27f, grey))
            list.addView(row, LinearLayout.LayoutParams(-1, dp(80)))
        }
    }

    private fun matches(file: DocumentFile): Boolean {
        val name = file.name ?: ""
        if (query.isNotBlank() && !name.contains(query, true)) return false
        if (category == null) return true
        if (file.isDirectory) return false
        val mime = file.type ?: ""
        return when (category) {
            "Belgeler" -> mime.startsWith("text/") || mime == "application/pdf" ||
                listOf(".doc", ".docx", ".pdf", ".xls", ".xlsx", ".txt").any { name.endsWith(it, true) }
            "Görüntüler" -> mime.startsWith("image/")
            "Videolar" -> mime.startsWith("video/")
            "Müzik" -> mime.startsWith("audio/")
            else -> true
        }
    }

    private fun bottomBar() {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("◷\nSon kullanılanlar", "▣\nGözat").forEachIndexed { i, title ->
            bar.addView(label(title, 13f, if (recentTab == (i == 0)) accent else grey, true).apply {
                gravity = Gravity.CENTER; setOnClickListener { recentTab = i == 0; render() }
            }, LinearLayout.LayoutParams(0, dp(66), 1f))
        }
        root.addView(bar)
    }

    private fun recent() {
        val raw = getPreferences(MODE_PRIVATE).getString("recent", "") ?: ""
        val items = raw.split("\n").filter { it.isNotBlank() }.take(20)
        if (items.isEmpty()) list.addView(label("Henüz son kullanılan dosya yok.", 14f, grey))
        items.forEach { entry ->
            try {
                val file = DocumentFile.fromSingleUri(this, Uri.parse(entry))
                if (file != null && file.exists()) {
                    list.addView(label(file.name ?: "Dosya", 16f).apply {
                        setPadding(dp(8), dp(17), 0, dp(17))
                        setOnClickListener { open(file) }
                    })
                }
            } catch (_: Exception) { }
        }
    }

    private fun searchDialog() {
        val input = EditText(this).apply { setText(query); hint = "Dosya adı" }
        AlertDialog.Builder(this).setTitle("Dosya ara").setView(input)
            .setPositiveButton("Ara") { _, _ -> query = input.text.toString().trim(); render() }
            .setNegativeButton("İptal", null).show()
    }

    private fun pickFolder() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, 501)
    }

    @Deprecated("Legacy SAF activity callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 501 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            folder = DocumentFile.fromTreeUri(this, uri)
            history.clear()
            getPreferences(MODE_PRIVATE).edit().putString("folder_uri", uri.toString()).apply()
            render()
        } catch (_: Exception) { Toast.makeText(this, "İzin alınamadı", Toast.LENGTH_SHORT).show() }
    }

    private fun open(file: DocumentFile) {
        try {
            val prefs = getPreferences(MODE_PRIVATE)
            val previous = prefs.getString("recent", "") ?: ""
            val entries = (listOf(file.uri.toString()) + previous.split("\n"))
                .filter { it.isNotBlank() }.distinct().take(20)
            prefs.edit().putString("recent", entries.joinToString("\n")).apply()
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(file.uri, file.type ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) { Toast.makeText(this, "Dosya açılamadı", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Legacy back navigation")
    override fun onBackPressed() {
        if (category != null) { category = null; render() }
        else if (history.isNotEmpty()) { folder = history.removeAt(history.lastIndex); render() }
        else super.onBackPressed()
    }
}
