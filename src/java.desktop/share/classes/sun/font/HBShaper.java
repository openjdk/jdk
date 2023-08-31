/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.font;

import java.awt.geom.Point2D;
import sun.font.GlyphLayout.GVData;
import sun.java2d.Disposer;
import sun.java2d.DisposerRecord;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import static java.lang.foreign.MemorySegment.NULL;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.UnionLayout;
import static java.lang.foreign.ValueLayout.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import java.util.Optional;
import java.util.WeakHashMap;

public class HBShaper {

    /*
     * union _hb_var_int_t {
     *     uint32_t u32;
     *     int32_t i32;
     *     uint16_t u16[2];
     *     int16_t i16[2];
     *     uint8_t u8[4];
     *     int8_t i8[4];
     * };
     */
   private static final UnionLayout VarIntLayout = MemoryLayout.unionLayout(
        JAVA_INT.withName("u32"),
        JAVA_INT.withName("i32"),
        MemoryLayout.sequenceLayout(2, JAVA_SHORT).withName("u16"),
        MemoryLayout.sequenceLayout(2, JAVA_SHORT).withName("i16"),
        MemoryLayout.sequenceLayout(4, JAVA_BYTE).withName("u8"),
        MemoryLayout.sequenceLayout(4, JAVA_BYTE).withName("i8")
    ).withName("_hb_var_int_t");

    /*
     * struct hb_glyph_position_t {
     *     hb_position_t x_advance;
     *     hb_position_t y_advance;
     *     hb_position_t x_offset;
     *     hb_position_t y_offset;
     *     hb_var_int_t var;
     * };
     */
     private static final StructLayout PositionLayout = MemoryLayout.structLayout(
        JAVA_INT.withName("x_advance"),
        JAVA_INT.withName("y_advance"),
        JAVA_INT.withName("x_offset"),
        JAVA_INT.withName("y_offset"),
        VarIntLayout.withName("var")
     ).withName("hb_glyph_position_t");

    /**
     * struct hb_glyph_info_t {
     *     hb_codepoint_t codepoint;
     *     hb_mask_t mask;
     *     uint32_t cluster;
     *     hb_var_int_t var1;
     *     hb_var_int_t var2;
     * };
     */
    private static final StructLayout GlyphInfoLayout = MemoryLayout.structLayout(
        JAVA_INT.withName("codepoint"),
        JAVA_INT.withName("mask"),
        JAVA_INT.withName("cluster"),
        VarIntLayout.withName("var1"),
        VarIntLayout.withName("var2")
    ).withName("hb_glyph_info_t");

    private static VarHandle getVarHandle(SequenceLayout layout, String name) {
        return layout.varHandle(
            PathElement.sequenceElement(),
            PathElement.groupElement(name));
    }

    private static final VarHandle x_offsetHandle;
    private static final VarHandle y_offsetHandle;
    private static final VarHandle x_advanceHandle;
    private static final VarHandle y_advanceHandle;
    private static final VarHandle codePointHandle;
    private static final VarHandle clusterHandle;

    private static final MethodHandles.Lookup MH_LOOKUP;
    private static final Linker LINKER;
    private static final SymbolLookup SYM_LOOKUP;
    private static final MethodHandle malloc_handle;
    private static final MethodHandle create_face_handle;
    private static final MethodHandle dispose_face_handle;
    private static final MethodHandle jdk_hb_shape_handle;

    private static final MemorySegment get_var_glyph_stub;
    private static final MemorySegment get_nominal_glyph_stub;
    private static final MemorySegment get_h_advance_stub;
    private static final MemorySegment get_v_advance_stub;
    private static final MemorySegment get_contour_pt_stub;

    private static MemorySegment store_layout_results_stub;

   private static FunctionDescriptor
       getFunctionDescriptor(MemoryLayout retType,
                             MemoryLayout... argTypes) {

       return (retType == null) ?
               FunctionDescriptor.ofVoid(argTypes) :
               FunctionDescriptor.of(retType, argTypes);
   }

   private static MethodHandle getMethodHandle
         (String mName,
          FunctionDescriptor fd) {

       try {
           MethodType mType = fd.toMethodType();
           return MH_LOOKUP.findStatic(HBShaper.class, mName, mType);
       } catch (IllegalAccessException | NoSuchMethodException e) {
          return null;
       }
   }

