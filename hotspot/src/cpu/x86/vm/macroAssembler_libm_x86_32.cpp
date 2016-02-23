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
#include "runtime/stubRoutines.hpp"
#include "macroAssembler_x86.hpp"

#ifdef _MSC_VER
#define ALIGNED_(x) __declspec(align(x))
#else
#define ALIGNED_(x) __attribute__ ((aligned(x)))
#endif

// The 32 bit code is at most SSE2 compliant

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

/******************************************************************************/
//                     ALGORITHM DESCRIPTION  - POW()
//                     ---------------------
//
//    Let x=2^k * mx, mx in [1,2)
//
//    log2(x) calculation:
//
//    Get B~1/mx based on the output of rcpps instruction (B0)
//    B = int((B0*LH*2^9+0.5))/2^9
//    LH is a short approximation for log2(e)
//
//    Reduced argument, scaled by LH:
//                r=B*mx-LH (computed accurately in high and low parts)
//
//    log2(x) result:  k - log2(B) + p(r)
//             p(r) is a degree 8 polynomial
//             -log2(B) read from data table (high, low parts)
//             log2(x) is formed from high and low parts
//    For |x| in [1-1/32, 1+1/16), a slower but more accurate computation
//    based om the same table design is performed.
//
//   Main path is taken if | floor(log2(|log2(|x|)|) + floor(log2|y|) | < 8,
//   to filter out all potential OF/UF cases.
//   exp2(y*log2(x)) is computed using an 8-bit index table and a degree 5
//   polynomial
//
// Special cases:
//  pow(-0,y) = -INF and raises the divide-by-zero exception for y an odd
//  integer < 0.
//  pow(-0,y) = +INF and raises the divide-by-zero exception for y < 0 and
//  not an odd integer.
//  pow(-0,y) = -0 for y an odd integer > 0.
//  pow(-0,y) = +0 for y > 0 and not an odd integer.
//  pow(-1,-INF) = NaN.
//  pow(+1,y) = NaN for any y, even a NaN.
//  pow(x,-0) = 1 for any x, even a NaN.
//  pow(x,y) = a NaN and raises the invalid exception for finite x < 0 and
//  finite non-integer y.
//  pow(x,-INF) = +INF for |x|<1.
//  pow(x,-INF) = +0 for |x|>1.
//  pow(x,+INF) = +0 for |x|<1.
//  pow(x,+INF) = +INF for |x|>1.
//  pow(-INF,y) = -0 for y an odd integer < 0.
//  pow(-INF,y) = +0 for y < 0 and not an odd integer.
//  pow(-INF,y) = -INF for y an odd integer > 0.
//  pow(-INF,y) = +INF for y > 0 and not an odd integer.
//  pow(+INF,y) = +0 for y <0.
//  pow(+INF,y) = +INF for y >0.
//
/******************************************************************************/

