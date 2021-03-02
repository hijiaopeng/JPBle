package com.jiaopeng.jpble

import android.bluetooth.BluetoothGatt

/**
 * 描述：蓝牙设备通知回调
 *
 * @author JiaoPeng by 2020/10/28
 */
data class BleNotificationCallback(
    /**
     * 通知成功
     */
    var bleNotificationSuccess: (() -> Unit)? = null,
    /**
     * 通知失败
     */
    var bleNotificationFail: (() -> Unit)? = null,
    /**
     * 通知的数据回调
     */
    var bleNotificationResult: ((ByteArray?) -> Unit)? = null,
)