package com.beta.auraos.ui.memberships

import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.R
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.Membership
import com.beta.auraos.util.formatDate
import com.beta.auraos.databinding.ActivityMembershipListBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class MembershipListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMembershipListBinding
    private lateinit var viewModel: MembershipViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        val repository = MembershipRepository()
        val factory = MembershipViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MembershipViewModel::class.java]

        binding = ActivityMembershipListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        binding.btnBack.setOnClickListener { finish() }
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, MembershipFormActivity::class.java))
        }

        viewModel.listState.observe(this) { state ->
            when (state) {
                is MembershipListUiState.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is MembershipListUiState.Success -> {
                    binding.loadingOverlay.visibility = View.GONE
                    renderMemberships(state.memberships)
                }
                is MembershipListUiState.Error -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        loadMemberships()
    }

    override fun onResume() {
        super.onResume()
        loadMemberships()
    }

    private fun loadMemberships() {
        val branchId = sessionManager.getSelectedBranchId()
        viewModel.loadMemberships(branchId)
    }

    private fun renderMemberships(memberships: List<Membership>) {
        binding.contentContainer.removeViews(1, binding.contentContainer.childCount - 1)

        if (memberships.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyState.visibility = View.GONE

        for (m in memberships) {
            val card = createMembershipCard(m)
            binding.contentContainer.addView(card)

            val params = card.layoutParams as LinearLayout.LayoutParams
            if (card !== binding.contentContainer.getChildAt(1)) {
                params.topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            }

            card.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(m.memberName)
                    .setMessage("${getString(R.string.membership_hint_tier)}: ${m.tier}\n${getString(R.string.membership_hint_end_date)}: ${formatDate(m.endDate)}\n${getString(R.string.membership_status_active)}: ${if (m.isActive) getString(R.string.membership_status_active) else getString(R.string.membership_status_inactive)}")
                    .setPositiveButton(getString(R.string.dialog_ok), null)
                    .setNegativeButton(getString(R.string.membership_delete_title)) { _, _ ->
                        confirmDelete(m)
                    }
                    .show()
            }
        }
    }

    private fun createMembershipCard(m: Membership): View {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setCardBackgroundColor(
                ColorStateList.valueOf(
                    MaterialColors.getColor(
                        this, R.attr.cardGlassFillColorCustom
                    )
                )
            )
            radius = resources.getDimension(R.dimen.card_corner_radius_large)
            setContentPadding(padding, padding, padding, padding)
            isClickable = true
            isFocusable = true
            foreground = {
                val tv = android.util.TypedValue()
                this@MembershipListActivity.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true
                )
                ContextCompat.getDrawable(this@MembershipListActivity, tv.resourceId)
            }()
        }

        val container = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        val nameText = TextView(this).apply {
            text = m.memberName
            setTextColor(
                MaterialColors.getColor(
                    binding.root, R.attr.textPrimaryCustom
                )
            )
            textSize = 16f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        container.addView(nameText)

        val tierText = TextView(this).apply {
            text = "${m.tier}  \u2022  ${formatDate(m.endDate)}"
            setTextColor(
                MaterialColors.getColor(
                    binding.root, R.attr.textSecondaryCustom
                )
            )
            textSize = 13f
        }
        container.addView(tierText)

        card.addView(container)
        return card
    }

    private fun confirmDelete(m: Membership) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.membership_delete_title))
            .setMessage(getString(R.string.membership_delete_confirm, m.memberName))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                val branchId = sessionManager.getSelectedBranchId()
                viewModel.deleteMembership(m.id, branchId)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentContainer) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBarInsets.bottom)
            insets
        }
    }
}
