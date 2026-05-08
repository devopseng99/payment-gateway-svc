package com.payment.gateway.service

import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.exceptions.JedisConnectionException

class RedisService(
    host: String = System.getenv("REDIS_HOST") ?: "localhost",
    port: Int = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379,
    password: String? = System.getenv("REDIS_PASSWORD")
) {
    private val log = LoggerFactory.getLogger(RedisService::class.java)

    private val pool: JedisPool = run {
        val config = JedisPoolConfig().apply {
            maxTotal = 20
            maxIdle = 10
            minIdle = 2
            testOnBorrow = true
            testWhileIdle = true
        }
        if (password != null) {
            JedisPool(config, host, port, 2000, password)
        } else {
            JedisPool(config, host, port, 2000)
        }
    }

    fun set(key: String, value: String, ttlSeconds: Int? = null) {
        pool.resource.use { jedis ->
            if (ttlSeconds != null) jedis.setex(key, ttlSeconds.toLong(), value)
            else jedis.set(key, value)
        }
    }

    fun get(key: String): String? = pool.resource.use { it.get(key) }

    fun exists(key: String): Boolean = pool.resource.use { it.exists(key) }

    fun del(key: String) = pool.resource.use { it.del(key) }

    fun isHealthy(): Boolean = try {
        pool.resource.use { "PONG" == it.ping() }
    } catch (e: JedisConnectionException) {
        log.warn("Redis health check failed: {}", e.message)
        false
    }

    fun initialize() {
        log.warn("Initializing Redis key namespace: payments/")
        pool.resource.use { jedis ->
            jedis.set("schema:version", "1")
            jedis.set("schema:initialized", System.currentTimeMillis().toString())
        }
    }

    fun close() = pool.close()
}
