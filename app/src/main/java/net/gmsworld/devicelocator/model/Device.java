package net.gmsworld.devicelocator.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Device implements Parcelable{

    private static final String TAG = Device.class.getName();

    private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); // ISO8601

    public String imei;
    public String name;
    public String creationDate;
    public String geo;

    private volatile Date date;

    public Device() {

    }

    private Device(Parcel in) {
        imei = in.readString();
        name = in.readString();
        creationDate = in.readString();
        geo = in.readString();
    }

    public static final Creator<Device> CREATOR = new Creator<Device>() {
        @Override
        public Device createFromParcel(Parcel in) {
            return new Device(in);
        }

        @Override
        public Device[] newArray(int size) {
            return new Device[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(imei);
        parcel.writeString(name);
        parcel.writeString(creationDate);
        parcel.writeString(geo);
    }

    public String toString() {
        return imei + "," + creationDate + "," + (StringUtils.isNotEmpty(name) ? name : "") + "," + (StringUtils.isNotEmpty(geo) ? geo : "");
    }

    public static Device fromString(String device) {
        //imei,creation,name,geo
        String[] tokens = StringUtils.split(device,',');
        Device d = null;
        if (tokens != null && tokens.length >= 2) {
            d = new Device();
            d.imei = tokens[0];
            d.creationDate = tokens[1];
            if (tokens.length > 2 && StringUtils.isNotEmpty(tokens[2])) {
                d.name = tokens[2];
            }
            if (tokens.length > 3 && StringUtils.isNotEmpty(tokens[3])) {
                d.geo = tokens[3];
            }
        }
        return d;
    }

    //formatter is not thread safe!
    public synchronized Date getDate() {
        if (date == null) {
            try {
                date = formatter.parse(creationDate);
            } catch (ParseException e) {
                Log.d(TAG, e.getMessage(), e);
            }
        }
        return date;
    }
}
