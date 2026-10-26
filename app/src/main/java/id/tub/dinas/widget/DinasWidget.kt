package id.tub.dinas.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import id.tub.dinas.DinasApp
import id.tub.dinas.MainActivity
import id.tub.dinas.R
import id.tub.dinas.domain.UmStatus
import id.tub.dinas.um.rupiah
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Widget layar utama: dinas aktif, sisa anggaran, dan tombol cepat Kamera / Kunci GPS. */
class DinasWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { gambar(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        const val AKSI = "id.tub.dinas.WIDGET_AKSI"
        const val EXTRA = "aksi"

        /** Dipanggil dari aplikasi (mis. saat ditutup) agar widget menampilkan data terbaru. */
        fun perbarui(context: Context) {
            val app = context.applicationContext
            val m = AppWidgetManager.getInstance(app)
            val ids = m.getAppWidgetIds(ComponentName(app, DinasWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { runCatching { gambar(app, m, ids) } }
        }

        private fun aksi(c: Context, kode: Int, nama: String?): PendingIntent {
            val i = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (nama != null) i.action = AKSI + "." + nama
            if (nama != null) i.putExtra(EXTRA, nama)
            return PendingIntent.getActivity(c, kode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private suspend fun gambar(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val c = (context.applicationContext as DinasApp).container
            val s = c.settings
            val tampilNominal = !s.lockEnabled || s.widgetNominal
            val rv = RemoteViews(context.packageName, R.layout.widget_dinas)
            rv.setOnClickPendingIntent(R.id.w_root, aksi(context, 0, null))
            rv.setOnClickPendingIntent(R.id.w_gps, aksi(context, 1, "gps"))
            rv.setOnClickPendingIntent(R.id.w_kamera, aksi(context, 2, "kamera"))
            try {
                val dinas = c.repo.observeDinas().first()
                val aktif = dinas.firstOrNull { it.id == s.lastDinasId } ?: dinas.firstOrNull()
                rv.setTextViewText(R.id.w_nama, aktif?.name ?: "Belum ada dinas")
                val berjalan = c.repo.observeUmBundles().first().filter { it.um.status != UmStatus.SELESAI }
                val dana = berjalan.sumOf { it.dana }; val bon = berjalan.sumOf { it.totalBon }
                if (berjalan.isEmpty()) {
                    rv.setTextViewText(R.id.w_sisa, "-"); rv.setProgressBar(R.id.w_bar, 100, 0, false)
                    rv.setTextViewText(R.id.w_rinci, "Belum ada uang muka berjalan")
                } else if (!tampilNominal) {
                    rv.setTextViewText(R.id.w_sisa, "Rp ••••••"); rv.setProgressBar(R.id.w_bar, 100, 0, false)
                    rv.setTextViewText(R.id.w_rinci, "Nominal disembunyikan (kunci aplikasi aktif)")
                } else {
                    rv.setTextViewText(R.id.w_sisa, rupiah(dana - bon))
                    rv.setProgressBar(R.id.w_bar, 100, if (dana <= 0) 0 else (bon / dana * 100).toInt().coerceIn(0, 100), false)
                    rv.setTextViewText(R.id.w_rinci, "Keluar ${rupiah(bon)} dari ${rupiah(dana)}")
                }
            } catch (e: Exception) {
                rv.setTextViewText(R.id.w_nama, "Ketuk untuk membuka aplikasi")
                rv.setTextViewText(R.id.w_sisa, "-"); rv.setTextViewText(R.id.w_rinci, "")
            }
            for (id in ids) manager.updateAppWidget(id, rv)
        }
    }
}
