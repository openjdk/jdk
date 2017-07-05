/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

// make sure the defines don't screw up the declarations later on in this file
#define DONT_USE_REGISTER_DEFINES

#include "incls/_precompiled.incl"
#include "incls/_register_definitions_sparc.cpp.incl"

REGISTER_DEFINITION(Register, noreg);

REGISTER_DEFINITION(Register, G0);
REGISTER_DEFINITION(Register, G1);
REGISTER_DEFINITION(Register, G2);
REGISTER_DEFINITION(Register, G3);
REGISTER_DEFINITION(Register, G4);
REGISTER_DEFINITION(Register, G5);
REGISTER_DEFINITION(Register, G6);
REGISTER_DEFINITION(Register, G7);

REGISTER_DEFINITION(Register, O0);
REGISTER_DEFINITION(Register, O1);
REGISTER_DEFINITION(Register, O2);
REGISTER_DEFINITION(Register, O3);
REGISTER_DEFINITION(Register, O4);
REGISTER_DEFINITION(Register, O5);
REGISTER_DEFINITION(Register, O6);
REGISTER_DEFINITION(Register, O7);

REGISTER_DEFINITION(Register, L0);
REGISTER_DEFINITION(Register, L1);
REGISTER_DEFINITION(Register, L2);
REGISTER_DEFINITION(Register, L3);
REGISTER_DEFINITION(Register, L4);
REGISTER_DEFINITION(Register, L5);
REGISTER_DEFINITION(Register, L6);
REGISTER_DEFINITION(Register, L7);

REGISTER_DEFINITION(Register, I0);
REGISTER_DEFINITION(Register, I1);
REGISTER_DEFINITION(Register, I2);
REGISTER_DEFINITION(Register, I3);
REGISTER_DEFINITION(Register, I4);
REGISTER_DEFINITION(Register, I5);
REGISTER_DEFINITION(Register, I6);
REGISTER_DEFINITION(Register, I7);

REGISTER_DEFINITION(Register, FP);
REGISTER_DEFINITION(Register, SP);

REGISTER_DEFINITION(FloatRegister, fnoreg);
REGISTER_DEFINITION(FloatRegister, F0);
REGISTER_DEFINITION(FloatRegister, F1);
REGISTER_DEFINITION(FloatRegister, F2);
REGISTER_DEFINITION(FloatRegister, F3);
REGISTER_DEFINITION(FloatRegister, F4);
REGISTER_DEFINITION(FloatRegister, F5);
REGISTER_DEFINITION(FloatRegister, F6);
REGISTER_DEFINITION(FloatRegister, F7);
REGISTER_DEFINITION(FloatRegister, F8);
REGISTER_DEFINITION(FloatRegister, F9);
REGISTER_DEFINITION(FloatRegister, F10);
REGISTER_DEFINITION(FloatRegister, F11);
REGISTER_DEFINITION(FloatRegister, F12);
REGISTER_DEFINITION(FloatRegister, F13);
REGISTER_DEFINITION(FloatRegister, F14);
REGISTER_DEFINITION(FloatRegister, F15);
REGISTER_DEFINITION(FloatRegister, F16);
REGISTER_DEFINITION(FloatRegister, F17);
REGISTER_DEFINITION(FloatRegister, F18);
REGISTER_DEFINITION(FloatRegister, F19);
REGISTER_DEFINITION(FloatRegister, F20);
REGISTER_DEFINITION(FloatRegister, F21);
REGISTER_DEFINITION(FloatRegister, F22);
REGISTER_DEFINITION(FloatRegister, F23);
REGISTER_DEFINITION(FloatRegister, F24);
REGISTER_DEFINITION(FloatRegister, F25);
REGISTER_DEFINITION(FloatRegister, F26);
REGISTER_DEFINITION(FloatRegister, F27);
REGISTER_DEFINITION(FloatRegister, F28);
REGISTER_DEFINITION(FloatRegister, F29);
REGISTER_DEFINITION(FloatRegister, F30);
REGISTER_DEFINITION(FloatRegister, F31);
REGISTER_DEFINITION(FloatRegister, F32);
REGISTER_DEFINITION(FloatRegister, F34);
REGISTER_DEFINITION(FloatRegister, F36);
REGISTER_DEFINITION(FloatRegister, F38);
REGISTER_DEFINITION(FloatRegister, F40);
REGISTER_DEFINITION(FloatRegister, F42);
REGISTER_DEFINITION(FloatRegister, F44);
REGISTER_DEFINITION(FloatRegister, F46);
REGISTER_DEFINITION(FloatRegister, F48);
REGISTER_DEFINITION(FloatRegister, F50);
REGISTER_DEFINITION(FloatRegister, F52);
REGISTER_DEFINITION(FloatRegister, F54);
REGISTER_DEFINITION(FloatRegister, F56);
REGISTER_DEFINITION(FloatRegister, F58);
REGISTER_DEFINITION(FloatRegister, F60);
REGISTER_DEFINITION(FloatRegister, F62);


