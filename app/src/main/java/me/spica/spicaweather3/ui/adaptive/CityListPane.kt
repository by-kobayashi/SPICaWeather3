package me.spica.spicaweather3.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.spica.spicaweather3.R
import me.spica.spicaweather3.ui.main.weather.WeatherPageState

/**
 * 左栏 - 城市列表
 */
@Composable
fun CityListPane(
  cities: List<WeatherPageState>,
  selectedIndex: Int,
  onCitySelected: (Int) -> Unit,
  onManageCities: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
      ),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 标题栏
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(R.string.weather_list_title),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      IconButton(onClick = onManageCities) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = stringResource(R.string.cd_add_city),
        )
      }
    }

    // 城市列表
    LazyColumn(
      modifier = Modifier
        .fillMaxHeight()
        .weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      itemsIndexed(
        items = cities,
        key = { _, state -> state.city.id }
      ) { index, state ->
        CityListItem(
          cityState = state,
          isSelected = index == selectedIndex,
          onClick = { onCitySelected(index) }
        )
      }
    }
  }
}

/**
 * 城市列表项
 */
@Composable
private fun CityListItem(
  cityState: WeatherPageState,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val city = cityState.city
  val weather = city.weather

  // 根据选中状态计算颜色
  val backgroundColor = if (isSelected) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
  } else {
    MaterialTheme.colorScheme.surfaceContainer
  }

  val contentColor = if (isSelected) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.onSurface
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(backgroundColor)
      .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // 定位图标（如果是用户位置）
    if (city.isUserLocation) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.LocationOn,
          contentDescription = stringResource(R.string.cd_user_location),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
    }

    // 城市信息
    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = city.name,
        fontSize = 16.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        color = contentColor
      )

      if (city.administrativeArea1.isNotEmpty() && city.administrativeArea1 != city.name) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "${city.administrativeArea1} · ${city.administrativeArea2}",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // 温度显示
    weather?.current?.let { todayWeather ->
      Text(
        text = "${todayWeather.temperature}°",
        fontSize = 24.sp,
        fontWeight = FontWeight.Light,
        color = contentColor
      )
    }
  }
}
