/*
 * Copyright (c) 2015, Intel Corporation.
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
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "macroAssembler_x86.hpp"

#ifdef _MSC_VER
#define ALIGNED_(x) __declspec(align(x))
#else
#define ALIGNED_(x) __attribute__ ((aligned(x)))
#endif

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

#ifdef _LP64

ALIGNED_(16) juint _cv[] =
{
    0x652b82feUL, 0x40571547UL, 0x652b82feUL, 0x40571547UL, 0xfefa0000UL,
    0x3f862e42UL, 0xfefa0000UL, 0x3f862e42UL, 0xbc9e3b3aUL, 0x3d1cf79aUL,
    0xbc9e3b3aUL, 0x3d1cf79aUL, 0xfffffffeUL, 0x3fdfffffUL, 0xfffffffeUL,
    0x3fdfffffUL, 0xe3289860UL, 0x3f56c15cUL, 0x555b9e25UL, 0x3fa55555UL,
    0xc090cf0fUL, 0x3f811115UL, 0x55548ba1UL, 0x3fc55555UL
};

ALIGNED_(16) juint _shifter[] =
{
    0x00000000UL, 0x43380000UL, 0x00000000UL, 0x43380000UL
};

ALIGNED_(16) juint _mmask[] =
{
    0xffffffc0UL, 0x00000000UL, 0xffffffc0UL, 0x00000000UL
};

ALIGNED_(16) juint _bias[] =
{
    0x0000ffc0UL, 0x00000000UL, 0x0000ffc0UL, 0x00000000UL
};

ALIGNED_(16) juint _Tbl_addr[] =
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

ALIGNED_(16) juint _ALLONES[] =
{
    0xffffffffUL, 0xffffffffUL, 0xffffffffUL, 0xffffffffUL
};

ALIGNED_(16) juint _ebias[] =
{
    0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0x3ff00000UL
};

ALIGNED_(4) juint _XMAX[] =
{
    0xffffffffUL, 0x7fefffffUL
};

ALIGNED_(4) juint _XMIN[] =
{
    0x00000000UL, 0x00100000UL
};

ALIGNED_(4) juint _INF[] =
{
    0x00000000UL, 0x7ff00000UL
};

ALIGNED_(4) juint _ZERO[] =
{
    0x00000000UL, 0x00000000UL
};

ALIGNED_(4) juint _ONE_val[] =
{
    0x00000000UL, 0x3ff00000UL
};


// Registers:
// input: xmm0
// scratch: xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
//          rax, rdx, rcx, tmp - r11

// Code generated by Intel C compiler for LIBM library

void MacroAssembler::fast_exp(XMMRegister xmm0, XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3, XMMRegister xmm4, XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7, Register eax, Register ecx, Register edx, Register tmp) {
  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label L_2TAG_PACKET_4_0_2, L_2TAG_PACKET_5_0_2, L_2TAG_PACKET_6_0_2, L_2TAG_PACKET_7_0_2;
  Label L_2TAG_PACKET_8_0_2, L_2TAG_PACKET_9_0_2, L_2TAG_PACKET_10_0_2, L_2TAG_PACKET_11_0_2;
  Label L_2TAG_PACKET_12_0_2, B1_3, B1_5, start;

  assert_different_registers(tmp, eax, ecx, edx);
  jmp(start);
  address cv = (address)_cv;
  address Shifter = (address)_shifter;
  address mmask = (address)_mmask;
  address bias = (address)_bias;
  address Tbl_addr = (address)_Tbl_addr;
  address ALLONES = (address)_ALLONES;
  address ebias = (address)_ebias;
  address XMAX = (address)_XMAX;
  address XMIN = (address)_XMIN;
  address INF = (address)_INF;
  address ZERO = (address)_ZERO;
  address ONE_val = (address)_ONE_val;

  bind(start);
  subq(rsp, 24);
  movsd(Address(rsp, 8), xmm0);
  unpcklpd(xmm0, xmm0);
  movdqu(xmm1, ExternalAddress(cv));       // 0x652b82feUL, 0x40571547UL, 0x652b82feUL, 0x40571547UL
  movdqu(xmm6, ExternalAddress(Shifter));  // 0x00000000UL, 0x43380000UL, 0x00000000UL, 0x43380000UL
  movdqu(xmm2, ExternalAddress(16+cv));    // 0xfefa0000UL, 0x3f862e42UL, 0xfefa0000UL, 0x3f862e42UL
  movdqu(xmm3, ExternalAddress(32+cv));    // 0xbc9e3b3aUL, 0x3d1cf79aUL, 0xbc9e3b3aUL, 0x3d1cf79aUL
  pextrw(eax, xmm0, 3);
  andl(eax, 32767);
  movl(edx, 16527);
  subl(edx, eax);
  subl(eax, 15504);
  orl(edx, eax);
  cmpl(edx, INT_MIN);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_0_0_2);
  mulpd(xmm1, xmm0);
  addpd(xmm1, xmm6);
  movapd(xmm7, xmm1);
  subpd(xmm1, xmm6);
  mulpd(xmm2, xmm1);
  movdqu(xmm4, ExternalAddress(64+cv));    // 0xe3289860UL, 0x3f56c15cUL, 0x555b9e25UL, 0x3fa55555UL
  mulpd(xmm3, xmm1);
  movdqu(xmm5, ExternalAddress(80+cv));    // 0xc090cf0fUL, 0x3f811115UL, 0x55548ba1UL, 0x3fc55555UL
  subpd(xmm0, xmm2);
  movdl(eax, xmm7);
  movl(ecx, eax);
  andl(ecx, 63);
  shll(ecx, 4);
  sarl(eax, 6);
  movl(edx, eax);
  movdqu(xmm6, ExternalAddress(mmask));    // 0xffffffc0UL, 0x00000000UL, 0xffffffc0UL, 0x00000000UL
  pand(xmm7, xmm6);
  movdqu(xmm6, ExternalAddress(bias));     // 0x0000ffc0UL, 0x00000000UL, 0x0000ffc0UL, 0x00000000UL
  paddq(xmm7, xmm6);
  psllq(xmm7, 46);
  subpd(xmm0, xmm3);
  lea(tmp, ExternalAddress(Tbl_addr));
  movdqu(xmm2, Address(ecx,tmp));
  mulpd(xmm4, xmm0);
  movapd(xmm6, xmm0);
  movapd(xmm1, xmm0);
  mulpd(xmm6, xmm6);
  mulpd(xmm0, xmm6);
  addpd(xmm5, xmm4);
  mulsd(xmm0, xmm6);
  mulpd(xmm6, ExternalAddress(48+cv));     // 0xfffffffeUL, 0x3fdfffffUL, 0xfffffffeUL, 0x3fdfffffUL
  addsd(xmm1, xmm2);
  unpckhpd(xmm2, xmm2);
  mulpd(xmm0, xmm5);
  addsd(xmm1, xmm0);
  por(xmm2, xmm7);
  unpckhpd(xmm0, xmm0);
  addsd(xmm0, xmm1);
  addsd(xmm0, xmm6);
  addl(edx, 894);
  cmpl(edx, 1916);
  jcc (Assembler::above, L_2TAG_PACKET_1_0_2);
  mulsd(xmm0, xmm2);
  addsd(xmm0, xmm2);
  jmp (B1_5);

  bind(L_2TAG_PACKET_1_0_2);
  xorpd(xmm3, xmm3);
  movdqu(xmm4, ExternalAddress(ALLONES));  // 0xffffffffUL, 0xffffffffUL, 0xffffffffUL, 0xffffffffUL
  movl(edx, -1022);
  subl(edx, eax);
  movdl(xmm5, edx);
  psllq(xmm4, xmm5);
  movl(ecx, eax);
  sarl(eax, 1);
  pinsrw(xmm3, eax, 3);
  movdqu(xmm6, ExternalAddress(ebias));    // 0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0x3ff00000UL
  psllq(xmm3, 4);
  psubd(xmm2, xmm3);
  mulsd(xmm0, xmm2);
  cmpl(edx, 52);
  jcc(Assembler::greater, L_2TAG_PACKET_2_0_2);
  pand(xmm4, xmm2);
  paddd(xmm3, xmm6);
  subsd(xmm2, xmm4);
  addsd(xmm0, xmm2);
  cmpl(ecx, 1023);
  jcc(Assembler::greaterEqual, L_2TAG_PACKET_3_0_2);
  pextrw(ecx, xmm0, 3);
  andl(ecx, 32768);
  orl(edx, ecx);
  cmpl(edx, 0);
  jcc(Assembler::equal, L_2TAG_PACKET_4_0_2);
  movapd(xmm6, xmm0);
  addsd(xmm0, xmm4);
  mulsd(xmm0, xmm3);
  pextrw(ecx, xmm0, 3);
  andl(ecx, 32752);
  cmpl(ecx, 0);
  jcc(Assembler::equal, L_2TAG_PACKET_5_0_2);
  jmp(B1_5);

  bind(L_2TAG_PACKET_5_0_2);
  mulsd(xmm6, xmm3);
  mulsd(xmm4, xmm3);
  movdqu(xmm0, xmm6);
  pxor(xmm6, xmm4);
  psrad(xmm6, 31);
  pshufd(xmm6, xmm6, 85);
  psllq(xmm0, 1);
  psrlq(xmm0, 1);
  pxor(xmm0, xmm6);
  psrlq(xmm6, 63);
  paddq(xmm0, xmm6);
  paddq(xmm0, xmm4);
  movl(Address(rsp,0), 15);
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_4_0_2);
  addsd(xmm0, xmm4);
  mulsd(xmm0, xmm3);
  jmp(B1_5);

  bind(L_2TAG_PACKET_3_0_2);
  addsd(xmm0, xmm4);
  mulsd(xmm0, xmm3);
  pextrw(ecx, xmm0, 3);
  andl(ecx, 32752);
  cmpl(ecx, 32752);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_7_0_2);
  jmp(B1_5);

  bind(L_2TAG_PACKET_2_0_2);
  paddd(xmm3, xmm6);
  addpd(xmm0, xmm2);
  mulsd(xmm0, xmm3);
  movl(Address(rsp,0), 15);
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_8_0_2);
  cmpl(eax, 2146435072);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_9_0_2);
  movl(eax, Address(rsp,12));
  cmpl(eax, INT_MIN);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_10_0_2);
  movsd(xmm0, ExternalAddress(XMAX));      // 0xffffffffUL, 0x7fefffffUL
  mulsd(xmm0, xmm0);

  bind(L_2TAG_PACKET_7_0_2);
  movl(Address(rsp,0), 14);
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_10_0_2);
  movsd(xmm0, ExternalAddress(XMIN));      // 0x00000000UL, 0x00100000UL
  mulsd(xmm0, xmm0);
  movl(Address(rsp,0), 15);
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_9_0_2);
  movl(edx, Address(rsp,8));
  cmpl(eax, 2146435072);
  jcc(Assembler::above, L_2TAG_PACKET_11_0_2);
  cmpl(edx, 0);
  jcc(Assembler::notEqual, L_2TAG_PACKET_11_0_2);
  movl(eax, Address(rsp,12));
  cmpl(eax, 2146435072);
  jcc(Assembler::notEqual, L_2TAG_PACKET_12_0_2);
  movsd(xmm0, ExternalAddress(INF));       // 0x00000000UL, 0x7ff00000UL
  jmp(B1_5);

  bind(L_2TAG_PACKET_12_0_2);
  movsd(xmm0, ExternalAddress(ZERO));      // 0x00000000UL, 0x00000000UL
  jmp(B1_5);

  bind(L_2TAG_PACKET_11_0_2);
  movsd(xmm0, Address(rsp, 8));
  addsd(xmm0, xmm0);
  jmp(B1_5);

  bind(L_2TAG_PACKET_0_0_2);
  movl(eax, Address(rsp, 12));
  andl(eax, 2147483647);
  cmpl(eax, 1083179008);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_8_0_2);
  movsd(Address(rsp, 8), xmm0);
  addsd(xmm0, ExternalAddress(ONE_val));   // 0x00000000UL, 0x3ff00000UL
  jmp(B1_5);

  bind(L_2TAG_PACKET_6_0_2);
  movq(Address(rsp, 16), xmm0);

  bind(B1_3);
  movq(xmm0, Address(rsp, 16));

  bind(B1_5);
  addq(rsp, 24);
}

#endif

#ifndef _LP64

ALIGNED_(16) juint _static_const_table[] =
{
    0x00000000UL, 0xfff00000UL, 0x00000000UL, 0xfff00000UL, 0xffffffc0UL,
    0x00000000UL, 0xffffffc0UL, 0x00000000UL, 0x0000ffc0UL, 0x00000000UL,
    0x0000ffc0UL, 0x00000000UL, 0x00000000UL, 0x43380000UL, 0x00000000UL,
    0x43380000UL, 0x652b82feUL, 0x40571547UL, 0x652b82feUL, 0x40571547UL,
    0xfefa0000UL, 0x3f862e42UL, 0xfefa0000UL, 0x3f862e42UL, 0xbc9e3b3aUL,
    0x3d1cf79aUL, 0xbc9e3b3aUL, 0x3d1cf79aUL, 0xfffffffeUL, 0x3fdfffffUL,
    0xfffffffeUL, 0x3fdfffffUL, 0xe3289860UL, 0x3f56c15cUL, 0x555b9e25UL,
    0x3fa55555UL, 0xc090cf0fUL, 0x3f811115UL, 0x55548ba1UL, 0x3fc55555UL,
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
    0x000fa7c1UL, 0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0x7ff00000UL,
    0x00000000UL, 0x00000000UL, 0xffffffffUL, 0x7fefffffUL, 0x00000000UL,
    0x00100000UL
};

//registers,
// input: (rbp + 8)
// scratch: xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
//          rax, rdx, rcx, rbx (tmp)

// Code generated by Intel C compiler for LIBM library

void MacroAssembler::fast_exp(XMMRegister xmm0, XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3, XMMRegister xmm4, XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7, Register eax, Register ecx, Register edx, Register tmp) {
  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label L_2TAG_PACKET_4_0_2, L_2TAG_PACKET_5_0_2, L_2TAG_PACKET_6_0_2, L_2TAG_PACKET_7_0_2;
  Label L_2TAG_PACKET_8_0_2, L_2TAG_PACKET_9_0_2, L_2TAG_PACKET_10_0_2, L_2TAG_PACKET_11_0_2;
  Label L_2TAG_PACKET_12_0_2, L_2TAG_PACKET_13_0_2, B1_3, B1_5, start;

  assert_different_registers(tmp, eax, ecx, edx);
  jmp(start);
  address static_const_table = (address)_static_const_table;

  bind(start);
  subl(rsp, 120);
  movl(Address(rsp, 64), tmp);
  lea(tmp, ExternalAddress(static_const_table));
  movdqu(xmm0, Address(rsp, 128));
  unpcklpd(xmm0, xmm0);
  movdqu(xmm1, Address(tmp, 64));          // 0x652b82feUL, 0x40571547UL, 0x652b82feUL, 0x40571547UL
  movdqu(xmm6, Address(tmp, 48));          // 0x00000000UL, 0x43380000UL, 0x00000000UL, 0x43380000UL
  movdqu(xmm2, Address(tmp, 80));          // 0xfefa0000UL, 0x3f862e42UL, 0xfefa0000UL, 0x3f862e42UL
  movdqu(xmm3, Address(tmp, 96));          // 0xbc9e3b3aUL, 0x3d1cf79aUL, 0xbc9e3b3aUL, 0x3d1cf79aUL
  pextrw(eax, xmm0, 3);
  andl(eax, 32767);
  movl(edx, 16527);
  subl(edx, eax);
  subl(eax, 15504);
  orl(edx, eax);
  cmpl(edx, INT_MIN);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_0_0_2);
  mulpd(xmm1, xmm0);
  addpd(xmm1, xmm6);
  movapd(xmm7, xmm1);
  subpd(xmm1, xmm6);
  mulpd(xmm2, xmm1);
  movdqu(xmm4, Address(tmp, 128));         // 0xe3289860UL, 0x3f56c15cUL, 0x555b9e25UL, 0x3fa55555UL
  mulpd(xmm3, xmm1);
  movdqu(xmm5, Address(tmp, 144));         // 0xc090cf0fUL, 0x3f811115UL, 0x55548ba1UL, 0x3fc55555UL
  subpd(xmm0, xmm2);
  movdl(eax, xmm7);
  movl(ecx, eax);
  andl(ecx, 63);
  shll(ecx, 4);
  sarl(eax, 6);
  movl(edx, eax);
  movdqu(xmm6, Address(tmp, 16));          // 0xffffffc0UL, 0x00000000UL, 0xffffffc0UL, 0x00000000UL
  pand(xmm7, xmm6);
  movdqu(xmm6, Address(tmp, 32));          // 0x0000ffc0UL, 0x00000000UL, 0x0000ffc0UL, 0x00000000UL
  paddq(xmm7, xmm6);
  psllq(xmm7, 46);
  subpd(xmm0, xmm3);
  movdqu(xmm2, Address(tmp, ecx, Address::times_1, 160));
  mulpd(xmm4, xmm0);
  movapd(xmm6, xmm0);
  movapd(xmm1, xmm0);
  mulpd(xmm6, xmm6);
  mulpd(xmm0, xmm6);
  addpd(xmm5, xmm4);
  mulsd(xmm0, xmm6);
  mulpd(xmm6, Address(tmp, 112));          // 0xfffffffeUL, 0x3fdfffffUL, 0xfffffffeUL, 0x3fdfffffUL
  addsd(xmm1, xmm2);
  unpckhpd(xmm2, xmm2);
  mulpd(xmm0, xmm5);
  addsd(xmm1, xmm0);
  por(xmm2, xmm7);
  unpckhpd(xmm0, xmm0);
  addsd(xmm0, xmm1);
  addsd(xmm0, xmm6);
  addl(edx, 894);
  cmpl(edx, 1916);
  jcc (Assembler::above, L_2TAG_PACKET_1_0_2);
  mulsd(xmm0, xmm2);
  addsd(xmm0, xmm2);
  jmp(L_2TAG_PACKET_2_0_2);

  bind(L_2TAG_PACKET_1_0_2);
  fnstcw(Address(rsp, 24));
  movzwl(edx, Address(rsp, 24));
  orl(edx, 768);
  movw(Address(rsp, 28), edx);
  fldcw(Address(rsp, 28));
  movl(edx, eax);
  sarl(eax, 1);
  subl(edx, eax);
  movdqu(xmm6, Address(tmp, 0));           // 0x00000000UL, 0xfff00000UL, 0x00000000UL, 0xfff00000UL
  pandn(xmm6, xmm2);
  addl(eax, 1023);
  movdl(xmm3, eax);
  psllq(xmm3, 52);
  por(xmm6, xmm3);
  addl(edx, 1023);
  movdl(xmm4, edx);
  psllq(xmm4, 52);
  movsd(Address(rsp, 8), xmm0);
  fld_d(Address(rsp, 8));
  movsd(Address(rsp, 16), xmm6);
  fld_d(Address(rsp, 16));
  fmula(1);
  faddp(1);
  movsd(Address(rsp, 8), xmm4);
  fld_d(Address(rsp, 8));
  fmulp(1);
  fstp_d(Address(rsp, 8));
  movsd(xmm0,Address(rsp, 8));
  fldcw(Address(rsp, 24));
  pextrw(ecx, xmm0, 3);
  andl(ecx, 32752);
  cmpl(ecx, 32752);
  jcc(Assembler::greaterEqual, L_2TAG_PACKET_3_0_2);
  cmpl(ecx, 0);
  jcc(Assembler::equal, L_2TAG_PACKET_4_0_2);
  jmp(L_2TAG_PACKET_2_0_2);
  cmpl(ecx, INT_MIN);
  jcc(Assembler::less, L_2TAG_PACKET_3_0_2);
  cmpl(ecx, -1064950997);
  jcc(Assembler::less, L_2TAG_PACKET_2_0_2);
  jcc(Assembler::greater, L_2TAG_PACKET_4_0_2);
  movl(edx, Address(rsp, 128));
  cmpl(edx ,-17155601);
  jcc(Assembler::less, L_2TAG_PACKET_2_0_2);
  jmp(L_2TAG_PACKET_4_0_2);

  bind(L_2TAG_PACKET_3_0_2);
  movl(edx, 14);
  jmp(L_2TAG_PACKET_5_0_2);

  bind(L_2TAG_PACKET_4_0_2);
  movl(edx, 15);

  bind(L_2TAG_PACKET_5_0_2);
  movsd(Address(rsp, 0), xmm0);
  movsd(xmm0, Address(rsp, 128));
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_7_0_2);
  cmpl(eax, 2146435072);
  jcc(Assembler::greaterEqual, L_2TAG_PACKET_8_0_2);
  movl(eax, Address(rsp, 132));
  cmpl(eax, INT_MIN);
  jcc(Assembler::greaterEqual, L_2TAG_PACKET_9_0_2);
  movsd(xmm0, Address(tmp, 1208));         // 0xffffffffUL, 0x7fefffffUL
  mulsd(xmm0, xmm0);
  movl(edx, 14);
  jmp(L_2TAG_PACKET_5_0_2);

  bind(L_2TAG_PACKET_9_0_2);
  movsd(xmm0, Address(tmp, 1216));
  mulsd(xmm0, xmm0);
  movl(edx, 15);
  jmp(L_2TAG_PACKET_5_0_2);

  bind(L_2TAG_PACKET_8_0_2);
  movl(edx, Address(rsp, 128));
  cmpl(eax, 2146435072);
  jcc(Assembler::above, L_2TAG_PACKET_10_0_2);
  cmpl(edx, 0);
  jcc(Assembler::notEqual, L_2TAG_PACKET_10_0_2);
  movl(eax, Address(rsp, 132));
  cmpl(eax, 2146435072);
  jcc(Assembler::notEqual, L_2TAG_PACKET_11_0_2);
  movsd(xmm0, Address(tmp, 1192));         // 0x00000000UL, 0x7ff00000UL
  jmp(L_2TAG_PACKET_2_0_2);

  bind(L_2TAG_PACKET_11_0_2);
  movsd(xmm0, Address(tmp, 1200));         // 0x00000000UL, 0x00000000UL
  jmp(L_2TAG_PACKET_2_0_2);

  bind(L_2TAG_PACKET_10_0_2);
  movsd(xmm0, Address(rsp, 128));
  addsd(xmm0, xmm0);
  jmp(L_2TAG_PACKET_2_0_2);

  bind(L_2TAG_PACKET_0_0_2);
  movl(eax, Address(rsp, 132));
  andl(eax, 2147483647);
  cmpl(eax, 1083179008);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_7_0_2);
  movsd(xmm0, Address(rsp, 128));
  addsd(xmm0, Address(tmp, 1184));         // 0x00000000UL, 0x3ff00000UL
  jmp(L_2TAG_PACKET_2_0_2);

  bind(L_2TAG_PACKET_2_0_2);
  movsd(Address(rsp, 48), xmm0);
  fld_d(Address(rsp, 48));

  bind(L_2TAG_PACKET_6_0_2);
  movl(tmp, Address(rsp, 64));
}

#endif

/******************************************************************************/
//                     ALGORITHM DESCRIPTION - LOG()
//                     ---------------------
//
//    x=2^k * mx, mx in [1,2)
//
//    Get B~1/mx based on the output of rcpss instruction (B0)
//    B = int((B0*2^7+0.5))/2^7
//
//    Reduced argument: r=B*mx-1.0 (computed accurately in high and low parts)
//
//    Result:  k*log(2) - log(B) + p(r) if |x-1| >= small value (2^-6)  and
//             p(r) is a degree 7 polynomial
//             -log(B) read from data table (high, low parts)
//             Result is formed from high and low parts
//
// Special cases:
//  log(NaN) = quiet NaN, and raise invalid exception
//  log(+INF) = that INF
//  log(0) = -INF with divide-by-zero exception raised
//  log(1) = +0
//  log(x) = NaN with invalid exception raised if x < -0, including -INF
//
/******************************************************************************/

