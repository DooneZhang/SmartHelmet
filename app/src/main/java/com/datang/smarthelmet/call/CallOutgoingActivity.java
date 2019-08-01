package com.datang.smarthelmet.call;

/*
CallOutgoingActivity.java
Copyright (C) 2017 Belledonne Communications, Grenoble, France

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
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import com.datang.smarthelmet.R;
import com.datang.smarthelmet.SmartHelmetManager;
import com.datang.smarthelmet.SmartHelmetService;
import com.datang.smarthelmet.activities.SmartHelmetGenericActivity;
import com.datang.smarthelmet.contacts.ContactsManager;
import com.datang.smarthelmet.contacts.SmartHelmetContact;
import com.datang.smarthelmet.settings.LinphonePreferences;
import com.datang.smarthelmet.utils.SmartHelmetUtils;
import com.datang.smarthelmet.views.ContactAvatar;
import java.util.ArrayList;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Reason;
import org.linphone.core.tools.Log;

public class CallOutgoingActivity extends SmartHelmetGenericActivity implements OnClickListener {
    private TextView mName, mNumber;
    private ImageView mMicro;
    private ImageView mSpeaker;
    private Call mCall;
    private CoreListenerStub mListener;
    private boolean mIsMicMuted, mIsSpeakerEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mAbortCreation) {
            return;
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.call_outgoing);

        mName = findViewById(R.id.contact_name);
        mNumber = findViewById(R.id.contact_number);

        mIsMicMuted = false;
        mIsSpeakerEnabled = false;

        mMicro = findViewById(R.id.micro);
        mMicro.setOnClickListener(this);
        mSpeaker = findViewById(R.id.speaker);
        mSpeaker.setOnClickListener(this);

        ImageView hangUp = findViewById(R.id.outgoing_hang_up);
        hangUp.setOnClickListener(this);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (state == State.Error) {
                            // Convert Core message for internalization
                            if (call.getErrorInfo().getReason() == Reason.Declined) {
                                Toast.makeText(
                                                CallOutgoingActivity.this,
                                                getString(R.string.error_call_declined),
                                                Toast.LENGTH_SHORT)
                                        .show();
                            } else if (call.getErrorInfo().getReason() == Reason.NotFound) {
                                Toast.makeText(
                                                CallOutgoingActivity.this,
                                                getString(R.string.error_user_not_found),
                                                Toast.LENGTH_SHORT)
                                        .show();
                            } else if (call.getErrorInfo().getReason() == Reason.NotAcceptable) {
                                Toast.makeText(
                                                CallOutgoingActivity.this,
                                                getString(R.string.error_incompatible_media),
                                                Toast.LENGTH_SHORT)
                                        .show();
                            } else if (call.getErrorInfo().getReason() == Reason.Busy) {
                                Toast.makeText(
                                                CallOutgoingActivity.this,
                                                getString(R.string.error_user_busy),
                                                Toast.LENGTH_SHORT)
                                        .show();
                            } else if (message != null) {
                                Toast.makeText(
                                                CallOutgoingActivity.this,
                                                getString(R.string.error_unknown) + " - " + message,
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }
                        } else if (state == State.End) {
                            // Convert Core message for internalization
                            if (call.getErrorInfo().getReason() == Reason.Declined) {
                                Toast.makeText(
                                                CallOutgoingActivity.this,
                                                getString(R.string.error_call_declined),
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }
                        } else if (state == State.Connected) {
                            startActivity(
                                    new Intent(CallOutgoingActivity.this, CallActivity.class));
                        }

                        if (SmartHelmetManager.getCore().getCallsNb() == 0) {
                            finish();
                        }
                    }
                };
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkAndRequestCallPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Core core = SmartHelmetManager.getCore();
        if (core != null) {
            core.addListener(mListener);
        }

        mCall = null;

        // Only one call ringing at a time is allowed
        if (SmartHelmetManager.getCore() != null) {
            for (Call call : SmartHelmetManager.getCore().getCalls()) {
                State cstate = call.getState();
                if (State.OutgoingInit == cstate
                        || State.OutgoingProgress == cstate
                        || State.OutgoingRinging == cstate
                        || State.OutgoingEarlyMedia == cstate) {
                    mCall = call;
                    break;
                }
            }
        }
        if (mCall == null) {
            Log.e("Couldn't find outgoing call");
            finish();
            return;
        }

        Address address = mCall.getRemoteAddress();
        SmartHelmetContact contact = ContactsManager.getInstance().findContactFromAddress(address);
        if (contact != null) {
            ContactAvatar.displayAvatar(contact, findViewById(R.id.avatar_layout), true);
            mName.setText(contact.getFullName());
        } else {
            String displayName = SmartHelmetUtils.getAddressDisplayName(address);
            ContactAvatar.displayAvatar(displayName, findViewById(R.id.avatar_layout), true);
            mName.setText(displayName);
        }
        mNumber.setText(SmartHelmetUtils.getDisplayableAddress(address));
    }

    @Override
    protected void onPause() {
        Core core = SmartHelmetManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mName = null;
        mNumber = null;
        mMicro = null;
        mSpeaker = null;
        mCall = null;
        mListener = null;

        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.micro) {
            mIsMicMuted = !mIsMicMuted;
            mMicro.setSelected(mIsMicMuted);
            SmartHelmetManager.getCore().enableMic(!mIsMicMuted);
        }
        if (id == R.id.speaker) {
            mIsSpeakerEnabled = !mIsSpeakerEnabled;
            mSpeaker.setSelected(mIsSpeakerEnabled);
            if (mIsSpeakerEnabled) {
                SmartHelmetManager.getAudioManager().routeAudioToSpeaker();
            } else {
                SmartHelmetManager.getAudioManager().routeAudioToEarPiece();
            }
        }
        if (id == R.id.outgoing_hang_up) {
            decline();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (SmartHelmetService.isReady()
                && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)) {
            mCall.terminate();
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void decline() {
        mCall.terminate();
        finish();
    }

    private void checkAndRequestCallPermissions() {
        ArrayList<String> permissionsList = new ArrayList<>();

        int recordAudio =
                getPackageManager()
                        .checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        Log.i(
                "[Permission] Record audio permission is "
                        + (recordAudio == PackageManager.PERMISSION_GRANTED
                                ? "granted"
                                : "denied"));
        int camera =
                getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
        Log.i(
                "[Permission] Camera permission is "
                        + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        int readPhoneState =
                getPackageManager()
                        .checkPermission(Manifest.permission.READ_PHONE_STATE, getPackageName());
        Log.i(
                "[Permission] Read phone state permission is "
                        + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (recordAudio != PackageManager.PERMISSION_GRANTED) {
            Log.i("[Permission] Asking for record audio");
            permissionsList.add(Manifest.permission.RECORD_AUDIO);
        }
        if (readPhoneState != PackageManager.PERMISSION_GRANTED) {
            Log.i("[Permission] Asking for read phone state");
            permissionsList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (LinphonePreferences.instance().shouldInitiateVideoCall()
                || LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests()) {
            if (camera != PackageManager.PERMISSION_GRANTED) {
                Log.i("[Permission] Asking for camera");
                permissionsList.add(Manifest.permission.CAMERA);
            }
        }

        if (permissionsList.size() > 0) {
            String[] permissions = new String[permissionsList.size()];
            permissions = permissionsList.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            Log.i(
                    "[Permission] "
                            + permissions[i]
                            + " is "
                            + (grantResults[i] == PackageManager.PERMISSION_GRANTED
                                    ? "granted"
                                    : "denied"));
            if (permissions[i].equals(Manifest.permission.CAMERA)
                    && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                SmartHelmetUtils.reloadVideoDevices();
            }
        }
    }
}
