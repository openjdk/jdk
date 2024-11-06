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


/*
 * @test
 * @summary Record timings of JMX Connector operations.
 * @library /test/lib
 *
 * @run main/othervm JMXBench rmi
 * @run main/othervm JMXBench http
 * @run main/othervm JMXBench rmi platform
 * @run main/othervm JMXBench http platform
 */
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.lang.management.*;

import javax.management.*;
import javax.management.remote.*;

import jdk.test.lib.Asserts;

public class JMXBench {

    String proto;
    boolean platform;
    String id;

    public static void main(String[] args) throws Exception {

        if (args.length < 1 || args.length > 2) {
            throw new RuntimeException("Run test with main arg to specify protocol (e.g. rmi or http), and optionally the keyword 'platform'.");
        }
        boolean platform = (args.length >= 2 && args[1].equals("platform"));
        new JMXBench(args[0], platform).run();
    }

    public JMXBench(String proto, boolean platform) {
        this.proto = proto;
        this.platform = platform;
        id = proto + " " + (platform ? "platformMBS" : "newMBS");
    }

    public void run() throws Exception {

        MBeanServer mbs = platform ? ManagementFactory.getPlatformMBeanServer() : MBeanServerFactory.createMBeanServer();

        JMXServiceURL addr = new JMXServiceURL(proto, null, 0);
        System.out.println("Creating ConnectorServer on " + addr);
        Map<String, ?> env = Collections.singletonMap("jmx.remote.x.daemon", "true");
        JMXConnectorServer server = JMXConnectorServerFactory.newJMXConnectorServer(addr, env, mbs);

        Timer t = new Timer(id + " server start");
        server.start();
        t.stop();
        System.out.println("Started server on " + server.getAddress());

        System.out.println("Connecting a client to the server ...");

        t = new Timer(id + " connect"); 
        final JMXConnector conn = JMXConnectorFactory.connect(server.getAddress());
        t.stop();

        t = new Timer(id + " getMBSC");
        MBeanServerConnection mbsc = conn.getMBeanServerConnection();
        t.stop();

        t = new Timer(id + " getDefaultDomain");
        String defaultDomain = mbsc.getDefaultDomain();
        t.stop();
        System.out.println("defaultDomain: " + defaultDomain);

        t = new Timer(id + " getMBeanCount");
        int mbeanCount = mbsc.getMBeanCount();
        t.stop();
        System.out.println("mbeanCount: " + mbeanCount);


        ObjectName o = new ObjectName("JMImplementation:type=MBeanServerDelegate");
        MBeanInfo mbeanInfo = null;
        t = new Timer(id + " getMBInfo JMImplementation 1");
        try {
            mbeanInfo = mbsc.getMBeanInfo(o);
        } catch (Exception e) {
            // ignore
        }
        t.stop();
        System.out.println("MBeanInfo: " + mbeanInfo);

        t = new Timer(id + " getMBInfo JMImplementation 2");
        try {
            mbeanInfo = mbsc.getMBeanInfo(o);
        } catch (Exception e) {
            // ignore
        }
        t.stop();
        System.out.println("MBeanInfo: " + mbeanInfo);

        t = new Timer(id + " isRegistered (known to be registered) 1");
        boolean isRegistered = mbsc.isRegistered(o);
        t.stop();
        Asserts.assertTrue(isRegistered, "expected isRegistered true");
        t = new Timer(id + " isRegistered (known to be registered) 2");
        isRegistered = mbsc.isRegistered(o);
        t.stop();
        Asserts.assertTrue(isRegistered, "expected isRegistered true");

        ObjectName unknownObj = new ObjectName("SomeUnknownObject:type=Mystery");
        t = new Timer(id + " isRegistered (known not to be registered) 1");
        isRegistered = mbsc.isRegistered(unknownObj);
        t.stop();
        Asserts.assertFalse(isRegistered, "expected isRegistered false");
        t = new Timer(id + " isRegistered (known not to be registered) 2");
        isRegistered = mbsc.isRegistered(unknownObj);
        t.stop();
        Asserts.assertFalse(isRegistered, "expected isRegistered false");

        Object attr = null;
        t = new Timer(id + " getAttribute (known not to exist) 1");
        try {
            attr = mbsc.getAttribute(o, "foobar");
            Asserts.fail("expected AttributeNotFoundException");
        } catch (AttributeNotFoundException anfe) {
        }
        t.stop();
        System.out.println("attr = " + attr);
        t = new Timer(id + " getAttribute (known not to exist) 2");
        try {
            attr = mbsc.getAttribute(o, "foobar");
            Asserts.fail("expected AttributeNotFoundException");
        } catch (AttributeNotFoundException anfe) {
        }
        t.stop();
        System.out.println("attr = " + attr);

        t = new Timer(id + " getAttribute (known to exist) 1");
        try {
            attr = mbsc.getAttribute(o, "ImplementationVersion");
        } catch (AttributeNotFoundException anfe) {
            Asserts.fail("NOT expected: " + anfe);
        }
        t.stop();
        System.out.println("attr = " + attr);

        t = new Timer(id + " getAttribute (known to exist) 2");
        try {
            attr = mbsc.getAttribute(o, "ImplementationVersion");
        } catch (AttributeNotFoundException anfe) {
            Asserts.fail("NOT expected: " + anfe);
        }
        t.stop();
        System.out.println("attr = " + attr);

        AttributeList attrs = new AttributeList();
        Attribute a1 = new Attribute("a1", 1);
        Attribute a2 = new Attribute("a2", 2);
        attrs.add(a1);
        attrs.add(a2);

        AttributeList attrResults = null;
        t = new Timer(id + " getAttributes (plural) (not known)");
        try {
            attrResults = mbsc.getAttributes(o, new String [] { "a1", "a2"});
        } catch (Exception e) {
            Asserts.fail("NOT expected: " + e);
        }
        Asserts.assertTrue(attrResults.size() == 0, "attribute values not expected");
        t.stop();
        System.out.println("attr = " + attr);


        t = new Timer(id + " setAttribute (not known)");
        Object attrValue = null;
        try {
            mbsc.setAttribute(o, a1);
            Asserts.fail("NO expected AttributeNotFoundException");
        } catch (AttributeNotFoundException e) {
            System.out.println("got expected: " + e);
        }
        t.stop();
        
        t = new Timer(id + " setAttributes");
        try {
            attrs = mbsc.setAttributes(o,  attrs);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            Asserts.fail("NOT expected: " + e);
        }
        t.stop();
        System.out.println("attrs = " + attrs);

        

        NotificationListener listener = new MyListener();
        t = new Timer(id + " addNotificationListener: null Listener"); 
        try {
            mbsc.addNotificationListener(o, (NotificationListener) null, (NotificationFilter) null, (Object) null); 
            Asserts.fail(id + ": No expected Exception.");
        } catch (Exception e) {
            System.err.println("As expected: " + e);
        }
        t.stop();

        t = new Timer(id + " addNotificationListener: valid Listener 1"); 
        mbsc.addNotificationListener(o, listener, null, null); 
        t.stop();
        t = new Timer(id + " addNotificationListener: valid Listener 2"); 
        mbsc.addNotificationListener(o, listener, null, null); 
        t.stop();

        t = new Timer(id + " addNotificationListener: with handback object"); 
        mbsc.addNotificationListener(o, listener, null, new byte[1024 * 1024]); 
        t.stop();


        t = new Timer(id + " queryMBeans (null query)");
        Set<ObjectInstance> objects = mbsc.queryMBeans(null, null);
        t.stop();
        System.out.println("objects: " + objects);


        if (platform) {
            MBeanInfo mbeanInfoClassLoading = null;
            // o = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
            o = new ObjectName("java.lang:type=ClassLoading");
            t = new Timer(id + " getMBInfo 2");
            try {
                mbeanInfoClassLoading = mbsc.getMBeanInfo(o);
            } catch (Exception e) {
                e.printStackTrace(System.err);
                Asserts.fail(id + ": unexpected Exception: " + e);
            }
            t.stop();
            System.out.println("MBeanInfo ClassLoading: " + mbeanInfoClassLoading);

            t = new Timer(id + " getAttribute (known to exist) 1");
            attr = mbsc.getAttribute(o, "TotalLoadedClassCount");
            t.stop();
            System.out.println("attr = " + attr);

            t = new Timer(id + " getAttribute (known to exist) 2");
            attr = mbsc.getAttribute(o, "TotalLoadedClassCount");
            t.stop();

            t = new Timer(id + " getAttribute (known to exist) 3");
            attr = mbsc.getAttribute(o, "TotalLoadedClassCount");
            t.stop();

            // Get a boolean Attribute value, flip it, and check it:
            t = new Timer(id + " getAttribute (Verbose, known to exist)");
            boolean isVerbose = (Boolean) mbsc.getAttribute(o, "Verbose");
            t.stop();
            System.out.println("Verbose attr = " + attr);
            Attribute updatedAttribute = new Attribute("Verbose", (Boolean) !isVerbose);
            t = new Timer(id + " setAttribute (updating, known to exist)");
            mbsc.setAttribute(o, updatedAttribute);
            t.stop();
            t = new Timer(id + " getAttribute (updated attribute, known to exist)");
            boolean isVerboseChanged = (boolean) mbsc.getAttribute(o, "Verbose");
            t.stop();

            System.out.println("updated attr = " + attr);
            Asserts.assertTrue(isVerbose != isVerboseChanged, "Verbose setting not changed: " + isVerboseChanged);
        }

        System.out.println("Closing the client ...");
        t = new Timer(id + " close connection");
        conn.close();
        t.stop();

    }

    public class MyListener implements NotificationListener {

        public void handleNotification(Notification notification, Object handback) {

        }
    }

    public interface MyMBean {
        public void setAttr1(int a);
        public int getAttr1(int a);
        public void setAttr2(int a);
        public int getAttr2(int a);
    }

    public class My implements MyMBean {
        int a1;
        int a2;
        public void setAttr1(int a) { a1 = a; }
        public int getAttr1(int a) { return a1; }
        public void setAttr2(int a) { a2 = a; }
        public int getAttr2(int a) { return a2; }

    }

    public class Timer {
        String s;
        long t1;

        public Timer(String s) {
            this.s = s;
            t1 = System.nanoTime();
        }

        public void stop() {
            long t = System.nanoTime() - t1;
            System.out.println("Timer: " + s + ": " + t + " nanos, or " + ((float) t/1000000) + " millis");
        }
    }
}
