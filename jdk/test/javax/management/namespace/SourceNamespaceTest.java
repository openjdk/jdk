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
 * @test SourceNamespaceTest.java
 * @summary Test how queryNames works with Namespaces.
 * @bug 5072476
 * @author Daniel Fuchs
 * @run clean SourceNamespaceTest Wombat WombatMBean
 * @run build SourceNamespaceTest Wombat WombatMBean
 * @run main SourceNamespaceTest
 */


import javax.management.JMException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;

/**
 * A simple test to test the source directory parameter...
 * @author dfuchs
 */
public class SourceNamespaceTest {


    public static void localTest() throws JMException {
        final JMXNamespace adir =
                new JMXNamespace(MBeanServerFactory.newMBeanServer());

        // put a wombat in adir...
        final Wombat w1 = new Wombat();
        final ObjectName wn1 = new ObjectName("wilderness:type=Wombat,name=gloups");
        adir.getSourceServer().registerMBean(w1,wn1);

        // register adir
        final MBeanServer server = MBeanServerFactory.newMBeanServer();
        server.registerMBean(adir, JMXNamespaces.getNamespaceObjectName("adir"));

        if (! (server.isRegistered(JMXNamespaces.insertPath("adir", wn1))))
            throw new RuntimeException("Test failed: " +
                    JMXNamespaces.insertPath("adir", wn1) + " not found");

        System.out.println("Wombat gloups correctly registered...");

        // put another wombat in adir...
        final Wombat w2 = new Wombat();
        final ObjectName wn2 =
                new ObjectName("wilderness:type=Wombat,name=pasgloups");
        server.registerMBean(w2,JMXNamespaces.insertPath("adir", wn2));

        if (! (server.isRegistered(JMXNamespaces.insertPath("adir", wn2))))
            throw new RuntimeException("Test failed: " +
                    JMXNamespaces.insertPath("adir", wn2) + " not found");

        System.out.println("Wombat pasgloups correctly registered...");


        // make an alias
        final JMXNamespace alias = new JMXNamespace(
                JMXNamespaces.narrowToNamespace(server,"adir"));
        server.registerMBean(alias,
                JMXNamespaces.getNamespaceObjectName("alias"));

        if (! (server.isRegistered(JMXNamespaces.insertPath("alias", wn1))))
            throw new RuntimeException("Test failed: " +
                    JMXNamespaces.insertPath("alias", wn1) + " not found");

        System.out.println("Wombat gloups accessible through alias...");

        if (! (server.isRegistered(JMXNamespaces.insertPath("alias", wn2))))
            throw new RuntimeException("Test failed: " +
                    JMXNamespaces.insertPath("alias", wn2) + " not found");

        System.out.println("Wombat pasgloups accessible through alias...");

        final WombatMBean wp2 = JMX.newMBeanProxy(server,
                JMXNamespaces.insertPath("alias",wn2), WombatMBean.class);
        System.out.println(JMXNamespaces.insertPath("alias",wn2).toString()
                +" says: "+wp2.getCaption());

        // We're going to make another alias, but register it in a different
        // MBeanServer. This is to make sure that source server and target
        // server are not mixed up.
        //
        final MBeanServer server2 = MBeanServerFactory.newMBeanServer();
        final JMXNamespace alias2 = new JMXNamespace(
                JMXNamespaces.narrowToNamespace(server,"adir"));
        server2.registerMBean(alias2,
                JMXNamespaces.getNamespaceObjectName("alias2"));


        if (! (server2.isRegistered(JMXNamespaces.insertPath("alias2", wn1))))
            throw new RuntimeException("Test failed: " +
                    JMXNamespaces.insertPath("alias2", wn1) + " not found");

        System.out.println("Wombat gloups accessible through alias2...");

        if (! (server2.isRegistered(JMXNamespaces.insertPath("alias2", wn2))))
            throw new RuntimeException("Test failed: " +
                    JMXNamespaces.insertPath("alias2", wn2) + " not found");

        System.out.println("Wombat pasgloups accessible through alias...");

        final WombatMBean wp22 = JMX.newMBeanProxy(server2,
                JMXNamespaces.insertPath("alias2",wn2), WombatMBean.class);
        System.out.println(JMXNamespaces.insertPath("alias2",wn2).toString()
                +" says: "+wp22.getCaption());



    }

    public static void main(String[] args) throws Exception {
        localTest();
    }

}