ALIGNED_(16) juint _static_const_table_pow[] =
{
  0x00000000UL, 0xbfd61a00UL, 0x00000000UL, 0xbf5dabe1UL, 0xf8000000UL,
  0xffffffffUL, 0x00000000UL, 0xfffff800UL, 0x00000000UL, 0x3ff00000UL,
  0x00000000UL, 0x00000000UL, 0x20000000UL, 0x3feff00aUL, 0x96621f95UL,
  0x3e5b1856UL, 0xe0000000UL, 0x3fefe019UL, 0xe5916f9eUL, 0xbe325278UL,
  0x00000000UL, 0x3fefd02fUL, 0x859a1062UL, 0x3e595fb7UL, 0xc0000000UL,
  0x3fefc049UL, 0xb245f18fUL, 0xbe529c38UL, 0xe0000000UL, 0x3fefb069UL,
  0xad2880a7UL, 0xbe501230UL, 0x60000000UL, 0x3fefa08fUL, 0xc8e72420UL,
  0x3e597bd1UL, 0x80000000UL, 0x3fef90baUL, 0xc30c4500UL, 0xbe5d6c75UL,
  0xe0000000UL, 0x3fef80eaUL, 0x02c63f43UL, 0x3e2e1318UL, 0xc0000000UL,
  0x3fef7120UL, 0xb3d4ccccUL, 0xbe44c52aUL, 0x00000000UL, 0x3fef615cUL,
  0xdbd91397UL, 0xbe4e7d6cUL, 0xa0000000UL, 0x3fef519cUL, 0x65c5cd68UL,
  0xbe522dc8UL, 0xa0000000UL, 0x3fef41e2UL, 0x46d1306cUL, 0xbe5a840eUL,
  0xe0000000UL, 0x3fef322dUL, 0xd2980e94UL, 0x3e5071afUL, 0xa0000000UL,
  0x3fef227eUL, 0x773abadeUL, 0xbe5891e5UL, 0xa0000000UL, 0x3fef12d4UL,
  0xdc6bf46bUL, 0xbe5cccbeUL, 0xe0000000UL, 0x3fef032fUL, 0xbc7247faUL,
  0xbe2bab83UL, 0x80000000UL, 0x3feef390UL, 0xbcaa1e46UL, 0xbe53bb3bUL,
  0x60000000UL, 0x3feee3f6UL, 0x5f6c682dUL, 0xbe54c619UL, 0x80000000UL,
  0x3feed461UL, 0x5141e368UL, 0xbe4b6d86UL, 0xe0000000UL, 0x3feec4d1UL,
  0xec678f76UL, 0xbe369af6UL, 0x80000000UL, 0x3feeb547UL, 0x41301f55UL,
  0xbe2d4312UL, 0x60000000UL, 0x3feea5c2UL, 0x676da6bdUL, 0xbe4d8dd0UL,
  0x60000000UL, 0x3fee9642UL, 0x57a891c4UL, 0x3e51f991UL, 0xa0000000UL,
  0x3fee86c7UL, 0xe4eb491eUL, 0x3e579bf9UL, 0x20000000UL, 0x3fee7752UL,
  0xfddc4a2cUL, 0xbe3356e6UL, 0xc0000000UL, 0x3fee67e1UL, 0xd75b5bf1UL,
  0xbe449531UL, 0x80000000UL, 0x3fee5876UL, 0xbd423b8eUL, 0x3df54fe4UL,
  0x60000000UL, 0x3fee4910UL, 0x330e51b9UL, 0x3e54289cUL, 0x80000000UL,
  0x3fee39afUL, 0x8651a95fUL, 0xbe55aad6UL, 0xa0000000UL, 0x3fee2a53UL,
  0x5e98c708UL, 0xbe2fc4a9UL, 0xe0000000UL, 0x3fee1afcUL, 0x0989328dUL,
  0x3e23958cUL, 0x40000000UL, 0x3fee0babUL, 0xee642abdUL, 0xbe425dd8UL,
  0xa0000000UL, 0x3fedfc5eUL, 0xc394d236UL, 0x3e526362UL, 0x20000000UL,
  0x3feded17UL, 0xe104aa8eUL, 0x3e4ce247UL, 0xc0000000UL, 0x3fedddd4UL,
  0x265a9be4UL, 0xbe5bb77aUL, 0x40000000UL, 0x3fedce97UL, 0x0ecac52fUL,
  0x3e4a7cb1UL, 0xe0000000UL, 0x3fedbf5eUL, 0x124cb3b8UL, 0x3e257024UL,
  0x80000000UL, 0x3fedb02bUL, 0xe6d4febeUL, 0xbe2033eeUL, 0x20000000UL,
  0x3feda0fdUL, 0x39cca00eUL, 0xbe3ddabcUL, 0xc0000000UL, 0x3fed91d3UL,
  0xef8a552aUL, 0xbe543390UL, 0x40000000UL, 0x3fed82afUL, 0xb8e85204UL,
  0x3e513850UL, 0xe0000000UL, 0x3fed738fUL, 0x3d59fe08UL, 0xbe5db728UL,
  0x40000000UL, 0x3fed6475UL, 0x3aa7ead1UL, 0x3e58804bUL, 0xc0000000UL,
  0x3fed555fUL, 0xf8a35ba9UL, 0xbe5298b0UL, 0x00000000UL, 0x3fed464fUL,
  0x9a88dd15UL, 0x3e5a8cdbUL, 0x40000000UL, 0x3fed3743UL, 0xb0b0a190UL,
  0x3e598635UL, 0x80000000UL, 0x3fed283cUL, 0xe2113295UL, 0xbe5c1119UL,
  0x80000000UL, 0x3fed193aUL, 0xafbf1728UL, 0xbe492e9cUL, 0x60000000UL,
  0x3fed0a3dUL, 0xe4a4ccf3UL, 0x3e19b90eUL, 0x20000000UL, 0x3fecfb45UL,
  0xba3cbeb8UL, 0x3e406b50UL, 0xc0000000UL, 0x3fecec51UL, 0x110f7dddUL,
  0x3e0d6806UL, 0x40000000UL, 0x3fecdd63UL, 0x7dd7d508UL, 0xbe5a8943UL,
  0x80000000UL, 0x3fecce79UL, 0x9b60f271UL, 0xbe50676aUL, 0x80000000UL,
  0x3fecbf94UL, 0x0b9ad660UL, 0x3e59174fUL, 0x60000000UL, 0x3fecb0b4UL,
  0x00823d9cUL, 0x3e5bbf72UL, 0x20000000UL, 0x3feca1d9UL, 0x38a6ec89UL,
  0xbe4d38f9UL, 0x80000000UL, 0x3fec9302UL, 0x3a0b7d8eUL, 0x3e53dbfdUL,
  0xc0000000UL, 0x3fec8430UL, 0xc6826b34UL, 0xbe27c5c9UL, 0xc0000000UL,
  0x3fec7563UL, 0x0c706381UL, 0xbe593653UL, 0x60000000UL, 0x3fec669bUL,
  0x7df34ec7UL, 0x3e461ab5UL, 0xe0000000UL, 0x3fec57d7UL, 0x40e5e7e8UL,
  0xbe5c3daeUL, 0x00000000UL, 0x3fec4919UL, 0x5602770fUL, 0xbe55219dUL,
  0xc0000000UL, 0x3fec3a5eUL, 0xec7911ebUL, 0x3e5a5d25UL, 0x60000000UL,
  0x3fec2ba9UL, 0xb39ea225UL, 0xbe53c00bUL, 0x80000000UL, 0x3fec1cf8UL,
  0x967a212eUL, 0x3e5a8ddfUL, 0x60000000UL, 0x3fec0e4cUL, 0x580798bdUL,
  0x3e5f53abUL, 0x00000000UL, 0x3febffa5UL, 0xb8282df6UL, 0xbe46b874UL,
  0x20000000UL, 0x3febf102UL, 0xe33a6729UL, 0x3e54963fUL, 0x00000000UL,
  0x3febe264UL, 0x3b53e88aUL, 0xbe3adce1UL, 0x60000000UL, 0x3febd3caUL,
  0xc2585084UL, 0x3e5cde9fUL, 0x80000000UL, 0x3febc535UL, 0xa335c5eeUL,
  0xbe39fd9cUL, 0x20000000UL, 0x3febb6a5UL, 0x7325b04dUL, 0x3e42ba15UL,
  0x60000000UL, 0x3feba819UL, 0x1564540fUL, 0x3e3a9f35UL, 0x40000000UL,
  0x3feb9992UL, 0x83fff592UL, 0xbe5465ceUL, 0xa0000000UL, 0x3feb8b0fUL,
  0xb9da63d3UL, 0xbe4b1a0aUL, 0x80000000UL, 0x3feb7c91UL, 0x6d6f1ea4UL,
  0x3e557657UL, 0x00000000UL, 0x3feb6e18UL, 0x5e80a1bfUL, 0x3e4ddbb6UL,
  0x00000000UL, 0x3feb5fa3UL, 0x1c9eacb5UL, 0x3e592877UL, 0xa0000000UL,
  0x3feb5132UL, 0x6d40beb3UL, 0xbe51858cUL, 0xa0000000UL, 0x3feb42c6UL,
  0xd740c67bUL, 0x3e427ad2UL, 0x40000000UL, 0x3feb345fUL, 0xa3e0cceeUL,
  0xbe5c2fc4UL, 0x40000000UL, 0x3feb25fcUL, 0x8e752b50UL, 0xbe3da3c2UL,
  0xc0000000UL, 0x3feb179dUL, 0xa892e7deUL, 0x3e1fb481UL, 0xc0000000UL,
  0x3feb0943UL, 0x21ed71e9UL, 0xbe365206UL, 0x20000000UL, 0x3feafaeeUL,
  0x0e1380a3UL, 0x3e5c5b7bUL, 0x20000000UL, 0x3feaec9dUL, 0x3c3d640eUL,
  0xbe5dbbd0UL, 0x60000000UL, 0x3feade50UL, 0x8f97a715UL, 0x3e3a8ec5UL,
  0x20000000UL, 0x3fead008UL, 0x23ab2839UL, 0x3e2fe98aUL, 0x40000000UL,
  0x3feac1c4UL, 0xf4bbd50fUL, 0x3e54d8f6UL, 0xe0000000UL, 0x3feab384UL,
  0x14757c4dUL, 0xbe48774cUL, 0xc0000000UL, 0x3feaa549UL, 0x7c7b0eeaUL,
  0x3e5b51bbUL, 0x20000000UL, 0x3fea9713UL, 0xf56f7013UL, 0x3e386200UL,
  0xe0000000UL, 0x3fea88e0UL, 0xbe428ebeUL, 0xbe514af5UL, 0xe0000000UL,
  0x3fea7ab2UL, 0x8d0e4496UL, 0x3e4f9165UL, 0x60000000UL, 0x3fea6c89UL,
  0xdbacc5d5UL, 0xbe5c063bUL, 0x20000000UL, 0x3fea5e64UL, 0x3f19d970UL,
  0xbe5a0c8cUL, 0x20000000UL, 0x3fea5043UL, 0x09ea3e6bUL, 0x3e5065dcUL,
  0x80000000UL, 0x3fea4226UL, 0x78df246cUL, 0x3e5e05f6UL, 0x40000000UL,
  0x3fea340eUL, 0x4057d4a0UL, 0x3e431b2bUL, 0x40000000UL, 0x3fea25faUL,
  0x82867bb5UL, 0x3e4b76beUL, 0xa0000000UL, 0x3fea17eaUL, 0x9436f40aUL,
  0xbe5aad39UL, 0x20000000UL, 0x3fea09dfUL, 0x4b5253b3UL, 0x3e46380bUL,
  0x00000000UL, 0x3fe9fbd8UL, 0x8fc52466UL, 0xbe386f9bUL, 0x20000000UL,
  0x3fe9edd5UL, 0x22d3f344UL, 0xbe538347UL, 0x60000000UL, 0x3fe9dfd6UL,
  0x1ac33522UL, 0x3e5dbc53UL, 0x00000000UL, 0x3fe9d1dcUL, 0xeabdff1dUL,
  0x3e40fc0cUL, 0xe0000000UL, 0x3fe9c3e5UL, 0xafd30e73UL, 0xbe585e63UL,
  0xe0000000UL, 0x3fe9b5f3UL, 0xa52f226aUL, 0xbe43e8f9UL, 0x20000000UL,
  0x3fe9a806UL, 0xecb8698dUL, 0xbe515b36UL, 0x80000000UL, 0x3fe99a1cUL,
  0xf2b4e89dUL, 0x3e48b62bUL, 0x20000000UL, 0x3fe98c37UL, 0x7c9a88fbUL,
  0x3e44414cUL, 0x00000000UL, 0x3fe97e56UL, 0xda015741UL, 0xbe5d13baUL,
  0xe0000000UL, 0x3fe97078UL, 0x5fdace06UL, 0x3e51b947UL, 0x00000000UL,
  0x3fe962a0UL, 0x956ca094UL, 0x3e518785UL, 0x40000000UL, 0x3fe954cbUL,
  0x01164c1dUL, 0x3e5d5b57UL, 0xc0000000UL, 0x3fe946faUL, 0xe63b3767UL,
  0xbe4f84e7UL, 0x40000000UL, 0x3fe9392eUL, 0xe57cc2a9UL, 0x3e34eda3UL,
  0xe0000000UL, 0x3fe92b65UL, 0x8c75b544UL, 0x3e5766a0UL, 0xc0000000UL,
  0x3fe91da1UL, 0x37d1d087UL, 0xbe5e2ab1UL, 0x80000000UL, 0x3fe90fe1UL,
  0xa953dc20UL, 0x3e5fa1f3UL, 0x80000000UL, 0x3fe90225UL, 0xdbd3f369UL,
  0x3e47d6dbUL, 0xa0000000UL, 0x3fe8f46dUL, 0x1c9be989UL, 0xbe5e2b0aUL,
  0xa0000000UL, 0x3fe8e6b9UL, 0x3c93d76aUL, 0x3e5c8618UL, 0xe0000000UL,
  0x3fe8d909UL, 0x2182fc9aUL, 0xbe41aa9eUL, 0x20000000UL, 0x3fe8cb5eUL,
  0xe6b3539dUL, 0xbe530d19UL, 0x60000000UL, 0x3fe8bdb6UL, 0x49e58cc3UL,
  0xbe3bb374UL, 0xa0000000UL, 0x3fe8b012UL, 0xa7cfeb8fUL, 0x3e56c412UL,
  0x00000000UL, 0x3fe8a273UL, 0x8d52bc19UL, 0x3e1429b8UL, 0x60000000UL,
  0x3fe894d7UL, 0x4dc32c6cUL, 0xbe48604cUL, 0xc0000000UL, 0x3fe8873fUL,
  0x0c868e56UL, 0xbe564ee5UL, 0x00000000UL, 0x3fe879acUL, 0x56aee828UL,
  0x3e5e2fd8UL, 0x60000000UL, 0x3fe86c1cUL, 0x7ceab8ecUL, 0x3e493365UL,
  0xc0000000UL, 0x3fe85e90UL, 0x78d4dadcUL, 0xbe4f7f25UL, 0x00000000UL,
  0x3fe85109UL, 0x0ccd8280UL, 0x3e31e7a2UL, 0x40000000UL, 0x3fe84385UL,
  0x34ba4e15UL, 0x3e328077UL, 0x80000000UL, 0x3fe83605UL, 0xa670975aUL,
  0xbe53eee5UL, 0xa0000000UL, 0x3fe82889UL, 0xf61b77b2UL, 0xbe43a20aUL,
  0xa0000000UL, 0x3fe81b11UL, 0x13e6643bUL, 0x3e5e5fe5UL, 0xc0000000UL,
  0x3fe80d9dUL, 0x82cc94e8UL, 0xbe5ff1f9UL, 0xa0000000UL, 0x3fe8002dUL,
  0x8a0c9c5dUL, 0xbe42b0e7UL, 0x60000000UL, 0x3fe7f2c1UL, 0x22a16f01UL,
  0x3e5d9ea0UL, 0x20000000UL, 0x3fe7e559UL, 0xc38cd451UL, 0x3e506963UL,
  0xc0000000UL, 0x3fe7d7f4UL, 0x9902bc71UL, 0x3e4503d7UL, 0x40000000UL,
  0x3fe7ca94UL, 0xdef2a3c0UL, 0x3e3d98edUL, 0xa0000000UL, 0x3fe7bd37UL,
  0xed49abb0UL, 0x3e24c1ffUL, 0xe0000000UL, 0x3fe7afdeUL, 0xe3b0be70UL,
  0xbe40c467UL, 0x00000000UL, 0x3fe7a28aUL, 0xaf9f193cUL, 0xbe5dff6cUL,
  0xe0000000UL, 0x3fe79538UL, 0xb74cf6b6UL, 0xbe258ed0UL, 0xa0000000UL,
  0x3fe787ebUL, 0x1d9127c7UL, 0x3e345fb0UL, 0x40000000UL, 0x3fe77aa2UL,
  0x1028c21dUL, 0xbe4619bdUL, 0xa0000000UL, 0x3fe76d5cUL, 0x7cb0b5e4UL,
  0x3e40f1a2UL, 0xe0000000UL, 0x3fe7601aUL, 0x2b1bc4adUL, 0xbe32e8bbUL,
  0xe0000000UL, 0x3fe752dcUL, 0x6839f64eUL, 0x3e41f57bUL, 0xc0000000UL,
  0x3fe745a2UL, 0xc4121f7eUL, 0xbe52c40aUL, 0x60000000UL, 0x3fe7386cUL,
  0xd6852d72UL, 0xbe5c4e6bUL, 0xc0000000UL, 0x3fe72b39UL, 0x91d690f7UL,
  0xbe57f88fUL, 0xe0000000UL, 0x3fe71e0aUL, 0x627a2159UL, 0xbe4425d5UL,
  0xc0000000UL, 0x3fe710dfUL, 0x50a54033UL, 0x3e422b7eUL, 0x60000000UL,
  0x3fe703b8UL, 0x3b0b5f91UL, 0x3e5d3857UL, 0xe0000000UL, 0x3fe6f694UL,
  0x84d628a2UL, 0xbe51f090UL, 0x00000000UL, 0x3fe6e975UL, 0x306d8894UL,
  0xbe414d83UL, 0xe0000000UL, 0x3fe6dc58UL, 0x30bf24aaUL, 0xbe4650caUL,
  0x80000000UL, 0x3fe6cf40UL, 0xd4628d69UL, 0xbe5db007UL, 0xc0000000UL,
  0x3fe6c22bUL, 0xa2aae57bUL, 0xbe31d279UL, 0xc0000000UL, 0x3fe6b51aUL,
  0x860edf7eUL, 0xbe2d4c4aUL, 0x80000000UL, 0x3fe6a80dUL, 0xf3559341UL,
  0xbe5f7e98UL, 0xe0000000UL, 0x3fe69b03UL, 0xa885899eUL, 0xbe5c2011UL,
  0xe0000000UL, 0x3fe68dfdUL, 0x2bdc6d37UL, 0x3e224a82UL, 0xa0000000UL,
  0x3fe680fbUL, 0xc12ad1b9UL, 0xbe40cf56UL, 0x00000000UL, 0x3fe673fdUL,
  0x1bcdf659UL, 0xbdf52f2dUL, 0x00000000UL, 0x3fe66702UL, 0x5df10408UL,
  0x3e5663e0UL, 0xc0000000UL, 0x3fe65a0aUL, 0xa4070568UL, 0xbe40b12fUL,
  0x00000000UL, 0x3fe64d17UL, 0x71c54c47UL, 0x3e5f5e8bUL, 0x00000000UL,
  0x3fe64027UL, 0xbd4b7e83UL, 0x3e42ead6UL, 0xa0000000UL, 0x3fe6333aUL,
  0x61598bd2UL, 0xbe4c48d4UL, 0xc0000000UL, 0x3fe62651UL, 0x6f538d61UL,
  0x3e548401UL, 0xa0000000UL, 0x3fe6196cUL, 0x14344120UL, 0xbe529af6UL,
  0x00000000UL, 0x3fe60c8bUL, 0x5982c587UL, 0xbe3e1e4fUL, 0x00000000UL,
  0x3fe5ffadUL, 0xfe51d4eaUL, 0xbe4c897aUL, 0x80000000UL, 0x3fe5f2d2UL,
  0xfd46ebe1UL, 0x3e552e00UL, 0xa0000000UL, 0x3fe5e5fbUL, 0xa4695699UL,
  0x3e5ed471UL, 0x60000000UL, 0x3fe5d928UL, 0x80d118aeUL, 0x3e456b61UL,
  0xa0000000UL, 0x3fe5cc58UL, 0x304c330bUL, 0x3e54dc29UL, 0x80000000UL,
  0x3fe5bf8cUL, 0x0af2dedfUL, 0xbe3aa9bdUL, 0xe0000000UL, 0x3fe5b2c3UL,
  0x15fc9258UL, 0xbe479a37UL, 0xc0000000UL, 0x3fe5a5feUL, 0x9292c7eaUL,
  0x3e188650UL, 0x20000000UL, 0x3fe5993dUL, 0x33b4d380UL, 0x3e5d6d93UL,
  0x20000000UL, 0x3fe58c7fUL, 0x02fd16c7UL, 0x3e2fe961UL, 0xa0000000UL,
  0x3fe57fc4UL, 0x4a05edb6UL, 0xbe4d55b4UL, 0xa0000000UL, 0x3fe5730dUL,
  0x3d443abbUL, 0xbe5e6954UL, 0x00000000UL, 0x3fe5665aUL, 0x024acfeaUL,
  0x3e50e61bUL, 0x00000000UL, 0x3fe559aaUL, 0xcc9edd09UL, 0xbe325403UL,
  0x60000000UL, 0x3fe54cfdUL, 0x1fe26950UL, 0x3e5d500eUL, 0x60000000UL,
  0x3fe54054UL, 0x6c5ae164UL, 0xbe4a79b4UL, 0xc0000000UL, 0x3fe533aeUL,
  0x154b0287UL, 0xbe401571UL, 0xa0000000UL, 0x3fe5270cUL, 0x0673f401UL,
  0xbe56e56bUL, 0xe0000000UL, 0x3fe51a6dUL, 0x751b639cUL, 0x3e235269UL,
  0xa0000000UL, 0x3fe50dd2UL, 0x7c7b2bedUL, 0x3ddec887UL, 0xc0000000UL,
  0x3fe5013aUL, 0xafab4e17UL, 0x3e5e7575UL, 0x60000000UL, 0x3fe4f4a6UL,
  0x2e308668UL, 0x3e59aed6UL, 0x80000000UL, 0x3fe4e815UL, 0xf33e2a76UL,
  0xbe51f184UL, 0xe0000000UL, 0x3fe4db87UL, 0x839f3e3eUL, 0x3e57db01UL,
  0xc0000000UL, 0x3fe4cefdUL, 0xa9eda7bbUL, 0x3e535e0fUL, 0x00000000UL,
  0x3fe4c277UL, 0x2a8f66a5UL, 0x3e5ce451UL, 0xc0000000UL, 0x3fe4b5f3UL,
  0x05192456UL, 0xbe4e8518UL, 0xc0000000UL, 0x3fe4a973UL, 0x4aa7cd1dUL,
  0x3e46784aUL, 0x40000000UL, 0x3fe49cf7UL, 0x8e23025eUL, 0xbe5749f2UL,
  0x00000000UL, 0x3fe4907eUL, 0x18d30215UL, 0x3e360f39UL, 0x20000000UL,
  0x3fe48408UL, 0x63dcf2f3UL, 0x3e5e00feUL, 0xc0000000UL, 0x3fe47795UL,
  0x46182d09UL, 0xbe5173d9UL, 0xa0000000UL, 0x3fe46b26UL, 0x8f0e62aaUL,
  0xbe48f281UL, 0xe0000000UL, 0x3fe45ebaUL, 0x5775c40cUL, 0xbe56aad4UL,
  0x60000000UL, 0x3fe45252UL, 0x0fe25f69UL, 0x3e48bd71UL, 0x40000000UL,
  0x3fe445edUL, 0xe9989ec5UL, 0x3e590d97UL, 0x80000000UL, 0x3fe4398bUL,
  0xb3d9ffe3UL, 0x3e479dbcUL, 0x20000000UL, 0x3fe42d2dUL, 0x388e4d2eUL,
  0xbe5eed80UL, 0xe0000000UL, 0x3fe420d1UL, 0x6f797c18UL, 0x3e554b4cUL,
  0x20000000UL, 0x3fe4147aUL, 0x31048bb4UL, 0xbe5b1112UL, 0x80000000UL,
  0x3fe40825UL, 0x2efba4f9UL, 0x3e48ebc7UL, 0x40000000UL, 0x3fe3fbd4UL,
  0x50201119UL, 0x3e40b701UL, 0x40000000UL, 0x3fe3ef86UL, 0x0a4db32cUL,
  0x3e551de8UL, 0xa0000000UL, 0x3fe3e33bUL, 0x0c9c148bUL, 0xbe50c1f6UL,
  0x20000000UL, 0x3fe3d6f4UL, 0xc9129447UL, 0x3e533fa0UL, 0x00000000UL,
  0x3fe3cab0UL, 0xaae5b5a0UL, 0xbe22b68eUL, 0x20000000UL, 0x3fe3be6fUL,
  0x02305e8aUL, 0xbe54fc08UL, 0x60000000UL, 0x3fe3b231UL, 0x7f908258UL,
  0x3e57dc05UL, 0x00000000UL, 0x3fe3a5f7UL, 0x1a09af78UL, 0x3e08038bUL,
  0xe0000000UL, 0x3fe399bfUL, 0x490643c1UL, 0xbe5dbe42UL, 0xe0000000UL,
  0x3fe38d8bUL, 0x5e8ad724UL, 0xbe3c2b72UL, 0x20000000UL, 0x3fe3815bUL,
  0xc67196b6UL, 0x3e1713cfUL, 0xa0000000UL, 0x3fe3752dUL, 0x6182e429UL,
  0xbe3ec14cUL, 0x40000000UL, 0x3fe36903UL, 0xab6eb1aeUL, 0x3e5a2cc5UL,
  0x40000000UL, 0x3fe35cdcUL, 0xfe5dc064UL, 0xbe5c5878UL, 0x40000000UL,
  0x3fe350b8UL, 0x0ba6b9e4UL, 0x3e51619bUL, 0x80000000UL, 0x3fe34497UL,
  0x857761aaUL, 0x3e5fff53UL, 0x00000000UL, 0x3fe3387aUL, 0xf872d68cUL,
  0x3e484f4dUL, 0xa0000000UL, 0x3fe32c5fUL, 0x087e97c2UL, 0x3e52842eUL,
  0x80000000UL, 0x3fe32048UL, 0x73d6d0c0UL, 0xbe503edfUL, 0x80000000UL,
  0x3fe31434UL, 0x0c1456a1UL, 0xbe5f72adUL, 0xa0000000UL, 0x3fe30823UL,
  0x83a1a4d5UL, 0xbe5e65ccUL, 0xe0000000UL, 0x3fe2fc15UL, 0x855a7390UL,
  0xbe506438UL, 0x40000000UL, 0x3fe2f00bUL, 0xa2898287UL, 0x3e3d22a2UL,
  0xe0000000UL, 0x3fe2e403UL, 0x8b56f66fUL, 0xbe5aa5fdUL, 0x80000000UL,
  0x3fe2d7ffUL, 0x52db119aUL, 0x3e3a2e3dUL, 0x60000000UL, 0x3fe2cbfeUL,
  0xe2ddd4c0UL, 0xbe586469UL, 0x40000000UL, 0x3fe2c000UL, 0x6b01bf10UL,
  0x3e352b9dUL, 0x40000000UL, 0x3fe2b405UL, 0xb07a1cdfUL, 0x3e5c5cdaUL,
  0x80000000UL, 0x3fe2a80dUL, 0xc7b5f868UL, 0xbe5668b3UL, 0xc0000000UL,
  0x3fe29c18UL, 0x185edf62UL, 0xbe563d66UL, 0x00000000UL, 0x3fe29027UL,
  0xf729e1ccUL, 0x3e59a9a0UL, 0x80000000UL, 0x3fe28438UL, 0x6433c727UL,
  0xbe43cc89UL, 0x00000000UL, 0x3fe2784dUL, 0x41782631UL, 0xbe30750cUL,
  0xa0000000UL, 0x3fe26c64UL, 0x914911b7UL, 0xbe58290eUL, 0x40000000UL,
  0x3fe2607fUL, 0x3dcc73e1UL, 0xbe4269cdUL, 0x00000000UL, 0x3fe2549dUL,
  0x2751bf70UL, 0xbe5a6998UL, 0xc0000000UL, 0x3fe248bdUL, 0x4248b9fbUL,
  0xbe4ddb00UL, 0x80000000UL, 0x3fe23ce1UL, 0xf35cf82fUL, 0x3e561b71UL,
  0x60000000UL, 0x3fe23108UL, 0x8e481a2dUL, 0x3e518fb9UL, 0x60000000UL,
  0x3fe22532UL, 0x5ab96edcUL, 0xbe5fafc5UL, 0x40000000UL, 0x3fe2195fUL,
  0x80943911UL, 0xbe07f819UL, 0x40000000UL, 0x3fe20d8fUL, 0x386f2d6cUL,
  0xbe54ba8bUL, 0x40000000UL, 0x3fe201c2UL, 0xf29664acUL, 0xbe5eb815UL,
  0x20000000UL, 0x3fe1f5f8UL, 0x64f03390UL, 0x3e5e320cUL, 0x20000000UL,
  0x3fe1ea31UL, 0x747ff696UL, 0x3e5ef0a5UL, 0x40000000UL, 0x3fe1de6dUL,
  0x3e9ceb51UL, 0xbe5f8d27UL, 0x20000000UL, 0x3fe1d2acUL, 0x4ae0b55eUL,
  0x3e5faa21UL, 0x20000000UL, 0x3fe1c6eeUL, 0x28569a5eUL, 0x3e598a4fUL,
  0x20000000UL, 0x3fe1bb33UL, 0x54b33e07UL, 0x3e46130aUL, 0x20000000UL,
  0x3fe1af7bUL, 0x024f1078UL, 0xbe4dbf93UL, 0x00000000UL, 0x3fe1a3c6UL,
  0xb0783bfaUL, 0x3e419248UL, 0xe0000000UL, 0x3fe19813UL, 0x2f02b836UL,
  0x3e4e02b7UL, 0xc0000000UL, 0x3fe18c64UL, 0x28dec9d4UL, 0x3e09064fUL,
  0x80000000UL, 0x3fe180b8UL, 0x45cbf406UL, 0x3e5b1f46UL, 0x40000000UL,
  0x3fe1750fUL, 0x03d9964cUL, 0x3e5b0a79UL, 0x00000000UL, 0x3fe16969UL,
  0x8b5b882bUL, 0xbe238086UL, 0xa0000000UL, 0x3fe15dc5UL, 0x73bad6f8UL,
  0xbdf1fca4UL, 0x20000000UL, 0x3fe15225UL, 0x5385769cUL, 0x3e5e8d76UL,
  0xa0000000UL, 0x3fe14687UL, 0x1676dc6bUL, 0x3e571d08UL, 0x20000000UL,
  0x3fe13aedUL, 0xa8c41c7fUL, 0xbe598a25UL, 0x60000000UL, 0x3fe12f55UL,
  0xc4e1aaf0UL, 0x3e435277UL, 0xa0000000UL, 0x3fe123c0UL, 0x403638e1UL,
  0xbe21aa7cUL, 0xc0000000UL, 0x3fe1182eUL, 0x557a092bUL, 0xbdd0116bUL,
  0xc0000000UL, 0x3fe10c9fUL, 0x7d779f66UL, 0x3e4a61baUL, 0xc0000000UL,
  0x3fe10113UL, 0x2b09c645UL, 0xbe5d586eUL, 0x20000000UL, 0x3fe0ea04UL,
  0xea2cad46UL, 0x3e5aa97cUL, 0x20000000UL, 0x3fe0d300UL, 0x23190e54UL,
  0x3e50f1a7UL, 0xa0000000UL, 0x3fe0bc07UL, 0x1379a5a6UL, 0xbe51619dUL,
  0x60000000UL, 0x3fe0a51aUL, 0x926a3d4aUL, 0x3e5cf019UL, 0xa0000000UL,
  0x3fe08e38UL, 0xa8c24358UL, 0x3e35241eUL, 0x20000000UL, 0x3fe07762UL,
  0x24317e7aUL, 0x3e512cfaUL, 0x00000000UL, 0x3fe06097UL, 0xfd9cf274UL,
  0xbe55bef3UL, 0x00000000UL, 0x3fe049d7UL, 0x3689b49dUL, 0xbe36d26dUL,
  0x40000000UL, 0x3fe03322UL, 0xf72ef6c4UL, 0xbe54cd08UL, 0xa0000000UL,
  0x3fe01c78UL, 0x23702d2dUL, 0xbe5900bfUL, 0x00000000UL, 0x3fe005daUL,
  0x3f59c14cUL, 0x3e57d80bUL, 0x40000000UL, 0x3fdfde8dUL, 0xad67766dUL,
  0xbe57fad4UL, 0x40000000UL, 0x3fdfb17cUL, 0x644f4ae7UL, 0x3e1ee43bUL,
  0x40000000UL, 0x3fdf8481UL, 0x903234d2UL, 0x3e501a86UL, 0x40000000UL,
  0x3fdf579cUL, 0xafe9e509UL, 0xbe267c3eUL, 0x00000000UL, 0x3fdf2acdUL,
  0xb7dfda0bUL, 0xbe48149bUL, 0x40000000UL, 0x3fdefe13UL, 0x3b94305eUL,
  0x3e5f4ea7UL, 0x80000000UL, 0x3fded16fUL, 0x5d95da61UL, 0xbe55c198UL,
  0x00000000UL, 0x3fdea4e1UL, 0x406960c9UL, 0xbdd99a19UL, 0x00000000UL,
  0x3fde7868UL, 0xd22f3539UL, 0x3e470c78UL, 0x80000000UL, 0x3fde4c04UL,
  0x83eec535UL, 0xbe3e1232UL, 0x40000000UL, 0x3fde1fb6UL, 0x3dfbffcbUL,
  0xbe4b7d71UL, 0x40000000UL, 0x3fddf37dUL, 0x7e1be4e0UL, 0xbe5b8f8fUL,
  0x40000000UL, 0x3fddc759UL, 0x46dae887UL, 0xbe350458UL, 0x80000000UL,
  0x3fdd9b4aUL, 0xed6ecc49UL, 0xbe5f0045UL, 0x80000000UL, 0x3fdd6f50UL,
  0x2e9e883cUL, 0x3e2915daUL, 0x80000000UL, 0x3fdd436bUL, 0xf0bccb32UL,
  0x3e4a68c9UL, 0x80000000UL, 0x3fdd179bUL, 0x9bbfc779UL, 0xbe54a26aUL,
  0x00000000UL, 0x3fdcebe0UL, 0x7cea33abUL, 0x3e43c6b7UL, 0x40000000UL,
  0x3fdcc039UL, 0xe740fd06UL, 0x3e5526c2UL, 0x40000000UL, 0x3fdc94a7UL,
  0x9eadeb1aUL, 0xbe396d8dUL, 0xc0000000UL, 0x3fdc6929UL, 0xf0a8f95aUL,
  0xbe5c0ab2UL, 0x80000000UL, 0x3fdc3dc0UL, 0x6ee2693bUL, 0x3e0992e6UL,
  0xc0000000UL, 0x3fdc126bUL, 0x5ac6b581UL, 0xbe2834b6UL, 0x40000000UL,
  0x3fdbe72bUL, 0x8cc226ffUL, 0x3e3596a6UL, 0x00000000UL, 0x3fdbbbffUL,
  0xf92a74bbUL, 0x3e3c5813UL, 0x00000000UL, 0x3fdb90e7UL, 0x479664c0UL,
  0xbe50d644UL, 0x00000000UL, 0x3fdb65e3UL, 0x5004975bUL, 0xbe55258fUL,
  0x00000000UL, 0x3fdb3af3UL, 0xe4b23194UL, 0xbe588407UL, 0xc0000000UL,
  0x3fdb1016UL, 0xe65d4d0aUL, 0x3e527c26UL, 0x80000000UL, 0x3fdae54eUL,
  0x814fddd6UL, 0x3e5962a2UL, 0x40000000UL, 0x3fdaba9aUL, 0xe19d0913UL,
  0xbe562f4eUL, 0x80000000UL, 0x3fda8ff9UL, 0x43cfd006UL, 0xbe4cfdebUL,
  0x40000000UL, 0x3fda656cUL, 0x686f0a4eUL, 0x3e5e47a8UL, 0xc0000000UL,
  0x3fda3af2UL, 0x7200d410UL, 0x3e5e1199UL, 0xc0000000UL, 0x3fda108cUL,
  0xabd2266eUL, 0x3e5ee4d1UL, 0x40000000UL, 0x3fd9e63aUL, 0x396f8f2cUL,
  0x3e4dbffbUL, 0x00000000UL, 0x3fd9bbfbUL, 0xe32b25ddUL, 0x3e5c3a54UL,
  0x40000000UL, 0x3fd991cfUL, 0x431e4035UL, 0xbe457925UL, 0x80000000UL,
  0x3fd967b6UL, 0x7bed3dd3UL, 0x3e40c61dUL, 0x00000000UL, 0x3fd93db1UL,
  0xd7449365UL, 0x3e306419UL, 0x80000000UL, 0x3fd913beUL, 0x1746e791UL,
  0x3e56fcfcUL, 0x40000000UL, 0x3fd8e9dfUL, 0xf3a9028bUL, 0xbe5041b9UL,
  0xc0000000UL, 0x3fd8c012UL, 0x56840c50UL, 0xbe26e20aUL, 0x40000000UL,
  0x3fd89659UL, 0x19763102UL, 0xbe51f466UL, 0x80000000UL, 0x3fd86cb2UL,
  0x7032de7cUL, 0xbe4d298aUL, 0x80000000UL, 0x3fd8431eUL, 0xdeb39fabUL,
  0xbe4361ebUL, 0x40000000UL, 0x3fd8199dUL, 0x5d01cbe0UL, 0xbe5425b3UL,
  0x80000000UL, 0x3fd7f02eUL, 0x3ce99aa9UL, 0x3e146fa8UL, 0x80000000UL,
  0x3fd7c6d2UL, 0xd1a262b9UL, 0xbe5a1a69UL, 0xc0000000UL, 0x3fd79d88UL,
  0x8606c236UL, 0x3e423a08UL, 0x80000000UL, 0x3fd77451UL, 0x8fd1e1b7UL,
  0x3e5a6a63UL, 0xc0000000UL, 0x3fd74b2cUL, 0xe491456aUL, 0x3e42c1caUL,
  0x40000000UL, 0x3fd7221aUL, 0x4499a6d7UL, 0x3e36a69aUL, 0x00000000UL,
  0x3fd6f91aUL, 0x5237df94UL, 0xbe0f8f02UL, 0x00000000UL, 0x3fd6d02cUL,
  0xb6482c6eUL, 0xbe5abcf7UL, 0x00000000UL, 0x3fd6a750UL, 0x1919fd61UL,
  0xbe57ade2UL, 0x00000000UL, 0x3fd67e86UL, 0xaa7a994dUL, 0xbe3f3fbdUL,
  0x00000000UL, 0x3fd655ceUL, 0x67db014cUL, 0x3e33c550UL, 0x00000000UL,
  0x3fd62d28UL, 0xa82856b7UL, 0xbe1409d1UL, 0xc0000000UL, 0x3fd60493UL,
  0x1e6a300dUL, 0x3e55d899UL, 0x80000000UL, 0x3fd5dc11UL, 0x1222bd5cUL,
  0xbe35bfc0UL, 0xc0000000UL, 0x3fd5b3a0UL, 0x6e8dc2d3UL, 0x3e5d4d79UL,
  0x00000000UL, 0x3fd58b42UL, 0xe0e4ace6UL, 0xbe517303UL, 0x80000000UL,
  0x3fd562f4UL, 0xb306e0a8UL, 0x3e5edf0fUL, 0xc0000000UL, 0x3fd53ab8UL,
  0x6574bc54UL, 0x3e5ee859UL, 0x80000000UL, 0x3fd5128eUL, 0xea902207UL,
  0x3e5f6188UL, 0xc0000000UL, 0x3fd4ea75UL, 0x9f911d79UL, 0x3e511735UL,
  0x80000000UL, 0x3fd4c26eUL, 0xf9c77397UL, 0xbe5b1643UL, 0x40000000UL,
  0x3fd49a78UL, 0x15fc9258UL, 0x3e479a37UL, 0x80000000UL, 0x3fd47293UL,
  0xd5a04dd9UL, 0xbe426e56UL, 0xc0000000UL, 0x3fd44abfUL, 0xe04042f5UL,
  0x3e56f7c6UL, 0x40000000UL, 0x3fd422fdUL, 0x1d8bf2c8UL, 0x3e5d8810UL,
  0x00000000UL, 0x3fd3fb4cUL, 0x88a8ddeeUL, 0xbe311454UL, 0xc0000000UL,
  0x3fd3d3abUL, 0x3e3b5e47UL, 0xbe5d1b72UL, 0x40000000UL, 0x3fd3ac1cUL,
  0xc2ab5d59UL, 0x3e31b02bUL, 0xc0000000UL, 0x3fd3849dUL, 0xd4e34b9eUL,
  0x3e51cb2fUL, 0x40000000UL, 0x3fd35d30UL, 0x177204fbUL, 0xbe2b8cd7UL,
  0x80000000UL, 0x3fd335d3UL, 0xfcd38c82UL, 0xbe4356e1UL, 0x80000000UL,
  0x3fd30e87UL, 0x64f54accUL, 0xbe4e6224UL, 0x00000000UL, 0x3fd2e74cUL,
  0xaa7975d9UL, 0x3e5dc0feUL, 0x80000000UL, 0x3fd2c021UL, 0x516dab3fUL,
  0xbe50ffa3UL, 0x40000000UL, 0x3fd29907UL, 0x2bfb7313UL, 0x3e5674a2UL,
  0xc0000000UL, 0x3fd271fdUL, 0x0549fc99UL, 0x3e385d29UL, 0xc0000000UL,
  0x3fd24b04UL, 0x55b63073UL, 0xbe500c6dUL, 0x00000000UL, 0x3fd2241cUL,
  0x3f91953aUL, 0x3e389977UL, 0xc0000000UL, 0x3fd1fd43UL, 0xa1543f71UL,
  0xbe3487abUL, 0xc0000000UL, 0x3fd1d67bUL, 0x4ec8867cUL, 0x3df6a2dcUL,
  0x00000000UL, 0x3fd1afc4UL, 0x4328e3bbUL, 0x3e41d9c0UL, 0x80000000UL,
  0x3fd1891cUL, 0x2e1cda84UL, 0x3e3bdd87UL, 0x40000000UL, 0x3fd16285UL,
  0x4b5331aeUL, 0xbe53128eUL, 0x00000000UL, 0x3fd13bfeUL, 0xb9aec164UL,
  0xbe52ac98UL, 0xc0000000UL, 0x3fd11586UL, 0xd91e1316UL, 0xbe350630UL,
  0x80000000UL, 0x3fd0ef1fUL, 0x7cacc12cUL, 0x3e3f5219UL, 0x40000000UL,
  0x3fd0c8c8UL, 0xbce277b7UL, 0x3e3d30c0UL, 0x00000000UL, 0x3fd0a281UL,
  0x2a63447dUL, 0xbe541377UL, 0x80000000UL, 0x3fd07c49UL, 0xfac483b5UL,
  0xbe5772ecUL, 0xc0000000UL, 0x3fd05621UL, 0x36b8a570UL, 0xbe4fd4bdUL,
  0xc0000000UL, 0x3fd03009UL, 0xbae505f7UL, 0xbe450388UL, 0x80000000UL,
  0x3fd00a01UL, 0x3e35aeadUL, 0xbe5430fcUL, 0x80000000UL, 0x3fcfc811UL,
  0x707475acUL, 0x3e38806eUL, 0x80000000UL, 0x3fcf7c3fUL, 0xc91817fcUL,
  0xbe40cceaUL, 0x80000000UL, 0x3fcf308cUL, 0xae05d5e9UL, 0xbe4919b8UL,
  0x80000000UL, 0x3fcee4f8UL, 0xae6cc9e6UL, 0xbe530b94UL, 0x00000000UL,
  0x3fce9983UL, 0x1efe3e8eUL, 0x3e57747eUL, 0x00000000UL, 0x3fce4e2dUL,
  0xda78d9bfUL, 0xbe59a608UL, 0x00000000UL, 0x3fce02f5UL, 0x8abe2c2eUL,
  0x3e4a35adUL, 0x00000000UL, 0x3fcdb7dcUL, 0x1495450dUL, 0xbe0872ccUL,
  0x80000000UL, 0x3fcd6ce1UL, 0x86ee0ba0UL, 0xbe4f59a0UL, 0x00000000UL,
  0x3fcd2205UL, 0xe81ca888UL, 0x3e5402c3UL, 0x00000000UL, 0x3fccd747UL,
  0x3b4424b9UL, 0x3e5dfdc3UL, 0x80000000UL, 0x3fcc8ca7UL, 0xd305b56cUL,
  0x3e202da6UL, 0x00000000UL, 0x3fcc4226UL, 0x399a6910UL, 0xbe482a1cUL,
  0x80000000UL, 0x3fcbf7c2UL, 0x747f7938UL, 0xbe587372UL, 0x80000000UL,
  0x3fcbad7cUL, 0x6fc246a0UL, 0x3e50d83dUL, 0x00000000UL, 0x3fcb6355UL,
  0xee9e9be5UL, 0xbe5c35bdUL, 0x80000000UL, 0x3fcb194aUL, 0x8416c0bcUL,
  0x3e546d4fUL, 0x00000000UL, 0x3fcacf5eUL, 0x49f7f08fUL, 0x3e56da76UL,
  0x00000000UL, 0x3fca858fUL, 0x5dc30de2UL, 0x3e5f390cUL, 0x00000000UL,
  0x3fca3bdeUL, 0x950583b6UL, 0xbe5e4169UL, 0x80000000UL, 0x3fc9f249UL,
  0x33631553UL, 0x3e52aeb1UL, 0x00000000UL, 0x3fc9a8d3UL, 0xde8795a6UL,
  0xbe59a504UL, 0x00000000UL, 0x3fc95f79UL, 0x076bf41eUL, 0x3e5122feUL,
  0x80000000UL, 0x3fc9163cUL, 0x2914c8e7UL, 0x3e3dd064UL, 0x00000000UL,
  0x3fc8cd1dUL, 0x3a30eca3UL, 0xbe21b4aaUL, 0x80000000UL, 0x3fc8841aUL,
  0xb2a96650UL, 0xbe575444UL, 0x80000000UL, 0x3fc83b34UL, 0x2376c0cbUL,
  0xbe2a74c7UL, 0x80000000UL, 0x3fc7f26bUL, 0xd8a0b653UL, 0xbe5181b6UL,
  0x00000000UL, 0x3fc7a9bfUL, 0x32257882UL, 0xbe4a78b4UL, 0x00000000UL,
  0x3fc7612fUL, 0x1eee8bd9UL, 0xbe1bfe9dUL, 0x80000000UL, 0x3fc718bbUL,
  0x0c603cc4UL, 0x3e36fdc9UL, 0x80000000UL, 0x3fc6d064UL, 0x3728b8cfUL,
  0xbe1e542eUL, 0x80000000UL, 0x3fc68829UL, 0xc79a4067UL, 0x3e5c380fUL,
  0x00000000UL, 0x3fc6400bUL, 0xf69eac69UL, 0x3e550a84UL, 0x80000000UL,
  0x3fc5f808UL, 0xb7a780a4UL, 0x3e5d9224UL, 0x80000000UL, 0x3fc5b022UL,
  0xad9dfb1eUL, 0xbe55242fUL, 0x00000000UL, 0x3fc56858UL, 0x659b18beUL,
  0xbe4bfda3UL, 0x80000000UL, 0x3fc520a9UL, 0x66ee3631UL, 0xbe57d769UL,
  0x80000000UL, 0x3fc4d916UL, 0x1ec62819UL, 0x3e2427f7UL, 0x80000000UL,
  0x3fc4919fUL, 0xdec25369UL, 0xbe435431UL, 0x00000000UL, 0x3fc44a44UL,
  0xa8acfc4bUL, 0xbe3c62e8UL, 0x00000000UL, 0x3fc40304UL, 0xcf1d3eabUL,
  0xbdfba29fUL, 0x80000000UL, 0x3fc3bbdfUL, 0x79aba3eaUL, 0xbdf1b7c8UL,
  0x80000000UL, 0x3fc374d6UL, 0xb8d186daUL, 0xbe5130cfUL, 0x80000000UL,
  0x3fc32de8UL, 0x9d74f152UL, 0x3e2285b6UL, 0x00000000UL, 0x3fc2e716UL,
  0x50ae7ca9UL, 0xbe503920UL, 0x80000000UL, 0x3fc2a05eUL, 0x6caed92eUL,
  0xbe533924UL, 0x00000000UL, 0x3fc259c2UL, 0x9cb5034eUL, 0xbe510e31UL,
  0x80000000UL, 0x3fc21340UL, 0x12c4d378UL, 0xbe540b43UL, 0x80000000UL,
  0x3fc1ccd9UL, 0xcc418706UL, 0x3e59887aUL, 0x00000000UL, 0x3fc1868eUL,
  0x921f4106UL, 0xbe528e67UL, 0x80000000UL, 0x3fc1405cUL, 0x3969441eUL,
  0x3e5d8051UL, 0x00000000UL, 0x3fc0fa46UL, 0xd941ef5bUL, 0x3e5f9079UL,
  0x80000000UL, 0x3fc0b44aUL, 0x5a3e81b2UL, 0xbe567691UL, 0x00000000UL,
  0x3fc06e69UL, 0x9d66afe7UL, 0xbe4d43fbUL, 0x00000000UL, 0x3fc028a2UL,
  0x0a92a162UL, 0xbe52f394UL, 0x00000000UL, 0x3fbfc5eaUL, 0x209897e5UL,
  0x3e529e37UL, 0x00000000UL, 0x3fbf3ac5UL, 0x8458bd7bUL, 0x3e582831UL,
  0x00000000UL, 0x3fbeafd5UL, 0xb8d8b4b8UL, 0xbe486b4aUL, 0x00000000UL,
  0x3fbe2518UL, 0xe0a3b7b6UL, 0x3e5bafd2UL, 0x00000000UL, 0x3fbd9a90UL,
  0x2bf2710eUL, 0x3e383b2bUL, 0x00000000UL, 0x3fbd103cUL, 0x73eb6ab7UL,
  0xbe56d78dUL, 0x00000000UL, 0x3fbc861bUL, 0x32ceaff5UL, 0xbe32dc5aUL,
  0x00000000UL, 0x3fbbfc2eUL, 0xbee04cb7UL, 0xbe4a71a4UL, 0x00000000UL,
  0x3fbb7274UL, 0x35ae9577UL, 0x3e38142fUL, 0x00000000UL, 0x3fbae8eeUL,
  0xcbaddab4UL, 0xbe5490f0UL, 0x00000000UL, 0x3fba5f9aUL, 0x95ce1114UL,
  0x3e597c71UL, 0x00000000UL, 0x3fb9d67aUL, 0x6d7c0f78UL, 0x3e3abc2dUL,
  0x00000000UL, 0x3fb94d8dUL, 0x2841a782UL, 0xbe566cbcUL, 0x00000000UL,
  0x3fb8c4d2UL, 0x6ed429c6UL, 0xbe3cfff9UL, 0x00000000UL, 0x3fb83c4aUL,
  0xe4a49fbbUL, 0xbe552964UL, 0x00000000UL, 0x3fb7b3f4UL, 0x2193d81eUL,
  0xbe42fa72UL, 0x00000000UL, 0x3fb72bd0UL, 0xdd70c122UL, 0x3e527a8cUL,
  0x00000000UL, 0x3fb6a3dfUL, 0x03108a54UL, 0xbe450393UL, 0x00000000UL,
  0x3fb61c1fUL, 0x30ff7954UL, 0x3e565840UL, 0x00000000UL, 0x3fb59492UL,
  0xdedd460cUL, 0xbe5422b5UL, 0x00000000UL, 0x3fb50d36UL, 0x950f9f45UL,
  0xbe5313f6UL, 0x00000000UL, 0x3fb4860bUL, 0x582cdcb1UL, 0x3e506d39UL,
  0x00000000UL, 0x3fb3ff12UL, 0x7216d3a6UL, 0x3e4aa719UL, 0x00000000UL,
  0x3fb3784aUL, 0x57a423fdUL, 0x3e5a9b9fUL, 0x00000000UL, 0x3fb2f1b4UL,
  0x7a138b41UL, 0xbe50b418UL, 0x00000000UL, 0x3fb26b4eUL, 0x2fbfd7eaUL,
  0x3e23a53eUL, 0x00000000UL, 0x3fb1e519UL, 0x18913ccbUL, 0x3e465fc1UL,
  0x00000000UL, 0x3fb15f15UL, 0x7ea24e21UL, 0x3e042843UL, 0x00000000UL,
  0x3fb0d941UL, 0x7c6d9c77UL, 0x3e59f61eUL, 0x00000000UL, 0x3fb0539eUL,
  0x114efd44UL, 0x3e4ccab7UL, 0x00000000UL, 0x3faf9c56UL, 0x1777f657UL,
  0x3e552f65UL, 0x00000000UL, 0x3fae91d2UL, 0xc317b86aUL, 0xbe5a61e0UL,
  0x00000000UL, 0x3fad87acUL, 0xb7664efbUL, 0xbe41f64eUL, 0x00000000UL,
  0x3fac7de6UL, 0x5d3d03a9UL, 0x3e0807a0UL, 0x00000000UL, 0x3fab7480UL,
  0x743c38ebUL, 0xbe3726e1UL, 0x00000000UL, 0x3faa6b78UL, 0x06a253f1UL,
  0x3e5ad636UL, 0x00000000UL, 0x3fa962d0UL, 0xa35f541bUL, 0x3e5a187aUL,
  0x00000000UL, 0x3fa85a88UL, 0x4b86e446UL, 0xbe508150UL, 0x00000000UL,
  0x3fa7529cUL, 0x2589cacfUL, 0x3e52938aUL, 0x00000000UL, 0x3fa64b10UL,
  0xaf6b11f2UL, 0xbe3454cdUL, 0x00000000UL, 0x3fa543e2UL, 0x97506fefUL,
  0xbe5fdec5UL, 0x00000000UL, 0x3fa43d10UL, 0xe75f7dd9UL, 0xbe388dd3UL,
  0x00000000UL, 0x3fa3369cUL, 0xa4139632UL, 0xbdea5177UL, 0x00000000UL,
  0x3fa23086UL, 0x352d6f1eUL, 0xbe565ad6UL, 0x00000000UL, 0x3fa12accUL,
  0x77449eb7UL, 0xbe50d5c7UL, 0x00000000UL, 0x3fa0256eUL, 0x7478da78UL,
  0x3e404724UL, 0x00000000UL, 0x3f9e40dcUL, 0xf59cef7fUL, 0xbe539d0aUL,
  0x00000000UL, 0x3f9c3790UL, 0x1511d43cUL, 0x3e53c2c8UL, 0x00000000UL,
  0x3f9a2f00UL, 0x9b8bff3cUL, 0xbe43b3e1UL, 0x00000000UL, 0x3f982724UL,
  0xad1e22a5UL, 0x3e46f0bdUL, 0x00000000UL, 0x3f962000UL, 0x130d9356UL,
  0x3e475ba0UL, 0x00000000UL, 0x3f941994UL, 0x8f86f883UL, 0xbe513d0bUL,
  0x00000000UL, 0x3f9213dcUL, 0x914d0dc8UL, 0xbe534335UL, 0x00000000UL,
  0x3f900ed8UL, 0x2d73e5e7UL, 0xbe22ba75UL, 0x00000000UL, 0x3f8c1510UL,
  0xc5b7d70eUL, 0x3e599c5dUL, 0x00000000UL, 0x3f880de0UL, 0x8a27857eUL,
  0xbe3d28c8UL, 0x00000000UL, 0x3f840810UL, 0xda767328UL, 0x3e531b3dUL,
  0x00000000UL, 0x3f8003b0UL, 0x77bacaf3UL, 0xbe5f04e3UL, 0x00000000UL,
  0x3f780150UL, 0xdf4b0720UL, 0x3e5a8bffUL, 0x00000000UL, 0x3f6ffc40UL,
  0x34c48e71UL, 0xbe3fcd99UL, 0x00000000UL, 0x3f5ff6c0UL, 0x1ad218afUL,
  0xbe4c78a7UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x80000000UL,
  0x00000000UL, 0xfffff800UL, 0x00000000UL, 0xfffff800UL, 0x00000000UL,
  0x3ff72000UL, 0x161bb241UL, 0xbf5dabe1UL, 0x6dc96112UL, 0xbf836578UL,
  0xee241472UL, 0xbf9b0301UL, 0x9f95985aUL, 0xbfb528dbUL, 0xb3841d2aUL,
  0xbfd619b6UL, 0x518775e3UL, 0x3f9004f2UL, 0xac8349bbUL, 0x3fa76c9bUL,
  0x486ececcUL, 0x3fc4635eUL, 0x161bb241UL, 0xbf5dabe1UL, 0x9f95985aUL,
  0xbfb528dbUL, 0xf8b5787dUL, 0x3ef2531eUL, 0x486ececbUL, 0x3fc4635eUL,
  0x412055ccUL, 0xbdd61bb2UL, 0x00000000UL, 0xfffffff8UL, 0x00000000UL,
  0xffffffffUL, 0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0x3b700000UL,
  0xfa5abcbfUL, 0x3ff00b1aUL, 0xa7609f71UL, 0xbc84f6b2UL, 0xa9fb3335UL,
  0x3ff0163dUL, 0x9ab8cdb7UL, 0x3c9b6129UL, 0x143b0281UL, 0x3ff02168UL,
  0x0fc54eb6UL, 0xbc82bf31UL, 0x3e778061UL, 0x3ff02c9aUL, 0x535b085dUL,
  0xbc719083UL, 0x2e11bbccUL, 0x3ff037d4UL, 0xeeade11aUL, 0x3c656811UL,
  0xe86e7f85UL, 0x3ff04315UL, 0x1977c96eUL, 0xbc90a31cUL, 0x72f654b1UL,
  0x3ff04e5fUL, 0x3aa0d08cUL, 0x3c84c379UL, 0xd3158574UL, 0x3ff059b0UL,
  0xa475b465UL, 0x3c8d73e2UL, 0x0e3c1f89UL, 0x3ff0650aUL, 0x5799c397UL,
  0xbc95cb7bUL, 0x29ddf6deUL, 0x3ff0706bUL, 0xe2b13c27UL, 0xbc8c91dfUL,
  0x2b72a836UL, 0x3ff07bd4UL, 0x54458700UL, 0x3c832334UL, 0x18759bc8UL,
  0x3ff08745UL, 0x4bb284ffUL, 0x3c6186beUL, 0xf66607e0UL, 0x3ff092bdUL,
  0x800a3fd1UL, 0xbc968063UL, 0xcac6f383UL, 0x3ff09e3eUL, 0x18316136UL,
  0x3c914878UL, 0x9b1f3919UL, 0x3ff0a9c7UL, 0x873d1d38UL, 0x3c85d16cUL,
  0x6cf9890fUL, 0x3ff0b558UL, 0x4adc610bUL, 0x3c98a62eUL, 0x45e46c85UL,
  0x3ff0c0f1UL, 0x06d21cefUL, 0x3c94f989UL, 0x2b7247f7UL, 0x3ff0cc92UL,
  0x16e24f71UL, 0x3c901edcUL, 0x23395decUL, 0x3ff0d83bUL, 0xe43f316aUL,
  0xbc9bc14dUL, 0x32d3d1a2UL, 0x3ff0e3ecUL, 0x27c57b52UL, 0x3c403a17UL,
  0x5fdfa9c5UL, 0x3ff0efa5UL, 0xbc54021bUL, 0xbc949db9UL, 0xaffed31bUL,
  0x3ff0fb66UL, 0xc44ebd7bUL, 0xbc6b9bedUL, 0x28d7233eUL, 0x3ff10730UL,
  0x1692fdd5UL, 0x3c8d46ebUL, 0xd0125b51UL, 0x3ff11301UL, 0x39449b3aUL,
  0xbc96c510UL, 0xab5e2ab6UL, 0x3ff11edbUL, 0xf703fb72UL, 0xbc9ca454UL,
  0xc06c31ccUL, 0x3ff12abdUL, 0xb36ca5c7UL, 0xbc51b514UL, 0x14f204abUL,
  0x3ff136a8UL, 0xba48dcf0UL, 0xbc67108fUL, 0xaea92de0UL, 0x3ff1429aUL,
  0x9af1369eUL, 0xbc932fbfUL, 0x934f312eUL, 0x3ff14e95UL, 0x39bf44abUL,
  0xbc8b91e8UL, 0xc8a58e51UL, 0x3ff15a98UL, 0xb9eeab0aUL, 0x3c82406aUL,
  0x5471c3c2UL, 0x3ff166a4UL, 0x82ea1a32UL, 0x3c58f23bUL, 0x3c7d517bUL,
  0x3ff172b8UL, 0xb9d78a76UL, 0xbc819041UL, 0x8695bbc0UL, 0x3ff17ed4UL,
  0xe2ac5a64UL, 0x3c709e3fUL, 0x388c8deaUL, 0x3ff18af9UL, 0xd1970f6cUL,
  0xbc911023UL, 0x58375d2fUL, 0x3ff19726UL, 0x85f17e08UL, 0x3c94aaddUL,
  0xeb6fcb75UL, 0x3ff1a35bUL, 0x7b4968e4UL, 0x3c8e5b4cUL, 0xf8138a1cUL,
  0x3ff1af99UL, 0xa4b69280UL, 0x3c97bf85UL, 0x84045cd4UL, 0x3ff1bbe0UL,
  0x352ef607UL, 0xbc995386UL, 0x95281c6bUL, 0x3ff1c82fUL, 0x8010f8c9UL,
  0x3c900977UL, 0x3168b9aaUL, 0x3ff1d487UL, 0x00a2643cUL, 0x3c9e016eUL,
  0x5eb44027UL, 0x3ff1e0e7UL, 0x088cb6deUL, 0xbc96fdd8UL, 0x22fcd91dUL,
  0x3ff1ed50UL, 0x027bb78cUL, 0xbc91df98UL, 0x8438ce4dUL, 0x3ff1f9c1UL,
  0xa097af5cUL, 0xbc9bf524UL, 0x88628cd6UL, 0x3ff2063bUL, 0x814a8495UL,
  0x3c8dc775UL, 0x3578a819UL, 0x3ff212beUL, 0x2cfcaac9UL, 0x3c93592dUL,
  0x917ddc96UL, 0x3ff21f49UL, 0x9494a5eeUL, 0x3c82a97eUL, 0xa27912d1UL,
  0x3ff22bddUL, 0x5577d69fUL, 0x3c8d34fbUL, 0x6e756238UL, 0x3ff2387aUL,
  0xb6c70573UL, 0x3c99b07eUL, 0xfb82140aUL, 0x3ff2451fUL, 0x911ca996UL,
  0x3c8acfccUL, 0x4fb2a63fUL, 0x3ff251ceUL, 0xbef4f4a4UL, 0x3c8ac155UL,
  0x711ece75UL, 0x3ff25e85UL, 0x4ac31b2cUL, 0x3c93e1a2UL, 0x65e27cddUL,
  0x3ff26b45UL, 0x9940e9d9UL, 0x3c82bd33UL, 0x341ddf29UL, 0x3ff2780eUL,
  0x05f9e76cUL, 0x3c9e067cUL, 0xe1f56381UL, 0x3ff284dfUL, 0x8c3f0d7eUL,
  0xbc9a4c3aUL, 0x7591bb70UL, 0x3ff291baUL, 0x28401cbdUL, 0xbc82cc72UL,
  0xf51fdee1UL, 0x3ff29e9dUL, 0xafad1255UL, 0x3c8612e8UL, 0x66d10f13UL,
  0x3ff2ab8aUL, 0x191690a7UL, 0xbc995743UL, 0xd0dad990UL, 0x3ff2b87fUL,
  0xd6381aa4UL, 0xbc410adcUL, 0x39771b2fUL, 0x3ff2c57eUL, 0xa6eb5124UL,
  0xbc950145UL, 0xa6e4030bUL, 0x3ff2d285UL, 0x54db41d5UL, 0x3c900247UL,
  0x1f641589UL, 0x3ff2df96UL, 0xfbbce198UL, 0x3c9d16cfUL, 0xa93e2f56UL,
  0x3ff2ecafUL, 0x45d52383UL, 0x3c71ca0fUL, 0x4abd886bUL, 0x3ff2f9d2UL,
  0x532bda93UL, 0xbc653c55UL, 0x0a31b715UL, 0x3ff306feUL, 0xd23182e4UL,
  0x3c86f46aUL, 0xedeeb2fdUL, 0x3ff31432UL, 0xf3f3fcd1UL, 0x3c8959a3UL,
  0xfc4cd831UL, 0x3ff32170UL, 0x8e18047cUL, 0x3c8a9ce7UL, 0x3ba8ea32UL,
  0x3ff32eb8UL, 0x3cb4f318UL, 0xbc9c45e8UL, 0xb26416ffUL, 0x3ff33c08UL,
  0x843659a6UL, 0x3c932721UL, 0x66e3fa2dUL, 0x3ff34962UL, 0x930881a4UL,
  0xbc835a75UL, 0x5f929ff1UL, 0x3ff356c5UL, 0x5c4e4628UL, 0xbc8b5ceeUL,
  0xa2de883bUL, 0x3ff36431UL, 0xa06cb85eUL, 0xbc8c3144UL, 0x373aa9cbUL,
  0x3ff371a7UL, 0xbf42eae2UL, 0xbc963aeaUL, 0x231e754aUL, 0x3ff37f26UL,
  0x9eceb23cUL, 0xbc99f5caUL, 0x6d05d866UL, 0x3ff38caeUL, 0x3c9904bdUL,
  0xbc9e958dUL, 0x1b7140efUL, 0x3ff39a40UL, 0xfc8e2934UL, 0xbc99a9a5UL,
  0x34e59ff7UL, 0x3ff3a7dbUL, 0xd661f5e3UL, 0xbc75e436UL, 0xbfec6cf4UL,
  0x3ff3b57fUL, 0xe26fff18UL, 0x3c954c66UL, 0xc313a8e5UL, 0x3ff3c32dUL,
  0x375d29c3UL, 0xbc9efff8UL, 0x44ede173UL, 0x3ff3d0e5UL, 0x8c284c71UL,
  0x3c7fe8d0UL, 0x4c123422UL, 0x3ff3dea6UL, 0x11f09ebcUL, 0x3c8ada09UL,
  0xdf1c5175UL, 0x3ff3ec70UL, 0x7b8c9bcaUL, 0xbc8af663UL, 0x04ac801cUL,
  0x3ff3fa45UL, 0xf956f9f3UL, 0xbc97d023UL, 0xc367a024UL, 0x3ff40822UL,
  0xb6f4d048UL, 0x3c8bddf8UL, 0x21f72e2aUL, 0x3ff4160aUL, 0x1c309278UL,
  0xbc5ef369UL, 0x2709468aUL, 0x3ff423fbUL, 0xc0b314ddUL, 0xbc98462dUL,
  0xd950a897UL, 0x3ff431f5UL, 0xe35f7999UL, 0xbc81c7ddUL, 0x3f84b9d4UL,
  0x3ff43ffaUL, 0x9704c003UL, 0x3c8880beUL, 0x6061892dUL, 0x3ff44e08UL,
  0x04ef80d0UL, 0x3c489b7aUL, 0x42a7d232UL, 0x3ff45c20UL, 0x82fb1f8eUL,
  0xbc686419UL, 0xed1d0057UL, 0x3ff46a41UL, 0xd1648a76UL, 0x3c9c944bUL,
  0x668b3237UL, 0x3ff4786dUL, 0xed445733UL, 0xbc9c20f0UL, 0xb5c13cd0UL,
  0x3ff486a2UL, 0xb69062f0UL, 0x3c73c1a3UL, 0xe192aed2UL, 0x3ff494e1UL,
  0x5e499ea0UL, 0xbc83b289UL, 0xf0d7d3deUL, 0x3ff4a32aUL, 0xf3d1be56UL,
  0x3c99cb62UL, 0xea6db7d7UL, 0x3ff4b17dUL, 0x7f2897f0UL, 0xbc8125b8UL,
  0xd5362a27UL, 0x3ff4bfdaUL, 0xafec42e2UL, 0x3c7d4397UL, 0xb817c114UL,
  0x3ff4ce41UL, 0x690abd5dUL, 0x3c905e29UL, 0x99fddd0dUL, 0x3ff4dcb2UL,
  0xbc6a7833UL, 0x3c98ecdbUL, 0x81d8abffUL, 0x3ff4eb2dUL, 0x2e5d7a52UL,
  0xbc95257dUL, 0x769d2ca7UL, 0x3ff4f9b2UL, 0xd25957e3UL, 0xbc94b309UL,
  0x7f4531eeUL, 0x3ff50841UL, 0x49b7465fUL, 0x3c7a249bUL, 0xa2cf6642UL,
  0x3ff516daUL, 0x69bd93efUL, 0xbc8f7685UL, 0xe83f4eefUL, 0x3ff5257dUL,
  0x43efef71UL, 0xbc7c998dUL, 0x569d4f82UL, 0x3ff5342bUL, 0x1db13cadUL,
  0xbc807abeUL, 0xf4f6ad27UL, 0x3ff542e2UL, 0x192d5f7eUL, 0x3c87926dUL,
  0xca5d920fUL, 0x3ff551a4UL, 0xefede59bUL, 0xbc8d689cUL, 0xdde910d2UL,
  0x3ff56070UL, 0x168eebf0UL, 0xbc90fb6eUL, 0x36b527daUL, 0x3ff56f47UL,
  0x011d93adUL, 0x3c99bb2cUL, 0xdbe2c4cfUL, 0x3ff57e27UL, 0x8a57b9c4UL,
  0xbc90b98cUL, 0xd497c7fdUL, 0x3ff58d12UL, 0x5b9a1de8UL, 0x3c8295e1UL,
  0x27ff07ccUL, 0x3ff59c08UL, 0xe467e60fUL, 0xbc97e2ceUL, 0xdd485429UL,
  0x3ff5ab07UL, 0x054647adUL, 0x3c96324cUL, 0xfba87a03UL, 0x3ff5ba11UL,
  0x4c233e1aUL, 0xbc9b77a1UL, 0x8a5946b7UL, 0x3ff5c926UL, 0x816986a2UL,
  0x3c3c4b1bUL, 0x90998b93UL, 0x3ff5d845UL, 0xa8b45643UL, 0xbc9cd6a7UL,
  0x15ad2148UL, 0x3ff5e76fUL, 0x3080e65eUL, 0x3c9ba6f9UL, 0x20dceb71UL,
  0x3ff5f6a3UL, 0xe3cdcf92UL, 0xbc89eaddUL, 0xb976dc09UL, 0x3ff605e1UL,
  0x9b56de47UL, 0xbc93e242UL, 0xe6cdf6f4UL, 0x3ff6152aUL, 0x4ab84c27UL,
  0x3c9e4b3eUL, 0xb03a5585UL, 0x3ff6247eUL, 0x7e40b497UL, 0xbc9383c1UL,
  0x1d1929fdUL, 0x3ff633ddUL, 0xbeb964e5UL, 0x3c984710UL, 0x34ccc320UL,
  0x3ff64346UL, 0x759d8933UL, 0xbc8c483cUL, 0xfebc8fb7UL, 0x3ff652b9UL,
  0xc9a73e09UL, 0xbc9ae3d5UL, 0x82552225UL, 0x3ff66238UL, 0x87591c34UL,
  0xbc9bb609UL, 0xc70833f6UL, 0x3ff671c1UL, 0x586c6134UL, 0xbc8e8732UL,
  0xd44ca973UL, 0x3ff68155UL, 0x44f73e65UL, 0x3c6038aeUL, 0xb19e9538UL,
  0x3ff690f4UL, 0x9aeb445dUL, 0x3c8804bdUL, 0x667f3bcdUL, 0x3ff6a09eUL,
  0x13b26456UL, 0xbc9bdd34UL, 0xfa75173eUL, 0x3ff6b052UL, 0x2c9a9d0eUL,
  0x3c7a38f5UL, 0x750bdabfUL, 0x3ff6c012UL, 0x67ff0b0dUL, 0xbc728956UL,
  0xddd47645UL, 0x3ff6cfdcUL, 0xb6f17309UL, 0x3c9c7aa9UL, 0x3c651a2fUL,
  0x3ff6dfb2UL, 0x683c88abUL, 0xbc6bbe3aUL, 0x98593ae5UL, 0x3ff6ef92UL,
  0x9e1ac8b2UL, 0xbc90b974UL, 0xf9519484UL, 0x3ff6ff7dUL, 0x25860ef6UL,
  0xbc883c0fUL, 0x66f42e87UL, 0x3ff70f74UL, 0xd45aa65fUL, 0x3c59d644UL,
  0xe8ec5f74UL, 0x3ff71f75UL, 0x86887a99UL, 0xbc816e47UL, 0x86ead08aUL,
  0x3ff72f82UL, 0x2cd62c72UL, 0xbc920aa0UL, 0x48a58174UL, 0x3ff73f9aUL,
  0x6c65d53cUL, 0xbc90a8d9UL, 0x35d7cbfdUL, 0x3ff74fbdUL, 0x618a6e1cUL,
  0x3c9047fdUL, 0x564267c9UL, 0x3ff75febUL, 0x57316dd3UL, 0xbc902459UL,
  0xb1ab6e09UL, 0x3ff77024UL, 0x169147f8UL, 0x3c9b7877UL, 0x4fde5d3fUL,
  0x3ff78069UL, 0x0a02162dUL, 0x3c9866b8UL, 0x38ac1cf6UL, 0x3ff790b9UL,
  0x62aadd3eUL, 0x3c9349a8UL, 0x73eb0187UL, 0x3ff7a114UL, 0xee04992fUL,
  0xbc841577UL, 0x0976cfdbUL, 0x3ff7b17bUL, 0x8468dc88UL, 0xbc9bebb5UL,
  0x0130c132UL, 0x3ff7c1edUL, 0xd1164dd6UL, 0x3c9f124cUL, 0x62ff86f0UL,
  0x3ff7d26aUL, 0xfb72b8b4UL, 0x3c91bddbUL, 0x36cf4e62UL, 0x3ff7e2f3UL,
  0xba15797eUL, 0x3c705d02UL, 0x8491c491UL, 0x3ff7f387UL, 0xcf9311aeUL,
  0xbc807f11UL, 0x543e1a12UL, 0x3ff80427UL, 0x626d972bUL, 0xbc927c86UL,
  0xadd106d9UL, 0x3ff814d2UL, 0x0d151d4dUL, 0x3c946437UL, 0x994cce13UL,
  0x3ff82589UL, 0xd41532d8UL, 0xbc9d4c1dUL, 0x1eb941f7UL, 0x3ff8364cUL,
  0x31df2bd5UL, 0x3c999b9aUL, 0x4623c7adUL, 0x3ff8471aUL, 0xa341cdfbUL,
  0xbc88d684UL, 0x179f5b21UL, 0x3ff857f4UL, 0xf8b216d0UL, 0xbc5ba748UL,
  0x9b4492edUL, 0x3ff868d9UL, 0x9bd4f6baUL, 0xbc9fc6f8UL, 0xd931a436UL,
  0x3ff879caUL, 0xd2db47bdUL, 0x3c85d2d7UL, 0xd98a6699UL, 0x3ff88ac7UL,
  0xf37cb53aUL, 0x3c9994c2UL, 0xa478580fUL, 0x3ff89bd0UL, 0x4475202aUL,
  0x3c9d5395UL, 0x422aa0dbUL, 0x3ff8ace5UL, 0x56864b27UL, 0x3c96e9f1UL,
  0xbad61778UL, 0x3ff8be05UL, 0xfc43446eUL, 0x3c9ecb5eUL, 0x16b5448cUL,
  0x3ff8cf32UL, 0x32e9e3aaUL, 0xbc70d55eUL, 0x5e0866d9UL, 0x3ff8e06aUL,
  0x6fc9b2e6UL, 0xbc97114aUL, 0x99157736UL, 0x3ff8f1aeUL, 0xa2e3976cUL,
  0x3c85cc13UL, 0xd0282c8aUL, 0x3ff902feUL, 0x85fe3fd2UL, 0x3c9592caUL,
  0x0b91ffc6UL, 0x3ff9145bUL, 0x2e582524UL, 0xbc9dd679UL, 0x53aa2fe2UL,
  0x3ff925c3UL, 0xa639db7fUL, 0xbc83455fUL, 0xb0cdc5e5UL, 0x3ff93737UL,
  0x81b57ebcUL, 0xbc675fc7UL, 0x2b5f98e5UL, 0x3ff948b8UL, 0x797d2d99UL,
  0xbc8dc3d6UL, 0xcbc8520fUL, 0x3ff95a44UL, 0x96a5f039UL, 0xbc764b7cUL,
  0x9a7670b3UL, 0x3ff96bddUL, 0x7f19c896UL, 0xbc5ba596UL, 0x9fde4e50UL,
  0x3ff97d82UL, 0x7c1b85d1UL, 0xbc9d185bUL, 0xe47a22a2UL, 0x3ff98f33UL,
  0xa24c78ecUL, 0x3c7cabdaUL, 0x70ca07baUL, 0x3ff9a0f1UL, 0x91cee632UL,
  0xbc9173bdUL, 0x4d53fe0dUL, 0x3ff9b2bbUL, 0x4df6d518UL, 0xbc9dd84eUL,
  0x82a3f090UL, 0x3ff9c491UL, 0xb071f2beUL, 0x3c7c7c46UL, 0x194bb8d5UL,
  0x3ff9d674UL, 0xa3dd8233UL, 0xbc9516beUL, 0x19e32323UL, 0x3ff9e863UL,
  0x78e64c6eUL, 0x3c7824caUL, 0x8d07f29eUL, 0x3ff9fa5eUL, 0xaaf1faceUL,
  0xbc84a9ceUL, 0x7b5de565UL, 0x3ffa0c66UL, 0x5d1cd533UL, 0xbc935949UL,
  0xed8eb8bbUL, 0x3ffa1e7aUL, 0xee8be70eUL, 0x3c9c6618UL, 0xec4a2d33UL,
  0x3ffa309bUL, 0x7ddc36abUL, 0x3c96305cUL, 0x80460ad8UL, 0x3ffa42c9UL,
  0x589fb120UL, 0xbc9aa780UL, 0xb23e255dUL, 0x3ffa5503UL, 0xdb8d41e1UL,
  0xbc9d2f6eUL, 0x8af46052UL, 0x3ffa674aUL, 0x30670366UL, 0x3c650f56UL,
  0x1330b358UL, 0x3ffa799eUL, 0xcac563c7UL, 0x3c9bcb7eUL, 0x53c12e59UL,
  0x3ffa8bfeUL, 0xb2ba15a9UL, 0xbc94f867UL, 0x5579fdbfUL, 0x3ffa9e6bUL,
  0x0ef7fd31UL, 0x3c90fac9UL, 0x21356ebaUL, 0x3ffab0e5UL, 0xdae94545UL,
  0x3c889c31UL, 0xbfd3f37aUL, 0x3ffac36bUL, 0xcae76cd0UL, 0xbc8f9234UL,
  0x3a3c2774UL, 0x3ffad5ffUL, 0xb6b1b8e5UL, 0x3c97ef3bUL, 0x995ad3adUL,
  0x3ffae89fUL, 0x345dcc81UL, 0x3c97a1cdUL, 0xe622f2ffUL, 0x3ffafb4cUL,
  0x0f315ecdUL, 0xbc94b2fcUL, 0x298db666UL, 0x3ffb0e07UL, 0x4c80e425UL,
  0xbc9bdef5UL, 0x6c9a8952UL, 0x3ffb20ceUL, 0x4a0756ccUL, 0x3c94dd02UL,
  0xb84f15fbUL, 0x3ffb33a2UL, 0x3084d708UL, 0xbc62805eUL, 0x15b749b1UL,
  0x3ffb4684UL, 0xe9df7c90UL, 0xbc7f763dUL, 0x8de5593aUL, 0x3ffb5972UL,
  0xbbba6de3UL, 0xbc9c71dfUL, 0x29f1c52aUL, 0x3ffb6c6eUL, 0x52883f6eUL,
  0x3c92a8f3UL, 0xf2fb5e47UL, 0x3ffb7f76UL, 0x7e54ac3bUL, 0xbc75584fUL,
  0xf22749e4UL, 0x3ffb928cUL, 0x54cb65c6UL, 0xbc9b7216UL, 0x30a1064aUL,
  0x3ffba5b0UL, 0x0e54292eUL, 0xbc9efcd3UL, 0xb79a6f1fUL, 0x3ffbb8e0UL,
  0xc9696205UL, 0xbc3f52d1UL, 0x904bc1d2UL, 0x3ffbcc1eUL, 0x7a2d9e84UL,
  0x3c823dd0UL, 0xc3f3a207UL, 0x3ffbdf69UL, 0x60ea5b53UL, 0xbc3c2623UL,
  0x5bd71e09UL, 0x3ffbf2c2UL, 0x3f6b9c73UL, 0xbc9efdcaUL, 0x6141b33dUL,
  0x3ffc0628UL, 0xa1fbca34UL, 0xbc8d8a5aUL, 0xdd85529cUL, 0x3ffc199bUL,
  0x895048ddUL, 0x3c811065UL, 0xd9fa652cUL, 0x3ffc2d1cUL, 0x17c8a5d7UL,
  0xbc96e516UL, 0x5fffd07aUL, 0x3ffc40abUL, 0xe083c60aUL, 0x3c9b4537UL,
  0x78fafb22UL, 0x3ffc5447UL, 0x2493b5afUL, 0x3c912f07UL, 0x2e57d14bUL,
  0x3ffc67f1UL, 0xff483cadUL, 0x3c92884dUL, 0x8988c933UL, 0x3ffc7ba8UL,
  0xbe255559UL, 0xbc8e76bbUL, 0x9406e7b5UL, 0x3ffc8f6dUL, 0x48805c44UL,
  0x3c71acbcUL, 0x5751c4dbUL, 0x3ffca340UL, 0xd10d08f5UL, 0xbc87f2beUL,
  0xdcef9069UL, 0x3ffcb720UL, 0xd1e949dbUL, 0x3c7503cbUL, 0x2e6d1675UL,
  0x3ffccb0fUL, 0x86009092UL, 0xbc7d220fUL, 0x555dc3faUL, 0x3ffcdf0bUL,
  0x53829d72UL, 0xbc8dd83bUL, 0x5b5bab74UL, 0x3ffcf315UL, 0xb86dff57UL,
  0xbc9a08e9UL, 0x4a07897cUL, 0x3ffd072dUL, 0x43797a9cUL, 0xbc9cbc37UL,
  0x2b08c968UL, 0x3ffd1b53UL, 0x219a36eeUL, 0x3c955636UL, 0x080d89f2UL,
  0x3ffd2f87UL, 0x719d8578UL, 0xbc9d487bUL, 0xeacaa1d6UL, 0x3ffd43c8UL,
  0xbf5a1614UL, 0x3c93db53UL, 0xdcfba487UL, 0x3ffd5818UL, 0xd75b3707UL,
  0x3c82ed02UL, 0xe862e6d3UL, 0x3ffd6c76UL, 0x4a8165a0UL, 0x3c5fe87aUL,
  0x16c98398UL, 0x3ffd80e3UL, 0x8beddfe8UL, 0xbc911ec1UL, 0x71ff6075UL,
  0x3ffd955dUL, 0xbb9af6beUL, 0x3c9a052dUL, 0x03db3285UL, 0x3ffda9e6UL,
  0x696db532UL, 0x3c9c2300UL, 0xd63a8315UL, 0x3ffdbe7cUL, 0x926b8be4UL,
  0xbc9b76f1UL, 0xf301b460UL, 0x3ffdd321UL, 0x78f018c3UL, 0x3c92da57UL,
  0x641c0658UL, 0x3ffde7d5UL, 0x8e79ba8fUL, 0xbc9ca552UL, 0x337b9b5fUL,
  0x3ffdfc97UL, 0x4f184b5cUL, 0xbc91a5cdUL, 0x6b197d17UL, 0x3ffe1167UL,
  0xbd5c7f44UL, 0xbc72b529UL, 0x14f5a129UL, 0x3ffe2646UL, 0x817a1496UL,
  0xbc97b627UL, 0x3b16ee12UL, 0x3ffe3b33UL, 0x31fdc68bUL, 0xbc99f4a4UL,
  0xe78b3ff6UL, 0x3ffe502eUL, 0x80a9cc8fUL, 0x3c839e89UL, 0x24676d76UL,
  0x3ffe6539UL, 0x7522b735UL, 0xbc863ff8UL, 0xfbc74c83UL, 0x3ffe7a51UL,
  0xca0c8de2UL, 0x3c92d522UL, 0x77cdb740UL, 0x3ffe8f79UL, 0x80b054b1UL,
  0xbc910894UL, 0xa2a490daUL, 0x3ffea4afUL, 0x179c2893UL, 0xbc9e9c23UL,
  0x867cca6eUL, 0x3ffeb9f4UL, 0x2293e4f2UL, 0x3c94832fUL, 0x2d8e67f1UL,
  0x3ffecf48UL, 0xb411ad8cUL, 0xbc9c93f3UL, 0xa2188510UL, 0x3ffee4aaUL,
  0xa487568dUL, 0x3c91c68dUL, 0xee615a27UL, 0x3ffefa1bUL, 0x86a4b6b0UL,
  0x3c9dc7f4UL, 0x1cb6412aUL, 0x3fff0f9cUL, 0x65181d45UL, 0xbc932200UL,
  0x376bba97UL, 0x3fff252bUL, 0xbf0d8e43UL, 0x3c93a1a5UL, 0x48dd7274UL,
  0x3fff3ac9UL, 0x3ed837deUL, 0xbc795a5aUL, 0x5b6e4540UL, 0x3fff5076UL,
  0x2dd8a18bUL, 0x3c99d3e1UL, 0x798844f8UL, 0x3fff6632UL, 0x3539343eUL,
  0x3c9fa37bUL, 0xad9cbe14UL, 0x3fff7bfdUL, 0xd006350aUL, 0xbc9dbb12UL,
  0x02243c89UL, 0x3fff91d8UL, 0xa779f689UL, 0xbc612ea8UL, 0x819e90d8UL,
  0x3fffa7c1UL, 0xf3a5931eUL, 0x3c874853UL, 0x3692d514UL, 0x3fffbdbaUL,
  0x15098eb6UL, 0xbc796773UL, 0x2b8f71f1UL, 0x3fffd3c2UL, 0x966579e7UL,
  0x3c62eb74UL, 0x6b2a23d9UL, 0x3fffe9d9UL, 0x7442fde3UL, 0x3c74a603UL,
  0xe78a6731UL, 0x3f55d87fUL, 0xd704a0c0UL, 0x3fac6b08UL, 0x6fba4e77UL,
  0x3f83b2abUL, 0xff82c58fUL, 0x3fcebfbdUL, 0xfefa39efUL, 0x3fe62e42UL,
  0x00000000UL, 0x00000000UL, 0xfefa39efUL, 0x3fe62e42UL, 0xfefa39efUL,
  0xbfe62e42UL, 0xf8000000UL, 0xffffffffUL, 0xf8000000UL, 0xffffffffUL,
  0x00000000UL, 0x80000000UL, 0x00000000UL, 0x00000000UL

};

