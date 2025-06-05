/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5016500
 * @library /test/lib/
 * @summary Test SslRmi[Client|Server]SocketFactory SSL socket parameters.
 * @run main/othervm SSLSocketParametersTest 1
 * @run main/othervm SSLSocketParametersTest 2
 * @run main/othervm SSLSocketParametersTest 3
 * @run main/othervm SSLSocketParametersTest 4
 * @run main/othervm SSLSocketParametersTest 5
 * @run main/othervm SSLSocketParametersTest 6
 * @run main/othervm SSLSocketParametersTest 7
 */
import jdk.test.lib.Asserts;

import java.io.IOException;
import java.io.File;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.rmi.ConnectIOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import javax.net.ssl.SSLContext;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

public class SSLSocketParametersTest implements Serializable {

    public interface Hello extends Remote {
        String sayHello() throws RemoteException;
    }

    public class HelloImpl implements Hello {
        public String sayHello() {
            return "Hello World!";
        }
    }

    public void testRmiCommunication(RMIServerSocketFactory serverSocketFactory) throws Exception {
        HelloImpl server = new HelloImpl();
        Hello stub = (Hello)UnicastRemoteObject.exportObject(server,
                0, new SslRMIClientSocketFactory(), serverSocketFactory);
        try {
            String msg = stub.sayHello();
            Asserts.assertEquals("Hello World!", msg);
        } finally {
            Reference.reachabilityFence(server);
        }
    }

    private static void testSslServerSocketFactory(String[] cipherSuites, String[] protocol) throws Exception {
        new SslRMIServerSocketFactory(SSLContext.getDefault(),
                    cipherSuites, protocol, false);
    }

    public void runTest(int testNumber) throws Exception {
        System.out.println("Running test " + testNumber);

        switch (testNumber) {
            /* default constructor - default config */
            case 1 ->
                testRmiCommunication(new SslRMIServerSocketFactory());

            /* non-default constructor - default config */
            case 2 ->
                testRmiCommunication(new SslRMIServerSocketFactory(null, null, false));

            /* needClientAuth=true */
            case 3 ->
                testRmiCommunication(new SslRMIServerSocketFactory(null, null, null, true));

            /* server side dummy_ciphersuite */
            case 4 -> {
                Exception exc = Asserts.assertThrows(IllegalArgumentException.class,
                        () -> testSslServerSocketFactory(new String[]{"dummy_ciphersuite"}, null));
                if (!exc.getMessage().toLowerCase().contains("unsupported ciphersuite")) {
                    throw exc;
                }
            }

            /* server side dummy_protocol */
            case 5 -> {
                Exception thrown = Asserts.assertThrows(IllegalArgumentException.class,
                        () -> testSslServerSocketFactory(null, new String[]{"dummy_protocol"}));
                if (!thrown.getMessage().toLowerCase().contains("unsupported protocol")) {
                    throw thrown;
                }
            }

            /* client side dummy_ciphersuite */
            case 6 -> {
                System.setProperty("javax.rmi.ssl.client.enabledCipherSuites",
                        "dummy_ciphersuite");
                Asserts.assertThrows(ConnectIOException.class,
                        () -> testRmiCommunication(new SslRMIServerSocketFactory()));
            }

            /* client side dummy_protocol */
            case 7 -> {
                System.setProperty("javax.rmi.ssl.client.enabledProtocols",
                        "dummy_protocol");
                Asserts.assertThrows(ConnectIOException.class,
                        () -> testRmiCommunication(new SslRMIServerSocketFactory()));
            }

            default ->
                    throw new RuntimeException("Unknown test number: " + testNumber);
        }
    }

    public static void main(String[] args) throws Exception {
        // Set keystore properties (server-side)
        //
        final String keystore = System.getProperty("test.src") +
                File.separator + "keystore";
        System.out.println("KeyStore = " + keystore);
        System.setProperty("javax.net.ssl.keyStore", keystore);
        System.setProperty("javax.net.ssl.keyStorePassword", "password");

        // Set truststore properties (client-side)
        //
        final String truststore = System.getProperty("test.src") +
                File.separator + "truststore";
        System.out.println("TrustStore = " + truststore);
        System.setProperty("javax.net.ssl.trustStore", truststore);
        System.setProperty("javax.net.ssl.trustStorePassword", "trustword");

        SSLSocketParametersTest test = new SSLSocketParametersTest();
        test.runTest(Integer.parseInt(args[0]));
    }
}