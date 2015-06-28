package com.chimp.junit;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.chimp.websocket.WebSocketHandler;

public class WebSocketTests {
	 private static WebSocketSession wsSession = null;
	 
	@BeforeClass
	public static void testSetup(){
		List<Transport> transports = new ArrayList<>(2);
		transports.add(new WebSocketTransport(new StandardWebSocketClient()));
		transports.add(new RestTemplateXhrTransport());

		SockJsClient sockJsClient = new SockJsClient(transports);
		ListenableFuture<WebSocketSession> listenableFuture = sockJsClient.doHandshake(new WebSocketHandler(), "http://localhost:8080/chimp/websocket");
		try{
			wsSession = listenableFuture.get();
			assertTrue(wsSession.isOpen());
		} catch (Exception e) {
			throw new RuntimeException("Error starting the websocket connection", e);
		}
	}
	
	@AfterClass
	public static void testTearDown(){
		
	}
	
	@Test
	public void loginTest(){
		TextMessage msg = new TextMessage("{\"username\":\"asdf\"}");
		try {
			wsSession.sendMessage(msg);
		} catch (Exception e) {
			throw new RuntimeException("Error in WebSocketTests#loginTest", e);
		}
	}
}
