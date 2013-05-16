/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (c) 2012-2013 The Linux Foundation. All rights reserved.
 *
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only
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

package com.android.contacts.interactions;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.telephony.PhoneNumberUtils;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.text.TextUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.Settings;
import android.provider.Settings.System;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import static com.android.internal.telephony.MSimConstants.SUBSCRIPTION_KEY;
import com.android.contacts.editor.SelectAccountDialogFragment;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.AccountSelectionUtil;
import com.android.contacts.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.vcard.ExportVCardActivity;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.editor.MultiPickContactActivity;
import com.android.contacts.SimContactsConstants;
import com.android.contacts.SimContactsOperation;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * An dialog invoked to import/export contacts.
 */
public class ImportExportDialogFragment extends DialogFragment
        implements SelectAccountDialogFragment.Listener {
    public static final String TAG = "ImportExportDialogFragment";
    private static final String SIM_INDEX = "sim_index";

    private static final String KEY_RES_ID = "resourceId";
    private static final String ARG_CONTACTS_ARE_AVAILABLE = "CONTACTS_ARE_AVAILABLE";
    private static int SIM_ID_INVALID = -1;
    private static int mSelectedSim = SIM_ID_INVALID;

    private final String[] LOOKUP_PROJECTION = new String[] {
            Contacts.LOOKUP_KEY
    };

    static final String[] PHONES_PROJECTION = new String[] {
        Phone._ID, //0
        Phone.TYPE, //1
        Phone.LABEL, //2
        Phone.NUMBER, //3
        Phone.DISPLAY_NAME, // 4
        Phone.CONTACT_ID, // 5
    };

    static final int PHONE_ID_COLUMN_INDEX = 0;
    static final int PHONE_TYPE_COLUMN_INDEX = 1;
    static final int PHONE_LABEL_COLUMN_INDEX = 2;
    static final int PHONE_NUMBER_COLUMN_INDEX = 3;
    static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 4;
    static final int PHONE_CONTACT_ID_COLUMN_INDEX = 5;

    // This value needs to start at 7. See {@link PeopleActivity}.
    public static final int SUBACTIVITY_MULTI_PICK_CONTACT = 7;

    private static final int SUB1 = 0;
    private static final int SUB2 = 1;
    private static final int SUB_INVALID = -1;

    public static final String SUBSCRIPTION = "sub_id";  // subscription column key
    public static final String ACTION_MULTI_PICK = Intent.ACTION_GET_CONTENT;   // multi pick contacts action
    public static final String ACTION_MULTI_PICK_EMAIL = "com.android.contacts.action.MULTI_PICK_EMAIL";  // multi-pick contacts which contains email address

    //TODO: we need to refactor the export code in future release.
    public static int mExportSub;                   // QRD enhancement: export subscription selected by user

    //add max count limits of UIM and SIM card
    public static final int MAX_COUNT_UIM_CARD = 500;
    public static final int MAX_COUNT_SIM_CARD = 250;
    // the max count limit of Chinese code or not
    private static final int CONTACT_NAME_MAX_LENGTH_NOT_CHN = 14;
    private static final int CONTACT_NAME_MAX_LENGTH_CHN = 6;

    // QRD enhancement: Toast handler for exporting concat to sim card
    private static final int TOAST_EXPORT_FAILED = 0;
    private static final int TOAST_EXPORT_FINISHED = 1;
    private static final int TOAST_SIM_CARD_FULL = 2; // only for sim card is full
    private static final int TOAST_CONTACT_NAME_TOO_LONG = 3; // only for contact name too long
    private static final int TOAST_EXPORT_CANCELED = 4; //there is a case export is canceled by user
    private static final int TOAST_EMAIL_FULL = 5;
    private static final int TOAST_ANR_FULL = 6;
    private static final int TOAST_SIM_CARD_LOADING = 7;
    private static final int TOAST_SIM_CARD_STOP = 8;

    private static final int RESULT_EMAIL_FULL_FAILURE = -1;
    private static final int RESULT_ANR_FULL_FAILURE = -3;

    // use for log.d or log.i,to disable or enable it.
    private static final boolean DEBUG = false;

    private SimContactsOperation mSimContactsOperation;
    private String name = null;
    private String number = null;
    private ArrayAdapter<Integer> mAdapter;

    private BroadcastReceiver mSimStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (null == mAdapter) {
                return;
            }
            final boolean contactsAreAvailable = getArguments().getBoolean(
                    ARG_CONTACTS_ARE_AVAILABLE);
            loadData(contactsAreAvailable);
            mAdapter.notifyDataSetChanged();
        }
    };
    private static boolean isExportingToSIM = false;
    public static boolean isExportingToSIM(){
        return isExportingToSIM;
    }
    private static ExportToSimThread exportThread = null;
    public ExportToSimThread createExportToSimThread(int type, int subscription, ArrayList<String[]> contactList, Activity mpactiv){
        if (exportThread == null)
            exportThread = new ExportToSimThread(type, subscription, contactList,  mpactiv);
        return exportThread;
    }

    public static void destroyExportToSimThread(){
        exportThread = null;
    }
    public void showExportToSIMProgressDialog(Activity activity){
        exportThread.showExportProgressDialog(activity);
    }
    /** Preferred way to show this dialog */
    public static ImportExportDialogFragment show(FragmentManager fragmentManager, boolean contactsAreAvailable) {
        final ImportExportDialogFragment fragment = new ImportExportDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_CONTACTS_ARE_AVAILABLE, contactsAreAvailable);
        fragment.setArguments(args);
        fragment.show(fragmentManager, ImportExportDialogFragment.TAG);
        return fragment;
    }

    private String getMultiSimName(int subscription) {
        String name = Settings.System.getString(getActivity().getContentResolver(),
            Settings.System.MULTI_SIM_NAME[subscription]);
        if(TextUtils.isEmpty(name) && mactiv != null) {
            name = mactiv.getResources().getStringArray(R.array.select_slot_item)[subscription];
        }

        return name;
    }

    Activity mactiv;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using the correct theme
        mactiv = getActivity();

        // Listening the sim card state change.
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mactiv.registerReceiver(mSimStateReceiver, filter);

        final LayoutInflater dialogInflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final boolean contactsAreAvailable = getArguments().getBoolean(ARG_CONTACTS_ARE_AVAILABLE);

        // Adapter that shows a list of string resources
        mAdapter = new ArrayAdapter<Integer>(getActivity(),
                R.layout.select_dialog_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TextView result = (TextView)(convertView != null ? convertView :
                        dialogInflater.inflate(R.layout.select_dialog_item, parent, false));

                final int resId = getItem(position);
                result.setText(resId);
                return result;
            }
        };

        // Manually call notifyDataSetChanged() to refresh the list.
        mAdapter.setNotifyOnChange(false);
        loadData(contactsAreAvailable);

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean dismissDialog;
                final int resId = mAdapter.getItem(which);
                switch (resId) {
                    case R.string.import_from_sim: {

                        // after click import from sim,we set this value to true.
                        // here set it to true,it indicate that at the end of this method this dialog should dismiss.
                        dismissDialog = true;
                        handleImportFromSimRequest(resId);
                        break;
                        }
                    case R.string.import_from_sdcard: {

                        // After click import from sdcard,we set this value to true.
                        // Here set it to true,it indicate that at the end of this method this dialog should dismiss.
                        dismissDialog = true;
                        handleImportRequest(resId);
                        break;
                    }
                    case R.string.export_to_sim: {
                        dismissDialog = true;
                        handleExportToSimRequest(resId);
                        break;
                    }
                    case R.string.export_to_sdcard: {
                        dismissDialog = true;
                        //Intent exportIntent = new Intent(getActivity(), ExportVCardActivity.class);
                        //getActivity().startActivity(exportIntent);
                        Intent exportIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        exportIntent.setClass(getActivity(), MultiPickContactActivity.class);
                        getActivity().startActivityForResult(exportIntent, PeopleActivity.CODE_EXPORT_CONTACTS);
                        break;
                    }
                    case R.string.share_visible_contacts: {
                        dismissDialog = true;
                        doShareVisibleContacts();
                        break;
                    }
                    default: {
                        dismissDialog = true;
                        Log.e(TAG, "Unexpected resource: "
                                + getActivity().getResources().getResourceEntryName(resId));
                    }
                }
                if (dismissDialog) {
                    dialog.dismiss();
                }
            }
        };
        return new AlertDialog.Builder(getActivity())
                .setTitle(contactsAreAvailable
                        ? R.string.dialog_import_export
                        : R.string.dialog_import)
                .setSingleChoiceItems(mAdapter, -1, clickListener)
                .create();
    }

    /**
     * Loading the menu list data.
     * @param contactsAreAvailable
     */
    private void loadData(boolean contactsAreAvailable) {
        if (null == mactiv && null == mAdapter) {
            return;
        }

        mAdapter.clear();
        final Resources res = mactiv.getResources();
        boolean hasIccCard = false;

        hasIccCard = hasEnabledIccCard();

        if (TelephonyManager.getDefault().hasIccCard()
                && res.getBoolean(R.bool.config_allow_sim_import)) {
            mAdapter.add(R.string.import_from_sim);
        }
        if (res.getBoolean(R.bool.config_allow_import_from_sdcard)) {
            mAdapter.add(R.string.import_from_sdcard);
        }

        boolean hasContact = hasContacts();

        if (hasIccCard && hasContact) {
            mAdapter.add(R.string.export_to_sim);
        }
        if (res.getBoolean(R.bool.config_allow_export_to_sdcard)) {
            // If contacts are available and there is at least one contact in
            // database, show "Export to SD card" menu item. Otherwise hide it
            // because it makes no sense.
            if (contactsAreAvailable && hasContact) {
                mAdapter.add(R.string.export_to_sdcard);
            }
        }
        if (res.getBoolean(R.bool.config_allow_share_visible_contacts)) {
            if (contactsAreAvailable) {
                mAdapter.add(R.string.share_visible_contacts);
            }
        }
    }

    // Judge if there is contacts in database, if has return true, otherwise
    // return false.
    private boolean hasContacts() {
        boolean hasContacts = false;
        if (null != mactiv) {
            Cursor cursor = mactiv.getContentResolver().query(Contacts.CONTENT_URI, new String[] {
                    Contacts._ID }, null, null, null);
            if (null != cursor) {
                try {
                    hasContacts = cursor.getCount() > 0;
                } finally {
                    cursor.close();
                }
            }
        }
        return hasContacts;
    }

    private void doShareVisibleContacts() {
        // TODO move the query into a loader and do this in a background thread
        final Cursor cursor = getActivity().getContentResolver().query(Contacts.CONTENT_URI,
                LOOKUP_PROJECTION, Contacts.IN_VISIBLE_GROUP + "!=0", null, null);
        if (cursor != null) {
            try {
                if (!cursor.moveToFirst()) {
                    Toast.makeText(getActivity(), R.string.share_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                if(cursor.getCount() > 2000){
                    Toast.makeText(getActivity(), R.string.share_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                StringBuilder uriListBuilder = new StringBuilder();
                int index = 0;
                do {
                    if (index != 0)
                        uriListBuilder.append(':');
                    uriListBuilder.append(cursor.getString(0));
                    index++;
                } while (cursor.moveToNext());
                Uri uri = Uri.withAppendedPath(
                        Contacts.CONTENT_MULTI_VCARD_URI,
                        Uri.encode(uriListBuilder.toString()));

                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_VCARD_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                getActivity().startActivity(intent);
            } finally {
                cursor.close();
            }
        }
    }

    /**
     * Handle "import from SIM" and "import from SD".
     *
     * @return {@code true} if the dialog show be closed.  {@code false} otherwise.
     */
    private boolean handleImportRequest(int resId) {
        // There are three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mactiv);
        final List<AccountWithDataSet> accountList = accountTypes.getAccounts(true);
        final int size = accountList.size();
        if (size > 1) {
            // Send over to the account selector
            final Bundle args = new Bundle();
            args.putInt(KEY_RES_ID, resId);
            switch (resId) {
                case R.string.import_from_sim:
                case R.string.import_from_sdcard:
                    SelectAccountDialogFragment.show(
                        mactiv.getFragmentManager(), this,
                        R.string.dialog_new_contact_account,
                        AccountListFilter.ACCOUNTS_CONTACT_WRITABLE_WITHOUT_SIM, args);
                    return false;
            }
            SelectAccountDialogFragment.show(
                    mactiv.getFragmentManager(), this,
                    R.string.dialog_new_contact_account,
                    AccountListFilter.ACCOUNTS_CONTACT_WRITABLE, args);

            // In this case, because this DialogFragment is used as a target fragment to
            // SelectAccountDialogFragment, we can't close it yet.  We close the dialog when
            // we get a callback from it.
            return false;
        }

        AccountSelectionUtil.doImport(mactiv, resId,
                (size == 1 ? accountList.get(0) : null));
        return true; // Close the dialog.
    }

    /**
     * Called when an account is selected on {@link SelectAccountDialogFragment}.
     */
    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        AccountSelectionUtil.doImport(mactiv, extraArgs.getInt(KEY_RES_ID), account);

        // At this point the dialog is still showing (which is why we can use getActivity() above)
        // So close it.
        dismiss();
    }

    @Override
    public void onAccountSelectorCancelled() {
        // See onAccountChosen() -- at this point the dialog is still showing.  Close it.
        dismiss();
    }

    private  void displaySIMSelection() {
        Log.d(TAG, "displayMyDialog");
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.select_sim);
        mSelectedSim = SIM_ID_INVALID;
        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        CharSequence[] subList = new CharSequence[numPhones];
        int i;
        for (i = 1; i <= numPhones; i++) {
            subList[i-1] = "SIM" + i;
        }
        builder.setSingleChoiceItems(subList, -1,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                Log.d(TAG, "onClicked Dialog on arg1 = " + arg1);
                mSelectedSim = arg1;
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.ok),
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "onClicked OK");
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setClassName("com.android.phone",
                    "com.android.phone.ExportContactsToSim");
                intent.putExtra(SIM_INDEX, mSelectedSim);
                if (mSelectedSim != SIM_ID_INVALID) {
                    ((AlertDialog)dialog).getContext().startActivity(intent);
                }
            }
        });
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "onClicked Cancel");
            }
        });

        dialog.setOnDismissListener(new OnDismissListener () {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Log.d(TAG, "onDismiss");
                Log.d(TAG, "Selected SUB = " + mSelectedSim);
            }
        });
        dialog.show();
    }

    private class ExportToSimSelectListener implements DialogInterface.OnClickListener{
        public void onClick(DialogInterface dialog, int which) {
            if (which >= 0) {
                mExportSub = which;
            } else if (which == DialogInterface.BUTTON_POSITIVE) {
                Intent pickPhoneIntent = new Intent(ACTION_MULTI_PICK, Contacts.CONTENT_URI);
                // do not show the contacts in SIM card
                pickPhoneIntent.putExtra(MultiPickContactActivity.EXT_NOT_SHOW_SIM_FLAG, true);
                mactiv.startActivityForResult(pickPhoneIntent, SUBACTIVITY_MULTI_PICK_CONTACT);
            }
        }
    }

    public class ImportFromSimSelectListener implements DialogInterface.OnClickListener{
        public void onClick(DialogInterface dialog, int which) {
            if (which >= 0) {
                AccountSelectionUtil.setImportSubscription(which);
            } else if (which == DialogInterface.BUTTON_POSITIVE) {
                handleImportRequest(R.string.import_from_sim);
            }
        }
    }

    private String getAccountNameBy(int subscription) {
        String accountName = null;
        if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            accountName = SimContactsConstants.SIM_NAME;
        } else {
            if (subscription == 0)
                accountName = SimContactsConstants.SIM_NAME_1;
            else if (subscription == 1)
                accountName = SimContactsConstants.SIM_NAME_2;
        }
        return accountName;
    }

    private  void actuallyImportOneSimContact(
        final ContentValues values, final ContentResolver resolver, int subscription) {

        ContentValues sEmptyContentValues = new ContentValues();
        final String name = values.getAsString(SimContactsConstants.STR_TAG);
        final String phoneNumber = values.getAsString(SimContactsConstants.STR_NUMBER);
        final String emailAddresses = values.getAsString(SimContactsConstants.STR_EMAILS);
        final String anrs = values.getAsString(SimContactsConstants.STR_ANRS);
        final String[] emailAddressArray;
        final String[] anrArray;
        if (!TextUtils.isEmpty(emailAddresses)) {
            emailAddressArray = emailAddresses.split(",");
        } else {
            emailAddressArray = null;
        }
        if (!TextUtils.isEmpty(anrs)) {
            anrArray = anrs.split(",");
        } else {
            anrArray = null;
        }
        Log.d(TAG," actuallyImportOneSimContact: name= " + name +
            ", phoneNumber= " + phoneNumber +", emails= "+ emailAddresses
            +", anrs= "+ anrs + ", sub " + subscription);

        String accountName = getAccountNameBy(subscription);
        String accountType = SimContactsConstants.ACCOUNT_TYPE_SIM;

        final ArrayList<ContentProviderOperation> operationList =
            new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
        builder.withValue(RawContacts.ACCOUNT_NAME, accountName);
        builder.withValue(RawContacts.ACCOUNT_TYPE, accountType);
        builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(StructuredName.DISPLAY_NAME, name);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
        builder.withValue(Phone.NUMBER, phoneNumber);
        builder.withValue(Data.IS_PRIMARY, 1);
        operationList.add(builder.build());

        if (anrArray != null) {
            for (String anr :anrArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
                builder.withValue(Phone.NUMBER, anr);
                //builder.withValue(Data.IS_PRIMARY, 1);
                operationList.add(builder.build());
            }
        }

        if (emailAddresses != null) {
            for (String emailAddress : emailAddressArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
                builder.withValue(Email.ADDRESS, emailAddress);
                operationList.add(builder.build());
            }
        }

        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG,String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    /**
     * A thread that export contacts to sim card
     */
    public class ExportToSimThread extends Thread {
        public static final int TYPE_ALL = 1;
        public static final int TYPE_SELECT = 2;
        private int subscription;
        private int type;
        private boolean canceled;
        private ArrayList<String[]> contactList;
        private ProgressDialog mExportProgressDlg;
        private ContentValues mValues = new ContentValues();
        Activity mpeople;
        private int adnCount = 0;
        boolean showEmailFull = true;
        boolean showAnrFull = true;
        private int freeSimCount = 0;

        public ExportToSimThread(int type, int subscription, ArrayList<String[]> contactList, Activity mpactiv) {
            super();
            this.type = type;
            this.subscription = subscription;
            this.contactList = contactList;
            canceled = false;
            mpeople = mpactiv;
            setExportProgress(contactList.size());
            adnCount = ContactsUtils.getAdnCount(subscription);
        }

        public void dismissPopup() {
            if(null != mExportProgressDlg) {
                mExportProgressDlg.dismiss();
            }
        }

        @Override
        public void run() {
            enablePowerWakeLock(mpeople, "ImportExport");
            freeSimCount = ContactsUtils.getSimFreeCount(mpeople,subscription);
            if(ContactsUtils.getUimLoaderStatus(subscription) != 1){
                mToastHandler.sendEmptyMessage(TOAST_SIM_CARD_LOADING);
                if (mExportProgressDlg != null) {
                    mExportProgressDlg.dismiss();
                    mExportProgressDlg = null;
                }
                disablePowerWakeLock();
                Intent intent = new Intent(PeopleActivity.INTENT_EXPORT_COMPLETE);
                mpeople.sendBroadcast(intent);
                return;
            }
            isExportingToSIM = true;
            String accountName = getAccountNameBy(subscription);
            String accountType = SimContactsConstants.ACCOUNT_TYPE_SIM;
            Account account = new Account(accountName,accountType);
            //GoogleSource.createMyContactsIfNotExist(account, getActivity());

            // in case export is stopped, record the count of inserted successfully
            int insertCount = 0;

            mSimContactsOperation = new SimContactsOperation(mpeople);
            Cursor cr = null;
            // call query first, otherwise insert will fail if this insert is called without any query before
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                if (subscription == SUB1) {
                    cr = mpeople.getContentResolver().query(Uri.parse("content://iccmsim/adn"),
                            null, null, null, null);
                } else if (subscription == SUB2) {
                    cr = mpeople.getContentResolver().query(Uri.parse("content://iccmsim/adn_sub2"),
                            null, null, null, null);
                } 
            } else {
                cr = mpeople.getContentResolver().query(Uri.parse("content://icc/adn"), null,
                        null, null, null);
            }
            if (cr != null) {
                cr.close();
            }

            boolean canSaveEmail = false;
            String CardType = "";
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                CardType = MSimTelephonyManager.getDefault().getCardType(subscription);
            }
            else {
                CardType = TelephonyManager.getDefault().getCardType();
            }
            // If the SIM card is USIM or CSIM, emails can be saved to the SIM phone book
            if (SimContactsConstants.USIM.equals(CardType)
                    || SimContactsConstants.CSIM.equals(CardType)) {
                canSaveEmail = true;
            }
            String emails = null;
            if (type == TYPE_SELECT) {
                if (contactList != null) {
                    Iterator<String[]> iterator = contactList.iterator();
                    Uri result = null;
                    boolean isSimCardFull = false;
                    while (iterator.hasNext() && !canceled) {
                        String[] contactInfo = iterator.next();
                        String name = "";
                        ArrayList<String> arrayNumber = new ArrayList<String>();
                        ArrayList<String> arrayEmail = new ArrayList<String>();

                        Uri dataUri = Uri.withAppendedPath(
                                ContentUris.withAppendedId(Contacts.CONTENT_URI, Long.parseLong(contactInfo[1])),
                                Contacts.Data.CONTENT_DIRECTORY);
                        final String[] projection = new String[] {
                                Contacts._ID, 
                                Contacts.Data.MIMETYPE, 
                                Contacts.Data.DATA1,
                        };

                        Cursor c = mpeople.getContentResolver().query(dataUri, projection, null, null, null);

                        if (c != null && c.moveToFirst()) {
                            do {
                                String mimeType = c.getString(1);
                                if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                                    String number = c.getString(2);
                                    if(!TextUtils.isEmpty(number)) {
                                        arrayNumber.add(number);
                                    }
                                } else if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                                    name = c.getString(2);
                                }
                                if (canSaveEmail) {
                                    if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                                        String email = c.getString(2);
                                        if(!TextUtils.isEmpty(email)) {
                                            arrayEmail.add(email);
                                        }           
                                    } 
                                }
                            } while (c.moveToNext());
                        }
                        if (c != null) {
                            c.close();
                        }

                        int nameCount = (name != null && !name.equals("")) ? 1 : 0;
                        int GroupNumCount = (arrayNumber.size()%2) != 0 ? (arrayNumber.size()/2 + 1) : (arrayNumber.size()/2);
                        int GroupEmailCount = arrayEmail.size();
                        int GroupCount = Math.max(GroupEmailCount, Math.max(nameCount, GroupNumCount));

                        Uri itemUri = null;
                        Log.i(TAG, "GroupCount = " + GroupCount);
                        for(int i = 0 ; i < GroupCount ; i++){
                            Log.d(TAG, "Exported contact freeSimCount = " + freeSimCount);
                            if(freeSimCount > 0) {
                                String num = arrayNumber.size() > 0 ? arrayNumber.remove(0) : null;
                                String anrNum = arrayNumber.size() > 0 ? arrayNumber.remove(0) : null;
                                String email = arrayEmail.size() > 0 ? arrayEmail.remove(0) : null;
                                Log.i(TAG, "name = " + name);
                                Log.i(TAG, "num = " + num);
                                Log.i(TAG, "anrNum = " + anrNum);
                                Log.i(TAG, "email = " + email);
                                itemUri = ContactsUtils.insertToCard(mpeople, name, num, email, anrNum, subscription);
                                Log.i(TAG, "itemUri = " + itemUri);
                                if (itemUri == null) {
                                    // add toast handler when sim card is full
                                    //if (isSimCardFull(mpeople.getContentResolver(),adnCount)) {
                                    //    isSimCardFull = true;
                                    //    mToastHandler.sendEmptyMessage(TOAST_SIM_CARD_FULL);
                                    //    break;
                                    //} else {
                                        mToastHandler.sendEmptyMessage(TOAST_EXPORT_FAILED);
                                        boolean airplane = (System.getInt(mpeople.getContentResolver(),
                                                System.AIRPLANE_MODE_ON, 0) != 0);
                                        if (airplane) break;
                                        else continue;
                                    //}
                                } else {
                                    Log.d(TAG, "Exported contact [" + name + ", " + contactInfo[0] + ", " + contactInfo[1]
                                        + "] to sub " + subscription);
                                    int ret = Integer.parseInt(itemUri.getLastPathSegment()); 
                                    Log.d(TAG, "Exported contact ret = " + ret);
                                    if(ret != 1) {
                                        if(ret == RESULT_EMAIL_FULL_FAILURE){
                                            itemUri = ContactsUtils.insertToCard(mpeople, name, num, "", anrNum, subscription);
                                            Log.d(TAG, "Exported contact showEmailFull = " + showEmailFull);
                                            if(showEmailFull) {
                                                mToastHandler.sendEmptyMessage(TOAST_EMAIL_FULL);
                                            }
                                            showEmailFull = false;
                                            ret = Integer.parseInt(itemUri.getLastPathSegment());
                                            if(ret == 1) {
                                                insertCount++;
                                                freeSimCount--;
                                            }
                                            else {
                                                mToastHandler.sendEmptyMessage(TOAST_EXPORT_FAILED);
                                            }
                                        }
                                        else if(ret == RESULT_ANR_FULL_FAILURE){
                                            itemUri = ContactsUtils.insertToCard(mpeople, name, num, email, "", subscription);
                                            Log.d(TAG, "Exported contact showAnrFull = " + showAnrFull);
                                            if(showAnrFull) {
                                                mToastHandler.sendEmptyMessage(TOAST_ANR_FULL);
                                            }
                                            showAnrFull = false;
                                            ret = Integer.parseInt(itemUri.getLastPathSegment());
                                            if(ret == 1) {
                                                insertCount++;
                                                freeSimCount--;
                                            }
                                            else {
                                                mToastHandler.sendEmptyMessage(TOAST_EXPORT_FAILED);
                                            }
                                        }
                                        else {
                                            //if (isSimCardFull(mpeople.getContentResolver(),adnCount)) {
                                            //    Log.d(TAG, "Exported contact SimCardFull");
                                            //    isSimCardFull = true;
                                            //    mToastHandler.sendEmptyMessage(TOAST_SIM_CARD_FULL);
                                            //    break;
                                            //} else {
                                                boolean airplane = (System.getInt(mpeople.getContentResolver(),
                                                        System.AIRPLANE_MODE_ON, 0) != 0);
                                                if(airplane){
                                                    isSimCardFull = true;
                                                    mToastHandler.sendEmptyMessage(TOAST_SIM_CARD_STOP);
                                                    break;
                                                }
                                                mToastHandler.sendEmptyMessage(TOAST_EXPORT_FAILED);
                                                if (airplane) {
                                                    isSimCardFull = true;
                                                    break;
                                                }    
                                                else continue;
                                            //}
                                        }
                                    }
                                    else {
                                        insertCount++;
                                        freeSimCount--;
                                    }
                                }
                            }
                            else {
                                isSimCardFull = true;
                                mToastHandler.sendEmptyMessage(TOAST_SIM_CARD_FULL);
                                break; 
                            }
                        }
                        if(isSimCardFull) {
                            break;
                        }
                    }
                }
            } else if (type == TYPE_ALL) {
                // Not use now.
                Cursor cursor = mpeople.getContentResolver().query(Phone.CONTENT_URI,
                        PHONES_PROJECTION, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext() && !canceled) {
                        if (canSaveEmail) {
                            emails = getEmails(mpeople,
                                    cursor.getString(PHONE_CONTACT_ID_COLUMN_INDEX));
                        }
                        insert(cursor.getString(PHONE_DISPLAY_NAME_COLUMN_INDEX),
                                cursor.getString(PHONE_NUMBER_COLUMN_INDEX), emails, subscription);
                    }
                    cursor.close();
                }
            }
            if (mExportProgressDlg != null) {
                mExportProgressDlg.dismiss();
                mExportProgressDlg = null;
            }

            // if canceled, show toast indicating export is interrupted.
            if (canceled) {
                mToastHandler.sendMessage(mToastHandler.obtainMessage(TOAST_EXPORT_CANCELED,
                        insertCount, 0));
            } else {
                mToastHandler.sendEmptyMessage(TOAST_EXPORT_FINISHED);
            }
            disablePowerWakeLock();
            isExportingToSIM = false;
            Intent intent = new Intent(PeopleActivity.INTENT_EXPORT_COMPLETE);
            mpeople.sendBroadcast(intent);
        }

        /**
         * refer to the code in the doSaveToSimCard() of ContactSaveService.
         */
        private Uri insert(String name, String number, String emails, int subscription) {
            //add the max count limit of Chinese code or not
            if (!TextUtils.isEmpty(name)) {
                final int maxLen = hasChinese(name) ? CONTACT_NAME_MAX_LENGTH_CHN : CONTACT_NAME_MAX_LENGTH_NOT_CHN;
                if (name.length() > maxLen) {
                    mToastHandler.sendEmptyMessage(TOAST_CONTACT_NAME_TOO_LONG);
                    return null;
                }
            }
            Uri result;
            mValues.clear();
            mValues.put("tag", name);
            mValues.put("number", PhoneNumberUtils.stripSeparators(number));
            if (!TextUtils.isEmpty(emails)) {
                mValues.put("emails", emails);
             }

            result = mSimContactsOperation.insert(mValues,subscription);


            if (result != null){
                // we should import the contact to the sim account at the same time.
                actuallyImportOneSimContact(mValues, mpeople.getContentResolver(),subscription);
                if (mExportProgressDlg != null) {
                    mExportProgressDlg.incrementProgressBy(1);
                }
            } else {
                Log.e(TAG, "export contact: [" + name + ", " + number + ", " + emails + "] to slot "
                    + subscription + " failed");
            }
            return result;
        }

        /**
         * The Function is added for sim card is full or not.
         * @param resolver
         * @return
         */
        private boolean isSimCardFull(ContentResolver resolver, int adnCount) {
            int count = 0;
            Cursor c = null;
            Uri iccUri;
            if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                iccUri = Uri.parse("content://icc/adn");
            } else {
                iccUri = Uri.parse(subscription == 0 ? "content://iccmsim/adn"
                        : "content://iccmsim/adn_sub2");
            }
            try {
                c = resolver.query(iccUri, null, null, null, null);
                if (c != null) {
                    count = c.getCount();
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }

            if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                String CardType = TelephonyManager.getDefault().getCardType();
                if (SimContactsConstants.USIM.equals(CardType)
                        || SimContactsConstants.CSIM.equals(CardType)) {
                    if (adnCount == count)
                        return true;
                } else {
                    if (adnCount == count)
                        return true;
                }
            } else {
                String CardType = MSimTelephonyManager.getDefault().getCardType(
                        subscription);
                if (SimContactsConstants.USIM.equals(CardType)
                        || SimContactsConstants.CSIM.equals(CardType)) {
                    if (adnCount == count)
                        return true;
                } else {
                    if (adnCount == count)
                        return true;
                }
            }
            return false;
        }

        private void setExportProgress(int size){
            mExportProgressDlg = new ProgressDialog(mpeople);
            mExportProgressDlg.setTitle(R.string.export_to_sim);
            mExportProgressDlg.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    Log.d(TAG, "Cancel exporting contacts");
                    canceled = true;
                }
            });
            mExportProgressDlg.setMessage(mpeople.getString(R.string.exporting));
            mExportProgressDlg.setProgressNumberFormat(mpeople.getString(R.string.reading_vcard_files));
            mExportProgressDlg.setMax(size);
            mExportProgressDlg.setProgress(0);

            // set cancel dialog by touching outside disabled.
            mExportProgressDlg.setCanceledOnTouchOutside(false);

            // add a cancel button to let user cancel explicitly.
            mExportProgressDlg.setButton(DialogInterface.BUTTON_NEGATIVE,
                    mpeople.getString(R.string.progressdialog_cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (DEBUG) {
                                Log.d(TAG, "Cancel exporting contacts by click button");
                            }
                            canceled = true;
                        }
                    });

            mExportProgressDlg.show();
        }

        private Handler mToastHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case TOAST_EXPORT_FAILED:
                        Toast.makeText(mpeople, R.string.export_failed, Toast.LENGTH_SHORT).show();
                        break;
                    case TOAST_EXPORT_FINISHED:
                        Toast.makeText(mpeople, R.string.export_finished, Toast.LENGTH_SHORT).show();
                        break;
                    // add toast handler when sim card is full
                    case TOAST_SIM_CARD_FULL:
                        Toast.makeText(mpeople, R.string.sim_card_full, Toast.LENGTH_SHORT).show();
                        break;
                    //add the max count limit of Chinese code or not
                    case TOAST_CONTACT_NAME_TOO_LONG:
                        Toast.makeText(mpeople, R.string.tag_too_long, Toast.LENGTH_SHORT).show();
                        break;

                    case TOAST_EMAIL_FULL:
                        Toast.makeText(mpeople, R.string.email_address_full, Toast.LENGTH_SHORT).show();
                        break;  

                    case TOAST_ANR_FULL:
                        Toast.makeText(mpeople, R.string.number_anr_full, Toast.LENGTH_SHORT).show();
                        break;      

                     // add toast handler when export is canceled
                    case TOAST_EXPORT_CANCELED:
                        int exportCount = msg.arg1;
                        Toast.makeText(mpeople,mpeople.getString(R.string.export_cancelled,
                                       String.valueOf(exportCount)), Toast.LENGTH_SHORT).show();
                        break;
                   case TOAST_SIM_CARD_LOADING:
                        Toast.makeText(mpeople, R.string.sim_loading, Toast.LENGTH_SHORT).show();
                        break;
                   case TOAST_SIM_CARD_STOP:
                        Toast.makeText(mpeople, R.string.sim_stop, Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };

        public void hideExportProgressDialog(){
            if(mExportProgressDlg != null)
                mExportProgressDlg.dismiss();
            mExportProgressDlg = null;
        }
        public void showExportProgressDialog(Activity activity){
            mpeople = activity;
            mExportProgressDlg = new ProgressDialog(mpeople);
            mExportProgressDlg.setTitle(R.string.export_to_sim);
            mExportProgressDlg.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    Log.d(TAG, "Cancel exporting contacts");
                    canceled = true;
                }
            });
            mExportProgressDlg.setMessage(mpeople.getString(R.string.exporting));
            mExportProgressDlg.setProgressNumberFormat(mpeople.getString(R.string.reading_vcard_files));
            mExportProgressDlg.setMax(contactList.size());
            //mExportProgressDlg.setProgress(insertCount);

            // set cancel dialog by touching outside disabled.
            mExportProgressDlg.setCanceledOnTouchOutside(false);

            // add a cancel button to let user cancel explicitly.
            mExportProgressDlg.setButton(DialogInterface.BUTTON_NEGATIVE,
                    mpeople.getString(R.string.progressdialog_cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (DEBUG) {
                                Log.d(TAG, "Cancel exporting contacts by click button");
                            }
                            canceled = true;
                        }
                    });

            mExportProgressDlg.show();
        }

    }

    Uri mSelectedContactUri;
    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            // TODO: need to consider USIM
            if (mSelectedContactUri != null) {
                long id = ContentUris.parseId(mSelectedContactUri);
                int sub = mSimContactsOperation.getSimSubscription(id);
                if (sub != -1) {
                    ContentValues values =
                        mSimContactsOperation.getSimAccountValues(id);
                    int result = mSimContactsOperation.delete(values,sub);
                    if (result == 1)
                    getActivity().getContentResolver().delete(mSelectedContactUri, null, null);
                } else {
                    getActivity().getContentResolver().delete(mSelectedContactUri, null, null);
                }
            }
        }
    }

    private int activeSubCount() {
        int count = 0;
        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            if (TelephonyManager.SIM_STATE_ABSENT != MSimTelephonyManager.getDefault()
                    .getSimState(i))
                count++;
        }
        Log.d(TAG, "active count:" + count);
        return count;
    }

    public ImportFromSimSelectListener listener;
    /**
     * Create a {@link Dialog} that allows the user to pick from a bulk import
     * or bulk export task across all contacts.
     */
    private Dialog displayImportExportDialog(int id, Bundle bundle) {
    Dialog diag;
        switch (id) {
            case R.string.import_from_sim:
            case R.string.import_from_sim_select: {
                if (activeSubCount() == 2) {
                    listener = new ImportFromSimSelectListener();
                    showSimSelectDialog();
                } else if (activeSubCount() == 1) {
                    AccountSelectionUtil.setImportSubscription(MSimTelephonyManager
                            .getDefault().getPreferredVoiceSubscription());
                    handleImportRequest(R.string.import_from_sim);
                }
                break;
            }
            case R.string.import_from_sdcard: {
                return AccountSelectionUtil.getSelectAccountDialog(getActivity(), id);
            }
            case R.string.export_to_sim: {
                String[] items = new String[MSimTelephonyManager.getDefault().getPhoneCount()];
                for (int i = 0; i < items.length; i++) {
                    items[i] = getString(R.string.export_to_sim) + ": " + getMultiSimName(i);
                }
                mExportSub = 0;
                ExportToSimSelectListener listener = new ExportToSimSelectListener();
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.export_to_sim)

                        //change the icon
                        .setIcon(R.drawable.ic_dialog_minfo)
                        .setPositiveButton(android.R.string.ok, listener)
                        .setSingleChoiceItems(items, 0, listener).create();
            }
            case R.id.dialog_sdcard_not_found: {
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.no_sdcard_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.no_sdcard_message)
                        .setPositiveButton(android.R.string.ok, null).create();
            }
            case R.id.dialog_delete_contact_confirmation: {
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_readonly_contact_hide_confirmation: {
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactWarning)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_readonly_contact_delete_confirmation: {
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_multiple_contact_delete_confirmation: {
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.multipleContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
        }
        return null;
    }

    //For generality of the Dialog, move it from PeopleActivity to ImportExportDialogFragment
    public void showSimSelectDialog() {
        AccountSelectionUtil.setImportSubscription(0);
        String[] items = new String[MSimTelephonyManager.getDefault().getPhoneCount()]; // item is for sim account to show
        for (int i = 0; i < items.length; i++) {
            items[i] = getString(R.string.import_from_sim) + ": " + getMultiSimName(i);
        }
        new AlertDialog.Builder(getActivity())

                // change the icon
                .setTitle(R.string.import_from_sim).setIcon(
                        R.drawable.ic_dialog_minfo)
                .setPositiveButton(android.R.string.ok, listener)
                .setSingleChoiceItems(items, 0, listener).create().show();
    }

    private void handleImportFromSimRequest(int Id) {
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            if (hasMultiEnabledIccCard()) {
                displayImportExportDialog(R.string.import_from_sim_select
                ,null);
            } else {
                AccountSelectionUtil.setImportSubscription(getEnabledIccCard());
                handleImportRequest(Id);
            }
        } else {
            handleImportRequest(Id);
        }
    }

    private void handleExportToSimRequest(int Id) {
        if (hasMultiEnabledIccCard()) {
            //has two enalbed sim cards, prompt dialog to select one
            displayImportExportDialog(Id, null).show();
        } else {
            mExportSub = getEnabledIccCard();
            Intent pickPhoneIntent = new Intent(ACTION_MULTI_PICK, Contacts.CONTENT_URI);
            // do not show the contacts in SIM card
            pickPhoneIntent.putExtra(MultiPickContactActivity.EXT_NOT_SHOW_SIM_FLAG, true);
            mactiv.startActivityForResult(pickPhoneIntent, SUBACTIVITY_MULTI_PICK_CONTACT);
        }
    }

    private boolean hasEnabledIccCard(int subscription) {
        return MSimTelephonyManager.getDefault().hasIccCard(subscription) &&
                   MSimTelephonyManager.getDefault().getSimState(subscription) == TelephonyManager.SIM_STATE_READY;
    }

    private boolean hasMultiEnabledIccCard() {
        int count = 0;
        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            boolean hasEnabledIccCard = hasEnabledIccCard(i);
            if (hasEnabledIccCard) {
                count++;
            }
        }
        return count > 1;
    }

    private boolean hasEnabledIccCard() {
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            boolean hasEnabledIccCard = false;
            for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                hasEnabledIccCard = hasEnabledIccCard(i);
                if (hasEnabledIccCard) {
                    break;
                }
            }
            if (hasEnabledIccCard) {
                return true;
            } else {
                return false;
            }
        } else {
            return TelephonyManager.getDefault().hasIccCard();
        }
    }

    private int getEnabledIccCard() {
        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            boolean hasEnabledIccCard = hasEnabledIccCard(i);
            if (hasEnabledIccCard) {
                return i;
            }
        }
        return 0;
    }

    /**
     * refer to the code of hasChinese() in ContactSaveService.
     */
    private boolean hasChinese(String name) {
        return name != null && name.getBytes().length > name.length();
    }

    /**
     * Get all email for a contact from the DATA database using the contact id
     * @param activity Provide a context to get ContentResolver
     * @param contactId Provide the contact id to search email for on contact
     * @return A string contains all email for a contact. The email is separated with ","
     */
    private String getEmails(Activity activity, String contactId) {
        if (contactId == null) {
            return null;
        }
        ContentResolver cr = activity.getContentResolver();
        Cursor emailCur = cr.query(Email.CONTENT_URI, new String [] {Email.ADDRESS},
                Email.CONTACT_ID + " = " + contactId , null, null);

        if (emailCur == null) {
            return null;
        }

        StringBuffer emails = new StringBuffer();
        while (emailCur.moveToNext()) {
            emails.append(emailCur.getString(0));
            emails.append(",");
        }
        emailCur.close();

        return emails.toString();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (null != mactiv && null != mSimStateReceiver) {
            mactiv.unregisterReceiver(mSimStateReceiver);
        }
    }

    private PowerManager.WakeLock mWakeLock = null;
    private final Object mWakeLockSync = new Object();

    /**
     * Start the service to process the current event notifications, acquiring
     * the wake lock before returning to ensure that the service will run.
     */
    public void enablePowerWakeLock(Context context, String tag) {
        synchronized (mWakeLockSync) {
            if (mWakeLock == null) {
                PowerManager pm =
                    (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        tag);
                mWakeLock.setReferenceCounted(false);
            }
            Log.i(TAG, "enablePowerWakeLock acquire");
            mWakeLock.acquire();
        }
    }

    /**
     * Called back by the service when it has finished processing notifications,
     * releasing the wake lock if the service is now stopping.
     */
    public void disablePowerWakeLock() {
        synchronized (mWakeLockSync) {
            if (mWakeLock != null) {
                mWakeLock.release();
                Log.i(TAG, "enablePowerWakeLock release");
            }
        }
    }
}
