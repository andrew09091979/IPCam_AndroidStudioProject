package com.ipcam.helper;

import com.example.ipcam.R;
import com.ipcam.service.IPCamService;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;

public class ServiceStartIntentFactory
{
	static public Intent getServiceStartingIntentWithInfoFromPrefs(Context cont, String startedFrom)
	{
    	Intent intent = new Intent(cont, IPCamService.class);
    	Resources res = cont.getResources();
    	String startedFromIntentExtraName = res.getString(R.string.started_from_intent_extra_name);
    	String periodToPhotoPropertyName = res.getString(R.string.photo_period_intent_extra_name);
    	String photoQualityPropertyName = res.getString(R.string.photo_quality_index_intent_extra_name);
    	String mailLoginPropertyName = res.getString(R.string.mail_login_intent_extra_name);
    	String mailPasswordPropertyName = res.getString(R.string.mail_password_intent_extra_name);
    	String mailPortalPropertyName = res.getString(R.string.mail_portal_intent_extra_name);
    	String smtpServerAddrPropertyName = res.getString(R.string.smtp_server_addr_intent_extra_name);
    	String smtpServerPortPropertyName = res.getString(R.string.smtp_server_port_intent_extra_name);
    	String mailBoxDestPropertyName = res.getString(R.string.mail_box_to_send_to_intent_extra_name);
    	startedFromIntentExtraName = res.getString(R.string.started_from_intent_extra_name);

    	SharedPreferences Prefs = cont.getSharedPreferences(periodToPhotoPropertyName, 1);
    	String periodToPhotoStr = Prefs.getString(periodToPhotoPropertyName, "10");
    	String photoQualityStr = Prefs.getString(photoQualityPropertyName, "3");
    	String mailLogin = Prefs.getString(mailLoginPropertyName, "");
    	String mailPassword = Prefs.getString(mailPasswordPropertyName, "");
    	String mailPortal = Prefs.getString(mailPortalPropertyName, "");
    	String smtpServerAddr = Prefs.getString(smtpServerAddrPropertyName, "");
    	String smtpServerPort = Prefs.getString(smtpServerPortPropertyName, "");
    	String mailBoxToSendTo = Prefs.getString(mailBoxDestPropertyName, "");
    	intent.putExtra(periodToPhotoPropertyName, periodToPhotoStr);
    	intent.putExtra(photoQualityPropertyName, photoQualityStr);
    	intent.putExtra(startedFromIntentExtraName, startedFrom);
    	intent.putExtra(mailLoginPropertyName, mailLogin);
    	intent.putExtra(mailPasswordPropertyName, mailPassword);
    	intent.putExtra(mailPortalPropertyName, mailPortal);
    	intent.putExtra(smtpServerAddrPropertyName, smtpServerAddr);
    	intent.putExtra(smtpServerPortPropertyName, smtpServerPort);
    	intent.putExtra(mailBoxDestPropertyName, mailBoxToSendTo);

		return intent;
	}
}
