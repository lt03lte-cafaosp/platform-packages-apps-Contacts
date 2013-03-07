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
package com.android.contacts.calllog;

//BEGIN_INCLUDE(complete)
import java.util.HashMap;

import android.content.Context;
import android.app.ActionBar;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.TabHost;
import android.view.LayoutInflater;
import android.app.ActionBar.LayoutParams;

import com.android.contacts.R;
import com.android.contacts.ContactsLib;

/**
 * This demonstrates how you can implement switching between the tabs of a
 * TabHost through fragments.  It uses a trick (see the code below) to allow
 * the tabs to switch between fragments instead of simple views.
 */
public class FragmentTabs extends FragmentActivity {
    TabHost mTabHost;
    TabManager mTabManager;
    String networkType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_tabs);
        mTabHost = (TabHost)findViewById(android.R.id.tabhost);
        mTabHost.setup();
		
		networkType = ContactsLib.UsimRecords.getNetWorkType();	 
		String subTitle = "";
		String subTitle1 = "";
		if(ContactsLib.isSingleMode()) {
			subTitle = getString(R.string.str_slot);
			subTitle1 = "";
		}
		else {
	        if(networkType.equals("4,1")) {
	        	subTitle = getString(R.string.sub1);
	        	subTitle1 = getString(R.string.sub2);
	        }
	        else if(networkType.equals("0,1")) {
	        	subTitle = ContactsLib.RecordsSync.subToCardName(0, this, "");
	        	subTitle1 = ContactsLib.RecordsSync.subToCardName(1, this, "");
	        }
        }
        
        mTabManager = new TabManager(this, mTabHost, R.id.realtabcontent);

        mTabManager.addTab(mTabHost.newTabSpec("all").setIndicator(getString(R.string.all), getResources().getDrawable(R.drawable.ic_tab_duration_all)),
                AllFragment.class, null);
    	if(!ContactsLib.isSingleMode()) {
	        mTabManager.addTab(mTabHost.newTabSpec("SUB1").setIndicator(subTitle, getResources().getDrawable(R.drawable.ic_tab_duration_cdma)),
	                SubOneFragment.class, null);
	        mTabManager.addTab(mTabHost.newTabSpec("SUB2").setIndicator(subTitle1, getResources().getDrawable(R.drawable.ic_tab_duration_gsm)),
	                SubTwoFragment.class, null);
        }        

        if (savedInstanceState != null) {
            mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
        }
        
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
			LayoutInflater inflater = (LayoutInflater) getSystemService
				(Context.LAYOUT_INFLATER_SERVICE);
			View customActionBarView = inflater.inflate(R.layout.call_duration, null);
			actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
			ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME |
			ActionBar.DISPLAY_SHOW_TITLE);
			ActionBar.LayoutParams lp=new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
			ActionBar.LayoutParams.WRAP_CONTENT);
			actionBar.setCustomView(customActionBarView,lp);
		}
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("tab", mTabHost.getCurrentTabTag());
    }

    /**
     * This is a helper class that implements a generic mechanism for
     * associating fragments with the tabs in a tab host.  It relies on a
     * trick.  Normally a tab host has a simple API for supplying a View or
     * Intent that each tab will show.  This is not sufficient for switching
     * between fragments.  So instead we make the content part of the tab host
     * 0dp high (it is not shown) and the TabManager supplies its own dummy
     * view to show as the tab content.  It listens to changes in tabs, and takes
     * care of switch to the correct fragment shown in a separate content area
     * whenever the selected tab changes.
     */
    public static class TabManager implements TabHost.OnTabChangeListener {
        private final FragmentActivity mActivity;
        private final TabHost mTabHost;
        private final int mContainerId;
        private final HashMap<String, TabInfo> mTabs = new HashMap<String, TabInfo>();
        TabInfo mLastTab;

        static final class TabInfo {
            private final String tag;
            private final Class<?> clss;
            private final Bundle args;
            private Fragment fragment;

            TabInfo(String _tag, Class<?> _class, Bundle _args) {
                tag = _tag;
                clss = _class;
                args = _args;
            }
        }

        static class DummyTabFactory implements TabHost.TabContentFactory {
            private final Context mContext;

            public DummyTabFactory(Context context) {
                mContext = context;
            }

            @Override
            public View createTabContent(String tag) {
                View v = new View(mContext);
                v.setMinimumWidth(0);
                v.setMinimumHeight(0);
                return v;
            }
        }

        public TabManager(FragmentActivity activity, TabHost tabHost, int containerId) {
            mActivity = activity;
            mTabHost = tabHost;
            mContainerId = containerId;
            mTabHost.setOnTabChangedListener(this);
        }

        public void addTab(TabHost.TabSpec tabSpec, Class<?> clss, Bundle args) {
            tabSpec.setContent(new DummyTabFactory(mActivity));
            String tag = tabSpec.getTag();

            TabInfo info = new TabInfo(tag, clss, args);

            // Check to see if we already have a fragment for this tab, probably
            // from a previously saved state.  If so, deactivate it, because our
            // initial state is that a tab isn't shown.
            info.fragment = mActivity.getSupportFragmentManager().findFragmentByTag(tag);
            if (info.fragment != null && !info.fragment.isDetached()) {
                FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
                ft.detach(info.fragment);
                ft.commit();
            }

            mTabs.put(tag, info);
            mTabHost.addTab(tabSpec);
        }

        @Override
        public void onTabChanged(String tabId) {
            TabInfo newTab = mTabs.get(tabId);
            if (mLastTab != newTab) {
                FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
                if (mLastTab != null) {
                    if (mLastTab.fragment != null) {
                        ft.detach(mLastTab.fragment);
                    }
                }
                if (newTab != null) {
                    if (newTab.fragment == null) {
                        newTab.fragment = Fragment.instantiate(mActivity,
                                newTab.clss.getName(), newTab.args);
                        ft.add(mContainerId, newTab.fragment, newTab.tag);
                    } else {
                        ft.attach(newTab.fragment);
                    }
                }

                mLastTab = newTab;
                ft.commit();
                mActivity.getSupportFragmentManager().executePendingTransactions();
            }
        }
    }
}
//END_INCLUDE(complete)
