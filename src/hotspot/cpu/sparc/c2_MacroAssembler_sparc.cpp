/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/arrayOop.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "opto/intrinsicnode.hpp"

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#define STOP(error) stop(error)
#else
#define BLOCK_COMMENT(str) block_comment(str)
#define STOP(error) block_comment(error); stop(error)
#endif

// Compress char[] to byte[] by compressing 16 bytes at once. Return 0 on failure.
void C2_MacroAssembler::string_compress_16(Register src, Register dst, Register cnt, Register result,
                                           Register tmp1, Register tmp2, Register tmp3, Register tmp4,
                                           FloatRegister ftmp1, FloatRegister ftmp2, FloatRegister ftmp3, Label& Ldone) {
  Label Lloop, Lslow;
  assert(UseVIS >= 3, "VIS3 is required");
  assert_different_registers(src, dst, cnt, tmp1, tmp2, tmp3, tmp4, result);
  assert_different_registers(ftmp1, ftmp2, ftmp3);

  // Check if cnt >= 8 (= 16 bytes)
  cmp(cnt, 8);
  br(Assembler::less, false, Assembler::pn, Lslow);
  delayed()->mov(cnt, result); // copy count

  // Check for 8-byte alignment of src and dst
  or3(src, dst, tmp1);
  andcc(tmp1, 7, G0);
  br(Assembler::notZero, false, Assembler::pn, Lslow);
  delayed()->nop();

  // Set mask for bshuffle instruction
  Register mask = tmp4;
  set(0x13579bdf, mask);
  bmask(mask, G0, G0);

  // Set mask to 0xff00 ff00 ff00 ff00 to check for non-latin1 characters
  Assembler::sethi(0xff00fc00, mask); // mask = 0x0000 0000 ff00 fc00
  add(mask, 0x300, mask);             // mask = 0x0000 0000 ff00 ff00
  sllx(mask, 32, tmp1);               // tmp1 = 0xff00 ff00 0000 0000
  or3(mask, tmp1, mask);              // mask = 0xff00 ff00 ff00 ff00

  // Load first 8 bytes
  ldx(src, 0, tmp1);

  bind(Lloop);
  // Load next 8 bytes
  ldx(src, 8, tmp2);

  // Check for non-latin1 character by testing if the most significant byte of a char is set.
  // Although we have to move the data between integer and floating point registers, this is
  // still faster than the corresponding VIS instructions (ford/fand/fcmpd).
  or3(tmp1, tmp2, tmp3);
  btst(tmp3, mask);
  // annul zeroing if branch is not taken to preserve original count
  brx(Assembler::notZero, true, Assembler::pn, Ldone);
  delayed()->mov(G0, result); // 0 - failed

  // Move bytes into float register
  movxtod(tmp1, ftmp1);
  movxtod(tmp2, ftmp2);

  // Compress by copying one byte per char from ftmp1 and ftmp2 to ftmp3
  bshuffle(ftmp1, ftmp2, ftmp3);
  stf(FloatRegisterImpl::D, ftmp3, dst, 0);

  // Increment addresses and decrement count
  inc(src, 16);
  inc(dst, 8);
  dec(cnt, 8);

  cmp(cnt, 8);
  // annul LDX if branch is not taken to prevent access past end of string
  br(Assembler::greaterEqual, true, Assembler::pt, Lloop);
  delayed()->ldx(src, 0, tmp1);

  // Fallback to slow version
  bind(Lslow);
}

// Compress char[] to byte[]. Return 0 on failure.
void C2_MacroAssembler::string_compress(Register src, Register dst, Register cnt, Register result, Register tmp, Label& Ldone) {
  Label Lloop;
  assert_different_registers(src, dst, cnt, tmp, result);

  lduh(src, 0, tmp);

  bind(Lloop);
  inc(src, sizeof(jchar));
  cmp(tmp, 0xff);
  // annul zeroing if branch is not taken to preserve original count
  br(Assembler::greater, true, Assembler::pn, Ldone); // don't check xcc
  delayed()->mov(G0, result); // 0 - failed
  deccc(cnt);
  stb(tmp, dst, 0);
  inc(dst);
  // annul LDUH if branch is not taken to prevent access past end of string
  br(Assembler::notZero, true, Assembler::pt, Lloop);
  delayed()->lduh(src, 0, tmp); // hoisted
}

