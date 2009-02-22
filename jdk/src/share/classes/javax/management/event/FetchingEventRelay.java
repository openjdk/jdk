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
import java.io.IOException;
import java.io.NotSerializableException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanException;
import javax.management.remote.NotificationResult;

/**
 * <p>This class is an implementation of the {@link EventRelay} interface. It calls
 * {@link EventClientDelegateMBean#fetchNotifications
 * fetchNotifications(String, long, int, long)} to get
 * notifications and then forwards them to an {@link EventReceiver} object.</p>
 *
 * <p>A {@code fetchExecutor} parameter can be specified when creating a
 * {@code FetchingEventRelay}.  That is then the {@code Executor} that will
 * be used to perform the {@code fetchNotifications} operation.  Only one
 * job at a time will be submitted to this {@code Executor}.  The behavior
 * is unspecified if {@link Executor#execute} throws an exception, including
 * {@link java.util.concurrent.RejectedExecutionException
 * RejectedExecutionException}.
 *
 * @since JMX 2.0
 */
public class FetchingEventRelay implements EventRelay {
    /**
     * The default buffer size: {@value #DEFAULT_BUFFER_SIZE}.
     */
    public final static int DEFAULT_BUFFER_SIZE = 1000;

    /**
     * The default waiting timeout: {@value #DEFAULT_WAITING_TIMEOUT}
     * in millseconds when fetching notifications from
     * an {@code EventClientDelegateMBean}.
     */
    public final static long DEFAULT_WAITING_TIMEOUT = 60000;

    /**
     * The default maximum notifications to fetch every time:
     * {@value #DEFAULT_MAX_NOTIFICATIONS}.
     */
    public final static int DEFAULT_MAX_NOTIFICATIONS = DEFAULT_BUFFER_SIZE;

    /**
     * Constructs a default {@code FetchingEventRelay} object by using the default
     * configuration: {@code DEFAULT_BUFFER_SIZE}, {@code DEFAULT_WAITING_TIMEOUT}
     * {@code DEFAULT_MAX_NOTIFICATIONS}. A single thread is created
     * to do fetching.
     *
     * @param delegate The {@code EventClientDelegateMBean} to work with.
     * @throws IOException If failed to work with the {@code delegate}.
     * @throws MBeanException if unable to add a client to the remote
     * {@code EventClientDelegateMBean} (see {@link
     * EventClientDelegateMBean#addClient(String, Object[], String[])
     * EventClientDelegateMBean.addClient}).
     * @throws IllegalArgumentException If {@code delegate} is {@code null}.
     */
    public FetchingEventRelay(EventClientDelegateMBean delegate)
    throws IOException, MBeanException {
        this(delegate, null);
    }

    /**
     * Constructs a {@code FetchingEventRelay} object by using the default
     * configuration: {@code DEFAULT_BUFFER_SIZE}, {@code DEFAULT_WAITING_TIMEOUT}
     * {@code DEFAULT_MAX_NOTIFICATIONS}, with a user-specific executor to do
     * the fetching.
     *
     * @param delegate The {@code EventClientDelegateMBean} to work with.
     * @param fetchExecutor Used to do the fetching. A new thread is created if
     * {@code null}.
     * @throws IOException If failed to work with the {@code delegate}.
     * @throws MBeanException if unable to add a client to the remote
     * {@code EventClientDelegateMBean} (see {@link
     * EventClientDelegateMBean#addClient(String, Object[], String[])
     * EventClientDelegateMBean.addClient}).
     * @throws IllegalArgumentException If {@code delegate} is {@code null}.
     */
    public FetchingEventRelay(EventClientDelegateMBean delegate,
            Executor fetchExecutor) throws IOException, MBeanException {
        this(delegate,
                DEFAULT_BUFFER_SIZE,
                DEFAULT_WAITING_TIMEOUT,
                DEFAULT_MAX_NOTIFICATIONS,
                fetchExecutor);
    }

