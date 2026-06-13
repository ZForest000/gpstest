package com.example.gpstest.data.source

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

/**
 * dumpsys location GNSS 节解析结果。
 * @property avgBasebandCn0 所有跟踪卫星的平均基带 CN0（需 root/Shizuku 访问）
 * @property measurementCount 每秒原始 GNSS 测量值数量
 * @property usedInFixConstellations 参与定位解算的星座列表
 */
data class DumpsysGnssData(
    val avgBasebandCn0: Float?,
    val measurementCount: Int,
    val usedInFixConstellations: List<String>,
)

/**
 * Shizuku 权限辅助工具，通过 UserService 执行 `dumpsys location`。
 *
 * Shizuku v13 已将 `Shizuku.newProcess` 设为 private，官方要求改用 UserService 模式：
 * 定义 AIDL 接口 [IDumpsysService]，实现 [DumpsysServiceImpl] 运行在 Shizuku 特权进程中，
 * 通过 [Shizuku.bindUserService] 异步绑定后跨进程调用。
 *
 * 此为可选功能：无 Shizuku 时应用功能完整，但失去基带 CN0 和测量计数统计。
 * 根模式（[isRootMode] = true，UID 0）有完全访问权限；非根 Shizuku 仅有 ADB 级别权限。
 *
 * 生命周期：binder 可能在 Shizuku 重启后失效，需通过 [Shizuku.OnBinderReceivedListener]
 * 重新绑定，通过 [Shizuku.OnBinderDeadListener] 清理引用。
 */
object ShizukuHelper {
    /** UserService 的 ProGuard 稳定标识，变更会触发新进程而非复用 */
    private const val USER_SERVICE_TAG = "dumpsys_service"
    private const val USER_SERVICE_VERSION = 1

    /** 当前绑定的 UserService 代理，null 表示未绑定或已失效 */
    @Volatile
    private var dumpsysService: IDumpsysService? = null

    /** 是否已完成 UserService 绑定请求（避免重复绑定） */
    @Volatile
    private var bindRequested = false

    val isShizukuAvailable: Boolean
        get() =
            try {
                Shizuku.pingBinder()
            } catch (e: Exception) {
                false
            }

    val isPermissionGranted: Boolean
        get() =
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }

    val isRootMode: Boolean
        get() =
            try {
                Shizuku.getUid() == 0
            } catch (e: Exception) {
                false
            }

    init {
        // 监听 Shizuku binder 生命周期：可用时绑定 UserService，失效时清理引用
        // 多次 addListener 安全（Shizuku 内部为 Set），但 init 仅在 object 首次访问时执行一次
        Shizuku.addBinderReceivedListener { bindUserServiceIfNeeded() }
        Shizuku.addBinderDeadListener {
            dumpsysService = null
            bindRequested = false
        }
    }

    /**
     * 通过 UserService 执行 `dumpsys location` 并解析 GNSS KPI 数据。
     *
     * 必须在 IO 线程调用。静默降级：Shizuku 不可用、未授权、未绑定或执行失败时返回 null。
     * 首次调用会触发异步绑定，可能返回 null；后续调用（绑定完成后）返回真实数据。
     */
    fun fetchDumpsysGnssData(): DumpsysGnssData? {
        if (!isShizukuAvailable || !isPermissionGranted) return null
        val service = dumpsysService ?: run {
            bindUserServiceIfNeeded()
            return null
        }
        return try {
            val output = service.exec("dumpsys location")
            DumpsysParser.parse(output)
        } catch (e: Exception) {
            null
        }
    }

    /** 触发 UserService 绑定（幂等：已请求过则跳过） */
    @Synchronized
    private fun bindUserServiceIfNeeded() {
        if (bindRequested || !isShizukuAvailable || !isPermissionGranted) return
        try {
            val args =
                Shizuku.UserServiceArgs(
                    ComponentName(
                        "com.example.gpstest",
                        "com.example.gpstest.data.source.DumpsysServiceImpl",
                    ),
                ).tag(USER_SERVICE_TAG)
                    .version(USER_SERVICE_VERSION)
            Shizuku.bindUserService(args, serviceConnection)
            bindRequested = true
        } catch (e: Exception) {
            // 绑定失败（如权限被撤销），下次重试
            bindRequested = false
        }
    }

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
                dumpsysService = IDumpsysService.Stub.asInterface(service)
            }

            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                dumpsysService = null
                // 非正常断开时允许重绑
                bindRequested = false
            }
        }
}