#ifdef _LP64

ALIGNED_(16) juint _L_tbl[] =
{
  0xfefa3800UL, 0x3fe62e42UL, 0x93c76730UL, 0x3d2ef357UL, 0xaa241800UL,
  0x3fe5ee82UL, 0x0cda46beUL, 0x3d220238UL, 0x5c364800UL, 0x3fe5af40UL,
  0xac10c9fbUL, 0x3d2dfa63UL, 0x26bb8c00UL, 0x3fe5707aUL, 0xff3303ddUL,
  0x3d09980bUL, 0x26867800UL, 0x3fe5322eUL, 0x5d257531UL, 0x3d05ccc4UL,
  0x835a5000UL, 0x3fe4f45aUL, 0x6d93b8fbUL, 0xbd2e6c51UL, 0x6f970c00UL,
  0x3fe4b6fdUL, 0xed4c541cUL, 0x3cef7115UL, 0x27e8a400UL, 0x3fe47a15UL,
  0xf94d60aaUL, 0xbd22cb6aUL, 0xf2f92400UL, 0x3fe43d9fUL, 0x481051f7UL,
  0xbcfd984fUL, 0x2125cc00UL, 0x3fe4019cUL, 0x30f0c74cUL, 0xbd26ce79UL,
  0x0c36c000UL, 0x3fe3c608UL, 0x7cfe13c2UL, 0xbd02b736UL, 0x17197800UL,
  0x3fe38ae2UL, 0xbb5569a4UL, 0xbd218b7aUL, 0xad9d8c00UL, 0x3fe35028UL,
  0x9527e6acUL, 0x3d10b83fUL, 0x44340800UL, 0x3fe315daUL, 0xc5a0ed9cUL,
  0xbd274e93UL, 0x57b0e000UL, 0x3fe2dbf5UL, 0x07b9dc11UL, 0xbd17a6e5UL,
  0x6d0ec000UL, 0x3fe2a278UL, 0xe797882dUL, 0x3d206d2bUL, 0x1134dc00UL,
  0x3fe26962UL, 0x05226250UL, 0xbd0b61f1UL, 0xd8bebc00UL, 0x3fe230b0UL,
  0x6e48667bUL, 0x3d12fc06UL, 0x5fc61800UL, 0x3fe1f863UL, 0xc9fe81d3UL,
  0xbd2a7242UL, 0x49ae6000UL, 0x3fe1c078UL, 0xed70e667UL, 0x3cccacdeUL,
  0x40f23c00UL, 0x3fe188eeUL, 0xf8ab4650UL, 0x3d14cc4eUL, 0xf6f29800UL,
  0x3fe151c3UL, 0xa293ae49UL, 0xbd2edd97UL, 0x23c75c00UL, 0x3fe11af8UL,
  0xbb9ddcb2UL, 0xbd258647UL, 0x8611cc00UL, 0x3fe0e489UL, 0x07801742UL,
  0x3d1c2998UL, 0xe2d05400UL, 0x3fe0ae76UL, 0x887e7e27UL, 0x3d1f486bUL,
  0x0533c400UL, 0x3fe078bfUL, 0x41edf5fdUL, 0x3d268122UL, 0xbe760400UL,
  0x3fe04360UL, 0xe79539e0UL, 0xbd04c45fUL, 0xe5b20800UL, 0x3fe00e5aUL,
  0xb1727b1cUL, 0xbd053ba3UL, 0xaf7a4800UL, 0x3fdfb358UL, 0x3c164935UL,
  0x3d0085faUL, 0xee031800UL, 0x3fdf4aa7UL, 0x6f014a8bUL, 0x3d12cde5UL,
  0x56b41000UL, 0x3fdee2a1UL, 0x5a470251UL, 0x3d2f27f4UL, 0xc3ddb000UL,
  0x3fde7b42UL, 0x5372bd08UL, 0xbd246550UL, 0x1a272800UL, 0x3fde148aUL,
  0x07322938UL, 0xbd1326b2UL, 0x484c9800UL, 0x3fddae75UL, 0x60dc616aUL,
  0xbd1ea42dUL, 0x46def800UL, 0x3fdd4902UL, 0xe9a767a8UL, 0x3d235bafUL,
  0x18064800UL, 0x3fdce42fUL, 0x3ec7a6b0UL, 0xbd0797c3UL, 0xc7455800UL,
  0x3fdc7ff9UL, 0xc15249aeUL, 0xbd29b6ddUL, 0x693fa000UL, 0x3fdc1c60UL,
  0x7fe8e180UL, 0x3d2cec80UL, 0x1b80e000UL, 0x3fdbb961UL, 0xf40a666dUL,
  0x3d27d85bUL, 0x04462800UL, 0x3fdb56faUL, 0x2d841995UL, 0x3d109525UL,
  0x5248d000UL, 0x3fdaf529UL, 0x52774458UL, 0xbd217cc5UL, 0x3c8ad800UL,
  0x3fda93edUL, 0xbea77a5dUL, 0x3d1e36f2UL, 0x0224f800UL, 0x3fda3344UL,
  0x7f9d79f5UL, 0x3d23c645UL, 0xea15f000UL, 0x3fd9d32bUL, 0x10d0c0b0UL,
  0xbd26279eUL, 0x43135800UL, 0x3fd973a3UL, 0xa502d9f0UL, 0xbd152313UL,
  0x635bf800UL, 0x3fd914a8UL, 0x2ee6307dUL, 0xbd1766b5UL, 0xa88b3000UL,
  0x3fd8b639UL, 0xe5e70470UL, 0xbd205ae1UL, 0x776dc800UL, 0x3fd85855UL,
  0x3333778aUL, 0x3d2fd56fUL, 0x3bd81800UL, 0x3fd7fafaUL, 0xc812566aUL,
  0xbd272090UL, 0x687cf800UL, 0x3fd79e26UL, 0x2efd1778UL, 0x3d29ec7dUL,
  0x76c67800UL, 0x3fd741d8UL, 0x49dc60b3UL, 0x3d2d8b09UL, 0xe6af1800UL,
  0x3fd6e60eUL, 0x7c222d87UL, 0x3d172165UL, 0x3e9c6800UL, 0x3fd68ac8UL,
  0x2756eba0UL, 0x3d20a0d3UL, 0x0b3ab000UL, 0x3fd63003UL, 0xe731ae00UL,
  0xbd2db623UL, 0xdf596000UL, 0x3fd5d5bdUL, 0x08a465dcUL, 0xbd0a0b2aUL,
  0x53c8d000UL, 0x3fd57bf7UL, 0xee5d40efUL, 0x3d1fadedUL, 0x0738a000UL,
  0x3fd522aeUL, 0x8164c759UL, 0x3d2ebe70UL, 0x9e173000UL, 0x3fd4c9e0UL,
  0x1b0ad8a4UL, 0xbd2e2089UL, 0xc271c800UL, 0x3fd4718dUL, 0x0967d675UL,
  0xbd2f27ceUL, 0x23d5e800UL, 0x3fd419b4UL, 0xec90e09dUL, 0x3d08e436UL,
  0x77333000UL, 0x3fd3c252UL, 0xb606bd5cUL, 0x3d183b54UL, 0x76be1000UL,
  0x3fd36b67UL, 0xb0f177c8UL, 0x3d116ecdUL, 0xe1d36000UL, 0x3fd314f1UL,
  0xd3213cb8UL, 0xbd28e27aUL, 0x7cdc9000UL, 0x3fd2bef0UL, 0x4a5004f4UL,
  0x3d2a9cfaUL, 0x1134d800UL, 0x3fd26962UL, 0xdf5bb3b6UL, 0x3d2c93c1UL,
  0x6d0eb800UL, 0x3fd21445UL, 0xba46baeaUL, 0x3d0a87deUL, 0x635a6800UL,
  0x3fd1bf99UL, 0x5147bdb7UL, 0x3d2ca6edUL, 0xcbacf800UL, 0x3fd16b5cUL,
  0xf7a51681UL, 0x3d2b9acdUL, 0x8227e800UL, 0x3fd1178eUL, 0x63a5f01cUL,
  0xbd2c210eUL, 0x67616000UL, 0x3fd0c42dUL, 0x163ceae9UL, 0x3d27188bUL,
  0x604d5800UL, 0x3fd07138UL, 0x16ed4e91UL, 0x3cf89cdbUL, 0x5626c800UL,
  0x3fd01eaeUL, 0x1485e94aUL, 0xbd16f08cUL, 0x6cb3b000UL, 0x3fcf991cUL,
  0xca0cdf30UL, 0x3d1bcbecUL, 0xe4dd0000UL, 0x3fcef5adUL, 0x65bb8e11UL,
  0xbcca2115UL, 0xffe71000UL, 0x3fce530eUL, 0x6041f430UL, 0x3cc21227UL,
  0xb0d49000UL, 0x3fcdb13dUL, 0xf715b035UL, 0xbd2aff2aUL, 0xf2656000UL,
  0x3fcd1037UL, 0x75b6f6e4UL, 0xbd084a7eUL, 0xc6f01000UL, 0x3fcc6ffbUL,
  0xc5962bd2UL, 0xbcf1ec72UL, 0x383be000UL, 0x3fcbd087UL, 0x595412b6UL,
  0xbd2d4bc4UL, 0x575bd000UL, 0x3fcb31d8UL, 0x4eace1aaUL, 0xbd0c358dUL,
  0x3c8ae000UL, 0x3fca93edUL, 0x50562169UL, 0xbd287243UL, 0x07089000UL,
  0x3fc9f6c4UL, 0x6865817aUL, 0x3d29904dUL, 0xdcf70000UL, 0x3fc95a5aUL,
  0x58a0ff6fUL, 0x3d07f228UL, 0xeb390000UL, 0x3fc8beafUL, 0xaae92cd1UL,
  0xbd073d54UL, 0x6551a000UL, 0x3fc823c1UL, 0x9a631e83UL, 0x3d1e0ddbUL,
  0x85445000UL, 0x3fc7898dUL, 0x70914305UL, 0xbd1c6610UL, 0x8b757000UL,
  0x3fc6f012UL, 0xe59c21e1UL, 0xbd25118dUL, 0xbe8c1000UL, 0x3fc6574eUL,
  0x2c3c2e78UL, 0x3d19cf8bUL, 0x6b544000UL, 0x3fc5bf40UL, 0xeb68981cUL,
  0xbd127023UL, 0xe4a1b000UL, 0x3fc527e5UL, 0xe5697dc7UL, 0x3d2633e8UL,
  0x8333b000UL, 0x3fc4913dUL, 0x54fdb678UL, 0x3d258379UL, 0xa5993000UL,
  0x3fc3fb45UL, 0x7e6a354dUL, 0xbd2cd1d8UL, 0xb0159000UL, 0x3fc365fcUL,
  0x234b7289UL, 0x3cc62fa8UL, 0x0c868000UL, 0x3fc2d161UL, 0xcb81b4a1UL,
  0x3d039d6cUL, 0x2a49c000UL, 0x3fc23d71UL, 0x8fd3df5cUL, 0x3d100d23UL,
  0x7e23f000UL, 0x3fc1aa2bUL, 0x44389934UL, 0x3d2ca78eUL, 0x8227e000UL,
  0x3fc1178eUL, 0xce2d07f2UL, 0x3d21ef78UL, 0xb59e4000UL, 0x3fc08598UL,
  0x7009902cUL, 0xbd27e5ddUL, 0x39dbe000UL, 0x3fbfe891UL, 0x4fa10afdUL,
  0xbd2534d6UL, 0x830a2000UL, 0x3fbec739UL, 0xafe645e0UL, 0xbd2dc068UL,
  0x63844000UL, 0x3fbda727UL, 0x1fa71733UL, 0x3d1a8940UL, 0x01bc4000UL,
  0x3fbc8858UL, 0xc65aacd3UL, 0x3d2646d1UL, 0x8dad6000UL, 0x3fbb6ac8UL,
  0x2bf768e5UL, 0xbd139080UL, 0x40b1c000UL, 0x3fba4e76UL, 0xb94407c8UL,
  0xbd0e42b6UL, 0x5d594000UL, 0x3fb9335eUL, 0x3abd47daUL, 0x3d23115cUL,
  0x2f40e000UL, 0x3fb8197eUL, 0xf96ffdf7UL, 0x3d0f80dcUL, 0x0aeac000UL,
  0x3fb700d3UL, 0xa99ded32UL, 0x3cec1e8dUL, 0x4d97a000UL, 0x3fb5e95aUL,
  0x3c5d1d1eUL, 0xbd2c6906UL, 0x5d208000UL, 0x3fb4d311UL, 0x82f4e1efUL,
  0xbcf53a25UL, 0xa7d1e000UL, 0x3fb3bdf5UL, 0xa5db4ed7UL, 0x3d2cc85eUL,
  0xa4472000UL, 0x3fb2aa04UL, 0xae9c697dUL, 0xbd20b6e8UL, 0xd1466000UL,
  0x3fb1973bUL, 0x560d9e9bUL, 0xbd25325dUL, 0xb59e4000UL, 0x3fb08598UL,
  0x7009902cUL, 0xbd17e5ddUL, 0xc006c000UL, 0x3faeea31UL, 0x4fc93b7bUL,
  0xbd0e113eUL, 0xcdddc000UL, 0x3faccb73UL, 0x47d82807UL, 0xbd1a68f2UL,
  0xd0fb0000UL, 0x3faaaef2UL, 0x353bb42eUL, 0x3d20fc1aUL, 0x149fc000UL,
  0x3fa894aaUL, 0xd05a267dUL, 0xbd197995UL, 0xf2d4c000UL, 0x3fa67c94UL,
  0xec19afa2UL, 0xbd029efbUL, 0xd42e0000UL, 0x3fa466aeUL, 0x75bdfd28UL,
  0xbd2c1673UL, 0x2f8d0000UL, 0x3fa252f3UL, 0xe021b67bUL, 0x3d283e9aUL,
  0x89e74000UL, 0x3fa0415dUL, 0x5cf1d753UL, 0x3d0111c0UL, 0xec148000UL,
  0x3f9c63d2UL, 0x3f9eb2f3UL, 0x3d2578c6UL, 0x28c90000UL, 0x3f984925UL,
  0x325a0c34UL, 0xbd2aa0baUL, 0x25980000UL, 0x3f9432a9UL, 0x928637feUL,
  0x3d098139UL, 0x58938000UL, 0x3f902056UL, 0x06e2f7d2UL, 0xbd23dc5bUL,
  0xa3890000UL, 0x3f882448UL, 0xda74f640UL, 0xbd275577UL, 0x75890000UL,
  0x3f801015UL, 0x999d2be8UL, 0xbd10c76bUL, 0x59580000UL, 0x3f700805UL,
  0xcb31c67bUL, 0x3d2166afUL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
  0x80000000UL
};

