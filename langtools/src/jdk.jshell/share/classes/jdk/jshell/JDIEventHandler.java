/*
 * Copyright (c) 1998, 2011, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jshell;

import com.sun.jdi.*;
import com.sun.jdi.event.*;

/**
 * Handler of Java Debug Interface events.
 * Adapted from jdb EventHandler; Handling of events not used by JShell stubbed out.
 */
class JDIEventHandler implements Runnable {

    Thread thread;
    volatile boolean connected = true;
    boolean completed = false;
    String shutdownMessageKey;
    final JDIEnv env;

    JDIEventHandler(JDIEnv env) {
        this.env = env;
        this.thread = new Thread(this, "event-handler");
        this.thread.start();
    }

    synchronized void shutdown() {
        connected = false;  // force run() loop termination
        thread.interrupt();
        while (!completed) {
            try {wait();} catch (InterruptedException exc) {}
        }
    }

    @Override
    public void run() {
        EventQueue queue = env.vm().eventQueue();
        while (connected) {
            try {
                EventSet eventSet = queue.remove();
                boolean resumeStoppedApp = false;
                EventIterator it = eventSet.eventIterator();
                while (it.hasNext()) {
                    resumeStoppedApp |= handleEvent(it.nextEvent());
                }

                if (resumeStoppedApp) {
                    eventSet.resume();
                }
            } catch (InterruptedException exc) {
                // Do nothing. Any changes will be seen at top of loop.
            } catch (VMDisconnectedException discExc) {
                handleDisconnectedException();
                break;
            }
        }
        synchronized (this) {
            completed = true;
            notifyAll();
        }
    }

    private boolean handleEvent(Event event) {
        if (event instanceof ExceptionEvent) {
            exceptionEvent(event);
        } else if (event instanceof WatchpointEvent) {
            fieldWatchEvent(event);
        } else if (event instanceof MethodEntryEvent) {
            methodEntryEvent(event);
        } else if (event instanceof MethodExitEvent) {
            methodExitEvent(event);
        } else if (event instanceof ClassPrepareEvent) {
            classPrepareEvent(event);
        } else if (event instanceof ThreadStartEvent) {
            threadStartEvent(event);
        } else if (event instanceof ThreadDeathEvent) {
            threadDeathEvent(event);
        } else if (event instanceof VMStartEvent) {
            vmStartEvent(event);
            return true;
        } else {
            handleExitEvent(event);
        }
        return true;
    }

    private boolean vmDied = false;

    private void handleExitEvent(Event event) {
        if (event instanceof VMDeathEvent) {
            vmDied = true;
            shutdownMessageKey = "The application exited";
        } else if (event instanceof VMDisconnectEvent) {
            connected = false;
            if (!vmDied) {
                shutdownMessageKey = "The application has been disconnected";
            }
        } else {
            throw new InternalError("Unexpected event type: " +
                    event.getClass());
        }
        env.shutdown();
    }

    synchronized void handleDisconnectedException() {
        /*
         * A VMDisconnectedException has happened while dealing with
         * another event. We need to flush the event queue, dealing only
         * with exit events (VMDeath, VMDisconnect) so that we terminate
         * correctly.
         */
        EventQueue queue = env.vm().eventQueue();
        while (connected) {
            try {
                EventSet eventSet = queue.remove();
                EventIterator iter = eventSet.eventIterator();
                while (iter.hasNext()) {
                    handleExitEvent(iter.next());
                }
            } catch (InterruptedException exc) {
                // ignore
            } catch (InternalError exc) {
                // ignore
            }
        }
    }

    private void vmStartEvent(Event event)  {
        VMStartEvent se = (VMStartEvent)event;
    }

    private void methodEntryEvent(Event event)  {
        MethodEntryEvent me = (MethodEntryEvent)event;
    }

    private void methodExitEvent(Event event)  {
        MethodExitEvent me = (MethodExitEvent)event;
    }

    private void fieldWatchEvent(Event event)  {
        WatchpointEvent fwe = (WatchpointEvent)event;
    }

    private void classPrepareEvent(Event event)  {
        ClassPrepareEvent cle = (ClassPrepareEvent)event;
    }

    private void exceptionEvent(Event event) {
        ExceptionEvent ee = (ExceptionEvent)event;
    }

    private void threadDeathEvent(Event event) {
        ThreadDeathEvent tee = (ThreadDeathEvent)event;
    }

    private void threadStartEvent(Event event) {
        ThreadStartEvent tse = (ThreadStartEvent)event;
    }
}
