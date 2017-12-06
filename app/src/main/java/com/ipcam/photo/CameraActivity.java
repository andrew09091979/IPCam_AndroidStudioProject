package com.ipcam.photo;
import com.example.ipcam.R;
import com.example.ipcam.R.id;
import com.example.ipcam.R.layout;
import com.ipcam.photo.Photographer.PhotoTimeoutHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

public class CameraActivity extends Activity
{
	private final String TAG = "Photographer";
    private final String shotIntentName = "com.example.ipcam.phototaken";
    
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private final int PHOTO_TIMEOUT_MS = 10000; 
	Camera cam = null;
    CameraPreview preview = null;
    private PowerManager powerManager = null;
    private boolean bInShotProcess = false;
    private boolean bResultHandled = false;
	private Timer photographyTimer = null;
    private int pictureQuality = 3; 
    private Object handleResultLock = new Object();

    // Add a listener to the Capture button 
    private Camera.PictureCallback pictureTakenCallback = new Camera.PictureCallback()
    {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) 
        {
        	Log.d(TAG, "onPictureTaken stopping timer");

        	if (photographyTimer != null)
        	{
        		photographyTimer.cancel();
        	}
	        Log.d(TAG, "onPictureTaken releasing cam");
	        cam.release();
	        cam = null;
        	Log.d(TAG, "onPictureTaken calling handleResult");
        	handleResult(data);
        }
    };	

    private Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback()
    {
		@Override
		public void onShutter()
		{
	        Log.d(TAG, "ShutterCallback: onShutter entered");
	        Log.d(TAG, "ShutterCallback: onShutter finished");
		}
	};

    synchronized
    private boolean lock()
    {
    	if (!bInShotProcess)
    	{
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
        bInShotProcess = false;
    }
    private void releaseCam()
    {
    	Log.d(TAG, "CameraActivity: releaseCam");

    	if (cam != null)
    	{
        	//Log.d(TAG, "CameraActivity: releaseCam: calling stopPreview");
		    //cam.stopPreview();
        	Log.d(TAG, "CameraActivity: releaseCam: calling release");
            cam.release();
		    cam = null;
		    preview = null;
    	}
    	else
    	{
        	Log.e(TAG, "CameraActivity: releaseCam: cam is null");
    	}
    }
    @Override
    protected void onPause()
    {
    	super.onPause();
    	Log.d(TAG, "CameraActivity: onPause");

//    	if (cam != null)
//    	{
//    		Log.d(TAG, "onPause: releasing camera");
//    		cam.release();
//    		cam = null;
//    	}
    }
    @Override 
    protected void onStop()
    {
    	super.onStop();
    	Log.d(TAG, "CameraActivity: onStop");

    	if (photographyTimer != null)
    	{
    		Log.d(TAG, "CameraActivity: onStop: cancelling photography timer");
    		photographyTimer.cancel();
    		photographyTimer = null;
    	}
    	if (cam != null)
    	{
    		Log.d(TAG, "CameraActivity: onStop: calling releaseCam");
    		releaseCam();
    		Log.d(TAG, "CameraActivity: onStop: calling handleResult");    		
    		handleResult(null);
    	}
    }

    @Override
    protected void onDestroy()
    {
    	super.onDestroy();
		Log.d(TAG, "CameraActivity: onDestroy");

    	if (photographyTimer != null)
    	{
    		Log.d(TAG, "CameraActivity: onDestroy: cancelling photography timer");
    		photographyTimer.cancel();
    		photographyTimer = null;
    	}
    	if (cam != null)
    	{
    		Log.d(TAG, "CameraActivity: onDestroy: releaseCam");
    		releaseCam();
    		Log.d(TAG, "CameraActivity: onDestroy: calling handleResult");   
    		handleResult(null);
    	}
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		Log.d(TAG, "CameraActivity: onCreate");
		setContentView(R.layout.camera);
		Intent intent = this.getIntent();
		pictureQuality = intent.getIntExtra("Quality", 3);
		Log.d(TAG, "CameraActivity: onCreate: extracted pictureQuality = " + Integer.toString(pictureQuality));		

		Window w = this.getWindow();
		w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				   WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
				   WindowManager.LayoutParams.FLAG_FULLSCREEN);

	}

	@Override
	protected void onResume()
	{
		super.onResume();
    	Log.d(TAG, "CameraAcivity: onResume");
        boolean shotThreadStarted = false;

    	if (!lock())
    	{
    		Log.d(TAG, "CameraActivity: onResume: finishing - shooting process already started");
    		return;
    	}
    	bResultHandled = false;

        if (cam == null)
		{
			Log.d(TAG, "CameraActivity: onResume: calling getCameraInstance");
			cam = getCameraInstance();
		}
		if (cam != null)
		{

			Camera.Parameters camParameters = cam.getParameters();
			List<Camera.Size> camSizes = camParameters.getSupportedPictureSizes();
            int counter = 0;
            int camSizeIndexToUse = 0;

			for (Camera.Size camSize : camSizes)
			{
                Log.d(TAG, "CameraActivity: onResume: camSize #" + Integer.toString(counter) +
                		   " height = " + Integer.toString(camSize.height) +
                		   " width = " + Integer.toString(camSize.width));
            	Log.d(TAG, "current = " + Integer.toString(camSizes.get(counter).width * camSizes.get(counter).height) +
            			" chosen = " + Integer.toString(camSizes.get(camSizeIndexToUse).width * camSizes.get(camSizeIndexToUse).height));                
                if (pictureQuality == 1)
                {
	                if (camSizes.get(counter).width * camSizes.get(counter).height >
	                    camSizes.get(camSizeIndexToUse).width * camSizes.get(camSizeIndexToUse).height)
	                {
	                	camSizeIndexToUse = counter; 
	                }
                } 
                else if (pictureQuality == 2)
                {
                	camSizeIndexToUse = camSizes.size()/2;
                	break;
                }
                else if (pictureQuality == 3)
                {
	                if (camSizes.get(counter).width * camSizes.get(counter).height <
                        camSizes.get(camSizeIndexToUse).width * camSizes.get(camSizeIndexToUse).height)
                    {
                	    camSizeIndexToUse = counter; 
                    }
                }
                else
                {
                	break;
                }
                ++counter;
			}
            Log.d(TAG, "CameraActivity: onResume: chosen camSize #" + Integer.toString(camSizeIndexToUse) +
         		   " height = " + Integer.toString(camSizes.get(camSizeIndexToUse).height) +
         		   " width = " + Integer.toString(camSizes.get(camSizeIndexToUse).width));			
			camParameters.setPictureSize(camSizes.get(camSizeIndexToUse).width,
					                     camSizes.get(camSizeIndexToUse).height);
			cam.setParameters(camParameters);

			preview = new CameraPreview(this, cam);

			Log.d(TAG, "CameraActivity: onResume: creating frmLayout");
			try
			{
				FrameLayout frmLayout = (FrameLayout) findViewById(R.id.camera_preview);
				
				if ((frmLayout != null) && (preview != null))
				{
					Log.d(TAG, "CameraActivity: onResume: calling addView");
					frmLayout.addView(preview);
			    	Log.d(TAG, "CameraAcivity: onResume creating and starting shotThread");		
					Thread shotThrd = new shotThread();
					shotThrd.start();
					shotThreadStarted = true;
				}
				else
				{
					Log.e(TAG, "CameraActivity: onResume: frmLayout or preview is null");
				}				
			}
			catch(Exception e)
			{
				Log.e(TAG, "CameraActivity: onResume: creating frmLayout error: " + e.getMessage());
			}
		}
		else
		{
			Log.e(TAG, "CameraActivity: onResume: cam is null, calling handleResult with data null");
		}
		if (!shotThreadStarted)
		{
			Log.e(TAG, "CameraAcivity: shot thread is not started, calling handleResult with data null");
			handleResult(null);
		}
    	Log.d(TAG, "CameraAcivity: onResume finish");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		return true;
	}

	public static Camera getCameraInstance()
	{
	    Camera c = null;

	    try
	    {
	    	Log.d("IPCam", "CameraActivity: getCameraInstance: opening camera");
	        c = Camera.open(); // attempt to get a Camera instance
	    }
	    catch (Exception e)
	    {
	    	Log.e("IPCam", "CameraActivity: getCameraInstance: can't open camera, error: " + e.getMessage());
	        // Camera is not available (in use or does not exist)
	    }
	    return c; // returns null if camera is unavailable
	}

    /** Create a file Uri for saving an image or video */
    void take_shot()
    {
		Log.d(TAG, "CameraActivity:  take_shot");
		boolean takePictureCalled = false;

    	if (cam != null)
    	{
    		if (pictureTakenCallback != null)
    		{
    	        PhotoTimeoutHandler pth = new PhotoTimeoutHandler();
    	        photographyTimer = new Timer();
    			Log.d(TAG,"CameraActivity: take_shot: starting timer");
    	        photographyTimer.schedule(pth, PHOTO_TIMEOUT_MS);

    			Log.d(TAG,"CameraActivity: take_shot: calling takePicture");
    			cam.takePicture(shutterCallback, null, pictureTakenCallback);
    			takePictureCalled = true;

    			Log.d(TAG, "take_shot finished");
    		}
    		else
    		{
    			Log.e(TAG, "CameraActivity: take_shot: pictureTakenCallback is null");
    		}
    	}
    	else
    	{
    		Log.e(TAG,"CameraActivity: take_shot: cam is null");
    	}

    	if (!takePictureCalled)
		    handleResult(null);
    }

    private boolean handleResult(byte[] data)
    {
    	Intent shotTaken = new Intent(shotIntentName);
        boolean result = false;
        Log.d(TAG, "CameraActivity: handleResult started");

        if (bResultHandled)
        {
        	Log.d(TAG, "CameraActivity: handleResult: result already handled, finishing");
        	return false;
        }
    	Log.d(TAG, "CameraActivity: handleResult: result is not handled, proceeding");
        bResultHandled = true;

    	if (data != null)
    	{
    		Log.d(TAG, "CameraActivity: handle result data is not null, creating file");
	        File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
	
	        try 
	        {
	        	Log.d(TAG, "onPictureTaken: writing image");
	            FileOutputStream fos = new FileOutputStream(pictureFile);
	            
	            fos.write(data);
	            fos.close();
	            result = true;
		        shotTaken.putExtra("PhotoFilePath", pictureFile.getPath());
		        shotTaken.putExtra("PhotoFileName", pictureFile.getName());
	        } 
	        catch (FileNotFoundException e) 
	        {
	            Log.e(TAG, "File not found: " + e.getMessage());
	        } 
	        catch (IOException e) 
	        {
	            Log.e(TAG, "Error accessing file: " + e.getMessage());
	        }
	        catch (Exception e)
	        {
	        	Log.e(TAG, "Exception while writing file: " + e.getMessage());
	        }
    	}
    	else
    	{
    		Log.e(TAG, "CameraActivity: handle result data is null");
    	}

		Log.d(TAG, "CameraActivity::handleResult calling unlock, sendBroadcast and finish");
        unlock();
        shotTaken.putExtra("Result", result);
        sendBroadcast(shotTaken);
        finish();

		return result;
    }

    private class shotThread extends Thread
    {
    	@Override
    	public void run()
    	{
    		try 
    		{
				Thread.sleep(3000);
				take_shot();
			} 
    		catch (InterruptedException e) 
    		{
				e.printStackTrace();
			}
    	}
    }

	class PhotoTimeoutHandler extends TimerTask
	{
	    private final String TAG = "IPCam";

		@Override
		public void run() 
		{
    		photographyTimer = null;
            releaseCam();
            handleResult(null);
		}
	}

    private static Uri getOutputMediaFileUri(int type)
    {
          return Uri.fromFile(getOutputMediaFile(type));
    }

    private static File getOutputMediaFile(int type)
    {
    	String TAG = "IPCam";
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
    	Log.d(TAG, "CameraActivity: getOutputMediaFile started");
    	Log.d(TAG, "CameraActivity: creating dir: " + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) +"/MyCameraApp");
        File mediaStorageDir = new File("/mnt/sdcard/DCIM/MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists())
        {
            if (! mediaStorageDir.mkdirs())
            {
                Log.d(TAG, "CameraActivity: failed to create directory");
                return null;
            }
        }

        // Create a media file name
        File mediaFile;
    	String timeStamp = buildPhotoFileName();
    
        if (type == MEDIA_TYPE_IMAGE)
        {
        	Log.d(TAG, "CameraActivity::getOutputMediaFile creating file:" + mediaStorageDir.getPath() + File.separator + "IMG_"+ timeStamp);
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_"+ timeStamp);
            try 
            {
            	mediaFile.createNewFile();
            }
            catch (IOException e)
            {
            	Log.d(TAG, "CameraActivity::getOutputMediaFile: IOException while creating file");
            }
        } 
        else if(type == MEDIA_TYPE_VIDEO) 
        {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_"+ timeStamp + ".mp4");
        } 
        else 
        {
            return null;
        }

        return mediaFile;
    }

    public static String buildPhotoFileName()
    {
		Calendar date = Calendar.getInstance();

		String filename = String.format("%02d.%02d.%04d %02d.%02d.%02d",date.get(Calendar.DATE),
						  (date.get(Calendar.MONTH) + 1), date.get(Calendar.YEAR), date.get(Calendar.HOUR_OF_DAY), 
						  date.get(Calendar.MINUTE), date.get(Calendar.SECOND)) + ".jpg";
		return filename;
    }	
}