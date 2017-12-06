package com.ipcam.adminconsole;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.internalevent.InternalEvent;
import com.ipcam.internalevent.InternalEventInfoImpl;
import com.ipcam.task.ITask;

import android.util.Log;

public class ConnectionHandler extends Thread implements ITask<IInternalEventInfo>
{
	private enum STATE
	{
		INITIAL,
		WAIT_FOR_COMMAND,
		SEND_ANSWER
	}
	
	private enum READ_MSG_STATE
	{
		READ_START_BYTE,
		READ_MSG_SIZE,
		READ_MSG,
		READ_FAILED,
		READ_OK
	}
	private final byte START_BYTE = '#';
	private final String TAG = "ConnectionHandler";
	private AsyncExecutor<IInternalEventInfo> eventHandler = null;
    private Socket sock = null;
    private boolean bExit = false;
    private OutputStream out = null;
    private InputStream in = null;
    private Map<String, Method> commands = null;

    public ConnectionHandler(Socket s, AsyncExecutor<IInternalEventInfo> evh)
    {
    	sock = s;
    	eventHandler = evh;
        initCommands();

		try
		{
	        sock.setKeepAlive(true);
	        sock.setSoTimeout(0);
			out = sock.getOutputStream();
			in = sock.getInputStream();
		}
		catch (IOException e)
		{
			Log.e(TAG, "ConnectionHandler: " + e.getMessage());
		}
    }
    private void initCommands()
    {
    	commands = new HashMap<String, Method>();

    	if (commands != null)
    	{
    		try
    		{
				commands.put("photo", ConnectionHandler.class.getMethod("commandPhoto",
						                                                 new Class[]{JSONObject.class}));
				commands.put("recordvideo", ConnectionHandler.class.getMethod("commandRecordVideo",
                                                                         new Class[]{JSONObject.class}));
				commands.put("recordsound", ConnectionHandler.class.getMethod("commandRecordSound",
                                                                         new Class[]{JSONObject.class}));
				commands.put("filelist", ConnectionHandler.class.getMethod("commandGetFileList",
                                                                         new Class[]{JSONObject.class}));
				commands.put("downloadfile", ConnectionHandler.class.getMethod("commandDownloadFile",
                                                                         new Class[]{JSONObject.class}));
			}
    		catch (NoSuchMethodException e)
    		{
    			Log.e(TAG, "initCommands: exception: " + e.getMessage());
				e.printStackTrace();
			}
    	}
    	else
    	{
    		Log.e(TAG, "can't create commands map");
    	}
    }
	@Override
    public void run()
    {
	    Log.d(TAG, "run: started");

	    while (!bExit)
	    {
//	    	Log.d(TAG, "sock.isClosed = " + sock.isClosed() + "; sock.isBound = " + sock.isBound() + "; sock.isConnected = " + sock.isConnected() + 
//	    			"; sock.isInputShutdown = " + sock.isInputShutdown() + "; sock.isOutputShutdown = " + sock.isOutputShutdown());
	    	if (!sock.isClosed())
	    	{
	    	    String cmd = receiveCommand();

	    	    if (cmd != null)
	    	    {
	    	    	Log.d(TAG, "run: calling parseAndExecute");
	    	    	extractCommandAndExecute(cmd);
	    	    }
	    	    else
	    	    {
    			    Log.d(TAG, "run: received null instead of command, closing in and out setting bExit as true");
    		    	bExit = true;

    		    	try
    		    	{
    	    	    	in.close();
    					out.close();
    					sock.close();
    				}
    		    	catch (IOException e)
    		    	{
    	    		    Log.d(TAG, "run: exception while closing stream or socket: " + e.getMessage());
    					e.printStackTrace();
    				}
    		    }
	    	}
	    	else
	    	{
	    		Log.d(TAG, "ConnectionHandler: run: sock is closed, setting bExit as true");
	    		bExit = true;
	    	}
	    };
		Log.d(TAG, "ConnectionHandler: run finished");
    }
	private String receiveCommand()
	{
		boolean res = false;
		boolean finish = false;
		byte msg_head[] = new byte[3];
		byte [] msg = null;
		int msg_size = 0;
		READ_MSG_STATE rmstate = READ_MSG_STATE.READ_START_BYTE;
		
		while (!finish)
		{
			switch (rmstate)
			{
				case READ_START_BYTE:
				{
					Log.d(TAG, "receiveCommand: reading start byte");
					res = read(msg_head, 0, 1);
					
					if (res)
					{
						if (msg_head[0] == START_BYTE)
						{
							rmstate = READ_MSG_STATE.READ_MSG_SIZE;
						}
						else
						{
							rmstate = READ_MSG_STATE.READ_START_BYTE;
						}
					}
					else
					{
						Log.e(TAG, "receiveCommand: READ_START_BYTE failed res == false");
						rmstate = READ_MSG_STATE.READ_FAILED;
					}
				}
				break;

				case READ_MSG_SIZE:
				{
					Log.d(TAG, "receiveCommand: reading message size");
					res = read(msg_head, 1, 2);
					
					if (res)
					{
						ByteBuffer buffer = ByteBuffer.wrap(msg_head, 1, 2);
						buffer.order(ByteOrder.LITTLE_ENDIAN);
						msg_size = buffer.getShort();
						
						if (msg_size > 0)
						{
							Log.d(TAG, "message size is " + msg_size);
							rmstate = READ_MSG_STATE.READ_MSG;
						}
						else
						{
							Log.d(TAG, "receiveCommand: READ_MSG_SIZE failed msg_size < 0");
							rmstate = READ_MSG_STATE.READ_FAILED;
						}
					}
					else
					{
						Log.d(TAG, "receiveCommand: READ_MSG_SIZE failed res == false");
						rmstate = READ_MSG_STATE.READ_FAILED;
					}
				}
				break;
				
				case READ_MSG:
				{
					Log.d(TAG, "receiveCommand: reading message");
/*
					msg = new byte[msg_size+3]; 
					msg[0] = msg_head[0]; msg[1] = msg_head[1]; msg[2] = msg_head[2];  
					res = read(msg, 3, msg_size);
*/
					msg = new byte[msg_size];   
					res = read(msg, 0, msg_size);

					if (res)
					{
						rmstate = READ_MSG_STATE.READ_OK;
					}
					else
					{
						Log.d(TAG, "receiveCommand: READ_MSG failed res == false");
						rmstate = READ_MSG_STATE.READ_FAILED;
					}					
				}
				break;
				
				case READ_OK:
				{
					Log.d(TAG, "receiveCommand: READ_OK");						
					res = true;
					finish = true;					
				}
				break;
				
				case READ_FAILED:
				{
					Log.e(TAG, "receiveCommand: READ_FAILED");
					res = false;
					finish = true;
				}
				break;
			}
		}

		String cmd = null;

        if (msg != null)
        {
		    cmd = new String(msg, 0, msg_size);
			Log.d(TAG, "message is " + cmd);
        }
        else
        {
        	
        }
		return cmd;
	}
	
