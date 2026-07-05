package com.island.recorder.framework.privileged.core.context.wrapper

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper

class SystemContext(base: Context) : ContextWrapper(base) {
    override fun getOpPackageName() = "android"

    override fun getAttributionSource() =
        AttributionSource.Builder(1000)
            .setPackageName(opPackageName)
            .build()
}
