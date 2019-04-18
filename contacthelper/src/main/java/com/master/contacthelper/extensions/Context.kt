package com.master.contacthelper.extensions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.widget.Toast


fun Context.hasPermission(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
fun Context.hasContactPermissions() = hasPermission(Manifest.permission.READ_CONTACTS) && hasPermission(Manifest.permission.WRITE_CONTACTS)

fun Context.showErrorToast(exception: Exception, length: Int = Toast.LENGTH_LONG) {
    showErrorToast(exception.toString(), length)
}

fun Context.showErrorToast(msg: String, length: Int = Toast.LENGTH_LONG) {
    toast(String.format("Error Occoured", msg), length)
}

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    try {
        if (isOnMainThread()) {
            Toast.makeText(applicationContext, msg, length).show()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, msg, length).show()
            }
        }
    } catch (e: Exception) {
    }
}

fun isOnMainThread() = Looper.myLooper() == Looper.getMainLooper()