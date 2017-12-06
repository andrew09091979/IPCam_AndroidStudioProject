package com.ipcam.photo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.internalevent.InternalEvent;
import com.ipcam.internalevent.InternalEventInfoImpl;
import com.ipcam.task.ITask;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Photographer extends BroadcastReceiver implements ITask<IInternalEventInfo>
{
	private final String TAG = "Photographer";
    private final int PHOTO_TIMEOUT_MS = 30000; 

	private Context context = null;
    private boolean bInShotProcess = false;
	private static Activity photographyActivity = null;
	private AsyncExecutor<IInternalEventInfo> eventHandler = null;
	private IInternalEventInfo info = null;
	private Timer photographyTimer = null;
//	private Object handleResultAndCleanupLock = new Object();
//	private PhotoResultIntentReceiver photoResultIntentReceiver = null;
	private int pictureQuality = 1;
//	private File log = null;
    private FileOutputStream logfos = null;

    public Photographer(Context cont, AsyncExecutor<IInternalEventInfo> evh)
    {
    	context = cont;
    	eventHandler = evh;
    	writeToLog("Photographer: Photographer ctor");
    }

    synchronized
    private boolean lock()
    {
    	if (!bInShotProcess)
    	{
    		writeToLog("Photographer: lock setting bInShotProcess = true");
            bInShotProcess = true;
    	    return true;
    	}
    	else
    	{
    	    return false;
    	}
    }

    synchronized
    private void unlock()
    {
		writeToLog("Photographer: unlock setting bInShotProcess = false");
        bInShotProcess = false;
    }

    @Override
	public void performTask(IInternalEventInfo i) 
	{
    	if (!lock())
    	{
    		writeToLog("Photographer: already taking picture, returning");
            Log.d(TAG, "Photographer: already taking picture, returning");
    		return;
    	}
    	info = i;
        pictureQuality = i.getParameter();
		writeToLog("Photographer: starting CameraActivity with pictureQuality = " + Integer.toString(pictureQuality));
        Log.d(TAG, "Photographer: starting CameraActivity with pictureQuality = " + Integer.toString(pictureQuality));
        Intent intent = new Intent(context, CameraActivity.class);
        intent.putExtra("Quality", pictureQuality);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        PhotoTimeoutHandler pth = new PhotoTimeoutHandler();
        photographyTimer = new Timer();
        photographyTimer.schedule(pth, PHOTO_TIMEOUT_MS);

        try
        {
        	context.startActivity(intent);
        }
        catch (Exception e)
        {
    		writeToLog("Photographer: startActivity threw an exception: " + e.getMessage());
        	e.printStackTrace();
        }
	}

    @Override
    public void stop()
    {
    	try
    	{
    		writeToLog("stop: closing logfos");
    		if (logfos != null)
			    logfos.close();
    		else
    			Log.e(TAG, "stop: logfos is null");
		}
    	catch (IOException e)
    	{
			e.printStackTrace();
		}
/*
        if (info == null)
        	info = new InternalEventInfoImpl();

        info.removeAllFiles();
		info.setHeadline("Photographer is stopped");
		info.setMessage("Photographer is stopped");

     	if (eventHandler != null)
	    	eventHandler.handleEvent(InternalEvent.NOTIFY_USER, info);    	
        unlock();
*/
    }

	private void cleanup()
	{
		writeToLog("Photographer: cleanup");
		Log.d(TAG, "Photographer: cleanup");

        if (photographyActivity != null)
            photographyActivity.finish();
        else
        	Log.e(TAG, "Photographer: cleanup cannot call finish for activity, photographyActivity is null");

		writeToLog("Photographer: cleanup unlocking");
		Log.d(TAG, "Photographer: cleanup unlocking");
        unlock();
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
		Log.d(TAG, "Photographer: onReceive");
		writeToLog("Photographer: onReceive");

		if (photographyTimer != null)
		{
			writeToLog("Photographer: onReceive cancelling photography timer");
			Log.d(TAG, "Photographer: onReceive cancelling photography timer");
		    photographyTimer.cancel();
		    photographyTimer = null;
		}
		
		boolean takeShotResult = intent.getBooleanExtra("Result", false);
		
        if (info == null)
        {
        	info = new InternalEventInfoImpl(InternalEvent.NOTIFY_USER);
        }
        else
        {
        	info.setInternalEvent(InternalEvent.NOTIFY_USER);
        }

		if (takeShotResult)
		{
			String photoFilePath = intent.getStringExtra("PhotoFilePath");
			String photoFileName = intent.getStringExtra("PhotoFileName");
			writeToLog("Photographer: onReceive path in intent is " + photoFilePath);
			Log.d(TAG, "Photographer: onReceive path in intent is " + photoFilePath);

			info.setHeadline(photoFileName);
	        info.removeAllFiles();
			info.addFile(photoFilePath);
			info.setMessage("Shot " + photoFileName);
		}
		else
		{
			info.setHeadline("Can't take a photo");
	        info.removeAllFiles();
			info.setMessage("Activity reported: attempt to take a photo per timer has failed");
		}
		
		cleanup();
		
		if (eventHandler != null)
			eventHandler.executeAsync(info);
		else
		{
			writeToLog("eventHandler is null");
			Log.e(TAG, "eventHandler is null");
		}
	}

    private void dropCurrentPhoto()
    {
		writeToLog("Photographer: dropCurrentPhoto starting");

        if (info == null)
        	info = new InternalEventInfoImpl(InternalEvent.NO_BROADCAST_FROM_PHOTO_ACTIVITY);

		info.setHeadline("Can't take a photo");
        info.removeAllFiles();
		info.setMessage("Attempt to take a photo per timer has failed, no broadcast intent from activity");
		
		writeToLog("Photographer: dropCurrentPhoto calling cleanup");
	    
		cleanup();

	    if (eventHandler != null)
	    {
			writeToLog("Photographer: dropCurrentPhoto calling eventHandler.handleEvent");
		    eventHandler.executeAsync(info);
	    }
	    else
	    {
			writeToLog("Photographer: dropCurrentPhoto eventHandler is null");
	    	Log.e(TAG, "dropCurrentPhoto: eventHandler is null");
	    }
    }

    private void writeToLog(String msg)
    {
/*
    	if (log == null)
    	{
        	String logname = "log_" + CameraActivity.buildPhotoFileName();
	    	log = new File("/mnt/sdcard/MyCameraApp/" + logname + ".txt");
	
	    	try
	    	{
	    		log.createNewFile();
				logfos = new FileOutputStream(log);
				writeToLog("creating Photographer object");
			}
	    	catch (FileNotFoundException e)
			{
	            Log.e(TAG, "Photographer: exception while FileOutputStream creation: " + e.getMessage());
				e.printStackTrace();
			}
	    	catch (IOException e)
	    	{
	            Log.e(TAG, "Photographer: exception while File creation: " + e.getMessage());
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
*/
    }

	class PhotoTimeoutHandler extends TimerTask
	{
	    private final String TAG = "IPCam";

		@Override
		public void run() 
		{
			writeToLog("PhotoTimeoutHandler: started, calling dropCurrentPhoto");
	        Log.d(TAG, "PhotoTimeoutHandler: started, calling dropCurrentPhoto");
	        dropCurrentPhoto();
		}
	}

	class PhotoResultIntentReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent) 
		{
			
			
		}
		
	}
}