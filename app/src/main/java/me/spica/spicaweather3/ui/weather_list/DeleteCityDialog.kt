package me.spica.spicaweather3.ui.weather_list

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import me.spica.spicaweather3.R

/**
 * 删除城市确认对话框
 * 
 * 用于确认删除操作，显示城市名称并提供确认/取消选项。
 * 
 * @param cityName 要删除的城市名称
 * @param show 对话框显示状态
 * @param onConfirm 确认删除回调
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun DeleteCityDialog(
  cityName: String,
  show: MutableState<Boolean>,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  if (show.value) {
    AlertDialog(
      title = { Text(stringResource(R.string.dialog_title_notice)) },
      text = { Text(stringResource(R.string.weather_list_delete_city_message, cityName)) },
      onDismissRequest = onDismiss,
      confirmButton = {
        TextButton(
          onClick = {
            onConfirm()
            show.value = false
          }
        ) {
          Text(stringResource(R.string.action_confirm))
        }
      },
    )
  }
}
