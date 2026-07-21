package com.chandailx.coderit

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * One-tap Termux launcher.
 *
 * Termux:API/Termux itself exposes a documented explicit-intent command,
 * `com.termux.RUN_COMMAND` (handled by `com.termux.app.RunCommandService`),
 * that lets another app ask Termux to run a shell command WITHOUT the user
 * manually opening Termux and typing anything.
 *
 * This requires two things that only the person (not this code, not
 * Android) can grant, one time:
 *   1. Termux's own setting `allow-external-apps=true` in
 *      ~/.termux/termux.properties (off by default, for security).
 *   2. The normal Android runtime permission prompt the first time this
 *      fires (Termux shows a permission dialog the first time).
 *
 * This is NOT a way to bypass Android's app sandboxing — it is Termux's
 * own supported extension point for exactly this use case, and Termux
 * still fully controls whether to allow it.
 */
class MainActivity : FlutterActivity() {
    private val CHANNEL = "coderit/termux_launcher"
    private val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    private val PERMISSION_REQUEST_CODE = 7738

    // Stashed while we wait for the runtime permission dialog result, so we
    // can finish the original launchServerCommand call once the person
    // answers the system prompt.
    private var pendingResult: MethodChannel.Result? = null
    private var pendingScript: String? = null
    private var pendingWorkDir: String? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "isTermuxInstalled" -> result.success(isPackageInstalled("com.termux"))
                "launchServerCommand" -> {
                    val script = call.argument<String>("script") ?: ""
                    val workDir = call.argument<String>("workDir") ?: "/data/data/com.termux/files/home"
                    handleLaunchServerCommand(script, workDir, result)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Termux is installed but not reachable via RUN_COMMAND: on Android 6+
     * that intent also requires the "com.termux.permission.RUN_COMMAND"
     * *runtime* permission (Termux declares it as a dangerous-level
     * permission, so a manifest declaration alone is NOT enough — the
     * person has to grant it in a system popup, once). Without this, the
     * old code hit a SecurityException on startService(), which the catch
     * block silently swallowed and reported back as if Termux weren't
     * installed at all — very confusing when it clearly is. Now we check
     * for the permission first and request it if missing, instead of
     * letting it fail silently.
     */
    private fun handleLaunchServerCommand(script: String, workDir: String, result: MethodChannel.Result) {
        if (!isPackageInstalled("com.termux")) {
            result.success(false)
            return
        }

        val granted = ContextCompat.checkSelfPermission(this, RUN_COMMAND_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            result.success(launchRunCommand(script, workDir))
            return
        }

        // Not granted yet: ask for it now and finish this call once the
        // person responds to the system dialog (see onRequestPermissionsResult).
        pendingResult = result
        pendingScript = script
        pendingWorkDir = workDir
        ActivityCompat.requestPermissions(this, arrayOf(RUN_COMMAND_PERMISSION), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST_CODE) return

        val result = pendingResult
        val script = pendingScript
        val workDir = pendingWorkDir
        pendingResult = null
        pendingScript = null
        pendingWorkDir = null

        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted && script != null && workDir != null) {
            result?.success(launchRunCommand(script, workDir))
        } else {
            // Permission denied — report false via the normal path. The
            // person will see "could not reach Termux"; if this keeps
            // happening they've likely denied it permanently and need to
            // grant it manually in Android Settings > Apps > CoderIt >
            // Permissions (it won't show a popup again after "Deny").
            result?.success(false)
        }
    }

    /**
     * Fires the RUN_COMMAND intent. [script] should be a single shell
     * command string; Termux runs it as `sh -c "<script>"` inside
     * [workDir]. Returns false only if Termux isn't installed or the
     * intent couldn't be sent at all — it does NOT mean the command
     * itself succeeded (that's checked separately via the /health poll).
     * Assumes the RUN_COMMAND permission has already been confirmed
     * granted by the caller.
     */
    private fun launchRunCommand(script: String, workDir: String): Boolean {
        if (!isPackageInstalled("com.termux")) return false
        return try {
            val intent = Intent()
            intent.setClassName("com.termux", "com.termux.app.RunCommandService")
            intent.action = "com.termux.RUN_COMMAND"
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", workDir)
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            startService(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
