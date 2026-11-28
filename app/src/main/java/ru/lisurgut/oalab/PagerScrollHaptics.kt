package ru.lisurgut.oalab

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.drop

@Composable
internal fun PagerScrollHaptics(pager: PagerState) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pager, haptics) {
        snapshotFlow { pager.currentPage }.drop(1).collect {
            if (pager.isScrollInProgress) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
    }
}