// Inflate byte[] to char[] by inflating 16 bytes at once.
void C2_MacroAssembler::string_inflate_16(Register src, Register dst, Register cnt, Register tmp,
                                          FloatRegister ftmp1, FloatRegister ftmp2, FloatRegister ftmp3, FloatRegister ftmp4, Label& Ldone) {
  Label Lloop, Lslow;
  assert(UseVIS >= 3, "VIS3 is required");
  assert_different_registers(src, dst, cnt, tmp);
  assert_different_registers(ftmp1, ftmp2, ftmp3, ftmp4);

  // Check if cnt >= 8 (= 16 bytes)
  cmp(cnt, 8);
  br(Assembler::less, false, Assembler::pn, Lslow);
  delayed()->nop();

  // Check for 8-byte alignment of src and dst
  or3(src, dst, tmp);
  andcc(tmp, 7, G0);
  br(Assembler::notZero, false, Assembler::pn, Lslow);
  // Initialize float register to zero
  FloatRegister zerof = ftmp4;
  delayed()->fzero(FloatRegisterImpl::D, zerof);

  // Load first 8 bytes
  ldf(FloatRegisterImpl::D, src, 0, ftmp1);

  bind(Lloop);
  inc(src, 8);
  dec(cnt, 8);

  // Inflate the string by interleaving each byte from the source array
  // with a zero byte and storing the result in the destination array.
  fpmerge(zerof, ftmp1->successor(), ftmp2);
  stf(FloatRegisterImpl::D, ftmp2, dst, 8);
  fpmerge(zerof, ftmp1, ftmp3);
  stf(FloatRegisterImpl::D, ftmp3, dst, 0);

  inc(dst, 16);

  cmp(cnt, 8);
  // annul LDX if branch is not taken to prevent access past end of string
  br(Assembler::greaterEqual, true, Assembler::pt, Lloop);
  delayed()->ldf(FloatRegisterImpl::D, src, 0, ftmp1);

  // Fallback to slow version
  bind(Lslow);
}

// Inflate byte[] to char[].
void C2_MacroAssembler::string_inflate(Register src, Register dst, Register cnt, Register tmp, Label& Ldone) {
  Label Loop;
  assert_different_registers(src, dst, cnt, tmp);

  ldub(src, 0, tmp);
  bind(Loop);
  inc(src);
  deccc(cnt);
  sth(tmp, dst, 0);
  inc(dst, sizeof(jchar));
  // annul LDUB if branch is not taken to prevent access past end of string
  br(Assembler::notZero, true, Assembler::pt, Loop);
  delayed()->ldub(src, 0, tmp); // hoisted
}

