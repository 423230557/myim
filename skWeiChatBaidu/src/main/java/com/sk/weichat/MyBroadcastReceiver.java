package com.sk.weichat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static com.sk.weichat.AppConfig.BROADCASTTEST_ACTION;

public class MyBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e("zxzxzx", "onReceive: ");
        if (BROADCASTTEST_ACTION.equals(intent.getAction())) {
            context.startService(new Intent(context, RestartService.class));

        }
    }
}
