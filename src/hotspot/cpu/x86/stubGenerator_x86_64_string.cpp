/*
 * Copyright (c) 2023, Intel Corporation. All rights reserved.
 * Intel Math Library (LIBM) Source Code
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
#include "precompiled.hpp"
#include "stubGenerator_x86_64.hpp"

/******************************************************************************/
//                     String handling intrinsics
//                     --------------------------
//
// Currently implementing strchr and strstr.  Used for IndexOf operations.
//
/******************************************************************************/

#define __ _masm->

void StubGenerator::loop_helper(int size, Label& bailout, Label& loop_top) {
  Label temp;

  __ movq(r13, -1);
  __ testq(r15, r15);
  __ jle(bailout);
  __ vpbroadcastb(xmm0, Address(r10, 0), Assembler::AVX_256bit);
  __ vpbroadcastb(xmm1, Address(r10, size - 1), Assembler::AVX_256bit);
  __ leaq(rax, Address(r11, r15, Address::times_1));
  __ leal(rcx, Address(r15, 33 - size));
  __ andl(rcx, 0x1f);
  __ cmpl(r15, 0x21);
  __ movl(r15, 0x20);
  __ cmovl(Assembler::aboveEqual, r15, rcx);
  __ movq(rcx, r11);
  __ jmpb(temp);
  __ bind(loop_top);
  __ addq(rcx, r15);
  __ movl(r15, 0x20);
  __ cmpq(rcx, rax);
  __ jae(bailout);
  __ bind(temp);
  __ vpcmpeqb(xmm2, xmm0, Address(rcx, 0), Assembler::AVX_256bit);
  __ vpcmpeqb(xmm3, xmm1, Address(rcx, size - 1), Assembler::AVX_256bit);
  __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
  __ vpmovmskb(rdx, xmm2, Assembler::AVX_256bit);
  __ testl(rdx, rdx);
  __ je_b(loop_top);
}

