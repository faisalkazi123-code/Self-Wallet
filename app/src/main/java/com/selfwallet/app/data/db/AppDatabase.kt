package com.selfwallet.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE smsHash = :hash")
    suspend fun countByHash(hash: String): Int

    /** The original SMS and any stored repeats (their hash is "<hash>#<time>"). */
    @Query("SELECT * FROM transactions WHERE smsHash = :hash OR smsHash LIKE :hash || '#%'")
    suspend fun byHash(hash: String): List<TransactionEntity>

    @Query(
        "SELECT * FROM transactions WHERE type = :type AND currency = :currency " +
            "AND ABS(amount - :amount) < 0.0001 AND timestamp BETWEEN :from AND :to",
    )
    suspend fun similar(type: String, currency: String, amount: Double, from: Long, to: Long): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TransactionEntity>)

    @Upsert
    suspend fun upsert(tx: TransactionEntity): Long

    @Delete
    suspend fun delete(tx: TransactionEntity)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("UPDATE transactions SET allocationId = NULL WHERE allocationId = :allocationId")
    suspend fun clearAllocation(allocationId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<CategoryEntity>

    @Upsert
    suspend fun upsert(item: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CategoryEntity>)

    @Delete
    suspend fun delete(item: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface AllocationDao {
    @Query("SELECT * FROM allocations ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<AllocationEntity>>

    @Query("SELECT * FROM allocations ORDER BY sortOrder, name")
    suspend fun getAll(): List<AllocationEntity>

    @Upsert
    suspend fun upsert(item: AllocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AllocationEntity>)

    @Delete
    suspend fun delete(item: AllocationEntity)

    @Query("DELETE FROM allocations")
    suspend fun deleteAll()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY keyword")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY keyword")
    suspend fun getAll(): List<RuleEntity>

    @Upsert
    suspend fun upsert(item: RuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RuleEntity>)

    @Delete
    suspend fun delete(item: RuleEntity)

    @Query("DELETE FROM rules")
    suspend fun deleteAll()
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates ORDER BY currency")
    fun observeAll(): Flow<List<RateEntity>>

    @Query("SELECT * FROM rates ORDER BY currency")
    suspend fun getAll(): List<RateEntity>

    @Upsert
    suspend fun upsert(item: RateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RateEntity>)

    @Delete
    suspend fun delete(item: RateEntity)

    @Query("DELETE FROM rates")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY categoryId IS NOT NULL, id")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    @Upsert
    suspend fun upsert(item: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BudgetEntity>)

    @Delete
    suspend fun delete(item: BudgetEntity)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteForCategory(categoryId: Long)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE accountKey = :key")
    suspend fun byKey(key: String): AccountEntity?

    @Upsert
    suspend fun upsert(item: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AccountEntity>)

    @Delete
    suspend fun delete(item: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY paid, dueDay")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills")
    suspend fun getAll(): List<BillEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: BillEntity): Long

    @Upsert
    suspend fun upsert(item: BillEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BillEntity>)

    @Delete
    suspend fun delete(item: BillEntity)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}

@Dao
interface SplitDao {
    @Query("SELECT * FROM splits ORDER BY settled, id")
    fun observeAll(): Flow<List<SplitEntity>>

    @Query("SELECT * FROM splits")
    suspend fun getAll(): List<SplitEntity>

    @Query("SELECT * FROM splits WHERE txId = :txId ORDER BY id")
    suspend fun forTx(txId: Long): List<SplitEntity>

    @Query("DELETE FROM splits WHERE txId = :txId")
    suspend fun deleteForTx(txId: Long)

    @Upsert
    suspend fun upsert(item: SplitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SplitEntity>)

    @Query("DELETE FROM splits")
    suspend fun deleteAll()
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDay DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips")
    suspend fun getAll(): List<TripEntity>

    @Upsert
    suspend fun upsert(item: TripEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TripEntity>)

    @Delete
    suspend fun delete(item: TripEntity)

    @Query("UPDATE transactions SET tripId = NULL WHERE tripId = :tripId")
    suspend fun clearFromTransactions(tripId: Long)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates")
    suspend fun getAll(): List<TemplateEntity>

    @Upsert
    suspend fun upsert(item: TemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TemplateEntity>)

    @Delete
    suspend fun delete(item: TemplateEntity)

    @Query("DELETE FROM templates")
    suspend fun deleteAll()
}

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AllocationEntity::class,
        RuleEntity::class,
        RateEntity::class,
        BudgetEntity::class,
        AccountEntity::class,
        BillEntity::class,
        SplitEntity::class,
        TripEntity::class,
        TemplateEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun allocations(): AllocationDao
    abstract fun rules(): RuleDao
    abstract fun rates(): RateDao
    abstract fun budgets(): BudgetDao
    abstract fun accounts(): AccountDao
    abstract fun bills(): BillDao
    abstract fun splits(): SplitDao
    abstract fun trips(): TripDao
    abstract fun templates(): TemplateDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "self_wallet.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN duplicateOf INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN dupKind TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN dupStatus TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptPath TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN claimStatus TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`categoryId` INTEGER, `kind` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                        "`alertedMonth` TEXT, `alertedLevel` INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN tripId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`accountKey` TEXT NOT NULL, `label` TEXT NOT NULL, `last4` TEXT, `balance` REAL NOT NULL, " +
                        "`currency` TEXT NOT NULL, `isCreditLimit` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_accountKey` ON `accounts` (`accountKey`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `amount` REAL NOT NULL, `currency` TEXT NOT NULL, `dueDay` INTEGER NOT NULL, " +
                        "`account` TEXT, `paid` INTEGER NOT NULL, `hash` TEXT NOT NULL, `reminded` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bills_hash` ON `bills` (`hash`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `splits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`txId` INTEGER NOT NULL, `person` TEXT NOT NULL, `amount` REAL NOT NULL, `settled` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_splits_txId` ON `splits` (`txId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `startDay` INTEGER NOT NULL, `endDay` INTEGER NOT NULL, `allocationId` INTEGER)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `amount` REAL NOT NULL, `currency` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                        "`categoryId` INTEGER, `allocationId` INTEGER, `merchant` TEXT NOT NULL)",
                )
            }
        }
    }
}
