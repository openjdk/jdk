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

address StubGenerator::generate_string_indexof() {
  StubCodeMark mark(this, "StubRoutines", "indexof");
  address jmp_table[13];
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

    Label L_exit, L_anysize, L_outer_loop_guts, L_outer_loop, L_inner_loop, L_loop2;
    Label L_tail_10_12, L_tail_3_9, L_tail_1, L_tail_2, L_tail_3;
    Label L_str2_len_0, L_str2_len_1, L_str2_len_2, L_str2_len_3, L_str2_len_4;
    Label L_str2_len_5, L_str2_len_6, L_str2_len_7, L_str2_len_8, L_str2_len_9;
    Label L_str2_len_10, L_str2_len_11, L_str2_len_12;
    Label L_outer3, L_mid3, L_inner3, L_outer4, L_mid4, L_inner4;
    Label L_outer5, L_mid5, L_inner5, L_outer6, L_mid6, L_inner6;
    Label L_outer7, L_mid7, L_inner7, L_outer8, L_mid8, L_inner8;
    Label L_outer9, L_mid9, L_inner9, L_outer10, L_mid10, L_inner10;
    Label L_outer11, L_mid11, L_inner11, L_outer12, L_mid12, L_inner12;
    Label L_inner_mid11, L_inner_mid12,L_0x404f26;

    Label L_begin;

    Label strchr_avx2, memcmp_avx2;

    address jump_table;

    __ jmp(L_begin);

    __ bind(L_str2_len_0);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404a6b <+59>:    mov    rax,r12
//   0x0000000000404a6e <+62>:    jmp    0x405025 <_Z14avx2_strstr_v2PKcmS0_m+1525> L_exit
    __ movq(rax, r12);
    __ jmp(L_exit);

    __ bind(L_anysize);
//   0x0000000000404a73 <+67>:    vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404a78 <+72>:    vmovdqu YMMWORD PTR [rsp+0x40],ymm0
//   0x0000000000404a7e <+78>:    vpbroadcastb ymm0,BYTE PTR [r12+r11*1-0x1]
//   0x0000000000404a85 <+85>:    vmovdqu YMMWORD PTR [rsp+0x20],ymm0
//   0x0000000000404a8b <+91>:    inc    r11
//   0x0000000000404a8e <+94>:    lea    r13,[r12-0x2]
//   0x0000000000404a93 <+99>:    xor    eax,eax
//   0x0000000000404a95 <+101>:   mov    QWORD PTR [rsp],r9
//   0x0000000000404a99 <+105>:   mov    QWORD PTR [rsp+0x18],rbx
//   0x0000000000404a9e <+110>:   mov    QWORD PTR [rsp+0x10],r10
//   0x0000000000404aa3 <+115>:   jmp    0x404acc <_Z14avx2_strstr_v2PKcmS0_m+156> L_outer_loop_guts
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vmovdqu(Address(rsp, 0x40), xmm0);
    __ vpbroadcastb(xmm0, Address(r12, r11, Address::times_1, -1), Assembler::AVX_256bit);
    __ vmovdqu(Address(rsp, 0x20), xmm0);
    __ incrementq(r11);
    __ leaq(r13, Address(r12, -2));
    __ xorl(rax, rax);
    __ movq(Address(rsp, 0), r9);
    __ movq(Address(rsp, 0x18), rbx);
    __ movq(Address(rsp, 0x10), r10);
    __ jmpb(L_outer_loop_guts);

    __ bind(L_outer_loop);
//   0x0000000000404aa5 <+117>:   mov    rax,QWORD PTR [rsp+0x8]
//   0x0000000000404aaa <+122>:   add    rax,0x20
//   0x0000000000404aae <+126>:   mov    rcx,0xffffffffffffffff
//   0x0000000000404ab5 <+133>:   mov    r10,QWORD PTR [rsp+0x10]
//   0x0000000000404aba <+138>:   cmp    rax,r10
//   0x0000000000404abd <+141>:   mov    r9,QWORD PTR [rsp]
//   0x0000000000404ac1 <+145>:   mov    rbx,QWORD PTR [rsp+0x18]
//   0x0000000000404ac6 <+150>:   jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ movq(rax, Address(rsp, 8));
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ movq(r10, Address(rsp, 0x10));
    __ cmpq(rax, r10);
    __ movq(r9, Address(rsp, 0));
    __ movq(rbx, Address(rsp, 0x18));
    __ jae(L_tail_3_9);

    __ bind(L_outer_loop_guts);
//   0x0000000000404acc <+156>:   lea    r14,[rbx+rax*1]
//   0x0000000000404ad0 <+160>:   mov    QWORD PTR [rsp+0x8],rax
//   0x0000000000404ad5 <+165>:   vmovdqu ymm0,YMMWORD PTR [rsp+0x40]
//   0x0000000000404adb <+171>:   vpcmpeqb ymm0,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404ae0 <+176>:   vmovdqu ymm1,YMMWORD PTR [rsp+0x20]
//   0x0000000000404ae6 <+182>:   vpcmpeqb ymm1,ymm1,YMMWORD PTR [r12+r14*1-0x1]
//   0x0000000000404aed <+189>:   vpand  ymm0,ymm1,ymm0
//   0x0000000000404af1 <+193>:   vpmovmskb r15d,ymm0
//   0x0000000000404af5 <+197>:   test   r15d,r15d
//   0x0000000000404af8 <+200>:   je     0x404aa5 <_Z14avx2_strstr_v2PKcmS0_m+117> L_outer_loop
//   0x0000000000404afa <+202>:   inc    r14
//   0x0000000000404afd <+205>:   nop    DWORD PTR [rax]
    __ leaq(r14, Address(rbx, rax, Address::times_1));
    __ movq(Address(rsp, 0x8), rax);
    __ vmovdqu(xmm0, Address(rsp, 0x40));
    __ vpcmpeqb(xmm0, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vmovdqu(xmm1, Address(rsp, 0x20));
    __ vpcmpeqb(xmm1, xmm1, Address(r12, r14, Address::times_1, -1), Assembler::AVX_256bit);
    __ vpand(xmm0, xmm1, xmm0, Assembler::AVX_256bit);
    __ vpmovmskb(r15, xmm0, Assembler::AVX_256bit);
    __ testl(r15, r15);
    __ je_b(L_outer_loop);
    __ incrementq(r14);

    __ align(16);
    __ bind(L_inner_loop);
//   0x0000000000404b00 <+208>:   xor    ebx,ebx
//   0x0000000000404b02 <+210>:   tzcnt  ebx,r15d
//   0x0000000000404b07 <+215>:   lea    rdi,[r14+rbx*1]
//   0x0000000000404b0b <+219>:   mov    rbp,r11
//   0x0000000000404b0e <+222>:   mov    rsi,r11
//   0x0000000000404b11 <+225>:   mov    rdx,r13
//   0x0000000000404b14 <+228>:   vzeroupper
//   0x0000000000404b17 <+231>:   call   0x4021e0 <bcmp@plt> memcmp
    __ xorl(rbx, rbx);
    __ tzcntl(rbx, r15);
    __ leaq(rdi, Address(r14, rbx, Address::times_1));
    __ movq(rbp, r11);
    __ movq(rsi, r11);
    __ movq(rdx, r13);
    __ vzeroupper();
    __ call(memcmp_avx2, relocInfo::none);

//   0x0000000000404b1c <+236>:   test   eax,eax
//   0x0000000000404b1e <+238>:   je     0x405037 <_Z14avx2_strstr_v2PKcmS0_m+1543> L_tail_1
//   0x0000000000404b24 <+244>:   blsr   r15d,r15d
//   0x0000000000404b29 <+249>:   mov    r11,rbp
//   0x0000000000404b2c <+252>:   jne    0x404b00 <_Z14avx2_strstr_v2PKcmS0_m+208> L_inner_loop
//   0x0000000000404b2e <+254>:   jmp    0x404aa5 <_Z14avx2_strstr_v2PKcmS0_m+117> L_outer_loop
    __ testl(rax, rax);
    __ je(L_tail_1);
    __ blsrl(r15, r15);
    __ movq(r11, rbp);
    __ jne_b(L_inner_loop);
    __ jmp(L_outer_loop);

    __ bind(L_str2_len_1);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404b33 <+259>:   movsx  esi,BYTE PTR [r11]
//   0x0000000000404b37 <+263>:   mov    rdi,rbx
//   0x0000000000404b3a <+266>:   call   0x408f80 <__strchr_avx2>
    __ movsbl(rsi, Address(r11, 0));
    __ movq(rdi, rbx);
    __ call(strchr_avx2, relocInfo::none);
//   0x0000000000404b3f <+271>:   mov    rcx,rax
//   0x0000000000404b42 <+274>:   mov    rdx,rax
//   0x0000000000404b45 <+277>:   sub    rdx,rbx
//   0x0000000000404b48 <+280>:   xor    eax,eax
//   0x0000000000404b4a <+282>:   cmp    rcx,0x1
//   0x0000000000404b4e <+286>:   sbb    rax,rax
//   0x0000000000404b51 <+289>:   or     rax,rdx
//   0x0000000000404b54 <+292>:   jmp    0x405025 <_Z14avx2_strstr_v2PKcmS0_m+1525> L_exit
    __ movq(rcx, rax);
    __ movq(rdx, rax);
    __ subq(rdx, rbx);
    __ xorl(rax, rax);
    __ cmpq(rcx, 0x1);
    __ sbbq(rax, rax);
    __ orq(rax, rdx);
    __ jmp(L_exit);

    __ bind(L_str2_len_2);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404b59 <+297>:   vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404b5e <+302>:   vpbroadcastb ymm1,BYTE PTR [r11+0x1]
//   0x0000000000404b64 <+308>:   vmovdqu ymm2,YMMWORD PTR [rbx]
//   0x0000000000404b68 <+312>:   mov    eax,0x40
//   0x0000000000404b6d <+317>:   mov    rcx,0xffffffffffffffff
//   0x0000000000404b74 <+324>:   data16 data16 cs nop WORD PTR [rax+rax*1+0x0]
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 1, Address::times_1), Assembler::AVX_256bit);
    __ vmovdqu(xmm2, Address(rbx, 0x0));
    __ movl(rax, 0x40);
    __ movq(rcx, -1);

    __ align(16);
    __ bind(L_loop2);
//   0x0000000000404b80 <+336>:   vpcmpeqb ymm3,ymm0,ymm2
//   0x0000000000404b84 <+340>:   vextracti128 xmm4,ymm2,0x1
//   0x0000000000404b8a <+346>:   vinserti128 ymm4,ymm4,XMMWORD PTR [rbx+rax*1-0x20],0x1
//   0x0000000000404b92 <+354>:   vpalignr ymm2,ymm4,ymm2,0x1
//   0x0000000000404b98 <+360>:   vpcmpeqb ymm2,ymm2,ymm1
//   0x0000000000404b9c <+364>:   vpand  ymm2,ymm2,ymm3
//   0x0000000000404ba0 <+368>:   vpmovmskb esi,ymm2
//   0x0000000000404ba4 <+372>:   test   esi,esi
//   0x0000000000404ba6 <+374>:   jne    0x405047 <_Z14avx2_strstr_v2PKcmS0_m+1559> L_tail_2
    __ vpcmpeqb(xmm3, xmm0, xmm2, Assembler::AVX_256bit);
    __ vextracti128(xmm4, xmm2, 0x1);
    __ vinserti128(xmm4, xmm4, Address(rbx, rax, Address::times_1, -0x20), 0x1);
    __ vpalignr(xmm2, xmm4, xmm2, 0x1, Assembler::AVX_256bit);
    __ vpcmpeqb(xmm2, xmm2, xmm1, Assembler::AVX_256bit);
    __ vpand(xmm2, xmm2, xmm3, Assembler::AVX_256bit);
    __ vpmovmskb(rsi, xmm2);
    __ testl(rsi, rsi);
    __ jne(L_tail_2);

