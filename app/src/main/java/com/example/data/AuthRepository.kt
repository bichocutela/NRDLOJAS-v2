package com.example.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    val isConfigured: Boolean
        get() = try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            false
        }

    suspend fun login(email: String, pass: String): AuthResult {
        if (!isConfigured) return AuthResult.Error("Firebase indisponível (não configurado).")
        
        return try {
            _authState.value = AuthState.Loading
            android.util.Log.d("LoginDebug", "Iniciando Firebase login para: $email")
            val firebaseApp = FirebaseApp.getInstance()
            android.util.Log.d("LoginDebug", "Firebase ProjectId: ${firebaseApp.options.projectId}")
            android.util.Log.d("LoginDebug", "Firebase ApplicationId: ${firebaseApp.options.applicationId}")
            val auth = FirebaseAuth.getInstance()
            android.util.Log.d("LoginDebug", "FirebaseAuth App Name: ${auth.app.name}")
            val result = kotlinx.coroutines.withTimeout(15000L) {
                auth.signInWithEmailAndPassword(email, pass).await()
            }
            val authenticatedEmail = result.user?.email ?: ""
            android.util.Log.d("LoginDebug", "Firebase login sucesso. currentUser email: $authenticatedEmail")
            _authState.value = AuthState.Authenticated(authenticatedEmail)
            AuthResult.Success(authenticatedEmail)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.e("LoginDebug", "Erro: Timeout no login (demorou muito)", e)
            _authState.value = AuthState.Unauthenticated
            AuthResult.Error("O Firebase demorou demais para responder.")
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            android.util.Log.e("LoginDebug", "Erro de rede real (FirebaseNetworkException): ${e.message}", e)
            _authState.value = AuthState.Unauthenticated
            AuthResult.Error("sem internet")
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            android.util.Log.e("LoginDebug", "Erro credentials (FirebaseAuthInvalidCredentialsException): ${e.message}", e)
            _authState.value = AuthState.Unauthenticated
            AuthResult.Error("senha incorreta")
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            android.util.Log.e("LoginDebug", "Erro user (FirebaseAuthInvalidUserException): ${e.message}", e)
            _authState.value = AuthState.Unauthenticated
            AuthResult.Error("usuário inexistente")
        } catch (e: com.google.firebase.FirebaseTooManyRequestsException) {
            android.util.Log.e("LoginDebug", "Muitas tentativas (FirebaseTooManyRequestsException): ${e.message}", e)
            _authState.value = AuthState.Unauthenticated
            AuthResult.Error("muitas tentativas, tente novamente mais tarde")
        } catch (e: com.google.firebase.FirebaseException) {
            android.util.Log.e("LoginDebug", "Erro do Firebase (FirebaseException): ${e.message}", e)
            _authState.value = AuthState.Unauthenticated
            AuthResult.Error(e.message ?: "Erro no Firebase")
        } catch (e: Exception) {
            android.util.Log.e("LoginDebug", "Erro geral/desconhecido: ${e.javaClass.simpleName} - ${e.message}", e)
            val msg = e.message?.lowercase() ?: ""
            val erroMsg = when {
                msg.contains("password") || msg.contains("credential") -> "senha incorreta"
                msg.contains("no user") || msg.contains("not found") -> "usuário inexistente"
                msg.contains("network") || msg.contains("host") -> "sem internet"
                msg.contains("format") || msg.contains("badly") -> "formato de usuário inválido"
                else -> "erro técnico: ${e.message}"
            }
            _authState.value = AuthState.Unauthenticated
            AuthResult.Error(erroMsg)
        } finally {
            if (_authState.value == AuthState.Loading) {
                 _authState.value = AuthState.Unauthenticated
            }
        }
    }
    
    suspend fun createAccount(email: String, pass: String): AuthResult {
         if (!isConfigured) return AuthResult.Error("Firebase não configurado.")
         
         return try {
            _authState.value = AuthState.Loading
            val auth = FirebaseAuth.getInstance()
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            _authState.value = AuthState.Authenticated(result.user?.email ?: "")
            AuthResult.Success(result.user?.email ?: "")
        } catch (e: Exception) {
            _authState.value = AuthState.Unauthenticated
            AuthResult.Error(e.message ?: "Erro desconhecido ao criar conta")
        }
    }

    fun logout() {
        if (isConfigured) {
            FirebaseAuth.getInstance().signOut()
        }
        _authState.value = AuthState.Unauthenticated
    }
}

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    data class Authenticated(val email: String) : AuthState()
    object Unauthenticated : AuthState()
}

sealed class AuthResult {
    data class Success(val email: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
