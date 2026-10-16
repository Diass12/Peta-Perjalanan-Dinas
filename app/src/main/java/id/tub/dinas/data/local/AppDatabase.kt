package id.tub.dinas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [DinasEntity::class, TitikEntity::class, TemuanEntity::class, FotoEntity::class, LampiranEntity::class, PoinEntity::class, MediaEntity::class, AsetEntity::class, AsetFotoEntity::class, AsetMediaEntity::class, UangMukaEntity::class, UmBonEntity::class, UmFotoEntity::class, JadwalEntity::class, DokumenEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        /** v1 -> v2: tabel media (video & rekaman suara). Data lama tidak disentuh. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `media` (`id` TEXT NOT NULL, `dinasId` TEXT NOT NULL, `ownerType` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `kind` TEXT NOT NULL, `path` TEXT NOT NULL, `name` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `urutan` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`dinasId`) REFERENCES `dinas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_dinasId` ON `media` (`dinasId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_ownerId` ON `media` (`ownerId`)")
            }
        }

        /** SQL v3: register aset. Dicocokkan dengan skema yang dihasilkan Room oleh SchemaMigrationTest. */
        const val SQL_ASET = "CREATE TABLE IF NOT EXISTS `aset` (`id` TEXT NOT NULL, `ord` INTEGER NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `classif` TEXT NOT NULL, `owner` TEXT NOT NULL, `tenure` TEXT NOT NULL, `dept` TEXT NOT NULL, `location` TEXT NOT NULL, `pengguna` TEXT NOT NULL, `cond` TEXT NOT NULL, `acq` TEXT NOT NULL, `checkDate` TEXT NOT NULL, `remarks` TEXT NOT NULL, `nbv` REAL, `harga` REAL, `masa` INTEGER, `tglTercatat` TEXT NOT NULL, `nbvKet` TEXT NOT NULL, `catatan` TEXT NOT NULL, `sumber` TEXT NOT NULL, `srcStatus` TEXT NOT NULL, `barcode` TEXT NOT NULL, `barcodeSrc` TEXT NOT NULL, `pcodes` TEXT NOT NULL, `state` TEXT NOT NULL, `auditedAt` INTEGER, `log` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `temuanId` TEXT, PRIMARY KEY(`id`))"
        const val SQL_ASET_IDX = "CREATE INDEX IF NOT EXISTS `index_aset_code` ON `aset` (`code`)"
        const val SQL_ASET_FOTO = "CREATE TABLE IF NOT EXISTS `aset_foto` (`id` TEXT NOT NULL, `asetId` TEXT NOT NULL, `path` TEXT NOT NULL, `urutan` INTEGER NOT NULL, `takenAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`asetId`) REFERENCES `aset`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        const val SQL_ASET_FOTO_IDX = "CREATE INDEX IF NOT EXISTS `index_aset_foto_asetId` ON `aset_foto` (`asetId`)"

        /** v2 -> v3: register aset + foto aset. Data lama tidak disentuh. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(SQL_ASET); db.execSQL(SQL_ASET_IDX); db.execSQL(SQL_ASET_FOTO); db.execSQL(SQL_ASET_FOTO_IDX)
            }
        }

        /** SQL v4: video & dokumen per aset. */
        const val SQL_ASET_MEDIA = "CREATE TABLE IF NOT EXISTS `aset_media` (`id` TEXT NOT NULL, `asetId` TEXT NOT NULL, `kind` TEXT NOT NULL, `path` TEXT NOT NULL, `name` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `urutan` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`asetId`) REFERENCES `aset`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        const val SQL_ASET_MEDIA_IDX = "CREATE INDEX IF NOT EXISTS `index_aset_media_asetId` ON `aset_media` (`asetId`)"

        /** v3 -> v4: tabel aset_media. Data lama tidak disentuh. */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL(SQL_ASET_MEDIA); db.execSQL(SQL_ASET_MEDIA_IDX) }
        }

        /** SQL v5: uang muka, bon, foto bon. */
        const val SQL_UM = "CREATE TABLE IF NOT EXISTS `uang_muka` (`id` TEXT NOT NULL, `nomor` TEXT NOT NULL, `judul` TEXT NOT NULL, `dinasId` TEXT, `pemohon` TEXT NOT NULL, `tglPengajuan` TEXT NOT NULL, `jumlahDiajukan` REAL NOT NULL, `tglCair` TEXT NOT NULL, `jumlahCair` REAL NOT NULL, `catatan` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        const val SQL_UM_BON = "CREATE TABLE IF NOT EXISTS `um_bon` (`id` TEXT NOT NULL, `umId` TEXT NOT NULL, `tanggal` TEXT NOT NULL, `kategori` TEXT NOT NULL, `uraian` TEXT NOT NULL, `jumlah` REAL NOT NULL, `urutan` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`umId`) REFERENCES `uang_muka`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        const val SQL_UM_BON_IDX = "CREATE INDEX IF NOT EXISTS `index_um_bon_umId` ON `um_bon` (`umId`)"
        const val SQL_UM_FOTO = "CREATE TABLE IF NOT EXISTS `um_foto` (`id` TEXT NOT NULL, `bonId` TEXT NOT NULL, `path` TEXT NOT NULL, `urutan` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bonId`) REFERENCES `um_bon`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        const val SQL_UM_FOTO_IDX = "CREATE INDEX IF NOT EXISTS `index_um_foto_bonId` ON `um_foto` (`bonId`)"

        const val SQL_JADWAL = "CREATE TABLE IF NOT EXISTS `jadwal` (`id` TEXT NOT NULL, `judul` TEXT NOT NULL, `jenis` TEXT NOT NULL, `tanggal` TEXT NOT NULL, `jamMulai` TEXT NOT NULL, `jamSelesai` TEXT NOT NULL, `lokasi` TEXT NOT NULL, `peserta` TEXT NOT NULL, `catatan` TEXT NOT NULL, `selesai` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        /** v4 -> v5: tabel uang muka dan jadwal. Data lama tidak disentuh. */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(SQL_UM); db.execSQL(SQL_UM_BON); db.execSQL(SQL_UM_BON_IDX); db.execSQL(SQL_UM_FOTO); db.execSQL(SQL_UM_FOTO_IDX); db.execSQL(SQL_JADWAL)
            }
        }

        /** SQL v6: dokumen & tiket perjalanan. */
        const val SQL_DOKUMEN = "CREATE TABLE IF NOT EXISTS `dokumen` (`id` TEXT NOT NULL, `jenis` TEXT NOT NULL, `judul` TEXT NOT NULL, `nomor` TEXT NOT NULL, `berlakuSampai` TEXT NOT NULL, `catatan` TEXT NOT NULL, `path` TEXT NOT NULL, `tipe` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        /** v5 -> v6: tabel dokumen. Data lama tidak disentuh. */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL(SQL_DOKUMEN) }
        }

        /** Database dienkripsi SQLCipher. Tidak ada fallbackToDestructiveMigration: data pengguna tidak boleh hilang diam-diam. */
        fun open(ctx: Context, key: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "dinas.db")
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
        }
    }
}
