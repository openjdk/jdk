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

#include "precompiled.hpp"
#include "macroAssembler_x86.hpp"
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
//  __ addl(r15, 33 - size);
  __ leal(rcx, Address(r15, 33-size));
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
  address jmp_table[32];
  address jmp_table_1[32];
  int jmp_ndx = 0;
  __ align(CodeEntryAlignment);
  address start = __ pc();
  __ enter(); // required for proper stackwalking of RuntimeStub frame

////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
//                         AVX2 code
////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
  if (VM_Version::supports_avx2()) {       // AVX2 version
    Label strchr_avx2, memcmp_avx2;

    Label L_begin, L_0x406044, L_CASE_0, L_0x406019;
    Label L_trampoline, L_0x404f1f, L_0x404912;
    Label L_exit, L_long_compare, L_top_loop_1, L_0x4049cc, L_error;
    Label L_small_string, L_0x405cee, L_0x405f5d, L_0x406008;
    Label L_0x405fff, L_0x406002, L_0x404f8c, L_0x404f73, L_0x4060a3;
    Label L_0x404fbe, L_0x40602e, L_0x40607f, L_0x405018;
    Label L_0x40605e, L_0x406093,L_0x40559d, L_0x404933, L_byte_copy;
    Label L_set_s, L_small_string2;

    address jump_table;
    address jump_table_1;

    __ jmp(L_begin);

////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
  //  0x00000000004048f0 <+0>:     push   rbp
  //  0x00000000004048f1 <+1>:     push   r15
  //  0x00000000004048f3 <+3>:     push   r14
  //  0x00000000004048f5 <+5>:     push   r13
  //  0x00000000004048f7 <+7>:     push   r12
  //  0x00000000004048f9 <+9>:     push   rbx
  //  0x00000000004048fa <+10>:    sub    rsp,0x78
  //  0x00000000004048fe <+14>:    mov    rbx,rsi   // n
  //  0x0000000000404901 <+17>:    sub    rbx,rcx   // n - k
  //  0x0000000000404904 <+20>:    jae    0x404912 <_Z14avx2_strstr_v2PKcmS0_m+34>
  //  0x0000000000404906 <+22>:    mov    r15,0xffffffffffffffff
  //  0x000000000040490d <+29>:    jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  //  0x0000000000404912 <+34>:    mov    r12,rcx   // k
  //  0x0000000000404915 <+37>:    test   rcx,rcx
  //  0x0000000000404918 <+40>:    je     0x40496c <_Z14avx2_strstr_v2PKcmS0_m+124>
  //  0x000000000040491a <+42>:    mov    r10,rdx   // needle
  //  0x000000000040491d <+45>:    mov    r15,rsi   // n
  //  0x0000000000404920 <+48>:    mov    r11,rdi   // s
  // if ((n < 32) || ((long long)n < 32 + (long long)k - 1))
  //  0x0000000000404923 <+51>:    cmp    rsi,0x20   // n <=> 32
  //  0x0000000000404927 <+55>:    jb     0x404974 <_Z14avx2_strstr_v2PKcmS0_m+132>
  //  0x0000000000404929 <+57>:    lea    rax,[r12+0x1f]   // k + 31
  //  0x000000000040492e <+62>:    cmp    rax,r15   // (k + 31) <=> n
  //  0x0000000000404931 <+65>:    jg     0x404974 <_Z14avx2_strstr_v2PKcmS0_m+132>
  //  0x0000000000404933 <+67>:    lea    rax,[r12-0x1]   // k - 1
  //  0x0000000000404938 <+72>:    cmp    rax,0x1e   // (k - 1) <=> 30
  //  0x000000000040493c <+76>:    ja     0x404f1f <_Z14avx2_strstr_v2PKcmS0_m+1583>
  //  0x0000000000404942 <+82>:    jmp    QWORD PTR [rax*8+0x415100]
  // case statement for AVX2 instructions:
  //    r15, rsi <= n
  //    rdi, r11 <= s
  //    r10, rdx <= needle
  //    r12, rcx <= k
  //    rbx <= n - k
  //    rax <= k - 1

  //  case 1: 0x0000000000404949 <+89>:    vpbroadcastb ymm0,BYTE PTR [r10]     // case 1 goes in jmp_table[0]
  //  0x000000000040494e <+94>:    vpcmpeqb ymm1,ymm0,YMMWORD PTR [r11]
  //  0x0000000000404953 <+99>:    vpmovmskb eax,ymm1
  //  0x0000000000404957 <+103>:   test   eax,eax
  //  0x0000000000404959 <+105>:   je     0x406044 <_Z14avx2_strstr_v2PKcmS0_m+5972>
  //  0x000000000040495f <+111>:   xor    r13d,r13d
  //  0x0000000000404962 <+114>:   tzcnt  r13d,eax
  //  0x0000000000404967 <+119>:   jmp    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>

  jmp_table[0] = __ pc();  // Case for needle size == 1
  __ vpbroadcastb(xmm0, Address(r10, 0), Assembler::AVX_256bit);
  __ vpcmpeqb(xmm1, xmm0, Address(r11, 0), Assembler::AVX_256bit);
  __ vpmovmskb(rax, xmm1);
  __ testl(rax, rax);
  __ je(L_0x406044);
  __ tzcntl(r13, rax);
  __ jmp(L_exit);
  //  ** CASE 0: 0x000000000040496c <+124>:   xor    r15d,r15d
  //  0x000000000040496f <+127>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>

  __ bind(L_CASE_0);
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
  __ leaq(r12, Address(rsp, 0x80));   // tmp_string

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

//    0x0000000000404974 <+132>:   inc    r15
//    0x0000000000404977 <+135>:   sub    r15,r12
//    0x000000000040497a <+138>:   je     0x404906 <_Z14avx2_strstr_v2PKcmS0_m+22>
//    0x000000000040497c <+140>:   movzx  ebp,BYTE PTR [r10]
//    0x0000000000404980 <+144>:   lea    rcx,[r10+0x1]
//    0x0000000000404984 <+148>:   lea    rdx,[r12-0x2]
//    0x0000000000404989 <+153>:   cmp    r15,0x2
//    0x000000000040498d <+157>:   mov    r13d,0x1
//    0x0000000000404993 <+163>:   cmovae r13,r15
//    0x0000000000404997 <+167>:   lea    rbx,[r11+0x1d]
//    0x000000000040499b <+171>:   lea    r14,[r12+r11*1]
//    0x000000000040499f <+175>:   dec    r14
//    0x00000000004049a2 <+178>:   inc    r11
//    0x00000000004049a5 <+181>:   xor    r15d,r15d
//    0x00000000004049a8 <+184>:   jmp    0x4049cc <_Z14avx2_strstr_v2PKcmS0_m+220>
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

  //  CASE 8 CASE 9: 0x00000000004049aa <+186>:   mov    rax,QWORD PTR [rbx+r15*1-0x1c]
  //  0x00000000004049af <+191>:   cmp    rax,QWORD PTR [rcx]
  //  0x00000000004049b2 <+194>:   je     0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  //  0x00000000004049b8 <+200>:   nop    DWORD PTR [rax+rax*1+0x0]
  jmp_table_1[8] = __ pc();
  jmp_table_1[9] = __ pc();
  __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
  __ cmpq(rax, Address(rcx, 0));
  __ je(L_0x406019);
  __ align(8);
  //   for (size_t i = 0; i < n - k + 1; i++) {
  //  0x00000000004049c0 <+208>:   inc    r15
  //  0x00000000004049c3 <+211>:   cmp    r13,r15
  //  0x00000000004049c6 <+214>:   je     0x404906 <_Z14avx2_strstr_v2PKcmS0_m+22>
  //     if (s[i] == needle[0] && s[i + k - 1] == needle[k - 1]) {
  //  0x00000000004049cc <+220>:   cmp    BYTE PTR [rbx+r15*1-0x1d],bpl
  //  0x00000000004049d1 <+225>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x00000000004049d3 <+227>:   movzx  eax,BYTE PTR [r14+r15*1]
  //  0x00000000004049d8 <+232>:   cmp    al,BYTE PTR [r10+r12*1-0x1]
  //  0x00000000004049dd <+237>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
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

  //  0x00000000004049df <+239>:   lea    rax,[r12-0x1]
  //  0x00000000004049e4 <+244>:   cmp    rax,0x1e
  //  0x00000000004049e8 <+248>:   ja     0x4049ff <_Z14avx2_strstr_v2PKcmS0_m+271>
  //  0x00000000004049ea <+250>:   jmp    QWORD PTR [rax*8+0x4151f8]
  __ leaq(rax, Address(r12, -0x1));
  __ cmpq(rax, 0x1e);
  __ ja_b(L_long_compare);
  __ jmp(L_trampoline);

  //  CASE 4: CASE 5: 0x00000000004049f1 <+257>:   mov    eax,DWORD PTR [rbx+r15*1-0x1c]
  //  0x00000000004049f6 <+262>:   cmp    eax,DWORD PTR [rcx]
  //  0x00000000004049f8 <+264>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x00000000004049fa <+266>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[4] = __ pc();
  jmp_table_1[5] = __ pc();
  __ movl(rax, Address(rbx, r15, Address::times_1, -0x1c));
  __ cmpl(rax, Address(rcx, 0));
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);

  //  0x00000000004049ff <+271>:   lea    rdi,[r11+r15*1]
  //  0x0000000000404a03 <+275>:   mov    QWORD PTR [rsp+0x10],rcx
  //  0x0000000000404a08 <+280>:   mov    rsi,QWORD PTR [rsp+0x10]
  //  0x0000000000404a0d <+285>:   mov    QWORD PTR [rsp+0x8],rdx
  //  0x0000000000404a12 <+290>:   mov    rdx,QWORD PTR [rsp+0x8]
  //  0x0000000000404a17 <+295>:   mov    QWORD PTR [rsp+0x18],r11
  //  0x0000000000404a1c <+300>:   mov    QWORD PTR [rsp+0x30],r10
  //  0x0000000000404a21 <+305>:   call   0x402130 <bcmp@plt>
  //  0x0000000000404a26 <+310>:   mov    rdx,QWORD PTR [rsp+0x8]
  //  0x0000000000404a2b <+315>:   mov    rcx,QWORD PTR [rsp+0x10]
  //  0x0000000000404a30 <+320>:   mov    r10,QWORD PTR [rsp+0x30]
  //  0x0000000000404a35 <+325>:   mov    r11,QWORD PTR [rsp+0x18]
  //  0x0000000000404a3a <+330>:   test   eax,eax
  //  0x0000000000404a3c <+332>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404a3e <+334>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
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

  //  CASE 2: 0x0000000000404a43 <+339>:   movzx  eax,BYTE PTR [rbx+r15*1-0x1c]    // goes into jmp_table_1[2]
  //  0x0000000000404a49 <+345>:   cmp    al,BYTE PTR [rcx]
  //  0x0000000000404a4b <+347>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404a51 <+353>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[2] = __ pc();
  __ movzbl(rax, Address(rbx, r15, Address::times_1, -0x1c));
  __ cmpb(rax, Address(rcx, 0));
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 3: 0x0000000000404a56 <+358>:   movzx  eax,WORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404a5c <+364>:   cmp    ax,WORD PTR [rcx]
  //  0x0000000000404a5f <+367>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404a65 <+373>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[3] = __ pc();
  __ movzwl(rax, Address(rbx, r15, Address::times_1, -0x1c));
  __ cmpw(Address(rcx, 0), rax);
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 6: 0x0000000000404a6a <+378>:   mov    eax,DWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404a6f <+383>:   cmp    eax,DWORD PTR [rcx]
  //  0x0000000000404a71 <+385>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404a77 <+391>:   movzx  eax,BYTE PTR [rbx+r15*1-0x18]
  //  0x0000000000404a7d <+397>:   cmp    al,BYTE PTR [r10+0x5]
  //  0x0000000000404a81 <+401>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404a87 <+407>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[6] = __ pc();
  __ movl(rax, Address(rbx, r15, Address::times_1, -0x1c));
  __ cmpl(rax, Address(rcx, 0));
  __ jne(L_top_loop_1);
  __ movzbl(rax, Address(rbx, r15, Address::times_1, -0x18));
  __ cmpb(rax, Address(r10, 0x5));
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 7: 0x0000000000404a8c <+412>:   mov    eax,DWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404a91 <+417>:   cmp    eax,DWORD PTR [rcx]
  //  0x0000000000404a93 <+419>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404a99 <+425>:   movzx  eax,WORD PTR [rbx+r15*1-0x18]
  //  0x0000000000404a9f <+431>:   cmp    ax,WORD PTR [r10+0x5]
  //  0x0000000000404aa4 <+436>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404aaa <+442>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[7] = __ pc();
  __ movl(rax, Address(rbx, r15, Address::times_1, -0x1c));
  __ cmpl(rax, Address(rcx, 0));
  __ jne(L_top_loop_1);
  __ movzwl(rax, Address(rbx, r15, Address::times_1, -0x18));
  __ cmpw(Address(r10, 0x5), rax);
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 10: 0x0000000000404aaf <+447>:   mov    rax,QWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404ab4 <+452>:   cmp    rax,QWORD PTR [r10+0x1]
  //  0x0000000000404ab8 <+456>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404abe <+462>:   movzx  eax,BYTE PTR [r10+0x9]
  //  0x0000000000404ac3 <+467>:   cmp    BYTE PTR [rbx+r15*1-0x14],al
  //  0x0000000000404ac8 <+472>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404ace <+478>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[10] = __ pc();
  __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
  __ cmpq(rax, Address(r10, 0x1));
  __ jne(L_top_loop_1);
  __ movzbl(rax, Address(r10, 0x9));
  __ cmpb(Address(rbx, r15, Address::times_1, -0x14), rax);
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 11: 0x0000000000404ad3 <+483>:   mov    rax,QWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404ad8 <+488>:   cmp    rax,QWORD PTR [r10+0x1]
  //  0x0000000000404adc <+492>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404ae2 <+498>:   movzx  eax,WORD PTR [r10+0x9]
  //  0x0000000000404ae7 <+503>:   cmp    WORD PTR [rbx+r15*1-0x14],ax
  //  0x0000000000404aed <+509>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404af3 <+515>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[11] = __ pc();
  __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
  __ cmpq(rax, Address(r10, 0x1));
  __ jne(L_top_loop_1);
  __ movzwl(rax, Address(r10, 0x9));
  __ cmpw(Address(rbx, r15, Address::times_1, -0x14), rax);
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 12: 0x0000000000404af8 <+520>:   mov    rax,QWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404afd <+525>:   cmp    rax,QWORD PTR [rcx]
  //  0x0000000000404b00 <+528>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404b06 <+534>:   movzx  eax,WORD PTR [rbx+r15*1-0x14]
  //  0x0000000000404b0c <+540>:   cmp    ax,WORD PTR [r10+0x9]
  //  0x0000000000404b11 <+545>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404b17 <+551>:   movzx  eax,BYTE PTR [rbx+r15*1-0x12]
  //  0x0000000000404b1d <+557>:   cmp    al,BYTE PTR [r10+0xb]
  //  0x0000000000404b21 <+561>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404b27 <+567>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[12] = __ pc();
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
  //  CASE 13: 0x0000000000404b2c <+572>:   mov    rax,QWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404b31 <+577>:   cmp    rax,QWORD PTR [r10+0x1]
  //  0x0000000000404b35 <+581>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404b3b <+587>:   mov    eax,DWORD PTR [r10+0x9]
  //  0x0000000000404b3f <+591>:   cmp    DWORD PTR [rbx+r15*1-0x14],eax
  //  0x0000000000404b44 <+596>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404b4a <+602>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[13] = __ pc();
  __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
  __ cmpq(rax, Address(r10, 0x1));
  __ jne(L_top_loop_1);
  __ movl(rax, Address(r10, 0x9));
  __ cmpl(Address(rbx, r15, Address::times_1, -0x14), rax);
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 14: 0x0000000000404b4f <+607>:   mov    rax,QWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404b54 <+612>:   cmp    rax,QWORD PTR [r10+0x1]
  //  0x0000000000404b58 <+616>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404b5e <+622>:   mov    eax,DWORD PTR [r10+0x9]
  //  0x0000000000404b62 <+626>:   cmp    DWORD PTR [rbx+r15*1-0x14],eax
  //  0x0000000000404b67 <+631>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404b6d <+637>:   movzx  eax,BYTE PTR [r10+0xd]
  //  0x0000000000404b72 <+642>:   cmp    BYTE PTR [rbx+r15*1-0x10],al
  //  0x0000000000404b77 <+647>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404b7d <+653>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[14] = __ pc();
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
  //  CASE 15: 0x0000000000404b82 <+658>:   mov    rax,QWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404b87 <+663>:   cmp    rax,QWORD PTR [r10+0x1]
  //  0x0000000000404b8b <+667>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404b91 <+673>:   mov    eax,DWORD PTR [r10+0x9]
  //  0x0000000000404b95 <+677>:   cmp    DWORD PTR [rbx+r15*1-0x14],eax
  //  0x0000000000404b9a <+682>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404ba0 <+688>:   movzx  eax,WORD PTR [r10+0xd]
  //  0x0000000000404ba5 <+693>:   cmp    WORD PTR [rbx+r15*1-0x10],ax
  //  0x0000000000404bab <+699>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404bb1 <+705>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[15] = __ pc();
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
  //  CASE 16: 0x0000000000404bb6 <+710>:   mov    rax,QWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404bbb <+715>:   cmp    rax,QWORD PTR [r10+0x1]
  //  0x0000000000404bbf <+719>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404bc5 <+725>:   mov    eax,DWORD PTR [r10+0x9]
  //  0x0000000000404bc9 <+729>:   cmp    DWORD PTR [rbx+r15*1-0x14],eax
  //  0x0000000000404bce <+734>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404bd4 <+740>:   movzx  eax,WORD PTR [r10+0xd]
  //  0x0000000000404bd9 <+745>:   cmp    WORD PTR [rbx+r15*1-0x10],ax
  //  0x0000000000404bdf <+751>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404be5 <+757>:   movzx  eax,BYTE PTR [r10+0xf]
  //  0x0000000000404bea <+762>:   cmp    BYTE PTR [rbx+r15*1-0xe],al
  //  0x0000000000404bef <+767>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404bf5 <+773>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[16] = __ pc();
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
  //  CASE 17: 0x0000000000404bfa <+778>:   mov    rax,QWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404bff <+783>:   cmp    rax,QWORD PTR [r10+0x1]
  //  0x0000000000404c03 <+787>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404c09 <+793>:   mov    rax,QWORD PTR [r10+0x9]
  //  0x0000000000404c0d <+797>:   cmp    QWORD PTR [rbx+r15*1-0x14],rax
  //  0x0000000000404c12 <+802>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404c18 <+808>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[17] = __ pc();
  __ movq(rax, Address(rbx, r15, Address::times_1, -0x1c));
  __ cmpq(rax, Address(r10, 0x1));
  __ jne(L_top_loop_1);
  __ movq(rax, Address(r10, 0x9));
  __ cmpq(Address(rbx, r15, Address::times_1, -0x14), rax);
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 18: 0x0000000000404c1d <+813>:   vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404c24 <+820>:   vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404c2a <+826>:   vptest xmm0,xmm0
  //  0x0000000000404c2f <+831>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404c35 <+837>:   movzx  eax,BYTE PTR [r10+0x11]
  //  0x0000000000404c3a <+842>:   cmp    BYTE PTR [rbx+r15*1-0xc],al
  //  0x0000000000404c3f <+847>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404c45 <+853>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[18] = __ pc();
  __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
  __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
  __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
  __ jne(L_top_loop_1);
  __ movzbl(rax, Address(r10, 0x11));
  __ cmpb(Address(rbx, r15, Address::times_1, -0xc), rax);
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 19: 0x0000000000404c4a <+858>:   vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404c51 <+865>:   vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404c57 <+871>:   vptest xmm0,xmm0
  //  0x0000000000404c5c <+876>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404c62 <+882>:   movzx  eax,WORD PTR [r10+0x11]
  //  0x0000000000404c67 <+887>:   cmp    WORD PTR [rbx+r15*1-0xc],ax
  //  0x0000000000404c6d <+893>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404c73 <+899>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[19] = __ pc();
  __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
  __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
  __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
  __ jne(L_top_loop_1);
  __ movzwl(rax, Address(r10, 0x11));
  __ cmpw(Address(rbx, r15, Address::times_1, -0xc), rax);
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 20: 0x0000000000404c78 <+904>:   vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404c7f <+911>:   vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404c85 <+917>:   vptest xmm0,xmm0
  //  0x0000000000404c8a <+922>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404c90 <+928>:   movzx  eax,WORD PTR [r10+0x11]
  //  0x0000000000404c95 <+933>:   cmp    WORD PTR [rbx+r15*1-0xc],ax
  //  0x0000000000404c9b <+939>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404ca1 <+945>:   movzx  eax,BYTE PTR [r10+0x13]
  //  0x0000000000404ca6 <+950>:   cmp    BYTE PTR [rbx+r15*1-0xa],al
  //  0x0000000000404cab <+955>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404cb1 <+961>:   jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[20] = __ pc();
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
  //  CASE 21: 0x0000000000404cb6 <+966>:   vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404cbd <+973>:   vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404cc3 <+979>:   vptest xmm0,xmm0
  //  0x0000000000404cc8 <+984>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404cce <+990>:   mov    eax,DWORD PTR [r10+0x11]
  //  0x0000000000404cd2 <+994>:   cmp    DWORD PTR [rbx+r15*1-0xc],eax
  //  0x0000000000404cd7 <+999>:   jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404cdd <+1005>:  jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[21] = __ pc();
  __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
  __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
  __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
  __ jne(L_top_loop_1);
  __ movl(rax, Address(r10, 0x11));
  __ cmpl(Address(rbx, r15, Address::times_1, -0xc), rax);
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 22: 0x0000000000404ce2 <+1010>:  vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404ce9 <+1017>:  vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404cef <+1023>:  vptest xmm0,xmm0
  //  0x0000000000404cf4 <+1028>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404cfa <+1034>:  mov    eax,DWORD PTR [r10+0x11]
  //  0x0000000000404cfe <+1038>:  cmp    DWORD PTR [rbx+r15*1-0xc],eax
  //  0x0000000000404d03 <+1043>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404d09 <+1049>:  movzx  eax,BYTE PTR [r10+0x15]
  //  0x0000000000404d0e <+1054>:  cmp    BYTE PTR [rbx+r15*1-0x8],al
  //  0x0000000000404d13 <+1059>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404d19 <+1065>:  jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[22] = __ pc();
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
  //  CASE 23: 0x0000000000404d1e <+1070>:  vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404d25 <+1077>:  vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404d2b <+1083>:  vptest xmm0,xmm0
  //  0x0000000000404d30 <+1088>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404d36 <+1094>:  mov    eax,DWORD PTR [r10+0x11]
  //  0x0000000000404d3a <+1098>:  cmp    DWORD PTR [rbx+r15*1-0xc],eax
  //  0x0000000000404d3f <+1103>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404d45 <+1109>:  movzx  eax,WORD PTR [r10+0x15]
  //  0x0000000000404d4a <+1114>:  cmp    WORD PTR [rbx+r15*1-0x8],ax
  //  0x0000000000404d50 <+1120>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404d56 <+1126>:  jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[23] = __ pc();
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
  //  CASE 24: 0x0000000000404d5b <+1131>:  vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404d62 <+1138>:  vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404d68 <+1144>:  vptest xmm0,xmm0
  //  0x0000000000404d6d <+1149>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404d73 <+1155>:  mov    eax,DWORD PTR [r10+0x11]
  //  0x0000000000404d77 <+1159>:  cmp    DWORD PTR [rbx+r15*1-0xc],eax
  //  0x0000000000404d7c <+1164>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404d82 <+1170>:  movzx  eax,WORD PTR [r10+0x15]
  //  0x0000000000404d87 <+1175>:  cmp    WORD PTR [rbx+r15*1-0x8],ax
  //  0x0000000000404d8d <+1181>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404d93 <+1187>:  movzx  eax,BYTE PTR [r10+0x17]
  //  0x0000000000404d98 <+1192>:  cmp    BYTE PTR [rbx+r15*1-0x6],al
  //  0x0000000000404d9d <+1197>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404da3 <+1203>:  jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[24] = __ pc();
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
  //  CASE 25: 0x0000000000404da8 <+1208>:  vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404daf <+1215>:  vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404db5 <+1221>:  vptest xmm0,xmm0
  //  0x0000000000404dba <+1226>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404dc0 <+1232>:  mov    rax,QWORD PTR [r10+0x11]
  //  0x0000000000404dc4 <+1236>:  cmp    QWORD PTR [rbx+r15*1-0xc],rax
  //  0x0000000000404dc9 <+1241>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404dcf <+1247>:  jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[25] = __ pc();
  __ movdqu(xmm0, Address(rbx, r15, Address::times_1, -0x1c));
  __ vpsubb(xmm0, xmm0, Address(r10, 0x1), Assembler::AVX_128bit);
  __ vptest(xmm0, xmm0, Assembler::AVX_128bit);
  __ jne(L_top_loop_1);
  __ movq(rax, Address(r10, 0x11));
  __ cmpq(Address(rbx, r15, Address::times_1, -0xc), rax);
  __ jne(L_top_loop_1);
  __ jmp(L_0x406019);
  //  CASE 26: 0x0000000000404dd4 <+1252>:  vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404ddb <+1259>:  vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404de1 <+1265>:  vptest xmm0,xmm0
  //  0x0000000000404de6 <+1270>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404dec <+1276>:  mov    rax,QWORD PTR [r10+0x11]
  //  0x0000000000404df0 <+1280>:  cmp    QWORD PTR [rbx+r15*1-0xc],rax
  //  0x0000000000404df5 <+1285>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404dfb <+1291>:  movzx  eax,BYTE PTR [r10+0x19]
  //  0x0000000000404e00 <+1296>:  cmp    BYTE PTR [rbx+r15*1-0x4],al
  //  0x0000000000404e05 <+1301>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404e0b <+1307>:  jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[26] = __ pc();
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
  //  CASE 27: 0x0000000000404e10 <+1312>:  vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404e17 <+1319>:  vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404e1d <+1325>:  vptest xmm0,xmm0
  //  0x0000000000404e22 <+1330>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404e28 <+1336>:  mov    rax,QWORD PTR [r10+0x11]
  //  0x0000000000404e2c <+1340>:  cmp    QWORD PTR [rbx+r15*1-0xc],rax
  //  0x0000000000404e31 <+1345>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404e37 <+1351>:  movzx  eax,WORD PTR [r10+0x19]
  //  0x0000000000404e3c <+1356>:  cmp    WORD PTR [rbx+r15*1-0x4],ax
  //  0x0000000000404e42 <+1362>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404e48 <+1368>:  jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[27] = __ pc();
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
  //  CASE 28: 0x0000000000404e4d <+1373>:  vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404e54 <+1380>:  vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404e5a <+1386>:  vptest xmm0,xmm0
  //  0x0000000000404e5f <+1391>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404e65 <+1397>:  mov    rax,QWORD PTR [r10+0x11]
  //  0x0000000000404e69 <+1401>:  cmp    QWORD PTR [rbx+r15*1-0xc],rax
  //  0x0000000000404e6e <+1406>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404e74 <+1412>:  movzx  eax,WORD PTR [r10+0x19]
  //  0x0000000000404e79 <+1417>:  cmp    WORD PTR [rbx+r15*1-0x4],ax
  //  0x0000000000404e7f <+1423>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404e85 <+1429>:  movzx  eax,BYTE PTR [r10+0x1b]
  //  0x0000000000404e8a <+1434>:  cmp    BYTE PTR [rbx+r15*1-0x2],al
  //  0x0000000000404e8f <+1439>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404e95 <+1445>:  jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[28] = __ pc();
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
  //  CASE 29: 0x0000000000404e9a <+1450>:  vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404ea1 <+1457>:  vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404ea7 <+1463>:  vptest xmm0,xmm0
  //  0x0000000000404eac <+1468>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404eb2 <+1474>:  mov    rax,QWORD PTR [r10+0x11]
  //  0x0000000000404eb6 <+1478>:  cmp    QWORD PTR [rbx+r15*1-0xc],rax
  //  0x0000000000404ebb <+1483>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404ec1 <+1489>:  mov    eax,DWORD PTR [r10+0x19]
  //  0x0000000000404ec5 <+1493>:  cmp    DWORD PTR [rbx+r15*1-0x4],eax
  //  0x0000000000404eca <+1498>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404ed0 <+1504>:  jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[29] = __ pc();
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
  //  CASE 30: 0x0000000000404ed5 <+1509>:  vmovdqu xmm0,XMMWORD PTR [rbx+r15*1-0x1c]
  //  0x0000000000404edc <+1516>:  vpsubb xmm0,xmm0,XMMWORD PTR [r10+0x1]
  //  0x0000000000404ee2 <+1522>:  vptest xmm0,xmm0
  //  0x0000000000404ee7 <+1527>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404eed <+1533>:  mov    rax,QWORD PTR [r10+0x11]
  //  0x0000000000404ef1 <+1537>:  cmp    QWORD PTR [rbx+r15*1-0xc],rax
  //  0x0000000000404ef6 <+1542>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404efc <+1548>:  mov    eax,DWORD PTR [r10+0x19]
  //  0x0000000000404f00 <+1552>:  cmp    DWORD PTR [rbx+r15*1-0x4],eax
  //  0x0000000000404f05 <+1557>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404f0b <+1563>:  movzx  eax,BYTE PTR [r10+0x1d]
  //  0x0000000000404f10 <+1568>:  cmp    BYTE PTR [rbx+r15*1],al
  //  0x0000000000404f14 <+1572>:  jne    0x4049c0 <_Z14avx2_strstr_v2PKcmS0_m+208>
  //  0x0000000000404f1a <+1578>:  jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  jmp_table_1[30] = __ pc();
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

