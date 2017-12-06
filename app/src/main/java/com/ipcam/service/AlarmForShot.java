package com.ipcam.service;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.internalevent.InternalEvent;
import com.ipcam.internalevent.InternalEventInfoImpl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmForShot extends BroadcastReceiver
{
	private final String TAG = "IPCam";
	private AsyncExecutor<IInternalEventInfo> photoEventHandler = null;

    public AlarmForShot()
    {
    	Log.d(TAG, "AlarmForShot: constructor with no parameters");
    	photoEventHandler = IPCam.getInternalEventHandler();
    }

    public AlarmForShot(AsyncExecutor<IInternalEventInfo> phi)
    {
    	Log.d(TAG, "AlarmForShot: constructor with IPhotographer parameter");
    	photoEventHandler = phi;
    }

	@Override
	public void onReceive(Context context, Intent intent)
	{
		Log.d(TAG, "AlarmForShot: onReceive");

		if (photoEventHandler != null)
		{
    	    Log.d(TAG, "AlarmForShot: calling executeAsync for message NEED_TO_COLLECT_DATA");
    	    photoEventHandler.executeAsync(new InternalEventInfoImpl(InternalEvent.NEED_TO_COLLECT_DATA));
		}
		else
		{
			Log.d(TAG, "AlarmForShot: photoEventHandler is null");
		}
	}
}
