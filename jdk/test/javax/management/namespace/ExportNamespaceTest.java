/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 * @test ExportNamespaceTest.java
 * @summary Test that you can export a single namespace through a
 *          JMXConnectorServer.
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean ExportNamespaceTest Wombat WombatMBean
 * @run build ExportNamespaceTest Wombat WombatMBean
 * @run main ExportNamespaceTest
 */

import javax.management.JMX;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;


/**
 * Test simple creation/registration of namespace.
 *
 */
public class ExportNamespaceTest {

    public static void testExport() throws Exception {
        final JMXNamespace my =
                new JMXNamespace(MBeanServerFactory.newMBeanServer());
        final MBeanServer s = MBeanServerFactory.newMBeanServer();
        final ObjectName myname = JMXNamespaces.getNamespaceObjectName("my");
        final ObjectName wname = ObjectName.getInstance("backyard:type=Wombat");
        my.getSourceServer().registerMBean(new Wombat(),wname);
        s.registerMBean(my,myname);

        if (!s.queryNames(new ObjectName("my//b*:*"),null).contains(
                JMXNamespaces.insertPath("my", wname))) {
            throw new RuntimeException("1: Wombat not found: "+wname);
        }

        final MBeanServer cd = JMXNamespaces.narrowToNamespace(s, "my");
        if (!cd.queryNames(new ObjectName("b*:*"),null).contains(wname)) {
            throw new RuntimeException("2: Wombat not found: "+wname);
        }

        final JMXServiceURL url = new JMXServiceURL("rmi",null,0);
        final JMXConnectorServer server =
                JMXConnectorServerFactory.newJMXConnectorServer(url, null, cd);
        server.start();

        final JMXConnector jc = JMXConnectorFactory.
                connect(server.getAddress(),null);
        final MBeanServerConnection mbsc = jc.getMBeanServerConnection();

        if (!mbsc.queryNames(new ObjectName("b*:*"),null).contains(wname)) {
            throw new RuntimeException("3: Wombat not found: "+wname);
        }
        System.out.println("Found a Wombat in my backyard.");

        final String deepThoughts = "I want to leave this backyard!";
        final WombatMBean w = JMX.newMBeanProxy(mbsc, wname, WombatMBean.class);
        w.setCaption(deepThoughts);
        if (!deepThoughts.equals(w.getCaption()))
                throw new RuntimeException("4: Wombat is not thinking right: "+
                        w.getCaption());

    }

    public static void main(String... args) throws Exception {
        testExport();
    }
}