// anysize
  __ bind(L_0x404f1f);
//    0x0000000000404f1f <+1583>:  mov    r13,0xffffffffffffffff
//    0x0000000000404f26 <+1590>:  test   r15,r15
//    0x0000000000404f29 <+1593>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000404f2f <+1599>:  mov    QWORD PTR [rsp+0x20],rbx
//    0x0000000000404f34 <+1604>:  lea    rax,[r11+r15*1]
//    0x0000000000404f38 <+1608>:  mov    QWORD PTR [rsp+0x28],rax
//    0x0000000000404f3d <+1613>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000404f42 <+1618>:  vmovdqu YMMWORD PTR [rsp+0x30],ymm0
//    0x0000000000404f48 <+1624>:  vpbroadcastb ymm0,BYTE PTR [r12+r10*1-0x1]
//    0x0000000000404f4f <+1631>:  vmovdqu YMMWORD PTR [rsp+0x50],ymm0
//    0x0000000000404f55 <+1637>:  sub    r15d,r12d
//    0x0000000000404f58 <+1640>:  inc    r15d
//    0x0000000000404f5b <+1643>:  and    r15d,0x1f
//    0x0000000000404f5f <+1647>:  inc    r10
//    0x0000000000404f62 <+1650>:  lea    rax,[r12-0x2]
//    0x0000000000404f67 <+1655>:  mov    QWORD PTR [rsp+0x10],rax
//    0x0000000000404f6c <+1660>:  mov    QWORD PTR [rsp+0x18],r11
//    0x0000000000404f71 <+1665>:  jmp    0x404f8c <_Z14avx2_strstr_v2PKcmS0_m+1692>
  __ movq(r13, -1);
  __ testq(r15, r15);
  __ jle(L_exit);
  __ movq(Address(rsp, 0x20), rbx);
  __ leaq(rax, Address(r11, r15, Address::times_1));
  __ movq(Address(rsp, 0x28), rax);
  __ vpbroadcastb(xmm0, Address(r10, 0), Assembler::AVX_256bit);
  __ vmovdqu(Address(rsp, 0x30), xmm0);
  __ vpbroadcastb(xmm0, Address(r12, r10, Address::times_1, -0x1), Assembler::AVX_256bit);
  __ vmovdqu(Address(rsp, 0x50), xmm0);
  __ subl(r15, r12);
  __ incrementl(r15);
  __ andl(r15, 0x1f);
  __ incrementq(r10);
  __ leaq(rax, Address(r12, -0x2));
  __ movq(Address(rsp, 0x10), rax);
  __ movq(Address(rsp, 0x18), r11);
  __ jmpb(L_0x404f8c);

  __ bind(L_0x404f73);
//    0x0000000000404f73 <+1667>:  mov    r11,QWORD PTR [rsp+0x8]
//    0x0000000000404f78 <+1672>:  add    r11,r15
//    0x0000000000404f7b <+1675>:  mov    r15d,0x20
//    0x0000000000404f81 <+1681>:  cmp    r11,QWORD PTR [rsp+0x28]
//    0x0000000000404f86 <+1686>:  jae    0x4060a3 <_Z14avx2_strstr_v2PKcmS0_m+6067>
  __ movq(r11, Address(rsp, 0x8));
  __ addq(r11, r15);
  __ movl(r15, 0x20);
  __ cmpq(r11, Address(rsp, 0x28));
  __ jae(L_0x4060a3);

  __ bind(L_0x404f8c);
//    0x0000000000404f8c <+1692>:  vmovdqu ymm0,YMMWORD PTR [rsp+0x30]
//    0x0000000000404f92 <+1698>:  vpcmpeqb ymm0,ymm0,YMMWORD PTR [r11]
//    0x0000000000404f97 <+1703>:  vmovdqu ymm1,YMMWORD PTR [rsp+0x50]
//    0x0000000000404f9d <+1709>:  mov    QWORD PTR [rsp+0x8],r11
//    0x0000000000404fa2 <+1714>:  vpcmpeqb ymm1,ymm1,YMMWORD PTR [r11+r12*1-0x1]
//    0x0000000000404fa9 <+1721>:  vpand  ymm0,ymm1,ymm0
//    0x0000000000404fad <+1725>:  vpmovmskb ebx,ymm0
//    0x0000000000404fb1 <+1729>:  test   ebx,ebx
//    0x0000000000404fb3 <+1731>:  je     0x404f73 <_Z14avx2_strstr_v2PKcmS0_m+1667>
//    0x0000000000404fb5 <+1733>:  mov    rax,QWORD PTR [rsp+0x8]
//    0x0000000000404fba <+1738>:  lea    r14,[rax+0x1]
  __ vmovdqu(xmm0, Address(rsp, 0x30));
  __ vpcmpeqb(xmm0, xmm0, Address(r11, 0), Assembler::AVX_256bit);
  __ vmovdqu(xmm1, Address(rsp, 0x50));
  __ movq(Address(rsp, 0x8), r11);
  __ vpcmpeqb(xmm1, xmm1, Address(r11, r12, Address::times_1, -0x1), Assembler::AVX_256bit);
  __ vpand(xmm0, xmm1, xmm0, Assembler::AVX_256bit);
  __ vpmovmskb(rbx, xmm0);
  __ testl(rbx, rbx);
  __ je(L_0x404f73);
  __ movq(rax, Address(rsp, 0x8));
  __ leaq(r14, Address(rax, 1));

  __ bind(L_0x404fbe);
//    0x0000000000404fbe <+1742>:  xor    ebp,ebp
//    0x0000000000404fc0 <+1744>:  tzcnt  ebp,ebx
//    0x0000000000404fc4 <+1748>:  lea    rdi,[r14+rbp*1]
//    0x0000000000404fc8 <+1752>:  mov    r13,r10
//    0x0000000000404fcb <+1755>:  mov    rsi,r10
//    0x0000000000404fce <+1758>:  mov    rdx,QWORD PTR [rsp+0x10]
//    0x0000000000404fd3 <+1763>:  vzeroupper
//    0x0000000000404fd6 <+1766>:  call   0x402130 <bcmp@plt>
//    0x0000000000404fdb <+1771>:  test   eax,eax
//    0x0000000000404fdd <+1773>:  je     0x40602e <_Z14avx2_strstr_v2PKcmS0_m+5950>
//    0x0000000000404fe3 <+1779>:  blsr   ebx,ebx
//    0x0000000000404fe8 <+1784>:  mov    r10,r13
//    0x0000000000404feb <+1787>:  jne    0x404fbe <_Z14avx2_strstr_v2PKcmS0_m+1742>
//    0x0000000000404fed <+1789>:  jmp    0x404f73 <_Z14avx2_strstr_v2PKcmS0_m+1667>
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
  __ jne_b(L_0x404fbe);
  __ jmpb(L_0x404f73);

//    case 2: 0x0000000000404fef <+1791>:  mov    r13,0xffffffffffffffff
//    0x0000000000404ff6 <+1798>:  test   r15,r15
//    0x0000000000404ff9 <+1801>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000404fff <+1807>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405004 <+1812>:  vpbroadcastb ymm1,BYTE PTR [r10+0x1]
//    0x000000000040500a <+1818>:  lea    rcx,[r11+r15*1]
//    0x000000000040500e <+1822>:  dec    r15d
//    0x0000000000405011 <+1825>:  and    r15d,0x1f
//    0x0000000000405015 <+1829>:  mov    rax,r11
//    0x0000000000405018 <+1832>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rax]
//    0x000000000040501c <+1836>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rax+0x1]
//    0x0000000000405021 <+1841>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405025 <+1845>:  vpmovmskb edx,ymm2
//    0x0000000000405029 <+1849>:  test   edx,edx
//    0x000000000040502b <+1851>:  jne    0x40607f <_Z14avx2_strstr_v2PKcmS0_m+6031>
//    0x0000000000405031 <+1857>:  add    rax,r15
//    0x0000000000405034 <+1860>:  cmp    rax,rcx
//    0x0000000000405037 <+1863>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040503d <+1869>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rax]
//    0x0000000000405041 <+1873>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rax+0x1]
//    0x0000000000405046 <+1878>:  vpand  ymm2,ymm3,ymm2
//    0x000000000040504a <+1882>:  vpmovmskb edx,ymm2
//    0x000000000040504e <+1886>:  test   edx,edx
//    0x0000000000405050 <+1888>:  jne    0x40607f <_Z14avx2_strstr_v2PKcmS0_m+6031>
//    0x0000000000405056 <+1894>:  add    rax,0x20
//    0x000000000040505a <+1898>:  mov    r15d,0x20
//    0x0000000000405060 <+1904>:  cmp    rax,rcx
//    0x0000000000405063 <+1907>:  jb     0x405018 <_Z14avx2_strstr_v2PKcmS0_m+1832>
//    0x0000000000405065 <+1909>:  jmp    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
  jmp_table[1] = __ pc();
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


//    case 3: 0x000000000040506a <+1914>:  mov    r13,0xffffffffffffffff
//    0x0000000000405071 <+1921>:  test   r15,r15
//    0x0000000000405074 <+1924>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040507a <+1930>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x000000000040507f <+1935>:  vpbroadcastb ymm1,BYTE PTR [r10+0x2]
//    0x0000000000405085 <+1941>:  lea    rax,[r11+r15*1]
//    0x0000000000405089 <+1945>:  add    r15d,0x1e
//    0x000000000040508d <+1949>:  and    r15d,0x1f
//    0x0000000000405091 <+1953>:  mov    rcx,r11
//    0x0000000000405094 <+1956>:  jmp    0x4050a8 <_Z14avx2_strstr_v2PKcmS0_m+1976>
//    0x0000000000405096 <+1958>:  add    rcx,r15
//    0x0000000000405099 <+1961>:  mov    r15d,0x20
//    0x000000000040509f <+1967>:  cmp    rcx,rax
//    0x00000000004050a2 <+1970>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004050a8 <+1976>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x00000000004050ac <+1980>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x2]
//    0x00000000004050b1 <+1985>:  vpand  ymm2,ymm3,ymm2
//    0x00000000004050b5 <+1989>:  vpmovmskb edx,ymm2
//    0x00000000004050b9 <+1993>:  test   edx,edx
//    0x00000000004050bb <+1995>:  je     0x405096 <_Z14avx2_strstr_v2PKcmS0_m+1958>
//    0x00000000004050bd <+1997>:  movzx  esi,BYTE PTR [r10+0x1]
//    0x00000000004050c2 <+2002>:  xor    edi,edi
//    0x00000000004050c4 <+2004>:  tzcnt  edi,edx
//    0x00000000004050c8 <+2008>:  cmp    BYTE PTR [rcx+rdi*1+0x1],sil
//    0x00000000004050cd <+2013>:  je     0x405cee <_Z14avx2_strstr_v2PKcmS0_m+5118>
//    0x00000000004050d3 <+2019>:  blsr   edx,edx
//    0x00000000004050d8 <+2024>:  jne    0x4050c2 <_Z14avx2_strstr_v2PKcmS0_m+2002>
//    0x00000000004050da <+2026>:  jmp    0x405096 <_Z14avx2_strstr_v2PKcmS0_m+1958>
  jmp_table[2] = __ pc();
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

//    case 4: 0x00000000004050dc <+2028>:  mov    r13,0xffffffffffffffff
//    0x00000000004050e3 <+2035>:  test   r15,r15
//    0x00000000004050e6 <+2038>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004050ec <+2044>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x00000000004050f1 <+2049>:  vpbroadcastb ymm1,BYTE PTR [r10+0x3]
//    0x00000000004050f7 <+2055>:  lea    rax,[r11+r15*1]
//    0x00000000004050fb <+2059>:  add    r15d,0x1d
//    0x00000000004050ff <+2063>:  and    r15d,0x1f
//    0x0000000000405103 <+2067>:  mov    rcx,r11
//    0x0000000000405106 <+2070>:  jmp    0x40511a <_Z14avx2_strstr_v2PKcmS0_m+2090>
//    0x0000000000405108 <+2072>:  add    rcx,r15
//    0x000000000040510b <+2075>:  mov    r15d,0x20
//    0x0000000000405111 <+2081>:  cmp    rcx,rax
//    0x0000000000405114 <+2084>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040511a <+2090>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x000000000040511e <+2094>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x3]
//    0x0000000000405123 <+2099>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405127 <+2103>:  vpmovmskb edx,ymm2
//    0x000000000040512b <+2107>:  test   edx,edx
//    0x000000000040512d <+2109>:  je     0x405108 <_Z14avx2_strstr_v2PKcmS0_m+2072>
//    0x000000000040512f <+2111>:  movzx  esi,WORD PTR [r10+0x1]
//    0x0000000000405134 <+2116>:  xor    edi,edi
//    0x0000000000405136 <+2118>:  tzcnt  edi,edx
//    0x000000000040513a <+2122>:  cmp    WORD PTR [rcx+rdi*1+0x1],si
//    0x000000000040513f <+2127>:  je     0x405cee <_Z14avx2_strstr_v2PKcmS0_m+5118>
//    0x0000000000405145 <+2133>:  blsr   edx,edx
//    0x000000000040514a <+2138>:  jne    0x405134 <_Z14avx2_strstr_v2PKcmS0_m+2116>
//    0x000000000040514c <+2140>:  jmp    0x405108 <_Z14avx2_strstr_v2PKcmS0_m+2072>
  jmp_table[3] = __ pc();
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

//    case 5: 0x000000000040514e <+2142>:  mov    r13,0xffffffffffffffff
//    0x0000000000405155 <+2149>:  test   r15,r15
//    0x0000000000405158 <+2152>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040515e <+2158>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405163 <+2163>:  vpbroadcastb ymm1,BYTE PTR [r10+0x4]
//    0x0000000000405169 <+2169>:  lea    rax,[r11+r15*1]
//    0x000000000040516d <+2173>:  add    r15d,0x1c
//    0x0000000000405171 <+2177>:  and    r15d,0x1f
//    0x0000000000405175 <+2181>:  mov    rcx,r11
//    0x0000000000405178 <+2184>:  jmp    0x40518c <_Z14avx2_strstr_v2PKcmS0_m+2204>
//    0x000000000040517a <+2186>:  add    rcx,r15
//    0x000000000040517d <+2189>:  mov    r15d,0x20
//    0x0000000000405183 <+2195>:  cmp    rcx,rax
//    0x0000000000405186 <+2198>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040518c <+2204>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405190 <+2208>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x4]
//    0x0000000000405195 <+2213>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405199 <+2217>:  vpmovmskb edx,ymm2
//    0x000000000040519d <+2221>:  test   edx,edx
//    0x000000000040519f <+2223>:  je     0x40517a <_Z14avx2_strstr_v2PKcmS0_m+2186>
//    0x00000000004051a1 <+2225>:  mov    esi,DWORD PTR [r10+0x1]
//    0x00000000004051a5 <+2229>:  xor    edi,edi
//    0x00000000004051a7 <+2231>:  tzcnt  edi,edx
//    0x00000000004051ab <+2235>:  cmp    DWORD PTR [rcx+rdi*1+0x1],esi
//    0x00000000004051af <+2239>:  je     0x405cee <_Z14avx2_strstr_v2PKcmS0_m+5118>
//    0x00000000004051b5 <+2245>:  blsr   edx,edx
//    0x00000000004051ba <+2250>:  jne    0x4051a5 <_Z14avx2_strstr_v2PKcmS0_m+2229>
//    0x00000000004051bc <+2252>:  jmp    0x40517a <_Z14avx2_strstr_v2PKcmS0_m+2186>
  jmp_table[4] = __ pc();
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

//    case 6: 0x00000000004051be <+2254>:  mov    r13,0xffffffffffffffff
//    0x00000000004051c5 <+2261>:  test   r15,r15
//    0x00000000004051c8 <+2264>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004051ce <+2270>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x00000000004051d3 <+2275>:  vpbroadcastb ymm1,BYTE PTR [r10+0x5]
//    0x00000000004051d9 <+2281>:  lea    rax,[r11+r15*1]
//    0x00000000004051dd <+2285>:  add    r15d,0x1b
//    0x00000000004051e1 <+2289>:  and    r15d,0x1f
//    0x00000000004051e5 <+2293>:  mov    rcx,r11
//    0x00000000004051e8 <+2296>:  jmp    0x4051fc <_Z14avx2_strstr_v2PKcmS0_m+2316>
//    0x00000000004051ea <+2298>:  add    rcx,r15
//    0x00000000004051ed <+2301>:  mov    r15d,0x20
//    0x00000000004051f3 <+2307>:  cmp    rcx,rax
//    0x00000000004051f6 <+2310>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004051fc <+2316>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405200 <+2320>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x5]
//    0x0000000000405205 <+2325>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405209 <+2329>:  vpmovmskb edx,ymm2
//    0x000000000040520d <+2333>:  test   edx,edx
//    0x000000000040520f <+2335>:  je     0x4051ea <_Z14avx2_strstr_v2PKcmS0_m+2298>
//    0x0000000000405211 <+2337>:  mov    esi,DWORD PTR [r10+0x1]
//    0x0000000000405215 <+2341>:  xor    edi,edi
//    0x0000000000405217 <+2343>:  tzcnt  edi,edx
//    0x000000000040521b <+2347>:  cmp    DWORD PTR [rcx+rdi*1+0x1],esi
//    0x000000000040521f <+2351>:  je     0x405cee <_Z14avx2_strstr_v2PKcmS0_m+5118>
//    0x0000000000405225 <+2357>:  blsr   edx,edx
//    0x000000000040522a <+2362>:  jne    0x405215 <_Z14avx2_strstr_v2PKcmS0_m+2341>
//    0x000000000040522c <+2364>:  jmp    0x4051ea <_Z14avx2_strstr_v2PKcmS0_m+2298>
  jmp_table[5] = __ pc();
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

//    case 7: 0x000000000040522e <+2366>:  mov    r13,0xffffffffffffffff
//    0x0000000000405235 <+2373>:  test   r15,r15
//    0x0000000000405238 <+2376>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040523e <+2382>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405243 <+2387>:  vpbroadcastb ymm1,BYTE PTR [r10+0x6]
//    0x0000000000405249 <+2393>:  lea    rax,[r11+r15*1]
//    0x000000000040524d <+2397>:  add    r15d,0x1a
//    0x0000000000405251 <+2401>:  and    r15d,0x1f
//    0x0000000000405255 <+2405>:  mov    rcx,r11
//    0x0000000000405258 <+2408>:  jmp    0x40526c <_Z14avx2_strstr_v2PKcmS0_m+2428>
//    0x000000000040525a <+2410>:  add    rcx,r15
//    0x000000000040525d <+2413>:  mov    r15d,0x20
//    0x0000000000405263 <+2419>:  cmp    rcx,rax
//    0x0000000000405266 <+2422>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040526c <+2428>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405270 <+2432>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x6]
//    0x0000000000405275 <+2437>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405279 <+2441>:  vpmovmskb edx,ymm2
//    0x000000000040527d <+2445>:  test   edx,edx
//    0x000000000040527f <+2447>:  je     0x40525a <_Z14avx2_strstr_v2PKcmS0_m+2410>
//    0x0000000000405281 <+2449>:  mov    esi,DWORD PTR [r10+0x1]
//    0x0000000000405285 <+2453>:  jmp    0x40528e <_Z14avx2_strstr_v2PKcmS0_m+2462>
//    0x0000000000405287 <+2455>:  blsr   edx,edx
//    0x000000000040528c <+2460>:  je     0x40525a <_Z14avx2_strstr_v2PKcmS0_m+2410>
//    0x000000000040528e <+2462>:  xor    edi,edi
//    0x0000000000405290 <+2464>:  tzcnt  edi,edx
//    0x0000000000405294 <+2468>:  cmp    DWORD PTR [rcx+rdi*1+0x1],esi
//    0x0000000000405298 <+2472>:  jne    0x405287 <_Z14avx2_strstr_v2PKcmS0_m+2455>
//    0x000000000040529a <+2474>:  movzx  r8d,BYTE PTR [rcx+rdi*1+0x5]
//    0x00000000004052a0 <+2480>:  cmp    r8b,BYTE PTR [r10+0x5]
//    0x00000000004052a4 <+2484>:  jne    0x405287 <_Z14avx2_strstr_v2PKcmS0_m+2455>
//    0x00000000004052a6 <+2486>:  jmp    0x40559d <_Z14avx2_strstr_v2PKcmS0_m+3245>
  jmp_table[6] = __ pc();
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

//    case 8: 0x00000000004052ab <+2491>:  mov    r13,0xffffffffffffffff
//    0x00000000004052b2 <+2498>:  test   r15,r15
//    0x00000000004052b5 <+2501>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004052bb <+2507>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x00000000004052c0 <+2512>:  vpbroadcastb ymm1,BYTE PTR [r10+0x7]
//    0x00000000004052c6 <+2518>:  lea    rax,[r11+r15*1]
//    0x00000000004052ca <+2522>:  add    r15d,0x19
//    0x00000000004052ce <+2526>:  and    r15d,0x1f
//    0x00000000004052d2 <+2530>:  mov    rcx,r11
//    0x00000000004052d5 <+2533>:  jmp    0x4052e9 <_Z14avx2_strstr_v2PKcmS0_m+2553>
//    0x00000000004052d7 <+2535>:  add    rcx,r15
//    0x00000000004052da <+2538>:  mov    r15d,0x20
//    0x00000000004052e0 <+2544>:  cmp    rcx,rax
//    0x00000000004052e3 <+2547>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004052e9 <+2553>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x00000000004052ed <+2557>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x7]
//    0x00000000004052f2 <+2562>:  vpand  ymm2,ymm3,ymm2
//    0x00000000004052f6 <+2566>:  vpmovmskb edx,ymm2
//    0x00000000004052fa <+2570>:  test   edx,edx
//    0x00000000004052fc <+2572>:  je     0x4052d7 <_Z14avx2_strstr_v2PKcmS0_m+2535>
//    0x00000000004052fe <+2574>:  mov    esi,DWORD PTR [r10+0x1]
//    0x0000000000405302 <+2578>:  jmp    0x40530b <_Z14avx2_strstr_v2PKcmS0_m+2587>
//    0x0000000000405304 <+2580>:  blsr   edx,edx
//    0x0000000000405309 <+2585>:  je     0x4052d7 <_Z14avx2_strstr_v2PKcmS0_m+2535>
//    0x000000000040530b <+2587>:  xor    edi,edi
//    0x000000000040530d <+2589>:  tzcnt  edi,edx
//    0x0000000000405311 <+2593>:  cmp    DWORD PTR [rcx+rdi*1+0x1],esi
//    0x0000000000405315 <+2597>:  jne    0x405304 <_Z14avx2_strstr_v2PKcmS0_m+2580>
//    0x0000000000405317 <+2599>:  movzx  r8d,WORD PTR [rcx+rdi*1+0x5]
//    0x000000000040531d <+2605>:  cmp    r8w,WORD PTR [r10+0x5]
//    0x0000000000405322 <+2610>:  jne    0x405304 <_Z14avx2_strstr_v2PKcmS0_m+2580>
//    0x0000000000405324 <+2612>:  jmp    0x40559d <_Z14avx2_strstr_v2PKcmS0_m+3245>
  jmp_table[7] = __ pc();
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

//    case 9: 0x0000000000405329 <+2617>:  mov    r13,0xffffffffffffffff
//    0x0000000000405330 <+2624>:  test   r15,r15
//    0x0000000000405333 <+2627>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405339 <+2633>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x000000000040533e <+2638>:  vpbroadcastb ymm1,BYTE PTR [r10+0x8]
//    0x0000000000405344 <+2644>:  lea    rax,[r11+r15*1]
//    0x0000000000405348 <+2648>:  add    r15d,0x18
//    0x000000000040534c <+2652>:  and    r15d,0x1f
//    0x0000000000405350 <+2656>:  mov    rcx,r11
//    0x0000000000405353 <+2659>:  jmp    0x405367 <_Z14avx2_strstr_v2PKcmS0_m+2679>
//    0x0000000000405355 <+2661>:  add    rcx,r15
//    0x0000000000405358 <+2664>:  mov    r15d,0x20
//    0x000000000040535e <+2670>:  cmp    rcx,rax
//    0x0000000000405361 <+2673>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405367 <+2679>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x000000000040536b <+2683>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x8]
//    0x0000000000405370 <+2688>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405374 <+2692>:  vpmovmskb edx,ymm2
//    0x0000000000405378 <+2696>:  test   edx,edx
//    0x000000000040537a <+2698>:  je     0x405355 <_Z14avx2_strstr_v2PKcmS0_m+2661>
//    0x000000000040537c <+2700>:  mov    rsi,QWORD PTR [r10+0x1]
//    0x0000000000405380 <+2704>:  xor    edi,edi
//    0x0000000000405382 <+2706>:  tzcnt  edi,edx
//    0x0000000000405386 <+2710>:  cmp    QWORD PTR [rcx+rdi*1+0x1],rsi
//    0x000000000040538b <+2715>:  je     0x405cee <_Z14avx2_strstr_v2PKcmS0_m+5118>
//    0x0000000000405391 <+2721>:  blsr   edx,edx
//    0x0000000000405396 <+2726>:  jne    0x405380 <_Z14avx2_strstr_v2PKcmS0_m+2704>
//    0x0000000000405398 <+2728>:  jmp    0x405355 <_Z14avx2_strstr_v2PKcmS0_m+2661>
  jmp_table[8] = __ pc();
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

//    case 10: 0x000000000040539a <+2730>:  mov    r13,0xffffffffffffffff
//    0x00000000004053a1 <+2737>:  test   r15,r15
//    0x00000000004053a4 <+2740>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004053aa <+2746>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x00000000004053af <+2751>:  vpbroadcastb ymm1,BYTE PTR [r10+0x9]
//    0x00000000004053b5 <+2757>:  lea    rax,[r11+r15*1]
//    0x00000000004053b9 <+2761>:  add    r15d,0x17
//    0x00000000004053bd <+2765>:  and    r15d,0x1f
//    0x00000000004053c1 <+2769>:  mov    rcx,r11
//    0x00000000004053c4 <+2772>:  jmp    0x4053d8 <_Z14avx2_strstr_v2PKcmS0_m+2792>
//    0x00000000004053c6 <+2774>:  add    rcx,r15
//    0x00000000004053c9 <+2777>:  mov    r15d,0x20
//    0x00000000004053cf <+2783>:  cmp    rcx,rax
//    0x00000000004053d2 <+2786>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004053d8 <+2792>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x00000000004053dc <+2796>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x9]
//    0x00000000004053e1 <+2801>:  vpand  ymm2,ymm3,ymm2
//    0x00000000004053e5 <+2805>:  vpmovmskb edx,ymm2
//    0x00000000004053e9 <+2809>:  test   edx,edx
//    0x00000000004053eb <+2811>:  je     0x4053c6 <_Z14avx2_strstr_v2PKcmS0_m+2774>
//    0x00000000004053ed <+2813>:  mov    rsi,QWORD PTR [r10+0x1]
//    0x00000000004053f1 <+2817>:  xor    edi,edi
//    0x00000000004053f3 <+2819>:  tzcnt  edi,edx
//    0x00000000004053f7 <+2823>:  cmp    QWORD PTR [rcx+rdi*1+0x1],rsi
//    0x00000000004053fc <+2828>:  je     0x405cee <_Z14avx2_strstr_v2PKcmS0_m+5118>
//    0x0000000000405402 <+2834>:  blsr   edx,edx
//    0x0000000000405407 <+2839>:  jne    0x4053f1 <_Z14avx2_strstr_v2PKcmS0_m+2817>
//    0x0000000000405409 <+2841>:  jmp    0x4053c6 <_Z14avx2_strstr_v2PKcmS0_m+2774>
  jmp_table[9] = __ pc();
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

