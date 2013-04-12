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
package com.android.contacts.vcard;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;
import com.android.contacts.R;
import java.util.ArrayList;
import com.android.contacts.ContactsLib;
import com.android.contacts.ContactsLib.RecordsSync;
import com.android.vcard.VCardEntryHandler;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardConstants;
import android.text.TextUtils;
import com.android.contacts.ContactsUtils;
import android.accounts.Account;
import android.widget.Toast;
import android.content.Context;
import android.os.Looper;
import com.android.contacts.vcard.VCardService;
import com.android.contacts.SimContactsConstants;
/**
 * <P>
 * {@link VCardEntryHandler} implementation which commits the entry to ContentResolver.
 * </P>
 * <P>
 * Note:<BR />
 * Each vCard may contain big photo images encoded by BASE64,
 * If we store all vCard entries in memory, OutOfMemoryError may be thrown.
 * Thus, this class push each VCard entry into ContentResolver immediately.
 * </P>
 */
public class VCardEntryCommitterEx implements VCardEntryHandler {
    public static String LOG_TAG = "VCardEntryCommitterEx";

    private final ContentResolver mContentResolver;
    private long mTimeToCommit;
    private int mCounter;
    private ArrayList<ContentProviderOperation> mOperationList;
    private final ArrayList<Uri> mCreatedUris = new ArrayList<Uri>();
    private ArrayList<Integer> mFreeID = null;
    private int mFreeIDIndex = 0;
    private final Account mAccount;
    private Boolean mIsDeafaultAccount = false;
	  private Context mContext;
    private Listener mListener;
    private int phone_avail = 2000;

    public interface Listener{
		public void onImportReachMax();
    }

    public VCardEntryCommitterEx(Context context , ContentResolver resolver, Account account) {
        mContentResolver = resolver;
        mAccount = account;
		    mContext = context;
        mListener = (VCardService)context;
        mIsDeafaultAccount = account.type.equals(SimContactsConstants.ACCOUNT_TYPE_PHONE);
        if(mIsDeafaultAccount) {
            phone_avail = ContactsUtils.getSimFreeCount(context,-1);
        }
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onEnd() {
        if (mOperationList != null) {
            mCreatedUris.add(pushIntoContentResolver(mOperationList));
        }

        //if (VCardConfig.showPerformanceLog()) {
            Log.d(LOG_TAG, String.format("time to commit entries: %d ms", mTimeToCommit));
        //}
    }

    @Override
    public void onEntryCreated(final VCardEntry vcardEntry) {
        final long start = System.currentTimeMillis();
        if (mIsDeafaultAccount && ContactsUtils.isCmccTest())
        {
            
            if (phone_avail <= 0){
                if(mListener != null){
                    mListener.onImportReachMax();
                }
				    return;
            }
            phone_avail--;
            mOperationList = vcardEntry.constructInsertOperations(mContentResolver, mOperationList);
        }
        else
        {
            mOperationList = vcardEntry.constructInsertOperations(mContentResolver, mOperationList);
        }
        mCounter++;
        if (mCounter >= 10 || mOperationList.size() >= 40) {
            ContactsLib.enableTransactionLock(mContentResolver);
            mCreatedUris.add(pushIntoContentResolver(mOperationList));
            ContactsLib.disableTransactionLock(mContentResolver);
            //mCreatedUris.add(pushIntoContentResolver(mOperationList));
            mCounter = 0;
            mOperationList = null;
        }
        mTimeToCommit += System.currentTimeMillis() - start;
        //Log.c(LOG_TAG, "***********&&&&&&&&&&end: "+System.currentTimeMillis(), new Exception());
        //Log.c(LOG_TAG, "***********&&&&&&&&&&commit time: "+(System.currentTimeMillis() - start), new Exception());
    }

    private Uri pushIntoContentResolver(ArrayList<ContentProviderOperation> operationList) {
        try {
            final ContentProviderResult[] results = mContentResolver.applyBatch(
                    ContactsContract.AUTHORITY, operationList);

            // the first result is always the raw_contact. return it's uri so
            // that it can be found later. do null checking for badly behaving
            // ContentResolvers
            return ((results == null || results.length == 0 || results[0] == null)
                            ? null : results[0].uri);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            return null;
        } catch (OperationApplicationException e) {
            Log.e(LOG_TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            return null;
        }
    }

    /**
     * Returns the list of created Uris. This list should not be modified by the caller as it is
     * not a clone.
     */
   public ArrayList<Uri> getCreatedUris() {
        return mCreatedUris;
    }
}
