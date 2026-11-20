package com.atala.app.core.run

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The "while using the app" location permission (Atala/Services/LocationPermission.swift), read
 * outside of a run (the onboarding) and by the run flow. Asking needs an activity result launcher:
 * launch [permissions] with `ActivityResultContracts.RequestMultiplePermissions`, then call [refresh].
 */
object LocationPermission {
    enum class Status { NotDetermined, Allowed, Refused }

    /** What the run asks for: precise location (approximate is accepted). */
    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private val _status = MutableStateFlow(Status.NotDetermined)
    val status: StateFlow<Status> = _status.asStateFlow()

    val isAllowed: Boolean get() = _status.value == Status.Allowed
    val isRefused: Boolean get() = _status.value == Status.Refused

    private var asked = false

    /** Reads the current permission without asking anything. */
    fun refresh(context: Context) {
        val granted = permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        _status.value = when {
            granted -> Status.Allowed
            asked -> Status.Refused
            context is Activity && permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(context, it) } -> Status.Refused
            else -> Status.NotDetermined
        }
    }

    /** Called with the answer of the system dialog. */
    fun answered(context: Context) {
        asked = true
        refresh(context)
    }
}
