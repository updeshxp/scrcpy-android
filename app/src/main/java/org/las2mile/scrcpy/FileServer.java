package org.las2mile.scrcpy;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private ServerSocket serverSocket = null;
    private Socket connectionSocket = null;
    private InputStream inputStream;
    private String targetip;
    private boolean first_time = true;


    public FileServer(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public void startServingFile(String ip) {
        this.targetip = "/" + ip;
        if (first_time) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        serverSocket = new ServerSocket(10025);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (serverSocket != null) {
                        try {
                            connectionSocket = serverSocket.accept();
                            if (connectionSocket != null) {
                                if (connectionSocket.getInetAddress().toString().equals(targetip)) {
                                    start();
                                }
                            }

                        } catch (IOException d) {
                            d.printStackTrace();
                        }

                    }
                }
            });
            thread.start();
            first_time =false;
        }
    }


    private void start() {

        try {

            InputStream theInputStream = null;
            OutputStream theOutputStream = null;
            try {
                if (connectionSocket.getInetAddress() != null) {
                    theInputStream = connectionSocket.getInputStream();
                    theOutputStream = connectionSocket.getOutputStream();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
                return;
            }

            if (theInputStream != null && theOutputStream != null) {
//  https://stackoverflow.com/questions/8854805/read-complete-http-request-header
                BufferedReader input = new BufferedReader(new InputStreamReader(
                        theInputStream));

                DataOutputStream output = new DataOutputStream(theOutputStream);
                String line;
                try {
                    line = input.readLine();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    return;
                }
                if (line.startsWith("GET")) {

                    try {
                        byte[] buffer = new byte[inputStream.available()];
                        inputStream.read(buffer);
                        output.write(buffer);
                        input.close();
                        output.close();
                        if (connectionSocket != null)
                            connectionSocket.close();
                        serverSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

            }
        } finally {
            try {
                if (connectionSocket != null)
                    connectionSocket.close();
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}