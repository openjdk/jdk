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

class V8FPop1Decoder extends FPopDecoder {
    static Map opfDecoders = new HashMap(); // Map<Integer, InstructionDecoder>
    static void addOpfDecoder(int fpOpcode, InstructionDecoder decoder) {
        opfDecoders.put(new Integer(fpOpcode), decoder);
    }

    // opf (op=2, op3=0x34=FPop1) - Table F -5 - Page 230.
    static {
        addOpfDecoder(FMOVs, new FPMoveDecoder(FMOVs, "fmovs", RTLDT_FL_SINGLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FNEGs, new FP2RegisterDecoder(FNEGs, "fnegs", RTLDT_FL_SINGLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FABSs, new FP2RegisterDecoder(FABSs, "fabss", RTLDT_FL_SINGLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FSQRTs, new FP2RegisterDecoder(FSQRTs, "fsqrts", RTLDT_FL_SINGLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FSQRTd, new FP2RegisterDecoder(FSQRTd, "fsqrtd", RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE));
        addOpfDecoder(FSQRTq, new FP2RegisterDecoder(FSQRTq, "fsqrtq", RTLDT_FL_QUAD, RTLDT_FL_QUAD));
        addOpfDecoder(FADDs, new FPArithmeticDecoder(FADDs, "fadds", RTLOP_ADD, RTLDT_FL_SINGLE, RTLDT_FL_SINGLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FADDd, new FPArithmeticDecoder(FADDd, "faddd", RTLOP_ADD, RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE));
        addOpfDecoder(FADDq, new FPArithmeticDecoder(FADDq, "faddq", RTLOP_ADD, RTLDT_FL_QUAD, RTLDT_FL_QUAD, RTLDT_FL_QUAD));
        addOpfDecoder(FSUBs, new FPArithmeticDecoder(FSUBs, "fsubs", RTLOP_SUB, RTLDT_FL_SINGLE, RTLDT_FL_SINGLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FSUBd, new FPArithmeticDecoder(FSUBd, "fsubd", RTLOP_SUB, RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE));
        addOpfDecoder(FSUBq, new FPArithmeticDecoder(FSUBq, "fsubq", RTLOP_SUB, RTLDT_FL_QUAD, RTLDT_FL_QUAD, RTLDT_FL_QUAD));
        addOpfDecoder(FMULs, new FPArithmeticDecoder(FMULs, "fmuls", RTLOP_SMUL, RTLDT_FL_SINGLE, RTLDT_FL_SINGLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FMULd, new FPArithmeticDecoder(FMULd, "fmuld", RTLOP_SMUL, RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE));
        addOpfDecoder(FMULq, new FPArithmeticDecoder(FMULq, "fmulq",RTLOP_SMUL,  RTLDT_FL_QUAD, RTLDT_FL_QUAD, RTLDT_FL_QUAD));
        addOpfDecoder(FsMULd, new FPArithmeticDecoder(FsMULd, "fsmuld", RTLOP_SMUL, RTLDT_FL_SINGLE, RTLDT_FL_SINGLE, RTLDT_FL_DOUBLE));
        addOpfDecoder(FdMULq, new FPArithmeticDecoder(FdMULq, "fdmulq",RTLOP_SMUL,  RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE, RTLDT_FL_QUAD));
        addOpfDecoder(FDIVs, new FPArithmeticDecoder(FDIVs, "fdivs", RTLOP_SDIV, RTLDT_FL_SINGLE, RTLDT_FL_SINGLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FDIVd, new FPArithmeticDecoder(FDIVd, "fdivd",  RTLOP_SDIV,RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE, RTLDT_FL_DOUBLE));
        addOpfDecoder(FDIVq, new FPArithmeticDecoder(FDIVq, "fdivq",  RTLOP_SDIV,RTLDT_FL_QUAD, RTLDT_FL_QUAD, RTLDT_FL_QUAD));
        addOpfDecoder(FiTOs, new FP2RegisterDecoder(FiTOs, "fitos", RTLDT_FL_SINGLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FiTOd, new FP2RegisterDecoder(FiTOd, "fitod", RTLDT_FL_SINGLE, RTLDT_FL_DOUBLE));
        addOpfDecoder(FiTOq, new FP2RegisterDecoder(FiTOq, "fitoq", RTLDT_FL_SINGLE, RTLDT_FL_QUAD));
        addOpfDecoder(FsTOi, new FP2RegisterDecoder(FsTOi, "fstoi", RTLDT_FL_SINGLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FdTOi, new FP2RegisterDecoder(FdTOi, "fdtoi", RTLDT_FL_DOUBLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FqTOi, new FP2RegisterDecoder(FqTOi, "fqtoi", RTLDT_FL_QUAD, RTLDT_FL_SINGLE));
        addOpfDecoder(FsTOd, new FP2RegisterDecoder(FsTOd, "fstod", RTLDT_FL_SINGLE, RTLDT_FL_DOUBLE));
        addOpfDecoder(FsTOq, new FP2RegisterDecoder(FsTOq, "fstoq", RTLDT_FL_SINGLE, RTLDT_FL_QUAD));
        addOpfDecoder(FdTOs, new FP2RegisterDecoder(FdTOs, "fdtos", RTLDT_FL_DOUBLE, RTLDT_FL_SINGLE));
        addOpfDecoder(FdTOq, new FP2RegisterDecoder(FdTOq, "fdtoq", RTLDT_FL_DOUBLE, RTLDT_FL_QUAD));
        addOpfDecoder(FqTOs, new FP2RegisterDecoder(FqTOs, "fqtos", RTLDT_FL_QUAD, RTLDT_FL_SINGLE));
        addOpfDecoder(FqTOd, new FP2RegisterDecoder(FqTOd, "fqtod", RTLDT_FL_QUAD, RTLDT_FL_DOUBLE));
    }

    InstructionDecoder getOpfDecoder(int opf) {
        return (InstructionDecoder) opfDecoders.get(new Integer(opf));
    }
}
