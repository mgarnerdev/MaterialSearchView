package com.michaelgarnerdev.materialsearchview;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
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

public class SearchDatabase extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "msv_searches.db";
    private static final String TEXT_TYPE = " TEXT";
    private static final String REAL_TYPE = " REAL";
    private static final String COMMA_SEP = ", ";
    private static final String SEARCHES_TABLE_NAME = "searches";

    private static final int DEFAULT_LIMIT = 5;

    private static SearchDatabase sInstance;
    private static SQLiteDatabase sWritableDatabase;
    private static SQLiteDatabase sReadableDatabase;

    public static synchronized void init(@NonNull Context context) {
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

    public static void destroy() {
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
                    COLUMN_NAME_SEARCH_DATE + REAL_TYPE +
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

    public static void addPerformedSearch(@Nullable DatabaseTaskListener listener,
                                          @NonNull PerformedSearch... performedSearches) {
        new AddPerformedSearchTask(listener).execute(performedSearches);
    }

    private static boolean addPerformedSearch(@NonNull SQLiteDatabase database,
                                              @NonNull PerformedSearch performedSearch) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_SEARCH_TERM, performedSearch.getSearchTerm());
        values.put(COLUMN_NAME_SEARCH_DATE, performedSearch.getDate());

        return database.insert(SearchDatabase.SEARCHES_TABLE_NAME, null, values) != -1;
    }

    public static GetPerformedSearchesStartingWithTask filterSearchesBy(@NonNull String searchTerm,
                                                                        @NonNull DatabaseReadSearchesListener listener) {
        GetPerformedSearchesStartingWithTask task = new GetPerformedSearchesStartingWithTask(searchTerm, listener);
        task.execute();
        return task;
    }

    public static void getPerformedSearches(@NonNull DatabaseReadSearchesListener listener) {
        new GetPerformedSearchesTask(listener).execute();
    }

    public static GetRecentSearchesTask getRecentSearches(int limit, @NonNull DatabaseReadSearchesListener listener) {
        GetRecentSearchesTask task = new GetRecentSearchesTask(limit, listener);
        task.execute();
        return task;
    }

    private static PerformedSearch cursorToPerformedSearch(Cursor cursor) {
        return new PerformedSearch(cursor.getString(1), cursor.getString(2));
    }

    public static void deleteDatabase(@NonNull DatabaseTaskListener listener) {
        new DeleteDatabaseTask(listener).execute();
    }

    private static class AddPerformedSearchTask extends AsyncTask<PerformedSearch, Void, Boolean> {
        private DatabaseTaskListener mListener = null;

        private AddPerformedSearchTask(@Nullable DatabaseTaskListener listener) {
            mListener = listener;
        }

        @Override
        protected final Boolean doInBackground(PerformedSearch... performedSearches) {
            boolean success = false;
            if (performedSearches != null) {
                List<PerformedSearch> searches = Arrays.asList(performedSearches);
                success = true;
                SQLiteDatabase database = editDatabase();
                database.beginTransaction();
                for (PerformedSearch search : searches) {
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

    public static class GetPerformedSearchesTask extends AsyncTask<Void, Void, ArrayList<PerformedSearch>> {
        private DatabaseReadSearchesListener mListener = null;

        private GetPerformedSearchesTask(@Nullable DatabaseReadSearchesListener listener) {
            mListener = listener;
        }

        @Override
        protected final ArrayList<PerformedSearch> doInBackground(Void... voids) {
            ArrayList<PerformedSearch> performedSearches = new ArrayList<>();
            SQLiteDatabase readableDatabase = readDatabase();
            if (readableDatabase != null) {
                Cursor cursor = readableDatabase.query(true, SearchDatabase.SEARCHES_TABLE_NAME,
                        sSearchesTableAllColumns, null, null, null, null, COLUMN_NAME_SEARCH_DATE + " DESC", null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        PerformedSearch performedSearch = cursorToPerformedSearch(cursor);
                        performedSearches.add(performedSearch);
                        if (performedSearches.size() > 4) {
                            break;
                        }
                        cursor.moveToNext();
                    }
                    // make sure to close the cursor
                    cursor.close();
                }
            }
            return performedSearches;
        }

        @Override
        protected void onPostExecute(@NonNull ArrayList<PerformedSearch> performedSearches) {
            super.onPostExecute(performedSearches);
            if (mListener != null) {
                mListener.onComplete(performedSearches);
            }
        }

        private void cancel() {
            mListener = null;
            cancel(true);
        }
    }

    public static class GetPerformedSearchesStartingWithTask extends AsyncTask<Void, Void, ArrayList<PerformedSearch>> {
        private final String mStartsWith;
        private DatabaseReadSearchesListener mListener = null;

        private GetPerformedSearchesStartingWithTask(@NonNull String startsWith, @Nullable DatabaseReadSearchesListener listener) {
            mStartsWith = startsWith;
            mListener = listener;
        }

        @Override
        protected final ArrayList<PerformedSearch> doInBackground(Void... voids) {
            ArrayList<PerformedSearch> performedSearches = new ArrayList<>();
            SQLiteDatabase readableDatabase = readDatabase();
            if (readableDatabase != null) {
                Cursor cursor = readableDatabase.rawQuery(getStartsWithQuery(mStartsWith), null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        PerformedSearch performedSearch = cursorToPerformedSearch(cursor);
                        performedSearches.add(performedSearch);
                        if (performedSearches.size() > 4) {
                            break;
                        }
                        cursor.moveToNext();
                    }
                    // make sure to close the cursor
                    cursor.close();
                }
            }
            return performedSearches;
        }

        private String getStartsWithQuery(String startsWith) {
            return "SELECT * FROM "
                    + SEARCHES_TABLE_NAME
                    + " WHERE LOWER(" + COLUMN_NAME_SEARCH_TERM + ")"
                    + " LIKE '" + startsWith.toLowerCase() + "%'"
                    + " ORDER BY " + COLUMN_NAME_SEARCH_DATE + " DESC";
        }

        @Override
        protected void onPostExecute(@NonNull ArrayList<PerformedSearch> performedSearches) {
            super.onPostExecute(performedSearches);
            if (mListener != null) {
                mListener.onComplete(performedSearches);
            }
        }

        public void cancel() {
            mListener = null;
            cancel(true);
        }
    }

    public static class GetRecentSearchesTask extends AsyncTask<Void, Void, ArrayList<PerformedSearch>> {

        private int mLimit = DEFAULT_LIMIT;
        private DatabaseReadSearchesListener mListener = null;

        private GetRecentSearchesTask(int limit, @Nullable DatabaseReadSearchesListener listener) {
            mLimit = limit;
            mListener = listener;
        }

        @Override
        protected final ArrayList<PerformedSearch> doInBackground(Void... voids) {
            ArrayList<PerformedSearch> performedSearches = new ArrayList<>();
            SQLiteDatabase readableDatabase = readDatabase();
            if (readableDatabase != null) {
                String query = "SELECT * FROM "
                        + SEARCHES_TABLE_NAME
                        + " ORDER BY " + COLUMN_NAME_SEARCH_DATE + " ASC LIMIT " + DEFAULT_LIMIT;
                Cursor cursor = readableDatabase.rawQuery(query, null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        PerformedSearch performedSearch = cursorToPerformedSearch(cursor);
                        performedSearches.add(performedSearch);
                        if (performedSearches.size() == mLimit) {
                            break;
                        }
                        cursor.moveToNext();
                    }
                    // make sure to close the cursor
                    cursor.close();
                }
            }
            return performedSearches;
        }

        @Override
        protected void onPostExecute(@NonNull ArrayList<PerformedSearch> performedSearches) {
            super.onPostExecute(performedSearches);
            if (mListener != null) {
                mListener.onComplete(performedSearches);
            }
        }

        public void cancel() {
            mListener = null;
            cancel(true);
        }
    }

    private static class DeleteDatabaseTask extends AsyncTask<Void, Void, Boolean> {

        private DatabaseTaskListener mListener = null;

        private DeleteDatabaseTask(@Nullable DatabaseTaskListener listener) {
            mListener = listener;
        }

        @Override
        protected final Boolean doInBackground(Void... voids) {
            return editDatabase().delete(SEARCHES_TABLE_NAME, "1", null) > 0;
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
        void onComplete(@NonNull ArrayList<PerformedSearch> searches);
    }
}
