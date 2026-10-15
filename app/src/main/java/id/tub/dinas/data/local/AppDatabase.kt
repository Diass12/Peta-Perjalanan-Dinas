package id.tub.dinas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [DinasEntity::class, TitikEntity::class, TemuanEntity::class, FotoEntity::class, LampiranEntity::class, PoinEntity::class, MediaEntity::class],
    version = 2,
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

        /** Database dienkripsi SQLCipher. Tidak ada fallbackToDestructiveMigration: data pengguna tidak boleh hilang diam-diam. */
        fun open(ctx: Context, key: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "dinas.db")
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
