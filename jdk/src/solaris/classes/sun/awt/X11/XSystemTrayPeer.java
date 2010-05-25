/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.X11;

import java.awt.*;
import java.awt.peer.SystemTrayPeer;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import sun.awt.SunToolkit;
import sun.awt.AppContext;
import sun.util.logging.PlatformLogger;

public class XSystemTrayPeer implements SystemTrayPeer, XMSelectionListener {
    private static final PlatformLogger log = PlatformLogger.getLogger("sun.awt.X11.XSystemTrayPeer");

    SystemTray target;
    static XSystemTrayPeer peerInstance; // there is only one SystemTray peer per application

    private volatile boolean available;
    private final XMSelection selection = new XMSelection("_NET_SYSTEM_TRAY");

    private static final Method firePropertyChangeMethod =
        XToolkit.getMethod(SystemTray.class, "firePropertyChange", new Class[] {String.class, Object.class, Object.class});
    private static final Method addNotifyMethod = XToolkit.getMethod(TrayIcon.class, "addNotify", null);
    private static final Method removeNotifyMethod = XToolkit.getMethod(TrayIcon.class, "removeNotify", null);

    private static final int SCREEN = 0;
    private static final String SYSTEM_TRAY_PROPERTY_NAME = "systemTray";
    private static final XAtom _NET_SYSTEM_TRAY = XAtom.get("_NET_SYSTEM_TRAY_S" + SCREEN);
    private static final XAtom _XEMBED_INFO = XAtom.get("_XEMBED_INFO");
    private static final XAtom _NET_SYSTEM_TRAY_OPCODE = XAtom.get("_NET_SYSTEM_TRAY_OPCODE");
    private static final XAtom _NET_WM_ICON = XAtom.get("_NET_WM_ICON");
    private static final long SYSTEM_TRAY_REQUEST_DOCK = 0;

    XSystemTrayPeer(SystemTray target) {
        this.target = target;
        peerInstance = this;

        selection.addSelectionListener(this);

        long selection_owner = selection.getOwner(SCREEN);
        available = (selection_owner != XConstants.None);

        log.fine(" check if system tray is available. selection owner: " + selection_owner);
    }

    public void ownerChanged(int screen, XMSelection sel, long newOwner, long data, long timestamp) {
        if (screen != SCREEN) {
            return;
        }
        if (!available) {
            available = true;
            firePropertyChange(SYSTEM_TRAY_PROPERTY_NAME, null, target);
        } else {
            removeTrayPeers();
        }
        createTrayPeers();
    }

    public void ownerDeath(int screen, XMSelection sel, long deadOwner) {
        if (screen != SCREEN) {
            return;
        }
        if (available) {
            available = false;
            firePropertyChange(SYSTEM_TRAY_PROPERTY_NAME, target, null);
            removeTrayPeers();
        }
    }

    public void selectionChanged(int screen, XMSelection sel, long owner, XPropertyEvent event) {
    }

    public Dimension getTrayIconSize() {
        return new Dimension(XTrayIconPeer.TRAY_ICON_HEIGHT, XTrayIconPeer.TRAY_ICON_WIDTH);
    }

    boolean isAvailable() {
        return available;
    }

    void dispose() {
        selection.removeSelectionListener(this);
    }

    // ***********************************************************************
    // ***********************************************************************

    void addTrayIcon(XTrayIconPeer tiPeer) throws AWTException {
        long selection_owner = selection.getOwner(SCREEN);

        log.fine(" send SYSTEM_TRAY_REQUEST_DOCK message to owner: " + selection_owner);

        if (selection_owner == XConstants.None) {
            throw new AWTException("TrayIcon couldn't be displayed.");
        }

        long tray_window = tiPeer.getWindow();
        long data[] = new long[] {XEmbedHelper.XEMBED_VERSION, XEmbedHelper.XEMBED_MAPPED};
        long data_ptr = Native.card32ToData(data);

        _XEMBED_INFO.setAtomData(tray_window, data_ptr, data.length);

        sendMessage(selection_owner, SYSTEM_TRAY_REQUEST_DOCK, tray_window, 0, 0);
    }

    void sendMessage(long win, long msg, long data1, long data2, long data3) {
        XClientMessageEvent xev = new XClientMessageEvent();

        try {
            xev.set_type(XConstants.ClientMessage);
            xev.set_window(win);
            xev.set_format(32);
            xev.set_message_type(_NET_SYSTEM_TRAY_OPCODE.getAtom());
            xev.set_data(0, 0);
            xev.set_data(1, msg);
            xev.set_data(2, data1);
            xev.set_data(3, data2);
            xev.set_data(4, data3);

            XToolkit.awtLock();
            try {
                XlibWrapper.XSendEvent(XToolkit.getDisplay(), win, false,
                                       XConstants.NoEventMask, xev.pData);
            } finally {
                XToolkit.awtUnlock();
            }
        } finally {
            xev.dispose();
        }
    }

    static XSystemTrayPeer getPeerInstance() {
        return peerInstance;
    }

    private void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
        Runnable runnable = new Runnable() {
                public void run() {
                    Object[] args = new Object[] {propertyName, oldValue, newValue};
                    invokeMethod(firePropertyChangeMethod, target, args);
                }
            };
        invokeOnEachAppContext(runnable);
    }

    private void createTrayPeers() {
        invokeOnEachTrayIcon(addNotifyMethod);
    }

    private void removeTrayPeers() {
        invokeOnEachTrayIcon(removeNotifyMethod);
    }

    private void invokeOnEachTrayIcon(final Method method) {
        Runnable runnable = new Runnable() {
                public void run() {
                    TrayIcon[] icons = target.getTrayIcons();
                    for (TrayIcon ti : icons) {
                        invokeMethod(method, ti, (Object[]) null);
                    }
                }
            };
        invokeOnEachAppContext(runnable);
    }

    private void invokeMethod(Method method, Object obj, Object[] args) {
        try{
            method.invoke(obj, args);
        } catch (InvocationTargetException e){
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void invokeOnEachAppContext(Runnable runnable) {
        for (AppContext appContext : AppContext.getAppContexts()) {
            SunToolkit.invokeLaterOnAppContext(appContext, runnable);
        }
    }

}
