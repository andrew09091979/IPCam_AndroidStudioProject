package com.ipcam.adminconsole;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.internalevent.InternalEvent;
import com.ipcam.internalevent.InternalEventInfoImpl;

import android.util.Log;

public class ServerConnector extends Thread implements Runnable 
{
	private final String TAG = "ServerConnector";
	private final int port = 6003;

	private AsyncExecutor<IInternalEventInfo> eventHandler = null;
	private InetAddress addressToUse = null;
	private ServerSocket serverSocket = null;

    public ServerConnector(InetAddress addr, AsyncExecutor<IInternalEventInfo> evh) 
    {
    	Log.d(TAG, "ServerConnector ctor with InetAddress parameter");
    	eventHandler = evh;
    	setInetAddress(addr);
	}

	@Override
    public void run()
    {
	    Log.d(TAG, "run: started");

		Socket sock = null;
    	InetAddress address = getInetAddress();
	 	InetSocketAddress isa = new InetSocketAddress(address, port);
		Log.d(TAG, "server is to be started on ip=" + addressToUse.getHostAddress());

	 	try
	 	{
			serverSocket = new ServerSocket();
	    	serverSocket.setReuseAddress(true);
		    Log.d(TAG, "run: trying to bind serverSocket");
		 	serverSocket.bind(isa);
			do
			{
			    if (addressToUse == null)
			    {
				    Log.d(TAG, "run: No available connections");
				    return;
			    }
	            sock = accept(addressToUse, port);

	            if ((sock != null) && (eventHandler != null))
	            {
	            	IInternalEventInfo info = new InternalEventInfoImpl(InternalEvent.ADMIN_CONNECTED);
	            	info.setMessage("Client connected inet address = " + sock.getInetAddress() + "  port = " + sock.getPort());
	            	info.setObject(sock);
	            	eventHandler.executeAsync(info);
	            }
			}
			while(sock != null);

		    Log.d(TAG, "run: cycle finished, sock is null");
		}
	 	catch (IOException e)
	 	{
			Log.e(TAG, "run IOException: " + e.getMessage());
		}
	    Log.d(TAG, "run: finished");
    }

    synchronized
    public void setInetAddress(InetAddress ia)
    {
    	addressToUse = ia;
    }

    synchronized
    public InetAddress getInetAddress()
    {
    	return addressToUse;
    }

    public Socket accept(InetAddress inetAddress, int port)
    {
		Log.d(TAG, "bindAndAccept: started");
		Socket sock = null;

    	if (inetAddress == null)
    		return null;

        try
        {
			sock = serverSocket.accept();
			Log.d(TAG, "accepted");
		} 
		catch (IOException e) 
		{
			//setInetAddress(null);
			Log.e(TAG, "IOException: " + e.getMessage());
			e.printStackTrace();
			sock = null;
		}
        return sock;
    }

    public void close()
    {
		try 
		{
    	    if (serverSocket != null)
    	    {
    	    	Log.d(TAG, "Closing serverSocket");
				serverSocket.close();
    	    }
		} 
		catch (IOException e)
		{
		    e.printStackTrace();
		}
    }
}