void C2_MacroAssembler::string_compare(Register str1, Register str2,
                                       Register cnt1, Register cnt2,
                                       Register tmp1, Register tmp2,
                                       Register result, int ae) {
  Label Ldone, Lloop;
  assert_different_registers(str1, str2, cnt1, cnt2, tmp1, result);
  int stride1, stride2;

  // Note: Making use of the fact that compareTo(a, b) == -compareTo(b, a)
  // we interchange str1 and str2 in the UL case and negate the result.
  // Like this, str1 is always latin1 encoded, expect for the UU case.

  if (ae == StrIntrinsicNode::LU || ae == StrIntrinsicNode::UL) {
    srl(cnt2, 1, cnt2);
  }

  // See if the lengths are different, and calculate min in cnt1.
  // Save diff in case we need it for a tie-breaker.
  Label Lskip;
  Register diff = tmp1;
  subcc(cnt1, cnt2, diff);
  br(Assembler::greater, true, Assembler::pt, Lskip);
  // cnt2 is shorter, so use its count:
  delayed()->mov(cnt2, cnt1);
  bind(Lskip);

  // Rename registers
  Register limit1 = cnt1;
  Register limit2 = limit1;
  Register chr1   = result;
  Register chr2   = cnt2;
  if (ae == StrIntrinsicNode::LU || ae == StrIntrinsicNode::UL) {
    // We need an additional register to keep track of two limits
    assert_different_registers(str1, str2, cnt1, cnt2, tmp1, tmp2, result);
    limit2 = tmp2;
  }

  // Is the minimum length zero?
  cmp(limit1, (int)0); // use cast to resolve overloading ambiguity
  br(Assembler::equal, true, Assembler::pn, Ldone);
  // result is difference in lengths
  if (ae == StrIntrinsicNode::UU) {
    delayed()->sra(diff, 1, result);  // Divide by 2 to get number of chars
  } else {
    delayed()->mov(diff, result);
  }

  // Load first characters
  if (ae == StrIntrinsicNode::LL) {
    stride1 = stride2 = sizeof(jbyte);
    ldub(str1, 0, chr1);
    ldub(str2, 0, chr2);
  } else if (ae == StrIntrinsicNode::UU) {
    stride1 = stride2 = sizeof(jchar);
    lduh(str1, 0, chr1);
    lduh(str2, 0, chr2);
  } else {
    stride1 = sizeof(jbyte);
    stride2 = sizeof(jchar);
    ldub(str1, 0, chr1);
    lduh(str2, 0, chr2);
  }

  // Compare first characters
  subcc(chr1, chr2, chr1);
  br(Assembler::notZero, false, Assembler::pt, Ldone);
  assert(chr1 == result, "result must be pre-placed");
  delayed()->nop();

  // Check if the strings start at same location
  cmp(str1, str2);
  brx(Assembler::equal, true, Assembler::pn, Ldone);
  delayed()->mov(G0, result);  // result is zero

  // We have no guarantee that on 64 bit the higher half of limit is 0
  signx(limit1);

  // Get limit
  if (ae == StrIntrinsicNode::LU || ae == StrIntrinsicNode::UL) {
    sll(limit1, 1, limit2);
    subcc(limit2, stride2, chr2);
  }
  subcc(limit1, stride1, chr1);
  br(Assembler::zero, true, Assembler::pn, Ldone);
  // result is difference in lengths
  if (ae == StrIntrinsicNode::UU) {
    delayed()->sra(diff, 1, result);  // Divide by 2 to get number of chars
  } else {
    delayed()->mov(diff, result);
  }

  // Shift str1 and str2 to the end of the arrays, negate limit
  add(str1, limit1, str1);
  add(str2, limit2, str2);
  neg(chr1, limit1);  // limit1 = -(limit1-stride1)
  if (ae == StrIntrinsicNode::LU || ae == StrIntrinsicNode::UL) {
    neg(chr2, limit2);  // limit2 = -(limit2-stride2)
  }

  // Compare the rest of the characters
  load_sized_value(Address(str1, limit1), chr1, (ae == StrIntrinsicNode::UU) ? 2 : 1, false);

  bind(Lloop);
  load_sized_value(Address(str2, limit2), chr2, (ae == StrIntrinsicNode::LL) ? 1 : 2, false);

  subcc(chr1, chr2, chr1);
  br(Assembler::notZero, false, Assembler::pt, Ldone);
  assert(chr1 == result, "result must be pre-placed");
  delayed()->inccc(limit1, stride1);
  if (ae == StrIntrinsicNode::LU || ae == StrIntrinsicNode::UL) {
    inccc(limit2, stride2);
  }

  // annul LDUB if branch is not taken to prevent access past end of string
  br(Assembler::notZero, true, Assembler::pt, Lloop);
  delayed()->load_sized_value(Address(str1, limit1), chr1, (ae == StrIntrinsicNode::UU) ? 2 : 1, false);

  // If strings are equal up to min length, return the length difference.
  if (ae == StrIntrinsicNode::UU) {
    // Divide by 2 to get number of chars
    sra(diff, 1, result);
  } else {
    mov(diff, result);
  }

  // Otherwise, return the difference between the first mismatched chars.
  bind(Ldone);
  if(ae == StrIntrinsicNode::UL) {
    // Negate result (see note above)
    neg(result);
  }
}

