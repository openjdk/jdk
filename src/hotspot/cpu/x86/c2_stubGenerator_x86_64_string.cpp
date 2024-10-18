/*
 * Copyright (c) 2024, Intel Corporation. All rights reserved.
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

#include "precompiled.hpp"
#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "opto/intrinsicnode.hpp"

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
#define CLEAR_BIT(mask) \
  if (isU) {                 \
    __ blsrl(mask, mask);     \
    __ blsrl(mask, mask);     \
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

static void broadcast_first_and_last_needle(Register needle, Register needle_len, Register rTmp,
                                            StrIntrinsicNode::ArgEncoding ae,
                                            MacroAssembler *_masm);

static void compare_big_haystack_to_needle(bool sizeKnown, int size, Label &noMatch,
                                           Register haystack, Register needleLen, Register eq_mask,
                                           XMMRegister rxTmp1, XMMRegister rxTmp2,
                                           XMMRegister rxTmp3, StrIntrinsicNode::ArgEncoding ae,
                                           MacroAssembler *_masm);

static void compare_haystack_to_needle(bool sizeKnown, int size, Label &noMatch, Register haystack,
                                       Register eq_mask, Register needleLen, Register rTmp,
                                       XMMRegister rxTmp1, XMMRegister rxTmp2,
                                       StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm);

static void big_case_loop_helper(bool sizeKnown, int size, Label &noMatch, Label &loop_top,
                                 Register eq_mask, Register hsPtrRet, Register needleLen,
                                 Register needle, Register haystack, Register hsLength,
                                 Register rTmp1, Register rTmp2, Register rTmp3, Register rTmp4,
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

static void vpcmpeq(XMMRegister dst, XMMRegister src, Address adr, int vector_len,
                    StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {
  if ((ae == StrIntrinsicNode::UL) || (ae == StrIntrinsicNode::UU)) {
      __ vpcmpeqw(dst, src, adr, vector_len);
  } else {
      __ vpcmpeqb(dst, src, adr, vector_len);
  }
}

static void generate_string_indexof_stubs(StubGenerator *stubgen, address *fnptrs,
                                          StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm);

////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
//                         Start of generator
////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////

void StubGenerator::generate_string_indexof(address *fnptrs) {
  assert((int) StrIntrinsicNode::LL < 4, "Enum out of range");
  assert((int) StrIntrinsicNode::UL < 4, "Enum out of range");
  assert((int) StrIntrinsicNode::UU < 4, "Enum out of range");
  generate_string_indexof_stubs(this, fnptrs, StrIntrinsicNode::LL, _masm);
  generate_string_indexof_stubs(this, fnptrs, StrIntrinsicNode::UU, _masm);
  generate_string_indexof_stubs(this, fnptrs, StrIntrinsicNode::UL, _masm);
  assert(fnptrs[StrIntrinsicNode::LL] != nullptr, "LL not generated.");
  assert(fnptrs[StrIntrinsicNode::UL] != nullptr, "UL not generated.");
  assert(fnptrs[StrIntrinsicNode::UU] != nullptr, "UU not generated.");
}

static void generate_string_indexof_stubs(StubGenerator *stubgen, address *fnptrs,
                                          StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {
  StubCodeMark mark(stubgen, "StubRoutines", "stringIndexOf");
  bool isLL = (ae == StrIntrinsicNode::LL);
  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16
  assert(isLL || isUL || isUU, "Encoding not recognized");

  // Keep track of isUL since we need to generate UU code in the main body
  // for the case where we expand the needle from bytes to words on the stack.
  // This is done at L_wcharBegin.  The algorithm used is:
  //  If the encoding is UL and the needle size is <= MAX_NEEDLE_LEN_TO_EXPAND,
  //  allocate space on the stack and expand the Latin-1 encoded needle.  Then
  //  effectively "recurse" into the mainline using UU encoding (since both the
  //  haystack and needle are now UTF-16 encoded).
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
  //  For UL where the needle size is > MAX_NEEDLE_LEN_TO_EXPAND and the haystack size minus
  //  the needle size is less than 32 bytes, we default to a
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

  Label L_returnError, L_bigCaseFixupAndReturn;
  Label L_bigSwitchTop, L_bigCaseDefault, L_smallCaseDefault;
  Label L_nextCheck, L_checksPassed, L_return;
  Label L_wcharBegin, L_continue, L_wideNoExpand, L_returnR11;

  __ align(CodeEntryAlignment);
  fnptrs[ae] = __ pc();
  __ enter();  // required for proper stackwalking of RuntimeStub frame

  // Check for trivial cases
  // needle length == 0?
  __ cmpq(needle_len_p, 0);
  __ jg_b(L_nextCheck);
  __ xorl(rax, rax);
  __ leave();
  __ ret(0);

  __ bind(L_nextCheck);
  // haystack length >= needle length?
  __ movq(rax, haystack_len_p);
  __ subq(rax, needle_len_p);
  __ jge_b(L_checksPassed);

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
  setup_jump_tables(ae, L_returnError, L_returnR11, L_bigCaseFixupAndReturn, &big_jump_table,
                    &small_jump_table, _masm);

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //
  // The above code handles all cases (LL, UL, UU) for haystack size <= 32 bytes
  // and needle size <= 6 bytes.
  //
  // Main processing proceeds as follows:
  //  Save state and setup stack, etc.
  //  If UL, jump to code to handle special-case UL situations (see L_wcharBegin below)
  //  Broadcast the first and last needle elements to XMM_BYTE_0 and XMM_BYTE_K, respectively
  //  If the length in bytes of the haystack is > 32, dispatch to the big switch handling code
  //  If the haystack length in bytes is <= 32:
  //    Copy the haystack to the stack.  This is done to prevent possible page faults and
  //      allows for reading full 32-byte chunks of the haystack.
  //    Dispatch to the small switch handling code
  //
  // Here, "big switch" and "small switch" refers to the haystack size: > 32 bytes for big
  // and <= 32 bytes for small.  The switches implement optimized code for handling 1 to
  // NUMBER_OF_CASES (currently 10) needle sizes for both big and small.  There are special
  // routines for handling needle sizes > NUMBER_OF_CASES (L_{big,small}CaseDefault).  These
  // cases use C2's arrays_equals() to compare the needle to the haystack.  The small cases
  // use specialized code for comparing the needle.
  //
  // The algorithm currently does vector comparisons for the first, last, and second bytes
  // of the needle and, where each of these needle elements matches the correct position
  // within the haystack, the "in-between" bytes are compared using the most efficient
  // instructions possible for short needles, or C2's arrays_equals for longer needles.

  __ align(CodeEntryAlignment);

  __ bind(L_begin);
  __ movdq(save_r12, r12);
  __ movdq(save_r13, r13);
  __ movdq(save_r14, r14);
  __ movdq(save_r15, r15);
  __ movdq(save_rbx, rbx);
#ifdef _WIN64
  __ push(rsi);
  __ push(rdi);

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

  if (isReallyUL) {
    // Branch out if doing UL
    __ jmp(L_wcharBegin);
  }

  if (!isReallyUL && isUU) {  // Adjust sizes of hs and needle
    // UU passes lengths in terms of chars - convert to bytes
    __ shlq(needle_len, 1);
    __ shlq(haystack_len, 1);
  }

  // UL processing comes here after expanding needle
  __ bind(L_continue);
  // nMinusK (haystack length in bytes minus needle length in bytes) is used several
  // places to determine whether a compare will read past the end of the haystack.
  __ movq(nMinusK, haystack_len);
  __ subq(nMinusK, needle_len);

  // Set up expected registers
  __ movq(save_ndl_len, needle_len);
  __ movq(r14, needle);
  __ movq(rbx, haystack);

  // Always need needle broadcast to ymm registers (XMM_BYTE_0 and XMM_BYTE_K)
  broadcast_first_and_last_needle(needle, needle_len, rax, ae, _masm);

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

    // Only a single vector load/store of either 16 or 32 bytes
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

    // Point the haystack at the correct location of the first byte of the "real" haystack on the stack
    __ bind(L_adjustHaystack);
    __ subq(index, haystack_len);
    __ leaq(haystack, Address(rsp, index, Address::times_1));
  }

  // Dispatch to handlers for small needle and small haystack
  // Note that needle sizes of 1-6 have been handled in highly_optimized_short_cases,
  // so the dispatch only has valid entries for 7-10.
  __ leaq(r13, Address(save_ndl_len, -1));
  __ cmpq(r13, NUMBER_OF_CASES - 1);
  __ ja(L_smallCaseDefault);
  __ lea(r15, InternalAddress(small_jump_table));
  __ jmp(Address(r15, r13, Address::times_8));

  // Dispatch to handlers for small needle and large haystack
  // For large haystacks, the jump table is fully populated (1-10)
  __ bind(L_bigSwitchTop);
  __ leaq(rax, Address(save_ndl_len, -1));
  __ cmpq(rax, NUMBER_OF_CASES - 1);
  __ ja(L_bigCaseDefault);
  __ lea(r15, InternalAddress(big_jump_table));
  __ jmp(Address(r15, rax, Address::times_8));

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  // Fixup and return routines

  // Return not found
  __ bind(L_returnError);
  __ movq(rax, -1);
  __ jmpb(L_return);

  // At this point, rcx has &haystack where match found, rbx has &haystack,
  // and r8 has the index where a match was found
  __ bind(L_bigCaseFixupAndReturn);
  __ subq(rcx, rbx);
  __ addq(rcx, r8);

  __ movq(r11, rcx);

  // r11 will contain the valid index.
  __ bind(L_returnR11);
  __ movq(rax, r11);

  // Restore stack, vzeroupper and return
  __ bind(L_return);
  __ addptr(rsp, STACK_SPACE);
  __ pop(rbp);
#ifdef _WIN64
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
    // Return value for UTF-16 is elements, not bytes
    // sar is used to preserve -1
    __ sarq(rax, 1);
  }
  __ vzeroupper();

  __ leave();  // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //
  // Big case default:
  //
  // Handle needle sizes > 10 bytes.  Uses C2's arrays_equals to compare the contents
  // of the needle to the haystack.

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

// #define used for registers that are re-used in the code
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
    // Big case default:  registers on entry
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
    //  rbp: junk
    //  XMM_BYTE_0 - first element of needle broadcast
    //  XMM_BYTE_K - last element of needle broadcast
    //
    // Set up in big_case_loop_helper
    //  XMM_BYTE_1 - second element of needle broadcast

    __ bind(L_bigCaseDefault);

    // Loop construct handling for big haystacks
    // The helper binds L_loopTop which should be jumped to if potential matches fail to compare
    // equal (thus moving on to the next chunk of haystack).  If we run out of haystack, the
    // helper jumps to L_returnError.
    big_case_loop_helper(false, 0, L_returnError, L_loopTop, mask, hsPtrRet, needleLen, needle,
                         haystack, hsLength, tmp1, tmp2, tmp3, rScratch, ae, _masm);

    // big_case_loop_helper will fall through to this point if one or more potential matches are found
    // The mask will have a bitmask indicating the position of the potential matches within the haystack
    __ align(OptoLoopAlignment);
    __ bind(L_innerLoop);
    __ tzcntl(index, mask);

// Re-use of r15 and rdx
#undef tmp2
#undef tmp3
#define retval r15
#define firstNeedleCompare rdx

    // Need a lot of registers here to preserve state across arrays_equals call

    // Starting address in the haystack
    __ leaq(haystackStart, Address(hsPtrRet, index, Address::times_1, isU ? 4 : 2));
        // Starting address of first byte of needle to compare
    __ leaq(firstNeedleCompare, Address(needle, isU ? 4 : 2));
        // Number of bytes to compare
    __ leaq(compLen, Address(needleLen, isU ? -6 : -3));

    // Call arrays_equals for both UU and LL cases as bytes should compare exact
    __C2 arrays_equals(false, haystackStart, firstNeedleCompare, compLen, retval, rScratch,
                        XMM_TMP3, XMM_TMP4, false /* char */, knoreg);
    __ testl(retval, retval);
    __ jne_b(L_found);

    // If more potential matches, continue at inner loop, otherwise go get another vector
    CLEAR_BIT(mask);
    __ jne(L_innerLoop);
    __ jmp(L_loopTop);

    // Found exact match.  Compute offset from beginning of haystack
    __ bind(L_found);
    __ subq(hsPtrRet, haystack);
    __ addq(hsPtrRet, index);
    __ movq(r11, hsPtrRet);
    __ jmp(L_returnR11);

