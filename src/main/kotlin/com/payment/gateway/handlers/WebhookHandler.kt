package com.payment.gateway.handlers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.payment.gateway.model.ErrorResponse
import com.payment.gateway.model.WebhookEvent
import com.payment.gateway.service.PaymentService
import org.http4k.core.*
import org.slf4j.LoggerFactory
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebhookHandler(private val paymentService: PaymentService) {
    private val log = LoggerFactory.getLogger(WebhookHandler::class.java)
    private val mapper = jacksonObjectMapper().also { it.findAndRegisterModules() }

    private val stripeSecret = System.getenv("STRIPE_WEBHOOK_SECRET") ?: ""
    private val adyenSecret = System.getenv("ADYEN_WEBHOOK_HMAC_KEY") ?: ""

    val stripe: HttpHandler = { req ->
        val signature = req.header("Stripe-Signature")
        val body = req.bodyString()

        if (stripeSecret.isNotEmpty() && !verifyStripeSignature(body, signature, stripeSecret)) {
            log.warn("Stripe webhook signature verification failed")
            errorResponse(Status.UNAUTHORIZED, "invalid_signature", "Webhook signature invalid")
        } else {
            runCatching {
                val event: WebhookEvent = mapper.readValue(body)
                paymentService.recordWebhookEvent("stripe", event)
                Response(Status.OK).body("{\"received\":true}").header("Content-Type", "application/json")
            }.getOrElse { e ->
                log.warn("Stripe webhook processing error: {}", e.message)
                errorResponse(Status.BAD_REQUEST, "parse_error", e.message ?: "Invalid payload")
            }
        }
    }

    val adyen: HttpHandler = { req ->
        val hmacHeader = req.header("HmacSignature")
        val body = req.bodyString()

        if (adyenSecret.isNotEmpty() && !verifyAdyenHmac(body, hmacHeader, adyenSecret)) {
            log.warn("Adyen webhook HMAC verification failed")
            errorResponse(Status.UNAUTHORIZED, "invalid_signature", "Webhook HMAC invalid")
        } else {
            runCatching {
                val event: WebhookEvent = mapper.readValue(body)
                paymentService.recordWebhookEvent("adyen", event)
                // Adyen expects [accepted] response
                Response(Status.OK).body("[accepted]").header("Content-Type", "text/plain")
            }.getOrElse { e ->
                log.warn("Adyen webhook processing error: {}", e.message)
                errorResponse(Status.BAD_REQUEST, "parse_error", e.message ?: "Invalid payload")
            }
        }
    }

    private fun verifyStripeSignature(payload: String, sigHeader: String?, secret: String): Boolean {
        if (sigHeader == null) return false
        return runCatching {
            val parts = sigHeader.split(",").associate {
                val (k, v) = it.split("=", limit = 2)
                k to v
            }
            val timestamp = parts["t"] ?: return false
            val expectedSig = parts["v1"] ?: return false
            val signedPayload = "$timestamp.$payload"
            val computed = hmacSha256(signedPayload.toByteArray(), secret.toByteArray())
            computed == expectedSig
        }.getOrDefault(false)
    }

    private fun verifyAdyenHmac(payload: String, hmacHeader: String?, secret: String): Boolean {
        if (hmacHeader == null) return false
        return runCatching {
            val computed = hmacSha256(payload.toByteArray(), secret.toByteArray())
            computed == hmacHeader
        }.getOrDefault(false)
    }

    private fun hmacSha256(data: ByteArray, key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    private fun errorResponse(status: Status, error: String, message: String): Response =
        Response(status)
            .header("Content-Type", "application/json")
            .body(mapper.writeValueAsString(ErrorResponse(error = error, message = message)))
}
