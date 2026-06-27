package com.example.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.notification.MealNotificationManager
import com.example.pdf.PdfExporter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

sealed interface SyncUIState {
    object Idle : SyncUIState
    object Syncing : SyncUIState
    object Success : SyncUIState
    data class Error(val msg: String) : SyncUIState
}

class MainViewModel(private val repository: MenuRepository) : ViewModel() {

    // Cache Lists
    val menuItems: StateFlow<List<MenuItem>> = repository.allMenuItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dayTypeTemplates: StateFlow<List<DayTypeTemplate>> = repository.allTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mealPlans: StateFlow<List<MealPlan>> = repository.allMealPlans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Sync status State
    private val _syncState = MutableStateFlow<SyncUIState>(SyncUIState.Idle)
    val syncState: StateFlow<SyncUIState> = _syncState.asStateFlow()

    // Search and Filters
    private val _planSearchQuery = MutableStateFlow("")
    val planSearchQuery = _planSearchQuery.asStateFlow()

    private val _menuSearchQuery = MutableStateFlow("")
    val menuSearchQuery = _menuSearchQuery.asStateFlow()

    val filteredMealPlans = combine(mealPlans, planSearchQuery) { plans, query ->
        if (query.isBlank()) {
            plans
        } else {
            plans.filter {
                it.customerName.contains(query, ignoreCase = true) ||
                it.planCode.contains(query, ignoreCase = true) ||
                (it.internalCode?.contains(query, ignoreCase = true) ?: false)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredMenuItems = combine(menuItems, menuSearchQuery) { items, query ->
        if (query.isBlank()) {
            items
        } else {
            items.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Pre-populate data on start
        viewModelScope.launch {
            prepopulateDataIfNeeded()
            // Pull Firestore updates on launch
            syncData()
        }
    }

    fun setPlanSearchQuery(query: String) {
        _planSearchQuery.value = query
    }

    fun setMenuSearchQuery(query: String) {
        _menuSearchQuery.value = query
    }

    // --- MenuItem CRUD ---
    fun addMenuItem(name: String, price: Double, packSize: Int, packageUnit: String, unit: String) {
        viewModelScope.launch {
            val item = MenuItem(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                packPrice = price,
                packSize = packSize,
                packageUnit = packageUnit,
                unit = unit
            )
            repository.insertMenuItem(item)
        }
    }

    fun updateMenuItem(item: MenuItem) {
        viewModelScope.launch {
            repository.updateMenuItem(item)
        }
    }

    fun deleteMenuItem(id: String) {
        viewModelScope.launch {
            repository.deleteMenuItem(id)
        }
    }

    // --- Template CRUD ---
    fun addTemplate(name: String, meals: List<Meal>, templateId: String = java.util.UUID.randomUUID().toString()) {
        viewModelScope.launch {
            val tpl = DayTypeTemplate(
                id = templateId,
                name = name,
                meals = meals
            )
            repository.insertTemplate(tpl)
        }
    }

    fun updateTemplate(tpl: DayTypeTemplate) {
        viewModelScope.launch {
            repository.updateTemplate(tpl)
        }
    }

    // --- MealPlan CRUD ---
    fun createMealPlan(customerName: String, internalCode: String?, startDate: Long, creatorId: String, onResult: (MealPlan) -> Unit) {
        viewModelScope.launch {
            val plan = repository.createMealPlan(customerName, internalCode, startDate, creatorId)
            onResult(plan)
        }
    }

    fun deleteMealPlan(planId: String) {
        viewModelScope.launch {
            repository.deleteMealPlan(planId)
        }
    }

    // --- MealPlanDay CRUD ---
    fun getDaysForPlan(planId: String): Flow<List<MealPlanDay>> {
        return repository.getDaysForPlan(planId)
    }

    fun getMealPlanFlow(planId: String): Flow<MealPlan?> {
        return repository.getMealPlanFlow(planId)
    }

    fun updateMealPlanDay(day: MealPlanDay) {
        viewModelScope.launch {
            repository.updateMealPlanDay(day)
        }
    }

    fun batchApplyDayTemplate(planId: String, dayIndices: List<Int>, template: DayTypeTemplate) {
        viewModelScope.launch {
            repository.batchApplyDayTemplate(planId, dayIndices, template)
        }
    }

    // --- Search with planCode ---
    fun findPlanByCode(planCode: String, onResult: (MealPlan?, String?) -> Unit) {
        viewModelScope.launch {
            val plan = repository.findMealPlanByCode(planCode)
            if (plan != null) {
                onResult(plan, null)
            } else {
                onResult(null, "此代碼不存在。若您目前處於離線狀態，本機找不到此菜單，請連線後再試。")
            }
        }
    }

    // --- Sync Engine trigger ---
    fun syncData() {
        viewModelScope.launch {
            _syncState.value = SyncUIState.Syncing
            val result = repository.triggerSync()
            if (result.isSuccess) {
                _syncState.value = SyncUIState.Success
            } else {
                _syncState.value = SyncUIState.Error(result.exceptionOrNull()?.message ?: "未知同步錯誤")
            }
        }
    }

    // --- PDF Export ---
    fun exportPdf(context: Context, plan: MealPlan, days: List<MealPlanDay>, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val file = PdfExporter.exportMealPlanToPdf(context, plan, days)
            onResult(file)
        }
    }

    // --- Notification Toggles ---
    fun setNotificationEnabled(context: Context, planId: String, enabled: Boolean) {
        MealNotificationManager.setReminderEnabled(context, planId, enabled)
        if (enabled) {
            MealNotificationManager.scheduleAlarmsForPlan(context, planId)
        } else {
            MealNotificationManager.cancelAlarmsForPlan(context, planId)
        }
    }

    fun isNotificationEnabled(context: Context, planId: String): Boolean {
        return MealNotificationManager.isReminderEnabled(context, planId)
    }

    fun refreshAlarmsForPlan(context: Context, planId: String) {
        if (isNotificationEnabled(context, planId)) {
            MealNotificationManager.scheduleAlarmsForPlan(context, planId)
        }
    }

    // --- Pre-populate Helper ---
    private suspend fun prepopulateDataIfNeeded() {
        val currentTemplates = dayTypeTemplates.first()
        if (currentTemplates.isEmpty()) {
            Log.d("MainViewModel", "Pre-populating default menu items and day type templates...")
            
            // 1. Menu Items
            val defaultItems = listOf(
                MenuItem("item_protein", name = "優質蛋白素－全植物配方家庭號", packPrice = 1890.0, packSize = 90, packageUnit = "罐", unit = "匙"),
                MenuItem("item_slimming", name = "纖體配方能量代餐", packPrice = 1500.0, packSize = 14, packageUnit = "盒", unit = "包"),
                MenuItem("item_tea", name = "新陳代謝複合茶飲", packPrice = 1200.0, packSize = 30, packageUnit = "盒", unit = "包"),
                MenuItem("item_omega", name = "複合深海魚油精華", packPrice = 980.0, packSize = 90, packageUnit = "瓶", unit = "顆"),
                MenuItem("item_double_x", name = "綜合維他命 DOUBLE X", packPrice = 2200.0, packSize = 31, packageUnit = "盒", unit = "對")
            )
            for (item in defaultItems) {
                repository.insertMenuItem(item.copy(syncStatus = SyncStatus.SYNCED))
            }

            // 2. Day Type Templates
            val prepTemplate = DayTypeTemplate(
                id = DayType.PREPARATION.name,
                name = "準備日",
                meals = listOf(
                    Meal(time = "08:00", title = "早餐", note = "沖泡蛋白飲配合充足水份", items = listOf(
                        MealItem("item_protein", "優質蛋白素－全植物配方家庭號", 2.0, "匙"),
                        MealItem("item_double_x", "綜合維他命 DOUBLE X", 1.0, "對")
                    )),
                    Meal(time = "12:00", title = "午餐", note = "清淡蔬菜五穀雜糧與魚油", items = listOf(
                        MealItem("item_omega", "複合深海魚油精華", 2.0, "顆")
                    )),
                    Meal(time = "18:00", title = "晚餐", note = "適量低脂肉類蛋白質", items = emptyList())
                )
            )

            val proteinTemplate = DayTypeTemplate(
                id = DayType.PROTEIN.name,
                name = "蛋白日",
                meals = listOf(
                    Meal(time = "08:00", title = "早餐", note = "高蛋白代餐高飽足感", items = listOf(
                        MealItem("item_protein", "優質蛋白素－全植物配方家庭號", 2.0, "匙"),
                        MealItem("item_slimming", "纖體配方能量代餐", 1.0, "包")
                    )),
                    Meal(time = "10:30", title = "早小餐", note = "補充氨基酸與維他命", items = emptyList()),
                    Meal(time = "13:00", title = "午餐", note = "低卡主食", items = listOf(
                        MealItem("item_omega", "複合深海魚油精華", 2.0, "顆")
                    )),
                    Meal(time = "15:30", title = "下午小餐", note = "下午茶飲茶多酚提神", items = listOf(
                        MealItem("item_tea", "新陳代謝複合茶飲", 1.0, "包")
                    )),
                    Meal(time = "18:00", title = "晚餐", note = "高蛋白少油蔬菜", items = emptyList()),
                    Meal(time = "睡前", title = "睡前餐", note = "幫助夜間修復", items = listOf(
                        MealItem("item_protein", "優質蛋白素－全植物配方家庭號", 1.0, "匙")
                    ))
                )
            )

            val slimmingTemplate = DayTypeTemplate(
                id = DayType.SLIMMING.name,
                name = "纖體日",
                meals = listOf(
                    Meal(time = "08:00", title = "早餐", note = "纖體能量補充", items = listOf(
                        MealItem("item_slimming", "纖體配方能量代餐", 1.0, "包")
                    )),
                    Meal(time = "12:00", title = "午餐", note = "豐富水溶性纖維", items = listOf(
                        MealItem("item_omega", "複合深海魚油精華", 2.0, "顆")
                    )),
                    Meal(time = "15:00", title = "下午餐", note = "茶飲加速體內環保", items = listOf(
                        MealItem("item_tea", "新陳代謝複合茶飲", 1.0, "包")
                    )),
                    Meal(time = "18:00", title = "晚餐", note = "高蛋白配合代餐", items = listOf(
                        MealItem("item_protein", "優質蛋白素－全植物配方家庭號", 1.0, "匙")
                    ))
                )
            )

            val metabolismTemplate = DayTypeTemplate(
                id = DayType.METABOLISM.name,
                name = "新陳代謝日",
                meals = listOf(
                    Meal(time = "08:00", title = "早餐", note = "啟動代謝日", items = listOf(
                        MealItem("item_protein", "優質蛋白素－全植物配方家庭號", 2.0, "匙"),
                        MealItem("item_tea", "新陳代謝複合茶飲", 1.0, "包")
                    )),
                    Meal(time = "12:00", title = "午餐", note = "補充不飽和脂肪酸", items = listOf(
                        MealItem("item_omega", "複合深海魚油精華", 2.0, "顆")
                    )),
                    Meal(time = "18:00", title = "晚餐", note = "適量蔬菜碳水化合物", items = emptyList())
                )
            )

            repository.insertTemplate(prepTemplate.copy(syncStatus = SyncStatus.SYNCED))
            repository.insertTemplate(proteinTemplate.copy(syncStatus = SyncStatus.SYNCED))
            repository.insertTemplate(slimmingTemplate.copy(syncStatus = SyncStatus.SYNCED))
            repository.insertTemplate(metabolismTemplate.copy(syncStatus = SyncStatus.SYNCED))
        }
    }
}
