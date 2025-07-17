package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;

public class FlutterBarcodeScannerPlugin implements
        MethodCallHandler,
        ActivityResultListener,
        StreamHandler,
        FlutterPlugin,
        ActivityAware {

    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final String EVENT_CHANNEL = "flutter_barcode_scanner_receiver";
    private static final String TAG = FlutterBarcodeScannerPlugin.class.getSimpleName();
    private static final int RC_BARCODE_CAPTURE = 9001;

    private MethodChannel channel;
    private EventChannel eventChannel;
    private static EventChannel.EventSink barcodeStream;

    private Activity activity;
    private Application applicationContext;
    private FlutterPluginBinding pluginBinding;
    private ActivityPluginBinding activityBinding;
    private Lifecycle lifecycle;
    private LifeCycleObserver observer;

    private static Result pendingResult;
    private Map<String, Object> arguments;

    public static String lineColor = "";
    public static boolean isShowFlashIcon = false;
    public static boolean isContinuousScan = false;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.pluginBinding = binding;
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        clearPluginSetup();
        this.pluginBinding = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activityBinding = binding;
        this.activity = binding.getActivity();
        this.applicationContext = (Application) activity.getApplicationContext();

        setupChannels(pluginBinding.getBinaryMessenger());
        binding.addActivityResultListener(this);

        lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding);
        observer = new LifeCycleObserver(activity);
        lifecycle.addObserver(observer);
        applicationContext.registerActivityLifecycleCallbacks(observer);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        clearPluginSetup();
    }

    private void setupChannels(BinaryMessenger messenger) {
        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(messenger, EVENT_CHANNEL);
        eventChannel.setStreamHandler(this);
    }

    private void clearPluginSetup() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this);
            activityBinding = null;
        }

        if (lifecycle != null && observer != null) {
            lifecycle.removeObserver(observer);
            lifecycle = null;
        }

        if (applicationContext != null && observer != null) {
            applicationContext.unregisterActivityLifecycleCallbacks(observer);
            applicationContext = null;
        }

        if (channel != null) {
            channel.setMethodCallHandler(null);
            channel = null;
        }

        if (eventChannel != null) {
            eventChannel.setStreamHandler(null);
            eventChannel = null;
        }

        activity = null;
        observer = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        pendingResult = result;

        if (call.method.equals("scanBarcode")) {
            if (!(call.arguments instanceof Map)) {
                result.error("INVALID_ARGUMENT", "Expected a map", null);
                return;
            }

            arguments = (Map<String, Object>) call.arguments;
            lineColor = (String) arguments.get("lineColor");
            isShowFlashIcon = (boolean) arguments.get("isShowFlashIcon");
            isContinuousScan = (boolean) arguments.get("isContinuousScan");

            if (lineColor == null || lineColor.isEmpty()) {
                lineColor = "#DC143C";
            }

            Object scanModeObj = arguments.get("scanMode");
            if (scanModeObj instanceof Integer) {
                int scanMode = (int) scanModeObj;
                if (scanMode == BarcodeCaptureActivity.SCAN_MODE_ENUM.DEFAULT.ordinal()) {
                    BarcodeCaptureActivity.SCAN_MODE = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
                } else {
                    BarcodeCaptureActivity.SCAN_MODE = scanMode;
                }
            } else {
                BarcodeCaptureActivity.SCAN_MODE = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
            }

            startBarcodeScannerActivityView((String) arguments.get("cancelButtonText"), isContinuousScan);
        } else {
            result.notImplemented();
        }
    }

    private void startBarcodeScannerActivityView(String buttonText, boolean isContinuousScan) {
        try {
            Intent intent = new Intent(activity, BarcodeCaptureActivity.class)
                    .putExtra("cancelButtonText", buttonText);
            if (isContinuousScan) {
                activity.startActivity(intent);
            } else {
                activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
            }
        } catch (Exception e) {
            Log.e(TAG, "startView: " + e.getLocalizedMessage());
            if (pendingResult != null) {
                pendingResult.error("ACTIVITY_ERROR", e.getMessage(), null);
            }
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (pendingResult == null) return false;

            if (resultCode == CommonStatusCodes.SUCCESS && data != null) {
                try {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    String barcodeResult = barcode.rawValue;
                    pendingResult.success(barcodeResult);
                } catch (Exception e) {
                    pendingResult.success("-1");
                }
            } else {
                pendingResult.success("-1");
            }
            pendingResult = null;
            arguments = null;
            return true;
        }
        return false;
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        barcodeStream = events;
    }

    @Override
    public void onCancel(Object arguments) {
        barcodeStream = null;
    }

    public static void onBarcodeScanReceiver(final Barcode barcode) {
        if (barcode != null && !barcode.displayValue.isEmpty() && barcodeStream != null && activity != null) {
            activity.runOnUiThread(() -> barcodeStream.success(barcode.rawValue));
        }
    }

    private static class LifeCycleObserver implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
        private final Activity thisActivity;

        LifeCycleObserver(Activity activity) {
            this.thisActivity = activity;
        }

        @Override public void onCreate(@NonNull LifecycleOwner owner) {}
        @Override public void onStart(@NonNull LifecycleOwner owner) {}
        @Override public void onResume(@NonNull LifecycleOwner owner) {}
        @Override public void onPause(@NonNull LifecycleOwner owner) {}
        @Override public void onStop(@NonNull LifecycleOwner owner) { onActivityStopped(thisActivity); }
        @Override public void onDestroy(@NonNull LifecycleOwner owner) { onActivityDestroyed(thisActivity); }

        @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityResumed(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
        @Override public void onActivityDestroyed(Activity activity) {
            if (thisActivity == activity && activity.getApplicationContext() != null) {
                ((Application) activity.getApplicationContext())
                        .unregisterActivityLifecycleCallbacks(this);
            }
        }
        @Override public void onActivityStopped(Activity activity) {}
    }
}
