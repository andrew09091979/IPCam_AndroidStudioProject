package com.ipcam.service;

import java.util.TimerTask;

import android.util.Log;

public class TaskTimeoutHandler extends TimerTask
{
    private final String TAG = "IPCam";
    private int jobID = 0;

	public TaskTimeoutHandler(int ID)
	{
		jobID = ID;
	}

	@Override
	public void run() 
	{
        Log.d(TAG, "JobTimeoutHandler: run started");	
	}

}
