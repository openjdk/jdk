/*
* Copyright (c) 2016, 2025, Intel Corporation. All rights reserved.
* Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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
#include "stubGenerator_x86_64.hpp"

/******************************************************************************/
//                     ALGORITHM DESCRIPTION - EXP()
//                     ---------------------
//
// Description:
//  Let K = 64 (table size).
//        x    x/log(2)     n
//       e  = 2          = 2 * T[j] * (1 + P(y))
//  where
//       x = m*log(2)/K + y,    y in [-log(2)/K..log(2)/K]
//       m = n*K + j,           m,n,j - signed integer, j in [-K/2..K/2]
//                  j/K
//       values of 2   are tabulated as T[j] = T_hi[j] ( 1 + T_lo[j]).
//
//       P(y) is a minimax polynomial approximation of exp(x)-1
//       on small interval [-log(2)/K..log(2)/K] (were calculated by Maple V).
//
//  To avoid problems with arithmetic overflow and underflow,
//            n                        n1  n2
//  value of 2  is safely computed as 2 * 2 where n1 in [-BIAS/2..BIAS/2]
//  where BIAS is a value of exponent bias.
//
// Special cases:
//  exp(NaN) = NaN
//  exp(+INF) = +INF
//  exp(-INF) = 0
//  exp(x) = 1 for subnormals
//  for finite argument, only exp(0)=1 is exact
//  For IEEE double
//    if x >  709.782712893383973096 then exp(x) overflow
//    if x < -745.133219101941108420 then exp(x) underflow
//
/******************************************************************************/

ATTRIBUTE_ALIGNED(16) static const juint _cv[] =
{
    0x652b82feUL, 0x40571547UL, 0x652b82feUL, 0x40571547UL, 0xfefa0000UL,
    0x3f862e42UL, 0xfefa0000UL, 0x3f862e42UL, 0xbc9e3b3aUL, 0x3d1cf79aUL,
    0xbc9e3b3aUL, 0x3d1cf79aUL, 0xfffffffeUL, 0x3fdfffffUL, 0xfffffffeUL,
    0x3fdfffffUL, 0xe3289860UL, 0x3f56c15cUL, 0x555b9e25UL, 0x3fa55555UL,
    0xc090cf0fUL, 0x3f811115UL, 0x55548ba1UL, 0x3fc55555UL
};

ATTRIBUTE_ALIGNED(16) static const juint _mmask[] =
{
    0xffffffc0UL, 0x00000000UL, 0xffffffc0UL, 0x00000000UL
};

ATTRIBUTE_ALIGNED(16) static const juint _bias[] =
{
    0x0000ffc0UL, 0x00000000UL, 0x0000ffc0UL, 0x00000000UL
};

