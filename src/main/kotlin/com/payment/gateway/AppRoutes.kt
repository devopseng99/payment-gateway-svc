package com.payment.gateway

import com.payment.gateway.handlers.HealthHandler
import com.payment.gateway.handlers.PaymentHandler
import com.payment.gateway.handlers.WebhookHandler
import org.http4k.core.HttpHandler
import org.http4k.core.Method.*
import org.http4k.routing.bind
import org.http4k.routing.routes

fun createRoutes(
    health: HealthHandler,
    payment: PaymentHandler,
    webhook: WebhookHandler
): HttpHandler = routes(
    "/health"              bind GET  to health.liveness,
    "/ready"               bind GET  to health.readiness,
    "/payments"            bind POST to payment.create,
    "/payments/{id}"       bind GET  to payment.get,
    "/payments/{id}/refund" bind POST to payment.refund,
    "/webhooks/stripe"     bind POST to webhook.stripe,
    "/webhooks/adyen"      bind POST to webhook.adyen
)
