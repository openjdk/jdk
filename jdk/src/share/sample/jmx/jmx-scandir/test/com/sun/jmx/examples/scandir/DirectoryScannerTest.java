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

package com.sun.jmx.examples.scandir;

import com.sun.jmx.examples.scandir.config.DirectoryScannerConfig;
import com.sun.jmx.examples.scandir.config.ResultRecord;
import com.sun.jmx.examples.scandir.config.ScanManagerConfig;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import junit.framework.*;
import com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState;
import static com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState.*;
import com.sun.jmx.examples.scandir.ScanManagerTest.Call;
import java.util.EnumSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.management.AttributeChangeNotification;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

import static com.sun.jmx.examples.scandir.ScanManagerTest.*;
import static com.sun.jmx.examples.scandir.TestUtils.*;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Unit tests for {@code DirectoryScanner}
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public class DirectoryScannerTest extends TestCase {

    public DirectoryScannerTest(String testName) {
        super(testName);
    }

    protected void setUp() throws Exception {
    }

    protected void tearDown() throws Exception {
    }

    public static Test suite() {
        TestSuite suite = new TestSuite(DirectoryScannerTest.class);

        return suite;
    }

    private void doTestOperation(
            DirectoryScannerMXBean proxy,
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
        final NotificationEmitter emitter = (NotificationEmitter)
                makeNotificationEmitter(proxy,DirectoryScannerMXBean.class);
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
            assertEquals(getObjectName(proxy),
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
     * Test of getRootDirectory method, of class com.sun.jmx.examples.scandir.DirectoryScanner.
     */
    public void testGetRootDirectory() throws Exception {
        System.out.println("getRootDirectory");

       final ScanManagerMXBean manager = ScanManager.register();
        try {
            final String tmpdir = System.getProperty("java.io.tmpdir");
            final ScanDirConfigMXBean config = manager.getConfigurationMBean();
            System.err.println("Configuration MXBean is: " + config);
            final DirectoryScannerConfig bean =
                    config.addDirectoryScanner("test",tmpdir,".*",0,0);
            final String root = bean.getRootDirectory();
            if (root == null)
                throw new NullPointerException("bean.getRootDirectory()");
            if (config.getConfiguration().getScan("test").getRootDirectory() == null)
                throw new NullPointerException("config.getConfig().getScan(\"test\").getRootDirectory()");
            manager.applyConfiguration(true);
            final DirectoryScannerMXBean proxy =
                    manager.getDirectoryScanners().get("test");
            final File tmpFile =  new File(tmpdir);
            final File rootFile = new File(proxy.getRootDirectory());
            assertEquals(tmpFile,rootFile);
        } catch (Exception x) {
            x.printStackTrace();
            throw x;
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
     * Test of scan method, of class com.sun.jmx.examples.scandir.DirectoryScanner.
     */
    public void testScan() throws Exception {
        System.out.println("scan");

        final ScanManagerMXBean manager = ScanManager.register();
        try {
            final String tmpdir = System.getProperty("java.io.tmpdir");
            final ScanDirConfigMXBean config = manager.getConfigurationMBean();
            final DirectoryScannerConfig bean =
                    config.addDirectoryScanner("test1",tmpdir,".*",0,0);
            config.addDirectoryScanner("test2",tmpdir,".*",0,0);
            config.addDirectoryScanner("test3",tmpdir,".*",0,0);
            manager.applyConfiguration(true);
            final DirectoryScannerMXBean proxy =
                    manager.getDirectoryScanners().get("test1");
            final Call op = new Call() {
                public void call() throws Exception {
                    final BlockingQueue<Notification> queue =
                            new LinkedBlockingQueue<Notification>();
                    final NotificationListener listener = new NotificationListener() {
                        public void handleNotification(Notification notification,
                                Object handback) {
                            try {
                               queue.put(notification);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    manager.start();
                    while(true) {
                        final Notification n = queue.poll(10,TimeUnit.SECONDS);
                        if (n == null) break;
                        final AttributeChangeNotification at =
                                (AttributeChangeNotification) n;
                        if (RUNNING == ScanState.valueOf((String)at.getNewValue()))
                            break;
                        else {
                            System.err.println("New state: "+(String)at.getNewValue()
                            +" isn't "+RUNNING);
                        }
                    }
                    assertContained(EnumSet.of(SCHEDULED,RUNNING,COMPLETED),
                            proxy.getState());
                }
                public void cancel() throws Exception {
                    manager.stop();
                }
            };
            doTestOperation(proxy,op,
                    EnumSet.of(RUNNING,SCHEDULED,COMPLETED),
                    "scan");
        } catch (Exception x) {
            x.printStackTrace();
            throw x;
        } finally {
            try {
                manager.stop();
            } catch (Exception x) {
                System.err.println("Failed to stop: "+x);
            }
            try {
                ManagementFactory.getPlatformMBeanServer().
                        unregisterMBean(ScanManager.SCAN_MANAGER_NAME);
            } catch (Exception x) {
                System.err.println("Failed to cleanup: "+x);
            }
        }
    }

    /**
     * Test of getState method, of class com.sun.jmx.examples.scandir.DirectoryScanner.
     */
    public void testGetState() {
        System.out.println("getState");

        final DirectoryScannerConfig bean =
                new DirectoryScannerConfig("test");
        bean.setRootDirectory(System.getProperty("java.io.tmpdir"));
        final ResultLogManager log = new ResultLogManager();
        DirectoryScanner instance =
                new DirectoryScanner(bean,log);

        ScanState expResult = STOPPED;
        ScanState result = instance.getState();
        assertEquals(STOPPED, result);
        instance.scan();
        result = instance.getState();
        assertEquals(COMPLETED, result);
    }

    /**
     * Test of addNotificationListener method, of class com.sun.jmx.examples.scandir.DirectoryScanner.
     */
    public void testAddNotificationListener() throws Exception {
        System.out.println("addNotificationListener");

        final ScanManagerMXBean manager = ScanManager.register();
        final Call op = new Call() {
            public void call() throws Exception {
                manager.start();
            }
            public void cancel() throws Exception {
                manager.stop();
            }
        };
        try {
            final String tmpdir = System.getProperty("java.io.tmpdir");
            final ScanDirConfigMXBean config = manager.getConfigurationMBean();
            final DirectoryScannerConfig bean =
                    config.addDirectoryScanner("test1",tmpdir,".*",0,0);
            manager.applyConfiguration(true);
            final DirectoryScannerMXBean proxy =
                    manager.getDirectoryScanners().get("test1");
           doTestOperation(proxy,op,
                            EnumSet.of(RUNNING,SCHEDULED),
                            "scan");
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