    /**
     * Constructs a {@code FetchingEventRelay} object with user-specific
     * configuration and executor to fetch notifications via the
     * {@link EventClientDelegateMBean}.
     *
     * @param delegate The {@code EventClientDelegateMBean} to work with.
     * @param bufferSize The buffer size for saving notifications in
     * {@link EventClientDelegateMBean} before they are fetched.
     * @param timeout The waiting time in millseconds when fetching
     * notifications from an {@code EventClientDelegateMBean}.
     * @param maxNotifs The maximum notifications to fetch every time.
     * @param fetchExecutor Used to do the fetching. A new thread is created if
     * {@code null}.
     * @throws IOException if failed to communicate with the {@code delegate}.
     * @throws MBeanException if unable to add a client to the remote
     * {@code EventClientDelegateMBean} (see {@link
     * EventClientDelegateMBean#addClient(String, Object[], String[])
     * EventClientDelegateMBean.addClient}).
     * @throws IllegalArgumentException If {@code delegate} is {@code null}.
     */
    public FetchingEventRelay(EventClientDelegateMBean delegate,
            int bufferSize,
            long timeout,
            int maxNotifs,
            Executor fetchExecutor) throws IOException, MBeanException {
        this(delegate,
                bufferSize,
                timeout,
                maxNotifs,
                fetchExecutor,
                FetchingEventForwarder.class.getName(),
                new Object[] {bufferSize},
                new String[] {int.class.getName()});
    }

