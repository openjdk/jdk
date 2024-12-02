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
    _masm.flush(); // icache invalidate
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

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
class CmpxchgTester {
 public:
  typedef TESTSIZE (*cmpxchg_func)(intptr_t addr, TESTSIZE expected, TESTSIZE new_value, TESTSIZE result);

  static TESTSIZE base_cmpxchg(int variant, intptr_t addr, TESTSIZE expected, TESTSIZE new_value, TESTSIZE result, bool boolean_result = false) {
    BufferBlob* bb = BufferBlob::create("riscvTest", 128);
    CodeBuffer code(bb);
    MacroAssembler _masm(&code);
    address entry = _masm.pc();
    {
      switch(variant) {
        default:
          _masm.cmpxchg(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/c_rarg2,
                        ASMSIZE, Assembler::relaxed, Assembler::relaxed,
                        /*result*/ c_rarg3, boolean_result);
          _masm.mv(c_rarg0, c_rarg3);
          break;
        case 1:
          // expected == result
          _masm.cmpxchg(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/c_rarg2,
                        ASMSIZE, Assembler::relaxed, Assembler::relaxed,
                        /*result*/ c_rarg1, boolean_result);
          _masm.mv(c_rarg0, c_rarg1);
          break;
        case 2:
          // new_value == result
          _masm.cmpxchg(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/c_rarg2,
                        ASMSIZE, Assembler::relaxed, Assembler::relaxed,
                        /*result*/ c_rarg2, boolean_result);
          _masm.mv(c_rarg0, c_rarg2);
          break;
        case 3:
          // expected == new_value
          _masm.cmpxchg(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/ c_rarg1,
                        ASMSIZE, Assembler::relaxed, Assembler::relaxed,
                        /*result*/ c_rarg2, boolean_result);
          _masm.mv(c_rarg0, c_rarg2);
          break;

      }
      _masm.ret();
    }
    _masm.flush(); // icache invalidate
    TESTSIZE ret = ((cmpxchg_func)entry)(addr, expected, new_value, result);
    BufferBlob::free(bb);
    return ret;
  }
};

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
void plain_cmpxchg_test(int variant, TESTSIZE dv, TESTSIZE ex, TESTSIZE nv, TESTSIZE eret, TESTSIZE edata, bool bv) {
  TESTSIZE data = dv;
  TESTSIZE ret = CmpxchgTester<TESTSIZE, ASMSIZE>::base_cmpxchg(variant, (intptr_t)&data, ex, nv, /* dummy */ 67, bv);
  ASSERT_EQ(ret,  eret);
  ASSERT_EQ(data, edata);
}

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
void run_plain_cmpxchg_tests() {
  // Normal
  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   0 /* variant */ , 1337 /* start value*/,
                                        1337 /* expected */,   42 /* new value */,
                                        1337 /* return */    , 42 /* end value*/, false /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   0 /* variant */ , 1337 /* start value*/,
                                        1336 /* expected */,   42 /* new value */,
                                        1337 /* return */  , 1337 /* end value*/, false /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   0 /* variant */ , 1337 /* start value*/,
                                        1337 /* expected */,   42 /* new value */,
                                           1 /* return */    , 42 /* end value*/, true /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   0 /* variant */ , 1337 /* start value*/,
                                        1336 /* expected */,   42 /* new value */,
                                           0 /* return */  , 1337 /* end value*/, true /* boolean ret*/);

  // result == expected register
  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   1 /* variant */ , 1337 /* start value*/,
                                        1337 /* expected */,   42 /* new value */,
                                        1337 /* return */    , 42 /* end value*/, false /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   1 /* variant */ , 1337 /* start value*/,
                                        1336 /* expected */,   42 /* new value */,
                                        1337 /* return */  , 1337 /* end value*/, false /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   1 /* variant */ , 1337 /* start value*/,
                                        1337 /* expected */,   42 /* new value */,
                                           1 /* return */    , 42 /* end value*/, true /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   1 /* variant */ , 1337 /* start value*/,
                                        1336 /* expected */,   42 /* new value */,
                                           0 /* return */  , 1337 /* end value*/, true /* boolean ret*/);

  // new_value == result register
  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   2 /* variant */ , 1337 /* start value*/,
                                        1337 /* expected */,   42 /* new value */,
                                        1337 /* return */    , 42 /* end value*/, false /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   2 /* variant */ , 1337 /* start value*/,
                                        1336 /* expected */,   42 /* new value */,
                                        1337 /* return */  , 1337 /* end value*/, false /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   2 /* variant */ , 1337 /* start value*/,
                                        1337 /* expected */,   42 /* new value */,
                                           1 /* return */    , 42 /* end value*/, true /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   2 /* variant */ , 1337 /* start value*/,
                                        1336 /* expected */,   42 /* new value */,
                                           0 /* return */  , 1337 /* end value*/, true /* boolean ret*/);

  // expected == new_value register
  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   3 /* variant */ , 1337 /* start value*/,
                                        1337 /* expected */,   42 /* new value */,
                                        1337 /* return */  , 1337 /* end value*/, false /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   3 /* variant */ , 1337 /* start value*/,
                                        1336 /* expected */,   42 /* new value */,
                                        1337 /* return */  , 1337 /* end value*/, false /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   3 /* variant */ , 1337 /* start value*/,
                                        1337 /* expected */,   42 /* new value */,
                                           1 /* return */  , 1337 /* end value*/, true /* boolean ret*/);

  plain_cmpxchg_test<TESTSIZE, ASMSIZE>(   3 /* variant */ , 1337 /* start value*/,
                                        1336 /* expected */,   42 /* new value */,
                                           0 /* return */  , 1337 /* end value*/, true /* boolean ret*/);
}

