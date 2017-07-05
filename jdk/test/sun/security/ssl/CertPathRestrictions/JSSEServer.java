/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import java.io.InputStream;
import java.io.OutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

/*
 * A SSL socket server.
 */
public class JSSEServer {

    private SSLServerSocket server = null;

    private Exception exception = null;

    public JSSEServer(SSLContext context, String constraint,
            boolean needClientAuth) throws Exception {
        TLSRestrictions.setConstraint("Server", constraint);

        SSLServerSocketFactory serverFactory = context.getServerSocketFactory();
        server = (SSLServerSocket) serverFactory.createServerSocket(0);
        server.setSoTimeout(TLSRestrictions.TIMEOUT);
        server.setNeedClientAuth(needClientAuth); // for dual authentication
        System.out.println("Server: port=" + getPort());
    }

    public void start() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    System.out.println("Server: started");
                    try (SSLSocket socket = (SSLSocket) server.accept()) {
                        socket.setSoTimeout(TLSRestrictions.TIMEOUT);
                        InputStream sslIS = socket.getInputStream();
                        OutputStream sslOS = socket.getOutputStream();
                        sslIS.read();
                        sslOS.write('S');
                        sslOS.flush();
                        System.out.println("Server: finished");
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                    exception = e;
                }
            }
        }).start();
    }

    public int getPort() {
        return server.getLocalPort();
    }

    public Exception getException() {
        return exception;
    }
}
