/*
 * Copyright (c) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/assembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "asm/register.hpp"
#include "interp_masm_riscv.hpp"
#include "register_riscv.hpp"

REGISTER_DEFINITION(Register, noreg);

REGISTER_DEFINITION(Register, x0);
REGISTER_DEFINITION(Register, x1);
REGISTER_DEFINITION(Register, x2);
REGISTER_DEFINITION(Register, x3);
REGISTER_DEFINITION(Register, x4);
REGISTER_DEFINITION(Register, x5);
REGISTER_DEFINITION(Register, x6);
REGISTER_DEFINITION(Register, x7);
REGISTER_DEFINITION(Register, x8);
REGISTER_DEFINITION(Register, x9);
REGISTER_DEFINITION(Register, x10);
REGISTER_DEFINITION(Register, x11);
REGISTER_DEFINITION(Register, x12);
REGISTER_DEFINITION(Register, x13);
REGISTER_DEFINITION(Register, x14);
REGISTER_DEFINITION(Register, x15);
REGISTER_DEFINITION(Register, x16);
REGISTER_DEFINITION(Register, x17);
REGISTER_DEFINITION(Register, x18);
REGISTER_DEFINITION(Register, x19);
REGISTER_DEFINITION(Register, x20);
REGISTER_DEFINITION(Register, x21);
REGISTER_DEFINITION(Register, x22);
REGISTER_DEFINITION(Register, x23);
REGISTER_DEFINITION(Register, x24);
REGISTER_DEFINITION(Register, x25);
REGISTER_DEFINITION(Register, x26);
REGISTER_DEFINITION(Register, x27);
REGISTER_DEFINITION(Register, x28);
REGISTER_DEFINITION(Register, x29);
REGISTER_DEFINITION(Register, x30);
REGISTER_DEFINITION(Register, x31);

REGISTER_DEFINITION(FloatRegister, fnoreg);

REGISTER_DEFINITION(FloatRegister, f0);
REGISTER_DEFINITION(FloatRegister, f1);
REGISTER_DEFINITION(FloatRegister, f2);
REGISTER_DEFINITION(FloatRegister, f3);
REGISTER_DEFINITION(FloatRegister, f4);
REGISTER_DEFINITION(FloatRegister, f5);
REGISTER_DEFINITION(FloatRegister, f6);
REGISTER_DEFINITION(FloatRegister, f7);
REGISTER_DEFINITION(FloatRegister, f8);
REGISTER_DEFINITION(FloatRegister, f9);
REGISTER_DEFINITION(FloatRegister, f10);
REGISTER_DEFINITION(FloatRegister, f11);
REGISTER_DEFINITION(FloatRegister, f12);
REGISTER_DEFINITION(FloatRegister, f13);
REGISTER_DEFINITION(FloatRegister, f14);
REGISTER_DEFINITION(FloatRegister, f15);
REGISTER_DEFINITION(FloatRegister, f16);
REGISTER_DEFINITION(FloatRegister, f17);
REGISTER_DEFINITION(FloatRegister, f18);
REGISTER_DEFINITION(FloatRegister, f19);
REGISTER_DEFINITION(FloatRegister, f20);
REGISTER_DEFINITION(FloatRegister, f21);
REGISTER_DEFINITION(FloatRegister, f22);
REGISTER_DEFINITION(FloatRegister, f23);
REGISTER_DEFINITION(FloatRegister, f24);
REGISTER_DEFINITION(FloatRegister, f25);
REGISTER_DEFINITION(FloatRegister, f26);
REGISTER_DEFINITION(FloatRegister, f27);
REGISTER_DEFINITION(FloatRegister, f28);
REGISTER_DEFINITION(FloatRegister, f29);
REGISTER_DEFINITION(FloatRegister, f30);
REGISTER_DEFINITION(FloatRegister, f31);

REGISTER_DEFINITION(VectorRegister, vnoreg);

REGISTER_DEFINITION(VectorRegister, v0);
REGISTER_DEFINITION(VectorRegister, v1);
REGISTER_DEFINITION(VectorRegister, v2);
REGISTER_DEFINITION(VectorRegister, v3);
REGISTER_DEFINITION(VectorRegister, v4);
REGISTER_DEFINITION(VectorRegister, v5);
REGISTER_DEFINITION(VectorRegister, v6);
REGISTER_DEFINITION(VectorRegister, v7);
REGISTER_DEFINITION(VectorRegister, v8);
REGISTER_DEFINITION(VectorRegister, v9);
REGISTER_DEFINITION(VectorRegister, v10);
REGISTER_DEFINITION(VectorRegister, v11);
REGISTER_DEFINITION(VectorRegister, v12);
REGISTER_DEFINITION(VectorRegister, v13);
REGISTER_DEFINITION(VectorRegister, v14);
REGISTER_DEFINITION(VectorRegister, v15);
REGISTER_DEFINITION(VectorRegister, v16);
REGISTER_DEFINITION(VectorRegister, v17);
REGISTER_DEFINITION(VectorRegister, v18);
REGISTER_DEFINITION(VectorRegister, v19);
REGISTER_DEFINITION(VectorRegister, v20);
REGISTER_DEFINITION(VectorRegister, v21);
REGISTER_DEFINITION(VectorRegister, v22);
REGISTER_DEFINITION(VectorRegister, v23);
REGISTER_DEFINITION(VectorRegister, v24);
REGISTER_DEFINITION(VectorRegister, v25);
REGISTER_DEFINITION(VectorRegister, v26);
REGISTER_DEFINITION(VectorRegister, v27);
REGISTER_DEFINITION(VectorRegister, v28);
REGISTER_DEFINITION(VectorRegister, v29);
REGISTER_DEFINITION(VectorRegister, v30);
REGISTER_DEFINITION(VectorRegister, v31);

REGISTER_DEFINITION(Register, c_rarg0);
REGISTER_DEFINITION(Register, c_rarg1);
REGISTER_DEFINITION(Register, c_rarg2);
REGISTER_DEFINITION(Register, c_rarg3);
REGISTER_DEFINITION(Register, c_rarg4);
REGISTER_DEFINITION(Register, c_rarg5);
REGISTER_DEFINITION(Register, c_rarg6);
REGISTER_DEFINITION(Register, c_rarg7);

REGISTER_DEFINITION(FloatRegister, c_farg0);
REGISTER_DEFINITION(FloatRegister, c_farg1);
REGISTER_DEFINITION(FloatRegister, c_farg2);
REGISTER_DEFINITION(FloatRegister, c_farg3);
REGISTER_DEFINITION(FloatRegister, c_farg4);
REGISTER_DEFINITION(FloatRegister, c_farg5);
REGISTER_DEFINITION(FloatRegister, c_farg6);
REGISTER_DEFINITION(FloatRegister, c_farg7);

REGISTER_DEFINITION(Register, j_rarg0);
REGISTER_DEFINITION(Register, j_rarg1);
REGISTER_DEFINITION(Register, j_rarg2);
REGISTER_DEFINITION(Register, j_rarg3);
REGISTER_DEFINITION(Register, j_rarg4);
REGISTER_DEFINITION(Register, j_rarg5);
REGISTER_DEFINITION(Register, j_rarg6);
REGISTER_DEFINITION(Register, j_rarg7);

REGISTER_DEFINITION(FloatRegister, j_farg0);
REGISTER_DEFINITION(FloatRegister, j_farg1);
REGISTER_DEFINITION(FloatRegister, j_farg2);
REGISTER_DEFINITION(FloatRegister, j_farg3);
REGISTER_DEFINITION(FloatRegister, j_farg4);
REGISTER_DEFINITION(FloatRegister, j_farg5);
REGISTER_DEFINITION(FloatRegister, j_farg6);
REGISTER_DEFINITION(FloatRegister, j_farg7);

REGISTER_DEFINITION(Register, zr);
REGISTER_DEFINITION(Register, gp);
REGISTER_DEFINITION(Register, tp);
REGISTER_DEFINITION(Register, xmethod);
REGISTER_DEFINITION(Register, ra);
REGISTER_DEFINITION(Register, sp);
REGISTER_DEFINITION(Register, fp);
REGISTER_DEFINITION(Register, xheapbase);
REGISTER_DEFINITION(Register, xcpool);
REGISTER_DEFINITION(Register, xmonitors);
REGISTER_DEFINITION(Register, xlocals);
REGISTER_DEFINITION(Register, xthread);
REGISTER_DEFINITION(Register, xbcp);
REGISTER_DEFINITION(Register, xdispatch);
REGISTER_DEFINITION(Register, esp);

REGISTER_DEFINITION(Register, t0);
REGISTER_DEFINITION(Register, t1);
REGISTER_DEFINITION(Register, t2);
