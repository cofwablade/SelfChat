package com.example.selfchat

import android.media.midi.MidiManager
import android.os.Handler
import android.os.HandlerThread
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PfSocketServer {

    val mLock = ReentrantLock()

    val mHThread = HandlerThread( "PfSocketServer" )

    var mSocket = ServerSocket()

    init {

    }

    val threadProc = object : Runnable {

        override fun run()
        {
            mLock.withLock() {
                mListener.onOpened?.invoke( mSocket.localPort )
            }

            // コネクト待ち
            while (!(mSocket.isClosed())) {

                try {

                    var socket = mSocket.accept()

                    var pfsocket = PfSocketSrv( socket )

                    mLock.withLock() {
                        mListener.onConnected?.invoke( pfsocket )
                    }

                } catch (e: Exception) {

                    continue
                }
            }

            mLock.withLock() {
                mListener.onClosed?.invoke( Unit )
            }
        }
    }

    fun open( port : Int )
    {
        val isa = InetSocketAddress( port )

        mSocket = ServerSocket()

        mSocket.bind( isa )

        if ( mSocket.isClosed() == true ) {

            mLock.withLock() {
                mListener.onOpened?.invoke(-1)
            }
            return
        }

        if (mHThread.isAlive() == false) {

            mHThread.start()
        }

        Handler(mHThread.looper).post(threadProc)
    }

    fun close( )
    {
        if( !mSocket.isBound() )
        {
            return
        }

        if( !mSocket.isClosed() )
        {
            mSocket.close()
        }
    }

    fun isAlive() : Boolean
    {
        if( mSocket.isBound() && !mSocket.isClosed() )
        {
            return true
        }

        return false
    }


    class Listener {

        init {

        }
        var onOpened: ((Int)->Unit)? = null
        var onClosed: ((Unit)->Unit)? = null
        var onConnected: ((PfSocket)->Unit)? = null
    }

    val mListener = Listener()

    fun setOnOpenedListener( listener : (Int)->Unit )
    {
        mListener.onOpened = listener
    }

    fun setOnClosedListener( listener : ( Unit ) -> Unit )
    {
        mListener.onClosed = listener
    }

    fun setOnConnectedListener( listener : (PfSocket) -> Unit)
    {
        mListener.onConnected = listener
    }
}