	private boolean read(byte buffer[], int offs, int count)
	{
		int read_res = 0;
		int numOfAttempts = 5;
		boolean read_finish = false;
		boolean res = false;
		int offset = offs;
		int bytes_remain_to_read = count;
		int availableToRead = 0;
		int pause = 100;

		while (!read_finish)
		{
			try 
			{
				String strCnt = String.valueOf(count);
				read_res = in.read(buffer, offset, 1);

				if (read_res > 0)
				{
					bytes_remain_to_read -= read_res;
					offset += read_res;
				}
				else if (read_res == -1)
				{
					--numOfAttempts;
				}
			}
			catch (SocketTimeoutException e)
			{
				String excptMsg = e.getMessage();
				e.printStackTrace();
				Log.e(TAG, "read: SocketTimeoutException");
				if (excptMsg != null)
					Log.e(TAG, excptMsg);
				else
					Log.e(TAG, "read: SocketTimeoutException occured, but getMessage returned null");
				
				--numOfAttempts;				
			}			
			catch (IOException e) 
			{
				String excptMsg = e.getMessage();
				e.printStackTrace();
				Log.e(TAG, "read: IOException");
				if (excptMsg != null)
					Log.d(TAG, excptMsg);
				else
					Log.d(TAG, "read: IOException occured, but getMessage returned null");
				
				--numOfAttempts;
			}

			if (numOfAttempts <= 0)
			{
				Log.d(TAG, "read: numOfAttempts <= 0");
				res = false;
				read_finish = true;
			}
			
			if (bytes_remain_to_read <= 0)
			{
				read_finish = true;
				res = true;
			}
		}

		return res;
	}

