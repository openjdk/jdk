/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "opto/compile.hpp"
#include "opto/intrinsicnode.hpp"
#include "opto/matcher.hpp"
#include "opto/output.hpp"
#include "opto/subnode.hpp"
#include "runtime/stubRoutines.hpp"

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#define STOP(error) stop(error)
#else
#define BLOCK_COMMENT(str) block_comment(str)
#define STOP(error) block_comment(error); stop(error)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

typedef void (MacroAssembler::* chr_insn)(Register Rt, const Address &adr);

void C2_MacroAssembler::fast_lock(Register objectReg, Register boxReg, Register tmpReg,
                                  Register tmp2Reg, Register tmp3Reg) {
  Register oop = objectReg;
  Register box = boxReg;
  Register disp_hdr = tmpReg;
  Register tmp = tmp2Reg;
  Label cont;
  Label object_has_monitor;
  Label count, no_count;

  assert_different_registers(oop, box, tmp, disp_hdr);

  // Load markWord from object into displaced_header.
  ldr(disp_hdr, Address(oop, oopDesc::mark_offset_in_bytes()));

  if (DiagnoseSyncOnValueBasedClasses != 0) {
    load_klass(tmp, oop);
    ldrw(tmp, Address(tmp, Klass::access_flags_offset()));
    tstw(tmp, JVM_ACC_IS_VALUE_BASED_CLASS);
    br(Assembler::NE, cont);
  }

  // Check for existing monitor
  tbnz(disp_hdr, exact_log2(markWord::monitor_value), object_has_monitor);

  if (LockingMode == LM_MONITOR) {
    tst(oop, oop); // Set NE to indicate 'failure' -> take slow-path. We know that oop != 0.
    b(cont);
  } else if (LockingMode == LM_LEGACY) {
    // Set tmp to be (markWord of object | UNLOCK_VALUE).
    orr(tmp, disp_hdr, markWord::unlocked_value);

    // Initialize the box. (Must happen before we update the object mark!)
    str(tmp, Address(box, BasicLock::displaced_header_offset_in_bytes()));

    // Compare object markWord with an unlocked value (tmp) and if
    // equal exchange the stack address of our box with object markWord.
    // On failure disp_hdr contains the possibly locked markWord.
    cmpxchg(oop, tmp, box, Assembler::xword, /*acquire*/ true,
            /*release*/ true, /*weak*/ false, disp_hdr);
    br(Assembler::EQ, cont);

    assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

    // If the compare-and-exchange succeeded, then we found an unlocked
    // object, will have now locked it will continue at label cont

    // Check if the owner is self by comparing the value in the
    // markWord of object (disp_hdr) with the stack pointer.
    mov(rscratch1, sp);
    sub(disp_hdr, disp_hdr, rscratch1);
    mov(tmp, (address) (~(os::vm_page_size()-1) | markWord::lock_mask_in_place));
    // If condition is true we are cont and hence we can store 0 as the
    // displaced header in the box, which indicates that it is a recursive lock.
    ands(tmp/*==0?*/, disp_hdr, tmp);   // Sets flags for result
    str(tmp/*==0, perhaps*/, Address(box, BasicLock::displaced_header_offset_in_bytes()));
    b(cont);
  } else {
    assert(LockingMode == LM_LIGHTWEIGHT, "must be");
    lightweight_lock(oop, disp_hdr, tmp, tmp3Reg, no_count);
    b(count);
  }

  // Handle existing monitor.
  bind(object_has_monitor);

  // The object's monitor m is unlocked iff m->owner == NULL,
  // otherwise m->owner may contain a thread or a stack address.
  //
  // Try to CAS m->owner from NULL to current thread.
  add(tmp, disp_hdr, (in_bytes(ObjectMonitor::owner_offset())-markWord::monitor_value));
  cmpxchg(tmp, zr, rthread, Assembler::xword, /*acquire*/ true,
          /*release*/ true, /*weak*/ false, tmp3Reg); // Sets flags for result

  if (LockingMode != LM_LIGHTWEIGHT) {
    // Store a non-null value into the box to avoid looking like a re-entrant
    // lock. The fast-path monitor unlock code checks for
    // markWord::monitor_value so use markWord::unused_mark which has the
    // relevant bit set, and also matches ObjectSynchronizer::enter.
    mov(tmp, (address)markWord::unused_mark().value());
    str(tmp, Address(box, BasicLock::displaced_header_offset_in_bytes()));
  }
  br(Assembler::EQ, cont); // CAS success means locking succeeded

  cmp(tmp3Reg, rthread);
  br(Assembler::NE, cont); // Check for recursive locking

  // Recursive lock case
  increment(Address(disp_hdr, in_bytes(ObjectMonitor::recursions_offset()) - markWord::monitor_value), 1);
  // flag == EQ still from the cmp above, checking if this is a reentrant lock

  bind(cont);
  // flag == EQ indicates success
  // flag == NE indicates failure
  br(Assembler::NE, no_count);

  bind(count);
  increment(Address(rthread, JavaThread::held_monitor_count_offset()));

  bind(no_count);
}

void C2_MacroAssembler::fast_unlock(Register objectReg, Register boxReg, Register tmpReg,
                                    Register tmp2Reg) {
  Register oop = objectReg;
  Register box = boxReg;
  Register disp_hdr = tmpReg;
  Register tmp = tmp2Reg;
  Label cont;
  Label object_has_monitor;
  Label count, no_count;

  assert_different_registers(oop, box, tmp, disp_hdr);

  if (LockingMode == LM_LEGACY) {
    // Find the lock address and load the displaced header from the stack.
    ldr(disp_hdr, Address(box, BasicLock::displaced_header_offset_in_bytes()));

    // If the displaced header is 0, we have a recursive unlock.
    cmp(disp_hdr, zr);
    br(Assembler::EQ, cont);
  }

  // Handle existing monitor.
  ldr(tmp, Address(oop, oopDesc::mark_offset_in_bytes()));
  tbnz(tmp, exact_log2(markWord::monitor_value), object_has_monitor);

  if (LockingMode == LM_MONITOR) {
    tst(oop, oop); // Set NE to indicate 'failure' -> take slow-path. We know that oop != 0.
    b(cont);
  } else if (LockingMode == LM_LEGACY) {
    // Check if it is still a light weight lock, this is is true if we
    // see the stack address of the basicLock in the markWord of the
    // object.

    cmpxchg(oop, box, disp_hdr, Assembler::xword, /*acquire*/ false,
            /*release*/ true, /*weak*/ false, tmp);
    b(cont);
  } else {
    assert(LockingMode == LM_LIGHTWEIGHT, "must be");
    lightweight_unlock(oop, tmp, box, disp_hdr, no_count);
    b(count);
  }

  assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

  // Handle existing monitor.
  bind(object_has_monitor);
  STATIC_ASSERT(markWord::monitor_value <= INT_MAX);
  add(tmp, tmp, -(int)markWord::monitor_value); // monitor

  if (LockingMode == LM_LIGHTWEIGHT) {
    // If the owner is anonymous, we need to fix it -- in an outline stub.
    Register tmp2 = disp_hdr;
    ldr(tmp2, Address(tmp, ObjectMonitor::owner_offset()));
    // We cannot use tbnz here, the target might be too far away and cannot
    // be encoded.
    tst(tmp2, (uint64_t)ObjectMonitor::ANONYMOUS_OWNER);
    C2HandleAnonOMOwnerStub* stub = new (Compile::current()->comp_arena()) C2HandleAnonOMOwnerStub(tmp, tmp2);
    Compile::current()->output()->add_stub(stub);
    br(Assembler::NE, stub->entry());
    bind(stub->continuation());
  }

  ldr(disp_hdr, Address(tmp, ObjectMonitor::recursions_offset()));

  Label notRecursive;
  cbz(disp_hdr, notRecursive);

  // Recursive lock
  sub(disp_hdr, disp_hdr, 1u);
  str(disp_hdr, Address(tmp, ObjectMonitor::recursions_offset()));
  cmp(disp_hdr, disp_hdr); // Sets flags for result
  b(cont);

  bind(notRecursive);
  ldr(rscratch1, Address(tmp, ObjectMonitor::EntryList_offset()));
  ldr(disp_hdr, Address(tmp, ObjectMonitor::cxq_offset()));
  orr(rscratch1, rscratch1, disp_hdr); // Will be 0 if both are 0.
  cmp(rscratch1, zr); // Sets flags for result
  cbnz(rscratch1, cont);
  // need a release store here
  lea(tmp, Address(tmp, ObjectMonitor::owner_offset()));
  stlr(zr, tmp); // set unowned

  bind(cont);
  // flag == EQ indicates success
  // flag == NE indicates failure
  br(Assembler::NE, no_count);

  bind(count);
  decrement(Address(rthread, JavaThread::held_monitor_count_offset()));

  bind(no_count);
}

