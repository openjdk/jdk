/*
 * Copyright (c) 2023, Intel Corporation. All rights reserved.
 *
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

#include "macroAssembler_x86.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "precompiled.hpp"
#include "stubGenerator_x86_64.hpp"
#include <functional>

/******************************************************************************/
//                     String handling intrinsics
//                     --------------------------
//
// Currently implements scheme described in http://0x80.pl/articles/simd-strfind.html
// Implementation can be found at https://github.com/WojciechMula/sse4-strstr
//
// The general idea is as follows:
// 1. Broadcast the first byte of the needle to a ymm register (32 bytes)
// 2. Broadcast the last byte of the needle to a different ymm register
// 3. Compare the first-byte ymm register to the first 32 bytes of the haystack
// 4. Compare the last-byte register to the 32 bytes of the haystack at the (k-1)st position
//    where k is the length of the needle
// 5. Logically AND the results of the comparison
//
// The result of the AND yields the position within the haystack where both the first
// and last bytes of the needle exist in their correct relative positions.  Check the full
// needle value against the haystack to confirm a match.
//
// This implementation uses memcmp to compare when the size of the needle is >= 32 bytes.
// For other needle sizes, the comparison is done with register compares to eliminate the
// overhead of the call (including range checks, etc.).  The size of the comparison is
// known, and it is also known to be safe reading the haystack for the full width of the needle.
//
// The original algorithm as implemented will potentially read past the end of the haystack.
// This implementation protects against that.  Instead of reading as many 32-byte chunks as
// possible and then handling the tail, we calculate the last position of a vaild 32-byte
// read and adjust the starting position of the second read such that the last read will not
// go beyond the end of the haystack.  So the first comparison is to the first 32 bytes of the
// haystack, and the second is offset by an amount to make the last read legal.  The remainder of
// the comparisons are done incrementing by 32 bytes.
//
// This will cause 16 bytes on average to be examined twice, but that is cheaper than the
// logic required for tail processing.
//
/******************************************************************************/

#define __ _masm->
#define __C2 ((C2_MacroAssembler *) _masm)->

// ASGASG
// #define DO_EARLY_BAILOUT 1
#undef DO_EARLY_BAILOUT

// Register definitions for consistency
// These registers can be counted on to always contain
// the correct values (once set up)
#define XMM_BYTE_0 xmm0
#define XMM_BYTE_K xmm1
#define XMM_BYTE_1 xmm12
#define save_r12 xmm4
#define save_r13 xmm5
#define save_r14 xmm6
#define save_r15 xmm7
#define save_rbx xmm8
#define nMinusK r10

// Global temporary xmm registers
#define XMM_TMP1 xmm15
#define XMM_TMP2 xmm14
#define XMM_TMP3 xmm2
#define XMM_TMP4 xmm3

//  This macro handles clearing the bits of the mask register depending
//  on whether we're comparing bytes or words.
#define CLEAR_BIT(mask, tmp) \
  if (isU) {                 \
    __ blsrl(tmp, mask);     \
    __ blsrl(mask, tmp);     \
  } else {                   \
    __ blsrl(mask, mask);    \
  }

#define NUMBER_OF_CASES 10

#undef STACK_SPACE
#undef MAX_NEEDLE_LEN_TO_EXPAND
#define MAX_NEEDLE_LEN_TO_EXPAND 0x28

// Stack layout:
#  define COPIED_HAYSTACK_STACK_OFFSET (0x0)  // MUST BE ZERO!
#  define COPIED_HAYSTACK_STACK_SIZE (64)     // MUST BE 64!

#  define EXPANDED_NEEDLE_STACK_OFFSET (COPIED_HAYSTACK_STACK_OFFSET + COPIED_HAYSTACK_STACK_SIZE)
#  define EXPANDED_NEEDLE_STACK_SIZE (MAX_NEEDLE_LEN_TO_EXPAND * 2 + 32)

#  define SAVED_HAYSTACK_STACK_OFFSET (EXPANDED_NEEDLE_STACK_OFFSET + EXPANDED_NEEDLE_STACK_SIZE)
#  define SAVED_HAYSTACK_STACK_SIZE (8)

#  define SAVED_INCREMENT_STACK_OFFSET (SAVED_HAYSTACK_STACK_OFFSET + SAVED_HAYSTACK_STACK_SIZE)
#  define SAVED_INCREMENT_STACK_SIZE (8)

#  define SAVED_TERM_ADDR_STACK_OFFSET (SAVED_INCREMENT_STACK_OFFSET + SAVED_INCREMENT_STACK_SIZE)
#  define SAVED_TERM_ADDR_STACK_SIZE (8)

#  define STACK_SPACE                                                                                                  \
    (COPIED_HAYSTACK_STACK_SIZE + EXPANDED_NEEDLE_STACK_SIZE + SAVED_HAYSTACK_STACK_SIZE + SAVED_INCREMENT_STACK_SIZE  \
     + SAVED_TERM_ADDR_STACK_SIZE)

// Forward declarations for helper functions
static void broadcast_additional_needles(bool sizeKnown, int size, Register needle,
                                         Register needleLen, Register rTmp,
                                         StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm);

static void compare_big_haystack_to_needle(bool sizeKnown, int size, Label &noMatch,
                                           Register haystack, Register hsLen, Register needleLen,
                                           bool isU, Register eq_mask, Register rTmp,
                                           Register rTmp2, XMMRegister rxTmp1, XMMRegister rxTmp2,
                                           XMMRegister rxTmp3, MacroAssembler *_masm);

static void compare_haystack_to_needle(bool sizeKnown, int size, Label &noMatch, Register haystack,
                                       bool isU, Register eq_mask, Register needleLen,
                                       Register rTmp, XMMRegister rxTmp1, XMMRegister rxTmp2,
                                       MacroAssembler *_masm);

static void big_case_loop_helper(bool sizeKnown, int size, Label &noMatch, Label &loop_top,
                                 Register eq_mask, Register hsPtrRet, Register needleLen,
                                 Register needle, Register haystack, Register hsLength,
                                 Register rTmp1, Register rTmp2, Register rTmp3, Register rTmp4,
                                 StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm);

static void preload_needle_helper(int size, Register needle, Register needleVal,
                                  StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm);

static void byte_compare_helper(int size, Label &L_noMatch, Label &L_matchFound, Register needle,
                                Register needleVal, Register haystack, Register mask,
                                Register foundIndex, Register tmp, StrIntrinsicNode::ArgEncoding ae,
                                MacroAssembler *_masm);

static void highly_optimized_short_cases(StrIntrinsicNode::ArgEncoding ae, Register haystack,
                                         Register haystack_len, Register needle,
                                         Register needle_len, XMMRegister XMM0, XMMRegister XMM1,
                                         Register mask, Register tmp, MacroAssembler *_masm);

static void setup_jump_tables(StrIntrinsicNode::ArgEncoding ae, Label &L_error, Label &L_checkRange,
                              Label &L_fixup, address *big_jump_table, address *small_jump_table,
                              MacroAssembler *_masm);

////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
//                         Start of generator
////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////

void StubGenerator::generate_string_indexof(address *fnptrs) {
  assert((int) StrIntrinsicNode::LL < 4, "Enum out of range");
  assert((int) StrIntrinsicNode::UL < 4, "Enum out of range");
  assert((int) StrIntrinsicNode::UU < 4, "Enum out of range");
  generate_string_indexof_stubs(fnptrs, StrIntrinsicNode::LL);
  generate_string_indexof_stubs(fnptrs, StrIntrinsicNode::UU);
  generate_string_indexof_stubs(fnptrs, StrIntrinsicNode::UL);
  assert(fnptrs[StrIntrinsicNode::LL] != nullptr, "LL not generated.");
  assert(fnptrs[StrIntrinsicNode::UL] != nullptr, "UL not generated.");
  assert(fnptrs[StrIntrinsicNode::UU] != nullptr, "UU not generated.");
}

