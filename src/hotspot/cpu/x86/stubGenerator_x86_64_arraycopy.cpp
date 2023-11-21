/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/macroAssembler.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "oops/objArrayKlass.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "stubGenerator_x86_64.hpp"
#ifdef COMPILER2
#include "opto/c2_globals.hpp"
#endif
#if INCLUDE_JVMCI
#include "jvmci/jvmci_globals.hpp"
#endif

#define __ _masm->

#define TIMES_OOP (UseCompressedOops ? Address::times_4 : Address::times_8)

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif // PRODUCT

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

#ifdef PRODUCT
#define INC_COUNTER_NP(counter, rscratch) ((void)0)
#else
#define INC_COUNTER_NP(counter, rscratch) \
BLOCK_COMMENT("inc_counter " #counter); \
inc_counter_np(_masm, counter, rscratch);

static void inc_counter_np(MacroAssembler* _masm, uint& counter, Register rscratch) {
  __ incrementl(ExternalAddress((address)&counter), rscratch);
}

#if COMPILER2_OR_JVMCI
static uint& get_profile_ctr(int shift) {
  if (shift == 0) {
    return SharedRuntime::_jbyte_array_copy_ctr;
  } else if (shift == 1) {
    return SharedRuntime::_jshort_array_copy_ctr;
  } else if (shift == 2) {
    return SharedRuntime::_jint_array_copy_ctr;
  } else {
    assert(shift == 3, "");
    return SharedRuntime::_jlong_array_copy_ctr;
  }
}
#endif // COMPILER2_OR_JVMCI
#endif // !PRODUCT

void StubGenerator::generate_arraycopy_stubs() {
  address entry;
  address entry_jbyte_arraycopy;
  address entry_jshort_arraycopy;
  address entry_jint_arraycopy;
  address entry_oop_arraycopy;
  address entry_jlong_arraycopy;
  address entry_checkcast_arraycopy;

  StubRoutines::_jbyte_disjoint_arraycopy  = generate_disjoint_byte_copy(false, &entry,
                                                                         "jbyte_disjoint_arraycopy");
  StubRoutines::_jbyte_arraycopy           = generate_conjoint_byte_copy(false, entry, &entry_jbyte_arraycopy,
                                                                         "jbyte_arraycopy");

  StubRoutines::_jshort_disjoint_arraycopy = generate_disjoint_short_copy(false, &entry,
                                                                          "jshort_disjoint_arraycopy");
  StubRoutines::_jshort_arraycopy          = generate_conjoint_short_copy(false, entry, &entry_jshort_arraycopy,
                                                                          "jshort_arraycopy");

  StubRoutines::_jint_disjoint_arraycopy   = generate_disjoint_int_oop_copy(false, false, &entry,
                                                                            "jint_disjoint_arraycopy");
  StubRoutines::_jint_arraycopy            = generate_conjoint_int_oop_copy(false, false, entry,
                                                                            &entry_jint_arraycopy, "jint_arraycopy");

  StubRoutines::_jlong_disjoint_arraycopy  = generate_disjoint_long_oop_copy(false, false, &entry,
                                                                             "jlong_disjoint_arraycopy");
  StubRoutines::_jlong_arraycopy           = generate_conjoint_long_oop_copy(false, false, entry,
                                                                             &entry_jlong_arraycopy, "jlong_arraycopy");
  if (UseCompressedOops) {
    StubRoutines::_oop_disjoint_arraycopy  = generate_disjoint_int_oop_copy(false, true, &entry,
                                                                            "oop_disjoint_arraycopy");
    StubRoutines::_oop_arraycopy           = generate_conjoint_int_oop_copy(false, true, entry,
                                                                            &entry_oop_arraycopy, "oop_arraycopy");
    StubRoutines::_oop_disjoint_arraycopy_uninit  = generate_disjoint_int_oop_copy(false, true, &entry,
                                                                                   "oop_disjoint_arraycopy_uninit",
                                                                                   /*dest_uninitialized*/true);
    StubRoutines::_oop_arraycopy_uninit           = generate_conjoint_int_oop_copy(false, true, entry,
                                                                                   nullptr, "oop_arraycopy_uninit",
                                                                                   /*dest_uninitialized*/true);
  } else {
    StubRoutines::_oop_disjoint_arraycopy  = generate_disjoint_long_oop_copy(false, true, &entry,
                                                                             "oop_disjoint_arraycopy");
    StubRoutines::_oop_arraycopy           = generate_conjoint_long_oop_copy(false, true, entry,
                                                                             &entry_oop_arraycopy, "oop_arraycopy");
    StubRoutines::_oop_disjoint_arraycopy_uninit  = generate_disjoint_long_oop_copy(false, true, &entry,
                                                                                    "oop_disjoint_arraycopy_uninit",
                                                                                    /*dest_uninitialized*/true);
    StubRoutines::_oop_arraycopy_uninit           = generate_conjoint_long_oop_copy(false, true, entry,
                                                                                    nullptr, "oop_arraycopy_uninit",
                                                                                    /*dest_uninitialized*/true);
  }

  StubRoutines::_checkcast_arraycopy        = generate_checkcast_copy("checkcast_arraycopy", &entry_checkcast_arraycopy);
  StubRoutines::_checkcast_arraycopy_uninit = generate_checkcast_copy("checkcast_arraycopy_uninit", nullptr,
                                                                      /*dest_uninitialized*/true);

  StubRoutines::_unsafe_arraycopy    = generate_unsafe_copy("unsafe_arraycopy",
                                                            entry_jbyte_arraycopy,
                                                            entry_jshort_arraycopy,
                                                            entry_jint_arraycopy,
                                                            entry_jlong_arraycopy);
  StubRoutines::_generic_arraycopy   = generate_generic_copy("generic_arraycopy",
                                                             entry_jbyte_arraycopy,
                                                             entry_jshort_arraycopy,
                                                             entry_jint_arraycopy,
                                                             entry_oop_arraycopy,
                                                             entry_jlong_arraycopy,
                                                             entry_checkcast_arraycopy);

  StubRoutines::_jbyte_fill = generate_fill(T_BYTE, false, "jbyte_fill");
  StubRoutines::_jshort_fill = generate_fill(T_SHORT, false, "jshort_fill");
  StubRoutines::_jint_fill = generate_fill(T_INT, false, "jint_fill");
  StubRoutines::_arrayof_jbyte_fill = generate_fill(T_BYTE, true, "arrayof_jbyte_fill");
  StubRoutines::_arrayof_jshort_fill = generate_fill(T_SHORT, true, "arrayof_jshort_fill");
  StubRoutines::_arrayof_jint_fill = generate_fill(T_INT, true, "arrayof_jint_fill");

  // We don't generate specialized code for HeapWord-aligned source
  // arrays, so just use the code we've already generated
  StubRoutines::_arrayof_jbyte_disjoint_arraycopy  = StubRoutines::_jbyte_disjoint_arraycopy;
  StubRoutines::_arrayof_jbyte_arraycopy           = StubRoutines::_jbyte_arraycopy;

  StubRoutines::_arrayof_jshort_disjoint_arraycopy = StubRoutines::_jshort_disjoint_arraycopy;
  StubRoutines::_arrayof_jshort_arraycopy          = StubRoutines::_jshort_arraycopy;

  StubRoutines::_arrayof_jint_disjoint_arraycopy   = StubRoutines::_jint_disjoint_arraycopy;
  StubRoutines::_arrayof_jint_arraycopy            = StubRoutines::_jint_arraycopy;

  StubRoutines::_arrayof_jlong_disjoint_arraycopy  = StubRoutines::_jlong_disjoint_arraycopy;
  StubRoutines::_arrayof_jlong_arraycopy           = StubRoutines::_jlong_arraycopy;

  StubRoutines::_arrayof_oop_disjoint_arraycopy    = StubRoutines::_oop_disjoint_arraycopy;
  StubRoutines::_arrayof_oop_arraycopy             = StubRoutines::_oop_arraycopy;

  StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit    = StubRoutines::_oop_disjoint_arraycopy_uninit;
  StubRoutines::_arrayof_oop_arraycopy_uninit             = StubRoutines::_oop_arraycopy_uninit;
}


// Verify that a register contains clean 32-bits positive value
// (high 32-bits are 0) so it could be used in 64-bits shifts.
//
//  Input:
//    Rint  -  32-bits value
//    Rtmp  -  scratch
//
void StubGenerator::assert_clean_int(Register Rint, Register Rtmp) {
#ifdef ASSERT
  Label L;
  assert_different_registers(Rtmp, Rint);
  __ movslq(Rtmp, Rint);
  __ cmpq(Rtmp, Rint);
  __ jcc(Assembler::equal, L);
  __ stop("high 32-bits of int value are not 0");
  __ bind(L);
#endif
}


//  Generate overlap test for array copy stubs
//
//  Input:
//     c_rarg0 - from
//     c_rarg1 - to
//     c_rarg2 - element count
//
//  Output:
//     rax   - &from[element count - 1]
//
void StubGenerator::array_overlap_test(address no_overlap_target, Label* NOLp, Address::ScaleFactor sf) {
  const Register from     = c_rarg0;
  const Register to       = c_rarg1;
  const Register count    = c_rarg2;
  const Register end_from = rax;

  __ cmpptr(to, from);
  __ lea(end_from, Address(from, count, sf, 0));
  if (NOLp == nullptr) {
    ExternalAddress no_overlap(no_overlap_target);
    __ jump_cc(Assembler::belowEqual, no_overlap);
    __ cmpptr(to, end_from);
    __ jump_cc(Assembler::aboveEqual, no_overlap);
  } else {
    __ jcc(Assembler::belowEqual, (*NOLp));
    __ cmpptr(to, end_from);
    __ jcc(Assembler::aboveEqual, (*NOLp));
  }
}


// Copy big chunks forward
//
// Inputs:
//   end_from     - source arrays end address
//   end_to       - destination array end address
//   qword_count  - 64-bits element count, negative
//   tmp1         - scratch
//   L_copy_bytes - entry label
//   L_copy_8_bytes  - exit  label
//
void StubGenerator::copy_bytes_forward(Register end_from, Register end_to,
                                       Register qword_count, Register tmp1,
                                       Register tmp2, Label& L_copy_bytes,
                                       Label& L_copy_8_bytes, DecoratorSet decorators,
                                       BasicType type) {
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  DEBUG_ONLY(__ stop("enter at entry label, not here"));
  Label L_loop;
  __ align(OptoLoopAlignment);
  if (UseUnalignedLoadStores) {
    Label L_end;
    __ BIND(L_loop);
    if (UseAVX >= 2) {
      bs->copy_load_at(_masm, decorators, type, 32,
                       xmm0, Address(end_from, qword_count, Address::times_8, -56),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 32,
                        Address(end_to, qword_count, Address::times_8, -56), xmm0,
                        tmp1, tmp2, xmm1);

      bs->copy_load_at(_masm, decorators, type, 32,
                       xmm0, Address(end_from, qword_count, Address::times_8, -24),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 32,
                        Address(end_to, qword_count, Address::times_8, -24), xmm0,
                        tmp1, tmp2, xmm1);
    } else {
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(end_from, qword_count, Address::times_8, -56),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(end_to, qword_count, Address::times_8, -56), xmm0,
                        tmp1, tmp2, xmm1);
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(end_from, qword_count, Address::times_8, -40),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(end_to, qword_count, Address::times_8, -40), xmm0,
                        tmp1, tmp2, xmm1);
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(end_from, qword_count, Address::times_8, -24),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(end_to, qword_count, Address::times_8, -24), xmm0,
                        tmp1, tmp2, xmm1);
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(end_from, qword_count, Address::times_8, -8),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(end_to, qword_count, Address::times_8, -8), xmm0,
                        tmp1, tmp2, xmm1);
    }

    __ BIND(L_copy_bytes);
    __ addptr(qword_count, 8);
    __ jcc(Assembler::lessEqual, L_loop);
    __ subptr(qword_count, 4);  // sub(8) and add(4)
    __ jcc(Assembler::greater, L_end);
    // Copy trailing 32 bytes
    if (UseAVX >= 2) {
      bs->copy_load_at(_masm, decorators, type, 32,
                       xmm0, Address(end_from, qword_count, Address::times_8, -24),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 32,
                        Address(end_to, qword_count, Address::times_8, -24), xmm0,
                        tmp1, tmp2, xmm1);
    } else {
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(end_from, qword_count, Address::times_8, -24),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(end_to, qword_count, Address::times_8, -24), xmm0,
                        tmp1, tmp2, xmm1);
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(end_from, qword_count, Address::times_8, -8),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(end_to, qword_count, Address::times_8, -8), xmm0,
                        tmp1, tmp2, xmm1);
    }
    __ addptr(qword_count, 4);
    __ BIND(L_end);
  } else {
    // Copy 32-bytes per iteration
    __ BIND(L_loop);
    bs->copy_load_at(_masm, decorators, type, 8,
                     tmp1, Address(end_from, qword_count, Address::times_8, -24),
                     tmp2);
    bs->copy_store_at(_masm, decorators, type, 8,
                      Address(end_to, qword_count, Address::times_8, -24), tmp1,
                      tmp2);
    bs->copy_load_at(_masm, decorators, type, 8,
                     tmp1, Address(end_from, qword_count, Address::times_8, -16),
                     tmp2);
    bs->copy_store_at(_masm, decorators, type, 8,
                      Address(end_to, qword_count, Address::times_8, -16), tmp1,
                      tmp2);
    bs->copy_load_at(_masm, decorators, type, 8,
                     tmp1, Address(end_from, qword_count, Address::times_8, -8),
                     tmp2);
    bs->copy_store_at(_masm, decorators, type, 8,
                      Address(end_to, qword_count, Address::times_8, -8), tmp1,
                      tmp2);
    bs->copy_load_at(_masm, decorators, type, 8,
                     tmp1, Address(end_from, qword_count, Address::times_8, 0),
                     tmp2);
    bs->copy_store_at(_masm, decorators, type, 8,
                      Address(end_to, qword_count, Address::times_8, 0), tmp1,
                      tmp2);

    __ BIND(L_copy_bytes);
    __ addptr(qword_count, 4);
    __ jcc(Assembler::lessEqual, L_loop);
  }
  __ subptr(qword_count, 4);
  __ jcc(Assembler::less, L_copy_8_bytes); // Copy trailing qwords
}


