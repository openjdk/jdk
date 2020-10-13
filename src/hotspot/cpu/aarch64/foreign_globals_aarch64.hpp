/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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
 */

#include "asm/macroAssembler.hpp"
#include "utilities/growableArray.hpp"

#ifndef CPU_AARCH64_VM_FOREIGN_GLOBALS_AARCH64_HPP
#define CPU_AARCH64_VM_FOREIGN_GLOBALS_AARCH64_HPP

#define __ _masm->

struct VectorRegister {
  static const size_t VECTOR_MAX_WIDTH_BITS = 128;
  static const size_t VECTOR_MAX_WIDTH_BYTES = VECTOR_MAX_WIDTH_BITS / 8;
  static const size_t VECTOR_MAX_WIDTH_U64S = VECTOR_MAX_WIDTH_BITS / 64;
  static const size_t VECTOR_MAX_WIDTH_FLOATS = VECTOR_MAX_WIDTH_BITS / 32;
  static const size_t VECTOR_MAX_WIDTH_DOUBLES = VECTOR_MAX_WIDTH_BITS / 64;

  union {
    uint8_t bits[VECTOR_MAX_WIDTH_BYTES];
    uint64_t u64[VECTOR_MAX_WIDTH_U64S];
    float f[VECTOR_MAX_WIDTH_FLOATS];
    double d[VECTOR_MAX_WIDTH_DOUBLES];
  };
};

struct ABIDescriptor {
  GrowableArray<Register> _integer_argument_registers;
  GrowableArray<Register> _integer_return_registers;
  GrowableArray<FloatRegister> _vector_argument_registers;
  GrowableArray<FloatRegister> _vector_return_registers;

  GrowableArray<Register> _integer_additional_volatile_registers;
  GrowableArray<FloatRegister> _vector_additional_volatile_registers;

  int32_t _stack_alignment_bytes;
  int32_t _shadow_space_bytes;

  bool is_volatile_reg(Register reg) const;
  bool is_volatile_reg(FloatRegister reg) const;
};

struct BufferLayout {
  size_t stack_args_bytes;
  size_t stack_args;
  size_t arguments_vector;
  size_t arguments_integer;
  size_t arguments_next_pc;
  size_t returns_vector;
  size_t returns_integer;
  size_t buffer_size;
};

const ABIDescriptor parseABIDescriptor(JNIEnv* env, jobject jabi);
const BufferLayout parseBufferLayout(JNIEnv* env, jobject jlayout);

#endif // CPU_AARCH64_VM_FOREIGN_GLOBALS_AARCH64_HPP
