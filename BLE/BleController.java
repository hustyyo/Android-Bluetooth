package au.com.collectiveintelligence.fleetiq360.WebService.BLE;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import au.com.collectiveintelligence.fleetiq360.WebService.webserviceclasses.EquipmentItem;
import au.com.collectiveintelligence.fleetiq360.ui.application.MyApplication;

/**
 * Created by steveyang on 10/6/17.
 */

public class BleController {
    private final String TAG = "CI_BLE";

    //Return code
    private static int EQUIPMENT_CONNECT_CODE_NONE = 0;
    public final static int EQUIPMENT_CONNECT_CODE_BLE_NOT_ON = ++EQUIPMENT_CONNECT_CODE_NONE;
    public final static int EQUIPMENT_CONNECT_CODE_BLE_FAILED = ++EQUIPMENT_CONNECT_CODE_NONE;
    public final static int EQUIPMENT_CONNECT_CODE_BUSY = ++EQUIPMENT_CONNECT_CODE_NONE;
    public final static int EQUIPMENT_CONNECT_CODE_INVALID_DATA = ++EQUIPMENT_CONNECT_CODE_NONE;

    //Status
    public static int STATUS_NONE = 0;
    public final static int STATUS_IDLE = ++STATUS_NONE;
    public final static int STATUS_CONNECTING = ++STATUS_NONE;
    public final static int STATUS_SERVICE_FOUND = ++STATUS_NONE;
    public final static int STATUS_AUTHENTICATED = ++STATUS_NONE;
    public final static int STATUS_SETUP_RELAY_DONE = ++STATUS_NONE;
    public final static int STATUS_SETUP_DONE = STATUS_SETUP_RELAY_DONE;
    public int ble_status = STATUS_IDLE;

    //Relay set done
    boolean relaySetDone = false;

    //Data
    public static final int SCAN_PERIOD = 10*1000;
    public static final int CONNECT_PERIOD = 30*1000;
    public BluetoothAdapter mBluetoothAdapter;
    public BluetoothLeScanner bleScanner;
    public Object scanCallback;
    public EquipmentListener equipmentListener;
    public boolean mScanning;
    public BleMachineService mBleService;
    BleMachine mBleMachine;
    public Context mContext;
    public EquipmentItem mEquipmentItem;
    public String mBleDeviceAddress = "";
    BluetoothDevice mBleDevice = null;
    public ArrayList<BluetoothGattCharacteristic> mGattCharacteristics = new ArrayList<>();
    public boolean bleTimeSynced = false;
    public boolean bleThresholdSynced = false;

