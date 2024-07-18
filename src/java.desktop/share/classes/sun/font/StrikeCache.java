/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.font;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import static java.lang.foreign.MemorySegment.NULL;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import static java.lang.foreign.ValueLayout.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;

import sun.java2d.Disposer;
import sun.java2d.pipe.BufferedContext;
import sun.java2d.pipe.RenderQueue;
import sun.java2d.pipe.hw.AccelGraphicsConfig;

/**

A FontStrike is the keeper of scaled glyph image data which is expensive
to compute so needs to be cached.
So long as that data may be being used it cannot be invalidated.
Yet we also need to limit the amount of native memory and number of
strike objects in use.
For scalability and ease of use, a key goal is multi-threaded read
access to a strike, so that it may be shared by multiple client objects,
potentially executing on different threads, with no special reference
counting or "check-out/check-in" requirements which would pass on the
burden of keeping track of strike references to the SG2D and other clients.

A cache of strikes is maintained via Reference objects.
This helps in two ways :
1. The VM will free references when memory is low or they have not been
used in a long time.
2. Reference queues provide a way to get notification of this so we can
free native memory resources.

 */

public final class StrikeCache {

    static ReferenceQueue<Object> refQueue = Disposer.getQueue();

    static ArrayList<GlyphDisposedListener> disposeListeners = new ArrayList<GlyphDisposedListener>(1);


    /* Reference objects may have their referents cleared when GC chooses.
     * During application client start-up there is typically at least one
     * GC which causes the hotspot VM to clear soft (not just weak) references
     * Thus not only is there a GC pause, but the work done do rasterise
     * glyphs that are fairly certain to be needed again almost immediately
     * is thrown away. So for performance reasons a simple optimisation is to
     * keep up to 8 strong references to strikes to reduce the chance of
     * GC'ing strikes that have been used recently. Note that this may not
     * suffice in Solaris UTF-8 locales where a single composite strike may be
     * composed of 15 individual strikes, plus the composite strike.
     * And this assumes the new architecture doesn't maintain strikes for
     * natively accessed bitmaps. It may be worth "tuning" the number of
     * strikes kept around for the platform or locale.
     * Since no attempt is made to ensure uniqueness or ensure synchronized
     * access there is no guarantee that this cache will ensure that unique
     * strikes are cached. Every time a strike is looked up it is added
     * to the current index in this cache. All this cache has to do to be
     * worthwhile is prevent excessive cache flushing of strikes that are
     * referenced frequently. The logic that adds references here could be
     * tweaked to keep only strikes  that represent untransformed, screen
     * sizes as that's the typical performance case.
     */
    static int MINSTRIKES = 8; // can be overridden by property
    static int recentStrikeIndex = 0;
    static FontStrike[] recentStrikes;
    static boolean cacheRefTypeWeak;

    /*
     * Native sizes and accessors for glyph cache structure.
     * There are 10 values. Also need native address size and a long which
     * references a memory address for a "null" glyph image.
     */
    static final int nativeAddressSize = (int)ValueLayout.ADDRESS.byteSize();
    static final long invisibleGlyphPtr = getInvisibleGlyphPtr(); // a singleton.

    static native long getInvisibleGlyphPtr();

    public static final StructLayout GlyphImageLayout = MemoryLayout.structLayout(
        JAVA_FLOAT.withName("xAdvance"), // 0+4=4,
        JAVA_FLOAT.withName("yAdvance"), // 4+4=8,
        JAVA_CHAR.withName("width"),     // 8+2=10,
        JAVA_CHAR.withName("height"),    // 10+2=12
        JAVA_CHAR.withName("rowBytes"),  // 12+2=14
        JAVA_BYTE.withName("managed"),   // 14+1=15
        MemoryLayout.paddingLayout(1),   // 15+1=16
        JAVA_FLOAT.withName("topLeftX"), // 16+4=20
        JAVA_FLOAT.withName("topLeftY"), // 20+4=24
        ADDRESS.withName("cellInfo"),    // 24+8=32
        ADDRESS.withName("image")        // 32+8=40
     );

   private static final long GLYPHIMAGESIZE = GlyphImageLayout.byteSize();

   private static VarHandle getVarHandle(StructLayout struct, String name) {
        VarHandle h = struct.varHandle(PathElement.groupElement(name));
        /* insert 0 offset so don't need to pass arg every time */
        return MethodHandles.insertCoordinates(h, 1, 0L).withInvokeExactBehavior();
    }

