package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.bilibili.BilibiliPlatformDetector
import dev.dimension.flare.data.network.bilibili.BilibiliService
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.bilibili.BilibiliCredential
import dev.dimension.flare.data.platform.bilibili.BilibiliPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.serialization.json.jsonPrimitive
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock

private const val LOGIN_ACTION = "login"
private const val BILIBILI_LOGIN_URL = "https://passport.bilibili.com/login"

public data object BilibiliLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Bilibili
    override val metadata: PlatformTypeMetadata get() = BilibiliPlatformSpec.metadata
    override val detector: PlatformDetector = BilibiliPlatformDetector
    override val methods: List<LoginMethodSpec> = listOf(
        LoginMethodSpec(type = LoginMethodType.WebCookie, title = UiStrings.WebCookieLogin),
    )
    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> = listOf(
        RecommendedInstance(
            instance = UiInstance(
                name = "Bilibili",
                description = "中国最大的视频弹幕网站",
                iconUrl = null,
                domain = "bilibili.com",
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
        require(context.methodType == LoginMethodType.WebCookie) { "Unsupported Bilibili login method: ${context.methodType}" }
        return BilibiliWebCookieLoginHandler(context)
    }
}

private class BilibiliWebCookieLoginHandler(
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
        _effects.emit(LoginEffect.OpenWebCookieLogin(url = BILIBILI_LOGIN_URL))
    }

    override suspend fun resume(value: String) {
        _state.value = state(loading = true)
        runCatching {
            val sessdata = value.extractBilibiliCookie("SESSDATA")
            require(sessdata != null) { "B站Cookie中缺少SESSDATA，请确保已登录" }

            val biliJct = value.extractBilibiliCookie("bili_jct")
            val buvid3 = value.extractBilibiliCookie("buvid3")
            val buvid4 = value.extractBilibiliCookie("buvid4")
            val dedeUserId = value.extractBilibiliCookie("DedeUserID")
            val dedeUserIdCkMd5 = value.extractBilibiliCookie("DedeUserID__ckMd5")
            val sid = value.extractBilibiliCookie("sid")

            // 1. 构建初始 credential
            val now = Clock.System.now().toEpochMilliseconds()
            val credential = BilibiliCredential(
                sessdata = sessdata,
                biliJct = biliJct,
                buvid3 = buvid3,
                buvid4 = buvid4,
                dedeUserId = dedeUserId,
                dedeUserIdCkMd5 = dedeUserIdCkMd5,
                sid = sid,
                rawCookie = value,
                lastRefreshEpochMillis = now,
            )

            // 2. 调 API 验证 session
            val credentialState = MutableStateFlow(credential)
            val service = BilibiliService(
                credentialFlow = credentialState,
            )
            val navInfo = service.getNavInfo()

            requireNotNull(navInfo) { "无法验证B站登录状态，请重新登录" }

            val mid = navInfo["mid"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
            val uname = navInfo["uname"]?.jsonPrimitive?.content ?: ""
            val face = navInfo["face"]?.jsonPrimitive?.content ?: ""

            // 3. 创建账号
            val accountKey = MicroBlogKey(
                id = mid.toString(),
                host = "bilibili.com",
            )

            // 4. 更新 credential 中的用户信息
            val verifiedCredential = credential.copy(
                mid = mid,
                userName = uname,
                avatarUrl = face,
            )

            context.requireReloginAccount(accountKey)
            val addJob = accountService.addAccount(
                account = UiAccount(accountKey = accountKey, platformType = PlatformType.Bilibili),
                credential = verifiedCredential,
                serializer = BilibiliCredential.serializer(),
            )
            addJob.join()
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override fun canResume(value: String): Boolean = value.extractBilibiliCookie("SESSDATA") != null
    override fun clear() { _state.value = state() }

    private fun state(loading: Boolean = false, error: String? = null): LoginFlowState = LoginFlowState(
        actions = listOf(LoginAction(id = LOGIN_ACTION, label = UiStrings.Login, enabled = !loading)),
        loading = loading, error = error,
    )
}

/** 从 Cookie 字符串中提取指定字段 */
private fun String.extractBilibiliCookie(name: String): String? =
    split(";").map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }
