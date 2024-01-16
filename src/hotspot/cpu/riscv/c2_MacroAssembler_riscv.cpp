/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

void C2_MacroAssembler::fast_lock(Register objectReg, Register boxReg,
                                  Register tmp1Reg, Register tmp2Reg, Register tmp3Reg) {
  // Use cr register to indicate the fast_lock result: zero for success; non-zero for failure.
  Register flag = t1;
  Register oop = objectReg;
  Register box = boxReg;
  Register disp_hdr = tmp1Reg;
  Register tmp = tmp2Reg;
  Label cont;
  Label object_has_monitor;
  Label count, no_count;

  assert_different_registers(oop, box, tmp, disp_hdr, flag, tmp3Reg, t0);

  // Load markWord from object into displaced_header.
  ld(disp_hdr, Address(oop, oopDesc::mark_offset_in_bytes()));

  if (DiagnoseSyncOnValueBasedClasses != 0) {
    load_klass(flag, oop);
    lwu(flag, Address(flag, Klass::access_flags_offset()));
    test_bit(flag, flag, exact_log2(JVM_ACC_IS_VALUE_BASED_CLASS));
    bnez(flag, cont, true /* is_far */);
  }

  // Check for existing monitor
  test_bit(t0, disp_hdr, exact_log2(markWord::monitor_value));
  bnez(t0, object_has_monitor);

  if (LockingMode == LM_MONITOR) {
    mv(flag, 1); // Set non-zero flag to indicate 'failure' -> take slow-path
    j(cont);
  } else if (LockingMode == LM_LEGACY) {
    // Set tmp to be (markWord of object | UNLOCK_VALUE).
    ori(tmp, disp_hdr, markWord::unlocked_value);

    // Initialize the box. (Must happen before we update the object mark!)
    sd(tmp, Address(box, BasicLock::displaced_header_offset_in_bytes()));

    // Compare object markWord with an unlocked value (tmp) and if
    // equal exchange the stack address of our box with object markWord.
    // On failure disp_hdr contains the possibly locked markWord.
    cmpxchg(/*memory address*/oop, /*expected value*/tmp, /*new value*/box, Assembler::int64, Assembler::aq,
            Assembler::rl, /*result*/disp_hdr);
    mv(flag, zr);
    beq(disp_hdr, tmp, cont); // prepare zero flag and goto cont if we won the cas

    assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

    // If the compare-and-exchange succeeded, then we found an unlocked
    // object, will have now locked it will continue at label cont
    // We did not see an unlocked object so try the fast recursive case.

    // Check if the owner is self by comparing the value in the
    // markWord of object (disp_hdr) with the stack pointer.
    sub(disp_hdr, disp_hdr, sp);
    mv(tmp, (intptr_t) (~(os::vm_page_size()-1) | (uintptr_t)markWord::lock_mask_in_place));
    // If (mark & lock_mask) == 0 and mark - sp < page_size, we are stack-locking and goto cont,
    // hence we can store 0 as the displaced header in the box, which indicates that it is a
    // recursive lock.
    andr(tmp/*==0?*/, disp_hdr, tmp);
    sd(tmp/*==0, perhaps*/, Address(box, BasicLock::displaced_header_offset_in_bytes()));
    mv(flag, tmp); // we can use the value of tmp as the result here
    j(cont);
  } else {
    assert(LockingMode == LM_LIGHTWEIGHT, "");
    Label slow;
    lightweight_lock(oop, disp_hdr, tmp, tmp3Reg, slow);

    // Indicate success on completion.
    mv(flag, zr);
    j(count);
    bind(slow);
    mv(flag, 1); // Set non-zero flag to indicate 'failure' -> take slow-path
    j(no_count);
  }

  // Handle existing monitor.
  bind(object_has_monitor);
  // The object's monitor m is unlocked iff m->owner == NULL,
  // otherwise m->owner may contain a thread or a stack address.
  //
  // Try to CAS m->owner from NULL to current thread.
  add(tmp, disp_hdr, (in_bytes(ObjectMonitor::owner_offset()) - markWord::monitor_value));
  cmpxchg(/*memory address*/tmp, /*expected value*/zr, /*new value*/xthread, Assembler::int64, Assembler::aq,
          Assembler::rl, /*result*/flag); // cas succeeds if flag == zr(expected)

  if (LockingMode != LM_LIGHTWEIGHT) {
    // Store a non-null value into the box to avoid looking like a re-entrant
    // lock. The fast-path monitor unlock code checks for
    // markWord::monitor_value so use markWord::unused_mark which has the
    // relevant bit set, and also matches ObjectSynchronizer::slow_enter.
    mv(tmp, (address)markWord::unused_mark().value());
    sd(tmp, Address(box, BasicLock::displaced_header_offset_in_bytes()));
  }

  beqz(flag, cont); // CAS success means locking succeeded

  bne(flag, xthread, cont); // Check for recursive locking

  // Recursive lock case
  mv(flag, zr);
  increment(Address(disp_hdr, in_bytes(ObjectMonitor::recursions_offset()) - markWord::monitor_value), 1, t0, tmp);

  bind(cont);
  // zero flag indicates success
  // non-zero flag indicates failure
  bnez(flag, no_count);

  bind(count);
  increment(Address(xthread, JavaThread::held_monitor_count_offset()), 1, t0, tmp);

  bind(no_count);
}

void C2_MacroAssembler::fast_unlock(Register objectReg, Register boxReg,
                                    Register tmp1Reg, Register tmp2Reg) {
  // Use cr register to indicate the fast_unlock result: zero for success; non-zero for failure.
  Register flag = t1;
  Register oop = objectReg;
  Register box = boxReg;
  Register disp_hdr = tmp1Reg;
  Register tmp = tmp2Reg;
  Label cont;
  Label object_has_monitor;
  Label count, no_count;

  assert_different_registers(oop, box, tmp, disp_hdr, flag, t0);

  if (LockingMode == LM_LEGACY) {
    // Find the lock address and load the displaced header from the stack.
    ld(disp_hdr, Address(box, BasicLock::displaced_header_offset_in_bytes()));

    // If the displaced header is 0, we have a recursive unlock.
    mv(flag, disp_hdr);
    beqz(disp_hdr, cont);
  }

  // Handle existing monitor.
  ld(tmp, Address(oop, oopDesc::mark_offset_in_bytes()));
  test_bit(t0, tmp, exact_log2(markWord::monitor_value));
  bnez(t0, object_has_monitor);

  if (LockingMode == LM_MONITOR) {
    mv(flag, 1); // Set non-zero flag to indicate 'failure' -> take slow path
    j(cont);
  } else if (LockingMode == LM_LEGACY) {
    // Check if it is still a light weight lock, this is true if we
    // see the stack address of the basicLock in the markWord of the
    // object.

    cmpxchg(/*memory address*/oop, /*expected value*/box, /*new value*/disp_hdr, Assembler::int64, Assembler::relaxed,
            Assembler::rl, /*result*/tmp);
    xorr(flag, box, tmp); // box == tmp if cas succeeds
    j(cont);
  } else {
    assert(LockingMode == LM_LIGHTWEIGHT, "");
    Label slow;
    lightweight_unlock(oop, tmp, box, disp_hdr, slow);

    // Indicate success on completion.
    mv(flag, zr);
    j(count);
    bind(slow);
    mv(flag, 1); // Set non-zero flag to indicate 'failure' -> take slow path
    j(no_count);
  }

  assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");

  // Handle existing monitor.
  bind(object_has_monitor);
  STATIC_ASSERT(markWord::monitor_value <= INT_MAX);
  add(tmp, tmp, -(int)markWord::monitor_value); // monitor

  if (LockingMode == LM_LIGHTWEIGHT) {
    // If the owner is anonymous, we need to fix it -- in an outline stub.
    Register tmp2 = disp_hdr;
    ld(tmp2, Address(tmp, ObjectMonitor::owner_offset()));
    test_bit(t0, tmp2, exact_log2(ObjectMonitor::ANONYMOUS_OWNER));
    C2HandleAnonOMOwnerStub* stub = new (Compile::current()->comp_arena()) C2HandleAnonOMOwnerStub(tmp, tmp2);
    Compile::current()->output()->add_stub(stub);
    bnez(t0, stub->entry(), /* is_far */ true);
    bind(stub->continuation());
  }

  ld(disp_hdr, Address(tmp, ObjectMonitor::recursions_offset()));

  Label notRecursive;
  beqz(disp_hdr, notRecursive); // Will be 0 if not recursive.

  // Recursive lock
  addi(disp_hdr, disp_hdr, -1);
  sd(disp_hdr, Address(tmp, ObjectMonitor::recursions_offset()));
  mv(flag, zr);
  j(cont);

  bind(notRecursive);
  ld(flag, Address(tmp, ObjectMonitor::EntryList_offset()));
  ld(disp_hdr, Address(tmp, ObjectMonitor::cxq_offset()));
  orr(flag, flag, disp_hdr); // Will be 0 if both are 0.
  bnez(flag, cont);
  // need a release store here
  la(tmp, Address(tmp, ObjectMonitor::owner_offset()));
  membar(MacroAssembler::LoadStore | MacroAssembler::StoreStore);
  sd(zr, Address(tmp)); // set unowned

  bind(cont);
  // zero flag indicates success
  // non-zero flag indicates failure
  bnez(flag, no_count);

  bind(count);
  decrement(Address(xthread, JavaThread::held_monitor_count_offset()), 1, t0, tmp);

  bind(no_count);
}

// short string
// StringUTF16.indexOfChar
// StringLatin1.indexOfChar
void C2_MacroAssembler::string_indexof_char_short(Register str1, Register cnt1,
                                                  Register ch, Register result,
                                                  bool isL)
{
  Register ch1 = t0;
  Register index = t1;

  BLOCK_COMMENT("string_indexof_char_short {");

  Label LOOP, LOOP1, LOOP4, LOOP8;
  Label MATCH,  MATCH1, MATCH2, MATCH3,
        MATCH4, MATCH5, MATCH6, MATCH7, NOMATCH;

  mv(result, -1);
  mv(index, zr);

  bind(LOOP);
  addi(t0, index, 8);
  ble(t0, cnt1, LOOP8);
  addi(t0, index, 4);
  ble(t0, cnt1, LOOP4);
  j(LOOP1);

  bind(LOOP8);
  isL ? lbu(ch1, Address(str1, 0)) : lhu(ch1, Address(str1, 0));
  beq(ch, ch1, MATCH);
  isL ? lbu(ch1, Address(str1, 1)) : lhu(ch1, Address(str1, 2));
  beq(ch, ch1, MATCH1);
  isL ? lbu(ch1, Address(str1, 2)) : lhu(ch1, Address(str1, 4));
  beq(ch, ch1, MATCH2);
  isL ? lbu(ch1, Address(str1, 3)) : lhu(ch1, Address(str1, 6));
  beq(ch, ch1, MATCH3);
  isL ? lbu(ch1, Address(str1, 4)) : lhu(ch1, Address(str1, 8));
  beq(ch, ch1, MATCH4);
  isL ? lbu(ch1, Address(str1, 5)) : lhu(ch1, Address(str1, 10));
  beq(ch, ch1, MATCH5);
  isL ? lbu(ch1, Address(str1, 6)) : lhu(ch1, Address(str1, 12));
  beq(ch, ch1, MATCH6);
  isL ? lbu(ch1, Address(str1, 7)) : lhu(ch1, Address(str1, 14));
  beq(ch, ch1, MATCH7);
  addi(index, index, 8);
  addi(str1, str1, isL ? 8 : 16);
  blt(index, cnt1, LOOP);
  j(NOMATCH);

  bind(LOOP4);
  isL ? lbu(ch1, Address(str1, 0)) : lhu(ch1, Address(str1, 0));
  beq(ch, ch1, MATCH);
  isL ? lbu(ch1, Address(str1, 1)) : lhu(ch1, Address(str1, 2));
  beq(ch, ch1, MATCH1);
  isL ? lbu(ch1, Address(str1, 2)) : lhu(ch1, Address(str1, 4));
  beq(ch, ch1, MATCH2);
  isL ? lbu(ch1, Address(str1, 3)) : lhu(ch1, Address(str1, 6));
  beq(ch, ch1, MATCH3);
  addi(index, index, 4);
  addi(str1, str1, isL ? 4 : 8);
  bge(index, cnt1, NOMATCH);

  bind(LOOP1);
  isL ? lbu(ch1, Address(str1)) : lhu(ch1, Address(str1));
  beq(ch, ch1, MATCH);
  addi(index, index, 1);
  addi(str1, str1, isL ? 1 : 2);
  blt(index, cnt1, LOOP1);
  j(NOMATCH);

  bind(MATCH1);
  addi(index, index, 1);
  j(MATCH);

  bind(MATCH2);
  addi(index, index, 2);
  j(MATCH);

  bind(MATCH3);
  addi(index, index, 3);
  j(MATCH);

  bind(MATCH4);
  addi(index, index, 4);
  j(MATCH);

  bind(MATCH5);
  addi(index, index, 5);
  j(MATCH);

  bind(MATCH6);
  addi(index, index, 6);
  j(MATCH);

  bind(MATCH7);
  addi(index, index, 7);

  bind(MATCH);
  mv(result, index);
  bind(NOMATCH);
  BLOCK_COMMENT("} string_indexof_char_short");
}

