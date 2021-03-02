package com.jiaopeng.jpble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt

/**
 * 描述：蓝牙设备实体类
 *
 * @author JiaoPeng by 2020/10/28
 */
data class BleEntity(
    var gatt: BluetoothGatt? = null
)