void StubGenerator::generate_string_indexof_stubs(address *fnptrs, StrIntrinsicNode::ArgEncoding ae) {
  StubCodeMark mark(this, "StubRoutines", "stringIndexOf");
  bool isLL = (ae == StrIntrinsicNode::LL);
  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16
  assert(isLL || isUL || isUU, "Encoding not recognized");

  bool isReallyUL = isUL;

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //                         AVX2 code
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  assert(VM_Version::supports_avx2(), "Needs AVX2");

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //                         Code generation explanation:
  //
  //  The generator will generate code for three cases:
  //  1. Both needle and haystack are Latin-1 (single byte) encoded (LL)
  //  2. Both the needle and haystack are UTF-16 encoded (two bytes per character) (UU)
  //  3. The haystack is UTF-16 encoded and the needle is Latin-1 encoded (UL)
  //
  //  The case of the haystack being Latin-1 and the needle being UTF-16 is short-circuited
  //  so that we never get called in this case.
  //
  //  For the UL case (haystack UTF-16 and needle Latin-1), the needle will be expanded
  //  onto the stack (for size <= MAX_NEEDLE_LEN_TO_EXPAND) and the UU code will do the work.
  //  For UL where the needle size is > MAX_NEEDLE_LEN_TO_EXPAND, we default to a
  //  byte-by-byte comparison (this will be rare).
  //
  //  Note that the code assumes MAX_NEEDLE_LEN_TO_EXPAND is >= 32.
  //
  //  The UU and LL cases are identical except for the loop increments and loading
  //  of the characters into registers.  UU loads and compares words, LL - bytes.
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////

  const Register haystack_p     = c_rarg0;
  const Register haystack_len_p = c_rarg1;
  const Register needle_p       = c_rarg2;
  const Register needle_len_p   = c_rarg3;

  // Addresses of the two jump tables used for small needle processing
  address big_jump_table;
  address small_jump_table;

  Label L_begin;

  Label L_returnRBP, L_checkRangeAndReturn, L_returnError, L_bigCaseFixupAndReturn;
  Label L_bigSwitchTop, L_bigCaseDefault, L_smallCaseDefault;
  Label L_nextCheck, L_checksPassed, L_zeroCheckFailed, L_return;
  Label L_wcharBegin, L_continue, L_wideNoExpand;

  __ align(CodeEntryAlignment);
  fnptrs[ae] = __ pc();
  __ enter();  // required for proper stackwalking of RuntimeStub frame

  // Check for trivial cases
  // needle length == 0?
  __ cmpq(needle_len_p, 0);
  __ jg_b(L_nextCheck);
  __ xorq(rax, rax);
  __ leave();
  __ ret(0);

  // haystack length == 0?
  __ bind(L_nextCheck);
  __ testq(haystack_len_p, haystack_len_p);
  __ je(L_zeroCheckFailed);

  // haystack length >= needle length?
  __ movq(rax, haystack_len_p);
  __ subq(rax, needle_len_p);
  __ jge_b(L_checksPassed);

  __ bind(L_zeroCheckFailed);
  __ movq(rax, -1);
  __ leave();
  __ ret(0);

  __ bind(L_checksPassed);

  // Check for highly-optimized ability - haystack <= 32 bytes and needle <= 6 bytes
  // haystack_len is in elements, not bytes, for UTF-16
  __ cmpq(haystack_len_p, isU ? 16 : 32);
  __ ja(L_begin);

  // needle_len is in elements, not bytes, for UTF-16 <=> UTF-16
  __ cmpq(needle_len_p, isUU ? 3 : 6);
  __ ja(L_begin);

  // Handle short haystack and needle specially
  // Generated code does not return - either found or not
  highly_optimized_short_cases(ae, haystack_p, haystack_len_p, needle_p, needle_len_p, xmm0, xmm1,
                               r10, r11, _masm);

  // If we're generating UL, we need to "pretend" we're generating UU code
  // for the case where the needle can be expanded onto the stack
  if (isReallyUL) {
    ae = StrIntrinsicNode::UU;
    isUL = false;
    isUU = true;
  }

  // Set up jump tables.  Used when needle size <= NUMBER_OF_CASES
  setup_jump_tables(ae, L_returnRBP, L_checkRangeAndReturn, L_bigCaseFixupAndReturn,
                    &big_jump_table, &small_jump_table, _masm);

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //
  // The above code handles all cases (LL, UL, UU) for haystack size <= 32 bytes
  // and needle size <= 6 bytes.

  __ bind(L_begin);
  __ movdq(save_r12, r12);
  __ movdq(save_r13, r13);
  __ movdq(save_r14, r14);
  __ movdq(save_r15, r15);
  __ movdq(save_rbx, rbx);
#ifdef _WIN64
  __ push(rsi);
  __ push(rdi);
  __ push(rcx);
  __ push(r8);
  __ push(r9);

  // Move to Linux-style ABI
  __ movq(rdi, rcx);
  __ movq(rsi, rdx);
  __ movq(rdx, r8);
  __ movq(rcx, r9);
#endif

  const Register haystack     = rdi;
  const Register haystack_len = rsi;
  const Register needle       = rdx;
  const Register needle_len   = rcx;
  const Register save_ndl_len = r12;

  __ push(rbp);
  __ subptr(rsp, STACK_SPACE);

  // Assume failure
  __ movq(rbp, -1);

  if (isReallyUL) {
    // Branch out if doing wide chars
    __ jmp(L_wcharBegin);
  }

  if (!isReallyUL && isUU) {  // Adjust sizes of hs and needle
    __ shlq(needle_len, 1);
    __ shlq(haystack_len, 1);
  }

  // wide char processing comes here after expanding needle
  __ bind(L_continue);
  __ movq(nMinusK, haystack_len);
  __ subq(nMinusK, needle_len);

  __ movq(save_ndl_len, needle_len);
  __ movq(r14, needle);
  __ movq(rbx, haystack);

  {
    Label L_short;

    // Always need needle broadcast to ymm registers
    // Broadcast the beginning of needle into a vector register.
    if (isU) {
      __ vpbroadcastw(XMM_BYTE_0, Address(needle, 0), Assembler::AVX_256bit);
    } else {
      __ vpbroadcastb(XMM_BYTE_0, Address(needle, 0), Assembler::AVX_256bit);
    }

    // Broadcast the end of needle into a vector register.
    // For a single-element needle this is redundant but does no harm and
    // reduces code size as opposed to broadcasting only if used.
    if (isU) {
      __ vpbroadcastw(XMM_BYTE_K, Address(needle, needle_len, Address::times_1, -2), Assembler::AVX_256bit);
    } else {
      __ vpbroadcastb(XMM_BYTE_K, Address(needle, needle_len, Address::times_1, -1), Assembler::AVX_256bit);
    }

    __ bind(L_short);
  }

  // Do "big switch" if haystack size > 32
  __ cmpq(haystack_len, 0x20);
  __ ja_b(L_bigSwitchTop);

  // Copy the small (< 32 byte) haystack to the stack.  Allows for vector reads without page fault
  // Only done for small haystacks
  //
  // NOTE: This code assumes that the haystack points to a java array type AND there are
  //       at least 16 bytes of header preceeding the haystack pointer.
  //
  // This means that we're copying up to 15 bytes of the header onto the stack along
  // with the haystack bytes.  After the copy completes, we adjust the haystack pointer
  // to the valid haystack bytes on the stack.
  {
    Label L_moreThan16, L_adjustHaystack;

    const Register index = rax;
    const Register haystack = rbx;

    __ cmpq(haystack_len, 0x10);
    __ ja_b(L_moreThan16);

    __ movq(index, COPIED_HAYSTACK_STACK_OFFSET + 0x10);
    __ movdqu(XMM_TMP1, Address(haystack, haystack_len, Address::times_1, -0x10));
    __ movdqu(Address(rsp, COPIED_HAYSTACK_STACK_OFFSET), XMM_TMP1);
    __ jmpb(L_adjustHaystack);

    __ bind(L_moreThan16);
    __ movq(index, COPIED_HAYSTACK_STACK_OFFSET + 0x20);
    __ vmovdqu(XMM_TMP1, Address(haystack, haystack_len, Address::times_1, -0x20));
    __ vmovdqu(Address(rsp, COPIED_HAYSTACK_STACK_OFFSET), XMM_TMP1);

    __ bind(L_adjustHaystack);
    __ subq(index, haystack_len);
    __ leaq(haystack, Address(rsp, index, Address::times_1));
  }

  // Dispatch to handlers for small needle and small haystack
  __ leaq(r13, Address(save_ndl_len, -1));
  __ cmpq(r13, NUMBER_OF_CASES - 1);
  __ ja(L_smallCaseDefault);
  __ mov64(r15, (int64_t)small_jump_table);
  __ jmp(Address(r15, r13, Address::times_8));

  // Dispatch to handlers for small needle and large haystack
  __ bind(L_bigSwitchTop);
  __ leaq(rax, Address(save_ndl_len, -1));
  __ cmpq(rax, NUMBER_OF_CASES - 1);
  __ ja(L_bigCaseDefault);
  __ mov64(r15, (int64_t)big_jump_table);
  __ jmp(Address(r15, rax, Address::times_8));

  __ align(CodeEntryAlignment);

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //
  // Big case default:

  {
    Label L_loopTop, L_innerLoop, L_found;

    const Register hsPtrRet = rax;
    const Register mask = r8;
    const Register index = r9;
    const Register compLen = rbp;
    const Register haystackStart = rcx;
    const Register rScratch = r13;
    const Register needleLen = r12;
    const Register needle = r14;
    const Register haystack = rbx;
    const Register hsLength = rsi;
    const Register tmp1 = rdi;

#undef retval
#undef firstNeedleCompare
#undef tmp2
#undef tmp3
#define tmp2 r15
#define tmp3 rdx
    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    //
    // Big case default:
    //
    //  rbx: haystack
    //  rcx: k
    //  rdx: junk
    //  rsi: n
    //  rdi: haystack
    //  r10: n - k
    //  r12: k
    //  r13: junk
    //  r14: needle
    //  rbp: -1
    //  XMM_BYTE_0 - first element of needle broadcast
    //  XMM_BYTE_K - last element of needle broadcast

    __ bind(L_bigCaseDefault);

    // Loop construct handling for big haystacks
    // The helper binds L_loopTop which should be jumped to if potential matches fail to compare
    // equal (thus moving on to the next chunk of haystack).  If we run out of haystack, the
    // helper jumps to L_checkRangeAndReturn with a (-1) return value.
    big_case_loop_helper(false, 0, L_checkRangeAndReturn, L_loopTop, mask, hsPtrRet, needleLen,
                         needle, haystack, hsLength, tmp1, tmp2, tmp3, rScratch, ae, _masm);

    // big_case_loop_helper will fall through to this point if one or more potential matches are found
    // The mask will have a bitmask indicating the position of the potential matches within the haystack
    __ align(8);
    __ bind(L_innerLoop);
    __ tzcntl(index, mask);

#undef tmp2
#undef tmp3
#define retval r15
#define firstNeedleCompare rdx

    // Starting address in the haystack
    __ leaq(haystackStart, Address(hsPtrRet, index, Address::times_1, isU ? 4 : 2));
    // Starting address of first byte of needle to compare
    __ leaq(firstNeedleCompare, Address(needle, isU ? 4 : 2));
    // Number of bytes to compare
    __ leaq(compLen, Address(needleLen, isU ? -6 : -3));
    __C2 arrays_equals(false, haystackStart, firstNeedleCompare, compLen, retval, rScratch,
                        XMM_TMP3, XMM_TMP4, false /* char */, knoreg);
    __ testl(retval, retval);
    __ jne_b(L_found);

    CLEAR_BIT(mask, index);
    __ jne(L_innerLoop);
    __ jmp(L_loopTop);

    // Found exact match.  Compute offset from beginning of haystack
    __ bind(L_found);
    __ subq(hsPtrRet, haystack);
    __ addq(hsPtrRet, index);
    __ movq(r11, hsPtrRet);
    __ jmp(L_checkRangeAndReturn);

#undef retval
#undef firstNeedleCompare
  }

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //
  // Small case default:
  //
  //  rbx: haystack
  //  r14: needle
  //  r13: k - 1
  //  r12: k
  //  r10: n - k
  //  rbp: -1
  //  rdi: junk
  //  rsi: n
  //  rdx: junk
  //  rcx: junk
  //  XMM_BYTE_0 - first element of needle broadcast
  //  XMM_BYTE_K - last element of needle broadcast
  //
  //  Haystack always copied to stack, so 32-byte reads OK
  //  Haystack length < 32
  //  10 < needle length < 32

  {
    __ bind(L_smallCaseDefault);

    Label L_innerLoop;

    const Register firstNeedleCompare = rdx;
    const Register compLen = r9;
    const Register haystack = rbx;
    const Register mask = r8;
    const Register rTmp = rdi;
    const Register rTmp2 = r13;
    const Register rTmp3 = rax;

// r14 and r12 will be re-used later in this procedure
#undef needle
#define needle r14
#undef needleLen
#define needleLen r12

    broadcast_additional_needles(false, 0 /* unknown */, needle, needleLen, rTmp3, ae, _masm);

    __ leaq(firstNeedleCompare, Address(needle, isU ? 4 : 2));
    __ leaq(compLen, Address(needleLen, isU ? -6 : -3));

    //  firstNeedleCompare has address of second element of needle
    //  compLen has length of comparison to do

    compare_haystack_to_needle(false, 0, L_returnRBP, haystack, isU, mask, needleLen, rTmp3,
                               XMM_TMP1, XMM_TMP2, _masm);

// NOTE: REGISTER RE-USE for r12 and r14
#undef needle
#undef saveCompLen
#define saveCompLen r14
#undef needleLen
#undef saveNeedleAddress
#define saveNeedleAddress r12

    __ movq(saveCompLen, compLen);
    __ movq(saveNeedleAddress, firstNeedleCompare);  // Save address of 2nd element of needle

    __ align(8);
    __ bind(L_innerLoop);
    __ tzcntl(r11, mask);

    __ leaq(rTmp, Address(haystack, r11, Address::times_1, isU ? 4 : 2));

    __C2 arrays_equals(false, rTmp, firstNeedleCompare, compLen, rTmp3, rTmp2, XMM_TMP3, XMM_TMP4, false /* char */,
                        knoreg);
    __ testl(rTmp3, rTmp3);
    __ jne_b(L_checkRangeAndReturn);

    __ movq(compLen, saveCompLen);
    __ movq(firstNeedleCompare, saveNeedleAddress);
    CLEAR_BIT(mask, rTmp3);
    __ jne(L_innerLoop);
    __ jmp(L_returnRBP);

#undef saveCompLen
#undef saveNeedleAddress
  }

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////

  __ bind(L_returnError);
  __ movq(rax, -1);
  __ jmpb(L_return);

  __ bind(L_returnRBP);
  __ movq(rax, rbp);

  __ bind(L_bigCaseFixupAndReturn);
  __ subq(rcx, rbx);
  __ addq(rcx, r8);

  __ movq(r11, rcx);

  __ bind(L_checkRangeAndReturn);
  __ movq(rax, -1);
  __ cmpq(r11, nMinusK);
  // __ cmovq(Assembler::belowEqual, rax, r11);
  __ ja_b(L_return);
  __ movq(rax, r11);

  __ bind(L_return);
  __ addptr(rsp, STACK_SPACE);
  __ pop(rbp);
#ifdef _WIN64
  __ pop(r9);
  __ pop(r8);
  __ pop(rcx);
  __ pop(rdi);
  __ pop(rsi);
#endif
  __ movdq(r12, save_r12);
  __ movdq(r13, save_r13);
  __ movdq(r14, save_r14);
  __ movdq(r15, save_r15);
  __ movdq(rbx, save_rbx);

  // Need to return elements for UTF-16 encodings
  if (isU) {
    __ sarq(rax, 1);
  }
  __ vzeroupper();

  __ leave();  // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  if (isReallyUL) {
    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    //                         Wide char code
    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    //
    // Pseudo-code:
    //
    // If needle length less than MAX_NEEDLE_LEN_TO_EXPAND, read the needle
    // bytes from r14 and write them as words onto the stack.  Then go to the
    // "regular" code.  This is equavilent to doing a UU comparison, since the
    // haystack will be in UTF-16.
    //
    // If the needle can't be expanded, process the same way as the default
    // cases above. That is, for each haystack chunk, compare the needle.
    __ bind(L_wcharBegin);

    ae = StrIntrinsicNode::UL;
    isUL = true;
    isUU = false;

    Label L_top, L_finished;

    const Register haystack = rdi;
    const Register hsLen = rsi;
    const Register needle = rdx;
    const Register nLen = rcx;

    const Register offset = rax;
    const Register index = rbx;
    const Register wr_index = r13;

    assert(MAX_NEEDLE_LEN_TO_EXPAND >= 32, "Small UL needles not supported");

    __ shlq(hsLen, 1);

    __ leaq(index, Address(nLen, nLen, Address::times_1));
    __ cmpq(index, hsLen);
    __ jg(L_returnRBP);

    __ cmpq(nLen, MAX_NEEDLE_LEN_TO_EXPAND);
    __ ja(L_wideNoExpand);

    //
    // Reads of existing needle are 16-byte chunks
    // Writes to copied needle are 32-byte chunks
    // Don't read past the end of the existing needle
    //
    // Start first read at [((ndlLen % 16) - 16) & 0xf]
    // outndx += 32
    // inndx += 16
    // cmp nndx, ndlLen
    // jae done
    //
    // Final index of start of needle @((16 - (ndlLen %16)) & 0xf) << 1
    //
    // Starting read for needle at -(16 - (nLen % 16))
    // Offset of needle in stack should be (16 - (nLen % 16)) * 2

    __ movq(index, needle_len);
    __ andq(index, 0xf);  // nLen % 16
    __ movq(offset, 0x10);
    __ subq(offset, index);  // 16 - (nLen % 16)
    __ movq(index, offset);
    __ shlq(offset, 1);  // * 2
    __ negq(index);      // -(16 - (nLen % 16))
    __ xorq(wr_index, wr_index);

    __ bind(L_top);
    // load needle[low-16]
    __ vpmovzxbw(xmm0, Address(needle, index, Address::times_1), Assembler::AVX_256bit);
    // store to stack
    __ vmovdqu(Address(rsp, wr_index, Address::times_1, EXPANDED_NEEDLE_STACK_OFFSET), xmm0);
    __ addq(index, 0x10);
    __ cmpq(index, needle_len);
    __ jae(L_finished);
    __ addq(wr_index, 32);
    __ jmpb(L_top);

    __ bind(L_finished);
    __ leaq(needle, Address(rsp, offset, Address::times_1, EXPANDED_NEEDLE_STACK_OFFSET));
    __ leaq(needle_len, Address(needle_len, needle_len));

    __ jmp(L_continue);

    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    //
    // Compare Latin-1 encoded needle against UTF-16 encoded haystack.
    //
    // The needle is more than MAX_NEEDLE_LEN_TO_EXPAND bytes in length, and the haystack
    // is at least as big.

    // Prepare for wchar anysize
    __ bind(L_wideNoExpand);

    {
      Label L_loopTop, L_temp, L_innerLoop, L_found, L_compareFull;
      Label doCompare, topLoop;

      ////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////////////////////////////////////////////////////////////////
      //
      //  rbx: haystack
      //  rcx: k
      //  rdx: junk
      //  rsi: n
      //  rdi: haystack
      //  r10: n - k
      //  r12: k
      //  r13: junk
      //  r14: needle
      //  rbp: -1
      //  XMM_BYTE_0 - first element of needle broadcast
      //  XMM_BYTE_K - last element of needle broadcast

      const Register rTmp = rax;
      const Register haystack = rbx;
      const Register saveNeedleAddress = rbx;  // NOTE re-use
      const Register origNeedleLen = rcx;
      const Register firstNeedleCompare = rdx;
      const Register hsLen = rsi;
      const Register origHsLen = rsi;  // NOTE re-use
      const Register rTmp2 = rdi;
      const Register mask = rbp;
      const Register rScratch = r8;
      const Register compLen = r9;
      const Register needleLen = r12;
      const Register hsIndex = r12;  // NOTE re-use
      const Register constOffset = r13;
      const Register needle = r14;
      const Register index = r14;  // NOTE re-use
      const Register haystackEnd = r15;

      const XMMRegister result = XMM_TMP3;
      const XMMRegister cmp_0 = XMM_TMP3;
      const XMMRegister cmp_k = XMM_TMP4;

      const XMMRegister saveCompLen = XMM_TMP2;
      const XMMRegister saveIndex = XMM_TMP1;

      // Move registers into expected registers for rest of this routine
      __ movq(rbx, rdi);
      __ movq(r12, rcx);
      __ movq(r14, rdx);

      __ movq(rTmp, origNeedleLen);
      __ shlq(rTmp, 1);
      __ movq(rScratch, origHsLen);
      __ subq(rScratch, rTmp);
      __ cmpq(rScratch, 0x20);
      __ jl(L_compareFull);

      // Now there is room for a 32-byte read for the last iteration

      // Always need needle broadcast to ymm registers
      __ movzbl(rax, Address(needle));  // First byte of needle
      __ movdl(XMM_BYTE_0, rax);
      // 1st byte of needle in words
      __ vpbroadcastw(XMM_BYTE_0, XMM_BYTE_0, Assembler::AVX_256bit);

      __ movzbl(rax, Address(needle, needle_len, Address::times_1,
                              -1));  // Last byte of needle
      __ movdl(XMM_BYTE_K, rax);
      __ vpbroadcastw(XMM_BYTE_K, XMM_BYTE_K,
                      Assembler::AVX_256bit);  // Last byte of needle in words

      // __ bind(L_bigCaseDefault);
      __ movq(r11, -1);

      broadcast_additional_needles(false, 0 /* unknown */, needle, origNeedleLen, rax, ae, _masm);

      __ leaq(haystackEnd, Address(haystack, hsLen, Address::times_1));

      __ leaq(firstNeedleCompare, Address(needle, 2));
      __ leaq(compLen, Address(needleLen, -2));

      //  firstNeedleCompare has address of second element of needle
      //  compLen has length of comparison to do

      // Save haystack
      __ movq(Address(rsp, SAVED_HAYSTACK_STACK_OFFSET), haystack);

      __ movq(index, origHsLen);
      __ negptr(index);  // incr

      // constant offset from end for full 32-byte read
      __ movq(constOffset, origHsLen);
      __ shlq(origNeedleLen, 1);
      __ subq(constOffset, origNeedleLen);
      __ andq(constOffset, 0x1f);
      __ negptr(constOffset);
      __ jmpb(L_temp);

      __ bind(L_loopTop);
      __ addq(index, 32);
      __ subq(origHsLen, 32);
      __ jle(L_returnError);
      __ cmpq(index, constOffset);
      // __ cmovq(Assembler::greater, index, constOffset);
      {
        Label L_tmp;
        __ jle_b(L_tmp);
        __ movq(index, constOffset);
        __ bind(L_tmp);
      }

      __ bind(L_temp);
      __ movq(hsIndex, origNeedleLen);
      __ addq(hsIndex, index);

      // Compare first byte of needle to haystack
      __ vpcmpeqw(cmp_0, XMM_BYTE_0, Address(haystackEnd, index), Assembler::AVX_256bit);
      // Compare last byte of needle to haystack at proper position
      __ vpcmpeqw(cmp_k, XMM_BYTE_K, Address(haystackEnd, hsIndex, Address::times_1, -2), Assembler::AVX_256bit);
      __ vpand(result, cmp_k, cmp_0, Assembler::AVX_256bit);
#ifdef DO_EARLY_BAILOUT
      __ vpmovmskb(mask, result, Assembler::AVX_256bit);
      __ testl(mask, mask);
      __ je_b(L_loopTop);
#endif

      // Compare second byte of needle to haystack
      __ vpcmpeqw(cmp_k, XMM_BYTE_1, Address(haystackEnd, index, Address::times_1, 2), Assembler::AVX_256bit);
      __ vpand(result, cmp_k, result, Assembler::AVX_256bit);
      __ vpmovmskb(mask, result, Assembler::AVX_256bit);
      __ testl(mask, mask);
      __ je_b(L_loopTop);

      __ align(8);
      __ bind(L_innerLoop);
      __ tzcntl(rTmp, mask);

      __ movdq(saveIndex, rTmp);
      __ movdq(saveCompLen, compLen);
      // Save address of nth element of needle
      __ movq(saveNeedleAddress, firstNeedleCompare);

      __ leaq(rTmp2, Address(haystackEnd, index, Address::times_1, 4));
      __ addq(rTmp2, rTmp);
      __C2 arrays_equals(false, rTmp2, firstNeedleCompare, compLen, rTmp, rScratch, XMM_TMP3,
                         XMM_TMP4, false /* char */, knoreg, true /* expand_ary2 */);
      __ testl(rTmp, rTmp);
      __ jne_b(L_found);

      __ movdq(compLen, saveCompLen);
      __ movq(firstNeedleCompare, saveNeedleAddress);
      CLEAR_BIT(mask, rTmp);
      __ jne(L_innerLoop);
      __ jmp(L_loopTop);

      __ bind(L_found);
      __ movdq(rTmp, saveIndex);
      __ leaq(rScratch, Address(haystackEnd, index, Address::times_1));
      __ subq(rScratch, Address(rsp, SAVED_HAYSTACK_STACK_OFFSET));
      __ addq(rScratch, rTmp);
      __ movq(r11, rScratch);
      __ jmp(L_checkRangeAndReturn);

      __ bind(L_compareFull);

      // rScratch has n - k.  Compare entire string word-by-word
      __ xorq(r11, r11);
      __ movq(nMinusK, rScratch);
      __ jmpb(doCompare);

      __ bind(topLoop);
      __ addq(r11, 2);
      __ cmpq(r11, rScratch);
      __ jg(L_returnRBP);

      __ bind(doCompare);
      __ leaq(r9, Address(haystack, r11));
      __ leaq(r12, Address(needle, 0));
      __ movq(r13, origNeedleLen);

      __C2 arrays_equals(false, r9, r12, r13, rax, rdx, XMM_TMP3, XMM_TMP4, false /* char */,
                         knoreg, true /* expand_ary2 */);
      __ testq(rax, rax);
      __ jz(topLoop);

      // Match found
      __ jmp(L_checkRangeAndReturn);
    }
  }

  return;
}

