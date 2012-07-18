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

package sun.java2d.opengl;

import sun.lwawt.macosx.CFRetainedResource;
import sun.lwawt.LWWindowPeer;

import sun.java2d.SurfaceData;
import sun.java2d.NullSurfaceData;

import sun.awt.CGraphicsConfig;

import java.awt.Rectangle;
import java.awt.GraphicsConfiguration;
import java.awt.Transparency;

public class CGLLayer extends CFRetainedResource {

    private native long nativeCreateLayer();

    private static native void validate(long layerPtr, CGLSurfaceData cglsd);
    private static native void blitTexture(long layerPtr);

    private LWWindowPeer peer;

    private SurfaceData surfaceData; // represents intermediate buffer (texture)

    public CGLLayer(LWWindowPeer peer) {
        super(0, true);

        setPtr(nativeCreateLayer());
        this.peer = peer;
    }

    public long getPointer() {
        return ptr;
    }

    public Rectangle getBounds() {
        return peer.getBounds();
    }

    public GraphicsConfiguration getGraphicsConfiguration() {
        return peer.getGraphicsConfiguration();
    }

    public boolean isOpaque() {
        return !peer.isTranslucent();
    }

    public int getTransparency() {
        return peer.isTranslucent() ? Transparency.TRANSLUCENT :
               Transparency.OPAQUE;
    }

    public Object getDestination() {
        return peer;
    }

    public SurfaceData replaceSurfaceData() {
        if (peer.getBounds().isEmpty()) {
            surfaceData = NullSurfaceData.theInstance;
            return surfaceData;
        }

        // the layer redirects all painting to the buffer's graphics
        // and blits the buffer to the layer surface (in drawInCGLContext callback)
        CGraphicsConfig gc = (CGraphicsConfig)peer.getGraphicsConfiguration();
        surfaceData = gc.createSurfaceData(this);

        // the layer holds a reference to the buffer, which in
        // turn has a reference back to this layer
        if (surfaceData instanceof CGLSurfaceData) {
            validate((CGLSurfaceData)surfaceData);
        }

        return surfaceData;
    }

    public SurfaceData getSurfaceData() {
        return surfaceData;
    }

    public void validate(final CGLSurfaceData cglsd) {
        OGLRenderQueue rq = OGLRenderQueue.getInstance();
        rq.lock();
        try {
            validate(getPointer(), cglsd);
        } finally {
            rq.unlock();
        }
    }

    @Override
    public void dispose() {
        // break the connection between the layer and the buffer
        validate(null);
        super.dispose();
    }

    // ----------------------------------------------------------------------
    // NATIVE CALLBACKS
    // ----------------------------------------------------------------------

    private void drawInCGLContext() {
        // tell the flusher thread not to update the intermediate buffer
        // until we are done blitting from it
        OGLRenderQueue rq = OGLRenderQueue.getInstance();
        rq.lock();
        try {
            blitTexture(getPointer());
        } finally {
            rq.unlock();
        }
    }
}
