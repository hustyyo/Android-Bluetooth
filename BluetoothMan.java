package com.collectiveintelligence.pandora.BaseFunction;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Message;
import android.util.Log;

import com.collectiveintelligence.pandora.PandoraApplication;
import com.collectiveintelligence.pandora.R;
import com.collectiveintelligence.pandora.UICommon.BluetoothDataItem;
import com.collectiveintelligence.pandora.UICommon.HandlerMessageType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by steveyang on 9/4/15.
 */
public class BluetoothMan {

    private static final String TAG = "BluetoothMan";

    static private BluetoothAdapter mBtAdapter;
    static BluetoothServiceListen bluetoothServiceListen;
    static ArrayList<BluetoothDevice> arrayDevice = new ArrayList<>();
    static Map<String,BluetoothService> mapConnectService = new HashMap<>();

    static public void clear(){
        unMonitorDevice();
        stopListen();
        stopConnect();
    }
    public static void clearConnect(){
        unMonitorDevice();
        arrayDevice.clear();
        stopConnect();
        stopListen();
    }

    public static void clearDevices(){
        arrayDevice.clear();
        stopConnect();
    }

    private static void startListen(){
        bluetoothServiceListen = new BluetoothServiceListen();
        bluetoothServiceListen.start();
    }
    private static void stopListen(){
        bluetoothServiceListen.stop();
    }
    static public void initBluetooth(){
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        startListen();
        monitorBluetooth();
        monitorDevice();
    }

    static boolean checkIfAdded(BluetoothDevice d){
        for(BluetoothDevice device: arrayDevice){
            if(device.getAddress().equals(d.getAddress())){
                return true;
            }
        }
        return false;
    }
    static private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                String name = device.getName();
                String id = device.getAddress();

                notifyUI(BluetoothStatus.DISCOVERED,id,name);

                Log.d(TAG, "Discovered bluetooth device " + name);

                if(!checkIfAdded(device)) {
                    arrayDevice.add(device);
                    connectDevice(device);
                    if(arrayDevice.size() >= 5){
                        stopDiscovery();
                        notifyUI(BluetoothStatus.DISCOVER_STOPPED,"");
                    }
                }
            }
            else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON){
                    Log.d(TAG, "Bluetooth is turned on");
                    stopListen();
                    startListen();
                }
            }
        }
    };

    public static int getDeviceNum(){
        return arrayDevice.size();
    }

    static void connectDevice(BluetoothDevice device){
        String s = device.getAddress();
        BluetoothService service = mapConnectService.get(s);
//        if(service!=null && (service.getState()==BluetoothService.STATE_CONNECTED)){
//            service.sendRequest();
//        }
//        else{
            if(service!=null){
                mapConnectService.remove(s);
                service.stop();
            }
            service = new BluetoothService();
            mapConnectService.put(device.getAddress(), service);
            service.connect(device, false);
//        }
    }
    public static boolean startConnect(){
        if(arrayDevice.size()==0){
            return false;
        }

        for(BluetoothDevice device: arrayDevice){
            connectDevice(device);
            String s = device.getName();
            Log.d(TAG, "Connecting bluetooth device " + s);
        }
        return true;
    }

    private static void monitorDevice(){
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        PandoraApplication.getAppContex().registerReceiver(mReceiver, filter);
        Log.d(TAG, "Start monitor bluetooth device");
    }
    private static void unMonitorDevice(){
        PandoraApplication.getAppContex().unregisterReceiver(mReceiver);
        Log.d(TAG, "Stop monitor bluetooth device");
    }

    private static void stopConnect(){
        for(Map.Entry<String,BluetoothService> entry: mapConnectService.entrySet()){
            BluetoothService service = entry.getValue();
            service.stop();
        }
        mapConnectService.clear();
    }

    static public void stopDiscovery(){
        Log.d(TAG, "Stop bluetooth discover");
        if(mBtAdapter != null){
            mBtAdapter.cancelDiscovery();
        }

    }
    static public boolean isDiscovering(){
        if(mBtAdapter != null){
            return mBtAdapter.isDiscovering();
        }
        return false;
    }

    static public void startDiscovery(){
        Log.d(TAG, "Start bluetooth discover");
        if(mBtAdapter != null){
            mBtAdapter.startDiscovery();
        }
    }

    static public void monitorBluetooth(){
        PandoraApplication.getAppContex().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    static public boolean isBluetoothEnable(){
        if(mBtAdapter != null){
            return mBtAdapter.isEnabled();
        }
        return false;
    }

    static public boolean isInsecureBluetoothSupport(){
        if(null == mBtAdapter){
            return false;
        }

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1){
            return false;
        }
//        return BluetoothServiceListen.isInsecureBluetoothSupport();
        return true;
    }

    static public void parseMessage(Message inputMessage, BluetoothDataItem item){
        BluetoothStatus status = BluetoothStatus.values()[inputMessage.arg1];
        item.bluetoothStatus = status;

        String s = (String)inputMessage.obj;
        if(s!=null && s.length()>0){
            int index = s.indexOf(';');
            item.itemName = s.substring(0,index);
            item.itemId = s.substring(index+1,s.length());
        }
    }

    static public void notifyUI(BluetoothStatus status, String id, String name){
        String s = makeBluetoothMessage(id,name);
        notifyUI(status,s);
    }

    static public void notifyUI(BluetoothStatus status, String s){
        android.os.Handler handler = PandoraApplication.getHandler();
        Message msg = handler.obtainMessage();
        msg.what = HandlerMessageType.BLUETOOTH_MESSAGE.ordinal();
        msg.arg1 = status.ordinal();
        msg.obj = s;
        handler.sendMessage(msg);
    }

    static public String makeBluetoothMessage(String id, String name){
        String s = name + ";" + id;
        return s;
    }

    static public String getStringByStatus(BluetoothStatus status){
        if(status==BluetoothStatus.DISCOVERED){
            return PandoraApplication.getAppContex().getResources().getString(R.string.add_user_bluetooth_status_discovered);
        }
        if(status==BluetoothStatus.CONNECTING || status==BluetoothStatus.CONNECTED){
            return PandoraApplication.getAppContex().getResources().getString(R.string.add_user_bluetooth_status_connecting);
        }
        if(status==BluetoothStatus.FAILED){
            return PandoraApplication.getAppContex().getResources().getString(R.string.add_user_bluetooth_status_failed);
        }
        return "";
    }
}
