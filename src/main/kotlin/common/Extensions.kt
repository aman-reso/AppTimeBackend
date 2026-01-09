package com.apptime.code.common

import common.ApiResponse
import common.ResponseHelper
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Extension function to respond with ApiResponse
 */
suspend inline fun <reified T> ApplicationCall.respondApi(
    data: T? = null,
    message: String? = null,
    statusCode: HttpStatusCode = HttpStatusCode.OK
) {
    @Suppress("UNCHECKED_CAST")
    val response: ApiResponse<T> = ResponseHelper.success(data as T, message)
    respond(statusCode, response)
}

suspend fun ApplicationCall.respondError(
    statusCode: HttpStatusCode,
    message: String,
    code: String? = null
) {
    val response: ApiResponse<Unit> = ResponseHelper.error(statusCode.value, message, code)
    respond(statusCode, response)
}

/**
 * Extension to get X-App-Language header from request
 */
val ApplicationCall.appLanguage: String?
    get() = request.headers["X-App-Language"]

/**
 * Extension to get X-App-Version header from request
 */
val ApplicationCall.appVersion: String?
    get() = request.headers["X-App-Version"]

/**
 * Extension to require X-App-Language header
 * Returns the language or null if not present
 */
fun ApplicationCall.requireAppLanguage(): String? {
    return appLanguage
}

/**
 * Extension to require X-App-Version header
 * Returns the version or null if not present
 */
fun ApplicationCall.requireAppVersion(): String? {
    return appVersion
}

/**
 * Execute database transaction with error handling
 */
inline fun <T> dbTransaction(crossinline block: () -> T): T {
    return transaction {
        block()
    }
}

