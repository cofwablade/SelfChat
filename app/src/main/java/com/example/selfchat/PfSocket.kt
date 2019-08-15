package com.example.selfchat

import java.util.*

open class PfSocket : Any() {

    init {

    }

    open fun send( data : ByteArray, len : Int = 0 ) : Int
    {
        return -1
    }

    open fun recv( data : ByteArray, len : Int = 0, timeout_msec : Int = -1 ) : Int
    // timeout_msec : -1:blocking, 0:non-blocking, >0:timeout(milliseconds)
    // only use in OnReceived Listener
    {
        return -1
    }

    open fun close()
    {

    }

    open fun isAlive() : Boolean
    {
        return false
    }

    class Listener {

        init {

        }
        var onConnected: ((Boolean)->Unit)? = null
        var onDisconnected: ((Unit)->Unit)? = null
        var onReceived: ((PfSocket)->Unit)? = null
    }

    val mListener = Listener()

    fun setOnConnectedListener( listener : (Boolean) -> Unit )
    {
        mListener.onConnected = listener
    }

    fun setOnDisconnectedListener( listener : ( Unit ) -> Unit )
    {
        mListener.onDisconnected = listener
    }

    fun setOnReceivedListener( listener : (PfSocket) -> Unit )
    {
        mListener.onReceived = listener
    }
}