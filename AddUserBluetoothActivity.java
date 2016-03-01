package com.collectiveintelligence.pandora;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ToggleButton;

import com.collectiveintelligence.pandora.BaseFunction.BluetoothMan;
import com.collectiveintelligence.pandora.BaseFunction.BluetoothStatus;
import com.collectiveintelligence.pandora.BaseFunction.NetworkDetect;
import com.collectiveintelligence.pandora.UICommon.BluetoothDataItem;
import com.collectiveintelligence.pandora.UICommon.HandlerMessageType;
import com.collectiveintelligence.pandora.WebService.User.MessageAddUser;
import com.collectiveintelligence.pandora.WebService.User.ResultAddUser;
import com.collectiveintelligence.pandora.WebService.WebService;
import com.collectiveintelligence.pandora.WebService.WebServiceLogin;
import com.collectiveintelligence.pandora.WebService.WebServiceResult;
import com.collectiveintelligence.pandora.WebService.WebServiceType;

import java.util.ArrayList;


public class AddUserBluetoothActivity extends PandoraActivity {

    Button mActionButton;
    Button refreshButton;
    ListView userListView;
    ListView deviceListView;
    ImageView deviceLoadingView;
    AddUserNearbyArrayAdapter userListAdapter = new AddUserNearbyArrayAdapter(null);
    AddUserNearbyArrayAdapter deviceListAdapter = new AddUserNearbyArrayAdapter(null);
    ArrayList<BluetoothDataItem> userList = new ArrayList<>();
    ArrayList<BluetoothDataItem> deviceList = new ArrayList<>();
    ToggleButton mSupervisorToggle;
    int REQUEST_BLUETOOTH = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.add_user_bluetooth_activity);

        checkChangeOnSelectOption = false;
        isAlertForBackPress = false;

        initViews();

        mUserDataMan.selectUserId(true, false);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        BluetoothMan.initBluetooth();
        if(BluetoothMan.isInsecureBluetoothSupport()){
            requestBluetooth();
        }else{
            bluetoothNotSupport();
        }
    }

    void bluetoothNotSupport(){
        showAlert(getResources().getString(R.string.add_user_bluetooth_not_support));
        refreshButton.setEnabled(false);
    }

    private void requestBluetooth(){
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
        startActivityForResult(discoverableIntent, REQUEST_BLUETOOTH);
    }

    @Override
    public void onDestroy() {
        BluetoothMan.stopDiscovery();
        BluetoothMan.clearConnect();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_BLUETOOTH){
            startBluetooth();
        }
    }

    public WebServiceType getCompanyType(){
        return WebServiceType.COMPANY_FOR_SUPERVISOR;
    }

    @Override
    public void handleMessage(Message inputMessage) {
        if(!isResumed){
            return;
        }

        super.handleMessage(inputMessage);

        if(inputMessage.what == HandlerMessageType.BLUETOOTH_MESSAGE.ordinal()){
            onBluetoothMessage(inputMessage);
        }

    }

    void checkFailed(){
        if(userList.size()>0){
            return;
        }
        if(BluetoothMan.isDiscovering()){
            return;
        }

        if(deviceList.size()==BluetoothMan.getDeviceNum()){
            if(deviceList.size()>0){
                for(BluetoothDataItem item:deviceList){
                    if(item.bluetoothStatus!=BluetoothStatus.FAILED){
                        return;
                    }
                }
                BluetoothMan.clearDevices();
                showAlert(getResources().getString(R.string.add_user_bluetooth_no_device));
            }else{
                BluetoothMan.clearDevices();
                showAlert(getResources().getString(R.string.add_user_bluetooth_no_device));
            }
        }
    }

    void onBluetoothMessage(Message msg){
        BluetoothDataItem item = new BluetoothDataItem();
        BluetoothMan.parseMessage(msg, item);

        if(BluetoothStatus.DISCOVER_STOPPED == item.bluetoothStatus){
            deviceLoadingView.setVisibility(View.GONE);
            return;
        }

        if(BluetoothStatus.FINISH == item.bluetoothStatus){
            for(BluetoothDataItem dataItem : userList){
                if(dataItem.itemId.equals(item.itemId)){
                    return;
                }
            }
            userList.add(item);
            userListAdapter.notifyDataSetChanged();
        }else{
            for(BluetoothDataItem dataItem : deviceList){
                if(dataItem.itemId.equals(item.itemId)){
                    if(BluetoothStatus.CONNECTED == item.bluetoothStatus){
                        deviceList.remove(dataItem);
                    }else{
                        dataItem.bluetoothStatus = item.bluetoothStatus;
                    }
                    deviceListAdapter.notifyDataSetChanged();
                    checkFailed();
                    return;
                }
            }
            if(BluetoothStatus.CONNECTED != item.bluetoothStatus){
                deviceList.add(item);
                deviceListAdapter.notifyDataSetChanged();
                checkFailed();
            }
        }
    }

    public void initOptions() {
        optionTypesAll = new WebServiceType[]{
                WebServiceType.COMPANY_FOR_SUPERVISOR,
        };

        optionTypesOfCurrentUser = new WebServiceType[]{
                WebServiceType.COMPANY_FOR_SUPERVISOR,
        };
    }

    public void initViews() {
        mActionButton = (Button) findViewById(R.id.button_send_add_user);
        mSupervisorToggle = (ToggleButton) findViewById(R.id.toggle_supervisor);
        mActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                onActionButton();
            }
        });

        refreshButton = (Button)findViewById(R.id.add_user_bluetooth_refresh);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRefresh();
            }
        });

        userListView = (ListView)findViewById(R.id.add_user_bluetooth_list);
        userListAdapter.setList(userList);
        userListView.setAdapter(userListAdapter);

        deviceListView = (ListView)findViewById(R.id.device_list);
        deviceListAdapter.setList(deviceList);
        deviceListView.setAdapter(deviceListAdapter);
        deviceLoadingView = (ImageView)findViewById(R.id.list_loading);
        deviceLoadingView.setVisibility(View.GONE);

        initOptionViews(WebServiceType.COMPANY_FOR_SUPERVISOR, R.id.company_option, R.id.company_arrow, R.id.company_loading);

        refreshOptionUI(true);
    }

    void startDiscovery(){
        userList.clear();
        userListAdapter.notifyDataSetChanged();
        deviceList.clear();
        deviceListAdapter.notifyDataSetChanged();
        BluetoothMan.clearDevices();
        BluetoothMan.startDiscovery();

        deviceLoadingView.setVisibility(View.VISIBLE);
        AnimationDrawable animationDrawable = (AnimationDrawable)deviceLoadingView.getDrawable();
        animationDrawable.start();
    }
    void startBluetooth(){

        startDiscovery();

        runLater(new Runnable() {
            @Override
            public void run() {
                deviceLoadingView.setVisibility(View.GONE);
                if (BluetoothMan.isDiscovering()) {
                    BluetoothMan.stopDiscovery();
                    if (BluetoothMan.getDeviceNum() == 0) {
                        showAlert(getResources().getString(R.string.add_user_bluetooth_no_device));
                    }
                }
            }
        }, 8000);
    }

    void onRefresh(){

        if(!BluetoothMan.isBluetoothEnable()){
            requestBluetooth();
            return;
        }

        startBluetooth();
    }

    @Override
    void onActionButton() {

        ArrayList<String> list = new ArrayList<>();
        for(int i = 0; i < userList.size(); i++){
            boolean check = userList.get(i).isChecked;
            if(check){
                list.add(userList.get(i).itemId);
            }
        }

        String companyId = mUserDataMan.getSelectedId(WebServiceType.COMPANY_FOR_SUPERVISOR);

        if(!NetworkDetect.isNetworkConnected()){
            showAlert(getResources().getString(R.string.network_down_alert_1));
            return;
        }

        if(userList.size()==0){
            return;
        }

        if (companyId.equals(UserDataMan.INVALID_ID)){
            showAlert(getResources().getString(R.string.company_invalid_alert));
        }
        else if (list.size()==0) {
            showAlert(getResources().getString(R.string.not_user_invalid_alert));
        }
        else {
            boolean isSupervisor = mSupervisorToggle.isChecked();
            MessageAddUser msg = new MessageAddUser(WebServiceLogin.getUserId());
            msg.setEmailList(list);
            msg.setCompanyId(companyId);
            msg.setIsSupervisor(isSupervisor);

            showProgressDialog(progressDialog,getResources().getString(R.string.send_add_user_progress),false);
            webServiceData.asyncSendMsg(msg, this, null);
        }
    }

    @Override
    public void onPostExecute(AsyncTask task, ArrayList<Object> resultArray) {

        super.onPostExecute(task,resultArray);

        for (int i = 0; i < resultArray.size(); i++) {
            WebServiceResult result = (WebServiceResult)resultArray.get(i);
            final WebServiceType type = result.getMessageType();

            if(type == WebServiceType.ADD_USER) {
                ResultAddUser ws = (ResultAddUser)result;
                if(!ws.isConnectSucceed()) {
                    stopProgressDialog(progressDialog,getResources().getString(R.string.connect_fail),1000,null);
                }else {
                    String s = "";
                    if(ws.getReturnCode()==ResultAddUser.ADD_USER_GOOD) {
                        s = getResources().getString(R.string.add_user_good);
                    }
                    else if(ws.getReturnCode()==ResultAddUser.ADD_USER_ERR_EXIST_EMAIL) {
                        //s = getResources().getString(R.string.add_user_existing_email);
                        s = getResources().getString(R.string.add_user_good);
                    }
                    else if(ws.getReturnCode()==ResultAddUser.ADD_USER_ERR_INVALID_EMAIL){
                        //s = getResources().getString(R.string.add_user_invalid_email);
                        s = getResources().getString(R.string.add_user_good);
                    }
                    else {
                        s = getResources().getString(R.string.add_user_bad);
                    }
                    stopProgressDialog(progressDialog, s, 1000, null);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }
}
