package au.com.collectiveintelligence.fleetiq360.WebService.BLE;

import au.com.collectiveintelligence.fleetiq360.WebService.webserviceclasses.SaveShockEventItem;
import au.com.collectiveintelligence.fleetiq360.WebService.webserviceclasses.parameters.SaveShockEventParameter;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmQuery;
import io.realm.RealmResults;

/**
 * Created by steveyang on 12/6/17.
 */

public class ShockEventsDb extends RealmObject {

    public String mac_address;
    public String time;
    public long magnitude;

    public static RealmResults<ShockEventsDb> readData(Realm realm){
        RealmQuery<ShockEventsDb> query = realm.where(ShockEventsDb.class);
        RealmResults<ShockEventsDb> dataList = query.findAll();
        return dataList;
    }

    public static void removeData(Realm realm, SaveShockEventParameter parameter){

        for(SaveShockEventItem saveShockEventItem : parameter.impactList){
            RealmQuery<ShockEventsDb> query = Realm.getDefaultInstance().where(ShockEventsDb.class);
            query.equalTo("time",saveShockEventItem.impact_time).equalTo("mac_address",saveShockEventItem.mac_address);
            final RealmResults<ShockEventsDb> existList = query.findAll();
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    if(existList != null){
                        existList.deleteAllFromRealm();
                    }
                }
            });
        }
    }

    public static boolean alreadySaved(final ShockEventsItem result){

        if(null == result){
            return false;
        }
        RealmQuery<ShockEventsDb> query = Realm.getDefaultInstance().where(ShockEventsDb.class);
        query.equalTo("time",result.time).equalTo("mac_address",result.mac_address);
        final RealmResults<ShockEventsDb> existList = query.findAll();
        if(existList.size() > 0){
            return true;
        }
        return false;
    }

    public static void saveData(final ShockEventsItem result){

        if(null == result){
            return;
        }

        RealmQuery<ShockEventsDb> query = Realm.getDefaultInstance().where(ShockEventsDb.class);
        query.equalTo("time",result.time).equalTo("mac_address",result.mac_address);
        final RealmResults<ShockEventsDb> existList = query.findAll();
        Realm.getDefaultInstance().executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                if(existList != null){
                    existList.deleteAllFromRealm();
                }

                ShockEventsDb dbObject = realm.createObject(ShockEventsDb.class);
                dbObject.mac_address = result.mac_address;
                dbObject.time = result.time;
                dbObject.magnitude = result.magnitude;
            }
        });
    }
}


