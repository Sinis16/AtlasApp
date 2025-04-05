package com.example.atlas.permissions.dispatcher

import android.content.pm.PackageManager
import com.example.atlas.permissions.PermissionManager
import com.example.atlas.permissions.dispatcher.dsl.PermissionDispatcher

class RequestResultsDispatcher(internal val manager: PermissionManager) : PermissionDispatcher() {
    internal var entries: MutableMap<Int, DispatcherEntry> = mutableMapOf()

    internal fun dispatchAction(requestCode: Int, grantResults: IntArray): (() -> Unit)? =
        when (checkGrantResults(grantResults)) {
            true -> entries[requestCode]?.onGranted
            false -> entries[requestCode]?.onDenied
        }

    internal fun getPermissions(requestCode: Int): Array<out String>? =
        entries[requestCode]?.getPermissions()

    internal fun dispatchOnGranted(requestCode: Int) {
        entries[requestCode]?.onGranted?.invoke()
    }

    internal fun showRationale(requestCode: Int, permissions: List<String>) {
        entries[requestCode]?.onShowRationale?.invoke(permissions, requestCode)
            ?: manager.checkRequestAndDispatch(requestCode, true)
    }

    private fun checkGrantResults(grantResults: IntArray): Boolean {
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }
}