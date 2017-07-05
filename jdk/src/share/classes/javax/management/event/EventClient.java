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
import com.sun.jmx.event.LeaseRenewer;
import com.sun.jmx.event.ReceiverBuffer;
import com.sun.jmx.event.RepeatedSingletonJob;
import com.sun.jmx.namespace.JMXNamespaceUtils;
import com.sun.jmx.mbeanserver.PerThreadGroupPool;
import com.sun.jmx.remote.util.ClassLogger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.NotificationResult;
import javax.management.remote.TargetedNotification;

/**
 * <p>This class is used to manage its notification listeners on the client
 * side in the same way as on the MBean server side. This class needs to work
 * with an {@link EventClientDelegateMBean} on the server side.</p>
 *
 * <P>A user can specify an {@link EventRelay} object to specify how to receive
 * notifications forwarded by the {@link EventClientDelegateMBean}. By default,
 * the class {@link FetchingEventRelay} is used.</p>
 *
 * <p>A user can specify an {@link java.util.concurrent.Executor Executor}
 * to distribute notifications to local listeners. If no executor is
 * specified, the thread in the {@link EventRelay} which calls {@link
 * EventReceiver#receive EventReceiver.receive} will be reused to distribute
 * the notifications (in other words, to call the {@link
 * NotificationListener#handleNotification handleNotification} method of the
 * appropriate listeners). It is useful to make a separate thread do this
 * distribution in some cases. For example, if network communication is slow,
 * the forwarding thread can concentrate on communication while, locally,
 * the distributing thread distributes the received notifications. Another
 * usage is to share a thread pool between many clients, for scalability.
 * Note, though, that if the {@code Executor} can create more than one thread
 * then it is possible that listeners will see notifications in a different
 * order from the order in which they were sent.</p>
 *
 * <p>An object of this class sends notifications to listeners added with
 * {@link #addEventClientListener}.  The {@linkplain Notification#getType()
 * type} of each such notification is one of {@link #FAILED}, {@link #NONFATAL},
 * or {@link #NOTIFS_LOST}.</p>
 *
 * @since JMX 2.0
 */
public class EventClient implements EventConsumer, NotificationManager {

    /**
     * <p>A notification string type used by an {@code EventClient} object
     * to inform a listener added by {@link #addEventClientListener} that
     * it failed to get notifications from a remote server, and that it is
     * possible that no more notifications will be delivered.</p>
     *
     * @see #addEventClientListener
     * @see EventReceiver#failed
     */
    public static final String FAILED = "jmx.event.service.failed";

    /**
     * <p>Reports that an unexpected exception has been received by the {@link
     * EventRelay} object but that it is non-fatal. For example, a notification
     * received is not serializable or its class is not found.</p>
     *
     * @see #addEventClientListener
     * @see EventReceiver#nonFatal
     */
    public static final String NONFATAL = "jmx.event.service.nonfatal";

    /**
     * <p>A notification string type used by an {@code EventClient} object
     * to inform a listener added by {@link #addEventClientListener
     * addEventClientListener} that it has detected that notifications have
     * been lost.  The {@link Notification#getUserData() userData} of the
     * notification is a Long which is an upper bound on the number of lost
     * notifications that have just been detected.</p>
     *
     * @see #addEventClientListener
     */
    public static final String NOTIFS_LOST = "jmx.event.service.notifs.lost";

    /**
     * The default lease time, {@value}, in milliseconds.
     *
     * @see EventClientDelegateMBean#lease
     */
    public static final long DEFAULT_LEASE_TIMEOUT = 300000;

    /**
     * <p>Constructs a default {@code EventClient} object.</p>
     *
     * <p>This object creates a {@link FetchingEventRelay} object to
     * receive notifications forwarded by the {@link EventClientDelegateMBean}.
     * The {@link EventClientDelegateMBean} that it works with is the
     * one registered with the {@linkplain EventClientDelegate#OBJECT_NAME
     * default ObjectName}.  The thread from the {@link FetchingEventRelay}
     * object that fetches the notifications is also used to distribute them.
     *
     * @param conn An {@link MBeanServerConnection} object used to communicate
     * with an {@link EventClientDelegateMBean} MBean.
     *
     * @throws IllegalArgumentException If {@code conn} is null.
     * @throws IOException If an I/O error occurs when communicating with the
     * {@code EventClientDelegateMBean}.
     */
    public EventClient(MBeanServerConnection conn) throws IOException {
        this(EventClientDelegate.getProxy(conn));
    }

