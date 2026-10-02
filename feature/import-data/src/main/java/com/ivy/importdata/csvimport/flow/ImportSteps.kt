package com.ivy.importdata.csvimport.flow

import androidx.compose.runtime.Composable
import com.ivy.importdata.csvimport.flow.instructions.IvyWalletSteps
import com.ivy.legacy.domain.deprecated.logic.csv.model.ImportType

/**
 * Only the Ivy Wallet backup path remains.
 *
 * The nine third-party CSV importers were removed by owner decision, so the
 * per-app `when` is gone rather than reduced: keeping nine unreachable branches
 * would suggest the app still supports them.
 */
@Composable
fun ImportType.ImportSteps(
    onUploadClick: () -> Unit
) {
    IvyWalletSteps(
        onUploadClick = onUploadClick
    )
}
