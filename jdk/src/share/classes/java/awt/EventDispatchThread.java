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

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.lang.reflect.Method;
import java.security.AccessController;
import sun.security.action.GetPropertyAction;
import sun.awt.AWTAutoShutdown;
import sun.awt.SunToolkit;

import java.util.Vector;
import sun.util.logging.PlatformLogger;

import sun.awt.dnd.SunDragSourceContextPeer;
import sun.awt.EventQueueDelegate;

/**
 * EventDispatchThread is a package-private AWT class which takes
 * events off the EventQueue and dispatches them to the appropriate
 * AWT components.
 *
 * The Thread starts a "permanent" event pump with a call to
 * pumpEvents(Conditional) in its run() method. Event handlers can choose to
 * block this event pump at any time, but should start a new pump (<b>not</b>
 * a new EventDispatchThread) by again calling pumpEvents(Conditional). This
 * secondary event pump will exit automatically as soon as the Condtional
 * evaluate()s to false and an additional Event is pumped and dispatched.
 *
 * @author Tom Ball
 * @author Amy Fowler
 * @author Fred Ecks
 * @author David Mendenhall
 *
 * @since 1.1
 */
class EventDispatchThread extends Thread {
    private static final PlatformLogger eventLog = PlatformLogger.getLogger("java.awt.event.EventDispatchThread");

    private EventQueue theQueue;
    private boolean doDispatch = true;
    private static final int ANY_EVENT = -1;

    private Vector<EventFilter> eventFilters = new Vector<EventFilter>();
    // used in handleException
    private int modalFiltersCount = 0;

    EventDispatchThread(ThreadGroup group, String name, EventQueue queue) {
        super(group, name);
        theQueue = queue;
    }

    void stopDispatchingImpl(boolean wait) {
        // Note: We stop dispatching via a flag rather than using
        // Thread.interrupt() because we can't guarantee that the wait()
        // we interrupt will be EventQueue.getNextEvent()'s.  -fredx 8-11-98

        StopDispatchEvent stopEvent = new StopDispatchEvent();

        // wait for the dispatcher to complete
        if (Thread.currentThread() != this) {

            // fix 4122683, 4128923
            // Post an empty event to ensure getNextEvent is unblocked
            //
            // We have to use postEventPrivate instead of postEvent because
            // EventQueue.pop calls EventDispatchThread.stopDispatching.
            // Calling SunToolkit.flushPendingEvents in this case could
            // lead to deadlock.
            theQueue.postEventPrivate(stopEvent);

            if (wait) {
                try {
                    join();
                } catch(InterruptedException e) {
                }
            }
        } else {
            stopEvent.dispatch();
        }
        synchronized (theQueue) {
            if (theQueue.getDispatchThread() == this) {
                theQueue.detachDispatchThread();
            }
        }
    }

    public void stopDispatching() {
        stopDispatchingImpl(true);
    }

    public void stopDispatchingLater() {
        stopDispatchingImpl(false);
    }

    class StopDispatchEvent extends AWTEvent implements ActiveEvent {
        /*
         * serialVersionUID
         */
        static final long serialVersionUID = -3692158172100730735L;

        public StopDispatchEvent() {
            super(EventDispatchThread.this,0);
        }

        public void dispatch() {
            doDispatch = false;
        }
    }

    public void run() {
        try {
            pumpEvents(new Conditional() {
                public boolean evaluate() {
                    return true;
                }
            });
        } finally {
            /*
             * This synchronized block is to secure that the event dispatch
             * thread won't die in the middle of posting a new event to the
             * associated event queue. It is important because we notify
             * that the event dispatch thread is busy after posting a new event
             * to its queue, so the EventQueue.dispatchThread reference must
             * be valid at that point.
             */
            synchronized (theQueue) {
                if (theQueue.getDispatchThread() == this) {
                    theQueue.detachDispatchThread();
                }
                /*
                 * Event dispatch thread dies in case of an uncaught exception.
                 * A new event dispatch thread for this queue will be started
                 * only if a new event is posted to it. In case if no more
                 * events are posted after this thread died all events that
                 * currently are in the queue will never be dispatched.
                 */
                /*
                 * Fix for 4648733. Check both the associated java event
                 * queue and the PostEventQueue.
                 */
                if (theQueue.peekEvent() != null ||
                    !SunToolkit.isPostEventQueueEmpty()) {
                    theQueue.initDispatchThread();
                }
                AWTAutoShutdown.getInstance().notifyThreadFree(this);
            }
        }
    }

    void pumpEvents(Conditional cond) {
        pumpEvents(ANY_EVENT, cond);
    }

    void pumpEventsForHierarchy(Conditional cond, Component modalComponent) {
        pumpEventsForHierarchy(ANY_EVENT, cond, modalComponent);
    }

    void pumpEvents(int id, Conditional cond) {
        pumpEventsForHierarchy(id, cond, null);
    }

    void pumpEventsForHierarchy(int id, Conditional cond, Component modalComponent)
    {
        pumpEventsForFilter(id, cond, new HierarchyEventFilter(modalComponent));
    }

    void pumpEventsForFilter(Conditional cond, EventFilter filter) {
        pumpEventsForFilter(ANY_EVENT, cond, filter);
    }

    void pumpEventsForFilter(int id, Conditional cond, EventFilter filter) {
        addEventFilter(filter);
        while (doDispatch && cond.evaluate()) {
            if (isInterrupted() || !pumpOneEventForFilters(id)) {
                doDispatch = false;
            }
        }
        removeEventFilter(filter);
    }

    void addEventFilter(EventFilter filter) {
        synchronized (eventFilters) {
            if (!eventFilters.contains(filter)) {
                if (filter instanceof ModalEventFilter) {
                    ModalEventFilter newFilter = (ModalEventFilter)filter;
                    int k = 0;
                    for (k = 0; k < eventFilters.size(); k++) {
                        EventFilter f = eventFilters.get(k);
                        if (f instanceof ModalEventFilter) {
                            ModalEventFilter cf = (ModalEventFilter)f;
                            if (cf.compareTo(newFilter) > 0) {
                                break;
                            }
                        }
                    }
                    eventFilters.add(k, filter);
                    modalFiltersCount++;
                } else {
                    eventFilters.add(filter);
                }
            }
        }
    }

    void removeEventFilter(EventFilter filter) {
        synchronized (eventFilters) {
            if (eventFilters.contains(filter)) {
                if (filter instanceof ModalEventFilter) {
                    modalFiltersCount--;
                }
                eventFilters.remove(filter);
            }
        }
    }

    boolean pumpOneEventForFilters(int id) {
        try {
            AWTEvent event;
            boolean eventOK;
            EventQueueDelegate.Delegate delegate =
                EventQueueDelegate.getDelegate();
            do {
                if (delegate != null && id == ANY_EVENT) {
                    event = delegate.getNextEvent(theQueue);
                } else {
                    event = (id == ANY_EVENT)
                        ? theQueue.getNextEvent()
                        : theQueue.getNextEvent(id);
                }

                eventOK = true;
                synchronized (eventFilters) {
                    for (int i = eventFilters.size() - 1; i >= 0; i--) {
                        EventFilter f = eventFilters.get(i);
                        EventFilter.FilterAction accept = f.acceptEvent(event);
                        if (accept == EventFilter.FilterAction.REJECT) {
                            eventOK = false;
                            break;
                        } else if (accept == EventFilter.FilterAction.ACCEPT_IMMEDIATELY) {
                            break;
                        }
                    }
                }
                eventOK = eventOK && SunDragSourceContextPeer.checkEvent(event);
                if (!eventOK) {
                    event.consume();
                }
            }
            while (eventOK == false);

            if (eventLog.isLoggable(PlatformLogger.FINEST)) {
                eventLog.finest("Dispatching: " + event);
            }

            Object handle = null;
            if (delegate != null) {
                handle = delegate.beforeDispatch(event);
            }
            theQueue.dispatchEvent(event);
            if (delegate != null) {
                delegate.afterDispatch(event, handle);
            }
            return true;
        }
        catch (ThreadDeath death) {
            return false;

        }
        catch (InterruptedException interruptedException) {
            return false; // AppContext.dispose() interrupts all
                          // Threads in the AppContext

        }
        // Can get and throw only unchecked exceptions
        catch (RuntimeException e) {
            processException(e);
        } catch (Error e) {
            processException(e);
        }
        return true;
    }

    private void processException(Throwable e) {
        if (eventLog.isLoggable(PlatformLogger.FINE)) {
            eventLog.fine("Processing exception: " + e);
        }
        getUncaughtExceptionHandler().uncaughtException(this, e);
        // don't rethrow the exception to avoid EDT recreation
    }

    boolean isDispatching(EventQueue eq) {
        return theQueue.equals(eq);
    }

    EventQueue getEventQueue() { return theQueue; }

    private static class HierarchyEventFilter implements EventFilter {
        private Component modalComponent;
        public HierarchyEventFilter(Component modalComponent) {
            this.modalComponent = modalComponent;
        }
        public FilterAction acceptEvent(AWTEvent event) {
            if (modalComponent != null) {
                int eventID = event.getID();
                boolean mouseEvent = (eventID >= MouseEvent.MOUSE_FIRST) &&
                                     (eventID <= MouseEvent.MOUSE_LAST);
                boolean actionEvent = (eventID >= ActionEvent.ACTION_FIRST) &&
                                      (eventID <= ActionEvent.ACTION_LAST);
                boolean windowClosingEvent = (eventID == WindowEvent.WINDOW_CLOSING);
                /*
                 * filter out MouseEvent and ActionEvent that's outside
                 * the modalComponent hierarchy.
                 * KeyEvent is handled by using enqueueKeyEvent
                 * in Dialog.show
                 */
                if (Component.isInstanceOf(modalComponent, "javax.swing.JInternalFrame")) {
                    /*
                     * Modal internal frames are handled separately. If event is
                     * for some component from another heavyweight than modalComp,
                     * it is accepted. If heavyweight is the same - we still accept
                     * event and perform further filtering in LightweightDispatcher
                     */
                    return windowClosingEvent ? FilterAction.REJECT : FilterAction.ACCEPT;
                }
                if (mouseEvent || actionEvent || windowClosingEvent) {
                    Object o = event.getSource();
                    if (o instanceof sun.awt.ModalExclude) {
                        // Exclude this object from modality and
                        // continue to pump it's events.
                        return FilterAction.ACCEPT;
                    } else if (o instanceof Component) {
                        Component c = (Component) o;
                        // 5.0u3 modal exclusion
                        boolean modalExcluded = false;
                        if (modalComponent instanceof Container) {
                            while (c != modalComponent && c != null) {
                                if ((c instanceof Window) &&
                                    (sun.awt.SunToolkit.isModalExcluded((Window)c))) {
                                    // Exclude this window and all its children from
                                    //  modality and continue to pump it's events.
                                    modalExcluded = true;
                                    break;
                                }
                                c = c.getParent();
                            }
                        }
                        if (!modalExcluded && (c != modalComponent)) {
                            return FilterAction.REJECT;
                        }
                    }
                }
            }
            return FilterAction.ACCEPT;
        }
    }
}
