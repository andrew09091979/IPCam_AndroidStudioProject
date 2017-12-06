package com.ipcam.mailsender;


public class SMTPParameters
{
	private String Portal = null;
	private String UserName = null;
	private String Pass = null;
	private String Receiver = null;
	private String Message = null;
	private String SmtpServerAddr = null;
	private String SmtpServerPort = null;

	public SMTPParameters(String Portal_, String UserName_, String Pass_, String Receiver_, String SmtpServerAddr_, String SmtpServerPort_)
	{
		Portal = Portal_;
		UserName = UserName_;
		Pass = Pass_;
		Receiver = Receiver_;
		SmtpServerAddr = SmtpServerAddr_;
		SmtpServerPort = SmtpServerPort_;
	}
	public String getPortal()
	{
		return Portal;
	}
	public String getUserName()
	{
		return UserName;
	}
	public String getPass()
	{
		return Pass;
	}
	public String getReceiver()
	{
		return Receiver;
	}
	public String getMessage()
	{
		return Message;
	}
	public String getSmtpServerAddr()
	{
		return SmtpServerAddr;
	}
	public String getSmtpServerPort()
	{
		return SmtpServerPort;
	}
	public void setMessage(String message_)
	{
		Message = message_;
	}
}