ALIGNED_(16) juint _log2[] =
{
  0xfefa3800UL, 0x3fa62e42UL, 0x93c76730UL, 0x3ceef357UL
};

ALIGNED_(16) juint _coeff[] =
{
  0x92492492UL, 0x3fc24924UL, 0x00000000UL, 0xbfd00000UL, 0x3d6fb175UL,
  0xbfc5555eUL, 0x55555555UL, 0x3fd55555UL, 0x9999999aUL, 0x3fc99999UL,
  0x00000000UL, 0xbfe00000UL
};

//registers,
// input: xmm0
// scratch: xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
//          rax, rdx, rcx, r8, r11

void MacroAssembler::fast_log(XMMRegister xmm0, XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3, XMMRegister xmm4, XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7, Register eax, Register ecx, Register edx, Register tmp1, Register tmp2) {
  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label L_2TAG_PACKET_4_0_2, L_2TAG_PACKET_5_0_2, L_2TAG_PACKET_6_0_2, L_2TAG_PACKET_7_0_2;
  Label L_2TAG_PACKET_8_0_2;
  Label L_2TAG_PACKET_12_0_2, L_2TAG_PACKET_13_0_2, B1_3, B1_5, start;

  assert_different_registers(tmp1, tmp2, eax, ecx, edx);
  jmp(start);
  address L_tbl = (address)_L_tbl;
  address log2 = (address)_log2;
  address coeff = (address)_coeff;

  bind(start);
  subq(rsp, 24);
  movsd(Address(rsp, 0), xmm0);
  mov64(rax, 0x3ff0000000000000);
  movdq(xmm2, rax);
  mov64(rdx, 0x77f0000000000000);
  movdq(xmm3, rdx);
  movl(ecx, 32768);
  movdl(xmm4, rcx);
  mov64(tmp1, 0xffffe00000000000);
  movdq(xmm5, tmp1);
  movdqu(xmm1, xmm0);
  pextrw(eax, xmm0, 3);
  por(xmm0, xmm2);
  movl(ecx, 16352);
  psrlq(xmm0, 27);
  lea(tmp2, ExternalAddress(L_tbl));
  psrld(xmm0, 2);
  rcpps(xmm0, xmm0);
  psllq(xmm1, 12);
  pshufd(xmm6, xmm5, 228);
  psrlq(xmm1, 12);
  subl(eax, 16);
  cmpl(eax, 32736);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_0_0_2);

  bind(L_2TAG_PACKET_1_0_2);
  paddd(xmm0, xmm4);
  por(xmm1, xmm3);
  movdl(edx, xmm0);
  psllq(xmm0, 29);
  pand(xmm5, xmm1);
  pand(xmm0, xmm6);
  subsd(xmm1, xmm5);
  mulpd(xmm5, xmm0);
  andl(eax, 32752);
  subl(eax, ecx);
  cvtsi2sdl(xmm7, eax);
  mulsd(xmm1, xmm0);
  movq(xmm6, ExternalAddress(log2));       // 0xfefa3800UL, 0x3fa62e42UL
  movdqu(xmm3, ExternalAddress(coeff));    // 0x92492492UL, 0x3fc24924UL, 0x00000000UL, 0xbfd00000UL
  subsd(xmm5, xmm2);
  andl(edx, 16711680);
  shrl(edx, 12);
  movdqu(xmm0, Address(tmp2, edx));
  movdqu(xmm4, ExternalAddress(16 + coeff)); // 0x3d6fb175UL, 0xbfc5555eUL, 0x55555555UL, 0x3fd55555UL
  addsd(xmm1, xmm5);
  movdqu(xmm2, ExternalAddress(32 + coeff)); // 0x9999999aUL, 0x3fc99999UL, 0x00000000UL, 0xbfe00000UL
  mulsd(xmm6, xmm7);
  movddup(xmm5, xmm1);
  mulsd(xmm7, ExternalAddress(8 + log2));    // 0x93c76730UL, 0x3ceef357UL
  mulsd(xmm3, xmm1);
  addsd(xmm0, xmm6);
  mulpd(xmm4, xmm5);
  mulpd(xmm5, xmm5);
  movddup(xmm6, xmm0);
  addsd(xmm0, xmm1);
  addpd(xmm4, xmm2);
  mulpd(xmm3, xmm5);
  subsd(xmm6, xmm0);
  mulsd(xmm4, xmm1);
  pshufd(xmm2, xmm0, 238);
  addsd(xmm1, xmm6);
  mulsd(xmm5, xmm5);
  addsd(xmm7, xmm2);
  addpd(xmm4, xmm3);
  addsd(xmm1, xmm7);
  mulpd(xmm4, xmm5);
  addsd(xmm1, xmm4);
  pshufd(xmm5, xmm4, 238);
  addsd(xmm1, xmm5);
  addsd(xmm0, xmm1);
  jmp(B1_5);

  bind(L_2TAG_PACKET_0_0_2);
  movq(xmm0, Address(rsp, 0));
  movq(xmm1, Address(rsp, 0));
  addl(eax, 16);
  cmpl(eax, 32768);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_2_0_2);
  cmpl(eax, 16);
  jcc(Assembler::below, L_2TAG_PACKET_3_0_2);

  bind(L_2TAG_PACKET_4_0_2);
  addsd(xmm0, xmm0);
  jmp(B1_5);

  bind(L_2TAG_PACKET_5_0_2);
  jcc(Assembler::above, L_2TAG_PACKET_4_0_2);
  cmpl(edx, 0);
  jcc(Assembler::above, L_2TAG_PACKET_4_0_2);
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_3_0_2);
  xorpd(xmm1, xmm1);
  addsd(xmm1, xmm0);
  movdl(edx, xmm1);
  psrlq(xmm1, 32);
  movdl(ecx, xmm1);
  orl(edx, ecx);
  cmpl(edx, 0);
  jcc(Assembler::equal, L_2TAG_PACKET_7_0_2);
  xorpd(xmm1, xmm1);
  movl(eax, 18416);
  pinsrw(xmm1, eax, 3);
  mulsd(xmm0, xmm1);
  movdqu(xmm1, xmm0);
  pextrw(eax, xmm0, 3);
  por(xmm0, xmm2);
  psrlq(xmm0, 27);
  movl(ecx, 18416);
  psrld(xmm0, 2);
  rcpps(xmm0, xmm0);
  psllq(xmm1, 12);
  pshufd(xmm6, xmm5, 228);
  psrlq(xmm1, 12);
  jmp(L_2TAG_PACKET_1_0_2);

  bind(L_2TAG_PACKET_2_0_2);
  movdl(edx, xmm1);
  psrlq(xmm1, 32);
  movdl(ecx, xmm1);
  addl(ecx, ecx);
  cmpl(ecx, -2097152);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_5_0_2);
  orl(edx, ecx);
  cmpl(edx, 0);
  jcc(Assembler::equal, L_2TAG_PACKET_7_0_2);

  bind(L_2TAG_PACKET_6_0_2);
  xorpd(xmm1, xmm1);
  xorpd(xmm0, xmm0);
  movl(eax, 32752);
  pinsrw(xmm1, eax, 3);
  mulsd(xmm0, xmm1);
  movl(Address(rsp, 16), 3);
  jmp(L_2TAG_PACKET_8_0_2);
  bind(L_2TAG_PACKET_7_0_2);
  xorpd(xmm1, xmm1);
  xorpd(xmm0, xmm0);
  movl(eax, 49136);
  pinsrw(xmm0, eax, 3);
  divsd(xmm0, xmm1);
  movl(Address(rsp, 16), 2);

  bind(L_2TAG_PACKET_8_0_2);
  movq(Address(rsp, 8), xmm0);

  bind(B1_3);
  movq(xmm0, Address(rsp, 8));

  bind(B1_5);
  addq(rsp, 24);
}