// Helper for broadcasting needle elements to ymm registers for compares
//
// For UTF-16 encoded needles, broadcast a word at the proper offset to the ymm
// register (case UU)
// For the UTF-16 encoded haystack with Latin1 encoded needle (case UL) we have
// to read into a temp register to zero-extend the single byte needle value, then
// broadcast words to the ymm register.
//
// Parameters:
// sizeKnown - True if size known at compile time
// size - the size of the needle.  Pass 0 if unknown at compile time
// needle - the address of the first byte of the needle
// needleLen - length of needle if !sizeKnown
// isUU and isUL - true if argument encoding is UU or UL, respectively
// _masm - Current MacroAssembler instance pointer
static void broadcast_additional_needles(bool sizeKnown, int size, Register needle,
                                         Register needleLen, Register rTmp,
                                         StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {
  Label L_done;

  assert_different_registers(needle, needleLen, rTmp);

  bool isLL = (ae == StrIntrinsicNode::LL);
  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = (isUU || isUL);

  size = sizeKnown ? size : NUMBER_OF_CASES + 1;

  if (!sizeKnown) {
    __ cmpq(needleLen, (isU ? 4 : 2));
    __ jl_b(L_done);
  }

  if (size > (isU ? 4 : 2)) {
    // Add compare for second byte
    if (isUU) {
      __ vpbroadcastw(XMM_BYTE_1, Address(needle, 2), Assembler::AVX_256bit);
    } else if (isUL) {
      __ movzbl(rTmp, Address(needle, 1));
      __ movdl(XMM_BYTE_1, rTmp);
      // 1st byte of needle in words
      __ vpbroadcastw(XMM_BYTE_1, XMM_BYTE_1, Assembler::AVX_256bit);
    } else {
      __ vpbroadcastb(XMM_BYTE_1, Address(needle, 1), Assembler::AVX_256bit);
    }
  }

  __ bind(L_done);
}

// Helper for comparing needle elements to a big haystack
//
// This helper compares bytes or words in the ymm registers to
// the proper positions within the haystack.  It will bail out early if
// no match found, otherwise it will progressively and together
// the comparison results, returning the answer at the end.
//
// On return, eq_mask will be set to the comparison mask value.  If no match
// is found, this helper will jump to noMatch.
//
// Parameters:
// sizeKnown - True if size known at compile time
// size - the size of the needle in bytes.  Pass 0 if unknown at compile time
// noMatch - label bound outside to jump to if there is no match
// haystack - the address of the first byte of the haystack
// hsLen - the sizeof the haystack in bytes
// needleLen - size of the needle in bytes known at runtime
// isU - true if argument encoding is either UU or UL
// eq_mask - The bit mask returned that holds the result of the comparison
// rTmp - a temporary register
// nMinusK - Size of haystack minus size of needle
// _masm - Current MacroAssembler instance pointer
//
// If (n - k) < 32, need to handle reading past end of haystack
static void compare_big_haystack_to_needle(bool sizeKnown, int size, Label &noMatch,
                                           Register haystack, Register hsLen, Register needleLen,
                                           bool isU, Register eq_mask, Register rTmp,
                                           Register rTmp2, XMMRegister rxTmp1, XMMRegister rxTmp2,
                                           XMMRegister rxTmp3, MacroAssembler *_masm) {

  assert_different_registers(eq_mask, haystack, needleLen, rTmp, hsLen, nMinusK, rTmp2);

  const XMMRegister result = rxTmp1;
  const XMMRegister cmp_0 = rxTmp2;
  const XMMRegister cmp_k = rxTmp3;
  const Register shiftVal = rTmp2;

#undef lastCompare
#define lastCompare rTmp

  std::function<void(XMMRegister dst, XMMRegister src, Address adr, int vector_len)> vpcmpeq;

  if (isU) {
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister src, Address adr, int vector_len) {
      __ vpcmpeqw(dst, src, adr, vector_len);
    };
  } else {
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister src, Address adr, int vector_len) {
      __ vpcmpeqb(dst, src, adr, vector_len);
    };
  }

  int sizeIncr = isU ? 2 : 1;

  Label L_OKtoCompareFull, L_done, L_specialCase_gt2;

  assert(!sizeKnown || (sizeKnown && ((size > 0) && (size <= NUMBER_OF_CASES))), "Incorrect size given");

  Address kThByte
      = sizeKnown ? Address(haystack, size - sizeIncr) : Address(haystack, needleLen, Address::times_1, -(sizeIncr));
  size = sizeKnown ? size : NUMBER_OF_CASES + 1;

  // Compare first byte of needle to haystack
     vpcmpeq(cmp_0, XMM_BYTE_0, Address(haystack, 0), Assembler::AVX_256bit);

  __ vpmovmskb(eq_mask, cmp_0, Assembler::AVX_256bit);
