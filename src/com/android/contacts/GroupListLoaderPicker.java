/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a Contribution
 */
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
package com.android.contacts;

import android.content.Context;
import android.content.CursorLoader;
import android.net.Uri;
import android.provider.ContactsContract.Groups;
import com.android.contacts.util.Constants;

/**
 * Group loader for the group list that includes details such as the number of contacts per group
 * and number of groups per account. This list is sorted by account type, account name, where the
 * group names are in alphabetical order. Note that the list excludes default, favorite, and deleted
 * groups.
 */
public final class GroupListLoaderPicker extends CursorLoader {

    // The Content URI returns data in this ORDER. So be sure before you change
    // this.
    // This is a custom CONTETN URI used to increase performance for pickers
    private final static String[] COLUMNS = new String[] { Groups.ACCOUNT_NAME,
            Groups.ACCOUNT_TYPE, Groups.DATA_SET, Groups._ID, Groups.TITLE,
            Groups.SUMMARY_COUNT, Constants.EMAIL_COUNT,Constants.PHONE_EMAIL_COUNT };

    // Caution: These constants have been defined but the values returned by the
    // query.
    // Do not change them if the query does not change.
    public final static int ACCOUNT_NAME = 0;
    public final static int ACCOUNT_TYPE = 1;
    public final static int DATA_SET = 2;
    public final static int GROUP_ID = 3;
    public final static int TITLE = 4;
    public final static int FILLER_COUNT = 5;
    public final static int EMAIL_COUNT = 6;
    public final static int EMAIL_PHONE_COUNT = 7;

    private static final Uri GROUP_LIST_URI_SUMMARY = Uri
            .parse(Constants.GROUP_SUMMARY_PICKER_URI);

    public GroupListLoaderPicker(Context context) {
        super(context, GROUP_LIST_URI_SUMMARY, null, null, null, null);
    }

}
