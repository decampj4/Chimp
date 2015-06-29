package com.caesar.main;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import com.caesar.constants.Constants;

public class TCPServer {
	Map<String, Set<Socket>> socketMap = new ConcurrentHashMap<String, Set<Socket>>();

	public static void main(String[] args){
		new TCPServer();
	}

	public TCPServer(){
		ServerSocket tcpServer = null;
		try{
			tcpServer = new ServerSocket(6789);
			System.out.println("TCP server listening on port 6789");

			while(true){
				Socket socket = tcpServer.accept();
				//Connection received
				System.out.println("Connection received from port " + socket.getPort());
				(new Thread(new SocketListenerRunnable(socket))).start();
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(tcpServer != null){
				try {
					tcpServer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private class SocketListenerRunnable implements Runnable{
		Socket socket = null;
		String username = null;
		
		SocketListenerRunnable(Socket socket){
			this.socket = socket;
		}

		@Override
		public void run() {
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				System.out.println("Socket reader established for socket on port " + socket.getPort());
				boolean run = true;
				while(run){
					if(socket.isClosed()){
						System.out.println("The socket on port " + socket.getPort() + " has been closed");
						removeUserFromSocketMap();
						run = false;
						continue;
					}
					String messageFromSocket = reader.readLine();
					System.out.println("Received the message: " + messageFromSocket);
					JSONObject jsonObject = new JSONObject(messageFromSocket);
					if(jsonObject.has(Constants.WS_JSON_USERNAME)){
						//Associate this socket with the user
						String username = jsonObject.getString(Constants.WS_JSON_USERNAME);
						System.out.println("Adding Socket associated with " + username); 
						this.username = username;
						if(socketMap.containsKey(username) && socketMap.get(username)!=null){
							//Add the socket to the pre-existing socket set
							socketMap.get(username).add(socket);
						}else{
							//Add a new Socket set associated with the user
							Set<Socket> socketSet = new HashSet<Socket>();
							socketSet.add(socket);
							socketMap.put(username, socketSet);
						}
					}else if(jsonObject.has(Constants.WS_JSON_REMOVE_USERNAME)){
						removeUserFromSocketMap();
					}else if(jsonObject.has(Constants.WS_JSON_TO) && jsonObject.has(Constants.WS_JSON_FROM)){
						//Send the message to the to and from users in order to sync multiple connections (computer, smart phone, etc.)
						String toUsername = jsonObject.getString(Constants.WS_JSON_TO);
						System.out.println("Sending a message with the recipient " + toUsername);
						distributeMessage(toUsername, jsonObject);
						String fromUsername = jsonObject.getString(Constants.WS_JSON_FROM);
						System.out.println("Sending a message from the sender " + fromUsername);
						distributeMessage(fromUsername, jsonObject);
					}
				} 
			} catch (Exception e) {
				throw new RuntimeException("Error reading from socket connection", e);
			}
		}
		
		private void distributeMessage(String username, JSONObject jsonObject) throws IOException{
			//Get all of the sockets associated with the to username and send them the message
			if(socketMap.get(username)==null ||socketMap.get(username).isEmpty()){
				System.out.println("The user " + username + " does not appear to have any connections");
			}else{
				//Build the JSON string because we've had problems with the JSONObject#toString method
				String messageToSendToSockets = "{\"" + Constants.WS_JSON_TO + "\": \"" + jsonObject.getString(Constants.WS_JSON_TO) + "\", "
						+ "\"" + Constants.WS_JSON_FROM + "\": \"" + jsonObject.getString(Constants.WS_JSON_FROM) + "\", "
						+ "\"" + Constants.WS_JSON_MESSAGE + "\": \"" + jsonObject.getString(Constants.WS_JSON_MESSAGE) + "\"}\n";
				System.out.println("Message being sent to all " + socketMap.get(username).size() + " sockets associated with the username " + username +": " + messageToSendToSockets);
				for(Socket distSock : socketMap.get(username)){
					DataOutputStream disSockOutputStream = new DataOutputStream(distSock.getOutputStream());
					disSockOutputStream.writeBytes(messageToSendToSockets);
				}
			}
		}
		
		private void removeUserFromSocketMap(){
			//Remove the socket from the socketMap
			if(StringUtils.isNotBlank(this.username) && socketMap.get(this.username)!=null){
				socketMap.get(this.username).remove(this.socket);
			}
		}

	}
}
