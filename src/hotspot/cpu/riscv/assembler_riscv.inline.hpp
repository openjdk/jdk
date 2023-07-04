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

inline bool Assembler::is_simm5(int64_t x) { return is_simm(x, 5); }
inline bool Assembler::is_simm6(int64_t x) { return is_simm(x, 6); }
inline bool Assembler::is_simm12(int64_t x) { return is_simm(x, 12); }
inline bool Assembler::is_simm13(int64_t x) { return is_simm(x, 13); }
inline bool Assembler::is_simm18(int64_t x) { return is_simm(x, 18); }
inline bool Assembler::is_simm21(int64_t x) { return is_simm(x, 21); }

inline bool Assembler::is_uimm3(uint64_t x) { return is_uimm(x, 3); }
inline bool Assembler::is_uimm5(uint64_t x) { return is_uimm(x, 5); }
inline bool Assembler::is_uimm6(uint64_t x) { return is_uimm(x, 6); }
inline bool Assembler::is_uimm7(uint64_t x) { return is_uimm(x, 7); }
inline bool Assembler::is_uimm8(uint64_t x) { return is_uimm(x, 8); }
inline bool Assembler::is_uimm9(uint64_t x) { return is_uimm(x, 9); }
inline bool Assembler::is_uimm10(uint64_t x) { return is_uimm(x, 10); }

#endif // CPU_RISCV_ASSEMBLER_RISCV_INLINE_HPP