    /**
     * Constructs an {@code EventClient} object with a specified
     * {@link EventClientDelegateMBean}.
     *
     * <p>This object creates a {@link FetchingEventRelay} object to receive
     * notifications forwarded by the {@link EventClientDelegateMBean}.  The
     * thread from the {@link FetchingEventRelay} object that fetches the
     * notifications is also used to distribute them.
     *
     * @param delegate An {@link EventClientDelegateMBean} object to work with.
     *
     * @throws IllegalArgumentException If {@code delegate} is null.
     * @throws IOException If an I/O error occurs when communicating with the
     * the {@link EventClientDelegateMBean}.
     */
    public EventClient(EventClientDelegateMBean delegate)
    throws IOException {
        this(delegate, null, null, null, DEFAULT_LEASE_TIMEOUT);
    }

    /**
     * Constructs an {@code EventClient} object with the specified
     * {@link EventClientDelegateMBean}, {@link EventRelay}
     * object, and distributing thread.
     *
     * @param delegate An {@link EventClientDelegateMBean} object to work with.
     * Usually, this will be a proxy constructed using
     * {@link EventClientDelegate#getProxy}.
     * @param eventRelay An object used to receive notifications
     * forwarded by the {@link EventClientDelegateMBean}. If {@code null}, a
     * {@link FetchingEventRelay} object will be used.
     * @param distributingExecutor Used to distribute notifications to local
     * listeners. If {@code null}, the thread that calls {@link
     * EventReceiver#receive EventReceiver.receive} from the {@link EventRelay}
     * object is used.
     * @param leaseScheduler An object that will be used to schedule the
     * periodic {@linkplain EventClientDelegateMBean#lease lease updates}.
     * If {@code null}, a default scheduler will be used.
     * @param requestedLeaseTime The lease time used to keep this client alive
     * in the {@link EventClientDelegateMBean}.  A value of zero is equivalent
     * to the {@linkplain #DEFAULT_LEASE_TIMEOUT default value}.
     *
     * @throws IllegalArgumentException If {@code delegate} is null.
     * @throws IOException If an I/O error occurs when communicating with the
     * {@link EventClientDelegateMBean}.
     */
    public EventClient(EventClientDelegateMBean delegate,
            EventRelay eventRelay,
            Executor distributingExecutor,
            ScheduledExecutorService leaseScheduler,
            long requestedLeaseTime)
            throws IOException {
        if (delegate == null) {
            throw new IllegalArgumentException("Null EventClientDelegateMBean");
        }

        if (requestedLeaseTime == 0)
            requestedLeaseTime = DEFAULT_LEASE_TIMEOUT;
        else if (requestedLeaseTime < 0) {
            throw new IllegalArgumentException(
                    "Negative lease time: " + requestedLeaseTime);
        }

        eventClientDelegate = delegate;

        if (eventRelay != null) {
            this.eventRelay = eventRelay;
        } else {
            try {
                this.eventRelay = new FetchingEventRelay(delegate);
            } catch (IOException ioe) {
                throw ioe;
            } catch (Exception e) {
                // impossible?
                final IOException ioee = new IOException(e.toString());
                ioee.initCause(e);
                throw ioee;
            }
        }

        if (distributingExecutor == null)
            distributingExecutor = callerExecutor;
        this.distributingExecutor = distributingExecutor;
        this.dispatchingJob = new DispatchingJob();

        clientId = this.eventRelay.getClientId();

        this.requestedLeaseTime = requestedLeaseTime;
        if (leaseScheduler == null)
            leaseScheduler = defaultLeaseScheduler();
        leaseRenewer = new LeaseRenewer(leaseScheduler, renewLease);

        if (logger.traceOn()) {
            logger.trace("init", "New EventClient: "+clientId);
        }
    }

