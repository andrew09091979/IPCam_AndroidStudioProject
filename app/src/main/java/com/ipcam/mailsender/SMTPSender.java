package com.ipcam.mailsender;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.ipcam.asyncio.IIOStreamProvider;
import com.ipcam.asyncio.ISender;
import com.ipcam.internalevent.IInternalEventInfo;
import com.ipcam.internalevent.InternalEvent;

import android.util.Base64;
import android.util.Log;

public class SMTPSender implements ISender<IInternalEventInfo>
{
	private final String TAG = "SMTPSender";
	
	private SMTPParameters mySMTPparams = null;
	private boolean eraseFileAfterSuccessfulSending = false;
    private IIOStreamProvider ioStreamProvider = null; 

	public SMTPSender(SMTPParameters mySMTPparams_)
	{
		mySMTPparams = mySMTPparams_;
	}  
    public void prepareSend(IInternalEventInfo letterToSend_)
    {
    }
	@Override
	public void injectIOStreamProvider(IIOStreamProvider iosp)
	{
		ioStreamProvider = iosp;
	}
	@Override
	public SEND_RESULT send(IInternalEventInfo letterToSend_)
	{
		SEND_RESULT result = SEND_RESULT.UNKNOWN;
		DataOutputStream os = null;
		DataInputStream is = null;
		String filePathToSend = null;

    	Log.d(TAG, "SMTPSender: sendLetter started, creating FSM");
		SMTP_FSM smtp_fsm = new SMTP_FSM(mySMTPparams, letterToSend_);
		
		if ((letterToSend_ != null) && (letterToSend_.getFilesNum() > 0))
			filePathToSend = letterToSend_.getFile(0);
		
		if (filePathToSend == null)
		{
			Log.e(TAG, "SMTPSender: filePathToSend is null, no files to send");
		}
		else
		{
		    Log.d(TAG, "SMTPSender: sending file " + filePathToSend);
		}
		
        try
        {
        	if (ioStreamProvider != null)
        	{
        		OutputStream outputStream = ioStreamProvider.getOutputStream();
        		InputStream inputStream = ioStreamProvider.getInputStream();

        		if ((outputStream != null) || (inputStream != null))
        		{
	                os=new DataOutputStream(outputStream);
	                is=new DataInputStream(inputStream);
        		}
        		else
        		{
        			Log.e(TAG, "ioStreamProvider returned null as output or input stream");
        		}
        	}
        	else
        	{
    			Log.e(TAG, "ioStreamProvider is null");
        	}
    		if ((is != null) || (os != null))
	        {
	        	Log.d(TAG, "streams are not null");
	            int avail = is.available();
	
	        	byte bfr[] = new byte[5000];
	    		avail = is.read(bfr);
	    		//Log.d(TAG, new String(bfr, 0, avail));
	    		int processingResult = 0;

	        	do
	        	{
	        		byte [] msg = smtp_fsm.getMessage();

	        		if (msg != null)
	        		{
	        			Log.d(TAG, "writing " + Integer.toString(msg.length) + " bytes");
	        		    os.write(msg);
	        		}
	        		else
	        		{
	        			Log.d(TAG, "nothing to send msg is null");
	        		}
	        		if (smtp_fsm.needToReadAnswer())
	        		{
	        		    avail = is.read(bfr);
	        		    //Log.d(TAG, "available " + Integer.toString(avail) + " bytes");

	        		    if (avail > 0)
	        			    Log.d(TAG, new String(bfr, 0, avail));

	        		    processingResult = smtp_fsm.processAnswer(bfr);
	        		}
	        	}
	        	while(processingResult == 0);

                if (processingResult == 1)
                	result = SEND_RESULT.SUCCESS;
                else if (processingResult == -1)
                	result = SEND_RESULT.SERVER_ERROR;
                else
                	result = SEND_RESULT.UNKNOWN;
	        }
        }
        catch(UnknownHostException e)
        {
            result = SEND_RESULT.NETWORK_ERROR;        	
        	Log.e(TAG,"UnknownHostException: " + e.getMessage());
        }
        catch(IOException e)
        {
            result = SEND_RESULT.NETWORK_ERROR;
        	Log.e(TAG,"IOException: " + e.getMessage());
        }
		if (ioStreamProvider != null)
		{
			Log.d(TAG, "calling ioStreamProvider.closeAll");
			ioStreamProvider.closeAll();
		}
		else
		{
			Log.e(TAG, "ioStreamProvider is null, can't call closeAll");
		}
        Log.d(TAG, "sendLetter return=" + result.toString());
        letterToSend_.setInternalEvent(InternalEvent.TASK_COMPLETE);

		return result;
	}
}

