package com.ipcam.mailsender;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import android.util.Log;

import com.ipcam.asyncio.IIOStreamProvider;
import com.ipcam.asyncio.ISender.SEND_RESULT;

public class SSLIOStreamProvider implements IIOStreamProvider
{
	private final int    SO_TIMEOUT = 40000;
	private final int    CONN_TIMEOUT = 10000;
    private final String TAG = "SMTPSender";
	private OutputStream outputStream = null;
	private InputStream inputStream = null;
	private SSLSocket sslsocket = null;
    private String host = null;
    private int port = 0;
    private InetSocketAddress inetAddrConnTo = null;
    private Object lock = new Object();

    public SSLIOStreamProvider(String h, String p)
    {
    	host = h;
    	port = Integer.parseInt(p);
    }
	@Override
	public OutputStream getOutputStream()
	{
		synchronized (lock)
		{
		    if (outputStream == null)
		    {
				Log.d(TAG,"SSLIOStreamProvider: getOutputStream calling connect");
		    	connect();
		    }
		}
		Log.d(TAG,"SSLIOStreamProvider: getOutputStream returning outputStream");
		return outputStream;
	}

	@Override
	public InputStream getInputStream()
	{
		synchronized (lock)
		{
		    if (inputStream == null)
		    {
				Log.d(TAG,"SSLIOStreamProvider: getInputStream calling connect");
		    	connect();
		    }
		}
		Log.d(TAG,"SSLIOStreamProvider: getInputStream returning inputStream");
		return inputStream;
	}
	@Override
	public void closeAll()
	{
		synchronized (lock)
		{
			Log.d(TAG,"SSLIOStreamProvider: closeAll started");
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
				Log.e(TAG, "SSLIOStreamProvider: exception while closing stream");
			}
			try
			{
				if (sslsocket != null)
				    sslsocket.close();
				sslsocket = null;
			}
			catch (IOException e)
			{
				e.printStackTrace();
				Log.e(TAG, "SSLIOStreamProvider: exception while closing sslsocket");
			}
		}
	}
    private void connect()
    {
		synchronized (lock)
		{
	        try
	        {
				Log.d(TAG,"SSLIOStreamProvider: connect started");
	
				inetAddrConnTo = new InetSocketAddress(host, port);
		        SSLSocketFactory factory=(SSLSocketFactory) SSLSocketFactory.getDefault();
		        sslsocket=(SSLSocket) factory.createSocket();
	
		        if (sslsocket != null)
		        {
		        	Log.d(TAG, "SSLIOStreamProvider: sslsocket is not null");
		        	sslsocket.setUseClientMode(true);
		        	Log.d(TAG, "SSLIOStreamProvider: calling sslsocket.connect");
		            sslsocket.connect(inetAddrConnTo, CONN_TIMEOUT);
		        	Log.d(TAG, "SSLIOStreamProvider: calling sslsocket.getOutputStream");
		            outputStream = sslsocket.getOutputStream();
		        	Log.d(TAG, "SSLIOStreamProvider: calling sslsocket.getInputStream");
		            inputStream = sslsocket.getInputStream();
		            sslsocket.setSoTimeout(SO_TIMEOUT);
		        }
	        }
	        catch(UnknownHostException e)
	        {
				e.printStackTrace();
	            outputStream = null;
	            inputStream = null;
	        }
	        catch(IOException e)
	        {
				e.printStackTrace();
	            outputStream = null;
	            inputStream = null;
	        }
		}
    }
}