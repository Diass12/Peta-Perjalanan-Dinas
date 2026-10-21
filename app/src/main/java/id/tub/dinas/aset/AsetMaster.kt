package id.tub.dinas.aset

import android.content.Context
import id.tub.dinas.domain.AsetModel

/** Data Master: daftar pilihan untuk isian formulir. Gabungan nilai tersimpan + nilai yang sudah dipakai aset. */
enum class MasterField(val key: String, val label: String, val get: (AsetModel) -> String, val set: (AsetModel, String) -> AsetModel, val default: List<String> = emptyList()) {
    KLASIF("klasif", "Klasifikasi", { it.classif }, { a, v -> a.copy(classif = v) }),
    DEPT("dept", "Departemen", { it.dept }, { a, v -> a.copy(dept = v) }),
    LOKASI("lokasi", "Lokasi", { it.location }, { a, v -> a.copy(location = v) }),
    KONDISI("kondisi", "Kondisi", { it.cond }, { a, v -> a.copy(cond = v) }, listOf("Baik", "Cukup", "Rusak")),
    PT("pt", "PT pemilik", { it.owner }, { a, v -> a.copy(owner = v) }),
}

class MasterStore(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("aset_master", Context.MODE_PRIVATE)

    fun saved(f: MasterField): List<String> =
        (p.getString(f.key, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()) + f.default

    fun options(f: MasterField, register: List<AsetModel>): List<String> =
        (saved(f) + register.map { f.get(it) }).map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    /** Hapus semua pilihan tersimpan (kembali ke bawaan). */
    fun reset() { p.edit().clear().apply() }

    fun add(f: MasterField, v: String) {
        val t = v.trim(); if (t.isEmpty()) return
        val cur = (p.getString(f.key, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList())
        if (cur.any { it.equals(t, true) }) return
        p.edit().putString(f.key, (cur + t).joinToString("\n")).apply()
    }

    fun remove(f: MasterField, v: String) {
        val cur = (p.getString(f.key, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList())
        p.edit().putString(f.key, cur.filterNot { it.equals(v, true) }.joinToString("\n")).apply()
    }

    fun rename(f: MasterField, old: String, new: String) {
        val cur = (p.getString(f.key, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList())
        p.edit().putString(f.key, (cur.filterNot { it.equals(old, true) } + new.trim()).distinctBy { it.lowercase() }.joinToString("\n")).apply()
    }
}
