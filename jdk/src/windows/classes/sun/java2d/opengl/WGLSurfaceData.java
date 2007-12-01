/*
 * Copyright 2004-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.opengl;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.image.ColorModel;
import sun.awt.windows.WComponentPeer;
import sun.java2d.SurfaceData;
import sun.java2d.loops.SurfaceType;

public abstract class WGLSurfaceData extends OGLSurfaceData {

    protected WComponentPeer peer;
    private WGLGraphicsConfig graphicsConfig;

    private native void initOps(long pConfigInfo, long pPeerData,
                                int xoff, int yoff);
    protected native boolean initPbuffer(long pData, long pConfigInfo,
                                         boolean isOpaque,
                                         int width, int height);

    protected WGLSurfaceData(WComponentPeer peer, WGLGraphicsConfig gc,
                             ColorModel cm, int type)
    {
        super(gc, cm, type);
        this.peer = peer;
        this.graphicsConfig = gc;

        long pConfigInfo = gc.getNativeConfigInfo();
        long pPeerData = 0L;
        int xoff = 0, yoff = 0;
        if (peer != null) {
            Component c = (Component)peer.getTarget();
            if (c instanceof Container) {
                Insets insets = ((Container)c).getInsets();
                xoff = -insets.left;
                yoff = -insets.bottom;
            }
            pPeerData = peer.getData();
        }

        initOps(pConfigInfo, pPeerData, xoff, yoff);
    }

    public GraphicsConfiguration getDeviceConfiguration() {
        return graphicsConfig;
    }

    /**
     * Creates a SurfaceData object representing the primary (front) buffer
     * of an on-screen Window.
     */
    public static WGLWindowSurfaceData createData(WComponentPeer peer) {
        WGLGraphicsConfig gc = getGC(peer);
        return new WGLWindowSurfaceData(peer, gc);
    }

    /**
     * Creates a SurfaceData object representing the back buffer of a
     * double-buffered on-screen Window.
     */
    public static WGLOffScreenSurfaceData createData(WComponentPeer peer,
                                                     Image image)
    {
        WGLGraphicsConfig gc = getGC(peer);
        Rectangle r = peer.getBounds();
        return new WGLOffScreenSurfaceData(peer, gc, r.width, r.height,
                                           image, peer.getColorModel(),
                                           FLIP_BACKBUFFER);
    }

    /**
     * Creates a SurfaceData object representing an off-screen buffer (either
     * a Pbuffer or Texture).
     */
    public static WGLOffScreenSurfaceData createData(WGLGraphicsConfig gc,
                                                     int width, int height,
                                                     ColorModel cm,
                                                     Image image, int type)
    {
        return new WGLOffScreenSurfaceData(null, gc, width, height,
                                           image, cm, type);
    }

    public static WGLGraphicsConfig getGC(WComponentPeer peer) {
        if (peer != null) {
            return (WGLGraphicsConfig)peer.getGraphicsConfiguration();
        } else {
            // REMIND: this should rarely (never?) happen, but what if
            //         default config is not WGL?
            GraphicsEnvironment env =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = env.getDefaultScreenDevice();
            return (WGLGraphicsConfig)gd.getDefaultConfiguration();
        }
    }

    public static class WGLWindowSurfaceData extends WGLSurfaceData {

        public WGLWindowSurfaceData(WComponentPeer peer,
                                    WGLGraphicsConfig gc)
        {
            super(peer, gc, peer.getColorModel(), WINDOW);
        }

        public SurfaceData getReplacement() {
            return peer.getSurfaceData();
        }

        public Rectangle getBounds() {
            Rectangle r = peer.getBounds();
            r.x = r.y = 0;
            return r;
        }

        /**
         * Returns destination Component associated with this SurfaceData.
         */
        public Object getDestination() {
            return peer.getTarget();
        }
    }

    public static class WGLOffScreenSurfaceData extends WGLSurfaceData {

        private Image offscreenImage;
        private int width, height;

        public WGLOffScreenSurfaceData(WComponentPeer peer,
                                       WGLGraphicsConfig gc,
                                       int width, int height,
                                       Image image, ColorModel cm,
                                       int type)
        {
            super(peer, gc, cm, type);

            this.width = width;
            this.height = height;
            offscreenImage = image;

            initSurface(width, height);
        }

        public SurfaceData getReplacement() {
            return restoreContents(offscreenImage);
        }

        public Rectangle getBounds() {
            if (type == FLIP_BACKBUFFER) {
                Rectangle r = peer.getBounds();
                r.x = r.y = 0;
                return r;
            } else {
                return new Rectangle(width, height);
            }
        }

        /**
         * Returns destination Image associated with this SurfaceData.
         */
        public Object getDestination() {
            return offscreenImage;
        }
    }
}
