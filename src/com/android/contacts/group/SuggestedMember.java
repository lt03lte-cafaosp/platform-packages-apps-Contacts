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

/**
 * This represents a single contact that is a suggestion for the user to add to a group.
 */
public class SuggestedMember {

        private long mRawContactId;
        private long mContactId;
        private String mDisplayName;
        private byte[] mPhoto;

        public SuggestedMember(long rawContactId, String displayName, long contactId) {
            mRawContactId = rawContactId;
            mDisplayName = displayName;
            mContactId = contactId;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public long getRawContactId() {
            return mRawContactId;
        }

        public long getContactId() {
            return mContactId;
        }

        public byte[] getPhotoByteArray() {
            return mPhoto;
        }


        public void setPhotoByteArray(byte[] photo) {
            mPhoto = photo;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }