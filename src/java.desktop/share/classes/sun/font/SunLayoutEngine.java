/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 *
 * (C) Copyright IBM Corp. 2003 - All Rights Reserved
 */

package sun.font;

import sun.font.GlyphLayout.*;
import sun.java2d.Disposer;
import sun.java2d.DisposerRecord;

import java.awt.geom.Point2D;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ConcurrentHashMap;
import java.util.WeakHashMap;

public final class SunLayoutEngine {

    static {
        FontManagerNativeLibrary.load();
    }

    private SunLayoutEngine() {
    }

    private static final WeakHashMap<Font2D, FaceRef> facePtr =
            new WeakHashMap<>();

    private static long getFacePtr(Font2D font2D) {
        FaceRef ref;
        synchronized (facePtr) {
            ref = facePtr.computeIfAbsent(font2D, FaceRef::new);
        }
        return ref.getNativePtr();
    }

    static boolean useFFM = true;
    static {
        String prop = System.getProperty("sun.font.layout.ffm", "true");
        useFFM = "true".equals(prop);

    }

    public static void layout(Font2D font, int script, FontStrikeDesc desc, float[] mat, float ptSize, int gmask,
                       int baseIndex, TextRecord tr, int typo_flags,
                       Point2D.Float pt, GVData data) {

        FontStrike strike = font.getStrike(desc);
        if (useFFM) {
            MemorySegment face = HBShaper.getFace(font);
            if (face != null) {
                HBShaper.shape(font, strike, ptSize, mat, face,
                        tr.text, data, script,
                        tr.start, tr.limit, baseIndex, pt,
                        typo_flags, gmask);
            }
        } else {
            long pFace = getFacePtr(font);
            if (pFace != 0) {
                shape(font, strike, ptSize, mat, pFace,
                    tr.text, data, script,
                    tr.start, tr.limit, baseIndex, pt,
                    typo_flags, gmask);
            }
        }
    }

    /* Native method to invoke harfbuzz layout engine */
    private static native boolean
        shape(Font2D font, FontStrike strike, float ptSize, float[] mat,
              long pFace,
              char[] chars, GVData data,
              int script, int offset, int limit,
              int baseIndex, Point2D.Float pt, int typo_flags, int slot);

    private static native long createFace(Font2D font,
                                          long platformNativeFontPtr);

    private static native void disposeFace(long facePtr);

    private static class FaceRef implements DisposerRecord {
        private Font2D font;
        private Long facePtr;

        private FaceRef(Font2D font) {
            this.font = font;
        }

        private synchronized long getNativePtr() {
            if (facePtr == null) {
                facePtr = createFace(font,
                        font.getPlatformNativeFontPtr());
                if (facePtr != 0) {
                    Disposer.addObjectRecord(font, this);
                }
                font = null;
            }
            return facePtr;
        }

        @Override
        public void dispose() {
            disposeFace(facePtr);
        }
    }
}