// Copy big chunks backward
//
// Inputs:
//   from         - source arrays address
//   dest         - destination array address
//   qword_count  - 64-bits element count
//   tmp1         - scratch
//   L_copy_bytes - entry label
//   L_copy_8_bytes  - exit  label
//
void StubGenerator::copy_bytes_backward(Register from, Register dest,
                                        Register qword_count, Register tmp1,
                                        Register tmp2, Label& L_copy_bytes,
                                        Label& L_copy_8_bytes, DecoratorSet decorators,
                                        BasicType type) {
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  DEBUG_ONLY(__ stop("enter at entry label, not here"));
  Label L_loop;
  __ align(OptoLoopAlignment);
  if (UseUnalignedLoadStores) {
    Label L_end;
    __ BIND(L_loop);
    if (UseAVX >= 2) {
      bs->copy_load_at(_masm, decorators, type, 32,
                       xmm0, Address(from, qword_count, Address::times_8, 32),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 32,
                        Address(dest, qword_count, Address::times_8, 32), xmm0,
                        tmp1, tmp2, xmm1);
      bs->copy_load_at(_masm, decorators, type, 32,
                       xmm0, Address(from, qword_count, Address::times_8, 0),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 32,
                        Address(dest, qword_count, Address::times_8, 0), xmm0,
                        tmp1, tmp2, xmm1);
    } else {
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(from, qword_count, Address::times_8, 48),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(dest, qword_count, Address::times_8, 48), xmm0,
                        tmp1, tmp2, xmm1);
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(from, qword_count, Address::times_8, 32),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(dest, qword_count, Address::times_8, 32), xmm0,
                        tmp1, tmp2, xmm1);
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(from, qword_count, Address::times_8, 16),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(dest, qword_count, Address::times_8, 16), xmm0,
                        tmp1, tmp2, xmm1);
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(from, qword_count, Address::times_8, 0),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(dest, qword_count, Address::times_8, 0), xmm0,
                        tmp1, tmp2, xmm1);
    }

    __ BIND(L_copy_bytes);
    __ subptr(qword_count, 8);
    __ jcc(Assembler::greaterEqual, L_loop);

    __ addptr(qword_count, 4);  // add(8) and sub(4)
    __ jcc(Assembler::less, L_end);
    // Copy trailing 32 bytes
    if (UseAVX >= 2) {
      bs->copy_load_at(_masm, decorators, type, 32,
                       xmm0, Address(from, qword_count, Address::times_8, 0),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 32,
                        Address(dest, qword_count, Address::times_8, 0), xmm0,
                        tmp1, tmp2, xmm1);
    } else {
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(from, qword_count, Address::times_8, 16),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(dest, qword_count, Address::times_8, 16), xmm0,
                        tmp1, tmp2, xmm1);
      bs->copy_load_at(_masm, decorators, type, 16,
                       xmm0, Address(from, qword_count, Address::times_8, 0),
                       tmp1, xmm1);
      bs->copy_store_at(_masm, decorators, type, 16,
                        Address(dest, qword_count, Address::times_8, 0), xmm0,
                        tmp1, tmp2, xmm1);
    }
    __ subptr(qword_count, 4);
    __ BIND(L_end);
  } else {
    // Copy 32-bytes per iteration
    __ BIND(L_loop);
    bs->copy_load_at(_masm, decorators, type, 8,
                     tmp1, Address(from, qword_count, Address::times_8, 24),
                     tmp2);
    bs->copy_store_at(_masm, decorators, type, 8,
                      Address(dest, qword_count, Address::times_8, 24), tmp1,
                      tmp2);
    bs->copy_load_at(_masm, decorators, type, 8,
                     tmp1, Address(from, qword_count, Address::times_8, 16),
                     tmp2);
    bs->copy_store_at(_masm, decorators, type, 8,
                      Address(dest, qword_count, Address::times_8, 16), tmp1,
                      tmp2);
    bs->copy_load_at(_masm, decorators, type, 8,
                     tmp1, Address(from, qword_count, Address::times_8, 8),
                     tmp2);
    bs->copy_store_at(_masm, decorators, type, 8,
                      Address(dest, qword_count, Address::times_8, 8), tmp1,
                      tmp2);
    bs->copy_load_at(_masm, decorators, type, 8,
                     tmp1, Address(from, qword_count, Address::times_8, 0),
                     tmp2);
    bs->copy_store_at(_masm, decorators, type, 8,
                      Address(dest, qword_count, Address::times_8, 0), tmp1,
                      tmp2);

    __ BIND(L_copy_bytes);
    __ subptr(qword_count, 4);
    __ jcc(Assembler::greaterEqual, L_loop);
  }
  __ addptr(qword_count, 4);
  __ jcc(Assembler::greater, L_copy_8_bytes); // Copy trailing qwords
}

#if COMPILER2_OR_JVMCI

// Note: Following rules apply to AVX3 optimized arraycopy stubs:-
// - If target supports AVX3 features (BW+VL+F) then implementation uses 32 byte vectors (YMMs)
//   for both special cases (various small block sizes) and aligned copy loop. This is the
//   default configuration.
// - If copy length is above AVX3Threshold, then implementation use 64 byte vectors (ZMMs)
//   for main copy loop (and subsequent tail) since bulk of the cycles will be consumed in it.
// - If user forces MaxVectorSize=32 then above 4096 bytes its seen that REP MOVs shows a
//   better performance for disjoint copies. For conjoint/backward copy vector based
//   copy performs better.
// - If user sets AVX3Threshold=0, then special cases for small blocks sizes operate over
//   64 byte vector registers (ZMMs).

// Inputs:
//   c_rarg0   - source array address
//   c_rarg1   - destination array address
//   c_rarg2   - element count, treated as ssize_t, can be zero
//
//
// Side Effects:
//   disjoint_copy_avx3_masked is set to the no-overlap entry point
//   used by generate_conjoint_[byte/int/short/long]_copy().
//
address StubGenerator::generate_disjoint_copy_avx3_masked(address* entry, const char *name,
                                                          int shift, bool aligned, bool is_oop,
                                                          bool dest_uninitialized) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  int avx3threshold = VM_Version::avx3_threshold();
  bool use64byteVector = (MaxVectorSize > 32) && (avx3threshold == 0);
  const int large_threshold = 2621440; // 2.5 MB
  Label L_main_loop, L_main_loop_64bytes, L_tail, L_tail64, L_exit, L_entry;
  Label L_repmovs, L_main_pre_loop, L_main_pre_loop_64bytes, L_pre_main_post_64;
  Label L_copy_large, L_finish;
  const Register from        = rdi;  // source array address
  const Register to          = rsi;  // destination array address
  const Register count       = rdx;  // elements count
  const Register temp1       = r8;
  const Register temp2       = r11;
  const Register temp3       = rax;
  const Register temp4       = rcx;
  // End pointers are inclusive, and if count is not zero they point
  // to the last unit copied:  end_to[0] := end_from[0]

  __ enter(); // required for proper stackwalking of RuntimeStub frame
  assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

  if (entry != nullptr) {
    *entry = __ pc();
     // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
    BLOCK_COMMENT("Entry:");
  }

  BasicType type_vec[] = { T_BYTE,  T_SHORT,  T_INT,   T_LONG};
  BasicType type = is_oop ? T_OBJECT : type_vec[shift];

  setup_argument_regs(type);

  DecoratorSet decorators = IN_HEAP | IS_ARRAY | ARRAYCOPY_DISJOINT;
  if (dest_uninitialized) {
    decorators |= IS_DEST_UNINITIALIZED;
  }
  if (aligned) {
    decorators |= ARRAYCOPY_ALIGNED;
  }
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->arraycopy_prologue(_masm, decorators, type, from, to, count);

  {
    // Type(shift)           byte(0), short(1), int(2),   long(3)
    int loop_size[]        = { 192,     96,       48,      24};
    int threshold[]        = { 4096,    2048,     1024,    512};

    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !is_oop && !aligned, true);
    // 'from', 'to' and 'count' are now valid

    // temp1 holds remaining count and temp4 holds running count used to compute
    // next address offset for start of to/from addresses (temp4 * scale).
    __ mov64(temp4, 0);
    __ movq(temp1, count);

    // Zero length check.
    __ BIND(L_tail);
    __ cmpq(temp1, 0);
    __ jcc(Assembler::lessEqual, L_exit);

    // Special cases using 32 byte [masked] vector copy operations.
    arraycopy_avx3_special_cases(xmm1, k2, from, to, temp1, shift,
                                 temp4, temp3, use64byteVector, L_entry, L_exit);

    // PRE-MAIN-POST loop for aligned copy.
    __ BIND(L_entry);

    if (MaxVectorSize == 64) {
      __ movq(temp2, temp1);
      __ shlq(temp2, shift);
      __ cmpq(temp2, large_threshold);
      __ jcc(Assembler::greaterEqual, L_copy_large);
    }
    if (avx3threshold != 0) {
      __ cmpq(count, threshold[shift]);
      if (MaxVectorSize == 64) {
        // Copy using 64 byte vectors.
        __ jcc(Assembler::greaterEqual, L_pre_main_post_64);
      } else {
        assert(MaxVectorSize < 64, "vector size should be < 64 bytes");
        // REP MOVS offer a faster copy path.
        __ jcc(Assembler::greaterEqual, L_repmovs);
      }
    }

    if ((MaxVectorSize < 64)  || (avx3threshold != 0)) {
      // Partial copy to make dst address 32 byte aligned.
      __ movq(temp2, to);
      __ andq(temp2, 31);
      __ jcc(Assembler::equal, L_main_pre_loop);

      __ negptr(temp2);
      __ addq(temp2, 32);
      if (shift) {
        __ shrq(temp2, shift);
      }
      __ movq(temp3, temp2);
      copy32_masked_avx(to, from, xmm1, k2, temp3, temp4, temp1, shift);
      __ movq(temp4, temp2);
      __ movq(temp1, count);
      __ subq(temp1, temp2);

      __ cmpq(temp1, loop_size[shift]);
      __ jcc(Assembler::less, L_tail);

      __ BIND(L_main_pre_loop);
      __ subq(temp1, loop_size[shift]);

      // Main loop with aligned copy block size of 192 bytes at 32 byte granularity.
      __ align32();
      __ BIND(L_main_loop);
         copy64_avx(to, from, temp4, xmm1, false, shift, 0);
         copy64_avx(to, from, temp4, xmm1, false, shift, 64);
         copy64_avx(to, from, temp4, xmm1, false, shift, 128);
         __ addptr(temp4, loop_size[shift]);
         __ subq(temp1, loop_size[shift]);
         __ jcc(Assembler::greater, L_main_loop);

      __ addq(temp1, loop_size[shift]);

      // Tail loop.
      __ jmp(L_tail);

      __ BIND(L_repmovs);
        __ movq(temp2, temp1);
        // Swap to(RSI) and from(RDI) addresses to comply with REP MOVs semantics.
        __ movq(temp3, to);
        __ movq(to,  from);
        __ movq(from, temp3);
        // Save to/from for restoration post rep_mov.
        __ movq(temp1, to);
        __ movq(temp3, from);
        if(shift < 3) {
          __ shrq(temp2, 3-shift);     // quad word count
        }
        __ movq(temp4 , temp2);        // move quad ward count into temp4(RCX).
        __ rep_mov();
        __ shlq(temp2, 3);             // convert quad words into byte count.
        if(shift) {
          __ shrq(temp2, shift);       // type specific count.
        }
        // Restore original addresses in to/from.
        __ movq(to, temp3);
        __ movq(from, temp1);
        __ movq(temp4, temp2);
        __ movq(temp1, count);
        __ subq(temp1, temp2);         // tailing part (less than a quad ward size).
        __ jmp(L_tail);
    }

    if (MaxVectorSize > 32) {
      __ BIND(L_pre_main_post_64);
      // Partial copy to make dst address 64 byte aligned.
      __ movq(temp2, to);
      __ andq(temp2, 63);
      __ jcc(Assembler::equal, L_main_pre_loop_64bytes);

      __ negptr(temp2);
      __ addq(temp2, 64);
      if (shift) {
        __ shrq(temp2, shift);
      }
      __ movq(temp3, temp2);
      copy64_masked_avx(to, from, xmm1, k2, temp3, temp4, temp1, shift, 0 , true);
      __ movq(temp4, temp2);
      __ movq(temp1, count);
      __ subq(temp1, temp2);

      __ cmpq(temp1, loop_size[shift]);
      __ jcc(Assembler::less, L_tail64);

      __ BIND(L_main_pre_loop_64bytes);
      __ subq(temp1, loop_size[shift]);

      // Main loop with aligned copy block size of 192 bytes at
      // 64 byte copy granularity.
      __ align32();
      __ BIND(L_main_loop_64bytes);
         copy64_avx(to, from, temp4, xmm1, false, shift, 0 , true);
         copy64_avx(to, from, temp4, xmm1, false, shift, 64, true);
         copy64_avx(to, from, temp4, xmm1, false, shift, 128, true);
         __ addptr(temp4, loop_size[shift]);
         __ subq(temp1, loop_size[shift]);
         __ jcc(Assembler::greater, L_main_loop_64bytes);

      __ addq(temp1, loop_size[shift]);
      // Zero length check.
      __ jcc(Assembler::lessEqual, L_exit);

      __ BIND(L_tail64);

      // Tail handling using 64 byte [masked] vector copy operations.
      use64byteVector = true;
      arraycopy_avx3_special_cases(xmm1, k2, from, to, temp1, shift,
                                   temp4, temp3, use64byteVector, L_entry, L_exit);
    }
    __ BIND(L_exit);
  }

  __ BIND(L_finish);
  address ucme_exit_pc = __ pc();
  // When called from generic_arraycopy r11 contains specific values
  // used during arraycopy epilogue, re-initializing r11.
  if (is_oop) {
    __ movq(r11, shift == 3 ? count : to);
  }
  bs->arraycopy_epilogue(_masm, decorators, type, from, to, count);
  restore_argument_regs(type);
  INC_COUNTER_NP(get_profile_ctr(shift), rscratch1); // Update counter after rscratch1 is free
  __ xorptr(rax, rax); // return 0
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  if (MaxVectorSize == 64) {
    __ BIND(L_copy_large);
    arraycopy_avx3_large(to, from, temp1, temp2, temp3, temp4, count, xmm1, xmm2, xmm3, xmm4, shift);
    __ jmp(L_finish);
  }
  return start;
}

