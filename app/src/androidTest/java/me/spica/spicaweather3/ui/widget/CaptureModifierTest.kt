package me.spica.spicaweather3.ui.widget

import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import me.spica.spicaweather3.utils.noRippleClickable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureModifierTest {
    private lateinit var activity: ActivityScenario<ComponentActivity>
    private lateinit var view: ComposeView
    private var touchPoint = Offset.Zero

    @Before
    fun launchHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 先将测试页面切到前台，避免设备拦截后台启动。
        val component = "${instrumentation.targetContext.packageName}/${ComponentActivity::class.java.name}"
        instrumentation.uiAutomation.executeShellCommand("am start -W -n $component").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        activity = ActivityScenario.launch(ComponentActivity::class.java)
    }

    @After
    fun closeHost() {
        if (::activity.isInitialized) activity.close()
    }

    @Test
    fun tapCapturesPixelsAndWindowBoundsWithCurrentStaticItems() {
        val controller = DropdownMenuController()
        var items by mutableStateOf(menuItems("first"))
        var expectedOffset = Offset.Zero
        var expectedSize = IntSize.Zero
        showContent {
            CompositionLocalProvider(LocalDropdownMenuController provides controller) {
                Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Box(
                        Modifier.size(64.dp)
                            .captureToDropdownMenu(items)
                            .background(Color.Red)
                            .onGloballyPositioned {
                                expectedOffset = it.positionInWindow()
                                expectedSize = it.size
                            }
                            .touchTarget()
                    )
                }
            }
        }

        tapAndAwait(controller)
        onIdle {
            assertEquals(expectedOffset, controller.state.targetOffset)
            assertEquals(expectedSize, controller.state.targetSize)
            assertEquals(expectedSize.width, controller.state.targetBitmap!!.width)
            assertEquals(expectedSize.height, controller.state.targetBitmap!!.height)
            assertCapturedColor(controller, Color.Red)
            controller.dismiss()
            items = menuItems("updated")
        }
        tapAndAwait(controller)
        onIdle { assertEquals("updated", controller.state.menuItems.single().text) }
    }

    @Test
    fun dynamicProviderAndCompositionLocalAreReadAtEachGesture() {
        val firstController = DropdownMenuController()
        val secondController = DropdownMenuController()
        var controller by mutableStateOf(firstController)
        var provider by mutableStateOf<() -> List<DropdownMenuItem>>({ menuItems("first") })
        showContent {
            CompositionLocalProvider(LocalDropdownMenuController provides controller) {
                Box(
                    Modifier.size(64.dp)
                        .captureToDropdownMenuDynamic(provider)
                        .background(Color.Blue)
                        .touchTarget()
                )
            }
        }

        tapAndAwait(firstController)
        onIdle {
            firstController.dismiss()
            provider = { menuItems("new provider") }
            controller = secondController
        }
        tapAndAwait(secondController)
        onIdle {
            assertFalse(firstController.isVisible)
            assertEquals("new provider", secondController.state.menuItems.single().text)
        }
    }

    @Test
    fun switchingToLongPressStopsTapActivation() {
        val controller = DropdownMenuController()
        var onLongClick by mutableStateOf(false)
        showContent {
            CompositionLocalProvider(LocalDropdownMenuController provides controller) {
                Box(
                    Modifier.size(64.dp)
                        .captureToDropdownMenuDynamic({ menuItems("menu") }, onLongClick)
                        .background(Color.Red)
                        .touchTarget()
                )
            }
        }

        tapAndAwait(controller)
        onIdle {
            controller.dismiss()
            onLongClick = true
        }
        tap()
        onIdle { assertFalse(controller.isVisible) }
        tap(longPress = true)
        eventually { controller.isVisible }
        onIdle { assertEquals("menu", controller.state.menuItems.single().text) }
    }

    @Test
    fun disabledAndEmptyMenusDoNotActivateOrEvaluateTheProvider() {
        val controller = DropdownMenuController()
        var enabled by mutableStateOf(false)
        var providerCalls = 0
        showContent {
            CompositionLocalProvider(LocalDropdownMenuController provides controller) {
                Box(
                    Modifier.size(64.dp)
                        .captureToDropdownMenuDynamic(
                            itemsProvider = { providerCalls++; emptyList() },
                            enabled = enabled
                        )
                        .background(Color.Red)
                        .touchTarget()
                )
            }
        }

        tap()
        onIdle {
            assertEquals(0, providerCalls)
            assertFalse(controller.isVisible)
            enabled = true
        }
        tap()
        onIdle {
            assertEquals(1, providerCalls)
            assertFalse(controller.isVisible)
            enabled = false
        }
        tap()
        onIdle {
            assertEquals(1, providerCalls)
            assertFalse(controller.isVisible)
        }

        val base = Modifier.size(64.dp)
        assertSame(base, base.captureToDropdownMenu(emptyList()))
        assertSame(base, base.captureToDropdownMenu(menuItems("menu"), enabled = false))
    }

    @Test
    fun reusedContentCapturesTheNewPixelsAndItems() {
        val controller = DropdownMenuController()
        var reuseKey by mutableStateOf(0)
        showContent {
            CompositionLocalProvider(LocalDropdownMenuController provides controller) {
                ReusableContent(reuseKey) {
                    Box(
                        Modifier.size(64.dp)
                            .captureToDropdownMenu(menuItems("item $reuseKey"))
                            .background(if (reuseKey == 0) Color.Red else Color.Blue)
                            .touchTarget()
                    )
                }
            }
        }

        tapAndAwait(controller)
        onIdle {
            assertCapturedColor(controller, Color.Red)
            controller.dismiss()
            reuseKey++
        }
        tapAndAwait(controller)
        onIdle {
            assertEquals("item 1", controller.state.menuItems.single().text)
            assertCapturedColor(controller, Color.Blue)
        }
    }

    @Test
    fun hoistedModifierCanDetachAndAttachWithANewLayer() {
        val controller = DropdownMenuController()
        val capture = Modifier.captureToDropdownMenu(menuItems("menu"))
        var enabled by mutableStateOf(true)
        var color by mutableStateOf(Color.Red)
        showContent {
            CompositionLocalProvider(LocalDropdownMenuController provides controller) {
                Box(
                    Modifier.size(64.dp)
                        .then(if (enabled) capture else Modifier)
                        .background(color)
                        .touchTarget()
                )
            }
        }

        tapAndAwait(controller)
        onIdle {
            controller.dismiss()
            enabled = false
        }
        tap()
        onIdle {
            assertFalse(controller.isVisible)
            enabled = true
            color = Color.Blue
        }
        tapAndAwait(controller)
        onIdle { assertCapturedColor(controller, Color.Blue) }
    }

    @Test
    fun rippleFreeClickUpdatesItsCallback() {
        var current by mutableStateOf("first")
        val clicks = mutableListOf<String>()
        showContent {
            val captured = current
            Box(
                Modifier.size(64.dp)
                    .noRippleClickable { clicks += captured }
                    .touchTarget()
            )
        }

        tap()
        onIdle { current = "second" }
        tap()
        onIdle { assertEquals(listOf("first", "second"), clicks) }
    }

    private fun tapAndAwait(controller: DropdownMenuController) {
        tap()
        eventually { controller.isVisible }
    }

    private fun assertCapturedColor(controller: DropdownMenuController, expected: Color) {
        val bitmap = controller.state.targetBitmap
        assertNotNull(bitmap)
        val pixels = bitmap!!.toPixelMap()
        val color = pixels[bitmap.width / 2, bitmap.height / 2]
        assertEquals(expected.red, color.red, 0.01f)
        assertEquals(expected.green, color.green, 0.01f)
        assertEquals(expected.blue, color.blue, 0.01f)
        assertTrue(color.alpha > 0.99f)
    }

    private fun menuItems(text: String) = listOf(DropdownMenuItem(text = text, onClick = {}))

    private fun Modifier.touchTarget() = onGloballyPositioned {
        touchPoint = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
    }

    private fun showContent(content: @Composable () -> Unit) {
        activity.onActivity {
            view = ComposeView(it).apply { setContent(content) }
            val extent = (160 * it.resources.displayMetrics.density).toInt()
            it.setContentView(view, FrameLayout.LayoutParams(extent, extent, Gravity.CENTER))
        }
        awaitFrames()
    }

    private fun tap(longPress: Boolean = false) {
        awaitFrames()
        val downTime = SystemClock.uptimeMillis()
        fun dispatch(action: Int) = onUi {
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, touchPoint.x, touchPoint.y, 0
            )
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        dispatch(MotionEvent.ACTION_DOWN)
        if (longPress) SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 100L)
        dispatch(MotionEvent.ACTION_UP)
        awaitFrames()
    }

    private fun onIdle(block: () -> Unit) {
        awaitFrames()
        onUi(block)
    }

    private fun onUi(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun awaitFrames() {
        val drawn = CountDownLatch(1)
        onUi { view.postOnAnimation { view.postOnAnimation { drawn.countDown() } } }
        assertTrue("Test view did not receive frames", drawn.await(5, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun eventually(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000L
        while (!predicate() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(16L)
        assertTrue("Condition did not become true before the deadline", predicate())
    }
}
