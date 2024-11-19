/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.spi.NamingManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.util.Hashtable;
import java.util.Objects;

import jdk.test.lib.net.URIBuilder;


/*
 * @test
 * @bug 8338536
 * @summary Check if an object factory builder can be used to reconstruct
 *          object factories from a code base specified in a
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 * @library /test/lib ../../../../../../java/rmi/testlibrary
 * @build TestLibrary
 * @compile TestFactory.java TestObjectFactoryBuilder.java
 *
 * @run main/othervm ObjectFactoryBuilderCodebaseTest setObjectFactoryBuilder
 * @run main/othervm ObjectFactoryBuilderCodebaseTest default
 */
public class ObjectFactoryBuilderCodebaseTest {

    public static void main(String[] args) throws Exception {
        setupRmiHostNameAndRmiSocketFactory();
        boolean useCustomObjectFactoryBuilder =
                "setObjectFactoryBuilder".equals(args[0]);

        if (args.length > 0 && useCustomObjectFactoryBuilder) {
            NamingManager.setObjectFactoryBuilder(new TestObjectFactoryBuilder());
        }
        FileServer fileServer = configureAndLaunchFileServer();
        int registryPort;
        try {
            Registry registry = TestLibrary.createRegistryOnEphemeralPort();
            registryPort = TestLibrary.getRegistryPort(registry);
            System.out.println("Registry port: " + registryPort);
        } catch (RemoteException re) {
            throw new RuntimeException("Failed to create registry", re);
        }

        Context context = getInitialContext(registryPort);
        // Bind the Reference object
        String factoryURL = fileServer.factoryLocation();
        System.err.println("Setting Reference factory location: " + factoryURL);
        Reference ref = new Reference("TestObject", "com.test.TestFactory",
                factoryURL);
        context.bind("objectTest", ref);

        // Try to load bound reference
        try {
            Object object = context.lookup("objectTest");
            if (!useCustomObjectFactoryBuilder) {
                throw new RuntimeException("Lookup not expected to complete");
            }
            System.err.println("Loaded object: " + object);
        } catch (NamingException ne) {
            if (useCustomObjectFactoryBuilder) {
                throw new RuntimeException("Lookup expected to complete successfully", ne);
            }
        }
    }

    private static Context getInitialContext(int port) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();

        // Prepare registry URL
        String providerURL = URIBuilder.newBuilder()
                .loopback()
                .port(port)
                .scheme("rmi")
                .buildUnchecked().toString();

        env.put(Context.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.rmi.registry.RegistryContextFactory");
        env.put(Context.PROVIDER_URL, providerURL);
        return new InitialContext(env);
    }

    private record FileServer(Path rootPath, InetSocketAddress address, HttpServer httpServer) {

        public static FileServer newInstance(Path rootPath, InetSocketAddress address) {
            Objects.requireNonNull(address);
            Objects.requireNonNull(rootPath);
            var httpServer = SimpleFileServer.createFileServer(address, rootPath,
                    SimpleFileServer.OutputLevel.VERBOSE);
            return new FileServer(rootPath, address, httpServer);
        }

        String factoryLocation() {
            return URIBuilder.newBuilder()
                    .loopback()
                    .port(port())
                    .scheme("http")
                    .path("/")
                    .buildUnchecked()
                    .toString();
        }

        int port() {
            return httpServer.getAddress().getPort();
        }

        void start() {
            httpServer.start();
        }
    }

    // Prepares and launches the file server capable of serving TestFactory.class
    private static FileServer configureAndLaunchFileServer() throws IOException {

        // Location of compiled classes with compiled MyFactory
        Path factoryClassPath = Path.of(System.getProperty("test.classes", "."))
                .resolve(OBJ_FACTORY_PACKAGE_PATH)
                .resolve(OBJ_FACTORY_CLASS_NAME);

        // File server content root directory
        Path serverRoot = Paths.get("serverRoot").toAbsolutePath();
        Path packageDirInServerRoot = serverRoot.resolve(OBJ_FACTORY_PACKAGE_PATH);
        Path factoryClassFileInServerRoot = packageDirInServerRoot.resolve(OBJ_FACTORY_CLASS_NAME);

        // Remove files from previous run
        Files.deleteIfExists(factoryClassFileInServerRoot);
        Files.deleteIfExists(packageDirInServerRoot);
        Files.deleteIfExists(packageDirInServerRoot.getParent());
        Files.deleteIfExists(serverRoot);

        // Create server root and copy compiled object factory inside
        Files.createDirectories(packageDirInServerRoot);
        Files.copy(factoryClassPath, factoryClassFileInServerRoot);

        // Bind file server to loopback address
        InetSocketAddress serverAddress =
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        FileServer fileServer = FileServer.newInstance(serverRoot, serverAddress);

        // Start the file server
        fileServer.start();
        System.err.println("File server content root: " + serverRoot);
        System.err.printf("File server running on %s:%d%n",
                serverAddress.getAddress(), fileServer.port());
        return fileServer;
    }


    // Configure RMI to launch registry on a loopback address
    private static void setupRmiHostNameAndRmiSocketFactory() throws IOException {
        String rmiServerHostAddressString = InetAddress.getLoopbackAddress().getHostAddress();
        System.out.println("Setting 'java.rmi.server.hostname' to: " + rmiServerHostAddressString);
        System.setProperty("java.rmi.server.hostname", rmiServerHostAddressString);
        RMISocketFactory.setSocketFactory(new TestRmiSocketFactory());
    }

    private static class TestRmiSocketFactory extends RMISocketFactory {
        public ServerSocket createServerSocket(int port) throws IOException {
            var loopbackAddress = InetAddress.getLoopbackAddress();
            System.out.printf("Creating RMI server socket on %s:%d%n", loopbackAddress, port);
            ServerSocket rmiServerSocket = new ServerSocket();
            rmiServerSocket.setOption(java.net.StandardSocketOptions.SO_REUSEADDR, false);
            SocketAddress serverAddress = new InetSocketAddress(loopbackAddress, port);
            rmiServerSocket.bind(serverAddress, BACKLOG_OF_5);
            return rmiServerSocket;
        }

        public Socket createSocket(String host, int port) throws IOException {
            System.out.printf("Creating RMI client socket connected to %s:%d%n", host, port);
            // just call the default client socket factory
            return RMISocketFactory.getDefaultSocketFactory()
                    .createSocket(host, port);
        }
    }

    // File server backlog value
    private static final int BACKLOG_OF_5 = 5;
    // Test objects factory class filename
    private static final String OBJ_FACTORY_CLASS_NAME = "TestFactory.class";
    // Package directory of the test's objects factory class
    private static final Path OBJ_FACTORY_PACKAGE_PATH = Paths.get("com").resolve("test");
}
