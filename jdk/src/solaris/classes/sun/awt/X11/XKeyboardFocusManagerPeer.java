/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.X11;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Window;

import java.awt.event.FocusEvent;

import java.awt.peer.KeyboardFocusManagerPeer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.logging.Level;
import java.util.logging.Logger;

import sun.awt.CausedFocusEvent;
import sun.awt.SunToolkit;

public class XKeyboardFocusManagerPeer implements KeyboardFocusManagerPeer {
    private static final Logger focusLog = Logger.getLogger("sun.awt.X11.focus.XKeyboardFocusManagerPeer");
    KeyboardFocusManager manager;

    XKeyboardFocusManagerPeer(KeyboardFocusManager manager) {
        this.manager = manager;
    }

    private static Object lock = new Object() {};
    private static Component currentFocusOwner;
    private static Window currentFocusedWindow;

    static void setCurrentNativeFocusOwner(Component comp) {
        if (focusLog.isLoggable(Level.FINER)) focusLog.finer("Setting current native focus owner " + comp);
        synchronized(lock) {
            currentFocusOwner = comp;
        }
    }

    static void setCurrentNativeFocusedWindow(Window win) {
        if (focusLog.isLoggable(Level.FINER)) focusLog.finer("Setting current native focused window " + win);
        synchronized(lock) {
            currentFocusedWindow = win;
        }
    }

    static Component getCurrentNativeFocusOwner() {
        synchronized(lock) {
            return currentFocusOwner;
        }
    }

    static Window getCurrentNativeFocusedWindow() {
        synchronized(lock) {
            return currentFocusedWindow;
        }
    }

    public Window getCurrentFocusedWindow() {
        return getCurrentNativeFocusedWindow();
    }

    public void setCurrentFocusOwner(Component comp) {
        setCurrentNativeFocusOwner(comp);
    }

    public Component getCurrentFocusOwner() {
        return getCurrentNativeFocusOwner();
    }

    public void clearGlobalFocusOwner(Window activeWindow) {
         if (activeWindow != null) {
            Component focusOwner = activeWindow.getFocusOwner();
            if (focusLog.isLoggable(Level.FINE)) focusLog.fine("Clearing global focus owner " + focusOwner);
            if (focusOwner != null) {
//                XComponentPeer nativePeer = XComponentPeer.getNativeContainer(focusOwner);
//                if (nativePeer != null) {
                    FocusEvent fl = new CausedFocusEvent(focusOwner, FocusEvent.FOCUS_LOST, false, null,
                                                         CausedFocusEvent.Cause.CLEAR_GLOBAL_FOCUS_OWNER);
                    XWindow.sendEvent(fl);
//                }
            }
        }
   }

    static boolean simulateMotifRequestFocus(Component lightweightChild, Component target, boolean temporary,
                                      boolean focusedWindowChangeAllowed, long time, CausedFocusEvent.Cause cause)
    {
        if (lightweightChild == null) {
            lightweightChild = (Component)target;
        }
        Component currentOwner = XKeyboardFocusManagerPeer.getCurrentNativeFocusOwner();
        if (currentOwner != null && currentOwner.getPeer() == null) {
            currentOwner = null;
        }
        if (focusLog.isLoggable(Level.FINER)) focusLog.finer("Simulating transfer from " + currentOwner + " to " + lightweightChild);
        FocusEvent  fg = new CausedFocusEvent(lightweightChild, FocusEvent.FOCUS_GAINED, false, currentOwner, cause);
        FocusEvent fl = null;
        if (currentOwner != null) {
            fl = new CausedFocusEvent(currentOwner, FocusEvent.FOCUS_LOST, false, lightweightChild, cause);
        }

        if (fl != null) {
            XWindow.sendEvent(fl);
        }
        XWindow.sendEvent(fg);
        return true;
    }

    static Method shouldNativelyFocusHeavyweightMethod;

    static int shouldNativelyFocusHeavyweight(Component heavyweight,
         Component descendant, boolean temporary,
         boolean focusedWindowChangeAllowed, long time, CausedFocusEvent.Cause cause)
    {
        if (shouldNativelyFocusHeavyweightMethod == null) {
            Class[] arg_types =
                new Class[] { Component.class,
                              Component.class,
                              Boolean.TYPE,
                              Boolean.TYPE,
                              Long.TYPE,
                              CausedFocusEvent.Cause.class
            };

            shouldNativelyFocusHeavyweightMethod =
                SunToolkit.getMethod(KeyboardFocusManager.class,
                                   "shouldNativelyFocusHeavyweight",
                                   arg_types);
        }
        Object[] args = new Object[] { heavyweight,
                                       descendant,
                                       Boolean.valueOf(temporary),
                                       Boolean.valueOf(focusedWindowChangeAllowed),
                                       Long.valueOf(time), cause};

        int result = XComponentPeer.SNFH_FAILURE;
        if (shouldNativelyFocusHeavyweightMethod != null) {
            try {
                result = ((Integer) shouldNativelyFocusHeavyweightMethod.invoke(null, args)).intValue();
            }
            catch (IllegalAccessException e) {
                assert false;
            }
            catch (InvocationTargetException e) {
                assert false;
            }
        }

        return result;
    }
}
