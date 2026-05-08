package com.payment.gateway.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.payment.gateway.model.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class PaymentService(
    private val redis: RedisService,
    private val fraudScoring: FraudScoringService
) {
    private val log = LoggerFactory.getLogger(PaymentService::class.java)
    private val mapper = jacksonObjectMapper().also {
        it.findAndRegisterModules()
    }

    companion object {
        private const val PAYMENT_TTL_SECS = 86_400 * 30  // 30 days
        private const val REFUND_TTL_SECS = 86_400 * 30
    }

    fun createPayment(request: PaymentRequest): Payment {
        // Idempotency: return existing if key already seen
        if (request.idempotencyKey != null) {
            val existingId = redis.get("idempotency:${request.idempotencyKey}")
            if (existingId != null) {
                log.warn("Idempotent replay for key {}", request.idempotencyKey)
                return getPayment(existingId) ?: error("Idempotent payment not found: $existingId")
            }
        }

        val fraudScore = fraudScoring.score(request)
        val status = if (fraudScoring.isDeclined(fraudScore)) PaymentStatus.DECLINED else PaymentStatus.CAPTURED
        val now = Instant.now().toString()
        val id = UUID.randomUUID().toString()

        val payment = Payment(
            id = id,
            amount = request.amount,
            currency = request.currency.uppercase(),
            status = status,
            fraudScore = fraudScore,
            last4 = request.paymentMethod.last4,
            brand = request.paymentMethod.brand,
            idempotencyKey = request.idempotencyKey,
            createdAt = now,
            updatedAt = now
        )

        redis.set("payment:$id", mapper.writeValueAsString(payment), PAYMENT_TTL_SECS)

        if (request.idempotencyKey != null) {
            redis.set("idempotency:${request.idempotencyKey}", id, PAYMENT_TTL_SECS)
        }

        log.warn("Payment {} created status={} fraudScore={} amount={} currency={} last4={}",
            id, status, fraudScore, request.amount, payment.currency, payment.last4)
        return payment
    }

    fun getPayment(id: String): Payment? {
        val json = redis.get("payment:$id") ?: return null
        return mapper.readValue(json)
    }

    fun createRefund(paymentId: String, request: RefundRequest): RefundRecord {
        val payment = getPayment(paymentId) ?: error("Payment not found: $paymentId")

        check(payment.status == PaymentStatus.CAPTURED) {
            "Cannot refund payment with status ${payment.status}"
        }

        val refundAmount = request.amount ?: payment.amount
        check(refundAmount > 0 && refundAmount <= payment.amount) {
            "Invalid refund amount: $refundAmount"
        }

        val now = Instant.now().toString()
        val refundId = UUID.randomUUID().toString()

        val refund = RefundRecord(
            id = refundId,
            paymentId = paymentId,
            amount = refundAmount,
            reason = request.reason,
            status = "succeeded",
            createdAt = now
        )

        redis.set("refund:$refundId", mapper.writeValueAsString(refund), REFUND_TTL_SECS)

        // Update payment status
        val updated = payment.copy(
            status = PaymentStatus.REFUNDED,
            updatedAt = now
        )
        redis.set("payment:$paymentId", mapper.writeValueAsString(updated), PAYMENT_TTL_SECS)

        log.warn("Refund {} created for payment {} amount={}", refundId, paymentId, refundAmount)
        return refund
    }

    fun recordWebhookEvent(source: String, event: WebhookEvent) {
        val key = "webhook:$source:${event.id}"
        if (redis.exists(key)) {
            log.warn("Duplicate webhook event {} from {}", event.id, source)
            return
        }
        redis.set(key, mapper.writeValueAsString(event), 86_400)
        log.warn("Webhook {} event={} id={}", source, event.type, event.id)
    }
}
