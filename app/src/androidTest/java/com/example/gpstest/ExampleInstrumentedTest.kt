package com.example.gpstest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 基础 instrumented 测试骨架。
 *
 * 目的：激活 app/src/androidTest/ 源集与 androidTestImplementation 依赖
 * （androidx.test.ext:junit、ui-test-junit4、work-testing），为未来 UI/集成测试铺路。
 * 需连接真实设备或模拟器运行：./gradlew connectedAndroidTest
 *
 * 本轮 E4 仅放骨架，不写复杂 UI 测试。
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // 验证目标应用包名正确，instrumented 环境可用
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.gpstest", appContext.packageName)
    }
}