   static {
        MH_LOOKUP = MethodHandles.lookup();
        LINKER = Linker.nativeLinker();
        SYM_LOOKUP = SymbolLookup.loaderLookup().or(LINKER.defaultLookup());
        FunctionDescriptor mallocDescriptor =
            FunctionDescriptor.of(ADDRESS, JAVA_LONG);
        Optional<MemorySegment> malloc_symbol = SYM_LOOKUP.find("malloc");
        malloc_handle = LINKER.downcallHandle(malloc_symbol.get(), mallocDescriptor);

        FunctionDescriptor createFaceDescriptor =
            FunctionDescriptor.of(ADDRESS, ADDRESS);
        Optional<MemorySegment> create_face_symbol = SYM_LOOKUP.find("HBCreateFace");
        create_face_handle = LINKER.downcallHandle(create_face_symbol.get(), createFaceDescriptor);

        FunctionDescriptor disposeFaceDescriptor = FunctionDescriptor.ofVoid(ADDRESS);
        Optional<MemorySegment> dispose_face_symbol = SYM_LOOKUP.find("HBDisposeFace");
        dispose_face_handle = LINKER.downcallHandle(dispose_face_symbol.get(), disposeFaceDescriptor);
        FunctionDescriptor shapeDesc = FunctionDescriptor.ofVoid(
            //JAVA_INT,    // return type
            JAVA_FLOAT,  // ptSize
            ADDRESS,     // matrix
            ADDRESS,     // face
            ADDRESS,     // chars
            JAVA_INT,    // len
            JAVA_INT,    // script
            JAVA_INT,    // offset
            JAVA_INT,    // limit
            JAVA_INT,    // baseIndex
            JAVA_FLOAT,  // startX
            JAVA_FLOAT,  // startY
            JAVA_INT,    // flags,
            JAVA_INT,    // slot,
            ADDRESS,     // glyph_fn
            ADDRESS,     // variation_fn
            ADDRESS,     // h_advance_fn
            ADDRESS,     // v_advance_fn
            ADDRESS,     // contour_pt_fn
            ADDRESS);    // store_results_fn

        Optional<MemorySegment> shape_sym = SYM_LOOKUP.find("jdk_hb_shape");
        jdk_hb_shape_handle = LINKER.downcallHandle(shape_sym.get(), shapeDesc);

        Arena garena = Arena.global(); // creating stubs that exist until VM exit.
        FunctionDescriptor get_var_glyph_fd = getFunctionDescriptor(JAVA_INT,  // return type
              ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS); // arg types
        MethodHandle get_var_glyph_mh =
            getMethodHandle("get_variation_glyph", get_var_glyph_fd);
        get_var_glyph_stub =
            LINKER.upcallStub(get_var_glyph_mh, get_var_glyph_fd, garena);

        FunctionDescriptor get_nominal_glyph_fd = getFunctionDescriptor(JAVA_INT, // return type
                   ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS); // arg types
        MethodHandle get_nominal_glyph_mh =
            getMethodHandle("get_nominal_glyph", get_nominal_glyph_fd);
        get_nominal_glyph_stub =
            LINKER.upcallStub(get_nominal_glyph_mh, get_nominal_glyph_fd, garena);

        FunctionDescriptor get_h_adv_fd = getFunctionDescriptor(JAVA_INT,  // return type
                   ADDRESS, ADDRESS, JAVA_INT, ADDRESS); // arg types
        MethodHandle get_h_adv_mh =
            getMethodHandle("get_glyph_h_advance", get_h_adv_fd);
        get_h_advance_stub =
            LINKER.upcallStub(get_h_adv_mh, get_h_adv_fd, garena);

        FunctionDescriptor get_v_adv_fd = getFunctionDescriptor(JAVA_INT,  // return type
                   ADDRESS, ADDRESS, JAVA_INT, ADDRESS); // arg types
        MethodHandle get_v_adv_mh =
            getMethodHandle("get_glyph_v_advance", get_v_adv_fd);
        get_v_advance_stub =
            LINKER.upcallStub(get_v_adv_mh, get_v_adv_fd, garena);

        FunctionDescriptor get_contour_pt_fd = getFunctionDescriptor(JAVA_INT,  // return type
            ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS); // arg types
        MethodHandle get_contour_pt_mh =
            getMethodHandle("get_glyph_contour_point", get_contour_pt_fd);
        get_contour_pt_stub =
            LINKER.upcallStub(get_contour_pt_mh, get_contour_pt_fd, garena);

       FunctionDescriptor store_layout_fd =
          FunctionDescriptor.ofVoid(
                   JAVA_INT,               // slot
                   JAVA_INT,               // baseIndex
                   JAVA_INT,               // offset
                   JAVA_FLOAT,             // startX
                   JAVA_FLOAT,             // startX
                   JAVA_FLOAT,             // devScale
                   JAVA_INT,               // charCount
                   JAVA_INT,               // glyphCount
                   ADDRESS,                // glyphInfo
                   ADDRESS);               // glyphPos
       MethodHandle store_layout_mh =
          getMethodHandle("store_layout_results", store_layout_fd);
       store_layout_results_stub =
           LINKER.upcallStub(store_layout_mh, store_layout_fd, garena);

        SequenceLayout glyphPosLayout = MemoryLayout.sequenceLayout(PositionLayout);
        x_offsetHandle = getVarHandle(glyphPosLayout, "x_offset");
        y_offsetHandle = getVarHandle(glyphPosLayout, "y_offset");
        x_advanceHandle = getVarHandle(glyphPosLayout, "x_advance");
        y_advanceHandle = getVarHandle(glyphPosLayout, "y_advance");
        SequenceLayout glyphInfosLayout = MemoryLayout.sequenceLayout(GlyphInfoLayout);
        codePointHandle = getVarHandle(glyphInfosLayout, "codepoint");
        clusterHandle = getVarHandle(glyphInfosLayout, "cluster");
    }