//   0x0000000000404bac <+380>:   lea    rdx,[rax-0x20]
//   0x0000000000404bb0 <+384>:   cmp    rdx,r10
//   0x0000000000404bb3 <+387>:   jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ leaq(rdx, Address(rax, -0x20));
    __ cmpq(rdx, r10);
    __ jae(L_tail_3_9);

//   0x0000000000404bb9 <+393>:   vmovdqu ymm2,YMMWORD PTR [rbx+rax*1-0x20]
//   0x0000000000404bbf <+399>:   vpcmpeqb ymm3,ymm0,ymm2
//   0x0000000000404bc3 <+403>:   vextracti128 xmm4,ymm2,0x1
//   0x0000000000404bc9 <+409>:   vinserti128 ymm4,ymm4,XMMWORD PTR [rbx+rax*1],0x1
//   0x0000000000404bd0 <+416>:   vpalignr ymm2,ymm4,ymm2,0x1
//   0x0000000000404bd6 <+422>:   vpcmpeqb ymm2,ymm2,ymm1
//   0x0000000000404bda <+426>:   vpand  ymm2,ymm2,ymm3
//   0x0000000000404bde <+430>:   vpmovmskb esi,ymm2
//   0x0000000000404be2 <+434>:   test   esi,esi
//   0x0000000000404be4 <+436>:   jne    0x40504e <_Z14avx2_strstr_v2PKcmS0_m+1566> L_tail_3
    __ vmovdqu(xmm2, Address(rbx, rax, Address::times_1, -0x20));
    __ vpcmpeqb(xmm3, xmm0, xmm2, Assembler::AVX_256bit);
    __ vextracti128(xmm4, xmm2, 0x1);
    __ vinserti128(xmm4, xmm4, Address(rbx, rax, Address::times_1), 0x1);
    __ vpalignr(xmm2, xmm4, xmm2, 0x1, Assembler::AVX_256bit);
    __ vpcmpeqb(xmm2, xmm2, xmm1, Assembler::AVX_256bit);
    __ vpand(xmm2, xmm2, xmm3, Assembler::AVX_256bit);
    __ vpmovmskb(rsi, xmm2);
    __ testl(rsi, rsi);
    __ jne(L_tail_3);

//   0x0000000000404bea <+442>:   cmp    rax,r10
//   0x0000000000404bed <+445>:   jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ cmpq(rax, r10);
    __ jae(L_tail_3_9);

//   0x0000000000404bf3 <+451>:   vmovdqu ymm2,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404bf8 <+456>:   add    rax,0x40
//   0x0000000000404bfc <+460>:   jmp    0x404b80 <_Z14avx2_strstr_v2PKcmS0_m+336> L_loop2
    __ vmovdqu(xmm2, Address(rbx, rax, Address::times_1));
    __ addq(rax, 0x40);
    __ jmpb(L_loop2);

    __ bind(L_str2_len_3);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404bfe <+462>:   vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404c03 <+467>:   vpbroadcastb ymm1,BYTE PTR [r11+0x2]
//   0x0000000000404c09 <+473>:   xor    eax,eax
//   0x0000000000404c0b <+475>:   jmp    0x404c21 <_Z14avx2_strstr_v2PKcmS0_m+497> L_mid3
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 2, Address::times_1), Assembler::AVX_256bit);
    __ xorl(rax, rax);
    __ jmpb(L_mid3);

    __ align(16);
    __ bind(L_outer3);
//   0x0000000000404c0d <+477>:   add    rax,0x20
//   0x0000000000404c11 <+481>:   mov    rcx,0xffffffffffffffff
//   0x0000000000404c18 <+488>:   cmp    rax,r10
//   0x0000000000404c1b <+491>:   jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ cmpq(rax, r10);
    __ jae(L_tail_3_9);

    __ bind(L_mid3);
//   0x0000000000404c21 <+497>:   vpcmpeqb ymm2,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404c26 <+502>:   vpcmpeqb ymm3,ymm1,YMMWORD PTR [rbx+rax*1+0x2]
//   0x0000000000404c2c <+508>:   vpand  ymm2,ymm3,ymm2
//   0x0000000000404c30 <+512>:   vpmovmskb ecx,ymm2
//   0x0000000000404c34 <+516>:   test   ecx,ecx
//   0x0000000000404c36 <+518>:   je     0x404c0d <_Z14avx2_strstr_v2PKcmS0_m+477> L_outer3
    __ vpcmpeqb(xmm2, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rbx, rax, Address::times_1, 0x2), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm2, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ je_b(L_outer3);

//   0x0000000000404c38 <+520>:   lea    rdx,[rbx+rax*1]
//   0x0000000000404c3c <+524>:   movzx  esi,BYTE PTR [r11+0x1]
//   0x0000000000404c41 <+529>:   data16 data16 data16 data16 data16 cs nop WORD PTR [rax+rax*1+0x0]
    __ leaq(rdx, Address(rbx, rax, Address::times_1));
    __ movzbl(rsi, Address(r11, 1));

    __ align(16);
    __ bind(L_inner3);
//   0x0000000000404c50 <+544>:   xor    edi,edi
//   0x0000000000404c52 <+546>:   tzcnt  edi,ecx
//   0x0000000000404c56 <+550>:   cmp    BYTE PTR [rdx+rdi*1+0x1],sil
//   0x0000000000404c5b <+555>:   je     0x404f26 <_Z14avx2_strstr_v2PKcmS0_m+1270> L_0x404f26
//   0x0000000000404c61 <+561>:   blsr   ecx,ecx
//   0x0000000000404c66 <+566>:   jne    0x404c50 <_Z14avx2_strstr_v2PKcmS0_m+544> L_inner3
//   0x0000000000404c68 <+568>:   jmp    0x404c0d <_Z14avx2_strstr_v2PKcmS0_m+477> L_outer3
    __ xorl(rdi, rdi);
    __ tzcntl(rdi, rcx);
    __ cmpb(Address(rdx, rdi, Address::times_1, 0x1), rsi);
    __ je(L_0x404f26);
    __ blsrl(rcx, rcx);
    __ jne_b(L_inner3);
    __ jmpb(L_outer3);

    __ bind(L_str2_len_4);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404c6a <+570>:   vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404c6f <+575>:   vpbroadcastb ymm1,BYTE PTR [r11+0x3]
//   0x0000000000404c75 <+581>:   xor    eax,eax
//   0x0000000000404c77 <+583>:   jmp    0x404c8d <_Z14avx2_strstr_v2PKcmS0_m+605> L_mid4
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 3, Address::times_1), Assembler::AVX_256bit);
    __ xorl(rax, rax);
    __ jmpb(L_mid4);

    __ align(16);
    __ bind(L_outer4);
//   0x0000000000404c79 <+585>:   add    rax,0x20
//   0x0000000000404c7d <+589>:   mov    rcx,0xffffffffffffffff
//   0x0000000000404c84 <+596>:   cmp    rax,r10
//   0x0000000000404c87 <+599>:   jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ cmpq(rax, r10);
    __ jae(L_tail_3_9);

    __ bind(L_mid4);
//   0x0000000000404c8d <+605>:   vpcmpeqb ymm2,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404c92 <+610>:   vpcmpeqb ymm3,ymm1,YMMWORD PTR [rbx+rax*1+0x3]
//   0x0000000000404c98 <+616>:   vpand  ymm2,ymm3,ymm2
//   0x0000000000404c9c <+620>:   vpmovmskb ecx,ymm2
//   0x0000000000404ca0 <+624>:   test   ecx,ecx
//   0x0000000000404ca2 <+626>:   je     0x404c79 <_Z14avx2_strstr_v2PKcmS0_m+585> L_outer4
    __ vpcmpeqb(xmm2, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rbx, rax, Address::times_1, 0x3), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm2, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ je_b(L_outer4);


//   0x0000000000404ca4 <+628>:   lea    rdx,[rbx+rax*1]
//   0x0000000000404ca8 <+632>:   movzx  esi,WORD PTR [r11+0x1]
//   0x0000000000404cad <+637>:   nop    DWORD PTR [rax]
    __ leaq(rdx, Address(rbx, rax, Address::times_1));
    __ movzwl(rsi, Address(r11, 1));

    __ align(16);
    __ bind(L_inner4);
//   0x0000000000404cb0 <+640>:   xor    edi,edi
//   0x0000000000404cb2 <+642>:   tzcnt  edi,ecx
//   0x0000000000404cb6 <+646>:   cmp    WORD PTR [rdx+rdi*1+0x1],si
//   0x0000000000404cbb <+651>:   je     0x404f26 <_Z14avx2_strstr_v2PKcmS0_m+1270> L_0x404f26
//   0x0000000000404cc1 <+657>:   blsr   ecx,ecx
//   0x0000000000404cc6 <+662>:   jne    0x404cb0 <_Z14avx2_strstr_v2PKcmS0_m+640> L_inner4
//   0x0000000000404cc8 <+664>:   jmp    0x404c79 <_Z14avx2_strstr_v2PKcmS0_m+585> L_outer4
    __ xorl(rdi, rdi);
    __ tzcntl(rdi, rcx);
    __ cmpw(Address(rdx, rdi, Address::times_1, 0x1), rsi);
    __ je(L_0x404f26);
    __ blsrl(rcx, rcx);
    __ jne_b(L_inner4);
    __ jmpb(L_outer4);

    __ bind(L_str2_len_5);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404cca <+666>:   vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404ccf <+671>:   vpbroadcastb ymm1,BYTE PTR [r11+0x4]
//   0x0000000000404cd5 <+677>:   xor    eax,eax
//   0x0000000000404cd7 <+679>:   jmp    0x404ced <_Z14avx2_strstr_v2PKcmS0_m+701> L_mid5
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 4, Address::times_1), Assembler::AVX_256bit);
    __ xorl(rax, rax);
    __ jmpb(L_mid5);

    __ align(16);
    __ bind(L_outer5);
//   0x0000000000404cd9 <+681>:   add    rax,0x20
//   0x0000000000404cdd <+685>:   mov    rcx,0xffffffffffffffff
//   0x0000000000404ce4 <+692>:   cmp    rax,r10
//   0x0000000000404ce7 <+695>:   jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ cmpq(rax, r10);
    __ jae(L_tail_3_9);

    __ bind(L_mid5);
//   0x0000000000404ced <+701>:   vpcmpeqb ymm2,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404cf2 <+706>:   vpcmpeqb ymm3,ymm1,YMMWORD PTR [rbx+rax*1+0x4]
//   0x0000000000404cf8 <+712>:   vpand  ymm2,ymm3,ymm2
//   0x0000000000404cfc <+716>:   vpmovmskb ecx,ymm2
//   0x0000000000404d00 <+720>:   test   ecx,ecx
//   0x0000000000404d02 <+722>:   je     0x404cd9 <_Z14avx2_strstr_v2PKcmS0_m+681> L_outer5
    __ vpcmpeqb(xmm2, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rbx, rax, Address::times_1, 0x4), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm2, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ je_b(L_outer5);

//   0x0000000000404d04 <+724>:   lea    rdx,[rbx+rax*1]
//   0x0000000000404d08 <+728>:   mov    esi,DWORD PTR [r11+0x1]
//   0x0000000000404d0c <+732>:   nop    DWORD PTR [rax+0x0]
    __ leaq(rdx, Address(rbx, rax, Address::times_1));
    __ movl(rsi, Address(r11, 1));

    __ align(16);
    __ bind(L_inner5);
