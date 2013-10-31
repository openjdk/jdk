/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.geom.Rectangle2D;

import sun.awt.CGraphicsConfig;
import sun.awt.CGraphicsEnvironment;
import sun.lwawt.LWWindowPeer;
import sun.lwawt.macosx.event.NSEvent;

import sun.java2d.SurfaceData;
import sun.java2d.opengl.CGLLayer;
import sun.java2d.opengl.CGLSurfaceData;

public class CPlatformView extends CFRetainedResource {
    private native long nativeCreateView(int x, int y, int width, int height, long windowLayerPtr);
    private static native void nativeSetAutoResizable(long awtView, boolean toResize);
    private static native int nativeGetNSViewDisplayID(long awtView);
    private static native Rectangle2D nativeGetLocationOnScreen(long awtView);
    private static native boolean nativeIsViewUnderMouse(long ptr);

    private LWWindowPeer peer;
    private SurfaceData surfaceData;
    private CGLLayer windowLayer;
    private CPlatformResponder responder;

    public CPlatformView() {
        super(0, true);
    }

    public void initialize(LWWindowPeer peer, CPlatformResponder responder) {
        initializeBase(peer, responder);

        if (!LWCToolkit.getSunAwtDisableCALayers()) {
            this.windowLayer = createCGLayer();
        }
        setPtr(nativeCreateView(0, 0, 0, 0, getWindowLayerPtr()));
    }

    public CGLLayer createCGLayer() {
        return new CGLLayer(peer);
    }

    protected void initializeBase(LWWindowPeer peer, CPlatformResponder responder) {
        this.peer = peer;
        this.responder = responder;
    }

    public long getAWTView() {
        return ptr;
    }

    public boolean isOpaque() {
        return !peer.isTranslucent();
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

    public void setToolTip(String msg) {
        CWrapper.NSView.setToolTip(ptr, msg);
    }

    // ----------------------------------------------------------------------
    // PAINTING METHODS
    // ----------------------------------------------------------------------
    public SurfaceData replaceSurfaceData() {
        if (!LWCToolkit.getSunAwtDisableCALayers()) {
            surfaceData = windowLayer.replaceSurfaceData();
        } else {
            if (surfaceData == null) {
                CGraphicsConfig graphicsConfig = (CGraphicsConfig)getGraphicsConfiguration();
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

    public void setAutoResizable(boolean toResize) {
        nativeSetAutoResizable(this.getAWTView(), toResize);
    }

    public boolean isUnderMouse() {
        return nativeIsViewUnderMouse(getAWTView());
    }

    public GraphicsDevice getGraphicsDevice() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        CGraphicsEnvironment cge = (CGraphicsEnvironment)ge;
        int displayID = nativeGetNSViewDisplayID(getAWTView());
        GraphicsDevice gd = cge.getScreenDevice(displayID);
        if (gd == null) {
            // this could possibly happen during device removal
            // use the default screen device in this case
            gd = ge.getDefaultScreenDevice();
        }
        return gd;
    }

    public Point getLocationOnScreen() {
        Rectangle r = nativeGetLocationOnScreen(this.getAWTView()).getBounds();
        return new Point(r.x, r.y);
    }

    // ----------------------------------------------------------------------
    // NATIVE CALLBACKS
    // ----------------------------------------------------------------------

    /*
     * The callback is called only in the embedded case when the view is
     * automatically resized by the superview.
     * In normal mode this method is never called.
     */
    private void deliverResize(int x, int y, int w, int h) {
        peer.notifyReshape(x, y, w, h);
    }


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
                                 event.getCharactersIgnoringModifiers(), event.getKeyCode(), true, false);
    }

    /**
     * Called by the native delegate in layer backed view mode or in the simple
     * NSView mode. See NSView.drawRect().
     */
    private void deliverWindowDidExposeEvent() {
        peer.notifyExpose(peer.getSize());
    }
}
