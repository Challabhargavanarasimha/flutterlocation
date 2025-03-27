package com.lyokone.location;

import android.app.Activity;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.plugins.FlutterPlugin;

public class LocationPlugin implements FlutterPlugin, ActivityAware {
    private FlutterLocationService locationService;
    private MethodCallHandlerImpl methodCallHandler;
    private StreamHandlerImpl streamHandlerImpl;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        locationService = new FlutterLocationService(binding.getApplicationContext());
        methodCallHandler = new MethodCallHandlerImpl(locationService);
        streamHandlerImpl = new StreamHandlerImpl(locationService);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        methodCallHandler = null;
        streamHandlerImpl = null;
        locationService = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding activityBinding) {
        locationService.setActivity(activityBinding.getActivity());
        activityBinding.addActivityResultListener(locationService.getActivityResultListener());
        activityBinding.addRequestPermissionsResultListener(locationService.getPermissionsResultListener());
        methodCallHandler.setLocationService(locationService);
    }

    @Override
    public void onDetachedFromActivity() {
        locationService.setActivity(null);
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }
}
