package com.michaelgarnerdev.materialsearchview;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;

import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.michaelgarnerdev.materialsearchview.SearchDatabase.SearchEntry.COLUMN_NAME_ID;
import static com.michaelgarnerdev.materialsearchview.SearchDatabase.SearchEntry.COLUMN_NAME_SEARCH_DATE;
import static com.michaelgarnerdev.materialsearchview.SearchDatabase.SearchEntry.COLUMN_NAME_SEARCH_TERM;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Created by mgarner on 7/29/2017.
 * Database for searches.
 */

class SearchDatabase extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "msv_searches.db";
    private static final String TEXT_TYPE = " TEXT";
    private static final String REAL_TYPE = " REAL";
    private static final String COMMA_SEP = ", ";
    private static final String SEARCHES_TABLE_NAME = "searches";

    private static final int DEFAULT_LIMIT = 5;

    private static SearchDatabase sInstance;
    private static SQLiteDatabase sWritableDatabase;
    private static SQLiteDatabase sReadableDatabase;

    static synchronized void init(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new SearchDatabase(context.getApplicationContext());
        }
    }

    private SearchDatabase(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    private static synchronized SearchDatabase get() {
        if (sInstance != null) {
            return sInstance;
        } else {
            throw new RuntimeException("Cannot call get if Database is not initialized.");
        }
    }

    static void destroy() {
        sInstance = null;
        if (sWritableDatabase != null) {
            sWritableDatabase.close();
            sWritableDatabase = null;
        }
        if (sReadableDatabase != null) {
            sReadableDatabase.close();
            sReadableDatabase = null;
        }
    }

    @Retention(SOURCE)
    @StringDef({COLUMN_NAME_ID, COLUMN_NAME_SEARCH_TERM, COLUMN_NAME_SEARCH_DATE})
    @interface SearchEntry {
        String COLUMN_NAME_ID = "_id";
        String COLUMN_NAME_SEARCH_TERM = "search_term";
        String COLUMN_NAME_SEARCH_DATE = "date_searched";
    }

    private static String[] sSearchesTableAllColumns = {
            COLUMN_NAME_ID,
            COLUMN_NAME_SEARCH_TERM,
            COLUMN_NAME_SEARCH_DATE
    };

    private static final String SQL_CREATE_SEARCHES_TABLE =
            "CREATE TABLE " + SEARCHES_TABLE_NAME + " (" +
                    COLUMN_NAME_ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_SEARCH_TERM + TEXT_TYPE + COMMA_SEP +
                    COLUMN_NAME_SEARCH_DATE + REAL_TYPE + COMMA_SEP +
                    " UNIQUE(" + COLUMN_NAME_SEARCH_TERM + ")" +
                    ")";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + SEARCHES_TABLE_NAME;

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_SEARCHES_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    private static SQLiteDatabase editDatabase() {
        if (sWritableDatabase == null) {
            sWritableDatabase = get().getWritableDatabase();
        }
        return sWritableDatabase;
    }

    private static SQLiteDatabase readDatabase() {
        if (sReadableDatabase == null) {
            sReadableDatabase = get().getReadableDatabase();
        }
        return sReadableDatabase;
    }

    static void addPerformedSearch(@Nullable DatabaseTaskListener listener,
                                   @NonNull SearchSuggestion... searchSuggestions) {
        new AddPerformedSearchTask(listener).execute(searchSuggestions);
    }

    static void addPerformedSearches(@NonNull ArrayList<SearchSuggestion> searchSuggestions,
                                     @Nullable DatabaseTaskListener listener) {
        new AddPerformedSearchesTask(searchSuggestions, listener).execute();
    }

    private static boolean addPerformedSearch(@NonNull SQLiteDatabase database,
                                              @NonNull SearchSuggestion searchSuggestion) {
        String insertQuery = "INSERT OR IGNORE INTO " +
                SEARCHES_TABLE_NAME + "(" +
                COLUMN_NAME_SEARCH_TERM + COMMA_SEP + COLUMN_NAME_SEARCH_DATE +
                ") VALUES(" + DatabaseUtils.sqlEscapeString(searchSuggestion.getSearchTerm()) + COMMA_SEP
                + DatabaseUtils.sqlEscapeString(searchSuggestion.getDate()) + ")";
        return database.rawQuery(insertQuery, null).getCount() != 0;
    }

    static GetPerformedSearchesStartingWithTask filterSearchesBy(int limit, @NonNull String searchTerm,
                                                                 @NonNull DatabaseReadSearchesListener listener) {
        GetPerformedSearchesStartingWithTask task = new GetPerformedSearchesStartingWithTask(searchTerm, limit, listener);
        task.execute();
        return task;
    }

    static GetPerformedSearchesTask getPerformedSearches(@NonNull DatabaseReadSearchesListener listener) {
        GetPerformedSearchesTask task = new GetPerformedSearchesTask(0, listener);
        task.execute();
        return task;
    }

    static GetPerformedSearchesTask getPerformedSearches(int limit, DatabaseReadSearchesListener listener) {
        GetPerformedSearchesTask task = new GetPerformedSearchesTask(limit, listener);
        task.execute();
        return task;
    }

    static GetRecentSearchesTask getRecentSearches(int limit, @NonNull DatabaseReadSearchesListener listener) {
        GetRecentSearchesTask task = new GetRecentSearchesTask(limit, listener);
        task.execute();
        return task;
    }

    private static SearchSuggestion cursorToPerformedSearch(Cursor cursor) {
        return new SearchSuggestion(cursor.getString(1), cursor.getString(2));
    }

    static void deleteDatabase(@Nullable DatabaseTaskListener listener) {
        new DeleteDatabaseTask(listener).execute();
    }

    @SuppressWarnings("unused")
    private static class AddPerformedSearchTask extends AsyncTask<SearchSuggestion, Void, Boolean> {
        private DatabaseTaskListener mListener = null;

        private AddPerformedSearchTask(@Nullable DatabaseTaskListener listener) {
            mListener = listener;
        }

        @Override
        protected final Boolean doInBackground(SearchSuggestion... searchSuggestions) {
            boolean success = false;
            if (searchSuggestions != null) {
                List<SearchSuggestion> searches = Arrays.asList(searchSuggestions);
                success = true;
                SQLiteDatabase database = editDatabase();
                database.beginTransaction();
                for (SearchSuggestion search : searches) {
                    if (!addPerformedSearch(database, search)) {
                        success = false;
                    }
                }
                if (database.inTransaction()) {
                    database.setTransactionSuccessful();
                    database.endTransaction();
                }
            }
            return success;
        }

        @Override
        protected void onPostExecute(Boolean successful) {
            super.onPostExecute(successful);
            if (mListener != null) {
                if (successful != null && successful) {
                    mListener.onDatabaseEditSuccess();
                } else {
                    mListener.onDatabaseEditFailure();
                }
            }
        }

        private void cancel() {
            mListener = null;
            cancel(true);
        }
    }

    @SuppressWarnings("unused")
    private static class AddPerformedSearchesTask extends AsyncTask<Void, Void, Boolean> {
        private ArrayList<SearchSuggestion> mSearchSuggestions;
        private DatabaseTaskListener mListener = null;

        private AddPerformedSearchesTask(@NonNull ArrayList<SearchSuggestion> searchSuggestions,
                                         @Nullable DatabaseTaskListener listener) {
            mSearchSuggestions = searchSuggestions;
            mListener = listener;
        }

        @Override
        protected final Boolean doInBackground(Void... voids) {
            boolean success = true;
            SQLiteDatabase database = editDatabase();
            if (database != null && database.isOpen()) {
                database.beginTransaction();
                for (SearchSuggestion search : mSearchSuggestions) {
                    if (!addPerformedSearch(database, search)) {
                        success = false;
                    }
                }
                if (database.inTransaction()) {
                    database.setTransactionSuccessful();
                    database.endTransaction();
                }
            }
            return success;
        }

        @Override
        protected void onPostExecute(Boolean successful) {
            super.onPostExecute(successful);
            if (mListener != null) {
                if (successful != null && successful) {
                    mListener.onDatabaseEditSuccess();
                } else {
                    mListener.onDatabaseEditFailure();
                }
            }
        }

        private void cancel() {
            mListener = null;
            cancel(true);
        }
    }

    @SuppressWarnings("unused")
    static class GetPerformedSearchesTask extends AsyncTask<Void, Void, ArrayList<SearchSuggestion>> {
        private int mRowLimit = 0;
        private DatabaseReadSearchesListener mListener = null;

        private GetPerformedSearchesTask(int limit, @Nullable DatabaseReadSearchesListener listener) {
            mRowLimit = limit;
            mListener = listener;
        }

        @Override
        protected final ArrayList<SearchSuggestion> doInBackground(Void... voids) {
            String limit = mRowLimit > 0 ? String.valueOf(mRowLimit) : null;
            ArrayList<SearchSuggestion> searchSuggestions = new ArrayList<>();
            SQLiteDatabase readableDatabase = readDatabase();
            if (readableDatabase != null && readableDatabase.isOpen()) {
                Cursor cursor = readableDatabase.query(true, SearchDatabase.SEARCHES_TABLE_NAME,
                        sSearchesTableAllColumns, null, null, null, null, COLUMN_NAME_SEARCH_DATE + " DESC", limit);
                if (cursor != null) {
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        SearchSuggestion searchSuggestion = cursorToPerformedSearch(cursor);
                        searchSuggestions.add(searchSuggestion);
                        if (searchSuggestions.size() > 4) {
                            break;
                        }
                        cursor.moveToNext();
                    }
                    // make sure to close the cursor
                    cursor.close();
                }
            }
            return searchSuggestions;
        }

        @Override
        protected void onPostExecute(@NonNull ArrayList<SearchSuggestion> searchSuggestions) {
            super.onPostExecute(searchSuggestions);
            if (mListener != null) {
                mListener.onComplete(searchSuggestions);
            }
        }

        private void cancel() {
            mListener = null;
            cancel(true);
        }
    }

    static class GetPerformedSearchesStartingWithTask extends AsyncTask<Void, Void, ArrayList<SearchSuggestion>> {
        private final String mStartsWith;
        private int mLimit = 0;
        private DatabaseReadSearchesListener mListener = null;

        private GetPerformedSearchesStartingWithTask(@NonNull String startsWith, int limit, @Nullable DatabaseReadSearchesListener listener) {
            mStartsWith = startsWith;
            mLimit = limit;
            mListener = listener;
        }

        @Override
        protected final ArrayList<SearchSuggestion> doInBackground(Void... voids) {
            ArrayList<SearchSuggestion> searchSuggestions = new ArrayList<>();
            SQLiteDatabase readableDatabase = readDatabase();
            if (readableDatabase != null && readableDatabase.isOpen()) {
                Cursor cursor = readableDatabase.rawQuery(getStartsWithQuery(mStartsWith, mLimit), null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        SearchSuggestion searchSuggestion = cursorToPerformedSearch(cursor);
                        searchSuggestions.add(searchSuggestion);
                        if (searchSuggestions.size() > 4) {
                            break;
                        }
                        cursor.moveToNext();
                    }
                    // make sure to close the cursor
                    cursor.close();
                }
            }
            return searchSuggestions;
        }

        private String getStartsWithQuery(String startsWith, int limit) {
            return "SELECT * FROM "
                    + SEARCHES_TABLE_NAME
                    + " WHERE LOWER(" + COLUMN_NAME_SEARCH_TERM + ")"
                    + " LIKE " + DatabaseUtils.sqlEscapeString(startsWith.toLowerCase() + "%")
                    + " ORDER BY " + COLUMN_NAME_SEARCH_DATE + " DESC LIMIT " + String.valueOf(limit);
        }

        @Override
        protected void onPostExecute(@NonNull ArrayList<SearchSuggestion> searchSuggestions) {
            super.onPostExecute(searchSuggestions);
            if (mListener != null) {
                mListener.onComplete(searchSuggestions);
            }
        }

        void cancel() {
            mListener = null;
            cancel(true);
        }
    }

    static class GetRecentSearchesTask extends AsyncTask<Void, Void, ArrayList<SearchSuggestion>> {

        private int mLimit = DEFAULT_LIMIT;
        private DatabaseReadSearchesListener mListener = null;

        private GetRecentSearchesTask(int limit, @Nullable DatabaseReadSearchesListener listener) {
            mLimit = limit;
            mListener = listener;
        }

        @Override
        protected final ArrayList<SearchSuggestion> doInBackground(Void... voids) {
            ArrayList<SearchSuggestion> searchSuggestions = new ArrayList<>();
            SQLiteDatabase readableDatabase = readDatabase();
            if (readableDatabase != null && readableDatabase.isOpen()) {
                String query = "SELECT * FROM "
                        + SEARCHES_TABLE_NAME
                        + " ORDER BY " + COLUMN_NAME_SEARCH_DATE + " DESC LIMIT " + DEFAULT_LIMIT;
                Cursor cursor = readableDatabase.rawQuery(query, null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        SearchSuggestion searchSuggestion = cursorToPerformedSearch(cursor);
                        searchSuggestions.add(searchSuggestion);
                        if (searchSuggestions.size() == mLimit) {
                            break;
                        }
                        cursor.moveToNext();
                    }
                    // make sure to close the cursor
                    cursor.close();
                }
            }
            return searchSuggestions;
        }

        @Override
        protected void onPostExecute(@NonNull ArrayList<SearchSuggestion> searchSuggestions) {
            super.onPostExecute(searchSuggestions);
            if (mListener != null) {
                mListener.onComplete(searchSuggestions);
            }
        }

        void cancel() {
            mListener = null;
            cancel(true);
        }
    }

    @SuppressWarnings("unused")
    private static class DeleteDatabaseTask extends AsyncTask<Void, Void, Boolean> {

        private DatabaseTaskListener mListener = null;

        private DeleteDatabaseTask(@Nullable DatabaseTaskListener listener) {
            mListener = listener;
        }

        @Override
        protected final Boolean doInBackground(Void... voids) {
            if (sInstance != null) {
                SQLiteDatabase database = editDatabase();
                return database != null && database.isOpen() && database.delete(SEARCHES_TABLE_NAME, "1", null) > 0;
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean successful) {
            super.onPostExecute(successful);
            if (mListener != null) {
                if (successful != null && successful) {
                    mListener.onDatabaseEditSuccess();
                } else {
                    mListener.onDatabaseEditFailure();
                }
            }
        }

        private void cancel() {
            mListener = null;
            cancel(true);
        }

    }

    @SuppressWarnings("WeakerAccess")
    public interface DatabaseTaskListener {
        void onDatabaseEditSuccess();

        void onDatabaseEditFailure();
    }

    @SuppressWarnings("WeakerAccess")
    public interface DatabaseReadSearchesListener {
        void onComplete(@NonNull ArrayList<SearchSuggestion> searches);
    }
}
