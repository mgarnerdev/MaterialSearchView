package com.michaelgarnerdev.materialsearchview;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.michaelgarnerdev.materialsearchview.SearchDatabase.DatabaseReadSearchesListener;
import com.michaelgarnerdev.materialsearchview.SearchDatabase.DatabaseTaskListener;
import com.michaelgarnerdev.materialsearchview.SearchDatabase.GetPerformedSearchesStartingWithTask;
import com.michaelgarnerdev.materialsearchview.SearchDatabase.GetPerformedSearchesTask;
import com.michaelgarnerdev.materialsearchview.SearchDatabase.GetRecentSearchesTask;

import java.lang.annotation.Retention;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Created by mgarnerdev on 7/29/2017.
 * MaterialSearchView helps you add material-themed searching to your app with ease.
 */

public class MaterialSearchView extends LinearLayout implements DatabaseReadSearchesListener, RecognitionListener {
    private static final String TAG = MaterialSearchView.class.getSimpleName();

    private static final long TIME_SEARCH_AFTER_KEY_PRESS_DELAY = 300;
    private static final int DEFAULT_SUGGESTIONS_LIMIT = 5;
    private static final int DEFAULT_MAX_SUGGESTIONS = 1000;

    private WeakReference<Context> mContext;

    private CardView mSearchCard;
    private ImageView mSearchIcon;
    private EditText mSearchInputEditText;
    private ImageButton mMicButton;
    private ImageButton mClearButton;

    private Handler mHandler = new Handler();

    private GetPerformedSearchesStartingWithTask mFilterSearchTask;
    private GetRecentSearchesTask mRecentSearchesTask;
    private Runnable mFilterRunnable;
    private RecyclerView mSuggestionsRecyclerView;
    private SuggestionsAdapter mSuggestionsAdapter;
    private ArrayList<SearchViewSearchListener> mSearchListeners = new ArrayList<>();
    private ArrayList<SearchViewInteractionListener> mInteractionListeners = new ArrayList<>();
    private ArrayList<SearchViewVoiceListener> mVoiceListeners = new ArrayList<>();
    private ArrayList<SearchViewEventListener> mEventListeners = new ArrayList<>();
    private float mSuggestionRowHeight = 0;
    private int mCurrentState = STATE_DEFAULT;
    private TextWatcher mSearchTextChangedListener;
    private OnEditorActionListener mOnEditorActionListener;

    private int mSearchSuggestionsLimit = DEFAULT_SUGGESTIONS_LIMIT;
    private boolean mDatabaseSuggestionsEnabled = true;
    private boolean mVoiceFeaturesEnabled = true;
    private boolean mHideMicIcon = false;
    private boolean mHideClearIcon = false;
    private int mSuggestionsResolvedTintColor;
    private int mMaxSuggestions = DEFAULT_MAX_SUGGESTIONS;


    public MaterialSearchView(Context context) {
        this(context, null);
    }

    public MaterialSearchView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaterialSearchView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @RequiresApi(api = VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("unused")
    public MaterialSearchView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(@NonNull Context context) {
        mContext = new WeakReference<>(context);
        Resources resources = mContext.get().getResources();
        mSuggestionRowHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, resources.getDisplayMetrics());
        mSuggestionsResolvedTintColor = ContextCompat.getColor(mContext.get(), R.color.secondaryText);
        Log.d(TAG, "ROW HEIGHT: " + mSuggestionRowHeight);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Log.d(TAG, ".onFinishInflate()");
        inflate(mContext.get(), R.layout.material_search_view, this);
        setupView();
    }

    private void setupView() {
        mSearchCard = findViewById(R.id.msv_search_card);
        mSearchIcon = findViewById(R.id.msv_search_card_input_icon);
        mSearchInputEditText = findViewById(R.id.msv_search_card_input_edit_text);
        mMicButton = findViewById(R.id.msv_search_card_input_microphone);
        mClearButton = findViewById(R.id.msv_search_card_input_cancel);

        mMicButton.setVisibility(mVoiceFeaturesEnabled && !mHideMicIcon ? View.VISIBLE : View.GONE);
        mClearButton.setVisibility(mVoiceFeaturesEnabled ? View.GONE : View.VISIBLE);

        mSuggestionsRecyclerView = findViewById(R.id.msv_search_card_suggestions_recycler_view);
        mSuggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(mContext.get()));
        mSuggestionsAdapter = new SuggestionsAdapter();
        mSuggestionsRecyclerView.setAdapter(mSuggestionsAdapter);

        mClearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mInteractionListeners != null) {
                    for (SearchViewInteractionListener listener : mInteractionListeners) {
                        listener.onClearButtonClick();
                    }
                }
                moveToState(STATE_FOCUSED_EMPTY);
            }
        });

        mMicButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mInteractionListeners != null) {
                    for (SearchViewInteractionListener listener : mInteractionListeners) {
                        listener.onVoiceButtonClick();
                    }
                }
                startVoiceSearch();
            }
        });

        mSearchTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence != null && charSequence.length() > 0) {
                    if (mCurrentState != STATE_FOCUSED_TEXT_PRESENT_DELAY_SEARCH) {
                        moveToState(STATE_FOCUSED_TEXT_PRESENT_DELAY_SEARCH);
                    } else {
                        timedFilterSearchSuggestions(getSearchText());
                    }
                } else {
                    moveToState(STATE_FOCUSED_EMPTY);
                }
            }

            @Override
            public void afterTextChanged(final Editable editable) {

            }
        };

        mSearchInputEditText.addTextChangedListener(mSearchTextChangedListener);

        mOnEditorActionListener = new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int imeActionId, KeyEvent keyEvent) {
                return keyEvent != null
                        && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || imeActionId == EditorInfo.IME_ACTION_DONE
                        || keyEvent.getKeyCode() == KeyEvent.KEYCODE_SEARCH)
                        && search(getSearchText());
            }
        };
        mSearchInputEditText.setOnEditorActionListener(mOnEditorActionListener);

        mSearchInputEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                String currentText = getSearchText();
                if (mInteractionListeners != null) {
                    for (SearchViewInteractionListener listener : mInteractionListeners) {
                        listener.onSearchViewFocusChanged(focused);
                    }
                }
                if (focused) {
                    if (currentText.length() > 0) {
                        moveToState(STATE_FOCUSED_TEXT_PRESENT);
                    } else {
                        moveToState(STATE_FOCUSED_EMPTY);
                    }
                    moveCursorToEnd();
                }
            }
        });
    }

    ///////////////////Begin API///////////////////

    /**
     * Enable or disable the database and search suggestions.
     *
     * @param enabled
     *         Whether the database and search suggestions is enabled.
     */
    @SuppressWarnings("unused")
    public void setDatabaseAndSuggestionsEnabled(boolean enabled) {
        if (mSuggestionsRecyclerView != null) {
            mSuggestionsRecyclerView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (enabled) {
            SearchDatabase.destroy();
            SearchDatabase.init(mContext.get());
        } else {
            cancelSuggestions();
            if (mSuggestionsAdapter != null) {
                mSuggestionsAdapter.setSuggestions(new ArrayList<SearchSuggestion>());
                mSuggestionsAdapter.notifyDataSetChanged();
            }
            SearchDatabase.deleteDatabase(null);
            SearchDatabase.destroy();
        }
        mDatabaseSuggestionsEnabled = enabled;
    }

    /**
     * Sets the maximum number of suggestions that should be shown at any given time.
     *
     * @param limit
     *         The maximum number or limit of suggestions.
     */
    @SuppressWarnings("unused")
    public void setMaximumSuggestions(int limit) {
        mMaxSuggestions = limit;
    }

    /**
     * Set the search term for the MaterialSearchView.
     *
     * @param searchTerm
     *         The search term to be set for the view. Can be set to null to clear the view.
     */
    @SuppressWarnings("unused")
    public void setSearchTerm(@Nullable String searchTerm) {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.setText(searchTerm);
            hideMic();
        } else {
            showMic();
        }
    }

    /**
     * Get the search term currently entered into the MaterialSearchView.
     *
     * @return The search term or null if none exists.
     */
    @Nullable
    @SuppressWarnings("unused")
    public String getSearchTerm() {
        if (mSearchInputEditText != null) {
            return mSearchInputEditText.getText().toString();
        }
        return null;
    }

    /**
     * Deletes the database.
     */
    @SuppressWarnings("unused")
    public void clearSearchHistory() {
        SearchDatabase.deleteDatabase(null);
        SearchDatabase.init(mContext.get());
    }

    /**
     * Deletes the database.
     *
     * @param listener
     *         DatabaseTaskListener to listen for the changes.
     */
    @SuppressWarnings("unused")
    public void clearSearchHistory(@Nullable DatabaseTaskListener listener) {
        SearchDatabase.deleteDatabase(listener);
        SearchDatabase.init(mContext.get());
    }

    /**
     * Sets the number of suggestions displayed to the user.
     *
     * @param limit
     *         The limit of suggestions. Default is 5.
     */
    @SuppressWarnings("unused")
    public void setSuggestionsLimit(int limit) {
        mSearchSuggestionsLimit = limit;
    }

    /**
     * Set the voice feature to be enabled or disabled.
     *
     * @param enabled
     *         Whether the voice feature is enabled.
     */
    @SuppressWarnings("unused")
    public void setVoiceEnabled(boolean enabled) {
        mVoiceFeaturesEnabled = enabled;
    }

    /**
     * Set the main icon for the MaterialSearchView.
     *
     * @param drawableResId
     *         The drawable resource id for the icon.
     */
    @SuppressWarnings("unused")
    public void setMainIcon(int drawableResId) {
        if (mSearchIcon != null) {
            mSearchIcon.setImageResource(drawableResId);
        }
    }

    /**
     * Set the main icon for the MaterialSearchView.
     *
     * @param drawable
     *         The drawable for the icon.
     */
    @SuppressWarnings("unused")
    public void setMainIcon(@Nullable Drawable drawable) {
        if (mSearchIcon != null) {
            mSearchIcon.setImageDrawable(drawable);
        }
    }

    /**
     * Set the main icon for the MaterialSearchView.
     *
     * @param bitmap
     *         The bitmap for the icon.
     */
    @SuppressWarnings("unused")
    public void setMainIcon(@Nullable Bitmap bitmap) {
        if (mSearchIcon != null) {
            mSearchIcon.setImageBitmap(bitmap);
        }
    }

    /**
     * Set the main icon for the MaterialSearchView.
     *
     * @param imageUri
     *         The image URI for the icon.
     */
    @SuppressWarnings("unused")
    public void setMainIcon(@Nullable Uri imageUri) {
        if (mSearchIcon != null) {
            mSearchIcon.setImageURI(imageUri);
        }
    }

    /**
     * Hide the main icon for the MaterialSearchView.
     */
    @SuppressWarnings("unused")
    public void hideMainIcon() {
        if (mSearchIcon != null) {
            mSearchIcon.setVisibility(View.GONE);
        }
    }

    /**
     * Gets the voice image button.
     *
     * @return The image button.
     */
    @Nullable
    @SuppressWarnings("unused")
    public ImageButton getVoiceImageButton() {
        return mMicButton;
    }

    /**
     * Sets the icon for the voice feature.
     *
     * @param drawableResId
     *         The drawable resource id for the icon.
     */
    @SuppressWarnings("unused")
    public void setVoiceIcon(int drawableResId) {
        if (mMicButton != null) {
            mMicButton.setImageResource(drawableResId);
        }
    }

    /**
     * Sets the icon for the voice feature.
     *
     * @param drawable
     *         The drawable for the icon.
     */
    @SuppressWarnings("unused")
    public void setVoiceIcon(@Nullable Drawable drawable) {
        if (mMicButton != null) {
            mMicButton.setImageDrawable(drawable);
        }
    }

    /**
     * Sets the icon for the voice feature.
     *
     * @param bitmap
     *         The bitmap for the icon.
     */
    @SuppressWarnings("unused")
    public void setVoiceIcon(@Nullable Bitmap bitmap) {
        if (mMicButton != null) {
            mMicButton.setImageBitmap(bitmap);
        }
    }

    /**
     * Sets the icon for the voice feature.
     *
     * @param imageUri
     *         The uri for the icon.
     */
    @SuppressWarnings("unused")
    public void setVoiceIcon(@Nullable Uri imageUri) {
        if (mMicButton != null) {
            mMicButton.setImageURI(imageUri);
        }
    }

    /**
     * Hides the voice icon until showVoiceIcon() is called.
     */
    @SuppressWarnings("unused")
    public void hideVoiceIcon() {
        if (mMicButton != null) {
            mMicButton.setVisibility(View.GONE);
            mHideMicIcon = true;
        }
    }

    /**
     * Get the clear text image button.
     *
     * @return The image button.
     */
    @Nullable
    @SuppressWarnings("unused")
    public ImageButton getClearButton() {
        return mClearButton;
    }

    /**
     * Set the icon for the clear button.
     *
     * @param drawableResId
     *         The drawable resource id to set.
     */
    @SuppressWarnings("unused")
    public void setClearIcon(int drawableResId) {
        if (mClearButton != null) {
            mClearButton.setImageResource(drawableResId);
        }
    }

    /**
     * Set the icon for the clear button.
     *
     * @param drawable
     *         The drawable to set.
     */
    @SuppressWarnings("unused")
    public void setClearIcon(@Nullable Drawable drawable) {
        if (mClearButton != null) {
            mClearButton.setImageDrawable(drawable);
        }
    }

    /**
     * Set the bitmap for the clear button.
     *
     * @param bitmap
     *         The bitmap to set.
     */
    @SuppressWarnings("unused")
    public void setClearIcon(@Nullable Bitmap bitmap) {
        if (mClearButton != null) {
            mClearButton.setImageBitmap(bitmap);
        }
    }

    /**
     * Set the icon for the clear button.
     *
     * @param imageUri
     *         The uri to set.
     */
    @SuppressWarnings("unused")
    public void setClearIcon(@Nullable Uri imageUri) {
        if (mClearButton != null) {
            mClearButton.setImageURI(imageUri);
        }
    }

    /**
     * Shows the clear icon until hideClearIcon() is called.
     */
    @SuppressWarnings("unused")
    public void showClearIcon() {
        if (mClearButton != null) {
            mClearButton.setVisibility(View.VISIBLE);
            mHideClearIcon = false;
        }
    }

    /**
     * Hides the clear icon until showClearIcon() is called.
     */
    @SuppressWarnings("unused")
    public void hideClearIcon() {
        if (mClearButton != null) {
            mClearButton.setVisibility(View.GONE);
            mHideClearIcon = true;
        }
    }

    /**
     * Sets the background for the MaterialSearchView.
     *
     * @param colorResId
     *         The color resource id for the background.
     */
    @SuppressWarnings("unused")
    public void setBackgroundColor(int colorResId) {
        if (mSearchCard != null) {
            mSearchCard.setBackgroundColor(colorResId);
        }
    }

    /**
     * Sets the background resource for the MaterialSearchView.
     *
     * @param drawable
     *         The drawable for the background.
     */
    @SuppressWarnings("unused")
    public void setBackground(@Nullable Drawable drawable) {
        if (mSearchCard != null) {
            mSearchCard.setBackground(drawable);
        }
    }

    /**
     * Sets the background resource for the MaterialSearchView.
     *
     * @param resourceId
     *         The resource id for the background.
     */
    @SuppressWarnings("unused")
    public void setBackground(int resourceId) {
        if (mSearchCard != null) {
            mSearchCard.setBackgroundResource(resourceId);
        }
    }

    /**
     * Sets the color of the hint text.
     *
     * @param colorResId
     *         The color for the hint text.
     */
    @SuppressWarnings("unused")
    public void setHintColor(int colorResId) {
        int resolvedColor = ContextCompat.getColor(mContext.get(), colorResId);
        if (mSearchInputEditText != null) {
            mSearchInputEditText.setHintTextColor(resolvedColor);
        }
    }

    /**
     * Sets the text color of the MaterialSearchView.
     *
     * @param colorResId
     *         The color for the text.
     */
    @SuppressWarnings("unused")
    public void setTextColor(int colorResId) {
        int resolvedColor = ContextCompat.getColor(mContext.get(), colorResId);
        if (mSearchInputEditText != null) {
            mSearchInputEditText.setTextColor(resolvedColor);
        }
    }

    /**
     * Sets the color of the existing icon set.
     *
     * @param colorResId
     *         The color for the icons.
     */
    @SuppressWarnings("unused")
    public void setIconTint(int colorResId) {
        int resolvedColor = ContextCompat.getColor(mContext.get(), colorResId);
        if (mSearchIcon != null) {
            DrawableCompat.setTint(mSearchIcon.getDrawable(), resolvedColor);
        }
        if (mMicButton != null) {
            DrawableCompat.setTint(mMicButton.getDrawable(), resolvedColor);
        }
        if (mClearButton != null) {
            DrawableCompat.setTint(mClearButton.getDrawable(), resolvedColor);
        }
        mSuggestionsResolvedTintColor = resolvedColor;
        if (mSuggestionsAdapter != null) {
            mSuggestionsAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Persist suggestions for future use.
     *
     * @param suggestions
     *         Suggestions to persist.
     */
    @SuppressWarnings("unused")
    public void persistSuggestions(@NonNull String[] suggestions) {
        if (mDatabaseSuggestionsEnabled) {
            ArrayList<SearchSuggestion> searchSuggestions = new ArrayList<>(suggestions.length);
            for (int i = 0; i < suggestions.length; i++) {
                String currentTime = String.valueOf(System.currentTimeMillis() - 1000 * i);
                searchSuggestions.add(new SearchSuggestion(suggestions[i], currentTime));
            }
            SearchDatabase.addPerformedSearches(searchSuggestions, null);
        }
    }

    /**
     * Persist suggestions for future use.
     *
     * @param suggestions
     *         Suggestions to persist.
     * @param listener
     *         A listener for the completion of the persistence.
     */
    @SuppressWarnings("unused")
    public void persistSuggestions(@NonNull String[] suggestions, @Nullable DatabaseTaskListener listener) {
        if (mDatabaseSuggestionsEnabled) {
            ArrayList<SearchSuggestion> searchSuggestions = new ArrayList<>(suggestions.length);
            for (int i = 0; i < suggestions.length; i++) {
                String currentTime = String.valueOf(System.currentTimeMillis() - 1000 * i);
                searchSuggestions.add(new SearchSuggestion(suggestions[i], currentTime));
            }
            SearchDatabase.addPerformedSearches(searchSuggestions, listener);
        }
    }

    /**
     * Set suggestions for the user to search for.
     *
     * @param suggestions
     *         Suggestions for the user.
     */
    @SuppressWarnings("unused")
    public void setSuggestions(@Nullable String[] suggestions) {
        ArrayList<SearchSuggestion> searchSuggestions = new ArrayList<>();
        if (suggestions != null) {
            searchSuggestions = new ArrayList<>(suggestions.length);
            for (int i = 0; i < suggestions.length; i++) {
                String currentTime = String.valueOf(System.currentTimeMillis() - 1000 * i);
                searchSuggestions.add(new SearchSuggestion(suggestions[i], currentTime));
            }
        }
        if (mSuggestionsAdapter != null) {
            mSuggestionsAdapter.setSuggestions(searchSuggestions);
        }
    }

    /**
     * Persist suggestions for future use.
     *
     * @param searchSuggestions
     *         Searches to persist.
     */
    @SuppressWarnings("unused")
    public void persistSuggestions(@NonNull ArrayList<SearchSuggestion> searchSuggestions) {
        if (mDatabaseSuggestionsEnabled) {
            SearchDatabase.addPerformedSearches(searchSuggestions, null);
        }
    }

    /**
     * Persist suggestions for future use.
     *
     * @param searchSuggestions
     *         Searches to persist.
     * @param listener
     *         A listener for the completion of the persistence.
     */
    @SuppressWarnings("unused")
    public void persistSuggestions(@NonNull ArrayList<SearchSuggestion> searchSuggestions,
                                   @Nullable DatabaseTaskListener listener) {
        if (mDatabaseSuggestionsEnabled) {
            SearchDatabase.addPerformedSearches(searchSuggestions, listener);
        }
    }

    /**
     * Set suggestions for the user to search for.
     *
     * @param suggestions
     *         The suggestions for the user.
     */
    @SuppressWarnings("unused")
    public void setSuggestions(@Nullable ArrayList<SearchSuggestion> suggestions) {
        if (mSuggestionsAdapter != null) {
            mSuggestionsAdapter.setSuggestions(suggestions != null ? suggestions : new ArrayList<SearchSuggestion>());
        }
    }

    /**
     * Get the suggested and historical searches performed on this device.
     * The response of this call can have a large ArrayList depending on maximum searches allowed.
     *
     * @param listener
     *         The listener for the completion of the lookup.
     * @return The task so that it can be canceled if necessary.
     */
    @SuppressWarnings("unused")
    public GetPerformedSearchesTask getAllSuggestedSearches(@NonNull DatabaseReadSearchesListener listener) {
        return SearchDatabase.getPerformedSearches(listener);
    }

    /**
     * Get the suggested and historical searches performed on this device.
     * The response of this call can have a large ArrayList depending on maximum searches allowed.
     *
     * @param limit
     *         The maximum number of rows that should be returned.
     * @param listener
     *         The listener for the completion of the lookup.
     * @return The task so that it can be canceled if necessary.
     */
    @SuppressWarnings("unused")
    public GetPerformedSearchesTask getAllSuggestedSearches(int limit, @NonNull DatabaseReadSearchesListener listener) {
        return SearchDatabase.getPerformedSearches(limit, listener);
    }

    /**
     * Add SearchViewSearchListener.
     *
     * @param listener
     *         The listener.
     */
    @SuppressWarnings("unused")
    public void addListener(@NonNull SearchViewSearchListener listener) {
        if (mSearchListeners != null) {
            mSearchListeners.add(listener);
        }
    }

    /**
     * Remove SearchViewSearchListener.
     *
     * @param listener
     *         The listener.
     */
    @SuppressWarnings("unused")
    public void removeListener(@NonNull SearchViewSearchListener listener) {
        if (mSearchListeners != null) {
            mSearchListeners.remove(listener);
        }
    }

    /**
     * Clears all SearchViewSearchListener listeners.
     */
    @SuppressWarnings("unused")
    public void clearSearchListeners() {
        if (mSearchListeners != null) {
            mSearchListeners.clear();
        }
    }

    /**
     * Add SearchViewInteractionListener.
     *
     * @param listener
     *         The listener.
     */
    @SuppressWarnings("unused")
    public void addListener(@NonNull SearchViewInteractionListener listener) {
        if (mInteractionListeners != null) {
            mInteractionListeners.add(listener);
        }
    }

    /**
     * Remove SearchViewInteractionListener.
     *
     * @param listener
     *         The listener.
     */
    @SuppressWarnings("unused")
    public void removeListener(@NonNull SearchViewInteractionListener listener) {
        if (mInteractionListeners != null) {
            mInteractionListeners.remove(listener);
        }
    }

    /**
     * Clears all SearchViewSearchListener listeners.
     */
    @SuppressWarnings("unused")
    public void clearInteractionListeners() {
        if (mInteractionListeners != null) {
            mInteractionListeners.clear();
        }
    }

    /**
     * Add SearchViewEventListener.
     *
     * @param listener
     *         The listener.
     */
    @SuppressWarnings("unused")
    public void addListener(@NonNull SearchViewEventListener listener) {
        if (mEventListeners != null) {
            mEventListeners.add(listener);
        }
    }

    /**
     * Remove SearchViewEventListener.
     *
     * @param listener
     *         The listener.
     */
    @SuppressWarnings("unused")
    public void removeListener(@NonNull SearchViewEventListener listener) {
        if (mEventListeners != null) {
            mEventListeners.remove(listener);
        }
    }

    /**
     * Clears all SearchViewSearchListener listeners.
     */
    @SuppressWarnings("unused")
    public void clearEventListeners() {
        if (mEventListeners != null) {
            mEventListeners.clear();
        }
    }

    /**
     * Add SearchViewVoiceListener.
     *
     * @param listener
     *         The listener.
     */
    @SuppressWarnings("unused")
    public void addListener(@NonNull SearchViewVoiceListener listener) {
        if (mVoiceListeners != null) {
            mVoiceListeners.add(listener);
        }
    }

    /**
     * Remove SearchViewVoiceListener.
     *
     * @param listener
     *         The listener.
     */
    @SuppressWarnings("unused")
    public void removeListener(@NonNull SearchViewVoiceListener listener) {
        if (mVoiceListeners != null) {
            mVoiceListeners.remove(listener);
        }
    }

    /**
     * Clears all SearchViewSearchListener listeners.
     */
    @SuppressWarnings("unused")
    public void clearVoiceListeners() {
        if (mVoiceListeners != null) {
            mVoiceListeners.clear();
        }
    }

    /**
     * Clears all listeners.
     */
    @SuppressWarnings("unused")
    public void clearAllListeners() {
        destroyListeners();
    }

    /**
     * Closes the SearchView on back press when used.
     */
    @SuppressWarnings("unused")
    public boolean onBackPressed() {
        if (mCurrentState != STATE_DEFAULT) {
            moveToState(STATE_DEFAULT);
            return true;
        }
        return false;
    }

    ///////////////////End API///////////////////

    private void moveCursorToEnd() {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.setSelection(getSearchText().length());
        }
    }

    private void startVoiceSearch() {
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            int permissionStatus = ContextCompat.checkSelfPermission(mContext.get(), permission.RECORD_AUDIO);
            if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                performVoiceSearch();
            }
        }
    }

    private void performVoiceSearch() {
        if (SpeechRecognizer.isRecognitionAvailable(mContext.get())) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    mContext.get().getString(R.string.speech_prompt));
            SpeechRecognizer speechRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext.get());
            speechRecognizer.setRecognitionListener(this);
            speechRecognizer.startListening(intent);
        }
    }

    private boolean search(@NonNull String searchTerm) {
        moveToState(STATE_UNFOCUSED_TEXT_PRESENT);
        if (!TextUtils.isEmpty(searchTerm)) {
            setSearchText(searchTerm);
            if (mDatabaseSuggestionsEnabled) {
                SearchDatabase.addPerformedSearch(null, new SearchSuggestion(searchTerm, String.valueOf(System.currentTimeMillis())));
            }
            if (mSearchListeners != null) {
                for (SearchViewSearchListener listener : mSearchListeners) {
                    listener.onSearch(searchTerm);
                }
            }
            if (mInteractionListeners != null) {
                for (SearchViewInteractionListener listener : mInteractionListeners) {
                    listener.onSearch(searchTerm);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private void setKeyboardActionListener() {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.setOnEditorActionListener(mOnEditorActionListener);
        }
    }

    private void clearKeyboardActionListener() {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.setOnEditorActionListener(null);
        }
    }

    private void setSearchText(@NonNull String searchTerm) {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.removeTextChangedListener(mSearchTextChangedListener);
            mSearchInputEditText.setText(searchTerm);
            moveCursorToEnd();
            mSearchInputEditText.addTextChangedListener(mSearchTextChangedListener);
        }
    }

    private void showMic() {
        if (mMicButton != null && mClearButton != null) {
            mMicButton.setVisibility(mHideMicIcon ? View.GONE : View.VISIBLE);
            mClearButton.setVisibility(View.GONE);
        }
    }

    private void hideMic() {
        if (mMicButton != null && mClearButton != null) {
            mMicButton.setVisibility(View.GONE);
            mClearButton.setVisibility(mHideClearIcon ? View.GONE : View.VISIBLE);
        }
    }

    private void clearSuggestions() {
        if (mSuggestionsAdapter != null) {
            mSuggestionsAdapter.setSuggestions(new ArrayList<SearchSuggestion>());
            adjustSuggestionsBoxHeight(0);
        }
    }

    private void cancelSuggestions() {
        if (mFilterRunnable != null) {
            mHandler.removeCallbacks(mFilterRunnable);
        }
        if (mFilterSearchTask != null) {
            mFilterSearchTask.cancel();
        }
    }

    private void adjustSuggestionsBoxHeight(int numberOfRows) {
        if (mSuggestionsRecyclerView != null && mSuggestionRowHeight > 0) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mSuggestionsRecyclerView.getLayoutParams();
            params.height = (int) (numberOfRows * mSuggestionRowHeight);
            mSuggestionsRecyclerView.setLayoutParams(params);
        }
    }

    private void closeKeyboard() {
        if (mContext != null && mSearchInputEditText != null) {
            InputMethodManager inputManager = (InputMethodManager)
                    mContext.get().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(mSearchInputEditText.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    private void showRecentSearches() {
        if (mDatabaseSuggestionsEnabled) {
            int limit = Math.min(mSearchSuggestionsLimit, mMaxSuggestions);
            mRecentSearchesTask = SearchDatabase.getRecentSearches(limit, MaterialSearchView.this);
        }
    }

    private void filterSearchSuggestions(@NonNull String searchTerm) {
        if (mDatabaseSuggestionsEnabled) {
            int limit = Math.min(mSearchSuggestionsLimit, mMaxSuggestions);
            mFilterSearchTask = SearchDatabase.filterSearchesBy(limit, searchTerm, MaterialSearchView.this);
        }
    }

    private void clearSearchViewFocus() {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.clearFocus();
        }
    }

    private void emptySearchView() {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.removeTextChangedListener(mSearchTextChangedListener);
            mSearchInputEditText.setText("");
            mSearchInputEditText.addTextChangedListener(mSearchTextChangedListener);
        }
    }

    private void timedFilterSearchSuggestions(@NonNull String searchTerm) {
        if (mDatabaseSuggestionsEnabled) {
            if (mFilterRunnable != null) {
                mHandler.removeCallbacks(mFilterRunnable);
            }
            mHandler.postDelayed(createFilterRunnable(searchTerm), TIME_SEARCH_AFTER_KEY_PRESS_DELAY);
        }
    }

    @NonNull
    private Runnable createFilterRunnable(@NonNull final String searchTerm) {
        mFilterRunnable = new Runnable() {
            @Override
            public void run() {
                if (mFilterSearchTask != null) {
                    mFilterSearchTask.cancel();
                }
                filterSearchSuggestions(searchTerm);
            }
        };
        return mFilterRunnable;
    }

    @NonNull
    private String getSearchText() {
        if (mSearchInputEditText != null) {
            return mSearchInputEditText.getText().toString();
        }
        return "";
    }

    private void moveToState(@MaterialSearchViewState int state) {
        if (mCurrentState != state) {
            mCurrentState = state;
            switch (state) {
                case STATE_FOCUSED_EMPTY:
                    cancelSuggestions();
                    emptySearchView();
                    setKeyboardActionListener();
                    showRecentSearches();
                    showMic();
                    break;
                case STATE_FOCUSED_TEXT_PRESENT:
                    filterSearchSuggestions(getSearchText());
                    setKeyboardActionListener();
                    hideMic();
                    break;
                case STATE_UNFOCUSED_TEXT_PRESENT:
                    cancelSuggestions();
                    clearSuggestions();
                    closeKeyboard();
                    clearKeyboardActionListener();
                    hideMic();
                    break;
                case STATE_FOCUSED_TEXT_PRESENT_DELAY_SEARCH:
                    setKeyboardActionListener();
                    timedFilterSearchSuggestions(getSearchText());
                    hideMic();
                    break;
                case STATE_DEFAULT:
                default:
                    cancelSuggestions();
                    clearKeyboardActionListener();
                    closeKeyboard();
                    clearSearchViewFocus();
                    emptySearchView();
                    clearSuggestions();
                    showMic();
                    break;
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.d(TAG, ".onAttachedToWindow()");
        if (mDatabaseSuggestionsEnabled) {
            SearchDatabase.init(mContext.get());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.d(TAG, ".onDetachedFromWindow()");
        mContext = null;
        if (mRecentSearchesTask != null) {
            mRecentSearchesTask.cancel();
        }
        cancelSuggestions();
        destroyListeners();
        SearchDatabase.destroy();
    }

    @Override
    public void onComplete(@NonNull ArrayList<SearchSuggestion> suggestions) {
        if (mSuggestionsAdapter != null && mDatabaseSuggestionsEnabled) {
            mSuggestionsAdapter.setSuggestions(suggestions);
            //expandSuggestions(suggestions.size());
            adjustSuggestionsBoxHeight(suggestions.size());
        }
        if (mEventListeners != null) {
            for (SearchViewEventListener listener : mEventListeners) {
                listener.onNewSuggestions(suggestions);
            }
        }
    }

    private void destroyListeners() {
        mSearchListeners = null;
        mInteractionListeners = null;
        mVoiceListeners = null;
        mEventListeners = null;
    }

    @Override
    public void onReadyForSpeech(Bundle bundle) {

    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onRmsChanged(float v) {

    }

    @Override
    public void onBufferReceived(byte[] bytes) {

    }

    @Override
    public void onEndOfSpeech() {

    }

    @Override
    public void onError(int i) {

    }

    @Override
    public void onResults(Bundle bundle) {

    }

    @Override
    public void onPartialResults(Bundle bundle) {

    }

    @Override
    public void onEvent(int i, Bundle bundle) {

    }

    private class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionViewHolder> {

        @NonNull
        private ArrayList<SearchSuggestion> mSuggestions = new ArrayList<>();

        @Override
        public SuggestionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_suggestion, parent, false);
            return new SuggestionViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(SuggestionViewHolder holder, int position) {
            if (position < mSuggestions.size()) {
                holder.bind(mSuggestions.get(position).getSearchTerm());
                if (position == mSuggestions.size() - 1) {
                    holder.hideDivider();
                }
            }
        }

        @Override
        public int getItemCount() {
            return mSuggestions.size();
        }

        void setSuggestions(@NonNull ArrayList<SearchSuggestion> suggestions) {
            mSuggestions = suggestions;
            notifyDataSetChanged();
        }
    }

    class SuggestionViewHolder extends ViewHolder {
        private final View mRootView;
        private TextView mSuggestionTextView;
        private ImageView mSuggestionHistoryIcon;
        private ImageView mSuggestionPointIcon;
        private View mDivider;
        private String mSuggestion;

        private OnClickListener mOnClickListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                search(mSuggestion);
            }
        };

        SuggestionViewHolder(View itemView) {
            super(itemView);
            mRootView = itemView;
            mSuggestionHistoryIcon = itemView.findViewById(R.id.list_item_history_icon);
            mSuggestionPointIcon = itemView.findViewById(R.id.list_item_pointer_icon);
            mSuggestionTextView = itemView.findViewById(R.id.list_item_suggestion);
            mDivider = itemView.findViewById(R.id.list_item_divider_line);
            DrawableCompat.setTint(mSuggestionHistoryIcon.getDrawable(), mSuggestionsResolvedTintColor);
            DrawableCompat.setTint(mSuggestionPointIcon.getDrawable(), mSuggestionsResolvedTintColor);
        }

        void bind(@NonNull final String suggestion) {
            mSuggestion = suggestion;
            mSuggestionTextView.setText(suggestion);
            mDivider.setVisibility(View.VISIBLE);
            mRootView.setOnClickListener(mOnClickListener);
        }

        void hideDivider() {
            mDivider.setVisibility(View.GONE);
        }
    }

    @Retention(SOURCE)
    @IntDef({STATE_DEFAULT, STATE_FOCUSED_EMPTY, STATE_FOCUSED_TEXT_PRESENT,
            STATE_FOCUSED_TEXT_PRESENT_DELAY_SEARCH, STATE_UNFOCUSED_TEXT_PRESENT})
    private @interface MaterialSearchViewState {}

    public static final int STATE_DEFAULT = 0;
    public static final int STATE_FOCUSED_EMPTY = 1;
    public static final int STATE_FOCUSED_TEXT_PRESENT = 2;
    public static final int STATE_FOCUSED_TEXT_PRESENT_DELAY_SEARCH = 3;
    public static final int STATE_UNFOCUSED_TEXT_PRESENT = 4;

    @SuppressWarnings({"unused", "WeakerAccess"})
    public interface SearchViewInteractionListener extends SearchViewSearchListener {
        void onVoiceButtonClick();

        void onClearButtonClick();

        void onSearchViewFocusChanged(boolean focused);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public interface SearchViewEventListener {
        void onNewSuggestions(@NonNull ArrayList<SearchSuggestion> suggestions);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public interface SearchViewVoiceListener {
        void onVoiceSearchFailed(int error);

        void onVoiceSearchPermissionNeeded();

        void onVoiceSearchIncompatible();

        void onVoiceSearch(@NonNull String searchTerm);
    }

    public interface SearchViewSearchListener {
        void onSearch(@NonNull String searchTerm);
    }
}
