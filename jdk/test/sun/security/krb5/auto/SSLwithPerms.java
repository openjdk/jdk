/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8038089
 * @summary TLS optional support for Kerberos cipher suites needs to be re-examined
 * @library ../../../../java/security/testlibrary/
 * @run main/othervm SSLwithPerms
 */
import java.io.*;
import javax.net.ssl.*;
import javax.security.auth.AuthPermission;
import javax.security.auth.kerberos.ServicePermission;
import java.net.SocketPermission;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Principal;
import java.security.Security;
import java.security.SecurityPermission;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.PropertyPermission;

import sun.security.jgss.GSSUtil;

public class SSLwithPerms {

    static String KRB5_CONF = "krb5.conf";
    static String JAAS_CONF = "jaas.conf";
    static String REALM = "REALM";
    static String KTAB = "ktab";
    static String HOST = "host." + REALM.toLowerCase(Locale.US);
    static String SERVER = "host/" + HOST;
    static String USER = "user";
    static char[] PASS = "password".toCharArray();

    public static void main(String[] args) throws Exception {

        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        if (args.length == 0) {
            KDC kdc = KDC.create(REALM, HOST, 0, true);

            kdc.addPrincipal(USER, PASS);
            kdc.addPrincipalRandKey("krbtgt/" + REALM);
            kdc.addPrincipalRandKey(SERVER);
            KDC.saveConfig(KRB5_CONF, kdc);
            kdc.writeKtab(KTAB);

            File f = new File(JAAS_CONF);
            FileOutputStream fos = new FileOutputStream(f);
            fos.write((
                    "ssl {\n" +
                            "    com.sun.security.auth.module.Krb5LoginModule required\n" +
                            "    principal=\"" + SERVER + "\"\n" +
                            "    useKeyTab=true\n" +
                            "    keyTab=" + KTAB + "\n" +
                            "    isInitiator=false\n" +
                            "    storeKey=true;\n};\n"
            ).getBytes());
            fos.close();

            String hostsFileName = System.getProperty("test.src", ".") + "/TestHosts";

            Proc pc = Proc.create("SSLwithPerms")
                    .args("client")
                    .inheritIO()
                    .prop("java.security.manager", "")
                    .prop("java.security.krb5.conf", KRB5_CONF)
                    .prop("jdk.net.hosts.file", hostsFileName)
                    .prop("javax.net.ssl", "handshake")
                    .prop("sun.security.krb5.debug", "true")
                    .perm(new SecurityPermission("setProperty.jdk.tls.disabledAlgorithms"))
                    .perm(new PropertyPermission("sun.security.krb5.principal", "read"))
                    .perm(new FilePermission("port", "read"))
                    .perm(new FilePermission(hostsFileName, "read"))
                    .perm(new FilePermission(KTAB, "read"))
                    .perm(new AuthPermission("modifyPrincipals"))
                    .perm(new AuthPermission("modifyPrivateCredentials"))
                    .perm(new AuthPermission("doAs"))
                    .perm(new SocketPermission("127.0.0.1", "connect"))
                    .perm(new ServicePermission("host/host.realm@REALM", "initiate"))
                    .start();

            Proc ps = Proc.create("SSLwithPerms")
                    .args("server")
                    .inheritIO()
                    .prop("java.security.manager", "")
                    .prop("java.security.krb5.conf", KRB5_CONF)
                    .prop("java.security.auth.login.config", JAAS_CONF)
                    .prop("jdk.net.hosts.file", hostsFileName)
                    .prop("javax.net.ssl", "handshake")
                    .prop("sun.security.krb5.debug", "true")
                    .perm(new SecurityPermission("setProperty.jdk.tls.disabledAlgorithms"))
                    .perm(new AuthPermission("createLoginContext.ssl"))
                    .perm(new AuthPermission("doAs"))
                    .perm(new FilePermission(hostsFileName, "read"))
                    .perm(new FilePermission("port", "write"))
                    .perm(new SocketPermission("127.0.0.1", "accept"))
                    .perm(new ServicePermission("host/host.realm@REALM", "accept"))
                    .start();

            if (pc.waitFor() != 0) {
                throw new Exception();
            }
            if (ps.waitFor() != 0) {
                throw new Exception();
            }
        } else if (args[0].equals("client")) {
            Context c;
            c = Context.fromUserPass(USER, PASS, false);
            c.doAs(new JsseClientAction(), null);
        } else if (args[0].equals("server")) {
            final Context s = Context.fromJAAS("ssl");
            s.doAs(new JsseServerAction(), null);
        }
    }

    private static class JsseClientAction implements Action {
        public byte[] run(Context s, byte[] input) throws Exception {
            SSLSocketFactory sslsf =
                (SSLSocketFactory) SSLSocketFactory.getDefault();
            while (!Files.exists(Paths.get("port"))) {
                Thread.sleep(100);
            }
            int port = ByteBuffer.allocate(4)
                    .put(Files.readAllBytes(Paths.get("port"))).getInt(0);
            System.out.println("Connecting " + SERVER + ":" + port);
            SSLSocket sslSocket = (SSLSocket) sslsf.createSocket(HOST, port);

            // Enable only a KRB5 cipher suite.
            String enabledSuites[] = {"TLS_KRB5_WITH_RC4_128_SHA"};
            sslSocket.setEnabledCipherSuites(enabledSuites);

            SSLParameters params = sslSocket.getSSLParameters();
            params.setServerNames(Collections.singletonList(new SNIHostName(HOST)));
            sslSocket.setSSLParameters(params);

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
                (SSLServerSocket) sslssf.createServerSocket(0); // any port
            int port = sslServerSocket.getLocalPort();
            System.out.println("Listening on " + port);

            String enabledSuites[] = {"TLS_KRB5_WITH_RC4_128_SHA"};
            sslServerSocket.setEnabledCipherSuites(enabledSuites);

            Files.write(Paths.get("port"), ByteBuffer.allocate(4).putInt(port).array());
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
            return null;
        }
    }
}
