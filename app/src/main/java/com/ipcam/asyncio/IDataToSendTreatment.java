package com.ipcam.asyncio;

import com.ipcam.internalevent.InternalEvent;

public interface IDataToSendTreatment<TDataToSend, TDataToReportResult>
{
	void addString(TDataToSend data, String str);
	void setResultFlag(TDataToSend data, boolean res);
	void setResultNotifier(TDataToSend data, AsyncExecutor<TDataToSend> o);
	void setEventType(TDataToSend data, InternalEvent ev);
	InternalEvent getEventType(TDataToSend data);
	TDataToReportResult convertIntoDataToReport(TDataToSend data);
}