// Search for str1 in str2 and return index or -1
// Clobbers: rscratch1, rscratch2, rflags. May also clobber v0-v1, when icnt1==-1.
void C2_MacroAssembler::string_indexof(Register str2, Register str1,
                                       Register cnt2, Register cnt1,
                                       Register tmp1, Register tmp2,
                                       Register tmp3, Register tmp4,
                                       Register tmp5, Register tmp6,
                                       int icnt1, Register result, int ae) {
  // NOTE: tmp5, tmp6 can be zr depending on specific method version
  Label LINEARSEARCH, LINEARSTUB, LINEAR_MEDIUM, DONE, NOMATCH, MATCH;

  Register ch1 = rscratch1;
  Register ch2 = rscratch2;
  Register cnt1tmp = tmp1;
  Register cnt2tmp = tmp2;
  Register cnt1_neg = cnt1;
  Register cnt2_neg = cnt2;
  Register result_tmp = tmp4;

  bool isL = ae == StrIntrinsicNode::LL;

  bool str1_isL = ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UL;
  bool str2_isL = ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::LU;
  int str1_chr_shift = str1_isL ? 0:1;
  int str2_chr_shift = str2_isL ? 0:1;
  int str1_chr_size = str1_isL ? 1:2;
  int str2_chr_size = str2_isL ? 1:2;
  chr_insn str1_load_1chr = str1_isL ? (chr_insn)&MacroAssembler::ldrb :
                                      (chr_insn)&MacroAssembler::ldrh;
  chr_insn str2_load_1chr = str2_isL ? (chr_insn)&MacroAssembler::ldrb :
                                      (chr_insn)&MacroAssembler::ldrh;
  chr_insn load_2chr = isL ? (chr_insn)&MacroAssembler::ldrh : (chr_insn)&MacroAssembler::ldrw;
  chr_insn load_4chr = isL ? (chr_insn)&MacroAssembler::ldrw : (chr_insn)&MacroAssembler::ldr;

  // Note, inline_string_indexOf() generates checks:
  // if (substr.count > string.count) return -1;
  // if (substr.count == 0) return 0;

  // We have two strings, a source string in str2, cnt2 and a pattern string
  // in str1, cnt1. Find the 1st occurrence of pattern in source or return -1.

  // For larger pattern and source we use a simplified Boyer Moore algorithm.
  // With a small pattern and source we use linear scan.

  if (icnt1 == -1) {
    sub(result_tmp, cnt2, cnt1);
    cmp(cnt1, (u1)8);             // Use Linear Scan if cnt1 < 8 || cnt1 >= 256
    br(LT, LINEARSEARCH);
    dup(v0, T16B, cnt1); // done in separate FPU pipeline. Almost no penalty
    subs(zr, cnt1, 256);
    lsr(tmp1, cnt2, 2);
    ccmp(cnt1, tmp1, 0b0000, LT); // Source must be 4 * pattern for BM
    br(GE, LINEARSTUB);
  }

// The Boyer Moore alogorithm is based on the description here:-
//
// http://en.wikipedia.org/wiki/Boyer%E2%80%93Moore_string_search_algorithm
//
// This describes and algorithm with 2 shift rules. The 'Bad Character' rule
// and the 'Good Suffix' rule.
//
// These rules are essentially heuristics for how far we can shift the
// pattern along the search string.
//
// The implementation here uses the 'Bad Character' rule only because of the
// complexity of initialisation for the 'Good Suffix' rule.
//
// This is also known as the Boyer-Moore-Horspool algorithm:-
//
// http://en.wikipedia.org/wiki/Boyer-Moore-Horspool_algorithm
//
// This particular implementation has few java-specific optimizations.
//
// #define ASIZE 256
//
//    int bm(unsigned char *x, int m, unsigned char *y, int n) {
//       int i, j;
//       unsigned c;
//       unsigned char bc[ASIZE];
//
//       /* Preprocessing */
//       for (i = 0; i < ASIZE; ++i)
//          bc[i] = m;
//       for (i = 0; i < m - 1; ) {
//          c = x[i];
//          ++i;
//          // c < 256 for Latin1 string, so, no need for branch
//          #ifdef PATTERN_STRING_IS_LATIN1
//          bc[c] = m - i;
//          #else
//          if (c < ASIZE) bc[c] = m - i;
//          #endif
//       }
//
//       /* Searching */
//       j = 0;
//       while (j <= n - m) {
//          c = y[i+j];
//          if (x[m-1] == c)
//            for (i = m - 2; i >= 0 && x[i] == y[i + j]; --i);
//          if (i < 0) return j;
//          // c < 256 for Latin1 string, so, no need for branch
//          #ifdef SOURCE_STRING_IS_LATIN1
//          // LL case: (c< 256) always true. Remove branch
//          j += bc[y[j+m-1]];
//          #endif
//          #ifndef PATTERN_STRING_IS_UTF
//          // UU case: need if (c<ASIZE) check. Skip 1 character if not.
//          if (c < ASIZE)
//            j += bc[y[j+m-1]];
//          else
//            j += 1
//          #endif
//          #ifdef PATTERN_IS_LATIN1_AND_SOURCE_IS_UTF
//          // UL case: need if (c<ASIZE) check. Skip <pattern length> if not.
//          if (c < ASIZE)
//            j += bc[y[j+m-1]];
//          else
//            j += m
//          #endif
//       }
//    }

  if (icnt1 == -1) {
    Label BCLOOP, BCSKIP, BMLOOPSTR2, BMLOOPSTR1, BMSKIP, BMADV, BMMATCH,
        BMLOOPSTR1_LASTCMP, BMLOOPSTR1_CMP, BMLOOPSTR1_AFTER_LOAD, BM_INIT_LOOP;
    Register cnt1end = tmp2;
    Register str2end = cnt2;
    Register skipch = tmp2;

    // str1 length is >=8, so, we can read at least 1 register for cases when
    // UTF->Latin1 conversion is not needed(8 LL or 4UU) and half register for
    // UL case. We'll re-read last character in inner pre-loop code to have
    // single outer pre-loop load
    const int firstStep = isL ? 7 : 3;

    const int ASIZE = 256;
    const int STORED_BYTES = 32; // amount of bytes stored per instruction
    sub(sp, sp, ASIZE);
    mov(tmp5, ASIZE/STORED_BYTES); // loop iterations
    mov(ch1, sp);
    BIND(BM_INIT_LOOP);
      stpq(v0, v0, Address(post(ch1, STORED_BYTES)));
      subs(tmp5, tmp5, 1);
      br(GT, BM_INIT_LOOP);

      sub(cnt1tmp, cnt1, 1);
      mov(tmp5, str2);
      add(str2end, str2, result_tmp, LSL, str2_chr_shift);
      sub(ch2, cnt1, 1);
      mov(tmp3, str1);
    BIND(BCLOOP);
      (this->*str1_load_1chr)(ch1, Address(post(tmp3, str1_chr_size)));
      if (!str1_isL) {
        subs(zr, ch1, ASIZE);
        br(HS, BCSKIP);
      }
      strb(ch2, Address(sp, ch1));
    BIND(BCSKIP);
      subs(ch2, ch2, 1);
      br(GT, BCLOOP);

      add(tmp6, str1, cnt1, LSL, str1_chr_shift); // address after str1
      if (str1_isL == str2_isL) {
        // load last 8 bytes (8LL/4UU symbols)
        ldr(tmp6, Address(tmp6, -wordSize));
      } else {
        ldrw(tmp6, Address(tmp6, -wordSize/2)); // load last 4 bytes(4 symbols)
        // convert Latin1 to UTF. We'll have to wait until load completed, but
        // it's still faster than per-character loads+checks
        lsr(tmp3, tmp6, BitsPerByte * (wordSize/2 - str1_chr_size)); // str1[N-1]
        ubfx(ch1, tmp6, 8, 8); // str1[N-2]
        ubfx(ch2, tmp6, 16, 8); // str1[N-3]
        andr(tmp6, tmp6, 0xFF); // str1[N-4]
        orr(ch2, ch1, ch2, LSL, 16);
        orr(tmp6, tmp6, tmp3, LSL, 48);
        orr(tmp6, tmp6, ch2, LSL, 16);
      }
    BIND(BMLOOPSTR2);
      (this->*str2_load_1chr)(skipch, Address(str2, cnt1tmp, Address::lsl(str2_chr_shift)));
      sub(cnt1tmp, cnt1tmp, firstStep); // cnt1tmp is positive here, because cnt1 >= 8
      if (str1_isL == str2_isL) {
        // re-init tmp3. It's for free because it's executed in parallel with
        // load above. Alternative is to initialize it before loop, but it'll
        // affect performance on in-order systems with 2 or more ld/st pipelines
        lsr(tmp3, tmp6, BitsPerByte * (wordSize - str1_chr_size));
      }
      if (!isL) { // UU/UL case
        lsl(ch2, cnt1tmp, 1); // offset in bytes
      }
      cmp(tmp3, skipch);
      br(NE, BMSKIP);
      ldr(ch2, Address(str2, isL ? cnt1tmp : ch2));
      mov(ch1, tmp6);
      if (isL) {
        b(BMLOOPSTR1_AFTER_LOAD);
      } else {
        sub(cnt1tmp, cnt1tmp, 1); // no need to branch for UU/UL case. cnt1 >= 8
        b(BMLOOPSTR1_CMP);
      }
    BIND(BMLOOPSTR1);
      (this->*str1_load_1chr)(ch1, Address(str1, cnt1tmp, Address::lsl(str1_chr_shift)));
      (this->*str2_load_1chr)(ch2, Address(str2, cnt1tmp, Address::lsl(str2_chr_shift)));
    BIND(BMLOOPSTR1_AFTER_LOAD);
      subs(cnt1tmp, cnt1tmp, 1);
      br(LT, BMLOOPSTR1_LASTCMP);
    BIND(BMLOOPSTR1_CMP);
      cmp(ch1, ch2);
      br(EQ, BMLOOPSTR1);
    BIND(BMSKIP);
      if (!isL) {
        // if we've met UTF symbol while searching Latin1 pattern, then we can
        // skip cnt1 symbols
        if (str1_isL != str2_isL) {
          mov(result_tmp, cnt1);
        } else {
          mov(result_tmp, 1);
        }
        subs(zr, skipch, ASIZE);
        br(HS, BMADV);
      }
      ldrb(result_tmp, Address(sp, skipch)); // load skip distance
    BIND(BMADV);
      sub(cnt1tmp, cnt1, 1);
      add(str2, str2, result_tmp, LSL, str2_chr_shift);
      cmp(str2, str2end);
      br(LE, BMLOOPSTR2);
      add(sp, sp, ASIZE);
      b(NOMATCH);
    BIND(BMLOOPSTR1_LASTCMP);
      cmp(ch1, ch2);
      br(NE, BMSKIP);
    BIND(BMMATCH);
      sub(result, str2, tmp5);
      if (!str2_isL) lsr(result, result, 1);
      add(sp, sp, ASIZE);
      b(DONE);

    BIND(LINEARSTUB);
    cmp(cnt1, (u1)16); // small patterns still should be handled by simple algorithm
    br(LT, LINEAR_MEDIUM);
    mov(result, zr);
    RuntimeAddress stub = nullptr;
    if (isL) {
      stub = RuntimeAddress(StubRoutines::aarch64::string_indexof_linear_ll());
      assert(stub.target() != nullptr, "string_indexof_linear_ll stub has not been generated");
    } else if (str1_isL) {
      stub = RuntimeAddress(StubRoutines::aarch64::string_indexof_linear_ul());
       assert(stub.target() != nullptr, "string_indexof_linear_ul stub has not been generated");
    } else {
      stub = RuntimeAddress(StubRoutines::aarch64::string_indexof_linear_uu());
      assert(stub.target() != nullptr, "string_indexof_linear_uu stub has not been generated");
    }
    address call = trampoline_call(stub);
    if (call == nullptr) {
      DEBUG_ONLY(reset_labels(LINEARSEARCH, LINEAR_MEDIUM, DONE, NOMATCH, MATCH));
      ciEnv::current()->record_failure("CodeCache is full");
      return;
    }
    b(DONE);
  }

  BIND(LINEARSEARCH);
  {
    Label DO1, DO2, DO3;

    Register str2tmp = tmp2;
    Register first = tmp3;

    if (icnt1 == -1)
    {
        Label DOSHORT, FIRST_LOOP, STR2_NEXT, STR1_LOOP, STR1_NEXT;

        cmp(cnt1, u1(str1_isL == str2_isL ? 4 : 2));
        br(LT, DOSHORT);
      BIND(LINEAR_MEDIUM);
        (this->*str1_load_1chr)(first, Address(str1));
        lea(str1, Address(str1, cnt1, Address::lsl(str1_chr_shift)));
        sub(cnt1_neg, zr, cnt1, LSL, str1_chr_shift);
        lea(str2, Address(str2, result_tmp, Address::lsl(str2_chr_shift)));
        sub(cnt2_neg, zr, result_tmp, LSL, str2_chr_shift);

      BIND(FIRST_LOOP);
        (this->*str2_load_1chr)(ch2, Address(str2, cnt2_neg));
        cmp(first, ch2);
        br(EQ, STR1_LOOP);
      BIND(STR2_NEXT);
        adds(cnt2_neg, cnt2_neg, str2_chr_size);
        br(LE, FIRST_LOOP);
        b(NOMATCH);

      BIND(STR1_LOOP);
        adds(cnt1tmp, cnt1_neg, str1_chr_size);
        add(cnt2tmp, cnt2_neg, str2_chr_size);
        br(GE, MATCH);

      BIND(STR1_NEXT);
        (this->*str1_load_1chr)(ch1, Address(str1, cnt1tmp));
        (this->*str2_load_1chr)(ch2, Address(str2, cnt2tmp));
        cmp(ch1, ch2);
        br(NE, STR2_NEXT);
        adds(cnt1tmp, cnt1tmp, str1_chr_size);
        add(cnt2tmp, cnt2tmp, str2_chr_size);
        br(LT, STR1_NEXT);
        b(MATCH);

      BIND(DOSHORT);
      if (str1_isL == str2_isL) {
        cmp(cnt1, (u1)2);
        br(LT, DO1);
        br(GT, DO3);
      }
    }

    if (icnt1 == 4) {
      Label CH1_LOOP;

        (this->*load_4chr)(ch1, str1);
        sub(result_tmp, cnt2, 4);
        lea(str2, Address(str2, result_tmp, Address::lsl(str2_chr_shift)));
        sub(cnt2_neg, zr, result_tmp, LSL, str2_chr_shift);

      BIND(CH1_LOOP);
        (this->*load_4chr)(ch2, Address(str2, cnt2_neg));
        cmp(ch1, ch2);
        br(EQ, MATCH);
        adds(cnt2_neg, cnt2_neg, str2_chr_size);
        br(LE, CH1_LOOP);
        b(NOMATCH);
      }

    if ((icnt1 == -1 && str1_isL == str2_isL) || icnt1 == 2) {
      Label CH1_LOOP;

      BIND(DO2);
        (this->*load_2chr)(ch1, str1);
        if (icnt1 == 2) {
          sub(result_tmp, cnt2, 2);
        }
        lea(str2, Address(str2, result_tmp, Address::lsl(str2_chr_shift)));
        sub(cnt2_neg, zr, result_tmp, LSL, str2_chr_shift);
      BIND(CH1_LOOP);
        (this->*load_2chr)(ch2, Address(str2, cnt2_neg));
        cmp(ch1, ch2);
        br(EQ, MATCH);
        adds(cnt2_neg, cnt2_neg, str2_chr_size);
        br(LE, CH1_LOOP);
        b(NOMATCH);
    }

    if ((icnt1 == -1 && str1_isL == str2_isL) || icnt1 == 3) {
      Label FIRST_LOOP, STR2_NEXT, STR1_LOOP;

      BIND(DO3);
        (this->*load_2chr)(first, str1);
        (this->*str1_load_1chr)(ch1, Address(str1, 2*str1_chr_size));
        if (icnt1 == 3) {
          sub(result_tmp, cnt2, 3);
        }
        lea(str2, Address(str2, result_tmp, Address::lsl(str2_chr_shift)));
        sub(cnt2_neg, zr, result_tmp, LSL, str2_chr_shift);
      BIND(FIRST_LOOP);
        (this->*load_2chr)(ch2, Address(str2, cnt2_neg));
        cmpw(first, ch2);
        br(EQ, STR1_LOOP);
      BIND(STR2_NEXT);
        adds(cnt2_neg, cnt2_neg, str2_chr_size);
        br(LE, FIRST_LOOP);
        b(NOMATCH);

      BIND(STR1_LOOP);
        add(cnt2tmp, cnt2_neg, 2*str2_chr_size);
        (this->*str2_load_1chr)(ch2, Address(str2, cnt2tmp));
        cmp(ch1, ch2);
        br(NE, STR2_NEXT);
        b(MATCH);
    }

    if (icnt1 == -1 || icnt1 == 1) {
      Label CH1_LOOP, HAS_ZERO, DO1_SHORT, DO1_LOOP;

      BIND(DO1);
        (this->*str1_load_1chr)(ch1, str1);
        cmp(cnt2, (u1)8);
        br(LT, DO1_SHORT);

        sub(result_tmp, cnt2, 8/str2_chr_size);
        sub(cnt2_neg, zr, result_tmp, LSL, str2_chr_shift);
        mov(tmp3, str2_isL ? 0x0101010101010101 : 0x0001000100010001);
        lea(str2, Address(str2, result_tmp, Address::lsl(str2_chr_shift)));

        if (str2_isL) {
          orr(ch1, ch1, ch1, LSL, 8);
        }
        orr(ch1, ch1, ch1, LSL, 16);
        orr(ch1, ch1, ch1, LSL, 32);
      BIND(CH1_LOOP);
        ldr(ch2, Address(str2, cnt2_neg));
        eor(ch2, ch1, ch2);
        sub(tmp1, ch2, tmp3);
        orr(tmp2, ch2, str2_isL ? 0x7f7f7f7f7f7f7f7f : 0x7fff7fff7fff7fff);
        bics(tmp1, tmp1, tmp2);
        br(NE, HAS_ZERO);
        adds(cnt2_neg, cnt2_neg, 8);
        br(LT, CH1_LOOP);

        cmp(cnt2_neg, (u1)8);
        mov(cnt2_neg, 0);
        br(LT, CH1_LOOP);
        b(NOMATCH);

      BIND(HAS_ZERO);
        rev(tmp1, tmp1);
        clz(tmp1, tmp1);
        add(cnt2_neg, cnt2_neg, tmp1, LSR, 3);
        b(MATCH);

      BIND(DO1_SHORT);
        mov(result_tmp, cnt2);
        lea(str2, Address(str2, cnt2, Address::lsl(str2_chr_shift)));
        sub(cnt2_neg, zr, cnt2, LSL, str2_chr_shift);
      BIND(DO1_LOOP);
        (this->*str2_load_1chr)(ch2, Address(str2, cnt2_neg));
        cmpw(ch1, ch2);
        br(EQ, MATCH);
        adds(cnt2_neg, cnt2_neg, str2_chr_size);
        br(LT, DO1_LOOP);
    }
  }
  BIND(NOMATCH);
    mov(result, -1);
    b(DONE);
  BIND(MATCH);
    add(result, result_tmp, cnt2_neg, ASR, str2_chr_shift);
  BIND(DONE);
}