class SMTP_FSM
{
	enum STATE
	{
		EHLO,
		AUTH_LOGIN,
		SEND_LOGIN,
		SEND_PASS,
		MAIL_FROM,
		RCPT_TO,
		DATA,
		BODY,
		QUIT
	}
	class SMTP_STATE_INFO
	{
		public SMTP_STATE_INFO(STATE next_state, int ok_code)
		{
			NEXT_STATE = next_state;
			OK_CODE = ok_code;
		}
		public STATE NEXT_STATE;
		public int OK_CODE;
	}
	enum BODY_SENDING_STATE
	{
		INLINE,
		ATTACHMENT,
		FINISHED
	}

	private final Map<STATE, SMTP_STATE_INFO> SMTP_STATE_INFO_MAP = new HashMap<STATE, SMTP_STATE_INFO>();

	private final String TAG = "SMTPSender";
	private STATE state;
	private BODY_SENDING_STATE bodySendingState;
	private SMTPParameters parameters;
	private IInternalEventInfo letterToSend;
	private final String MIME_VERSION_1_0 = "MIME-Version: 1.0\n";
	private final String CONTENT_TRANSFER_ENCODING_BASE64 = "Content-Transfer-Encoding: base64\n";
	private final String CONTENT_TRANSFER_ENCODING_7BIT = "Content-Transfer-Encoding: 7bit\n";
	private final String CONTENT_TYPE_JPEG = "Content-Type: image/jpeg; ";
	private final String CONTENT_TYPE_TEXT_PLAIN = "Content-Type: text/plain; ";
	private final String CONTENT_TYPE_MULTIPART_MIXED = "Content-Type: multipart/mixed; ";
	private final String CONTENT_TYPE_MULTIPART_ALTERNATIVE = "Content-Type: multipart/alternative; ";
	private final String CONTENT_DISPOSITION_ATTACHMENT = "Content-Disposition: attachment; ";
	private final String NAME_FIELD = "name=";
	private final String FILENAME_FIELD = "filename=";
	private final String CONTENT_DISPOSITION_INLINE = "Content-Disposition: inline\n";
	private final String CONTENT_TRANSFER_ENCODING_PRINTABLE = "Content-Transfer-Encoding: quoted-printable\n";
	private final String CONTENT_TRANSFER_ENCODING_8BIT = "Content-Transfer-Encoding: 8bit\n";	
	private final String CONTENT_TYPE_TEXT_PLAIN_FLOWED = "Content-Type: text/plain; charset=\"utf-8\"; format=\"flowed\"\n";

	private final String CONTENT_TYPE_TEXT_HTML = "Content-Type: text/html; charset=\"utf-8\"";

	private final String BOUNDARY_FIELD = "boundary=";
	private final String BOUNDARY_FOR_ALTERNATIVE = "_----------=_21323424242425638";
	private final String BOUNDARY_FOR_MIXED = "_----------=_21323424242425637";
	private final String THIS_IS_MULTIPART_MESSAGE = "\nThis is a multi-part message in MIME format.\n\n";
	
	private int currentFileNum = 0;
	private boolean needToReadAnswer;

