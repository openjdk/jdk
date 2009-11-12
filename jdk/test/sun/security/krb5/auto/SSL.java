/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6894643
 * @summary Test JSSE Kerberos ciphersuite
 */
import java.io.*;
import java.net.InetAddress;
import javax.net.ssl.*;
import java.security.Principal;
import java.util.Date;
import sun.security.jgss.GSSUtil;

public class SSL {

    private static final String KRB5_CIPHER = "TLS_KRB5_WITH_3DES_EDE_CBC_SHA";
    private static final int PORT = 4569;
    private static final int LOOP_LIMIT = 1;
    private static final char[] PASS = "secret".toCharArray();
    private static int loopCount = 0;

    private static String SERVER;

    public static void main(String[] args) throws Exception {

        KDC kdc = KDC.create(OneKDC.REALM);
        // Run this after KDC, so our own DNS service can be started
        try {
            SERVER = InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            SERVER = "localhost";
        }

        kdc.addPrincipal(OneKDC.USER, OneKDC.PASS);
        kdc.addPrincipalRandKey("krbtgt/" + OneKDC.REALM);
        kdc.addPrincipal("host/" + SERVER, PASS);
        KDC.saveConfig(OneKDC.KRB5_CONF, kdc);
        System.setProperty("java.security.krb5.conf", OneKDC.KRB5_CONF);

        final Context c = Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        final Context s = Context.fromUserPass("host/" + SERVER, PASS, true);

        c.startAsClient("host/" + SERVER, GSSUtil.GSS_KRB5_MECH_OID);
        s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);

        new Thread(new Runnable() {
            public void run() {
                try {
                    s.doAs(new JsseServerAction(), null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Warm the server
        Thread.sleep(2000);

        c.doAs(new JsseClientAction(), null);
    }

    // Following codes copied from
    // http://java.sun.com/javase/6/docs/technotes/guides/security/jgss/lab/part2.html#JSSE
    private static class JsseClientAction implements Action {
        public byte[] run(Context s, byte[] input) throws Exception {
            SSLSocketFactory sslsf =
                (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslsf.createSocket(SERVER, PORT);

            // Enable only a KRB5 cipher suite.
            String enabledSuites[] = {KRB5_CIPHER};
            sslSocket.setEnabledCipherSuites(enabledSuites);
            // Should check for exception if enabledSuites is not supported

            BufferedReader in = new BufferedReader(new InputStreamReader(
                sslSocket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                sslSocket.getOutputStream()));

            String outStr = "Hello There!\n";
            out.write(outStr);
            out.flush();
            System.out.print("Sending " + outStr);

            String inStr = in.readLine();
            System.out.println("Received " + inStr);

            String cipherSuiteChosen = sslSocket.getSession().getCipherSuite();
            System.out.println("Cipher suite in use: " + cipherSuiteChosen);
            Principal self = sslSocket.getSession().getLocalPrincipal();
            System.out.println("I am: " + self.toString());
            Principal peer = sslSocket.getSession().getPeerPrincipal();
            System.out.println("Server is: " + peer.toString());

            sslSocket.close();
            return null;
        }
    }

    private static class JsseServerAction implements Action {
        public byte[] run(Context s, byte[] input) throws Exception {
            SSLServerSocketFactory sslssf =
                (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket sslServerSocket =
                (SSLServerSocket) sslssf.createServerSocket(PORT);

            // Enable only a KRB5 cipher suite.
            String enabledSuites[] = {KRB5_CIPHER};
            sslServerSocket.setEnabledCipherSuites(enabledSuites);
            // Should check for exception if enabledSuites is not supported

            while (loopCount++ < LOOP_LIMIT) {
                System.out.println("Waiting for incoming connection...");

                SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();

                System.out.println("Got connection from client "
                    + sslSocket.getInetAddress());

                BufferedReader in = new BufferedReader(new InputStreamReader(
                    sslSocket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                    sslSocket.getOutputStream()));

                String inStr = in.readLine();
                System.out.println("Received " + inStr);

                String outStr = inStr + " " + new Date().toString() + "\n";
                out.write(outStr);
                System.out.println("Sending " + outStr);
                out.flush();

                String cipherSuiteChosen =
                    sslSocket.getSession().getCipherSuite();
                System.out.println("Cipher suite in use: " + cipherSuiteChosen);
                Principal self = sslSocket.getSession().getLocalPrincipal();
                System.out.println("I am: " + self.toString());
                Principal peer = sslSocket.getSession().getPeerPrincipal();
                System.out.println("Client is: " + peer.toString());

                sslSocket.close();
            }
            return null;
        }
    }
}
