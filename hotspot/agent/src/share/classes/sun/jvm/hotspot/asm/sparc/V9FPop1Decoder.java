/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package sun.jvm.hotspot.asm.sparc;

import sun.jvm.hotspot.asm.*;
import java.util.*;

class V9FPop1Decoder extends FPopDecoder
                     implements V9InstructionDecoder {
    static Map v9opfDecoders = new HashMap(); // Map<Integer, InstructionDecoder>
    static void addV9OpfDecoder(int fpOpcode, InstructionDecoder decoder) {
        v9opfDecoders.put(new Integer(fpOpcode), decoder);
    }

    static {
        addV9OpfDecoder(FMOVd, new FPMoveDecoder(FMOVd, "fmovd", RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE));
        addV9OpfDecoder(FMOVq, new FPMoveDecoder(FMOVq, "fmovq", RTLDT_FL_QUAD, RTLDT_FL_QUAD));
        addV9OpfDecoder(FNEGd, new FP2RegisterDecoder(FNEGd, "fnegd", RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE));
        addV9OpfDecoder(FNEGq, new FP2RegisterDecoder(FNEGq, "fnegq", RTLDT_FL_QUAD, RTLDT_FL_QUAD));
        addV9OpfDecoder(FABSd, new FP2RegisterDecoder(FABSd, "fabsd", RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE));
        addV9OpfDecoder(FABSq, new FP2RegisterDecoder(FABSq, "fabsq", RTLDT_FL_QUAD, RTLDT_FL_QUAD));
        addV9OpfDecoder(FsTOx, new FP2RegisterDecoder(FsTOx, "fstox", RTLDT_FL_SINGLE, RTLDT_FL_QUAD));
        addV9OpfDecoder(FdTOx, new FP2RegisterDecoder(FdTOx, "fdtox", RTLDT_FL_DOUBLE, RTLDT_FL_QUAD));
        addV9OpfDecoder(FqTOx, new FP2RegisterDecoder(FqTOx, "fqtox", RTLDT_FL_QUAD, RTLDT_FL_QUAD));
        addV9OpfDecoder(FxTOs, new FP2RegisterDecoder(FxTOs, "fxtos", RTLDT_FL_QUAD, RTLDT_FL_SINGLE));
        addV9OpfDecoder(FxTOd, new FP2RegisterDecoder(FxTOd, "fxtod", RTLDT_FL_QUAD, RTLDT_FL_SINGLE));
        addV9OpfDecoder(FxTOq, new FP2RegisterDecoder(FxTOq, "fxtoq", RTLDT_FL_QUAD, RTLDT_FL_QUAD));
    }

    InstructionDecoder getOpfDecoder(int opf) {
        InstructionDecoder decoder = (InstructionDecoder) V8FPop1Decoder.opfDecoders.get(new Integer(opf));
        return (decoder !=null)? decoder : (InstructionDecoder) v9opfDecoders.get(new Integer(opf));
    }
}
