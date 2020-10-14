package au.com.collectiveintelligence.fleetiq360.WebService.BLE;

import android.app.IntentService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.support.v4.app.JobIntentService;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.List;

import au.com.collectiveintelligence.fleetiq360.ui.application.MyApplication;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class BleControlService extends JobIntentService {

    public static String ACTION_AUTO_RECONNECT = "com.BleDataService.ACTION_AUTO_RECONNECT";
    public static String ACTION_INIT_DEVICE = "com.BleDataService.ACTION_INIT_DEVICE";
    private static String ACTION_AUTH_DEVICE = "com.BleDataService.ACTION_AUTH_DEVICE";
    private static String ACTION_SET_RELAY = "com.BleDataService.ACTION_SET_RELAY";
    private static String ACTION_SET_TIME = "com.BleDataService.SET_TIME";
    private static String ACTION_SET_IMPACT_THRESHOLD = "com.BleDataService.ACTION_SET_IMPACT_THRESHOLD";
    private static String ACTION_ENABLE_SHOCK_NOTIFICATION = "com.BleDataService.ACTION_ENABLE_SHOCK_NOTIFICATION";
    public static String TAG = "CI_BLE_" + BleControlService.class.getSimpleName();
    static BleMachine mBleMachine = null;


    public void processBleEvents(Intent intent) {

        String action = intent.getAction();
        if(null == action){
            return;
        }

        if(handleBleOperation(intent)){
            return;
        }

        if(!BleController.instance().isBroadcastNeedToHandle(intent)){
            Log.d(TAG,"Received ble event but no need to handle");
            return;
        }

        if (BleMachineService.ACTION_GATT_CONNECTED.equals(action)) {
            Log.d(TAG,"Received connected event");
        }
        else if (BleMachineService.ACTION_GATT_DISCONNECTED.equals(action)) {
            Log.d(TAG,"Received disconnected event");

            if(BleController.instance().ble_status != BleController.STATUS_IDLE){

                if(BleController.instance().ble_status == BleController.STATUS_SETUP_DONE){
                    // do not autoreconnect, notify user to choose instead
//                    BleController.instance().equipmentListener = new BleController.EquipmentListener(){
//                        @Override
//                        public void onSucceed() {
//                        }
//
//                        @Override
//                        public void onFailed(int errorCode) {
//                            //sendActionWithDelay(ACTION_AUTO_RECONNECT,5000);
//                        }
//                    };
                    BleController.instance().ble_status = BleController.STATUS_IDLE;
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                }
                else {
                    BleController.instance().ble_status = BleController.STATUS_IDLE;
                    BleController.instance().autoReconnect();
                }

            }
        }
        else if (BleMachineService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

            List<BluetoothGattService> services = BleController.instance().getSupportedGattServices();
            Log.d(TAG,"Service discorvered " + (services!=null?services.size():"null"));
            mBleMachine.onGattServices(services);
        }
        else {
            String address = intent.getStringExtra(BleMachineService.DEVICE_ADDRESS);
            String uuid = intent.getStringExtra(BleMachineService.CHARA_UUID);
            Log.d(TAG,"Received event with uuid " + uuid);

            if (BleMachineService.ACTION_DATA_AVAILABLE.equals(action) || BleMachineService.ACTION_DATA_CHANGED.equals(action)) {
                Log.d(TAG,"Received data event " + action);
                mBleMachine.onBleDataRead(intent);
            }
        }
    }

    boolean handleBleOperation(Intent intent){

        String action = intent.getAction();
        boolean processed = false;
        boolean result = true;

        boolean isInitDevice = action.equals(ACTION_INIT_DEVICE);

        if(action.equals(ACTION_AUTH_DEVICE)){
            result = mBleMachine.authDevice();
            if(!result){
                mBleMachine.authDevice();
            }
            else {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mBleMachine.onAuthDone();
            }
            processed = true;
        }

        if(action.equals(ACTION_AUTO_RECONNECT)){
            BleController.instance().autoReconnect();
            processed = true;
        }

        if(action.equals(ACTION_SET_RELAY)){
            result = mBleMachine.setRelay(true);
            if(!result){
                setRelayWithDelay();
            }
            processed = true;
        }

        if(action.equals(ACTION_SET_TIME) || isInitDevice){
            result = mBleMachine.setTime();
            if(!result){
                setTime();
            }
            processed = true;
        }

        if(action.equals(ACTION_SET_IMPACT_THRESHOLD) || isInitDevice){
            result = mBleMachine.setImpactThreshold();
            if(!result){
                setImpactThreshold();
            }
            processed = true;
        }

        if(action.equals(ACTION_ENABLE_SHOCK_NOTIFICATION) || isInitDevice){
            result = mBleMachine.setShockNotification();
            if(!result){
                setShockNotification();
            }
            processed = true;
        }


        return processed;
    }

    @Override
    protected void onHandleWork(Intent intent) {
        if(null == mBleMachine){
            mBleMachine = BleController.instance().mBleMachine;
        }
        processBleEvents(intent);
    }

    public static void authDevice() {
        BleControlService.sendAction(BleControlService.ACTION_AUTH_DEVICE);
    }

    public static void sendData(Intent intent, String uuid) {

        if (uuid.equals(BleModel.UUID_SHOCK_COUNT) || uuid.equals(BleModel.UUID_SHOCK_EVENT_ITEM)) {
            BleDataService.sendData(intent);
            ShockEventService.sendData(intent);
        }
        else {
            Intent dataIntent = new Intent(intent.getAction());
            dataIntent.putExtras(intent);
            MyApplication.startIntentService(BleControlService.class,MyApplication.JOB_INTENT_SERVICE_ID_BleControlService, dataIntent);
        }
    }

    private static void sendActionWithDelay(String action, int delayInMilli) {
        final Intent dataIntent = new Intent(action);
        MyApplication.runLater(new Runnable() {
            @Override
            public void run() {
                MyApplication.startIntentService(BleControlService.class,MyApplication.JOB_INTENT_SERVICE_ID_BleControlService, dataIntent);
            }
        },delayInMilli);
    }

    public static void sendAction(String action) {
        Intent dataIntent = new Intent(action);
        MyApplication.startIntentService(BleControlService.class,MyApplication.JOB_INTENT_SERVICE_ID_BleControlService, dataIntent);
    }

    public static void setRelayWithDelay() {
        try {
            Thread.sleep(2*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mBleMachine.setRelay(true);
    }

    public static void setRelayNoDelay() {
        BleControlService.sendAction(BleControlService.ACTION_SET_RELAY);
    }

    public static void setTime() {
        BleControlService.sendActionWithDelay(BleControlService.ACTION_SET_TIME, 2*1000);
    }

    public static void initDevice(){
        BleControlService.sendAction(BleControlService.ACTION_INIT_DEVICE);
        BleDataService.sendAction(BleDataService.ACTION_READ_SHOCK_COUNT);
    }

    public static void setImpactThreshold() {
        BleControlService.sendActionWithDelay(BleControlService.ACTION_SET_IMPACT_THRESHOLD, 2*1000);
    }

    public static void setShockNotification() {
        BleControlService.sendActionWithDelay(BleControlService.ACTION_ENABLE_SHOCK_NOTIFICATION, 2*1000);
    }

    public static void afterWriteAttribute(boolean r, final BluetoothGattCharacteristic characteristic, Runnable failedRunnable) {
        if(r){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mBleMachine.readCharacteristic(characteristic);
        }
        else {
            if(failedRunnable != null){
                failedRunnable.run();
            }
        }
    }
}