//    case 11: 0x000000000040540b <+2843>:  mov    r13,0xffffffffffffffff
//    0x0000000000405412 <+2850>:  test   r15,r15
//    0x0000000000405415 <+2853>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040541b <+2859>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405420 <+2864>:  vpbroadcastb ymm1,BYTE PTR [r10+0xa]
//    0x0000000000405426 <+2870>:  lea    rax,[r11+r15*1]
//    0x000000000040542a <+2874>:  add    r15d,0x16
//    0x000000000040542e <+2878>:  and    r15d,0x1f
//    0x0000000000405432 <+2882>:  mov    rcx,r11
//    0x0000000000405435 <+2885>:  jmp    0x405449 <_Z14avx2_strstr_v2PKcmS0_m+2905>
//    0x0000000000405437 <+2887>:  add    rcx,r15
//    0x000000000040543a <+2890>:  mov    r15d,0x20
//    0x0000000000405440 <+2896>:  cmp    rcx,rax
//    0x0000000000405443 <+2899>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405449 <+2905>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x000000000040544d <+2909>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0xa]
//    0x0000000000405452 <+2914>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405456 <+2918>:  vpmovmskb edx,ymm2
//    0x000000000040545a <+2922>:  test   edx,edx
//    0x000000000040545c <+2924>:  je     0x405437 <_Z14avx2_strstr_v2PKcmS0_m+2887>
//    0x000000000040545e <+2926>:  mov    rsi,QWORD PTR [r10+0x1]
//    0x0000000000405462 <+2930>:  movzx  edi,BYTE PTR [r10+0x9]
//    0x0000000000405467 <+2935>:  jmp    0x405470 <_Z14avx2_strstr_v2PKcmS0_m+2944>
//    0x0000000000405469 <+2937>:  blsr   edx,edx
//    0x000000000040546e <+2942>:  je     0x405437 <_Z14avx2_strstr_v2PKcmS0_m+2887>
//    0x0000000000405470 <+2944>:  xor    r8d,r8d
//    0x0000000000405473 <+2947>:  tzcnt  r8d,edx
//    0x0000000000405478 <+2952>:  cmp    QWORD PTR [rcx+r8*1+0x1],rsi
//    0x000000000040547d <+2957>:  jne    0x405469 <_Z14avx2_strstr_v2PKcmS0_m+2937>
//    0x000000000040547f <+2959>:  cmp    BYTE PTR [rcx+r8*1+0x9],dil
//    0x0000000000405484 <+2964>:  jne    0x405469 <_Z14avx2_strstr_v2PKcmS0_m+2937>
//    0x0000000000405486 <+2966>:  jmp    0x405f5d <_Z14avx2_strstr_v2PKcmS0_m+5741>
  jmp_table[10] = __ pc();
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

//    case 12: 0x000000000040548b <+2971>:  mov    r13,0xffffffffffffffff
//    0x0000000000405492 <+2978>:  test   r15,r15
//    0x0000000000405495 <+2981>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040549b <+2987>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x00000000004054a0 <+2992>:  vpbroadcastb ymm1,BYTE PTR [r10+0xb]
//    0x00000000004054a6 <+2998>:  lea    rax,[r11+r15*1]
//    0x00000000004054aa <+3002>:  add    r15d,0x15
//    0x00000000004054ae <+3006>:  and    r15d,0x1f
//    0x00000000004054b2 <+3010>:  mov    rcx,r11
//    0x00000000004054b5 <+3013>:  jmp    0x4054c9 <_Z14avx2_strstr_v2PKcmS0_m+3033>
//    0x00000000004054b7 <+3015>:  add    rcx,r15
//    0x00000000004054ba <+3018>:  mov    r15d,0x20
//    0x00000000004054c0 <+3024>:  cmp    rcx,rax
//    0x00000000004054c3 <+3027>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004054c9 <+3033>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x00000000004054cd <+3037>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0xb]
//    0x00000000004054d2 <+3042>:  vpand  ymm2,ymm3,ymm2
//    0x00000000004054d6 <+3046>:  vpmovmskb edx,ymm2
//    0x00000000004054da <+3050>:  test   edx,edx
//    0x00000000004054dc <+3052>:  je     0x4054b7 <_Z14avx2_strstr_v2PKcmS0_m+3015>
//    0x00000000004054de <+3054>:  mov    rsi,QWORD PTR [r10+0x1]
//    0x00000000004054e2 <+3058>:  movzx  edi,WORD PTR [r10+0x9]
//    0x00000000004054e7 <+3063>:  jmp    0x4054f0 <_Z14avx2_strstr_v2PKcmS0_m+3072>
//    0x00000000004054e9 <+3065>:  blsr   edx,edx
//    0x00000000004054ee <+3070>:  je     0x4054b7 <_Z14avx2_strstr_v2PKcmS0_m+3015>
//    0x00000000004054f0 <+3072>:  xor    r8d,r8d
//    0x00000000004054f3 <+3075>:  tzcnt  r8d,edx
//    0x00000000004054f8 <+3080>:  cmp    QWORD PTR [rcx+r8*1+0x1],rsi
//    0x00000000004054fd <+3085>:  jne    0x4054e9 <_Z14avx2_strstr_v2PKcmS0_m+3065>
//    0x00000000004054ff <+3087>:  cmp    WORD PTR [rcx+r8*1+0x9],di
//    0x0000000000405505 <+3093>:  jne    0x4054e9 <_Z14avx2_strstr_v2PKcmS0_m+3065>
//    0x0000000000405507 <+3095>:  jmp    0x405f5d <_Z14avx2_strstr_v2PKcmS0_m+5741>
  jmp_table[11] = __ pc();
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

//    case 13: 0x000000000040550c <+3100>:  mov    r13,0xffffffffffffffff
//    0x0000000000405513 <+3107>:  test   r15,r15
//    0x0000000000405516 <+3110>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040551c <+3116>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405521 <+3121>:  vpbroadcastb ymm1,BYTE PTR [r10+0xc]
//    0x0000000000405527 <+3127>:  lea    rax,[r11+r15*1]
//    0x000000000040552b <+3131>:  add    r15d,0x14
//    0x000000000040552f <+3135>:  and    r15d,0x1f
//    0x0000000000405533 <+3139>:  mov    rcx,r11
//    0x0000000000405536 <+3142>:  jmp    0x40554a <_Z14avx2_strstr_v2PKcmS0_m+3162>
//    0x0000000000405538 <+3144>:  add    rcx,r15
//    0x000000000040553b <+3147>:  mov    r15d,0x20
//    0x0000000000405541 <+3153>:  cmp    rcx,rax
//    0x0000000000405544 <+3156>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040554a <+3162>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x000000000040554e <+3166>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0xc]
//    0x0000000000405553 <+3171>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405557 <+3175>:  vpmovmskb edx,ymm2
//    0x000000000040555b <+3179>:  test   edx,edx
//    0x000000000040555d <+3181>:  je     0x405538 <_Z14avx2_strstr_v2PKcmS0_m+3144>
//    0x000000000040555f <+3183>:  mov    rsi,QWORD PTR [r10+0x1]
//    0x0000000000405563 <+3187>:  jmp    0x405577 <_Z14avx2_strstr_v2PKcmS0_m+3207>
//    0x0000000000405565 <+3189>:  data16 cs nop WORD PTR [rax+rax*1+0x0]
//    0x0000000000405570 <+3200>:  blsr   edx,edx
//    0x0000000000405575 <+3205>:  je     0x405538 <_Z14avx2_strstr_v2PKcmS0_m+3144>
//    0x0000000000405577 <+3207>:  xor    edi,edi
//    0x0000000000405579 <+3209>:  tzcnt  edi,edx
//    0x000000000040557d <+3213>:  cmp    QWORD PTR [rcx+rdi*1+0x1],rsi
//    0x0000000000405582 <+3218>:  jne    0x405570 <_Z14avx2_strstr_v2PKcmS0_m+3200>
//    0x0000000000405584 <+3220>:  movzx  r8d,WORD PTR [rcx+rdi*1+0x9]
//    0x000000000040558a <+3226>:  cmp    r8w,WORD PTR [r10+0x9]
//    0x000000000040558f <+3231>:  jne    0x405570 <_Z14avx2_strstr_v2PKcmS0_m+3200>
//    0x0000000000405591 <+3233>:  movzx  r8d,BYTE PTR [rcx+rdi*1+0xb]
//    0x0000000000405597 <+3239>:  cmp    r8b,BYTE PTR [r10+0xb]
//    0x000000000040559b <+3243>:  jne    0x405570 <_Z14avx2_strstr_v2PKcmS0_m+3200>
//    0x000000000040559d <+3245>:  sub    rcx,r11
//    0x00000000004055a0 <+3248>:  add    rcx,rdi
//    0x00000000004055a3 <+3251>:  jmp    0x406008 <_Z14avx2_strstr_v2PKcmS0_m+5912>
  jmp_table[12] = __ pc();
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

//    case 14 0x00000000004055a8 <+3256>:  mov    r13,0xffffffffffffffff
//    0x00000000004055af <+3263>:  test   r15,r15
//    0x00000000004055b2 <+3266>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004055b8 <+3272>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x00000000004055bd <+3277>:  vpbroadcastb ymm1,BYTE PTR [r10+0xd]
//    0x00000000004055c3 <+3283>:  lea    rax,[r11+r15*1]
//    0x00000000004055c7 <+3287>:  add    r15d,0x13
//    0x00000000004055cb <+3291>:  and    r15d,0x1f
//    0x00000000004055cf <+3295>:  mov    rcx,r11
//    0x00000000004055d2 <+3298>:  jmp    0x4055e6 <_Z14avx2_strstr_v2PKcmS0_m+3318>
//    0x00000000004055d4 <+3300>:  add    rcx,r15
//    0x00000000004055d7 <+3303>:  mov    r15d,0x20
//    0x00000000004055dd <+3309>:  cmp    rcx,rax
//    0x00000000004055e0 <+3312>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004055e6 <+3318>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x00000000004055ea <+3322>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0xd]
//    0x00000000004055ef <+3327>:  vpand  ymm2,ymm3,ymm2
//    0x00000000004055f3 <+3331>:  vpmovmskb edx,ymm2
//    0x00000000004055f7 <+3335>:  test   edx,edx
//    0x00000000004055f9 <+3337>:  je     0x4055d4 <_Z14avx2_strstr_v2PKcmS0_m+3300>
//    0x00000000004055fb <+3339>:  mov    rsi,QWORD PTR [r10+0x1]
//    0x00000000004055ff <+3343>:  mov    edi,DWORD PTR [r10+0x9]
//    0x0000000000405603 <+3347>:  jmp    0x40560c <_Z14avx2_strstr_v2PKcmS0_m+3356>
//    0x0000000000405605 <+3349>:  blsr   edx,edx
//    0x000000000040560a <+3354>:  je     0x4055d4 <_Z14avx2_strstr_v2PKcmS0_m+3300>
//    0x000000000040560c <+3356>:  xor    r8d,r8d
//    0x000000000040560f <+3359>:  tzcnt  r8d,edx
//    0x0000000000405614 <+3364>:  cmp    QWORD PTR [rcx+r8*1+0x1],rsi
//    0x0000000000405619 <+3369>:  jne    0x405605 <_Z14avx2_strstr_v2PKcmS0_m+3349>
//    0x000000000040561b <+3371>:  cmp    DWORD PTR [rcx+r8*1+0x9],edi
//    0x0000000000405620 <+3376>:  jne    0x405605 <_Z14avx2_strstr_v2PKcmS0_m+3349>
//    0x0000000000405622 <+3378>:  jmp    0x405f5d <_Z14avx2_strstr_v2PKcmS0_m+5741>
  jmp_table[13] = __ pc();
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

//    case 15: 0x0000000000405627 <+3383>:  mov    r13,0xffffffffffffffff
//    0x000000000040562e <+3390>:  test   r15,r15
//    0x0000000000405631 <+3393>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405637 <+3399>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x000000000040563c <+3404>:  vpbroadcastb ymm1,BYTE PTR [r10+0xe]
//    0x0000000000405642 <+3410>:  lea    rax,[r11+r15*1]
//    0x0000000000405646 <+3414>:  add    r15d,0x12
//    0x000000000040564a <+3418>:  and    r15d,0x1f
//    0x000000000040564e <+3422>:  mov    rcx,r11
//    0x0000000000405651 <+3425>:  jmp    0x405665 <_Z14avx2_strstr_v2PKcmS0_m+3445>
//    0x0000000000405653 <+3427>:  add    rcx,r15
//    0x0000000000405656 <+3430>:  mov    r15d,0x20
//    0x000000000040565c <+3436>:  cmp    rcx,rax
//    0x000000000040565f <+3439>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405665 <+3445>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405669 <+3449>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0xe]
//    0x000000000040566e <+3454>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405672 <+3458>:  vpmovmskb edx,ymm2
//    0x0000000000405676 <+3462>:  test   edx,edx
//    0x0000000000405678 <+3464>:  je     0x405653 <_Z14avx2_strstr_v2PKcmS0_m+3427>
//    0x000000000040567a <+3466>:  mov    rsi,QWORD PTR [r10+0x1]
//    0x000000000040567e <+3470>:  mov    edi,DWORD PTR [r10+0x9]
//    0x0000000000405682 <+3474>:  movzx  r8d,BYTE PTR [r10+0xd]
//    0x0000000000405687 <+3479>:  jmp    0x405690 <_Z14avx2_strstr_v2PKcmS0_m+3488>
//    0x0000000000405689 <+3481>:  blsr   edx,edx
//    0x000000000040568e <+3486>:  je     0x405653 <_Z14avx2_strstr_v2PKcmS0_m+3427>
//    0x0000000000405690 <+3488>:  xor    r9d,r9d
//    0x0000000000405693 <+3491>:  tzcnt  r9d,edx
//    0x0000000000405698 <+3496>:  cmp    QWORD PTR [rcx+r9*1+0x1],rsi
//    0x000000000040569d <+3501>:  jne    0x405689 <_Z14avx2_strstr_v2PKcmS0_m+3481>
//    0x000000000040569f <+3503>:  cmp    DWORD PTR [rcx+r9*1+0x9],edi
//    0x00000000004056a4 <+3508>:  jne    0x405689 <_Z14avx2_strstr_v2PKcmS0_m+3481>
//    0x00000000004056a6 <+3510>:  cmp    BYTE PTR [rcx+r9*1+0xd],r8b
//    0x00000000004056ab <+3515>:  jne    0x405689 <_Z14avx2_strstr_v2PKcmS0_m+3481>
//    0x00000000004056ad <+3517>:  jmp    0x405fff <_Z14avx2_strstr_v2PKcmS0_m+5903>
  jmp_table[14] = __ pc();
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

//    case 16: 0x00000000004056b2 <+3522>:  mov    r13,0xffffffffffffffff
//    0x00000000004056b9 <+3529>:  test   r15,r15
//    0x00000000004056bc <+3532>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004056c2 <+3538>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x00000000004056c7 <+3543>:  vpbroadcastb ymm1,BYTE PTR [r10+0xf]
//    0x00000000004056cd <+3549>:  lea    rax,[r11+r15*1]
//    0x00000000004056d1 <+3553>:  add    r15d,0x11
//    0x00000000004056d5 <+3557>:  and    r15d,0x1f
//    0x00000000004056d9 <+3561>:  mov    rcx,r11
//    0x00000000004056dc <+3564>:  jmp    0x4056f0 <_Z14avx2_strstr_v2PKcmS0_m+3584>
//    0x00000000004056de <+3566>:  add    rcx,r15
//    0x00000000004056e1 <+3569>:  mov    r15d,0x20
//    0x00000000004056e7 <+3575>:  cmp    rcx,rax
//    0x00000000004056ea <+3578>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004056f0 <+3584>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x00000000004056f4 <+3588>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0xf]
//    0x00000000004056f9 <+3593>:  vpand  ymm2,ymm3,ymm2
//    0x00000000004056fd <+3597>:  vpmovmskb edx,ymm2
//    0x0000000000405701 <+3601>:  test   edx,edx
//    0x0000000000405703 <+3603>:  je     0x4056de <_Z14avx2_strstr_v2PKcmS0_m+3566>
//    0x0000000000405705 <+3605>:  mov    rsi,QWORD PTR [r10+0x1]
//    0x0000000000405709 <+3609>:  mov    edi,DWORD PTR [r10+0x9]
//    0x000000000040570d <+3613>:  movzx  r8d,WORD PTR [r10+0xd]
//    0x0000000000405712 <+3618>:  jmp    0x40571b <_Z14avx2_strstr_v2PKcmS0_m+3627>
//    0x0000000000405714 <+3620>:  blsr   edx,edx
//    0x0000000000405719 <+3625>:  je     0x4056de <_Z14avx2_strstr_v2PKcmS0_m+3566>
//    0x000000000040571b <+3627>:  xor    r9d,r9d
//    0x000000000040571e <+3630>:  tzcnt  r9d,edx
//    0x0000000000405723 <+3635>:  cmp    QWORD PTR [rcx+r9*1+0x1],rsi
//    0x0000000000405728 <+3640>:  jne    0x405714 <_Z14avx2_strstr_v2PKcmS0_m+3620>
//    0x000000000040572a <+3642>:  cmp    DWORD PTR [rcx+r9*1+0x9],edi
//    0x000000000040572f <+3647>:  jne    0x405714 <_Z14avx2_strstr_v2PKcmS0_m+3620>
//    0x0000000000405731 <+3649>:  cmp    WORD PTR [rcx+r9*1+0xd],r8w
//    0x0000000000405737 <+3655>:  jne    0x405714 <_Z14avx2_strstr_v2PKcmS0_m+3620>
//    0x0000000000405739 <+3657>:  jmp    0x405fff <_Z14avx2_strstr_v2PKcmS0_m+5903>
  jmp_table[15] = __ pc();
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

//    case 17: 0x000000000040573e <+3662>:  mov    r13,0xffffffffffffffff
//    0x0000000000405745 <+3669>:  test   r15,r15
//    0x0000000000405748 <+3672>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040574e <+3678>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405753 <+3683>:  mov    r14,r10
//    0x0000000000405756 <+3686>:  vpbroadcastb ymm1,BYTE PTR [r10+0x10]
//    0x000000000040575c <+3692>:  lea    rax,[r11+r15*1]
//    0x0000000000405760 <+3696>:  add    r15d,0x10
//    0x0000000000405764 <+3700>:  and    r15d,0x1f
//    0x0000000000405768 <+3704>:  mov    rcx,r11
//    0x000000000040576b <+3707>:  jmp    0x40577f <_Z14avx2_strstr_v2PKcmS0_m+3727>
//    0x000000000040576d <+3709>:  add    rcx,r15
//    0x0000000000405770 <+3712>:  mov    r15d,0x20
//    0x0000000000405776 <+3718>:  cmp    rcx,rax
//    0x0000000000405779 <+3721>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040577f <+3727>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405783 <+3731>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x10]
//    0x0000000000405788 <+3736>:  vpand  ymm2,ymm3,ymm2
//    0x000000000040578c <+3740>:  vpmovmskb edx,ymm2
//    0x0000000000405790 <+3744>:  test   edx,edx
//    0x0000000000405792 <+3746>:  je     0x40576d <_Z14avx2_strstr_v2PKcmS0_m+3709>
//    0x0000000000405794 <+3748>:  mov    r9,r14
//    0x0000000000405797 <+3751>:  mov    rsi,QWORD PTR [r14+0x1]
//    0x000000000040579b <+3755>:  mov    edi,DWORD PTR [r14+0x9]
//    0x000000000040579f <+3759>:  movzx  r8d,WORD PTR [r14+0xd]
//    0x00000000004057a4 <+3764>:  movzx  r9d,BYTE PTR [r14+0xf]
//    0x00000000004057a9 <+3769>:  jmp    0x4057b2 <_Z14avx2_strstr_v2PKcmS0_m+3778>
//    0x00000000004057ab <+3771>:  blsr   edx,edx
//    0x00000000004057b0 <+3776>:  je     0x40576d <_Z14avx2_strstr_v2PKcmS0_m+3709>
//    0x00000000004057b2 <+3778>:  xor    r10d,r10d
//    0x00000000004057b5 <+3781>:  tzcnt  r10d,edx
//    0x00000000004057ba <+3786>:  cmp    QWORD PTR [rcx+r10*1+0x1],rsi
//    0x00000000004057bf <+3791>:  jne    0x4057ab <_Z14avx2_strstr_v2PKcmS0_m+3771>
//    0x00000000004057c1 <+3793>:  cmp    DWORD PTR [rcx+r10*1+0x9],edi
//    0x00000000004057c6 <+3798>:  jne    0x4057ab <_Z14avx2_strstr_v2PKcmS0_m+3771>
//    0x00000000004057c8 <+3800>:  cmp    WORD PTR [rcx+r10*1+0xd],r8w
//    0x00000000004057ce <+3806>:  jne    0x4057ab <_Z14avx2_strstr_v2PKcmS0_m+3771>
//    0x00000000004057d0 <+3808>:  cmp    BYTE PTR [rcx+r10*1+0xf],r9b
//    0x00000000004057d5 <+3813>:  jne    0x4057ab <_Z14avx2_strstr_v2PKcmS0_m+3771>
//    0x00000000004057d7 <+3815>:  mov    eax,r10d
//    0x00000000004057da <+3818>:  jmp    0x406002 <_Z14avx2_strstr_v2PKcmS0_m+5906>
  jmp_table[16] = __ pc();
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
    __ jmp(L_0x406002);
  }

//    case 18: 0x00000000004057df <+3823>:  mov    r13,0xffffffffffffffff
//    0x00000000004057e6 <+3830>:  test   r15,r15
//    0x00000000004057e9 <+3833>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004057ef <+3839>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x00000000004057f4 <+3844>:  vpbroadcastb ymm1,BYTE PTR [r10+0x11]
//    0x00000000004057fa <+3850>:  lea    rax,[r11+r15*1]
//    0x00000000004057fe <+3854>:  add    r15d,0xf
//    0x0000000000405802 <+3858>:  and    r15d,0x1f
//    0x0000000000405806 <+3862>:  mov    rcx,r11
//    0x0000000000405809 <+3865>:  jmp    0x40581d <_Z14avx2_strstr_v2PKcmS0_m+3885>
//    0x000000000040580b <+3867>:  add    rcx,r15
//    0x000000000040580e <+3870>:  mov    r15d,0x20
//    0x0000000000405814 <+3876>:  cmp    rcx,rax
//    0x0000000000405817 <+3879>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040581d <+3885>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405821 <+3889>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x11]
//    0x0000000000405826 <+3894>:  vpand  ymm2,ymm3,ymm2
//    0x000000000040582a <+3898>:  vpmovmskb edx,ymm2
//    0x000000000040582e <+3902>:  test   edx,edx
//    0x0000000000405830 <+3904>:  je     0x40580b <_Z14avx2_strstr_v2PKcmS0_m+3867>
//    0x0000000000405832 <+3906>:  mov    rsi,QWORD PTR [r10+0x1]
//    0x0000000000405836 <+3910>:  mov    rdi,QWORD PTR [r10+0x9]
//    0x000000000040583a <+3914>:  jmp    0x405843 <_Z14avx2_strstr_v2PKcmS0_m+3923>
//    0x000000000040583c <+3916>:  blsr   edx,edx
//    0x0000000000405841 <+3921>:  je     0x40580b <_Z14avx2_strstr_v2PKcmS0_m+3867>
//    0x0000000000405843 <+3923>:  xor    r8d,r8d
//    0x0000000000405846 <+3926>:  tzcnt  r8d,edx
//    0x000000000040584b <+3931>:  cmp    QWORD PTR [rcx+r8*1+0x1],rsi
//    0x0000000000405850 <+3936>:  jne    0x40583c <_Z14avx2_strstr_v2PKcmS0_m+3916>
//    0x0000000000405852 <+3938>:  cmp    QWORD PTR [rcx+r8*1+0x9],rdi
//    0x0000000000405857 <+3943>:  jne    0x40583c <_Z14avx2_strstr_v2PKcmS0_m+3916>
//    0x0000000000405859 <+3945>:  jmp    0x405f5d <_Z14avx2_strstr_v2PKcmS0_m+5741>
  jmp_table[17] = __ pc();
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

//    case 19: 0x000000000040585e <+3950>:  mov    r13,0xffffffffffffffff
//    0x0000000000405865 <+3957>:  test   r15,r15
//    0x0000000000405868 <+3960>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040586e <+3966>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405873 <+3971>:  vpbroadcastb ymm1,BYTE PTR [r10+0x12]
//    0x0000000000405879 <+3977>:  lea    rax,[r11+r15*1]
//    0x000000000040587d <+3981>:  add    r15d,0xe
//    0x0000000000405881 <+3985>:  and    r15d,0x1f
//    0x0000000000405885 <+3989>:  mov    rcx,r11
//    0x0000000000405888 <+3992>:  jmp    0x40589c <_Z14avx2_strstr_v2PKcmS0_m+4012>
//    0x000000000040588a <+3994>:  add    rcx,r15
//    0x000000000040588d <+3997>:  mov    r15d,0x20
//    0x0000000000405893 <+4003>:  cmp    rcx,rax
//    0x0000000000405896 <+4006>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040589c <+4012>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x00000000004058a0 <+4016>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x12]
//    0x00000000004058a5 <+4021>:  vpand  ymm2,ymm3,ymm2
//    0x00000000004058a9 <+4025>:  vpmovmskb edx,ymm2
//    0x00000000004058ad <+4029>:  test   edx,edx
//    0x00000000004058af <+4031>:  je     0x40588a <_Z14avx2_strstr_v2PKcmS0_m+3994>
//    0x00000000004058b1 <+4033>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x00000000004058b7 <+4039>:  movzx  esi,BYTE PTR [r10+0x11]
//    0x00000000004058bc <+4044>:  jmp    0x4058c5 <_Z14avx2_strstr_v2PKcmS0_m+4053>
//    0x00000000004058be <+4046>:  blsr   edx,edx
//    0x00000000004058c3 <+4051>:  je     0x40588a <_Z14avx2_strstr_v2PKcmS0_m+3994>
//    0x00000000004058c5 <+4053>:  xor    edi,edi
//    0x00000000004058c7 <+4055>:  tzcnt  edi,edx
//    0x00000000004058cb <+4059>:  vmovdqu xmm3,XMMWORD PTR [rcx+rdi*1+0x1]
//    0x00000000004058d1 <+4065>:  vpsubb xmm3,xmm3,xmm2
//    0x00000000004058d5 <+4069>:  vptest xmm3,xmm3
//    0x00000000004058da <+4074>:  jne    0x4058be <_Z14avx2_strstr_v2PKcmS0_m+4046>
//    0x00000000004058dc <+4076>:  cmp    BYTE PTR [rcx+rdi*1+0x11],sil
//    0x00000000004058e1 <+4081>:  jne    0x4058be <_Z14avx2_strstr_v2PKcmS0_m+4046>
//    0x00000000004058e3 <+4083>:  jmp    0x405cee <_Z14avx2_strstr_v2PKcmS0_m+5118>
  jmp_table[18] = __ pc();
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

//    case 20: 0x00000000004058e8 <+4088>:  mov    r13,0xffffffffffffffff
//    0x00000000004058ef <+4095>:  test   r15,r15
//    0x00000000004058f2 <+4098>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004058f8 <+4104>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x00000000004058fd <+4109>:  vpbroadcastb ymm1,BYTE PTR [r10+0x13]
//    0x0000000000405903 <+4115>:  lea    rax,[r11+r15*1]
//    0x0000000000405907 <+4119>:  add    r15d,0xd
//    0x000000000040590b <+4123>:  and    r15d,0x1f
//    0x000000000040590f <+4127>:  mov    rcx,r11
//    0x0000000000405912 <+4130>:  jmp    0x405926 <_Z14avx2_strstr_v2PKcmS0_m+4150>
//    0x0000000000405914 <+4132>:  add    rcx,r15
//    0x0000000000405917 <+4135>:  mov    r15d,0x20
//    0x000000000040591d <+4141>:  cmp    rcx,rax
//    0x0000000000405920 <+4144>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405926 <+4150>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x000000000040592a <+4154>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x13]
//    0x000000000040592f <+4159>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405933 <+4163>:  vpmovmskb edx,ymm2
//    0x0000000000405937 <+4167>:  test   edx,edx
//    0x0000000000405939 <+4169>:  je     0x405914 <_Z14avx2_strstr_v2PKcmS0_m+4132>
//    0x000000000040593b <+4171>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405941 <+4177>:  movzx  esi,WORD PTR [r10+0x11]
//    0x0000000000405946 <+4182>:  jmp    0x40594f <_Z14avx2_strstr_v2PKcmS0_m+4191>
//    0x0000000000405948 <+4184>:  blsr   edx,edx
//    0x000000000040594d <+4189>:  je     0x405914 <_Z14avx2_strstr_v2PKcmS0_m+4132>
//    0x000000000040594f <+4191>:  xor    edi,edi
//    0x0000000000405951 <+4193>:  tzcnt  edi,edx
//    0x0000000000405955 <+4197>:  vmovdqu xmm3,XMMWORD PTR [rcx+rdi*1+0x1]
//    0x000000000040595b <+4203>:  vpsubb xmm3,xmm3,xmm2
//    0x000000000040595f <+4207>:  vptest xmm3,xmm3
//    0x0000000000405964 <+4212>:  jne    0x405948 <_Z14avx2_strstr_v2PKcmS0_m+4184>
//    0x0000000000405966 <+4214>:  cmp    WORD PTR [rcx+rdi*1+0x11],si
//    0x000000000040596b <+4219>:  jne    0x405948 <_Z14avx2_strstr_v2PKcmS0_m+4184>
//    0x000000000040596d <+4221>:  jmp    0x405cee <_Z14avx2_strstr_v2PKcmS0_m+5118>
  jmp_table[19] = __ pc();
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

