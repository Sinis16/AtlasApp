package com.example.atlas.permissions

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.example.atlas.permissions.dispatcher.RequestResultsDispatcher
import com.example.atlas.permissions.dispatcher.dsl.PermissionDispatcherDsl

class PermissionManager(val activity: Activity) {
    lateinit var dispatcher: RequestResultsDispatcher
        private set

    @PermissionDispatcherDsl
    infix fun buildRequestResultsDispatcher(init: RequestResultsDispatcher.() -> Unit) {
        dispatcher = RequestResultsDispatcher(this)
        dispatcher.apply(init)
    }

    infix fun checkRequestAndDispatch(requestCode: Int) {
        checkRequestAndDispatch(requestCode, false)
    }

    internal fun checkRequestAndDispatch(requestCode: Int, comingFromRationale: Boolean = false) {
        val permissionsNotGranted = dispatcher.getPermissions(requestCode)?.filter { permission ->
            ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }?.toTypedArray() ?: throw UnhandledRequestCodeException(requestCode, activity)

        if (permissionsNotGranted.isEmpty()) {
            dispatcher.dispatchOnGranted(requestCode)
        } else {
            dispatchSomePermissionsNotGranted(permissionsNotGranted, requestCode, comingFromRationale)
        }
    }

    private fun dispatchSomePermissionsNotGranted(
        permissionsNotGranted: Array<out String>,
        requestCode: Int,
        comingFromRationale: Boolean
    ) {
        val permissionsRequiringRationale =
            if (!comingFromRationale) getPermissionsRequiringRationale(permissionsNotGranted) else emptyList()

        if (permissionsRequiringRationale.isNotEmpty()) {
            dispatcher.showRationale(requestCode, permissionsRequiringRationale)
        } else {
            ActivityCompat.requestPermissions(activity, permissionsNotGranted, requestCode)
        }
    }

    private fun getPermissionsRequiringRationale(permissionsNotGranted: Array<out String>) =
        permissionsNotGranted.filter { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }.toList()

    fun dispatchOnRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        dispatcher.dispatchAction(requestCode, grantResults)?.invoke()
    }
}

class UnhandledRequestCodeException(requestCode: Int, context: Context) : Throwable() {
    override val message: String = "Unhandled request code: $requestCode"
}