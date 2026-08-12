/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#if (defined(RISCV) || defined(RISCV64)) && !defined(ZERO)

#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "cppstdlib/limits.hpp"
#include "memory/resourceArea.hpp"
#include "metaprogramming/enableIf.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/vm_version.hpp"
#include "threadHelper.inline.hpp"
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

static void run_cmov_tests() {
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

template <Assembler::operand_size ASMSIZE>
bool using_narrow() {
  if (ASMSIZE == Assembler::int8 || ASMSIZE == Assembler::int16) {
    return !(UseZacas && UseZabha);
  }
  return false;
}

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
class CmpxchgTester {
  // The functions expect arguments to be type represented, not C-ABI argument representation.
  // Hence an unsigned should be zero-extended, and the same goes for the return value.
  typedef int64_t (*cmpxchg_func)(intptr_t addr, int64_t expected, int64_t new_value, int64_t result);

  typedef int64_t (*cmpxchg_narrow_func)(intptr_t addr, int64_t expected, int64_t new_value, int64_t result,
                                          int64_t scratch0, int64_t scratch1, int64_t scratch2);

  BufferBlob*  _bb;
  cmpxchg_func _func;
  cmpxchg_narrow_func _narrow;

 public:
  CmpxchgTester(int variant, bool boolean_result) {
    _bb = BufferBlob::create("riscvTest", 128);
    CodeBuffer code(_bb);
    MacroAssembler _masm(&code);
    address entry = _masm.pc();
    if (using_narrow<ASMSIZE>()) {
        address entry = _masm.pc();
       _masm.cmpxchg_narrow_value(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/c_rarg2,
                        ASMSIZE, Assembler::relaxed, Assembler::relaxed,
                        /*result*/ c_rarg3, boolean_result, c_rarg4, c_rarg5, c_rarg6); /* Uses also t0-t1, caller saved */
      _masm.mv(c_rarg0, c_rarg3);
      _masm.ret();
      _narrow = ((cmpxchg_narrow_func)entry);
    } else {
      switch(variant) {
        default:
          _masm.cmpxchg(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/c_rarg2,
                        ASMSIZE, Assembler::aq, Assembler::rl,
                        /*result*/ c_rarg3, boolean_result);
          _masm.mv(c_rarg0, c_rarg3);
          break;
        case 1:
          // expected == result
          _masm.cmpxchg(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/c_rarg2,
                        ASMSIZE, Assembler::aq, Assembler::rl,
                        /*result*/ c_rarg1, boolean_result);
          _masm.mv(c_rarg0, c_rarg1);
          break;
        case 2:
          // new_value == result
          _masm.cmpxchg(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/c_rarg2,
                        ASMSIZE, Assembler::aq, Assembler::rl,
                        /*result*/ c_rarg2, boolean_result);
          _masm.mv(c_rarg0, c_rarg2);
          break;
        case 3:
          // expected == new_value
          _masm.cmpxchg(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/ c_rarg1,
                        ASMSIZE, Assembler::aq, Assembler::rl,
                        /*result*/ c_rarg2, boolean_result);
          _masm.mv(c_rarg0, c_rarg2);
          break;

      }
      _masm.ret();
      _func = ((cmpxchg_func)entry);
    }
    _masm.flush(); // icache invalidate
  }

  ~CmpxchgTester() {
    BufferBlob::free(_bb);
  }

  TESTSIZE cmpxchg(intptr_t addr, TESTSIZE expected, TESTSIZE new_value) {
    if (using_narrow<ASMSIZE>()) {
      return _narrow(addr, expected, new_value, /* dummy result */ 67, -1, -1, -1);
    } else {
      return _func(addr, expected, new_value, /* dummy result */ 67);
    }
  }
};

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
static void plain_cmpxchg_test(int variant, TESTSIZE dv, TESTSIZE ex, TESTSIZE nv, TESTSIZE eret, TESTSIZE edata, bool bv) {
  CmpxchgTester<TESTSIZE, ASMSIZE> cmpxchg(variant, bv);
  TESTSIZE data = dv;
  TESTSIZE ret = cmpxchg.cmpxchg((intptr_t)&data, ex, nv);
  ASSERT_EQ(ret,  eret);
  ASSERT_EQ(data, edata);
}

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
static void run_plain_cmpxchg_tests() {
  TESTSIZE max = std::numeric_limits<TESTSIZE>::max();
  TESTSIZE min = std::numeric_limits<TESTSIZE>::min();
  TESTSIZE val[] = {37, min, max};
  for (int i = 0; i < 3; i++) {
    // Normal
    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     0 /* variant */ , val[i] /* start value */,
                                          val[i] /* expected */,     42 /* new value */,
                                          val[i] /* return */  ,     42 /* end value*/, false /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     0 /* variant */ , val[i] /* start value */,
                                              36 /* expected */,     42 /* new value */,
                                          val[i] /* return */  , val[i] /* end value */, false /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     0 /* variant */ , val[i] /* start value */,
                                          val[i] /* expected */,     42 /* new value */,
                                               1 /* return */  ,     42 /* end value*/, true /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     0 /* variant */ , val[i] /* start value */,
                                              36 /* expected */,     42 /* new value */,
                                               0 /* return */  , val[i] /* end value */, true /* boolean ret*/);

    // result == expected register
    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     1 /* variant */ ,  val[i] /* start value */,
                                          val[i] /* expected */,      42 /* new value */,
                                          val[i] /* return */  ,      42 /* end value*/, false /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     1 /* variant */ ,  val[i] /* start value */,
                                              36 /* expected */,      42 /* new value */,
                                          val[i] /* return */  ,  val[i] /* end value */, false /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     1 /* variant */ , val[i] /* start value */,
                                          val[i] /* expected */,     42 /* new value */,
                                               1 /* return */  ,     42 /* end value*/, true /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     1 /* variant */ , val[i] /* start value */,
                                              36 /* expected */,     42 /* new value */,
                                               0 /* return */  , val[i] /* end value */, true /* boolean ret*/);

    // new_value == result register
    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     2 /* variant */ , val[i] /* start value */,
                                          val[i] /* expected */,     42 /* new value */,
                                          val[i] /* return */  ,     42 /* end value*/, false /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     2 /* variant */ , val[i] /* start value */,
                                              36 /* expected */,     42 /* new value */,
                                          val[i] /* return */  , val[i] /* end value */, false /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     2 /* variant */ , val[i] /* start value */,
                                          val[i] /* expected */,     42 /* new value */,
                                               1 /* return */  ,     42 /* end value*/, true /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(    2 /* variant */ , val[i] /* start value */,
                                             36 /* expected */,     42 /* new value */,
                                              0 /* return */  , val[i] /* end value */, true /* boolean ret*/);

    // expected == new_value register
    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     3 /* variant */ , val[i] /* start value */,
                                          val[i] /* expected */,    42 /* new value */,
                                          val[i] /* return */  , val[i] /* end value */, false /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     3 /* variant */ , val[i] /* start value */,
                                              36 /* expected */,     42 /* new value */,
                                          val[i] /* return */  , val[i] /* end value */, false /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(     3 /* variant */ , val[i] /* start value */,
                                          val[i] /* expected */,     42 /* new value */,
                                               1 /* return */  , val[i] /* end value */, true /* boolean ret*/);

    plain_cmpxchg_test<TESTSIZE, ASMSIZE>(    3 /* variant */ , val[i] /* start value */,
                                             36 /* expected */,     42 /* new value */,
                                              0 /* return */  , val[i] /* end value */, true /* boolean ret*/);
  }
}

TEST_VM(RiscV, cmpxchg_int64_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_plain_cmpxchg_tests<int64_t, Assembler::int64>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_int64_maybe_zacas) {
  if (UseZacas) {
    run_plain_cmpxchg_tests<int64_t, Assembler::int64>();
  }
}

TEST_VM(RiscV, cmpxchg_int32_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_plain_cmpxchg_tests<int32_t, Assembler::int32>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_int32_maybe_zacas) {
  if (UseZacas) {
    run_plain_cmpxchg_tests<int32_t, Assembler::int32>();
  }
}

TEST_VM(RiscV, cmpxchg_uint32_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_plain_cmpxchg_tests<uint32_t, Assembler::uint32>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_uint32_maybe_zacas) {
  if (UseZacas) {
    run_plain_cmpxchg_tests<uint32_t, Assembler::uint32>();
  }
}

TEST_VM(RiscV, cmpxchg_int16_maybe_zacas_zabha) {
  if (UseZacas && UseZabha) {
    run_plain_cmpxchg_tests<int16_t, Assembler::int16>();
  }
}

TEST_VM(RiscV, cmpxchg_int8_maybe_zacas_zabha) {
  if (UseZacas && UseZabha) {
    run_plain_cmpxchg_tests<int8_t, Assembler::int8>();
  }
}

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
static void run_narrow_cmpxchg_tests() {
  CmpxchgTester<TESTSIZE, ASMSIZE> cmpxchg(0, false);
  CmpxchgTester<TESTSIZE, ASMSIZE> cmpxchg_bool(0, true);
  // Assume natural aligned
  TESTSIZE data[8];
  TESTSIZE ret;
  TESTSIZE max = std::numeric_limits<TESTSIZE>::max();
  TESTSIZE min = std::numeric_limits<TESTSIZE>::min();
  TESTSIZE val[] = {121, min, max};
  for (int i = 0; i < 3; i++) {
    for (int j = 0; j < 7; j++) {
      memset(data, -1, sizeof(data));
      data[i] = val[i];
      ret = cmpxchg.cmpxchg((intptr_t)&data[i], val[i], 42);
      ASSERT_EQ(ret, val[i]);
      ASSERT_EQ(data[i], 42);

      data[i] = val[i];
      ret = cmpxchg.cmpxchg((intptr_t)&data[i], 120, 42);
      ASSERT_EQ(ret, val[i]);
      ASSERT_EQ(data[i], val[i]);

      data[i] = val[i];
      ret = cmpxchg_bool.cmpxchg((intptr_t)&data[i], val[i], 42);
      ASSERT_EQ(ret, 1);
      ASSERT_EQ(data[i], 42);

      data[i] = val[i];
      ret = cmpxchg_bool.cmpxchg((intptr_t)&data[i], 120, 42);
      ASSERT_EQ(ret, 0);
      ASSERT_EQ(data[i], val[i]);
    }
  }
}

