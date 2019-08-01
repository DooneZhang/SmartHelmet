package com.datang.smarthelmet.settings;

/*
CallSettingsFragment.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import com.datang.smarthelmet.R;
import com.datang.smarthelmet.SmartHelmetManager;
import com.datang.smarthelmet.settings.widget.BasicSetting;
import com.datang.smarthelmet.settings.widget.ListSetting;
import com.datang.smarthelmet.settings.widget.SeekBarSetting;
import com.datang.smarthelmet.settings.widget.SettingListenerBase;
import com.datang.smarthelmet.settings.widget.SwitchSetting;
import java.util.ArrayList;
import java.util.List;

public class TTSSettingsFragment extends SettingsFragment {
    private View mRootView;
    private LinphonePreferences mPrefs;

    private SwitchSetting mTTS_Enable;
    private ListSetting mTTS_EffectLauguage, mTTS_EffectSex;
    private SeekBarSetting mTTS_Effect_Tone, mTTS_Effect_Volume;
    private BasicSetting mDndPermissionSettings, mTTS_Effect_Preview;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.settings_tts, container, false);

        loadSettings();

        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        mPrefs = LinphonePreferences.instance();

        updateValues();
    }

    private void loadSettings() {

        mTTS_Enable = mRootView.findViewById(R.id.pref_enable_tts);

        mTTS_EffectLauguage = mRootView.findViewById(R.id.pref_tts_effect_laugeage);

        mTTS_EffectSex = mRootView.findViewById(R.id.pref_tts_effect_sex);

        mTTS_Effect_Tone = mRootView.findViewById(R.id.pref_tts_effect_tone);

        mTTS_Effect_Volume = mRootView.findViewById(R.id.pref_tts_effect_volume);

        mTTS_Effect_Preview = mRootView.findViewById(R.id.pref_tts_effect_preview);
    }

    private void setListeners() {
        mTTS_Enable.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.set
                                setEchoCancellation(newValue);

                    }
                });

        mTTS_Effect_Preview.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enableIncomingCallVibration(newValue);
                    }
                });

        mTTS_Effect_Tone.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (newValue) mDtmfRfc2833.setChecked(false);
                        mPrefs.sendDTMFsAsSipInfo(newValue);
                    }
                });

        mTTS_Effect_Volume.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (newValue) mDtmfSipInfo.setChecked(false);
                        mPrefs.sendDtmfsAsRfc2833(newValue);
                    }
                });

        mTTS_EffectLauguage.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enableAutoAnswer(newValue);
                        mAutoAnswerTime.setVisibility(
                                mPrefs.isAutoAnswerEnabled() ? View.VISIBLE : View.GONE);
                    }
                });

        mTTS_EffectSex.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onListValueChanged(int position, String newLabel, String newValue) {
                        try {
                            mPrefs.setMediaEncryption(
                                    MediaEncryption.fromInt(Integer.parseInt(newValue)));
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });

        mDndPermissionSettings.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {
                        startActivity(
                                new Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"));
                    }
                });
    }

    private void updateValues() {

        mTTS_EffectSex.setValue(mPrefs.getMediaEncryption().toInt());

        mTTS_EffectLauguage.setValue(mPrefs.getAutoAnswerTime());
        mTTS_Effect_Volume.setVisibility(mPrefs.isAutoAnswerEnabled() ? View.VISIBLE : View.GONE);

        mTTS_Effect_Tone.setValue(mPrefs.getIncTimeout());

        mDndPermissionSettings.setVisibility(
                Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60) ? View.VISIBLE : View.GONE);

        mMediaEncryptionMandatory.setChecked(mPrefs.acceptMediaEncryptionMandatory());

        setListeners();
    }

    private void initMediaEncryptionList() {
        List<String> entries = new ArrayList<>();
        List<String> values = new ArrayList<>();

        entries.add(getString(R.string.pref_none));
        values.add(String.valueOf(MediaEncryption.None.toInt()));

        Core core = SmartHelmetManager.getCore();
        if (core != null
                && !getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
            boolean hasZrtp = core.mediaEncryptionSupported(MediaEncryption.ZRTP);
            boolean hasSrtp = core.mediaEncryptionSupported(MediaEncryption.SRTP);
            boolean hasDtls = core.mediaEncryptionSupported(MediaEncryption.DTLS);

            if (!hasSrtp && !hasZrtp && !hasDtls) {
                mMediaEncryption.setEnabled(false);
            } else {
                if (hasSrtp) {
                    entries.add("SRTP");
                    values.add(String.valueOf(MediaEncryption.SRTP.toInt()));
                }
                if (hasZrtp) {
                    entries.add("ZRTP");
                    values.add(String.valueOf(MediaEncryption.ZRTP.toInt()));
                }
                if (hasDtls) {
                    entries.add("DTLS");
                    values.add(String.valueOf(MediaEncryption.DTLS.toInt()));
                }
            }
        }

        mMediaEncryption.setItems(entries, values);
    }
}