ATTRIBUTE_ALIGNED(16) static const juint _Tbl_addr[] =
{
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x0e03754dUL,
    0x3cad7bbfUL, 0x3e778060UL, 0x00002c9aUL, 0x3567f613UL, 0x3c8cd252UL,
    0xd3158574UL, 0x000059b0UL, 0x61e6c861UL, 0x3c60f74eUL, 0x18759bc8UL,
    0x00008745UL, 0x5d837b6cUL, 0x3c979aa6UL, 0x6cf9890fUL, 0x0000b558UL,
    0x702f9cd1UL, 0x3c3ebe3dUL, 0x32d3d1a2UL, 0x0000e3ecUL, 0x1e63bcd8UL,
    0x3ca3516eUL, 0xd0125b50UL, 0x00011301UL, 0x26f0387bUL, 0x3ca4c554UL,
    0xaea92ddfUL, 0x0001429aUL, 0x62523fb6UL, 0x3ca95153UL, 0x3c7d517aUL,
    0x000172b8UL, 0x3f1353bfUL, 0x3c8b898cUL, 0xeb6fcb75UL, 0x0001a35bUL,
    0x3e3a2f5fUL, 0x3c9aecf7UL, 0x3168b9aaUL, 0x0001d487UL, 0x44a6c38dUL,
    0x3c8a6f41UL, 0x88628cd6UL, 0x0002063bUL, 0xe3a8a894UL, 0x3c968efdUL,
    0x6e756238UL, 0x0002387aUL, 0x981fe7f2UL, 0x3c80472bUL, 0x65e27cddUL,
    0x00026b45UL, 0x6d09ab31UL, 0x3c82f7e1UL, 0xf51fdee1UL, 0x00029e9dUL,
    0x720c0ab3UL, 0x3c8b3782UL, 0xa6e4030bUL, 0x0002d285UL, 0x4db0abb6UL,
    0x3c834d75UL, 0x0a31b715UL, 0x000306feUL, 0x5dd3f84aUL, 0x3c8fdd39UL,
    0xb26416ffUL, 0x00033c08UL, 0xcc187d29UL, 0x3ca12f8cUL, 0x373aa9caUL,
    0x000371a7UL, 0x738b5e8bUL, 0x3ca7d229UL, 0x34e59ff6UL, 0x0003a7dbUL,
    0xa72a4c6dUL, 0x3c859f48UL, 0x4c123422UL, 0x0003dea6UL, 0x259d9205UL,
    0x3ca8b846UL, 0x21f72e29UL, 0x0004160aUL, 0x60c2ac12UL, 0x3c4363edUL,
    0x6061892dUL, 0x00044e08UL, 0xdaa10379UL, 0x3c6ecce1UL, 0xb5c13cd0UL,
    0x000486a2UL, 0xbb7aafb0UL, 0x3c7690ceUL, 0xd5362a27UL, 0x0004bfdaUL,
    0x9b282a09UL, 0x3ca083ccUL, 0x769d2ca6UL, 0x0004f9b2UL, 0xc1aae707UL,
    0x3ca509b0UL, 0x569d4f81UL, 0x0005342bUL, 0x18fdd78eUL, 0x3c933505UL,
    0x36b527daUL, 0x00056f47UL, 0xe21c5409UL, 0x3c9063e1UL, 0xdd485429UL,
    0x0005ab07UL, 0x2b64c035UL, 0x3c9432e6UL, 0x15ad2148UL, 0x0005e76fUL,
    0x99f08c0aUL, 0x3ca01284UL, 0xb03a5584UL, 0x0006247eUL, 0x0073dc06UL,
    0x3c99f087UL, 0x82552224UL, 0x00066238UL, 0x0da05571UL, 0x3c998d4dUL,
    0x667f3bccUL, 0x0006a09eUL, 0x86ce4786UL, 0x3ca52bb9UL, 0x3c651a2eUL,
    0x0006dfb2UL, 0x206f0dabUL, 0x3ca32092UL, 0xe8ec5f73UL, 0x00071f75UL,
    0x8e17a7a6UL, 0x3ca06122UL, 0x564267c8UL, 0x00075febUL, 0x461e9f86UL,
    0x3ca244acUL, 0x73eb0186UL, 0x0007a114UL, 0xabd66c55UL, 0x3c65ebe1UL,
    0x36cf4e62UL, 0x0007e2f3UL, 0xbbff67d0UL, 0x3c96fe9fUL, 0x994cce12UL,
    0x00082589UL, 0x14c801dfUL, 0x3c951f14UL, 0x9b4492ecUL, 0x000868d9UL,
    0xc1f0eab4UL, 0x3c8db72fUL, 0x422aa0dbUL, 0x0008ace5UL, 0x59f35f44UL,
    0x3c7bf683UL, 0x99157736UL, 0x0008f1aeUL, 0x9c06283cUL, 0x3ca360baUL,
    0xb0cdc5e4UL, 0x00093737UL, 0x20f962aaUL, 0x3c95e8d1UL, 0x9fde4e4fUL,
    0x00097d82UL, 0x2b91ce27UL, 0x3c71affcUL, 0x82a3f090UL, 0x0009c491UL,
    0x589a2ebdUL, 0x3c9b6d34UL, 0x7b5de564UL, 0x000a0c66UL, 0x9ab89880UL,
    0x3c95277cUL, 0xb23e255cUL, 0x000a5503UL, 0x6e735ab3UL, 0x3c846984UL,
    0x5579fdbfUL, 0x000a9e6bUL, 0x92cb3387UL, 0x3c8c1a77UL, 0x995ad3adUL,
    0x000ae89fUL, 0xdc2d1d96UL, 0x3ca22466UL, 0xb84f15faUL, 0x000b33a2UL,
    0xb19505aeUL, 0x3ca1112eUL, 0xf2fb5e46UL, 0x000b7f76UL, 0x0a5fddcdUL,
    0x3c74ffd7UL, 0x904bc1d2UL, 0x000bcc1eUL, 0x30af0cb3UL, 0x3c736eaeUL,
    0xdd85529cUL, 0x000c199bUL, 0xd10959acUL, 0x3c84e08fUL, 0x2e57d14bUL,
    0x000c67f1UL, 0x6c921968UL, 0x3c676b2cUL, 0xdcef9069UL, 0x000cb720UL,
    0x36df99b3UL, 0x3c937009UL, 0x4a07897bUL, 0x000d072dUL, 0xa63d07a7UL,
    0x3c74a385UL, 0xdcfba487UL, 0x000d5818UL, 0xd5c192acUL, 0x3c8e5a50UL,
    0x03db3285UL, 0x000da9e6UL, 0x1c4a9792UL, 0x3c98bb73UL, 0x337b9b5eUL,
    0x000dfc97UL, 0x603a88d3UL, 0x3c74b604UL, 0xe78b3ff6UL, 0x000e502eUL,
    0x92094926UL, 0x3c916f27UL, 0xa2a490d9UL, 0x000ea4afUL, 0x41aa2008UL,
    0x3c8ec3bcUL, 0xee615a27UL, 0x000efa1bUL, 0x31d185eeUL, 0x3c8a64a9UL,
    0x5b6e4540UL, 0x000f5076UL, 0x4d91cd9dUL, 0x3c77893bUL, 0x819e90d8UL,
    0x000fa7c1UL
};

