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
package com.android.contacts.activities;

import android.app.ActionBar;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.editor.ContactEditorFragment.SaveMode;
import com.android.contacts.group.GroupEditorFragment;
import com.android.contacts.common.model.account.GroupDataHolder;
import com.android.contacts.util.Constants;
import com.android.contacts.util.DialogManager;
import com.android.contacts.util.PhoneCapabilityTester;

import java.util.ArrayList;

public class GroupEditorActivity extends ContactsActivity
        implements DialogManager.DialogShowingViewActivity {

    private static final String TAG = "GroupEditorActivity";

    public static final String ACTION_SAVE_COMPLETED = "saveCompleted";
    public static final String ACTION_ADD_MEMBER_COMPLETED = "addMemberCompleted";
    public static final String ACTION_REMOVE_MEMBER_COMPLETED = "removeMemberCompleted";
   ProgressDialog mProgressDialogSave = null;

    private GroupEditorFragment mFragment;
    private final String KEY_IS_PROGRESS_DIALOG_SAVE_ONGOING = "progress_dialog_save_ongoing";
    private ArrayList<String> mSelectedUriStrings; /* @$ For Handling selected Uris in String format*/
    private ArrayList<Uri> mSelectedUri=null; /* @$ Contains the Uri converted from the string */
    private boolean mIsMultiContactSelection=false;/* @$ Flag to indicate whether group creation request is from
                                                   * multi contact selection by user*/

    private DialogManager mDialogManager = new DialogManager(this);

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        final Intent intent = getIntent();
        String action = getIntent().getAction();

        if (ACTION_SAVE_COMPLETED.equals(action)) {
            finish();
            return;
        }

        setContentView(R.layout.group_editor_activity);
        if (savedState != null) {
            if (savedState.getBoolean(KEY_IS_PROGRESS_DIALOG_SAVE_ONGOING,
                    false) == true) {
                mProgressDialogSave = ProgressDialog.show(this, "",
                        getString(R.string.saving_group));
            }
        }
        // if this activity started for Adding contact to group then set the
        // flag
        if (Constants.MULTI_USER_CONTACT_SELECTION.equals(intent
                .getStringExtra(Constants.MULTI_USER_CONTACT_SELECTION))) {
            mIsMultiContactSelection = true;
            mSelectedUriStrings = (ArrayList<String>) getIntent()
                    .getStringArrayListExtra(Constants.SELECTED_CONTACT_URI);
            if (mSelectedUriStrings != null) {
                mSelectedUri = new ArrayList<Uri>();
                for (int i = 0; i < mSelectedUriStrings.size(); i++)
                    mSelectedUri.add(Uri.parse(mSelectedUriStrings.get(i)));
            }
        }

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // Inflate a custom action bar that contains the "done" button for
            // saving changes
            // to the group
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View customActionBarView = inflater.inflate(
                    R.layout.editor_custom_action_bar, null);
            TextView mContactEditText = (TextView) customActionBarView.findViewById(R.id.page_editor_text);
            mContactEditText.setText(R.string.editGroupDescription);
            // Show the custom action bar but hide the home icon and title
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME
                            | ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setCustomView(customActionBarView);
        }

        mFragment = (GroupEditorFragment) getFragmentManager()
                .findFragmentById(R.id.group_editor_fragment);
        mFragment.setListener(mFragmentListener);
        mFragment.setContentResolver(getContentResolver());

        // NOTE The fragment will restore its state by itself after orientation
        // changes, so
        // we need to do this only for a new instance.
        if (savedState == null) {
            mFragment.addToGroup(mSelectedUri);
            Uri uri = Intent.ACTION_EDIT.equals(action) ? getIntent().getData()
                    : null;
            mFragment.load(action, uri, getIntent().getExtras());
        }
        GroupDataHolder data = (GroupDataHolder) getLastNonConfigurationInstance();
        if (data != null) {
            mFragment.setSelectedData(data);
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return mFragment.getSelectedData();
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (DialogManager.isManagedId(id)) {
            return mDialogManager.onCreateDialog(id, args);
        } else {
            // Nobody knows about the Dialog
            Log.w(TAG, "Unknown dialog requested, id: " + id + ", args: "
                    + args);
            return null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        boolean isProgressDialogShown = (mProgressDialogSave != null)
                && (mProgressDialogSave.isShowing());
        outState.putBoolean(KEY_IS_PROGRESS_DIALOG_SAVE_ONGOING,
                isProgressDialogShown);
        if ((isProgressDialogShown == true) && (mProgressDialogSave != null)) {
            mProgressDialogSave.dismiss();
        }
    }

    @Override
    public void onBackPressed() {
        // If the change could not be saved, then revert to the default "back"
        // button behavior.
        mProgressDialogSave = new ProgressDialog(this);

        if (!mFragment.save(SaveMode.CLOSE)) {
            if (mProgressDialogSave != null
                    && mProgressDialogSave.isShowing() == true) {
                mProgressDialogSave.dismiss();
            }
            Toast.makeText(getBaseContext(), R.string.group_cancel,
                    Toast.LENGTH_SHORT).show();
            super.onBackPressed();
        } else {
            mProgressDialogSave = mProgressDialogSave.show(this, "",
                    getString(R.string.saving_group));

        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (mFragment == null) {
            return;
        }
        String action = intent.getAction();
        if (ACTION_SAVE_COMPLETED.equals(action)) {
            if (mProgressDialogSave != null
                    && mProgressDialogSave.isShowing() == true) {
                mProgressDialogSave.dismiss();
            }

            // if its adding contact to group then finish this activity and
            // return result
            if (mIsMultiContactSelection) {
                setResultAndFinish(intent);
                return;
            }
            mFragment.onSaveCompleted(true, intent.getIntExtra(
                    GroupEditorFragment.SAVE_MODE_EXTRA_KEY, SaveMode.CLOSE),
                    intent.getData());
        }
    }

    /**
     * Finishes the activity and return the result
     *
     * @param result
     *            Intent
     */
    private void setResultAndFinish(Intent result) {
        setResult(RESULT_OK, result);
        mIsMultiContactSelection = false;
        finish();
    }

    private final GroupEditorFragment.Listener mFragmentListener =
            new GroupEditorFragment.Listener() {
        @Override
        public void onGroupNotFound() {
            finish();
        }

        @Override
        public void onReverted() {
            finish();
        }

        @Override
        public void onAccountsNotFound() {
            finish();
        }

        @Override
        public void onSaveFinished(int resultCode, Intent resultIntent) {
            // TODO: Collapse these 2 cases into 1 that will just launch an intent with the VIEW
            // action to see the group URI (when group URIs are supported)
            // For a 2-pane screen, set the activity result, so the original activity (that launched
            // the editor) can display the group detail page
            if (PhoneCapabilityTester.isUsingTwoPanes(GroupEditorActivity.this)) {
                setResult(resultCode, resultIntent);
            } else if (resultIntent != null) {
                // For a 1-pane screen, launch the group detail page
                Intent intent = new Intent(GroupEditorActivity.this, GroupDetailActivity.class);
                intent.setData(resultIntent.getData());
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
            finish();
        }
    };

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }

}
