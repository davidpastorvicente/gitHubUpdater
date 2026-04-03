package com.davidpv.githubupdater.install

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.davidpv.githubupdater.data.model.AppAction

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let(AppAction::valueOf)
            ?: AppAction.Install

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                pendingUserActionIntent(intent)?.let { confirmationIntent ->
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (isAppInForeground(context)) {
                        context.startActivity(confirmationIntent)
                    } else {
                        showInstallNotification(context, confirmationIntent, packageName)
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                recentlyCancelledPackages.remove(packageName)
                val cleanupFailed = if (intent.getBooleanExtra(EXTRA_DELETE_APK_AFTER_INSTALL, false)) {
                    deleteDownloadedApk(context, intent.getStringExtra(EXTRA_APK_URI))
                } else {
                    false
                }
                InstallResultEvents.emit(
                    InstallResultEvent(
                        packageName = packageName,
                        status = InstallResultStatus.Success,
                        action = action,
                        cleanupFailed = cleanupFailed,
                    ),
                )
            }

            else -> {
                val resultStatus = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                    InstallResultStatus.Cancelled
                } else {
                    InstallResultStatus.Failed
                }
                if (resultStatus == InstallResultStatus.Cancelled) {
                    if (!recentlyCancelledPackages.add(packageName)) return
                }
                val failureMessage = if (resultStatus == InstallResultStatus.Failed) {
                    installFailureMessage(intent)
                } else {
                    null
                }
                InstallResultEvents.emit(
                    InstallResultEvent(
                        packageName = packageName,
                        status = resultStatus,
                        action = action,
                        failureMessage = failureMessage,
                    ),
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun pendingUserActionIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    private fun showInstallNotification(context: Context, confirmationIntent: Intent, packageName: String) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            confirmationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Ready to install")
            .setContentText("Tap to confirm installation")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(INSTALL_CONFIRM_NOTIFICATION_ID + packageName.hashCode(), notification)
    }

    private fun isAppInForeground(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java)
        val appProcesses = am.runningAppProcesses ?: return false
        return appProcesses.any {
            it.processName == context.packageName &&
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }

    private companion object {
        const val EXTRA_PACKAGE_NAME = "install_package_name"
        const val EXTRA_APK_URI = "install_apk_uri"
        const val EXTRA_DELETE_APK_AFTER_INSTALL = "install_delete_apk_after_install"
        const val EXTRA_ACTION = "install_action"
        const val INSTALL_CONFIRM_NOTIFICATION_ID = 0x1000_0000
        val recentlyCancelledPackages = mutableSetOf<String>()
    }

    private fun installFailureMessage(intent: Intent): String {
        val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        return when {
            rawMessage.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE", ignoreCase = true) ||
                rawMessage.contains("not enough space", ignoreCase = true) ||
                rawMessage.contains("insufficient storage", ignoreCase = true) -> {
                "Not enough storage space to install this APK."
            }

            rawMessage.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
                rawMessage.contains("signatures do not match", ignoreCase = true) -> {
                "This APK uses a different signature than the installed app. Uninstall the current app first, then install this APK."
            }

            rawMessage.isNotBlank() -> rawMessage
            else -> "Android rejected the APK installation."
        }
    }

    private fun deleteDownloadedApk(context: Context, apkUri: String?): Boolean {
        val uri = apkUri?.let(Uri::parse) ?: return true
        return try {
            context.contentResolver.delete(uri, null, null)
            false
        } catch (_: SecurityException) {
            true
        } catch (_: IllegalArgumentException) {
            true
        }
    }
}
