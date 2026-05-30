package com.goydevv.gitide.engine

import java.io.File

object GitEngine {

    private var prootBinary: File? = null
    private var rootfsDir: File? = null
    private var baseDir: File? = null

    fun initialize(filesDir: File) {
        baseDir = filesDir
        prootBinary = File(filesDir, "usr/bin/proot")
        rootfsDir = File(filesDir, "proot/rootfs")
    }

    fun execute(projectDir: File, args: List<String>): Result<String> {
        return try {
            val proot = prootBinary
            val rootfs = rootfsDir
            val base = baseDir

            val command = if (proot != null && proot.exists() && rootfs != null && rootfs.exists() && base != null) {
                listOf(
                    proot.absolutePath,
                    "-r", rootfs.absolutePath,
                    "-0",
                    "-b", "/dev",
                    "-b", "/proc",
                    "-b", "/sys",
                    "-b", "/sdcard",
                    "-b", "${base.absolutePath}:${base.absolutePath}", // Map internal storage
                    "-w", projectDir.absolutePath, // Working directory (same path works due to bind)
                    "/usr/bin/git"
                ) + args
            } else {
                listOf("git") + args
            }

            val process = ProcessBuilder()
                .command(command)
                .directory(projectDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = "/home/goydevv"
                    environment()["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin"
                    environment()["TERM"] = "xterm-256color"
                }
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Result.success(output.trim())
            } else {
                Result.failure(Exception(output.trim().ifBlank { "Git exited with code $exitCode" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun initRepository(projectDir: File): Result<String> {
        if (!projectDir.exists()) projectDir.mkdirs()
        return execute(projectDir, listOf("init"))
    }

    fun clone(repoUrl: String, targetDir: File, token: String? = null): Result<String> {
        if (!targetDir.exists()) targetDir.mkdirs()
        
        val authenticatedUrl = if (!token.isNullOrBlank() && repoUrl.startsWith("https://")) {
            repoUrl.replace("https://", "https://$token@")
        } else {
            repoUrl
        }

        return execute(targetDir, listOf("clone", authenticatedUrl, "."))
    }

    fun getStatus(projectDir: File): List<Pair<String, String>> {
        val result = execute(projectDir, listOf("status", "--porcelain"))
        val raw = result.getOrNull() ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        return raw.lines().filter { it.isNotBlank() }.map { line ->
            val state = line.substring(0, 2).trim()
            val file = line.substring(2).trim()
            Pair(file, state)
        }
    }

    fun getLog(projectDir: File, limit: Int = 30): List<String> {
        val result = execute(projectDir, listOf("log", "--oneline", "-n", limit.toString()))
        val raw = result.getOrNull() ?: return emptyList()
        return raw.lines().filter { it.isNotBlank() }
    }

    fun stageFiles(projectDir: File, filePaths: List<String> = listOf(".")): Result<String> {
        return execute(projectDir, listOf("add") + filePaths)
    }

    fun unstageFiles(projectDir: File, filePaths: List<String> = listOf(".")): Result<String> {
        return execute(projectDir, listOf("restore", "--staged") + filePaths)
    }

    fun discardChanges(projectDir: File, filePaths: List<String>): Result<String> {
        return execute(projectDir, listOf("restore") + filePaths)
    }

    fun commit(projectDir: File, message: String): Result<String> {
        if (message.isBlank()) return Result.failure(Exception("Commit message cannot be empty"))
        return execute(projectDir, listOf("commit", "-m", message))
    }

    fun push(projectDir: File, remote: String = "origin", branch: String = "main"): Result<String> {
        return execute(projectDir, listOf("push", remote, branch))
    }

    fun pull(projectDir: File, remote: String = "origin", branch: String = "main"): Result<String> {
        return execute(projectDir, listOf("pull", remote, branch))
    }

    fun fetch(projectDir: File, remote: String = "origin"): Result<String> {
        return execute(projectDir, listOf("fetch", remote))
    }

    fun getCurrentBranch(projectDir: File): String {
        val result = execute(projectDir, listOf("branch", "--show-current"))
        return result.getOrNull()?.ifBlank { "detached" } ?: "main"
    }

    fun getBranches(projectDir: File, all: Boolean = false): List<String> {
        val args = mutableListOf("branch", "--format=%(refname:short)")
        if (all) args.add("-a")
        val result = execute(projectDir, args)
        val raw = result.getOrNull() ?: return emptyList()
        return raw.lines().filter { it.isNotBlank() }.map { it.trim() }
    }

    fun checkout(projectDir: File, target: String, createNew: Boolean = false): Result<String> {
        val args = mutableListOf("checkout")
        if (createNew) args.add("-b")
        args.add(target)
        return execute(projectDir, args)
    }

    fun deleteBranch(projectDir: File, branchName: String, force: Boolean = false): Result<String> {
        val flag = if (force) "-D" else "-d"
        return execute(projectDir, listOf("branch", flag, branchName))
    }

    fun merge(projectDir: File, branchName: String): Result<String> {
        return execute(projectDir, listOf("merge", branchName))
    }

    fun rebase(projectDir: File, branchName: String): Result<String> {
        return execute(projectDir, listOf("rebase", branchName))
    }

    fun stashSave(projectDir: File, message: String? = null): Result<String> {
        val args = mutableListOf("stash", "push")
        if (!message.isNullOrBlank()) {
            args.add("-m")
            args.add(message)
        }
        return execute(projectDir, args)
    }

    fun stashPop(projectDir: File, index: Int = 0): Result<String> {
        return execute(projectDir, listOf("stash", "pop", "stash@{$index}"))
    }

    fun stashList(projectDir: File): List<String> {
        val result = execute(projectDir, listOf("stash", "list"))
        val raw = result.getOrNull() ?: return emptyList()
        return raw.lines().filter { it.isNotBlank() }
    }

    fun stashDrop(projectDir: File, index: Int = 0): Result<String> {
        return execute(projectDir, listOf("stash", "drop", "stash@{$index}"))
    }

    fun reset(projectDir: File, mode: String = "--mixed", commitHash: String = "HEAD"): Result<String> {
        return execute(projectDir, listOf("reset", mode, commitHash))
    }

    fun getDiff(projectDir: File, filePath: String? = null): Result<String> {
        val args = mutableListOf("diff")
        if (!filePath.isNullOrBlank()) args.add(filePath)
        return execute(projectDir, args)
    }

    fun addRemote(projectDir: File, name: String, url: String): Result<String> {
        return execute(projectDir, listOf("remote", "add", name, url))
    }

    fun removeRemote(projectDir: File, name: String): Result<String> {
        return execute(projectDir, listOf("remote", "remove", name))
    }

    fun getRemotes(projectDir: File): List<String> {
        val result = execute(projectDir, listOf("remote", "-v"))
        val raw = result.getOrNull() ?: return emptyList()
        return raw.lines().filter { it.isNotBlank() }.distinct()
    }

    fun setConfig(projectDir: File?, key: String, value: String, global: Boolean = false): Result<String> {
        val args = mutableListOf("config")
        if (global) args.add("--global")
        args.addAll(listOf(key, value))
        return execute(projectDir ?: File("/"), args)
    }
}
