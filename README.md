[ ![Download](https://api.bintray.com/packages/michaelgarnerdev/materialsearchview/materialsearchview/images/download.svg) ](https://bintray.com/michaelgarnerdev/materialsearchview/materialsearchview/_latestVersion)
# MaterialSearchView
MaterialSearchView - A search view that you'll love.

### What is MaterialSearchView?
MaterialSearchView is a free and open-source Android library that allows developers to quickly and easily add a Material Design compatible SearchView that emulates Google Search in many ways. MaterialSearchView incorporates an SQLite Database to save recent searches and allow for auto-populated search suggestions based upon what the user is typing at the time. In addition to those cool features, it also sports a voice search feature with built-in permission callbacks to enable the microphone.

The minimum API level supported by this library is API 16.

## Table of Contents
+ [Examples](#examples)
+ [Add MaterialSearchView to Your Project](#add-materialsearchview-to-your-project)
  - [Get MaterialSearchView](#get-materialsearchview)
  - [Use MaterialSearchView](#use-materialsearchview)
    * [Add MaterialSearchView to Your Layout](#add-materialsearchview-to-your-layout)
    * [Add and Remove Listeners Appropriately](#add-and-remove-listeners-appropriately)
      + [Add Listeners](#add-listeners)
      + [Remove Listeners](#remove-listeners)
      + [Handle Backpress If Desired](#handle-backpress-if-desired)
      + [Handle Search Results](#handle-search-results)
      + [Handle Voice Searching](#handle-voice-searching)
    * [Make the MaterialSearchView Your Own](#make-the-materialsearchview-your-own)
      + [XML Attributes:](#xml-attributes)
      + [Java API:](#java-api)
+ [Thanks for Using MaterialSearchView](#thanks-for-using-materialsearchview)
+ [License](#license)

### Examples

![Demo GIF](https://www.github.com/mgarnerdev/MaterialSearchView/art/material-search-view-demo.gif)
![Screenshots](https://www.github.com/mgarnerdev/MaterialSearchView/art/screenshots.jpg)


### Add MaterialSearchView to Your Project

#### Get MaterialSearchView
MaterialSearchView is available via jCenter. jCenter is the default Maven repository used by Android Studio.
Simply add the following maven url to your project-level `build.gradle`
```
repositories {
    maven {
        url  "https://dl.bintray.com/michaelgarnerdev/materialsearchview"
    }
}
```

And then add the following to your app-level `build.gradle`
```
compile 'com.michaelgarnerdev.materialsearchview:materialsearchview:0.1.0'
```

You will also need the following support libraries:
```
compile "com.android.support:appcompat-v7:26.0.2"
compile "com.android.support:cardview-v7:26.0.2"
compile "com.android.support:recyclerview-v7:26.0.2"
```

You can find more information about how to add the support libraries to your project here:
https://developer.android.com/topic/libraries/support-library/setup.html

You can find the current version of the support libraries here:
https://developer.android.com/topic/libraries/support-library/revisions.html

#### Use MaterialSearchView

##### Add MaterialSearchView to Your Layout

```
<com.michaelgarnerdev.materialsearchview.MaterialSearchView
        android:id="@+id/material_search_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"/>
```

##### Add and Remove Listeners Appropriately

###### Add Listeners
```
    @Override
    protected void onStart() {
        super.onStart();
        mMaterialSearchView.addListener((SearchViewSearchListener) this);
        mMaterialSearchView.addListener((SearchViewVoiceListener) this);
    }
```

###### Remove Listeners
In version 24 (N or Nougat), multi-window support was added to the Android Framework. `onStop()` was accordingly guaranteed to be called and `onStop()` should be responsible for take down code to prevent strange behavior if the user is 'switching' between two apps in multi-window.
```
    @Override
    protected void onStop() {
        super.onStop();
        if (mMaterialSearchView != null && VERSION.SDK_INT >= VERSION_CODES.N) {
            mMaterialSearchView.removeListener((SearchViewSearchListener) this);
            mMaterialSearchView.removeListener((SearchViewVoiceListener) this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMaterialSearchView != null && VERSION.SDK_INT < VERSION_CODES.N) {
            mMaterialSearchView.removeListener((SearchViewSearchListener) this);
            mMaterialSearchView.removeListener((SearchViewVoiceListener) this);
        }
    }
```

###### Handle Backpress If Desired
```
    @Override
    public void onBackPressed() {
        if (!mMaterialSearchView.onBackPressed()) {
            super.onBackPressed();
        }
    }
```

###### Handle Search Results
```
    @Override
    public void onSearch(@NonNull String searchTerm) {
        Log.d(TAG, "SEARCH TERM: " + searchTerm);
        Snackbar.make(mRootLayout, searchTerm, Snackbar.LENGTH_SHORT).show();
    }
```

###### Handle Voice Searching

To allow voice searching on version 23 (M or Marshmallow) or higher, you'll need to handle runtime permissions for the microphone permission. The MaterialSearchView makes this easier for you:
```
    @Override
    public void onVoiceSearchError(int error) {
        Log.d(TAG, "VOICE_SEARCH_ERROR: " + error);
        switch (error) {
            case VOICE_SEARCH_ERROR_PERMISSION_NEEDED:
                //Check if you can call requestPermissions(). This error is only returned on 23+.
                if (VERSION.SDK_INT >= VERSION_CODES.M) {
                //Request the record audio permission.
                    requestPermissions(new String[]{permission.RECORD_AUDIO},
                            MaterialSearchView.REQUEST_CODE_PERMISSION_RECORD_AUDIO);
                }
                break;
                ...
```

The MaterialSearchView is also able to handle the permission results for you:

```
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mMaterialSearchView.handlePermissionResult(requestCode, permissions, grantResults);
    }
```

Lastly, you can now receive search results or errors in the appropriate callbacks from the `SearchViewVoiceListener`

```
    @Override
    public void onVoiceSearch(@NonNull String searchTerm) {
        //Do something with the searchTerm
    }

    @Override
    public void onVoiceSearchError(int error) {
        //Handle or log the error. The below is provided as an example.
        switch (error) {
            case VOICE_SEARCH_ERROR_PERMISSION_NEEDED:
                if (VERSION.SDK_INT >= VERSION_CODES.M) {
                    requestPermissions(new String[]{permission.RECORD_AUDIO},
                            MaterialSearchView.REQUEST_CODE_PERMISSION_RECORD_AUDIO);
                }
                break;
            case VOICE_SEARCH_ERROR_MISSING_CONTEXT:
                Log.d(TAG, "VOICE_SEARCH_ERROR: Missing Context");
                break;
            case VOICE_SEARCH_ERROR_UNAVAILABLE:
                AlertDialog.Builder builder = new Builder(this);
                builder.setTitle(R.string.voice_search_unavailable_title);
                builder.setMessage(R.string.voice_search_unavailable_message);
                builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                });
                builder.show();
                break;
        }
    }
```

##### Make the MaterialSearchView Your Own

There are a number of methods provided in the API for the MaterialSearchView that let you control everything from whether voice searching should be allowed to what color the hint text should be.
###### XML Attributes

```
<com.michaelgarnerdev.materialsearchview.MaterialSearchView
        android:id="@+id/material_search_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:databaseSuggestionsEnabled="true"
        app:maxSuggestionsShown="5"/>

```
Here are all of the options available in XML:
```
<declare-styleable name="MaterialSearchView">
    <attr name="databaseSuggestionsEnabled" format="boolean"/>
    <attr name="maxSuggestionsPersisted" format="reference|integer"/>
    <attr name="maxSuggestionsShown" format="reference|integer"/>
    <attr name="voiceFeaturesEnabled" format="boolean"/>
    <attr name="hintText" format="reference"/>
    <attr name="text" format="reference|string"/>
    <attr name="hintTextColor" format="reference"/>
    <attr name="textColor" format="reference"/>
    <attr name="mainIcon" format="reference"/>
    <attr name="voiceIcon" format="reference"/>
    <attr name="clearIcon" format="reference"/>
    <attr name="background" format="reference"/>
    <attr name="backgroundColor" format="reference"/>
    <attr name="iconTintColor" format="reference"/>
</declare-styleable>
```

###### Java API

```
///////////////////Begin API///////////////////

    getCardView() - Returns the parent view essentially.

    getSearchInputEditText() - Returns the actual EditText the user is typing into.

    getVoiceImageButton() - Returns the voice image button that initiates voice searching.

    getClearButton() - Returns the clear image button that clears the text from the search field.

    getMainIcon() - Returns the image view for the main icon on the left of the view.

    setDatabaseAndSuggestionsEnabled(boolean enabled) - Set whether the database and suggestions are enabled. Default is enabled.

    setMaximumSuggestions() - Set the maximum number of suggestions that can be persisted to the database. Default is 1000.

    setMaxSuggestionsShown() - Set the maximum number of suggestions shown to the user while searching. Default is 5.

    getSearchTerm() - Get the currently entered search term.

    setSearchTerm() - Set the search term for the view.

    setHintText() - Set the hint text for the view.

    getHintTextString() - Get the hint text for the view.

    getHintText() - Get the hint text as a CharSequence.

    setHintTextColor() - Sets the hint text color.

    clearSearchHistory() - Deletes the database and thus clears search history. You should limit the number of calls to this methods for performance reasons.

    setVoiceEnabled() - Sets whether voice searching is enabled or not. Default is enabled.

    setMainIcon() - Sets the main icon for the view.

    hideMainIcon() - Hides the main icon for the view.

    setVoiceIcon() - Sets the voice icon for the view.

    hideVoiceIcon() - Hides the voice icon for the view.

    setClearIcon() - Sets the clear icon for the view.

    showClearIcon() - Shows the clear icon for the view.

    hideClearIcon() - Hides the clear icon for the view.

    setBackgroundColor() - Sets the background color for the view.

    setTextColor() - Sets the text color for the view.

    setIconTint() - Sets the icon tint for the view. All icons will be set with this method.

    persistSuggestions() - Persists your own custom array of suggestions.

    setSuggestions() - Sets the suggestions for the view without persisting them.

    getAllSuggestedSearches() - Gets all persisted suggested searches.

    addListener() - Add one of the provided listeners.

    removeListener() - Remove one of the provided listeners.

    clearSearchListeners() - Clear search listeners. (Only search listeners)

    clearInteractionListeners() - Clear interaction listeners. (Only interaction listeners)

    clearEventListeners() - Clear event listeners. (Only event listeners)

    clearVoiceListeners() - Clear voice listeners. (Only voice listeners)

    clearAllListeners() - Clear all listeners of all types.

    onBackPressed() - Check to see if the search view can handle the backpress. True if view handled the backpress.

    close() - Closes the search view if open.

    isOpen() - Determines if the search view is open.

    ///////////////////End API///////////////////
```

There are even more options available as well and the APIs can be found in the MaterialSearchView class.
You can view that here:
https://github.com/mgarnerdev/MaterialSearchView/blob/development/materialsearchview/src/main/java/com/michaelgarnerdev/materialsearchview/MaterialSearchView.java

### Thanks for using MaterialSearchView
Please feel free to submit pull requests or open issues that you may find.

### License
This library is open-source and licenses with the Apache 2.0 license.

```
Copyright 2017 Michael Garner (mgarnerdev)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```