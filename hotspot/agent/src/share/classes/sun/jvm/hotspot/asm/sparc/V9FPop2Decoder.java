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

class V9FPop2Decoder extends FPopDecoder
                     implements V9InstructionDecoder {
    static Map v9fpop2Decoders = new HashMap(); // Map<Integer, InstructionDecoder>
    static void addV9FPop2Decoder(int fpOpcode, InstructionDecoder decoder) {
        v9fpop2Decoders.put(new Integer(fpOpcode), decoder);
    }

    static {
        // flag conditional moves

        addV9FPop2Decoder(FMOVs_fcc0, new V9FMOVccDecoder(FMOVs_fcc0, RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVs_fcc1, new V9FMOVccDecoder(FMOVs_fcc1, RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVs_fcc2, new V9FMOVccDecoder(FMOVs_fcc2, RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVs_fcc3, new V9FMOVccDecoder(FMOVs_fcc3, RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVs_icc, new V9FMOVccDecoder(FMOVs_icc, RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVs_xcc, new V9FMOVccDecoder(FMOVs_xcc, RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVd_fcc0, new V9FMOVccDecoder(FMOVd_fcc0, RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVd_fcc1, new V9FMOVccDecoder(FMOVd_fcc1, RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVd_fcc2, new V9FMOVccDecoder(FMOVd_fcc2, RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVd_fcc3, new V9FMOVccDecoder(FMOVd_fcc3, RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVd_icc, new V9FMOVccDecoder(FMOVd_icc, RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVd_xcc, new V9FMOVccDecoder(FMOVd_xcc, RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVq_fcc0, new V9FMOVccDecoder(FMOVq_fcc0, RTLDT_FL_QUAD));
        addV9FPop2Decoder(FMOVq_fcc1, new V9FMOVccDecoder(FMOVq_fcc1, RTLDT_FL_QUAD));
        addV9FPop2Decoder(FMOVq_fcc2, new V9FMOVccDecoder(FMOVq_fcc2, RTLDT_FL_QUAD));
        addV9FPop2Decoder(FMOVq_fcc3, new V9FMOVccDecoder(FMOVq_fcc3, RTLDT_FL_QUAD));
        addV9FPop2Decoder(FMOVq_icc, new V9FMOVccDecoder(FMOVq_icc, RTLDT_FL_QUAD));
        addV9FPop2Decoder(FMOVq_xcc, new V9FMOVccDecoder(FMOVq_xcc, RTLDT_FL_QUAD));

        // register conditional moves

        addV9FPop2Decoder(FMOVRsZ, new V9FMOVrDecoder(FMOVRsZ, "fmovrsz", RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVRsLEZ, new V9FMOVrDecoder(FMOVRsLEZ, "fmovrslez", RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVRsLZ, new V9FMOVrDecoder(FMOVRsLZ, "fmovrslz", RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVRsNZ, new V9FMOVrDecoder(FMOVRsNZ, "fmovrsnz", RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVRsGZ, new V9FMOVrDecoder(FMOVRsGZ, "fmovrsgz", RTLDT_FL_SINGLE));
        addV9FPop2Decoder(FMOVRsGEZ, new V9FMOVrDecoder(FMOVRsGEZ, "fmovrsgez", RTLDT_FL_SINGLE));

        addV9FPop2Decoder(FMOVRdZ, new V9FMOVrDecoder(FMOVRdZ, "fmovrdz", RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVRdLEZ, new V9FMOVrDecoder(FMOVRdLEZ, "fmovrdlez", RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVRdLZ, new V9FMOVrDecoder(FMOVRdLZ, "fmovrdlz", RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVRdNZ, new V9FMOVrDecoder(FMOVRdNZ, "fmovrdnz", RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVRdGZ, new V9FMOVrDecoder(FMOVRdGZ, "fmovrdgz", RTLDT_FL_DOUBLE));
        addV9FPop2Decoder(FMOVRdGEZ, new V9FMOVrDecoder(FMOVRdGEZ, "fmovrdgez", RTLDT_FL_DOUBLE));

        addV9FPop2Decoder(FMOVRqZ, new V9FMOVrDecoder(FMOVRqZ, "fmovrqz", RTLDT_FL_QUAD));
        addV9FPop2Decoder(FMOVRqLEZ, new V9FMOVrDecoder(FMOVRqLEZ, "fmovrqlez", RTLDT_FL_QUAD));
        addV9FPop2Decoder(FMOVRqLZ, new V9FMOVrDecoder(FMOVRqLZ, "fmovrqlz", RTLDT_FL_QUAD));
        addV9FPop2Decoder(FMOVRqNZ, new V9FMOVrDecoder(FMOVRqNZ, "fmovrqnz", RTLDT_FL_QUAD));
        addV9FPop2Decoder(FMOVRqGZ, new V9FMOVrDecoder(FMOVRqGZ, "fmovrqgz", RTLDT_FL_QUAD));
        addV9FPop2Decoder(FMOVRqGEZ, new V9FMOVrDecoder(FMOVRqGEZ, "fmovrqgez", RTLDT_FL_QUAD));
    }

    InstructionDecoder getOpfDecoder(int opf) {
        InstructionDecoder decoder = (InstructionDecoder) V8FPop2Decoder.fpop2Decoders.get(new Integer(opf));
        return (decoder != null) ? decoder : (InstructionDecoder) v9fpop2Decoders.get(new Integer(opf));
    }
}
