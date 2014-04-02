/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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


package sun.lwawt.macosx;

import java.awt.AWTKeyStroke;
import java.awt.Point;
import java.awt.Toolkit;

import sun.awt.EmbeddedFrame;
import sun.lwawt.LWWindowPeer;

@SuppressWarnings("serial") // JDK implementation class
public class CEmbeddedFrame extends EmbeddedFrame {

    private CPlatformResponder responder;
    private static final Object classLock = new Object();
    private static volatile CEmbeddedFrame focusedWindow;
    private boolean parentWindowActive = true;

    public CEmbeddedFrame() {
        show();
    }

    public void addNotify() {
        if (getPeer() == null) {
            LWCToolkit toolkit = (LWCToolkit)Toolkit.getDefaultToolkit();
            LWWindowPeer peer = toolkit.createEmbeddedFrame(this);
            setPeer(peer);
            responder = new CPlatformResponder(peer, true);
        }
        super.addNotify();
    }

    public void registerAccelerator(AWTKeyStroke stroke) {}

    public void unregisterAccelerator(AWTKeyStroke stroke) {}

    protected long getLayerPtr() {
        LWWindowPeer peer = (LWWindowPeer)getPeer();
        return peer.getLayerPtr();
    }

    // -----------------------------------------------------------------------
    //                          SYNTHETIC EVENT DELIVERY
    // -----------------------------------------------------------------------

    public void handleMouseEvent(int eventType, int modifierFlags, double pluginX,
                                 double pluginY, int buttonNumber, int clickCount) {
        int x = (int)pluginX;
        int y = (int)pluginY;
        Point locationOnScreen = getLocationOnScreen();
        int screenX = locationOnScreen.x + x;
        int screenY = locationOnScreen.y + y;

        if (eventType == CocoaConstants.NPCocoaEventMouseEntered) {
            CCursorManager.nativeSetAllowsCursorSetInBackground(true);
        } else if (eventType == CocoaConstants.NPCocoaEventMouseExited) {
            CCursorManager.nativeSetAllowsCursorSetInBackground(false);
        }

        responder.handleMouseEvent(eventType, modifierFlags, buttonNumber,
                                   clickCount, x, y, screenX, screenY);
    }

    public void handleScrollEvent(double pluginX, double pluginY, int modifierFlags,
                                  double deltaX, double deltaY, double deltaZ) {
        int x = (int)pluginX;
        int y = (int)pluginY;

        responder.handleScrollEvent(x, y, modifierFlags, deltaX, deltaY);
    }

    public void handleKeyEvent(int eventType, int modifierFlags, String characters,
                               String charsIgnoringMods, boolean isRepeat, short keyCode,
                               boolean needsKeyTyped) {
        responder.handleKeyEvent(eventType, modifierFlags, charsIgnoringMods, keyCode, needsKeyTyped, isRepeat);
    }

    public void handleInputEvent(String text) {
        responder.handleInputEvent(text);
    }

    // handleFocusEvent is called when the applet becames focused/unfocused.
    // This method can be called from different threads.
    public void handleFocusEvent(boolean focused) {
        synchronized (classLock) {
            // In some cases an applet may not receive the focus lost event
            // from the parent window (see 8012330)
            focusedWindow = (focused) ? this
                    : ((focusedWindow == this) ? null : focusedWindow);
        }
        if (focusedWindow == this) {
            // see bug 8010925
            // we can't put this to handleWindowFocusEvent because
            // it won't be invoced if focuse is moved to a html element
            // on the same page.
            CClipboard clipboard = (CClipboard) Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.checkPasteboard();
        }
        if (parentWindowActive) {
            responder.handleWindowFocusEvent(focused, null);
        }
    }

    /**
     * When the parent window is activated this method is called for all EmbeddedFrames in it.
     *
     * For the CEmbeddedFrame which had focus before the deactivation this method triggers
     * focus events in the following order:
     *  1. WINDOW_ACTIVATED for this EmbeddedFrame
     *  2. WINDOW_GAINED_FOCUS for this EmbeddedFrame
     *  3. FOCUS_GAINED for the most recent focus owner in this EmbeddedFrame
     *
     * The caller must not requestFocus on the EmbeddedFrame together with calling this method.
     *
     * @param parentWindowActive true if the window is activated, false otherwise
     */
    // handleWindowFocusEvent is called for all applets, when the browser
    // becomes active/inactive. This event should be filtered out for
    // non-focused applet. This method can be called from different threads.
    public void handleWindowFocusEvent(boolean parentWindowActive) {
        this.parentWindowActive = parentWindowActive;
        // ignore focus "lost" native request as it may mistakenly
        // deactivate active window (see 8001161)
        if (focusedWindow == this && parentWindowActive) {
            responder.handleWindowFocusEvent(parentWindowActive, null);
        }
    }

    public boolean isParentWindowActive() {
        return parentWindowActive;
    }
}
