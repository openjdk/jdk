/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.jmx.examples.scandir;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.management.InstanceNotFoundException;
import javax.management.Notification;
import junit.framework.*;
import static com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState.*;
import com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.logging.Logger;
import javax.management.AttributeChangeNotification;
import javax.management.JMException;
import javax.management.JMX;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import static com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState.*;

/**
 * Unit tests for {@code ScanManager}
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public class ScanManagerTest extends TestCase {

    public ScanManagerTest(String testName) {
        super(testName);
    }

    protected void setUp() throws Exception {
    }

    protected void tearDown() throws Exception {
    }

    public static Test suite() {
        TestSuite suite = new TestSuite(ScanManagerTest.class);

        return suite;
    }

    /**
     * Test of makeSingletonName method, of class com.sun.jmx.examples.scandir.ScanManager.
     */
    public void testMakeSingletonName() {
        System.out.println("makeSingletonName");

        Class clazz = ScanManagerMXBean.class;

        ObjectName expResult = ScanManager.SCAN_MANAGER_NAME;
        ObjectName result = ScanManager.makeSingletonName(clazz);
        assertEquals(expResult, result);

    }

    /**
     * Test of register method, of class com.sun.jmx.examples.scandir.ScanManager.
     */
    public void testRegister() throws Exception {
        System.out.println("register");

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();


        ScanManagerMXBean result = ScanManager.register(mbs);
        try {
            assertEquals(STOPPED,result.getState());
        } finally {
            try {
                mbs.unregisterMBean(ScanManager.SCAN_MANAGER_NAME);
            } catch (Exception x) {
                System.err.println("Failed to cleanup: "+x);
            }
        }

    }

    public interface Call {
        public void call() throws Exception;
        public void cancel() throws Exception;
    }

    /**
     * Test of addNotificationListener method, of class com.sun.jmx.examples.scandir.ScanManager.
     */
    public void testAddNotificationListener() throws Exception {
        System.out.println("addNotificationListener");

        final ScanManagerMXBean manager = ScanManager.register();
        final Call op = new Call() {
            public void call() throws Exception {
                manager.schedule(100000,0);
            }
            public void cancel() throws Exception {
                manager.stop();
            }
        };
        try {
            doTestOperation(manager,op,
                            EnumSet.of(RUNNING,SCHEDULED),
                            "schedule");
        } finally {
            try {
                ManagementFactory.getPlatformMBeanServer().
                        unregisterMBean(ScanManager.SCAN_MANAGER_NAME);
            } catch (Exception x) {
                System.err.println("Failed to cleanup: "+x);
            }
        }
    }

    /**
     * Test of addNotificationListener method, of class com.sun.jmx.examples.scandir.ScanManager.
     */
    private void doTestOperation(
            ScanManagerMXBean proxy,
            Call op,
            EnumSet<ScanState> after,
            String testName)
        throws Exception {
        System.out.println("doTestOperation: "+testName);

        final LinkedBlockingQueue<Notification> queue =
                new LinkedBlockingQueue<Notification>();

        NotificationListener listener = new NotificationListener() {
            public void handleNotification(Notification notification,
                        Object handback) {
                try {
                    queue.put(notification);
                } catch (Exception x) {
                    System.err.println("Failed to queue notif: "+x);
                }
            }
        };
        NotificationFilter filter = null;
        Object handback = null;
        final ScanState before;
        final NotificationEmitter emitter = (NotificationEmitter)proxy;
        emitter.addNotificationListener(listener, filter, handback);
        before = proxy.getState();
        op.call();
        try {
            final Notification notification =
                    queue.poll(3000,TimeUnit.MILLISECONDS);
            assertEquals(AttributeChangeNotification.ATTRIBUTE_CHANGE,
                    notification.getType());
            assertEquals(AttributeChangeNotification.class,
                    notification.getClass());
            assertEquals(ScanManager.SCAN_MANAGER_NAME,
                    notification.getSource());
            AttributeChangeNotification acn =
                    (AttributeChangeNotification)notification;
            assertEquals("State",acn.getAttributeName());
            assertEquals(ScanState.class.getName(),acn.getAttributeType());
            assertEquals(before,ScanState.valueOf((String)acn.getOldValue()));
            assertContained(after,ScanState.valueOf((String)acn.getNewValue()));
            emitter.removeNotificationListener(listener,filter,handback);
        } finally {
            try {
                op.cancel();
            } catch (Exception x) {
                System.err.println("Failed to cleanup: "+x);
            }
        }
    }

    /**
     * Test of preRegister method, of class com.sun.jmx.examples.scandir.ScanManager.
     */
    public void testPreRegister() throws Exception {
        System.out.println("preRegister");

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("DownUnder:type=Wombat");
        ScanManager instance = new ScanManager();

        ObjectName expResult = ScanManager.SCAN_MANAGER_NAME;
        ObjectName result;
        try {
            result = instance.preRegister(server, name);
            throw new RuntimeException("bad name accepted!");
        } catch (IllegalArgumentException x) {
            // OK!
            result = instance.preRegister(server, null);
        }
        assertEquals(expResult, result);
        result = instance.preRegister(server, ScanManager.SCAN_MANAGER_NAME);
        assertEquals(expResult, result);
    }


    /**
     * Test of getState method, of class com.sun.jmx.examples.scandir.ScanManager.
     */
    public void testGetState() throws IOException, InstanceNotFoundException {
        System.out.println("getState");

        ScanManager instance = new ScanManager();

        ScanState expResult = ScanState.STOPPED;
        ScanState result = instance.getState();
        assertEquals(expResult, result);
        instance.start();
        final ScanState afterStart = instance.getState();
        assertContained(EnumSet.of(RUNNING,SCHEDULED,COMPLETED),afterStart);
        instance.stop();
        assertEquals(STOPPED,instance.getState());
        instance.schedule(1000000L,1000000L);
        assertEquals(SCHEDULED,instance.getState());
        instance.stop();
        assertEquals(STOPPED,instance.getState());
    }

    /**
     * Test of schedule method, of class com.sun.jmx.examples.scandir.ScanManager.
     */
    public void testSchedule() throws Exception {
        System.out.println("schedule");

        final long delay = 10000L;
        final long interval = 10000L;

        final ScanManagerMXBean manager = ScanManager.register();
        final Call op = new Call() {
            public void call() throws Exception {
                manager.schedule(delay,interval);
                assertEquals(SCHEDULED,manager.getState());
            }
            public void cancel() throws Exception {
                manager.stop();
            }
        };
        try {
            doTestOperation(manager,op,EnumSet.of(SCHEDULED),
                    "schedule");
        } finally {
            try {
                ManagementFactory.getPlatformMBeanServer().
                        unregisterMBean(ScanManager.SCAN_MANAGER_NAME);
            } catch (Exception x) {
                System.err.println("Failed to cleanup: "+x);
            }
        }
    }

    public static void assertContained(EnumSet<ScanState> allowed,
            ScanState state) {
         final String msg = String.valueOf(state) + " is not one of " + allowed;
         assertTrue(msg,allowed.contains(state));
    }

    /**
     * Test of stop method, of class com.sun.jmx.examples.scandir.ScanManager.
     */
    public void testStop() throws Exception {
        System.out.println("stop");
        final ScanManagerMXBean manager = ScanManager.register();
        try {
            manager.schedule(1000000,0);
            assertContained(EnumSet.of(SCHEDULED),manager.getState());
            final Call op = new Call() {
                public void call() throws Exception {
                    manager.stop();
                    assertEquals(STOPPED,manager.getState());
                }
                public void cancel() throws Exception {
                    if (manager.getState() != STOPPED)
                        manager.stop();
                }
            };
            doTestOperation(manager,op,EnumSet.of(STOPPED),"stop");
        } finally {
            try {
                ManagementFactory.getPlatformMBeanServer().
                        unregisterMBean(ScanManager.SCAN_MANAGER_NAME);
            } catch (Exception x) {
                System.err.println("Failed to cleanup: "+x);
            }
        }
    }

    /**
     * Test of start method, of class com.sun.jmx.examples.scandir.ScanManager.
     */
    public void testStart() throws Exception {
        final ScanManagerMXBean manager = ScanManager.register();
        try {
            final Call op = new Call() {
                public void call() throws Exception {
                    assertEquals(STOPPED,manager.getState());
                    manager.start();
                    assertContained(EnumSet.of(RUNNING,SCHEDULED,COMPLETED),
                            manager.getState());
                }
                public void cancel() throws Exception {
                    manager.stop();
                }
            };
            doTestOperation(manager,op,EnumSet.of(RUNNING,SCHEDULED,COMPLETED),
                    "start");
        } finally {
            try {
                ManagementFactory.getPlatformMBeanServer().
                        unregisterMBean(ScanManager.SCAN_MANAGER_NAME);
            } catch (Exception x) {
                System.err.println("Failed to cleanup: "+x);
            }
        }
    }

}