//   0x0000000000404d10 <+736>:   xor    edi,edi
//   0x0000000000404d12 <+738>:   tzcnt  edi,ecx
//   0x0000000000404d16 <+742>:   cmp    DWORD PTR [rdx+rdi*1+0x1],esi
//   0x0000000000404d1a <+746>:   je     0x404f26 <_Z14avx2_strstr_v2PKcmS0_m+1270> L_0x404f26
//   0x0000000000404d20 <+752>:   blsr   ecx,ecx
//   0x0000000000404d25 <+757>:   jne    0x404d10 <_Z14avx2_strstr_v2PKcmS0_m+736> L_inner5
//   0x0000000000404d27 <+759>:   jmp    0x404cd9 <_Z14avx2_strstr_v2PKcmS0_m+681> L_outer5
    __ xorl(rdi, rdi);
    __ tzcntl(rdi, rcx);
    __ cmpl(Address(rdx, rdi, Address::times_1, 0x1), rsi);
    __ je(L_0x404f26);
    __ blsrl(rcx, rcx);
    __ jne_b(L_inner5);
    __ jmpb(L_outer5);

    __ bind(L_str2_len_6);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404d29 <+761>:   vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404d2e <+766>:   vpbroadcastb ymm1,BYTE PTR [r11+0x5]
//   0x0000000000404d34 <+772>:   xor    eax,eax
//   0x0000000000404d36 <+774>:   jmp    0x404d4c <_Z14avx2_strstr_v2PKcmS0_m+796> L_mid6
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 5, Address::times_1), Assembler::AVX_256bit);
    __ xorl(rax, rax);
    __ jmpb(L_mid6);

    __ align(16);
    __ bind(L_outer6);
//   0x0000000000404d38 <+776>:   add    rax,0x20
//   0x0000000000404d3c <+780>:   mov    rcx,0xffffffffffffffff
//   0x0000000000404d43 <+787>:   cmp    rax,r10
//   0x0000000000404d46 <+790>:   jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ cmpq(rax, r10);
    __ jae(L_tail_3_9);

    __ bind(L_mid6);
//   0x0000000000404d4c <+796>:   vpcmpeqb ymm2,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404d51 <+801>:   vpcmpeqb ymm3,ymm1,YMMWORD PTR [rbx+rax*1+0x5]
//   0x0000000000404d57 <+807>:   vpand  ymm2,ymm3,ymm2
//   0x0000000000404d5b <+811>:   vpmovmskb ecx,ymm2
//   0x0000000000404d5f <+815>:   test   ecx,ecx
//   0x0000000000404d61 <+817>:   je     0x404d38 <_Z14avx2_strstr_v2PKcmS0_m+776> L_outer6
    __ vpcmpeqb(xmm2, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rbx, rax, Address::times_1, 0x5), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm2, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ je_b(L_outer6);

//   0x0000000000404d63 <+819>:   lea    rdx,[rbx+rax*1]
//   0x0000000000404d67 <+823>:   mov    esi,DWORD PTR [r11+0x1]
//   0x0000000000404d6b <+827>:   nop    DWORD PTR [rax+rax*1+0x0]
    __ leaq(rdx, Address(rbx, rax, Address::times_1));
    __ movl(rsi, Address(r11, 1));

    __ align(16);
    __ bind(L_inner6);
//   0x0000000000404d70 <+832>:   xor    edi,edi
//   0x0000000000404d72 <+834>:   tzcnt  edi,ecx
//   0x0000000000404d76 <+838>:   cmp    DWORD PTR [rdx+rdi*1+0x1],esi
//   0x0000000000404d7a <+842>:   je     0x404f26 <_Z14avx2_strstr_v2PKcmS0_m+1270> L_0x404f26
//   0x0000000000404d80 <+848>:   blsr   ecx,ecx
//   0x0000000000404d85 <+853>:   jne    0x404d70 <_Z14avx2_strstr_v2PKcmS0_m+832> L_inner6
//   0x0000000000404d87 <+855>:   jmp    0x404d38 <_Z14avx2_strstr_v2PKcmS0_m+776> L_outer6
    __ xorl(rdi, rdi);
    __ tzcntl(rdi, rcx);
    __ cmpl(Address(rdx, rdi, Address::times_1, 0x1), rsi);
    __ je(L_0x404f26);
    __ blsrl(rcx, rcx);
    __ jne_b(L_inner6);
    __ jmpb(L_outer6);

    __ bind(L_str2_len_7);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404d89 <+857>:   vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404d8e <+862>:   vpbroadcastb ymm1,BYTE PTR [r11+0x6]
//   0x0000000000404d94 <+868>:   xor    eax,eax
//   0x0000000000404d96 <+870>:   jmp    0x404dac <_Z14avx2_strstr_v2PKcmS0_m+892> L_mid7
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 6, Address::times_1), Assembler::AVX_256bit);
    __ xorl(rax, rax);
    __ jmpb(L_mid7);

    __ align(16);
    __ bind(L_outer7);
//   0x0000000000404d98 <+872>:   add    rax,0x20
//   0x0000000000404d9c <+876>:   mov    rcx,0xffffffffffffffff
//   0x0000000000404da3 <+883>:   cmp    rax,r10
//   0x0000000000404da6 <+886>:   jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ cmpq(rax, r10);
    __ jae(L_tail_3_9);

    __ bind(L_mid7);
//   0x0000000000404dac <+892>:   vpcmpeqb ymm2,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404db1 <+897>:   vpcmpeqb ymm3,ymm1,YMMWORD PTR [rbx+rax*1+0x6]
//   0x0000000000404db7 <+903>:   vpand  ymm2,ymm3,ymm2
//   0x0000000000404dbb <+907>:   vpmovmskb ecx,ymm2
//   0x0000000000404dbf <+911>:   test   ecx,ecx
//   0x0000000000404dc1 <+913>:   je     0x404d98 <_Z14avx2_strstr_v2PKcmS0_m+872> L_outer7
    __ vpcmpeqb(xmm2, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rbx, rax, Address::times_1, 0x6), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm2, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ je_b(L_outer7);

//   0x0000000000404dc3 <+915>:   lea    rdx,[rbx+rax*1]
//   0x0000000000404dc7 <+919>:   mov    rsi,QWORD PTR [r11+0x1]
//   0x0000000000404dcb <+923>:   nop    DWORD PTR [rax+rax*1+0x0]
    __ leaq(rdx, Address(rbx, rax, Address::times_1));
    __ movq(rsi, Address(r11, 1));

    __ align(16);
    __ bind(L_inner7);
//   0x0000000000404dd0 <+928>:   xor    edi,edi
//   0x0000000000404dd2 <+930>:   tzcnt  edi,ecx
//   0x0000000000404dd6 <+934>:   mov    r8,QWORD PTR [rdx+rdi*1+0x1]
//   0x0000000000404ddb <+939>:   xor    r8,rsi
//   0x0000000000404dde <+942>:   shl    r8,0x18
//   0x0000000000404de2 <+946>:   je     0x404f26 <_Z14avx2_strstr_v2PKcmS0_m+1270> L_0x404f26
//   0x0000000000404de8 <+952>:   blsr   ecx,ecx
//   0x0000000000404ded <+957>:   jne    0x404dd0 <_Z14avx2_strstr_v2PKcmS0_m+928> L_inner7
//   0x0000000000404def <+959>:   jmp    0x404d98 <_Z14avx2_strstr_v2PKcmS0_m+872> L_outer7
    __ xorl(rdi, rdi);
    __ tzcntl(rdi, rcx);
    __ movq(r8, Address(rdx, rdi, Address::times_1, 0x1));
    __ xorq(r8, rsi);
    __ shlq(r8, 0x18);
    __ je(L_0x404f26);
    __ blsrl(rcx, rcx);
    __ jne_b(L_inner7);
    __ jmpb(L_outer7);

    __ bind(L_str2_len_8);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404df1 <+961>:   vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404df6 <+966>:   vpbroadcastb ymm1,BYTE PTR [r11+0x7]
//   0x0000000000404dfc <+972>:   xor    eax,eax
//   0x0000000000404dfe <+974>:   jmp    0x404e14 <_Z14avx2_strstr_v2PKcmS0_m+996> L_mid8
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 7, Address::times_1), Assembler::AVX_256bit);
    __ xorl(rax, rax);
    __ jmpb(L_mid8);

    __ align(16);
    __ bind(L_outer8);
//   0x0000000000404e00 <+976>:   add    rax,0x20
//   0x0000000000404e04 <+980>:   mov    rcx,0xffffffffffffffff
//   0x0000000000404e0b <+987>:   cmp    rax,r10
//   0x0000000000404e0e <+990>:   jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ cmpq(rax, r10);
    __ jae(L_tail_3_9);

    __ bind(L_mid8);
//   0x0000000000404e14 <+996>:   vpcmpeqb ymm2,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404e19 <+1001>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rbx+rax*1+0x7]
//   0x0000000000404e1f <+1007>:  vpand  ymm2,ymm3,ymm2
//   0x0000000000404e23 <+1011>:  vpmovmskb ecx,ymm2
//   0x0000000000404e27 <+1015>:  test   ecx,ecx
//   0x0000000000404e29 <+1017>:  je     0x404e00 <_Z14avx2_strstr_v2PKcmS0_m+976> L_outer8
    __ vpcmpeqb(xmm2, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rbx, rax, Address::times_1, 0x7), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm2, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ je_b(L_outer8);

//   0x0000000000404e2b <+1019>:  lea    rdx,[rbx+rax*1]
//   0x0000000000404e2f <+1023>:  mov    rsi,QWORD PTR [r11+0x1]
//   0x0000000000404e33 <+1027>:  data16 data16 data16 cs nop WORD PTR [rax+rax*1+0x0]
    __ leaq(rdx, Address(rbx, rax, Address::times_1));
    __ movq(rsi, Address(r11, 1));

    __ align(16);
    __ bind(L_inner8);
//   0x0000000000404e40 <+1040>:  xor    edi,edi
//   0x0000000000404e42 <+1042>:  tzcnt  edi,ecx
//   0x0000000000404e46 <+1046>:  mov    r8,QWORD PTR [rdx+rdi*1+0x1]
//   0x0000000000404e4b <+1051>:  xor    r8,rsi
//   0x0000000000404e4e <+1054>:  shl    r8,0x10
//   0x0000000000404e52 <+1058>:  je     0x404f26 <_Z14avx2_strstr_v2PKcmS0_m+1270> L_0x404f26
//   0x0000000000404e58 <+1064>:  blsr   ecx,ecx
//   0x0000000000404e5d <+1069>:  jne    0x404e40 <_Z14avx2_strstr_v2PKcmS0_m+1040> L_inner8
//   0x0000000000404e5f <+1071>:  jmp    0x404e00 <_Z14avx2_strstr_v2PKcmS0_m+976> L_outer8
    __ xorl(rdi, rdi);
    __ tzcntl(rdi, rcx);
    __ movq(r8, Address(rdx, rdi, Address::times_1, 0x1));
    __ xorq(r8, rsi);
    __ shlq(r8, 0x10);
    __ je(L_0x404f26);
    __ blsrl(rcx, rcx);
    __ jne_b(L_inner8);
    __ jmpb(L_outer8);

    __ bind(L_str2_len_9);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404e61 <+1073>:  vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404e66 <+1078>:  vpbroadcastb ymm1,BYTE PTR [r11+0x8]
