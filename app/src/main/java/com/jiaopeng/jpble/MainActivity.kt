package com.jiaopeng.jpble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankj.utilcode.util.ConvertUtils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.noButton
import org.jetbrains.anko.toast
import org.jetbrains.anko.yesButton
import permissions.dispatcher.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext

@RuntimePermissions
class MainActivity : AppCompatActivity(), CoroutineScope {
    //获取设备自身的蓝牙适配器
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    //列表展示的Adapter
    private val bleAdapter: BleAdapter by lazy {
        BleAdapter(arrayListOf())
    }

    //列表展示的数据
    private val bles = arrayListOf<BluetoothDevice>()

    //协程
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private val coroutineScope by lazy {
        CoroutineScope(coroutineContext)
    }

    //蓝牙扫描对象
    private var scanner: BluetoothLeScanner? = null

    private val serviceUUID = "0000fff0-0000-1000-8000-00805f9b34fb"
    private val characteristicNotifyUUID = "0000fff1-0000-1000-8000-00805f9b34fb"
    private val characteristicWriteUUID = "0000fff2-0000-1000-8000-00805f9b34fb"

    //记录已连接的蓝牙设备
    private val bleConnectList = arrayListOf<BluetoothGatt>()

    //蓝牙扫描回调
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            //当一个蓝牙ble广播被发现时回调
            Log.e("TAG", "扫描一个")
            result?.device?.let {
                bles.add(it)
                val b = bles.distinctBy { data -> data.address }
                bleAdapter.setList(b)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            //批量返回扫描结果
            //@param results 以前扫描到的扫描结果列表。
            Log.e("TAG", "扫描列表")
        }

