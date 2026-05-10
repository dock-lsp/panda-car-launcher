package android.car.hardware.media;

import android.car.Car;

/**
 * CarAudioManager Stub
 */
public class CarAudioManager {
    public CarAudioManager(Car car) {}
    
    public void setGroupVolume(int groupId, int index, int flags) {}
    
    public int getGroupVolume(int groupId) {
        return 50;
    }
    
    public int getVolumeGroupCount() {
        return 1;
    }
}
