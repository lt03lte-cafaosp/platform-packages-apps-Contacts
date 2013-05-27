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
 * limitations under the License.
 */

package com.android.contacts.activities;

import android.app.ActionBar;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Bundle;
import android.os.Message;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.ContactsActivity;
import com.android.contacts.ContactsUtils;
import com.android.contacts.SimContactsConstants;
import com.android.contacts.R;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.AccountTypeManager;
import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.List;


/**
 * Shows a list of all available accounts, letting the user select under which account to view
 * contacts.
 */
public class MemoryStatusActivity extends ContactsActivity {

    private static final String TAG = "MemoryStatusActivity";

    private static final int SUBACTIVITY_CUSTOMIZE_FILTER = 0;

    public static final String KEY_EXTRA_CONTACT_LIST_FILTER = "contactListFilter";

    private static final int FILTER_LOADER_ID = 0;

    private ListView mListView;

    private List<AccountListItem> mFilters;

    private AccountListAdapter mAdapter;

    Handler mHandler;

    private loaderThread mThread = null;

    public  final class AccountListItem {
    
        /**
         * Obsolete filter which had been used in Honeycomb. This may be stored in
         * {@link SharedPreferences}, but should be replaced with ALL filter when it is found.
         *
         * TODO: "group" filter and relevant variables are all obsolete. Remove them.
         */
    
        public final String accountType;
        public final String accountName;
        public final String dataSet;
        public final Drawable icon;
        public final int total;
        public final int count;
        
        public AccountListItem(String accountType, String accountName, String dataSet,
                Drawable icon, int total, int count) {
            this.accountType = accountType;
            this.accountName = accountName;
            this.dataSet = dataSet;
            this.icon = icon;
            this.total = total;
            this.count = count;
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.contact_list_filter);