//registers,
// input: xmm0, xmm1
// scratch: xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
//          eax, edx, ecx, ebx

// Code generated by Intel C compiler for LIBM library

void MacroAssembler::fast_pow(XMMRegister xmm0, XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3, XMMRegister xmm4, XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7, Register eax, Register ecx, Register edx, Register tmp) {
  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label L_2TAG_PACKET_4_0_2, L_2TAG_PACKET_5_0_2, L_2TAG_PACKET_6_0_2, L_2TAG_PACKET_7_0_2;
  Label L_2TAG_PACKET_8_0_2, L_2TAG_PACKET_9_0_2, L_2TAG_PACKET_10_0_2, L_2TAG_PACKET_11_0_2;
  Label L_2TAG_PACKET_12_0_2, L_2TAG_PACKET_13_0_2, L_2TAG_PACKET_14_0_2, L_2TAG_PACKET_15_0_2;
  Label L_2TAG_PACKET_16_0_2, L_2TAG_PACKET_17_0_2, L_2TAG_PACKET_18_0_2, L_2TAG_PACKET_19_0_2;
  Label L_2TAG_PACKET_20_0_2, L_2TAG_PACKET_21_0_2, L_2TAG_PACKET_22_0_2, L_2TAG_PACKET_23_0_2;
  Label L_2TAG_PACKET_24_0_2, L_2TAG_PACKET_25_0_2, L_2TAG_PACKET_26_0_2, L_2TAG_PACKET_27_0_2;
  Label L_2TAG_PACKET_28_0_2, L_2TAG_PACKET_29_0_2, L_2TAG_PACKET_30_0_2, L_2TAG_PACKET_31_0_2;
  Label L_2TAG_PACKET_32_0_2, L_2TAG_PACKET_33_0_2, L_2TAG_PACKET_34_0_2, L_2TAG_PACKET_35_0_2;
  Label L_2TAG_PACKET_36_0_2, L_2TAG_PACKET_37_0_2, L_2TAG_PACKET_38_0_2, L_2TAG_PACKET_39_0_2;
  Label L_2TAG_PACKET_40_0_2, L_2TAG_PACKET_41_0_2, L_2TAG_PACKET_42_0_2, L_2TAG_PACKET_43_0_2;
  Label L_2TAG_PACKET_44_0_2, L_2TAG_PACKET_45_0_2, L_2TAG_PACKET_46_0_2, L_2TAG_PACKET_47_0_2;
  Label L_2TAG_PACKET_48_0_2, L_2TAG_PACKET_49_0_2, L_2TAG_PACKET_50_0_2, L_2TAG_PACKET_51_0_2;
  Label L_2TAG_PACKET_52_0_2, L_2TAG_PACKET_53_0_2, L_2TAG_PACKET_54_0_2, L_2TAG_PACKET_55_0_2;
  Label L_2TAG_PACKET_56_0_2, L_2TAG_PACKET_57_0_2, L_2TAG_PACKET_58_0_2, start;

  assert_different_registers(tmp, eax, ecx, edx);

  address static_const_table_pow = (address)_static_const_table_pow;

  bind(start);
  subl(rsp, 120);
  movl(Address(rsp, 64), tmp);
  lea(tmp, ExternalAddress(static_const_table_pow));
  movsd(xmm0, Address(rsp, 128));
  movsd(xmm1, Address(rsp, 136));
  xorpd(xmm2, xmm2);
  movl(eax, 16368);
  pinsrw(xmm2, eax, 3);
  movl(ecx, 1069088768);
  movdl(xmm7, ecx);
  movsd(Address(rsp, 16), xmm1);
  xorpd(xmm1, xmm1);
  movl(edx, 30704);
  pinsrw(xmm1, edx, 3);
  movsd(Address(rsp, 8), xmm0);
  movdqu(xmm3, xmm0);
  movl(edx, 8192);
  movdl(xmm4, edx);
  movdqu(xmm6, Address(tmp, 8240));
  pextrw(eax, xmm0, 3);
  por(xmm0, xmm2);
  psllq(xmm0, 5);
  movsd(xmm2, Address(tmp, 8256));
  psrlq(xmm0, 34);
  movl(edx, eax);
  andl(edx, 32752);
  subl(edx, 16368);
  movl(ecx, edx);
  sarl(edx, 31);
  addl(ecx, edx);
  xorl(ecx, edx);
  rcpss(xmm0, xmm0);
  psllq(xmm3, 12);
  addl(ecx, 16);
  bsrl(ecx, ecx);
  psrlq(xmm3, 12);
  movl(Address(rsp, 24), rsi);
  subl(eax, 16);
  cmpl(eax, 32736);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_0_0_2);
  movl(rsi, 0);

  bind(L_2TAG_PACKET_1_0_2);
  mulss(xmm0, xmm7);
  movl(edx, -1);
  subl(ecx, 4);
  shll(edx);
  movdl(xmm5, edx);
  por(xmm3, xmm1);
  subl(eax, 16351);
  cmpl(eax, 1);
  jcc(Assembler::belowEqual, L_2TAG_PACKET_2_0_2);
  paddd(xmm0, xmm4);
  psllq(xmm5, 32);
  movdl(edx, xmm0);
  psllq(xmm0, 29);
  pand(xmm5, xmm3);

  bind(L_2TAG_PACKET_3_0_2);
  pand(xmm0, xmm6);
  subsd(xmm3, xmm5);
  subl(eax, 1);
  sarl(eax, 4);
  cvtsi2sdl(xmm7, eax);
  mulpd(xmm5, xmm0);

  bind(L_2TAG_PACKET_4_0_2);
  mulsd(xmm3, xmm0);
  movdqu(xmm1, Address(tmp, 8272));
  subsd(xmm5, xmm2);
  movdqu(xmm4, Address(tmp, 8288));
  movl(ecx, eax);
  sarl(eax, 31);
  addl(ecx, eax);
  xorl(eax, ecx);
  addl(eax, 1);
  bsrl(eax, eax);
  unpcklpd(xmm5, xmm3);
  movdqu(xmm6, Address(tmp, 8304));
  addsd(xmm3, xmm5);
  andl(edx, 16760832);
  shrl(edx, 10);
  addpd(xmm5, Address(tmp, edx, Address::times_1, -3616));
  movdqu(xmm0, Address(tmp, 8320));
  pshufd(xmm2, xmm3, 68);
  mulsd(xmm3, xmm3);
  mulpd(xmm1, xmm2);
  mulpd(xmm4, xmm2);
  addsd(xmm5, xmm7);
  mulsd(xmm2, xmm3);
  addpd(xmm6, xmm1);
  mulsd(xmm3, xmm3);
  addpd(xmm0, xmm4);
  movsd(xmm1, Address(rsp, 16));
  movzwl(ecx, Address(rsp, 22));
  pshufd(xmm7, xmm5, 238);
  movsd(xmm4, Address(tmp, 8368));
  mulpd(xmm6, xmm2);
  pshufd(xmm3, xmm3, 68);
  mulpd(xmm0, xmm2);
  shll(eax, 4);
  subl(eax, 15872);
  andl(ecx, 32752);
  addl(eax, ecx);
  mulpd(xmm3, xmm6);
  cmpl(eax, 624);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_5_0_2);
  xorpd(xmm6, xmm6);
  movl(edx, 17080);
  pinsrw(xmm6, edx, 3);
  movdqu(xmm2, xmm1);
  pand(xmm4, xmm1);
  subsd(xmm1, xmm4);
  mulsd(xmm4, xmm5);
  addsd(xmm0, xmm7);
  mulsd(xmm1, xmm5);
  movdqu(xmm7, xmm6);
  addsd(xmm6, xmm4);
  addpd(xmm3, xmm0);
  movdl(edx, xmm6);
  subsd(xmm6, xmm7);
  pshufd(xmm0, xmm3, 238);
  subsd(xmm4, xmm6);
  addsd(xmm0, xmm3);
  movl(ecx, edx);
  andl(edx, 255);
  addl(edx, edx);
  movdqu(xmm5, Address(tmp, edx, Address::times_8, 8384));
  addsd(xmm4, xmm1);
  mulsd(xmm2, xmm0);
  movdqu(xmm7, Address(tmp, 12480));
  movdqu(xmm3, Address(tmp, 12496));
  shll(ecx, 12);
  xorl(ecx, rsi);
  andl(ecx, -1048576);
  movdl(xmm6, ecx);
  addsd(xmm2, xmm4);
  movsd(xmm1, Address(tmp, 12512));
  pshufd(xmm0, xmm2, 68);
  pshufd(xmm4, xmm2, 68);
  mulpd(xmm0, xmm0);
  movl(rsi, Address(rsp, 24));
  mulpd(xmm7, xmm4);
  pshufd(xmm6, xmm6, 17);
  mulsd(xmm1, xmm2);
  mulsd(xmm0, xmm0);
  paddd(xmm5, xmm6);
  addpd(xmm3, xmm7);
  mulsd(xmm1, xmm5);
  pshufd(xmm6, xmm5, 238);
  mulpd(xmm0, xmm3);
  addsd(xmm1, xmm6);
  pshufd(xmm3, xmm0, 238);
  mulsd(xmm0, xmm5);
  mulsd(xmm3, xmm5);
  addsd(xmm0, xmm1);
  addsd(xmm0, xmm3);
  addsd(xmm0, xmm5);
  movsd(Address(rsp, 0), xmm0);
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_7_0_2);
  movsd(xmm0, Address(rsp, 128));
  movsd(xmm1, Address(rsp, 136));
  mulsd(xmm0, xmm1);
  movsd(Address(rsp, 0), xmm0);
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_0_0_2);
  addl(eax, 16);
  movl(edx, 32752);
  andl(edx, eax);
  cmpl(edx, 32752);
  jcc(Assembler::equal, L_2TAG_PACKET_8_0_2);
  testl(eax, 32768);
  jcc(Assembler::notEqual, L_2TAG_PACKET_9_0_2);

  bind(L_2TAG_PACKET_10_0_2);
  movl(ecx, Address(rsp, 16));
  xorl(edx, edx);
  testl(ecx, ecx);
  movl(ecx, 1);
  cmovl(Assembler::notEqual, edx, ecx);
  orl(edx, Address(rsp, 20));
  cmpl(edx, 1072693248);
  jcc(Assembler::equal, L_2TAG_PACKET_7_0_2);
  movsd(xmm0, Address(rsp, 8));
  movsd(xmm3, Address(rsp, 8));
  movdl(edx, xmm3);
  psrlq(xmm3, 32);
  movdl(ecx, xmm3);
  orl(edx, ecx);
  cmpl(edx, 0);
  jcc(Assembler::equal, L_2TAG_PACKET_11_0_2);
  xorpd(xmm3, xmm3);
  movl(eax, 18416);
  pinsrw(xmm3, eax, 3);
  mulsd(xmm0, xmm3);
  xorpd(xmm2, xmm2);
  movl(eax, 16368);
  pinsrw(xmm2, eax, 3);
  movdqu(xmm3, xmm0);
  pextrw(eax, xmm0, 3);
  por(xmm0, xmm2);
  movl(ecx, 18416);
  psllq(xmm0, 5);
  movsd(xmm2, Address(tmp, 8256));
  psrlq(xmm0, 34);
  rcpss(xmm0, xmm0);
  psllq(xmm3, 12);
  movdqu(xmm6, Address(tmp, 8240));
  psrlq(xmm3, 12);
  mulss(xmm0, xmm7);
  movl(edx, -1024);
  movdl(xmm5, edx);
  por(xmm3, xmm1);
  paddd(xmm0, xmm4);
  psllq(xmm5, 32);
  movdl(edx, xmm0);
  psllq(xmm0, 29);
  pand(xmm5, xmm3);
  movl(rsi, 0);
  pand(xmm0, xmm6);
  subsd(xmm3, xmm5);
  andl(eax, 32752);
  subl(eax, 18416);
  sarl(eax, 4);
  cvtsi2sdl(xmm7, eax);
  mulpd(xmm5, xmm0);
  jmp(L_2TAG_PACKET_4_0_2);

  bind(L_2TAG_PACKET_12_0_2);
  movl(ecx, Address(rsp, 16));
  xorl(edx, edx);
  testl(ecx, ecx);
  movl(ecx, 1);
  cmovl(Assembler::notEqual, edx, ecx);
  orl(edx, Address(rsp, 20));
  cmpl(edx, 1072693248);
  jcc(Assembler::equal, L_2TAG_PACKET_7_0_2);
  movsd(xmm0, Address(rsp, 8));
  movsd(xmm3, Address(rsp, 8));
  movdl(edx, xmm3);
  psrlq(xmm3, 32);
  movdl(ecx, xmm3);
  orl(edx, ecx);
  cmpl(edx, 0);
  jcc(Assembler::equal, L_2TAG_PACKET_11_0_2);
  xorpd(xmm3, xmm3);
  movl(eax, 18416);
  pinsrw(xmm3, eax, 3);
  mulsd(xmm0, xmm3);
  xorpd(xmm2, xmm2);
  movl(eax, 16368);
  pinsrw(xmm2, eax, 3);
  movdqu(xmm3, xmm0);
  pextrw(eax, xmm0, 3);
  por(xmm0, xmm2);
  movl(ecx, 18416);
  psllq(xmm0, 5);
  movsd(xmm2, Address(tmp, 8256));
  psrlq(xmm0, 34);
  rcpss(xmm0, xmm0);
  psllq(xmm3, 12);
  movdqu(xmm6, Address(tmp, 8240));
  psrlq(xmm3, 12);
  mulss(xmm0, xmm7);
  movl(edx, -1024);
  movdl(xmm5, edx);
  por(xmm3, xmm1);
  paddd(xmm0, xmm4);
  psllq(xmm5, 32);
  movdl(edx, xmm0);
  psllq(xmm0, 29);
  pand(xmm5, xmm3);
  movl(rsi, INT_MIN);
  pand(xmm0, xmm6);
  subsd(xmm3, xmm5);
  andl(eax, 32752);
  subl(eax, 18416);
  sarl(eax, 4);
  cvtsi2sdl(xmm7, eax);
  mulpd(xmm5, xmm0);
  jmp(L_2TAG_PACKET_4_0_2);

  bind(L_2TAG_PACKET_5_0_2);
  cmpl(eax, 0);
  jcc(Assembler::less, L_2TAG_PACKET_13_0_2);
  cmpl(eax, 752);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_14_0_2);

  bind(L_2TAG_PACKET_15_0_2);
  addsd(xmm0, xmm7);
  movsd(xmm2, Address(tmp, 12544));
  addpd(xmm3, xmm0);
  xorpd(xmm6, xmm6);
  movl(eax, 17080);
  pinsrw(xmm6, eax, 3);
  pshufd(xmm0, xmm3, 238);
  addsd(xmm0, xmm3);
  movdqu(xmm3, xmm5);
  addsd(xmm5, xmm0);
  movdqu(xmm4, xmm2);
  subsd(xmm3, xmm5);
  movdqu(xmm7, xmm5);
  pand(xmm5, xmm2);
  movdqu(xmm2, xmm1);
  pand(xmm4, xmm1);
  subsd(xmm7, xmm5);
  addsd(xmm0, xmm3);
  subsd(xmm1, xmm4);
  mulsd(xmm4, xmm5);
  addsd(xmm0, xmm7);
  mulsd(xmm2, xmm0);
  movdqu(xmm7, xmm6);
  mulsd(xmm1, xmm5);
  addsd(xmm6, xmm4);
  movdl(eax, xmm6);
  subsd(xmm6, xmm7);
  addsd(xmm2, xmm1);
  movdqu(xmm7, Address(tmp, 12480));
  movdqu(xmm3, Address(tmp, 12496));
  subsd(xmm4, xmm6);
  pextrw(edx, xmm6, 3);
  movl(ecx, eax);
  andl(eax, 255);
  addl(eax, eax);
  movdqu(xmm5, Address(tmp, eax, Address::times_8, 8384));
  addsd(xmm2, xmm4);
  sarl(ecx, 8);
  movl(eax, ecx);
  sarl(ecx, 1);
  subl(eax, ecx);
  shll(ecx, 20);
  xorl(ecx, rsi);
  movdl(xmm6, ecx);
  movsd(xmm1, Address(tmp, 12512));
  andl(edx, 32767);
  cmpl(edx, 16529);
  jcc(Assembler::above, L_2TAG_PACKET_14_0_2);
  pshufd(xmm0, xmm2, 68);
  pshufd(xmm4, xmm2, 68);
  mulpd(xmm0, xmm0);
  mulpd(xmm7, xmm4);
  pshufd(xmm6, xmm6, 17);
  mulsd(xmm1, xmm2);
  mulsd(xmm0, xmm0);
  paddd(xmm5, xmm6);
  addpd(xmm3, xmm7);
  mulsd(xmm1, xmm5);
  pshufd(xmm6, xmm5, 238);
  mulpd(xmm0, xmm3);
  addsd(xmm1, xmm6);
  pshufd(xmm3, xmm0, 238);
  mulsd(xmm0, xmm5);
  mulsd(xmm3, xmm5);
  shll(eax, 4);
  xorpd(xmm4, xmm4);
  addl(eax, 16368);
  pinsrw(xmm4, eax, 3);
  addsd(xmm0, xmm1);
  movl(rsi, Address(rsp, 24));
  addsd(xmm0, xmm3);
  movdqu(xmm1, xmm0);
  addsd(xmm0, xmm5);
  mulsd(xmm0, xmm4);
  pextrw(eax, xmm0, 3);
  andl(eax, 32752);
  jcc(Assembler::equal, L_2TAG_PACKET_16_0_2);
  cmpl(eax, 32752);
  jcc(Assembler::equal, L_2TAG_PACKET_17_0_2);

  bind(L_2TAG_PACKET_18_0_2);
  movsd(Address(rsp, 0), xmm0);
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_8_0_2);
  movsd(xmm1, Address(rsp, 16));
  movsd(xmm0, Address(rsp, 8));
  movdqu(xmm2, xmm0);
  movdl(eax, xmm2);
  psrlq(xmm2, 20);
  movdl(edx, xmm2);
  orl(eax, edx);
  jcc(Assembler::equal, L_2TAG_PACKET_19_0_2);
  addsd(xmm0, xmm0);
  movdl(eax, xmm1);
  psrlq(xmm1, 32);
  movdl(edx, xmm1);
  movl(ecx, edx);
  addl(edx, edx);
  orl(eax, edx);
  jcc(Assembler::equal, L_2TAG_PACKET_20_0_2);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_20_0_2);
  xorpd(xmm0, xmm0);
  movl(eax, 16368);
  pinsrw(xmm0, eax, 3);
  movl(edx, 29);
  jmp(L_2TAG_PACKET_21_0_2);

  bind(L_2TAG_PACKET_22_0_2);
  movsd(xmm0, Address(rsp, 16));
  addpd(xmm0, xmm0);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_19_0_2);
  movdl(eax, xmm1);
  movdqu(xmm2, xmm1);
  psrlq(xmm1, 32);
  movdl(edx, xmm1);
  movl(ecx, edx);
  addl(edx, edx);
  orl(eax, edx);
  jcc(Assembler::equal, L_2TAG_PACKET_23_0_2);
  pextrw(eax, xmm2, 3);
  andl(eax, 32752);
  cmpl(eax, 32752);
  jcc(Assembler::notEqual, L_2TAG_PACKET_24_0_2);
  movdl(eax, xmm2);
  psrlq(xmm2, 20);
  movdl(edx, xmm2);
  orl(eax, edx);
  jcc(Assembler::notEqual, L_2TAG_PACKET_22_0_2);

  bind(L_2TAG_PACKET_24_0_2);
  pextrw(eax, xmm0, 3);
  testl(eax, 32768);
  jcc(Assembler::notEqual, L_2TAG_PACKET_25_0_2);
  testl(ecx, INT_MIN);
  jcc(Assembler::notEqual, L_2TAG_PACKET_26_0_2);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_27_0_2);
  movsd(xmm1, Address(rsp, 16));
  movdl(eax, xmm1);
  testl(eax, 1);
  jcc(Assembler::notEqual, L_2TAG_PACKET_28_0_2);
  testl(eax, 2);
  jcc(Assembler::notEqual, L_2TAG_PACKET_29_0_2);
  jmp(L_2TAG_PACKET_28_0_2);

  bind(L_2TAG_PACKET_25_0_2);
  shrl(ecx, 20);
  andl(ecx, 2047);
  cmpl(ecx, 1075);
  jcc(Assembler::above, L_2TAG_PACKET_28_0_2);
  jcc(Assembler::equal, L_2TAG_PACKET_30_0_2);
  cmpl(ecx, 1074);
  jcc(Assembler::above, L_2TAG_PACKET_27_0_2);
  cmpl(ecx, 1023);
  jcc(Assembler::below, L_2TAG_PACKET_28_0_2);
  movsd(xmm1, Address(rsp, 16));
  movl(eax, 17208);
  xorpd(xmm3, xmm3);
  pinsrw(xmm3, eax, 3);
  movdqu(xmm4, xmm3);
  addsd(xmm3, xmm1);
  subsd(xmm4, xmm3);
  addsd(xmm1, xmm4);
  pextrw(eax, xmm1, 3);
  andl(eax, 32752);
  jcc(Assembler::notEqual, L_2TAG_PACKET_28_0_2);
  movdl(eax, xmm3);
  andl(eax, 1);
  jcc(Assembler::equal, L_2TAG_PACKET_28_0_2);

  bind(L_2TAG_PACKET_29_0_2);
  movsd(xmm1, Address(rsp, 16));
  pextrw(eax, xmm1, 3);
  andl(eax, 32768);
  jcc(Assembler::equal, L_2TAG_PACKET_18_0_2);
  xorpd(xmm0, xmm0);
  movl(eax, 32768);
  pinsrw(xmm0, eax, 3);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_28_0_2);
  movsd(xmm1, Address(rsp, 16));
  pextrw(eax, xmm1, 3);
  andl(eax, 32768);
  jcc(Assembler::notEqual, L_2TAG_PACKET_26_0_2);

  bind(L_2TAG_PACKET_31_0_2);
  xorpd(xmm0, xmm0);
  movl(eax, 32752);
  pinsrw(xmm0, eax, 3);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_30_0_2);
  movsd(xmm1, Address(rsp, 16));
  movdl(eax, xmm1);
  andl(eax, 1);
  jcc(Assembler::equal, L_2TAG_PACKET_28_0_2);
  jmp(L_2TAG_PACKET_29_0_2);

  bind(L_2TAG_PACKET_32_0_2);
  movdl(eax, xmm1);
  psrlq(xmm1, 20);
  movdl(edx, xmm1);
  orl(eax, edx);
  jcc(Assembler::equal, L_2TAG_PACKET_33_0_2);
  movsd(xmm0, Address(rsp, 16));
  addsd(xmm0, xmm0);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_33_0_2);
  movsd(xmm0, Address(rsp, 8));
  pextrw(eax, xmm0, 3);
  cmpl(eax, 49136);
  jcc(Assembler::notEqual, L_2TAG_PACKET_34_0_2);
  movdl(ecx, xmm0);
  psrlq(xmm0, 20);
  movdl(edx, xmm0);
  orl(ecx, edx);
  jcc(Assembler::notEqual, L_2TAG_PACKET_34_0_2);
  xorpd(xmm0, xmm0);
  movl(eax, 32760);
  pinsrw(xmm0, eax, 3);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_34_0_2);
  movsd(xmm1, Address(rsp, 16));
  andl(eax, 32752);
  subl(eax, 16368);
  pextrw(edx, xmm1, 3);
  xorpd(xmm0, xmm0);
  xorl(eax, edx);
  andl(eax, 32768);
  jcc(Assembler::notEqual, L_2TAG_PACKET_18_0_2);
  movl(ecx, 32752);
  pinsrw(xmm0, ecx, 3);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_35_0_2);
  movdl(eax, xmm1);
  cmpl(edx, 17184);
  jcc(Assembler::above, L_2TAG_PACKET_36_0_2);
  testl(eax, 1);
  jcc(Assembler::notEqual, L_2TAG_PACKET_37_0_2);
  testl(eax, 2);
  jcc(Assembler::equal, L_2TAG_PACKET_38_0_2);
  jmp(L_2TAG_PACKET_39_0_2);

  bind(L_2TAG_PACKET_36_0_2);
  testl(eax, 1);
  jcc(Assembler::equal, L_2TAG_PACKET_38_0_2);
  jmp(L_2TAG_PACKET_39_0_2);

  bind(L_2TAG_PACKET_9_0_2);
  movsd(xmm2, Address(rsp, 8));
  movdl(eax, xmm2);
  psrlq(xmm2, 31);
  movdl(ecx, xmm2);
  orl(eax, ecx);
  jcc(Assembler::equal, L_2TAG_PACKET_11_0_2);
  movsd(xmm1, Address(rsp, 16));
  pextrw(edx, xmm1, 3);
  movdl(eax, xmm1);
  movdqu(xmm2, xmm1);
  psrlq(xmm2, 32);
  movdl(ecx, xmm2);
  addl(ecx, ecx);
  orl(ecx, eax);
  jcc(Assembler::equal, L_2TAG_PACKET_40_0_2);
  andl(edx, 32752);
  cmpl(edx, 32752);
  jcc(Assembler::equal, L_2TAG_PACKET_32_0_2);
  cmpl(edx, 17200);
  jcc(Assembler::above, L_2TAG_PACKET_38_0_2);
  cmpl(edx, 17184);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_35_0_2);
  cmpl(edx, 16368);
  jcc(Assembler::below, L_2TAG_PACKET_37_0_2);
  movl(eax, 17208);
  xorpd(xmm2, xmm2);
  pinsrw(xmm2, eax, 3);
  movdqu(xmm4, xmm2);
  addsd(xmm2, xmm1);
  subsd(xmm4, xmm2);
  addsd(xmm1, xmm4);
  pextrw(eax, xmm1, 3);
  andl(eax, 32767);
  jcc(Assembler::notEqual, L_2TAG_PACKET_37_0_2);
  movdl(eax, xmm2);
  andl(eax, 1);
  jcc(Assembler::equal, L_2TAG_PACKET_38_0_2);

  bind(L_2TAG_PACKET_39_0_2);
  xorpd(xmm1, xmm1);
  movl(edx, 30704);
  pinsrw(xmm1, edx, 3);
  movsd(xmm2, Address(tmp, 8256));
  movsd(xmm4, Address(rsp, 8));
  pextrw(eax, xmm4, 3);
  movl(edx, 8192);
  movdl(xmm4, edx);
  andl(eax, 32767);
  subl(eax, 16);
  jcc(Assembler::less, L_2TAG_PACKET_12_0_2);
  movl(edx, eax);
  andl(edx, 32752);
  subl(edx, 16368);
  movl(ecx, edx);
  sarl(edx, 31);
  addl(ecx, edx);
  xorl(ecx, edx);
  addl(ecx, 16);
  bsrl(ecx, ecx);
  movl(rsi, INT_MIN);
  jmp(L_2TAG_PACKET_1_0_2);

  bind(L_2TAG_PACKET_37_0_2);
  xorpd(xmm1, xmm1);
  movl(eax, 32752);
  pinsrw(xmm1, eax, 3);
  xorpd(xmm0, xmm0);
  mulsd(xmm0, xmm1);
  movl(edx, 28);
  jmp(L_2TAG_PACKET_21_0_2);

  bind(L_2TAG_PACKET_38_0_2);
  xorpd(xmm1, xmm1);
  movl(edx, 30704);
  pinsrw(xmm1, edx, 3);
  movsd(xmm2, Address(tmp, 8256));
  movsd(xmm4, Address(rsp, 8));
  pextrw(eax, xmm4, 3);
  movl(edx, 8192);
  movdl(xmm4, edx);
  andl(eax, 32767);
  subl(eax, 16);
  jcc(Assembler::less, L_2TAG_PACKET_10_0_2);
  movl(edx, eax);
  andl(edx, 32752);
  subl(edx, 16368);
  movl(ecx, edx);
  sarl(edx, 31);
  addl(ecx, edx);
  xorl(ecx, edx);
  addl(ecx, 16);
  bsrl(ecx, ecx);
  movl(rsi, 0);
  jmp(L_2TAG_PACKET_1_0_2);

  bind(L_2TAG_PACKET_23_0_2);
  xorpd(xmm0, xmm0);
  movl(eax, 16368);
  pinsrw(xmm0, eax, 3);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_26_0_2);
  xorpd(xmm0, xmm0);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_13_0_2);
  addl(eax, 384);
  cmpl(eax, 0);
  jcc(Assembler::less, L_2TAG_PACKET_41_0_2);
  mulsd(xmm5, xmm1);
  addsd(xmm0, xmm7);
  shrl(rsi, 31);
  addpd(xmm3, xmm0);
  pshufd(xmm0, xmm3, 238);
  addsd(xmm3, xmm0);
  movsd(xmm4, Address(tmp, rsi, Address::times_8, 12528));
  mulsd(xmm1, xmm3);
  xorpd(xmm0, xmm0);
  movl(eax, 16368);
  shll(rsi, 15);
  orl(eax, rsi);
  pinsrw(xmm0, eax, 3);
  addsd(xmm5, xmm1);
  movl(rsi, Address(rsp, 24));
  mulsd(xmm5, xmm4);
  addsd(xmm0, xmm5);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_41_0_2);
  movl(rsi, Address(rsp, 24));
  xorpd(xmm0, xmm0);
  movl(eax, 16368);
  pinsrw(xmm0, eax, 3);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_40_0_2);
  xorpd(xmm0, xmm0);
  movl(eax, 16368);
  pinsrw(xmm0, eax, 3);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_42_0_2);
  xorpd(xmm0, xmm0);
  movl(eax, 16368);
  pinsrw(xmm0, eax, 3);
  movl(edx, 26);
  jmp(L_2TAG_PACKET_21_0_2);

  bind(L_2TAG_PACKET_11_0_2);
  movsd(xmm1, Address(rsp, 16));
  movdqu(xmm2, xmm1);
  pextrw(eax, xmm1, 3);
  andl(eax, 32752);
  cmpl(eax, 32752);
  jcc(Assembler::notEqual, L_2TAG_PACKET_43_0_2);
  movdl(eax, xmm2);
  psrlq(xmm2, 20);
  movdl(edx, xmm2);
  orl(eax, edx);
  jcc(Assembler::notEqual, L_2TAG_PACKET_22_0_2);

  bind(L_2TAG_PACKET_43_0_2);
  movdl(eax, xmm1);
  psrlq(xmm1, 32);
  movdl(edx, xmm1);
  movl(ecx, edx);
  addl(edx, edx);
  orl(eax, edx);
  jcc(Assembler::equal, L_2TAG_PACKET_42_0_2);
  shrl(edx, 21);
  cmpl(edx, 1075);
  jcc(Assembler::above, L_2TAG_PACKET_44_0_2);
  jcc(Assembler::equal, L_2TAG_PACKET_45_0_2);
  cmpl(edx, 1023);
  jcc(Assembler::below, L_2TAG_PACKET_44_0_2);
  movsd(xmm1, Address(rsp, 16));
  movl(eax, 17208);
  xorpd(xmm3, xmm3);
  pinsrw(xmm3, eax, 3);
  movdqu(xmm4, xmm3);
  addsd(xmm3, xmm1);
  subsd(xmm4, xmm3);
  addsd(xmm1, xmm4);
  pextrw(eax, xmm1, 3);
  andl(eax, 32752);
  jcc(Assembler::notEqual, L_2TAG_PACKET_44_0_2);
  movdl(eax, xmm3);
  andl(eax, 1);
  jcc(Assembler::equal, L_2TAG_PACKET_44_0_2);

  bind(L_2TAG_PACKET_46_0_2);
  movsd(xmm0, Address(rsp, 8));
  testl(ecx, INT_MIN);
  jcc(Assembler::notEqual, L_2TAG_PACKET_47_0_2);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_45_0_2);
  movsd(xmm1, Address(rsp, 16));
  movdl(eax, xmm1);
  testl(eax, 1);
  jcc(Assembler::notEqual, L_2TAG_PACKET_46_0_2);

  bind(L_2TAG_PACKET_44_0_2);
  testl(ecx, INT_MIN);
  jcc(Assembler::equal, L_2TAG_PACKET_26_0_2);
  xorpd(xmm0, xmm0);

  bind(L_2TAG_PACKET_47_0_2);
  movl(eax, 16368);
  xorpd(xmm1, xmm1);
  pinsrw(xmm1, eax, 3);
  divsd(xmm1, xmm0);
  movdqu(xmm0, xmm1);
  movl(edx, 27);
  jmp(L_2TAG_PACKET_21_0_2);

  bind(L_2TAG_PACKET_14_0_2);
  movsd(xmm2, Address(rsp, 8));
  movsd(xmm6, Address(rsp, 16));
  pextrw(eax, xmm2, 3);
  pextrw(edx, xmm6, 3);
  movl(ecx, 32752);
  andl(ecx, edx);
  cmpl(ecx, 32752);
  jcc(Assembler::equal, L_2TAG_PACKET_48_0_2);
  andl(eax, 32752);
  subl(eax, 16368);
  xorl(edx, eax);
  testl(edx, 32768);
  jcc(Assembler::notEqual, L_2TAG_PACKET_49_0_2);

  bind(L_2TAG_PACKET_50_0_2);
  movl(eax, 32736);
  pinsrw(xmm0, eax, 3);
  shrl(rsi, 16);
  orl(eax, rsi);
  pinsrw(xmm1, eax, 3);
  movl(rsi, Address(rsp, 24));
  mulsd(xmm0, xmm1);

  bind(L_2TAG_PACKET_17_0_2);
  movl(edx, 24);

  bind(L_2TAG_PACKET_21_0_2);
  movsd(Address(rsp, 0), xmm0);
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_49_0_2);
  movl(eax, 16);
  pinsrw(xmm0, eax, 3);
  mulsd(xmm0, xmm0);
  testl(rsi, INT_MIN);
  jcc(Assembler::equal, L_2TAG_PACKET_51_0_2);
  movsd(xmm2, Address(tmp, 12560));
  xorpd(xmm0, xmm2);

  bind(L_2TAG_PACKET_51_0_2);
  movl(rsi, Address(rsp, 24));
  movl(edx, 25);
  jmp(L_2TAG_PACKET_21_0_2);

  bind(L_2TAG_PACKET_16_0_2);
  pextrw(ecx, xmm5, 3);
  pextrw(edx, xmm4, 3);
  movl(eax, -1);
  andl(ecx, 32752);
  subl(ecx, 16368);
  andl(edx, 32752);
  addl(edx, ecx);
  movl(ecx, -31);
  sarl(edx, 4);
  subl(ecx, edx);
  jcc(Assembler::lessEqual, L_2TAG_PACKET_52_0_2);
  cmpl(ecx, 20);
  jcc(Assembler::above, L_2TAG_PACKET_53_0_2);
  shll(eax);

  bind(L_2TAG_PACKET_52_0_2);
  movdl(xmm0, eax);
  psllq(xmm0, 32);
  pand(xmm0, xmm5);
  subsd(xmm5, xmm0);
  addsd(xmm5, xmm1);
  mulsd(xmm0, xmm4);
  mulsd(xmm5, xmm4);
  addsd(xmm0, xmm5);

  bind(L_2TAG_PACKET_53_0_2);
  movl(edx, 25);
  jmp(L_2TAG_PACKET_21_0_2);

  bind(L_2TAG_PACKET_2_0_2);
  movzwl(ecx, Address(rsp, 22));
  movl(edx, INT_MIN);
  movdl(xmm1, edx);
  xorpd(xmm7, xmm7);
  paddd(xmm0, xmm4);
  psllq(xmm5, 32);
  movdl(edx, xmm0);
  psllq(xmm0, 29);
  paddq(xmm1, xmm3);
  pand(xmm5, xmm1);
  andl(ecx, 32752);
  cmpl(ecx, 16560);
  jcc(Assembler::below, L_2TAG_PACKET_3_0_2);
  pand(xmm0, xmm6);
  subsd(xmm3, xmm5);
  addl(eax, 16351);
  shrl(eax, 4);
  subl(eax, 1022);
  cvtsi2sdl(xmm7, eax);
  mulpd(xmm5, xmm0);
  movsd(xmm4, Address(tmp, 0));
  mulsd(xmm3, xmm0);
  movsd(xmm6, Address(tmp, 0));
  subsd(xmm5, xmm2);
  movsd(xmm1, Address(tmp, 8));
  pshufd(xmm2, xmm3, 68);
  unpcklpd(xmm5, xmm3);
  addsd(xmm3, xmm5);
  movsd(xmm0, Address(tmp, 8));
  andl(edx, 16760832);
  shrl(edx, 10);
  addpd(xmm7, Address(tmp, edx, Address::times_1, -3616));
  mulsd(xmm4, xmm5);
  mulsd(xmm0, xmm5);
  mulsd(xmm6, xmm2);
  mulsd(xmm1, xmm2);
  movdqu(xmm2, xmm5);
  mulsd(xmm4, xmm5);
  addsd(xmm5, xmm0);
  movdqu(xmm0, xmm7);
  addsd(xmm2, xmm3);
  addsd(xmm7, xmm5);
  mulsd(xmm6, xmm2);
  subsd(xmm0, xmm7);
  movdqu(xmm2, xmm7);
  addsd(xmm7, xmm4);
  addsd(xmm0, xmm5);
  subsd(xmm2, xmm7);
  addsd(xmm4, xmm2);
  pshufd(xmm2, xmm5, 238);
  movdqu(xmm5, xmm7);
  addsd(xmm7, xmm2);
  addsd(xmm4, xmm0);
  movdqu(xmm0, Address(tmp, 8272));
  subsd(xmm5, xmm7);
  addsd(xmm6, xmm4);
  movdqu(xmm4, xmm7);
  addsd(xmm5, xmm2);
  addsd(xmm7, xmm1);
  movdqu(xmm2, Address(tmp, 8336));
  subsd(xmm4, xmm7);
  addsd(xmm6, xmm5);
  addsd(xmm4, xmm1);
  pshufd(xmm5, xmm7, 238);
  movdqu(xmm1, xmm7);
  addsd(xmm7, xmm5);
  subsd(xmm1, xmm7);
  addsd(xmm1, xmm5);
  movdqu(xmm5, Address(tmp, 8352));
  pshufd(xmm3, xmm3, 68);
  addsd(xmm6, xmm4);
  addsd(xmm6, xmm1);
  movdqu(xmm1, Address(tmp, 8304));
  mulpd(xmm0, xmm3);
  mulpd(xmm2, xmm3);
  pshufd(xmm4, xmm3, 68);
  mulpd(xmm3, xmm3);
  addpd(xmm0, xmm1);
  addpd(xmm5, xmm2);
  mulsd(xmm4, xmm3);
  movsd(xmm2, Address(tmp, 16));
  mulpd(xmm3, xmm3);
  movsd(xmm1, Address(rsp, 16));
  movzwl(ecx, Address(rsp, 22));
  mulpd(xmm0, xmm4);
  pextrw(eax, xmm7, 3);
  mulpd(xmm5, xmm4);
  mulpd(xmm0, xmm3);
  movsd(xmm4, Address(tmp, 8376));
  pand(xmm2, xmm7);
  addsd(xmm5, xmm6);
  subsd(xmm7, xmm2);
  addpd(xmm5, xmm0);
  andl(eax, 32752);
  subl(eax, 16368);
  andl(ecx, 32752);
  cmpl(ecx, 32752);
  jcc(Assembler::equal, L_2TAG_PACKET_48_0_2);
  addl(ecx, eax);
  cmpl(ecx, 16576);
  jcc(Assembler::aboveEqual, L_2TAG_PACKET_54_0_2);
  pshufd(xmm0, xmm5, 238);
  pand(xmm4, xmm1);
  movdqu(xmm3, xmm1);
  addsd(xmm5, xmm0);
  subsd(xmm1, xmm4);
  xorpd(xmm6, xmm6);
  movl(edx, 17080);
  pinsrw(xmm6, edx, 3);
  addsd(xmm7, xmm5);
  mulsd(xmm4, xmm2);
  mulsd(xmm1, xmm2);
  movdqu(xmm5, xmm6);
  mulsd(xmm3, xmm7);
  addsd(xmm6, xmm4);
  addsd(xmm1, xmm3);
  movdqu(xmm7, Address(tmp, 12480));
  movdl(edx, xmm6);
  subsd(xmm6, xmm5);
  movdqu(xmm3, Address(tmp, 12496));
  movsd(xmm2, Address(tmp, 12512));
  subsd(xmm4, xmm6);
  movl(ecx, edx);
  andl(edx, 255);
  addl(edx, edx);
  movdqu(xmm5, Address(tmp, edx, Address::times_8, 8384));
  addsd(xmm4, xmm1);
  pextrw(edx, xmm6, 3);
  shrl(ecx, 8);
  movl(eax, ecx);
  shrl(ecx, 1);
  subl(eax, ecx);
  shll(ecx, 20);
  movdl(xmm6, ecx);
  pshufd(xmm0, xmm4, 68);
  pshufd(xmm1, xmm4, 68);
  mulpd(xmm0, xmm0);
  mulpd(xmm7, xmm1);
  pshufd(xmm6, xmm6, 17);
  mulsd(xmm2, xmm4);
  andl(edx, 32767);
  cmpl(edx, 16529);
  jcc(Assembler::above, L_2TAG_PACKET_14_0_2);
  mulsd(xmm0, xmm0);
  paddd(xmm5, xmm6);
  addpd(xmm3, xmm7);
  mulsd(xmm2, xmm5);
  pshufd(xmm6, xmm5, 238);
  mulpd(xmm0, xmm3);
  addsd(xmm2, xmm6);
  pshufd(xmm3, xmm0, 238);
  addl(eax, 1023);
  shll(eax, 20);
  orl(eax, rsi);
  movdl(xmm4, eax);
  mulsd(xmm0, xmm5);
  mulsd(xmm3, xmm5);
  addsd(xmm0, xmm2);
  psllq(xmm4, 32);
  addsd(xmm0, xmm3);
  movdqu(xmm1, xmm0);
  addsd(xmm0, xmm5);
  movl(rsi, Address(rsp, 24));
  mulsd(xmm0, xmm4);
  pextrw(eax, xmm0, 3);
  andl(eax, 32752);
  jcc(Assembler::equal, L_2TAG_PACKET_16_0_2);
  cmpl(eax, 32752);
  jcc(Assembler::equal, L_2TAG_PACKET_17_0_2);

  bind(L_2TAG_PACKET_55_0_2);
  movsd(Address(rsp, 0), xmm0);
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_6_0_2);

  bind(L_2TAG_PACKET_48_0_2);
  movl(rsi, Address(rsp, 24));

  bind(L_2TAG_PACKET_56_0_2);
  movsd(xmm0, Address(rsp, 8));
  movsd(xmm1, Address(rsp, 16));
  addsd(xmm1, xmm1);
  xorpd(xmm2, xmm2);
  movl(eax, 49136);
  pinsrw(xmm2, eax, 3);
  addsd(xmm2, xmm0);
  pextrw(eax, xmm2, 3);
  cmpl(eax, 0);
  jcc(Assembler::notEqual, L_2TAG_PACKET_57_0_2);
  xorpd(xmm0, xmm0);
  movl(eax, 32760);
  pinsrw(xmm0, eax, 3);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_57_0_2);
  movdl(edx, xmm1);
  movdqu(xmm3, xmm1);
  psrlq(xmm3, 20);
  movdl(ecx, xmm3);
  orl(ecx, edx);
  jcc(Assembler::equal, L_2TAG_PACKET_58_0_2);
  addsd(xmm1, xmm1);
  movdqu(xmm0, xmm1);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_58_0_2);
  pextrw(eax, xmm0, 3);
  andl(eax, 32752);
  pextrw(edx, xmm1, 3);
  xorpd(xmm0, xmm0);
  subl(eax, 16368);
  xorl(eax, edx);
  testl(eax, 32768);
  jcc(Assembler::notEqual, L_2TAG_PACKET_18_0_2);
  movl(edx, 32752);
  pinsrw(xmm0, edx, 3);
  jmp(L_2TAG_PACKET_18_0_2);

  bind(L_2TAG_PACKET_54_0_2);
  pextrw(eax, xmm1, 3);
  pextrw(ecx, xmm2, 3);
  xorl(eax, ecx);
  testl(eax, 32768);
  jcc(Assembler::equal, L_2TAG_PACKET_50_0_2);
  jmp(L_2TAG_PACKET_49_0_2);

  bind(L_2TAG_PACKET_6_0_2);
  movl(tmp, Address(rsp, 64));

}

