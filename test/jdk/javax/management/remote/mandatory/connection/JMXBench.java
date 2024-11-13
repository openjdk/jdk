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
        t = new Timer(id + " connector.getConnectionId"); 
        String connId = conn.getConnectionId();
        t.stop();
        System.out.println("connector: " + conn);
        System.out.println("connectionId: " + connId);

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


        // getAttribute
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

        // getAttributes (multiple)
        AttributeList attrResults = null;
        t = new Timer(id + " getAttributes (two, not known)");
        try {
            attrResults = mbsc.getAttributes(o, new String [] { "unknown1", "unknown2"});
        } catch (Exception e) {
            Asserts.fail("NOT expected: " + e);
        }
        Asserts.assertTrue(attrResults.size() == 0, "attribute values not expected: " + attrResults);
        t.stop();
        System.out.println("attr = " + attr);

        // Get multiple Attributes that exist:
        AttributeList attrsJMimpl = new AttributeList();
        Attribute a1 = new Attribute("ImplementationName", null);
        Attribute a2 = new Attribute("ImplementationVersion", null);
        attrsJMimpl.add(a1);
        attrsJMimpl.add(a2);

        attrResults = null;
        t = new Timer(id + " getAttributes (two, known)");
        try {
            attrResults = mbsc.getAttributes(o,  new String [] { "ImplementationName", "ImplementationVersion" });
        } catch (Exception e) {
            e.printStackTrace(System.err);
            Asserts.fail("NOT expected: " + e);
        }
        t.stop();
        System.out.println("attrResults = " + attrResults);
        Asserts.assertTrue(attrResults.size() == 2, "two attribute values expected");


        // setAttribute
        Attribute aUnknown = new Attribute("foobar", null);
        t = new Timer(id + " setAttribute (not known)");
        try {
            mbsc.setAttribute(o, aUnknown);
            Asserts.fail("NO expected AttributeNotFoundException");
        } catch (AttributeNotFoundException e) {
            System.out.println("setAttribute got expected: " + e);
        }
        t.stop();

        t = new Timer(id + " setAttribute (known, not writeable)");
        try {
            mbsc.setAttribute(o, a1);
//            Asserts.fail("NO expected XXXXX AttributeNotFoundException");
        } catch (Exception e) {
            System.out.println("setAttribute got expected: " + e);
        }
        t.stop();
        Object attrValue = mbsc.getAttribute(o, "ImplementationName");
        System.out.println("getAttribute(ImplementationName) after write got: " + attrValue);
        Asserts.assertEquals(attrValue, "JMX", "ImplementationName was updated? " + attrValue);

        
        t = new Timer(id + " setAttributes (JMimpl, two, known)");
        try {
            attrResults = mbsc.setAttributes(o,  attrsJMimpl);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            Asserts.fail("setAttributes: NOT expected: " + e);
        }
        t.stop();
            System.out.println("setAttributes attrResults = " + attrResults);

            

            NotificationListener listener = new MyListener();
            t = new Timer(id + " addNotificationListener: null Listener"); 
            try {
                mbsc.addNotificationListener(o, (NotificationListener) null, (NotificationFilter) null, (Object) null); 
                Asserts.fail(id + ": addNotificationListener(null): No expected Exception.");
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
                // ClassLoading MBean has a Verbose attribute we can set and check.
                MBeanInfo mbeanInfoClassLoading = null;
                o = new ObjectName("java.lang:type=ClassLoading");
                t = new Timer(id + " getMBInfo ClassLoading");
                try {
                    mbeanInfoClassLoading = mbsc.getMBeanInfo(o);
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    Asserts.fail(id + ": getMBeanInfo: unexpected Exception: " + e);
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
                System.out.println("attr = " + attr);

                t = new Timer(id + " getAttribute (known to exist) 3");
                attr = mbsc.getAttribute(o, "TotalLoadedClassCount");
                t.stop();
                System.out.println("attr = " + attr);

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
                boolean updatedIsVerbose = (boolean) mbsc.getAttribute(o, "Verbose");
                t.stop();
                System.out.println("updated attr = " + attr);
                Asserts.assertTrue(isVerbose != updatedIsVerbose, "Verbose setting not changed: " + updatedIsVerbose);

                // Put it back: set Verbose to the opposite of what it now is.
                updatedAttribute = new Attribute("Verbose", (Boolean) !updatedIsVerbose);
                t = new Timer(id + " setAttribute (updating again, known to exist)");
                mbsc.setAttribute(o, updatedAttribute);
                t.stop();
                t = new Timer(id + " getAttribute again (updated attribute, known to exist)");
                updatedIsVerbose = (boolean) mbsc.getAttribute(o, "Verbose");
                t.stop();
                Asserts.assertTrue(isVerbose == updatedIsVerbose, "Verbose setting not changed back: " + updatedIsVerbose);


                // Set multiple Attributes
                // o = new ObjectName("java.lang:type=ClassLoading");// XXXXXXXXXX
                AttributeList attrsSet = new AttributeList();
                a1 = new Attribute("Verbose", (Boolean) true);
                a2 = new Attribute("Verbose", (Boolean) false);
                attrsSet.add(a1);
                attrsSet.add(a2);

                t = new Timer(id + " setAttributes ClassLoading (two, known)");
                try {
                    attrResults = mbsc.setAttributes(o,  attrsSet);
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    Asserts.fail("setAttributes: NOT expected: " + e);
                }
                t.stop();
                System.out.println("setAttributes, attrResults = " + attrResults);

                // Invoke
                MBeanInfo mbeanInfoThreading = null;
                o = new ObjectName("java.lang:type=Threading");
                t = new Timer(id + " getMBInfo (Threading)");
                mbeanInfoThreading = mbsc.getMBeanInfo(o);
                t.stop();

                // Invoke an unknown operation...
                Object invokeResult = null;
                t = new Timer(id + " invoke(Threading.notAKnownOperation()) 1");
                try {
                    invokeResult = mbsc.invoke(o, "notAKnownOperation", new Object[] { }, new String [] { });
                    Asserts.fail("invoke(unknown): NO expected exception on invoke");
                } catch (Exception e) {
                    System.out.println("Got expected: " + e);
                }
                t.stop();
                System.out.println("invokeResult = " + invokeResult);
                
                t = new Timer(id + " invoke(Threading.notAKnownOperation()) 2");
                try {
                    invokeResult = mbsc.invoke(o, "notAKnownOperation", new Object[] { }, new String [] { });
                    Asserts.fail("NO expected exception on invoke");
                } catch (Exception e) {
                    System.out.println("Got expected: " + e);
                }
                t.stop();
                System.out.println("invokeResult = " + invokeResult);
                
                // Invoke a known operation
                invokeResult = null;
                t = new Timer(id + " invoke(Threading.getThreadAllocatedBytes(long)) 1");
                invokeResult = mbsc.invoke(o, "getThreadAllocatedBytes",
                                           new Object[] { Thread.currentThread().getId() },
                                           new String [] { "long" }
                                          );
                t.stop();
                System.out.println("invokeResult = " + invokeResult);
                
                t = new Timer(id + " invoke(Threading.getThreadAllocateBytes(long)) 2");
                invokeResult = mbsc.invoke(o, "getThreadAllocatedBytes",
                                           new Object[] { Thread.currentThread().getId() },
                                           new String [] { "long" }
                                          );
                t.stop();
                System.out.println("invokeResult = " + invokeResult);
                    
                // invoke another...
                try {
                /*t = new Timer(id + " invoke(Threading.getThreadAllocatedBytes(long[])) 1");
                invokeResult = mbsc.invoke(o, "getThreadAllocatedBytes",
                                           new Object[] { new long [] { Thread.currentThread().getId() } },
                                           new String [] { "long[]" }
                                          );
                t.stop();
                System.out.println("invokeResult = " + invokeResult);
                
                t = new Timer(id + " invoke(Threading.getThreadAllocatedBytes(long[])) 2");
                invokeResult = mbsc.invoke(o, "getThreadAllocatedBytes",
                                           new Object[] { new long [] { Thread.currentThread().getId() } },
                                           new String [] { "long[]" }
                                          );
                t.stop();
                System.out.println("invokeResult = " + invokeResult); */
                    
                // invoke another...
                t = new Timer(id + " invoke(Threading.getThreadInfo(long)) 1");
                invokeResult = mbsc.invoke(o, "getThreadInfo",
                                           new Object[] { Thread.currentThread().getId() },
                                           new String [] { "long" }
                                          );
                t.stop();
                System.out.println("invokeResult class = " + invokeResult.getClass());
                System.out.println("invokeResult = " + invokeResult);
                
                t = new Timer(id + " invoke(Threading.getThreadInfo(long)) 2");
                invokeResult = mbsc.invoke(o, "getThreadInfo",
                                           new Object[] { Thread.currentThread().getId() },
                                           new String [] { "long" }
                                          );
                t.stop();
                System.out.println("invokeResult = " + invokeResult);

                // Using MBean Proxy:
                // java.lang:type=Threading == ThreadMXBean
                ThreadMXBean threadMXBean = null;
                t = new Timer(id + " newPlatformMXBeanProxy");
                threadMXBean = ManagementFactory.newPlatformMXBeanProxy(mbsc, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);
                t.stop();
                System.out.println("MXBeanProxy: threadMXBean = " + threadMXBean);

                t = new Timer(id + " MXBeanProxy.getThreadInfo");
                ThreadInfo threadInfo = threadMXBean.getThreadInfo(Thread.currentThread().getId());
                t.stop();
                System.out.println("threadInfo = " + threadInfo);
                    
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }

            System.out.println("Closing the client ...");
            t = new Timer(id + " close connection");
            conn.close();
            t.stop();

            System.out.println("Timer: " + id + " Cumulative time: " + Timer.getCumulativeTimeMillis() + " millis");

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
            static long cumulative = 0L;

            public Timer(String s) {
                this.s = s;
                t1 = System.nanoTime();
            }

            public void stop() {
                long t = System.nanoTime() - t1;
                cumulative += t;
                System.out.println("Timer: " + String.format("%-70s", s, 70) + ": " + ((double) t / 1000000) + " millis");
            }

            public static double getCumulativeTimeMillis() {
                return ((double) cumulative / 1000000);
            }
        }
}