//   0x0000000000404e6c <+1084>:  xor    eax,eax
//   0x0000000000404e6e <+1086>:  jmp    0x404e84 <_Z14avx2_strstr_v2PKcmS0_m+1108> L_mid9
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 8, Address::times_1), Assembler::AVX_256bit);
    __ xorl(rax, rax);
    __ jmpb(L_mid9);

    __ align(16);
    __ bind(L_outer9);
//   0x0000000000404e70 <+1088>:  add    rax,0x20
//   0x0000000000404e74 <+1092>:  mov    rcx,0xffffffffffffffff
//   0x0000000000404e7b <+1099>:  cmp    rax,r10
//   0x0000000000404e7e <+1102>:  jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ cmpq(rax, r10);
    __ jae(L_tail_3_9);

    __ bind(L_mid9);
//   0x0000000000404e84 <+1108>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404e89 <+1113>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rbx+rax*1+0x8]
//   0x0000000000404e8f <+1119>:  vpand  ymm2,ymm3,ymm2
//   0x0000000000404e93 <+1123>:  vpmovmskb ecx,ymm2
//   0x0000000000404e97 <+1127>:  test   ecx,ecx
//   0x0000000000404e99 <+1129>:  je     0x404e70 <_Z14avx2_strstr_v2PKcmS0_m+1088> L_outer9
    __ vpcmpeqb(xmm2, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rbx, rax, Address::times_1, 0x8), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm2, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ je_b(L_outer9);

//   0x0000000000404e9b <+1131>:  lea    rdx,[rbx+rax*1]
//   0x0000000000404e9f <+1135>:  mov    rsi,QWORD PTR [r11+0x1]
//   0x0000000000404ea3 <+1139>:  data16 data16 data16 cs nop WORD PTR [rax+rax*1+0x0]
    __ leaq(rdx, Address(rbx, rax, Address::times_1));
    __ movq(rsi, Address(r11, 1));

    __ align(16);
    __ bind(L_inner9);
//   0x0000000000404eb0 <+1152>:  xor    edi,edi
//   0x0000000000404eb2 <+1154>:  tzcnt  edi,ecx
//   0x0000000000404eb6 <+1158>:  cmp    QWORD PTR [rdx+rdi*1+0x1],rsi
//   0x0000000000404ebb <+1163>:  je     0x404f26 <_Z14avx2_strstr_v2PKcmS0_m+1270> L_0x404f26
//   0x0000000000404ebd <+1165>:  blsr   ecx,ecx
//   0x0000000000404ec2 <+1170>:  jne    0x404eb0 <_Z14avx2_strstr_v2PKcmS0_m+1152> L_inner9
//   0x0000000000404ec4 <+1172>:  jmp    0x404e70 <_Z14avx2_strstr_v2PKcmS0_m+1088> L_outer9
    __ xorl(rdi, rdi);
    __ tzcntl(rdi, rcx);
    __ cmpq(Address(rdx, rdi, Address::times_1, 0x1), rsi);
    __ je_b(L_0x404f26);
    __ blsrl(rcx, rcx);
    __ jne_b(L_inner9);
    __ jmpb(L_outer9);

    __ bind(L_str2_len_10);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404ec6 <+1174>:  vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404ecb <+1179>:  vpbroadcastb ymm1,BYTE PTR [r11+0x9]
//   0x0000000000404ed1 <+1185>:  xor    eax,eax
//   0x0000000000404ed3 <+1187>:  jmp    0x404ee9 <_Z14avx2_strstr_v2PKcmS0_m+1209> L_mid10
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 9, Address::times_1), Assembler::AVX_256bit);
    __ xorl(rax, rax);
    __ jmpb(L_mid10);

    __ align(16);
    __ bind(L_outer10);
//   0x0000000000404ed5 <+1189>:  add    rax,0x20
//   0x0000000000404ed9 <+1193>:  mov    rcx,0xffffffffffffffff
//   0x0000000000404ee0 <+1200>:  cmp    rax,r10
//   0x0000000000404ee3 <+1203>:  jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ cmpq(rax, r10);
    __ jae(L_tail_3_9);

    __ bind(L_mid10);
//   0x0000000000404ee9 <+1209>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404eee <+1214>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rbx+rax*1+0x9]
//   0x0000000000404ef4 <+1220>:  vpand  ymm2,ymm3,ymm2
//   0x0000000000404ef8 <+1224>:  vpmovmskb ecx,ymm2
//   0x0000000000404efc <+1228>:  test   ecx,ecx
//   0x0000000000404efe <+1230>:  je     0x404ed5 <_Z14avx2_strstr_v2PKcmS0_m+1189> L_outer10
    __ vpcmpeqb(xmm2, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rbx, rax, Address::times_1, 0x9), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm2, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ je_b(L_outer10);

//   0x0000000000404f00 <+1232>:  lea    rdx,[rbx+rax*1]
//   0x0000000000404f04 <+1236>:  mov    rsi,QWORD PTR [r11+0x1]
//   0x0000000000404f08 <+1240>:  nop    DWORD PTR [rax+rax*1+0x0]
    __ leaq(rdx, Address(rbx, rax, Address::times_1));
    __ movq(rsi, Address(r11, 1));

    __ align(16);
    __ bind(L_inner10);
//   0x0000000000404f10 <+1248>:  xor    edi,edi
//   0x0000000000404f12 <+1250>:  tzcnt  edi,ecx
//   0x0000000000404f16 <+1254>:  cmp    QWORD PTR [rdx+rdi*1+0x1],rsi
//   0x0000000000404f1b <+1259>:  je     0x404f26 <_Z14avx2_strstr_v2PKcmS0_m+1270> L_0x404f26
//   0x0000000000404f1d <+1261>:  blsr   ecx,ecx
//   0x0000000000404f22 <+1266>:  jne    0x404f10 <_Z14avx2_strstr_v2PKcmS0_m+1248> L_inner10
//   0x0000000000404f24 <+1268>:  jmp    0x404ed5 <_Z14avx2_strstr_v2PKcmS0_m+1189> L_outer10
    __ xorl(rdi, rdi);
    __ tzcntl(rdi, rcx);
    __ cmpq(Address(rdx, rdi, Address::times_1, 0x1), rsi);
    __ je_b(L_0x404f26);
    __ blsrl(rcx, rcx);
    __ jne_b(L_inner10);
    __ jmpb(L_outer10);

    __ bind(L_0x404f26);
//   0x0000000000404f26 <+1270>:  mov    ecx,edi
//   0x0000000000404f28 <+1272>:  jmp    0x405011 <_Z14avx2_strstr_v2PKcmS0_m+1505> L_tail_10_12
    __ movl(rcx, rdi);
    __ jmp(L_tail_10_12);

    __ bind(L_str2_len_11);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404f2d <+1277>:  vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404f32 <+1282>:  vpbroadcastb ymm1,BYTE PTR [r11+0xa]
//   0x0000000000404f38 <+1288>:  xor    eax,eax
//   0x0000000000404f3a <+1290>:  jmp    0x404f50 <_Z14avx2_strstr_v2PKcmS0_m+1312> L_mid11
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 0xa, Address::times_1), Assembler::AVX_256bit);
    __ xorl(rax, rax);
    __ jmpb(L_mid11);

    __ align(16);
    __ bind(L_outer11);
//   0x0000000000404f3c <+1292>:  add    rax,0x20
//   0x0000000000404f40 <+1296>:  mov    rcx,0xffffffffffffffff
//   0x0000000000404f47 <+1303>:  cmp    rax,r10
//   0x0000000000404f4a <+1306>:  jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ cmpq(rax, r10);
    __ jae(L_tail_3_9);

    __ bind(L_mid11);
//   0x0000000000404f50 <+1312>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404f55 <+1317>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rbx+rax*1+0xa]
//   0x0000000000404f5b <+1323>:  vpand  ymm2,ymm3,ymm2
//   0x0000000000404f5f <+1327>:  vpmovmskb ecx,ymm2
//   0x0000000000404f63 <+1331>:  test   ecx,ecx
//   0x0000000000404f65 <+1333>:  je     0x404f3c <_Z14avx2_strstr_v2PKcmS0_m+1292> L_outer11
    __ vpcmpeqb(xmm2, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rbx, rax, Address::times_1, 0xa), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm2, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ je_b(L_outer11);

//   0x0000000000404f67 <+1335>:  lea    rdx,[rbx+rax*1]
//   0x0000000000404f6b <+1339>:  mov    rsi,QWORD PTR [r11+0x1]
//   0x0000000000404f6f <+1343>:  movzx  edi,BYTE PTR [r11+0x9]
//   0x0000000000404f74 <+1348>:  jmp    0x404f87 <_Z14avx2_strstr_v2PKcmS0_m+1367> L_inner_mid11
//   0x0000000000404f76 <+1350>:  cs nop WORD PTR [rax+rax*1+0x0]
    __ leaq(rdx, Address(rbx, rax, Address::times_1));
    __ movq(rsi, Address(r11, 1));
    __ movzbl(rdi, Address(r11, 9));
    __ jmpb(L_inner_mid11);

    __ align(16);
    __ bind(L_inner11);
//   0x0000000000404f80 <+1360>:  blsr   ecx,ecx
//   0x0000000000404f85 <+1365>:  je     0x404f3c <_Z14avx2_strstr_v2PKcmS0_m+1292> L_outer11
    __ blsrl(rcx, rcx);
    __ je_b(L_outer11);

    __ bind(L_inner_mid11);
//   0x0000000000404f87 <+1367>:  xor    r8d,r8d
//   0x0000000000404f8a <+1370>:  tzcnt  r8d,ecx
//   0x0000000000404f8f <+1375>:  cmp    QWORD PTR [rdx+r8*1+0x1],rsi
//   0x0000000000404f94 <+1380>:  jne    0x404f80 <_Z14avx2_strstr_v2PKcmS0_m+1360> L_inner11
//   0x0000000000404f96 <+1382>:  cmp    BYTE PTR [rdx+r8*1+0x9],dil
//   0x0000000000404f9b <+1387>:  jne    0x404f80 <_Z14avx2_strstr_v2PKcmS0_m+1360> L_inner11
//   0x0000000000404f9d <+1389>:  jmp    0x40500e <_Z14avx2_strstr_v2PKcmS0_m+1502> L_tail_10_12
    __ xorl(r8, r8);
    __ tzcntl(r8, rcx);
    __ cmpq(Address(rdx, r8, Address::times_1, 0x1), rsi);
    __ jne_b(L_inner11);
    __ cmpb(Address(rdx, r8, Address::times_1, 0x9), rdi);
    __ jne_b(L_inner11);
    __ jmpb(L_tail_10_12);

    __ bind(L_str2_len_12);
    jmp_table[jmp_ndx++] = __ pc();
//   0x0000000000404f9f <+1391>:  vpbroadcastb ymm0,BYTE PTR [r11]
//   0x0000000000404fa4 <+1396>:  vpbroadcastb ymm1,BYTE PTR [r11+0xb]
//   0x0000000000404faa <+1402>:  xor    eax,eax
//   0x0000000000404fac <+1404>:  jmp    0x404fbe <_Z14avx2_strstr_v2PKcmS0_m+1422> L_mid12
    __ vpbroadcastb(xmm0, Address(r11, 0, Address::times_1), Assembler::AVX_256bit);
    __ vpbroadcastb(xmm1, Address(r11, 0xb, Address::times_1), Assembler::AVX_256bit);
    __ xorl(rax, rax);
    __ jmpb(L_mid12);

    __ align(16);
    __ bind(L_outer12);
