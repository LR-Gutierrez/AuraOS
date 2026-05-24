package com.beta.auraos.ui.memberships

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.R
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.CreateMembershipInput
import com.beta.auraos.databinding.ActivityMembershipFormBinding

class MembershipFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMembershipFormBinding
    private lateinit var viewModel: MembershipViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        val repository = MembershipRepository()
        val factory = MembershipViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MembershipViewModel::class.java]

        binding = ActivityMembershipFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        binding.btnBack.setOnClickListener { finish() }

        viewModel.formState.observe(this) { state ->
            when (state) {
                is MembershipFormUiState.Saving -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is MembershipFormUiState.Saved -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, R.string.membership_create_success, Toast.LENGTH_SHORT).show()
                    finish()
                }
                is MembershipFormUiState.Error -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is MembershipFormUiState.Idle -> {
                    binding.loadingOverlay.visibility = View.GONE
                }
            }
        }

        binding.btnSave.setOnClickListener {
            saveMembership()
        }
    }

    private fun saveMembership() {
        val name = binding.etMemberName.text.toString().trim()
        val tier = binding.etTier.text.toString().trim()
        val endDate = binding.etEndDate.text.toString().trim()
        val branchId = sessionManager.getSelectedBranchId()

        if (name.isEmpty()) {
            binding.etMemberName.error = getString(R.string.field_required)
            binding.etMemberName.requestFocus()
            return
        }

        if (branchId == null) {
            Toast.makeText(this, "Selecciona una sucursal primero", Toast.LENGTH_LONG).show()
            return
        }

        val input = CreateMembershipInput(
            memberName = name,
            tier = tier.ifEmpty { "Básico" },
            endDate = endDate.ifEmpty { "2026-12-31" },
            branchId = branchId
        )

        viewModel.createMembership(input)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBarInsets.bottom)
            insets
        }
    }
}
