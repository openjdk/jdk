/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.font.TextHitInfo;
import java.awt.im.spi.InputMethodContext;
import java.awt.peer.ComponentPeer;

import sun.awt.AWTAccessor;
import sun.awt.X11InputMethod;

import sun.util.logging.PlatformLogger;

/**
 * Input Method Adapter for XIM (without Motif)
 *
 * @author JavaSoft International
 */
public class XInputMethod extends X11InputMethod {
    private static final PlatformLogger log = PlatformLogger.getLogger("sun.awt.X11.XInputMethod");

    private InputMethodContext inputContext;

    public XInputMethod() throws AWTException {
        super();
    }

    @Override
    public void setInputMethodContext(InputMethodContext context) {
        this.inputContext = context;
        context.enableClientWindowNotification(this, true);
    }

    @Override
    public void notifyClientWindowChange(Rectangle location) {
        XComponentPeer peer = (XComponentPeer)getPeer(clientComponentWindow);
        if (peer != null) {
            adjustStatusWindow(peer.getContentWindow());
        }

        //After window moved, this would called.
        positionCandidateWindow();
    }

    @Override
    protected boolean openXIM() {
        return openXIMNative(XToolkit.getDisplay());
    }

    @Override
    protected boolean createXIC() {
        XComponentPeer peer = (XComponentPeer)getPeer(clientComponentWindow);
        if (peer == null) {
            return false;
        }
        return createXICNative(peer.getContentWindow());
    }


    private static volatile long xicFocus;

    @Override
    protected void setXICFocus(ComponentPeer peer,
                                    boolean value, boolean active) {
        if (peer == null) {
            return;
        }
        xicFocus = ((XComponentPeer)peer).getContentWindow();
        setXICFocusNative(((XComponentPeer)peer).getContentWindow(),
                          value,
                          active);
    }

    public static long getXICFocus() {
        return xicFocus;
    }

/* XAWT_HACK  FIX ME!
   do NOT call client code!
*/
    @Override
    protected Container getParent(Component client) {
        return client.getParent();
    }

    /**
     * Returns peer of the given client component. If the given client component
     * doesn't have peer, peer of the native container of the client is returned.
     */
    @Override
    protected ComponentPeer getPeer(Component client) {
        XComponentPeer peer;

        if (log.isLoggable(PlatformLogger.Level.FINE)) {
            log.fine("Client is " + client);
        }
        peer = (XComponentPeer)XToolkit.targetToPeer(client);
        while (client != null && peer == null) {
            client = getParent(client);
            peer = (XComponentPeer)XToolkit.targetToPeer(client);
        }
        if (log.isLoggable(PlatformLogger.Level.FINE)) {
            log.fine("Peer is {0}, client is {1}", peer, client);
        }

        if (peer != null)
            return peer;

        return null;
    }

    /*
     * Subclasses should override disposeImpl() instead of dispose(). Client
     * code should always invoke dispose(), never disposeImpl().
     */
    @Override
    protected synchronized void disposeImpl() {
        super.disposeImpl();
        clientComponentWindow = null;
    }

    @Override
    protected void awtLock() {
        XToolkit.awtLock();
    }

    @Override
    protected void awtUnlock() {
        XToolkit.awtUnlock();
    }

    long getCurrentParentWindow() {
        XWindow peer = AWTAccessor.getComponentAccessor()
                                  .getPeer(clientComponentWindow);
        return peer.getContentWindow();
    }

    @Override
    public void dispatchEvent(AWTEvent e) {
        switch (e.getID()) {
            case KeyEvent.KEY_PRESSED:
            case KeyEvent.KEY_RELEASED:
            case MouseEvent.MOUSE_CLICKED:
                positionCandidateWindow();
                break;

            default:
                break;
        }
    }

    private void positionCandidateWindow() {
        if (this.inputContext == null) {
            return;
        }

        Component client = getClientComponent();
        if (client == null || !client.isShowing()) {
            return;
        }

        int x = 0;
        int y = 0;

        // Get this textcomponent coordinate from root window.
        Component temp = client;
        while (temp != null) {
             Component parent = temp.getParent();
             if (parent == null) {
                 break;
             }

             x += temp.getX();
             y += temp.getY();
             temp = parent;
        }

        if (haveActiveClient()) {
            Rectangle rc = inputContext.getTextLocation(TextHitInfo.leading(0));
            x += rc.x;
            y += rc.y + rc.height;

            Point p = client.getLocationOnScreen();
            x -= p.x;
            y -= p.y;
        } else {
            Dimension size = client.getSize();
            y += size.height;
        }

        moveCandidateWindow(x, y);
    }

    /*
     * Native methods
     */
    private native boolean openXIMNative(long display);
    private native boolean createXICNative(long window);
    private native void setXICFocusNative(long window,
                                    boolean value, boolean active);
    private native void adjustStatusWindow(long window);
    private native void moveCandidateWindow(int x, int y);
}
