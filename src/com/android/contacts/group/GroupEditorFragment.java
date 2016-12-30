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

import java.util.ArrayList;
import java.util.List;

import com.android.contacts.R;
import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.GroupMemberLoader;
import com.android.contacts.GroupMemberLoader.GroupEditorQuery;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.activities.GroupEditorActivity;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.group.SuggestedMemberListAdapter.SuggestedMember;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.model.account.BaseAccountType.MaxLength;
import com.android.contacts.common.util.ViewUtil;
import com.android.contacts.common.model.account.GroupDataHolder;
import com.google.common.base.Objects;
import com.android.contacts.util.Constants;
import com.android.contacts.editor.ContactEditorFragment.SaveMode;
import java.util.ArrayList;
import java.util.List;
import android.widget.ImageButton;

import android.view.View.OnFocusChangeListener;
public class GroupEditorFragment extends Fragment implements
        SelectAccountDialogFragment.Listener {
    private static final String TAG = "GroupEditorFragment";

    private static final String LEGACY_CONTACTS_AUTHORITY = "contacts";

    private static final String KEY_ACTION = "action";
    private static final String KEY_GROUP_URI = "groupUri";
    private static final String KEY_GROUP_ID = "groupId";
    private static final String KEY_STATUS = "status";
    private static final String KEY_ACCOUNT_NAME = "accountName";
    private static final String KEY_ACCOUNT_TYPE = "accountType";
    private static final String KEY_DATA_SET = "dataSet";
    private static final String KEY_GROUP_NAME_IS_READ_ONLY = "groupNameIsReadOnly";
    private static final String KEY_ORIGINAL_GROUP_NAME = "originalGroupName";
    private static final String KEY_MEMBERS_TO_ADD = "membersToAdd";
    private static final String KEY_MEMBERS_TO_REMOVE = "membersToRemove";
    private static final String KEY_MEMBERS_TO_DISPLAY = "membersToDisplay";

    private static final String CURRENT_EDITOR_TAG = "currentEditorForAccount";

    private static boolean mFinishLoad = true;
    private ProgressDialog mProgressDialog = null;
    /* Progress dialog shown when adding existing contacts of group */
    private ProgressDialog mProgressDialogAddToGroup = null;
    ProgressDialog mProgressDialogSave = null;

    /** Constants used to maintain data while orientation change */
    private final String KEY_IS_PROGRESS_DIALOG_ONGOING = "progress_dialog_ongoing";
    private final String KEY_IS_PROGRESS_DIALOG_SAVE_ONGOING = "progress_dialog_save_ongoing";
    private final String KEY_IS_PROGRESS_DIALOG_ADD_TO_GROUP_ONGOING = "progress_dialog_add_to_group_ongoing";
    private final String KEY_PROGRESS_DIALOG_STRING = "progress_dialog_string";
    private String mProgressDialogString = null;
    ArrayList<Long> mLoadingRawContactIds = null;
    ArrayList<Long> mLoadingContactIds = null;
    private AddGroupMembersAsyncTask mAddGroupMembersAsyncTask = null;
    private Member mClickedMember = null;
    public static final int ALL_MEMBERS_ALREADY_ADDED = 1;

    private final String KEY_IS_DELETE_DIALOG_SHOWN = "delete_dialog_shown";
    private final String KEY_IS_REMOVE_MEMBER_DIALOG_SHOWN = "remove_member_dialog_shown";
    private final String KEY_MEMBER_TO_REMOVE = "member_to_remove";
    /** Alert dialog to delete all selected members */
    private AlertDialog mDeleteAllAlertdialog = null;
    /** Alert dialog to remove single member from selected list */
    private AlertDialog mRemoveMemberDialog = null;
    /** Member to be deleted */
    private Member mMemberToRemove = null;

    /** Async task to add selected members */
    private AddSelectedUriMembersAsyncTask mAddSelectedMembersAsyncTask = null;
    /** Selected URIs */
    private ArrayList<Uri> mSelectedUri = null;


    public static interface Listener {
        /**
         * Group metadata was not found, close the fragment now.
         */
        public void onGroupNotFound();

        /**
         * User has tapped Revert, close the fragment now.
         */
        void onReverted();

        /**
         * Contact was saved and the Fragment can now be closed safely.
         */
        void onSaveFinished(int resultCode, Intent resultIntent);

        /**
         * Fragment is created but there's no accounts set up.
         */
        void onAccountsNotFound();
    }

    private static final int LOADER_GROUP_METADATA = 1;
    private static final int LOADER_EXISTING_MEMBERS = 2;
    private static final int LOADER_NEW_GROUP_MEMBER = 3;

    public static final String SAVE_MODE_EXTRA_KEY = "saveMode";

    private static final String MEMBER_RAW_CONTACT_ID_KEY = "rawContactId";
    private static final String MEMBER_LOOKUP_URI_KEY = "memberLookupUri";
    private static final String NAME_RAW_CONTACT_ID = "name_raw_contact_id";
    protected static final String[] PROJECTION_CONTACT = new String[] {
            Contacts._ID, // 0
            Contacts.DISPLAY_NAME_PRIMARY, // 1
            Contacts.DISPLAY_NAME_ALTERNATIVE, // 2
            Contacts.SORT_KEY_PRIMARY, // 3
            Contacts.STARRED, // 4
            Contacts.CONTACT_PRESENCE, // 5
            Contacts.CONTACT_CHAT_CAPABILITY, // 6
            Contacts.PHOTO_ID, // 7
            Contacts.PHOTO_THUMBNAIL_URI, // 8
            Contacts.LOOKUP_KEY, // 9
            Contacts.PHONETIC_NAME, // 10
            Contacts.HAS_PHONE_NUMBER, // 11
            Contacts.IS_USER_PROFILE, // 12
            NAME_RAW_CONTACT_ID, // 13
    };

    protected static final int CONTACT_ID_COLUMN_INDEX = 0;
    protected static final int CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    protected static final int CONTACT_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    protected static final int CONTACT_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    protected static final int CONTACT_STARRED_COLUMN_INDEX = 4;
    protected static final int CONTACT_PRESENCE_STATUS_COLUMN_INDEX = 5;
    protected static final int CONTACT_CHAT_CAPABILITY_COLUMN_INDEX = 6;
    protected static final int CONTACT_PHOTO_ID_COLUMN_INDEX = 7;
    protected static final int CONTACT_PHOTO_URI_COLUMN_INDEX = 8;
    protected static final int CONTACT_LOOKUP_KEY_COLUMN_INDEX = 9;
    protected static final int CONTACT_PHONETIC_NAME_COLUMN_INDEX = 10;
    protected static final int CONTACT_HAS_PHONE_COLUMN_INDEX = 11;
    protected static final int CONTACT_IS_USER_PROFILE = 12;
    protected static final int CONTACT_RAW_CONTACT_ID = 13;

    public static final int CONTACT_PICKER_ACTIVITY = 50;
    public static final int CONTACT_CREATION_ACTIVITY = 60;
    private static final String KEY_IS_SYSTEM_GROUP = "isSystemGroup";

    /**
     * Modes that specify the status of the editor
     */
    public enum Status {
        SELECTING_ACCOUNT, // Account select dialog is showing
        LOADING, // Loader is fetching the group metadata
        EDITING, // Not currently busy. We are waiting forthe user to enter
                 // data.
        SAVING, // Data is currently being saved
        CLOSING // Prevents any more saves
    }

    private Context mContext;
    private String mAction;
    private Bundle mIntentExtras;
    private Uri mGroupUri;
    private long mGroupId;
    private Listener mListener;

    private Status mStatus;

    private ViewGroup mRootView;
    private ListView mListView;
    private LayoutInflater mLayoutInflater;

    private EditText mGroupNameView;
    private AutoCompleteTextView mAutoCompleteTextView;
    private LinearLayout mMemberHeader;
    private TextView mMemberCount;
    private ImageView mMemberHeaderSeperator;

    private String mAccountName;
    private String mAccountType;
    private String mDataSet;

    private boolean mGroupNameIsReadOnly;

    private boolean mIsSystemGroup;
    private String mOriginalGroupName = "";
    private int mLastGroupEditorId;

    private MemberListAdapter mMemberListAdapter;
    private ContactPhotoManager mPhotoManager;

    private ContentResolver mContentResolver;
    private SuggestedMemberListAdapter mAutoCompleteAdapter;

    private ArrayList<Member> mListMembersToAdd = new ArrayList<Member>();
    private ArrayList<Member> mListMembersToRemove = new ArrayList<Member>();
    private ArrayList<Member> mListToDisplay = new ArrayList<Member>();
    boolean mIsPhoneAccount = false;
    boolean mIsEditGroup = false;
    int madapterPosition =-1;

    public GroupEditorFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedState) {
        setHasOptionsMenu(true);
        mLayoutInflater = inflater;
        mRootView = (ViewGroup) inflater.inflate(
                R.layout.group_editor_fragment, container, false);
        return mRootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mPhotoManager = ContactPhotoManager.getInstance(mContext);
        mMemberListAdapter = new MemberListAdapter();
        mClickedMember = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (mProgressDialogAddToGroup != null
        && mProgressDialogAddToGroup.isShowing() == true) {
            mProgressDialogAddToGroup.dismiss();
            mProgressDialogAddToGroup = null;
        }
        if (mProgressDialog != null
        && mProgressDialog.isShowing() == true) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        if (mProgressDialogSave != null
        && mProgressDialogSave.isShowing() == true) {
            mProgressDialogSave.dismiss();
            mProgressDialogSave = null;
        }
        if (mAddGroupMembersAsyncTask != null) {
            mAddGroupMembersAsyncTask.detach();
            mAddGroupMembersAsyncTask = null;
        }

        if (mDeleteAllAlertdialog != null
                && mDeleteAllAlertdialog.isShowing() == true) {
            mDeleteAllAlertdialog.dismiss();
            mDeleteAllAlertdialog = null;
        }
        if (mRemoveMemberDialog != null
                && mRemoveMemberDialog.isShowing() == true) {
            mRemoveMemberDialog.dismiss();
            mRemoveMemberDialog = null;
        }

        try {
            getLoaderManager().destroyLoader(LOADER_NEW_GROUP_MEMBER);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            // Just restore from the saved state. No loading.
            onRestoreInstanceState(savedInstanceState);
            if (mStatus == Status.SELECTING_ACCOUNT) {
                // Account select dialog is showing. Don't setup the editor yet.
            } else if (mStatus == Status.LOADING) {
                startGroupMetaDataLoader();
            } else {
                setupEditorForAccount();
            }
        } else if (Intent.ACTION_EDIT.equals(mAction)) {
            startGroupMetaDataLoader();
        } else if (Intent.ACTION_INSERT.equals(mAction)) {
            final Account account = mIntentExtras == null ? null
                    : (Account) mIntentExtras
                            .getParcelable(Intents.Insert.ACCOUNT);
            final String dataSet = mIntentExtras == null ? null : mIntentExtras
                    .getString(Intents.Insert.DATA_SET);

            if (account != null) {
                // Account specified in Intent - no data set can be specified in
                // this manner.
                mAccountName = account.name;
                mAccountType = account.type;
                mDataSet = dataSet;
                setupEditorForAccount();
            } else {
                // No Account specified. Let the user choose from a
                // disambiguation dialog.
                selectAccountAndCreateGroup();
            }
        } else {
            throw new IllegalArgumentException("Unknown Action String "
                    + mAction + ". Only support " + Intent.ACTION_EDIT + " or "
                    + Intent.ACTION_INSERT);
        }
    }

    private void startGroupMetaDataLoader() {
        mStatus = Status.LOADING;
        getLoaderManager().initLoader(LOADER_GROUP_METADATA, null,
                mGroupMetaDataLoaderListener);
    }

    /**
     * Returns selected contact-ids and raw contact-ids.
     */
    public GroupDataHolder getSelectedData() {
        GroupDataHolder groupDataHolder = null;
        if (mLoadingContactIds != null && mLoadingRawContactIds != null
                && mLoadingContactIds.size() > 0
                && mLoadingRawContactIds.size() > 0) {
            if (groupDataHolder == null) {
                groupDataHolder = new GroupDataHolder();
            }
            groupDataHolder.setIds(mLoadingContactIds, mLoadingRawContactIds);
        }
        if (mSelectedUri != null && mSelectedUri.size() > 0) {
            if (groupDataHolder == null) {
                groupDataHolder = new GroupDataHolder();
            }
            groupDataHolder.setSelectUri(mSelectedUri);
        }
        return groupDataHolder;
    }

    /**
     * Sets selected contact-ids and raw contact-ids.
     */
    public void setSelectedData(GroupDataHolder groupData) {
        mLoadingContactIds = groupData.getContactIds();
        mLoadingRawContactIds = groupData.getRawContactIds();
        mSelectedUri = groupData.getSelectedUri();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_ACTION, mAction);
        outState.putParcelable(KEY_GROUP_URI, mGroupUri);
        outState.putLong(KEY_GROUP_ID, mGroupId);

        outState.putSerializable(KEY_STATUS, mStatus);
        outState.putString(KEY_ACCOUNT_NAME, mAccountName);
        outState.putString(KEY_ACCOUNT_TYPE, mAccountType);
        outState.putString(KEY_DATA_SET, mDataSet);

        outState.putBoolean(KEY_GROUP_NAME_IS_READ_ONLY, mGroupNameIsReadOnly);
        outState.putBoolean(KEY_IS_SYSTEM_GROUP, mIsSystemGroup);
        outState.putString(KEY_ORIGINAL_GROUP_NAME, mOriginalGroupName);

        outState.putParcelableArrayList(KEY_MEMBERS_TO_ADD, mListMembersToAdd);
        outState.putParcelableArrayList(KEY_MEMBERS_TO_REMOVE,
                mListMembersToRemove);
        outState.putParcelableArrayList(KEY_MEMBERS_TO_DISPLAY, mListToDisplay);

        boolean isProgressDialogShown = mProgressDialog != null
                && mProgressDialog.isShowing() && mLoadingContactIds != null
                && mLoadingContactIds.size() > 0;
        outState.putBoolean(KEY_IS_PROGRESS_DIALOG_ONGOING,
                isProgressDialogShown);
        if (isProgressDialogShown == true) {
            outState.putString(KEY_PROGRESS_DIALOG_STRING,
                    mProgressDialogString);
        }

        boolean isProgressDialogSaveShown = mProgressDialogSave != null
                && mProgressDialogSave.isShowing();
        outState.putBoolean(KEY_IS_PROGRESS_DIALOG_SAVE_ONGOING,
                isProgressDialogSaveShown);

        if (isProgressDialogSaveShown == true) {
            mProgressDialogSave.dismiss();
        }
        boolean isProgressDialogAddToGroupShown = mProgressDialogAddToGroup != null
                && mProgressDialogAddToGroup.isShowing();
        outState.putBoolean(KEY_IS_PROGRESS_DIALOG_ADD_TO_GROUP_ONGOING,
        isProgressDialogAddToGroupShown);
        if (mAddGroupMembersAsyncTask != null) {
            mAddGroupMembersAsyncTask.cancel(true);
        }
        outState.putBoolean(
                KEY_IS_DELETE_DIALOG_SHOWN,
                mDeleteAllAlertdialog != null
                        && mDeleteAllAlertdialog.isShowing() == true);

        boolean isRemoveMemberShown = mRemoveMemberDialog != null
                && mRemoveMemberDialog.isShowing();
        outState.putBoolean(KEY_IS_REMOVE_MEMBER_DIALOG_SHOWN,
                isRemoveMemberShown);
        if (isRemoveMemberShown == true) {
            outState.putParcelable(KEY_MEMBER_TO_REMOVE, mMemberToRemove);
        }
        if (mAddSelectedMembersAsyncTask != null) {
            mAddSelectedMembersAsyncTask.cancel(true);
        }
    }

    private void onRestoreInstanceState(Bundle state) {
        mAction = state.getString(KEY_ACTION);
        mGroupUri = state.getParcelable(KEY_GROUP_URI);
        mGroupId = state.getLong(KEY_GROUP_ID);

        mStatus = (Status) state.getSerializable(KEY_STATUS);
        mAccountName = state.getString(KEY_ACCOUNT_NAME);
        mAccountType = state.getString(KEY_ACCOUNT_TYPE);
        mDataSet = state.getString(KEY_DATA_SET);

        mGroupNameIsReadOnly = state.getBoolean(KEY_GROUP_NAME_IS_READ_ONLY);
        mIsSystemGroup = state.getBoolean(KEY_IS_SYSTEM_GROUP);
        mOriginalGroupName = state.getString(KEY_ORIGINAL_GROUP_NAME);

        mListMembersToAdd = state.getParcelableArrayList(KEY_MEMBERS_TO_ADD);
        mListMembersToRemove = state
                .getParcelableArrayList(KEY_MEMBERS_TO_REMOVE);
        mListToDisplay = state.getParcelableArrayList(KEY_MEMBERS_TO_DISPLAY);
        if (state.getBoolean(KEY_IS_PROGRESS_DIALOG_ONGOING, false) == true) {
            mProgressDialogString = state.getString(KEY_PROGRESS_DIALOG_STRING);
            if (mProgressDialogString != null) {
                if (mProgressDialog != null
                        && mProgressDialog.isShowing() == true) {
                    mProgressDialog.dismiss();
                }
                mProgressDialog = ProgressDialog.show(mContext, "",
                        mProgressDialogString);
            }
        }
        if (state.getBoolean(KEY_IS_PROGRESS_DIALOG_SAVE_ONGOING, false) == true) {
            mProgressDialogSave = ProgressDialog.show(mContext, "",
                    getString(R.string.saving_group));
            mProgressDialogSave.setCancelable(false);
        }

        if (state
                .getBoolean(KEY_IS_PROGRESS_DIALOG_ADD_TO_GROUP_ONGOING, false) == true) {
            if (null == mProgressDialogAddToGroup
                    || mProgressDialogAddToGroup.isShowing() == false) {
                mProgressDialogAddToGroup = ProgressDialog.show(mContext, "",
                        getString(R.string.adding_contacts));
                mProgressDialogAddToGroup.setCancelable(false);
            }
        }

        if (mLoadingContactIds != null && mLoadingRawContactIds != null
                && mLoadingContactIds.size() > 0
                && mLoadingRawContactIds.size() > 0) {
            mFinishLoad = true;
            mAddGroupMembersAsyncTask = new AddGroupMembersAsyncTask(
                    mProgressDialog);
            mAddGroupMembersAsyncTask.execute(true);
        }
        if (state.getBoolean(KEY_IS_DELETE_DIALOG_SHOWN, false) == true) {
            showDeleteAllDialog();
        }

        if (state.getBoolean(KEY_IS_REMOVE_MEMBER_DIALOG_SHOWN, false) == true) {
            Member member = state.getParcelable(KEY_MEMBER_TO_REMOVE);
            if (member != null && mMemberListAdapter != null) {
                mMemberListAdapter.showRemoveMemberDialog(member);
            }
        }
        if (mSelectedUri != null && mSelectedUri.size() > 0) {
            mAddSelectedMembersAsyncTask = new AddSelectedUriMembersAsyncTask(
                    mProgressDialogAddToGroup);
            mAddSelectedMembersAsyncTask.execute(true);
        }
    }

    public void setContentResolver(ContentResolver resolver) {
        mContentResolver = resolver;
        if (mAutoCompleteAdapter != null) {
            mAutoCompleteAdapter.setContentResolver(mContentResolver);
        }
    }

    private void selectAccountAndCreateGroup() {
        final List<AccountWithDataSet> accounts = AccountTypeManager
                .getInstance(mContext).getAccounts(true /* writeable */);
        // No Accounts available
        if (accounts.isEmpty()) {
            Log.e(TAG, "No accounts were found.");
            if (mListener != null) {
                mListener.onAccountsNotFound();
            }
            return;
        }

        // In the common case of a single account being writable, auto-select
        // it without showing a dialog.
        if (accounts.size() == 1) {
            mAccountName = accounts.get(0).name;
            mAccountType = accounts.get(0).type;
            mDataSet = accounts.get(0).dataSet;
            setupEditorForAccount();
            return; // Don't show a dialog.
        }

        mStatus = Status.SELECTING_ACCOUNT;
        SelectAccountDialogFragment.show(getFragmentManager(), this,
                R.string.dialog_new_group_account,
                AccountListFilter.ACCOUNTS_GROUP_WRITABLE, null);
    }

    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        mAccountName = account.name;
        mAccountType = account.type;
        mDataSet = account.dataSet;
        setupEditorForAccount();
    }

    @Override
    public void onAccountSelectorCancelled() {
        if (mListener != null) {
            // Exit the fragment because we cannot continue without selecting an
            // account
            mListener.onGroupNotFound();
        }
    }

    private AccountType getAccountType() {
        return AccountTypeManager.getInstance(mContext).getAccountType(
                mAccountType, mDataSet);
    }

    /**
     * @return true if the group membership is editable on this account type.
     *         false otherwise, or account is not set yet.
     */
    private boolean isGroupMembershipEditable() {
        if (mAccountType == null) {
            return false;
        }
        return getAccountType().isGroupMembershipEditable();
    }

    /**
     * Sets up the editor based on the group's account name and type.
     */
    private void setupEditorForAccount() {
        final AccountType accountType = getAccountType();
        final boolean editable = isGroupMembershipEditable();
        boolean isNewEditor = false;
        mMemberListAdapter.setIsGroupMembershipEditable(editable);

        // Since this method can be called multiple time, remove old editor if
        // the editor type
        // is different from the new one and mark the editor with a tag so it
        // can be found for
        // removal if needed
        View editorView;
        int newGroupEditorId = editable ? R.layout.group_editor_view
                : R.layout.external_group_editor_view;
        this.getActivity().invalidateOptionsMenu();
        if (newGroupEditorId != mLastGroupEditorId) {
            View oldEditorView = mRootView.findViewWithTag(CURRENT_EDITOR_TAG);
            if (oldEditorView != null) {
                mRootView.removeView(oldEditorView);
            }
            editorView = mLayoutInflater.inflate(newGroupEditorId, mRootView,
                    false);
            editorView.setTag(CURRENT_EDITOR_TAG);
            mAutoCompleteAdapter = null;
            mLastGroupEditorId = newGroupEditorId;
            isNewEditor = true;
        } else {
            editorView = mRootView.findViewWithTag(CURRENT_EDITOR_TAG);
            if (editorView == null) {
                throw new IllegalStateException("Group editor view not found");
            }
        }

        mIsPhoneAccount = isPhoneAccount();
        if (mOriginalGroupName != null && mIsPhoneAccount) {
            mIsEditGroup = true;
            getActivity().invalidateOptionsMenu();
        }

        mGroupNameView = (EditText) editorView.findViewById(R.id.group_name);

        if (true == mIsPhoneAccount) {
            InputFilter[] filterArray = new InputFilter[1];
            filterArray[0] = new InputFilter.LengthFilter(
                    MaxLength.GroupMebership.GROUP_NAME);
            mGroupNameView.setFilters(filterArray);
            mGroupNameView.addTextChangedListener(mGroupNameTextWatcher);
        }

        mAutoCompleteTextView = (AutoCompleteTextView) editorView
                .findViewById(R.id.add_member_field);
        mGroupNameView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_FILTER);
        mGroupNameView.requestFocus();
        mListView = (ListView) editorView.findViewById(android.R.id.list);
        mListView.setAdapter(mMemberListAdapter);

        mListView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus){
                    madapterPosition = -1;
                    getActivity().invalidateOptionsMenu();
                }else {
                    madapterPosition = 0;
                    mListView.setOnItemSelectedListener(mItemSelectedListener);
                    getActivity().invalidateOptionsMenu();
                }
               }
            });

        // _india: Adding members header and count of members in GroupEditor
        mMemberHeader = (LinearLayout) editorView
                .findViewById(R.id.member_header);
        mMemberCount = (TextView) editorView.findViewById(R.id.count);
        mMemberHeaderSeperator = (ImageView) editorView
                .findViewById(R.id.member_header_seperator);
        int size = (mListToDisplay != null) ? mListToDisplay.size() : 0;
        if (size == 0) {
            mMemberHeader.setVisibility(View.INVISIBLE);
            mMemberHeaderSeperator.setVisibility(View.INVISIBLE);
        } else {
            String countString = getResources().getQuantityString(
                    R.plurals.numberOfMembers, size, size);
            mMemberHeader.setVisibility(View.VISIBLE);
            mMemberHeaderSeperator.setVisibility(View.VISIBLE);
            mMemberCount.setText(countString);
        }

        // Setup the account header, only when exists.
        if (editorView.findViewById(R.id.account_header) != null) {
            CharSequence accountTypeDisplayLabel = accountType
                    .getDisplayLabel(mContext);
            ImageView accountIcon = (ImageView) editorView
                    .findViewById(R.id.account_icon);
            TextView accountTypeTextView = (TextView) editorView
                    .findViewById(R.id.account_type);
            TextView accountNameTextView = (TextView) editorView
                    .findViewById(R.id.account_name);
            if (!TextUtils.isEmpty(mAccountName)) {
                accountNameTextView.setText(mContext.getString(
                     R.string.from_account_format, mAccountName));
                    accountTypeTextView.setText(accountTypeDisplayLabel);
            }
            accountIcon.setImageDrawable(accountType.getDisplayIcon(mContext));
        }

        // Setup the autocomplete adapter (for contacts to suggest to add to the
        // group) based on the
        // account name and type. For groups that cannot have membership edited,
        // there will be no
        // autocomplete text view.
        if (mAutoCompleteTextView != null) {
            mAutoCompleteAdapter = new SuggestedMemberListAdapter(mContext,
                    android.R.layout.simple_dropdown_item_1line);
            mAutoCompleteAdapter.setContentResolver(mContentResolver);
            mAutoCompleteAdapter.setAccountType(mAccountType);
            mAutoCompleteAdapter.setAccountName(mAccountName);
            mAutoCompleteAdapter.setDataSet(mDataSet);
            mAutoCompleteAdapter.setGroupId(mGroupId);
           mAutoCompleteTextView.setThreshold(1);
            mAutoCompleteTextView.setAdapter(mAutoCompleteAdapter);
            mAutoCompleteTextView
                    .setOnItemClickListener(new OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent,
                                View view, int position, long id) {
                            SuggestedMember member = (SuggestedMember) view
                                    .getTag();
                            if (member == null) {
                                return; // just in case
                            }
                            loadMemberToAddToGroup(member.getRawContactId(),
                                    String.valueOf(member.getContactId()));

                            // Update the autocomplete adapter so the contact
                            // doesn't get suggested again
                            // mAutoCompleteAdapter.addNewMember(member.getContactId());

                            // Clear out the text field
                            mAutoCompleteTextView.setText("");
                        }
                    });
            mAutoCompleteTextView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return true;
                }
            });
            // Update the exempt list. (mListToDisplay might have been restored
            // from the saved
            // state.)
            if (mClickedMember != null) {
                if (!isContactExist(mClickedMember.getLookupUri())) {
                    removeMember((mClickedMember));
                }
            }
            mAutoCompleteAdapter.updateExistingMembersList(mListToDisplay);
        }
        if (mGroupNameView != null) {
            if (mIsSystemGroup || mGroupNameIsReadOnly
                    || isDefaultGroup(mOriginalGroupName)) {
                Log.i(TAG,
                        "mGroupNameView Set as NOT FOCUSABLE ## mIsSystemGroup "
                                + mIsSystemGroup
                                + "   mGroupNameIsReadOnly ## "
                                + mGroupNameIsReadOnly);
                mGroupNameView.setFocusable(false);
                if (mAutoCompleteTextView != null) {
                    mAutoCompleteTextView.requestFocus();
                }
            } else {
                mGroupNameView.requestFocus();
            }
        }

        // If the group name is ready only, don't let the user focus on the
        // field.
        mGroupNameView.setFocusable(!mGroupNameIsReadOnly);
        if (isNewEditor) {
            mRootView.addView(editorView);
        }

        ImageView addMembers = (ImageView) editorView
                .findViewById(R.id.add_member_picker);
        if (editable) {
            OnClickListener addMemberClickListner = new OnClickListener() {

                @Override
                public void onClick(View v) {
                    // Raise Intent for ChooseGroupPicker Activity
                    mProgressDialogString = getString(R.string.please_wait);
                    mProgressDialog = ProgressDialog.show(mContext, "",
                            mProgressDialogString);
                    mProgressDialog.setCancelable(false);
                    new Thread() {
                        public void run() {
                            Looper.prepare();

                            Intent intent = new Intent();
                            intent.setClass(mContext, GroupPickerActivity.class);
                            intent.putExtra("AccountName", mAccountName);
                            intent.putExtra("AccountType", mAccountType);
                            intent.putExtra("GroupName", mOriginalGroupName);
                            intent.putExtra("DataSet", mDataSet);
                            intent.putExtra("isGroupPicker", true);
                            long[] existingListArray = new long[mAutoCompleteAdapter
                                    .getExistingMemberList().size()];
                            List<Long> list = mAutoCompleteAdapter
                                    .getExistingMemberList();
                            int i = 0;
                            for (Long contactId : list) {
                                existingListArray[i++] = contactId.longValue();
                            }
                            intent.putExtra("ExistingContactId",
                                    existingListArray);
                            startActivityForResult(intent,
                                    CONTACT_PICKER_ACTIVITY);
                            messageHandler.sendEmptyMessage(0);
                            Looper.loop();

                        }
                    }.start();

                }
            };
            if (addMembers != null)
                addMembers.setOnClickListener(addMemberClickListner);
        }
        mStatus = Status.EDITING;
    }

    AdapterView.OnItemSelectedListener mItemSelectedListener = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> arg0, View arg1, int pos,
                long arg3) {
            if(mListView.isFocused()){
                madapterPosition = pos;
                getActivity().invalidateOptionsMenu();
            }
        }
        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
        }
    };


    public boolean isDefaultGroup(String groupName) {
        if (null == groupName)
            return false;

        return false;
    }

    private Handler messageHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg != null && msg.what == 1) {
                if (mProgressDialogAddToGroup != null
                && mProgressDialogAddToGroup.isShowing() == true ){
                    mProgressDialogAddToGroup.dismiss();
                    mProgressDialogAddToGroup = null;
                }
            } else {
                if (mProgressDialog != null
                && mProgressDialog.isShowing() == true) {
                    mProgressDialog.dismiss();
                    mProgressDialog = null;
                }
            }
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode,
            final Intent data) {

        if (requestCode == CONTACT_PICKER_ACTIVITY) {
            if (resultCode == GroupEditorFragment.ALL_MEMBERS_ALREADY_ADDED) {
                // All Members already Selected.
                Toast.makeText(mContext, R.string.no_members_to_add,
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (data != null) {
                // Start a thread to Popullate the adapted and add the members.
                // Do it in a thread because this might take time
                Bundle bundle = data.getExtras();
                if (bundle != null) {
                    Object contactIds = bundle.get("ContactIdArray");
                    Object rawContactIds = bundle.get("RawContactIdArray");
                    if (contactIds != null && rawContactIds != null) {
                        // show progress only if there are contacts to add.
                        mProgressDialogString = getString(R.string.adding_contacts);
                        mProgressDialog = ProgressDialog.show(mContext, "",
                                mProgressDialogString);
                        mProgressDialog.setCancelable(false);

                        mLoadingContactIds = (ArrayList<Long>) contactIds;
                        mLoadingRawContactIds = (ArrayList<Long>) rawContactIds;

                        mAddGroupMembersAsyncTask = new AddGroupMembersAsyncTask(
                                mProgressDialog);
                        mAddGroupMembersAsyncTask.execute(true);
                    }
                }
            }
        }
        if (requestCode == CONTACT_CREATION_ACTIVITY) {
            return;
        }

    }

    public void load(String action, Uri groupUri, Bundle intentExtras) {
        mAction = action;
        mGroupUri = groupUri;
        mGroupId = (groupUri != null) ? ContentUris.parseId(mGroupUri) : 0;
        mIntentExtras = intentExtras;
    }

    private void bindGroupMetaData(Cursor cursor) {
        if (!cursor.moveToFirst()) {
            Log.i(TAG, "Group not found with URI: " + mGroupUri
                    + " Closing activity now.");
            if (mListener != null) {
                mListener.onGroupNotFound();
            }
            return;
        }
        mOriginalGroupName = cursor.getString(GroupMetaDataLoader.TITLE);
        mAccountName = cursor.getString(GroupMetaDataLoader.ACCOUNT_NAME);
        mAccountType = cursor.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
        mDataSet = cursor.getString(GroupMetaDataLoader.DATA_SET);
        mGroupNameIsReadOnly = (cursor.getInt(GroupMetaDataLoader.IS_READ_ONLY) == 1);

        setupEditorForAccount();

        // Setup the group metadata display
        mGroupNameView.removeTextChangedListener(mGroupNameTextWatcher);
        mGroupNameView.setText(mOriginalGroupName);
        mGroupNameView.setSelection(mOriginalGroupName.length());
        mGroupNameView.addTextChangedListener(mGroupNameTextWatcher);
    }

    public void loadMemberToAddToGroup(long rawContactId, String contactId) {
        Bundle args = new Bundle();
        args.putLong(MEMBER_RAW_CONTACT_ID_KEY, rawContactId);
        args.putString(MEMBER_LOOKUP_URI_KEY, contactId);
        Loader loader = getLoaderManager().getLoader(LOADER_NEW_GROUP_MEMBER);
        if (loader == null)
            getLoaderManager().initLoader(LOADER_NEW_GROUP_MEMBER, args,
                    mContactLoaderListener);
        else
            getLoaderManager().restartLoader(LOADER_NEW_GROUP_MEMBER, args,
                    mContactLoaderListener);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    public void onDoneClicked() {
        if (isGroupMembershipEditable()) {
            if (save(SaveMode.CLOSE)) {
                mProgressDialogSave = ProgressDialog.show(mContext, "",
                        getString(R.string.saving_group));
                mProgressDialogSave.setCancelable(false);
            }
        } else {
            // Just revert it.
            doRevertAction();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.edit_group, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // TODO Auto-generated method stub
        menu.findItem(R.id.menu_save).setVisible(true);
        if (isGroupMembershipEditable())
            menu.findItem(R.id.menu_delete_all).setVisible(true);
        else
            menu.findItem(R.id.menu_delete_all).setVisible(false);
        /* Show "Add contact menu" only for existing groups*/
        if (mIsEditGroup && mGroupId!=0)
            menu.findItem(R.id.menu_add_new_contact).setVisible(true);
        else
            menu.findItem(R.id.menu_add_new_contact).setVisible(false);
        if(madapterPosition != -1)
            menu.findItem(R.id.menu_remove_member).setEnabled(true);
        else
            menu.findItem(R.id.menu_remove_member).setEnabled(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_save :
            onDoneClicked();
            break;
        case R.id.menu_discard:
            return revert();
        case R.id.menu_delete_all:
            showDeleteAllDialog();
            break;
        case R.id.menu_add_new_contact:
            final Intent intent = new Intent(Intent.ACTION_INSERT,
                    Contacts.CONTENT_URI);
            intent.putExtra("groupid", mGroupId);
            startActivityForResult(intent, CONTACT_CREATION_ACTIVITY);
            break;
        case R.id.menu_add_member :
            addGroupMembers();
            break;
        case R.id.menu_remove_member :
            removeGroupMember();
            break;
        }
        return false;
    }

    /* Remove group member */
    public void removeGroupMember() {
        if (!mMemberListAdapter.isEmpty() && madapterPosition != -1) {
            mMemberListAdapter.showRemoveMemberDialog(mMemberListAdapter
                    .getItem(madapterPosition));
        }
    }

    private void addGroupMembers(){
        mProgressDialogString = getString(R.string.please_wait);
        mProgressDialog = ProgressDialog.show(mContext, "",
                mProgressDialogString);
        mProgressDialog.setCancelable(false);
        new Thread() {
            public void run() {
                Looper.prepare();

                Intent intent = new Intent();
                intent.setClass(mContext, GroupPickerActivity.class);
                intent.putExtra("AccountName", mAccountName);
                intent.putExtra("AccountType", mAccountType);
                intent.putExtra("GroupName", mOriginalGroupName);
                intent.putExtra("DataSet", mDataSet);
                intent.putExtra("isGroupPicker", true);
                long[] existingListArray = new long[mAutoCompleteAdapter
                        .getExistingMemberList().size()];
                List<Long> list = mAutoCompleteAdapter
                        .getExistingMemberList();
                int i = 0;
                for (Long contactId : list) {
                    existingListArray[i++] = contactId.longValue();
                }
                intent.putExtra("ExistingContactId",
                        existingListArray);
                startActivityForResult(intent,
                        CONTACT_PICKER_ACTIVITY);
                messageHandler.sendEmptyMessage(0);
                Looper.loop();

            }
        }.start();
    }

    private void showDeleteAllDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.removeAllMembersTitle)
                .setMessage(R.string.removeAllMembers)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                int size = mListToDisplay.size();
                                if (size <= 0)
                                    return;
                                while ((size--) != 0) {
                                    removeMember(mListToDisplay.get(0));
                                }
                            }
                        }).setNegativeButton(android.R.string.cancel, null);
        mDeleteAllAlertdialog = builder.create();
        mDeleteAllAlertdialog.show();
    }


    private boolean revert() {
        if (!hasNameChange() && !hasMembershipChange()) {
            doRevertAction();
        } else {
            CancelEditDialogFragment.show(this);
        }
        return true;
    }

    private void doRevertAction() {
        // When this Fragment is closed we don't want it to auto-save
        mStatus = Status.CLOSING;
        if (mListener != null)
            mListener.onReverted();
    }

    public static class CancelEditDialogFragment extends DialogFragment {

        public static void show(GroupEditorFragment fragment) {
            CancelEditDialogFragment dialog = new CancelEditDialogFragment();
            dialog.setTargetFragment(fragment, 0);
            dialog.show(fragment.getFragmentManager(), "cancelEditor");
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.cancel_confirmation_dialog_message)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        DialogInterface dialogInterface,
                                        int whichButton) {
                                    ((GroupEditorFragment) getTargetFragment())
                                            .doRevertAction();
                                }
                            }).setNegativeButton(android.R.string.cancel, null)
                    .create();
            return dialog;
        }
    }

    /**
     * Saves or creates the group based on the mode, and if successful finishes
     * the activity. This actually only handles saving the group name.
     *
     * @return true when successful
     */
    public boolean save(int saveMode) {
        if (!hasValidGroupName() || mStatus != Status.EDITING) {
            /*
             * fOR TIME BEING mStatus = Status.CLOSING; if (mListener != null) {
             * mListener.onReverted(); }
             */
            return false;
        }

        // If we are about to close the editor - there is no need to refresh the
        // data
        if (saveMode == SaveMode.CLOSE) {
            getLoaderManager().destroyLoader(LOADER_EXISTING_MEMBERS);
        }

        // If there are no changes, then go straight to onSaveCompleted()
        if (!hasNameChange() && !hasMembershipChange()) {
            if (mProgressDialogSave != null
            && mProgressDialogSave.isShowing() == true ){
                mProgressDialogSave.dismiss();
                mProgressDialogSave = null;
            }
            onSaveCompleted(false, SaveMode.CLOSE, mGroupUri);
            return true;
        }

        mStatus = Status.SAVING;

        Activity activity = getActivity();
        // If the activity is not there anymore, then we can't continue with the
        // save process.
        if (activity == null) {
            return false;
        }
        Intent saveIntent = null;
        if (Intent.ACTION_INSERT.equals(mAction)) {
            // Create array of raw contact IDs for contacts to add to the group
            long[] membersToAddArray = convertToArray(mListMembersToAdd);

            // Create the save intent to create the group and add members at the
            // same time
            saveIntent = ContactSaveService
                    .createNewGroupIntent(activity, new AccountWithDataSet(
                            mAccountName, mAccountType, mDataSet),
                            mGroupNameView.getText().toString(),
                            membersToAddArray, activity.getClass(),
                            GroupEditorActivity.ACTION_SAVE_COMPLETED);
        } else if (Intent.ACTION_EDIT.equals(mAction)) {
            // Create array of raw contact IDs for contacts to add to the group
            long[] membersToAddArray = convertToArray(mListMembersToAdd);

            // Create array of raw contact IDs for contacts to add to the group
            long[] membersToRemoveArray = convertToArray(mListMembersToRemove);

            // Create the update intent (which includes the updated group name
            // if necessary)
            saveIntent = ContactSaveService.createGroupUpdateIntent(activity,
                    mGroupId, getUpdatedName(), membersToAddArray,
                    membersToRemoveArray, activity.getClass(),
                    GroupEditorActivity.ACTION_SAVE_COMPLETED);
        } else {
            throw new IllegalStateException("Invalid intent action type "
                    + mAction);
        }
        activity.startService(saveIntent);
        return true;
    }

    public void onSaveCompleted(boolean hadChanges, int saveMode, Uri groupUri) {
        boolean success = groupUri != null;
        Log.d(TAG, "onSaveCompleted(" + groupUri + ")");
        if (hadChanges) {
            Toast.makeText(
                    mContext,
                    success ? R.string.groupSavedToast
                            : R.string.groupSavedErrorToast, Toast.LENGTH_SHORT)
                    .show();
        }
        switch (saveMode) {
        case SaveMode.CLOSE:
        case SaveMode.HOME:
            final Intent resultIntent;
            final int resultCode;
            if (success && groupUri != null) {
                final String requestAuthority = groupUri == null ? null
                        : groupUri.getAuthority();

                resultIntent = new Intent();
                if (LEGACY_CONTACTS_AUTHORITY.equals(requestAuthority)) {
                    // Build legacy Uri when requested by caller
                    final long groupId = ContentUris.parseId(groupUri);
                    final Uri legacyContentUri = Uri
                            .parse("content://contacts/groups");
                    final Uri legacyUri = ContentUris.withAppendedId(
                            legacyContentUri, groupId);
                    resultIntent.setData(legacyUri);
                } else {
                    // Otherwise pass back the given Uri
                    resultIntent.setData(groupUri);
                }

                resultCode = Activity.RESULT_OK;
            } else {
                resultCode = Activity.RESULT_CANCELED;
                resultIntent = null;
            }
            // It is already saved, so prevent that it is saved again
            mStatus = Status.CLOSING;
            if (mListener != null) {
                mListener.onSaveFinished(resultCode, resultIntent);
            }
            break;
        case SaveMode.RELOAD:
            // TODO: Handle reloading the group list
        default:
            throw new IllegalStateException("Unsupported save mode " + saveMode);
        }
    }

    private boolean hasValidGroupName() {
        return ((mGroupNameView != null)
                && (false == isGroupNameEmpty())
        && (false == isGroupNameAlreadyExists(mGroupNameView.getText()
                .toString().trim())));
    }

    /**
     * Checks if group name is empty.
     */
    private boolean isGroupNameEmpty() {
        if (mGroupNameView != null
                && TextUtils
                        .isEmpty(mGroupNameView.getText().toString().trim()) == true) {
            mGroupNameView.setError(getString(R.string.no_group_name));
            return true;
        } else {
            return false;
        }
    }


    /*
     * added on- 30-06-2012 returns false if the group name doesn't exists ,
     * returns true if group name already exists
     */
    private boolean isGroupNameAlreadyExists(String gName) {
        final String[] GROUP_PROJECTION = new String[] {
                ContactsContract.Groups._ID, ContactsContract.Groups.TITLE };
        Cursor cursor = null;
        try {
            if (hasNameChange()) {
                cursor = getActivity().getContentResolver().query(
                        ContactsContract.Groups.CONTENT_URI,
                        GROUP_PROJECTION,
                        ContactsContract.Groups.DELETED + "=0" + " AND "
                                + ContactsContract.Groups.TITLE + "=\"" + gName
                                + "\"", null, ContactsContract.Groups.TITLE);
                if (cursor.getCount() > 0) {
                    mGroupNameView
                            .setError(getString(R.string.duplicate_group_name));
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (null != cursor) {
                cursor.close();
            }
        }
        return false;
    }

    private boolean hasNameChange() {
        return mGroupNameView != null
                && !mGroupNameView.getText().toString()
                        .equals(mOriginalGroupName);
    }

    private boolean hasMembershipChange() {
        return mListMembersToAdd.size() > 0 || mListMembersToRemove.size() > 0;
    }

    /**
     * Returns the group's new name or null if there is no change from the
     * original name that was loaded for the group.
     */
    private String getUpdatedName() {
        String groupNameFromTextView = mGroupNameView.getText().toString();
        if (groupNameFromTextView.equals(mOriginalGroupName)) {
            // No name change, so return null
            return null;
        }
        return groupNameFromTextView;
    }

    private static long[] convertToArray(List<Member> listMembers) {
        int size = listMembers.size();
        long[] membersArray = new long[size];
        for (int i = 0; i < size; i++) {
            membersArray[i] = listMembers.get(i).getRawContactId();
        }
        return membersArray;
    }

    private void addExistingMembers(List<Member> members) {

        // Re-create the list to display
        mListToDisplay.clear();
        mListToDisplay.addAll(members);
        mListToDisplay.addAll(mListMembersToAdd);
        mListToDisplay.removeAll(mListMembersToRemove);
        mMemberListAdapter.notifyDataSetChanged();

        // Update the autocomplete adapter (if there is one) so these contacts
        // don't get suggested
        if (mAutoCompleteAdapter != null) {
            mAutoCompleteAdapter.updateExistingMembersList(members);
        }
    }

    private void addMember(Member member) {
        // Update the display list
        // _india: Adding member is the top of the display list.
        mListToDisplay.add(0, member);
        // Add remove member from list to Remove
        if (mListMembersToRemove.contains(member))
            mListMembersToRemove.remove(member);
        else
            mListMembersToAdd.add(member);
        // mListToDisplay.add(member);
        mMemberListAdapter.notifyDataSetChanged();

        if (null != mAutoCompleteAdapter) {
            // Update the autocomplete adapter so the contact doesn't get
            // suggested again
            mAutoCompleteAdapter.addNewMember(member.getContactId());
        }
    }

    private void removeMember(Member member) {
        // If the contact was just added during this session, remove it from the
        // list of
        // members to add
        if (mListMembersToAdd.contains(member)) {
            mListMembersToAdd.remove(member);
        } else {
            // Otherwise this contact was already part of the existing list of
            // contacts,
            // so we need to do a content provider deletion operation
            mListMembersToRemove.add(member);
        }
        // In either case, update the UI so the contact is no longer in the list
        // of
        // members
        mListToDisplay.remove(member);
        if (mListToDisplay.size() == 0 && mMemberHeader != null) {
            mMemberHeader.setVisibility(View.INVISIBLE);
            mMemberHeaderSeperator.setVisibility(View.INVISIBLE);
        }
        mMemberListAdapter.notifyDataSetChanged();

        // Update the autocomplete adapter so the contact can get suggested
        // again
        mAutoCompleteAdapter.removeMember(member.getContactId());
        if(mMemberListAdapter.isEmpty()){
            madapterPosition= -1;
            getActivity().invalidateOptionsMenu();
        }
    }
    /**
     * The listener for the group metadata (i.e. group name, account type, and
     * account name) loader.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupMetaDataLoaderListener = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(mContext, mGroupUri);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            bindGroupMetaData(data);

            // Load existing members
            // Display FC when add contacts to
            // group.
            try {
                getLoaderManager().initLoader(LOADER_EXISTING_MEMBERS, null,
                        mGroupMemberListLoaderListener);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Exeception" + e);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    /**
     * The loader listener for the list of existing group members.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupMemberListLoaderListener = new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return GroupMemberLoader.constructLoaderForGroupEditorQuery(
                    mContext, mGroupId);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            List<Member> listExistingMembers = new ArrayList<Member>();
            data.moveToPosition(-1);
            while (data.moveToNext()) {
                long contactId = data.getLong(GroupEditorQuery.CONTACT_ID);
                long rawContactId = data
                        .getLong(GroupEditorQuery.RAW_CONTACT_ID);
                String lookupKey = data
                        .getString(GroupEditorQuery.CONTACT_LOOKUP_KEY);
                String displayName = data
                        .getString(GroupEditorQuery.CONTACT_DISPLAY_NAME_PRIMARY);
                String photoUri = data
                        .getString(GroupEditorQuery.CONTACT_PHOTO_URI);
                Long photoId = data.getLong(GroupEditorQuery.CONTACT_PHOTO_ID);
                listExistingMembers.add(new Member(rawContactId, lookupKey,
                        contactId, displayName, photoUri, photoId));
            }

            // Update the display list
            addExistingMembers(listExistingMembers);

            // No more updates
            // TODO: move to a runnable
            getLoaderManager().destroyLoader(LOADER_EXISTING_MEMBERS);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    public void addContactToGroup(long rawContactId) {
        loadMemberToAddToGroup(rawContactId, String.valueOf(rawContactId));
    }

    public void addToGroup(final ArrayList<Uri> selectedUri) {
        mSelectedUri = selectedUri;
        if (mSelectedUri != null && mSelectedUri.size() > 0) {
            mProgressDialogAddToGroup = ProgressDialog.show(mContext, "",
                    getString(R.string.adding_contacts));
            mProgressDialogAddToGroup.setCancelable(false);

            mAddSelectedMembersAsyncTask = new AddSelectedUriMembersAsyncTask(
                    mProgressDialogAddToGroup);
            mAddSelectedMembersAsyncTask.execute(true);
        }
    }

    /**
     * The listener to load a summary of details for a contact.
     */
    // TODO: Remove this step because showing the aggregate contact can be
    // confusing when the user
    // just selected a raw contact
    private final LoaderManager.LoaderCallbacks<Cursor> mContactLoaderListener = new LoaderCallbacks<Cursor>() {

        private long mRawContactId;

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            String memberId = args.getString(MEMBER_LOOKUP_URI_KEY);
            mRawContactId = args.getLong(MEMBER_RAW_CONTACT_ID_KEY);
            return new CursorLoader(mContext, Uri.withAppendedPath(
                    Contacts.CONTENT_URI, memberId), PROJECTION_CONTACT, null,
                    null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            if (!cursor.moveToFirst()) {
                mFinishLoad = true;
                if (mLoadingContactIds != null && mLoadingRawContactIds != null
                        && mLoadingContactIds.size() > 0
                        && mLoadingRawContactIds.size() > 0) {
                    mLoadingContactIds.remove(0);
                    mLoadingRawContactIds.remove(0);
                }
                return;
            }
            // Retrieve the contact data fields that will be sufficient to
            // update the adapter with
            // a new entry for this contact
            long contactId = cursor.getLong(CONTACT_ID_COLUMN_INDEX);
            String displayName = cursor
                    .getString(CONTACT_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
            String lookupKey = cursor
                    .getString(CONTACT_LOOKUP_KEY_COLUMN_INDEX);
            String photoUri = cursor.getString(CONTACT_PHOTO_URI_COLUMN_INDEX);
            long photoId = cursor.getLong(CONTACT_PHOTO_ID_COLUMN_INDEX);
            getLoaderManager().destroyLoader(LOADER_NEW_GROUP_MEMBER);
            Member member = new Member(mRawContactId, lookupKey, contactId,
                    displayName, photoUri, photoId);
            addMember(member);

            if (mLoadingContactIds != null && mLoadingRawContactIds != null
                    && mLoadingContactIds.size() > 0
                    && mLoadingRawContactIds.size() > 0) {
                mLoadingContactIds.remove(0);
                mLoadingRawContactIds.remove(0);
                if (mLoadingContactIds.size() == 0) {
                    if (mProgressDialog != null
                            && mProgressDialog.isShowing() == true) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                }
            }
            if (null != mSelectedUri && mSelectedUri.size() == 0
                    && null != mProgressDialogAddToGroup
                    && mProgressDialogAddToGroup.isShowing() == true) {
                mProgressDialogAddToGroup.dismiss();
            }
            mFinishLoad = true;
            if (isAdded() == true) {
                LoaderManager loadManager = getLoaderManager();
                if (loadManager != null) {
                    try {
                        loadManager.destroyLoader(LOADER_NEW_GROUP_MEMBER);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            getLoaderManager().destroyLoader(LOADER_NEW_GROUP_MEMBER);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            mFinishLoad = true;
        }

    };

    /**
     * This represents a single member of the current group.
     */
    public static class Member implements Parcelable {
        private static final Member[] EMPTY_ARRAY = new Member[0];

        // TODO: Switch to just dealing with raw contact IDs everywhere if
        // possible
        private final long mRawContactId;
        private final long mContactId;
        private final Uri mLookupUri;
        private final String mDisplayName;
        private final Uri mPhotoUri;
        private final long mPhotoId;


        public Member(long rawContactId, String lookupKey, long contactId,
                String displayName, String photoUri, long photoId) {
            mRawContactId = rawContactId;
            mContactId = contactId;
            mLookupUri = Contacts.getLookupUri(contactId, lookupKey);
            mDisplayName = displayName;
            mPhotoUri = (photoUri != null) ? Uri.parse(photoUri) : null;
            mPhotoId = photoId;
        }

        public long getRawContactId() {
            return mRawContactId;
        }

        public long getContactId() {
            return mContactId;
        }

        public Uri getLookupUri() {
            return mLookupUri;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public Uri getPhotoUri() {
            return mPhotoUri;
        }

        public long getPhotoId() {
            return mPhotoId;
        }


        @Override
        public boolean equals(Object object) {
            if (object instanceof Member) {
                Member otherMember = (Member) object;
                return Objects.equal(mLookupUri, otherMember.getLookupUri());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return mLookupUri == null ? 0 : mLookupUri.hashCode();
        }

        // Parcelable
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mRawContactId);
            dest.writeLong(mContactId);
            dest.writeParcelable(mLookupUri, flags);
            dest.writeString(mDisplayName);
            dest.writeParcelable(mPhotoUri, flags);
            dest.writeLong(mPhotoId);
        }

        private Member(Parcel in) {
            mRawContactId = in.readLong();
            mContactId = in.readLong();
            mLookupUri = in.readParcelable(getClass().getClassLoader());
            mDisplayName = in.readString();
            mPhotoUri = in.readParcelable(getClass().getClassLoader());
            mPhotoId = in.readLong();
        }

        public static final Parcelable.Creator<Member> CREATOR = new Parcelable.Creator<Member>() {
            @Override
            public Member createFromParcel(Parcel in) {
                return new Member(in);
            }

            @Override
            public Member[] newArray(int size) {
                return new Member[size];
            }
        };
    }

    /**
     * Async task to add selected group members.
     */
    public class AddGroupMembersAsyncTask extends
            AsyncTask<Object, Void, Boolean> {
        /** Progress dialog shown while async task is on going */
        ProgressDialog mProgressDialog = null;
        /** Activity */
        GroupEditorActivity activity = null;

        ArrayList<Long> mRawContactIds = null;
        ArrayList<Long> mContactIds = null;

        /** Constructor */
        public AddGroupMembersAsyncTask(ProgressDialog progressDialog) {
            mProgressDialog = progressDialog;
            mRawContactIds = new ArrayList<Long>();
            mRawContactIds.addAll(mLoadingRawContactIds);
            mContactIds = new ArrayList<Long>();
            mContactIds.addAll(mLoadingContactIds);
            mFinishLoad = true;
        }

        /** Detach activity */
        void detach() {
            activity = null;
        }

        /** Attach activity */
        void attach(GroupEditorActivity activity) {
            this.activity = activity;
        }

        @Override
        protected Boolean doInBackground(Object... args) {
            Boolean result = false;
            mFinishLoad = true;
            try {
                int i = 0;
                if (mLoadingContactIds != null) {
                    if (Looper.myLooper() == null) {
                        Looper.prepare();
                    }
                    while (i < mContactIds.size()) {
                        if (mFinishLoad) {
                            mFinishLoad = false;
                            long rawContactId = mRawContactIds.get(i);
                            long contactId = mContactIds.get(i);
                            loadMemberToAddToGroup(rawContactId,
                                    String.valueOf(contactId));
                            i++;
                        }
                    }
                    if (mContactIds.size() >= i) {
                        result = true;
                        return result;
                    }
                    Looper.loop();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (null != mProgressDialog && mProgressDialog.isShowing() == true) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
        }
    }

    /**
     * Async task to add selected group members.
     */
    public class AddSelectedUriMembersAsyncTask extends
            AsyncTask<Object, Void, Boolean> {

        /** Progress dialog shown while async task is on going */
        ProgressDialog mProgressDialog = null;
        /** Activity */
        GroupEditorActivity activity = null;
        ArrayList<Uri> mAllSelectedUri = null;

        /** Constructor */
        public AddSelectedUriMembersAsyncTask(ProgressDialog progressDialog) {
            mProgressDialog = progressDialog;
            mAllSelectedUri = new ArrayList<Uri>();
            mAllSelectedUri.addAll(mSelectedUri);
            mFinishLoad = true;
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            Boolean result = false;
            mFinishLoad = true;
            try {
                if (Looper.myLooper() == null) {
                    Looper.prepare();
                }
                int i = 0;
                if (mAllSelectedUri != null) {
                    int size = mAllSelectedUri.size();
                    while (i < size) {
                        if (mFinishLoad) {
                            mFinishLoad = false;
                            Uri uri = mAllSelectedUri.get(i);
                            addContactToGroup(ContentUris.parseId(uri));
                            mSelectedUri.remove(uri);
                            i++;
                            // mAutoCompleteAdapter.addNewMember(contactId[i++]);
                        }
                    }
                    if (size >= i) {
                        result = true;
                        return result;
                    }
                }
                Looper.loop();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }

        /** Detach activity */
        void detach() {
            activity = null;
        }

        /** Attach activity */
        void attach(GroupEditorActivity activity) {
            this.activity = activity;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (null != mProgressDialog && mProgressDialog.isShowing() == true) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
        }
    }

    /**
     * This adapter displays a list of members for the current group being
     * edited.
     */
    private final class MemberListAdapter extends BaseAdapter {

        private boolean mIsGroupMembershipEditable = true;

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View result;
            mMemberHeader = (LinearLayout) parent.getRootView().findViewById(
                    R.id.member_header);
            mMemberCount = (TextView) parent.getRootView().findViewById(
                    R.id.count);
            mMemberHeaderSeperator = (ImageView) parent.getRootView()
                    .findViewById(R.id.member_header_seperator);
            int size = mListToDisplay.size();
            if (size == 0) {
                mMemberHeader.setVisibility(View.INVISIBLE);
                mMemberHeaderSeperator.setVisibility(View.INVISIBLE);
            } else {
                String countString = getResources().getQuantityString(
                        R.plurals.numberOfMembers, size, size);
                mMemberHeader.setVisibility(View.VISIBLE);
                mMemberHeaderSeperator.setVisibility(View.VISIBLE);
                mMemberCount.setText(countString);
            }

            if (convertView == null) {
                result = mLayoutInflater.inflate(
                        mIsGroupMembershipEditable ? R.layout.group_member_item
                                : R.layout.external_group_member_item, parent,
                        false);
            } else {
                result = convertView;
            }
            final Member member = getItem(position);

            QuickContactBadge badge = (QuickContactBadge) result
                    .findViewById(R.id.badge);
            badge.setTag(member);
            badge.setEnabled(false);
            badge.setFocusable(false);
            
            TextView name = (TextView) result.findViewById(R.id.name);
            if (member.getDisplayName() == null) {
                name.setText(mContext.getString(R.string.missing_name));
            } else {
                name.setText(member.getDisplayName());
            }

            ImageView deleteButton = (ImageView) result.findViewById(R.id.delete_button);
            deleteButton.setVisibility(View.GONE);
            if (deleteButton != null) {
                deleteButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showRemoveMemberDialog(member);
                    }
                });
            }

            mListView.setFocusable(true);
            long photoId = member.getPhotoId();
            notifyDataSetChanged();
            return result;
        }

        void showRemoveMemberDialog(final Member member) {
            mMemberToRemove = member;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.removeMemberTitle)
                    .setMessage(R.string.removeMember)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    removeMember(member);
                                }
                            }).setNegativeButton(android.R.string.cancel, null);
            mRemoveMemberDialog = builder.create();
            mRemoveMemberDialog.show();
        }

        @Override
        public int getCount() {
            return mListToDisplay.size();
        }

        @Override
        public Member getItem(int position) {
            return mListToDisplay.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public void setIsGroupMembershipEditable(boolean editable) {
            mIsGroupMembershipEditable = editable;
        }
    }

    private boolean isContactExist(Uri lookupUri) {
        if (lookupUri == null) {
            return false;
        }
        Cursor cursor = mContext.getContentResolver().query(lookupUri,
                new String[] { Contacts._ID }, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.close();
            return true;
        }
        return false;
    }

    private boolean isPhoneAccount() {
        return true;
    }

    TextWatcher mGroupNameTextWatcher = new TextWatcher() {
        int mBeforeText = 0, mAfterText = 0;

        @Override
        public void onTextChanged(CharSequence s, int start, int before,
                int count) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                int after) {
            mBeforeText = s.toString().getBytes().length;
        }

        @Override
        public void afterTextChanged(Editable s) {
            mAfterText = s.toString().getBytes().length;
            try {
                if (mIsPhoneAccount && (mBeforeText != mAfterText)) {
                    if (s.toString().getBytes().length >= MaxLength.GroupMebership.GROUP_NAME) {
                        mGroupNameView.setError(getResources().getString(
                                R.string.max_limit_reached));
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    synchronized (this) {
                                        this.wait(Constants.ERROR_TOAST_TIME);
                                    }
                                    mTextFieldHandler.sendEmptyMessage(1);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    } else {
                        mGroupNameView.setError(null);
                    }
                }
            } catch (Exception e) {

            }
        }
    };

    final Handler mTextFieldHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (mGroupNameView != null) {
                mGroupNameView.setError(null);
            }
        };
    };
}
