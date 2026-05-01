package io.github.habsradiosync.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.github.habsradiosync.shared.HRS_DEFAULT_CONTROL_PORT
import io.github.habsradiosync.shared.HRS_SERVICE_TYPE

class TvDiscoveryRegistration(private val context: Context) {
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun register() {
        if (registrationListener != null) return

        val manager = context.getSystemService(NsdManager::class.java)
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Habs Radio Sync"
            serviceType = HRS_SERVICE_TYPE
            port = HRS_DEFAULT_CONTROL_PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }

        nsdManager = manager
        registrationListener = listener
        manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun unregister() {
        val manager = nsdManager ?: return
        val listener = registrationListener ?: return
        runCatching { manager.unregisterService(listener) }
        registrationListener = null
        nsdManager = null
    }
}
