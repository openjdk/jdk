/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import sun.lwawt.LWToolkit;
import sun.lwawt.LWWindowPeer;
import sun.lwawt.macosx.CocoaConstants;
import sun.lwawt.macosx.event.NSEvent;

import sun.awt.EmbeddedFrame;

import java.awt.*;
import java.awt.event.*;

public class CEmbeddedFrame extends EmbeddedFrame {

    private CPlatformResponder responder;
    private boolean focused = true;
    private boolean parentWindowActive = true;

    public CEmbeddedFrame() {
        show();
    }

    public void addNotify() {
        if (getPeer() == null) {
            LWToolkit toolkit = (LWToolkit)Toolkit.getDefaultToolkit();
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
                               String charsIgnoringMods, boolean isRepeat, short keyCode) {
        responder.handleKeyEvent(eventType, modifierFlags, charsIgnoringMods, keyCode);
    }

    public void handleInputEvent(String text) {
        new RuntimeException("Not implemented");
    }

    public void handleFocusEvent(boolean focused) {
        this.focused = focused;
        updateOverlayWindowActiveState();
    }

    public void handleWindowFocusEvent(boolean parentWindowActive) {
        this.parentWindowActive = parentWindowActive;
        updateOverlayWindowActiveState();
    }

    public boolean isParentWindowActive() {
        return parentWindowActive;
    }

    /*
     * May change appearance of contents of window, and generate a
     * WINDOW_ACTIVATED event.
     */
    private void updateOverlayWindowActiveState() {
        final boolean showAsFocused = parentWindowActive && focused;
        dispatchEvent(
            new FocusEvent(this, showAsFocused ?
                                 FocusEvent.FOCUS_GAINED :
                                 FocusEvent.FOCUS_LOST));
     }

}
