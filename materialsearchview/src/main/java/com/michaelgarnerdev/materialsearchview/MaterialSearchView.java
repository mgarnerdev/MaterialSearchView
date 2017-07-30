package com.michaelgarnerdev.materialsearchview;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.michaelgarnerdev.materialsearchview.SearchDatabase.DatabaseReadSearchesListener;
import com.michaelgarnerdev.materialsearchview.SearchDatabase.DatabaseTask;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Created by mgarnerdev on 7/29/2017.
 * MaterialSearchView helps you add material-themed searching to your app with ease.
 */

public class MaterialSearchView extends LinearLayout implements DatabaseReadSearchesListener {
    private static final String TAG = MaterialSearchView.class.getSimpleName();

    private static final long TIME_SEARCH_AFTER_KEY_PRESS_DELAY = 200;


    private WeakReference<Context> mContext;

    private EditText mSearchInputEditText;
    private ImageButton mMicButton;
    private ImageButton mCancelButton;

    private Handler mHandler = new Handler();

    private boolean mMicVisible = true;
    private DatabaseTask mFilterSearchTask;
    private DatabaseTask mRecentSearchesTask;
    private Runnable mFilterRunnable;
    private RecyclerView mSuggestionsRecyclerView;
    private SuggestionsAdapter mSuggestionsAdapter;
    private ArrayList<SearchViewListener> mListeners = new ArrayList<>();

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
                }
            }
        });

        mSearchInputEditText.addTextChangedListener(new TextWatcher() {
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
                        mMicVisible = false;
                        mMicButton.setVisibility(View.GONE);
                        mCancelButton.setVisibility(View.VISIBLE);
                    }
                } else {
                    resetSearch(false);
                }
            }

            @Override
            public void afterTextChanged(final Editable editable) {

            }
        });

        mSearchInputEditText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int imeActionId, KeyEvent keyEvent) {
                if (keyEvent != null && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || imeActionId == EditorInfo.IME_ACTION_DONE
                        || keyEvent.getKeyCode() == KeyEvent.KEYCODE_SEARCH)) {
                    closeKeyboard();
                    String searchTerm = mSearchInputEditText.getText() != null ? mSearchInputEditText.getText().toString() : "";
                    if (!TextUtils.isEmpty(searchTerm)) {
                        SearchDatabase.addPerformedSearch(null, new PerformedSearch(searchTerm, String.valueOf(System.currentTimeMillis())));
                        if (mListeners != null) {
                            for (SearchViewListener listener : mListeners) {
                                listener.onSearch(searchTerm);
                            }
                            resetSearch(true);
                        }
                        return true;
                    } else {
                        return false;
                    }
                }
                return false;
            }
        });
    }

    private void resetSearch(boolean emptyText) {
        if (emptyText) {
            mSearchInputEditText.setText("");
        }
        mMicButton.setVisibility(View.VISIBLE);
        mCancelButton.setVisibility(View.GONE);
        mMicVisible = true;
    }

    private void closeKeyboard() {
        if (mContext != null && mSearchInputEditText != null) {
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
        mRecentSearchesTask = SearchDatabase.getRecentSearches(5, MaterialSearchView.this);
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
        if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
            if (!isAttachedToWindow()) {
                if (mSuggestionsAdapter != null) {
                    mSuggestionsAdapter.setSuggestions(searches);
                }
            }
        }
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
        private TextView mSuggestionTextView;

        public SuggestionViewHolder(View itemView) {
            super(itemView);
            mSuggestionTextView = itemView.findViewById(R.id.list_item_suggestion);
        }

        public void bind(@NonNull String suggestion) {
            mSuggestionTextView.setText(suggestion);
        }
    }

    public interface SearchViewListener {
        void onSearch(@NonNull String searchTerm);
    }
}
