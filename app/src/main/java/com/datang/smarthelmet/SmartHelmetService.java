package com.datang.smarthelmet;

/*
SmartHelmetService.java
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
import android.app.Application;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.view.WindowManager;
import com.datang.smarthelmet.call.CallIncomingActivity;
import com.datang.smarthelmet.call.CallOutgoingActivity;
import com.datang.smarthelmet.contacts.ContactsManager;
import com.datang.smarthelmet.fragments.SmartHelmetGL2JNIViewOverlay;
import com.datang.smarthelmet.notifications.NotificationsManager;
import com.datang.smarthelmet.settings.LinphonePreferences;
import com.datang.smarthelmet.utils.ActivityMonitor;
import com.datang.smarthelmet.utils.SmartHelmetUtils;
import com.datang.smarthelmet.views.SmartHelmetOverlay;
import com.datang.smarthelmet.views.SmartHelmetTextureViewOverlay;
import java.util.Locale;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.LogLevel;
import org.linphone.core.LoggingService;
import org.linphone.core.LoggingServiceListener;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;

/**
 * Linphone service, reacting to Incoming calls, ...<br>
 *
 * <p>Roles include:
 *
 * <ul>
 *   <li>Initializing SmartHelmetManager
 *   <li>Starting C libLinphone through SmartHelmetManager
 *   <li>Reacting to SmartHelmetManager state changes
 *   <li>Delegating GUI state change actions to GUI listener
 */
public final class SmartHelmetService extends Service {
    private static final String START_LINPHONE_LOGS = " ==== Phone information dump ====";
    private static SmartHelmetService sInstance;

    public final Handler handler = new Handler();
    private SmartHelmetOverlay mOverlay;
    private WindowManager mWindowManager;
    private Application.ActivityLifecycleCallbacks mActivityCallbacks;
    public static TextToSpeech mSpeech = null;
    public static String speakText = null;
    private static final String TAG = "SmartHelmetService";

    private final LoggingServiceListener mJavaLoggingService =
            new LoggingServiceListener() {
                @Override
                public void onLogMessageWritten(
                        LoggingService logService, String domain, LogLevel lev, String message) {
                    switch (lev) {
                        case Debug:
                            android.util.Log.d(domain, message);
                            break;
                        case Message:
                            android.util.Log.i(domain, message);
                            break;
                        case Warning:
                            android.util.Log.w(domain, message);
                            break;
                        case Error:
                            android.util.Log.e(domain, message);
                            break;
                        case Fatal:
                        default:
                            android.util.Log.wtf(domain, message);
                            break;
                    }
                }
            };
    private CoreListenerStub mListener;
    private NotificationsManager mNotificationManager;
    private SmartHelmetManager mLinphoneManager;
    private ContactsManager mContactsManager;

    private Class<? extends Activity> mIncomingReceivedActivity = CallIncomingActivity.class;

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化语音播报
        intiTTS();
        // 设置活动监听器
        setupActivityMonitor();

        // 需要为了下面两个调用成功,库文件必须先加载
        // Needed in order for the two next calls to succeed, libraries must have been loaded first
        LinphonePreferences.instance().setContext(this);
        Factory.instance().setLogCollectionPath(getFilesDir().getAbsolutePath());
        boolean isDebugEnabled = LinphonePreferences.instance().isDebugEnabled();
        SmartHelmetUtils.configureLoggingService(isDebugEnabled, getString(R.string.app_name));
        // SmartHelmetService isn't ready yet so we have to manually set up the Java logging service
        if (LinphonePreferences.instance().useJavaLogger()) {
            Factory.instance().getLoggingService().addListener(mJavaLoggingService);
        }

        // Dump some debugging information to the logs
        Log.i(START_LINPHONE_LOGS);
        dumpDeviceInformation();
        dumpInstalledSmartHelmetInformation();

        String incomingReceivedActivityName =
                LinphonePreferences.instance().getActivityToLaunchOnIncomingReceived();
        try {
            mIncomingReceivedActivity =
                    (Class<? extends Activity>) Class.forName(incomingReceivedActivityName);
        } catch (ClassNotFoundException e) {
            Log.e(e);
        }

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (sInstance == null) {
                            Log.i(
                                    "[Service] Service not ready, discarding call state change to ",
                                    state.toString());
                            return;
                        }

