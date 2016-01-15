/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_VM_VMSTRUCTS_X86_HPP
#define CPU_X86_VM_VMSTRUCTS_X86_HPP

// These are the CPU-specific fields, types and integer
// constants required by the Serviceability Agent. This file is
// referenced by vmStructs.cpp.

#define VM_STRUCTS_CPU(nonstatic_field, static_field, unchecked_nonstatic_field, volatile_nonstatic_field, nonproduct_nonstatic_field, c2_nonstatic_field, unchecked_c1_static_field, unchecked_c2_static_field)            \
  volatile_nonstatic_field(JavaFrameAnchor, _last_Java_fp, intptr_t*)

#define VM_TYPES_CPU(declare_type, declare_toplevel_type, declare_oop_type, declare_integer_type, declare_unsigned_integer_type, declare_c1_toplevel_type, declare_c2_type, declare_c2_toplevel_type) \

#define VM_INT_CONSTANTS_CPU(declare_constant, declare_preprocessor_constant, declare_c1_constant, declare_c2_constant, declare_c2_preprocessor_constant) \
  LP64_ONLY(declare_constant(frame::arg_reg_save_area_bytes))       \
  declare_constant(frame::interpreter_frame_sender_sp_offset)       \
  declare_constant(frame::interpreter_frame_last_sp_offset)         \
  declare_constant(VM_Version::CPU_CX8)                             \
  declare_constant(VM_Version::CPU_CMOV)                            \
  declare_constant(VM_Version::CPU_FXSR)                            \
  declare_constant(VM_Version::CPU_HT)                              \
  declare_constant(VM_Version::CPU_MMX)                             \
  declare_constant(VM_Version::CPU_3DNOW_PREFETCH)                  \
  declare_constant(VM_Version::CPU_SSE)                             \
  declare_constant(VM_Version::CPU_SSE2)                            \
  declare_constant(VM_Version::CPU_SSE3)                            \
  declare_constant(VM_Version::CPU_SSSE3)                           \
  declare_constant(VM_Version::CPU_SSE4A)                           \
  declare_constant(VM_Version::CPU_SSE4_1)                          \
  declare_constant(VM_Version::CPU_SSE4_2)                          \
  declare_constant(VM_Version::CPU_POPCNT)                          \
  declare_constant(VM_Version::CPU_LZCNT)                           \
  declare_constant(VM_Version::CPU_TSC)                             \
  declare_constant(VM_Version::CPU_TSCINV)                          \
  declare_constant(VM_Version::CPU_AVX)                             \
  declare_constant(VM_Version::CPU_AVX2)                            \
  declare_constant(VM_Version::CPU_AES)                             \
  declare_constant(VM_Version::CPU_ERMS)                            \
  declare_constant(VM_Version::CPU_CLMUL)                           \
  declare_constant(VM_Version::CPU_BMI1)                            \
  declare_constant(VM_Version::CPU_BMI2)                            \
  declare_constant(VM_Version::CPU_RTM)                             \
  declare_constant(VM_Version::CPU_ADX)                             \
  declare_constant(VM_Version::CPU_AVX512F)                         \
  declare_constant(VM_Version::CPU_AVX512DQ)                        \
  declare_constant(VM_Version::CPU_AVX512PF)                        \
  declare_constant(VM_Version::CPU_AVX512ER)                        \
  declare_constant(VM_Version::CPU_AVX512CD)                        \
  declare_constant(VM_Version::CPU_AVX512BW)

#define VM_LONG_CONSTANTS_CPU(declare_constant, declare_preprocessor_constant, declare_c1_constant, declare_c2_constant, declare_c2_preprocessor_constant) \
  declare_preprocessor_constant("VM_Version::CPU_AVX512VL", CPU_AVX512VL)

#endif // CPU_X86_VM_VMSTRUCTS_X86_HPP
