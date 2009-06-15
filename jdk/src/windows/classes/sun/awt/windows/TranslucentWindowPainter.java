/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.windows;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.VolatileImage;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import sun.awt.image.BufImgSurfaceData;
import sun.java2d.DestSurfaceProvider;
import sun.java2d.InvalidPipeException;
import sun.java2d.Surface;
import sun.java2d.pipe.RenderQueue;
import sun.java2d.pipe.hw.AccelGraphicsConfig;
import sun.java2d.pipe.hw.AccelSurface;
import sun.security.action.GetPropertyAction;

import static java.awt.image.VolatileImage.*;
import static java.awt.Transparency.*;
import static sun.java2d.pipe.hw.AccelSurface.*;
import static sun.java2d.pipe.hw.ContextCapabilities.*;

/**
 * This class handles the updates of the non-opaque windows.
 * The window associated with the peer is updated either given an image or
 * the window is repainted to an internal buffer which is then used to update
 * the window.
 *
 * Note: this class does not attempt to be thread safe, it is expected to be
 * called from a single thread (EDT).
 */
public abstract class TranslucentWindowPainter {

    protected Window window;
    protected WWindowPeer peer;

    // REMIND: we probably would want to remove this later
    private static final boolean forceOpt  =
        Boolean.valueOf(AccessController.doPrivileged(
            new GetPropertyAction("sun.java2d.twp.forceopt", "false")));
    private static final boolean forceSW  =
        Boolean.valueOf(AccessController.doPrivileged(
            new GetPropertyAction("sun.java2d.twp.forcesw", "false")));

    /**
     * Creates an instance of the painter for particular peer.
     */
    public static TranslucentWindowPainter createInstance(WWindowPeer peer) {
        GraphicsConfiguration gc = peer.getGraphicsConfiguration();
        if (!forceSW && gc instanceof AccelGraphicsConfig) {
            String gcName = gc.getClass().getSimpleName();
            AccelGraphicsConfig agc = (AccelGraphicsConfig)gc;
            // this is a heuristic to check that we have a pcix board
            // (those have higher transfer rate from gpu to cpu)
            if ((agc.getContextCapabilities().getCaps() & CAPS_PS30) != 0 ||
                forceOpt)
            {
                // we check for name to avoid loading classes unnecessarily if
                // a pipeline isn't enabled
                if (gcName.startsWith("D3D")) {
                    return new VIOptD3DWindowPainter(peer);
                } else if (forceOpt && gcName.startsWith("WGL")) {
                    // on some boards (namely, ATI, even on pcix bus) ogl is
                    // very slow reading pixels back so for now it is disabled
                    // unless forced
                    return new VIOptWGLWindowPainter(peer);
                }
            }
        }
        return new BIWindowPainter(peer);
    }

    protected TranslucentWindowPainter(WWindowPeer peer) {
        this.peer = peer;
        this.window = (Window)peer.getTarget();
    }

    /**
     * Creates (if needed), clears (if requested) and returns the buffer
     * for this painter.
     */
    protected abstract Image getBackBuffer(boolean clear);

    /**
     * Updates the the window associated with this painter with the contents
     * of the passed image.
     * The image can not be null, and NPE will be thrown if it is.
     */
    protected abstract boolean update(Image bb);

    /**
     * Flushes the resources associated with the painter. They will be
     * recreated as needed.
     */
    public abstract void flush();

    /**
     * Updates the window associated with the painter.
     *
     * @param repaint indicates if the window should be completely repainted
     * to the back buffer using {@link java.awt.Window#paintAll} before update.
     */
    public void updateWindow(boolean repaint) {
        boolean done = false;
        Image bb = getBackBuffer(repaint);
        while (!done) {
            if (repaint) {
                Graphics2D g = (Graphics2D)bb.getGraphics();
                try {
                    window.paintAll(g);
                } finally {
                    g.dispose();
                }
            }

            done = update(bb);
            if (!done) {
                repaint = true;
                bb = getBackBuffer(true);
            }
        }
    }

    private static final Image clearImage(Image bb) {
        Graphics2D g = (Graphics2D)bb.getGraphics();
        int w = bb.getWidth(null);
        int h = bb.getHeight(null);

        g.setComposite(AlphaComposite.Src);
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, w, h);