//    case 21: 0x0000000000405972 <+4226>:  mov    r13,0xffffffffffffffff
//    0x0000000000405979 <+4233>:  test   r15,r15
//    0x000000000040597c <+4236>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405982 <+4242>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405987 <+4247>:  vpbroadcastb ymm1,BYTE PTR [r10+0x14]
//    0x000000000040598d <+4253>:  lea    rax,[r11+r15*1]
//    0x0000000000405991 <+4257>:  add    r15d,0xc
//    0x0000000000405995 <+4261>:  and    r15d,0x1f
//    0x0000000000405999 <+4265>:  mov    rcx,r11
//    0x000000000040599c <+4268>:  jmp    0x4059b0 <_Z14avx2_strstr_v2PKcmS0_m+4288>
//    0x000000000040599e <+4270>:  add    rcx,r15
//    0x00000000004059a1 <+4273>:  mov    r15d,0x20
//    0x00000000004059a7 <+4279>:  cmp    rcx,rax
//    0x00000000004059aa <+4282>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x00000000004059b0 <+4288>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x00000000004059b4 <+4292>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x14]
//    0x00000000004059b9 <+4297>:  vpand  ymm2,ymm3,ymm2
//    0x00000000004059bd <+4301>:  vpmovmskb edx,ymm2
//    0x00000000004059c1 <+4305>:  test   edx,edx
//    0x00000000004059c3 <+4307>:  je     0x40599e <_Z14avx2_strstr_v2PKcmS0_m+4270>
//    0x00000000004059c5 <+4309>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x00000000004059cb <+4315>:  movzx  esi,WORD PTR [r10+0x11]
//    0x00000000004059d0 <+4320>:  movzx  edi,BYTE PTR [r10+0x13]
//    0x00000000004059d5 <+4325>:  jmp    0x4059de <_Z14avx2_strstr_v2PKcmS0_m+4334>
//    0x00000000004059d7 <+4327>:  blsr   edx,edx
//    0x00000000004059dc <+4332>:  je     0x40599e <_Z14avx2_strstr_v2PKcmS0_m+4270>
//    0x00000000004059de <+4334>:  xor    r8d,r8d
//    0x00000000004059e1 <+4337>:  tzcnt  r8d,edx
//    0x00000000004059e6 <+4342>:  vmovdqu xmm3,XMMWORD PTR [rcx+r8*1+0x1]
//    0x00000000004059ed <+4349>:  vpsubb xmm3,xmm3,xmm2
//    0x00000000004059f1 <+4353>:  vptest xmm3,xmm3
//    0x00000000004059f6 <+4358>:  jne    0x4059d7 <_Z14avx2_strstr_v2PKcmS0_m+4327>
//    0x00000000004059f8 <+4360>:  cmp    WORD PTR [rcx+r8*1+0x11],si
//    0x00000000004059fe <+4366>:  jne    0x4059d7 <_Z14avx2_strstr_v2PKcmS0_m+4327>
//    0x0000000000405a00 <+4368>:  cmp    BYTE PTR [rcx+r8*1+0x13],dil
//    0x0000000000405a05 <+4373>:  jne    0x4059d7 <_Z14avx2_strstr_v2PKcmS0_m+4327>
//    0x0000000000405a07 <+4375>:  jmp    0x405f5d <_Z14avx2_strstr_v2PKcmS0_m+5741>
  jmp_table[20] = __ pc();
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

//    case 22: 0x0000000000405a0c <+4380>:  mov    r13,0xffffffffffffffff
//    0x0000000000405a13 <+4387>:  test   r15,r15
//    0x0000000000405a16 <+4390>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405a1c <+4396>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405a21 <+4401>:  vpbroadcastb ymm1,BYTE PTR [r10+0x15]
//    0x0000000000405a27 <+4407>:  lea    rax,[r11+r15*1]
//    0x0000000000405a2b <+4411>:  add    r15d,0xb
//    0x0000000000405a2f <+4415>:  and    r15d,0x1f
//    0x0000000000405a33 <+4419>:  mov    rcx,r11
//    0x0000000000405a36 <+4422>:  jmp    0x405a4a <_Z14avx2_strstr_v2PKcmS0_m+4442>
//    0x0000000000405a38 <+4424>:  add    rcx,r15
//    0x0000000000405a3b <+4427>:  mov    r15d,0x20
//    0x0000000000405a41 <+4433>:  cmp    rcx,rax
//    0x0000000000405a44 <+4436>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405a4a <+4442>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405a4e <+4446>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x15]
//    0x0000000000405a53 <+4451>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405a57 <+4455>:  vpmovmskb edx,ymm2
//    0x0000000000405a5b <+4459>:  test   edx,edx
//    0x0000000000405a5d <+4461>:  je     0x405a38 <_Z14avx2_strstr_v2PKcmS0_m+4424>
//    0x0000000000405a5f <+4463>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405a65 <+4469>:  mov    esi,DWORD PTR [r10+0x11]
//    0x0000000000405a69 <+4473>:  jmp    0x405a72 <_Z14avx2_strstr_v2PKcmS0_m+4482>
//    0x0000000000405a6b <+4475>:  blsr   edx,edx
//    0x0000000000405a70 <+4480>:  je     0x405a38 <_Z14avx2_strstr_v2PKcmS0_m+4424>
//    0x0000000000405a72 <+4482>:  xor    edi,edi
//    0x0000000000405a74 <+4484>:  tzcnt  edi,edx
//    0x0000000000405a78 <+4488>:  vmovdqu xmm3,XMMWORD PTR [rcx+rdi*1+0x1]
//    0x0000000000405a7e <+4494>:  vpsubb xmm3,xmm3,xmm2
//    0x0000000000405a82 <+4498>:  vptest xmm3,xmm3
//    0x0000000000405a87 <+4503>:  jne    0x405a6b <_Z14avx2_strstr_v2PKcmS0_m+4475>
//    0x0000000000405a89 <+4505>:  cmp    DWORD PTR [rcx+rdi*1+0x11],esi
//    0x0000000000405a8d <+4509>:  jne    0x405a6b <_Z14avx2_strstr_v2PKcmS0_m+4475>
//    0x0000000000405a8f <+4511>:  jmp    0x405cee <_Z14avx2_strstr_v2PKcmS0_m+5118>
  jmp_table[21] = __ pc();
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

//    case 23: 0x0000000000405a94 <+4516>:  mov    r13,0xffffffffffffffff
//    0x0000000000405a9b <+4523>:  test   r15,r15
//    0x0000000000405a9e <+4526>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405aa4 <+4532>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405aa9 <+4537>:  vpbroadcastb ymm1,BYTE PTR [r10+0x16]
//    0x0000000000405aaf <+4543>:  lea    rax,[r11+r15*1]
//    0x0000000000405ab3 <+4547>:  add    r15d,0xa
//    0x0000000000405ab7 <+4551>:  and    r15d,0x1f
//    0x0000000000405abb <+4555>:  mov    rcx,r11
//    0x0000000000405abe <+4558>:  jmp    0x405ad2 <_Z14avx2_strstr_v2PKcmS0_m+4578>
//    0x0000000000405ac0 <+4560>:  add    rcx,r15
//    0x0000000000405ac3 <+4563>:  mov    r15d,0x20
//    0x0000000000405ac9 <+4569>:  cmp    rcx,rax
//    0x0000000000405acc <+4572>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405ad2 <+4578>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405ad6 <+4582>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x16]
//    0x0000000000405adb <+4587>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405adf <+4591>:  vpmovmskb edx,ymm2
//    0x0000000000405ae3 <+4595>:  test   edx,edx
//    0x0000000000405ae5 <+4597>:  je     0x405ac0 <_Z14avx2_strstr_v2PKcmS0_m+4560>
//    0x0000000000405ae7 <+4599>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405aed <+4605>:  mov    esi,DWORD PTR [r10+0x11]
//    0x0000000000405af1 <+4609>:  movzx  edi,BYTE PTR [r10+0x15]
//    0x0000000000405af6 <+4614>:  jmp    0x405aff <_Z14avx2_strstr_v2PKcmS0_m+4623>
//    0x0000000000405af8 <+4616>:  blsr   edx,edx
//    0x0000000000405afd <+4621>:  je     0x405ac0 <_Z14avx2_strstr_v2PKcmS0_m+4560>
//    0x0000000000405aff <+4623>:  xor    r8d,r8d
//    0x0000000000405b02 <+4626>:  tzcnt  r8d,edx
//    0x0000000000405b07 <+4631>:  vmovdqu xmm3,XMMWORD PTR [rcx+r8*1+0x1]
//    0x0000000000405b0e <+4638>:  vpsubb xmm3,xmm3,xmm2
//    0x0000000000405b12 <+4642>:  vptest xmm3,xmm3
//    0x0000000000405b17 <+4647>:  jne    0x405af8 <_Z14avx2_strstr_v2PKcmS0_m+4616>
//    0x0000000000405b19 <+4649>:  cmp    DWORD PTR [rcx+r8*1+0x11],esi
//    0x0000000000405b1e <+4654>:  jne    0x405af8 <_Z14avx2_strstr_v2PKcmS0_m+4616>
//    0x0000000000405b20 <+4656>:  cmp    BYTE PTR [rcx+r8*1+0x15],dil
//    0x0000000000405b25 <+4661>:  jne    0x405af8 <_Z14avx2_strstr_v2PKcmS0_m+4616>
//    0x0000000000405b27 <+4663>:  jmp    0x405f5d <_Z14avx2_strstr_v2PKcmS0_m+5741>
  jmp_table[22] = __ pc();
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

//    case 24: 0x0000000000405b2c <+4668>:  mov    r13,0xffffffffffffffff
//    0x0000000000405b33 <+4675>:  test   r15,r15
//    0x0000000000405b36 <+4678>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405b3c <+4684>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405b41 <+4689>:  vpbroadcastb ymm1,BYTE PTR [r10+0x17]
//    0x0000000000405b47 <+4695>:  lea    rax,[r11+r15*1]
//    0x0000000000405b4b <+4699>:  add    r15d,0x9
//    0x0000000000405b4f <+4703>:  and    r15d,0x1f
//    0x0000000000405b53 <+4707>:  mov    rcx,r11
//    0x0000000000405b56 <+4710>:  jmp    0x405b6a <_Z14avx2_strstr_v2PKcmS0_m+4730>
//    0x0000000000405b58 <+4712>:  add    rcx,r15
//    0x0000000000405b5b <+4715>:  mov    r15d,0x20
//    0x0000000000405b61 <+4721>:  cmp    rcx,rax
//    0x0000000000405b64 <+4724>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405b6a <+4730>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405b6e <+4734>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x17]
//    0x0000000000405b73 <+4739>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405b77 <+4743>:  vpmovmskb edx,ymm2
//    0x0000000000405b7b <+4747>:  test   edx,edx
//    0x0000000000405b7d <+4749>:  je     0x405b58 <_Z14avx2_strstr_v2PKcmS0_m+4712>
//    0x0000000000405b7f <+4751>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405b85 <+4757>:  mov    esi,DWORD PTR [r10+0x11]
//    0x0000000000405b89 <+4761>:  movzx  edi,WORD PTR [r10+0x15]
//    0x0000000000405b8e <+4766>:  jmp    0x405b97 <_Z14avx2_strstr_v2PKcmS0_m+4775>
//    0x0000000000405b90 <+4768>:  blsr   edx,edx
//    0x0000000000405b95 <+4773>:  je     0x405b58 <_Z14avx2_strstr_v2PKcmS0_m+4712>
//    0x0000000000405b97 <+4775>:  xor    r8d,r8d
//    0x0000000000405b9a <+4778>:  tzcnt  r8d,edx
//    0x0000000000405b9f <+4783>:  vmovdqu xmm3,XMMWORD PTR [rcx+r8*1+0x1]
//    0x0000000000405ba6 <+4790>:  vpsubb xmm3,xmm3,xmm2
//    0x0000000000405baa <+4794>:  vptest xmm3,xmm3
//    0x0000000000405baf <+4799>:  jne    0x405b90 <_Z14avx2_strstr_v2PKcmS0_m+4768>
//    0x0000000000405bb1 <+4801>:  cmp    DWORD PTR [rcx+r8*1+0x11],esi
//    0x0000000000405bb6 <+4806>:  jne    0x405b90 <_Z14avx2_strstr_v2PKcmS0_m+4768>
//    0x0000000000405bb8 <+4808>:  cmp    WORD PTR [rcx+r8*1+0x15],di
//    0x0000000000405bbe <+4814>:  jne    0x405b90 <_Z14avx2_strstr_v2PKcmS0_m+4768>
//    0x0000000000405bc0 <+4816>:  jmp    0x405f5d <_Z14avx2_strstr_v2PKcmS0_m+5741>
  jmp_table[23] = __ pc();
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

//    case 25: 0x0000000000405bc5 <+4821>:  mov    r13,0xffffffffffffffff
//    0x0000000000405bcc <+4828>:  test   r15,r15
//    0x0000000000405bcf <+4831>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405bd5 <+4837>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405bda <+4842>:  vpbroadcastb ymm1,BYTE PTR [r10+0x18]
//    0x0000000000405be0 <+4848>:  lea    rax,[r11+r15*1]
//    0x0000000000405be4 <+4852>:  add    r15d,0x8
//    0x0000000000405be8 <+4856>:  and    r15d,0x1f
//    0x0000000000405bec <+4860>:  mov    rcx,r11
//    0x0000000000405bef <+4863>:  jmp    0x405c03 <_Z14avx2_strstr_v2PKcmS0_m+4883>
//    0x0000000000405bf1 <+4865>:  add    rcx,r15
//    0x0000000000405bf4 <+4868>:  mov    r15d,0x20
//    0x0000000000405bfa <+4874>:  cmp    rcx,rax
//    0x0000000000405bfd <+4877>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405c03 <+4883>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405c07 <+4887>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x18]
//    0x0000000000405c0c <+4892>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405c10 <+4896>:  vpmovmskb edx,ymm2
//    0x0000000000405c14 <+4900>:  test   edx,edx
//    0x0000000000405c16 <+4902>:  je     0x405bf1 <_Z14avx2_strstr_v2PKcmS0_m+4865>
//    0x0000000000405c18 <+4904>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405c1e <+4910>:  mov    esi,DWORD PTR [r10+0x11]
//    0x0000000000405c22 <+4914>:  movzx  edi,WORD PTR [r10+0x15]
//    0x0000000000405c27 <+4919>:  movzx  r8d,BYTE PTR [r10+0x17]
//    0x0000000000405c2c <+4924>:  jmp    0x405c35 <_Z14avx2_strstr_v2PKcmS0_m+4933>
//    0x0000000000405c2e <+4926>:  blsr   edx,edx
//    0x0000000000405c33 <+4931>:  je     0x405bf1 <_Z14avx2_strstr_v2PKcmS0_m+4865>
//    0x0000000000405c35 <+4933>:  xor    r9d,r9d
//    0x0000000000405c38 <+4936>:  tzcnt  r9d,edx
//    0x0000000000405c3d <+4941>:  vmovdqu xmm3,XMMWORD PTR [rcx+r9*1+0x1]
//    0x0000000000405c44 <+4948>:  vpsubb xmm3,xmm3,xmm2
//    0x0000000000405c48 <+4952>:  vptest xmm3,xmm3
//    0x0000000000405c4d <+4957>:  jne    0x405c2e <_Z14avx2_strstr_v2PKcmS0_m+4926>
//    0x0000000000405c4f <+4959>:  cmp    DWORD PTR [rcx+r9*1+0x11],esi
//    0x0000000000405c54 <+4964>:  jne    0x405c2e <_Z14avx2_strstr_v2PKcmS0_m+4926>
//    0x0000000000405c56 <+4966>:  cmp    WORD PTR [rcx+r9*1+0x15],di
//    0x0000000000405c5c <+4972>:  jne    0x405c2e <_Z14avx2_strstr_v2PKcmS0_m+4926>
//    0x0000000000405c5e <+4974>:  cmp    BYTE PTR [rcx+r9*1+0x17],r8b
//    0x0000000000405c63 <+4979>:  jne    0x405c2e <_Z14avx2_strstr_v2PKcmS0_m+4926>
//    0x0000000000405c65 <+4981>:  jmp    0x405fff <_Z14avx2_strstr_v2PKcmS0_m+5903>
  jmp_table[24] = __ pc();
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

//    case 26: 0x0000000000405c6a <+4986>:  mov    r13,0xffffffffffffffff
//    0x0000000000405c71 <+4993>:  test   r15,r15
//    0x0000000000405c74 <+4996>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405c7a <+5002>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405c7f <+5007>:  vpbroadcastb ymm1,BYTE PTR [r10+0x19]
//    0x0000000000405c85 <+5013>:  lea    rax,[r11+r15*1]
//    0x0000000000405c89 <+5017>:  add    r15d,0x7
//    0x0000000000405c8d <+5021>:  and    r15d,0x1f
//    0x0000000000405c91 <+5025>:  mov    rcx,r11
//    0x0000000000405c94 <+5028>:  jmp    0x405ca8 <_Z14avx2_strstr_v2PKcmS0_m+5048>
//    0x0000000000405c96 <+5030>:  add    rcx,r15
//    0x0000000000405c99 <+5033>:  mov    r15d,0x20
//    0x0000000000405c9f <+5039>:  cmp    rcx,rax
//    0x0000000000405ca2 <+5042>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405ca8 <+5048>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405cac <+5052>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x19]
//    0x0000000000405cb1 <+5057>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405cb5 <+5061>:  vpmovmskb edx,ymm2
//    0x0000000000405cb9 <+5065>:  test   edx,edx
//    0x0000000000405cbb <+5067>:  je     0x405c96 <_Z14avx2_strstr_v2PKcmS0_m+5030>
//    0x0000000000405cbd <+5069>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405cc3 <+5075>:  mov    rsi,QWORD PTR [r10+0x11]
//    0x0000000000405cc7 <+5079>:  jmp    0x405cd0 <_Z14avx2_strstr_v2PKcmS0_m+5088>
//    0x0000000000405cc9 <+5081>:  blsr   edx,edx
//    0x0000000000405cce <+5086>:  je     0x405c96 <_Z14avx2_strstr_v2PKcmS0_m+5030>
//    0x0000000000405cd0 <+5088>:  xor    edi,edi
//    0x0000000000405cd2 <+5090>:  tzcnt  edi,edx
//    0x0000000000405cd6 <+5094>:  vmovdqu xmm3,XMMWORD PTR [rcx+rdi*1+0x1]
//    0x0000000000405cdc <+5100>:  vpsubb xmm3,xmm3,xmm2
//    0x0000000000405ce0 <+5104>:  vptest xmm3,xmm3
//    0x0000000000405ce5 <+5109>:  jne    0x405cc9 <_Z14avx2_strstr_v2PKcmS0_m+5081>
//    0x0000000000405ce7 <+5111>:  cmp    QWORD PTR [rcx+rdi*1+0x11],rsi
//    0x0000000000405cec <+5116>:  jne    0x405cc9 <_Z14avx2_strstr_v2PKcmS0_m+5081>
//    0x0000000000405cee <+5118>:  mov    eax,edi
//    0x0000000000405cf0 <+5120>:  jmp    0x406002 <_Z14avx2_strstr_v2PKcmS0_m+5906>
  jmp_table[25] = __ pc();
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
    __ jmp(L_0x406002);
  }

//    case 27: 0x0000000000405cf5 <+5125>:  mov    r13,0xffffffffffffffff
//    0x0000000000405cfc <+5132>:  test   r15,r15
//    0x0000000000405cff <+5135>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405d05 <+5141>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405d0a <+5146>:  vpbroadcastb ymm1,BYTE PTR [r10+0x1a]
//    0x0000000000405d10 <+5152>:  lea    rax,[r11+r15*1]
//    0x0000000000405d14 <+5156>:  add    r15d,0x6
//    0x0000000000405d18 <+5160>:  and    r15d,0x1f
//    0x0000000000405d1c <+5164>:  mov    rcx,r11
//    0x0000000000405d1f <+5167>:  jmp    0x405d33 <_Z14avx2_strstr_v2PKcmS0_m+5187>
//    0x0000000000405d21 <+5169>:  add    rcx,r15
//    0x0000000000405d24 <+5172>:  mov    r15d,0x20
//    0x0000000000405d2a <+5178>:  cmp    rcx,rax
//    0x0000000000405d2d <+5181>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405d33 <+5187>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405d37 <+5191>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x1a]
//    0x0000000000405d3c <+5196>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405d40 <+5200>:  vpmovmskb edx,ymm2
//    0x0000000000405d44 <+5204>:  test   edx,edx
//    0x0000000000405d46 <+5206>:  je     0x405d21 <_Z14avx2_strstr_v2PKcmS0_m+5169>
//    0x0000000000405d48 <+5208>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405d4e <+5214>:  mov    rsi,QWORD PTR [r10+0x11]
//    0x0000000000405d52 <+5218>:  movzx  edi,BYTE PTR [r10+0x19]
//    0x0000000000405d57 <+5223>:  jmp    0x405d60 <_Z14avx2_strstr_v2PKcmS0_m+5232>
//    0x0000000000405d59 <+5225>:  blsr   edx,edx
//    0x0000000000405d5e <+5230>:  je     0x405d21 <_Z14avx2_strstr_v2PKcmS0_m+5169>
//    0x0000000000405d60 <+5232>:  xor    r8d,r8d
//    0x0000000000405d63 <+5235>:  tzcnt  r8d,edx
//    0x0000000000405d68 <+5240>:  vmovdqu xmm3,XMMWORD PTR [rcx+r8*1+0x1]
//    0x0000000000405d6f <+5247>:  vpsubb xmm3,xmm3,xmm2
//    0x0000000000405d73 <+5251>:  vptest xmm3,xmm3
//    0x0000000000405d78 <+5256>:  jne    0x405d59 <_Z14avx2_strstr_v2PKcmS0_m+5225>
//    0x0000000000405d7a <+5258>:  cmp    QWORD PTR [rcx+r8*1+0x11],rsi
//    0x0000000000405d7f <+5263>:  jne    0x405d59 <_Z14avx2_strstr_v2PKcmS0_m+5225>
//    0x0000000000405d81 <+5265>:  cmp    BYTE PTR [rcx+r8*1+0x19],dil
//    0x0000000000405d86 <+5270>:  jne    0x405d59 <_Z14avx2_strstr_v2PKcmS0_m+5225>
//    0x0000000000405d88 <+5272>:  jmp    0x405f5d <_Z14avx2_strstr_v2PKcmS0_m+5741>
  jmp_table[26] = __ pc();
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

//    case 28: 0x0000000000405d8d <+5277>:  mov    r13,0xffffffffffffffff
//    0x0000000000405d94 <+5284>:  test   r15,r15
//    0x0000000000405d97 <+5287>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405d9d <+5293>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405da2 <+5298>:  vpbroadcastb ymm1,BYTE PTR [r10+0x1b]
//    0x0000000000405da8 <+5304>:  lea    rax,[r11+r15*1]
//    0x0000000000405dac <+5308>:  add    r15d,0x5
//    0x0000000000405db0 <+5312>:  and    r15d,0x1f
//    0x0000000000405db4 <+5316>:  mov    rcx,r11
//    0x0000000000405db7 <+5319>:  jmp    0x405dcb <_Z14avx2_strstr_v2PKcmS0_m+5339>
//    0x0000000000405db9 <+5321>:  add    rcx,r15
//    0x0000000000405dbc <+5324>:  mov    r15d,0x20
//    0x0000000000405dc2 <+5330>:  cmp    rcx,rax
//    0x0000000000405dc5 <+5333>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405dcb <+5339>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405dcf <+5343>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x1b]
//    0x0000000000405dd4 <+5348>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405dd8 <+5352>:  vpmovmskb edx,ymm2
//    0x0000000000405ddc <+5356>:  test   edx,edx
//    0x0000000000405dde <+5358>:  je     0x405db9 <_Z14avx2_strstr_v2PKcmS0_m+5321>
//    0x0000000000405de0 <+5360>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405de6 <+5366>:  mov    rsi,QWORD PTR [r10+0x11]
//    0x0000000000405dea <+5370>:  movzx  edi,WORD PTR [r10+0x19]
//    0x0000000000405def <+5375>:  jmp    0x405df8 <_Z14avx2_strstr_v2PKcmS0_m+5384>
//    0x0000000000405df1 <+5377>:  blsr   edx,edx
//    0x0000000000405df6 <+5382>:  je     0x405db9 <_Z14avx2_strstr_v2PKcmS0_m+5321>
//    0x0000000000405df8 <+5384>:  xor    r8d,r8d
//    0x0000000000405dfb <+5387>:  tzcnt  r8d,edx
//    0x0000000000405e00 <+5392>:  vmovdqu xmm3,XMMWORD PTR [rcx+r8*1+0x1]
//    0x0000000000405e07 <+5399>:  vpsubb xmm3,xmm3,xmm2
//    0x0000000000405e0b <+5403>:  vptest xmm3,xmm3
//    0x0000000000405e10 <+5408>:  jne    0x405df1 <_Z14avx2_strstr_v2PKcmS0_m+5377>
//    0x0000000000405e12 <+5410>:  cmp    QWORD PTR [rcx+r8*1+0x11],rsi
//    0x0000000000405e17 <+5415>:  jne    0x405df1 <_Z14avx2_strstr_v2PKcmS0_m+5377>
//    0x0000000000405e19 <+5417>:  cmp    WORD PTR [rcx+r8*1+0x19],di
//    0x0000000000405e1f <+5423>:  jne    0x405df1 <_Z14avx2_strstr_v2PKcmS0_m+5377>
//    0x0000000000405e21 <+5425>:  jmp    0x405f5d <_Z14avx2_strstr_v2PKcmS0_m+5741>
  jmp_table[27] = __ pc();
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

//    case 29: 0x0000000000405e26 <+5430>:  mov    r13,0xffffffffffffffff
//    0x0000000000405e2d <+5437>:  test   r15,r15
//    0x0000000000405e30 <+5440>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405e36 <+5446>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405e3b <+5451>:  vpbroadcastb ymm1,BYTE PTR [r10+0x1c]
//    0x0000000000405e41 <+5457>:  lea    rax,[r11+r15*1]
//    0x0000000000405e45 <+5461>:  add    r15d,0x4
//    0x0000000000405e49 <+5465>:  and    r15d,0x1f
//    0x0000000000405e4d <+5469>:  mov    rcx,r11
//    0x0000000000405e50 <+5472>:  jmp    0x405e64 <_Z14avx2_strstr_v2PKcmS0_m+5492>
//    0x0000000000405e52 <+5474>:  add    rcx,r15
//    0x0000000000405e55 <+5477>:  mov    r15d,0x20
//    0x0000000000405e5b <+5483>:  cmp    rcx,rax
//    0x0000000000405e5e <+5486>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405e64 <+5492>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405e68 <+5496>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x1c]
//    0x0000000000405e6d <+5501>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405e71 <+5505>:  vpmovmskb edx,ymm2
//    0x0000000000405e75 <+5509>:  test   edx,edx
//    0x0000000000405e77 <+5511>:  je     0x405e52 <_Z14avx2_strstr_v2PKcmS0_m+5474>
//    0x0000000000405e79 <+5513>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405e7f <+5519>:  mov    rsi,QWORD PTR [r10+0x11]
//    0x0000000000405e83 <+5523>:  movzx  edi,WORD PTR [r10+0x19]
//    0x0000000000405e88 <+5528>:  movzx  r8d,BYTE PTR [r10+0x1b]
//    0x0000000000405e8d <+5533>:  jmp    0x405e96 <_Z14avx2_strstr_v2PKcmS0_m+5542>
//    0x0000000000405e8f <+5535>:  blsr   edx,edx
//    0x0000000000405e94 <+5540>:  je     0x405e52 <_Z14avx2_strstr_v2PKcmS0_m+5474>
//    0x0000000000405e96 <+5542>:  xor    r9d,r9d
//    0x0000000000405e99 <+5545>:  tzcnt  r9d,edx
//    0x0000000000405e9e <+5550>:  vmovdqu xmm3,XMMWORD PTR [rcx+r9*1+0x1]
//    0x0000000000405ea5 <+5557>:  vpsubb xmm3,xmm3,xmm2
//    0x0000000000405ea9 <+5561>:  vptest xmm3,xmm3
//    0x0000000000405eae <+5566>:  jne    0x405e8f <_Z14avx2_strstr_v2PKcmS0_m+5535>
//    0x0000000000405eb0 <+5568>:  cmp    QWORD PTR [rcx+r9*1+0x11],rsi
//    0x0000000000405eb5 <+5573>:  jne    0x405e8f <_Z14avx2_strstr_v2PKcmS0_m+5535>
//    0x0000000000405eb7 <+5575>:  cmp    WORD PTR [rcx+r9*1+0x19],di
//    0x0000000000405ebd <+5581>:  jne    0x405e8f <_Z14avx2_strstr_v2PKcmS0_m+5535>
//    0x0000000000405ebf <+5583>:  cmp    BYTE PTR [rcx+r9*1+0x1b],r8b
//    0x0000000000405ec4 <+5588>:  jne    0x405e8f <_Z14avx2_strstr_v2PKcmS0_m+5535>
//    0x0000000000405ec6 <+5590>:  jmp    0x405fff <_Z14avx2_strstr_v2PKcmS0_m+5903>
  jmp_table[28] = __ pc();
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

