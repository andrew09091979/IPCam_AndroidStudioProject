package com.ipcam.soundplayer;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.task.ITask;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

public class SoundPlayerImpl extends AsyncExecutor<IInternalEventInfo>
{
	private final String TAG = "SoundPlayerImpl";

    private MediaPlayer mediaPlayer = null;
    private Context context = null;
    private String audioFile = null;
    
    public SoundPlayerImpl(Context cont, String af)
	{
		context = cont;
		audioFile = af;

		if ((context != null) && (audioFile != null))
		{
            mediaPlayer = MediaPlayer.create(context, Uri.parse(audioFile));
		}
		else
		{
			Log.e(TAG, "SoundPlayerImpl::SoundPlayerImpl: context or audioFile is null");
		}
	}

	@Override
	public void stop()
	{
        super.stop();

        if (mediaPlayer != null)
        {
        	if (mediaPlayer.isPlaying())
        	{
        		mediaPlayer.stop();
        	}
        	else
        	{
        		Log.d(TAG, "stop: not playing, nothing to stop");
        	}
        }
        else
        {
        	Log.e(TAG, "stop: mediaPlayer is null");
        }
	}

	@Override
    public void executor(IInternalEventInfo info)
    {
		Log.d(TAG, "SoundPlayerImpl: executor started");

        if (mediaPlayer != null)
        {
        	if (!mediaPlayer.isPlaying())
        	{
				Thread alarmPlayer = new PlaySound();
				alarmPlayer.start();
        	}
        	else
        	{
        		Log.e(TAG, "already playing");
        	}
        }
        else
        {
        	Log.e(TAG, "mediaPlayer is null");
        }
    }

    private class PlaySound extends Thread
    {
    	private final String TAG = "SoundPlayerImpl";

    	@Override
    	public void run()
    	{
    		playAlarm();
    	}
    	synchronized private void playAlarm()
    	{
	        if (mediaPlayer != null)
	        {
	        	if (!mediaPlayer.isPlaying())
	        		mediaPlayer.start();
	        	else
	        	{
	        		Log.e(TAG, "playAlarm: already playing");
	        	}
	        }
	        else
	        {
	        	Log.e(TAG, "playAlarm: mediaPlayer is null");
	        }
    	}    	
    }
}