TEST_VM(RiscV, cmpxchg_narrow_int16_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_narrow_cmpxchg_tests<int16_t, Assembler::int16>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_narrow_int16_maybe_zacas) {
  if (UseZacas) {
    bool zabha = UseZabha;
    UseZabha = false;
    run_narrow_cmpxchg_tests<int16_t, Assembler::int16>();
    UseZabha = zabha;
  }
}

TEST_VM(RiscV, cmpxchg_narrow_int8_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_narrow_cmpxchg_tests<int8_t, Assembler::int8>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_narrow_int8_maybe_zacas) {
  if (UseZacas) {
    bool zabha = UseZabha;
    UseZabha = false;
    run_narrow_cmpxchg_tests<int8_t, Assembler::int8>();
    UseZabha = zabha;
  }
}

template <typename TESTSIZE>
TESTSIZE next_count(TESTSIZE now, TESTSIZE add) {
  if ((std::numeric_limits<TESTSIZE>::max() - add) >= now) {
    return now + add;
  }
  TESTSIZE diff = std::numeric_limits<TESTSIZE>::max() - now;
  add -= diff + 1; // add one to the diff for the wrap around.
  return std::numeric_limits<TESTSIZE>::min() + add;
}

constexpr int64_t PAR_IT_END       = 10000;
constexpr int64_t NUMBER_THREADS   = 4;
constexpr int64_t TOTAL_ITERATIONS = NUMBER_THREADS * PAR_IT_END;

template <typename TESTSIZE, ENABLE_IF(std::numeric_limits<TESTSIZE>::max() <= (std::numeric_limits<TESTSIZE>::min() + TOTAL_ITERATIONS))>
constexpr TESTSIZE result_count() {
  int64_t range = std::numeric_limits<TESTSIZE>::max() - std::numeric_limits<TESTSIZE>::min() + 1;
  int64_t rest = TOTAL_ITERATIONS % range;
  return std::numeric_limits<TESTSIZE>::min() + rest;
}

template <typename TESTSIZE, ENABLE_IF(std::numeric_limits<TESTSIZE>::max() > (std::numeric_limits<TESTSIZE>::min() + TOTAL_ITERATIONS))>
constexpr TESTSIZE result_count() {
  return std::numeric_limits<TESTSIZE>::min() + TOTAL_ITERATIONS;
}

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
static void run_concurrent_cmpxchg_tests() {
  volatile TESTSIZE data = std::numeric_limits<TESTSIZE>::min();
  int num_threads = NUMBER_THREADS;
  CmpxchgTester<TESTSIZE, ASMSIZE> cmpxchg(0, false); // variant 0, not bool ret
  auto incThread = [&](Thread* _current, int _id) {   // _id starts from 0..(CTHREAD-1)
    TESTSIZE my_oldvalue = std::numeric_limits<TESTSIZE>::min() + _id;
    for (int64_t i = 0; i < PAR_IT_END ; i++) {
      TESTSIZE newvalue = next_count<TESTSIZE>(my_oldvalue,  1);
      TESTSIZE ret;
      do {
        ret = cmpxchg.cmpxchg((intptr_t)&data, my_oldvalue, newvalue);
      } while (ret != my_oldvalue);
      my_oldvalue = next_count<TESTSIZE>(my_oldvalue, num_threads);
    }
  };
  TestThreadGroup<decltype(incThread)> ttg(incThread, num_threads);
  ttg.doit();
  ttg.join();
  ASSERT_EQ(data, result_count<TESTSIZE>());
}

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
static void run_concurrent_alt_cmpxchg_tests() {
  volatile TESTSIZE data = std::numeric_limits<TESTSIZE>::min();
  int num_threads = NUMBER_THREADS;
  CmpxchgTester<TESTSIZE, ASMSIZE> cmpxchg(0, false); // variant 0, not bool ret
  auto incThread = [&](Thread* _current, int _id) {   // _id starts from 0..(CTHREAD-1)
    for (int i = 0; i < PAR_IT_END; i++) {
      TESTSIZE oldvalue;
      TESTSIZE ret = 0;
      do {
        oldvalue = ret;
        TESTSIZE newvalue = next_count<TESTSIZE>(oldvalue, 1);
        ret = cmpxchg.cmpxchg((intptr_t)&data, oldvalue, newvalue);
      } while (ret != oldvalue);
    }
  };
  TestThreadGroup<decltype(incThread)> ttg(incThread, num_threads);
  ttg.doit();
  ttg.join();
  ASSERT_EQ(data, result_count<TESTSIZE>());
}

