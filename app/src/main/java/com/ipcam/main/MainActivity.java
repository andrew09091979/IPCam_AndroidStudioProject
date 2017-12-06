package com.ipcam.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.example.ipcam.R;
import com.ipcam.photo.CameraActivity;
import com.ipcam.service.IPCamService;

import android.support.v7.app.ActionBarActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity 
{
    final String TAG = "IPCam";
	private EditText periodToPhotoEditBox = null;
	private EditText photoQualityEditBox = null;
	private EditText mailLoginEditBox = null;
	private EditText mailPasswordEditBox = null;
	private EditText mailPortalEditBox = null;
	private EditText smtpServerAddrEditBox = null;
	private EditText smtpServerPortEditBox = null;
	private EditText mailBoxDestEditBox = null;
	private SharedPreferences Prefs = null;
	private String periodToPhotoPropertyName = null;
	private String photoQualityPropertyName = null;
	private String mailLoginPropertyName = null;
	private String mailPasswordPropertyName = null;
	private String mailPortalPropertyName = null;
	private String smtpServerAddrPropertyName = null;
	private String smtpServerPortPropertyName = null;
	private String mailBoxDestPropertyName = null;

	private File log = null;
    private FileOutputStream logfos = null;
	private String startedFromIntentExtraName = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		////writeToLog("MainActivity: onCreate");
    	Log.d(TAG, "MainActivity: onCreate");
    	
    	final Button startServBtn = (Button)findViewById(R.id.button1);
    	startServBtn.setOnClickListener(new View.OnClickListener() 
    	{
            public void onClick(View v) 
            {
            	Log.d(TAG, "onClick Start Service");
            	startSrv();
            }
        });
    	final Button stopServBtn = (Button)findViewById(R.id.button2);    	
    	stopServBtn.setOnClickListener(new View.OnClickListener() 
    	{
            public void onClick(View v) 
            {
            	Log.d(TAG, "onClick Stop Service");
            	stopSrv();            	
            }
        });
    	periodToPhotoEditBox = (EditText)findViewById(R.id.editText1);
    	photoQualityEditBox = (EditText)findViewById(R.id.editText2);
    	mailLoginEditBox = (EditText)findViewById(R.id.editText3);
    	mailPasswordEditBox = (EditText)findViewById(R.id.editText4);
    	mailPortalEditBox = (EditText)findViewById(R.id.editText5);
    	smtpServerAddrEditBox = (EditText)findViewById(R.id.editText6);
    	smtpServerPortEditBox = (EditText)findViewById(R.id.editText7);
    	mailBoxDestEditBox = (EditText)findViewById(R.id.editText8);

    	Resources res = getResources();
    	periodToPhotoPropertyName = res.getString(R.string.photo_period_intent_extra_name);
    	photoQualityPropertyName = res.getString(R.string.photo_quality_index_intent_extra_name);
    	mailLoginPropertyName = res.getString(R.string.mail_login_intent_extra_name);
    	mailPasswordPropertyName = res.getString(R.string.mail_password_intent_extra_name);
    	mailPortalPropertyName = res.getString(R.string.mail_portal_intent_extra_name);
    	smtpServerAddrPropertyName = res.getString(R.string.smtp_server_addr_intent_extra_name);
    	smtpServerPortPropertyName = res.getString(R.string.smtp_server_port_intent_extra_name);
    	mailBoxDestPropertyName = res.getString(R.string.mail_box_to_send_to_intent_extra_name);

    	startedFromIntentExtraName = res.getString(R.string.started_from_intent_extra_name);
    	Prefs = getSharedPreferences(periodToPhotoPropertyName, 1);
    	String periodToPhotoStr = Prefs.getString(periodToPhotoPropertyName, "10");
    	periodToPhotoEditBox.setText(periodToPhotoStr);
    	String photoQualityStr = Prefs.getString(photoQualityPropertyName, "3");
    	photoQualityEditBox.setText(photoQualityStr);
    	String mailLogin = Prefs.getString(mailLoginPropertyName, "");
    	mailLoginEditBox.setText(mailLogin);
    	String mailPassword = Prefs.getString(mailPasswordPropertyName, "");
    	mailPasswordEditBox.setText(mailPassword);
    	String mailPortal = Prefs.getString(mailPortalPropertyName, "");
    	mailPortalEditBox.setText(mailPortal);
    	String smtpServerAddr = Prefs.getString(smtpServerAddrPropertyName, "");
    	smtpServerAddrEditBox.setText(smtpServerAddr);
    	String smtpServerPort = Prefs.getString(smtpServerPortPropertyName, "");
    	smtpServerPortEditBox.setText(smtpServerPort);
    	String mailBoxToSendTo = Prefs.getString(mailBoxDestPropertyName, "");
    	mailBoxDestEditBox.setText(mailBoxToSendTo);
/*
    	final Button startCamBtn = (Button)findViewById(R.id.button3);    	
    	startCamBtn.setOnClickListener(new View.OnClickListener() 
    	{
            public void onClick(View v) 
            {
            	Log.d(TAG, "onClick Start cam");
            	start_camera();            	
            }
        });
*/
	}

	protected void start_camera()
	{
        Intent intent = new Intent(this, CameraActivity.class);
    	startActivityForResult(intent, 0);			
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
    	Log.d(TAG, "MainActivity: onCreateOptionsMenu");
		////writeToLog("MainActivity: onCreateOptionsMenu");
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
    	Log.d(TAG, "MainActivity: onDestroy");
		////writeToLog("MainActivity: onDestroy");
    	try
    	{
    		////writeToLog("onDestroy: closing logfos");

    		if (logfos != null)
			    logfos.close();
    		else
    			Log.e(TAG, "stop: logfos is null");
		}
    	catch (IOException e)
    	{
			e.printStackTrace();
		}    	
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void startSrv()
	{
    	Resources res = getResources();
		String periodToPhotoStr = periodToPhotoEditBox.getText().toString();
		String photoQualityStr = photoQualityEditBox.getText().toString();
    	String mailLoginStr = mailLoginEditBox.getText().toString();
    	String mailPasswordStr = mailPasswordEditBox.getText().toString();
    	String mailPortalStr = mailPortalEditBox.getText().toString();
    	String smtpServerAddrStr = smtpServerAddrEditBox.getText().toString();
    	String smtpServerPortStr = smtpServerPortEditBox.getText().toString();
    	String mailBoxToSendToStr = mailBoxDestEditBox.getText().toString();

//save entered agent IP
		SharedPreferences.Editor ed = Prefs.edit();
        ed.putString(periodToPhotoPropertyName, periodToPhotoStr);
        ed.putString(photoQualityPropertyName, photoQualityStr);
        ed.putString(mailLoginPropertyName, mailLoginStr);
        ed.putString(mailPasswordPropertyName, mailPasswordStr);
        ed.putString(mailPortalPropertyName, mailPortalStr);
        ed.putString(smtpServerAddrPropertyName, smtpServerAddrStr);
        ed.putString(smtpServerPortPropertyName, smtpServerPortStr);
        ed.putString(mailBoxDestPropertyName, mailBoxToSendToStr);
        ed.commit();

    	Intent intent = new Intent(this, IPCamService.class);
/*
    	Log.d(TAG, "Putting into intent name = " + periodToPhotoPropertyName + " periodToPhotoStr = " + periodToPhotoStr);
    	Log.d(TAG, "Putting into intent name = " + photoQualityPropertyName + " photoQualityStr = " + photoQualityStr);
    	Log.d(TAG, "Putting into intent name = " + mailLoginPropertyName + " mailLoginStr = " + mailLoginStr);
    	Log.d(TAG, "Putting into intent name = " + mailPasswordPropertyName + " mailPasswordStr = " + mailPasswordStr);
    	Log.d(TAG, "Putting into intent name = " + mailPortalPropertyName + " mailPortalStr = " + mailPortalStr);
    	Log.d(TAG, "Putting into intent name = " + smtpServerAddrPropertyName + " smtpServerAddrStr = " + smtpServerAddrStr);
    	Log.d(TAG, "Putting into intent name = " + smtpServerPortPropertyName + " smtpServerPortStr = " + smtpServerPortStr);
    	Log.d(TAG, "Putting into intent name = " + mailBoxDestPropertyName + " mailBoxToSendTpStr = " + mailBoxToSendToStr);
*/
    	intent.putExtra(periodToPhotoPropertyName, periodToPhotoStr);
    	intent.putExtra(photoQualityPropertyName, photoQualityStr);
    	intent.putExtra(mailLoginPropertyName, mailLoginStr);
    	intent.putExtra(mailPasswordPropertyName, mailPasswordStr);
    	Toast.makeText(this, mailPasswordStr, Toast.LENGTH_LONG).show();
    	intent.putExtra(mailPortalPropertyName, mailPortalStr);
    	intent.putExtra(smtpServerAddrPropertyName, smtpServerAddrStr);
    	intent.putExtra(smtpServerPortPropertyName, smtpServerPortStr);
    	intent.putExtra(mailBoxDestPropertyName, mailBoxToSendToStr);
    	intent.putExtra(startedFromIntentExtraName, "MainActivity");
    	Log.d(TAG, "startingService");
    	startService(intent);
	}
	private void stopSrv()
	{
    	Intent intent = new Intent(this, IPCamService.class);
    	Log.d(TAG, "stoppingService");
    	stopService(intent);
	}
/*
    private void writeToLog(String msg)
    {
    	if (log == null)
    	{
        	String logname = "mainActivitylog_" + CameraActivity.buildPhotoFileName();
	    	log = new File("/mnt/sdcard/MyCameraApp/" + logname + ".txt");
	
	    	try
	    	{
	    		log.createNewFile();
				logfos = new FileOutputStream(log);
			}
	    	catch (FileNotFoundException e)
			{
	            Log.e(TAG, "Photographer: exception while FileOutputStream creation: " + e.getMessage());
				e.printStackTrace();
			}
	    	catch (IOException e)
	    	{
	            Log.e(TAG, "Photographer: exception while File creation: " + e.getMessage());
				e.printStackTrace();
			}
    	}
		try
		{
			msg += "\n";
			logfos.write(msg.getBytes());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
    }
*/
}
