package com.jiaopeng.jpble

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 描述：蓝牙状态广播监听器
 *
 * @author JiaoPeng by 2020/10/27
 */
class BluetoothBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.ERROR
            )
            when (state) {
                BluetoothAdapter.STATE_OFF -> {
                    //蓝牙关闭
                    Log.e("TAG", "蓝牙关闭")
                }
                BluetoothAdapter.STATE_TURNING_OFF -> {
                    //蓝牙正在关闭
                    Log.e("TAG", "蓝牙正在关闭")
                }
                BluetoothAdapter.STATE_ON -> {
                    //蓝牙开启
                    Log.e("TAG", "蓝牙开启")
                }
                BluetoothAdapter.STATE_TURNING_ON -> {
                    //蓝牙正在开启
                    Log.e("TAG", "蓝牙正在开启")
                }
            }
        }
    }

}