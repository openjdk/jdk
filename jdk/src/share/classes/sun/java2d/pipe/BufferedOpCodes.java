/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.pipe;

public class BufferedOpCodes {
    // draw ops
    public static final int DRAW_LINE            = 10;
    public static final int DRAW_RECT            = 11;
    public static final int DRAW_POLY            = 12;
    public static final int DRAW_PIXEL           = 13;
    public static final int DRAW_SCANLINES       = 14;
    public static final int DRAW_PARALLELOGRAM   = 15;
    public static final int DRAW_AAPARALLELOGRAM = 16;

    // fill ops
    public static final int FILL_RECT            = 20;
    public static final int FILL_SPANS           = 21;
    public static final int FILL_PARALLELOGRAM   = 22;
    public static final int FILL_AAPARALLELOGRAM = 23;

    // copy-related ops
    public static final int COPY_AREA            = 30;
    public static final int BLIT                 = 31;
    public static final int MASK_FILL            = 32;
    public static final int MASK_BLIT            = 33;
    public static final int SURFACE_TO_SW_BLIT   = 34;

    // text-related ops
    public static final int DRAW_GLYPH_LIST      = 40;

    // state-related ops
    public static final int SET_RECT_CLIP        = 51;
    public static final int BEGIN_SHAPE_CLIP     = 52;
    public static final int SET_SHAPE_CLIP_SPANS = 53;
    public static final int END_SHAPE_CLIP       = 54;
    public static final int RESET_CLIP           = 55;
    public static final int SET_ALPHA_COMPOSITE  = 56;
    public static final int SET_XOR_COMPOSITE    = 57;
    public static final int RESET_COMPOSITE      = 58;
    public static final int SET_TRANSFORM        = 59;
    public static final int RESET_TRANSFORM      = 60;

    // context-related ops
    public static final int SET_SURFACES         = 70;
    public static final int SET_SCRATCH_SURFACE  = 71;
    public static final int FLUSH_SURFACE        = 72;
    public static final int DISPOSE_SURFACE      = 73;
    public static final int DISPOSE_CONFIG       = 74;
    public static final int INVALIDATE_CONTEXT   = 75;
    public static final int SYNC                 = 76;
    public static final int RESTORE_DEVICES      = 77;
    public static final int SAVE_STATE           = 78;
    public static final int RESTORE_STATE        = 79;

    // multibuffering ops
    public static final int SWAP_BUFFERS         = 80;

    // special no-op op code (mainly used for achieving 8-byte alignment)
    public static final int NOOP                 = 90;

    // paint-related ops
    public static final int RESET_PAINT               = 100;
    public static final int SET_COLOR                 = 101;
    public static final int SET_GRADIENT_PAINT        = 102;
    public static final int SET_LINEAR_GRADIENT_PAINT = 103;
    public static final int SET_RADIAL_GRADIENT_PAINT = 104;
    public static final int SET_TEXTURE_PAINT         = 105;

    // BufferedImageOp-related ops
    public static final int ENABLE_CONVOLVE_OP     = 120;
    public static final int DISABLE_CONVOLVE_OP    = 121;
    public static final int ENABLE_RESCALE_OP      = 122;
    public static final int DISABLE_RESCALE_OP     = 123;
    public static final int ENABLE_LOOKUP_OP       = 124;
    public static final int DISABLE_LOOKUP_OP      = 125;
}