TEST_VM(RiscV, cmpxchg_int64_plain_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_plain_cmpxchg_tests<int64_t, Assembler::int64>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_int64_plain_maybe_zacas) {
  if (UseZacas) {
    run_plain_cmpxchg_tests<int64_t, Assembler::int64>();
  }
}

TEST_VM(RiscV, cmpxchg_int32_plain_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_plain_cmpxchg_tests<int32_t, Assembler::int32>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_int32_plain_maybe_zacas) {
  if (UseZacas) {
    run_plain_cmpxchg_tests<int32_t, Assembler::int32>();
  }
}

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
class NarrowCmpxchgTester {
 public:
  typedef TESTSIZE (*cmpxchg_func)(intptr_t addr, TESTSIZE expected, TESTSIZE new_value, TESTSIZE result,
                                   int64_t scratch0, int64_t scratch1, int64_t scratch2);

  static TESTSIZE narrow_cmpxchg(intptr_t addr, TESTSIZE expected, TESTSIZE new_value, TESTSIZE result, bool boolean_result = false) {
    BufferBlob* bb = BufferBlob::create("riscvTest", 128);
    CodeBuffer code(bb);
    MacroAssembler _masm(&code);
    address entry = _masm.pc();
    {
       _masm.cmpxchg_narrow_value(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/c_rarg2,
                        ASMSIZE, Assembler::relaxed, Assembler::relaxed,
                        /*result*/ c_rarg3, boolean_result, c_rarg4, c_rarg5, c_rarg6); /* Uses also t0-t1, caller saved */
      _masm.mv(c_rarg0, c_rarg3);
      _masm.ret();
    }
    _masm.flush(); // icache invalidate
    TESTSIZE ret = ((cmpxchg_func)entry)(addr, expected, new_value, result, -1, -1, -1);
    BufferBlob::free(bb);
    return ret;
  }
};

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
void run_narrow_cmpxchg_tests() {
  // Assume natural aligned
  TESTSIZE data[8];
  TESTSIZE ret;
  for (int i = 0; i < 7; i++) {
    memset(data, -1, sizeof(data));

    data[i] = 121;
    ret = NarrowCmpxchgTester<TESTSIZE, ASMSIZE>::narrow_cmpxchg((intptr_t)&data[i], 121, 42, /* result */ 67, false);
    ASSERT_EQ(ret, 121);
    ASSERT_EQ(data[i], 42);

    data[i] = 121;
    ret = NarrowCmpxchgTester<TESTSIZE, ASMSIZE>::narrow_cmpxchg((intptr_t)&data[i], 120, 42, /* result */ 67, false);
    ASSERT_EQ(ret, 121);
    ASSERT_EQ(data[i], 121);

    data[i] = 121;
    ret = NarrowCmpxchgTester<TESTSIZE, ASMSIZE>::narrow_cmpxchg((intptr_t)&data[i], 121, 42, /* result */ 67, true);
    ASSERT_EQ(ret, 1);
    ASSERT_EQ(data[i], 42);

    data[i] = 121;
    ret = NarrowCmpxchgTester<TESTSIZE, ASMSIZE>::narrow_cmpxchg((intptr_t)&data[i], 120, 42, /* result */ 67, true);
    ASSERT_EQ(ret, 0);
    ASSERT_EQ(data[i], 121);
  }
}

TEST_VM(RiscV, cmpxchg_int16_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_narrow_cmpxchg_tests<int16_t, Assembler::int16>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_int8_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_narrow_cmpxchg_tests<int8_t, Assembler::int8>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_int16_maybe_zacas) {
  if (UseZacas) {
    run_narrow_cmpxchg_tests<int16_t, Assembler::int16>();
  }
}

TEST_VM(RiscV, cmpxchg_int8_maybe_zacas) {
  if (UseZacas) {
    run_narrow_cmpxchg_tests<int8_t, Assembler::int8>();
  }
}

#endif  // RISCV