typedef void (MacroAssembler::* chr_insn)(Register Rt, const Address &adr);
typedef void (MacroAssembler::* uxt_insn)(Register Rd, Register Rn);

void C2_MacroAssembler::string_indexof_char(Register str1, Register cnt1,
                                            Register ch, Register result,
                                            Register tmp1, Register tmp2, Register tmp3)
{
  Label CH1_LOOP, HAS_ZERO, DO1_SHORT, DO1_LOOP, MATCH, NOMATCH, DONE;
  Register cnt1_neg = cnt1;
  Register ch1 = rscratch1;
  Register result_tmp = rscratch2;

  cbz(cnt1, NOMATCH);

  cmp(cnt1, (u1)4);
  br(LT, DO1_SHORT);

  orr(ch, ch, ch, LSL, 16);
  orr(ch, ch, ch, LSL, 32);

  sub(cnt1, cnt1, 4);
  mov(result_tmp, cnt1);
  lea(str1, Address(str1, cnt1, Address::uxtw(1)));
  sub(cnt1_neg, zr, cnt1, LSL, 1);

  mov(tmp3, 0x0001000100010001);

  BIND(CH1_LOOP);
    ldr(ch1, Address(str1, cnt1_neg));
    eor(ch1, ch, ch1);
    sub(tmp1, ch1, tmp3);
    orr(tmp2, ch1, 0x7fff7fff7fff7fff);
    bics(tmp1, tmp1, tmp2);
    br(NE, HAS_ZERO);
    adds(cnt1_neg, cnt1_neg, 8);
    br(LT, CH1_LOOP);

    cmp(cnt1_neg, (u1)8);
    mov(cnt1_neg, 0);
    br(LT, CH1_LOOP);
    b(NOMATCH);

  BIND(HAS_ZERO);
    rev(tmp1, tmp1);
    clz(tmp1, tmp1);
    add(cnt1_neg, cnt1_neg, tmp1, LSR, 3);
    b(MATCH);

  BIND(DO1_SHORT);
    mov(result_tmp, cnt1);
    lea(str1, Address(str1, cnt1, Address::uxtw(1)));
    sub(cnt1_neg, zr, cnt1, LSL, 1);
  BIND(DO1_LOOP);
    ldrh(ch1, Address(str1, cnt1_neg));
    cmpw(ch, ch1);
    br(EQ, MATCH);
    adds(cnt1_neg, cnt1_neg, 2);
    br(LT, DO1_LOOP);
  BIND(NOMATCH);
    mov(result, -1);
    b(DONE);
  BIND(MATCH);
    add(result, result_tmp, cnt1_neg, ASR, 1);
  BIND(DONE);
}

void C2_MacroAssembler::string_indexof_char_sve(Register str1, Register cnt1,
                                                Register ch, Register result,
                                                FloatRegister ztmp1,
                                                FloatRegister ztmp2,
                                                PRegister tmp_pg,
                                                PRegister tmp_pdn, bool isL)
{
  // Note that `tmp_pdn` should *NOT* be used as governing predicate register.
  assert(tmp_pg->is_governing(),
         "this register has to be a governing predicate register");

  Label LOOP, MATCH, DONE, NOMATCH;
  Register vec_len = rscratch1;
  Register idx = rscratch2;

  SIMD_RegVariant T = (isL == true) ? B : H;

  cbz(cnt1, NOMATCH);

  // Assign the particular char throughout the vector.
  sve_dup(ztmp2, T, ch);
  if (isL) {
    sve_cntb(vec_len);
  } else {
    sve_cnth(vec_len);
  }
  mov(idx, 0);

  // Generate a predicate to control the reading of input string.
  sve_whilelt(tmp_pg, T, idx, cnt1);

  BIND(LOOP);
    // Read a vector of 8- or 16-bit data depending on the string type. Note
    // that inactive elements indicated by the predicate register won't cause
    // a data read from memory to the destination vector.
    if (isL) {
      sve_ld1b(ztmp1, T, tmp_pg, Address(str1, idx));
    } else {
      sve_ld1h(ztmp1, T, tmp_pg, Address(str1, idx, Address::lsl(1)));
    }
    add(idx, idx, vec_len);

    // Perform the comparison. An element of the destination predicate is set
    // to active if the particular char is matched.
    sve_cmp(Assembler::EQ, tmp_pdn, T, tmp_pg, ztmp1, ztmp2);

    // Branch if the particular char is found.
    br(NE, MATCH);

    sve_whilelt(tmp_pg, T, idx, cnt1);

    // Loop back if the particular char not found.
    br(MI, LOOP);

  BIND(NOMATCH);
    mov(result, -1);
    b(DONE);

  BIND(MATCH);
    // Undo the index increment.
    sub(idx, idx, vec_len);

    // Crop the vector to find its location.
    sve_brka(tmp_pdn, tmp_pg, tmp_pdn, false /* isMerge */);
    add(result, idx, -1);
    sve_incp(result, T, tmp_pdn);
  BIND(DONE);
}

void C2_MacroAssembler::stringL_indexof_char(Register str1, Register cnt1,
                                            Register ch, Register result,
                                            Register tmp1, Register tmp2, Register tmp3)
{
  Label CH1_LOOP, HAS_ZERO, DO1_SHORT, DO1_LOOP, MATCH, NOMATCH, DONE;
  Register cnt1_neg = cnt1;
  Register ch1 = rscratch1;
  Register result_tmp = rscratch2;

  cbz(cnt1, NOMATCH);

  cmp(cnt1, (u1)8);
  br(LT, DO1_SHORT);

  orr(ch, ch, ch, LSL, 8);
  orr(ch, ch, ch, LSL, 16);
  orr(ch, ch, ch, LSL, 32);

  sub(cnt1, cnt1, 8);
  mov(result_tmp, cnt1);
  lea(str1, Address(str1, cnt1));
  sub(cnt1_neg, zr, cnt1);

  mov(tmp3, 0x0101010101010101);

  BIND(CH1_LOOP);
    ldr(ch1, Address(str1, cnt1_neg));
    eor(ch1, ch, ch1);
    sub(tmp1, ch1, tmp3);
    orr(tmp2, ch1, 0x7f7f7f7f7f7f7f7f);
    bics(tmp1, tmp1, tmp2);
    br(NE, HAS_ZERO);
    adds(cnt1_neg, cnt1_neg, 8);
    br(LT, CH1_LOOP);

    cmp(cnt1_neg, (u1)8);
    mov(cnt1_neg, 0);
    br(LT, CH1_LOOP);
    b(NOMATCH);

  BIND(HAS_ZERO);
    rev(tmp1, tmp1);
    clz(tmp1, tmp1);
    add(cnt1_neg, cnt1_neg, tmp1, LSR, 3);
    b(MATCH);

  BIND(DO1_SHORT);
    mov(result_tmp, cnt1);
    lea(str1, Address(str1, cnt1));
    sub(cnt1_neg, zr, cnt1);
  BIND(DO1_LOOP);
    ldrb(ch1, Address(str1, cnt1_neg));
    cmp(ch, ch1);
    br(EQ, MATCH);
    adds(cnt1_neg, cnt1_neg, 1);
    br(LT, DO1_LOOP);
  BIND(NOMATCH);
    mov(result, -1);
    b(DONE);
  BIND(MATCH);
    add(result, result_tmp, cnt1_neg);
  BIND(DONE);
}