#undef retval
#undef firstNeedleCompare
  }

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //
  // Small case default:
  //
  // Handle needle sizes > 10 bytes.  Uses C2's arrays_equals to compare the contents
  // of the needle to the haystack.

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //
  // Small case default: register on entry
  //
  //  rbx: haystack
  //  r14: needle
  //  r13: k - 1
  //  r12: k
  //  r10: n - k
  //  rbp: junk
  //  rdi: junk
  //  rsi: n
  //  rdx: junk
  //  rcx: junk
  //  XMM_BYTE_0 - first element of needle broadcast
  //  XMM_BYTE_K - last element of needle broadcast
  //
  // Set up in broadcast_additional_needles
  //  XMM_BYTE_1 - second element of needle broadcast
  //
  //  Haystack always copied to stack, so 32-byte reads OK
  //  Haystack length <= 32
  //  10 < needle length <= 32

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

    // For small haystacks we already know that the 1st, 2nd, and last bytes of the needle
    // compare equal, so we can reduce the byte count to arrays_equals
    __ leaq(firstNeedleCompare, Address(needle, isU ? 4 : 2));
    __ leaq(compLen, Address(needleLen, isU ? -6 : -3));

    //  firstNeedleCompare has address of third element of needle
    //  compLen has length of comparison to do (3 elements less than needle size)

    // Helper to compare the 1st, 2nd, and last byte of the needle to the haystack
    // in the correct position.  Since the haystack is < 32 bytes, not finding matching
    // needle bytes can just return failure.  Otherwise, we loop through the found
    // matches.
    compare_haystack_to_needle(false, 0, L_returnError, haystack, mask, needleLen, rTmp3, XMM_TMP1,
                               XMM_TMP2, ae, _masm);