    /**
     * Constructs a {@code FetchingEventRelay} object with user-specific
     * configuration and executor to fetch notifications via the
     * {@link EventClientDelegateMBean}.
     *
     * @param delegate The {@code EventClientDelegateMBean} to work with.
     * @param bufferSize The buffer size for saving notifications in
     * {@link EventClientDelegateMBean} before they are fetched.
     * @param timeout The waiting time in millseconds when fetching
     * notifications from an {@code EventClientDelegateMBean}.
     * @param maxNotifs The maximum notifications to fetch every time.
     * @param fetchExecutor Used to do the fetching.
     * @param forwarderName the class name of a user specific EventForwarder
     * to create in server to forward notifications to this object. The class
     * should be a subclass of the class {@link FetchingEventForwarder}.
     * @param params the parameters passed to create {@code forwarderName}
     * @param sig the signature of the {@code params}
     * @throws IOException if failed to communicate with the {@code delegate}.
     * @throws MBeanException if unable to add a client to the remote
     * {@code EventClientDelegateMBean} (see {@link
     * EventClientDelegateMBean#addClient(String, Object[], String[])
     * EventClientDelegateMBean.addClient}).
     * @throws IllegalArgumentException if {@code bufferSize} or
     * {@code maxNotifs} is less than {@code 1}
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    public FetchingEventRelay(EventClientDelegateMBean delegate,
            int bufferSize,
            long timeout,
            int maxNotifs,
            Executor fetchExecutor,
            String forwarderName,
            Object[] params,
            String[] sig) throws IOException, MBeanException {

        if (logger.traceOn()) {
            logger.trace("FetchingEventRelay", "delegateMBean "+
                    bufferSize+" "+
                    timeout+" "+
                    maxNotifs+" "+
                    fetchExecutor+" "+
                    forwarderName+" ");
        }

        if (delegate == null) {
            throw new NullPointerException("Null EventClientDelegateMBean!");
        }


        if (bufferSize<=1) {
            throw new IllegalArgumentException(
                    "The bufferSize cannot be less than 1, no meaning.");
        }

        if (maxNotifs<=1) {
            throw new IllegalArgumentException(
                    "The maxNotifs cannot be less than 1, no meaning.");
        }

        clientId = delegate.addClient(
                forwarderName,
                params,
                sig);

        this.delegate = delegate;
        this.timeout = timeout;
        this.maxNotifs = maxNotifs;

        if (fetchExecutor == null) {
            ScheduledThreadPoolExecutor executor =
                    new ScheduledThreadPoolExecutor(1, daemonThreadFactory);
            executor.setKeepAliveTime(1, TimeUnit.SECONDS);
            executor.allowCoreThreadTimeOut(true);
            fetchExecutor = executor;
            this.defaultExecutor = executor;
        } else
            this.defaultExecutor = null;
        this.fetchExecutor = fetchExecutor;

        startSequenceNumber = 0;
        fetchingJob = new MyJob();
    }

    public synchronized void setEventReceiver(EventReceiver eventReceiver) {
        if (logger.traceOn()) {
            logger.trace("setEventReceiver", ""+eventReceiver);
        }

        EventReceiver old = this.eventReceiver;
        this.eventReceiver = eventReceiver;
        if (old == null && eventReceiver != null)
            fetchingJob.resume();
    }

    public String getClientId() {
        return clientId;
    }

    public synchronized void stop() {
        if (logger.traceOn()) {
            logger.trace("stop", "");
        }
        if (stopped) {
            return;
        }

        stopped = true;
        clientId = null;
        if (defaultExecutor != null)
            defaultExecutor.shutdown();
    }

    private class MyJob extends RepeatedSingletonJob {
        public MyJob() {
            super(fetchExecutor);
        }

        public boolean isSuspended() {
            boolean b;
            synchronized(FetchingEventRelay.this) {
                b = stopped ||
                        (eventReceiver == null) ||
                        (clientId == null);
            }

            if (logger.traceOn()) {
                logger.trace("-MyJob-isSuspended", ""+b);
            }
            return b;
        }

        public void task() {
            logger.trace("MyJob-task", "");
            long fetchTimeout = timeout;
            NotificationResult nr = null;
            Throwable failedExcep = null;
            try {
                nr = delegate.fetchNotifications(
                        clientId,
                        startSequenceNumber,
                        maxNotifs,
                        fetchTimeout);
            } catch (Exception e) {
                if (isSerialOrClassNotFound(e)) {
                    try {
                        nr = fetchOne();
                    } catch (Exception ee) {
                        failedExcep = e;
                    }
                } else {
                    failedExcep = e;
                }
            }

            if (failedExcep != null &&
                    !isSuspended()) {
                logger.fine("MyJob-task",
                        "Failed to fetch notification, stopping...", failedExcep);
                try {
                    eventReceiver.failed(failedExcep);
                } catch (Exception e) {
                    logger.trace(
                            "MyJob-task", "exception from eventReceiver.failed", e);
                }

                stop();
            } else if (nr != null) {
                try {
                    eventReceiver.receive(nr);
                } catch (RuntimeException e) {
                    logger.trace(
                            "MyJob-task",
                            "exception delivering notifs to EventClient", e);
                } finally {
                    startSequenceNumber = nr.getNextSequenceNumber();
                }
            }
        }
    }

    private NotificationResult fetchOne() throws Exception {
        logger.trace("fetchOne", "");

        while (true) {
            try {
                // 1 notif to skip possible missing class
                return delegate.fetchNotifications(
                        clientId,
                        startSequenceNumber,
                        1,
                        timeout);
            } catch (Exception e) {
                if (isSerialOrClassNotFound(e)) { // skip and continue
                    if (logger.traceOn()) {
                        logger.trace("fetchOne", "Ignore", e);
                    }
                    eventReceiver.nonFatal(e);
                    startSequenceNumber++;
                } else {
                    throw e;
                }
            }
        }
    }

    static boolean isSerialOrClassNotFound(Exception e) {
        Throwable cause = e.getCause();

        while (cause != null &&
                !(cause instanceof ClassNotFoundException) &&
                !(cause instanceof NotSerializableException)) {
            cause = cause.getCause();
        }

        return (cause instanceof ClassNotFoundException ||
                cause instanceof NotSerializableException);
    }

    private long startSequenceNumber = 0;
    private EventReceiver eventReceiver = null;
    private final EventClientDelegateMBean delegate;
    private String clientId;
    private boolean stopped = false;

    private final Executor fetchExecutor;
    private final ExecutorService defaultExecutor;
    private final MyJob fetchingJob;

    private final long timeout;
    private final int maxNotifs;

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event",
            "FetchingEventRelay");
    private static final ThreadFactory daemonThreadFactory =
                    new DaemonThreadFactory("JMX FetchingEventRelay executor %d");
}