// Compare strings.
void C2_MacroAssembler::string_compare(Register str1, Register str2,
    Register cnt1, Register cnt2, Register result, Register tmp1, Register tmp2,
    FloatRegister vtmp1, FloatRegister vtmp2, FloatRegister vtmp3,
    PRegister pgtmp1, PRegister pgtmp2, int ae) {
  Label DONE, SHORT_LOOP, SHORT_STRING, SHORT_LAST, TAIL, STUB,
      DIFF, NEXT_WORD, SHORT_LOOP_TAIL, SHORT_LAST2, SHORT_LAST_INIT,
      SHORT_LOOP_START, TAIL_CHECK;

  bool isLL = ae == StrIntrinsicNode::LL;
  bool isLU = ae == StrIntrinsicNode::LU;
  bool isUL = ae == StrIntrinsicNode::UL;

  // The stub threshold for LL strings is: 72 (64 + 8) chars
  // UU: 36 chars, or 72 bytes (valid for the 64-byte large loop with prefetch)
  // LU/UL: 24 chars, or 48 bytes (valid for the 16-character loop at least)
  const u1 stub_threshold = isLL ? 72 : ((isLU || isUL) ? 24 : 36);

  bool str1_isL = isLL || isLU;
  bool str2_isL = isLL || isUL;

  int str1_chr_shift = str1_isL ? 0 : 1;
  int str2_chr_shift = str2_isL ? 0 : 1;
  int str1_chr_size = str1_isL ? 1 : 2;
  int str2_chr_size = str2_isL ? 1 : 2;
  int minCharsInWord = isLL ? wordSize : wordSize/2;

  FloatRegister vtmpZ = vtmp1, vtmp = vtmp2;
  chr_insn str1_load_chr = str1_isL ? (chr_insn)&MacroAssembler::ldrb :
                                      (chr_insn)&MacroAssembler::ldrh;
  chr_insn str2_load_chr = str2_isL ? (chr_insn)&MacroAssembler::ldrb :
                                      (chr_insn)&MacroAssembler::ldrh;
  uxt_insn ext_chr = isLL ? (uxt_insn)&MacroAssembler::uxtbw :
                            (uxt_insn)&MacroAssembler::uxthw;

  BLOCK_COMMENT("string_compare {");

  // Bizzarely, the counts are passed in bytes, regardless of whether they
  // are L or U strings, however the result is always in characters.
  if (!str1_isL) asrw(cnt1, cnt1, 1);
  if (!str2_isL) asrw(cnt2, cnt2, 1);

  // Compute the minimum of the string lengths and save the difference.
  subsw(result, cnt1, cnt2);
  cselw(cnt2, cnt1, cnt2, Assembler::LE); // min

  // A very short string
  cmpw(cnt2, minCharsInWord);
  br(Assembler::LE, SHORT_STRING);

  // Compare longwords
  // load first parts of strings and finish initialization while loading
  {
    if (str1_isL == str2_isL) { // LL or UU
      ldr(tmp1, Address(str1));
      cmp(str1, str2);
      br(Assembler::EQ, DONE);
      ldr(tmp2, Address(str2));
      cmp(cnt2, stub_threshold);
      br(GE, STUB);
      subsw(cnt2, cnt2, minCharsInWord);
      br(EQ, TAIL_CHECK);
      lea(str2, Address(str2, cnt2, Address::uxtw(str2_chr_shift)));
      lea(str1, Address(str1, cnt2, Address::uxtw(str1_chr_shift)));
      sub(cnt2, zr, cnt2, LSL, str2_chr_shift);
    } else if (isLU) {
      ldrs(vtmp, Address(str1));
      ldr(tmp2, Address(str2));
      cmp(cnt2, stub_threshold);
      br(GE, STUB);
      subw(cnt2, cnt2, 4);
      eor(vtmpZ, T16B, vtmpZ, vtmpZ);
      lea(str1, Address(str1, cnt2, Address::uxtw(str1_chr_shift)));
      lea(str2, Address(str2, cnt2, Address::uxtw(str2_chr_shift)));
      zip1(vtmp, T8B, vtmp, vtmpZ);
      sub(cnt1, zr, cnt2, LSL, str1_chr_shift);
      sub(cnt2, zr, cnt2, LSL, str2_chr_shift);
      add(cnt1, cnt1, 4);
      fmovd(tmp1, vtmp);
    } else { // UL case
      ldr(tmp1, Address(str1));
      ldrs(vtmp, Address(str2));
      cmp(cnt2, stub_threshold);
      br(GE, STUB);
      subw(cnt2, cnt2, 4);
      lea(str1, Address(str1, cnt2, Address::uxtw(str1_chr_shift)));
      eor(vtmpZ, T16B, vtmpZ, vtmpZ);
      lea(str2, Address(str2, cnt2, Address::uxtw(str2_chr_shift)));
      sub(cnt1, zr, cnt2, LSL, str1_chr_shift);
      zip1(vtmp, T8B, vtmp, vtmpZ);
      sub(cnt2, zr, cnt2, LSL, str2_chr_shift);
      add(cnt1, cnt1, 8);
      fmovd(tmp2, vtmp);
    }
    adds(cnt2, cnt2, isUL ? 4 : 8);
    br(GE, TAIL);
    eor(rscratch2, tmp1, tmp2);
    cbnz(rscratch2, DIFF);
    // main loop
    bind(NEXT_WORD);
    if (str1_isL == str2_isL) {
      ldr(tmp1, Address(str1, cnt2));
      ldr(tmp2, Address(str2, cnt2));
      adds(cnt2, cnt2, 8);
    } else if (isLU) {
      ldrs(vtmp, Address(str1, cnt1));
      ldr(tmp2, Address(str2, cnt2));
      add(cnt1, cnt1, 4);
      zip1(vtmp, T8B, vtmp, vtmpZ);
      fmovd(tmp1, vtmp);
      adds(cnt2, cnt2, 8);
    } else { // UL
      ldrs(vtmp, Address(str2, cnt2));
      ldr(tmp1, Address(str1, cnt1));
      zip1(vtmp, T8B, vtmp, vtmpZ);
      add(cnt1, cnt1, 8);
      fmovd(tmp2, vtmp);
      adds(cnt2, cnt2, 4);
    }
    br(GE, TAIL);

    eor(rscratch2, tmp1, tmp2);
    cbz(rscratch2, NEXT_WORD);
    b(DIFF);
    bind(TAIL);
    eor(rscratch2, tmp1, tmp2);
    cbnz(rscratch2, DIFF);
    // Last longword.  In the case where length == 4 we compare the
    // same longword twice, but that's still faster than another
    // conditional branch.
    if (str1_isL == str2_isL) {
      ldr(tmp1, Address(str1));
      ldr(tmp2, Address(str2));
    } else if (isLU) {
      ldrs(vtmp, Address(str1));
      ldr(tmp2, Address(str2));
      zip1(vtmp, T8B, vtmp, vtmpZ);
      fmovd(tmp1, vtmp);
    } else { // UL
      ldrs(vtmp, Address(str2));
      ldr(tmp1, Address(str1));
      zip1(vtmp, T8B, vtmp, vtmpZ);
      fmovd(tmp2, vtmp);
    }
    bind(TAIL_CHECK);
    eor(rscratch2, tmp1, tmp2);
    cbz(rscratch2, DONE);

    // Find the first different characters in the longwords and
    // compute their difference.
    bind(DIFF);
    rev(rscratch2, rscratch2);
    clz(rscratch2, rscratch2);
    andr(rscratch2, rscratch2, isLL ? -8 : -16);
    lsrv(tmp1, tmp1, rscratch2);
    (this->*ext_chr)(tmp1, tmp1);
    lsrv(tmp2, tmp2, rscratch2);
    (this->*ext_chr)(tmp2, tmp2);
    subw(result, tmp1, tmp2);
    b(DONE);
  }

  bind(STUB);
    RuntimeAddress stub = nullptr;
    switch(ae) {
      case StrIntrinsicNode::LL:
        stub = RuntimeAddress(StubRoutines::aarch64::compare_long_string_LL());
        break;
      case StrIntrinsicNode::UU:
        stub = RuntimeAddress(StubRoutines::aarch64::compare_long_string_UU());
        break;
      case StrIntrinsicNode::LU:
        stub = RuntimeAddress(StubRoutines::aarch64::compare_long_string_LU());
        break;
      case StrIntrinsicNode::UL:
        stub = RuntimeAddress(StubRoutines::aarch64::compare_long_string_UL());
        break;
      default:
        ShouldNotReachHere();
     }
    assert(stub.target() != nullptr, "compare_long_string stub has not been generated");
    address call = trampoline_call(stub);
    if (call == nullptr) {
      DEBUG_ONLY(reset_labels(DONE, SHORT_LOOP, SHORT_STRING, SHORT_LAST, SHORT_LOOP_TAIL, SHORT_LAST2, SHORT_LAST_INIT, SHORT_LOOP_START));
      ciEnv::current()->record_failure("CodeCache is full");
      return;
    }
    b(DONE);

  bind(SHORT_STRING);
  // Is the minimum length zero?
  cbz(cnt2, DONE);
  // arrange code to do most branches while loading and loading next characters
  // while comparing previous
  (this->*str1_load_chr)(tmp1, Address(post(str1, str1_chr_size)));
  subs(cnt2, cnt2, 1);
  br(EQ, SHORT_LAST_INIT);
  (this->*str2_load_chr)(cnt1, Address(post(str2, str2_chr_size)));
  b(SHORT_LOOP_START);
  bind(SHORT_LOOP);
  subs(cnt2, cnt2, 1);
  br(EQ, SHORT_LAST);
  bind(SHORT_LOOP_START);
  (this->*str1_load_chr)(tmp2, Address(post(str1, str1_chr_size)));
  (this->*str2_load_chr)(rscratch1, Address(post(str2, str2_chr_size)));
  cmp(tmp1, cnt1);
  br(NE, SHORT_LOOP_TAIL);
  subs(cnt2, cnt2, 1);
  br(EQ, SHORT_LAST2);
  (this->*str1_load_chr)(tmp1, Address(post(str1, str1_chr_size)));
  (this->*str2_load_chr)(cnt1, Address(post(str2, str2_chr_size)));
  cmp(tmp2, rscratch1);
  br(EQ, SHORT_LOOP);
  sub(result, tmp2, rscratch1);
  b(DONE);
  bind(SHORT_LOOP_TAIL);
  sub(result, tmp1, cnt1);
  b(DONE);
  bind(SHORT_LAST2);
  cmp(tmp2, rscratch1);
  br(EQ, DONE);
  sub(result, tmp2, rscratch1);

  b(DONE);
  bind(SHORT_LAST_INIT);
  (this->*str2_load_chr)(cnt1, Address(post(str2, str2_chr_size)));
  bind(SHORT_LAST);
  cmp(tmp1, cnt1);
  br(EQ, DONE);
  sub(result, tmp1, cnt1);

  bind(DONE);

  BLOCK_COMMENT("} string_compare");
}

void C2_MacroAssembler::neon_compare(FloatRegister dst, BasicType bt, FloatRegister src1,
                                     FloatRegister src2, Condition cond, bool isQ) {
  SIMD_Arrangement size = esize2arrangement((unsigned)type2aelembytes(bt), isQ);
  FloatRegister zn = src1, zm = src2;
  bool needs_negation = false;
  switch (cond) {
    case LT: cond = GT; zn = src2; zm = src1; break;
    case LE: cond = GE; zn = src2; zm = src1; break;
    case LO: cond = HI; zn = src2; zm = src1; break;
    case LS: cond = HS; zn = src2; zm = src1; break;
    case NE: cond = EQ; needs_negation = true; break;
    default:
      break;
  }

  if (is_floating_point_type(bt)) {
    fcm(cond, dst, size, zn, zm);
  } else {
    cm(cond, dst, size, zn, zm);
  }

  if (needs_negation) {
    notr(dst, isQ ? T16B : T8B, dst);
  }
}

void C2_MacroAssembler::neon_compare_zero(FloatRegister dst, BasicType bt, FloatRegister src,
                                          Condition cond, bool isQ) {
  SIMD_Arrangement size = esize2arrangement((unsigned)type2aelembytes(bt), isQ);
  if (bt == T_FLOAT || bt == T_DOUBLE) {
    if (cond == Assembler::NE) {
      fcm(Assembler::EQ, dst, size, src);
      notr(dst, isQ ? T16B : T8B, dst);
    } else {
      fcm(cond, dst, size, src);
    }
  } else {
    if (cond == Assembler::NE) {
      cm(Assembler::EQ, dst, size, src);
      notr(dst, isQ ? T16B : T8B, dst);
    } else {
      cm(cond, dst, size, src);
    }
  }
}