ATTRIBUTE_ALIGNED(16) static const juint _ALLONES[] =
{
    0xffffffffUL, 0xffffffffUL, 0xffffffffUL, 0xffffffffUL
};

ATTRIBUTE_ALIGNED(16) static const juint _ebias[] =
{
    0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0x3ff00000UL
};

ATTRIBUTE_ALIGNED(4) static const juint _XMAX[] =
{
    0xffffffffUL, 0x7fefffffUL
};

ATTRIBUTE_ALIGNED(4) static const juint _XMIN[] =
{
    0x00000000UL, 0x00100000UL
};

ATTRIBUTE_ALIGNED(4) static const juint _INF[] =
{
    0x00000000UL, 0x7ff00000UL
};

#define __ _masm->

address StubGenerator::generate_libmExp() {
  StubId stub_id = StubId::stubgen_dexp_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();

  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label L_2TAG_PACKET_4_0_2, L_2TAG_PACKET_5_0_2, L_2TAG_PACKET_6_0_2, L_2TAG_PACKET_7_0_2;
  Label L_2TAG_PACKET_8_0_2, L_2TAG_PACKET_9_0_2, L_2TAG_PACKET_10_0_2, L_2TAG_PACKET_11_0_2;
  Label L_2TAG_PACKET_12_0_2, B1_3, B1_5;

  address cv       = (address)_cv;
  address mmask    = (address)_mmask;
  address bias     = (address)_bias;
  address Tbl_addr = (address)_Tbl_addr;
  address ALLONES  = (address)_ALLONES;
  address ebias    = (address)_ebias;
  address XMAX     = (address)_XMAX;
  address XMIN     = (address)_XMIN;
  address INF      = (address)_INF;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  __ subq(rsp, 24);
  __ movsd(Address(rsp, 8), xmm0);
  __ unpcklpd(xmm0, xmm0);
  __ movdqu(xmm1, ExternalAddress(cv),      r11 /*rscratch*/); // 0x652b82feUL, 0x40571547UL, 0x652b82feUL, 0x40571547UL
  __ movdqu(xmm2, ExternalAddress(cv + 16), r11 /*rscratch*/); // 0xfefa0000UL, 0x3f862e42UL, 0xfefa0000UL, 0x3f862e42UL
  __ movdqu(xmm3, ExternalAddress(cv + 32), r11 /*rscratch*/); // 0xbc9e3b3aUL, 0x3d1cf79aUL, 0xbc9e3b3aUL, 0x3d1cf79aUL
  __ movdqu(xmm6, ExternalAddress(SHIFTER), r11 /*rscratch*/); // 0x00000000UL, 0x43380000UL, 0x00000000UL, 0x43380000UL
  __ pextrw(rax, xmm0, 3);
  __ andl(rax, 32767);
  __ movl(rdx, 16527);
  __ subl(rdx, rax);
  __ subl(rax, 15504);
  __ orl(rdx, rax);
  __ cmpl(rdx, INT_MIN);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_0_0_2);
  __ mulpd(xmm1, xmm0);
  __ addpd(xmm1, xmm6);
  __ movapd(xmm7, xmm1);
  __ subpd(xmm1, xmm6);
  __ mulpd(xmm2, xmm1);
  __ movdqu(xmm4, ExternalAddress(cv + 64), r11 /*rscratch*/);  // 0xe3289860UL, 0x3f56c15cUL, 0x555b9e25UL, 0x3fa55555UL
  __ mulpd(xmm3, xmm1);
  __ movdqu(xmm5, ExternalAddress(cv + 80), r11 /*rscratch*/);  // 0xc090cf0fUL, 0x3f811115UL, 0x55548ba1UL, 0x3fc55555UL
  __ subpd(xmm0, xmm2);
  __ movdl(rax, xmm7);
  __ movl(rcx, rax);
  __ andl(rcx, 63);
  __ shll(rcx, 4);
  __ sarl(rax, 6);
  __ movl(rdx, rax);
  __ movdqu(xmm6, ExternalAddress(mmask), r11 /*rscratch*/);    // 0xffffffc0UL, 0x00000000UL, 0xffffffc0UL, 0x00000000UL
  __ pand(xmm7, xmm6);
  __ movdqu(xmm6, ExternalAddress(bias), r11 /*rscratch*/);     // 0x0000ffc0UL, 0x00000000UL, 0x0000ffc0UL, 0x00000000UL
  __ paddq(xmm7, xmm6);
  __ psllq(xmm7, 46);
  __ subpd(xmm0, xmm3);
  __ lea(r11, ExternalAddress(Tbl_addr));
  __ movdqu(xmm2, Address(rcx, r11));
  __ mulpd(xmm4, xmm0);
  __ movapd(xmm6, xmm0);
  __ movapd(xmm1, xmm0);
  __ mulpd(xmm6, xmm6);
  __ mulpd(xmm0, xmm6);
  __ addpd(xmm5, xmm4);
  __ mulsd(xmm0, xmm6);
  __ mulpd(xmm6, ExternalAddress(cv + 48), r11 /*rscratch*/);     // 0xfffffffeUL, 0x3fdfffffUL, 0xfffffffeUL, 0x3fdfffffUL
  __ addsd(xmm1, xmm2);
  __ unpckhpd(xmm2, xmm2);
  __ mulpd(xmm0, xmm5);
  __ addsd(xmm1, xmm0);
  __ por(xmm2, xmm7);
  __ unpckhpd(xmm0, xmm0);
  __ addsd(xmm0, xmm1);
  __ addsd(xmm0, xmm6);
  __ addl(rdx, 894);
  __ cmpl(rdx, 1916);
  __ jcc(Assembler::above, L_2TAG_PACKET_1_0_2);
  __ mulsd(xmm0, xmm2);
  __ addsd(xmm0, xmm2);
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_1_0_2);
  __ xorpd(xmm3, xmm3);
  __ movdqu(xmm4, ExternalAddress(ALLONES), r11 /*rscratch*/);  // 0xffffffffUL, 0xffffffffUL, 0xffffffffUL, 0xffffffffUL
  __ movl(rdx, -1022);
  __ subl(rdx, rax);
  __ movdl(xmm5, rdx);
  __ psllq(xmm4, xmm5);
  __ movl(rcx, rax);
  __ sarl(rax, 1);
  __ pinsrw(xmm3, rax, 3);
  __ movdqu(xmm6, ExternalAddress(ebias), r11 /*rscratch*/);    // 0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0x3ff00000UL
  __ psllq(xmm3, 4);
  __ psubd(xmm2, xmm3);
  __ mulsd(xmm0, xmm2);
  __ cmpl(rdx, 52);
  __ jcc(Assembler::greater, L_2TAG_PACKET_2_0_2);
  __ pand(xmm4, xmm2);
  __ paddd(xmm3, xmm6);
  __ subsd(xmm2, xmm4);
  __ addsd(xmm0, xmm2);
  __ cmpl(rcx, 1023);
  __ jcc(Assembler::greaterEqual, L_2TAG_PACKET_3_0_2);
  __ pextrw(rcx, xmm0, 3);
  __ andl(rcx, 32768);
  __ orl(rdx, rcx);
  __ cmpl(rdx, 0);
  __ jcc(Assembler::equal, L_2TAG_PACKET_4_0_2);
  __ movapd(xmm6, xmm0);
  __ addsd(xmm0, xmm4);
  __ mulsd(xmm0, xmm3);
  __ pextrw(rcx, xmm0, 3);
  __ andl(rcx, 32752);
  __ cmpl(rcx, 0);
  __ jcc(Assembler::equal, L_2TAG_PACKET_5_0_2);
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_5_0_2);
  __ mulsd(xmm6, xmm3);
  __ mulsd(xmm4, xmm3);
  __ movdqu(xmm0, xmm6);
  __ pxor(xmm6, xmm4);
  __ psrad(xmm6, 31);
  __ pshufd(xmm6, xmm6, 85);
  __ psllq(xmm0, 1);
  __ psrlq(xmm0, 1);
  __ pxor(xmm0, xmm6);
  __ psrlq(xmm6, 63);
  __ paddq(xmm0, xmm6);
  __ paddq(xmm0, xmm4);
  __ movl(Address(rsp, 0), 15);
  __ jmp(L_2TAG_PACKET_6_0_2);

  __ bind(L_2TAG_PACKET_4_0_2);
  __ addsd(xmm0, xmm4);
  __ mulsd(xmm0, xmm3);
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_3_0_2);
  __ addsd(xmm0, xmm4);
  __ mulsd(xmm0, xmm3);
  __ pextrw(rcx, xmm0, 3);
  __ andl(rcx, 32752);
  __ cmpl(rcx, 32752);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_7_0_2);
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_2_0_2);
  __ paddd(xmm3, xmm6);
  __ addpd(xmm0, xmm2);
  __ mulsd(xmm0, xmm3);
  __ movl(Address(rsp, 0), 15);
  __ jmp(L_2TAG_PACKET_6_0_2);

  __ bind(L_2TAG_PACKET_8_0_2);
  __ cmpl(rax, 2146435072);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_9_0_2);
  __ movl(rax, Address(rsp, 12));
  __ cmpl(rax, INT_MIN);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_10_0_2);
  __ movsd(xmm0, ExternalAddress(XMAX), r11 /*rscratch*/);      // 0xffffffffUL, 0x7fefffffUL
  __ mulsd(xmm0, xmm0);

  __ bind(L_2TAG_PACKET_7_0_2);
  __ movl(Address(rsp, 0), 14);
  __ jmp(L_2TAG_PACKET_6_0_2);

  __ bind(L_2TAG_PACKET_10_0_2);
  __ movsd(xmm0, ExternalAddress(XMIN), r11 /*rscratch*/);      // 0x00000000UL, 0x00100000UL
  __ mulsd(xmm0, xmm0);
  __ movl(Address(rsp, 0), 15);
  __ jmp(L_2TAG_PACKET_6_0_2);

  __ bind(L_2TAG_PACKET_9_0_2);
  __ movl(rdx, Address(rsp, 8));
  __ cmpl(rax, 2146435072);
  __ jcc(Assembler::above, L_2TAG_PACKET_11_0_2);
  __ cmpl(rdx, 0);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_11_0_2);
  __ movl(rax, Address(rsp, 12));
  __ cmpl(rax, 2146435072);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_12_0_2);
  __ movsd(xmm0, ExternalAddress(INF), r11 /*rscratch*/);       // 0x00000000UL, 0x7ff00000UL
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_12_0_2);
  __ movsd(xmm0, ExternalAddress(ZERO), r11 /*rscratch*/);      // 0x00000000UL, 0x00000000UL
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_11_0_2);
  __ movsd(xmm0, Address(rsp, 8));
  __ addsd(xmm0, xmm0);
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_0_0_2);
  __ movl(rax, Address(rsp, 12));
  __ andl(rax, 2147483647);
  __ cmpl(rax, 1083179008);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_8_0_2);
  __ movsd(Address(rsp, 8), xmm0);
  __ addsd(xmm0, ExternalAddress(ONE), r11 /*rscratch*/); // 0x00000000UL, 0x3ff00000UL
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_6_0_2);
  __ movq(Address(rsp, 16), xmm0);

  __ bind(B1_3);
  __ movq(xmm0, Address(rsp, 16));

  __ bind(B1_5);
  __ addq(rsp, 24);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

#undef __