    private static ScheduledExecutorService defaultLeaseScheduler() {
        // The default lease scheduler uses a ScheduledThreadPoolExecutor
        // with a maximum of 20 threads.  This means that if you have many
        // EventClient instances and some of them get blocked (because of an
        // unresponsive network, for example), then even the instances that
        // are connected to responsive servers may have their leases expire.
        // XXX check if the above is true and possibly fix.
        PerThreadGroupPool.Create<ScheduledThreadPoolExecutor> create =
                new PerThreadGroupPool.Create<ScheduledThreadPoolExecutor>() {
            public ScheduledThreadPoolExecutor createThreadPool(ThreadGroup group) {
                ThreadFactory daemonThreadFactory = new DaemonThreadFactory(
                        "JMX EventClient lease renewer %d");
                ScheduledThreadPoolExecutor executor =
                        new ScheduledThreadPoolExecutor(20, daemonThreadFactory);
                executor.setKeepAliveTime(1, TimeUnit.SECONDS);
                executor.allowCoreThreadTimeOut(true);
                executor.setRemoveOnCancelPolicy(true);
                // By default, a ScheduledThreadPoolExecutor will keep jobs
                // in its queue even after they have been cancelled.  They
                // will only be removed when their scheduled time arrives.
                // Since the job references the LeaseRenewer which references
                // this EventClient, this can lead to a moderately large number
                // of objects remaining referenced until the renewal time
                // arrives.  Hence the above call, which removes the job from
                // the queue as soon as it is cancelled.
                return executor;
            }
        };
        return leaseRenewerThreadPool.getThreadPoolExecutor(create);

    }

    /**
     * <p>Closes this EventClient, removes all listeners and stops receiving
     * notifications.</p>
     *
     * <p>This method calls {@link
     * EventClientDelegateMBean#removeClient(String)} and {@link
     * EventRelay#stop}.  Both operations occur even if one of them
     * throws an {@code IOException}.
     *
     * @throws IOException if an I/O error occurs when communicating with
     * {@link EventClientDelegateMBean}, or if {@link EventRelay#stop}
     * throws an {@code IOException}.
     */
    public void close() throws IOException {
        if (logger.traceOn()) {
            logger.trace("close", clientId);
        }

        synchronized(listenerInfoMap) {
            if (closed) {
                return;
            }

            closed = true;
            listenerInfoMap.clear();
        }

        if (leaseRenewer != null)
            leaseRenewer.close();

        IOException ioe = null;
        try {
            eventRelay.stop();
        } catch (IOException e) {
            ioe = e;
            logger.debug("close", "EventRelay.stop", e);
        }

        try {
            eventClientDelegate.removeClient(clientId);
        } catch (Exception e) {
            if (e instanceof IOException)
                ioe = (IOException) e;
            else
                ioe = new IOException(e);
            logger.debug("close",
                    "Got exception when removing "+clientId, e);
        }

        if (ioe != null)
            throw ioe;
    }

    /**
     * <p>Determine if this {@code EventClient} is closed.</p>
     *
     * @return True if the {@code EventClient} is closed.
     */
    public boolean closed() {
        return closed;
    }

    /**
     * <p>Return the {@link EventRelay} associated with this
     * {@code EventClient}.</p>
     *
     * @return The {@link EventRelay} object used.
     */
    public EventRelay getEventRelay() {
        return eventRelay;
    }

    /**
     * <p>Return the lease time that this {@code EventClient} requests
     * on every lease renewal.</p>
     *
     * @return The requested lease time.
     *
     * @see EventClientDelegateMBean#lease
     */
    public long getRequestedLeaseTime() {
        return requestedLeaseTime;
    }

    /**
     * @see javax.management.MBeanServerConnection#addNotificationListener(
     * ObjectName, NotificationListener, NotificationFilter, Object).
     */
    public void addNotificationListener(ObjectName name,
            NotificationListener listener,
            NotificationFilter filter,
            Object handback)
            throws InstanceNotFoundException, IOException {
        if (logger.traceOn()) {
            logger.trace("addNotificationListener", "");
        }

        checkState();

        Integer listenerId;
        try {
            listenerId =
                    eventClientDelegate.addListener(clientId, name, filter);
        } catch (EventClientNotFoundException ecnfe) {
            final IOException ioe = new IOException(ecnfe.getMessage());
            ioe.initCause(ecnfe);
            throw ioe;
        }

        synchronized(listenerInfoMap) {
            listenerInfoMap.put(listenerId,  new ListenerInfo(
                    name,
                    listener,
                    filter,
                    handback,
                    false));
        }

        startListening();
    }

