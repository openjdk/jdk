/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6894643 6913636
 * @summary Test JSSE Kerberos ciphersuite
 * @run main/othervm SSL TLS_KRB5_WITH_RC4_128_SHA
 * @run main/othervm SSL TLS_KRB5_WITH_RC4_128_MD5
 * @run main/othervm SSL TLS_KRB5_WITH_3DES_EDE_CBC_SHA
 * @run main/othervm SSL TLS_KRB5_WITH_3DES_EDE_CBC_MD5
 * @run main/othervm SSL TLS_KRB5_WITH_DES_CBC_SHA
 * @run main/othervm SSL TLS_KRB5_WITH_DES_CBC_MD5
 * @run main/othervm SSL TLS_KRB5_EXPORT_WITH_RC4_40_SHA
 * @run main/othervm SSL TLS_KRB5_EXPORT_WITH_RC4_40_MD5
 * @run main/othervm SSL TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA
 * @run main/othervm SSL TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5
 */
import java.io.*;
import java.net.InetAddress;
import javax.net.ssl.*;
import java.security.Principal;
import java.util.Date;
import sun.security.jgss.GSSUtil;
import sun.security.krb5.PrincipalName;
import sun.security.krb5.internal.ktab.KeyTab;

public class SSL {

    private static String krb5Cipher;
    private static final int LOOP_LIMIT = 3;
    private static int loopCount = 0;
    private static volatile String server;
    private static volatile int port;

    public static void main(String[] args) throws Exception {

        krb5Cipher = args[0];

        KDC kdc = KDC.create(OneKDC.REALM);
        // Run this after KDC, so our own DNS service can be started
        try {
            server = InetAddress.getLocalHost().getHostName().toLowerCase();
        } catch (java.net.UnknownHostException e) {
            server = "localhost";
        }

        kdc.addPrincipal(OneKDC.USER, OneKDC.PASS);
        kdc.addPrincipalRandKey("krbtgt/" + OneKDC.REALM);
        KDC.saveConfig(OneKDC.KRB5_CONF, kdc);
        System.setProperty("java.security.krb5.conf", OneKDC.KRB5_CONF);

        // Add 3 versions of keys into keytab
        KeyTab ktab = KeyTab.create(OneKDC.KTAB);
        PrincipalName service = new PrincipalName(
                "host/" + server, PrincipalName.KRB_NT_SRV_HST);
        ktab.addEntry(service, "pass1".toCharArray(), 1, true);
        ktab.addEntry(service, "pass2".toCharArray(), 2, true);
        ktab.addEntry(service, "pass3".toCharArray(), 3, true);
        ktab.save();

        // and use the middle one as the real key
        kdc.addPrincipal("host/" + server, "pass2".toCharArray());

        // JAAS config entry name ssl
        System.setProperty("java.security.auth.login.config", OneKDC.JAAS_CONF);
        File f = new File(OneKDC.JAAS_CONF);
        FileOutputStream fos = new FileOutputStream(f);
        fos.write((
                "ssl {\n" +
                "    com.sun.security.auth.module.Krb5LoginModule required\n" +
                "    principal=\"host/" + server + "\"\n" +
                "    useKeyTab=true\n" +
                "    keyTab=" + OneKDC.KTAB + "\n" +
                "    isInitiator=false\n" +
                "    storeKey=true;\n};\n"
                ).getBytes());
        fos.close();
        f.deleteOnExit();

        Context c;
        final Context s = Context.fromJAAS("ssl");

        // There's no keytab file when server starts.
        s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);

        Thread server = new Thread(new Runnable() {
            public void run() {
                try {
                    s.doAs(new JsseServerAction(), null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        server.setDaemon(true);
        server.start();

        // Warm the server
        Thread.sleep(2000);

        // Now create the keytab

        /*
        // Add 3 versions of keys into keytab
        KeyTab ktab = KeyTab.create(OneKDC.KTAB);
        PrincipalName service = new PrincipalName(
                "host/" + server, PrincipalName.KRB_NT_SRV_HST);
        ktab.addEntry(service, "pass1".toCharArray(), 1);
        ktab.addEntry(service, "pass2".toCharArray(), 2);
        ktab.addEntry(service, "pass3".toCharArray(), 3);
        ktab.save();

        // and use the middle one as the  real key
        kdc.addPrincipal("host/" + server, "pass2".toCharArray());
         */
        c = Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        c.startAsClient("host/" + server, GSSUtil.GSS_KRB5_MECH_OID);
        c.doAs(new JsseClientAction(), null);

        // Add another version of key, make sure it can be loaded
        Thread.sleep(2000);
        ktab = KeyTab.getInstance(OneKDC.KTAB);
        ktab.addEntry(service, "pass4".toCharArray(), 4, true);
        ktab.save();
        kdc.addPrincipal("host/" + server, "pass4".toCharArray());

        c = Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        c.startAsClient("host/" + server, GSSUtil.GSS_KRB5_MECH_OID);
        c.doAs(new JsseClientAction(), null);

        // Revoke the old key
        /*Thread.sleep(2000);
        ktab = KeyTab.create(OneKDC.KTAB);
        ktab.addEntry(service, "pass5".toCharArray(), 5, false);
        ktab.save();

        c = Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        c.startAsClient("host/" + server, GSSUtil.GSS_KRB5_MECH_OID);
        try {
            c.doAs(new JsseClientAction(), null);
            throw new Exception("Should fail this time.");
        } catch (SSLException e) {
            // Correct behavior.
        }*/
    }

    // Following codes copied from
    // http://java.sun.com/javase/6/docs/technotes/guides/security/jgss/lab/part2.html#JSSE
    private static class JsseClientAction implements Action {
        public byte[] run(Context s, byte[] input) throws Exception {
            SSLSocketFactory sslsf =
                (SSLSocketFactory) SSLSocketFactory.getDefault();
            System.out.println("Connecting " + server + ":" + port);
            SSLSocket sslSocket = (SSLSocket) sslsf.createSocket(server, port);

            // Enable only a KRB5 cipher suite.
            String enabledSuites[] = {krb5Cipher};
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
            // This line should not be needed. It's the server's duty to
            // forget the old key
            //sslSocket.getSession().invalidate();
            return null;
        }
    }

    private static class JsseServerAction implements Action {
        public byte[] run(Context s, byte[] input) throws Exception {
            SSLServerSocketFactory sslssf =
                (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket sslServerSocket =
                (SSLServerSocket) sslssf.createServerSocket(0); // any port
            port = sslServerSocket.getLocalPort();
            System.out.println("Listening on " + port);

            // Enable only a KRB5 cipher suite.
            String enabledSuites[] = {krb5Cipher};
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
