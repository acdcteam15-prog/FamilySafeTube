package com.example.familysafetube

import android.app.Application
import android.content.Context
import com.example.familysafetube.update.UpdateCheckWorker
import com.example.familysafetube.update.UpdateHttp
import com.example.familysafetube.update.UpdateRepository

/** Process-wide singletons, created once in [FamilySafeTubeApp.onCreate]. */
object AppGraph {
    lateinit var settings: PrefsStore
        private set
    lateinit var usage: UsageRepository
        private set
    /** Checks GitHub Releases for a newer build of this app and installs it (see the update package). */
    lateinit var updates: UpdateRepository
        private set

    private var started = false

    @Synchronized
    fun init(context: Context) {
        if (started) return
        settings = SettingsStore.create(context)
        usage = UsageRepository(settings)
        updates = UpdateRepository(
            context.applicationContext, UpdateHttp.client, settings,
            BuildConfig.UPDATE_REPO, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME
        )
        started = true
    }

    /** For tests / previews: install fakes without touching DataStore. */
    fun installForTest(store: PrefsStore, repo: UsageRepository) {
        settings = store
        usage = repo
        started = true
    }
}

class FamilySafeTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        UpdateCheckWorker.schedule(this)      // looks for a new build twice a day (keeps the existing schedule)
    }
}