//    case 30: 0x0000000000405ecb <+5595>:  mov    r13,0xffffffffffffffff
//    0x0000000000405ed2 <+5602>:  test   r15,r15
//    0x0000000000405ed5 <+5605>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405edb <+5611>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405ee0 <+5616>:  vpbroadcastb ymm1,BYTE PTR [r10+0x1d]
//    0x0000000000405ee6 <+5622>:  lea    rax,[r11+r15*1]
//    0x0000000000405eea <+5626>:  add    r15d,0x3
//    0x0000000000405eee <+5630>:  and    r15d,0x1f
//    0x0000000000405ef2 <+5634>:  mov    rcx,r11
//    0x0000000000405ef5 <+5637>:  jmp    0x405f09 <_Z14avx2_strstr_v2PKcmS0_m+5657>
//    0x0000000000405ef7 <+5639>:  add    rcx,r15
//    0x0000000000405efa <+5642>:  mov    r15d,0x20
//    0x0000000000405f00 <+5648>:  cmp    rcx,rax
//    0x0000000000405f03 <+5651>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405f09 <+5657>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405f0d <+5661>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x1d]
//    0x0000000000405f12 <+5666>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405f16 <+5670>:  vpmovmskb edx,ymm2
//    0x0000000000405f1a <+5674>:  test   edx,edx
//    0x0000000000405f1c <+5676>:  je     0x405ef7 <_Z14avx2_strstr_v2PKcmS0_m+5639>
//    0x0000000000405f1e <+5678>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405f24 <+5684>:  mov    rsi,QWORD PTR [r10+0x11]
//    0x0000000000405f28 <+5688>:  mov    edi,DWORD PTR [r10+0x19]
//    0x0000000000405f2c <+5692>:  jmp    0x405f35 <_Z14avx2_strstr_v2PKcmS0_m+5701>
//    0x0000000000405f2e <+5694>:  blsr   edx,edx
//    0x0000000000405f33 <+5699>:  je     0x405ef7 <_Z14avx2_strstr_v2PKcmS0_m+5639>
//    0x0000000000405f35 <+5701>:  xor    r8d,r8d
//    0x0000000000405f38 <+5704>:  tzcnt  r8d,edx
//    0x0000000000405f3d <+5709>:  vmovdqu xmm3,XMMWORD PTR [rcx+r8*1+0x1]
//    0x0000000000405f44 <+5716>:  vpsubb xmm3,xmm3,xmm2
//    0x0000000000405f48 <+5720>:  vptest xmm3,xmm3
//    0x0000000000405f4d <+5725>:  jne    0x405f2e <_Z14avx2_strstr_v2PKcmS0_m+5694>
//    0x0000000000405f4f <+5727>:  cmp    QWORD PTR [rcx+r8*1+0x11],rsi
//    0x0000000000405f54 <+5732>:  jne    0x405f2e <_Z14avx2_strstr_v2PKcmS0_m+5694>
//    0x0000000000405f56 <+5734>:  cmp    DWORD PTR [rcx+r8*1+0x19],edi
//    0x0000000000405f5b <+5739>:  jne    0x405f2e <_Z14avx2_strstr_v2PKcmS0_m+5694>
//    0x0000000000405f5d <+5741>:  mov    eax,r8d
//    0x0000000000405f60 <+5744>:  jmp    0x406002 <_Z14avx2_strstr_v2PKcmS0_m+5906>
  jmp_table[29] = __ pc();
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
    __ jmp(L_0x406002);
  }

//    case 31: 0x0000000000405f65 <+5749>:  mov    r13,0xffffffffffffffff
//    0x0000000000405f6c <+5756>:  test   r15,r15
//    0x0000000000405f6f <+5759>:  jle    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405f75 <+5765>:  vpbroadcastb ymm0,BYTE PTR [r10]
//    0x0000000000405f7a <+5770>:  vpbroadcastb ymm1,BYTE PTR [r10+0x1e]
//    0x0000000000405f80 <+5776>:  lea    rax,[r11+r15*1]
//    0x0000000000405f84 <+5780>:  add    r15d,0x2
//    0x0000000000405f88 <+5784>:  and    r15d,0x1f
//    0x0000000000405f8c <+5788>:  mov    rcx,r11
//    0x0000000000405f8f <+5791>:  jmp    0x405f9f <_Z14avx2_strstr_v2PKcmS0_m+5807>
//    0x0000000000405f91 <+5793>:  add    rcx,r15
//    0x0000000000405f94 <+5796>:  mov    r15d,0x20
//    0x0000000000405f9a <+5802>:  cmp    rcx,rax
//    0x0000000000405f9d <+5805>:  jae    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x0000000000405f9f <+5807>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rcx]
//    0x0000000000405fa3 <+5811>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rcx+0x1e]
//    0x0000000000405fa8 <+5816>:  vpand  ymm2,ymm3,ymm2
//    0x0000000000405fac <+5820>:  vpmovmskb edx,ymm2
//    0x0000000000405fb0 <+5824>:  test   edx,edx
//    0x0000000000405fb2 <+5826>:  je     0x405f91 <_Z14avx2_strstr_v2PKcmS0_m+5793>
//    0x0000000000405fb4 <+5828>:  vmovdqu xmm2,XMMWORD PTR [r10+0x1]
//    0x0000000000405fba <+5834>:  mov    rsi,QWORD PTR [r10+0x11]
//    0x0000000000405fbe <+5838>:  mov    edi,DWORD PTR [r10+0x19]
//    0x0000000000405fc2 <+5842>:  movzx  r8d,BYTE PTR [r10+0x1d]
//    0x0000000000405fc7 <+5847>:  jmp    0x405fd0 <_Z14avx2_strstr_v2PKcmS0_m+5856>
//    0x0000000000405fc9 <+5849>:  blsr   edx,edx
//    0x0000000000405fce <+5854>:  je     0x405f91 <_Z14avx2_strstr_v2PKcmS0_m+5793>
//    0x0000000000405fd0 <+5856>:  xor    r9d,r9d
//    0x0000000000405fd3 <+5859>:  tzcnt  r9d,edx
//    0x0000000000405fd8 <+5864>:  vmovdqu xmm3,XMMWORD PTR [rcx+r9*1+0x1]
//    0x0000000000405fdf <+5871>:  vpsubb xmm3,xmm3,xmm2
//    0x0000000000405fe3 <+5875>:  vptest xmm3,xmm3
//    0x0000000000405fe8 <+5880>:  jne    0x405fc9 <_Z14avx2_strstr_v2PKcmS0_m+5849>
//    0x0000000000405fea <+5882>:  cmp    QWORD PTR [rcx+r9*1+0x11],rsi
//    0x0000000000405fef <+5887>:  jne    0x405fc9 <_Z14avx2_strstr_v2PKcmS0_m+5849>
//    0x0000000000405ff1 <+5889>:  cmp    DWORD PTR [rcx+r9*1+0x19],edi
//    0x0000000000405ff6 <+5894>:  jne    0x405fc9 <_Z14avx2_strstr_v2PKcmS0_m+5849>
//    0x0000000000405ff8 <+5896>:  cmp    BYTE PTR [rcx+r9*1+0x1d],r8b
//    0x0000000000405ffd <+5901>:  jne    0x405fc9 <_Z14avx2_strstr_v2PKcmS0_m+5849>
  jmp_table[30] = __ pc();
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
//    0x0000000000405fff <+5903>:  mov    eax,r9d
  __ movl(rax, r9);

//   if (result <= n - k)
//   {
//     return result;
//   }
  __ bind(L_0x406002);
//    final_check: 0x0000000000406002 <+5906>:  sub    rcx,r11
//    0x0000000000406005 <+5909>:  add    rcx,rax
  __ subq(rcx, r11);
  __ addq(rcx, rax);

  __ bind(L_0x406008);
//    0x0000000000406008 <+5912>:  mov    r13,rcx
  __ movq(r13, rcx);

  __ bind(L_exit);
//    0x000000000040600b <+5915>:  cmp    r13,rbx
//    0x000000000040600e <+5918>:  mov    r15,0xffffffffffffffff
//    0x0000000000406015 <+5925>:  cmovbe r15,r13
//    0x0000000000406019 <+5929>:  mov    rax,r15
//    0x000000000040601c <+5932>:  add    rsp,0x78
//    0x0000000000406020 <+5936>:  pop    rbx
//    0x0000000000406021 <+5937>:  pop    r12
//    0x0000000000406023 <+5939>:  pop    r13
//    0x0000000000406025 <+5941>:  pop    r14
//    0x0000000000406027 <+5943>:  pop    r15
//    0x0000000000406029 <+5945>:  pop    rbp
//    0x000000000040602a <+5946>:  vzeroupper
//    0x000000000040602d <+5949>:  ret
  __ cmpq(r13, rbx);
  __ movq(r15, -1);
  __ cmovq(Assembler::belowEqual, r15, r13);
  __ bind(L_0x406019);
  jmp_table_1[0] = __ pc();
  jmp_table_1[1] = __ pc();
  __ movq(rax, r15);
  __ addptr(rsp, 0xf0);
#ifdef _WIN64
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

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);


  __ bind(L_0x40602e);
//    0x000000000040602e <+5950>:  mov    eax,ebp
//    0x0000000000406030 <+5952>:  mov    r13,QWORD PTR [rsp+0x8]
//    0x0000000000406035 <+5957>:  sub    r13,QWORD PTR [rsp+0x18]
//    0x000000000040603a <+5962>:  add    r13,rax
//    0x000000000040603d <+5965>:  mov    rbx,QWORD PTR [rsp+0x20]
//    0x0000000000406042 <+5970>:  jmp    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
  __ movl(rax, rbp);
  __ movq(r13, Address(rsp, 0x8));
  __ subq(r13, Address(rsp, 0x18));
  __ addq(r13, rax);
  __ movq(rbx, Address(rsp, 0x20));
  __ jmpb(L_exit);

  __ bind(L_0x406044);
//    0x0000000000406044 <+5972>:  mov    rax,r15
//    0x0000000000406047 <+5975>:  and    rax,0xffffffffffffffe0
//    0x000000000040604b <+5979>:  and    r15d,0x1f
//    0x000000000040604f <+5983>:  mov    r13,0xffffffffffffffff
//    0x0000000000406056 <+5990>:  cmp    r15,rax
//    0x0000000000406059 <+5993>:  jge    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
//    0x000000000040605b <+5995>:  add    rax,r11
  __ movq(rax, r15);
  __ andq(rax, -32);
  __ andl(r15, 0x1f);
  __ movq(r13, -1);
  __ cmpq(r15, rax);
  __ jge(L_exit);
  __ addq(rax, r11);

  __ bind(L_0x40605e);
//    0x000000000040605e <+5998>:  vpcmpeqb ymm1,ymm0,YMMWORD PTR [r11+r15*1]
//    0x0000000000406064 <+6004>:  vpmovmskb ecx,ymm1
//    0x0000000000406068 <+6008>:  test   ecx,ecx
//    0x000000000040606a <+6010>:  jne    0x406093 <_Z14avx2_strstr_v2PKcmS0_m+6051>
  __ vpcmpeqb(xmm1, xmm0, Address(r11, r15, Address::times_1), Assembler::AVX_256bit);
  __ vpmovmskb(rcx, xmm1, Assembler::AVX_256bit);
  __ testl(rcx, rcx);
  __ jne_b(L_0x406093);
//    0x000000000040606c <+6012>:  lea    rcx,[r11+r15*1]
//    0x0000000000406070 <+6016>:  add    rcx,0x20
//    0x0000000000406074 <+6020>:  add    r15,0x20
//    0x0000000000406078 <+6024>:  cmp    rcx,rax
//    0x000000000040607b <+6027>:  jb     0x40605e <_Z14avx2_strstr_v2PKcmS0_m+5998>
//    0x000000000040607d <+6029>:  jmp    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
  __ leaq(rcx, Address(r11, r15, Address::times_1));
  __ addq(rcx, 0x20);
  __ addq(r15, 0x20);
  __ cmpq(rcx, rax);
  __ jb(L_0x40605e);
  __ jmp(L_exit);

//    0x000000000040607f <+6031>:  xor    ecx,ecx
//    0x0000000000406081 <+6033>:  tzcnt  ecx,edx
//    0x0000000000406085 <+6037>:  sub    rax,r11
//    0x0000000000406088 <+6040>:  add    rax,rcx
//    0x000000000040608b <+6043>:  mov    r13,rax
//    0x000000000040608e <+6046>:  jmp    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
  __ bind(L_0x40607f);
  __ tzcntl(rcx, rdx);
  __ subq(rax, r11);
  __ addq(rax, rcx);
  __ movq(r13, rax);
  __ jmp(L_exit);

  __ bind(L_0x406093);
//    0x0000000000406093 <+6051>:  xor    r13d,r13d
//    0x0000000000406096 <+6054>:  tzcnt  r13d,ecx
//    0x000000000040609b <+6059>:  add    r13,r15
//    0x000000000040609e <+6062>:  jmp    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
  __ tzcntl(r13, rcx);
  __ addq(r13, r15);
  __ jmp(L_exit);

  __ bind(L_0x4060a3);
//    0x00000000004060a3 <+6067>:  mov    rbx,QWORD PTR [rsp+0x20]
//    0x00000000004060a8 <+6072>:  mov    r13,0xffffffffffffffff
//    0x00000000004060af <+6079>:  jmp    0x40600b <_Z14avx2_strstr_v2PKcmS0_m+5915>
  __ movq(rbx, Address(rsp, 0x20));
  __ movq(r13, -1);
  __ jmp(L_exit);

////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
  __ align(8);

  jump_table = __ pc();

  for(jmp_ndx = 0; jmp_ndx < 32; jmp_ndx++) {
    __ emit_address(jmp_table[jmp_ndx]);
  }

  jump_table_1 = __ pc();

  for(jmp_ndx = 0; jmp_ndx < 32; jmp_ndx++) {
    __ emit_address(jmp_table_1[jmp_ndx]);
  }

  __ align(16);
  __ bind(L_begin);
//  0x00000000004048f0 <+0>:     push   rbp
//  0x00000000004048f1 <+1>:     push   r15
//  0x00000000004048f3 <+3>:     push   r14
//  0x00000000004048f5 <+5>:     push   r13
//  0x00000000004048f7 <+7>:     push   r12
//  0x00000000004048f9 <+9>:     push   rbx
//  0x00000000004048fa <+10>:    sub    rsp,0x78
//  0x00000000004048fe <+14>:    mov    rbx,rsi
//  0x0000000000404901 <+17>:    sub    rbx,rcx
//  0x0000000000404904 <+20>:    jae    0x404912 <_Z14avx2_strstr_v2PKcmS0_m+34>
//  0x0000000000404906 <+22>:    mov    r15,0xffffffffffffffff
//  0x000000000040490d <+29>:    jmp    0x406019 <_Z14avx2_strstr_v2PKcmS0_m+5929>
  __ push(r15);
  __ push(r14);
  __ push(r13);
  __ push(r12);
  __ push(rbx);
  __ push(rbp);
#ifdef _WIN64
  __ push(rsi);
  __ push(rdi);

  __ movq(rdi, rcx);
  __ movq(rsi, rdx);
  __ movq(rdx, r8);
  __ movq(rcx, r9);
#endif

  __ subptr(rsp, 0xf0);
  __ movq(rbx, rsi);
  __ subq(rbx, rcx);
  __ jae(L_0x404912);
  __ bind(L_error);
  __ movq(r15, -1);
  __ jmp(L_0x406019);

  __ bind(L_0x404912);

//  0x0000000000404912 <+34>:    mov    r12,rcx
//  0x0000000000404915 <+37>:    test   rcx,rcx
//  0x0000000000404918 <+40>:    je     0x40496c <_Z14avx2_strstr_v2PKcmS0_m+124>
//  0x000000000040491a <+42>:    mov    r10,rdx
//  0x000000000040491d <+45>:    mov    r15,rsi
//  0x0000000000404920 <+48>:    mov    r11,rdi
// if ((n < 32) || ((long long)n < 32 + (long long)k - 1))
//  0x0000000000404923 <+51>:    cmp    rsi,0x20
//  0x0000000000404927 <+55>:    jb     0x404974 <_Z14avx2_strstr_v2PKcmS0_m+132>
//  0x0000000000404929 <+57>:    lea    rax,[r12+0x1f]
//  0x000000000040492e <+62>:    cmp    rax,r15
//  0x0000000000404931 <+65>:    jg     0x404974 <_Z14avx2_strstr_v2PKcmS0_m+132>
//  0x0000000000404933 <+67>:    lea    rax,[r12-0x1]
//  0x0000000000404938 <+72>:    cmp    rax,0x1e
//  0x000000000040493c <+76>:    ja     0x404f1f <_Z14avx2_strstr_v2PKcmS0_m+1583>
//  0x0000000000404942 <+82>:    jmp    QWORD PTR [rax*8+0x415100]

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
  __ bind(L_0x404933);
  __ leaq(rax, Address(r12, -0x1));
  __ cmpq(rax, 0x1e);
  __ ja(L_0x404f1f);
  __ mov64(r13, (int64_t) jump_table);
  __ jmp(Address(r13, rax, Address::times_8));

  __ bind(L_trampoline);
  __ mov64(r13, (int64_t) jump_table_1);
  __ jmp(Address(r13, rax, Address::times_8));




  __ align(CodeEntryAlignment);
  __ bind(memcmp_avx2);

//    1 /* memcmp/wmemcmp optimized with AVX2.
//    2    Copyright (C) 2017-2023 Free Software Foundation, Inc.
//    3    This file is part of the GNU C Library.
//    4
//    5    The GNU C Library is free software; you can redistribute it and/or
//    6    modify it under the terms of the GNU Lesser General Public
//    7    License as published by the Free Software Foundation; either
//    8    version 2.1 of the License, or (at your option) any later version.
//    9
//   10    The GNU C Library is distributed in the hope that it will be useful,
//   11    but WITHOUT ANY WARRANTY; without even the implied warranty of
//   12    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//   13    Lesser General Public License for more details.
//   14
//   15    You should have received a copy of the GNU Lesser General Public
//   16    License along with the GNU C Library; if not, see
//   17    <https://www.gnu.org/licenses/>.  */
//   18
//   23 /* memcmp/wmemcmp is implemented as:
//   24    1. Use ymm vector compares when possible. The only case where
//   25       vector compares is not possible for when size < VEC_SIZE
//   26       and loading from either s1 or s2 would cause a page cross.
//   27    2. For size from 2 to 7 bytes on page cross, load as big endian
//   28       with movbe and bswap to avoid branches.
//   29    3. Use xmm vector compare when size >= 4 bytes for memcmp or
//   30       size >= 8 bytes for wmemcmp.
//   31    4. Optimistically compare up to first 4 * VEC_SIZE one at a
//   32       to check for early mismatches. Only do this if its guaranteed the
//   33       work is not wasted.
//   34    5. If size is 8 * VEC_SIZE or less, unroll the loop.
//   35    6. Compare 4 * VEC_SIZE at a time with the aligned first memory
//   36       area.
//   37    7. Use 2 vector compares when size is 2 * VEC_SIZE or less.
//   38    8. Use 4 vector compares when size is 4 * VEC_SIZE or less.
//   39    9. Use 8 vector compares when size is 8 * VEC_SIZE or less.  */

    Label L_less_vec, L_return_vec_0, L_last_1x_vec, L_return_vec_1, L_last_2x_vec;
    Label L_return_vec_2, L_retun_vec_3, L_more_8x_vec, L_return_vec_0_1_2_3;
    Label L_return_vzeroupper, L_8x_return_vec_0_1_2_3, L_loop_4x_vec;
    Label L_return_vec_3, L_8x_last_1x_vec, L_8x_last_2x_vec, L_8x_return_vec_2;
    Label L_8x_return_vec_3, L_return_vec_1_end, L_return_vec_0_end, L_one_or_less;
    Label L_page_cross_less_vec, L_between_16_31, L_between_8_15, L_between_2_3, L_zero;
    Label L_ret_nonzero;

// Dump of assembler code for function __memcmp_avx2_movbe:
// 71
//    0x00007ffff7d99c00 <+0>:     f3 0f 1e fa     endbr64

// 72              .section SECTION(.text),"ax",@progbits
// 73      ENTRY (MEMCMP)
// 74      # ifdef USE_AS_WMEMCMP
// 75              shl     $2, %RDX_LP
// 76      # elif defined __ILP32__
// 77              /* Clear the upper 32 bits.  */
// 78              movl    %edx, %edx
//    0x00007ffff7d99c04 <+4>:     48 83 fa 20     cmp    rdx,0x20
    __ cmpq(rdx, 0x20);

// 79      # endif
//    0x00007ffff7d99c08 <+8>:     0f 82 d2 02 00 00       jb     0x7ffff7d99ee0 <__memcmp_avx2_movbe+736>
    __ jb(L_less_vec);

// 80              cmp     $VEC_SIZE, %RDX_LP
// 81              jb      L(less_vec)
// 82
//    0x00007ffff7d99c0e <+14>:    c5 fe 6f 0e     vmovdqu ymm1,YMMWORD PTR [rsi]
    __ vmovdqu(xmm1, Address(rsi, 0));

// 83              /* From VEC to 2 * VEC.  No branch when size == VEC_SIZE.  */
//    0x00007ffff7d99c12 <+18>:    c5 f5 74 0f     vpcmpeqb ymm1,ymm1,YMMWORD PTR [rdi]
    __ vpcmpeqb(xmm1, xmm1, Address(rdi, 0), Assembler::AVX_256bit);

// 84              vmovdqu (%rsi), %ymm1
//    0x00007ffff7d99c16 <+22>:    c5 fd d7 c1     vpmovmskb eax,ymm1
    __ vpmovmskb(rax, xmm1, Assembler::AVX_256bit);

// 85              VPCMPEQ (%rdi), %ymm1, %ymm1
// 86              vpmovmskb %ymm1, %eax
// 87              /* NB: eax must be destination register if going to
// 88                 L(return_vec_[0,2]). For L(return_vec_3 destination register
//    0x00007ffff7d99c1a <+26>:    ff c0   inc    eax
    __ incrementl(rax);

// 89                 must be ecx.  */
//    0x00007ffff7d99c1c <+28>:    0f 85 be 00 00 00       jne    0x7ffff7d99ce0 <__memcmp_avx2_movbe+224>
    __ jne(L_return_vec_0);

// 90              incl    %eax
// 91              jnz     L(return_vec_0)
//    0x00007ffff7d99c22 <+34>:    48 83 fa 40     cmp    rdx,0x40
    __ cmpq(rdx, 0x40);

// 92
//    0x00007ffff7d99c26 <+38>:    0f 86 38 02 00 00       jbe    0x7ffff7d99e64 <__memcmp_avx2_movbe+612>
    __ jbe(L_last_1x_vec);

// 93              cmpq    $(VEC_SIZE * 2), %rdx
// 94              jbe     L(last_1x_vec)
// 95
//    0x00007ffff7d99c2c <+44>:    c5 fe 6f 56 20  vmovdqu ymm2,YMMWORD PTR [rsi+0x20]
    __ vmovdqu(xmm2, Address(rsi, 0x20));

// 96              /* Check second VEC no matter what.  */
//    0x00007ffff7d99c31 <+49>:    c5 ed 74 57 20  vpcmpeqb ymm2,ymm2,YMMWORD PTR [rdi+0x20]
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0x20), Assembler::AVX_256bit);

// 97              vmovdqu VEC_SIZE(%rsi), %ymm2
//    0x00007ffff7d99c36 <+54>:    c5 fd d7 c2     vpmovmskb eax,ymm2
    __ vpmovmskb(rax, xmm2, Assembler::AVX_256bit);

// 98              VPCMPEQ VEC_SIZE(%rdi), %ymm2, %ymm2
// 99              vpmovmskb %ymm2, %eax
// 100             /* If all 4 VEC where equal eax will be all 1s so incl will
//    0x00007ffff7d99c3a <+58>:    ff c0   inc    eax
    __ incrementl(rax);

// 101                overflow and set zero flag.  */
//    0x00007ffff7d99c3c <+60>:    0f 85 be 00 00 00       jne    0x7ffff7d99d00 <__memcmp_avx2_movbe+256>
    __ jne(L_return_vec_1);

// 102             incl    %eax
// 103             jnz     L(return_vec_1)
// 104
//    0x00007ffff7d99c42 <+66>:    48 81 fa 80 00 00 00    cmp    rdx,0x80
    __ cmpq(rdx, 0x80);

// 105             /* Less than 4 * VEC.  */
//    0x00007ffff7d99c49 <+73>:    0f 86 01 02 00 00       jbe    0x7ffff7d99e50 <__memcmp_avx2_movbe+592>
    __ jbe(L_last_2x_vec);

// 106             cmpq    $(VEC_SIZE * 4), %rdx
// 107             jbe     L(last_2x_vec)
// 108
//    0x00007ffff7d99c4f <+79>:    c5 fe 6f 5e 40  vmovdqu ymm3,YMMWORD PTR [rsi+0x40]
    __ vmovdqu(xmm3, Address(rsi, 0x40));

// 109             /* Check third and fourth VEC no matter what.  */
//    0x00007ffff7d99c54 <+84>:    c5 e5 74 5f 40  vpcmpeqb ymm3,ymm3,YMMWORD PTR [rdi+0x40]
    __ vpcmpeqb(xmm3, xmm3, Address(rdi, 0x40), Assembler::AVX_256bit);

// 110             vmovdqu (VEC_SIZE * 2)(%rsi), %ymm3
//    0x00007ffff7d99c59 <+89>:    c5 fd d7 c3     vpmovmskb eax,ymm3
    __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);

// 111             VPCMPEQ (VEC_SIZE * 2)(%rdi), %ymm3, %ymm3
//    0x00007ffff7d99c5d <+93>:    ff c0   inc    eax
    __ incrementl(rax);

// 112             vpmovmskb %ymm3, %eax
//    0x00007ffff7d99c5f <+95>:    0f 85 bb 00 00 00       jne    0x7ffff7d99d20 <__memcmp_avx2_movbe+288>
    __ jne(L_return_vec_2);

// 113             incl    %eax
//    0x00007ffff7d99c65 <+101>:   c5 fe 6f 66 60  vmovdqu ymm4,YMMWORD PTR [rsi+0x60]
    __ vmovdqu(xmm4, Address(rsi, 0x60));

// 114             jnz     L(return_vec_2)
//    0x00007ffff7d99c6a <+106>:   c5 dd 74 67 60  vpcmpeqb ymm4,ymm4,YMMWORD PTR [rdi+0x60]
    __ vpcmpeqb(xmm4, xmm4, Address(rdi, 0x60), Assembler::AVX_256bit);

// 115             vmovdqu (VEC_SIZE * 3)(%rsi), %ymm4
//    0x00007ffff7d99c6f <+111>:   c5 fd d7 cc     vpmovmskb ecx,ymm4
    __ vpmovmskb(rcx, xmm4, Assembler::AVX_256bit);

// 116             VPCMPEQ (VEC_SIZE * 3)(%rdi), %ymm4, %ymm4
//    0x00007ffff7d99c73 <+115>:   ff c1   inc    ecx
    __ incrementl(rcx);

// 117             vpmovmskb %ymm4, %ecx
//    0x00007ffff7d99c75 <+117>:   0f 85 e0 00 00 00       jne    0x7ffff7d99d5b <__memcmp_avx2_movbe+347>
    __ jne(L_return_vec_3);

// 118             incl    %ecx
// 119             jnz     L(return_vec_3)
// 120
//    0x00007ffff7d99c7b <+123>:   48 81 fa 00 01 00 00    cmp    rdx,0x100
    __ cmpq(rdx, 0x100);

// 121             /* Go to 4x VEC loop.  */
//    0x00007ffff7d99c82 <+130>:   0f 87 e8 00 00 00       ja     0x7ffff7d99d70 <__memcmp_avx2_movbe+368>
    __ ja(L_more_8x_vec);

// 122             cmpq    $(VEC_SIZE * 8), %rdx
// 123             ja      L(more_8x_vec)
// 124
// 125             /* Handle remainder of size = 4 * VEC + 1 to 8 * VEC without any
// 126                branches.  */
// 127
//    0x00007ffff7d99c88 <+136>:   c5 fe 6f 4c 16 80       vmovdqu ymm1,YMMWORD PTR [rsi+rdx*1-0x80]
    __ vmovdqu(xmm1, Address(rsi, rdx, Address::times_1, -0x80));

// 128             /* Load first two VEC from s2 before adjusting addresses.  */
//    0x00007ffff7d99c8e <+142>:   c5 fe 6f 54 16 a0       vmovdqu ymm2,YMMWORD PTR [rsi+rdx*1-0x60]
    __ vmovdqu(xmm2, Address(rsi, rdx, Address::times_1, -0x60));

// 129             vmovdqu -(VEC_SIZE * 4)(%rsi, %rdx), %ymm1
//    0x00007ffff7d99c94 <+148>:   48 8d 7c 17 80  lea    rdi,[rdi+rdx*1-0x80]
    __ leaq(rdi, Address(rdi, rdx, Address::times_1, -0x80));

// 130             vmovdqu -(VEC_SIZE * 3)(%rsi, %rdx), %ymm2
//    0x00007ffff7d99c99 <+153>:   48 8d 74 16 80  lea    rsi,[rsi+rdx*1-0x80]
    __ leaq(rsi, Address(rsi, rdx, Address::times_1, -0x80));

// 131             leaq    -(4 * VEC_SIZE)(%rdi, %rdx), %rdi
// 132             leaq    -(4 * VEC_SIZE)(%rsi, %rdx), %rsi
// 133
// 134             /* Wait to load from s1 until addressed adjust due to
//    0x00007ffff7d99c9e <+158>:   c5 f5 74 0f     vpcmpeqb ymm1,ymm1,YMMWORD PTR [rdi]
    __ vpcmpeqb(xmm1, xmm1, Address(rdi, 0), Assembler::AVX_256bit);