// Compress the least significant bit of each byte to the rightmost and clear
// the higher garbage bits.
void C2_MacroAssembler::bytemask_compress(Register dst) {
  // Example input, dst = 0x01 00 00 00 01 01 00 01
  // The "??" bytes are garbage.
  orr(dst, dst, dst, Assembler::LSR, 7);  // dst = 0x?? 02 ?? 00 ?? 03 ?? 01
  orr(dst, dst, dst, Assembler::LSR, 14); // dst = 0x????????08 ??????0D
  orr(dst, dst, dst, Assembler::LSR, 28); // dst = 0x????????????????8D
  andr(dst, dst, 0xff);                   // dst = 0x8D
}

// Pack the lowest-numbered bit of each mask element in src into a long value
// in dst, at most the first 64 lane elements.
// Clobbers: rscratch1, if UseSVE=1 or the hardware doesn't support FEAT_BITPERM.
void C2_MacroAssembler::sve_vmask_tolong(Register dst, PRegister src, BasicType bt, int lane_cnt,
                                         FloatRegister vtmp1, FloatRegister vtmp2) {
  assert(lane_cnt <= 64 && is_power_of_2(lane_cnt), "Unsupported lane count");
  assert_different_registers(dst, rscratch1);
  assert_different_registers(vtmp1, vtmp2);

  Assembler::SIMD_RegVariant size = elemType_to_regVariant(bt);
  // Example:   src = 0b01100101 10001101, bt = T_BYTE, lane_cnt = 16
  // Expected:  dst = 0x658D

  // Convert the mask into vector with sequential bytes.
  // vtmp1 = 0x00010100 0x00010001 0x01000000 0x01010001
  sve_cpy(vtmp1, size, src, 1, false);
  if (bt != T_BYTE) {
    sve_vector_narrow(vtmp1, B, vtmp1, size, vtmp2);
  }

  if (UseSVE > 1 && VM_Version::supports_svebitperm()) {
    // Given a vector with the value 0x00 or 0x01 in each byte, the basic idea
    // is to compress each significant bit of the byte in a cross-lane way. Due
    // to the lack of a cross-lane bit-compress instruction, we use BEXT
    // (bit-compress in each lane) with the biggest lane size (T = D) then
    // concatenate the results.

    // The second source input of BEXT, initialized with 0x01 in each byte.
    // vtmp2 = 0x01010101 0x01010101 0x01010101 0x01010101
    sve_dup(vtmp2, B, 1);

    // BEXT vtmp1.D, vtmp1.D, vtmp2.D
    // vtmp1 = 0x0001010000010001 | 0x0100000001010001
    // vtmp2 = 0x0101010101010101 | 0x0101010101010101
    //         ---------------------------------------
    // vtmp1 = 0x0000000000000065 | 0x000000000000008D
    sve_bext(vtmp1, D, vtmp1, vtmp2);

    // Concatenate the lowest significant 8 bits in each 8 bytes, and extract the
    // result to dst.
    // vtmp1 = 0x0000000000000000 | 0x000000000000658D
    // dst   = 0x658D
    if (lane_cnt <= 8) {
      // No need to concatenate.
      umov(dst, vtmp1, B, 0);
    } else if (lane_cnt <= 16) {
      ins(vtmp1, B, vtmp1, 1, 8);
      umov(dst, vtmp1, H, 0);
    } else {
      // As the lane count is 64 at most, the final expected value must be in
      // the lowest 64 bits after narrowing vtmp1 from D to B.
      sve_vector_narrow(vtmp1, B, vtmp1, D, vtmp2);
      umov(dst, vtmp1, D, 0);
    }
  } else if (UseSVE > 0) {
    // Compress the lowest 8 bytes.
    fmovd(dst, vtmp1);
    bytemask_compress(dst);
    if (lane_cnt <= 8) return;

    // Repeat on higher bytes and join the results.
    // Compress 8 bytes in each iteration.
    for (int idx = 1; idx < (lane_cnt / 8); idx++) {
      sve_extract_integral(rscratch1, T_LONG, vtmp1, idx, vtmp2);
      bytemask_compress(rscratch1);
      orr(dst, dst, rscratch1, Assembler::LSL, idx << 3);
    }
  } else {
    assert(false, "unsupported");
    ShouldNotReachHere();
  }
}

// Unpack the mask, a long value in src, into predicate register dst based on the
// corresponding data type. Note that dst can support at most 64 lanes.
// Below example gives the expected dst predicate register in different types, with
// a valid src(0x658D) on a 1024-bit vector size machine.
// BYTE:  dst = 0x00 00 00 00 00 00 00 00 00 00 00 00 00 00 65 8D
// SHORT: dst = 0x00 00 00 00 00 00 00 00 00 00 00 00 14 11 40 51
// INT:   dst = 0x00 00 00 00 00 00 00 00 01 10 01 01 10 00 11 01
// LONG:  dst = 0x00 01 01 00 00 01 00 01 01 00 00 00 01 01 00 01
//
// The number of significant bits of src must be equal to lane_cnt. E.g., 0xFF658D which
// has 24 significant bits would be an invalid input if dst predicate register refers to
// a LONG type 1024-bit vector, which has at most 16 lanes.
void C2_MacroAssembler::sve_vmask_fromlong(PRegister dst, Register src, BasicType bt, int lane_cnt,
                                           FloatRegister vtmp1, FloatRegister vtmp2) {
  assert(UseSVE == 2 && VM_Version::supports_svebitperm() &&
         lane_cnt <= 64 && is_power_of_2(lane_cnt), "unsupported");
  Assembler::SIMD_RegVariant size = elemType_to_regVariant(bt);
  // Example:   src = 0x658D, bt = T_BYTE, size = B, lane_cnt = 16
  // Expected:  dst = 0b01101001 10001101

  // Put long value from general purpose register into the first lane of vector.
  // vtmp1 = 0x0000000000000000 | 0x000000000000658D
  sve_dup(vtmp1, B, 0);
  mov(vtmp1, D, 0, src);

  // As sve_cmp generates mask value with the minimum unit in byte, we should
  // transform the value in the first lane which is mask in bit now to the
  // mask in byte, which can be done by SVE2's BDEP instruction.

  // The first source input of BDEP instruction. Deposite each byte in every 8 bytes.
  // vtmp1 = 0x0000000000000065 | 0x000000000000008D
  if (lane_cnt <= 8) {
    // Nothing. As only one byte exsits.
  } else if (lane_cnt <= 16) {
    ins(vtmp1, B, vtmp1, 8, 1);
    mov(vtmp1, B, 1, zr);
  } else {
    sve_vector_extend(vtmp1, D, vtmp1, B);
  }

  // The second source input of BDEP instruction, initialized with 0x01 for each byte.
  // vtmp2 = 0x01010101 0x01010101 0x01010101 0x01010101
  sve_dup(vtmp2, B, 1);

  // BDEP vtmp1.D, vtmp1.D, vtmp2.D
  // vtmp1 = 0x0000000000000065 | 0x000000000000008D
  // vtmp2 = 0x0101010101010101 | 0x0101010101010101
  //         ---------------------------------------
  // vtmp1 = 0x0001010000010001 | 0x0100000001010001
  sve_bdep(vtmp1, D, vtmp1, vtmp2);

  if (bt != T_BYTE) {
    sve_vector_extend(vtmp1, size, vtmp1, B);
  }
  // Generate mask according to the given vector, in which the elements have been
  // extended to expected type.
  // dst = 0b01101001 10001101
  sve_cmp(Assembler::NE, dst, size, ptrue, vtmp1, 0);
}

// Clobbers: rflags
void C2_MacroAssembler::sve_compare(PRegister pd, BasicType bt, PRegister pg,
                                    FloatRegister zn, FloatRegister zm, Condition cond) {
  assert(pg->is_governing(), "This register has to be a governing predicate register");
  FloatRegister z1 = zn, z2 = zm;
  switch (cond) {
    case LE: z1 = zm; z2 = zn; cond = GE; break;
    case LT: z1 = zm; z2 = zn; cond = GT; break;
    case LO: z1 = zm; z2 = zn; cond = HI; break;
    case LS: z1 = zm; z2 = zn; cond = HS; break;
    default:
      break;
  }

  SIMD_RegVariant size = elemType_to_regVariant(bt);
  if (is_floating_point_type(bt)) {
    sve_fcm(cond, pd, size, pg, z1, z2);
  } else {
    assert(is_integral_type(bt), "unsupported element type");
    sve_cmp(cond, pd, size, pg, z1, z2);
  }
}

// Get index of the last mask lane that is set
void C2_MacroAssembler::sve_vmask_lasttrue(Register dst, BasicType bt, PRegister src, PRegister ptmp) {
  SIMD_RegVariant size = elemType_to_regVariant(bt);
  sve_rev(ptmp, size, src);
  sve_brkb(ptmp, ptrue, ptmp, false);
  sve_cntp(dst, size, ptrue, ptmp);
  movw(rscratch1, MaxVectorSize / type2aelembytes(bt) - 1);
  subw(dst, rscratch1, dst);
}

// Extend integer vector src to dst with the same lane count
// but larger element size, e.g. 4B -> 4I
void C2_MacroAssembler::neon_vector_extend(FloatRegister dst, BasicType dst_bt, unsigned dst_vlen_in_bytes,
                                           FloatRegister src, BasicType src_bt) {
  if (src_bt == T_BYTE) {
    if (dst_bt == T_SHORT) {
      // 4B/8B to 4S/8S
      assert(dst_vlen_in_bytes == 8 || dst_vlen_in_bytes == 16, "unsupported");
      sxtl(dst, T8H, src, T8B);
    } else {
      // 4B to 4I
      assert(dst_vlen_in_bytes == 16 && dst_bt == T_INT, "unsupported");
      sxtl(dst, T8H, src, T8B);
      sxtl(dst, T4S, dst, T4H);
    }
  } else if (src_bt == T_SHORT) {
    // 4S to 4I
    assert(dst_vlen_in_bytes == 16 && dst_bt == T_INT, "unsupported");
    sxtl(dst, T4S, src, T4H);
  } else if (src_bt == T_INT) {
    // 2I to 2L
    assert(dst_vlen_in_bytes == 16 && dst_bt == T_LONG, "unsupported");
    sxtl(dst, T2D, src, T2S);
  } else {
    ShouldNotReachHere();
  }
}

// Narrow integer vector src down to dst with the same lane count
// but smaller element size, e.g. 4I -> 4B
void C2_MacroAssembler::neon_vector_narrow(FloatRegister dst, BasicType dst_bt,
                                           FloatRegister src, BasicType src_bt, unsigned src_vlen_in_bytes) {
  if (src_bt == T_SHORT) {
    // 4S/8S to 4B/8B
    assert(src_vlen_in_bytes == 8 || src_vlen_in_bytes == 16, "unsupported");
    assert(dst_bt == T_BYTE, "unsupported");
    xtn(dst, T8B, src, T8H);
  } else if (src_bt == T_INT) {
    // 4I to 4B/4S
    assert(src_vlen_in_bytes == 16, "unsupported");
    assert(dst_bt == T_BYTE || dst_bt == T_SHORT, "unsupported");
    xtn(dst, T4H, src, T4S);
    if (dst_bt == T_BYTE) {
      xtn(dst, T8B, dst, T8H);
    }
  } else if (src_bt == T_LONG) {
    // 2L to 2I
    assert(src_vlen_in_bytes == 16, "unsupported");
    assert(dst_bt == T_INT, "unsupported");
    xtn(dst, T2S, src, T2D);
  } else {
    ShouldNotReachHere();
  }
}

