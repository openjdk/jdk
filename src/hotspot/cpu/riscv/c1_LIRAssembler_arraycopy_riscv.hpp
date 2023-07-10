/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_RISCV_C1_LIRASSEMBLER_ARRAYCOPY_RISCV_HPP
#define CPU_RISCV_C1_LIRASSEMBLER_ARRAYCOPY_RISCV_HPP

  // arraycopy sub functions
  void generic_arraycopy(Register src, Register src_pos, Register length,
                         Register dst, Register dst_pos, CodeStub *stub);
  void arraycopy_simple_check(Register src, Register src_pos, Register length,
                              Register dst, Register dst_pos, Register tmp,
                              CodeStub *stub, int flags);
  void arraycopy_checkcast(Register src, Register src_pos, Register length,
                           Register dst, Register dst_pos, Register tmp,
                           CodeStub *stub, BasicType basic_type,
                           address copyfunc_addr, int flags);
  void arraycopy_type_check(Register src, Register src_pos, Register length,
                            Register dst, Register dst_pos, Register tmp,
                            CodeStub *stub, BasicType basic_type, int flags);
  void arraycopy_assert(Register src, Register dst, Register tmp, ciArrayKlass *default_type, int flags);
  void arraycopy_prepare_params(Register src, Register src_pos, Register length,
                                Register dst, Register dst_pos, BasicType basic_type);
  void arraycopy_checkcast_prepare_params(Register src, Register src_pos, Register length,
                                          Register dst, Register dst_pos, BasicType basic_type);
  void arraycopy_store_args(Register src, Register src_pos, Register length,
                            Register dst, Register dst_pos);
  void arraycopy_load_args(Register src, Register src_pos, Register length,
                           Register dst, Register dst_pos);

#endif // CPU_RISCV_C1_LIRASSEMBLER_ARRAYCOPY_RISCV_HPP