TEST_VM(RiscV, cmpxchg_int64_concurrent_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_concurrent_cmpxchg_tests<int64_t, Assembler::int64>();
  run_concurrent_alt_cmpxchg_tests<int64_t, Assembler::int64>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_int64_concurrent_maybe_zacas) {
  if (UseZacas) {
    run_concurrent_cmpxchg_tests<int64_t, Assembler::int64>();
    run_concurrent_alt_cmpxchg_tests<int64_t, Assembler::int64>();
  }
}

TEST_VM(RiscV, cmpxchg_int32_concurrent_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_concurrent_cmpxchg_tests<int32_t, Assembler::int32>();
  run_concurrent_alt_cmpxchg_tests<int32_t, Assembler::int32>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_int32_concurrent_maybe_zacas) {
  if (UseZacas) {
    run_concurrent_cmpxchg_tests<int32_t, Assembler::int32>();
    run_concurrent_alt_cmpxchg_tests<int32_t, Assembler::int32>();
  }
}

TEST_VM(RiscV, cmpxchg_uint32_concurrent_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_concurrent_cmpxchg_tests<uint32_t, Assembler::uint32>();
  run_concurrent_alt_cmpxchg_tests<uint32_t, Assembler::uint32>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_uint32_concurrent_maybe_zacas) {
  if (UseZacas) {
    run_concurrent_cmpxchg_tests<uint32_t, Assembler::uint32>();
    run_concurrent_alt_cmpxchg_tests<uint32_t, Assembler::uint32>();
  }
}

TEST_VM(RiscV, cmpxchg_narrow_int16_concurrent_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_concurrent_cmpxchg_tests<int16_t, Assembler::int16>();
  run_concurrent_alt_cmpxchg_tests<int16_t, Assembler::int16>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_narrow_int16_concurrent_maybe_zacas) {
  if (UseZacas) {
    bool zabha = UseZabha;
    UseZabha = false;
    run_concurrent_cmpxchg_tests<int16_t, Assembler::int16>();
    run_concurrent_alt_cmpxchg_tests<int16_t, Assembler::int16>();
    UseZabha = zabha;
  }
}

TEST_VM(RiscV, cmpxchg_narrow_int8_concurrent_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_concurrent_cmpxchg_tests<int8_t, Assembler::int8>();
  run_concurrent_alt_cmpxchg_tests<int8_t, Assembler::int8>();
  UseZacas = zacas;
}

TEST_VM(RiscV, cmpxchg_narrow_int8_concurrent_maybe_zacas) {
  if (UseZacas) {
    bool zabha = UseZabha;
    UseZabha = false;
    run_concurrent_cmpxchg_tests<int8_t, Assembler::int8>();
    run_concurrent_alt_cmpxchg_tests<int8_t, Assembler::int8>();
    UseZabha = zabha;
  }
}

TEST_VM(RiscV, cmpxchg_int16_concurrent_maybe_zacas_zabha) {
  if (UseZacas && UseZabha) {
    run_concurrent_cmpxchg_tests<int16_t, Assembler::int16>();
    run_concurrent_alt_cmpxchg_tests<int16_t, Assembler::int16>();
  }
}

