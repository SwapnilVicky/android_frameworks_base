/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal;


import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.MathUtils;

import java.util.LinkedList;
import java.util.Queue;

/**
 * BrightnessSynchronizer helps convert between the int (old) system and float
 * (new) system for storing the brightness. It has methods to convert between the two and also
 * observes for when one of the settings is changed and syncs this with the other.
 */
public class BrightnessSynchronizer{

    private static final int MSG_UPDATE_FLOAT = 1;
    private static final int MSG_UPDATE_INT = 2;

    private static final String TAG = "BrightnessSynchronizer";
    private static final Uri BRIGHTNESS_URI =
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS);
    private static final Uri BRIGHTNESS_FLOAT_URI =
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_FLOAT);

    // The tolerance within which we consider brightness values approximately equal to eachother.
    // This value is approximately 1/3 of the smallest possible brightness value.
    public static final float EPSILON = 0.001f;

    private final Context mContext;

    private final Queue<Object> mWriteHistory = new LinkedList<>();

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_FLOAT:
                    updateBrightnessFloatFromInt(msg.arg1);
                    break;
                case MSG_UPDATE_INT:
                    updateBrightnessIntFromFloat(Float.intBitsToFloat(msg.arg1));
                    break;
                default:
                    super.handleMessage(msg);
            }

        }
    };


    public BrightnessSynchronizer(Context context) {
        final BrightnessSyncObserver mBrightnessSyncObserver;
        mContext = context;
        mBrightnessSyncObserver = new BrightnessSyncObserver(mHandler);
        mBrightnessSyncObserver.startObserving();
    }

    /**
     * Converts between the int brightness system and the float brightness system.
     */
    public static float brightnessIntToFloat(int brightnessInt) {
        if (brightnessInt == PowerManager.BRIGHTNESS_OFF) {
            return PowerManager.BRIGHTNESS_OFF_FLOAT;
        } else if (brightnessInt == PowerManager.BRIGHTNESS_INVALID) {
            return PowerManager.BRIGHTNESS_INVALID_FLOAT;
        } else {
            final float minFloat = PowerManager.BRIGHTNESS_MIN;
            final float maxFloat = PowerManager.BRIGHTNESS_MAX;
            final float minInt = PowerManager.BRIGHTNESS_OFF + 1;
            final float maxInt = PowerManager.BRIGHTNESS_ON;
            return MathUtils.constrainedMap(minFloat, maxFloat, minInt, maxInt, brightnessInt);
        }
    }

    /**
     * Converts between the float brightness system and the int brightness system.
     */
    public static int brightnessFloatToInt(float brightnessFloat) {
        return Math.round(brightnessFloatToIntRange(brightnessFloat));
    }

    /**
     * Translates specified value from the float brightness system to the int brightness system,
     * given the min/max of each range.  Accounts for special values such as OFF and invalid values.
     * Value returned as a float privimite (to preserve precision), but is a value within the
     * int-system range.
     */
    public static float brightnessFloatToIntRange(float brightnessFloat) {
        if (floatEquals(brightnessFloat, PowerManager.BRIGHTNESS_OFF_FLOAT)) {
            return PowerManager.BRIGHTNESS_OFF;
        } else if (Float.isNaN(brightnessFloat)) {
            return PowerManager.BRIGHTNESS_INVALID;
        } else {
            final float minFloat = PowerManager.BRIGHTNESS_MIN;
            final float maxFloat = PowerManager.BRIGHTNESS_MAX;
            final float minInt = PowerManager.BRIGHTNESS_OFF + 1;
            final float maxInt = PowerManager.BRIGHTNESS_ON;
            return MathUtils.constrainedMap(minInt, maxInt, minFloat, maxFloat, brightnessFloat);
        }
    }

    private static float getScreenBrightnessFloat(Context context) {
        return Settings.System.getFloatForUser(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_FLOAT, PowerManager.BRIGHTNESS_INVALID_FLOAT,
                UserHandle.USER_CURRENT);
    }

    private static int getScreenBrightnessInt(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 0, UserHandle.USER_CURRENT);
    }

    private float mPreferredSettingValue;

    /**
     * Updates the float setting based on a passed in int value. This is called whenever the int
     * setting changes. mWriteHistory keeps a record of the values that been written to the settings
     * from either this method or updateBrightnessIntFromFloat. This is to ensure that the value
     * being set is due to an external value being set, rather than the updateBrightness* methods.
     * The intention of this is to avoid race conditions when the setting is being changed
     * frequently and to ensure we are not reacting to settings changes from this file.
     * @param value Brightness value as int to store in the float setting.
     */
    private void updateBrightnessFloatFromInt(int value) {
        Object topOfQueue = mWriteHistory.peek();
        if (topOfQueue != null && topOfQueue.equals(value)) {
            mWriteHistory.poll();
        } else {
            if (brightnessFloatToInt(mPreferredSettingValue) == value) {
                return;
            }
            float newBrightnessFloat = brightnessIntToFloat(value);
            mWriteHistory.offer(newBrightnessFloat);
            mPreferredSettingValue = newBrightnessFloat;
            Settings.System.putFloatForUser(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_FLOAT, newBrightnessFloat,
                    UserHandle.USER_CURRENT);
        }
    }

    /**
     * Updates the int setting based on a passed in float value. This is called whenever the float
     * setting changes. mWriteHistory keeps a record of the values that been written to the settings
     * from either this method or updateBrightnessFloatFromInt. This is to ensure that the value
     * being set is due to an external value being set, rather than the updateBrightness* methods.
     * The intention of this is to avoid race conditions when the setting is being changed
     * frequently and to ensure we are not reacting to settings changes from this file.
     * @param value Brightness setting as float to store in int setting.
     */
    private void updateBrightnessIntFromFloat(float value) {
        int newBrightnessInt = brightnessFloatToInt(value);
        Object topOfQueue = mWriteHistory.peek();
        if (topOfQueue != null && topOfQueue.equals(value)) {
            mWriteHistory.poll();
        } else {
            mWriteHistory.offer(newBrightnessInt);
            mPreferredSettingValue = value;
            Settings.System.putIntForUser(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, newBrightnessInt, UserHandle.USER_CURRENT);
        }
    }

    /**
     * Tests whether two brightness float values are within a small enough tolerance
     * of each other.
     * @param a first float to compare
     * @param b second float to compare
     * @return whether the two values are within a small enough tolerance value
     */
    public static boolean floatEquals(float a, float b) {
        if (a == b) {
            return true;
        } else if (Float.isNaN(a) && Float.isNaN(b)) {
            return true;
        } else if (Math.abs(a - b) < EPSILON) {
            return true;
        } else {
            return false;
        }
    }

    private class BrightnessSyncObserver extends ContentObserver {
        /**
         * Creates a content observer.
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        BrightnessSyncObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (selfChange) {
                return;
            }
            if (BRIGHTNESS_URI.equals(uri)) {
                int currentBrightness = getScreenBrightnessInt(mContext);
                mHandler.obtainMessage(MSG_UPDATE_FLOAT, currentBrightness, 0).sendToTarget();
            } else if (BRIGHTNESS_FLOAT_URI.equals(uri)) {
                float currentFloat = getScreenBrightnessFloat(mContext);
                int toSend = Float.floatToIntBits(currentFloat);
                mHandler.obtainMessage(MSG_UPDATE_INT, toSend, 0).sendToTarget();
            }
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.unregisterContentObserver(this);
            cr.registerContentObserver(BRIGHTNESS_URI, false, this, UserHandle.USER_ALL);
            cr.registerContentObserver(BRIGHTNESS_FLOAT_URI, false, this, UserHandle.USER_ALL);
        }

        public void stopObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.unregisterContentObserver(this);
        }
    }
}
