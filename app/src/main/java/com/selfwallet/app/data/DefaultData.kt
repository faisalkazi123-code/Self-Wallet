package com.selfwallet.app.data

import com.selfwallet.app.data.db.AllocationEntity
import com.selfwallet.app.data.db.CategoryEntity
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.data.db.TxType

object DefaultData {
    fun categories(): List<CategoryEntity> = listOf(
        CategoryEntity(name = "Food & Dining", icon = "food", color = 0xFFF97316, sortOrder = 0),
        CategoryEntity(name = "Groceries", icon = "grocery", color = 0xFF22C55E, sortOrder = 1),
        CategoryEntity(name = "Transport", icon = "car", color = 0xFF3B82F6, sortOrder = 2),
        CategoryEntity(name = "Fuel", icon = "fuel", color = 0xFF64748B, sortOrder = 3),
        CategoryEntity(name = "Shopping", icon = "shopping", color = 0xFFEC4899, sortOrder = 4),
        CategoryEntity(name = "Bills & Utilities", icon = "bill", color = 0xFFEAB308, sortOrder = 5),
        CategoryEntity(name = "Mobile & Internet", icon = "phone", color = 0xFF06B6D4, sortOrder = 6),
        CategoryEntity(name = "Entertainment", icon = "movie", color = 0xFF8B5CF6, sortOrder = 7),
        CategoryEntity(name = "Health", icon = "health", color = 0xFFEF4444, sortOrder = 8),
        CategoryEntity(name = "Travel", icon = "flight", color = 0xFF0EA5E9, sortOrder = 9),
        CategoryEntity(name = "Hotel & Stay", icon = "hotel", color = 0xFF6366F1, sortOrder = 10),
        CategoryEntity(name = "Rent & Home", icon = "home", color = 0xFFA16207, sortOrder = 11),
        CategoryEntity(name = "Education", icon = "school", color = 0xFF14B8A6, sortOrder = 12),
        CategoryEntity(name = "Office & Supplies", icon = "work", color = 0xFF475569, sortOrder = 13),
        CategoryEntity(name = "Cash Withdrawal", icon = "cash", color = 0xFF84CC16, sortOrder = 14),
        CategoryEntity(name = "Other", icon = "other", color = 0xFF94A3B8, sortOrder = 15),
        CategoryEntity(name = "Salary", icon = "salary", color = 0xFF10B981, type = TxType.INCOME, sortOrder = 20),
        CategoryEntity(name = "Reimbursement", icon = "work", color = 0xFF0D9488, type = TxType.INCOME, sortOrder = 21),
        CategoryEntity(name = "Refund", icon = "refund", color = 0xFF22D3EE, type = TxType.INCOME, sortOrder = 22),
        CategoryEntity(name = "Other Income", icon = "savings", color = 0xFF34D399, type = TxType.INCOME, sortOrder = 23),
    )

    fun allocations(): List<AllocationEntity> = listOf(
        AllocationEntity(name = "Personal", kind = SpendKind.PERSONAL, color = 0xFF0F766E, sortOrder = 0),
        AllocationEntity(name = "Family", kind = SpendKind.PERSONAL, color = 0xFFDB2777, sortOrder = 1),
        AllocationEntity(name = "Company", kind = SpendKind.CORPORATE, color = 0xFF2563EB, sortOrder = 2),
    )
}
