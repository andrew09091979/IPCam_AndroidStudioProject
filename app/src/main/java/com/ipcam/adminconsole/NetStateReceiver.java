package com.ipcam.adminconsole;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.internalevent.InternalEvent;
import com.ipcam.internalevent.InternalEventInfoImpl;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetStateReceiver extends BroadcastReceiver 
{
	private final String TAG = "NetStateReceiver";

	private AsyncExecutor<IInternalEventInfo> eventHandler = null;
	private IInternalEventInfo info = null;
    private InetAddress currentAddress = null;

	public NetStateReceiver(AsyncExecutor<IInternalEventInfo> evh)
	{
		eventHandler = evh;
	}

	@Override
	public void onReceive(Context context, Intent intent) 
	{
        String mTypeName = "Unknown";
        String mSubtypeName = "Unknown";
        boolean mAvailable = false;

        Log.d(TAG, "NetStateReceiver: onReceive: action = " + intent.getAction());

		if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) 
		{
			ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		    NetworkInfo networkInfo = cm.getActiveNetworkInfo();//intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

		    if (networkInfo != null)
		    {
		        mTypeName = networkInfo.getTypeName();
		        mSubtypeName = networkInfo.getSubtypeName();
    		    mAvailable = networkInfo.isAvailable();
    		    NetworkInfo.State state = networkInfo.getState();

			    Log.d(TAG, "Network Type: " + mTypeName 
					+ ", subtype: " + mSubtypeName
					+ ", available: " + mAvailable
					+ ", state: " + state.toString());
			    
				InetAddress addressToUse = getWorkingInetAddress();

				if (addressToUse != null)
				{
					Log.d(TAG, "addressToUse.toString() = " + addressToUse.toString());

					if ((currentAddress == null) || (addressToUse.toString().compareTo(currentAddress.toString()) != 0))
					{
						currentAddress = addressToUse;

						if (info == null)
			        	    info = new InternalEventInfoImpl(InternalEvent.NETWORK_CONNECTED);
						else
							info.setInternalEvent(InternalEvent.NETWORK_CONNECTED);

			    		info.setHeadline("Network " + mTypeName);
			    		info.setMessage("Network type = " + mTypeName +
			    				        "\nsubtype = " + mSubtypeName +
			    				        "\navailable = " + mAvailable +
			    				        "\nstate = " + state.toString());
			    		info.setObject(currentAddress);
			    		if (eventHandler != null)
			    		{
			    			Log.d(TAG, "Sending NETWORK_CONNECTED message to ipcam");
			    			eventHandler.executeAsync(info);
			    		}
					}
					else
						Log.d(TAG, "no need to restart server connector for the same address");
				}
				else
					Log.d(TAG, "addressToUse is null");

		    }
		    else
		    {
				if (info == null)
	        	    info = new InternalEventInfoImpl(InternalEvent.NETWORK_DISCONNECTED);
				else
					info.setInternalEvent(InternalEvent.NETWORK_DISCONNECTED);

		    	Log.d("NetStateReceiver", "no connected networks found");
		    	eventHandler.executeAsync(info);
		    }
		}
		else
		{
			Log.d("SensorsService", "BroadcastReceiver::onReceive not connectivity action");
		}
    }
    
	public InetAddress getWorkingInetAddress()
	{
		InetAddress addressToUse = null;
		Enumeration<NetworkInterface> nie = null;

		try 
		{
			nie = NetworkInterface.getNetworkInterfaces();
		} 
		catch (SocketException e) 
		{
			e.printStackTrace();
		}

		while (nie.hasMoreElements())
		{
			NetworkInterface ni = nie.nextElement();
			Enumeration<InetAddress> iae = ni.getInetAddresses();

			while(iae.hasMoreElements())
			{
				InetAddress ia = iae.nextElement();
				Log.d(TAG, "addr = ----->" + ia.getHostAddress() + " isLinkLocalAddress = " + Boolean.toString(ia.isLinkLocalAddress()));
				Log.d(TAG, "addr = ----->" + " isLoopbackAddress = " + Boolean.toString(ia.isLoopbackAddress()));

				if (!ia.isLoopbackAddress())
				{
					 if (!ia.isLinkLocalAddress())
					 {
						 addressToUse = ia;
						 break;
					 }
				}
			}
		}
		return addressToUse;
	}
}
