package com.example.selfchat

import android.os.Handler
import android.os.HandlerThread
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PfSocketClient : PfSocket()
{
    companion object {
        // sendなどのスレッド( PfSocketClientで共用 )
        val mHThreadMisc = HandlerThread( "PfSocketClientMisc" )

        // mHThreadMiscスレッドとの同期用のフラグ
        val mFlagMisc = PfFlag()
    }

    // Locker
    val mLock = ReentrantLock()

    // 接続待ち受信待ちのスレッド
    val mHThread = HandlerThread( "PfSocketClient" )

    // mHThreadスレッドとの同期用
    val mFlag = PfFlag()

    // ソケット
    var mSocket : Socket = Socket()

    // 入力Stream
    var mIStream : InputStream? = null

    // 接続状態
    val mStatus = MStatus( false )

        data class MStatus( var connecting : Boolean ) {

            var connected : Boolean = false

            init {

            }
        }

    // ホスト名
    var mHostname : String? = null

    // ポート番号
    var mPort : Int = 0

    // 接続時のタイムアウト時間
    var mTimeOutToConnect : Int = 0


    // 初期化
    init {

    }

    // スレッド処理 接続
    val threadProc_Connect = object : Runnable {

        override fun run()
        {
            mFlag.clear()

            // 接続待ち中のフラグを立てる
            mLock.withLock {
                mStatus.connecting = true
                mStatus.connected = false

                // 接続の前に現状のSocketをcloseしておく
                mSocket.close()

                // ソケットを新規に
                mSocket = Socket()
            }


            // 通常の処理フロー
            try {

                // Host名が未指定ならばlocalhost
                if (mHostname == null) {

                    val lhost = InetAddress.getLocalHost()

                    val isaddr = InetSocketAddress(lhost, mPort)

                    mSocket.connect(isaddr, mTimeOutToConnect)

                }
                // Host名が指定されている場合
                else {

                    val iaddr = InetAddress.getByName(mHostname)

                    val isaddr = InetSocketAddress(iaddr, mPort)

                    mSocket.connect(isaddr, mTimeOutToConnect)
                }

                // 送信用のスレッドが起動していなければ起動させる
                if( !mHThreadMisc.isAlive() )
                {
                    mHThreadMisc.start()
                }

                // 入力ストリームを得る
                mIStream = mSocket.getInputStream()

                if( mIStream == null )
                {
                    throw Exception()
                }

                // 接続済みのフラグを立てる
                // 接続待ち中のフラグを下す
                mLock.withLock {
                    mStatus.connected = true
                    mStatus.connecting = false
                }

                // リスナーを呼び出す
                mListener.onConnected?.invoke( true )

                // 受信待ちの処理を起動する
                val handler = Handler( mHThread.looper )

                handler.post( threadProc_WaitRecv )
            }
            // 例外発生時
            catch( e : Exception ) {

                // リスナーを呼び出す
                mListener.onConnected?.invoke( false )

                // 接続待ち中のフラグを下す
                mLock.withLock {
                    mStatus.connecting = false
                }

                mFlag.set(1)
            }
        }
    }

    val mReceived1stByte = ByteArray( 1 )

    // スレッド処理 受信待ち
    val threadProc_WaitRecv = object : Runnable {

        override fun run() {

            while( isAlive_inner() )
            {
                try {

                    // タイムアウトを永久待ちに
                    mSocket.soTimeout = 0

                    // サイズ0では待てないので、1byte待ちをする。
                    var nread : Int = 0

                    mIStream?.run {
                        nread = read(mReceived1stByte, 0, mReceived1stByte.size)
                    }

                    when
                    {
                        // データがあればリスナーを呼び出す
                        nread > 0 -> {

                            mListener.onReceived?.invoke( this@PfSocketClient as PfSocket )
                        }

                        // 0 以下の場合、相手から切断された可能性がある
                        else -> {
                            throw Exception()
                        }
                    }
                }

                catch( e:Exception ) {

                    mSocket.close()
                }
            }

            // 接続済みのフラグを落とす
            mLock.withLock {
                mStatus.connected = false

                mIStream?.close()

                mIStream = null
            }

            mListener.onDisconnected?.invoke( Unit )

            mFlag.set(1)
        }
    }

    // 接続
    // port ポート番号
    // hostname ホスト名 nullの場合にはローカルホスト
    // timeout_msec タイムアウト -1:block 0:non-block >0:timeout( millisecond )
    fun connect( port : Int, hostname : String? = null, timeout_msec : Int = -1 ) : Boolean
    {
        var timeout_act :Int

        when
        {
            timeout_msec < 0  -> timeout_act = 0
            timeout_msec == 0 -> timeout_act = 1
            else              -> timeout_act = timeout_msec
        }

        mLock.withLock {

            if (mStatus.connecting || mStatus.connected) {
                return false
            }

            mHostname = hostname
            mPort = port
            mTimeOutToConnect = timeout_act

            mStatus.connecting = true

            if ( mHThread.isAlive() == false ) {

                mHThread.start()
            }

            val handler = Handler( mHThread.looper )

            handler.post( threadProc_Connect )
        }

        return true
    }

    // 送信もメインスレッドからはNG
    override fun send( data : ByteArray, len : Int  ) : Int
    {
        mLock.withLock {

            if( !isAlive_inner() ) {
                return -1
            }

            mFlagMisc.clear()

            val handler = Handler( mHThreadMisc.looper )

            handler.post {

                try {

                    val ost = mSocket.getOutputStream()

                    when( len ) {

                        0 -> {
                            ost.write( data )

                            mFlagMisc.set( data.size )
                        }

                        else -> {
                            ost.write( data, 0, len )

                            mFlagMisc.set( len )
                        }
                    }

                } catch( e: Exception ) {

                    mFlagMisc.set( -1 )
                }
            }

            return mFlagMisc.wait().toInt()
        }
    }

    // 受信はmHThreadにおいて、onReceiveからの呼び出しを想定するので、ここでは特別なケアはしない。
    override fun recv( data : ByteArray, len : Int, timeout_msec : Int ) : Int
    {
        try {

            // mIStream = mSocket.getInputStream()

            when {

                timeout_msec == 0 -> {
                    mSocket.soTimeout = 1
                }
                timeout_msec < 0 -> {
                    mSocket.soTimeout = 0
                }
                else -> {
                    mSocket.soTimeout = timeout_msec
                }
            }

            // 1byte分はすでにスレッド側でリードしたうえで、
            // mListener.OnReceived経由でここに至るはず
            data[0] = mReceived1stByte[0]

            var n_read : Int = 0

            when (len) {

                0 -> {
                    mIStream?.run { n_read = read( data, 1, data.size-1 ) }
                }

                else -> {

                    if( len > 1 ) {
                        mIStream?.run { n_read = read(data, 1, len - 1) }
                    }
                }
            }

            return n_read + 1

        } catch (e: Exception) {

            return -1
        }
    }

    override fun close()
    {
        mLock.withLock {

            if (!mStatus.connected || mStatus.connecting) {
                return
            }

            if( mSocket.isConnected() == true ) {

                mFlag.clear()

                mStatus.connected = false
                mStatus.connecting = false

                Handler( mHThreadMisc.looper ).post {

                    mSocket.close()

                    mIStream?.close()
                }
            }
        }

        mFlag.wait()
    }

    private fun isAlive_inner() : Boolean
    {
        if( mStatus.connected ) {

            if( mSocket.isConnected() && !mSocket.isClosed() )
            {
                return true
            }
        }

        return false
    }

    override fun isAlive() : Boolean
    {
        mLock.withLock {

            return isAlive_inner()
        }
    }
}
