package com.michaelgarnerdev.materialsearchview;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * Created by mgarner on 7/29/2017.
 */

public class PerformedSearch implements Parcelable {

    @NonNull
    private String mSearchTerm = "";
    @NonNull
    private String mDate = "";

    public PerformedSearch() {

    }

    public PerformedSearch(@NonNull String searchTerm,
                           @NonNull String date) {
        this.mSearchTerm = searchTerm;
        this.mDate = date;
    }

    protected PerformedSearch(Parcel in) {
        mSearchTerm = in.readString();
        mDate = in.readString();
    }

    public static final Creator<PerformedSearch> CREATOR = new Creator<PerformedSearch>() {
        @Override
        public PerformedSearch createFromParcel(Parcel in) {
            return new PerformedSearch(in);
        }

        @Override
        public PerformedSearch[] newArray(int size) {
            return new PerformedSearch[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(mSearchTerm);
        parcel.writeString(mDate);
    }

    @NonNull
    public String getSearchTerm() {
        return mSearchTerm;
    }

    @NonNull
    public String getDate() {
        return mDate;
    }
}
