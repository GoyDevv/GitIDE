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
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

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

    private fun getBaseProotArgs(rootfsDir: File): List<String> {
        val baseDirPath = filesDir.absolutePath
        val homePath = File(filesDir, "home").absolutePath
        return listOf(
            "-r", rootfsDir.absolutePath,
            "-0",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/sdcard",
            "-b", "$baseDirPath:$baseDirPath",
            "-b", "$homePath:/home/goydevv"
        )
    }

    private fun buildProotCommand(prootFile: File, rootfsDir: File, extraArgs: List<String>): List<String> {
        val command = mutableListOf<String>()
        command.add(prootFile.absolutePath)
        command.addAll(getBaseProotArgs(rootfsDir))
        command.addAll(extraArgs)
        return command
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

        logStage("Stage 1/8: Verifying terminal emulator availability...")
        _setupState.value = SetupState.Progress("Verifying backend...", 10)
        try {
            Class.forName("com.termux.terminal.TerminalSession")
            logStage("SUCCESS: Terminal modules identified in classpath.")
        } catch (e: Exception) {
            logStage("FAILURE: CRITICAL: Terminal emulator modules missing.")
            throw RuntimeException("CRITICAL: Terminal emulator modules missing.")
        }

        logStage("Stage 2/8: Testing JNI native hooks...")
        _setupState.value = SetupState.Progress("Testing JNI hooks...", 20)
        logStage("SUCCESS: Native bridge (libtermux.so) active.")

        logStage("Stage 3/8: Deploying architecture binaries...")
        _setupState.value = SetupState.Progress("Deploying binaries...", 30)
        val busyboxFile = File(binDir, "busybox")
        val prootFile = File(binDir, "proot")
        copyAssetToInternal("busybox-aarch64", busyboxFile)
        copyAssetToInternal("proot-aarch64", prootFile)
        busyboxFile.setExecutable(true, false)
        prootFile.setExecutable(true, false)
        logStage("SUCCESS: Binaries deployed to internal bin path.")

        logStage("Stage 4/8: Testing virtualization layer...")
        _setupState.value = SetupState.Progress("Testing PRoot...", 40)
        val testProc = ProcessBuilder(prootFile.absolutePath, "--version")
            .redirectErrorStream(true)
            .start()
        val versionOutput = testProc.inputStream.bufferedReader().readText()
        if (testProc.waitFor() == 0) {
            logStage("SUCCESS: PRoot identified: ${versionOutput.lines().firstOrNull()}")
        } else {
            logStage("FAILURE: CRITICAL: PRoot binary failed to execute.")
            throw RuntimeException("CRITICAL: PRoot binary failed to execute.")
        }

        if (File(rootfsDir, "bin/sh").exists()) {
            val verifyCmd = buildProotCommand(prootFile, rootfsDir, listOf("/bin/sh", "-c", "echo OK"))
            val verifyProc = ProcessBuilder(verifyCmd)
                .redirectErrorStream(true)
                .start()
            val verifyOutput = verifyProc.inputStream.bufferedReader().readText().trim()
            if (verifyProc.waitFor() != 0 || verifyOutput != "OK") {
                logStage("FAILURE: CRITICAL: PRoot guest execution sandbox check failed: $verifyOutput")
                throw RuntimeException("CRITICAL: PRoot guest execution sandbox check failed: $verifyOutput")
            }
            logStage("SUCCESS: PRoot guest execution sandbox verified.")
        }

        logStage("Stage 5/8: Validating subsystem integrity...")
        _setupState.value = SetupState.Progress("Verifying rootfs...", 50)
        val rootfsTar = File(prootDir, "rootfs.tar.gz")

        val isRootfsValid = File(rootfsDir, "bin/sh").exists() &&
                            File(rootfsDir, "bin").exists() &&
                            File(rootfsDir, "usr").exists() &&
                            File(rootfsDir, "etc").exists() &&
                            File(rootfsDir, "lib").exists()

        if (!isRootfsValid) {
            logStage("INF: Rootfs components incomplete or missing. Purging target path and initiating remote retrieval...")
            if (rootfsDir.exists()) {
                rootfsDir.deleteRecursively()
            }
            rootfsDir.mkdirs()
            
            val testPermissionFile = File(rootfsDir, "write_permission_test.txt")
            try {
                testPermissionFile.writeText("TARGET_WRITE_TEST")
                val readCheck = testPermissionFile.readText()
                testPermissionFile.delete()
                logStage("DIAGNOSTIC: Filesystem write/read/delete integrity verified inside rootfs target.")
            } catch (permException: Exception) {
                logStage("FAILURE: Filesystem verification failed: ${permException.javaClass.name} - ${permException.message}")
                throw permException
            }
            
            val targetUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-minirootfs-3.19.1-aarch64.tar.gz"
            downloadRootfs(targetUrl, rootfsTar)

            logStage("Stage 6/8: Constructing Linux filesystem...")
            _setupState.value = SetupState.Progress("Extracting structure...", 70)
            extractTarGz(rootfsTar, rootfsDir)
            
            val missingPaths = mutableListOf<String>()
            if (!File(rootfsDir, "bin/sh").exists()) missingPaths.add("bin/sh")
            if (!File(rootfsDir, "bin").exists()) missingPaths.add("bin")
            if (!File(rootfsDir, "usr").exists()) missingPaths.add("usr")
            if (!File(rootfsDir, "etc").exists()) missingPaths.add("etc")
            if (!File(rootfsDir, "lib").exists()) missingPaths.add("lib")

            if (missingPaths.isNotEmpty()) {
                logStage("FAILURE: Post-extraction validation failed. Missing structural components: ${missingPaths.joinToString(", ")}")
                throw RuntimeException("Rootfs extraction failed due to missing vital paths.")
            }
            logStage("SUCCESS: Linux guest filesystem extracted.")

            val verifyFreshCmd = buildProotCommand(prootFile, rootfsDir, listOf("/bin/sh", "-c", "echo OK"))
            val verifyFreshProc = ProcessBuilder(verifyFreshCmd)
                .redirectErrorStream(true)
                .start()
            val verifyFreshOutput = verifyFreshProc.inputStream.bufferedReader().readText().trim()
            if (verifyFreshProc.waitFor() != 0 || verifyFreshOutput != "OK") {
                logStage("FAILURE: CRITICAL: PRoot fresh guest sandbox execution validation failed: $verifyFreshOutput")
                throw RuntimeException("CRITICAL: PRoot fresh guest sandbox execution validation failed: $verifyFreshOutput")
            }
            logStage("SUCCESS: PRoot guest execution sandbox verified.")
        } else {
            logStage("SUCCESS: Subsystem storage verified.")
            _setupState.value = SetupState.Progress("Subsystem verified.", 70)
        }

        logStage("Stage 7/8: Synchronizing environment entry point...")
        _setupState.value = SetupState.Progress("Configuring launcher...", 85)
        writeLauncherScript(baseDir, binDir, prootFile, rootfsDir)

        val launcherScript = File(binDir, "proot_launch.sh")
        
        if (!launcherScript.exists() || !launcherScript.canExecute()) {
            logStage("FAILURE: CRITICAL: Launcher script validation failed or script is missing execute privileges.")
            throw RuntimeException("CRITICAL: Launcher script validation failed or script is missing execute privileges.")
        }

        val selfTestProc = ProcessBuilder(launcherScript.absolutePath, "/bin/sh", "-c", "echo LAUNCHER_OK")
            .redirectErrorStream(true)
            .start()
        val selfTestOutput = selfTestProc.inputStream.bufferedReader().readText().trim()
        if (selfTestProc.waitFor() != 0 || selfTestOutput != "LAUNCHER_OK") {
            logStage("FAILURE: CRITICAL: Launcher self-test failed: $selfTestOutput")
            throw RuntimeException("CRITICAL: Launcher self-test failed: $selfTestOutput")
        }

        val guestTest = ProcessBuilder(launcherScript.absolutePath, "/bin/sh", "-c", "echo READY")
            .redirectErrorStream(true)
            .start()
        val guestOutput = guestTest.inputStream.bufferedReader().readText().trim()
        if (guestTest.waitFor() != 0 || guestOutput != "READY") {
            logStage("FAILURE: CRITICAL: Guest shell verification failed: $guestOutput")
            throw RuntimeException("CRITICAL: Guest shell verification failed: $guestOutput")
        }

        val interactiveTest = ProcessBuilder(launcherScript.absolutePath, "/bin/sh", "-i", "-c", "echo INTERACTIVE_OK")
            .redirectErrorStream(true)
            .start()
        val interactiveOutput = interactiveTest.inputStream.bufferedReader().readText().trim()
        if (interactiveTest.waitFor() != 0 || !interactiveOutput.contains("INTERACTIVE_OK")) {
            logStage("FAILURE: CRITICAL: Guest interactive shell validation failed: $interactiveOutput")
            throw RuntimeException("CRITICAL: Guest interactive shell validation failed: $interactiveOutput")
        }
        logStage("SUCCESS: Guest environment verified (Alpine sh is active and interactive context is fully stable).")

        logStage("Stage 8/8: Verifying Git engine hooks...")
        _setupState.value = SetupState.Progress("Verifying Git...", 95)
        
        val gitPathCheck = ProcessBuilder(launcherScript.absolutePath, "/bin/sh", "-c", "command -v git")
            .redirectErrorStream(true)
            .start()
        val hasGit = gitPathCheck.waitFor() == 0

        var gitWorking = false
        if (hasGit) {
            val gitVersionCheck = ProcessBuilder(launcherScript.absolutePath, "git", "--version")
                .redirectErrorStream(true)
                .start()
            gitWorking = gitVersionCheck.waitFor() == 0
        }

        if (!gitWorking) {
            logStage("INF: Git missing or non-functional in guest. Verifying package manager structure...")
            
            val apkFile = File(rootfsDir, "sbin/apk")
            val apkUsrFile = File(rootfsDir, "usr/sbin/apk")
            val guestBusybox = File(rootfsDir, "bin/busybox")
            
            if (!apkFile.exists() && !apkUsrFile.exists() && !guestBusybox.exists()) {
                logStage("FAILURE: CRITICAL: Rootfs layout structural corruption detected. Package manager implementation tool (apk) missing.")
                throw RuntimeException("CRITICAL: Rootfs layout structural corruption detected. Package manager implementation tool (apk) missing.")
            }

            executeInLauncher(launcherScript, "/bin/sh", "-c", "apk update && apk add git")
            
            val postGitCheck = ProcessBuilder(launcherScript.absolutePath, "/bin/sh", "-c", "command -v git && git --version")
                .redirectErrorStream(true)
                .start()
            if (postGitCheck.waitFor() != 0) {
                logStage("FAILURE: CRITICAL: Git installation completed but engine runtime checks are still failing.")
                throw RuntimeException("CRITICAL: Git installation completed but engine runtime checks are still failing.")
            }
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
        logStage("DIAGNOSTIC: Initiating remote connection connection sequence.")
        logStage("DIAGNOSTIC: Target Remote URL Path: $urlString")
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()
        logStage("DIAGNOSTIC: Received connection response status code code: ${connection.responseCode}")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            logStage("FAILURE: Mirror connection failed code: ${connection.responseCode}")
            throw RuntimeException("Mirror connection failed code: ${connection.responseCode}")
        }
        val expectedLength = connection.contentLengthLong
        logStage("DIAGNOSTIC: Metadata header tracking evaluation stream length: $expectedLength bytes")
        connection.inputStream.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
        logStage("DIAGNOSTIC: Output local layout target length confirmation: ${outputFile.length()} bytes")
    }

    private fun extractTarGz(tarFile: File, outputDir: File) {
        logStage("DIAGNOSTIC: Core system execution framework evaluation.")
        logStage("DIAGNOSTIC: Absolute storage container execution path: ${tarFile.absolutePath}")
        logStage("DIAGNOSTIC: Storage container footprint check status flag: ${tarFile.exists()}")
        
        var parsedCounter = 0
        var directoryMetrics = 0
        var fileMetrics = 0
        var symbolicMetrics = 0
        var linkMetrics = 0

        try {
            val fileStream = tarFile.inputStream()
            val zipDecoderStream = GZIPInputStream(fileStream)
            logStage("DIAGNOSTIC: Standard GZip protocol processing wrapper open logic verified.")
            
            val archiveInputStream = TarArchiveInputStream(zipDecoderStream)
            while (true) {
                val entry: TarArchiveEntry? = try {
                    archiveInputStream.nextTarEntry
                } catch (streamError: Exception) {
                    val traceOutput = StringWriter()
                    streamError.printStackTrace(PrintWriter(traceOutput))
                    logStage("FAILURE: Tar stream decoding corrupted sequentially at offset record #$parsedCounter.")
                    logStage("Exception Type Reference: ${streamError.javaClass.name}")
                    logStage("Exception Context message: ${streamError.message}")
                    logStage("Call sequence raw trace metadata:\n$traceOutput")
                    throw streamError
                }

                if (entry == null) break
                parsedCounter++

                val payloadName = entry.name
                val structuralCheckDir = entry.isDirectory
                val structuralCheckSym = entry.isSymbolicLink
                val structuralCheckLnk = entry.isLink

                val nodeTypeDescription = when {
                    structuralCheckDir -> { directoryMetrics++; "DIRECTORY" }
                    structuralCheckSym -> { symbolicMetrics++; "SYMBOLIC_LINK" }
                    structuralCheckLnk -> { linkMetrics++; "HARD_LINK" }
                    else -> { fileMetrics++; "REGULAR_FILE" }
                }

                logStage("PROCESSING: Item #$parsedCounter Name Target: $payloadName Type: $nodeTypeDescription Size Metric Value: ${entry.size} bytes")

                try {
                    val targetDiskFile = File(outputDir, payloadName)
                    if (structuralCheckDir) {
                        targetDiskFile.mkdirs()
                    } else if (structuralCheckSym) {
                        val symlinkReferencePath = entry.linkName
                        if (targetDiskFile.exists()) targetDiskFile.delete()
                        targetDiskFile.parentFile?.mkdirs()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            java.nio.file.Files.createSymbolicLink(targetDiskFile.toPath(), java.nio.file.Paths.get(symlinkReferencePath))
                        }
                    } else if (structuralCheckLnk) {
                        val hardlinkReferencePath = entry.linkName
                        val referenceSourceFile = File(outputDir, hardlinkReferencePath)
                        if (targetDiskFile.exists()) targetDiskFile.delete()
                        targetDiskFile.parentFile?.mkdirs()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && referenceSourceFile.exists()) {
                            java.nio.file.Files.createLink(targetDiskFile.toPath(), referenceSourceFile.toPath())
                        }
                    } else {
                        if (targetDiskFile.exists()) targetDiskFile.delete()
                        targetDiskFile.parentFile?.mkdirs()
                        FileOutputStream(targetDiskFile).use { fileOutputStream ->
                            archiveInputStream.copyTo(fileOutputStream)
                        }
                        val accessFlagsMode = entry.mode
                        if ((accessFlagsMode and 73) != 0) {
                            targetDiskFile.setExecutable(true, false)
                        }
                    }
                } catch (entryException: Exception) {
                    val errorDumpStream = StringWriter()
                    entryException.printStackTrace(PrintWriter(errorDumpStream))
                    logStage("CRITICAL ITEM EXTRACTION EXCEPTION REPORT:")
                    logStage("Target Node Element Name: $payloadName")
                    logStage("Target Node Type Context: $nodeTypeDescription")
                    logStage("Exception Trapped Signature Class: ${entryException.javaClass.name}")
                    logStage("Exception Message Statement Context: ${entryException.message}")
                    logStage("Cause Trace Class Type Context: ${entryException.cause?.javaClass?.name}")
                    logStage("Cause Context Message String Text: ${entryException.cause?.message}")
                    logStage("Complete Nested Object Footprint Tree Sequence Trace:\n$errorDumpStream")
                    throw entryException
                }
            }
            archiveInputStream.close()
            logStage("EXTRACTION COMPLETE METRICS EVALUATION: Evaluated Items=$parsedCounter, Extracted Files=$fileMetrics, Created Directories=$directoryMetrics, Symlinks Deployed=$symbolicMetrics, Hardlinks Synced=$linkMetrics")
        } catch (globalFaultException: Exception) {
            val globalErrorDumpStream = StringWriter()
            globalFaultException.printStackTrace(PrintWriter(globalErrorDumpStream))
            logStage("GLOBAL PIPELINE EXTRACTION PROCESSING ABORTED:")
            logStage("Global Exception Trapped Signature Class: ${globalFaultException.javaClass.name}")
            logStage("Global Exception Message Statement Context: ${globalFaultException.message}")
            logStage("Global Cause Trace Class Type Context: ${globalFaultException.cause?.javaClass?.name}")
            logStage("Global Cause Context Message String Text: ${globalFaultException.cause?.message}")
            logStage("Global Structural Execution Footprint Tree Sequence Trace:\n$globalErrorDumpStream")
            throw globalFaultException
        }

        if (tarFile.exists()) {
            tarFile.delete()
        }
    }

    private fun writeLauncherScript(baseDir: File, binDir: File, proot: File, rootfs: File) {
        val launcherScript = File(binDir, "proot_launch.sh")
        val prootPath = proot.absolutePath
        val baseDirPath = baseDir.absolutePath
        val homePath = File(baseDir, "home").absolutePath

        val script = StringBuilder()
        script.append("#!/system/bin/sh\n")
        script.append("export HOME=/home/goydevv\n")
        script.append("export PATH=/usr/bin:/bin:/usr/sbin:/sbin\n")
        script.append("export TERM=xterm-256color\n")
        script.append("export LANG=C.UTF-8\n\n")
        
        script.append("if [ \$# -eq 0 ]; then\n")
        script.append("    set -- /bin/sh\n")
        script.append("else\n")
        script.append("    set -- \"\$@\"\n")
        script.append("fi\n\n")
        
        script.append("exec \"").append(prootPath).append("\" \\\n")
        
        val args = getBaseProotArgs(rootfs)
        var i = 0
        while (i < args.size) {
            val key = args[i]
            if (key == "-0") {
                script.append("    -0 \\\n")
                i += 1
            } else {
                val value = args[i + 1]
                script.append("    ").append(key).append(" \"").append(value).append("\" \\\n")
                i += 2
            }
        }
        script.append("    \"\$@\"\n")

        launcherScript.writeText(script.toString())
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
