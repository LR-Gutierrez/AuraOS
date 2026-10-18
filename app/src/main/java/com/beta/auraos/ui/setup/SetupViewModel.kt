package com.beta.auraos.ui.setup

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.R
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.CreateBranchInput
import com.beta.auraos.data.model.LoggedInUser
import com.beta.auraos.ui.login.LoggedInUserView
import com.beta.auraos.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** What the operator typed in the no-server setup. Numbers arrive as text and are parsed here. */
data class SetupInput(
    val name: String,
    val email: String,
    val password: String,
    val confirm: String,
    val branchName: String,
    val lightCapacity: String,
    val heavyCapacity: String,
    val motoCapacity: String,
    val lightRate: String,
    val heavyRate: String,
    val motoRate: String,
)

/** Field errors as string resources; null means the field is fine. */
data class SetupErrors(
    val name: Int? = null,
    val email: Int? = null,
    val password: Int? = null,
    val confirm: Int? = null,
    val branchName: Int? = null,
    val capacity: Int? = null,
    val rates: Int? = null,
) {
    val isValid: Boolean
        get() = listOf(name, email, password, confirm, branchName, capacity, rates).all { it == null }
}

sealed class SetupState {
    data object Idle : SetupState()
    data object Saving : SetupState()
    data class Invalid(val errors: SetupErrors) : SetupState()
    data class Done(val user: LoggedInUserView, val password: String) : SetupState()
    data class Failed(val message: String) : SetupState()
}

/**
 * Creates the device's first account and branch without a backend. The account lives in the
 * same local store the offline login already uses (PBKDF2 hash in encrypted prefs) and is
 * flagged local-only until the backend accepts a login for it; the branch is journaled so it
 * syncs once a server session exists.
 */
class SetupViewModel(
    private val sessionManager: SessionManager,
    private val branchRepository: BranchRepository,
) : ViewModel() {

    private val _state = MutableLiveData<SetupState>(SetupState.Idle)
    val state: LiveData<SetupState> = _state

    fun validate(input: SetupInput): SetupErrors {
        val capacities = listOf(input.lightCapacity, input.heavyCapacity, input.motoCapacity)
            .map { it.trim().ifEmpty { "0" }.toIntOrNull() }
        val rates = listOf(input.lightRate, input.heavyRate, input.motoRate)
            .map { it.trim().replace(',', '.').ifEmpty { "0" }.toDoubleOrNull() }
        return SetupErrors(
            name = if (input.name.isBlank()) R.string.setup_error_required else null,
            email = if (!Patterns.EMAIL_ADDRESS.matcher(input.email.trim()).matches()) R.string.setup_error_email else null,
            password = if (input.password.length < MIN_PASSWORD) R.string.setup_error_password else null,
            confirm = if (input.confirm != input.password) R.string.setup_error_confirm else null,
            branchName = if (input.branchName.isBlank()) R.string.setup_error_required else null,
            capacity = if (capacities.any { it == null || it < 0 } || capacities.sumOf { it ?: 0 } == 0) {
                R.string.setup_error_capacity
            } else null,
            rates = if (rates.any { it == null || it < 0 }) R.string.setup_error_rates else null,
        )
    }

    fun create(input: SetupInput) {
        if (_state.value is SetupState.Saving) return
        // Only an empty device may be set up this way: never overwrite an existing account.
        if (!sessionManager.isDeviceEmpty()) {
            _state.value = SetupState.Failed("device_not_empty")
            return
        }
        val errors = validate(input)
        if (!errors.isValid) {
            _state.value = SetupState.Invalid(errors)
            return
        }
        _state.value = SetupState.Saving
        viewModelScope.launch {
            val user = LoggedInUser(
                userId = "local-" + UUID.randomUUID(),
                displayName = input.name.trim(),
                email = input.email.trim().lowercase(),
                avatarUrl = null,
            )
            val branchResult = withContext(Dispatchers.IO) {
                sessionManager.saveLocalUserProfile(user)
                sessionManager.saveLocalPasswordHash(PasswordHasher.hash(input.password))
                sessionManager.setLocalOnlyAccount(true)
                sessionManager.saveUserId(user.userId)
                sessionManager.saveLastEmail(user.email)
                sessionManager.saveLastDisplayName(user.displayName)
                branchRepository.createBranch(branchInput(input), localOnly = true)
            }
            _state.value = when (branchResult) {
                is Result.Success -> {
                    sessionManager.saveSelectedBranchId(branchResult.data.id)
                    SetupState.Done(
                        LoggedInUserView(
                            displayName = user.displayName,
                            email = user.email,
                            userId = user.userId,
                            avatarUrl = null,
                        ),
                        input.password,
                    )
                }
                is Result.Error -> SetupState.Failed(branchResult.exception.message ?: "")
            }
        }
    }

    private fun branchInput(input: SetupInput): CreateBranchInput {
        fun int(s: String) = s.trim().ifEmpty { "0" }.toInt()
        fun rate(s: String) = s.trim().replace(',', '.').ifEmpty { "0" }.toDouble()
        return CreateBranchInput(
            name = input.branchName.trim(),
            address = null,
            motorcycleCapacity = int(input.motoCapacity),
            lightVehicleCapacity = int(input.lightCapacity),
            heavyVehicleCapacity = int(input.heavyCapacity),
            motorcycleRate = rate(input.motoRate),
            lightVehicleRate = rate(input.lightRate),
            heavyVehicleRate = rate(input.heavyRate),
            currency = "USD",
        )
    }

    companion object {
        const val MIN_PASSWORD = 6
    }
}
