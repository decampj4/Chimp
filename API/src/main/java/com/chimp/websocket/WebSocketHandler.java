package com.chimp.websocket;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.chimp.constants.Constants;

public class WebSocketHandler extends TextWebSocketHandler{
	//Socket connection to the controller
	Socket socket = null;

	//Map to keep track of which sessions have been authenticated
	protected Map<String, String> sessionAuthMap = new ConcurrentHashMap<String, String>();
	//Map to keep track of which sessions are associated with a user, keyed by user
	protected Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<String, Set<WebSocketSession>>();

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage rcvdRessage) {
		if(socket == null){
			//Our TCP connection to the controller hasn't been established yet. Let's do that
			initSocketConnection();
		}

		try {
			//Check to see if the session is registered
			if(!sessionAuthMap.containsKey(session.getId()) || StringUtils.isBlank(sessionAuthMap.get(session.getId()))){
				//The session is not authenticated
				System.out.println("Session " + session.getId() + " is valid but not authenticated. Looking for username in JSON payload");
				boolean authenticated = false;
				JSONObject jsonObj = null;
				try{
					jsonObj = new JSONObject(rcvdRessage.getPayload());
				}catch(JSONException e){
					throw new RuntimeException("Error parsing message JSON", e);
				}
				if(jsonObj.has(Constants.WS_JSON_USERNAME) && jsonObj.get(Constants.WS_JSON_USERNAME) instanceof String && StringUtils.isNotBlank((String) jsonObj.get(Constants.WS_JSON_USERNAME))){
					//TODO Check username against DB
					String username = (String) jsonObj.get(Constants.WS_JSON_USERNAME);
					if(userSessions.containsKey(username)){
						userSessions.get(username).add(session);
					}else{
						Set<WebSocketSession> userSessionsSet = new HashSet<WebSocketSession>();
						userSessionsSet.add(session);
						userSessions.put(username, userSessionsSet);
						registerUserWithController(username);
					}
					authenticated = true;
					//Set username associated with the sessionId so that we know that the session is authenticated
					sessionAuthMap.put(session.getId(), username);
				}


				//Send a response message so that the application knows that they're authenticated
				TextMessage wsMsg = null;
				if(authenticated){
					wsMsg = new TextMessage("{\"" + Constants.WS_JSON_AUTHENTICATED + "\": true}");
				}else{
					wsMsg = new TextMessage("{\"" + Constants.WS_JSON_AUTHENTICATED + "\": false}");
				}
				session.sendMessage(wsMsg);
			}else{
				//The session is valid and authenticated. JSONify the message and get the to value and the message
				JSONObject jsonObject = null;
				String to = null;
				try{
					jsonObject = new JSONObject(rcvdRessage.getPayload());
				}catch(JSONException e){
					throw new RuntimeException("Error parsing JSON from message", e);
				}
				if(jsonObject.has(Constants.WS_JSON_TO) && jsonObject.get(Constants.WS_JSON_TO) instanceof String){
					to = (String) jsonObject.get(Constants.WS_JSON_TO);
				}else{
					throw new RuntimeException("The to value could not be found on the inbound websocket JSON object");
				}
				String message = null;
				if(jsonObject.has(Constants.WS_JSON_MESSAGE) && jsonObject.get(Constants.WS_JSON_MESSAGE) instanceof String){
					message = (String) jsonObject.get(Constants.WS_JSON_MESSAGE);
				}else{
					throw new RuntimeException("The message could not be found on the inbound websocket JSON object");
				}
				String from = sessionAuthMap.get(session.getId());
				System.out.println("The message " + message + " | from: " + from + " | to: " + to + "");

				sendMessageToTCPServer(to, from, message);

				//TODO Log the message in the database
			}
		} catch (Exception e) {
			System.err.println("Error in WebSocketHandler#handleTextMessage");
			e.printStackTrace();
		}

	}

	/*
	@Override
	public void afterConnectionEstablished(WebSocketSession session){
		//Register a new session and mark it as unauthenticated
		sessionAuthMap.put(session.getId(), null);
	}
	 */

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status){
		//Instantiate our hashmaps if necessary
		if(sessionAuthMap == null){
			sessionAuthMap = new ConcurrentHashMap<String, String>();
		}
		if(userSessions == null){
			userSessions = new ConcurrentHashMap<String, Set<WebSocketSession>>();
		}

		//Get the username associated with the session
		String username = sessionAuthMap.get(session.getId());
		if(StringUtils.isBlank(username)){
			return;
		}
		removeSession(username, session.getId());
	}

	private void removeSession(String username, String sessionId){
		//Remove the session from the sessionAuthMap
		sessionAuthMap.remove(sessionId);
		//Remove the sessionId from the user's entry in the userSessions map if the user has an entry
		if(userSessions.containsKey(username)){
			userSessions.get(username).remove(sessionId);
		}

		if((userSessions.containsKey(username) && userSessions.get(username).isEmpty()) || !userSessions.containsKey(username)){
			//TODO Inform the controller that no websockets exist for the user on this machine anymore
			//Remove the user's entry from the userSessions map
			userSessions.remove(username);
		}
	}

	private void initSocketConnection(){
		try {
			socket = new Socket("localhost", 6789);
			//Start listening on the socket
			(new Thread(new SocketListenerRunnable(socket))).start();
		} catch (Exception e) {
			socket = null;
			throw new RuntimeException("There was an error connecting to the controller", e);
		} 
	}

	private void sendMessageToTCPServer(String to, String from, String message){
		//Send the message to the TCP server
		try{
			DataOutputStream output = new DataOutputStream(socket.getOutputStream());
			output.writeBytes("{\"" + Constants.WS_JSON_TO + "\": \"" + to + "\", \"" + Constants.WS_JSON_FROM + "\": \"" + from + "\",\"" + Constants.WS_JSON_MESSAGE + "\": \"" + message + "\"}\n");
		}catch(Exception e){
			IOUtils.closeQuietly(socket);
			socket = null;
			throw new RuntimeException("Error while trying to communicate with the TCP server", e);
		}
	}

	private void registerUserWithController(String username){
		try{
			DataOutputStream output = new DataOutputStream(socket.getOutputStream());
			output.writeBytes("{\"" + Constants.WS_JSON_USERNAME + "\": \"" + username + "\"}\n");
		}catch(Exception e){
			IOUtils.closeQuietly(socket);
			socket = null;
			throw new RuntimeException("Error while trying to communicate with the TCP server", e);
		}
	}

	private class SocketListenerRunnable implements Runnable{
		Socket socket = null;

		SocketListenerRunnable(Socket socket){
			this.socket = socket;
		}

		@Override
		public void run() {
			while(true){
				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					String messageFromCaesar = reader.readLine();
					System.out.println("Received the following message from Caesar: " + messageFromCaesar);
					if(StringUtils.isBlank(messageFromCaesar)){
						continue;
					}
					
					JSONObject jsonObject = new JSONObject(messageFromCaesar);
					if(jsonObject.has(Constants.WS_JSON_TO) && jsonObject.has(Constants.WS_JSON_FROM)){
						//Send the message from Caesar to all websockets associated with the recipient AND the sender to maintain websocket message sync across servers, websockets, and devices
						String toUsername = jsonObject.getString(Constants.WS_JSON_TO);
						distributeMessageFromCaesar(toUsername, messageFromCaesar);
						String fromUsername = jsonObject.getString(Constants.WS_JSON_FROM);
						distributeMessageFromCaesar(fromUsername, messageFromCaesar);
					}
				} catch (Exception e) {
					System.err.println("Error reading from socket connection to Caesar");
					e.printStackTrace();
				}
			}
		}
		
		private void distributeMessageFromCaesar(String username, String messageFromCaesar) throws IOException{
			//Check to make sure that the user has associated websockets
			if(userSessions.get(username)==null || userSessions.get(username).isEmpty()){
				System.out.println("No websockets associated with the user " + username + ". Skipping the distribution of Caesar's message to this user");
				return;
			}
			
			//Get all of the websockets associated with the username and send them the message from Caesar
			System.out.println("Distributing Caesar's message to all " + userSessions.get(username).size() + " websockets associated with the recipient user " + username);
			TextMessage wsMsg = new TextMessage(messageFromCaesar);
			for(WebSocketSession session : userSessions.get(username)){
				if(session.isOpen()){
					session.sendMessage(wsMsg);
				}else{
					//We need to remove this closed session from the userSessions map
					removeSession(username, session.getId());
				}
			}
		}

	}
}
