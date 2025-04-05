package com.example.atlas.permissions.dispatcher.dsl

import com.example.atlas.permissions.dispatcher.DispatcherEntry
import com.example.atlas.permissions.dispatcher.RequestResultsDispatcher

@DslMarker
annotation class PermissionDispatcherDsl

@PermissionDispatcherDsl
abstract class PermissionDispatcher

@PermissionDispatcherDsl
infix fun DispatcherEntry.checkPermissions(permissions: Array<out String>) {
    this.setPermissions(permissions)
}

@PermissionDispatcherDsl
fun DispatcherEntry.doOnGranted(onGranted: () -> Unit) {
    this.onGranted = onGranted
}

@PermissionDispatcherDsl
fun DispatcherEntry.doOnDenied(onDenied: () -> Unit) {
    this.onDenied = onDenied
}

@PermissionDispatcherDsl
fun DispatcherEntry.rationale(onShowRationale: (List<String>, Int) -> Unit) {
    this.onShowRationale = onShowRationale
}

@PermissionDispatcherDsl
fun DispatcherEntry.showRationaleDialog(
    message: String,
    onDismiss: () -> Unit = {}
) {
    rationale { permissions, requestCode ->
        // Pass the rationale request back to the caller (CardConnectionScreen will handle the UI)
        dispatcher.manager.activity.runOnUiThread {
            dispatcher.manager.dispatcher.showRationale(requestCode, permissions)
        }
    }
}

@PermissionDispatcherDsl
fun RequestResultsDispatcher.withRequestCode(
    requestCode: Int, init: DispatcherEntry.() -> Unit
) {
    entries[requestCode] = DispatcherEntry(manager.dispatcher, requestCode).apply(init)
}