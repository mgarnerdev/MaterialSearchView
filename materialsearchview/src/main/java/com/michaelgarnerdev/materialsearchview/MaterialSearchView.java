package com.michaelgarnerdev.materialsearchview;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.michaelgarnerdev.materialsearchview.SearchDatabase.DatabaseReadSearchesListener;
import com.michaelgarnerdev.materialsearchview.SearchDatabase.GetPerformedSearchesStartingWithTask;
import com.michaelgarnerdev.materialsearchview.SearchDatabase.GetRecentSearchesTask;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by mgarnerdev on 7/29/2017.
 * MaterialSearchView helps you add material-themed searching to your app with ease.
 */

public class MaterialSearchView extends LinearLayout implements DatabaseReadSearchesListener, RecognitionListener {
    private static final String TAG = MaterialSearchView.class.getSimpleName();

    private static final long TIME_SEARCH_AFTER_KEY_PRESS_DELAY = 300;


    private WeakReference<Context> mContext;

    private EditText mSearchInputEditText;
    private ImageButton mMicButton;
    private ImageButton mCancelButton;

    private Handler mHandler = new Handler();

    private boolean mMicVisible = true;
    private GetPerformedSearchesStartingWithTask mFilterSearchTask;
    private GetRecentSearchesTask mRecentSearchesTask;
    private Runnable mFilterRunnable;
    private RecyclerView mSuggestionsRecyclerView;
    private SuggestionsAdapter mSuggestionsAdapter;
    private ArrayList<SearchViewListener> mListeners = new ArrayList<>();
    private float mSuggestionRowHeight = 0;
    private TextWatcher mSearchTextChangedListener;

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
    public MaterialSearchView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(@NonNull Context context) {
        mContext = new WeakReference<>(context);
        Resources resources = mContext.get().getResources();
        mSuggestionRowHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, resources.getDisplayMetrics());
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
        mSearchInputEditText = findViewById(R.id.msv_search_card_input_edit_text);
        mMicButton = findViewById(R.id.msv_search_card_input_microphone);
        mCancelButton = findViewById(R.id.msv_search_card_input_cancel);