void StubGenerator::arraycopy_avx3_large(Register to, Register from, Register temp1, Register temp2,
                                         Register temp3, Register temp4, Register count,
                                         XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3,
                                         XMMRegister xmm4, int shift) {

  // Type(shift)           byte(0), short(1), int(2),   long(3)
  int loop_size[]        = { 256,     128,       64,      32};
  int threshold[]        = { 4096,    2048,     1024,    512};

  Label L_main_loop_large;
  Label L_tail_large;
  Label L_exit_large;
  Label L_entry_large;
  Label L_main_pre_loop_large;
  Label L_pre_main_post_large;

  assert(MaxVectorSize == 64, "vector length != 64");
  __ BIND(L_entry_large);

  __ BIND(L_pre_main_post_large);
  // Partial copy to make dst address 64 byte aligned.
  __ movq(temp2, to);
  __ andq(temp2, 63);
  __ jcc(Assembler::equal, L_main_pre_loop_large);

  __ negptr(temp2);
  __ addq(temp2, 64);
  if (shift) {
    __ shrq(temp2, shift);
  }
  __ movq(temp3, temp2);
  copy64_masked_avx(to, from, xmm1, k2, temp3, temp4, temp1, shift, 0, true);
  __ movq(temp4, temp2);
  __ movq(temp1, count);
  __ subq(temp1, temp2);

  __ cmpq(temp1, loop_size[shift]);
  __ jcc(Assembler::less, L_tail_large);

  __ BIND(L_main_pre_loop_large);
  __ subq(temp1, loop_size[shift]);

  // Main loop with aligned copy block size of 256 bytes at 64 byte copy granularity.
  __ align32();
  __ BIND(L_main_loop_large);
  copy256_avx3(to, from, temp4, xmm1, xmm2, xmm3, xmm4, shift, 0);
  __ addptr(temp4, loop_size[shift]);
  __ subq(temp1, loop_size[shift]);
  __ jcc(Assembler::greater, L_main_loop_large);
  // fence needed because copy256_avx3 uses non-temporal stores
  __ sfence();

  __ addq(temp1, loop_size[shift]);
  // Zero length check.
  __ jcc(Assembler::lessEqual, L_exit_large);
  __ BIND(L_tail_large);
  // Tail handling using 64 byte [masked] vector copy operations.
  __ cmpq(temp1, 0);
  __ jcc(Assembler::lessEqual, L_exit_large);
  arraycopy_avx3_special_cases_256(xmm1, k2, from, to, temp1, shift,
                               temp4, temp3, L_exit_large);
  __ BIND(L_exit_large);
}

// Inputs:
//   c_rarg0   - source array address
//   c_rarg1   - destination array address
//   c_rarg2   - element count, treated as ssize_t, can be zero
//
//
address StubGenerator::generate_conjoint_copy_avx3_masked(address* entry, const char *name, int shift,
                                                          address nooverlap_target, bool aligned,
                                                          bool is_oop, bool dest_uninitialized) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  int avx3threshold = VM_Version::avx3_threshold();
  bool use64byteVector = (MaxVectorSize > 32) && (avx3threshold == 0);

  Label L_main_pre_loop, L_main_pre_loop_64bytes, L_pre_main_post_64;
  Label L_main_loop, L_main_loop_64bytes, L_tail, L_tail64, L_exit, L_entry;
  const Register from        = rdi;  // source array address
  const Register to          = rsi;  // destination array address
  const Register count       = rdx;  // elements count
  const Register temp1       = r8;
  const Register temp2       = rcx;
  const Register temp3       = r11;
  const Register temp4       = rax;
  // End pointers are inclusive, and if count is not zero they point
  // to the last unit copied:  end_to[0] := end_from[0]

  __ enter(); // required for proper stackwalking of RuntimeStub frame
  assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

  if (entry != nullptr) {
    *entry = __ pc();
     // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
    BLOCK_COMMENT("Entry:");
  }

  array_overlap_test(nooverlap_target, (Address::ScaleFactor)(shift));

  BasicType type_vec[] = { T_BYTE,  T_SHORT,  T_INT,   T_LONG};
  BasicType type = is_oop ? T_OBJECT : type_vec[shift];

  setup_argument_regs(type);

  DecoratorSet decorators = IN_HEAP | IS_ARRAY;
  if (dest_uninitialized) {
    decorators |= IS_DEST_UNINITIALIZED;
  }
  if (aligned) {
    decorators |= ARRAYCOPY_ALIGNED;
  }
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->arraycopy_prologue(_masm, decorators, type, from, to, count);
  {
    // Type(shift)       byte(0), short(1), int(2),   long(3)
    int loop_size[]   = { 192,     96,       48,      24};
    int threshold[]   = { 4096,    2048,     1024,    512};

    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !is_oop && !aligned, true);
    // 'from', 'to' and 'count' are now valid

    // temp1 holds remaining count.
    __ movq(temp1, count);

    // Zero length check.
    __ BIND(L_tail);
    __ cmpq(temp1, 0);
    __ jcc(Assembler::lessEqual, L_exit);

    __ mov64(temp2, 0);
    __ movq(temp3, temp1);
    // Special cases using 32 byte [masked] vector copy operations.
    arraycopy_avx3_special_cases_conjoint(xmm1, k2, from, to, temp2, temp3, temp1, shift,
                                          temp4, use64byteVector, L_entry, L_exit);

    // PRE-MAIN-POST loop for aligned copy.
    __ BIND(L_entry);

    if ((MaxVectorSize > 32) && (avx3threshold != 0)) {
      __ cmpq(temp1, threshold[shift]);
      __ jcc(Assembler::greaterEqual, L_pre_main_post_64);
    }

    if ((MaxVectorSize < 64)  || (avx3threshold != 0)) {
      // Partial copy to make dst address 32 byte aligned.
      __ leaq(temp2, Address(to, temp1, (Address::ScaleFactor)(shift), 0));
      __ andq(temp2, 31);
      __ jcc(Assembler::equal, L_main_pre_loop);

      if (shift) {
        __ shrq(temp2, shift);
      }
      __ subq(temp1, temp2);
      copy32_masked_avx(to, from, xmm1, k2, temp2, temp1, temp3, shift);

      __ cmpq(temp1, loop_size[shift]);
      __ jcc(Assembler::less, L_tail);

      __ BIND(L_main_pre_loop);

      // Main loop with aligned copy block size of 192 bytes at 32 byte granularity.
      __ align32();
      __ BIND(L_main_loop);
         copy64_avx(to, from, temp1, xmm1, true, shift, -64);
         copy64_avx(to, from, temp1, xmm1, true, shift, -128);
         copy64_avx(to, from, temp1, xmm1, true, shift, -192);
         __ subptr(temp1, loop_size[shift]);
         __ cmpq(temp1, loop_size[shift]);
         __ jcc(Assembler::greater, L_main_loop);

      // Tail loop.
      __ jmp(L_tail);
    }

    if (MaxVectorSize > 32) {
      __ BIND(L_pre_main_post_64);
      // Partial copy to make dst address 64 byte aligned.
      __ leaq(temp2, Address(to, temp1, (Address::ScaleFactor)(shift), 0));
      __ andq(temp2, 63);
      __ jcc(Assembler::equal, L_main_pre_loop_64bytes);

      if (shift) {
        __ shrq(temp2, shift);
      }
      __ subq(temp1, temp2);
      copy64_masked_avx(to, from, xmm1, k2, temp2, temp1, temp3, shift, 0 , true);

      __ cmpq(temp1, loop_size[shift]);
      __ jcc(Assembler::less, L_tail64);

      __ BIND(L_main_pre_loop_64bytes);

      // Main loop with aligned copy block size of 192 bytes at
      // 64 byte copy granularity.
      __ align32();
      __ BIND(L_main_loop_64bytes);
         copy64_avx(to, from, temp1, xmm1, true, shift, -64 , true);
         copy64_avx(to, from, temp1, xmm1, true, shift, -128, true);
         copy64_avx(to, from, temp1, xmm1, true, shift, -192, true);
         __ subq(temp1, loop_size[shift]);
         __ cmpq(temp1, loop_size[shift]);
         __ jcc(Assembler::greater, L_main_loop_64bytes);

      // Zero length check.
      __ cmpq(temp1, 0);
      __ jcc(Assembler::lessEqual, L_exit);

      __ BIND(L_tail64);

      // Tail handling using 64 byte [masked] vector copy operations.
      use64byteVector = true;
      __ mov64(temp2, 0);
      __ movq(temp3, temp1);
      arraycopy_avx3_special_cases_conjoint(xmm1, k2, from, to, temp2, temp3, temp1, shift,
                                            temp4, use64byteVector, L_entry, L_exit);
    }
    __ BIND(L_exit);
  }
  address ucme_exit_pc = __ pc();
  // When called from generic_arraycopy r11 contains specific values
  // used during arraycopy epilogue, re-initializing r11.
  if(is_oop) {
    __ movq(r11, count);
  }
  bs->arraycopy_epilogue(_masm, decorators, type, from, to, count);
  restore_argument_regs(type);
  INC_COUNTER_NP(get_profile_ctr(shift), rscratch1); // Update counter after rscratch1 is free
  __ xorptr(rax, rax); // return 0
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

void StubGenerator::arraycopy_avx3_special_cases(XMMRegister xmm, KRegister mask, Register from,
                                                 Register to, Register count, int shift,
                                                 Register index, Register temp,
                                                 bool use64byteVector, Label& L_entry, Label& L_exit) {
  Label L_entry_64, L_entry_96, L_entry_128;
  Label L_entry_160, L_entry_192;

  int size_mat[][6] = {
  /* T_BYTE */ {32 , 64,  96 , 128 , 160 , 192 },
  /* T_SHORT*/ {16 , 32,  48 , 64  , 80  , 96  },
  /* T_INT  */ {8  , 16,  24 , 32  , 40  , 48  },
  /* T_LONG */ {4  ,  8,  12 , 16  , 20  , 24  }
  };

  // Case A) Special case for length less than equal to 32 bytes.
  __ cmpq(count, size_mat[shift][0]);
  __ jccb(Assembler::greater, L_entry_64);
  copy32_masked_avx(to, from, xmm, mask, count, index, temp, shift);
  __ jmp(L_exit);

  // Case B) Special case for length less than equal to 64 bytes.
  __ BIND(L_entry_64);
  __ cmpq(count, size_mat[shift][1]);
  __ jccb(Assembler::greater, L_entry_96);
  copy64_masked_avx(to, from, xmm, mask, count, index, temp, shift, 0, use64byteVector);
  __ jmp(L_exit);

  // Case C) Special case for length less than equal to 96 bytes.
  __ BIND(L_entry_96);
  __ cmpq(count, size_mat[shift][2]);
  __ jccb(Assembler::greater, L_entry_128);
  copy64_avx(to, from, index, xmm, false, shift, 0, use64byteVector);
  __ subq(count, 64 >> shift);
  copy32_masked_avx(to, from, xmm, mask, count, index, temp, shift, 64);
  __ jmp(L_exit);

  // Case D) Special case for length less than equal to 128 bytes.
  __ BIND(L_entry_128);
  __ cmpq(count, size_mat[shift][3]);
  __ jccb(Assembler::greater, L_entry_160);
  copy64_avx(to, from, index, xmm, false, shift, 0, use64byteVector);
  copy32_avx(to, from, index, xmm, shift, 64);
  __ subq(count, 96 >> shift);
  copy32_masked_avx(to, from, xmm, mask, count, index, temp, shift, 96);
  __ jmp(L_exit);

  // Case E) Special case for length less than equal to 160 bytes.
  __ BIND(L_entry_160);
  __ cmpq(count, size_mat[shift][4]);
  __ jccb(Assembler::greater, L_entry_192);
  copy64_avx(to, from, index, xmm, false, shift, 0, use64byteVector);
  copy64_avx(to, from, index, xmm, false, shift, 64, use64byteVector);
  __ subq(count, 128 >> shift);
  copy32_masked_avx(to, from, xmm, mask, count, index, temp, shift, 128);
  __ jmp(L_exit);

  // Case F) Special case for length less than equal to 192 bytes.
  __ BIND(L_entry_192);
  __ cmpq(count, size_mat[shift][5]);
  __ jcc(Assembler::greater, L_entry);
  copy64_avx(to, from, index, xmm, false, shift, 0, use64byteVector);
  copy64_avx(to, from, index, xmm, false, shift, 64, use64byteVector);
  copy32_avx(to, from, index, xmm, shift, 128);
  __ subq(count, 160 >> shift);
  copy32_masked_avx(to, from, xmm, mask, count, index, temp, shift, 160);
  __ jmp(L_exit);
}