TEST_VM(RiscV, cmpxchg_int8_concurrent_maybe_zacas_zabha) {
  if (UseZacas && UseZabha) {
    run_concurrent_cmpxchg_tests<int8_t, Assembler::int8>();
    run_concurrent_alt_cmpxchg_tests<int8_t, Assembler::int8>();
  }
}

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
class WeakCmpxchgTester {
  // The functions expect arguments to be type represented, not C-ABI argument representation.
  // Hence an unsigned should be zero-extended, and the same goes for the return value.
  typedef int64_t (*weak_cmpxchg_narrow_func)(intptr_t addr, int64_t expected, int64_t new_value, int64_t result,
                                   int64_t scratch0, int64_t scratch1, int64_t scratch2);

  typedef int64_t (*weak_cmpxchg_func)(intptr_t addr, int64_t expected, int64_t new_value, int64_t result);

  BufferBlob*  _bb;
  weak_cmpxchg_narrow_func _narrow_weak;
  weak_cmpxchg_func _weak;

 public:
  WeakCmpxchgTester() : _bb(nullptr), _narrow_weak(nullptr), _weak(nullptr) {
    _bb = BufferBlob::create("riscvTest", 128);
    CodeBuffer code(_bb);
    MacroAssembler _masm(&code);
    if (using_narrow<ASMSIZE>()) {
        address entry = _masm.pc();
       _masm.weak_cmpxchg_narrow_value(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/ c_rarg2,
                                      ASMSIZE, Assembler::relaxed, Assembler::relaxed,
                                      /*result*/ c_rarg3, c_rarg4, c_rarg5, c_rarg6); /* Uses also t0-t1, caller saved */
      _masm.mv(c_rarg0, c_rarg3);
      _masm.ret();
      _narrow_weak = ((weak_cmpxchg_narrow_func)entry);
    } else {
        address entry = _masm.pc();
       _masm.weak_cmpxchg(/*addr*/ c_rarg0, /*expected*/ c_rarg1, /*new_value*/ c_rarg2,
                          ASMSIZE, Assembler::relaxed, Assembler::relaxed, /*result*/ c_rarg3);
      _masm.mv(c_rarg0, c_rarg3);
      _masm.ret();
      _weak = ((weak_cmpxchg_func)entry);
    }
    _masm.flush(); // icache invalidate
  }

  TESTSIZE weak_cmpxchg(intptr_t addr, TESTSIZE expected, TESTSIZE new_value) {
    if (using_narrow<ASMSIZE>()) {
      return _narrow_weak(addr, expected, new_value, /* dummy result */ 67, -1, -1, -1);
    } else {
      return _weak(addr, expected, new_value, /* dummy result */ 67);
    }
  }

  ~WeakCmpxchgTester() {
    BufferBlob::free(_bb);
  }
};

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
void run_weak_cmpxchg_tests() {
  TESTSIZE max = std::numeric_limits<TESTSIZE>::max();
  TESTSIZE min = std::numeric_limits<TESTSIZE>::min();
  TESTSIZE val[] = {121, min, max};
  for (int i = 0; i < 3; i++) {
    WeakCmpxchgTester<TESTSIZE, ASMSIZE> cmpxchg;
    TESTSIZE data = val[i];
    TESTSIZE ret = cmpxchg.weak_cmpxchg((intptr_t)&data, val[i], 42);
    ASSERT_EQ(ret, (TESTSIZE)1);
    ASSERT_EQ(data, (TESTSIZE)42);

    data = val[i];
    ret = cmpxchg.weak_cmpxchg((intptr_t)&data, 120, 42);
    ASSERT_EQ(ret, (TESTSIZE)0);
    ASSERT_EQ(data, (TESTSIZE)val[i]);
  }
}

TEST_VM(RiscV, weak_cmpxchg_int64_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_weak_cmpxchg_tests<int64_t, Assembler::int64>();
  UseZacas = zacas;
}

TEST_VM(RiscV, weak_cmpxchg_int64_maybe_zacas) {
  if (UseZacas) {
    run_weak_cmpxchg_tests<int64_t, Assembler::int64>();
  }
}

TEST_VM(RiscV, weak_cmpxchg_int32_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_weak_cmpxchg_tests<int32_t, Assembler::int32>();
  UseZacas = zacas;
}

TEST_VM(RiscV, weak_cmpxchg_int32_maybe_zacas) {
  if (UseZacas) {
    run_weak_cmpxchg_tests<int32_t, Assembler::int32>();
  }
}

TEST_VM(RiscV, weak_cmpxchg_uint32_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_weak_cmpxchg_tests<uint32_t, Assembler::uint32>();
  UseZacas = zacas;
}

TEST_VM(RiscV, weak_cmpxchg_uint32_maybe_zacas) {
  if (UseZacas) {
    run_weak_cmpxchg_tests<uint32_t, Assembler::uint32>();
  }
}

TEST_VM(RiscV, weak_cmpxchg_narrow_int16_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_weak_cmpxchg_tests<int16_t, Assembler::int16>();
  UseZacas = zacas;
}