// 135                unlamination of microfusion with complex address mode.  */
//    0x00007ffff7d99ca2 <+162>:   c5 ed 74 57 20  vpcmpeqb ymm2,ymm2,YMMWORD PTR [rdi+0x20]
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0x20), Assembler::AVX_256bit);

// 136             VPCMPEQ (%rdi), %ymm1, %ymm1
// 137             VPCMPEQ (VEC_SIZE)(%rdi), %ymm2, %ymm2
//    0x00007ffff7d99ca7 <+167>:   c5 fe 6f 5e 40  vmovdqu ymm3,YMMWORD PTR [rsi+0x40]
    __ vmovdqu(xmm3, Address(rsi, 0x40));

// 138
//    0x00007ffff7d99cac <+172>:   c5 e5 74 5f 40  vpcmpeqb ymm3,ymm3,YMMWORD PTR [rdi+0x40]
    __ vpcmpeqb(xmm3, xmm3, Address(rdi, 0x40), Assembler::AVX_256bit);

// 139             vmovdqu (VEC_SIZE * 2)(%rsi), %ymm3
//    0x00007ffff7d99cb1 <+177>:   c5 fe 6f 66 60  vmovdqu ymm4,YMMWORD PTR [rsi+0x60]
    __ vmovdqu(xmm4, Address(rsi, 0x60));

// 140             VPCMPEQ (VEC_SIZE * 2)(%rdi), %ymm3, %ymm3
//    0x00007ffff7d99cb6 <+182>:   c5 dd 74 67 60  vpcmpeqb ymm4,ymm4,YMMWORD PTR [rdi+0x60]
    __ vpcmpeqb(xmm4, xmm4, Address(rdi, 0x60), Assembler::AVX_256bit);

// 141             vmovdqu (VEC_SIZE * 3)(%rsi), %ymm4
// 142             VPCMPEQ (VEC_SIZE * 3)(%rdi), %ymm4, %ymm4
// 143
//    0x00007ffff7d99cbb <+187>:   c5 ed db e9     vpand  ymm5,ymm2,ymm1
    __ vpand(xmm5, xmm2, xmm1, Assembler::AVX_256bit);

// 144             /* Reduce VEC0 - VEC4.  */
//    0x00007ffff7d99cbf <+191>:   c5 dd db f3     vpand  ymm6,ymm4,ymm3
    __ vpand(xmm6, xmm4, xmm3, Assembler::AVX_256bit);

// 145             vpand   %ymm1, %ymm2, %ymm5
//    0x00007ffff7d99cc3 <+195>:   c5 cd db fd     vpand  ymm7,ymm6,ymm5
    __ vpand(xmm7, xmm6, xmm5, Assembler::AVX_256bit);

// 146             vpand   %ymm3, %ymm4, %ymm6
//    0x00007ffff7d99cc7 <+199>:   c5 fd d7 cf     vpmovmskb ecx,ymm7
    __ vpmovmskb(rcx, xmm7, Assembler::AVX_256bit);

// 147             vpand   %ymm5, %ymm6, %ymm7
//    0x00007ffff7d99ccb <+203>:   ff c1   inc    ecx
    __ incrementl(rcx);

// 148             vpmovmskb %ymm7, %ecx
//    0x00007ffff7d99ccd <+205>:   75 74   jne    0x7ffff7d99d43 <__memcmp_avx2_movbe+323>
    __ jne_b(L_return_vec_0_1_2_3);

// 149             incl    %ecx
// 150             jnz     L(return_vec_0_1_2_3)
//    0x00007ffff7d99ccf <+207>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99cd2 <+210>:   c3      ret
//    0x00007ffff7d99cd3 <+211>:   66 66 2e 0f 1f 84 00 00 00 00 00        data16 cs nop WORD PTR [rax+rax*1+0x0]
//    0x00007ffff7d99cde <+222>:   66 90   xchg   ax,ax
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_return_vec_0);

// 151             /* NB: eax must be zero to reach here.  */
// 152             VZEROUPPER_RETURN
// 153
// 154             .p2align 4
//    0x00007ffff7d99ce0 <+224>:   f3 0f bc c0     tzcnt  eax,eax
    __ tzcntl(rax, rax);

// 155     L(return_vec_0):
// 156             tzcntl  %eax, %eax
// 157     # ifdef USE_AS_WMEMCMP
// 158             movl    (%rdi, %rax), %ecx
// 159             xorl    %edx, %edx
// 160             cmpl    (%rsi, %rax), %ecx
// 161             /* NB: no partial register stall here because xorl zero idiom
// 162                above.  */
// 163             setg    %dl
// 164             leal    -1(%rdx, %rdx), %eax
//    0x00007ffff7d99ce4 <+228>:   0f b6 0c 06     movzx  ecx,BYTE PTR [rsi+rax*1]
    __ movzbl(rcx, Address(rsi, rax, Address::times_1));

// 165     # else
//    0x00007ffff7d99ce8 <+232>:   0f b6 04 07     movzx  eax,BYTE PTR [rdi+rax*1]
    __ movzbl(rax, Address(rdi, rax, Address::times_1));

// 166             movzbl  (%rsi, %rax), %ecx
//    0x00007ffff7d99cec <+236>:   29 c8   sub    eax,ecx
    __ subl(rax, rcx);

// 167             movzbl  (%rdi, %rax), %eax
// 168             subl    %ecx, %eax
// 169     # endif
//    0x00007ffff7d99cee <+238>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99cf1 <+241>:   c3      ret
//    0x00007ffff7d99cf2 <+242>:   66 66 2e 0f 1f 84 00 00 00 00 00        data16 cs nop WORD PTR [rax+rax*1+0x0]
//    0x00007ffff7d99cfd <+253>:   0f 1f 00        nop    DWORD PTR [rax]
    __ bind(L_return_vzeroupper);
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_return_vec_1);

// 170     L(return_vzeroupper):
// 171             ZERO_UPPER_VEC_REGISTERS_RETURN
// 172
// 173             .p2align 4
//    0x00007ffff7d99d00 <+256>:   f3 0f bc c0     tzcnt  eax,eax
    __ tzcntl(rax, rax);

// 174     L(return_vec_1):
// 175             tzcntl  %eax, %eax
// 176     # ifdef USE_AS_WMEMCMP
// 177             movl    VEC_SIZE(%rdi, %rax), %ecx
// 178             xorl    %edx, %edx
// 179             cmpl    VEC_SIZE(%rsi, %rax), %ecx
// 180             setg    %dl
// 181             leal    -1(%rdx, %rdx), %eax
//    0x00007ffff7d99d04 <+260>:   0f b6 4c 06 20  movzx  ecx,BYTE PTR [rsi+rax*1+0x20]
    __ movzbl(rcx, Address(rsi, rax, Address::times_1, 0x20));

// 182     # else
//    0x00007ffff7d99d09 <+265>:   0f b6 44 07 20  movzx  eax,BYTE PTR [rdi+rax*1+0x20]
    __ movzbl(rax, Address(rdi, rax, Address::times_1, 0x20));

// 183             movzbl  VEC_SIZE(%rsi, %rax), %ecx
//    0x00007ffff7d99d0e <+270>:   29 c8   sub    eax,ecx
    __ subl(rax, rcx);

// 184             movzbl  VEC_SIZE(%rdi, %rax), %eax
// 185             subl    %ecx, %eax
//    0x00007ffff7d99d10 <+272>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99d13 <+275>:   c3      ret
//    0x00007ffff7d99d14 <+276>:   66 66 2e 0f 1f 84 00 00 00 00 00        data16 cs nop WORD PTR [rax+rax*1+0x0]
//    0x00007ffff7d99d1f <+287>:   90      nop
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_return_vec_2);

// 186     # endif
// 187             VZEROUPPER_RETURN
// 188
// 189             .p2align 4
//    0x00007ffff7d99d20 <+288>:   f3 0f bc c0     tzcnt  eax,eax
    __ tzcntl(rax, rax);

// 190     L(return_vec_2):
// 191             tzcntl  %eax, %eax
// 192     # ifdef USE_AS_WMEMCMP
// 193             movl    (VEC_SIZE * 2)(%rdi, %rax), %ecx
// 194             xorl    %edx, %edx
// 195             cmpl    (VEC_SIZE * 2)(%rsi, %rax), %ecx
// 196             setg    %dl
// 197             leal    -1(%rdx, %rdx), %eax
//    0x00007ffff7d99d24 <+292>:   0f b6 4c 06 40  movzx  ecx,BYTE PTR [rsi+rax*1+0x40]
    __ movzbl(rcx, Address(rsi, rax, Address::times_1, 0x40));

// 198     # else
//    0x00007ffff7d99d29 <+297>:   0f b6 44 07 40  movzx  eax,BYTE PTR [rdi+rax*1+0x40]
    __ movzbl(rax, Address(rdi, rax, Address::times_1, 0x40));

// 199             movzbl  (VEC_SIZE * 2)(%rsi, %rax), %ecx
//    0x00007ffff7d99d2e <+302>:   29 c8   sub    eax,ecx
    __ subl(rax, rcx);

// 200             movzbl  (VEC_SIZE * 2)(%rdi, %rax), %eax
// 201             subl    %ecx, %eax
//    0x00007ffff7d99d30 <+304>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99d33 <+307>:   c3      ret
//    0x00007ffff7d99d34 <+308>:   66 66 2e 0f 1f 84 00 00 00 00 00        data16 cs nop WORD PTR [rax+rax*1+0x0]
//    0x00007ffff7d99d3f <+319>:   90      nop
    __ vzeroupper();
    __ ret(0);
    __ align(32);

    __ bind(L_8x_return_vec_0_1_2_3);

// 202     # endif
// 203             VZEROUPPER_RETURN
// 204
// 205             /* NB: p2align 5 here to ensure 4x loop is 32 byte aligned.  */
// 206             .p2align 5
// 207     L(8x_return_vec_0_1_2_3):
//    0x00007ffff7d99d40 <+320>:   48 01 fe        add    rsi,rdi
    __ addq(rsi, rdi);

// 208             /* Returning from L(more_8x_vec) requires restoring rsi.  */
// 209             addq    %rdi, %rsi
//    0x00007ffff7d99d43 <+323>:   c5 fd d7 c1     vpmovmskb eax,ymm1
    __ bind(L_return_vec_0_1_2_3);
    __ vpmovmskb(rax, xmm1, Assembler::AVX_256bit);

// 210     L(return_vec_0_1_2_3):
//    0x00007ffff7d99d47 <+327>:   ff c0   inc    eax
    __ incrementl(rax);

// 211             vpmovmskb %ymm1, %eax
//    0x00007ffff7d99d49 <+329>:   75 95   jne    0x7ffff7d99ce0 <__memcmp_avx2_movbe+224>
    __ jne_b(L_return_vec_0);

// 212             incl    %eax
// 213             jnz     L(return_vec_0)
//    0x00007ffff7d99d4b <+331>:   c5 fd d7 c2     vpmovmskb eax,ymm2
    __ vpmovmskb(rax, xmm2, Assembler::AVX_256bit);

// 214
//    0x00007ffff7d99d4f <+335>:   ff c0   inc    eax
    __ incrementl(rax);

// 215             vpmovmskb %ymm2, %eax
//    0x00007ffff7d99d51 <+337>:   75 ad   jne    0x7ffff7d99d00 <__memcmp_avx2_movbe+256>
    __ jne_b(L_return_vec_1);

// 216             incl    %eax
// 217             jnz     L(return_vec_1)
//    0x00007ffff7d99d53 <+339>:   c5 fd d7 c3     vpmovmskb eax,ymm3
    __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);

// 218
//    0x00007ffff7d99d57 <+343>:   ff c0   inc    eax
    __ incrementl(rax);

// 219             vpmovmskb %ymm3, %eax
//    0x00007ffff7d99d59 <+345>:   75 c5   jne    0x7ffff7d99d20 <__memcmp_avx2_movbe+288>
    __ jne_b(L_return_vec_2);

// 220             incl    %eax
// 221             jnz     L(return_vec_2)
//    0x00007ffff7d99d5b <+347>:   f3 0f bc c9     tzcnt  ecx,ecx
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
//    0x00007ffff7d99d5f <+351>:   0f b6 44 0f 60  movzx  eax,BYTE PTR [rdi+rcx*1+0x60]
    __ movzbl(rax, Address(rdi, rcx, Address::times_1, 0x60));

// 230     # else
//    0x00007ffff7d99d64 <+356>:   0f b6 4c 0e 60  movzx  ecx,BYTE PTR [rsi+rcx*1+0x60]
    __ movzbl(rcx, Address(rsi, rcx, Address::times_1, 0x60));

// 231             movzbl  (VEC_SIZE * 3)(%rdi, %rcx), %eax
//    0x00007ffff7d99d69 <+361>:   29 c8   sub    eax,ecx
    __ subl(rax, rcx);

// 232             movzbl  (VEC_SIZE * 3)(%rsi, %rcx), %ecx
// 233             subl    %ecx, %eax
//    0x00007ffff7d99d6b <+363>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99d6e <+366>:   c3      ret
//    0x00007ffff7d99d6f <+367>:   90      nop
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_more_8x_vec);

// 234     # endif
// 235             VZEROUPPER_RETURN
// 236
// 237             .p2align 4
// 238     L(more_8x_vec):
//    0x00007ffff7d99d70 <+368>:   48 8d 54 17 80  lea    rdx,[rdi+rdx*1-0x80]
    __ leaq(rdx, Address(rdi, rdx, Address::times_1, -0x80));

// 239             /* Set end of s1 in rdx.  */
// 240             leaq    -(VEC_SIZE * 4)(%rdi, %rdx), %rdx
// 241             /* rsi stores s2 - s1. This allows loop to only update one
//    0x00007ffff7d99d75 <+373>:   48 29 fe        sub    rsi,rdi
    __ subq(rsi, rdi);

// 242                pointer.  */
// 243             subq    %rdi, %rsi
//    0x00007ffff7d99d78 <+376>:   48 83 e7 e0     and    rdi,0xffffffffffffffe0
    __ andq(rdi, -32);

// 244             /* Align s1 pointer.  */
// 245             andq    $-VEC_SIZE, %rdi
//    0x00007ffff7d99d7c <+380>:   48 83 ef 80     sub    rdi,0xffffffffffffff80
    __ subq(rdi, -128);
    __ align(16);
    __ bind(L_loop_4x_vec);

// 246             /* Adjust because first 4x vec where check already.  */
// 247             subq    $-(VEC_SIZE * 4), %rdi
// 248             .p2align 4
// 249     L(loop_4x_vec):
// 250             /* rsi has s2 - s1 so get correct address by adding s1 (in rdi).
//    0x00007ffff7d99d80 <+384>:   c5 fe 6f 0c 3e  vmovdqu ymm1,YMMWORD PTR [rsi+rdi*1]
    __ vmovdqu(xmm1, Address(rsi, rdi, Address::times_1));

// 251              */
//    0x00007ffff7d99d85 <+389>:   c5 f5 74 0f     vpcmpeqb ymm1,ymm1,YMMWORD PTR [rdi]
    __ vpcmpeqb(xmm1, xmm1, Address(rdi, 0), Assembler::AVX_256bit);

// 252             vmovdqu (%rsi, %rdi), %ymm1
// 253             VPCMPEQ (%rdi), %ymm1, %ymm1
//    0x00007ffff7d99d89 <+393>:   c5 fe 6f 54 3e 20       vmovdqu ymm2,YMMWORD PTR [rsi+rdi*1+0x20]
    __ vmovdqu(xmm2, Address(rsi, rdi, Address::times_1, 0x20));

// 254
//    0x00007ffff7d99d8f <+399>:   c5 ed 74 57 20  vpcmpeqb ymm2,ymm2,YMMWORD PTR [rdi+0x20]
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0x20), Assembler::AVX_256bit);

// 255             vmovdqu VEC_SIZE(%rsi, %rdi), %ymm2
// 256             VPCMPEQ VEC_SIZE(%rdi), %ymm2, %ymm2
//    0x00007ffff7d99d94 <+404>:   c5 fe 6f 5c 3e 40       vmovdqu ymm3,YMMWORD PTR [rsi+rdi*1+0x40]
    __ vmovdqu(xmm3, Address(rsi, rdi, Address::times_1, 0x40));

// 257
//    0x00007ffff7d99d9a <+410>:   c5 e5 74 5f 40  vpcmpeqb ymm3,ymm3,YMMWORD PTR [rdi+0x40]
    __ vpcmpeqb(xmm3, xmm3, Address(rdi, 0x40), Assembler::AVX_256bit);

// 258             vmovdqu (VEC_SIZE * 2)(%rsi, %rdi), %ymm3
// 259             VPCMPEQ (VEC_SIZE * 2)(%rdi), %ymm3, %ymm3
//    0x00007ffff7d99d9f <+415>:   c5 fe 6f 64 3e 60       vmovdqu ymm4,YMMWORD PTR [rsi+rdi*1+0x60]
    __ vmovdqu(xmm4, Address(rsi, rdi, Address::times_1, 0x60));

// 260
//    0x00007ffff7d99da5 <+421>:   c5 dd 74 67 60  vpcmpeqb ymm4,ymm4,YMMWORD PTR [rdi+0x60]
    __ vpcmpeqb(xmm4, xmm4, Address(rdi, 0x60), Assembler::AVX_256bit);

// 261             vmovdqu (VEC_SIZE * 3)(%rsi, %rdi), %ymm4
// 262             VPCMPEQ (VEC_SIZE * 3)(%rdi), %ymm4, %ymm4
//    0x00007ffff7d99daa <+426>:   c5 ed db e9     vpand  ymm5,ymm2,ymm1
    __ vpand(xmm5, xmm2, xmm1, Assembler::AVX_256bit);

// 263
//    0x00007ffff7d99dae <+430>:   c5 dd db f3     vpand  ymm6,ymm4,ymm3
    __ vpand(xmm6, xmm4, xmm3, Assembler::AVX_256bit);

// 264             vpand   %ymm1, %ymm2, %ymm5
//    0x00007ffff7d99db2 <+434>:   c5 cd db fd     vpand  ymm7,ymm6,ymm5
    __ vpand(xmm7, xmm6, xmm5, Assembler::AVX_256bit);

// 265             vpand   %ymm3, %ymm4, %ymm6
//    0x00007ffff7d99db6 <+438>:   c5 fd d7 cf     vpmovmskb ecx,ymm7
    __ vpmovmskb(rcx, xmm7, Assembler::AVX_256bit);

// 266             vpand   %ymm5, %ymm6, %ymm7
//    0x00007ffff7d99dba <+442>:   ff c1   inc    ecx
    __ incrementl(rcx);

// 267             vpmovmskb %ymm7, %ecx
//    0x00007ffff7d99dbc <+444>:   75 82   jne    0x7ffff7d99d40 <__memcmp_avx2_movbe+320>
    __ jne_b(L_8x_return_vec_0_1_2_3);

// 268             incl    %ecx
//    0x00007ffff7d99dbe <+446>:   48 83 ef 80     sub    rdi,0xffffffffffffff80
    __ subq(rdi, -128);

// 269             jnz     L(8x_return_vec_0_1_2_3)
// 270             subq    $-(VEC_SIZE * 4), %rdi
//    0x00007ffff7d99dc2 <+450>:   48 39 d7        cmp    rdi,rdx
    __ cmpq(rdi, rdx);

// 271             /* Check if s1 pointer at end.  */
//    0x00007ffff7d99dc5 <+453>:   72 b9   jb     0x7ffff7d99d80 <__memcmp_avx2_movbe+384>
    __ jb_b(L_loop_4x_vec);

// 272             cmpq    %rdx, %rdi
// 273             jb      L(loop_4x_vec)
//    0x00007ffff7d99dc7 <+455>:   48 29 d7        sub    rdi,rdx
    __ subq(rdi, rdx);

// 274
// 275             subq    %rdx, %rdi
//    0x00007ffff7d99dca <+458>:   83 ff 60        cmp    edi,0x60
    __ cmpl(rdi, 0x60);

// 276             /* rdi has 4 * VEC_SIZE - remaining length.  */
//    0x00007ffff7d99dcd <+461>:   73 61   jae    0x7ffff7d99e30 <__memcmp_avx2_movbe+560>
    __ jae_b(L_8x_last_1x_vec);

// 277             cmpl    $(VEC_SIZE * 3), %edi
// 278             jae     L(8x_last_1x_vec)
//    0x00007ffff7d99dcf <+463>:   c5 fe 6f 5c 16 40       vmovdqu ymm3,YMMWORD PTR [rsi+rdx*1+0x40]
    __ vmovdqu(xmm3, Address(rsi, rdx, Address::times_1, 0x40));

// 279             /* Load regardless of branch.  */
//    0x00007ffff7d99dd5 <+469>:   83 ff 40        cmp    edi,0x40
    __ cmpl(rdi, 0x40);

// 280             vmovdqu (VEC_SIZE * 2)(%rsi, %rdx), %ymm3
//    0x00007ffff7d99dd8 <+472>:   73 46   jae    0x7ffff7d99e20 <__memcmp_avx2_movbe+544>
    __ jae_b(L_8x_last_2x_vec);

// 281             cmpl    $(VEC_SIZE * 2), %edi
// 282             jae     L(8x_last_2x_vec)
// 283
//    0x00007ffff7d99dda <+474>:   c5 fe 6f 0c 16  vmovdqu ymm1,YMMWORD PTR [rsi+rdx*1]
    __ vmovdqu(xmm1, Address(rsi, rdx, Address::times_1));

// 284             /* Check last 4 VEC.  */
//    0x00007ffff7d99ddf <+479>:   c5 f5 74 0a     vpcmpeqb ymm1,ymm1,YMMWORD PTR [rdx]
    __ vpcmpeqb(xmm1, xmm1, Address(rdx, 0), Assembler::AVX_256bit);

// 285             vmovdqu (%rsi, %rdx), %ymm1
// 286             VPCMPEQ (%rdx), %ymm1, %ymm1
//    0x00007ffff7d99de3 <+483>:   c5 fe 6f 54 16 20       vmovdqu ymm2,YMMWORD PTR [rsi+rdx*1+0x20]
    __ vmovdqu(xmm2, Address(rsi, rdx, Address::times_1, 0x20));

// 287
//    0x00007ffff7d99de9 <+489>:   c5 ed 74 52 20  vpcmpeqb ymm2,ymm2,YMMWORD PTR [rdx+0x20]
    __ vpcmpeqb(xmm2, xmm2, Address(rdx, 0x20), Assembler::AVX_256bit);

// 288             vmovdqu VEC_SIZE(%rsi, %rdx), %ymm2
// 289             VPCMPEQ VEC_SIZE(%rdx), %ymm2, %ymm2
//    0x00007ffff7d99dee <+494>:   c5 e5 74 5a 40  vpcmpeqb ymm3,ymm3,YMMWORD PTR [rdx+0x40]
    __ vpcmpeqb(xmm3, xmm3, Address(rdx, 0x40), Assembler::AVX_256bit);

// 290
// 291             VPCMPEQ (VEC_SIZE * 2)(%rdx), %ymm3, %ymm3
//    0x00007ffff7d99df3 <+499>:   c5 fe 6f 64 16 60       vmovdqu ymm4,YMMWORD PTR [rsi+rdx*1+0x60]
    __ vmovdqu(xmm4, Address(rsi, rdx, Address::times_1, 0x60));

// 292
//    0x00007ffff7d99df9 <+505>:   c5 dd 74 62 60  vpcmpeqb ymm4,ymm4,YMMWORD PTR [rdx+0x60]
    __ vpcmpeqb(xmm4, xmm4, Address(rdx, 0x60), Assembler::AVX_256bit);

// 293             vmovdqu (VEC_SIZE * 3)(%rsi, %rdx), %ymm4
// 294             VPCMPEQ (VEC_SIZE * 3)(%rdx), %ymm4, %ymm4
//    0x00007ffff7d99dfe <+510>:   c5 ed db e9     vpand  ymm5,ymm2,ymm1
    __ vpand(xmm5, xmm2, xmm1, Assembler::AVX_256bit);

// 295
//    0x00007ffff7d99e02 <+514>:   c5 dd db f3     vpand  ymm6,ymm4,ymm3
    __ vpand(xmm6, xmm4, xmm3, Assembler::AVX_256bit);

// 296             vpand   %ymm1, %ymm2, %ymm5
//    0x00007ffff7d99e06 <+518>:   c5 cd db fd     vpand  ymm7,ymm6,ymm5
    __ vpand(xmm7, xmm6, xmm5, Assembler::AVX_256bit);

// 297             vpand   %ymm3, %ymm4, %ymm6
//    0x00007ffff7d99e0a <+522>:   c5 fd d7 cf     vpmovmskb ecx,ymm7
    __ vpmovmskb(rcx, xmm7, Assembler::AVX_256bit);

// 298             vpand   %ymm5, %ymm6, %ymm7
// 299             vpmovmskb %ymm7, %ecx
//    0x00007ffff7d99e0e <+526>:   48 89 d7        mov    rdi,rdx
    __ movq(rdi, rdx);

// 300             /* Restore s1 pointer to rdi.  */
//    0x00007ffff7d99e11 <+529>:   ff c1   inc    ecx
    __ incrementl(rcx);

// 301             movq    %rdx, %rdi
//    0x00007ffff7d99e13 <+531>:   0f 85 27 ff ff ff       jne    0x7ffff7d99d40 <__memcmp_avx2_movbe+320>
    __ jne(L_8x_return_vec_0_1_2_3);

// 302             incl    %ecx
// 303             jnz     L(8x_return_vec_0_1_2_3)
//    0x00007ffff7d99e19 <+537>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99e1c <+540>:   c3      ret
//    0x00007ffff7d99e1d <+541>:   0f 1f 00        nop    DWORD PTR [rax]
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
// 310             /* Check second to last VEC. rdx store end pointer of s1 and
// 311                ymm3 has already been loaded with second to last VEC from s2.
//    0x00007ffff7d99e20 <+544>:   c5 e5 74 5a 40  vpcmpeqb ymm3,ymm3,YMMWORD PTR [rdx+0x40]
    __ vpcmpeqb(xmm3, xmm3, Address(rdx, 0x40), Assembler::AVX_256bit);

// 312              */
//    0x00007ffff7d99e25 <+549>:   c5 fd d7 c3     vpmovmskb eax,ymm3
    __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);

// 313             VPCMPEQ (VEC_SIZE * 2)(%rdx), %ymm3, %ymm3
//    0x00007ffff7d99e29 <+553>:   ff c0   inc    eax
    __ incrementl(rax);

// 314             vpmovmskb %ymm3, %eax
//    0x00007ffff7d99e2b <+555>:   75 53   jne    0x7ffff7d99e80 <__memcmp_avx2_movbe+640>
//    0x00007ffff7d99e2d <+557>:   0f 1f 00        nop    DWORD PTR [rax]
    __ jne_b(L_8x_return_vec_2);
    __ align(16);

    __ bind(L_8x_last_1x_vec);

// 315             incl    %eax
// 316             jnz     L(8x_return_vec_2)
// 317             /* Check last VEC.  */
// 318             .p2align 4
//    0x00007ffff7d99e30 <+560>:   c5 fe 6f 64 16 60       vmovdqu ymm4,YMMWORD PTR [rsi+rdx*1+0x60]
    __ vmovdqu(xmm4, Address(rsi, rdx, Address::times_1, 0x60));

// 319     L(8x_last_1x_vec):
//    0x00007ffff7d99e36 <+566>:   c5 dd 74 62 60  vpcmpeqb ymm4,ymm4,YMMWORD PTR [rdx+0x60]
    __ vpcmpeqb(xmm4, xmm4, Address(rdx, 0x60), Assembler::AVX_256bit);

// 320             vmovdqu (VEC_SIZE * 3)(%rsi, %rdx), %ymm4
//    0x00007ffff7d99e3b <+571>:   c5 fd d7 c4     vpmovmskb eax,ymm4
    __ vpmovmskb(rax, xmm4, Assembler::AVX_256bit);

// 321             VPCMPEQ (VEC_SIZE * 3)(%rdx), %ymm4, %ymm4
//    0x00007ffff7d99e3f <+575>:   ff c0   inc    eax
    __ incrementl(rax);

// 322             vpmovmskb %ymm4, %eax
//    0x00007ffff7d99e41 <+577>:   75 41   jne    0x7ffff7d99e84 <__memcmp_avx2_movbe+644>
    __ jne_b(L_8x_return_vec_3);

// 323             incl    %eax
//    0x00007ffff7d99e43 <+579>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99e46 <+582>:   c3      ret
//    0x00007ffff7d99e47 <+583>:   66 0f 1f 84 00 00 00 00 00      nop    WORD PTR [rax+rax*1+0x0]
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_last_2x_vec);

// 324             jnz     L(8x_return_vec_3)
// 325             VZEROUPPER_RETURN
// 326
// 327             .p2align 4
// 328     L(last_2x_vec):
//    0x00007ffff7d99e50 <+592>:   c5 fe 6f 4c 16 c0       vmovdqu ymm1,YMMWORD PTR [rsi+rdx*1-0x40]
    __ vmovdqu(xmm1, Address(rsi, rdx, Address::times_1, -0x40));

