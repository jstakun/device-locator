package net.gmsworld.devicelocator.model;

import android.os.Parcel;
import android.os.Parcelable;

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
}
