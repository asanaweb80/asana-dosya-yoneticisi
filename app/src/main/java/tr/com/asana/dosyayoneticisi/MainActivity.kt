package tr.com.asana.dosyayoneticisi

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

/** Privacy-first SAF explorer: accesses only user-granted document trees. */
class MainActivity : Activity() {
    private val requestFolder = 501
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var grantedRoot: DocumentFile? = null
    private var current: DocumentFile? = null
    private val history = mutableListOf<DocumentFile>()
    private var category: String? = null
    private var grid = false
    private var mode = 0  // 0 system, 1 light, 2 AMOLED
    private var accent = Color.rgb(0, 112, 121)
    private var foreground = Color.rgb(21, 36, 43)
    private var background = Color.rgb(248, 250, 252)
    private var surface = Color.WHITE
    private var secondary = Color.rgb(104, 119, 128)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getPreferences(MODE_PRIVATE)
        mode = prefs.getInt("theme", 0)
        grid = prefs.getBoolean("grid", false)
        val uriString = prefs.getString("folder_uri", null)
        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                if (contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
                    grantedRoot = DocumentFile.fromTreeUri(this, uri)
                    current = grantedRoot
                }
            } catch (_: Exception) { }
        }
        buildShell()
        render()
    }

    private fun night(): Boolean = mode == 2 || (mode == 0 &&
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)

    private fun colors() {
        val dark = night()
        foreground = if (dark) Color.WHITE else Color.rgb(21, 36, 43)
        background = if (dark) Color.BLACK else Color.rgb(248, 250, 252)
        surface = if (dark) Color.rgb(22, 24, 29) else Color.WHITE
        secondary = if (dark) Color.rgb(174, 186, 192) else Color.rgb(104, 119, 128)
        accent = if (Build.VERSION.SDK_INT >= 31) {
            getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
        } else if (dark) Color.rgb(108, 214, 218) else Color.rgb(0, 112, 121)
        window.statusBarColor = background
        window.navigationBarColor = background
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    private fun buildShell() {
        colors()
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(background) }
        val scroll = ScrollView(this).apply { fillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun render() {
        content.removeAllViews()
        title("ASANA", 13f, accent)
        title("Dosya Yöneticisi", 28f, foreground)
        text("Ücretsiz • Reklamsız • Güvenli", 12f, secondary)
        space(13)
        storageCard()
        space(12)
        actions()
        space(16)
        if (current == null) {
            card("Başlamak için klasör seç", "Android'in güvenli klasör seçicisinden erişim izni verin. Yalnızca izin verdiğiniz dosyalar görüntülenir.")
            categoryCards()
            return
        }
        if (category != null) {
            rowTitle("Kategori: " + category, "← Geri") { category = null; render() }
        } else if (history.isNotEmpty()) {
            rowTitle(current?.name ?: "Klasör", "← Üst klasör") { goBack() }
        } else {
            title("Kategoriler", 19f, foreground)
            categoryCards()
            space(14)
            title("Dosyalarım", 19f, foreground)
            text("Seçilen klasör: " + (current?.name ?: "Bilinmiyor"), 12f, secondary)
        }
        fileItems()
        space(12)
        text("v0.1 • Dosyalar salt okunur • Kategoriler seçili klasör kapsamındadır", 11f, secondary)
    }

    private fun actions() {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(action("📂 Klasör seç") { pickFolder() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        r.addView(action(if (grid) "☷ Liste" else "▦ Izgara") {
            grid = !grid
            getPreferences(MODE_PRIVATE).edit().putBoolean("grid", grid).apply()
            render()
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(8) })
        r.addView(action("◐ Tema") {
            mode = (mode + 1) % 3
            getPreferences(MODE_PRIVATE).edit().putInt("theme", mode).apply()
            buildShell()
            render()
            Toast.makeText(this, arrayOf("Sistem teması", "Açık tema", "AMOLED siyah")[mode], Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(8) })
        content.addView(r)
    }

    private fun storageCard() {
        val box = panel()
        box.addView(textView("DEPOLAMA DURUMU", 12f, accent, true))
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            if (total > 0) {
                box.addView(textView(size(total - free) + " kullanılıyor", 22f, foreground, true))
                box.addView(textView(size(free) + " boş / " + size(total) + " toplam", 13f, secondary, false))
                val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 1000
                    this.progress = (((total - free).toDouble() / total) * 1000).toInt()
                    progressTintList = android.content.res.ColorStateList.valueOf(accent)
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(secondary)
                }
                box.addView(progress, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(14) })
                box.addView(textView("Cihazın paylaşılan depolama alanı • yaklaşık", 11f, secondary, false))
            }
        } catch (_: Exception) {
            box.addView(textView("Depolama bilgisi alınamadı", 13f, secondary, false))
        }
        content.addView(box)
    }

    private fun categoryCards() {
        val items = listOf(
            Triple("▧", "Görseller", "#E48B60"),
            Triple("▶", "Videolar", "#698FE2"),
            Triple("♫", "Müzikler", "#9C77DB"),
            Triple("▤", "Belgeler", "#51A896"),
            Triple("⬡", "APK'lar", "#D5A24D"),
            Triple("◫", "Büyük Dosyalar", "#C16F95")
        )
        for (pair in items.chunked(2)) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, item ->
                val tile = panel().apply {
                    setOnClickListener {
                        if (current == null) Toast.makeText(this@MainActivity, "Önce klasör seçin", Toast.LENGTH_SHORT).show()
                        else { category = item.second; render() }
                    }
                }
                tile.addView(textView(item.first, 29f, Color.parseColor(item.third), true))
                tile.addView(textView(item.second, 15f, foreground, true))
                line.addView(tile, LinearLayout.LayoutParams(0, dp(104), 1f).apply {
                    if (i > 0) marginStart = dp(10)
                })
            }
            content.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun fileItems() {
        val dir = current ?: return
        val entries = try {
            dir.listFiles().filter { accepts(it) }.sortedWith(
                compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase(Locale.forLanguageTag("tr")) ?: "" }
            )
        } catch (_: Exception) {
            card("Erişim hatası", "Bu klasör okunamadı. Lütfen farklı bir klasör seçin.")
            return
        }
        if (entries.isEmpty()) {
            card("Dosya bulunamadı", "Bu klasörde seçilen kategoriye uygun dosya bulunamadı.")
            return
        }
        if (grid) {
            val gridContainer = GridLayout(this).apply { columnCount = 2 }
            val width = (resources.displayMetrics.widthPixels - dp(54)) / 2
            entries.forEach { file ->
                val tile = fileTile(file)
                gridContainer.addView(tile, GridLayout.LayoutParams().apply {
                    this.width = width
                    height = dp(118)
                    setMargins(dp(2), dp(3), dp(5), dp(5))
                })
            }
            content.addView(gridContainer)
        } else {
            entries.forEach { file ->
                content.addView(fileTile(file), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            }
        }
    }

    private fun accepts(file: DocumentFile): Boolean {
        val filter = category ?: return true
        if (file.isDirectory) return false
        val mime = file.type ?: ""
        val name = (file.name ?: "").lowercase(Locale.ROOT)
        return when (filter) {
            "Görseller" -> mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".png")
            "Videolar" -> mime.startsWith("video/")
            "Müzikler" -> mime.startsWith("audio/")
            "Belgeler" -> mime == "application/pdf" || mime.startsWith("text/") ||
                listOf(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt").any { name.endsWith(it) }
            "APK'lar" -> name.endsWith(".apk") || mime == "application/vnd.android.package-archive"
            "Büyük Dosyalar" -> file.length() >= 100L * 1024 * 1024
            else -> true
        }
    }

    private fun fileTile(file: DocumentFile): LinearLayout {
        val panel = panel()
        val name = file.name ?: "Adsız"
        panel.addView(textView((if (file.isDirectory) "📁 " else "📄 ") + name, 14f, foreground, true))
        panel.addView(textView(if (file.isDirectory) "Klasör" else size(file.length()), 12f, secondary, false))
        panel.setOnClickListener {
            if (file.isDirectory) {
                current?.let { history.add(it) }
                current = file
                category = null
                render()
            } else open(file)
        }
        return panel
    }

    private fun pickFolder() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, requestFolder)
        } catch (_: Exception) {
            Toast.makeText(this, "Klasör seçici açılamadı", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Legacy result callback for Android 8 support")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestFolder || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            grantedRoot = DocumentFile.fromTreeUri(this, uri)
            current = grantedRoot
            category = null
            history.clear()
            getPreferences(MODE_PRIVATE).edit().putString("folder_uri", uri.toString()).apply()
            render()
        } catch (_: Exception) {
            Toast.makeText(this, "Klasöre erişim izni alınamadı", Toast.LENGTH_LONG).show()
        }
    }

    private fun open(file: DocumentFile) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(file.uri, file.type ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "Dosyayı açacak uygulama bulunamadı", Toast.LENGTH_LONG).show()
        }
    }

    private fun goBack() {
        if (category != null) category = null
        else if (history.isNotEmpty()) current = history.removeAt(history.lastIndex)
        render()
    }

    @Deprecated("Back navigation across Android 8+")
    override fun onBackPressed() {
        if (category != null || history.isNotEmpty()) goBack() else super.onBackPressed()
    }

    private fun size(bytes: Long): String {
        if (bytes < 1024) return bytes.toString() + " B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var number = bytes.toDouble()
        var index = -1
        do { number /= 1024; index++ } while (number >= 1024 && index < units.lastIndex)
        return String.format(Locale.forLanguageTag("tr-TR"), "%.1f %s", number, units[index])
    }

    private fun action(value: String, callback: () -> Unit): TextView =
        textView(value, 12f, accent, true).apply {
            gravity = Gravity.CENTER
            background = shape(surface, dp(14))
            setOnClickListener { callback() }
        }

    private fun rowTitle(value: String, back: String, callback: () -> Unit) {
        title(value, 19f, foreground)
        content.addView(action(back, callback), LinearLayout.LayoutParams(-1, dp(45)).apply { bottomMargin = dp(10) })
    }

    private fun card(name: String, detail: String) {
        val box = panel()
        box.addView(textView(name, 17f, foreground, true))
        box.addView(textView(detail, 13f, secondary, false))
        content.addView(box)
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(13), dp(16), dp(13))
        background = shape(surface, dp(17))
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun title(s: String, size: Float, color: Int) {
        content.addView(textView(s, size, color, true))
    }
    private fun text(s: String, size: Float, color: Int) {
        content.addView(textView(s, size, color, false))
    }
    private fun textView(s: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private fun space(n: Int) { content.addView(View(this), LinearLayout.LayoutParams(1, dp(n))) }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
}
