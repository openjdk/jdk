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

import java.awt.*;
import java.awt.event.*;
import java.awt.image.VolatileImage;

import sun.awt.CGraphicsConfig;
import sun.lwawt.LWWindowPeer;
import sun.lwawt.macosx.event.NSEvent;

import sun.java2d.SurfaceData;
import sun.java2d.opengl.CGLLayer;
import sun.java2d.opengl.CGLSurfaceData;

public class CPlatformView extends CFRetainedResource {
    private native long nativeCreateView(int x, int y, int width, int height, long windowLayerPtr);

    private LWWindowPeer peer;
    private SurfaceData surfaceData;
    private CGLLayer windowLayer;
    private CPlatformResponder responder;

    public CPlatformView() {
        super(0, true);
    }

    public void initialize(LWWindowPeer peer, CPlatformResponder responder) {
        this.peer = peer;
        this.responder = responder;

        if (!LWCToolkit.getSunAwtDisableCALayers()) {
            this.windowLayer = new CGLLayer(peer);
        }
        setPtr(nativeCreateView(0, 0, 0, 0, getWindowLayerPtr()));
    }

    public long getAWTView() {
        return ptr;
    }

    public boolean isOpaque() {
        return peer.isOpaque();
    }

    /*
     * All coordinates passed to the method should be based on the origin being in the bottom-left corner (standard
     * Cocoa coordinates).
     */
    public void setBounds(int x, int y, int width, int height) {
        CWrapper.NSView.setFrame(ptr, x, y, width, height);
    }

    // REMIND: CGLSurfaceData expects top-level's size
    public Rectangle getBounds() {
        return peer.getBounds();
    }

    public Object getDestination() {
        return peer;
    }

    public void enterFullScreenMode(final long nsWindowPtr) {
        CWrapper.NSView.enterFullScreenMode(ptr);

        // REMIND: CGLSurfaceData expects top-level's size
        // and therefore we need to account insets before
        // recreating the surface data
        Insets insets = peer.getInsets();

        Rectangle screenBounds;
        final long screenPtr = CWrapper.NSWindow.screen(nsWindowPtr);
        try {
            screenBounds = CWrapper.NSScreen.frame(screenPtr).getBounds();
        } finally {
            CWrapper.NSObject.release(screenPtr);
        }

        // the move/size notification from the underlying system comes
        // but it contains a bounds smaller than the whole screen
        // and therefore we need to create the synthetic notifications
        peer.notifyReshape(screenBounds.x - insets.left,
                           screenBounds.y - insets.bottom,
                           screenBounds.width + insets.left + insets.right,
                           screenBounds.height + insets.top + insets.bottom);
    }

    public void exitFullScreenMode() {
        CWrapper.NSView.exitFullScreenMode(ptr);
    }

    // ----------------------------------------------------------------------
    // PAINTING METHODS
    // ----------------------------------------------------------------------

    public void drawImageOnPeer(VolatileImage xBackBuffer, int x1, int y1, int x2, int y2) {
        Graphics g = peer.getGraphics();
        try {
            g.drawImage(xBackBuffer, x1, y1, x2, y2, x1, y1, x2, y2, null);
        } finally {
            g.dispose();
        }
    }

    public Image createBackBuffer() {
        Rectangle r = peer.getBounds();
        Image im = null;
        if (!r.isEmpty()) {
            int transparency = (isOpaque() ? Transparency.OPAQUE : Transparency.TRANSLUCENT);
            im = peer.getGraphicsConfiguration().createCompatibleImage(r.width, r.height, transparency);
        }
        return im;
    }

    public SurfaceData replaceSurfaceData() {
        if (!LWCToolkit.getSunAwtDisableCALayers()) {
            surfaceData = windowLayer.replaceSurfaceData();
        } else {
            if (surfaceData == null) {
                CGraphicsConfig graphicsConfig = (CGraphicsConfig)peer.getGraphicsConfiguration();
                surfaceData = graphicsConfig.createSurfaceData(this);
            } else {
                validateSurface();
            }
        }
        return surfaceData;
    }

    private void validateSurface() {
        if (surfaceData != null) {
            ((CGLSurfaceData)surfaceData).validate();
        }
    }

    public GraphicsConfiguration getGraphicsConfiguration() {
        return peer.getGraphicsConfiguration();
    }

    public SurfaceData getSurfaceData() {
        return surfaceData;
    }

    @Override
    public void dispose() {
        if (!LWCToolkit.getSunAwtDisableCALayers()) {
            windowLayer.dispose();
        }
        super.dispose();
    }

    public long getWindowLayerPtr() {
        if (!LWCToolkit.getSunAwtDisableCALayers()) {
            return windowLayer.getPointer();
        } else {
            return 0;
        }
    }

    // ----------------------------------------------------------------------
    // NATIVE CALLBACKS
    // ----------------------------------------------------------------------

    private void deliverMouseEvent(NSEvent event) {
        int x = event.getX();
        int y = getBounds().height - event.getY();

        if (event.getType() == CocoaConstants.NSScrollWheel) {
            responder.handleScrollEvent(x, y, event.getModifierFlags(),
                                        event.getScrollDeltaX(), event.getScrollDeltaY());
        } else {
            responder.handleMouseEvent(event.getType(), event.getModifierFlags(), event.getButtonNumber(),
                                       event.getClickCount(), x, y, event.getAbsX(), event.getAbsY());
        }
    }

    private void deliverKeyEvent(NSEvent event) {
        responder.handleKeyEvent(event.getType(), event.getModifierFlags(),
                                 event.getCharactersIgnoringModifiers(), event.getKeyCode(), true);
    }

    private void deliverWindowDidExposeEvent() {
        Rectangle r = peer.getBounds();
        peer.notifyExpose(0, 0, r.width, r.height);
    }

    private void deliverWindowDidExposeEvent(float x, float y, float w, float h) {
        peer.notifyExpose((int)x, (int)y, (int)w, (int)h);
    }
}