    private static final VarHandle xAdvanceHandle = getVarHandle(GlyphImageLayout, "xAdvance");
    private static final VarHandle yAdvanceHandle = getVarHandle(GlyphImageLayout, "yAdvance");
    private static final VarHandle widthHandle    = getVarHandle(GlyphImageLayout, "width");
    private static final VarHandle heightHandle   = getVarHandle(GlyphImageLayout, "height");
    private static final VarHandle rowBytesHandle = getVarHandle(GlyphImageLayout, "rowBytes");
    private static final VarHandle managedHandle  = getVarHandle(GlyphImageLayout, "managed");
    private static final VarHandle topLeftXHandle = getVarHandle(GlyphImageLayout, "topLeftX");
    private static final VarHandle topLeftYHandle = getVarHandle(GlyphImageLayout, "topLeftY");
    private static final VarHandle cellInfoHandle = getVarHandle(GlyphImageLayout, "cellInfo");
    private static final VarHandle imageHandle    = getVarHandle(GlyphImageLayout, "image");

    @SuppressWarnings("restricted")
    static final float getGlyphXAdvance(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        return (float)xAdvanceHandle.get(seg);
    }

    @SuppressWarnings("restricted")
    static final void setGlyphXAdvance(long ptr, float val) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        xAdvanceHandle.set(seg, val);
    }

    @SuppressWarnings("restricted")
    static final float getGlyphYAdvance(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        return (float)yAdvanceHandle.get(seg);
    }

    @SuppressWarnings("restricted")
    static final char getGlyphWidth(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        return (char)widthHandle.get(seg);
    }

    @SuppressWarnings("restricted")
    static final char getGlyphHeight(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        return (char)heightHandle.get(seg);
    }

    @SuppressWarnings("restricted")
    static final char getGlyphRowBytes(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        return (char)rowBytesHandle.get(seg);
    }

    @SuppressWarnings("restricted")
    static final byte getGlyphManaged(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        return (byte)managedHandle.get(seg);
    }

    @SuppressWarnings("restricted")
    static final float getGlyphTopLeftX(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        return (float)topLeftXHandle.get(seg);
    }

    @SuppressWarnings("restricted")
    static final float getGlyphTopLeftY(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        return (float)topLeftYHandle.get(seg);
    }

    @SuppressWarnings("restricted")
    static final long getGlyphCellInfo(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        return ((MemorySegment)cellInfoHandle.get(seg)).address();
    }

    @SuppressWarnings("restricted")
    static final void setGlyphCellInfo(long ptr, long val) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        MemorySegment segval = MemorySegment.ofAddress(val);
        cellInfoHandle.set(seg, segval);
    }

    @SuppressWarnings("restricted")
    static final long getGlyphImagePtr(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        return ((MemorySegment)imageHandle.get(seg)).address();
    }

    @SuppressWarnings("restricted")
    static final MemorySegment getGlyphPixelData(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        char hgt = (char)heightHandle.get(seg);
        char rb = (char)rowBytesHandle.get(seg);
        MemorySegment pixelData = (MemorySegment)imageHandle.get(seg);
        pixelData = pixelData.reinterpret(rb * hgt);
        return pixelData;
    }

    @SuppressWarnings("restricted")
    static final byte[] getGlyphPixelBytes(long ptr) {
        MemorySegment seg = MemorySegment.ofAddress(ptr);
        seg = seg.reinterpret(GLYPHIMAGESIZE);
        char hgt = (char)heightHandle.get(seg);
        char rb = (char)rowBytesHandle.get(seg);
        MemorySegment pixelData = (MemorySegment)imageHandle.get(seg);
        int sz = rb * hgt;
        pixelData = pixelData.reinterpret(sz);
        return pixelData.toArray(ValueLayout.JAVA_BYTE);
    }

    static final byte getPixelByte(MemorySegment pixelData, long index) {
       return pixelData.getAtIndex(JAVA_BYTE, index);
    }

    static {
        initStatic();
    }

    @SuppressWarnings("removal")
    private static void initStatic() {

        if (nativeAddressSize < 4) {
            throw new InternalError("Unexpected address size for font data: " +
                                    nativeAddressSize);
        }

        java.security.AccessController.doPrivileged(
                                    new java.security.PrivilegedAction<Object>() {
            public Object run() {

               /* Allow a client to override the reference type used to
                * cache strikes. The default is "soft" which hints to keep
                * the strikes around. This property allows the client to
                * override this to "weak" which hint to the GC to free
                * memory more aggressively.
                */
               String refType =
                   System.getProperty("sun.java2d.font.reftype", "soft");
               cacheRefTypeWeak = refType.equals("weak");

                String minStrikesStr =
                    System.getProperty("sun.java2d.font.minstrikes");
                if (minStrikesStr != null) {
                    try {
                        MINSTRIKES = Integer.parseInt(minStrikesStr);
                        if (MINSTRIKES <= 0) {
                            MINSTRIKES = 1;
                        }
                    } catch (NumberFormatException e) {
                    }
                }

                recentStrikes = new FontStrike[MINSTRIKES];

                return null;
            }
        });
    }


    static void refStrike(FontStrike strike) {
        int index = recentStrikeIndex;
        recentStrikes[index] = strike;
        index++;
        if (index == MINSTRIKES) {
            index = 0;
        }
        recentStrikeIndex = index;
    }

    private static void doDispose(FontStrikeDisposer disposer) {
        if (disposer.intGlyphImages != null) {
            freeCachedIntMemory(disposer.intGlyphImages,
                    disposer.pScalerContext);
        } else if (disposer.longGlyphImages != null) {
            freeCachedLongMemory(disposer.longGlyphImages,
                    disposer.pScalerContext);
        } else if (disposer.segIntGlyphImages != null) {
            /* NB Now making multiple JNI calls in this case.
             * But assuming that there's a reasonable amount of locality
             * rather than sparse references then it should be OK.
             */
            for (int i=0; i<disposer.segIntGlyphImages.length; i++) {
                if (disposer.segIntGlyphImages[i] != null) {
                    freeCachedIntMemory(disposer.segIntGlyphImages[i],
                            disposer.pScalerContext);
                    /* native will only free the scaler context once */
                    disposer.pScalerContext = 0L;
                    disposer.segIntGlyphImages[i] = null;
                }
            }
            /* This may appear inefficient but it should only be invoked
             * for a strike that never was asked to rasterise a glyph.
             */
            if (disposer.pScalerContext != 0L) {
                freeCachedIntMemory(new int[0], disposer.pScalerContext);
            }
        } else if (disposer.segLongGlyphImages != null) {
            for (int i=0; i<disposer.segLongGlyphImages.length; i++) {
                if (disposer.segLongGlyphImages[i] != null) {
                    freeCachedLongMemory(disposer.segLongGlyphImages[i],
                            disposer.pScalerContext);
                    disposer.pScalerContext = 0L;
                    disposer.segLongGlyphImages[i] = null;
                }
            }
            if (disposer.pScalerContext != 0L) {
                freeCachedLongMemory(new long[0], disposer.pScalerContext);
            }
        } else if (disposer.pScalerContext != 0L) {
            /* Rarely a strike may have been created that never cached
             * any glyphs. In this case we still want to free the scaler
             * context.
             */
            if (longAddresses()) {
                freeCachedLongMemory(new long[0], disposer.pScalerContext);
            } else {
                freeCachedIntMemory(new int[0], disposer.pScalerContext);
            }
        }
    }

    private static boolean longAddresses() {
        return nativeAddressSize == 8;
    }

    static void disposeStrike(final FontStrikeDisposer disposer) {
        // we need to execute the strike disposal on the rendering thread
        // because they may be accessed on that thread at the time of the
        // disposal (for example, when the accel. cache is invalidated)

        // Whilst this is a bit heavyweight, in most applications
        // strike disposal is a relatively infrequent operation, so it
        // doesn't matter. But in some tests that use vast numbers
        // of strikes, the switching back and forth is measurable.
        // So the "pollRemove" call is added to batch up the work.
        // If we are polling we know we've already been called back
        // and can directly dispose the record.
        // Also worrisome is the necessity of getting a GC here.

        if (Disposer.pollingQueue) {
            doDispose(disposer);
            return;
        }

        RenderQueue rq = null;
        GraphicsEnvironment ge =
            GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (!GraphicsEnvironment.isHeadless()) {
            GraphicsConfiguration gc =
                ge.getDefaultScreenDevice().getDefaultConfiguration();
            if (gc instanceof AccelGraphicsConfig) {
                AccelGraphicsConfig agc = (AccelGraphicsConfig)gc;
                BufferedContext bc = agc.getContext();
                if (bc != null) {
                    rq = bc.getRenderQueue();
                }
            }
        }
        if (rq != null) {
            rq.lock();
            try {
                rq.flushAndInvokeNow(new Runnable() {
                    public void run() {
                        doDispose(disposer);
                        Disposer.pollRemove();
                    }
                });
            } finally {
                rq.unlock();
            }
        } else {
            doDispose(disposer);
        }
    }

    static native void freeIntPointer(int ptr);
    static native void freeLongPointer(long ptr);
    private static native void freeIntMemory(int[] glyphPtrs, long pContext);
    private static native void freeLongMemory(long[] glyphPtrs, long pContext);

    private static void freeCachedIntMemory(int[] glyphPtrs, long pContext) {
        synchronized(disposeListeners) {
            if (disposeListeners.size() > 0) {
                ArrayList<Long> gids = null;

                for (int i = 0; i < glyphPtrs.length; i++) {
                    if ((glyphPtrs[i] != 0) && getGlyphManaged(glyphPtrs[i]) == 0) {

                        if (gids == null) {
                            gids = new ArrayList<Long>();
                        }
                        gids.add((long) glyphPtrs[i]);
                    }
                }

                if (gids != null) {
                    // Any reference by the disposers to the native glyph ptrs
                    // must be done before this returns.
                    notifyDisposeListeners(gids);
                }
            }
        }

        freeIntMemory(glyphPtrs, pContext);
    }

    private static void  freeCachedLongMemory(long[] glyphPtrs, long pContext) {
        synchronized(disposeListeners) {
        if (disposeListeners.size() > 0)  {
                ArrayList<Long> gids = null;

                for (int i=0; i < glyphPtrs.length; i++) {
                    if ((glyphPtrs[i] != 0) && getGlyphManaged(glyphPtrs[i]) == 0) {

                        if (gids == null) {
                            gids = new ArrayList<Long>();
                        }
                        gids.add(glyphPtrs[i]);
                    }
                }

                if (gids != null) {
                    // Any reference by the disposers to the native glyph ptrs
                    // must be done before this returns.
                    notifyDisposeListeners(gids);
                }
        }
        }

        freeLongMemory(glyphPtrs, pContext);
    }

    public static void addGlyphDisposedListener(GlyphDisposedListener listener) {
        synchronized(disposeListeners) {
            disposeListeners.add(listener);
        }
    }

    private static void notifyDisposeListeners(ArrayList<Long> glyphs) {
        for (GlyphDisposedListener listener : disposeListeners) {
            listener.glyphDisposed(glyphs);
        }
    }

    public static Reference<FontStrike> getStrikeRef(FontStrike strike) {
        return getStrikeRef(strike, cacheRefTypeWeak);
    }

    public static Reference<FontStrike> getStrikeRef(FontStrike strike, boolean weak) {
        /* Some strikes may have no disposer as there's nothing
         * for them to free, as they allocated no native resource
         * eg, if they did not allocate resources because of a problem,
         * or they never hold native resources. So they create no disposer.
         * But any strike that reaches here that has a null disposer is
         * a potential memory leak.
         */
        if (strike.disposer == null) {
            if (weak) {
                return new WeakReference<>(strike);
            } else {
                return new SoftReference<>(strike);
            }
        }

        if (weak) {
            return new WeakDisposerRef(strike);
        } else {
            return new SoftDisposerRef(strike);
        }
    }

    static interface DisposableStrike {
        FontStrikeDisposer getDisposer();
    }

    static class SoftDisposerRef
        extends SoftReference<FontStrike> implements DisposableStrike {

        private FontStrikeDisposer disposer;

        public FontStrikeDisposer getDisposer() {
            return disposer;
        }

        @SuppressWarnings("unchecked")
        SoftDisposerRef(FontStrike strike) {
            super(strike, StrikeCache.refQueue);
            disposer = strike.disposer;
            Disposer.addReference((Reference<Object>)(Reference)this, disposer);
        }
    }

    static class WeakDisposerRef
        extends WeakReference<FontStrike> implements DisposableStrike {

        private FontStrikeDisposer disposer;

        public FontStrikeDisposer getDisposer() {
            return disposer;
        }

        @SuppressWarnings("unchecked")
        WeakDisposerRef(FontStrike strike) {
            super(strike, StrikeCache.refQueue);
            disposer = strike.disposer;
            Disposer.addReference((Reference<Object>)(Reference)this, disposer);
        }
    }

}
