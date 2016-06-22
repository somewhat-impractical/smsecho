package net.darktrojan.smsecho;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

class SocketThread extends Thread {

	private static final String LOG_TAG = "SocketThread";

	private ServerSocket serverSocket;
	private EventSource currentEventSource = null;

	private boolean keepListening;
//	public static Map<String, String> documents;

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

			InputStream input = clientSocket.getInputStream();
			byte[] buffer = new byte[4096];
			int count = input.read(buffer);
			String request = new String(buffer, 0, count);

			String[] parts = request.split("\r\n\r\n", 2);
			String[] headers = parts[0].split("\r\n");
			String requestLine = headers[0];

			if (requestLine == null) {
				Log.d(LOG_TAG, "No request, wtf, socket: " + clientSocket.toString());
				clientSocket.close();
			} else {
				if (!requestLine.startsWith("GET ") || !requestLine.endsWith(" HTTP/1.1")) {
					Log.d(LOG_TAG, "Odd request, wtf: " + requestLine);
					clientSocket.close();
				} else {
					Log.d(LOG_TAG, "Request: " + requestLine);
					String[] requestLineParts = requestLine.split(" ");
					if (requestLineParts[1].equals("/eventsource")) {
						if (currentEventSource != null) {
							currentEventSource.close();
						}
						currentEventSource = new EventSource(clientSocket);
					} else {
						PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
						if (requestLineParts[1].equals("/test.html")) {
							output.print("HTTP/1.1 200 OK\r\n" +
									"Content-Type: text/html;charset=utf-8\r\n" +
									"Connection: close\r\n" +
									"\r\n");
							output.print("<html>test</html>\n");
//						} else if (documents.containsKey(requestLineParts[1])) {
//							output.print("HTTP/1.1 200 OK\r\n" +
//									"Content-Type: text/html;charset=utf-8\r\n" +
//									"Connection: close\r\n" +
//									"\r\n");
//							output.print(documents.get(requestLineParts[1]));
						} else {
							output.print("HTTP/1.1 404 Not Found\r\n" +
									"Connection: close\r\n" +
									"\r\n");
						}
						output.flush();
						output.close();
						clientSocket.close();
					}
				}
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

	public void sendMessage(String message) {
		if (currentEventSource != null) {
			currentEventSource.sendMessage(message);
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
				output.print("HTTP/1.1 200 OK\r\n" +
						"Content-Type: text/event-stream\r\n" +
						"Transfer-Encoding: identity\r\n" +
						"Connection: keep-alive\r\n" +
						"Access-Control-Allow-Origin: *\r\n" +
						"Access-Control-Allow-Methods: GET\r\n" +
						"Access-Control-Allow-Headers: Content-Type\r\n" +
						"\r\n");
				output.flush();
			} catch (IOException ex) {
				if (!ex.getMessage().equals("Socket closed")) {
					Log.e(LOG_TAG, "Failed to open server.", ex);
				}
			}
		}

		public void sendMessage(String message) {
			if (serverSocket == null || serverSocket.isClosed() || output == null) {
				Log.e(LOG_TAG, "Connection not open.");
				return;
			}
			output.write("event: ping\n" +
					"data: " + message + "\n\n");
			output.flush();
		}

		public void close() {
			try {
				if (output != null) {
					sendMessage("__close__");
					output.close();
				}
				if (clientSocket != null) {
					clientSocket.close();
				}
			} catch (IOException ex) {
				Log.d(LOG_TAG, "Failed to close server.", ex);
			}
		}
	}
}