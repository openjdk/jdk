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

import com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.management.JMException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

/**
 * <p>
 * The <code>ScanDirAgent</code> is the Agent class for the <i>scandir</i>
 * application.
 * This class contains the {@link #main} method to start a standalone
 * <i>scandir</i> application.
 * </p>
 * <p>
 * The {@link #main main()} method simply registers a {@link
 * ScanManagerMXBean} in the platform MBeanServer - see {@link #init init},
 * and then waits for someone to call {@link ScanManagerMXBean#close close}
 * on that MBean.
 * </p>
 * <p>
 * When the {@link ScanManagerMXBean} state is switched to {@link
 * ScanManagerMXBean.ScanState#CLOSED CLOSED}, {@link #cleanup cleanup} is
 * called, the {@link ScanManagerMXBean} is unregistered, and the application
 * terminates (i.e. the main thread completes).
 * </p>
 * @author Sun Microsystems, 2006 - All rights reserved.
 **/
public class ScanDirAgent {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(ScanDirAgent.class.getName());

    // Proxy to the ScanManagerMXBean - created by init();
    //
    private volatile ScanManagerMXBean proxy = null;

    // A queue to put received Notifications.
    //
    private final BlockingQueue<Notification> queue;

    // A listener that will put notifications into the queue.
    //
    private final NotificationListener listener;

    /**
     * Creates a new instance of ScanDirAgent
     * You will need to call {@link #init()} later on in order to initialize
     * the application.
     * @see #main
     **/
    public ScanDirAgent() {
        // Initialize the notification queue
        queue = new LinkedBlockingQueue<Notification>();

        // Creates the listener.
        listener = new NotificationListener() {
            public void handleNotification(Notification notification,
                                           Object handback) {
                try {
                    // Just put the received notification in the queue.
                    // It will be consumed later on by 'waitForClose()'
                    //
                    LOG.finer("Queuing received notification "+notification);
                    queue.put(notification);
                } catch (InterruptedException ex) {
                    // OK
                }
            }
        };
    }

    /**
     * Initialize the application by registering a ScanManagerMXBean in
     * the platform MBeanServer
     * @throws java.io.IOException Registration failed for communication-related reasons.
     * @throws javax.management.JMException Registration failed for JMX-related reasons.
     */
    public void init() throws IOException, JMException {

        // Registers the ScanManagerMXBean singleton in the
        // platform MBeanServer
        //
        proxy = ScanManager.register();

        // Registers a NotificationListener with the ScanManagerMXBean in
        // order to receive state changed notifications.
        //
        ((NotificationEmitter)proxy).addNotificationListener(listener,null,null);
    }

    /**
     * Cleanup after close: unregister the ScanManagerMXBean singleton.
     * @throws java.io.IOException Cleanup failed for communication-related reasons.
     * @throws javax.management.JMException Cleanup failed for JMX-related reasons.
     */
    public void cleanup() throws IOException, JMException {
        try {
            ((NotificationEmitter)proxy).
                    removeNotificationListener(listener,null,null);
        } finally {
            ManagementFactory.getPlatformMBeanServer().
                unregisterMBean(ScanManager.SCAN_MANAGER_NAME);
        }
    }

    /**
     * Wait for someone to call 'close()' on the ScanManagerMXBean singleton.
     * Every time its state changes, the ScanManagerMXBean emits a notification.
     * We don't rely on the notification content (if we were using a remote
     * connection, we could miss some notifications) - we simply use the
     * state change notifications to react more quickly to state changes.
     * We do so simply by polling the {@link BlockingQueue} in which our
     * listener is pushing notifications, and checking the ScanManagerMXBean
     * state every time that a notification is received.
     * <p>
     * We can do so because we know that once the ScanManagerMXBean state is
     * switched to 'CLOSED', it will remain 'CLOSED' whatsoever. <br>
     * Therefore we don't need to concern ourselves with the possibility of
     * missing the window in which the ScanManagerMXBean state's will be
     * CLOSED, because that particular window stays opened forever.
     * <p>
     * Had we wanted to wait for 'RUNNING', we would have needed to apply
     * a different strategy - e.g. by taking into account the actual content
     * of the state changed notifications we received.
     * @throws java.io.IOException wait failed - a communication problem occurred.
     * @throws javax.management.JMException wait failed - the MBeanServer threw an exception.
     */
    public void waitForClose() throws IOException, JMException {

        // Wait until state is closed
        while(proxy.getState() != ScanState.CLOSED ) {
            try {
                // Wake up at least every 30 seconds - if we missed a
                // notification - we will at least get a chance to
                // call getState(). 30 seconds is obviously quite
                // arbitrary - if this were a real daemon - id'be tempted
                // to wait 30 minutes - knowing that any incoming
                // notification will wake me up anyway.
                // Note: we simply use the state change notifications to
                // react more quickly to state changes: see javadoc above.
                //
                queue.poll(30,TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                // OK
            }
        }
    }

    /**
     * The agent's main: {@link #init registers} a {@link ScanManagerMXBean},
     * {@link #waitForClose waits} until its state is {@link
     * ScanManagerMXBean.ScanState#CLOSED CLOSED}, {@link #cleanup cleanup}
     * and exits.
     * @param args the command line arguments - ignored
     * @throws java.io.IOException A communication problem occurred.
     * @throws javax.management.JMException A JMX problem occurred.
     */
    public static void main(String[] args)
        throws IOException, JMException {
        System.out.println("Initializing ScanManager...");
        final ScanDirAgent agent = new ScanDirAgent();
        agent.init();
        try {
            System.out.println("Waiting for ScanManager to close...");
            agent.waitForClose();
        } finally {
            System.out.println("Cleaning up...");
            agent.cleanup();
        }
    }
}
