package com.goydevv.gitide.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class SetupWizardService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val CHANNEL_ID = "setup_wizard_channel"
        private const val NOTIFICATION_ID = 101

        sealed class SetupState {
            object Idle : SetupState()
            data class Progress(val message: String, val percentage: Int) : SetupState()
            data class TerminalLog(val line: String) : SetupState()
            object Success : SetupState()
            data class Error(val reason: String) : SetupState()
        }

        private val _setupState = MutableStateFlow<SetupState>(SetupState.Idle)
        val setupState: StateFlow<SetupState> = _setupState
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing system components..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (_setupState.value is SetupState.Progress) return START_NOT_STICKY
        
        _setupState.value = SetupState.Progress("Preparing folders...", 5)
        serviceScope.launch {
            try {
                initializeEnvironment()
            } catch (e: Exception) {
                _setupState.value = SetupState.Error(e.localizedMessage ?: "Unknown bootstrapping error")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun initializeEnvironment() {
        val baseDir = filesDir
        val binDir = File(baseDir, "usr/bin")
        val homeDir = File(baseDir, "home")
        val prootDir = File(baseDir, "proot")
        val rootfsDir = File(prootDir, "rootfs")

        listOf(binDir, homeDir, prootDir, rootfsDir).forEach { if (!it.exists()) it.mkdirs() }

        val busyboxFile = File(binDir, "busybox")
        val prootFile = File(binDir, "proot")

        _setupState.value = SetupState.Progress("Deploying system binaries...", 20)
        copyAssetToInternal("busybox-aarch64", busyboxFile)
        copyAssetToInternal("proot-aarch64", prootFile)

        busyboxFile.setExecutable(true, false)
        prootFile.setExecutable(true, false)

        _setupState.value = SetupState.Progress("Downloading core Linux filesystem (Alpine)...", 40)
        val rootfsTar = File(prootDir, "rootfs.tar.gz")
        downloadRootfs("https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-minirootfs-3.19.1-aarch64.tar.gz", rootfsTar)

        _setupState.value = SetupState.Progress("Extracting subsystem structure (Restoring symlinks)...", 65)
        extractTarGz(busyboxFile, rootfsTar, rootfsDir)

        _setupState.value = SetupState.Progress("Configuring environmental paths...", 80)
        val etcDir = File(rootfsDir, "etc")
        if (!etcDir.exists()) etcDir.mkdirs()
        File(etcDir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

        writeLauncherScript(binDir, prootFile, rootfsDir, homeDir)

        _setupState.value = SetupState.Progress("Synchronizing environment and installing Git...", 90)
        executeInitialPkgUpdate(prootFile, rootfsDir)

        _setupState.value = SetupState.Success
        stopSelf()
    }

    private fun copyAssetToInternal(assetName: String, targetFile: File) {
        assets.open(assetName).use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun downloadRootfs(urlString: String, outputFile: File) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 60000
        connection.readTimeout = 60000
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("Mirror connection failed code: ${connection.responseCode}")
        }

        connection.inputStream.use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun extractTarGz(busybox: File, tarFile: File, outputDir: File) {
        val process = Runtime.getRuntime().exec(
            arrayOf(busybox.absolutePath, "tar", "-zxf", tarFile.absolutePath, "-C", outputDir.absolutePath)
        )
        process.waitFor()
        if (tarFile.exists()) tarFile.delete()
    }

    private fun writeLauncherScript(binDir: File, proot: File, rootfs: File, home: File) {
        val launcherScript = File(binDir, "proot_launch.sh")
        launcherScript.writeText(
            """
            #!/system/bin/sh
            export UNSET_AM_VARIABLES=1
            export HOME=/home/goydevv
            exec ${proot.absolutePath} -r ${rootfs.absolutePath} -0 -b /dev -b /proc -b /sys -b /sdcard -w /home/goydevv /bin/sh
            """.trimIndent()
        )
        launcherScript.setExecutable(true, false)
    }

    private fun executeInitialPkgUpdate(proot: File, rootfs: File) {
        _setupState.value = SetupState.TerminalLog("goydevv@gitide:~# apk update && apk add git")
        
        val process = ProcessBuilder().command(
            proot.absolutePath,
            "-r", rootfs.absolutePath,
            "-0",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "/bin/sh", "-c", "apk update && apk add git"
        ).redirectErrorStream(true).start()

        process.inputStream.bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                _setupState.value = SetupState.TerminalLog(line!!)
            }
        }
        process.waitFor()
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GitIDE Subsystem Setup")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Subsystem Bootstrap Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
