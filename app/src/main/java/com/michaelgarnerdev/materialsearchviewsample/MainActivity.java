package com.michaelgarnerdev.materialsearchviewsample;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;

import com.michaelgarnerdev.materialsearchview.MaterialSearchView;
import com.michaelgarnerdev.materialsearchview.MaterialSearchView.SearchViewSearchListener;

public class MainActivity extends AppCompatActivity implements SearchViewSearchListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private LinearLayout mRootLayout;
    private MaterialSearchView mMaterialSearchView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRootLayout = findViewById(R.id.root_view);
        mMaterialSearchView = mRootLayout.findViewById(R.id.material_search_view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mMaterialSearchView.addListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mMaterialSearchView != null && VERSION.SDK_INT >= VERSION_CODES.M) {
            mMaterialSearchView.removeListener(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMaterialSearchView != null && VERSION.SDK_INT < VERSION_CODES.M) {
            mMaterialSearchView.removeListener(this);
        }
    }

    @Override
    public void onBackPressed() {
        if (mMaterialSearchView != null) {
            if (!mMaterialSearchView.onBackPressed()) {
                super.onBackPressed();
            }
        }
    }

    @Override
    public void onSearch(@NonNull String searchTerm) {
        Log.d(TAG, "SEARCH TERM: " + searchTerm);
        Snackbar.make(mRootLayout, searchTerm, Snackbar.LENGTH_SHORT).show();
    }
}