TEST_VM(RiscV, weak_cmpxchg_narrow_int8_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_weak_cmpxchg_tests<int8_t, Assembler::int8>();
  UseZacas = zacas;
}

TEST_VM(RiscV, weak_cmpxchg_narrow_int16_maybe_zacas) {
  if (UseZacas) {
    bool zabha = UseZabha;
    UseZabha = false;
    run_weak_cmpxchg_tests<int16_t, Assembler::int16>();
    UseZabha = zabha;
  }
}

TEST_VM(RiscV, weak_cmpxchg_narrow_int8_maybe_zacas) {
  if (UseZacas) {
    bool zabha = UseZabha;
    UseZabha = false;
    run_weak_cmpxchg_tests<int8_t, Assembler::int8>();
    UseZabha = zabha;
  }
}

TEST_VM(RiscV, weak_cmpxchg_int16_maybe_zacas_zabha) {
  if (UseZacas && UseZabha) {
    run_weak_cmpxchg_tests<int16_t, Assembler::int16>();
  }
}

TEST_VM(RiscV, weak_cmpxchg_int8_maybe_zacas_zabha) {
  if (UseZacas && UseZabha) {
    run_weak_cmpxchg_tests<int8_t, Assembler::int8>();
  }
}

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
static void run_concurrent_weak_cmpxchg_tests() {
  volatile TESTSIZE data = std::numeric_limits<TESTSIZE>::min();
  int num_threads = NUMBER_THREADS;
  WeakCmpxchgTester<TESTSIZE, ASMSIZE> cmpxchg; // not bool ret
  auto incThread = [&](Thread* _current, int _id) { // _id starts from 0..(CTHREAD-1)
    TESTSIZE my_oldvalue = std::numeric_limits<TESTSIZE>::min() + _id;
    for (int64_t i = 0; i < PAR_IT_END; i++) {
      TESTSIZE newvalue = next_count<TESTSIZE>(my_oldvalue, 1);
      TESTSIZE ret;
      do {
        ret = cmpxchg.weak_cmpxchg((intptr_t)&data, my_oldvalue, newvalue);
      } while (ret != 1);
      my_oldvalue = next_count<TESTSIZE>(my_oldvalue, num_threads);
    }
  };
  TestThreadGroup<decltype(incThread)> ttg(incThread, num_threads);
  ttg.doit();
  ttg.join();
  ASSERT_EQ(data, result_count<TESTSIZE>());
}

template <typename TESTSIZE, Assembler::operand_size ASMSIZE>
static void run_concurrent_alt_weak_cmpxchg_tests() {
  volatile TESTSIZE data = std::numeric_limits<TESTSIZE>::min();
  int num_threads = NUMBER_THREADS;
  WeakCmpxchgTester<TESTSIZE, ASMSIZE> cmpxchg; // not bool ret
  auto incThread = [&](Thread* _current, int _id) { // _id starts from 0..(CTHREAD-1)
    for (int i = 0; i < PAR_IT_END; i++) {
      TESTSIZE oldvalue;
      TESTSIZE ret = 0;
      do {
        oldvalue = data;
        TESTSIZE newvalue = next_count<TESTSIZE>(oldvalue, 1);
        ret = cmpxchg.weak_cmpxchg((intptr_t)&data, oldvalue, newvalue);
      } while (ret != 1);
    }
  };
  TestThreadGroup<decltype(incThread)> ttg(incThread, num_threads);
  ttg.doit();
  ttg.join();
  ASSERT_EQ(data, result_count<TESTSIZE>());
}

TEST_VM(RiscV, weak_cmpxchg_int64_concurrent_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_concurrent_weak_cmpxchg_tests<int64_t, Assembler::int64>();
  run_concurrent_alt_weak_cmpxchg_tests<int64_t, Assembler::int64>();
  UseZacas = zacas;
}

TEST_VM(RiscV, weak_cmpxchg_int64_concurrent_maybe_zacas) {
  if (UseZacas) {
    run_concurrent_weak_cmpxchg_tests<int64_t, Assembler::int64>();
    run_concurrent_alt_weak_cmpxchg_tests<int64_t, Assembler::int64>();
  }
}

TEST_VM(RiscV, weak_cmpxchg_int32_concurrent_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_concurrent_weak_cmpxchg_tests<int32_t, Assembler::int32>();
  run_concurrent_alt_weak_cmpxchg_tests<int32_t, Assembler::int32>();
  UseZacas = zacas;
}

