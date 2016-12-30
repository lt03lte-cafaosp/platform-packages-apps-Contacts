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
 * limitations under the License
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

import java.lang.reflect.Array;
import java.util.ArrayList;
import com.android.contacts.common.util.ContactLoaderUtils;
import com.android.contacts.ContactSaveService;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContactsEntity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.contacts.GroupMemberLoader;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.interactions.GroupDeletionDialogFragment;
import com.android.contacts.common.list.ContactTileAdapter;
import com.android.contacts.common.list.ContactTileView;
import com.android.contacts.list.GroupMemberTileAdapter;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.vcard.NotificationImportExportListener;
import com.android.contacts.detail.ContactLoaderFragment;
import com.android.contacts.common.list.ContactEntry;
/**
 * Displays the details of a group and shows a list of actions possible for the
 * group.
 */
public class GroupDetailFragment extends Fragment implements OnScrollListener {

    public static interface Listener {
        /**
         * The group title has been loaded
         */
        public void onGroupTitleUpdated(String title);

        /**
         * The number of group members has been determined
         */
        public void onGroupSizeUpdated(String size);

        /**
         * The account type and dataset have been determined.
         */
        public void onAccountTypeUpdated(String accountTypeString,
                String dataSet);

        /**
         * User decided to go to Edit-Mode
         */
        public void onEditRequested(Uri groupUri);

        /**
         * Contact is selected and should launch details page
         */
        public void onContactSelected(Uri contactUri);
    }

    private static final String TAG = "GroupDetailFragment";

    private static final int LOADER_METADATA = 0;
    private static final int LOADER_MEMBERS = 1;
    private static final int MENU_ADD = 20;


    private Context mContext;

    private View mRootView;
    private ViewGroup mGroupSourceViewContainer;
    private View mGroupSourceView;
    private TextView mGroupTitle;
    private TextView mGroupSize;
    private ListView mMemberListView;
    private View mEmptyView;

    private Listener mListener;

    private ContactTileAdapter mAdapter;
    private ContactPhotoManager mPhotoManager;
    private AccountTypeManager mAccountTypeManager;

    private Uri mGroupUri;
    private long mGroupId;
    private String mGroupName;
    private String mAccountTypeString;
    private String mDataSet;
    private boolean mIsReadOnly;
    private boolean mIsMembershipEditable;

    private boolean mShowGroupActionInActionBar;
    private boolean mOptionsMenuGroupDeletable;
    private boolean mOptionsMenuGroupEditable;
    private boolean mCloseActivityAfterDelete;
    private ProgressDialog mProgressDialog;
    NotificationImportExportListener mNotificationImportExportListener;

    /**
     * Progress dialog to be shown while retrieving contact-ids for selected
     * group.
     */
    private ProgressDialog mGroupIdsProgressDialog = null;

    public GroupDetailFragment() {
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mAccountTypeManager = AccountTypeManager.getInstance(mContext);

        Resources res = getResources();
        int columnCount = res.getInteger(R.integer.contact_tile_column_count);

        mAdapter = new GroupMemberTileAdapter(activity, mContactTileListener,
                columnCount);

        configurePhotoLoader();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (mGroupIdsProgressDialog != null
                && mGroupIdsProgressDialog.isShowing() == true) {
            mGroupIdsProgressDialog.dismiss();
            mGroupIdsProgressDialog = null;
        }
        mContext = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedState) {
        setHasOptionsMenu(true);
        mRootView = inflater.inflate(R.layout.group_detail_fragment, container,
                false);
        mGroupTitle = (TextView) mRootView.findViewById(R.id.group_title);
        mGroupSize = (TextView) mRootView.findViewById(R.id.group_size);
        mGroupSourceViewContainer = (ViewGroup) mRootView
                .findViewById(R.id.group_source_view_container);
        mEmptyView = mRootView.findViewById(android.R.id.empty);
        mMemberListView = (ListView) mRootView.findViewById(android.R.id.list);
        mMemberListView.setItemsCanFocus(true);
        mMemberListView.setAdapter(mAdapter);
        mMemberListView.setFocusable(true);
        mNotificationImportExportListener = new NotificationImportExportListener(getActivity());
        return mRootView;
    }

