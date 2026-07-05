package com.island.recorder.framework.privileged

enum class Authorizer {
    Root,
    Shizuku
}

enum class RootMode {
    None,
    Magisk,
    KernelSU,
    APatch
}

enum class ShizukuMode {
    NotRunning,
    NotAuthorized,
    Authorized
}

data class DeviceCapability(
    val rootMode: RootMode,
    val shizukuMode: ShizukuMode
) {
    val hasPrivilegedOperations: Boolean
        get() = rootMode != RootMode.None || shizukuMode == ShizukuMode.Authorized
}

data class AuthorizationState(
    val preferredAuthorizer: Authorizer,
    val capability: DeviceCapability
)