TEST_VM(RiscV, weak_cmpxchg_int32_concurrent_maybe_zacas) {
  if (UseZacas) {
    run_concurrent_weak_cmpxchg_tests<int32_t, Assembler::int32>();
    run_concurrent_alt_weak_cmpxchg_tests<int32_t, Assembler::int32>();
  }
}

TEST_VM(RiscV, weak_cmpxchg_narrow_int16_concurrent_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_concurrent_weak_cmpxchg_tests<int16_t, Assembler::int16>();
  run_concurrent_alt_weak_cmpxchg_tests<int16_t, Assembler::int16>();
  UseZacas = zacas;
}

TEST_VM(RiscV, weak_cmpxchg_narrow_int16_concurrent_maybe_zacas) {
  if (UseZacas) {
    bool zabha = UseZabha;
    UseZabha = false;
    run_concurrent_weak_cmpxchg_tests<int16_t, Assembler::int16>();
    run_concurrent_alt_weak_cmpxchg_tests<int16_t, Assembler::int16>();
    UseZabha = zabha;
  }
}

TEST_VM(RiscV, weak_cmpxchg_narrow_int8_concurrent_lr_sc) {
  bool zacas = UseZacas;
  UseZacas = false;
  run_concurrent_weak_cmpxchg_tests<int8_t, Assembler::int8>();
  run_concurrent_alt_weak_cmpxchg_tests<int8_t, Assembler::int8>();
  UseZacas = zacas;
}

TEST_VM(RiscV, weak_cmpxchg_narrow_int8_concurrent_maybe_zacas) {
  if (UseZacas) {
    bool zabha = UseZabha;
    UseZabha = false;
    run_concurrent_weak_cmpxchg_tests<int8_t, Assembler::int8>();
    run_concurrent_alt_weak_cmpxchg_tests<int8_t, Assembler::int8>();
    UseZabha = zabha;
  }
}

TEST_VM(RiscV, weak_cmpxchg_int16_concurrent_maybe_zacas_zabha) {
  if (UseZacas && UseZabha) {
    run_concurrent_weak_cmpxchg_tests<int16_t, Assembler::int16>();
    run_concurrent_alt_weak_cmpxchg_tests<int16_t, Assembler::int16>();
  }
}

TEST_VM(RiscV, weak_cmpxchg_int8_concurrent_maybe_zacas_zabha) {
  if (UseZacas && UseZabha) {
    run_concurrent_weak_cmpxchg_tests<int8_t, Assembler::int8>();
    run_concurrent_alt_weak_cmpxchg_tests<int8_t, Assembler::int8>();
  }
}

// ------------------------------ Zibi ---------------------------------------

// Decode the B-immediate (in bytes) out of an already-emitted beqi/bnei word.
static int64_t zibi_decode_offset(uint32_t insn) {
  int64_t offset = 0;
  offset |= (int64_t)((insn >> 31) & 0x1)  << 12; // imm[12]
  offset |= (int64_t)((insn >> 7)  & 0x1)  << 11; // imm[11]
  offset |= (int64_t)((insn >> 25) & 0x3f) << 5;  // imm[10:5]
  offset |= (int64_t)((insn >> 8)  & 0xf)  << 1;  // imm[4:1]
  // Sign-extend the 13-bit signed offset.
  offset = (offset << 51) >> 51;
  return offset;
}

// Verify that beqi/bnei encode exactly as the Zibi spec prescribes:
//  opcode = 0b1100011, funct3 = 010 (beqi) / 011 (bnei), rs1 in [19:15],
//  cimm in [24:20], and the standard B-type immediate scatter.
static void check_zibi_encoding(bool is_beqi, Register rs1, uint32_t cimm, int64_t offset) {
  BufferBlob* bb = BufferBlob::create("zibiEnc", 128);
  CodeBuffer code(bb);
  MacroAssembler _masm(&code);
  address entry = _masm.pc();
  if (is_beqi) {
    _masm.beqi(rs1, cimm, entry + offset);
  } else {
    _masm.bnei(rs1, cimm, entry + offset);
  }
  _masm.flush();

  uint32_t insn = Assembler::ld_instr(entry);
  EXPECT_EQ(insn & 0x7f, (uint32_t)0b1100011)           << "opcode";
  EXPECT_EQ((insn >> 12) & 0x7, is_beqi ? 0b010u : 0b011u) << "funct3";
  EXPECT_EQ((insn >> 15) & 0x1f, (uint32_t)rs1->encoding()) << "rs1";
  EXPECT_EQ((insn >> 20) & 0x1f, cimm)                  << "cimm";
  EXPECT_EQ(zibi_decode_offset(insn), offset)           << "offset";

  BufferBlob::free(bb);
}