void C2_MacroAssembler::sve_vector_extend(FloatRegister dst, SIMD_RegVariant dst_size,
                                          FloatRegister src, SIMD_RegVariant src_size) {
  assert(dst_size > src_size && dst_size <= D && src_size <= S, "invalid element size");
  if (src_size == B) {
    switch (dst_size) {
    case H:
      sve_sunpklo(dst, H, src);
      break;
    case S:
      sve_sunpklo(dst, H, src);
      sve_sunpklo(dst, S, dst);
      break;
    case D:
      sve_sunpklo(dst, H, src);
      sve_sunpklo(dst, S, dst);
      sve_sunpklo(dst, D, dst);
      break;
    default:
      ShouldNotReachHere();
    }
  } else if (src_size == H) {
    if (dst_size == S) {
      sve_sunpklo(dst, S, src);
    } else { // D
      sve_sunpklo(dst, S, src);
      sve_sunpklo(dst, D, dst);
    }
  } else if (src_size == S) {
    sve_sunpklo(dst, D, src);
  }
}

// Vector narrow from src to dst with specified element sizes.
// High part of dst vector will be filled with zero.
void C2_MacroAssembler::sve_vector_narrow(FloatRegister dst, SIMD_RegVariant dst_size,
                                          FloatRegister src, SIMD_RegVariant src_size,
                                          FloatRegister tmp) {
  assert(dst_size < src_size && dst_size <= S && src_size <= D, "invalid element size");
  assert_different_registers(src, tmp);
  sve_dup(tmp, src_size, 0);
  if (src_size == D) {
    switch (dst_size) {
    case S:
      sve_uzp1(dst, S, src, tmp);
      break;
    case H:
      assert_different_registers(dst, tmp);
      sve_uzp1(dst, S, src, tmp);
      sve_uzp1(dst, H, dst, tmp);
      break;
    case B:
      assert_different_registers(dst, tmp);
      sve_uzp1(dst, S, src, tmp);
      sve_uzp1(dst, H, dst, tmp);
      sve_uzp1(dst, B, dst, tmp);
      break;
    default:
      ShouldNotReachHere();
    }
  } else if (src_size == S) {
    if (dst_size == H) {
      sve_uzp1(dst, H, src, tmp);
    } else { // B
      assert_different_registers(dst, tmp);
      sve_uzp1(dst, H, src, tmp);
      sve_uzp1(dst, B, dst, tmp);
    }
  } else if (src_size == H) {
    sve_uzp1(dst, B, src, tmp);
  }
}

// Extend src predicate to dst predicate with the same lane count but larger
// element size, e.g. 64Byte -> 512Long
void C2_MacroAssembler::sve_vmaskcast_extend(PRegister dst, PRegister src,
                                             uint dst_element_length_in_bytes,
                                             uint src_element_length_in_bytes) {
  if (dst_element_length_in_bytes == 2 * src_element_length_in_bytes) {
    sve_punpklo(dst, src);
  } else if (dst_element_length_in_bytes == 4 * src_element_length_in_bytes) {
    sve_punpklo(dst, src);
    sve_punpklo(dst, dst);
  } else if (dst_element_length_in_bytes == 8 * src_element_length_in_bytes) {
    sve_punpklo(dst, src);
    sve_punpklo(dst, dst);
    sve_punpklo(dst, dst);
  } else {
    assert(false, "unsupported");
    ShouldNotReachHere();
  }
}

// Narrow src predicate to dst predicate with the same lane count but
// smaller element size, e.g. 512Long -> 64Byte
void C2_MacroAssembler::sve_vmaskcast_narrow(PRegister dst, PRegister src, PRegister ptmp,
                                             uint dst_element_length_in_bytes, uint src_element_length_in_bytes) {
  // The insignificant bits in src predicate are expected to be zero.
  // To ensure the higher order bits of the resultant narrowed vector are 0, an all-zero predicate is
  // passed as the second argument. An example narrowing operation with a given mask would be -
  // 128Long -> 64Int on a 128-bit machine i.e 2L -> 2I
  // Mask (for 2 Longs) : TF
  // Predicate register for the above mask (16 bits) : 00000001 00000000
  // After narrowing (uzp1 dst.b, src.b, ptmp.b) : 0000 0000 0001 0000
  // Which translates to mask for 2 integers as : TF (lower half is considered while upper half is 0)
  assert_different_registers(src, ptmp);
  assert_different_registers(dst, ptmp);
  sve_pfalse(ptmp);
  if (dst_element_length_in_bytes * 2 == src_element_length_in_bytes) {
    sve_uzp1(dst, B, src, ptmp);
  } else if (dst_element_length_in_bytes * 4 == src_element_length_in_bytes) {
    sve_uzp1(dst, H, src, ptmp);
    sve_uzp1(dst, B, dst, ptmp);
  } else if (dst_element_length_in_bytes * 8 == src_element_length_in_bytes) {
    sve_uzp1(dst, S, src, ptmp);
    sve_uzp1(dst, H, dst, ptmp);
    sve_uzp1(dst, B, dst, ptmp);
  } else {
    assert(false, "unsupported");
    ShouldNotReachHere();
  }
}

// Vector reduction add for integral type with ASIMD instructions.
void C2_MacroAssembler::neon_reduce_add_integral(Register dst, BasicType bt,
                                                 Register isrc, FloatRegister vsrc,
                                                 unsigned vector_length_in_bytes,
                                                 FloatRegister vtmp) {
  assert(vector_length_in_bytes == 8 || vector_length_in_bytes == 16, "unsupported");
  assert_different_registers(dst, isrc);
  bool isQ = vector_length_in_bytes == 16;

  BLOCK_COMMENT("neon_reduce_add_integral {");
    switch(bt) {
      case T_BYTE:
        addv(vtmp, isQ ? T16B : T8B, vsrc);
        smov(dst, vtmp, B, 0);
        addw(dst, dst, isrc, ext::sxtb);
        break;
      case T_SHORT:
        addv(vtmp, isQ ? T8H : T4H, vsrc);
        smov(dst, vtmp, H, 0);
        addw(dst, dst, isrc, ext::sxth);
        break;
      case T_INT:
        isQ ? addv(vtmp, T4S, vsrc) : addpv(vtmp, T2S, vsrc, vsrc);
        umov(dst, vtmp, S, 0);
        addw(dst, dst, isrc);
        break;
      case T_LONG:
        assert(isQ, "unsupported");
        addpd(vtmp, vsrc);
        umov(dst, vtmp, D, 0);
        add(dst, dst, isrc);
        break;
      default:
        assert(false, "unsupported");
        ShouldNotReachHere();
    }
  BLOCK_COMMENT("} neon_reduce_add_integral");
}

// Vector reduction multiply for integral type with ASIMD instructions.
// Note: temporary registers vtmp1 and vtmp2 are not used in some cases.
// Clobbers: rscratch1
void C2_MacroAssembler::neon_reduce_mul_integral(Register dst, BasicType bt,
                                                 Register isrc, FloatRegister vsrc,
                                                 unsigned vector_length_in_bytes,
                                                 FloatRegister vtmp1, FloatRegister vtmp2) {
  assert(vector_length_in_bytes == 8 || vector_length_in_bytes == 16, "unsupported");
  bool isQ = vector_length_in_bytes == 16;

  BLOCK_COMMENT("neon_reduce_mul_integral {");
    switch(bt) {
      case T_BYTE:
        if (isQ) {
          // Multiply the lower half and higher half of vector iteratively.
          // vtmp1 = vsrc[8:15]
          ins(vtmp1, D, vsrc, 0, 1);
          // vtmp1[n] = vsrc[n] * vsrc[n + 8], where n=[0, 7]
          mulv(vtmp1, T8B, vtmp1, vsrc);
          // vtmp2 = vtmp1[4:7]
          ins(vtmp2, S, vtmp1, 0, 1);
          // vtmp1[n] = vtmp1[n] * vtmp1[n + 4], where n=[0, 3]
          mulv(vtmp1, T8B, vtmp2, vtmp1);
        } else {
          ins(vtmp1, S, vsrc, 0, 1);
          mulv(vtmp1, T8B, vtmp1, vsrc);
        }
        // vtmp2 = vtmp1[2:3]
        ins(vtmp2, H, vtmp1, 0, 1);
        // vtmp2[n] = vtmp1[n] * vtmp1[n + 2], where n=[0, 1]
        mulv(vtmp2, T8B, vtmp2, vtmp1);
        // dst = vtmp2[0] * isrc * vtmp2[1]
        umov(rscratch1, vtmp2, B, 0);
        mulw(dst, rscratch1, isrc);
        sxtb(dst, dst);
        umov(rscratch1, vtmp2, B, 1);
        mulw(dst, rscratch1, dst);
        sxtb(dst, dst);
        break;
      case T_SHORT:
        if (isQ) {
          ins(vtmp2, D, vsrc, 0, 1);
          mulv(vtmp2, T4H, vtmp2, vsrc);
          ins(vtmp1, S, vtmp2, 0, 1);
          mulv(vtmp1, T4H, vtmp1, vtmp2);
        } else {
          ins(vtmp1, S, vsrc, 0, 1);
          mulv(vtmp1, T4H, vtmp1, vsrc);
        }
        umov(rscratch1, vtmp1, H, 0);
        mulw(dst, rscratch1, isrc);
        sxth(dst, dst);
        umov(rscratch1, vtmp1, H, 1);
        mulw(dst, rscratch1, dst);
        sxth(dst, dst);
        break;
      case T_INT:
        if (isQ) {
          ins(vtmp1, D, vsrc, 0, 1);
          mulv(vtmp1, T2S, vtmp1, vsrc);
        } else {
          vtmp1 = vsrc;
        }
        umov(rscratch1, vtmp1, S, 0);
        mul(dst, rscratch1, isrc);
        umov(rscratch1, vtmp1, S, 1);
        mul(dst, rscratch1, dst);
        break;
      case T_LONG:
        umov(rscratch1, vsrc, D, 0);
        mul(dst, isrc, rscratch1);
        umov(rscratch1, vsrc, D, 1);
        mul(dst, dst, rscratch1);
        break;
      default:
        assert(false, "unsupported");
        ShouldNotReachHere();
    }
  BLOCK_COMMENT("} neon_reduce_mul_integral");
}

// Vector reduction multiply for floating-point type with ASIMD instructions.
void C2_MacroAssembler::neon_reduce_mul_fp(FloatRegister dst, BasicType bt,
                                           FloatRegister fsrc, FloatRegister vsrc,
                                           unsigned vector_length_in_bytes,
                                           FloatRegister vtmp) {
  assert(vector_length_in_bytes == 8 || vector_length_in_bytes == 16, "unsupported");
  bool isQ = vector_length_in_bytes == 16;

  BLOCK_COMMENT("neon_reduce_mul_fp {");
    switch(bt) {
      case T_FLOAT:
        fmuls(dst, fsrc, vsrc);
        ins(vtmp, S, vsrc, 0, 1);
        fmuls(dst, dst, vtmp);
        if (isQ) {
          ins(vtmp, S, vsrc, 0, 2);
          fmuls(dst, dst, vtmp);
          ins(vtmp, S, vsrc, 0, 3);
          fmuls(dst, dst, vtmp);
         }
        break;
      case T_DOUBLE:
        assert(isQ, "unsupported");
        fmuld(dst, fsrc, vsrc);
        ins(vtmp, D, vsrc, 0, 1);
        fmuld(dst, dst, vtmp);
        break;
      default:
        assert(false, "unsupported");
        ShouldNotReachHere();
    }
  BLOCK_COMMENT("} neon_reduce_mul_fp");
}

// Helper to select logical instruction
void C2_MacroAssembler::neon_reduce_logical_helper(int opc, bool is64, Register Rd,
                                                   Register Rn, Register Rm,
                                                   enum shift_kind kind, unsigned shift) {
  switch(opc) {
    case Op_AndReductionV:
      is64 ? andr(Rd, Rn, Rm, kind, shift) : andw(Rd, Rn, Rm, kind, shift);
      break;
    case Op_OrReductionV:
      is64 ? orr(Rd, Rn, Rm, kind, shift) : orrw(Rd, Rn, Rm, kind, shift);
      break;
    case Op_XorReductionV:
      is64 ? eor(Rd, Rn, Rm, kind, shift) : eorw(Rd, Rn, Rm, kind, shift);
      break;
    default:
      assert(false, "unsupported");
      ShouldNotReachHere();
  }
}