    /**
     * @see javax.management.MBeanServerConnection#removeNotificationListener(
     * ObjectName, NotificationListener).
     */
    public void removeNotificationListener(ObjectName name,
            NotificationListener listener)
            throws InstanceNotFoundException,
            ListenerNotFoundException,
            IOException {
        if (logger.traceOn()) {
            logger.trace("removeNotificationListener", "");
        }
        checkState();

        for (Integer id : getListenerInfo(name, listener, false)) {
            removeListener(id);
        }
    }

    /**
     * @see javax.management.MBeanServerConnection#removeNotificationListener(
     * ObjectName, NotificationListener, NotificationFilter, Object).
     */
    public void removeNotificationListener(ObjectName name,
            NotificationListener listener,
            NotificationFilter filter,
            Object handback)
            throws InstanceNotFoundException,
            ListenerNotFoundException,
            IOException {
        if (logger.traceOn()) {
            logger.trace("removeNotificationListener", "with all arguments.");
        }
        checkState();
        final Integer listenerId =
                getListenerInfo(name, listener, filter, handback, false);

        removeListener(listenerId);
    }

    /**
     * @see javax.management.event.EventConsumer#unsubscribe(
     * ObjectName, NotificationListener).
     */
    public void unsubscribe(ObjectName name,
            NotificationListener listener)
            throws ListenerNotFoundException, IOException {
        if (logger.traceOn()) {
            logger.trace("unsubscribe", "");
        }
        checkState();
        final Integer listenerId =
                getMatchedListenerInfo(name, listener, true);

        synchronized(listenerInfoMap) {
            if (listenerInfoMap.remove(listenerId) == null) {
                throw new ListenerNotFoundException();
            }
        }

        stopListening();

        try {
            eventClientDelegate.removeListenerOrSubscriber(clientId, listenerId);
        } catch (InstanceNotFoundException e) {
            logger.trace("unsubscribe", "removeSubscriber", e);
        } catch (EventClientNotFoundException cnfe) {
            logger.trace("unsubscribe", "removeSubscriber", cnfe);
        }
    }

    /**
     * @see javax.management.event.EventConsumer#subscribe(
     * ObjectName, NotificationListener, NotificationFilter, Object).
     */
    public void subscribe(ObjectName name,
            NotificationListener listener,
            NotificationFilter filter,
            Object handback) throws IOException {
        if (logger.traceOn()) {
            logger.trace("subscribe", "");
        }

        checkState();

        Integer listenerId;
        try {
            listenerId =
                    eventClientDelegate.addSubscriber(clientId, name, filter);
        } catch (EventClientNotFoundException ecnfe) {
            final IOException ioe = new IOException(ecnfe.getMessage());
            ioe.initCause(ecnfe);
            throw ioe;
        }

        synchronized(listenerInfoMap) {
            listenerInfoMap.put(listenerId,  new ListenerInfo(
                    name,
                    listener,
                    filter,
                    handback,
                    true));
        }

        startListening();
    }

    /**
     * <p>Adds a set of listeners to the remote MBeanServer.  This method can
     * be used to copy the listeners from one {@code EventClient} to another.</p>
     *
     * <p>A listener is represented by a {@link ListenerInfo} object. The listener
     * is added by calling {@link #subscribe(ObjectName,
     * NotificationListener, NotificationFilter, Object)} if the method
     * {@link ListenerInfo#isSubscription() isSubscription}
     * returns {@code true}; otherwise it is added by calling
     * {@link #addNotificationListener(ObjectName, NotificationListener,
     * NotificationFilter, Object)}.</p>
     *
     * <P>The method returns the listeners which were added successfully. The
     * elements in the returned collection are a subset of the elements in
     * {@code infoList}. If all listeners were added successfully, the two
     * collections are the same. If no listener was added successfully, the
     * returned collection is empty.</p>
     *
     * @param listeners the listeners to add.
     *
     * @return The listeners that were added successfully.
     *
     * @throws IOException If an I/O error occurs.
     *
     * @see #getListeners()
     */
    public Collection<ListenerInfo> addListeners(Collection<ListenerInfo> listeners)
    throws IOException {
        if (logger.traceOn()) {
            logger.trace("addListeners", "");
        }

        checkState();

        if (listeners == null || listeners.isEmpty())
            return Collections.emptySet();

        final List<ListenerInfo> list = new ArrayList<ListenerInfo>();
        for (ListenerInfo l : listeners) {
            try {
                if (l.isSubscription()) {
                    subscribe(l.getObjectName(),
                            l.getListener(),
                            l.getFilter(),
                            l.getHandback());
                } else {
                    addNotificationListener(l.getObjectName(),
                            l.getListener(),
                            l.getFilter(),
                            l.getHandback());
                }

                list.add(l);
            } catch (Exception e) {
                if (logger.traceOn()) {
                    logger.trace("addListeners", "failed to add: "+l, e);
                }
            }
        }

        return list;
    }