	public SMTP_FSM(SMTPParameters parameters_, IInternalEventInfo letterToSend_)
	{
		parameters = parameters_;
		letterToSend = letterToSend_;
		state = STATE.EHLO;
		bodySendingState = BODY_SENDING_STATE.INLINE;
		SMTP_STATE_INFO_MAP.put(STATE.EHLO,       new SMTP_STATE_INFO(STATE.AUTH_LOGIN, 250));
		SMTP_STATE_INFO_MAP.put(STATE.AUTH_LOGIN, new SMTP_STATE_INFO(STATE.SEND_LOGIN, 334));
		SMTP_STATE_INFO_MAP.put(STATE.SEND_LOGIN, new SMTP_STATE_INFO(STATE.SEND_PASS, 334));
		SMTP_STATE_INFO_MAP.put(STATE.SEND_PASS,  new SMTP_STATE_INFO(STATE.MAIL_FROM, 235));
		SMTP_STATE_INFO_MAP.put(STATE.MAIL_FROM,  new SMTP_STATE_INFO(STATE.RCPT_TO, 250));
		SMTP_STATE_INFO_MAP.put(STATE.RCPT_TO,    new SMTP_STATE_INFO(STATE.DATA, 250));
		SMTP_STATE_INFO_MAP.put(STATE.DATA,       new SMTP_STATE_INFO(STATE.BODY, 354));
		SMTP_STATE_INFO_MAP.put(STATE.BODY,       new SMTP_STATE_INFO(STATE.QUIT, 250));
		SMTP_STATE_INFO_MAP.put(STATE.QUIT,       new SMTP_STATE_INFO(STATE.EHLO, 250));
	}

	public byte [] getMessage()
	{
		byte [] msgBytes = null;
		
		switch (state)
		{
			case EHLO:
			{
				String msgStr = "EHLO " + parameters.getPortal() + "\n";
				msgBytes = msgStr.getBytes();
				needToReadAnswer = true;
				return msgBytes; 
			}
			case AUTH_LOGIN:
			{
				String msgStr = "AUTH LOGIN\n";;
				msgBytes = msgStr.getBytes();
				needToReadAnswer = true;
				return msgBytes; 				
			}
			case SEND_LOGIN:
			{
            	byte baddr[] = parameters.getUserName().getBytes();
            	byte baddrencoded[] = Base64.encode(baddr, Base64.DEFAULT);
            	String addr = new String(baddrencoded, 0, baddrencoded.length);
				needToReadAnswer = true;
				return addr.getBytes();
			}
			case SEND_PASS:
			{
            	byte baddr[] = parameters.getPass().getBytes();
            	byte baddrencoded[] = Base64.encode(baddr, Base64.DEFAULT);
            	String pass = new String(baddrencoded, 0, baddrencoded.length);
				needToReadAnswer = true;
				return pass.getBytes();
			}
			case MAIL_FROM:
			{
				String msgStr = "mail from:<" + parameters.getUserName() + "@" + parameters.getPortal() + ">\n";
				msgBytes = msgStr.getBytes();
				needToReadAnswer = true;
				return msgBytes; 					
			}
			case RCPT_TO:
			{
				String msgStr = "rcpt to:<" + parameters.getReceiver() + ">\n";
				msgBytes = msgStr.getBytes();
				needToReadAnswer = true;
				return msgBytes; 					
			}
			case DATA:
			{
				String msgStr = "DATA\n";
				msgBytes = msgStr.getBytes();
				needToReadAnswer = true;
				return msgBytes; 					
			}
			case BODY:
			{
				String msgStr = getNextPart();
				msgBytes = msgStr.getBytes();						
				return msgBytes; 				
			}
			case QUIT:
			{
				String msgStr = "QUIT\n";
				msgBytes = msgStr.getBytes();
				needToReadAnswer = true;
				return msgBytes;
			}
		}
		return msgBytes;
	}

	public int processAnswer(byte msg[])
	{
		int res = 0;
		int serverResCode = Integer.parseInt(new String(msg,0, 3));

		Log.i(TAG, "in ProcessAnswer: state = " + state.toString() + " result from server = " + serverResCode);

		if (serverResCode != SMTP_STATE_INFO_MAP.get(state).OK_CODE)
		{
			res = -1;
		}

		if (state == STATE.QUIT)
			res = 1;

		state = SMTP_STATE_INFO_MAP.get(state).NEXT_STATE;

		return res;
	}

	public boolean needToReadAnswer()
	{
		return needToReadAnswer;
	}

