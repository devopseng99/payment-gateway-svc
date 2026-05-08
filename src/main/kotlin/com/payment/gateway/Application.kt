package com.payment.gateway

import com.payment.gateway.handlers.HealthHandler
import com.payment.gateway.handlers.PaymentHandler
import com.payment.gateway.handlers.WebhookHandler
import com.payment.gateway.service.FraudScoringService
import com.payment.gateway.service.PaymentService
import com.payment.gateway.service.RedisService
import org.http4k.server.Undertow
import org.http4k.server.asServer
import org.slf4j.LoggerFactory

fun main() {
    val log = LoggerFactory.getLogger("Application")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8000

    val redis = RedisService()
    redis.initialize()

    val fraudScoring = FraudScoringService(redis)
    val paymentService = PaymentService(redis, fraudScoring)

    val healthHandler = HealthHandler(redis)
    val paymentHandler = PaymentHandler(paymentService)
    val webhookHandler = WebhookHandler(paymentService)

    val app = createRoutes(healthHandler, paymentHandler, webhookHandler)

    val server = app.asServer(Undertow(port))
    Runtime.getRuntime().addShutdownHook(Thread {
        log.warn("Shutting down payment-gateway-svc")
        server.stop()
        redis.close()
    })

    server.start()
    log.warn("payment-gateway-svc started on port {}", port)
}
