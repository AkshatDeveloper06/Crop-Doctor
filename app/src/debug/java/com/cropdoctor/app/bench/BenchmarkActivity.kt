package com.cropdoctor.app.bench

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import com.cropdoctor.app.CropDoctorApp
import org.json.JSONObject
import java.io.File
import kotlin.math.max

/**
 * Debug-only benchmark: runs the app's real model and real image pipeline over a folder of labelled test images and
 * writes one CSV row per image and mode. Metrics (accuracy, F1, confusion matrix) are computed on the PC by
 * benchmark/benchmark_report.py, so nothing heavy is needed on the phone.
 *
 *   files/bench_set/<class folder>/<image files>   class folder = an app class id ("Tomato___late_blight")
 *                                                  or one of the model's own labels
 *   files/bench_results.csv                         written by this screen
 *
 * Start it with:  adb shell am start -n com.cropdoctor.app/.bench.BenchmarkActivity
 * Modes: "gallery" = whole image, smoothly resized (how a picked photo is read);
 *        "camera"  = middle 72% sampled at live-frame size (how the live scan reads).
 */
class BenchmarkActivity : Activity() {
    private lateinit var view: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = TextView(this).apply { textSize = 18f; setPadding(40, 120, 40, 40); text = "Benchmark starting…" }
        setContentView(view)
        Thread { run() }.start()
    }

    private fun show(s: String) = runOnUiThread { view.text = s }

    private fun run() {
        val app = applicationContext as CropDoctorApp
        val clf = app.classifier
        val ready = clf.ready
        if (ready == null) { show("Model not loaded."); return }
        val ids = ready.labels

        val base = filesDir   // private folder: files are loaded with `adb shell run-as` (see benchmark/README.md)
        val root = File(base, "bench_set")
        val out = File(base, "bench_results.csv")
        val done = File(base, "bench_done.txt").also { it.delete() }

        // model label -> app class id, so folders may use either naming
        val rawToId = runCatching {
            val o = JSONObject(assets.open("label_map.json").bufferedReader().readText())
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())

        val classDirs = root.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()
        if (classDirs.isEmpty()) {
            show(
                "No class folders found in:\n${root.absolutePath}\n\nexists=${root.exists()} isDirectory=${root.isDirectory} " +
                    "canRead=${root.canRead()}\nentries=${root.list()?.toList()}\nfiles dir entries=${base.list()?.toList()}",
            )
            return
        }

        val files = ArrayList<Pair<String, File>>()   // (true class id, file)
        val skipped = ArrayList<String>()
        for (d in classDirs) {
            val id = when {
                d.name in ids -> d.name
                rawToId.containsKey(d.name) -> rawToId.getValue(d.name)
                else -> { skipped += d.name; continue }
            }
            d.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                ?.sortedBy { it.name }?.forEach { files += id to it }
        }
        Log.i(TAG, "images=${files.size} classes=${classDirs.size - skipped.size} skipped=$skipped")

        val t0 = System.currentTimeMillis()
        out.bufferedWriter().use { w ->
            w.write("file,true,mode,pred,conf,certainty,top1,p1,top2,p2,top3,p3,ms\n")
            files.forEachIndexed { i, (trueId, f) ->
                val bmp = decode(f)
                if (bmp != null) {
                    for (mode in arrayOf("gallery", "camera")) {
                        val t = System.nanoTime()
                        val probs = clf.classify(bmp, camera = mode == "camera")
                        val ms = (System.nanoTime() - t) / 1e6
                        val v = clf.verdict(probs)
                        val order = probs.indices.sortedByDescending { probs[it] }.take(3)
                        w.write(
                            "${q("${f.parentFile!!.name}/${f.name}")},$trueId,$mode,${v.classId ?: "NOT_A_LEAF"}," +
                                "${"%.5f".format(v.confidence)},${v.certainty}," +
                                order.joinToString(",") { "${ids[it]},${"%.5f".format(probs[it])}" } + ",${"%.1f".format(ms)}\n",
                        )
                    }
                    bmp.recycle()
                }
                if (i % 25 == 0 || i == files.size - 1) {
                    val secs = (System.currentTimeMillis() - t0) / 1000
                    show("Benchmark running\n${i + 1} / ${files.size} images\n${secs}s elapsed" + if (skipped.isNotEmpty()) "\nskipped folders: ${skipped.size}" else "")
                }
            }
        }
        done.writeText("images=${files.size}\nskipped=${skipped.joinToString(";")}\nseconds=${(System.currentTimeMillis() - t0) / 1000}\n")
        show("Done.\n${files.size} images\nresults: ${out.absolutePath}")
        Log.i(TAG, "done images=${files.size}")
    }

    /** Same size cap as a picked gallery photo (1600 px on the long side). */
    private fun decode(f: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private companion object { const val TAG = "CDBench" }
}
