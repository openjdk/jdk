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

package com.apple.laf;

import java.awt.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.locks.*;

import apple.laf.JRSUIState;
import com.apple.laf.AquaUtils.RecyclableSingleton;

/**
 * ImageCache - A fixed pixel count sized cache of Images keyed by arbitrary set of arguments. All images are held with
 * SoftReferences so they will be dropped by the GC if heap memory gets tight. When our size hits max pixel count least
 * recently requested images are removed first.
 */
class ImageCache {
    // Ordered Map keyed by args hash, ordered by most recent accessed entry.
    private final LinkedHashMap<Integer, PixelCountSoftReference> map = new LinkedHashMap<Integer, PixelCountSoftReference>(16, 0.75f, true);

    // Maximum number of pixels to cache, this is used if maxCount
    private final int maxPixelCount;
    // The current number of pixels stored in the cache
    private int currentPixelCount = 0;

    // Lock for concurrent access to map
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    // Reference queue for tracking lost softreferences to images in the cache
    private final ReferenceQueue<Image> referenceQueue = new ReferenceQueue<Image>();

    // Singleton Instance
    private static final RecyclableSingleton<ImageCache> instance = new RecyclableSingleton<ImageCache>() {
        @Override
        protected ImageCache getInstance() {
            return new ImageCache();
        }
    };
    static ImageCache getInstance() {
        return instance.get();
    }

    public ImageCache(final int maxPixelCount) {
        this.maxPixelCount = maxPixelCount;
    }

    public ImageCache() {
        this((8 * 1024 * 1024) / 4); // 8Mb of pixels
    }

    public void flush() {
        lock.writeLock().lock();
        try {
            map.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Image getImage(final GraphicsConfiguration config, final int w, final int h, final JRSUIState state) {
        lock.readLock().lock();
        try {
            final PixelCountSoftReference ref = map.get(hash(config, w, h, state));
            // check reference has not been lost and the key truly matches, in case of false positive hash match
            if (ref != null && ref.equals(config, w, h, state)) return ref.get();
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Sets the cached image for the specified constraints.
     *
     * @param image  The image to store in cache
     * @param config The graphics configuration, needed if cached image is a Volatile Image. Used as part of cache key
     * @param w      The image width, used as part of cache key
     * @param h      The image height, used as part of cache key
     * @param args   Other arguments to use as part of the cache key
     * @return true if the image could be cached or false if the image is too big
     */
    public boolean setImage(final Image image, final GraphicsConfiguration config, final int w, final int h, final JRSUIState state) {
        final int hash = hash(config, w, h, state);

        lock.writeLock().lock();
        try {
            PixelCountSoftReference ref = map.get(hash);
            // check if currently in map
            if (ref != null && ref.get() == image) return true;

            // clear out old
            if (ref != null) {
                currentPixelCount -= ref.pixelCount;
                map.remove(hash);
            }

            // add new image to pixel count
            final int newPixelCount = image.getWidth(null) * image.getHeight(null);
            currentPixelCount += newPixelCount;
            // clean out lost references if not enough space
            if (currentPixelCount > maxPixelCount) {
                while ((ref = (PixelCountSoftReference)referenceQueue.poll()) != null) {
                    //reference lost
                    map.remove(ref.hash);
                    currentPixelCount -= ref.pixelCount;
                }
            }

            // remove old items till there is enough free space
            if (currentPixelCount > maxPixelCount) {
                final Iterator<Map.Entry<Integer, PixelCountSoftReference>> mapIter = map.entrySet().iterator();
                while ((currentPixelCount > maxPixelCount) && mapIter.hasNext()) {
                    final Map.Entry<Integer, PixelCountSoftReference> entry = mapIter.next();
                    mapIter.remove();
                    final Image img = entry.getValue().get();
                    if (img != null) img.flush();
                    currentPixelCount -= entry.getValue().pixelCount;
                }
            }
            // finally put new in map
            map.put(hash, new PixelCountSoftReference(image, referenceQueue, newPixelCount, hash, config, w, h, state));
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private int hash(final GraphicsConfiguration config, final int w, final int h, final JRSUIState state) {
        int hash = (config != null ? config.hashCode() : 0);
        hash = 31 * hash + w;
        hash = 31 * hash + h;
        hash = 31 * hash + state.hashCode();
        return hash;
    }

    /** Extended SoftReference that stores the pixel count even after the image is lost */
    private static class PixelCountSoftReference extends SoftReference<Image> {
        private final int pixelCount;
        private final int hash;

        // key parts
        private final GraphicsConfiguration config;
        private final int w;
        private final int h;
        private final JRSUIState state;

        public PixelCountSoftReference(final Image referent, final ReferenceQueue<? super Image> q, final int pixelCount, final int hash, final GraphicsConfiguration config, final int w, final int h, final JRSUIState state) {
            super(referent, q);
            this.pixelCount = pixelCount;
            this.hash = hash;
            this.config = config;
            this.w = w;
            this.h = h;
            this.state = state;
        }

        public boolean equals(final GraphicsConfiguration config, final int w, final int h, final JRSUIState state) {
            return config == this.config && w == this.w && h == this.h && state.equals(this.state);
        }
    }

//    /** Gets the rendered image for this painter at the requested size, either from cache or create a new one */
//    private VolatileImage getImage(GraphicsConfiguration config, JComponent c, int w, int h, Object[] extendedCacheKeys) {
//        VolatileImage buffer = (VolatileImage)getImage(config, w, h, this, extendedCacheKeys);
//
//        int renderCounter = 0; // to avoid any potential, though unlikely, infinite loop
//        do {
//            //validate the buffer so we can check for surface loss
//            int bufferStatus = VolatileImage.IMAGE_INCOMPATIBLE;
//            if (buffer != null) {
//                bufferStatus = buffer.validate(config);
//            }
//
//            //If the buffer status is incompatible or restored, then we need to re-render to the volatile image
//            if (bufferStatus == VolatileImage.IMAGE_INCOMPATIBLE || bufferStatus == VolatileImage.IMAGE_RESTORED) {
//                // if the buffer isn't the right size, or has lost its contents, then recreate
//                if (buffer != null) {
//                    if (buffer.getWidth() != w || buffer.getHeight() != h || bufferStatus == VolatileImage.IMAGE_INCOMPATIBLE) {
//                        // clear any resources related to the old back buffer
//                        buffer.flush();
//                        buffer = null;
//                    }
//                }
//
//                if (buffer == null) {
//                    // recreate the buffer
//                    buffer = config.createCompatibleVolatileImage(w, h, Transparency.TRANSLUCENT);
//                    // put in cache for future
//                    setImage(buffer, config, w, h, this, extendedCacheKeys);
//                }
//
//                //create the graphics context with which to paint to the buffer
//                Graphics2D bg = buffer.createGraphics();
//
//                //clear the background before configuring the graphics
//                bg.setComposite(AlphaComposite.Clear);
//                bg.fillRect(0, 0, w, h);
//                bg.setComposite(AlphaComposite.SrcOver);
//                bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//
//                // paint the painter into buffer
//                paint0(bg, c, w, h, extendedCacheKeys);
//                //close buffer graphics
//                bg.dispose();
//            }
//        } while (buffer.contentsLost() && renderCounter++ < 3);
//
//        // check if we failed
//        if (renderCounter >= 3) return null;
//
//        return buffer;
//    }
}