#ifdef DO_EARLY_BAILOUT
  __ testl(eq_mask, eq_mask);
  __ je(noMatch);
#endif

  if (size != sizeIncr) {
    __ cmpq(nMinusK, 32);
    __ jae(L_OKtoCompareFull);

    // If n-k less than 32, comparing the last byte of the needle will result
    // in reading past the end of the haystack.  Account for this here.
    __ leaq(lastCompare, Address(haystack, hsLen, Address::times_1, -32));
    __ movl(shiftVal, isU ? 30 : 31);
    __ subl(shiftVal, nMinusK);

       vpcmpeq(cmp_k, XMM_BYTE_K, Address(lastCompare, 0), Assembler::AVX_256bit);

#undef lastMask
#undef lastCompare
#define lastMask rTmp
    __ vpmovmskb(lastMask, cmp_k, Assembler::AVX_256bit);
    __ shrxl(lastMask, lastMask, shiftVal);
    __ andl(eq_mask, lastMask);

    if (size > sizeIncr * 2) {
      __ testl(eq_mask, eq_mask);
      __ je(noMatch);
      __ cmpq(hsLen, isU ? 34 : 33);
      __ jl(L_specialCase_gt2);
      vpcmpeq(cmp_k, XMM_BYTE_1, Address(haystack, 1 * sizeIncr), Assembler::AVX_256bit);
      __ vpmovmskb(lastMask, cmp_k, Assembler::AVX_256bit);
      __ andl(eq_mask, lastMask);
    }

    __ jmpb(L_done);

    __ bind(L_specialCase_gt2);
    // Comparing multiple bytes and haystack length == 32
    vpcmpeq(cmp_k, XMM_BYTE_1, Address(haystack, 0), Assembler::AVX_256bit);
    __ vpmovmskb(lastMask, cmp_k, Assembler::AVX_256bit);
    __ shrl(lastMask, sizeIncr);
    __ andl(eq_mask, lastMask);

    __ jmp(L_done);

    //////////////////////////////////////////////////////////////////////
    __ bind(L_OKtoCompareFull);

    // Compare last byte of needle to haystack at proper position
    vpcmpeq(cmp_k, XMM_BYTE_K, kThByte, Assembler::AVX_256bit);

    __ vpand(result, cmp_k, cmp_0, Assembler::AVX_256bit);

    if (size > sizeIncr * 2) {
#ifdef DO_EARLY_BAILOUT
      __ vpmovmskb(eq_mask, result, Assembler::AVX_256bit);
      __ testl(eq_mask, eq_mask);
      __ je(noMatch);
#endif
      vpcmpeq(cmp_k, XMM_BYTE_1, Address(haystack, 1 * sizeIncr), Assembler::AVX_256bit);
      __ vpand(result, cmp_k, result, Assembler::AVX_256bit);
    }

    __ vpmovmskb(eq_mask, result, Assembler::AVX_256bit);
  }

  __ bind(L_done);
  __ testl(eq_mask, eq_mask);
  __ je(noMatch);
  // At this point, we have at least one "match" where first and last bytes
  // of the needle are found the correct distance apart.
