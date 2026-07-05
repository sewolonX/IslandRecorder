package com.island.recorder.framework.privileged.core.context.wrapper

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import rikka.shizuku.Shizuku

class ShizukuContext(base: Context) : ContextWrapper(base) {
    override fun getOpPackageName() = "com.android.shell"

    override fun getAttributionSource() =
        AttributionSource.Builder(Shizuku.getUid())
            .setPackageName(opPackageName)
            .setPid(android.os.Process.INVALID_PID)
            .build()
}
