$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$testRoot = Join-Path $projectRoot 'app/build/lifecycle-test'
$javaDirectory = (Get-ChildItem (Join-Path $projectRoot '.tools/jdk17') -Directory | Select-Object -First 1).FullName
$sources = @{
    'android/content/Context.java' = 'package android.content; public class Context {}'
    'android/os/Build.java' = 'package android.os; public class Build { public static class VERSION { public static int SDK_INT=33; } }'
    'android/os/Looper.java' = 'package android.os; public class Looper { public static Looper getMainLooper() { return new Looper(); } }'
    'android/os/Handler.java' = @'
package android.os;
public class Handler {
    static final java.util.Queue<Runnable> queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public Handler(Looper looper) {}
    public boolean post(Runnable work) { queue.add(work); return true; }
    public static void drain() { Runnable task; while ((task=queue.poll())!=null) task.run(); }
}
'@
    'android/bluetooth/BluetoothDevice.java' = @'
package android.bluetooth;
public class BluetoothDevice {
    private final String address;
    public BluetoothDevice(String value) { address=value; }
    public String getAddress() { return address; }
}
'@
    'android/bluetooth/BluetoothProfile.java' = @'
package android.bluetooth;
public interface BluetoothProfile {
    int A2DP=2, HEADSET=1, LE_AUDIO=22;
    java.util.List<BluetoothDevice> getConnectedDevices();
    interface ServiceListener {
        void onServiceConnected(int profile, BluetoothProfile proxy);
        void onServiceDisconnected(int profile);
    }
}
'@
    'android/bluetooth/BluetoothAdapter.java' = @'
package android.bluetooth;
import java.util.*;
public class BluetoothAdapter {
    public boolean deliverImmediately=true;
    public int closeCount;
    public final List<Proxy> proxies = new ArrayList<>();
    public static class Proxy implements BluetoothProfile {
        public final int kind; public final ServiceListener listener;
        public Runnable onQuery;
        Proxy(int value, ServiceListener callback) { kind=value; listener=callback; }
        public List<BluetoothDevice> getConnectedDevices() {
            if(onQuery!=null) { Runnable work=onQuery; onQuery=null; work.run(); }
            return Collections.singletonList(new BluetoothDevice("device-"+kind));
        }
    }
    public boolean getProfileProxy(android.content.Context context, BluetoothProfile.ServiceListener listener, int profile) {
        Proxy proxy = new Proxy(profile,listener); proxies.add(proxy);
        if(deliverImmediately) listener.onServiceConnected(profile,proxy);
        return true;
    }
    public void closeProfileProxy(int profile, BluetoothProfile value) {
        closeCount++;
        // Reentrant callbacks during close reproduce the reported lifecycle failure.
        ((Proxy)value).listener.onServiceDisconnected(profile);
    }
}
'@
    'com/karen/juntos/LifecycleRegression.java' = @'
package com.karen.juntos;
import android.bluetooth.*;
import android.os.Handler;
import android.content.Context;
import java.util.*;
public class LifecycleRegression {
    static void check(boolean valid, String message) { if(!valid) throw new AssertionError(message); }
    public static void main(String[] args) {
        int[] changes={0};
        BluetoothAdapter adapter=new BluetoothAdapter();
        BluetoothAudioState state=new BluetoothAudioState(new Context(),adapter,()->changes[0]++);
        state.start(); Handler.drain();
        check(state.connectedDevices().size()==3,"All profiles should be observed");
        int beforeStop=changes[0];
        state.stop(); Handler.drain();
        check(state.connectedDevices().isEmpty(),"Stop must clear connections");
        check(adapter.closeCount==3,"Stop must close every proxy despite reentrant callbacks");
        check(changes[0]==beforeStop,"Stopped callbacks must not restart UI or service work");
        state.stop(); Handler.drain();
        check(adapter.closeCount==3,"Repeated stop must be idempotent");

        BluetoothAdapter delayed=new BluetoothAdapter(); delayed.deliverImmediately=false;
        BluetoothAudioState restarted=new BluetoothAudioState(new Context(),delayed,()->{});
        restarted.start(); List<BluetoothAdapter.Proxy> old=new ArrayList<>(delayed.proxies);
        restarted.stop(); delayed.deliverImmediately=true; restarted.start();
        for(BluetoothAdapter.Proxy proxy:old) proxy.listener.onServiceConnected(proxy.kind,proxy);
        Handler.drain();
        check(restarted.connectedDevices().size()==3,"Late old callbacks must not replace or remove the new session");
        check(delayed.closeCount==3,"Late old proxies must be released");
        restarted.stop(); Handler.drain();
        check(delayed.closeCount==6,"Both sessions must release their proxies exactly once");

        BluetoothAdapter disappearing=new BluetoothAdapter();
        BluetoothAudioState refreshing=new BluetoothAudioState(new Context(),disappearing,()->{});
        refreshing.start(); Handler.drain();
        for(BluetoothAdapter.Proxy proxy:disappearing.proxies)
            proxy.onQuery=()->proxy.listener.onServiceDisconnected(proxy.kind);
        refreshing.refresh(); Handler.drain();
        check(refreshing.connectedDevices().isEmpty(),"Disconnection during refresh must settle without concurrent modification");
        refreshing.stop(); Handler.drain();
        System.out.println("PASS: reentrant close, repeated stop, stale callbacks after restart, disconnect during refresh");
    }
}
'@
}
foreach ($entry in $sources.GetEnumerator()) {
    $target = Join-Path $testRoot $entry.Key
    New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
    [IO.File]::WriteAllText($target, $entry.Value)
}
$productionSource = Join-Path $projectRoot 'app/src/main/java/com/karen/juntos/BluetoothAudioState.java'
$testClasses = Join-Path $testRoot 'classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sourcePaths = @($sources.Keys | ForEach-Object { Join-Path $testRoot $_ }) + @($productionSource)
& (Join-Path $javaDirectory 'bin/javac.exe') -d $testClasses @sourcePaths
if ($LASTEXITCODE -ne 0) { throw 'Lifecycle regression compilation failed' }
& (Join-Path $javaDirectory 'bin/java.exe') -cp $testClasses com.karen.juntos.LifecycleRegression
if ($LASTEXITCODE -ne 0) { throw 'Lifecycle regression failed' }
