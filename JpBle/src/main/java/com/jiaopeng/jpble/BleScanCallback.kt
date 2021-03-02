package com.jiaopeng.jpble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult

/**
 * 描述：蓝牙设备扫描回调
 *
 * @author JiaoPeng by 2020/10/28
 */
data class BleScanCallback(
    /**
     * 扫描成功，每扫描到一个设备，就返回当前数据
     */
    var bleScanResult: ((Int, BluetoothDevice) -> Unit)? = null,
    /**
     * 扫描完成，将扫描得到的所有设备返回
     */
    var bleScanFinish: ((ArrayList<BluetoothDevice>) -> Unit)? = null,
    /**
     * 批量扫描结果
     */
    var bleBatchScanResult: ((ArrayList<ScanResult>) -> Unit)? = null,
    /**
     * 扫描失败
     */
    var bleScanFail: ((Int) -> Unit)? = null
)