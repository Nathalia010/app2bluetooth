package com.karen.juntos;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Anticipated media outputs, not proof that a device is audibly playing. */
final class MediaOutputSnapshot {
    final Set<String> bluetoothAddresses = new HashSet<>();
    int outputCount;
    String status = "Consulta disponible desde Android 13";

    static MediaOutputSnapshot read(Context context) {
        MediaOutputSnapshot result = new MediaOutputSnapshot();
        if (Build.VERSION.SDK_INT < 33) return result;
        try {
            List<AudioDeviceInfo> devices = context.getSystemService(AudioManager.class).getAudioDevicesForAttributes(
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            result.outputCount = devices.size();
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_HEADSET
                        || type == AudioDeviceInfo.TYPE_BLE_SPEAKER || type == AudioDeviceInfo.TYPE_HEARING_AID) {
                    String address = device.getAddress();
                    if (address != null && android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address.toUpperCase(java.util.Locale.ROOT))
                            && !address.equalsIgnoreCase("02:00:00:00:00:00") && !address.equalsIgnoreCase("00:00:00:00:00:00"))
                        result.bluetoothAddresses.add(address.toUpperCase(java.util.Locale.ROOT));
                }
            }
            result.status = devices.isEmpty() ? "Android no informó salidas multimedia" : "Consulta multimedia disponible";
        } catch (RuntimeException ex) { result.status = "El celular no permitió consultar las salidas multimedia"; }
        return result;
    }

    boolean includes(String address) {
        return address != null && bluetoothAddresses.contains(address.toUpperCase(java.util.Locale.ROOT));
    }
}
