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
import android.content.Context;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import java.util.List;
import android.os.Bundle;
import android.provider.ContactsContract.RawContacts;
import android.provider.BaseColumns;
import android.provider.SyncStateContract;
import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorEntityIterator;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.graphics.Rect;
import android.graphics.Color;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.CallLog;
import android.provider.ContactsContract.DataUsageFeedback;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.View;

import android.util.Log;
import java.util.ArrayList;
import android.os.ServiceManager;
import com.android.internal.telephony.IIccPhoneBook;
//import com.android.internal.telephony.IIccPhoneBookMSim;
import com.android.internal.telephony.ITelephony;
//import com.android.internal.telephony.ITelephonyMSim;
//import com.android.internal.telephony.AdnRecord;
//import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.TelephonyProperties;

import android.os.RemoteException;
import android.os.Process;

import android.os.StatFs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import android.content.IContentProvider;
import android.telephony.TelephonyManager;
import android.os.SystemProperties;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

public class ContactsLib {
    private static final String TAG = "ContactsLib";
    
	public static final class Intents {
	    public static final String ACTION_GET_CONTENTS =
	            "com.android.contacts.action.ACTION_GET_CONTENTS";
	
	    public static final String MULTI_SEL_KEY =
	        "com.android.contacts.MULTI_SEL_KEY";
	
	    public static final String MULTI_SEL_NAME_KEY =
	        "com.android.contacts.MULTI_SEL_NAME_KEY";
	
	    public static final String MULTI_SEL_EXTRA_KEY =
	        "com.android.contacts.MULTI_SEL_EXTRA_KEY";
	
	    public static final String MULTI_SEL_EXTRA_MAXITEMS =
	        "com.android.contacts.MULTI_SEL_EXTRA_MAXITEMS";
    }
    public static final class Insert {
    	/**
             * The extra field for the contact website.
             * <P>Type: String</P>
             */
        public static final String WEBSITE = "website";

        /**
             * The extra field for the contact website type.
             * <P>Type: Either an integer value from
             * {@link CommonDataKinds.Website}
             *  or a string specifying a custom label.</P>
             */
        public static final String WEBSITE_TYPE = "website_type";

        /**
             * The extra field for the website isprimary flag.
             * <P>Type: boolean</P>
             */
        public static final String WEBSITE_ISPRIMARY = "website_isprimary";
    }
    protected interface MessagesColumns {
	    public static final String PERSON_ID = "person_id";
	    public static final String NUMBER = "number";
	    public static final String NAME = "name";
	    public static final String LOOKUP = "lookup";
	    public static final String PHOTO_ID = "photo_id";
	    public static final String DATE = "date";
    }

    public static final class Messages implements BaseColumns, MessagesColumns{
        /**
	         * This utility class cannot be instantiated
	         */
        private Messages() {
        }

