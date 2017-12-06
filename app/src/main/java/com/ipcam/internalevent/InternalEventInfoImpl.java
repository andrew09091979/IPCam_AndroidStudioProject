package com.ipcam.internalevent;

import java.util.Vector;
import java.util.Random;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.asyncio.AsyncMessageSender;
import com.ipcam.task.ITask;

import android.os.PowerManager.WakeLock;
import android.util.Log;

public class InternalEventInfoImpl implements IInternalEventInfo
{
	private final String TAG = "IPCam";

	private InternalEvent event;
	private int ID = 0;
	private String headline = null;
	private String message = null;
	private Vector<String> file = null;
    private WakeLock wakeLock = null;
    private int parameter = 0;
    private Object object = null;
    private AsyncExecutor<IInternalEventInfo> resNotifier = null;

    public InternalEventInfoImpl(InternalEvent ie)
    {
    	Random rand = new Random();
    	ID = rand.nextInt();
    	event = ie;
    }
    @Override	
    public void setHeadline(String h)
    {
    	headline = h;
    }
    @Override
    public void addFile(String fn)
    {
    	if (file == null)
    		file = new Vector<String>();

    	file.add(fn);
    }
	@Override
	public void removeAllFiles()
	{
    	if (file != null)
    		file.clear();		
	}    
    @Override
    public void setMessage(String m)
    {
    	message = m;
    }
	@Override
	public void concatToMessage(String m)
	{
		message += m;
	}    
	@Override
	public int getID()
	{
		return ID;
	}
	@Override
	public void setWakeLock(WakeLock wl)
	{
		wakeLock = wl;
	}
	@Override
	public String getHeadline()
	{
		return headline;
	}
	@Override
	public String getMessage()
	{
		return message;
	}	
	@Override
	public WakeLock getWakeLock()
	{
		return wakeLock;
	}
	@Override
	public int getFilesNum()
	{
		int res = 0;
		
		if (file != null)
			res = file.size();
		return res;
	}
	@Override
	public String getFile(int fileNum)
	{
		String fileToReturn = null;
		try
		{
	        fileToReturn = file.get(fileNum);
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			Log.e(TAG, "InternalEventInfoImpl: wrong index");
		}
		return fileToReturn;
	}
	@Override
	public void setParameter(int p)
	{
		parameter = p;
	}
	@Override
	public int getParameter() 
	{
		return parameter;
	}
	@Override
	public void setObject(Object obj)
	{
		object = obj;
	}
	@Override
	public Object getObject()
	{
		return object;
	}
	@Override
	public void setResultNotifier(AsyncExecutor<IInternalEventInfo> t)
	{
		resNotifier = t;
	}
	@Override
	public AsyncExecutor<IInternalEventInfo> getResultNotifier()
	{
		return resNotifier;
	}
	@Override
	public InternalEvent getInternalEvent()
	{
		return event;
	}
	@Override
	public void setInternalEvent(InternalEvent ie)
	{
		event = ie;
	}
}
