package com.datang.smarthelmet.compatibility;

/*
ApiTwentyThreePlus.java
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
import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import androidx.core.content.ContextCompat;
import com.datang.smarthelmet.contacts.ContactsManager;
import com.datang.smarthelmet.contacts.SmartHelmetContact;
import org.linphone.core.Address;
import org.linphone.core.tools.Log;

@TargetApi(23)
class ApiTwentyThreePlus {

    public static boolean isAppIdleMode(Context context) {
        return ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isDeviceIdleMode();
    }

    public static boolean isDoNotDisturbSettingsAccessGranted(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return notificationManager.isNotificationPolicyAccessGranted();
    }

    public static boolean isDoNotDisturbPolicyAllowingRinging(
            Context context, Address remoteAddress) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int filter = notificationManager.getCurrentInterruptionFilter();
        if (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            Log.w("[Audio Manager] Priority interruption filter detected");
            boolean accessGranted = notificationManager.isNotificationPolicyAccessGranted();
            if (!accessGranted) {
                Log.e(
                        "[Audio Manager] Access to policy is denied, let's assume it is not safe for ringing");
                return false;
            }

            NotificationManager.Policy policy = notificationManager.getNotificationPolicy();
            int callPolicy = policy.priorityCallSenders;
            if (callPolicy == NotificationManager.Policy.PRIORITY_SENDERS_ANY) {
                Log.i("[Audio Manager] Priority for calls is Any, we can ring");
            } else {
                if (remoteAddress == null) {
                    Log.e(
                            "[Audio Manager] Remote address is null, let's assume it is not safe for ringing");
                    return false;
                }

                SmartHelmetContact contact =
                        ContactsManager.getInstance().findContactFromAddress(remoteAddress);
                if (callPolicy == NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS) {
                    Log.i("[Audio Manager] Priority for calls is Contacts, let's check");
                    if (contact == null) {
                        Log.w(
                                "[Audio Manager] Couldn't find a contact for address "
                                        + remoteAddress.asStringUriOnly());
                        return false;
                    } else {
                        Log.i(
                                "[Audio Manager] Contact found for address "
                                        + remoteAddress.asStringUriOnly()
                                        + ", we can ring");
                    }
                } else if (callPolicy == NotificationManager.Policy.PRIORITY_SENDERS_STARRED) {
                    Log.i("[Audio Manager] Priority for calls is Starred Contacts, let's check");
                    if (contact == null) {
                        Log.w(
                                "[Audio Manager] Couldn't find a contact for address "
                                        + remoteAddress.asStringUriOnly());
                        return false;
                    } else if (!contact.isFavourite()) {
                        Log.w(
                                "[Audio Manager] Contact found for address "
                                        + remoteAddress.asStringUriOnly()
                                        + ", but it isn't starred");
                        return false;
                    } else {
                        Log.i(
                                "[Audio Manager] Starred contact found for address "
                                        + remoteAddress.asStringUriOnly()
                                        + ", we can ring");
                    }
                }
            }
        } else if (filter == NotificationManager.INTERRUPTION_FILTER_ALARMS) {
            Log.w("[Audio Manager] Alarms interruption filter detected");
            return false;
        } else {
            Log.i("[Audio Manager] Interruption filter is " + filter + ", we can ring");
        }

        return true;
    }

    public static boolean getLocations(Context context) {
        // 先检查是否有位置权限
        // 如果不存在授权
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // 未授权，返回false
            return false;
        } else {
            // 如果存在ACCESS_FINE_LOCATION授权,则查看是否有ACCESS_COARSE_LOCATION
            // 如果不存在ACCESS_COARSE_LOCATION授权
            if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                // 未授权，返回false
                return false;
            } else {
                // 如果两个授权都存在，则返回true
                return true;
            }
        }
    }
}
