/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settingslib.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;

import com.android.settingslib.SettingsLibRobolectricTestRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

@RunWith(SettingsLibRobolectricTestRunner.class)
public class LocalBluetoothProfileManagerTest {
    @Mock
    private CachedBluetoothDeviceManager mDeviceManager;
    @Mock
    private BluetoothEventManager mEventManager;
    @Mock
    private LocalBluetoothAdapter mAdapter;
    @Mock
    private BluetoothDevice mDevice;
    @Mock
    private CachedBluetoothDevice mCachedBluetoothDevice;

    private Context mContext;
    private LocalBluetoothProfileManager mProfileManager;
    private Intent mIntent;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(RuntimeEnvironment.application);
        mEventManager = spy(new BluetoothEventManager(mAdapter,
                mDeviceManager, mContext));
        when(mAdapter.getBluetoothState()).thenReturn(BluetoothAdapter.STATE_ON);
        when(mDeviceManager.findDevice(mDevice)).thenReturn(mCachedBluetoothDevice);
    }

    /**
     * Verify HID and HID Device profiles are not null without running updateUuids()
     */
    @Test
    public void constructor_initiateHidAndHidDeviceProfile() {
        when(mAdapter.getSupportedProfiles()).thenReturn(
                generateList(new int[] {BluetoothProfile.HID_HOST}));
        when(mAdapter.getSupportedProfiles()).thenReturn(
                generateList(new int[] {BluetoothProfile.HID_HOST, BluetoothProfile.HID_DEVICE}));
        mProfileManager =
                new LocalBluetoothProfileManager(mContext, mAdapter, mDeviceManager, mEventManager);

        assertThat(mProfileManager.getHidProfile()).isNotNull();
        assertThat(mProfileManager.getHidDeviceProfile()).isNotNull();
    }

    /**
     * Verify updateLocalProfiles() for a local A2DP source adds A2dpProfile
     */
    @Test
    public void updateLocalProfiles_addA2dpToLocalProfiles() {
        mProfileManager =
                new LocalBluetoothProfileManager(mContext, mAdapter, mDeviceManager, mEventManager);
        assertThat(mProfileManager.getA2dpProfile()).isNull();
        assertThat(mProfileManager.getHeadsetProfile()).isNull();

        when(mAdapter.getSupportedProfiles()).thenReturn(generateList(
                new int[] {BluetoothProfile.A2DP}));
        mProfileManager.updateLocalProfiles();

        assertThat(mProfileManager.getA2dpProfile()).isNotNull();
        assertThat(mProfileManager.getHeadsetProfile()).isNull();
    }

    /**
     * Verify updateProfiles() for a remote HID device updates profiles and removedProfiles
     */
    @Test
    public void updateProfiles_addHidProfileForRemoteDevice() {
        when(mAdapter.getSupportedProfiles()).thenReturn(generateList(
                new int[] {BluetoothProfile.HID_HOST}));
        mProfileManager =
                new LocalBluetoothProfileManager(mContext, mAdapter, mDeviceManager, mEventManager);
        ParcelUuid[] uuids = new ParcelUuid[]{BluetoothUuid.Hid};
        ParcelUuid[] localUuids = new ParcelUuid[]{};
        List<LocalBluetoothProfile> profiles = new ArrayList<>();
        List<LocalBluetoothProfile> removedProfiles = new ArrayList<>();

        mProfileManager.updateProfiles(uuids, localUuids, profiles, removedProfiles, false,
                mDevice);

        assertThat(mProfileManager.getHidProfile()).isNotNull();
        assertThat(profiles.contains(mProfileManager.getHidProfile())).isTrue();
        assertThat(removedProfiles.contains(mProfileManager.getHidProfile())).isFalse();
    }

    /**
     * Verify BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED with uuid intent will dispatch to
     * profile connection state changed callback
     */
    @Test
    public void stateChangedHandler_receiveA2dpConnectionStateChanged_shouldDispatchCallback() {
        when(mAdapter.getSupportedProfiles()).thenReturn(generateList(
                new int[] {BluetoothProfile.A2DP}));
        mProfileManager = new LocalBluetoothProfileManager(mContext, mAdapter, mDeviceManager,
                mEventManager);
        // Refer to BluetoothControllerImpl, it will call setReceiverHandler after
        // LocalBluetoothProfileManager created.
        mEventManager.setReceiverHandler(null);
        mIntent = new Intent(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        mIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        mIntent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTING);
        mIntent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED);

        mContext.sendBroadcast(mIntent);

        verify(mEventManager).dispatchProfileConnectionStateChanged(
                mCachedBluetoothDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.A2DP);
    }

    /**
     * Verify BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED with uuid intent will dispatch to
     * profile connection state changed callback
     */
    @Test
    public void stateChangedHandler_receiveHeadsetConnectionStateChanged_shouldDispatchCallback() {
        when(mAdapter.getSupportedProfiles()).thenReturn(generateList(
                new int[] {BluetoothProfile.HEADSET}));
        mProfileManager = new LocalBluetoothProfileManager(mContext, mAdapter, mDeviceManager,
                mEventManager);
        // Refer to BluetoothControllerImpl, it will call setReceiverHandler after
        // LocalBluetoothProfileManager created.
        mEventManager.setReceiverHandler(null);
        mIntent = new Intent(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        mIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        mIntent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTING);
        mIntent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED);

        mContext.sendBroadcast(mIntent);

        verify(mEventManager).dispatchProfileConnectionStateChanged(mCachedBluetoothDevice,
                BluetoothProfile.STATE_CONNECTED, BluetoothProfile.HEADSET);
    }

    /**
     * Verify BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED with uuid intent will dispatch to
     * profile connection state changed callback
     */
    @Test
    public void stateChangedHandler_receiveHAPConnectionStateChanged_shouldDispatchCallback() {
        ArrayList<Integer> supportProfiles = new ArrayList<>();
        supportProfiles.add(BluetoothProfile.HEARING_AID);
        when(mAdapter.getSupportedProfiles()).thenReturn(supportProfiles);
        when(mAdapter.getUuids()).thenReturn(new ParcelUuid[]{BluetoothUuid.HearingAid});
        mProfileManager = new LocalBluetoothProfileManager(mContext, mAdapter, mDeviceManager,
                mEventManager);
        // Refer to BluetoothControllerImpl, it will call setReceiverHandler after
        // LocalBluetoothProfileManager created.
        mEventManager.setReceiverHandler(null);
        mIntent = new Intent(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED);
        mIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        mIntent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTING);
        mIntent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED);

        mContext.sendBroadcast(mIntent);

        verify(mEventManager).dispatchProfileConnectionStateChanged(mCachedBluetoothDevice,
                BluetoothProfile.STATE_CONNECTED, BluetoothProfile.HEARING_AID);
    }

    /**
     * Verify BluetoothPan.ACTION_CONNECTION_STATE_CHANGED intent with uuid will dispatch to
     * profile connection state changed callback
     */
    @Test
    public void stateChangedHandler_receivePanConnectionStateChanged_shouldNotDispatchCallback() {
        when(mAdapter.getSupportedProfiles()).thenReturn(
                generateList(new int[] {BluetoothProfile.PAN}));
        mProfileManager = new LocalBluetoothProfileManager(mContext, mAdapter, mDeviceManager,
                mEventManager);
        // Refer to BluetoothControllerImpl, it will call setReceiverHandler after
        // LocalBluetoothProfileManager created.
        mEventManager.setReceiverHandler(null);
        mIntent = new Intent(BluetoothPan.ACTION_CONNECTION_STATE_CHANGED);
        mIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        mIntent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTING);
        mIntent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED);

        mContext.sendBroadcast(mIntent);

        verify(mEventManager).dispatchProfileConnectionStateChanged(
                any(CachedBluetoothDevice.class), anyInt(), anyInt());
    }

    /**
     * Verify BluetoothPan.ACTION_CONNECTION_STATE_CHANGED intent without uuids will not dispatch to
     * handler and refresh CachedBluetoothDevice
     */
    @Test
    public void stateChangedHandler_receivePanConnectionStateChangedWithoutProfile_shouldNotRefresh
    () {
        when(mAdapter.getSupportedProfiles()).thenReturn(null);
        mProfileManager = new LocalBluetoothProfileManager(mContext, mAdapter, mDeviceManager,
                mEventManager);
        // Refer to BluetoothControllerImpl, it will call setReceiverHandler after
        // LocalBluetoothProfileManager created.
        mEventManager.setReceiverHandler(null);
        mIntent = new Intent(BluetoothPan.ACTION_CONNECTION_STATE_CHANGED);
        mIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        mIntent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTING);
        mIntent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED);

        mContext.sendBroadcast(mIntent);

        verify(mCachedBluetoothDevice, never()).refresh();
    }

    /**
     * Verify BluetoothPan.ACTION_CONNECTION_STATE_CHANGED intent with uuids will dispatch to
     * handler and refresh CachedBluetoothDevice
     */
    @Test
    public void stateChangedHandler_receivePanConnectionStateChangedWithProfile_shouldRefresh() {
        when(mAdapter.getSupportedProfiles()).thenReturn(generateList(
                new int[] {BluetoothProfile.PAN}));
        mProfileManager = new LocalBluetoothProfileManager(mContext, mAdapter, mDeviceManager,
                mEventManager);
        // Refer to BluetoothControllerImpl, it will call setReceiverHandler after
        // LocalBluetoothProfileManager created.
        mEventManager.setReceiverHandler(null);
        mIntent = new Intent(BluetoothPan.ACTION_CONNECTION_STATE_CHANGED);
        mIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        mIntent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTING);
        mIntent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED);

        mContext.sendBroadcast(mIntent);

        verify(mCachedBluetoothDevice).refresh();
    }

    private List<Integer> generateList(int[] profile) {
        if (profile == null) {
            return null;
        }
        final List<Integer> profileList = new ArrayList<>(profile.length);
        for(int i = 0; i < profile.length; i++) {
            profileList.add(profile[i]);
        }
        return profileList;
    }
}
