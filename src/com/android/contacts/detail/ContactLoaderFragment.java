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

package com.android.contacts.detail;

import android.accounts.Account;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.Entity;
import android.content.Entity.NamedContentValues;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;


import com.android.contacts.activities.ContactDetailActivity.FragmentKeyListener;
import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsUtils;
import com.android.contacts.interactions.PhoneNumberInteraction;
import com.android.contacts.list.ShortcutIntentBuilder;
import com.android.contacts.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.PhoneAccountType;
import com.android.contacts.model.account.SimAccountType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.Contact;
import com.android.contacts.model.ContactLoader;
import com.android.contacts.model.dataitem.DataItem;
import com.android.contacts.model.dataitem.EmailDataItem;
import com.android.contacts.model.dataitem.OrganizationDataItem;
import com.android.contacts.model.dataitem.PhoneDataItem;
import com.android.contacts.model.dataitem.StructuredPostalDataItem;
import com.android.contacts.model.RawContact;
import com.android.contacts.R;
import com.android.contacts.SimContactsConstants;
import com.android.contacts.util.PhoneCapabilityTester;
import com.android.internal.util.Objects;

import java.util.ArrayList;

/**
 * This is an invisible worker {@link Fragment} that loads the contact details for the contact card.
 * The data is then passed to the listener, who can then pass the data to other {@link View}s.
 */
public class ContactLoaderFragment extends Fragment implements FragmentKeyListener {

    private static final String TAG = ContactLoaderFragment.class.getSimpleName();

    /** The launch code when picking a ringtone */
    private static final int REQUEST_CODE_PICK_RINGTONE = 1;

    /** This is the Intent action to install a shortcut in the launcher. */
    private static final String ACTION_INSTALL_SHORTCUT =
            "com.android.launcher.action.INSTALL_SHORTCUT";

    private boolean mOptionsMenuOptions;
    private boolean mOptionsMenuEditable;
    private boolean mOptionsMenuShareable;
    private boolean mOptionsMenuCanCreateShortcut;
    private boolean mSendToVoicemailState;
    private String mCustomRingtone;

    private static final String ACTION_INSTALL_SHORTCUT_SUCCESSFUL =
            "com.android.launcher.action.INSTALL_SHORTCUT_SUCCESSFUL";
    private static final String EXTRA_RESPONSE_PACKAGENAME = "response_packagename";

    /**
     * This is a listener to the {@link ContactLoaderFragment} and will be notified when the
     * contact details have finished loading or if the user selects any menu options.
     */
    public static interface ContactLoaderFragmentListener {
        /**
         * Contact was not found, so somehow close this fragment. This is raised after a contact
         * is removed via Menu/Delete
         */
        public void onContactNotFound();

        /**
         * Contact details have finished loading.
         */
        public void onDetailsLoaded(Contact result);

        /**
         * User decided to go to Edit-Mode
         */
        public void onEditRequested(Uri lookupUri);

        /**
         * User decided to delete the contact
         */
        public void onDeleteRequested(Uri lookupUri);

    }

    private static final int LOADER_DETAILS = 1;

    private static final String KEY_CONTACT_URI = "contactUri";
    private static final String LOADER_ARG_CONTACT_URI = "contactUri";

    private Context mContext;
    private Uri mLookupUri;
    private ContactLoaderFragmentListener mListener;

    private Contact mContactData;
    private IntentFilter mResponseFilter;

