package com.chimp.websocket;

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
		} catch (Exception e) {
			e.printStackTrace();
		} // simulated delay
        
    }
}