//   0x0000000000404fae <+1406>:  add    rax,0x20
//   0x0000000000404fb2 <+1410>:  mov    rcx,0xffffffffffffffff
//   0x0000000000404fb9 <+1417>:  cmp    rax,r10
//   0x0000000000404fbc <+1420>:  jae    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ addq(rax, 0x20);
    __ movq(rcx, -1);
    __ cmpq(rax, r10);
    __ jae_b(L_tail_3_9);

    __ bind(L_mid12);
//   0x0000000000404fbe <+1422>:  vpcmpeqb ymm2,ymm0,YMMWORD PTR [rbx+rax*1]
//   0x0000000000404fc3 <+1427>:  vpcmpeqb ymm3,ymm1,YMMWORD PTR [rbx+rax*1+0xb]
//   0x0000000000404fc9 <+1433>:  vpand  ymm2,ymm3,ymm2
//   0x0000000000404fcd <+1437>:  vpmovmskb ecx,ymm2
//   0x0000000000404fd1 <+1441>:  test   ecx,ecx
//   0x0000000000404fd3 <+1443>:  je     0x404fae <_Z14avx2_strstr_v2PKcmS0_m+1406> L_outer12
    __ vpcmpeqb(xmm2, xmm0, Address(rbx, rax, Address::times_1), Assembler::AVX_256bit);
    __ vpcmpeqb(xmm3, xmm1, Address(rbx, rax, Address::times_1, 0xb), Assembler::AVX_256bit);
    __ vpand(xmm2, xmm3, xmm2, Assembler::AVX_256bit);
    __ vpmovmskb(rcx, xmm2, Assembler::AVX_256bit);
    __ testl(rcx, rcx);
    __ je_b(L_outer12);

//   0x0000000000404fd5 <+1445>:  lea    rdx,[rbx+rax*1]
//   0x0000000000404fd9 <+1449>:  mov    rsi,QWORD PTR [r11+0x1]
//   0x0000000000404fdd <+1453>:  movzx  edi,WORD PTR [r11+0x9]
//   0x0000000000404fe2 <+1458>:  jmp    0x404ff7 <_Z14avx2_strstr_v2PKcmS0_m+1479> L_inner_mid12
//   0x0000000000404fe4 <+1460>:  data16 data16 cs nop WORD PTR [rax+rax*1+0x0]
    __ leaq(rdx, Address(rbx, rax, Address::times_1));
    __ movq(rsi, Address(r11, 1));
    __ movzwl(rdi, Address(r11, 9));
    __ jmpb(L_inner_mid12);

    __ align(16);
    __ bind(L_inner12);
//   0x0000000000404ff0 <+1472>:  blsr   ecx,ecx
//   0x0000000000404ff5 <+1477>:  je     0x404fae <_Z14avx2_strstr_v2PKcmS0_m+1406>
    __ blsrl(rcx, rcx);
    __ je_b(L_outer12);

    __ bind(L_inner_mid12);
//   0x0000000000404ff7 <+1479>:  xor    r8d,r8d
//   0x0000000000404ffa <+1482>:  tzcnt  r8d,ecx
//   0x0000000000404fff <+1487>:  cmp    QWORD PTR [rdx+r8*1+0x1],rsi
//   0x0000000000405004 <+1492>:  jne    0x404ff0 <_Z14avx2_strstr_v2PKcmS0_m+1472> L_inner12
//   0x0000000000405006 <+1494>:  cmp    WORD PTR [rdx+r8*1+0x9],di
//   0x000000000040500c <+1500>:  jne    0x404ff0 <_Z14avx2_strstr_v2PKcmS0_m+1472> L_inner12
    __ xorl(r8, r8);
    __ tzcntl(r8, rcx);
    __ cmpq(Address(rdx, r8, Address::times_1, 0x1), rsi);
    __ jne_b(L_inner12);
    __ cmpw(Address(rdx, r8, Address::times_1, 0x9), rdi);
    __ jne_b(L_inner12);

    __ bind(L_tail_10_12);
//   0x000000000040500e <+1502>:  mov    ecx,r8d
//   0x0000000000405011 <+1505>:  add    rax,rcx
//   0x0000000000405014 <+1508>:  mov    rcx,rax
    __ movl(rcx, r8);
    __ addq(rax, rcx);
    __ movq(rcx, rax);

    __ bind(L_tail_3_9);
//   0x0000000000405017 <+1511>:  cmp    rcx,r9
//   0x000000000040501a <+1514>:  mov    rax,0xffffffffffffffff
//   0x0000000000405021 <+1521>:  cmovbe rax,rcx
    __ cmpq(rcx, r9);
    __ movq(rax, -1);
    __ cmovq(Assembler::belowEqual, rax, rcx);

    __ bind(L_exit);
//   0x0000000000405025 <+1525>:  add    rsp,0x68
//   0x0000000000405029 <+1529>:  pop    rbx
//   0x000000000040502a <+1530>:  pop    r12
//   0x000000000040502c <+1532>:  pop    r13
//   0x000000000040502e <+1534>:  pop    r14
//   0x0000000000405030 <+1536>:  pop    r15
//   0x0000000000405032 <+1538>:  pop    rbp
//   0x0000000000405033 <+1539>:  vzeroupper
//   0x0000000000405036 <+1542>:  ret
    __ addptr(rsp, 0x68);
    __ pop(rbx);
    __ pop(r12);
    __ pop(r13);
    __ pop(r14);
    __ pop(r15);
    __ pop(rbp);
    __ vzeroupper();

    __ leave(); // required for proper stackwalking of RuntimeStub frame
    __ ret(0);

    __ bind(L_tail_1);
//   0x0000000000405037 <+1543>:  mov    eax,ebx
//   0x0000000000405039 <+1545>:  mov    rcx,QWORD PTR [rsp+0x8]
//   0x000000000040503e <+1550>:  add    rcx,rax
//   0x0000000000405041 <+1553>:  mov    r9,QWORD PTR [rsp]
//   0x0000000000405045 <+1557>:  jmp    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ movl(rax, rbx);
    __ movq(rcx, Address(rsp, 0x8));
    __ addq(rcx, rax);
    __ movq(r9, Address(rsp, 0));
    __ jmpb(L_tail_3_9);

    __ bind(L_tail_2);
//   0x0000000000405047 <+1559>:  add    rax,0xffffffffffffffc0
//   0x000000000040504b <+1563>:  mov    rdx,rax
    __ addq(rax, -64);
    __ movq(rdx, rax);

    __ bind(L_tail_3);
//   0x000000000040504e <+1566>:  xor    ecx,ecx
//   0x0000000000405050 <+1568>:  tzcnt  ecx,esi
//   0x0000000000405054 <+1572>:  or     rcx,rdx
//   0x0000000000405057 <+1575>:  jmp    0x405017 <_Z14avx2_strstr_v2PKcmS0_m+1511> L_tail_3_9
    __ xorl(rcx, rcx);
    __ tzcntl(rcx, rsi);
    __ orq(rcx, rdx);
    __ jmpb(L_tail_3_9);

    __ align(8);

    jump_table = __ pc();

    for(jmp_ndx = 0; jmp_ndx < 12; jmp_ndx++) {
      __ emit_address(jmp_table[jmp_ndx]);
    }

    __ align(16);
    __ bind(L_begin);
//   0x0000000000404a30 <+0>:     push   rbp
//   0x0000000000404a31 <+1>:     push   r15
//   0x0000000000404a33 <+3>:     push   r14
//   0x0000000000404a35 <+5>:     push   r13
//   0x0000000000404a37 <+7>:     push   r12
//   0x0000000000404a39 <+9>:     push   rbx
//   0x0000000000404a3a <+10>:    sub    rsp,0x68
//   0x0000000000404a3e <+14>:    mov    rax,0xffffffffffffffff
//   0x0000000000404a45 <+21>:    mov    r9,rsi
//   0x0000000000404a48 <+24>:    sub    r9,rcx
//   0x0000000000404a4b <+27>:    jb     0x405025 <_Z14avx2_strstr_v2PKcmS0_m+1525> L_exit
    __ push(rbp);
    __ push(r15);
    __ push(r14);
    __ push(r13);
    __ push(r12);
    __ push(rbx);
    __ subptr(rsp, 0x68);
    __ movq(rax, -1);
    __ movq(r9, rsi);
    __ subq(r9, rcx);
    __ jb(L_exit);