#endif

#ifndef _LP64

ALIGNED_(16) juint _static_const_table_log[] =
{
  0xfefa3800UL, 0x3fe62e42UL, 0x93c76730UL, 0x3d2ef357UL, 0xaa241800UL,
  0x3fe5ee82UL, 0x0cda46beUL, 0x3d220238UL, 0x5c364800UL, 0x3fe5af40UL,
  0xac10c9fbUL, 0x3d2dfa63UL, 0x26bb8c00UL, 0x3fe5707aUL, 0xff3303ddUL,
  0x3d09980bUL, 0x26867800UL, 0x3fe5322eUL, 0x5d257531UL, 0x3d05ccc4UL,
  0x835a5000UL, 0x3fe4f45aUL, 0x6d93b8fbUL, 0xbd2e6c51UL, 0x6f970c00UL,
  0x3fe4b6fdUL, 0xed4c541cUL, 0x3cef7115UL, 0x27e8a400UL, 0x3fe47a15UL,
  0xf94d60aaUL, 0xbd22cb6aUL, 0xf2f92400UL, 0x3fe43d9fUL, 0x481051f7UL,
  0xbcfd984fUL, 0x2125cc00UL, 0x3fe4019cUL, 0x30f0c74cUL, 0xbd26ce79UL,
  0x0c36c000UL, 0x3fe3c608UL, 0x7cfe13c2UL, 0xbd02b736UL, 0x17197800UL,
  0x3fe38ae2UL, 0xbb5569a4UL, 0xbd218b7aUL, 0xad9d8c00UL, 0x3fe35028UL,
  0x9527e6acUL, 0x3d10b83fUL, 0x44340800UL, 0x3fe315daUL, 0xc5a0ed9cUL,
  0xbd274e93UL, 0x57b0e000UL, 0x3fe2dbf5UL, 0x07b9dc11UL, 0xbd17a6e5UL,
  0x6d0ec000UL, 0x3fe2a278UL, 0xe797882dUL, 0x3d206d2bUL, 0x1134dc00UL,
  0x3fe26962UL, 0x05226250UL, 0xbd0b61f1UL, 0xd8bebc00UL, 0x3fe230b0UL,
  0x6e48667bUL, 0x3d12fc06UL, 0x5fc61800UL, 0x3fe1f863UL, 0xc9fe81d3UL,
  0xbd2a7242UL, 0x49ae6000UL, 0x3fe1c078UL, 0xed70e667UL, 0x3cccacdeUL,
  0x40f23c00UL, 0x3fe188eeUL, 0xf8ab4650UL, 0x3d14cc4eUL, 0xf6f29800UL,
  0x3fe151c3UL, 0xa293ae49UL, 0xbd2edd97UL, 0x23c75c00UL, 0x3fe11af8UL,
  0xbb9ddcb2UL, 0xbd258647UL, 0x8611cc00UL, 0x3fe0e489UL, 0x07801742UL,
  0x3d1c2998UL, 0xe2d05400UL, 0x3fe0ae76UL, 0x887e7e27UL, 0x3d1f486bUL,
  0x0533c400UL, 0x3fe078bfUL, 0x41edf5fdUL, 0x3d268122UL, 0xbe760400UL,
  0x3fe04360UL, 0xe79539e0UL, 0xbd04c45fUL, 0xe5b20800UL, 0x3fe00e5aUL,
  0xb1727b1cUL, 0xbd053ba3UL, 0xaf7a4800UL, 0x3fdfb358UL, 0x3c164935UL,
  0x3d0085faUL, 0xee031800UL, 0x3fdf4aa7UL, 0x6f014a8bUL, 0x3d12cde5UL,
  0x56b41000UL, 0x3fdee2a1UL, 0x5a470251UL, 0x3d2f27f4UL, 0xc3ddb000UL,
  0x3fde7b42UL, 0x5372bd08UL, 0xbd246550UL, 0x1a272800UL, 0x3fde148aUL,
  0x07322938UL, 0xbd1326b2UL, 0x484c9800UL, 0x3fddae75UL, 0x60dc616aUL,
  0xbd1ea42dUL, 0x46def800UL, 0x3fdd4902UL, 0xe9a767a8UL, 0x3d235bafUL,
  0x18064800UL, 0x3fdce42fUL, 0x3ec7a6b0UL, 0xbd0797c3UL, 0xc7455800UL,
  0x3fdc7ff9UL, 0xc15249aeUL, 0xbd29b6ddUL, 0x693fa000UL, 0x3fdc1c60UL,
  0x7fe8e180UL, 0x3d2cec80UL, 0x1b80e000UL, 0x3fdbb961UL, 0xf40a666dUL,
  0x3d27d85bUL, 0x04462800UL, 0x3fdb56faUL, 0x2d841995UL, 0x3d109525UL,
  0x5248d000UL, 0x3fdaf529UL, 0x52774458UL, 0xbd217cc5UL, 0x3c8ad800UL,
  0x3fda93edUL, 0xbea77a5dUL, 0x3d1e36f2UL, 0x0224f800UL, 0x3fda3344UL,
  0x7f9d79f5UL, 0x3d23c645UL, 0xea15f000UL, 0x3fd9d32bUL, 0x10d0c0b0UL,
  0xbd26279eUL, 0x43135800UL, 0x3fd973a3UL, 0xa502d9f0UL, 0xbd152313UL,
  0x635bf800UL, 0x3fd914a8UL, 0x2ee6307dUL, 0xbd1766b5UL, 0xa88b3000UL,
  0x3fd8b639UL, 0xe5e70470UL, 0xbd205ae1UL, 0x776dc800UL, 0x3fd85855UL,
  0x3333778aUL, 0x3d2fd56fUL, 0x3bd81800UL, 0x3fd7fafaUL, 0xc812566aUL,
  0xbd272090UL, 0x687cf800UL, 0x3fd79e26UL, 0x2efd1778UL, 0x3d29ec7dUL,
  0x76c67800UL, 0x3fd741d8UL, 0x49dc60b3UL, 0x3d2d8b09UL, 0xe6af1800UL,
  0x3fd6e60eUL, 0x7c222d87UL, 0x3d172165UL, 0x3e9c6800UL, 0x3fd68ac8UL,
  0x2756eba0UL, 0x3d20a0d3UL, 0x0b3ab000UL, 0x3fd63003UL, 0xe731ae00UL,
  0xbd2db623UL, 0xdf596000UL, 0x3fd5d5bdUL, 0x08a465dcUL, 0xbd0a0b2aUL,
  0x53c8d000UL, 0x3fd57bf7UL, 0xee5d40efUL, 0x3d1fadedUL, 0x0738a000UL,
  0x3fd522aeUL, 0x8164c759UL, 0x3d2ebe70UL, 0x9e173000UL, 0x3fd4c9e0UL,
  0x1b0ad8a4UL, 0xbd2e2089UL, 0xc271c800UL, 0x3fd4718dUL, 0x0967d675UL,
  0xbd2f27ceUL, 0x23d5e800UL, 0x3fd419b4UL, 0xec90e09dUL, 0x3d08e436UL,
  0x77333000UL, 0x3fd3c252UL, 0xb606bd5cUL, 0x3d183b54UL, 0x76be1000UL,
  0x3fd36b67UL, 0xb0f177c8UL, 0x3d116ecdUL, 0xe1d36000UL, 0x3fd314f1UL,
  0xd3213cb8UL, 0xbd28e27aUL, 0x7cdc9000UL, 0x3fd2bef0UL, 0x4a5004f4UL,
  0x3d2a9cfaUL, 0x1134d800UL, 0x3fd26962UL, 0xdf5bb3b6UL, 0x3d2c93c1UL,
  0x6d0eb800UL, 0x3fd21445UL, 0xba46baeaUL, 0x3d0a87deUL, 0x635a6800UL,
  0x3fd1bf99UL, 0x5147bdb7UL, 0x3d2ca6edUL, 0xcbacf800UL, 0x3fd16b5cUL,
  0xf7a51681UL, 0x3d2b9acdUL, 0x8227e800UL, 0x3fd1178eUL, 0x63a5f01cUL,
  0xbd2c210eUL, 0x67616000UL, 0x3fd0c42dUL, 0x163ceae9UL, 0x3d27188bUL,
  0x604d5800UL, 0x3fd07138UL, 0x16ed4e91UL, 0x3cf89cdbUL, 0x5626c800UL,
  0x3fd01eaeUL, 0x1485e94aUL, 0xbd16f08cUL, 0x6cb3b000UL, 0x3fcf991cUL,
  0xca0cdf30UL, 0x3d1bcbecUL, 0xe4dd0000UL, 0x3fcef5adUL, 0x65bb8e11UL,
  0xbcca2115UL, 0xffe71000UL, 0x3fce530eUL, 0x6041f430UL, 0x3cc21227UL,
  0xb0d49000UL, 0x3fcdb13dUL, 0xf715b035UL, 0xbd2aff2aUL, 0xf2656000UL,
  0x3fcd1037UL, 0x75b6f6e4UL, 0xbd084a7eUL, 0xc6f01000UL, 0x3fcc6ffbUL,
  0xc5962bd2UL, 0xbcf1ec72UL, 0x383be000UL, 0x3fcbd087UL, 0x595412b6UL,
  0xbd2d4bc4UL, 0x575bd000UL, 0x3fcb31d8UL, 0x4eace1aaUL, 0xbd0c358dUL,
  0x3c8ae000UL, 0x3fca93edUL, 0x50562169UL, 0xbd287243UL, 0x07089000UL,
  0x3fc9f6c4UL, 0x6865817aUL, 0x3d29904dUL, 0xdcf70000UL, 0x3fc95a5aUL,
  0x58a0ff6fUL, 0x3d07f228UL, 0xeb390000UL, 0x3fc8beafUL, 0xaae92cd1UL,
  0xbd073d54UL, 0x6551a000UL, 0x3fc823c1UL, 0x9a631e83UL, 0x3d1e0ddbUL,
  0x85445000UL, 0x3fc7898dUL, 0x70914305UL, 0xbd1c6610UL, 0x8b757000UL,
  0x3fc6f012UL, 0xe59c21e1UL, 0xbd25118dUL, 0xbe8c1000UL, 0x3fc6574eUL,
  0x2c3c2e78UL, 0x3d19cf8bUL, 0x6b544000UL, 0x3fc5bf40UL, 0xeb68981cUL,
  0xbd127023UL, 0xe4a1b000UL, 0x3fc527e5UL, 0xe5697dc7UL, 0x3d2633e8UL,
  0x8333b000UL, 0x3fc4913dUL, 0x54fdb678UL, 0x3d258379UL, 0xa5993000UL,
  0x3fc3fb45UL, 0x7e6a354dUL, 0xbd2cd1d8UL, 0xb0159000UL, 0x3fc365fcUL,
  0x234b7289UL, 0x3cc62fa8UL, 0x0c868000UL, 0x3fc2d161UL, 0xcb81b4a1UL,
  0x3d039d6cUL, 0x2a49c000UL, 0x3fc23d71UL, 0x8fd3df5cUL, 0x3d100d23UL,
  0x7e23f000UL, 0x3fc1aa2bUL, 0x44389934UL, 0x3d2ca78eUL, 0x8227e000UL,
  0x3fc1178eUL, 0xce2d07f2UL, 0x3d21ef78UL, 0xb59e4000UL, 0x3fc08598UL,
  0x7009902cUL, 0xbd27e5ddUL, 0x39dbe000UL, 0x3fbfe891UL, 0x4fa10afdUL,
  0xbd2534d6UL, 0x830a2000UL, 0x3fbec739UL, 0xafe645e0UL, 0xbd2dc068UL,
  0x63844000UL, 0x3fbda727UL, 0x1fa71733UL, 0x3d1a8940UL, 0x01bc4000UL,
  0x3fbc8858UL, 0xc65aacd3UL, 0x3d2646d1UL, 0x8dad6000UL, 0x3fbb6ac8UL,
  0x2bf768e5UL, 0xbd139080UL, 0x40b1c000UL, 0x3fba4e76UL, 0xb94407c8UL,
  0xbd0e42b6UL, 0x5d594000UL, 0x3fb9335eUL, 0x3abd47daUL, 0x3d23115cUL,
  0x2f40e000UL, 0x3fb8197eUL, 0xf96ffdf7UL, 0x3d0f80dcUL, 0x0aeac000UL,
  0x3fb700d3UL, 0xa99ded32UL, 0x3cec1e8dUL, 0x4d97a000UL, 0x3fb5e95aUL,
  0x3c5d1d1eUL, 0xbd2c6906UL, 0x5d208000UL, 0x3fb4d311UL, 0x82f4e1efUL,
  0xbcf53a25UL, 0xa7d1e000UL, 0x3fb3bdf5UL, 0xa5db4ed7UL, 0x3d2cc85eUL,
  0xa4472000UL, 0x3fb2aa04UL, 0xae9c697dUL, 0xbd20b6e8UL, 0xd1466000UL,
  0x3fb1973bUL, 0x560d9e9bUL, 0xbd25325dUL, 0xb59e4000UL, 0x3fb08598UL,
  0x7009902cUL, 0xbd17e5ddUL, 0xc006c000UL, 0x3faeea31UL, 0x4fc93b7bUL,
  0xbd0e113eUL, 0xcdddc000UL, 0x3faccb73UL, 0x47d82807UL, 0xbd1a68f2UL,
  0xd0fb0000UL, 0x3faaaef2UL, 0x353bb42eUL, 0x3d20fc1aUL, 0x149fc000UL,
  0x3fa894aaUL, 0xd05a267dUL, 0xbd197995UL, 0xf2d4c000UL, 0x3fa67c94UL,
  0xec19afa2UL, 0xbd029efbUL, 0xd42e0000UL, 0x3fa466aeUL, 0x75bdfd28UL,
  0xbd2c1673UL, 0x2f8d0000UL, 0x3fa252f3UL, 0xe021b67bUL, 0x3d283e9aUL,
  0x89e74000UL, 0x3fa0415dUL, 0x5cf1d753UL, 0x3d0111c0UL, 0xec148000UL,
  0x3f9c63d2UL, 0x3f9eb2f3UL, 0x3d2578c6UL, 0x28c90000UL, 0x3f984925UL,
  0x325a0c34UL, 0xbd2aa0baUL, 0x25980000UL, 0x3f9432a9UL, 0x928637feUL,
  0x3d098139UL, 0x58938000UL, 0x3f902056UL, 0x06e2f7d2UL, 0xbd23dc5bUL,
  0xa3890000UL, 0x3f882448UL, 0xda74f640UL, 0xbd275577UL, 0x75890000UL,
  0x3f801015UL, 0x999d2be8UL, 0xbd10c76bUL, 0x59580000UL, 0x3f700805UL,
  0xcb31c67bUL, 0x3d2166afUL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
  0x80000000UL, 0xfefa3800UL, 0x3fa62e42UL, 0x93c76730UL, 0x3ceef357UL,
  0x92492492UL, 0x3fc24924UL, 0x00000000UL, 0xbfd00000UL, 0x3d6fb175UL,
  0xbfc5555eUL, 0x55555555UL, 0x3fd55555UL, 0x9999999aUL, 0x3fc99999UL,
  0x00000000UL, 0xbfe00000UL, 0x00000000UL, 0xffffe000UL, 0x00000000UL,
  0xffffe000UL
};
//registers,
// input: xmm0
// scratch: xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
//          rax, rdx, rcx, rbx (tmp)

