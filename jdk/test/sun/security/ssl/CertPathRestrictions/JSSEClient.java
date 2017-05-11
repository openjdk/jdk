/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/*
 * A SSL socket client.
 */
public class JSSEClient {

    public static void main(String[] args) throws Exception {
        System.out.println("Client: arguments=" + String.join("; ", args));

        int port = Integer.valueOf(args[0]);
        String[] trustNames = args[1].split(TLSRestrictions.DELIMITER);
        String[] certNames = args[2].split(TLSRestrictions.DELIMITER);
        String constraint = args[3];

        TLSRestrictions.setConstraint("Client", constraint);

        SSLContext context = TLSRestrictions.createSSLContext(
                trustNames, certNames);
        SSLSocketFactory socketFactory = context.getSocketFactory();
        try (SSLSocket socket = (SSLSocket) socketFactory.createSocket()) {
            socket.connect(new InetSocketAddress("localhost", port),
                    TLSRestrictions.TIMEOUT);
            socket.setSoTimeout(TLSRestrictions.TIMEOUT);
            System.out.println("Client: connected");

            InputStream sslIS = socket.getInputStream();
            OutputStream sslOS = socket.getOutputStream();
            sslOS.write('C');
            sslOS.flush();
            sslIS.read();
            System.out.println("Client: finished");
        } catch (Exception e) {
            throw new RuntimeException("Client: failed.", e);
        }
    }
}