// Vector reduction logical operations And, Or, Xor
// Clobbers: rscratch1
void C2_MacroAssembler::neon_reduce_logical(int opc, Register dst, BasicType bt,
                                            Register isrc, FloatRegister vsrc,
                                            unsigned vector_length_in_bytes) {
  assert(opc == Op_AndReductionV || opc == Op_OrReductionV || opc == Op_XorReductionV,
         "unsupported");
  assert(vector_length_in_bytes == 8 || vector_length_in_bytes == 16, "unsupported");
  assert_different_registers(dst, isrc);
  bool isQ = vector_length_in_bytes == 16;

  BLOCK_COMMENT("neon_reduce_logical {");
    umov(rscratch1, vsrc, isQ ? D : S, 0);
    umov(dst, vsrc, isQ ? D : S, 1);
    neon_reduce_logical_helper(opc, /* is64 */ true, dst, dst, rscratch1);
    switch(bt) {
      case T_BYTE:
        if (isQ) {
          neon_reduce_logical_helper(opc, /* is64 */ true, dst, dst, dst, Assembler::LSR, 32);
        }
        neon_reduce_logical_helper(opc, /* is64 */ false, dst, dst, dst, Assembler::LSR, 16);
        neon_reduce_logical_helper(opc, /* is64 */ false, dst, dst, dst, Assembler::LSR, 8);
        neon_reduce_logical_helper(opc, /* is64 */ false, dst, isrc, dst);
        sxtb(dst, dst);
        break;
      case T_SHORT:
        if (isQ) {
          neon_reduce_logical_helper(opc, /* is64 */ true, dst, dst, dst, Assembler::LSR, 32);
        }
        neon_reduce_logical_helper(opc, /* is64 */ false, dst, dst, dst, Assembler::LSR, 16);
        neon_reduce_logical_helper(opc, /* is64 */ false, dst, isrc, dst);
        sxth(dst, dst);
        break;
      case T_INT:
        if (isQ) {
          neon_reduce_logical_helper(opc, /* is64 */ true, dst, dst, dst, Assembler::LSR, 32);
        }
        neon_reduce_logical_helper(opc, /* is64 */ false, dst, isrc, dst);
        break;
      case T_LONG:
        assert(isQ, "unsupported");
        neon_reduce_logical_helper(opc, /* is64 */ true, dst, isrc, dst);
        break;
      default:
        assert(false, "unsupported");
        ShouldNotReachHere();
    }
  BLOCK_COMMENT("} neon_reduce_logical");
}

// Vector reduction min/max for integral type with ASIMD instructions.
// Note: vtmp is not used and expected to be fnoreg for T_LONG case.
// Clobbers: rscratch1, rflags
void C2_MacroAssembler::neon_reduce_minmax_integral(int opc, Register dst, BasicType bt,
                                                    Register isrc, FloatRegister vsrc,
                                                    unsigned vector_length_in_bytes,
                                                    FloatRegister vtmp) {
  assert(opc == Op_MinReductionV || opc == Op_MaxReductionV, "unsupported");
  assert(vector_length_in_bytes == 8 || vector_length_in_bytes == 16, "unsupported");
  assert(bt == T_BYTE || bt == T_SHORT || bt == T_INT || bt == T_LONG, "unsupported");
  assert_different_registers(dst, isrc);
  bool isQ = vector_length_in_bytes == 16;
  bool is_min = opc == Op_MinReductionV;

  BLOCK_COMMENT("neon_reduce_minmax_integral {");
    if (bt == T_LONG) {
      assert(vtmp == fnoreg, "should be");
      assert(isQ, "should be");
      umov(rscratch1, vsrc, D, 0);
      cmp(isrc, rscratch1);
      csel(dst, isrc, rscratch1, is_min ? LT : GT);
      umov(rscratch1, vsrc, D, 1);
      cmp(dst, rscratch1);
      csel(dst, dst, rscratch1, is_min ? LT : GT);
    } else {
      SIMD_Arrangement size = esize2arrangement((unsigned)type2aelembytes(bt), isQ);
      if (size == T2S) {
        is_min ? sminp(vtmp, size, vsrc, vsrc) : smaxp(vtmp, size, vsrc, vsrc);
      } else {
        is_min ? sminv(vtmp, size, vsrc) : smaxv(vtmp, size, vsrc);
      }
      if (bt == T_INT) {
        umov(dst, vtmp, S, 0);
      } else {
        smov(dst, vtmp, elemType_to_regVariant(bt), 0);
      }
      cmpw(dst, isrc);
      cselw(dst, dst, isrc, is_min ? LT : GT);
    }
  BLOCK_COMMENT("} neon_reduce_minmax_integral");
}

// Vector reduction for integral type with SVE instruction.
// Supported operations are Add, And, Or, Xor, Max, Min.
// rflags would be clobbered if opc is Op_MaxReductionV or Op_MinReductionV.
void C2_MacroAssembler::sve_reduce_integral(int opc, Register dst, BasicType bt, Register src1,
                                            FloatRegister src2, PRegister pg, FloatRegister tmp) {
  assert(bt == T_BYTE || bt == T_SHORT || bt == T_INT || bt == T_LONG, "unsupported element type");
  assert(pg->is_governing(), "This register has to be a governing predicate register");
  assert_different_registers(src1, dst);
  // Register "dst" and "tmp" are to be clobbered, and "src1" and "src2" should be preserved.
  Assembler::SIMD_RegVariant size = elemType_to_regVariant(bt);
  switch (opc) {
    case Op_AddReductionVI: {
      sve_uaddv(tmp, size, pg, src2);
      if (bt == T_BYTE) {
        smov(dst, tmp, size, 0);
        addw(dst, src1, dst, ext::sxtb);
      } else if (bt == T_SHORT) {
        smov(dst, tmp, size, 0);
        addw(dst, src1, dst, ext::sxth);
      } else {
        umov(dst, tmp, size, 0);
        addw(dst, dst, src1);
      }
      break;
    }
    case Op_AddReductionVL: {
      sve_uaddv(tmp, size, pg, src2);
      umov(dst, tmp, size, 0);
      add(dst, dst, src1);
      break;
    }
    case Op_AndReductionV: {
      sve_andv(tmp, size, pg, src2);
      if (bt == T_INT || bt == T_LONG) {
        umov(dst, tmp, size, 0);
      } else {
        smov(dst, tmp, size, 0);
      }
      if (bt == T_LONG) {
        andr(dst, dst, src1);
      } else {
        andw(dst, dst, src1);
      }
      break;
    }
    case Op_OrReductionV: {
      sve_orv(tmp, size, pg, src2);
      if (bt == T_INT || bt == T_LONG) {
        umov(dst, tmp, size, 0);
      } else {
        smov(dst, tmp, size, 0);
      }
      if (bt == T_LONG) {
        orr(dst, dst, src1);
      } else {
        orrw(dst, dst, src1);
      }
      break;
    }
    case Op_XorReductionV: {
      sve_eorv(tmp, size, pg, src2);
      if (bt == T_INT || bt == T_LONG) {
        umov(dst, tmp, size, 0);
      } else {
        smov(dst, tmp, size, 0);
      }
      if (bt == T_LONG) {
        eor(dst, dst, src1);
      } else {
        eorw(dst, dst, src1);
      }
      break;
    }
    case Op_MaxReductionV: {
      sve_smaxv(tmp, size, pg, src2);
      if (bt == T_INT || bt == T_LONG) {
        umov(dst, tmp, size, 0);
      } else {
        smov(dst, tmp, size, 0);
      }
      if (bt == T_LONG) {
        cmp(dst, src1);
        csel(dst, dst, src1, Assembler::GT);
      } else {
        cmpw(dst, src1);
        cselw(dst, dst, src1, Assembler::GT);
      }
      break;
    }
    case Op_MinReductionV: {
      sve_sminv(tmp, size, pg, src2);
      if (bt == T_INT || bt == T_LONG) {
        umov(dst, tmp, size, 0);
      } else {
        smov(dst, tmp, size, 0);
      }
      if (bt == T_LONG) {
        cmp(dst, src1);
        csel(dst, dst, src1, Assembler::LT);
      } else {
        cmpw(dst, src1);
        cselw(dst, dst, src1, Assembler::LT);
      }
      break;
    }
    default:
      assert(false, "unsupported");
      ShouldNotReachHere();
  }

  if (opc == Op_AndReductionV || opc == Op_OrReductionV || opc == Op_XorReductionV) {
    if (bt == T_BYTE) {
      sxtb(dst, dst);
    } else if (bt == T_SHORT) {
      sxth(dst, dst);
    }
  }
}

// Set elements of the dst predicate to true for lanes in the range of [0, lane_cnt), or
// to false otherwise. The input "lane_cnt" should be smaller than or equal to the supported
// max vector length of the basic type. Clobbers: rscratch1 and the rFlagsReg.
void C2_MacroAssembler::sve_gen_mask_imm(PRegister dst, BasicType bt, uint32_t lane_cnt) {
  uint32_t max_vector_length = Matcher::max_vector_size(bt);
  assert(lane_cnt <= max_vector_length, "unsupported input lane_cnt");

  // Set all elements to false if the input "lane_cnt" is zero.
  if (lane_cnt == 0) {
    sve_pfalse(dst);
    return;
  }

  SIMD_RegVariant size = elemType_to_regVariant(bt);
  assert(size != Q, "invalid size");

  // Set all true if "lane_cnt" equals to the max lane count.
  if (lane_cnt == max_vector_length) {
    sve_ptrue(dst, size, /* ALL */ 0b11111);
    return;
  }

  // Fixed numbers for "ptrue".
  switch(lane_cnt) {
  case 1: /* VL1 */
  case 2: /* VL2 */
  case 3: /* VL3 */
  case 4: /* VL4 */
  case 5: /* VL5 */
  case 6: /* VL6 */
  case 7: /* VL7 */
  case 8: /* VL8 */
    sve_ptrue(dst, size, lane_cnt);
    return;
  case 16:
    sve_ptrue(dst, size, /* VL16 */ 0b01001);
    return;
  case 32:
    sve_ptrue(dst, size, /* VL32 */ 0b01010);
    return;
  case 64:
    sve_ptrue(dst, size, /* VL64 */ 0b01011);
    return;
  case 128:
    sve_ptrue(dst, size, /* VL128 */ 0b01100);
    return;
  case 256:
    sve_ptrue(dst, size, /* VL256 */ 0b01101);
    return;
  default:
    break;
  }

  // Special patterns for "ptrue".
  if (lane_cnt == round_down_power_of_2(max_vector_length)) {
    sve_ptrue(dst, size, /* POW2 */ 0b00000);
  } else if (lane_cnt == max_vector_length - (max_vector_length % 4)) {
    sve_ptrue(dst, size, /* MUL4 */ 0b11101);
  } else if (lane_cnt == max_vector_length - (max_vector_length % 3)) {
    sve_ptrue(dst, size, /* MUL3 */ 0b11110);
  } else {
    // Encode to "whileltw" for the remaining cases.
    mov(rscratch1, lane_cnt);
    sve_whileltw(dst, size, zr, rscratch1);
  }
}