    /**
     * <p>Returns the collection of listeners that have been added through
     * this {@code EventClient} and not subsequently removed.  The returned
     * collection contains one entry for every listener added with
     * {@link #addNotificationListener addNotificationListener} or
     * {@link #subscribe subscribe} and not subsequently removed with
     * {@link #removeNotificationListener removeNotificationListener} or
     * {@link #unsubscribe unsubscribe}, respectively.</p>
     *
     * @return A collection of listener information. Empty if there are no
     * current listeners or if this {@code EventClient} has been {@linkplain
     * #close closed}.
     *
     * @see #addListeners
     */
    public Collection<ListenerInfo> getListeners() {
        if (logger.traceOn()) {
            logger.trace("getListeners", "");
        }

        synchronized(listenerInfoMap) {
            return Collections.unmodifiableCollection(listenerInfoMap.values());
        }
    }

    /**
     * Adds a listener to receive the {@code EventClient} notifications specified in
     * {@link #getEventClientNotificationInfo}.
     *
     * @param listener A listener to receive {@code EventClient} notifications.
     * @param filter A filter to select which notifications are to be delivered
     * to the listener, or {@code null} if all notifications are to be delivered.
     * @param handback An object to be given to the listener along with each
     * notification. Can be null.
     * @throws NullPointerException If listener is null.
     * @see #removeEventClientListener
     */
    public void addEventClientListener(NotificationListener listener,
            NotificationFilter filter,
            Object handback) {
        if (logger.traceOn()) {
            logger.trace("addEventClientListener", "");
        }
        broadcaster.addNotificationListener(listener, filter, handback);
    }

    /**
     * Removes a listener added to receive {@code EventClient} notifications specified in
     * {@link #getEventClientNotificationInfo}.
     *
     * @param listener A listener to receive {@code EventClient} notifications.
     * @throws NullPointerException If listener is null.
     * @throws ListenerNotFoundException If the listener is not added to
     * this {@code EventClient}.
     */
    public void removeEventClientListener(NotificationListener listener)
    throws ListenerNotFoundException {
        if (logger.traceOn()) {
            logger.trace("removeEventClientListener", "");
        }
        broadcaster.removeNotificationListener(listener);
    }

    /**
     * <p>Get the types of notification that an {@code EventClient} can send
     * to listeners added with {@link #addEventClientListener
     * addEventClientListener}.</p>
     *
     * @return Types of notification emitted by this {@code EventClient}.
     *
     * @see #FAILED
     * @see #NONFATAL
     * @see #NOTIFS_LOST
     */
    public MBeanNotificationInfo[] getEventClientNotificationInfo() {
        return myInfo.clone();
    }

    private static boolean match(ListenerInfo li,
            ObjectName name,
            NotificationListener listener,
            boolean subscribed) {
        return li.getObjectName().equals(name) &&
                li.getListener() == listener &&
                li.isSubscription() == subscribed;
    }

    private static boolean match(ListenerInfo li,
            ObjectName name,
            NotificationListener listener,
            NotificationFilter filter,
            Object handback,
            boolean subscribed) {
        return li.getObjectName().equals(name) &&
                li.getFilter() == filter &&
                li.getListener() == listener &&
                li.getHandback() == handback &&
                li.isSubscription() == subscribed;
    }

// ---------------------------------------------------
// private classes
// ---------------------------------------------------
    private class DispatchingJob extends RepeatedSingletonJob {
        public DispatchingJob() {
            super(distributingExecutor);
        }

