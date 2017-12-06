package com.ipcam.asyncio;


public interface ISender<T>
{
	public enum SEND_RESULT
	{
		SUCCESS,
		SERVER_ERROR,
		NETWORK_ERROR,
		UNKNOWN
	}	
	public SEND_RESULT send(T letterToSend_);
	public void injectIOStreamProvider(IIOStreamProvider iosp);
}
