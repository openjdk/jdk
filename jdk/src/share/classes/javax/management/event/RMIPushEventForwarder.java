/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package javax.management.event;

import com.sun.jmx.event.DaemonThreadFactory;
import com.sun.jmx.event.RepeatedSingletonJob;
import com.sun.jmx.remote.util.ClassLogger;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.management.Notification;
import javax.management.remote.NotificationResult;
import javax.management.remote.TargetedNotification;


/**
 * This class is used by {@link RMIPushEventRelay}. When
 * {@link RMIPushEventRelay} calls {@link
 * EventClientDelegateMBean#addClient(String, Object[], String[])} to get a new
 * client identifier, it uses this class name as the
 * first argument to ask {@code EventClientDelegateMBean} to create an object of
 * this class.
 * Then {@code EventClientDelegateMBean} forwards client notifications
 * to this object. This object then continues forwarding the notifications
 * to the {@code RMIPushEventRelay}.
 */
public class RMIPushEventForwarder implements EventForwarder {
    private static final int DEFAULT_BUFFER_SIZE = 6000;

    /**
     * Creates a new instance of {@code RMIPushEventForwarder}.
     *
     * @param receiver An RMI stub exported to receive notifications
     * from this object for its {@link RMIPushEventRelay}.
     *
     * @param bufferSize The maximum number of notifications to store
     * while waiting for the last remote send to complete.
     */
    public RMIPushEventForwarder(RMIPushServer receiver, int bufferSize) {
        if (logger.traceOn()) {
            logger.trace("RMIEventForwarder", "new one");
        }

        if (bufferSize < 0) {
            throw new IllegalArgumentException(
                    "Negative buffer size: " + bufferSize);
        } else if (bufferSize == 0)
            bufferSize = DEFAULT_BUFFER_SIZE;

        if (receiver == null) {
            throw new NullPointerException();
        }

        this.receiver = receiver;
        this.buffer = new ArrayBlockingQueue<TargetedNotification>(bufferSize);
    }

    public void forward(Notification n, Integer listenerId) {
        if (logger.traceOn()) {
            logger.trace("forward", "to the listener: "+listenerId);
        }
        synchronized(sendingJob) {
            TargetedNotification tn = new TargetedNotification(n, listenerId);
            while (!buffer.offer(tn)) {
                buffer.remove();
                passed++;
            }
            sendingJob.resume();
        }
    }

    public void close() {
        if (logger.traceOn()) {
            logger.trace("close", "called");
        }

        synchronized(sendingJob) {
            ended = true;
            buffer.clear();
        }
    }

    public void setClientId(String clientId) {
        if (logger.traceOn()) {
            logger.trace("setClientId", clientId);
        }
    }

    private class SendingJob extends RepeatedSingletonJob {
        public SendingJob() {
            super(executor);
        }

        public boolean isSuspended() {
            return ended || buffer.isEmpty();
        }

        public void task() {
            final long earliest = passed;

            List<TargetedNotification> tns =
                    new ArrayList<TargetedNotification>(buffer.size());
            synchronized(sendingJob) {
                buffer.drainTo(tns);
                passed += tns.size();
            }

            if (logger.traceOn()) {
                logger.trace("SendingJob-task", "sending: "+tns.size());
            }

            if (!tns.isEmpty()) {
                try {
                    TargetedNotification[] tnArray =
                            new TargetedNotification[tns.size()];
                    tns.toArray(tnArray);
                    receiver.receive(new NotificationResult(earliest, passed, tnArray));
                } catch (RemoteException e) {
                    if (logger.debugOn()) {
                        logger.debug("SendingJob-task",
                                "Got exception to forward notifs.", e);
                    }

                    long currentLost = passed - earliest;
                    if (FetchingEventRelay.isSerialOrClassNotFound(e)) {
                        // send one by one
                        long tmpPassed = earliest;
                        for (TargetedNotification tn : tns) {
                            try {
                                receiver.receive(new NotificationResult(earliest,
                                        ++tmpPassed, new TargetedNotification[]{tn}));
                            } catch (RemoteException ioee) {
                                logger.trace(
                                        "SendingJob-task", "send to remote", ioee);
                                // sends nonFatal notifs?
                            }
                        }

                        currentLost = passed - tmpPassed;
                    }

                    if (currentLost > 0) { // inform of the lost.
                        try {
                            receiver.receive(new NotificationResult(
                                    passed, passed,
                                    new TargetedNotification[]{}));
                        } catch (RemoteException ee) {
                            logger.trace(
                                    "SendingJob-task", "receiver.receive", ee);
                        }
                    }
                }
            }
        }
    }

    private long passed = 0;

    private static final ExecutorService executor =
            Executors.newCachedThreadPool(
            new DaemonThreadFactory("JMX RMIEventForwarder Executor"));
    private final SendingJob sendingJob = new SendingJob();

    private final BlockingQueue<TargetedNotification> buffer;

    private final RMIPushServer receiver;
    private boolean ended = false;

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "RMIEventForwarder");
}