/******************************************************************************/
//                     ALGORITHM DESCRIPTION - SIN()
//                     ---------------------
//
//     1. RANGE REDUCTION
//
//     We perform an initial range reduction from X to r with
//
//          X =~= N * pi/32 + r
//
//     so that |r| <= pi/64 + epsilon. We restrict inputs to those
//     where |N| <= 932560. Beyond this, the range reduction is
//     insufficiently accurate. For extremely small inputs,
//     denormalization can occur internally, impacting performance.
//     This means that the main path is actually only taken for
//     2^-252 <= |X| < 90112.
//
//     To avoid branches, we perform the range reduction to full
//     accuracy each time.
//
//          X - N * (P_1 + P_2 + P_3)
//
//     where P_1 and P_2 are 32-bit numbers (so multiplication by N
//     is exact) and P_3 is a 53-bit number. Together, these
//     approximate pi well enough for all cases in the restricted
//     range.
//
//     The main reduction sequence is:
//
//             y = 32/pi * x
//             N = integer(y)
//     (computed by adding and subtracting off SHIFTER)
//
//             m_1 = N * P_1
//             m_2 = N * P_2
//             r_1 = x - m_1
//             r = r_1 - m_2
//     (this r can be used for most of the calculation)
//
//             c_1 = r_1 - r
//             m_3 = N * P_3
//             c_2 = c_1 - m_2
//             c = c_2 - m_3
//
//     2. MAIN ALGORITHM
//
//     The algorithm uses a table lookup based on B = M * pi / 32
//     where M = N mod 64. The stored values are:
//       sigma             closest power of 2 to cos(B)
//       C_hl              53-bit cos(B) - sigma
//       S_hi + S_lo       2 * 53-bit sin(B)
//
//     The computation is organized as follows:
//
//          sin(B + r + c) = [sin(B) + sigma * r] +
//                           r * (cos(B) - sigma) +
//                           sin(B) * [cos(r + c) - 1] +
//                           cos(B) * [sin(r + c) - r]
//
//     which is approximately:
//
//          [S_hi + sigma * r] +
//          C_hl * r +
//          S_lo + S_hi * [(cos(r) - 1) - r * c] +
//          (C_hl + sigma) * [(sin(r) - r) + c]
//
//     and this is what is actually computed. We separate this sum
//     into four parts:
//
//          hi + med + pols + corr
//
//     where
//
//          hi       = S_hi + sigma r
//          med      = C_hl * r
//          pols     = S_hi * (cos(r) - 1) + (C_hl + sigma) * (sin(r) - r)
//          corr     = S_lo + c * ((C_hl + sigma) - S_hi * r)
//
//     3. POLYNOMIAL
//
//     The polynomial S_hi * (cos(r) - 1) + (C_hl + sigma) *
//     (sin(r) - r) can be rearranged freely, since it is quite
//     small, so we exploit parallelism to the fullest.
//
//          psc4       =   SC_4 * r_1
//          msc4       =   psc4 * r
//          r2         =   r * r
//          msc2       =   SC_2 * r2
//          r4         =   r2 * r2
//          psc3       =   SC_3 + msc4
//          psc1       =   SC_1 + msc2
//          msc3       =   r4 * psc3
//          sincospols =   psc1 + msc3
//          pols       =   sincospols *
//                         <S_hi * r^2 | (C_hl + sigma) * r^3>
//
//     4. CORRECTION TERM
//
//     This is where the "c" component of the range reduction is
//     taken into account; recall that just "r" is used for most of
//     the calculation.
//
//          -c   = m_3 - c_2
//          -d   = S_hi * r - (C_hl + sigma)
//          corr = -c * -d + S_lo
//
//     5. COMPENSATED SUMMATIONS
//
//     The two successive compensated summations add up the high
//     and medium parts, leaving just the low parts to add up at
//     the end.
//
//          rs        =  sigma * r
//          res_int   =  S_hi + rs
//          k_0       =  S_hi - res_int
//          k_2       =  k_0 + rs
//          med       =  C_hl * r
//          res_hi    =  res_int + med
//          k_1       =  res_int - res_hi
//          k_3       =  k_1 + med
//
//     6. FINAL SUMMATION
//
//     We now add up all the small parts:
//
//          res_lo = pols(hi) + pols(lo) + corr + k_1 + k_3
//
//     Now the overall result is just:
//
//          res_hi + res_lo
//
//     7. SMALL ARGUMENTS
//
//     If |x| < SNN (SNN meaning the smallest normal number), we
//     simply perform 0.1111111 cdots 1111 * x. For SNN <= |x|, we
//     do 2^-55 * (2^55 * x - x).
//
// Special cases:
//  sin(NaN) = quiet NaN, and raise invalid exception
//  sin(INF) = NaN and raise invalid exception
//  sin(+/-0) = +/-0
//
/******************************************************************************/

