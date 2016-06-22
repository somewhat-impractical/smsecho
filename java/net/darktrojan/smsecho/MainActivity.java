package net.darktrojan.smsecho;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.View;

public class MainActivity extends Activity {

	static final String LOG_TAG = "MainActivity";

	SocketThread thread = null;
	IntentFilter smsFilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
	int testMessageCount = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

//		SocketThread.documents = new Hashtable<String, String>();
//		readFile("askpanel.html");
//		readFile("display.html");

		registerReceiver(smsReceiver, smsFilter);
	}

//	private void readFile(String name) {
//		try {
//			InputStream stream = getAssets().open(name);
//			byte[] buffer = new byte[stream.available()];
//			//noinspection ResultOfMethodCallIgnored
//			stream.read(buffer);
//			String content = new String(buffer);
//			SocketThread.documents.put("/" + name, content);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}

	protected void onStop() {
		try {
			unregisterReceiver(smsReceiver);
		} catch (IllegalArgumentException ex) {
			Log.d(LOG_TAG, "Couldn't unregister receiver.", ex);
		}

		super.onStop();
	}

	public void buttonClick(View view) {
		switch (view.getId()) {
			case R.id.button_start:
				if (thread == null) {
					Log.d(LOG_TAG, "Starting server.");
					thread = new SocketThread();
					thread.start();
				}
				break;
			case R.id.button_stop:
				if (thread != null) {
					Log.d(LOG_TAG, "Stopping server.");
					thread.close();
					thread = null;
				}
				break;
			case R.id.button_send:
				if (thread != null) {
					thread.sendMessage("Test message " + testMessageCount++);
				}
				break;
		}
	}

	BroadcastReceiver smsReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			Log.d(LOG_TAG, "Message received.");
			Bundle extras = intent.getExtras();
			if (extras == null) {
				return;
			}

			for (Object pdu : (Object[]) extras.get("pdus")) {
				SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu);
				if (thread != null) {
					thread.sendMessage(message.getMessageBody());
				}
			}
		}
	};
}
