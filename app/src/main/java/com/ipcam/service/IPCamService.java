package com.ipcam.service;

import java.util.Timer;
import java.util.TimerTask;

import com.ipcam.helper.ServiceStartIntentFactory;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class IPCamService extends Service
{
    private final String TAG = "IPCamService";
    private final int ON_START_AFTER_ON_CREATE_TIMEOUT = 10000;
    private IPCam ipcam = null;
    private Timer onStartAfterOnCreate = null;
    private OnStartCommandWaitingTimeoutHandler onStartCommandWaitingTimeoutHandler = null;
    private Object startLock = new Object();
    private boolean bServiceActivated = false;

	@Override
	public IBinder onBind(Intent intent) 
	{
		return null;
	}

	@Override
	public void onCreate()
	{
    	Log.d(TAG, "onCreate");
    	Log.d(TAG, "onCreate: starting onStartCommand waiting timer");
    	onStartCommandWaitingTimeoutHandler = new OnStartCommandWaitingTimeoutHandler();
    	onStartAfterOnCreate = new Timer();
    	onStartAfterOnCreate.schedule(onStartCommandWaitingTimeoutHandler, ON_START_AFTER_ON_CREATE_TIMEOUT);
        ipcam = new IPCam();
	}

    @Override
    public int onStartCommand(Intent intent, int startId, int arg) 
    {
    	synchronized(startLock)
    	{
    		if (!bServiceActivated)
    		{
    			bServiceActivated = true;
		        Log.d(TAG, "onStartCommand: canceling timer, activating service");
		    	onStartAfterOnCreate.cancel();
		        Toast.makeText(this, "IPCamService starting", Toast.LENGTH_SHORT).show();
		        ipcam.activate(intent, getResources(), getApplicationContext());
    		}
			else
			{
				Log.e(TAG, "onStartCommand: serivce already activated");
			}
    	}
    	return START_STICKY;
    }

	@Override
    public void onDestroy() 
    {
        Toast.makeText(this, "IPCamService stopping", Toast.LENGTH_SHORT).show();
        ipcam.deactivate();
        bServiceActivated = false;

        super.onDestroy();
    }
	class OnStartCommandWaitingTimeoutHandler extends TimerTask
	{
	    private final String TAG = "IPCam";

		@Override
		public void run() 
		{
            Log.d(TAG, "OnStartCommandWaitingTimeoutHandler: run entered");

			synchronized(startLock)
			{
				if (!bServiceActivated)
				{
					bServiceActivated = true;
			        Log.d(TAG, "OnStartCommandWaitingTimeoutHandler: run: activating service");
					Intent intent = ServiceStartIntentFactory.getServiceStartingIntentWithInfoFromPrefs(getApplicationContext(),
							                                                                            "OnStartCommandWaitingTimeoutHandler");
					ipcam.activate(intent, getResources(), getApplicationContext());
				}
				else
				{
					Log.e(TAG, "OnStartCommandWaitingTimeoutHandler: run: serivce already activated");
				}
			}
		}
	}
}
