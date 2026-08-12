package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.datastore.PlatformOAuthPending
import dev.dimension.flare.data.datastore.PlatformOAuthPendingRepository
import dev.dimension.flare.data.network.deviantart.DeviantartPlatformDetector
import dev.dimension.flare.data.network.deviantart.DeviantartService
import dev.dimension.flare.ui.presenter.login.PlatformDetector
import dev.dimension.flare.data.platform.deviantart.DeviantartCredential
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.io.encoding.Base64
import kotlin.random.Random

private const val DA_CLIENT_ID = "73136"
private const val DA_REDIRECT_URI = "flare://Callback/SignIn/Deviantart"
private const val DA_HOST = "deviantart.com"
private const val LOGIN_ACTION = "login"
private const val DA_FLOW_ID = "DeviantartOAuth"

private const val ATTR_VERIFIER = "code_verifier"
private const val ATTR_STATE = "state"
private const val ATTR_REDIRECT_URI = "redirect_uri"

// 生成 PKCE code_verifier (43-128 个字符，仅含 [A-Za-z0-9-._~])
private fun generateCodeVerifier(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    return (1..64).map { chars[Random.nextInt(chars.length)] }.joinToString("")
}

// 生成 PKCE code_challenge = base64url(sha256(code_verifier))
private suspend fun generateCodeChallenge(verifier: String): String {
    val hasher = CryptographyProvider.Default.get(SHA256).hasher()
    val hash = hasher.hash(verifier.encodeToByteArray())
    return hash.encodeBase64Url()
}

private fun ByteArray.encodeBase64Url(): String =
    Base64.encode(this).trimEnd('=').replace('+', '-').replace('/', '_')

public data object DeviantartLoginProvider : LoginPlatformProvider {
    override val platformId: String = "Deviantart"
    override val metadata: PlatformMetadata
        get() = PlatformMetadata(
            displayName = "DeviantArt",
            icon = UiIcon.Art,
        )
    override val detector: PlatformDetector = DeviantartPlatformDetector
    override val methods: List<LoginMethodSpec> = listOf(
        LoginMethodSpec(type = LoginMethodType.OAuth, title = UiStrings.OAuthLogin),
    )
    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> = listOf(
        RecommendedInstance(
            instance = UiInstance(
                name = "DeviantArt",
                description = "World's largest online art community",
                iconUrl = null,
                domain = "deviantart.com",
                platformId = platformId,
                bannerUrl = null,
                usersCount = 0,
            ),
            priority = 50,
        ),
    )
    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformId} metadata is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.OAuth) { "Unsupported DeviantArt login method: ${context.methodType}" }
        return DeviantartOAuth2LoginHandler(context)
    }
}