    /*
     * This is expensive but it is done just once per font.
     * The unbound stub could be cached but the savings would
     * be very low in the only case it is used.
     */
    private static MemorySegment getBoundUpcallStub
         (Arena arena, Class<?> clazz, Object bindArg, String mName,
          MemoryLayout retType, MemoryLayout... argTypes) {

       try {
            FunctionDescriptor nativeDescriptor =
               (retType == null) ?
                   FunctionDescriptor.ofVoid(argTypes) :
                   FunctionDescriptor.of(retType, argTypes);
           MethodType mType = nativeDescriptor.toMethodType();
           mType = mType.insertParameterTypes(0, clazz);
           MethodHandle mh = MH_LOOKUP.findStatic(HBShaper.class, mName, mType);
           MethodHandle bound_handle = mh.bindTo(bindArg);
           return LINKER.upcallStub(bound_handle, nativeDescriptor, arena);
       } catch (IllegalAccessException | NoSuchMethodException e) {
          return null;
       }
   }

    private static int get_nominal_glyph(
        MemorySegment font_ptr,   /* Not used */
        MemorySegment font_data,  /* Not used */
        int unicode,
        MemorySegment glyph,      /* pointer to location to store glyphID */
        MemorySegment user_data   /* Not used */
    ) {

        Font2D font2D = scopedVars.get().font();
        int glyphID = font2D.charToGlyph(unicode);
        MemorySegment glyphIDPtr = glyph.reinterpret(4);
        glyphIDPtr.setAtIndex(JAVA_INT, 0, glyphID);
        return (glyphID != 0) ? 1 : 0;
}

    private static int get_variation_glyph(
        MemorySegment font_ptr,   /* Not used */
        MemorySegment font_data,  /* Not used */
        int unicode,
        int variation_selector,
        MemorySegment glyph,      /* pointer to location to store glyphID */
        MemorySegment user_data   /* Not used */
    ) {
        Font2D font2D = scopedVars.get().font();
        int glyphID = font2D.charToVariationGlyph(unicode, variation_selector);
        MemorySegment glyphIDPtr = glyph.reinterpret(4);
        glyphIDPtr.setAtIndex(JAVA_INT, 0, glyphID);
        return (glyphID != 0) ? 1 : 0;
    }

    private static final float HBFloatToFixedScale = ((float)(1 << 16));
    private static final int HBFloatToFixed(float f) {
        return ((int)((f) * HBFloatToFixedScale));
    }

    private static int get_glyph_h_advance(
        MemorySegment font_ptr,   /* Not used */
        MemorySegment font_data,  /* Not used */
        int glyph,
        MemorySegment user_data  /* Not used */
    ) {

        FontStrike strike = scopedVars.get().fontStrike();
        Point2D.Float pt = strike.getGlyphMetrics(glyph);
        return (pt != null) ? HBFloatToFixed(pt.x) : 0;
    }

    private static int get_glyph_v_advance(
        MemorySegment font_ptr,   /* Not used */
        MemorySegment font_data,  /* Not used */
        int glyph,
        MemorySegment user_data  /* Not used */
    ) {

        FontStrike strike = scopedVars.get().fontStrike();
        Point2D.Float pt = strike.getGlyphMetrics(glyph);
        return (pt != null) ? HBFloatToFixed(pt.y) : 0;
    }