#undef lastCompare
#undef lastMask
}

// Helper for comparing needle elements to a small haystack
//
// This helper compares bytes or words in the ymm registers to
// the proper positions within the haystack.  It will bail out early if
// a match is not found, otherwise it will progressively and together
// the comparison results, returning the answer at the end.
//
// On return, eq_mask will be set to the comparison mask value.  If no match
// is found, this helper will jump to noMatch.
//
// Parameters:
// sizeKnown - if true, size is valid and needleLen invalid.
//             if false, size invalid and needleLen valid.
// size - the size of the needle.  Pass 0 if unknown at compile time
// noMatch - label bound outside to jump to if there is no match
// haystack - the address of the first byte of the haystack
// isU - true if argument encoding is either UU or UL
// eq_mask - The bit mask returned that holds the result of the comparison
// needleLen - a temporary register.  Only used if isUL true
// _masm - Current MacroAssembler instance pointer
//
// No need to worry about reading past end of haystack since haystack
// has been copied to the stack
//
// If !sizeKnown, needle is at least 11 bytes long
static void compare_haystack_to_needle(bool sizeKnown, int size, Label &noMatch, Register haystack,
                                       bool isU, Register eq_mask, Register needleLen,
                                       Register rTmp, XMMRegister rxTmp1, XMMRegister rxTmp2,
                                       MacroAssembler *_masm) {

  assert_different_registers(eq_mask, haystack, needleLen, rTmp, nMinusK);

  // NOTE: cmp_0 and result are the same register
  const XMMRegister cmp_0 = rxTmp1;
  const XMMRegister result = rxTmp1;
  const XMMRegister cmp_k = rxTmp2;

  std::function<void(XMMRegister dst, XMMRegister src, Address adr, int vector_len)> vpcmpeq;

  if (isU) {
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister src, Address adr, int vector_len) {
      __ vpcmpeqw(dst, src, adr, vector_len);
    };
  } else {
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister src, Address adr, int vector_len) {
      __ vpcmpeqb(dst, src, adr, vector_len);
    };
  }

  int sizeIncr = isU ? 2 : 1;

  assert((!sizeKnown) || (((size > 0) && (size <= NUMBER_OF_CASES))), "Incorrect size given");

  Address kThByte
      = sizeKnown ? Address(haystack, size - sizeIncr) : Address(haystack, needleLen, Address::times_1, -(sizeIncr));
  size = sizeKnown ? size : NUMBER_OF_CASES + 1;

  // Creates a mask of (n - k + 1) ones.  This prevents
  // recognizing any false-positives past the end of
  // the valid haystack.
  __ movq(rTmp, -1);
  __ movq(eq_mask, nMinusK);
  __ addq(eq_mask, 1);
  __ bzhiq(rTmp, rTmp, eq_mask);

  // Compare first byte of needle to haystack
     vpcmpeq(cmp_0, XMM_BYTE_0, Address(haystack, 0), Assembler::AVX_256bit);
  if (size != sizeIncr) {
    // Compare last byte of needle to haystack at proper position
    vpcmpeq(cmp_k, XMM_BYTE_K, kThByte, Assembler::AVX_256bit);

    __ vpand(result, cmp_k, cmp_0, Assembler::AVX_256bit);

    if (size > (sizeIncr * 2)) {
#ifdef DO_EARLY_BAILOUT
      __ vpmovmskb(eq_mask, result, Assembler::AVX_256bit);
      __ andq(eq_mask, rTmp);
      __ testl(eq_mask, eq_mask);
      __ je(noMatch);
#endif
      vpcmpeq(cmp_k, XMM_BYTE_1, Address(haystack, 1 * sizeIncr), Assembler::AVX_256bit);
      __ vpand(result, cmp_k, result, Assembler::AVX_256bit);
    }
  }

  __ vpmovmskb(eq_mask, result, Assembler::AVX_256bit);
  __ andq(eq_mask, rTmp);

  __ testl(eq_mask, eq_mask);
  __ je(noMatch);
  // At this point, we have at least one "match" where first and last bytes
  // of the needle are found the correct distance apart.
}

