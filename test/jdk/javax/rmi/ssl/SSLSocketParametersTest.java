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
 * @summary Test SslRmi[Client|Server]SocketFactory SSL socket parameters.
 * @run main/othervm SSLSocketParametersTest 1
 * @run main/othervm SSLSocketParametersTest 2
 * @run main/othervm SSLSocketParametersTest 3
 * @run main/othervm SSLSocketParametersTest 4
 * @run main/othervm SSLSocketParametersTest 5
 * @run main/othervm SSLSocketParametersTest 6
 * @run main/othervm SSLSocketParametersTest 7
 */
import java.io.IOException;
import java.io.File;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import javax.net.ssl.SSLContext;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

public class SSLSocketParametersTest implements Serializable {

    public interface Hello extends Remote {
        public String sayHello() throws RemoteException;
    }

    public class HelloImpl extends UnicastRemoteObject implements Hello {

        public HelloImpl(int port,
                         RMIClientSocketFactory csf,
                         RMIServerSocketFactory ssf)
            throws RemoteException {
            super(port, csf, ssf);
        }

        public String sayHello() {
            return "Hello World!";
        }

        public Remote runServer() throws IOException {
            System.out.println("Inside HelloImpl::runServer");
            // Get a remote stub for this RMI object
            //
            Remote stub = toStub(this);
            System.out.println("Stub = " + stub);
            return stub;
        }
    }

    public class HelloClient {

        public void runClient(Remote stub) throws IOException {
            System.out.println("Inside HelloClient::runClient");
            // "obj" is the identifier that we'll use to refer
            // to the remote object that implements the "Hello"
            // interface
            Hello obj = (Hello) stub;
            String message = obj.sayHello();
            System.out.println(message);
        }
    }

    public static class ClientFactory extends SslRMIClientSocketFactory {

        public ClientFactory() {
            super();
        }

        public Socket createSocket(String host, int port) throws IOException {
            System.out.println("ClientFactory::Calling createSocket(" +
                               host + "," + port + ")");
            return super.createSocket(host, port);
        }
    }

    public static class ServerFactory extends SslRMIServerSocketFactory {

        public ServerFactory() {
            super();
        }

        public ServerFactory(String[] ciphers,
                             String[] protocols,
                             boolean need) {
            super(ciphers, protocols, need);
        }

        public ServerFactory(SSLContext context,
                             String[] ciphers,
                             String[] protocols,
                             boolean need) {
            super(context, ciphers, protocols, need);
        }

        public ServerSocket createServerSocket(int port) throws IOException {
            System.out.println("ServerFactory::Calling createServerSocket(" +
                               port + ")");
            return super.createServerSocket(port);
        }
    }

    public void testRmiCommunication(RMIServerSocketFactory serverFactory, boolean expectException) {

        HelloImpl server = null;
        try {
            server = new HelloImpl(0,
                                    new ClientFactory(),
                                    serverFactory);
            Remote stub = server.runServer();
            HelloClient client = new HelloClient();
            client.runClient(stub);
            if (expectException) {
                throw new RuntimeException("Test completed without throwing an expected exception.");
            }

        } catch (IOException exc) {
            if (!expectException) {
                throw new RuntimeException("An error occurred during test execution", exc);
            } else {
                System.out.println("Caught expected exception: " + exc);
            }

        }
    }

    private static void testServerFactory(String[] cipherSuites, String[] protocol, String expectedMessage) throws Exception {
        try {
            new ServerFactory(SSLContext.getDefault(),
                    cipherSuites, protocol, false);
            throw new RuntimeException(
                    "The expected exception for "+ expectedMessage + " was not thrown.");
        } catch (IllegalArgumentException exc) {
            // expecting an exception with a specific message
            // anything else is an error
            if (!exc.getMessage().toLowerCase().contains(expectedMessage)) {
                throw exc;
            }
        }
    }

    public void runTest(int testNumber) throws Exception {
        System.out.println("Running test " + testNumber);

        switch (testNumber) {
            /* default constructor - default config */
            case 1 -> testRmiCommunication(new ServerFactory(), false);

            /* non-default constructor - default config */
            case 2 -> testRmiCommunication(new ServerFactory(null, null, false), false);

            /* needClientAuth=true */
            case 3 -> testRmiCommunication(new ServerFactory(null, null, null, true), false);

            /* server side dummy_ciphersuite */
            case 4 ->
                testServerFactory(new String[]{"dummy_ciphersuite"}, null, "unsupported ciphersuite");

            /* server side dummy_protocol */
            case 5 ->
                testServerFactory(null, new String[]{"dummy_protocol"}, "unsupported protocol");

            /* client side dummy_ciphersuite */
            case 6 -> {
                System.setProperty("javax.rmi.ssl.client.enabledCipherSuites",
                        "dummy_ciphersuite");
                testRmiCommunication(new ServerFactory(), true);
            }

            /* client side dummy_protocol */
            case 7 -> {
                System.setProperty("javax.rmi.ssl.client.enabledProtocols",
                        "dummy_protocol");
                testRmiCommunication(new ServerFactory(), true);
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