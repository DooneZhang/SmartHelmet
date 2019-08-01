package com.datang.smarthelmet.receivers;

/*
BootReceiver.java
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.datang.smarthelmet.SmartHelmetService;
import com.datang.smarthelmet.activities.SmartHelmetLauncherActivity;
import com.datang.smarthelmet.compatibility.Compatibility;
import com.datang.smarthelmet.settings.LinphonePreferences;

// 启动时的广播接收者
public class BootReceiver extends BroadcastReceiver {
    // 当Android启动时，会发出系统广播ACTION_BOOT_COMPLETED
    private final String ACTION = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {

        // 如果捕获到设备关机则销毁程序得注册和停止服务
        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_SHUTDOWN)) {
            android.util.Log.d(
                    "SmartHelmet",
                    "[Boot Receiver] Device is shutting down, destroying Core to unregister");
            context.stopService(
                    new Intent(Intent.ACTION_MAIN).setClass(context, SmartHelmetService.class));
        } else {
            // 开机或其他命令则进行以下操作
            // if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED))
            LinphonePreferences.instance().setContext(context);
            boolean autostart = LinphonePreferences.instance().isAutoStartEnabled();
            android.util.Log.i(
                    "SmartHelmet",
                    "[Boot Receiver] Device is starting, auto_start is " + autostart);
            // 启动Activity
            Intent activityIntent = new Intent(context, SmartHelmetLauncherActivity.class);
            //    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Compatibility.startActivity(context, activityIntent);
            // 启动服务
            Intent serviceIntent = new Intent(Intent.ACTION_MAIN);
            serviceIntent.setClass(context, SmartHelmetService.class);
            serviceIntent.putExtra("ForceStartForeground", true);
            Compatibility.startService(context, serviceIntent);
            /*
                            // 启动整个应用//包名为要唤醒的应用包名
                            Intent AppIntent =
                                    context.getPackageManager()
                                            .getLaunchIntentForPackage("com.datang.smarthelmet");
                            context.startActivity(AppIntent);
            */
            Log.d("DEBUG", "开机自动启动...");
            android.util.Log.i(
                    "SmartHelmet",
                    "[Boot Receiver] Device is starting, auto_start is "
                            + autostart
                            + "and activity is start");
        }
    }
}
