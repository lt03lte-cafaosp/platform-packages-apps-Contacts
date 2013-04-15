/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.calllog;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import android.database.CrossProcessCursor;
import android.content.Intent;
import android.database.CursorWindow;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.database.sqlite.SQLiteException;

/**
 * Provides access to the dictionary database.
 */
public class RecentCallSearchProvider extends ContentProvider {
    String TAG = "RecentCallSearchProvider";

    public static String AUTHORITY = "com.android.contacts.calllog.RecentCallSearchProvider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/dictionary");

    // MIME types used for searching words or looking up a single definition
    public static final String WORDS_MIME_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE +
                                                  "/vnd.example.android.searchabledict";
    public static final String DEFINITION_MIME_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE +
                                                       "/vnd.example.android.searchabledict";


    // UriMatcher stuff
    private static final int SEARCH_WORDS = 0;
    private static final int GET_WORD = 1;
    private static final int SEARCH_SUGGEST = 2;
    private static final int REFRESH_SHORTCUT = 3;

    /**
     * Builds up a UriMatcher for search suggestion and shortcut refresh queries.
     */
     

    @Override
    public boolean onCreate() {
        Log.d(TAG, "RecentCallSearchProvider onCreate");
        return true;
    }

    /**
     * Handles all the dictionary searches and suggestion queries from the Search Manager.
     * When requesting a specific word, the uri alone is required.
     * When searching all of the dictionary for matches, the selectionArgs argument must carry
     * the search query as the first element.
     * All other arguments are ignored.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        Log.d(TAG, "RecentCallSearchProvider query selectionArgs[0]: "+selectionArgs[0]);
        Uri u = Uri.parse(String.format(        
                "content://call_log/calls/searchSuggest?pattern=%s",
                selectionArgs[0]));
        Cursor c = getContext().getContentResolver().query(
                u,
                null,
                null,
                null,
                null);

        return new SuggestionsCursor(c, selectionArgs[0]);
///        return c;
    }

    private class SuggestionsCursor implements CrossProcessCursor {
        Cursor mDatabaseCursor;
        int mColumnCount;
        int mCurrentRow;
        ArrayList<Row> mRows = new ArrayList<Row>();

        public SuggestionsCursor(Cursor cursor, String query) {
            mDatabaseCursor = cursor;

            mColumnCount = cursor.getColumnCount();
            Log.d("SuggestionsProvider", "SuggestionsCursor mColumnCount:" + mColumnCount);
            try {
                computeRows();
            } catch (SQLiteException ex) {
                // This can happen if the user enters -n (anything starting with -).
                // sqlite3/fts3 can't handle it.  Google for "logic error or missing database fts3"
                // for commentary on it.
                mRows.clear(); // assume no results
            }
        }

        public int getCount() {
            return mRows.size();
        }
 
        public int getType(int columnIndex) {
            return -1;
        }
    
        private class Row {
            public Row(int row, String name, String number, int startOffset, int endOffset) {
                mName = name;
                mNumber = number;
                mRowNumber = row;
                mStartOffset = startOffset;
                mEndOffset = endOffset;
            }
            String mName;
            String mNumber;
            int mRowNumber;
            int mStartOffset;
            int mEndOffset;

            public String getName() {
                return mName;
            }
            
            public String getNumber() {
                return mNumber;
            }
            
            public String getLine1() {
                if (mName != null && !mName.equals("")) {
                    return mName;
                } else {
                    return mNumber;
                }
            }
            
            public String getLine2() {
                if (mName != null && !mName.equals("")) {
                    return mNumber;
                } else {
                    return null;
                }
            }
        }

        private void computeRows() {
            HashSet<String> got = new HashSet<String>();

            int nameColumn = mDatabaseCursor.getColumnIndex("name");
            int numberColumn = mDatabaseCursor.getColumnIndex("number");
//          int offsetsColumn = mDatabaseCursor.getColumnIndex("offsets(words)");

            int count = mDatabaseCursor.getCount();
            Log.d("SuggestionsProvider", "computeRows count:" + count);
            for (int i = 0; i < count; i++) {
                mDatabaseCursor.moveToPosition(i);
                String name = mDatabaseCursor.getString(nameColumn);
                String number = mDatabaseCursor.getString(numberColumn);
                mRows.add(new Row(i, name, number, 0, 0));
//                int [] offsets = computeOffsets(mDatabaseCursor.getString(offsetsColumn));
//                for (int j = 0; j < offsets.length; j += 4) {
//                    int startOffset  = offsets[j+2];
//                    int length       = offsets[j+3];
//                    int endOffset = startOffset + length;
//                    String candidate = message.substring(startOffset, endOffset);
//                    String key = candidate.toLowerCase();
//                    if (got.contains(key)) {
//                        continue;
//                    }
//                    got.add(key);
//                    mRows.add(new Row(i, message, startOffset, endOffset));
//                }
            }
        }

//        private int [] computeOffsets(String offsetsString) {
//            String [] vals = offsetsString.split(" ");
//
//            int [] retvals = new int[vals.length];
//            for (int i = retvals.length-1; i >= 0; i--) {
//                retvals[i] = Integer.parseInt(vals[i]);
//            }
//            return retvals;
//        }

        public void fillWindow(int position, CursorWindow window) {
            Log.d("SuggestionsProvider", "fillWindow position :"+position);
            int count = getCount();
            if (position < 0 || position > count - 1) {
                return;
            }
            window.acquireReference();
            try {
                int oldpos = getPosition();
                int pos = position;
                window.clear();
                window.setStartPosition(position);
                int columnNum = getColumnCount();
                window.setNumColumns(columnNum);
                while (moveToPosition(pos) && window.allocRow()) {
                    for (int i = 0; i < columnNum; i++) {
                        String field = getString(i);
                        Log.d("SuggestionsProvider", "fillWindow field:" + field);
                        if (field != null) {
                            if (!window.putString(field, pos, i)) {
                                window.freeLastRow();
                                break;
                            }
                        } else {
                            if (!window.putNull(pos, i)) {
                                window.freeLastRow();
                                break;
                            }
                        }
                    }
                    ++pos;
                }
                moveToPosition(oldpos);
            } catch (IllegalStateException e){
                // simply ignore it
            } finally {
                window.releaseReference();
            }
        }

        public CursorWindow getWindow() {
//          return ((CrossProcessCursor)mCursor).getWindow();
            Log.d("SuggestionsProvider", "getWindow ");
            CursorWindow window = new CursorWindow(false);
            
            int count = mDatabaseCursor.getCount();
            fillWindow(0, window);
            return window;
        }

        public boolean onMove(int oldPosition, int newPosition) {
            return ((CrossProcessCursor)mDatabaseCursor).onMove(oldPosition, newPosition);
        }

        /*
         * These "virtual columns" are columns which don't exist in the underlying
         * database cursor but are exported by this cursor.  For example, we compute
         * a "word" by taking the substring of the full row text in the words table
         * using the provided offsets.
         */
        private String [] mVirtualColumns = new String [] {
                SearchManager.SUGGEST_COLUMN_INTENT_DATA,
                SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
                SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,
                SearchManager.SUGGEST_COLUMN_TEXT_1,
                SearchManager.SUGGEST_COLUMN_TEXT_2
            };

        // Cursor column offsets for the above virtual columns.
        // These columns exist after the natural columns in the
        // database cursor.  So, for example, the column called
        // SUGGEST_COLUMN_TEXT_1 comes 3 after mDatabaseCursor.getColumnCount().
        private final int INTENT_DATA_COLUMN = 0;
        private final int INTENT_ACTION_COLUMN = 1;
        private final int INTENT_EXTRA_DATA_COLUMN = 2;
        private final int INTENT_TEXT_COLUMN = 3;
        private final int INTENT_TEXT2_COLUMN = 4;


        public int getColumnCount() {
            Log.d("SuggestionsProvider", "getColumnCount " + (mColumnCount + mVirtualColumns.length));
            return mColumnCount + mVirtualColumns.length;
        }

        public int getColumnIndex(String columnName) {
            for (int i = 0; i < mVirtualColumns.length; i++) {
                if (mVirtualColumns[i].equals(columnName)) {
                    return mColumnCount + i;
                }
            }
            return mDatabaseCursor.getColumnIndex(columnName);
        }

        public String [] getColumnNames() {
            String [] x = mDatabaseCursor.getColumnNames();
            String [] y = new String [x.length + mVirtualColumns.length];

            for (int i = 0; i < x.length; i++) {
                y[i] = x[i];
            }

            for (int i = 0; i < mVirtualColumns.length; i++) {
                y[x.length + i] = mVirtualColumns[i];
            }

            return y;
        }

        public boolean moveToPosition(int position) {
            Log.d("SuggestionsProvider", "moveToPosition " + position);
            if (position >= 0 && position < mRows.size()) {
                mCurrentRow = position;
                mDatabaseCursor.moveToPosition(mRows.get(position).mRowNumber);
                return true;
            } else {
                return false;
            }
        }

        public boolean move(int offset) {
            return moveToPosition(mCurrentRow + offset);
        }

        public boolean moveToFirst() {
            return moveToPosition(0);
        }

        public boolean moveToLast() {
            return moveToPosition(mRows.size() - 1);
        }

        public boolean moveToNext() {
            return moveToPosition(mCurrentRow + 1);
        }

        public boolean moveToPrevious() {
            return moveToPosition(mCurrentRow - 1);
        }

        public String getString(int column) {
            Log.d(TAG, "RecentCallSearchProvider getString column"+column);
            if (column < mColumnCount) {
                return mDatabaseCursor.getString(column);
            }

            Row row = mRows.get(mCurrentRow);
            switch (column - mColumnCount) {
                case INTENT_DATA_COLUMN:
                    Uri u = Uri.parse("content://call_log/calls").buildUpon().appendQueryParameter("pattern", row.getNumber()).build();
                    return u.toString();
                case INTENT_ACTION_COLUMN:
                    return Intent.ACTION_SEARCH;
                case INTENT_EXTRA_DATA_COLUMN:
                    return getString(getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1));
                case INTENT_TEXT_COLUMN:
                    return row.getLine1();
                case INTENT_TEXT2_COLUMN:
                    return row.getLine2();
                default:
                    return null;
            }
        }

        public void abortUpdates() {
        }

        public void close() {
            mDatabaseCursor.close();
        }

        public boolean commitUpdates() {
            return false;
        }

        public boolean commitUpdates(Map<? extends Long, ? extends Map<String, Object>> values) {
            return false;
        }

        public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
            mDatabaseCursor.copyStringToBuffer(columnIndex, buffer);
        }

        public void deactivate() {
            mDatabaseCursor.deactivate();
        }

        public boolean deleteRow() {
            return false;
        }

        public byte[] getBlob(int columnIndex) {
            return null;
        }

        public int getColumnIndexOrThrow(String columnName)
                throws IllegalArgumentException {
            return 0;
        }

        public String getColumnName(int columnIndex) {
            return null;
        }

        public double getDouble(int columnIndex) {
            return 0;
        }

        public Bundle getExtras() {
            return Bundle.EMPTY;
        }

        public float getFloat(int columnIndex) {
            return 0;
        }

        public int getInt(int columnIndex) {
            return 0;
        }

        public long getLong(int columnIndex) {
            return 0;
        }

        public int getPosition() {
            return mCurrentRow;
        }

        public short getShort(int columnIndex) {
            return 0;
        }

        public boolean getWantsAllOnMoveCalls() {
            return false;
        }

        public boolean hasUpdates() {
            return false;
        }

        public boolean isAfterLast() {
            return mCurrentRow >= mRows.size();
        }

        public boolean isBeforeFirst() {
            return mCurrentRow < 0;
        }

        public boolean isClosed() {
            return mDatabaseCursor.isClosed();
        }

        public boolean isFirst() {
            return mCurrentRow == 0;
        }

        public boolean isLast() {
            return mCurrentRow == mRows.size() - 1;
        }

        public boolean isNull(int columnIndex) {
            return false;  // TODO revisit
        }

        public void registerContentObserver(ContentObserver observer) {
            mDatabaseCursor.registerContentObserver(observer);
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mDatabaseCursor.registerDataSetObserver(observer);
        }

        public boolean requery() {
            return false;
        }

        public Bundle respond(Bundle extras) {
            return mDatabaseCursor.respond(extras);
        }

        public void setNotificationUri(ContentResolver cr, Uri uri) {
            mDatabaseCursor.setNotificationUri(cr, uri);
        }

        public boolean supportsUpdates() {
            return false;
        }

        public void unregisterContentObserver(ContentObserver observer) {
            mDatabaseCursor.unregisterContentObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mDatabaseCursor.unregisterDataSetObserver(observer);
        }

        public boolean updateBlob(int columnIndex, byte[] value) {
            return false;
        }

        public boolean updateDouble(int columnIndex, double value) {
            return false;
        }

        public boolean updateFloat(int columnIndex, float value) {
            return false;
        }

        public boolean updateInt(int columnIndex, int value) {
            return false;
        }

        public boolean updateLong(int columnIndex, long value) {
            return false;
        }

        public boolean updateShort(int columnIndex, short value) {
            return false;
        }

        public boolean updateString(int columnIndex, String value) {
            return false;
        }

        public boolean updateToNull(int columnIndex) {
            return false;
        }
        
        
                
    }


    @Override
    public String getType(Uri uri) {
        return null;
    }

   
    // Other required implementations...

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

}