TEST_VM(RiscV, zibi_encoding) {
  // cimm covers the full 5-bit range: 0 (encodes value -1) through 31.
  for (uint32_t cimm = 0; cimm <= 31; cimm++) {
    check_zibi_encoding(true,  x5,  cimm, 4);
    check_zibi_encoding(false, x5,  cimm, 4);
  }
  // Exercise different source registers.
  check_zibi_encoding(true,  x0,  1, 8);
  check_zibi_encoding(true,  x31, 7, 16);
  check_zibi_encoding(false, x1,  31, 2);
  // Exercise the full B-immediate bit scatter, forward and backward.
  check_zibi_encoding(true,  x10, 5, 4094);
  check_zibi_encoding(true,  x10, 5, -4096);
  check_zibi_encoding(false, x10, 5, 2048);
  check_zibi_encoding(false, x10, 5, -2048);
  check_zibi_encoding(true,  x10, 5, 42);
}

// The comparison constant maps to the cimm field as: -1 -> 0, 1..31 -> 1..31.
TEST_VM(RiscV, zibi_encode_cimm) {
  EXPECT_TRUE(Assembler::is_zibi_cimm(-1));
  EXPECT_TRUE(Assembler::is_zibi_cimm(1));
  EXPECT_TRUE(Assembler::is_zibi_cimm(31));
  EXPECT_FALSE(Assembler::is_zibi_cimm(0));
  EXPECT_FALSE(Assembler::is_zibi_cimm(32));
  EXPECT_FALSE(Assembler::is_zibi_cimm(-2));

  EXPECT_EQ(Assembler::encode_zibi_cimm(-1), 0u);
  EXPECT_EQ(Assembler::encode_zibi_cimm(1), 1u);
  EXPECT_EQ(Assembler::encode_zibi_cimm(31), 31u);
}

typedef int64_t (*zibi_func)(int64_t arg);

// Emit: <branch> arg, cimm, taken; mv a0, not_taken_val; ret; taken: mv a0, taken_val; ret;
static int64_t run_zibi_branch(bool is_beqi, uint32_t cimm, int64_t arg) {
  const int64_t taken_val = 1;
  const int64_t not_taken_val = 0;
  BufferBlob* bb = BufferBlob::create("zibiRun", 256);
  CodeBuffer code(bb);
  MacroAssembler _masm(&code);
  address entry = _masm.pc();
  {
    Label taken;
    if (is_beqi) {
      _masm.beqi(c_rarg0, cimm, taken);
    } else {
      _masm.bnei(c_rarg0, cimm, taken);
    }
    _masm.mv(c_rarg0, not_taken_val);
    _masm.ret();
    _masm.bind(taken);
    _masm.mv(c_rarg0, taken_val);
    _masm.ret();
  }
  _masm.flush();
  int64_t ret = ((zibi_func)entry)(arg);
  BufferBlob::free(bb);
  return ret;
}

TEST_VM(RiscV, zibi_execution) {
  // Only run if the current CPU actually implements Zibi. UseZibi alone is not
  // sufficient: a user can request -XX:+UseZibi on hardware without the
  // extension, and executing beqi/bnei there would SIGILL. supports_Zibi()
  // reflects detected hardware capability, so require both.
  if (!UseZibi || !VM_Version::supports_Zibi()) {
    return;
  }
  // cimm == 0 decodes to the comparison value -1.
  EXPECT_EQ(run_zibi_branch(true, 0, -1), 1); // beqi: -1 == -1 -> taken
  EXPECT_EQ(run_zibi_branch(true, 0, 0), 0);  // beqi: 0 == -1 -> not taken
  EXPECT_EQ(run_zibi_branch(false, 0, -1), 0); // bnei: -1 != -1 -> not taken
  EXPECT_EQ(run_zibi_branch(false, 0, 0), 1);  // bnei: 0 != -1 -> taken

  // cimm in [1, 31] decodes to the corresponding positive value.
  EXPECT_EQ(run_zibi_branch(true, 1, 1), 1);
  EXPECT_EQ(run_zibi_branch(true, 1, 2), 0);
  EXPECT_EQ(run_zibi_branch(true, 31, 31), 1);
  EXPECT_EQ(run_zibi_branch(true, 31, 30), 0);
  EXPECT_EQ(run_zibi_branch(false, 7, 7), 0);
  EXPECT_EQ(run_zibi_branch(false, 7, 8), 1);
  // Comparison constant is zero-extended to XLEN, so a negative arg never
  // equals a positive cimm.
  EXPECT_EQ(run_zibi_branch(true, 5, -5), 0);
  EXPECT_EQ(run_zibi_branch(false, 5, -5), 1);
}

#endif  // RISCV
