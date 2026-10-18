@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package me.spica.spicaweather3.ui.adaptive

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * 窄窗口优先显示天气，宽窗口增加城市侧栏。页面导航和城市分页状态由调用方持有。
 */
@Composable
fun AdaptiveWeatherLayout(
    listPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
    detailPane: @Composable () -> Unit,
) {
    val directive = weatherPaneScaffoldDirective(windowAdaptiveInfo)
    val value = calculateThreePaneScaffoldValue(
        maxHorizontalPartitions = directive.maxHorizontalPartitions,
        adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
        currentDestination = null,
    )

    // 天气是主窗格；城市侧栏只在空间足够时展示，无需额外维护一套窗格导航栈。
    ListDetailPaneScaffold(
        directive = directive,
        value = value,
        modifier = modifier,
        listPane = {
            AnimatedPane(modifier = Modifier.preferredWidth(280.dp)) {
                listPane()
            }
        },
        detailPane = {
            AnimatedPane {
                detailPane()
            }
        },
    )
}

internal fun weatherPaneScaffoldDirective(
    windowAdaptiveInfo: WindowAdaptiveInfo,
): PaneScaffoldDirective {
    val directive = calculatePaneScaffoldDirective(windowAdaptiveInfo)
    // 手机横屏和较矮的分屏窗口仍以天气内容为主。
    val hasEnoughHeight = windowAdaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(
        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
    )
    return directive.copy(
        maxHorizontalPartitions = if (hasEnoughHeight) {
            minOf(2, directive.maxHorizontalPartitions)
        } else {
            1
        },
        horizontalPartitionSpacerSize = if (hasEnoughHeight) {
            directive.horizontalPartitionSpacerSize
        } else {
            0.dp
        },
    )
}