void StubGenerator::arraycopy_avx3_special_cases_256(XMMRegister xmm, KRegister mask, Register from,
                                                     Register to, Register count, int shift, Register index,
                                                     Register temp, Label& L_exit) {
  Label L_entry_64, L_entry_128, L_entry_192, L_entry_256;

  int size_mat[][4] = {
  /* T_BYTE */ {64, 128, 192, 256},
  /* T_SHORT*/ {32, 64 , 96 , 128},
  /* T_INT  */ {16, 32 , 48 ,  64},
  /* T_LONG */ { 8, 16 , 24 ,  32}
  };

  assert(MaxVectorSize == 64, "vector length != 64");
  // Case A) Special case for length less than or equal to 64 bytes.
  __ BIND(L_entry_64);
  __ cmpq(count, size_mat[shift][0]);
  __ jccb(Assembler::greater, L_entry_128);
  copy64_masked_avx(to, from, xmm, mask, count, index, temp, shift, 0, true);
  __ jmp(L_exit);

  // Case B) Special case for length less than or equal to 128 bytes.
  __ BIND(L_entry_128);
  __ cmpq(count, size_mat[shift][1]);
  __ jccb(Assembler::greater, L_entry_192);
  copy64_avx(to, from, index, xmm, false, shift, 0, true);
  __ subq(count, 64 >> shift);
  copy64_masked_avx(to, from, xmm, mask, count, index, temp, shift, 64, true);
  __ jmp(L_exit);

  // Case C) Special case for length less than or equal to 192 bytes.
  __ BIND(L_entry_192);
  __ cmpq(count, size_mat[shift][2]);
  __ jcc(Assembler::greater, L_entry_256);
  copy64_avx(to, from, index, xmm, false, shift, 0, true);
  copy64_avx(to, from, index, xmm, false, shift, 64, true);
  __ subq(count, 128 >> shift);
  copy64_masked_avx(to, from, xmm, mask, count, index, temp, shift, 128, true);
  __ jmp(L_exit);

  // Case D) Special case for length less than or equal to 256 bytes.
  __ BIND(L_entry_256);
  copy64_avx(to, from, index, xmm, false, shift, 0, true);
  copy64_avx(to, from, index, xmm, false, shift, 64, true);
  copy64_avx(to, from, index, xmm, false, shift, 128, true);
  __ subq(count, 192 >> shift);
  copy64_masked_avx(to, from, xmm, mask, count, index, temp, shift, 192, true);
  __ jmp(L_exit);
}

void StubGenerator::arraycopy_avx3_special_cases_conjoint(XMMRegister xmm, KRegister mask, Register from,
                                                           Register to, Register start_index, Register end_index,
                                                           Register count, int shift, Register temp,
                                                           bool use64byteVector, Label& L_entry, Label& L_exit) {
  Label L_entry_64, L_entry_96, L_entry_128;
  Label L_entry_160, L_entry_192;
  bool avx3 = (MaxVectorSize > 32) && (VM_Version::avx3_threshold() == 0);

  int size_mat[][6] = {
  /* T_BYTE */ {32 , 64,  96 , 128 , 160 , 192 },
  /* T_SHORT*/ {16 , 32,  48 , 64  , 80  , 96  },
  /* T_INT  */ {8  , 16,  24 , 32  , 40  , 48  },
  /* T_LONG */ {4  ,  8,  12 , 16  , 20  , 24  }
  };

  // Case A) Special case for length less than equal to 32 bytes.
  __ cmpq(count, size_mat[shift][0]);
  __ jccb(Assembler::greater, L_entry_64);
  copy32_masked_avx(to, from, xmm, mask, count, start_index, temp, shift);
  __ jmp(L_exit);

  // Case B) Special case for length less than equal to 64 bytes.
  __ BIND(L_entry_64);
  __ cmpq(count, size_mat[shift][1]);
  __ jccb(Assembler::greater, L_entry_96);
  if (avx3) {
     copy64_masked_avx(to, from, xmm, mask, count, start_index, temp, shift, 0, true);
  } else {
     copy32_avx(to, from, end_index, xmm, shift, -32);
     __ subq(count, 32 >> shift);
     copy32_masked_avx(to, from, xmm, mask, count, start_index, temp, shift);
  }
  __ jmp(L_exit);

  // Case C) Special case for length less than equal to 96 bytes.
  __ BIND(L_entry_96);
  __ cmpq(count, size_mat[shift][2]);
  __ jccb(Assembler::greater, L_entry_128);
  copy64_avx(to, from, end_index, xmm, true, shift, -64, use64byteVector);
  __ subq(count, 64 >> shift);
  copy32_masked_avx(to, from, xmm, mask, count, start_index, temp, shift);
  __ jmp(L_exit);

  // Case D) Special case for length less than equal to 128 bytes.
  __ BIND(L_entry_128);
  __ cmpq(count, size_mat[shift][3]);
  __ jccb(Assembler::greater, L_entry_160);
  copy64_avx(to, from, end_index, xmm, true, shift, -64, use64byteVector);
  copy32_avx(to, from, end_index, xmm, shift, -96);
  __ subq(count, 96 >> shift);
  copy32_masked_avx(to, from, xmm, mask, count, start_index, temp, shift);
  __ jmp(L_exit);

  // Case E) Special case for length less than equal to 160 bytes.
  __ BIND(L_entry_160);
  __ cmpq(count, size_mat[shift][4]);
  __ jccb(Assembler::greater, L_entry_192);
  copy64_avx(to, from, end_index, xmm, true, shift, -64, use64byteVector);
  copy64_avx(to, from, end_index, xmm, true, shift, -128, use64byteVector);
  __ subq(count, 128 >> shift);
  copy32_masked_avx(to, from, xmm, mask, count, start_index, temp, shift);
  __ jmp(L_exit);

  // Case F) Special case for length less than equal to 192 bytes.
  __ BIND(L_entry_192);
  __ cmpq(count, size_mat[shift][5]);
  __ jcc(Assembler::greater, L_entry);
  copy64_avx(to, from, end_index, xmm, true, shift, -64, use64byteVector);
  copy64_avx(to, from, end_index, xmm, true, shift, -128, use64byteVector);
  copy32_avx(to, from, end_index, xmm, shift, -160);
  __ subq(count, 160 >> shift);
  copy32_masked_avx(to, from, xmm, mask, count, start_index, temp, shift);
  __ jmp(L_exit);
}

void StubGenerator::copy256_avx3(Register dst, Register src, Register index, XMMRegister xmm1,
                                XMMRegister xmm2, XMMRegister xmm3, XMMRegister xmm4,
                                int shift, int offset) {
  if (MaxVectorSize == 64) {
    Address::ScaleFactor scale = (Address::ScaleFactor)(shift);
    __ prefetcht0(Address(src, index, scale, offset + 0x200));
    __ prefetcht0(Address(src, index, scale, offset + 0x240));
    __ prefetcht0(Address(src, index, scale, offset + 0x280));
    __ prefetcht0(Address(src, index, scale, offset + 0x2C0));

    __ prefetcht0(Address(src, index, scale, offset + 0x400));
    __ prefetcht0(Address(src, index, scale, offset + 0x440));
    __ prefetcht0(Address(src, index, scale, offset + 0x480));
    __ prefetcht0(Address(src, index, scale, offset + 0x4C0));

    __ evmovdquq(xmm1, Address(src, index, scale, offset), Assembler::AVX_512bit);
    __ evmovdquq(xmm2, Address(src, index, scale, offset + 0x40), Assembler::AVX_512bit);
    __ evmovdquq(xmm3, Address(src, index, scale, offset + 0x80), Assembler::AVX_512bit);
    __ evmovdquq(xmm4, Address(src, index, scale, offset + 0xC0), Assembler::AVX_512bit);

    __ evmovntdquq(Address(dst, index, scale, offset), xmm1, Assembler::AVX_512bit);
    __ evmovntdquq(Address(dst, index, scale, offset + 0x40), xmm2, Assembler::AVX_512bit);
    __ evmovntdquq(Address(dst, index, scale, offset + 0x80), xmm3, Assembler::AVX_512bit);
    __ evmovntdquq(Address(dst, index, scale, offset + 0xC0), xmm4, Assembler::AVX_512bit);
  }
}

void StubGenerator::copy64_masked_avx(Register dst, Register src, XMMRegister xmm,
                                       KRegister mask, Register length, Register index,
                                       Register temp, int shift, int offset,
                                       bool use64byteVector) {
  BasicType type[] = { T_BYTE,  T_SHORT,  T_INT,   T_LONG};
  assert(MaxVectorSize >= 32, "vector length should be >= 32");
  if (!use64byteVector) {
    copy32_avx(dst, src, index, xmm, shift, offset);
    __ subptr(length, 32 >> shift);
    copy32_masked_avx(dst, src, xmm, mask, length, index, temp, shift, offset+32);
  } else {
    Address::ScaleFactor scale = (Address::ScaleFactor)(shift);
    assert(MaxVectorSize == 64, "vector length != 64");
    __ mov64(temp, -1L);
    __ bzhiq(temp, temp, length);
    __ kmovql(mask, temp);
    __ evmovdqu(type[shift], mask, xmm, Address(src, index, scale, offset), false, Assembler::AVX_512bit);
    __ evmovdqu(type[shift], mask, Address(dst, index, scale, offset), xmm, true, Assembler::AVX_512bit);
  }
}


void StubGenerator::copy32_masked_avx(Register dst, Register src, XMMRegister xmm,
                                       KRegister mask, Register length, Register index,
                                       Register temp, int shift, int offset) {
  assert(MaxVectorSize >= 32, "vector length should be >= 32");
  BasicType type[] = { T_BYTE,  T_SHORT,  T_INT,   T_LONG};
  Address::ScaleFactor scale = (Address::ScaleFactor)(shift);
  __ mov64(temp, -1L);
  __ bzhiq(temp, temp, length);
  __ kmovql(mask, temp);
  __ evmovdqu(type[shift], mask, xmm, Address(src, index, scale, offset), false, Assembler::AVX_256bit);
  __ evmovdqu(type[shift], mask, Address(dst, index, scale, offset), xmm, true, Assembler::AVX_256bit);
}


void StubGenerator::copy32_avx(Register dst, Register src, Register index, XMMRegister xmm,
                                int shift, int offset) {
  assert(MaxVectorSize >= 32, "vector length should be >= 32");
  Address::ScaleFactor scale = (Address::ScaleFactor)(shift);
  __ vmovdqu(xmm, Address(src, index, scale, offset));
  __ vmovdqu(Address(dst, index, scale, offset), xmm);
}


void StubGenerator::copy64_avx(Register dst, Register src, Register index, XMMRegister xmm,
                                bool conjoint, int shift, int offset, bool use64byteVector) {
  assert(MaxVectorSize == 64 || MaxVectorSize == 32, "vector length mismatch");
  if (!use64byteVector) {
    if (conjoint) {
      copy32_avx(dst, src, index, xmm, shift, offset+32);
      copy32_avx(dst, src, index, xmm, shift, offset);
    } else {
      copy32_avx(dst, src, index, xmm, shift, offset);
      copy32_avx(dst, src, index, xmm, shift, offset+32);
    }
  } else {
    Address::ScaleFactor scale = (Address::ScaleFactor)(shift);
    __ evmovdquq(xmm, Address(src, index, scale, offset), Assembler::AVX_512bit);
    __ evmovdquq(Address(dst, index, scale, offset), xmm, Assembler::AVX_512bit);
  }
}

#endif // COMPILER2_OR_JVMCI


