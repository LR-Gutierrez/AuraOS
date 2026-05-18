package com.beta.auraos.ui.login

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.data.LoginDataSource
import com.beta.auraos.data.LoginRepository
import com.beta.auraos.data.local.SessionManager

/**
 * Manual dependency injection for [LoginViewModel].
 *
 * Wires the full object graph: `SessionManager → LoginDataSource →
 * LoginRepository → LoginViewModel`. Manual DI keeps the project lean;
 * if dependencies grow consider migrating to Hilt.
 */
class LoginViewModelFactory(private val application: Application) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            val sessionManager = SessionManager(application)
            val dataSource = LoginDataSource(sessionManager)
            val repository = LoginRepository(dataSource, sessionManager)
            return LoginViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}