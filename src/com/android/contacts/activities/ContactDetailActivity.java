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
 * limitations under the License
 */

package com.android.contacts.activities;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.detail.ContactDetailDisplayUtils;
import com.android.contacts.detail.ContactDetailFragment;
import com.android.contacts.detail.ContactDetailLayoutController;
import com.android.contacts.detail.ContactLoaderFragment;
import com.android.contacts.detail.ContactLoaderFragment.ContactLoaderFragmentListener;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.model.Contact;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.PhoneCapabilityTester;

import java.util.ArrayList;

public class ContactDetailActivity extends ContactsActivity implements View.OnClickListener{
    private static final String TAG = "ContactDetailActivity";

    /** Shows a toogle button for hiding/showing updates. Don't submit with true */
    private static final boolean DEBUG_TRANSITIONS = false;

    /** Shows a alert dialog for contact not found. Don't directly finish */
    private static final int CONTACT_NOT_FOUND_DIALOG = 0;

    /** Shows the  CONTACT_NOT_FOUND_DIALOG when viewvcard from mms */
    private boolean mIsFromVcard = false;
    private static final String VIEW_VCARD = "VIEW_VCARD_FROM_MMS";
    private Contact mContactData;
    private Uri mLookupUri;

    private ContactDetailLayoutController mContactDetailLayoutController;
    private ContactLoaderFragment mLoaderFragment;

    private Handler mHandler = new Handler();
    //add for UX_Enhance_Contacts details view
    private ImageView mBack;
    private TextView mName;
    private TextView mNameSub;
    private View mName_view;
    private ImageView mStar;
    private ImageView mPhoto;
    private boolean mStarred;

