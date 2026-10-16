package com.selfwallet.app

import android.app.Application
import android.content.Context
import com.selfwallet.app.backup.BackupManager
import com.selfwallet.app.backup.BackupScheduler
import com.selfwallet.app.data.Repository
import com.selfwallet.app.data.db.AppDatabase
import com.selfwallet.app.data.prefs.SettingsStore
import com.selfwallet.app.sms.Notifications
import com.selfwallet.app.widget.SpendWidget
import com.selfwallet.app.work.DailyWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appContext = context.applicationContext
    val settings = SettingsStore(appContext)
    val database = AppDatabase.create(appContext)
    val repository = Repository(
        database,
        settings,
        onDataChanged = {
            scope.launch {
                val s = settings.current()
                if (s.autoBackup && s.backupUri != null) BackupScheduler.requestSoon(appContext)
                SpendWidget.refresh(appContext)
            }
        },
        onBudgetAlert = { title, body -> Notifications.showBudget(appContext, title, body) },
        onSmartAlert = { title, body -> Notifications.showInsight(appContext, title, body) },
    )
    val backup = BackupManager(appContext, repository)
}

class SelfWalletApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
        container.scope.launch {
            container.repository.ensureSeeded()
            val s = container.settings.current()
            if (s.autoBackup && s.backupUri != null) BackupScheduler.ensureDaily(this@SelfWalletApp)
            DailyWorker.schedule(this@SelfWalletApp)
        }
    }
}