        mSuggestionsRecyclerView = findViewById(R.id.msv_search_card_suggestions_recycler_view);
        mSuggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(mContext.get()));
        mSuggestionsAdapter = new SuggestionsAdapter();
        mSuggestionsRecyclerView.setAdapter(mSuggestionsAdapter);

        mCancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSearchInputEditText != null) {
                    resetSearch(true);
                    clearSuggestions();
                }
            }
        });

        mMicButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
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
                    if (mFilterRunnable != null) {
                        mHandler.removeCallbacks(mFilterRunnable);
                    }
                    mHandler.postDelayed(createFilterRunnable(charSequence.toString()), TIME_SEARCH_AFTER_KEY_PRESS_DELAY);
                    if (mMicVisible) {
                        hideMic();
                    }
                } else {
                    resetSearch(false);
                }
            }

            @Override
            public void afterTextChanged(final Editable editable) {

            }
        };

        mSearchInputEditText.addTextChangedListener(mSearchTextChangedListener);

        mSearchInputEditText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int imeActionId, KeyEvent keyEvent) {
                if (keyEvent != null && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || imeActionId == EditorInfo.IME_ACTION_DONE
                        || keyEvent.getKeyCode() == KeyEvent.KEYCODE_SEARCH)) {
                    return search(mSearchInputEditText.getText().toString());
                }
                return false;
            }
        });

        mSearchInputEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                if (focused) {
                    String currentText = mSearchInputEditText.getText().toString();
                    if (currentText.length() > 0) {
                        hideMic();
                        mFilterSearchTask = SearchDatabase.filterSearchesBy(currentText, MaterialSearchView.this);
                    } else {
                        showMic();
                        mRecentSearchesTask = SearchDatabase.getRecentSearches(5, MaterialSearchView.this);
                    }
                }
            }
        });
    }

    private void startVoiceSearch() {
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            int permissionStatus = ContextCompat.checkSelfPermission(mContext.get(), permission.RECORD_AUDIO);
            if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                performVoiceSearch();
            } else {

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
        } else {

        }
    }

    private void clearSuggestions() {
        if (mSearchInputEditText.hasFocus()) {
            mRecentSearchesTask = SearchDatabase.getRecentSearches(5, MaterialSearchView.this);
        } else {
            if (mSuggestionsAdapter != null) {
                mSuggestionsAdapter.setSuggestions(new ArrayList<PerformedSearch>());
                adjustSuggestionsBoxHeight(0);
            }
        }

    }

    private void adjustSuggestionsBoxHeight(int numberOfRows) {
        if (mSuggestionsRecyclerView != null && mSuggestionRowHeight > 0) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mSuggestionsRecyclerView.getLayoutParams();
            params.height = (int) (numberOfRows * mSuggestionRowHeight);
            mSuggestionsRecyclerView.setLayoutParams(params);
        }
    }

    private void resetSearch(boolean emptyText) {
        if (emptyText) {
            mSearchInputEditText.setText("");
        }
        showMic();
    }

    private boolean search(@NonNull String searchTerm) {
        if (mSearchInputEditText != null) {
            mSearchInputEditText.clearFocus();
            mSearchInputEditText.removeTextChangedListener(mSearchTextChangedListener);
            mSearchInputEditText.setText(searchTerm);
            mSearchInputEditText.addTextChangedListener(mSearchTextChangedListener);
        }
        closeKeyboard();
        if (!TextUtils.isEmpty(searchTerm)) {
            SearchDatabase.addPerformedSearch(null, new PerformedSearch(searchTerm, String.valueOf(System.currentTimeMillis())));
            if (mListeners != null) {
                for (SearchViewListener listener : mListeners) {
                    listener.onSearch(searchTerm);
                }
                showMic();
            }
            return true;
        } else {
            return false;
        }
    }

    private void showMic() {
        mMicButton.setVisibility(View.VISIBLE);
        mCancelButton.setVisibility(View.GONE);
        mMicVisible = true;
    }

    private void hideMic() {
        mMicButton.setVisibility(View.GONE);
        mCancelButton.setVisibility(View.VISIBLE);
        mMicVisible = false;
    }

    private void closeKeyboard() {
        if (mContext != null && mSearchInputEditText != null) {
            clearSuggestions();
            InputMethodManager inputManager = (InputMethodManager)
                    mContext.get().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(mSearchInputEditText.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public void addListener(@NonNull SearchViewListener listener) {
        if (mListeners != null) {
            mListeners.add(listener);
        }
    }

    public void removeListener(@NonNull SearchViewListener listener) {
        if (mListeners != null) {
            mListeners.remove(listener);
        }
    }

    public void clearListeners() {
        if (mListeners != null) {
            mListeners.clear();
        }
    }

    private Runnable createFilterRunnable(@NonNull final String searchTerm) {
        mFilterRunnable = new Runnable() {
            @Override
            public void run() {
                if (mFilterSearchTask != null) {
                    mFilterSearchTask.cancel();
                }
                mFilterSearchTask = SearchDatabase.filterSearchesBy(searchTerm, MaterialSearchView.this);
            }
        };
        return mFilterRunnable;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.d(TAG, ".onAttachedToWindow()");
        SearchDatabase.init(mContext.get());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.d(TAG, ".onDetachedFromWindow()");
        SearchDatabase.destroy();
        mContext = null;
        if (mFilterSearchTask != null) {
            mFilterSearchTask.cancel();
        }
        if (mRecentSearchesTask != null) {
            mRecentSearchesTask.cancel();
        }
        if (mFilterRunnable != null) {
            mHandler.removeCallbacks(mFilterRunnable);
        }
        if (mListeners != null) {
            mListeners.clear();
            mListeners = null;
        }
    }

    @Override
    public void onComplete(@NonNull ArrayList<PerformedSearch> searches) {
        if (mSuggestionsAdapter != null) {
            mSuggestionsAdapter.setSuggestions(searches);
            //expandSuggestions(searches.size());
            adjustSuggestionsBoxHeight(searches.size());
        }
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

    public class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionViewHolder> {

        @NonNull
        private ArrayList<PerformedSearch> mSuggestions = new ArrayList<>();

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

        public void setSuggestions(@NonNull ArrayList<PerformedSearch> suggestions) {
            mSuggestions = suggestions;
            notifyDataSetChanged();
        }
    }

    public class SuggestionViewHolder extends ViewHolder {
        private final View mRootView;
        private TextView mSuggestionTextView;
        private View mDivider;

        private OnClickListener mOnClickListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSuggestionTextView != null) {
                    search(mSuggestionTextView.getText().toString());
                }
            }
        };

        public SuggestionViewHolder(View itemView) {
            super(itemView);
            mRootView = itemView;
            mSuggestionTextView = itemView.findViewById(R.id.list_item_suggestion);
            mDivider = itemView.findViewById(R.id.list_item_divider_line);
        }

        public void bind(@NonNull String suggestion) {
            mSuggestionTextView.setText(suggestion);
            mDivider.setVisibility(View.VISIBLE);
            mRootView.setOnClickListener(mOnClickListener);
        }

        public void hideDivider() {
            mDivider.setVisibility(View.GONE);
        }
    }

    public interface SearchViewListener {
        void onSearch(@NonNull String searchTerm);

        void onVoiceSearchFailed(int error);

        void onVoiceSearchPermissionNeeded();

        void onVoiceSearchIncompatible();
    }
}