ALIGNED_(8) juint _zero_none[] =
{
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0xbff00000UL
};

ALIGNED_(4) juint __4onpi_d[] =
{
    0x6dc9c883UL, 0x3ff45f30UL
};

ALIGNED_(4) juint _TWO_32H[] =
{
    0x00000000UL, 0x41f80000UL
};

ALIGNED_(4) juint _pi04_3d[] =
{
    0x54442d00UL, 0x3fe921fbUL, 0x98cc5180UL, 0x3ce84698UL, 0xcbb5bf6cUL,
    0xb9dfc8f8UL
};

ALIGNED_(4) juint _pi04_5d[] =
{
    0x54400000UL, 0x3fe921fbUL, 0x1a600000UL, 0x3dc0b461UL, 0x2e000000UL,
    0x3b93198aUL, 0x25200000UL, 0x396b839aUL, 0x533e63a0UL, 0x37027044UL
};

ALIGNED_(4) juint _SCALE[] =
{
    0x00000000UL, 0x32600000UL
};

ALIGNED_(4) juint _zeros[] =
{
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x80000000UL
};

ALIGNED_(4) juint _pi04_2d[] =
{
    0x54400000UL, 0x3fe921fbUL, 0x1a626331UL, 0x3dc0b461UL
};

ALIGNED_(4) juint _TWO_12H[] =
{
    0x00000000UL, 0x40b80000UL
};

ALIGNED_(2) jushort __4onpi_31l[] =
{
    0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x836e, 0xa2f9,
    0x40d8, 0x0000, 0x0000, 0x0000, 0x2a50, 0x9c88, 0x40b7, 0x0000, 0x0000, 0x0000,
    0xabe8, 0xfe13, 0x4099, 0x0000, 0x0000, 0x0000, 0x6ee0, 0xfa9a, 0x4079, 0x0000,
    0x0000, 0x0000, 0x9580, 0xdb62, 0x4058, 0x0000, 0x0000, 0x0000, 0x1c82, 0xc9e2,
    0x403d, 0x0000, 0x0000, 0x0000, 0xb1c0, 0xff28, 0x4019, 0x0000, 0x0000, 0x0000,
    0xef14, 0xaf7a, 0x3ffe, 0x0000, 0x0000, 0x0000, 0x48dc, 0xc36e, 0x3fdf, 0x0000,
    0x0000, 0x0000, 0x3740, 0xe909, 0x3fbe, 0x0000, 0x0000, 0x0000, 0x924a, 0xb801,
    0x3fa2, 0x0000, 0x0000, 0x0000, 0x3a32, 0xdd41, 0x3f83, 0x0000, 0x0000, 0x0000,
    0x8778, 0x873f, 0x3f62, 0x0000, 0x0000, 0x0000, 0x1298, 0xb1cb, 0x3f44, 0x0000,
    0x0000, 0x0000, 0xa208, 0x9cfb, 0x3f26, 0x0000, 0x0000, 0x0000, 0xbaec, 0xd7d4,
    0x3f06, 0x0000, 0x0000, 0x0000, 0xd338, 0x8909, 0x3ee7, 0x0000, 0x0000, 0x0000,
    0x68b8, 0xe04d, 0x3ec7, 0x0000, 0x0000, 0x0000, 0x4e64, 0xdf90, 0x3eaa, 0x0000,
    0x0000, 0x0000, 0xc1a8, 0xeb1c, 0x3e89, 0x0000, 0x0000, 0x0000, 0x2720, 0xce7d,
    0x3e6a, 0x0000, 0x0000, 0x0000, 0x77b8, 0x8bf1, 0x3e4b, 0x0000, 0x0000, 0x0000,
    0xec7e, 0xe4a0, 0x3e2e, 0x0000, 0x0000, 0x0000, 0xffbc, 0xf12f, 0x3e0f, 0x0000,
    0x0000, 0x0000, 0xfdc0, 0xb301, 0x3deb, 0x0000, 0x0000, 0x0000, 0xc5ac, 0x9788,
    0x3dd1, 0x0000, 0x0000, 0x0000, 0x47da, 0x829b, 0x3db2, 0x0000, 0x0000, 0x0000,
    0xd9e4, 0xa6cf, 0x3d93, 0x0000, 0x0000, 0x0000, 0x36e8, 0xf961, 0x3d73, 0x0000,
    0x0000, 0x0000, 0xf668, 0xf463, 0x3d54, 0x0000, 0x0000, 0x0000, 0x5168, 0xf2ff,
    0x3d35, 0x0000, 0x0000, 0x0000, 0x758e, 0xea4f, 0x3d17, 0x0000, 0x0000, 0x0000,
    0xf17a, 0xebe5, 0x3cf8, 0x0000, 0x0000, 0x0000, 0x9cfa, 0x9e83, 0x3cd9, 0x0000,
    0x0000, 0x0000, 0xa4ba, 0xe294, 0x3cba, 0x0000, 0x0000, 0x0000, 0xd7ec, 0x9afe,
    0x3c9a, 0x0000, 0x0000, 0x0000, 0xae80, 0x8fc6, 0x3c79, 0x0000, 0x0000, 0x0000,
    0x3304, 0x8560, 0x3c5c, 0x0000, 0x0000, 0x0000, 0x6d70, 0xdf8f, 0x3c3b, 0x0000,
    0x0000, 0x0000, 0x3ef0, 0xafc3, 0x3c1e, 0x0000, 0x0000, 0x0000, 0xd0d8, 0x826b,
    0x3bfe, 0x0000, 0x0000, 0x0000, 0x1c80, 0xed4f, 0x3bdd, 0x0000, 0x0000, 0x0000,
    0x730c, 0xb0af, 0x3bc1, 0x0000, 0x0000, 0x0000, 0x6660, 0xc219, 0x3ba2, 0x0000,
    0x0000, 0x0000, 0x940c, 0xabe2, 0x3b83, 0x0000, 0x0000, 0x0000, 0xdffc, 0x8408,
    0x3b64, 0x0000, 0x0000, 0x0000, 0x6b98, 0xc402, 0x3b45, 0x0000, 0x0000, 0x0000,
    0x1818, 0x9cc4, 0x3b26, 0x0000, 0x0000, 0x0000, 0x5390, 0xaab6, 0x3b05, 0x0000,
    0x0000, 0x0000, 0xb070, 0xd464, 0x3ae9, 0x0000, 0x0000, 0x0000, 0x231a, 0x9ef0,
    0x3aca, 0x0000, 0x0000, 0x0000, 0x0670, 0xd1f1, 0x3aaa, 0x0000, 0x0000, 0x0000,
    0x7738, 0xd9f3, 0x3a8a, 0x0000, 0x0000, 0x0000, 0xa834, 0x8092, 0x3a6c, 0x0000,
    0x0000, 0x0000, 0xb45c, 0xce23, 0x3a4d, 0x0000, 0x0000, 0x0000, 0x36e8, 0xb0e5,
    0x3a2d, 0x0000, 0x0000, 0x0000, 0xd156, 0xaf44, 0x3a10, 0x0000, 0x0000, 0x0000,
    0x9f52, 0x8c82, 0x39f1, 0x0000, 0x0000, 0x0000, 0x829c, 0xff83, 0x39d1, 0x0000,
    0x0000, 0x0000, 0x7d06, 0xefc6, 0x39b3, 0x0000, 0x0000, 0x0000, 0x93e0, 0xb0b7,
    0x3992, 0x0000, 0x0000, 0x0000, 0xedde, 0xc193, 0x3975, 0x0000, 0x0000, 0x0000,
    0xbbc0, 0xcf49, 0x3952, 0x0000, 0x0000, 0x0000, 0xbdf0, 0xd63c, 0x3937, 0x0000,
    0x0000, 0x0000, 0x1f34, 0x9f3a, 0x3918, 0x0000, 0x0000, 0x0000, 0x3f8e, 0xe579,
    0x38f9, 0x0000, 0x0000, 0x0000, 0x90c8, 0xc3f8, 0x38d9, 0x0000, 0x0000, 0x0000,
    0x48c0, 0xf8f8, 0x38b7, 0x0000, 0x0000, 0x0000, 0xed56, 0xafa6, 0x389c, 0x0000,
    0x0000, 0x0000, 0x8218, 0xb969, 0x387d, 0x0000, 0x0000, 0x0000, 0x1852, 0xec57,
    0x385e, 0x0000, 0x0000, 0x0000, 0x670c, 0xd674, 0x383e, 0x0000, 0x0000, 0x0000,
    0xad40, 0xc2c4, 0x3820, 0x0000, 0x0000, 0x0000, 0x2e80, 0xa696, 0x3801, 0x0000,
    0x0000, 0x0000, 0xd800, 0xc467, 0x37dc, 0x0000, 0x0000, 0x0000, 0x3c72, 0xc5ae,
    0x37c3, 0x0000, 0x0000, 0x0000, 0xb006, 0xac69, 0x37a4, 0x0000, 0x0000, 0x0000,
    0x34a0, 0x8cdf, 0x3782, 0x0000, 0x0000, 0x0000, 0x9ed2, 0xd25e, 0x3766, 0x0000,
    0x0000, 0x0000, 0x6fec, 0xaaaa, 0x3747, 0x0000, 0x0000, 0x0000, 0x6040, 0xfb5c,
    0x3726, 0x0000, 0x0000, 0x0000, 0x764c, 0xa3fc, 0x3708, 0x0000, 0x0000, 0x0000,
    0xb254, 0x954e, 0x36e9, 0x0000, 0x0000, 0x0000, 0x3e1c, 0xf5dc, 0x36ca, 0x0000,
    0x0000, 0x0000, 0x7b06, 0xc635, 0x36ac, 0x0000, 0x0000, 0x0000, 0xa8ba, 0xd738,
    0x368d, 0x0000, 0x0000, 0x0000, 0x06cc, 0xb24e, 0x366d, 0x0000, 0x0000, 0x0000,
    0x7108, 0xac76, 0x364f, 0x0000, 0x0000, 0x0000, 0x2324, 0xa7cb, 0x3630, 0x0000,
    0x0000, 0x0000, 0xac40, 0xef15, 0x360f, 0x0000, 0x0000, 0x0000, 0xae46, 0xd516,
    0x35f2, 0x0000, 0x0000, 0x0000, 0x615e, 0xe003, 0x35d3, 0x0000, 0x0000, 0x0000,
    0x0cf0, 0xefe7, 0x35b1, 0x0000, 0x0000, 0x0000, 0xfb50, 0xf98c, 0x3595, 0x0000,
    0x0000, 0x0000, 0x0abc, 0xf333, 0x3575, 0x0000, 0x0000, 0x0000, 0xdd60, 0xca3f,
    0x3555, 0x0000, 0x0000, 0x0000, 0x7eb6, 0xd87f, 0x3538, 0x0000, 0x0000, 0x0000,
    0x44f4, 0xb291, 0x3519, 0x0000, 0x0000, 0x0000, 0xff80, 0xc982, 0x34f6, 0x0000,
    0x0000, 0x0000, 0x9de0, 0xd9b8, 0x34db, 0x0000, 0x0000, 0x0000, 0xcd42, 0x9366,
    0x34bc, 0x0000, 0x0000, 0x0000, 0xbef0, 0xfaee, 0x349d, 0x0000, 0x0000, 0x0000,
    0xdac4, 0xb6f1, 0x347d, 0x0000, 0x0000, 0x0000, 0xf140, 0x94de, 0x345d, 0x0000,
    0x0000, 0x0000, 0xa218, 0x8b4b, 0x343e, 0x0000, 0x0000, 0x0000, 0x6380, 0xa135,
    0x341e, 0x0000, 0x0000, 0x0000, 0xb184, 0x8cb2, 0x3402, 0x0000, 0x0000, 0x0000,
    0x196e, 0xdc61, 0x33e3, 0x0000, 0x0000, 0x0000, 0x0c00, 0xde05, 0x33c4, 0x0000,
    0x0000, 0x0000, 0xef9a, 0xbd38, 0x33a5, 0x0000, 0x0000, 0x0000, 0xc1a0, 0xdf00,
    0x3385, 0x0000, 0x0000, 0x0000, 0x1090, 0x9973, 0x3365, 0x0000, 0x0000, 0x0000,
    0x4882, 0x8301, 0x3348, 0x0000, 0x0000, 0x0000, 0x7abe, 0xadc7, 0x3329, 0x0000,
    0x0000, 0x0000, 0x7cba, 0xec2b, 0x330a, 0x0000, 0x0000, 0x0000, 0xa520, 0x8f21,
    0x32e9, 0x0000, 0x0000, 0x0000, 0x710c, 0x8d36, 0x32cc, 0x0000, 0x0000, 0x0000,
    0x5212, 0xc6ed, 0x32ad, 0x0000, 0x0000, 0x0000, 0x7308, 0xfd76, 0x328d, 0x0000,
    0x0000, 0x0000, 0x5014, 0xd548, 0x326f, 0x0000, 0x0000, 0x0000, 0xd3f2, 0xb499,
    0x3250, 0x0000, 0x0000, 0x0000, 0x7f74, 0xa606, 0x3230, 0x0000, 0x0000, 0x0000,
    0xf0a8, 0xd720, 0x3212, 0x0000, 0x0000, 0x0000, 0x185c, 0xe20f, 0x31f2, 0x0000,
    0x0000, 0x0000, 0xa5a8, 0x8738, 0x31d4, 0x0000, 0x0000, 0x0000, 0xdd74, 0xcafb,
    0x31b4, 0x0000, 0x0000, 0x0000, 0x98b6, 0xbd8e, 0x3196, 0x0000, 0x0000, 0x0000,
    0xe9de, 0x977f, 0x3177, 0x0000, 0x0000, 0x0000, 0x67c0, 0x818d, 0x3158, 0x0000,
    0x0000, 0x0000, 0xe52a, 0x9322, 0x3139, 0x0000, 0x0000, 0x0000, 0xe568, 0x9b6c,
    0x3119, 0x0000, 0x0000, 0x0000, 0x2358, 0xaa0a, 0x30fa, 0x0000, 0x0000, 0x0000,
    0xe480, 0xe13b, 0x30d9, 0x0000, 0x0000, 0x0000, 0x3024, 0x90a1, 0x30bd, 0x0000,
    0x0000, 0x0000, 0x9620, 0xda30, 0x309d, 0x0000, 0x0000, 0x0000, 0x898a, 0xb388,
    0x307f, 0x0000, 0x0000, 0x0000, 0xb24c, 0xc891, 0x3060, 0x0000, 0x0000, 0x0000,
    0x8056, 0xf98b, 0x3041, 0x0000, 0x0000, 0x0000, 0x72a4, 0xa1ea, 0x3021, 0x0000,
    0x0000, 0x0000, 0x6af8, 0x9488, 0x3001, 0x0000, 0x0000, 0x0000, 0xe00c, 0xdfcb,
    0x2fe4, 0x0000, 0x0000, 0x0000, 0xeeec, 0xc941, 0x2fc4, 0x0000, 0x0000, 0x0000,
    0x53e0, 0xe70f, 0x2fa4, 0x0000, 0x0000, 0x0000, 0x8f60, 0x9c07, 0x2f85, 0x0000,
    0x0000, 0x0000, 0xb328, 0xc3e7, 0x2f68, 0x0000, 0x0000, 0x0000, 0x9404, 0xf8c7,
    0x2f48, 0x0000, 0x0000, 0x0000, 0x38e0, 0xc99f, 0x2f29, 0x0000, 0x0000, 0x0000,
    0x9778, 0xd984, 0x2f09, 0x0000, 0x0000, 0x0000, 0xe700, 0xd142, 0x2eea, 0x0000,
    0x0000, 0x0000, 0xd904, 0x9443, 0x2ecd, 0x0000, 0x0000, 0x0000, 0xd4ba, 0xae7e,
    0x2eae, 0x0000, 0x0000, 0x0000, 0x8e5e, 0x8524, 0x2e8f, 0x0000, 0x0000, 0x0000,
    0xb550, 0xc9ed, 0x2e6e, 0x0000, 0x0000, 0x0000, 0x53b8, 0x8648, 0x2e51, 0x0000,
    0x0000, 0x0000, 0xdae4, 0x87f9, 0x2e32, 0x0000, 0x0000, 0x0000, 0x2942, 0xd966,
    0x2e13, 0x0000, 0x0000, 0x0000, 0x4f28, 0xcf3c, 0x2df3, 0x0000, 0x0000, 0x0000,
    0xfa40, 0xc4ef, 0x2dd1, 0x0000, 0x0000, 0x0000, 0x4424, 0xbca7, 0x2db5, 0x0000,
    0x0000, 0x0000, 0x2e62, 0xcdc5, 0x2d97, 0x0000, 0x0000, 0x0000, 0xed88, 0x996b,
    0x2d78, 0x0000, 0x0000, 0x0000, 0x7c30, 0xd97d, 0x2d56, 0x0000, 0x0000, 0x0000,
    0xed26, 0xbf6e, 0x2d3a, 0x0000, 0x0000, 0x0000, 0x2918, 0x921b, 0x2d1a, 0x0000,
    0x0000, 0x0000, 0x4e24, 0xe84e, 0x2cfb, 0x0000, 0x0000, 0x0000, 0x6dc0, 0x92ec,
    0x2cdd, 0x0000, 0x0000, 0x0000, 0x4f2c, 0xacf8, 0x2cbd, 0x0000, 0x0000, 0x0000,
    0xc634, 0xf094, 0x2c9e, 0x0000, 0x0000, 0x0000, 0xdc70, 0xe5d3, 0x2c7e, 0x0000,
    0x0000, 0x0000, 0x2180, 0xa600, 0x2c5b, 0x0000, 0x0000, 0x0000, 0x8480, 0xd680,
    0x2c3c, 0x0000, 0x0000, 0x0000, 0x8b24, 0xd63b, 0x2c22, 0x0000, 0x0000, 0x0000,
    0x02e0, 0xaa47, 0x2c00, 0x0000, 0x0000, 0x0000, 0x9ad0, 0xee84, 0x2be3, 0x0000,
    0x0000, 0x0000, 0xf7dc, 0xf699, 0x2bc6, 0x0000, 0x0000, 0x0000, 0xddde, 0xe490,
    0x2ba7, 0x0000, 0x0000, 0x0000, 0x34a0, 0xb4fd, 0x2b85, 0x0000, 0x0000, 0x0000,
    0x91b4, 0x8ef6, 0x2b68, 0x0000, 0x0000, 0x0000, 0xa3e0, 0xa2a7, 0x2b47, 0x0000,
    0x0000, 0x0000, 0xcce4, 0x82b3, 0x2b2a, 0x0000, 0x0000, 0x0000, 0xe4be, 0x8207,
    0x2b0c, 0x0000, 0x0000, 0x0000, 0x1d92, 0xab43, 0x2aed, 0x0000, 0x0000, 0x0000,
    0xe818, 0xf9f6, 0x2acd, 0x0000, 0x0000, 0x0000, 0xff12, 0xba80, 0x2aaf, 0x0000,
    0x0000, 0x0000, 0x5254, 0x8529, 0x2a90, 0x0000, 0x0000, 0x0000, 0x1b88, 0xe032,
    0x2a71, 0x0000, 0x0000, 0x0000, 0x3248, 0xd86d, 0x2a50, 0x0000, 0x0000, 0x0000,
    0x3140, 0xc9d5, 0x2a2e, 0x0000, 0x0000, 0x0000, 0x14e6, 0xbd47, 0x2a14, 0x0000,
    0x0000, 0x0000, 0x5c10, 0xe544, 0x29f4, 0x0000, 0x0000, 0x0000, 0x9f50, 0x90b6,
    0x29d4, 0x0000, 0x0000, 0x0000, 0x9850, 0xab55, 0x29b6, 0x0000, 0x0000, 0x0000,
    0x2750, 0x9d07, 0x2998, 0x0000, 0x0000, 0x0000, 0x6700, 0x8bbb, 0x2973, 0x0000,
    0x0000, 0x0000, 0x5dba, 0xed31, 0x295a, 0x0000, 0x0000, 0x0000, 0x61dc, 0x85fe,
    0x293a, 0x0000, 0x0000, 0x0000, 0x9ba2, 0xd6b4, 0x291c, 0x0000, 0x0000, 0x0000,
    0x2d30, 0xe3a5, 0x28fb, 0x0000, 0x0000, 0x0000, 0x6630, 0xb566, 0x28dd, 0x0000,
    0x0000, 0x0000, 0x5ad4, 0xa829, 0x28bf, 0x0000, 0x0000, 0x0000, 0x89d8, 0xe290,
    0x28a0, 0x0000, 0x0000, 0x0000, 0x3916, 0xc428, 0x2881, 0x0000, 0x0000, 0x0000,
    0x0490, 0xbea4, 0x2860, 0x0000, 0x0000, 0x0000, 0xee06, 0x80ee, 0x2843, 0x0000,
    0x0000, 0x0000, 0xfc00, 0xf327, 0x2820, 0x0000, 0x0000, 0x0000, 0xea40, 0xa871,
    0x2800, 0x0000, 0x0000, 0x0000, 0x63d8, 0x9c26, 0x27e4, 0x0000, 0x0000, 0x0000,
    0x07ba, 0xc0c9, 0x27c7, 0x0000, 0x0000, 0x0000, 0x3fa2, 0x9797, 0x27a8, 0x0000,
    0x0000, 0x0000, 0x21c6, 0xfeca, 0x2789, 0x0000, 0x0000, 0x0000, 0xde40, 0x860d,
    0x2768, 0x0000, 0x0000, 0x0000, 0x9cc8, 0x98ce, 0x2749, 0x0000, 0x0000, 0x0000,
    0x3778, 0xa31c, 0x272a, 0x0000, 0x0000, 0x0000, 0xe778, 0xf6e2, 0x270b, 0x0000,
    0x0000, 0x0000, 0x59b8, 0xf841, 0x26ed, 0x0000, 0x0000, 0x0000, 0x02e0, 0xad04,
    0x26cd, 0x0000, 0x0000, 0x0000, 0x5a92, 0x9380, 0x26b0, 0x0000, 0x0000, 0x0000,
    0xc740, 0x8886, 0x268d, 0x0000, 0x0000, 0x0000, 0x0680, 0xfaf8, 0x266c, 0x0000,
    0x0000, 0x0000, 0xfb60, 0x897f, 0x2653, 0x0000, 0x0000, 0x0000, 0x8760, 0xf903,
    0x2634, 0x0000, 0x0000, 0x0000, 0xad2a, 0xc2c8, 0x2615, 0x0000, 0x0000, 0x0000,
    0x2d86, 0x8aef, 0x25f6, 0x0000, 0x0000, 0x0000, 0x1ef4, 0xe627, 0x25d6, 0x0000,
    0x0000, 0x0000, 0x09e4, 0x8020, 0x25b7, 0x0000, 0x0000, 0x0000, 0x7548, 0xd227,
    0x2598, 0x0000, 0x0000, 0x0000, 0x75dc, 0xfb5b, 0x2579, 0x0000, 0x0000, 0x0000,
    0xea84, 0xc8b6, 0x255a, 0x0000, 0x0000, 0x0000, 0xe4d0, 0x8145, 0x253b, 0x0000,
    0x0000, 0x0000, 0x3640, 0x9768, 0x251c, 0x0000, 0x0000, 0x0000, 0x246a, 0xccec,
    0x24fe, 0x0000, 0x0000, 0x0000, 0x51d0, 0xa075, 0x24dd, 0x0000, 0x0000, 0x0000,
    0x4638, 0xa385, 0x24bf, 0x0000, 0x0000, 0x0000, 0xd788, 0xd776, 0x24a1, 0x0000,
    0x0000, 0x0000, 0x1370, 0x8997, 0x2482, 0x0000, 0x0000, 0x0000, 0x1e88, 0x9b67,
    0x2462, 0x0000, 0x0000, 0x0000, 0x6c08, 0xd975, 0x2444, 0x0000, 0x0000, 0x0000,
    0xfdb0, 0xcfc0, 0x2422, 0x0000, 0x0000, 0x0000, 0x3100, 0xc026, 0x2406, 0x0000,
    0x0000, 0x0000, 0xc5b4, 0xae64, 0x23e6, 0x0000, 0x0000, 0x0000, 0x2280, 0xf687,
    0x23c3, 0x0000, 0x0000, 0x0000, 0x2de0, 0x9006, 0x23a9, 0x0000, 0x0000, 0x0000,
    0x24bc, 0xf631, 0x238a, 0x0000, 0x0000, 0x0000, 0xb8d4, 0xa975, 0x236b, 0x0000,
    0x0000, 0x0000, 0xd9a4, 0xb949, 0x234b, 0x0000, 0x0000, 0x0000, 0xb54e, 0xbd39,
    0x232d, 0x0000, 0x0000, 0x0000, 0x4aac, 0x9a52, 0x230e, 0x0000, 0x0000, 0x0000,
    0xbbbc, 0xd085, 0x22ef, 0x0000, 0x0000, 0x0000, 0xdf18, 0xc633, 0x22cf, 0x0000,
    0x0000, 0x0000, 0x16d0, 0xeca5, 0x22af, 0x0000, 0x0000, 0x0000, 0xf2a0, 0xdf6f,
    0x228e, 0x0000, 0x0000, 0x0000, 0x8c44, 0xe86b, 0x2272, 0x0000, 0x0000, 0x0000,
    0x35c0, 0xbbf4, 0x2253, 0x0000, 0x0000, 0x0000, 0x0c40, 0xdafb, 0x2230, 0x0000,
    0x0000, 0x0000, 0x92dc, 0x9935, 0x2216, 0x0000, 0x0000, 0x0000, 0x0ca0, 0xbda6,
    0x21f3, 0x0000, 0x0000, 0x0000, 0x5958, 0xa6fd, 0x21d6, 0x0000, 0x0000, 0x0000,
    0xa3dc, 0x9d7f, 0x21b9, 0x0000, 0x0000, 0x0000, 0x79dc, 0xfcb5, 0x2199, 0x0000,
    0x0000, 0x0000, 0xf264, 0xcebb, 0x217b, 0x0000, 0x0000, 0x0000, 0x0abe, 0x8308,
    0x215c, 0x0000, 0x0000, 0x0000, 0x30ae, 0xb463, 0x213d, 0x0000, 0x0000, 0x0000,
    0x6228, 0xb040, 0x211c, 0x0000, 0x0000, 0x0000, 0xc9b2, 0xf43b, 0x20ff, 0x0000,
    0x0000, 0x0000, 0x3d8e, 0xa4b3, 0x20e0, 0x0000, 0x0000, 0x0000, 0x84e6, 0x8dab,
    0x20c1, 0x0000, 0x0000, 0x0000, 0xa124, 0x9b74, 0x20a1, 0x0000, 0x0000, 0x0000,
    0xc276, 0xd497, 0x2083, 0x0000, 0x0000, 0x0000, 0x6354, 0xa466, 0x2063, 0x0000,
    0x0000, 0x0000, 0x8654, 0xaf0a, 0x2044, 0x0000, 0x0000, 0x0000, 0x1d20, 0xfa5c,
    0x2024, 0x0000, 0x0000, 0x0000, 0xbcd0, 0xf3f0, 0x2004, 0x0000, 0x0000, 0x0000,
    0xedf0, 0xf0b6, 0x1fe7, 0x0000, 0x0000, 0x0000, 0x45bc, 0x9182, 0x1fc9, 0x0000,
    0x0000, 0x0000, 0xe254, 0xdc85, 0x1faa, 0x0000, 0x0000, 0x0000, 0xb898, 0xe9b1,
    0x1f8a, 0x0000, 0x0000, 0x0000, 0x0ebe, 0xe6f0, 0x1f6c, 0x0000, 0x0000, 0x0000,
    0xa9b8, 0xf584, 0x1f4c, 0x0000, 0x0000, 0x0000, 0x12e8, 0xdf6b, 0x1f2e, 0x0000,
    0x0000, 0x0000, 0x9f9e, 0xcd55, 0x1f0f, 0x0000, 0x0000, 0x0000, 0x05a0, 0xec3a,
    0x1eef, 0x0000, 0x0000, 0x0000, 0xd8e0, 0x96f8, 0x1ed1, 0x0000, 0x0000, 0x0000,
    0x3bd4, 0xccc6, 0x1eb1, 0x0000, 0x0000, 0x0000, 0x4910, 0xb87b, 0x1e93, 0x0000,
    0x0000, 0x0000, 0xbefc, 0xd40b, 0x1e73, 0x0000, 0x0000, 0x0000, 0x317e, 0xa406,
    0x1e55, 0x0000, 0x0000, 0x0000, 0x6bb2, 0xc2b2, 0x1e36, 0x0000, 0x0000, 0x0000,
    0xb87e, 0xbb78, 0x1e17, 0x0000, 0x0000, 0x0000, 0xa03c, 0xdbbd, 0x1df7, 0x0000,
    0x0000, 0x0000, 0x5b6c, 0xe3c8, 0x1dd9, 0x0000, 0x0000, 0x0000, 0x8968, 0xca8e,
    0x1dba, 0x0000, 0x0000, 0x0000, 0xc024, 0xe6ab, 0x1d9a, 0x0000, 0x0000, 0x0000,
    0x4110, 0xd4eb, 0x1d7a, 0x0000, 0x0000, 0x0000, 0xa168, 0xbdb5, 0x1d5d, 0x0000,
    0x0000, 0x0000, 0x012e, 0xa5fa, 0x1d3e, 0x0000, 0x0000, 0x0000, 0x6838, 0x9c1f,
    0x1d1e, 0x0000, 0x0000, 0x0000, 0xa158, 0xaa76, 0x1d00, 0x0000, 0x0000, 0x0000,
    0x090a, 0xbd95, 0x1ce1, 0x0000, 0x0000, 0x0000, 0xf73e, 0x8b6d, 0x1cc2, 0x0000,
    0x0000, 0x0000, 0x5fda, 0xbcbf, 0x1ca3, 0x0000, 0x0000, 0x0000, 0xdbe8, 0xb89f,
    0x1c84, 0x0000, 0x0000, 0x0000, 0x6e4c, 0x96c7, 0x1c64, 0x0000, 0x0000, 0x0000,
    0x19c2, 0xf2a4, 0x1c46, 0x0000, 0x0000, 0x0000, 0xb800, 0xf855, 0x1c1e, 0x0000,
    0x0000, 0x0000, 0x87fc, 0x85ff, 0x1c08, 0x0000, 0x0000, 0x0000, 0x1418, 0x839f,
    0x1be9, 0x0000, 0x0000, 0x0000, 0x6186, 0xd9d8, 0x1bca, 0x0000, 0x0000, 0x0000,
    0xf500, 0xabaa, 0x1ba6, 0x0000, 0x0000, 0x0000, 0x7b36, 0xdafe, 0x1b8c, 0x0000,
    0x0000, 0x0000, 0xf394, 0xe6d8, 0x1b6c, 0x0000, 0x0000, 0x0000, 0x6efc, 0x9e55,
    0x1b4e, 0x0000, 0x0000, 0x0000, 0x5e10, 0xc523, 0x1b2e, 0x0000, 0x0000, 0x0000,
    0x8210, 0xb6f9, 0x1b0d, 0x0000, 0x0000, 0x0000, 0x9ab0, 0x96e3, 0x1af1, 0x0000,
    0x0000, 0x0000, 0x3864, 0x92e7, 0x1ad1, 0x0000, 0x0000, 0x0000, 0x9878, 0xdc65,
    0x1ab1, 0x0000, 0x0000, 0x0000, 0xfa20, 0xd6cb, 0x1a94, 0x0000, 0x0000, 0x0000,
    0x6c00, 0xa4e4, 0x1a70, 0x0000, 0x0000, 0x0000, 0xab40, 0xb41b, 0x1a53, 0x0000,
    0x0000, 0x0000, 0x43a4, 0x8ede, 0x1a37, 0x0000, 0x0000, 0x0000, 0x22e0, 0x9314,
    0x1a15, 0x0000, 0x0000, 0x0000, 0x6170, 0xb949, 0x19f8, 0x0000, 0x0000, 0x0000,
    0x6b00, 0xe056, 0x19d8, 0x0000, 0x0000, 0x0000, 0x9ba8, 0xa94c, 0x19b9, 0x0000,
    0x0000, 0x0000, 0xfaa0, 0xaa16, 0x199b, 0x0000, 0x0000, 0x0000, 0x899a, 0xf627,
    0x197d, 0x0000, 0x0000, 0x0000, 0x9f20, 0xfb70, 0x195d, 0x0000, 0x0000, 0x0000,
    0xa4b8, 0xc176, 0x193e, 0x0000, 0x0000, 0x0000, 0xb21c, 0x85c3, 0x1920, 0x0000,
    0x0000, 0x0000, 0x50d2, 0x9b19, 0x1901, 0x0000, 0x0000, 0x0000, 0xd4b0, 0xb708,
    0x18e0, 0x0000, 0x0000, 0x0000, 0xfb88, 0xf510, 0x18c1, 0x0000, 0x0000, 0x0000,
    0x31ec, 0xdc8d, 0x18a3, 0x0000, 0x0000, 0x0000, 0x3c00, 0xbff9, 0x1885, 0x0000,
    0x0000, 0x0000, 0x5020, 0xc30b, 0x1862, 0x0000, 0x0000, 0x0000, 0xd4f0, 0xda0c,
    0x1844, 0x0000, 0x0000, 0x0000, 0x20d2, 0x99a5, 0x1828, 0x0000, 0x0000, 0x0000,
    0x852e, 0xd159, 0x1809, 0x0000, 0x0000, 0x0000, 0x7cd8, 0x97a1, 0x17e9, 0x0000,
    0x0000, 0x0000, 0x423a, 0x997b, 0x17cb, 0x0000, 0x0000, 0x0000, 0xc1c0, 0xbe7d,
    0x17a8, 0x0000, 0x0000, 0x0000, 0xe8bc, 0xdcdd, 0x178d, 0x0000, 0x0000, 0x0000,
    0x8b28, 0xae06, 0x176e, 0x0000, 0x0000, 0x0000, 0x102e, 0xb8d4, 0x174f, 0x0000,
    0x0000, 0x0000, 0xaa00, 0xaa5c, 0x172f, 0x0000, 0x0000, 0x0000, 0x51f0, 0x9fc0,
    0x170e, 0x0000, 0x0000, 0x0000, 0xf858, 0xe181, 0x16f2, 0x0000, 0x0000, 0x0000,
    0x91a8, 0x8162, 0x16d3, 0x0000, 0x0000, 0x0000, 0x5f40, 0xcb6f, 0x16b1, 0x0000,
    0x0000, 0x0000, 0xbb50, 0xe55f, 0x1693, 0x0000, 0x0000, 0x0000, 0xacd2, 0xd895,
    0x1676, 0x0000, 0x0000, 0x0000, 0xef30, 0x97bf, 0x1654, 0x0000, 0x0000, 0x0000,
    0xf700, 0xb3d7, 0x1633, 0x0000, 0x0000, 0x0000, 0x3454, 0xa7b5, 0x1619, 0x0000,
    0x0000, 0x0000, 0x6b00, 0xa929, 0x15f6, 0x0000, 0x0000, 0x0000, 0x9f04, 0x89f7,
    0x15db, 0x0000, 0x0000, 0x0000, 0xad78, 0xd985, 0x15bc, 0x0000, 0x0000, 0x0000,
    0xa46a, 0xae3f, 0x159d, 0x0000, 0x0000, 0x0000, 0x63a0, 0xd0da, 0x157c, 0x0000,
    0x0000, 0x0000, 0x5e90, 0x817d, 0x155e, 0x0000, 0x0000, 0x0000, 0x1494, 0xb13f,
    0x1540, 0x0000, 0x0000, 0x0000, 0x0090, 0x9c40, 0x1521, 0x0000, 0x0000, 0x0000,
    0xdd70, 0xcc86, 0x1500, 0x0000, 0x0000, 0x0000, 0x64f8, 0xdb6f, 0x14e1, 0x0000,
    0x0000, 0x0000, 0xe22c, 0xac17, 0x14c3, 0x0000, 0x0000, 0x0000, 0x60e0, 0xa9ad,
    0x14a3, 0x0000, 0x0000, 0x0000, 0x4640, 0xd658, 0x1481, 0x0000, 0x0000, 0x0000,
    0x6490, 0xa181, 0x1467, 0x0000, 0x0000, 0x0000, 0x1df4, 0xaaa2, 0x1447, 0x0000,
    0x0000, 0x0000, 0xb94a, 0x8f61, 0x1429, 0x0000, 0x0000, 0x0000, 0x5198, 0x9d83,
    0x1409, 0x0000, 0x0000, 0x0000, 0x0f7a, 0xa818, 0x13eb, 0x0000, 0x0000, 0x0000,
    0xc45e, 0xc06c, 0x13cc, 0x0000, 0x0000, 0x0000, 0x4ec0, 0xfa29, 0x13a8, 0x0000,
    0x0000, 0x0000, 0x6418, 0x8cad, 0x138c, 0x0000, 0x0000, 0x0000, 0xbcc8, 0xe7d1,
    0x136f, 0x0000, 0x0000, 0x0000, 0xc934, 0xf9b0, 0x134f, 0x0000, 0x0000, 0x0000,
    0x6ce0, 0x98df, 0x1331, 0x0000, 0x0000, 0x0000, 0x3516, 0xe5e9, 0x1312, 0x0000,
    0x0000, 0x0000, 0xc6c0, 0xef8b, 0x12ef, 0x0000, 0x0000, 0x0000, 0xaf02, 0x913d,
    0x12d4, 0x0000, 0x0000, 0x0000, 0xd230, 0xe1d5, 0x12b5, 0x0000, 0x0000, 0x0000,
    0xfba8, 0xc232, 0x1295, 0x0000, 0x0000, 0x0000, 0x7ba4, 0xabeb, 0x1277, 0x0000,
    0x0000, 0x0000, 0x6e5c, 0xc692, 0x1258, 0x0000, 0x0000, 0x0000, 0x76a2, 0x9756,
    0x1239, 0x0000, 0x0000, 0x0000, 0xe180, 0xe423, 0x1214, 0x0000, 0x0000, 0x0000,
    0x8c3c, 0x90f8, 0x11fb, 0x0000, 0x0000, 0x0000, 0x9f3c, 0x9fd2, 0x11dc, 0x0000,
    0x0000, 0x0000, 0x53e0, 0xb73e, 0x11bd, 0x0000, 0x0000, 0x0000, 0x45be, 0x88d6,
    0x119e, 0x0000, 0x0000, 0x0000, 0x111a, 0x8bc0, 0x117f, 0x0000, 0x0000, 0x0000,
    0xe26a, 0xd7ff, 0x1160, 0x0000, 0x0000, 0x0000, 0xfb60, 0xdd8d, 0x113f, 0x0000,
    0x0000, 0x0000, 0x9370, 0xc108, 0x1120, 0x0000, 0x0000, 0x0000, 0x9654, 0x8baf,
    0x1103, 0x0000, 0x0000, 0x0000, 0xd6ec, 0xd6b9, 0x10e4, 0x0000, 0x0000, 0x0000,
    0x23e4, 0xd7b7, 0x10c4, 0x0000, 0x0000, 0x0000, 0x1aa6, 0xa847, 0x10a6, 0x0000,
    0x0000, 0x0000, 0xbee6, 0x9fef, 0x1087, 0x0000, 0x0000, 0x0000, 0x26d0, 0xa6eb,
    0x1066, 0x0000, 0x0000, 0x0000, 0x5b86, 0xa880, 0x1049, 0x0000, 0x0000, 0x0000,
    0x125c, 0xd971, 0x1029, 0x0000, 0x0000, 0x0000, 0x1f78, 0x9d18, 0x100a, 0x0000,
    0x0000, 0x0000, 0x0e84, 0xb15b, 0x0feb, 0x0000, 0x0000, 0x0000, 0xd0c0, 0xc150,
    0x0fcc, 0x0000, 0x0000, 0x0000, 0xa330, 0xc40c, 0x0fad, 0x0000, 0x0000, 0x0000,
    0x5202, 0xfc2c, 0x0f8f, 0x0000, 0x0000, 0x0000, 0x3f7c, 0xecf5, 0x0f6f, 0x0000,
    0x0000, 0x0000, 0xef44, 0xfdfd, 0x0f50, 0x0000, 0x0000, 0x0000, 0x3f6c, 0xab1b,
    0x0f31, 0x0000, 0x0000, 0x0000, 0xf658, 0x89ec, 0x0f11, 0x0000, 0x0000, 0x0000,
    0xbfc8, 0x9ba8, 0x0ef4, 0x0000, 0x0000, 0x0000, 0x3d40, 0xbe21, 0x0ed5, 0x0000,
    0x0000, 0x0000, 0xbbc4, 0xc70d, 0x0eb6, 0x0000, 0x0000, 0x0000, 0x5158, 0xdb16,
    0x0e96, 0x0000, 0x0000, 0x0000, 0xb5a8, 0xa8d8, 0x0e78, 0x0000, 0x0000, 0x0000,
    0xcccc, 0xb40e, 0x0e58, 0x0000, 0x0000, 0x0000, 0x448c, 0xcb62, 0x0e3a, 0x0000,
    0x0000, 0x0000, 0xf12a, 0x8aed, 0x0e1b, 0x0000, 0x0000, 0x0000, 0x79d0, 0xc59c,
    0x0dfb, 0x0000, 0x0000, 0x0000, 0x06b4, 0xcdc9, 0x0ddd, 0x0000, 0x0000, 0x0000,
    0xae70, 0xa979, 0x0dbe, 0x0000, 0x0000, 0x0000, 0x317c, 0xa8fb, 0x0d9e, 0x0000,
    0x0000, 0x0000, 0x5fe0, 0x8a50, 0x0d7d, 0x0000, 0x0000, 0x0000, 0x70b6, 0xfdfa,
    0x0d61, 0x0000, 0x0000, 0x0000, 0x1640, 0x9dc7, 0x0d41, 0x0000, 0x0000, 0x0000,
    0x9a9c, 0xdc50, 0x0d23, 0x0000, 0x0000, 0x0000, 0x4fcc, 0x9a9b, 0x0d04, 0x0000,
    0x0000, 0x0000, 0x7e48, 0x8f77, 0x0ce5, 0x0000, 0x0000, 0x0000, 0x84e4, 0xd4b9,
    0x0cc6, 0x0000, 0x0000, 0x0000, 0x84e0, 0xbd10, 0x0ca6, 0x0000, 0x0000, 0x0000,
    0x1b0a, 0xc8d9, 0x0c88, 0x0000, 0x0000, 0x0000, 0x6a48, 0xfc81, 0x0c68, 0x0000,
    0x0000, 0x0000, 0x070a, 0xbef6, 0x0c4a, 0x0000, 0x0000, 0x0000, 0x8a70, 0xf096,
    0x0c2b, 0x0000, 0x0000, 0x0000, 0xecc2, 0xc994, 0x0c0c, 0x0000, 0x0000, 0x0000,
    0x1540, 0x9537, 0x0bea, 0x0000, 0x0000, 0x0000, 0x1b02, 0xab5b, 0x0bce, 0x0000,
    0x0000, 0x0000, 0x5dc0, 0xb0c8, 0x0bad, 0x0000, 0x0000, 0x0000, 0xc928, 0xe034,
    0x0b8f, 0x0000, 0x0000, 0x0000, 0x2d12, 0xb4b0, 0x0b71, 0x0000, 0x0000, 0x0000,
    0x8fc2, 0xbb94, 0x0b52, 0x0000, 0x0000, 0x0000, 0xe236, 0xe22f, 0x0b33, 0x0000,
    0x0000, 0x0000, 0xb97c, 0xbe9e, 0x0b13, 0x0000, 0x0000, 0x0000, 0xe1a6, 0xe16d,
    0x0af5, 0x0000, 0x0000, 0x0000, 0xd330, 0xbaf0, 0x0ad6, 0x0000, 0x0000, 0x0000,
    0xc0bc, 0xbbd0, 0x0ab7, 0x0000, 0x0000, 0x0000, 0x8e66, 0xdd9b, 0x0a98, 0x0000,
    0x0000, 0x0000, 0xc95c, 0xf799, 0x0a79, 0x0000, 0x0000, 0x0000, 0xdac0, 0xbe4c,
    0x0a55, 0x0000, 0x0000, 0x0000, 0xafc0, 0xc378, 0x0a37, 0x0000, 0x0000, 0x0000,
    0xa880, 0xe341, 0x0a19, 0x0000, 0x0000, 0x0000, 0xc242, 0x81f6, 0x09fd, 0x0000,
    0x0000, 0x0000, 0x7470, 0xc777, 0x09de, 0x0000, 0x0000, 0x0000, 0x62bc, 0xb684,
    0x09be, 0x0000, 0x0000, 0x0000, 0x43ac, 0x8c58, 0x099f, 0x0000, 0x0000, 0x0000,
    0xcc3c, 0xf9ac, 0x0981, 0x0000, 0x0000, 0x0000, 0x1526, 0xb670, 0x0962, 0x0000,
    0x0000, 0x0000, 0xc9fe, 0xdf50, 0x0943, 0x0000, 0x0000, 0x0000, 0x6ae6, 0xc065,
    0x0924, 0x0000, 0x0000, 0x0000, 0xb114, 0xcf29, 0x0905, 0x0000, 0x0000, 0x0000,
    0xd388, 0x922a, 0x08e4, 0x0000, 0x0000, 0x0000, 0xcf54, 0xb926, 0x08c7, 0x0000,
    0x0000, 0x0000, 0x3826, 0xe855, 0x08a8, 0x0000, 0x0000, 0x0000, 0xe7c8, 0x829b,
    0x0888, 0x0000, 0x0000, 0x0000, 0x546c, 0xa903, 0x086a, 0x0000, 0x0000, 0x0000,
    0x8768, 0x99cc, 0x0849, 0x0000, 0x0000, 0x0000, 0x00ac, 0xf529, 0x082b, 0x0000,
    0x0000, 0x0000, 0x2658, 0x9f0b, 0x080c, 0x0000, 0x0000, 0x0000, 0xfe5c, 0x9e21,
    0x07ee, 0x0000, 0x0000, 0x0000, 0x6da2, 0x9910, 0x07cf, 0x0000, 0x0000, 0x0000,
    0x9220, 0xf9b3, 0x07b0, 0x0000, 0x0000, 0x0000, 0x3d90, 0xa541, 0x0791, 0x0000,
    0x0000, 0x0000, 0x6e4c, 0xe7cc, 0x0771, 0x0000, 0x0000, 0x0000, 0xa8fa, 0xe80a,
    0x0753, 0x0000, 0x0000, 0x0000, 0x4e14, 0xc3a7, 0x0734, 0x0000, 0x0000, 0x0000,
    0xf7e0, 0xbad9, 0x0712, 0x0000, 0x0000, 0x0000, 0xfea0, 0xeff2, 0x06f5, 0x0000,
    0x0000, 0x0000, 0xcef6, 0xbd48, 0x06d7, 0x0000, 0x0000, 0x0000, 0x7544, 0xf559,
    0x06b7, 0x0000, 0x0000, 0x0000, 0x2388, 0xf655, 0x0698, 0x0000, 0x0000, 0x0000,
    0xe900, 0xad56, 0x0676, 0x0000, 0x0000, 0x0000, 0x2cc0, 0x8437, 0x0659, 0x0000,
    0x0000, 0x0000, 0x3068, 0xc544, 0x063b, 0x0000, 0x0000, 0x0000, 0xdc70, 0xe73c,
    0x061b, 0x0000, 0x0000, 0x0000, 0xee50, 0x9d49, 0x05fc, 0x0000, 0x0000, 0x0000,
    0x93d2, 0x81f6, 0x05df, 0x0000, 0x0000, 0x0000, 0x941c, 0xadff, 0x05bf, 0x0000,
    0x0000, 0x0000, 0x2ce2, 0x8e45, 0x05a1, 0x0000, 0x0000, 0x0000, 0x4a60, 0x95fd,
    0x0581, 0x0000, 0x0000, 0x0000, 0x79f8, 0xb83a, 0x0563, 0x0000, 0x0000, 0x0000,
    0xcb58, 0xa1f5, 0x0543, 0x0000, 0x0000, 0x0000, 0x2a3a, 0xdc36, 0x0525, 0x0000,
    0x0000, 0x0000, 0x14ee, 0x890e, 0x0506, 0x0000, 0x0000, 0x0000, 0x8f20, 0xc432,
    0x04e3, 0x0000, 0x0000, 0x0000, 0x8440, 0xb21d, 0x04c6, 0x0000, 0x0000, 0x0000,
    0x5430, 0xf698, 0x04a7, 0x0000, 0x0000, 0x0000, 0x04ae, 0x8b20, 0x048a, 0x0000,
    0x0000, 0x0000, 0x04d0, 0xe872, 0x046b, 0x0000, 0x0000, 0x0000, 0xc78e, 0x8893,
    0x044c, 0x0000, 0x0000, 0x0000, 0x0f78, 0x9895, 0x042b, 0x0000, 0x0000, 0x0000,
    0x11d4, 0xdf2e, 0x040d, 0x0000, 0x0000, 0x0000, 0xe84c, 0x89d5, 0x03ef, 0x0000,
    0x0000, 0x0000, 0xf7be, 0x8a67, 0x03d0, 0x0000, 0x0000, 0x0000, 0x95d0, 0xc906,
    0x03b1, 0x0000, 0x0000, 0x0000, 0x64ce, 0xd96c, 0x0392, 0x0000, 0x0000, 0x0000,
    0x97ba, 0xa16f, 0x0373, 0x0000, 0x0000, 0x0000, 0x463c, 0xc51a, 0x0354, 0x0000,
    0x0000, 0x0000, 0xef0a, 0xe93e, 0x0335, 0x0000, 0x0000, 0x0000, 0x526a, 0xa466,
    0x0316, 0x0000, 0x0000, 0x0000, 0x4140, 0xa94d, 0x02f5, 0x0000, 0x0000, 0x0000,
    0xb4ec, 0xce68, 0x02d8, 0x0000, 0x0000, 0x0000, 0x4fa2, 0x8490, 0x02b9, 0x0000,
    0x0000, 0x0000, 0x4e60, 0xca98, 0x0298, 0x0000, 0x0000, 0x0000, 0x08dc, 0xe09c,
    0x027a, 0x0000, 0x0000, 0x0000, 0x2b90, 0xc7e3, 0x025c, 0x0000, 0x0000, 0x0000,
    0x5a7c, 0xf8ef, 0x023c, 0x0000, 0x0000, 0x0000, 0x5022, 0x9d58, 0x021e, 0x0000,
    0x0000, 0x0000, 0x553a, 0xe242, 0x01ff, 0x0000, 0x0000, 0x0000, 0x7e6e, 0xb54d,
    0x01e0, 0x0000, 0x0000, 0x0000, 0xd2d4, 0xa88c, 0x01c1, 0x0000, 0x0000, 0x0000,
    0x75b6, 0xfe6d, 0x01a2, 0x0000, 0x0000, 0x0000, 0x3bb2, 0xf04c, 0x0183, 0x0000,
    0x0000, 0x0000, 0xc2d0, 0xc046, 0x0163, 0x0000, 0x0000, 0x0000, 0x250c, 0xf9d6,
    0x0145, 0x0000, 0x0000, 0x0000, 0xb7b4, 0x8a0d, 0x0126, 0x0000, 0x0000, 0x0000,
    0x1a72, 0xe4f5, 0x0107, 0x0000, 0x0000, 0x0000, 0x825c, 0xa9b8, 0x00e8, 0x0000,
    0x0000, 0x0000, 0x6c90, 0xc9ad, 0x00c6, 0x0000, 0x0000, 0x0000, 0x4d00, 0xd1bb,
    0x00aa, 0x0000, 0x0000, 0x0000, 0xa4a0, 0xee01, 0x0087, 0x0000, 0x0000, 0x0000,
    0x89a8, 0xbe9f, 0x006b, 0x0000, 0x0000, 0x0000, 0x038e, 0xc80c, 0x004d, 0x0000,
    0x0000, 0x0000, 0xfe26, 0x8384, 0x002e, 0x0000, 0x0000, 0x0000, 0xcd90, 0xca57,
    0x000e, 0x0000
};

