package com.ipcam.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.Math;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.internalevent.InternalEvent;
import com.ipcam.internalevent.InternalEventInfoImpl;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class SensorsReceiver implements SensorEventListener
{
	private final String TAG = "SensorsReceiver";
    private final int ACCEL_OFFSET = 0;
    private final int LIGHT_OFFSET = 3;
    private final int MAGN_FIELD_OFFSET = 4;
    private final int BATTERY_CAPC_OFFSET = 7;
    private final int ACCEL_DELTA_THRESHOLD = 3;
    private final int MAGN_FIELD_DELTA_THRESHOLD = 30;
    private final int EVENT_REPORT_COOLTIME_MS = 30000; 

	private SensorManager sensorManager = null;
	private List<Sensor> channels = null;
    private float values[] = null;
	private AsyncExecutor<IInternalEventInfo> eventHandler = null;
    private boolean bEventAlreadyReported = true;
	private Timer eventReportCooltime = null;
    
	public SensorsReceiver(Context context, AsyncExecutor<IInternalEventInfo> evh)
	{
		eventHandler = evh;
		channels = new ArrayList<Sensor>();

		if (sensorManager == null)
		{
	        sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
	        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
	        
	        if(sensors.size() > 0)
	        {
	        	for (Sensor sensor : sensors) 
	        	{
	        		switch(sensor.getType())
	        		{
	        		case Sensor.TYPE_ACCELEROMETER:
	        		case Sensor.TYPE_MAGNETIC_FIELD:
	        		case Sensor.TYPE_LIGHT:	
//	        			devCfg.insertChan(sensor.getName(), sensor.getMaximumRange(), -sensor.getMaximumRange());
	        			Log.d(TAG, "SensorsReceiver: sensor name = " + sensor.getName() + "  maxRange = " 
	        			                               + Float.toString(sensor.getMaximumRange()));
	        			channels.add(sensor);
	        		break;
	        		default:
	        			break;
	        		}
				}
	        	values = new float[sensors.size()];

	        	for(int i = 0; i < sensors.size(); ++i)
	        	{
	        		values[i] = 0;
	        	}
	        }
		}
	}

	public boolean registerAllListeners()
	{
		boolean res = false;
		
		if (channels != null)
		{
	    	for (Sensor sensor : channels) 
	    	{
	    		if (sensor != null && sensorManager != null)
	    		{
	    			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
	    		}
	    	}
	    	res = true;
		}
        //Don't send movement event alarm when service is started and device is set up on its place  
    	eventReportCooltime = new Timer();
    	EventCooltimeHandler handleEventReportCooltime = new EventCooltimeHandler();
    	eventReportCooltime.schedule(handleEventReportCooltime, EVENT_REPORT_COOLTIME_MS);

		return res;
	}
	
	public boolean unregisterAllListeners()
	{
		boolean res = false;
		
		Log.d(TAG, "SensorsReceiver: unregisterAllListeners");
		
		if (sensorManager != null)
			sensorManager.unregisterListener(this);
//		if (locationManager != null)
//		{
//			locationManager.removeUpdates(this);
//			locationManager.removeGpsStatusListener(this);
//		}
		
		return res;
	}
	
	@Override
	public void onSensorChanged(SensorEvent event)
	{
		float [] vals = event.values;

		switch(event.sensor.getType())
		{
			case Sensor.TYPE_ACCELEROMETER:
			{
				setValues(vals, ACCEL_OFFSET, 3, ACCEL_DELTA_THRESHOLD, "accelerometer");
			}
			break;
			case Sensor.TYPE_LIGHT:
			{
                SetLightValue(vals[0]);
				//handler.post(service.new ValuesChangedLightReact(event.values[0]));
			}
			break;
			case Sensor.TYPE_MAGNETIC_FIELD:
			{
				setValues(vals, MAGN_FIELD_OFFSET, 3, MAGN_FIELD_DELTA_THRESHOLD, "Magnetic field");
			}
			break;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		Log.d(TAG, "SensorsReceiver: onAccuracyChanged");
	}

	public void SetLightValue(float val)
	{
		values[LIGHT_OFFSET] = val;
	}	
	
	public void SetBatteryCapacityValue(int val)
	{
		values[BATTERY_CAPC_OFFSET] = (float)val; 
	}

    private void setValues(float vals[], int offset, int channelsNum, float threshold, String sensorName)
    {
		int j = 0;
        boolean initialSetupIsNeeded = true;
        
        for (int i = 0; i < channelsNum; ++i)
        {
        	if (values[offset + i] != 0)
        	{
        		initialSetupIsNeeded = false;
        		break;
        	}
        }
		if (initialSetupIsNeeded)
		{
			//initial set up
			Log.d(TAG, "Initial setup of standard values for " + sensorName + " sensor");			
			for (int i = 0; i < channelsNum; ++i)
			{
				values[offset + i] = vals[i];
			}
		}
		else
		{
			boolean isThresholdExceeded = false;

			for (int i = 0; i < channelsNum; ++i)
			{
				if (Math.abs(values[offset + i] - vals[i]) > threshold)
				{
					isThresholdExceeded = true;
					break;
				}
			};
			if (isThresholdExceeded && !isEventReported())
			{
				Log.d(TAG, "Device movement detected by " + sensorName + " sensor");
				IInternalEventInfo info = new InternalEventInfoImpl(InternalEvent.NOTIFY_USER_URGENTLY);
				info.setHeadline("Device movement detected by " + sensorName + " sensor");
				String msg = new String();

                for (int i = 0; i < channelsNum; ++ i)
                {
                	msg += "Initial value = " + Float.toString(values[offset + i]) + 
     					   " => current value = " + Float.toString(vals[i]) + " => delta = "+ Float.toString(Math.abs(values[offset + i] - vals[i])) + "\n";
                }
				info.setMessage(msg);
				Log.d(TAG, msg);
				reportEvent(info);

				for (int i = 0; i < channelsNum ; ++i)
				{
					values[offset + i] = vals[i];
				}				
			}
		}
    }

	private synchronized void reportEvent(IInternalEventInfo info)
	{
		Log.d(TAG, "SensorsReceiver: reportEvent");

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
	        Log.d(TAG, "EventTimeoutHandler: started, bEventAlreadyReported setting to false");
	        resetEventFlag();
		}
	}	
}
