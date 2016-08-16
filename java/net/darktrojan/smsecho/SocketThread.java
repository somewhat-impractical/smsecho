package net.darktrojan.smsecho;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

class SocketThread extends Thread {

	private static final String LOG_TAG = "SocketThread";

	private Handler eventHandler;
	private ServerSocket serverSocket;
	private EventSource currentEventSource = null;

	private boolean keepListening;

	public SocketThread(Handler handler) {
		eventHandler = handler;
	}

	@Override
	public void run() {
		try {
			serverSocket = new ServerSocket(6900);
			Log.d(LOG_TAG, "Server opened.");
		} catch (IOException ex) {
			Log.e(LOG_TAG, "Failed to open server.", ex);
			return;
		}

		keepListening = true;
		listen();
	}

	private void listen() {
		try {
			Socket clientSocket = serverSocket.accept();
			Log.d(LOG_TAG, "Connection successful.");

			BufferedInputStream input = new BufferedInputStream(clientSocket.getInputStream());
			// Adjust this if the total request size is likely to be bigger.
			byte[] buffer = new byte[4096];
			int count = input.read(buffer);
			String request = new String(buffer, 0, count);

			String[] parts = request.split("\r\n\r\n", 2);
			String[] headers = parts[0].split("\r\n");
			String requestLine = headers[0];

			if (requestLine == null) {
				Log.d(LOG_TAG, "No request, wtf, socket: " + clientSocket.toString());
			} else if (requestLine.equals("GET / HTTP/1.1")) {
				if (currentEventSource != null) {
					currentEventSource.close();
				}
				currentEventSource = new EventSource(clientSocket);
			} else if (requestLine.equals("POST /send HTTP/1.1")) {
				Bundle bundle = new Bundle();
				bundle.putStringArray("headers", headers);
				if (parts.length == 2) {
					bundle.putString("body", parts[1]);
				}
				Message message = eventHandler.obtainMessage();
				message.setData(bundle);
				eventHandler.sendMessage(message);

				PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
				output.print("HTTP/1.1 206 No Content\r\n" +
						"Connection: close\r\n" +
						"Access-Control-Allow-Origin: *\r\n" +
						"Access-Control-Allow-Methods: GET,POST\r\n" +
						"Access-Control-Allow-Headers: Content-Type\r\n" +
						"\r\n");
				output.flush();
				output.close();
				clientSocket.close();
			} else {
				Log.d(LOG_TAG, "Unknown request: " + requestLine);
			}
		} catch (IOException ex) {
			if (!ex.getMessage().equals("Socket closed")) {
				Log.e(LOG_TAG, "Failed to open server.", ex);
			}
		}

		if (keepListening) {
			listen();
		}
	}

	public void sendMessage(String eventType, String eventData) {
		if (currentEventSource == null) {
			Log.d(LOG_TAG, "no currentEventSource");
		} else {
			currentEventSource.sendMessage(eventType, eventData);
		}
	}

	public void close() {
		keepListening = false;
		try {
			if (currentEventSource != null) {
				currentEventSource.close();
			}
			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
			}
			Log.d(LOG_TAG, "Server closed.");
		} catch (IOException ex) {
			Log.d(LOG_TAG, "Failed to close server.", ex);
		}
	}

	private class EventSource {
		Socket clientSocket;
		PrintWriter output;

		public EventSource(Socket socket) {
			clientSocket = socket;

			try {
				output = new PrintWriter(clientSocket.getOutputStream(), true);
				output.print("HTTP/1.1 200 OK\r\n");
				output.print("Content-Type: text/event-stream\r\n");
				output.print("Transfer-Encoding: identity\r\n");
				output.print("Connection: keep-alive\r\n");
				output.print("Access-Control-Allow-Origin: *\r\n");
				output.print("Access-Control-Allow-Methods: GET\r\n");
				output.print("Access-Control-Allow-Headers: Content-Type\r\n");
				output.print("\r\n");
				output.flush();
			} catch (IOException ex) {
				if (!ex.getMessage().equals("Socket closed")) {
					Log.e(LOG_TAG, "Failed to open server.", ex);
				}
			}
		}

		public void sendMessage(String eventType, String eventData) {
			Log.v(LOG_TAG, "Sending " + eventType + " message");
			if (serverSocket == null || serverSocket.isClosed() || output == null) {
				Log.e(LOG_TAG, "Connection not open.");
				return;
			}
			if (clientSocket.isClosed()) {
				Log.e(LOG_TAG, "Client closed connection.");
				output = null;
				this.close();
				return;
			}
			output.write("event: " + eventType + "\n");
			output.write("data: " + eventData + "\n");
			output.write("\n");
			output.flush();
		}

		public void close() {
			try {
				if (output != null) {
					sendMessage("close", "");
					output.close();
				}
				if (clientSocket != null) {
					clientSocket.close();
				}
				currentEventSource = null;
			} catch (IOException ex) {
				Log.d(LOG_TAG, "Failed to close server.", ex);
			}
		}
	}
}