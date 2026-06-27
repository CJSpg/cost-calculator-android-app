package com.example.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class MenuRepository(
    private val context: Context,
    private val menuItemDao: MenuItemDao,
    private val dayTypeTemplateDao: DayTypeTemplateDao,
    private val mealPlanDao: MealPlanDao,
    private val mealPlanDayDao: MealPlanDayDao
) {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val deviceId: String by lazy {
        try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    // Live Flow data from local cache
    val allMenuItems: Flow<List<MenuItem>> = menuItemDao.getAllMenuItems()
    val allTemplates: Flow<List<DayTypeTemplate>> = dayTypeTemplateDao.getAllTemplates()
    val allMealPlans: Flow<List<MealPlan>> = mealPlanDao.getAllMealPlans()

    fun getDaysForPlan(planId: String): Flow<List<MealPlanDay>> = mealPlanDayDao.getDaysForPlan(planId)
    fun getMealPlanFlow(planId: String): Flow<MealPlan?> = mealPlanDao.getMealPlanFlow(planId)

    // --- MenuItem CRUD ---
    suspend fun insertMenuItem(item: MenuItem) = withContext(Dispatchers.IO) {
        val localItem = item.copy(
            syncStatus = SyncStatus.PENDING_CREATE,
            lastModifiedAt = System.currentTimeMillis(),
            updatedByDeviceId = deviceId
        )
        menuItemDao.insertMenuItem(localItem)
        triggerSync()
    }

    suspend fun updateMenuItem(item: MenuItem) = withContext(Dispatchers.IO) {
        val localItem = item.copy(
            syncStatus = SyncStatus.PENDING_UPDATE,
            lastModifiedAt = System.currentTimeMillis(),
            updatedByDeviceId = deviceId
        )
        menuItemDao.insertMenuItem(localItem)
        triggerSync()
    }

    suspend fun deleteMenuItem(id: String) = withContext(Dispatchers.IO) {
        menuItemDao.markDeleted(id)
        triggerSync()
    }

    // --- DayTypeTemplate CRUD ---
    suspend fun insertTemplate(template: DayTypeTemplate) = withContext(Dispatchers.IO) {
        val localTemplate = template.copy(
            syncStatus = SyncStatus.PENDING_CREATE,
            lastModifiedAt = System.currentTimeMillis(),
            updatedByDeviceId = deviceId
        )
        dayTypeTemplateDao.insertTemplate(localTemplate)
        triggerSync()
    }

    suspend fun updateTemplate(template: DayTypeTemplate) = withContext(Dispatchers.IO) {
        val localTemplate = template.copy(
            syncStatus = SyncStatus.PENDING_UPDATE,
            lastModifiedAt = System.currentTimeMillis(),
            updatedByDeviceId = deviceId
        )
        dayTypeTemplateDao.insertTemplate(localTemplate)
        triggerSync()
    }

    // --- MealPlan CRUD ---
    suspend fun createMealPlan(customerName: String, internalCode: String?, startDate: Long, creatorId: String): MealPlan = withContext(Dispatchers.IO) {
        val planId = UUID.randomUUID().toString()
        val planCode = generateUniquePlanCode()
        val endDate = startDate + (44L * 24 * 60 * 60 * 1000) // +44 days in ms

        val mealPlan = MealPlan(
            id = planId,
            planCode = planCode,
            customerName = customerName,
            internalCode = internalCode,
            startDate = startDate,
            endDate = endDate,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            createdBy = creatorId,
            syncStatus = SyncStatus.PENDING_CREATE,
            lastModifiedAt = System.currentTimeMillis(),
            updatedByDeviceId = deviceId
        )

        // Generate 45 empty days by default, or with some default dayType (e.g. PREPARATION)
        val days = (1..45).map { i ->
            val dayDate = startDate + ((i - 1).toLong() * 24 * 60 * 60 * 1000)
            MealPlanDay(
                id = "${planId}_day_$i",
                planId = planId,
                date = dayDate,
                dayIndex = i,
                dayType = DayType.PREPARATION,
                dayTypeName = "準備日",
                meals = emptyList(),
                syncStatus = SyncStatus.PENDING_CREATE,
                lastModifiedAt = System.currentTimeMillis(),
                updatedByDeviceId = deviceId
            )
        }

        mealPlanDao.insertMealPlan(mealPlan)
        mealPlanDayDao.insertMealPlanDays(days)

        triggerSync()
        return@withContext mealPlan
    }

    suspend fun updateMealPlan(plan: MealPlan) = withContext(Dispatchers.IO) {
        val updatedPlan = plan.copy(
            syncStatus = SyncStatus.PENDING_UPDATE,
            lastModifiedAt = System.currentTimeMillis(),
            updatedByDeviceId = deviceId
        )
        mealPlanDao.insertMealPlan(updatedPlan)
        triggerSync()
    }

    suspend fun deleteMealPlan(planId: String) = withContext(Dispatchers.IO) {
        mealPlanDao.markDeleted(planId)
        // Also mark days as pending delete or delete directly
        mealPlanDayDao.deleteDaysForPlan(planId)
        triggerSync()
    }

    // --- MealPlanDay CRUD ---
    suspend fun updateMealPlanDay(day: MealPlanDay) = withContext(Dispatchers.IO) {
        val updatedDay = day.copy(
            syncStatus = SyncStatus.PENDING_UPDATE,
            lastModifiedAt = System.currentTimeMillis(),
            updatedByDeviceId = deviceId
        )
        mealPlanDayDao.insertMealPlanDay(updatedDay)
        // Update parent plan's modified date as well
        val plan = mealPlanDao.getMealPlanById(day.planId)
        if (plan != null) {
            mealPlanDao.insertMealPlan(plan.copy(
                syncStatus = SyncStatus.PENDING_UPDATE,
                lastModifiedAt = System.currentTimeMillis(),
                updatedByDeviceId = deviceId
            ))
        }
        triggerSync()
    }

    suspend fun batchApplyDayTemplate(planId: String, dayIndices: List<Int>, template: DayTypeTemplate) = withContext(Dispatchers.IO) {
        val plan = mealPlanDao.getMealPlanById(planId) ?: return@withContext
        val daysToUpdate = dayIndices.mapNotNull { index ->
            val day = mealPlanDayDao.getDay(planId, index) ?: return@mapNotNull null
            day.copy(
                dayType = DayType.valueOf(template.id),
                dayTypeName = template.name,
                meals = template.meals,
                syncStatus = SyncStatus.PENDING_UPDATE,
                lastModifiedAt = System.currentTimeMillis(),
                updatedByDeviceId = deviceId
            )
        }
        mealPlanDayDao.insertMealPlanDays(daysToUpdate)
        
        mealPlanDao.insertMealPlan(plan.copy(
            syncStatus = SyncStatus.PENDING_UPDATE,
            lastModifiedAt = System.currentTimeMillis(),
            updatedByDeviceId = deviceId
        ))
        triggerSync()
    }

    // --- Query and Fetch via PlanCode ---
    suspend fun findMealPlanByCode(planCode: String): MealPlan? = withContext(Dispatchers.IO) {
        // 1. Search Room
        val localPlan = mealPlanDao.getMealPlanByCode(planCode)
        if (localPlan != null) return@withContext localPlan

        // 2. Search Firestore
        try {
            val snapshot = db.collection("mealPlans")
                .whereEqualTo("planCode", planCode)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val doc = snapshot.documents.first()
                val firestorePlan = doc.toObject(MealPlan::class.java)
                if (firestorePlan != null) {
                    // Download to Room cache
                    mealPlanDao.insertMealPlan(firestorePlan.copy(syncStatus = SyncStatus.SYNCED))
                    
                    // Also download the subcollection days
                    val daysSnapshot = doc.reference.collection("days").get().await()
                    val days = daysSnapshot.documents.mapNotNull { dayDoc ->
                        dayDoc.toObject(MealPlanDay::class.java)
                    }
                    if (days.isNotEmpty()) {
                        mealPlanDayDao.insertMealPlanDays(days.map { it.copy(syncStatus = SyncStatus.SYNCED) })
                    }
                    return@withContext firestorePlan
                }
            }
        } catch (e: Exception) {
            Log.e("MenuRepository", "Error searching Firestore for planCode: $planCode", e)
        }
        return@withContext null
    }

    // --- Sync Logic (Offline-first) ---
    suspend fun triggerSync(): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d("MenuRepository", "Starting Sync Engine...")
        try {
            // 1. Sync Menu Items
            syncMenuItems()

            // 2. Sync Templates
            syncTemplates()

            // 3. Sync Meal Plans & Days
            syncMealPlans()

            Log.d("MenuRepository", "Sync Engine Completed Successfully.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MenuRepository", "Sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun syncMenuItems() {
        // Upload pending
        val pendingItems = menuItemDao.getPendingMenuItems()
        for (item in pendingItems) {
            try {
                if (item.syncStatus == SyncStatus.PENDING_DELETE) {
                    db.collection("menuItems").document(item.id).delete().await()
                    menuItemDao.deleteMenuItemDirectly(item.id)
                } else {
                    db.collection("menuItems").document(item.id).set(item.copy(syncStatus = SyncStatus.SYNCED)).await()
                    menuItemDao.insertMenuItem(item.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                Log.e("MenuRepository", "Failed to sync MenuItem ${item.id}: ${e.message}")
            }
        }

        // Download updates from Firestore
        try {
            val snapshot = db.collection("menuItems").get().await()
            val firestoreItems = snapshot.documents.mapNotNull { it.toObject(MenuItem::class.java) }
            for (fItem in firestoreItems) {
                val localItem = menuItemDao.getMenuItemById(fItem.id)
                if (localItem == null || fItem.lastModifiedAt > localItem.lastModifiedAt) {
                    // Update cache
                    menuItemDao.insertMenuItem(fItem.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis()))
                }
            }
        } catch (e: Exception) {
            Log.e("MenuRepository", "Failed to pull MenuItems: ${e.message}")
        }
    }

    private suspend fun syncTemplates() {
        // Upload pending
        val pendingTemplates = dayTypeTemplateDao.getPendingTemplates()
        for (tpl in pendingTemplates) {
            try {
                db.collection("dayTypeTemplates").document(tpl.id).set(tpl.copy(syncStatus = SyncStatus.SYNCED)).await()
                dayTypeTemplateDao.insertTemplate(tpl.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e("MenuRepository", "Failed to sync Template ${tpl.id}: ${e.message}")
            }
        }

        // Download updates
        try {
            val snapshot = db.collection("dayTypeTemplates").get().await()
            val firestoreTemplates = snapshot.documents.mapNotNull { it.toObject(DayTypeTemplate::class.java) }
            for (fTpl in firestoreTemplates) {
                val localTpl = dayTypeTemplateDao.getTemplateById(fTpl.id)
                if (localTpl == null || fTpl.lastModifiedAt > localTpl.lastModifiedAt) {
                    dayTypeTemplateDao.insertTemplate(fTpl.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis()))
                }
            }
        } catch (e: Exception) {
            Log.e("MenuRepository", "Failed to pull DayTypeTemplates: ${e.message}")
        }
    }

    private suspend fun syncMealPlans() {
        // Upload pending plans
        val pendingPlans = mealPlanDao.getPendingMealPlans()
        for (plan in pendingPlans) {
            try {
                if (plan.syncStatus == SyncStatus.PENDING_DELETE) {
                    db.collection("mealPlans").document(plan.id).delete().await()
                    mealPlanDao.deleteMealPlanDirectly(plan.id)
                } else {
                    db.collection("mealPlans").document(plan.id).set(plan.copy(syncStatus = SyncStatus.SYNCED), SetOptions.merge()).await()
                    
                    // Upload days for this plan that are modified
                    val localDays = mealPlanDayDao.getDaysForPlanDirect(plan.id)
                    for (day in localDays) {
                        if (day.syncStatus != SyncStatus.SYNCED) {
                            db.collection("mealPlans").document(plan.id)
                                .collection("days").document(day.id)
                                .set(day.copy(syncStatus = SyncStatus.SYNCED)).await()
                            
                            mealPlanDayDao.insertMealPlanDay(day.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis()))
                        }
                    }

                    mealPlanDao.insertMealPlan(plan.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                Log.e("MenuRepository", "Failed to sync MealPlan ${plan.id}: ${e.message}")
            }
        }

        // Download / Sync all downloaded meal plans for conflicts
        val allLocalPlans = mealPlanDao.getAllMealPlans().first()
        for (localPlan in allLocalPlans) {
            try {
                val doc = db.collection("mealPlans").document(localPlan.id).get().await()
                if (doc.exists()) {
                    val fPlan = doc.toObject(MealPlan::class.java)
                    if (fPlan != null && fPlan.lastModifiedAt > localPlan.lastModifiedAt) {
                        // Last modified wins: overwrite local with newer remote
                        mealPlanDao.insertMealPlan(fPlan.copy(syncStatus = SyncStatus.SYNCED))
                        
                        // Overwrite local days with newer remote days
                        val daysSnapshot = doc.reference.collection("days").get().await()
                        val remoteDays = daysSnapshot.documents.mapNotNull { it.toObject(MealPlanDay::class.java) }
                        if (remoteDays.isNotEmpty()) {
                            mealPlanDayDao.insertMealPlanDays(remoteDays.map { it.copy(syncStatus = SyncStatus.SYNCED) })
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MenuRepository", "Failed to sync-check MealPlan ${localPlan.id}: ${e.message}")
            }
        }
    }

    // Helper to generate a unique random 8-character alpha-numeric code
    private suspend fun generateUniquePlanCode(): String {
        val allowedChars = ('A'..'Z') + ('0'..'9')
        var code: String
        var exists: Boolean
        do {
            code = (1..8)
                .map { allowedChars.random() }
                .joinToString("")
            
            // Check Room
            val localPlan = mealPlanDao.getMealPlanByCode(code)
            exists = if (localPlan != null) {
                true
            } else {
                // Check Firestore
                try {
                    val snap = db.collection("mealPlans")
                        .whereEqualTo("planCode", code)
                        .limit(1)
                        .get()
                        .await()
                    !snap.isEmpty
                } catch (e: Exception) {
                    false
                }
            }
        } while (exists)
        return code
    }
}