    /*
     * This class exists to make the code that uses it less verbose
     */
    private static class IntPtr {
        MemorySegment seg;
        IntPtr(MemorySegment seg) {
        }

        void set(int i) {
            seg.setAtIndex(JAVA_INT, 0, i);
        }
    }

    private static int get_glyph_contour_point(
        MemorySegment font_ptr,   /* Not used */
        MemorySegment font_data,  /* Not used */
        int glyph,
        int point_index,
        MemorySegment x_ptr,     /* ptr to return x */
        MemorySegment y_ptr,     /* ptr to return y */
        MemorySegment user_data  /* Not used */
    ) {
        IntPtr x = new IntPtr(x_ptr);
        IntPtr y = new IntPtr(y_ptr);

        if ((glyph & 0xfffe) == 0xfffe) {
            x.set(0);
            y.set(0);
            return 1;
        }

        FontStrike strike = scopedVars.get().fontStrike();
        Point2D.Float pt = ((PhysicalStrike)strike).getGlyphPoint(glyph, point_index);
        x.set(HBFloatToFixed(pt.x));
        y.set(HBFloatToFixed(pt.y));

       return 1;
    }

    record ScopedVars (
        Font2D font,
        FontStrike fontStrike,
        GVData gvData,
        Point2D.Float point) {}

    static final ScopedValue<ScopedVars> scopedVars = ScopedValue.newInstance();

    static void shape(
        Font2D font2D,
        FontStrike fontStrike,
        float ptSize,
        float[] mat,
        MemorySegment hbface,
        char[] text,
        GVData gvData,
        int script,
        int offset,
        int limit,
        int baseIndex,
        Point2D.Float startPt,
        int flags,
        int slot) {

        /*
         * ScopedValue is needed so that call backs into Java during
         * shaping can locate the correct instances of these to query or update.
         * The alternative of creating bound method handles is far too slow.
         */ 
        ScopedVars vars = new ScopedVars(font2D, fontStrike, gvData, startPt);
        ScopedValue.where(scopedVars, vars)
                   .run(() -> {

            try (Arena arena = Arena.ofConfined()) {

                float startX = (float)startPt.getX();
                float startY = (float)startPt.getY();

                MemorySegment matrix = arena.allocateArray(JAVA_FLOAT, mat.length);
                MemorySegment.copy(mat, 0, matrix, JAVA_FLOAT, 0, mat.length);
                MemorySegment chars = arena.allocateArray(JAVA_CHAR, text.length);
                MemorySegment.copy(text, 0, chars, JAVA_CHAR, 0, text.length);

                /*int ret =*/ jdk_hb_shape_handle.invokeExact(
                     ptSize, matrix, hbface, chars, text.length,
                     script, offset, limit,
                     baseIndex, startX, startY, flags, slot,
                     get_nominal_glyph_stub,
                     get_var_glyph_stub,
                     get_h_advance_stub,
                     get_v_advance_stub,
                     get_contour_pt_stub,
                     store_layout_results_stub);
            } catch (Throwable t) {
            }
        });
    }

    private static int getFontTableData(Font2D font2D,
                                int tag,
                                MemorySegment data_ptr_out) {

        /*
         * On return, the data_out_ptr will point to memory allocated by native malloc,
         * so it will be freed by the caller using native free - when it is
         * done with it.
         */
        MemorySegment data_ptr = data_ptr_out.reinterpret(ADDRESS.byteSize());
        if (tag == 0) {
            data_ptr.setAtIndex(ADDRESS, 0, NULL);
            return 0;
        }
        byte[] data = font2D.getTableBytes(tag);
        if (data == null) {
            data_ptr.setAtIndex(ADDRESS, 0, NULL);
            return 0;
        }
        int len = data.length;
        MemorySegment zero_len = NULL;
        try {
            zero_len = (MemorySegment)malloc_handle.invokeExact((long)len);
        } catch (Throwable t) {
        }
        if (zero_len.equals(NULL)) {
            data_ptr.setAtIndex(ADDRESS, 0, NULL);
            return 0;
        }
        MemorySegment mem = zero_len.reinterpret(len);
        MemorySegment.copy(data, 0, mem, JAVA_BYTE, 0, len);
        data_ptr.setAtIndex(ADDRESS, 0, mem);
        return len;
    }

    /* WeakHashMap is used so that we do not retain temporary fonts 
     *
     * The value is a class that implements the 2D Disposer, so
     * that the native resources for temp. fonts can be freed.
     *
     * Installed fonts should never be cleared from the map as
     * they are permanently referenced.
     */
    private static final WeakHashMap<Font2D, FaceRef>
       faceMap = new WeakHashMap<>();