void MacroAssembler::libm_reduce_pi04l(Register eax, Register ecx, Register edx, Register ebx, Register esi, Register edi, Register ebp, Register esp) {
  Label B1_1, B1_2, B1_3, B1_4, B1_5, B1_6, B1_7, B1_8, B1_9, B1_10, B1_11, B1_12;
  Label B1_13, B1_14, B1_15;

  assert_different_registers(ebx, eax, ecx, edx, esi, edi, ebp, esp);

  address zero_none = (address)_zero_none;
  address _4onpi_d = (address)__4onpi_d;
  address TWO_32H = (address)_TWO_32H;
  address pi04_3d = (address)_pi04_3d;
  address pi04_5d = (address)_pi04_5d;
  address SCALE = (address)_SCALE;
  address zeros = (address)_zeros;
  address pi04_2d = (address)_pi04_2d;
  address TWO_12H = (address)_TWO_12H;
  address _4onpi_31l = (address)__4onpi_31l;

  bind(B1_1);
  push(ebp);
  movl(ebp, esp);
  andl(esp, -16);
  push(esi);
  push(edi);
  push(ebx);
  subl(esp, 20);
  movzwl(ebx, Address(ebp, 16));
  andl(ebx, 32767);
  movl(eax, Address(ebp, 20));
  cmpl(ebx, 16413);
  movl(esi, Address(ebp, 24));
  movl(Address(esp, 4), eax);
  jcc(Assembler::greaterEqual, B1_8);

  bind(B1_2);
  fld_x(Address(ebp, 8));
  fld_d(ExternalAddress(_4onpi_d));    //0x6dc9c883UL, 0x3ff45f30UL
  fmul(1);
  fstp_x(Address(esp, 8));
  movzwl(ecx, Address(esp, 16));
  negl(ecx);
  addl(ecx, 30);
  movl(eax, Address(esp, 12));
  shrl(eax);
  cmpl(Address(esp, 4), 0);
  jcc(Assembler::notEqual, B1_4);

  bind(B1_3);
  lea(ecx, Address(eax, 1));
  andl(ecx, -2);
  jmp(B1_5);

  bind(B1_4);
  movl(ecx, eax);
  addl(eax, Address(esp, 4));
  movl(edx, eax);
  andl(edx, 1);
  addl(ecx, edx);

  bind(B1_5);
  fld_d(ExternalAddress(TWO_32H));    //0x00000000UL, 0x41f80000UL
  cmpl(ebx, 16400);
  movl(Address(esp, 0), ecx);
  fild_s(Address(esp, 0));
  jcc(Assembler::greaterEqual, B1_7);

  bind(B1_6);
  fld_d(ExternalAddress(pi04_3d));    //0x54442d00UL, 0x3fe921fbUL
  fmul(1);
  fsubp(3);
  fxch(1);
  fmul(2);
  fld_s(2);
  fadd(1);
  fsubrp(1);
  fld_s(0);
  fxch(1);
  fsuba(3);
  fld_d(ExternalAddress(8 + pi04_3d));    //0x98cc5180UL, 0x3ce84698UL
  fmul(3);
  fsuba(2);
  fxch(1);
  fsub(2);
  fsubrp(1);
  faddp(3);
  fld_d(ExternalAddress(16 + pi04_3d));    //0xcbb5bf6cUL, 0xb9dfc8f8UL
  fmulp(2);
  fld_s(1);
  fsubr(1);
  fsuba(1);
  fxch(2);
  fsubp(1);
  faddp(2);
  fxch(1);
  jmp(B1_15);

  bind(B1_7);
  fld_d(ExternalAddress(pi04_5d));    //0x54400000UL, 0x3fe921fbUL
  fmul(1);
  fsubp(3);
  fxch(1);
  fmul(2);
  fld_s(2);
  fadd(1);
  fsubrp(1);
  fld_s(0);
  fxch(1);
  fsuba(3);
  fld_d(ExternalAddress(8 + pi04_5d));    //0x1a600000UL, 0x3dc0b461UL
  fmul(3);
  fsuba(2);
  fxch(1);
  fsub(2);
  fsubrp(1);
  faddp(3);
  fld_d(ExternalAddress(16 + pi04_5d));    //0x2e000000UL, 0x3b93198aUL
  fmul(2);
  fld_s(0);
  fsubr(2);
  fsuba(2);
  fxch(1);
  fsubp(2);
  fxch(1);
  faddp(3);
  fld_d(ExternalAddress(24 + pi04_5d));    //0x25200000UL, 0x396b839aUL
  fmul(2);
  fld_s(0);
  fsubr(2);
  fsuba(2);
  fxch(1);
  fsubp(2);
  fxch(1);
  faddp(3);
  fld_d(ExternalAddress(32 + pi04_5d));    //0x533e63a0UL, 0x37027044UL
  fmulp(2);
  fld_s(1);
  fsubr(1);
  fsuba(1);
  fxch(2);
  fsubp(1);
  faddp(2);
  fxch(1);
  jmp(B1_15);

  bind(B1_8);
  fld_x(Address(ebp, 8));
  addl(ebx, -16417);
  fmul_d(as_Address(ExternalAddress(SCALE)));    //0x00000000UL, 0x32600000UL
  movl(eax, -2078209981);
  imull(ebx);
  addl(edx, ebx);
  movl(ecx, ebx);
  sarl(edx, 4);
  sarl(ecx, 31);
  subl(edx, ecx);
  movl(eax, edx);
  shll(eax, 5);
  fstp_x(Address(ebp, 8));
  fld_x(Address(ebp, 8));
  subl(eax, edx);
  movl(Address(ebp, 8), 0);
  subl(ebx, eax);
  fld_x(Address(ebp, 8));
  cmpl(ebx, 17);
  fsuba(1);
  jcc(Assembler::less, B1_10);

  bind(B1_9);
  lea(eax, Address(noreg, edx, Address::times_8));
  lea(ecx, Address(eax, edx, Address::times_4));
  incl(edx);
  fld_x(Address(_4onpi_31l, RelocationHolder::none).plus_disp(ecx, Address::times_1));
  fmul(2);
  fld_x(Address(12 + _4onpi_31l, RelocationHolder::none).plus_disp(ecx, Address::times_1));
  fmul(2);
  fld_s(0);
  fadd(2);
  fsuba(2);
  fxch(1);
  faddp(2);
  fld_s(1);
  fadd(1);
  fstp_x(Address(esp, 8));
  andl(Address(esp, 8), -16777216);
  fld_x(Address(esp, 8));
  fsubp(1);
  jmp(B1_11);

  bind(B1_10);
  fld_d(ExternalAddress(zeros));    //0x00000000UL, 0x00000000UL
  fld_s(0);

  bind(B1_11);
  fld_s(0);
  lea(eax, Address(noreg, edx, Address::times_8));
  fld_s(3);
  lea(edx, Address(eax, edx, Address::times_4));
  fld_x(Address(_4onpi_31l, RelocationHolder::none).plus_disp(edx, Address::times_1));
  fmul(6);
  movl(Address(esp, 0), edx);
  fadda(2);
  fxch(2);
  fsuba(3);
  fxch(2);
  faddp(3);
  fxch(2);
  faddp(3);
  fld_x(Address(12 + _4onpi_31l, RelocationHolder::none).plus_disp(edx, Address::times_1));
  fmula(2);
  fld_s(2);
  fadd(2);
  fld_s(0);
  fxch(1);
  fsubra(3);
  fxch(3);
  fchs();
  faddp(4);
  fxch(3);
  faddp(4);
  fxch(2);
  fadd(3);
  fxch(2);
  fmul(5);
  fadda(2);
  fld_s(4);
  fld_x(Address(24 + _4onpi_31l, RelocationHolder::none).plus_disp(edx, Address::times_1));
  fmula(1);
  fxch(1);
  fadda(4);
  fxch(4);
  fstp_x(Address(esp, 8));
  movzwl(ebx, Address(esp, 16));
  andl(ebx, 32767);
  cmpl(ebx, 16415);
  jcc(Assembler::greaterEqual, B1_13);

  bind(B1_12);
  negl(ebx);
  addl(ebx, 30);
  movl(ecx, ebx);
  movl(eax, Address(esp, 12));
  shrl(eax);
  shll(eax);
  movl(Address(esp, 12), eax);
  movl(Address(esp, 8), 0);
  shrl(eax);
  jmp(B1_14);

  bind(B1_13);
  negl(ebx);
  addl(ebx, 30);
  movl(ecx, ebx);
  movl(edx, Address(esp, 8));
  shrl(edx);
  shll(edx);
  negl(ecx);
  movl(eax, Address(esp, 12));
  shll(eax);
  movl(ecx, ebx);
  movl(Address(esp, 8), edx);
  shrl(edx);
  orl(eax, edx);

  bind(B1_14);
  fld_x(Address(esp, 8));
  addl(eax, Address(esp, 4));
  fsubp(3);
  fmul(6);
  fld_s(4);
  movl(edx, eax);
  andl(edx, 1);
  fadd(3);
  movl(ecx, Address(esp, 0));
  fsuba(3);
  fxch(3);
  faddp(5);
  fld_s(1);
  fxch(3);
  fadd_d(Address(zero_none, RelocationHolder::none).plus_disp(edx, Address::times_8));
  fadda(3);
  fsub(3);
  faddp(2);
  fxch(1);
  faddp(4);
  fld_s(2);
  fadd(2);
  fsuba(2);
  fxch(3);
  faddp(2);
  fxch(1);
  faddp(3);
  fld_s(0);
  fadd(2);
  fsuba(2);
  fxch(1);
  faddp(2);
  fxch(1);
  faddp(2);
  fld_s(2);
  fld_x(Address(36 + _4onpi_31l, RelocationHolder::none).plus_disp(ecx, Address::times_1));
  fmula(1);
  fld_s(1);
  fadd(3);
  fsuba(3);
  fxch(2);
  faddp(3);
  fxch(2);
  faddp(3);
  fxch(1);
  fmul(4);
  fld_s(0);
  fadd(2);
  fsuba(2);
  fxch(1);
  faddp(2);
  fxch(1);
  faddp(2);
  fld_s(2);
  fld_x(Address(48 + _4onpi_31l, RelocationHolder::none).plus_disp(ecx, Address::times_1));
  fmula(1);
  fld_s(1);
  fadd(3);
  fsuba(3);
  fxch(2);
  faddp(3);
  fxch(2);
  faddp(3);
  fld_s(3);
  fxch(2);
  fmul(5);
  fld_x(Address(60 + _4onpi_31l, RelocationHolder::none).plus_disp(ecx, Address::times_1));
  fmula(3);
  fxch(3);
  faddp(1);
  fld_s(0);
  fadd(2);
  fsuba(2);
  fxch(1);
  faddp(2);
  fxch(1);
  faddp(3);
  fld_s(3);
  fxch(2);
  fmul(5);
  fld_x(Address(72 + _4onpi_31l, RelocationHolder::none).plus_disp(ecx, Address::times_1));
  fmula(3);
  fxch(3);
  faddp(1);
  fld_s(0);
  fadd(2);
  fsuba(2);
  fxch(1);
  faddp(2);
  fxch(1);
  faddp(3);
  fxch(1);
  fmulp(4);
  fld_x(Address(84 + _4onpi_31l, RelocationHolder::none).plus_disp(ecx, Address::times_1));
  fmulp(3);
  fxch(2);
  faddp(3);
  fld_s(2);
  fadd(2);
  fld_d(ExternalAddress(TWO_32H));    //0x00000000UL, 0x41f80000UL
  fmul(1);
  fadda(1);
  fsubp(1);
  fsuba(2);
  fxch(3);
  faddp(2);
  faddp(1);
  fld_d(ExternalAddress(pi04_2d));    //0x54400000UL, 0x3fe921fbUL
  fld_s(0);
  fmul(2);
  fxch(2);
  fadd(3);
  fxch(1);
  fmulp(3);
  fmul_d(as_Address(ExternalAddress(8 + pi04_2d)));    //0x1a626331UL, 0x3dc0b461UL
  faddp(1);

  bind(B1_15);
  fld_d(ExternalAddress(TWO_12H));    //0x00000000UL, 0x40b80000UL
  fld_s(2);
  fadd(2);
  fmula(1);
  fstp_x(Address(esp, 8));
  fld_x(Address(esp, 8));
  fadd(1);
  fsubrp(1);
  fst_d(Address(esi, 0));
  fsubp(2);
  faddp(1);
  fstp_d(Address(esi, 8));
  addl(esp, 20);
  pop(ebx);
  pop(edi);
  pop(esi);
  movl(esp, ebp);
  pop(ebp);
  ret(0);
}

