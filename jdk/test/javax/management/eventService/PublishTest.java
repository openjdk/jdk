/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 */
public class PublishTest {
    private static MBeanServer mbeanServer;
    private static EventManager eventManager;
    private static ObjectName emitter;
    private static JMXServiceURL url;
    private static JMXConnectorServer server;
    private static JMXConnector conn;
    private static MBeanServerConnection client;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        System.out.println(">>> PublishTest-main basic tests ...");
        mbeanServer = MBeanServerFactory.createMBeanServer();

        // for 1.5
        if (System.getProperty("java.version").startsWith("1.5") &&
                !mbeanServer.isRegistered(EventClientDelegateMBean.OBJECT_NAME)) {
            System.out.print("Working on "+System.getProperty("java.version")+
                    " register "+EventClientDelegateMBean.OBJECT_NAME);

            mbeanServer.registerMBean(EventClientDelegate.
                    getEventClientDelegate(mbeanServer),
                    EventClientDelegateMBean.OBJECT_NAME);
        }

        eventManager = EventManager.getEventManager(mbeanServer);

        emitter = new ObjectName("Default:name=NotificationEmitter");

        url = new JMXServiceURL("rmi", null, 0) ;
        server =
                JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbeanServer);
        server.start();

        url = server.getAddress();
        conn = JMXConnectorFactory.connect(url, null);
        client = conn.getMBeanServerConnection();

        boolean succeed;

        System.out.println(">>> PublishTest-main: using the fetching EventRelay...");
        succeed = test(new EventClient(client));

        System.out.println(">>> PublishTest-main: using the pushing EventRelay...");
        succeed &= test(new EventClient(client,
                new RMIPushEventRelay(EventClientDelegate.getProxy(client)),
                null,
                EventClient.DEFAULT_LEASE_TIMEOUT));

        conn.close();
        server.stop();

        if (succeed) {
            System.out.println(">>> PublishTest-main: PASSE!");
        } else {
            System.out.println("\n>>> PublishTest-main: FAILED!");
            System.exit(1);
        }
    }

    public static boolean test(EventClient efClient) throws Exception {
        // add listener from the client side
        Listener listener = new Listener();
        efClient.subscribe(emitter, listener, null, null);

        ObjectName other = new ObjectName("Default:name=other");
        // publish notifs
        for (int i=0; i<sendNB; i++) {
            Notification notif = new Notification(myType, emitter, count++);
            Notification notif2 = new Notification(myType, other, 0);
            //System.out.println(">>> EventManagerService-NotificationEmitter-sendNotifications: "+i);

            eventManager.publish(emitter, notif);
            eventManager.publish(other, notif2); // should not received
        }

        // waiting
        long toWait = 6000;
        long stopTime = System.currentTimeMillis() + toWait;

        synchronized(listener) {
            while(listener.received < sendNB && toWait > 0) {
                listener.wait(toWait);
                toWait = stopTime - System.currentTimeMillis();
            }
        }

        // clean
        efClient.unsubscribe(emitter, listener);
        efClient.close();

        if (listener.received != sendNB) {
            System.out.println(">>> PublishTest-test: FAILED! Expected to receive "+sendNB+", but got "+listener.received);

            return false;
        } else if (listener.seqErr > 0) {
            System.out.println(">>> PublishTest-test: FAILED! The receiving sequence is not correct.");

            return false;
        } else {
            System.out.println(">>> PublishTest-test: got all expected "+listener.received);
            return true;
        }
    }

    private static class Listener implements NotificationListener {
        public int received = 0;
        public int seqErr = 0;

        private long lastSeq = -1;

        public void handleNotification(Notification notif, Object handback) {
            if (!myType.equals(notif.getType())) {
                System.out.println(">>> PublishTest-Listener: got unexpected notif: "+notif);
                System.exit(1);
            } else if (!emitter.equals(notif.getSource())) {
                System.out.println(">>> PublishTest-Listener: unknown ObjectName: "+notif.getSource());
                System.exit(1);
            }

            if (lastSeq == -1) {
                lastSeq = notif.getSequenceNumber();
            } else if (notif.getSequenceNumber() - lastSeq++ != 1) {
                seqErr++;
            }

            System.out.println(">>> PublishTest-Listener: got notif "+notif.getSequenceNumber());

            synchronized(this) {
                if (++received >= sendNB) {
                    this.notify();
                }
            }

            System.out.println(">>> PublishTest-Listener: received = "+received);
        }
    }

    private static int sendNB = 20;
    private static long count = 0;

    private static final String myType = "notification.my_notification";
}