    static MemorySegment getFace(Font2D font2D) {
        FaceRef ref;
        synchronized (faceMap) {
            ref = faceMap.computeIfAbsent(font2D, FaceRef::new);
        }
        return ref.getFace();
    }

    private static class FaceRef implements DisposerRecord {
        private Font2D font2D;
        private MemorySegment face;
        // get_table_data_fn uses an Arena managed by GC,
        // so we need to keep a reference to it here until
        // this FaceRef is collected.
        private MemorySegment get_table_data_fn;

        private FaceRef(Font2D font) {
            this.font2D = font;
        }
 
        private synchronized MemorySegment getFace() {
            if (face == null) {
                createFace();
                if (face != null) {
                    Disposer.addObjectRecord(font2D, this);
                }
                font2D = null;
            }
            return face;
        }

        private void createFace() {
            try {
                get_table_data_fn = getBoundUpcallStub(Arena.ofAuto(),
                        Font2D.class,
                        font2D,                      // bind arg
                        "getFontTableData",          // method name
                        JAVA_INT,                   // return type
                        JAVA_INT, ADDRESS); // arg types
                if (get_table_data_fn == null) {
                    return;
                }
                face = (MemorySegment)create_face_handle.invokeExact(get_table_data_fn);
            } catch (Throwable t) {
            }
        }

        @Override
        public void dispose() {
            try {
                dispose_face_handle.invokeExact(face);
            } catch (Throwable t) {
            }
        }
    }


    /* Upcall to receive results of layout */
    private static void store_layout_results(
        int slot,
        int baseIndex,
        int offset,
        float startX,
        float startY,
        float devScale,
        int charCount,
        int glyphCount,
        MemorySegment /* hb_glyph_info_t* */ glyphInfo,
        MemorySegment /* hb_glyph_position_t* */ glyphPos
        ) {

        GVData gvdata = scopedVars.get().gvData();
        Point2D.Float startPt = scopedVars.get().point();
        float x=0, y=0;
        float advX, advY;
        float scale = 1.0f / HBFloatToFixedScale / devScale;

        int initialCount = gvdata._count;

        int maxGlyphs = (charCount > glyphCount) ? charCount : glyphCount;
        int maxStore = maxGlyphs + initialCount;
        boolean needToGrow = (maxStore > gvdata._glyphs.length) ||
                             ((maxStore * 2 + 2) > gvdata._positions.length);
        if (needToGrow) {
            gvdata.grow(maxStore-initialCount);
        }

        int glyphPosLen = glyphCount * 2 + 2;
        long posSize = glyphPosLen * PositionLayout.byteSize();
        MemorySegment glyphPosArr = glyphPos.reinterpret(posSize);

        long glyphInfoSize = glyphCount * GlyphInfoLayout.byteSize();
        MemorySegment glyphInfoArr = glyphInfo.reinterpret(glyphInfoSize);

         for (int i=0; i<glyphCount; i++) {
             int storei = i + initialCount;
             int cluster = (int)clusterHandle.get(glyphInfoArr, i) - offset;
             gvdata._indices[storei] = baseIndex + cluster;
             int codePoint = (int)codePointHandle.get(glyphInfoArr, i);
             gvdata._glyphs[storei] = (slot | codePoint);
             int x_offset = (int)x_offsetHandle.get(glyphPosArr, i);
             int y_offset = (int)y_offsetHandle.get(glyphPosArr, i);
             gvdata._positions[(storei*2)]   = startX + x + (x_offset * scale);
             gvdata._positions[(storei*2)+1] = startY + y + (y_offset * scale);
             int x_advance = (int)x_advanceHandle.get(glyphPosArr, i);
             int y_advance = (int)y_advanceHandle.get(glyphPosArr, i);
             x += x_advance * scale;
             y += y_advance * scale;
        }
        int storeadv = initialCount + glyphCount;
        gvdata._count = storeadv;
        // The final slot in the positions array is important
        // because when the GlyphVector is created from this
        // data it determines the overall advance of the glyphvector
        // and this is used in positioning the next glyphvector
        // during rendering where text is broken into runs.
        // We also need to report it back into "pt", so layout can
        // pass it back down for any next run.
        advX = startX + x;
        advY = startY + y;
        gvdata._positions[(storeadv*2)] = advX;
        gvdata._positions[(storeadv*2)+1] = advY;
        startPt.x = advX;
        startPt.y = advY;
        startPt.x = advX;
  }
}
