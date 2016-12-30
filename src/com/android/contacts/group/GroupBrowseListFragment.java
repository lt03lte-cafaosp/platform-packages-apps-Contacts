/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a Contribution
 */
/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.contacts.util.Constants;
import com.android.contacts.ContactsUtils;
import com.android.contacts.GroupListLoader;
import com.android.contacts.R;
import com.android.contacts.group.GroupBrowseListAdapter.GroupListItemViewCache;
import com.android.contacts.common.list.AutoScrollListView;

import com.android.contacts.GroupListLoaderPicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.CursorJoiner.Result;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.contacts.GroupListLoader;
import com.android.contacts.R;
import com.android.contacts.group.GroupBrowseListAdapter.GroupListItemViewCache;
import android.view.KeyEvent;
import com.android.contacts.common.list.AutoScrollListView;

/**
 * Fragment to display the list of groups.
 */
public class GroupBrowseListFragment extends Fragment implements
        OnFocusChangeListener, OnTouchListener,
        ListView.MultiChoiceModeListener {

    /**
     * Action callbacks that can be sent by a group list.
     */
    public interface OnGroupBrowserActionListener {

        /**
         * Opens the specified group for viewing.
         *
         * @param groupUri
         *            for the group that the user wishes to view.
         */
        void onViewGroupAction(Uri groupUri);

    }

    private static final String TAG = "GroupBrowseListFragment";

    private static final int LOADER_GROUPS = 1;

    private Context mContext;
    private Cursor mGroupListCursor;

    private boolean mSelectionToScreenRequested;

    private static final String EXTRA_KEY_GROUP_URI = "groups.groupUri";

    private View mRootView;
    private AutoScrollListView mListView;
    private TextView mEmptyView;
    private View mAddAccountsView;
    private View mAddAccountButton;
    private ProgressDialog mProgressDialog;

    private View mMultiSelectActionBarView;
    private TextView mSelectedContactsCount;
    private ArrayList<Long> mSuggestedGroupListItem = null;

    private GroupBrowseListAdapter mAdapter;
    private boolean mSelectionVisible;
    private Uri mSelectedGroupUri;

    private int mVerticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT;

    private OnGroupBrowserActionListener mListener;

    private int mPickerType = -1;
    private int mLimit = -1;
    int mCheckedCount = 0;
    Intent mIntent = null;
    Activity mActivity = null;

    String mlocale = null;

    float sLastFontScale = -1;

    // Handling system font change
    BroadcastReceiver mConfigChangeBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                if (isAdded() == false) {
                    return;
                }
                float fontScale = getResources().getConfiguration().fontScale;
                String locale = getResources().getConfiguration().locale
                        .toString();
                if (sLastFontScale != -1 && sLastFontScale != fontScale) {
                    if (mSuggestedGroupListItem != null) {
                        /*
                         * Finish the activity on detecting system font change.
                         * System re-draws the activity
                         */
                        mSuggestedGroupListItem.clear();
                        getActivity().finish();
                    }
                    sLastFontScale = fontScale;
                }
                if (!locale.equals(mlocale)) {
                    if (mSuggestedGroupListItem != null) {
                        /*
                         * Finish the activity on detecting system font change.
                         * System re-draws the activity
                         */
                        mSuggestedGroupListItem.clear();
                        getActivity().finish();
                    }
                }
            }
        }
    };

    public GroupBrowseListFragment() {
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mActivity = this.getActivity();
        mIntent = mActivity.getIntent();
        mPickerType = mIntent.getIntExtra("filter", -1);
        mLimit = mIntent.getIntExtra("limit", -1);
        mContext = mActivity;
        mlocale = mActivity.getResources().getConfiguration().locale.toString();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mSelectedGroupUri = savedInstanceState
                    .getParcelable(EXTRA_KEY_GROUP_URI);
            if (mSelectedGroupUri != null) {
                // The selection may be out of screen, if rotated from portrait
                // to landscape,
                // so ensure it's visible.
                mSelectionToScreenRequested = true;
            }
        }

        mRootView = inflater.inflate(R.layout.group_browse_list_fragment, null);
        mEmptyView = (TextView) mRootView.findViewById(R.id.empty);

        mAdapter = new GroupBrowseListAdapter(mContext);

        if(mPickerType == Constants.EMAIL_AND_PHONE_FILTER ||
                mPickerType == Constants.EMAIL_ONLY_FILTER){
            mAdapter.setPickerType(mPickerType);
        }
        mAdapter.setSelectionVisible(mSelectionVisible);
        mAdapter.setSelectedGroup(mSelectedGroupUri);

        mListView = (AutoScrollListView) mRootView.findViewById(R.id.list);
        mListView.setOnFocusChangeListener(this);
        mListView.setOnItemSelectedListener(mItemSelectedListener);
        mListView.setOnTouchListener(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
               if(!(mPickerType == Constants.EMAIL_AND_PHONE_FILTER ||
                            mPickerType == Constants.EMAIL_ONLY_FILTER)){
                    GroupListItemViewCache groupListItem = (GroupListItemViewCache) view.getTag();
                    if (groupListItem != null) {
                        viewGroup(groupListItem.getUri());
                    }
               } else{
                   mListView.setItemChecked(position, true);
               }
            }
        });

        mListView.setEmptyView(mEmptyView);
        configureVerticalScrollbar();
        mAddAccountsView = mRootView.findViewById(R.id.add_accounts);
        mAddAccountButton = mRootView.findViewById(R.id.add_account_button);
        mAddAccountButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                intent.putExtra(Settings.EXTRA_AUTHORITIES,
                        new String[] { ContactsContract.AUTHORITY });
                startActivity(intent);
            }
        });
        setAddAccountsVisibility(!ContactsUtils
                .areGroupWritableAccountsAvailable(mContext));

        return mRootView;
    }

    AdapterView.OnItemSelectedListener mItemSelectedListener = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> arg0, View arg1, int pos,
                long arg3) {
            mAdapter.setSelectedPosition(pos);
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
        }
    };

    public void setVerticalScrollbarPosition(int position) {
        mVerticalScrollbarPosition = position;
        if (mListView != null) {
            configureVerticalScrollbar();
        }
    }

    private void configureVerticalScrollbar() {
        mListView.setVerticalScrollbarPosition(mVerticalScrollbarPosition);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        int leftPadding = 0;
        int rightPadding = 0;
        if (mVerticalScrollbarPosition == View.SCROLLBAR_POSITION_LEFT) {
            leftPadding = mContext.getResources().getDimensionPixelOffset(
                    R.dimen.list_visible_scrollbar_padding);
        } else {
            rightPadding = mContext.getResources().getDimensionPixelOffset(
                    R.dimen.list_visible_scrollbar_padding);
        }
        mListView.setPadding(leftPadding, mListView.getPaddingTop(),
                rightPadding, mListView.getPaddingBottom());
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onStart() {
        getLoaderManager()
                .initLoader(LOADER_GROUPS, null, mGroupLoaderListener);
        super.onStart();
    }

    /**
     * The listener for the group meta data loader for all groups.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupLoaderListener = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            mEmptyView.setText(null);
            if ((mPickerType == Constants.EMAIL_AND_PHONE_FILTER || mPickerType == Constants.EMAIL_ONLY_FILTER)) {
                Log.i(TAG,"inside oncreate of groupbrowse list fragment ");
                return new GroupListLoaderPicker(mContext);
            } else {
                Log.i(TAG,"no picker selected");
                return new GroupListLoader(mContext);
            }
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mGroupListCursor = data;
            Log.i(TAG,"on load of cursor is "+mGroupListCursor.getCount());
            // If there's no selected group already and the cursor is valid,
            // then by default, select the
            // first group
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    bindGroupList();
                }
            });

        }

        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    private void bindGroupList() {
        mEmptyView.setText(R.string.noGroups);
        if (!(mPickerType == Constants.EMAIL_AND_PHONE_FILTER || mPickerType == Constants.EMAIL_ONLY_FILTER)) {
            setAddAccountsVisibility(!ContactsUtils
                    .areGroupWritableAccountsAvailable(mContext));
        } else {
            setAddAccountsVisibility(false);
        }
        if (mGroupListCursor == null) {
            return;
        }
        mAdapter.setCursor(mGroupListCursor);

        if (mSelectionToScreenRequested) {
            mSelectionToScreenRequested = false;
            requestSelectionToScreen();
        }

        mSelectedGroupUri = mAdapter.getSelectedGroup();
        if (mSelectionVisible && mSelectedGroupUri != null) {
            viewGroup(mSelectedGroupUri);
        }
    }

    public void setListener(OnGroupBrowserActionListener listener) {
        mListener = listener;
    }

    public void setSelectionVisible(boolean flag) {
        mSelectionVisible = flag;
        if (mAdapter != null) {
            mAdapter.setSelectionVisible(mSelectionVisible);
        }
    }

    private void setSelectedGroup(Uri groupUri) {
        mSelectedGroupUri = groupUri;
        mAdapter.setSelectedGroup(groupUri);
        mListView.invalidateViews();
    }

    private void viewGroup(Uri groupUri) {
        setSelectedGroup(groupUri);
        if (mListener != null)
            mListener.onViewGroupAction(groupUri);
    }

    public void setSelectedUri(Uri groupUri) {
        viewGroup(groupUri);
        mSelectionToScreenRequested = true;
    }

    protected void requestSelectionToScreen() {
        if (!mSelectionVisible) {
            return; // If selection isn't visible we don't care.
        }
        int selectedPosition = mAdapter.getSelectedGroupPosition();
        if (selectedPosition != -1) {
            mListView.requestPositionToScreen(selectedPosition, true );
        }
    }

    private void hideSoftKeyboard() {
        if (mContext == null) {
            return;
        }
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager) mContext
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mListView.getWindowToken(),
                0);
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (view == mListView && hasFocus) {
            hideSoftKeyboard();
        }
    }

    /**
     * Dismisses the soft keyboard when the list is touched.
     */
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view == mListView) {
            hideSoftKeyboard();
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(EXTRA_KEY_GROUP_URI, mSelectedGroupUri);
    }

    public void setAddAccountsVisibility(boolean visible) {
        if (mAddAccountsView != null) {
            mAddAccountsView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private Handler messageHandler = new Handler() {

        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                if (mProgressDialog != null)
                    mProgressDialog.dismiss();
            } else if (msg.what == 1) {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                mActivity.setResult(Activity.RESULT_OK, mIntent);
                mActivity.finish();
            }
        }
    };

    /*
     * Send message to the group
     */
    public void sendMessageGroup() {
        if (mListView.getCount() == 0) {
            Toast.makeText(mContext, getString(R.string.noGroups),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int position = -1;
        mSuggestedGroupListItem = new ArrayList<Long>();
        if (mListView.hasFocus()) {
            position = mAdapter.getSelectedPosition();
            if (position == -1) {
                position = 0;
            }
        }
        final GroupListItem groupListItem = mAdapter.getItem(position);
        mSuggestedGroupListItem.add(groupListItem.getGroupId());
        if (mSuggestedGroupListItem == null
                || mSuggestedGroupListItem.isEmpty()) {
            Toast.makeText(
                    mContext,
                    mContext.getResources().getString(R.string.no_data_message),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        mProgressDialog = ProgressDialog.show(mContext, "",
                getString(R.string.please_wait));

        new Thread() {
            public void run() {
                Looper.prepare();
                String finalRecipients = null;
                for (Long item : mSuggestedGroupListItem) {
                    String recipients = new String();
                    recipients = GroupUtil.getGroupMessageRecipients(item,
                            mContext);
                    if (recipients == null || recipients.trim().isEmpty()) {
                        continue;
                    } else {
                        if (finalRecipients == null
                                || finalRecipients.trim().isEmpty()
                                || finalRecipients.isEmpty()) {
                            finalRecipients = new String();
                            finalRecipients = finalRecipients
                                    .concat(recipients);
                        } else {
                            finalRecipients = finalRecipients.concat(",");
                            finalRecipients = finalRecipients
                                    .concat(recipients);
                        }
                        recipients = null;
                    }
                }
                mSuggestedGroupListItem.clear();
                messageHandler.sendEmptyMessage(0);
                if (finalRecipients != null && !finalRecipients.isEmpty()) {
                    GroupUtil.sendMessage(finalRecipients, mContext);
                    finalRecipients = null;
                } else {
                    Toast.makeText(
                            mContext,
                            mContext.getResources().getString(
                                    R.string.no_data_message),
                            Toast.LENGTH_SHORT).show();
                }

                Looper.loop();
            }
        }.start();
    }

    /*
     * Share the Focused group
     */
    public void shareGroup() {
        if (mListView.getCount() == 0) {
            Toast.makeText(mContext, getString(R.string.no_groups_share),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int position = -1;
        mSuggestedGroupListItem = new ArrayList<Long>();
        if (mListView.hasFocus()) {
            position = mAdapter.getSelectedPosition();
            if (position == -1) {
                position = 0;
            }
        }
        final GroupListItem groupListItem = mAdapter.getItem(position);
        mSuggestedGroupListItem.add(groupListItem.getGroupId());
        if (mSuggestedGroupListItem == null
                || mSuggestedGroupListItem.isEmpty()) {
            Toast.makeText(mContext,
                    mContext.getResources().getString(R.string.no_data_share),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        mProgressDialog = ProgressDialog.show(mContext, "",
                getString(R.string.please_wait));

        new Thread() {
            public void run() {
                // Send Message to Group code will go here
                Looper.prepare();
                StringBuilder finalRecipients = null;
                for (Long item : mSuggestedGroupListItem) {
                    StringBuilder recipients = new StringBuilder();
                    recipients = GroupUtil
                            .getShareGroupContacts(item, mContext);
                    if (recipients == null || (recipients.length() == 0)) {
                        continue;
                    } else {
                        if (finalRecipients == null
                                || finalRecipients.length() == 0) {
                            finalRecipients = new StringBuilder();
                            finalRecipients = finalRecipients
                                    .append(recipients);
                        } else {
                            finalRecipients = finalRecipients.append(":");
                            finalRecipients = finalRecipients
                                    .append(recipients);
                        }
                        recipients = null;
                    }
                }
                mSuggestedGroupListItem.clear();
                messageHandler.sendEmptyMessage(0);
                if (finalRecipients != null && finalRecipients.length() != 0) {
                    GroupUtil.shareContacts(finalRecipients, mContext);
                    finalRecipients = null;
                } else {
                    Toast.makeText(
                            mContext,
                            mContext.getResources().getString(
                                    R.string.no_data_share), Toast.LENGTH_SHORT)
                            .show();
                }

                Looper.loop();
            }
        }.start();
    }

    /*
     * Deletes the focused group
     */
    public void deleteGroup() {
        if (mListView.getCount() == 0) {
            Toast.makeText(mContext, getString(R.string.no_groups_delete), Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        int position = -1;
        if (mListView.hasFocus()) {
            position = mAdapter.getSelectedPosition();
            if (position == -1) {
                position = 0;
            }
        }

        final GroupListItem groupListItem = mAdapter.getItem(position);
        String accountType = groupListItem.getAccountType();
        final GroupBrowseListAdapter delete_adapter = (GroupBrowseListAdapter) mListView
                .getAdapter();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.groups_delete_title).setIcon(
                R.drawable.ic_dialog_alert_holo_light);
        String message = getActivity().getString(
                R.string.delete_group_dialog_message, groupListItem.getTitle());
        builder.setMessage(message)
                .setCancelable(true)
                .setPositiveButton(getString(R.string.common_btn_ok),
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int id) {
                                boolean containIceGroup = false;
                                boolean containPhoneGroup = false;
                                Cursor cursor = null;
                                try {
                                    boolean isReadOnlyContactDeletion = false;

                                    long curr_group_id = groupListItem
                                            .getGroupId();
                                    final String[] GROUP_PROJECTION = new String[] {
                                            ContactsContract.Groups._ID,
                                            ContactsContract.Groups.TITLE,
                                            ContactsContract.Groups.ACCOUNT_TYPE,
                                            ContactsContract.Groups.GROUP_IS_READ_ONLY };
                                    cursor = mContext
                                            .getContentResolver()
                                            .query(ContactsContract.Groups.CONTENT_URI,
                                                    GROUP_PROJECTION,
                                                    "_id=" + curr_group_id,
                                                    null,
                                                    ContactsContract.Groups.TITLE);
                                    if ((null != cursor)
                                            && (cursor.getCount() > 0)) {
                                        while (cursor.moveToNext()) {
                                            String existGName = cursor.getString(cursor
                                                    .getColumnIndex(ContactsContract.Groups.TITLE));
                                            String accType = cursor.getString(cursor
                                                    .getColumnIndex(ContactsContract.Groups.ACCOUNT_TYPE));
                                            boolean isReadOnly = (cursor.getInt(cursor
                                                    .getColumnIndex(ContactsContract.Groups.GROUP_IS_READ_ONLY)) == 1);
                                            if (!isReadOnly) {
                                                mContext
                                                        .getContentResolver()
                                                        .delete(ContactsContract.Groups.CONTENT_URI,
                                                                "_id="
                                                                        + curr_group_id,
                                                                null);
                                                mContext
                                                        .getContentResolver()
                                                        .notifyChange(
                                                                ContactsContract.Groups.CONTENT_URI,
                                                                null);
                                                delete_adapter
                                                        .notifyDataSetChanged();
                                                delete_adapter
                                                        .notifyDataSetInvalidated();
                                            } else {
                                                isReadOnlyContactDeletion = true;
                                            }
                                        }
                                    }
                                    if (isReadOnlyContactDeletion) {
                                        Toast.makeText(
                                                mContext,
                                                getString(R.string.read_only_group_deletion_message),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                } finally {
                                    if (null != cursor)
                                        cursor.close();
                                }

                            }
                        })
                .setNegativeButton(getString(R.string.common_btn_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

        return true;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {

        MenuInflater inflater = new MenuInflater(mContext);
        mAdapter.setMulitSelectMode(true);

        if (mMultiSelectActionBarView == null) {
            mMultiSelectActionBarView = (ViewGroup) LayoutInflater.from(
                    mContext).inflate(
                    R.layout.group_list_multi_select_actionbar, null);

            mSelectedContactsCount = (TextView) mMultiSelectActionBarView
                    .findViewById(R.id.selected_contacts_count);

        }
        mode.setCustomView(mMultiSelectActionBarView);
        ((TextView) mMultiSelectActionBarView.findViewById(R.id.title))
                .setText(getString(R.string.selected));
        mSuggestedGroupListItem = new ArrayList<Long>();

        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            getActivity().setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            getActivity().setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }

        getActivity().registerReceiver(mConfigChangeBroadcastReceiver,
                new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
        sLastFontScale = getResources().getConfiguration().fontScale;
        return true;

    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mAdapter.setMulitSelectMode(false);
        mAdapter.deSelectAll();
        mSuggestedGroupListItem.clear();
        mCheckedCount = 0;
        getActivity().setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_USER);

        try {
            if (mConfigChangeBroadcastReceiver != null) {
                getActivity()
                        .unregisterReceiver(mConfigChangeBroadcastReceiver);

            }
        } catch (Exception e) {
            Log.i(TAG, "Exception: " + e);
        }
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        if (mMultiSelectActionBarView == null) {
            ViewGroup v = (ViewGroup) LayoutInflater.from(mContext).inflate(
                    R.layout.group_list_multi_select_actionbar, null);
            mode.setCustomView(v);

            mSelectedContactsCount = (TextView) v
                    .findViewById(R.id.selected_contacts_count);
        }
        int checkedItemCount = mListView.getCheckedItemCount();
        int listItemCount = mListView.getCount();
        MenuItem itemSelectAll = menu.findItem(R.id.select_all);

        if (null != itemSelectAll) {
            if (checkedItemCount == listItemCount) {
                itemSelectAll.setEnabled(false);
            } else {
                itemSelectAll.setEnabled(true);
            }
        }
        return true;
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position,
            long id, boolean checked) {

        ListView listView = mListView;

        GroupBrowseListAdapter adapter = (GroupBrowseListAdapter) mListView
                .getAdapter();
        if (mPickerType == Constants.EMAIL_AND_PHONE_FILTER
                || mPickerType == Constants.EMAIL_ONLY_FILTER) {
            GroupListItem item = (GroupListItem) mAdapter.getItem(position);
            if (checked == true) {
                mCheckedCount = mCheckedCount + item.getMemberCount();
                if (mLimit != -1 && mCheckedCount > mLimit) {
                    Toast.makeText(
                            mContext,
                            mContext.getResources().getString(
                                    R.string.group_max_recipient_limit_reached,
                                    mLimit), Toast.LENGTH_LONG).show();
                    mListView.setItemChecked(position, false);
                    return;
                }
            } else {
                mCheckedCount = mCheckedCount - item.getMemberCount();
            }
        } else {
            mCheckedCount = listView.getCheckedItemCount();
        }
        mSelectedContactsCount.setText(Integer.toString(mCheckedCount));

        GroupListItem item = (GroupListItem) mAdapter.getItem(position);
        if (checked) {
            if (!mSuggestedGroupListItem.contains(item.getGroupId())) {
                mSuggestedGroupListItem.add(item.getGroupId());
            }
            adapter.addSelection(position);
        } else {
            mSuggestedGroupListItem.remove(item.getGroupId());
            adapter.removeSelection(position);
        }
    }

}
