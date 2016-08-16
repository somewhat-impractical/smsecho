package net.darktrojan.smsecho;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;


public class MainActivity extends Activity {

	static final String LOG_TAG = "MainActivity";
	SocketThread socketThread;
	IntentFilter smsFilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
	SmsManager smsManager = SmsManager.getDefault();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Log.v(LOG_TAG, "onCreate");

		socketThread = new SocketThread(new Handler() {
			@Override
			public void handleMessage(Message message) {
				onMessageFromSocket(message);
			}
		});
		socketThread.start();

		registerReceiver(smsReceiver, smsFilter);
	}

	@Override
	protected void onDestroy() {
		Log.v(LOG_TAG, "onDestroy");
		try {
			socketThread.close();
			unregisterReceiver(smsReceiver);
		} catch (IllegalArgumentException ex) {
			Log.d(LOG_TAG, "Couldn't unregister receiver.", ex);
		}

		super.onDestroy();
	}

	int testMessageCount = 1;
	public void sendTestMessage(View view) {
		try {
			JSONObject json = new JSONObject();
			json.put("mobileNumber", "0900 99909");
			json.put("messageText", "test message " + testMessageCount++);
			sendMessageToSocket("sms", json.toString());
		} catch (JSONException ex) {
			Log.e(LOG_TAG, "This is extremely unlikely.", ex);
		}
	}

	private void onMessageFromSocket(Message message) {
		Bundle data = message.getData();
		String[] headers = data.getStringArray("headers");
		String body = data.getString("body");
		for (String h : headers) {
			Log.d(LOG_TAG, h);
		}
		if (body != null) {
			Log.d(LOG_TAG, body);
			try {
				JSONObject jsonObject = new JSONObject(new JSONTokener(body));
				String mobileNumber = jsonObject.getString("mobileNumber");
				String messageText = jsonObject.getString("messageText");
				if (false) {
					smsManager.sendTextMessage(mobileNumber, null, messageText, null, null);
				} else {
					Toast.makeText(this, "Send \"" + messageText + "\" to " + mobileNumber, Toast.LENGTH_SHORT).show();
				}
			} catch (JSONException ex) {
				Log.e(LOG_TAG, "This is extremely unlikely.", ex);
			}
		}
	}

	private void sendMessageToSocket(String eventType, String eventData) {
		if (socketThread != null) {
			socketThread.sendMessage(eventType, eventData);
		}
	}

	BroadcastReceiver smsReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			Log.d(LOG_TAG, "Message received.");
			Bundle extras = intent.getExtras();
			if (extras == null) {
				return;
			}

			try {
				for (Object pdu : (Object[]) extras.get("pdus")) {
					SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
					JSONObject json = new JSONObject();
					json.put("mobileNumber", sms.getOriginatingAddress());
					json.put("messageText", sms.getMessageBody());
					sendMessageToSocket("sms", json.toString());
				}
			} catch (NullPointerException ex) {
				Log.e(LOG_TAG, "This should never happen.", ex);
			} catch (JSONException ex) {
				Log.e(LOG_TAG, "This is extremely unlikely.", ex);
			}
		}
	};
}
