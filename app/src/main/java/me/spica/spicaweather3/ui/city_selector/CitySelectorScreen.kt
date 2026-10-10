package me.spica.spicaweather3.ui.city_selector

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.random.Random
import me.spica.spicaweather3.R
import me.spica.spicaweather3.route.LocalNavController
import me.spica.spicaweather3.ui.main.WeatherViewModel
import me.spica.spicaweather3.utils.navSharedBounds
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CitySelectorScreen() {

    var searchText by rememberSaveable { mutableStateOf("") }

    val viewModel = koinViewModel<CitySelectorViewModel>()

    val errorMessage = viewModel.errorMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(errorMessage.value) {
        if (errorMessage.value?.isNotEmpty() == false) {
            Toast.makeText(context, "${errorMessage.value}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(searchText) {
        viewModel.searchCities(searchText)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
        ) {
            // 顶部搜索栏：与城市管理页共享转场，实现统一的搜索入口体验
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navSharedBounds("search_bar"),
                placeholder = { Text(stringResource(R.string.city_selector_input_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )

            // 根据输入内容动态切换展示热门城市或搜索结果
            AnimatedContent(
                searchText, modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { searchText ->
                if (searchText.isEmpty()) {
                    TopCities()
                } else {
                    ListSelector()
                }
            }
        }
    }
}

@Composable
private fun ListSelector() {
    val viewModel: CitySelectorViewModel = koinViewModel()
    val cities = viewModel.searchResult.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    val weatherViewModel = koinActivityViewModel<WeatherViewModel>()

    // 搜索结果列表：按输入关键字实时返回城市并支持点击添加
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 48.dp, top = 20.dp)
    ) {

        itemsIndexed(cities.value, key = { _, item -> item.id }) { index, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.saveLocation(item, onSucceed = {
                            weatherViewModel.refresh()
                            navController.removeLastOrNull()
                        })
                    }
            ) {
                if (index != 0) {
                    HorizontalDivider()
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(item.name, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${item.administrativeArea2}, ${item.administrativeArea1}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

    }

}

@Composable
private fun TopCities(modifier: Modifier = Modifier) {
    val viewModel: CitySelectorViewModel = koinViewModel()
    val tops = viewModel.topCities.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val weatherViewModel = koinActivityViewModel<WeatherViewModel>()
    // 热门城市宫格：展示预置热门城市，便于快速选择
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.city_selector_hot_cities),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(tops.value, key = { _, location -> location.id }) { _, location ->
                val density = LocalDensity.current
                val appearAnim = remember(location.id) { Animatable(0f) }
                val delayMillis = remember(location.id) { Random.nextInt(0, 200) }
                val durationMillis = remember(location.id) { Random.nextInt(260, 520) }

                LaunchedEffect(location.id) {
                    appearAnim.snapTo(0f)
                    appearAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = durationMillis,
                            delayMillis = delayMillis
                        )
                    )
                }

                val progress = appearAnim.value
                val translationY = with(density) { 36.dp.toPx() * (1f - progress) }
                val scale = 0.5f + 0.5f * progress

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            this.translationY = translationY
                            scaleX = scale
                            scaleY = scale
                            alpha = progress
                        }
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            viewModel.saveLocation(location = location, onSucceed = {
                                weatherViewModel.refresh()
                                navController.removeLastOrNull()
                            })
                        }
                        .padding(
                            vertical = 12.dp
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(location.name)
                }
            }
        }
    }
}