// NOTE: REGISTER RE-USE for r12 and r14
#undef needle
#undef saveCompLen
#define saveCompLen r14
#undef needleLen
#undef saveNeedleAddress
#define saveNeedleAddress r12

    // Save registers stomped by arrays_equals
    __ movq(saveCompLen, compLen);
    __ movq(saveNeedleAddress, firstNeedleCompare);  // Save address of 2nd element of needle

    // Find index of a potential match
    __ align(OptoLoopAlignment);
    __ bind(L_innerLoop);
    __ tzcntl(r11, mask);

    __ leaq(rTmp, Address(haystack, r11, Address::times_1, isU ? 4 : 2));

    // Check for needle equality.  Handles UU and LL cases since byte comparison should be exact
    __C2 arrays_equals(false, rTmp, firstNeedleCompare, compLen, rTmp3, rTmp2, XMM_TMP3, XMM_TMP4,
                       false /* char */, knoreg);
    __ testl(rTmp3, rTmp3);
    __ jne(L_returnR11);

    // Restore saved registers
    __ movq(compLen, saveCompLen);
    __ movq(firstNeedleCompare, saveNeedleAddress);

    // Jump to inner loop if more matches to check, otherwise return not found
    CLEAR_BIT(mask);
    __ jne(L_innerLoop);
    __ jmp(L_returnError);