// Pack active elements of src, under the control of mask, into the lowest-numbered elements of dst.
// Any remaining elements of dst will be filled with zero.
// Clobbers: rscratch1
// Preserves: src, mask
void C2_MacroAssembler::sve_compress_short(FloatRegister dst, FloatRegister src, PRegister mask,
                                           FloatRegister vtmp1, FloatRegister vtmp2,
                                           PRegister pgtmp) {
  assert(pgtmp->is_governing(), "This register has to be a governing predicate register");
  assert_different_registers(dst, src, vtmp1, vtmp2);
  assert_different_registers(mask, pgtmp);

  // Example input:   src   = 8888 7777 6666 5555 4444 3333 2222 1111
  //                  mask  = 0001 0000 0000 0001 0001 0000 0001 0001
  // Expected result: dst   = 0000 0000 0000 8888 5555 4444 2222 1111
  sve_dup(vtmp2, H, 0);

  // Extend lowest half to type INT.
  // dst = 00004444 00003333 00002222 00001111
  sve_uunpklo(dst, S, src);
  // pgtmp = 00000001 00000000 00000001 00000001
  sve_punpklo(pgtmp, mask);
  // Pack the active elements in size of type INT to the right,
  // and fill the remainings with zero.
  // dst = 00000000 00004444 00002222 00001111
  sve_compact(dst, S, dst, pgtmp);
  // Narrow the result back to type SHORT.
  // dst = 0000 0000 0000 0000 0000 4444 2222 1111
  sve_uzp1(dst, H, dst, vtmp2);
  // Count the active elements of lowest half.
  // rscratch1 = 3
  sve_cntp(rscratch1, S, ptrue, pgtmp);

  // Repeat to the highest half.
  // pgtmp = 00000001 00000000 00000000 00000001
  sve_punpkhi(pgtmp, mask);
  // vtmp1 = 00008888 00007777 00006666 00005555
  sve_uunpkhi(vtmp1, S, src);
  // vtmp1 = 00000000 00000000 00008888 00005555
  sve_compact(vtmp1, S, vtmp1, pgtmp);
  // vtmp1 = 0000 0000 0000 0000 0000 0000 8888 5555
  sve_uzp1(vtmp1, H, vtmp1, vtmp2);

  // Compressed low:   dst   = 0000 0000 0000 0000 0000 4444 2222 1111
  // Compressed high:  vtmp1 = 0000 0000 0000 0000 0000 0000 8888  5555
  // Left shift(cross lane) compressed high with TRUE_CNT lanes,
  // TRUE_CNT is the number of active elements in the compressed low.
  neg(rscratch1, rscratch1);
  // vtmp2 = {4 3 2 1 0 -1 -2 -3}
  sve_index(vtmp2, H, rscratch1, 1);
  // vtmp1 = 0000 0000 0000 8888 5555 0000 0000 0000
  sve_tbl(vtmp1, H, vtmp1, vtmp2);

  // Combine the compressed high(after shifted) with the compressed low.
  // dst = 0000 0000 0000 8888 5555 4444 2222 1111
  sve_orr(dst, dst, vtmp1);
}

// Clobbers: rscratch1, rscratch2
// Preserves: src, mask
void C2_MacroAssembler::sve_compress_byte(FloatRegister dst, FloatRegister src, PRegister mask,
                                          FloatRegister vtmp1, FloatRegister vtmp2,
                                          FloatRegister vtmp3, FloatRegister vtmp4,
                                          PRegister ptmp, PRegister pgtmp) {
  assert(pgtmp->is_governing(), "This register has to be a governing predicate register");
  assert_different_registers(dst, src, vtmp1, vtmp2, vtmp3, vtmp4);
  assert_different_registers(mask, ptmp, pgtmp);
  // Example input:   src   = 88 77 66 55 44 33 22 11
  //                  mask  = 01 00 00 01 01 00 01 01
  // Expected result: dst   = 00 00 00 88 55 44 22 11

  sve_dup(vtmp4, B, 0);
  // Extend lowest half to type SHORT.
  // vtmp1 = 0044 0033 0022 0011
  sve_uunpklo(vtmp1, H, src);
  // ptmp = 0001 0000 0001 0001
  sve_punpklo(ptmp, mask);
  // Count the active elements of lowest half.
  // rscratch2 = 3
  sve_cntp(rscratch2, H, ptrue, ptmp);
  // Pack the active elements in size of type SHORT to the right,
  // and fill the remainings with zero.
  // dst = 0000 0044 0022 0011
  sve_compress_short(dst, vtmp1, ptmp, vtmp2, vtmp3, pgtmp);
  // Narrow the result back to type BYTE.
  // dst = 00 00 00 00 00 44 22 11
  sve_uzp1(dst, B, dst, vtmp4);

  // Repeat to the highest half.
  // ptmp = 0001 0000 0000 0001
  sve_punpkhi(ptmp, mask);
  // vtmp1 = 0088 0077 0066 0055
  sve_uunpkhi(vtmp2, H, src);
  // vtmp1 = 0000 0000 0088 0055
  sve_compress_short(vtmp1, vtmp2, ptmp, vtmp3, vtmp4, pgtmp);

  sve_dup(vtmp4, B, 0);
  // vtmp1 = 00 00 00 00 00 00 88 55
  sve_uzp1(vtmp1, B, vtmp1, vtmp4);

  // Compressed low:   dst   = 00 00 00 00 00 44 22 11
  // Compressed high:  vtmp1 = 00 00 00 00 00 00 88 55
  // Left shift(cross lane) compressed high with TRUE_CNT lanes,
  // TRUE_CNT is the number of active elements in the compressed low.
  neg(rscratch2, rscratch2);
  // vtmp2 = {4 3 2 1 0 -1 -2 -3}
  sve_index(vtmp2, B, rscratch2, 1);
  // vtmp1 = 00 00 00 88 55 00 00 00
  sve_tbl(vtmp1, B, vtmp1, vtmp2);
  // Combine the compressed high(after shifted) with the compressed low.
  // dst = 00 00 00 88 55 44 22 11
  sve_orr(dst, dst, vtmp1);
}

void C2_MacroAssembler::neon_reverse_bits(FloatRegister dst, FloatRegister src, BasicType bt, bool isQ) {
  assert(bt == T_BYTE || bt == T_SHORT || bt == T_INT || bt == T_LONG, "unsupported basic type");
  SIMD_Arrangement size = isQ ? T16B : T8B;
  if (bt == T_BYTE) {
    rbit(dst, size, src);
  } else {
    neon_reverse_bytes(dst, src, bt, isQ);
    rbit(dst, size, dst);
  }
}

void C2_MacroAssembler::neon_reverse_bytes(FloatRegister dst, FloatRegister src, BasicType bt, bool isQ) {
  assert(bt == T_BYTE || bt == T_SHORT || bt == T_INT || bt == T_LONG, "unsupported basic type");
  SIMD_Arrangement size = isQ ? T16B : T8B;
  switch (bt) {
    case T_BYTE:
      if (dst != src) {
        orr(dst, size, src, src);
      }
      break;
    case T_SHORT:
      rev16(dst, size, src);
      break;
    case T_INT:
      rev32(dst, size, src);
      break;
    case T_LONG:
      rev64(dst, size, src);
      break;
    default:
      assert(false, "unsupported");
      ShouldNotReachHere();
  }
}

// Extract a scalar element from an sve vector at position 'idx'.
// The input elements in src are expected to be of integral type.
void C2_MacroAssembler::sve_extract_integral(Register dst, BasicType bt, FloatRegister src,
                                             int idx, FloatRegister vtmp) {
  assert(bt == T_BYTE || bt == T_SHORT || bt == T_INT || bt == T_LONG, "unsupported element type");
  Assembler::SIMD_RegVariant size = elemType_to_regVariant(bt);
  if (regVariant_to_elemBits(size) * idx < 128) { // generate lower cost NEON instruction
    if (bt == T_INT || bt == T_LONG) {
      umov(dst, src, size, idx);
    } else {
      smov(dst, src, size, idx);
    }
  } else {
    sve_orr(vtmp, src, src);
    sve_ext(vtmp, vtmp, idx << size);
    if (bt == T_INT || bt == T_LONG) {
      umov(dst, vtmp, size, 0);
    } else {
      smov(dst, vtmp, size, 0);
    }
  }
}

// java.lang.Math::round intrinsics

// Clobbers: rscratch1, rflags
void C2_MacroAssembler::vector_round_neon(FloatRegister dst, FloatRegister src, FloatRegister tmp1,
                                          FloatRegister tmp2, FloatRegister tmp3, SIMD_Arrangement T) {
  assert_different_registers(tmp1, tmp2, tmp3, src, dst);
  switch (T) {
    case T2S:
    case T4S:
      fmovs(tmp1, T, 0.5f);
      mov(rscratch1, jint_cast(0x1.0p23f));
      break;
    case T2D:
      fmovd(tmp1, T, 0.5);
      mov(rscratch1, julong_cast(0x1.0p52));
      break;
    default:
      assert(T == T2S || T == T4S || T == T2D, "invalid arrangement");
  }
  fadd(tmp1, T, tmp1, src);
  fcvtms(tmp1, T, tmp1);
  // tmp1 = floor(src + 0.5, ties to even)

  fcvtas(dst, T, src);
  // dst = round(src), ties to away

  fneg(tmp3, T, src);
  dup(tmp2, T, rscratch1);
  cm(HS, tmp3, T, tmp3, tmp2);
  // tmp3 is now a set of flags

  bif(dst, T16B, tmp1, tmp3);
  // result in dst
}

// Clobbers: rscratch1, rflags
void C2_MacroAssembler::vector_round_sve(FloatRegister dst, FloatRegister src, FloatRegister tmp1,
                                         FloatRegister tmp2, PRegister pgtmp, SIMD_RegVariant T) {
  assert(pgtmp->is_governing(), "This register has to be a governing predicate register");
  assert_different_registers(tmp1, tmp2, src, dst);

  switch (T) {
    case S:
      mov(rscratch1, jint_cast(0x1.0p23f));
      break;
    case D:
      mov(rscratch1, julong_cast(0x1.0p52));
      break;
    default:
      assert(T == S || T == D, "invalid register variant");
  }

  sve_frinta(dst, T, ptrue, src);
  // dst = round(src), ties to away

  Label none;

  sve_fneg(tmp1, T, ptrue, src);
  sve_dup(tmp2, T, rscratch1);
  sve_cmp(HS, pgtmp, T, ptrue, tmp2, tmp1);
  br(EQ, none);
  {
    sve_cpy(tmp1, T, pgtmp, 0.5);
    sve_fadd(tmp1, T, pgtmp, src);
    sve_frintm(dst, T, pgtmp, tmp1);
    // dst = floor(src + 0.5, ties to even)
  }
  bind(none);

  sve_fcvtzs(dst, T, ptrue, dst, T);
  // result in dst
}

void C2_MacroAssembler::vector_signum_neon(FloatRegister dst, FloatRegister src, FloatRegister zero,
                                           FloatRegister one, SIMD_Arrangement T) {
  assert_different_registers(dst, src, zero, one);
  assert(T == T2S || T == T4S || T == T2D, "invalid arrangement");

  facgt(dst, T, src, zero);
  ushr(dst, T, dst, 1); // dst=0 for +-0.0 and NaN. 0x7FF..F otherwise
  bsl(dst, T == T2S ? T8B : T16B, one, src); // Result in dst
}

void C2_MacroAssembler::vector_signum_sve(FloatRegister dst, FloatRegister src, FloatRegister zero,
                                          FloatRegister one, FloatRegister vtmp, PRegister pgtmp, SIMD_RegVariant T) {
    assert_different_registers(dst, src, zero, one, vtmp);
    assert(pgtmp->is_governing(), "This register has to be a governing predicate register");

    sve_orr(vtmp, src, src);
    sve_fac(Assembler::GT, pgtmp, T, ptrue, src, zero); // pmtp=0 for +-0.0 and NaN. 0x1 otherwise
    switch (T) {
    case S:
      sve_and(vtmp, T, min_jint); // Extract the sign bit of float value in every lane of src
      sve_orr(vtmp, T, jint_cast(1.0)); // OR it with +1 to make the final result +1 or -1 depending
                                        // on the sign of the float value
      break;
    case D:
      sve_and(vtmp, T, min_jlong);
      sve_orr(vtmp, T, jlong_cast(1.0));
      break;
    default:
      assert(false, "unsupported");
      ShouldNotReachHere();
    }
    sve_sel(dst, T, pgtmp, vtmp, src); // Select either from src or vtmp based on the predicate register pgtmp
                                       // Result in dst
}

bool C2_MacroAssembler::in_scratch_emit_size() {
  if (ciEnv::current()->task() != nullptr) {
    PhaseOutput* phase_output = Compile::current()->output();
    if (phase_output != nullptr && phase_output->in_scratch_emit_size()) {
      return true;
    }
  }
  return MacroAssembler::in_scratch_emit_size();
}
