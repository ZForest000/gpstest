package com.example.gpstest.data.source

import androidx.annotation.Keep

/**
 * Shizuku UserService 实现：运行在 Shizuku 的特权进程中，执行 shell 命令。
 *
 * 通过 `Runtime.getRuntime().exec()` 执行 `dumpsys location` 等诊断命令，
 * 权限来自 Shizuku（ADB UID 2000 或 root UID 0）。
 *
 * 必须保留空构造函数（Shizuku 通过反射实例化），并用 [@Keep][Keep] 防止混淆。
 */
@Keep
class DumpsysServiceImpl : IDumpsysService.Stub() {
    override fun exec(command: String): String =
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            // 先读完 stdout 再 waitFor，避免管道缓冲区满导致进程挂起
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }

    override fun destroy() {
        // UserService 进程退出。应用调用此方法后，Shizuku 会回收服务进程
        System.exit(0)
    }
}
