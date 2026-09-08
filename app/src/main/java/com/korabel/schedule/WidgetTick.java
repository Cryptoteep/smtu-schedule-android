package com.korabel.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Срабатывает по самозаводящемуся будильнику виджетов. */
public final class WidgetTick extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        Widgets.poke(ctx);
    }
}
