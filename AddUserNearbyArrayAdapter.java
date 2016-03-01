package com.collectiveintelligence.pandora;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.collectiveintelligence.pandora.BaseFunction.BluetoothMan;
import com.collectiveintelligence.pandora.BaseFunction.BluetoothStatus;
import com.collectiveintelligence.pandora.UICommon.BluetoothDataItem;
import com.collectiveintelligence.pandora.UICommon.ToggleImageButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by steveyang on 7/14/15.
 */

public class AddUserNearbyArrayAdapter extends BaseAdapter {
    List<BluetoothDataItem> list;

    AddUserBluetoothActivity activity = null;

    public void setActivity(AddUserBluetoothActivity d) {
        this.activity = d;
    }

    public AddUserNearbyArrayAdapter(List<BluetoothDataItem> pList) {
        if(pList == null){
            this.list = new ArrayList<>();
            return;
        }
        this.list = pList;
    }

    public List<BluetoothDataItem> getList() {
        return list;
    }

    public void setList(List<BluetoothDataItem> d){
        list = d;
    }

    @Override
    public int getCount() {
        if(list!=null){
            return list.size();
        }
        return 0;
    }

    @Override
    public Object getItem(int position) {
        if(list!=null && position<list.size()){
            return list.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater _LayoutInflater=LayoutInflater.from(PandoraApplication.getAppContex());

        final BluetoothDataItem item = list.get(position);

        if(item.bluetoothStatus == BluetoothStatus.FINISH){
            convertView=_LayoutInflater.inflate(R.layout.add_user_list_item, null);
            TextView textView=(TextView)convertView.findViewById(R.id.text);
            TextView textView1=(TextView)convertView.findViewById(R.id.text1);
            ToggleImageButton toggleImageButton = (ToggleImageButton)convertView.findViewById(R.id.toggle);

            toggleImageButton.setOnCheckedChangeListener(new ToggleImageButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(ToggleImageButton buttonView, boolean isChecked) {
                    if (list != null) {
                        item.isChecked = isChecked;
                    }
                }
            });

            if(list != null){
                String name = item.itemName;
                String email = item.itemId;
                textView.setText(name);
                textView1.setText(" ("+email+")");
                boolean sel = item.isChecked;
                toggleImageButton.setChecked(sel);
            }
        }
        else{
            convertView=_LayoutInflater.inflate(R.layout.add_user_device_list_item, null);
            TextView textView=(TextView)convertView.findViewById(R.id.name);
            TextView statusView =(TextView)convertView.findViewById(R.id.text);

            if(list != null){
                String name = item.itemName;
                textView.setText(name);
                statusView.setText(BluetoothMan.getStringByStatus(item.bluetoothStatus));
            }
        }
        return convertView;
    }
}