////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
// Helper for big haystack loop construct
//
// For UTF-16 encoded needles, broadcast a word at the proper offset to the ymm
// register (case UU)
// For the UTF-16 encoded haystack with Latin1 encoded needle (case UL) we have
// to read into a temp register to zero-extend the single byte needle value, then
// broadcast words to the ymm register.
//
// Parameters:
// sizeKnown - if true, size is valid and needleLen invalid.
// size - the size of the needle.  Pass 0 if unknown at compile time
// noMatch - label bound outside to jump to if there is no match
// loop_top - label bound inside this helper that should be branched to
//            for additional comparisons.
// eq_mask - The bit mask returned that holds the result of the comparison
// hsPtrRet - This will hold the place within the needle where a match is found
//            This is modified
// needleLen - The length of the needle
// ae - Argument encoding
// _masm - Current MacroAssembler instance pointer
//
// On entry:
//
//  rbx: haystack
//  rcx: k
//  rdx: junk
//  rsi: n
//  rdi: haystack
//  r10: n - k
//  r12: k
//  r13: junk
//  r14: needle
//  rbp: -1
//  XMM_BYTE_0 - first element of needle broadcast
//  XMM_BYTE_K - last element of needle broadcast

static void big_case_loop_helper(bool sizeKnown, int size, Label &noMatch, Label &loop_top,
                                 Register eq_mask, Register hsPtrRet, Register needleLen,
                                 Register needle, Register haystack, Register hsLength,
                                 Register rTmp1, Register rTmp2, Register rTmp3, Register rTmp4,
                                 StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {
  Label L_midLoop;

  assert_different_registers(eq_mask, hsPtrRet, needleLen, rdi, r15, rdx, rsi, rbx, r14, nMinusK);

  const Register last = rTmp1;
  const Register temp1 = rTmp2;
  const Register temp2 = rTmp3;
  const Register temp3 = rTmp4;

  bool isLL = (ae == StrIntrinsicNode::LL);
  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16

  broadcast_additional_needles(sizeKnown, size, needle, needleLen, temp1, ae, _masm);

  __ movq(r11, -1);

  // Read 32-byte chunks at a time until the last 32-byte read would go
  // past the end of the haystack.  Then, set the final read to read exactly
  // the number of bytes in the haystack.
  // For example, if haystack length is 45 and needle length is 13, the compares
  // will read the following bytes:
  //
  //  First compare          Last compare
  //   [  0 : 31]            [12 : 43]
  // Next compare will go past end of haystack ([32:63])
  // Adjust so final read is:
  //   [  1 : 32]            [13 : 44]

  __ movq(hsPtrRet, haystack);
  __ leaq(last, Address(haystack, nMinusK, Address::times_1, isU ? -30 : -31));
  __ jmpb(L_midLoop);

  __ align(16);
  __ bind(loop_top);
  __ cmpq(hsPtrRet, last);
  __ je(noMatch);
  __ addq(hsPtrRet, 32);

  __ cmpq(hsPtrRet, last);
  // __ cmovq(Assembler::aboveEqual, hsPtrRet, last);
  {
    __ jb_b(L_midLoop);
    __ movq(hsPtrRet, last);
  }

  __ bind(L_midLoop);

  // compare_big_haystack_to_needle will jump to loop_top until a match has been
  // found
  compare_big_haystack_to_needle(sizeKnown, size, loop_top, hsPtrRet, hsLength, needleLen, isU,
                                 eq_mask, temp2, temp3, XMM_TMP1, XMM_TMP2, XMM_TMP3, _masm);

  // At this point, we have at least one "match" where first and last bytes
  // of the needle are found the correct distance apart.
  //
  // NOTE: haystack (rbx) should be preserved; hsPtrRet(rcx) is expected to
  //    point to the haystack such that hsPtrRet[tzcntl(eq_mask)] points to
  //    the matched string.
}

static void preload_needle_helper(int size, Register needle, Register needleVal,
                                  StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {
  // Pre-load the value (correctly sized) of the needle for comparison purposes.

  assert_different_registers(needle, needleVal);

  bool isLL = (ae == StrIntrinsicNode::LL);
  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16

  int bytesAlreadyCompared = 0;
  int bytesLeftToCompare = 0;
  int offsetOfFirstByteToCompare = 0;

  bytesAlreadyCompared = isU ? 6 : 3;
  offsetOfFirstByteToCompare = isU ? 4 : 2;

  bytesLeftToCompare = size - bytesAlreadyCompared;
  assert((bytesLeftToCompare <= 8), "Too many bytes left to compare");

  if (bytesLeftToCompare <= 0) {
    return;
  }

  // At this point, there is at least one byte of the needle that needs to be
  // compared to the haystack.

  switch (bytesLeftToCompare) {
  case 1:
  case 2:
    __ movl(needleVal, Address(needle, (offsetOfFirstByteToCompare - 2)));
    break;

  case 3:
  case 4:
    __ movl(needleVal, Address(needle, offsetOfFirstByteToCompare));
    break;

  case 5:
  case 6:
    __ movq(needleVal, Address(needle, (offsetOfFirstByteToCompare - 2)));
    break;

  case 7:
  case 8:
    __ movq(needleVal, Address(needle, offsetOfFirstByteToCompare));
    break;

  default:
    break;
  }
}

static void byte_compare_helper(int size, Label &L_noMatch, Label &L_matchFound, Register needle,
                                Register needleVal, Register haystack, Register mask,
                                Register foundIndex, Register tmp, StrIntrinsicNode::ArgEncoding ae,
                                MacroAssembler *_masm) {
  // Compare size bytes of needle to haystack
  //
  // At a minimum, the first, second and last bytes of needle already compare equal
  // to the haystack, so there is no need to compare them again.

  Label L_loopTop;

  assert_different_registers(needle, needleVal, haystack, mask, foundIndex, tmp);

  bool isLL = (ae == StrIntrinsicNode::LL);
  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16

  int bytesAlreadyCompared = 0;
  int bytesLeftToCompare = 0;
  int offsetOfFirstByteToCompare = 0;

  Label temp;

  bytesAlreadyCompared = isU ? 6 : 3;
  offsetOfFirstByteToCompare = isU ? 4 : 2;

  bytesLeftToCompare = size - bytesAlreadyCompared;
  assert(bytesLeftToCompare <= 8, "Too many bytes left to compare");

  if (bytesLeftToCompare <= 0) {
    __ tzcntl(foundIndex, mask);
    __ jmp(L_matchFound);
    return;
  }

  // At this point, there is at least one byte of the needle that needs to be
  // compared to the haystack.

  // Load in the correct sized needle value for comparison.  Used when checking
  // bytes of the haystack after first/last have compared equal.
  preload_needle_helper(size, needle, needleVal, ae, _masm);

  __ align(8);
  __ bind(L_loopTop);
  __ tzcntl(foundIndex, mask);  // Index of match within haystack

  switch (bytesLeftToCompare) {
  case 1:
  case 2:
    __ cmpl(Address(haystack, foundIndex, Address::times_1, offsetOfFirstByteToCompare - 2),
            needleVal);
    __ je(L_matchFound);
    break;

  case 3:
  case 4:
    __ cmpl(Address(haystack, foundIndex, Address::times_1, offsetOfFirstByteToCompare), needleVal);
    __ je(L_matchFound);
    break;

  case 5:
  case 6:
    __ cmpq(Address(haystack, foundIndex, Address::times_1, offsetOfFirstByteToCompare - 2),
            needleVal);
    __ je(L_matchFound);
    break;

  case 7:
  case 8:
    __ cmpq(Address(haystack, foundIndex, Address::times_1, offsetOfFirstByteToCompare), needleVal);
    __ je(L_matchFound);
    break;
  }

  CLEAR_BIT(mask, tmp);  // Loop as long as there are other bits set
  __ jne(L_loopTop);
  __ jmp(L_noMatch);
}

// highly_optimized_short_cases
// We can handle the cases where haystack size is <= 32 bytes and needle size <= 6 bytes
// as a special case.  We first copy the haystack tpo the stack to avoid page faults.  A mask is
// generated with (n - k + 1) bits set that ensures matches past the end of the original
// haystack do not get considered during compares. In this equation, n is length of haystack
// and k is length of needle.
//
// A vector compare for the first needle byte is done against the haystack and anded with the mask.
// For needle size == 1, if there's a match we found it, otherwise failure.  The 2nd position
// of the needle is compared starting from the 2nd position of the haystack and anded with the
// mask.  If needle size == 2 and a match is found, success else failure.  This continues for
// all needle sizes up to 6 bytes.
//
//
static void highly_optimized_short_cases(StrIntrinsicNode::ArgEncoding ae, Register haystack,
                                         Register haystack_len, Register needle,
                                         Register needle_len, XMMRegister XMM0, XMMRegister XMM1,
                                         Register mask, Register tmp, MacroAssembler *_masm) {
  // Highly optimized special-cases
  Label L_noMatch, L_foundall, L_out;

  bool isLL = (ae == StrIntrinsicNode::LL);
  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16

  // Only optimize when haystack can fit on stack with room
  // left over for page fault prevention
  assert((COPIED_HAYSTACK_STACK_OFFSET == 0), "Must be zero!");
  assert((COPIED_HAYSTACK_STACK_SIZE == 64), "Must be 64!");

  // Copy incoming haystack onto stack
  {
    Label L_adjustHaystack, L_moreThan16;

    // Copy haystack to stack (haystack <= 32 bytes)
    __ subptr(rsp, COPIED_HAYSTACK_STACK_SIZE);
    __ cmpq(haystack_len, isU ? 0x8 : 0x10);
    __ ja_b(L_moreThan16);

    __ movq(tmp, COPIED_HAYSTACK_STACK_OFFSET + 0x10);
    __ movdqu(XMM0, Address(haystack, haystack_len, isU ? Address::times_2 : Address::times_1, -0x10));
    __ movdqu(Address(rsp, COPIED_HAYSTACK_STACK_OFFSET), XMM0);
    __ jmpb(L_adjustHaystack);

    __ bind(L_moreThan16);
    __ movq(tmp, COPIED_HAYSTACK_STACK_OFFSET + 0x20);
    __ vmovdqu(XMM0, Address(haystack, haystack_len, isU ? Address::times_2 : Address::times_1, -0x20));
    __ vmovdqu(Address(rsp, COPIED_HAYSTACK_STACK_OFFSET), XMM0);

    __ bind(L_adjustHaystack);
    __ subq(tmp, haystack_len);
    if (isU) {
      // For UTF-16, lengths are half
      __ subq(tmp, haystack_len);
    }
    __ leaq(haystack, Address(rsp, tmp, Address::times_1));
  }

  // Creates a mask of (n - k + 1) ones.  This prevents
  // recognizing any false-positives past the end of
  // the valid haystack.
  __ movq(mask, -1);
  __ subq(haystack_len, needle_len);
  __ incrementq(haystack_len);
  if (isU) {
    __ shlq(haystack_len, 1);
  }
  __ bzhiq(mask, mask, haystack_len);

  for (int size = 1; size <= (isUU ? 3 : 6); size++) {
    // Broadcast next needle byte into ymm register
    int needle_position = isUU ? (size - 1) * 2 : size - 1;
    int haystack_position = isU ? (size - 1) * 2 : size - 1;
    if (isUU) {
      __ vpbroadcastw(XMM0, Address(needle, needle_position), Assembler::AVX_256bit);
    } else if (isUL) {
      // Expand needle
      __ movzbl(rax, Address(needle, needle_position));
      __ movdl(XMM0, rax);
      // Byte of needle to words
      __ vpbroadcastw(XMM0, XMM0, Assembler::AVX_256bit);
    } else {
      __ vpbroadcastb(XMM0, Address(needle, needle_position), Assembler::AVX_256bit);
    }

    // Compare next byte.  Keep the comparison mask in mask, which will
    // accumulate
    if (isU) {
      __ vpcmpeqw(XMM1, XMM0, Address(haystack, haystack_position), Assembler::AVX_256bit);
    } else {
      __ vpcmpeqb(XMM1, XMM0, Address(haystack, haystack_position), Assembler::AVX_256bit);
    }
    __ vpmovmskb(tmp, XMM1, Assembler::AVX_256bit);
    __ andq(mask, tmp);  // Accumulate matched bytes
    __ testl(mask, mask);
    __ je(L_noMatch);

    if (size != (isUU ? 3 : 6)) {
      // Found a match for this needle size
      __ cmpq(needle_len, size);
      __ je(L_foundall);
    }
  }

  __ bind(L_foundall);
  __ tzcntl(rax, mask);

  if (isU) {
    __ shrl(rax, 1);
  }

  __ bind(L_out);
  __ addptr(rsp, COPIED_HAYSTACK_STACK_SIZE);
  __ vzeroupper();
  __ leave();
  __ ret(0);

  __ bind(L_noMatch);
  __ movq(rax, -1);
  __ jmpb(L_out);
}

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //
  // Set up jump table entries for both small and large haystack switches.

static void setup_jump_tables(StrIntrinsicNode::ArgEncoding ae, Label &L_error, Label &L_checkRange, Label &L_fixup,
                              address *big_jump_table, address *small_jump_table, MacroAssembler *_masm) {
  bool isLL = (ae == StrIntrinsicNode::LL);
  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16
  const XMMRegister byte_1    = XMM_BYTE_1;

  address big_hs_jmp_table[NUMBER_OF_CASES];  // Jump table for large haystacks
  address small_hs_jmp_table[NUMBER_OF_CASES];  // Jump table for small haystacks
  int jmp_ndx = 0;

  {
    ////////////////////////////////////////////////////////////////////////////////////////
    //
    // Small haystack (<32 bytes) switch
    //
    // Handle cases that were not handled in highly_optimized_short_cases, which will be
    // haystack size <= 32 bytes with 6 < needle size < NUMBER_OF_CASES bytes.

    ////////////////////////////////////////////////
    //  On entry to each case of small_hs, the register state is:
    //
    //  rax = unused
    //  rbx = &haystack
    //  rcx = haystack length
    //  rdx = &needle
    //  rsi = haystack length
    //  rdi = &haystack
    //  rbp = -1
    //  r8  = unused
    //  r9  = unused
    //  r10 = hs_len - needle len
    //  r11 = unused
    //  r12 = needle length
    //  r13 = (needle length - 1)
    //  r14 = &needle
    //  r15 = unused
    //  XMM_BYTE_0 - first element of needle, broadcast
    //  XMM_BYTE_K - last element of needle, broadcast
    //
    //  The haystack is <= 32 bytes
    //
    // If a match is not found, branch to L_returnRBP (which will always
    // return -1).
    //
    // If a match is found, jump to L_checkRangeAndReturn, which ensures the
    // matched needle is not past the end of the haystack.

    const Register haystack = rbx;
    const Register needle = r14;
    const Register needle_val = r8;
    const Register set_bit = r11;
    const Register eq_mask = rsi;
    const Register rTmp = rax;

    for (int i = (isUU ? 3 : 6); i < NUMBER_OF_CASES; i++) {
      small_hs_jmp_table[i] = __ pc();
      if (isU && ((i + 1) & 1)) {
        continue;
      } else {
        Label L_loopTop;

      broadcast_additional_needles(true, i + 1, needle, noreg, rTmp, ae, _masm);

      compare_haystack_to_needle(true, i + 1, L_error, haystack, isU, eq_mask, noreg, rTmp,
                                 XMM_TMP1, XMM_TMP2, _masm);

      byte_compare_helper(i + 1, L_error, L_checkRange, needle, needle_val, haystack, eq_mask,
                          set_bit, rTmp, ae, _masm);
      }
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////
  //
  // Large haystack (> 32 bytes) switch

  {

    ////////////////////////////////////////////////
    //  On entry to each case of large_hs, the register state is:
    //
    //  rax = unused
    //  rbx = &haystack
    //  rcx = haystack length
    //  rdx = &needle
    //  rsi = haystack length
    //  rdi = &haystack
    //  rbp = -1
    //  r8  = unused
    //  r9  = unused
    //  r10 = hs_len - needle len
    //  r11 = unused
    //  r12 = needle length
    //  r13 = (needle length - 1)
    //  r14 = &needle
    //  r15 = unused
    //  XMM_BYTE_0 - first element of needle, broadcast
    //  XMM_BYTE_K - last element of needle, broadcast
    //
    //  The haystack is >= 32 bytes

    const Register haystack = rbx;
    const Register needle = r14;
    const Register needle_len = r12;
    const Register needle_val = r15;
    const Register set_bit = r8;
    const Register eq_mask = r9;
    const Register hs_ptr = rcx;
    const Register hsLength = rsi;
    const Register rTmp1 = rdi;
    const Register rTmp2 = r15;
    const Register rTmp3 = rdx;
    const Register rTmp4 = r13;

    for (int i = 0; i < NUMBER_OF_CASES; i++) {
      big_hs_jmp_table[i] = __ pc();
      if (isU && ((i + 1) & 1)) {
        continue;
      } else {
        Label L_loopTop;

        big_case_loop_helper(true, i + 1, L_checkRange, L_loopTop, eq_mask, hs_ptr, needle_len,
                             needle, haystack, hsLength, rTmp1, rTmp2, rTmp3, rTmp4, ae, _masm);
        byte_compare_helper(i + 1, L_loopTop, L_fixup, needle, needle_val, hs_ptr, eq_mask, set_bit,
                            rTmp4, ae, _masm);
      }
    }
  }
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  // JUMP TABLES
  __ align(8);

  *big_jump_table = __ pc();

  for (jmp_ndx = 0; jmp_ndx < NUMBER_OF_CASES; jmp_ndx++) {
    __ emit_address(big_hs_jmp_table[jmp_ndx]);
  }

  *small_jump_table = __ pc();

  for (jmp_ndx = 0; jmp_ndx < NUMBER_OF_CASES; jmp_ndx++) {
    __ emit_address(small_hs_jmp_table[jmp_ndx]);
  }
}

#undef STACK_SPACE
#undef MAX_NEEDLE_LEN_TO_EXPAND
#undef CLEAR_BIT
#undef XMM_BYTE_0
#undef XMM_BYTE_K
#undef XMM_BYTE_1
#undef XMM_TMP1
#undef XMM_TMP2
#undef XMM_TMP3
#undef XMM_TMP4

#undef __
