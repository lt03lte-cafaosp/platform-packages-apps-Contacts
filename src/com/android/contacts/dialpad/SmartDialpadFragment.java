/*
 * Copyright (C) 2011-2012, The Linux Foundation. All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.
     * Neither the name of The Linux Foundation nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.dialpad;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Color;
import android.media.ToneGenerator;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.dialpad.HanziToPinyin.Token;
import com.android.contacts.list.ContactListItemView;
import com.android.contacts.list.ContactListItemView.PhotoPosition;
import com.android.contacts.R;
import com.android.contacts.widget.TextWithHighlighting;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * Fragment that displays a twelve-key phone dialpad.
 */
public class SmartDialpadFragment extends DialpadFragment
        implements View.OnClickListener, TextWatcher {
    private static final String TAG = SmartDialpadFragment.class.getSimpleName();

    private static final String[] CONTACTS_SUMMARY_FILTER_NUMBER_PROJECTION = new String[] {
            ("_id"),
            ("normalized_number"),
            ("display_name"),
            ("photo_id"),
            ("lookup"),
    };
    private static final int QUERY_CONTACT_ID = 0;
    private static final int QUERY_NUMBER = 1;
    private static final int QUERY_DISPLAY_NAME = 2;
    private static final int QUERY_PHOTO_ID = 3;
    private static final int QUERY_LOOKUP_KEY = 4;
    private static final Uri CONTENT_SMART_DIALER_FILTER_URI =
            Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "smart_dialer_filter");
    private Handler mHandler = new Handler();
    private Animation showAction;
    private Animation hideAction;
    private ContactItemListAdapter mAdapter;
    private Cursor mCursor;
    private View mListoutside;
    private View mCountButton;
    private Button mCancel;
    private View mAddContact;
    private TextView mAddContactText;
    private TextView mCountView;
    private ListView mList;

    @Override
    public void afterTextChanged(Editable input) {
        super.afterTextChanged(input);

        if (isDigitsEmpty()) {
            mAddContact.setVisibility(View.INVISIBLE);
        }
        setQueryFilter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View fragmentView = super.onCreateView(inflater, container, savedState);

        mList = (ListView) fragmentView.findViewById(R.id.listview);
        mList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onListItemClick(mList, view, position, id);
            }
        });
        mListoutside = fragmentView.findViewById(R.id.listoutside);
        mCountButton = fragmentView.findViewById(R.id.filterbutton);
        mCountButton.setOnClickListener(this);
        mCountView = (TextView) fragmentView.findViewById(R.id.filter_number);
        mCancel = (Button) fragmentView.findViewById(R.id.cancel_btn);
        mCancel.setOnClickListener(this);
        mAddContact = fragmentView.findViewById(R.id.add_contact);
        mAddContact.setOnClickListener(this);
        mAddContactText = (TextView) fragmentView.findViewById(R.id.add_contact_text);

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Invisible as default,visible only when there is no match for
        // searching user input numbers
        mAddContact.setVisibility(View.INVISIBLE);
        if (!phoneIsInUse()) {
            setupListView();
            setQueryFilter();
            hideDialPadShowList(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.cancel_btn: {
                hideDialPadShowList(false);
                listScrollTop();
                return;
            }
            case R.id.filterbutton: {
                if (mDialpad.getVisibility() == View.VISIBLE) {
                    hideDialPadShowList(true);
                }
                return;
            }
            case R.id.add_contact: {
                final CharSequence digits = mDigits.getText();
                startActivity(getAddToContactIntent(digits));
                return;
            }
        }
        super.onClick(view);
    }

    protected void showDialpadChooser(boolean enabled) {
        super.showDialpadChooser(enabled);

        if (enabled && mListoutside != null) {
            mListoutside.setVisibility(View.GONE);
        }
    }

    protected void showDialConference(boolean enabled) {
        super.showDialConference(enabled);

        if (!enabled) {
            if (mListoutside != null) {
                mListoutside.setVisibility(View.VISIBLE);
            }
        }
    }

    final static class ContactListItemCache {
        public CharArrayBuffer nameBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer dataBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer highlightedTextBuffer = new CharArrayBuffer(128);
        public TextWithHighlighting textWithHighlighting;
        public CharArrayBuffer phoneticNameBuffer = new CharArrayBuffer(128);
    }

    private void setQueryFilter() {
        listScrollTop();
        if (mAdapter != null) {
            String filterString = getTextFilter();
            if (TextUtils.isEmpty(filterString)) {
                mAdapter.changeCursor(null);
            } else {
                Filter filter = mAdapter.getFilter();
                filter.filter(getTextFilter());
            }
        }
    }

    private void hideDialPadShowList(boolean isHide) {
        if (isHide) {
            mDialpad.startAnimation(hideAction);
            mDialpad.setVisibility(View.GONE);
            mCountButton.setVisibility(View.GONE);
            mCancel.setVisibility(View.VISIBLE);
            mCancel.setText(android.R.string.cancel);
        } else {
            if (!mDialpad.isShown()) {
                mDialpad.startAnimation(showAction);
                mDialpad.setVisibility(View.VISIBLE);
            }
            if (mCursor != null && !mCursor.isClosed() && mCursor.getCount() > 0) {
                mCountButton.setVisibility(View.VISIBLE);
            }
            mCancel.setVisibility(View.GONE);
            listScrollTop();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        showAction = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        showAction.setDuration(100);
        hideAction = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f);
        hideAction.setDuration(100);
        setupListView();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (null != mAddContactText) {
            mAddContactText.setText(R.string.non_phone_add_to_contacts);
        }
        if (mCancel instanceof TextView) {
            ((TextView) mCancel).setText(android.R.string.cancel);
        }
    }

    private void setDigitsPhoneByString(String phone) {
        playTone(ToneGenerator.TONE_DTMF_0);
        mDigits.setText(phone);
        hideDialPadShowList(false);
        listScrollTop();
    }

    public void onListItemClick(ListView l, View v, int position, long id) {
        final Cursor cursor = (Cursor) mAdapter.getItem(position);
        String phone;
        phone = cursor.getString(QUERY_NUMBER);

        setDigitsPhoneByString(phone);
        mDigits.setSelection(mDigits.length());

    }

    private void listScrollTop() {
        if (mList != null) {
            mList.post(new Runnable() {
                public void run() {
                    if (isResumed() && mList != null) {
                        mList.setSelection(0);
                    }
                }
            });
        }
    }

    private void setupListView() {
        final ListView list = mList;
        mAdapter = new ContactItemListAdapter(getActivity());
        mList.setAdapter(mAdapter);
        list.setOnCreateContextMenuListener(this);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (mDialpad.getVisibility() == View.VISIBLE
                        && AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL == scrollState) {
                    hideDialPadShowList(true);
                } else if (mDialpad.getVisibility() == View.VISIBLE
                        && AbsListView.OnScrollListener.SCROLL_STATE_IDLE == scrollState) {
                    list.setSelection(0);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                    int visibleItemCount, int totalItemCount) {
            }
        });
        list.setSaveEnabled(false);
    }

    private String getTextFilter() {
        if (mDigits != null) {
            // filter useless space
            return mDigits.getText().toString().replaceAll("[^0123456789+]", "");
        }
        return null;
    }

    private Cursor doFilter(String filter) {
        final ContentResolver resolver = getActivity().getContentResolver();
        Builder builder = CONTENT_SMART_DIALER_FILTER_URI.buildUpon();
        builder.appendQueryParameter("filter", filter);
        mCursor = resolver.query(builder.build(), CONTACTS_SUMMARY_FILTER_NUMBER_PROJECTION, null,
                null, null);
        // Bring on "Add to contacts" in UI thread when there is no match in
        // result for user input numbers
        if (mCursor.getCount() == 0) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mAddContact != null) {
                        mAddContact.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
        return mCursor;
    }

    private final class ContactItemListAdapter extends CursorAdapter {
        private CharSequence mUnknownNameText;
        private Cursor mSuggestionsCursor;
        private int mSuggestionsCursorCount;

        private int[] getStartEnd(String s, int start, int end) {
            int[] offset = new int[2];

            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (!(c >= '0' && c <= '9' || c == '+' || c >= 'a' && c <= 'z')) {
                    if (i <= start) {
                        start++;
                        end++;
                    } else if (i > start && i <= end) {
                        end++;
                    }
                }
            }
            if (start > s.length()) {
                start = s.length();
            }
            if (end > s.length()) {
                end = s.length();
            }
            offset[0] = start;
            offset[1] = end;
            return offset;
        }

        private String getNumberFormChar(char c) {
            if (c >= 'a' && c <= 'c') {
                return "2";
            } else if (c >= 'd' && c <= 'f') {
                return "3";
            } else if (c >= 'g' && c <= 'i') {
                return "4";
            } else if (c >= 'j' && c <= 'l') {
                return "5";
            } else if (c >= 'm' && c <= 'o') {
                return "6";
            } else if (c >= 'p' && c <= 's') {
                return "7";
            } else if (c >= 't' && c <= 'v') {
                return "8";
            } else if (c >= 'w' && c <= 'z') {
                return "9";
            } else if ('0' <= c && c <= '9') {
                return "" + c;
            } else {
                return "";
            }
        }

        private String getNameNumber(String name) {
            String number = "";
            String nameLow = name.toLowerCase();
            for (int i = 0; i < nameLow.length(); i++) {
                char c = nameLow.charAt(i);
                number = number + getNumberFormChar(c);
            }
            return number;
        }

        public String getFullPinYin(String source) {
            if (!Arrays.asList(Collator.getAvailableLocales()).contains(Locale.CHINA)) {
                return source;
            }
            ArrayList<Token> tokens = HanziToPinyin.getInstance().get(source);
            if (tokens == null || tokens.size() == 0) {
                return source;
            }
            StringBuffer result = new StringBuffer();
            for (Token token : tokens) {
                if (token.type == Token.PINYIN) {
                    result.append(token.target);
                } else {
                    result.append(token.source);
                }
            }
            return result.toString();
        }

        private void setTextViewSearchByNumber(char[] charName, int size, Cursor cursor,
                TextView nameView, TextView dataView) {
            String strNameView = String.copyValueOf(charName, 0, size);
            String strDataViewPhone;
            strDataViewPhone = cursor.getString(QUERY_NUMBER);
            String nameNumcopy = null;
            int i, j;

            String inputNum = getTextFilter();
            String phoneNum = strDataViewPhone != null ? strDataViewPhone.replaceAll(
                    "[^0123456789+]", "") : null;

            if (phoneNum != null && inputNum != null && phoneNum.contains(inputNum)) {
                int start, end;
                start = phoneNum.indexOf(inputNum);
                end = start + inputNum.length();
                int[] offset = getStartEnd(strDataViewPhone, start, end);
                SpannableStringBuilder style = new SpannableStringBuilder(strDataViewPhone);
                style.setSpan(new BackgroundColorSpan(0xFF33B5E5), offset[0], offset[1],
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                dataView.setText(style);
            } else {
                dataView.setText(strDataViewPhone);
            }

            String nameNum = getNameNumber(strNameView);
            if (nameNum == null || nameNum.trim().length() == 0) {
                String strNameViewcopy = getFullPinYin(strNameView);
                nameNumcopy = getNameNumber(strNameViewcopy);
            }
            if (nameNum != null && inputNum != null && nameNum.contains(inputNum)) {
                int start, end;
                start = nameNum.indexOf(inputNum);
                end = start + inputNum.length();
                int[] offset = getStartEnd(strNameView.toLowerCase(), start, end);
                SpannableStringBuilder style = new SpannableStringBuilder(strNameView);
                style.setSpan(new BackgroundColorSpan(0xFF33B5E5), offset[0], offset[1],
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                nameView.setText(style);
            } else if (nameNumcopy != null && inputNum != null && nameNumcopy.contains(inputNum)) {
                int start = 0;
                int end = 0;
                int lengthsave = 0;
                String strNameViewword;
                String strNameViewwordcopy;
                String nameNumwordcopy;
                String inputNumcopy;
                for (i = 0; i < size; i++) {
                    strNameViewword = String.copyValueOf(charName, i, 1);
                    strNameViewwordcopy = getFullPinYin(strNameViewword);
                    nameNumwordcopy = getNameNumber(strNameViewwordcopy);
                    if (nameNumwordcopy != null && inputNum != null
                            && inputNum.contains(nameNumwordcopy) &&
                            (nameNumwordcopy.length() > lengthsave)
                            && (inputNum.indexOf(nameNumwordcopy) == 0)) {
                        lengthsave = nameNumwordcopy.length();
                        start = i;
                        end = i + 1;
                    }
                }
                if (start == 0 && end == 0) {
                    for (i = 0; i < size; i++) {
                        strNameViewword = String.copyValueOf(charName, i, 1);
                        strNameViewwordcopy = getFullPinYin(strNameViewword);
                        nameNumwordcopy = getNameNumber(strNameViewwordcopy);
                        if (nameNumwordcopy != null && inputNum != null
                                && nameNumwordcopy.contains(inputNum) &&
                                (nameNumwordcopy.indexOf(inputNum) == 0)) {
                            start = i;
                            end = i + 1;
                            break;
                        }
                    }
                } else {
                    inputNumcopy = inputNum.substring(lengthsave);
                    for (j = start + 1; j <= size; j++) {
                        strNameViewword = String.copyValueOf(charName, j, 1);
                        strNameViewwordcopy = getFullPinYin(strNameViewword);
                        nameNumwordcopy = getNameNumber(strNameViewwordcopy);
                        if (nameNumwordcopy != null && inputNumcopy != null
                                && inputNumcopy.contains(nameNumwordcopy)) {
                            inputNumcopy = inputNumcopy.substring(nameNumwordcopy.length());
                            end++;
                        } else if (nameNumwordcopy != null && inputNumcopy != null
                                && nameNumwordcopy.contains(inputNumcopy) &&
                                (nameNumwordcopy.indexOf(inputNumcopy) == 0)
                                && (inputNumcopy.length() != 0)) {
                            end++;
                            break;
                        } else {
                            break;
                        }
                    }
                }
                if (end > size) {
                    end = size;
                }
                SpannableStringBuilder style = new SpannableStringBuilder(strNameView);
                style.setSpan(new BackgroundColorSpan(0xFF33B5E5), start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                nameView.setText(style);
            } else {
                nameView.setText(strNameView);// here haven't highlight name
            }
        }

        public ContactItemListAdapter(Context context) {
            super(context, null, false);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!mDataValid) {
                throw new IllegalStateException(
                        "this should only be called when the cursor is valid");
            }

            if (!mCursor.moveToPosition(position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }

            View v;
            if (convertView == null || convertView.getTag() == null) {
                v = newView(mContext, mCursor, parent);
            } else {
                v = convertView;
            }
            bindView(v, mContext, mCursor);
            return v;
        }

        public void bindView(View itemView, Context context, Cursor cursor) {
            final ContactListItemView view = (ContactListItemView) itemView;
            final ContactListItemCache cache = (ContactListItemCache) view.getTag();

            cursor.copyStringToBuffer(QUERY_DISPLAY_NAME, cache.nameBuffer);

            TextView nameView = view.getNameTextView();
            TextView dataView = view.getDataView();
            int size = cache.nameBuffer.sizeCopied;
            if (size != 0) {
                setTextViewSearchByNumber(cache.nameBuffer.data, size, cursor, nameView, dataView);
            } else {
                nameView.setText(mUnknownNameText);
            }

            final long contactId = cursor.getLong(QUERY_CONTACT_ID);
            final String lookupKey = cursor.getString(QUERY_LOOKUP_KEY);
            long photoId = 0;
            if (!cursor.isNull(QUERY_PHOTO_ID)) {
                photoId = cursor.getLong(QUERY_PHOTO_ID);
            }

            ImageView photo = view.getPhotoView();
            ContactPhotoManager.getInstance(mContext).loadThumbnail(photo, photoId, false);
            view.setPresence(null);

        }

        @Override
        public View newView(Context arg0, Cursor arg1, ViewGroup arg2) {
            final ContactListItemView view = new ContactListItemView(getActivity(), null);
            view.setPhotoPosition(PhotoPosition.LEFT);
            view.setTag(new ContactListItemCache());
            view.setQuickContactEnabled(false);
            return view;
        }

        /**
         * Run the query on a helper thread. Beware that this code does not run
         * on the main UI thread!
         */
        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            return doFilter(constraint.toString());
        }

        public void changeCursor(Cursor cursor) {
            // If the view doesnot created, do nothing.
            if (SmartDialpadFragment.this.getView() == null) {
                return;
            }
            if (isDigitsEmpty()) {
                mCountButton.setVisibility(View.GONE);
                mList.setVisibility(View.GONE);
                hideDialPadShowList(false);
            } else if (cursor != null && cursor.moveToFirst()) {
                mCountButton.setVisibility(View.VISIBLE);
                mList.setVisibility(View.VISIBLE);
            } else {
                mCountButton.setVisibility(View.GONE);
                mList.setVisibility(View.GONE);
                hideDialPadShowList(false);
                mAddContact.setVisibility(View.VISIBLE);
            }
            if (mDialpad.isShown() && !isDigitsEmpty() && cursor != null && cursor.getCount() > 0) {
                mCountButton.setVisibility(View.VISIBLE);
                mCountView.setText(cursor.getCount() + "");
                mCountView.invalidate();
            } else {
                mCountButton.setVisibility(View.GONE);
            }
            super.changeCursor(cursor);
        }
    }
}
