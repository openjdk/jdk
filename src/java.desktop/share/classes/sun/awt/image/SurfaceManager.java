/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.image;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.ImageCapabilities;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import sun.java2d.InvalidPipeException;
import sun.java2d.SurfaceData;
import sun.java2d.SurfaceDataProxy;

/**
 * The abstract base class that manages the various SurfaceData objects that
 * represent an Image's contents.  Subclasses can customize how the surfaces
 * are organized, whether to cache the original contents in an accelerated
 * surface, and so on.
 * <p>
 * The SurfaceManager also maintains an arbitrary "cache" mechanism which
 * allows other agents to store data in it specific to their use of this
 * image.  The most common use of the caching mechanism is for destination
 * SurfaceData objects to store cached copies of the source image.
 */
public abstract class SurfaceManager {

    public abstract static class ImageAccessor {
        public abstract SurfaceManager getSurfaceManager(Image img);
        public abstract void setSurfaceManager(Image img, SurfaceManager mgr);
    }

    private static ImageAccessor imgaccessor;

    public static void setImageAccessor(ImageAccessor ia) {
        if (imgaccessor != null) {
            throw new InternalError("Attempt to set ImageAccessor twice");
        }
        imgaccessor = ia;
    }

    /**
     * Returns the SurfaceManager object contained within the given Image.
     */
    public static SurfaceManager getManager(Image img) {
        SurfaceManager sMgr = imgaccessor.getSurfaceManager(img);
        if (sMgr == null) {
            /*
             * In practice only a BufferedImage will get here.
             */
            try {
                BufferedImage bi = (BufferedImage) img;
                sMgr = new BufImgSurfaceManager(bi);
                setManager(bi, sMgr);
            } catch (ClassCastException e) {
                throw new InvalidPipeException("Invalid Image variant");
            }
        }
        return sMgr;
    }

    public static void setManager(Image img, SurfaceManager mgr) {
        imgaccessor.setSurfaceManager(img, mgr);
    }

    /**
     * This map holds references to SurfaceDataProxy per given ProxyCache.
     * Unlike ProxyCache, which contains SurfaceDataProxy objects per given SurfaceManager,
     * this map does not prevent contained proxies from being garbage collected.
     * Therefore, ProxyCache can be considered an "owning" container for the SurfaceDataProxy objects,
     * and this map is just a weak mapping for the bookkeeping purposes.
     */
    private final Map<ProxyCache, WeakReference<SurfaceDataProxy>> weakCache = new WeakHashMap<>(2);

    /**
     * Returns the main SurfaceData object that "owns" the pixels for
     * this SurfaceManager.  This SurfaceData is used as the destination
     * surface in a rendering operation and is the most authoritative
     * storage for the current state of the pixels, though other
     * versions might be cached in other locations for efficiency.
     */
    public abstract SurfaceData getPrimarySurfaceData();

    /**
     * Restores the primary surface being managed, and then returns the
     * replacement surface.  This is called when an accelerated surface has
     * been "lost", in an attempt to auto-restore its contents.
     */
    public abstract SurfaceData restoreContents();

    /**
     * Notification that any accelerated surfaces associated with this manager
     * have been "lost", which might mean that they need to be manually
     * restored or recreated.
     *
     * The default implementation does nothing, but platform-specific
     * variants which have accelerated surfaces should perform any necessary
     * actions.
     */
    public void acceleratedSurfaceLost() {}

    /**
     * Returns an ImageCapabilities object which can be
     * inquired as to the specific capabilities of this
     * Image.  The capabilities object will return true for
     * isAccelerated() if the image has a current and valid
     * SurfaceDataProxy object cached for the specified
     * GraphicsConfiguration parameter.
     * <p>
     * This class provides a default implementation of the
     * ImageCapabilities that will try to determine if there
     * is an associated SurfaceDataProxy object and if it is
     * up to date, but only works for GraphicsConfiguration
     * objects which implement the ProxiedGraphicsConfig
     * interface defined below.  In practice, all configs
     * which can be accelerated are currently implementing
     * that interface.
     * <p>
     * A null GraphicsConfiguration returns a value based on whether the
     * image is currently accelerated on its default GraphicsConfiguration.
     *
     * @see java.awt.Image#getCapabilities
     * @since 1.5
     */
    public ImageCapabilities getCapabilities(GraphicsConfiguration gc) {
        return new ImageCapabilitiesGc(gc);
    }

    class ImageCapabilitiesGc extends ImageCapabilities {
        GraphicsConfiguration gc;

        public ImageCapabilitiesGc(GraphicsConfiguration gc) {
            super(false);
            this.gc = gc;
        }

        public boolean isAccelerated() {
            // Note that when img.getAccelerationPriority() gets set to 0
            // we remove SurfaceDataProxy objects from the cache and the
            // answer will be false.
            GraphicsConfiguration tmpGc = gc;
            if (tmpGc == null) {
                tmpGc = GraphicsEnvironment.getLocalGraphicsEnvironment().
                    getDefaultScreenDevice().getDefaultConfiguration();
            }
            if (tmpGc instanceof ProxiedGraphicsConfig pgc) {
                ProxyCache cache = pgc.getSurfaceDataProxyCache();
                if (cache != null) {
                    SurfaceDataProxy sdp = cache.get(SurfaceManager.this);
                    return (sdp != null && sdp.isAccelerated());
                }
            }
            return false;
        }
    }

