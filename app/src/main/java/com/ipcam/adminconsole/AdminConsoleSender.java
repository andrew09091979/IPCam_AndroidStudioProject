package com.ipcam.adminconsole;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.asyncio.IIOStreamProvider;
import com.ipcam.asyncio.ISender;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.internalevent.InternalEvent;
import com.ipcam.internalevent.InternalEventInfoImpl;

public class AdminConsoleSender implements ISender<IInternalEventInfo>
{
	private final String TAG = "AdminConsoleSender";
    private IIOStreamProvider ioStreamProvider = null;
    private OutputStream outputStream = null;
    private InputStream inputStream = null;
    private ADMIN_CONSOLE_FSM adminConsoleFSM = null;
    private boolean bExit = false;

    public AdminConsoleSender()
    {
    	adminConsoleFSM = new ADMIN_CONSOLE_FSM();
    }
	@Override
	public SEND_RESULT send(IInternalEventInfo letterToSend_)
	{
		SEND_RESULT res = SEND_RESULT.UNKNOWN;
        int avail = 0;
        int processingResult = 0;

        adminConsoleFSM.setCurrentInternalEvent(letterToSend_);

    	if (ioStreamProvider != null)
    	{
    		if (outputStream == null)
			    outputStream = ioStreamProvider.getOutputStream();
    		if (inputStream == null)
			    inputStream = ioStreamProvider.getInputStream();

        	byte bfr[] = new byte[5000];

    		try
    		{
    			do
    			{
		        	byte [] msg = adminConsoleFSM.getMessageToSend();

		        	if(msg != null)
		        	{
	        			Log.d(TAG, "writing " + Integer.toString(msg.length) + " bytes");
					    outputStream.write(msg);
		        	}
		        	else
		        	{
		        		Log.d(TAG, "nothing to send, msg is null");
		        	}

		        	int needToRead = adminConsoleFSM.needToRead();
        			Log.d(TAG, "needToRead = " + needToRead + " bytes");

		        	if (needToRead > 0)
		        	{
		        		if (read(bfr, 0, needToRead))
		        		{
		        		    //avail = inputStream.read(bfr);
		        		    //Log.d(TAG, "available " + Integer.toString(avail) + " bytes");
	
		        		    //if (avail > 0)
		        			//    Log.d(TAG, new String(bfr, 0, avail));
	
		        		    processingResult = adminConsoleFSM.processMsg(bfr, letterToSend_);
		        		    Log.d(TAG, "processMsg returned " + processingResult);
		        		}
		        		else
		        		{
		        			Log.d(TAG, "read failed, shutting down admin console");
		        			inputStream.close();
		        			outputStream.close();
		        			ioStreamProvider.closeAll();
		        			processingResult = adminConsoleFSM.processMsg(null, letterToSend_);
		        		}
		        	}
    	        }
    			while(processingResult == 0);

    			if (processingResult == 1)
    			{
    				res = SEND_RESULT.SUCCESS;
    			}
    			else
    			{
    				res = SEND_RESULT.SERVER_ERROR;
    			}
			}
    		catch (IOException e)
    		{
				e.printStackTrace();
			}
    	}
    	else
    	{
    		Log.e(TAG, "ioStreamProvider is null");
    	}
	    Log.d(TAG, "send finished");
		return res;
	}
	private boolean read(byte buffer[], int offs, int count)
	{
		int read_res = 0;
		int numOfAttempts = 5;
		boolean read_finish = false;
		boolean res = false;
		int offset = offs;
		int bytes_remain_to_read = count;

		while (!read_finish)
		{
			try 
			{
				read_res = inputStream.read(buffer, offset, 1);

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

	@Override
	public void injectIOStreamProvider(IIOStreamProvider iosp)
	{
		ioStreamProvider = iosp;
	}

}

class ADMIN_CONSOLE_FSM
{
	enum STATE
	{
		READ_START_BYTE,
		READ_MSG_SIZE,
		READ_MSG,
		READ_FAILED,
		READ_OK,
		SEND_ANSWER
	}
	private final byte START_BYTE = '#';
	private boolean needToRead;
	private final String TAG = "AdminConsoleSender";
	private STATE state = STATE.READ_START_BYTE;
    private Map<String, Method> commands = null;
    private Map<STATE, STATE> nextState = null;
    private Map<STATE, Integer> messageSize = null;
    private int currentyHandledMessageSize = 0;
    private AsyncExecutor<IInternalEventInfo> eventHandler = null;
    private IInternalEventInfo infoForMessage = null;

	public ADMIN_CONSOLE_FSM()
	{
        initFSM();
        initCommands();
	}
	public void resetFSM()
	{
		state = STATE.READ_START_BYTE;
	}
	private void initFSM()
	{
		state = STATE.READ_START_BYTE;
		needToRead = true;

		messageSize = new HashMap<STATE, Integer>();
		messageSize.put(STATE.READ_START_BYTE, 1);
		messageSize.put(STATE.READ_MSG_SIZE, 2);
		messageSize.put(STATE.READ_MSG, 0); //Should be equal to currentyHandledMessageSize
		messageSize.put(STATE.READ_FAILED, 0);
		messageSize.put(STATE.READ_OK, 0);
		messageSize.put(STATE.SEND_ANSWER, 0);

		nextState = new HashMap<STATE, STATE>();
		nextState.put(STATE.READ_START_BYTE, STATE.READ_MSG_SIZE);
		nextState.put(STATE.READ_MSG_SIZE, STATE.READ_MSG);
		nextState.put(STATE.READ_MSG, STATE.READ_OK);
		initCommands();
	}
    private void initCommands()
    {
    	commands = new HashMap<String, Method>();

    	if (commands != null)
    	{
    		try
    		{
				commands.put("photo", ADMIN_CONSOLE_FSM.class.getMethod("commandPhoto",
						                                                 new Class[]{JSONObject.class}));
				commands.put("recordvideo", ADMIN_CONSOLE_FSM.class.getMethod("commandRecordVideo",
                                                                         new Class[]{JSONObject.class}));
				commands.put("recordsound", ADMIN_CONSOLE_FSM.class.getMethod("commandRecordSound",
                                                                         new Class[]{JSONObject.class}));
				commands.put("filelist", ADMIN_CONSOLE_FSM.class.getMethod("commandGetFileList",
                                                                         new Class[]{JSONObject.class}));
				commands.put("downloadfile", ADMIN_CONSOLE_FSM.class.getMethod("commandDownloadFile",
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
    public void setCurrentInternalEvent(IInternalEventInfo ifm)
    {
    	infoForMessage = ifm;
    }
	public int needToRead()
	{
	    Log.d(TAG, "needToRead: state is " + state.toString());
		return messageSize.get(state);
	}
	public byte [] getMessageToSend()
	{
		byte [] msgBytes = null;

        if (state == STATE.SEND_ANSWER)
        {
        	
        	state = STATE.READ_START_BYTE;
        }

		return msgBytes;
	}
	public int processMsg(byte [] msg, IInternalEventInfo iei)
	{
		int res = 0;
		String cmd;
	    Log.d(TAG, "processMsg: state is " + state.toString());

		if (msg == null)
		{
			shutdown();
        	return -1; //TBD
		}
		switch (state)
		{
		case READ_START_BYTE:

			if (msg[0] == START_BYTE)
				state = STATE.READ_MSG_SIZE;
			else
			{
				state = STATE.READ_FAILED;
		        res = -1;
			}
			break;
		case READ_MSG_SIZE:
			ByteBuffer buffer = ByteBuffer.wrap(msg, 0, 2);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			currentyHandledMessageSize = buffer.getShort();
			messageSize.put(STATE.READ_MSG, currentyHandledMessageSize);
			state = STATE.READ_MSG;
			break;
		case SEND_ANSWER:
			state = STATE.READ_START_BYTE;
			break;
		case READ_MSG:
	        if ((msg != null) && needToRead() > 0)
	        {
			    cmd = new String(msg, 0, needToRead());
				Log.d(TAG, "message is " + cmd + " calling extractCommandAndExecute");
				extractAndProcessCommand(cmd, iei);
	        }
	        else
	        {

	        }
	        state = STATE.SEND_ANSWER;
	        res = 1;
			break;
		case READ_FAILED:
			shutdown();
			break;
		}

		return res;
	}

	private void extractAndProcessCommand(String cmd, IInternalEventInfo iei)
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
					//initCommands();
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
	    if (infoForMessage != null)
	    {
	    	infoForMessage.setInternalEvent(InternalEvent.NEED_TO_COLLECT_DATA);
	    	infoForMessage.setMessage("Taking shot per admin demand");
	    	infoForMessage.setHeadline("Taking shot per admin demand");
	    	//info.setResultNotifier(this);
	    	infoForMessage.setParameter(photoQuality);
	    }
	    else
	    {
	    	Log.e(TAG, "run: infoForMessage is null");
	    }		
		Log.d(TAG, "commandPhoto finish");
	}
	public void commandRecordVideo(JSONObject jsonObj)
	{
		Log.d(TAG, "commandRecordVideo enter");
		int recordLength = 5;

        try
        {
        	recordLength = jsonObj.getInt("parameter");
        }
        catch (JSONException e1)
        {
			e1.printStackTrace();
		}
	    if (infoForMessage != null)
	    {
	    	infoForMessage.setInternalEvent(InternalEvent.NEED_TO_RECORD_VIDEO);
	    	infoForMessage.setMessage("Recording video per admin demand");
	    	infoForMessage.setHeadline("Recording video per admin demand");
	    	//info.setResultNotifier(this);
	    	infoForMessage.setParameter(recordLength);
	    }
	    else
	    {
	    	Log.e(TAG, "run: infoForMessage is null");
	    }
		Log.d(TAG, "commandRecordSound finish");
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
	    if (infoForMessage != null)
	    {
	    	infoForMessage.setInternalEvent(InternalEvent.NEED_TO_RECORD_AMBIENT_SOUND);
	    	infoForMessage.setMessage("Recording sound per admin demand");
	    	infoForMessage.setHeadline("Recording sound per admin demand");
	    	//info.setResultNotifier(this);
	    	infoForMessage.setParameter(recordLength);
	    }
	    else
	    {
	    	Log.e(TAG, "run: infoForMessage is null");
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
    private void shutdown()
    {
		Log.d(TAG, "shutdown enter");

	    if (infoForMessage != null)
	    {
	    	infoForMessage.setInternalEvent(InternalEvent.STOP_ASYNC_EXECUTOR);
	    	infoForMessage.setMessage("Connection lost, closing admin console");
	    	infoForMessage.setHeadline("Connection lost, closing admin console");
	    	//info.setResultNotifier(this);
	    }
	    else
	    {
	    	Log.e(TAG, "run: infoForMessage is null");
	    }		
		Log.d(TAG, "shutdown finish");
    }
}