package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.cbart.CbartService
import dev.dimension.flare.data.network.cbart.CbartPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.data.platform.CbartCredential
import dev.dimension.flare.data.platform.CbartPlatformSpec
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

private const val LOGIN_ACTION = "login"
private const val CBART_LOGIN_URL = "https://www.linzijiang.app/login"

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
                name = "Cbart",
                description = "Fetish content marketplace",
                iconUrl = null, domain = CBART_HOST,
                type = platformType, bannerUrl = null, usersCount = 0,
            ), priority = 50,
        ),
    )
    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} metadata is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.WebCookie) { "Unsupported Cbart login method: ${context.methodType}" }
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
        _effects.emit(LoginEffect.OpenWebCookieLogin(url = CBART_LOGIN_URL))
    }

    override suspend fun resume(value: String) {
        _state.value = state(loading = true)
        runCatching {
            val laravelSession = value.extractCookieValue("laravel_session")
                ?: error("Cbart session cookie (laravel_session) is missing")

            val xsrfToken = value.extractCookieValue("XSRF-TOKEN")

            val credential = CbartCredential(
                laravelSession = laravelSession,
                xsrfToken = xsrfToken,
            )

            val service = CbartService(flowOf(credential))

            // 验证 session
            require(service.validateSession()) {
                "Failed to verify Cbart session - session may be expired or invalid"
            }

            // 从 /profile 页面 profileJSON 获取全部用户信息（最可靠）
            val userProfile = service.fetchCurrentUserProfile()
            val username = userProfile?.username?.takeIf { it.isNotBlank() }
                ?: service.fetchUsernameFromHomePage()?.first
                ?: "cbart_user"
            val nickName = userProfile?.nickName?.takeIf { it.isNotBlank() }
                ?: username
            val avatarUrl = userProfile?.avatarUrl

            // 尝试获取真实数字 uid
            val numericUid = userProfile?.uid?.toLongOrNull()
                ?: service.fetchNumericUid()
            val realUid = if (numericUid != null) numericUid.toString() else null

            val finalUid = realUid ?: "cb_${username}_${laravelSession.take(6)}"
            val accountKey = MicroBlogKey(id = finalUid, host = CBART_HOST)

            val verifiedCredential = credential.copy(
                userId = finalUid,
                userName = username,
                nickName = nickName,
                avatarUrl = avatarUrl,
            )

            context.requireReloginAccount(accountKey)
            val addJob = accountService.addAccount(
                account = UiAccount(accountKey = accountKey, platformType = PlatformType.Cbart),
                credential = verifiedCredential,
                serializer = CbartCredential.serializer(),
            )
            addJob.join()
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    /**
     * 只检查 laravel_session cookie 是否存在，不做异步验证。
     * 真正的 session 验证和用户信息提取在 resume() 里完成。
     * 跟知乎的 z_c0 检查是同样的思路——cookie 在就说明浏览器登录成功了。
     */
    override fun canResume(value: String): Boolean {
        return value.extractCookieValue("laravel_session") != null
    }

    override fun clear() { _state.value = state() }

    override fun close() = Unit

    private fun state(loading: Boolean = false, error: String? = null): LoginFlowState = LoginFlowState(
        actions = listOf(LoginAction(id = LOGIN_ACTION, label = UiStrings.Login, enabled = !loading)),
        loading = loading, error = error,
    )
}

/**
 * 从 Cookie 字符串中提取指定 key 的值
 * "laravel_session=xxx; XSRF-TOKEN=yyy" → extractCookieValue("laravel_session") = "xxx"
 */
private fun String.extractCookieValue(key: String): String? {
    val pattern = Regex("""${Regex.escape(key)}\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
    return pattern.find(this)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
}
