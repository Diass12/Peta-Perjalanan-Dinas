package id.tub.dinas

import android.app.Application
import id.tub.dinas.data.Repository
import id.tub.dinas.data.Settings
import id.tub.dinas.data.local.AppDatabase
import id.tub.dinas.location.GpsTracker
import id.tub.dinas.security.KeyVault
import id.tub.dinas.security.MediaVault
import org.osmdroid.config.Configuration
import java.io.File

class Container(app: Application) {
    val settings = Settings(app)
    private val keys = KeyVault(app)
    val db: AppDatabase by lazy { AppDatabase.open(app, keys.dbKey()) }
    val vault: MediaVault by lazy { MediaVault(File(app.filesDir, "media"), keys.mediaKey()) }
    val repo: Repository by lazy { Repository(app, db, vault) }
    val tracker = GpsTracker(app)
}

class DinasApp : Application() {
    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        container = Container(this)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osm")
            osmdroidTileCache = File(filesDir, "osm/tiles")
        }
    }
}
