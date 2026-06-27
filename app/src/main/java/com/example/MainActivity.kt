package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.MenuRepository
import com.example.notification.MealNotificationManager
import com.example.ui.AppNavigation
import com.example.ui.AuthViewModel
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.ViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var authViewModel: AuthViewModel

    // Backstack for custom dynamic navigation
    private val backstack = mutableStateListOf<Screen>(Screen.FrontHome)

    // Dynamic screen navigation state
    private var currentScreen by mutableStateOf<Screen>(Screen.FrontHome)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "通知權限已啟用", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "通知權限已被拒絕。如需用餐提醒，請手動前往系統設定開啟通知。", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize Firebase
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                val resId = resources.getIdentifier("google_app_id", "string", packageName)
                if (resId == 0) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:1234567890:android:abcdef123456")
                        .setApiKey("AIzaSyFakeKeyPlaceholder")
                        .setProjectId("fake-project-id")
                        .build()
                    com.google.firebase.FirebaseApp.initializeApp(this, options)
                } else {
                    com.google.firebase.FirebaseApp.initializeApp(this)
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // 2. Initialize Room Database & Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = MenuRepository(
            context = applicationContext,
            menuItemDao = database.menuItemDao(),
            dayTypeTemplateDao = database.dayTypeTemplateDao(),
            mealPlanDao = database.mealPlanDao(),
            mealPlanDayDao = database.mealPlanDayDao()
        )

        // 3. Instantiate ViewModels
        mainViewModel = ViewModelProvider(this, ViewModelFactory(repository))[MainViewModel::class.java]
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        // 4. Setup meal notification channels
        MealNotificationManager.createNotificationChannel(this)

        // 5. Request dynamic POST_NOTIFICATIONS permission on Android 13+
        requestNotificationPermission()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        mainViewModel = mainViewModel,
                        authViewModel = authViewModel,
                        currentScreen = currentScreen,
                        onNavigate = { nextScreen ->
                            // Simple push navigation
                            backstack.add(nextScreen)
                            currentScreen = nextScreen
                        },
                        onBack = {
                            if (backstack.size > 1) {
                                backstack.removeAt(backstack.lastIndex)
                                currentScreen = backstack.last()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
