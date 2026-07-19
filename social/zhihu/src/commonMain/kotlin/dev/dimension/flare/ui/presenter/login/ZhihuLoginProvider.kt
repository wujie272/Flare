package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.zhihu.ZhihuPlatformDetector
import dev.dimension.flare.data.network.zhihu.ZhihuService
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.ZHIHU_HOST
import dev.dimension.flare.data.platform.ZhihuCredential
import dev.dimension.flare.data.platform.ZhihuPlatformSpec
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
import kotlin.time.Clock

private const val LOGIN_ACTION = "login"
private const val ZHIHU_LOGIN_URL = "https://www.zhihu.com/signin"

public data object ZhihuLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Zhihu
    override val metadata: PlatformTypeMetadata get() = ZhihuPlatformSpec.metadata
    override val detector: PlatformDetector = ZhihuPlatformDetector
    override val methods: List<LoginMethodSpec> = listOf(
        LoginMethodSpec(type = LoginMethodType.WebCookie, title = UiStrings.WebCookieLogin),
    )
    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> = listOf(
        RecommendedInstance(
            instance = UiInstance(
                name = "知乎",
                description = "中文互联网问答社区",
                iconUrl = null,
                domain = ZHIHU_HOST,
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
        require(context.methodType == LoginMethodType.WebCookie) { "Unsupported Zhihu login method: ${context.methodType}" }
        return ZhihuWebCookieLoginHandler(context)
    }
}

private class ZhihuWebCookieLoginHandler(
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
        _effects.emit(LoginEffect.OpenWebCookieLogin(url = ZHIHU_LOGIN_URL))
    }

    override suspend fun resume(value: String) {
        _state.value = state(loading = true)
        runCatching {
            val zc0 = value.extractZc0()
            require(zc0 != null) { "知乎Cookie中缺少z_c0字段，请确保已登录" }

            val dc0 = value.extractDc0()
            val xsrf = value.extractXsrf()

            // 1. 构建初始 credential
            val now = Clock.System.now().toEpochMilliseconds()
            val credential = ZhihuCredential(
                zc0 = zc0,
                dc0 = dc0,
                xsrfToken = xsrf,
                rawCookie = value,
                lastCookieRefreshEpochMillis = now,
            )

            // 2. 调 API 验证 session（跟 VVo 的 config() 一样）
            val credentialState = MutableStateFlow(credential)
            val service = ZhihuService(
                credentialFlow = credentialState,
            )
            val userInfo = service.fetchCurrentUser()
            requireNotNull(userInfo) { "无法验证知乎登录状态，请重新登录" }

            // 3. 从 /api/v4/me 拿到用户信息
            val accountKey = MicroBlogKey(
                id = userInfo.id,
                host = ZHIHU_HOST,
            )

            // 4. 更新 credential 中的用户信息
            val verifiedCredential = credential.copy(
                userId = userInfo.id,
                userName = userInfo.name,
                avatarUrl = userInfo.avatarUrl,
            )

            context.requireReloginAccount(accountKey)
            accountService.addAccount(
                account = UiAccount(accountKey = accountKey, platformType = PlatformType.Zhihu),
                credential = verifiedCredential,
                serializer = ZhihuCredential.serializer(),
            )
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override fun canResume(value: String): Boolean = value.extractZc0() != null
    override fun clear() { _state.value = state() }

    private fun state(loading: Boolean = false, error: String? = null): LoginFlowState = LoginFlowState(
        actions = listOf(LoginAction(id = LOGIN_ACTION, label = UiStrings.Login, enabled = !loading)),
        loading = loading, error = error,
    )
}

/** 从 Cookie 字符串中提取 z_c0 */
private fun String.extractZc0(): String? =
    split(";").map { it.trim() }.firstOrNull { it.startsWith("z_c0=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }

/** 从 Cookie 字符串中提取 d_c0 */
private fun String.extractDc0(): String? =
    split(";").map { it.trim() }.firstOrNull { it.startsWith("d_c0=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }

/** 从 Cookie 字符串中提取 xsrf */
private fun String.extractXsrf(): String? =
    split(";").map { it.trim() }.firstOrNull { it.startsWith("xsrf=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }
