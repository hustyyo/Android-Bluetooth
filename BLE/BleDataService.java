package au.com.collectiveintelligence.fleetiq360.WebService.BLE;

import android.app.IntentService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import au.com.collectiveintelligence.fleetiq360.ui.application.MyApplication;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class BleDataService extends JobIntentService {

    public static String ACTION_READ_SHOCK_COUNT = "com.BleDataService.ACTION_READ_SHOCK_COUNT";
    public static String TAG = "CI_BLE_SHOCK_EVENT" + BleDataService.class.getSimpleName();
    static BleMachine mBleMachine = null;
    static BleController mBleController = null;


    public void processBleEvents(Intent intent) {

        String action = intent.getAction();
        if(null == action){
            return;
        }

        if(action.equals(ACTION_READ_SHOCK_COUNT)){
            Log.d(TAG,"shock event received action to read count ");

            readShockCount();
            return;
        }

        if(!BleController.instance().isBroadcastNeedToHandle(intent)){
            Log.d(TAG,"shock event Received ble event but no need to handle");
            return;
        }

        String address = intent.getStringExtra(BleMachineService.DEVICE_ADDRESS);
        String uuid = intent.getStringExtra(BleMachineService.CHARA_UUID);
        Log.d(TAG,"shock event Received event with uuid " + uuid);
        if (BleMachineService.ACTION_DATA_AVAILABLE.equals(action) || BleMachineService.ACTION_DATA_CHANGED.equals(action)) {
            Log.d(TAG,"shock event Received data event " + action);

            onShockDataRead(address,intent);

        }
    }

    @Override
    protected void onHandleWork(Intent intent) {

        if(null == mBleMachine){
            mBleMachine = BleController.instance().mBleMachine;
        }
        if(null == mBleController){
            mBleController= BleController.instance();
        }
        processBleEvents(intent);
    }

    public static void sendAction(String action) {

        Log.d(TAG,"shock event sendAction ");

        Intent dataIntent = new Intent(action);
        MyApplication.startIntentService(BleDataService.class,MyApplication.JOB_INTENT_SERVICE_ID_BleDataService, dataIntent);

    }


    public boolean readShockEvent() {

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Log.d(TAG,"shock event readShockEvent enter ");

        if(mBleController.ble_status != mBleController.STATUS_SETUP_DONE) {
            return true;
        }
        BluetoothGattCharacteristic characteristic = mBleController.getCharacteristic(BleModel.UUID_SHOCK_EVENT_ITEM);
        if(null != characteristic){
            boolean r = mBleMachine.readCharacteristic(characteristic);
            Log.d(TAG,"shock event readShockEvent read return " + (r?"true":"false"));
            return r;
        }
        return true;
    }

    boolean popShockEvent() {
        Log.d(TAG,"shock event popShockEvent enter ");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        BluetoothGattCharacteristic characteristic = mBleController.getCharacteristic(BleModel.UUID_POP_SHOCK_EVENT);
        if(null != characteristic){
            byte[] v = {0x01};
            characteristic.setValue(v);
            boolean r = mBleMachine.writeCharacteristic(characteristic);
            Log.d(TAG,"shock event popShockEvent write return " + (r?"true":"false"));
            return r;
        }
        return true;
    }

    void onShockDataRead(String address, Intent intent) {

        if(mBleController.ble_status != mBleController.STATUS_SETUP_DONE){
            return;
        }
        String uuid = intent.getStringExtra(BleMachineService.CHARA_UUID);
        if (null == uuid || uuid.length() == 0) {
            return;
        }
        boolean actionSucceed = intent.hasExtra(BleMachineService.STATUS_CODE)
                && intent.getBooleanExtra(BleMachineService.STATUS_CODE,false);

        if (uuid.equals(BleModel.UUID_SHOCK_COUNT)) {

            if (actionSucceed) {

                byte[] data = intent.getByteArrayExtra(BleMachineService.EXTRA_DATA);
                Log.d(TAG,"shock event count read return raw " + BleUtil.bytesToHexStr(data));
                Integer count = BleUtil.getIntValue(data,BluetoothGattCharacteristic.FORMAT_UINT16);

                onShockCountRead(count);

            } else {

                readShockCount();

            }
        }
        else if (uuid.equals(BleModel.UUID_SHOCK_EVENT_ITEM)) {

            if (actionSucceed) {
                popShockEvent();
            }

            readShockCount();
        }
    }

    void onShockCountRead(Integer count) {

        Log.d(TAG,"shock event count returned " + count);
        if(mBleController.ble_status != mBleController.STATUS_SETUP_DONE) {
            return;
        }

        if(null != count && count > 0){
            boolean r = readShockEvent();
            if(!r){
                readShockCount();
            }
        }
        else {
            mBleMachine.setShockNotification();
        }
    }

    boolean readShockCount() {

        mBleMachine.disableShockNotification();

        Log.d(TAG,"shock event readShockCount enter");
        if(mBleController.ble_status < mBleController.STATUS_SETUP_DONE) {
            Log.d(TAG,"shock event readShockCount abort");
            return true;
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        BluetoothGattCharacteristic characteristic = mBleController.getCharacteristic(BleModel.UUID_SHOCK_COUNT);
        if(null == characteristic){
            Log.d(TAG,"shock event readShockCount characteristic null");
            return true;
        }
        boolean r = mBleMachine.readCharacteristic(characteristic);
        Log.d(TAG,"shock event readShockCount read return " + (r?"true":"false"));

        if(!r){
            readShockCount();
        }

        return r;
    }

    public static void sendData(Intent intent) {
        Intent dataIntent = new Intent(intent.getAction());
        dataIntent.putExtras(intent);
        MyApplication.startIntentService(BleDataService.class,MyApplication.JOB_INTENT_SERVICE_ID_BleDataService, dataIntent);
    }

}