        /**
	         * The content:// style URI for this table, which requests a directory of
	         * raw contact rows matching the selection criteria.
	         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "messages");
    }

    public static final class Calls implements BaseColumns {
    	public static final int REJECTED_TYPE = 5; // chenxiang 20120216 

        public static final int MESSAGE_TYPE = 6; //add for dialpad

        public static final int INCOMING_REJECTED_TYPE = 7;  // chenxiang 20111206 add a fake type for user-rejected type, real type is MISSED_TYPE
        
        public static final String SUB_TYPE = "subtype";

        public static final String ACCOUNT_NAME = "account_name";
        
    	public static final long getCallsDuration(Context context, String key) {
			TelephonyManager mtelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			long duration = 0L;
			Class<?> hideClass = null;
			Method getMethod;
    		
			hideClass = Invoke.getClass("android.telephony.TelephonyManager");
            getMethod= Invoke.getMethod(hideClass, "getCallsDuration", String.class);

            if(getMethod != null) {
	    		duration = (Long)Invoke.invoke(mtelephonyManager,
	    			0L, getMethod, key);
    		}
            
	        return duration;
    	}

    	public static final void setCallsDuration(Context context, String key, long value) {
    		TelephonyManager mtelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			Class<?> hideClass = null;
			Method getMethod;
    		
			hideClass = Invoke.getClass("android.telephony.TelephonyManager");
            getMethod= Invoke.getMethod(hideClass, "setCallsDuration", String.class, long.class);

            if(getMethod != null) {
	    		Invoke.invoke(mtelephonyManager, null, getMethod, key, value);
    		}
	        return ;
    	}
    }

    //20120222 gelifeng add for ipnumbers start
    public static final class Ipnumbers implements BaseColumns {

        public static final Uri CONTENT_URI = Uri.parse("content://ipnumber/ipnumbers");

        public static final Uri DEFAULT_IPNUMBER_URI = 
            Uri.parse("content://ipnumber/ipnumbers/default");

        public static final Uri DEFAULT_GSM_IPNUMBER_URI = 
            Uri.parse("content://ipnumber/ipnumbers/default_gsm");


        public static final String DEFAULT_SORT_ORDER = "_id ASC";

        public static final String NUMBER = "number";

        public static final String TYPE = "type";

        public static final String CURRENT = "current";
    } 
	  public static boolean isCMCCProduct()
	  {
	      String OP = android.os.SystemProperties.get("ro.operator.optr","");
	      return ("OP01").equals(OP);
      
	  }
    public static boolean isExportProduct()
    {
	    return SystemProperties.getBoolean("ro.export",false); 
    }

    public static boolean isSingleMode()
    {
	    Boolean isMulti = false;
		Method getMethod;
		getMethod = Invoke.getMethod(getTelephonyClass(),"isMultiSimEnabled");

		try
		{
			if(getMethod != null) 
			{
				isMulti = (Boolean)Invoke.invoke(getDefaultMethod(),
				true, getMethod);
				if(isMulti == null)
				{
					isMulti = false;
				}
			}	
			else
			{
				isMulti =  false;
			}
		}
		catch (Exception e)
		{
			isMulti =  false;
		}
		return !isMulti;
	    
    }
	
    public static final class RecordsSync
      implements android.provider.BaseColumns
    {
        RecordsSync() { throw new RuntimeException("Stub!"); }

        public static final String CONTENT_DIRECTORY = "recordssync";
        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(Uri.parse("content://com.android.contacts"), CONTENT_DIRECTORY);
                
        public static final String GROUP_RINGTONE = "group_ringtone";
        
        
        public static final String NLOCATION = "nlocation";
        public static final String OPERATION = "operation";
        public static final String PERSON_ID = "person_id";

        //RECORDS_STATUS
        public static final int PIMPB_NO_USE = 0;
        public static final int PIMPB_STATE_ALL = 1;
        public static final int PIMPB_STATE_SYNCED = 2;
        public static final int PIMPB_STATE_ADDED = 3;
        public static final int PIMPB_STATE_REPLACED = 4;
        public static final int PIMPB_STATE_DELETED = 5;
        public static final int PIMPB_STATE_INVALI = 6;

        public static final String STATUS_INFO_SYNCED = "synced";
        public static final String STATUS_INFO_ADD = "add";
        public static final String STATUS_INFO_UPDATE = "update";
        public static final String STATUS_INFO_REMOVE = "remove";
        
        
        public static final int MEMORY_TYPE_PHONE = 0;
        public static final int MEMORY_TYPE_UIM = 1;
        public static final int MEMORY_TYPE_SIM = 2;
        public static final int MEMORY_TYPE_USIM = 3;
        public static final int MEMORY_TYPE_UIM_DU = 101;
        public static final int MEMORY_TYPE_SIM_DU = 102;
        public static final int MEMORY_TYPE_USIM_DU = 103;
        public static final int MAX_COUNT_PHONE = SystemProperties.getInt("ro.hisense.cmcc.test",0)==1?2000:65535;
        
        public static final String ACCOUNT_NAME_HISENSE_LOCAL = "accountname.hisense.contacts.local";
	    public static final String ACCOUNT_TYPE_HISENSE_LOCAL = "accounttype.hisense.contacts.local";
	    public static final String ACCOUNT_NAME_HISENSE_PHONE = "accountname.hisense.contacts.phone";
	    public static final String ACCOUNT_TYPE_HISENSE_PHONE = "accounttype.hisense.contacts.phone";
	    
	    public static final String ACCOUNT_NAME_HISENSE_CARD_USIM = "accountname.hisense.contacts.card.usim";
	    public static final String ACCOUNT_NAME_HISENSE_CARD_SIM = "accountname.hisense.contacts.card.sim";
	    public static final String ACCOUNT_NAME_HISENSE_CARD_UIM = "accountname.hisense.contacts.card.uim";
	    public static final String ACCOUNT_NAME_HISENSE_CARD_USIM_DU = "accountname.hisense.contacts.card.usim.du";
	    public static final String ACCOUNT_NAME_HISENSE_CARD_SIM_DU = "accountname.hisense.contacts.card.sim.du";
	    public static final String ACCOUNT_NAME_HISENSE_CARD_UIM_DU = "accountname.hisense.contacts.card.uim.du";
	    
	    public static final String ACCOUNT_NAME_HISENSE_CARD_UNKNOWN = "accountname.hisense.contacts.card.unknown";
	    
	    public static final String ACCOUNT_TYPE_HISENSE_CARD = "accounttype.hisense.contacts.card";
	    public static final String ACCOUNT_TYPE_HISENSE_CARD_3G = "accounttype.hisense.contacts.card_3g";
    
        static final String ACCOUNT_NAME_UIM = ACCOUNT_NAME_HISENSE_CARD_UIM;
    	static final String ACCOUNT_NAME_SIM = ACCOUNT_NAME_HISENSE_CARD_SIM;
    	static final String ACCOUNT_NAME_USIM = ACCOUNT_NAME_HISENSE_CARD_USIM;
    	static final String ACCOUNT_NAME_UIM_DU = ACCOUNT_NAME_HISENSE_CARD_UIM_DU;
    	static final String ACCOUNT_NAME_SIM_DU = ACCOUNT_NAME_HISENSE_CARD_SIM_DU;
    	static final String ACCOUNT_NAME_USIM_DU = ACCOUNT_NAME_HISENSE_CARD_USIM_DU;
    	static final String ACCOUNT_NAME_PHONE = ACCOUNT_NAME_HISENSE_PHONE;
    	
    	public static final String ACCOUNT_NAME_HISENSE_READONLY = "accountname.hisense.contacts.readonly";
    	public static final String ACCOUNT_TYPE_HISENSE_READONLY = "accounttype.hisense.contacts.readonly";
      public static boolean isHisenseReadOnlyAccount(Account account)
     {
        if (account == null)
            return false;
        
        if (!TextUtils.isEmpty(account.type) 
            && (account.type.equals(ACCOUNT_TYPE_HISENSE_READONLY)))
        {
            return true;
        }

        return false;
     }
     public static boolean isHisenseReadOnlyAccountType(String accounttype)
     {
        if (accounttype == null)
            return false;
        
        if (accounttype.equals(ACCOUNT_TYPE_HISENSE_READONLY))
        {
            return true;
        }

        return false;
     }
    	public static final boolean hasEnoughSpace()
    	{
            return true;
    	}
		
    	public static final String subToCardName(int sub,Context context,String extra)
    	{
    		if(isSingleMode()) {
    			return extra;
    		}
    		Method getSimNameMethod = Invoke.getMethod(getTelephonyClass(),"getSimName",Context.class,int.class);
    		
    		if(getSimNameMethod != null) {
	    		return (String)Invoke.invoke(getDefaultMethod(),
	    			"", getSimNameMethod, context, sub);
    		}	
	    	else {
	    		Method getSimNameMethod1 = Invoke.getMethod(getTelephonyClass(),"getSimName");
	    		if(getSimNameMethod1 != null) {
		    		return "<" + (String)Invoke.invoke(getDefaultMethod(),
		    			"", getSimNameMethod1) + ">";
	    		}
	    		else {
	    			  return extra;
	    		}
	    	}
    	}
    	
    	public static final int AccountNameToSub(String accountName)
    	{
            if(accountName == null)return 0;
			if(accountName.equals(ACCOUNT_NAME_HISENSE_CARD_USIM))
            {
              return 0;  
            }
            if(accountName.equals(ACCOUNT_NAME_HISENSE_CARD_SIM))
            {
              return 0;  
            }
            if(accountName.equals(ACCOUNT_NAME_HISENSE_CARD_UIM))
            {
              return 0;  
            }
            if(accountName.equals(ACCOUNT_NAME_HISENSE_CARD_USIM_DU))
            {
              return 1;  
            }
            if(accountName.equals(ACCOUNT_NAME_HISENSE_CARD_SIM_DU))
            {
              return 1;  
            }
            if(accountName.equals(ACCOUNT_NAME_HISENSE_CARD_UIM_DU))
            {
              return 1;  
            }
            if(accountName.equals(ACCOUNT_NAME_HISENSE_PHONE))
            {
              return 0;  
            }
            return 0;
    	}
    	public static final String subToAccountName(int sub)
    	{
            String modem = UsimRecords.getCurModem(sub);
            if("TD".equals(modem) || "WCDMA".equals(modem))
            {
                if(sub == 0)return  ACCOUNT_NAME_USIM;
                if(sub == 1)return ACCOUNT_NAME_USIM_DU;
                return ACCOUNT_NAME_USIM;
            }
            if("GSM".equals(modem))
            {
                if(sub == 0)return  ACCOUNT_NAME_SIM;
                if(sub == 1)return ACCOUNT_NAME_SIM_DU;
                return ACCOUNT_NAME_SIM;
            }
            if("CDMA".equals(modem))
            {
                if(sub == 0)return  ACCOUNT_NAME_UIM;
                if(sub == 1)return ACCOUNT_NAME_UIM_DU;
                return ACCOUNT_NAME_UIM;
            }
            return ACCOUNT_NAME_SIM;
            
    	}
    	
    	public static final String subToAccountType(int sub)
    	{
            boolean  is3g = UsimRecords.isUsimCard(sub);
            if(is3g)
            {
                
                return com.android.contacts.ContactsLib.RecordsSync.ACCOUNT_TYPE_HISENSE_CARD_3G;
            }
            else
            {
                return com.android.contacts.ContactsLib.RecordsSync.ACCOUNT_TYPE_HISENSE_CARD;
            }
    	}
    	public static final String typeToAccount(int type)
    	{
            
            String return_type = ACCOUNT_NAME_PHONE;
            
            switch (type) {
                case MEMORY_TYPE_UIM:
                    return_type = ACCOUNT_NAME_UIM;
                    break;
                case MEMORY_TYPE_SIM:
                    return_type = ACCOUNT_NAME_SIM;
                    break;
                case MEMORY_TYPE_USIM:
                    return_type = ACCOUNT_NAME_USIM;
                    break;
                case MEMORY_TYPE_UIM_DU:
                    return_type = ACCOUNT_NAME_UIM_DU;
                    break;
                case MEMORY_TYPE_SIM_DU:
                    return_type = ACCOUNT_NAME_SIM_DU;
                    break;
                case MEMORY_TYPE_USIM_DU:
                    return_type = ACCOUNT_NAME_USIM_DU;
                    break;
                case MEMORY_TYPE_PHONE:
                    return_type = ACCOUNT_NAME_PHONE;
                    break;
    
                default:
                    break;
            }
            return return_type;
    	}
    	public static final int accountToType(String account)
    	{
            if(ACCOUNT_NAME_UIM.equals(account))
                return MEMORY_TYPE_UIM;
            if(ACCOUNT_NAME_SIM.equals(account))
                return MEMORY_TYPE_SIM;
            if(ACCOUNT_NAME_USIM.equals(account))
                return MEMORY_TYPE_USIM;
            if(ACCOUNT_NAME_UIM_DU.equals(account))
                return MEMORY_TYPE_UIM_DU;
            if(ACCOUNT_NAME_SIM_DU.equals(account))
                return MEMORY_TYPE_SIM_DU;
            if(ACCOUNT_NAME_USIM_DU.equals(account))
                return MEMORY_TYPE_USIM_DU;
            if(ACCOUNT_NAME_PHONE.equals(account))
                return MEMORY_TYPE_PHONE;
            else
                return MEMORY_TYPE_PHONE;
            
    	}
      
        public static final int pimGetFreeEntryID(ContentResolver cr, int memory_type, int maxcount) {
            String type = "";
            int freeindex = 1;
            Cursor queryCursor = cr.query(RawContacts.CONTENT_URI, new String[] { 
            com.android.contacts.ContactsLib.RecordsSync.NLOCATION},
                    RawContacts.ACCOUNT_NAME + " = '" + typeToAccount(memory_type)+ "' AND " + RawContacts.DELETED + " = 0", null, com.android.contacts.ContactsLib.RecordsSync.NLOCATION);
            if (queryCursor != null) {
                queryCursor.moveToPosition(-1);
                try {
                    while (queryCursor.moveToNext() && freeindex<=maxcount) {
                        int pos = Integer.parseInt(queryCursor.getString(0));
                        if (pos != freeindex)
                            break;
                        freeindex ++;
                    }
                } finally {
                    queryCursor.close();
                }
            }
                
            if (freeindex > maxcount)
                return -1;
                
            return freeindex;
        }

        public static final void pimGetFreeEntryID_Batch(ContentResolver cr, int memory_type, int maxcount, 
                ArrayList<Integer> outlist) {
                    
            if ( null == outlist )
            {
                return;
            }
            
            int type = 0;
            int freeindex = 1;
            Cursor queryCursor = cr.query(RawContacts.CONTENT_URI, new String[] { 
            com.android.contacts.ContactsLib.RecordsSync.NLOCATION},
                    RawContacts.ACCOUNT_NAME + " = '" + typeToAccount(memory_type)+ "' AND " + RawContacts.DELETED + " = 0", null, com.android.contacts.ContactsLib.RecordsSync.NLOCATION);
            if (queryCursor != null) {
                queryCursor.moveToPosition(-1);
                try {
                    int pos = 0;
                    while (freeindex<=maxcount) {
                        if (pos<freeindex)
                        {
                            if (queryCursor.moveToNext())
                            {
                                pos = Integer.parseInt(queryCursor.getString(0));
                            }
                            if (pos != freeindex)
                            {
                                outlist.add(freeindex);
                            }
                        }
                        else if (pos>freeindex)
                        {
                            outlist.add(freeindex);
                        }
                        //pos = Integer.parseInt(queryCursor.getString(0));
                        freeindex ++;
                    }
                } finally {
                    queryCursor.close();
                }
            }
            
        }
      
    
        public static ArrayList<Integer> pimGetRecordsInfo(ContentResolver resolver, int status) {
            String operation = "";
            String where = null;
            switch(status)
            {
                case PIMPB_STATE_SYNCED:
                    operation = "\"" + STATUS_INFO_SYNCED + "\"";
                    where = OPERATION + " = " + operation;
                    break;
                
                case PIMPB_STATE_ADDED:
                    operation = "\"" + STATUS_INFO_ADD;
                    where = OPERATION + " = " + operation + "\"";
                break;
                
                case PIMPB_STATE_REPLACED:
                    operation = "\"" + STATUS_INFO_UPDATE;
                    where = OPERATION + " = " + operation + "\"";
                    break;
                
                case PIMPB_STATE_DELETED:
                    operation = "\"" + STATUS_INFO_REMOVE;
                    where = OPERATION + " = " + operation + "\"";
                    break;
                    
                case PIMPB_STATE_ALL:
                    {
                        Cursor cursor = resolver.query(RawContacts.CONTENT_URI, new String[] {com.android.contacts.ContactsLib.RecordsSync.NLOCATION}, 
                        RawContacts.ACCOUNT_NAME + " = '" + typeToAccount(0)+ "' AND " + RawContacts.DELETED + " = 0" , null, null);
                        ArrayList<Integer> locations = new ArrayList<Integer>();
                        if (cursor != null) {
                            cursor.moveToPosition(-1);
                            try {
                                while (cursor.moveToNext() ) {
                                    locations.add(Integer.parseInt(cursor.getString(0)));
                                }
                            } finally {
                                cursor.close();
                            }
                        }
                        return locations;
                    }
                    //break;
                default:
                    break;
            }
            Cursor queryCursor = resolver.query(CONTENT_URI, new String[] { NLOCATION}, where , null, null);
            ArrayList<Integer> location_list = new ArrayList<Integer>();
            if (queryCursor != null) {
                queryCursor.moveToPosition(-1);
                try {
                    while (queryCursor.moveToNext() ) {
                        location_list.add(Integer.parseInt(queryCursor.getString(0)));
                    }
                } finally {
                    queryCursor.close();
                }
            }
            
            return location_list;
        }

        
    

        public static final int pimUpdateStatus_Batch( ContentResolver resolver, int status)
        {
            ContentValues values = new ContentValues();
            String oper;
            switch(status)
            {
                case PIMPB_NO_USE:
                    oper = STATUS_INFO_REMOVE;
                    return (resolver.delete(CONTENT_URI, OPERATION+ " = \"" + oper + "\"", null));
                
                case PIMPB_STATE_SYNCED:
                    oper = STATUS_INFO_SYNCED;
                    break;
                    
                case PIMPB_STATE_DELETED:
                    oper = STATUS_INFO_REMOVE;
                    break;
                    
                case PIMPB_STATE_ADDED:
                    oper = STATUS_INFO_ADD;
                    break;
                    
                case PIMPB_STATE_REPLACED:
                    oper = STATUS_INFO_UPDATE;
                    break;
              
                default:
                    return -1;
            }
            values.clear();
            values.put(RecordsSync.OPERATION, oper);
            return (resolver.update(CONTENT_URI, values, null, null));
        }

        public static final int pimUpdateStatusAfterSync( ContentResolver resolver)
        {
            int ret = -1;
            ContentValues values = new ContentValues();
            ret = resolver.delete(CONTENT_URI, OPERATION + "=\"" + STATUS_INFO_REMOVE + "\"",null);
            if (ret == -1) {
                return ret;
            }
            values.clear();
            values.put(RecordsSync.OPERATION, STATUS_INFO_SYNCED);
            return (resolver.update(CONTENT_URI, values,null,null));
        }

        
        public static final int pimClearAll( ContentResolver resolver)
        {
            resolver.delete(RawContacts.CONTENT_URI, RawContacts.ACCOUNT_NAME + " = '" + typeToAccount(0)+ "' AND " + RawContacts.DELETED + " = 0", null);
            resolver.delete(CONTENT_URI, null, null);
            
            return 1;
        }
    }

    private static Object getDefaultMethod() {
		Method getDefaultMethod = Invoke.getMethod(getTelephonyClass(),"getDefault");

		return Invoke.invoke(getTelephonyClass(),null,getDefaultMethod);
	}

	private static Class<?> getTelephonyClass() {
		Class<?> hideClass = null;
    		
		hideClass = Invoke.getClass("android.telephony.MSimTelephonyManager");
		if(hideClass == null )
			hideClass = Invoke.getClass("android.telephony.TelephonyManager");

		return hideClass;
	}

    public static final class PbTelephonyManager {
    
    	public static int getColor(Context context, int sub) {
    		Method getColorMethod = Invoke.getMethod(getTelephonyClass(),"getSimColor",Context.class,int.class);
    		
    		if(getColorMethod != null) {
	    		return (Integer)Invoke.invoke(getDefaultMethod(),
	    			-1, getColorMethod, context, sub);
    		}	
	    	else {
	    		Method getColorMethod1 = Invoke.getMethod(getTelephonyClass(),"getSimColor");
	    		return (Integer)Invoke.invoke(getDefaultMethod(),
	    			-1, getColorMethod1);
	    	}
    	}
    	
    	public static int getDefaultColor(int sub) {
    		if(sub == 0) {
    			return Color.GREEN;
    		}
    		else if(sub == 1) {
    			return Color.YELLOW;
    		}

    		return Color.WHITE;
    	}

    	public static String getLast4Number(int sub) {
    		String number = null;
    		int len = 0;
    		
    		Method getLine1NumMethod = Invoke.getMethod(getTelephonyClass(), "getLine1Number", int.class);

    		if(getLine1NumMethod != null) {
	    		number = (String)Invoke.invoke(getDefaultMethod(),
	    			"", getLine1NumMethod, sub);
    		}	
	    	else {
	    		Method getLine1NumMethod1 = Invoke.getMethod(getTelephonyClass(),"getLine1Number");
	    		number = (String)Invoke.invoke(getDefaultMethod(),
	    			"", getLine1NumMethod1);
	    	}

	    	if(number != null) {
	    		if(number.length() > 4) {
	    			len = number.length() - 4;
	    		}
	    		return number.substring(len);
	    	}
	    	else {
	    		return "";
	    	}
    	}

    
    }

    public static final class Invoke {
    	public static Class<?> getClass(String className) {
	        try {
	            return Class.forName(className);
	        } catch (ClassNotFoundException e) {
	            return null;
	        }
	    }

	    public static Method getMethod(Class<?> targetClass, String name,
	            Class<?>... parameterTypes) {
	        if (targetClass == null || TextUtils.isEmpty(name)) return null;
	        try {
	            return targetClass.getMethod(name, parameterTypes);
	        } catch (SecurityException e) {
	            // ignore
	        } catch (NoSuchMethodException e) {
	            // ignore
	        }
	        return null;
	    }

	    public static Field getField(Class<?> targetClass, String name) {
	        if (targetClass == null || TextUtils.isEmpty(name)) return null;
	        try {
	            return targetClass.getField(name);
	        } catch (SecurityException e) {
	            // ignore
	        } catch (NoSuchFieldException e) {
	            // ignore
	        }
	        return null;
	    }

	    public static Field getDeclaredField(Class<?> targetClass, String name) {
	        if (targetClass == null || TextUtils.isEmpty(name)) return null;
	        try {
	            return targetClass.getDeclaredField(name);
	        } catch (SecurityException e) {
	            // ignore
	        } catch (NoSuchFieldException e) {
	            // ignore
	        }
	        return null;
	    }

	    public static Constructor<?> getConstructor(Class<?> targetClass, Class<?> ... types) {
	        if (targetClass == null || types == null) return null;
	        try {
	            return targetClass.getConstructor(types);
	        } catch (SecurityException e) {
	            // ignore
	        } catch (NoSuchMethodException e) {
	            // ignore
	        }
	        return null;
	    }

	    public static Object newInstance(Constructor<?> constructor, Object ... args) {
	        if (constructor == null) return null;
	        try {
	            return constructor.newInstance(args);
	        } catch (Exception e) {
	            Log.e(TAG, "Exception in newInstance: " + e.getClass().getSimpleName());
	        }
	        return null;
	    }

	    public static Object invoke(
	            Object receiver, Object defaultValue, Method method, Object... args) {
	        if (method == null) return defaultValue;
	        try {
	            return method.invoke(receiver, args);
	        } catch (Exception e) {
	            Log.e(TAG, "Exception in invoke: " + e.getClass().getSimpleName());
	        }
	        return defaultValue;
	    }

	    public static Object getFieldValue(Object receiver, Object defaultValue, Field field) {
	        if (field == null) return defaultValue;
	        try {
	            return field.get(receiver);
	        } catch (Exception e) {
	            Log.e(TAG, "Exception in getFieldValue: " + e.getClass().getSimpleName());
	        }
	        return defaultValue;
	    }

	    public static void setFieldValue(Object receiver, Field field, Object value) {
	        if (field == null) return;
	        try {
	        	field.setAccessible(true);
	            field.set(receiver, value);
	        } catch (Exception e) {
	            Log.e(TAG, "Exception in setFieldValue: " + e.getClass().getSimpleName());
	        }
	    }
    }
    public static String getPhoneStoragePath()
    {
         File file = null;
         Method getMethod = Invoke.getMethod(Invoke.getClass("android.os.Environment"), "getPhoneStorageDirectory");
         if(getMethod != null) {
	    		file = (File)Invoke.invoke(null,
	    			"/mnt/hack_it", getMethod);
    		}	
    		Log.i("llikz","getPhoneStoragePath:" + file);
    		if(file == null)return "/mnt/hack_it";
    		if ((!file.exists() || !file.isDirectory() || !file.canWrite()))
        {
           return "/mnt/hack_it";
        }
	    	
	    	return file.toString();
	    	
	    	
   }
   public static String getExternalStoragePath()
    {
        File file=android.os.Environment.getExternalStorageDirectory();
        if(file == null)return "/mnt/hack_it";
	    	if ((!file.exists() || !file.isDirectory() || !file.canWrite()))
        {
           return "/mnt/hack_it";
        }
	    	Log.i("llikz","getExternalStoragePath:" + file.toString());
	    	return file.toString();
	    	
   }
    public static final class UsimRecords
    {
        UsimRecords() { throw new RuntimeException("Stub!"); }

        public static String getNetWorkType() {
        	String netWorkType = SystemProperties.get("ro.telephony.default_network");
        	if(netWorkType != null) {
        		return netWorkType;
        	}

        	return "";
        }
        
        public static String ServiceName = isSingleMode()? "simphonebook" : "simphonebook_msim";
        public static String TELEPHONY_SERVICE = isSingleMode() ? "phone" : "phone_msim";
        
        public static String getCurModem(int subscription)
        {
        	if(isSingleMode()) {
        		if(subscription == 1) {
        			return "";
        		}
        	}
            TelephonyManager telManager = null;
            int iccState;
            telManager = TelephonyManager.getDefault();
            Method getMethod;
            Method getMethod1;
            getMethod= Invoke.getMethod(getTelephonyClass(), "getSimState", int.class);

    		if(getMethod != null) {
	    		iccState = (Integer)Invoke.invoke(getDefaultMethod(),
	    			0, getMethod, subscription);
    		}	
	    	else {
	    		getMethod1 = Invoke.getMethod(getTelephonyClass(),"getSimState");
	    		iccState = (Integer)Invoke.invoke(getDefaultMethod(),
	    			0, getMethod1);
	    	}
    	    	
            //Log.d(TAG,"getCurModem:" + subscription + " iccState = " + iccState);
            if (telManager.SIM_STATE_UNKNOWN == iccState
            	|| telManager.SIM_STATE_ABSENT == iccState
                || telManager.SIM_STATE_PIN_REQUIRED == iccState
                || telManager.SIM_STATE_PUK_REQUIRED == iccState)
            {
                return "";
            }         
            else
            {
				int phoneType;
				getMethod = Invoke.getMethod(getTelephonyClass(), "getCurrentPhoneType", int.class);

          		if(getMethod != null) {
      	    		phoneType = (Integer)Invoke.invoke(getDefaultMethod(),
      	    			0, getMethod, subscription);
          		}	
      	    	else {
      	    		getMethod1 = Invoke.getMethod(getTelephonyClass(),"getCurrentPhoneType");
      	    		phoneType = (Integer)Invoke.invoke(getDefaultMethod(),
      	    			0, getMethod1);
      	    	}

      	    	//Log.d(TAG,"getCurModem:" + subscription + " phoneType = " + phoneType);
				if (phoneType == TelephonyManager.PHONE_TYPE_GSM)
                {
                    if(ContactsLib.UsimRecords.isUsimCard(subscription))
                        return "TD";
                    else
                        return "GSM";
                }
                else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA)
                {
                    return "CDMA";
                }
            }        
            return "";
        }

        public static String getPreCurModem(int subscription) {
        	if(getCurModem(subscription).equals("CDMA")) {
        		return "C";
        	}
        	else if(getCurModem(subscription).equals("GSM")) {
        		return "G";
        	}
        	else if(getCurModem(subscription).equals("TD")) {
        		return "W";
        	}

        	return "";
        }
        public static int getSimState(int subscription)
        {
        	if(isSingleMode()) {
        		if(subscription == 1) {
        			return TelephonyManager.SIM_STATE_ABSENT;
        		}
        	}
            TelephonyManager telManager = null;
            int iccState;
            telManager = TelephonyManager.getDefault();
            Method getMethod;
            Method getMethod1;
            getMethod= Invoke.getMethod(getTelephonyClass(), "getSimState", int.class);

    		if(getMethod != null) {
	    		iccState = (Integer)Invoke.invoke(getDefaultMethod(),
	    			0, getMethod, subscription);
    		}	
	    	else {
	    		getMethod1 = Invoke.getMethod(getTelephonyClass(),"getSimState");
	    		iccState = (Integer)Invoke.invoke(getDefaultMethod(),
	    			0, getMethod1);
	    	}
    	  Log.d(TAG,"getSimState:" + subscription + " iccState = " + iccState);
    	  return iccState;
        }
        public static int getSimType()
        {
        	return getSimType(0);
        }
        
        public static int getSimType(int sub)
        {
        	return 1;
        }
        
        public static boolean isUsimCard(int sub)
        {
        	boolean isUsimCard = false;
            Method getMethod = Invoke.getMethod(getTelephonyClass(), "isUsimCard", int.class);

    		if(getMethod != null) {
	    		isUsimCard = (Boolean)Invoke.invoke(getDefaultMethod(),
	    			false, getMethod, sub);
    		}	
	    	else {
	    		Method getMethod1 = Invoke.getMethod(getTelephonyClass(),"isUsimCard");
	    		isUsimCard = (Boolean)Invoke.invoke(getDefaultMethod(),
	    			false, getMethod1);
	    	}

	    	return isUsimCard;
        }

      
    }

    public static final class Settings {
		public static final String PHONEBOOK_CALL_NETWORK_SELECT = "quick_dial_call_settings";

		public static int SUB1_ONLY = 0;
		public static int SUB2_ONLY = 1;
		public static int SUB1_AND_SUB2 = 2;
	}

    //huangjiufa add for bulk operation
    public static int enableTransactionLock(ContentResolver cr)
    {
        IContentProvider provider = cr.acquireProvider(Contacts.CONTENT_URI);
        if (provider == null) {
            return -1;
        }
        try {
             provider.call("enableTransactionLock",null,null);
             return 1;
            
        } catch (RemoteException e) {
            return -1;
        } finally {
            cr.releaseProvider(provider);
        }
    }

    //huangjiufa add for bulk operation
    public static int disableTransactionLock(ContentResolver cr)
    {
        IContentProvider provider = cr.acquireProvider(Contacts.CONTENT_URI);
        if (provider == null) {
            return -1;
        }
        try {
             provider.call("disableTransactionLock",null,null);
             return 1;
        } catch (RemoteException e) {
            return -1;
        } finally {
            cr.releaseProvider(provider);
        }
    }

    public static HashMap<Integer,String> name_to_pinyin = new HashMap<Integer,String>(1024);//duoyinzi
    static
    {
		name_to_pinyin.put(0x4E01,"ding,zheng");
		name_to_pinyin.put(0x4E07,"wan,mo");
		name_to_pinyin.put(0x4E14,"qie,ju");
		name_to_pinyin.put(0x89E3,"jie,xie");
		name_to_pinyin.put(0x513F,"er,er");
		name_to_pinyin.put(0x4E3C,"jing,dan");
		name_to_pinyin.put(0x4E42,"yi,ai");
		name_to_pinyin.put(0x4E48,"me,ma,yao");
		name_to_pinyin.put(0x4E50,"le,yue");
		name_to_pinyin.put(0x4E57,"cheng,sheng");
		name_to_pinyin.put(0x4E58,"cheng,sheng");
		name_to_pinyin.put(0x4E5C,"mie,nie");
		name_to_pinyin.put(0x4E79,"gan,qian");
		name_to_pinyin.put(0x4E7E,"qian,gan");
		name_to_pinyin.put(0x4E80,"gui,jun,qiu");
		name_to_pinyin.put(0x4E9F,"ji,qi");
		name_to_pinyin.put(0x4EA1,"wang,wu");
		name_to_pinyin.put(0x4EC0,"shen,shi,she");
		name_to_pinyin.put(0x4EC7,"chou,qiu");
		name_to_pinyin.put(0x4ECF,"fo,fu");
		name_to_pinyin.put(0x4ED4,"zi,zai");
		name_to_pinyin.put(0x4EE1,"ge,yi");
		name_to_pinyin.put(0x4F1A,"hui,kuai");
		name_to_pinyin.put(0x4F1C,"cui,zu");
		name_to_pinyin.put(0x4F20,"chuan,zhuan");
		name_to_pinyin.put(0x4F25,"chang,tang");
		name_to_pinyin.put(0x4F27,"cang,chen");
		name_to_pinyin.put(0x4F2E,"nao,nu");
		name_to_pinyin.put(0x4F2F,"bo,bai,ba");
		name_to_pinyin.put(0x4F3A,"si,ci");
		name_to_pinyin.put(0x4F3C,"si,shi");
		name_to_pinyin.put(0x4F3D,"jia,ga,qie,qia");
		name_to_pinyin.put(0x4F40,"si,shi");
		name_to_pinyin.put(0x4F43,"dian,tian");
		name_to_pinyin.put(0x4F53,"ti,ben,ti");
		name_to_pinyin.put(0x4F59,"yu,tu");
		name_to_pinyin.put(0x4F5A,"yi,die");
		name_to_pinyin.put(0x4F5B,"fo,fu");
		name_to_pinyin.put(0x4F61,"xuan,san");
		name_to_pinyin.put(0x4F74,"nai,er");
		name_to_pinyin.put(0x4F7C,"jiao,jia");
		name_to_pinyin.put(0x4F85,"gai,kai");
		name_to_pinyin.put(0x4FA1,"jia,jie");
		name_to_pinyin.put(0x4FA5,"jiao,yao,jia");
		name_to_pinyin.put(0x4FA7,"ce,ze,zhai");
		name_to_pinyin.put(0x4FBF,"bian,pian");
		name_to_pinyin.put(0x4FCA,"jun,zun,juan");
		name_to_pinyin.put(0x4FDE,"yu,shu");
		name_to_pinyin.put(0x4FDF,"si,qi");
		name_to_pinyin.put(0x4FE1,"xin,shen");
		name_to_pinyin.put(0x4FE9,"liang,lia");
		name_to_pinyin.put(0x4FF6,"ti,chu");
		name_to_pinyin.put(0x4FFC,"yu,zhou");
		name_to_pinyin.put(0x4FFE,"bi,bei,bi");
		name_to_pinyin.put(0x5018,"tang,chang");
		name_to_pinyin.put(0x501E,"jing,liang");
		name_to_pinyin.put(0x5048,"jie,ji");
		name_to_pinyin.put(0x5055,"xie,jie");
		name_to_pinyin.put(0x5072,"si,cai");
		name_to_pinyin.put(0x5074,"ce,ze");
		name_to_pinyin.put(0x507B,"lou,lv");
		name_to_pinyin.put(0x5080,"kui,gui");
		name_to_pinyin.put(0x50B3,"chuan,zhuan");
		name_to_pinyin.put(0x50C2,"lou,lv");
		name_to_pinyin.put(0x50E5,"jiao,yao");
		name_to_pinyin.put(0x50EE,"tong,zhuang");
		name_to_pinyin.put(0x5101,"jun,juan");
		name_to_pinyin.put(0x5139,"zan,zuan");
		name_to_pinyin.put(0x514D,"mian,wen");
		name_to_pinyin.put(0x514F,"chang,zhang");
		name_to_pinyin.put(0x5159,"shi,ke");
		name_to_pinyin.put(0x5161,"bai,ke");
		name_to_pinyin.put(0x5166,"wang,wu");
		name_to_pinyin.put(0x516A,"yu,shu");
		name_to_pinyin.put(0x516D,"liu,lu");
		name_to_pinyin.put(0x5176,"qi,ji");
		name_to_pinyin.put(0x5179,"zi,ci");
		name_to_pinyin.put(0x5187,"mao,mou");
		name_to_pinyin.put(0x5190,"mao,mo");
		name_to_pinyin.put(0x5192,"mao,mo");
		name_to_pinyin.put(0x51AF,"feng,ping");
		name_to_pinyin.put(0x51CA,"qing,jing");
		name_to_pinyin.put(0x51F5,"qu,kan");
		name_to_pinyin.put(0x51F9,"ao,wa");
		name_to_pinyin.put(0x51FF,"zao,zuo");
		name_to_pinyin.put(0x5228,"bao,pao");
		name_to_pinyin.put(0x5238,"quan,xuan");
		name_to_pinyin.put(0x5239,"cha,sha");
		name_to_pinyin.put(0x524A,"xue,xiao");
		name_to_pinyin.put(0x5261,"yan,shan");
		name_to_pinyin.put(0x5265,"bo,bao");
		name_to_pinyin.put(0x5278,"tuan,zhuan");
		name_to_pinyin.put(0x527F,"jiao,chao,jia");
		name_to_pinyin.put(0x52AA,"nu,nao");
		name_to_pinyin.put(0x52B2,"jing,jin");
		name_to_pinyin.put(0x52C1,"jing,jin");
		name_to_pinyin.put(0x52E6,"jiao,chao");
		name_to_pinyin.put(0x52FA,"shao,shuo,biao");
		name_to_pinyin.put(0x5319,"chi,shi");
		name_to_pinyin.put(0x531A,"fang,xi");
		name_to_pinyin.put(0x5328,"cang,zang");
		name_to_pinyin.put(0x533A,"qu,ou");
		name_to_pinyin.put(0x5340,"qu,ou");
		name_to_pinyin.put(0x5352,"zu,cu");
		name_to_pinyin.put(0x5355,"dan,chan,shan");
		name_to_pinyin.put(0x5358,"dan,chan");
		name_to_pinyin.put(0x535B,"shuai,lv");
		name_to_pinyin.put(0x535C,"bu,bo");
		name_to_pinyin.put(0x5361,"ka,qia");
		name_to_pinyin.put(0x536C,"ang,yang");
		name_to_pinyin.put(0x5388,"an,chang");
		name_to_pinyin.put(0x5395,"ce,si");
		name_to_pinyin.put(0x53A0,"ce,si");
		name_to_pinyin.put(0x53A6,"sha,xia");
		name_to_pinyin.put(0x53AA,"qin,jin");
		name_to_pinyin.put(0x53B0,"chang,an");
		name_to_pinyin.put(0x53C2,"can,cen,shen");
		name_to_pinyin.put(0x53C3,"can,cen,shen,san");
		name_to_pinyin.put(0x53C5,"can,cen,shen");
		name_to_pinyin.put(0x53E5,"ju,gou");
		name_to_pinyin.put(0x53EC,"zhao,shao");
		name_to_pinyin.put(0x53F6,"ye,xie");
		name_to_pinyin.put(0x5401,"yu,xu,yu");
		name_to_pinyin.put(0x5403,"chi,ji");
		name_to_pinyin.put(0x5408,"he,ge");
		name_to_pinyin.put(0x5413,"he,xia");
		name_to_pinyin.put(0x5421,"bi,pi");
		name_to_pinyin.put(0x5426,"fou,pi");
		//name_to_pinyin.put(0x542C,"ting,yin");
		name_to_pinyin.put(0x542D,"hang,keng");
		name_to_pinyin.put(0x5431,"zhi,zi");
		name_to_pinyin.put(0x543D,"ou,hong");
		name_to_pinyin.put(0x543F,"gao,gu");
		name_to_pinyin.put(0x544A,"gao,gu");
		name_to_pinyin.put(0x5454,"dai,tai");
		name_to_pinyin.put(0x5457,"bei,bai");
		name_to_pinyin.put(0x5462,"ni,ne,na,ne");
		name_to_pinyin.put(0x5471,"gua,gu,wa,gua");
		name_to_pinyin.put(0x5472,"ci,zi");
		name_to_pinyin.put(0x5480,"ju,zui");
		name_to_pinyin.put(0x5489,"bi,fu");
		name_to_pinyin.put(0x5496,"ka,ga");
		name_to_pinyin.put(0x54A5,"xi,die");
		name_to_pinyin.put(0x54AF,"ge,ka,lo,luo,ge");
		name_to_pinyin.put(0x54BC,"guo,kuai");
		name_to_pinyin.put(0x54C5,"xiong,hong");
		name_to_pinyin.put(0x54D5,"hui,yue");
		name_to_pinyin.put(0x54EA,"na,nei,ne,nai");
		name_to_pinyin.put(0x54FC,"heng,hng");
		name_to_pinyin.put(0x5504,"bai,bei");
		name_to_pinyin.put(0x552C,"hu,xia");
		name_to_pinyin.put(0x5541,"zhou,zhao");
		name_to_pinyin.put(0x555C,"chuo,chuai");
		name_to_pinyin.put(0x5574,"tan,chan");
		name_to_pinyin.put(0x5580,"ka,ke,ka");
		name_to_pinyin.put(0x558B,"die,zha");
		name_to_pinyin.put(0x558F,"re,nuo");
		name_to_pinyin.put(0x55AE,"dan,chan,shan");
		name_to_pinyin.put(0x55B0,"shi,si");
		name_to_pinyin.put(0x55B3,"zha,cha");
		name_to_pinyin.put(0x55C4,"a,sha");
		name_to_pinyin.put(0x55CC,"ai,yi");
		name_to_pinyin.put(0x55D2,"da,ta");
		name_to_pinyin.put(0x55D5,"ru,nou");
		name_to_pinyin.put(0x55DF,"jie,jue");
		name_to_pinyin.put(0x55E7,"jia,lun");
		name_to_pinyin.put(0x55E8,"hai,hei");
		name_to_pinyin.put(0x55FE,"sou,zu");
		name_to_pinyin.put(0x560F,"gu,jia");
		name_to_pinyin.put(0x5618,"xu,shi");
		name_to_pinyin.put(0x562C,"zuo,chuai,zhuai");
		name_to_pinyin.put(0x5632,"chao,zhao");
		name_to_pinyin.put(0x5638,"fu,m");
		name_to_pinyin.put(0x563F,"hei,mo,hai");
		name_to_pinyin.put(0x564C,"cheng,ceng");
		name_to_pinyin.put(0x5653,"xu,shi");
		name_to_pinyin.put(0x5666,"hui,yue");
		name_to_pinyin.put(0x5671,"xue,jue");
		name_to_pinyin.put(0x5687,"xia,he");
		name_to_pinyin.put(0x5693,"ca,cha");
		name_to_pinyin.put(0x56E4,"dun,tun");
		name_to_pinyin.put(0x56EA,"cong,chuang");
		name_to_pinyin.put(0x5715,"tu,shu,guan");
		name_to_pinyin.put(0x571C,"yuan,huan");
		name_to_pinyin.put(0x5729,"wei,xu");
		name_to_pinyin.put(0x5730,"di,de");
		name_to_pinyin.put(0x5734,"zhuo,shao");
		name_to_pinyin.put(0x573B,"qi,yin");
		name_to_pinyin.put(0x5747,"jun,yun");
		name_to_pinyin.put(0x574F,"huai,pi,pei");
		name_to_pinyin.put(0x577B,"chi,di");
		name_to_pinyin.put(0x578C,"tong,dong");
		name_to_pinyin.put(0x57CB,"mai,man");
		name_to_pinyin.put(0x57D2,"lie,le");
		name_to_pinyin.put(0x57E1,"wu,ya");
		name_to_pinyin.put(0x57E4,"pi,bei,bi");
		name_to_pinyin.put(0x5800,"ku,jue");
		name_to_pinyin.put(0x5806,"dui,zui");
		name_to_pinyin.put(0x580A,"e,wu");
		name_to_pinyin.put(0x580B,"peng,beng");
		name_to_pinyin.put(0x580E,"leng,ling");
		name_to_pinyin.put(0x5815,"duo,hui");
		name_to_pinyin.put(0x5824,"di,ti");
		name_to_pinyin.put(0x5828,"e,ai");
		name_to_pinyin.put(0x5854,"ta,da");
		name_to_pinyin.put(0x585E,"sai,se");
		name_to_pinyin.put(0x5864,"xun,xuan");
		name_to_pinyin.put(0x5896,"ta,da");
		name_to_pinyin.put(0x58AE,"duo,hui");
		name_to_pinyin.put(0x58B1,"deng,yan");
		name_to_pinyin.put(0x58CA,"huai,pi");
		name_to_pinyin.put(0x58D1,"he,huo");
		name_to_pinyin.put(0x58DE,"huai,pi");
		name_to_pinyin.put(0x58F3,"qiao,ke");
		name_to_pinyin.put(0x590F,"xia,jia");
		name_to_pinyin.put(0x592F,"hang,ben");
		name_to_pinyin.put(0x5932,"ben,tao");
		name_to_pinyin.put(0x5940,"en,mang");
		name_to_pinyin.put(0x5947,"qi,ji");
		name_to_pinyin.put(0x5951,"qi,xie,qie");
		name_to_pinyin.put(0x5958,"zhuang,zang");
		name_to_pinyin.put(0x5973,"nv,ru");
		name_to_pinyin.put(0x59B3,"nai,ni");
		name_to_pinyin.put(0x59E5,"lao,mu");
		name_to_pinyin.put(0x5A1C,"na,nuo");
		name_to_pinyin.put(0x5A20,"shen,chen,zhen");
		name_to_pinyin.put(0x5A29,"mian,wan");
		name_to_pinyin.put(0x5A41,"lou,lv");
		name_to_pinyin.put(0x5A64,"chou,zhou");
		name_to_pinyin.put(0x5A9E,"ti,shi");
		name_to_pinyin.put(0x5ACD,"tao,yao");
		name_to_pinyin.put(0x5AE8,"han,ran");
		name_to_pinyin.put(0x5B1B,"huan,qiong,xuan");
		name_to_pinyin.put(0x5B5B,"bei,bo");
		name_to_pinyin.put(0x5B71,"chan,can");
		name_to_pinyin.put(0x5B78,"xue,xiao");
		name_to_pinyin.put(0x5B85,"zhai,zhe");
		name_to_pinyin.put(0x5B9B,"wan,yuan");
		name_to_pinyin.put(0x5BC0,"cai,shen");
		name_to_pinyin.put(0x5BFB,"xun,xin");
		name_to_pinyin.put(0x5BFD,"lv,luo");
		name_to_pinyin.put(0x5C04,"she,shi,ye");
		name_to_pinyin.put(0x5C09,"wei,yu");
		name_to_pinyin.put(0x5C0B,"xun,xin");
		name_to_pinyin.put(0x5C22,"wang,you");
		name_to_pinyin.put(0x5C23,"wang,you");
		name_to_pinyin.put(0x5C28,"mang,pang");
		name_to_pinyin.put(0x5C3A,"chi,che");
		name_to_pinyin.put(0x5C3E,"wei,yi");
		name_to_pinyin.put(0x5C3F,"niao,sui,ni");
		name_to_pinyin.put(0x5C45,"ju,ji");
		name_to_pinyin.put(0x5C4F,"ping,bing");
		name_to_pinyin.put(0x5C5E,"shu,zhu");
		name_to_pinyin.put(0x5C6C,"shu,zhu");
		name_to_pinyin.put(0x5C6F,"tun,zhun");
		name_to_pinyin.put(0x5C79,"yi,ge");
		name_to_pinyin.put(0x5CD2,"tong,dong");
		name_to_pinyin.put(0x5CD9,"zhi,shi");
		name_to_pinyin.put(0x5CE4,"jiao,qiao");
		name_to_pinyin.put(0x5CFF,"wu,yu");
		name_to_pinyin.put(0x5D24,"yao,xiao");
		name_to_pinyin.put(0x5D34,"wei,wai");
		name_to_pinyin.put(0x5D51,"jie,he");
		name_to_pinyin.put(0x5D80,"tu,die");
		name_to_pinyin.put(0x5DA0,"jiao,qiao");
		name_to_pinyin.put(0x5DC2,"sui,xi");
		name_to_pinyin.put(0x5DF7,"xiang,hang");
		name_to_pinyin.put(0x5E25,"shuai,shuo");
		name_to_pinyin.put(0x5E31,"chou,dao");
		name_to_pinyin.put(0x5E4D,"dao,tao");
		name_to_pinyin.put(0x5E62,"chuang,zhuang");
		name_to_pinyin.put(0x5E6C,"chou,dao");
		name_to_pinyin.put(0x5E7F,"guang,an");
		name_to_pinyin.put(0x5E83,"guang,an");
		name_to_pinyin.put(0x5E95,"di,de");
		name_to_pinyin.put(0x5EAC,"pang,mang");
		name_to_pinyin.put(0x5EB3,"bi,bei");
		name_to_pinyin.put(0x5EC1,"ce,ci");
		name_to_pinyin.put(0x5EC8,"sha,xia");
		name_to_pinyin.put(0x5ED1,"qin,jin");
		name_to_pinyin.put(0x5EE0,"chang,an");
		name_to_pinyin.put(0x5EE3,"guang,an");
		name_to_pinyin.put(0x5F04,"nong,long");
		name_to_pinyin.put(0x5F09,"zang,zhuang");
		name_to_pinyin.put(0x5F37,"qiang,jiang,qiang");
		name_to_pinyin.put(0x5F39,"dan,tan");
		name_to_pinyin.put(0x5F3E,"dan,tan");
		name_to_pinyin.put(0x5F48,"dan,tan");
		name_to_pinyin.put(0x5F4A,"qiang,jiang");
		name_to_pinyin.put(0x5F77,"pang,fang");
		name_to_pinyin.put(0x5F8A,"huai,hui");
		name_to_pinyin.put(0x5FA5,"chi,shi");
		name_to_pinyin.put(0x5FB5,"zhi,zheng");
		name_to_pinyin.put(0x5FD2,"te,tui,tei");
		name_to_pinyin.put(0x5FEA,"zhong,song");
		name_to_pinyin.put(0x5FF8,"niu,nv");
		name_to_pinyin.put(0x600E,"zen,ze");
		name_to_pinyin.put(0x601C,"lian,ling");
		name_to_pinyin.put(0x601D,"si,sai");
		name_to_pinyin.put(0x602B,"fu,fei");
		name_to_pinyin.put(0x602F,"qie,que");
		name_to_pinyin.put(0x6048,"mou,mu");
		name_to_pinyin.put(0x6056,"sai,si");
		name_to_pinyin.put(0x606A,"ke,que");
		name_to_pinyin.put(0x606B,"dong,tong");
		name_to_pinyin.put(0x608A,"zhe,qi");
		name_to_pinyin.put(0x609D,"kui,li");
		name_to_pinyin.put(0x6112,"kai,qi");
		name_to_pinyin.put(0x614A,"qian,qie");
		name_to_pinyin.put(0x615D,"ni,te");
		name_to_pinyin.put(0x61FE,"she,zhe");
		name_to_pinyin.put(0x6206,"gang,zhuang");
		name_to_pinyin.put(0x620C,"xu,qu");
		name_to_pinyin.put(0x620F,"xi,hu");
		name_to_pinyin.put(0x6220,"shi,chi");
		name_to_pinyin.put(0x622F,"xi,hu");
		name_to_pinyin.put(0x6232,"xi,hu");
		name_to_pinyin.put(0x6241,"bian,pian");
		name_to_pinyin.put(0x624E,"zha,za");
		name_to_pinyin.put(0x6255,"fu,bi");
		name_to_pinyin.put(0x6273,"ban,pan");
		name_to_pinyin.put(0x6298,"zhe,she");
		name_to_pinyin.put(0x629E,"ze,zhai");
		name_to_pinyin.put(0x62B9,"mo,ma,mo");
		name_to_pinyin.put(0x62BB,"chen,shen");
		name_to_pinyin.put(0x62C2,"fu,bi");
		name_to_pinyin.put(0x62C6,"chai,ca");
		name_to_pinyin.put(0x62D3,"tuo,ta");
		name_to_pinyin.put(0x62DA,"pan,pin");
		name_to_pinyin.put(0x62E9,"ze,zhai");
		name_to_pinyin.put(0x62EC,"kuo,gua");
		name_to_pinyin.put(0x62F6,"zan,za");
		name_to_pinyin.put(0x62FD,"zhuai,ye,zhuai");
		name_to_pinyin.put(0x62FE,"shi,she");
		name_to_pinyin.put(0x630A,"nong,long");
		name_to_pinyin.put(0x6310,"ru,na");
		name_to_pinyin.put(0x631D,"wo,zhua");
		name_to_pinyin.put(0x631F,"xie,jia");
		name_to_pinyin.put(0x6322,"jiao,jia");
		name_to_pinyin.put(0x6332,"suo,sa,sha");
		name_to_pinyin.put(0x6335,"long,nong");
		name_to_pinyin.put(0x633E,"xie,jia,xia");
		name_to_pinyin.put(0x6341,"jiao,jia");
		name_to_pinyin.put(0x634B,"lv,luo");
		name_to_pinyin.put(0x637F,"xi,qi");
		name_to_pinyin.put(0x63A0,"lve,lve");
		name_to_pinyin.put(0x63B0,"bai,bo");
		name_to_pinyin.put(0x63B1,"shou,pa");
		name_to_pinyin.put(0x63B4,"guai,guo");
		name_to_pinyin.put(0x63BA,"chan,shan,can");
		name_to_pinyin.put(0x63D0,"ti,di,shi");
		name_to_pinyin.put(0x63DF,"xu,ju");
		name_to_pinyin.put(0x63F2,"she,die");
		name_to_pinyin.put(0x6405,"jiao,jia");
		name_to_pinyin.put(0x6412,"bang,peng");
		name_to_pinyin.put(0x6436,"qiang,chuang,qiang");
		name_to_pinyin.put(0x6451,"guo,guai");
		name_to_pinyin.put(0x6458,"zhai,zhe");
		name_to_pinyin.put(0x6461,"gai,xi");
		name_to_pinyin.put(0x6469,"mo,ma");
		name_to_pinyin.put(0x647B,"chan,shan");
		name_to_pinyin.put(0x649E,"zhuang,chuang");
		name_to_pinyin.put(0x649F,"jiao,jia");
		name_to_pinyin.put(0x64B9,"jiao,jia");
		name_to_pinyin.put(0x64BE,"zhua,wo");
		name_to_pinyin.put(0x64C7,"ze,zhai");
		name_to_pinyin.put(0x64D8,"bo,bai");
		name_to_pinyin.put(0x64F5,"mo,ma");
		name_to_pinyin.put(0x6505,"zan,cuan");
		name_to_pinyin.put(0x6512,"zan,cuan");
		name_to_pinyin.put(0x651C,"xie,xi");
		name_to_pinyin.put(0x6522,"zan,cuan");
		name_to_pinyin.put(0x652A,"jiao,gao,jia");
		name_to_pinyin.put(0x655E,"chang,tang");
		name_to_pinyin.put(0x6566,"dun,dui");
		name_to_pinyin.put(0x656B,"jiao,jia");
		name_to_pinyin.put(0x656F,"hun,min");
		name_to_pinyin.put(0x6581,"yi,du");
		name_to_pinyin.put(0x6589,"qi,ji,qi");
		name_to_pinyin.put(0x658A,"qi,ji,qi");
		name_to_pinyin.put(0x659C,"xie,xia");
		name_to_pinyin.put(0x65A2,"tiao,tou");
		name_to_pinyin.put(0x65AA,"qu,ju");
		name_to_pinyin.put(0x65C1,"pang,bang");
		name_to_pinyin.put(0x65E0,"wu,mo");
		name_to_pinyin.put(0x6632,"fei,fu");
		name_to_pinyin.put(0x6647,"xu,kua");
		name_to_pinyin.put(0x665F,"sheng,cheng");
		name_to_pinyin.put(0x666F,"jing,ying");
		name_to_pinyin.put(0x66AB,"zan,zhan");
		name_to_pinyin.put(0x66B4,"bao,pu");
		name_to_pinyin.put(0x66DC,"yao,yue");
		name_to_pinyin.put(0x66DD,"pu,bao");
		name_to_pinyin.put(0x66F3,"ye,zhuai,yi");
		name_to_pinyin.put(0x66FD,"ceng,zeng");
		name_to_pinyin.put(0x66FE,"ceng,zeng");
		name_to_pinyin.put(0x6707,"pi,bi");
		name_to_pinyin.put(0x6718,"juan,zui");
		name_to_pinyin.put(0x671D,"chao,zhao");
		name_to_pinyin.put(0x671E,"ji,qi");
		name_to_pinyin.put(0x671F,"qi,ji,qi");
		name_to_pinyin.put(0x672F,"shu,zhu");
		name_to_pinyin.put(0x6734,"po,piao,pu");
		name_to_pinyin.put(0x6749,"shan,sha");
		name_to_pinyin.put(0x6753,"shao,biao");
		name_to_pinyin.put(0x6773,"yao,miao");
		name_to_pinyin.put(0x6777,"pa,ba");
		name_to_pinyin.put(0x677B,"chou,niu");
		name_to_pinyin.put(0x679D,"zhi,qi");
		name_to_pinyin.put(0x679E,"zong,cong");
		name_to_pinyin.put(0x67B9,"bao,fu");
		name_to_pinyin.put(0x67C9,"fan,bian");
		name_to_pinyin.put(0x67CF,"bo,bai");
		name_to_pinyin.put(0x67DC,"ju,gui");
		name_to_pinyin.put(0x67DE,"zuo,zha");
		name_to_pinyin.put(0x67E5,"cha,zha");
		name_to_pinyin.put(0x67EB,"fu,bi");
		name_to_pinyin.put(0x67FB,"cha,zha");
		name_to_pinyin.put(0x6805,"zha,shan");
		name_to_pinyin.put(0x680E,"li,yue");
		name_to_pinyin.put(0x6816,"qi,xi");
		name_to_pinyin.put(0x6818,"chi,yi");
		name_to_pinyin.put(0x681D,"gua,kuo");
		name_to_pinyin.put(0x681F,"bing,ben");
		name_to_pinyin.put(0x6821,"xiao,jiao");
		name_to_pinyin.put(0x6822,"bo,bai");
		name_to_pinyin.put(0x6838,"he,hu");
		name_to_pinyin.put(0x6841,"heng,hang");
		name_to_pinyin.put(0x6854,"jie,ju");
		name_to_pinyin.put(0x6867,"gui,hui");
		name_to_pinyin.put(0x68A2,"shao,sao");
		name_to_pinyin.put(0x68B9,"bing,bin");
		name_to_pinyin.put(0x68D3,"pou,bang");
		name_to_pinyin.put(0x68F1,"leng,ling");
		name_to_pinyin.put(0x68F2,"qi,xi");
		name_to_pinyin.put(0x68F9,"zhao,zhuo");
		name_to_pinyin.put(0x6909,"cheng,sheng");
		name_to_pinyin.put(0x690E,"zhui,chui");
		name_to_pinyin.put(0x6911,"bei,pi");
		name_to_pinyin.put(0x6940,"yu,ju");
		name_to_pinyin.put(0x6942,"zha,cha");
		name_to_pinyin.put(0x695B,"hu,ku");
		name_to_pinyin.put(0x696F,"shun,dun");
		name_to_pinyin.put(0x6977,"kai,jie");
		name_to_pinyin.put(0x697D,"le,yue");
		name_to_pinyin.put(0x69A6,"gan,han");
		name_to_pinyin.put(0x69C7,"dian,zhen");
		name_to_pinyin.put(0x69DB,"jian,kan");
		name_to_pinyin.put(0x69ED,"qi,cu,qi");
		name_to_pinyin.put(0x6A02,"le,yue");
		name_to_pinyin.put(0x6A05,"cong,zong");
		name_to_pinyin.put(0x6A17,"chu,shu");
		name_to_pinyin.put(0x6A21,"mo,mu");
		name_to_pinyin.put(0x6A38,"pu,po,pu");
		name_to_pinyin.put(0x6A45,"mo,mu");
		name_to_pinyin.put(0x6A47,"qiao,cui");
		name_to_pinyin.put(0x6A48,"rao,nao");
		name_to_pinyin.put(0x6A59,"cheng,chen");
		name_to_pinyin.put(0x6A66,"tong,chuang");
		name_to_pinyin.put(0x6A6D,"gu,ku");
		name_to_pinyin.put(0x6A90,"yan,yin");
		name_to_pinyin.put(0x6A9C,"gui,hui,kuai");
		name_to_pinyin.put(0x6AAA,"li,yue");
		name_to_pinyin.put(0x6AB7,"mi,ni");
		name_to_pinyin.put(0x6ABB,"jian,kan");
		name_to_pinyin.put(0x6AC3,"gui,ju");
		name_to_pinyin.put(0x6ADB,"zhi,jie");
		name_to_pinyin.put(0x6ADF,"li,yue");
		name_to_pinyin.put(0x6B1A,"li,ji");
		name_to_pinyin.put(0x6B39,"yi,qi");
		name_to_pinyin.put(0x6B96,"zhi,shi");
		name_to_pinyin.put(0x6BA0,"chou,xiu");
		name_to_pinyin.put(0x6BB7,"yin,yan");
		name_to_pinyin.put(0x6BBB,"ke,qiao");
		name_to_pinyin.put(0x6BBC,"ke,qiao,que");
		name_to_pinyin.put(0x6C0F,"shi,zhi");
		name_to_pinyin.put(0x6C13,"mang,meng");
		name_to_pinyin.put(0x6C1D,"nei,nai");
		name_to_pinyin.put(0x6C5E,"gong,hong");
		name_to_pinyin.put(0x6C64,"tang,shang");
		name_to_pinyin.put(0x6C81,"qin,shen");
		name_to_pinyin.put(0x6C88,"shen,chen");
		name_to_pinyin.put(0x6C8C,"dun,zhuan");
		name_to_pinyin.put(0x6C92,"mei,mo");
		name_to_pinyin.put(0x6C93,"ta,da,ta");
		name_to_pinyin.put(0x6CA1,"mei,mo");
		name_to_pinyin.put(0x6CCC,"mi,bi");
		name_to_pinyin.put(0x6CE2,"bo,po");
		name_to_pinyin.put(0x6CE3,"qi,xie");
		name_to_pinyin.put(0x6CF7,"long,shuang");
		name_to_pinyin.put(0x6CFA,"luo,po");
		name_to_pinyin.put(0x6D2F,"qie,jie");
		name_to_pinyin.put(0x6D38,"guang,huang");
		name_to_pinyin.put(0x6D3D,"qia,xia");
		name_to_pinyin.put(0x6D3E,"pai,pa");
		name_to_pinyin.put(0x6D45,"qian,jian");
		name_to_pinyin.put(0x6D4D,"hui,kuai");
		name_to_pinyin.put(0x6D52,"hu,xu");
		name_to_pinyin.put(0x6D5A,"jun,xun");
		name_to_pinyin.put(0x6D63,"huan,wan");
		name_to_pinyin.put(0x6D8C,"yong,chong");
		name_to_pinyin.put(0x6DA1,"wo,guo");
		name_to_pinyin.put(0x6DB2,"ye,yi");
		name_to_pinyin.put(0x6DB8,"he,hao");
		name_to_pinyin.put(0x6DC6,"xiao,yao");
		name_to_pinyin.put(0x6DF2,"piao,hu");
		name_to_pinyin.put(0x6DFA,"qian,jian");
		name_to_pinyin.put(0x6E11,"mian,sheng");
		name_to_pinyin.put(0x6E26,"wo,guo");
		name_to_pinyin.put(0x6E67,"yong,chong");
		name_to_pinyin.put(0x6E6E,"yin,yan");
		name_to_pinyin.put(0x6E6F,"tang,shang");
		name_to_pinyin.put(0x6E83,"kui,hui");
		name_to_pinyin.put(0x6EAA,"xi,qi");
		name_to_pinyin.put(0x6EB1,"zhen,qin");
		name_to_pinyin.put(0x6ECE,"ying,xing");
		name_to_pinyin.put(0x6ED1,"hua,gu");
		name_to_pinyin.put(0x6EDD,"long,shuang");
		name_to_pinyin.put(0x6EF8,"hu,xu");
		name_to_pinyin.put(0x6F06,"qi,qu,xi");
		name_to_pinyin.put(0x6F0A,"lv,lou");
		name_to_pinyin.put(0x6F2F,"luo,ta");
		name_to_pinyin.put(0x6F37,"kuo,huo");
		name_to_pinyin.put(0x6F3A,"chuang,shuang");
		name_to_pinyin.put(0x6F5A,"xiao,su");
		name_to_pinyin.put(0x6F70,"kui,hui");
		name_to_pinyin.put(0x6F82,"cheng,deng");
		name_to_pinyin.put(0x6F84,"cheng,deng");
		name_to_pinyin.put(0x6FA0,"min,mian,sheng");
		name_to_pinyin.put(0x6FAE,"kuai,hui");
		name_to_pinyin.put(0x6FB9,"dan,tan");
		name_to_pinyin.put(0x6FD8,"ning,neng");
		name_to_pinyin.put(0x6FDA,"ying,xing");
		name_to_pinyin.put(0x6FE2,"zui,cui");
		name_to_pinyin.put(0x6FFB,"dui,wei");
		name_to_pinyin.put(0x7011,"pu,bao");
		name_to_pinyin.put(0x7015,"bin,pin");
		name_to_pinyin.put(0x7027,"long,shuang");
		name_to_pinyin.put(0x7085,"gui,jiong");
		name_to_pinyin.put(0x7094,"gui,que");
		name_to_pinyin.put(0x70B5,"tong,dong");
		name_to_pinyin.put(0x70D9,"luo,lao");
		name_to_pinyin.put(0x70DF,"yan,yin");
		name_to_pinyin.put(0x70F4,"jing,ting");
		name_to_pinyin.put(0x710C,"qu,jun");
		name_to_pinyin.put(0x710D,"di,ti");
		name_to_pinyin.put(0x7118,"dao,tao");
		name_to_pinyin.put(0x7121,"wu,mo");
		name_to_pinyin.put(0x712F,"zhuo,chao");
		name_to_pinyin.put(0x7156,"nuan,xuan");
		name_to_pinyin.put(0x719F,"shu,shou");
		name_to_pinyin.put(0x71A8,"yun,yu");
		name_to_pinyin.put(0x7219,"rang,shang");
		name_to_pinyin.put(0x721D,"jue,jiao");
		name_to_pinyin.put(0x722A,"zhua,zhao");
		name_to_pinyin.put(0x723F,"pan,qiang,ban");
		name_to_pinyin.put(0x725F,"mou,mu");
		name_to_pinyin.put(0x728D,"jian,qian");
		name_to_pinyin.put(0x7292,"kao,di");
		name_to_pinyin.put(0x72B4,"an,han");
		name_to_pinyin.put(0x72E1,"jiao,jia");
		name_to_pinyin.put(0x72E2,"he,hao,mo");
		name_to_pinyin.put(0x7308,"ba,pi");
		name_to_pinyin.put(0x7332,"xie,he");
		name_to_pinyin.put(0x7387,"lv,shuai,shuo");
		name_to_pinyin.put(0x739A,"yang,chang");
		name_to_pinyin.put(0x739F,"min,wen");
		name_to_pinyin.put(0x73A2,"bin,fen");
		name_to_pinyin.put(0x73E9,"heng,hang");
		name_to_pinyin.put(0x73F2,"hun,hui");
		name_to_pinyin.put(0x73F6,"ti,di");
		name_to_pinyin.put(0x740A,"ya,ye");
		name_to_pinyin.put(0x7422,"zhuo,zuo");
		name_to_pinyin.put(0x7436,"pa,ba");
		name_to_pinyin.put(0x743F,"hun,hui");
		name_to_pinyin.put(0x7441,"mao,mei");
		name_to_pinyin.put(0x74E8,"jiang,hong");
		name_to_pinyin.put(0x74E9,"qian,wa");
		name_to_pinyin.put(0x753A,"ding,ting");
		name_to_pinyin.put(0x753C,"ting,ding");
		name_to_pinyin.put(0x755C,"chu,xu");
		name_to_pinyin.put(0x7566,"qi,xi");
		name_to_pinyin.put(0x756A,"fan,pan");
		name_to_pinyin.put(0x758B,"pi,shu,ya");
		name_to_pinyin.put(0x759F,"nve,yao");
		name_to_pinyin.put(0x75B8,"dan,da");
		name_to_pinyin.put(0x75C3,"xuan,xian");
		name_to_pinyin.put(0x7608,"ji,zhi");
		name_to_pinyin.put(0x7615,"jia,xia");
		name_to_pinyin.put(0x7625,"chai,cuo");
		name_to_pinyin.put(0x7627,"nve,yao");
		name_to_pinyin.put(0x764C,"ai,yan");
		name_to_pinyin.put(0x7658,"li,ji");
		name_to_pinyin.put(0x766C,"xian,xuan");
		name_to_pinyin.put(0x767E,"bai,bo");
		name_to_pinyin.put(0x768E,"jiao,jia");
		name_to_pinyin.put(0x7696,"wan,huan");
		name_to_pinyin.put(0x76D6,"gai,ge");
		name_to_pinyin.put(0x76DB,"sheng,cheng");
		name_to_pinyin.put(0x76DF,"meng,ming");
		name_to_pinyin.put(0x76FE,"dun,shun");
		name_to_pinyin.put(0x7701,"sheng,xing");
		name_to_pinyin.put(0x7765,"pi,bi");
		name_to_pinyin.put(0x77A7,"qiao,ya");
		name_to_pinyin.put(0x77C9,"bin,pin");
		name_to_pinyin.put(0x77DC,"jin,qin,guan");
		name_to_pinyin.put(0x77F3,"shi,dan");
		name_to_pinyin.put(0x7809,"hua,xu");
		name_to_pinyin.put(0x781F,"zha,zuo");
		name_to_pinyin.put(0x7829,"fu,fei");
		name_to_pinyin.put(0x782C,"la,li");
		name_to_pinyin.put(0x7845,"gui,huo");
		name_to_pinyin.put(0x784A,"wei,kui");
		name_to_pinyin.put(0x784C,"luo,ge");
		name_to_pinyin.put(0x788C,"liu,lu");
		name_to_pinyin.put(0x78A9,"shuo,shi");
		name_to_pinyin.put(0x7912,"wo,yi");
		name_to_pinyin.put(0x7947,"zhi,qi,zhi");
		name_to_pinyin.put(0x7962,"mi,ni");
		name_to_pinyin.put(0x796D,"ji,zhai");
		name_to_pinyin.put(0x7974,"gai,jie");
		name_to_pinyin.put(0x7985,"shan,chan");
		name_to_pinyin.put(0x79AA,"chan,shan");
		name_to_pinyin.put(0x79B0,"ni,mi");
		name_to_pinyin.put(0x79CD,"zhong,chong");
		name_to_pinyin.put(0x79D8,"mi,bi,lin");
		name_to_pinyin.put(0x7A17,"bai,bi");
		name_to_pinyin.put(0x7A18,"ji,qi");
		name_to_pinyin.put(0x7A2E,"zhong,chong");
		name_to_pinyin.put(0x7A3D,"ji,qi");
		name_to_pinyin.put(0x7A4C,"su,wei");
		name_to_pinyin.put(0x7A84,"zhai,ze");
		name_to_pinyin.put(0x7A98,"jiong,jun");
		name_to_pinyin.put(0x7AA8,"yin,xun");
		name_to_pinyin.put(0x7ACD,"shi,gong,sheng");
		name_to_pinyin.put(0x7ACF,"qian,gong,sheng");
		name_to_pinyin.put(0x7AD2,"qi,ji");
		name_to_pinyin.put(0x7AD3,"qian,fen,zhi,yi,gong,sheng");
		name_to_pinyin.put(0x7AD4,"gong,sheng");
		name_to_pinyin.put(0x7AD5,"shi,fen,zhi,yi,gong,sheng");
		name_to_pinyin.put(0x7AE1,"yi,gong,sheng,bai,bei,si");
		name_to_pinyin.put(0x7AE2,"si,qi");
		name_to_pinyin.put(0x7B2E,"ze,zuo");
		name_to_pinyin.put(0x7B60,"yun,jun");
		name_to_pinyin.put(0x7B74,"jia,ce");
		name_to_pinyin.put(0x7BB7,"shi,yi");
		name_to_pinyin.put(0x7C00,"ze,kui");
		name_to_pinyin.put(0x7C8B,"cui,sui");
		name_to_pinyin.put(0x7CA2,"zi,ci");
		name_to_pinyin.put(0x7CA5,"zhou,yu");
		name_to_pinyin.put(0x7CC1,"san,shen");
		name_to_pinyin.put(0x7CC2,"san,shen");
		name_to_pinyin.put(0x7CD3,"gu,yu");
		name_to_pinyin.put(0x7CDC,"mi,mei");
		name_to_pinyin.put(0x7CDD,"san,shen");
		name_to_pinyin.put(0x7CFB,"xi,ji");
		name_to_pinyin.put(0x7D04,"yue,yao");
		name_to_pinyin.put(0x7D07,"he,ge");
		name_to_pinyin.put(0x7D2E,"zha,za");
		name_to_pinyin.put(0x7D4E,"heng,hang");
		name_to_pinyin.put(0x7D5C,"xie,jie");
		name_to_pinyin.put(0x7D5E,"jiao,jia");
		name_to_pinyin.put(0x7D61,"luo,lao");
		name_to_pinyin.put(0x7D63,"beng,ping");
		name_to_pinyin.put(0x7D66,"gei,ji");
		name_to_pinyin.put(0x7D88,"ti,di,ti");
		name_to_pinyin.put(0x7D9C,"zong,zeng,zong");
		name_to_pinyin.put(0x7DB8,"lun,guan");
		name_to_pinyin.put(0x7DF6,"bian,pian");
		name_to_pinyin.put(0x7E23,"xian,xuan");
		name_to_pinyin.put(0x7E2E,"suo,su");
		name_to_pinyin.put(0x7E41,"fan,po");
		name_to_pinyin.put(0x7E46,"mou,miao,miu");
		name_to_pinyin.put(0x7E47,"zhou,yao,you");
		name_to_pinyin.put(0x7E4B,"xi,ji");
		name_to_pinyin.put(0x7E6B,"xi,ji");
		name_to_pinyin.put(0x7E70,"qiao,zao");
		name_to_pinyin.put(0x7E73,"jiao,jia,zhuo");
		name_to_pinyin.put(0x7E7F,"jian,kan");
		name_to_pinyin.put(0x7E9B,"dao,du");
		name_to_pinyin.put(0x7EA4,"xian,qian");
		name_to_pinyin.put(0x7EA5,"he,ge");
		name_to_pinyin.put(0x7EA6,"yue,yao");
		name_to_pinyin.put(0x7EB6,"lun,guan");
		name_to_pinyin.put(0x7ED9,"ji,gei");
		name_to_pinyin.put(0x7EDC,"luo,lao");
		name_to_pinyin.put(0x7EDE,"jiao,jia");
		name_to_pinyin.put(0x7EF0,"chuo,chao,chuo");
		name_to_pinyin.put(0x7EFC,"zong,zeng");
		name_to_pinyin.put(0x7EFF,"lv,lu");
		name_to_pinyin.put(0x7F09,"ji,qi");
		name_to_pinyin.put(0x7F0F,"bian,pian");
		name_to_pinyin.put(0x7F29,"suo,su");
		name_to_pinyin.put(0x7F2A,"mou,miao,miu");
		name_to_pinyin.put(0x7F32,"qiao,sao,zao");
		name_to_pinyin.put(0x7F34,"jiao,zhuo,jia");
		name_to_pinyin.put(0x7F58,"fu,fou");
		name_to_pinyin.put(0x7F67,"lin,sen");
		name_to_pinyin.put(0x7F86,"pi,biao");
		name_to_pinyin.put(0x7FC7,"fu,pei");
		name_to_pinyin.put(0x7FCD,"pi,po");
		name_to_pinyin.put(0x7FDF,"zhai,di");
		name_to_pinyin.put(0x8000,"yao,yue");
		name_to_pinyin.put(0x8011,"zhuan,duan");
		name_to_pinyin.put(0x8019,"pa,ba");
		name_to_pinyin.put(0x8052,"guo,gua");
		name_to_pinyin.put(0x80B2,"yu,yo");
		name_to_pinyin.put(0x80D6,"pang,pan");
		name_to_pinyin.put(0x80F2,"hai,gai");
		name_to_pinyin.put(0x80F3,"ge,ga,ge");
		name_to_pinyin.put(0x8108,"mo,mai");
		name_to_pinyin.put(0x8109,"mai,mo");
		name_to_pinyin.put(0x811A,"jiao,jue,jia");
		name_to_pinyin.put(0x812F,"fu,pu");
		name_to_pinyin.put(0x8144,"chui,zhui");
		name_to_pinyin.put(0x814A,"la,xi");
		name_to_pinyin.put(0x814B,"ye,yi");
		name_to_pinyin.put(0x814C,"a,yan");
		name_to_pinyin.put(0x814F,"chuo,duo");
		name_to_pinyin.put(0x815E,"zhuan,dun");
		name_to_pinyin.put(0x8161,"gua,luo");
		name_to_pinyin.put(0x81C2,"bei,bi,bei");
		name_to_pinyin.put(0x81C8,"la,xi");
		name_to_pinyin.put(0x81D1,"nao,ru");
		name_to_pinyin.put(0x81D8,"la,xi");
		name_to_pinyin.put(0x81E6,"guang,wang");
		name_to_pinyin.put(0x81ED,"chou,xiu");
		name_to_pinyin.put(0x81F0,"chou,xiu");
		name_to_pinyin.put(0x822C,"ban,bo,pan");
		name_to_pinyin.put(0x8235,"duo,tuo");
		name_to_pinyin.put(0x823A,"jia,xia");
		name_to_pinyin.put(0x8258,"sou,sao");
		name_to_pinyin.put(0x825F,"chong,tong");
		name_to_pinyin.put(0x8272,"se,shai");
		name_to_pinyin.put(0x827E,"ai,yi");
		name_to_pinyin.put(0x8292,"mang,wang");
		name_to_pinyin.put(0x8298,"bi,pi");
		name_to_pinyin.put(0x82A5,"jie,gai");
		name_to_pinyin.put(0x82AB,"yan,yuan");
		name_to_pinyin.put(0x82BD,"ya,di");
		name_to_pinyin.put(0x82BE,"fei,fu");
		name_to_pinyin.put(0x82CE,"zhu,ning");
		name_to_pinyin.put(0x82D0,"yi,ti");
		name_to_pinyin.put(0x82D5,"tiao,shao");
		name_to_pinyin.put(0x82E3,"ju,qu");
		name_to_pinyin.put(0x82E5,"ruo,re");
		name_to_pinyin.put(0x82E7,"zhu,ning");
		name_to_pinyin.put(0x82F9,"ping,pin");
		name_to_pinyin.put(0x8304,"qie,jia");
		name_to_pinyin.put(0x8308,"zi,ci");
		name_to_pinyin.put(0x831C,"qian,xi");
		name_to_pinyin.put(0x832C,"cha,zha");
		name_to_pinyin.put(0x8351,"ti,yi");
		name_to_pinyin.put(0x8356,"lao,pei");
		name_to_pinyin.put(0x8360,"ji,qi");
		name_to_pinyin.put(0x8364,"hun,xun");
		name_to_pinyin.put(0x8365,"ying,xing");
		name_to_pinyin.put(0x8368,"qian,xun");
		name_to_pinyin.put(0x838E,"sha,suo");
		name_to_pinyin.put(0x8398,"xin,shen");
		name_to_pinyin.put(0x83A5,"niu,chou");
		name_to_pinyin.put(0x83A8,"lang,liang,lang");
		name_to_pinyin.put(0x83A9,"fu,piao");
		name_to_pinyin.put(0x83B4,"wo,zhua");
		name_to_pinyin.put(0x83C0,"yu,wan");
		name_to_pinyin.put(0x8401,"ji,qi");
		name_to_pinyin.put(0x8406,"bei,bi");
		name_to_pinyin.put(0x8415,"qi,ji");
		name_to_pinyin.put(0x842C,"wan,mo");
		name_to_pinyin.put(0x8439,"pian,bian");
		name_to_pinyin.put(0x8449,"xie,ye,she");
		name_to_pinyin.put(0x845A,"shen,ren");
		name_to_pinyin.put(0x8477,"hun,xun");
		name_to_pinyin.put(0x84A1,"bang,pang");
		name_to_pinyin.put(0x84CB,"gai,ge,he");
		name_to_pinyin.put(0x84F2,"qiu,ou");
		name_to_pinyin.put(0x84FC,"liao,lu");
		name_to_pinyin.put(0x84FF,"xu,su");
		name_to_pinyin.put(0x8508,"biao,piao");
		name_to_pinyin.put(0x8513,"man,wan,man");
		name_to_pinyin.put(0x8514,"bo,bu,bo");
		name_to_pinyin.put(0x851A,"yu,wei");
		name_to_pinyin.put(0x8535,"cang,zang");
		name_to_pinyin.put(0x8541,"qian,xun");
		name_to_pinyin.put(0x8548,"xun,jun");
		name_to_pinyin.put(0x8549,"jiao,qiao");
		name_to_pinyin.put(0x8584,"bo,bao,bo");
		name_to_pinyin.put(0x8593,"shen,can,cen");
		name_to_pinyin.put(0x859C,"bi,bo");
		name_to_pinyin.put(0x859F,"lian,xian");
		name_to_pinyin.put(0x85BA,"qi,ji");
		name_to_pinyin.put(0x85C9,"jie,ji");
		name_to_pinyin.put(0x85CD,"lan,la");
		name_to_pinyin.put(0x85CF,"zang,cang");
		name_to_pinyin.put(0x85D3,"xian,li");
		name_to_pinyin.put(0x85E5,"yao,yue");
		name_to_pinyin.put(0x85F7,"zhu,shu");
		name_to_pinyin.put(0x8601,"wu,e");
		name_to_pinyin.put(0x8617,"bo,nie");
		name_to_pinyin.put(0x861A,"xian,li");
		name_to_pinyin.put(0x866B,"chong,hui");
		name_to_pinyin.put(0x8675,"she,yi");
		name_to_pinyin.put(0x8679,"hong,jiang");
		name_to_pinyin.put(0x867E,"xia,ha");
		name_to_pinyin.put(0x868C,"bang,beng");
		name_to_pinyin.put(0x8694,"qi,chi");
		name_to_pinyin.put(0x8695,"can,tian");
		name_to_pinyin.put(0x86B5,"he,ke");
		name_to_pinyin.put(0x86C6,"qu,ju");
		name_to_pinyin.put(0x86C7,"she,yi");
		name_to_pinyin.put(0x86E4,"ha,ge");
		name_to_pinyin.put(0x86F8,"shao,xiao");
		name_to_pinyin.put(0x86FB,"tui,shui");
		name_to_pinyin.put(0x86FE,"e,yi");
		name_to_pinyin.put(0x8721,"la,zha");
		name_to_pinyin.put(0x872F,"bang,beng");
		name_to_pinyin.put(0x874B,"la,zha");
		name_to_pinyin.put(0x874E,"xie,he");
		name_to_pinyin.put(0x8764,"you,qiu");
		name_to_pinyin.put(0x8766,"xia,ha");
		name_to_pinyin.put(0x8777,"li,xi");
		name_to_pinyin.put(0x8778,"gua,wo");
		name_to_pinyin.put(0x8784,"si,shi");
		name_to_pinyin.put(0x87A3,"teng,te");
		name_to_pinyin.put(0x87AB,"shi,zhe");
		name_to_pinyin.put(0x87C0,"shuai,shuo");
		name_to_pinyin.put(0x87C4,"zhe,zhi");
		name_to_pinyin.put(0x87EF,"rao,nao");
		name_to_pinyin.put(0x881F,"la,zha");
		name_to_pinyin.put(0x8840,"xue,xie");
		name_to_pinyin.put(0x8853,"shu,zhu");
		name_to_pinyin.put(0x8870,"shuai,cui");
		name_to_pinyin.put(0x8871,"jie,ji");
		name_to_pinyin.put(0x8879,"qi,zhi");
		name_to_pinyin.put(0x889A,"bo,fu");
		name_to_pinyin.put(0x88AB,"bei,pi");
		name_to_pinyin.put(0x88B7,"jia,qia");
		name_to_pinyin.put(0x88E8,"bi,pi,bei");
		name_to_pinyin.put(0x88F3,"shang,chang");
		name_to_pinyin.put(0x890C,"kun,hui");
		name_to_pinyin.put(0x891A,"chu,zhu");
		name_to_pinyin.put(0x892A,"tun,tui");
		name_to_pinyin.put(0x8952,"bie,bi");
		name_to_pinyin.put(0x8983,"tan,qin");
		name_to_pinyin.put(0x898B,"jian,xian");
		name_to_pinyin.put(0x8990,"jue,jiao");
		name_to_pinyin.put(0x8998,"zhan,chan");
		name_to_pinyin.put(0x899A,"jue,jiao");
		name_to_pinyin.put(0x89BA,"jue,jiao");
		name_to_pinyin.put(0x89C1,"jian,xian");
		name_to_pinyin.put(0x89C9,"jue,jiao");
		name_to_pinyin.put(0x89D2,"jiao,jue,jia");
		name_to_pinyin.put(0x89DC,"zui,zi");
		//name_to_pinyin.put(0x8A25,"ne,na");
		//name_to_pinyin.put(0x8A31,"xu,hu");
		//name_to_pinyin.put(0x8A58,"chu,qu");
		//name_to_pinyin.put(0x8A82,"tiao,diao");
		//name_to_pinyin.put(0x8AAA,"shuo,shui,yue");
		//name_to_pinyin.put(0x8AAC,"shuo,shui,yue");
		//name_to_pinyin.put(0x8AAD,"du,dou");
		//name_to_pinyin.put(0x8AB0,"shui,shei");
		//name_to_pinyin.put(0x8ABF,"diao,tiao");
		//name_to_pinyin.put(0x8B05,"zou,zhou");
		//name_to_pinyin.put(0x8B0E,"mi,mei");
		//name_to_pinyin.put(0x8B0F,"xiao,sou");
		//name_to_pinyin.put(0x8B14,"nve,xue");
		//name_to_pinyin.put(0x8B1A,"shi,yi");
		//name_to_pinyin.put(0x8B56,"jian,zen");
		//name_to_pinyin.put(0x8B58,"shi,zhi");
		//name_to_pinyin.put(0x8B80,"du,dou");
		name_to_pinyin.put(0x8BC6,"shi,zhi");
		name_to_pinyin.put(0x8BD8,"jie,ji");
		name_to_pinyin.put(0x8BF4,"shuo,shui,yue");
		name_to_pinyin.put(0x8BFB,"du,dou");
		name_to_pinyin.put(0x8C01,"shei,shui");
		name_to_pinyin.put(0x8C03,"diao,tiao");
		name_to_pinyin.put(0x8C1C,"mi,mei");
		name_to_pinyin.put(0x8C2E,"jian,zen");
		name_to_pinyin.put(0x8C37,"gu,yu");
		name_to_pinyin.put(0x8C3F,"qi,xi");
		name_to_pinyin.put(0x8C48,"qi,kai");
		name_to_pinyin.put(0x8C4A,"feng,li");
		name_to_pinyin.put(0x8C89,"he,hao,mo");
		name_to_pinyin.put(0x8CAC,"ze,zhai");
		name_to_pinyin.put(0x8CC1,"bi,ben");
		name_to_pinyin.put(0x8CC3,"lin,ren");
		name_to_pinyin.put(0x8CC8,"jia,gu");
		name_to_pinyin.put(0x8CCA,"zei,ze");
		name_to_pinyin.put(0x8CDC,"si,ci");
		name_to_pinyin.put(0x8CFA,"zhuan,zuan");
		name_to_pinyin.put(0x8D32,"bi,ben");
		name_to_pinyin.put(0x8D3E,"jia,gu");
		name_to_pinyin.put(0x8D5A,"zhuan,zuan");
		name_to_pinyin.put(0x8DA3,"qu,cu");
		name_to_pinyin.put(0x8DA8,"qu,cu");
		name_to_pinyin.put(0x8DB3,"zu,ju");
		name_to_pinyin.put(0x8DB5,"bao,bo");
		name_to_pinyin.put(0x8DDE,"li,luo");
		name_to_pinyin.put(0x8DE9,"zhuai,shi");
		name_to_pinyin.put(0x8E0B,"jiao,jia,jue");
		name_to_pinyin.put(0x8E2E,"dian,die");
		name_to_pinyin.put(0x8E4A,"xi,qi");
		name_to_pinyin.put(0x8E62,"di,zhi");
		name_to_pinyin.put(0x8E63,"man,pan");
		name_to_pinyin.put(0x8E72,"dun,cun");
		name_to_pinyin.put(0x8EAB,"shen,juan");
		name_to_pinyin.put(0x8ECA,"che,ju");
		//name_to_pinyin.put(0x8ECB,"ya,zha,ga");
		//name_to_pinyin.put(0x8EE2,"zhuan,zhuai");
		//name_to_pinyin.put(0x8EF5,"rong,fu");
		//name_to_pinyin.put(0x8F49,"zhuan,zhuai");
		name_to_pinyin.put(0x8F4D,"che,zhe");
		name_to_pinyin.put(0x8F66,"che,ju");
		name_to_pinyin.put(0x8F67,"ya,zha,ga");
		name_to_pinyin.put(0x8F9F,"pi,bi,pi");
		name_to_pinyin.put(0x8FD8,"hai,huan");
		name_to_pinyin.put(0x8FD9,"zhei,zhe");
		name_to_pinyin.put(0x8FEB,"po,pai");
		name_to_pinyin.put(0x9002,"shi,kuo");
		name_to_pinyin.put(0x904D,"bian,pian");
		name_to_pinyin.put(0x9057,"yi,wei");
		name_to_pinyin.put(0x9069,"shi,kuo");
		name_to_pinyin.put(0x907A,"yi,wei");
		name_to_pinyin.put(0x9084,"huan,hai,xuan");
		name_to_pinyin.put(0x90AA,"xie,ye");
		name_to_pinyin.put(0x90C7,"xun,huan");
		name_to_pinyin.put(0x90FD,"dou,du");
		name_to_pinyin.put(0x9156,"dan,zhen");
		name_to_pinyin.put(0x9162,"zuo,cu");
		name_to_pinyin.put(0x9166,"fa,po");
		name_to_pinyin.put(0x916A,"lao,luo");
		name_to_pinyin.put(0x9175,"jiao,xiao");
		name_to_pinyin.put(0x917E,"shi,shai");
		//name_to_pinyin.put(0x9197,"fa,po");
		//name_to_pinyin.put(0x91B1,"fa,po");
		name_to_pinyin.put(0x91CD,"zhong,chong");
		name_to_pinyin.put(0x91D0,"li,xi");
		//name_to_pinyin.put(0x91F6,"ta,tuo");
		//name_to_pinyin.put(0x9200,"ba,pa");
		//name_to_pinyin.put(0x923F,"dian,tian");
		//name_to_pinyin.put(0x9247,"ta,tuo");
		//name_to_pinyin.put(0x9248,"she,tuo,ta");
		//name_to_pinyin.put(0x925A,"liu,mao");
		//name_to_pinyin.put(0x925B,"qian,yan");
		//name_to_pinyin.put(0x9278,"jiao,jia");
		//name_to_pinyin.put(0x927F,"jia,ha");
		//name_to_pinyin.put(0x9291,"xian,xi");
		//name_to_pinyin.put(0x929A,"yao,diao,tiao");
		//name_to_pinyin.put(0x92CC,"ting,ding");
		//name_to_pinyin.put(0x9306,"qing,qiang");
		//name_to_pinyin.put(0x9312,"a,e");
		//name_to_pinyin.put(0x932F,"cuo,cu");
		//name_to_pinyin.put(0x937A,"zang,zhe");
		//name_to_pinyin.put(0x93AC,"hao,gao");
		//name_to_pinyin.put(0x93C3,"zu,cu");
		//name_to_pinyin.put(0x9413,"dui,dun");
		//name_to_pinyin.put(0x9414,"tan,chan,xin");
		//name_to_pinyin.put(0x941A,"lou,lue");
		//name_to_pinyin.put(0x943A,"dang,cheng");
		//name_to_pinyin.put(0x9441,"dang,zheng");
		//name_to_pinyin.put(0x9443,"diao,yao");
		//name_to_pinyin.put(0x9470,"yao,yue");
		//name_to_pinyin.put(0x947F,"zao,zuo");
		name_to_pinyin.put(0x94A5,"yao,yue");
		name_to_pinyin.put(0x94AF,"ba,pa");
		name_to_pinyin.put(0x94BF,"dian,tian");
		name_to_pinyin.put(0x94C5,"qian,yan");
		name_to_pinyin.put(0x94CA,"tuo,she,ta");
		name_to_pinyin.put(0x94DB,"dang,cheng");
		name_to_pinyin.put(0x94E3,"xian,xi");
		name_to_pinyin.put(0x94E4,"ting,ding");
		name_to_pinyin.put(0x94EB,"yao,diao,tiao");
		name_to_pinyin.put(0x94F0,"jiao,jia");
		name_to_pinyin.put(0x9516,"qing,qiang");
		name_to_pinyin.put(0x9517,"zhe,zang");
		name_to_pinyin.put(0x9550,"hao,gao");
		name_to_pinyin.put(0x9561,"chan,xin,tan");
		name_to_pinyin.put(0x9566,"dun,dui");
		name_to_pinyin.put(0x9577,"chang,zhang");
		name_to_pinyin.put(0x9578,"chang,zhang");
		name_to_pinyin.put(0x957F,"zhang,chang");
		name_to_pinyin.put(0x95A4,"he,ge");
		//name_to_pinyin.put(0x95BA,"min,wen");
		//name_to_pinyin.put(0x95BC,"e,yan");
		//name_to_pinyin.put(0x95CD,"du,she");
		//name_to_pinyin.put(0x95D2,"da,ta");
		//name_to_pinyin.put(0x95DE,"kan,han,kan");
		//name_to_pinyin.put(0x95E2,"pi,bi,pi");
		name_to_pinyin.put(0x9607,"du,she");
		name_to_pinyin.put(0x960F,"yan,e");
		name_to_pinyin.put(0x9618,"da,ta");
		name_to_pinyin.put(0x961A,"kan,han");
		name_to_pinyin.put(0x961D,"fu,yi");
		name_to_pinyin.put(0x9620,"xin,shen");
		name_to_pinyin.put(0x963D,"dian,yan");
		name_to_pinyin.put(0x9642,"po,bei,pi");
		name_to_pinyin.put(0x9646,"lu,liu");
		name_to_pinyin.put(0x964D,"jiang,xiang");
		name_to_pinyin.put(0x965D,"shan,xia");
		name_to_pinyin.put(0x9676,"tao,yao");
		name_to_pinyin.put(0x9678,"lu,liu");
		name_to_pinyin.put(0x9697,"wei,kui");
		name_to_pinyin.put(0x96B9,"zhui,cui");
		name_to_pinyin.put(0x96BC,"sun,zhun");
		name_to_pinyin.put(0x96BD,"juan,jun");
		name_to_pinyin.put(0x96CB,"jun,juan");
		name_to_pinyin.put(0x96FD,"hang,yu");
		name_to_pinyin.put(0x9719,"ying,ji");
		name_to_pinyin.put(0x9730,"xian,san");
		name_to_pinyin.put(0x9732,"lu,lou");
		name_to_pinyin.put(0x9743,"huo,sui");
		name_to_pinyin.put(0x9753,"jing,liang");
		name_to_pinyin.put(0x975A,"jing,liang");
		name_to_pinyin.put(0x9766,"tian,mian");
		name_to_pinyin.put(0x9781,"bei,tuo");
		name_to_pinyin.put(0x9794,"man,wan");
		name_to_pinyin.put(0x9798,"qiao,shao");
		name_to_pinyin.put(0x97A5,"yi,eng");
		name_to_pinyin.put(0x97B9,"kuo,kui");
		name_to_pinyin.put(0x97FD,"yin,an");
		name_to_pinyin.put(0x9813,"dun,du");
		name_to_pinyin.put(0x981C,"he,han");
		name_to_pinyin.put(0x9821,"jie,xie");
		name_to_pinyin.put(0x986B,"zhan,chan");
		name_to_pinyin.put(0x987F,"dun,du");
		name_to_pinyin.put(0x9889,"jie,xie");
		name_to_pinyin.put(0x988C,"he,ge");
		name_to_pinyin.put(0x98A4,"zhan,chan");
		name_to_pinyin.put(0x98DF,"shi,si,yi");
		name_to_pinyin.put(0x98E0,"shi,si");
		name_to_pinyin.put(0x9903,"jiao,jia");
		name_to_pinyin.put(0x9933,"xing,tang");
		name_to_pinyin.put(0x9939,"tang,xing");
		name_to_pinyin.put(0x9961,"zuan,zan");
		name_to_pinyin.put(0x9963,"shi,si");
		name_to_pinyin.put(0x9967,"xing,tang");
		name_to_pinyin.put(0x997A,"jiao,jia");
		name_to_pinyin.put(0x99AE,"feng,ping");
		//name_to_pinyin.put(0x99B1,"tuo,duo");
		//name_to_pinyin.put(0x99C4,"tuo,duo");
		//name_to_pinyin.put(0x99ED,"hai,xie");
		//name_to_pinyin.put(0x9A0E,"qi,ji");
		//name_to_pinyin.put(0x9A14,"jie,ge");
		//name_to_pinyin.put(0x9A28,"tuo,tan");
		//name_to_pinyin.put(0x9A43,"piao,biao");
		//name_to_pinyin.put(0x9A5F,"zou,zhou");
		//name_to_pinyin.put(0x9A6E,"tuo,duo");
		name_to_pinyin.put(0x9A80,"tai,dai");
		name_to_pinyin.put(0x9AA0,"piao,biao");
		name_to_pinyin.put(0x9AA3,"zhan,chan");
		name_to_pinyin.put(0x9AB0,"tou,shai");
		name_to_pinyin.put(0x9AB1,"jie,xie");
		name_to_pinyin.put(0x9ADF,"biao,shan");
		name_to_pinyin.put(0x9AF4,"fu,fo");
		name_to_pinyin.put(0x9B08,"quan,qian");
		name_to_pinyin.put(0x9B32,"li,ge");
		name_to_pinyin.put(0x9B44,"po,tuo,bo");
		//name_to_pinyin.put(0x9B7A,"he,ge");
		//name_to_pinyin.put(0x9BAD,"gui,xie");
		//name_to_pinyin.put(0x9BD6,"qing,zheng");
		//name_to_pinyin.put(0x9C13,"sai,xi");
		//name_to_pinyin.put(0x9C15,"ha,xia");
		//name_to_pinyin.put(0x9C8C,"ba,bo");
		//name_to_pinyin.put(0x9C91,"gui,xie");
		//name_to_pinyin.put(0x9CAD,"qing,zheng");
		name_to_pinyin.put(0x9D1F,"chi,zhi");
		name_to_pinyin.put(0x9D60,"hu,gu");
		name_to_pinyin.put(0x9D72,"que,qiao");
		name_to_pinyin.put(0x9DA3,"pian,bin");
		name_to_pinyin.put(0x9DA9,"mu,wu");
		name_to_pinyin.put(0x9DB4,"he,hao");
		name_to_pinyin.put(0x9DBB,"gu,hu,gu");
		name_to_pinyin.put(0x9DC1,"yi,ni");
		name_to_pinyin.put(0x9DC5,"li,piao");
		name_to_pinyin.put(0x9DDA,"liao,liu");
		name_to_pinyin.put(0x9E1F,"niao,diao");
		name_to_pinyin.put(0x9E44,"hu,gu");
		name_to_pinyin.put(0x9E58,"gu,hu");
		name_to_pinyin.put(0x9E87,"jun,qun");
		name_to_pinyin.put(0x9E89,"jian,qian");
		name_to_pinyin.put(0x9E8F,"jun,qun");
		name_to_pinyin.put(0x9E95,"jun,qun");
		name_to_pinyin.put(0x9EB6,"chi,li");
		name_to_pinyin.put(0x9EBC,"ma,me,mo");
		name_to_pinyin.put(0x9EBD,"mo,me");
		name_to_pinyin.put(0x9ED0,"chi,li");
		name_to_pinyin.put(0x9EE5,"qing,jing");
		name_to_pinyin.put(0x9EFD,"min,mian");
		name_to_pinyin.put(0x9EFE,"min,mian");
		name_to_pinyin.put(0x9F13,"gu,hu");
		name_to_pinyin.put(0x9F3D,"yan,qui");
		name_to_pinyin.put(0x9F4A,"qi,zhai");
		name_to_pinyin.put(0x9F4D,"zi,ji");
		name_to_pinyin.put(0x9F50,"qi,ji");
		name_to_pinyin.put(0x9F66,"yin,ken");
		name_to_pinyin.put(0x9F88,"yin,ken");
		name_to_pinyin.put(0x5927,"da,dai");
		name_to_pinyin.put(0x9F9F,"gui,jun,qiu");	
		//add frequent chinese
    name_to_pinyin.put(20102,"liao,le");
    name_to_pinyin.put(22823,"da,da");
    name_to_pinyin.put(20195,"dai,dai");
    name_to_pinyin.put(36827,"jin,jin");
    name_to_pinyin.put(25237,"tou,tou");
    name_to_pinyin.put(21574,"dai,dai");
    name_to_pinyin.put(36817,"jin,jin");
    name_to_pinyin.put(39540,"lv,lv");
    name_to_pinyin.put(27850,"bo,bo");
    name_to_pinyin.put(23452,"yi,yi");
    name_to_pinyin.put(24102,"dai,dai");
    name_to_pinyin.put(21683,"ke,ke");
    name_to_pinyin.put(36151,"dai,dai");
    name_to_pinyin.put(24453,"dai,dai");
    name_to_pinyin.put(24459,"lv,lv");
    name_to_pinyin.put(20146,"qin,qin");
    name_to_pinyin.put(23458,"ke,ke");
    name_to_pinyin.put(24608,"dai,dai");
    name_to_pinyin.put(26187,"jin,jin");
    name_to_pinyin.put(34385,"lv,lv");
    name_to_pinyin.put(36879,"tou,tou");
    name_to_pinyin.put(26053,"lv,lv");
    name_to_pinyin.put(30410,"yi,yi");
    name_to_pinyin.put(28024,"jin,jin");
    name_to_pinyin.put(35838,"ke,ke");
    name_to_pinyin.put(35850,"yi,yi");
    name_to_pinyin.put(33879,"zhu,zhu");
    name_to_pinyin.put(21202,"le,le");
    name_to_pinyin.put(34955,"dai,dai");
    name_to_pinyin.put(24471,"de,de");
    name_to_pinyin.put(36910,"dai,dai");
    name_to_pinyin.put(23649,"lv,lv");
    name_to_pinyin.put(31105,"jin,jin");
    name_to_pinyin.put(24847,"yi,yi");
    name_to_pinyin.put(28388,"lv,lv");
    name_to_pinyin.put(22013,"su,su");
    name_to_pinyin.put(27589,"yi,yi");
    name_to_pinyin.put(25140,"dai,dai");
    name_to_pinyin.put(32764,"yi,yi");
    name_to_pinyin.put(22204,"jiao,jiao");
    name_to_pinyin.put(27513,"dai,dai");
    name_to_pinyin.put(21525,"lv,lv");
    name_to_pinyin.put(32907,"lei,lei");
    name_to_pinyin.put(21584,"na,na");
    name_to_pinyin.put(22391,"ke,ke");
    name_to_pinyin.put(21621,"he,he");
    name_to_pinyin.put(20387,"lv,lv");
    name_to_pinyin.put(38109,"lv,lv");
    name_to_pinyin.put(36920,"yi,yi");
    name_to_pinyin.put(27695,"lv,lv");
    name_to_pinyin.put(32533,"lv,lv");
    name_to_pinyin.put(32900,"yi,yi");
    name_to_pinyin.put(28322,"yi,yi");
    name_to_pinyin.put(35074,"gua,gua");
    name_to_pinyin.put(30249,"da,da");
    name_to_pinyin.put(23653,"lv,lv");

    }
}
