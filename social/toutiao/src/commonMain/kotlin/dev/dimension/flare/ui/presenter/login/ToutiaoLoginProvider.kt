package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.toutiao.ToutiaoPlatformDetector
import dev.dimension.flare.data.network.toutiao.ToutiaoService
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.TOUTIAO_HOST
import dev.dimension.flare.data.platform.ToutiaoCredential
import dev.dimension.flare.data.platform.ToutiaoPlatformSpec
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.firstOrNull

private const val LOGIN_ACTION = "login"
// 用 /user/ 页面，未登录会自动跳转登录页
// 首页在 WebView 里会跳到 snssdk143:// 私有 scheme
// 用移动版 m.toutiao.com，WebView 友好度更高
// PC 版首页会检测 WebView 环境并跳到 snssdk143:// 私有 scheme
private const val TOUTIAO_LOGIN_URL = "https://m.toutiao.com/"

public data object ToutiaoLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Toutiao
    override val metadata: PlatformTypeMetadata get() = ToutiaoPlatformSpec.metadata
    override val detector: PlatformDetector = ToutiaoPlatformDetector
    override val methods: List<LoginMethodSpec> = listOf(
        LoginMethodSpec(type = LoginMethodType.WebCookie, title = UiStrings.WebCookieLogin),
    )
    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> = listOf(
        RecommendedInstance(
            instance = UiInstance(
                name = "今日头条",
                description = "ByteDance news aggregation platform",
                iconUrl = null,
                domain = TOUTIAO_HOST,
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
        require(context.methodType == LoginMethodType.WebCookie) { "Unsupported Toutiao login method: ${context.methodType}" }
        return ToutiaoWebCookieLoginHandler(context)
    }
}

private class ToutiaoWebCookieLoginHandler(
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
        _effects.emit(LoginEffect.OpenWebCookieLogin(url = TOUTIAO_LOGIN_URL))
    }

    override suspend fun resume(value: String) {
        _state.value = state(loading = true)
        runCatching {
            val ttwid = value.extractTtwid()
            require(!ttwid.isNullOrBlank()) { "今日头条Cookie中缺少ttwid字段，请确保已登录" }

            val csrftoken = value.extractCsrftoken()
            val ttWebid = value.extractTtWebid()

            val credential = ToutiaoCredential(
                ttwid = ttwid,
                csrftoken = csrftoken,
                ttWebid = ttWebid,
                rawCookie = value,
            )

            val service = ToutiaoService(flowOf(credential))

            // Validate session by calling hot board API
            val hotBoard = service.fetchHotBoard()
            require(hotBoard.isNotEmpty()) { "Failed to verify Toutiao session" }

            // Try to get user info from personal page
            val userInfo = runCatching { service.fetchCurrentUser() }.getOrNull()

            val verifiedCredential = credential.copy(
                userId = userInfo?.userId,
                userName = userInfo?.userName,
                avatarUrl = userInfo?.avatarUrl,
            )

            val accountKey = MicroBlogKey(
                id = userInfo?.userId ?: "tt_${ttwid.take(8)}",
                host = TOUTIAO_HOST,
            )
            context.requireReloginAccount(accountKey)
            accountService.addAccount(
                account = UiAccount(accountKey = accountKey, platformType = PlatformType.Toutiao),
                credential = verifiedCredential,
                serializer = ToutiaoCredential.serializer(),
            )
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override fun canResume(value: String): Boolean = value.extractTtwid() != null
    override fun clear() { _state.value = state() }

    private fun state(loading: Boolean = false, error: String? = null): LoginFlowState = LoginFlowState(
        actions = listOf(LoginAction(id = LOGIN_ACTION, label = UiStrings.Login, enabled = !loading)),
        loading = loading, error = error,
    )
}

private fun String.extractTtwid(): String? =
    split(";").map { it.trim() }.firstOrNull { it.startsWith("ttwid=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }

private fun String.extractCsrftoken(): String? =
    split(";").map { it.trim() }.firstOrNull { it.startsWith("csrftoken=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }

private fun String.extractTtWebid(): String? =
    split(";").map { it.trim() }.firstOrNull { it.startsWith("tt_webid=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }
