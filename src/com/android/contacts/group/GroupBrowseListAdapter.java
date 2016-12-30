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
import java.util.List;
import java.util.ResourceBundle.Control;

import com.android.contacts.GroupListLoader;
import com.android.contacts.GroupListLoaderPicker;
import com.android.contacts.util.Constants;
import com.android.internal.util.Objects;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.contacts.GroupListLoader;
import com.android.contacts.R;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.util.Constants;

/**
 * Adapter to populate the list of groups.
 */
public class GroupBrowseListAdapter extends BaseAdapter {

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final AccountTypeManager mAccountTypeManager;

    private Cursor mCursor;

    private boolean mSelectionVisible;
    private Uri mSelectedGroupUri;

    private ProgressDialog mProgressDialog;

    private int mGroupCount;
    private boolean mIsMultiSelectMode = false;

    private int mPickerType = -1;
    private int mPosition = -1;

    private ArrayList<Integer> mSelectedList = new ArrayList<Integer>();

    public GroupBrowseListAdapter(Context context) {
        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mAccountTypeManager = AccountTypeManager.getInstance(mContext);
    }

    public void setPickerType(int picker_type) {
        mPickerType = picker_type;
    }

    public void setCursor(Cursor cursor) {
        mCursor = cursor;

        // If there's no selected group already and the cursor is valid, then by
        // default, select the
        // first group
        if (mSelectedGroupUri == null && cursor != null
                && cursor.getCount() > 0) {
            GroupListItem firstItem = getItem(0);
            long groupId = (firstItem == null) ? 0 : firstItem.getGroupId();
            mSelectedGroupUri = getGroupUriFromId(groupId);
        }

        notifyDataSetChanged();
    }

    public int getSelectedGroupPosition() {
        if (mSelectedGroupUri == null || mCursor == null
                || mCursor.getCount() == 0) {
            return -1;
        }

        int index = 0;
        mCursor.moveToPosition(-1);
        while (mCursor.moveToNext()) {
            long groupId = mCursor.getLong(GroupListLoader.GROUP_ID);
            Uri uri = getGroupUriFromId(groupId);
            if (mSelectedGroupUri.equals(uri)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public void setSelectionVisible(boolean flag) {
        mSelectionVisible = flag;
    }

    public void setSelectedGroup(Uri groupUri) {
        mSelectedGroupUri = groupUri;
    }

    private boolean isSelectedGroup(Uri groupUri) {
        return mSelectedGroupUri != null && mSelectedGroupUri.equals(groupUri);
    }

    public Uri getSelectedGroup() {
        return mSelectedGroupUri;
    }

    @Override
    public int getCount() {
        return (mCursor == null || mCursor.isClosed()) ? 0 : mCursor.getCount();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Creates the content URI for Query
     *
     * @return uri
     */
    private Uri createUri() {
        Uri uri = Data.CONTENT_URI;
        uri = uri
                .buildUpon()
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT)).build();
        return uri;
    }

    /**
     * Prepare the selection part of the Query
     *
     * @return selection of Query
     */
    private String createSelection() {
        StringBuilder selection = new StringBuilder();
        selection.append(Data.MIMETYPE + "=?" + " AND "
                + GroupMembership.GROUP_ROW_ID + "=?");
        return selection.toString();
    }

    /**
     * Prepare the Selection argument for the Query
     *
     * @param groupId
     *            Group ID
     * @return selection argument for query
     */
    private String[] createSelectionArgs(long groupId) {
        List<String> selectionArgs = new ArrayList<String>();
        selectionArgs.add(GroupMembership.CONTENT_ITEM_TYPE);
        selectionArgs.add(String.valueOf(groupId));
        return selectionArgs.toArray(new String[0]);
    }

    @Override
    public GroupListItem getItem(int position) {
        if (mCursor == null || mCursor.isClosed()
                || !mCursor.moveToPosition(position)) {
            return null;
        }
        String accountName = mCursor.getString(GroupListLoader.ACCOUNT_NAME);
        String accountType = mCursor.getString(GroupListLoader.ACCOUNT_TYPE);
        String dataSet = mCursor.getString(GroupListLoader.DATA_SET);
        final long groupId = mCursor.getLong(GroupListLoader.GROUP_ID);
        String title = mCursor.getString(GroupListLoader.TITLE);

        int memberCount = mCursor.getInt(GroupListLoader.MEMBER_COUNT);
        if (mPickerType == Constants.EMAIL_AND_PHONE_FILTER) {
            memberCount = mCursor
                    .getInt(GroupListLoaderPicker.EMAIL_PHONE_COUNT);
        } else if (mPickerType == Constants.EMAIL_ONLY_FILTER) {
            memberCount = mCursor.getInt(GroupListLoaderPicker.EMAIL_COUNT);
        }

        // Figure out if this is the first group for this account name / account
        // type pair by
        // checking the previous entry. This is to determine whether or not we
        // need to display an
        // account header in this item.
        int previousIndex = position - 1;
        boolean isFirstGroupInAccount = true;
        if (previousIndex >= 0 && mCursor.moveToPosition(previousIndex)) {
            String previousGroupAccountName = mCursor
                    .getString(GroupListLoader.ACCOUNT_NAME);
            String previousGroupAccountType = mCursor
                    .getString(GroupListLoader.ACCOUNT_TYPE);
            String previousGroupDataSet = mCursor
                    .getString(GroupListLoader.DATA_SET);

            if (accountName.equals(previousGroupAccountName)
                    && accountType.equals(previousGroupAccountType)
                    && Objects.equal(dataSet, previousGroupDataSet)) {
                isFirstGroupInAccount = false;
            }
        }

        return new GroupListItem(accountName, accountType, dataSet, groupId,
                title, isFirstGroupInAccount, memberCount);
    }

    private Handler messageHandler = new Handler() {

        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                notifyDataSetChanged();
            } else {
                if (mProgressDialog != null)
                    mProgressDialog.dismiss();
            }
        }

    };

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        GroupListItem entry = getItem(position);
        View result;
        GroupListItemViewCache viewCache;
        if (convertView != null) {
            result = convertView;
            viewCache = (GroupListItemViewCache) result.getTag();
        } else {
            result = mLayoutInflater.inflate(R.layout.group_browse_list_item,
                    parent, false);
            viewCache = new GroupListItemViewCache(result);
            result.setTag(viewCache);
        }

