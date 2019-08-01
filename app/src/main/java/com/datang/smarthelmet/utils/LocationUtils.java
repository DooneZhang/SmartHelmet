package com.datang.smarthelmet.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import com.datang.smarthelmet.compatibility.Compatibility;
import java.util.List;
import java.util.Locale;

/*
 * 注意
 * 1、Android6.0动态权限,
 * 2、Geocoder获取地理位置信息是一个后台的耗时操作，
 * 为了不阻塞主线程，强力建议在使用Geocoder获取地理位置信息时采用异步线程的方式来请求服务，这样不会造成主线程的阻塞。
 * */
public class LocationUtils {

    private static volatile LocationUtils uniqueInstance;
    private LocationManager locationManager;
    private Context mContext;
    private String TAG = "LocationUtils";
    public static String currentPosition;
    double latitude;
    double longitude;

    // 采用Double CheckLock(DCL)实现单例
    public static LocationUtils getInstance(Context context) {
        if (uniqueInstance == null) {
            synchronized (LocationUtils.class) {
                if (uniqueInstance == null) {
                    uniqueInstance = new LocationUtils(context);
                }
            }
        }
        return uniqueInstance;
    }

    public LocationUtils(Context context) {
        mContext = context;
        // 判断是否已获位置授权
        if (Compatibility.getLocations(context)) {
            // 如果已获授权则获取位置信息
            getLocation();
        } else {
            // 如果没有授权，将弹出授权
            /*
                        // 这个方法是跳转到系统位置设置，让用户手动打开位置和打开软件的位置授权
                        // 跳转到系统让手动用户打开或授权
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        mContext.startActivity(intent);
            */
            // 这个方法是直接在软件里弹出授权对话框

        }
    }

    @SuppressLint("MissingPermission")
    private void getLocation() {
        // 1.获取位置管理器
        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        // 2.获取位置提供器，GPS或是NetWork
        // 获取所有可用的位置提供器
        List<String> providerList = locationManager.getProviders(true);
        String locationProvider;
        if (providerList.contains(LocationManager.GPS_PROVIDER)) {
            // GPS 定位的精准度比较高，但是非常耗电。
            System.out.println("=====GPS_PROVIDER=====");
            locationProvider = LocationManager.GPS_PROVIDER;
        } else if (providerList.contains(LocationManager.NETWORK_PROVIDER)) { // Google服务被墙不可用
            // 网络定位的精准度稍差，但耗电量比较少。
            System.out.println("=====NETWORK_PROVIDER=====");
            locationProvider = LocationManager.NETWORK_PROVIDER;
        } else {
            System.out.println("=====NO_PROVIDER=====");
            // 当没有可用的位置提供器时，弹出Toast提示用户
            Toast.makeText(mContext, "请先打开位置开关！", Toast.LENGTH_SHORT).show();

            // 跳转到系统让手动用户打开或授权
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            mContext.startActivity(intent);

            return;
        }
        // 如果软件已获得了相应的权限，则直接做相应操作
        @SuppressLint("MissingPermission")
        Location location = locationManager.getLastKnownLocation(locationProvider);
        if (location != null) {
            // 显示当前设备的位置信息
            System.out.println("==显示当前设备的位置信息==");
            showLocation(location);
        } else {
            // 当GPS信号弱没获取到位置的时候可从网络获取
            System.out.println("==GPS信号弱从网络获取==");
            getLngAndLatWithNetwork();
        }
        // 监视地理位置变化，第二个和第三个参数分别为更新的最短时间minTime和最短距离minDistace
        // LocationManager 每隔 5 秒钟会检测一下位置的变化情况，当移动距离超过 10 米的时候，
        // 就会调用 LocationListener 的 onLocationChanged() 方法，并把新的位置信息作为参数传入。
        locationManager.requestLocationUpdates(locationProvider, 5000, 10, locationListener);
    }

    // 显示获取到的经纬度
    private void showLocation(Location location) {
        latitude = location.getLatitude(); // 纬度
        longitude = location.getLongitude(); // 经度
        getAddress(latitude, longitude);
    }

