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
import java.awt.peer.ComponentPeer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import sun.util.logging.PlatformLogger;

import sun.awt.CausedFocusEvent;
import sun.awt.SunToolkit;
import sun.awt.KeyboardFocusManagerPeerImpl;

public class XKeyboardFocusManagerPeer extends KeyboardFocusManagerPeerImpl {
    private static final PlatformLogger focusLog = PlatformLogger.getLogger("sun.awt.X11.focus.XKeyboardFocusManagerPeer");

    private static Object lock = new Object() {};
    private static Component currentFocusOwner;
    private static Window currentFocusedWindow;

    XKeyboardFocusManagerPeer(KeyboardFocusManager manager) {
        super(manager);
    }

    @Override
    public void setCurrentFocusOwner(Component comp) {
        setCurrentNativeFocusOwner(comp);
    }

    @Override
    public Component getCurrentFocusOwner() {
        return getCurrentNativeFocusOwner();
    }

    @Override
    public Window getCurrentFocusedWindow() {
        return getCurrentNativeFocusedWindow();
    }

    public static void setCurrentNativeFocusOwner(Component comp) {
        synchronized (lock) {
            currentFocusOwner = comp;
        }
    }

    public static Component getCurrentNativeFocusOwner() {
        synchronized(lock) {
            return currentFocusOwner;
        }
    }

    public static void setCurrentNativeFocusedWindow(Window win) {
        if (focusLog.isLoggable(PlatformLogger.FINER)) focusLog.finer("Setting current native focused window " + win);
        XWindowPeer from = null, to = null;

        synchronized(lock) {
            if (currentFocusedWindow != null) {
                from = (XWindowPeer)currentFocusedWindow.getPeer();
            }

            currentFocusedWindow = win;

            if (currentFocusedWindow != null) {
                to = (XWindowPeer)currentFocusedWindow.getPeer();
            }
        }

        if (from != null) {
            from.updateSecurityWarningVisibility();
        }
        if (to != null) {
            to.updateSecurityWarningVisibility();
        }
    }

    public static Window getCurrentNativeFocusedWindow() {
        synchronized(lock) {
            return currentFocusedWindow;
        }
    }

    // TODO: do something to eliminate this forwarding
    public static boolean deliverFocus(Component lightweightChild,
                                       Component target,
                                       boolean temporary,
                                       boolean focusedWindowChangeAllowed,
                                       long time,
                                       CausedFocusEvent.Cause cause)
    {
        return KeyboardFocusManagerPeerImpl.deliverFocus(lightweightChild,
                                                         target,
                                                         temporary,
                                                         focusedWindowChangeAllowed,
                                                         time,
                                                         cause,
                                                         getCurrentNativeFocusOwner());
    }
}
