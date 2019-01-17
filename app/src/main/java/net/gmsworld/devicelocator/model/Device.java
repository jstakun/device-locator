package net.gmsworld.devicelocator.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.apache.commons.lang3.StringUtils;

public class Device implements Parcelable{
    public String imei;
    public String name;
    public String creationDate;
    public String geo;

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
}
