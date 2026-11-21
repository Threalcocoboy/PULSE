package com.pulse.app.integration.googlehealth

import com.pulse.app.BuildConfig
import com.pulse.app.core.DataMode

internal object GoogleHealthAvailability {
    val enabled = BuildConfig.GOOGLE_HEALTH_ENABLED
    const val comingSoon = "Coming soon · pending verification"
    const val paused = "Google Health sync is paused. Your previously imported records are still on this device."

    fun canConnect(mode: DataMode) = mode != DataMode.GOOGLE_HEALTH || enabled
}
