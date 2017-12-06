package com.ipcam.asyncio;

import java.io.InputStream;
import java.io.OutputStream;

public interface IIOStreamProvider
{
    public OutputStream getOutputStream();
    public InputStream getInputStream();
    public void closeAll();
}
