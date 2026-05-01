package io.github.cococraft.puckradiosync.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.github.cococraft.puckradiosync.shared.HRS_DEFAULT_CONTROL_PORT
import io.github.cococraft.puckradiosync.shared.HRS_SERVICE_TYPE
import java.util.concurrent.Executor

class RemoteDiscovery(
    private val context: Context,
    private val mainExecutor: Executor,
    private val onEvent: (RemoteDiscoveryEvent) -> Unit,
) {
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (discoveryListener != null) return

        val manager = context.getSystemService(NsdManager::class.java)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                onEvent(RemoteDiscoveryEvent.Searching)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != HRS_SERVICE_TYPE) return
                onEvent(RemoteDiscoveryEvent.Found(serviceInfo.serviceName))
                resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        onEvent(RemoteDiscoveryEvent.ResolveFailed(errorCode))
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val resolvedHost = resolvedHostAddress(info) ?: return
                        val resolvedPort = if (info.port > 0) info.port else HRS_DEFAULT_CONTROL_PORT
                        onEvent(RemoteDiscoveryEvent.Resolved(info.serviceName, resolvedHost, resolvedPort))
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                onEvent(RemoteDiscoveryEvent.Lost)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onEvent(RemoteDiscoveryEvent.Failed(errorCode))
                stop()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stop()
            }
        }

        nsdManager = manager
        discoveryListener = listener
        runCatching {
            manager.discoverServices(HRS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { error ->
            discoveryListener = null
            nsdManager = null
            onEvent(RemoteDiscoveryEvent.Unavailable(error.message.orEmpty()))
        }
    }

    fun stop() {
        val manager = nsdManager
        val listener = discoveryListener
        if (manager != null && listener != null) {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
        nsdManager = null
    }

    private fun resolveService(
        serviceInfo: NsdServiceInfo,
        listener: NsdManager.ResolveListener,
    ) {
        val manager = nsdManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    listener.onResolveFailed(serviceInfo, errorCode)
                }

                override fun onServiceInfoCallbackUnregistered() = Unit

                override fun onServiceLost() {
                    listener.onResolveFailed(serviceInfo, NsdManager.FAILURE_INTERNAL_ERROR)
                    runCatching { manager.unregisterServiceInfoCallback(this) }
                }

                override fun onServiceUpdated(info: NsdServiceInfo) {
                    listener.onServiceResolved(info)
                    runCatching { manager.unregisterServiceInfoCallback(this) }
                }
            }
            manager.registerServiceInfoCallback(serviceInfo, mainExecutor, callback)
            return
        }

        NsdManager::class.java
            .getMethod("resolveService", NsdServiceInfo::class.java, NsdManager.ResolveListener::class.java)
            .invoke(manager, serviceInfo, listener)
    }

    private fun resolvedHostAddress(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return info.hostAddresses.firstOrNull()?.hostAddress
        }

        return runCatching {
            NsdServiceInfo::class.java.getMethod("getHost").invoke(info) as? java.net.InetAddress
        }.getOrNull()?.hostAddress
    }
}

sealed interface RemoteDiscoveryEvent {
    data object Searching : RemoteDiscoveryEvent
    data class Found(val serviceName: String) : RemoteDiscoveryEvent
    data class Resolved(val serviceName: String, val host: String, val port: Int) : RemoteDiscoveryEvent
    data class ResolveFailed(val errorCode: Int) : RemoteDiscoveryEvent
    data object Lost : RemoteDiscoveryEvent
    data class Failed(val errorCode: Int) : RemoteDiscoveryEvent
    data class Unavailable(val message: String) : RemoteDiscoveryEvent
}
