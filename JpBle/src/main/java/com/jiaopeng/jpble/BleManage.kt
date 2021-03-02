package com.jiaopeng.jpble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.reflect.Method
import java.util.*


/**
 * 描述：
 *
 * @author JiaoPeng by 2020/10/28
 */
@RequiresApi(Build.VERSION_CODES.M)
class BleManage private constructor(private val context: Context) {

    /**
     * 实现单例
     */
    companion object : SingletonHolder<BleManage, Context>(::BleManage)

    //获取设备自身的蓝牙适配器
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    //蓝牙扫描对象
    private var scanner: BluetoothLeScanner? = null

    //扫描出来的设备
    private val bleScanList = arrayListOf<BluetoothDevice>()

    //扫描结果回调
    private var mBleScanCallback: BleScanCallback? = null

    //服务ID
    private var mServiceUUID = ""

    //特征ID
    private var mCharacteristicNotifyUUID = ""

    //蓝牙扫描回调
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            //当一个蓝牙ble广播被发现时回调
//            Log.e("TAG", "扫描一个")
            result?.device?.let {
                if (!bleScanList.any { ble -> ble.address == it.address }) {
                    bleScanList.add(it)
                    mBleScanCallback?.bleScanResult?.invoke(callbackType, it)
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            //批量返回扫描结果
            //@param results 以前扫描到的扫描结果列表。
            Log.e("TAG", "扫描列表")
            mBleScanCallback?.bleBatchScanResult?.invoke(results as ArrayList<ScanResult>)
        }

        override fun onScanFailed(errorCode: Int) {
            //当扫描不能开启时回调
            //扫描太频繁会返回ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED，表示app无法注册，无法开始扫描。
            Log.e("TAG", "扫描失败")
            mBleScanCallback?.bleScanFail?.invoke(errorCode)
        }
    }

    /**
     * 开始扫描蓝牙设备
     * serviceUUID：服务UUID
     * delayMillis：扫描时长，小于等于0时，不限制时间，一直扫描
     * bleScanCallback：扫描结果回调
     */
    fun startScanBle(
        serviceUUID: String,
        delayMillis: Long = 10000L,
        bleScanCallback: BleScanCallback
    ) {
        mBleScanCallback = bleScanCallback
        scanner = bluetoothAdapter?.bluetoothLeScanner
        //配置扫描过滤参数
        val filters = arrayListOf<ScanFilter>()
        filters.apply {
            add(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(serviceUUID))
                    .build()
            )
        }
        //配置扫描设置
        val setting = ScanSettings.Builder()
        /**
         * 设置扫描模式
         * ScanSettings.SCAN_MODE_LOW_POWER 低功耗模式(默认扫描模式,如果扫描应用程序不在前台，则强制使用此模式。)
         * ScanSettings.SCAN_MODE_BALANCED 平衡模式
         * ScanSettings.SCAN_MODE_LOW_LATENCY 高功耗模式(建议仅在应用程序在前台运行时才使用此模式。)
         */
        setting.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        /**
         * 设置回调类型
         * ScanSettings.CALLBACK_TYPE_ALL_MATCHES  寻找符合过滤条件的蓝牙广播，如果没有设置过滤条件，则返回全部广播包
         * ScanSettings.CALLBACK_TYPE_FIRST_MATCH  仅针对与筛选条件匹配的第一个广播包触发结果回调
         * ScanSettings.CALLBACK_TYPE_MATCH_LOST   有过滤条件时过滤，返回符合过滤条件的蓝牙广播。无过滤条件时，返回全部蓝牙广播
         */
        setting.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        /**
         * 设置蓝牙LE扫描滤波器硬件匹配的匹配模式
         * ScanSettings.MATCH_MODE_STICKY            粘性模式，在通过硬件报告之前，需要更高的信号强度和目击阈值
         * ScanSettings.MATCH_MODE_AGGRESSIVE        激进模式，即使信号强度微弱且持续时间内瞄准/匹配的次数很少，也会更快地确定匹配
         */
        setting.setMatchMode(ScanSettings.MATCH_MODE_STICKY)
        //如果芯片组支持批处理芯片上的扫描
        bluetoothAdapter?.isOffloadedScanBatchingSupported?.let {
            //判断当前手机蓝牙芯片是否支持批处理扫描
            if (it) {
                //设置蓝牙LE扫描的报告延迟的时间（以毫秒为单位）
                //设置为0以立即通知结果
                setting.setReportDelay(0L)
            }
        }

