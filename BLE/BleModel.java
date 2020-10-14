package au.com.collectiveintelligence.fleetiq360.WebService.BLE;

import java.util.HashMap;

import au.com.collectiveintelligence.fleetiq360.util.CommonFunc;

/**
 * Created by steveyang on 2/7/17.
 */

public class BleModel {

    public final static String UUID_BLE_TIME = "00002a2b-0000-1000-8000-00805f9b34fb";
    public final static String UUID_BLE_DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb";
    public final static String UUID_BLE_MANUFACTURE = "00002a29-0000-1000-8000-00805f9b34fb";
    public final static String UUID_BLE_MODEL = "00002a24-0000-1000-8000-00805f9b34fb";
    public final static String UUID_SHOCK_COUNT = "69dfccac-59b7-ef3e-5309-db222d1c2d09";
    public final static String UUID_SHOCK_EVENT_ITEM = "53535322-e7fc-8229-b470-89d9fda99e46";
    public final static String UUID_POP_SHOCK_EVENT = "1642fcc6-bcc9-63f5-039a-721e9acbfb93";
    public final static String UUID_SHOCK_THRESHOLD = "6d3f3a08-06dd-4c11-6add-00c0c2443c77";
    public final static String UUID_RELAY_0 = "3cfd3551-3e85-7cfa-7425-9b54c464c6b1";
    public final static String UUID_RELAY_1 = "1ffbb954-2443-c7c4-f98d-78e9abdd54de";
    public final static String UUID_TOKEN_AUTH = "6d1e95a3-12bf-1107-705e-16edf5954aba";
    public final static String TOKEN_AUTH = "uS8MgpklMx";

    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";


    final static String basePath = "au.com.collectiveintelligence.characteristic.";
    final static String PATH_VERSION_MAIN = "system.version_main";
    final static String PATH_VERSION_RADIO = "system.version_radio";
    final static String PATH_SHOCK_EVENT_THRESHOLD = "shock_detection.shock_magnitude_threshold";
    final static String PATH_SHOCK_EVENT_POP = "shock_detection.pop_shock_event";
    final static String PATH_SHOCK_EVENT = "shock_detection.shock_event";
    final static String PATH_SHOCK_EVENT_COUNT = "shock_detection.shock_event_count";
    HashMap<String,String> mapUuidForPath = new HashMap<>();

    private static BleModel ourInstance;
    public static BleModel instance()
    {
        if (ourInstance == null){

            ourInstance = new BleModel();
        }

        return ourInstance;
    }

    public BleModel() {
        initUuids();
    }

    public String getPrimaryRelayId(){
        return UUID_RELAY_0;
    }

    public String getRelayId(int index){
        if(index == 0){
            return UUID_RELAY_0;
        }
        else if(index == 1){
            return UUID_RELAY_1;
        }
        return "";
    }

    private void initUuids() {

        getUUIDFromPath(PATH_SHOCK_EVENT_THRESHOLD);
        getUUIDFromPath(PATH_SHOCK_EVENT_POP);
        getUUIDFromPath(PATH_SHOCK_EVENT);
        getUUIDFromPath(PATH_SHOCK_EVENT_COUNT);
        getUUIDFromPath(PATH_VERSION_MAIN);
        getUUIDFromPath(PATH_VERSION_RADIO);

        for(int i = 0; i<2; i++) {
            String s = getPathExternalRelayKey(i);
            getUUIDFromPath(s);
        }
    }

    public String getPathExternalRelayKey(int index) {
        return "external_output.relay_"+index+"_state";
    }

    public String getUUIDFromPath(String path) {

        if(mapUuidForPath.containsKey(path)){
            return mapUuidForPath.get(path);
        }
        else {
            String fullPath = basePath + path;
            String uuid = "";
            String h = CommonFunc.SHA1(fullPath);
            if(null == h || h.length() == 0){
                return "";
            }

            String[] sa = new String[5];
            sa[0] = h.substring(0,9);
            sa[1] = h.substring(9,13);
            sa[2] = h.substring(13,17);
            sa[3] = h.substring(17,21);
            sa[4] = h.substring(21,32);

            for(int i = 0; i < sa.length; i++) {

                if(i == 0) {
                    uuid = sa[i];
                }
                else {
                    uuid = uuid + "-" + sa[i];
                }
            }
            mapUuidForPath.put(path,uuid);
            return uuid;
        }
    }


}
