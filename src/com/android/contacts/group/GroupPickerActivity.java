/*
Copyright (c) 2016, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
/*
 * BORQS Software Solutions Pvt Ltd. CONFIDENTIAL
 * Copyright (c) 2016 All rights reserved.
 *
 * The source code contained or described herein and all documents
 * related to the source code ("Material") are owned by BORQS Software
 * Solutions Pvt Ltd. No part of the Material may be used,copied,
 * reproduced, modified, published, uploaded,posted, transmitted,
 * distributed, or disclosed in any way without BORQS Software
 * Solutions Pvt Ltd. prior written permission.
 *
 * No license under any patent, copyright, trade secret or other
 * intellectual property right is granted to or conferred upon you
 * by disclosure or delivery of the Materials, either expressly, by
 * implication, inducement, estoppel or otherwise. Any license
 * under such intellectual property rights must be express and
 * approved by BORQS Software Solutions Pvt Ltd. in writing.
 *
 */
package com.android.contacts.group;

import java.util.ArrayList;

import com.android.contacts.R;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ListActivity;
import android.app.ActionBar.LayoutParams;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import com.android.contacts.group.SuggestedMember;
import com.android.contacts.util.Constants;

public class GroupPickerActivity extends Activity implements
    OnItemClickListener, OnItemSelectedListener {

    private String mAccountName = null;
    private String mAccountType = null;
    private String mDataSet = null;
    private String mGroupName = null;
    private Boolean mIsGroupPicker = null;
    private ContentResolver mContentResolver = null;
    private long[] mContactIds = null;
    private long[] mRawContactIds = null;
    private View mMultiSelectActionBarView;
    private TextView mSelectedContactsCount;

    private SearchView mSearchView;
    private ArrayList<SuggestedMember> mSuggestedMembers = null;
    private ArrayList<Long> mExistingListArray = new ArrayList<Long>();
    private ArrayList<SuggestedMember> mListToDisplay = new ArrayList<SuggestedMember>();

    private static final String[] PROJECTION_FILTERED_MEMBERS = new String[] {
            RawContacts._ID, // 0
            RawContacts.CONTACT_ID, // 1
            RawContacts.DISPLAY_NAME_PRIMARY // 2
    };

    private static final String[] PROJECTION_MEMBER_DATA = new String[] {
            RawContacts._ID, // 0
            RawContacts.CONTACT_ID, // 1
            Data.MIMETYPE, // 2
            Photo.PHOTO // 3
    };

    private static final String[] PROJECTION_MEMBER_PHONE = new String[] {
            RawContacts._ID, // 0
            RawContacts.CONTACT_ID, // 1
            Data.MIMETYPE, // 2
            Data.DATA1 // 3
    };

    private static final int MIMETYPE_COLUMN_INDEX = 2;
    private static final int PHOTO_COLUMN_INDEX = 3;
    ListView mListView;
    Context mContext;
    Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_picker_list);
        mContext = this;
        mContentResolver = this.getContentResolver();

        Intent intent = this.getIntent();
        mAccountName = intent.getStringExtra("AccountName");
        mAccountType = intent.getStringExtra("AccountType");
        mDataSet = intent.getStringExtra("DataSet");
        mIsGroupPicker = intent.getBooleanExtra("isGroupPicker", false);
        mSuggestedMembers = new ArrayList<SuggestedMember>();

        mGroupName = intent.getStringExtra("GroupName");
        long[] existinglist = intent.getLongArrayExtra("ExistingContactId");
        if (existinglist != null) {
            for (long id : existinglist) {
                mExistingListArray.add(id);
            }
        }

        prepareSearchViewAndActionBar();

        mListView = (ListView) findViewById(R.id.list);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        mListView.setOnItemClickListener(this);
        mListView.setOnItemSelectedListener(this);
        mListView.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                ListView listView = mListView;
                PickerMemberAdapter adapter = (PickerMemberAdapter) mListView
                        .getAdapter();
                if (!hasFocus) {
                    adapter.setSelectedPosition(-1);
                    adapter.notifyDataSetChanged();
                } else {
                    if (adapter.getSelectedPosition() == -1) {
                        int count = listView.getCount();
                        int firstPosition = listView.getFirstVisiblePosition();
                        int lastPosition = listView.getLastVisiblePosition();
                        if (firstPosition == 1) {
                            adapter.setSelectedPosition(firstPosition);
                            adapter.notifyDataSetChanged();
                        } else if (lastPosition == listView.getCount() - 1) {
                            adapter.setSelectedPosition(lastPosition);
                            adapter.notifyDataSetChanged();
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });
        TextView emptyView = new TextView(this);
        emptyView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        emptyView.setText(getString(R.string.loading_contacts));
        emptyView.setVisibility(View.GONE);
        ((ViewGroup) mListView.getParent()).addView(emptyView);
        mListView.setEmptyView(emptyView);
        Thread startQuery = new Thread(queryThread);
        startQuery.start();
    }

    private void prepareSearchViewAndActionBar() {

        // If ActionBar is available, show SearchView on it. If not, show SearchView inside the
        // Activity's layout.
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            final View searchViewContainer = LayoutInflater.from(actionBar.getThemedContext())
                    .inflate(R.layout.contacts_list_multi_select_picker_actionbar, null);
            mSelectedContactsCount = (TextView) searchViewContainer
                    .findViewById(R.id.selected_contacts_count);
            mSearchView = (SearchView) searchViewContainer.findViewById(R.id.search_view);

            actionBar.setCustomView(searchViewContainer,
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);

            mSearchView.setIconifiedByDefault(true);
            mSearchView.setQueryHint(getString(R.string.hint_findContacts));
            mSearchView.setIconified(false);

            int id = mSearchView.getContext().getResources()
                    .getIdentifier("android:id/search_src_text", null, null);
            AutoCompleteTextView searchText = (AutoCompleteTextView) mSearchView
                    .findViewById(id);
            searchText.setTextColor(getResources().getColor(
                    android.R.color.holo_blue_light));

            mSearchView.onActionViewExpanded();
            mSearchView.requestFocus();
            mSelectedContactsCount.setText(Integer.toString(0)
                    +" "+getResources().getString(R.string.selected));
        }

        // Clear focus and suppress keyboard show-up.
        mSearchView.clearFocus();
        mSearchView.setVisibility(View.GONE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    PickerMemberAdapter mAdapter = null;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (mListToDisplay != null && !mListToDisplay.isEmpty()) {
                mListView.setAdapter(mAdapter);
                int currentOrientation = getResources().getConfiguration().orientation;
            } else {
                Intent result = getIntent();
                setResult(GroupEditorFragment.ALL_MEMBERS_ALREADY_ADDED, result);
                finish();
            }
        }

    };

    private Runnable queryThread = new Runnable() {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            mListToDisplay = getMemberToAdd();
            if (mListToDisplay != null && !mListToDisplay.isEmpty()) {
                mAdapter = new PickerMemberAdapter(GroupPickerActivity.this,
                        mListToDisplay);
            }
            mHandler.sendEmptyMessage(0);
        }
    };

    private ArrayList<SuggestedMember> getMemberToAdd() {

        Cursor allMembersCursor = null;
        String accountClause = RawContacts.ACCOUNT_NAME + "=? AND "
                + RawContacts.ACCOUNT_TYPE + "=?";
        String[] args = null;
        if (mDataSet == null) {
            accountClause += " AND " + RawContacts.DATA_SET + " IS NULL";
            args = new String[] { mAccountName, mAccountType };
        } else {
            accountClause += " AND " + RawContacts.DATA_SET + "=?";
            args = new String[] { mAccountName, mAccountType, mDataSet };
        }

        String where = accountClause + " AND (" + RawContacts.DELETED + "= 0) ";
        allMembersCursor = mContentResolver.query(RawContacts.CONTENT_URI,
                PROJECTION_FILTERED_MEMBERS, where, args,
                RawContacts.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC");

        if (allMembersCursor.getCount() == mExistingListArray.size()){
            if (allMembersCursor.getCount() == 1
                    && mExistingListArray.get(0) == allMembersCursor
                            .getColumnIndex(RawContacts._ID)) {
                allMembersCursor.close();
                return null;
            }
        }

        ArrayList<SuggestedMember> suggestionsList = new ArrayList<SuggestedMember>();

        ArrayList<Long> contactList = new ArrayList<Long>();

        allMembersCursor.move(-1);
        while (allMembersCursor.moveToNext()) {
            if (mExistingListArray != null && mExistingListArray
                    .contains(allMembersCursor.getLong(1))) {
                continue;
            }

            long contactId = allMembersCursor.getLong(1);
            long rawContactId = allMembersCursor.getLong(0);
            if (mIsGroupPicker == true) {
                if (!contactList.contains(contactId)) {
                    String displayName = allMembersCursor.getString(2);
                    SuggestedMember member = new SuggestedMember(rawContactId,
                            displayName, contactId);
                    suggestionsList.add(member);
                    contactList.add(contactId);
                }
            } else {
                String displayName = allMembersCursor.getString(2);
                SuggestedMember member = new SuggestedMember(rawContactId,
                        displayName, contactId);
                suggestionsList.add(member);
            }
        }
        allMembersCursor.close();

        ArrayList<String> selectionArgsData = new ArrayList<String>();
        selectionArgsData.add(Photo.CONTENT_ITEM_TYPE);

        // Perform a second query to retrieve a photo and possibly a phone
        // number or email
        // address for the suggested contact
        Cursor memberDataCursor = null;
        for (SuggestedMember suggestedMember : suggestionsList) {
            memberDataCursor = mContentResolver
                    .query(RawContactsEntity.CONTENT_URI,
                            PROJECTION_MEMBER_DATA,
                            "(" + Data.MIMETYPE + "=? ) AND ( "
                                    + RawContacts._ID + " = "
                                    + suggestedMember.getRawContactId() + " ) ",
                            selectionArgsData.toArray(new String[0]), null);

            memberDataCursor.move(-1);
            while (memberDataCursor.moveToNext()) {
                String mimetype = memberDataCursor
                        .getString(MIMETYPE_COLUMN_INDEX);
                if (Photo.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    // Set photo
                    byte[] bitmapArray = memberDataCursor
                            .getBlob(PHOTO_COLUMN_INDEX);
                    suggestedMember.setPhotoByteArray(bitmapArray);
                }
            }
            memberDataCursor.close();
        }
        return suggestionsList;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {

        if (mListView.isItemChecked(position)) {
            mListView.setItemChecked(position, true);
        } else {
            mListView.setItemChecked(position, false);
        }
        ListView listView = mListView;

        PickerMemberAdapter adapter = (PickerMemberAdapter) listView
                .getAdapter();
        final int checkedCount = listView.getCheckedItemCount();
        mSelectedContactsCount.setText(Integer.toString(checkedCount)
                +" "+getResources().getString(R.string.selected));
        SuggestedMember item = (SuggestedMember) adapter.getItem(position);
        boolean checked = mListView.isItemChecked(position);

        if (checked) {
            if (!mSuggestedMembers.contains(item))
                mSuggestedMembers.add(item);
            adapter.addSelection(position);
        } else {
            mSuggestedMembers.remove(item);
            adapter.removeSelection(position);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.add_members: {
            setIntentAndfinish();
            break;
        }
        case R.id.deselect_all: {
            ListView listView1 = mListView;
            PickerMemberAdapter adapter = (PickerMemberAdapter) listView1
                    .getAdapter();
            for (int i = 0; i < listView1.getCount(); i++) {
                listView1.setItemChecked(i, false);
            }
            adapter.deSelectAll();
            mSuggestedMembers.clear();
            mSelectedContactsCount.setText(Integer.toString(0)
                    +" "+getResources().getString(R.string.selected));
            adapter.notifyDataSetChanged();
            break;
        }
        case R.id.discard:
            ListView listView = mListView;
            for (int i = 0; i < listView.getCount(); i++) {
                listView.setItemChecked(i, false);
            }
            mSuggestedMembers.clear();
            setIntentAndfinish();
            break;
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.groups_picker_multi_select_menu, menu);

        MenuItem addMembers = menu.findItem(R.id.add_members);
        addMembers.setVisible(true);

        MenuItem deselectMembers = menu.findItem(R.id.deselect_all);
        deselectMembers.setVisible(true);

        MenuItem discard = menu.findItem(R.id.discard);
        discard.setVisible(true);

        return super.onCreateOptionsMenu(menu);

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // TODO Auto-generated method stub
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * This represents a single contact that is a suggestion for the user to add
     * to a group.
     */
    // TODO: Merge this with the {@link GroupEditorFragment} Member class once
    // we can find the
    // lookup URI for this contact using the autocomplete filter queries

    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.groups_picker_multi_select_menu, menu);

        if (mMultiSelectActionBarView == null) {
            mMultiSelectActionBarView = (ViewGroup) LayoutInflater.from(
                    getBaseContext()).inflate(
                    R.layout.group_list_multi_select_actionbar, null);

            mSelectedContactsCount = (TextView) mMultiSelectActionBarView
                    .findViewById(R.id.selected_contacts_count);

        }
        mode.setCustomView(mMultiSelectActionBarView);
        ((TextView) mMultiSelectActionBarView.findViewById(R.id.title))
                .setText(R.string.selected);
        mSuggestedMembers = new ArrayList<SuggestedMember>();
        return true;
    }

    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        if (mMultiSelectActionBarView == null) {
            ViewGroup v = (ViewGroup) LayoutInflater.from(getBaseContext())
                    .inflate(R.layout.group_list_multi_select_actionbar, null);
            mode.setCustomView(v);

            mSelectedContactsCount = (TextView) v
                    .findViewById(R.id.selected_contacts_count);
        }
        return true;
    }

    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
        case R.id.add_members: {
            setIntentAndfinish();
            break;
        }
        case R.id.deselect_all: {
            ListView listView1 = mListView;
            for (int i = 0; i < listView1.getCount(); i++) {
                listView1.setItemChecked(i, false);
            }
            break;
        }
        case R.id.discard:
            ListView listView = mListView;
            for (int i = 0; i < listView.getCount(); i++) {
                listView.setItemChecked(i, false);
            }
            setIntentAndfinish();
            break;
        }
        return true;
    }

    public void onDestroyActionMode(ActionMode mode) {
        PickerMemberAdapter adapter = (PickerMemberAdapter) mListView
                .getAdapter();
        if (adapter != null) {
            adapter.deSelectAll();
            adapter.setSelectedPosition(-1);
        }

        if (mSuggestedMembers != null && !mSuggestedMembers.isEmpty())
            mSuggestedMembers.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void setIntentAndfinish() {

        Intent result = getIntent();
        if (mSuggestedMembers != null && !mSuggestedMembers.isEmpty()) {
            ArrayList<Long> contactIds = new ArrayList<Long>();
            ArrayList<Long> rawContactIds = new ArrayList<Long>();
            int i = 0;
            for (SuggestedMember member : mSuggestedMembers) {

                contactIds.add(member.getContactId());
                rawContactIds.add(member.getRawContactId());
            }
            result.putExtra("ContactIdArray", contactIds);
            result.putExtra("RawContactIdArray", rawContactIds);
        }
        setResult(RESULT_OK, result);
        finish();

    }

    public void onItemCheckedStateChanged(ActionMode mode, int position,
            long id, boolean checked) {
        ListView listView = mListView;

        PickerMemberAdapter adapter = (PickerMemberAdapter) listView
                .getAdapter();
        final int checkedCount = listView.getCheckedItemCount();
        mSelectedContactsCount.setText(Integer.toString(checkedCount)
                +" "+getResources().getString(R.string.selected));

        SuggestedMember item = (SuggestedMember) adapter.getItem(position);
        if (checked) {
            if (!mSuggestedMembers.contains(item))
                mSuggestedMembers.add(item);
            adapter.addSelection(position);
        } else {
            mSuggestedMembers.remove(item);
            adapter.removeSelection(position);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onItemSelected(AdapterView<?> arg0, View arg1, int position,
            long arg3) {
        PickerMemberAdapter adapter = (PickerMemberAdapter) mListView
                .getAdapter();
        adapter.setSelectedPosition(position);
        adapter.notifyDataSetChanged();
        mListView.requestFocus();
        mListView.smoothScrollToPosition(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
        PickerMemberAdapter adapter = (PickerMemberAdapter) mListView
                .getAdapter();
        adapter.setSelectedPosition(-1);
        adapter.notifyDataSetChanged();
    }

}
