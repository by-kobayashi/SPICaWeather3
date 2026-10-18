package me.spica.spicaweather3.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import me.spica.spicaweather3.presentation.theme.SpicaWeatherTheme
import me.spica.spicaweather3.route.LocalNavController
import me.spica.spicaweather3.route.Routes
import me.spica.spicaweather3.ui.air_quality.AirQualityScreen
import me.spica.spicaweather3.ui.alert.WeatherAlertScreen
import me.spica.spicaweather3.ui.city_selector.CitySelectorScreen
import me.spica.spicaweather3.ui.main.MainScreen
import me.spica.spicaweather3.ui.main.WeatherViewModel
import me.spica.spicaweather3.ui.weather_list.WeatherListScreen
import me.spica.spicaweather3.ui.widget.BottomSheetMenu
import me.spica.spicaweather3.ui.widget.DropdownMenuOverlay
import me.spica.spicaweather3.ui.widget.LocalDropdownMenuController
import me.spica.spicaweather3.ui.widget.LocalMenuState
import me.spica.spicaweather3.ui.widget.materialSharedAxisZIn
import me.spica.spicaweather3.ui.widget.materialSharedAxisZOut
import me.spica.spicaweather3.utils.DataStoreUtil
import me.spica.spicaweather3.utils.LocationHelper
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel


@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> {
    error("LocalSharedTransitionScope not provided")
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalPermissionsApi::class)
@Composable
fun AppMain() {

    val locationHelper = koinInject<LocationHelper>()

    val dataStoreUtil = koinInject<DataStoreUtil>()

    val locationPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    val isFirstLaunch = dataStoreUtil.getIsFirstLaunch().collectAsStateWithLifecycle(false).value

    LaunchedEffect(isFirstLaunch, locationPermissionState) {
        if (!locationPermissionState.allPermissionsGranted) {
            locationPermissionState.launchMultiplePermissionRequest()
        }
    }

    val weatherViewModel = koinActivityViewModel<WeatherViewModel>()

    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSize = LocalWindowInfo.current.containerSize

    LaunchedEffect(locationPermissionState.allPermissionsGranted) {
        if (locationPermissionState.allPermissionsGranted) {
            locationHelper.fetchLocation(
                onSuccess = { loc ->
                    weatherViewModel.insertUserLoc(loc, shouldRefresh = true)
                },
                onFailure = {
                    weatherViewModel.insertUserLoc(null, shouldRefresh = true)
                }
            )
        } else {
            weatherViewModel.insertUserLoc(null, shouldRefresh = true)
        }
    }

    val menuState = LocalMenuState.current

    val dropdownMenuController = LocalDropdownMenuController.current

    SpicaWeatherTheme {
        LaunchedEffect(windowSize, windowAdaptiveInfo.windowPosture) {
            menuState.dismiss()
            dropdownMenuController.dismiss()
        }

        AppNavigation(windowAdaptiveInfo)
    }

}

/**
 * 所有窗口尺寸共用同一导航栈，布局变化由各页面的自适应容器处理。
 * 包含导航、底部菜单、下拉菜单等
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppNavigation(windowAdaptiveInfo: WindowAdaptiveInfo) {

    val backStack = rememberNavBackStack(Routes.Main)

    val blurRadius2 = animateDpAsState(
        targetValue = if (LocalDropdownMenuController.current.isVisible) 10.dp else 0.dp,
        label = "DropdownMenuBlur",
        animationSpec = tween(550)
    )

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            CompositionLocalProvider(
                LocalNavController provides backStack,
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
            ) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurRadius2.value)
                        .background(MaterialTheme.colorScheme.surface),
                    sharedTransitionScope = this@SharedTransitionLayout,
                    transitionSpec = {
                        materialSharedAxisZIn(true) togetherWith materialSharedAxisZOut(true)
                    },
                    popTransitionSpec = {
                        materialSharedAxisZIn(true) togetherWith materialSharedAxisZOut(true)
                    },
                    predictivePopTransitionSpec = {
                        materialSharedAxisZIn(true) togetherWith materialSharedAxisZOut(true)
                    },
                    entryProvider = entryProvider {
                        entry<Routes.Main> {
                            MainScreen(windowAdaptiveInfo = windowAdaptiveInfo)
                        }
                        entry<Routes.CitySelect> {
                            CitySelectorScreen()
                        }
                        entry<Routes.WeatherList> {
                            WeatherListScreen()
                        }
                        entry<Routes.AirQuality> {
                            AirQualityScreen()
                        }
                        entry<Routes.WeatherAlert> { route ->
                            WeatherAlertScreen(cityId = route.cityId)
                        }
                    }
                )
            }
            BottomSheetMenu(
                state = LocalMenuState.current,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            // 下拉菜单叠加层
            DropdownMenuOverlay()
        }
    }
}