                        if (getResources().getBoolean(R.bool.enable_call_notification)) {
                            mNotificationManager.displayCallNotification(call);
                        }

                        if (state == Call.State.IncomingReceived
                                || state == State.IncomingEarlyMedia) {
                            if (!mLinphoneManager.getCallGsmON()) onIncomingReceived();
                        } else if (state == State.OutgoingInit) {
                            onOutgoingStarted();
                        } else if (state == State.End
                                || state == State.Released
                                || state == State.Error) {
                            destroyOverlay();

                            if (state == State.Released
                                    && call.getCallLog().getStatus() == Call.Status.Missed) {
                                mNotificationManager.displayMissedCallNotification(call);
                            }
                        }
                    }
                };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        boolean isPush = false;
        if (intent != null && intent.getBooleanExtra("PushNotification", false)) {
            Log.i("[Service] [Push Notification] SmartHelmetService started because of a push");
            isPush = true;
        }

        if (sInstance != null) {
            Log.w("[Service] Attempt to start the SmartHelmetService but it is already running !");
            return START_STICKY;
        }

        mLinphoneManager = new SmartHelmetManager(this);
        sInstance = this; // sInstance is ready once linphone manager has been created

        mNotificationManager = new NotificationsManager(this);
        if (Version.sdkAboveOrEqual(Version.API26_O_80)
                && intent != null
                && intent.getBooleanExtra("ForceStartForeground", false)) {
            // We need to call this asap after the Service can be accessed through it's singleton
            mNotificationManager.startForeground();
        }

        mLinphoneManager.startLibLinphone(isPush);
        SmartHelmetManager.getCore().addListener(mListener);

        mNotificationManager.onCoreReady();

        mContactsManager = new ContactsManager(this, handler);
        if (!Version.sdkAboveOrEqual(Version.API26_O_80)
                || (mContactsManager.hasReadContactsAccess())) {
            getContentResolver()
                    .registerContentObserver(
                            ContactsContract.Contacts.CONTENT_URI, true, mContactsManager);
        }
        if (mContactsManager.hasReadContactsAccess()) {
            mContactsManager.enableContactsAccess();
        }
        mContactsManager.initializeContactManager();

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        boolean serviceNotif = LinphonePreferences.instance().getServiceNotificationVisibility();
        if (serviceNotif) {
            Log.i("[Service] Service is running in foreground, don't stop it");
        } else if (getResources().getBoolean(R.bool.kill_service_with_task_manager)) {
            Log.i("[Service] Task removed, stop service");
            Core core = SmartHelmetManager.getCore();
            if (core != null) {
                core.terminateAllCalls();
            }

            // If push is enabled, don't unregister account, otherwise do unregister
            if (LinphonePreferences.instance().isPushNotificationEnabled()) {
                if (core != null) core.setNetworkReachable(false);
            }
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @SuppressWarnings("UnusedAssignment")
    @Override
    public synchronized void onDestroy() {
        if (mActivityCallbacks != null) {
            getApplication().unregisterActivityLifecycleCallbacks(mActivityCallbacks);
            mActivityCallbacks = null;
        }
        destroyOverlay();

        Core core = SmartHelmetManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
            core = null; // To allow the gc calls below to free the Core
        }

        // Make sure our notification is gone.
        if (mNotificationManager != null) {
            mNotificationManager.destroy();
        }
        mContactsManager.destroy();

        // Destroy the SmartHelmetManager second to last to ensure any getCore() call will work
        mLinphoneManager.destroy();

        // Wait for every other object to be destroyed to make SmartHelmetService.instance() invalid
        sInstance = null;

        if (LinphonePreferences.instance().useJavaLogger()) {
            Factory.instance().getLoggingService().removeListener(mJavaLoggingService);
        }
        LinphonePreferences.instance().destroy();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isReady() {
        return sInstance != null;
    }

    public static SmartHelmetService instance() {
        if (isReady()) return sInstance;

        throw new RuntimeException("SmartHelmetService not instantiated yet");
    }

    /* Managers accessors */

    public LoggingServiceListener getJavaLoggingService() {
        return mJavaLoggingService;
    }

    public NotificationsManager getNotificationManager() {
        return mNotificationManager;
    }

    public SmartHelmetManager getLinphoneManager() {
        return mLinphoneManager;
    }

    public ContactsManager getContactsManager() {
        return mContactsManager;
    }

    public void createOverlay() {
        if (mOverlay != null) destroyOverlay();

        Core core = SmartHelmetManager.getCore();
        Call call = core.getCurrentCall();
        if (call == null || !call.getCurrentParams().videoEnabled()) return;

        if ("MSAndroidOpenGLDisplay".equals(core.getVideoDisplayFilter())) {
            mOverlay = new SmartHelmetGL2JNIViewOverlay(this);
        } else {
            mOverlay = new SmartHelmetTextureViewOverlay(this);
        }
        WindowManager.LayoutParams params = mOverlay.getWindowManagerLayoutParams();
        params.x = 0;
        params.y = 0;
        mOverlay.addToWindowManager(mWindowManager, params);
    }

    public void destroyOverlay() {
        if (mOverlay != null) {
            mOverlay.removeFromWindowManager(mWindowManager);
            mOverlay.destroy();
        }
        mOverlay = null;
    }

    // 设置活动监听器
    private void setupActivityMonitor() {
        if (mActivityCallbacks != null) return;
        getApplication()
                .registerActivityLifecycleCallbacks(mActivityCallbacks = new ActivityMonitor());
    }
    // 转储设备信息
    private void dumpDeviceInformation() {
        StringBuilder sb = new StringBuilder();
        sb.append("DEVICE=").append(Build.DEVICE).append("\n");
        sb.append("MODEL=").append(Build.MODEL).append("\n");
        sb.append("MANUFACTURER=").append(Build.MANUFACTURER).append("\n");
        sb.append("SDK=").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Supported ABIs=");
        for (String abi : Version.getCpuAbis()) {
            sb.append(abi).append(", ");
        }
        sb.append("\n");
        Log.i(sb.toString());
    }
    // 转储SmartHelmet安装信息
    private void dumpInstalledSmartHelmetInformation() {
        PackageInfo info = null;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException nnfe) {
            Log.e(nnfe);
        }

        if (info != null) {
            Log.i(
                    "[Service] SmartHelmet version is ",
                    info.versionName + " (" + info.versionCode + ")");
        } else {
            Log.i("[Service] SmartHelmet version is unknown");
        }
    }
    // 检测来电的服务进程
    private void onIncomingReceived() {
        SmartHelmetService.speakText = "老板，来电话了！";
        Intent intent = new Intent().setClass(this, mIncomingReceivedActivity);
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    // 检测去电的服务进程
    private void onOutgoingStarted() {
        SmartHelmetService.speakText = "我太难了！";
        Intent intent = new Intent(SmartHelmetService.this, CallOutgoingActivity.class);
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // 初始化语音播报服务进程，软件开启后在后台服务进程不关闭的情况下只运行一次
    private void intiTTS() {
        // 如果mSpeech还没有被清空则关闭清空一下在注册

        if (mSpeech != null) {
            mSpeech.stop();
            mSpeech.shutdown();
            mSpeech = null;

            // 创建TTS对象
            mSpeech = new TextToSpeech(SmartHelmetService.this, new TTSListener());
            // 设置为中文语音
            mSpeech.setLanguage(Locale.CHINA);
            mSpeech.setSpeechRate(0.3f);
            mSpeech.setPitch(5.0f);
        } else {
            // 创建TTS对象
            mSpeech = new TextToSpeech(SmartHelmetService.this, new TTSListener());
            // 设置为中文语音
            mSpeech.setLanguage(Locale.CHINA);
            mSpeech.setSpeechRate(0.3f);
            mSpeech.setPitch(5.0f);
        }
    }

    private class TTSListener implements TextToSpeech.OnInitListener {
        @Override
        public void onInit(int status) {
            // TODO Auto-generated method stub
            if (status == TextToSpeech.SUCCESS) {
                // 一定要等初始化完毕后才能播放语音
                // mSpeech.speak(speakText, TextToSpeech.QUEUE_FLUSH, null);
                //           playTTS("来了老弟！");
                Log.i(TAG, "onInit: TTS引擎初始化成功");
            } else {
                Log.i(TAG, "onInit: TTS引擎初始化失败");
            }
        }
    }
    // 传入要播放的文字
    public static void playTTS(String speakText) {
        mSpeech.speak(speakText, TextToSpeech.QUEUE_FLUSH, null);
    }
}
