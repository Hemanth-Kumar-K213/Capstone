package com.example.testble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.testble.databinding.ItemDeviceBinding

class BleDeviceAdapter(
    private val onDeviceClick: (String, String) -> Unit
) : RecyclerView.Adapter<BleDeviceAdapter.DeviceViewHolder>() {

    private var devices = emptyList<ScanResult>()

    @SuppressLint("MissingPermission", "NotifyDataSetChanged")
    fun updateDevices(newDevices: List<ScanResult>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position], onDeviceClick)
    }

    override fun getItemCount() = devices.size

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("MissingPermission")
        fun bind(result: ScanResult, onClick: (String, String) -> Unit) {
            val name = result.device.name ?: "Unknown"
            val address = result.device.address
            val rssi = result.rssi

            binding.apply {
                deviceName.text = name
                deviceAddress.text = address
                deviceRssi.text = "RSSI: $rssi dBm"
                root.setOnClickListener {
                    onClick(address, name)
                }
            }
        }
    }
}
