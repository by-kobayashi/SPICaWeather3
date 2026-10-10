@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.spica.spicaweather3.ui.weather_list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import me.spica.spicaweather3.R
import me.spica.spicaweather3.ui.widget.MainTopBar
import me.spica.spicaweather3.utils.navSharedBounds
import me.spica.spicaweather3.utils.noRippleClickable

/**
 * 天气列表顶栏组件
 * 
 * 包含返回按钮、标题和搜索栏，支持毛玻璃效果和共享元素转场动画。
 * 
 * @param scrollBehavior 滚动行为控制器
 * @param hazeState 毛玻璃效果状态
 * @param onNavigateBack 返回按钮点击回调
 * @param onSearchClick 搜索框点击回调
 */
@Composable
fun WeatherListTopBar(
  scrollBehavior: TopAppBarScrollBehavior,
  hazeState: HazeState,
  onNavigateBack: () -> Unit,
  onSearchClick: () -> Unit
) {
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
        stringResource(R.string.weather_list_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.W600
      )
    },
    largeTitle = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 48.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            // 共享元素动画，与城市选择页面的搜索框联动
            .navSharedBounds("search_bar")
            .background(
              MaterialTheme.colorScheme.onSurface.copy(alpha = .1f),
              RoundedCornerShape(12.dp)
            )
            .noRippleClickable(onClick = onSearchClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Default.Search,
            contentDescription = stringResource(R.string.cd_search),
            tint = MaterialTheme.colorScheme.onSurface
          )

          Text(
            stringResource(R.string.weather_list_search_placeholder),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp
          )
        }
      }
    },
    navigationIcon = {
      IconButton(onClick = onNavigateBack) {
        Icon(
          Icons.AutoMirrored.Default.ArrowBack,
          contentDescription = stringResource(R.string.cd_back)
        )
      }
    }
  )
}
