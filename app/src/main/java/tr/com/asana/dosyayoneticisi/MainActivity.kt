package tr.com.asana.dosyayoneticisi

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

class MainActivity : Activity() {
    private val requestFolder = 501
    private val accent = Color.rgb(0, 112, 121)
    private val dark = Color.rgb(27, 40, 49)
    private lateinit var area: LinearLayout
    private var selected: DocumentFile? = null
    private val parents = mutableListOf<DocumentFile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 249, 250))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            setBackgroundColor(dark)
        }
        header.addView(label("ASANA", 14f, Color.CYAN, true))
        header.addView(label("Dosya Yöneticisi", 27f, Color.WHITE, true))
        header.addView(label("Güvenli • Ücretsiz • Reklamsız", 12f, Color.LTGRAY, false))
        layout.addView(header)
        val scroll = ScrollView(this)
        area = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(22))
        }
        scroll.addView(area)
        layout.addView(scroll)
        setContentView(layout)

        val saved = getPreferences(MODE_PRIVATE).getString("folder_uri", null)
        if (saved != null) {
            try {
                val uri = Uri.parse(saved)
                if (contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
                    selected = DocumentFile.fromTreeUri(this, uri)
                }
            } catch (_: Exception) { }
        }
        render()
    }

    private fun render() {
        area.removeAllViews()
        card("DEPOLAMA", storage())
        button("Klasör seç / değiştir") { pickFolder() }
        val folder = selected
        if (folder == null) {
            card("HOŞ GELDİNİZ", "Dosyalarınıza göz atmak için Android dosya seçiciden bir klasör seçin. Uygulama verilerinizi internete göndermez.")
            return
        }
        card("AÇIK KLASÖR", folder.name ?: "Seçilen klasör")
        if (parents.isNotEmpty()) button("← Üst klasör") { goBack() }
        area.addView(label("DOSYALAR VE KLASÖRLER", 14f, accent, true))
        val items = try {
            folder.listFiles().sortedWith(compareBy<DocumentFile> { !it.isDirectory }
                .thenBy { it.name?.lowercase(Locale.forLanguageTag("tr")) ?: "" })
        } catch (_: Exception) {
            card("ERİŞİM HATASI", "Bu klasör okunamadı. Lütfen başka bir klasör seçin.")
            return
        }
        if (items.isEmpty()) card("BOŞ KLASÖR", "Bu klasörde görüntülenecek dosya yok.")
        items.forEach { item ->
            val caption = if (item.isDirectory) "Klasör" else formatSize(item.length())
            button("${if (item.isDirectory) "📁" else "📄"}  ${item.name ?: "Adsız"}\n$caption") {
                if (item.isDirectory) {
                    parents.add(folder)
                    selected = item
                    render()
                } else open(item)
            }
        }
        area.addView(label("v0.1 • Bu sürüm dosyaları silmez veya değiştirmez.", 12f, Color.GRAY, false))
    }

    private fun pickFolder() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, requestFolder)
        } catch (_: Exception) {
            Toast.makeText(this, "Klasör seçici açılamadı", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Using legacy result callback with no additional dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestFolder || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selected = DocumentFile.fromTreeUri(this, uri)
            parents.clear()
            getPreferences(MODE_PRIVATE).edit().putString("folder_uri", uri.toString()).apply()
            render()
        } catch (_: Exception) {
            Toast.makeText(this, "Klasöre erişim izni alınamadı", Toast.LENGTH_LONG).show()
        }
    }

    private fun open(file: DocumentFile) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(file.uri, file.type ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Dosyayı açabilecek uygulama bulunamadı", Toast.LENGTH_LONG).show()
        }
    }

    private fun goBack() {
        if (parents.isNotEmpty()) {
            selected = parents.removeAt(parents.lastIndex)
            render()
        }
    }

    @Deprecated("Back navigation supported across Android 8+")
    override fun onBackPressed() {
        if (parents.isNotEmpty()) goBack() else super.onBackPressed()
    }

    private fun storage(): String = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val available = stat.availableBytes
        "Kullanılan: ${formatSize(total - available)} / ${formatSize(total)}\nBoş: ${formatSize(available)} (cihaz depolaması tahmini)"
    } catch (_: Exception) { "Depolama bilgisi alınamadı" }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = -1
        do { value /= 1024; index++ } while (value >= 1024 && index < units.lastIndex)
        return String.format(Locale.forLanguageTag("tr-TR"), "%.1f %s", value, units[index])
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun card(title: String, detail: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        container.addView(label(title, 12f, accent, true))
        container.addView(label(detail, 15f, dark, false))
        area.addView(container, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun button(value: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = value
            isAllCaps = false
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(dark)
            setOnClickListener { action() }
        }
        area.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
}