        mListView = (ListView) findViewById(android.R.id.list);
        View empty=(View)findViewById(R.id.empty);
        mListView.setEmptyView(empty);
        
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        mFilters = Lists.newArrayList();
        mAdapter = new AccountListAdapter(this);
        mListView.setAdapter(mAdapter);
        
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
            	super.handleMessage(msg);
                mFilters = (List<AccountListItem>)msg.obj;
                mAdapter.notifyDataSetChanged();
            }
	    };
    }

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, PeopleActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
                return true;
			default:
                break;
    	}
		return super.onOptionsItemSelected(item);
	}
	
    @Override
    protected void onResume() {
        super.onResume();
        if(mFilters.isEmpty()){
            if(mThread == null){
                mThread = new loaderThread();
            }
            try{mThread.start();}catch (Exception e){}
        }
    }

    public class loaderThread extends Thread {
    	@Override
    	public void run() {
    	    List<AccountListItem> list = loadAccountFilters(MemoryStatusActivity.this);
                Message msg = Message.obtain();
                msg.obj = list;
                mHandler.sendMessage(msg);
    	}
    }
    private  List<AccountListItem> loadAccountFilters(Context context) {
        final ArrayList<AccountListItem> result = Lists.newArrayList();
        final ArrayList<AccountListItem> accountFilters = Lists.newArrayList();
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(context);
        List<AccountWithDataSet> accounts = accountTypes.getAccounts(true);
        
        ContentResolver cr = context.getContentResolver();
            
        for (AccountWithDataSet account : accounts) {
            AccountType accountType = accountTypes.getAccountType(account.type, account.dataSet);
            if (accountType.isExtension() && !account.hasData(context)) {
                // Hide extensions with no raw_contacts.
                continue;
            }
            Drawable icon = accountType != null ? accountType.getDisplayIcon(context) : null;
            int total = -1;
            int count = 0;
            if (!TextUtils.isEmpty(account.type))
            {
                if (account.type.equals(SimContactsConstants.ACCOUNT_TYPE_SIM))
                {
                    int sub = ContactsUtils.getSub(account.name, account.type);
                    if(ContactsUtils.getUimLoaderStatus(sub) == 1) {
                        total = ContactsUtils.getAdnCount(ContactsUtils.getSub(account.name, account.type));
                        
                        Cursor queryCursor = cr.query(RawContacts.CONTENT_URI, new String[] { RawContacts._ID },
                                RawContacts.ACCOUNT_NAME + " = '" + account.name + "' AND " + RawContacts.DELETED + " = 0", null, null);
                        if (queryCursor != null) {
                            try {
                                count = queryCursor.getCount();
                            } finally {
                                queryCursor.close();
                            }
                        }
                    }
                }
                else {
                    if(account.type.equals(SimContactsConstants.ACCOUNT_TYPE_PHONE) && ContactsUtils.isCmccTest()){
                      total = ContactsUtils.getAdnCount(ContactsUtils.getSub(account.name, account.type));
                    }
                    
                    Cursor queryCursor = cr.query(RawContacts.CONTENT_URI, new String[] { RawContacts._ID },
                            RawContacts.ACCOUNT_NAME + " = '" + account.name + "' AND " + RawContacts.DELETED + " = 0", null, null);
                    if (queryCursor != null) {
                        try {
                            count = queryCursor.getCount();
                        } finally {
                            queryCursor.close();
                        }
                    }
                }
            }
            accountFilters.add(new AccountListItem(
                    account.type, account.name, account.dataSet, icon, total, count));
        }

        final int count = accountFilters.size();
        if (count >= 1) {
            // If we only have one account, don't show it as "account", instead show it as "all"
            result.addAll(accountFilters);
        }
        
        return result;
    }

    private  class AccountListAdapter extends BaseAdapter {
        private final LayoutInflater mLayoutInflater;
        private Context accountContext;

        public AccountListAdapter(Context context) {
            mLayoutInflater = (LayoutInflater) context.getSystemService
                    (Context.LAYOUT_INFLATER_SERVICE);
            accountContext = context;
        }

        @Override
        public int getCount() {
            return mFilters.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public AccountListItem getItem(int position) {
            return mFilters.get(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final View view;
            AccountListItemViewCache viewCache;
            if (convertView != null) {
                view = convertView;
                viewCache = (AccountListItemViewCache) view.getTag();
            } else {
                view = mLayoutInflater.inflate(R.layout.memory_account_list_item, parent, false);
                viewCache = new AccountListItemViewCache(view);
                view.setTag(viewCache);
            }

            bindView(position, convertView, parent, viewCache);
            
            return view;
        }

        private void bindView(int position, View convertView, ViewGroup parent, AccountListItemViewCache viewCache) {
            final AccountListItem filter = mFilters.get(position);
            final AccountTypeManager accountTypes = AccountTypeManager.getInstance(accountContext);
            final AccountType accountType =
                        accountTypes.getAccountType(filter.accountType, filter.dataSet);
            viewCache.accountName.setText(accountType.getDisplayLabel(accountContext)
                + "<" + filter.accountName + ">");
            viewCache.accountType.setVisibility( View.GONE);
            viewCache.totalLy.setVisibility((filter.total != -1) ? View.VISIBLE : View.GONE);
            viewCache.count_total.setText(Integer.toString( filter.total));
            viewCache.count_cur.setText(Integer.toString( filter.count));
        }

        /**
             * Cache of the children views of a contact detail entry represented by a
             * {@link GroupListItem}
             */
        public  class AccountListItemViewCache {
            public final TextView accountType;
            public final TextView accountName;
            public final TextView count_total;
            public final TextView count_cur;
            public final LinearLayout accountLy;
            public final LinearLayout totalLy;
            public final LinearLayout countLy;
            public final View divider;

            public AccountListItemViewCache(View view) {
                accountType = (TextView) view.findViewById(R.id.account_type);
                accountName = (TextView) view.findViewById(R.id.account_name);
                count_total = (TextView) view.findViewById(R.id.count_max);
                count_cur = (TextView) view.findViewById(R.id.count_cur);
                accountLy = (LinearLayout) view.findViewById(R.id.accountLy);
                countLy = (LinearLayout) view.findViewById(R.id.countLy);
                totalLy = (LinearLayout) view.findViewById(R.id.totalLy);
                divider = view.findViewById(R.id.divider);
            }

        }
    }
}