        // Add a header if this is the first group in an account and hide the
        // divider
        if (entry.isFirstGroupInAccount()) {
            bindHeaderView(entry, viewCache, position);
            viewCache.accountHeader.setVisibility(View.VISIBLE);
            viewCache.divider.setVisibility(View.GONE);
            if (position == 0) {
                // Have the list's top padding in the first header.
                //
                // This allows the ListView to show correct fading effect on
                // top.
                // If we have topPadding in the ListView itself, an
                // inappropriate padding is
                // inserted between fading items and the top edge.
                viewCache.accountHeaderExtraTopPadding
                        .setVisibility(View.VISIBLE);
            } else {
                viewCache.accountHeaderExtraTopPadding.setVisibility(View.GONE);
            }
        } else {
            viewCache.accountHeader.setVisibility(View.GONE);
            viewCache.divider.setVisibility(View.VISIBLE);
            viewCache.accountHeaderExtraTopPadding.setVisibility(View.GONE);
        }

        final View.OnClickListener clickMessage = new View.OnClickListener() {
            public void onClick(View v) {
                mProgressDialog = ProgressDialog.show(mContext, "",
                        mContext.getString(R.string.please_wait));

                new Thread() {
                    public void run() {
                        // Send Message to Group code will go here
                        Looper.prepare();
                        GroupListItem entry = getItem(position);
                        String recipients = GroupUtil
                                .getGroupMessageRecipients(entry.getGroupId(),
                                        mContext);
                        messageHandler.sendEmptyMessage(0);
                        if (recipients != null) {
                            GroupUtil.sendMessage(recipients, mContext);
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
        };
        viewCache.message.setOnClickListener(clickMessage);
        final View.OnClickListener clickShare = new View.OnClickListener() {
            public void onClick(View v) {
                // TODO Auto-generated method stub
                mProgressDialog = ProgressDialog.show(mContext, "",
                        mContext.getString(R.string.please_wait));
                new Thread() {
                    public void run() {
                        Looper.prepare();
                        GroupListItem entry = getItem(position);
                        StringBuilder uriList = GroupUtil
                                .getShareGroupContacts(entry.getGroupId(),
                                        mContext);
                        messageHandler.sendEmptyMessage(0);
                        if (uriList != null) {
                            GroupUtil.shareContacts(uriList, mContext);
                        } else {
                            Toast.makeText(mContext, R.string.no_data_share,
                                    Toast.LENGTH_SHORT).show();
                        }
                        Looper.loop();
                    }
                }.start();
            }
        };
        viewCache.share.setOnClickListener(clickShare);
        // When multiselect mode starts disable icons and dividers should
        // disappear.
        if (mIsMultiSelectMode
                || mPickerType == Constants.EMAIL_AND_PHONE_FILTER
                || mPickerType == Constants.EMAIL_ONLY_FILTER) {
            viewCache.share.setVisibility(View.GONE);
            viewCache.message.setVisibility(View.GONE);
            viewCache.divider2.setVisibility(View.GONE);
        } else {
            viewCache.share.setVisibility(View.VISIBLE);
            viewCache.message.setVisibility(View.VISIBLE);
            viewCache.divider2.setVisibility(View.VISIBLE);
        }

        // Bind the group data
        Uri groupUri = getGroupUriFromId(entry.getGroupId());
        String memberCountString;
        memberCountString =
                    mContext.getResources().getQuantityString(
                            R.plurals.group_list_num_contacts_in_group,
                            entry.getMemberCount(), entry.getMemberCount());
        viewCache.setUri(groupUri);
        viewCache.groupTitle.setText(entry.getTitle());
        viewCache.groupMemberCount.setText(memberCountString);

        if (mSelectionVisible) {
            result.setActivated(isSelectedGroup(groupUri));
        }
        viewCache.share.setVisibility(View.GONE);
        viewCache.message.setVisibility(View.GONE);
        viewCache.divider2.setVisibility(View.GONE);
        return result;
    }

    private void bindHeaderView(GroupListItem entry,
            GroupListItemViewCache viewCache, int position) {
        // : To find number of groups for the account.
        String accountName = entry.getAccountName();
        String accountType1 = entry.getAccountType();
        int count = 1; // : Because first is always the header
        if (accountName != null || accountType1 != null) {
            GroupListItem nextEntry = getItem(++position);
            while (nextEntry != null
                    && (accountName.equals(nextEntry.getAccountName()) && accountType1
                            .equals(nextEntry.getAccountType()))) {
                nextEntry = getItem(++position);
                count++;
            }
        }
        AccountType accountType = mAccountTypeManager.getAccountType(
                entry.getAccountType(), entry.getDataSet());
        viewCache.accountType.setText(accountType.getDisplayLabel(mContext)
                .toString());
        viewCache.accountName.setText(entry.getAccountName());

        Resources res = mContext.getResources();
        String groupsFound = res.getQuantityString(R.plurals.numberOfGroups,
                count, count);
    }

    private static Uri getGroupUriFromId(long groupId) {
        return ContentUris.withAppendedId(Groups.CONTENT_URI, groupId);
    }

    public void addSelection(Integer position) {
        if (!mSelectedList.contains(position))
            mSelectedList.add(position);
    }

    public void removeSelection(Integer position) {
        mSelectedList.remove(position);
    }

    public void deSelectAll() {
        mSelectedList.clear();
    }

    public ArrayList<Integer> getSelection() {
        return mSelectedList;
    }

    public void setMulitSelectMode(boolean flag) {
        mIsMultiSelectMode = flag;
    }

    public void setSelectedPosition(int position){
        mPosition = position;
    }

    public int getSelectedPosition(){
        return mPosition;
    }

    /**
     * Cache of the children views of a contact detail entry represented by a
     * {@link GroupListItem}
     */
    public static class GroupListItemViewCache {
        public final TextView accountType;
        public final TextView accountName;
        public final TextView groupTitle;
        public final TextView groupMemberCount;
        public final View accountHeader;
        public final View accountHeaderExtraTopPadding;
        public final View divider;
        private Uri mUri;
        public final ImageView message;
        public final ImageView share;
        public final View divider2;
        public final TextView groupCount;

        public GroupListItemViewCache(View view) {
            accountType = (TextView) view.findViewById(R.id.account_type);
            accountName = (TextView) view.findViewById(R.id.account_name);
            groupTitle = (TextView) view.findViewById(R.id.label);
            groupMemberCount = (TextView) view.findViewById(R.id.count);
            accountHeader = view.findViewById(R.id.group_list_header);
            accountHeaderExtraTopPadding = view
                    .findViewById(R.id.header_extra_top_padding);
            divider = view.findViewById(R.id.divider);
            divider2 = view.findViewById(R.id.divider2);
            message = (ImageView) view.findViewById(R.id.sendMessage);
            share = (ImageView) view.findViewById(R.id.share);
            groupCount = (TextView) view.findViewById(R.id.group_count);
        }

        public void setUri(Uri uri) {
            mUri = uri;
        }

        public Uri getUri() {
            return mUri;
        }
    }
}
