package id.tub.dinas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [DinasEntity::class, TitikEntity::class, TemuanEntity::class, FotoEntity::class, LampiranEntity::class, PoinEntity::class, MediaEntity::class, AsetEntity::class, AsetFotoEntity::class, AsetMediaEntity::class],
    version = 4,
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

        /** Database dienkripsi SQLCipher. Tidak ada fallbackToDestructiveMigration: data pengguna tidak boleh hilang diam-diam. */
        fun open(ctx: Context, key: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "dinas.db")
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
