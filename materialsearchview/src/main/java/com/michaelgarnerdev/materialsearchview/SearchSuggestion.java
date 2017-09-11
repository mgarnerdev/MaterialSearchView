package com.michaelgarnerdev.materialsearchview;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * SearchSuggestion helps encapsulate search data with easy-to-use accessor methods.
 * <p>
 * Copyright 2017 Michael Garner (mgarnerdev)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
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
