package com.kumo.bubu

import android.app.Application

class BuBuApplication : Application() {
    val container by lazy { com.kumo.bubu.core.database.AppContainer(this) }
}
