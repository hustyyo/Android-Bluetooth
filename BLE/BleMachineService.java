/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.com.collectiveintelligence.fleetiq360.WebService.BLE;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BleMachineService extends Service {
    private final static String TAG = "CI_BLE_Service";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBleDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED = "com.fleetiq.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.fleetiq.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.fleetiq.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.fleetiq.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITE = "com.fleetiq.bluetooth.le.ACTION_DATA_WRITE";
    public final static String ACTION_DATA_CHANGED = "com.fleetiq.bluetooth.le.ACTION_DATA_CHANGED";
    public final static String CHARA_UUID = "com.fleetiq.bluetooth.le.CHARA_UUID";
    public final static String EXTRA_DATA = "com.fleetiq.bluetooth.le.EXTRA_DATA";
    public final static String DEVICE_ADDRESS = "com.fleetiq.bluetooth.le.DEVICE_ADDRESS";
    public final static String STATUS_CODE = "com.fleetiq.bluetooth.le.STATUS_CODE";


    public void discoverServices(){
        if(mBluetoothGatt != null){
            boolean r = mBluetoothGatt.discoverServices();
        }
    }
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            String address = gatt.getDevice().getAddress();
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                if(mConnectionState != STATE_CONNECTED){

                    broadcastUpdate(intentAction, address);
                    Log.i(TAG, "Connected to GATT server.");
                    boolean r = mBluetoothGatt.discoverServices();
                    Log.i(TAG, "Attempting to startConnect service discovery:" + r);
                }
                mConnectionState = STATE_CONNECTED;

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                if(mBluetoothGatt != null){
                    mBluetoothGatt.close();
                }
                broadcastUpdate(intentAction, address);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED,gatt.getDevice().getAddress());
                List<BluetoothGattService> list = getSupportedGattServices();
                Log.w(TAG, "onServicesDiscovered received count: " + list.size());
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {

            String addr = gatt.getDevice().getAddress();
            broadcastUpdate(status == BluetoothGatt.GATT_SUCCESS,addr,ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            String addr = gatt.getDevice().getAddress();
            broadcastUpdate(true,addr,ACTION_DATA_CHANGED, characteristic);

            Log.d(TAG,"shock count changed ");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            String addr = gatt.getDevice().getAddress();
            broadcastUpdate(status == BluetoothGatt.GATT_SUCCESS,addr,ACTION_DATA_WRITE, characteristic);
        }
    };

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        mBleDeviceAddress = address;
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        startConnect(device);

        return true;
    }

    public boolean connect(final BluetoothDevice device) {
        if (mBluetoothAdapter == null || device == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        mBleDeviceAddress = device.getAddress();
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        startConnect(device);

        return true;
    }

    void startConnect(BluetoothDevice device) {

        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mConnectionState = STATE_CONNECTING;
    }

    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        boolean r =  mBluetoothGatt.readCharacteristic(characteristic);
        Log.w(TAG, "readCharacteristic return " + (r?"true":"false"));

        return r;
    }

    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        boolean r =  mBluetoothGatt.writeCharacteristic(characteristic);
        Log.w(TAG, "writeCharacteristic characteristic return " + (r?"true":"false"));
        return r;
    }

    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        boolean r = mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        if(!r){
            return false;
        }

        if (BleModel.UUID_SHOCK_COUNT.toLowerCase().equalsIgnoreCase(characteristic.getUuid().toString())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(BleModel.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            return mBluetoothGatt.writeDescriptor(descriptor);
        }
        return true;
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public class LocalBinder extends Binder {
        BleMachineService getService() {
            return BleMachineService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    private void broadcastUpdate(final String action, String address) {
        broadcastUpdate(true,address,action,null);
    }

    private void broadcastUpdate(boolean succeed, String address, final String action,
                                 final BluetoothGattCharacteristic characteristic) {

        final Intent intent = new Intent(action);
        String uuid = "";
        if(characteristic != null){
            intent.putExtra(EXTRA_DATA,characteristic.getValue());
            uuid = characteristic.getUuid().toString();
            intent.putExtra(CHARA_UUID, uuid);
        }
        intent.putExtra(DEVICE_ADDRESS, address);
        intent.putExtra(STATUS_CODE, succeed);
        //sendBroadcast(intent);
        BleControlService.sendData(intent,uuid);
    }

    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }
}
