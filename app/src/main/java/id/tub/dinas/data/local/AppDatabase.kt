package id.tub.dinas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [DinasEntity::class, TitikEntity::class, TemuanEntity::class, FotoEntity::class, LampiranEntity::class, PoinEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        /** Database dienkripsi SQLCipher. Tidak ada fallbackToDestructiveMigration: data pengguna tidak boleh hilang diam-diam. */
        fun open(ctx: Context, key: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "dinas.db")
                .openHelperFactory(SupportOpenHelperFactory(key))
                .build()
        }
    }
}
