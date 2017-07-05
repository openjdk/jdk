/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test ProviderTest.java
 * @summary Tests jar services provider are called
 * @run clean ProviderTest provider.JMXConnectorProviderImpl provider.JMXConnectorServerProviderImpl
 * @run build ProviderTest provider.JMXConnectorProviderImpl provider.JMXConnectorServerProviderImpl
 * @run main ProviderTest
 */

import java.net.MalformedURLException;

import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.JMXProviderException;


import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServer;

/*
 * Tests jar services provider are called
 */
import provider.JMXConnectorProviderImpl;
import provider.JMXConnectorServerProviderImpl;
public class ProviderTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting ProviderTest");
        MBeanServer mbs = MBeanServerFactory.newMBeanServer();
        //First do the test with a protocol handled by Service providers
        JMXServiceURL url =
            new JMXServiceURL("service:jmx:rmi://");

        dotest(url, mbs);

        boolean clientCalled = provider.JMXConnectorProviderImpl.called();
        boolean serverCalled = provider.JMXConnectorServerProviderImpl.called();
        boolean ok = clientCalled && serverCalled;
        if (!ok) {
            if (!clientCalled)
                System.out.println("Client provider not called");
            if (!serverCalled)
                System.out.println("Server provider not called");
            System.out.println("Test Failed");
            System.exit(1);
        }

        //The Service Provider doesn't handle IIOP. Default providers MUST
        //be called.
        url =
            new JMXServiceURL("service:jmx:iiop://");

        dotest(url, mbs);

        // Unsupported protocol.
        JMXConnectorServer server = null;
        JMXConnector client = null;
        url =
            new JMXServiceURL("service:jmx:unknown-protocol://");
        try {
            server =
                JMXConnectorServerFactory.newJMXConnectorServer(url,
                                                                null,
                                                                mbs);
            System.out.println("Exception not thrown.");
            System.exit(1);
        }catch(MalformedURLException e) {
            System.out.println("Expected MalformedURLException thrown.");
        }
        catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exception thrown : " + e);
            System.exit(1);
        }

        try {
            client =
                JMXConnectorFactory.newJMXConnector(url,
                                                    null);
            System.out.println("Exception not thrown.");
            System.exit(1);
        }catch(MalformedURLException e) {
            System.out.println("Expected MalformedURLException thrown.");
        }
        catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exception thrown : " + e);
            System.exit(1);
        }

        //JMXConnectorProviderException
        url =
            new JMXServiceURL("service:jmx:throw-provider-exception://");
                try {
            server =
                JMXConnectorServerFactory.newJMXConnectorServer(url,
                                                                null,
                                                                mbs);
            System.out.println("Exception not thrown.");
            System.exit(1);
        }catch(JMXProviderException e) {
            System.out.println("Expected JMXProviderException thrown.");
        }
        catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exception thrown : " + e);
            System.exit(1);
        }

        try {
            client =
                JMXConnectorFactory.newJMXConnector(url,
                                                    null);
            System.out.println("Exception not thrown.");
            System.exit(1);
        }catch(JMXProviderException e) {
            System.out.println("Expected JMXProviderException thrown.");
        }
        catch(Exception e) {
            e.printStackTrace();
            System.out.println("Exception thrown : " + e);
            System.exit(1);
        }

        System.out.println("Test OK");
        return;
    }

    private static void dotest(JMXServiceURL url, MBeanServer mbs)
        throws Exception {
        JMXConnectorServer server = null;
        JMXConnector client = null;
        try {
            server =
                JMXConnectorServerFactory.newJMXConnectorServer(url,
                                                                null,
                                                                mbs);
        }catch(IllegalArgumentException e) {
            e.printStackTrace();
            System.exit(1);
        }
        server.start();
        JMXServiceURL outputAddr = server.getAddress();
        System.out.println("Server started ["+ outputAddr+ "]");

        try {
            client =
                JMXConnectorFactory.newJMXConnector(outputAddr, null);
        }catch(IllegalArgumentException e) {
            e.printStackTrace();
            System.exit(1);
        }

        client.connect();
        System.out.println("Client connected");

        MBeanServerConnection connection
            = client.getMBeanServerConnection();

        System.out.println(connection.getDefaultDomain());
    }
}
