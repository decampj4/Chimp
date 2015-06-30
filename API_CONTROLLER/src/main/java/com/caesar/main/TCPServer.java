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
	Map<String, Set<SocketContainer>> socketMapKeyedByUser = new ConcurrentHashMap<String, Set<SocketContainer>>();
	Map<Socket, String> socketMapKeyedBySocket = new ConcurrentHashMap<Socket, String>();

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
			IOUtils.closeQuietly(tcpServer);
		}
	}

	private class SocketListenerRunnable implements Runnable{
		SocketContainer socketContainer = null;

		SocketListenerRunnable(Socket socket){
			this.socketContainer = new SocketContainer(socket);
		}

		@Override
		public void run() {
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(socketContainer.getSocket().getInputStream()));
				System.out.println("Socket reader established for socket on port " + socketContainer.getSocket().getPort());
				boolean run = true;
				while(run){
					if(socketContainer.getSocket().isClosed()){
						System.out.println("The socket on port " + socketContainer.getSocket().getPort() + " has been closed");
						removeUserFromSocketMap(socketMapKeyedBySocket.get(socketContainer.getSocket()));
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
						if(socketMapKeyedByUser.containsKey(username) && socketMapKeyedByUser.get(username)!=null){
							//Add the socket to the pre-existing socket set
							socketMapKeyedByUser.get(username).add(socketContainer);
						}else{
							//Add a new Socket set associated with the user
							Set<SocketContainer> socketSet = new HashSet<SocketContainer>();
							socketSet.add(socketContainer);
							socketMapKeyedByUser.put(username, socketSet);
						}
						//Add the socket and username combination to the socketMapKeyedBySocket map
						socketMapKeyedBySocket.put(socketContainer.getSocket(), username);
					}else if(jsonObject.has(Constants.WS_JSON_REMOVE_USERNAME)){
						removeUserFromSocketMap(jsonObject.getString(Constants.WS_JSON_REMOVE_USERNAME));
					}else if(jsonObject.has(Constants.WS_JSON_TO) && jsonObject.has(Constants.WS_JSON_FROM)){
						//Send the message to the chimps that have the to and from users in order to sync multiple connections (computer, smart phone, etc.)
						//Let the chimps distribute to the to and from user websockets appropriately
						String toUsername = jsonObject.getString(Constants.WS_JSON_TO);
						String fromUsername = jsonObject.getString(Constants.WS_JSON_FROM);
						System.out.println("Sending a message with the recipient " + toUsername + " and the sender " + fromUsername);
						distributeMessage(jsonObject);
					}
				} 
			} catch (Exception e) {
				throw new RuntimeException("Error reading from socket connection", e);
			}
		}

		private void distributeMessage(JSONObject jsonObject) throws IOException{
			//Get all of the SocketContainer objects associated with the to username and the from username and send them the message. Gather them in a set to avoid repeats
			Set<SocketContainer> containersToSendToSet = new HashSet<SocketContainer>();
			String toUsername = jsonObject.getString(Constants.WS_JSON_TO);
			String fromUsername = jsonObject.getString(Constants.WS_JSON_FROM);
			//Add the SocketContainers associated with the to user
			if(socketMapKeyedByUser.get(toUsername)!=null){
				containersToSendToSet.addAll(socketMapKeyedByUser.get(toUsername));
			}
			//Add the SocketContainers associated with the from user
			if(socketMapKeyedByUser.get(fromUsername)!=null){
				containersToSendToSet.addAll(socketMapKeyedByUser.get(fromUsername));
			}
			//Build the JSON string because we've had problems with the JSONObject#toString method
			String messageToSendToSockets = "{\"" + Constants.WS_JSON_TO + "\": \"" + jsonObject.getString(Constants.WS_JSON_TO) + "\", "
					+ "\"" + Constants.WS_JSON_FROM + "\": \"" + jsonObject.getString(Constants.WS_JSON_FROM) + "\", "
					+ "\"" + Constants.WS_JSON_MESSAGE + "\": \"" + jsonObject.getString(Constants.WS_JSON_MESSAGE) + "\"}\n";
			System.out.println("Message being sent to " + containersToSendToSet.size() + " sockets associated with the recipient " + toUsername + " and sender " + fromUsername + ": " + messageToSendToSockets);
			for(SocketContainer distSockContainer : containersToSendToSet){
				//This is where the magic happens. Basically, we're going to use the socketAvailable field to avoid writing to the same socket at the 
				//same time in concurrent threads. We could use a synchronized method here, but at scale, that method would become a choke point.
				int tries = 0;
				do{
					if(distSockContainer.getSocketAvailable()){
						//Lock the socket down while we're performing our write
						distSockContainer.setSocketAvailable(false);
						DataOutputStream disSockOutputStream = new DataOutputStream(distSockContainer.getSocket().getOutputStream());
						disSockOutputStream.writeBytes(messageToSendToSockets);
						//Make the socket available again now that we're done
						distSockContainer.setSocketAvailable(true);
						break;
					}else{
						tries ++;
					}
				}while(tries < 1000);//Attempt to write to the socket 1000 times
				if(tries >= 1000){
					//Couldn't write to the socket. Log the error
					System.err.println("Couldn't write to the socket on port " + distSockContainer.getSocket().getPort() + ". The number of attempts exceeded the maximum allowable number");
				}
			}
		}

		private void removeUserFromSocketMap(String username){
			//Remove the socket from the socketMap
			if(StringUtils.isNotBlank(username) && socketMapKeyedByUser.get(username)!=null){
				socketMapKeyedByUser.get(username).remove(this.socketContainer);
			}
		}
	}

	//Class used to store sockets and determine whether or not they can be written to
	private class SocketContainer{
		private Socket socket = null;
		private boolean socketAvailable = false;

		public SocketContainer(Socket socket){
			this.socket = socket;
			this.socketAvailable = true;
		}

		public SocketContainer(Socket socket, boolean socketAvailable){
			this.socket = socket;
			this.socketAvailable = socketAvailable;
		}

		public void setSocketAvailable(boolean socketAvailable){
			this.socketAvailable = socketAvailable;
		}

		public boolean getSocketAvailable(){
			return this.socketAvailable;
		}

		public void setSocket(Socket socket){
			this.socket = socket;
		}

		public Socket getSocket(){
			return this.socket;
		}
	}

}