private class DeviantartOAuth2LoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler {
    private val accountService: AccountService by koinInject()
    private val pendingRepository: PlatformOAuthPendingRepository by koinInject()
    private val _state = MutableStateFlow(state())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects
    override fun updateField(id: String, value: String) = Unit

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _state.value = state(loading = true)
        runCatching {
            val verifier = generateCodeVerifier()
            val challenge = generateCodeChallenge(verifier)
            val state = "flare"
            val redirectUri = context.redirectUri ?: DA_REDIRECT_URI

            // 保存 pending OAuth 状态
            pendingRepository.save(
                PlatformOAuthPending(
                    platformId = "Deviantart",
                    host = DA_HOST,
                    flowId = DA_FLOW_ID,
                    createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    attributes = mapOf(
                        ATTR_VERIFIER to verifier,
                        ATTR_STATE to state,
                        ATTR_REDIRECT_URI to redirectUri,
                    ),
                )
            )

            val loginUrl = "https://www.deviantart.com/oauth2/authorize" +
                "?response_type=code" +
                "&client_id=$DA_CLIENT_ID" +
                "&redirect_uri=$redirectUri" +
                "&scope=basic%20browse%20user%20collection" +
                "&state=$state" +
                "&code_challenge=$challenge" +
                "&code_challenge_method=S256"

            _effects.emit(LoginEffect.OpenUrl(url = loginUrl))
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override suspend fun resume(value: String) {
        _state.value = state(loading = true)
        runCatching {
            val code = parseCallbackCode(value)
            requireNotNull(code) { "Authorization code not found in callback" }

            // 从持久化存储加载 pending OAuth 状态
            val pending = pendingRepository.latest("Deviantart", DA_FLOW_ID)
            requireNotNull(pending) { "No pending OAuth state found" }

            val verifier = pending.attributes[ATTR_VERIFIER]
            requireNotNull(verifier) { "PKCE code_verifier is missing" }

            val tokenResponse = exchangeCodeForToken(code, verifier)
            requireNotNull(tokenResponse) { "Token exchange failed" }

            val now = Clock.System.now().toEpochMilliseconds()
            val credential = DeviantartCredential(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresIn = tokenResponse.expiresIn,
                lastRefreshEpochMillis = now,
            )

            // 验证 token 并获取用户信息
            val credentialState = MutableStateFlow(credential)
            val service = DeviantartService(credentialFlow = credentialState)
            val userInfo = service.whoami()
            requireNotNull(userInfo) { "Failed to verify DeviantArt login" }

            val accountKey = MicroBlogKey(
                id = userInfo.userName,
                host = DA_HOST,
            )

            // 尝试获取 session cookies + CSRF token 用于 _puppy API
            val sessionCredential = service.fetchSessionCookies()

            val verifiedCredential = credential.copy(
                userId = userInfo.userId,
                userName = userInfo.userName,
                avatarUrl = userInfo.avatarUrl,
                sessionCookies = sessionCredential?.sessionCookies ?: credential.sessionCookies,
                csrfToken = sessionCredential?.csrfToken ?: credential.csrfToken,
            )

            context.requireReloginAccount(accountKey)

            val addJob = accountService.addAccount(
                account = UiAccount(accountKey = accountKey, platformId = "Deviantart"),
                credential = verifiedCredential,
                serializer = DeviantartCredential.serializer(),
            )
            addJob.join()

            // 清除 pending 状态
            pendingRepository.clear(pending)
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    private data class TokenResponse(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Long,
    )

    private suspend fun exchangeCodeForToken(code: String, verifier: String): TokenResponse? {
        return try {
            val client = io.ktor.client.HttpClient()
            val resp = client.post("https://www.deviantart.com/oauth2/token") {
                contentType(io.ktor.http.ContentType.Application.FormUrlEncoded)
                setBody(
                    "grant_type=authorization_code" +
                        "&client_id=$DA_CLIENT_ID" +
                        "&redirect_uri=$DA_REDIRECT_URI" +
                        "&code=$code" +
                        "&code_verifier=$verifier"
                )
            }
            val text = resp.bodyAsText()
            client.close()
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
            val accessToken = obj["access_token"]?.jsonPrimitive?.content ?: return null
            val refreshToken = obj["refresh_token"]?.jsonPrimitive?.content
            val expiresIn = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600
            TokenResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = expiresIn,
            )
        } catch (_: Exception) { null }
    }

    override fun canResume(value: String): Boolean = value.startsWith(DA_REDIRECT_URI)
    override fun clear() { _state.value = state() }

    private fun state(loading: Boolean = false, error: String? = null): LoginFlowState = LoginFlowState(
        actions = listOf(LoginAction(id = LOGIN_ACTION, label = UiStrings.Login, enabled = !loading)),
        loading = loading, error = error,
    )
}

// 从回调 URL 中解析 authorization code
private fun parseCallbackCode(callbackUrl: String): String? {
    if (!callbackUrl.startsWith(DA_REDIRECT_URI)) return null
    val query = callbackUrl.substringAfter("?", "")
    if (query.isBlank()) return null
    val params = query.split("&").mapNotNull { param ->
        val parts = param.split("=", limit = 2)
        if (parts.size == 2) parts[0] to parts[1] else null
    }.toMap()
    // 检查是否有 error
    if (params.containsKey("error")) return null
    return params["code"]
}
