package com.gowarrior.camera.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Jim.Yu on 2015/7/31.
 */
public class AppBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent mIntent = new Intent(context, MainActivity.class);
        mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mIntent.putExtra("ByBroadcast", true);
        context.startActivity(mIntent);
    }
}
