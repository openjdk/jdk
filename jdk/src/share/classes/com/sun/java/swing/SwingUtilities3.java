/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.swing;

import sun.awt.EventQueueDelegate;
import sun.awt.AppContext;
import sun.awt.SunToolkit;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.applet.Applet;
import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.RepaintManager;

/**
 * A collection of utility methods for Swing.
 * <p>
 * <b>WARNING:</b> While this class is public, it should not be treated as
 * public API and its API may change in incompatable ways between dot dot
 * releases and even patch releases. You should not rely on this class even
 * existing.
 *
 * This is a second part of sun.swing.SwingUtilities2. It is required
 * to provide services for JavaFX applets.
 *
 */
public class SwingUtilities3 {
    /**
     * The {@code clientProperty} key for delegate {@code RepaintManager}
     */
    private static final Object DELEGATE_REPAINT_MANAGER_KEY =
        new StringBuilder("DelegateRepaintManagerKey");

    /**
      * Registers delegate RepaintManager for {@code JComponent}.
      */
    public static void setDelegateRepaintManager(JComponent component,
                                                RepaintManager repaintManager) {
        /* setting up flag in AppContext to speed up lookups in case
         * there are no delegate RepaintManagers used.
         */
        AppContext.getAppContext().put(DELEGATE_REPAINT_MANAGER_KEY,
                                       Boolean.TRUE);

        component.putClientProperty(DELEGATE_REPAINT_MANAGER_KEY,
                                    repaintManager);
    }

    private static final Map<Container, Boolean> vsyncedMap =
        Collections.synchronizedMap(new WeakHashMap<Container, Boolean>());

    /**
     * Sets vsyncRequested state for the {@code rootContainer}.  If
     * {@code isRequested} is {@code true} then vsynced
     * {@code BufferStrategy} is enabled for this {@code rootContainer}.
     *
     * Note: requesting vsynced painting does not guarantee one. The outcome
     * depends on current RepaintManager's RepaintManager.PaintManager
     * and on the capabilities of the graphics hardware/software and what not.
     *
     * @param rootContainer topmost container. Should be either {@code Window}
     *  or {@code Applet}
     * @param isRequested the value to set vsyncRequested state to
     */
    public static void setVsyncRequested(Container rootContainer,
                                         boolean isRequested) {
        assert (rootContainer instanceof Applet) || (rootContainer instanceof Window);
        if (isRequested) {
            vsyncedMap.put(rootContainer, Boolean.TRUE);
        } else {
            vsyncedMap.remove(rootContainer);
        }
    }

    /**
     * Checks if vsync painting is requested for {@code rootContainer}
     *
     * @param rootContainer topmost container. Should be either Window or Applet
     * @return {@code true} if vsync painting is requested for {@code rootContainer}
     */
    public static boolean isVsyncRequested(Container rootContainer) {
        assert (rootContainer instanceof Applet) || (rootContainer instanceof Window);
        return Boolean.TRUE == vsyncedMap.get(rootContainer);
    }

    /**
     * Returns delegate {@code RepaintManager} for {@code component} hierarchy.
     */
    public static RepaintManager getDelegateRepaintManager(Component
                                                            component) {
        RepaintManager delegate = null;
        if (Boolean.TRUE == SunToolkit.targetToAppContext(component)
                                      .get(DELEGATE_REPAINT_MANAGER_KEY)) {
            while (delegate == null && component != null) {
                while (component != null
                         && ! (component instanceof JComponent)) {
                    component = component.getParent();
                }
                if (component != null) {
                    delegate = (RepaintManager)
                        ((JComponent) component)
                          .getClientProperty(DELEGATE_REPAINT_MANAGER_KEY);
                    component = component.getParent();
                }

            }
        }
        return delegate;
    }

    /*
     * We use maps to avoid reflection. Hopefully it should perform better
     * this way.
     */
    public static void setEventQueueDelegate(
            Map<String, Map<String, Object>> map) {
        EventQueueDelegate.setDelegate(new EventQueueDelegateFromMap(map));
    }

    private static class EventQueueDelegateFromMap
    implements EventQueueDelegate.Delegate {
        private final AWTEvent[] afterDispatchEventArgument;
        private final Object[] afterDispatchHandleArgument;
        private final Callable<Void> afterDispatchCallable;

        private final AWTEvent[] beforeDispatchEventArgument;
        private final Callable<Object> beforeDispatchCallable;

        private final EventQueue[] getNextEventEventQueueArgument;
        private final Callable<AWTEvent> getNextEventCallable;

        @SuppressWarnings("unchecked")
        public EventQueueDelegateFromMap(Map<String, Map<String, Object>> objectMap) {
            Map<String, Object> methodMap = objectMap.get("afterDispatch");
            afterDispatchEventArgument = (AWTEvent[]) methodMap.get("event");
            afterDispatchHandleArgument = (Object[]) methodMap.get("handle");
            afterDispatchCallable = (Callable<Void>) methodMap.get("method");

            methodMap = objectMap.get("beforeDispatch");
            beforeDispatchEventArgument = (AWTEvent[]) methodMap.get("event");
            beforeDispatchCallable = (Callable<Object>) methodMap.get("method");

            methodMap = objectMap.get("getNextEvent");
            getNextEventEventQueueArgument =
                (EventQueue[]) methodMap.get("eventQueue");
            getNextEventCallable = (Callable<AWTEvent>) methodMap.get("method");
        }

        @Override
        public void afterDispatch(AWTEvent event, Object handle) throws InterruptedException {
            afterDispatchEventArgument[0] = event;
            afterDispatchHandleArgument[0] = handle;
            try {
                afterDispatchCallable.call();
            } catch (InterruptedException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Object beforeDispatch(AWTEvent event) throws InterruptedException {
            beforeDispatchEventArgument[0] = event;
            try {
                return beforeDispatchCallable.call();
            } catch (InterruptedException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public AWTEvent getNextEvent(EventQueue eventQueue) throws InterruptedException {
            getNextEventEventQueueArgument[0] = eventQueue;
            try {
                return getNextEventCallable.call();
            } catch (InterruptedException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