//   0x0000000000404a51 <+33>:    mov    r12,rcx
//   0x0000000000404a54 <+36>:    mov    r11,rdx
//   0x0000000000404a57 <+39>:    mov    r10,rsi
//   0x0000000000404a5a <+42>:    mov    rbx,rdi
//   0x0000000000404a5d <+45>:    cmp    rcx,0xc
//   0x0000000000404a61 <+49>:    ja     0x404a73 <_Z14avx2_strstr_v2PKcmS0_m+67> L_anysize
//   0x0000000000404a63 <+51>:    jmp    QWORD PTR [r12*8+0x416128]
    __ movq(r12, rcx);
    __ movq(r11, rdx);
    __ movq(r10, rsi);
    __ movq(rbx, rdi);
    __ cmpq(rcx, 0xc);
    __ ja(L_anysize);
    __ mov64(rax, (int64_t) jump_table);
    __ shlq(r12, 0x3);
    __ addq(rax, r12);
    __ jmp(rax);

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
//   19 #include <isa-level.h>
//   20
//   21 #if ISA_SHOULD_BUILD (3)
//   22
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
//   40
//   41
//   42 # include <sysdep.h>
//   43
//   44 # ifndef MEMCMP
//   45 #  define MEMCMP        __memcmp_avx2_movbe
//   46 # endif
//   47
//   48 # ifdef USE_AS_WMEMCMP
//   49 #  define CHAR_SIZE     4
//   50 #  define VPCMPEQ       vpcmpeqd
//   51 # else
//   52 #  define CHAR_SIZE     1
//   53 #  define VPCMPEQ       vpcmpeqb
//   54 # endif
//   55
//   56 # ifndef VZEROUPPER
//   57 #  define VZEROUPPER    vzeroupper
//   58 # endif
//   59
//   60 # ifndef SECTION
//   61 #  define SECTION(p)    p##.avx
//   62 # endif
//   63
//   64 # define VEC_SIZE 32
//   65 # define PAGE_SIZE      4096
//   66
//   67 /* Warning!
//   68            wmemcmp has to use SIGNED comparison for elements.
//   69            memcmp has to use UNSIGNED comparison for elements.
//   70 */
//   71
//   72         .section SECTION(.text),"ax",@progbits
//   73 ENTRY (MEMCMP)
//   74 # ifdef USE_AS_WMEMCMP
//   75         shl     $2, %RDX_LP
//   76 # elif defined __ILP32__
//   77         /* Clear the upper 32 bits.  */
//   78         movl    %edx, %edx
//   79 # endif
//   80         cmp     $VEC_SIZE, %RDX_LP
//   81         jb      L(less_vec)
//   82
//   83         /* From VEC to 2 * VEC.  No branch when size == VEC_SIZE.  */
//   84         vmovdqu (%rsi), %ymm1
//   85         VPCMPEQ (%rdi), %ymm1, %ymm1
//   86         vpmovmskb %ymm1, %eax
//   87         /* NB: eax must be destination register if going to
//   88            L(return_vec_[0,2]). For L(return_vec_3 destination register
//   89            must be ecx.  */
//   90         incl    %eax
//   91         jnz     L(return_vec_0)
//   92
//   93         cmpq    $(VEC_SIZE * 2), %rdx
//   94         jbe     L(last_1x_vec)
//   95
//   96         /* Check second VEC no matter what.  */
//   97         vmovdqu VEC_SIZE(%rsi), %ymm2
//   98         VPCMPEQ VEC_SIZE(%rdi), %ymm2, %ymm2
//   99         vpmovmskb %ymm2, %eax
//  100         /* If all 4 VEC where equal eax will be all 1s so incl will
//  101            overflow and set zero flag.  */
//  102         incl    %eax
//  103         jnz     L(return_vec_1)
//  104
//  105         /* Less than 4 * VEC.  */
//  106         cmpq    $(VEC_SIZE * 4), %rdx
//  107         jbe     L(last_2x_vec)
//  108
//  109         /* Check third and fourth VEC no matter what.  */
//  110         vmovdqu (VEC_SIZE * 2)(%rsi), %ymm3
//  111         VPCMPEQ (VEC_SIZE * 2)(%rdi), %ymm3, %ymm3
//  112         vpmovmskb %ymm3, %eax
//  113         incl    %eax
//  114         jnz     L(return_vec_2)
//  115         vmovdqu (VEC_SIZE * 3)(%rsi), %ymm4
//  116         VPCMPEQ (VEC_SIZE * 3)(%rdi), %ymm4, %ymm4
//  117         vpmovmskb %ymm4, %ecx
//  118         incl    %ecx
//  119         jnz     L(return_vec_3)
//  120
//  121         /* Go to 4x VEC loop.  */
//  122         cmpq    $(VEC_SIZE * 8), %rdx
//  123         ja      L(more_8x_vec)
//  124
//  125         /* Handle remainder of size = 4 * VEC + 1 to 8 * VEC without any
//  126            branches.  */
//  127
//  128         /* Load first two VEC from s2 before adjusting addresses.  */
//  129         vmovdqu -(VEC_SIZE * 4)(%rsi, %rdx), %ymm1
//  130         vmovdqu -(VEC_SIZE * 3)(%rsi, %rdx), %ymm2
//  131         leaq    -(4 * VEC_SIZE)(%rdi, %rdx), %rdi
//  132         leaq    -(4 * VEC_SIZE)(%rsi, %rdx), %rsi
//  133
//  134         /* Wait to load from s1 until addressed adjust due to
//  135            unlamination of microfusion with complex address mode.  */
//  136         VPCMPEQ (%rdi), %ymm1, %ymm1
//  137         VPCMPEQ (VEC_SIZE)(%rdi), %ymm2, %ymm2
//  138
//  139         vmovdqu (VEC_SIZE * 2)(%rsi), %ymm3
//  140         VPCMPEQ (VEC_SIZE * 2)(%rdi), %ymm3, %ymm3
//  141         vmovdqu (VEC_SIZE * 3)(%rsi), %ymm4
//  142         VPCMPEQ (VEC_SIZE * 3)(%rdi), %ymm4, %ymm4
//  143
//  144         /* Reduce VEC0 - VEC4.  */
//  145         vpand   %ymm1, %ymm2, %ymm5
//  146         vpand   %ymm3, %ymm4, %ymm6
//  147         vpand   %ymm5, %ymm6, %ymm7
//  148         vpmovmskb %ymm7, %ecx
//  149         incl    %ecx
//  150         jnz     L(return_vec_0_1_2_3)
//  151         /* NB: eax must be zero to reach here.  */
//  152         VZEROUPPER_RETURN
//  153
//  154         .p2align 4
//  155 L(return_vec_0):
//  156         tzcntl  %eax, %eax
//  157 # ifdef USE_AS_WMEMCMP
//  158         movl    (%rdi, %rax), %ecx
//  159         xorl    %edx, %edx
//  160         cmpl    (%rsi, %rax), %ecx
//  161         /* NB: no partial register stall here because xorl zero idiom
//  162            above.  */
//  163         setg    %dl
//  164         leal    -1(%rdx, %rdx), %eax
//  165 # else
//  166         movzbl  (%rsi, %rax), %ecx
//  167         movzbl  (%rdi, %rax), %eax
//  168         subl    %ecx, %eax
//  169 # endif
//  170 L(return_vzeroupper):
//  171         ZERO_UPPER_VEC_REGISTERS_RETURN
//  172
//  173         .p2align 4
//  174 L(return_vec_1):
//  175         tzcntl  %eax, %eax
//  176 # ifdef USE_AS_WMEMCMP
//  177         movl    VEC_SIZE(%rdi, %rax), %ecx
//  178         xorl    %edx, %edx
//  179         cmpl    VEC_SIZE(%rsi, %rax), %ecx
//  180         setg    %dl
//  181         leal    -1(%rdx, %rdx), %eax
//  182 # else
//  183         movzbl  VEC_SIZE(%rsi, %rax), %ecx
//  184         movzbl  VEC_SIZE(%rdi, %rax), %eax
//  185         subl    %ecx, %eax
//  186 # endif
//  187         VZEROUPPER_RETURN
//  188
//  189         .p2align 4
//  190 L(return_vec_2):
//  191         tzcntl  %eax, %eax
//  192 # ifdef USE_AS_WMEMCMP
//  193         movl    (VEC_SIZE * 2)(%rdi, %rax), %ecx
//  194         xorl    %edx, %edx
//  195         cmpl    (VEC_SIZE * 2)(%rsi, %rax), %ecx
//  196         setg    %dl
//  197         leal    -1(%rdx, %rdx), %eax
//  198 # else
//  199         movzbl  (VEC_SIZE * 2)(%rsi, %rax), %ecx
//  200         movzbl  (VEC_SIZE * 2)(%rdi, %rax), %eax
//  201         subl    %ecx, %eax
//  202 # endif
//  203         VZEROUPPER_RETURN
//  204
//  205         /* NB: p2align 5 here to ensure 4x loop is 32 byte aligned.  */
//  206         .p2align 5
//  207 L(8x_return_vec_0_1_2_3):
//  208         /* Returning from L(more_8x_vec) requires restoring rsi.  */
//  209         addq    %rdi, %rsi
//  210 L(return_vec_0_1_2_3):
//  211         vpmovmskb %ymm1, %eax
//  212         incl    %eax
//  213         jnz     L(return_vec_0)
//  214
//  215         vpmovmskb %ymm2, %eax
//  216         incl    %eax
//  217         jnz     L(return_vec_1)
//  218
//  219         vpmovmskb %ymm3, %eax
//  220         incl    %eax
//  221         jnz     L(return_vec_2)
//  222 L(return_vec_3):
//  223         tzcntl  %ecx, %ecx
//  224 # ifdef USE_AS_WMEMCMP
//  225         movl    (VEC_SIZE * 3)(%rdi, %rcx), %eax
//  226         xorl    %edx, %edx
//  227         cmpl    (VEC_SIZE * 3)(%rsi, %rcx), %eax
//  228         setg    %dl
//  229         leal    -1(%rdx, %rdx), %eax
//  230 # else
//  231         movzbl  (VEC_SIZE * 3)(%rdi, %rcx), %eax
//  232         movzbl  (VEC_SIZE * 3)(%rsi, %rcx), %ecx
//  233         subl    %ecx, %eax
//  234 # endif
//  235         VZEROUPPER_RETURN
//  236
//  237         .p2align 4
//  238 L(more_8x_vec):
//  239         /* Set end of s1 in rdx.  */
//  240         leaq    -(VEC_SIZE * 4)(%rdi, %rdx), %rdx
//  241         /* rsi stores s2 - s1. This allows loop to only update one
//  242            pointer.  */
//  243         subq    %rdi, %rsi
//  244         /* Align s1 pointer.  */
//  245         andq    $-VEC_SIZE, %rdi
//  246         /* Adjust because first 4x vec where check already.  */
//  247         subq    $-(VEC_SIZE * 4), %rdi
//  248         .p2align 4
//  249 L(loop_4x_vec):
//  250         /* rsi has s2 - s1 so get correct address by adding s1 (in rdi).
//  251          */
//  252         vmovdqu (%rsi, %rdi), %ymm1
//  253         VPCMPEQ (%rdi), %ymm1, %ymm1
//  254
//  255         vmovdqu VEC_SIZE(%rsi, %rdi), %ymm2
//  256         VPCMPEQ VEC_SIZE(%rdi), %ymm2, %ymm2
//  257
//  258         vmovdqu (VEC_SIZE * 2)(%rsi, %rdi), %ymm3
//  259         VPCMPEQ (VEC_SIZE * 2)(%rdi), %ymm3, %ymm3
//  260
//  261         vmovdqu (VEC_SIZE * 3)(%rsi, %rdi), %ymm4
//  262         VPCMPEQ (VEC_SIZE * 3)(%rdi), %ymm4, %ymm4
//  263
//  264         vpand   %ymm1, %ymm2, %ymm5
//  265         vpand   %ymm3, %ymm4, %ymm6
//  266         vpand   %ymm5, %ymm6, %ymm7
//  267         vpmovmskb %ymm7, %ecx
//  268         incl    %ecx
//  269         jnz     L(8x_return_vec_0_1_2_3)
//  270         subq    $-(VEC_SIZE * 4), %rdi
//  271         /* Check if s1 pointer at end.  */
//  272         cmpq    %rdx, %rdi
//  273         jb      L(loop_4x_vec)
//  274
//  275         subq    %rdx, %rdi
//  276         /* rdi has 4 * VEC_SIZE - remaining length.  */
//  277         cmpl    $(VEC_SIZE * 3), %edi
//  278         jae     L(8x_last_1x_vec)
//  279         /* Load regardless of branch.  */
//  280         vmovdqu (VEC_SIZE * 2)(%rsi, %rdx), %ymm3
//  281         cmpl    $(VEC_SIZE * 2), %edi
//  282         jae     L(8x_last_2x_vec)
//  283
//  284         /* Check last 4 VEC.  */
//  285         vmovdqu (%rsi, %rdx), %ymm1
//  286         VPCMPEQ (%rdx), %ymm1, %ymm1
//  287
//  288         vmovdqu VEC_SIZE(%rsi, %rdx), %ymm2
//  289         VPCMPEQ VEC_SIZE(%rdx), %ymm2, %ymm2
//  290
//  291         VPCMPEQ (VEC_SIZE * 2)(%rdx), %ymm3, %ymm3
//  292
//  293         vmovdqu (VEC_SIZE * 3)(%rsi, %rdx), %ymm4
//  294         VPCMPEQ (VEC_SIZE * 3)(%rdx), %ymm4, %ymm4
//  295
//  296         vpand   %ymm1, %ymm2, %ymm5
//  297         vpand   %ymm3, %ymm4, %ymm6
//  298         vpand   %ymm5, %ymm6, %ymm7
//  299         vpmovmskb %ymm7, %ecx
//  300         /* Restore s1 pointer to rdi.  */
//  301         movq    %rdx, %rdi
//  302         incl    %ecx
//  303         jnz     L(8x_return_vec_0_1_2_3)
//  304         /* NB: eax must be zero to reach here.  */
//  305         VZEROUPPER_RETURN
//  306
//  307         /* Only entry is from L(more_8x_vec).  */
//  308         .p2align 4
//  309 L(8x_last_2x_vec):
//  310         /* Check second to last VEC. rdx store end pointer of s1 and
//  311            ymm3 has already been loaded with second to last VEC from s2.
//  312          */
//  313         VPCMPEQ (VEC_SIZE * 2)(%rdx), %ymm3, %ymm3
//  314         vpmovmskb %ymm3, %eax
//  315         incl    %eax
//  316         jnz     L(8x_return_vec_2)
//  317         /* Check last VEC.  */
//  318         .p2align 4
//  319 L(8x_last_1x_vec):
//  320         vmovdqu (VEC_SIZE * 3)(%rsi, %rdx), %ymm4
//  321         VPCMPEQ (VEC_SIZE * 3)(%rdx), %ymm4, %ymm4
//  322         vpmovmskb %ymm4, %eax
//  323         incl    %eax
//  324         jnz     L(8x_return_vec_3)
//  325         VZEROUPPER_RETURN
//  326
//  327         .p2align 4
//  328 L(last_2x_vec):
//  329         /* Check second to last VEC.  */
//  330         vmovdqu -(VEC_SIZE * 2)(%rsi, %rdx), %ymm1
//  331         VPCMPEQ -(VEC_SIZE * 2)(%rdi, %rdx), %ymm1, %ymm1
//  332         vpmovmskb %ymm1, %eax
//  333         incl    %eax
//  334         jnz     L(return_vec_1_end)
//  335         /* Check last VEC.  */
//  336 L(last_1x_vec):
//  337         vmovdqu -(VEC_SIZE * 1)(%rsi, %rdx), %ymm1
//  338         VPCMPEQ -(VEC_SIZE * 1)(%rdi, %rdx), %ymm1, %ymm1
//  339         vpmovmskb %ymm1, %eax
//  340         incl    %eax
//  341         jnz     L(return_vec_0_end)
//  342         VZEROUPPER_RETURN
//  343
//  344         .p2align 4
//  345 L(8x_return_vec_2):
//  346         subq    $VEC_SIZE, %rdx
//  347 L(8x_return_vec_3):
//  348         tzcntl  %eax, %eax
//  349         addq    %rdx, %rax
//  350 # ifdef USE_AS_WMEMCMP
//  351         movl    (VEC_SIZE * 3)(%rax), %ecx
//  352         xorl    %edx, %edx
//  353         cmpl    (VEC_SIZE * 3)(%rsi, %rax), %ecx
//  354         setg    %dl
//  355         leal    -1(%rdx, %rdx), %eax
//  356 # else
//  357         movzbl  (VEC_SIZE * 3)(%rsi, %rax), %ecx
//  358         movzbl  (VEC_SIZE * 3)(%rax), %eax
//  359         subl    %ecx, %eax
//  360 # endif
//  361         VZEROUPPER_RETURN
//  362
//  363         .p2align 4
//  364 L(return_vec_1_end):
//  365         tzcntl  %eax, %eax
//  366         addl    %edx, %eax
//  367 # ifdef USE_AS_WMEMCMP
//  368         movl    -(VEC_SIZE * 2)(%rdi, %rax), %ecx
//  369         xorl    %edx, %edx
//  370         cmpl    -(VEC_SIZE * 2)(%rsi, %rax), %ecx
//  371         setg    %dl
//  372         leal    -1(%rdx, %rdx), %eax
//  373 # else
//  374         movzbl  -(VEC_SIZE * 2)(%rsi, %rax), %ecx
//  375         movzbl  -(VEC_SIZE * 2)(%rdi, %rax), %eax
//  376         subl    %ecx, %eax
//  377 # endif
//  378         VZEROUPPER_RETURN
//  379
//  380         .p2align 4
//  381 L(return_vec_0_end):
//  382         tzcntl  %eax, %eax
//  383         addl    %edx, %eax
//  384 # ifdef USE_AS_WMEMCMP
//  385         movl    -VEC_SIZE(%rdi, %rax), %ecx
//  386         xorl    %edx, %edx
//  387         cmpl    -VEC_SIZE(%rsi, %rax), %ecx
//  388         setg    %dl
//  389         leal    -1(%rdx, %rdx), %eax
//  390 # else
//  391         movzbl  -VEC_SIZE(%rsi, %rax), %ecx
//  392         movzbl  -VEC_SIZE(%rdi, %rax), %eax
//  393         subl    %ecx, %eax
//  394 # endif
//  395         VZEROUPPER_RETURN
//  396
//  397         .p2align 4
//  398 L(less_vec):
//  399         /* Check if one or less CHAR. This is necessary for size = 0 but
//  400            is also faster for size = CHAR_SIZE.  */
//  401         cmpl    $CHAR_SIZE, %edx
//  402         jbe     L(one_or_less)
//  403
//  404         /* Check if loading one VEC from either s1 or s2 could cause a
//  405            page cross. This can have false positives but is by far the
//  406            fastest method.  */
//  407         movl    %edi, %eax
//  408         orl     %esi, %eax
//  409         andl    $(PAGE_SIZE - 1), %eax
//  410         cmpl    $(PAGE_SIZE - VEC_SIZE), %eax
//  411         jg      L(page_cross_less_vec)
//  412
//  413         /* No page cross possible.  */
//  414         vmovdqu (%rsi), %ymm2
//  415         VPCMPEQ (%rdi), %ymm2, %ymm2
//  416         vpmovmskb %ymm2, %eax
//  417         incl    %eax
//  418         /* Result will be zero if s1 and s2 match. Otherwise first set
//  419            bit will be first mismatch.  */
//  420         bzhil   %edx, %eax, %edx
//  421         jnz     L(return_vec_0)
//  422         xorl    %eax, %eax
//  423         VZEROUPPER_RETURN
//  424
//  425         .p2align 4
//  426 L(page_cross_less_vec):
//  427         /* if USE_AS_WMEMCMP it can only be 0, 4, 8, 12, 16, 20, 24, 28
//  428            bytes.  */
//  429         cmpl    $16, %edx
//  430         jae     L(between_16_31)
//  431 # ifndef USE_AS_WMEMCMP
//  432         cmpl    $8, %edx
//  433         jae     L(between_8_15)
//  434         /* Fall through for [4, 7].  */
//  435         cmpl    $4, %edx
//  436         jb      L(between_2_3)
//  437
//  438         movbe   (%rdi), %eax
//  439         movbe   (%rsi), %ecx
//  440         shlq    $32, %rax
//  441         shlq    $32, %rcx
//  442         movbe   -4(%rdi, %rdx), %edi
//  443         movbe   -4(%rsi, %rdx), %esi
//  444         orq     %rdi, %rax
//  445         orq     %rsi, %rcx
//  446         subq    %rcx, %rax
//  447         /* Fast path for return zero.  */
//  448         jnz     L(ret_nonzero)
//  449         /* No ymm register was touched.  */
//  450         ret
//  451
//  452         .p2align 4
//  453 L(one_or_less):
//  454         jb      L(zero)
//  455         movzbl  (%rsi), %ecx
//  456         movzbl  (%rdi), %eax
//  457         subl    %ecx, %eax
//  458         /* No ymm register was touched.  */
//  459         ret
//  460
//  461         .p2align 4,, 5
//  462 L(ret_nonzero):
//  463         sbbl    %eax, %eax
//  464         orl     $1, %eax
//  465         /* No ymm register was touched.  */
//  466         ret
//  467
//  468         .p2align 4,, 2
//  469 L(zero):
//  470         xorl    %eax, %eax
//  471         /* No ymm register was touched.  */
//  472         ret
//  473
//  474         .p2align 4
//  475 L(between_8_15):
//  476         movbe   (%rdi), %rax
//  477         movbe   (%rsi), %rcx
//  478         subq    %rcx, %rax
//  479         jnz     L(ret_nonzero)
//  480         movbe   -8(%rdi, %rdx), %rax
//  481         movbe   -8(%rsi, %rdx), %rcx
//  482         subq    %rcx, %rax
//  483         /* Fast path for return zero.  */
//  484         jnz     L(ret_nonzero)
//  485         /* No ymm register was touched.  */
//  486         ret
//  487 # else
//  488         /* If USE_AS_WMEMCMP fall through into 8-15 byte case.  */
//  489         vmovq   (%rdi), %xmm1
//  490         vmovq   (%rsi), %xmm2
//  491         VPCMPEQ %xmm1, %xmm2, %xmm2
//  492         vpmovmskb %xmm2, %eax
//  493         subl    $0xffff, %eax
//  494         jnz     L(return_vec_0)
//  495         /* Use overlapping loads to avoid branches.  */
//  496         leaq    -8(%rdi, %rdx), %rdi
//  497         leaq    -8(%rsi, %rdx), %rsi
//  498         vmovq   (%rdi), %xmm1
//  499         vmovq   (%rsi), %xmm2
//  500         VPCMPEQ %xmm1, %xmm2, %xmm2
//  501         vpmovmskb %xmm2, %eax
//  502         subl    $0xffff, %eax
//  503         /* Fast path for return zero.  */
//  504         jnz     L(return_vec_0)
//  505         /* No ymm register was touched.  */
//  506         ret
//  507 # endif
//  508
//  509         .p2align 4,, 10
//  510 L(between_16_31):
//  511         /* From 16 to 31 bytes.  No branch when size == 16.  */
//  512         vmovdqu (%rsi), %xmm2
//  513         VPCMPEQ (%rdi), %xmm2, %xmm2
//  514         vpmovmskb %xmm2, %eax
//  515         subl    $0xffff, %eax
//  516         jnz     L(return_vec_0)
//  517
//  518         /* Use overlapping loads to avoid branches.  */
//  519
//  520         vmovdqu -16(%rsi, %rdx), %xmm2
//  521         leaq    -16(%rdi, %rdx), %rdi
//  522         leaq    -16(%rsi, %rdx), %rsi
//  523         VPCMPEQ (%rdi), %xmm2, %xmm2
//  524         vpmovmskb %xmm2, %eax
//  525         subl    $0xffff, %eax
//  526         /* Fast path for return zero.  */
//  527         jnz     L(return_vec_0)
//  528         /* No ymm register was touched.  */
//  529         ret
//  530
//  531 # ifdef USE_AS_WMEMCMP
//  532         .p2align 4,, 2
//  533 L(zero):
//  534         xorl    %eax, %eax
//  535         ret
//  536
//  537         .p2align 4
//  538 L(one_or_less):
//  539         jb      L(zero)
//  540         movl    (%rdi), %ecx
//  541         xorl    %edx, %edx
//  542         cmpl    (%rsi), %ecx
//  543         je      L(zero)
//  544         setg    %dl
//  545         leal    -1(%rdx, %rdx), %eax
//  546         /* No ymm register was touched.  */
//  547         ret
//  548 # else
//  549
//  550         .p2align 4
//  551 L(between_2_3):
//  552         /* Load as big endian to avoid branches.  */
//  553         movzwl  (%rdi), %eax
//  554         movzwl  (%rsi), %ecx
//  555         bswap   %eax
//  556         bswap   %ecx
//  557         shrl    %eax
//  558         shrl    %ecx
//  559         movzbl  -1(%rdi, %rdx), %edi
//  560         movzbl  -1(%rsi, %rdx), %esi
//  561         orl     %edi, %eax
//  562         orl     %esi, %ecx
//  563         /* Subtraction is okay because the upper bit is zero.  */
//  564         subl    %ecx, %eax
//  565         /* No ymm register was touched.  */
//  566         ret
//  567 # endif
//  568
//  569 END (MEMCMP)
//  570 #endif

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
    __ tzcntl(rax, rax);

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

