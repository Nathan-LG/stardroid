package com.google.android.stardroid.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

public class BluetoothControler extends Thread {

    public static String UARTSERVICE_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static String UART_TX_CHARACTERISTIC_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

    private int connectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTED = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattCallback bluetoothGattCallback;
    private BluetoothGattCharacteristic characteristic;
    private Handler handler;
    private Context context;
    private BluetoothGatt bluetoothGatt;
    private boolean mScanning = false;
    private boolean stop = false;
    private boolean connected = false;

    private String message = null;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 5000;

    private BluetoothAdapter.LeScanCallback leScanCallback =
        new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                if (device.getAddress().equals("DC:61:83:59:D3:11")) {
                    bluetoothAdapter.stopLeScan(leScanCallback);
                    bluetoothGattCallback = new BluetoothGattCallback() {
                        @Override
                        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                            int newState) {
                            if (newState == STATE_CONNECTED) {
                                bluetoothGatt = gatt;
                                connected = true;
                                Log.wtf("BLE Device", "Connected to GATT server.");

                                gatt.discoverServices();

                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                bluetoothGatt = null;
                                connected = false;
                                Log.wtf("BLE Device", "Disconnected from GATT server.");
                            }
                        }

                        @Override
                        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                            super.onServicesDiscovered(gatt, status);
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                return;
                            }
                            bluetoothGatt = gatt;
                            BluetoothGattService service = gatt.getService(UUID.fromString(UARTSERVICE_SERVICE_UUID));
                            characteristic = service.getCharacteristic(UUID.fromString(UART_TX_CHARACTERISTIC_UUID));

                            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            gatt.setCharacteristicNotification(characteristic, true);
                        }

                    };
                    Log.wtf("BLE Device", device.getAddress() + " " + device.getName());
                    device.connectGatt(context, false, bluetoothGattCallback);

                }
            }
        };

    public BluetoothControler(BluetoothAdapter bluetoothAdapter, Context context) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.handler = new Handler();
        this.context = context;
        this.bluetoothGatt = null;
    }

    public void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    Log.wtf("BLE Device", "Stop scanning");
                    bluetoothAdapter.stopLeScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            Log.wtf("BLE Device", "Start scanning");
            bluetoothAdapter.startLeScan(leScanCallback);
        } else {
            mScanning = false;
            Log.wtf("BLE Device", "Stop scanning");
            bluetoothAdapter.stopLeScan(leScanCallback);
        }
    }

    public void switchConnection() {
        if (this.connected) {
            bluetoothGatt.disconnect();
        } else {
            this.scanLeDevice(true);
        }
    }

    public void sendMessage (String message) {
        this.message = message;
        Log.wtf("To send", "Message to send : " + message);
    }

    public void interrupt() {
        this.stop = true;
    }

    public void startAgain() {
        this.stop = false;

        if (!this.isAlive()) {
            this.start();
        }
    }

    public void run() {

        while (true) {
            if (this.message != null && !this.stop) {
                message = message.concat(";");
                Log.wtf("BLE Device", "Sent " + message);
                byte[] messageBytes = new byte[0];
                try {
                    messageBytes = message.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.wtf("BLE Device", "Failed to convert message string to byte array");
                }

                if (characteristic != null && bluetoothGatt != null) {
                    characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    characteristic.setValue(messageBytes);
                    boolean success = bluetoothGatt.writeCharacteristic(characteristic);
                    Log.wtf("BLE Device", "Sent success ? " + success);
                } else {
                    Log.wtf("BLE Device", "Sent success ? " + false);
                }

                this.message = null;
            }
        }

    }

}
