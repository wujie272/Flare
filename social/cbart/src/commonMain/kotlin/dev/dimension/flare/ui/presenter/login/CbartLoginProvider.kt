package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.cbart.CbartPlatformDetector
import dev.dimension.flare.data.network.cbart.CbartService
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val LOGIN_ACTION = "login"
private const val USERNAME_FIELD = "username"
private const val PASSWORD_FIELD = "password"
private const val AUTO_LOGIN_FIELD = "auto_login"

public data object CbartLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Cbart
    override val metadata: PlatformTypeMetadata get() = CbartPlatformSpec.metadata
    override val detector: PlatformDetector = CbartPlatformDetector
    override val methods: List<LoginMethodSpec> = listOf(
        LoginMethodSpec(type = LoginMethodType.Password, title = UiStrings.PasswordLogin),
    )
    override fun agreementUrl(host: String): String? = null

    override suspend fun recommendInstances(): List<RecommendedInstance> = listOf(
        RecommendedInstance(
            instance = UiInstance(
                name = "妖狐吧",
                description = "妖狐吧内容平台",
                iconUrl = null, domain = CBART_HOST,
                type = platformType, bannerUrl = null, usersCount = 0,
            ), priority = 50,
        ),
    )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} metadata is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.Password) { "Unsupported 妖狐吧 login method: ${context.methodType}" }
        return CbartPasswordLoginHandler(context)
    }
}

/**
 * 妖狐吧账号密码登录 Handler
 * 支持用户名/密码输入 + 自动登录开关
 */
private class CbartPasswordLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler {
    private val accountService: AccountService by koinInject()
    private val values = mutableMapOf<String, String>()
    private val _state = MutableStateFlow(loginState())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(id: String, value: String) {
        values[id] = value
        _state.value = loginState(error = null)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _state.value = loginState(loading = true)

        runCatching {
            val username = values[USERNAME_FIELD].orEmpty()
            val password = values[PASSWORD_FIELD].orEmpty()
            val autoLogin = values[AUTO_LOGIN_FIELD].orEmpty() == "true"
            val deviceId = Uuid.random().toString()

            // 初始化凭证
            val credential = CbartCredential(
                apiToken = "ACF09D095C44ADD56B80FEE4A3A5BB3A",
                uuid = deviceId,
                userName = username.ifBlank { null },
                password = if (autoLogin) password.ifBlank { null } else null,
            )

            val service = CbartService(flowOf(credential))

            // 1. init 获取服务器信息
            service.initDevice(deviceId = deviceId)

            // 2. 注册/登录，传用户名和密码
            val registerData = service.registerDevice(
                deviceId = deviceId,
                username = username.ifBlank { null },
                password = password.ifBlank { null },
            )

            val userId = registerData?.uid?.toString() ?: deviceId.take(8)
            val nickName = registerData?.nickName ?: username.ifBlank { "yaohu_${userId.take(6)}" }
            val avatarUrl = registerData?.avatarUrl

            val accountKey = MicroBlogKey(id = userId, host = CBART_HOST)

            val verifiedCredential = credential.copy(
                userId = userId,
                nickName = nickName,
                avatarUrl = avatarUrl,
                autoLogin = autoLogin,
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
            _state.value = loginState(error = it.message)
        }
    }

    override suspend fun resume(value: String) = Unit
    override fun canResume(value: String): Boolean = false

    override fun clear() {
        values.clear()
        _state.value = loginState()
    }

    override fun close() = Unit

    private fun loginState(
        loading: Boolean = false,
        error: String? = null,
    ): LoginFlowState {
        val username = values[USERNAME_FIELD].orEmpty()
        val password = values[PASSWORD_FIELD].orEmpty()
        val autoLogin = values[AUTO_LOGIN_FIELD].orEmpty() != "false"

        val canLogin = username.isNotBlank() && password.isNotBlank()

        return LoginFlowState(
            fields = listOf(
                LoginField(
                    id = USERNAME_FIELD,
                    type = LoginFieldType.TextInput,
                    label = UiStrings.Username,
                    value = username,
                ),
                LoginField(
                    id = PASSWORD_FIELD,
                    type = LoginFieldType.PasswordInput,
                    label = UiStrings.Password,
                    value = password,
                ),
                LoginField(
                    id = AUTO_LOGIN_FIELD,
                    type = LoginFieldType.DisplayText,
                    label = UiStrings.Settings,
                    value = if (autoLogin) "✅ 自动登录已开启" else " 自动登录已关闭",
                ),
            ),
            actions = listOf(
                LoginAction(
                    id = LOGIN_ACTION,
                    label = UiStrings.Login,
                    enabled = !loading && canLogin,
                ),
            ),
            loading = loading,
            error = error,
        )
    }
}
