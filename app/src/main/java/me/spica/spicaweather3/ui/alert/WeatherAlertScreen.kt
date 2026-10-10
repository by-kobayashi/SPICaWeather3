@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.spica.spicaweather3.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import me.spica.spicaweather3.R
import me.spica.spicaweather3.domain.model.WeatherAlert
import me.spica.spicaweather3.presentation.theme.WIDGET_CARD_CORNER_SHAPE
import me.spica.spicaweather3.route.LocalNavController
import me.spica.spicaweather3.ui.main.WeatherViewModel
import me.spica.spicaweather3.ui.widget.MainTopBar
import org.koin.compose.viewmodel.koinActivityViewModel

/**
 * 预警详情页
 *
 * 展示指定城市全部生效预警的完整信息：
 * 级别、标题、发布/生效/失效时间、详细描述与防御指南。
 *
 * 数据来源于共享的 [WeatherViewModel]，刷新后自动更新。
 *
 * @param cityId 城市ID，对应 [me.spica.spicaweather3.domain.model.City.id]
 */
@Composable
fun WeatherAlertScreen(cityId: String) {
  val navController = LocalNavController.current
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val viewModel = koinActivityViewModel<WeatherViewModel>()

  val pageStates = viewModel.weatherPageStates.collectAsStateWithLifecycle().value

  // 根据城市ID查找预警数据；城市被删除或预警解除时得到空列表，展示空态
  val alerts = remember(pageStates, cityId) {
    pageStates.firstOrNull { it.city.id == cityId }
      ?.city?.weather?.weatherAlerts
      .orEmpty()
  }

  val hazeState = rememberHazeState()

  Scaffold(
    containerColor = MaterialTheme.colorScheme.surface,
    topBar = {
      MainTopBar(
        modifier = Modifier.hazeBlur(
          input = HazeInput.Sources(hazeState),
          style = HazeMaterials.ultraThin(MaterialTheme.colorScheme.surface).then {
            progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f))
          }
        ),
        scrollBehavior = scrollBehavior,
        title = {
          Text(
            stringResource(R.string.alert_info_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.W600
          )
        },
        navigationIcon = {
          IconButton(onClick = { navController.removeLastOrNull() }) {
            Icon(
              Icons.AutoMirrored.Default.ArrowBack,
              contentDescription = stringResource(R.string.cd_back)
            )
          }
        }
      )
    }
  ) { paddingValues ->
    if (alerts.isEmpty()) {
      // 空态：无生效预警
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = stringResource(R.string.alert_placeholder),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .hazeSource(hazeState)
          .fillMaxSize()
          .padding(paddingValues)
          .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
          start = 15.dp,
          end = 15.dp,
          top = 12.dp,
          bottom = 48.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        itemsIndexed(
          items = alerts,
          // 拼接下标避免后端返回重复预警ID导致 key 冲突
          key = { index, alert -> "${alert.id}_$index" }
        ) { _, alert ->
          AlertDetailItem(alert = alert)
        }
      }
    }
  }
}

/**
 * 单条预警的完整信息卡片
 */
@Composable
private fun AlertDetailItem(alert: WeatherAlert) {
  val levelColor = alertLevelColor(alert.colorCode, MaterialTheme.colorScheme.primary)
  val onLevelColor = if (levelColor.luminance() > 0.5f) Color.Black else Color.White
  val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
  val hasTimeInfo = alert.issuedTime.isNotBlank() ||
    alert.effectiveTime.isNotBlank() ||
    alert.expireTime.isNotBlank()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = WIDGET_CARD_CORNER_SHAPE
      )
      .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    horizontalAlignment = Alignment.Start
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .background(levelColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = alertSeverityLabel(alert.severity),
            style = MaterialTheme.typography.labelSmall,
            color = onLevelColor,
            fontWeight = FontWeight.W600
          )
        }
        if (alert.eventType.isNotBlank()) {
          Text(
            text = alert.eventType,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.W600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
      Text(
        text = alert.headline.ifBlank { alert.eventType },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.W700
      )
    }

    if (hasTimeInfo) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f),
            shape = RoundedCornerShape(10.dp)
          )
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.Start
      ) {
        if (alert.issuedTime.isNotBlank()) {
          AlertTimeRow(
            text = stringResource(
              R.string.alert_issued_at,
              formatAlertTimeFull(alert.issuedTime)
            )
          )
        }
        if (alert.effectiveTime.isNotBlank()) {
          AlertTimeRow(
            text = stringResource(
              R.string.alert_effective_at,
              formatAlertTimeFull(alert.effectiveTime)
            )
          )
        }
        if (alert.expireTime.isNotBlank()) {
          AlertTimeRow(
            text = stringResource(
              R.string.alert_expire_at,
              formatAlertTimeFull(alert.expireTime)
            )
          )
        }
      }
    }

    if (alert.description.isNotBlank()) {
      HorizontalDivider(color = dividerColor)
      AlertTextSection(
        title = stringResource(R.string.alert_description_title),
        content = alert.description
      )
    }

    if (alert.instruction.isNotBlank()) {
      HorizontalDivider(color = dividerColor)
      AlertTextSection(
        title = stringResource(R.string.alert_instruction_title),
        content = alert.instruction
      )
    }
  }
}

@Composable
private fun AlertTextSection(title: String, content: String) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.W700
    )
    Text(
      text = content,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    )
  }
}

@Composable
private fun AlertTimeRow(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}