    public void loadGroup(Uri groupUri) {
        mGroupUri = groupUri;
        startGroupMetadataLoader();
    }

    public void setQuickContact(boolean enableQuickContact) {
        mAdapter.enableQuickContact(false);
    }

    private void configurePhotoLoader() {
        if (mContext != null) {
            if (mPhotoManager == null) {
                mPhotoManager = ContactPhotoManager.getInstance(mContext);
            }
            if (mMemberListView != null) {
                mMemberListView.setOnScrollListener(this);
            }
            if (mAdapter != null) {
                mAdapter.setPhotoLoader(mPhotoManager);
            }
        }
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    public void setShowGroupSourceInActionBar(boolean show) {
        mShowGroupActionInActionBar = show;
    }

    public Uri getGroupUri() {
        return mGroupUri;
    }

    /**
     * Start the loader to retrieve the metadata for this group.
     */
    private void startGroupMetadataLoader() {
        getLoaderManager().restartLoader(LOADER_METADATA, null,
                mGroupMetadataLoaderListener);
    }

    /**
     * Start the loader to retrieve the list of group members.
     */
    private void startGroupMembersLoader() {
        getLoaderManager().restartLoader(LOADER_MEMBERS, null,
                mGroupMemberListLoaderListener);
    }

    private final ContactTileView.Listener mContactTileListener = new ContactTileView.Listener() {

        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            mListener.onContactSelected(contactUri);
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            // No need to call phone number directly from People app.
            Log.w(TAG, "unexpected invocation of onCallNumberDirectly()");
        }

        @Override
        public int getApproximateTileWidth() {
            return getView().getWidth() / mAdapter.getColumnCount();
        }
    };

    /**
     * The listener for the group metadata loader.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupMetadataLoaderListener = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(mContext, mGroupUri);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            data.moveToPosition(-1);
            if (data.moveToNext()) {
                boolean deleted = data.getInt(GroupMetaDataLoader.DELETED) == 1;
                if (!deleted) {
                    bindGroupMetaData(data);

                    // Retrieve the list of members
                    startGroupMembersLoader();
                    return;
                }
            }
            updateSize(-1);
            updateTitle(null);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    /**
     * The listener for the group members list loader
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupMemberListLoaderListener = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return GroupMemberLoader.constructLoaderForGroupDetailQuery(
                    mContext, mGroupId);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            updateSize(data.getCount());
            mAdapter.setContactCursor(data);
            getActivity().invalidateOptionsMenu();
            mMemberListView.setEmptyView(mEmptyView);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    private void bindGroupMetaData(Cursor cursor) {
        cursor.moveToPosition(-1);
        if (cursor.moveToNext()) {
            mAccountTypeString = cursor
                    .getString(GroupMetaDataLoader.ACCOUNT_TYPE);
            mDataSet = cursor.getString(GroupMetaDataLoader.DATA_SET);
            mGroupId = cursor.getLong(GroupMetaDataLoader.GROUP_ID);
            mGroupName = cursor.getString(GroupMetaDataLoader.TITLE);
            mIsReadOnly = cursor.getInt(GroupMetaDataLoader.IS_READ_ONLY) == 1;
            updateTitle(mGroupName);
            // Must call invalidate so that the option menu will get updated
            getActivity().invalidateOptionsMenu();

            final String accountTypeString = cursor
                    .getString(GroupMetaDataLoader.ACCOUNT_TYPE);
            final String dataSet = cursor
                    .getString(GroupMetaDataLoader.DATA_SET);
            updateAccountType(accountTypeString, dataSet);
        }
    }

    private void updateTitle(String title) {
        if (mGroupTitle != null) {
            mGroupTitle.setText(title);
        } else {
            mListener.onGroupTitleUpdated(title);
        }
    }

    /**
     * Display the count of the number of group members.
     *
     * @param size
     *            of the group (can be -1 if no size could be determined)
     */
    private void updateSize(int size) {
        String groupSizeString;
        if (size == -1) {
            groupSizeString = null;
        } else {
            AccountType accountType = mAccountTypeManager.getAccountType(
                    mAccountTypeString, mDataSet);

            CharSequence accType = accountType.getDisplayLabel(mContext);
            String groupSizeTemplateString;
            groupSizeTemplateString = getResources().getQuantityString(
                        R.plurals.num_contacts_in_group, size);
            groupSizeString = String.format(groupSizeTemplateString, size,
                        accType);
        }

        if (mGroupSize != null) {
            mGroupSize.setText(groupSizeString);
        } else {
            mListener.onGroupSizeUpdated(groupSizeString);
        }
    }

