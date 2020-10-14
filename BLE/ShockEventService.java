package au.com.collectiveintelligence.fleetiq360.WebService.BLE;

import android.app.IntentService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.support.v4.app.JobIntentService;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;

import au.com.collectiveintelligence.fleetiq360.WebService.WebApi;
import au.com.collectiveintelligence.fleetiq360.WebService.WebData;
import au.com.collectiveintelligence.fleetiq360.WebService.WebListener;
import au.com.collectiveintelligence.fleetiq360.WebService.WebResult;
import au.com.collectiveintelligence.fleetiq360.WebService.webserviceclasses.EquipmentItem;
import au.com.collectiveintelligence.fleetiq360.WebService.webserviceclasses.SaveShockEventItem;
import au.com.collectiveintelligence.fleetiq360.WebService.webserviceclasses.parameters.SaveShockEventParameter;
import au.com.collectiveintelligence.fleetiq360.WebService.webserviceclasses.results.CommonResult;
import au.com.collectiveintelligence.fleetiq360.WebService.webserviceclasses.results.SessionResult;
import au.com.collectiveintelligence.fleetiq360.model.MyCommonValue;
import au.com.collectiveintelligence.fleetiq360.ui.application.MyApplication;
import io.realm.Realm;
import io.realm.RealmResults;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class ShockEventService extends JobIntentService {

    public static String TAG = "CI_BLE_SHOCK_EVENT"+ShockEventService.class.getSimpleName();
    RealmResults<ShockEventsDb> shockEventsDbs = null;

    static Realm realmInstance;

    @Override
    protected void onHandleWork(Intent intent) {

        String action = intent.getAction();
        if(null == action){

            uploadEvent();
            MyApplication.runLater(new Runnable() {
                @Override
                public void run() {
                    startService();
                }
            },10*60*1000);
        }
        else {

            String address = intent.getStringExtra(BleMachineService.DEVICE_ADDRESS);
            String uuid = intent.getStringExtra(BleMachineService.CHARA_UUID);
            Log.d(TAG,"Received event with uuid " + uuid);

            if (BleMachineService.ACTION_DATA_AVAILABLE.equals(action) || BleMachineService.ACTION_DATA_CHANGED.equals(action)) {
                Log.d(TAG,"Received data event " + action);
                onShockDataRead(address,intent);
            }
        }
    }

    void onShockDataRead(String address, Intent intent) {

        String uuid = intent.getStringExtra(BleMachineService.CHARA_UUID);
        if (null == uuid || uuid.length() == 0) {
            return;
        }
        boolean actionSucceed = intent.hasExtra(BleMachineService.STATUS_CODE)
                && intent.getBooleanExtra(BleMachineService.STATUS_CODE,false);

        if (uuid.equals(BleModel.UUID_SHOCK_EVENT_ITEM)) {

            if (actionSucceed) {
                byte[] data = intent.getByteArrayExtra(BleMachineService.EXTRA_DATA);
                saveShockEvent(address,data);
            }
        }
    }

    void uploadEvent() {
        realmInstance = Realm.getDefaultInstance();

        shockEventsDbs = ShockEventsDb.readData(realmInstance);

        Log.d(TAG,"upload event enter "+shockEventsDbs.size());
        if(shockEventsDbs.size() == 0){
            return;
        }

        ArrayList<SaveShockEventItem> list = new ArrayList<>();
        final SaveShockEventParameter parameter = new SaveShockEventParameter();

        for(int i = 0; i<100; i++){

            if(i < shockEventsDbs.size()){
                SaveShockEventItem saveShockEventItem = new SaveShockEventItem();
                saveShockEventItem.impact_time = shockEventsDbs.get(i).time;
                saveShockEventItem.mac_address = shockEventsDbs.get(i).mac_address;
                saveShockEventItem.impact_value = (int) shockEventsDbs.get(i).magnitude;
                list.add(saveShockEventItem);
                parameter.impactList = list;
            }
            else {
                if(list.size() == 0){
                    break;
                }
                Log.d(TAG,"upload event start count: "+ list.size());
                WebApi.sync().saveShockEvent(parameter,new WebListener<CommonResult>(){
                    @Override
                    public void onSucceed(CommonResult result) {
                        ShockEventsDb.removeData(realmInstance,parameter);
                        Log.d(TAG,"upload event return succeed ");
                    }

                    @Override
                    public void onFailed(WebResult webResult) {
                        Log.d(TAG,"upload event return failed");
                    }
                });
                break;
            }
        }
        realmInstance.close();
    }


    public static void startService() {
        MyApplication.startIntentService(ShockEventService.class,MyApplication.JOB_INTENT_SERVICE_ID_ShockEventService, new Intent());
    }

    public static void sendData(Intent intent) {
        Intent dataIntent = new Intent(intent.getAction());
        dataIntent.putExtras(intent);
        MyApplication.startIntentService(ShockEventService.class,MyApplication.JOB_INTENT_SERVICE_ID_ShockEventService, dataIntent);
    }

    static ShockEventsItem lastShockEventsItem;

    public static void saveShockEvent(String address, byte[] data) {

        Integer tt = BleUtil.getIntValue(data, BluetoothGattCharacteristic.FORMAT_UINT32,0);
        Integer mm = BleUtil.getIntValue(data,BluetoothGattCharacteristic.FORMAT_UINT32,4);

        long time = 0;
        if(tt != null){
            time = tt.longValue() * 1000;
        }
        long magnitude = 0;
        if(mm != null){
            magnitude = mm;
        }

        ShockEventsItem shockEventsItem = new ShockEventsItem();
        shockEventsItem.mac_address = address;
        shockEventsItem.unixTime = time;
        shockEventsItem.time = BleUtil.getLocalTimeStringFromGmtUnixTime(time);
        shockEventsItem.magnitude = magnitude;

        Log.d(TAG,"shock event item time raw: " +time);
        Log.d(TAG,"shock event item address: " + shockEventsItem.mac_address +
                ", time: " + shockEventsItem.time + ", magnitude: " + shockEventsItem.magnitude);
        Log.d(TAG,"upload event save db");

        boolean duplicated = false;
        if(lastShockEventsItem != null
                && lastShockEventsItem.time.equals(shockEventsItem.time)
                && lastShockEventsItem.mac_address.equals(shockEventsItem.mac_address)){
            duplicated = true;
        }
        if(ShockEventsDb.alreadySaved(shockEventsItem)){
            duplicated = true;
        }
        if(!duplicated){
            showShockAlertIfNeeded(shockEventsItem);
        }
        lastShockEventsItem = shockEventsItem;
        ShockEventsDb.saveData(shockEventsItem);
        startService();
    }

    public static String RED_ALERT = "com.ShockEventService.RED_ALERT";
    public static ShockEventsItem redAlertShockEvent = null;

    static void showShockAlertIfNeeded(ShockEventsItem shockEventsItem) {

        SessionResult sessionResult = WebData.instance().getSessionResult();
        EquipmentItem equipmentItem = MyCommonValue.currentEquipmentItem;

        if(sessionResult != null
                && equipmentItem != null
                && equipmentItem.alert_enabled
                && MyCommonValue.currentEquipmentItem.impact_threshold>0
                && equipmentItem.mac_address.equals(shockEventsItem.mac_address)
                && shockEventsItem.magnitude > equipmentItem.impact_threshold
                ) {

            redAlertShockEvent = shockEventsItem;
            LocalBroadcastManager.getInstance(MyApplication.getContext()).sendBroadcast(new Intent(RED_ALERT));
        }
    }
}