        public boolean isSuspended() {
            return closed || buffer.size() == 0;
        }

        public void task() {
            TargetedNotification[] tns ;
            int lost = 0;

            synchronized(buffer) {
                tns = buffer.removeNotifs();
                lost = buffer.removeLost();
            }

            if ((tns == null || tns.length == 0)
            && lost == 0) {
                return;
            }

            // forwarding
            if (tns != null && tns.length > 0) {
                if (logger.traceOn()) {
                    logger.trace("DispatchingJob-task",
                            "Forwarding: "+tns.length);
                }
                for (TargetedNotification tn : tns) {
                    final ListenerInfo li = listenerInfoMap.get(tn.getListenerID());
                    try {
                        li.getListener().handleNotification(tn.getNotification(),
                                li.getHandback());
                    } catch (Exception e) {
                        logger.fine(
                                "DispatchingJob.task", "listener got exception", e);
                    }
                }
            }

            if (lost > 0) {
                if (logger.traceOn()) {
                    logger.trace("DispatchingJob-task",
                            "lost: "+lost);
                }
                final Notification n = new Notification(NOTIFS_LOST,
                        EventClient.this,
                        myNotifCounter.getAndIncrement(),
                        System.currentTimeMillis(),
                        "Lost notifications.");
                n.setUserData(new Long(lost));
                broadcaster.sendNotification(n);
            }
        }
    }


    private class EventReceiverImpl implements EventReceiver {
        public void receive(NotificationResult nr) {
            if (logger.traceOn()) {
                logger.trace("MyEventReceiver-receive", "");
            }

            synchronized(buffer) {
                buffer.addNotifs(nr);

                dispatchingJob.resume();
            }
        }

        public void failed(Throwable t) {
            if (logger.traceOn()) {
                logger.trace("MyEventReceiver-failed", "", t);
            }
            final Notification n = new Notification(FAILED,
                    this,
                    myNotifCounter.getAndIncrement(),
                    System.currentTimeMillis());
            n.setSource(t);
            broadcaster.sendNotification(n);
        }

        public void nonFatal(Exception e) {
            if (logger.traceOn()) {
                logger.trace("MyEventReceiver-nonFatal", "", e);
            }

            final Notification n = new Notification(NONFATAL,
                    this,
                    myNotifCounter.getAndIncrement(),
                    System.currentTimeMillis());
            n.setSource(e);
            broadcaster.sendNotification(n);
        }
    }

// ----------------------------------------------------
// private class
// ----------------------------------------------------


// ----------------------------------------------------
// private methods
// ----------------------------------------------------
    private Integer getListenerInfo(ObjectName name,
            NotificationListener listener,
            NotificationFilter filter,
            Object handback,
            boolean subscribed) throws ListenerNotFoundException {

        synchronized(listenerInfoMap) {
            for (Map.Entry<Integer, ListenerInfo> entry :
                    listenerInfoMap.entrySet()) {
                ListenerInfo li = entry.getValue();
                if (match(li, name, listener, filter, handback, subscribed)) {
                    return entry.getKey();
                }
            }
        }

        throw new ListenerNotFoundException();
    }

    private Integer getMatchedListenerInfo(ObjectName name,
            NotificationListener listener,
            boolean subscribed) throws ListenerNotFoundException {

        synchronized(listenerInfoMap) {
            for (Map.Entry<Integer, ListenerInfo> entry :
                    listenerInfoMap.entrySet()) {
                ListenerInfo li = entry.getValue();
                if (li.getObjectName().equals(name) &&
                        li.getListener() == listener &&
                        li.isSubscription() == subscribed) {
                    return entry.getKey();
                }
            }
        }

        throw new ListenerNotFoundException();
    }

    private Collection<Integer> getListenerInfo(ObjectName name,
            NotificationListener listener,
            boolean subscribed) throws ListenerNotFoundException {

        final ArrayList<Integer> ids = new ArrayList<Integer>();
        synchronized(listenerInfoMap) {
            for (Map.Entry<Integer, ListenerInfo> entry :
                    listenerInfoMap.entrySet()) {
                ListenerInfo li = entry.getValue();
                if (match(li, name, listener, subscribed)) {
                    ids.add(entry.getKey());
                }
            }
        }

        if (ids.isEmpty()) {
            throw new ListenerNotFoundException();
        }

        return ids;
    }

