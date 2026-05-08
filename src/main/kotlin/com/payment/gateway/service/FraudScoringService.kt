package com.payment.gateway.service

import com.payment.gateway.model.PaymentRequest
import org.slf4j.LoggerFactory

class FraudScoringService(private val redis: RedisService) {
    private val log = LoggerFactory.getLogger(FraudScoringService::class.java)

    companion object {
        private const val HIGH_AMOUNT_THRESHOLD = 100_000L  // $1000.00 in cents
        private const val VELOCITY_WINDOW_SECS = 60
        private const val MAX_VELOCITY = 5
        private const val FRAUD_SCORE_THRESHOLD = 70
    }

    fun score(request: PaymentRequest): Int {
        var score = 0

        // High-amount rule: 30 points if over threshold
        if (request.amount >= HIGH_AMOUNT_THRESHOLD) {
            score += 30
            log.warn("Fraud check: high amount {} for token {}", request.amount, mask(request.paymentMethod.token))
        }

        // Velocity check: count requests from same token in window
        val velocityKey = "velocity:${request.paymentMethod.token}"
        val velocityCount = redis.get(velocityKey)?.toIntOrNull() ?: 0
        if (velocityCount >= MAX_VELOCITY) {
            score += 50
            log.warn("Fraud check: high velocity {} for token {}", velocityCount, mask(request.paymentMethod.token))
        }
        redis.set(velocityKey, (velocityCount + 1).toString(), VELOCITY_WINDOW_SECS)

        // Expired card: 40 points
        val now = java.time.YearMonth.now()
        val expiry = java.time.YearMonth.of(request.paymentMethod.expYear, request.paymentMethod.expMonth)
        if (expiry.isBefore(now)) {
            score += 40
        }

        // Suspicious currency mismatch: 10 points for unusual currencies
        if (request.currency !in setOf("USD", "EUR", "GBP", "CAD", "AUD")) {
            score += 10
        }

        return minOf(score, 100)
    }

    fun isDeclined(score: Int): Boolean = score >= FRAUD_SCORE_THRESHOLD

    private fun mask(token: String): String =
        if (token.length > 8) "****${token.takeLast(4)}" else "****"
}