        return bb;
    }

    /**
     * A painter which uses BufferedImage as the internal buffer. The window
     * is painted into this buffer, and the contents then are uploaded
     * into the layered window.
     *
     * This painter handles all types of images passed to its paint(Image)
     * method (VI, BI, regular Images).
     */
    private static class BIWindowPainter extends TranslucentWindowPainter {
        private BufferedImage backBuffer;

        protected BIWindowPainter(WWindowPeer peer) {
            super(peer);
        }

        @Override
        protected Image getBackBuffer(boolean clear) {
            int w = window.getWidth();
            int h = window.getHeight();
            if (backBuffer == null ||
                backBuffer.getWidth() != w ||
                backBuffer.getHeight() != h)
            {
                flush();
                backBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            }
            return clear ? (BufferedImage)clearImage(backBuffer) : backBuffer;
        }

        @Override
        protected boolean update(Image bb) {
            VolatileImage viBB = null;

            if (bb instanceof BufferedImage) {
                BufferedImage bi = (BufferedImage)bb;
                int data[] =
                    ((DataBufferInt)bi.getRaster().getDataBuffer()).getData();
                peer.updateWindowImpl(data, bi.getWidth(), bi.getHeight());
                return true;
            } else if (bb instanceof VolatileImage) {
                viBB = (VolatileImage)bb;
                if (bb instanceof DestSurfaceProvider) {
                    Surface s = ((DestSurfaceProvider)bb).getDestSurface();
                    if (s instanceof BufImgSurfaceData) {
                        // the image is probably lost, upload the data from the
                        // backup surface to avoid creating another heap-based
                        // image (the parent's buffer)
                        int w = viBB.getWidth();
                        int h = viBB.getHeight();
                        BufImgSurfaceData bisd = (BufImgSurfaceData)s;
                        int data[] = ((DataBufferInt)bisd.getRaster(0,0,w,h).
                            getDataBuffer()).getData();
                        peer.updateWindowImpl(data, w, h);
                        return true;
                    }
                }
            }

            // copy the passed image into our own buffer, then upload
            BufferedImage bi = (BufferedImage)clearImage(backBuffer);

            int data[] =
                ((DataBufferInt)bi.getRaster().getDataBuffer()).getData();
            peer.updateWindowImpl(data, bi.getWidth(), bi.getHeight());

            return (viBB != null ? !viBB.contentsLost() : true);
        }

        public void flush() {
            if (backBuffer != null) {
                backBuffer.flush();
                backBuffer = null;
            }
        }
    }

    /**
     * A version of the painter which uses VolatileImage as the internal buffer.
     * The window is painted into this VI and then copied into the parent's
     * Java heap-based buffer (which is then uploaded to the layered window)
     */
    private static class VIWindowPainter extends BIWindowPainter {
        private VolatileImage viBB;

        protected VIWindowPainter(WWindowPeer peer) {
            super(peer);
        }

        @Override
        protected Image getBackBuffer(boolean clear) {
            int w = window.getWidth();
            int h = window.getHeight();
            GraphicsConfiguration gc = peer.getGraphicsConfiguration();

            if (viBB == null || viBB.getWidth() != w || viBB.getHeight() != h ||
                viBB.validate(gc) == IMAGE_INCOMPATIBLE)
            {
                flush();

                if (gc instanceof AccelGraphicsConfig) {
                    AccelGraphicsConfig agc = ((AccelGraphicsConfig)gc);
                    viBB = agc.createCompatibleVolatileImage(w, h,
                                                             TRANSLUCENT,
                                                             RT_PLAIN);
                }
                if (viBB == null) {
                    viBB = gc.createCompatibleVolatileImage(w, h, TRANSLUCENT);
                }
                viBB.validate(gc);
            }

            return clear ? clearImage(viBB) : viBB;
        }

        @Override
        public void flush() {
            if (viBB != null) {
                viBB.flush();
                viBB = null;
            }
        }
    }

    /**
     * Optimized version of hw painter. Uses VolatileImages for the
     * buffer, and uses an optimized path to pull the data from those into
     * the layered window, bypassing Java heap-based image.
     */
    private abstract static class VIOptWindowPainter extends VIWindowPainter {

        protected VIOptWindowPainter(WWindowPeer peer) {
            super(peer);
        }

        protected abstract boolean updateWindowAccel(long psdops, int w, int h);

        @Override
        protected boolean update(Image bb) {
            if (bb instanceof DestSurfaceProvider) {
                Surface s = ((DestSurfaceProvider)bb).getDestSurface();
                if (s instanceof AccelSurface) {
                    final int w = bb.getWidth(null);
                    final int h = bb.getHeight(null);
                    final boolean arr[] = { false };
                    final AccelSurface as = (AccelSurface)s;
                    RenderQueue rq = as.getContext().getRenderQueue();
                    rq.lock();
                    try {
                        as.getContext().validateContext(as);
                        rq.flushAndInvokeNow(new Runnable() {
                            public void run() {
                                long psdops = as.getNativeOps();
                                arr[0] = updateWindowAccel(psdops, w, h);
                            }
                        });
                    } catch (InvalidPipeException e) {
                        // ignore, false will be returned
                    } finally {
                        rq.unlock();
                    }
                    return arr[0];
                }
            }
            return super.update(bb);
        }
    }

    private static class VIOptD3DWindowPainter extends VIOptWindowPainter {

        protected VIOptD3DWindowPainter(WWindowPeer peer) {
            super(peer);
        }

        @Override
        protected boolean updateWindowAccel(long psdops, int w, int h) {
            // note: this method is executed on the toolkit thread, no sync is
            // necessary at the native level, and a pointer to peer can be used
            return sun.java2d.d3d.D3DSurfaceData.
                updateWindowAccelImpl(psdops, peer.getData(), w, h);
        }
    }

    private static class VIOptWGLWindowPainter extends VIOptWindowPainter {

        protected VIOptWGLWindowPainter(WWindowPeer peer) {
            super(peer);
        }

        @Override
        protected boolean updateWindowAccel(long psdops, int w, int h) {
            // note: part of this method which deals with GDI will be on the
            // toolkit thread
            return sun.java2d.opengl.WGLSurfaceData.
                updateWindowAccelImpl(psdops, peer, w, h);
        }
    }
}