        override fun onScanFailed(errorCode: Int) {
            //当扫描不能开启时回调
            //扫描太频繁会返回ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED，表示app无法注册，无法开始扫描。
            Log.e("TAG", "扫描失败")
        }
    }

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
                    BluetoothProfile.STATE_CONNECTED -> {
                        //判断是否连接码
                        Log.e("TAG", "newState success == ${newState}")
                        //延迟发现设备服务,触发onConnectionStateChange函数
                        coroutineScope.launch {
                            delay(1000)
                            gatt?.discoverServices()
                        }
                        //蓝牙设备连接成功后添加到集合中保存
                        gatt?.let {
                            bleConnectList.add(it)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        //判断是否断开连接码
                        Log.e("TAG", "newState dis == ${newState}")
                        //断开后在蓝牙设备记录集合中移除
                        gatt?.let {
                            //会终止onConnectionStateChange回调，视情况分开调用
                            it.close()
                            bleConnectList.remove(it)
                        }
                    }
                }
            } else {
                //异常码
                //可以在此处配置重连机制
                Log.e("TAG", "status error == ${status}")
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
                //获取指定uuid的service
                val gattService = gatt?.getService(UUID.fromString(serviceUUID))
                //获取到特定的服务不为空
                if (gattService != null) {
                    Log.e("TAG", "获取特定服务成功")
                    //获取指定通知uuid的Characteristic
                    val gattCharacteristic =
                            gattService.getCharacteristic(UUID.fromString(characteristicNotifyUUID))
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
                        //设置订阅notificationGattCharacteristic值改变的通知
                        gatt.setCharacteristicNotification(
                                gattCharacteristic,
                                true
                        )

                        //通过Gatt对象读取特定特征（Characteristic）的特征值
//                        gatt.readCharacteristic(gattCharacteristic)

                        //写入你需要传递给外设的特征值（即传递给外设的信息）
//                        gattCharacteristic.setValue(bytes)
                        //通过GATT实体类将，特征值写入到外设中
//                        gatt.writeCharacteristic(gattCharacteristic)
                    } else {
                        //获取特定特征失败
                        Log.e("TAG", "获取特定特征失败")
                    }
                } else {
                    //获取特定服务失败
                    Log.e("TAG", "获取特定服务失败")
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
                Log.e(
                        "TAG", "特征读取回调 characteristic?.value == ${
                    ConvertUtils.bytes2HexString(
                            characteristic?.value
                    )
                }"
                )
            }
        }

        //外设特征值改变回调
        override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
        ) {
            Log.e("TAG", "外设特征值改变回调")
            //获取外设修改的特征值
            //对特征值进行解析
            Log.e(
                    "TAG", "外设特征值改变回调 characteristic?.value == ${
                ConvertUtils.bytes2HexString(
                        characteristic?.value
                )
            }"
            )
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

    companion object {
        //启动蓝牙回调的标示
        const val REQUEST_ENABLE_BT = 100

        //扫描时间
        const val SCAN_PERIOD: Long = 10000L
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //扫描
        btn_search?.setOnClickListener {
            //判断设备是否支持蓝牙
            if (bluetoothAdapter != null) {
                //判断蓝牙是否打开
                if (bluetoothAdapter!!.isEnabled) {
                    //判断定位服务是否开启
                    if (LocationUtils.isLocationEnabled) {
                        //开启扫描
                        searchBleWithPermissionCheck()
                    } else {
                        //进入设置手动打开位置服务
                        LocationUtils.openGpsSettings()
                    }
                } else {
                    //通过系统设置发起启用蓝牙的请求
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }
            }
        }

        //配置列表
        rvShow?.layoutManager = LinearLayoutManager(this)
        rvShow?.adapter = bleAdapter
        //列表Item子组件点击监听
        bleAdapter.addChildClickViewIds(R.id.btnConnect, R.id.btnSend, R.id.btnClose)
        bleAdapter.setOnItemChildClickListener { adapter, view, position ->
            val d = adapter.getItem(position) as BluetoothDevice
            when (view.id) {
                //连接
                R.id.btnConnect -> {
                    //连接设备
//                    d.connectGatt(this@MainActivity, true, gattCallback)

                    BleManage.getInstance(this).connect(
                            d, false,
                            BleConnectCallback(
                                    bleConnecting = {
                                        Log.e("TAG", "连接中")
                                    },
                                    bleConnectResult = { i: Int, bluetoothGatt: BluetoothGatt? ->
                                        Log.e(
                                                "TAG",
                                                "连接成功 status == ${i} / mac == ${bluetoothGatt?.device?.address}"
                                        )
                                        BleManage.getInstance(this).bleNotification(
                                                bluetoothGatt,
                                                serviceUUID,
                                                characteristicNotifyUUID,
                                                BleNotificationCallback(
                                                        bleNotificationSuccess = {
                                                            Log.e("TAG", "调用通知成功")
                                                        },
                                                        bleNotificationFail = {
                                                            Log.e("TAG", "调用连接失败")
                                                        },
                                                        bleNotificationResult = {
                                                            Log.e("TAG", "调用连接结果 status == ${it}")
                                                        }
                                                )
                                        )
                                    },
                                    bleDisConnect = { i: Int, bluetoothGatt: BluetoothGatt? ->
                                        Log.e(
                                                "TAG",
                                                "连接断开 status == ${i} / mac == ${bluetoothGatt?.device?.address}"
                                        )
                                    },
                                    bleConnectFail = {
                                        Log.e("TAG", "连接失败 status == ${it}")
                                    }
                            )
                    )
                }
                //发送信息到蓝牙设备
                R.id.btnSend -> {
                    val gatt = bleConnectList[position]
                    val gattService =
                            gatt.getService(UUID.fromString(serviceUUID))
                    if (gattService != null) {
                        val gattCharacteristic =
                                gattService.getCharacteristic(UUID.fromString(characteristicWriteUUID))
                        //获取特定特征成功
                        if (gattCharacteristic != null) {
                            //写入你需要传递给外设的特征值（即传递给外设的信息）
                            gattCharacteristic.value = ConvertUtils.hexString2Bytes("5A6B030000C8")
                            //通过GATT实体类将，特征值写入到外设中
                            gatt.writeCharacteristic(gattCharacteristic)
                        }
                    }
                }
                //断开连接
                R.id.btnClose -> {
                    closeBle(bleConnectList[position])
                }
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (RESULT_OK == resultCode && REQUEST_ENABLE_BT == requestCode) {
            //开启蓝牙成功
            Log.e("TAG", "开启蓝牙成功")
        } else if (RESULT_CANCELED == resultCode && REQUEST_ENABLE_BT == requestCode) {
            //开启蓝牙失败
            Log.e("TAG", "开启蓝牙失败")
        }
    }

    /**
     *  开始扫描蓝牙设备
     *  需要调用运行时权限的方法上，当用户给予权限后执行
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @NeedsPermission(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    )
    fun searchBle() {
//        scanner = bluetoothAdapter?.bluetoothLeScanner
//        //配置扫描过滤参数
//        val filters = arrayListOf<ScanFilter>()
//        filters.apply {
//            add(
//                ScanFilter.Builder()
//                    .setServiceUuid(ParcelUuid.fromString(serviceUUID))
//                    .build()
//            )
//        }
//        //配置扫描设置
//        val setting = ScanSettings.Builder()
//        /**
//         * 设置扫描模式
//         * ScanSettings.SCAN_MODE_LOW_POWER 低功耗模式(默认扫描模式,如果扫描应用程序不在前台，则强制使用此模式。)
//         * ScanSettings.SCAN_MODE_BALANCED 平衡模式
//         * ScanSettings.SCAN_MODE_LOW_LATENCY 高功耗模式(建议仅在应用程序在前台运行时才使用此模式。)
//         */
//        setting.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
//        /**
//         * 设置回调类型
//         * ScanSettings.CALLBACK_TYPE_ALL_MATCHES  寻找符合过滤条件的蓝牙广播，如果没有设置过滤条件，则返回全部广播包
//         * ScanSettings.CALLBACK_TYPE_FIRST_MATCH  仅针对与筛选条件匹配的第一个广播包触发结果回调
//         * ScanSettings.CALLBACK_TYPE_MATCH_LOST   有过滤条件时过滤，返回符合过滤条件的蓝牙广播。无过滤条件时，返回全部蓝牙广播
//         */
//        setting.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
//        /**
//         * 设置蓝牙LE扫描滤波器硬件匹配的匹配模式
//         * ScanSettings.MATCH_MODE_STICKY            粘性模式，在通过硬件报告之前，需要更高的信号强度和目击阈值
//         * ScanSettings.MATCH_MODE_AGGRESSIVE        激进模式，即使信号强度微弱且持续时间内瞄准/匹配的次数很少，也会更快地确定匹配
//         */
//        setting.setMatchMode(ScanSettings.MATCH_MODE_STICKY)
//        //如果芯片组支持批处理芯片上的扫描
//        bluetoothAdapter?.isOffloadedScanBatchingSupported?.let {
//            //判断当前手机蓝牙芯片是否支持批处理扫描
//            if (it) {
//                //设置蓝牙LE扫描的报告延迟的时间（以毫秒为单位）
//                //设置为0以立即通知结果
//                setting.setReportDelay(0L)
//            }
//        }
//
//        /**
//         * 配置扫描回调
//         * 注意：回调函数中尽量不要做耗时操作！一般蓝牙设备对象都是通过onScanResult(int,ScanResult)返回，
//         * 而不会在onBatchScanResults(List)方法中返回，除非手机支持批量扫描模式并且开启了批量扫描模式。
//         * 批处理的开启请查看ScanSettings
//         */
//        scanner?.startScan(filters, setting.build(), scanCallback)
//
//        /**
//         * 配置指定扫描时间，及时关闭扫描， 不然费电
//         */
//        coroutineScope.launch {
//            delay(SCAN_PERIOD)
//            scanner?.stopScan(scanCallback)
//            Log.e("TAG", "扫描完毕")
//        }


        BleManage.getInstance(this).startScanBle(
                serviceUUID,
                10000,
                BleScanCallback(
                        bleScanResult = { _: Int, bluetoothDevice: BluetoothDevice ->
                            bleAdapter.addData(bluetoothDevice)
                            bleAdapter.notifyDataSetChanged()
                        },
                        bleScanFinish = { arrayList: ArrayList<BluetoothDevice> ->
                            Log.e("TAG", "bleScanListResult = ${arrayList.size}")
                        },
                        bleBatchScanResult = {
                            Log.e("TAG", "bleBatchScanResult = ${it.size}")
                        },
                        bleScanFail = {
                            Log.e("TAG", "bleScanFail = $it")
                        }
                )
        )
    }

    /**
     * 用于向用户解释为什么需要调用该权限的方法上，只有当第一次请求权限被用户拒绝，下次请求权限之前会调用
     */
    @OnShowRationale(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    )
    fun blePermissionRationale(request: PermissionRequest) {
        alert("我们需要对应的权限", "提示") {
            yesButton {
                request.proceed()
            }
            noButton {
                request.cancel()
            }
        }.show()
    }

    /**
     * 当用户拒绝了权限请求时，需要调用的方法上
     */
    @OnPermissionDenied(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    )
    fun blePermissionDenied() {
        toast("无法获得权限")
    }

    /**
     * 当用户选择了授权窗口的不再询问复选框后，并拒绝了权限请求时需要调用的方法，
     * 一般可以向用户解释为何申请此权限，并根据实际需求决定是否再次弹出权限请求对话框
     */
    @OnNeverAskAgain(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    )
    fun blePermissionNeverAskAgain() {
        toast("应用权限被拒绝,为了不影响您的正常使用，请在设置中开启对应权限")
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    /**
     * 断开并关闭蓝牙设备通信
     */
    private fun closeBle(gatt: BluetoothGatt?) {
        //断开连接，触发onConnectionStateChange回调，回调断开连接信息
        gatt?.disconnect()
    }
}