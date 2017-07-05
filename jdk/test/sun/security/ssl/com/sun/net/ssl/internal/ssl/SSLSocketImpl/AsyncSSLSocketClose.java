/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6447412
 * @summary Issue with socket.close() for ssl sockets when poweroff on
 *          other system
 */

import javax.net.ssl.*;
import java.io.*;

public class AsyncSSLSocketClose implements Runnable
{
    SSLSocket socket;
    SSLServerSocket ss;

    // Where do we find the keystores?
    static String pathToStores = "../../../../../../../etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";

    public static void main(String[] args) {
        String keyFilename =
            System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + keyStoreFile;
        String trustFilename =
            System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + trustStoreFile;

        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", passwd);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);

        new AsyncSSLSocketClose();
    }

    public AsyncSSLSocketClose() {
        try {
            SSLServerSocketFactory sslssf =
                (SSLServerSocketFactory)SSLServerSocketFactory.getDefault();
            ss = (SSLServerSocket) sslssf.createServerSocket(0);

            SSLSocketFactory sslsf =
                (SSLSocketFactory)SSLSocketFactory.getDefault();
            socket = (SSLSocket)sslsf.createSocket("localhost",
                                                        ss.getLocalPort());
            SSLSocket serverSoc = (SSLSocket) ss.accept();
            ss.close();

            (new Thread(this)).start();
            serverSoc.startHandshake();

            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            socket.setSoLinger(true, 10);
            System.out.println("Calling Socket.close");
            socket.close();
            System.out.println("ssl socket get closed");
            System.out.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // block in write
    public void run() {
        try {
            byte[] ba = new byte[1024];
            for (int i=0; i<ba.length; i++)
                ba[i] = 0x7A;

            OutputStream os = socket.getOutputStream();
            int count = 0;
            while (true) {
                count += ba.length;
                System.out.println(count + " bytes to be written");
                os.write(ba);
                System.out.println(count + " bytes written");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
