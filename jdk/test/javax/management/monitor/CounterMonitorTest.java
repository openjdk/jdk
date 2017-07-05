/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4981829
 * @summary Test that the counter monitor, when running in difference mode,
 *          emits a notification every time the threshold is exceeded.
 * @author Luis-Miguel Alventosa
 * @run clean CounterMonitorTest
 * @run build CounterMonitorTest
 * @run main CounterMonitorTest
 */

import javax.management.*;
import javax.management.monitor.*;

public class CounterMonitorTest implements NotificationListener {

    // threshold number
    private Number threshold = new Integer(2);

    // modulus number
    private Number modulus = new Integer(7);

    // offset number
    private int offset = 0;

    // difference mode flag
    private boolean differenceModeFlag = true;

    // notify flag
    private boolean notifyFlag = true;

    // granularity period
    private int granularityperiod = 500;

    // counter values
    private int[] values = new int[] {4, 6, 9, 11};

    // time to wait for notification (in seconds)
    private int timeout = 5;

    // flag to notify that a message has been received
    private volatile boolean messageReceived = false;

    // MBean class
    public class StdObservedObject implements StdObservedObjectMBean {
        public Object getNbObjects() {
            return count;
        }
        public void setNbObjects(Object n) {
            count = n;
        }
        private Object count= null;
    }

    // MBean interface
    public interface StdObservedObjectMBean {
        public Object getNbObjects();
        public void setNbObjects(Object n);
    }

    // Notification handler
    public void handleNotification(Notification notification,
                                   Object handback) {
        MonitorNotification n = (MonitorNotification) notification;
        echo("\tInside handleNotification...");
        String type = n.getType();
        try {
            if (type.equals(MonitorNotification.THRESHOLD_VALUE_EXCEEDED)) {
                echo("\t\t" + n.getObservedAttribute() +
                     " has reached or exceeded the threshold");
                echo("\t\tDerived Gauge = " + n.getDerivedGauge());
                messageReceived = true;
                synchronized (this) {
                    notifyAll();
                }
            } else {
                echo("\t\tSkipping notification of type: " + type);
            }
        } catch (Exception e) {
            echo("\tError in handleNotification!");
            e.printStackTrace(System.out);
        }
    }

    /**
     * Update the counter and check for notifications
     */
    public void thresholdNotification() throws Exception {

        CounterMonitor counterMonitor = new CounterMonitor();
        try {
            MBeanServer server = MBeanServerFactory.newMBeanServer();

            String domain = server.getDefaultDomain();

            // Create a new CounterMonitor MBean and add it to the MBeanServer.
            //
            echo(">>> CREATE a new CounterMonitor MBean");
            ObjectName counterMonitorName = new ObjectName(
                            domain + ":type=" + CounterMonitor.class.getName());
            server.registerMBean(counterMonitor, counterMonitorName);

            echo(">>> ADD a listener to the CounterMonitor");
            counterMonitor.addNotificationListener(this, null, null);

            //
            // MANAGEMENT OF A STANDARD MBEAN
            //

            echo(">>> CREATE a new StdObservedObject MBean");

            ObjectName stdObsObjName =
                new ObjectName(domain + ":type=StdObservedObject");
            StdObservedObject stdObsObj = new StdObservedObject();
            server.registerMBean(stdObsObj, stdObsObjName);

            echo(">>> SET the attributes of the CounterMonitor:");

            counterMonitor.addObservedObject(stdObsObjName);
            echo("\tATTRIBUTE \"ObservedObject\"    = " + stdObsObjName);

            counterMonitor.setObservedAttribute("NbObjects");
            echo("\tATTRIBUTE \"ObservedAttribute\" = NbObjects");

            counterMonitor.setNotify(notifyFlag);
            echo("\tATTRIBUTE \"Notify\"            = " + notifyFlag);

            counterMonitor.setInitThreshold(threshold);
            echo("\tATTRIBUTE \"Threshold\"         = " + threshold);

            counterMonitor.setGranularityPeriod(granularityperiod);
            echo("\tATTRIBUTE \"GranularityPeriod\" = " + granularityperiod);

            counterMonitor.setModulus(modulus);
            echo("\tATTRIBUTE \"Modulus\"           = " + modulus);

            counterMonitor.setDifferenceMode(differenceModeFlag);
            echo("\tATTRIBUTE \"DifferenceMode\"    = " + differenceModeFlag);

            echo(">>> START the CounterMonitor");
            counterMonitor.start();

            // Set initial value
            //
            Integer data = new Integer(0);
            echo(">>> Set data = " + data.intValue());

            Attribute attrib = new Attribute("NbObjects", data);
            server.setAttribute(stdObsObjName, attrib);

            // Wait for granularity period (multiplied by 2 for sure)
            //
            Thread.sleep(granularityperiod * 2);

            // Loop through the values
            //
            for (int i = 0; i < values.length; i++) {
                data = new Integer(values[i]);
                echo(">>> Set data = " + data.intValue());

                attrib = new Attribute("NbObjects", data);
                server.setAttribute(stdObsObjName, attrib);

                echo("\tdoWait in Counter Monitor");
                doWait();

                // Check if notification was received
                //
                if (messageReceived) {
                    echo("\tOKAY: Notification received");
                } else {
                    echo("\tError: notification missed or not emitted");
                    throw new IllegalStateException("Notification lost");
                }
                messageReceived = false;
            }
        } finally {
            counterMonitor.stop();
        }

        echo(">>> Bye! Bye!");
    }

    /*
     * Wait until timeout reached
     */
    void doWait() {
        for (int i = 0; i < timeout; i++) {
            echo("\tdoWait: Waiting for " + timeout + " seconds. " +
                 "i = " + i + ", messageReceived = " + messageReceived);
            if (messageReceived) {
                break;
            }
            try {
                synchronized (this) {
                    wait(1000);
                }
            } catch (InterruptedException e) {
                // OK: Ignore...
            }
        }
    }

    /*
     * Print message
     */
    void echo(String message) {
        System.out.println(message);
    }

    /*
     * Standalone entry point.
     *
     * Run the test and report to stdout.
     */
    public static void main (String args[]) throws Exception {
        CounterMonitorTest test = new CounterMonitorTest();
        test.thresholdNotification();
    }
}
