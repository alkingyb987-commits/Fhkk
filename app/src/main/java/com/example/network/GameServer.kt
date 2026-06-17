package com.example.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

class GameServer(
    private val onConnectionStatusChanged: (Boolean, String?) -> Unit,
    private val onMessageReceived: (String) -> Unit
) {
    private val TAG = "GameServer"
    private val PORT = 8888

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outWriter: PrintWriter? = null
    private var readerJob: Job? = null
    private var serverJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Starts hosting a game.
     */
    fun startHosting() {
        stopAll()
        serverJob = scope.launch {
            try {
                Log.d(TAG, "Starting socket server on port $PORT...")
                serverSocket = ServerSocket(PORT).apply {
                    soTimeout = 0 // Wait indefinitely
                }
                onConnectionStatusChanged(false, "Waiting for coordinates...")

                val socket = serverSocket?.accept()
                if (socket != null) {
                    clientSocket = socket
                    outWriter = PrintWriter(socket.getOutputStream(), true)
                    Log.d(TAG, "Client connected: ${socket.inetAddress.hostAddress}")
                    
                    onConnectionStatusChanged(true, socket.inetAddress.hostAddress)
                    startListening(socket)
                    // Inform the connected client
                    sendMessage("WELCOME_HOST")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting server: ${e.message}")
                onConnectionStatusChanged(false, null)
            }
        }
    }

    /**
     * Connects to a Host IP.
     */
    fun connectToHost(hostIp: String) {
        stopAll()
        serverJob = scope.launch {
            try {
                Log.d(TAG, "Connecting to host $hostIp on $PORT...")
                onConnectionStatusChanged(false, "Locking target IP...")
                
                // Try to connect with a 5-second timeout
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(hostIp, PORT), 5000)
                clientSocket = socket
                outWriter = PrintWriter(socket.getOutputStream(), true)

                Log.d(TAG, "Successfully connected to host: $hostIp")
                onConnectionStatusChanged(true, hostIp)
                startListening(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to host: ${e.message}")
                onConnectionStatusChanged(false, "Host offline or wrong IP")
            }
        }
    }

    private fun startListening(socket: Socket) {
        readerJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (socket.isConnected && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    Log.d(TAG, "Socket Recv: $line")
                    onMessageReceived(line)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket read error: ${e.message}")
            } finally {
                onConnectionStatusChanged(false, "Radar signal lost")
            }
        }
    }

    /**
     * Sends a raw message to the connected peer.
     */
    fun sendMessage(message: String) {
        scope.launch {
            try {
                outWriter?.println(message)
                Log.d(TAG, "Socket Sent: $message")
            } catch (e: Exception) {
                Log.e(TAG, "Socket write error: ${e.message}")
            }
        }
    }

    /**
     * Terminate server and clients.
     */
    fun stopAll() {
        readerJob?.cancel()
        serverJob?.cancel()
        try {
            outWriter?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connections: ${e.message}")
        }
        clientSocket = null
        serverSocket = null
        outWriter = null
    }

    /**
     * Utility to fetch device's local IP address to show in UI.
     */
    @SuppressLint("DefaultLocale")
    fun getLocalIpAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            Formatter.formatIpAddress(ipAddress)
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }
}