address StubGenerator::generate_string_indexof() {
  StubCodeMark mark(this, "StubRoutines", "stringIndexOf");
  address large_hs_jmp_table[32];
  address small_hs_jmp_table[32];
  int jmp_ndx = 0;
  __ align(CodeEntryAlignment);
  address start = __ pc();
  __ enter();  // required for proper stackwalking of RuntimeStub frame

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //                         AVX2 code
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  if (VM_Version::supports_avx2()) {  // AVX2 version
    Label memcmp_avx2;

    Label L_begin, L_0x406044, L_CASE_0, L_0x406019;
    Label L_trampoline, L_anysize, L_0x404912;
    Label L_exit, L_long_compare, L_top_loop_1, L_0x4049cc, L_error;
    Label L_small_string, L_0x405cee, L_0x405f5d, L_0x406008;
    Label L_0x405fff, L_final_check, L_mid_anysize_loop, L_top_anysize_loop;
    Label L_inner_anysize_loop, L_0x40602e, L_0x40607f, L_0x405018;
    Label L_0x40605e, L_0x406093, L_0x40559d, L_0x404933, L_byte_copy;
    Label L_set_s, L_small_string2, L_0x4060a3;

    address jump_table;
    address jump_table_1;

    __ jmp(L_begin);

    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    // switch statement for AVX2 instructions:
    //    r15, rsi <= n
    //    rdi, r11 <= s
    //    r10, rdx <= needle
    //    r12, rcx <= k
    //    rbx <= n - k
    //    rax <= k - 1

    large_hs_jmp_table[0] = __ pc();  // Case for needle size == 1
    __ vpbroadcastb(xmm0, Address(r10, 0), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm1, xmm0, Address(r11, 0), Assembler::AVX_256bit);
    __ vpmovmskb(rax, xmm1);
    __ testl(rax, rax);
    __ je(L_0x406044);
    __ tzcntl(r13, rax);
    __ jmp(L_exit);

    __ bind(L_CASE_0);  // Needle size == 0
    __ xorl(r15, r15);
    __ jmp(L_0x406019);

    // Small-ish string
    // On entry:
    //    r15, rsi <= n
    //    rax <= scratch
    //    rdi, r11 <= s
    //    r10, rdx <= needle
    //    r12, rcx <= k
    //    rbx <= n - k
    // if (n <= STACK_SZ) {
    //   // Copy haystack to stack and adjust parameters
    //   int i = n;
    //   char *hs = (char *) s;
    //   char *dst = tmp_string;
    //   int ndx = 0;
    __ bind(L_small_string);
    __ cmpq(r15, 0x20);
    __ ja(L_small_string2);
    __ leaq(r12, Address(rsp, 0x80));  // tmp_string

    // if (i <= 16) {
    //   hs = (char *) &s[i - 16];
    //   ndx = 16 - i;
    //   const __m128i *A = reinterpret_cast<const __m128i*>(hs);
    //   __m128i *B = reinterpret_cast<__m128i*>(dst);
    //   *B = *A;
    __ cmpl(r15, 0x10);
    __ ja(L_byte_copy);
    __ leaq(rax, Address(r15, -0x10));
    __ movdqu(xmm0, Address(r11, rax, Address::times_1, -0x10));
    __ movdqu(Address(r12, 0), xmm0);
    __ movl(rax, 0x10);
    __ subl(rax, r15);  // 16 - i

    __ bind(L_set_s);
    // s = &tmp_string[ndx];
    __ leaq(rdi, Address(r12, rax, Address::times_1));
    __ movq(r12, rcx);
    __ jmp(L_0x404933);

    __ bind(L_byte_copy);
    {
      Label L_8, L_4, L_2, L_1, L_restore;
      __ cmpl(r15, 0x10);
      __ jb_b(L_8);
      __ movdqu(xmm0, Address(r11, 0));
      __ movdqu(Address(r12, 0), xmm0);
      __ subl(r15, 0x10);
      __ addptr(r11, 0x10);
      __ addptr(r12, 0x10);

      __ bind(L_8);
      __ cmpl(r15, 0x8);
      __ jb_b(L_4);
      __ movq(rax, Address(r11, 0));
      __ movq(Address(r12, 0), rax);
      __ subl(r15, 0x8);
      __ addptr(r11, 0x8);
      __ addptr(r12, 0x8);

      __ bind(L_4);
      __ cmpl(r15, 0x4);
      __ jb_b(L_2);
      __ movl(rax, Address(r11, 0));
      __ movl(Address(r12, 0), rax);
      __ subl(r15, 0x4);
      __ addptr(r11, 0x4);
      __ addptr(r12, 0x4);

      __ bind(L_2);
      __ cmpl(r15, 0x2);
      __ jb_b(L_1);
      __ movzwl(rax, Address(r11, 0));
      __ movw(Address(r12, 0), rax);
      __ subl(r15, 0x2);
      __ addptr(r11, 0x2);
      __ addptr(r12, 0x2);

      __ bind(L_1);
      __ cmpl(r15, 0x1);
      __ jb_b(L_restore);
      __ movzbl(rax, Address(r11, 0));
      __ movb(Address(r12, 0), rax);

      __ bind(L_restore);
      __ xorq(rax, rax);
      __ movq(r15, rsi);
      __ movq(r11, rdi);
      __ jmp(L_set_s);
    }

    //    Move through the small-ish string char by char
    // for (size_t i = 0; i < n - k + 1; i++) {
    //   if (s[i] == needle[0] && s[i + k - 1] == needle[k - 1]) {
    //     switch (k) {
    __ bind(L_small_string2);
    __ incrementq(r15);
    __ subq(r15, r12);
    __ je(L_error);
    __ movzbl(rbp, Address(r10, 0));
    __ leaq(rcx, Address(r10, 0x1));
    __ leaq(rdx, Address(r12, -0x2));
    __ cmpq(r15, 0x2);
    __ movl(r13, 1);
    __ cmovq(Assembler::aboveEqual, r13, r15);
    __ leaq(rbx, Address(r11, 0x1d));
    __ leaq(r14, Address(r12, r11, Address::times_1));
    __ decrementq(r14);
    __ incrementq(r11);
    __ xorl(r15, r15);
    __ jmpb(L_0x4049cc);

    //  CASE 8 CASE 9:
    small_hs_jmp_table[8] = __ pc();
    small_hs_jmp_table[9] = __ pc();
    __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpq(rax, Address(rcx, 0));
    __ je(L_0x406019);
    __ align(8);

    __ bind(L_top_loop_1);
    __ incrementq(r15);
    __ cmpq(r13, r15);
    __ je(L_error);
    __ bind(L_0x4049cc);
    __ cmpb(Address(rbx, r15, Address::times_1, -0x1d), rbp);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r14, r15, Address::times_1));
    __ cmpb(rax, Address(r10, r12, Address::times_1, -0x1));
    __ jne(L_top_loop_1);

    __ leaq(rax, Address(r12, -0x1));
    __ cmpq(rax, 0x1e);
    __ ja_b(L_long_compare);
    __ jmp(L_trampoline);  // Jump to the correct case for small haystacks

    //  CASE 4: CASE 5:
    small_hs_jmp_table[4] = __ pc();
    small_hs_jmp_table[5] = __ pc();
    __ movl(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpl(rax, Address(rcx, 0));
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  Needle size >= 32 - use memcmp
    __ bind(L_long_compare);
    __ leaq(rdi, Address(r11, r15, Address::times_1));
    __ movq(Address(rsp, 0x10), rcx);
    __ movq(rsi, Address(rsp, 0x10));
    __ movq(Address(rsp, 0x8), rdx);
    __ movq(rdx, Address(rsp, 0x8));
    __ movq(Address(rsp, 0x18), r11);
    __ movq(Address(rsp, 0x30), r10);
    __ call(memcmp_avx2, relocInfo::none);
    __ movq(rdx, Address(rsp, 0x8));
    __ movq(rcx, Address(rsp, 0x10));
    __ movq(r10, Address(rsp, 0x30));
    __ movq(r11, Address(rsp, 0x18));
    __ testl(rax, rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 2:
    small_hs_jmp_table[2] = __ pc();
    __ movzbl(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpb(rax, Address(rcx, 0));
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 3:
    small_hs_jmp_table[3] = __ pc();
    __ movzwl(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpw(Address(rcx, 0), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 6:
    small_hs_jmp_table[6] = __ pc();
    __ movl(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpl(rax, Address(rcx, 0));
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(rbx, r15, Address::times_1, -0x18));
    __ cmpb(rax, Address(r10, 0x5));
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 7:
    small_hs_jmp_table[7] = __ pc();
    __ movl(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpl(rax, Address(rcx, 0));
    __ jne(L_top_loop_1);
    __ movzwl(rax, Address(rbx, r15, Address::times_1, -0x18));
    __ cmpw(Address(r10, 0x5), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 10:
    small_hs_jmp_table[10] = __ pc();
    __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpq(rax, Address(r10, 0x1));
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r10, 0x9));
    __ cmpb(Address(rbx, r15, Address::times_1, -0x14), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 11:
    small_hs_jmp_table[11] = __ pc();
    __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpq(rax, Address(r10, 0x1));
    __ jne(L_top_loop_1);
    __ movzwl(rax, Address(r10, 0x9));
    __ cmpw(Address(rbx, r15, Address::times_1, -0x14), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 12:
    small_hs_jmp_table[12] = __ pc();
    __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpq(rax, Address(rcx, 0));
    __ jne(L_top_loop_1);
    __ movzwl(rax, Address(rbx, r15, Address::times_1, -0x14));
    __ cmpw(Address(r10, 0x9), rax);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(rbx, r15, Address::times_1, -0x12));
    __ cmpb(rax, Address(r10, 0xb));
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 13:
    small_hs_jmp_table[13] = __ pc();
    __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpq(rax, Address(r10, 0x1));
    __ jne(L_top_loop_1);
    __ movl(rax, Address(r10, 0x9));
    __ cmpl(Address(rbx, r15, Address::times_1, -0x14), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 14:
    small_hs_jmp_table[14] = __ pc();
    __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpq(rax, Address(r10, 0x1));
    __ jne(L_top_loop_1);
    __ movl(rax, Address(r10, 0x9));
    __ cmpl(Address(rbx, r15, Address::times_1, -0x14), rax);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r10, 0xd));
    __ cmpb(Address(rbx, r15, Address::times_1, -0x10), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 15:
    small_hs_jmp_table[15] = __ pc();
    __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpq(rax, Address(r10, 0x1));
    __ jne(L_top_loop_1);
    __ movl(rax, Address(r10, 0x9));
    __ cmpl(Address(rbx, r15, Address::times_1, -0x14), rax);
    __ jne(L_top_loop_1);
    __ movzwl(rax, Address(r10, 0xd));
    __ cmpw(Address(rbx, r15, Address::times_1, -0x10), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 16:
    small_hs_jmp_table[16] = __ pc();
    __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpq(rax, Address(r10, 0x1));
    __ jne(L_top_loop_1);
    __ movl(rax, Address(r10, 0x9));
    __ cmpl(Address(rbx, r15, Address::times_1, -0x14), rax);
    __ jne(L_top_loop_1);
    __ movzwl(rax, Address(r10, 0xd));
    __ cmpw(Address(rbx, r15, Address::times_1, -0x10), rax);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r10, 0xf));
    __ cmpb(Address(rbx, r15, Address::times_1, -0xe), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 17:
    small_hs_jmp_table[17] = __ pc();
    __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
    __ cmpq(rax, Address(r10, 0x1));
    __ jne(L_top_loop_1);
    __ movq(rax, Address(r10, 0x9));
    __ cmpq(Address(rbx, r15, Address::times_1, -0x14), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 18:
    small_hs_jmp_table[18] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r10, 0x11));
    __ cmpb(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 19:
    small_hs_jmp_table[19] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movzwl(rax, Address(r10, 0x11));
    __ cmpw(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 20:
    small_hs_jmp_table[20] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movzwl(rax, Address(r10, 0x11));
    __ cmpw(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r10, 0x13));
    __ cmpb(Address(rbx, r15, Address::times_1, -0xa), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 21:
    small_hs_jmp_table[21] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movl(rax, Address(r10, 0x11));
    __ cmpl(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 22:
    small_hs_jmp_table[22] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movl(rax, Address(r10, 0x11));
    __ cmpl(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r10, 0x15));
    __ cmpb(Address(rbx, r15, Address::times_1, -0x8), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 23:
    small_hs_jmp_table[23] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movl(rax, Address(r10, 0x11));
    __ cmpl(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ movw(rax, Address(r10, 0x15));
    __ cmpw(Address(rbx, r15, Address::times_1, -0x8), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 24:
    small_hs_jmp_table[24] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movl(rax, Address(r10, 0x11));
    __ cmpl(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ movw(rax, Address(r10, 0x15));
    __ cmpw(Address(rbx, r15, Address::times_1, -0x8), rax);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r10, 0x17));
    __ cmpb(Address(rbx, r15, Address::times_1, -0x6), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 25:
    small_hs_jmp_table[25] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movq(rax, Address(r10, 0x11));
    __ cmpq(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 26:
    small_hs_jmp_table[26] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movq(rax, Address(r10, 0x11));
    __ cmpq(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r10, 0x19));
    __ cmpb(Address(rbx, r15, Address::times_1, -0x4), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 27:
    small_hs_jmp_table[27] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movq(rax, Address(r10, 0x11));
    __ cmpq(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ movzwl(rax, Address(r10, 0x19));
    __ cmpw(Address(rbx, r15, Address::times_1, -0x4), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 28:
    small_hs_jmp_table[28] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movq(rax, Address(r10, 0x11));
    __ cmpq(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ movzwl(rax, Address(r10, 0x19));
    __ cmpw(Address(rbx, r15, Address::times_1, -0x4), rax);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r10, 0x1b));
    __ cmpb(Address(rbx, r15, Address::times_1, -0x2), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 29:
    small_hs_jmp_table[29] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movq(rax, Address(r10, 0x11));
    __ cmpq(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ movl(rax, Address(r10, 0x19));
    __ cmpl(Address(rbx, r15, Address::times_1, -0x4), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    //  CASE 30:
    small_hs_jmp_table[30] = __ pc();
    __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
    __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
    __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
    __ jne(L_top_loop_1);
    __ movq(rax, Address(r10, 0x11));
    __ cmpq(Address(rbx, r15, Address::times_1, -0xc), rax);
    __ jne(L_top_loop_1);
    __ movl(rax, Address(r10, 0x19));
    __ cmpl(Address(rbx, r15, Address::times_1, -0x4), rax);
    __ jne(L_top_loop_1);
    __ movzbl(rax, Address(r10, 0x1d));
    __ cmpb(Address(rbx, r15, Address::times_1), rax);
    __ jne(L_top_loop_1);
    __ jmp(L_0x406019);

    /////////////////////////////////////////////////////////////////////////////
    // anysize - needle >= 32 and haystack > 32
    __ bind(L_anysize);
    __ movq(r13, -1);
    __ testq(r15, r15);
    __ jle(L_exit);
    __ movq(Address(rsp, 0x20), rbx);
    __ leaq(rax, Address(r11, r15, Address::times_1));
    __ movq(Address(rsp, 0x28), rax);
    __ vpbroadcastb(xmm0, Address(r10, 0), Assembler::AVX_256bit);
    __ vmovdqu(Address(rsp, 0x30), xmm0);
    __ vpbroadcastb(xmm0, Address(r12, r10, Address::times_1, -0x1),
                    Assembler::AVX_256bit);
    __ vmovdqu(Address(rsp, 0x50), xmm0);
    __ subl(r15, r12);
    __ incrementl(r15);
    __ andl(r15, 0x1f);
    __ incrementq(r10);
    __ leaq(rax, Address(r12, -0x2));
    __ movq(Address(rsp, 0x10), rax);
    __ movq(Address(rsp, 0x18), r11);
    __ jmpb(L_mid_anysize_loop);

    __ bind(L_top_anysize_loop);
    __ movq(r11, Address(rsp, 0x8));
    __ addq(r11, r15);
    __ movl(r15, 0x20);
    __ cmpq(r11, Address(rsp, 0x28));
    __ jae(L_0x4060a3);

    __ bind(L_mid_anysize_loop);
    __ vmovdqu(xmm0, Address(rsp, 0x30));
    __ vpcmpeqb(xmm0, xmm0, Address(r11, 0), Assembler::AVX_256bit);
    __ vmovdqu(xmm1, Address(rsp, 0x50));
    __ movq(Address(rsp, 0x8), r11);
    __ vpcmpeqb(xmm1, xmm1, Address(r11, r12, Address::times_1, -0x1),
                Assembler::AVX_256bit);
    __ vpand(xmm0, xmm1, xmm0, Assembler::AVX_256bit);
    __ vpmovmskb(rbx, xmm0);
    __ testl(rbx, rbx);
    __ je(L_top_anysize_loop);
    __ movq(rax, Address(rsp, 0x8));
    __ leaq(r14, Address(rax, 1));

    __ bind(L_inner_anysize_loop);
    __ tzcntl(rbp, rbx);
    __ leaq(rdi, Address(r14, rbp, Address::times_1));
    __ movq(r13, r10);
    __ movq(rsi, r10);
    __ movq(rdx, Address(rsp, 0x10));
    __ vzeroupper();
    __ call(memcmp_avx2, relocInfo::none);
    __ testl(rax, rax);
    __ je(L_0x40602e);
    __ blsrl(rbx, rbx);
    __ movq(r10, r13);
    __ jne_b(L_inner_anysize_loop);
    __ jmpb(L_top_anysize_loop);

    //    case 2:   // case for needle size == 2
    large_hs_jmp_table[1] = __ pc();
    __ movq(r13, -1);
    __ testq(r15, r15);
    __ jle(L_exit);
    __ vpbroadcastb(xmm0, Address(r10, 0), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r10, 0x1), Assembler::AVX_256bit);
    __ leaq(rcx, Address(r11, r15, Address::times_1));
    __ decl(r15);
    __ andl(r15, 0x1f);
    __ cmpl(r15, 0x21);
    __ movl(rdx, 0x20);
    __ cmovl(Assembler::aboveEqual, rdx, r15);
    __ movl(r15, rdx);
    __ movq(rax, r11);
    __ bind(L_0x405018);
    __ vpcmpeqb(xmm2, xmm0, Address(rax, 0), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rax, 0x1), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rdx, xmm2, Assembler::AVX_256bit);
    __ testl(rdx, rdx);
    __ jne(L_0x40607f);
    __ addq(rax, r15);
    __ cmpq(rax, rcx);
    __ jae(L_exit);
    __ vpcmpeqb(xmm2, xmm0, Address(rax, 0), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rax, 0x1), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rdx, xmm2, Assembler::AVX_256bit);
    __ testl(rdx, rdx);
    __ jne(L_0x40607f);
    __ addq(rax, 0x20);
    __ movl(r15, 0x20);
    __ cmpq(rax, rcx);
    __ jb(L_0x405018);
    __ jmp(L_exit);

    //    case 3:
    large_hs_jmp_table[2] = __ pc();
    {
      Label L_top, L_inner;
      loop_helper(3, L_exit, L_top);
      __ movzbl(rsi, Address(r10, 0x1));
      __ bind(L_inner);
      __ tzcntl(rdi, rdx);
      __ cmpb(Address(rcx, rdi, Address::times_1, 0x1), rsi);
      __ je(L_0x405cee);
      __ blsrl(rdx, rdx);
      __ jne_b(L_inner);
      __ jmp(L_top);
    }

    //    case 4:
    large_hs_jmp_table[3] = __ pc();
    {
      Label L_top, L_inner;
      loop_helper(4, L_exit, L_top);
      __ movzwl(rsi, Address(r10, 0x1));
      __ bind(L_inner);
      __ tzcntl(rdi, rdx);
      __ cmpw(Address(rcx, rdi, Address::times_1, 0x1), rsi);
      __ je(L_0x405cee);
      __ blsrl(rdx, rdx);
      __ jne_b(L_inner);
      __ jmp(L_top);
    }

    //    case 5:
    large_hs_jmp_table[4] = __ pc();
    {
      Label L_top, L_inner;
      loop_helper(5, L_exit, L_top);
      __ movl(rsi, Address(r10, 0x1));
      __ bind(L_inner);
      __ tzcntl(rdi, rdx);
      __ cmpl(Address(rcx, rdi, Address::times_1, 0x1), rsi);
      __ je(L_0x405cee);
      __ blsrl(rdx, rdx);
      __ jne_b(L_inner);
      __ jmp(L_top);
    }

    //    case 6:
    large_hs_jmp_table[5] = __ pc();
    {
      Label L_top, L_inner;
      loop_helper(6, L_exit, L_top);
      __ movl(rsi, Address(r10, 0x1));
      __ bind(L_inner);
      __ tzcntl(rdi, rdx);
      __ cmpl(Address(rcx, rdi, Address::times_1, 0x1), rsi);
      __ je(L_0x405cee);
      __ blsrl(rdx, rdx);
      __ jne_b(L_inner);
      __ jmp(L_top);
    }

    //    case 7:
    large_hs_jmp_table[6] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(7, L_exit, L_top);
      __ movl(rsi, Address(r10, 0x1));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(rdi, rdx);
      __ cmpl(Address(rcx, rdi, Address::times_1, 0x1), rsi);
      __ jne_b(L_inner);
      __ movzbl(r8, Address(rcx, rdi, Address::times_1, 0x5));
      __ cmpb(r8, Address(r10, 0x5));
      __ jne_b(L_inner);
      __ jmp(L_0x40559d);
    }

    //    case 8:
    large_hs_jmp_table[7] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(8, L_exit, L_top);
      __ movl(rsi, Address(r10, 0x1));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(rdi, rdx);
      __ cmpl(Address(rcx, rdi, Address::times_1, 0x1), rsi);
      __ jne_b(L_inner);
      __ movzwl(r8, Address(rcx, rdi, Address::times_1, 0x5));
      __ cmpw(Address(r10, 0x5), r8);
      __ jne_b(L_inner);
      __ jmp(L_0x40559d);
    }

    //    case 9:
    large_hs_jmp_table[8] = __ pc();
    {
      Label L_top, L_inner;
      loop_helper(9, L_exit, L_top);
      __ movq(rsi, Address(r10, 0x1));
      __ bind(L_inner);
      __ tzcntl(rdi, rdx);
      __ cmpq(Address(rcx, rdi, Address::times_1, 0x1), rsi);
      __ je(L_0x405cee);
      __ blsrl(rdx, rdx);
      __ jne_b(L_inner);
      __ jmp(L_top);
    }

    //    case 10:
    large_hs_jmp_table[9] = __ pc();
    {
      Label L_top, L_inner;
      loop_helper(10, L_exit, L_top);
      __ movq(rsi, Address(r10, 0x1));
      __ bind(L_inner);
      __ tzcntl(rdi, rdx);
      __ cmpq(Address(rcx, rdi, Address::times_1, 0x1), rsi);
      __ je(L_0x405cee);
      __ blsrl(rdx, rdx);
      __ jne_b(L_inner);
      __ jmp(L_top);
    }

    //    case 11:
    large_hs_jmp_table[10] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(11, L_exit, L_top);
      __ movq(rsi, Address(r10, 0x1));
      __ movzbl(rdi, Address(r10, 0x9));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r8, rdx);
      __ cmpq(Address(rcx, r8, Address::times_1, 0x1), rsi);
      __ jne_b(L_inner);
      __ cmpb(Address(rcx, r8, Address::times_1, 0x9), rdi);
      __ jne_b(L_inner);
      __ jmp(L_0x405f5d);
    }

    //    case 12:
    large_hs_jmp_table[11] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(12, L_exit, L_top);
      __ movq(rsi, Address(r10, 0x1));
      __ movzwl(rdi, Address(r10, 0x9));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r8, rdx);
      __ cmpq(Address(rcx, r8, Address::times_1, 0x1), rsi);
      __ jne_b(L_inner);
      __ cmpw(Address(rcx, r8, Address::times_1, 0x9), rdi);
      __ jne_b(L_inner);
      __ jmp(L_0x405f5d);
    }

    //    case 13:
    large_hs_jmp_table[12] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(13, L_exit, L_top);
      __ movq(rsi, Address(r10, 0x1));
      __ jmpb(L_tmp);
      __ align(8);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(rdi, rdx);
      __ cmpq(Address(rcx, rdi, Address::times_1, 0x1), rsi);
      __ jne_b(L_inner);
      __ movzwl(r8, Address(rcx, rdi, Address::times_1, 0x9));
      __ cmpw(Address(r10, 0x9), r8);
      __ jne_b(L_inner);
      __ movzbl(r8, Address(rcx, rdi, Address::times_1, 0xb));
      __ cmpb(r8, Address(r10, 0xb));
      __ jne_b(L_inner);
      __ bind(L_0x40559d);
      __ subq(rcx, r11);
      __ addq(rcx, rdi);
      __ jmp(L_0x406008);
    }

    //    case 14:
    large_hs_jmp_table[13] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(14, L_exit, L_top);
      __ movq(rsi, Address(r10, 0x1));
      __ movl(rdi, Address(r10, 0x9));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r8, rdx);
      __ cmpq(Address(rcx, r8, Address::times_1, 0x1), rsi);
      __ jne_b(L_inner);
      __ cmpl(Address(rcx, r8, Address::times_1, 0x9), rdi);
      __ jne_b(L_inner);
      __ jmp(L_0x405f5d);
    }

    //    case 15:
    large_hs_jmp_table[14] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(15, L_exit, L_top);
      __ movq(rsi, Address(r10, 0x1));
      __ movl(rdi, Address(r10, 0x9));
      __ movzbl(r8, Address(r10, 0xd));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r9, rdx);
      __ cmpq(Address(rcx, r9, Address::times_1, 0x1), rsi);
      __ jne_b(L_inner);
      __ cmpl(Address(rcx, r9, Address::times_1, 0x9), rdi);
      __ jne_b(L_inner);
      __ cmpb(Address(rcx, r9, Address::times_1, 0xd), r8);
      __ jne_b(L_inner);
      __ jmp(L_0x405fff);
    }

    //    case 16:
    large_hs_jmp_table[15] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(16, L_exit, L_top);
      __ movq(rsi, Address(r10, 0x1));
      __ movl(rdi, Address(r10, 0x9));
      __ movzwl(r8, Address(r10, 0xd));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r9, rdx);
      __ cmpq(Address(rcx, r9, Address::times_1, 0x1), rsi);
      __ jne_b(L_inner);
      __ cmpl(Address(rcx, r9, Address::times_1, 0x9), rdi);
      __ jne_b(L_inner);
      __ cmpw(Address(rcx, r9, Address::times_1, 0xd), r8);
      __ jne_b(L_inner);
      __ jmp(L_0x405fff);
    }

    //    case 17:
    large_hs_jmp_table[16] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      __ movq(r14, r10);
      loop_helper(17, L_exit, L_top);
      __ movq(r9, r14);
      __ movq(rsi, Address(r14, 0x1));
      __ movl(rdi, Address(r14, 0x9));
      __ movzwl(r8, Address(r14, 0xd));
      __ movzbl(r9, Address(r14, 0xf));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r10, rdx);
      __ cmpq(Address(rcx, r10, Address::times_1, 0x1), rsi);
      __ jne_b(L_inner);
      __ cmpl(Address(rcx, r10, Address::times_1, 0x9), rdi);
      __ jne_b(L_inner);
      __ cmpw(Address(rcx, r10, Address::times_1, 0xd), r8);
      __ jne_b(L_inner);
      __ cmpb(Address(rcx, r10, Address::times_1, 0xf), r9);
      __ jne_b(L_inner);
      __ movl(rax, r10);
      __ jmp(L_final_check);
    }

    //    case 18:
    large_hs_jmp_table[17] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(18, L_exit, L_top);
      __ movq(rsi, Address(r10, 0x1));
      __ movq(rdi, Address(r10, 0x9));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r8, rdx);
      __ cmpq(Address(rcx, r8, Address::times_1, 0x1), rsi);
      __ jne_b(L_inner);
      __ cmpq(Address(rcx, r8, Address::times_1, 0x9), rdi);
      __ jne_b(L_inner);
      __ jmp(L_0x405f5d);
    }

    //    case 19:
    large_hs_jmp_table[18] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(19, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movzbl(rsi, Address(r10, 0x11));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(rdi, rdx);
      __ movdqu(xmm3, Address(rcx, rdi, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpb(Address(rcx, rdi, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ jmp(L_0x405cee);
    }

    //    case 20:
    large_hs_jmp_table[19] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(20, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movzwl(rsi, Address(r10, 0x11));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(rdi, rdx);
      __ movdqu(xmm3, Address(rcx, rdi, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpw(Address(rcx, rdi, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ jmp(L_0x405cee);
    }

    //    case 21:
    large_hs_jmp_table[20] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(21, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movzwl(rsi, Address(r10, 0x11));
      __ movzbl(rdi, Address(r10, 0x13));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r8, rdx);
      __ movdqu(xmm3, Address(rcx, r8, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpw(Address(rcx, r8, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ cmpb(Address(rcx, r8, Address::times_1, 0x13), rdi);
      __ jne_b(L_inner);
      __ jmp(L_0x405f5d);
    }

    //    case 22:
    large_hs_jmp_table[21] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(22, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movl(rsi, Address(r10, 0x11));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(rdi, rdx);
      __ movdqu(xmm3, Address(rcx, rdi, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpl(Address(rcx, rdi, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ jmp(L_0x405cee);
    }

    //    case 23:
    large_hs_jmp_table[22] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(23, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movl(rsi, Address(r10, 0x11));
      __ movzbl(rdi, Address(r10, 0x15));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r8, rdx);
      __ movdqu(xmm3, Address(rcx, r8, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpl(Address(rcx, r8, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ cmpb(Address(rcx, r8, Address::times_1, 0x15), rdi);
      __ jne_b(L_inner);
      __ jmp(L_0x405f5d);
    }

    //    case 24:
    large_hs_jmp_table[23] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(24, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movl(rsi, Address(r10, 0x11));
      __ movzwl(rdi, Address(r10, 0x15));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r8, rdx);
      __ movdqu(xmm3, Address(rcx, r8, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpl(Address(rcx, r8, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ cmpw(Address(rcx, r8, Address::times_1, 0x15), rdi);
      __ jne_b(L_inner);
      __ jmp(L_0x405f5d);
    }

    //    case 25:
    large_hs_jmp_table[24] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(25, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movl(rsi, Address(r10, 0x11));
      __ movzwl(rdi, Address(r10, 0x15));
      __ movzbl(r8, Address(r10, 0x17));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r9, rdx);
      __ movdqu(xmm3, Address(rcx, r9, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpl(Address(rcx, r9, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ cmpw(Address(rcx, r9, Address::times_1, 0x15), rdi);
      __ jne_b(L_inner);
      __ cmpb(Address(rcx, r9, Address::times_1, 0x17), r8);
      __ jne_b(L_inner);
      __ jmp(L_0x405fff);
    }

    //    case 26:
    large_hs_jmp_table[25] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(26, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movq(rsi, Address(r10, 0x11));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(rdi, rdx);
      __ movdqu(xmm3, Address(rcx, rdi, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpq(Address(rcx, rdi, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ bind(L_0x405cee);
      __ movl(rax, rdi);
      __ jmp(L_final_check);
    }

    //    case 27:
    large_hs_jmp_table[26] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(27, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movq(rsi, Address(r10, 0x11));
      __ movzbl(rdi, Address(r10, 0x19));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r8, rdx);
      __ movdqu(xmm3, Address(rcx, r8, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpq(Address(rcx, r8, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ cmpb(Address(rcx, r8, Address::times_1, 0x19), rdi);
      __ jne_b(L_inner);
      __ jmp(L_0x405f5d);
    }

    //    case 28:
    large_hs_jmp_table[27] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(28, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movq(rsi, Address(r10, 0x11));
      __ movzwl(rdi, Address(r10, 0x19));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r8, rdx);
      __ movdqu(xmm3, Address(rcx, r8, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpq(Address(rcx, r8, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ cmpw(Address(rcx, r8, Address::times_1, 0x19), rdi);
      __ jne_b(L_inner);
      __ jmp(L_0x405f5d);
    }

    //    case 29:
    large_hs_jmp_table[28] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(29, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movq(rsi, Address(r10, 0x11));
      __ movzwl(rdi, Address(r10, 0x19));
      __ movzbl(r8, Address(r10, 0x1b));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r9, rdx);
      __ movdqu(xmm3, Address(rcx, r9, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpq(Address(rcx, r9, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ cmpw(Address(rcx, r9, Address::times_1, 0x19), rdi);
      __ jne_b(L_inner);
      __ cmpb(Address(rcx, r9, Address::times_1, 0x1b), r8);
      __ jne_b(L_inner);
      __ jmp(L_0x405fff);
    }

    //    case 30:
    large_hs_jmp_table[29] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(30, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movq(rsi, Address(r10, 0x11));
      __ movl(rdi, Address(r10, 0x19));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r8, rdx);
      __ movdqu(xmm3, Address(rcx, r8, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpq(Address(rcx, r8, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ cmpl(Address(rcx, r8, Address::times_1, 0x19), rdi);
      __ jne_b(L_inner);
      __ bind(L_0x405f5d);
      __ movl(rax, r8);
      __ jmp(L_final_check);
    }

    //    case 31:
    large_hs_jmp_table[30] = __ pc();
    {
      Label L_top, L_inner, L_tmp;
      loop_helper(31, L_exit, L_top);
      __ movdqu(xmm2, Address(r10, 0x1));
      __ movq(rsi, Address(r10, 0x11));
      __ movl(rdi, Address(r10, 0x19));
      __ movzbl(r8, Address(r10, 0x1d));
      __ jmpb(L_tmp);
      __ bind(L_inner);
      __ blsrl(rdx, rdx);
      __ je(L_top);
      __ bind(L_tmp);
      __ tzcntl(r9, rdx);
      __ movdqu(xmm3, Address(rcx, r9, Address::times_1, 0x1));
      __ vpsubb(xmm3, xmm3, xmm2, Assembler::AVX_128bit);
      __ vptest(xmm3, xmm3, Assembler::AVX_128bit);
      __ jne_b(L_inner);
      __ cmpq(Address(rcx, r9, Address::times_1, 0x11), rsi);
      __ jne_b(L_inner);
      __ cmpl(Address(rcx, r9, Address::times_1, 0x19), rdi);
      __ jne_b(L_inner);
      __ cmpb(Address(rcx, r9, Address::times_1, 0x1d), r8);
      __ jne_b(L_inner);
    }
    __ bind(L_0x405fff);
    __ movl(rax, r9);

    //   if (result <= n - k)
    //   {
    //     return result;
    //   }
    __ bind(L_final_check);
    __ subq(rcx, r11);
    __ addq(rcx, rax);

    __ bind(L_0x406008);
    __ movq(r13, rcx);

    __ bind(L_exit);
    __ cmpq(r13, rbx);
    __ movq(r15, -1);
    __ cmovq(Assembler::belowEqual, r15, r13);
    __ bind(L_0x406019);

    // CASE 0: CASE 1:
    small_hs_jmp_table[0] = __ pc();
    small_hs_jmp_table[1] = __ pc();
    __ movq(rax, r15);
    __ addptr(rsp, 0xf0);
#ifdef _WIN64
    __ pop(r9);
    __ pop(r8);
    __ pop(rcx);
    __ pop(rdi);
    __ pop(rsi);
#endif
    __ pop(rbp);
    __ pop(rbx);
    __ pop(r12);
    __ pop(r13);
    __ pop(r14);
    __ pop(r15);
    __ vzeroupper();

    __ leave();  // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    __ bind(L_0x40602e);
    __ movl(rax, rbp);
    __ movq(r13, Address(rsp, 0x8));
    __ subq(r13, Address(rsp, 0x18));
    __ addq(r13, rax);
    __ movq(rbx, Address(rsp, 0x20));
    __ jmpb(L_exit);

    __ bind(L_0x406044);
    __ movq(rax, r15);
    __ andq(rax, -32);
    __ andl(r15, 0x1f);
    __ movq(r13, -1);
    __ cmpq(r15, rax);
    __ jge(L_exit);
    __ addq(rax, r11);

    __ bind(L_0x40605e);
    __ vpcmpeqb(xmm1, xmm0, Address(r11, r15, Address::times_1),
                Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm1, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ jne_b(L_0x406093);
    __ leaq(rcx, Address(r11, r15, Address::times_1));
    __ addq(rcx, 0x20);
    __ addq(r15, 0x20);
    __ cmpq(rcx, rax);
    __ jb(L_0x40605e);
    __ jmp(L_exit);

    __ bind(L_0x40607f);
    __ tzcntl(rcx, rdx);
    __ subq(rax, r11);
    __ addq(rax, rcx);
    __ movq(r13, rax);
    __ jmp(L_exit);

    __ bind(L_0x406093);
    __ tzcntl(r13, rcx);
    __ addq(r13, r15);
    __ jmp(L_exit);

    __ bind(L_0x4060a3);
    __ movq(rbx, Address(rsp, 0x20));
    __ movq(r13, -1);
    __ jmp(L_exit);

    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    __ align(8);

    jump_table = __ pc();

    for (jmp_ndx = 0; jmp_ndx < 32; jmp_ndx++) {
      __ emit_address(large_hs_jmp_table[jmp_ndx]);
    }

    jump_table_1 = __ pc();

    for (jmp_ndx = 0; jmp_ndx < 32; jmp_ndx++) {
      __ emit_address(small_hs_jmp_table[jmp_ndx]);
    }

    __ align(16);
    __ bind(L_begin);
    __ push(r15);
    __ push(r14);
    __ push(r13);
    __ push(r12);
    __ push(rbx);
    __ push(rbp);
#ifdef _WIN64
    __ push(rsi);
    __ push(rdi);
    __ push(rcx);
    __ push(r8);
    __ push(r9);

    __ movq(rdi, rcx);
    __ movq(rsi, rdx);
    __ movq(rdx, r8);
    __ movq(rcx, r9);
#endif

    __ subptr(rsp, 0xf0);
    // if (n < k) {
    //   return result;
    // }
    __ movq(rbx, rsi);
    __ subq(rbx, rcx);
    __ jae_b(L_0x404912);

    __ bind(L_error);
    __ movq(r15, -1);
    __ jmp(L_0x406019);

    __ bind(L_0x404912);

    // if (k == 0) {
    //   return 0;
    // }
    __ movq(r12, rcx);
    __ testq(rcx, rcx);
    __ je(L_CASE_0);
    __ movq(r10, rdx);
    __ movq(r15, rsi);
    __ movq(r11, rdi);
    __ cmpq(rsi, 0x20);
    __ jb(L_small_string);
    __ leaq(rax, Address(r12, 0x1f));
    __ cmpq(rax, r15);
    __ jg(L_small_string);

    // if ((n < 32) || ((long long)n < 32 + (long long)k - 1))
    __ bind(L_0x404933);
    __ leaq(rax, Address(r12, -0x1));
    __ cmpq(rax, 0x1e);
    __ ja(L_anysize);
    __ mov64(r13, (int64_t)jump_table);
    __ jmp(Address(r13, rax, Address::times_8));

    __ bind(L_trampoline);
    __ mov64(rdi, (int64_t)jump_table_1);
    __ jmp(Address(rdi, rax, Address::times_8));

  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////
  //                         memcmp_avx2
  ////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////

    __ align(CodeEntryAlignment);
    __ bind(memcmp_avx2);

    //    1 /* memcmp/wmemcmp optimized with AVX2.
    //    2    Copyright (C) 2017-2023 Free Software Foundation, Inc.
    //    3    This file is part of the GNU C Library.
    //    4
    //    5    The GNU C Library is free software; you can redistribute it
    //    and/or 6    modify it under the terms of the GNU Lesser General Public
    //    7    License as published by the Free Software Foundation; either
    //    8    version 2.1 of the License, or (at your option) any later
    //    version.
    //    9
    //   10    The GNU C Library is distributed in the hope that it will be
    //   useful, 11    but WITHOUT ANY WARRANTY; without even the implied
    //   warranty of 12    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    //   See the GNU 13    Lesser General Public License for more details. 14 15
    //   You should have received a copy of the GNU Lesser General Public 16
    //   License along with the GNU C Library; if not, see 17
    //   <https://www.gnu.org/licenses/>.  */ 18 23 /* memcmp/wmemcmp is
    //   implemented as: 24    1. Use ymm vector compares when possible. The
    //   only case where 25       vector compares is not possible for when size
    //   < VEC_SIZE 26       and loading from either s1 or s2 would cause a page
    //   cross. 27    2. For size from 2 to 7 bytes on page cross, load as big
    //   endian 28       with movbe and bswap to avoid branches. 29    3. Use
    //   xmm vector compare when size >= 4 bytes for memcmp or 30       size >=
    //   8 bytes for wmemcmp. 31    4. Optimistically compare up to first 4 *
    //   VEC_SIZE one at a 32       to check for early mismatches. Only do this
    //   if its guaranteed the 33       work is not wasted. 34    5. If size is
    //   8 * VEC_SIZE or less, unroll the loop. 35    6. Compare 4 * VEC_SIZE at
    //   a time with the aligned first memory 36       area. 37    7. Use 2
    //   vector compares when size is 2 * VEC_SIZE or less. 38    8. Use 4
    //   vector compares when size is 4 * VEC_SIZE or less. 39    9. Use 8
    //   vector compares when size is 8 * VEC_SIZE or less.  */

    Label L_less_vec, L_return_vec_0, L_last_1x_vec, L_return_vec_1,
        L_last_2x_vec;
    Label L_return_vec_2, L_retun_vec_3, L_more_8x_vec, L_return_vec_0_1_2_3;
    Label L_return_vzeroupper, L_8x_return_vec_0_1_2_3, L_loop_4x_vec;
    Label L_return_vec_3, L_8x_last_1x_vec, L_8x_last_2x_vec, L_8x_return_vec_2;
    Label L_8x_return_vec_3, L_return_vec_1_end, L_return_vec_0_end,
        L_one_or_less;
    Label L_page_cross_less_vec, L_between_16_31, L_between_8_15, L_between_2_3,
        L_zero;
    Label L_ret_nonzero;

    // 72              .section SECTION(.text),"ax",@progbits
    // 73      ENTRY (MEMCMP)
    // 74      # ifdef USE_AS_WMEMCMP
    // 75              shl     $2, %RDX_LP
    // 76      # elif defined __ILP32__
    // 77              /* Clear the upper 32 bits.  */
    // 78              movl    %edx, %edx

    // 79      # endif

    // 80              cmp     $VEC_SIZE, %RDX_LP
    // 81              jb      L(less_vec)
    // 82
    __ cmpq(rdx, 0x20);
    __ jb(L_less_vec);
    __ vmovdqu(xmm1, Address(rsi, 0));

    // 83              /* From VEC to 2 * VEC.  No branch when size == VEC_SIZE.
    // */
    __ vpcmpeqb(xmm1, xmm1, Address(rdi, 0), Assembler::AVX_256bit);

    // 84              vmovdqu (%rsi), %ymm1
    __ vpmovmskb(rax, xmm1, Assembler::AVX_256bit);

    // 85              VPCMPEQ (%rdi), %ymm1, %ymm1
    // 86              vpmovmskb %ymm1, %eax
    // 87              /* NB: eax must be destination register if going to
    // 88                 L(return_vec_[0,2]). For L(return_vec_3 destination
    // register 89                 must be ecx.  */
    __ incrementl(rax);
    __ jne(L_return_vec_0);

    // 90              incl    %eax
    // 91              jnz     L(return_vec_0)
    __ cmpq(rdx, 0x40);

    // 92
    __ jbe(L_last_1x_vec);

    // 93              cmpq    $(VEC_SIZE * 2), %rdx
    // 94              jbe     L(last_1x_vec)
    // 95
    __ vmovdqu(xmm2, Address(rsi, 0x20));

    // 96              /* Check second VEC no matter what.  */
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0x20), Assembler::AVX_256bit);

    // 97              vmovdqu VEC_SIZE(%rsi), %ymm2
    __ vpmovmskb(rax, xmm2, Assembler::AVX_256bit);

    // 98              VPCMPEQ VEC_SIZE(%rdi), %ymm2, %ymm2
    // 99              vpmovmskb %ymm2, %eax
    // 100             /* If all 4 VEC where equal eax will be all 1s so incl
    // will
    __ incrementl(rax);

    // 101                overflow and set zero flag.  */
    __ jne(L_return_vec_1);

    // 102             incl    %eax
    // 103             jnz     L(return_vec_1)
    // 104
    __ cmpq(rdx, 0x80);

    // 105             /* Less than 4 * VEC.  */
    __ jbe(L_last_2x_vec);

    // 106             cmpq    $(VEC_SIZE * 4), %rdx
    // 107             jbe     L(last_2x_vec)
    // 108
    __ vmovdqu(xmm3, Address(rsi, 0x40));

    // 109             /* Check third and fourth VEC no matter what.  */
    __ vpcmpeqb(xmm3, xmm3, Address(rdi, 0x40), Assembler::AVX_256bit);

    // 110             vmovdqu (VEC_SIZE * 2)(%rsi), %ymm3
    __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);

    // 111             VPCMPEQ (VEC_SIZE * 2)(%rdi), %ymm3, %ymm3
    __ incrementl(rax);

    // 112             vpmovmskb %ymm3, %eax
    __ jne(L_return_vec_2);

    // 113             incl    %eax
    __ vmovdqu(xmm4, Address(rsi, 0x60));

    // 114             jnz     L(return_vec_2)
    __ vpcmpeqb(xmm4, xmm4, Address(rdi, 0x60), Assembler::AVX_256bit);

    // 115             vmovdqu (VEC_SIZE * 3)(%rsi), %ymm4
    __ vpmovmskb(rcx, xmm4, Assembler::AVX_256bit);

    // 116             VPCMPEQ (VEC_SIZE * 3)(%rdi), %ymm4, %ymm4
    __ incrementl(rcx);

    // 117             vpmovmskb %ymm4, %ecx
    __ jne(L_return_vec_3);

    // 118             incl    %ecx
    // 119             jnz     L(return_vec_3)
    // 120
    __ cmpq(rdx, 0x100);

    // 121             /* Go to 4x VEC loop.  */
    __ ja(L_more_8x_vec);

    // 122             cmpq    $(VEC_SIZE * 8), %rdx
    // 123             ja      L(more_8x_vec)
    // 124
    // 125             /* Handle remainder of size = 4 * VEC + 1 to 8 * VEC
    // without any 126                branches.  */ 127
    __ vmovdqu(xmm1, Address(rsi, rdx, Address::times_1, -0x80));

    // 128             /* Load first two VEC from s2 before adjusting addresses.
    // */
    __ vmovdqu(xmm2, Address(rsi, rdx, Address::times_1, -0x60));

    // 129             vmovdqu -(VEC_SIZE * 4)(%rsi, %rdx), %ymm1
    __ leaq(rdi, Address(rdi, rdx, Address::times_1, -0x80));

    // 130             vmovdqu -(VEC_SIZE * 3)(%rsi, %rdx), %ymm2
    __ leaq(rsi, Address(rsi, rdx, Address::times_1, -0x80));

    // 131             leaq    -(4 * VEC_SIZE)(%rdi, %rdx), %rdi
    // 132             leaq    -(4 * VEC_SIZE)(%rsi, %rdx), %rsi
    // 133
    // 134             /* Wait to load from s1 until addressed adjust due to
    __ vpcmpeqb(xmm1, xmm1, Address(rdi, 0), Assembler::AVX_256bit);

    // 135                unlamination of microfusion with complex address mode.
    // */
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0x20), Assembler::AVX_256bit);

    // 136             VPCMPEQ (%rdi), %ymm1, %ymm1
    // 137             VPCMPEQ (VEC_SIZE)(%rdi), %ymm2, %ymm2
    __ vmovdqu(xmm3, Address(rsi, 0x40));

    // 138
    __ vpcmpeqb(xmm3, xmm3, Address(rdi, 0x40), Assembler::AVX_256bit);

    // 139             vmovdqu (VEC_SIZE * 2)(%rsi), %ymm3
    __ vmovdqu(xmm4, Address(rsi, 0x60));

    // 140             VPCMPEQ (VEC_SIZE * 2)(%rdi), %ymm3, %ymm3
    __ vpcmpeqb(xmm4, xmm4, Address(rdi, 0x60), Assembler::AVX_256bit);

    // 141             vmovdqu (VEC_SIZE * 3)(%rsi), %ymm4
    // 142             VPCMPEQ (VEC_SIZE * 3)(%rdi), %ymm4, %ymm4
    // 143
    __ vpand(xmm5, xmm2, xmm1, Assembler::AVX_256bit);

    // 144             /* Reduce VEC0 - VEC4.  */
    __ vpand(xmm6, xmm4, xmm3, Assembler::AVX_256bit);

    // 145             vpand   %ymm1, %ymm2, %ymm5
    __ vpand(xmm7, xmm6, xmm5, Assembler::AVX_256bit);

    // 146             vpand   %ymm3, %ymm4, %ymm6
    __ vpmovmskb(rcx, xmm7, Assembler::AVX_256bit);

    // 147             vpand   %ymm5, %ymm6, %ymm7
    __ incrementl(rcx);

    // 148             vpmovmskb %ymm7, %ecx
    __ jne_b(L_return_vec_0_1_2_3);

    // 149             incl    %ecx
    // 150             jnz     L(return_vec_0_1_2_3)
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_return_vec_0);

    // 151             /* NB: eax must be zero to reach here.  */
    // 152             VZEROUPPER_RETURN
    // 153
    // 154             .p2align 4
    __ tzcntl(rax, rax);

    // 155     L(return_vec_0):
    // 156             tzcntl  %eax, %eax
    // 157     # ifdef USE_AS_WMEMCMP
    // 158             movl    (%rdi, %rax), %ecx
    // 159             xorl    %edx, %edx
    // 160             cmpl    (%rsi, %rax), %ecx
    // 161             /* NB: no partial register stall here because xorl zero
    // idiom 162                above.  */ 163             setg    %dl 164 leal
    // -1(%rdx, %rdx), %eax
    __ movzbl(rcx, Address(rsi, rax, Address::times_1));

    // 165     # else
    __ movzbl(rax, Address(rdi, rax, Address::times_1));

    // 166             movzbl  (%rsi, %rax), %ecx
    __ subl(rax, rcx);

    // 167             movzbl  (%rdi, %rax), %eax
    // 168             subl    %ecx, %eax
    // 169     # endif
    __ bind(L_return_vzeroupper);
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_return_vec_1);

    // 170     L(return_vzeroupper):
    // 171             ZERO_UPPER_VEC_REGISTERS_RETURN
    // 172
    // 173             .p2align 4
    __ tzcntl(rax, rax);

    // 174     L(return_vec_1):
    // 175             tzcntl  %eax, %eax
    // 176     # ifdef USE_AS_WMEMCMP
    // 177             movl    VEC_SIZE(%rdi, %rax), %ecx
    // 178             xorl    %edx, %edx
    // 179             cmpl    VEC_SIZE(%rsi, %rax), %ecx
    // 180             setg    %dl
    // 181             leal    -1(%rdx, %rdx), %eax
    __ movzbl(rcx, Address(rsi, rax, Address::times_1, 0x20));

    // 182     # else
    __ movzbl(rax, Address(rdi, rax, Address::times_1, 0x20));

    // 183             movzbl  VEC_SIZE(%rsi, %rax), %ecx
    __ subl(rax, rcx);

    // 184             movzbl  VEC_SIZE(%rdi, %rax), %eax
    // 185             subl    %ecx, %eax
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_return_vec_2);

    // 186     # endif
    // 187             VZEROUPPER_RETURN
    // 188
    // 189             .p2align 4
    __ tzcntl(rax, rax);

    // 190     L(return_vec_2):
    // 191             tzcntl  %eax, %eax
    // 192     # ifdef USE_AS_WMEMCMP
    // 193             movl    (VEC_SIZE * 2)(%rdi, %rax), %ecx
    // 194             xorl    %edx, %edx
    // 195             cmpl    (VEC_SIZE * 2)(%rsi, %rax), %ecx
    // 196             setg    %dl
    // 197             leal    -1(%rdx, %rdx), %eax
    __ movzbl(rcx, Address(rsi, rax, Address::times_1, 0x40));

    // 198     # else
    __ movzbl(rax, Address(rdi, rax, Address::times_1, 0x40));

    // 199             movzbl  (VEC_SIZE * 2)(%rsi, %rax), %ecx
    __ subl(rax, rcx);

    // 200             movzbl  (VEC_SIZE * 2)(%rdi, %rax), %eax
    // 201             subl    %ecx, %eax
    __ vzeroupper();
    __ ret(0);
    __ align(32);

    __ bind(L_8x_return_vec_0_1_2_3);

    // 202     # endif
    // 203             VZEROUPPER_RETURN
    // 204
    // 205             /* NB: p2align 5 here to ensure 4x loop is 32 byte
    // aligned.  */ 206             .p2align 5 207     L(8x_return_vec_0_1_2_3):
    __ addq(rsi, rdi);

    // 208             /* Returning from L(more_8x_vec) requires restoring rsi.
    // */ 209             addq    %rdi, %rsi
    __ bind(L_return_vec_0_1_2_3);
    __ vpmovmskb(rax, xmm1, Assembler::AVX_256bit);

    // 210     L(return_vec_0_1_2_3):
    __ incrementl(rax);

    // 211             vpmovmskb %ymm1, %eax
    __ jne_b(L_return_vec_0);

    // 212             incl    %eax
    // 213             jnz     L(return_vec_0)
    __ vpmovmskb(rax, xmm2, Assembler::AVX_256bit);

    // 214
    __ incrementl(rax);

    // 215             vpmovmskb %ymm2, %eax
    __ jne_b(L_return_vec_1);

    // 216             incl    %eax
    // 217             jnz     L(return_vec_1)
    __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);

    // 218
    __ incrementl(rax);

    // 219             vpmovmskb %ymm3, %eax
    __ jne_b(L_return_vec_2);

    // 220             incl    %eax
    // 221             jnz     L(return_vec_2)
    __ bind(L_return_vec_3);
    __ tzcntl(rcx, rcx);

    // 222     L(return_vec_3):
    // 223             tzcntl  %ecx, %ecx
    // 224     # ifdef USE_AS_WMEMCMP
    // 225             movl    (VEC_SIZE * 3)(%rdi, %rcx), %eax
    // 226             xorl    %edx, %edx
    // 227             cmpl    (VEC_SIZE * 3)(%rsi, %rcx), %eax
    // 228             setg    %dl
    // 229             leal    -1(%rdx, %rdx), %eax
    __ movzbl(rax, Address(rdi, rcx, Address::times_1, 0x60));

    // 230     # else
    __ movzbl(rcx, Address(rsi, rcx, Address::times_1, 0x60));

    // 231             movzbl  (VEC_SIZE * 3)(%rdi, %rcx), %eax
    __ subl(rax, rcx);

    // 232             movzbl  (VEC_SIZE * 3)(%rsi, %rcx), %ecx
    // 233             subl    %ecx, %eax
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_more_8x_vec);

    // 234     # endif
    // 235             VZEROUPPER_RETURN
    // 236
    // 237             .p2align 4
    // 238     L(more_8x_vec):
    __ leaq(rdx, Address(rdi, rdx, Address::times_1, -0x80));

    // 239             /* Set end of s1 in rdx.  */
    // 240             leaq    -(VEC_SIZE * 4)(%rdi, %rdx), %rdx
    // 241             /* rsi stores s2 - s1. This allows loop to only update
    // one
    __ subq(rsi, rdi);

    // 242                pointer.  */
    // 243             subq    %rdi, %rsi
    __ andq(rdi, -32);

    // 244             /* Align s1 pointer.  */
    // 245             andq    $-VEC_SIZE, %rdi
    __ subq(rdi, -128);
    __ align(16);
    __ bind(L_loop_4x_vec);

    // 246             /* Adjust because first 4x vec where check already.  */
    // 247             subq    $-(VEC_SIZE * 4), %rdi
    // 248             .p2align 4
    // 249     L(loop_4x_vec):
    // 250             /* rsi has s2 - s1 so get correct address by adding s1
    // (in rdi).
    __ vmovdqu(xmm1, Address(rsi, rdi, Address::times_1));

    // 251              */
    __ vpcmpeqb(xmm1, xmm1, Address(rdi, 0), Assembler::AVX_256bit);

    // 252             vmovdqu (%rsi, %rdi), %ymm1
    // 253             VPCMPEQ (%rdi), %ymm1, %ymm1
    __ vmovdqu(xmm2, Address(rsi, rdi, Address::times_1, 0x20));

    // 254
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0x20), Assembler::AVX_256bit);

    // 255             vmovdqu VEC_SIZE(%rsi, %rdi), %ymm2
    // 256             VPCMPEQ VEC_SIZE(%rdi), %ymm2, %ymm2
    __ vmovdqu(xmm3, Address(rsi, rdi, Address::times_1, 0x40));

    // 257
    __ vpcmpeqb(xmm3, xmm3, Address(rdi, 0x40), Assembler::AVX_256bit);

    // 258             vmovdqu (VEC_SIZE * 2)(%rsi, %rdi), %ymm3
    // 259             VPCMPEQ (VEC_SIZE * 2)(%rdi), %ymm3, %ymm3
    __ vmovdqu(xmm4, Address(rsi, rdi, Address::times_1, 0x60));

    // 260
    __ vpcmpeqb(xmm4, xmm4, Address(rdi, 0x60), Assembler::AVX_256bit);

    // 261             vmovdqu (VEC_SIZE * 3)(%rsi, %rdi), %ymm4
    // 262             VPCMPEQ (VEC_SIZE * 3)(%rdi), %ymm4, %ymm4
    __ vpand(xmm5, xmm2, xmm1, Assembler::AVX_256bit);

    // 263
    __ vpand(xmm6, xmm4, xmm3, Assembler::AVX_256bit);

    // 264             vpand   %ymm1, %ymm2, %ymm5
    __ vpand(xmm7, xmm6, xmm5, Assembler::AVX_256bit);

    // 265             vpand   %ymm3, %ymm4, %ymm6
    __ vpmovmskb(rcx, xmm7, Assembler::AVX_256bit);

    // 266             vpand   %ymm5, %ymm6, %ymm7
    __ incrementl(rcx);

    // 267             vpmovmskb %ymm7, %ecx
    __ jne_b(L_8x_return_vec_0_1_2_3);

    // 268             incl    %ecx
    __ subq(rdi, -128);

    // 269             jnz     L(8x_return_vec_0_1_2_3)
    // 270             subq    $-(VEC_SIZE * 4), %rdi
    __ cmpq(rdi, rdx);

    // 271             /* Check if s1 pointer at end.  */
    __ jb_b(L_loop_4x_vec);

    // 272             cmpq    %rdx, %rdi
    // 273             jb      L(loop_4x_vec)
    __ subq(rdi, rdx);

    // 274
    // 275             subq    %rdx, %rdi
    __ cmpl(rdi, 0x60);

    // 276             /* rdi has 4 * VEC_SIZE - remaining length.  */
    __ jae_b(L_8x_last_1x_vec);

    // 277             cmpl    $(VEC_SIZE * 3), %edi
    // 278             jae     L(8x_last_1x_vec)
    __ vmovdqu(xmm3, Address(rsi, rdx, Address::times_1, 0x40));

    // 279             /* Load regardless of branch.  */
    __ cmpl(rdi, 0x40);

    // 280             vmovdqu (VEC_SIZE * 2)(%rsi, %rdx), %ymm3
    __ jae_b(L_8x_last_2x_vec);

    // 281             cmpl    $(VEC_SIZE * 2), %edi
    // 282             jae     L(8x_last_2x_vec)
    // 283
    __ vmovdqu(xmm1, Address(rsi, rdx, Address::times_1));

    // 284             /* Check last 4 VEC.  */
    __ vpcmpeqb(xmm1, xmm1, Address(rdx, 0), Assembler::AVX_256bit);

    // 285             vmovdqu (%rsi, %rdx), %ymm1
    // 286             VPCMPEQ (%rdx), %ymm1, %ymm1
    __ vmovdqu(xmm2, Address(rsi, rdx, Address::times_1, 0x20));

    // 287
    __ vpcmpeqb(xmm2, xmm2, Address(rdx, 0x20), Assembler::AVX_256bit);

    // 288             vmovdqu VEC_SIZE(%rsi, %rdx), %ymm2
    // 289             VPCMPEQ VEC_SIZE(%rdx), %ymm2, %ymm2
    __ vpcmpeqb(xmm3, xmm3, Address(rdx, 0x40), Assembler::AVX_256bit);

    // 290
    // 291             VPCMPEQ (VEC_SIZE * 2)(%rdx), %ymm3, %ymm3
    __ vmovdqu(xmm4, Address(rsi, rdx, Address::times_1, 0x60));

    // 292
    __ vpcmpeqb(xmm4, xmm4, Address(rdx, 0x60), Assembler::AVX_256bit);

    // 293             vmovdqu (VEC_SIZE * 3)(%rsi, %rdx), %ymm4
    // 294             VPCMPEQ (VEC_SIZE * 3)(%rdx), %ymm4, %ymm4
    __ vpand(xmm5, xmm2, xmm1, Assembler::AVX_256bit);

    // 295
    __ vpand(xmm6, xmm4, xmm3, Assembler::AVX_256bit);

    // 296             vpand   %ymm1, %ymm2, %ymm5
    __ vpand(xmm7, xmm6, xmm5, Assembler::AVX_256bit);

    // 297             vpand   %ymm3, %ymm4, %ymm6
    __ vpmovmskb(rcx, xmm7, Assembler::AVX_256bit);

    // 298             vpand   %ymm5, %ymm6, %ymm7
    // 299             vpmovmskb %ymm7, %ecx
    __ movq(rdi, rdx);

    // 300             /* Restore s1 pointer to rdi.  */
    __ incrementl(rcx);

    // 301             movq    %rdx, %rdi
    __ jne(L_8x_return_vec_0_1_2_3);

    // 302             incl    %ecx
    // 303             jnz     L(8x_return_vec_0_1_2_3)
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_8x_last_2x_vec);

    // 304             /* NB: eax must be zero to reach here.  */
    // 305             VZEROUPPER_RETURN
    // 306
    // 307             /* Only entry is from L(more_8x_vec).  */
    // 308             .p2align 4
    // 309     L(8x_last_2x_vec):
    // 310             /* Check second to last VEC. rdx store end pointer of s1
    // and 311                ymm3 has already been loaded with second to last
    // VEC from s2.
    __ vpcmpeqb(xmm3, xmm3, Address(rdx, 0x40), Assembler::AVX_256bit);

    // 312              */
    __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);

    // 313             VPCMPEQ (VEC_SIZE * 2)(%rdx), %ymm3, %ymm3
    __ incrementl(rax);

    // 314             vpmovmskb %ymm3, %eax
    __ jne_b(L_8x_return_vec_2);
    __ align(16);

    __ bind(L_8x_last_1x_vec);

    // 315             incl    %eax
    // 316             jnz     L(8x_return_vec_2)
    // 317             /* Check last VEC.  */
    // 318             .p2align 4
    __ vmovdqu(xmm4, Address(rsi, rdx, Address::times_1, 0x60));

    // 319     L(8x_last_1x_vec):
    __ vpcmpeqb(xmm4, xmm4, Address(rdx, 0x60), Assembler::AVX_256bit);

    // 320             vmovdqu (VEC_SIZE * 3)(%rsi, %rdx), %ymm4
    __ vpmovmskb(rax, xmm4, Assembler::AVX_256bit);

    // 321             VPCMPEQ (VEC_SIZE * 3)(%rdx), %ymm4, %ymm4
    __ incrementl(rax);

    // 322             vpmovmskb %ymm4, %eax
    __ jne_b(L_8x_return_vec_3);

    // 323             incl    %eax
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_last_2x_vec);

    // 324             jnz     L(8x_return_vec_3)
    // 325             VZEROUPPER_RETURN
    // 326
    // 327             .p2align 4
    // 328     L(last_2x_vec):
    __ vmovdqu(xmm1, Address(rsi, rdx, Address::times_1, -0x40));

    // 329             /* Check second to last VEC.  */
    __ vpcmpeqb(xmm1, xmm1, Address(rdi, rdx, Address::times_1, -0x40),
                Assembler::AVX_256bit);

    // 330             vmovdqu -(VEC_SIZE * 2)(%rsi, %rdx), %ymm1
    __ vpmovmskb(rax, xmm1, Assembler::AVX_256bit);

    // 331             VPCMPEQ -(VEC_SIZE * 2)(%rdi, %rdx), %ymm1, %ymm1
    __ incrementl(rax);

    // 332             vpmovmskb %ymm1, %eax
    __ jne_b(L_return_vec_1_end);

    __ bind(L_last_1x_vec);

    // 333             incl    %eax
    // 334             jnz     L(return_vec_1_end)
    // 335             /* Check last VEC.  */
    __ vmovdqu(xmm1, Address(rsi, rdx, Address::times_1, -0x20));

    // 336     L(last_1x_vec):
    __ vpcmpeqb(xmm1, xmm1, Address(rdi, rdx, Address::times_1, -0x20),
                Assembler::AVX_256bit);

    // 337             vmovdqu -(VEC_SIZE * 1)(%rsi, %rdx), %ymm1
    __ vpmovmskb(rax, xmm1, Assembler::AVX_256bit);

    // 338             VPCMPEQ -(VEC_SIZE * 1)(%rdi, %rdx), %ymm1, %ymm1
    __ incrementl(rax);

    // 339             vpmovmskb %ymm1, %eax
    __ jne_b(L_return_vec_0_end);

    // 340             incl    %eax
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_8x_return_vec_2);

    // 341             jnz     L(return_vec_0_end)
    // 342             VZEROUPPER_RETURN
    // 343
    // 344             .p2align 4
    __ subq(rdx, 0x20);
    __ bind(L_8x_return_vec_3);

    // 345     L(8x_return_vec_2):
    // 346             subq    $VEC_SIZE, %rdx
    __ tzcntl(rax, rax);

    // 347     L(8x_return_vec_3):
    __ addq(rax, rdx);

    // 348             tzcntl  %eax, %eax
    // 349             addq    %rdx, %rax
    // 350     # ifdef USE_AS_WMEMCMP
    // 351             movl    (VEC_SIZE * 3)(%rax), %ecx
    // 352             xorl    %edx, %edx
    // 353             cmpl    (VEC_SIZE * 3)(%rsi, %rax), %ecx
    // 354             setg    %dl
    // 355             leal    -1(%rdx, %rdx), %eax
    __ movzbl(rcx, Address(rsi, rax, Address::times_1, 0x60));

    // 356     # else
    __ movzbl(rax, Address(rax, 0x60));

    // 357             movzbl  (VEC_SIZE * 3)(%rsi, %rax), %ecx
    __ subl(rax, rcx);

    // 358             movzbl  (VEC_SIZE * 3)(%rax), %eax
    // 359             subl    %ecx, %eax
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_return_vec_1_end);

    // 360     # endif
    // 361             VZEROUPPER_RETURN
    // 362
    // 363             .p2align 4
    __ tzcntl(rax, rax);

    // 364     L(return_vec_1_end):
    __ addl(rax, rdx);

    // 365             tzcntl  %eax, %eax
    // 366             addl    %edx, %eax
    // 367     # ifdef USE_AS_WMEMCMP
    // 368             movl    -(VEC_SIZE * 2)(%rdi, %rax), %ecx
    // 369             xorl    %edx, %edx
    // 370             cmpl    -(VEC_SIZE * 2)(%rsi, %rax), %ecx
    // 371             setg    %dl
    // 372             leal    -1(%rdx, %rdx), %eax
    __ movzbl(rcx, Address(rsi, rax, Address::times_1, -0x40));

    // 373     # else
    __ movzbl(rax, Address(rdi, rax, Address::times_1, -0x40));

    // 374             movzbl  -(VEC_SIZE * 2)(%rsi, %rax), %ecx
    __ subl(rax, rcx);

    // 375             movzbl  -(VEC_SIZE * 2)(%rdi, %rax), %eax
    // 376             subl    %ecx, %eax
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_return_vec_0_end);

    // 377     # endif
    // 378             VZEROUPPER_RETURN
    // 379
    // 380             .p2align 4
    __ tzcntl(rax, rax);

    // 381     L(return_vec_0_end):
    __ addl(rax, rdx);

    // 382             tzcntl  %eax, %eax
    // 383             addl    %edx, %eax
    // 384     # ifdef USE_AS_WMEMCMP
    // 385             movl    -VEC_SIZE(%rdi, %rax), %ecx
    // 386             xorl    %edx, %edx
    // 387             cmpl    -VEC_SIZE(%rsi, %rax), %ecx
    // 388             setg    %dl
    // 389             leal    -1(%rdx, %rdx), %eax
    __ movzbl(rcx, Address(rsi, rax, Address::times_1, -0x20));

    // 390     # else
    __ movzbl(rax, Address(rdi, rax, Address::times_1, -0x20));

    // 391             movzbl  -VEC_SIZE(%rsi, %rax), %ecx
    __ subl(rax, rcx);

    // 392             movzbl  -VEC_SIZE(%rdi, %rax), %eax
    // 393             subl    %ecx, %eax
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_less_vec);

    // 394     # endif
    // 395             VZEROUPPER_RETURN
    // 396
    // 397             .p2align 4
    // 398     L(less_vec):
    // 399             /* Check if one or less CHAR. This is necessary for size
    // = 0 but
    __ cmpl(rdx, 0x1);

    // 400                is also faster for size = CHAR_SIZE.  */
    __ jbe_b(L_one_or_less);

    // 401             cmpl    $CHAR_SIZE, %edx
    // 402             jbe     L(one_or_less)
    // 403
    // 404             /* Check if loading one VEC from either s1 or s2 could
    // cause a 405                page cross. This can have false positives but
    // is by far the
    __ movl(rax, rdi);

    // 406                fastest method.  */
    __ orl(rax, rsi);

    // 407             movl    %edi, %eax
    __ andl(rax, 0xfff);

    // 408             orl     %esi, %eax
    __ cmpl(rax, 0xfe0);

    // 409             andl    $(PAGE_SIZE - 1), %eax
    __ jg_b(L_page_cross_less_vec);

    // 410             cmpl    $(PAGE_SIZE - VEC_SIZE), %eax
    // 411             jg      L(page_cross_less_vec)
    // 412
    __ vmovdqu(xmm2, Address(rsi, 0));

    // 413             /* No page cross possible.  */
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0), Assembler::AVX_256bit);

    // 414             vmovdqu (%rsi), %ymm2
    __ vpmovmskb(rax, xmm2, Assembler::AVX_256bit);

    // 415             VPCMPEQ (%rdi), %ymm2, %ymm2
    __ incrementl(rax);

    // 416             vpmovmskb %ymm2, %eax
    // 417             incl    %eax
    // 418             /* Result will be zero if s1 and s2 match. Otherwise
    // first set
    __ bzhil(rdx, rax, rdx);

    // 419                bit will be first mismatch.  */
    __ jne(L_return_vec_0);

    // 420             bzhil   %edx, %eax, %edx
    __ xorl(rax, rax);

    // 421             jnz     L(return_vec_0)
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_page_cross_less_vec);

    // 422             xorl    %eax, %eax
    // 423             VZEROUPPER_RETURN
    // 424
    // 425             .p2align 4
    // 426     L(page_cross_less_vec):
    // 427             /* if USE_AS_WMEMCMP it can only be 0, 4, 8, 12, 16, 20,
    // 24, 28
    __ cmpl(rdx, 0x10);

    // 428                bytes.  */
    __ jae(L_between_16_31);

    // 429             cmpl    $16, %edx
    // 430             jae     L(between_16_31)
    __ cmpl(rdx, 0x8);

    // 431     # ifndef USE_AS_WMEMCMP
    __ jae_b(L_between_8_15);

    // 432             cmpl    $8, %edx
    __ cmpl(rdx, 0x4);

    // 433             jae     L(between_8_15)
    __ jae(L_between_2_3);

    // 434             /* Fall through for [4, 7].  */
    // 435             cmpl    $4, %edx
    // 436             jb      L(between_2_3)
    __ movzbl(rax, Address(rdi, 0));

    // 437
    __ movzbl(rcx, Address(rsi, 0));

    // 438             movbe   (%rdi), %eax
    // 439             movbe   (%rsi), %ecx

    __ shlq(rax, 0x20);
    // 	shlq	$32, %rcx
    __ shlq(rcx, 0x20);
    // 	movbe	-4(%rdi, %rdx), %edi
    __ movzbl(rdi, Address(rdi, rdx, Address::times_1, -0x4));
    // 	movbe	-4(%rsi, %rdx), %esi
    __ movzbl(rsi, Address(rsi, rdx, Address::times_1, -0x4));
    // 	orq	%rdi, %rax
    __ orq(rax, rdi);
    // 	orq	%rsi, %rcx
    __ orq(rcx, rsi);
    // 	subq	%rcx, %rax
    __ subq(rax, rcx);
    // 	/* Fast path for return zero.  */
    // 	jnz	L(ret_nonzero)
    __ jne_b(L_ret_nonzero);
    // 	/* No ymm register was touched.  */
    // 	ret
    __ ret(0);
    __ align(16);

    __ bind(L_one_or_less);

    // 	.p2align 4
    // L(one_or_less):
    // 	jb	L(zero)
    __ jb_b(L_zero);
    // 	movzbl	(%rsi), %ecx
    __ movzbl(rcx, Address(rsi, 0));
    // 	movzbl	(%rdi), %eax
    __ movzbl(rax, Address(rdi, 0));
    // 	subl	%ecx, %eax
    __ subl(rax, rcx);
    // 	/* No ymm register was touched.  */
    // 	ret
    __ ret(0);
    __ p2align(16, 5);

    __ bind(L_ret_nonzero);

    // 	.p2align 4,, 5
    // L(ret_nonzero):
    // 	sbbl	%eax, %eax
    __ sbbl(rax, rax);
    // 	orl	$1, %eax
    __ orl(rax, 0x1);
    // 	/* No ymm register was touched.  */
    // 	ret
    __ ret(0);
    __ p2align(16, 2);

    __ bind(L_zero);

    // 	.p2align 4,, 2
    // L(zero):
    // 	xorl	%eax, %eax
    __ xorl(rax, rax);
    // 	/* No ymm register was touched.  */
    // 	ret
    __ ret(0);
    __ align(16);

    __ bind(L_between_8_15);

    // 	.p2align 4
    // L(between_8_15):
    // 	movbe	(%rdi), %rax
    __ movzbl(rax, Address(rdi, 0));
    // 	movbe	(%rsi), %rcx
    __ movzbl(rcx, Address(rsi, 0));
    // 	subq	%rcx, %rax
    __ subq(rax, rcx);
    // 	jnz	L(ret_nonzero)
    __ jne_b(L_ret_nonzero);
    // 	movbe	-8(%rdi, %rdx), %rax
    __ movzbl(rax, Address(rdi, rdx, Address::times_1, -0x8));
    // 	movbe	-8(%rsi, %rdx), %rcx
    __ movzbl(rcx, Address(rsi, rdx, Address::times_1, -0x8));
    // 	subq	%rcx, %rax
    __ subq(rax, rcx);
    // 	/* Fast path for return zero.  */
    // 	jnz	L(ret_nonzero)
    __ jne_b(L_ret_nonzero);
    // 	/* No ymm register was touched.  */
    // 	ret
    // # endif
    __ ret(0);
    __ p2align(16, 10);

    __ bind(L_between_16_31);

    // 	.p2align 4,, 10
    // L(between_16_31):
    // 	/* From 16 to 31 bytes.  No branch when size == 16.  */
    // 	vmovdqu	(%rsi), %xmm2
    __ movdqu(xmm2, Address(rsi, 0));
    // 	VPCMPEQ	(%rdi), %xmm2, %xmm2
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0), Assembler::AVX_128bit);
    // 	vpmovmskb %xmm2, %eax
    __ vpmovmskb(rax, xmm2, Assembler::AVX_128bit);
    // 	subl	$0xffff, %eax
    __ subl(rax, 0xffff);
    // 	jnz	L(return_vec_0)
    __ jne(L_return_vec_0);

    // 	/* Use overlapping loads to avoid branches.  */

    // 	vmovdqu	-16(%rsi, %rdx), %xmm2
    __ movdqu(xmm2, Address(rsi, rdx, Address::times_1, -0x10));
    // 	leaq	-16(%rdi, %rdx), %rdi
    __ leaq(rdi, Address(rdi, rdx, Address::times_1, -0x10));
    // 	leaq	-16(%rsi, %rdx), %rsi
    __ leaq(rsi, Address(rsi, rdx, Address::times_1, -0x10));
    // 	VPCMPEQ	(%rdi), %xmm2, %xmm2
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0), Assembler::AVX_128bit);
    // 	vpmovmskb %xmm2, %eax
    __ vpmovmskb(rax, xmm2, Assembler::AVX_128bit);
    // 	subl	$0xffff, %eax
    __ subl(rax, 0xffff);
    // 	/* Fast path for return zero.  */
    // 	jnz	L(return_vec_0)
    __ jne(L_return_vec_0);
    // 	/* No ymm register was touched.  */
    // 	ret
    // # else
    __ ret(0);
    __ align(16);

    __ bind(L_between_2_3);

    // 	.p2align 4
    // L(between_2_3):
    // 	/* Load as big endian to avoid branches.  */
    // 	movzwl	(%rdi), %eax
    __ movzwl(rax, Address(rdi, 0));
    // 	movzwl	(%rsi), %ecx
    __ movzwl(rcx, Address(rsi, 0));
    // 	bswap	%eax
    __ bswapl(rax);
    // 	bswap	%ecx
    __ bswapl(rcx);
    // 	shrl	%eax
    __ shrl(rax, 1);
    // 	shrl	%ecx
    __ shrl(rcx, 1);
    // 	movzbl	-1(%rdi, %rdx), %edi
    __ movzbl(rdi, Address(rdi, rdx, Address::times_1, -0x1));
    // 	movzbl	-1(%rsi, %rdx), %esi
    __ movzbl(rsi, Address(rsi, rdx, Address::times_1, -0x1));
    // 	orl	%edi, %eax
    __ orl(rax, rdi);
    // 	orl	%esi, %ecx
    __ orl(rcx, rsi);
    // 	/* Subtraction is okay because the upper bit is zero.  */
    // 	subl	%ecx, %eax
    __ subl(rax, rcx);
    // 	/* No ymm register was touched.  */
    // 	ret
    __ ret(0);

  } else {  // SSE version
    assert(false, "Only supports AVX2");
  }

  return start;
}

#undef __
