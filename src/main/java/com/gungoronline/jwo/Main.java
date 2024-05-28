package com.gungoronline.jwo;


import com.gungoronline.jwo.Util.DateUtil;
import com.gungoronline.jwo.Webserver.ClientThread;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static Map<String, String> config = new HashMap<>();
    private static int httpPort,httpsPort;
    private static boolean isSecure = false;
    private String ipAddress; // Sunucunun IP adresini al
    private static long startupTime;

    public Main(int port) {
        if(isSecure){
            this.httpsPort = port;
        }else{
            this.httpPort = port;
        }
        try {
            this.ipAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        startupTime = DateUtil.getCurrentTimeSeconds();
        System.out.println("Server - Ip Adress: "+ipAddress);
        System.out.println("Server - Port: "+port);
        System.out.println("Server - Start Time: "+ DateUtil.getDateAsString(startupTime));

    }


    public static void main(String[] args) throws Exception {
        loadConfig();
        //new RPGServer(port).run();
        if(!isSecure){
            startHttpServer(httpPort);
        }
    }

    public static void startHttpServer(int port){
        try
        {
            ServerSocket serverSocket = new ServerSocket(httpPort); // create a server socket object
            boolean isStop = false;
            while(!isStop) // while server is not stopped
            {
                Socket clientSocket = serverSocket.accept(); //accept a client
                System.out.println("Client " + clientSocket.getInetAddress().getHostAddress() + " is connected"); // print client ip address
                ClientThread clientThread = new ClientThread(clientSocket); // create a new thread for each client
                clientThread.start(); //start the thread
            }
        }
        catch(Exception e)
        {
            System.out.println(e.toString());
        }
    }

    private static void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader(".jwoconf"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue; // Yorum satırlarını ve boş satırları atla
                }
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    config.put(parts[0].trim(), parts[1].trim());
                }
            }
            httpPort = Integer.parseInt(config.getOrDefault("server.httpPort", "80"));
            httpsPort = Integer.parseInt(config.getOrDefault("server.httpsPort", "8443"));
            isSecure = Boolean.parseBoolean(config.getOrDefault("server.isSecure","false"));
        } catch (IOException e) {
            System.out.println("Failed to load configuration file: " + e.getMessage());
        }
    }
}