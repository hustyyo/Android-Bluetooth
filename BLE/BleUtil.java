package au.com.collectiveintelligence.fleetiq360.WebService.BLE;

/* Copyright (C) 2017 Relish Technologies Ltd. - All Rights Reserved
 * You may use, distribute and modify this code under the
 * terms of the MIT license.
 *
 * You should have received a copy of the MIT license with
 * this file. If not, please visit https://opensource.org/licenses/MIT
 */

import android.icu.util.TimeZone;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_SINT16;
import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_SINT32;
import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_SINT8;
import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT16;
import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT32;
import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8;

public class BleUtil {

    public BleUtil() {}

    static byte UPDATE_REASON_UNKNOWN = 0;
    static byte UPDATE_REASON_MANUAL = 1;
    static byte UPDATE_REASON_EXTERNAL_REF = (1 << 1);
    static byte UPDATE_REASON_TIME_ZONE_CHANGE = (1 << 2);
    static byte UPDATE_REASON_DAYLIGHT_SAVING = (1 << 3);

    public static String getLocalTimeStringFromGmtUnixTime(long time){

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        sdf.setTimeZone(java.util.TimeZone.getDefault());
        Date date = new java.util.Date(time);
        String s = sdf.format(date);

        return s;
    }

    public static class BleTimeItem {
        int year;
        int month;
        int day;
        int hour;
        int minute;
        int second;
        int dayOfWeek;

    }
    public static BleTimeItem parseBleTime(byte[] data){

        //        bleTimeRaw = struct.pack('<HBBBBBBBB', nowTime.tm_year, nowTime.tm_mon, nowTime.tm_mday,
//                nowTime.tm_hour, nowTime.tm_min, nowTime.tm_sec, nowTime.tm_wday, 0, 0)
        BleTimeItem bleTimeItem = new BleTimeItem();
        if(data.length < 10){
            return bleTimeItem;
        }

        bleTimeItem.year = getIntValue(data,FORMAT_UINT16,0);
        bleTimeItem.month =  getIntValue(data,FORMAT_UINT8,2);
        bleTimeItem.day =  getIntValue(data,FORMAT_UINT8,3);
        bleTimeItem.hour =  getIntValue(data,FORMAT_UINT8,4);
        bleTimeItem.minute =  getIntValue(data,FORMAT_UINT8,5);
        bleTimeItem.second =  getIntValue(data,FORMAT_UINT8,6);
        bleTimeItem.dayOfWeek =  getIntValue(data,FORMAT_UINT8,7);

        return bleTimeItem;
    }

    public static boolean isTimeSetSucceed(BleTimeItem bleTimeItem, BleTimeItem expectItem) {

        if(bleTimeItem.year <= 0 || bleTimeItem.month <=0 || bleTimeItem.day<=0){
            return false;
        }

        if(bleTimeItem.year < expectItem.year){
            return false;
        }
        if(bleTimeItem.month < expectItem.month){
            return false;
        }
        if(bleTimeItem.day < expectItem.day){
            return false;
        }
        if(bleTimeItem.hour < expectItem.hour){
            return false;
        }
        if(bleTimeItem.minute < expectItem.minute){
            return false;
        }
        if(bleTimeItem.second < expectItem.second){
            return false;
        }

        return true;
    }

