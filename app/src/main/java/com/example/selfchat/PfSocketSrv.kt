package com.example.selfchat

import android.os.Handler
import android.os.HandlerThread
import java.net.Socket

class PfSocketSrv( val mSocket : Socket ) : PfSocket()
{
    // send用のスレッド( PfSocketSrvで共用 )
    companion object {
        val mHThreadToSend = HandlerThread( "PfSocketSrvSend" )
    }

    // send時に mHThreadToSendとの同期待ち用のフラグ
    val mFlag = PfFlag()

    // 受信待ちのスレッド
    val mHThread = HandlerThread( "PfSocketSrvRecv" )

    // mHThreadでの受信待ちの1byteのバッファ
    val mReceived1stByte = ByteArray(1)

    // 受信待ちの
    val threadProc = object : Runnable {

        override fun run() {

            // InputStreamを取得
            val ist = mSocket.getInputStream()

            // 接続している間繰り返す
            while( isAlive() )
            {
                try {

                    // タイムアウトを永久待ちに
                    mSocket.soTimeout = 0

                    // サイズ0では待てないので、1byte待ちをする。
                    val nread = ist.read( mReceived1stByte, 0, mReceived1stByte.size )

                    when
                    {
                        // データがあればリスナーを呼び出す
                        nread > 0 -> {
                            // onReceived リスナーを呼び出す
                            mListener.onReceived?.invoke( this@PfSocketSrv as PfSocket )
                        }

                        // 0 以下の場合、相手から切断された可能性がある
                        else -> {
                            throw Exception()
                        }
                    }

                } catch (e: Exception) {

                    mSocket.close()
                }
            }

            // 切断されたらその旨をリスナーに通知し終了
            mListener.onDisconnected?.invoke( Unit )
        }
    }

    init {

        // 送信用のスレッドが起動していなければ起動させる
        if( !mHThreadToSend.isAlive() )
        {
            mHThreadToSend.start()
        }

        // 受信待ちのスレッドを起動
        mHThread.start()

        // 受信待ちの処理を mHThreadにpostする
        val handler = Handler( mHThread.looper )

        handler.post( threadProc )
    }

    // 送信もメインスレッドからはNG
    override fun send( data : ByteArray, len : Int ) : Int
    {
        if( !mSocket.isConnected ) {
            return -1
        }

        mFlag.clear()

        val handler = Handler( mHThreadToSend.looper )

        handler.post{

            try {
                val ost = mSocket.getOutputStream()

                when( len )
                {
                    0 -> {
                        ost.write( data )

                        mFlag.set( data.size )
                    }

                    else -> {
                        ost.write( data, 0, len )

                        mFlag.set( len )
                    }
                }

            } catch( e: Exception ) {

                mFlag.set( -1 )
            }
        }

        return mFlag.wait().toInt()
    }

    // 受信はmHThreadにおいて、onReceiveからの呼び出しを想定するので、ここでは特別なケアはしない。
    override fun recv( data : ByteArray, len : Int, timeout_msec : Int ) : Int
    {
        try {

            val ist = mSocket.getInputStream()

            when
            {
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

            when( len )
            {
                0 -> {
                    n_read = ist.read( data, 1, data.size-1 )
                }

                else -> {

                    if( len > 1 ) {
                        n_read = ist.read(data, 1, len - 1)
                    }
                }
            }

            return n_read+1

        } catch( e : Exception ){

            return -1
        }
    }

    override fun close()
    {
        mSocket.close()
    }

    override fun isAlive() : Boolean
    {
        if( mSocket.isConnected() && !mSocket.isClosed() )
        {
            return true
        }

        return false
    }

}
