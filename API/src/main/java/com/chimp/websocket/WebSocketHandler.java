package com.chimp.websocket;

import java.io.DataOutputStream;
import java.net.Socket;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class WebSocketHandler extends TextWebSocketHandler{
	@Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
			Thread.sleep(3000);
			TextMessage msg = new TextMessage("Hello, " + message.getPayload() + "!");
	        session.sendMessage(msg);
	        
	        //Send the message to the TCP server
	        Socket socket = null;
	        try{
	        	socket = new Socket("localhost", 6789);
	        	DataOutputStream output = new DataOutputStream(socket.getOutputStream());
	        	output.writeBytes(message.getPayload());
	        }catch(Exception e){
	        	throw new RuntimeException("Error while trying to communicate with the TCP server", e);
	        }finally{
	        	IOUtils.closeQuietly(socket);
	        }
		} catch (Exception e) {
			e.printStackTrace();
		} // simulated delay
        
    }
}
