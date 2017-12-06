package com.ipcam.photo;

import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback 
{
	private final String TAG = "Photographer";
    private SurfaceHolder mHolder;
    private Camera mCamera;

    public CameraPreview(Context context, Camera camera) 
    {
        super(context);
        mCamera = camera;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        
        if (mHolder != null)
        {
        	Log.d(TAG,"CameraPreview: adding callback");
	        mHolder.addCallback(this);
	        // deprecated setting, but required on Android versions prior to 3.0
	        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        else
        {
        	Log.d("","CameraPreview: mHolder is null");
        }
    }

    public void setCamera(Camera cam)
    {
    	mCamera = cam;
    	
    	//mCamera.setParameters(params)
    }

    public void surfaceCreated(SurfaceHolder holder) 
    {
        // The Surface has been created, now tell the camera where to draw the preview.
        try 
        {
        	Log.d(TAG, "CameraPreview: surfaceCreated: calling setPreviewDisplay");
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } 
        catch (IOException e) 
        {
        	String msg = e.getMessage();
        	if (msg != null)
        		Log.d(TAG, "CameraPreview: Error setting camera preview: " + msg);
        	else
        		Log.d(TAG, "CameraPreview: Error setting camera preview: null msg");
        }
        catch (Exception e1)
        {
        	String msg = e1.getMessage();
        	if (msg != null)
        		Log.d(TAG, "Error setting camera preview: " + msg);
        	else
        		Log.d(TAG, "Error setting camera preview: null msg");
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) 
    {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) 
    {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null)
        {
          // preview surface does not exist
          return;
        }

        // stop preview before making changes
        try 
        {
            mCamera.stopPreview();
        } 
        catch (Exception e)
        {
          // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try 
        {
        	Log.d(TAG, "CameraPreview: surfaceChanged: calling setPreviewDisplay");
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
        } 
        catch (Exception e)
        {
            Log.d("", "Error starting camera preview: " + e.getMessage());
        }
    }
}