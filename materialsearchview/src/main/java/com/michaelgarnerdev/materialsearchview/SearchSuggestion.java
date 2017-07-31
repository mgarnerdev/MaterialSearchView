package com.michaelgarnerdev.materialsearchview;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * Created by mgarner on 7/29/2017.
 */

public class SearchSuggestion implements Parcelable {

    @NonNull
    private String mSearchTerm = "";
    @NonNull
    private String mDate = "";

    public SearchSuggestion() {

    }

    public SearchSuggestion(@NonNull String searchTerm,
                            @NonNull String date) {
        this.mSearchTerm = searchTerm;
        this.mDate = date;
    }

    protected SearchSuggestion(Parcel in) {
        mSearchTerm = in.readString();
        mDate = in.readString();
    }

    public static final Creator<SearchSuggestion> CREATOR = new Creator<SearchSuggestion>() {
        @Override
        public SearchSuggestion createFromParcel(Parcel in) {
            return new SearchSuggestion(in);
        }

        @Override
        public SearchSuggestion[] newArray(int size) {
            return new SearchSuggestion[size];
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
