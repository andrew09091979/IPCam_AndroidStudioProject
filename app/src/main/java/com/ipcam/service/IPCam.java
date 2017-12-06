package com.ipcam.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import com.example.ipcam.R;
import com.ipcam.adminconsole.AdminConsoleSender;
import com.ipcam.adminconsole.ConnectionHandler;
import com.ipcam.adminconsole.NetStateReceiver;
import com.ipcam.adminconsole.ServerConnector;
import com.ipcam.adminconsole.SocketIOStreamProvider;
import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.asyncio.AsyncMessageSender;
import com.ipcam.asyncio.IIOStreamProvider;
import com.ipcam.asyncio.ISender;
import com.ipcam.battery.BatteryStatusReceiver;
import com.ipcam.helper.ServiceStartIntentFactory;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.helper.FileName;
import com.ipcam.internalevent.FunctionsForInternalEvent;
import com.ipcam.internalevent.InternalEvent;
import com.ipcam.internalevent.InternalEventInfoImpl;
import com.ipcam.mailsender.SMTPParameters;
import com.ipcam.mailsender.SMTPSender;
import com.ipcam.mailsender.SSLIOStreamProvider;
import com.ipcam.photo.Photographer;
import com.ipcam.soundplayer.SoundPlayerImpl;
import com.ipcam.soundrecorder.SoundRecorderImpl;
import com.ipcam.task.ITask;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public class IPCam extends AsyncExecutor<IInternalEventInfo>
{
    private final String TAG = "IPCam";
    private final String shotIntentName = "com.example.ipcam.phototaken";

    private String PERIOD_TO_TAKE_PHOTO_SETTING_NAME;
    private String PHOTO_QUALITY_SETTING_NAME;
    private String MAIL_LOGIN_SETTING_NAME;
    private String MAIL_PASSWORD_SETTING_NAME;
    private String MAIL_PORTAL_SETTING_NAME;
    private String SMTP_SERVER_ADDR_SETTING_NAME;
    private String SMTP_SERVER_PORT_SETTING_NAME;
    private String MAIL_BOX_TO_SEND_TO_SETTING_NAME;
    private String STARTED_FROM_SETTING_NAME;

    private Map<InternalEvent, Method> internalEventHandlingMethods = null;
    private Map<String, String> settings = null;
    private ServerConnector serverConnector = null;
    private IntentFilter netIntentFilter = null;
    
    private Context context = null;
    private AlarmManager alarmManager = null;
    private PowerManager powerManager = null;
    private WakeLock screenLock = null;
    private PendingIntent intentToShot = null;
    private NetStateReceiver netStateReceiver = null;
    private AlarmForShot shotRecvr = null;
    private IntentFilter shotIntentFilter = null;
    private Photographer photographerInst = null;
    private AsyncExecutor<IInternalEventInfo> mailSenderInst = null;
    private AsyncExecutor<IInternalEventInfo> soundPlayerInst = null;
    private AsyncExecutor<IInternalEventInfo> soundRecorderInst = null;
    private AsyncExecutor<IInternalEventInfo> adminConsole = null;
    private ITask<IInternalEventInfo> batteryStatusReceiver = null;
    private SensorsReceiver sensorsReceiver = null;
    private static AsyncExecutor<IInternalEventInfo> internalEventHandler = null;
    private int pictureQualityIndex = 3;
    private int shotAlarmPeriod = 10;
    private String alarmAudioFile = "/mnt/sdcard/MyCameraApp/siren.mp3";
    private String batteryLevelStr = "Battery level: unknown%";
    private String localIPAddrStr = "unknown";

    private int debugWakeLockReleased = 0;
    private int debugWakeLockAcquired = 0;
    private String debugStartedFrom = null;
    private UncaughtExceptionHandler debugUEH = null;
/*
	private File log = null;
    private FileOutputStream logfos = null;
*/
    public IPCam()
    {
        debugUEH = new IPCamUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(debugUEH);
    }

    public static AsyncExecutor<IInternalEventInfo> getInternalEventHandler()
    {
    	return internalEventHandler;
    }

	public void activate(Intent intent, Resources res, Context cont)
	{
		context = cont;
		readStringsFromResources(res);

		if (intent != null)
		{
		    Log.d(TAG, "onStartCommand: calling readSettingsFromIntent");
		    ////writeToLog("onStartCommand: calling readSettingsFromIntent");
		    settings = readSettingsFromIntent(intent);
		}
		else
		{
			////writeToLog("onStartCommand: intent is null, reading settings from prefs");
			intent = ServiceStartIntentFactory.getServiceStartingIntentWithInfoFromPrefs(cont, "activateWithNullIntent");
			settings = readSettingsFromIntent(intent);
		}
		
		////writeToLog("activate: calling initMethodsMap");
		initMethodsMap();
		////writeToLog("activate: calling startAll");
		startAll(settings);
	}

    public void deactivate()
    {
    	Log.d(TAG, "deactivate");
        ////writeToLog("deactivate");

    	if (serverConnector != null)
    	{
    		Log.d(TAG, "deactivate: closing server socket");
            ////writeToLog("deactivate: closing serverConnector");
		    serverConnector.close();
		    serverConnector = null;
    	}
    	if (netStateReceiver != null)
    	{
            ////writeToLog("deactivate: unregistering netStateReceiver");
            context.unregisterReceiver(netStateReceiver);
    		netStateReceiver = null;
    	}
        if (shotRecvr != null)
        {
            ////writeToLog("deactivate: unregistering shotRecvr");
            context.unregisterReceiver(shotRecvr);
        }
        if (batteryStatusReceiver != null)
        {
            ////writeToLog("deactivate: setting batteryStatusReceiver to null");
        	batteryStatusReceiver = null;
        }        
		if (alarmManager != null)
		{
            ////writeToLog("deactivate: cancelling AlarmForShot");
            Intent intent1 = new Intent(context, AlarmForShot.class);
	        Log.d(TAG, "deactivate: creating PendingIntent");
	        intentToShot = PendingIntent.getBroadcast(context, 0, intent1, 0);
			alarmManager.cancel(intentToShot);
			intentToShot = null;
		}
        if (photographerInst != null)
        {
        	photographerInst.stop();
            ////writeToLog("deactivate: unregistering photographerInst");
        	Log.d(TAG, "Unregistering photographerInst");
        	context.unregisterReceiver((BroadcastReceiver)photographerInst);
        	photographerInst = null;
        }
        if (mailSenderInst != null)
        {
            ////writeToLog("deactivate: stopping mailSenderInst");
        	mailSenderInst.stop();
        	mailSenderInst = null;
        }
        if (soundPlayerInst != null)
        {
            ////writeToLog("deactivate: stopping soundPlayerInst");
        	soundPlayerInst.stop();
        	soundPlayerInst = null;
        }
        if (soundRecorderInst != null)
        {
        	////writeToLog("deactivate: stopping soundRecorderInst");
        	soundRecorderInst.stop();
        	soundRecorderInst = null;
        }
        if (sensorsReceiver != null)
        {
            ////writeToLog("deactivate: calling sensorsReceiver.unregisterAllListeners");
        	sensorsReceiver.unregisterAllListeners();
        	sensorsReceiver = null;
        }
        if (internalEventHandlingMethods != null)
            internalEventHandlingMethods = null;
        if (internalEventHandler != null)
            internalEventHandler = null;
/*
        if (logfos != null)
        {
			try
            {
				logfos.close();
			}
            catch (IOException e)
            {
				e.printStackTrace();
			}
        }
*/
    }

    private Map<String, String> readSettingsFromIntent(Intent intent)
    {
    	Map<String, String> settings = new HashMap<String, String>();
    	settings.put(PERIOD_TO_TAKE_PHOTO_SETTING_NAME, intent.getStringExtra(PERIOD_TO_TAKE_PHOTO_SETTING_NAME));
    	settings.put(PHOTO_QUALITY_SETTING_NAME, intent.getStringExtra(PHOTO_QUALITY_SETTING_NAME));
    	settings.put(MAIL_LOGIN_SETTING_NAME, intent.getStringExtra(MAIL_LOGIN_SETTING_NAME));
    	settings.put(MAIL_PASSWORD_SETTING_NAME, intent.getStringExtra(MAIL_PASSWORD_SETTING_NAME));
    	settings.put(MAIL_PORTAL_SETTING_NAME, intent.getStringExtra(MAIL_PORTAL_SETTING_NAME));
    	settings.put(SMTP_SERVER_ADDR_SETTING_NAME, intent.getStringExtra(SMTP_SERVER_ADDR_SETTING_NAME));
    	settings.put(SMTP_SERVER_PORT_SETTING_NAME, intent.getStringExtra(SMTP_SERVER_PORT_SETTING_NAME));
    	settings.put(MAIL_BOX_TO_SEND_TO_SETTING_NAME, intent.getStringExtra(MAIL_BOX_TO_SEND_TO_SETTING_NAME));
    	debugStartedFrom = intent.getStringExtra(STARTED_FROM_SETTING_NAME);
    	
    	return settings;
    }

    private void readStringsFromResources(Resources res)
    {
		PERIOD_TO_TAKE_PHOTO_SETTING_NAME = res.getString(R.string.photo_period_intent_extra_name);
		PHOTO_QUALITY_SETTING_NAME = res.getString(R.string.photo_quality_index_intent_extra_name);
		MAIL_LOGIN_SETTING_NAME = res.getString(R.string.mail_login_intent_extra_name);
		MAIL_PASSWORD_SETTING_NAME = res.getString(R.string.mail_password_intent_extra_name);
		MAIL_PORTAL_SETTING_NAME = res.getString(R.string.mail_portal_intent_extra_name);
		SMTP_SERVER_ADDR_SETTING_NAME = res.getString(R.string.smtp_server_addr_intent_extra_name);
		SMTP_SERVER_PORT_SETTING_NAME = res.getString(R.string.smtp_server_port_intent_extra_name);
		MAIL_BOX_TO_SEND_TO_SETTING_NAME = res.getString(R.string.mail_box_to_send_to_intent_extra_name);
		STARTED_FROM_SETTING_NAME = res.getString(R.string.started_from_intent_extra_name);
    }

    private void initMethodsMap()
    {
    	internalEventHandlingMethods = new HashMap<InternalEvent, Method>();

        ////writeToLog("initMethodsMap: filling internalEventHandlingMethods map");
    	try
    	{
			internalEventHandlingMethods.put(InternalEvent.NEED_TO_COLLECT_DATA,
					IPCam.class.getMethod("handleNeedToCollectData", new Class[]{IInternalEventInfo.class}));
			internalEventHandlingMethods.put(InternalEvent.NEED_TO_RECORD_VIDEO,
					IPCam.class.getMethod("handleNeedToRecordVideo", new Class[]{IInternalEventInfo.class}));
			internalEventHandlingMethods.put(InternalEvent.NEED_TO_RECORD_AMBIENT_SOUND,
					IPCam.class.getMethod("handleNeedToRecordAmbientSound", new Class[]{IInternalEventInfo.class}));
			internalEventHandlingMethods.put(InternalEvent.GET_CURRENT_VALUES,
					IPCam.class.getMethod("handleGetCurrentValues", new Class[]{IInternalEventInfo.class}));
			internalEventHandlingMethods.put(InternalEvent.RENEW_BATTERY_LEVEL,
					IPCam.class.getMethod("handleRenewBatteryLevel", new Class[]{IInternalEventInfo.class}));				
			internalEventHandlingMethods.put(InternalEvent.NO_BROADCAST_FROM_PHOTO_ACTIVITY,
					IPCam.class.getMethod("handleNoBroadcastFromPhotoActivity", new Class[]{IInternalEventInfo.class}));				
			internalEventHandlingMethods.put(InternalEvent.NOTIFY_USER,
					IPCam.class.getMethod("handleNeedToNotifyUser", new Class[]{IInternalEventInfo.class}));
			internalEventHandlingMethods.put(InternalEvent.NOTIFY_USER_URGENTLY,
					IPCam.class.getMethod("handleNeedToNotifyUserUrgently", new Class[]{IInternalEventInfo.class}));
			internalEventHandlingMethods.put(InternalEvent.NETWORK_CONNECTED,
					IPCam.class.getMethod("handleNetworkConnected", new Class[]{IInternalEventInfo.class}));
			internalEventHandlingMethods.put(InternalEvent.NETWORK_DISCONNECTED,
					IPCam.class.getMethod("handleNetworkDisconnected", new Class[]{IInternalEventInfo.class}));
			internalEventHandlingMethods.put(InternalEvent.TASK_COMPLETE,
					IPCam.class.getMethod("handleTaskComplete", new Class[]{IInternalEventInfo.class}));
			internalEventHandlingMethods.put(InternalEvent.ADMIN_CONNECTED,
					IPCam.class.getMethod("handleAdminConnected", new Class[]{IInternalEventInfo.class}));	
			internalEventHandlingMethods.put(InternalEvent.STOP_ASYNC_EXECUTOR,
					IPCam.class.getMethod("handleStopAsyncExecutor", new Class[]{IInternalEventInfo.class}));
//				Log.e(TAG, "IPCamService: onCreate internalEventHandlingMethods is " + internalEventHandlingMethods.toString());
		}
    	catch (NoSuchMethodException e)
    	{
    		////writeToLog("initMethodsMap: exception while adding reference to method into the map: " + e.getMessage());
    		Log.e(TAG, "initMethodsMap: exception while adding reference to method into the map: " + e.getMessage());
			e.printStackTrace();
		}
    }

	private void startAll(Map<String, String> settings)
	{
    	////writeToLog("startAll");

        if (netIntentFilter == null)
        {
        	////writeToLog("startAll: creating netIntentFilter");
        	netIntentFilter = new IntentFilter();
        	netIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        }
        if (shotIntentFilter == null)
        {
        	////writeToLog("startAll: creating shotIntentFilter");
        	shotIntentFilter = new IntentFilter();
        	shotIntentFilter.addAction(shotIntentName);
        }
    	if (photographerInst == null)
    	{
        	////writeToLog("startAll: creating photographerInst");
    		photographerInst = new Photographer(context, this);
    	}
    	if (soundPlayerInst == null)
    	{
    		soundPlayerInst = new SoundPlayerImpl(context, alarmAudioFile);
    	}
    	if (soundRecorderInst == null)
    	{
    		soundRecorderInst = new SoundRecorderImpl(this);
    	}
    	if (internalEventHandler == null)
    	{
        	////writeToLog("startAll: setting interalEventHandler to this");
    		internalEventHandler = this;
    	}
    	if (alarmManager == null)
    	{
    		Log.d(TAG, "initializing alarmManager");
        	////writeToLog("startAll: initializing alarmManager");
    		alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    	}
    	if (powerManager == null)
    	{
    	    Log.d(TAG, "initializing powerManager");
        	////writeToLog("startAll: initializing powerManager");
    		powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
    	}
    	if (sensorsReceiver == null)
    	{
    		Log.d(TAG, "initializing sensorsReceiver");
        	////writeToLog("startAll: initializing sensorsReceiver");
    		sensorsReceiver = new SensorsReceiver(context, this);
    		sensorsReceiver.registerAllListeners();
    	}

    	pictureQualityIndex = Integer.parseInt(settings.get(PHOTO_QUALITY_SETTING_NAME));
    	shotAlarmPeriod = Integer.parseInt(settings.get(PERIOD_TO_TAKE_PHOTO_SETTING_NAME));
//        Log.d(TAG, "startAll: pictureQualityIndex = " + Integer.toString(pictureQualityIndex) +
//        		   "  shotAlarmPeriod = " + Integer.toString(shotAlarmPeriod));
    	if (mailSenderInst == null)
    	{
        	////writeToLog("startAll: creating SMTPParameters");
    		SMTPParameters smtpParameters = new SMTPParameters(settings.get(MAIL_PORTAL_SETTING_NAME),
											    				settings.get(MAIL_LOGIN_SETTING_NAME),
											    				settings.get(MAIL_PASSWORD_SETTING_NAME),
											    				settings.get(MAIL_BOX_TO_SEND_TO_SETTING_NAME),
											    				settings.get(SMTP_SERVER_ADDR_SETTING_NAME),
											    				settings.get(SMTP_SERVER_PORT_SETTING_NAME));
        	////writeToLog("startAll: creating MailSenderImpl");
    		ISender<IInternalEventInfo> smtpSender = new SMTPSender(smtpParameters);
    		smtpSender.injectIOStreamProvider(new SSLIOStreamProvider(smtpParameters.getSmtpServerAddr(), smtpParameters.getSmtpServerPort()));
    		mailSenderInst = new AsyncMessageSender<IInternalEventInfo, IInternalEventInfo>(new FunctionsForInternalEvent(), smtpSender, this);
    	}
    	if (photographerInst != null)
    	{
        	////writeToLog("startAll: registering photographerInst as BroadcastReceiver");
        	context.registerReceiver((BroadcastReceiver)photographerInst, shotIntentFilter);
    	}
        if (netStateReceiver == null)
        {
        	////writeToLog("startAll: creating netStateReceiver");
        	netStateReceiver = new NetStateReceiver(this);
        	context.registerReceiver(netStateReceiver, netIntentFilter);
        }
        if (batteryStatusReceiver == null)
        {
        	////writeToLog("startAll: creating batteryStatusReceiver");
        	batteryStatusReceiver = new BatteryStatusReceiver(context);
        }
        if (alarmManager != null)
        {
            Log.d(TAG, "startAll: alarmManager is not null, creating Intent");
        	//writeToLog("startAll: alarmManager is not null, creating Intent");
            Intent intent1 = new Intent(context, AlarmForShot.class);
	        Log.d(TAG, "creating PendingIntent");
        	//writeToLog("startAll: creating PendingIntent");
	        intentToShot = PendingIntent.getBroadcast(context, 0, intent1, 0);
	        Log.d(TAG, "startAll: calling setRepeating");
        	//writeToLog("startAll: calling setRepeating");
	        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
	        		                  shotAlarmPeriod * 60 * 1000, intentToShot);
        }
        else
        {
        	//writeToLog("startAll: error alarmManager is null");
        	Log.e(TAG, "startAll: alarmManager is null");
        }
        if (powerManager != null)
        {
        	//writeToLog("startAll: powerManager is not null (nothing to do yet)");
        }
        //writeToLog("startAll: setting internalEventHandler to this");
    	internalEventHandler = this;
	}

	private void startTakingShot(IInternalEventInfo info)
	{
    	if (powerManager != null)
    	{
			Log.d(TAG, "IPCam: triggerTakingShot acquiring wake lock");
		    screenLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "IPCam");
	        screenLock.acquire();
	        debugWakeLockAcquired++;
	
			if (photographerInst != null)
			{
				Log.d(TAG, "IPCam: triggerTakingShot calling takePicture");
	
				if (info == null)
					info = new InternalEventInfoImpl(InternalEvent.NEED_TO_COLLECT_DATA);
	
				info.setWakeLock(screenLock);
				info.setParameter(pictureQualityIndex);

				photographerInst.performTask(info);
			}
			else
			{
	    		//writeToLog("IPCam: triggerTakingShot photographerInst is null");
				Log.e(TAG, "IPCam: triggerTakingShot photographerInst is null");
			}
    	}
    	else
    	{
    		//writeToLog("IPCam: triggerTakingShot powerManager is null");
    		Log.e(TAG, "IPCam: triggerTakingShot powerManager is null");
    	}
	}

	private void addServiceInfo(IInternalEventInfo info)
	{
		info.concatToMessage("\nDevice manufacturer: " + Build.MANUFACTURER + "; Device model: " + Build.MODEL);

		synchronized (batteryLevelStr)
		{
		    info.concatToMessage("\n" + batteryLevelStr);
		}
		info.concatToMessage("\n wakelock acquired = " + debugWakeLockAcquired + ", wakelock released = " + debugWakeLockReleased);
		info.concatToMessage("\nService started from: " + debugStartedFrom);
		info.concatToMessage("\nLocal IP address: " + localIPAddrStr);
		Log.d(TAG, "IPCam: addServiceInfo: message after adding information: " + info.getMessage());
	}

	@Override
	public void executor(IInternalEventInfo info)
	{
		InternalEvent ev = info.getInternalEvent();

		try
		{
			Log.d(TAG, "IPCam: handleEvent: " + ev);
			Method mth = internalEventHandlingMethods.get(ev);

			if (mth != null)
			{
			    Log.d(TAG, "IPCam: method found, invoking...");
			    mth.invoke(this, info);

			}
			else
			{
	    		//writeToLog("IPCam: internalEventHandlingMethods is " + internalEventHandlingMethods.toString());
	    		//writeToLog("IPCam: handler not found, calling initMethodsMap");
				Log.e(TAG, "IPCam: internalEventHandlingMethods is " + internalEventHandlingMethods.toString());
				Log.e(TAG, "IPCam: handler not found, calling initMethodsMap");
				initMethodsMap();
			}
		}
		catch (IllegalAccessException e)
		{
    		//writeToLog("IPCam: handleEvent: IllegalAccessException while invoking method from methods map: " + e.getMessage());
			Log.e(TAG, "IPCam: handleEvent: IllegalAccessException while invoking method from methods map: " + e.getMessage());
			e.printStackTrace();
		}
		catch (IllegalArgumentException e)
		{
    		//writeToLog("IPCam: handleEvent: IllegalArgumentException while invoking method from methods map: " + e.getMessage());
			Log.e(TAG, "IPCam: handleEvent: IllegalArgumentException while invoking method from methods map: " + e.getMessage());
			e.printStackTrace();
		}
		catch (InvocationTargetException e)
		{
    		//writeToLog("IPCam: handleEvent: InvocationTargetException while invoking method from methods map: " + e.getMessage());
			Log.e(TAG, "IPCam: handleEvent: InvocationTargetException while invoking method from methods map: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void handleNeedToCollectData(IInternalEventInfo info)
	{
		Log.d(TAG, "IPCam: handleNeedToTakePhoto");

        startTakingShot(info);

    	if (batteryStatusReceiver != null)
    	{
    		InternalEventInfoImpl info1 = new InternalEventInfoImpl(InternalEvent.NEED_TO_COLLECT_DATA);
    		batteryStatusReceiver.performTask(info1);
    	}
    	else
    	{
    		//writeToLog("IPCam: handleNeedToTakePhoto batteryStatusReceiver is null");
    		Log.e(TAG, "IPCam: handleNeedToTakePhoto batteryStatusReceiver is null");
    	}    	
	}

    public void handleNoBroadcastFromPhotoActivity(IInternalEventInfo info)
    {
		Log.d(TAG, "IPCam: handleNoBroadcastFromPhotoActivity");

        if (photographerInst != null)
        {
        	photographerInst.stop();

        	try
        	{
        		context.unregisterReceiver((BroadcastReceiver)photographerInst);
        	}
        	catch (Exception e)
        	{
        		//writeToLog("IPCam: handleNoBroadcastFromPhotoActivity exception while unregistering photographerInst: " + e.getMessage());
            	Log.e(TAG, "IPCam: handleNoBroadcastFromPhotoActivity exception while unregistering photographerInst: " + e.getMessage());
        	}
        	photographerInst = null;
        }
        else
        {
    		//writeToLog("IPCam: handleNoBroadcastFromPhotoActivity photographerInst is null");
        	Log.e(TAG, "IPCam: handleNoBroadcastFromPhotoActivity photographerInst is null");
        }
		photographerInst = new Photographer(context, this);
		context.registerReceiver((BroadcastReceiver)photographerInst, shotIntentFilter);

		addServiceInfo(info);
		info.setParameter(3);

		AsyncExecutor<IInternalEventInfo> resultNotifier = info.getResultNotifier();

		if (resultNotifier == null)
		{
	        if (mailSenderInst != null)
	        {
	        	mailSenderInst.executeAsync(info);
	        }
		}
		else
		{
			resultNotifier.executeAsync(info);
		}
    }

	public void handleNeedToNotifyUser(IInternalEventInfo info)
	{
		Log.d(TAG, "IPCam: handleNeedToNotifyUser");

		addServiceInfo(info);
		info.setParameter(5);
		AsyncExecutor<IInternalEventInfo> resultNotifier = info.getResultNotifier();

		if (resultNotifier == null)
		{
	        if (mailSenderInst != null)
	        {
	        	mailSenderInst.executeAsync(info);
	        }
		}
		else
		{
			resultNotifier.executeAsync(info);
		}
	}

	public void handleNeedToNotifyUserUrgently(IInternalEventInfo info)
	{
		Log.d(TAG, "IPCam: handleNeedToNotifyUserUrgently");

		addServiceInfo(info);
		info.setParameter(5);

		AsyncExecutor<IInternalEventInfo> resultNotifier = info.getResultNotifier();

		if (resultNotifier == null)
		{
	        if (mailSenderInst != null)
	        {
	        	mailSenderInst.executeAsync(info);
	        }
		}
		else
		{
			resultNotifier.executeAsync(info);
		}
        if (soundPlayerInst != null)
        {
        	soundPlayerInst.executeAsync(info);
        }
        else
        {
    		//writeToLog("IPCam: handleNeedToNotifyUserUrgently soundPlayerInst is null");
        	Log.e(TAG, "IPCam: handleNeedToNotifyUserUrgently soundPlayerInst is null");
        }
	}

	public void handleNetworkDisconnected(IInternalEventInfo info)
	{
    	if (serverConnector != null)
    	{
		    serverConnector.close();
		    serverConnector = null;
    	}
    	Log.d(TAG, "notConnected");
	}

	public void handleNetworkConnected(IInternalEventInfo info)
	{
		Log.d(TAG, "IPCam: handleNetworkConnected entered");

        InetAddress inetAddress = (InetAddress)info.getObject();
		Log.d(TAG, "IPCam: handleNetworkConnected inetAddress extracted from info");

        if (inetAddress != null)
        {
            localIPAddrStr = inetAddress.getHostAddress();
            Log.d(TAG, "handleNetworkConnected: inetAddress is not null, hostAddress:" + inetAddress.getHostAddress());
            //+ " hostName:" + inetAddress.getHostName());
            serverConnector = new ServerConnector(inetAddress, this);
            serverConnector.start();
        }
      	Log.d(TAG, "connected starting serverConnector info: headline = " + info.getHeadline() +
  			    "message = " + info.getMessage());

        if (mailSenderInst != null)
        {
        	mailSenderInst.executeAsync(info);
        }
	}

	public void handleRenewBatteryLevel(IInternalEventInfo info) 
	{
      	Log.d(TAG, "IPCam: handleRenewBatteryLevel message = " + info.getMessage());

        synchronized(batteryLevelStr)
        {
      	    batteryLevelStr = info.getMessage();
        }
	}

	public void handleTaskComplete(IInternalEventInfo info) 
	{
      	Log.d(TAG, "IPCam: handleTaskComplete result = " + info.getParameter());

		if (info.getWakeLock() != null)
		{
			if (info.getWakeLock().isHeld())
			{
				Log.d(TAG, "IPCam: handleTaskComplete: releasing wakelock");
                info.getWakeLock().release();
				debugWakeLockReleased++;
			}
			else
			{
				Log.d(TAG, "IPCam: handleTaskComplete: no need to release wakelock as it is not held");
			}
		}
		else
		{
    		//writeToLog("IPCam: handleTaskComplete screenLock is null");
			Log.e(TAG, "IPCam: handleTaskComplete screenLock is null");
		}
	}

	public void handleAdminConnected(IInternalEventInfo info)
	{
      	Log.d(TAG, "IPCam: handleAdminConnected");
      	Socket sock = (Socket)info.getObject();
      	Log.d(TAG, "IPCam: handleAdminConnected info message = " + info.getMessage());

      	Log.d(TAG, "IPCam: handleAdminConnected: starting ConnectionHandler");
/*
		ConnectionHandler ch = new ConnectionHandler(sock, this);
		ch.start();
*/

      	IIOStreamProvider sockIOProvider = new SocketIOStreamProvider(sock);
   		ISender<IInternalEventInfo> adminConsoleSender = new AdminConsoleSender();
   		adminConsoleSender.injectIOStreamProvider(new SocketIOStreamProvider(sock));
   		adminConsole = new AsyncMessageSender<IInternalEventInfo, IInternalEventInfo>(new FunctionsForInternalEvent(), adminConsoleSender, this);
        adminConsole.executeAsync(info);

	}

	public void handleNeedToRecordAmbientSound(IInternalEventInfo info)
	{
		Log.d(TAG, "IPCam: handleNeedToRecordAmbientSound");

		if (soundRecorderInst != null)
		{
			soundRecorderInst.executeAsync(info);
		}
	}

	public void handleNeedToRecordVideo(IInternalEventInfo info)
	{
		Log.d(TAG, "IPCam: handleNeedToRecordVideo");
	}

	public void handleGetCurrentValues(IInternalEventInfo info)
	{
		Log.d(TAG, "IPCam: handleGetCurrentValues");
	}

	public void handleStopAsyncExecutor(IInternalEventInfo info)
	{
		Log.d(TAG, "IPCam: handleStopAsyncExecutor");
		AsyncExecutor<IInternalEventInfo> asyncExecutorToStop = info.getResultNotifier();

		if (asyncExecutorToStop != null)
		{
			asyncExecutorToStop.stop();
		}
	}
/*
    private void writeToLog(String msg)
    {
    	File logDirectory = new File(Environment.getExternalStorageDirectory().getPath() + "/MyCameraApp/");

    	if (!logDirectory.exists())
    	{
    		if(!logDirectory.mkdirs())
    		{
    			return;
    		}
    	}
    	if (log == null)
    	{
        	String logname = "IPCamServicelog_" + CameraActivity.buildPhotoFileName();
	    	log = new File("/mnt/sdcard/MyCameraApp/" + logname + ".txt");
	
	    	try
	    	{
	    		log.createNewFile();
				logfos = new FileOutputStream(log);
			}
	    	catch (FileNotFoundException e)
			{
	            Log.e(TAG, "IPCam: exception while FileOutputStream creation: " + e.getMessage());
				e.printStackTrace();
			}
	    	catch (IOException e)
	    	{
	            Log.e(TAG, "IPCam: exception while File creation: " + e.getMessage());
				e.printStackTrace();
			}
    	}
		try
		{
			msg += "\n";
			logfos.write(msg.getBytes());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
    }
*/
    public class IPCamUncaughtExceptionHandler implements UncaughtExceptionHandler
    {

		@Override
		public void uncaughtException(Thread thread, Throwable ex)
		{
			String uncaughtExceptionlogname = "UncaughtExcpt_" + FileName.DateTimeFileName() + ".txt";
	    	File uncaughtExceptionlog = new File(Environment.getExternalStorageDirectory().getPath() + "/MyCameraApp/" + uncaughtExceptionlogname);
	        FileOutputStream uncaughtExceptionlogfos = null;

	    	try
	    	{
	    		uncaughtExceptionlog.createNewFile();
	    		uncaughtExceptionlogfos = new FileOutputStream(uncaughtExceptionlog);
	    		String msg = ex.getMessage() + "\n";
	    		uncaughtExceptionlogfos.write(msg.getBytes());
	    		StackTraceElement stackTraceElements[] = ex.getStackTrace();

	    		for(StackTraceElement stackTraceElement : stackTraceElements)
	    		{
	    			msg = stackTraceElement.toString() + "\n";
		    		uncaughtExceptionlogfos.write(msg.getBytes());	    			
	    		}
			}
	    	catch (FileNotFoundException e)
			{
	            Log.e(TAG, "IPCam: exception while FileOutputStream creation: " + e.getMessage());
				e.printStackTrace();
			}
	    	catch (IOException e)
	    	{
	            Log.e(TAG, "IPCam: exception while File creation: " + e.getMessage());
				e.printStackTrace();
			}
		}
    	
    }
}