	private void sendResult()
	{
	}
	@Override
	public void performTask(IInternalEventInfo info)
	{
	    Log.d(TAG, "performTask: task result received message: " + info.getMessage() + "; headline: " + info.getHeadline());
		String filePathToSend = null;
		File fileToSend = null;
        FileInputStream fis = null;

		if ((info != null) && (info.getFilesNum() > 0))
			filePathToSend = info.getFile(0);

		if (filePathToSend == null)
		{
			Log.e(TAG, "performTask: filePathToSend is null, no files to send");
		}
		else
		{
		    Log.d(TAG, "performTask: sending file " + filePathToSend);
		    fileToSend = new File(filePathToSend);

			try
			{
		    	if (sock != null)
		    	{
					fis = new FileInputStream(fileToSend);
	                long length = fileToSend.length();
	                Log.d(TAG, "performTask: file length = " + Long.toString(length));
	                byte [] pic = new byte[(int)length];
					fis.read(pic);

		    		Log.d(TAG, "performTask: writing file");
					out.write(pic);
					pic = null;
		    		//Log.d(TAG, "performTask: closing socket");
				    //sock.close();
		    	}
			}
			catch (FileNotFoundException e)
			{
				Log.e(TAG, "performTask: exception: " + e.getMessage());
			}
			catch (IOException e)
			{
				Log.e(TAG, "performTask: exception: " + e.getMessage());
			}
		}
	}
	private void extractCommandAndExecute(String cmd)
	{
	    Log.d(TAG, "parseAndExecute entered");

	    if (cmd != null)
	    {
            try
            {
				JSONObject jsonobj = new JSONObject(cmd);
		        Log.d(TAG, "extractCommandAndExecute: received command: " + cmd);
		        String strCmd = jsonobj.getString("command");
				Method mth = commands.get(strCmd);

				if (mth != null)
				{
				    Log.d(TAG, "extractCommandAndExecute: method found, invoking...");		
					mth.invoke(this, jsonobj);
				}
				else
				{
					Log.e(TAG, "extractCommandAndExecute: internalEventHandlingMethods is " + commands.toString());
					Log.e(TAG, "extractCommandAndExecute: handler not found, calling initMethodsMap");
					initCommands();
				}
			}
            catch (JSONException e1)
            {
				e1.printStackTrace();
			}
			catch (Exception e)
			{
				Log.e(TAG, "extractCommandAndExecute: exception while invoking method from methods map: " + e.getMessage());
				e.printStackTrace();
			}
	    }
	}
	public void commandPhoto(JSONObject jsonObj)
	{
		Log.d(TAG, "commandPhoto enter");
		int photoQuality = 1;

        try
        {
        	photoQuality = jsonObj.getInt("parameter");
        }
        catch (JSONException e1)
        {
			e1.printStackTrace();
		}
	    if (eventHandler != null)
	    {
	    	IInternalEventInfo info = new InternalEventInfoImpl(InternalEvent.NEED_TO_COLLECT_DATA);
	    	info.setMessage("Taking shot per admin demand");
	    	info.setHeadline("Taking shot per admin demand");
	    	//info.setResultNotifier(this);
	    	info.setParameter(photoQuality);
	    	eventHandler.executeAsync(info);
	    }
	    else
	    {
	    	Log.e(TAG, "run: eventHandler is null");
	    }		
		Log.d(TAG, "commandPhoto finish");
	}
	public void commandRecordVideo(JSONObject jsonObj)
	{
		Log.d(TAG, "commandRecordVideo enter");
		Log.d(TAG, "commandRecordVideo finish");
	}
	public void commandRecordSound(JSONObject jsonObj)
	{
		Log.d(TAG, "commandRecordSound enter");
		int recordLength = 5;

        try
        {
        	recordLength = jsonObj.getInt("parameter");
        }
        catch (JSONException e1)
        {
			e1.printStackTrace();
		}
	    if (eventHandler != null)
	    {
	    	IInternalEventInfo info = new InternalEventInfoImpl(InternalEvent.NEED_TO_RECORD_AMBIENT_SOUND);
	    	info.setMessage("Recording sound per admin demand");
	    	info.setHeadline("Recording sound per admin demand");
	    	info.setResultNotifier(null);
			info.setParameter(recordLength);
	    	eventHandler.executeAsync(info);
	    }
	    else
	    {
	    	Log.e(TAG, "run: eventHandler is null");
	    }		
		Log.d(TAG, "commandRecordSound finish");
	}
	public void commandGetFileList(JSONObject jsonObj)
	{
		Log.d(TAG, "commandGetFileList enter");
		Log.d(TAG, "commandGetFileList finish");
	}
	public void commandDownloadFile(JSONObject jsonObj)
	{
		Log.d(TAG, "commandDownloadFile enter");
		Log.d(TAG, "commandDownloadFile finish");
	}
}
