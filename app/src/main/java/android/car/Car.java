package android.car;

import android.content.Context;

/**
 * Android Automotive Car API Stub
 * 用于在非 AAOS 设备上编译
 */
public class Car {
    public static final String CAR_SERVICE_NAME = "car";

    public static boolean isCarServiceSupported(Context context) {
        return false; // 在非 AAOS 设备上返回 false
    }

    public static Car createCar(Context context, CarConnectionCallback callback) {
        return null; // Stub 实现
    }

    public boolean isConnected() {
        return false;
    }

    public void disconnect() {}

    public interface CarConnectionCallback {
        void onConnected(Car car);
        void onDisconnected(Car car);
    }
}
