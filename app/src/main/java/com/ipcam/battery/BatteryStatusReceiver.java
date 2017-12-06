package com.ipcam.battery;

import java.util.Timer;
import java.util.TimerTask;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.internalevent.InternalEvent;
import com.ipcam.internalevent.InternalEventInfoImpl;
import com.ipcam.service.IPCam;
import com.ipcam.task.ITask;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

public class BatteryStatusReceiver extends BroadcastReceiver implements ITask<IInternalEventInfo>
{
    private final String TAG = "BatteryStatusReceiver";
    private final int EVENT_REPORT_COOLTIME_MS = 15000; 

    private AsyncExecutor<IInternalEventInfo> eventHandler = null;
    private Context context = null;
    private boolean bEventAlreadyReported = true;
	private Timer eventReportCooltime = null;

	public BatteryStatusReceiver(Context cont)
	{
		Log.d(TAG, "BatteryStatusReceier in constructor with Context parameter");		
		context = cont;
	}

	public BatteryStatusReceiver()
	{
		Log.d(TAG, "BatteryStatusReceier in constructor with no parameters");
		context = null;
	}

	@Override
	public void onReceive(Context context, Intent intent)
    {
		int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL;

        int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float)scale;
        String message = "BatteryStatusReceiver: onReceive: status = " + Integer.toString(status) +
				" isCharging = " + Boolean.toString(isCharging) +
				" chargePlug = " + Integer.toString(chargePlug) + 
				" usbCharge = " + Boolean.toString(usbCharge) +
				" acCharge = " + Boolean.toString(acCharge) +
				" level = " + Integer.toString(level) +
                " scale = " + Integer.toString(scale);
		Log.d(TAG, message);
		eventHandler = IPCam.getInternalEventHandler();

		if (eventHandler != null)
		{
			if (!isEventReported())
			{
				Log.d(TAG, "BatteryStatusReceiver: eventHandler is not null, sending event");
				IInternalEventInfo info = new InternalEventInfoImpl(InternalEvent.NOTIFY_USER);
				info.setHeadline("Battery status changed");
				info.setMessage(message);
	            reportEvent(info);
			}
			else
			{
				Log.e(TAG, "BatteryStatusReceiver: event is already reported");
			}
		}
		else
		{
			Log.e(TAG, "BatteryStatusReceiver: eventHandler is null");
		}
	}
	
	private synchronized void reportEvent(IInternalEventInfo info)
	{
		Log.d(TAG, "BatteryStatusReceiver: reportEvent");
		bEventAlreadyReported = true;
    	eventReportCooltime = new Timer();
    	EventCooltimeHandler handleEventReportCooltime = new EventCooltimeHandler();
    	eventReportCooltime.schedule(handleEventReportCooltime, EVENT_REPORT_COOLTIME_MS);

		eventHandler.executeAsync(info);
	}	
	private synchronized void resetEventFlag()
	{
		bEventAlreadyReported = false;
	}
	private synchronized boolean isEventReported()
	{
		return bEventAlreadyReported;
	}

	class EventCooltimeHandler extends TimerTask
	{
		@Override
		public void run() 
		{
	        Log.d(TAG, "BatteryStatusReceiver: EventTimeoutHandler: started, bEventAlreadyReported setting to false");
	        resetEventFlag();
		}
	}

	@Override
	public void performTask(IInternalEventInfo info)
	{
		float batteryLevel = 0f;
		if (info == null)
			info = new InternalEventInfoImpl(InternalEvent.RENEW_BATTERY_LEVEL);
		else
			info.setInternalEvent(InternalEvent.RENEW_BATTERY_LEVEL);

		batteryLevel = getBatteryLevel();
		info.setMessage(new String("Battery: " + batteryLevel + "%"));

		eventHandler = IPCam.getInternalEventHandler();
		eventHandler.executeAsync(info);
	}

	@Override
	public void stop()
	{
		
	}

	public float getBatteryLevel()
	{
		float result = -1f;

		if (context != null)
		{
		    Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

		    if((level != -1) && (scale != -1))
		    {
			    result = ((float)level / (float)scale) * 100.0f;
		    }
		}
		else
		{
			Log.e(TAG, "getBatteryLevel: context is null");
		}
		return result;
	}
}