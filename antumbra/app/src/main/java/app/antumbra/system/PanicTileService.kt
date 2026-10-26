package app.antumbra.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.antumbra.AntumbraApp

/** Quick Settings tile that wipes every session and terminates the app. */
class PanicTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        (application as AntumbraApp).panic()
    }
}
