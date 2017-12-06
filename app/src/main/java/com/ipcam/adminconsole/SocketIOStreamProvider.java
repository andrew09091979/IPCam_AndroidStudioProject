package com.ipcam.adminconsole;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

import android.util.Log;

import com.ipcam.asyncio.IIOStreamProvider;

public class SocketIOStreamProvider implements IIOStreamProvider
{
	private final String TAG = "SocketIOStreamProvider"; 
    private Socket socket = null;
    private Object lock = new Object();
    private OutputStream outputStream = null;
    private InputStream inputStream = null;

    public SocketIOStreamProvider(Socket sock)
    {
    	socket = sock;

    	try
    	{
			socket.setSoTimeout(0);
		}
    	catch (SocketException e)
    	{
			e.printStackTrace();
		}
    }
	@Override
	public OutputStream getOutputStream()
	{
		synchronized (lock)
		{
			if (outputStream == null)
			{
				if (socket != null)
				{
					try
					{
						outputStream = socket.getOutputStream();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}
			}
		}
		return outputStream;
	}

	@Override
	public InputStream getInputStream()
	{
		synchronized (lock)
		{
			if (inputStream == null)
			{
				if (socket != null)
				{
					try
					{
						inputStream = socket.getInputStream();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}
			}
		}
		return inputStream;
	}

	@Override
	public void closeAll()
	{
		synchronized (lock)
		{
			Log.d(TAG,"SocketIOStreamProvider: closeAll started");
			try
			{
				if (inputStream != null)
					inputStream.close();
				inputStream = null;
	
				if (outputStream != null)
					outputStream.close();
				outputStream = null;
			}
			catch (IOException e)
			{
				e.printStackTrace();
				Log.e(TAG, "SocketIOStreamProvider: exception while closing stream");
			}
			try
			{
				if (socket != null)
				    socket.close();
				socket = null;
			}
			catch (IOException e)
			{
				e.printStackTrace();
				Log.e(TAG, "SocketIOStreamProvider: exception while closing sslsocket");
			}
		}
	}

}
