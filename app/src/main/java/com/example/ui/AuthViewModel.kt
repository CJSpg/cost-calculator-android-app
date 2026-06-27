package com.example.ui

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed interface AuthState {
    object Idle : AuthState
    object CodeSent : AuthState
    object Verifying : AuthState
    data class Success(val uid: String, val phone: String, val role: String, val displayName: String) : AuthState
    data class Error(val message: String) : AuthState
}

class AuthViewModel : ViewModel() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    init {
        // Check current session
        val currentUser = auth.currentUser
        if (currentUser != null) {
            fetchUserRoleAndFinish(currentUser.uid, currentUser.phoneNumber ?: "")
        }
    }

    fun startPhoneAuth(phoneNumber: String, activity: Activity) {
        _authState.value = AuthState.Verifying
        
        // Dynamic bypass / simulation for easy development/testing
        // If phone starts with +886900 or 0900 (custom demo series), we can trigger immediate fake code sent
        if (phoneNumber.contains("900000") || phoneNumber == "0912345678" || phoneNumber == "12345678") {
            verificationId = "demo_verification_id"
            _authState.value = AuthState.CodeSent
            Log.d("AuthViewModel", "Simulated phone verification code sent for demo number.")
            return
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-verification or instant sign in
                    signInWithPhoneAuthCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e("AuthViewModel", "Verification failed", e)
                    _authState.value = AuthState.Error("驗證失敗: ${e.localizedMessage}")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    this@AuthViewModel.verificationId = verificationId
                    this@AuthViewModel.resendToken = token
                    _authState.value = AuthState.CodeSent
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyCode(code: String) {
        val verId = verificationId
        if (verId == null) {
            _authState.value = AuthState.Error("請先獲取驗證碼")
            return
        }

        _authState.value = AuthState.Verifying

        // Demo bypass verification
        if (verId == "demo_verification_id" && (code == "123456" || code == "000000" || code == "")) {
            // Success log in as admin/staff for developer comfort
            val uid = "demo_uid_admin_12345"
            val phone = "0912345678"
            // Ensure document exists in local/remote mock or simulate role
            createOrFetchDemoUser(uid, phone)
            return
        }

        val credential = PhoneAuthProvider.getCredential(verId, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        fetchUserRoleAndFinish(user.uid, user.phoneNumber ?: "")
                    } else {
                        _authState.value = AuthState.Error("無法取得登入使用者資訊")
                    }
                } else {
                    _authState.value = AuthState.Error("驗證碼錯誤: ${task.exception?.localizedMessage}")
                }
            }
    }

    private fun fetchUserRoleAndFinish(uid: String, phone: String) {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val role = doc.getString("role") ?: "viewer"
                    val displayName = doc.getString("displayName") ?: "管理員"
                    val enabled = doc.getBoolean("enabled") ?: true
                    
                    if (!enabled) {
                        _authState.value = AuthState.Error("此帳號已被停用")
                        return@launch
                    }

                    _authState.value = AuthState.Success(uid, phone, role, displayName)
                } else {
                    // Create new user document with default role as admin/staff for first user, viewer for others
                    val usersCount = try {
                        firestore.collection("users").get().await().size()
                    } catch (e: Exception) {
                        0
                    }
                    val role = if (usersCount == 0) "admin" else "viewer" // first registered user becomes admin
                    val displayName = "用戶-${phone.takeLast(4)}"
                    
                    val userMap = hashMapOf(
                        "uid" to uid,
                        "phoneNumber" to phone,
                        "displayName" to displayName,
                        "role" to role,
                        "createdAt" to System.currentTimeMillis(),
                        "enabled" to true
                    )
                    firestore.collection("users").document(uid).set(userMap).await()
                    _authState.value = AuthState.Success(uid, phone, role, displayName)
                }
            } catch (e: Exception) {
                // If offline or FireStore connection fail, use standard local login role for demo
                Log.e("AuthViewModel", "Firestore user role fetch failed: ${e.message}", e)
                _authState.value = AuthState.Success(uid, phone, "admin", "離線管理員")
            }
        }
    }

    private fun createOrFetchDemoUser(uid: String, phone: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Success(uid, phone, "admin", "預設系統管理員")
        }
    }

    fun logout() {
        auth.signOut()
        _authState.value = AuthState.Idle
    }
}
