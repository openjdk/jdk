/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_VMSTRUCTS_SPARC_HPP
#define CPU_SPARC_VM_VMSTRUCTS_SPARC_HPP

// These are the CPU-specific fields, types and integer
// constants required by the Serviceability Agent. This file is
// referenced by vmStructs.cpp.

#define VM_STRUCTS_CPU(nonstatic_field, static_field, unchecked_nonstatic_field, volatile_nonstatic_field, nonproduct_nonstatic_field, c2_nonstatic_field, unchecked_c1_static_field, unchecked_c2_static_field)            \
 \
  /******************************/                                                                                                   \
  /* JavaCallWrapper            */                                                                                                   \
  /******************************/                                                                                                   \
  /******************************/                                                                                                   \
  /* JavaFrameAnchor            */                                                                                                   \
  /******************************/                                                                                                   \
  volatile_nonstatic_field(JavaFrameAnchor,     _flags,                                          int)                                \
  static_field(VM_Version, _features, int)

#define VM_TYPES_CPU(declare_type, declare_toplevel_type, declare_oop_type, declare_integer_type, declare_unsigned_integer_type, declare_c1_toplevel_type, declare_c2_type, declare_c2_toplevel_type) \
  declare_toplevel_type(VM_Version)

#define VM_INT_CONSTANTS_CPU(declare_constant, declare_preprocessor_constant, declare_c1_constant, declare_c2_constant, declare_c2_preprocessor_constant)                                                              \
  /******************************/                                        \
  /* Register numbers (C2 only) */                                        \
  /******************************/                                        \
                                                                          \
  declare_c2_constant(R_L0_num)                                           \
  declare_c2_constant(R_L1_num)                                           \
  declare_c2_constant(R_L2_num)                                           \
  declare_c2_constant(R_L3_num)                                           \
  declare_c2_constant(R_L4_num)                                           \
  declare_c2_constant(R_L5_num)                                           \
  declare_c2_constant(R_L6_num)                                           \
  declare_c2_constant(R_L7_num)                                           \
  declare_c2_constant(R_I0_num)                                           \
  declare_c2_constant(R_I1_num)                                           \
  declare_c2_constant(R_I2_num)                                           \
  declare_c2_constant(R_I3_num)                                           \
  declare_c2_constant(R_I4_num)                                           \
  declare_c2_constant(R_I5_num)                                           \
  declare_c2_constant(R_FP_num)                                           \
  declare_c2_constant(R_I7_num)                                           \
  declare_c2_constant(R_O0_num)                                           \
  declare_c2_constant(R_O1_num)                                           \
  declare_c2_constant(R_O2_num)                                           \
  declare_c2_constant(R_O3_num)                                           \
  declare_c2_constant(R_O4_num)                                           \
  declare_c2_constant(R_O5_num)                                           \
  declare_c2_constant(R_SP_num)                                           \
  declare_c2_constant(R_O7_num)                                           \
  declare_c2_constant(R_G0_num)                                           \
  declare_c2_constant(R_G1_num)                                           \
  declare_c2_constant(R_G2_num)                                           \
  declare_c2_constant(R_G3_num)                                           \
  declare_c2_constant(R_G4_num)                                           \
  declare_c2_constant(R_G5_num)                                           \
  declare_c2_constant(R_G6_num)                                           \
  declare_c2_constant(R_G7_num)                                           \
  declare_constant(VM_Version::vis1_instructions_m)                       \
  declare_constant(VM_Version::vis2_instructions_m)                       \
  declare_constant(VM_Version::vis3_instructions_m)                       \
  declare_constant(VM_Version::cbcond_instructions_m)

#define VM_LONG_CONSTANTS_CPU(declare_constant, declare_preprocessor_constant, declare_c1_constant, declare_c2_constant, declare_c2_preprocessor_constant)

#endif // CPU_SPARC_VM_VMSTRUCTS_SPARC_HPP