void MacroAssembler::fast_log(XMMRegister xmm0, XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3, XMMRegister xmm4, XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7, Register eax, Register ecx, Register edx, Register tmp) {
  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label L_2TAG_PACKET_4_0_2, L_2TAG_PACKET_5_0_2, L_2TAG_PACKET_6_0_2, L_2TAG_PACKET_7_0_2;
  Label L_2TAG_PACKET_8_0_2, L_2TAG_PACKET_9_0_2;
  Label L_2TAG_PACKET_10_0_2, start;

  assert_different_registers(tmp, eax, ecx, edx);
  jmp(start);
  address static_const_table = (address)_static_const_table_log;

  bind(start);
  subl(rsp, 104);
  movl(Address(rsp, 40), tmp);
  lea(tmp, ExternalAddress(static_const_table));
  xorpd(xmm2, xmm2);
  movl(eax, 16368);
  pinsrw(xmm2, eax, 3);
  xorpd(xmm3, xmm3);
  movl(edx, 30704);
  pinsrw(xmm3, edx, 3);
  movsd(xmm0, Address(rsp, 112));
  movapd(xmm1, xmm0);
  movl(ecx, 32768);
  movdl(xmm4, ecx);
  movsd(xmm5, Address(tmp, 2128));         // 0x00000000UL, 0xffffe000UL
  pextrw(eax, xmm0, 3);
  por(xmm0, xmm2);
  psllq(xmm0, 5);
  movl(ecx, 16352);
  psrlq(xmm0, 34);
  rcpss(xmm0, xmm0);
  psllq(xmm1, 12);
  pshufd(xmm6, xmm5, 228);
  psrlq(xmm1, 12);
  subl(eax, 16);
  cmpl(eax, 32736);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_0_0_2);

  bind(L_2TAG_PACKET_1_0_2);
  paddd(xmm0, xmm4);
  por(xmm1, xmm3);
  movdl(edx, xmm0);
  psllq(xmm0, 29);
  pand(xmm5, xmm1);
  pand(xmm0, xmm6);
  subsd(xmm1, xmm5);
  mulpd(xmm5, xmm0);
  andl(eax, 32752);
  subl(eax, ecx);
  cvtsi2sdl(xmm7, eax);
  mulsd(xmm1, xmm0);
  movsd(xmm6, Address(tmp, 2064));         // 0xfefa3800UL, 0x3fa62e42UL
  movdqu(xmm3, Address(tmp, 2080));        // 0x92492492UL, 0x3fc24924UL, 0x00000000UL, 0xbfd00000UL
  subsd(xmm5, xmm2);
  andl(edx, 16711680);
  shrl(edx, 12);
  movdqu(xmm0, Address(tmp, edx));
  movdqu(xmm4, Address(tmp, 2096));        // 0x3d6fb175UL, 0xbfc5555eUL, 0x55555555UL, 0x3fd55555UL
  addsd(xmm1, xmm5);
  movdqu(xmm2, Address(tmp, 2112));        // 0x9999999aUL, 0x3fc99999UL, 0x00000000UL, 0xbfe00000UL
  mulsd(xmm6, xmm7);
  pshufd(xmm5, xmm1, 68);
  mulsd(xmm7, Address(tmp, 2072));         // 0x93c76730UL, 0x3ceef357UL, 0x92492492UL, 0x3fc24924UL
  mulsd(xmm3, xmm1);
  addsd(xmm0, xmm6);
  mulpd(xmm4, xmm5);
  mulpd(xmm5, xmm5);
  pshufd(xmm6, xmm0, 228);
  addsd(xmm0, xmm1);
  addpd(xmm4, xmm2);
  mulpd(xmm3, xmm5);
  subsd(xmm6, xmm0);
  mulsd(xmm4, xmm1);
  pshufd(xmm2, xmm0, 238);
  addsd(xmm1, xmm6);
  mulsd(xmm5, xmm5);
  addsd(xmm7, xmm2);
  addpd(xmm4, xmm3);
  addsd(xmm1, xmm7);
  mulpd(xmm4, xmm5);
  addsd(xmm1, xmm4);
  pshufd(xmm5, xmm4, 238);
  addsd(xmm1, xmm5);
  addsd(xmm0, xmm1);
  jmp(L_2TAG_PACKET_2_0_2);

  bind(L_2TAG_PACKET_0_0_2);
  movsd(xmm0, Address(rsp, 112));
  movdqu(xmm1, xmm0);
  addl(eax, 16);
  cmpl(eax, 32768);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_3_0_2);
  cmpl(eax, 16);
  jcc(Assembler::below, L_2TAG_PACKET_4_0_2);

  bind(L_2TAG_PACKET_5_0_2);
  addsd(xmm0, xmm0);
  jmp(L_2TAG_PACKET_2_0_2);

  bind(L_2TAG_PACKET_6_0_2);
  jcc(Assembler::above, L_2TAG_PACKET_5_0_2);
  cmpl(edx, 0);
  jcc(Assembler::above, L_2TAG_PACKET_5_0_2);
  jmp(L_2TAG_PACKET_7_0_2);

  bind(L_2TAG_PACKET_3_0_2);
  movdl(edx, xmm1);
  psrlq(xmm1, 32);
  movdl(ecx, xmm1);
  addl(ecx, ecx);
  cmpl(ecx, -2097152);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_6_0_2);
  orl(edx, ecx);
  cmpl(edx, 0);
  jcc(Assembler::equal, L_2TAG_PACKET_8_0_2);

  bind(L_2TAG_PACKET_7_0_2);
  xorpd(xmm1, xmm1);
  xorpd(xmm0, xmm0);
  movl(eax, 32752);
  pinsrw(xmm1, eax, 3);
  movl(edx, 3);
  mulsd(xmm0, xmm1);

  bind(L_2TAG_PACKET_9_0_2);
  movsd(Address(rsp, 0), xmm0);
  movsd(xmm0, Address(rsp, 112));
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_10_0_2);

  bind(L_2TAG_PACKET_8_0_2);
  xorpd(xmm1, xmm1);
  xorpd(xmm0, xmm0);
  movl(eax, 49136);
  pinsrw(xmm0, eax, 3);
  divsd(xmm0, xmm1);
  movl(edx, 2);
  jmp(L_2TAG_PACKET_9_0_2);

  bind(L_2TAG_PACKET_4_0_2);
  movdl(edx, xmm1);
  psrlq(xmm1, 32);
  movdl(ecx, xmm1);
  orl(edx, ecx);
  cmpl(edx, 0);
  jcc(Assembler::equal, L_2TAG_PACKET_8_0_2);
  xorpd(xmm1, xmm1);
  movl(eax, 18416);
  pinsrw(xmm1, eax, 3);
  mulsd(xmm0, xmm1);
  movapd(xmm1, xmm0);
  pextrw(eax, xmm0, 3);
  por(xmm0, xmm2);
  psllq(xmm0, 5);
  movl(ecx, 18416);
  psrlq(xmm0, 34);
  rcpss(xmm0, xmm0);
  psllq(xmm1, 12);
  pshufd(xmm6, xmm5, 228);
  psrlq(xmm1, 12);
  jmp(L_2TAG_PACKET_1_0_2);

  bind(L_2TAG_PACKET_2_0_2);
  movsd(Address(rsp, 24), xmm0);
  fld_d(Address(rsp, 24));

  bind(L_2TAG_PACKET_10_0_2);
  movl(tmp, Address(rsp, 40));
}

#endif