void C2_MacroAssembler::array_equals(bool is_array_equ, Register ary1, Register ary2,
                                     Register limit, Register tmp, Register result, bool is_byte) {
  Label Ldone, Lloop, Lremaining;
  assert_different_registers(ary1, ary2, limit, tmp, result);

  int length_offset  = arrayOopDesc::length_offset_in_bytes();
  int base_offset    = arrayOopDesc::base_offset_in_bytes(is_byte ? T_BYTE : T_CHAR);
  assert(base_offset % 8 == 0, "Base offset must be 8-byte aligned");

  if (is_array_equ) {
    // return true if the same array
    cmp(ary1, ary2);
    brx(Assembler::equal, true, Assembler::pn, Ldone);
    delayed()->mov(1, result);  // equal

    br_null(ary1, true, Assembler::pn, Ldone);
    delayed()->clr(result);     // not equal

    br_null(ary2, true, Assembler::pn, Ldone);
    delayed()->clr(result);     // not equal

    // load the lengths of arrays
    ld(Address(ary1, length_offset), limit);
    ld(Address(ary2, length_offset), tmp);

    // return false if the two arrays are not equal length
    cmp(limit, tmp);
    br(Assembler::notEqual, true, Assembler::pn, Ldone);
    delayed()->clr(result);     // not equal
  }

  cmp_zero_and_br(Assembler::zero, limit, Ldone, true, Assembler::pn);
  delayed()->mov(1, result); // zero-length arrays are equal

  if (is_array_equ) {
    // load array addresses
    add(ary1, base_offset, ary1);
    add(ary2, base_offset, ary2);
    // set byte count
    if (!is_byte) {
      sll(limit, exact_log2(sizeof(jchar)), limit);
    }
  } else {
    // We have no guarantee that on 64 bit the higher half of limit is 0
    signx(limit);
  }

#ifdef ASSERT
  // Sanity check for doubleword (8-byte) alignment of ary1 and ary2.
  // Guaranteed on 64-bit systems (see arrayOopDesc::header_size_in_bytes()).
  Label Laligned;
  or3(ary1, ary2, tmp);
  andcc(tmp, 7, tmp);
  br_null_short(tmp, Assembler::pn, Laligned);
  STOP("First array element is not 8-byte aligned.");
  should_not_reach_here();
  bind(Laligned);
#endif

  // Shift ary1 and ary2 to the end of the arrays, negate limit
  add(ary1, limit, ary1);
  add(ary2, limit, ary2);
  neg(limit, limit);

  // MAIN LOOP
  // Load and compare array elements of size 'byte_width' until the elements are not
  // equal or we reached the end of the arrays. If the size of the arrays is not a
  // multiple of 'byte_width', we simply read over the end of the array, bail out and
  // compare the remaining bytes below by skipping the garbage bytes.
  ldx(ary1, limit, result);
  bind(Lloop);
  ldx(ary2, limit, tmp);
  inccc(limit, 8);
  // Bail out if we reached the end (but still do the comparison)
  br(Assembler::positive, false, Assembler::pn, Lremaining);
  delayed()->cmp(result, tmp);
  // Check equality of elements
  brx(Assembler::equal, false, Assembler::pt, target(Lloop));
  delayed()->ldx(ary1, limit, result);

  ba(Ldone);
  delayed()->clr(result); // not equal

  // TAIL COMPARISON
  // We got here because we reached the end of the arrays. 'limit' is the number of
  // garbage bytes we may have compared by reading over the end of the arrays. Shift
  // out the garbage and compare the remaining elements.
  bind(Lremaining);
  // Optimistic shortcut: elements potentially including garbage are equal
  brx(Assembler::equal, true, Assembler::pt, target(Ldone));
  delayed()->mov(1, result); // equal
  // Shift 'limit' bytes to the right and compare
  sll(limit, 3, limit); // bytes to bits
  srlx(result, limit, result);
  srlx(tmp, limit, tmp);
  cmp(result, tmp);
  clr(result);
  movcc(Assembler::equal, false, xcc, 1, result);

  bind(Ldone);
}

