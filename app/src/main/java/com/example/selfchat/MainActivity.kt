package com.example.selfchat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    // サーバーのソケットクラス（ port を openし、接続を待つ)
    val mServer = PfSocketServer()

    // サーバーに接続してきたクライアントと通信をするためのソケット
    var mSocketServer : PfSocket? = null

    // クライアントのソケットクラス( サーバーに接続をする )
    val mSocketClient = PfSocketClient()

    // --------------------------------------------------------------------------------------------
    //
    // サーバーの処理の定義
    //
    // --------------------------------------------------------------------------------------------
    fun setupServer()
    {
        // サーバーのOpen時の処理
        mServer.setOnOpenedListener{ port : Int ->

            Handler( this@MainActivity.mainLooper ).post {

                buttonSrvExec.isClickable = true

                if( port >= 0 )
                {
                    buttonSrvExec.text = "Close"
                }

            }
        }

        // サーバーのClose時の処理
        mServer.setOnClosedListener {

            mSocketServer?.close()

            mSocketServer = null

            Handler( this@MainActivity.mainLooper ).post {

                buttonSrvExec.isClickable = true
                buttonSrvExec.text = "Exec"
            }
        }

        // サーバーにクライアントが接続してきた場合の処理
        mServer.setOnConnectedListener {

            if( mSocketServer == null )
            {
                mSocketServer = it

                mSocketServer?.run {

                    // クライアントとの接続が切れた場合の処理
                    setOnDisconnectedListener {

                        mSocketServer?.close()
                        mSocketServer = null

                        Handler(this@MainActivity.mainLooper).post {

                            textViewSrv.text = ""
                            buttonSrvSend.isClickable = false
                        }
                    }

                    // クライアントからデータを受信した場合の処理
                    setOnReceivedListener {

                        val data = ByteArray(128)

                        recv( data, timeout_msec = 100 )

                        val str = String( data )

                        Handler(this@MainActivity.mainLooper).post {

                            textViewSrv.text = textViewSrv.text.toString() + str + "\n"
                        }
                    }

                    Handler(this@MainActivity.mainLooper).post {

                        buttonSrvSend.isClickable = true
                    }
                }
            }
            else
            {
                // クライアントの接続は1つだけ
                it.close()
            }
        }

        // サーバー側の「EXEC」ボタンが押下された場合の処理
        buttonSrvExec.setOnClickListener { _ : View ->

            // サーバーが生きていた場合には、close指示して終了
            if( mServer.isAlive() )
            {
                buttonSrvExec.isClickable = false

                // サーバーを閉じる
                mServer.close()
            }
            else {

                // サーバーに接続したクライアントがあればクローズを指示する
                mSocketServer?.close()
                mSocketServer = null

                buttonSrvExec.isClickable = false

                // サーバーを開く
                mServer.open(8888)
            }
        }

        // サーバー側の「Send」ボタンが押下された場合の処理
        buttonSrvSend.setOnClickListener {

            val str = editTextSrv.text.toString()

            mSocketServer?.run {

                send( str.toByteArray() )
            }

            editTextSrv.text.clear()
        }
    }

    // --------------------------------------------------------------------------------------------
    //
    // クライアントの処理の定義
    //
    // --------------------------------------------------------------------------------------------
    fun setupClient()
    {
        // クライアント側で接続が成功 or 失敗した場合
        mSocketClient.setOnConnectedListener { success : Boolean ->

            Handler( this@MainActivity.mainLooper ).post {

                if( success )
                {
                    buttonCliConnect.text = "Disconnect"
                    buttonCliConnect.isClickable = true
                }
                else
                {
                    buttonCliConnect.isClickable = true
                }
            }
        }

        // クライアント側で接続が切断された場合
        mSocketClient.setOnDisconnectedListener {

            Handler( this@MainActivity.mainLooper ).post {

                buttonCliConnect.text = "Connect"
                buttonCliConnect.isClickable = true

                textViewCli.text = ""
            }
        }

        // クライアント側でデータを受信した場合
        mSocketClient.setOnReceivedListener {

            val data = ByteArray( 128 )

            it.recv( data, timeout_msec = 100 )

            val str = String( data )

            Handler( this@MainActivity.mainLooper ).post {

                textViewCli.text = textViewCli.text.toString() + str + "\n"
            }
        }

        // クライアント側の「Connect」ボタンが押下された場合の処理
        buttonCliConnect.setOnClickListener { _: View ->

            if ( mSocketClient.isAlive()) {
                mSocketClient.close()
            }
            else {
                buttonCliConnect.isClickable = false
                mSocketClient.connect(8888, timeout_msec = 5000)
            }
        }

        // クライアント側の「Send」ボタンが押下された場合の処理
        buttonCliSend.setOnClickListener{ _ : View->

            val data = editTextCli.text.toString().toByteArray()

            mSocketClient.send( data )

            editTextCli.text.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupServer()

        setupClient()
    }
}