REGISTER_DEFINITION(     Register, Otos_i);
REGISTER_DEFINITION(     Register, Otos_l);
REGISTER_DEFINITION(     Register, Otos_l1);
REGISTER_DEFINITION(     Register, Otos_l2);
REGISTER_DEFINITION(FloatRegister, Ftos_f);
REGISTER_DEFINITION(FloatRegister, Ftos_d);
REGISTER_DEFINITION(FloatRegister, Ftos_d1);
REGISTER_DEFINITION(FloatRegister, Ftos_d2);


REGISTER_DEFINITION(Register, G2_thread);
REGISTER_DEFINITION(Register, G6_heapbase);
REGISTER_DEFINITION(Register, G5_method);
REGISTER_DEFINITION(Register, G5_megamorphic_method);
REGISTER_DEFINITION(Register, G5_inline_cache_reg);
REGISTER_DEFINITION(Register, Gargs);
REGISTER_DEFINITION(Register, L7_thread_cache);
REGISTER_DEFINITION(Register, Gframe_size);
REGISTER_DEFINITION(Register, G1_scratch);
REGISTER_DEFINITION(Register, G3_scratch);
REGISTER_DEFINITION(Register, G4_scratch);
REGISTER_DEFINITION(Register, Gtemp);
REGISTER_DEFINITION(Register, Lentry_args);

// JSR 292
REGISTER_DEFINITION(Register, G5_method_type);
REGISTER_DEFINITION(Register, G3_method_handle);
REGISTER_DEFINITION(Register, L7_mh_SP_save);

#ifdef CC_INTERP
REGISTER_DEFINITION(Register, Lstate);
REGISTER_DEFINITION(Register, L1_scratch);
REGISTER_DEFINITION(Register, Lmirror);
REGISTER_DEFINITION(Register, L2_scratch);
REGISTER_DEFINITION(Register, L3_scratch);
REGISTER_DEFINITION(Register, L4_scratch);
REGISTER_DEFINITION(Register, Lscratch);
REGISTER_DEFINITION(Register, Lscratch2);
REGISTER_DEFINITION(Register, L7_scratch);
REGISTER_DEFINITION(Register, I5_savedSP);
#else // CC_INTERP
REGISTER_DEFINITION(Register, Lesp);
REGISTER_DEFINITION(Register, Lbcp);
REGISTER_DEFINITION(Register, Lmonitors);
REGISTER_DEFINITION(Register, Lbyte_code);
REGISTER_DEFINITION(Register, Llast_SP);
REGISTER_DEFINITION(Register, Lscratch);
REGISTER_DEFINITION(Register, Lscratch2);
REGISTER_DEFINITION(Register, LcpoolCache);
REGISTER_DEFINITION(Register, I5_savedSP);
REGISTER_DEFINITION(Register, O5_savedSP);
REGISTER_DEFINITION(Register, IdispatchAddress);
REGISTER_DEFINITION(Register, ImethodDataPtr);
REGISTER_DEFINITION(Register, IdispatchTables);
#endif // CC_INTERP
REGISTER_DEFINITION(Register, Lmethod);
REGISTER_DEFINITION(Register, Llocals);
REGISTER_DEFINITION(Register, Oexception);
REGISTER_DEFINITION(Register, Oissuing_pc);
