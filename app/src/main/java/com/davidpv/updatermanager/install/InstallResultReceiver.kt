package com.davidpv.updatermanager.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
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
                InstallResultEvents.emit(
                    InstallResultEvent(packageName = packageName, status = InstallResultStatus.Success),
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
    }
}
