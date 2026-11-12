package com.example.dailytrack_mobile.data.vault

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts the private vault in front of the Investments API, as the web's
 * vault/intercept.js does: with the vault off, requests go to the server as
 * always; with it on, the engine answers them from the opened vault, in the same
 * shapes, so no screen (or the widget) needs to know which it is.
 */
@Singleton
class VaultInterceptor @Inject constructor(
    private val store: VaultStore,
    private val engine: VaultEngine,
    private val kiteMode: KiteModeSource,
) : Interceptor {

    private fun respond(chain: Interceptor.Chain, answer: VaultEngine.Answer): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(answer.code)
        .message(if (answer.code in 200..299) "OK" else "Vault")
        .body(answer.json.toResponseBody(JSON))
        .build()

    private fun answer(chain: Interceptor.Chain): Response? {
        val request = chain.request()
        if (store.status().optString("state") == "on" && !store.isOpen) store.tryRemembered()
        if (!store.isOpen) return respond(chain, engine.lockedAnswer(request.method, request.url))
        val body = request.body
        val text = if (body != null && body.contentType()?.subtype == "json") Buffer().also { body.writeTo(it) }.readUtf8() else null
        return engine.handle(request.method, request.url, body, text)?.let { respond(chain, it) }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (path == "/api/me/settings" && request.method == "PUT") kiteMode.forget()
        if (!VaultEngine.isInvestPath(path)) return chain.proceed(request)

        val state = runCatching { store.status().optString("state", "off") }.getOrDefault("off")
        if (state != "off") return answer(chain) ?: chain.proceed(request)

        val response = chain.proceed(request)
        if (response.code != 409) return response
        // Turned on from another device since this phone read the state: ask again.
        val code = runCatching { org.json.JSONObject(response.peekBody(4096).string()).optString("code") }.getOrNull()
        if (code != "VAULT_ON" && code != "VAULT_MOVING") return response
        runCatching { store.status(force = true) }
        val fromVault = runCatching { answer(chain) }.getOrNull() ?: return response
        response.close()
        return fromVault
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