// 329             /* Check second to last VEC.  */
//    0x00007ffff7d99e56 <+598>:   c5 f5 74 4c 17 c0       vpcmpeqb ymm1,ymm1,YMMWORD PTR [rdi+rdx*1-0x40]
    __ vpcmpeqb(xmm1, xmm1, Address(rdi, rdx, Address::times_1, -0x40), Assembler::AVX_256bit);

// 330             vmovdqu -(VEC_SIZE * 2)(%rsi, %rdx), %ymm1
//    0x00007ffff7d99e5c <+604>:   c5 fd d7 c1     vpmovmskb eax,ymm1
    __ vpmovmskb(rax, xmm1, Assembler::AVX_256bit);

// 331             VPCMPEQ -(VEC_SIZE * 2)(%rdi, %rdx), %ymm1, %ymm1
//    0x00007ffff7d99e60 <+608>:   ff c0   inc    eax
    __ incrementl(rax);

// 332             vpmovmskb %ymm1, %eax
//    0x00007ffff7d99e62 <+610>:   75 3c   jne    0x7ffff7d99ea0 <__memcmp_avx2_movbe+672>
    __ jne_b(L_return_vec_1_end);

    __ bind(L_last_1x_vec);

// 333             incl    %eax
// 334             jnz     L(return_vec_1_end)
// 335             /* Check last VEC.  */
//    0x00007ffff7d99e64 <+612>:   c5 fe 6f 4c 16 e0       vmovdqu ymm1,YMMWORD PTR [rsi+rdx*1-0x20]
    __ vmovdqu(xmm1, Address(rsi, rdx, Address::times_1, -0x20));

// 336     L(last_1x_vec):
//    0x00007ffff7d99e6a <+618>:   c5 f5 74 4c 17 e0       vpcmpeqb ymm1,ymm1,YMMWORD PTR [rdi+rdx*1-0x20]
    __ vpcmpeqb(xmm1, xmm1, Address(rdi, rdx, Address::times_1, -0x20), Assembler::AVX_256bit);

// 337             vmovdqu -(VEC_SIZE * 1)(%rsi, %rdx), %ymm1
//    0x00007ffff7d99e70 <+624>:   c5 fd d7 c1     vpmovmskb eax,ymm1
    __ vpmovmskb(rax, xmm1, Assembler::AVX_256bit);

// 338             VPCMPEQ -(VEC_SIZE * 1)(%rdi, %rdx), %ymm1, %ymm1
//    0x00007ffff7d99e74 <+628>:   ff c0   inc    eax
    __ incrementl(rax);

// 339             vpmovmskb %ymm1, %eax
//    0x00007ffff7d99e76 <+630>:   75 48   jne    0x7ffff7d99ec0 <__memcmp_avx2_movbe+704>
    __ jne_b(L_return_vec_0_end);

// 340             incl    %eax
//    0x00007ffff7d99e78 <+632>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99e7b <+635>:   c3      ret
//    0x00007ffff7d99e7c <+636>:   0f 1f 40 00     nop    DWORD PTR [rax+0x0]
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_8x_return_vec_2);

// 341             jnz     L(return_vec_0_end)
// 342             VZEROUPPER_RETURN
// 343
// 344             .p2align 4
//    0x00007ffff7d99e80 <+640>:   48 83 ea 20     sub    rdx,0x20
    __ subq(rdx, 0x20);
    __ bind(L_8x_return_vec_3);

// 345     L(8x_return_vec_2):
// 346             subq    $VEC_SIZE, %rdx
//    0x00007ffff7d99e84 <+644>:   f3 0f bc c0     tzcnt  eax,eax
    __ tzcntl(rax, rax);

// 347     L(8x_return_vec_3):
//    0x00007ffff7d99e88 <+648>:   48 01 d0        add    rax,rdx
    __ addq(rax, rdx);

// 348             tzcntl  %eax, %eax
// 349             addq    %rdx, %rax
// 350     # ifdef USE_AS_WMEMCMP
// 351             movl    (VEC_SIZE * 3)(%rax), %ecx
// 352             xorl    %edx, %edx
// 353             cmpl    (VEC_SIZE * 3)(%rsi, %rax), %ecx
// 354             setg    %dl
// 355             leal    -1(%rdx, %rdx), %eax
//    0x00007ffff7d99e8b <+651>:   0f b6 4c 06 60  movzx  ecx,BYTE PTR [rsi+rax*1+0x60]
    __ movzbl(rcx, Address(rsi, rax, Address::times_1, 0x60));

// 356     # else
//    0x00007ffff7d99e90 <+656>:   0f b6 40 60     movzx  eax,BYTE PTR [rax+0x60]
    __ movzbl(rax, Address(rax, 0x60));

// 357             movzbl  (VEC_SIZE * 3)(%rsi, %rax), %ecx
//    0x00007ffff7d99e94 <+660>:   29 c8   sub    eax,ecx
    __ subl(rax, rcx);

// 358             movzbl  (VEC_SIZE * 3)(%rax), %eax
// 359             subl    %ecx, %eax
//    0x00007ffff7d99e96 <+662>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99e99 <+665>:   c3      ret
//    0x00007ffff7d99e9a <+666>:   66 0f 1f 44 00 00       nop    WORD PTR [rax+rax*1+0x0]
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_return_vec_1_end);

// 360     # endif
// 361             VZEROUPPER_RETURN
// 362
// 363             .p2align 4
//    0x00007ffff7d99ea0 <+672>:   f3 0f bc c0     tzcnt  eax,eax
    __ tzcntl(rax, rax);

// 364     L(return_vec_1_end):
//    0x00007ffff7d99ea4 <+676>:   01 d0   add    eax,edx
    __ addl(rax, rdx);

// 365             tzcntl  %eax, %eax
// 366             addl    %edx, %eax
// 367     # ifdef USE_AS_WMEMCMP
// 368             movl    -(VEC_SIZE * 2)(%rdi, %rax), %ecx
// 369             xorl    %edx, %edx
// 370             cmpl    -(VEC_SIZE * 2)(%rsi, %rax), %ecx
// 371             setg    %dl
// 372             leal    -1(%rdx, %rdx), %eax
//    0x00007ffff7d99ea6 <+678>:   0f b6 4c 06 c0  movzx  ecx,BYTE PTR [rsi+rax*1-0x40]
    __ movzbl(rcx, Address(rsi, rax, Address::times_1, -0x40));

// 373     # else
//    0x00007ffff7d99eab <+683>:   0f b6 44 07 c0  movzx  eax,BYTE PTR [rdi+rax*1-0x40]
    __ movzbl(rax, Address(rdi, rax, Address::times_1, -0x40));

// 374             movzbl  -(VEC_SIZE * 2)(%rsi, %rax), %ecx
//    0x00007ffff7d99eb0 <+688>:   29 c8   sub    eax,ecx
    __ subl(rax, rcx);

// 375             movzbl  -(VEC_SIZE * 2)(%rdi, %rax), %eax
// 376             subl    %ecx, %eax
//    0x00007ffff7d99eb2 <+690>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99eb5 <+693>:   c3      ret
//    0x00007ffff7d99eb6 <+694>:   66 2e 0f 1f 84 00 00 00 00 00   cs nop WORD PTR [rax+rax*1+0x0]
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_return_vec_0_end);

// 377     # endif
// 378             VZEROUPPER_RETURN
// 379
// 380             .p2align 4
//    0x00007ffff7d99ec0 <+704>:   f3 0f bc c0     tzcnt  eax,eax
    __ tzcntl(rax, rax);

// 381     L(return_vec_0_end):
//    0x00007ffff7d99ec4 <+708>:   01 d0   add    eax,edx
    __ addl(rax, rdx);

// 382             tzcntl  %eax, %eax
// 383             addl    %edx, %eax
// 384     # ifdef USE_AS_WMEMCMP
// 385             movl    -VEC_SIZE(%rdi, %rax), %ecx
// 386             xorl    %edx, %edx
// 387             cmpl    -VEC_SIZE(%rsi, %rax), %ecx
// 388             setg    %dl
// 389             leal    -1(%rdx, %rdx), %eax
//    0x00007ffff7d99ec6 <+710>:   0f b6 4c 06 e0  movzx  ecx,BYTE PTR [rsi+rax*1-0x20]
    __ movzbl(rcx, Address(rsi, rax, Address::times_1, -0x20));

// 390     # else
//    0x00007ffff7d99ecb <+715>:   0f b6 44 07 e0  movzx  eax,BYTE PTR [rdi+rax*1-0x20]
    __ movzbl(rax, Address(rdi, rax, Address::times_1, -0x20));

// 391             movzbl  -VEC_SIZE(%rsi, %rax), %ecx
//    0x00007ffff7d99ed0 <+720>:   29 c8   sub    eax,ecx
    __ subl(rax, rcx);

// 392             movzbl  -VEC_SIZE(%rdi, %rax), %eax
// 393             subl    %ecx, %eax
//    0x00007ffff7d99ed2 <+722>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99ed5 <+725>:   c3      ret
//    0x00007ffff7d99ed6 <+726>:   66 2e 0f 1f 84 00 00 00 00 00   cs nop WORD PTR [rax+rax*1+0x0]
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_less_vec);

// 394     # endif
// 395             VZEROUPPER_RETURN
// 396
// 397             .p2align 4
// 398     L(less_vec):
// 399             /* Check if one or less CHAR. This is necessary for size = 0 but
//    0x00007ffff7d99ee0 <+736>:   83 fa 01        cmp    edx,0x1
    __ cmpl(rdx, 0x1);

// 400                is also faster for size = CHAR_SIZE.  */
//    0x00007ffff7d99ee3 <+739>:   76 7b   jbe    0x7ffff7d99f60 <__memcmp_avx2_movbe+864>
    __ jbe_b(L_one_or_less);

// 401             cmpl    $CHAR_SIZE, %edx
// 402             jbe     L(one_or_less)
// 403
// 404             /* Check if loading one VEC from either s1 or s2 could cause a
// 405                page cross. This can have false positives but is by far the
//    0x00007ffff7d99ee5 <+741>:   89 f8   mov    eax,edi
    __ movl(rax, rdi);

// 406                fastest method.  */
//    0x00007ffff7d99ee7 <+743>:   09 f0   or     eax,esi
    __ orl(rax, rsi);

// 407             movl    %edi, %eax
//    0x00007ffff7d99ee9 <+745>:   25 ff 0f 00 00  and    eax,0xfff
    __ andl(rax, 0xfff);

// 408             orl     %esi, %eax
//    0x00007ffff7d99eee <+750>:   3d e0 0f 00 00  cmp    eax,0xfe0
    __ cmpl(rax, 0xfe0);

// 409             andl    $(PAGE_SIZE - 1), %eax
//    0x00007ffff7d99ef3 <+755>:   7f 2b   jg     0x7ffff7d99f20 <__memcmp_avx2_movbe+800>
    __ jg_b(L_page_cross_less_vec);

// 410             cmpl    $(PAGE_SIZE - VEC_SIZE), %eax
// 411             jg      L(page_cross_less_vec)
// 412
//    0x00007ffff7d99ef5 <+757>:   c5 fe 6f 16     vmovdqu ymm2,YMMWORD PTR [rsi]
    __ vmovdqu(xmm2, Address(rsi, 0));

// 413             /* No page cross possible.  */
//    0x00007ffff7d99ef9 <+761>:   c5 ed 74 17     vpcmpeqb ymm2,ymm2,YMMWORD PTR [rdi]
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0), Assembler::AVX_256bit);

// 414             vmovdqu (%rsi), %ymm2
//    0x00007ffff7d99efd <+765>:   c5 fd d7 c2     vpmovmskb eax,ymm2
    __ vpmovmskb(rax, xmm2, Assembler::AVX_256bit);

// 415             VPCMPEQ (%rdi), %ymm2, %ymm2
//    0x00007ffff7d99f01 <+769>:   ff c0   inc    eax
    __ incrementl(rax);

// 416             vpmovmskb %ymm2, %eax
// 417             incl    %eax
// 418             /* Result will be zero if s1 and s2 match. Otherwise first set
//    0x00007ffff7d99f03 <+771>:   c4 e2 68 f5 d0  bzhi   edx,eax,edx
    __ bzhil(rdx, rax, rdx);

// 419                bit will be first mismatch.  */
//    0x00007ffff7d99f08 <+776>:   0f 85 d2 fd ff ff       jne    0x7ffff7d99ce0 <__memcmp_avx2_movbe+224>
    __ jne(L_return_vec_0);

// 420             bzhil   %edx, %eax, %edx
//    0x00007ffff7d99f0e <+782>:   31 c0   xor    eax,eax
    __ xorl(rax, rax);

// 421             jnz     L(return_vec_0)
//    0x00007ffff7d99f10 <+784>:   c5 f8 77        vzeroupper
//    0x00007ffff7d99f13 <+787>:   c3      ret
//    0x00007ffff7d99f14 <+788>:   66 66 2e 0f 1f 84 00 00 00 00 00        data16 cs nop WORD PTR [rax+rax*1+0x0]
//    0x00007ffff7d99f1f <+799>:   90      nop
    __ vzeroupper();
    __ ret(0);
    __ align(16);

    __ bind(L_page_cross_less_vec);

// 422             xorl    %eax, %eax
// 423             VZEROUPPER_RETURN
// 424
// 425             .p2align 4
// 426     L(page_cross_less_vec):
// 427             /* if USE_AS_WMEMCMP it can only be 0, 4, 8, 12, 16, 20, 24, 28
//    0x00007ffff7d99f20 <+800>:   83 fa 10        cmp    edx,0x10
    __ cmpl(rdx, 0x10);

// 428                bytes.  */
//    0x00007ffff7d99f23 <+803>:   0f 83 a7 00 00 00       jae    0x7ffff7d99fd0 <__memcmp_avx2_movbe+976>
    __ jae(L_between_16_31);

// 429             cmpl    $16, %edx
// 430             jae     L(between_16_31)
//    0x00007ffff7d99f29 <+809>:   83 fa 08        cmp    edx,0x8
    __ cmpl(rdx, 0x8);

// 431     # ifndef USE_AS_WMEMCMP
//    0x00007ffff7d99f2c <+812>:   73 42   jae    0x7ffff7d99f70 <__memcmp_avx2_movbe+880>
    __ jae_b(L_between_8_15);

// 432             cmpl    $8, %edx
//    0x00007ffff7d99f2e <+814>:   83 fa 04        cmp    edx,0x4
    __ cmpl(rdx, 0x4);

// 433             jae     L(between_8_15)
//    0x00007ffff7d99f31 <+817>:   0f 83 d9 00 00 00       jae    0x7ffff7d9a010 <__memcmp_avx2_movbe+1040>
    __ jae(L_between_2_3);

// 434             /* Fall through for [4, 7].  */
// 435             cmpl    $4, %edx
// 436             jb      L(between_2_3)
//    0x00007ffff7d99f37 <+823>:   0f b7 07        movzx  eax,WORD PTR [rdi]
    __ movzbl(rax, Address(rdi, 0));

// 437
//    0x00007ffff7d99f3a <+826>:   0f b7 0e        movzx  ecx,WORD PTR [rsi]
    __ movzbl(rcx, Address(rsi, 0));

// 438             movbe   (%rdi), %eax
// 439             movbe   (%rsi), %ecx

//  33b:	48 c1 e0 20          	shl    rax,0x20
   __ shlq(rax, 0x20);
// 	shlq	$32, %rcx
//  33f:	48 c1 e1 20          	shl    rcx,0x20
    __ shlq(rcx, 0x20);
// 	movbe	-4(%rdi, %rdx), %edi
//  343:	0f 38 f0 7c 17 fc    	movbe  edi,DWORD PTR [rdi+rdx*1-0x4]
    __ movzbl(rdi, Address(rdi, rdx, Address::times_1, -0x4));
// 	movbe	-4(%rsi, %rdx), %esi
//  349:	0f 38 f0 74 16 fc    	movbe  esi,DWORD PTR [rsi+rdx*1-0x4]
    __ movzbl(rsi, Address(rsi, rdx, Address::times_1, -0x4));
// 	orq	%rdi, %rax
//  34f:	48 09 f8             	or     rax,rdi
    __ orq(rax, rdi);
// 	orq	%rsi, %rcx
//  352:	48 09 f1             	or     rcx,rsi
    __ orq(rcx, rsi);
// 	subq	%rcx, %rax
//  355:	48 29 c8             	sub    rax,rcx
    __ subq(rax, rcx);
// 	/* Fast path for return zero.  */
// 	jnz	L(ret_nonzero)
//  358:	75 16                	jne    370 <__memcmp_avx2_movbe+0x370>
    __ jne_b(L_ret_nonzero);
// 	/* No ymm register was touched.  */
// 	ret
//  35a:	c3                   	ret
//  35b:	0f 1f 44 00 00       	nop    DWORD PTR [rax+rax*1+0x0]
    __ ret(0);
    __ align(16);

    __ bind(L_one_or_less);

// 	.p2align 4
// L(one_or_less):
// 	jb	L(zero)
//  360:	72 14                	jb     376 <__memcmp_avx2_movbe+0x376>
    __ jb_b(L_zero);
// 	movzbl	(%rsi), %ecx
//  362:	0f b6 0e             	movzx  ecx,BYTE PTR [rsi]
    __ movzbl(rcx, Address(rsi, 0));
// 	movzbl	(%rdi), %eax
//  365:	0f b6 07             	movzx  eax,BYTE PTR [rdi]
    __ movzbl(rax, Address(rdi, 0));
// 	subl	%ecx, %eax
//  368:	29 c8                	sub    eax,ecx
    __ subl(rax, rcx);
// 	/* No ymm register was touched.  */
// 	ret
//  36a:	c3                   	ret
//  36b:	0f 1f 44 00 00       	nop    DWORD PTR [rax+rax*1+0x0]
    __ ret(0);
    __ p2align(16, 5);

    __ bind(L_ret_nonzero);

// 	.p2align 4,, 5
// L(ret_nonzero):
// 	sbbl	%eax, %eax
//  370:	19 c0                	sbb    eax,eax
    __ sbbl(rax, rax);
// 	orl	$1, %eax
//  372:	83 c8 01             	or     eax,0x1
    __ orl(rax, 0x1);
// 	/* No ymm register was touched.  */
// 	ret
//  375:	c3                   	ret
    __ ret(0);
    __ p2align(16, 2);

    __ bind(L_zero);

// 	.p2align 4,, 2
// L(zero):
// 	xorl	%eax, %eax
//  376:	31 c0                	xor    eax,eax
    __ xorl(rax, rax);
// 	/* No ymm register was touched.  */
// 	ret
//  378:	c3                   	ret
//  379:	0f 1f 80 00 00 00 00 	nop    DWORD PTR [rax+0x0]
    __ ret(0);
    __ align(16);

    __ bind(L_between_8_15);

// 	.p2align 4
// L(between_8_15):
// 	movbe	(%rdi), %rax
//  380:	48 0f 38 f0 07       	movbe  rax,QWORD PTR [rdi]
    __ movzbl(rax, Address(rdi, 0));
// 	movbe	(%rsi), %rcx
//  385:	48 0f 38 f0 0e       	movbe  rcx,QWORD PTR [rsi]
    __ movzbl(rcx, Address(rsi, 0));
// 	subq	%rcx, %rax
//  38a:	48 29 c8             	sub    rax,rcx
    __ subq(rax, rcx);
// 	jnz	L(ret_nonzero)
//  38d:	75 e1                	jne    370 <__memcmp_avx2_movbe+0x370>
    __ jne_b(L_ret_nonzero);
// 	movbe	-8(%rdi, %rdx), %rax
//  38f:	48 0f 38 f0 44 17 f8 	movbe  rax,QWORD PTR [rdi+rdx*1-0x8]
    __ movzbl(rax, Address(rdi, rdx, Address::times_1, -0x8));
// 	movbe	-8(%rsi, %rdx), %rcx
//  396:	48 0f 38 f0 4c 16 f8 	movbe  rcx,QWORD PTR [rsi+rdx*1-0x8]
    __ movzbl(rcx, Address(rsi, rdx, Address::times_1, -0x8));
// 	subq	%rcx, %rax
//  39d:	48 29 c8             	sub    rax,rcx
    __ subq(rax, rcx);
// 	/* Fast path for return zero.  */
// 	jnz	L(ret_nonzero)
//  3a0:	75 ce                	jne    370 <__memcmp_avx2_movbe+0x370>
    __ jne_b(L_ret_nonzero);
// 	/* No ymm register was touched.  */
// 	ret
//  3a2:	c3                   	ret
// # endif
    __ ret(0);
    __ p2align(16, 10);

    __ bind(L_between_16_31);

// 	.p2align 4,, 10
// L(between_16_31):
// 	/* From 16 to 31 bytes.  No branch when size == 16.  */
// 	vmovdqu	(%rsi), %xmm2
//  3a3:	c5 fa 6f 16          	vmovdqu xmm2,XMMWORD PTR [rsi]
    __ movdqu(xmm2, Address(rsi, 0));
// 	VPCMPEQ	(%rdi), %xmm2, %xmm2
//  3a7:	c5 e9 74 17          	vpcmpeqb xmm2,xmm2,XMMWORD PTR [rdi]
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0), Assembler::AVX_128bit);
// 	vpmovmskb %xmm2, %eax
//  3ab:	c5 f9 d7 c2          	vpmovmskb eax,xmm2
    __ vpmovmskb(rax, xmm2, Assembler::AVX_128bit);
// 	subl	$0xffff, %eax
//  3af:	2d ff ff 00 00       	sub    eax,0xffff
    __ subl(rax, 0xffff);
// 	jnz	L(return_vec_0)
//  3b4:	0f 85 26 fd ff ff    	jne    e0 <__memcmp_avx2_movbe+0xe0>
    __ jne(L_return_vec_0);

// 	/* Use overlapping loads to avoid branches.  */

// 	vmovdqu	-16(%rsi, %rdx), %xmm2
//  3ba:	c5 fa 6f 54 16 f0    	vmovdqu xmm2,XMMWORD PTR [rsi+rdx*1-0x10]
    __ movdqu(xmm2, Address(rsi, rdx, Address::times_1, -0x10));
// 	leaq	-16(%rdi, %rdx), %rdi
//  3c0:	48 8d 7c 17 f0       	lea    rdi,[rdi+rdx*1-0x10]
    __ leaq(rdi, Address(rdi, rdx, Address::times_1, -0x10));
// 	leaq	-16(%rsi, %rdx), %rsi
//  3c5:	48 8d 74 16 f0       	lea    rsi,[rsi+rdx*1-0x10]
    __ leaq(rsi, Address(rsi, rdx, Address::times_1, -0x10));
// 	VPCMPEQ	(%rdi), %xmm2, %xmm2
//  3ca:	c5 e9 74 17          	vpcmpeqb xmm2,xmm2,XMMWORD PTR [rdi]
    __ vpcmpeqb(xmm2, xmm2, Address(rdi, 0), Assembler::AVX_128bit);
// 	vpmovmskb %xmm2, %eax
//  3ce:	c5 f9 d7 c2          	vpmovmskb eax,xmm2
    __ vpmovmskb(rax, xmm2, Assembler::AVX_128bit);
// 	subl	$0xffff, %eax
//  3d2:	2d ff ff 00 00       	sub    eax,0xffff
    __ subl(rax, 0xffff);
// 	/* Fast path for return zero.  */
// 	jnz	L(return_vec_0)
//  3d7:	0f 85 03 fd ff ff    	jne    e0 <__memcmp_avx2_movbe+0xe0>
    __ jne(L_return_vec_0);
// 	/* No ymm register was touched.  */
// 	ret
//  3dd:	c3                   	ret
//  3de:	66 90                	xchg   ax,ax
// # else
    __ ret(0);
    __ align(16);

    __ bind(L_between_2_3);

// 	.p2align 4
// L(between_2_3):
// 	/* Load as big endian to avoid branches.  */
// 	movzwl	(%rdi), %eax
//  3e0:	0f b7 07             	movzx  eax,WORD PTR [rdi]
    __ movzwl(rax, Address(rdi, 0));
// 	movzwl	(%rsi), %ecx
//  3e3:	0f b7 0e             	movzx  ecx,WORD PTR [rsi]
    __ movzwl(rcx, Address(rsi, 0));
// 	bswap	%eax
//  3e6:	0f c8                	bswap  eax
    __ bswapl(rax);
// 	bswap	%ecx
//  3e8:	0f c9                	bswap  ecx
    __ bswapl(rcx);
// 	shrl	%eax
//  3ea:	d1 e8                	shr    eax,1
    __ shrl(rax, 1);
// 	shrl	%ecx
//  3ec:	d1 e9                	shr    ecx,1
    __ shrl(rcx, 1);
// 	movzbl	-1(%rdi, %rdx), %edi
//  3ee:	0f b6 7c 17 ff       	movzx  edi,BYTE PTR [rdi+rdx*1-0x1]
    __ movzbl(rdi, Address(rdi, rdx, Address::times_1, -0x1));
// 	movzbl	-1(%rsi, %rdx), %esi
//  3f3:	0f b6 74 16 ff       	movzx  esi,BYTE PTR [rsi+rdx*1-0x1]
    __ movzbl(rsi, Address(rsi, rdx, Address::times_1, -0x1));
// 	orl	%edi, %eax
//  3f8:	09 f8                	or     eax,edi
    __ orl(rax, rdi);
// 	orl	%esi, %ecx
//  3fa:	09 f1                	or     ecx,esi
    __ orl(rcx, rsi);
// 	/* Subtraction is okay because the upper bit is zero.  */
// 	subl	%ecx, %eax
//  3fc:	29 c8                	sub    eax,ecx
    __ subl(rax, rcx);
// 	/* No ymm register was touched.  */
// 	ret
//  3fe:	c3                   	ret
    __ ret(0);

// End of assembler dump.


    {
      Label L_return_vzeroupper, L_zero, L_first_vec_x1, L_first_vec_x2;
      Label L_first_vec_x3, L_first_vec_x4, L_aligned_more, L_cross_page_continue;
      Label L_loop_4x_vec, L_last_vec_x0, L_last_vec_x1, L_zero_end, L_cross_page_boundary;

      __ align(CodeEntryAlignment);
      __ bind(strchr_avx2);

// Disassembly of section .text.avx:

// 0000000000000000 <__strchr_avx2>:

// # define VEC_SIZE 32
// # define PAGE_SIZE 4096

// 	.section SECTION(.text),"ax",@progbits
// ENTRY_P2ALIGN (STRCHR, 5)
//    0:	f3 0f 1e fa          	endbr64
// 	/* Broadcast CHAR to YMM0.	*/
// 	vmovd	%esi, %xmm0
//    4:	c5 f9 6e c6          	vmovd  xmm0,esi
      __ movdl(xmm0, rsi);
// 	movl	%edi, %eax
//    8:	89 f8                	mov    eax,edi
      __ movl(rax, rdi);
// 	andl	$(PAGE_SIZE - 1), %eax
//    a:	25 ff 0f 00 00       	and    eax,0xfff
      __ andl(rax, 0xfff);
// 	VPBROADCAST	%xmm0, %ymm0
//    f:	c4 e2 7d 78 c0       	vpbroadcastb ymm0,xmm0
      __ vpbroadcastb(xmm0, xmm0, Assembler::AVX_256bit);
// 	vpxor	%xmm1, %xmm1, %xmm1
//   14:	c5 f1 ef c9          	vpxor  xmm1,xmm1,xmm1
      __ vpxor(xmm1, xmm1, xmm1, Assembler::AVX_128bit);

// 	/* Check if we cross page boundary with one vector load.  */
// 	cmpl	$(PAGE_SIZE - VEC_SIZE), %eax
//   18:	3d e0 0f 00 00       	cmp    eax,0xfe0
      __ cmpl(rax, 0xfe0);
// 	ja	L(cross_page_boundary)
//   1d:	0f 87 dd 01 00 00    	ja     200 <__strchr_avx2+0x200>
      __ ja(L_cross_page_boundary);

// 	/* Check the first VEC_SIZE bytes.	Search for both CHAR and the
// 	   null byte.  */
// 	vmovdqu	(%rdi), %ymm2
//   23:	c5 fe 6f 17          	vmovdqu ymm2,YMMWORD PTR [rdi]
      __ vmovdqu(xmm2, Address(rdi, 0));
// 	VPCMPEQ	%ymm2, %ymm0, %ymm3
//   27:	c5 fd 74 da          	vpcmpeqb ymm3,ymm0,ymm2
      __ vpcmpeqb(xmm3, xmm0, xmm2, Assembler::AVX_256bit);
// 	VPCMPEQ	%ymm2, %ymm1, %ymm2
//   2b:	c5 f5 74 d2          	vpcmpeqb ymm2,ymm1,ymm2
      __ vpcmpeqb(xmm2, xmm1, xmm2, Assembler::AVX_256bit);
// 	vpor	%ymm3, %ymm2, %ymm3
//   2f:	c5 ed eb db          	vpor   ymm3,ymm2,ymm3
      __ vpor(xmm3, xmm2, xmm3, Assembler::AVX_256bit);
// 	vpmovmskb %ymm3, %eax
//   33:	c5 fd d7 c3          	vpmovmskb eax,ymm3
      __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);
// 	testl	%eax, %eax
//   37:	85 c0                	test   eax,eax
      __ testl(rax, rax);
// 	jz	L(aligned_more)
//   39:	0f 84 81 00 00 00    	je     c0 <__strchr_avx2+0xc0>
      __ je(L_aligned_more);
