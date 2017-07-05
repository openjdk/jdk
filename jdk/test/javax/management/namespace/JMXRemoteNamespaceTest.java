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
 * @test JMXRemoteNamespaceTest.java
 * @summary Basic tests on a JMXRemoteNamespace.
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean JMXRemoteNamespaceTest Wombat WombatMBean
 * @run build JMXRemoteNamespaceTest Wombat WombatMBean
 * @run main JMXRemoteNamespaceTest
 */

import javax.management.JMX;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespaces;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.NotificationListener;
import javax.management.namespace.JMXRemoteNamespace;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.IOException;
import javax.management.AttributeChangeNotification;

/**
 * Test simple creation/registration of namespace.
 *
 */
public class JMXRemoteNamespaceTest {

    static class MyConnect implements NotificationListener {
        private final JMXRemoteNamespace my;
        private final List<Notification> list;
        private volatile int connectCount=0;
        private int closeCount=0;
        private final ObjectName myname;
        public MyConnect(JMXRemoteNamespace my, ObjectName myname) {
            this.my=my;
            this.myname = myname;
            list = Collections.synchronizedList(new ArrayList<Notification>());
            my.addNotificationListener(this, null, null);
        }

        public synchronized void connect() throws IOException {
                my.connect();
                if (!my.isConnected())
                    throw new IOException(myname+" should be connected");
                connectCount++;
        }

        public void close() throws IOException {
                my.close();
                if (my.isConnected())
                    throw new IOException(myname+" shouldn't be connected");
                closeCount++;
        }

        public synchronized int getConnectCount() {
            return connectCount;
        }
        public synchronized int getClosedCount() {
            return closeCount;
        }

        public synchronized void handleNotification(Notification notification,
                Object handback) {
            list.add(notification);
        }

        public synchronized void checkNotifs(int externalConnect,
                int externalClosed) throws Exception {
            System.err.println("Connected: "+connectCount+" time"+
                    ((connectCount>1)?"s":""));
            System.err.println("Closed: "+closeCount+" time"+
                    ((closeCount>1)?"s":""));
            System.err.println("Received:");
            int cl=0;
            int co=0;
            for (Notification n : list) {
                System.err.println("\t"+n);
                if (!(n instanceof AttributeChangeNotification))
                    throw new Exception("Unexpected notif: "+n.getClass());
                final AttributeChangeNotification acn =
                        (AttributeChangeNotification)n;
                if (((Boolean)acn.getNewValue()).booleanValue())
                    co++;
                else cl++;
                if ((((Boolean)acn.getNewValue()).booleanValue())
                    == (((Boolean)acn.getOldValue()).booleanValue())) {
                    throw new Exception("Bad values: old=new");
                }
            }
            if (! (list.size()==(closeCount+connectCount+
                    externalClosed+externalConnect))) {
                throw new Exception("Bad notif count - got "+list.size());
            }
            if (cl!=(closeCount+externalClosed)) {
                throw new Exception("Bad count of close notif: expected "
                        +(closeCount+externalClosed)+", got"+cl);
            }
            if (co!=(connectCount+externalConnect)) {
                throw new Exception("Bad count of connect notif: expected "
                        +(connectCount+externalConnect)+", got"+co);
            }
        }
    }

    public static void testConnectClose() throws Exception {
        final MBeanServer myServer = MBeanServerFactory.newMBeanServer();
        final JMXConnectorServer myRMI =
                JMXConnectorServerFactory.newJMXConnectorServer(
                new JMXServiceURL("rmi",null,0), null, myServer);
        myRMI.start();
        try {
        final JMXRemoteNamespace my =
                JMXRemoteNamespace.newJMXRemoteNamespace(
                myRMI.getAddress(),null);
        final MBeanServer s = MBeanServerFactory.newMBeanServer();
        final ObjectName myname = JMXNamespaces.getNamespaceObjectName("my");
        final ObjectName wname = ObjectName.getInstance("backyard:type=Wombat");
        myServer.registerMBean(new Wombat(),wname);
        final MyConnect myc = new MyConnect(my,myname);
        myc.connect();
        myc.close();
        myc.connect();
        s.registerMBean(my,myname);
        myc.close();
        myc.connect();
        if (!s.queryNames(new ObjectName("my//b*:*"),null).contains(
                JMXNamespaces.insertPath("my", wname))) {
            throw new RuntimeException("1: Wombat not found: "+wname);
        }
        myc.close();
        myc.connect();
        final MBeanServer cd = JMXNamespaces.narrowToNamespace(s, "my");
        if (!cd.queryNames(new ObjectName("b*:*"),null).contains(wname)) {
            throw new RuntimeException("2: Wombat not found: "+wname);
        }
        myc.close();
        myc.connect();
        System.out.println("Found a Wombat in my backyard.");

        final String deepThoughts = "I want to leave this backyard!";
        final WombatMBean w = JMX.newMBeanProxy(cd, wname, WombatMBean.class);
        w.setCaption(deepThoughts);
        if (!deepThoughts.equals(w.getCaption()))
                throw new RuntimeException("4: Wombat is not thinking right: "+
                        w.getCaption());
        s.unregisterMBean(myname);
        if (my.isConnected())
            throw new Exception(myname+" shouldn't be connected");
        myc.connect();
        myc.close();
        myc.checkNotifs(0,1);
        } finally {
            myRMI.stop();
        }

    }

    public static void main(String... args) throws Exception {
        testConnectClose();
    }
}