#undef saveCompLen
#undef saveNeedleAddress
  }

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
    // "regular" UU code.  This is equavilent to doing a UU comparison, since the
    // haystack will be in UTF-16.
    //
    // If the needle can't be expanded, process the same way as the default
    // cases above.
    __ bind(L_wcharBegin);

    // Restore argument encoding from UU back to UL for helpers
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

    // haystack length to bytes
    __ shlq(hsLen, 1);

    // Ensure haystack >= needle
    __ leaq(index, Address(nLen, nLen, Address::times_1));
    __ cmpq(index, hsLen);
    __ jg(L_returnError);

    // Can't expand large-ish needles
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
    // Final index of start of needle at ((16 - (ndlLen %16)) & 0xf) << 1
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
    // load needle and expand
    __ vpmovzxbw(xmm0, Address(needle, index, Address::times_1), Assembler::AVX_256bit);
    // store expanded needle to stack
    __ vmovdqu(Address(rsp, wr_index, Address::times_1, EXPANDED_NEEDLE_STACK_OFFSET), xmm0);
    __ addq(index, 0x10);
    __ cmpq(index, needle_len);
    __ jae(L_finished);
    __ addq(wr_index, 32);
    __ jmpb(L_top);

    // adjust pointer and length of needle
    __ bind(L_finished);
    __ leaq(needle, Address(rsp, offset, Address::times_1, EXPANDED_NEEDLE_STACK_OFFSET));
    __ leaq(needle_len, Address(needle_len, needle_len));

    // Go handle this the same as UU
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
      //  rbp: junk
      //  XMM_BYTE_0 - first element of needle broadcast
      //  XMM_BYTE_K - last element of needle broadcast

      const Register hsPtrRet = rax;
      const Register haystack = rbx;
      const Register haystackStart = rcx;
      const Register hsLength = rsi;
      const Register tmp1 = rdi;
      const Register compLen = rbp;
      const Register mask = r8;
      const Register index = r9;
      const Register needleLen = r12;
      const Register rScratch = r13;
      const Register needle = r14;

      // Move registers into expected registers for rest of this routine
      __ movq(rbx, rdi);
      __ movq(r12, rcx);
      __ movq(r14, rdx);

      // Set up nMinusK
      __ movq(tmp1, needleLen);
      __ shlq(tmp1, 1);
      __ movq(rScratch, hsLength);
      __ subq(rScratch, tmp1);
      __ movq(nMinusK, rScratch);

      // Check for room for a 32-byte read for the last iteration
      __ cmpq(nMinusK, 0x1f);
      __ jl(L_compareFull);

      // Always need needle broadcast to ymm registers
      broadcast_first_and_last_needle(needle, needleLen, tmp1, ae, _masm);

// Register redefinition for rbx and r15
#undef retval
#undef firstNeedleCompare
#undef tmp2
#undef tmp3
#define tmp2 r15
#define tmp3 rdx

      // Loop construct handling for big haystacks
      // The helper binds L_loopTop which should be jumped to if potential matches fail to compare
      // equal (thus moving on to the next chunk of haystack).  If we run out of haystack, the
      // helper jumps to L_returnError.
      big_case_loop_helper(false, 0, L_returnError, L_loopTop, mask, hsPtrRet, needleLen, needle,
                           haystack, hsLength, tmp1, tmp2, tmp3, rScratch, ae, _masm);

      // big_case_loop_helper will fall through to this point if one or more potential matches are
      // found The mask will have a bitmask indicating the position of the potential matches within
      // the haystack
      __ align(OptoLoopAlignment);
      __ bind(L_innerLoop);
      __ tzcntl(index, mask);

#undef tmp2
#undef tmp3
#define retval r15
#define firstNeedleCompare rdx

      // Note that we're comparing the full needle here even though in some paths
      // the 1st, 2nd, and last bytes are already known to be equal.  This is necessary
      // due to the handling of cases where nMinusK is < 32

      // Need a lot of registers here to preserve state across arrays_equals call

      // Starting address in the haystack
      __ leaq(haystackStart, Address(hsPtrRet, index));
      // Starting address of first byte of needle to compare
      __ movq(firstNeedleCompare, needle);
      // Number of bytes to compare
      __ movq(compLen, needleLen);

      // Passing true as last parameter causes arrays_equals to expand the second array (needle)
      // as the comparison is done.
      __C2 arrays_equals(false, haystackStart, firstNeedleCompare, compLen, retval, rScratch,
                         XMM_TMP3, XMM_TMP4, false /* char */, knoreg, true /* expand_ary2 */);
      __ testl(retval, retval);
      __ jne_b(L_found);

    // If more potential matches, continue at inner loop, otherwise go get another vector
      CLEAR_BIT(mask);
      __ jne(L_innerLoop);
      __ jmp(L_loopTop);

      // Found exact match.  Compute offset from beginning of haystack
      __ bind(L_found);
      __ subq(hsPtrRet, haystack);
      __ addq(hsPtrRet, index);
      __ movq(r11, hsPtrRet);
      __ jmp(L_returnR11);

#undef retval
#undef firstNeedleCompare

      __ bind(L_compareFull);

      // rScratch has n - k.  Compare entire string word-by-word
      // Index returned in r11
      __ xorq(r11, r11);
      __ movq(nMinusK, rScratch);
      __ jmpb(doCompare);

      __ bind(topLoop);
      __ addq(r11, 2);
      __ cmpq(r11, nMinusK);
      __ jg(L_returnError);

      __ bind(doCompare);
      __ leaq(r9, Address(haystack, r11));
      __ leaq(r8, Address(needle, 0));
      __ movq(r13, needleLen);

      __C2 arrays_equals(false, r9, r8, r13, rax, rdx, XMM_TMP3, XMM_TMP4, false /* char */, knoreg,
                         true /* expand_ary2 */);
      __ testq(rax, rax);
      __ jz(topLoop);

      // Match found
      __ jmp(L_returnR11);
    }
  }

  return;
}

