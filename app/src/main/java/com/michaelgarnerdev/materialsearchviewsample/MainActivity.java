package com.michaelgarnerdev.materialsearchviewsample;

import android.Manifest.permission;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;

import com.michaelgarnerdev.materialsearchview.MaterialSearchView;
import com.michaelgarnerdev.materialsearchview.MaterialSearchView.SearchViewSearchListener;
import com.michaelgarnerdev.materialsearchview.MaterialSearchView.SearchViewVoiceListener;

public class MainActivity extends AppCompatActivity implements
        SearchViewSearchListener,
        SearchViewVoiceListener {

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
        mMaterialSearchView.addListener((SearchViewSearchListener) this);
        mMaterialSearchView.addListener((SearchViewVoiceListener) this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mMaterialSearchView != null) {
            mMaterialSearchView.handlePermissionResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mMaterialSearchView != null && VERSION.SDK_INT >= VERSION_CODES.M) {
            mMaterialSearchView.removeListener((SearchViewSearchListener) this);
            mMaterialSearchView.removeListener((SearchViewVoiceListener) this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMaterialSearchView != null && VERSION.SDK_INT < VERSION_CODES.M) {
            mMaterialSearchView.removeListener((SearchViewSearchListener) this);
            mMaterialSearchView.removeListener((SearchViewVoiceListener) this);
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

    @Override
    public void onVoiceSearchError(int error) {
        Log.d(TAG, "VOICE_SEARCH_ERROR: " + error);
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

    @Override
    public void onVoiceSearch(@NonNull String searchTerm) {
        Log.d(TAG, "VOICE SEARCH TERM: " + searchTerm);
        Snackbar.make(mRootLayout, searchTerm, Snackbar.LENGTH_SHORT).show();
    }
}
