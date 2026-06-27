package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class SyncStatus {
    SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE, CONFLICT
}

enum class DayType {
    PREPARATION, PROTEIN, SLIMMING, METABOLISM
}

@Entity(tableName = "menu_items")
@JsonClass(generateAdapter = true)
data class MenuItem(
    @PrimaryKey val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val name: String,
    val packPrice: Double,
    val packSize: Int,
    val packageUnit: String,
    val unit: String,
    // Sync fields
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = 0L,
    val updatedByDeviceId: String = ""
)

@Entity(tableName = "day_type_templates")
@JsonClass(generateAdapter = true)
data class DayTypeTemplate(
    @PrimaryKey val id: String, // e.g. "PREPARATION", "PROTEIN", "SLIMMING", "METABOLISM"
    val name: String, // e.g. "準備日", "蛋白日", "纖體日", "新陳代謝日"
    val meals: List<Meal> = emptyList(),
    // Sync fields
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = 0L,
    val updatedByDeviceId: String = ""
)

@JsonClass(generateAdapter = true)
data class Meal(
    val id: String = java.util.UUID.randomUUID().toString(),
    val time: String, // e.g. "07:45", "睡前"
    val title: String, // e.g. "早餐", "小餐"
    val note: String = "",
    val items: List<MealItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MealItem(
    val menuItemId: String,
    val menuItemName: String,
    val quantity: Double,
    val unit: String,
    val note: String = ""
)

@Entity(tableName = "meal_plans")
@JsonClass(generateAdapter = true)
data class MealPlan(
    @PrimaryKey val id: String,
    val planCode: String, // 6-10 code
    val customerName: String, // displayName
    val internalCode: String? = null,
    val startDate: Long, // timestamp
    val endDate: Long, // startDate + 44 days
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE",
    val createdBy: String = "",
    val note: String = "",
    // Sync fields
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = 0L,
    val updatedByDeviceId: String = ""
)

@Entity(tableName = "meal_plan_days")
@JsonClass(generateAdapter = true)
data class MealPlanDay(
    @PrimaryKey val id: String, // e.g. "${planId}_day_${dayIndex}"
    val planId: String,
    val date: Long,
    val dayIndex: Int, // 1 to 45
    val dayType: DayType,
    val dayTypeName: String, // e.g. "準備日"
    val meals: List<Meal> = emptyList(),
    // Sync fields
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = 0L,
    val updatedByDeviceId: String = ""
)