// Helper for broadcasting needle elements to ymm registers for compares
// Expands into XMM_BYTE_0 and XMM_BYTE_K
//
// For UTF-16 encoded needles, broadcast a word at the proper offset to the ymm
// register (case UU)
// For the UTF-16 encoded haystack with Latin1 encoded needle (case UL) we have
// to read into a temp register to zero-extend the single byte needle value, then
// broadcast words to the ymm register.
//
// Parameters:
// needle - the address of the first byte of the needle
// needle_len - length of needle if !sizeKnown
// rTmp - temp register (for UL only)
// ae - the argument encodings
// _masm - Current MacroAssembler instance pointer
//
// Modifies XMM_BYTE_0 and XMM_BYTE_K
static void broadcast_first_and_last_needle(Register needle, Register needle_len, Register rTmp,
                                            StrIntrinsicNode::ArgEncoding ae,
                                            MacroAssembler *_masm) {
  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = (isUU || isUL);
  Label L_short;

  // Always need needle broadcast to ymm registers
  // Broadcast the beginning of needle into a vector register.
  if (isUU) {
    __ vpbroadcastw(XMM_BYTE_0, Address(needle, 0), Assembler::AVX_256bit);
  } else if (isUL) {

    __ movzbl(rTmp, Address(needle));
    __ movdl(XMM_BYTE_0, rTmp);
    // 1st byte of needle in words
    __ vpbroadcastw(XMM_BYTE_0, XMM_BYTE_0, Assembler::AVX_256bit);
  } else {
    __ vpbroadcastb(XMM_BYTE_0, Address(needle, 0), Assembler::AVX_256bit);
  }

  // Broadcast the end of needle into a vector register.
  // For a single-element needle this is redundant but does no harm and
  // reduces code size as opposed to broadcasting only if used.
  if (isUU) {
    __ vpbroadcastw(XMM_BYTE_K, Address(needle, needle_len, Address::times_1, -2),
                    Assembler::AVX_256bit);
  } else if (isUL) {
    __ movzbl(rTmp, Address(needle, needle_len, Address::times_1, -1));
    __ movdl(XMM_BYTE_K, rTmp);
    // last byte of needle in words
    __ vpbroadcastw(XMM_BYTE_K, XMM_BYTE_K, Assembler::AVX_256bit);
  } else {
    __ vpbroadcastb(XMM_BYTE_K, Address(needle, needle_len, Address::times_1, -1),
                    Assembler::AVX_256bit);
  }

  __ bind(L_short);
}