void C2_MacroAssembler::has_negatives(Register inp, Register size, Register result, Register t2, Register t3, Register t4, Register t5) {

  // test for negative bytes in input string of a given size
  // result 1 if found, 0 otherwise.

  Label Lcore, Ltail, Lreturn, Lcore_rpt;

  assert_different_registers(inp, size, t2, t3, t4, t5, result);

  Register i     = result;  // result used as integer index i until very end
  Register lmask = t2;      // t2 is aliased to lmask

  // INITIALIZATION
  // ===========================================================
  // initialize highbits mask -> lmask = 0x8080808080808080  (8B/64b)
  // compute unaligned offset -> i
  // compute core end index   -> t5
  Assembler::sethi(0x80808000, t2);   //! sethi macro fails to emit optimal
  add(t2, 0x80, t2);
  sllx(t2, 32, t3);
  or3(t3, t2, lmask);                 // 0x8080808080808080 -> lmask
  sra(size,0,size);
  andcc(inp, 0x7, i);                 // unaligned offset -> i
  br(Assembler::zero, true, Assembler::pn, Lcore); // starts 8B aligned?
  delayed()->add(size, -8, t5);       // (annuled) core end index -> t5

  // ===========================================================

  // UNALIGNED HEAD
  // ===========================================================
  // * unaligned head handling: grab aligned 8B containing unaligned inp(ut)
  // * obliterate (ignore) bytes outside string by shifting off reg ends
  // * compare with bitmask, short circuit return true if one or more high
  //   bits set.
  cmp(size, 0);
  br(Assembler::zero, true, Assembler::pn, Lreturn); // short-circuit?
  delayed()->mov(0,result);      // annuled so i not clobbered for following
  neg(i, t4);
  add(i, size, t5);
  ldx(inp, t4, t3);  // raw aligned 8B containing unaligned head -> t3
  mov(8, t4);
  sub(t4, t5, t4);
  sra(t4, 31, t5);
  andn(t4, t5, t5);
  add(i, t5, t4);
  sll(t5, 3, t5);
  sll(t4, 3, t4);   // # bits to shift right, left -> t5,t4
  srlx(t3, t5, t3);
  sllx(t3, t4, t3); // bytes outside string in 8B header obliterated -> t3
  andcc(lmask, t3, G0);
  brx(Assembler::notZero, true, Assembler::pn, Lreturn); // short circuit?
  delayed()->mov(1,result);      // annuled so i not clobbered for following
  add(size, -8, t5);             // core end index -> t5
  mov(8, t4);
  sub(t4, i, i);                 // # bytes examined in unalgn head (<8) -> i
  // ===========================================================

  // ALIGNED CORE
  // ===========================================================
  // * iterate index i over aligned 8B sections of core, comparing with
  //   bitmask, short circuit return true if one or more high bits set
  // t5 contains core end index/loop limit which is the index
  //     of the MSB of last (unaligned) 8B fully contained in the string.
  // inp   contains address of first byte in string/array
  // lmask contains 8B high bit mask for comparison
  // i     contains next index to be processed (adr. inp+i is on 8B boundary)
  bind(Lcore);
  cmp_and_br_short(i, t5, Assembler::greater, Assembler::pn, Ltail);
  bind(Lcore_rpt);
  ldx(inp, i, t3);
  andcc(t3, lmask, G0);
  brx(Assembler::notZero, true, Assembler::pn, Lreturn);
  delayed()->mov(1, result);    // annuled so i not clobbered for following
  add(i, 8, i);
  cmp_and_br_short(i, t5, Assembler::lessEqual, Assembler::pn, Lcore_rpt);
  // ===========================================================

  // ALIGNED TAIL (<8B)
  // ===========================================================
  // handle aligned tail of 7B or less as complete 8B, obliterating end of
  // string bytes by shifting them off end, compare what's left with bitmask
  // inp   contains address of first byte in string/array
  // lmask contains 8B high bit mask for comparison
  // i     contains next index to be processed (adr. inp+i is on 8B boundary)
  bind(Ltail);
  subcc(size, i, t4);   // # of remaining bytes in string -> t4
  // return 0 if no more remaining bytes
  br(Assembler::lessEqual, true, Assembler::pn, Lreturn);
  delayed()->mov(0, result); // annuled so i not clobbered for following
  ldx(inp, i, t3);       // load final 8B (aligned) containing tail -> t3
  mov(8, t5);
  sub(t5, t4, t4);
  mov(0, result);        // ** i clobbered at this point
  sll(t4, 3, t4);        // bits beyond end of string          -> t4
  srlx(t3, t4, t3);      // bytes beyond end now obliterated   -> t3
  andcc(lmask, t3, G0);
  movcc(Assembler::notZero, false, xcc,  1, result);
  bind(Lreturn);
}

