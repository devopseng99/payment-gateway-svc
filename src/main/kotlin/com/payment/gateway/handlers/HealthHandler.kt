package com.payment.gateway.handlers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.payment.gateway.model.HealthResponse
import com.payment.gateway.service.RedisService
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status

class HealthHandler(private val redis: RedisService) {
    private val mapper = jacksonObjectMapper()

    val liveness: HttpHandler = {
        Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(mapper.writeValueAsString(HealthResponse(status = "ok")))
    }

    val readiness: HttpHandler = {
        if (redis.isHealthy()) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(HealthResponse(status = "ready")))
        } else {
            Response(Status.SERVICE_UNAVAILABLE)
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(HealthResponse(status = "not_ready")))
        }
    }
}