/////////////////////////////////////////////
//  Screwed up here
/////////////////////////////////////////////

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
    __ vpmovmskb(rax, xmm2, Assembler::AVX_256bit);
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
    __ vpmovmskb(rax, xmm2, Assembler::AVX_256bit);
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

#if 0
// Dump of assembler code for function __strchr_avx2:
//   0x0000000000408f80 <+0>:     mov    ecx,edi
//   0x0000000000408f82 <+2>:     vmovd  xmm0,esi
//   0x0000000000408f86 <+6>:     vpxor  xmm9,xmm9,xmm9
//   0x0000000000408f8b <+11>:    vpbroadcastb ymm0,xmm0
//   0x0000000000408f90 <+16>:    and    ecx,0x3f
//   0x0000000000408f93 <+19>:    cmp    ecx,0x20
//   0x0000000000408f96 <+22>:    ja     0x408fd0 <__strchr_avx2+80>
//   0x0000000000408f98 <+24>:    vmovdqu ymm8,YMMWORD PTR [rdi]
//   0x0000000000408f9c <+28>:    vpcmpeqb ymm1,ymm0,ymm8
//   0x0000000000408fa1 <+33>:    vpcmpeqb ymm2,ymm9,ymm8
//   0x0000000000408fa6 <+38>:    vpor   ymm1,ymm2,ymm1
//   0x0000000000408faa <+42>:    vpmovmskb eax,ymm1
//   0x0000000000408fae <+46>:    test   eax,eax
//   0x0000000000408fb0 <+48>:    jne    0x409110 <__strchr_avx2+400>
//   0x0000000000408fb6 <+54>:    add    rdi,0x20
//   0x0000000000408fba <+58>:    and    ecx,0x1f
//   0x0000000000408fbd <+61>:    and    rdi,0xffffffffffffffe0
//   0x0000000000408fc1 <+65>:    jmp    0x409014 <__strchr_avx2+148>
//   0x0000000000408fc3 <+67>:    data16 data16 data16 cs nop WORD PTR [rax+rax*1+0x0]

    __ align(16);
    __ bind(L_0x408fd0);
