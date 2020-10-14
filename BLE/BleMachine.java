package au.com.collectiveintelligence.fleetiq360.WebService.BLE;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import au.com.collectiveintelligence.fleetiq360.ui.application.MyApplication;

/**
 * Created by steveyang on 10/6/17.
 */

public class BleMachine {
    private final String TAG = "CI_BLE";
    BleController mBleController;
    public int expectingRelayValue;
    public BleUtil.BleTimeItem expectingTimeItem;
    public int expectingImpactThreshold;

    public BleMachine(BleController bleController){
        mBleController = bleController;
    }

    boolean authDevice() {

        if(mBleController.ble_status != BleController.STATUS_SERVICE_FOUND){
            return true;
        }

        BluetoothGattCharacteristic characteristic = mBleController.getCharacteristic(BleModel.UUID_TOKEN_AUTH);
        if(null == characteristic){
            Log.d(TAG,"Failed to found token attribute");
            return true;
        }

        characteristic.setValue(BleModel.TOKEN_AUTH);
        boolean r = writeCharacteristic(characteristic);
        Log.d(TAG,"authDevice write return " + (r?"true":"false"));
        return r;
    }

    void onServiceFound() {
        if(mBleController.ble_status != BleController.STATUS_SERVICE_FOUND &&
                mBleController.ble_status != BleController.STATUS_CONNECTING){
            return;
        }
        List<BluetoothGattService> list = mBleController.getSupportedGattServices();
        if(null == list || list.size()==0){
            MyApplication.runLater(new Runnable() {
                @Override
                public void run() {
                    mBleController.discoverServices();
                }
            },200);
            return;
        }

        Log.d(TAG,"status start auth");
        if(authDevice()){
            MyApplication.runLater(new Runnable() {
                @Override
                public void run() {
                    onAuthDone();
                }
            },200);
        }
        else {
            BleControlService.authDevice();
        }
    }


    void onAuthDone() {
        if(mBleController.ble_status != BleController.STATUS_SERVICE_FOUND){
            return;
        }
        mBleController.pushStateMachine();
    }

    boolean disableShockNotification() {
        if(mBleController.ble_status < mBleController.STATUS_SETUP_DONE)
            Log.d(TAG,"disableShockNotification enter");
        BluetoothGattCharacteristic characteristic = mBleController.getCharacteristic(BleModel.UUID_SHOCK_COUNT);
        if(null == characteristic){
            Log.d(TAG,"disableShockNotification characteristic null");
            return true;
        }
        boolean r = mBleController.mBleService.setCharacteristicNotification(characteristic,false);
        Log.d(TAG,"disableShockNotification return " + (r?"true":"false"));
        return r;
    }

    boolean setShockNotification() {
        if(mBleController.ble_status < mBleController.STATUS_SETUP_DONE)
        Log.d(TAG,"setShockNotification enter");
        BluetoothGattCharacteristic characteristic = mBleController.getCharacteristic(BleModel.UUID_SHOCK_COUNT);
        if(null == characteristic){
            Log.d(TAG,"setShockNotification characteristic null");
            return true;
        }
        boolean r = mBleController.mBleService.setCharacteristicNotification(characteristic,true);
        Log.d(TAG,"setShockNotification return " + (r?"true":"false"));
        return r;
    }

    boolean setRelay(final boolean enable) {

        if(mBleController.ble_status != mBleController.STATUS_AUTHENTICATED) {
            mBleController.onFailed();
            return true;
        }

        String relayId = BleModel.instance().getPrimaryRelayId();
        final BluetoothGattCharacteristic characteristic = mBleController.getCharacteristic(relayId);
        if(null == characteristic){
            mBleController.onFailed();
            return true;
        }

        byte[] vv = new byte[1];
        vv[0] = (byte) (enable?0x01:0x00);
        characteristic.setValue(vv);
        expectingRelayValue = BleUtil.getIntValue(vv,BluetoothGattCharacteristic.FORMAT_UINT8);
        boolean r = writeCharacteristic(characteristic);
        Log.d(TAG,"setRelay write return " + (r?"true":"false"));
        BleControlService.afterWriteAttribute(r, characteristic, null);
        return r;
    }

    boolean clearRelay() {

        String relayId = BleModel.instance().getPrimaryRelayId();
        BluetoothGattCharacteristic characteristic = mBleController.getCharacteristic(relayId);
        if(null == characteristic){
            return true;
        }
        byte[] vv = new byte[1];
        vv[0] = (byte) (0x00);
        characteristic.setValue(vv);
        boolean r = writeCharacteristic(characteristic);
        Log.d(TAG,"clearRelay write return " + (r?"true":"false"));
        return r;
    }

    void logTime(String s, BleUtil.BleTimeItem bleTimeItem){
        Log.d(TAG,String.format(s+" y:%d m:%d d:%d h:%d m:%d s:%d dofw:%d", bleTimeItem.year,
                bleTimeItem.month, bleTimeItem.day, bleTimeItem.hour, bleTimeItem.minute,
                bleTimeItem.second, bleTimeItem.dayOfWeek));
    }

