/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a Contribution
 */
/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * limitations under the License
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
package com.android.contacts.list;

import android.content.Context;
import android.database.Cursor;

import com.android.contacts.GroupMemberLoader;
import com.android.contacts.common.list.ContactEntry;
import com.android.contacts.common.list.ContactTileAdapter;
import com.android.contacts.common.list.ContactTileView;
import com.google.common.collect.Lists;

import java.util.ArrayList;

/**
 * Tile adapter for groups.
 */
public class GroupMemberTileAdapter extends ContactTileAdapter {

    public GroupMemberTileAdapter(Context context, ContactTileView.Listener listener, int numCols) {
        super(context, listener, numCols, DisplayType.GROUP_MEMBERS);
    }

    @Override
    protected void bindColumnIndices() {
        mIdIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_ID;
        mLookupIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_LOOKUP_KEY;
        mPhotoUriIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_PHOTO_URI;
        mNameIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_DISPLAY_NAME_PRIMARY;
        mPresenceIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_PRESENCE_STATUS;
        mStatusIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_STATUS;
    }

    @Override
    protected void saveNumFrequentsFromCursor(Cursor cursor) {
        mNumFrequents = 0;
    }

    @Override
    public int getItemViewType(int position) {
        return ViewTypes.FREQUENT;
    }

    @Override
    protected int getDividerPosition(Cursor cursor) {
        // No divider
        return -1;
    }

    @Override
    public int getCount() {
        if (mContactCursor == null || mContactCursor.isClosed()) {
            return 0;
        }

        return getRowCount(mContactCursor.getCount());
    }

    @Override
    public ArrayList<ContactEntry> getItem(int position) {
        final ArrayList<ContactEntry> resultList = Lists.newArrayListWithCapacity(mColumnCount);
        int contactIndex = position ;

        for (int columnCounter = 0; columnCounter < mColumnCount; columnCounter++) {
            resultList.add(createContactEntryFromCursor(mContactCursor, contactIndex));
            contactIndex++;
        }
        return resultList;
    }
}
