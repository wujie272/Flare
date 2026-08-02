package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.coolapk.CoolapkAuthUtil
import dev.dimension.flare.data.network.coolapk.CoolapkPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.CoolapkCredential
import dev.dimension.flare.data.platform.CoolapkPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull

private const val LOGIN_ACTION = "login"
private const val COOLAPK_LOGIN_URL = "https://account.coolapk.com/auth/login"

public data object CoolapkLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Coolapk
    override val metadata: PlatformTypeMetadata get() = CoolapkPlatformSpec.metadata
    override val detector: PlatformDetector = CoolapkPlatformDetector
    override val methods: List<LoginMethodSpec> =
        listOf(
            LoginMethodSpec(type = LoginMethodType.WebCookie, title = UiStrings.WebCookieLogin),
        )

    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                instance =
                    UiInstance(
                        name = "酷安",
                        description = "分享美好科技生活",
                        iconUrl = null,
                        domain = "coolapk.com",
                        type = platformType,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                priority = 50,
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} metadata is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.WebCookie) { "Unsupported Coolapk login method: ${context.methodType}" }
        return CoolapkWebCookieLoginHandler(context)
    }
}

private class CoolapkWebCookieLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler {
    private val accountService: AccountService by koinInject()
    private val _state = MutableStateFlow(state())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) = Unit

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _effects.emit(LoginEffect.OpenWebCookieLogin(url = COOLAPK_LOGIN_URL))
    }

    override suspend fun resume(value: String) {
        _state.value = state(loading = true)
        runCatching {
            val token = value.extractCoolapkToken()
            require(token != null) { "酷安Cookie中缺少token字段，请确保已登录" }

            val uid = value.extractCoolapkUid()
            val username = value.extractCoolapkUsername()

            // 生成设备码和App Token（用于API鉴权）
            val deviceCode = CoolapkAuthUtil.generateDeviceCode()
            val appToken = CoolapkAuthUtil.generateAppToken(deviceCode)

            val credential =
                CoolapkCredential(
                    token = token,
                    uid = uid ?: "",
                    username = username ?: "",
                    rawCookie = value,
                    deviceCode = deviceCode,
                    appToken = appToken,
                )

            val accountKey =
                MicroBlogKey(
                    id = uid ?: token,
                    host = "coolapk.com",
                )

            context.requireReloginAccount(accountKey)
            val addJob =
                accountService.addAccount(
                    account = UiAccount(accountKey = accountKey, platformType = PlatformType.Coolapk),
                    credential = credential,
                    serializer = CoolapkCredential.serializer(),
                )
            addJob.join()
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override fun canResume(value: String): Boolean = value.extractCoolapkToken() != null

    override fun clear() {
        _state.value = state()
    }

    private fun state(
        loading: Boolean = false,
        error: String? = null,
    ): LoginFlowState =
        LoginFlowState(
            actions = listOf(LoginAction(id = LOGIN_ACTION, label = UiStrings.Login, enabled = !loading)),
            loading = loading,
            error = error,
        )
}

/** 从 Cookie 字符串中提取 token */
private fun String.extractCoolapkToken(): String? =
    split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("token=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotBlank() }

/** 从 Cookie 字符串中提取 uid */
private fun String.extractCoolapkUid(): String? =
    split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("uid=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotBlank() }

/** 从 Cookie 字符串中提取 username */
private fun String.extractCoolapkUsername(): String? =
    split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("username=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotBlank() }
        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