    /** Receive broadcast, show toast only when put shortcut sucessful in laucher */
    private BroadcastReceiver mResponseReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_INSTALL_SHORTCUT_SUCCESSFUL.equals(intent.getAction())) {
                return;
            }
            String packageName = intent.getStringExtra(EXTRA_RESPONSE_PACKAGENAME);
            if (packageName != null && packageName.equals(context.getPackageName())) {
                // Send a toast to give feedback to the user that a shortcut to this
                // contact was added to the launcher.
                Toast.makeText(context, R.string.createContactShortcutSuccessful,
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    public ContactLoaderFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mLookupUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);
        }
        mResponseFilter = new IntentFilter(ACTION_INSTALL_SHORTCUT_SUCCESSFUL);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_CONTACT_URI, mLookupUri);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setHasOptionsMenu(true);
        // This is an invisible view.  This fragment is declared in a layout, so it can't be
        // "viewless".  (i.e. can't return null here.)
        // See also the comment in the layout file.
        return inflater.inflate(R.layout.contact_detail_loader_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mLookupUri != null) {
            Bundle args = new Bundle();
            args.putParcelable(LOADER_ARG_CONTACT_URI, mLookupUri);
            getLoaderManager().initLoader(LOADER_DETAILS, args, mDetailLoaderListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mResponseReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(mResponseReceiver, mResponseFilter);
    }

    public void loadUri(Uri lookupUri) {
        if (Objects.equal(lookupUri, mLookupUri)) {
            // Same URI, no need to load the data again
            return;
        }

        mLookupUri = lookupUri;
        if (mLookupUri == null) {
            getLoaderManager().destroyLoader(LOADER_DETAILS);
            mContactData = null;
            if (mListener != null) {
                mListener.onDetailsLoaded(mContactData);
            }
        } else if (getActivity() != null) {
            Bundle args = new Bundle();
            args.putParcelable(LOADER_ARG_CONTACT_URI, mLookupUri);
            getLoaderManager().restartLoader(LOADER_DETAILS, args, mDetailLoaderListener);
        }
    }

    public void setListener(ContactLoaderFragmentListener value) {
        mListener = value;
    }

    /**
     * The listener for the detail loader
     */
    private final LoaderManager.LoaderCallbacks<Contact> mDetailLoaderListener =
            new LoaderCallbacks<Contact>() {
        @Override
        public Loader<Contact> onCreateLoader(int id, Bundle args) {
            Uri lookupUri = args.getParcelable(LOADER_ARG_CONTACT_URI);
            return new ContactLoader(mContext, lookupUri, true /* loadGroupMetaData */,
                    true /* loadStreamItems */, true /* load invitable account types */,
                    true /* postViewNotification */, true /* computeFormattedPhoneNumber */);
        }

        @Override
        public void onLoadFinished(Loader<Contact> loader, Contact data) {
            if (!mLookupUri.equals(data.getRequestedUri())) {
                Log.e(TAG, "Different URI: requested=" + mLookupUri + "  actual=" + data);
                return;
            }

            if (data.isError()) {
                // This shouldn't ever happen, so throw an exception. The {@link ContactLoader}
                // should log the actual exception.
                throw new IllegalStateException("Failed to load contact", data.getException());
            } else if (data.isNotFound()) {
                Log.i(TAG, "No contact found: " + ((ContactLoader)loader).getLookupUri());
                mContactData = null;
            } else {
                mContactData = data;
            }

            if (mListener != null) {
                if (mContactData == null) {
                    mListener.onContactNotFound();
                } else {
                    mListener.onDetailsLoaded(mContactData);
                }
            }
            // Make sure the options menu is setup correctly with the loaded data.
            if (getActivity() != null) getActivity().invalidateOptionsMenu();
        }

        @Override
        public void onLoaderReset(Loader<Contact> loader) {}
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.view_contact, menu);
    }

    public boolean isOptionsMenuChanged() {
        return mOptionsMenuOptions != isContactOptionsChangeEnabled()
                || mOptionsMenuEditable != isContactEditable()
                || mOptionsMenuShareable != isContactShareable()
                || mOptionsMenuCanCreateShortcut != isContactCanCreateShortcut();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuOptions = isContactOptionsChangeEnabled();
        mOptionsMenuEditable = isContactEditable();
        mOptionsMenuShareable = isContactShareable();
        mOptionsMenuCanCreateShortcut = isContactCanCreateShortcut();
        if (mContactData != null) {
            mSendToVoicemailState = mContactData.isSendToVoicemail();
            mCustomRingtone = mContactData.getCustomRingtone();
        }

        // Hide telephony-related settings (ringtone, send to voicemail)
        // if we don't have a telephone
        final MenuItem optionsSendToVoicemail = menu.findItem(R.id.menu_send_to_voicemail);
        if (optionsSendToVoicemail != null) {
            optionsSendToVoicemail.setChecked(mSendToVoicemailState);
            optionsSendToVoicemail.setVisible(mOptionsMenuOptions);
        }
        final MenuItem optionsRingtone = menu.findItem(R.id.menu_set_ringtone);
        if (optionsRingtone != null) {
            optionsRingtone.setVisible(mOptionsMenuOptions);
        }

        final MenuItem editMenu = menu.findItem(R.id.menu_edit);
        editMenu.setVisible(mOptionsMenuEditable);

        final MenuItem deleteMenu = menu.findItem(R.id.menu_delete);
        deleteMenu.setVisible(mOptionsMenuEditable);

        final MenuItem shareMenu = menu.findItem(R.id.menu_share);
        shareMenu.setVisible(mOptionsMenuShareable);

        final MenuItem createContactShortcutMenu = menu.findItem(R.id.menu_create_contact_shortcut);
        createContactShortcutMenu.setVisible(mOptionsMenuCanCreateShortcut);

        String accoutName = null;
        String accoutType = null;
        String dataSet = null;
        if(mContactData != null) {
            final RawContact rawContact = (RawContact) mContactData.getRawContacts().get(0);
            accoutName = rawContact.getAccountName();
            accoutType = rawContact.getAccountTypeString();
            dataSet = rawContact.getDataSet();
        }

        final MenuItem copyToPhoneMenu = menu.findItem(R.id.menu_copy_to_phone);
        final MenuItem copyToSim1Menu = menu.findItem(R.id.menu_copy_to_sim1);
        final MenuItem copyToSim2Menu = menu.findItem(R.id.menu_copy_to_sim2);         
        
        copyToPhoneMenu.setVisible(false);
        copyToSim1Menu.setVisible(false);
        copyToSim2Menu.setVisible(false);
        if(!TextUtils.isEmpty(accoutType))
        {
            final AccountTypeManager mAccountTypes = AccountTypeManager.getInstance(mContext);
            //final AccountType accountType =
            //            mAccountTypes.getAccountType(accoutType, dataSet);
        
            if(SimContactsConstants.ACCOUNT_TYPE_SIM.equals(accoutType))
            {
                copyToPhoneMenu.setVisible(true);
                copyToPhoneMenu.setTitle("" + getString(R.string.menu_copyTo) 
                            + getString(R.string.phoneLabelsGroup));
                if(MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    if(SimContactsConstants.SIM_NAME_1.equals(accoutName) && hasEnabledIccCard(1))
                    {
                      copyToSim2Menu.setTitle("" + getString(R.string.menu_copyTo) 
                            + getString(R.string.account_sim)+ "<" + SimContactsConstants.SIM_NAME_2+ ">");
                      copyToSim2Menu.setVisible(true);
                    }
                    if(SimContactsConstants.SIM_NAME_2.equals(accoutName) && hasEnabledIccCard(0))
                    {
                      copyToSim1Menu.setTitle("" + getString(R.string.menu_copyTo) 
                            + getString(R.string.account_sim) + "<" + SimContactsConstants.SIM_NAME_1 + ">");
                      copyToSim1Menu.setVisible(true);
                    }
                }
                else {
                    // do nothing, only display copy to phone
                }
                
            }
            else if(SimContactsConstants.ACCOUNT_TYPE_PHONE.equals(accoutType))
            {
                copyToPhoneMenu.setVisible(false);

                if(MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    if(hasEnabledIccCard(0))
                    {
                        copyToSim1Menu.setTitle("" + getString(R.string.menu_copyTo) 
                                + getString(R.string.account_sim) + "<" + SimContactsConstants.SIM_NAME_1 + ">");
                        copyToSim1Menu.setVisible(true);
                    }
                    if(hasEnabledIccCard(1))
                    {
                        copyToSim2Menu.setTitle("" + getString(R.string.menu_copyTo) 
                                + getString(R.string.account_sim) + "<" + SimContactsConstants.SIM_NAME_2 + ">");
                        copyToSim2Menu.setVisible(true);
                    }
                }
                else {
                    if(TelephonyManager.getDefault().hasIccCard() &&
                        TelephonyManager.getDefault().getSimState() == TelephonyManager.SIM_STATE_READY) {
                        copyToSim1Menu.setTitle("" + getString(R.string.menu_copyTo) 
                                + getString(R.string.account_sim));
                        copyToSim1Menu.setVisible(true);
                    }
                }
            }

        }
    }
    
    private boolean hasEnabledIccCard(int subscription) {
        return MSimTelephonyManager.getDefault().hasIccCard(subscription) &&
                   MSimTelephonyManager.getDefault().getSimState(subscription) == TelephonyManager.SIM_STATE_READY;
    }

    public boolean isContactOptionsChangeEnabled() {
        return mContactData != null && !mContactData.isDirectoryEntry()
                && PhoneCapabilityTester.isPhone(mContext);
    }

    public boolean isContactEditable() {
        return mContactData != null && !mContactData.isDirectoryEntry();
    }

    public boolean isContactShareable() {
        return mContactData != null && !mContactData.isDirectoryEntry();
    }

    public boolean isContactCanCreateShortcut() {
        return mContactData != null && !mContactData.isUserProfile()
                && !mContactData.isDirectoryEntry();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_edit: {
                if (mListener != null && mContactData != null) {
                    mListener.onEditRequested(mContactData.getLookupUri());
                }
                break;
            }
            case R.id.menu_delete: {
                if (mListener != null) mListener.onDeleteRequested(mLookupUri);
                return true;
            }
            case R.id.menu_set_ringtone: {
                if (mContactData == null) return false;
                doPickRingtone();
                return true;
            }
            case R.id.menu_send_via_sms: {
                if (mContactData == null) return false;
                sendContactViaSMS();
                return true;
            }
            case R.id.menu_copy_to_phone: {
                if (mContactData == null) return false;
                copyToPhone();
                return true;
            }
            case R.id.menu_copy_to_sim1: {
                if (mContactData == null) return false;
                copyToCard(0);
                return true;
            }
            case R.id.menu_copy_to_sim2: {
                if (mContactData == null) return false;
                copyToCard(1);
                return true;
            }
            case R.id.menu_share: {
                if (mContactData == null) return false;

                final String lookupKey = mContactData.getLookupKey();
                Uri shareUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);
                if (mContactData.isUserProfile()) {
                    // User is sharing the profile.  We don't want to force the receiver to have
                    // the highly-privileged READ_PROFILE permission, so we need to request a
                    // pre-authorized URI from the provider.
                    shareUri = getPreAuthorizedUri(shareUri);
                }

                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_VCARD_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, shareUri);

                // Launch chooser to share contact via
                final CharSequence chooseTitle = mContext.getText(R.string.share_via);
                final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);

                try {
                    mContext.startActivity(chooseIntent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(mContext, R.string.share_error, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            case R.id.menu_send_to_voicemail: {
                // Update state and save
                mSendToVoicemailState = !mSendToVoicemailState;
                item.setChecked(mSendToVoicemailState);
                Intent intent = ContactSaveService.createSetSendToVoicemail(
                        mContext, mLookupUri, mSendToVoicemailState);
                mContext.startService(intent);
                return true;
            }
            case R.id.menu_create_contact_shortcut: {
                // Create a launcher shortcut with this contact
                createLauncherShortcutWithContact();
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a launcher shortcut with the current contact.
     */
    private void createLauncherShortcutWithContact() {
        // Hold the parent activity of this fragment in case this fragment is destroyed
        // before the callback to onShortcutIntentCreated(...)
        final Activity parentActivity = getActivity();

        ShortcutIntentBuilder builder = new ShortcutIntentBuilder(parentActivity,
                new OnShortcutIntentCreatedListener() {

            @Override
            public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
                // Broadcast the shortcutIntent to the launcher to create a
                // shortcut to this contact
                shortcutIntent.setAction(ACTION_INSTALL_SHORTCUT);
                parentActivity.sendBroadcast(shortcutIntent);
            }

        });
        builder.createContactShortcutIntent(mLookupUri);
    }

    /**
     * Calls into the contacts provider to get a pre-authorized version of the given URI.
     */
    private Uri getPreAuthorizedUri(Uri uri) {
        Bundle uriBundle = new Bundle();
        uriBundle.putParcelable(ContactsContract.Authorization.KEY_URI_TO_AUTHORIZE, uri);
        Bundle authResponse = mContext.getContentResolver().call(
                ContactsContract.AUTHORITY_URI,
                ContactsContract.Authorization.AUTHORIZATION_METHOD,
                null,
                uriBundle);
        if (authResponse != null) {
            return (Uri) authResponse.getParcelable(
                    ContactsContract.Authorization.KEY_AUTHORIZED_URI);
        } else {
            return uri;
        }
    }

    @Override
    public boolean handleKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL: {
                if (mListener != null) mListener.onDeleteRequested(mLookupUri);
                return true;
            }
        }
        return false;
    }

    private void copyToPhone() {
        if(ContactsUtils.checkContactsFull(mContext)){
              Toast.makeText(mContext, R.string.contacts_full, 
                                Toast.LENGTH_SHORT).show();
              return;
        }
        String name = mContactData.getDisplayName();
        if(TextUtils.isEmpty(name)) {
            name = "";
        }
        String phoneNumber = "";
        String anrNumber = "";
        String email = "";

        Log.d(TAG, "copyToPhone Contact name: " + name);

        for (RawContact rawContact: mContactData.getRawContacts()) {
            for (DataItem dataItem : rawContact.getDataItems()) {
                if (dataItem.getMimeType() == null) continue;

                if (dataItem instanceof PhoneDataItem) { // Get phone string
                    PhoneDataItem phoneNum = (PhoneDataItem) dataItem;
                    final String number = phoneNum.getNumber();
                    if(!TextUtils.isEmpty(number)) {
                        if(Phone.TYPE_MOBILE == phoneNum.getType()) {
                            phoneNumber = number;
                        } else {
                            if(!anrNumber.equals("")) {
                                anrNumber += ",";
                            }
                            else {
                                anrNumber += number;
                            }
                        }
                    }
                } else if (dataItem instanceof EmailDataItem) { // Get email string
                    EmailDataItem emailData = (EmailDataItem) dataItem;
                    final String address = emailData.getData();
                    if(!TextUtils.isEmpty(address)) {
                        email = address;
                    }
                } 
            }

        }

        String[] value = new String[]{name, phoneNumber, email, anrNumber};
        ContactsUtils.insertToPhone(value, mContext.getContentResolver(), -1 /*PHONE account*/);
        Toast.makeText(mContext,R.string.copy_done,Toast.LENGTH_SHORT).show();
    }

    private Handler mHandler = null;
    private static final int RESULT_EMAIL_FULL_FAILURE = -1;
    private static final int RESULT_ANR_FULL_FAILURE = -3;
    private static final int RESULT_SUCCESS = 1;
    private void copyToCard(final int sub) {
        if (is3GCard(sub))
        {
            final int MSG_SUCCESS = 0;
            final int MSG_ERROR = 1;
            final int MSG_CANCEL = 2;
            final int MSG_NO_EMPTY_EMAIL = 3;
            final int MSG_NO_EMPTY_ANR = 4;
            if(mHandler == null){
                mHandler = new Handler() {
                    public void handleMessage(Message msg) {
                    switch(msg.what){
                        case MSG_SUCCESS:
                           Toast.makeText(mContext,R.string.copy_done,Toast.LENGTH_SHORT).show();
                           break;
                        case MSG_ERROR:
                           Toast.makeText(mContext,R.string.copy_failure,Toast.LENGTH_SHORT).show();
                           break;
                        case MSG_CANCEL:
                            Toast.makeText(mContext, R.string.sim_card_full, Toast.LENGTH_SHORT).show();
                            break;
                        case MSG_NO_EMPTY_EMAIL:
                            Toast.makeText(mContext,R.string.no_empty_email_in_usim, Toast.LENGTH_SHORT).show();
                            break;
                        case MSG_NO_EMPTY_ANR:
                            Toast.makeText(mContext,R.string.number_anr_full, Toast.LENGTH_SHORT).show();
                            break;    
                    }
                  }
                };
            }

            new Thread(new Runnable() {
                public void run() {
                    synchronized (this) {
                        String adn = "1";
                        String anr = "1";
                        int totalEmptyAdn = ContactsUtils.getSimFreeCount(mContext, sub);
                        int totalEmptyAnr = 0;
                        int totalEmptyEmail = 0;

                        Message msg = Message.obtain();
                        if (totalEmptyAdn <= 0)
                        {
                           msg.what = MSG_CANCEL;
                           mHandler.sendMessage(msg);
                           return;
                        }
                
                        try{
                            if(true/*getUimLoaderStatus(sub) == 1*/){
                                totalEmptyAnr = ContactsUtils.getSpareAnrCount(sub);
                                totalEmptyEmail = ContactsUtils.getSpareEmailCount(sub);
                            }
                        }catch(Exception e){
                            Log.d(TAG,"e:"+e);
                            return ;
                        }
                        int numEntity = Integer.parseInt(adn) + Integer.parseInt(anr);
                        int EmptyNumTotal = totalEmptyAdn + totalEmptyAnr;

                        String name = mContactData.getDisplayName();
                        ArrayList<String> arrayNumber = new ArrayList<String>();
                        ArrayList<String> arrayEmail = new ArrayList<String>();

                        for (RawContact rawContact: mContactData.getRawContacts()) {
                            for (DataItem dataItem : rawContact.getDataItems()) {
                                if (dataItem.getMimeType() == null) continue;

                                if (dataItem instanceof PhoneDataItem) { // Get phone string
                                    PhoneDataItem phoneNum = (PhoneDataItem) dataItem;
                                    final String number = phoneNum.getNumber();
                                    if(!TextUtils.isEmpty(number) && EmptyNumTotal-- > 0) {
                                        arrayNumber.add(number);
                                    }
                                } else if (dataItem instanceof EmailDataItem) { // Get email string
                                    EmailDataItem emailData = (EmailDataItem) dataItem;
                                    final String address = emailData.getData();
                                    if(!TextUtils.isEmpty(address) && totalEmptyEmail-- > 0) {
                                        arrayEmail.add(address);
                                    }
                                } 
                            }

                        }
                        int nameCount = (name != null && !name.equals("")) ? 1 : 0;
                        Log.i(TAG, "arrayNumber.size() == " + arrayNumber.size() + " numEntity = " + numEntity);
                        int GroupNumCount = (arrayNumber.size()%numEntity) != 0 ? (arrayNumber.size()/numEntity + 1) : (arrayNumber.size()/numEntity);
                        int GroupEmailCount = arrayEmail.size();
                        int GroupCount = Math.max(GroupEmailCount, Math.max(nameCount, GroupNumCount));
                        Log.i(TAG, "GroupCount= " + GroupCount);
                        ArrayList<UsimEntity> results = new ArrayList<UsimEntity>();
                        for(int i = 0 ; i < GroupCount ; i++){
                            results.add(new UsimEntity());
                        }
                        UsimEntity values;
                        for(int i = 0; i < GroupNumCount; i++)
                        {
                            values = results.get(i);
                            ArrayList<String> numberItem = new ArrayList<String>();
                            for (int j = 0; j < numEntity; j++ )
                            {
                                if((i*numEntity + j) < arrayNumber.size()){
                                    numberItem.add(arrayNumber.get( i*numEntity + j));
                                }
                            }
                            values.putNumberList(numberItem);
                        }

                        for(int i= 0 ; i < GroupEmailCount; i++)
                        {
                            values = results.get(i);
                            values.putEmail(arrayEmail.get(i));
                        }
                    
                        String strEmail = null;
                        String num = null;
                        String anrNum = null;
                        ArrayList<String> EmptyList = new ArrayList<String>();
                        Uri itemUri = null;
                        if(totalEmptyEmail < 0){
                            Message e_msg = Message.obtain();
                            e_msg.what = MSG_NO_EMPTY_EMAIL;
                            mHandler.sendMessage(e_msg);
                        }

                        int ret = 1;
                        boolean showToast = true;
                        for(int i = 0 ; i < GroupCount ; i++){
                            values = results.get(i);
                            if(values.containsNumber()){
                                arrayNumber = (ArrayList<String>)values.getNumberList();
                            }else{
                                arrayNumber = EmptyList;
                            }

                            if(values.containsEmail()){
                                strEmail = (String)values.getEmail();
                            }else{
                                strEmail = null;
                            }
                            num = arrayNumber.size() > 0 ? arrayNumber.get(0) : null;
                            anrNum = arrayNumber.size() > 1 ? arrayNumber.get(1) : null;
                            itemUri = ContactsUtils.insertToCard(mContext, name, num, strEmail, anrNum, sub);
                            Log.i(TAG, "name = " + name + " num=" + num + " strEmail=" + strEmail + " anrNum= " + anrNum);
                            Log.i(TAG, "itemUri = " + itemUri);
                            if(itemUri != null) {
                                ret = Integer.parseInt(itemUri.getLastPathSegment());
                                if(ret == RESULT_EMAIL_FULL_FAILURE) {
                                    itemUri = ContactsUtils.insertToCard(mContext, name, num, "", anrNum, sub);
                                    if(showToast) {
                                        msg.what = MSG_NO_EMPTY_EMAIL;
                                        mHandler.sendMessage(msg);
                                        showToast = false;
                                    }
                                }
                                else if(ret == RESULT_ANR_FULL_FAILURE) {
                                    itemUri = ContactsUtils.insertToCard(mContext, name, num, strEmail, "", sub);
                                    if(showToast) {
                                        msg.what = MSG_NO_EMPTY_ANR;
                                        mHandler.sendMessage(msg);
                                        showToast = false;
                                    }
                                }
                            }
                        }
                        // if show toast MSG_NO_EMPTY_EMAIL or RESULT_ANR_FULL_FAILURE, don't show succes or other error toast
                        if(ret != RESULT_EMAIL_FULL_FAILURE && ret != RESULT_ANR_FULL_FAILURE) {
                            if(itemUri != null){
                                ret = Integer.parseInt(itemUri.getLastPathSegment());
                                if(ret == RESULT_SUCCESS) {
                                    msg.what = MSG_SUCCESS;
                                    mHandler.sendMessage(msg);
                                }
                                else {
                                    msg.what = MSG_ERROR;
                                    mHandler.sendMessage(msg);
                                }
                            }else{
                                msg.what = MSG_ERROR;
                                mHandler.sendMessage(msg);
                            }
                        }
                    }
                }
            }).start(); 
        }
        else {
            Cursor cursor = mContext.getContentResolver().query(mLookupUri, 
                new String [ ]{Contacts.HAS_PHONE_NUMBER, Contacts.DISPLAY_NAME}, null, null, null);
        
            int hasphonenumber = 1;
            String name = "";
    
            if(cursor != null){
                try{
                    if(cursor.moveToFirst()){
                        hasphonenumber = cursor.getInt(0);
                        name = cursor.getString(1);
                    }
                }
                finally{
                    if(cursor != null){
                        cursor.close();
                    }
                }
            }
            if (ContactsUtils.getSimFreeCount(mContext, sub) <= 0)
            {
                Toast.makeText(mContext, R.string.sim_card_full, 
                                Toast.LENGTH_SHORT).show();        
                return ;
            }
            if (hasphonenumber == 0)
            {
               Uri itemUri = ContactsUtils.insertToCard(mContext, name, "", "", "", sub); 
               int ret = Integer.parseInt(itemUri.getLastPathSegment());
               if(ret == 1) {
                    Toast.makeText(mContext,R.string.copy_done,Toast.LENGTH_SHORT).show();
               }
               else {
                    Toast.makeText(mContext,R.string.copy_failure,Toast.LENGTH_SHORT).show();
               }
            }
            else
            {
                PhoneNumberInteraction.startInteractionForNumberCopy(mContext, mLookupUri, sub);
            }
        }
    }

    public String getCardType(int subscription) {
        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        MSimTelephonyManager msimtm = (MSimTelephonyManager)mContext.getSystemService(Context.MSIM_TELEPHONY_SERVICE);
        if (msimtm != null && msimtm.isMultiSimEnabled()) {
            return msimtm.getCardType(subscription);
        }
        if (tm != null && !tm.isMultiSimEnabled()) {
            return tm.getCardType();
        }
        return null;
    }

    public boolean is3GCard(int subscription) {
        String type = getCardType(subscription);
        if ( SimContactsConstants.USIM.equals(type) || SimContactsConstants.CSIM.equals(type) ) {
            return true;
        }
        return false;
    }

    class UsimEntity{
        private ArrayList<String> mNumberList = new ArrayList<String>();
        private String mEmail = null;

        public String getEmail(){
            return mEmail;
        }

        public ArrayList<String> getNumberList(){
            return mNumberList;
        }

        public void putEmail(String email){
            mEmail = email;
        }

        public void putNumberList(ArrayList<String> list){
            mNumberList = list;
        }

        public boolean containsEmail(){
            return mEmail != null;
        }

        public boolean containsNumber(){
            return !mNumberList.isEmpty();
        }
   }

    private void sendContactViaSMS() {
        // Get name string
        String name = mContactData.getDisplayName();
        String phone = null;
        String email = null;
        String postal = null;
        String organization = null;

        Log.d(TAG, "Contact name: " + name);

        for (RawContact rawContact: mContactData.getRawContacts()) {
            for (DataItem dataItem : rawContact.getDataItems()) {
                if (dataItem.getMimeType() == null) continue;

                if (dataItem instanceof PhoneDataItem) { // Get phone string
                    PhoneDataItem phoneNum = (PhoneDataItem) dataItem;
                    final String number = phoneNum.getNumber();
                    if(!TextUtils.isEmpty(number)) {
                        if (phone == null) {
                            phone = number;
                        } else {
                            phone = phone + ", " + number;
                        }
                    }
                } else if (dataItem instanceof EmailDataItem) { // Get email string
                    EmailDataItem emailData = (EmailDataItem) dataItem;
                    final String address = emailData.getData();
                    if(!TextUtils.isEmpty(address)) {
                        if (email == null) {
                            email = address;
                        } else {
                            email = email + ", " + address;
                        }
                    }
                } else if (dataItem instanceof StructuredPostalDataItem) { // Get StructuredPostal address string
                    StructuredPostalDataItem postalData = (StructuredPostalDataItem) dataItem;
                    final String postalAddress = postalData.getFormattedAddress();
                    if (!TextUtils.isEmpty(postalAddress)) {
                        if (postal == null) {
                            postal = postalAddress;
                        } else {
                            postal = postal + ", " + postalAddress;
                        }
                    }
                } else if (dataItem instanceof OrganizationDataItem) { // Get Organization string
                    OrganizationDataItem organizationData = (OrganizationDataItem) dataItem;
                    final String company = organizationData.getCompany();
                    if (!TextUtils.isEmpty(company))
                    if (organization == null) {
                        organization = company;
                    } else {
                        organization = organization + ", " + company;
                    }
                }
            }

        }

        if (TextUtils.isEmpty(name)) {
            name = mContext.getResources().getString(R.string.missing_name);
        }
        name = getString(R.string.nameLabelsGroup) + ":" + name + "\r\n";
        phone = phone == null ? "" : getString(R.string.phoneLabelsGroup) + ":" + phone + "\r\n";
        email = email == null ? "" : getString(R.string.emailLabelsGroup) + ":" + email + "\r\n";
        postal = postal == null ? "" : getString(R.string.postalLabelsGroup) + ":" + postal + "\r\n";
        organization = organization == null ? "" : getString(R.string.organizationLabelsGroup) + ":" + organization + "\r\n";

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("sms_body", name + phone + email + postal + organization);
        intent.setType("vnd.android-dir/mms-sms");
        mContext.startActivity(intent);
    }

    private void doPickRingtone() {

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        // Allow user to pick 'Default'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        // Show only ringtones
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        // Don't show 'Silent'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);

        Uri ringtoneUri;
        if (mCustomRingtone != null) {
            ringtoneUri = Uri.parse(mCustomRingtone);
        } else {
            // Otherwise pick default ringtone Uri so that something is selected.
            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }

        // Put checkmark next to the current ringtone for this contact
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri);

        // Launch!
        startActivityForResult(intent, REQUEST_CODE_PICK_RINGTONE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case REQUEST_CODE_PICK_RINGTONE: {
                Uri pickedUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                handleRingtonePicked(pickedUri);
                break;
            }
        }
    }

    private void handleRingtonePicked(Uri pickedUri) {
        if (pickedUri == null || RingtoneManager.isDefault(pickedUri)) {
            mCustomRingtone = null;
        } else {
            mCustomRingtone = pickedUri.toString();
        }
        Intent intent = ContactSaveService.createSetRingtone(
                mContext, mLookupUri, mCustomRingtone);
        mContext.startService(intent);
    }

    /** Toggles whether to load stream items. Just for debugging */
    public void toggleLoadStreamItems() {
        Loader<Contact> loaderObj = getLoaderManager().getLoader(LOADER_DETAILS);
        ContactLoader loader = (ContactLoader) loaderObj;
        loader.setLoadStreamItems(!loader.getLoadStreamItems());
    }

    /** Returns whether to load stream items. Just for debugging */
    public boolean getLoadStreamItems() {
        Loader<Contact> loaderObj = getLoaderManager().getLoader(LOADER_DETAILS);
        ContactLoader loader = (ContactLoader) loaderObj;
        return loader != null && loader.getLoadStreamItems();
    }
}
