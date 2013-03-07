package com.android.contacts.calllog;


import android.app.Activity;
import android.app.ActionBar;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.content.Intent;
import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;

import com.android.contacts.R;
import com.android.contacts.ContactsLib;

public class AllFragment extends Fragment {

    private static final String TAG = "AllFragment";

    private TextView mTextViewLast;

    private TextView mTextViewIncoming;

    private TextView mTextViewOutgoing;

    private TextView mTextViewAll;

    private String mStringLast;

    private String mStringIncoming;

    private String mStringOutgoing;

    private String mStringAll;

    private int mSubType;

    private static final int MENU_ITEM_CLEAR = 1;

    private static final boolean debug = true;

    private static final boolean mIsSingleCard = false; // chenxiang 20110713

    private static final String ALL_LAST_CALLS = "all_last_calls";
    private static final String ALL_INCOMING_CALLS = "all_incoming_calls";
    private static final String ALL_OUTGOING_CALLS = "all_outgoing_calls";
    private static final String ALL_TOTAL_CALLS = "all_total_calls";
    
    private static final String SUB1_LAST_CALLS = "sub1_last_calls";
    private static final String SUB1_INCOMING_CALLS = "sub1_incoming_calls";
    private static final String SUB1_OUTGOING_CALLS = "sub1_outgoing_calls";
    private static final String SUB1_TOTAL_CALLS = "sub1_total_calls";

    private static final String SUB2_LAST_CALLS = "sub2_last_calls";
    private static final String SUB2_INCOMING_CALLS = "sub2_incoming_calls";
    private static final String SUB2_OUTGOING_CALLS = "sub2_outgoing_calls";
    private static final String SUB2_TOTAL_CALLS = "sub2_total_calls";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		mSubType = 0;
        if (debug)
            Log.e(TAG, "onCreate");

    }
    
    @Override 
    public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            // We have a menu item to show in action bar.
            setHasOptionsMenu(true);
        }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.calls_duration, container, false);
		mTextViewLast = (TextView) v.findViewById(R.id.last_call_duration);
        mTextViewIncoming = (TextView) v.findViewById(R.id.incoming_calls_duration);
        mTextViewOutgoing = (TextView) v.findViewById(R.id.outgoing_calls_duration);
        mTextViewAll = (TextView) v.findViewById(R.id.all_calls_duration);
        
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (debug)
            Log.e(TAG, "onResume");

        getDuration();
        mTextViewLast.setText(mStringLast);
        mTextViewIncoming.setText(mStringIncoming);
        mTextViewOutgoing.setText(mStringOutgoing);
        mTextViewAll.setText(mStringAll);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		android.util.Log.i("lvxinyang", "onCreateOptionsMenu");
        menu.add(0, MENU_ITEM_CLEAR, 0, R.string.clear);
        android.util.Log.i("lvxinyang", "onCreateOptionsMenu");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_CLEAR: {
                doClearAction();
                onResume();
                break;
            }

        }
        return super.onOptionsItemSelected(item);
    }

    private void getDuration() {
        switch (mSubType) {
            case 0: {
                long lastDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), ALL_LAST_CALLS);
                long incomingDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), ALL_INCOMING_CALLS);
                long outgoingDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), ALL_OUTGOING_CALLS);
                long allDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), ALL_TOTAL_CALLS);
                mStringLast = transforToFormat(lastDuration);
                mStringIncoming = transforToFormat(incomingDuration);
                mStringOutgoing = transforToFormat(outgoingDuration);
                mStringAll = transforToFormat(allDuration);
                return;
            }

            case 1: {
                long lastDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB1_LAST_CALLS);
                long incomingDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB1_INCOMING_CALLS);
                long outgoingDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB1_OUTGOING_CALLS);
                long allDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB1_TOTAL_CALLS);
                mStringLast = transforToFormat(lastDuration);
                mStringIncoming = transforToFormat(incomingDuration);
                mStringOutgoing = transforToFormat(outgoingDuration);
                mStringAll = transforToFormat(allDuration);
                return;
            }

            case 2: {
                long lastDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB2_LAST_CALLS);
                long incomingDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB2_INCOMING_CALLS);
                long outgoingDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB2_OUTGOING_CALLS);
                long allDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB2_TOTAL_CALLS);
                mStringLast = transforToFormat(lastDuration);
                mStringIncoming = transforToFormat(incomingDuration);
                mStringOutgoing = transforToFormat(outgoingDuration);
                mStringAll = transforToFormat(allDuration);
                return;
            }
        }

    }

    private String transforToFormat(long duration) {
        String formatDuration;
        long hour, minute, second;
        String StringHour, StringMinute, StringSecond;
        second = duration % 60;
        minute = duration / 60;
        hour = minute / 60;
        minute = minute % 60;
        StringHour = Long.toString(hour);
        if (StringHour.length() < 4) {
            int i = 0;
            int index = (4 - StringHour.length());
            for (i = 0; i < index; i++) {
                StringHour = "0" + StringHour;
            }
        }

        StringMinute = Long.toString(minute);
        if (StringMinute.length() < 2) {
            StringMinute = "0" + StringMinute;
        }

        StringSecond = Long.toString(second);
        if (StringSecond.length() < 2) {
            StringSecond = "0" + StringSecond;
        }

        formatDuration = (StringHour + ": " + StringMinute + ": " + StringSecond);
        return formatDuration;
    }

    private void doClearAction() {
        switch (mSubType) {
            case 0: {
                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_LAST_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_INCOMING_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_OUTGOING_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_TOTAL_CALLS, 0);

                ContactsLib.Calls.setCallsDuration(getActivity(), SUB1_LAST_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB1_INCOMING_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB1_OUTGOING_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB1_TOTAL_CALLS, 0);

                ContactsLib.Calls.setCallsDuration(getActivity(), SUB2_LAST_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB2_INCOMING_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB2_OUTGOING_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB2_TOTAL_CALLS, 0);
                return;
            }

            case 1: {
            	long lastDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB2_LAST_CALLS);
                long incomingDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB2_INCOMING_CALLS);
                long outgoingDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB2_OUTGOING_CALLS);
                long allDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB2_TOTAL_CALLS);

                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_LAST_CALLS, lastDuration);
                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_INCOMING_CALLS, incomingDuration);
                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_OUTGOING_CALLS, outgoingDuration);
                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_TOTAL_CALLS, allDuration);
                
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB1_LAST_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB1_INCOMING_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB1_OUTGOING_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB1_TOTAL_CALLS, 0);
                return;
            }

            case 2: {
            	long lastDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB1_LAST_CALLS);
                long incomingDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB1_INCOMING_CALLS);
                long outgoingDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB1_OUTGOING_CALLS);
                long allDuration = ContactsLib.Calls
                        .getCallsDuration(getActivity(), SUB1_TOTAL_CALLS);

                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_LAST_CALLS, lastDuration);
                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_INCOMING_CALLS, incomingDuration);
                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_OUTGOING_CALLS, outgoingDuration);
                ContactsLib.Calls.setCallsDuration(getActivity(), ALL_TOTAL_CALLS, allDuration);
                
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB2_LAST_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB2_INCOMING_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB2_OUTGOING_CALLS, 0);
                ContactsLib.Calls.setCallsDuration(getActivity(), SUB2_TOTAL_CALLS, 0);
                return;
            }
        }
    }
}
