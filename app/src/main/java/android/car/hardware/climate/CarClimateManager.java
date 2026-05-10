package android.car.hardware.hvac;

import android.car.Car;

/**
 * CarClimateManager Stub
 */
public class CarClimateManager {
    public CarClimateManager(Car car) {}
    
    // HVAC Zone constants
    public static final int HVAC_ZONE_DRV = 1;
    public static final int HVAC_ZONE_PASS = 2;
    
    // HVAC Mode constants
    public static final int HVAC_MODE_DEFROST = 1;
    public static final int HVAC_MODE_FACE = 2;
    public static final int HVAC_MODE_FLOOR = 4;
}
