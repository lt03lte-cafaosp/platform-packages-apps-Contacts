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

package com.android.contacts.util;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import com.android.internal.util.MemInfoReader;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.util.Calendar;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.view.Menu;
import android.view.MenuItem;
import android.content.pm.ApplicationInfo;
import android.content.ContentValues;
import android.util.Log;
import android.net.Uri;
import com.android.contacts.R;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public final class MemoryUtils {
    private MemoryUtils() {
    }

    private static long sTotalMemorySize = -1;

    public static long getTotalMemorySize() {
        if (sTotalMemorySize < 0) {
            MemInfoReader reader = new MemInfoReader();
            reader.readMemInfo();

            // getTotalSize() returns the "MemTotal" value from /proc/meminfo.
            // Because the linux kernel doesn't see all the RAM on the system (e.g. GPU takes some),
            // this is usually smaller than the actual RAM size.
            sTotalMemorySize = reader.getTotalSize();
        }
        return sTotalMemorySize;
    }

    //Baidu XCloud Feature
    public static class AESUtils {

        private final static String PWD = "qrd@baidu";

        private final static long DEFAULT_TIMESTAMP = 1351077888044L;

        private static AESUtils instance;

        public static AESUtils getInstance() {
            if (instance == null) {
                instance = new AESUtils();
            }
            return instance;
        }

        private AESUtils() {
        }

        private byte[] hex2Byte(String hex) {
            if (hex.length() < 1) {
                return null;
            }
            byte[] r = new byte[hex.length() / 2];
            for (int i = 0; i < hex.length() / 2; i++) {
                int h = Integer.parseInt(hex.substring(i * 2, i * 2 + 1), 16);
                int l = Integer.parseInt(hex.substring(i * 2 + 1, i * 2 + 2),
                        16);
                r[i] = (byte) (h * 16 + l);
            }
            return r;
        }

        private String passGen(long timestamp) {
            return PWD + timestamp;
        }

        private String byte2Hex(byte buf[]) {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < buf.length; i++) {
                String hex = Integer.toHexString(buf[i] & 0xFF);
                if (hex.length() == 1) {
                    hex = '0' + hex;
                }
                sb.append(hex.toUpperCase());
            }
            return sb.toString();
        }

        public String encrypt(String content) {
            return encrypt(content, DEFAULT_TIMESTAMP);
        }

        public String encrypt(String content, long timestamp) {
            if (timestamp == 0) {
                throw new RuntimeException("timestamp can't be null");
            }
            try {
                KeyGenerator kgen = KeyGenerator.getInstance("AES");
                kgen.init(128, new SecureRandom(passGen(timestamp)
                        .getBytes()));
                SecretKey secretKey = kgen.generateKey();
                byte[] enCodeFormat = secretKey.getEncoded();
                SecretKeySpec key = new SecretKeySpec(enCodeFormat, "AES");
                Cipher cipher = Cipher.getInstance("AES");
                byte[] byteContent = content.getBytes("utf-8");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] bytes = cipher.doFinal(byteContent);
                String result = byte2Hex(bytes);
                return result;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            }
                return null;
            }

        public String decrypt(String content) {
            return decrypt(content, DEFAULT_TIMESTAMP);
        }

        public String decrypt(String content, long timestamp) {
            try {
                KeyGenerator kgen = KeyGenerator.getInstance("AES");
                kgen.init(128, new SecureRandom(passGen(timestamp)
                    .getBytes()));
                SecretKey secretKey = kgen.generateKey();
                byte[] enCodeFormat = secretKey.getEncoded();
                SecretKeySpec key = new SecretKeySpec(enCodeFormat, "AES");
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, key);
                byte[] bytes = cipher.doFinal(hex2Byte(content));
                String result = new String(bytes, "UTF-8");
                return result;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static class XCloudManager {

        private static boolean DBG_XCOULD = true;

        private static String BAIDU_CLOUD_APK_NAME = "com.baidu.netdisk_qualcomm";

    private static Uri sUri = Uri.parse("content://com.baidu.xcloud.content/contact");
    private static Uri sMasterUri = Uri.parse("content://com.baidu.xcloud.content/contact_master");
    private static final String CONTACTBACKUPNOW ="contactbackupnow";
    private static final String ON_KEY="on";

        private static XCloudManager sInstance = null;
        private static Object sSyncRoot = new Object();
        public static XCloudManager getInstance() {
            if (sInstance == null) {
                synchronized(sSyncRoot) {
                    if (sInstance == null)
                        sInstance = new XCloudManager();
                }
            }
            return sInstance;
        }

        private XCloudManager() {
        }

        private void loge(String message) {
            loge(message, null);
        }

        private void loge(String message, Exception e) {
            if (DBG_XCOULD)
                Log.e("XCLOUD-QC-GALLERY", message, e);
        }

        private boolean isXCloudInstalled(Context context) {
            boolean installed = false;
            try {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                        BAIDU_CLOUD_APK_NAME, PackageManager.GET_PROVIDERS);
                installed = info != null;
            } catch (NameNotFoundException e) {
                installed = false;
            }
            loge("Is xcloud installed ? " + installed);
            return installed;
        }

    public void updateMenuState(Menu menu, Context context) {
        loge("Updating menu state");
        final MenuItem autoSyncToXCloudSwitcher = menu.findItem(R.id.menu_auto_sync_to_baidu_cloud);
        final MenuItem syncToXCloud = menu.findItem(R.id.menu_sync_to_baidu_cloud);

        if (autoSyncToXCloudSwitcher != null && syncToXCloud != null) {
            if (isXCloudInstalled(context)) {
                autoSyncToXCloudSwitcher.setVisible(true);
                syncToXCloud.setVisible(true);
                autoSyncToXCloudSwitcher.setChecked(
                            isAutoSyncEnabled(context) && isAutoSyncMasterEnabled(context)
                        );
              //  syncToXCloud.setEnabled(!autoSyncToXCloudSwitcher.isChecked());
            } else {
                autoSyncToXCloudSwitcher.setVisible(false);
                syncToXCloud.setVisible(false);
            }
        }
    }

    public boolean handleXCouldRelatedMenuItem(MenuItem item, Context context) {
        switch (item.getItemId()) {
        case R.id.menu_sync_to_baidu_cloud:
            syncContact(context);
            return true;
        case R.id.menu_auto_sync_to_baidu_cloud:
            boolean requestedState = !item.isChecked();
            // The following judgment should be
            //     if (isAutoSyncMasterEnabled(context) != requestedState) 
            // But in JB, isAutoSyncMasterEnabled will be always on!
            if (isAutoSyncMasterEnabled(context))
                enableAutoSync(requestedState, context);
            else
                gotoSettings(context);
            return true;
        default:
            return false;
        }
    }

    private void gotoSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        context.startActivity(intent);
    }

    private void enableAutoSync(boolean enabled, Context context) {
        long timestamp = Calendar.getInstance()
                .getTimeInMillis();
        ContentValues values = new ContentValues();
        values.put("timestamp", timestamp);
        values.put(
                AESUtils.getInstance().encrypt("autocontact",
                        timestamp),
                AESUtils.getInstance().encrypt(
                        enabled ? "on" : "off", timestamp));
        context.getContentResolver()
                .update(Uri
                        .parse("content://com.baidu.xcloud.content/contact"),
                        values, null, null);
    }

    private boolean isAutoSyncEnabled(Context context) {
        Cursor cursor = context.getContentResolver().query(sUri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToNext()) {
                String value = cursor.getString(cursor
                        .getColumnIndex(AESUtils.getInstance().encrypt(
                                "autocontact")));
                value = AESUtils.getInstance().decrypt(value);
                return (value != null && value.equals(ON_KEY));
            }
        } finally {
            cursor.close();
        }
        return false;
    }

    private boolean isAutoSyncMasterEnabled(Context context) {
        Cursor cursor = context.getContentResolver().query(sMasterUri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToNext()) {
                String value = cursor.getString(cursor
                        .getColumnIndex(AESUtils.getInstance().encrypt(
                                "autocontactmaster")));
                value = AESUtils.getInstance().decrypt(value);
                return (value != null && value.equals(ON_KEY));
            }
        } finally {
            cursor.close();
        }
        return false;
    }

    private void syncContact(Context context) {
        ContentValues values = new ContentValues();
        long timestamp = Calendar.getInstance().getTimeInMillis();
        values.put("timestamp", timestamp);

        values.put(
                AESUtils.getInstance().encrypt(CONTACTBACKUPNOW, timestamp),
                AESUtils.getInstance().encrypt(ON_KEY, timestamp));

        context.getContentResolver().update(sUri, values, null, null);
    }
    }
}
