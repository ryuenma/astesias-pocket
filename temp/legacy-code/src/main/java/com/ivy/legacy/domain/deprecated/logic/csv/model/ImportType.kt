package com.ivy.legacy.domain.deprecated.logic.csv.model

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.ivy.design.l0_system.Ivy
import com.ivy.ui.R

/**
 * Import sources.
 *
 * Ivy Wallet's own backup export is the only supported source. The nine
 * third-party CSV adapters were removed by owner decision, so this enum has a
 * single member rather than nine unreachable branches.
 */
@Immutable
enum class ImportType {
    IVY;

    fun color(): Color = Ivy

    fun appId(): String = "com.ivy.wallet"

    @DrawableRes
    fun logo(): Int = R.drawable.astesiawallet_logo

    /**
     * Names the app a file comes FROM. Still "Ivy Wallet" on purpose: this
     * describes the source of the data being imported, not this app.
     */
    fun listName(): String = "Ivy Wallet"

    fun appName(): String = "Ivy Wallet"
}