    /** Airplane receiver */
    private BroadcastReceiver mAirplaneModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                boolean isAirMode = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.AIRPLANE_MODE_ON, 0) == 1;
                if (isAirMode && mContactData != null
                        && mContactData.isSimAccountType(ContactDetailActivity.this)) {
                    finish();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        // judget if view the vcard from mms to decide whether to show the CONTACT_NOT_FOUND_DIALOG.
        mIsFromVcard = getIntent().getBooleanExtra(VIEW_VCARD,false) ;
        if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
            // This activity must not be shown. We have to select the contact in the
            // PeopleActivity instead ==> Create a forward intent and finish
            final Intent originalIntent = getIntent();
            Intent intent = new Intent();
            intent.setAction(originalIntent.getAction());
            intent.setDataAndType(originalIntent.getData(), originalIntent.getType());

            // If we are launched from the outside, we should create a new task, because the user
            // can freely navigate the app (this is different from phones, where only the UP button
            // kicks the user into the full app)
            if (shouldUpRecreateTask(intent)) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK |
                        Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            } else {
                intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS |
                        Intent.FLAG_ACTIVITY_FORWARD_RESULT | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }

            intent.setClass(this, PeopleActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.contact_detail_activity);
        initView();

        mContactDetailLayoutController = new ContactDetailLayoutController(this, savedState,
                getFragmentManager(), null, findViewById(R.id.contact_detail_container),
                mContactDetailFragmentListener);

        // We want the UP affordance but no app icon.
        // Setting HOME_AS_UP, SHOW_TITLE and clearing SHOW_HOME does the trick.
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE
                    | ActionBar.DISPLAY_SHOW_HOME);
            actionBar.setTitle("");
            actionBar.hide();
        }

        // register Airplane receiver
        IntentFilter airIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mAirplaneModeReceiver, airIntentFilter);
        //Log.i(TAG, getIntent().getData().toString());  //delete the log for NullPointerException of Monkey test
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
         if (fragment instanceof ContactLoaderFragment) {
            mLoaderFragment = (ContactLoaderFragment) fragment;
            mLoaderFragment.setListener(mLoaderFragmentListener);
            mLoaderFragment.loadUri(getIntent().getData());
        }
    }

    @Override
    protected void onDestroy() {
        // Unregister receiver
        if (!PhoneCapabilityTester.isUsingTwoPanes(this)) {
            unregisterReceiver(mAirplaneModeReceiver);
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // First check if the {@link ContactLoaderFragment} can handle the key
        if (mLoaderFragment != null && mLoaderFragment.handleKeyDown(keyCode)) return true;

        // Otherwise find the correct fragment to handle the event
        FragmentKeyListener mCurrentFragment = mContactDetailLayoutController.getCurrentPage();
        if (mCurrentFragment != null && mCurrentFragment.handleKeyDown(keyCode)) return true;

        // In the last case, give the key event to the superclass.
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mContactDetailLayoutController != null) {
            mContactDetailLayoutController.onSaveInstanceState(outState);
        }
    }

    private final ContactLoaderFragmentListener mLoaderFragmentListener =
            new ContactLoaderFragmentListener() {
        @Override
        public void onContactNotFound() {
            // if viewvcard from mms when the contact is deleted, show the dialog,
            // otherwise need not the show it just finish the activity.
            if(true == mIsFromVcard) {
                // if contact not found, prompts a alert dialog
                showDialog(CONTACT_NOT_FOUND_DIALOG);
            } else {
                finish();
            }
        }

        @Override
        public void onDetailsLoaded(final Contact result) {
            if (result == null) {
                return;
            }
            // Since {@link FragmentTransaction}s cannot be done in the onLoadFinished() of the
            // {@link LoaderCallbacks}, then post this {@link Runnable} to the {@link Handler}
            // on the main thread to execute later.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // If the activity is destroyed (or will be destroyed soon), don't update the UI
                    if (isFinishing()) {
                        return;
                    }
                    mContactData = result;
                    mLookupUri = result.getLookupUri();
                    invalidateOptionsMenu();
                    setupBar();
                    mContactDetailLayoutController.setContactData(mContactData);
                }
            });
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            intent.putExtra(
                    ContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, true);
            // Don't finish the detail activity after launching the editor because when the
            // editor is done, we will still want to show the updated contact details using
            // this activity.
            startActivity(intent);
        }

        @Override
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(ContactDetailActivity.this, contactUri, true);
        }
    };

    /**
     * Setup the activity title and subtitle with contact name and company.
     */
    private void setupBar() {
        CharSequence displayName = ContactDetailDisplayUtils.getDisplayName(this, mContactData);
        String company =  ContactDetailDisplayUtils.getCompany(this, mContactData);

        mName.setText(displayName);
        if(!TextUtils.isEmpty(company)) {
            mNameSub.setVisibility(View.VISIBLE);
            mNameSub.setText(company);
        }

        byte[] photoBytes = mContactData.getPhotoBinaryData();
        if (photoBytes != null) {
            final Bitmap photo = BitmapFactory.decodeByteArray(photoBytes, 0,
                    photoBytes.length);

            mPhoto.setImageBitmap(photo);
        } else {
            mPhoto.setImageResource(R.drawable.ic_contact_picture_holo_light);
        }
       // If there is contact data, update the starred state
        if (mContactData != null) {
            ContactDetailDisplayUtils.configureStarredMenuItem(mStar,
                    mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                    mContactData.getStarred());
            mStarred = mContactData.getStarred();
        }

        if (!TextUtils.isEmpty(displayName)) {
            AccessibilityManager accessibilityManager =
                    (AccessibilityManager) this.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (accessibilityManager.isEnabled()) {
                View decorView = getWindow().getDecorView();
                decorView.setContentDescription(displayName);
                decorView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            }
        }
    }

    private final ContactDetailFragment.Listener mContactDetailFragmentListener =
            new ContactDetailFragment.Listener() {
        @Override
        public void onItemClicked(Intent intent) {
            if (intent == null) {
                return;
            }
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found for intent: " + intent);
            }
        }

        @Override
        public void onCreateRawContactRequested(
                ArrayList<ContentValues> values, AccountWithDataSet account) {
            Toast.makeText(ContactDetailActivity.this, R.string.toast_making_personal_copy,
                    Toast.LENGTH_LONG).show();
            Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                    ContactDetailActivity.this, values, account,
                    ContactDetailActivity.class, Intent.ACTION_VIEW);
            startService(serviceIntent);

        }
    };

    /**
     * This interface should be implemented by {@link Fragment}s within this
     * activity so that the activity can determine whether the currently
     * displayed view is handling the key event or not.
     */
    public interface FragmentKeyListener {
        /**
         * Returns true if the key down event will be handled by the implementing class, or false
         * otherwise.
         */
        public boolean handleKeyDown(int keyCode);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case CONTACT_NOT_FOUND_DIALOG:
            return showContactNotFoundDialog();
        }
        return super.onCreateDialog(id);
    }

    /**
     * Create the AlertDialog if contact not found
     */
    private Dialog showContactNotFoundDialog(){

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(android.R.string.dialog_alert_title);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(R.string.noMatchingContacts);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.setCancelable(false);

        return builder.create();
    }

    //add for UX_Enhance_Contacts details view
     private void initView(){
         mName = ((TextView)this.findViewById(R.id.name));
         mNameSub = ((TextView)this.findViewById(R.id.action_bar_subtitle));
         mName_view = this.findViewById(R.id.name_view);
         mName_view.setOnClickListener(this);
         mBack = ((ImageView)this.findViewById(R.id.back));
         mBack.setOnClickListener(this);
         mStar = ((ImageView)this.findViewById(R.id.star));
         mStar.setOnClickListener(this);
         mPhoto = ((ImageView)this.findViewById(R.id.photo));
     }

    //add for UX_Enhance_Contacts details view
     public void onClick(View v) {
         int id = v.getId();
         switch(id){
         case R.id.back:
         case R.id.name_view:
             finish();
             break;
         case R.id.star:
             if (mLookupUri != null && null != mContactData) {
                 // Read the current starred value from the UI instead of using the last
                 // loaded state. This allows rapid tapping without writing the same
                 // value several times
                 mStarred = !mStarred;

                 // To improve responsiveness, swap out the picture (and tag) in the UI already
                 ContactDetailDisplayUtils.configureStarredMenuItem(mStar,
                         mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                         mStarred);

                 // Now perform the real save
                 Intent intent = ContactSaveService.createSetStarredIntent(
                         ContactDetailActivity.this, mLookupUri, mStarred);
                 ContactDetailActivity.this.startService(intent);
             }
             break;
         }
     }
}