// Arguments:
//   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
//             ignored
//   name    - stub name string
//
// Inputs:
//   c_rarg0   - source array address
//   c_rarg1   - destination array address
//   c_rarg2   - element count, treated as ssize_t, can be zero
//
// If 'from' and/or 'to' are aligned on 4-, 2-, or 1-byte boundaries,
// we let the hardware handle it.  The one to eight bytes within words,
// dwords or qwords that span cache line boundaries will still be loaded
// and stored atomically.
//
// Side Effects:
//   disjoint_byte_copy_entry is set to the no-overlap entry point
//   used by generate_conjoint_byte_copy().
//
address StubGenerator::generate_disjoint_byte_copy(bool aligned, address* entry, const char *name) {
#if COMPILER2_OR_JVMCI
  if (VM_Version::supports_avx512vlbw() && VM_Version::supports_bmi2() && MaxVectorSize  >= 32) {
     return generate_disjoint_copy_avx3_masked(entry, "jbyte_disjoint_arraycopy_avx3", 0,
                                               aligned, false, false);
  }
#endif
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();
  DecoratorSet decorators = IN_HEAP | IS_ARRAY | ARRAYCOPY_DISJOINT;

  Label L_copy_bytes, L_copy_8_bytes, L_copy_4_bytes, L_copy_2_bytes;
  Label L_copy_byte, L_exit;
  const Register from        = rdi;  // source array address
  const Register to          = rsi;  // destination array address
  const Register count       = rdx;  // elements count
  const Register byte_count  = rcx;
  const Register qword_count = count;
  const Register end_from    = from; // source array end address
  const Register end_to      = to;   // destination array end address
  // End pointers are inclusive, and if count is not zero they point
  // to the last unit copied:  end_to[0] := end_from[0]

  __ enter(); // required for proper stackwalking of RuntimeStub frame
  assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

  if (entry != nullptr) {
    *entry = __ pc();
     // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
    BLOCK_COMMENT("Entry:");
  }

  setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                    // r9 and r10 may be used to save non-volatile registers

  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !aligned, true);
    // 'from', 'to' and 'count' are now valid
    __ movptr(byte_count, count);
    __ shrptr(count, 3); // count => qword_count

    // Copy from low to high addresses.  Use 'to' as scratch.
    __ lea(end_from, Address(from, qword_count, Address::times_8, -8));
    __ lea(end_to,   Address(to,   qword_count, Address::times_8, -8));
    __ negptr(qword_count); // make the count negative
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(end_from, qword_count, Address::times_8, 8));
    __ movq(Address(end_to, qword_count, Address::times_8, 8), rax);
    __ increment(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    // Check for and copy trailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(byte_count, 4);
    __ jccb(Assembler::zero, L_copy_2_bytes);
    __ movl(rax, Address(end_from, 8));
    __ movl(Address(end_to, 8), rax);

    __ addptr(end_from, 4);
    __ addptr(end_to, 4);

    // Check for and copy trailing word
  __ BIND(L_copy_2_bytes);
    __ testl(byte_count, 2);
    __ jccb(Assembler::zero, L_copy_byte);
    __ movw(rax, Address(end_from, 8));
    __ movw(Address(end_to, 8), rax);

    __ addptr(end_from, 2);
    __ addptr(end_to, 2);

    // Check for and copy trailing byte
  __ BIND(L_copy_byte);
    __ testl(byte_count, 1);
    __ jccb(Assembler::zero, L_exit);
    __ movb(rax, Address(end_from, 8));
    __ movb(Address(end_to, 8), rax);
  }
__ BIND(L_exit);
  address ucme_exit_pc = __ pc();
  restore_arg_regs();
  INC_COUNTER_NP(SharedRuntime::_jbyte_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
  __ xorptr(rax, rax); // return 0
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  {
    UnsafeCopyMemoryMark ucmm(this, !aligned, false, ucme_exit_pc);
    // Copy in multi-bytes chunks
    copy_bytes_forward(end_from, end_to, qword_count, rax, r10, L_copy_bytes, L_copy_8_bytes, decorators, T_BYTE);
    __ jmp(L_copy_4_bytes);
  }
  return start;
}


// Arguments:
//   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
//             ignored
//   name    - stub name string
//
// Inputs:
//   c_rarg0   - source array address
//   c_rarg1   - destination array address
//   c_rarg2   - element count, treated as ssize_t, can be zero
//
// If 'from' and/or 'to' are aligned on 4-, 2-, or 1-byte boundaries,
// we let the hardware handle it.  The one to eight bytes within words,
// dwords or qwords that span cache line boundaries will still be loaded
// and stored atomically.
//
address StubGenerator::generate_conjoint_byte_copy(bool aligned, address nooverlap_target,
                                                   address* entry, const char *name) {
#if COMPILER2_OR_JVMCI
  if (VM_Version::supports_avx512vlbw() && VM_Version::supports_bmi2() && MaxVectorSize  >= 32) {
     return generate_conjoint_copy_avx3_masked(entry, "jbyte_conjoint_arraycopy_avx3", 0,
                                               nooverlap_target, aligned, false, false);
  }
#endif
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();
  DecoratorSet decorators = IN_HEAP | IS_ARRAY;

  Label L_copy_bytes, L_copy_8_bytes, L_copy_4_bytes, L_copy_2_bytes;
  const Register from        = rdi;  // source array address
  const Register to          = rsi;  // destination array address
  const Register count       = rdx;  // elements count
  const Register byte_count  = rcx;
  const Register qword_count = count;

  __ enter(); // required for proper stackwalking of RuntimeStub frame
  assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

  if (entry != nullptr) {
    *entry = __ pc();
    // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
    BLOCK_COMMENT("Entry:");
  }

  array_overlap_test(nooverlap_target, Address::times_1);
  setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                    // r9 and r10 may be used to save non-volatile registers

  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !aligned, true);
    // 'from', 'to' and 'count' are now valid
    __ movptr(byte_count, count);
    __ shrptr(count, 3);   // count => qword_count

    // Copy from high to low addresses.

    // Check for and copy trailing byte
    __ testl(byte_count, 1);
    __ jcc(Assembler::zero, L_copy_2_bytes);
    __ movb(rax, Address(from, byte_count, Address::times_1, -1));
    __ movb(Address(to, byte_count, Address::times_1, -1), rax);
    __ decrement(byte_count); // Adjust for possible trailing word

    // Check for and copy trailing word
  __ BIND(L_copy_2_bytes);
    __ testl(byte_count, 2);
    __ jcc(Assembler::zero, L_copy_4_bytes);
    __ movw(rax, Address(from, byte_count, Address::times_1, -2));
    __ movw(Address(to, byte_count, Address::times_1, -2), rax);

    // Check for and copy trailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(byte_count, 4);
    __ jcc(Assembler::zero, L_copy_bytes);
    __ movl(rax, Address(from, qword_count, Address::times_8));
    __ movl(Address(to, qword_count, Address::times_8), rax);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(from, qword_count, Address::times_8, -8));
    __ movq(Address(to, qword_count, Address::times_8, -8), rax);
    __ decrement(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);
  }
  restore_arg_regs();
  INC_COUNTER_NP(SharedRuntime::_jbyte_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
  __ xorptr(rax, rax); // return 0
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !aligned, true);
    // Copy in multi-bytes chunks
    copy_bytes_backward(from, to, qword_count, rax, r10, L_copy_bytes, L_copy_8_bytes, decorators, T_BYTE);
  }
  restore_arg_regs();
  INC_COUNTER_NP(SharedRuntime::_jbyte_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
  __ xorptr(rax, rax); // return 0
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}


// Arguments:
//   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
//             ignored
//   name    - stub name string
//
// Inputs:
//   c_rarg0   - source array address
//   c_rarg1   - destination array address
//   c_rarg2   - element count, treated as ssize_t, can be zero
//
// If 'from' and/or 'to' are aligned on 4- or 2-byte boundaries, we
// let the hardware handle it.  The two or four words within dwords
// or qwords that span cache line boundaries will still be loaded
// and stored atomically.
//
// Side Effects:
//   disjoint_short_copy_entry is set to the no-overlap entry point
//   used by generate_conjoint_short_copy().
//
address StubGenerator::generate_disjoint_short_copy(bool aligned, address *entry, const char *name) {
#if COMPILER2_OR_JVMCI
  if (VM_Version::supports_avx512vlbw() && VM_Version::supports_bmi2() && MaxVectorSize  >= 32) {
     return generate_disjoint_copy_avx3_masked(entry, "jshort_disjoint_arraycopy_avx3", 1,
                                               aligned, false, false);
  }
#endif

  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();
  DecoratorSet decorators = IN_HEAP | IS_ARRAY | ARRAYCOPY_DISJOINT;

  Label L_copy_bytes, L_copy_8_bytes, L_copy_4_bytes,L_copy_2_bytes,L_exit;
  const Register from        = rdi;  // source array address
  const Register to          = rsi;  // destination array address
  const Register count       = rdx;  // elements count
  const Register word_count  = rcx;
  const Register qword_count = count;
  const Register end_from    = from; // source array end address
  const Register end_to      = to;   // destination array end address
  // End pointers are inclusive, and if count is not zero they point
  // to the last unit copied:  end_to[0] := end_from[0]

  __ enter(); // required for proper stackwalking of RuntimeStub frame
  assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

  if (entry != nullptr) {
    *entry = __ pc();
    // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
    BLOCK_COMMENT("Entry:");
  }

  setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                    // r9 and r10 may be used to save non-volatile registers

  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !aligned, true);
    // 'from', 'to' and 'count' are now valid
    __ movptr(word_count, count);
    __ shrptr(count, 2); // count => qword_count

    // Copy from low to high addresses.  Use 'to' as scratch.
    __ lea(end_from, Address(from, qword_count, Address::times_8, -8));
    __ lea(end_to,   Address(to,   qword_count, Address::times_8, -8));
    __ negptr(qword_count);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(end_from, qword_count, Address::times_8, 8));
    __ movq(Address(end_to, qword_count, Address::times_8, 8), rax);
    __ increment(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    // Original 'dest' is trashed, so we can't use it as a
    // base register for a possible trailing word copy

    // Check for and copy trailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(word_count, 2);
    __ jccb(Assembler::zero, L_copy_2_bytes);
    __ movl(rax, Address(end_from, 8));
    __ movl(Address(end_to, 8), rax);

    __ addptr(end_from, 4);
    __ addptr(end_to, 4);

    // Check for and copy trailing word
  __ BIND(L_copy_2_bytes);
    __ testl(word_count, 1);
    __ jccb(Assembler::zero, L_exit);
    __ movw(rax, Address(end_from, 8));
    __ movw(Address(end_to, 8), rax);
  }
__ BIND(L_exit);
  address ucme_exit_pc = __ pc();
  restore_arg_regs();
  INC_COUNTER_NP(SharedRuntime::_jshort_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
  __ xorptr(rax, rax); // return 0
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  {
    UnsafeCopyMemoryMark ucmm(this, !aligned, false, ucme_exit_pc);
    // Copy in multi-bytes chunks
    copy_bytes_forward(end_from, end_to, qword_count, rax, r10, L_copy_bytes, L_copy_8_bytes, decorators, T_SHORT);
    __ jmp(L_copy_4_bytes);
  }

  return start;
}


address StubGenerator::generate_fill(BasicType t, bool aligned, const char *name) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  BLOCK_COMMENT("Entry:");

  const Register to       = c_rarg0;  // destination array address
  const Register value    = c_rarg1;  // value
  const Register count    = c_rarg2;  // elements count
  __ mov(r11, count);

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  __ generate_fill(t, aligned, to, value, r11, rax, xmm0);

  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}


// Arguments:
//   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
//             ignored
//   name    - stub name string
//
// Inputs:
//   c_rarg0   - source array address
//   c_rarg1   - destination array address
//   c_rarg2   - element count, treated as ssize_t, can be zero
//
// If 'from' and/or 'to' are aligned on 4- or 2-byte boundaries, we
// let the hardware handle it.  The two or four words within dwords
// or qwords that span cache line boundaries will still be loaded
// and stored atomically.
//
address StubGenerator::generate_conjoint_short_copy(bool aligned, address nooverlap_target,
                                                    address *entry, const char *name) {
#if COMPILER2_OR_JVMCI
  if (VM_Version::supports_avx512vlbw() && VM_Version::supports_bmi2() && MaxVectorSize  >= 32) {
     return generate_conjoint_copy_avx3_masked(entry, "jshort_conjoint_arraycopy_avx3", 1,
                                               nooverlap_target, aligned, false, false);
  }
#endif
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();
  DecoratorSet decorators = IN_HEAP | IS_ARRAY;

  Label L_copy_bytes, L_copy_8_bytes, L_copy_4_bytes;
  const Register from        = rdi;  // source array address
  const Register to          = rsi;  // destination array address
  const Register count       = rdx;  // elements count
  const Register word_count  = rcx;
  const Register qword_count = count;

  __ enter(); // required for proper stackwalking of RuntimeStub frame
  assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

  if (entry != nullptr) {
    *entry = __ pc();
    // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
    BLOCK_COMMENT("Entry:");
  }

  array_overlap_test(nooverlap_target, Address::times_2);
  setup_arg_regs(); // from => rdi, to => rsi, count => rdx
                    // r9 and r10 may be used to save non-volatile registers

  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !aligned, true);
    // 'from', 'to' and 'count' are now valid
    __ movptr(word_count, count);
    __ shrptr(count, 2); // count => qword_count

    // Copy from high to low addresses.  Use 'to' as scratch.

    // Check for and copy trailing word
    __ testl(word_count, 1);
    __ jccb(Assembler::zero, L_copy_4_bytes);
    __ movw(rax, Address(from, word_count, Address::times_2, -2));
    __ movw(Address(to, word_count, Address::times_2, -2), rax);

   // Check for and copy trailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(word_count, 2);
    __ jcc(Assembler::zero, L_copy_bytes);
    __ movl(rax, Address(from, qword_count, Address::times_8));
    __ movl(Address(to, qword_count, Address::times_8), rax);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(from, qword_count, Address::times_8, -8));
    __ movq(Address(to, qword_count, Address::times_8, -8), rax);
    __ decrement(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);
  }
  restore_arg_regs();
  INC_COUNTER_NP(SharedRuntime::_jshort_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
  __ xorptr(rax, rax); // return 0
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !aligned, true);
    // Copy in multi-bytes chunks
    copy_bytes_backward(from, to, qword_count, rax, r10, L_copy_bytes, L_copy_8_bytes, decorators, T_SHORT);
  }
  restore_arg_regs();
  INC_COUNTER_NP(SharedRuntime::_jshort_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
  __ xorptr(rax, rax); // return 0
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}


