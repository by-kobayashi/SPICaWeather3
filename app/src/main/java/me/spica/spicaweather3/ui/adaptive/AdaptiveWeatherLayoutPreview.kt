package me.spica.spicaweather3.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import me.spica.spicaweather3.presentation.theme.SpicaWeatherTheme

@Preview(name = "Phone", widthDp = 390, heightDp = 844)
@Preview(name = "Tablet portrait", widthDp = 800, heightDp = 1280)
@Preview(name = "Tablet landscape", widthDp = 1280, heightDp = 800)
@Preview(name = "Split screen", widthDp = 540, heightDp = 800)
@Composable
private fun AdaptiveWeatherLayoutPreview() {
    SpicaWeatherTheme {
        AdaptiveWeatherLayout(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            listPane = {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("城市列表", style = MaterialTheme.typography.titleLarge)
                }
            },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("天气内容", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
