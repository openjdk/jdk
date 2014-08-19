/*
 * Copyright (c) 2004, 2006, Oracle and/or its affiliates. All rights reserved.
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
package sun.swing;

import java.awt.*;
import java.awt.image.*;
import java.util.*;

/**
 * A base class used for icons or images that are expensive to paint.
 * A subclass will do the following:
 * <ol>
 * <li>Invoke <code>paint</code> when you want to paint the image,
 *     if you are implementing <code>Icon</code> you'll invoke this from
 *     <code>paintIcon</code>.
 *     The args argument is useful when additional state is needed.
 * <li>Override <code>paintToImage</code> to render the image.  The code that
 *     lives here is equivalent to what previously would go in
 *     <code>paintIcon</code>, for an <code>Icon</code>.
 * </ol>
 * The two ways to use this class are:
 * <ol>
 * <li>Invoke <code>paint</code> to draw the cached reprensentation at
 *     the specified location.
 * <li>Invoke <code>getImage</code> to get the cached reprensentation and
 *     draw the image yourself.  This is primarly useful when you are not
 *     using <code>VolatileImage</code>.
 * </ol>
 *
 *
 */
public abstract class CachedPainter {
    // CacheMap maps from class to ImageCache.
    private static final Map<Object,ImageCache> cacheMap =
                   new HashMap<Object,ImageCache>();


    private static ImageCache getCache(Object key) {
        synchronized(CachedPainter.class) {
            ImageCache cache = cacheMap.get(key);
            if (cache == null) {
                cache = new ImageCache(1);
                cacheMap.put(key, cache);
            }
            return cache;
        }
    }

    /**
     * Creates an instance of <code>CachedPainter</code> that will cache up
     * to <code>cacheCount</code> images of this class.
     *
     * @param cacheCount Max number of images to cache
     */
    public CachedPainter(int cacheCount) {
        getCache(getClass()).setMaxCount(cacheCount);
    }

    /**
     * Renders the cached image to the the passed in <code>Graphic</code>.
     * If there is no cached image <code>paintToImage</code> will be invoked.
     * <code>paintImage</code> is invoked to paint the cached image.
     *
     * @param c Component rendering to, this may be null.
     * @param g Graphics to paint to
     * @param x X-coordinate to render to
     * @param y Y-coordinate to render to
     * @param w Width to render in
     * @param h Height to render in
     * @param arg Variable arguments that will be passed to paintToImage
     */
    public void paint(Component c, Graphics g, int x,
                         int y, int w, int h, Object... args) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (c != null) {
            synchronized(c.getTreeLock()) {
                synchronized(CachedPainter.class) {
                    // If c is non-null, synchronize on the tree lock.
                    // This is necessary because asking for the
                    // GraphicsConfiguration will grab a tree lock.
                    paint0(c, g, x, y, w, h, args);
                }
            }
        }
        else {
            synchronized(CachedPainter.class) {
                paint0(c, g, x, y, w, h, args);
            }
        }
    }

    private void paint0(Component c, Graphics g, int x,
                         int y, int w, int h, Object... args) {
        Object key = getClass();
        GraphicsConfiguration config = getGraphicsConfiguration(c);
        ImageCache cache = getCache(key);
        Image image = cache.getImage(key, config, w, h, args);
        int attempts = 0;
        do {
            boolean draw = false;
            if (image instanceof VolatileImage) {
                // See if we need to recreate the image
                switch (((VolatileImage)image).validate(config)) {
                case VolatileImage.IMAGE_INCOMPATIBLE:
                    ((VolatileImage)image).flush();
                    image = null;
                    break;
                case VolatileImage.IMAGE_RESTORED:
                    draw = true;
                    break;
                }
            }
            if (image == null) {
                // Recreate the image
                image = createImage(c, w, h, config, args);
                cache.setImage(key, config, w, h, args, image);
                draw = true;
            }
            if (draw) {
                // Render to the Image
                Graphics g2 = image.getGraphics();
                paintToImage(c, image, g2, w, h, args);
                g2.dispose();
            }

            // Render to the passed in Graphics
            paintImage(c, g, x, y, w, h, image, args);

            // If we did this 3 times and the contents are still lost
            // assume we're painting to a VolatileImage that is bogus and
            // give up.  Presumably we'll be called again to paint.
        } while ((image instanceof VolatileImage) &&
                 ((VolatileImage)image).contentsLost() && ++attempts < 3);
    }

    /**
     * Paints the representation to cache to the supplied Graphics.
     *
     * @param c Component painting to, may be null.
     * @param image Image to paint to
     * @param g Graphics to paint to, obtained from the passed in Image.
     * @param w Width to paint to
     * @param h Height to paint to
     * @param args Arguments supplied to <code>paint</code>
     */
    protected abstract void paintToImage(Component c, Image image, Graphics g,
                                         int w, int h, Object[] args);


    /**
     * Paints the image to the specified location.
     *
     * @param c Component painting to
     * @param g Graphics to paint to
     * @param x X coordinate to paint to
     * @param y Y coordinate to paint to
     * @param w Width to paint to
     * @param h Height to paint to
     * @param image Image to paint
     * @param args Arguments supplied to <code>paint</code>
     */
    protected void paintImage(Component c, Graphics g,
                              int x, int y, int w, int h, Image image,
                              Object[] args) {
        g.drawImage(image, x, y, null);
    }

    /**
     * Creates the image to cache.  This returns an opaque image, subclasses
     * that require translucency or transparency will need to override this
     * method.
     *
     * @param c Component painting to
     * @param w Width of image to create
     * @param h Height to image to create
     * @param config GraphicsConfiguration that will be
     *        rendered to, this may be null.
     * @param args Arguments passed to paint
     */
    protected Image createImage(Component c, int w, int h,
                                GraphicsConfiguration config, Object[] args) {
        if (config == null) {
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }
        return config.createCompatibleVolatileImage(w, h);
    }

    /**
     * Clear the image cache
     */
    protected void flush() {
        synchronized(CachedPainter.class) {
            getCache(getClass()).flush();
        }
    }

    private GraphicsConfiguration getGraphicsConfiguration(Component c) {
        if (c == null) {
            return null;
        }
        return c.getGraphicsConfiguration();
    }
}