// Helper for broadcasting the 2nd needle element to XMM_BYTE_1
//
// For UTF-16 encoded needles, broadcast a word at the proper offset to the ymm
// register (case UU)
// For the UTF-16 encoded haystack with Latin1 encoded needle (case UL) we have
// to read into a temp register to zero-extend the single byte needle value, then
// broadcast words to the ymm register.
//
// Parameters:
// sizeKnown - True if needle size known at compile time
// size - the size of the needle.  Pass 0 if unknown at compile time
// needle - the address of the first byte of the needle
// needleLen - length of needle if !sizeKnown
// rTmp - temp register (for UL only)
// ae - Argument encoding
// _masm - Current MacroAssembler instance pointer
//
// Modifies XMM_BYTE_1
static void broadcast_additional_needles(bool sizeKnown, int size, Register needle,
                                         Register needleLen, Register rTmp,
                                         StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {
  Label L_done;

  assert_different_registers(needle, needleLen, rTmp);

  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = (isUU || isUL);

  size = sizeKnown ? size : NUMBER_OF_CASES + 1;

  // Need code to determine whether it's valid to use second byte of
  // needle if the size isn't known at compile-time
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
// eq_mask - The bit mask returned that holds the result of the comparison
// rxTmp1 - a temporary xmm register
// rxTmp2 - a temporary xmm register
// rxTmp3 - a temporary xmm register
// ae - Argument encoding
// _masm - Current MacroAssembler instance pointer
//
// (n - k) will always be >= 32 on entry
static void compare_big_haystack_to_needle(bool sizeKnown, int size, Label &noMatch,
                                           Register haystack, Register needleLen, Register eq_mask,
                                           XMMRegister rxTmp1, XMMRegister rxTmp2,
                                           XMMRegister rxTmp3, StrIntrinsicNode::ArgEncoding ae,
                                           MacroAssembler *_masm) {

  assert_different_registers(eq_mask, haystack, needleLen, nMinusK);

  const XMMRegister result = rxTmp1;
  const XMMRegister cmp_0 = rxTmp2;
  const XMMRegister cmp_k = rxTmp3;

  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = (isUU || isUL);

  int sizeIncr = isU ? 2 : 1;

  Label L_OKtoCompareFull, L_done, L_specialCase_gt2;

  assert(!sizeKnown || (sizeKnown && ((size > 0) && (size <= NUMBER_OF_CASES))), "Incorrect size given");

  // Address of the kth byte of the needle within the haystack
  Address kThByte = sizeKnown ? Address(haystack, size - sizeIncr)
                              : Address(haystack, needleLen,
                                        isUL ? Address::times_2 : Address::times_1, -(sizeIncr));
  size = sizeKnown ? size : NUMBER_OF_CASES + 1;

  // Compare first byte of needle to haystack
     vpcmpeq(cmp_0, XMM_BYTE_0, Address(haystack, 0), Assembler::AVX_256bit, ae, _masm);

  __ vpmovmskb(eq_mask, cmp_0, Assembler::AVX_256bit);

  // If the needle is a single element (at compile time) no need to compare more
  if (size != sizeIncr) {
    // Compare last byte of needle to haystack at proper position
    vpcmpeq(cmp_k, XMM_BYTE_K, kThByte, Assembler::AVX_256bit, ae, _masm);

    __ vpand(result, cmp_k, cmp_0, Assembler::AVX_256bit);

    if (size > sizeIncr * 2) {
      vpcmpeq(cmp_k, XMM_BYTE_1, Address(haystack, 1 * sizeIncr), Assembler::AVX_256bit, ae, _masm);
      __ vpand(result, cmp_k, result, Assembler::AVX_256bit);
    }

    __ vpmovmskb(eq_mask, result, Assembler::AVX_256bit);
  }

  __ bind(L_done);
  __ testl(eq_mask, eq_mask);
  __ je(noMatch);
  // At this point, we have at least one "match" where first and last bytes
  // of the needle are found the correct distance apart.
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
// eq_mask - The bit mask returned that holds the result of the comparison
// needleLen - Length of the needle in bytes.  Only used if isUL true
// rTmp - temporary register
// rxTmp1 - temporary xmm register
// rxTmp2 - temporary xmm register
// ae - Argument encoding
// _masm - Current MacroAssembler instance pointer
//
// No need to worry about reading past end of haystack since haystack
// has been copied to the stack
//
// If !sizeKnown, needle is at least 11 bytes long
static void compare_haystack_to_needle(bool sizeKnown, int size, Label &noMatch, Register haystack,
                                       Register eq_mask, Register needleLen, Register rTmp,
                                       XMMRegister rxTmp1, XMMRegister rxTmp2,
                                       StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {

  assert_different_registers(eq_mask, haystack, needleLen, rTmp, nMinusK);

  // NOTE: cmp_0 and result are the same register
  const XMMRegister cmp_0 = rxTmp1;
  const XMMRegister result = rxTmp1;
  const XMMRegister cmp_k = rxTmp2;

  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16

  int sizeIncr = isU ? 2 : 1;

  assert((!sizeKnown) || (((size > 0) && (size <= NUMBER_OF_CASES))), "Incorrect size given");

  // Address of the kth byte of the needle within the haystack
  Address kThByte = sizeKnown ? Address(haystack, size - sizeIncr)
                              : Address(haystack, needleLen, Address::times_1, -(sizeIncr));
  size = sizeKnown ? size : NUMBER_OF_CASES + 1;

  // Creates a mask of (n - k + 1) ones.  This prevents
  // recognizing any false-positives past the end of
  // the valid haystack.
  __ movq(rTmp, -1);
  __ movq(eq_mask, nMinusK);
  __ addq(eq_mask, 1);
  __ bzhiq(rTmp, rTmp, eq_mask);

  // Compare first byte of needle to haystack
     vpcmpeq(cmp_0, XMM_BYTE_0, Address(haystack, 0), Assembler::AVX_256bit, ae, _masm);
  if (size != sizeIncr) {
    // Compare last byte of needle to haystack at proper position
    vpcmpeq(cmp_k, XMM_BYTE_K, kThByte, Assembler::AVX_256bit, ae, _masm);

    __ vpand(result, cmp_k, cmp_0, Assembler::AVX_256bit);

    if (size > (sizeIncr * 2)) {
      vpcmpeq(cmp_k, XMM_BYTE_1, Address(haystack, 1 * sizeIncr), Assembler::AVX_256bit, ae, _masm);
      __ vpand(result, cmp_k, result, Assembler::AVX_256bit);
    }
  }

  __ vpmovmskb(eq_mask, result, Assembler::AVX_256bit);
  __ andl(eq_mask, rTmp);

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
// needle - Address of the needle
// haystack - Address of the haystack
// hsLength - The length of the haystack
// rTmp1 - Temporary
// rTmp2 - Temporary
// rTmp3 - Temporary
// rTmp4 - Temporary
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
//  rbp: junk
//  XMM_BYTE_0 - first element of needle broadcast
//  XMM_BYTE_K - last element of needle broadcast

static void big_case_loop_helper(bool sizeKnown, int size, Label &noMatch, Label &loop_top,
                                 Register eq_mask, Register hsPtrRet, Register needleLen,
                                 Register needle, Register haystack, Register hsLength,
                                 Register rTmp1, Register rTmp2, Register rTmp3, Register rTmp4,
                                 StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {
  Label L_midLoop, L_greaterThan32, L_out;

  assert_different_registers(eq_mask, hsPtrRet, needleLen, rdi, r15, rdx, rsi, rbx, r14, nMinusK);

  const Register last = rTmp1;
  const Register temp1 = rTmp2;
  const Register temp2 = rTmp3;
  const Register temp3 = rTmp4;

  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16

  // Assume failure
  __ movq(r11, -1);

  broadcast_additional_needles(sizeKnown, size, needle, needleLen, temp1, ae, _masm);

  __ cmpq(nMinusK, 31);
  __ jae_b(L_greaterThan32);

  // Here the needle is too long, so we can't do a 32-byte read to compare the last element.
  //
  // Instead we match the first two characters, read from the end of the haystack
  // back 32 characters, shift the result, compare and check that way.
  //
  // Set last to hsPtrRet so the next attempt at loop iteration ends the compare.
  __ movq(last, haystack);
  __ movq(hsPtrRet, haystack);

  // Compare first element of needle to haystack
  vpcmpeq(XMM_TMP3, XMM_BYTE_0, Address(haystack, 0), Assembler::AVX_256bit, ae, _masm);

  __ vpmovmskb(eq_mask, XMM_TMP3, Assembler::AVX_256bit);

  if (!sizeKnown || (sizeKnown && (size > (isU ? 4 : 2)))) {
    // Compare second element of needle to haystack and mask result
    vpcmpeq(XMM_TMP3, XMM_BYTE_1, Address(haystack, isU ? 2 : 1), Assembler::AVX_256bit, ae, _masm);

    __ vpmovmskb(temp1, XMM_TMP3, Assembler::AVX_256bit);
    __ andq(eq_mask, temp1);
  }

  // Compare last element of needle to haystack, shift and mask result
  vpcmpeq(XMM_TMP3, XMM_BYTE_K, Address(haystack, hsLength, Address::times_1, -32),
          Assembler::AVX_256bit, ae, _masm);

  __ vpmovmskb(temp1, XMM_TMP3, Assembler::AVX_256bit);

  // Compute the proper shift value.  If we let k be the needle length and n be the haystack
  // length, we should be comparing to haystack[k - 1] through haystack[k - 1 + 31].  Since
  // (n - k) < 32, (k - 1 + 31) would be past the end of the haystack.  So the shift value
  // is computed as (k + 31 - n).
  //
  // Clarification:  The BYTE_K compare above compares haystack[(n-32):(n-1)].  We need to
  // compare haystack[(k-1):(k-1+31)].  Subtracting either index gives shift value of
  // (k + 31 - n):  x = (k-1+31)-(n-1) = k-1+31-n+1 = k+31-n.
  if (sizeKnown) {
    __ movl(temp2, 31 + size);
  } else {
    __ movl(temp2, 31);
    __ addl(temp2, needleLen);
  }
  __ subl(temp2, hsLength);
  __ shrxl(temp1, temp1, temp2);
  __ andl(eq_mask, temp1);

  __ testl(eq_mask, eq_mask);
  __ je(noMatch);

  __ jmp(L_out);

  __ bind(L_greaterThan32);

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

  __ align(OptoLoopAlignment);
  __ bind(loop_top);
  // An equal comparison indicates completion with no match
  __ cmpq(hsPtrRet, last);
  __ je(noMatch);
  __ addq(hsPtrRet, 32);

  // If next compare will go beyond end of haystack adjust start of read
  // back to last valid read position
  __ cmpq(hsPtrRet, last);
  __ jbe_b(L_midLoop);
  __ movq(hsPtrRet, last);

  __ bind(L_midLoop);

  // compare_big_haystack_to_needle will jump to loop_top until a match has been
  // found
  compare_big_haystack_to_needle(sizeKnown, size, loop_top, hsPtrRet, needleLen, eq_mask, XMM_TMP1,
                                 XMM_TMP2, XMM_TMP3, ae, _masm);

  // At this point, we have at least one "match" where first and last bytes
  // of the needle are found the correct distance apart.
  //
  // NOTE: haystack (rbx) should be preserved; hsPtrRet(rcx) is expected to
  //    point to the haystack such that hsPtrRet[tzcntl(eq_mask)] points to
  //    the matched string.

  __ bind(L_out);
}

////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
// Helper for comparing small needles to the haystack after a potential match found.
//
// Parameters:
// size - The size of the needle in bytes
// L_noMatch - Label to jump to if needle does not match haystack at this location
// L_matchFound - Label to jump to if needle matches haystack at this location
// needle - the address of the first byte of the needle
// needleVal - The bytes of the needle to compare
// haystack - The address of the first byte of the haystack
// mask - The comparison mask from comparing the first 2 and last elements of the needle
// foundIndex - The index within the haystack of the match
// tmp - A temporary register
// ae - the argument encodings
// _masm - Current MacroAssembler instance pointer
//
// Branches to either L_noMatch or L_matchFound depending on the result of the comparison
// foundIndex will contain the index within the haystack of the match for L_matchFound

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

  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16

  int bytesAlreadyCompared = 0;
  int bytesLeftToCompare = 0;
  int offsetOfFirstByteToCompare = 0;

  Label temp;

  // Getting her we already have the first two and last elements of the needle
  // comparing equal, so no need to compare them again
  bytesAlreadyCompared = isU ? 6 : 3;
  offsetOfFirstByteToCompare = isU ? 4 : 2;

  bytesLeftToCompare = size - bytesAlreadyCompared;
  assert(bytesLeftToCompare <= 7, "Too many bytes left to compare");

  // The needle is <= 3 elements long, so the ultimate result comes from the mask
  if (bytesLeftToCompare <= 0) {
    __ tzcntl(foundIndex, mask);
    __ jmp(L_matchFound);
    return;
  }

  // At this point, there is at least one byte of the needle that needs to be
  // compared to the haystack.

  // Pre-load the needle bytes to compare here
  switch (bytesLeftToCompare) {
  case 1:
  case 2:
    // Load for needle size of 4 and 5 bytes
    __ movl(needleVal, Address(needle, (offsetOfFirstByteToCompare - 2)));
    break;

  case 3:
  case 4:
    // Load for needle size of 6 and 7 bytes
    __ movl(needleVal, Address(needle, offsetOfFirstByteToCompare));
    break;

  case 5:
  case 6:
    // Load for needle size of 8 and 9 bytes
    __ movq(needleVal, Address(needle, (offsetOfFirstByteToCompare - 2)));
    break;

  case 7:
    // Load for needle size of 10 bytes
    __ movq(needleVal, Address(needle, offsetOfFirstByteToCompare));
    break;

  default:
    break;
  }

  __ align(OptoLoopAlignment);
  __ bind(L_loopTop);
  __ tzcntl(foundIndex, mask);  // Index of match within haystack

  switch (bytesLeftToCompare) {
  case 1:
  case 2:
    // Comparison for needle size of 4 and 5 bytes
    __ cmpl(Address(haystack, foundIndex, Address::times_1, offsetOfFirstByteToCompare - 2),
            needleVal);
    __ je(L_matchFound);
    break;

  case 3:
  case 4:
    // Comparison for needle size of 6 and 7 bytes
    __ cmpl(Address(haystack, foundIndex, Address::times_1, offsetOfFirstByteToCompare), needleVal);
    __ je(L_matchFound);
    break;

  case 5:
  case 6:
    // Comparison for needle size of 8 and 9 bytes
    __ cmpq(Address(haystack, foundIndex, Address::times_1, offsetOfFirstByteToCompare - 2),
            needleVal);
    __ je(L_matchFound);
    break;

  case 7:
    // Comparison for needle size of 10 bytes
    __ cmpq(Address(haystack, foundIndex, Address::times_1, offsetOfFirstByteToCompare), needleVal);
    __ je(L_matchFound);
    break;

  default:
    break;
  }

  CLEAR_BIT(mask);  // Loop as long as there are other bits set
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
// ae - Argument encoding
// haystack - The address of the haystack
// haystack_len - the length of the haystack in elements
// needle - The address of the needle
// needle_len - the length of the needle in elements
// XMM0 - Temporary xmm register
// XMM1 - Temporary xmm register
// mask - Used to hold comparison mask
// tmp - Temporary register
// _masm - Current MacroAssembler instance pointer
static void highly_optimized_short_cases(StrIntrinsicNode::ArgEncoding ae, Register haystack,
                                         Register haystack_len, Register needle,
                                         Register needle_len, XMMRegister XMM0, XMMRegister XMM1,
                                         Register mask, Register tmp, MacroAssembler *_masm) {
  // Highly optimized special-cases
  Label L_noMatch, L_foundall, L_out;

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
    __ subptr(tmp, haystack_len);

    if (isU) {
      // For UTF-16, lengths are half
      __ subptr(tmp, haystack_len);
    }
    // Point the haystack to the stack
    __ leaq(haystack, Address(rsp, tmp, Address::times_1));
  }

  // Creates a mask of (n - k + 1) ones.  This prevents recognizing any false-positives
  // past the end of the valid haystack.
  __ movq(mask, -1);
  __ subq(haystack_len, needle_len);
  __ incrementq(haystack_len);
  if (isU) {
    __ shlq(haystack_len, 1);
  }
  __ bzhiq(mask, mask, haystack_len);

  // Loop for each needle size from 1 to 6 bytes long.  For UU, only 3 elements.
  for (int size = 1; size <= (isUU ? 3 : 6); size++) {
    // Broadcast next needle byte into ymm register
    int needle_position = isUU ? (size - 1) * 2 : size - 1;
    int haystack_position = isU ? (size - 1) * 2 : size - 1;
    if (isUU) {
      __ vpbroadcastw(XMM0, Address(needle, needle_position), Assembler::AVX_256bit);
    } else if (isUL) {
      // Expand needle
      __ movzbl(tmp, Address(needle, needle_position));
      __ movdl(XMM0, tmp);
      // Byte of needle to words
      __ vpbroadcastw(XMM0, XMM0, Assembler::AVX_256bit);
    } else {
      __ vpbroadcastb(XMM0, Address(needle, needle_position), Assembler::AVX_256bit);
    }

    // Compare next byte.  Keep the comparison mask in mask, which will
    // accumulate
    vpcmpeq(XMM1, XMM0, Address(haystack, haystack_position), Assembler::AVX_256bit, ae, _masm);
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
//
// ae - Argument encoding
// L_error - Label to branch to if no match found
// L_checkRange - label to jump to when match found.  Checks validity of returned index
// L_fixup - Jump to here for big cases.  Return value is pointer to matching haystack byte
// *big_jump_table - Address of pointer to the first element of big jump table
// *small_jump_table - Address of pointer to the first element of small jump table
// _masm - Current MacroAssembler instance pointer

static void setup_jump_tables(StrIntrinsicNode::ArgEncoding ae, Label &L_error, Label &L_checkRange,
                              Label &L_fixup, address *big_jump_table, address *small_jump_table,
                              MacroAssembler *_masm) {
  bool isUL = (ae == StrIntrinsicNode::UL);
  bool isUU = (ae == StrIntrinsicNode::UU);
  bool isU = isUL || isUU;  // At least one is UTF-16
  const XMMRegister byte_1 = XMM_BYTE_1;

  address big_hs_jmp_table[NUMBER_OF_CASES];    // Jump table for large haystacks
  address small_hs_jmp_table[NUMBER_OF_CASES];  // Jump table for small haystacks
  int jmp_ndx = 0;

  ////////////////////////////////////////////////
  //  On entry to each case, the register state is:
  //
  //  rax = unused
  //  rbx = &haystack
  //  rcx = haystack length
  //  rdx = &needle
  //  rsi = haystack length
  //  rdi = &haystack
  //  rbp = unused
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

  {
    ////////////////////////////////////////////////////////////////////////////////////////
    //
    // Small haystack (<=32 bytes) switch
    //
    // Handle cases that were not handled in highly_optimized_short_cases, which will be
    // haystack size <= 32 bytes with 6 < needle size < NUMBER_OF_CASES bytes.

    ////////////////////////////////////////////////
    //  The haystack is <= 32 bytes
    //
    // If a match is not found, branch to L_error (which will always
    // return -1).
    //
    // If a match is found, jump to L_checkRange, which ensures the
    // matched needle is not past the end of the haystack.
    //
    // The index where a match is found is returned in set_bit (r11).

    const Register haystack = rbx;
    const Register needle = r14;
    const Register needle_val = r8;
    const Register set_bit = r11;
    const Register eq_mask = rsi;
    const Register rTmp = rax;

    for (int i = 6; i < NUMBER_OF_CASES; i++) {
      small_hs_jmp_table[i] = __ pc();
      if (isU && ((i + 1) & 1)) {
        continue;
      } else {
        broadcast_additional_needles(true, i + 1, needle, noreg, rTmp, ae, _masm);

        compare_haystack_to_needle(true, i + 1, L_error, haystack, eq_mask, noreg, rTmp, XMM_TMP1,
                                   XMM_TMP2, ae, _masm);

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
    //  The haystack is > 32 bytes
    //
    // The value returned on a match is in hs_ptr (rcx) which is the address
    // of the first matching byte within the haystack.  The L_fixup label
    // takes hs_ptr (rcx), haystack (rbx), and set_bit (r8) to compute the
    // index as: hs_ptr - haystack + r8.  hs_ptr - haystack is the offset
    // within the haystack of the 32-byte chunk wherein a match was found,
    // and set_bit is the index within that 32-byte chunk of the matching string.

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

        big_case_loop_helper(true, i + 1, L_error, L_loopTop, eq_mask, hs_ptr, needle_len,
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