// Arguments:
//   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
//             ignored
//   is_oop  - true => oop array, so generate store check code
//   name    - stub name string
//
// Inputs:
//   c_rarg0   - source array address
//   c_rarg1   - destination array address
//   c_rarg2   - element count, treated as ssize_t, can be zero
//
// If 'from' and/or 'to' are aligned on 4-byte boundaries, we let
// the hardware handle it.  The two dwords within qwords that span
// cache line boundaries will still be loaded and stored atomically.
//
// Side Effects:
//   disjoint_int_copy_entry is set to the no-overlap entry point
//   used by generate_conjoint_int_oop_copy().
//
address StubGenerator::generate_disjoint_int_oop_copy(bool aligned, bool is_oop, address* entry,
                                                      const char *name, bool dest_uninitialized) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
#if COMPILER2_OR_JVMCI
  if ((!is_oop || bs->supports_avx3_masked_arraycopy()) && VM_Version::supports_avx512vlbw() && VM_Version::supports_bmi2() && MaxVectorSize  >= 32) {
     return generate_disjoint_copy_avx3_masked(entry, "jint_disjoint_arraycopy_avx3", 2,
                                               aligned, is_oop, dest_uninitialized);
  }
#endif

  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  Label L_copy_bytes, L_copy_8_bytes, L_copy_4_bytes, L_exit;
  const Register from        = rdi;  // source array address
  const Register to          = rsi;  // destination array address
  const Register count       = rdx;  // elements count
  const Register dword_count = rcx;
  const Register qword_count = count;
  const Register end_from    = from; // source array end address
  const Register end_to      = to;   // destination array end address
  // End pointers are inclusive, and if count is not zero they point
  // to the last unit copied:  end_to[0] := end_from[0]

  __ enter(); // required for proper stackwalking of RuntimeStub frame
  assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

  if (entry != nullptr) {
    *entry = __ pc();
    // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
    BLOCK_COMMENT("Entry:");
  }

  setup_arg_regs_using_thread(); // from => rdi, to => rsi, count => rdx
                                 // r9 is used to save r15_thread

  DecoratorSet decorators = IN_HEAP | IS_ARRAY | ARRAYCOPY_DISJOINT;
  if (dest_uninitialized) {
    decorators |= IS_DEST_UNINITIALIZED;
  }
  if (aligned) {
    decorators |= ARRAYCOPY_ALIGNED;
  }

  BasicType type = is_oop ? T_OBJECT : T_INT;
  bs->arraycopy_prologue(_masm, decorators, type, from, to, count);

  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !is_oop && !aligned, true);
    // 'from', 'to' and 'count' are now valid
    __ movptr(dword_count, count);
    __ shrptr(count, 1); // count => qword_count

    // Copy from low to high addresses.  Use 'to' as scratch.
    __ lea(end_from, Address(from, qword_count, Address::times_8, -8));
    __ lea(end_to,   Address(to,   qword_count, Address::times_8, -8));
    __ negptr(qword_count);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(end_from, qword_count, Address::times_8, 8));
    __ movq(Address(end_to, qword_count, Address::times_8, 8), rax);
    __ increment(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);

    // Check for and copy trailing dword
  __ BIND(L_copy_4_bytes);
    __ testl(dword_count, 1); // Only byte test since the value is 0 or 1
    __ jccb(Assembler::zero, L_exit);
    __ movl(rax, Address(end_from, 8));
    __ movl(Address(end_to, 8), rax);
  }
__ BIND(L_exit);
  address ucme_exit_pc = __ pc();
  bs->arraycopy_epilogue(_masm, decorators, type, from, to, dword_count);
  restore_arg_regs_using_thread();
  INC_COUNTER_NP(SharedRuntime::_jint_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
  __ vzeroupper();
  __ xorptr(rax, rax); // return 0
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  {
    UnsafeCopyMemoryMark ucmm(this, !is_oop && !aligned, false, ucme_exit_pc);
    // Copy in multi-bytes chunks
    copy_bytes_forward(end_from, end_to, qword_count, rax, r10, L_copy_bytes, L_copy_8_bytes, decorators, is_oop ? T_OBJECT : T_INT);
    __ jmp(L_copy_4_bytes);
  }

  return start;
}


// Arguments:
//   aligned - true => Input and output aligned on a HeapWord == 8-byte boundary
//             ignored
//   is_oop  - true => oop array, so generate store check code
//   name    - stub name string
//
// Inputs:
//   c_rarg0   - source array address
//   c_rarg1   - destination array address
//   c_rarg2   - element count, treated as ssize_t, can be zero
//
// If 'from' and/or 'to' are aligned on 4-byte boundaries, we let
// the hardware handle it.  The two dwords within qwords that span
// cache line boundaries will still be loaded and stored atomically.
//
address StubGenerator::generate_conjoint_int_oop_copy(bool aligned, bool is_oop, address nooverlap_target,
                                                      address *entry, const char *name,
                                                      bool dest_uninitialized) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
#if COMPILER2_OR_JVMCI
  if ((!is_oop || bs->supports_avx3_masked_arraycopy()) && VM_Version::supports_avx512vlbw() && VM_Version::supports_bmi2() && MaxVectorSize  >= 32) {
     return generate_conjoint_copy_avx3_masked(entry, "jint_conjoint_arraycopy_avx3", 2,
                                               nooverlap_target, aligned, is_oop, dest_uninitialized);
  }
#endif
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  Label L_copy_bytes, L_copy_8_bytes, L_exit;
  const Register from        = rdi;  // source array address
  const Register to          = rsi;  // destination array address
  const Register count       = rdx;  // elements count
  const Register dword_count = rcx;
  const Register qword_count = count;

  __ enter(); // required for proper stackwalking of RuntimeStub frame
  assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

  if (entry != nullptr) {
    *entry = __ pc();
     // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
    BLOCK_COMMENT("Entry:");
  }

  array_overlap_test(nooverlap_target, Address::times_4);
  setup_arg_regs_using_thread(); // from => rdi, to => rsi, count => rdx
                                 // r9 is used to save r15_thread

  DecoratorSet decorators = IN_HEAP | IS_ARRAY;
  if (dest_uninitialized) {
    decorators |= IS_DEST_UNINITIALIZED;
  }
  if (aligned) {
    decorators |= ARRAYCOPY_ALIGNED;
  }

  BasicType type = is_oop ? T_OBJECT : T_INT;
  // no registers are destroyed by this call
  bs->arraycopy_prologue(_masm, decorators, type, from, to, count);

  assert_clean_int(count, rax); // Make sure 'count' is clean int.
  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !is_oop && !aligned, true);
    // 'from', 'to' and 'count' are now valid
    __ movptr(dword_count, count);
    __ shrptr(count, 1); // count => qword_count

    // Copy from high to low addresses.  Use 'to' as scratch.

    // Check for and copy trailing dword
    __ testl(dword_count, 1);
    __ jcc(Assembler::zero, L_copy_bytes);
    __ movl(rax, Address(from, dword_count, Address::times_4, -4));
    __ movl(Address(to, dword_count, Address::times_4, -4), rax);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    __ movq(rax, Address(from, qword_count, Address::times_8, -8));
    __ movq(Address(to, qword_count, Address::times_8, -8), rax);
    __ decrement(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);
  }
  if (is_oop) {
    __ jmp(L_exit);
  }
  restore_arg_regs_using_thread();
  INC_COUNTER_NP(SharedRuntime::_jint_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
  __ xorptr(rax, rax); // return 0
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !is_oop && !aligned, true);
    // Copy in multi-bytes chunks
    copy_bytes_backward(from, to, qword_count, rax, r10, L_copy_bytes, L_copy_8_bytes, decorators, is_oop ? T_OBJECT : T_INT);
  }

__ BIND(L_exit);
  bs->arraycopy_epilogue(_masm, decorators, type, from, to, dword_count);
  restore_arg_regs_using_thread();
  INC_COUNTER_NP(SharedRuntime::_jint_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
  __ xorptr(rax, rax); // return 0
  __ vzeroupper();
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}


// Arguments:
//   aligned - true => Input and output aligned on a HeapWord boundary == 8 bytes
//             ignored
//   is_oop  - true => oop array, so generate store check code
//   name    - stub name string
//
// Inputs:
//   c_rarg0   - source array address
//   c_rarg1   - destination array address
//   c_rarg2   - element count, treated as ssize_t, can be zero
//
 // Side Effects:
//   disjoint_oop_copy_entry or disjoint_long_copy_entry is set to the
//   no-overlap entry point used by generate_conjoint_long_oop_copy().
//
address StubGenerator::generate_disjoint_long_oop_copy(bool aligned, bool is_oop, address *entry,
                                                       const char *name, bool dest_uninitialized) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
#if COMPILER2_OR_JVMCI
  if ((!is_oop || bs->supports_avx3_masked_arraycopy()) && VM_Version::supports_avx512vlbw() && VM_Version::supports_bmi2() && MaxVectorSize >= 32) {
     return generate_disjoint_copy_avx3_masked(entry, "jlong_disjoint_arraycopy_avx3", 3,
                                               aligned, is_oop, dest_uninitialized);
  }
#endif
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  Label L_copy_bytes, L_copy_8_bytes, L_exit;
  const Register from        = rdi;  // source array address
  const Register to          = rsi;  // destination array address
  const Register qword_count = rdx;  // elements count
  const Register end_from    = from; // source array end address
  const Register end_to      = rcx;  // destination array end address
  const Register saved_count = r11;
  // End pointers are inclusive, and if count is not zero they point
  // to the last unit copied:  end_to[0] := end_from[0]

  __ enter(); // required for proper stackwalking of RuntimeStub frame
  // Save no-overlap entry point for generate_conjoint_long_oop_copy()
  assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

  if (entry != nullptr) {
    *entry = __ pc();
    // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
    BLOCK_COMMENT("Entry:");
  }

  setup_arg_regs_using_thread(); // from => rdi, to => rsi, count => rdx
                                   // r9 is used to save r15_thread
  // 'from', 'to' and 'qword_count' are now valid

  DecoratorSet decorators = IN_HEAP | IS_ARRAY | ARRAYCOPY_DISJOINT;
  if (dest_uninitialized) {
    decorators |= IS_DEST_UNINITIALIZED;
  }
  if (aligned) {
    decorators |= ARRAYCOPY_ALIGNED;
  }

  BasicType type = is_oop ? T_OBJECT : T_LONG;
  bs->arraycopy_prologue(_masm, decorators, type, from, to, qword_count);
  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !is_oop && !aligned, true);

    // Copy from low to high addresses.  Use 'to' as scratch.
    __ lea(end_from, Address(from, qword_count, Address::times_8, -8));
    __ lea(end_to,   Address(to,   qword_count, Address::times_8, -8));
    __ negptr(qword_count);
    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    bs->copy_load_at(_masm, decorators, type, 8,
                     rax, Address(end_from, qword_count, Address::times_8, 8),
                     r10);
    bs->copy_store_at(_masm, decorators, type, 8,
                      Address(end_to, qword_count, Address::times_8, 8), rax,
                      r10);
    __ increment(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);
  }
  if (is_oop) {
    __ jmp(L_exit);
  } else {
    restore_arg_regs_using_thread();
    INC_COUNTER_NP(SharedRuntime::_jlong_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ vzeroupper();
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);
  }

  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !is_oop && !aligned, true);
    // Copy in multi-bytes chunks
    copy_bytes_forward(end_from, end_to, qword_count, rax, r10, L_copy_bytes, L_copy_8_bytes, decorators, is_oop ? T_OBJECT : T_LONG);
  }

  __ BIND(L_exit);
  bs->arraycopy_epilogue(_masm, decorators, type, from, to, qword_count);
  restore_arg_regs_using_thread();
  INC_COUNTER_NP(is_oop ? SharedRuntime::_oop_array_copy_ctr :
                          SharedRuntime::_jlong_array_copy_ctr,
                 rscratch1); // Update counter after rscratch1 is free
  __ vzeroupper();
  __ xorptr(rax, rax); // return 0
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}


// Arguments:
//   aligned - true => Input and output aligned on a HeapWord boundary == 8 bytes
//             ignored
//   is_oop  - true => oop array, so generate store check code
//   name    - stub name string
//
// Inputs:
//   c_rarg0   - source array address
//   c_rarg1   - destination array address
//   c_rarg2   - element count, treated as ssize_t, can be zero
//
address StubGenerator::generate_conjoint_long_oop_copy(bool aligned, bool is_oop, address nooverlap_target,
                                                       address *entry, const char *name,
                                                       bool dest_uninitialized) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
#if COMPILER2_OR_JVMCI
  if ((!is_oop || bs->supports_avx3_masked_arraycopy()) && VM_Version::supports_avx512vlbw() && VM_Version::supports_bmi2() && MaxVectorSize  >= 32) {
     return generate_conjoint_copy_avx3_masked(entry, "jlong_conjoint_arraycopy_avx3", 3,
                                               nooverlap_target, aligned, is_oop, dest_uninitialized);
  }
