package com.dhalam.ap.data

import android.content.Context

/** One lazily opened store per process, shared by activities and WorkManager.
 * Call from an IO thread; the store lives until the app process exits.
 */
object SecurityStore {
    @Volatile private var repository: SecurityRepository? = null

    fun repository(context: Context): SecurityRepository =
        repository ?: synchronized(this) {
            repository ?: SecurityRepository(
                MyObjectBox.builder().androidContext(context.applicationContext).build()
            ).also { repository = it }
        }
}
