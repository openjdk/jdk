/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_STUBROUTINES_RISCV_HPP
#define CPU_RISCV_STUBROUTINES_RISCV_HPP

// This file holds the platform specific parts of the StubRoutines
// definition. See stubRoutines.hpp for a description on how to
// extend it.

static bool returns_to_call_stub(address return_pc) {
  return return_pc == _call_stub_return_address;
}

enum platform_dependent_constants {
  // simply increase sizes if too small (assembler will crash if too small)
  _initial_stubs_code_size      = 10000,
  _continuation_stubs_code_size =  2000,
  _compiler_stubs_code_size     = 45000,
  _final_stubs_code_size        = 20000 ZGC_ONLY(+10000)
};

class riscv {
 friend class StubGenerator;

 private:
  static address _zero_blocks;

  static address _compare_long_string_LL;
  static address _compare_long_string_LU;
  static address _compare_long_string_UL;
  static address _compare_long_string_UU;
  static address _string_indexof_linear_ll;
  static address _string_indexof_linear_uu;
  static address _string_indexof_linear_ul;

  static bool _completed;

 public:

  static address zero_blocks() {
    return _zero_blocks;
  }

  static address compare_long_string_LL() {
    return _compare_long_string_LL;
  }

  static address compare_long_string_LU() {
    return _compare_long_string_LU;
  }

  static address compare_long_string_UL() {
    return _compare_long_string_UL;
  }

  static address compare_long_string_UU() {
    return _compare_long_string_UU;
  }

  static address string_indexof_linear_ul() {
    return _string_indexof_linear_ul;
  }

  static address string_indexof_linear_ll() {
    return _string_indexof_linear_ll;
  }

  static address string_indexof_linear_uu() {
    return _string_indexof_linear_uu;
  }

  static bool complete() {
    return _completed;
  }

  static void set_completed() {
    _completed = true;
  }

private:
  static juint    _crc_table[];
};

#endif // CPU_RISCV_STUBROUTINES_RISCV_HPP