    private void checkState() throws IOException {
        synchronized(listenerInfoMap) {
            if (closed) {
                throw new IOException("Ended!");
            }
        }
    }

    private void startListening() throws IOException {
        synchronized(listenerInfoMap) {
            if (!startedListening && listenerInfoMap.size() > 0) {
                eventRelay.setEventReceiver(myReceiver);
            }

            startedListening = true;

            if (logger.traceOn()) {
                logger.trace("startListening", "listening");
            }
        }
    }

    private void stopListening() throws IOException {
        synchronized(listenerInfoMap) {
            if (listenerInfoMap.size() == 0 && startedListening) {
                eventRelay.setEventReceiver(null);

                startedListening = false;

                if (logger.traceOn()) {
                    logger.trace("stopListening", "non listening");
                }
            }
        }
    }

    private void removeListener(Integer id)
    throws InstanceNotFoundException,
            ListenerNotFoundException,
            IOException {
        synchronized(listenerInfoMap) {
            if (listenerInfoMap.remove(id) == null) {
                throw new ListenerNotFoundException();
            }

            stopListening();
        }

        try {
            eventClientDelegate.removeListenerOrSubscriber(clientId, id);
        } catch (EventClientNotFoundException cnfe) {
            logger.trace("removeListener", "ecd.removeListener", cnfe);
        }
    }


// ----------------------------------------------------
// private variables
// ----------------------------------------------------
    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "EventClient");

    private final Executor distributingExecutor;
    private final EventClientDelegateMBean eventClientDelegate;
    private final EventRelay eventRelay;
    private volatile String clientId = null;
    private final long requestedLeaseTime;

    private final ReceiverBuffer buffer = new ReceiverBuffer();

    private final EventReceiverImpl myReceiver =
            new EventReceiverImpl();
    private final DispatchingJob dispatchingJob;

    private final HashMap<Integer, ListenerInfo> listenerInfoMap =
            new HashMap<Integer, ListenerInfo>();

    private volatile boolean closed = false;

    private volatile boolean startedListening = false;

    // Could change synchronization here. But at worst a race will mean
    // sequence numbers are not contiguous, which may not matter much.
    private final AtomicLong myNotifCounter = new AtomicLong();

    private final static MBeanNotificationInfo[] myInfo =
            new MBeanNotificationInfo[] {
        new MBeanNotificationInfo(
                new String[] {FAILED, NONFATAL, NOTIFS_LOST},
                Notification.class.getName(),
                "Notifications that can be sent to a listener added with " +
                "EventClient.addEventClientListener")};

    private final NotificationBroadcasterSupport broadcaster =
            new NotificationBroadcasterSupport();

    private final static Executor callerExecutor = new Executor() {
        // DirectExecutor using caller thread
        public void execute(Runnable r) {
            r.run();
        }
    };

    private static void checkInit(final MBeanServerConnection conn,
            final ObjectName delegateName)
            throws IOException {
        if (conn == null) {
            throw new IllegalArgumentException("No connection specified");
        }
        if (delegateName != null &&
                (!conn.isRegistered(delegateName))) {
            throw new IllegalArgumentException(
                    delegateName +
                    ": not found");
        }
        if (delegateName == null &&
                (!conn.isRegistered(
                EventClientDelegate.OBJECT_NAME))) {
            throw new IllegalArgumentException(
                    EventClientDelegate.OBJECT_NAME +
                    ": not found");
        }
    }

// ----------------------------------------------------
// private event lease issues
// ----------------------------------------------------
    private Callable<Long> renewLease = new Callable<Long>() {
        public Long call() throws IOException, EventClientNotFoundException {
            return eventClientDelegate.lease(clientId, requestedLeaseTime);
        }
    };

    private final LeaseRenewer leaseRenewer;

