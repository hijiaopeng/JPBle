package com.jiaopeng.jpble

import android.bluetooth.BluetoothDevice
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder

/**
 * 描述：
 *
 * @author JiaoPeng by 2020/10/27
 */
class BleAdapter(data: ArrayList<BluetoothDevice>) :
    BaseQuickAdapter<BluetoothDevice, BaseViewHolder>(R.layout.ble_item, data) {

    override fun convert(holder: BaseViewHolder, item: BluetoothDevice) {
        holder.setText(R.id.tvAddress, item.address + " -- " + item.name)
    }

}