/*
Copyright (c) 2016, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;
import android.widget.Toast;
import com.android.contacts.R;
import com.android.contacts.util.Constants;
import com.android.contacts.common.util.ContactLoaderUtils;

public class GroupUtil {

    /**
     * Share the group.
     *
     * @param id
     *            the group-id
     * @param baseContext
     *            the base context
     */
    public static StringBuilder getShareGroupContacts(final long groupId,
            final Context baseContext) {

        final Cursor cursor = baseContext.getContentResolver().query(
                RawContactsEntity.CONTENT_URI,
                new String[] { RawContactsEntity.CONTACT_ID },
                RawContactsEntity.MIMETYPE + " = \""
                        + CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
                        + "\" AND " + RawContactsEntity.DATA1 + " = " + groupId
                        + " AND " + RawContactsEntity.DELETED + " = 0 ", null,
                null);

        try {
            if (!cursor.moveToFirst()) {
                if (cursor != null)
                    cursor.close();
                return null;
            }

            StringBuilder uriListBuilder = new StringBuilder();
            int index = 0;
            for (; !cursor.isAfterLast(); cursor.moveToNext()) {
                final Cursor cur = baseContext
                        .getContentResolver()
                        .query(Contacts.CONTENT_URI,
                                new String[] { Contacts.LOOKUP_KEY },
                                BaseColumns._ID
                                        + " = "
                                        + cursor.getLong(cursor
                                                .getColumnIndex(RawContactsEntity.CONTACT_ID)),
                                null, null);
                if (cur != null && cur.getCount() > 0) {
                    cur.moveToFirst();
                    if (index != 0) {
                        uriListBuilder.append(':');
                    }
                    uriListBuilder.append(cur.getString(0));
                    index++;
                }
                if (cur != null) {
                    cur.close();
                }
            }
            return uriListBuilder;
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    public static boolean shareContacts(StringBuilder uriList, Context context) {

        if (uriList != null) {
            Uri uri = Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI,
                    Uri.encode(uriList.toString()));
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(Contacts.CONTENT_VCARD_TYPE);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            final CharSequence chooseTitle = context.getText(R.string.share_contacts_via);
            final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);
            context.startActivity(chooseIntent);
            return true;
        }
        return false;
    }

    /**
     * Sends a message to a group.
     *
     * @param id
     *            the group id
     * @param context
     *            the context
     * @return true, if successful
     */
    public static String getGroupMessageRecipients(final long id,
            final Context context) {
        StringBuffer mPhones = new StringBuffer();

        // return cursor : list of contact ids for this group
        final Cursor cursor = context.getContentResolver().query(
                RawContactsEntity.CONTENT_URI,
                new String[] { RawContactsEntity.CONTACT_ID },
                RawContactsEntity.MIMETYPE + " = \""
                        + CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
                        + "\" AND " + RawContactsEntity.DATA1 + " = " + id
                        + " AND " + RawContactsEntity.DELETED + " = 0", null,
                null);

        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null)
                cursor.close();
            return null;
        }

        int i = 0;
        String num = null;
        boolean IsFirstRecipient = true;
        for (i = 0; cursor.moveToNext(); i++) {

            long con_id = cursor.getLong(cursor
                    .getColumnIndex(RawContactsEntity.CONTACT_ID));
            // return cur : list of raw_contacts id (with either phone or email)
            // for each contact_id
            final Cursor cur = context.getContentResolver().query(
                    RawContactsEntity.CONTENT_URI,
                    new String[] { RawContactsEntity._ID },
                    "( " + RawContactsEntity.MIMETYPE + " = \""
                            + Phone.CONTENT_ITEM_TYPE + "\" OR "
                            + RawContactsEntity.MIMETYPE + " = \""
                            + Email.CONTENT_ITEM_TYPE + "\" ) AND ( "
                            + RawContactsEntity.CONTACT_ID + " = " + con_id
                            + " )", null, null);

            if (cur != null && (cur.getCount() > 0)) {
                long[] raw_ids = new long[cur.getCount()];
                while (cur.moveToNext()) {
                    raw_ids[cur.getPosition()] = cur
                            .getLong(cur
                                    .getColumnIndex(ContactsContract.RawContactsEntity._ID));
                    Log.i("GroupUtil",
                            "raw_id["
                                    + cur.getPosition()
                                    + "]  === "
                                    + cur.getLong(cur
                                            .getColumnIndex(ContactsContract.RawContactsEntity._ID)));
                }
                num = getRecipientNumOrEmail(raw_ids, context);

                if (num != null && num.length() > 0) {
                    if (IsFirstRecipient == true) {
                        mPhones.append(num);
                        IsFirstRecipient = false;
                    } else
                        mPhones.append("," + num);
                }
                cur.close();
            }
        }

        Log.d("GroupBrowseListAdapter", " Recipients " + mPhones.toString());
        cursor.close();
        if (mPhones.length() == 0) {
            return null;
        }
        return mPhones.toString();

    }

    /**
     * Get the count of people in the group with valid filter
     *
     * @param id
     *            the group id
     * @param context
     *            the context
     * @return true, if successful
     */
    public static int getGroupCountForEmailOrPhone(final long id,
            final Context context, int filter) {
        StringBuffer mPhones = new StringBuffer();

        // return cursor : list of contact ids for this group
        final Cursor cursor = context.getContentResolver().query(
                RawContactsEntity.CONTENT_URI,
                new String[] { RawContactsEntity.CONTACT_ID },
                RawContactsEntity.MIMETYPE + " = \""
                        + CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
                        + "\" AND " + RawContactsEntity.DATA1 + " = " + id
                        + " AND " + RawContactsEntity.DELETED + " = 0", null,
                null);

        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null)
                cursor.close();
            return 0;
        }

        int i = 0;
        String num = null;
        boolean IsFirstRecipient = true;
        Set conSet = new HashSet<Long>();
        for (i = 0; cursor.moveToNext(); i++) {

            long con_id = cursor.getLong(cursor
                    .getColumnIndex(RawContactsEntity.CONTACT_ID));
            conSet.add(con_id);
        }
        cursor.close();
        Iterator setIterator = conSet.iterator();
        int count = 0;
        while (setIterator.hasNext()) {
            // return cur : list of raw_contacts id (with either phone or email)
            // for each contact_id
            String where = ContactsContract.Contacts._ID + " ="
                    + setIterator.next();
            final Cursor cur = context.getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI,
                    new String[] { ContactsContract.Contacts.HAS_PHONE_NUMBER,
                            "has_email_id" }, where, null, null);
            if (cur != null && cur.getCount() > 0) {
                cur.moveToFirst();
                if (filter == Constants.EMAIL_AND_PHONE_FILTER) {
                    if ((cur.getInt(0) == 1) || (cur.getInt(1) == 1)) {
                        count++;
                    }
                } else if (filter == Constants.EMAIL_ONLY_FILTER) {
                    if (cur.getInt(1) == 1) {
                        count++;
                    }
                }
                cur.close();
            }
        }
        return count;

    }

    public static boolean sendMessage(String recipients, Context context) {
        if (recipients != null) {
            Log.i("GroupUtil", "Sending Message to : " + recipients);
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                    "smsto", recipients, null));
            intent.putExtra("address", recipients);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        }
        return false;
    }

    /**
     * Sends a message to a group.
     *
     * @param id
     *            the group id
     * @param context
     *            the context
     * @return true, if successful
     */
    public static String getGroupEmailRecipients(final long id,
            final Context context) {
        StringBuffer mEmails = new StringBuffer();

        // return cursor : list of contact ids for this group
        final Cursor cursor = context.getContentResolver().query(
                RawContactsEntity.CONTENT_URI,
                new String[] { RawContactsEntity.CONTACT_ID },
                RawContactsEntity.MIMETYPE + " = \""
                        + CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
                        + "\" AND " + RawContactsEntity.DATA1 + " = " + id
                        + " AND " + RawContactsEntity.DELETED + " = 0", null,
                null);

        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null)
                cursor.close();
            return null;
        }

        int i = 0;
        String emailId = null;
        boolean IsFirstRecipient = true;
        ArrayList<String> recipients = new ArrayList<String>();
        for (i = 0; cursor.moveToNext(); i++) {
            long con_id = cursor.getLong(cursor
                    .getColumnIndex(RawContactsEntity.CONTACT_ID));
            // return cur : list of raw_contacts id (with either phone or email)
            // for each contact_id
            final Cursor cur = context.getContentResolver().query(
                    RawContactsEntity.CONTENT_URI,
                    new String[] { RawContactsEntity._ID },
                    "( " + RawContactsEntity.MIMETYPE + " = \""
                            + Email.CONTENT_ITEM_TYPE + "\" ) AND ( "
                            + RawContactsEntity.CONTACT_ID + " = " + con_id
                            + " )", null, null);

            if (cur != null) {
                long[] raw_ids = new long[cur.getCount()];
                while (cur.moveToNext()) {
                    raw_ids[cur.getPosition()] = cur
                            .getLong(cur
                                    .getColumnIndex(ContactsContract.RawContactsEntity._ID));
                    Log.i("GroupUtil",
                            "raw_id["
                                    + cur.getPosition()
                                    + "]  === "
                                    + cur.getLong(cur
                                            .getColumnIndex(ContactsContract.RawContactsEntity._ID)));
                }
                emailId = getRecipientEmailId(raw_ids, context);
                Log.d("GroupUtil", " getRecipientEmailId " + emailId);

                if (emailId != null && emailId.length() > 0) {
                    recipients.add(emailId);
                }

                if (emailId != null && emailId.length() > 0) {
                    if (IsFirstRecipient == true) {
                        mEmails.append(emailId);
                        IsFirstRecipient = false;
                    } else
                        mEmails.append("," + emailId);
                }
                cur.close();
            }
        }

        Log.d("GroupUtil", " Recipients " + mEmails.toString());
        cursor.close();
        if (recipients == null || recipients.size() == 0) {
            return null;
        }

        return mEmails.toString();
    }

    public static boolean sendEmail(String recipients, Context context) {

        if (recipients != null && (!recipients.isEmpty())) {
            /*
             * Intent intent = new
             * Intent(Intent.ACTION_SENDTO,Uri.fromParts("mailto", recipients,
             * null));
             */

            Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_EMAIL, recipients.split(","));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(context,
                        context.getString(R.string.activity_not_found),
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return false;
    }

    private static String getRecipientEmailId(long[] raw_ids, Context context) {
        String SuperpriEmail = null;
        String priEmail = null;
        String num = null;
        ArrayList<String> Emailarray = new ArrayList<String>();
        for (int i = 0; i < raw_ids.length; i++) {
            long raw_id = raw_ids[i];
            // return cursor : phone or email for each raw_contacts
            Cursor cursor = context.getContentResolver().query(
                    Data.CONTENT_URI,
                    new String[] { Data.DATA1, Data.MIMETYPE, Data.IS_PRIMARY,
                            Data.IS_SUPER_PRIMARY },
                    "( " + Data.MIMETYPE + " = \"" + Email.CONTENT_ITEM_TYPE
                            + "\" ) AND ( " + Data.RAW_CONTACT_ID + " = "
                            + raw_id + " )", null, null);
            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    num = cursor.getString(cursor.getColumnIndex(Data.DATA1));
                    if (cursor.getString(cursor.getColumnIndex(Data.MIMETYPE))
                            .equals(Email.CONTENT_ITEM_TYPE)) {
                        if (cursor.getInt(cursor
                                .getColumnIndex(Data.IS_SUPER_PRIMARY)) == 1) {
                            SuperpriEmail = num;
                        } else if (cursor.getInt(cursor
                                .getColumnIndex(Data.IS_PRIMARY)) == 1) {
                            priEmail = num;
                        } else {
                            Emailarray.add(num);
                        }
                    }
                } // end of while
            }
            if (cursor != null) {
                cursor.close();
            }

        }

        if (SuperpriEmail != null && SuperpriEmail.length() != 0) {
            return SuperpriEmail;
        } else if (priEmail != null && priEmail.length() != 0) {
            return priEmail;
        } else if (Emailarray.size() != 0) {
            return Emailarray.get(0);
        }
        return null;
    }

    private static String getRecipientNumOrEmail(long[] raw_ids, Context context) {
        String Superprinum = null;
        String prinum = null;
        String SuperpriEmail = null;
        String priEmail = null;
        String num = null;
        ArrayList<String> phonearray = new ArrayList<String>();
        ArrayList<String> Emailarray = new ArrayList<String>();
        for (int i = 0; i < raw_ids.length; i++) {
            long raw_id = raw_ids[i];
            // return cursor : phone or email for each raw_contacts
            Cursor cursor = context.getContentResolver().query(
                    Data.CONTENT_URI,
                    new String[] { Data.DATA1, Data.MIMETYPE, Data.IS_PRIMARY,
                            Data.IS_SUPER_PRIMARY },
                    "( " + Data.MIMETYPE + " = \"" + Phone.CONTENT_ITEM_TYPE
                            + "\" OR " + Data.MIMETYPE + " = \""
                            + Email.CONTENT_ITEM_TYPE + "\" ) AND ( "
                            + Data.RAW_CONTACT_ID + " = " + raw_id + " )",
                    null, null);
            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    num = cursor.getString(cursor.getColumnIndex(Data.DATA1));
                    if (cursor.getString(cursor.getColumnIndex(Data.MIMETYPE))
                            .equals(Phone.CONTENT_ITEM_TYPE)) {
                        if (cursor.getInt(cursor
                                .getColumnIndex(Data.IS_SUPER_PRIMARY)) == 1) {
                            Superprinum = num;
                            break;
                        } else if (cursor.getInt(cursor
                                .getColumnIndex(Data.IS_PRIMARY)) == 1) {
                            prinum = num;
                        } else {
                            phonearray.add(num);
                        }
                    } else if (cursor.getString(
                            cursor.getColumnIndex(Data.MIMETYPE)).equals(
                            Email.CONTENT_ITEM_TYPE)) {
                        if (cursor.getInt(cursor
                                .getColumnIndex(Data.IS_SUPER_PRIMARY)) == 1) {
                            SuperpriEmail = num;
                        } else if (cursor.getInt(cursor
                                .getColumnIndex(Data.IS_PRIMARY)) == 1) {
                            priEmail = num;
                        } else {
                            Emailarray.add(num);
                        }
                    }
                } // end of while
            }
            if (cursor != null) {
                cursor.close();
            }

        }

        if (Superprinum != null && Superprinum.length() != 0) {
            return Superprinum;
        } else if (prinum != null && prinum.length() != 0) {
            return prinum;
        } else if (phonearray.size() != 0) {
            return phonearray.get(0);
        } else if (SuperpriEmail != null && SuperpriEmail.length() != 0) {
            return SuperpriEmail;
        } else if (priEmail != null && priEmail.length() != 0) {
            return priEmail;
        } else if (Emailarray.size() != 0) {
            return Emailarray.get(0);
        }
        return null;
    }

    /**
     * get all contact id from group id
     *
     * @param groupId
     * @param baseContext
     * @return list of contact ids
     */
    public static ArrayList<String> getGroupContactIds(final long groupId,
            final Context baseContext) {

        final Cursor cursor = baseContext
                .getContentResolver()
                .query(RawContactsEntity.CONTENT_URI,
                        new String[] { RawContactsEntity.CONTACT_ID },
                        RawContactsEntity.MIMETYPE
                                + " = \""
                                + CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
                                + "\" AND " + RawContactsEntity.DATA1 + " = "
                                + groupId, null, null);

        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            ArrayList<String> contactIds = new ArrayList<String>();
            for (; !cursor.isAfterLast(); cursor.moveToNext()) {
                long id = cursor.getLong(cursor
                        .getColumnIndex(RawContactsEntity.CONTACT_ID));
                if (ContactLoaderUtils.hasContactAddress(baseContext,
                        Long.toString(id))) {
                    contactIds.add(Long.toString(id));
                }
            }
            return contactIds;
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    public static boolean isGroupEmpty(final long groupId,
            final Context baseContext) {

        final Cursor cursor = baseContext
                .getContentResolver()
                .query(RawContactsEntity.CONTENT_URI,
                        new String[] { RawContactsEntity.CONTACT_ID },
                        RawContactsEntity.MIMETYPE
                                + " = \""
                                + CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
                                + "\" AND " + RawContactsEntity.DATA1 + " = "
                                + groupId, null, null);

        if (cursor != null && cursor.getCount() > 0) {
            return false;
        }

        return true;
    }
}
