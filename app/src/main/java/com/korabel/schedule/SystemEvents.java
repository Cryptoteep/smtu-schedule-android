package com.korabel.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Перезагрузка, перевод часов, смена часового пояса, обновление приложения —
 * всё это сбрасывает будильник виджетов, поэтому его нужно завести заново.
 */
public final class SystemEvents extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        Widgets.poke(ctx);
    }
}
