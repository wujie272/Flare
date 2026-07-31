package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.cbart.CbartPlatformDetector
import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.data.platform.CbartCredential
import dev.dimension.flare.data.platform.CbartPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
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
import kotlinx.coroutines.flow.flowOf

private const val LOGIN_ACTION = "login"
private const val LARAVEL_LOGIN_URL = "https://linzijun.app/login"

public data object CbartLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Cbart
    override val metadata: PlatformTypeMetadata get() = CbartPlatformSpec.metadata
    override val detector: PlatformDetector = CbartPlatformDetector
    override val methods: List<LoginMethodSpec> = listOf(
        LoginMethodSpec(type = LoginMethodType.WebCookie, title = UiStrings.WebCookieLogin),
    )
    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> = listOf(
        RecommendedInstance(
            instance = UiInstance(
                name = "林子菌", description = "林子菌内容平台",
                iconUrl = null, domain = CBART_HOST,
                type = platformType, bannerUrl = null, usersCount = 0,
            ), priority = 50,
        ),
    )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} metadata is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.WebCookie) { "Unsupported 妖狐吧 login method: ${context.methodType}" }
        return CbartWebCookieLoginHandler(context)
    }
}

private class CbartWebCookieLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler {
    private val accountService: AccountService by koinInject()
    private val _state = MutableStateFlow(state())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(id: String, value: String) = Unit

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _effects.emit(LoginEffect.OpenWebCookieLogin(url = LARAVEL_LOGIN_URL))
    }

    override suspend fun resume(value: String) {
        _state.value = state(loading = true)
        runCatching {
            val laravelSession = value.extractCookie("laravel_session")
                ?: throw IllegalStateException("未获取到 laravel_session，请确保已登录")
            val xsrfToken = value.extractCookie("XSRF-TOKEN")
            val credential = CbartCredential(laravelSession = laravelSession, xsrfToken = xsrfToken)

            // 三步握手绑定 CSRF token 到 session
            val service = CbartService(flowOf(credential))
            val refreshed = service.api.refreshLaravelSession()

            var finalCredential = (refreshed ?: credential).copy(laravelSessionLoggedIn = true)

            // 获取用户信息（昵称、头像等）
            val userInfo = service.fetchUserInfo()
            if (userInfo != null) {
                finalCredential = finalCredential.copy(
                    userId = userInfo.id?.toString() ?: finalCredential.userId,
                    userName = userInfo.name ?: finalCredential.userName,
                    nickName = userInfo.nickName ?: finalCredential.nickName,
                    avatarUrl = userInfo.avatarUrl ?: finalCredential.avatarUrl,
                    purchasedVideoIds = userInfo.purchasedVideoIds ?: finalCredential.purchasedVideoIds,
                )
            }

            val accountKey = MicroBlogKey(id = "cbart_user", host = CBART_HOST)
            context.requireReloginAccount(accountKey)
            accountService.addAccount(
                account = UiAccount(accountKey = accountKey, platformType = PlatformType.Cbart),
                credential = finalCredential,
                serializer = CbartCredential.serializer(),
            )
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override fun canResume(value: String): Boolean = value.extractCookie("laravel_session") != null

    override fun clear() { _state.value = state() }
    override fun close() = Unit

    private fun state(loading: Boolean = false, error: String? = null): LoginFlowState = LoginFlowState(
        actions = listOf(LoginAction(id = LOGIN_ACTION, label = UiStrings.Login, enabled = !loading)),
        loading = loading, error = error,
    )
}

private fun String.extractCookie(name: String): String? =
    split(";").map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }
