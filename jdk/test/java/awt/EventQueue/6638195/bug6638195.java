/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 *
 * @bug 6638195
 * @author Igor Kushnirskiy
 * @summary tests if EventQueueDelegate.Delegate is invoked.
 */

import sun.awt.EventQueueDelegate;
import com.sun.java.swing.SwingUtilities3;

import java.util.*;
import java.util.concurrent.*;
import java.awt.*;

public class bug6638195 {
    public static void main(String[] args) throws Exception {
        MyEventQueueDelegate delegate = new MyEventQueueDelegate();
        EventQueueDelegate.setDelegate(delegate);
        runTest(delegate);

        delegate = new MyEventQueueDelegate();
        SwingUtilities3.setEventQueueDelegate(getObjectMap(delegate));
        runTest(delegate);
    }

    private static void runTest(MyEventQueueDelegate delegate) throws Exception {
        EventQueue.invokeLater(
            new Runnable() {
                public void run() {
                }
            });
        final CountDownLatch latch = new CountDownLatch(1);
        EventQueue.invokeLater(
            new Runnable() {
                public void run() {
                    latch.countDown();
                }
            });
        latch.await();
        if (! delegate.allInvoked()) {
            throw new RuntimeException("failed");
        }
    }

    static Map<String, Map<String, Object>> getObjectMap(
          final EventQueueDelegate.Delegate delegate) {
        Map<String, Map<String, Object>> objectMap =
            new HashMap<String, Map<String, Object>>();
        Map<String, Object> methodMap;

        final AWTEvent[] afterDispatchEventArgument = new AWTEvent[1];
        final Object[] afterDispatchHandleArgument = new Object[1];
        Callable<Void> afterDispatchCallable =
            new Callable<Void>() {
                public Void call() {
                    delegate.afterDispatch(afterDispatchEventArgument[0],
                                           afterDispatchHandleArgument[0]);
                    return null;
                }
            };
        methodMap = new HashMap<String, Object>();
        methodMap.put("event", afterDispatchEventArgument);
        methodMap.put("handle", afterDispatchHandleArgument);
        methodMap.put("method", afterDispatchCallable);
        objectMap.put("afterDispatch", methodMap);

        final AWTEvent[] beforeDispatchEventArgument = new AWTEvent[1];
        Callable<Object> beforeDispatchCallable =
            new Callable<Object>() {
                public Object call() {
                    return delegate.beforeDispatch(
                        beforeDispatchEventArgument[0]);
                }
            };
        methodMap = new HashMap<String, Object>();
        methodMap.put("event", beforeDispatchEventArgument);
        methodMap.put("method", beforeDispatchCallable);
        objectMap.put("beforeDispatch", methodMap);

        final EventQueue[] getNextEventEventQueueArgument = new EventQueue[1];
        Callable<AWTEvent> getNextEventCallable =
            new Callable<AWTEvent>() {
                public AWTEvent call() throws Exception {
                    return delegate.getNextEvent(
                        getNextEventEventQueueArgument[0]);
                }
            };
        methodMap = new HashMap<String, Object>();
        methodMap.put("eventQueue", getNextEventEventQueueArgument);
        methodMap.put("method", getNextEventCallable);
        objectMap.put("getNextEvent", methodMap);

        return objectMap;
    }
    static class MyEventQueueDelegate implements EventQueueDelegate.Delegate {
        private volatile boolean getNextEventInvoked = false;
        private volatile boolean beforeDispatchInvoked = false;
        private volatile boolean afterDispatchInvoked = false;
        public AWTEvent getNextEvent(EventQueue eventQueue)
              throws InterruptedException {
            getNextEventInvoked = true;
            return eventQueue.getNextEvent();
        }
        public Object beforeDispatch(AWTEvent event) {
            beforeDispatchInvoked = true;
            return null;
        }
        public void afterDispatch(AWTEvent event, Object handle) {
            afterDispatchInvoked = true;
        }
        private boolean allInvoked() {
            return getNextEventInvoked && beforeDispatchInvoked && afterDispatchInvoked;
        }
    }
}
