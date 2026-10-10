package me.spica.spicaweather3.ui.main.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.spica.spicaweather3.R
import me.spica.spicaweather3.domain.model.WeatherAlert
import me.spica.spicaweather3.domain.model.WeatherData
import me.spica.spicaweather3.presentation.theme.WIDGET_CARD_PADDING
import me.spica.spicaweather3.presentation.theme.WIDGET_CARD_TITLE_TEXT_STYLE
import me.spica.spicaweather3.ui.alert.alertLevelColor
import me.spica.spicaweather3.ui.alert.alertSeverityLabel
import me.spica.spicaweather3.ui.alert.formatAlertTimeShort

private const val MAX_ALERT_PREVIEWS = 3

@Composable
fun AlertCard(
    weatherData: WeatherData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alerts = remember(weatherData.weatherAlerts) {
        weatherData.weatherAlerts.orEmpty()
    }
    val previewAlerts = remember(alerts) { alerts.take(MAX_ALERT_PREVIEWS) }
    val remainingCount = alerts.size - previewAlerts.size
    val dividerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.08f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = alerts.isNotEmpty(), onClick = onClick)
            .padding(WIDGET_CARD_PADDING),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.material_symbols_outlined_brightness_alert),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.alert_info_title),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                style = WIDGET_CARD_TITLE_TEXT_STYLE(),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (previewAlerts.isEmpty()) {
            Text(
                text = stringResource(R.string.alert_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column {
                previewAlerts.forEachIndexed { index, alert ->
                    AlertSummaryItem(alert = alert)
                    if (index != previewAlerts.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = dividerColor
                        )
                    }
                }
            }

            if (remainingCount > 0) {
                Text(
                    text = stringResource(R.string.alert_more_count, remainingCount),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlertSummaryItem(alert: WeatherAlert) {
    val levelColor = alertLevelColor(alert.colorCode, MaterialTheme.colorScheme.primary)
    val onLevelColor = if (levelColor.luminance() > 0.5f) Color.Black else Color.White
    val time = alert.expireTime.ifBlank { alert.issuedTime }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(levelColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = alertSeverityLabel(alert.severity),
                    style = MaterialTheme.typography.labelSmall,
                    color = onLevelColor,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    letterSpacing = 1.5.sp
                )
            }
            if (alert.eventType.isNotBlank()) {
                Text(
                    text = alert.eventType,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = alert.headline.ifBlank { alert.eventType },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.W600
        )
        if (time.isNotBlank()) {
            Text(
                text = if (alert.expireTime.isNotBlank()) {
                    stringResource(R.string.alert_valid_until, formatAlertTimeShort(time))
                } else {
                    stringResource(R.string.alert_issued_at, formatAlertTimeShort(time))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
