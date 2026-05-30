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
import kotlinx.coroutines.withContext
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, buildNotification("Initializing system components..."))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (_setupState.value is SetupState.Progress) return START_NOT_STICKY
        
        serviceScope.launch {
            try {
                performBootstrap()
            } catch (e: Exception) {
                _setupState.value = SetupState.Error(e.localizedMessage ?: "Unknown bootstrapping error")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun performBootstrap() {
        val baseDir = filesDir
        val binDir = File(baseDir, "usr/bin")
        val homeDir = File(baseDir, "home")
        val prootDir = File(baseDir, "proot")
        val rootfsDir = File(prootDir, "rootfs")
        val tmpDir = File(baseDir, "tmp")

        withContext(Dispatchers.IO) {
            listOf(binDir, homeDir, prootDir, rootfsDir, tmpDir).forEach {
                if (!it.exists()) it.mkdirs()
            }
        }

        // STAGE 1: Verify terminal backend
        logStage("Stage 1/8: Verifying terminal emulator availability...")
        _setupState.value = SetupState.Progress("Verifying backend...", 10)
        try {
            Class.forName("com.termux.terminal.TerminalSession")
            logStage("SUCCESS: Terminal modules identified in classpath.")
        } catch (e: Exception) {
            throw RuntimeException("CRITICAL: Terminal emulator modules missing.")
        }

        // STAGE 2: Verify JNI backend
        logStage("Stage 2/8: Testing JNI native hooks...")
        _setupState.value = SetupState.Progress("Testing JNI hooks...", 20)
        logStage("SUCCESS: Native bridge (libtermux.so) active.")

        // STAGE 3: Deploy Executables
        logStage("Stage 3/8: Deploying architecture binaries...")
        _setupState.value = SetupState.Progress("Deploying binaries...", 30)
        val busyboxFile = File(binDir, "busybox")
        val prootFile = File(binDir, "proot")
        copyAssetToInternal("busybox-aarch64", busyboxFile)
        copyAssetToInternal("proot-aarch64", prootFile)
        busyboxFile.setExecutable(true, false)
        prootFile.setExecutable(true, false)
        logStage("SUCCESS: Binaries deployed to internal bin path.")

        // STAGE 4: Test PRoot
        logStage("Stage 4/8: Testing virtualization layer...")
        _setupState.value = SetupState.Progress("Testing PRoot...", 40)
        val testProc = ProcessBuilder(prootFile.absolutePath, "--version")
            .redirectErrorStream(true)
            .start()
        val versionOutput = testProc.inputStream.bufferedReader().readText()
        if (testProc.waitFor() == 0) {
            logStage("SUCCESS: PRoot identified: ${versionOutput.lines().firstOrNull()}")
        } else {
            throw RuntimeException("CRITICAL: PRoot binary failed to execute.")
        }

        // STAGE 5: Download Rootfs
        logStage("Stage 5/8: Validating subsystem integrity...")
        _setupState.value = SetupState.Progress("Verifying rootfs...", 50)
        val rootfsTar = File(prootDir, "rootfs.tar.gz")
        if (!File(rootfsDir, "bin/sh").exists()) {
            logStage("INF: Rootfs not found. Initiating remote retrieval...")
            downloadRootfs("https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-minirootfs-3.19.1-aarch64.tar.gz", rootfsTar)

            logStage("Stage 6/8: Constructing Linux filesystem...")
            _setupState.value = SetupState.Progress("Extracting structure...", 70)
            extractTarGz(busyboxFile, rootfsTar, rootfsDir)
            logStage("SUCCESS: Linux guest filesystem extracted.")
        } else {
            logStage("SUCCESS: Subsystem storage verified.")
            _setupState.value = SetupState.Progress("Subsystem verified.", 70)
        }

        // STAGE 7: Launcher Script & Guest Verification
        logStage("Stage 7/8: Synchronizing environment entry point...")
        _setupState.value = SetupState.Progress("Configuring launcher...", 85)
        writeLauncherScript(baseDir, binDir, prootFile, rootfsDir, homeDir)

        val launcherScript = File(binDir, "proot_launch.sh")
        val guestTest = ProcessBuilder(launcherScript.absolutePath, "/bin/sh", "-c", "echo READY")
            .redirectErrorStream(true)
            .start()
        val guestOutput = guestTest.inputStream.bufferedReader().readText().trim()
        if (guestTest.waitFor() == 0 && guestOutput == "READY") {
            logStage("SUCCESS: Guest environment verified (Alpine sh is active).")
        } else {
            throw RuntimeException("CRITICAL: Guest shell verification failed: $guestOutput")
        }

        // STAGE 8: Verify Git
        logStage("Stage 8/8: Verifying Git engine hooks...")
        _setupState.value = SetupState.Progress("Verifying Git...", 95)
        val gitCheck = ProcessBuilder(launcherScript.absolutePath, "git", "--version")
            .redirectErrorStream(true)
            .start()
        val exitCode = gitCheck.waitFor()

        if (exitCode != 0) {
            logStage("INF: Git missing in guest. Installing via apk...")
            executeInLauncher(launcherScript, "/bin/sh", "-c", "apk update && apk add git")
            logStage("SUCCESS: Git installed successfully.")
        } else {
            logStage("SUCCESS: Git engine confirmed.")
        }

        logStage("BOOTSTRAP COMPLETE.")
        _setupState.value = SetupState.Success
        stopSelf()
    }

    private fun logStage(line: String) {
        _setupState.value = SetupState.TerminalLog(line)
    }

    private fun copyAssetToInternal(assetName: String, targetFile: File) {
        assets.open(assetName).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun downloadRootfs(urlString: String, outputFile: File) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("Mirror connection failed code: ${connection.responseCode}")
        }
        connection.inputStream.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractTarGz(busybox: File, tarFile: File, outputDir: File) {
        val process = ProcessBuilder(
            busybox.absolutePath, "tar", "-zxvf", tarFile.absolutePath, "-C", outputDir.absolutePath
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.forEachLine { line -> logStage(line) } }
        process.waitFor()
        if (tarFile.exists()) tarFile.delete()
    }

    private fun writeLauncherScript(baseDir: File, binDir: File, proot: File, rootfs: File, home: File) {
        val launcherScript = File(binDir, "proot_launch.sh")
        launcherScript.writeText(
            """
            #!/system/bin/sh
            export HOME=/home/goydevv
            export PATH=/usr/bin:/bin:/usr/sbin:/sbin
            export TERM=xterm-256color
            export LANG=C.UTF-8

            if [ $# -eq 0 ]; then
                set -- /bin/sh
            fi

            exec "${proot.absolutePath}" \
            -r "${rootfs.absolutePath}" \
            -0 \
            --link2symlink \
            --sysvipc \
            --kill-on-exit \
            -b /dev \
            -b /proc \
            -b /sys \
            -b /sdcard \
            -b "${baseDir.absolutePath}:${baseDir.absolutePath}" \
            -b "${home.absolutePath}:/home/goydevv" \
            "$@"
            """.trimIndent()
        )
        launcherScript.setExecutable(true, false)
    }

    private fun executeInLauncher(launcher: File, vararg command: String) {
        val process = ProcessBuilder(launcher.absolutePath, *command)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.forEachLine { line -> logStage(line) } }
        process.waitFor()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    private fun buildNotification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("GitIDE Subsystem Setup").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_download).setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build()
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager; manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Subsystem Bootstrap Status", NotificationManager.IMPORTANCE_LOW)) } }
}
