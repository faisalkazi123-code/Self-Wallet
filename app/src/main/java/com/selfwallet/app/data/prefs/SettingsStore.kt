package com.selfwallet.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object ThemeMode {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
}

data class Settings(
    val onboardingDone: Boolean = false,
    val baseCurrency: String = "USD",
    val themeMode: String = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val smsAutoImport: Boolean = true,
    val smsNotify: Boolean = true,
    val defaultAllocationId: Long = 0,
    val backupUri: String? = null,
    val backupLabel: String? = null,
    val autoBackup: Boolean = true,
    val lastBackupAt: Long = 0,
    val hideAmounts: Boolean = false,
    /** AppStyle.TEAL or AppStyle.PAPER. */
    val appStyle: String = "TEAL",
    val appLock: Boolean = false,
    /** Shown on PDF expense reports. */
    val ownerName: String = "",
    /** Warn about unusually large payments. */
    val smartAlerts: Boolean = true,
    /** Remind about bills and upcoming recurring payments. */
    val reminders: Boolean = true,
    /** "2026-09": last month a monthly summary was sent for. */
    val lastSummaryMonth: String = "",
    /** Keys of recurring payments already reminded about ("merchant|date"). */
    val remindedRecurring: Set<String> = emptySet(),
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val baseCurrency = stringPreferencesKey("base_currency")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val smsAutoImport = booleanPreferencesKey("sms_auto_import")
        val smsNotify = booleanPreferencesKey("sms_notify")
        val defaultAllocationId = longPreferencesKey("default_allocation")
        val backupUri = stringPreferencesKey("backup_uri")
        val backupLabel = stringPreferencesKey("backup_label")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val hideAmounts = booleanPreferencesKey("hide_amounts")
        val appStyle = stringPreferencesKey("app_style")
        val appLock = booleanPreferencesKey("app_lock")
        val ownerName = stringPreferencesKey("owner_name")
        val smartAlerts = booleanPreferencesKey("smart_alerts")
        val reminders = booleanPreferencesKey("reminders")
        val lastSummaryMonth = stringPreferencesKey("last_summary_month")
        val remindedRecurring = stringSetPreferencesKey("reminded_recurring")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        onboardingDone = this[Keys.onboardingDone] ?: false,
        baseCurrency = this[Keys.baseCurrency] ?: "USD",
        themeMode = this[Keys.themeMode] ?: ThemeMode.SYSTEM,
        dynamicColor = this[Keys.dynamicColor] ?: false,
        smsAutoImport = this[Keys.smsAutoImport] ?: true,
        smsNotify = this[Keys.smsNotify] ?: true,
        defaultAllocationId = this[Keys.defaultAllocationId] ?: 0L,
        backupUri = this[Keys.backupUri],
        backupLabel = this[Keys.backupLabel],
        autoBackup = this[Keys.autoBackup] ?: true,
        lastBackupAt = this[Keys.lastBackupAt] ?: 0L,
        hideAmounts = this[Keys.hideAmounts] ?: false,
        appStyle = this[Keys.appStyle] ?: "TEAL",
        appLock = this[Keys.appLock] ?: false,
        ownerName = this[Keys.ownerName] ?: "",
        smartAlerts = this[Keys.smartAlerts] ?: true,
        reminders = this[Keys.reminders] ?: true,
        lastSummaryMonth = this[Keys.lastSummaryMonth] ?: "",
        remindedRecurring = this[Keys.remindedRecurring] ?: emptySet(),
    )

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.onboardingDone] = done }
    suspend fun setBaseCurrency(code: String) = context.dataStore.edit { it[Keys.baseCurrency] = code }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.themeMode] = mode }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = on }
    suspend fun setSmsAutoImport(on: Boolean) = context.dataStore.edit { it[Keys.smsAutoImport] = on }
    suspend fun setSmsNotify(on: Boolean) = context.dataStore.edit { it[Keys.smsNotify] = on }
    suspend fun setDefaultAllocation(id: Long) = context.dataStore.edit { it[Keys.defaultAllocationId] = id }
    suspend fun setAutoBackup(on: Boolean) = context.dataStore.edit { it[Keys.autoBackup] = on }
    suspend fun setLastBackupAt(time: Long) = context.dataStore.edit { it[Keys.lastBackupAt] = time }
    suspend fun setHideAmounts(on: Boolean) = context.dataStore.edit { it[Keys.hideAmounts] = on }
    suspend fun setAppStyle(style: String) = context.dataStore.edit { it[Keys.appStyle] = style }
    suspend fun setAppLock(on: Boolean) = context.dataStore.edit { it[Keys.appLock] = on }
    suspend fun setOwnerName(name: String) = context.dataStore.edit { it[Keys.ownerName] = name }
    suspend fun setSmartAlerts(on: Boolean) = context.dataStore.edit { it[Keys.smartAlerts] = on }
    suspend fun setReminders(on: Boolean) = context.dataStore.edit { it[Keys.reminders] = on }
    suspend fun setLastSummaryMonth(month: String) = context.dataStore.edit { it[Keys.lastSummaryMonth] = month }
    suspend fun setRemindedRecurring(keys: Set<String>) = context.dataStore.edit { it[Keys.remindedRecurring] = keys }

    suspend fun setBackupTarget(uri: String?, label: String?) = context.dataStore.edit {
        if (uri == null) {
            it.remove(Keys.backupUri)
            it.remove(Keys.backupLabel)
        } else {
            it[Keys.backupUri] = uri
            if (label != null) it[Keys.backupLabel] = label else it.remove(Keys.backupLabel)
        }
    }
}