// StringUTF16.indexOfChar
// StringLatin1.indexOfChar
void C2_MacroAssembler::string_indexof_char(Register str1, Register cnt1,
                                            Register ch, Register result,
                                            Register tmp1, Register tmp2,
                                            Register tmp3, Register tmp4,
                                            bool isL)
{
  Label CH1_LOOP, HIT, NOMATCH, DONE, DO_LONG;
  Register ch1 = t0;
  Register orig_cnt = t1;
  Register mask1 = tmp3;
  Register mask2 = tmp2;
  Register match_mask = tmp1;
  Register trailing_char = tmp4;
  Register unaligned_elems = tmp4;

  BLOCK_COMMENT("string_indexof_char {");
  beqz(cnt1, NOMATCH);

  addi(t0, cnt1, isL ? -32 : -16);
  bgtz(t0, DO_LONG);
  string_indexof_char_short(str1, cnt1, ch, result, isL);
  j(DONE);

  bind(DO_LONG);
  mv(orig_cnt, cnt1);
  if (AvoidUnalignedAccesses) {
    Label ALIGNED;
    andi(unaligned_elems, str1, 0x7);
    beqz(unaligned_elems, ALIGNED);
    sub(unaligned_elems, unaligned_elems, 8);
    neg(unaligned_elems, unaligned_elems);
    if (!isL) {
      srli(unaligned_elems, unaligned_elems, 1);
    }
    // do unaligned part per element
    string_indexof_char_short(str1, unaligned_elems, ch, result, isL);
    bgez(result, DONE);
    mv(orig_cnt, cnt1);
    sub(cnt1, cnt1, unaligned_elems);
    bind(ALIGNED);
  }

  // duplicate ch
  if (isL) {
    slli(ch1, ch, 8);
    orr(ch, ch1, ch);
  }
  slli(ch1, ch, 16);
  orr(ch, ch1, ch);
  slli(ch1, ch, 32);
  orr(ch, ch1, ch);

  if (!isL) {
    slli(cnt1, cnt1, 1);
  }

  uint64_t mask0101 = UCONST64(0x0101010101010101);
  uint64_t mask0001 = UCONST64(0x0001000100010001);
  mv(mask1, isL ? mask0101 : mask0001);
  uint64_t mask7f7f = UCONST64(0x7f7f7f7f7f7f7f7f);
  uint64_t mask7fff = UCONST64(0x7fff7fff7fff7fff);
  mv(mask2, isL ? mask7f7f : mask7fff);

  bind(CH1_LOOP);
  ld(ch1, Address(str1));
  addi(str1, str1, 8);
  addi(cnt1, cnt1, -8);
  compute_match_mask(ch1, ch, match_mask, mask1, mask2);
  bnez(match_mask, HIT);
  bgtz(cnt1, CH1_LOOP);
  j(NOMATCH);

  bind(HIT);
  ctzc_bit(trailing_char, match_mask, isL, ch1, result);
  srli(trailing_char, trailing_char, 3);
  addi(cnt1, cnt1, 8);
  ble(cnt1, trailing_char, NOMATCH);
  // match case
  if (!isL) {
    srli(cnt1, cnt1, 1);
    srli(trailing_char, trailing_char, 1);
  }

  sub(result, orig_cnt, cnt1);
  add(result, result, trailing_char);
  j(DONE);

  bind(NOMATCH);
  mv(result, -1);

  bind(DONE);
  BLOCK_COMMENT("} string_indexof_char");
}

typedef void (MacroAssembler::* load_chr_insn)(Register rd, const Address &adr, Register temp);

// Search for needle in haystack and return index or -1
// x10: result
// x11: haystack
// x12: haystack_len
// x13: needle
// x14: needle_len
void C2_MacroAssembler::string_indexof(Register haystack, Register needle,
                                       Register haystack_len, Register needle_len,
                                       Register tmp1, Register tmp2,
                                       Register tmp3, Register tmp4,
                                       Register tmp5, Register tmp6,
                                       Register result, int ae)
{
  assert(ae != StrIntrinsicNode::LU, "Invalid encoding");

  Label LINEARSEARCH, LINEARSTUB, DONE, NOMATCH;

  Register ch1 = t0;
  Register ch2 = t1;
  Register nlen_tmp = tmp1; // needle len tmp
  Register hlen_tmp = tmp2; // haystack len tmp
  Register result_tmp = tmp4;

  bool isLL = ae == StrIntrinsicNode::LL;

  bool needle_isL = ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UL;
  bool haystack_isL = ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::LU;
  int needle_chr_shift = needle_isL ? 0 : 1;
  int haystack_chr_shift = haystack_isL ? 0 : 1;
  int needle_chr_size = needle_isL ? 1 : 2;
  int haystack_chr_size = haystack_isL ? 1 : 2;
  load_chr_insn needle_load_1chr = needle_isL ? (load_chr_insn)&MacroAssembler::lbu :
                              (load_chr_insn)&MacroAssembler::lhu;
  load_chr_insn haystack_load_1chr = haystack_isL ? (load_chr_insn)&MacroAssembler::lbu :
                                (load_chr_insn)&MacroAssembler::lhu;

  BLOCK_COMMENT("string_indexof {");

  // Note, inline_string_indexOf() generates checks:
  // if (pattern.count > src.count) return -1;
  // if (pattern.count == 0) return 0;

  // We have two strings, a source string in haystack, haystack_len and a pattern string
  // in needle, needle_len. Find the first occurrence of pattern in source or return -1.

  // For larger pattern and source we use a simplified Boyer Moore algorithm.
  // With a small pattern and source we use linear scan.

  // needle_len >=8 && needle_len < 256 && needle_len < haystack_len/4, use bmh algorithm.
  sub(result_tmp, haystack_len, needle_len);
  // needle_len < 8, use linear scan
  sub(t0, needle_len, 8);
  bltz(t0, LINEARSEARCH);
  // needle_len >= 256, use linear scan
  sub(t0, needle_len, 256);
  bgez(t0, LINEARSTUB);
  // needle_len >= haystack_len/4, use linear scan
  srli(t0, haystack_len, 2);
  bge(needle_len, t0, LINEARSTUB);

  // Boyer-Moore-Horspool introduction:
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
  // This is also known as the Boyer-Moore-Horspool algorithm:
  //
  // http://en.wikipedia.org/wiki/Boyer-Moore-Horspool_algorithm
  //
  // #define ASIZE 256
  //
  //    int bm(unsigned char *pattern, int m, unsigned char *src, int n) {
  //      int i, j;
  //      unsigned c;
  //      unsigned char bc[ASIZE];
  //
  //      /* Preprocessing */
  //      for (i = 0; i < ASIZE; ++i)
  //        bc[i] = m;
  //      for (i = 0; i < m - 1; ) {
  //        c = pattern[i];
  //        ++i;
  //        // c < 256 for Latin1 string, so, no need for branch
  //        #ifdef PATTERN_STRING_IS_LATIN1
  //        bc[c] = m - i;
  //        #else
  //        if (c < ASIZE) bc[c] = m - i;
  //        #endif
  //      }
  //
  //      /* Searching */
  //      j = 0;
  //      while (j <= n - m) {
  //        c = src[i+j];
  //        if (pattern[m-1] == c)
  //          int k;
  //          for (k = m - 2; k >= 0 && pattern[k] == src[k + j]; --k);
  //          if (k < 0) return j;
  //          // c < 256 for Latin1 string, so, no need for branch
  //          #ifdef SOURCE_STRING_IS_LATIN1_AND_PATTERN_STRING_IS_LATIN1
  //          // LL case: (c< 256) always true. Remove branch
  //          j += bc[pattern[j+m-1]];
  //          #endif
  //          #ifdef SOURCE_STRING_IS_UTF_AND_PATTERN_STRING_IS_UTF
  //          // UU case: need if (c<ASIZE) check. Skip 1 character if not.
  //          if (c < ASIZE)
  //            j += bc[pattern[j+m-1]];
  //          else
  //            j += 1
  //          #endif
  //          #ifdef SOURCE_IS_UTF_AND_PATTERN_IS_LATIN1
  //          // UL case: need if (c<ASIZE) check. Skip <pattern length> if not.
  //          if (c < ASIZE)
  //            j += bc[pattern[j+m-1]];
  //          else
  //            j += m
  //          #endif
  //      }
  //      return -1;
  //    }

  // temp register:t0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, result
  Label BCLOOP, BCSKIP, BMLOOPSTR2, BMLOOPSTR1, BMSKIP, BMADV, BMMATCH,
        BMLOOPSTR1_LASTCMP, BMLOOPSTR1_CMP, BMLOOPSTR1_AFTER_LOAD, BM_INIT_LOOP;

  Register haystack_end = haystack_len;
  Register skipch = tmp2;

  // pattern length is >=8, so, we can read at least 1 register for cases when
  // UTF->Latin1 conversion is not needed(8 LL or 4UU) and half register for
  // UL case. We'll re-read last character in inner pre-loop code to have
  // single outer pre-loop load
  const int firstStep = isLL ? 7 : 3;

  const int ASIZE = 256;
  const int STORE_BYTES = 8; // 8 bytes stored per instruction(sd)

  sub(sp, sp, ASIZE);

  // init BC offset table with default value: needle_len
  slli(t0, needle_len, 8);
  orr(t0, t0, needle_len); // [63...16][needle_len][needle_len]
  slli(tmp1, t0, 16);
  orr(t0, tmp1, t0); // [63...32][needle_len][needle_len][needle_len][needle_len]
  slli(tmp1, t0, 32);
  orr(tmp5, tmp1, t0); // tmp5: 8 elements [needle_len]

  mv(ch1, sp);  // ch1 is t0
  mv(tmp6, ASIZE / STORE_BYTES); // loop iterations

  bind(BM_INIT_LOOP);
  // for (i = 0; i < ASIZE; ++i)
  //   bc[i] = m;
  for (int i = 0; i < 4; i++) {
    sd(tmp5, Address(ch1, i * wordSize));
  }
  add(ch1, ch1, 32);
  sub(tmp6, tmp6, 4);
  bgtz(tmp6, BM_INIT_LOOP);

  sub(nlen_tmp, needle_len, 1); // m - 1, index of the last element in pattern
  Register orig_haystack = tmp5;
  mv(orig_haystack, haystack);
  // result_tmp = tmp4
  shadd(haystack_end, result_tmp, haystack, haystack_end, haystack_chr_shift);
  sub(ch2, needle_len, 1); // bc offset init value, ch2 is t1
  mv(tmp3, needle);

  //  for (i = 0; i < m - 1; ) {
  //    c = pattern[i];
  //    ++i;
  //    // c < 256 for Latin1 string, so, no need for branch
  //    #ifdef PATTERN_STRING_IS_LATIN1
  //    bc[c] = m - i;
  //    #else
  //    if (c < ASIZE) bc[c] = m - i;
  //    #endif
  //  }
  bind(BCLOOP);
  (this->*needle_load_1chr)(ch1, Address(tmp3), noreg);
  add(tmp3, tmp3, needle_chr_size);
  if (!needle_isL) {
    // ae == StrIntrinsicNode::UU
    mv(tmp6, ASIZE);
    bgeu(ch1, tmp6, BCSKIP);
  }
  add(tmp4, sp, ch1);
  sb(ch2, Address(tmp4)); // store skip offset to BC offset table

  bind(BCSKIP);
  sub(ch2, ch2, 1); // for next pattern element, skip distance -1
  bgtz(ch2, BCLOOP);

  // tmp6: pattern end, address after needle
  shadd(tmp6, needle_len, needle, tmp6, needle_chr_shift);
  if (needle_isL == haystack_isL) {
    // load last 8 bytes (8LL/4UU symbols)
    ld(tmp6, Address(tmp6, -wordSize));
  } else {
    // UL: from UTF-16(source) search Latin1(pattern)
    lwu(tmp6, Address(tmp6, -wordSize / 2)); // load last 4 bytes(4 symbols)
    // convert Latin1 to UTF. eg: 0x0000abcd -> 0x0a0b0c0d
    // We'll have to wait until load completed, but it's still faster than per-character loads+checks
    srli(tmp3, tmp6, BitsPerByte * (wordSize / 2 - needle_chr_size)); // pattern[m-1], eg:0x0000000a
    slli(ch2, tmp6, XLEN - 24);
    srli(ch2, ch2, XLEN - 8); // pattern[m-2], 0x0000000b
    slli(ch1, tmp6, XLEN - 16);
    srli(ch1, ch1, XLEN - 8); // pattern[m-3], 0x0000000c
    andi(tmp6, tmp6, 0xff); // pattern[m-4], 0x0000000d
    slli(ch2, ch2, 16);
    orr(ch2, ch2, ch1); // 0x00000b0c
    slli(result, tmp3, 48); // use result as temp register
    orr(tmp6, tmp6, result); // 0x0a00000d
    slli(result, ch2, 16);
    orr(tmp6, tmp6, result); // UTF-16:0x0a0b0c0d
  }

  // i = m - 1;
  // skipch = j + i;
  // if (skipch == pattern[m - 1]
  //   for (k = m - 2; k >= 0 && pattern[k] == src[k + j]; --k);
  // else
  //   move j with bad char offset table
  bind(BMLOOPSTR2);
  // compare pattern to source string backward
  shadd(result, nlen_tmp, haystack, result, haystack_chr_shift);
  (this->*haystack_load_1chr)(skipch, Address(result), noreg);
  sub(nlen_tmp, nlen_tmp, firstStep); // nlen_tmp is positive here, because needle_len >= 8
  if (needle_isL == haystack_isL) {
    // re-init tmp3. It's for free because it's executed in parallel with
    // load above. Alternative is to initialize it before loop, but it'll
    // affect performance on in-order systems with 2 or more ld/st pipelines
    srli(tmp3, tmp6, BitsPerByte * (wordSize - needle_chr_size)); // UU/LL: pattern[m-1]
  }
  if (!isLL) { // UU/UL case
    slli(ch2, nlen_tmp, 1); // offsets in bytes
  }
  bne(tmp3, skipch, BMSKIP); // if not equal, skipch is bad char
  add(result, haystack, isLL ? nlen_tmp : ch2);
  // load 8 bytes from source string
  // if isLL is false then read granularity can be 2
  load_long_misaligned(ch2, Address(result), ch1, isLL ? 1 : 2); // can use ch1 as temp register here as it will be trashed by next mv anyway
  mv(ch1, tmp6);
  if (isLL) {
    j(BMLOOPSTR1_AFTER_LOAD);
  } else {
    sub(nlen_tmp, nlen_tmp, 1); // no need to branch for UU/UL case. cnt1 >= 8
    j(BMLOOPSTR1_CMP);
  }

  bind(BMLOOPSTR1);
  shadd(ch1, nlen_tmp, needle, ch1, needle_chr_shift);
  (this->*needle_load_1chr)(ch1, Address(ch1), noreg);
  shadd(ch2, nlen_tmp, haystack, ch2, haystack_chr_shift);
  (this->*haystack_load_1chr)(ch2, Address(ch2), noreg);

  bind(BMLOOPSTR1_AFTER_LOAD);
  sub(nlen_tmp, nlen_tmp, 1);
  bltz(nlen_tmp, BMLOOPSTR1_LASTCMP);

  bind(BMLOOPSTR1_CMP);
  beq(ch1, ch2, BMLOOPSTR1);

  bind(BMSKIP);
  if (!isLL) {
    // if we've met UTF symbol while searching Latin1 pattern, then we can
    // skip needle_len symbols
    if (needle_isL != haystack_isL) {
      mv(result_tmp, needle_len);
    } else {
      mv(result_tmp, 1);
    }
    mv(t0, ASIZE);
    bgeu(skipch, t0, BMADV);
  }
  add(result_tmp, sp, skipch);
  lbu(result_tmp, Address(result_tmp)); // load skip offset

  bind(BMADV);
  sub(nlen_tmp, needle_len, 1);
  // move haystack after bad char skip offset
  shadd(haystack, result_tmp, haystack, result, haystack_chr_shift);
  ble(haystack, haystack_end, BMLOOPSTR2);
  add(sp, sp, ASIZE);
  j(NOMATCH);

  bind(BMLOOPSTR1_LASTCMP);
  bne(ch1, ch2, BMSKIP);

  bind(BMMATCH);
  sub(result, haystack, orig_haystack);
  if (!haystack_isL) {
    srli(result, result, 1);
  }
  add(sp, sp, ASIZE);
  j(DONE);

  bind(LINEARSTUB);
  sub(t0, needle_len, 16); // small patterns still should be handled by simple algorithm
  bltz(t0, LINEARSEARCH);
  mv(result, zr);
  RuntimeAddress stub = nullptr;
  if (isLL) {
    stub = RuntimeAddress(StubRoutines::riscv::string_indexof_linear_ll());
    assert(stub.target() != nullptr, "string_indexof_linear_ll stub has not been generated");
  } else if (needle_isL) {
    stub = RuntimeAddress(StubRoutines::riscv::string_indexof_linear_ul());
    assert(stub.target() != nullptr, "string_indexof_linear_ul stub has not been generated");
  } else {
    stub = RuntimeAddress(StubRoutines::riscv::string_indexof_linear_uu());
    assert(stub.target() != nullptr, "string_indexof_linear_uu stub has not been generated");
  }
  address call = trampoline_call(stub);
  if (call == nullptr) {
    DEBUG_ONLY(reset_labels(LINEARSEARCH, DONE, NOMATCH));
    ciEnv::current()->record_failure("CodeCache is full");
    return;
  }
  j(DONE);

  bind(NOMATCH);
  mv(result, -1);
  j(DONE);

  bind(LINEARSEARCH);
  string_indexof_linearscan(haystack, needle, haystack_len, needle_len, tmp1, tmp2, tmp3, tmp4, -1, result, ae);

  bind(DONE);
  BLOCK_COMMENT("} string_indexof");
}