    public static byte[] getTimeData() {

//        bleTimeRaw = struct.pack('<HBBBBBBBB', nowTime.tm_year, nowTime.tm_mon, nowTime.tm_mday,
//                nowTime.tm_hour, nowTime.tm_min, nowTime.tm_sec, nowTime.tm_wday, 0, 0)

        // See https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.current_time.xml
        Calendar time = Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT"));
        // Calendar: January = 0
        // CTS: January = 1
        int month = time.get(Calendar.MONTH) + 1;
        // Calendar: Monday = 2, Sunday = 1
        // CTS: Monday = 1, Sunday = 7
        int dayOfWeek = time.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.SUNDAY) {
            dayOfWeek = 7;
        } else {
            dayOfWeek = dayOfWeek - 1;
        }
        int year = time.get(Calendar.YEAR);
        byte[] da = ByteBuffer.allocate(10)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) year)
                .put((byte) month)
                .put((byte) time.get(Calendar.DAY_OF_MONTH))
                .put((byte) time.get(Calendar.HOUR_OF_DAY))
                .put((byte) time.get(Calendar.MINUTE))
                .put((byte) time.get(Calendar.SECOND))
                .put((byte) dayOfWeek)
                .put((byte) 0)
                .put((byte)0)
                .array();

        //BleTimeItem bleTimeItem = parseBleTime(da);
        return da;
    }

    public static  boolean isTheSameByteArray(byte[] a1, byte[] a2){
        if(null == a1 || null == a2){
            return false;
        }
        return 0==ByteBuffer.wrap(a1).compareTo(ByteBuffer.wrap(a2));
    }

    static byte[] timezoneWithDstOffset(Calendar time) {
        // See https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.local_time_information.xml

        // UTC-1:00 = -4
        // UTC+0:00 = 0
        // UTC+1:00 = 4
        int timezone = time.get(Calendar.ZONE_OFFSET) / 1000 / 60 / 15;
        int dstOffset = time.get(Calendar.DST_OFFSET) / 1000 / 60 / 15;

        return ByteBuffer.allocate(2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) timezone)
                .put((byte) dstOffset)
                .array();
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHexStr(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Convert a signed byte to an unsigned int.
     */
    static public int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }

    /**
     * Convert signed bytes to a 16-bit unsigned int.
     */
    static public int unsignedBytesToInt(byte b0, byte b1) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8));
    }

    /**
     * Convert signed bytes to a 32-bit unsigned int.
     */
    static public int unsignedBytesToInt(byte b0, byte b1, byte b2, byte b3) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8))
                + (unsignedByteToInt(b2) << 16) + (unsignedByteToInt(b3) << 24);
    }

    /**
     * Convert signed bytes to a 16-bit short float value.
     */
    static public float bytesToFloat(byte b0, byte b1) {
        int mantissa = unsignedToSigned(unsignedByteToInt(b0)
                + ((unsignedByteToInt(b1) & 0x0F) << 8), 12);
        int exponent = unsignedToSigned(unsignedByteToInt(b1) >> 4, 4);
        return (float)(mantissa * Math.pow(10, exponent));
    }

    /**
     * Convert signed bytes to a 32-bit short float value.
     */
    static public float bytesToFloat(byte b0, byte b1, byte b2, byte b3) {
        int mantissa = unsignedToSigned(unsignedByteToInt(b0)
                + (unsignedByteToInt(b1) << 8)
                + (unsignedByteToInt(b2) << 16), 24);
        return (float)(mantissa * Math.pow(10, b3));
    }

    /**
     * Convert an unsigned integer value to a two's-complement encoded
     * signed value.
     */
    static public int unsignedToSigned(int unsigned, int size) {
        if ((unsigned & (1 << size-1)) != 0) {
            unsigned = -1 * ((1 << size-1) - (unsigned & ((1 << size-1) - 1)));
        }
        return unsigned;
    }

    /**
     * Convert an integer into the signed bits of a given length.
     */
    static public int intToSignedBits(int i, int size) {
        if (i < 0) {
            i = (1 << size-1) + (i & ((1 << size-1) - 1));
        }
        return i;
    }

    /**
     * Returns the size of a give value type.
     */
    static private int getTypeLen(int formatType) {
        return formatType & 0xF;
    }

    static public Integer getIntValue(byte[] data, int formatType) {
        return getIntValue(data,formatType,0);
    }
    static public Integer getIntValue(byte[] data, int formatType, int offset) {

        if ((offset + getTypeLen(formatType)) > data.length) return null;

        switch (formatType) {
            case FORMAT_UINT8:
                return unsignedByteToInt(data[offset]);

            case FORMAT_UINT16:
                return unsignedBytesToInt(data[offset], data[offset+1]);

            case FORMAT_UINT32:
                return unsignedBytesToInt(data[offset],   data[offset+1],
                        data[offset+2], data[offset+3]);

            case FORMAT_SINT8:
                return unsignedToSigned(unsignedByteToInt(data[offset]), 8);

            case FORMAT_SINT16:
                return unsignedToSigned(unsignedBytesToInt(data[offset],
                        data[offset+1]), 16);

            case FORMAT_SINT32:
                return unsignedToSigned(unsignedBytesToInt(data[offset],
                        data[offset+1], data[offset+2], data[offset+3]), 32);
        }

        return null;
    }
}