// ------------------------------------------------------------------------
    /**
     * Constructs an {@code MBeanServerConnection} that uses an {@code EventClient} object,
     * if the underlying connection has an {@link EventClientDelegateMBean}.
     * <P> The {@code EventClient} object creates a default
     * {@link FetchingEventRelay} object to
     * receive notifications forwarded by the {@link EventClientDelegateMBean}.
     * The {@link EventClientDelegateMBean} it works with is the
     * default one registered with the ObjectName
     * {@link EventClientDelegate#OBJECT_NAME
     * OBJECT_NAME}.
     * The thread from the {@link FetchingEventRelay} object that fetches the
     * notifications is also used to distribute them.
     *
     * @param conn An {@link MBeanServerConnection} object used to communicate
     * with an {@link EventClientDelegateMBean}.
     * @throws IllegalArgumentException If the value of {@code conn} is null,
     *         or the default {@link EventClientDelegateMBean} is not registered.
     * @throws IOException If an I/O error occurs.
     */
    public static MBeanServerConnection getEventClientConnection(
            final MBeanServerConnection conn)
            throws IOException {
        return getEventClientConnection(conn, null);
    }

    /**
     * Constructs an MBeanServerConnection that uses an {@code EventClient}
     * object with a user-specific {@link EventRelay}
     * object.
     * <P>
     * The {@link EventClientDelegateMBean} which it works with is the
     * default one registered with the ObjectName
     * {@link EventClientDelegate#OBJECT_NAME
     * OBJECT_NAME}
     * The thread that calls {@link EventReceiver#receive
     * EventReceiver.receive} from the {@link EventRelay} object is used
     * to distribute notifications to their listeners.
     *
     * @param conn An {@link MBeanServerConnection} object used to communicate
     * with an {@link EventClientDelegateMBean}.
     * @param eventRelay A user-specific object used to receive notifications
     * forwarded by the {@link EventClientDelegateMBean}. If null, the default
     * {@link FetchingEventRelay} object is used.
     * @throws IllegalArgumentException If the value of {@code conn} is null,
     *         or the default {@link EventClientDelegateMBean} is not registered.
     * @throws IOException If an I/O error occurs.
     */
    public static MBeanServerConnection getEventClientConnection(
            final MBeanServerConnection conn,
            final EventRelay eventRelay)
            throws IOException {

        if (newEventConn == null) {
            throw new IllegalArgumentException(
                    "Class not found: EventClientConnection");
        }

        checkInit(conn,null);
        final Callable<EventClient> factory = new Callable<EventClient>() {
            final public EventClient call() throws Exception {
                EventClientDelegateMBean ecd = EventClientDelegate.getProxy(conn);
                return new EventClient(ecd, eventRelay, null, null,
                        DEFAULT_LEASE_TIMEOUT);
            }
        };

        try {
            return (MBeanServerConnection)newEventConn.invoke(null,
                    conn, factory);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Method newEventConn = null;
    static {
        try {
            Class<?> c = Class.forName(
                    "com.sun.jmx.remote.util.EventClientConnection",
                    false, Thread.currentThread().getContextClassLoader());
            newEventConn = c.getMethod("getEventConnectionFor",
                    MBeanServerConnection.class, Callable.class);
        } catch (Exception e) {
            // OK: we're running in a subset of our classes
        }
    }

    /**
     * <p>Get the client id of this {@code EventClient} in the
     * {@link EventClientDelegateMBean}.
     *
     * @return the client id.
     *
     * @see EventClientDelegateMBean#addClient(String, Object[], String[])
     * EventClientDelegateMBean.addClient
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Returns a JMX Connector that will use an {@link EventClient}
     * to subscribe for notifications. If the server doesn't have
     * an {@link EventClientDelegateMBean}, then the connector will
     * use the legacy notification mechanism instead.
     *
     * @param wrapped The underlying JMX Connector wrapped by the returned
     *               connector.
     *
     * @return A JMX Connector that will uses an {@link EventClient}, if
     *         available.
     *
     * @see EventClient#getEventClientConnection(MBeanServerConnection)
     */
    public static JMXConnector withEventClient(final JMXConnector wrapped) {
        return JMXNamespaceUtils.withEventClient(wrapped);
    }

    private static final PerThreadGroupPool<ScheduledThreadPoolExecutor>
            leaseRenewerThreadPool = PerThreadGroupPool.make();
}