ALIGNED_(16) juint _L_2il0floatpacket_0[] =
{
    0xffffffffUL, 0x7fffffffUL, 0x00000000UL, 0x00000000UL
};

ALIGNED_(16) juint _Pi4Inv[] =
{
    0x6dc9c883UL, 0x3ff45f30UL
};

ALIGNED_(16) juint _Pi4x3[] =
{
    0x54443000UL, 0xbfe921fbUL, 0x3b39a000UL, 0x3d373dcbUL, 0xe0e68948UL,
    0xba845c06UL
};

ALIGNED_(16) juint _Pi4x4[] =
{
    0x54400000UL, 0xbfe921fbUL, 0x1a600000UL, 0xbdc0b461UL, 0x2e000000UL,
    0xbb93198aUL, 0x252049c1UL, 0xb96b839aUL
};

ALIGNED_(16) jushort _SP[] =
{
    0xaaab, 0xaaaa, 0xaaaa, 0xaaaa, 0xbffc, 0x0000, 0x8887, 0x8888, 0x8888, 0x8888,
    0x3ff8, 0x0000, 0xc527, 0x0d00, 0x00d0, 0xd00d, 0xbff2, 0x0000, 0x45f6, 0xb616,
    0x1d2a, 0xb8ef, 0x3fec, 0x0000, 0x825b, 0x3997, 0x2b3f, 0xd732, 0xbfe5, 0x0000,
    0xbf33, 0x8bb4, 0x2fda, 0xb092, 0x3fde, 0x0000, 0x44a6, 0xed1a, 0x29ef, 0xd73e,
    0xbfd6, 0x0000, 0x8610, 0x307f, 0x62a1, 0xc921, 0x3fce, 0x0000
};

ALIGNED_(16) jushort _CP[] =
{
    0x0000, 0x0000, 0x0000, 0x8000, 0xbffe, 0x0000, 0xaaa5, 0xaaaa, 0xaaaa, 0xaaaa,
    0x3ffa, 0x0000, 0x9c2f, 0x0b60, 0x60b6, 0xb60b, 0xbff5, 0x0000, 0xf024, 0x0cac,
    0x00d0, 0xd00d, 0x3fef, 0x0000, 0x03fe, 0x3f65, 0x7dbb, 0x93f2, 0xbfe9, 0x0000,
    0xd84d, 0xadee, 0xc698, 0x8f76, 0x3fe2, 0x0000, 0xdaba, 0xfe79, 0xea36, 0xc9c9,
    0xbfda, 0x0000, 0x3ac6, 0x0ba0, 0x07ce, 0xd585, 0x3fd2, 0x0000
};

ALIGNED_(16) juint _ones[] =
{
    0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0xbff00000UL
};

void MacroAssembler::libm_sincos_huge(XMMRegister xmm0, XMMRegister xmm1, Register eax, Register ecx, Register edx, Register ebx, Register esi, Register edi, Register ebp, Register esp) {
  Label B1_1, B1_2, B1_3, B1_4, B1_5, B1_6, B1_7, B1_8, B1_9, B1_10, B1_11, B1_12;
  Label B1_13, B1_14, B1_15, B1_16, B1_17, B1_18, B1_19, B1_20, B1_21, B1_22, B1_23;
  Label B1_24, B1_25, B1_26, B1_27, B1_28, B1_29, B1_30, B1_31, B1_32, B1_33, B1_34;
  Label B1_35, B1_36, B1_37, B1_38, B1_39, B1_40, B1_41, B1_42, B1_43, B1_44, B1_45, B1_46;

  assert_different_registers(ebx, eax, ecx, edx, esi, edi, ebp, esp);

  address L_2il0floatpacket_0 = (address)_L_2il0floatpacket_0;
  address Pi4Inv = (address)_Pi4Inv;
  address Pi4x3 = (address)_Pi4x3;
  address Pi4x4 = (address)_Pi4x4;
  address ones = (address)_ones;
  address CP = (address)_CP;
  address SP = (address)_SP;

  bind(B1_1);
  push(ebp);
  movl(ebp, esp);
  andl(esp, -64);
  push(esi);
  push(edi);
  push(ebx);
  subl(esp, 52);
  movl(eax, Address(ebp, 16));
  movl(edx, Address(ebp, 20));
  movl(Address(esp, 32), eax);
  movl(Address(esp, 36), edx);

  bind(B1_2);
  fnstcw(Address(esp, 30));

  bind(B1_3);
  movsd(xmm1, Address(ebp, 8));
  movl(esi, Address(ebp, 12));
  movl(eax, esi);
  andl(eax, 2147483647);
  andps(xmm1, ExternalAddress(L_2il0floatpacket_0));    //0xffffffffUL, 0x7fffffffUL, 0x00000000UL, 0x00000000UL
  shrl(esi, 31);
  movl(Address(esp, 40), eax);
  cmpl(eax, 1104150528);
  movsd(Address(ebp, 8), xmm1);
  jcc(Assembler::aboveEqual, B1_11);

  bind(B1_4);
  movsd(xmm0, ExternalAddress(Pi4Inv));    //0x6dc9c883UL, 0x3ff45f30UL
  mulsd(xmm0, xmm1);
  movzwl(edx, Address(esp, 30));
  movl(eax, edx);
  andl(eax, 768);
  movsd(Address(esp, 0), xmm0);
  cmpl(eax, 768);
  jcc(Assembler::equal, B1_42);

  bind(B1_5);
  orl(edx, -64768);
  movw(Address(esp, 28), edx);

  bind(B1_6);
  fldcw(Address(esp, 28));

  bind(B1_7);
  movsd(xmm1, Address(ebp, 8));
  movl(ebx, 1);

  bind(B1_8);
  movl(Address(esp, 12), ebx);
  movl(ebx, Address(esp, 4));
  movl(eax, ebx);
  movl(Address(esp, 8), esi);
  movl(esi, ebx);
  shrl(esi, 20);
  andl(eax, 1048575);
  movl(ecx, esi);
  orl(eax, 1048576);
  negl(ecx);
  movl(edx, eax);
  addl(ecx, 19);
  addl(esi, 13);
  movl(Address(esp, 24), ecx);
  shrl(edx);
  movl(ecx, esi);
  shll(eax);
  movl(ecx, Address(esp, 24));
  movl(esi, Address(esp, 0));
  shrl(esi);
  orl(eax, esi);
  cmpl(ebx, 1094713344);
  movsd(Address(esp, 16), xmm1);
  fld_d(Address(esp, 16));
  cmov32(Assembler::below, eax, edx);
  movl(esi, Address(esp, 8));
  lea(edx, Address(eax, 1));
  movl(ebx, edx);
  andl(ebx, -2);
  movl(Address(esp, 16), ebx);
  fild_s(Address(esp, 16));
  movl(ebx, Address(esp, 12));
  cmpl(Address(esp, 40), 1094713344);
  jcc(Assembler::aboveEqual, B1_10);

  bind(B1_9);
  fld_d(ExternalAddress(Pi4x3));    //0x54443000UL, 0xbfe921fbUL
  fmul(1);
  faddp(2);
  fld_d(ExternalAddress(8 + Pi4x3));    //0x3b39a000UL, 0x3d373dcbUL
  fmul(1);
  faddp(2);
  fld_d(ExternalAddress(16 + Pi4x3));    //0xe0e68948UL, 0xba845c06UL
  fmulp(1);
  faddp(1);
  jmp(B1_17);

  bind(B1_10);
  fld_d(ExternalAddress(Pi4x4));    //0x54400000UL, 0xbfe921fbUL
  fmul(1);
  faddp(2);
  fld_d(ExternalAddress(8 + Pi4x4));    //0x1a600000UL, 0xbdc0b461UL
  fmul(1);
  faddp(2);
  fld_d(ExternalAddress(16 + Pi4x4));    //0x2e000000UL, 0xbb93198aUL
  fmul(1);
  faddp(2);
  fld_d(ExternalAddress(24 + Pi4x4));    //0x252049c1UL, 0xb96b839aUL
  fmulp(1);
  faddp(1);
  jmp(B1_17);

  bind(B1_11);
  movzwl(edx, Address(esp, 30));
  movl(eax, edx);
  andl(eax, 768);
  cmpl(eax, 768);
  jcc(Assembler::equal, B1_43);
  bind(B1_12);
  orl(edx, -64768);
  movw(Address(esp, 28), edx);

  bind(B1_13);
  fldcw(Address(esp, 28));

  bind(B1_14);
  movsd(xmm1, Address(ebp, 8));
  movl(ebx, 1);

  bind(B1_15);
  movsd(Address(esp, 16), xmm1);
  fld_d(Address(esp, 16));
  addl(esp, -32);
  lea(eax, Address(esp, 32));
  fstp_x(Address(esp, 0));
  movl(Address(esp, 12), 0);
  movl(Address(esp, 16), eax);
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::dlibm_reduce_pi04l())));

  bind(B1_46);
  addl(esp, 32);

  bind(B1_16);
  fld_d(Address(esp, 0));
  lea(edx, Address(eax, 1));
  fld_d(Address(esp, 8));
  faddp(1);

  bind(B1_17);
  movl(ecx, edx);
  addl(eax, 3);
  shrl(ecx, 2);
  andl(ecx, 1);
  shrl(eax, 2);
  xorl(esi, ecx);
  movl(ecx, Address(esp, 36));
  andl(eax, 1);
  andl(ecx, 3);
  cmpl(ecx, 3);
  jcc(Assembler::notEqual, B1_25);

  bind(B1_18);
  fld_x(ExternalAddress(84 + SP));    //0x8610, 0x307f, 0x62
  fld_s(1);
  fmul((2));
  testb(edx, 2);
  fmula((1));
  fld_x(ExternalAddress(72 + SP));    //0x44a6, 0xed1a, 0x29
  faddp(2);
  fmula(1);
  fld_x(ExternalAddress(60 + SP));    //0xbf33, 0x8bb4, 0x2f
  faddp(2);
  fmula(1);
  fld_x(ExternalAddress(48 + SP));    //0x825b, 0x3997, 0x2b
  faddp(2);
  fmula(1);
  fld_x(ExternalAddress(36 + SP));    //0x45f6, 0xb616, 0x1d
  faddp(2);
  fmula(1);
  fld_x(ExternalAddress(24 + SP));    //0xc527, 0x0d00, 0x00
  faddp(2);
  fmula(1);
  fld_x(ExternalAddress(12 + SP));    //0x8887, 0x8888, 0x88
  faddp(2);
  fmula(1);
  fld_x(ExternalAddress(SP));    //0xaaab, 0xaaaa, 0xaa
  faddp(2);
  fmula(1);
  fld_x(ExternalAddress(84 + CP));    //0x3ac6, 0x0ba0, 0x07
  fmul(1);
  fld_x(ExternalAddress(72 + CP));    //0xdaba, 0xfe79, 0xea
  faddp(1);
  fmul(1);
  fld_x(ExternalAddress(62 + CP));    //0xd84d, 0xadee, 0xc6
  faddp(1);
  fmul(1);
  fld_x(ExternalAddress(48 + CP));    //0x03fe, 0x3f65, 0x7d
  faddp(1);
  fmul(1);
  fld_x(ExternalAddress(36 + CP));    //0xf024, 0x0cac, 0x00
  faddp(1);
  fmul(1);
  fld_x(ExternalAddress(24 + CP));    //0x9c2f, 0x0b60, 0x60
  faddp(1);
  fmul(1);
  fld_x(ExternalAddress(12 + CP));    //0xaaa5, 0xaaaa, 0xaa
  faddp(1);
  fmul(1);
  fld_x(ExternalAddress(CP));    //0x0000, 0x0000, 0x00
  faddp(1);
  fmulp(1);
  fld_d(Address(ones, RelocationHolder::none).plus_disp(esi, Address::times_8));
  fld_d(Address(ones, RelocationHolder::none).plus_disp(eax, Address::times_8));
  jcc(Assembler::equal, B1_22);

  bind(B1_19);
  fmulp(4);
  testl(ebx, ebx);
  fxch(2);
  fmul(3);
  movl(eax, Address(esp, 2));
  faddp(3);
  fxch(2);
  fstp_d(Address(eax, 0));
  fmula(1);
  faddp(1);
  fstp_d(Address(eax, 8));
  jcc(Assembler::equal, B1_21);

  bind(B1_20);
  fldcw(Address(esp, 30));

  bind(B1_21);
  addl(esp, 52);
  pop(ebx);
  pop(edi);
  pop(esi);
  movl(esp, ebp);
  pop(ebp);
  ret(0);

  bind(B1_22);
  fxch(1);
  fmulp(4);
  testl(ebx, ebx);
  fxch(2);
  fmul(3);
  movl(eax, Address(esp, 32));
  faddp(3);
  fxch(2);
  fstp_d(Address(eax, 8));
  fmula(1);
  faddp(1);
  fstp_d(Address(eax, 0));
  jcc(Assembler::equal, B1_24);

  bind(B1_23);
  fldcw(Address(esp, 30));

  bind(B1_24);
  addl(esp, 52);
  pop(ebx);
  pop(edi);
  pop(esi);
  movl(esp, ebp);
  pop(ebp);
  ret(0);

  bind(B1_25);
  testb(Address(esp, 36), 2);
  jcc(Assembler::equal, B1_33);

  bind(B1_26);
  fld_s(0);
  testb(edx, 2);
  fmul(1);
  fld_s(0);
  fmul(1);
  jcc(Assembler::equal, B1_30);

  bind(B1_27);
  fstp_d(2);
  fld_x(ExternalAddress(84 + CP));    //0x3ac6, 0x0ba0, 0x07
  testl(ebx, ebx);
  fmul(2);
  fld_x(ExternalAddress(72 + CP));    //0xdaba, 0xfe79, 0xea
  fmul(3);
  fld_x(ExternalAddress(60 + CP));    //0xd84d, 0xadee, 0xc6
  movl(eax, Address(rsp, 32));
  faddp(2);
  fxch(1);
  fmul(3);
  fld_x(ExternalAddress(48 + CP));    //0x03fe, 0x3f65, 0x7d
  faddp(2);
  fxch(1);
  fmul(3);
  fld_x(ExternalAddress(36 + CP));    //0xf024, 0x0cac, 0x00
  faddp(2);
  fxch(1);
  fmul(3);
  fld_x(ExternalAddress(24 + CP));    //0x9c2f, 0x0b60, 0x60
  faddp(2);
  fxch(1);
  fmul(3);
  fld_x(ExternalAddress(12 + CP));    //0xaaa5, 0xaaaa, 0xaa
  faddp(2);
  fxch(1);
  fmulp(3);
  fld_x(ExternalAddress(CP));    //0x0000, 0x0000, 0x00
  faddp(1);
  fmulp(1);
  faddp(1);
  fld_d(Address(ones, RelocationHolder::none).plus_disp(rsi, Address::times_8));
  fmula(1);
  faddp(1);
  fstp_d(Address(eax, 8));
  jcc(Assembler::equal, B1_29);

  bind(B1_28);
  fldcw(Address(esp, 30));

  bind(B1_29);
  addl(esp, 52);
  pop(ebx);
  pop(edi);
  pop(esi);
  movl(esp, ebp);
  pop(ebp);
  ret(0);

  bind(B1_30);
  fld_x(ExternalAddress(84 + SP));    //0x8610, 0x307f, 0x62
  testl(ebx, ebx);
  fmul(1);
  fld_x(ExternalAddress(72 + SP));    //0x44a6, 0xed1a, 0x29
  fmul(2);
  fld_x(ExternalAddress(60 + SP));    //0xbf33, 0x8bb4, 0x2f
  movl(eax, Address(rsp, 32));
  faddp(2);
  fxch(1);
  fmul(2);
  fld_x(ExternalAddress(48 + SP));    //0x825b, 0x3997, 0x2b
  faddp(2);
  fxch(1);
  fmul(2);
  fld_x(ExternalAddress(36 + SP));    //0x45f6, 0xb616, 0x1d
  faddp(2);
  fxch(1);
  fmul(2);
  fld_x(ExternalAddress(24 + SP));    //0xc527, 0x0d00, 0x00
  faddp(2);
  fxch(1);
  fmul(2);
  fld_x(ExternalAddress(12 + SP));    //0x8887, 0x8888, 0x88
  faddp(2);
  fxch(1);
  fmulp(2);
  fld_x(ExternalAddress(SP));    //0xaaab, 0xaaaa, 0xaa
  faddp(1);
  fmulp(2);
  faddp(1);
  fld_d(Address(ones, RelocationHolder::none).plus_disp(rsi, Address::times_8));
  fmulp(2);
  fmul(1);
  faddp(1);
  fstp_d(Address(eax, 8));
  jcc(Assembler::equal, B1_32);

  bind(B1_31);
  fldcw(Address(esp, 30));

  bind(B1_32);
  addl(esp, 52);
  pop(ebx);
  pop(edi);
  pop(esi);
  movl(esp, ebp);
  pop(ebp);
  ret(0);

  bind(B1_33);
  testb(Address(esp, 36), 1);
  jcc(Assembler::equal, B1_41);

  bind(B1_34);
  fld_s(0);
  testb(edx, 2);
  fmul(1);
  fld_s(0);
  fmul(1);
  jcc(Assembler::equal, B1_38);

  bind(B1_35);
  fld_x(ExternalAddress(84 + SP));    //0x8610, 0x307f, 0x62
  testl(ebx, ebx);
  fmul(1);
  fld_x(ExternalAddress(72 + SP));    //0x44a6, 0xed1a, 0x29
  fmul(2);
  fld_x(ExternalAddress(60 + SP));    //0xbf33, 0x8bb4, 0x2f
  faddp(2);
  fxch(1);
  fmul(2);
  fld_x(ExternalAddress(48 + SP));    //0x825b, 0x3997, 0x2b
  faddp(2);
  fxch(1);
  fmul(2);
  fld_x(ExternalAddress(36 + SP));    //0x45f6, 0xb616, 0x1d
  faddp(2);
  fxch(1);
  fmul(2);
  fld_x(ExternalAddress(24 + SP));    //0xc527, 0x0d00, 0x00
  faddp(2);
  fxch(1);
  fmul(2);
  fld_x(ExternalAddress(12 + SP));    //0x8887, 0x8888, 0x88
  faddp(2);
  fxch(1);
  fmulp(2);
  fld_x(ExternalAddress(SP));    //0xaaab, 0xaaaa, 0xaa
  faddp(1);
  fmulp(2);
  faddp(1);
  fld_d(Address(ones, RelocationHolder::none).plus_disp(eax, Address::times_8));
  fmulp(2);
  fmul(1);
  movl(eax, Address(esp, 32));
  faddp(1);
  fstp_d(Address(eax, 0));
  jcc(Assembler::equal, B1_37);

  bind(B1_36);
  fldcw(Address(esp, 30));

  bind(B1_37);
  addl(esp, 52);
  pop(ebx);
  pop(edi);
  pop(esi);
  movl(esp, ebp);
  pop(ebp);
  ret(0);

  bind(B1_38);
  fstp_d(2);
  fld_x(ExternalAddress(84 + CP));    //0x3ac6, 0x0ba0, 0x07
  testl(ebx, ebx);
  fmul(2);
  fld_x(ExternalAddress(72 + CP));    //0xdaba, 0xfe79, 0xea
  fmul(3);
  fld_x(ExternalAddress(60 + CP));    //0xd84d, 0xadee, 0xc6
  faddp(2);
  fxch(1);
  fmul(3);
  fld_x(ExternalAddress(48 + CP));    //0x03fe, 0x3f65, 0x7d
  faddp(2);
  fxch(1);
  fmul(3);
  fld_x(ExternalAddress(36 + CP));    //0xf024, 0x0cac, 0x00
  faddp(2);
  fxch(1);
  fmul(3);
  fld_x(ExternalAddress(24 + CP));    //0x9c2f, 0x0b60, 0x60
  faddp(2);
  fxch(1);
  fmul(3);
  fld_x(ExternalAddress(12 + CP));    //0xaaa5, 0xaaaa, 0xaa
  faddp(2);
  fxch(1);
  fmulp(3);
  fld_x(ExternalAddress(CP));    //0x0000, 0x0000, 0x00
  faddp(1);
  fmulp(1);
  faddp(1);
  fld_d(Address(ones, RelocationHolder::none).plus_disp(eax, Address::times_8));
  fmula(1);
  movl(eax, Address(esp, 32));
  faddp(1);
  fstp_d(Address(eax, 0));
  jcc(Assembler::equal, B1_40);

  bind(B1_39);
  fldcw(Address(esp, 30));
  bind(B1_40);
  addl(esp, 52);
  pop(ebx);
  pop(edi);
  pop(esi);
  movl(esp, ebp);
  pop(ebp);
  ret(0);
  bind(B1_41);
  fstp_d(0);
  addl(esp, 52);
  pop(ebx);
  pop(edi);
  pop(esi);
  movl(esp, ebp);
  pop(ebp);
  ret(0);
  bind(B1_42);
  xorl(ebx, ebx);
  jmp(B1_8);
  bind(B1_43);
  xorl(ebx, ebx);
  jmp(B1_15);
}

ALIGNED_(16) juint _static_const_table_sin[] =
{
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x3ff00000UL, 0x176d6d31UL, 0xbf73b92eUL,
    0xbc29b42cUL, 0x3fb917a6UL, 0xe0000000UL, 0xbc3e2718UL, 0x00000000UL,
    0x3ff00000UL, 0x011469fbUL, 0xbf93ad06UL, 0x3c69a60bUL, 0x3fc8f8b8UL,
    0xc0000000UL, 0xbc626d19UL, 0x00000000UL, 0x3ff00000UL, 0x939d225aUL,
    0xbfa60beaUL, 0x2ed59f06UL, 0x3fd29406UL, 0xa0000000UL, 0xbc75d28dUL,
    0x00000000UL, 0x3ff00000UL, 0x866b95cfUL, 0xbfb37ca1UL, 0xa6aea963UL,
    0x3fd87de2UL, 0xe0000000UL, 0xbc672cedUL, 0x00000000UL, 0x3ff00000UL,
    0x73fa1279UL, 0xbfbe3a68UL, 0x3806f63bUL, 0x3fde2b5dUL, 0x20000000UL,
    0x3c5e0d89UL, 0x00000000UL, 0x3ff00000UL, 0x5bc57974UL, 0xbfc59267UL,
    0x39ae68c8UL, 0x3fe1c73bUL, 0x20000000UL, 0x3c8b25ddUL, 0x00000000UL,
    0x3ff00000UL, 0x53aba2fdUL, 0xbfcd0dfeUL, 0x25091dd6UL, 0x3fe44cf3UL,
    0x20000000UL, 0x3c68076aUL, 0x00000000UL, 0x3ff00000UL, 0x99fcef32UL,
    0x3fca8279UL, 0x667f3bcdUL, 0x3fe6a09eUL, 0x20000000UL, 0xbc8bdd34UL,
    0x00000000UL, 0x3fe00000UL, 0x94247758UL, 0x3fc133ccUL, 0x6b151741UL,
    0x3fe8bc80UL, 0x20000000UL, 0xbc82c5e1UL, 0x00000000UL, 0x3fe00000UL,
    0x9ae68c87UL, 0x3fac73b3UL, 0x290ea1a3UL, 0x3fea9b66UL, 0xe0000000UL,
    0x3c39f630UL, 0x00000000UL, 0x3fe00000UL, 0x7f909c4eUL, 0xbf9d4a2cUL,
    0xf180bdb1UL, 0x3fec38b2UL, 0x80000000UL, 0xbc76e0b1UL, 0x00000000UL,
    0x3fe00000UL, 0x65455a75UL, 0xbfbe0875UL, 0xcf328d46UL, 0x3fed906bUL,
    0x20000000UL, 0x3c7457e6UL, 0x00000000UL, 0x3fe00000UL, 0x76acf82dUL,
    0x3fa4a031UL, 0x56c62ddaUL, 0x3fee9f41UL, 0xe0000000UL, 0x3c8760b1UL,
    0x00000000UL, 0x3fd00000UL, 0x0e5967d5UL, 0xbfac1d1fUL, 0xcff75cb0UL,
    0x3fef6297UL, 0x20000000UL, 0x3c756217UL, 0x00000000UL, 0x3fd00000UL,
    0x0f592f50UL, 0xbf9ba165UL, 0xa3d12526UL, 0x3fefd88dUL, 0x40000000UL,
    0xbc887df6UL, 0x00000000UL, 0x3fc00000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x0f592f50UL, 0x3f9ba165UL, 0xa3d12526UL, 0x3fefd88dUL,
    0x40000000UL, 0xbc887df6UL, 0x00000000UL, 0xbfc00000UL, 0x0e5967d5UL,
    0x3fac1d1fUL, 0xcff75cb0UL, 0x3fef6297UL, 0x20000000UL, 0x3c756217UL,
    0x00000000UL, 0xbfd00000UL, 0x76acf82dUL, 0xbfa4a031UL, 0x56c62ddaUL,
    0x3fee9f41UL, 0xe0000000UL, 0x3c8760b1UL, 0x00000000UL, 0xbfd00000UL,
    0x65455a75UL, 0x3fbe0875UL, 0xcf328d46UL, 0x3fed906bUL, 0x20000000UL,
    0x3c7457e6UL, 0x00000000UL, 0xbfe00000UL, 0x7f909c4eUL, 0x3f9d4a2cUL,
    0xf180bdb1UL, 0x3fec38b2UL, 0x80000000UL, 0xbc76e0b1UL, 0x00000000UL,
    0xbfe00000UL, 0x9ae68c87UL, 0xbfac73b3UL, 0x290ea1a3UL, 0x3fea9b66UL,
    0xe0000000UL, 0x3c39f630UL, 0x00000000UL, 0xbfe00000UL, 0x94247758UL,
    0xbfc133ccUL, 0x6b151741UL, 0x3fe8bc80UL, 0x20000000UL, 0xbc82c5e1UL,
    0x00000000UL, 0xbfe00000UL, 0x99fcef32UL, 0xbfca8279UL, 0x667f3bcdUL,
    0x3fe6a09eUL, 0x20000000UL, 0xbc8bdd34UL, 0x00000000UL, 0xbfe00000UL,
    0x53aba2fdUL, 0x3fcd0dfeUL, 0x25091dd6UL, 0x3fe44cf3UL, 0x20000000UL,
    0x3c68076aUL, 0x00000000UL, 0xbff00000UL, 0x5bc57974UL, 0x3fc59267UL,
    0x39ae68c8UL, 0x3fe1c73bUL, 0x20000000UL, 0x3c8b25ddUL, 0x00000000UL,
    0xbff00000UL, 0x73fa1279UL, 0x3fbe3a68UL, 0x3806f63bUL, 0x3fde2b5dUL,
    0x20000000UL, 0x3c5e0d89UL, 0x00000000UL, 0xbff00000UL, 0x866b95cfUL,
    0x3fb37ca1UL, 0xa6aea963UL, 0x3fd87de2UL, 0xe0000000UL, 0xbc672cedUL,
    0x00000000UL, 0xbff00000UL, 0x939d225aUL, 0x3fa60beaUL, 0x2ed59f06UL,
    0x3fd29406UL, 0xa0000000UL, 0xbc75d28dUL, 0x00000000UL, 0xbff00000UL,
    0x011469fbUL, 0x3f93ad06UL, 0x3c69a60bUL, 0x3fc8f8b8UL, 0xc0000000UL,
    0xbc626d19UL, 0x00000000UL, 0xbff00000UL, 0x176d6d31UL, 0x3f73b92eUL,
    0xbc29b42cUL, 0x3fb917a6UL, 0xe0000000UL, 0xbc3e2718UL, 0x00000000UL,
    0xbff00000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0xbff00000UL, 0x176d6d31UL,
    0x3f73b92eUL, 0xbc29b42cUL, 0xbfb917a6UL, 0xe0000000UL, 0x3c3e2718UL,
    0x00000000UL, 0xbff00000UL, 0x011469fbUL, 0x3f93ad06UL, 0x3c69a60bUL,
    0xbfc8f8b8UL, 0xc0000000UL, 0x3c626d19UL, 0x00000000UL, 0xbff00000UL,
    0x939d225aUL, 0x3fa60beaUL, 0x2ed59f06UL, 0xbfd29406UL, 0xa0000000UL,
    0x3c75d28dUL, 0x00000000UL, 0xbff00000UL, 0x866b95cfUL, 0x3fb37ca1UL,
    0xa6aea963UL, 0xbfd87de2UL, 0xe0000000UL, 0x3c672cedUL, 0x00000000UL,
    0xbff00000UL, 0x73fa1279UL, 0x3fbe3a68UL, 0x3806f63bUL, 0xbfde2b5dUL,
    0x20000000UL, 0xbc5e0d89UL, 0x00000000UL, 0xbff00000UL, 0x5bc57974UL,
    0x3fc59267UL, 0x39ae68c8UL, 0xbfe1c73bUL, 0x20000000UL, 0xbc8b25ddUL,
    0x00000000UL, 0xbff00000UL, 0x53aba2fdUL, 0x3fcd0dfeUL, 0x25091dd6UL,
    0xbfe44cf3UL, 0x20000000UL, 0xbc68076aUL, 0x00000000UL, 0xbff00000UL,
    0x99fcef32UL, 0xbfca8279UL, 0x667f3bcdUL, 0xbfe6a09eUL, 0x20000000UL,
    0x3c8bdd34UL, 0x00000000UL, 0xbfe00000UL, 0x94247758UL, 0xbfc133ccUL,
    0x6b151741UL, 0xbfe8bc80UL, 0x20000000UL, 0x3c82c5e1UL, 0x00000000UL,
    0xbfe00000UL, 0x9ae68c87UL, 0xbfac73b3UL, 0x290ea1a3UL, 0xbfea9b66UL,
    0xe0000000UL, 0xbc39f630UL, 0x00000000UL, 0xbfe00000UL, 0x7f909c4eUL,
    0x3f9d4a2cUL, 0xf180bdb1UL, 0xbfec38b2UL, 0x80000000UL, 0x3c76e0b1UL,
    0x00000000UL, 0xbfe00000UL, 0x65455a75UL, 0x3fbe0875UL, 0xcf328d46UL,
    0xbfed906bUL, 0x20000000UL, 0xbc7457e6UL, 0x00000000UL, 0xbfe00000UL,
    0x76acf82dUL, 0xbfa4a031UL, 0x56c62ddaUL, 0xbfee9f41UL, 0xe0000000UL,
    0xbc8760b1UL, 0x00000000UL, 0xbfd00000UL, 0x0e5967d5UL, 0x3fac1d1fUL,
    0xcff75cb0UL, 0xbfef6297UL, 0x20000000UL, 0xbc756217UL, 0x00000000UL,
    0xbfd00000UL, 0x0f592f50UL, 0x3f9ba165UL, 0xa3d12526UL, 0xbfefd88dUL,
    0x40000000UL, 0x3c887df6UL, 0x00000000UL, 0xbfc00000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0xbff00000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x0f592f50UL, 0xbf9ba165UL, 0xa3d12526UL,
    0xbfefd88dUL, 0x40000000UL, 0x3c887df6UL, 0x00000000UL, 0x3fc00000UL,
    0x0e5967d5UL, 0xbfac1d1fUL, 0xcff75cb0UL, 0xbfef6297UL, 0x20000000UL,
    0xbc756217UL, 0x00000000UL, 0x3fd00000UL, 0x76acf82dUL, 0x3fa4a031UL,
    0x56c62ddaUL, 0xbfee9f41UL, 0xe0000000UL, 0xbc8760b1UL, 0x00000000UL,
    0x3fd00000UL, 0x65455a75UL, 0xbfbe0875UL, 0xcf328d46UL, 0xbfed906bUL,
    0x20000000UL, 0xbc7457e6UL, 0x00000000UL, 0x3fe00000UL, 0x7f909c4eUL,
    0xbf9d4a2cUL, 0xf180bdb1UL, 0xbfec38b2UL, 0x80000000UL, 0x3c76e0b1UL,
    0x00000000UL, 0x3fe00000UL, 0x9ae68c87UL, 0x3fac73b3UL, 0x290ea1a3UL,
    0xbfea9b66UL, 0xe0000000UL, 0xbc39f630UL, 0x00000000UL, 0x3fe00000UL,
    0x94247758UL, 0x3fc133ccUL, 0x6b151741UL, 0xbfe8bc80UL, 0x20000000UL,
    0x3c82c5e1UL, 0x00000000UL, 0x3fe00000UL, 0x99fcef32UL, 0x3fca8279UL,
    0x667f3bcdUL, 0xbfe6a09eUL, 0x20000000UL, 0x3c8bdd34UL, 0x00000000UL,
    0x3fe00000UL, 0x53aba2fdUL, 0xbfcd0dfeUL, 0x25091dd6UL, 0xbfe44cf3UL,
    0x20000000UL, 0xbc68076aUL, 0x00000000UL, 0x3ff00000UL, 0x5bc57974UL,
    0xbfc59267UL, 0x39ae68c8UL, 0xbfe1c73bUL, 0x20000000UL, 0xbc8b25ddUL,
    0x00000000UL, 0x3ff00000UL, 0x73fa1279UL, 0xbfbe3a68UL, 0x3806f63bUL,
    0xbfde2b5dUL, 0x20000000UL, 0xbc5e0d89UL, 0x00000000UL, 0x3ff00000UL,
    0x866b95cfUL, 0xbfb37ca1UL, 0xa6aea963UL, 0xbfd87de2UL, 0xe0000000UL,
    0x3c672cedUL, 0x00000000UL, 0x3ff00000UL, 0x939d225aUL, 0xbfa60beaUL,
    0x2ed59f06UL, 0xbfd29406UL, 0xa0000000UL, 0x3c75d28dUL, 0x00000000UL,
    0x3ff00000UL, 0x011469fbUL, 0xbf93ad06UL, 0x3c69a60bUL, 0xbfc8f8b8UL,
    0xc0000000UL, 0x3c626d19UL, 0x00000000UL, 0x3ff00000UL, 0x176d6d31UL,
    0xbf73b92eUL, 0xbc29b42cUL, 0xbfb917a6UL, 0xe0000000UL, 0x3c3e2718UL,
    0x00000000UL, 0x3ff00000UL, 0x55555555UL, 0xbfc55555UL, 0x00000000UL,
    0xbfe00000UL, 0x11111111UL, 0x3f811111UL, 0x55555555UL, 0x3fa55555UL,
    0x1a01a01aUL, 0xbf2a01a0UL, 0x16c16c17UL, 0xbf56c16cUL, 0xa556c734UL,
    0x3ec71de3UL, 0x1a01a01aUL, 0x3efa01a0UL, 0x1a600000UL, 0x3d90b461UL,
    0x1a600000UL, 0x3d90b461UL, 0x54400000UL, 0x3fb921fbUL, 0x00000000UL,
    0x00000000UL, 0x2e037073UL, 0x3b63198aUL, 0x00000000UL, 0x00000000UL,
    0x6dc9c883UL, 0x40245f30UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x43380000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x43600000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x3c800000UL, 0x00000000UL,
    0x00000000UL, 0xffffffffUL, 0x3fefffffUL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x80000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x80000000UL, 0x00000000UL, 0x80000000UL, 0x00000000UL, 0x3fe00000UL,
    0x00000000UL, 0x3fe00000UL
};

