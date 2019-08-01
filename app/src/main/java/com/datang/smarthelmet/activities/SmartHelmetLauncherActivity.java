package com.datang.smarthelmet.activities;

/*
SmartHelmetLauncherActivity.java
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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import com.datang.smarthelmet.R;
import com.datang.smarthelmet.SmartHelmetManager;
import com.datang.smarthelmet.SmartHelmetService;
import com.datang.smarthelmet.assistant.GenericConnectionAssistantActivity;
import com.datang.smarthelmet.chat.ChatActivity;
import com.datang.smarthelmet.history.HistoryActivity;
import com.datang.smarthelmet.settings.LinphonePreferences;
import com.datang.smarthelmet.utils.NetWorkUtils;
import com.datang.smarthelmet.utils.SmartHelmetUtils;

/** Creates SmartHelmetService and wait until Core is ready to start main Activity */
public class SmartHelmetLauncherActivity extends Activity {

    private static final String TAG = "SmartHelmetLauncherActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        if (!getResources().getBoolean(R.bool.use_full_screen_image_splashscreen)) {
            setContentView(R.layout.launch_screen);
        } // Otherwise use drawable/launch_screen layer list up until first activity starts
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (SmartHelmetService.isReady()) {
            onServiceReady();
        } else {
            // 开启整个SmartHelmet服务进程
            startService(
                    new Intent()
                            .setClass(SmartHelmetLauncherActivity.this, SmartHelmetService.class));
            // 开启服务等待线程
            new ServiceWaitThread().start();
        }
    }

    private void onServiceReady() {

        // 检测网络状况
        if (NetWorkUtils.isNetworkAvailable(SmartHelmetLauncherActivity.this)) {
            SmartHelmetService.playTTS("来了老弟！");
        } else {
            SmartHelmetService.playTTS("老哥，没网啊！");
        }
        final Class<? extends Activity> classToStart;

        boolean useFirstLoginActivity =
                getResources().getBoolean(R.bool.display_account_assistant_at_first_start);
        if (useFirstLoginActivity && LinphonePreferences.instance().isFirstLaunch()) {
            // 把原来首次进入登陆菜单换成直接进入Sip登陆界面
            // classToStart = MenuAssistantActivity.class;
            classToStart = GenericConnectionAssistantActivity.class;
        } else {
            if (getIntent().getExtras() != null) {
                String activity = getIntent().getExtras().getString("Activity", null);
                if (ChatActivity.NAME.equals(activity)) {
                    classToStart = ChatActivity.class;
                } else if (HistoryActivity.NAME.equals(activity)) {
                    classToStart = HistoryActivity.class;
                } else {
                    classToStart = DialerActivity.class;
                }
            } else {
                classToStart = DialerActivity.class;
            }
        }

        // 检查升级
        if (getResources().getBoolean(R.bool.check_for_update_when_app_starts)) {
            SmartHelmetManager.getInstance().checkForUpdate();
        }

        SmartHelmetUtils.dispatchOnUIThreadAfter(
                new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent();
                        intent.setClass(SmartHelmetLauncherActivity.this, classToStart);
                        if (getIntent() != null && getIntent().getExtras() != null) {
                            intent.putExtras(getIntent().getExtras());
                        }
                        intent.setAction(getIntent().getAction());
                        intent.setType(getIntent().getType());
                        startActivity(intent);
                    }
                },
                100);

        SmartHelmetManager.getInstance().changeStatusToOnline();
    }

    private class ServiceWaitThread extends Thread {
        public void run() {
            // 如果服务没准备好就一直sleep30毫秒
            while (!SmartHelmetService.isReady()) {
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException("waiting thread sleep() has been interrupted");
                }
            }
            SmartHelmetUtils.dispatchOnUIThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            onServiceReady();
                        }
                    });
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }
}
