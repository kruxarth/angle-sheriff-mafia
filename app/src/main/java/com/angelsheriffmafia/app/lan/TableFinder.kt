package com.angelsheriffmafia.app.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.angelsheriffmafia.app.ui.screens.FoundTable
import com.angelsheriffmafia.lan.APP_ID
import com.angelsheriffmafia.lan.LanClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/** Finds tables hosted on this Wi-Fi or hotspot, as they come and go. */
class TableFinder(context: Context) {
    private val nsd: NsdManager? = context.applicationContext.getSystemService(NsdManager::class.java)

    fun tables(): Flow<List<FoundTable>> = callbackFlow {
        val nsd = nsd ?: run {
            awaitClose()
            return@callbackFlow
        }
        val tables = LinkedHashMap<String, FoundTable>()
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val lost = Channel<String>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                found.trySend(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                lost.trySend(info.serviceName)
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        launch {
            for (name in lost) {
                if (tables.remove(name) != null) send(tables.values.toList())
            }
        }
        launch {
            // One at a time: older Android versions resolve only one service at once.
            for (info in found) {
                val table = resolve(nsd, info) ?: continue
                tables[info.serviceName] = table
                send(tables.values.toList())
            }
        }
        val started = runCatching { nsd.discoverServices(NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.isSuccess
        awaitClose {
            if (started) runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): FoundTable? {
        val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                val listener = object : NsdManager.ResolveListener {
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(serviceInfo)
                    }

                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                // Deprecated from Android 14 in favour of service info callbacks, but still works everywhere.
                @Suppress("DEPRECATION")
                val ok = runCatching { nsd.resolveService(info, listener) }.isSuccess
                if (!ok && cont.isActive) cont.resume(null)
            }
        } ?: return null
        @Suppress("DEPRECATION")
        val host = resolved.host as? Inet4Address ?: return null
        val address = "${host.hostAddress}:${resolved.port}"
        // Ask the table itself how full it is. If it doesn't answer, list it anyway.
        val hello = withContext(Dispatchers.IO) {
            runCatching { LanClient("http://$address").hello() }.getOrNull()
        }
        if (hello != null && hello.app != APP_ID) return null
        return FoundTable(
            name = hello?.table ?: info.serviceName,
            address = address,
            players = hello?.players,
            inGame = hello?.inGame == true,
        )
    }

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 5_000L
    }
}
