package android.car.hardware;

import android.car.Car;

/**
 * CarPropertyManager Stub
 */
public class CarPropertyManager {
    public CarPropertyManager(Car car) {}
    
    public Object getProperty(int propertyId, int areaId) {
        return null;
    }
    
    public void setProperty(int propertyId, int areaId, Object value) {}
    
    public void registerCallback(CarPropertyEventCallback callback) {}
    
    public void unregisterCallback(CarPropertyEventCallback callback) {}
    
    public interface CarPropertyEventCallback {
        void onChangeEvent(Object value);
        void onErrorEvent(int propertyId, int areaId);
    }
}
