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

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AllocationEntity::class,
        RuleEntity::class,
        RateEntity::class,
        BudgetEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun allocations(): AllocationDao
    abstract fun rules(): RuleDao
    abstract fun rates(): RateDao
    abstract fun budgets(): BudgetDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "self_wallet.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