    BroadcastReceiver bluetoothEnableReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG,"Bluetooth off");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG,"Bluetooth on and start auto connect");
                        BleControlService.sendAction(BleControlService.ACTION_AUTO_RECONNECT);
                        break;
                }
            }
        }
    };
    void registerBluetoothEnableCallback() {
        LocalBroadcastManager.getInstance(MyApplication.getContext()).registerReceiver(bluetoothEnableReceiver,new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    void unRegisterBluetoothEnable(){
        try {
            LocalBroadcastManager.getInstance(MyApplication.getContext()).unregisterReceiver(bluetoothEnableReceiver);
        }
        catch (Exception e){

        }
    }

    public static ArrayList<BluetoothDevice> cachedBleDevices = new ArrayList<>();
    void addDevice(BluetoothDevice bluetoothDevice){
        Log.d(TAG,"ble scan cache device " + bluetoothDevice.getAddress());
        ArrayList<BluetoothDevice> toRemove = new ArrayList<>();
        for(BluetoothDevice device : cachedBleDevices){
            if(device.getAddress().equals(bluetoothDevice.getAddress())){
                toRemove.add(device);
            }
        }
        cachedBleDevices.removeAll(toRemove);
        cachedBleDevices.add(bluetoothDevice);
    }
    BluetoothDevice getCachedDevice(String address){
        for(BluetoothDevice device : cachedBleDevices){
            if(device.getAddress().equals(address)){
                return device;
            }
        }
        return null;
    }

    public boolean isDeviceSetupDone(String address) {
        if(mBleDeviceAddress.equals(address)){
            if(ble_status >= STATUS_SETUP_DONE){
                return true;
            }
        }
        return false;
    }
    public boolean isDeviceDisconnected(String address) {
        if(mBleDeviceAddress.equals(address)){
            if(ble_status == STATUS_IDLE){
                return true;
            }
        }
        return false;
    }

    boolean stillNeedToConnectEquipment(){
        if(mEquipmentItem == null){
            return false;
        }
        return true;
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            onBleServiceConnected(service);
            mBleService.connect(mBleDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

            onBleServiceDisconnected();
        }
    };

    void onBleServiceConnected(IBinder service) {
        mBleService = ((BleMachineService.LocalBinder) service).getService();
        if (null == mBleService || !mBleService.initialize()) {
            onFailed();
            return;
        }
    }

    public void onBleServiceDisconnected() {
        onFailed();
        mBleService = null;
    }

    boolean isBroadcastNeedToHandle(Intent intent) {

        if(ble_status == STATUS_IDLE){
            return false;
        }
        if(!intent.hasExtra(BleMachineService.DEVICE_ADDRESS)){
            return false;
        }

        String address = intent.getStringExtra(BleMachineService.DEVICE_ADDRESS);
        if(address == null || !address.toLowerCase().equalsIgnoreCase(mBleDeviceAddress)){
            return false;
        }

        return true;
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if(mBleService != null){
            return mBleService.getSupportedGattServices();
        }

        return new ArrayList<>();
    }

    public void discoverServices(){
        if(ble_status != BleController.STATUS_SERVICE_FOUND &&
                ble_status != BleController.STATUS_CONNECTING){
            return;
        }
        if(mBleService != null){
            mBleService.discoverServices();
        }
    }

    public void autoReconnect(){

        if(!stillNeedToConnectEquipment()){
            Log.d(TAG,"auto reconnect Not ready to run");
            return;
        }
        Log.d(TAG,"auto reconnect start");
        startConnect(relaySetDone,true,mEquipmentItem,equipmentListener);
    }

    private void startBleService() {
        Intent gattServiceIntent = new Intent(mContext, BleMachineService.class);
        if(mBleService != null){
            mBleService.connect(mBleDeviceAddress);
        }
        else {
            mContext.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void startBleService(final BluetoothDevice device) {
        Intent gattServiceIntent = new Intent(mContext, BleMachineService.class);
        if(mBleService != null){
            mBleService.connect(device);
        }
        else {
            mContext.bindService(gattServiceIntent, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    onBleServiceConnected(service);
                    mBleService.connect(device);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    onBleServiceDisconnected();
                }
            }, Context.BIND_AUTO_CREATE);
        }
    }

    void stopBleService(){
        if(mBleService != null){
            mBleService.disconnect();
            mBleService.onUnbind(new Intent());
        }
    }

    public void stopConnection() {
        ble_status = STATUS_IDLE;

        unRegisterBluetoothEnable();
        stopScanLeDevice();
        clearRelay();
    }

    static int clearRelayRetryTime = 0;

    void clearConnection(){
        stopBleService();
        mGattCharacteristics.clear();
        mBleDeviceAddress = "";
        mEquipmentItem = null;
        mBluetoothAdapter = null;
    }
    void clearRelay() {
        boolean r = mBleMachine.clearRelay();
        if(!r) {
            MyApplication.runLater(new Runnable() {
                @Override
                public void run() {
                    clearRelayRetryTime++;
                    if(clearRelayRetryTime < 3){
                        clearRelay();
                    }
                    else {
                        clearConnection();
                    }
                }
            },1000);
        }
        else {
            MyApplication.runLater(new Runnable() {
                @Override
                public void run() {
                    clearConnection();
                }
            },5000);
        }
    }

    void stopScanLeDevice() {
        if(mScanning){
            stopScanner();
        }
        mScanning = false;
    }

    void stopScanner() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if(bleScanner != null){
                bleScanner.stopScan((ScanCallback) scanCallback);
            }
        }
        else {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    private void scanLeDevice() {

        if(ble_status != STATUS_CONNECTING){
            return;
        }

        if(null == mBluetoothAdapter || !mBluetoothAdapter.isEnabled()){
            onFailed();
            return;
        }

        stopScanLeDevice();
        scanRetryCount = 0;
        scanDeviceFunc();
    }

    static int scanRetryCount = 0;

    void scanDeviceFunc() {

        if(mScanning){
            return;
        }

        if(null == mBluetoothAdapter || !mBluetoothAdapter.isEnabled()){
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            bleScanner = mBluetoothAdapter.getBluetoothLeScanner();
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mLeScanCallback.onLeScan (result.getDevice(),result.getRssi(),result.getScanRecord().getBytes());
                    }
                }
            };
            mScanning = true;
            bleScanner.startScan((ScanCallback) scanCallback);

        } else {
            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }

//        MyApplication.runLater(new Runnable() {
//            @Override
//            public void run() {
//                if(mScanning){
//                    stopScanLeDevice();
//                    scanRetryCount++;
////                    if(scanRetryCount < 2){
//                        MyApplication.runLater(new Runnable() {
//                            @Override
//                            public void run() {
//                                scanDeviceFunc();
//                            }
//                        },1000);
////                    }
//                }
//            }
//        },4000);
    }


    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

                    addDevice(device);

                    String address = device.getAddress();
                    if(!address.equalsIgnoreCase(mBleDeviceAddress)){
                        return;
                    }
                    if(mScanning){
                        stopScanLeDevice();
                        startBleService();
                    }
                }
            };


    public void preStartCheck(boolean relaySet, final boolean autoReconnect, final EquipmentItem equipmentItem, final EquipmentListener listener){

        if(ble_status != STATUS_IDLE){
            if(listener != null) {
                listener.onFailed(EQUIPMENT_CONNECT_CODE_BUSY);
            }
            Log.d(TAG,"start connect busy return device " + equipmentItem.mac_address);

            return;
        }

        if(equipmentItem == null) {
            if(listener != null) {
                listener.onFailed(EQUIPMENT_CONNECT_CODE_INVALID_DATA);
            }
            Log.d(TAG,"start connect invalid data return device " + equipmentItem.mac_address);

            return;
        }

        if(equipmentItem.mac_address == null || equipmentItem.mac_address.length()==0){
            if(listener != null) {
                listener.onFailed(EQUIPMENT_CONNECT_CODE_INVALID_DATA);
            }
            Log.d(TAG,"start connect invalid address return device " + equipmentItem.mac_address);

            return;
        }

        relaySetDone = relaySet;
        new AsyncTask(){
            @Override
            protected Object doInBackground(Object[] params) {
                final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = bluetoothManager.getAdapter();
                return null;
            }

            @Override
            protected void onPostExecute(Object o) {
                if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                    if(listener != null) {
                        listener.onFailed(EQUIPMENT_CONNECT_CODE_BLE_NOT_ON);
                    }
                }
                else {
                    discoverDevice(autoReconnect,equipmentItem,listener);
                }
            }
        }.execute();
