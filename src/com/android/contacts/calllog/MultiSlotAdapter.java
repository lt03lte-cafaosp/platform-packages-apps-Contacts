/*
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.calllog;

import android.content.Context;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;

public class MultiSlotAdapter extends BaseAdapter {

    private Context mContext;

    private int[] icons = new int[] {
            R.drawable.ic_tab_sim1, R.drawable.ic_tab_sim2, R.drawable.ic_tab_sim12
    };

    // The position of the currently selected
    private int mPosition = 0;

    // The all type's subsciption is -1, but the at the slot, it's position is 2.
    private static final int ALLSELECT = -1;
    private static final int ALL_SELECT_POSITION = 2;

    private String[] slots = new String[3];

    public MultiSlotAdapter(Context context) {
        mContext = context;
        slots[2] = mContext.getString(R.string.all_call_log);
        initSlots();

        // Get the selected subslot, when the sub is all, that set the sub scription to all select position.
        mPosition = PreferenceManager.getDefaultSharedPreferences(mContext).getInt("Subscription", -1);
        if (ALLSELECT == mPosition) {
            mPosition = ALL_SELECT_POSITION;
        }
    }

   private void initSlots() {
        slots[0] = getMultiSimName(0);
        slots[1] = getMultiSimName(1);
    }

    private String getMultiSimName(int subscription) {
        return Settings.System.getString(mContext.getContentResolver(),
                Settings.System.MULTI_SIM_NAME[subscription]);
    }

    public int getCount() {
        return slots.length;
    }

    public Object getItem(int position) {
        return slots[position];
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null)
            view = LayoutInflater.from(mContext).inflate(R.layout.item_slot, null);
        ImageView icon = (ImageView) view.findViewById(R.id.icon);
        icon.setImageResource(icons[position]);
        TextView msg = (TextView) view.findViewById(R.id.msg);
        msg.setText(slots[position]);

        // Set the selected sub Slot view highlight.
        if (mPosition == position) {
            view.setBackgroundColor(Color.DKGRAY);
        } else {
            view.setBackground(null);
        }
        return view;
    }
}
