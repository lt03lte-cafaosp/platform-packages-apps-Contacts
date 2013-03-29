/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;

import android.accounts.Account;
import android.content.Context;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Rect;
import android.location.Country;
import android.location.CountryDetector;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StatFs;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayPhoto;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import com.android.contacts.activities.DialtactsActivity;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.test.NeededForTesting;
import com.android.contacts.util.Constants;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.msim.IIccPhoneBookMSim;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ContactsUtils {
    private static final String TAG = "ContactsUtils";
    private static final String WAIT_SYMBOL_AS_STRING = String.valueOf(PhoneNumberUtils.WAIT);

    private static int sThumbnailSize = -1;

    // TODO find a proper place for the canonical version of these
    public interface ProviderNames {
        String YAHOO = "Yahoo";
        String GTALK = "GTalk";
        String MSN = "MSN";
        String ICQ = "ICQ";
        String AIM = "AIM";
        String XMPP = "XMPP";
        String JABBER = "JABBER";
        String SKYPE = "SKYPE";
        String QQ = "QQ";
    }

    /**
     * This looks up the provider name defined in
     * ProviderNames from the predefined IM protocol id.
     * This is used for interacting with the IM application.
     *
     * @param protocol the protocol ID
     * @return the provider name the IM app uses for the given protocol, or null if no
     * provider is defined for the given protocol
     * @hide
     */
    public static String lookupProviderNameFromId(int protocol) {
        switch (protocol) {
            case Im.PROTOCOL_GOOGLE_TALK:
                return ProviderNames.GTALK;
            case Im.PROTOCOL_AIM:
                return ProviderNames.AIM;
            case Im.PROTOCOL_MSN:
                return ProviderNames.MSN;
            case Im.PROTOCOL_YAHOO:
                return ProviderNames.YAHOO;
            case Im.PROTOCOL_ICQ:
                return ProviderNames.ICQ;
            case Im.PROTOCOL_JABBER:
                return ProviderNames.JABBER;
            case Im.PROTOCOL_SKYPE:
                return ProviderNames.SKYPE;
            case Im.PROTOCOL_QQ:
                return ProviderNames.QQ;
        }
        return null;
    }

    /**
     * Test if the given {@link CharSequence} contains any graphic characters,
     * first checking {@link TextUtils#isEmpty(CharSequence)} to handle null.
     */
    public static boolean isGraphic(CharSequence str) {
        return !TextUtils.isEmpty(str) && TextUtils.isGraphic(str);
    }

    /**
     * Returns true if two objects are considered equal.  Two null references are equal here.
     */
    @NeededForTesting
    public static boolean areObjectsEqual(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    /**
     * Returns true if two data with mimetypes which represent values in contact entries are
     * considered equal for collapsing in the GUI. For caller-id, use
     * {@link PhoneNumberUtils#compare(Context, String, String)} instead
     */
    public static final boolean shouldCollapse(CharSequence mimetype1, CharSequence data1,
            CharSequence mimetype2, CharSequence data2) {
        // different mimetypes? don't collapse
        if (!TextUtils.equals(mimetype1, mimetype2)) return false;

        // exact same string? good, bail out early
        if (TextUtils.equals(data1, data2)) return true;

        // so if either is null, these two must be different
        if (data1 == null || data2 == null) return false;

        // if this is not about phone numbers, we know this is not a match (of course, some
        // mimetypes could have more sophisticated matching is the future, e.g. addresses)
        if (!TextUtils.equals(Phone.CONTENT_ITEM_TYPE, mimetype1)) return false;

        return shouldCollapsePhoneNumbers(data1.toString(), data2.toString());
    }

    private static final boolean shouldCollapsePhoneNumbers(
            String number1WithLetters, String number2WithLetters) {
        final String number1 = PhoneNumberUtils.convertKeypadLettersToDigits(number1WithLetters);
        final String number2 = PhoneNumberUtils.convertKeypadLettersToDigits(number2WithLetters);

        int index1 = 0;
        int index2 = 0;
        for (;;) {
            // Skip formatting characters.
            while (index1 < number1.length() &&
                    !PhoneNumberUtils.isNonSeparator(number1.charAt(index1))) {
                index1++;
            }
            while (index2 < number2.length() &&
                    !PhoneNumberUtils.isNonSeparator(number2.charAt(index2))) {
                index2++;
            }
            // If both have finished, match.  If only one has finished, not match.
            final boolean number1End = (index1 == number1.length());
            final boolean number2End = (index2 == number2.length());
            if (number1End) {
                return number2End;
            }
            if (number2End) return false;

            // If the non-formatting characters are different, not match.
            if (number1.charAt(index1) != number2.charAt(index2)) return false;

            // Go to the next characters.
            index1++;
            index2++;
        }
    }

    /**
     * Returns true if two {@link Intent}s are both null, or have the same action.
     */
    public static final boolean areIntentActionEqual(Intent a, Intent b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return TextUtils.equals(a.getAction(), b.getAction());
    }

    /**
     * @return The ISO 3166-1 two letters country code of the country the user
     *         is in.
     */
    public static final String getCurrentCountryIso(Context context) {
        // add Non-null protection of detector for monkey test
        String countryIso;
        CountryDetector detector = (CountryDetector) context
                .getSystemService(Context.COUNTRY_DETECTOR);
        Country c = null;
        if (detector != null && (c = detector.detectCountry()) != null) {
            countryIso = c.getCountryIso();
        } else {
            Locale locale = context.getResources().getConfiguration().locale;
            countryIso = locale.getCountry();
        }
        return countryIso;
    }

    public static boolean areContactWritableAccountsAvailable(Context context) {
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(context).getAccounts(true /* writeable */);
        return !accounts.isEmpty();
    }

    public static boolean areGroupWritableAccountsAvailable(Context context) {
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(context).getGroupWritableAccounts();
        return !accounts.isEmpty();
    }

    /**
     * Returns the intent to launch for the given invitable account type and contact lookup URI.
     * This will return null if the account type is not invitable (i.e. there is no
     * {@link AccountType#getInviteContactActivityClassName()} or
     * {@link AccountType#syncAdapterPackageName}).
     */
    public static Intent getInvitableIntent(AccountType accountType, Uri lookupUri) {
        String syncAdapterPackageName = accountType.syncAdapterPackageName;
        String className = accountType.getInviteContactActivityClassName();
        if (TextUtils.isEmpty(syncAdapterPackageName) || TextUtils.isEmpty(className)) {
            return null;
        }
        Intent intent = new Intent();
        intent.setClassName(syncAdapterPackageName, className);

        intent.setAction(ContactsContract.Intents.INVITE_CONTACT);

        // Data is the lookup URI.
        intent.setData(lookupUri);
        return intent;
    }

    /**
     * Return Uri with an appropriate scheme, accepting Voicemail, SIP, and usual phone call
     * numbers.
     */
    public static Uri getCallUri(String number) {
        if (PhoneNumberUtils.isUriNumber(number)) {
             return Uri.fromParts(Constants.SCHEME_SIP, number, null);
        }
        return Uri.fromParts(Constants.SCHEME_TEL, number, null);
     }

    /**
     * Return an Intent for making a phone call. Scheme (e.g. tel, sip) will be determined
     * automatically.
     */
    public static Intent getCallIntent(String number) {
        return getCallIntent(number, null);
    }

    /**
     * Return an Intent for making a phone call. A given Uri will be used as is (without any
     * sanity check).
     */
    public static Intent getCallIntent(Uri uri) {
        return getCallIntent(uri, null);
    }

    /**
     * A variant of {@link #getCallIntent(String)} but also accept a call origin. For more
     * information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(String number, String callOrigin) {
        return getCallIntent(getCallUri(number), callOrigin);
    }

    /**
     * A variant of {@link #getCallIntent(Uri)} but also accept a call origin. For more
     * information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(Uri uri, String callOrigin) {
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (callOrigin != null) {
            intent.putExtra(DialtactsActivity.EXTRA_CALL_ORIGIN, callOrigin);
        }
        return intent;
    }

    /**
     * Return an Intent for launching voicemail screen.
     */
    public static Intent getVoicemailIntent() {
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                Uri.fromParts("voicemail", "", null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * Returns a header view based on the R.layout.list_separator, where the
     * containing {@link TextView} is set using the given textResourceId.
     */
    public static View createHeaderView(Context context, int textResourceId) {
        View view = View.inflate(context, R.layout.list_separator, null);
        TextView textView = (TextView) view.findViewById(R.id.title);
        textView.setText(context.getString(textResourceId));
        return view;
    }

    /**
     * Returns the {@link Rect} with left, top, right, and bottom coordinates
     * that are equivalent to the given {@link View}'s bounds. This is equivalent to how the
     * target {@link Rect} is calculated in {@link QuickContact#showQuickContact}.
     */
    public static Rect getTargetRectFromView(Context context, View view) {
        final float appScale = context.getResources().getCompatibilityInfo().applicationScale;
        final int[] pos = new int[2];
        view.getLocationOnScreen(pos);

        final Rect rect = new Rect();
        rect.left = (int) (pos[0] * appScale + 0.5f);
        rect.top = (int) (pos[1] * appScale + 0.5f);
        rect.right = (int) ((pos[0] + view.getWidth()) * appScale + 0.5f);
        rect.bottom = (int) ((pos[1] + view.getHeight()) * appScale + 0.5f);
        return rect;
    }

    /**
     * Returns the size (width and height) of thumbnail pictures as configured in the provider. This
     * can safely be called from the UI thread, as the provider can serve this without performing
     * a database access
     */
    public static int getThumbnailSize(Context context) {
        if (sThumbnailSize == -1) {
            final Cursor c = context.getContentResolver().query(
                    DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
                    new String[] { DisplayPhoto.THUMBNAIL_MAX_DIM }, null, null, null);
            try {
                c.moveToFirst();
                sThumbnailSize = c.getInt(0);
            } finally {
                c.close();
            }
        }
        return sThumbnailSize;
    }

    /**
     * @return if the context is in landscape orientation.
     */
    public static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Returns the display name of the contact, using the current display order setting.
     * Returns res/string/missing_name if there is no display name.
     */
    public static String getDisplayName(Context context, String displayName, String altDisplayName) {
        ContactsPreferences prefs = new ContactsPreferences(context);
        String styledName = "";
        if (!TextUtils.isEmpty(displayName) && !TextUtils.isEmpty(altDisplayName)) {
            if (prefs.getDisplayOrder() == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
                styledName = displayName;
            } else {
                styledName = altDisplayName;
            }
        } else {
            styledName = context.getResources().getString(R.string.missing_name);
        }
        return styledName;
    }

    public static Account getAcount(int sub){
        Account account = null;
        if(sub == -1) {
            account = new Account(SimContactsConstants.PHONE_NAME, SimContactsConstants.ACCOUNT_TYPE_PHONE);
        }
        else {
            if(MSimTelephonyManager.getDefault().isMultiSimEnabled()){
                if(sub == 0) {
                    account = new Account(SimContactsConstants.SIM_NAME_1, SimContactsConstants.ACCOUNT_TYPE_SIM);
                }
                else if(sub == 1) {
                    account = new Account(SimContactsConstants.SIM_NAME_2, SimContactsConstants.ACCOUNT_TYPE_SIM);
                }
            }
            else {
                account = new Account(SimContactsConstants.SIM_NAME, SimContactsConstants.ACCOUNT_TYPE_SIM);
            }
        }

        return account;
    }

    public static int getSub(String name, String type){
        int sub = -1;
        if(name != null && type != null) {
            android.util.Log.i("ContactsUtils", "getSub name = " + name + " type = " + type);
            if(MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                if(SimContactsConstants.PHONE_NAME.equals(name) && SimContactsConstants.ACCOUNT_TYPE_PHONE.equals(type)) {
                    sub = -1;
                }
                else if(SimContactsConstants.SIM_NAME_1.equals(name) && SimContactsConstants.ACCOUNT_TYPE_SIM.equals(type)) {
                    sub  = 0;
                }
                else if(SimContactsConstants.SIM_NAME_2.equals(name) && SimContactsConstants.ACCOUNT_TYPE_SIM.equals(type)) {
                    sub = 1;
                }
            }
            else {
                if(SimContactsConstants.SIM_NAME.equals(name) && SimContactsConstants.ACCOUNT_TYPE_SIM.equals(type)) {
                    sub = 0;
                }
            }
        }

        android.util.Log.i("ContactsUtils", "getSub return sub = " + sub);
        return sub;
    }

    public static void insertToPhone(
            String[] values, final ContentResolver resolver, int sub) {
        Account account = getAcount(sub);    

        final String name = values[0];
        final String phoneNumber = values[1];
        final String emailAddresses = values[2];
        final String anrs = values[3];
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
        Log.d(TAG, "insertToPhone: name= " + name +
            ", phoneNumber= " + phoneNumber +", emails= "+ emailAddresses
            +", anrs= "+ anrs + ", account is " + account);

        final ArrayList<ContentProviderOperation> operationList =
            new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
        builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        if (account != null) {
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
        }
        operationList.add(builder.build());

        // do not allow empty value insert into database, because QuickContact may be appear exception in UI
        if(!TextUtils.isEmpty(name)) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
            builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
            builder.withValue(StructuredName.DISPLAY_NAME, name);
            operationList.add(builder.build());
        }

        if(!TextUtils.isEmpty(phoneNumber)) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
            builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
            builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
            builder.withValue(Phone.NUMBER, phoneNumber);
            builder.withValue(Data.IS_PRIMARY, 1);
            operationList.add(builder.build());
        }

        if (anrArray != null) {
            for (String anr :anrArray) {
                if(!TextUtils.isEmpty(anr)) {
                    builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                    builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
                    builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                    builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
                    builder.withValue(Phone.NUMBER, anr);
                    //builder.withValue(Data.IS_PRIMARY, 1);
                    operationList.add(builder.build());
                }
            }
        }

        if (emailAddressArray != null) {
            for (String emailAddress : emailAddressArray) {
                if(!TextUtils.isEmpty(emailAddress)) {
                    builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                    builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
                    builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                    builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
                    builder.withValue(Email.ADDRESS, emailAddress);
                    operationList.add(builder.build());
                }
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

    public static Uri insertToCard(Context context, String name, String number, String emails, String anrNumber, int subscription) {
        //add the max count limit of Chinese code or not
        if (!TextUtils.isEmpty(name)) {
            final int maxLen = hasChinese(name) ? 6 : 14;
            if (name.length() > maxLen) {
                Toast.makeText(context, R.string.tag_too_long, Toast.LENGTH_SHORT).show();
                return null;
            }
        }
        Uri result;
        ContentValues mValues = new ContentValues();
        mValues.clear();
        mValues.put("tag", name);
        mValues.put("number", PhoneNumberUtils.stripSeparators(number));
        if (!TextUtils.isEmpty(emails)) {
            mValues.put("emails", emails);
        }
        if (!TextUtils.isEmpty(anrNumber)) {
            mValues.put("anrs", anrNumber);
        }

        SimContactsOperation mSimContactsOperation = new SimContactsOperation(context);
        result = mSimContactsOperation.insert(mValues,subscription);


        if (result != null){
            // we should import the contact to the sim account at the same time.
            String[] value = new String[]{name, number, emails, anrNumber};
            insertToPhone(value, context.getContentResolver(),subscription);
        } else {
            Log.e(TAG, "export contact: [" + name + ", " + number + ", " + emails + "] to slot "
                + subscription + " failed");
        }
        return result;
    }

    private static boolean hasChinese(String name) {
        return name != null && name.getBytes().length > name.length();
    }

    public static int getAdnCount(int sub) 
    {
        int adnCount = 0;
        if(MSimTelephonyManager.getDefault().isMultiSimEnabled())
        {
            try {
                IIccPhoneBookMSim iccIpb = IIccPhoneBookMSim.Stub.asInterface(
                            ServiceManager.getService("simphonebook_msim"));
                if (iccIpb != null) {
                    adnCount = iccIpb.getAdnCount(sub);
                }
            } catch (RemoteException ex) {
            // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString(), (new Exception()));
            }
            catch (Exception ex){
            }
            android.util.Log.i("ContactsUtils", "isMultiSimEnabled adnCount = " + adnCount + " sub = " + sub);
        }
        else
        {
            try {
                IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                            ServiceManager.getService("simphonebook"));
                if (iccIpb != null) {
                    adnCount = iccIpb.getAdnCount();
                }
            } catch (RemoteException ex) {
            // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString(), (new Exception()));
            }
            catch (Exception ex){
            }
            android.util.Log.i("ContactsUtils", "adnCount = " + adnCount + " sub = " + sub);
        }
        return adnCount;
    }

    public static int getSpareAnrCount(int sub) {
        int anrCount = 0;
        if(MSimTelephonyManager.getDefault().isMultiSimEnabled())
        {
            try {
                IIccPhoneBookMSim iccIpb = IIccPhoneBookMSim.Stub.asInterface(
                            ServiceManager.getService("simphonebook_msim"));
                if (iccIpb != null) {
                    anrCount = iccIpb.getSpareAnrCount(sub);
                }
            } catch (RemoteException ex) {
            // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString(), (new Exception()));
            }
            catch (Exception ex){
            }
        }
        else
        {
            try {
                IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                            ServiceManager.getService("simphonebook"));
                if (iccIpb != null) {
                    anrCount = iccIpb.getSpareAnrCount();
                }
            } catch (RemoteException ex) {
            // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString(), (new Exception()));
            }
            catch (Exception ex){
            }
        }
        return anrCount;
    }

    public static int getSpareEmailCount(int sub) {
        int emailCount = 0;
        if(MSimTelephonyManager.getDefault().isMultiSimEnabled())
        {
            try {
                IIccPhoneBookMSim iccIpb = IIccPhoneBookMSim.Stub.asInterface(
                            ServiceManager.getService("simphonebook_msim"));
                if (iccIpb != null) {
                    emailCount = iccIpb.getSpareEmailCount(sub);
                }
            } catch (RemoteException ex) {
            // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString(), (new Exception()));
            }
            catch (Exception ex){
            }
        }
        else
        {
            try {
                IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                            ServiceManager.getService("simphonebook"));
                if (iccIpb != null) {
                    emailCount = iccIpb.getSpareEmailCount();
                }
            } catch (RemoteException ex) {
            // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString(), (new Exception()));
            }
            catch (Exception ex){
            }
        }
        return emailCount;
    }

    public static int getSimFreeCount(Context context, int sub) {
        String accountName = getAcount(sub).name;
        int count = 0;

        if(context == null) {
            return 0;
        }
        
        Cursor queryCursor = context.getContentResolver().query(RawContacts.CONTENT_URI, new String[] { RawContacts._ID },
                RawContacts.ACCOUNT_NAME + " = '" + accountName + "' AND " + RawContacts.DELETED + " = 0", null, null);
        if (queryCursor != null) {
            try {
                count = queryCursor.getCount();
            } finally {
                queryCursor.close();
            }
        }

        return getAdnCount(sub) - count;
    }
    public static boolean checkContactsFull(){
     
        File path = new File("/data/");
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        
        long available = availableBlocks*blockSize;
        if (available < 10*1024*1024)
        {
            return true;
        }
    	  return false;
    }
}
