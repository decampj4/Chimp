package com.caesar.main;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {
	public static void main(String[] args){
		try{
			ServerSocket tcpServer = new ServerSocket(6789); 
			while(true){
				Socket socket = tcpServer.accept();
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				DataOutputStream outToClient = new DataOutputStream(socket.getOutputStream());
				System.out.println("Received a message: " + reader.readLine());
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
