package com.ipcam.asyncio;

import android.util.Log;
import com.ipcam.asyncio.AsyncExecutor;

public class AsyncMessageSender<TDataToSend, TDataToReportResult> extends AsyncExecutor<TDataToSend>
{
	private final String TAG = "AdminConsoleSender";//"AsyncMessageSender"; 
    private ISender<TDataToSend> sender = null;
    private int networkFailCounter = 0;
    private int serverFailCounter = 0;
    private IDataToSendTreatment<TDataToSend, TDataToReportResult> dataTreatment = null;
    private AsyncExecutor<TDataToReportResult> resultHandler = null;

    public AsyncMessageSender(IDataToSendTreatment<TDataToSend, TDataToReportResult> dt, ISender<TDataToSend> ls,
    		                   AsyncExecutor<TDataToReportResult> r)
    {
    	dataTreatment = dt;
    	sender = ls;
    	resultHandler = r;
    }
    @Override
    public void executor(TDataToSend info)
    {
    	Log.d(TAG, "executor: calling sendLetter");
    	sendMessage(info);
    }
	private boolean sendMessage(TDataToSend infoForLetter)
	{
		ISender.SEND_RESULT res = ISender.SEND_RESULT.UNKNOWN;

        int maxAttemptsToSend = 5;//infoForLetter.getParameter();

        if ((dataTreatment != null) || (infoForLetter != null))
        {
            dataTreatment.addString(infoForLetter, "\nNetwork failures: " + networkFailCounter + "; Server failures: " + serverFailCounter);
        }
        else
        {
        	Log.e(TAG, "can't add string dataTreatment or infoForLetter is null");
        }
        int attemptsToSend = 0;
        res = ISender.SEND_RESULT.UNKNOWN;

        do
        {
        	if (attemptsToSend > 0)
        	{
        		try
        		{
					Thread.sleep(2000 * attemptsToSend);
				}
        		catch (InterruptedException e)
        		{
        			Log.e(TAG, "AsyncMessageSender: InterruptedException in Thread.sleep: " + e.getMessage());
					e.printStackTrace();
				}
        	}
        	Log.d(TAG, "Trying to send letter, attempt #" + attemptsToSend);
            res = sender.send(infoForLetter);
            ++attemptsToSend;
        }
        while ((res != ISender.SEND_RESULT.SUCCESS) &&
        		(res != ISender.SEND_RESULT.SERVER_ERROR) &&
        		(attemptsToSend < maxAttemptsToSend));

        if (res == ISender.SEND_RESULT.SUCCESS)
        {
        	Log.d(TAG, "AsyncMessageSender: successfully sent");
        }
        else if (res  == ISender.SEND_RESULT.SERVER_ERROR)
        {
        	++serverFailCounter;
        	Log.e(TAG, "AsyncMessageSender: server error serverFailCounter = " + Integer.toString(serverFailCounter));
        }
        else if (res == ISender.SEND_RESULT.NETWORK_ERROR)
        {
        	++networkFailCounter;
        	Log.e(TAG, "AsyncMessageSender: network error networkFailCounter = " + Integer.toString(networkFailCounter));
        }

        if ((dataTreatment != null) && (infoForLetter != null)) 
        {
        	//dataTreatment.setResultFlag(infoForLetter, (res == ISender.SEND_RESULT.SUCCESS));
        	Log.d(TAG, "AsyncMessageSender: reporting result");
        	dataTreatment.setResultNotifier(infoForLetter, this);
        	TDataToReportResult reportRes = dataTreatment.convertIntoDataToReport(infoForLetter);
        	resultHandler.executeAsync(reportRes);
        }
        else
        {
        	Log.e(TAG, "AsyncMessageSender sendLetter: eventHandler or item is null");
        }

        return true;
	}
}