        /**
         * 配置扫描回调
         * 注意：回调函数中尽量不要做耗时操作！一般蓝牙设备对象都是通过onScanResult(int,ScanResult)返回，
         * 而不会在onBatchScanResults(List)方法中返回，除非手机支持批量扫描模式并且开启了批量扫描模式。
         * 批处理的开启请查看ScanSettings
         */
        scanner?.startScan(filters, setting.build(), scanCallback)

        /**
         * 配置指定扫描时间，及时关闭扫描，不然费电
         */
        if (delayMillis > 0L) {
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    scanner?.stopScan(scanCallback)
                    //去重后扫描出来的设备
                    val b = bleScanList.distinctBy { data -> data.address }
                        .toMutableList() as ArrayList<BluetoothDevice>
                    mBleScanCallback?.bleScanFinish?.invoke(b)
                    Log.e("TAG", "扫描完毕")
                },
                delayMillis
            )
        }
    }

    /**
     * 关闭蓝牙扫描
     */
    fun stopScanBle() {
        scanner?.stopScan(scanCallback)
    }

    /**
     * 蓝牙设备连接回调
     */
    private var mBleConnectCallback: BleConnectCallback? = null

    /**
     * 蓝牙设备连接回调
     */
    private val gattCallback = object : BluetoothGattCallback() {

        //连接状态回调
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            //操作成功的情况下
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.e("TAG", "status success == ${status}")
                when (newState) {
                    BluetoothProfile.STATE_CONNECTING -> {
                        //开始连接
                        mBleConnectCallback?.bleConnecting?.invoke()
                    }
                    BluetoothProfile.STATE_CONNECTED -> {
                        //成功连接
                        Log.e("TAG", "newState success == ${newState}")
                        mBleConnectCallback?.bleConnectResult?.invoke(newState, gatt)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        //连接断开
                        Log.e("TAG", "newState dis == ${newState}")
                        mBleConnectCallback?.bleDisConnect?.invoke(newState, gatt)
                    }
                }
            } else {
                //异常码
                //可以在此处配置重连机制
                Log.e("TAG", "status error == ${status}")
                //关闭gatt
                refreshDeviceCache(gatt)
                gatt?.disconnect()
                gatt?.close()
                mBleConnectCallback?.bleConnectFail?.invoke(status)
                when (status) {
                    //连接超时或未找到设备
                    133 -> {
                        Log.e("TAG", "连接超时或未找到设备")
                    }
                    //设备超出范围
                    8 -> {
                        Log.e("TAG", "设备超出范围")
                    }
                    //表示本地设备终止了连接
                    22 -> {
                        Log.e("TAG", "表示本地设备终止了连接")
                    }
                }
            }
        }

        //服务发现回调
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.e("TAG", "服务发现回调 status == ${status}")
            //服务发现成功
            if (BluetoothGatt.GATT_SUCCESS == status) {
                Log.e("TAG", "服务发现成功")
                //获取指定uuid的service
                val gattService = gatt?.getService(UUID.fromString(mServiceUUID))
                //获取到特定的服务不为空
                if (gattService != null) {
                    Log.e("TAG", "获取特定服务成功")
                    //获取指定通知uuid的Characteristic
                    val gattCharacteristic =
                        gattService.getCharacteristic(UUID.fromString(mCharacteristicNotifyUUID))
                    //获取特定特征成功
                    if (gattCharacteristic != null) {
                        Log.e("TAG", "获取特定特征成功")
                        //获取其对应的通知Descriptor
                        //如果获取不到BluetoothGattDescriptor的时候，可以通过获取系统默认的BluetoothGattDescriptor，进行蓝牙接口设置
                        val descriptor: BluetoothGattDescriptor =
                            gattCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        if (descriptor != null) {
                            //设置通知值
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            val descriptorResult: Boolean =
                                gatt.writeDescriptor(descriptor)
                            Log.e("TAG", "descriptorResult = ${descriptorResult}")
                        }
                        mBleNotificationCallback?.bleNotificationSuccess?.invoke()
                        //设置订阅notificationGattCharacteristic值改变的通知
                        gatt.setCharacteristicNotification(
                            gattCharacteristic,
                            true
                        )
                    } else {
                        //获取特定特征失败
                        Log.e("TAG", "获取特定特征失败")
                        mBleNotificationCallback?.bleNotificationFail?.invoke()
                    }
                } else {
                    //获取特定特征失败
                    Log.e("TAG", "获取特定服务失败")
                    mBleNotificationCallback?.bleNotificationFail?.invoke()
                }
            } else {
                //服务发现失败
                Log.e("TAG", "服务发现失败")
            }
        }

        //特征写入回调
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            Log.e("TAG", "特征写入回调 status == ${status}")
        }

        //特征读取回调
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            Log.e("TAG", "特征读取回调 status == ${status}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //获取读取到的特征值
            }
        }

        //外设特征值改变回调
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            Log.e("TAG", "外设特征值改变回调")
            //获取外设修改的特征值
            mBleNotificationCallback?.bleNotificationResult?.invoke(characteristic?.value)
        }

        //描述写入回调
        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            Log.e("TAG", "描述写入回调 status == ${status}")
        }

        //描述读取回调
        override fun onDescriptorRead(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            Log.e("TAG", "描述读取回调 status == ${status}")
        }

    }

    /**
     * 连接蓝牙设备
     */
    fun connect(
        bluetoothDevice: BluetoothDevice,
        autoConnect: Boolean = true,
        bleConnectCallback: BleConnectCallback
    ) {
        mBleConnectCallback = bleConnectCallback
        bluetoothDevice.connectGatt(context, autoConnect, gattCallback)
    }

    /**
     * 断开并关闭蓝牙设备通信
     */
    fun closeBle(gatt: BluetoothGatt?) {
        //断开连接，触发onConnectionStateChange回调，回调断开连接信息
        gatt?.disconnect()
        gatt?.close()
    }

    private var mBleNotificationCallback: BleNotificationCallback? = null

    /**
     * 对已连接设备进行通知
     */
    fun bleNotification(
        gatt: BluetoothGatt?,
        serviceUUID: String,
        characteristicNotifyUUID: String,
        bleNotificationCallback: BleNotificationCallback
    ) {
        mBleNotificationCallback = bleNotificationCallback
        mServiceUUID = serviceUUID
        mCharacteristicNotifyUUID = characteristicNotifyUUID
        gatt?.discoverServices()
    }

    /**
     * 清理本地的BluetoothGatt的缓存，以保证在蓝牙连接设备的时候，设备的服务、特征是最新的
     */
    private fun refreshDeviceCache(gatt: BluetoothGatt?): Boolean {
        var refreshtMethod: Method? = null
        if (null != gatt) {
            try {
                for (methodSub in gatt.javaClass.declaredMethods) {
                    if ("connect".equals(methodSub.name, ignoreCase = true)) {
                        val types = methodSub.parameterTypes
                        if (types != null && types.size > 0) {
                            if ("int".equals(types[0].name, ignoreCase = true)) {
                                refreshtMethod = methodSub
                            }
                        }
                    }
                }
                if (refreshtMethod != null) {
                    refreshtMethod.invoke(gatt)
                }
                Log.e("TAG", "refreshDeviceCache-->" + "清理本地的BluetoothGatt 的缓存成功")
                return true
            } catch (localException: Exception) {
                localException.printStackTrace()
            }
        }
        Log.e("TAG", "refreshDeviceCache-->" + "清理本地清理本地的BluetoothGatt缓存失败")
        return false
    }

}