	private String getNextPart()
	{
		String message = null;

		switch(bodySendingState)
		{
			case INLINE:
			{
	            message = new String("Subject: ");
	            message += letterToSend.getHeadline() + "\n";

	            if (letterToSend.getFilesNum() > 0)
	            {

//	            	message += CONTENT_TRANSFER_ENCODING_7BIT;
		            message += MIME_VERSION_1_0;
	            	message += CONTENT_TYPE_MULTIPART_MIXED + BOUNDARY_FIELD + "\"" + BOUNDARY_FOR_MIXED + "\"\n";
	            	message += THIS_IS_MULTIPART_MESSAGE;
	            	message += "--" + BOUNDARY_FOR_MIXED + "\n";
/*
	            	message += CONTENT_TYPE_MULTIPART_ALTERNATIVE + BOUNDARY_FIELD + "\"" + BOUNDARY_FOR_ALTERNATIVE + "\"\n";
	            	message += "\n" + THIS_IS_MULTIPART_MESSAGE + "\n";
	            	message += "--" + BOUNDARY_FOR_ALTERNATIVE+ "\n";
	            	message += CONTENT_DISPOSITION_INLINE;
	            	message += CONTENT_TRANSFER_ENCODING_PRINTABLE;
	            	message += CONTENT_TYPE_TEXT_PLAIN_FLOWED;
	            	message += "\n" + parameters.getLetter().getMessage() + "\n\n.\n\n";
	            	message += "--" + BOUNDARY_FOR_ALTERNATIVE + "\n";
	            	message += CONTENT_DISPOSITION_INLINE;
	            	message += CONTENT_TRANSFER_ENCODING_PRINTABLE;
	            	message += CONTENT_TYPE_TEXT_HTML;
	            	message += "<p>&nbsp;" + parameters.getLetter().getMessage() + "</p> \n <div id=3D\"editor_compose_signature\"> <p>.</p> </div>=\n\n"; 
	            	message += "--" + BOUNDARY_FOR_ALTERNATIVE + "\n";	            	
*/
	            	message += CONTENT_DISPOSITION_INLINE;
     	            message += CONTENT_TRANSFER_ENCODING_8BIT;
	                message += CONTENT_TYPE_TEXT_PLAIN_FLOWED + "\n";
	                message += letterToSend.getMessage() + "\n\n";
	            	message += "--" + BOUNDARY_FOR_MIXED + "\n";
					needToReadAnswer = false;
		            bodySendingState = BODY_SENDING_STATE.ATTACHMENT;
	            	//Log.d(TAG, "Sending assembled inline message with file: " + message);
	            }
	            else
	            {
		            message += MIME_VERSION_1_0;
	                message += CONTENT_DISPOSITION_INLINE;
     	            message += CONTENT_TRANSFER_ENCODING_PRINTABLE;
	                message += CONTENT_TYPE_TEXT_PLAIN_FLOWED + "\n";
	                message += letterToSend.getMessage() + "\n\n.\n";
					needToReadAnswer = true;
	                bodySendingState = BODY_SENDING_STATE.FINISHED;
	                //Log.d(TAG, "Sending assembled inline message with no files: " + message);
	            }
			}
			break;

			case ATTACHMENT:
			{
				message = new String();//"--" + BOUNDARY_FOR_MIXED + "\n\n");
				String filePath = letterToSend.getFile(currentFileNum);
				File fileToSend = new File(filePath);
	            FileInputStream fis;

				try
				{
					message += CONTENT_DISPOSITION_ATTACHMENT + FILENAME_FIELD + "\"" + fileToSend.getName() + "\"\n";
					message += CONTENT_TRANSFER_ENCODING_BASE64;
					message += CONTENT_TYPE_JPEG + NAME_FIELD + "\"" + fileToSend.getName() + "\"\n\n";
					fis = new FileInputStream(fileToSend);
		            long length = fileToSend.length();
		            Log.d(TAG, "file length = " + Long.toString(length));
		            
		            byte [] pic = new byte[(int)length];
		            fis.read(pic);
		            byte picEncoded[] = Base64.encode(pic, Base64.DEFAULT);
		            message += new String(picEncoded);
		            picEncoded = null;
		            pic = null;
				}
				catch (FileNotFoundException e)
				{
					e.printStackTrace();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				message += "\n--" + BOUNDARY_FOR_MIXED;
				++currentFileNum;
				
				if (currentFileNum == letterToSend.getFilesNum())
				{
					
					message += "--\n\n.\n";
					needToReadAnswer = true;
					bodySendingState = BODY_SENDING_STATE.FINISHED;				
				}
				else
				{
					message += "\n";
				}
			}
			break;
		}
		return message;
	}
}
/*
Return-Path: <yak_2003@rambler.ru>
Received: from [10.8.18.1] (HELO mx1.mail.rambler.ru)
  by mail180.rambler.ru (rmaild SMTP 1.2.41)
  with ESMTP id 350408154 for anjak_1@rambler.ru; Sun, 01 May 2016 20:50:40 +0300
Received: from mxout4.rambler.ru (mxout4.rambler.ru [81.19.67.207])
	by mx1.mail.rambler.ru (Postfix) with ESMTP id A6F1923C4
	for <anjak_1@rambler.ru>; Sun,  1 May 2016 20:50:40 +0300 (MSK)
Received: from saddam4.rambler.ru (saddam4.rambler.ru [10.32.16.4])
	by mxout4.rambler.ru (Postfix) with ESMTP id 8D7CF140278
	for <anjak_1@rambler.ru>; Sun,  1 May 2016 20:50:40 +0300 (MSK)
DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed; d=rambler.ru; s=mail;
	t=1462125040; bh=a0Xp94OMJ/f+61L1YJ5pDo9hgqO6ouMshGHFkSVdhLc=;
	h=From:To:Reply-To:Subject:Date;
	b=noxEfN0co3X7r+0yrGsqOsM7Fh2+QLeuk9k4VFqcpr8+H5ms4mJ0ie+gzmy0CzDxn
	 fE1fDdQYKW4sQ/9w9vfNqTt1baSCGwZaK9HuF52Usapi1klUn9yje+QsISA9aFbBdU
	 HyfiiDwChO5ns82VJJmhNgdHoltyQUN6WA901QJ4=
Received: from localhost.localdomain (localhost [127.0.0.1])
	by saddam4.rambler.ru (Postfix) with ESMTP id 7AFC75AF9272
	for <anjak_1@rambler.ru>; Sun,  1 May 2016 20:50:40 +0300 (MSK)
Received: from [93.115.95.216] by mail.rambler.ru with HTTP; Sun, 1 May 2016 20:50:40 +0300
From: =?koi8-r?B?4c7E0sXKIPHLz9fMxdc=?= <yak_2003@rambler.ru>
To: anjak_1@rambler.ru
Reply-To: =?koi8-r?B?4c7E0sXKIPHLz9fMxdc=?= <yak_2003@rambler.ru>
Subject: mail test
Date: Sun, 1 May 2016 20:50:40 +0300
Content-Transfer-Encoding: 7bit
Content-Type: multipart/mixed; boundary="_----------=_14621250402854790"
Message-Id: <1462125040.362701.28547.48155@mail.rambler.ru>
MIME-Version: 1.0
X-Mailer: Rambler WebMail, http://mail.rambler.ru/
X-Rambler-User: yak_2003@rambler.ru/93.115.95.216


This is a multi-part message in MIME format.

--_----------=_14621250402854790
Content-Type: multipart/alternative; boundary="_----------=_14621250402854791"

This is a multi-part message in MIME format.

--_----------=_14621250402854791
Content-Disposition: inline
Content-Transfer-Encoding: quoted-printable
Content-Type: text/plain; charset="utf-8"; format="flowed"

Hello! this is testing mail with photo included.

Andrew.

--_----------=_14621250402854791
Content-Disposition: inline
Content-Transfer-Encoding: quoted-printable
Content-Type: text/html; charset="utf-8"

<p>Hello! this is testing mail with photo included.</p>
<p>Andrew.</p>=

--_----------=_14621250402854791--

--_----------=_14621250402854790
Content-Disposition: attachment; filename="battery-icon.jpeg"
Content-Transfer-Encoding: base64
Content-Type: image/jpeg; name="battery-icon.jpeg"

/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAMDAwMDAwMDAwMEAwMDBQYFAwMF
BgcGBgYGBgcJBwcHBwcHCQgJCQoJCQgMDA0NDAwRERERERISEhISEhISEhL/
2wBDAQQEBAcGBw4JCQ4RDgsOERQTExMTFBQSEhISEhISEhISEhISEhISEhIS
EhISEhISEhISEhISEhISEhISEhISEhL/wAARCAFoAeADAREAAhEBAxEB/8QA
HQABAAEFAQEBAAAAAAAAAAAAAAECAwQFBwYICf/EAFAQAAECAwMGCQcKAwYE
BwAAAAABAgMEBRETFAYSITFUYQcVFkFRVWKSkyJTcZGUodIXIyQyM0JScoGx
JjTBCCU2Y3OCQ0SisjVFZHSj0fH/xAAcAQEAAgMBAQEAAAAAAAAAAAAAAQID
BAYHBQj/xAA0EQEAAQMCBAMGBgIDAQEAAAAAAQIREhVSAwQTYhQhkQUGMTJR
kiI0QXFygiSBIzNCYeH/2gAMAwEAAhEDEQA/APyqAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
*/

/*
Return-Path: <yak_2003@rambler.ru>
Received: from [10.8.18.3] (HELO mx3.mail.rambler.ru)
  by mail180.rambler.ru (rmaild SMTP 1.2.41)
  with ESMTP id 350408863 for anjak_1@rambler.ru; Sun, 01 May 2016 20:57:06 +0300
Received: from huan1.mail.rambler.ru (huan1.mail.rambler.ru [81.19.66.27])
	by mx3.mail.rambler.ru (Postfix) with ESMTP id 44EDF9402B9
	for <anjak_1@rambler.ru>; Sun,  1 May 2016 20:57:06 +0300 (MSK)
DKIM-Signature: v=1; a=rsa-sha256; q=dns/txt; c=relaxed/relaxed; d=rambler.ru; s=mail;
	h=Date:From:Message-Id:Content-Type:Content-Transfer-Encoding:MIME-Version:Subject; bh=mpgJDqeUDNeE3JpKLC84P00U0J4knJBJiV0XSv8XKCw=;
	b=l1KaS/Js257BO+VIxX0Vsqe1yFuoMjpyXA5SiSfHziQNSBwDkC3UqaQAjvCIL8xbPTiMyCSpuTvUQSE7miK61d8YHSPrus7bVoztItslogygjpGnbUt913mCGC0eQB8poDIdHN0rH184JGAorCXCkz/6PpxyTWBaZRYzf/OAwjk=;
Received: from [UNAVAILABLE] ([178.66.254.63]:54617 helo=rambler.ru)
	by huan1.mail.rambler.ru with esmtpa (Exim 4.76)
	(envelope-from <yak_2003@rambler.ru>)
	id 1awvbU-0005ex-RE
	for anjak_1@rambler.ru; Sun, 01 May 2016 20:57:06 +0300
Subject: Shot was taken per timer
MIME-Version: 1.0
Content-Disposition: attachment; filename="IMG_01.05.2016 20.55.32.jpg"
Content-Transfer-Encoding: base64
Content-Type: image/jpeg; name="IMG_01.05.2016 20.55.32.jpg"
Message-Id: <E1awvbU-0005ex-RE@huan1.mail.rambler.ru>
From: yak_2003@rambler.ru
Date: Sun, 01 May 2016 20:56:25 +0300
X-Rambler-User: yak_2003@rambler.ru/178.66.254.63

/9j/4UJYRXhpZgAASUkqAAgAAAAJAAABBAABAAAAAAoAAAEBBAABAAAAgAcAAA8BAgAIAAAAegAA
ABABAgAJAAAAggAAABIBAwABAAAAAQAAADEBAgALAAAAiwAAADIBAgAUAAAAlgAAABMCAwABAAAA
AQAAAGmHBAABAAAAqgAAAOYBAABTQU1TVU5HAEdULVM1NjcwAFM1NjcwWFhLUFEAMjAxNjowNTow
MSAyMDo1NTozMgASAJqCBQABAAAAiAEAAJ2CBQABAAAAkAEAACKIAwABAAAAAwAAACeIAwABAAAA
kAEAAACQBwAEAAAAMDIyMAOQAgAUAAAAmAEAAASQAgAUAAAArAEAAAWSBQABAAAAwAEAAAeSAwAB
AAAAAQAAAAmSAwABAAAAEqgAAAqSBQABAAAAyAEAAIaSBwAWAAAA0AEAAAGgAwABAAAAAQAAAAKg
BAABAAAAAAoAAAOgBAABAAAAgAcAAAKkBAABAAAAAAAAAAOkBAABAAAAAAAAAAakBAABAAAAAAAA
AAAAAAABAAAACAAAABoAAAAKAAAAMjAxNjowNTowMSAyMDo1NTozMgAyMDE2OjA1OjAxIDIwOjU1
*/