    /**
     * An interface for GraphicsConfiguration objects to implement if
     * they create their own VolatileSurfaceManager implementations.
     */
    public interface Factory {

        /**
         * Creates a new instance of a VolatileSurfaceManager given a
         * compatible SunVolatileImage.
         * An optional context Object can be supplied as a way for the caller
         * to pass pipeline-specific context data to the VolatileSurfaceManager
         * (such as a backbuffer handle, for example).
         */
        VolatileSurfaceManager createVolatileManager(SunVolatileImage image,
                                                     Object context);
    }

    /**
     * An interface for GraphicsConfiguration objects to implement if
     * their surfaces accelerate images using SurfaceDataProxy objects.
     *
     * Implementing this interface facilitates the default
     * implementation of getImageCapabilities() above.
     */
    public interface ProxiedGraphicsConfig {

        /**
         * Return the cache that destination surfaces created on the
         * given GraphicsConfiguration use to store SurfaceDataProxy
         * objects for their cached copies.
         */
        ProxyCache getSurfaceDataProxyCache();
    }

    public static class ProxyCache {
        private final Map<SurfaceManager, SurfaceDataProxy> map =
                Collections.synchronizedMap(new WeakHashMap<>());

        /**
         * Return a cached SurfaceDataProxy object for a given SurfaceManager.
         * <p>
         * Note that the cache is maintained as a simple Map with no
         * attempts to keep it up to date or invalidate it so any data
         * stored here must either not be dependent on the state of the
         * image or it must be individually tracked to see if it is
         * outdated or obsolete.
         * <p>
         * The SurfaceData object of the primary (destination) surface
         * has a StateTracker mechanism which can help track the validity
         * and "currentness" of any data stored here.
         * For convenience and expediency an object stored as cached
         * data may implement the FlushableCacheData interface specified
         * below so that it may be notified immediately if the flush()
         * method is ever called.
         */
        public SurfaceDataProxy get(SurfaceManager manager) {
            return map.get(manager);
        }

        /**
         * Store a cached SurfaceDataProxy object for a given SurfaceManager.
         * See the get() method for notes on tracking the
         * validity of data stored using this mechanism.
         */
        public void put(SurfaceManager manager, SurfaceDataProxy proxy) {
            synchronized (manager.weakCache) { // Synchronize on weakCache first!
                manager.weakCache.put(this, new WeakReference<>(proxy));
                map.put(manager, proxy);
            }
        }
    }

    /**
     * Releases system resources in use by ancillary SurfaceData objects,
     * such as surfaces cached in accelerated memory.  Subclasses should
     * override to release any of their flushable data.
     * <p>
     * The default implementation will visit all of the value objects
     * in the cacheMap and flush them if they implement the
     * FlushableCacheData interface.
     */
    public synchronized void flush() {
        flush(false);
    }

    void flush(boolean deaccelerate) {
        synchronized (weakCache) {
            Iterator<WeakReference<SurfaceDataProxy>> i =
                    weakCache.values().iterator();
            while (i.hasNext()) {
                SurfaceDataProxy sdp = i.next().get();
                if (sdp == null || sdp.flush(deaccelerate)) {
                    i.remove();
                }
            }
        }
    }

    /**
     * An interface for Objects used in the SurfaceManager cache
     * to implement if they have data that should be flushed when
     * the Image is flushed.
     */
    public static interface FlushableCacheData {
        /**
         * Flush all cached resources.
         * The deaccelerated parameter indicates if the flush is
         * happening because the associated surface is no longer
         * being accelerated (for instance the acceleration priority
         * is set below the threshold needed for acceleration).
         * Returns a boolean that indicates if the cached object is
         * no longer needed and should be removed from the cache.
         */
        public boolean flush(boolean deaccelerated);
    }

    /**
     * Called when image's acceleration priority is changed.
     * <p>
     * The default implementation will visit all of the value objects
     * in the cacheMap when the priority gets set to 0.0 and flush them
     * if they implement the FlushableCacheData interface.
     */
    public void setAccelerationPriority(float priority) {
        if (priority == 0.0f) {
            flush(true);
        }
    }

    /**
     * Returns a horizontal scale factor of the image. This is utility method,
     * which fetches information from the SurfaceData of the image.
     *
     * @see SurfaceData#getDefaultScaleX
     */
    public static double getImageScaleX(final Image img) {
        if (!(img instanceof VolatileImage)) {
            return 1;
        }
        final SurfaceManager sm = getManager(img);
        return sm.getPrimarySurfaceData().getDefaultScaleX();
    }

    /**
     * Returns a vertical scale factor of the image. This is utility method,
     * which fetches information from the SurfaceData of the image.
     *
     * @see SurfaceData#getDefaultScaleY
     */
    public static double getImageScaleY(final Image img) {
        if (!(img instanceof VolatileImage)) {
            return 1;
        }
        final SurfaceManager sm = getManager(img);
        return sm.getPrimarySurfaceData().getDefaultScaleY();
    }
}
