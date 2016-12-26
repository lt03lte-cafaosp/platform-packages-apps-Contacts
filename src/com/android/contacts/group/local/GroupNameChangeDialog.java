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
package com.android.contacts.group.local;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.provider.LocalGroups;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import com.android.contacts.R;

public class GroupNameChangeDialog extends AlertDialog implements OnClickListener, TextWatcher {

    public static interface GroupNameChangeListener {
        void onGroupNameChange(String name);
    }

    public static final int GROUP_NAME_MAX_LENGTH = 20;

    private EditText mGroupSettings;

    private GroupNameChangeListener mGroupNameChangeListener;
    private int mChangeStartPos;
    private int mChangeCount;

    public GroupNameChangeDialog(Context context, String oldGroupName,
                                 GroupNameChangeListener groupNameChangeListener){
        super(context);
        this.mGroupNameChangeListener = groupNameChangeListener;
        mGroupSettings = new EditText(context);
        mGroupSettings.setText(oldGroupName);
        mGroupSettings.setHint(R.string.title_group_name);
        mGroupSettings.addTextChangedListener(this);
        setTitle(R.string.title_group_name);
        setView(mGroupSettings);
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                this);
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
        setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                // Because the dialog will reuse, should remove the last group
                // name if we show another add group dialog.
                getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                        mGroupSettings.getText().length() > 0);
            }
        });
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                String name = mGroupSettings.getText().toString();
                if (checkGroupTitleExist(name)) {
                    Toast.makeText(getContext(), R.string.error_group_exist,
                            Toast.LENGTH_SHORT).show();
                } else {
                    mGroupNameChangeListener.onGroupNameChange(name);
                }
                break;
        }
        mGroupSettings.setText(null);
    }

    private boolean checkGroupTitleExist(String name) {
        Cursor cursor = null;
        try {
            cursor = this.getContext().getContentResolver()
                    .query(LocalGroups.CONTENT_URI, null, LocalGroups.GroupColumns.TITLE + "=?",
                            new String[] { name }, null);
            if (cursor != null)
                return cursor.getCount() > 0;
            else {
                return false;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public void beforeTextChanged(CharSequence sequence, int start, int count, int after) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onTextChanged(CharSequence sequence, int start, int before, int count) {
        // The start position of new added characters
        mChangeStartPos = start;
        // The number of new added characters
        mChangeCount = count;
    }

    @Override
    public void afterTextChanged(Editable editable) {
        limitTextSize(editable);
        getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(mGroupSettings.getText().length() > 0);
    }

    private void limitTextSize(Editable editable) {
        String name = editable.toString();
        int length = 0;
        // The end position of insert string
        int insertEnd = mChangeStartPos + mChangeCount - 1;
        // The string need to keep.
        String keepStr = name.substring(insertEnd + 1);
        // Keep string len
        int keepStrCharLen = 0;
        for (int position = 0; position < keepStr.length(); position++) {
            // Get the char length of keep stringi.
            // To get the char number need insert.
            keepStrCharLen += getCharacterVisualLength(keepStr, position);
        }
        String headStr = name.substring(0, insertEnd + 1);
        for (int position = 0; position < headStr.length(); position++) {
            int characterPostion = Character.codePointAt(name, position);
            length += getCharacterVisualLength(name, position);
            if (length > GROUP_NAME_MAX_LENGTH - keepStrCharLen || characterPostion == 10
                    || characterPostion == 32) {
                // delete the redundant text.
                editable.delete(position, editable.length() - keepStr.length());
                mGroupSettings.setTextKeepState(editable);
                break;
            }
        }
    }

    /**
     * A character beyond 0xff is twice as big as a character within 0xff in
     * width when showing.
     */
    private int getCharacterVisualLength(String sequence, int index) {
        int codePointAt = Character.codePointAt(sequence, index);
        if (codePointAt >= 0x00 && codePointAt <= 0xFF) {
            return 1;
        } else {
            return 2;
        }
    }

}