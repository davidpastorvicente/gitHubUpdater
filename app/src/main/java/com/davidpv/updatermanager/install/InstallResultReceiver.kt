package com.davidpv.updatermanager.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                pendingUserActionIntent(intent)?.let { confirmationIntent ->
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmationIntent)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                val cleanupFailed = if (intent.getBooleanExtra(EXTRA_DELETE_APK_AFTER_INSTALL, false)) {
                    deleteDownloadedApk(context, intent.getStringExtra(EXTRA_APK_URI))
                } else {
                    false
                }
                InstallResultEvents.emit(
                    InstallResultEvent(
                        packageName = packageName,
                        status = InstallResultStatus.Success,
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
                InstallResultEvents.emit(
                    InstallResultEvent(packageName = packageName, status = resultStatus),
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

    private companion object {
        const val EXTRA_PACKAGE_NAME = "install_package_name"
        const val EXTRA_APK_URI = "install_apk_uri"
        const val EXTRA_DELETE_APK_AFTER_INSTALL = "install_delete_apk_after_install"
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