    boolean setTime() {
        if(mBleController.ble_status < mBleController.STATUS_SETUP_DONE) {
            return true;
        }
        byte[] v = BleUtil.getTimeData();
        expectingTimeItem = BleUtil.parseBleTime(v);
        Log.d(TAG,"try set " + BleUtil.bytesToHexStr(v) + " for " + mBleController.mBleDeviceAddress);
        logTime("try set ",expectingTimeItem);
        final BluetoothGattCharacteristic characteristic = mBleController.getCharacteristic(BleModel.UUID_BLE_TIME);
        if(null == characteristic){
            return true;
        }
        characteristic.setValue(v);
        boolean r =  writeCharacteristic(characteristic);
        Log.d(TAG,"setTime write return " + r);
        BleControlService.afterWriteAttribute(r, characteristic, null);
        return r;
    }

    boolean setImpactThreshold() {

        if(mBleController.ble_status < mBleController.STATUS_SETUP_DONE) {
            return true;
        }

//        if(mBleController.mEquipmentItem.impact_threshold == 0){
//            Log.d(TAG,"setImpactThreshold value is 0");
//            return true;
//        }
        final BluetoothGattCharacteristic characteristic = mBleController.getCharacteristic(BleModel.UUID_SHOCK_THRESHOLD);
        if(null == characteristic){
            Log.d(TAG,"setImpactThreshold no characteristic found");
            return true;
        }
        characteristic.setValue(mBleController.mEquipmentItem.impact_threshold,BluetoothGattCharacteristic.FORMAT_UINT32,0);
        expectingImpactThreshold = mBleController.mEquipmentItem.impact_threshold;
        boolean r = writeCharacteristic(characteristic);
        Log.d(TAG,"setImpactThreshold write " + mBleController.mEquipmentItem.impact_threshold+" return " + (r?"true":"false"));
        BleControlService.afterWriteAttribute(r, characteristic, null);
        return r;
    }

    boolean writeCharacteristic(BluetoothGattCharacteristic characteristic){
        if(mBleController.mBleService != null){
            return mBleController.mBleService.writeCharacteristic(characteristic);
        }
        return false;
    }

    boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBleController.mBleService != null) {
            return mBleController.mBleService.readCharacteristic(characteristic);
        }
        return false;
    }

    void onBleDataRead(Intent intent) {

        String uuid = intent.getStringExtra(BleMachineService.CHARA_UUID);
        if (null == uuid || uuid.length() == 0) {
            return;
        }

        boolean actionSucceed = intent.hasExtra(BleMachineService.STATUS_CODE)
                && intent.getBooleanExtra(BleMachineService.STATUS_CODE,false);

        byte[] data = intent.getByteArrayExtra(BleMachineService.EXTRA_DATA);

        if (uuid.equals(BleModel.UUID_TOKEN_AUTH)) {

//            Log.d(TAG,"auth device  return " + actionSucceed);
//            if(actionSucceed) {
//                pushStateMachine();
//            }
//            else {
//                authDevice();
//            }
        }
        else if (uuid.toLowerCase().equalsIgnoreCase(BleModel.instance().getPrimaryRelayId())) {
            if(mBleController.ble_status != mBleController.STATUS_AUTHENTICATED){
                return;
            }
            int value = BleUtil.getIntValue(data,BluetoothGattCharacteristic.FORMAT_UINT8);
            Log.d(TAG,"relay read return " + value);
            if (actionSucceed && value == expectingRelayValue){
                mBleController.pushStateMachine();
            } else {
                BleControlService.setRelayWithDelay();
            }
        }
        else if (uuid.equals(BleModel.UUID_BLE_TIME)) {

            if(mBleController.ble_status < mBleController.STATUS_SETUP_DONE){
                return;
            }
            Log.d(TAG,"time read return " + data);
            BleUtil.BleTimeItem bleTimeItem = BleUtil.parseBleTime(data);
            logTime("time read return ",bleTimeItem);
            if (BleUtil.isTimeSetSucceed(bleTimeItem,expectingTimeItem) && actionSucceed){
                mBleController.bleTimeSynced = true;
            } else {
                BleControlService.setTime();
            }
        }
        else if (uuid.equals(BleModel.UUID_SHOCK_THRESHOLD)) {

            if(mBleController.ble_status < mBleController.STATUS_SETUP_DONE){
                return;
            }
            int value = BleUtil.getIntValue(data,BluetoothGattCharacteristic.FORMAT_UINT32,0);
            Log.d(TAG,"shock threshold read return " + value);
            if (expectingImpactThreshold  == value && actionSucceed){
                mBleController.bleThresholdSynced = true;
            } else {
                BleControlService.setImpactThreshold();
            }
        }
    }

    void onGattServices(List<BluetoothGattService> gattServices) {

        if(mBleController.ble_status != mBleController.STATUS_CONNECTING) {
            return;
        }

        if (gattServices == null) {
            mBleController.onFailed();
            return;
        }
        mBleController.mGattCharacteristics = new ArrayList<>();
        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                mBleController.mGattCharacteristics.add(gattCharacteristic);
            }
        }
        mBleController.pushStateMachine();
    }

}
