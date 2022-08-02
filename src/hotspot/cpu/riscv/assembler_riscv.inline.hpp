/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_ASSEMBLER_RISCV_INLINE_HPP
#define CPU_RISCV_ASSEMBLER_RISCV_INLINE_HPP

#include "asm/assembler.inline.hpp"
#include "asm/codeBuffer.hpp"
#include "code/codeCache.hpp"

inline bool is_imm_in_range(long value, unsigned bits, unsigned align_bits) {
  intx sign_bits = (value >> (bits + align_bits - 1));
  return ((value & right_n_bits(align_bits)) == 0) && ((sign_bits == 0) || (sign_bits == -1));
}

inline bool is_unsigned_imm_in_range(intx value, unsigned bits, unsigned align_bits) {
  return (value >= 0) && ((value & right_n_bits(align_bits)) == 0) && ((value >> (align_bits + bits)) == 0);
}

inline bool is_offset_in_range(intx offset, unsigned bits) {
  return is_imm_in_range(offset, bits, 0);
}

#endif // CPU_RISCV_ASSEMBLER_RISCV_INLINE_HPP
