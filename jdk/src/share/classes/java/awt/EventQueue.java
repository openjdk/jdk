/*
 * Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.awt;

import java.awt.event.*;

import java.awt.peer.ComponentPeer;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.EmptyStackException;
import sun.util.logging.PlatformLogger;

import sun.awt.AppContext;
import sun.awt.AWTAutoShutdown;
import sun.awt.PeerEvent;
import sun.awt.SunToolkit;
import sun.awt.EventQueueItem;
import sun.awt.AWTAccessor;

/**
 * <code>EventQueue</code> is a platform-independent class
 * that queues events, both from the underlying peer classes
 * and from trusted application classes.
 * <p>
 * It encapsulates asynchronous event dispatch machinery which
 * extracts events from the queue and dispatches them by calling
 * {@link #dispatchEvent(AWTEvent) dispatchEvent(AWTEvent)} method
 * on this <code>EventQueue</code> with the event to be dispatched
 * as an argument.  The particular behavior of this machinery is
 * implementation-dependent.  The only requirements are that events
 * which were actually enqueued to this queue (note that events
 * being posted to the <code>EventQueue</code> can be coalesced)
 * are dispatched:
 * <dl>
 *   <dt> Sequentially.
 *   <dd> That is, it is not permitted that several events from
 *        this queue are dispatched simultaneously.
 *   <dt> In the same order as they are enqueued.
 *   <dd> That is, if <code>AWTEvent</code>&nbsp;A is enqueued
 *        to the <code>EventQueue</code> before
 *        <code>AWTEvent</code>&nbsp;B then event B will not be
 *        dispatched before event A.
 * </dl>
 * <p>
 * Some browsers partition applets in different code bases into
 * separate contexts, and establish walls between these contexts.
 * In such a scenario, there will be one <code>EventQueue</code>
 * per context. Other browsers place all applets into the same
 * context, implying that there will be only a single, global
 * <code>EventQueue</code> for all applets. This behavior is
 * implementation-dependent.  Consult your browser's documentation
 * for more information.
 * <p>
 * For information on the threading issues of the event dispatch
 * machinery, see <a href="doc-files/AWTThreadIssues.html#Autoshutdown"
 * >AWT Threading Issues</a>.
 *
 * @author Thomas Ball
 * @author Fred Ecks
 * @author David Mendenhall
 *
 * @since       1.1
 */
public class EventQueue {

