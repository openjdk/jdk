/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Rivos Inc. All rights reserved.
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

#include "precompiled.hpp"

#if (defined(RISCV) || defined(RISCV64)) && !defined(ZERO)

#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/orderAccess.hpp"
#include "unittest.hpp"

typedef int64_t (*zicond_func)(int64_t cmp1, int64_t cmp2, int64_t dst, int64_t src);
typedef void (MacroAssembler::*cmov_func)(Register cmp1, Register cmp2, Register dst, Register src);

class CmovTester {
 public:
  static void test(cmov_func func, int64_t a0, int64_t a1, int64_t a2, int64_t a3, int64_t result) {
    BufferBlob* bb = BufferBlob::create("riscvTest", 128);
    CodeBuffer code(bb);
    MacroAssembler _masm(&code);
    address entry = _masm.pc();
    {
      ((&_masm)->*func)(c_rarg0, c_rarg1, c_rarg2, c_rarg3);
      _masm.mv(c_rarg0, c_rarg2);
      _masm.ret();
    }
    _masm.flush();
    OrderAccess::cross_modify_fence();
    int64_t ret = ((zicond_func)entry)(a0, a1, a2, a3);
    ASSERT_EQ(ret, result);
    BufferBlob::free(bb);
  }
};

void run_cmov_tests() {
  // If 42(a0) eq 42(a1): assign dest(a2/66) the src(a3/77), expect result: 77
  CmovTester::test(&MacroAssembler::cmov_eq, 42, 42, 66, 77, 77);
  // If 41(a0) eq 42(a1): assign dest(a2/66) the src(a3/77), expect result: 66
  CmovTester::test(&MacroAssembler::cmov_eq, 41, 42, 66, 77, 66);

  CmovTester::test(&MacroAssembler::cmov_ne, 41, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_ne, 42, 42, 66, 77, 66);

  CmovTester::test(&MacroAssembler::cmov_le, 41, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_le, 42, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_le, 42, -1, 66, 77, 66);

  CmovTester::test(&MacroAssembler::cmov_leu, 41, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_leu, 42, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_leu, -1, 42, 66, 77, 66);

  CmovTester::test(&MacroAssembler::cmov_ge, 43, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_ge, 42, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_ge, -1, 42, 66, 77, 66);

  CmovTester::test(&MacroAssembler::cmov_geu, 43, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_geu, 42, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_geu, 42, -1, 66, 77, 66);

  CmovTester::test(&MacroAssembler::cmov_lt, 41, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_lt, 42, 42, 66, 77, 66);
  CmovTester::test(&MacroAssembler::cmov_lt, 42, -1, 66, 77, 66);

  CmovTester::test(&MacroAssembler::cmov_ltu, 41, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_ltu, 42, 42, 66, 77, 66);
  CmovTester::test(&MacroAssembler::cmov_ltu, -1, 42, 66, 77, 66);

  CmovTester::test(&MacroAssembler::cmov_gt, 43, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_gt, 42, 42, 66, 77, 66);
  CmovTester::test(&MacroAssembler::cmov_gt, -1, 42, 66, 77, 66);

  CmovTester::test(&MacroAssembler::cmov_gtu, 43, 42, 66, 77, 77);
  CmovTester::test(&MacroAssembler::cmov_gtu, 42, 42, 66, 77, 66);
  CmovTester::test(&MacroAssembler::cmov_gtu, 42, -1, 66, 77, 66);
}

TEST_VM(RiscV, cmov) {
  run_cmov_tests();
  if (UseZicond) {
    UseZicond = false;
    run_cmov_tests();
    UseZicond = true;
  }
}

#endif  // RISCV
