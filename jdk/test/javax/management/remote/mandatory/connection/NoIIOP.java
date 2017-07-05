/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8004502
 * @summary Sanity check that attempts to use the IIOP transport or
 *   RMIIIOPServerImpl when RMI/IIOP not present throws the expected exceptions
 */

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.remote.*;
import javax.management.remote.rmi.*;
import java.net.MalformedURLException;
import java.io.IOException;
import javax.security.auth.Subject;
import java.rmi.NoSuchObjectException;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServerFactory;

public class NoIIOP {

    /**
     * RMIIIOPServerImpl implementation for testing purposes (methods are
     * overridden to be public to allow for testing)
     */
    static class MyRMIIIOPServerImpl extends RMIIIOPServerImpl {
        MyRMIIIOPServerImpl() throws IOException {
            super(null);
        }
        @Override
        public void export() throws IOException {
            super.export();
        }
        @Override
        public String getProtocol() {
            return super.getProtocol();
        }
        @Override
        public RMIConnection makeClient(String connectionId, Subject subject)
            throws IOException
        {
            return super.makeClient(connectionId, subject);
        }
        @Override
        public void closeClient(RMIConnection client) throws IOException {
            super.closeClient(client);
        }
        @Override
        public void closeServer() throws IOException {
            super.closeServer();
        }
    }


    public static void main(String[] args) throws Exception {
        try {
            Class.forName("javax.management.remote.rmi._RMIConnectionImpl_Tie");
            System.out.println("RMI/IIOP appears to be supported, test skipped");
            return;
        } catch (ClassNotFoundException okay) { }

        JMXServiceURL url = new JMXServiceURL("service:jmx:iiop://");
        MBeanServer mbs = MBeanServerFactory.createMBeanServer();


        // test JMXConnectorFactory/JMXConnectorServerFactory

        try {
            JMXConnectorFactory.connect(url);
            throw new RuntimeException("connect did not throw MalformedURLException");
        } catch (MalformedURLException expected) { }

        try {
            JMXConnectorServerFactory.newJMXConnectorServer(url, null, null);
            throw new RuntimeException("newJMXConnectorServer did not throw MalformedURLException");
        } catch (MalformedURLException expected) { }


        // test RMIConnector/RMIConnectorServer

        RMIConnector connector = new RMIConnector(url, null);
        try {
            connector.connect();
            throw new RuntimeException("connect did not throw IOException");
        } catch (IOException expected) { }

        RMIConnectorServer server = new RMIConnectorServer(url, null, mbs);
        try {
            server.start();
            throw new RuntimeException("start did not throw IOException");
        } catch (IOException expected) { }


        // test RMIIIOPServerImpl

        MyRMIIIOPServerImpl impl = new MyRMIIIOPServerImpl();
        impl.setMBeanServer(mbs);
        System.out.println(impl.getProtocol());

        try {
            impl.export();
            throw new RuntimeException("export did not throw IOException");
        } catch (IOException expected) { }

        try {
            impl.newClient(null);
            throw new RuntimeException("newClient did not throw IOException");
        } catch (IOException expected) { }

        try {
            impl.toStub();
            throw new RuntimeException("toStub did not throw NoSuchObjectException");
        } catch (NoSuchObjectException expected) { }

        try {
            impl.closeServer();
            throw new RuntimeException("closeServer did not throw NoSuchObjectException");
        } catch (NoSuchObjectException expected) { }
    }
}
