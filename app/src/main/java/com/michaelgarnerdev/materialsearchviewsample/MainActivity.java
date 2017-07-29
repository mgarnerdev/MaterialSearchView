package com.michaelgarnerdev.materialsearchviewsample;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;

import com.michaelgarnerdev.materialsearchview.MaterialSearchView;
import com.michaelgarnerdev.materialsearchview.MaterialSearchView.SearchViewListener;

public class MainActivity extends AppCompatActivity implements SearchViewListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private LinearLayout mRootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRootLayout = findViewById(R.id.root_view);
        MaterialSearchView materialSearchView = mRootLayout.findViewById(R.id.material_search_view);
        materialSearchView.addListener(this);
    }

    @Override
    public void onSearch(@NonNull String searchTerm) {
        Log.d(TAG, "SEARCH TERM: " + searchTerm);
        Snackbar.make(mRootLayout, searchTerm, Snackbar.LENGTH_SHORT).show();
    }
}
