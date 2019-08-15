package com.example.selfchat

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import kotlin.concurrent.withLock

class PfFlag()
{
    val mLock = ReentrantLock()

    var mFlag : Long = 0

    var mCond : Condition

    init {

        mCond = mLock.newCondition()
    }

    fun wait( timeout_msec : Int = -1 ) : Long
    {
        mLock.withLock {

            if( timeout_msec == 0 || mFlag != 0L )
            {
                val ret = mFlag

                mFlag = 0

                return ret
            }

            if( timeout_msec < 0 ) {

                mCond.await()
            }
            else
            if( timeout_msec > 0 ) {

                mCond.await( timeout_msec.toLong(), TimeUnit.MILLISECONDS )
            }

            val ret = mFlag

            mFlag = 0

            return ret
        }
    }

    fun get( clear : Boolean = false ) : Long
    {
        mLock.withLock {

            val ret = mFlag

            if( clear )
            {
                mFlag = 0
            }

            return ret
        }
    }

    fun clear( flag : Long = 0 )
    {
        mLock.withLock {

            mFlag = mFlag and flag
        }
    }

    fun set( flag : Int )
    {
        set( flag.toLong() )
    }

    fun set( flag : Long )
    {
        mLock.withLock {

            mFlag = mFlag or flag

            if( mFlag != 0L )
            {
                mCond.signal()
            }
        }
    }
}