//   0x0000000000408fd0 <+80>:    and    ecx,0x1f
//   0x0000000000408fd3 <+83>:    and    rdi,0xffffffffffffffe0
//   0x0000000000408fd7 <+87>:    vmovdqu ymm8,YMMWORD PTR [rdi]
//   0x0000000000408fdb <+91>:    vpcmpeqb ymm1,ymm0,ymm8
//   0x0000000000408fe0 <+96>:    vpcmpeqb ymm2,ymm9,ymm8
//   0x0000000000408fe5 <+101>:   vpor   ymm1,ymm2,ymm1
//   0x0000000000408fe9 <+105>:   vpmovmskb eax,ymm1
//   0x0000000000408fed <+109>:   sar    eax,cl
//   0x0000000000408fef <+111>:   test   eax,eax
//   0x0000000000408ff1 <+113>:   je     0x409010 <__strchr_avx2+144>
//   0x0000000000408ff3 <+115>:   tzcnt  eax,eax
//   0x0000000000408ff7 <+119>:   add    rax,rcx
//   0x0000000000408ffa <+122>:   xor    edx,edx
//   0x0000000000408ffc <+124>:   lea    rax,[rdi+rax*1]
//   0x0000000000409000 <+128>:   cmp    sil,BYTE PTR [rax]
//   0x0000000000409003 <+131>:   cmovne rax,rdx
//   0x0000000000409007 <+135>:   vzeroupper
//   0x000000000040900a <+138>:   ret
//   0x000000000040900b <+139>:   nop    DWORD PTR [rax+rax*1+0x0]

    __ align(16);
    __ bind(L_0x409010);
//   0x0000000000409010 <+144>:   add    rdi,0x20

    __ bind(L_0x409014);
//   0x0000000000409014 <+148>:   vmovdqa ymm8,YMMWORD PTR [rdi]
//   0x0000000000409018 <+152>:   vpcmpeqb ymm1,ymm0,ymm8
//   0x000000000040901d <+157>:   vpcmpeqb ymm2,ymm9,ymm8
//   0x0000000000409022 <+162>:   vpor   ymm1,ymm2,ymm1
//   0x0000000000409026 <+166>:   vpmovmskb eax,ymm1
//   0x000000000040902a <+170>:   test   eax,eax
//   0x000000000040902c <+172>:   jne    0x409110 <__strchr_avx2+400>
//   0x0000000000409032 <+178>:   vmovdqa ymm8,YMMWORD PTR [rdi+0x20]
//   0x0000000000409037 <+183>:   vpcmpeqb ymm1,ymm0,ymm8
//   0x000000000040903c <+188>:   vpcmpeqb ymm2,ymm9,ymm8
//   0x0000000000409041 <+193>:   vpor   ymm1,ymm2,ymm1
//   0x0000000000409045 <+197>:   vpmovmskb eax,ymm1
//   0x0000000000409049 <+201>:   test   eax,eax
//   0x000000000040904b <+203>:   jne    0x409130 <__strchr_avx2+432>
//   0x0000000000409051 <+209>:   vmovdqa ymm8,YMMWORD PTR [rdi+0x40]
//   0x0000000000409056 <+214>:   vpcmpeqb ymm1,ymm0,ymm8
//   0x000000000040905b <+219>:   vpcmpeqb ymm2,ymm9,ymm8
//   0x0000000000409060 <+224>:   vpor   ymm1,ymm2,ymm1
//   0x0000000000409064 <+228>:   vpmovmskb eax,ymm1
//   0x0000000000409068 <+232>:   test   eax,eax
//   0x000000000040906a <+234>:   jne    0x409150 <__strchr_avx2+464>
//   0x0000000000409070 <+240>:   vmovdqa ymm8,YMMWORD PTR [rdi+0x60]
//   0x0000000000409075 <+245>:   vpcmpeqb ymm1,ymm0,ymm8
//   0x000000000040907a <+250>:   vpcmpeqb ymm2,ymm9,ymm8
//   0x000000000040907f <+255>:   vpor   ymm1,ymm2,ymm1
//   0x0000000000409083 <+259>:   vpmovmskb eax,ymm1
//   0x0000000000409087 <+263>:   test   eax,eax
//   0x0000000000409089 <+265>:   jne    0x40918e <__strchr_avx2+526>
//   0x000000000040908f <+271>:   add    rdi,0x80
//   0x0000000000409096 <+278>:   mov    rcx,rdi
//   0x0000000000409099 <+281>:   and    ecx,0x7f
//   0x000000000040909c <+284>:   and    rdi,0xffffffffffffff80

    __ align(16);
    __ bind(L_0x4090a0);
//   0x00000000004090a0 <+288>:   vmovdqa ymm5,YMMWORD PTR [rdi]
//   0x00000000004090a4 <+292>:   vmovdqa ymm6,YMMWORD PTR [rdi+0x20]
//   0x00000000004090a9 <+297>:   vmovdqa ymm7,YMMWORD PTR [rdi+0x40]
//   0x00000000004090ae <+302>:   vmovdqa ymm8,YMMWORD PTR [rdi+0x60]
//   0x00000000004090b3 <+307>:   vpcmpeqb ymm1,ymm0,ymm5
//   0x00000000004090b7 <+311>:   vpcmpeqb ymm2,ymm0,ymm6
//   0x00000000004090bb <+315>:   vpcmpeqb ymm3,ymm0,ymm7
//   0x00000000004090bf <+319>:   vpcmpeqb ymm4,ymm0,ymm8
//   0x00000000004090c4 <+324>:   vpcmpeqb ymm5,ymm9,ymm5
//   0x00000000004090c8 <+328>:   vpcmpeqb ymm6,ymm9,ymm6
//   0x00000000004090cc <+332>:   vpcmpeqb ymm7,ymm9,ymm7
//   0x00000000004090d0 <+336>:   vpcmpeqb ymm8,ymm9,ymm8
//   0x00000000004090d5 <+341>:   vpor   ymm1,ymm5,ymm1
//   0x00000000004090d9 <+345>:   vpor   ymm2,ymm6,ymm2
//   0x00000000004090dd <+349>:   vpor   ymm3,ymm7,ymm3
//   0x00000000004090e1 <+353>:   vpor   ymm4,ymm8,ymm4
//   0x00000000004090e5 <+357>:   vpor   ymm5,ymm2,ymm1
//   0x00000000004090e9 <+361>:   vpor   ymm6,ymm4,ymm3
//   0x00000000004090ed <+365>:   vpor   ymm5,ymm6,ymm5
//   0x00000000004090f1 <+369>:   vpmovmskb eax,ymm5
//   0x00000000004090f5 <+373>:   test   eax,eax
//   0x00000000004090f7 <+375>:   jne    0x409170 <__strchr_avx2+496>
//   0x00000000004090f9 <+377>:   add    rdi,0x80
//   0x0000000000409100 <+384>:   jmp    0x4090a0 <__strchr_avx2+288>
//   0x0000000000409102 <+386>:   data16 data16 data16 data16 cs nop WORD PTR [rax+rax*1+0x0]

    __ align(16);
    __ bind(L_0x409110);
//   0x0000000000409110 <+400>:   tzcnt  eax,eax
//   0x0000000000409114 <+404>:   xor    edx,edx
//   0x0000000000409116 <+406>:   lea    rax,[rdi+rax*1]
//   0x000000000040911a <+410>:   cmp    sil,BYTE PTR [rax]
//   0x000000000040911d <+413>:   cmovne rax,rdx
//   0x0000000000409121 <+417>:   vzeroupper
//   0x0000000000409124 <+420>:   ret
//   0x0000000000409125 <+421>:   data16 cs nop WORD PTR [rax+rax*1+0x0]

    __ align(16);
    __ bind(L_0x409130);
//   0x0000000000409130 <+432>:   tzcnt  eax,eax
//   0x0000000000409134 <+436>:   xor    edx,edx
//   0x0000000000409136 <+438>:   lea    rax,[rdi+rax*1+0x20]
//   0x000000000040913b <+443>:   cmp    sil,BYTE PTR [rax]
//   0x000000000040913e <+446>:   cmovne rax,rdx
//   0x0000000000409142 <+450>:   vzeroupper
//   0x0000000000409145 <+453>:   ret
//   0x0000000000409146 <+454>:   cs nop WORD PTR [rax+rax*1+0x0]

    __ align(16);
    __ bind(L_0x409150);
//   0x0000000000409150 <+464>:   tzcnt  eax,eax
//   0x0000000000409154 <+468>:   xor    edx,edx
//   0x0000000000409156 <+470>:   lea    rax,[rdi+rax*1+0x40]
//   0x000000000040915b <+475>:   cmp    sil,BYTE PTR [rax]
//   0x000000000040915e <+478>:   cmovne rax,rdx
//   0x0000000000409162 <+482>:   vzeroupper
//   0x0000000000409165 <+485>:   ret
//   0x0000000000409166 <+486>:   cs nop WORD PTR [rax+rax*1+0x0]

    __ align(16);
    __ bind(L_0x409170);
//   0x0000000000409170 <+496>:   vpmovmskb eax,ymm1
//   0x0000000000409174 <+500>:   test   eax,eax
//   0x0000000000409176 <+502>:   jne    0x409110 <__strchr_avx2+400>
//   0x0000000000409178 <+504>:   vpmovmskb eax,ymm2
//   0x000000000040917c <+508>:   test   eax,eax
//   0x000000000040917e <+510>:   jne    0x409130 <__strchr_avx2+432>
//   0x0000000000409180 <+512>:   vpmovmskb eax,ymm3
//   0x0000000000409184 <+516>:   test   eax,eax
//   0x0000000000409186 <+518>:   jne    0x409150 <__strchr_avx2+464>
//   0x0000000000409188 <+520>:   vpmovmskb eax,ymm4
//   0x000000000040918c <+524>:   test   eax,eax

    __ bind(L_0x40918e);
//   0x000000000040918e <+526>:   tzcnt  eax,eax
//   0x0000000000409192 <+530>:   xor    edx,edx
//   0x0000000000409194 <+532>:   lea    rax,[rdi+rax*1+0x60]
//   0x0000000000409199 <+537>:   cmp    sil,BYTE PTR [rax]
//   0x000000000040919c <+540>:   cmovne rax,rdx
//   0x00000000004091a0 <+544>:   vzeroupper
//   0x00000000004091a3 <+547>:   ret
#endif

  } else {                                       // SSE version
    assert(false, "Only supports AVX2");
  }

  return start;
}

#undef __
