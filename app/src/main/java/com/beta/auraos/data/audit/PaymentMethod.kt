package com.beta.auraos.data.audit

import androidx.annotation.StringRes
import com.beta.auraos.R

/** How a paid exit was settled; [key] is what audit records store. */
enum class PaymentMethod(val key: String, @StringRes val labelRes: Int) {
    CASH("cash", R.string.pc_pay_cash),
    MOBILE("mobile", R.string.pc_pay_mobile),
    CARD("card", R.string.pc_pay_card);

    companion object {
        fun fromKey(key: String?): PaymentMethod? = entries.find { it.key == key }
    }
}

/** The drawer at closing time: what it started with and what the operator counted. */
data class CashCount(
    val openingFloat: Double,
    val counted: Double,
)
