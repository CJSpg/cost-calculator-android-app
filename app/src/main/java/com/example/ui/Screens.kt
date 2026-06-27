package com.example.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.data.*
import com.example.ui.theme.WellnessAmber
import com.example.ui.theme.WellnessGreen
import com.example.ui.theme.WellnessGold
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen {
    object FrontHome : Screen()
    object BackLogin : Screen()
    object BackHome : Screen()
    data class CalendarView(val planId: String) : Screen()
    data class DayDetail(val planId: String, val dayIndex: Int) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel,
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()
    val syncState by mainViewModel.syncState.collectAsState()

    // Global back handling
    BackHandler(enabled = currentScreen != Screen.FrontHome) {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            is Screen.FrontHome -> "45天客製化菜單管理"
                            is Screen.BackLogin -> "管理後台登入"
                            is Screen.BackHome -> "後台管理系統"
                            is Screen.CalendarView -> "45天菜單安排"
                            is Screen.DayDetail -> "單日餐次細節"
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    if (currentScreen != Screen.FrontHome) {
                        IconButton(onClick = { onBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = {
                    // Manual sync indicator / trigger in top bar
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .clickable { mainViewModel.syncData() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            val syncIcon = when (syncState) {
                                is SyncUIState.Syncing -> Icons.Default.Sync
                                is SyncUIState.Error -> Icons.Default.CloudOff
                                else -> Icons.Default.CloudDone
                            }
                            val syncColor = when (syncState) {
                                is SyncUIState.Syncing -> WellnessGold
                                is SyncUIState.Error -> MaterialTheme.colorScheme.error
                                else -> Color.White
                            }
                            Icon(
                                imageVector = syncIcon,
                                contentDescription = "同步",
                                tint = syncColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (syncState) {
                                    is SyncUIState.Syncing -> "同步中..."
                                    is SyncUIState.Error -> "同步失敗"
                                    else -> "已同步"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Logout or Login shortcuts
                    if (currentScreen is Screen.FrontHome) {
                        IconButton(onClick = {
                            if (authState is AuthState.Success) {
                                onNavigate(Screen.BackHome)
                            } else {
                                onNavigate(Screen.BackLogin)
                            }
                        }) {
                            Icon(
                                imageVector = if (authState is AuthState.Success) Icons.Default.AdminPanelSettings else Icons.Default.Lock,
                                contentDescription = "後台管理",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else if (currentScreen is Screen.BackHome) {
                        IconButton(onClick = {
                            authViewModel.logout()
                            onNavigate(Screen.FrontHome)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "登出",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                is Screen.FrontHome -> FrontHomeScreen(mainViewModel, onNavigate)
                is Screen.BackLogin -> LoginScreen(authViewModel, onNavigate)
                is Screen.BackHome -> BackHomeScreen(mainViewModel, authViewModel, onNavigate)
                is Screen.CalendarView -> CalendarScreen(mainViewModel, currentScreen.planId, onNavigate)
                is Screen.DayDetail -> DayDetailScreen(mainViewModel, currentScreen.planId, currentScreen.dayIndex, onBack)
            }
        }
    }
}

// ==================== SCREEN 1: Front-End Home ====================
@Composable
fun FrontHomeScreen(
    viewModel: MainViewModel,
    onNavigate: (Screen) -> Unit
) {
    val context = LocalContext.current
    val plans by viewModel.filteredMealPlans.collectAsState()
    val searchQuery by viewModel.planSearchQuery.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var searchCodeInput by remember { mutableStateOf("") }
    var searchLoading by remember { mutableStateOf(false) }

    // Floating UI trigger for sync success / failures
    LaunchedEffect(syncState) {
        if (syncState is SyncUIState.Error) {
            Toast.makeText(context, "同步失敗: ${(syncState as SyncUIState.Error).msg}", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(WellnessGreen, WellnessGreen.copy(alpha = 0.8f))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        text = "健康 45 天",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "客製化菜單管理系統",
                        color = WellnessGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "本系統提供多日菜單客製、餐次管理、自動產出 PDF 週表與本機精準膳食提醒。支援離線作業與雲端庫自動同步。",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Action: Create New Plan
        item {
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("create_plan_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "新增")
                Spacer(modifier = Modifier.width(8.dp))
                Text("建立 45 天新菜單", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Action: Enter Plan Code Search
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("以菜單代碼 (planCode) 查回菜單", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = searchCodeInput,
                            onValueChange = { searchCodeInput = it.uppercase() },
                            placeholder = { Text("例如: A1B2C3D4") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("code_search_input"),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (searchCodeInput.isBlank()) {
                                    Toast.makeText(context, "請輸入菜單代碼", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                searchLoading = true
                                viewModel.findPlanByCode(searchCodeInput) { plan, error ->
                                    searchLoading = false
                                    if (plan != null) {
                                        Toast.makeText(context, "找到菜單！", Toast.LENGTH_SHORT).show()
                                        onNavigate(Screen.CalendarView(plan.id))
                                    } else {
                                        Toast.makeText(context, error ?: "找不到菜單", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !searchLoading,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(54.dp)
                        ) {
                            if (searchLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "搜尋")
                            }
                        }
                    }
                }
            }
        }

        // Local Cache Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "本機已建立菜單",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                // Small offline notification
                Text(
                    text = "支援離線編輯",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Local List Search query
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setPlanSearchQuery(it) },
                label = { Text("搜尋本機菜單 (名字、代碼...)") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_search_input"),
                shape = RoundedCornerShape(8.dp)
            )
        }

        if (plans.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "無資料",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("尚無菜單資料", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(plans, key = { it.id }) { plan ->
                MealPlanItemCard(
                    plan = plan,
                    onClick = { onNavigate(Screen.CalendarView(plan.id)) },
                    onDelete = { viewModel.deleteMealPlan(plan.id) },
                    onExportPdf = {
                        // PDF Trigger
                        scope.launch {
                            try {
                                val days = viewModel.getDaysForPlan(plan.id).first()
                                viewModel.exportPdf(context, plan, days) { file ->
                                    if (file != null) {
                                        sharePdfFile(context, file)
                                    } else {
                                        Toast.makeText(context, "PDF 產生失敗", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "讀取菜單內容中，請稍候再試...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    // New Plan Creation Dialog
    if (showCreateDialog) {
        var clientName by remember { mutableStateOf("") }
        var internalCode by remember { mutableStateOf("") }
        var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("新增 45 天菜單", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = clientName,
                        onValueChange = { clientName = it },
                        label = { Text("客戶名稱 / 使用者姓名 (必填)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = internalCode,
                        onValueChange = { internalCode = it },
                        label = { Text("內部代號 (選填)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Date Picker Trigger
                    Column {
                        Text("開始日期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable {
                                    val calendar = Calendar.getInstance()
                                    calendar.timeInMillis = selectedDate
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val outCalendar = Calendar.getInstance().apply {
                                                set(y, m, d, 0, 0, 0)
                                            }
                                            selectedDate = outCalendar.timeInMillis
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dateFormat.format(Date(selectedDate)))
                            Icon(Icons.Default.DateRange, "選擇日期")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (clientName.isBlank()) {
                                    Toast.makeText(context, "客戶姓名不能為空", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.createMealPlan(clientName, internalCode.ifBlank { null }, selectedDate, "client") { plan ->
                                    showCreateDialog = false
                                    Toast.makeText(context, "菜單建立成功！", Toast.LENGTH_SHORT).show()
                                    onNavigate(Screen.CalendarView(plan.id))
                                }
                            }
                        ) {
                            Text("確認建立")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealPlanItemCard(
    plan: MealPlan,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExportPdf: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("meal_plan_card_${plan.planCode}"),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (!plan.internalCode.isNullOrEmpty()) {
                        Text(
                            text = "內部編號: ${plan.internalCode}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sync status chip
                val (statusText, statusBg, statusColor) = when (plan.syncStatus) {
                    SyncStatus.SYNCED -> Triple("已同步", WellnessGreen.copy(alpha = 0.1f), WellnessGreen)
                    SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE -> Triple("待同步", WellnessGold.copy(alpha = 0.1f), WellnessAmber)
                    SyncStatus.CONFLICT -> Triple("衝突", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
                    else -> Triple("待刪除", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "代碼: ${plan.planCode}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${dateFormat.format(Date(plan.startDate))} ~ ${dateFormat.format(Date(plan.endDate))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row {
                    IconButton(onClick = onExportPdf) {
                        Icon(Icons.Default.PictureAsPdf, "匯出 PDF", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, "刪除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("確認刪除菜單") },
            text = { Text("您確定要刪除這份 45 天菜單嗎？此動作將同時清除本機及雲端的菜單紀錄且無法復原。") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ==================== SCREEN 2: BACK-END LOGIN ====================
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigate: (Screen) -> Unit
) {
    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()

    var phoneInput by remember { mutableStateOf("") }
    var smsInput by remember { mutableStateOf("") }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            Toast.makeText(context, "登入成功！", Toast.LENGTH_SHORT).show()
            onNavigate(Screen.BackHome)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AdminPanelSettings,
            contentDescription = "後台管理",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Text("登入後台管理系統", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(
            "限內部管理者與服務團隊登入，用以管理品項、菜單模板及設定使用者角色。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Emulation Helper
        Card(
            colors = CardDefaults.cardColors(containerColor = WellnessGold.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, WellnessGold.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("💡 快速測試模擬登入：", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WellnessAmber)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "輸入電話 0912345678 後點選「獲取驗證碼」，再輸入驗證碼 123456 即可快速以系統管理員權限登入後台體驗。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        OutlinedTextField(
            value = phoneInput,
            onValueChange = { phoneInput = it },
            label = { Text("電話號碼 (例如: +886912345678)") },
            placeholder = { Text("+886912345678") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("phone_input"),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Button(
            onClick = {
                if (phoneInput.isBlank()) {
                    Toast.makeText(context, "請輸入電話號碼", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                authViewModel.startPhoneAuth(phoneInput, context as Activity)
            },
            enabled = authState !is AuthState.Verifying,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("get_code_button")
        ) {
            Text("獲取簡訊驗證碼")
        }

        if (authState is AuthState.CodeSent || phoneInput == "0912345678" || phoneInput == "12345678") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = smsInput,
                onValueChange = { smsInput = it },
                label = { Text("輸入 6 位數驗證碼") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("code_input"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Button(
                onClick = {
                    authViewModel.verifyCode(smsInput)
                },
                enabled = authState !is AuthState.Verifying,
                colors = ButtonDefaults.buttonColors(containerColor = WellnessGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("verify_code_button")
            ) {
                Text("確認登入", fontWeight = FontWeight.Bold)
            }
        }

        if (authState is AuthState.Verifying) {
            CircularProgressIndicator()
        }

        if (authState is AuthState.Error) {
            Text(
                text = (authState as AuthState.Error).message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==================== SCREEN 3: BACK-END HOME ====================
@Composable
fun BackHomeScreen(
    viewModel: MainViewModel,
    authViewModel: AuthViewModel,
    onNavigate: (Screen) -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current

    // Make sure user role is authorized
    val currentRole = if (authState is AuthState.Success) {
        (authState as AuthState.Success).role
    } else {
        "viewer"
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = mutableListOf("品項管理", "日型模板", "菜單總覽")
    if (currentRole == "admin") {
        tabs.add("權限管理")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> MenuItemManagementTab(viewModel, currentRole)
                1 -> DayTypeTemplateManagementTab(viewModel, currentRole)
                2 -> GlobalMealPlansTab(viewModel, currentRole, onNavigate)
                3 -> if (currentRole == "admin") UserRoleManagementTab()
            }
        }
    }
}

@Composable
fun MenuItemManagementTab(viewModel: MainViewModel, role: String) {
    val items by viewModel.filteredMenuItems.collectAsState()
    val query by viewModel.menuSearchQuery.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<MenuItem?>(null) }

    val canEdit = role == "admin" || role == "staff"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("品項數據庫 (${items.size} 個)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (canEdit) {
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, "新增")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新增品項")
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setMenuSearchQuery(it) },
            placeholder = { Text("搜尋品項名稱...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "包裝價格: $${item.packPrice.toInt()} 元 / ${item.packSize}${item.packageUnit} | 食用單位: ${item.unit}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (canEdit) {
                            Row {
                                IconButton(onClick = { editItem = item }) {
                                    Icon(Icons.Default.Edit, "編輯", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { viewModel.deleteMenuItem(item.id) }) {
                                    Icon(Icons.Default.Delete, "刪除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Item Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var priceStr by remember { mutableStateOf("") }
        var packSizeStr by remember { mutableStateOf("") }
        var packUnit by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("新增菜單品項", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("品項名稱") })
                    OutlinedTextField(value = priceStr, onValueChange = { priceStr = it }, label = { Text("整包裝價格 (元)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = packSizeStr, onValueChange = { packSizeStr = it }, label = { Text("整包裝份量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = packUnit, onValueChange = { packUnit = it }, label = { Text("整包裝單位 (如: 罐, 盒)") })
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("單次份量單位 (如: 匙, 包)") })

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddDialog = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val price = priceStr.toDoubleOrNull() ?: 0.0
                            val size = packSizeStr.toIntOrNull() ?: 0
                            if (name.isBlank() || price <= 0 || size <= 0 || packUnit.isBlank() || unit.isBlank()) {
                                Toast.makeText(context, "請完整填寫正確欄位格式", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.addMenuItem(name, price, size, packUnit, unit)
                            showAddDialog = false
                        }) {
                            Text("新增")
                        }
                    }
                }
            }
        }
    }

    // Edit Item Dialog
    if (editItem != null) {
        var name by remember { mutableStateOf(editItem!!.name) }
        var priceStr by remember { mutableStateOf(editItem!!.packPrice.toInt().toString()) }
        var packSizeStr by remember { mutableStateOf(editItem!!.packSize.toString()) }
        var packUnit by remember { mutableStateOf(editItem!!.packageUnit) }
        var unit by remember { mutableStateOf(editItem!!.unit) }

        Dialog(onDismissRequest = { editItem = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("編輯菜單品項", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("品項名稱") })
                    OutlinedTextField(value = priceStr, onValueChange = { priceStr = it }, label = { Text("整包裝價格 (元)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = packSizeStr, onValueChange = { packSizeStr = it }, label = { Text("整包裝份量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = packUnit, onValueChange = { packUnit = it }, label = { Text("整包裝單位") })
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("單次份量單位") })

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editItem = null }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val price = priceStr.toDoubleOrNull() ?: 0.0
                            val size = packSizeStr.toIntOrNull() ?: 0
                            if (name.isBlank() || price <= 0 || size <= 0 || packUnit.isBlank() || unit.isBlank()) {
                                Toast.makeText(context, "請填寫正確格式", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.updateMenuItem(editItem!!.copy(
                                name = name,
                                packPrice = price,
                                packSize = size,
                                packageUnit = packUnit,
                                unit = unit
                            ))
                            editItem = null
                        }) {
                            Text("保存修改")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayTypeTemplateManagementTab(viewModel: MainViewModel, role: String) {
    val templates by viewModel.dayTypeTemplates.collectAsState()
    var editingTemplate by remember { mutableStateOf<DayTypeTemplate?>(null) }
    val canEdit = role == "admin" || role == "staff"

    if (editingTemplate != null) {
        TemplateEditorView(
            viewModel = viewModel,
            template = editingTemplate!!,
            onClose = { editingTemplate = null },
            canEdit = canEdit
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("後台日型固定模板管理", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "此模板用於前台套用客製。模板異動時，已套用的現有菜單不受影響，使用者手動重新套用除外。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(templates) { tpl ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingTemplate = tpl },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tpl.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(Icons.Default.Edit, "編輯", tint = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "預設餐次: ${tpl.meals.joinToString(", ") { "${it.title}(${it.time})" }}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorView(
    viewModel: MainViewModel,
    template: DayTypeTemplate,
    onClose: () -> Unit,
    canEdit: Boolean
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(template.name) }
    var mealsList by remember { mutableStateOf(template.meals) }

    var showAddMealDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${name} 模板編輯") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (canEdit) {
                        Button(onClick = {
                            viewModel.updateTemplate(template.copy(name = name, meals = mealsList))
                            Toast.makeText(context, "模板更新成功", Toast.LENGTH_SHORT).show()
                            onClose()
                        }) {
                            Text("儲存模板")
                        }
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (canEdit) name = it },
                    label = { Text("模板名稱") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canEdit
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("餐次時間表", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (canEdit) {
                        IconButton(onClick = { showAddMealDialog = true }) {
                            Icon(Icons.Default.AddCircle, "新增餐次", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }

            items(mealsList) { meal ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${meal.title} [時間: ${meal.time}]", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (canEdit) {
                                IconButton(onClick = {
                                    mealsList = mealsList.filter { it.id != meal.id }
                                }) {
                                    Icon(Icons.Default.Delete, "刪除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (meal.note.isNotEmpty()) {
                            Text("備註: ${meal.note}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Meal Items
                        Spacer(modifier = Modifier.height(4.dp))
                        meal.items.forEach { itm ->
                            Text(
                                text = "• ${itm.menuItemName} x${itm.quantity}${itm.unit} ${if (itm.note.isNotEmpty()) "(${itm.note})" else ""}",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddMealDialog) {
        var mealTitle by remember { mutableStateOf("") }
        var mealTime by remember { mutableStateOf("") }
        var mealNote by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddMealDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("新增餐次項目", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(value = mealTitle, onValueChange = { mealTitle = it }, label = { Text("餐次標題 (如: 早餐, 晚餐)") })
                    OutlinedTextField(value = mealTime, onValueChange = { mealTime = it }, label = { Text("餐次時間 (如: 08:30, 睡前)") })
                    OutlinedTextField(value = mealNote, onValueChange = { mealNote = it }, label = { Text("提醒備註") })

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddMealDialog = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (mealTitle.isBlank() || mealTime.isBlank()) {
                                Toast.makeText(context, "請填寫餐次及時間", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val newMeal = Meal(
                                id = java.util.UUID.randomUUID().toString(),
                                time = mealTime,
                                title = mealTitle,
                                note = mealNote,
                                items = emptyList() // Will add menuItems during client-specific customize
                            )
                            mealsList = mealsList + newMeal
                            showAddMealDialog = false
                        }) {
                            Text("新增")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalMealPlansTab(viewModel: MainViewModel, role: String, onNavigate: (Screen) -> Unit) {
    val plans by viewModel.filteredMealPlans.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("全域客戶 45 天菜單總覽 (後台管理模式)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(plans) { plan ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(Screen.CalendarView(plan.id)) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(plan.customerName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("planCode: ${plan.planCode}", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    val days = viewModel.getDaysForPlan(plan.id).first()
                                    viewModel.exportPdf(context, plan, days) { file ->
                                        if (file != null) sharePdfFile(context, file)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "讀取菜單內容中，請稍候再試...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.PictureAsPdf, "PDF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserRoleManagementTab() {
    // Standard visual dashboard for administrative role management
    val users = remember {
        mutableStateListOf(
            UserDemo("u1", "0912345678", "系統管理員", "admin", true),
            UserDemo("u2", "0900111222", "測試團隊 A", "staff", true),
            UserDemo("u3", "0922333444", "訪客人員 B", "viewer", true)
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("內部帳號與角色權限控管", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("僅限 admin (系統管理員) 變更使用者權限級別。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(users) { usr ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(usr.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${usr.phone} | 角色: ${usr.role.uppercase()}", fontSize = 12.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Cycle Role
                            TextButton(onClick = {
                                val index = users.indexOf(usr)
                                val nextRole = when (usr.role) {
                                    "admin" -> "staff"
                                    "staff" -> "viewer"
                                    else -> "admin"
                                }
                                users[index] = usr.copy(role = nextRole)
                            }) {
                                Text("切換權限")
                            }

                            Switch(
                                checked = usr.enabled,
                                onCheckedChange = { chk ->
                                    val index = users.indexOf(usr)
                                    users[index] = usr.copy(enabled = chk)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class UserDemo(val uid: String, val phone: String, val displayName: String, val role: String, val enabled: Boolean)

// ==================== SCREEN 4: CALENDAR / 45-DAY MATRIX SCREEN ====================
@Composable
fun CalendarScreen(
    viewModel: MainViewModel,
    planId: String,
    onNavigate: (Screen) -> Unit
) {
    val context = LocalContext.current
    val plan by viewModel.getMealPlanFlow(planId).collectAsState(null)
    val days by viewModel.getDaysForPlan(planId).collectAsState(emptyList())
    val templates by viewModel.dayTypeTemplates.collectAsState()

    var batchMode by remember { mutableStateOf(false) }
    val selectedDays = remember { mutableStateListOf<Int>() }

    var showTemplateSelector by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    val reminderEnabled = remember(planId) {
        mutableStateOf(viewModel.isNotificationEnabled(context, planId))
    }

    if (plan == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Info Box
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "${plan!!.customerName} 的 45 天計畫",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "分享代碼 (planCode): ${plan!!.planCode}",
                    fontWeight = FontWeight.SemiBold,
                    color = WellnessAmber,
                    fontSize = 13.sp
                )
                Text(
                    text = "期間: ${dateFormat.format(Date(plan!!.startDate))} ~ ${dateFormat.format(Date(plan!!.endDate))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Row of quick tools: PDF week print and exact reminders switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Notifications Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (reminderEnabled.value) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    contentDescription = "提醒通知",
                    tint = if (reminderEnabled.value) WellnessGreen else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("啟用用餐時間提醒", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = reminderEnabled.value,
                    onCheckedChange = { enabled ->
                        reminderEnabled.value = enabled
                        viewModel.setNotificationEnabled(context, planId, enabled)
                        Toast.makeText(
                            context,
                            if (enabled) "本機定時餐次提醒已建立" else "提醒通知已全部取消",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            // PDF Share Button
            Button(
                onClick = {
                    viewModel.exportPdf(context, plan!!, days) { file ->
                        if (file != null) sharePdfFile(context, file)
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, "PDF")
                Spacer(modifier = Modifier.width(4.dp))
                Text("匯出 PDF 週表")
            }
        }

        // Multi-select batch mode bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = batchMode,
                    onCheckedChange = { chk ->
                        batchMode = chk ?: false
                        if (!batchMode) selectedDays.clear()
                    }
                )
                Text("批次套用日型模板", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            if (batchMode && selectedDays.isNotEmpty()) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = WellnessGreen),
                    onClick = { showTemplateSelector = true }
                ) {
                    Text("套用模板 (${selectedDays.size} 天)")
                }
            }
        }

        // 45 Days Grid
        Text("點擊單日可調整細部菜單及添加特製餐次：", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(5), // 5 columns is beautiful for vertical mobile screens
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(days) { day ->
                val isSelected = selectedDays.contains(day.dayIndex)
                
                // Color mapping for 4 Day types
                val (chipColor, chipBg) = when (day.dayType) {
                    DayType.PREPARATION -> Pair(WellnessGreen, WellnessGreen.copy(alpha = 0.12f))
                    DayType.PROTEIN -> Pair(WellnessAmber, WellnessAmber.copy(alpha = 0.12f))
                    DayType.SLIMMING -> Pair(WellnessGold, WellnessGold.copy(alpha = 0.15f))
                    DayType.METABOLISM -> Pair(Color(0xFF3F51B5), Color(0xFF3F51B5).copy(alpha = 0.1f))
                }

                Box(
                    modifier = Modifier
                        .aspectRatio(0.9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else chipBg)
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else chipColor.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (batchMode) {
                                if (isSelected) selectedDays.remove(day.dayIndex) else selectedDays.add(day.dayIndex)
                            } else {
                                onNavigate(Screen.DayDetail(planId, day.dayIndex))
                            }
                        }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "第 ${day.dayIndex} 天",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day.dayTypeName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = chipColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${day.meals.size} 餐次",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Batch Template Application Selector Dialog
    if (showTemplateSelector) {
        Dialog(onDismissRequest = { showTemplateSelector = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("選擇套用之日型模板", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Text("此動作將覆蓋已選取的 ${selectedDays.size} 天既有客製菜單餐次：", fontSize = 12.sp)

                    templates.forEach { tpl ->
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.batchApplyDayTemplate(planId, selectedDays, tpl)
                                viewModel.refreshAlarmsForPlan(context, planId) // Re-schedule notifications
                                Toast.makeText(context, "批量套用完成", Toast.LENGTH_SHORT).show()
                                showTemplateSelector = false
                                batchMode = false
                                selectedDays.clear()
                            }
                        ) {
                            Text(tpl.name)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTemplateSelector = false }) { Text("取消") }
                    }
                }
            }
        }
    }
}

// ==================== SCREEN 5: SINGLE DAY EDIT / DETAIL SCREEN ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    viewModel: MainViewModel,
    planId: String,
    dayIndex: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val days by viewModel.getDaysForPlan(planId).collectAsState(emptyList())
    val menuItems by viewModel.menuItems.collectAsState()
    val templates by viewModel.dayTypeTemplates.collectAsState()

    val day = days.firstOrNull { it.dayIndex == dayIndex }
    
    var showAddMealDialog by remember { mutableStateOf(false) }
    var activeAddProductToMeal by remember { mutableStateOf<Meal?>(null) }

    if (day == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back header and Day metadata
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "第 $dayIndex 天菜單排程",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "目前日型: ${day.dayTypeName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Template Quick reset
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("重設此日為標準模板：", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        templates.forEach { tpl ->
                            Button(
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                onClick = {
                                    viewModel.updateMealPlanDay(day.copy(
                                        dayType = DayType.valueOf(tpl.id),
                                        dayTypeName = tpl.name,
                                        meals = tpl.meals,
                                        lastModifiedAt = System.currentTimeMillis()
                                    ))
                                    viewModel.refreshAlarmsForPlan(context, planId) // Re-schedule
                                    Toast.makeText(context, "重設成功", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(tpl.name, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Header and add meal button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("此日餐次清單", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                Button(
                    shape = RoundedCornerShape(8.dp),
                    onClick = { showAddMealDialog = true }
                ) {
                    Icon(Icons.Default.Add, "新增餐次")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新餐次")
                }
            }
        }

        // Meal Rows
        if (day.meals.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("本期無餐次規劃，點擊右上角新增餐次", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(day.meals) { meal ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${meal.title} [時間: ${meal.time}]",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = {
                                // Delete meal
                                val updatedMeals = day.meals.filter { it.id != meal.id }
                                viewModel.updateMealPlanDay(day.copy(meals = updatedMeals))
                                viewModel.refreshAlarmsForPlan(context, planId)
                            }) {
                                Icon(Icons.Default.Delete, "刪除餐次", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        if (meal.note.isNotEmpty()) {
                            Text("提醒備註: ${meal.note}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Meal Products
                        Text("搭配品項：", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        meal.items.forEach { itm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• ${itm.menuItemName} x${itm.quantity} ${itm.unit} ${if (itm.note.isNotEmpty()) "(${itm.note})" else ""}",
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        // Remove product from meal
                                        val updatedItems = meal.items.filter { it.menuItemId != itm.menuItemId }
                                        val updatedMeals = day.meals.map {
                                            if (it.id == meal.id) it.copy(items = updatedItems) else it
                                        }
                                        viewModel.updateMealPlanDay(day.copy(meals = updatedMeals))
                                        viewModel.refreshAlarmsForPlan(context, planId)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, "移除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Add Product Trigger Button
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { activeAddProductToMeal = meal },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, "新增搭配品項")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("搭配新膳食品項")
                        }
                    }
                }
            }
        }
    }

    // Add Meal Dialog
    if (showAddMealDialog) {
        var mTitle by remember { mutableStateOf("") }
        var mTime by remember { mutableStateOf("") }
        var mNote by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddMealDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("新增此日餐次", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(value = mTitle, onValueChange = { mTitle = it }, label = { Text("餐次名稱 (例如: 點心餐)") })
                    OutlinedTextField(value = mTime, onValueChange = { mTime = it }, label = { Text("提醒時間 (如: 15:30 或 睡前)") })
                    OutlinedTextField(value = mNote, onValueChange = { mNote = it }, label = { Text("餐前提醒備註") })

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddMealDialog = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (mTitle.isBlank() || mTime.isBlank()) {
                                Toast.makeText(context, "請填寫餐次名稱及時間", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val newMeal = Meal(
                                id = java.util.UUID.randomUUID().toString(),
                                time = mTime,
                                title = mTitle,
                                note = mNote,
                                items = emptyList()
                            )
                            viewModel.updateMealPlanDay(day.copy(meals = day.meals + newMeal))
                            viewModel.refreshAlarmsForPlan(context, planId)
                            showAddMealDialog = false
                        }) {
                            Text("確認新增")
                        }
                    }
                }
            }
        }
    }

    // Add Product to Meal Dialog
    if (activeAddProductToMeal != null) {
        var selectedItemIndex by remember { mutableStateOf(0) }
        var quantityStr by remember { mutableStateOf("1.0") }
        var itemNote by remember { mutableStateOf("") }
        var showDropdown by remember { mutableStateOf(false) }

        val activeMeal = activeAddProductToMeal!!

        Dialog(onDismissRequest = { activeAddProductToMeal = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("選擇餐次搭配品項", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    if (menuItems.isEmpty()) {
                        Text("品項資料庫為空，請先至後台新增品項。")
                    } else {
                        val currentItem = menuItems[selectedItemIndex]
                        
                        // Pseudo custom dropdown selector
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable { showDropdown = true }
                                .padding(14.dp)
                        ) {
                            Text(currentItem.name)
                            DropdownMenu(
                                expanded = showDropdown,
                                onDismissRequest = { showDropdown = false }
                            ) {
                                menuItems.forEachIndexed { idx, item ->
                                    DropdownMenuItem(
                                        text = { Text(item.name) },
                                        onClick = {
                                            selectedItemIndex = idx
                                            showDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it },
                            label = { Text("攝取份量 (單位: ${currentItem.unit})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = itemNote,
                            onValueChange = { itemNote = it },
                            label = { Text("搭配備註 (如: 沖水 200cc)") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { activeAddProductToMeal = null }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = menuItems.isNotEmpty(),
                            onClick = {
                                val item = menuItems[selectedItemIndex]
                                val qty = quantityStr.toDoubleOrNull() ?: 1.0
                                val newItem = MealItem(
                                    menuItemId = item.id,
                                    menuItemName = item.name,
                                    quantity = qty,
                                    unit = item.unit,
                                    note = itemNote
                                )

                                val updatedMeals = day.meals.map { m ->
                                    if (m.id == activeMeal.id) {
                                        // Avoid duplicate items, or append
                                        m.copy(items = m.items + newItem)
                                    } else m
                                }

                                viewModel.updateMealPlanDay(day.copy(meals = updatedMeals))
                                viewModel.refreshAlarmsForPlan(context, planId)
                                activeAddProductToMeal = null
                            }
                        ) {
                            Text("添加搭配")
                        }
                    }
                }
            }
        }
    }
}

// PDF Sharing action helper
private fun sharePdfFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 45 天菜單 PDF 週表"))
    } catch (e: Exception) {
        Log.e("DayDetailScreen", "Failed to share PDF", e)
        Toast.makeText(context, "無法開啟分享對話框: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
