package net.gmsworld.devicelocator.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.apache.commons.lang3.StringUtils;

public class Device implements Parcelable{
    public String imei;
    public String name;
    public String creationDate;

    public Device() {

    }

    private Device(Parcel in) {
        imei = in.readString();
        name = in.readString();
        creationDate = in.readString();
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
    }

    public String toString() {
        return imei + "," + creationDate + "," + name;
    }

    public static Device fromString(String device) {
        //imei,creation,name
        String[] tokens = StringUtils.split(device,',');
        Device d = null;
        if (tokens != null && tokens.length >= 2) {
            d = new Device();
            d.imei = tokens[0];
            d.creationDate = tokens[1];
            if (tokens.length >= 3) {
                d.name = tokens[2];
            }
        }
        return d;
    }
}
