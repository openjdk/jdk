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
 * @test
 * @bug 5108776
 * @summary Test that the EventClientDelegate MBean does not require extra
 * permissions compared with plain addNotificationListener.
 * @author Eamonn McManus
 * @run main/othervm -Dxjava.security.debug=policy,access,failure EventDelegateSecurityTest
 */

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanPermission;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventClient;
import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXPrincipal;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.MBeanServerForwarder;
import javax.security.auth.Subject;

public class EventDelegateSecurityTest {
    private static final BlockingQueue<Notification> notifQ =
            new SynchronousQueue<Notification>();

    private static volatile long seqNo;
    private static volatile long expectSeqNo;

    private static class QueueListener implements NotificationListener {
        public void handleNotification(Notification notification,
                                       Object handback) {
            try {
                notifQ.put(notification);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }
    private static final NotificationListener queueListener = new QueueListener();

    public static interface SenderMBean {
        public void send();
    }

    public static class Sender
            extends NotificationBroadcasterSupport implements SenderMBean {
        public void send() {
            Notification n = new Notification("x", this, seqNo++);
            sendNotification(n);
        }
    }

    private static class LimitInvocationHandler implements InvocationHandler {
        private MBeanServer nextMBS;
        private final Set<String> allowedMethods = new HashSet<String>();

        void allow(String... names) {
            synchronized (allowedMethods) {
                allowedMethods.addAll(Arrays.asList(names));
            }
        }

        public Object invoke(Object proxy, Method m, Object[] args)
                throws Throwable {
            System.out.println(
                    "filter: " + m.getName() +
                    ((args == null) ? "[]" : Arrays.deepToString(args)));
            String name = m.getName();

            if (name.equals("getMBeanServer"))
                return nextMBS;

            if (name.equals("setMBeanServer")) {
                nextMBS = (MBeanServer) args[0];
                return null;
            }

            if (m.getDeclaringClass() == Object.class ||
                    allowedMethods.contains(name)) {
                try {
                    return m.invoke(nextMBS, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            } else {
                System.out.println("...refused");
                throw new SecurityException(
                        "Method refused: " + m.getDeclaringClass().getName() +
                        "." + m.getName() +
                        ((args == null) ? "[]" : Arrays.deepToString(args)));
            }
        }

    }

    private static interface MakeConnectorServer {
        public JMXConnectorServer make(JMXServiceURL url) throws IOException;
    }


    public static void main(String[] args) throws Exception {
        JMXPrincipal rootPrincipal = new JMXPrincipal("root");
        Subject rootSubject = new Subject();
        rootSubject.getPrincipals().add(rootPrincipal);
        Subject.doAsPrivileged(rootSubject, new PrivilegedExceptionAction<Void>() {
            public Void run() throws Exception {
                mainAsRoot();
                return null;
            }
        }, null);
    }

    private static void mainAsRoot() throws Exception {
        AccessControlContext acc = AccessController.getContext();
        Subject subject = Subject.getSubject(acc);
        System.out.println("Subject: " + subject);
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("a:b=c");
        mbs.registerMBean(new Sender(), name);

        System.out.println("Test with no installed security");
        test(mbs, name, new MakeConnectorServer() {
            public JMXConnectorServer make(JMXServiceURL url) throws IOException {
                return
                    JMXConnectorServerFactory.newJMXConnectorServer(url, null, null);
            }
        });

        System.out.println("Test with filtering MBeanServerForwarder");
        LimitInvocationHandler limitIH = new LimitInvocationHandler();
        // We allow getClassLoaderRepository because the ConnectorServer
        // calls it so any real checking MBeanServerForwarder must accept it.
        limitIH.allow(
                "addNotificationListener", "removeNotificationListener",
                "getClassLoaderRepository"
                );
        final MBeanServerForwarder limitMBSF = (MBeanServerForwarder)
            Proxy.newProxyInstance(
                MBeanServerForwarder.class.getClassLoader(),
                new Class<?>[] {MBeanServerForwarder.class},
                limitIH);
        // We go to considerable lengths to ensure that the ConnectorServer has
        // no MBeanServer when the EventClientDelegate forwarder is activated,
        // so that the calls it makes when it is later linked to an MBeanServer
        // go through the limitMBSF.
        test(mbs, name, new MakeConnectorServer() {
            public JMXConnectorServer make(JMXServiceURL url) throws IOException {
                JMXConnectorServer cs =
                    JMXConnectorServerFactory.newJMXConnectorServer(url, null, null);
                limitMBSF.setMBeanServer(mbs);
                cs.setMBeanServerForwarder(limitMBSF);
                return cs;
            }
        });

        final File policyFile =
                File.createTempFile("EventDelegateSecurityTest", ".policy");
        PrintWriter pw = new PrintWriter(policyFile);
        String JMXPrincipal = JMXPrincipal.class.getName();
        String AllPermission = AllPermission.class.getName();
        String MBeanPermission = MBeanPermission.class.getName();
        pw.println("grant principal " + JMXPrincipal + " \"root\" {");
        pw.println("    permission " + AllPermission + ";");
        pw.println("};");
        pw.println("grant principal " + JMXPrincipal + " \"user\" {");
        pw.println("    permission " + MBeanPermission + " \"*\", " +
                " \"addNotificationListener\";");
        pw.println("    permission " + MBeanPermission + " \"*\", " +
                " \"removeNotificationListener\";");
        pw.println("};");
        pw.close();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                policyFile.delete();
            }
        });
        System.setProperty("java.security.policy", policyFile.getAbsolutePath());
        System.setSecurityManager(new SecurityManager());
        test(mbs, name, new MakeConnectorServer() {
            public JMXConnectorServer make(JMXServiceURL url) throws IOException {
                Map<String, Object> env = new HashMap<String, Object>();
                env.put(JMXConnectorServer.AUTHENTICATOR, new JMXAuthenticator() {
                    public Subject authenticate(Object credentials) {
                        Subject s = new Subject();
                        s.getPrincipals().add(new JMXPrincipal("user"));
                        return s;
                    }
                });
                return
                    JMXConnectorServerFactory.newJMXConnectorServer(url, env, null);
            }
        });
    }

    private static void test(MBeanServer mbs, ObjectName name) throws Exception {
        test(mbs, name, null);
    }

    private static void test(
            MBeanServer mbs, ObjectName name, MakeConnectorServer make)
            throws Exception {
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///");
        JMXConnectorServer cs = make.make(url);
        ObjectName csName = new ObjectName("a:type=ConnectorServer");
        mbs.registerMBean(cs, csName);
        cs.start();
        try {
            JMXServiceURL addr = cs.getAddress();
            JMXConnector cc = JMXConnectorFactory.connect(addr);
            MBeanServerConnection mbsc = cc.getMBeanServerConnection();
            test(mbs, mbsc, name);
            cc.close();
            mbs.unregisterMBean(csName);
        } finally {
            cs.stop();
        }
    }

    private static void test(
            MBeanServer mbs, MBeanServerConnection mbsc, ObjectName name)
            throws Exception {
        EventClient ec = new EventClient(mbsc);
        ec.addNotificationListener(name, queueListener, null, null);
        mbs.invoke(name, "send", null, null);

        Notification n = notifQ.poll(5, TimeUnit.SECONDS);
        if (n == null)
            throw new Exception("FAILED: notif not delivered");
        if (n.getSequenceNumber() != expectSeqNo) {
            throw new Exception(
                    "FAILED: notif seqno " + n.getSequenceNumber() +
                    " should be " + expectSeqNo);
        }
        expectSeqNo++;

        ec.removeNotificationListener(name, queueListener);
        ec.close();
    }
}
