package com.payment.gateway.handlers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.payment.gateway.model.*
import com.payment.gateway.service.PaymentService
import org.http4k.core.*
import org.http4k.routing.path
import org.slf4j.LoggerFactory

class PaymentHandler(private val paymentService: PaymentService) {
    private val log = LoggerFactory.getLogger(PaymentHandler::class.java)
    private val mapper = jacksonObjectMapper().also { it.findAndRegisterModules() }

    val create: HttpHandler = { req ->
        runCatching {
            val request: PaymentRequest = mapper.readValue(req.bodyString())
            val payment = paymentService.createPayment(request)
            val status = if (payment.status == PaymentStatus.DECLINED) Status.PAYMENT_REQUIRED else Status.CREATED
            Response(status)
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(payment))
        }.getOrElse { e ->
            log.warn("Payment creation error: {}", e.message)
            errorResponse(Status.BAD_REQUEST, "invalid_request", e.message ?: "Bad request")
        }
    }

    val get: HttpHandler = { req ->
        val id = req.path("id")
        if (id == null) {
            errorResponse(Status.BAD_REQUEST, "missing_param", "id required")
        } else {
            val payment = paymentService.getPayment(id)
            if (payment != null) {
                Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .body(mapper.writeValueAsString(payment))
            } else {
                errorResponse(Status.NOT_FOUND, "not_found", "Payment $id not found")
            }
        }
    }

    val refund: HttpHandler = { req ->
        val id = req.path("id")
        if (id == null) {
            errorResponse(Status.BAD_REQUEST, "missing_param", "id required")
        } else {
            runCatching {
                val request: RefundRequest = if (req.bodyString().isBlank()) RefundRequest()
                                             else mapper.readValue(req.bodyString())
                val refund = paymentService.createRefund(id, request)
                Response(Status.CREATED)
                    .header("Content-Type", "application/json")
                    .body(mapper.writeValueAsString(refund))
            }.getOrElse { e ->
                log.warn("Refund error for payment {}: {}", id, e.message)
                val status = if (e is IllegalArgumentException || e is IllegalStateException) Status.BAD_REQUEST
                             else Status.NOT_FOUND
                errorResponse(status, "refund_error", e.message ?: "Refund failed")
            }
        }
    }

    private fun errorResponse(status: Status, error: String, message: String): Response =
        Response(status)
            .header("Content-Type", "application/json")
            .body(mapper.writeValueAsString(ErrorResponse(error = error, message = message)))
}
