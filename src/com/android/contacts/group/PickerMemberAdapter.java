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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.android.contacts.group.SuggestedMember;

import com.android.contacts.R;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;

public class PickerMemberAdapter extends ArrayAdapter<SuggestedMember>
        implements SectionIndexer {

    private List<SuggestedMember> mSuggestedMember = null;
    private Context mContext = null;
    private ArrayList<Integer> mSelectedList = new ArrayList<Integer>();

    HashMap<String, Integer> alphaIndexer = null;
    String[] sections = null;
    public int mSelectedPos = -1;

    public PickerMemberAdapter(Context context,
            ArrayList<SuggestedMember> memberList) {
        super(context, R.layout.row_picker, memberList);
        mSuggestedMember = memberList;
        mContext = context;

        alphaIndexer = new HashMap<String, Integer>();
        int size = mSuggestedMember.size();

        for (int x = (size - 1); x >= 0; x--) {
            SuggestedMember s = mSuggestedMember.get(x);

            // get the first letter of the store
            if ((s != null) && (s.getDisplayName() != null)) {
                String ch = s.getDisplayName().substring(0, 1);
                // convert to uppercase otherwise lowercase a -z will be sorted
                // after upper A-Z
                ch = ch.toUpperCase();

                // HashMap will prevent duplicates
                alphaIndexer.put(ch, x);
            }
        }

        Set<String> sectionLetters = alphaIndexer.keySet();

        // create a list from the set to sort
        ArrayList<String> sectionList = new ArrayList<String>(sectionLetters);
        Collections.sort(sectionList);
        sections = new String[sectionList.size()];
        sectionList.toArray(sections);

    }

    static class ViewHolder {
        public TextView name;
        public ImageView icon;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // TODO Auto-generated method stub
        View rowView = convertView;

        if (rowView == null) {
            LayoutInflater inflator = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            rowView = inflator.inflate(R.layout.row_picker, parent, false);
            ViewHolder viewHolder = new ViewHolder();

            viewHolder.name = (TextView) rowView.findViewById(R.id.name);
            viewHolder.icon = (ImageView) rowView.findViewById(R.id.icon);
            rowView.setTag(viewHolder);
        }

        ViewHolder holder = (ViewHolder) rowView.getTag();

        String displayName = mSuggestedMember.get(position).getDisplayName();
        displayName = (displayName != null && !displayName.trim().isEmpty()) ? displayName
                : mContext.getString(R.string.missing_name);
        holder.name.setText(displayName);

        byte[] byteArray = mSuggestedMember.get(position).getPhotoByteArray();
        if (byteArray == null) {
            holder.icon
                    .setImageResource(R.drawable.ic_contact_picture_holo_light);
        } else {
            Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0,
                    byteArray.length);
            holder.icon.setImageBitmap(bitmap);
        }

        if (mSelectedList.contains(position)) {
            int backgroundId = R.drawable.list_activated_holo;
            Drawable background = mContext.getResources().getDrawable(
                    backgroundId);
            rowView.setBackgroundDrawable(background);
        } else if (mSelectedPos == position) {
            int backgroundId = R.drawable.list_focused_holo;
            Drawable background = mContext.getResources().getDrawable(
                    backgroundId);
            rowView.setBackgroundDrawable(background);
        } else {
            rowView.setBackgroundColor(Color.WHITE);
        }
        return rowView;
    }

    public void addSelection(Integer position) {
        if (!mSelectedList.contains(position))
            mSelectedList.add(position);
    }

    public void removeSelection(Integer position) {
        if (mSelectedList.contains(position))
            mSelectedList.remove(position);
    }

    public ArrayList<Integer> getSelection() {
        return mSelectedList;
    }

    public void deSelectAll() {
        mSelectedList.clear();
    }

    @Override
    public int getPositionForSection(int section) {
        // TODO Auto-generated method stub
        if (alphaIndexer != null && sections != null) {
            return alphaIndexer.get(sections[section]);
        }
        return 0;
    }

    @Override
    public int getSectionForPosition(int position) {
        // TODO Auto-generated method stub
        return 1;
    }

    @Override
    public Object[] getSections() {
        // TODO Auto-generated method stub
        return sections;
    }

    public void setSelectedPosition(int position) {
        mSelectedPos = position;
    }

    public int getSelectedPosition() {
        return mSelectedPos;
    }

}