    /**
     * Once the account type, group source action, and group source URI have
     * been determined (based on the result from the {@link Loader}), then we
     * can display this to the user in 1 of 2 ways depending on screen size and
     * orientation: either as a button in the action bar or as a button in a
     * static header on the page. We also use isGroupMembershipEditable() of
     * accountType to determine whether or not we should display the Edit option
     * in the Actionbar.
     */
    private void updateAccountType(final String accountTypeString,
            final String dataSet) {
        final AccountTypeManager manager = AccountTypeManager
                .getInstance(getActivity());
        final AccountType accountType = manager.getAccountType(
                accountTypeString, dataSet);

        mIsMembershipEditable = accountType.isGroupMembershipEditable();

        // If the group action should be shown in the action bar, then pass the
        // data to the
        // listener who will take care of setting up the view and click
        // listener. There is nothing
        // else to be done by this {@link Fragment}.
        if (mShowGroupActionInActionBar) {
            mListener.onAccountTypeUpdated(accountTypeString, dataSet);
            return;
        }

        // Otherwise, if the {@link Fragment} needs to create and setup the
        // button, then first
        // verify that there is a valid action.
        if (!TextUtils.isEmpty(accountType.getViewGroupActivity())) {
            if (mGroupSourceView == null) {
                mGroupSourceView = GroupDetailDisplayUtils
                        .getNewGroupSourceView(mContext);
                // Figure out how to add the view to the fragment.
                // If there is a static header with a container for the group
                // source view, insert
                // the view there.
                if (mGroupSourceViewContainer != null) {
                    mGroupSourceViewContainer.addView(mGroupSourceView);
                }
            }

            // Rebind the data since this action can change if the loader
            // returns updated data
            mGroupSourceView.setVisibility(View.VISIBLE);
            GroupDetailDisplayUtils.bindGroupSourceView(mContext,
                    mGroupSourceView, accountTypeString, dataSet);
            mGroupSourceView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Uri uri = ContentUris.withAppendedId(
                            Groups.CONTENT_URI, mGroupId);
                    final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.setClassName(accountType.syncAdapterPackageName,
                            accountType.getViewGroupActivity());
                    startActivity(intent);
                }
            });
        } else if (mGroupSourceView != null) {
            mGroupSourceView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
            mPhotoManager.pause();
        } else {
            mPhotoManager.resume();
        }
    }
    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
         inflater.inflate(R.menu.view_group, menu);
    }

    public boolean isOptionsMenuChanged() {
        return mOptionsMenuGroupDeletable != isGroupDeletable()
                && mOptionsMenuGroupEditable != isGroupEditableAndPresent();
    }

    public boolean isGroupDeletable() {
        return mGroupUri != null && !mIsReadOnly;
    }

    public boolean isGroupEditableAndPresent() {
        return mGroupUri != null
                && mIsMembershipEditable;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuGroupDeletable = isGroupDeletable() && isVisible();
        mOptionsMenuGroupEditable = isGroupEditableAndPresent() && isVisible();

        final MenuItem editMenu = menu.findItem(R.id.menu_edit_group);
        editMenu.setVisible(mOptionsMenuGroupEditable);

        final MenuItem deleteMenu = menu.findItem(R.id.menu_delete_group);
        deleteMenu.setVisible(mOptionsMenuGroupDeletable);

        final MenuItem messageMenu = menu
                .findItem(R.id.menu_send_message_group);
        messageMenu.setVisible(false);

        final MenuItem shareMenu = menu.findItem(R.id.menu_share_group);
        shareMenu.setVisible(false);

        menu.findItem(R.id.menu_create_group_chat).setVisible(false);
        menu.findItem(R.id.menu_enhancedscreen).setVisible(false);
    }

    private Handler messageHandler = new Handler() {

        public void handleMessage(Message msg) {
            if (mProgressDialog != null)
                mProgressDialog.dismiss();
        }

    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_edit_group: {
            if (mNotificationImportExportListener.mIsImportProcessing) {
                Toast.makeText(
                        mContext,
                        mContext.getResources().getString(R.string.device_busy),
                        Toast.LENGTH_SHORT).show();
            } else {
                if (mListener != null)
                    mListener.onEditRequested(mGroupUri);
            }
            break;
        }
        case R.id.menu_delete_group: {
            GroupDeletionDialogFragment.show(getFragmentManager(), mGroupId,
                    mGroupName, mCloseActivityAfterDelete);
            return true;
        }
        case R.id.menu_send_message_group: {
            if (isEmptyGroup(getString(
            R.string.no_data_message
            ))) {
                return true;
            }
            mProgressDialog = ProgressDialog.show(mContext, "",
                    getString(R.string.please_wait));
            new Thread() {
                public void run() {
                    Looper.prepare();
                    String recipients = GroupUtil.getGroupMessageRecipients(
                            mGroupId, mContext);
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
            return true;
        }
        case R.id.menu_share_group: {
            if (isEmptyGroup(getString(
            /* R.string.share) */
            R.string.no_data_share
            ))) {
                return true;
            }
            mProgressDialog = ProgressDialog.show(mContext, "",
                    getString(R.string.please_wait));
            new Thread() {
                public void run() {
                    Looper.prepare();
                    StringBuilder uriList = GroupUtil.getShareGroupContacts(
                            mGroupId, mContext);
                    messageHandler.sendEmptyMessage(0);
                    if (uriList != null) {
                        GroupUtil.shareContacts(uriList, mContext);
                    } else {
                        Toast.makeText(mContext,
                                getString(R.string.no_data_share),
                                Toast.LENGTH_SHORT).show();
                    }

                    Looper.loop();
                }
            }.start();
            return true;
        }
        case MENU_ADD: {
            Intent intent = new Intent();
            intent.setData(getGroupUri());
            getActivity().setResult(getActivity().RESULT_OK, intent);
            getActivity().finish();
        }
        }
        return false;
    }

    /**
     * Returns boolean result whether group is empty or not
     *
     * @return true if group is empty otherwise false
     */
    private boolean isEmptyGroup(String msg) {
        boolean isEmpty = false;
        if (null != mAdapter && mAdapter.isEmpty()) {
            isEmpty = true;
            Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
        }
        return isEmpty;
    }

    public void closeActivityAfterDelete(boolean closeActivity) {
        mCloseActivityAfterDelete = closeActivity;
    }

    public long getGroupId() {
        return mGroupId;
    }

    public void deleteGroup() {
        if (mMemberListView.hasFocus() && mMemberListView.getCount() > 0) {
            int pos = -1;
            pos = mMemberListView.getSelectedItemPosition();
            if (pos != -1) {
                ArrayList<ContactEntry>  arrylist = mAdapter.getItem(pos);
            }
        }
    }
}
