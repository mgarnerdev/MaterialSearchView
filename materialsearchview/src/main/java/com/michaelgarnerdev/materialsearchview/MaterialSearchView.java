package com.michaelgarnerdev.materialsearchview;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Animatable;
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
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
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

import static com.michaelgarnerdev.materialsearchview.MaterialSearchView.SearchViewVoiceListener.VOICE_SEARCH_ERROR_MISSING_CONTEXT;
import static com.michaelgarnerdev.materialsearchview.MaterialSearchView.SearchViewVoiceListener.VOICE_SEARCH_ERROR_NO_RESULTS;
import static com.michaelgarnerdev.materialsearchview.MaterialSearchView.SearchViewVoiceListener.VOICE_SEARCH_ERROR_PERMISSION_DENIED;
import static com.michaelgarnerdev.materialsearchview.MaterialSearchView.SearchViewVoiceListener.VOICE_SEARCH_ERROR_PERMISSION_NEEDED;
import static com.michaelgarnerdev.materialsearchview.MaterialSearchView.SearchViewVoiceListener.VOICE_SEARCH_ERROR_UNAVAILABLE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Created by mgarnerdev on 7/29/2017.
 * MaterialSearchView helps you add material-themed searching to your app with ease.
 */

public class MaterialSearchView extends LinearLayout implements
        DatabaseReadSearchesListener,
        RecognitionListener {
    private static final String TAG = MaterialSearchView.class.getSimpleName();

    public static final int REQUEST_CODE_PERMISSION_RECORD_AUDIO = 777;

    private static final long TIME_SEARCH_AFTER_KEY_PRESS_DELAY = 300;

    private static final boolean DEFAULT_DATABASE_SUGGESTIONS_ENABLED = true;
    private static final int DEFAULT_MAX_SUGGESTIONS_SHOWN = 5;
    private static final int DEFAULT_MAX_SUGGESTIONS_PERSISTED = 1000;
    private static final boolean DEFAULT_VOICE_FEATURES_ENABLED = VERSION.SDK_INT < VERSION_CODES.M;
    private static final long CLEAR_TEXT_DELAY_TIME = 100;

    private WeakReference<Context> mContext;

    private CardView mSearchCard;
    private ImageView mSearchIcon;
    private EditText mSearchInputEditText;
    private ImageButton mVoiceButton;
    private ImageButton mClearButton;

    private CardView mVoiceSearchCard;
    private TextView mVoiceSearchListeningPrompt;
    private TextView mVoiceSearchListeningRealTimeText;

    private Handler mHandler = new Handler();

    private GetPerformedSearchesStartingWithTask mFilterSearchTask;
    private GetRecentSearchesTask mRecentSearchesTask;
    private Runnable mFilterRunnable;
    private Runnable mClearTextRunnable;
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

    private int mMaxSuggestionsPersisted = DEFAULT_MAX_SUGGESTIONS_PERSISTED;
    private int mMaxSuggestionsShown = DEFAULT_MAX_SUGGESTIONS_SHOWN;
    private boolean mDatabaseSuggestionsEnabled = DEFAULT_DATABASE_SUGGESTIONS_ENABLED;
    private boolean mVoiceFeaturesEnabled = DEFAULT_VOICE_FEATURES_ENABLED;
    private String mHintText = null;
    private String mText = null;
    private Integer mTextColor = null;
    private Integer mHintTextColor = null;
    private Drawable mMainIcon;
    private Drawable mVoiceIcon;
    private Drawable mClearIcon;
    private Drawable mBackground = null;
    private Integer mBackgroundColor = null;
    private Integer mIconTintColor = null;
    private boolean mHideMicIcon = false;
    private boolean mHideClearIcon = false;
    private SpeechRecognizer mSpeechRecognizer;
    private AnimatedVectorDrawableCompat mAnimatedVoiceListenerDrawable;
    private AnimatedVectorDrawableWrapper mAnimatedVoiceListenerDrawableWrapper;
    private long mAnimatedVoiceListenerDuration;
    private boolean mVoiceResultsAvailable = false;


    public MaterialSearchView(Context context) {
        this(context, null);
    }

    public MaterialSearchView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaterialSearchView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @RequiresApi(api = VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("unused")
    public MaterialSearchView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(@NonNull Context context,
                      @Nullable AttributeSet attrs,
                      int defStyle,
                      int defStyleRes) {
        mContext = new WeakReference<>(context);
        Resources resources = context.getResources();
        mSuggestionRowHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, resources.getDisplayMetrics());
        mIconTintColor = ContextCompat.getColor(context, R.color.primary_text);
        mHintTextColor = ContextCompat.getColor(context, R.color.secondary_text);
        mAnimatedVoiceListenerDrawable = AnimatedVectorDrawableCompat.create(
                context,
                R.drawable.ic_animated_vector_listening
        );
        mAnimatedVoiceListenerDuration = context.getResources()
                .getInteger(R.integer.voice_listener_animation_duration);
        TypedArray typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.MaterialSearchView,
                defStyle,
                defStyleRes);
        if (typedArray != null) {
            try {
                mDatabaseSuggestionsEnabled = typedArray.getBoolean(
                        R.styleable.MaterialSearchView_databaseSuggestionsEnabled,
                        DEFAULT_DATABASE_SUGGESTIONS_ENABLED
                );
                mMaxSuggestionsPersisted = typedArray.getInt(
                        R.styleable.MaterialSearchView_maxSuggestionsPersisted,
                        DEFAULT_MAX_SUGGESTIONS_PERSISTED
                );
                mMaxSuggestionsShown = typedArray.getInt(
                        R.styleable.MaterialSearchView_maxSuggestionsShown,
                        DEFAULT_MAX_SUGGESTIONS_SHOWN
                );
                mVoiceFeaturesEnabled = typedArray.getBoolean(
                        R.styleable.MaterialSearchView_voiceFeaturesEnabled,
                        DEFAULT_VOICE_FEATURES_ENABLED
                );
                mMainIcon = typedArray.getDrawable(R.styleable.MaterialSearchView_mainIcon);
                mVoiceIcon = typedArray.getDrawable(R.styleable.MaterialSearchView_voiceIcon);
                mClearIcon = typedArray.getDrawable(R.styleable.MaterialSearchView_clearIcon);
                mBackground = typedArray.getDrawable(R.styleable.MaterialSearchView_background);

                if (typedArray.hasValue(R.styleable.MaterialSearchView_hintText)) {
                    mHintText = typedArray.getString(R.styleable.MaterialSearchView_hintText);
                }
                if (typedArray.hasValue(R.styleable.MaterialSearchView_text)) {
                    mText = typedArray.getString(R.styleable.MaterialSearchView_text);
                }
                if (typedArray.hasValue(R.styleable.MaterialSearchView_hintTextColor)) {
                    mHintTextColor = typedArray.getColor(R.styleable.MaterialSearchView_hintTextColor, mHintTextColor);
                }
                if (typedArray.hasValue(R.styleable.MaterialSearchView_textColor)) {
                    if (mTextColor == null) {
                        mTextColor = mIconTintColor;
                    }
                    mTextColor = typedArray.getColor(R.styleable.MaterialSearchView_textColor, mTextColor);
                }
                if (typedArray.hasValue(R.styleable.MaterialSearchView_backgroundColor)) {
                    if (mBackgroundColor == null) {
                        mBackgroundColor = ContextCompat.getColor(context, android.R.color.white);
                    }
                    mBackgroundColor = typedArray.getColor(R.styleable.MaterialSearchView_backgroundColor, mBackgroundColor);
                }
                if (typedArray.hasValue(R.styleable.MaterialSearchView_iconTintColor)) {
                    mIconTintColor = typedArray.getColor(R.styleable.MaterialSearchView_iconTintColor, mIconTintColor);
                }
            } finally {
                typedArray.recycle();
            }
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Log.d(TAG, ".onFinishInflate()");
        if (mContext != null) {
            Context context = mContext.get();
            if (context != null) {
                inflate(context, R.layout.material_search_view, this);
                setupView(context);
            }
        }
    }

    private void setupView(@NonNull Context context) {
        mSearchCard = findViewById(R.id.msv_search_card);
        mSearchIcon = findViewById(R.id.msv_search_card_input_icon);
        mSearchInputEditText = findViewById(R.id.msv_search_card_input_edit_text);
        mVoiceButton = findViewById(R.id.msv_search_card_input_microphone);
        mClearButton = findViewById(R.id.msv_search_card_input_cancel);

        mVoiceSearchCard = findViewById(R.id.msv_voice_search_card);
        ImageView voiceSearchImageView = findViewById(R.id.msv_voice_search_image_view);
        if (mAnimatedVoiceListenerDrawable != null) {
            voiceSearchImageView.setImageDrawable(mAnimatedVoiceListenerDrawable);
        }

        mVoiceSearchListeningPrompt = findViewById(R.id.msv_voice_search_listening_prompt);
        mVoiceSearchListeningRealTimeText = findViewById(R.id.msv_voice_search_listening_real_time_text);

        mVoiceButton.setVisibility(mVoiceFeaturesEnabled && !mHideMicIcon ? View.VISIBLE : View.GONE);

        mSuggestionsRecyclerView = findViewById(R.id.msv_search_card_suggestions_recycler_view);
        mSuggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mSuggestionsAdapter = new SuggestionsAdapter();
        mSuggestionsRecyclerView.setAdapter(mSuggestionsAdapter);

        setDefaultAttributes();

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

        mVoiceButton.setOnClickListener(new OnClickListener() {
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
            }

            @Override
            public void afterTextChanged(final Editable editable) {
                String currentText = editable.toString();
                if (currentText.length() > 0) {
                    mHandler.removeCallbacks(getOrCreateEmptyTextRunnable());
                    if (mCurrentState != STATE_FOCUSED_TEXT_PRESENT_DELAY_SEARCH) {
                        moveToState(STATE_FOCUSED_TEXT_PRESENT_DELAY_SEARCH);
                    } else {
                        timedFilterSearchSuggestions(currentText);
                    }
                } else {
                    mHandler.postDelayed(getOrCreateEmptyTextRunnable(), CLEAR_TEXT_DELAY_TIME);
                }
            }
        };

        mSearchInputEditText.removeTextChangedListener(mSearchTextChangedListener);
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

    private Runnable getOrCreateEmptyTextRunnable() {
        if (mClearTextRunnable == null) {
            mClearTextRunnable = new Runnable() {

                @Override
                public void run() {
                    if (getSearchText().length() == 0) {
                        moveToState(STATE_FOCUSED_EMPTY);
                    }
                }
            };
        }
        return mClearTextRunnable;
    }

    private void setDefaultAttributes() {
        if (mSearchInputEditText != null) {
            if (!TextUtils.isEmpty(mHintText)) {
                mSearchInputEditText.setHint(mHintText);
            }
            if (!TextUtils.isEmpty(mText)) {
                mSearchInputEditText.setText(mText);
            }
            if (mHintTextColor != null) {
                mSearchInputEditText.setHintTextColor(mHintTextColor);
            }
            if (mTextColor != null) {
                mSearchInputEditText.setTextColor(mTextColor);
            }
        }

        if (mSearchIcon != null && mMainIcon != null) {
            mSearchIcon.setImageDrawable(mMainIcon);
        }

        if (mVoiceButton != null && mVoiceIcon != null) {
            mVoiceButton.setImageDrawable(mVoiceIcon);
        }

        if (mClearButton != null && mClearIcon != null) {
            mClearButton.setImageDrawable(mClearIcon);
        }

        if (mSearchCard != null && mBackground != null) {
            mSearchCard.setBackground(mBackground);
        }

        if (mSearchCard != null && mBackgroundColor != null) {
            mSearchCard.setBackgroundColor(mBackgroundColor);
        }

        setIconTintWithResolvedColor(mIconTintColor);
    }

    ///////////////////Begin API///////////////////

    /**
     * Get the card view that surrounds the other views.
     *
     * @return The CardView.
     */
    @SuppressWarnings("unused")
    @Nullable
    public CardView getCardView() {
        return mSearchCard;
    }

    /**
     * Get the underlying search input edit text view.
     *
     * @return The search input EditText view.
     */
    @SuppressWarnings("unused")
    @Nullable
    public EditText getSearchInputEditText() {
        return mSearchInputEditText;
    }

    /**
     * Get the voice image button.
     *
     * @return The image button.
     */
    @Nullable
    @SuppressWarnings("unused")
    public ImageButton getVoiceImageButton() {
        return mVoiceButton;
    }

    /**
     * Get the clear text image button.
     *
     * @return The image button.
     */
    @SuppressWarnings("unused")
    @Nullable
    public ImageButton getClearButton() {
        return mClearButton;
    }

    @Nullable
    @SuppressWarnings("unused")
    public ImageView getMainIcon() {
        return mSearchIcon;
    }

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
            if (mContext != null) {
                Context context = mContext.get();
                if (context != null) {
                    SearchDatabase.init(context);
                }
            }
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
        mMaxSuggestionsPersisted = limit;
    }

    /**
     * Set the search term for the MaterialSearchView.
     *
     * @param searchTerm
     *         The search term to be set for the view. Can be set to null to clear the view.
     */
    @SuppressWarnings("unused")
    public void setSearchTerm(@Nullable CharSequence searchTerm) {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.setText(searchTerm);
            hideMic();
        } else {
            showMic();
        }
    }

    /**
     * Set the hint text.
     *
     * @param text
     *         The text.
     */
    @SuppressWarnings("unused")
    public void setHintText(@Nullable CharSequence text) {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.setHint(text);
        }
    }

    /**
     * Set the hint text with a String resource id.
     *
     * @param stringResId
     *         The string resource id.
     */
    @SuppressWarnings("unused")
    public void setHintText(int stringResId) {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.setHint(stringResId);
        }
    }

    /**
     * Get the current hint text as a String.
     *
     * @return The hint text.
     */
    @SuppressWarnings("unused")
    @Nullable
    public String getHintTextString() {
        CharSequence charSequence = getHintText();
        return charSequence != null ? charSequence.toString() : null;
    }

    /**
     * Get the hint text as a CharSequence.
     *
     * @return The hint text.
     */
    @SuppressWarnings("unused")
    @Nullable
    public CharSequence getHintText() {
        if (mSearchInputEditText != null) {
            return mSearchInputEditText.getHint();
        }
        return null;
    }

    /**
     * Set the hint text color.
     *
     * @param colorResId
     *         The color resource id.
     */
    @SuppressWarnings("unused")
    public void setHintTextColor(int colorResId) {
        if (mSearchInputEditText != null) {
            if (mContext != null) {
                Context context = mContext.get();
                if (context != null) {
                    int color = ContextCompat.getColor(context, colorResId);
                    mSearchInputEditText.setHintTextColor(color);
                }
            }
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
        if (mContext != null) {
            Context context = mContext.get();
            if (context != null) {
                SearchDatabase.init(context);
            }
        }
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
        if (mContext != null) {
            Context context = mContext.get();
            if (context != null) {
                SearchDatabase.init(context);
            }
        }
    }

    /**
     * Sets the number of suggestions displayed to the user.
     *
     * @param limit
     *         The limit of suggestions. Default is 5.
     */
    @SuppressWarnings("unused")
    public void setMaxSuggestionsShown(int limit) {
        mMaxSuggestionsShown = limit;
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
     * Sets the icon for the voice feature.
     *
     * @param drawableResId
     *         The drawable resource id for the icon.
     */
    @SuppressWarnings("unused")
    public void setVoiceIcon(int drawableResId) {
        if (mVoiceButton != null) {
            mVoiceButton.setImageResource(drawableResId);
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
        if (mVoiceButton != null) {
            mVoiceButton.setImageDrawable(drawable);
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
        if (mVoiceButton != null) {
            mVoiceButton.setImageBitmap(bitmap);
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
        if (mVoiceButton != null) {
            mVoiceButton.setImageURI(imageUri);
        }
    }

    /**
     * Hides the voice icon until showVoiceIcon() is called.
     */
    @SuppressWarnings("unused")
    public void hideVoiceIcon() {
        if (mVoiceButton != null) {
            mVoiceButton.setVisibility(View.GONE);
            mHideMicIcon = true;
        }
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
     * Sets the text color of the MaterialSearchView.
     *
     * @param colorResId
     *         The color for the text.
     */
    @SuppressWarnings("unused")
    public void setTextColor(int colorResId) {
        if (mContext != null) {
            Context context = mContext.get();
            if (context != null && mSearchInputEditText != null) {
                int resolvedColor = ContextCompat.getColor(context, colorResId);
                mSearchInputEditText.setTextColor(resolvedColor);
            }
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
        if (mContext != null) {
            Context context = mContext.get();
            if (context != null) {
                int resolvedColor = ContextCompat.getColor(context, colorResId);
                setIconTintWithResolvedColor(resolvedColor);
            }
        }
    }

    /**
     * Sets the color of the existing icon set with a resolved color.
     *
     * @param resolvedColor
     *         The resolved color for the icons.
     */
    public void setIconTintWithResolvedColor(int resolvedColor) {
        if (mSearchIcon != null) {
            DrawableCompat.setTint(mSearchIcon.getDrawable(), resolvedColor);
        }
        if (mVoiceButton != null) {
            DrawableCompat.setTint(mVoiceButton.getDrawable(), resolvedColor);
        }
        if (mClearButton != null) {
            DrawableCompat.setTint(mClearButton.getDrawable(), resolvedColor);
        }
        mIconTintColor = resolvedColor;
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
    public void persistSuggestions(@NonNull CharSequence[] suggestions) {
        if (mDatabaseSuggestionsEnabled) {
            ArrayList<SearchSuggestion> searchSuggestions = new ArrayList<>(suggestions.length);
            for (int i = 0; i < suggestions.length; i++) {
                String currentTime = String.valueOf(System.currentTimeMillis() - 1000 * i);
                searchSuggestions.add(new SearchSuggestion(suggestions[i].toString(), currentTime));
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
    public void persistSuggestions(@NonNull CharSequence[] suggestions, @Nullable DatabaseTaskListener listener) {
        if (mDatabaseSuggestionsEnabled) {
            ArrayList<SearchSuggestion> searchSuggestions = new ArrayList<>(suggestions.length);
            for (int i = 0; i < suggestions.length; i++) {
                String currentTime = String.valueOf(System.currentTimeMillis() - 1000 * i);
                searchSuggestions.add(new SearchSuggestion(suggestions[i].toString(), currentTime));
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
    public void setSuggestions(@Nullable CharSequence[] suggestions) {
        ArrayList<SearchSuggestion> searchSuggestions = new ArrayList<>();
        if (suggestions != null) {
            searchSuggestions = new ArrayList<>(suggestions.length);
            for (int i = 0; i < suggestions.length; i++) {
                String currentTime = String.valueOf(System.currentTimeMillis() - 1000 * i);
                searchSuggestions.add(new SearchSuggestion(suggestions[i].toString(), currentTime));
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
     *
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
     *
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
        mVoiceFeaturesEnabled = true;
        showMic();
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

    /**
     * Closes the MaterialSearchView.
     */
    @SuppressWarnings("unused")
    public void close() {
        moveToState(STATE_DEFAULT);
    }

    /**
     * Indicates whether the MaterialSearchView is open.
     *
     * @return True if open.
     */
    @SuppressWarnings("unused")
    public boolean isOpen() {
        return mCurrentState == STATE_DEFAULT;
    }

    ///////////////////End API///////////////////

    private void moveCursorToEnd() {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.setSelection(getSearchText().length());
        }
    }

    private void startVoiceSearch() {
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            if (mContext != null) {
                Context context = mContext.get();
                if (context != null) {
                    int permissionStatus = ContextCompat.checkSelfPermission(context, permission.RECORD_AUDIO);
                    if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                        performVoiceSearch();
                    } else {
                        for (SearchViewVoiceListener listener : mVoiceListeners) {
                            listener.onVoiceSearchError(VOICE_SEARCH_ERROR_PERMISSION_NEEDED);
                        }
                    }
                    return;
                }
            }
        } else {
            performVoiceSearch();
            return;
        }
        for (SearchViewVoiceListener listener : mVoiceListeners) {
            listener.onVoiceSearchError(VOICE_SEARCH_ERROR_MISSING_CONTEXT);
        }
    }

    private void performVoiceSearch() {
        if (mContext != null) {
            Context context = mContext.get();
            if (context != null) {
                if (mSpeechRecognizer == null) {
                    if (SpeechRecognizer.isRecognitionAvailable(context)) {
                        moveToState(STATE_VOICE_LISTENING);
                        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                        mSpeechRecognizer.setRecognitionListener(this);
                        mSpeechRecognizer.startListening(createSpeechRecognizerIntent(context));
                        return;
                    }
                    for (SearchViewVoiceListener listener : mVoiceListeners) {
                        listener.onVoiceSearchError(VOICE_SEARCH_ERROR_UNAVAILABLE);
                    }
                } else {
                    moveToState(STATE_VOICE_LISTENING);
                    mSpeechRecognizer.setRecognitionListener(this);
                    mSpeechRecognizer.startListening(createSpeechRecognizerIntent(context));
                }
                return;
            }
        }
        for (SearchViewVoiceListener listener : mVoiceListeners) {
            listener.onVoiceSearchError(VOICE_SEARCH_ERROR_MISSING_CONTEXT);
        }
    }

    private Intent createSpeechRecognizerIntent(@NonNull Context context) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                context.getString(R.string.speech_prompt));
        return intent;
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

    private void showClear() {
        if (mClearButton != null) {
            mClearButton.setVisibility(mHideClearIcon ? View.GONE : View.VISIBLE);
        }
    }

    private void showMic() {
        if (mVoiceButton != null && mVoiceFeaturesEnabled) {
            mVoiceButton.setVisibility(mHideMicIcon ? View.GONE : View.VISIBLE);
        }
    }

    private void hideClear() {
        if (mClearButton != null) {
            mClearButton.setVisibility(View.GONE);
        }
    }

    private void hideMic() {
        if (mVoiceButton != null) {
            mVoiceButton.setVisibility(View.GONE);
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
            Context context = mContext.get();
            if (context != null) {
                InputMethodManager inputManager = (InputMethodManager)
                        context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputManager != null) {
                    inputManager.hideSoftInputFromWindow(mSearchInputEditText.getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        }
    }

    private void openKeyboard() {
        if (mContext != null && mSearchInputEditText != null) {
            Context context = mContext.get();
            if (context != null) {
                InputMethodManager inputManager = (InputMethodManager)
                        context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputManager != null) {
                    inputManager.showSoftInput(mSearchInputEditText, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }
    }

    private void showRecentSearches() {
        if (mDatabaseSuggestionsEnabled) {
            int limit = Math.min(mMaxSuggestionsShown, mMaxSuggestionsPersisted);
            mRecentSearchesTask = SearchDatabase.getRecentSearches(limit, MaterialSearchView.this);
        }
    }

    private void filterSearchSuggestions(@NonNull String searchTerm) {
        if (mDatabaseSuggestionsEnabled) {
            int limit = Math.min(mMaxSuggestionsShown, mMaxSuggestionsPersisted);
            mFilterSearchTask = SearchDatabase.filterSearchesBy(limit, searchTerm, MaterialSearchView.this);
        }
    }

    private void clearSearchViewFocus() {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.clearFocus();
        }
    }

    private void requestSearchViewFocus() {
        if (mSearchInputEditText != null) {
            if (!mSearchInputEditText.hasFocus()) {
                mSearchInputEditText.requestFocus();
                openKeyboard();
            }
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
            mHandler.removeCallbacks(mFilterRunnable);
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

    private void showListeningView() {
        if (mVoiceSearchCard != null) {
            mVoiceSearchCard.setVisibility(View.VISIBLE);
        }
        if (mSearchCard != null) {
            mSearchCard.setVisibility(View.GONE);
        }
        startVoiceListeningAnimation();
    }

    private void showSearchView() {
        if (mSearchCard != null) {
            mSearchCard.setVisibility(View.VISIBLE);
        }
        if (mVoiceSearchCard != null) {
            mVoiceSearchCard.setVisibility(View.GONE);
        }
        stopVoiceListeningAnimation();
    }

    private void stopVoiceRecognitionIfNecessary() {
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.stopListening();
        }
    }

    private void startVoiceListeningAnimation() {
        if (mAnimatedVoiceListenerDrawable != null) {
            if (mAnimatedVoiceListenerDrawableWrapper == null) {
                mAnimatedVoiceListenerDrawableWrapper = new AnimatedVectorDrawableWrapper(
                        mAnimatedVoiceListenerDrawable,
                        mAnimatedVoiceListenerDuration);
            }
            mAnimatedVoiceListenerDrawableWrapper.repeat();
        }
    }

    private void stopVoiceListeningAnimation() {
        if (mAnimatedVoiceListenerDrawableWrapper != null) {
            mAnimatedVoiceListenerDrawableWrapper.stop();
        }
    }

    private void destroyVoiceListeningAnimation() {
        if (mAnimatedVoiceListenerDrawableWrapper != null) {
            mAnimatedVoiceListenerDrawableWrapper.destroy();
        }
    }

    private void moveToState(@MaterialSearchViewState int state) {
        Log.d(TAG, "moveToState(" + state + ")");
        if (mCurrentState != state) {
            mCurrentState = state;
            Log.d(TAG, "newState = " + state);
            switch (state) {
                case STATE_FOCUSED_EMPTY:
                    stopVoiceRecognitionIfNecessary();
                    showSearchView();
                    cancelSuggestions();
                    emptySearchView();
                    setKeyboardActionListener();
                    showRecentSearches();
                    showMic();
                    hideClear();
                    requestSearchViewFocus();
                    break;
                case STATE_FOCUSED_TEXT_PRESENT:
                    stopVoiceRecognitionIfNecessary();
                    showSearchView();
                    filterSearchSuggestions(getSearchText());
                    setKeyboardActionListener();
                    hideMic();
                    showClear();
                    requestSearchViewFocus();
                    break;
                case STATE_UNFOCUSED_TEXT_PRESENT:
                    stopVoiceRecognitionIfNecessary();
                    showSearchView();
                    cancelSuggestions();
                    clearSuggestions();
                    closeKeyboard();
                    clearKeyboardActionListener();
                    hideMic();
                    showClear();
                    break;
                case STATE_FOCUSED_TEXT_PRESENT_DELAY_SEARCH:
                    stopVoiceRecognitionIfNecessary();
                    showSearchView();
                    setKeyboardActionListener();
                    hideMic();
                    showClear();
                    requestSearchViewFocus();
                    break;
                case STATE_VOICE_LISTENING:
                    cancelSuggestions();
                    clearSuggestions();
                    showListeningView();
                    break;
                case STATE_DEFAULT:
                default:
                    stopVoiceRecognitionIfNecessary();
                    showSearchView();
                    cancelSuggestions();
                    clearKeyboardActionListener();
                    closeKeyboard();
                    emptySearchView();
                    clearSearchViewFocus();
                    clearSuggestions();
                    showMic();
                    hideClear();
                    break;
            }
        } else {
            Log.d(TAG, "moveToState - Already Current State");
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.d(TAG, ".onAttachedToWindow()");
        restoreContextIfNeeded();
        if (mDatabaseSuggestionsEnabled) {
            if (mContext != null) {
                Context context = mContext.get();
                if (context != null) {
                    SearchDatabase.init(context);
                }
            }
        }
    }

    private void restoreContextIfNeeded() {
        if ((mContext == null || mContext.get() == null) && mSearchCard != null) {
            mContext = new WeakReference<>(mSearchCard.getContext());
            Log.d(TAG, "CONTEXT RESTORED");
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
        destroyVoiceListeningAnimation();
        mSearchListeners = null;
        mInteractionListeners = null;
        mVoiceListeners = null;
        mEventListeners = null;
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.stopListening();
            mSpeechRecognizer.setRecognitionListener(null);
        }
    }

    @Override
    public void onReadyForSpeech(Bundle bundle) {
        Log.d(TAG, "onReadyForSpeech()");
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech()");
        mVoiceResultsAvailable = false;
    }

    @Override
    public void onRmsChanged(float v) {

    }

    @Override
    public void onBufferReceived(byte[] bytes) {

    }

    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech()");
        if (!mVoiceResultsAvailable) {
            for (SearchViewVoiceListener listener : mVoiceListeners) {
                listener.onVoiceSearchError(VOICE_SEARCH_ERROR_NO_RESULTS);
            }
            moveToState(STATE_DEFAULT);
        }
    }

    @Override
    public void onError(int error) {
        Log.d(TAG, "onError(" + error + ")");
        for (SearchViewVoiceListener listener : mVoiceListeners) {
            listener.onVoiceSearchError(error);
        }
        if (!mVoiceResultsAvailable) {
            moveToState(STATE_DEFAULT);
        }
    }

    @Override
    public void onResults(Bundle bundle) {
        Log.d(TAG, "onResults(" + (bundle != null ? bundle.toString() : "null") + ")");
        if (bundle != null) {
            mVoiceResultsAvailable = true;
            ArrayList<String> voiceSearchResults = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (voiceSearchResults != null && voiceSearchResults.size() > 0) {
                search(voiceSearchResults.get(0));
            }
        }
    }

    @Override
    public void onPartialResults(Bundle bundle) {
        Log.d(TAG, "onPartialResults(" + (bundle != null ? bundle.toString() : "null") + ")");
        if (bundle != null) {
            ArrayList<String> voiceSearchResults = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (voiceSearchResults != null
                    && voiceSearchResults.size() > 0
                    && mVoiceSearchListeningRealTimeText != null) {
                mVoiceSearchListeningRealTimeText.setVisibility(View.VISIBLE);
                if (mVoiceSearchListeningPrompt != null) {
                    mVoiceSearchListeningPrompt.setVisibility(View.GONE);
                }
                CharSequence realTimeText = mVoiceSearchListeningRealTimeText.getText();
                String realTimeTextString = (realTimeText != null ? realTimeText : "")
                        + voiceSearchResults.get(0);
                mVoiceSearchListeningRealTimeText.setText(realTimeTextString);
            }
        }
    }

    @Override
    public void onEvent(int i, Bundle bundle) {
        Log.d(TAG, "onEvent(" + i + ", " + (bundle != null ? bundle.toString() : null) + ")");
    }

    public void handlePermissionResult(int requestCode,
                                       @Nullable String[] permissions,
                                       int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION_RECORD_AUDIO
                && grantResults.length > 0 && permissions != null && permissions.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && permission.RECORD_AUDIO.equals(permissions[0])) {
                startVoiceSearch();
            } else {
                for (SearchViewVoiceListener listener : mVoiceListeners) {
                    listener.onVoiceSearchError(VOICE_SEARCH_ERROR_PERMISSION_DENIED);
                }
            }
        }
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
            DrawableCompat.setTint(mSuggestionHistoryIcon.getDrawable(), mIconTintColor);
            DrawableCompat.setTint(mSuggestionPointIcon.getDrawable(), mIconTintColor);
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

    private static class AnimatedVectorDrawableWrapper {

        private Handler mHandler = new Handler();
        private Animatable mDrawable;
        private final long mDuration;
        private Runnable mRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (mDrawable != null && mHandler != null) {
                    mDrawable.start();
                    mHandler.postDelayed(this, mDuration);
                }
            }
        };

        private AnimatedVectorDrawableWrapper(@NonNull Animatable drawable, long duration) {
            mDrawable = drawable;
            mDuration = duration;
        }

        private void repeat() {
            if (mDrawable != null && mRepeatRunnable != null && mHandler != null) {
                mDrawable.start();
                mHandler.postDelayed(mRepeatRunnable, mDuration);
            }
        }

        private void stop() {
            if (mDrawable != null && mRepeatRunnable != null && mHandler != null) {
                mDrawable.stop();
                mHandler.removeCallbacks(mRepeatRunnable);
            }
        }

        private void destroy() {
            if (mDrawable != null && mRepeatRunnable != null && mHandler != null) {
                mDrawable.stop();
                mHandler.removeCallbacks(mRepeatRunnable);
            }
            mDrawable = null;
            mRepeatRunnable = null;
            mHandler = null;
        }
    }

    @Retention(SOURCE)
    @IntDef({STATE_DEFAULT,
            STATE_FOCUSED_EMPTY,
            STATE_FOCUSED_TEXT_PRESENT,
            STATE_FOCUSED_TEXT_PRESENT_DELAY_SEARCH,
            STATE_UNFOCUSED_TEXT_PRESENT,
            STATE_VOICE_LISTENING})
    private @interface MaterialSearchViewState {}

    public static final int STATE_DEFAULT = 0;
    public static final int STATE_FOCUSED_EMPTY = 1;
    public static final int STATE_FOCUSED_TEXT_PRESENT = 2;
    public static final int STATE_FOCUSED_TEXT_PRESENT_DELAY_SEARCH = 3;
    public static final int STATE_UNFOCUSED_TEXT_PRESENT = 4;
    public static final int STATE_VOICE_LISTENING = 5;

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
        /**
         * Speech RecognitionListener Error Codes
         * int 1 - ERROR_NETWORK_TIMEOUT - Network operation timed out.
         * int 2 - ERROR_NETWORK - Other network related errors.
         * int 3 - ERROR_AUDIO - Audio recording error.
         * int 4 - ERROR_SERVER - Server sends error status.
         * int 5 - ERROR_CLIENT - Other client side errors.
         * int 6 - ERROR_SPEECH_TIMEOUT - No speech input
         * int 7 - ERROR_NO_MATCH - No recognition result matched.
         * int 8 - ERROR_RECOGNIZER_BUSY - RecognitionService busy.
         * int 9 - ERROR_INSUFFICIENT_PERMISSIONS - Insufficient permissions
         **/
        int VOICE_SEARCH_ERROR_UNAVAILABLE = 100;
        int VOICE_SEARCH_ERROR_MISSING_CONTEXT = 110;
        int VOICE_SEARCH_ERROR_PERMISSION_NEEDED = 120;
        int VOICE_SEARCH_ERROR_PERMISSION_DENIED = 130;
        int VOICE_SEARCH_ERROR_NO_RESULTS = 140;

        void onVoiceSearchError(int error);

        void onVoiceSearch(@NonNull String searchTerm);
    }

    public interface SearchViewSearchListener {
        void onSearch(@NonNull String searchTerm);
    }
}