// string_indexof
// result: x10
// src: x11
// src_count: x12
// pattern: x13
// pattern_count: x14 or 1/2/3/4
void C2_MacroAssembler::string_indexof_linearscan(Register haystack, Register needle,
                                               Register haystack_len, Register needle_len,
                                               Register tmp1, Register tmp2,
                                               Register tmp3, Register tmp4,
                                               int needle_con_cnt, Register result, int ae)
{
  // Note:
  // needle_con_cnt > 0 means needle_len register is invalid, needle length is constant
  // for UU/LL: needle_con_cnt[1, 4], UL: needle_con_cnt = 1
  assert(needle_con_cnt <= 4, "Invalid needle constant count");
  assert(ae != StrIntrinsicNode::LU, "Invalid encoding");

  Register ch1 = t0;
  Register ch2 = t1;
  Register hlen_neg = haystack_len, nlen_neg = needle_len;
  Register nlen_tmp = tmp1, hlen_tmp = tmp2, result_tmp = tmp4;

  bool isLL = ae == StrIntrinsicNode::LL;

  bool needle_isL = ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UL;
  bool haystack_isL = ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::LU;
  int needle_chr_shift = needle_isL ? 0 : 1;
  int haystack_chr_shift = haystack_isL ? 0 : 1;
  int needle_chr_size = needle_isL ? 1 : 2;
  int haystack_chr_size = haystack_isL ? 1 : 2;

  load_chr_insn needle_load_1chr = needle_isL ? (load_chr_insn)&MacroAssembler::lbu :
                              (load_chr_insn)&MacroAssembler::lhu;
  load_chr_insn haystack_load_1chr = haystack_isL ? (load_chr_insn)&MacroAssembler::lbu :
                                (load_chr_insn)&MacroAssembler::lhu;
  load_chr_insn load_2chr = isLL ? (load_chr_insn)&MacroAssembler::lhu : (load_chr_insn)&MacroAssembler::lwu;
  load_chr_insn load_4chr = isLL ? (load_chr_insn)&MacroAssembler::lwu : (load_chr_insn)&MacroAssembler::ld;

  Label DO1, DO2, DO3, MATCH, NOMATCH, DONE;

  Register first = tmp3;

  if (needle_con_cnt == -1) {
    Label DOSHORT, FIRST_LOOP, STR2_NEXT, STR1_LOOP, STR1_NEXT;

    sub(t0, needle_len, needle_isL == haystack_isL ? 4 : 2);
    bltz(t0, DOSHORT);

    (this->*needle_load_1chr)(first, Address(needle), noreg);
    slli(t0, needle_len, needle_chr_shift);
    add(needle, needle, t0);
    neg(nlen_neg, t0);
    slli(t0, result_tmp, haystack_chr_shift);
    add(haystack, haystack, t0);
    neg(hlen_neg, t0);

    bind(FIRST_LOOP);
    add(t0, haystack, hlen_neg);
    (this->*haystack_load_1chr)(ch2, Address(t0), noreg);
    beq(first, ch2, STR1_LOOP);

    bind(STR2_NEXT);
    add(hlen_neg, hlen_neg, haystack_chr_size);
    blez(hlen_neg, FIRST_LOOP);
    j(NOMATCH);

    bind(STR1_LOOP);
    add(nlen_tmp, nlen_neg, needle_chr_size);
    add(hlen_tmp, hlen_neg, haystack_chr_size);
    bgez(nlen_tmp, MATCH);

    bind(STR1_NEXT);
    add(ch1, needle, nlen_tmp);
    (this->*needle_load_1chr)(ch1, Address(ch1), noreg);
    add(ch2, haystack, hlen_tmp);
    (this->*haystack_load_1chr)(ch2, Address(ch2), noreg);
    bne(ch1, ch2, STR2_NEXT);
    add(nlen_tmp, nlen_tmp, needle_chr_size);
    add(hlen_tmp, hlen_tmp, haystack_chr_size);
    bltz(nlen_tmp, STR1_NEXT);
    j(MATCH);

    bind(DOSHORT);
    if (needle_isL == haystack_isL) {
      sub(t0, needle_len, 2);
      bltz(t0, DO1);
      bgtz(t0, DO3);
    }
  }

  if (needle_con_cnt == 4) {
    Label CH1_LOOP;
    (this->*load_4chr)(ch1, Address(needle), noreg);
    sub(result_tmp, haystack_len, 4);
    slli(tmp3, result_tmp, haystack_chr_shift); // result as tmp
    add(haystack, haystack, tmp3);
    neg(hlen_neg, tmp3);
    if (AvoidUnalignedAccesses) {
      // preload first value, then we will read by 1 character per loop, instead of four
      // just shifting previous ch2 right by size of character in bits
      add(tmp3, haystack, hlen_neg);
      (this->*load_4chr)(ch2, Address(tmp3), noreg);
      if (isLL) {
        // need to erase 1 most significant byte in 32-bit value of ch2
        slli(ch2, ch2, 40);
        srli(ch2, ch2, 32);
      } else {
        slli(ch2, ch2, 16); // 2 most significant bytes will be erased by this operation
      }
    }

    bind(CH1_LOOP);
    add(tmp3, haystack, hlen_neg);
    if (AvoidUnalignedAccesses) {
      srli(ch2, ch2, isLL ? 8 : 16);
      (this->*haystack_load_1chr)(tmp3, Address(tmp3, isLL ? 3 : 6), noreg);
      slli(tmp3, tmp3, isLL ? 24 : 48);
      add(ch2, ch2, tmp3);
    } else {
      (this->*load_4chr)(ch2, Address(tmp3), noreg);
    }
    beq(ch1, ch2, MATCH);
    add(hlen_neg, hlen_neg, haystack_chr_size);
    blez(hlen_neg, CH1_LOOP);
    j(NOMATCH);
  }

  if ((needle_con_cnt == -1 && needle_isL == haystack_isL) || needle_con_cnt == 2) {
    Label CH1_LOOP;
    BLOCK_COMMENT("string_indexof DO2 {");
    bind(DO2);
    (this->*load_2chr)(ch1, Address(needle), noreg);
    if (needle_con_cnt == 2) {
      sub(result_tmp, haystack_len, 2);
    }
    slli(tmp3, result_tmp, haystack_chr_shift);
    add(haystack, haystack, tmp3);
    neg(hlen_neg, tmp3);
    if (AvoidUnalignedAccesses) {
      // preload first value, then we will read by 1 character per loop, instead of two
      // just shifting previous ch2 right by size of character in bits
      add(tmp3, haystack, hlen_neg);
      (this->*haystack_load_1chr)(ch2, Address(tmp3), noreg);
      slli(ch2, ch2, isLL ? 8 : 16);
    }
    bind(CH1_LOOP);
    add(tmp3, haystack, hlen_neg);
    if (AvoidUnalignedAccesses) {
      srli(ch2, ch2, isLL ? 8 : 16);
      (this->*haystack_load_1chr)(tmp3, Address(tmp3, isLL ? 1 : 2), noreg);
      slli(tmp3, tmp3, isLL ? 8 : 16);
      add(ch2, ch2, tmp3);
    } else {
      (this->*load_2chr)(ch2, Address(tmp3), noreg);
    }
    beq(ch1, ch2, MATCH);
    add(hlen_neg, hlen_neg, haystack_chr_size);
    blez(hlen_neg, CH1_LOOP);
    j(NOMATCH);
    BLOCK_COMMENT("} string_indexof DO2");
  }

  if ((needle_con_cnt == -1 && needle_isL == haystack_isL) || needle_con_cnt == 3) {
    Label FIRST_LOOP, STR2_NEXT, STR1_LOOP;
    BLOCK_COMMENT("string_indexof DO3 {");

    bind(DO3);
    (this->*load_2chr)(first, Address(needle), noreg);
    (this->*needle_load_1chr)(ch1, Address(needle, 2 * needle_chr_size), noreg);
    if (needle_con_cnt == 3) {
      sub(result_tmp, haystack_len, 3);
    }
    slli(hlen_tmp, result_tmp, haystack_chr_shift);
    add(haystack, haystack, hlen_tmp);
    neg(hlen_neg, hlen_tmp);

    bind(FIRST_LOOP);
    add(ch2, haystack, hlen_neg);
    if (AvoidUnalignedAccesses) {
      (this->*haystack_load_1chr)(tmp2, Address(ch2, isLL ? 1 : 2), noreg); // we need a temp register, we can safely use hlen_tmp here, which is a synonym for tmp2
      (this->*haystack_load_1chr)(ch2, Address(ch2), noreg);
      slli(tmp2, tmp2, isLL ? 8 : 16);
      add(ch2, ch2, tmp2);
    } else {
      (this->*load_2chr)(ch2, Address(ch2), noreg);
    }
    beq(first, ch2, STR1_LOOP);

    bind(STR2_NEXT);
    add(hlen_neg, hlen_neg, haystack_chr_size);
    blez(hlen_neg, FIRST_LOOP);
    j(NOMATCH);

    bind(STR1_LOOP);
    add(hlen_tmp, hlen_neg, 2 * haystack_chr_size);
    add(ch2, haystack, hlen_tmp);
    (this->*haystack_load_1chr)(ch2, Address(ch2), noreg);
    bne(ch1, ch2, STR2_NEXT);
    j(MATCH);
    BLOCK_COMMENT("} string_indexof DO3");
  }

  if (needle_con_cnt == -1 || needle_con_cnt == 1) {
    Label DO1_LOOP;

    BLOCK_COMMENT("string_indexof DO1 {");
    bind(DO1);
    (this->*needle_load_1chr)(ch1, Address(needle), noreg);
    sub(result_tmp, haystack_len, 1);
    slli(tmp3, result_tmp, haystack_chr_shift);
    add(haystack, haystack, tmp3);
    neg(hlen_neg, tmp3);

    bind(DO1_LOOP);
    add(tmp3, haystack, hlen_neg);
    (this->*haystack_load_1chr)(ch2, Address(tmp3), noreg);
    beq(ch1, ch2, MATCH);
    add(hlen_neg, hlen_neg, haystack_chr_size);
    blez(hlen_neg, DO1_LOOP);
    BLOCK_COMMENT("} string_indexof DO1");
  }

  bind(NOMATCH);
  mv(result, -1);
  j(DONE);

  bind(MATCH);
  srai(t0, hlen_neg, haystack_chr_shift);
  add(result, result_tmp, t0);

  bind(DONE);
}

