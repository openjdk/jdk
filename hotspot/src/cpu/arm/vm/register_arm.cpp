/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "register_arm.hpp"
#include "utilities/debug.hpp"

const int ConcreteRegisterImpl::max_gpr = ConcreteRegisterImpl::num_gpr;
const int ConcreteRegisterImpl::max_fpr = ConcreteRegisterImpl::num_fpr +
                                          ConcreteRegisterImpl::max_gpr;

const char* RegisterImpl::name() const {
  const char* names[number_of_registers] = {
#ifdef AARCH64
    "x0",  "x1",  "x2",  "x3",  "x4",  "x5",  "x6",  "x7",
    "x8",  "x9",  "x10", "x11", "x12", "x13", "x14", "x15",
    "x16", "x17", "x18", "x19", "x20", "x21", "x22", "x23",
    "x24", "x25", "x26", "x27", "x28", "fp",  "lr",  "xzr", "sp"
#else
    "r0", "r1", "r2", "r3", "r4", "r5", "r6",
#if (FP_REG_NUM == 7)
    "fp",
#else
    "r7",
#endif
    "r8", "r9", "r10",
#if (FP_REG_NUM == 11)
    "fp",
#else
    "r11",
#endif
    "r12", "sp", "lr", "pc"
#endif // AARCH64
  };
  return is_valid() ? names[encoding()] : "noreg";
}

const char* FloatRegisterImpl::name() const {
  const char* names[number_of_registers] = {
#ifdef AARCH64
    "v0",  "v1",  "v2",  "v3",  "v4",  "v5",  "v6",  "v7",
    "v8",  "v9",  "v10", "v11", "v12", "v13", "v14", "v15",
    "v16", "v17", "v18", "v19", "v20", "v21", "v22", "v23",
    "v24", "v25", "v26", "v27", "v28", "v29", "v30", "v31"
#else
     "s0",  "s1",  "s2",  "s3",  "s4",  "s5",  "s6",  "s7",
     "s8",  "s9", "s10", "s11", "s12", "s13", "s14", "s15",
    "s16", "s17", "s18", "s19", "s20", "s21", "s22", "s23",
    "s24", "s25", "s26", "s27", "s28", "s29", "s30", "s31"
#ifdef COMPILER2
   ,"s32", "s33?","s34", "s35?","s36", "s37?","s38", "s39?",
    "s40", "s41?","s42", "s43?","s44", "s45?","s46", "s47?",
    "s48", "s49?","s50", "s51?","s52", "s53?","s54", "s55?",
    "s56", "s57?","s58", "s59?","s60", "s61?","s62", "s63?"
#endif
#endif // AARCH64
  };
  return is_valid() ? names[encoding()] : "fnoreg";
}