#endif
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  Label L_copy_bytes, L_copy_8_bytes, L_exit;
  const Register from        = rdi;  // source array address
  const Register to          = rsi;  // destination array address
  const Register qword_count = rdx;  // elements count
  const Register saved_count = rcx;

  __ enter(); // required for proper stackwalking of RuntimeStub frame
  assert_clean_int(c_rarg2, rax);    // Make sure 'count' is clean int.

  if (entry != nullptr) {
    *entry = __ pc();
    // caller can pass a 64-bit byte count here (from Unsafe.copyMemory)
    BLOCK_COMMENT("Entry:");
  }

  array_overlap_test(nooverlap_target, Address::times_8);
  setup_arg_regs_using_thread(); // from => rdi, to => rsi, count => rdx
                                 // r9 is used to save r15_thread
  // 'from', 'to' and 'qword_count' are now valid

  DecoratorSet decorators = IN_HEAP | IS_ARRAY;
  if (dest_uninitialized) {
    decorators |= IS_DEST_UNINITIALIZED;
  }
  if (aligned) {
    decorators |= ARRAYCOPY_ALIGNED;
  }

  BasicType type = is_oop ? T_OBJECT : T_LONG;
  bs->arraycopy_prologue(_masm, decorators, type, from, to, qword_count);
  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !is_oop && !aligned, true);

    __ jmp(L_copy_bytes);

    // Copy trailing qwords
  __ BIND(L_copy_8_bytes);
    bs->copy_load_at(_masm, decorators, type, 8,
                     rax, Address(from, qword_count, Address::times_8, -8),
                     r10);
    bs->copy_store_at(_masm, decorators, type, 8,
                      Address(to, qword_count, Address::times_8, -8), rax,
                      r10);
    __ decrement(qword_count);
    __ jcc(Assembler::notZero, L_copy_8_bytes);
  }
  if (is_oop) {
    __ jmp(L_exit);
  } else {
    restore_arg_regs_using_thread();
    INC_COUNTER_NP(SharedRuntime::_jlong_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
    __ xorptr(rax, rax); // return 0
    __ vzeroupper();
    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);
  }
  {
    // UnsafeCopyMemory page error: continue after ucm
    UnsafeCopyMemoryMark ucmm(this, !is_oop && !aligned, true);

    // Copy in multi-bytes chunks
    copy_bytes_backward(from, to, qword_count, rax, r10, L_copy_bytes, L_copy_8_bytes, decorators, is_oop ? T_OBJECT : T_LONG);
  }
  __ BIND(L_exit);
  bs->arraycopy_epilogue(_masm, decorators, type, from, to, qword_count);
  restore_arg_regs_using_thread();
  INC_COUNTER_NP(is_oop ? SharedRuntime::_oop_array_copy_ctr :
                          SharedRuntime::_jlong_array_copy_ctr,
                 rscratch1); // Update counter after rscratch1 is free
  __ vzeroupper();
  __ xorptr(rax, rax); // return 0
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}


// Helper for generating a dynamic type check.
// Smashes no registers.
void StubGenerator::generate_type_check(Register sub_klass,
                                        Register super_check_offset,
                                        Register super_klass,
                                        Label& L_success) {
  assert_different_registers(sub_klass, super_check_offset, super_klass);

  BLOCK_COMMENT("type_check:");

  Label L_miss;

  __ check_klass_subtype_fast_path(sub_klass, super_klass, noreg,        &L_success, &L_miss, nullptr,
                                   super_check_offset);
  __ check_klass_subtype_slow_path(sub_klass, super_klass, noreg, noreg, &L_success, nullptr);

  // Fall through on failure!
  __ BIND(L_miss);
}

//
//  Generate checkcasting array copy stub
//
//  Input:
//    c_rarg0   - source array address
//    c_rarg1   - destination array address
//    c_rarg2   - element count, treated as ssize_t, can be zero
//    c_rarg3   - size_t ckoff (super_check_offset)
// not Win64
//    c_rarg4   - oop ckval (super_klass)
// Win64
//    rsp+40    - oop ckval (super_klass)
//
//  Output:
//    rax ==  0  -  success
//    rax == -1^K - failure, where K is partial transfer count
//
address StubGenerator::generate_checkcast_copy(const char *name, address *entry, bool dest_uninitialized) {

  Label L_load_element, L_store_element, L_do_card_marks, L_done;

  // Input registers (after setup_arg_regs)
  const Register from        = rdi;   // source array address
  const Register to          = rsi;   // destination array address
  const Register length      = rdx;   // elements count
  const Register ckoff       = rcx;   // super_check_offset
  const Register ckval       = r8;    // super_klass

  // Registers used as temps (r13, r14 are save-on-entry)
  const Register end_from    = from;  // source array end address
  const Register end_to      = r13;   // destination array end address
  const Register count       = rdx;   // -(count_remaining)
  const Register r14_length  = r14;   // saved copy of length
  // End pointers are inclusive, and if length is not zero they point
  // to the last unit copied:  end_to[0] := end_from[0]

  const Register rax_oop    = rax;    // actual oop copied
  const Register r11_klass  = r11;    // oop._klass

  //---------------------------------------------------------------
  // Assembler stub will be used for this call to arraycopy
  // if the two arrays are subtypes of Object[] but the
  // destination array type is not equal to or a supertype
  // of the source type.  Each element must be separately
  // checked.

  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef ASSERT
  // caller guarantees that the arrays really are different
  // otherwise, we would have to make conjoint checks
  { Label L;
    array_overlap_test(L, TIMES_OOP);
    __ stop("checkcast_copy within a single array");
    __ bind(L);
  }
#endif //ASSERT

  setup_arg_regs_using_thread(4); // from => rdi, to => rsi, length => rdx
                                  // ckoff => rcx, ckval => r8
                                  // r9 is used to save r15_thread
#ifdef _WIN64
  // last argument (#4) is on stack on Win64
  __ movptr(ckval, Address(rsp, 6 * wordSize));
#endif

  // Caller of this entry point must set up the argument registers.
  if (entry != nullptr) {
    *entry = __ pc();
    BLOCK_COMMENT("Entry:");
  }

  // allocate spill slots for r13, r14
  enum {
    saved_r13_offset,
    saved_r14_offset,
    saved_r10_offset,
    saved_rbp_offset
  };
  __ subptr(rsp, saved_rbp_offset * wordSize);
  __ movptr(Address(rsp, saved_r13_offset * wordSize), r13);
  __ movptr(Address(rsp, saved_r14_offset * wordSize), r14);
  __ movptr(Address(rsp, saved_r10_offset * wordSize), r10);

#ifdef ASSERT
    Label L2;
    __ get_thread(r14);
    __ cmpptr(r15_thread, r14);
    __ jcc(Assembler::equal, L2);
    __ stop("StubRoutines::call_stub: r15_thread is modified by call");
    __ bind(L2);
#endif // ASSERT

  // check that int operands are properly extended to size_t
  assert_clean_int(length, rax);
  assert_clean_int(ckoff, rax);

#ifdef ASSERT
  BLOCK_COMMENT("assert consistent ckoff/ckval");
  // The ckoff and ckval must be mutually consistent,
  // even though caller generates both.
  { Label L;
    int sco_offset = in_bytes(Klass::super_check_offset_offset());
    __ cmpl(ckoff, Address(ckval, sco_offset));
    __ jcc(Assembler::equal, L);
    __ stop("super_check_offset inconsistent");
    __ bind(L);
  }
#endif //ASSERT

  // Loop-invariant addresses.  They are exclusive end pointers.
  Address end_from_addr(from, length, TIMES_OOP, 0);
  Address   end_to_addr(to,   length, TIMES_OOP, 0);
  // Loop-variant addresses.  They assume post-incremented count < 0.
  Address from_element_addr(end_from, count, TIMES_OOP, 0);
  Address   to_element_addr(end_to,   count, TIMES_OOP, 0);

  DecoratorSet decorators = IN_HEAP | IS_ARRAY | ARRAYCOPY_CHECKCAST | ARRAYCOPY_DISJOINT;
  if (dest_uninitialized) {
    decorators |= IS_DEST_UNINITIALIZED;
  }

  BasicType type = T_OBJECT;
  size_t element_size = UseCompressedOops ? 4 : 8;

  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->arraycopy_prologue(_masm, decorators, type, from, to, count);

  // Copy from low to high addresses, indexed from the end of each array.
  __ lea(end_from, end_from_addr);
  __ lea(end_to,   end_to_addr);
  __ movptr(r14_length, length);        // save a copy of the length
  assert(length == count, "");          // else fix next line:
  __ negptr(count);                     // negate and test the length
  __ jcc(Assembler::notZero, L_load_element);

  // Empty array:  Nothing to do.
  __ xorptr(rax, rax);                  // return 0 on (trivial) success
  __ jmp(L_done);

  // ======== begin loop ========
  // (Loop is rotated; its entry is L_load_element.)
  // Loop control:
  //   for (count = -count; count != 0; count++)
  // Base pointers src, dst are biased by 8*(count-1),to last element.
  __ align(OptoLoopAlignment);

  __ BIND(L_store_element);
  bs->copy_store_at(_masm,
                    decorators,
                    type,
                    element_size,
                    to_element_addr,
                    rax_oop,
                    r10);
  __ increment(count);               // increment the count toward zero
  __ jcc(Assembler::zero, L_do_card_marks);

  // ======== loop entry is here ========
  __ BIND(L_load_element);
  bs->copy_load_at(_masm,
                   decorators,
                   type,
                   element_size,
                   rax_oop,
                   from_element_addr,
                   r10);
  __ testptr(rax_oop, rax_oop);
  __ jcc(Assembler::zero, L_store_element);

  __ load_klass(r11_klass, rax_oop, rscratch1);// query the object klass
  generate_type_check(r11_klass, ckoff, ckval, L_store_element);
  // ======== end loop ========

  // It was a real error; we must depend on the caller to finish the job.
  // Register rdx = -1 * number of *remaining* oops, r14 = *total* oops.
  // Emit GC store barriers for the oops we have copied (r14 + rdx),
  // and report their number to the caller.
  assert_different_registers(rax, r14_length, count, to, end_to, rcx, rscratch1);
  Label L_post_barrier;
  __ addptr(r14_length, count);     // K = (original - remaining) oops
  __ movptr(rax, r14_length);       // save the value
  __ notptr(rax);                   // report (-1^K) to caller (does not affect flags)
  __ jccb(Assembler::notZero, L_post_barrier);
  __ jmp(L_done); // K == 0, nothing was copied, skip post barrier

  // Come here on success only.
  __ BIND(L_do_card_marks);
  __ xorptr(rax, rax);              // return 0 on success

  __ BIND(L_post_barrier);
  bs->arraycopy_epilogue(_masm, decorators, type, from, to, r14_length);

  // Common exit point (success or failure).
  __ BIND(L_done);
  __ movptr(r13, Address(rsp, saved_r13_offset * wordSize));
  __ movptr(r14, Address(rsp, saved_r14_offset * wordSize));
  __ movptr(r10, Address(rsp, saved_r10_offset * wordSize));
  restore_arg_regs_using_thread();
  INC_COUNTER_NP(SharedRuntime::_checkcast_array_copy_ctr, rscratch1); // Update counter after rscratch1 is free
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}


//  Generate 'unsafe' array copy stub
//  Though just as safe as the other stubs, it takes an unscaled
//  size_t argument instead of an element count.
//
//  Input:
//    c_rarg0   - source array address
//    c_rarg1   - destination array address
//    c_rarg2   - byte count, treated as ssize_t, can be zero
//
// Examines the alignment of the operands and dispatches
// to a long, int, short, or byte copy loop.
//
address StubGenerator::generate_unsafe_copy(const char *name,
                                            address byte_copy_entry, address short_copy_entry,
                                            address int_copy_entry, address long_copy_entry) {

  Label L_long_aligned, L_int_aligned, L_short_aligned;

  // Input registers (before setup_arg_regs)
  const Register from        = c_rarg0;  // source array address
  const Register to          = c_rarg1;  // destination array address
  const Register size        = c_rarg2;  // byte count (size_t)

  // Register used as a temp
  const Register bits        = rax;      // test copy of low bits

  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", name);
  address start = __ pc();

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  // bump this on entry, not on exit:
  INC_COUNTER_NP(SharedRuntime::_unsafe_array_copy_ctr, rscratch1);

  __ mov(bits, from);
  __ orptr(bits, to);
  __ orptr(bits, size);

  __ testb(bits, BytesPerLong-1);
  __ jccb(Assembler::zero, L_long_aligned);

  __ testb(bits, BytesPerInt-1);
  __ jccb(Assembler::zero, L_int_aligned);

  __ testb(bits, BytesPerShort-1);
  __ jump_cc(Assembler::notZero, RuntimeAddress(byte_copy_entry));

  __ BIND(L_short_aligned);
  __ shrptr(size, LogBytesPerShort); // size => short_count
  __ jump(RuntimeAddress(short_copy_entry));

  __ BIND(L_int_aligned);
  __ shrptr(size, LogBytesPerInt); // size => int_count
  __ jump(RuntimeAddress(int_copy_entry));

  __ BIND(L_long_aligned);
  __ shrptr(size, LogBytesPerLong); // size => qword_count
  __ jump(RuntimeAddress(long_copy_entry));

  return start;
}


// Perform range checks on the proposed arraycopy.
// Kills temp, but nothing else.
// Also, clean the sign bits of src_pos and dst_pos.
void StubGenerator::arraycopy_range_checks(Register src,     // source array oop (c_rarg0)
                                           Register src_pos, // source position (c_rarg1)
                                           Register dst,     // destination array oo (c_rarg2)
                                           Register dst_pos, // destination position (c_rarg3)
                                           Register length,
                                           Register temp,
                                           Label& L_failed) {
  BLOCK_COMMENT("arraycopy_range_checks:");

  //  if (src_pos + length > arrayOop(src)->length())  FAIL;
  __ movl(temp, length);
  __ addl(temp, src_pos);             // src_pos + length
  __ cmpl(temp, Address(src, arrayOopDesc::length_offset_in_bytes()));
  __ jcc(Assembler::above, L_failed);

  //  if (dst_pos + length > arrayOop(dst)->length())  FAIL;
  __ movl(temp, length);
  __ addl(temp, dst_pos);             // dst_pos + length
  __ cmpl(temp, Address(dst, arrayOopDesc::length_offset_in_bytes()));
  __ jcc(Assembler::above, L_failed);

  // Have to clean up high 32-bits of 'src_pos' and 'dst_pos'.
  // Move with sign extension can be used since they are positive.
  __ movslq(src_pos, src_pos);
  __ movslq(dst_pos, dst_pos);

  BLOCK_COMMENT("arraycopy_range_checks done");
}