// Compare strings.
void C2_MacroAssembler::string_compare(Register str1, Register str2,
                                       Register cnt1, Register cnt2, Register result,
                                       Register tmp1, Register tmp2, Register tmp3,
                                       int ae)
{
  Label DONE, SHORT_LOOP, SHORT_STRING, SHORT_LAST, TAIL, STUB,
        DIFFERENCE, NEXT_WORD, SHORT_LOOP_TAIL, SHORT_LAST2, SHORT_LAST_INIT,
        SHORT_LOOP_START, TAIL_CHECK, L;

  const int STUB_THRESHOLD = 64 + 8;
  bool isLL = ae == StrIntrinsicNode::LL;
  bool isLU = ae == StrIntrinsicNode::LU;
  bool isUL = ae == StrIntrinsicNode::UL;

  bool str1_isL = isLL || isLU;
  bool str2_isL = isLL || isUL;

  // for L strings, 1 byte for 1 character
  // for U strings, 2 bytes for 1 character
  int str1_chr_size = str1_isL ? 1 : 2;
  int str2_chr_size = str2_isL ? 1 : 2;
  int minCharsInWord = isLL ? wordSize : wordSize / 2;

  load_chr_insn str1_load_chr = str1_isL ? (load_chr_insn)&MacroAssembler::lbu : (load_chr_insn)&MacroAssembler::lhu;
  load_chr_insn str2_load_chr = str2_isL ? (load_chr_insn)&MacroAssembler::lbu : (load_chr_insn)&MacroAssembler::lhu;

  BLOCK_COMMENT("string_compare {");

  // Bizzarely, the counts are passed in bytes, regardless of whether they
  // are L or U strings, however the result is always in characters.
  if (!str1_isL) {
    sraiw(cnt1, cnt1, 1);
  }
  if (!str2_isL) {
    sraiw(cnt2, cnt2, 1);
  }

  // Compute the minimum of the string lengths and save the difference in result.
  sub(result, cnt1, cnt2);
  bgt(cnt1, cnt2, L);
  mv(cnt2, cnt1);
  bind(L);

  // A very short string
  mv(t0, minCharsInWord);
  ble(cnt2, t0, SHORT_STRING);

  // Compare longwords
  // load first parts of strings and finish initialization while loading
  {
    if (str1_isL == str2_isL) { // LL or UU
      // check if str1 and str2 is same pointer
      beq(str1, str2, DONE);
      // load 8 bytes once to compare
      ld(tmp1, Address(str1));
      ld(tmp2, Address(str2));
      mv(t0, STUB_THRESHOLD);
      bge(cnt2, t0, STUB);
      sub(cnt2, cnt2, minCharsInWord);
      beqz(cnt2, TAIL_CHECK);
      // convert cnt2 from characters to bytes
      if (!str1_isL) {
        slli(cnt2, cnt2, 1);
      }
      add(str2, str2, cnt2);
      add(str1, str1, cnt2);
      sub(cnt2, zr, cnt2);
    } else if (isLU) { // LU case
      lwu(tmp1, Address(str1));
      ld(tmp2, Address(str2));
      mv(t0, STUB_THRESHOLD);
      bge(cnt2, t0, STUB);
      addi(cnt2, cnt2, -4);
      add(str1, str1, cnt2);
      sub(cnt1, zr, cnt2);
      slli(cnt2, cnt2, 1);
      add(str2, str2, cnt2);
      inflate_lo32(tmp3, tmp1);
      mv(tmp1, tmp3);
      sub(cnt2, zr, cnt2);
      addi(cnt1, cnt1, 4);
    } else { // UL case
      ld(tmp1, Address(str1));
      lwu(tmp2, Address(str2));
      mv(t0, STUB_THRESHOLD);
      bge(cnt2, t0, STUB);
      addi(cnt2, cnt2, -4);
      slli(t0, cnt2, 1);
      sub(cnt1, zr, t0);
      add(str1, str1, t0);
      add(str2, str2, cnt2);
      inflate_lo32(tmp3, tmp2);
      mv(tmp2, tmp3);
      sub(cnt2, zr, cnt2);
      addi(cnt1, cnt1, 8);
    }
    addi(cnt2, cnt2, isUL ? 4 : 8);
    bne(tmp1, tmp2, DIFFERENCE);
    bgez(cnt2, TAIL);

    // main loop
    bind(NEXT_WORD);
    if (str1_isL == str2_isL) { // LL or UU
      add(t0, str1, cnt2);
      ld(tmp1, Address(t0));
      add(t0, str2, cnt2);
      ld(tmp2, Address(t0));
      addi(cnt2, cnt2, 8);
    } else if (isLU) { // LU case
      add(t0, str1, cnt1);
      lwu(tmp1, Address(t0));
      add(t0, str2, cnt2);
      ld(tmp2, Address(t0));
      addi(cnt1, cnt1, 4);
      inflate_lo32(tmp3, tmp1);
      mv(tmp1, tmp3);
      addi(cnt2, cnt2, 8);
    } else { // UL case
      add(t0, str2, cnt2);
      lwu(tmp2, Address(t0));
      add(t0, str1, cnt1);
      ld(tmp1, Address(t0));
      inflate_lo32(tmp3, tmp2);
      mv(tmp2, tmp3);
      addi(cnt1, cnt1, 8);
      addi(cnt2, cnt2, 4);
    }
    bne(tmp1, tmp2, DIFFERENCE);
    bltz(cnt2, NEXT_WORD);
    bind(TAIL);
    if (str1_isL == str2_isL) { // LL or UU
      load_long_misaligned(tmp1, Address(str1), tmp3, isLL ? 1 : 2);
      load_long_misaligned(tmp2, Address(str2), tmp3, isLL ? 1 : 2);
    } else if (isLU) { // LU case
      load_int_misaligned(tmp1, Address(str1), tmp3, false);
      load_long_misaligned(tmp2, Address(str2), tmp3, 2);
      inflate_lo32(tmp3, tmp1);
      mv(tmp1, tmp3);
    } else { // UL case
      load_int_misaligned(tmp2, Address(str2), tmp3, false);
      load_long_misaligned(tmp1, Address(str1), tmp3, 2);
      inflate_lo32(tmp3, tmp2);
      mv(tmp2, tmp3);
    }
    bind(TAIL_CHECK);
    beq(tmp1, tmp2, DONE);

    // Find the first different characters in the longwords and
    // compute their difference.
    bind(DIFFERENCE);
    xorr(tmp3, tmp1, tmp2);
    ctzc_bit(result, tmp3, isLL); // count zero from lsb to msb
    srl(tmp1, tmp1, result);
    srl(tmp2, tmp2, result);
    if (isLL) {
      andi(tmp1, tmp1, 0xFF);
      andi(tmp2, tmp2, 0xFF);
    } else {
      andi(tmp1, tmp1, 0xFFFF);
      andi(tmp2, tmp2, 0xFFFF);
    }
    sub(result, tmp1, tmp2);
    j(DONE);
  }

  bind(STUB);
  RuntimeAddress stub = nullptr;
  switch (ae) {
    case StrIntrinsicNode::LL:
      stub = RuntimeAddress(StubRoutines::riscv::compare_long_string_LL());
      break;
    case StrIntrinsicNode::UU:
      stub = RuntimeAddress(StubRoutines::riscv::compare_long_string_UU());
      break;
    case StrIntrinsicNode::LU:
      stub = RuntimeAddress(StubRoutines::riscv::compare_long_string_LU());
      break;
    case StrIntrinsicNode::UL:
      stub = RuntimeAddress(StubRoutines::riscv::compare_long_string_UL());
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
  j(DONE);

  bind(SHORT_STRING);
  // Is the minimum length zero?
  beqz(cnt2, DONE);
  // arrange code to do most branches while loading and loading next characters
  // while comparing previous
  (this->*str1_load_chr)(tmp1, Address(str1), t0);
  addi(str1, str1, str1_chr_size);
  addi(cnt2, cnt2, -1);
  beqz(cnt2, SHORT_LAST_INIT);
  (this->*str2_load_chr)(cnt1, Address(str2), t0);
  addi(str2, str2, str2_chr_size);
  j(SHORT_LOOP_START);
  bind(SHORT_LOOP);
  addi(cnt2, cnt2, -1);
  beqz(cnt2, SHORT_LAST);
  bind(SHORT_LOOP_START);
  (this->*str1_load_chr)(tmp2, Address(str1), t0);
  addi(str1, str1, str1_chr_size);
  (this->*str2_load_chr)(t0, Address(str2), t0);
  addi(str2, str2, str2_chr_size);
  bne(tmp1, cnt1, SHORT_LOOP_TAIL);
  addi(cnt2, cnt2, -1);
  beqz(cnt2, SHORT_LAST2);
  (this->*str1_load_chr)(tmp1, Address(str1), t0);
  addi(str1, str1, str1_chr_size);
  (this->*str2_load_chr)(cnt1, Address(str2), t0);
  addi(str2, str2, str2_chr_size);
  beq(tmp2, t0, SHORT_LOOP);
  sub(result, tmp2, t0);
  j(DONE);
  bind(SHORT_LOOP_TAIL);
  sub(result, tmp1, cnt1);
  j(DONE);
  bind(SHORT_LAST2);
  beq(tmp2, t0, DONE);
  sub(result, tmp2, t0);

  j(DONE);
  bind(SHORT_LAST_INIT);
  (this->*str2_load_chr)(cnt1, Address(str2), t0);
  addi(str2, str2, str2_chr_size);
  bind(SHORT_LAST);
  beq(tmp1, cnt1, DONE);
  sub(result, tmp1, cnt1);

  bind(DONE);

  BLOCK_COMMENT("} string_compare");
}

void C2_MacroAssembler::arrays_equals(Register a1, Register a2, Register tmp3,
                                      Register tmp4, Register tmp5, Register tmp6, Register result,
                                      Register cnt1, int elem_size) {
  Label DONE, SAME, NEXT_DWORD, SHORT, TAIL, TAIL2, IS_TMP5_ZR;
  Register tmp1 = t0;
  Register tmp2 = t1;
  Register cnt2 = tmp2;  // cnt2 only used in array length compare
  Register elem_per_word = tmp6;
  int log_elem_size = exact_log2(elem_size);
  int length_offset = arrayOopDesc::length_offset_in_bytes();
  int base_offset   = arrayOopDesc::base_offset_in_bytes(elem_size == 2 ? T_CHAR : T_BYTE);

  assert(elem_size == 1 || elem_size == 2, "must be char or byte");
  assert_different_registers(a1, a2, result, cnt1, t0, t1, tmp3, tmp4, tmp5, tmp6);
  mv(elem_per_word, wordSize / elem_size);

  BLOCK_COMMENT("arrays_equals {");

  // if (a1 == a2), return true
  beq(a1, a2, SAME);

  mv(result, false);
  beqz(a1, DONE);
  beqz(a2, DONE);
  lwu(cnt1, Address(a1, length_offset));
  lwu(cnt2, Address(a2, length_offset));
  bne(cnt2, cnt1, DONE);
  beqz(cnt1, SAME);

  slli(tmp5, cnt1, 3 + log_elem_size);
  sub(tmp5, zr, tmp5);
  add(a1, a1, base_offset);
  add(a2, a2, base_offset);
  ld(tmp3, Address(a1, 0));
  ld(tmp4, Address(a2, 0));
  ble(cnt1, elem_per_word, SHORT); // short or same

  // Main 16 byte comparison loop with 2 exits
  bind(NEXT_DWORD); {
    ld(tmp1, Address(a1, wordSize));
    ld(tmp2, Address(a2, wordSize));
    sub(cnt1, cnt1, 2 * wordSize / elem_size);
    blez(cnt1, TAIL);
    bne(tmp3, tmp4, DONE);
    ld(tmp3, Address(a1, 2 * wordSize));
    ld(tmp4, Address(a2, 2 * wordSize));
    add(a1, a1, 2 * wordSize);
    add(a2, a2, 2 * wordSize);
    ble(cnt1, elem_per_word, TAIL2);
  } beq(tmp1, tmp2, NEXT_DWORD);
  j(DONE);

  bind(TAIL);
  xorr(tmp4, tmp3, tmp4);
  xorr(tmp2, tmp1, tmp2);
  sll(tmp2, tmp2, tmp5);
  orr(tmp5, tmp4, tmp2);
  j(IS_TMP5_ZR);

  bind(TAIL2);
  bne(tmp1, tmp2, DONE);

  bind(SHORT);
  xorr(tmp4, tmp3, tmp4);
  sll(tmp5, tmp4, tmp5);

  bind(IS_TMP5_ZR);
  bnez(tmp5, DONE);

  bind(SAME);
  mv(result, true);
  // That's it.
  bind(DONE);

  BLOCK_COMMENT("} array_equals");
}

// Compare Strings

// For Strings we're passed the address of the first characters in a1
// and a2 and the length in cnt1.
// elem_size is the element size in bytes: either 1 or 2.
// There are two implementations.  For arrays >= 8 bytes, all
// comparisons (for hw supporting unaligned access: including the final one,
// which may overlap) are performed 8 bytes at a time.
// For strings < 8 bytes (and for tails of long strings when
// AvoidUnalignedAccesses is true), we compare a
// halfword, then a short, and then a byte.

void C2_MacroAssembler::string_equals(Register a1, Register a2,
                                      Register result, Register cnt1, int elem_size)
{
  Label SAME, DONE, SHORT, NEXT_WORD;
  Register tmp1 = t0;
  Register tmp2 = t1;

  assert(elem_size == 1 || elem_size == 2, "must be 2 or 1 byte");
  assert_different_registers(a1, a2, result, cnt1, tmp1, tmp2);

  BLOCK_COMMENT("string_equals {");

  beqz(cnt1, SAME);
  mv(result, false);

  // Check for short strings, i.e. smaller than wordSize.
  sub(cnt1, cnt1, wordSize);
  bltz(cnt1, SHORT);

  // Main 8 byte comparison loop.
  bind(NEXT_WORD); {
    ld(tmp1, Address(a1, 0));
    add(a1, a1, wordSize);
    ld(tmp2, Address(a2, 0));
    add(a2, a2, wordSize);
    sub(cnt1, cnt1, wordSize);
    bne(tmp1, tmp2, DONE);
  } bgez(cnt1, NEXT_WORD);

  if (!AvoidUnalignedAccesses) {
    // Last longword.  In the case where length == 4 we compare the
    // same longword twice, but that's still faster than another
    // conditional branch.
    // cnt1 could be 0, -1, -2, -3, -4 for chars; -4 only happens when
    // length == 4.
    add(tmp1, a1, cnt1);
    ld(tmp1, Address(tmp1, 0));
    add(tmp2, a2, cnt1);
    ld(tmp2, Address(tmp2, 0));
    bne(tmp1, tmp2, DONE);
    j(SAME);
  } else {
    add(tmp1, cnt1, wordSize);
    beqz(tmp1, SAME);
  }

  bind(SHORT);
  Label TAIL03, TAIL01;

  // 0-7 bytes left.
  test_bit(tmp1, cnt1, 2);
  beqz(tmp1, TAIL03);
  {
    lwu(tmp1, Address(a1, 0));
    add(a1, a1, 4);
    lwu(tmp2, Address(a2, 0));
    add(a2, a2, 4);
    bne(tmp1, tmp2, DONE);
  }

  bind(TAIL03);
  // 0-3 bytes left.
  test_bit(tmp1, cnt1, 1);
  beqz(tmp1, TAIL01);
  {
    lhu(tmp1, Address(a1, 0));
    add(a1, a1, 2);
    lhu(tmp2, Address(a2, 0));
    add(a2, a2, 2);
    bne(tmp1, tmp2, DONE);
  }

  bind(TAIL01);
  if (elem_size == 1) { // Only needed when comparing 1-byte elements
    // 0-1 bytes left.
    test_bit(tmp1, cnt1, 0);
    beqz(tmp1, SAME);
    {
      lbu(tmp1, Address(a1, 0));
      lbu(tmp2, Address(a2, 0));
      bne(tmp1, tmp2, DONE);
    }
  }

  // Arrays are equal.
  bind(SAME);
  mv(result, true);

  // That's it.
  bind(DONE);
  BLOCK_COMMENT("} string_equals");
}

// jdk.internal.util.ArraysSupport.vectorizedHashCode
void C2_MacroAssembler::arrays_hashcode(Register ary, Register cnt, Register result,
                                        Register tmp1, Register tmp2, Register tmp3,
                                        Register tmp4, Register tmp5, Register tmp6,
                                        BasicType eltype)
{
  assert_different_registers(ary, cnt, result, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, t0, t1);

  const int elsize = arrays_hashcode_elsize(eltype);
  const int chunks_end_shift = exact_log2(elsize);

  switch (eltype) {
  case T_BOOLEAN: BLOCK_COMMENT("arrays_hashcode(unsigned byte) {"); break;
  case T_CHAR:    BLOCK_COMMENT("arrays_hashcode(char) {");          break;
  case T_BYTE:    BLOCK_COMMENT("arrays_hashcode(byte) {");          break;
  case T_SHORT:   BLOCK_COMMENT("arrays_hashcode(short) {");         break;
  case T_INT:     BLOCK_COMMENT("arrays_hashcode(int) {");           break;
  default:
    ShouldNotReachHere();
  }

  const int stride = 4;
  const Register pow31_4 = tmp1;
  const Register pow31_3 = tmp2;
  const Register pow31_2 = tmp3;
  const Register chunks  = tmp4;
  const Register chunks_end = chunks;

  Label DONE, TAIL, TAIL_LOOP, WIDE_LOOP;

  // result has a value initially

  beqz(cnt, DONE);

  andi(chunks, cnt, ~(stride-1));
  beqz(chunks, TAIL);

  mv(pow31_4, 923521);           // [31^^4]
  mv(pow31_3,  29791);           // [31^^3]
  mv(pow31_2,    961);           // [31^^2]

  slli(chunks_end, chunks, chunks_end_shift);
  add(chunks_end, ary, chunks_end);
  andi(cnt, cnt, stride-1);      // don't forget about tail!

  bind(WIDE_LOOP);
  mulw(result, result, pow31_4); // 31^^4 * h
  arrays_hashcode_elload(t0,   Address(ary, 0 * elsize), eltype);
  arrays_hashcode_elload(t1,   Address(ary, 1 * elsize), eltype);
  arrays_hashcode_elload(tmp5, Address(ary, 2 * elsize), eltype);
  arrays_hashcode_elload(tmp6, Address(ary, 3 * elsize), eltype);
  mulw(t0, t0, pow31_3);         // 31^^3 * ary[i+0]
  addw(result, result, t0);
  mulw(t1, t1, pow31_2);         // 31^^2 * ary[i+1]
  addw(result, result, t1);
  slli(t0, tmp5, 5);             // optimize 31^^1 * ary[i+2]
  subw(tmp5, t0, tmp5);          // with ary[i+2]<<5 - ary[i+2]
  addw(result, result, tmp5);
  addw(result, result, tmp6);    // 31^^4 * h + 31^^3 * ary[i+0] + 31^^2 * ary[i+1]
                                 //           + 31^^1 * ary[i+2] + 31^^0 * ary[i+3]
  addi(ary, ary, elsize * stride);
  bne(ary, chunks_end, WIDE_LOOP);
  beqz(cnt, DONE);

  bind(TAIL);
  slli(chunks_end, cnt, chunks_end_shift);
  add(chunks_end, ary, chunks_end);

  bind(TAIL_LOOP);
  arrays_hashcode_elload(t0, Address(ary), eltype);
  slli(t1, result, 5);           // optimize 31 * result
  subw(result, t1, result);      // with result<<5 - result
  addw(result, result, t0);
  addi(ary, ary, elsize);
  bne(ary, chunks_end, TAIL_LOOP);

  bind(DONE);
  BLOCK_COMMENT("} // arrays_hashcode");
}

int C2_MacroAssembler::arrays_hashcode_elsize(BasicType eltype) {
  switch (eltype) {
  case T_BOOLEAN: return sizeof(jboolean);
  case T_BYTE:    return sizeof(jbyte);
  case T_SHORT:   return sizeof(jshort);
  case T_CHAR:    return sizeof(jchar);
  case T_INT:     return sizeof(jint);
  default:
    ShouldNotReachHere();
    return -1;
  }
}

void C2_MacroAssembler::arrays_hashcode_elload(Register dst, Address src, BasicType eltype) {
  switch (eltype) {
  // T_BOOLEAN used as surrogate for unsigned byte
  case T_BOOLEAN: lbu(dst, src);   break;
  case T_BYTE:     lb(dst, src);   break;
  case T_SHORT:    lh(dst, src);   break;
  case T_CHAR:    lhu(dst, src);   break;
  case T_INT:      lw(dst, src);   break;
  default:
    ShouldNotReachHere();
  }
}

typedef void (Assembler::*conditional_branch_insn)(Register op1, Register op2, Label& label, bool is_far);
typedef void (MacroAssembler::*float_conditional_branch_insn)(FloatRegister op1, FloatRegister op2, Label& label,
                                                              bool is_far, bool is_unordered);

static conditional_branch_insn conditional_branches[] =
{
  /* SHORT branches */
  (conditional_branch_insn)&MacroAssembler::beq,
  (conditional_branch_insn)&MacroAssembler::bgt,
  nullptr, // BoolTest::overflow
  (conditional_branch_insn)&MacroAssembler::blt,
  (conditional_branch_insn)&MacroAssembler::bne,
  (conditional_branch_insn)&MacroAssembler::ble,
  nullptr, // BoolTest::no_overflow
  (conditional_branch_insn)&MacroAssembler::bge,

  /* UNSIGNED branches */
  (conditional_branch_insn)&MacroAssembler::beq,
  (conditional_branch_insn)&MacroAssembler::bgtu,
  nullptr,
  (conditional_branch_insn)&MacroAssembler::bltu,
  (conditional_branch_insn)&MacroAssembler::bne,
  (conditional_branch_insn)&MacroAssembler::bleu,
  nullptr,
  (conditional_branch_insn)&MacroAssembler::bgeu
};

static float_conditional_branch_insn float_conditional_branches[] =
{
  /* FLOAT SHORT branches */
  (float_conditional_branch_insn)&MacroAssembler::float_beq,
  (float_conditional_branch_insn)&MacroAssembler::float_bgt,
  nullptr,  // BoolTest::overflow
  (float_conditional_branch_insn)&MacroAssembler::float_blt,
  (float_conditional_branch_insn)&MacroAssembler::float_bne,
  (float_conditional_branch_insn)&MacroAssembler::float_ble,
  nullptr, // BoolTest::no_overflow
  (float_conditional_branch_insn)&MacroAssembler::float_bge,

  /* DOUBLE SHORT branches */
  (float_conditional_branch_insn)&MacroAssembler::double_beq,
  (float_conditional_branch_insn)&MacroAssembler::double_bgt,
  nullptr,
  (float_conditional_branch_insn)&MacroAssembler::double_blt,
  (float_conditional_branch_insn)&MacroAssembler::double_bne,
  (float_conditional_branch_insn)&MacroAssembler::double_ble,
  nullptr,
  (float_conditional_branch_insn)&MacroAssembler::double_bge
};

void C2_MacroAssembler::cmp_branch(int cmpFlag, Register op1, Register op2, Label& label, bool is_far) {
  assert(cmpFlag >= 0 && cmpFlag < (int)(sizeof(conditional_branches) / sizeof(conditional_branches[0])),
         "invalid conditional branch index");
  (this->*conditional_branches[cmpFlag])(op1, op2, label, is_far);
}

// This is a function should only be used by C2. Flip the unordered when unordered-greater, C2 would use
// unordered-lesser instead of unordered-greater. Finally, commute the result bits at function do_one_bytecode().
void C2_MacroAssembler::float_cmp_branch(int cmpFlag, FloatRegister op1, FloatRegister op2, Label& label, bool is_far) {
  assert(cmpFlag >= 0 && cmpFlag < (int)(sizeof(float_conditional_branches) / sizeof(float_conditional_branches[0])),
         "invalid float conditional branch index");
  int booltest_flag = cmpFlag & ~(C2_MacroAssembler::double_branch_mask);
  (this->*float_conditional_branches[cmpFlag])(op1, op2, label, is_far,
    (booltest_flag == (BoolTest::ge) || booltest_flag == (BoolTest::gt)) ? false : true);
}

void C2_MacroAssembler::enc_cmpUEqNeLeGt_imm0_branch(int cmpFlag, Register op1, Label& L, bool is_far) {
  switch (cmpFlag) {
    case BoolTest::eq:
    case BoolTest::le:
      beqz(op1, L, is_far);
      break;
    case BoolTest::ne:
    case BoolTest::gt:
      bnez(op1, L, is_far);
      break;
    default:
      ShouldNotReachHere();
  }
}

void C2_MacroAssembler::enc_cmpEqNe_imm0_branch(int cmpFlag, Register op1, Label& L, bool is_far) {
  switch (cmpFlag) {
    case BoolTest::eq:
      beqz(op1, L, is_far);
      break;
    case BoolTest::ne:
      bnez(op1, L, is_far);
      break;
    default:
      ShouldNotReachHere();
  }
}

void C2_MacroAssembler::enc_cmove(int cmpFlag, Register op1, Register op2, Register dst, Register src) {
  Label L;
  cmp_branch(cmpFlag ^ (1 << neg_cond_bits), op1, op2, L);
  mv(dst, src);
  bind(L);
}

// Set dst to NaN if any NaN input.
void C2_MacroAssembler::minmax_fp(FloatRegister dst, FloatRegister src1, FloatRegister src2,
                                  bool is_double, bool is_min) {
  assert_different_registers(dst, src1, src2);

  Label Done, Compare;

  is_double ? fclass_d(t0, src1)
            : fclass_s(t0, src1);
  is_double ? fclass_d(t1, src2)
            : fclass_s(t1, src2);
  orr(t0, t0, t1);
  andi(t0, t0, fclass_mask::nan); // if src1 or src2 is quiet or signaling NaN then return NaN
  beqz(t0, Compare);
  is_double ? fadd_d(dst, src1, src2)
            : fadd_s(dst, src1, src2);
  j(Done);

  bind(Compare);
  if (is_double) {
    is_min ? fmin_d(dst, src1, src2)
           : fmax_d(dst, src1, src2);
  } else {
    is_min ? fmin_s(dst, src1, src2)
           : fmax_s(dst, src1, src2);
  }

  bind(Done);
}

// According to Java SE specification, for floating-point round operations, if
// the input is NaN, +/-infinity, or +/-0, the same input is returned as the
// rounded result; this differs from behavior of RISC-V fcvt instructions (which
// round out-of-range values to the nearest max or min value), therefore special
// handling is needed by NaN, +/-Infinity, +/-0.
void C2_MacroAssembler::round_double_mode(FloatRegister dst, FloatRegister src, int round_mode,
                                          Register tmp1, Register tmp2, Register tmp3) {

  assert_different_registers(dst, src);
  assert_different_registers(tmp1, tmp2, tmp3);

  // Set rounding mode for conversions
  // Here we use similar modes to double->long and long->double conversions
  // Different mode for long->double conversion matter only if long value was not representable as double,
  // we got long value as a result of double->long conversion so, it is definitely representable
  RoundingMode rm;
  switch (round_mode) {
    case RoundDoubleModeNode::rmode_ceil:
      rm = RoundingMode::rup;
      break;
    case RoundDoubleModeNode::rmode_floor:
      rm = RoundingMode::rdn;
      break;
    case RoundDoubleModeNode::rmode_rint:
      rm = RoundingMode::rne;
      break;
    default:
      ShouldNotReachHere();
  }

  // tmp1 - is a register to store double converted to long int
  // tmp2 - is a register to create constant for comparison
  // tmp3 - is a register where we store modified result of double->long conversion
  Label done, bad_val;

  // Conversion from double to long
  fcvt_l_d(tmp1, src, rm);

  // Generate constant (tmp2)
  // tmp2 = 100...0000
  addi(tmp2, zr, 1);
  slli(tmp2, tmp2, 63);

  // Prepare converted long (tmp1)
  // as a result when conversion overflow we got:
  // tmp1 = 011...1111 or 100...0000
  // Convert it to: tmp3 = 100...0000
  addi(tmp3, tmp1, 1);
  andi(tmp3, tmp3, -2);
  beq(tmp3, tmp2, bad_val);

  // Conversion from long to double
  fcvt_d_l(dst, tmp1, rm);
  // Add sign of input value to result for +/- 0 cases
  fsgnj_d(dst, dst, src);
  j(done);

  // If got conversion overflow return src
  bind(bad_val);
  fmv_d(dst, src);

  bind(done);
}

// According to Java SE specification, for floating-point signum operations, if
// on input we have NaN or +/-0.0 value we should return it,
// otherwise return +/- 1.0 using sign of input.
// one - gives us a floating-point 1.0 (got from matching rule)
// bool is_double - specifies single or double precision operations will be used.
void C2_MacroAssembler::signum_fp(FloatRegister dst, FloatRegister one, bool is_double) {
  Label done;

  is_double ? fclass_d(t0, dst)
            : fclass_s(t0, dst);

  // check if input is -0, +0, signaling NaN or quiet NaN
  andi(t0, t0, fclass_mask::zero | fclass_mask::nan);

  bnez(t0, done);

  // use floating-point 1.0 with a sign of input
  is_double ? fsgnj_d(dst, one, dst)
            : fsgnj_s(dst, one, dst);

  bind(done);
}

static void float16_to_float_slow_path(C2_MacroAssembler& masm, C2GeneralStub<FloatRegister, Register, Register>& stub) {
#define __ masm.
  FloatRegister dst = stub.data<0>();
  Register src = stub.data<1>();
  Register tmp = stub.data<2>();
  __ bind(stub.entry());

  // following instructions mainly focus on NaN, as riscv does not handle
  // NaN well with fcvt, but the code also works for Inf at the same time.

  // construct a NaN in 32 bits from the NaN in 16 bits,
  // we need the payloads of non-canonical NaNs to be preserved.
  __ mv(tmp, 0x7f800000);
  // sign-bit was already set via sign-extension if necessary.
  __ slli(t0, src, 13);
  __ orr(tmp, t0, tmp);
  __ fmv_w_x(dst, tmp);

  __ j(stub.continuation());
#undef __
}

// j.l.Float.float16ToFloat
void C2_MacroAssembler::float16_to_float(FloatRegister dst, Register src, Register tmp) {
  auto stub = C2CodeStub::make<FloatRegister, Register, Register>(dst, src, tmp, 20, float16_to_float_slow_path);

  // in riscv, NaN needs a special process as fcvt does not work in that case.
  // in riscv, Inf does not need a special process as fcvt can handle it correctly.
  // but we consider to get the slow path to process NaN and Inf at the same time,
  // as both of them are rare cases, and if we try to get the slow path to handle
  // only NaN case it would sacrifise the performance for normal cases,
  // i.e. non-NaN and non-Inf cases.

  // check whether it's a NaN or +/- Inf.
  mv(t0, 0x7c00);
  andr(tmp, src, t0);
  // jump to stub processing NaN and Inf cases.
  beq(t0, tmp, stub->entry());

  // non-NaN or non-Inf cases, just use built-in instructions.
  fmv_h_x(dst, src);
  fcvt_s_h(dst, dst);

  bind(stub->continuation());
}

void C2_MacroAssembler::signum_fp_v(VectorRegister dst, VectorRegister one, BasicType bt, int vlen) {
  vsetvli_helper(bt, vlen);

  // check if input is -0, +0, signaling NaN or quiet NaN
  vfclass_v(v0, dst);
  mv(t0, fclass_mask::zero | fclass_mask::nan);
  vand_vx(v0, v0, t0);
  vmseq_vi(v0, v0, 0);

  // use floating-point 1.0 with a sign of input
  vfsgnj_vv(dst, one, dst, v0_t);
}

void C2_MacroAssembler::compress_bits_v(Register dst, Register src, Register mask, bool is_long) {
  Assembler::SEW sew = is_long ? Assembler::e64 : Assembler::e32;
  // intrinsic is enabled when MaxVectorSize >= 16
  Assembler::LMUL lmul = is_long ? Assembler::m4 : Assembler::m2;
  long len = is_long ? 64 : 32;

  // load the src data(in bits) to be compressed.
  vsetivli(x0, 1, sew, Assembler::m1);
  vmv_s_x(v0, src);
  // reset the src data(in bytes) to zero.
  mv(t0, len);
  vsetvli(x0, t0, Assembler::e8, lmul);
  vmv_v_i(v4, 0);
  // convert the src data from bits to bytes.
  vmerge_vim(v4, v4, 1); // v0 as the implicit mask register
  // reset the dst data(in bytes) to zero.
  vmv_v_i(v8, 0);
  // load the mask data(in bits).
  vsetivli(x0, 1, sew, Assembler::m1);
  vmv_s_x(v0, mask);
  // compress the src data(in bytes) to dst(in bytes).
  vsetvli(x0, t0, Assembler::e8, lmul);
  vcompress_vm(v8, v4, v0);
  // convert the dst data from bytes to bits.
  vmseq_vi(v0, v8, 1);
  // store result back.
  vsetivli(x0, 1, sew, Assembler::m1);
  vmv_x_s(dst, v0);
}

void C2_MacroAssembler::compress_bits_i_v(Register dst, Register src, Register mask) {
  compress_bits_v(dst, src, mask, /* is_long */ false);
}

void C2_MacroAssembler::compress_bits_l_v(Register dst, Register src, Register mask) {
  compress_bits_v(dst, src, mask, /* is_long */ true);
}

void C2_MacroAssembler::expand_bits_v(Register dst, Register src, Register mask, bool is_long) {
  Assembler::SEW sew = is_long ? Assembler::e64 : Assembler::e32;
  // intrinsic is enabled when MaxVectorSize >= 16
  Assembler::LMUL lmul = is_long ? Assembler::m4 : Assembler::m2;
  long len = is_long ? 64 : 32;

  // load the src data(in bits) to be expanded.
  vsetivli(x0, 1, sew, Assembler::m1);
  vmv_s_x(v0, src);
  // reset the src data(in bytes) to zero.
  mv(t0, len);
  vsetvli(x0, t0, Assembler::e8, lmul);
  vmv_v_i(v4, 0);
  // convert the src data from bits to bytes.
  vmerge_vim(v4, v4, 1); // v0 as implicit mask register
  // reset the dst data(in bytes) to zero.
  vmv_v_i(v12, 0);
  // load the mask data(in bits).
  vsetivli(x0, 1, sew, Assembler::m1);
  vmv_s_x(v0, mask);
  // expand the src data(in bytes) to dst(in bytes).
  vsetvli(x0, t0, Assembler::e8, lmul);
  viota_m(v8, v0);
  vrgather_vv(v12, v4, v8, VectorMask::v0_t); // v0 as implicit mask register
  // convert the dst data from bytes to bits.
  vmseq_vi(v0, v12, 1);
  // store result back.
  vsetivli(x0, 1, sew, Assembler::m1);
  vmv_x_s(dst, v0);
}

void C2_MacroAssembler::expand_bits_i_v(Register dst, Register src, Register mask) {
  expand_bits_v(dst, src, mask, /* is_long */ false);
}

void C2_MacroAssembler::expand_bits_l_v(Register dst, Register src, Register mask) {
  expand_bits_v(dst, src, mask, /* is_long */ true);
}

void C2_MacroAssembler::element_compare(Register a1, Register a2, Register result, Register cnt, Register tmp1, Register tmp2,
                                        VectorRegister vr1, VectorRegister vr2, VectorRegister vrs, bool islatin, Label &DONE) {
  Label loop;
  Assembler::SEW sew = islatin ? Assembler::e8 : Assembler::e16;

  bind(loop);
  vsetvli(tmp1, cnt, sew, Assembler::m2);
  vlex_v(vr1, a1, sew);
  vlex_v(vr2, a2, sew);
  vmsne_vv(vrs, vr1, vr2);
  vfirst_m(tmp2, vrs);
  bgez(tmp2, DONE);
  sub(cnt, cnt, tmp1);
  if (!islatin) {
    slli(tmp1, tmp1, 1); // get byte counts
  }
  add(a1, a1, tmp1);
  add(a2, a2, tmp1);
  bnez(cnt, loop);

  mv(result, true);
}

void C2_MacroAssembler::string_equals_v(Register a1, Register a2, Register result, Register cnt, int elem_size) {
  Label DONE;
  Register tmp1 = t0;
  Register tmp2 = t1;

  BLOCK_COMMENT("string_equals_v {");

  mv(result, false);

  if (elem_size == 2) {
    srli(cnt, cnt, 1);
  }

  element_compare(a1, a2, result, cnt, tmp1, tmp2, v2, v4, v2, elem_size == 1, DONE);

  bind(DONE);
  BLOCK_COMMENT("} string_equals_v");
}

// used by C2 ClearArray patterns.
// base: Address of a buffer to be zeroed
// cnt: Count in HeapWords
//
// base, cnt, v4, v5, v6, v7 and t0 are clobbered.
void C2_MacroAssembler::clear_array_v(Register base, Register cnt) {
  Label loop;

  // making zero words
  vsetvli(t0, cnt, Assembler::e64, Assembler::m4);
  vxor_vv(v4, v4, v4);

  bind(loop);
  vsetvli(t0, cnt, Assembler::e64, Assembler::m4);
  vse64_v(v4, base);
  sub(cnt, cnt, t0);
  shadd(base, t0, base, t0, 3);
  bnez(cnt, loop);
}

void C2_MacroAssembler::arrays_equals_v(Register a1, Register a2, Register result,
                                        Register cnt1, int elem_size) {
  Label DONE;
  Register tmp1 = t0;
  Register tmp2 = t1;
  Register cnt2 = tmp2;
  int length_offset = arrayOopDesc::length_offset_in_bytes();
  int base_offset = arrayOopDesc::base_offset_in_bytes(elem_size == 2 ? T_CHAR : T_BYTE);

  BLOCK_COMMENT("arrays_equals_v {");

  // if (a1 == a2), return true
  mv(result, true);
  beq(a1, a2, DONE);

  mv(result, false);
  // if a1 == null or a2 == null, return false
  beqz(a1, DONE);
  beqz(a2, DONE);
  // if (a1.length != a2.length), return false
  lwu(cnt1, Address(a1, length_offset));
  lwu(cnt2, Address(a2, length_offset));
  bne(cnt1, cnt2, DONE);

  la(a1, Address(a1, base_offset));
  la(a2, Address(a2, base_offset));

  element_compare(a1, a2, result, cnt1, tmp1, tmp2, v2, v4, v2, elem_size == 1, DONE);

  bind(DONE);

  BLOCK_COMMENT("} arrays_equals_v");
}

void C2_MacroAssembler::string_compare_v(Register str1, Register str2, Register cnt1, Register cnt2,
                                         Register result, Register tmp1, Register tmp2, int encForm) {
  Label DIFFERENCE, DONE, L, loop;
  bool encLL = encForm == StrIntrinsicNode::LL;
  bool encLU = encForm == StrIntrinsicNode::LU;
  bool encUL = encForm == StrIntrinsicNode::UL;

  bool str1_isL = encLL || encLU;
  bool str2_isL = encLL || encUL;

  int minCharsInWord = encLL ? wordSize : wordSize / 2;

  BLOCK_COMMENT("string_compare {");

  // for Latin strings, 1 byte for 1 character
  // for UTF16 strings, 2 bytes for 1 character
  if (!str1_isL)
    sraiw(cnt1, cnt1, 1);
  if (!str2_isL)
    sraiw(cnt2, cnt2, 1);

  // if str1 == str2, return the difference
  // save the minimum of the string lengths in cnt2.
  sub(result, cnt1, cnt2);
  bgt(cnt1, cnt2, L);
  mv(cnt2, cnt1);
  bind(L);

  if (str1_isL == str2_isL) { // LL or UU
    element_compare(str1, str2, zr, cnt2, tmp1, tmp2, v2, v4, v2, encLL, DIFFERENCE);
    j(DONE);
  } else { // LU or UL
    Register strL = encLU ? str1 : str2;
    Register strU = encLU ? str2 : str1;
    VectorRegister vstr1 = encLU ? v8 : v4;
    VectorRegister vstr2 = encLU ? v4 : v8;

    bind(loop);
    vsetvli(tmp1, cnt2, Assembler::e8, Assembler::m2);
    vle8_v(vstr1, strL);
    vsetvli(tmp1, cnt2, Assembler::e16, Assembler::m4);
    vzext_vf2(vstr2, vstr1);
    vle16_v(vstr1, strU);
    vmsne_vv(v4, vstr2, vstr1);
    vfirst_m(tmp2, v4);
    bgez(tmp2, DIFFERENCE);
    sub(cnt2, cnt2, tmp1);
    add(strL, strL, tmp1);
    shadd(strU, tmp1, strU, tmp1, 1);
    bnez(cnt2, loop);
    j(DONE);
  }

  bind(DIFFERENCE);
  slli(tmp1, tmp2, 1);
  add(str1, str1, str1_isL ? tmp2 : tmp1);
  add(str2, str2, str2_isL ? tmp2 : tmp1);
  str1_isL ? lbu(tmp1, Address(str1, 0)) : lhu(tmp1, Address(str1, 0));
  str2_isL ? lbu(tmp2, Address(str2, 0)) : lhu(tmp2, Address(str2, 0));
  sub(result, tmp1, tmp2);

  bind(DONE);
}

void C2_MacroAssembler::byte_array_inflate_v(Register src, Register dst, Register len, Register tmp) {
  Label loop;
  assert_different_registers(src, dst, len, tmp, t0);

  BLOCK_COMMENT("byte_array_inflate_v {");
  bind(loop);
  vsetvli(tmp, len, Assembler::e8, Assembler::m2);
  vle8_v(v6, src);
  vsetvli(t0, len, Assembler::e16, Assembler::m4);
  vzext_vf2(v4, v6);
  vse16_v(v4, dst);
  sub(len, len, tmp);
  add(src, src, tmp);
  shadd(dst, tmp, dst, tmp, 1);
  bnez(len, loop);
  BLOCK_COMMENT("} byte_array_inflate_v");
}

// Compress char[] array to byte[].
// Intrinsic for java.lang.StringUTF16.compress(char[] src, int srcOff, byte[] dst, int dstOff, int len)
// result: the array length if every element in array can be encoded,
// otherwise, the index of first non-latin1 (> 0xff) character.
void C2_MacroAssembler::char_array_compress_v(Register src, Register dst, Register len,
                                              Register result, Register tmp) {
  encode_iso_array_v(src, dst, len, result, tmp, false);
}

// Intrinsic for
//
// - sun/nio/cs/ISO_8859_1$Encoder.implEncodeISOArray
//     return the number of characters copied.
// - java/lang/StringUTF16.compress
//     return index of non-latin1 character if copy fails, otherwise 'len'.
//
// This version always returns the number of characters copied. A successful
// copy will complete with the post-condition: 'res' == 'len', while an
// unsuccessful copy will exit with the post-condition: 0 <= 'res' < 'len'.
//
// Clobbers: src, dst, len, result, t0
void C2_MacroAssembler::encode_iso_array_v(Register src, Register dst, Register len,
                                           Register result, Register tmp, bool ascii) {
  Label loop, fail, done;

  BLOCK_COMMENT("encode_iso_array_v {");
  mv(result, 0);

  bind(loop);
  mv(tmp, ascii ? 0x7f : 0xff);
  vsetvli(t0, len, Assembler::e16, Assembler::m2);
  vle16_v(v2, src);

  vmsgtu_vx(v1, v2, tmp);
  vfirst_m(tmp, v1);
  vmsbf_m(v0, v1);
  // compress char to byte
  vsetvli(t0, len, Assembler::e8);
  vncvt_x_x_w(v1, v2, Assembler::v0_t);
  vse8_v(v1, dst, Assembler::v0_t);

  // fail if char > 0x7f/0xff
  bgez(tmp, fail);
  add(result, result, t0);
  add(dst, dst, t0);
  sub(len, len, t0);
  shadd(src, t0, src, t0, 1);
  bnez(len, loop);
  j(done);

  bind(fail);
  add(result, result, tmp);

  bind(done);
  BLOCK_COMMENT("} encode_iso_array_v");
}

void C2_MacroAssembler::count_positives_v(Register ary, Register len, Register result, Register tmp) {
  Label LOOP, SET_RESULT, DONE;

  BLOCK_COMMENT("count_positives_v {");
  assert_different_registers(ary, len, result, tmp);

  mv(result, zr);

  bind(LOOP);
  vsetvli(t0, len, Assembler::e8, Assembler::m4);
  vle8_v(v4, ary);
  vmslt_vx(v4, v4, zr);
  vfirst_m(tmp, v4);
  bgez(tmp, SET_RESULT);
  // if tmp == -1, all bytes are positive
  add(result, result, t0);

  sub(len, len, t0);
  add(ary, ary, t0);
  bnez(len, LOOP);
  j(DONE);

  // add remaining positive bytes count
  bind(SET_RESULT);
  add(result, result, tmp);

  bind(DONE);
  BLOCK_COMMENT("} count_positives_v");
}

void C2_MacroAssembler::string_indexof_char_v(Register str1, Register cnt1,
                                              Register ch, Register result,
                                              Register tmp1, Register tmp2,
                                              bool isL) {
  mv(result, zr);

  Label loop, MATCH, DONE;
  Assembler::SEW sew = isL ? Assembler::e8 : Assembler::e16;
  bind(loop);
  vsetvli(tmp1, cnt1, sew, Assembler::m4);
  vlex_v(v4, str1, sew);
  vmseq_vx(v4, v4, ch);
  vfirst_m(tmp2, v4);
  bgez(tmp2, MATCH); // if equal, return index

  add(result, result, tmp1);
  sub(cnt1, cnt1, tmp1);
  if (!isL) slli(tmp1, tmp1, 1);
  add(str1, str1, tmp1);
  bnez(cnt1, loop);

  mv(result, -1);
  j(DONE);

  bind(MATCH);
  add(result, result, tmp2);

  bind(DONE);
}

// Set dst to NaN if any NaN input.
void C2_MacroAssembler::minmax_fp_v(VectorRegister dst, VectorRegister src1, VectorRegister src2,
                                    BasicType bt, bool is_min, int vector_length) {
  assert_different_registers(dst, src1, src2);

  vsetvli_helper(bt, vector_length);

  is_min ? vfmin_vv(dst, src1, src2)
         : vfmax_vv(dst, src1, src2);

  vmfne_vv(v0,  src1, src1);
  vfadd_vv(dst, src1, src1, Assembler::v0_t);
  vmfne_vv(v0,  src2, src2);
  vfadd_vv(dst, src2, src2, Assembler::v0_t);
}

// Set dst to NaN if any NaN input.
// The destination vector register elements corresponding to masked-off elements
// are handled with a mask-undisturbed policy.
void C2_MacroAssembler::minmax_fp_masked_v(VectorRegister dst, VectorRegister src1, VectorRegister src2,
                                           VectorRegister vmask, VectorRegister tmp1, VectorRegister tmp2,
                                           BasicType bt, bool is_min, int vector_length) {
  assert_different_registers(src1, src2, tmp1, tmp2);
  vsetvli_helper(bt, vector_length);

  // Check vector elements of src1 and src2 for NaN.
  vmfeq_vv(tmp1, src1, src1);
  vmfeq_vv(tmp2, src2, src2);

  vmandn_mm(v0, vmask, tmp1);
  vfadd_vv(dst, src1, src1, Assembler::v0_t);
  vmandn_mm(v0, vmask, tmp2);
  vfadd_vv(dst, src2, src2, Assembler::v0_t);

  vmand_mm(tmp2, tmp1, tmp2);
  vmand_mm(v0, vmask, tmp2);
  is_min ? vfmin_vv(dst, src1, src2, Assembler::v0_t)
         : vfmax_vv(dst, src1, src2, Assembler::v0_t);
}

// Set dst to NaN if any NaN input.
void C2_MacroAssembler::reduce_minmax_fp_v(FloatRegister dst,
                                           FloatRegister src1, VectorRegister src2,
                                           VectorRegister tmp1, VectorRegister tmp2,
                                           bool is_double, bool is_min, int vector_length, VectorMask vm) {
  assert_different_registers(dst, src1);
  assert_different_registers(src2, tmp1, tmp2);

  Label L_done, L_NaN_1, L_NaN_2;
  // Set dst to src1 if src1 is NaN
  is_double ? feq_d(t0, src1, src1)
            : feq_s(t0, src1, src1);
  beqz(t0, L_NaN_2);

  vsetvli_helper(is_double ? T_DOUBLE : T_FLOAT, vector_length);
  vfmv_s_f(tmp2, src1);

  is_min ? vfredmin_vs(tmp1, src2, tmp2, vm)
         : vfredmax_vs(tmp1, src2, tmp2, vm);
  vfmv_f_s(dst, tmp1);

  // Checking NaNs in src2
  vmfne_vv(tmp1, src2, src2, vm);
  vcpop_m(t0, tmp1, vm);
  beqz(t0, L_done);

  bind(L_NaN_1);
  vfredusum_vs(tmp1, src2, tmp2, vm);
  vfmv_f_s(dst, tmp1);
  j(L_done);

  bind(L_NaN_2);
  is_double ? fmv_d(dst, src1)
            : fmv_s(dst, src1);
  bind(L_done);
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

void C2_MacroAssembler::reduce_integral_v(Register dst, Register src1,
                                          VectorRegister src2, VectorRegister tmp,
                                          int opc, BasicType bt, int vector_length, VectorMask vm) {
  assert(bt == T_BYTE || bt == T_SHORT || bt == T_INT || bt == T_LONG, "unsupported element type");
  vsetvli_helper(bt, vector_length);
  vmv_s_x(tmp, src1);
  switch (opc) {
    case Op_AddReductionVI:
    case Op_AddReductionVL:
      vredsum_vs(tmp, src2, tmp, vm);
      break;
    case Op_AndReductionV:
      vredand_vs(tmp, src2, tmp, vm);
      break;
    case Op_OrReductionV:
      vredor_vs(tmp, src2, tmp, vm);
      break;
    case Op_XorReductionV:
      vredxor_vs(tmp, src2, tmp, vm);
      break;
    case Op_MaxReductionV:
      vredmax_vs(tmp, src2, tmp, vm);
      break;
    case Op_MinReductionV:
      vredmin_vs(tmp, src2, tmp, vm);
      break;
    default:
      ShouldNotReachHere();
  }
  vmv_x_s(dst, tmp);
}

// Set vl and vtype for full and partial vector operations.
// (vma = mu, vta = tu, vill = false)
void C2_MacroAssembler::vsetvli_helper(BasicType bt, int vector_length, LMUL vlmul, Register tmp) {
  Assembler::SEW sew = Assembler::elemtype_to_sew(bt);
  if (vector_length <= 31) {
    vsetivli(tmp, vector_length, sew, vlmul);
  } else if (vector_length == (MaxVectorSize / type2aelembytes(bt))) {
    vsetvli(tmp, x0, sew, vlmul);
  } else {
    mv(tmp, vector_length);
    vsetvli(tmp, tmp, sew, vlmul);
  }
}

void C2_MacroAssembler::compare_integral_v(VectorRegister vd, VectorRegister src1, VectorRegister src2,
                                           int cond, BasicType bt, int vector_length, VectorMask vm) {
  assert(is_integral_type(bt), "unsupported element type");
  assert(vm == Assembler::v0_t ? vd != v0 : true, "should be different registers");
  vsetvli_helper(bt, vector_length);
  vmclr_m(vd);
  switch (cond) {
    case BoolTest::eq: vmseq_vv(vd, src1, src2, vm); break;
    case BoolTest::ne: vmsne_vv(vd, src1, src2, vm); break;
    case BoolTest::le: vmsle_vv(vd, src1, src2, vm); break;
    case BoolTest::ge: vmsge_vv(vd, src1, src2, vm); break;
    case BoolTest::lt: vmslt_vv(vd, src1, src2, vm); break;
    case BoolTest::gt: vmsgt_vv(vd, src1, src2, vm); break;
    default:
      assert(false, "unsupported compare condition");
      ShouldNotReachHere();
  }
}

void C2_MacroAssembler::compare_fp_v(VectorRegister vd, VectorRegister src1, VectorRegister src2,
                                     int cond, BasicType bt, int vector_length, VectorMask vm) {
  assert(is_floating_point_type(bt), "unsupported element type");
  assert(vm == Assembler::v0_t ? vd != v0 : true, "should be different registers");
  vsetvli_helper(bt, vector_length);
  vmclr_m(vd);
  switch (cond) {
    case BoolTest::eq: vmfeq_vv(vd, src1, src2, vm); break;
    case BoolTest::ne: vmfne_vv(vd, src1, src2, vm); break;
    case BoolTest::le: vmfle_vv(vd, src1, src2, vm); break;
    case BoolTest::ge: vmfge_vv(vd, src1, src2, vm); break;
    case BoolTest::lt: vmflt_vv(vd, src1, src2, vm); break;
    case BoolTest::gt: vmfgt_vv(vd, src1, src2, vm); break;
    default:
      assert(false, "unsupported compare condition");
      ShouldNotReachHere();
  }
}

void C2_MacroAssembler::integer_extend_v(VectorRegister dst, BasicType dst_bt, int vector_length,
                                         VectorRegister src, BasicType src_bt) {
  assert(type2aelembytes(dst_bt) > type2aelembytes(src_bt) && type2aelembytes(dst_bt) <= 8 && type2aelembytes(src_bt) <= 4, "invalid element size");
  assert(dst_bt != T_FLOAT && dst_bt != T_DOUBLE && src_bt != T_FLOAT && src_bt != T_DOUBLE, "unsupported element type");
  // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#52-vector-operands
  // The destination EEW is greater than the source EEW, the source EMUL is at least 1,
  // and the overlap is in the highest-numbered part of the destination register group.
  // Since LMUL=1, vd and vs cannot be the same.
  assert_different_registers(dst, src);

  vsetvli_helper(dst_bt, vector_length);
  if (src_bt == T_BYTE) {
    switch (dst_bt) {
    case T_SHORT:
      vsext_vf2(dst, src);
      break;
    case T_INT:
      vsext_vf4(dst, src);
      break;
    case T_LONG:
      vsext_vf8(dst, src);
      break;
    default:
      ShouldNotReachHere();
    }
  } else if (src_bt == T_SHORT) {
    if (dst_bt == T_INT) {
      vsext_vf2(dst, src);
    } else {
      vsext_vf4(dst, src);
    }
  } else if (src_bt == T_INT) {
    vsext_vf2(dst, src);
  }
}

// Vector narrow from src to dst with specified element sizes.
// High part of dst vector will be filled with zero.
void C2_MacroAssembler::integer_narrow_v(VectorRegister dst, BasicType dst_bt, int vector_length,
                                         VectorRegister src, BasicType src_bt) {
  assert(type2aelembytes(dst_bt) < type2aelembytes(src_bt) && type2aelembytes(dst_bt) <= 4 && type2aelembytes(src_bt) <= 8, "invalid element size");
  assert(dst_bt != T_FLOAT && dst_bt != T_DOUBLE && src_bt != T_FLOAT && src_bt != T_DOUBLE, "unsupported element type");
  mv(t0, vector_length);
  if (src_bt == T_LONG) {
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#117-vector-narrowing-integer-right-shift-instructions
    // Future extensions might add support for versions that narrow to a destination that is 1/4 the width of the source.
    // So we can currently only scale down by 1/2 the width at a time.
    vsetvli(t0, t0, Assembler::e32, Assembler::mf2);
    vncvt_x_x_w(dst, src);
    if (dst_bt == T_SHORT || dst_bt == T_BYTE) {
      vsetvli(t0, t0, Assembler::e16, Assembler::mf2);
      vncvt_x_x_w(dst, dst);
      if (dst_bt == T_BYTE) {
        vsetvli(t0, t0, Assembler::e8, Assembler::mf2);
        vncvt_x_x_w(dst, dst);
      }
    }
  } else if (src_bt == T_INT) {
    // T_SHORT
    vsetvli(t0, t0, Assembler::e16, Assembler::mf2);
    vncvt_x_x_w(dst, src);
    if (dst_bt == T_BYTE) {
      vsetvli(t0, t0, Assembler::e8, Assembler::mf2);
      vncvt_x_x_w(dst, dst);
    }
  } else if (src_bt == T_SHORT) {
    vsetvli(t0, t0, Assembler::e8, Assembler::mf2);
    vncvt_x_x_w(dst, src);
  }
}

#define VFCVT_SAFE(VFLOATCVT)                                                      \
void C2_MacroAssembler::VFLOATCVT##_safe(VectorRegister dst, VectorRegister src) { \
  assert_different_registers(dst, src);                                            \
  vxor_vv(dst, dst, dst);                                                          \
  vmfeq_vv(v0, src, src);                                                          \
  VFLOATCVT(dst, src, Assembler::v0_t);                                            \
}

VFCVT_SAFE(vfcvt_rtz_x_f_v);

#undef VFCVT_SAFE

// Extract a scalar element from an vector at position 'idx'.
// The input elements in src are expected to be of integral type.
void C2_MacroAssembler::extract_v(Register dst, VectorRegister src, BasicType bt,
                                  int idx, VectorRegister tmp) {
  assert(is_integral_type(bt), "unsupported element type");
  assert(idx >= 0, "idx cannot be negative");
  // Only need the first element after vector slidedown
  vsetvli_helper(bt, 1);
  if (idx == 0) {
    vmv_x_s(dst, src);
  } else if (idx <= 31) {
    vslidedown_vi(tmp, src, idx);
    vmv_x_s(dst, tmp);
  } else {
    mv(t0, idx);
    vslidedown_vx(tmp, src, t0);
    vmv_x_s(dst, tmp);
  }
}

// Extract a scalar element from an vector at position 'idx'.
// The input elements in src are expected to be of floating point type.
void C2_MacroAssembler::extract_fp_v(FloatRegister dst, VectorRegister src, BasicType bt,
                                     int idx, VectorRegister tmp) {
  assert(is_floating_point_type(bt), "unsupported element type");
  assert(idx >= 0, "idx cannot be negative");
  // Only need the first element after vector slidedown
  vsetvli_helper(bt, 1);
  if (idx == 0) {
    vfmv_f_s(dst, src);
  } else if (idx <= 31) {
    vslidedown_vi(tmp, src, idx);
    vfmv_f_s(dst, tmp);
  } else {
    mv(t0, idx);
    vslidedown_vx(tmp, src, t0);
    vfmv_f_s(dst, tmp);
  }
}
