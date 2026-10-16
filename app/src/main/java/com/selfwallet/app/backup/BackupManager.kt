package com.selfwallet.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.selfwallet.app.SelfWalletApp
import com.selfwallet.app.data.Repository
import com.selfwallet.app.data.db.AllocationEntity
import com.selfwallet.app.data.db.BudgetEntity
import com.selfwallet.app.data.db.CategoryEntity
import com.selfwallet.app.data.db.RateEntity
import com.selfwallet.app.data.db.RuleEntity
import com.selfwallet.app.data.db.TransactionEntity
import com.selfwallet.app.util.Currencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class BackupFile(
    val app: String = "SelfWallet",
    val version: Int = 1,
    val exportedAt: Long,
    val baseCurrency: String,
    val categories: List<CategoryEntity>,
    val allocations: List<AllocationEntity>,
    val rules: List<RuleEntity>,
    val rates: List<RateEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity> = emptyList(),
)

/**
 * Backups are a single JSON file written through Android's Storage Access Framework.
 * Because Google Drive, OneDrive, Dropbox etc. all plug into the system file picker,
 * the user can keep the file on the phone or in their own cloud drive, and this app
 * never needs the INTERNET permission or any account sign-in.
 */
class BackupManager(private val context: Context, private val repo: Repository) {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun writeTo(uri: Uri) = withContext(Dispatchers.IO) {
        val s = repo.snapshot()
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            baseCurrency = repo.settings.current().baseCurrency,
            categories = s.categories,
            allocations = s.allocations,
            rules = s.rules,
            rates = s.rates,
            transactions = s.transactions,
            budgets = s.budgets,
        )
        val bytes = json.encodeToString(BackupFile.serializer(), file).toByteArray()
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: context.contentResolver.openOutputStream(uri, "w")
            ?: error("Cannot open backup file for writing")
        out.use { it.write(bytes) }
        repo.settings.setLastBackupAt(System.currentTimeMillis())
    }

    /** Reads a backup and returns how many transactions it contains, without changing anything. */
    suspend fun inspect(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Cannot open file")
        json.decodeFromString(BackupFile.serializer(), text)
    }

    suspend fun restore(file: BackupFile) {
        repo.replaceAll(
            Repository.Snapshot(
                categories = file.categories,
                allocations = file.allocations,
                rules = file.rules,
                rates = file.rates,
                transactions = file.transactions,
                budgets = file.budgets,
            ),
        )
        repo.settings.setBaseCurrency(file.baseCurrency)
        repo.ensureSeeded()
    }

    /** Keeps read/write access to [uri] across reboots. */
    fun persist(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            .onFailure {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
    }

    fun release(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
    }

    /** A human label for the chosen file, e.g. "Google Drive · self-wallet-backup.json". */
    fun describe(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "backup.json"
        return "${providerName(uri)} · $name"
    }

    companion object {
        fun providerName(uri: Uri): String {
            val auth = uri.authority.orEmpty()
            return when {
                auth.contains("google.android.apps.docs") -> "Google Drive"
                auth.contains("skydrive") || auth.contains("onedrive") -> "OneDrive"
                auth.contains("dropbox") -> "Dropbox"
                auth.contains("externalstorage") || auth.contains("media") || auth.contains("downloads") -> "This device"
                else -> auth.substringBefore('.').ifEmpty { "Storage" }.replaceFirstChar { it.uppercase() }
            }
        }

        fun defaultFileName(): String = "self-wallet-backup.json"
    }
}

object CsvExporter {
    suspend fun write(
        context: Context,
        uri: Uri,
        rows: List<TransactionEntity>,
        categories: Map<Long, CategoryEntity>,
        allocations: Map<Long, AllocationEntity>,
        baseCurrency: String,
        rateOf: (String) -> Double,
    ) = withContext(Dispatchers.IO) {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append("Date,Type,Merchant,Category,Expense For,Personal/Corporate,Amount,Currency,Amount ($baseCurrency),Account,Note,Source\n")
        rows.forEach { t ->
            val alloc = t.allocationId?.let { allocations[it] }
            val base = t.amount * rateOf(t.currency)
            sb.append(
                listOf(
                    df.format(Date(t.timestamp)),
                    t.type,
                    t.merchant,
                    t.categoryId?.let { categories[it]?.name }.orEmpty(),
                    alloc?.name.orEmpty(),
                    alloc?.kind.orEmpty(),
                    String.format(Locale.US, "%.${Currencies.info(t.currency).decimals}f", t.amount),
                    t.currency,
                    String.format(Locale.US, "%.2f", base),
                    t.account.orEmpty(),
                    t.note,
                    t.source,
                ).joinToString(",") { csv(it) },
            ).append('\n')
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(sb.toString().toByteArray()) }
            ?: error("Cannot open file")
    }

    private fun csv(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SelfWalletApp
        val settings = app.container.repository.settings.current()
        val uri = settings.backupUri ?: return Result.success()
        if (!settings.autoBackup) return Result.success()
        return runCatching { app.container.backup.writeTo(Uri.parse(uri)) }
            .fold({ Result.success() }, { if (runAttemptCount < 3) Result.retry() else Result.failure() })
    }
}

object BackupScheduler {
    private const val ONE_SHOT = "backup-soon"
    private const val DAILY = "backup-daily"

    /** Debounced backup a little after the data changed. */
    fun requestSoon(context: Context) {
        val req = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(20, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, req)
    }

    fun ensureDaily(context: Context) {
        val req = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