//  Generate generic array copy stubs
//
//  Input:
//    c_rarg0    -  src oop
//    c_rarg1    -  src_pos (32-bits)
//    c_rarg2    -  dst oop
//    c_rarg3    -  dst_pos (32-bits)
// not Win64
//    c_rarg4    -  element count (32-bits)
// Win64
//    rsp+40     -  element count (32-bits)
//
//  Output:
//    rax ==  0  -  success
//    rax == -1^K - failure, where K is partial transfer count
//
address StubGenerator::generate_generic_copy(const char *name,
                                             address byte_copy_entry, address short_copy_entry,
                                             address int_copy_entry, address oop_copy_entry,
                                             address long_copy_entry, address checkcast_copy_entry) {

  Label L_failed, L_failed_0, L_objArray;
  Label L_copy_shorts, L_copy_ints, L_copy_longs;

  // Input registers
  const Register src        = c_rarg0;  // source array oop
  const Register src_pos    = c_rarg1;  // source position
  const Register dst        = c_rarg2;  // destination array oop
  const Register dst_pos    = c_rarg3;  // destination position
#ifndef _WIN64
  const Register length     = c_rarg4;
  const Register rklass_tmp = r9;  // load_klass
#else
  const Address  length(rsp, 7 * wordSize);  // elements count is on stack on Win64
  const Register rklass_tmp = rdi;  // load_klass
#endif

  { int modulus = CodeEntryAlignment;
    int target  = modulus - 5; // 5 = sizeof jmp(L_failed)
    int advance = target - (__ offset() % modulus);
    if (advance < 0)  advance += modulus;
    if (advance > 0)  __ nop(advance);
  }
  StubCodeMark mark(this, "StubRoutines", name);

  // Short-hop target to L_failed.  Makes for denser prologue code.
  __ BIND(L_failed_0);
  __ jmp(L_failed);
  assert(__ offset() % CodeEntryAlignment == 0, "no further alignment needed");

  __ align(CodeEntryAlignment);
  address start = __ pc();

  __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
  __ push(rklass_tmp); // rdi is callee-save on Windows
#endif

  // bump this on entry, not on exit:
  INC_COUNTER_NP(SharedRuntime::_generic_array_copy_ctr, rscratch1);

  //-----------------------------------------------------------------------
  // Assembler stub will be used for this call to arraycopy
  // if the following conditions are met:
  //
  // (1) src and dst must not be null.
  // (2) src_pos must not be negative.
  // (3) dst_pos must not be negative.
  // (4) length  must not be negative.
  // (5) src klass and dst klass should be the same and not null.
  // (6) src and dst should be arrays.
  // (7) src_pos + length must not exceed length of src.
  // (8) dst_pos + length must not exceed length of dst.
  //

  //  if (src == nullptr) return -1;
  __ testptr(src, src);         // src oop
  size_t j1off = __ offset();
  __ jccb(Assembler::zero, L_failed_0);

  //  if (src_pos < 0) return -1;
  __ testl(src_pos, src_pos); // src_pos (32-bits)
  __ jccb(Assembler::negative, L_failed_0);

  //  if (dst == nullptr) return -1;
  __ testptr(dst, dst);         // dst oop
  __ jccb(Assembler::zero, L_failed_0);

  //  if (dst_pos < 0) return -1;
  __ testl(dst_pos, dst_pos); // dst_pos (32-bits)
  size_t j4off = __ offset();
  __ jccb(Assembler::negative, L_failed_0);

  // The first four tests are very dense code,
  // but not quite dense enough to put four
  // jumps in a 16-byte instruction fetch buffer.
  // That's good, because some branch predicters
  // do not like jumps so close together.
  // Make sure of this.
  guarantee(((j1off ^ j4off) & ~15) != 0, "I$ line of 1st & 4th jumps");

  // registers used as temp
  const Register r11_length    = r11; // elements count to copy
  const Register r10_src_klass = r10; // array klass

  //  if (length < 0) return -1;
  __ movl(r11_length, length);        // length (elements count, 32-bits value)
  __ testl(r11_length, r11_length);
  __ jccb(Assembler::negative, L_failed_0);

  __ load_klass(r10_src_klass, src, rklass_tmp);
#ifdef ASSERT
  //  assert(src->klass() != nullptr);
  {
    BLOCK_COMMENT("assert klasses not null {");
    Label L1, L2;
    __ testptr(r10_src_klass, r10_src_klass);
    __ jcc(Assembler::notZero, L2);   // it is broken if klass is null
    __ bind(L1);
    __ stop("broken null klass");
    __ bind(L2);
    __ load_klass(rax, dst, rklass_tmp);
    __ cmpq(rax, 0);
    __ jcc(Assembler::equal, L1);     // this would be broken also
    BLOCK_COMMENT("} assert klasses not null done");
  }
#endif

  // Load layout helper (32-bits)
  //
  //  |array_tag|     | header_size | element_type |     |log2_element_size|
  // 32        30    24            16              8     2                 0
  //
  //   array_tag: typeArray = 0x3, objArray = 0x2, non-array = 0x0
  //

  const int lh_offset = in_bytes(Klass::layout_helper_offset());

  // Handle objArrays completely differently...
  const jint objArray_lh = Klass::array_layout_helper(T_OBJECT);
  __ cmpl(Address(r10_src_klass, lh_offset), objArray_lh);
  __ jcc(Assembler::equal, L_objArray);

  //  if (src->klass() != dst->klass()) return -1;
  __ load_klass(rax, dst, rklass_tmp);
  __ cmpq(r10_src_klass, rax);
  __ jcc(Assembler::notEqual, L_failed);

  const Register rax_lh = rax;  // layout helper
  __ movl(rax_lh, Address(r10_src_klass, lh_offset));

  //  if (!src->is_Array()) return -1;
  __ cmpl(rax_lh, Klass::_lh_neutral_value);
  __ jcc(Assembler::greaterEqual, L_failed);

  // At this point, it is known to be a typeArray (array_tag 0x3).
#ifdef ASSERT
  {
    BLOCK_COMMENT("assert primitive array {");
    Label L;
    __ cmpl(rax_lh, (Klass::_lh_array_tag_type_value << Klass::_lh_array_tag_shift));
    __ jcc(Assembler::greaterEqual, L);
    __ stop("must be a primitive array");
    __ bind(L);
    BLOCK_COMMENT("} assert primitive array done");
  }
#endif

  arraycopy_range_checks(src, src_pos, dst, dst_pos, r11_length,
                         r10, L_failed);

  // TypeArrayKlass
  //
  // src_addr = (src + array_header_in_bytes()) + (src_pos << log2elemsize);
  // dst_addr = (dst + array_header_in_bytes()) + (dst_pos << log2elemsize);
  //

  const Register r10_offset = r10;    // array offset
  const Register rax_elsize = rax_lh; // element size

  __ movl(r10_offset, rax_lh);
  __ shrl(r10_offset, Klass::_lh_header_size_shift);
  __ andptr(r10_offset, Klass::_lh_header_size_mask);   // array_offset
  __ addptr(src, r10_offset);           // src array offset
  __ addptr(dst, r10_offset);           // dst array offset
  BLOCK_COMMENT("choose copy loop based on element size");
  __ andl(rax_lh, Klass::_lh_log2_element_size_mask); // rax_lh -> rax_elsize

#ifdef _WIN64
  __ pop(rklass_tmp); // Restore callee-save rdi
#endif

  // next registers should be set before the jump to corresponding stub
  const Register from     = c_rarg0;  // source array address
  const Register to       = c_rarg1;  // destination array address
  const Register count    = c_rarg2;  // elements count

  // 'from', 'to', 'count' registers should be set in such order
  // since they are the same as 'src', 'src_pos', 'dst'.

  __ cmpl(rax_elsize, 0);
  __ jccb(Assembler::notEqual, L_copy_shorts);
  __ lea(from, Address(src, src_pos, Address::times_1, 0));// src_addr
  __ lea(to,   Address(dst, dst_pos, Address::times_1, 0));// dst_addr
  __ movl2ptr(count, r11_length); // length
  __ jump(RuntimeAddress(byte_copy_entry));

__ BIND(L_copy_shorts);
  __ cmpl(rax_elsize, LogBytesPerShort);
  __ jccb(Assembler::notEqual, L_copy_ints);
  __ lea(from, Address(src, src_pos, Address::times_2, 0));// src_addr
  __ lea(to,   Address(dst, dst_pos, Address::times_2, 0));// dst_addr
  __ movl2ptr(count, r11_length); // length
  __ jump(RuntimeAddress(short_copy_entry));

__ BIND(L_copy_ints);
  __ cmpl(rax_elsize, LogBytesPerInt);
  __ jccb(Assembler::notEqual, L_copy_longs);
  __ lea(from, Address(src, src_pos, Address::times_4, 0));// src_addr
  __ lea(to,   Address(dst, dst_pos, Address::times_4, 0));// dst_addr
  __ movl2ptr(count, r11_length); // length
  __ jump(RuntimeAddress(int_copy_entry));

__ BIND(L_copy_longs);
#ifdef ASSERT
  {
    BLOCK_COMMENT("assert long copy {");
    Label L;
    __ cmpl(rax_elsize, LogBytesPerLong);
    __ jcc(Assembler::equal, L);
    __ stop("must be long copy, but elsize is wrong");
    __ bind(L);
    BLOCK_COMMENT("} assert long copy done");
  }
#endif
  __ lea(from, Address(src, src_pos, Address::times_8, 0));// src_addr
  __ lea(to,   Address(dst, dst_pos, Address::times_8, 0));// dst_addr
  __ movl2ptr(count, r11_length); // length
  __ jump(RuntimeAddress(long_copy_entry));

  // ObjArrayKlass
__ BIND(L_objArray);
  // live at this point:  r10_src_klass, r11_length, src[_pos], dst[_pos]

  Label L_plain_copy, L_checkcast_copy;
  //  test array classes for subtyping
  __ load_klass(rax, dst, rklass_tmp);
  __ cmpq(r10_src_klass, rax); // usual case is exact equality
  __ jcc(Assembler::notEqual, L_checkcast_copy);

  // Identically typed arrays can be copied without element-wise checks.
  arraycopy_range_checks(src, src_pos, dst, dst_pos, r11_length,
                         r10, L_failed);

  __ lea(from, Address(src, src_pos, TIMES_OOP,
               arrayOopDesc::base_offset_in_bytes(T_OBJECT))); // src_addr
  __ lea(to,   Address(dst, dst_pos, TIMES_OOP,
               arrayOopDesc::base_offset_in_bytes(T_OBJECT))); // dst_addr
  __ movl2ptr(count, r11_length); // length
__ BIND(L_plain_copy);
#ifdef _WIN64
  __ pop(rklass_tmp); // Restore callee-save rdi
#endif
  __ jump(RuntimeAddress(oop_copy_entry));

__ BIND(L_checkcast_copy);
  // live at this point:  r10_src_klass, r11_length, rax (dst_klass)
  {
    // Before looking at dst.length, make sure dst is also an objArray.
    __ cmpl(Address(rax, lh_offset), objArray_lh);
    __ jcc(Assembler::notEqual, L_failed);

    // It is safe to examine both src.length and dst.length.
    arraycopy_range_checks(src, src_pos, dst, dst_pos, r11_length,
                           rax, L_failed);

    const Register r11_dst_klass = r11;
    __ load_klass(r11_dst_klass, dst, rklass_tmp); // reload

    // Marshal the base address arguments now, freeing registers.
    __ lea(from, Address(src, src_pos, TIMES_OOP,
                 arrayOopDesc::base_offset_in_bytes(T_OBJECT)));
    __ lea(to,   Address(dst, dst_pos, TIMES_OOP,
                 arrayOopDesc::base_offset_in_bytes(T_OBJECT)));
    __ movl(count, length);           // length (reloaded)
    Register sco_temp = c_rarg3;      // this register is free now
    assert_different_registers(from, to, count, sco_temp,
                               r11_dst_klass, r10_src_klass);
    assert_clean_int(count, sco_temp);

    // Generate the type check.
    const int sco_offset = in_bytes(Klass::super_check_offset_offset());
    __ movl(sco_temp, Address(r11_dst_klass, sco_offset));
    assert_clean_int(sco_temp, rax);
    generate_type_check(r10_src_klass, sco_temp, r11_dst_klass, L_plain_copy);

    // Fetch destination element klass from the ObjArrayKlass header.
    int ek_offset = in_bytes(ObjArrayKlass::element_klass_offset());
    __ movptr(r11_dst_klass, Address(r11_dst_klass, ek_offset));
    __ movl(  sco_temp,      Address(r11_dst_klass, sco_offset));
    assert_clean_int(sco_temp, rax);

#ifdef _WIN64
    __ pop(rklass_tmp); // Restore callee-save rdi
#endif

    // the checkcast_copy loop needs two extra arguments:
    assert(c_rarg3 == sco_temp, "#3 already in place");
    // Set up arguments for checkcast_copy_entry.
    setup_arg_regs_using_thread(4);
    __ movptr(r8, r11_dst_klass);  // dst.klass.element_klass, r8 is c_rarg4 on Linux/Solaris
    __ jump(RuntimeAddress(checkcast_copy_entry));
  }

__ BIND(L_failed);
#ifdef _WIN64
  __ pop(rklass_tmp); // Restore callee-save rdi
#endif
  __ xorptr(rax, rax);
  __ notptr(rax); // return -1
  __ leave();   // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

#undef __