//        MyApplication.runOnAsyncTask(new Runnable() {
//            @Override
//            public void run() {
//                final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
//                mBluetoothAdapter = bluetoothManager.getAdapter();
//                MyApplication.runOnMainThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
//                            if(listener != null) {
//                                listener.onFailed(EQUIPMENT_CONNECT_CODE_BLE_NOT_ON);
//                            }
//                        }
//                        else {
//                            discoverDevice(equipmentItem,listener);
//                        }
//                    }
//                });
//            }
//        });
        return;
    }

    void preStart(EquipmentItem equipmentItem, EquipmentListener listener) {
        ble_status = STATUS_CONNECTING;
        bleTimeSynced = false;
        bleThresholdSynced = false;
        mEquipmentItem = equipmentItem;
        mBleDeviceAddress = equipmentItem.mac_address;
        equipmentListener = listener;
    }


    public void startConnect(boolean relaySet, boolean autoReconnect, EquipmentItem equipmentItem, EquipmentListener listener){

        Log.d(TAG,"start connect equipment " + equipmentItem.mac_address);
        registerBluetoothEnableCallback();
        preStartCheck(relaySet,autoReconnect,equipmentItem,listener);
    }

    void discoverDevice(boolean autoReconnect, EquipmentItem equipmentItem, EquipmentListener listener){
        Log.d(TAG,"start ble connect device " + equipmentItem.mac_address);
        preStart(equipmentItem,listener);

        stopScanLeDevice();

        BluetoothDevice device = getCachedDevice(mBleDeviceAddress);
        if(device != null && device.getAddress().toLowerCase().equalsIgnoreCase(mBleDeviceAddress)){
            startBleService(device);
            Log.d(TAG,"start ble connect device use cached device " + equipmentItem.mac_address);
        }
        else {
            Log.d(TAG,"start ble connect device start scan " + equipmentItem.mac_address);
            scanLeDevice();
        }

        if(!autoReconnect){
            MyApplication.runLater(new Runnable() {
                @Override
                public void run() {
                    onSetupTimeout();
                }
            },CONNECT_PERIOD);
        }
    }

    void onSetupTimeout() {
        if(ble_status < STATUS_SETUP_DONE && equipmentListener != null) {
            stopConnection();
            if(equipmentListener != null){
                equipmentListener.onFailed(EQUIPMENT_CONNECT_CODE_BLE_FAILED);
                equipmentListener = null;
            }
        }
    }

    public static class EquipmentListener {

        public void onSucceed() {

        }

        public void onFailed(int errorCode) {

        }
    }

    void onFailed() {
        MyApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if(equipmentListener != null){
                    equipmentListener.onFailed(EQUIPMENT_CONNECT_CODE_BLE_FAILED);
                    equipmentListener = null;
                }
                stopConnection();
            }
        });
    }

    void onSetupDone() {
        MyApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if(equipmentListener != null) {
                    equipmentListener.onSucceed();
                    equipmentListener = null;
                }
                startTimer();
            }
        });

    }

    void pushStateMachine() {

        if(ble_status == STATUS_IDLE){
            return;
        }

        ++ble_status;

        Log.d(TAG,"Ble status is " + ble_status);

        if(ble_status == STATUS_SERVICE_FOUND){
            mBleMachine.onServiceFound();
        }
        else if(ble_status == STATUS_AUTHENTICATED){
            Log.d(TAG,"status Auth done");
            if(!relaySetDone){
                BleControlService.setRelayNoDelay();
            }
            else {
                pushStateMachine();
            }
        }
        else if(ble_status == STATUS_SETUP_RELAY_DONE){
            onRelaySetDone();
        }
    }

    void onRelaySetDone() {
        Log.d(TAG,"status set relay done");
        relaySetDone = true;
        onSetupDone();
        BleControlService.initDevice();
    }


    public Handler myHandler = new Handler();
    int timerDuration = 5*60*1000;
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if(ble_status != STATUS_SETUP_DONE){
                return;
            }
            BleDataService.sendAction(BleDataService.ACTION_READ_SHOCK_COUNT);
            myHandler.postDelayed(this, timerDuration);
        }
    };
    public void startTimer() {
        myHandler.removeCallbacks(timerRunnable);
        myHandler.postDelayed(timerRunnable, timerDuration);
    }


    BluetoothGattCharacteristic getCharacteristic(String uuid) {
        if(mGattCharacteristics == null){
            return null;
        }

        for(BluetoothGattCharacteristic characteristic : mGattCharacteristics) {
            String s = characteristic.getUuid().toString();
            if(s.toLowerCase().equalsIgnoreCase(uuid)){
                return characteristic;
            }
        }
        return null;
    }

    public void init(Context context) {
        mContext = context;
        BleModel.instance();
    }

    private static BleController ourInstance;
    public static synchronized BleController instance()
    {
        if (ourInstance == null){
            ourInstance = new BleController();
        }

        return ourInstance;
    }

    public BleController() {
        mBleMachine = new BleMachine(this);
    }
}

