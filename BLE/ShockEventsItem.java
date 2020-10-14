package au.com.collectiveintelligence.fleetiq360.WebService.BLE;

import au.com.collectiveintelligence.fleetiq360.WebService.WebData;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmQuery;
import io.realm.RealmResults;

/**
 * Created by steveyang on 12/6/17.
 */

public class ShockEventsItem {

    public String mac_address;
    public String time;
    public long unixTime;
    public long magnitude;

}


