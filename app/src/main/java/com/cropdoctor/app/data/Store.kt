package com.cropdoctor.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

private data class StoreFile(
    val fields: List<FieldPlot> = emptyList(),
    val scans: List<ScanRecord> = emptyList(),
)

/**
 * Local-only storage: one small JSON file plus the scan photos. Nothing leaves the phone.
 * Hundreds of scans is the realistic upper bound, so a JSON file is plenty and keeps the app dependency-light.
 */
class Store(context: Context) {
    private val gson: Gson = GsonBuilder().create()
    private val dir = File(context.filesDir, "data").apply { mkdirs() }
    private val photos = File(context.filesDir, "scans").apply { mkdirs() }
    private val file = File(dir, "store.json")

    private val _fields = MutableStateFlow<List<FieldPlot>>(emptyList())
    private val _scans = MutableStateFlow<List<ScanRecord>>(emptyList())
    val fields: StateFlow<List<FieldPlot>> = _fields.asStateFlow()
    val scans: StateFlow<List<ScanRecord>> = _scans.asStateFlow()

    init {
        runCatching {
            if (file.exists()) {
                val s = gson.fromJson(file.readText(), StoreFile::class.java)
                _fields.value = s.fields
                _scans.value = s.scans.sortedByDescending { it.timestamp }
            }
        }
    }

    @Synchronized
    private fun persist() {
        val tmp = File(dir, "store.json.tmp")
        tmp.writeText(gson.toJson(StoreFile(_fields.value, _scans.value)))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun newId(): String = UUID.randomUUID().toString().take(12)

    // ---- fields
    fun addField(name: String, crop: String, acres: Double): FieldPlot {
        val f = FieldPlot(newId(), name.trim(), crop, acres)
        _fields.value = _fields.value + f
        persist()
        return f
    }

    fun deleteField(id: String) {
        _fields.value = _fields.value.filterNot { it.id == id }
        // scans stay (history is valuable) but become unassigned
        _scans.value = _scans.value.map { if (it.fieldId == id) it.copy(fieldId = null) else it }
        persist()
    }

    fun field(id: String?): FieldPlot? = _fields.value.firstOrNull { it.id == id }

    // ---- scans
    fun savePhoto(id: String, bitmap: Bitmap): String {
        val name = "$id.jpg"
        val maxSide = 1280
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        File(photos, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (out !== bitmap) out.recycle()
        return name
    }

    fun addScan(r: ScanRecord) {
        _scans.value = (listOf(r) + _scans.value).sortedByDescending { it.timestamp }
        persist()
    }

    fun updateScan(id: String, f: (ScanRecord) -> ScanRecord) {
        _scans.value = _scans.value.map { if (it.id == id) f(it) else it }
        persist()
    }

    fun scan(id: String?): ScanRecord? = _scans.value.firstOrNull { it.id == id }

    fun deleteScan(id: String) {
        scan(id)?.let { File(photos, it.imageFile).delete() }
        _scans.value = _scans.value.filterNot { it.id == id }
        persist()
    }

    fun clearScans() {
        photos.listFiles()?.forEach { it.delete() }
        _scans.value = emptyList()
        persist()
    }

    fun photoFile(r: ScanRecord) = File(photos, r.imageFile)

    fun loadPhoto(r: ScanRecord, maxSide: Int = 900): Bitmap? = runCatching {
        val f = photoFile(r)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    // ---- field trend
    /** Compares the last two diagnosed scans of a field. Lower severity = better. */
    fun trend(fieldId: String): Trend {
        val s = _scans.value.filter { it.fieldId == fieldId && it.severity >= 0 }.sortedByDescending { it.timestamp }
        if (s.size < 2) return Trend.NEW
        return when {
            s[0].severity < s[1].severity -> Trend.BETTER
            s[0].severity > s[1].severity -> Trend.WORSE
            else -> Trend.SAME
        }
    }
}
