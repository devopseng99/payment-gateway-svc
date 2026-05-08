package com.payment.gateway.model

import java.time.Instant

enum class PaymentStatus { PENDING, AUTHORIZED, CAPTURED, DECLINED, REFUNDED }

data class PaymentMethodToken(
    val token: String,
    val last4: String,
    val brand: String,
    val expMonth: Int,
    val expYear: Int
)

data class PaymentRequest(
    val amount: Long,
    val currency: String,
    val paymentMethod: PaymentMethodToken,
    val idempotencyKey: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class Payment(
    val id: String,
    val amount: Long,
    val currency: String,
    val status: PaymentStatus,
    val fraudScore: Int,
    val last4: String,
    val brand: String,
    val idempotencyKey: String?,
    val createdAt: String,
    val updatedAt: String
)

data class RefundRequest(
    val amount: Long? = null,
    val reason: String = "requested_by_customer"
)

data class RefundRecord(
    val id: String,
    val paymentId: String,
    val amount: Long,
    val reason: String,
    val status: String,
    val createdAt: String
)

data class WebhookEvent(
    val id: String,
    val type: String,
    val created: Long,
    val data: Map<String, Any>
)

data class HealthResponse(
    val status: String,
    val version: String = "1.0.0",
    val timestamp: String = Instant.now().toString()
)

data class ErrorResponse(
    val error: String,
    val message: String,
    val requestId: String? = null
)