    private void getAddress(final double latitude, final double longitude) {
        // 在子线程里完成Geocoder这种消耗资源的事件
        new Thread() {
            @Override
            public void run() {
                // 在子线程中进行下载操作
                // Geocoder通过经纬度获取具体信息
                Geocoder gc = new Geocoder(mContext, Locale.getDefault());
                // text.setText("下载完成");//设置TextView,通知UI界面下载完成
                try {
                    List<Address> locationList = gc.getFromLocation(latitude, longitude, 1);
                    if (locationList != null) {
                        // 摘取位置信息列表裏的第一個位置信息
                        Address address = locationList.get(0);

                        String countryName = address.getCountryName(); // 国家
                        String countryCode = address.getCountryCode();
                        String adminArea = address.getAdminArea(); // 省
                        String locality = address.getLocality(); // 市
                        String subAdminArea = address.getSubAdminArea(); // 区
                        String featureName = address.getFeatureName(); // 街道

                        for (int i = 0; address.getAddressLine(i) != null; i++) {
                            String addressLine = address.getAddressLine(i);
                            // 街道名称
                            System.out.println("addressLine=====" + addressLine);

                            currentPosition =
                                    "经坐标为："
                                            + latitude
                                            + "\n纬坐标为："
                                            + longitude
                                            + "\n具体位置为："
                                            + addressLine;
                        }

                        // currentPosition = "定位到的位置为："
                        /*
                                                "latitude is "
                                                        + latitude // 22.545975
                                                        + "\n"
                                                        + "longitude is "
                                                        + longitude // 114.101232
                                                        + "\n"
                                                        + "countryName is "
                                                        + countryName // null
                                                        + "\n"
                                                        + "countryCode is "
                                                        + countryCode // CN
                                                        + "\n"
                                                        + "adminArea is "
                                                        + adminArea // 省
                                                        + "\n"
                                                        + "locality is "
                                                        + locality // 市
                                                        + "\n"
                                                        + "subAdminArea is "
                                                        + subAdminArea // 区
                                                        + "\n"
                                                        + "featureName is "
                                                        + featureName; // 具体名字
                        */
                        System.out.println(currentPosition);
                    }
                } catch (Exception e) {
                    // 这里写对异常的处理，对于Exception e写
                    e.printStackTrace();
                    // 打印出来异常
                    //   Log.e(TAG, Log.getStackTraceString(e));
                    // 如果JSONException e，打印出来就 Log.e(TAG, e.toString() + "");
                }
            }
        }.start();
    }

    public void removeLocationUpdatesListener() {
        if (locationManager != null) {
            uniqueInstance = null;
            locationManager.removeUpdates(locationListener);
        }
    }

    private LocationListener locationListener =
            new LocationListener() {
                // Provider的状态在可用、暂时不可用和无服务三个状态直接切换时触发此函数
                @Override
                public void onStatusChanged(String provider, int status, Bundle arg2) {}
                // Provider被enable时触发此函数，比如GPS被打开
                @Override
                public void onProviderEnabled(String provider) {}
                // Provider被disable时触发此函数，比如GPS被关闭
                @Override
                public void onProviderDisabled(String provider) {}
                // 当坐标改变时触发此函数，如果Provider传进相同的坐标，它就不会被触发
                @Override
                public void onLocationChanged(Location location) {
                    System.out.println("==onLocationChanged==");
                    showLocation(location);
                }
            };
    // 从网络获取经纬度
    @SuppressLint("MissingPermission")
    private void getLngAndLatWithNetwork() {
        LocationManager locationManager =
                (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        // 监视地理位置变化，第二个和第三个参数分别为更新的最短时间minTime和最短距离minDistace
        // LocationManager 每隔 5 秒钟会检测一下位置的变化情况，当移动距离超过 10 米的时候，
        // 就会调用 LocationListener 的 onLocationChanged() 方法，并把新的位置信息作为参数传入。
        locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 5000, 10, locationListener);
        Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (location != null) {
            showLocation(location);
        } else {
            Log.i("Locator", "location为空");
        }
    }
}
