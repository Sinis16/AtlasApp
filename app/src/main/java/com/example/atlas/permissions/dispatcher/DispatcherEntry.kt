package com.example.atlas.permissions.dispatcher

import com.example.atlas.permissions.dispatcher.dsl.PermissionDispatcher

class DispatcherEntry(
    internal val dispatcher: RequestResultsDispatcher, val requestCode: Int
) : PermissionDispatcher() {
    private lateinit var permissions: Array<out String>
    var onGranted: () -> Unit = {}
        internal set
    var onDenied: () -> Unit = {}
        internal set
    var onShowRationale: ((List<String>, requestCode: Int) -> Unit)? = null
        internal set

    internal fun setPermissions(permissions: Array<out String>) {
        this.permissions = permissions
    }

    internal fun getPermissions(): Array<out String> = permissions
}