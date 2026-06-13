// IDumpsysService.aidl
package com.example.gpstest.data.source;

/**
 * Shizuku UserService 接口：在 Shizuku 的特权进程中执行 shell 命令。
 *
 * 用于读取 `dumpsys location` 等 GNSS 诊断数据（需 ADB/root 权限）。
 * 该接口由应用定义，实现在 Shizuku 服务进程中运行。
 */
interface IDumpsysService {
    /**
     * 执行 shell 命令并返回合并的 stdout/stderr 输出。
     *
     * @param command 要执行的命令（如 "dumpsys location"）
     * @return 命令的标准输出，失败时返回空字符串
     */
    String exec(String command);

    // 销毁 UserService 进程（transaction code 16777114）
    // 应用调用 destroy() 时，UserService 进程应调用 System.exit(0)
    void destroy();
}