// 	tzcntl	%eax, %eax
//   3f:	f3 0f bc c0          	tzcnt  eax,eax
      __ tzcntl(rax, rax);
// # ifndef USE_AS_STRCHRNUL
// 	/* Found CHAR or the null byte.  */
// 	cmp	(%rdi, %rax), %CHAR_REG
//   43:	40 3a 34 07          	cmp    sil,BYTE PTR [rdi+rax*1]
      __ cmpb(rsi, Address(rdi, rax, Address::times_1));
// 	   null. Since this branch will be 100% predictive of the user
// 	   branch a branch miss here should save what otherwise would
// 	   be branch miss in the user code. Otherwise using a branch 1)
// 	   saves code size and 2) is faster in highly predictable
// 	   environments.  */
// 	jne	L(zero)
//   47:	75 07                	jne    50 <__strchr_avx2+0x50>
      __ jne_b(L_zero);
// # endif
// 	addq	%rdi, %rax
//   49:	48 01 f8             	add    rax,rdi
      __ addq(rax, rdi);
// L(return_vzeroupper):
      __ bind(L_return_vzeroupper);
// 	ZERO_UPPER_VEC_REGISTERS_RETURN
//   4c:	c5 f8 77             	vzeroupper
//   4f:	c3                   	ret
      __ vzeroupper();
      __ ret(0);

// # ifndef USE_AS_STRCHRNUL
// L(zero):
      __ bind(L_zero);
// 	xorl	%eax, %eax
//   50:	31 c0                	xor    eax,eax
      __ xorl(rax, rax);
// 	VZEROUPPER_RETURN
//   52:	c5 f8 77             	vzeroupper
//   55:	c3                   	ret
//   56:	66 2e 0f 1f 84 00 00 	cs nop WORD PTR [rax+rax*1+0x0]
//   5d:	00 00 00
      __ vzeroupper();
      __ ret(0);
      __ align(16);

// 	.p2align 4
// L(first_vec_x1):
      __ bind(L_first_vec_x1);
// 	/* Use bsf to save code size.  */
// 	bsfl	%eax, %eax
//   60:	0f bc c0             	bsf    eax,eax
      __ bsfl(rax, rax);
// 	incq	%rdi
//   63:	48 ff c7             	inc    rdi
      __ incrementq(rdi);
// # ifndef USE_AS_STRCHRNUL
// 	/* Found CHAR or the null byte.	 */
// 	cmp	(%rdi, %rax), %CHAR_REG
//   66:	40 3a 34 07          	cmp    sil,BYTE PTR [rdi+rax*1]
      __ cmpb(rsi, Address(rdi, rax, Address::times_1));
// 	jne	L(zero)
//   6a:	75 e4                	jne    50 <__strchr_avx2+0x50>
      __ jne_b(L_zero);
// # endif
// 	addq	%rdi, %rax
//   6c:	48 01 f8             	add    rax,rdi
      __ addq(rax, rdi);
// 	VZEROUPPER_RETURN
//   6f:	c5 f8 77             	vzeroupper
//   72:	c3                   	ret
      __ vzeroupper();
      __ ret(0);
      __ p2align(16, 10);

// 	.p2align 4,, 10
// L(first_vec_x2):
      __ bind(L_first_vec_x2);
// 	/* Use bsf to save code size.  */
// 	bsfl	%eax, %eax
//   73:	0f bc c0             	bsf    eax,eax
      __ bsfl(rax, rax);
// 	addq	$(VEC_SIZE + 1), %rdi
//   76:	48 83 c7 21          	add    rdi,0x21
      __ addq(rdi, 0x21);
// # ifndef USE_AS_STRCHRNUL
// 	/* Found CHAR or the null byte.	 */
// 	cmp	(%rdi, %rax), %CHAR_REG
//   7a:	40 3a 34 07          	cmp    sil,BYTE PTR [rdi+rax*1]
      __ cmpb(rsi, Address(rdi, rax, Address::times_1));
// 	jne	L(zero)
//   7e:	75 d0                	jne    50 <__strchr_avx2+0x50>
      __ jne_b(L_zero);
// # endif
// 	addq	%rdi, %rax
//   80:	48 01 f8             	add    rax,rdi
      __ addq(rax, rdi);
// 	VZEROUPPER_RETURN
//   83:	c5 f8 77             	vzeroupper
//   86:	c3                   	ret
      __ vzeroupper();
      __ ret(0);
      __ p2align(16, 8);

// 	.p2align 4,, 8
// L(first_vec_x3):
      __ bind(L_first_vec_x3);
// 	/* Use bsf to save code size.  */
// 	bsfl	%eax, %eax
//   87:	0f bc c0             	bsf    eax,eax
      __ bsfl(rax, rax);
// 	addq	$(VEC_SIZE * 2 + 1), %rdi
//   8a:	48 83 c7 41          	add    rdi,0x41
      __ addq(rdi, 0x41);
// # ifndef USE_AS_STRCHRNUL
// 	/* Found CHAR or the null byte.	 */
// 	cmp	(%rdi, %rax), %CHAR_REG
//   8e:	40 3a 34 07          	cmp    sil,BYTE PTR [rdi+rax*1]
      __ cmpb(rsi, Address(rdi, rax, Address::times_1));
// 	jne	L(zero)
//   92:	75 bc                	jne    50 <__strchr_avx2+0x50>
      __ jne_b(L_zero);
// # endif
// 	addq	%rdi, %rax
//   94:	48 01 f8             	add    rax,rdi
      __ addq(rax, rdi);
// 	VZEROUPPER_RETURN
//   97:	c5 f8 77             	vzeroupper
//   9a:	c3                   	ret
//   9b:	0f 1f 44 00 00       	nop    DWORD PTR [rax+rax*1+0x0]
      __ vzeroupper();
      __ ret(0);
      __ p2align(16, 10);

// 	.p2align 4,, 10
// L(first_vec_x4):
      __ bind(L_first_vec_x4);
// 	/* Use bsf to save code size.  */
// 	bsfl	%eax, %eax
//   a0:	0f bc c0             	bsf    eax,eax
      __ bsfl(rax, rax);
// 	addq	$(VEC_SIZE * 3 + 1), %rdi
//   a3:	48 83 c7 61          	add    rdi,0x61
      __ addq(rdi, 0x61);
// # ifndef USE_AS_STRCHRNUL
// 	/* Found CHAR or the null byte.	 */
// 	cmp	(%rdi, %rax), %CHAR_REG
//   a7:	40 3a 34 07          	cmp    sil,BYTE PTR [rdi+rax*1]
      __ cmpb(rsi, Address(rdi, rax, Address::times_1));
// 	jne	L(zero)
//   ab:	75 a3                	jne    50 <__strchr_avx2+0x50>
      __ jne_b(L_zero);
// # endif
// 	addq	%rdi, %rax
//   ad:	48 01 f8             	add    rax,rdi
      __ addq(rax, rdi);
// 	VZEROUPPER_RETURN
//   b0:	c5 f8 77             	vzeroupper
//   b3:	c3                   	ret
//   b4:	66 66 2e 0f 1f 84 00 	data16 cs nop WORD PTR [rax+rax*1+0x0]
//   bb:	00 00 00 00
//   bf:	90                   	nop
      __ vzeroupper();
      __ ret(0);
      __ align(16);

// 	.p2align 4
// L(aligned_more):
      __ bind(L_aligned_more);
// 	/* Align data to VEC_SIZE - 1. This is the same number of
// 	   instructions as using andq -VEC_SIZE but saves 4 bytes of code
// 	   on x4 check.  */
// 	orq	$(VEC_SIZE - 1), %rdi
//   c0:	48 83 cf 1f          	or     rdi,0x1f
      __ orq(rdi, 0x1f);
// L(cross_page_continue):
      __ bind(L_cross_page_continue);
// 	/* Check the next 4 * VEC_SIZE.  Only one VEC_SIZE at a time
// 	   since data is only aligned to VEC_SIZE.  */
// 	vmovdqa	1(%rdi), %ymm2
//   c4:	c5 fd 6f 57 01       	vmovdqa ymm2,YMMWORD PTR [rdi+0x1]
      __ vmovdqu(xmm2, Address(rdi, 0x1));
// 	VPCMPEQ	%ymm2, %ymm0, %ymm3
//   c9:	c5 fd 74 da          	vpcmpeqb ymm3,ymm0,ymm2
      __ vpcmpeqb(xmm3, xmm0, xmm2, Assembler::AVX_256bit);
// 	VPCMPEQ	%ymm2, %ymm1, %ymm2
//   cd:	c5 f5 74 d2          	vpcmpeqb ymm2,ymm1,ymm2
      __ vpcmpeqb(xmm2, xmm1, xmm2, Assembler::AVX_256bit);
// 	vpor	%ymm3, %ymm2, %ymm3
//   d1:	c5 ed eb db          	vpor   ymm3,ymm2,ymm3
      __ vpor(xmm3, xmm2, xmm3, Assembler::AVX_256bit);
// 	vpmovmskb %ymm3, %eax
//   d5:	c5 fd d7 c3          	vpmovmskb eax,ymm3
      __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);
// 	testl	%eax, %eax
//   d9:	85 c0                	test   eax,eax
      __ testl(rax, rax);
// 	jnz	L(first_vec_x1)
//   db:	75 83                	jne    60 <__strchr_avx2+0x60>
      __ jne_b(L_first_vec_x1);

// 	vmovdqa	(VEC_SIZE + 1)(%rdi), %ymm2
//   dd:	c5 fd 6f 57 21       	vmovdqa ymm2,YMMWORD PTR [rdi+0x21]
      __ vmovdqu(xmm2, Address(rdi, 0x21));
// 	VPCMPEQ	%ymm2, %ymm0, %ymm3
//   e2:	c5 fd 74 da          	vpcmpeqb ymm3,ymm0,ymm2
      __ vpcmpeqb(xmm3, xmm0, xmm2, Assembler::AVX_256bit);
// 	VPCMPEQ	%ymm2, %ymm1, %ymm2
//   e6:	c5 f5 74 d2          	vpcmpeqb ymm2,ymm1,ymm2
      __ vpcmpeqb(xmm2, xmm1, xmm2, Assembler::AVX_256bit);
// 	vpor	%ymm3, %ymm2, %ymm3
//   ea:	c5 ed eb db          	vpor   ymm3,ymm2,ymm3
      __ vpor(xmm3, xmm2, xmm3, Assembler::AVX_256bit);
// 	vpmovmskb %ymm3, %eax
//   ee:	c5 fd d7 c3          	vpmovmskb eax,ymm3
      __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);
// 	testl	%eax, %eax
//   f2:	85 c0                	test   eax,eax
      __ testl(rax, rax);
// 	jnz	L(first_vec_x2)
//   f4:	0f 85 79 ff ff ff    	jne    73 <__strchr_avx2+0x73>
      __ jne(L_first_vec_x2);

// 	vmovdqa	(VEC_SIZE * 2 + 1)(%rdi), %ymm2
//   fa:	c5 fd 6f 57 41       	vmovdqa ymm2,YMMWORD PTR [rdi+0x41]
      __ vmovdqu(xmm2, Address(rdi, 0x41));
// 	VPCMPEQ	%ymm2, %ymm0, %ymm3
//   ff:	c5 fd 74 da          	vpcmpeqb ymm3,ymm0,ymm2
      __ vpcmpeqb(xmm3, xmm0, xmm2, Assembler::AVX_256bit);
// 	VPCMPEQ	%ymm2, %ymm1, %ymm2
//  103:	c5 f5 74 d2          	vpcmpeqb ymm2,ymm1,ymm2
      __ vpcmpeqb(xmm2, xmm1, xmm2, Assembler::AVX_256bit);
// 	vpor	%ymm3, %ymm2, %ymm3
//  107:	c5 ed eb db          	vpor   ymm3,ymm2,ymm3
      __ vpor(xmm3, xmm2, xmm3, Assembler::AVX_256bit);
// 	vpmovmskb %ymm3, %eax
//  10b:	c5 fd d7 c3          	vpmovmskb eax,ymm3
      __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);
// 	testl	%eax, %eax
//  10f:	85 c0                	test   eax,eax
      __ testl(rax, rax);
// 	jnz	L(first_vec_x3)
//  111:	0f 85 70 ff ff ff    	jne    87 <__strchr_avx2+0x87>
      __ jne(L_first_vec_x3);

// 	vmovdqa	(VEC_SIZE * 3 + 1)(%rdi), %ymm2
//  117:	c5 fd 6f 57 61       	vmovdqa ymm2,YMMWORD PTR [rdi+0x61]
      __ vmovdqu(xmm2, Address(rdi, 0x61));
// 	VPCMPEQ	%ymm2, %ymm0, %ymm3
//  11c:	c5 fd 74 da          	vpcmpeqb ymm3,ymm0,ymm2
      __ vpcmpeqb(xmm3, xmm0, xmm2, Assembler::AVX_256bit);
// 	VPCMPEQ	%ymm2, %ymm1, %ymm2
//  120:	c5 f5 74 d2          	vpcmpeqb ymm2,ymm1,ymm2
      __ vpcmpeqb(xmm2, xmm1, xmm2, Assembler::AVX_256bit);
// 	vpor	%ymm3, %ymm2, %ymm3
//  124:	c5 ed eb db          	vpor   ymm3,ymm2,ymm3
      __ vpor(xmm3, xmm2, xmm3, Assembler::AVX_256bit);
// 	vpmovmskb %ymm3, %eax
//  128:	c5 fd d7 c3          	vpmovmskb eax,ymm3
      __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);
// 	testl	%eax, %eax
//  12c:	85 c0                	test   eax,eax
      __ testl(rax, rax);
// 	jnz	L(first_vec_x4)
//  12e:	0f 85 6c ff ff ff    	jne    a0 <__strchr_avx2+0xa0>
      __ jne(L_first_vec_x4);
// 	/* Align data to VEC_SIZE * 4 - 1.  */
// 	incq	%rdi
//  134:	48 ff c7             	inc    rdi
      __ incrementq(rdi);
// 	orq	$(VEC_SIZE * 4 - 1), %rdi
//  137:	48 83 cf 7f          	or     rdi,0x7f
//  13b:	0f 1f 44 00 00       	nop    DWORD PTR [rax+rax*1+0x0]
      __ orq(rdi, 0x7f);
// 	.p2align 4
// L(loop_4x_vec):
      __ bind(L_loop_4x_vec);
// 	/* Compare 4 * VEC at a time forward.  */
// 	vmovdqa	1(%rdi), %ymm6
//  140:	c5 fd 6f 77 01       	vmovdqa ymm6,YMMWORD PTR [rdi+0x1]
      __ vmovdqu(xmm6, Address(rdi, 0x1));
// 	vmovdqa	(VEC_SIZE + 1)(%rdi), %ymm7
//  145:	c5 fd 6f 7f 21       	vmovdqa ymm7,YMMWORD PTR [rdi+0x21]
      __ vmovdqu(xmm7, Address(rdi, 0x21));

// 	/* Leaves only CHARS matching esi as 0.	 */
// 	vpxor	%ymm6, %ymm0, %ymm2
//  14a:	c5 fd ef d6          	vpxor  ymm2,ymm0,ymm6
      __ vpxor(xmm2, xmm0, xmm6, Assembler::AVX_256bit);
// 	vpxor	%ymm7, %ymm0, %ymm3
//  14e:	c5 fd ef df          	vpxor  ymm3,ymm0,ymm7
      __ vpxor(xmm3, xmm0, xmm7, Assembler::AVX_256bit);

// 	VPMINU	%ymm2, %ymm6, %ymm2
//  152:	c5 cd da d2          	vpminub ymm2,ymm6,ymm2
      __ vpminub(xmm2, xmm6, xmm2, Assembler::AVX_256bit);
// 	VPMINU	%ymm3, %ymm7, %ymm3
//  156:	c5 c5 da db          	vpminub ymm3,ymm7,ymm3
      __ vpminub(xmm3, xmm7, xmm3, Assembler::AVX_256bit);

// 	vmovdqa	(VEC_SIZE * 2 + 1)(%rdi), %ymm6
//  15a:	c5 fd 6f 77 41       	vmovdqa ymm6,YMMWORD PTR [rdi+0x41]
      __ vmovdqu(xmm6, Address(rdi, 0x41));
// 	vmovdqa	(VEC_SIZE * 3 + 1)(%rdi), %ymm7
//  15f:	c5 fd 6f 7f 61       	vmovdqa ymm7,YMMWORD PTR [rdi+0x61]
      __ vmovdqu(xmm7, Address(rdi, 0x61));

// 	vpxor	%ymm6, %ymm0, %ymm4
//  164:	c5 fd ef e6          	vpxor  ymm4,ymm0,ymm6
      __ vpxor(xmm4, xmm0, xmm6, Assembler::AVX_256bit);
// 	vpxor	%ymm7, %ymm0, %ymm5
//  168:	c5 fd ef ef          	vpxor  ymm5,ymm0,ymm7
      __ vpxor(xmm5, xmm0, xmm7, Assembler::AVX_256bit);

// 	VPMINU	%ymm4, %ymm6, %ymm4
//  16c:	c5 cd da e4          	vpminub ymm4,ymm6,ymm4
      __ vpminub(xmm4, xmm6, xmm4, Assembler::AVX_256bit);
// 	VPMINU	%ymm5, %ymm7, %ymm5
//  170:	c5 c5 da ed          	vpminub ymm5,ymm7,ymm5
      __ vpminub(xmm5, xmm7, xmm5, Assembler::AVX_256bit);

// 	VPMINU	%ymm2, %ymm3, %ymm6
//  174:	c5 e5 da f2          	vpminub ymm6,ymm3,ymm2
      __ vpminub(xmm6, xmm3, xmm2, Assembler::AVX_256bit);
// 	VPMINU	%ymm4, %ymm5, %ymm7
//  178:	c5 d5 da fc          	vpminub ymm7,ymm5,ymm4
      __ vpminub(xmm7, xmm5, xmm4, Assembler::AVX_256bit);

// 	VPMINU	%ymm6, %ymm7, %ymm7
//  17c:	c5 c5 da fe          	vpminub ymm7,ymm7,ymm6
      __ vpminub(xmm7, xmm7, xmm6, Assembler::AVX_256bit);

// 	VPCMPEQ	%ymm7, %ymm1, %ymm7
//  180:	c5 f5 74 ff          	vpcmpeqb ymm7,ymm1,ymm7
      __ vpcmpeqb(xmm7, xmm1, xmm7, Assembler::AVX_256bit);
// 	vpmovmskb %ymm7, %ecx
//  184:	c5 fd d7 cf          	vpmovmskb ecx,ymm7
      __ vpmovmskb(rcx, xmm7, Assembler::AVX_256bit);
// 	subq	$-(VEC_SIZE * 4), %rdi
//  188:	48 83 ef 80          	sub    rdi,0xffffffffffffff80
      __ subq(rdi, -128);
// 	testl	%ecx, %ecx
//  18c:	85 c9                	test   ecx,ecx
      __ testl(rcx, rcx);
// 	jz	L(loop_4x_vec)
//  18e:	74 b0                	je     140 <__strchr_avx2+0x140>
      __ je_b(L_loop_4x_vec);

// 	VPCMPEQ	%ymm2, %ymm1, %ymm2
//  190:	c5 f5 74 d2          	vpcmpeqb ymm2,ymm1,ymm2
      __ vpcmpeqb(xmm2, xmm1, xmm2, Assembler::AVX_256bit);
// 	vpmovmskb %ymm2, %eax
//  194:	c5 fd d7 c2          	vpmovmskb eax,ymm2
      __ vpmovmskb(rax, xmm2, Assembler::AVX_256bit);
// 	testl	%eax, %eax
//  198:	85 c0                	test   eax,eax
      __ testl(rax, rax);
// 	jnz	L(last_vec_x0)
//  19a:	75 34                	jne    1d0 <__strchr_avx2+0x1d0>
      __ jne_b(L_last_vec_x0);


// 	VPCMPEQ	%ymm3, %ymm1, %ymm3
//  19c:	c5 f5 74 db          	vpcmpeqb ymm3,ymm1,ymm3
      __ vpcmpeqb(xmm3, xmm1, xmm3, Assembler::AVX_256bit);
// 	vpmovmskb %ymm3, %eax
//  1a0:	c5 fd d7 c3          	vpmovmskb eax,ymm3
      __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);
// 	testl	%eax, %eax
//  1a4:	85 c0                	test   eax,eax
      __ testl(rax, rax);
// 	jnz	L(last_vec_x1)
//  1a6:	75 3c                	jne    1e4 <__strchr_avx2+0x1e4>
      __ jne_b(L_last_vec_x1);

// 	VPCMPEQ	%ymm4, %ymm1, %ymm4
//  1a8:	c5 f5 74 e4          	vpcmpeqb ymm4,ymm1,ymm4
      __ vpcmpeqb(xmm4, xmm1, xmm4, Assembler::AVX_256bit);
// 	vpmovmskb %ymm4, %eax
//  1ac:	c5 fd d7 c4          	vpmovmskb eax,ymm4
      __ vpmovmskb(rax, xmm4, Assembler::AVX_256bit);
// 	/* rcx has combined result from all 4 VEC. It will only be used
// 	   if the first 3 other VEC all did not contain a match.  */
// 	salq	$32, %rcx
//  1b0:	48 c1 e1 20          	shl    rcx,0x20
      __ shlq(rcx, 0x20);
// 	orq	%rcx, %rax
//  1b4:	48 09 c8             	or     rax,rcx
      __ orq(rax,rcx);
// 	tzcntq	%rax, %rax
//  1b7:	f3 48 0f bc c0       	tzcnt  rax,rax
      __ tzcntq(rax, rax);
// 	subq	$(VEC_SIZE * 2 - 1), %rdi
//  1bc:	48 83 ef 3f          	sub    rdi,0x3f
      __ subq(rdi, 0x3f);
// # ifndef USE_AS_STRCHRNUL
// 	/* Found CHAR or the null byte.	 */
// 	cmp	(%rdi, %rax), %CHAR_REG
//  1c0:	40 3a 34 07          	cmp    sil,BYTE PTR [rdi+rax*1]
      __ cmpb(rsi, Address(rdi, rax, Address::times_1));
// 	jne	L(zero_end)
//  1c4:	75 33                	jne    1f9 <__strchr_avx2+0x1f9>
      __ jne_b(L_zero_end);
// # endif
// 	addq	%rdi, %rax
//  1c6:	48 01 f8             	add    rax,rdi
      __ addq(rax, rdi);
// 	VZEROUPPER_RETURN
//  1c9:	c5 f8 77             	vzeroupper
//  1cc:	c3                   	ret
//  1cd:	0f 1f 00             	nop    DWORD PTR [rax]
      __ vzeroupper();
      __ ret(0);
      __ p2align(16, 10);


// 	.p2align 4,, 10
// L(last_vec_x0):
      __ bind(L_last_vec_x0);
// 	/* Use bsf to save code size.  */
// 	bsfl	%eax, %eax
//  1d0:	0f bc c0             	bsf    eax,eax
      __ bsfl(rax, rax);
// 	addq	$-(VEC_SIZE * 4 - 1), %rdi
//  1d3:	48 83 c7 81          	add    rdi,0xffffffffffffff81
      __ addq(rdi, -127);
// # ifndef USE_AS_STRCHRNUL
// 	/* Found CHAR or the null byte.	 */
// 	cmp	(%rdi, %rax), %CHAR_REG
//  1d7:	40 3a 34 07          	cmp    sil,BYTE PTR [rdi+rax*1]
      __ cmpb(rsi, Address(rdi, rax, Address::times_1));
// 	jne	L(zero_end)
//  1db:	75 1c                	jne    1f9 <__strchr_avx2+0x1f9>
      __ jne_b(L_zero_end);
// # endif
// 	addq	%rdi, %rax
//  1dd:	48 01 f8             	add    rax,rdi
      __ addq(rax, rdi);
// 	VZEROUPPER_RETURN
//  1e0:	c5 f8 77             	vzeroupper
//  1e3:	c3                   	ret
      __ vzeroupper();
      __ ret(0);
      __ p2align(16, 10);


// 	.p2align 4,, 10
// L(last_vec_x1):
      __ bind(L_last_vec_x1);
// 	tzcntl	%eax, %eax
//  1e4:	f3 0f bc c0          	tzcnt  eax,eax
      __ tzcntl(rax, rax);
// 	subq	$(VEC_SIZE * 3 - 1), %rdi
//  1e8:	48 83 ef 5f          	sub    rdi,0x5f
      __ subq(rdi, 0x5f);
// # ifndef USE_AS_STRCHRNUL
// 	/* Found CHAR or the null byte.	 */
// 	cmp	(%rdi, %rax), %CHAR_REG
//  1ec:	40 3a 34 07          	cmp    sil,BYTE PTR [rdi+rax*1]
      __ cmpb(rsi, Address(rdi, rax, Address::times_1));
// 	jne	L(zero_end)
//  1f0:	75 07                	jne    1f9 <__strchr_avx2+0x1f9>
      __ jne_b(L_zero_end);
// # endif
// 	addq	%rdi, %rax
//  1f2:	48 01 f8             	add    rax,rdi
      __ addq(rax, rdi);
// 	VZEROUPPER_RETURN
//  1f5:	c5 f8 77             	vzeroupper
//  1f8:	c3                   	ret
      __ vzeroupper();
      __ ret(0);

// # ifndef USE_AS_STRCHRNUL
// L(zero_end):
      __ bind(L_zero_end);
// 	xorl	%eax, %eax
//  1f9:	31 c0                	xor    eax,eax
      __ xorq(rax, rax);
// 	VZEROUPPER_RETURN
//  1fb:	c5 f8 77             	vzeroupper
//  1fe:	c3                   	ret
//  1ff:	90                   	nop
// # endif
      __ vzeroupper();
      __ ret(0);
      __ p2align(16, 8);

// 	/* Cold case for crossing page with first load.	 */
// 	.p2align 4,, 8
// L(cross_page_boundary):
      __ bind(L_cross_page_boundary);
// 	movq	%rdi, %rdx
//  200:	48 89 fa             	mov    rdx,rdi
      __ movq(rdx, rdi);
// 	/* Align rdi to VEC_SIZE - 1.  */
// 	orq	$(VEC_SIZE - 1), %rdi
//  203:	48 83 cf 1f          	or     rdi,0x1f
      __ orq(rdi, 0x1f);
// 	vmovdqa	-(VEC_SIZE - 1)(%rdi), %ymm2
//  207:	c5 fd 6f 57 e1       	vmovdqa ymm2,YMMWORD PTR [rdi-0x1f]
      __ vmovdqu(xmm2, Address(rdi, -0x1f));
// 	VPCMPEQ	%ymm2, %ymm0, %ymm3
//  20c:	c5 fd 74 da          	vpcmpeqb ymm3,ymm0,ymm2
      __ vpcmpeqb(xmm3, xmm0, xmm2, Assembler::AVX_256bit);
// 	VPCMPEQ	%ymm2, %ymm1, %ymm2
//  210:	c5 f5 74 d2          	vpcmpeqb ymm2,ymm1,ymm2
      __ vpcmpeqb(xmm2, xmm1, xmm2, Assembler::AVX_256bit);
// 	vpor	%ymm3, %ymm2, %ymm3
//  214:	c5 ed eb db          	vpor   ymm3,ymm2,ymm3
      __ vpor(xmm3, xmm2, xmm3, Assembler::AVX_256bit);
// 	vpmovmskb %ymm3, %eax
//  218:	c5 fd d7 c3          	vpmovmskb eax,ymm3
      __ vpmovmskb(rax, xmm3, Assembler::AVX_256bit);
// 	/* Remove the leading bytes. sarxl only uses bits [5:0] of COUNT
// 	   so no need to manually mod edx.  */
// 	sarxl	%edx, %eax, %eax
//  21c:	c4 e2 6a f7 c0       	sarx   eax,eax,edx
      __ sarxl(rax, rax, rdx);
// 	testl	%eax, %eax
//  221:	85 c0                	test   eax,eax
      __ testl(rax, rax);
// 	jz	L(cross_page_continue)
//  223:	0f 84 9b fe ff ff    	je     c4 <__strchr_avx2+0xc4>
      __ je(L_cross_page_continue);
// 	tzcntl	%eax, %eax
//  229:	f3 0f bc c0          	tzcnt  eax,eax
      __ tzcntl(rax, rax);
// # ifndef USE_AS_STRCHRNUL
// 	xorl	%ecx, %ecx
//  22d:	31 c9                	xor    ecx,ecx
      __ xorl(rcx, rcx);
// 	/* Found CHAR or the null byte.	 */
// 	cmp	(%rdx, %rax), %CHAR_REG
//  22f:	40 3a 34 02          	cmp    sil,BYTE PTR [rdx+rax*1]
      __ cmpb(rsi, Address(rdx, rax, Address::times_1));
// 	jne	L(zero_end)
//  233:	75 c4                	jne    1f9 <__strchr_avx2+0x1f9>
      __ jne_b(L_zero_end);
// # endif
// 	addq	%rdx, %rax
//  235:	48 01 d0             	add    rax,rdx
      __ addq(rax, rdx);
// 	VZEROUPPER_RETURN
//  238:	c5 f8 77             	vzeroupper
//  23b:	c3                   	ret
      __ vzeroupper();
      __ ret(0);
    }

  } else {                                       // SSE version
    assert(false, "Only supports AVX2");
  }

  return start;
}

#undef __
