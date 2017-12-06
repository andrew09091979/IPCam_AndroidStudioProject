package com.ipcam.helper;

import java.util.Calendar;

public class FileName
{
    public static String DateTimeFileName()
    {
		Calendar date = Calendar.getInstance();

		String filename = String.format("%02d.%02d.%04d %02d.%02d.%02d",date.get(Calendar.DATE),
						  (date.get(Calendar.MONTH) + 1), date.get(Calendar.YEAR), date.get(Calendar.HOUR_OF_DAY), 
						  date.get(Calendar.MINUTE), date.get(Calendar.SECOND));
		return filename;
    }
    public static String AdminConsoleCommandsFilePath()
    {
    	return "/mnt/sdcard/IPCamFiles/";
    }
}