void MacroAssembler::fast_sin(XMMRegister xmm0, XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3, XMMRegister xmm4, XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7, Register eax, Register ebx, Register edx) {

  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label L_2TAG_PACKET_4_0_2, start;
  assert_different_registers(eax, ebx, edx);
  address static_const_table_sin = (address)_static_const_table_sin;

  bind(start);
  subl(rsp, 120);
  movl(Address(rsp, 56), ebx);
  lea(ebx, ExternalAddress(static_const_table_sin));
  movsd(xmm0, Address(rsp, 128));
  pextrw(eax, xmm0, 3);
  andl(eax, 32767);
  subl(eax, 12336);
  cmpl(eax, 4293);
  jcc(Assembler::above, L_2TAG_PACKET_0_0_2);
  movsd(xmm1, Address(ebx, 2160));
  mulsd(xmm1, xmm0);
  movsd(xmm5, Address(ebx, 2272));
  movdqu(xmm4, Address(ebx, 2256));
  pand(xmm4, xmm0);
  por(xmm5, xmm4);
  movsd(xmm3, Address(ebx, 2128));
  movdqu(xmm2, Address(ebx, 2112));
  addpd(xmm1, xmm5);
  cvttsd2sil(edx, xmm1);
  cvtsi2sdl(xmm1, edx);
  mulsd(xmm3, xmm1);
  unpcklpd(xmm1, xmm1);
  addl(edx, 1865216);
  movdqu(xmm4, xmm0);
  andl(edx, 63);
  movdqu(xmm5, Address(ebx, 2096));
  lea(eax, Address(ebx, 0));
  shll(edx, 5);
  addl(eax, edx);
  mulpd(xmm2, xmm1);
  subsd(xmm0, xmm3);
  mulsd(xmm1, Address(ebx, 2144));
  subsd(xmm4, xmm3);
  movsd(xmm7, Address(eax, 8));
  unpcklpd(xmm0, xmm0);
  movapd(xmm3, xmm4);
  subsd(xmm4, xmm2);
  mulpd(xmm5, xmm0);
  subpd(xmm0, xmm2);
  movdqu(xmm6, Address(ebx, 2064));
  mulsd(xmm7, xmm4);
  subsd(xmm3, xmm4);
  mulpd(xmm5, xmm0);
  mulpd(xmm0, xmm0);
  subsd(xmm3, xmm2);
  movdqu(xmm2, Address(eax, 0));
  subsd(xmm1, xmm3);
  movsd(xmm3, Address(eax, 24));
  addsd(xmm2, xmm3);
  subsd(xmm7, xmm2);
  mulsd(xmm2, xmm4);
  mulpd(xmm6, xmm0);
  mulsd(xmm3, xmm4);
  mulpd(xmm2, xmm0);
  mulpd(xmm0, xmm0);
  addpd(xmm5, Address(ebx, 2080));
  mulsd(xmm4, Address(eax, 0));
  addpd(xmm6, Address(ebx, 2048));
  mulpd(xmm5, xmm0);
  movapd(xmm0, xmm3);
  addsd(xmm3, Address(eax, 8));
  mulpd(xmm1, xmm7);
  movapd(xmm7, xmm4);
  addsd(xmm4, xmm3);
  addpd(xmm6, xmm5);
  movsd(xmm5, Address(eax, 8));
  subsd(xmm5, xmm3);
  subsd(xmm3, xmm4);
  addsd(xmm1, Address(eax, 16));
  mulpd(xmm6, xmm2);
  addsd(xmm5, xmm0);
  addsd(xmm3, xmm7);
  addsd(xmm1, xmm5);
  addsd(xmm1, xmm3);
  addsd(xmm1, xmm6);
  unpckhpd(xmm6, xmm6);
  addsd(xmm1, xmm6);
  addsd(xmm4, xmm1);
  movsd(Address(rsp, 0), xmm4);
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_1_0_2);

  bind(L_2TAG_PACKET_0_0_2);
  jcc(Assembler::greater, L_2TAG_PACKET_2_0_2);
  shrl(eax, 4);
  cmpl(eax, 268434685);
  jcc(Assembler::notEqual, L_2TAG_PACKET_3_0_2);
  movsd(Address(rsp, 0), xmm0);
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_1_0_2);

  bind(L_2TAG_PACKET_3_0_2);
  movsd(xmm3, Address(ebx, 2192));
  mulsd(xmm3, xmm0);
  subsd(xmm3, xmm0);
  mulsd(xmm3, Address(ebx, 2208));
  movsd(Address(rsp, 0), xmm0);
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_1_0_2);

  bind(L_2TAG_PACKET_2_0_2);
  movl(eax, Address(rsp, 132));
  andl(eax, 2146435072);
  cmpl(eax, 2146435072);
  jcc(Assembler::equal, L_2TAG_PACKET_4_0_2);
  subl(rsp, 32);
  movsd(Address(rsp, 0), xmm0);
  lea(eax, Address(rsp, 40));
  movl(Address(rsp, 8), eax);
  movl(eax, 2);
  movl(Address(rsp, 12), eax);
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::dlibm_sin_cos_huge())));
  addl(rsp, 32);
  fld_d(Address(rsp, 16));
  jmp(L_2TAG_PACKET_1_0_2);
  bind(L_2TAG_PACKET_4_0_2);
  fld_d(Address(rsp, 128));
  fmul_d(Address(ebx, 2240));
  bind(L_2TAG_PACKET_1_0_2);
  movl(ebx, Address(rsp, 56));
}

/******************************************************************************/
//                     ALGORITHM DESCRIPTION - COS()
//                     ---------------------
//
//     1. RANGE REDUCTION
//
//     We perform an initial range reduction from X to r with
//
//          X =~= N * pi/32 + r
//
//     so that |r| <= pi/64 + epsilon. We restrict inputs to those
//     where |N| <= 932560. Beyond this, the range reduction is
//     insufficiently accurate. For extremely small inputs,
//     denormalization can occur internally, impacting performance.
//     This means that the main path is actually only taken for
//     2^-252 <= |X| < 90112.
//
//     To avoid branches, we perform the range reduction to full
//     accuracy each time.
//
//          X - N * (P_1 + P_2 + P_3)
//
//     where P_1 and P_2 are 32-bit numbers (so multiplication by N
//     is exact) and P_3 is a 53-bit number. Together, these
//     approximate pi well enough for all cases in the restricted
//     range.
//
//     The main reduction sequence is:
//
//             y = 32/pi * x
//             N = integer(y)
//     (computed by adding and subtracting off SHIFTER)
//
//             m_1 = N * P_1
//             m_2 = N * P_2
//             r_1 = x - m_1
//             r = r_1 - m_2
//     (this r can be used for most of the calculation)
//
//             c_1 = r_1 - r
//             m_3 = N * P_3
//             c_2 = c_1 - m_2
//             c = c_2 - m_3
//
//     2. MAIN ALGORITHM
//
//     The algorithm uses a table lookup based on B = M * pi / 32
//     where M = N mod 64. The stored values are:
//       sigma             closest power of 2 to cos(B)
//       C_hl              53-bit cos(B) - sigma
//       S_hi + S_lo       2 * 53-bit sin(B)
//
//     The computation is organized as follows:
//
//          sin(B + r + c) = [sin(B) + sigma * r] +
//                           r * (cos(B) - sigma) +
//                           sin(B) * [cos(r + c) - 1] +
//                           cos(B) * [sin(r + c) - r]
//
//     which is approximately:
//
//          [S_hi + sigma * r] +
//          C_hl * r +
//          S_lo + S_hi * [(cos(r) - 1) - r * c] +
//          (C_hl + sigma) * [(sin(r) - r) + c]
//
//     and this is what is actually computed. We separate this sum
//     into four parts:
//
//          hi + med + pols + corr
//
//     where
//
//          hi       = S_hi + sigma r
//          med      = C_hl * r
//          pols     = S_hi * (cos(r) - 1) + (C_hl + sigma) * (sin(r) - r)
//          corr     = S_lo + c * ((C_hl + sigma) - S_hi * r)
//
//     3. POLYNOMIAL
//
//     The polynomial S_hi * (cos(r) - 1) + (C_hl + sigma) *
//     (sin(r) - r) can be rearranged freely, since it is quite
//     small, so we exploit parallelism to the fullest.
//
//          psc4       =   SC_4 * r_1
//          msc4       =   psc4 * r
//          r2         =   r * r
//          msc2       =   SC_2 * r2
//          r4         =   r2 * r2
//          psc3       =   SC_3 + msc4
//          psc1       =   SC_1 + msc2
//          msc3       =   r4 * psc3
//          sincospols =   psc1 + msc3
//          pols       =   sincospols *
//                         <S_hi * r^2 | (C_hl + sigma) * r^3>
//
//     4. CORRECTION TERM
//
//     This is where the "c" component of the range reduction is
//     taken into account; recall that just "r" is used for most of
//     the calculation.
//
//          -c   = m_3 - c_2
//          -d   = S_hi * r - (C_hl + sigma)
//          corr = -c * -d + S_lo
//
//     5. COMPENSATED SUMMATIONS
//
//     The two successive compensated summations add up the high
//     and medium parts, leaving just the low parts to add up at
//     the end.
//
//          rs        =  sigma * r
//          res_int   =  S_hi + rs
//          k_0       =  S_hi - res_int
//          k_2       =  k_0 + rs
//          med       =  C_hl * r
//          res_hi    =  res_int + med
//          k_1       =  res_int - res_hi
//          k_3       =  k_1 + med
//
//     6. FINAL SUMMATION
//
//     We now add up all the small parts:
//
//          res_lo = pols(hi) + pols(lo) + corr + k_1 + k_3
//
//     Now the overall result is just:
//
//          res_hi + res_lo
//
//     7. SMALL ARGUMENTS
//
//     Inputs with |X| < 2^-252 are treated specially as
//     1 - |x|.
//
// Special cases:
//  cos(NaN) = quiet NaN, and raise invalid exception
//  cos(INF) = NaN and raise invalid exception
//  cos(0) = 1
//
/******************************************************************************/

ALIGNED_(16) juint _static_const_table_cos[] =
{
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x3ff00000UL, 0x176d6d31UL, 0xbf73b92eUL,
    0xbc29b42cUL, 0x3fb917a6UL, 0xe0000000UL, 0xbc3e2718UL, 0x00000000UL,
    0x3ff00000UL, 0x011469fbUL, 0xbf93ad06UL, 0x3c69a60bUL, 0x3fc8f8b8UL,
    0xc0000000UL, 0xbc626d19UL, 0x00000000UL, 0x3ff00000UL, 0x939d225aUL,
    0xbfa60beaUL, 0x2ed59f06UL, 0x3fd29406UL, 0xa0000000UL, 0xbc75d28dUL,
    0x00000000UL, 0x3ff00000UL, 0x866b95cfUL, 0xbfb37ca1UL, 0xa6aea963UL,
    0x3fd87de2UL, 0xe0000000UL, 0xbc672cedUL, 0x00000000UL, 0x3ff00000UL,
    0x73fa1279UL, 0xbfbe3a68UL, 0x3806f63bUL, 0x3fde2b5dUL, 0x20000000UL,
    0x3c5e0d89UL, 0x00000000UL, 0x3ff00000UL, 0x5bc57974UL, 0xbfc59267UL,
    0x39ae68c8UL, 0x3fe1c73bUL, 0x20000000UL, 0x3c8b25ddUL, 0x00000000UL,
    0x3ff00000UL, 0x53aba2fdUL, 0xbfcd0dfeUL, 0x25091dd6UL, 0x3fe44cf3UL,
    0x20000000UL, 0x3c68076aUL, 0x00000000UL, 0x3ff00000UL, 0x99fcef32UL,
    0x3fca8279UL, 0x667f3bcdUL, 0x3fe6a09eUL, 0x20000000UL, 0xbc8bdd34UL,
    0x00000000UL, 0x3fe00000UL, 0x94247758UL, 0x3fc133ccUL, 0x6b151741UL,
    0x3fe8bc80UL, 0x20000000UL, 0xbc82c5e1UL, 0x00000000UL, 0x3fe00000UL,
    0x9ae68c87UL, 0x3fac73b3UL, 0x290ea1a3UL, 0x3fea9b66UL, 0xe0000000UL,
    0x3c39f630UL, 0x00000000UL, 0x3fe00000UL, 0x7f909c4eUL, 0xbf9d4a2cUL,
    0xf180bdb1UL, 0x3fec38b2UL, 0x80000000UL, 0xbc76e0b1UL, 0x00000000UL,
    0x3fe00000UL, 0x65455a75UL, 0xbfbe0875UL, 0xcf328d46UL, 0x3fed906bUL,
    0x20000000UL, 0x3c7457e6UL, 0x00000000UL, 0x3fe00000UL, 0x76acf82dUL,
    0x3fa4a031UL, 0x56c62ddaUL, 0x3fee9f41UL, 0xe0000000UL, 0x3c8760b1UL,
    0x00000000UL, 0x3fd00000UL, 0x0e5967d5UL, 0xbfac1d1fUL, 0xcff75cb0UL,
    0x3fef6297UL, 0x20000000UL, 0x3c756217UL, 0x00000000UL, 0x3fd00000UL,
    0x0f592f50UL, 0xbf9ba165UL, 0xa3d12526UL, 0x3fefd88dUL, 0x40000000UL,
    0xbc887df6UL, 0x00000000UL, 0x3fc00000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x0f592f50UL, 0x3f9ba165UL, 0xa3d12526UL, 0x3fefd88dUL,
    0x40000000UL, 0xbc887df6UL, 0x00000000UL, 0xbfc00000UL, 0x0e5967d5UL,
    0x3fac1d1fUL, 0xcff75cb0UL, 0x3fef6297UL, 0x20000000UL, 0x3c756217UL,
    0x00000000UL, 0xbfd00000UL, 0x76acf82dUL, 0xbfa4a031UL, 0x56c62ddaUL,
    0x3fee9f41UL, 0xe0000000UL, 0x3c8760b1UL, 0x00000000UL, 0xbfd00000UL,
    0x65455a75UL, 0x3fbe0875UL, 0xcf328d46UL, 0x3fed906bUL, 0x20000000UL,
    0x3c7457e6UL, 0x00000000UL, 0xbfe00000UL, 0x7f909c4eUL, 0x3f9d4a2cUL,
    0xf180bdb1UL, 0x3fec38b2UL, 0x80000000UL, 0xbc76e0b1UL, 0x00000000UL,
    0xbfe00000UL, 0x9ae68c87UL, 0xbfac73b3UL, 0x290ea1a3UL, 0x3fea9b66UL,
    0xe0000000UL, 0x3c39f630UL, 0x00000000UL, 0xbfe00000UL, 0x94247758UL,
    0xbfc133ccUL, 0x6b151741UL, 0x3fe8bc80UL, 0x20000000UL, 0xbc82c5e1UL,
    0x00000000UL, 0xbfe00000UL, 0x99fcef32UL, 0xbfca8279UL, 0x667f3bcdUL,
    0x3fe6a09eUL, 0x20000000UL, 0xbc8bdd34UL, 0x00000000UL, 0xbfe00000UL,
    0x53aba2fdUL, 0x3fcd0dfeUL, 0x25091dd6UL, 0x3fe44cf3UL, 0x20000000UL,
    0x3c68076aUL, 0x00000000UL, 0xbff00000UL, 0x5bc57974UL, 0x3fc59267UL,
    0x39ae68c8UL, 0x3fe1c73bUL, 0x20000000UL, 0x3c8b25ddUL, 0x00000000UL,
    0xbff00000UL, 0x73fa1279UL, 0x3fbe3a68UL, 0x3806f63bUL, 0x3fde2b5dUL,
    0x20000000UL, 0x3c5e0d89UL, 0x00000000UL, 0xbff00000UL, 0x866b95cfUL,
    0x3fb37ca1UL, 0xa6aea963UL, 0x3fd87de2UL, 0xe0000000UL, 0xbc672cedUL,
    0x00000000UL, 0xbff00000UL, 0x939d225aUL, 0x3fa60beaUL, 0x2ed59f06UL,
    0x3fd29406UL, 0xa0000000UL, 0xbc75d28dUL, 0x00000000UL, 0xbff00000UL,
    0x011469fbUL, 0x3f93ad06UL, 0x3c69a60bUL, 0x3fc8f8b8UL, 0xc0000000UL,
    0xbc626d19UL, 0x00000000UL, 0xbff00000UL, 0x176d6d31UL, 0x3f73b92eUL,
    0xbc29b42cUL, 0x3fb917a6UL, 0xe0000000UL, 0xbc3e2718UL, 0x00000000UL,
    0xbff00000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0xbff00000UL, 0x176d6d31UL,
    0x3f73b92eUL, 0xbc29b42cUL, 0xbfb917a6UL, 0xe0000000UL, 0x3c3e2718UL,
    0x00000000UL, 0xbff00000UL, 0x011469fbUL, 0x3f93ad06UL, 0x3c69a60bUL,
    0xbfc8f8b8UL, 0xc0000000UL, 0x3c626d19UL, 0x00000000UL, 0xbff00000UL,
    0x939d225aUL, 0x3fa60beaUL, 0x2ed59f06UL, 0xbfd29406UL, 0xa0000000UL,
    0x3c75d28dUL, 0x00000000UL, 0xbff00000UL, 0x866b95cfUL, 0x3fb37ca1UL,
    0xa6aea963UL, 0xbfd87de2UL, 0xe0000000UL, 0x3c672cedUL, 0x00000000UL,
    0xbff00000UL, 0x73fa1279UL, 0x3fbe3a68UL, 0x3806f63bUL, 0xbfde2b5dUL,
    0x20000000UL, 0xbc5e0d89UL, 0x00000000UL, 0xbff00000UL, 0x5bc57974UL,
    0x3fc59267UL, 0x39ae68c8UL, 0xbfe1c73bUL, 0x20000000UL, 0xbc8b25ddUL,
    0x00000000UL, 0xbff00000UL, 0x53aba2fdUL, 0x3fcd0dfeUL, 0x25091dd6UL,
    0xbfe44cf3UL, 0x20000000UL, 0xbc68076aUL, 0x00000000UL, 0xbff00000UL,
    0x99fcef32UL, 0xbfca8279UL, 0x667f3bcdUL, 0xbfe6a09eUL, 0x20000000UL,
    0x3c8bdd34UL, 0x00000000UL, 0xbfe00000UL, 0x94247758UL, 0xbfc133ccUL,
    0x6b151741UL, 0xbfe8bc80UL, 0x20000000UL, 0x3c82c5e1UL, 0x00000000UL,
    0xbfe00000UL, 0x9ae68c87UL, 0xbfac73b3UL, 0x290ea1a3UL, 0xbfea9b66UL,
    0xe0000000UL, 0xbc39f630UL, 0x00000000UL, 0xbfe00000UL, 0x7f909c4eUL,
    0x3f9d4a2cUL, 0xf180bdb1UL, 0xbfec38b2UL, 0x80000000UL, 0x3c76e0b1UL,
    0x00000000UL, 0xbfe00000UL, 0x65455a75UL, 0x3fbe0875UL, 0xcf328d46UL,
    0xbfed906bUL, 0x20000000UL, 0xbc7457e6UL, 0x00000000UL, 0xbfe00000UL,
    0x76acf82dUL, 0xbfa4a031UL, 0x56c62ddaUL, 0xbfee9f41UL, 0xe0000000UL,
    0xbc8760b1UL, 0x00000000UL, 0xbfd00000UL, 0x0e5967d5UL, 0x3fac1d1fUL,
    0xcff75cb0UL, 0xbfef6297UL, 0x20000000UL, 0xbc756217UL, 0x00000000UL,
    0xbfd00000UL, 0x0f592f50UL, 0x3f9ba165UL, 0xa3d12526UL, 0xbfefd88dUL,
    0x40000000UL, 0x3c887df6UL, 0x00000000UL, 0xbfc00000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0xbff00000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x0f592f50UL, 0xbf9ba165UL, 0xa3d12526UL,
    0xbfefd88dUL, 0x40000000UL, 0x3c887df6UL, 0x00000000UL, 0x3fc00000UL,
    0x0e5967d5UL, 0xbfac1d1fUL, 0xcff75cb0UL, 0xbfef6297UL, 0x20000000UL,
    0xbc756217UL, 0x00000000UL, 0x3fd00000UL, 0x76acf82dUL, 0x3fa4a031UL,
    0x56c62ddaUL, 0xbfee9f41UL, 0xe0000000UL, 0xbc8760b1UL, 0x00000000UL,
    0x3fd00000UL, 0x65455a75UL, 0xbfbe0875UL, 0xcf328d46UL, 0xbfed906bUL,
    0x20000000UL, 0xbc7457e6UL, 0x00000000UL, 0x3fe00000UL, 0x7f909c4eUL,
    0xbf9d4a2cUL, 0xf180bdb1UL, 0xbfec38b2UL, 0x80000000UL, 0x3c76e0b1UL,
    0x00000000UL, 0x3fe00000UL, 0x9ae68c87UL, 0x3fac73b3UL, 0x290ea1a3UL,
    0xbfea9b66UL, 0xe0000000UL, 0xbc39f630UL, 0x00000000UL, 0x3fe00000UL,
    0x94247758UL, 0x3fc133ccUL, 0x6b151741UL, 0xbfe8bc80UL, 0x20000000UL,
    0x3c82c5e1UL, 0x00000000UL, 0x3fe00000UL, 0x99fcef32UL, 0x3fca8279UL,
    0x667f3bcdUL, 0xbfe6a09eUL, 0x20000000UL, 0x3c8bdd34UL, 0x00000000UL,
    0x3fe00000UL, 0x53aba2fdUL, 0xbfcd0dfeUL, 0x25091dd6UL, 0xbfe44cf3UL,
    0x20000000UL, 0xbc68076aUL, 0x00000000UL, 0x3ff00000UL, 0x5bc57974UL,
    0xbfc59267UL, 0x39ae68c8UL, 0xbfe1c73bUL, 0x20000000UL, 0xbc8b25ddUL,
    0x00000000UL, 0x3ff00000UL, 0x73fa1279UL, 0xbfbe3a68UL, 0x3806f63bUL,
    0xbfde2b5dUL, 0x20000000UL, 0xbc5e0d89UL, 0x00000000UL, 0x3ff00000UL,
    0x866b95cfUL, 0xbfb37ca1UL, 0xa6aea963UL, 0xbfd87de2UL, 0xe0000000UL,
    0x3c672cedUL, 0x00000000UL, 0x3ff00000UL, 0x939d225aUL, 0xbfa60beaUL,
    0x2ed59f06UL, 0xbfd29406UL, 0xa0000000UL, 0x3c75d28dUL, 0x00000000UL,
    0x3ff00000UL, 0x011469fbUL, 0xbf93ad06UL, 0x3c69a60bUL, 0xbfc8f8b8UL,
    0xc0000000UL, 0x3c626d19UL, 0x00000000UL, 0x3ff00000UL, 0x176d6d31UL,
    0xbf73b92eUL, 0xbc29b42cUL, 0xbfb917a6UL, 0xe0000000UL, 0x3c3e2718UL,
    0x00000000UL, 0x3ff00000UL, 0x55555555UL, 0xbfc55555UL, 0x00000000UL,
    0xbfe00000UL, 0x11111111UL, 0x3f811111UL, 0x55555555UL, 0x3fa55555UL,
    0x1a01a01aUL, 0xbf2a01a0UL, 0x16c16c17UL, 0xbf56c16cUL, 0xa556c734UL,
    0x3ec71de3UL, 0x1a01a01aUL, 0x3efa01a0UL, 0x1a600000UL, 0x3d90b461UL,
    0x1a600000UL, 0x3d90b461UL, 0x54400000UL, 0x3fb921fbUL, 0x00000000UL,
    0x00000000UL, 0x2e037073UL, 0x3b63198aUL, 0x00000000UL, 0x00000000UL,
    0x6dc9c883UL, 0x40245f30UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x43380000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x3ff00000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x80000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x80000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x3fe00000UL, 0x00000000UL, 0x3fe00000UL
};

//registers,
// input: (rbp + 8)
// scratch: xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
//          rax, rdx, rcx, rbx (tmp)

// Code generated by Intel C compiler for LIBM library

void MacroAssembler::fast_cos(XMMRegister xmm0, XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3, XMMRegister xmm4, XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7, Register eax, Register ecx, Register edx, Register tmp) {
  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label L_2TAG_PACKET_4_0_2, L_2TAG_PACKET_5_0_2, L_2TAG_PACKET_6_0_2, L_2TAG_PACKET_7_0_2;
  Label L_2TAG_PACKET_8_0_2, L_2TAG_PACKET_9_0_2, L_2TAG_PACKET_10_0_2, L_2TAG_PACKET_11_0_2;
  Label L_2TAG_PACKET_12_0_2, L_2TAG_PACKET_13_0_2, B1_3, B1_5, start;

  assert_different_registers(tmp, eax, ecx, edx);

  address static_const_table_cos = (address)_static_const_table_cos;

  bind(start);
  subl(rsp, 120);
  movl(Address(rsp, 56), tmp);
  lea(tmp, ExternalAddress(static_const_table_cos));
  movsd(xmm0, Address(rsp, 128));
  pextrw(eax, xmm0, 3);
  andl(eax, 32767);
  subl(eax, 12336);
  cmpl(eax, 4293);
  jcc(Assembler::above, L_2TAG_PACKET_0_0_2);
  movsd(xmm1, Address(tmp, 2160));
  mulsd(xmm1, xmm0);
  movdqu(xmm5, Address(tmp, 2240));
  movsd(xmm4, Address(tmp, 2224));
  pand(xmm4, xmm0);
  por(xmm5, xmm4);
  movsd(xmm3, Address(tmp, 2128));
  movdqu(xmm2, Address(tmp, 2112));
  addpd(xmm1, xmm5);
  cvttsd2sil(edx, xmm1);
  cvtsi2sdl(xmm1, edx);
  mulsd(xmm3, xmm1);
  unpcklpd(xmm1, xmm1);
  addl(edx, 1865232);
  movdqu(xmm4, xmm0);
  andl(edx, 63);
  movdqu(xmm5, Address(tmp, 2096));
  lea(eax, Address(tmp, 0));
  shll(edx, 5);
  addl(eax, edx);
  mulpd(xmm2, xmm1);
  subsd(xmm0, xmm3);
  mulsd(xmm1, Address(tmp, 2144));
  subsd(xmm4, xmm3);
  movsd(xmm7, Address(eax, 8));
  unpcklpd(xmm0, xmm0);
  movapd(xmm3, xmm4);
  subsd(xmm4, xmm2);
  mulpd(xmm5, xmm0);
  subpd(xmm0, xmm2);
  movdqu(xmm6, Address(tmp, 2064));
  mulsd(xmm7, xmm4);
  subsd(xmm3, xmm4);
  mulpd(xmm5, xmm0);
  mulpd(xmm0, xmm0);
  subsd(xmm3, xmm2);
  movdqu(xmm2, Address(eax, 0));
  subsd(xmm1, xmm3);
  movsd(xmm3, Address(eax, 24));
  addsd(xmm2, xmm3);
  subsd(xmm7, xmm2);
  mulsd(xmm2, xmm4);
  mulpd(xmm6, xmm0);
  mulsd(xmm3, xmm4);
  mulpd(xmm2, xmm0);
  mulpd(xmm0, xmm0);
  addpd(xmm5, Address(tmp, 2080));
  mulsd(xmm4, Address(eax, 0));
  addpd(xmm6, Address(tmp, 2048));
  mulpd(xmm5, xmm0);
  movapd(xmm0, xmm3);
  addsd(xmm3, Address(eax, 8));
  mulpd(xmm1, xmm7);
  movapd(xmm7, xmm4);
  addsd(xmm4, xmm3);
  addpd(xmm6, xmm5);
  movsd(xmm5, Address(eax, 8));
  subsd(xmm5, xmm3);
  subsd(xmm3, xmm4);
  addsd(xmm1, Address(eax, 16));
  mulpd(xmm6, xmm2);
  addsd(xmm5, xmm0);
  addsd(xmm3, xmm7);
  addsd(xmm1, xmm5);
  addsd(xmm1, xmm3);
  addsd(xmm1, xmm6);
  unpckhpd(xmm6, xmm6);
  addsd(xmm1, xmm6);
  addsd(xmm4, xmm1);
  movsd(Address(rsp, 0), xmm4);
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_1_0_2);

  bind(L_2TAG_PACKET_0_0_2);
  jcc(Assembler::greater, L_2TAG_PACKET_2_0_2);
  pextrw(eax, xmm0, 3);
  andl(eax, 32767);
  pinsrw(xmm0, eax, 3);
  movsd(xmm1, Address(tmp, 2192));
  subsd(xmm1, xmm0);
  movsd(Address(rsp, 0), xmm1);
  fld_d(Address(rsp, 0));
  jmp(L_2TAG_PACKET_1_0_2);

  bind(L_2TAG_PACKET_2_0_2);
  movl(eax, Address(rsp, 132));
  andl(eax, 2146435072);
  cmpl(eax, 2146435072);
  jcc(Assembler::equal, L_2TAG_PACKET_3_0_2);
  subl(rsp, 32);
  movsd(Address(rsp, 0), xmm0);
  lea(eax, Address(rsp, 40));
  movl(Address(rsp, 8), eax);
  movl(eax, 1);
  movl(Address(rsp, 12), eax);
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::dlibm_sin_cos_huge())));
  addl(rsp, 32);
  fld_d(Address(rsp, 8));
  jmp(L_2TAG_PACKET_1_0_2);

  bind(L_2TAG_PACKET_3_0_2);
  fld_d(Address(rsp, 128));
  fmul_d(Address(tmp, 2208));

  bind(L_2TAG_PACKET_1_0_2);
  movl(tmp, Address(rsp, 56));
}

