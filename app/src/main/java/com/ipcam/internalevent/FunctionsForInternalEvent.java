package com.ipcam.internalevent;

import android.util.Log;

import com.ipcam.asyncio.AsyncExecutor;
import com.ipcam.asyncio.IDataToSendTreatment;
import com.ipcam.asyncio.ISender;
import com.ipcam.asyncio.ISender.SEND_RESULT;

public class FunctionsForInternalEvent implements IDataToSendTreatment<IInternalEventInfo, IInternalEventInfo>
{
	private final String TAG = "AsyncMessageSender";

	public FunctionsForInternalEvent()
	{
	}
	@Override
	public void addString(IInternalEventInfo data, String str)
	{
		data.concatToMessage(str);
	}
	@Override
	public void setResultFlag(IInternalEventInfo data, boolean res)
	{
		data.setParameter(res ? 1 : 0);
	}
	@Override
	public void setResultNotifier(IInternalEventInfo data,
			                        AsyncExecutor<IInternalEventInfo> o)
	{
		data.setResultNotifier(o);
	}
	@Override
	public void setEventType(IInternalEventInfo data, InternalEvent ev)
	{
		data.setInternalEvent(ev);
	}
	@Override
	public IInternalEventInfo convertIntoDataToReport(IInternalEventInfo data)
	{
		return data;
	}
	@Override
	public InternalEvent getEventType(IInternalEventInfo data)
	{
		return data.getInternalEvent();
	}
}
