package com.ipcam.service;

import com.example.ipcam.R;
import com.ipcam.helper.ServiceStartIntentFactory;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;

public class BootCompleteReceiver extends BroadcastReceiver
{
	@Override
	public void onReceive(Context arg0, Intent arg1) 
	{
        Intent intent = ServiceStartIntentFactory.getServiceStartingIntentWithInfoFromPrefs(arg0, "BootCompleteReceiver");
    	arg0.startService(intent);		
	}
}