    // From Thread.java
    private static int threadInitNumber;
    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }

    private static final int LOW_PRIORITY = 0;
    private static final int NORM_PRIORITY = 1;
    private static final int HIGH_PRIORITY = 2;
    private static final int ULTIMATE_PRIORITY = 3;

    private static final int NUM_PRIORITIES = ULTIMATE_PRIORITY + 1;

    /*
     * We maintain one Queue for each priority that the EventQueue supports.
     * That is, the EventQueue object is actually implemented as
     * NUM_PRIORITIES queues and all Events on a particular internal Queue
     * have identical priority. Events are pulled off the EventQueue starting
     * with the Queue of highest priority. We progress in decreasing order
     * across all Queues.
     */
    private Queue[] queues = new Queue[NUM_PRIORITIES];

    /*
     * The next EventQueue on the stack, or null if this EventQueue is
     * on the top of the stack.  If nextQueue is non-null, requests to post
     * an event are forwarded to nextQueue.
     */
    private EventQueue nextQueue;

    /*
     * The previous EventQueue on the stack, or null if this is the
     * "base" EventQueue.
     */
    private EventQueue previousQueue;

    private EventDispatchThread dispatchThread;

    private final ThreadGroup threadGroup =
        Thread.currentThread().getThreadGroup();
    private final ClassLoader classLoader =
        Thread.currentThread().getContextClassLoader();

    /*
     * The time stamp of the last dispatched InputEvent or ActionEvent.
     */
    private long mostRecentEventTime = System.currentTimeMillis();

    /**
     * The modifiers field of the current event, if the current event is an
     * InputEvent or ActionEvent.
     */
    private WeakReference currentEvent;

    /*
     * Non-zero if a thread is waiting in getNextEvent(int) for an event of
     * a particular ID to be posted to the queue.
     */
    private int waitForID;

    private final String name = "AWT-EventQueue-" + nextThreadNum();

    private static final PlatformLogger eventLog = PlatformLogger.getLogger("java.awt.event.EventQueue");

    static {
        AWTAccessor.setEventQueueAccessor(
            new AWTAccessor.EventQueueAccessor() {
                public EventQueue getNextQueue(EventQueue eventQueue) {
                    return eventQueue.nextQueue;
                }
                public Thread getDispatchThread(EventQueue eventQueue) {
                    return eventQueue.dispatchThread;
                }
            });
    }

    public EventQueue() {
        for (int i = 0; i < NUM_PRIORITIES; i++) {
            queues[i] = new Queue();
        }
        /*
         * NOTE: if you ever have to start the associated event dispatch
         * thread at this point, be aware of the following problem:
         * If this EventQueue instance is created in
         * SunToolkit.createNewAppContext() the started dispatch thread
         * may call AppContext.getAppContext() before createNewAppContext()
         * completes thus causing mess in thread group to appcontext mapping.
         */
    }

    /**
     * Posts a 1.1-style event to the <code>EventQueue</code>.
     * If there is an existing event on the queue with the same ID
     * and event source, the source <code>Component</code>'s
     * <code>coalesceEvents</code> method will be called.
     *
     * @param theEvent an instance of <code>java.awt.AWTEvent</code>,
     *          or a subclass of it
     * @throws NullPointerException if <code>theEvent</code> is <code>null</code>
     */
    public void postEvent(AWTEvent theEvent) {
        SunToolkit.flushPendingEvents();
        postEventPrivate(theEvent);
    }

    /**
     * Posts a 1.1-style event to the <code>EventQueue</code>.
     * If there is an existing event on the queue with the same ID
     * and event source, the source <code>Component</code>'s
     * <code>coalesceEvents</code> method will be called.
     *
     * @param theEvent an instance of <code>java.awt.AWTEvent</code>,
     *          or a subclass of it
     */
    final void postEventPrivate(AWTEvent theEvent) {
        theEvent.isPosted = true;
        synchronized(this) {
            if (dispatchThread == null && nextQueue == null) {
                if (theEvent.getSource() == AWTAutoShutdown.getInstance()) {
                    return;
                } else {
                    initDispatchThread();
                }
            }
            if (nextQueue != null) {
                // Forward event to top of EventQueue stack.
                nextQueue.postEventPrivate(theEvent);
                return;
            }
            postEvent(theEvent, getPriority(theEvent));
        }
    }

    private static int getPriority(AWTEvent theEvent) {
        if (theEvent instanceof PeerEvent &&
            (((PeerEvent)theEvent).getFlags() &
                PeerEvent.ULTIMATE_PRIORITY_EVENT) != 0)
        {
            return ULTIMATE_PRIORITY;
        }

        if (theEvent instanceof PeerEvent &&
            (((PeerEvent)theEvent).getFlags() &
                PeerEvent.PRIORITY_EVENT) != 0)
        {
            return HIGH_PRIORITY;
        }

        if (theEvent instanceof PeerEvent &&
            (((PeerEvent)theEvent).getFlags() &
                PeerEvent.LOW_PRIORITY_EVENT) != 0)
        {
            return LOW_PRIORITY;
        }

        int id = theEvent.getID();
        if (id == PaintEvent.PAINT || id == PaintEvent.UPDATE) {
            return LOW_PRIORITY;
        }
        return NORM_PRIORITY;
    }

    /**
     * Posts the event to the internal Queue of specified priority,
     * coalescing as appropriate.
     *
     * @param theEvent an instance of <code>java.awt.AWTEvent</code>,
     *          or a subclass of it
     * @param priority  the desired priority of the event
     */
    private void postEvent(AWTEvent theEvent, int priority) {
        if (coalesceEvent(theEvent, priority)) {
            return;
        }

        EventQueueItem newItem = new EventQueueItem(theEvent);

        cacheEQItem(newItem);

        boolean notifyID = (theEvent.getID() == this.waitForID);

        if (queues[priority].head == null) {
            boolean shouldNotify = noEvents();
            queues[priority].head = queues[priority].tail = newItem;

            if (shouldNotify) {
                if (theEvent.getSource() != AWTAutoShutdown.getInstance()) {
                    AWTAutoShutdown.getInstance().notifyThreadBusy(dispatchThread);
                }
                notifyAll();
            } else if (notifyID) {
                notifyAll();
            }
        } else {
            // The event was not coalesced or has non-Component source.
            // Insert it at the end of the appropriate Queue.
            queues[priority].tail.next = newItem;
            queues[priority].tail = newItem;
            if (notifyID) {
                notifyAll();
            }
        }
    }

    private boolean coalescePaintEvent(PaintEvent e) {
        ComponentPeer sourcePeer = ((Component)e.getSource()).peer;
        if (sourcePeer != null) {
            sourcePeer.coalescePaintEvent(e);
        }
        EventQueueItem[] cache = ((Component)e.getSource()).eventCache;
        if (cache == null) {
            return false;
        }
        int index = eventToCacheIndex(e);

        if (index != -1 && cache[index] != null) {
            PaintEvent merged = mergePaintEvents(e, (PaintEvent)cache[index].event);
            if (merged != null) {
                cache[index].event = merged;
                return true;
            }
        }
        return false;
    }

    private PaintEvent mergePaintEvents(PaintEvent a, PaintEvent b) {
        Rectangle aRect = a.getUpdateRect();
        Rectangle bRect = b.getUpdateRect();
        if (bRect.contains(aRect)) {
            return b;
        }
        if (aRect.contains(bRect)) {
            return a;
        }
        return null;
    }

    private boolean coalesceMouseEvent(MouseEvent e) {
        EventQueueItem[] cache = ((Component)e.getSource()).eventCache;
        if (cache == null) {
            return false;
        }
        int index = eventToCacheIndex(e);
        if (index != -1 && cache[index] != null) {
            cache[index].event = e;
            return true;
        }
        return false;
    }

    private boolean coalescePeerEvent(PeerEvent e) {
        EventQueueItem[] cache = ((Component)e.getSource()).eventCache;
        if (cache == null) {
            return false;
        }
        int index = eventToCacheIndex(e);
        if (index != -1 && cache[index] != null) {
            e = e.coalesceEvents((PeerEvent)cache[index].event);
            if (e != null) {
                cache[index].event = e;
                return true;
            } else {
                cache[index] = null;
            }
        }
        return false;
    }

    /*
     * Should avoid of calling this method by any means
     * as it's working time is dependant on EQ length.
     * In the wors case this method alone can slow down the entire application
     * 10 times by stalling the Event processing.
     * Only here by backward compatibility reasons.
     */
    private boolean coalesceOtherEvent(AWTEvent e, int priority) {
        int id = e.getID();
        Component source = (Component)e.getSource();
        for (EventQueueItem entry = queues[priority].head;
            entry != null; entry = entry.next)
        {
            // Give Component.coalesceEvents a chance
            if (entry.event.getSource() == source && entry.event.getID() == id) {
                AWTEvent coalescedEvent = source.coalesceEvents(
                    entry.event, e);
                if (coalescedEvent != null) {
                    entry.event = coalescedEvent;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean coalesceEvent(AWTEvent e, int priority) {
        if (!(e.getSource() instanceof Component)) {
            return false;
        }
        if (e instanceof PeerEvent) {
            return coalescePeerEvent((PeerEvent)e);
        }
        // The worst case
        if (((Component)e.getSource()).isCoalescingEnabled()
            && coalesceOtherEvent(e, priority))
        {
            return true;
        }
        if (e instanceof PaintEvent) {
            return coalescePaintEvent((PaintEvent)e);
        }
        if (e instanceof MouseEvent) {
            return coalesceMouseEvent((MouseEvent)e);
        }
        return false;
    }

    private void cacheEQItem(EventQueueItem entry) {
        int index = eventToCacheIndex(entry.event);
        if (index != -1 && entry.event.getSource() instanceof Component) {
            Component source = (Component)entry.event.getSource();
            if (source.eventCache == null) {
                source.eventCache = new EventQueueItem[CACHE_LENGTH];
            }
            source.eventCache[index] = entry;
        }
    }

    private void uncacheEQItem(EventQueueItem entry) {
        int index = eventToCacheIndex(entry.event);
        if (index != -1 && entry.event.getSource() instanceof Component) {
            Component source = (Component)entry.event.getSource();
            if (source.eventCache == null) {
                return;
            }
            source.eventCache[index] = null;
        }
    }

    private static final int PAINT = 0;
    private static final int UPDATE = 1;
    private static final int MOVE = 2;
    private static final int DRAG = 3;
    private static final int PEER = 4;
    private static final int CACHE_LENGTH = 5;

    private static int eventToCacheIndex(AWTEvent e) {
        switch(e.getID()) {
        case PaintEvent.PAINT:
            return PAINT;
        case PaintEvent.UPDATE:
            return UPDATE;
        case MouseEvent.MOUSE_MOVED:
            return MOVE;
        case MouseEvent.MOUSE_DRAGGED:
            return DRAG;
        default:
            return e instanceof PeerEvent ? PEER : -1;
        }
    }

    /**
     * Returns whether an event is pending on any of the separate
     * Queues.
     * @return whether an event is pending on any of the separate Queues
     */
    private boolean noEvents() {
        for (int i = 0; i < NUM_PRIORITIES; i++) {
            if (queues[i].head != null) {
                return false;
            }
        }

        return true;
    }

    /**
     * Removes an event from the <code>EventQueue</code> and
     * returns it.  This method will block until an event has
     * been posted by another thread.
     * @return the next <code>AWTEvent</code>
     * @exception InterruptedException
     *            if any thread has interrupted this thread
     */
    public AWTEvent getNextEvent() throws InterruptedException {
        do {
            /*
             * SunToolkit.flushPendingEvents must be called outside
             * of the synchronized block to avoid deadlock when
             * event queues are nested with push()/pop().
             */
            SunToolkit.flushPendingEvents();
            synchronized (this) {
                for (int i = NUM_PRIORITIES - 1; i >= 0; i--) {
                    if (queues[i].head != null) {
                        EventQueueItem entry = queues[i].head;
                        queues[i].head = entry.next;
                        if (entry.next == null) {
                            queues[i].tail = null;
                        }
                        uncacheEQItem(entry);
                        return entry.event;
                    }
                }
                AWTAutoShutdown.getInstance().notifyThreadFree(dispatchThread);
                wait();
            }
        } while(true);
    }

    AWTEvent getNextEvent(int id) throws InterruptedException {
        do {
            /*
             * SunToolkit.flushPendingEvents must be called outside
             * of the synchronized block to avoid deadlock when
             * event queues are nested with push()/pop().
             */
            SunToolkit.flushPendingEvents();
            synchronized (this) {
                for (int i = 0; i < NUM_PRIORITIES; i++) {
                    for (EventQueueItem entry = queues[i].head, prev = null;
                         entry != null; prev = entry, entry = entry.next)
                    {
                        if (entry.event.getID() == id) {
                            if (prev == null) {
                                queues[i].head = entry.next;
                            } else {
                                prev.next = entry.next;
                            }
                            if (queues[i].tail == entry) {
                                queues[i].tail = prev;
                            }
                            uncacheEQItem(entry);
                            return entry.event;
                        }
                    }
                }
                this.waitForID = id;
                wait();
                this.waitForID = 0;
            }
        } while(true);
    }

    /**
     * Returns the first event on the <code>EventQueue</code>
     * without removing it.
     * @return the first event
     */
    public synchronized AWTEvent peekEvent() {
        for (int i = NUM_PRIORITIES - 1; i >= 0; i--) {
            if (queues[i].head != null) {
                return queues[i].head.event;
            }
        }

        return null;
    }

    /**
     * Returns the first event with the specified id, if any.
     * @param id the id of the type of event desired
     * @return the first event of the specified id or <code>null</code>
     *    if there is no such event
     */
    public synchronized AWTEvent peekEvent(int id) {
        for (int i = NUM_PRIORITIES - 1; i >= 0; i--) {
            EventQueueItem q = queues[i].head;
            for (; q != null; q = q.next) {
                if (q.event.getID() == id) {
                    return q.event;
                }
            }
        }

        return null;
    }

    /**
     * Dispatches an event. The manner in which the event is
     * dispatched depends upon the type of the event and the
     * type of the event's source object:
     * <p> </p>
     * <table border=1 summary="Event types, source types, and dispatch methods">
     * <tr>
     *     <th>Event Type</th>
     *     <th>Source Type</th>
     *     <th>Dispatched To</th>
     * </tr>
     * <tr>
     *     <td>ActiveEvent</td>
     *     <td>Any</td>
     *     <td>event.dispatch()</td>
     * </tr>
     * <tr>
     *     <td>Other</td>
     *     <td>Component</td>
     *     <td>source.dispatchEvent(AWTEvent)</td>
     * </tr>
     * <tr>
     *     <td>Other</td>
     *     <td>MenuComponent</td>
     *     <td>source.dispatchEvent(AWTEvent)</td>
     * </tr>
     * <tr>
     *     <td>Other</td>
     *     <td>Other</td>
     *     <td>No action (ignored)</td>
     * </tr>
     * </table>
     * <p> </p>
     * @param event an instance of <code>java.awt.AWTEvent</code>,
     *          or a subclass of it
     * @throws NullPointerException if <code>event</code> is <code>null</code>
     * @since           1.2
     */
    protected void dispatchEvent(AWTEvent event) {
        event.isPosted = true;
        Object src = event.getSource();
        if (event instanceof ActiveEvent) {
            // This could become the sole method of dispatching in time.
            setCurrentEventAndMostRecentTimeImpl(event);

            ((ActiveEvent)event).dispatch();
        } else if (src instanceof Component) {
            ((Component)src).dispatchEvent(event);
            event.dispatched();
        } else if (src instanceof MenuComponent) {
            ((MenuComponent)src).dispatchEvent(event);
        } else if (src instanceof TrayIcon) {
            ((TrayIcon)src).dispatchEvent(event);
        } else if (src instanceof AWTAutoShutdown) {
            if (noEvents()) {
                dispatchThread.stopDispatching();
            }
        } else {
            System.err.println("unable to dispatch event: " + event);
        }
    }

    /**
     * Returns the timestamp of the most recent event that had a timestamp, and
     * that was dispatched from the <code>EventQueue</code> associated with the
     * calling thread. If an event with a timestamp is currently being
     * dispatched, its timestamp will be returned. If no events have yet
     * been dispatched, the EventQueue's initialization time will be
     * returned instead.In the current version of
     * the JDK, only <code>InputEvent</code>s,
     * <code>ActionEvent</code>s, and <code>InvocationEvent</code>s have
     * timestamps; however, future versions of the JDK may add timestamps to
     * additional event types. Note that this method should only be invoked
     * from an application's {@link #isDispatchThread event dispatching thread}.
     * If this method is
     * invoked from another thread, the current system time (as reported by
     * <code>System.currentTimeMillis()</code>) will be returned instead.
     *
     * @return the timestamp of the last <code>InputEvent</code>,
     *         <code>ActionEvent</code>, or <code>InvocationEvent</code> to be
     *         dispatched, or <code>System.currentTimeMillis()</code> if this
     *         method is invoked on a thread other than an event dispatching
     *         thread
     * @see java.awt.event.InputEvent#getWhen
     * @see java.awt.event.ActionEvent#getWhen
     * @see java.awt.event.InvocationEvent#getWhen
     * @see #isDispatchThread
     *
     * @since 1.4
     */
    public static long getMostRecentEventTime() {
        return Toolkit.getEventQueue().getMostRecentEventTimeImpl();
    }
    private synchronized long getMostRecentEventTimeImpl() {
        return (Thread.currentThread() == dispatchThread)
            ? mostRecentEventTime
            : System.currentTimeMillis();
    }

    /**
     * @return most recent event time on all threads.
     */
    synchronized long getMostRecentEventTimeEx() {
        return mostRecentEventTime;
    }

    /**
     * Returns the the event currently being dispatched by the
     * <code>EventQueue</code> associated with the calling thread. This is
     * useful if a method needs access to the event, but was not designed to
     * receive a reference to it as an argument. Note that this method should
     * only be invoked from an application's event dispatching thread. If this
     * method is invoked from another thread, null will be returned.
     *
     * @return the event currently being dispatched, or null if this method is
     *         invoked on a thread other than an event dispatching thread
     * @since 1.4
     */
    public static AWTEvent getCurrentEvent() {
        return Toolkit.getEventQueue().getCurrentEventImpl();
    }
    private synchronized AWTEvent getCurrentEventImpl() {
        return (Thread.currentThread() == dispatchThread)
            ? ((AWTEvent)currentEvent.get())
            : null;
    }

    /**
     * Replaces the existing <code>EventQueue</code> with the specified one.
     * Any pending events are transferred to the new <code>EventQueue</code>
     * for processing by it.
     *
     * @param newEventQueue an <code>EventQueue</code>
     *          (or subclass thereof) instance to be use
     * @see      java.awt.EventQueue#pop
     * @throws NullPointerException if <code>newEventQueue</code> is <code>null</code>
     * @since           1.2
     */
    public synchronized void push(EventQueue newEventQueue) {
        if (eventLog.isLoggable(PlatformLogger.FINE)) {
            eventLog.fine("EventQueue.push(" + newEventQueue + ")");
        }

        if (nextQueue != null) {
            nextQueue.push(newEventQueue);
            return;
        }

        synchronized (newEventQueue) {
            // Transfer all events forward to new EventQueue.
            while (peekEvent() != null) {
                try {
                    newEventQueue.postEventPrivate(getNextEvent());
                } catch (InterruptedException ie) {
                    if (eventLog.isLoggable(PlatformLogger.FINE)) {
                        eventLog.fine("Interrupted push", ie);
                    }
                }
            }

            newEventQueue.previousQueue = this;
        }
        /*
         * Stop the event dispatch thread associated with the currently
         * active event queue, so that after the new queue is pushed
         * on the top this event dispatch thread won't prevent AWT from
         * being automatically shut down.
         * Use stopDispatchingLater() to avoid deadlock: stopDispatching()
         * waits for the dispatch thread to exit, so if the dispatch
         * thread attempts to synchronize on this EventQueue object
         * it will never exit since we already hold this lock.
         */
        if (dispatchThread != null) {
            dispatchThread.stopDispatchingLater();
        }

        nextQueue = newEventQueue;

        AppContext appContext = AppContext.getAppContext();
        if (appContext.get(AppContext.EVENT_QUEUE_KEY) == this) {
            appContext.put(AppContext.EVENT_QUEUE_KEY, newEventQueue);
        }
    }

    /**
     * Stops dispatching events using this <code>EventQueue</code>.
     * Any pending events are transferred to the previous
     * <code>EventQueue</code> for processing.
     * <p>
     * Warning: To avoid deadlock, do not declare this method
     * synchronized in a subclass.
     *
     * @exception EmptyStackException if no previous push was made
     *  on this <code>EventQueue</code>
     * @see      java.awt.EventQueue#push
     * @since           1.2
     */
    protected void pop() throws EmptyStackException {
        if (eventLog.isLoggable(PlatformLogger.FINE)) {
            eventLog.fine("EventQueue.pop(" + this + ")");
        }

        // To prevent deadlock, we lock on the previous EventQueue before
        // this one.  This uses the same locking order as everything else
        // in EventQueue.java, so deadlock isn't possible.
        EventQueue prev = previousQueue;
        synchronized ((prev != null) ? prev : this) {
          synchronized(this) {
            if (nextQueue != null) {
                nextQueue.pop();
                return;
            }
            if (previousQueue == null) {
                throw new EmptyStackException();
            }

            // Transfer all events back to previous EventQueue.
            previousQueue.nextQueue = null;
            while (peekEvent() != null) {
                try {
                    previousQueue.postEventPrivate(getNextEvent());
                } catch (InterruptedException ie) {
                    if (eventLog.isLoggable(PlatformLogger.FINE)) {
                        eventLog.fine("Interrupted pop", ie);
                    }
                }
            }
            AppContext appContext = AppContext.getAppContext();
            if (appContext.get(AppContext.EVENT_QUEUE_KEY) == this) {
                appContext.put(AppContext.EVENT_QUEUE_KEY, previousQueue);
            }

            previousQueue = null;
          }
        }

        EventDispatchThread dt = this.dispatchThread;
        if (dt != null) {
            dt.stopDispatching(); // Must be done outside synchronized
                                  // block to avoid possible deadlock
        }
    }

    /**
     * Returns true if the calling thread is
     * {@link Toolkit#getSystemEventQueue the current AWT EventQueue}'s
     * dispatch thread. Use this method to ensure that a particular
     * task is being executed (or not being) there.
     * <p>
     * Note: use the {@link #invokeLater} or {@link #invokeAndWait}
     * methods to execute a task in
     * {@link Toolkit#getSystemEventQueue the current AWT EventQueue}'s
     * dispatch thread.
     * <p>
     *
     * @return true if running in
     * {@link Toolkit#getSystemEventQueue the current AWT EventQueue}'s
     * dispatch thread
     * @see             #invokeLater
     * @see             #invokeAndWait
     * @see             Toolkit#getSystemEventQueue
     * @since           1.2
     */
    public static boolean isDispatchThread() {
        EventQueue eq = Toolkit.getEventQueue();
        EventQueue next = eq.nextQueue;
        while (next != null) {
            eq = next;
            next = eq.nextQueue;
        }
        return (Thread.currentThread() == eq.dispatchThread);
    }

    final void initDispatchThread() {
        synchronized (this) {
            if (dispatchThread == null && !threadGroup.isDestroyed()) {
                dispatchThread = (EventDispatchThread)
                    AccessController.doPrivileged(new PrivilegedAction() {
                        public Object run() {
                            EventDispatchThread t =
                                new EventDispatchThread(threadGroup,
                                                        name,
                                                        EventQueue.this);
                            t.setContextClassLoader(classLoader);
                            t.setPriority(Thread.NORM_PRIORITY + 1);
                            t.setDaemon(false);
                            return t;
                        }
                    });
                AWTAutoShutdown.getInstance().notifyThreadBusy(dispatchThread);
                dispatchThread.start();
            }
        }
    }

    final void detachDispatchThread() {
        dispatchThread = null;
    }

    /*
     * Gets the <code>EventDispatchThread</code> for this
     * <code>EventQueue</code>.
     * @return the event dispatch thread associated with this event queue
     *         or <code>null</code> if this event queue doesn't have a
     *         working thread associated with it
     * @see    java.awt.EventQueue#initDispatchThread
     * @see    java.awt.EventQueue#detachDispatchThread
     */
    final EventDispatchThread getDispatchThread() {
        return dispatchThread;
    }

    /*
     * Removes any pending events for the specified source object.
     * If removeAllEvents parameter is <code>true</code> then all
     * events for the specified source object are removed, if it
     * is <code>false</code> then <code>SequencedEvent</code>, <code>SentEvent</code>,
     * <code>FocusEvent</code>, <code>WindowEvent</code>, <code>KeyEvent</code>,
     * and <code>InputMethodEvent</code> are kept in the queue, but all other
     * events are removed.
     *
     * This method is normally called by the source's
     * <code>removeNotify</code> method.
     */
    final void removeSourceEvents(Object source, boolean removeAllEvents) {
        SunToolkit.flushPendingEvents();
        synchronized (this) {
            for (int i = 0; i < NUM_PRIORITIES; i++) {
                EventQueueItem entry = queues[i].head;
                EventQueueItem prev = null;
                while (entry != null) {
                    if ((entry.event.getSource() == source)
                        && (removeAllEvents
                            || ! (entry.event instanceof SequencedEvent
                                  || entry.event instanceof SentEvent
                                  || entry.event instanceof FocusEvent
                                  || entry.event instanceof WindowEvent
                                  || entry.event instanceof KeyEvent
                                  || entry.event instanceof InputMethodEvent)))
                    {
                        if (entry.event instanceof SequencedEvent) {
                            ((SequencedEvent)entry.event).dispose();
                        }
                        if (entry.event instanceof SentEvent) {
                            ((SentEvent)entry.event).dispose();
                        }
                        if (prev == null) {
                            queues[i].head = entry.next;
                        } else {
                            prev.next = entry.next;
                        }
                        uncacheEQItem(entry);
                    } else {
                        prev = entry;
                    }
                    entry = entry.next;
                }
                queues[i].tail = prev;
            }
        }
    }

    static void setCurrentEventAndMostRecentTime(AWTEvent e) {
        Toolkit.getEventQueue().setCurrentEventAndMostRecentTimeImpl(e);
    }
    private synchronized void setCurrentEventAndMostRecentTimeImpl(AWTEvent e)
    {
        if (Thread.currentThread() != dispatchThread) {
            return;
        }

        currentEvent = new WeakReference(e);

        // This series of 'instanceof' checks should be replaced with a
        // polymorphic type (for example, an interface which declares a
        // getWhen() method). However, this would require us to make such
        // a type public, or to place it in sun.awt. Both of these approaches
        // have been frowned upon. So for now, we hack.
        //
        // In tiger, we will probably give timestamps to all events, so this
        // will no longer be an issue.
        long mostRecentEventTime2 = Long.MIN_VALUE;
        if (e instanceof InputEvent) {
            InputEvent ie = (InputEvent)e;
            mostRecentEventTime2 = ie.getWhen();
        } else if (e instanceof InputMethodEvent) {
            InputMethodEvent ime = (InputMethodEvent)e;
            mostRecentEventTime2 = ime.getWhen();
        } else if (e instanceof ActionEvent) {
            ActionEvent ae = (ActionEvent)e;
            mostRecentEventTime2 = ae.getWhen();
        } else if (e instanceof InvocationEvent) {
            InvocationEvent ie = (InvocationEvent)e;
            mostRecentEventTime2 = ie.getWhen();
        }
        mostRecentEventTime = Math.max(mostRecentEventTime, mostRecentEventTime2);
    }

    /**
     * Causes <code>runnable</code> to have its <code>run</code>
     * method called in the {@link #isDispatchThread dispatch thread} of
     * {@link Toolkit#getSystemEventQueue the system EventQueue}.
     * This will happen after all pending events are processed.
     *
     * @param runnable  the <code>Runnable</code> whose <code>run</code>
     *                  method should be executed
     *                  asynchronously in the
     *                  {@link #isDispatchThread event dispatch thread}
     *                  of {@link Toolkit#getSystemEventQueue the system EventQueue}
     * @see             #invokeAndWait
     * @see             Toolkit#getSystemEventQueue
     * @see             #isDispatchThread
     * @since           1.2
     */
    public static void invokeLater(Runnable runnable) {
        Toolkit.getEventQueue().postEvent(
            new InvocationEvent(Toolkit.getDefaultToolkit(), runnable));
    }

    /**
     * Causes <code>runnable</code> to have its <code>run</code>
     * method called in the {@link #isDispatchThread dispatch thread} of
     * {@link Toolkit#getSystemEventQueue the system EventQueue}.
     * This will happen after all pending events are processed.
     * The call blocks until this has happened.  This method
     * will throw an Error if called from the
     * {@link #isDispatchThread event dispatcher thread}.
     *
     * @param runnable  the <code>Runnable</code> whose <code>run</code>
     *                  method should be executed
     *                  synchronously in the
     *                  {@link #isDispatchThread event dispatch thread}
     *                  of {@link Toolkit#getSystemEventQueue the system EventQueue}
     * @exception       InterruptedException  if any thread has
     *                  interrupted this thread
     * @exception       InvocationTargetException  if an throwable is thrown
     *                  when running <code>runnable</code>
     * @see             #invokeLater
     * @see             Toolkit#getSystemEventQueue
     * @see             #isDispatchThread
     * @since           1.2
     */
    public static void invokeAndWait(Runnable runnable)
             throws InterruptedException, InvocationTargetException {

        if (EventQueue.isDispatchThread()) {
            throw new Error("Cannot call invokeAndWait from the event dispatcher thread");
        }

        class AWTInvocationLock {}
        Object lock = new AWTInvocationLock();

        InvocationEvent event =
            new InvocationEvent(Toolkit.getDefaultToolkit(), runnable, lock,
                                true);

        synchronized (lock) {
            Toolkit.getEventQueue().postEvent(event);
            lock.wait();
        }

        Throwable eventThrowable = event.getThrowable();
        if (eventThrowable != null) {
            throw new InvocationTargetException(eventThrowable);
        }
    }

    /*
     * Called from PostEventQueue.postEvent to notify that a new event
     * appeared. First it proceeds to the EventQueue on the top of the
     * stack, then notifies the associated dispatch thread if it exists
     * or starts a new one otherwise.
     */
    private void wakeup(boolean isShutdown) {
        synchronized(this) {
            if (nextQueue != null) {
                // Forward call to the top of EventQueue stack.
                nextQueue.wakeup(isShutdown);
            } else if (dispatchThread != null) {
                notifyAll();
            } else if (!isShutdown) {
                initDispatchThread();
            }
        }
    }
}

/**
 * The Queue object holds pointers to the beginning and end of one internal
 * queue. An EventQueue object is composed of multiple internal Queues, one
 * for each priority supported by the EventQueue. All Events on a particular
 * internal Queue have identical priority.
 */
class Queue {
    EventQueueItem head;
    EventQueueItem tail;
}
