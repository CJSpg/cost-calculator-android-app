package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuItemDao {
    @Query("SELECT * FROM menu_items WHERE syncStatus != 'PENDING_DELETE' ORDER BY name ASC")
    fun getAllMenuItems(): Flow<List<MenuItem>>

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getMenuItemById(id: String): MenuItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItem(menuItem: MenuItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItems(menuItems: List<MenuItem>)

    @Query("UPDATE menu_items SET syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("DELETE FROM menu_items WHERE id = :id")
    suspend fun deleteMenuItemDirectly(id: String)

    @Query("SELECT * FROM menu_items WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingMenuItems(): List<MenuItem>
}

@Dao
interface DayTypeTemplateDao {
    @Query("SELECT * FROM day_type_templates ORDER BY id ASC")
    fun getAllTemplates(): Flow<List<DayTypeTemplate>>

    @Query("SELECT * FROM day_type_templates WHERE id = :id")
    suspend fun getTemplateById(id: String): DayTypeTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: DayTypeTemplate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<DayTypeTemplate>)

    @Query("DELETE FROM day_type_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("SELECT * FROM day_type_templates WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingTemplates(): List<DayTypeTemplate>
}

@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_plans WHERE syncStatus != 'PENDING_DELETE' ORDER BY createdAt DESC")
    fun getAllMealPlans(): Flow<List<MealPlan>>

    @Query("SELECT * FROM meal_plans WHERE id = :id")
    fun getMealPlanFlow(id: String): Flow<MealPlan?>

    @Query("SELECT * FROM meal_plans WHERE id = :id")
    suspend fun getMealPlanById(id: String): MealPlan?

    @Query("SELECT * FROM meal_plans WHERE planCode = :planCode AND syncStatus != 'PENDING_DELETE' LIMIT 1")
    suspend fun getMealPlanByCode(planCode: String): MealPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlan(mealPlan: MealPlan)

    @Query("UPDATE meal_plans SET syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("DELETE FROM meal_plans WHERE id = :id")
    suspend fun deleteMealPlanDirectly(id: String)

    @Query("SELECT * FROM meal_plans WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingMealPlans(): List<MealPlan>
}

@Dao
interface MealPlanDayDao {
    @Query("SELECT * FROM meal_plan_days WHERE planId = :planId ORDER BY dayIndex ASC")
    fun getDaysForPlan(planId: String): Flow<List<MealPlanDay>>

    @Query("SELECT * FROM meal_plan_days WHERE planId = :planId ORDER BY dayIndex ASC")
    suspend fun getDaysForPlanDirect(planId: String): List<MealPlanDay>

    @Query("SELECT * FROM meal_plan_days WHERE planId = :planId AND dayIndex = :dayIndex LIMIT 1")
    suspend fun getDay(planId: String, dayIndex: Int): MealPlanDay?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlanDay(day: MealPlanDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlanDays(days: List<MealPlanDay>)

    @Query("DELETE FROM meal_plan_days WHERE planId = :planId")
    suspend fun deleteDaysForPlan(planId: String)

    @Query("SELECT * FROM meal_plan_days WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingMealPlanDays(): List<MealPlanDay>
}
