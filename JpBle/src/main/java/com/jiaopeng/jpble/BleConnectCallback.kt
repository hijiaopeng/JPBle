package com.jiaopeng.jpble

import android.bluetooth.BluetoothGatt

/**
 * 描述：蓝牙设备连接回调
 *
 * @author JiaoPeng by 2020/10/28
 */
data class BleConnectCallback(
    /**
     * 开始连接
     */
    var bleConnecting: (() -> Unit)? = null,
    /**
     * 连接成功
     */
    var bleConnectResult: ((Int, BluetoothGatt?) -> Unit)? = null,
    /**
     * 断开连接
     */
    var bleDisConnect: ((Int,BluetoothGatt?) -> Unit)? = null,
    /**
     * 连接失败
     */
    var bleConnectFail: ((Int) -> Unit)? = null,
)