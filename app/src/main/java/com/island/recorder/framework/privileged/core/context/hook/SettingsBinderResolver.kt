package com.island.recorder.framework.privileged.core.context.hook

import android.os.IBinder
import android.provider.Settings
import com.island.recorder.core.reflection.ReflectionProvider
import com.island.recorder.core.reflection.getStaticValue
import com.island.recorder.core.reflection.getValue
import java.lang.reflect.Field

data class SettingsReflectionInfo(
    val provider: Any,
    val remoteField: Field,
    val originalBinder: IBinder
)

fun ReflectionProvider.resolveSettingsBinder(
    settingsClass: Class<*> = Settings.Global::class.java
): SettingsReflectionInfo? {
    val holder = this.getStaticValue<Any>("sProviderHolder", settingsClass) ?: return null
    val provider = this.getValue<Any>(holder, "mContentProvider") ?: return null

    val remoteField = this.getDeclaredField("mRemote", provider.javaClass) ?: return null
    val originalBinder = remoteField.get(provider) as? IBinder ?: return null

    return SettingsReflectionInfo(provider, remoteField, originalBinder)
}
