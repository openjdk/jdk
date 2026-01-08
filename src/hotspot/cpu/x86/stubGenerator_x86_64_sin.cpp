/*
 * Copyright (c) 2016, 2025, Intel Corporation. All rights reserved.
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

// The 64 bit code is at most SSE2 compliant
ATTRIBUTE_ALIGNED(8) static const juint _ALL_ONES[] =
{
    0xffffffffUL, 0x3fefffffUL
};

#define __ _masm->

address StubGenerator::generate_libmSin() {
  StubId stub_id = StubId::stubgen_dsin_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();

  Label L_2TAG_PACKET_0_0_1, L_2TAG_PACKET_1_0_1, L_2TAG_PACKET_2_0_1, L_2TAG_PACKET_3_0_1;
  Label L_2TAG_PACKET_4_0_1, L_2TAG_PACKET_5_0_1, L_2TAG_PACKET_6_0_1, L_2TAG_PACKET_7_0_1;
  Label L_2TAG_PACKET_8_0_1, L_2TAG_PACKET_9_0_1, L_2TAG_PACKET_10_0_1, L_2TAG_PACKET_11_0_1;
  Label L_2TAG_PACKET_13_0_1, L_2TAG_PACKET_14_0_1;
  Label L_2TAG_PACKET_12_0_1, B1_4;

  address ALL_ONES = (address)_ALL_ONES;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
  __ push_ppx(rsi);
  __ push_ppx(rdi);
#endif

  __ push_ppx(rbx);
  __ subq(rsp, 16);
  __ movsd(Address(rsp, 8), xmm0);
  __ movl(rax, Address(rsp, 12));
  __ movq(xmm1, ExternalAddress(PI32INV), r8 /*rscratch*/); //0x6dc9c883UL, 0x40245f30UL
  __ movq(xmm2, ExternalAddress(SHIFTER), r8 /*rscratch*/); //0x00000000UL, 0x43380000UL
  __ andl(rax, 2147418112);
  __ subl(rax, 808452096);
  __ cmpl(rax, 281346048);
  __ jcc(Assembler::above, L_2TAG_PACKET_0_0_1);
  __ mulsd(xmm1, xmm0);
  __ movdqu(xmm5, ExternalAddress(ONEHALF), r8 /*rscratch*/); //0x00000000UL, 0x3fe00000UL, 0x00000000UL, 0x3fe00000UL
  __ movq(xmm4, ExternalAddress(SIGN_MASK), r8 /*rscratch*/); //0x00000000UL, 0x80000000UL
  __ pand(xmm4, xmm0);
  __ por(xmm5, xmm4);
  __ addpd(xmm1, xmm5);
  __ cvttsd2sil(rdx, xmm1);
  __ cvtsi2sdl(xmm1, rdx);
  __ movdqu(xmm6, ExternalAddress(P_2), r8 /*rscratch*/); //0x1a600000UL, 0x3d90b461UL, 0x1a600000UL, 0x3d90b461UL
  __ mov64(r8, 0x3fb921fb54400000);
  __ movdq(xmm3, r8);
  __ movdqu(xmm5, ExternalAddress(SC_4), r8 /*rscratch*/); //0xa556c734UL, 0x3ec71de3UL, 0x1a01a01aUL, 0x3efa01a0UL
  __ pshufd(xmm4, xmm0, 68);
  __ mulsd(xmm3, xmm1);
  if (VM_Version::supports_sse3()) {
    __ movddup(xmm1, xmm1);
  } else {
    __ movlhps(xmm1, xmm1);
  }
  __ andl(rdx, 63);
  __ shll(rdx, 5);
  __ lea(rax, ExternalAddress(Ctable));
  __ addq(rax, rdx);
  __ mulpd(xmm6, xmm1);
  __ mulsd(xmm1, ExternalAddress(P_3), r8 /*rscratch*/); //0x2e037073UL, 0x3b63198aUL
  __ subsd(xmm4, xmm3);
  __ movq(xmm7, Address(rax, 8));
  __ subsd(xmm0, xmm3);
  if (VM_Version::supports_sse3()) {
    __ movddup(xmm3, xmm4);
  } else {
    __ movdqu(xmm3, xmm4);
    __ movlhps(xmm3, xmm3);
  }
  __ subsd(xmm4, xmm6);
  __ pshufd(xmm0, xmm0, 68);
  __ movdqu(xmm2, Address(rax, 0));
  __ mulpd(xmm5, xmm0);
  __ subpd(xmm0, xmm6);
  __ mulsd(xmm7, xmm4);
  __ subsd(xmm3, xmm4);
  __ mulpd(xmm5, xmm0);
  __ mulpd(xmm0, xmm0);
  __ subsd(xmm3, xmm6);
  __ movdqu(xmm6, ExternalAddress(SC_2), r8 /*rscratch*/); //0x11111111UL, 0x3f811111UL, 0x55555555UL, 0x3fa55555UL
  __ subsd(xmm1, xmm3);
  __ movq(xmm3, Address(rax, 24));
  __ addsd(xmm2, xmm3);
  __ subsd(xmm7, xmm2);
  __ mulsd(xmm2, xmm4);
  __ mulpd(xmm6, xmm0);
  __ mulsd(xmm3, xmm4);
  __ mulpd(xmm2, xmm0);
  __ mulpd(xmm0, xmm0);
  __ addpd(xmm5, ExternalAddress(SC_3), r8 /*rscratch*/); //0x1a01a01aUL, 0xbf2a01a0UL, 0x16c16c17UL, 0xbf56c16cUL
  __ mulsd(xmm4, Address(rax, 0));
  __ addpd(xmm6, ExternalAddress(SC_1), r8 /*rscratch*/); //0x55555555UL, 0xbfc55555UL, 0x00000000UL, 0xbfe00000UL
  __ mulpd(xmm5, xmm0);
  __ movdqu(xmm0, xmm3);
  __ addsd(xmm3, Address(rax, 8));
  __ mulpd(xmm1, xmm7);
  __ movdqu(xmm7, xmm4);
  __ addsd(xmm4, xmm3);
  __ addpd(xmm6, xmm5);
  __ movq(xmm5, Address(rax, 8));
  __ subsd(xmm5, xmm3);
  __ subsd(xmm3, xmm4);
  __ addsd(xmm1, Address(rax, 16));
  __ mulpd(xmm6, xmm2);
  __ addsd(xmm5, xmm0);
  __ addsd(xmm3, xmm7);
  __ addsd(xmm1, xmm5);
  __ addsd(xmm1, xmm3);
  __ addsd(xmm1, xmm6);
  __ unpckhpd(xmm6, xmm6);
  __ movdqu(xmm0, xmm4);
  __ addsd(xmm1, xmm6);
  __ addsd(xmm0, xmm1);
  __ jmp(B1_4);

  __ bind(L_2TAG_PACKET_0_0_1);
  __ jcc(Assembler::greater, L_2TAG_PACKET_1_0_1);
  __ shrl(rax, 20);
  __ cmpl(rax, 3325);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_2_0_1);
  __ mulsd(xmm0, ExternalAddress(ALL_ONES), r8 /*rscratch*/); //0xffffffffUL, 0x3fefffffUL
  __ jmp(B1_4);

  __ bind(L_2TAG_PACKET_2_0_1);
  __ movq(xmm3, ExternalAddress(TWO_POW_55), r8 /*rscratch*/); //0x00000000UL, 0x43600000UL
  __ mulsd(xmm3, xmm0);
  __ subsd(xmm3, xmm0);
  __ mulsd(xmm3, ExternalAddress(TWO_POW_M55), r8 /*rscratch*/); //0x00000000UL, 0x3c800000UL
  __ jmp(B1_4);

  __ bind(L_2TAG_PACKET_1_0_1);
  __ pextrw(rax, xmm0, 3);
  __ andl(rax, 32752);
  __ cmpl(rax, 32752);
  __ jcc(Assembler::equal, L_2TAG_PACKET_3_0_1);
  __ pextrw(rcx, xmm0, 3);
  __ andl(rcx, 32752);
  __ subl(rcx, 16224);
  __ shrl(rcx, 7);
  __ andl(rcx, 65532);
  __ lea(r11, ExternalAddress(PI_INV_TABLE));
  __ addq(rcx, r11);
  __ movdq(rax, xmm0);
  __ movl(r10, Address(rcx, 20));
  __ movl(r8, Address(rcx, 24));
  __ movl(rdx, rax);
  __ shrq(rax, 21);
  __ orl(rax, INT_MIN);
  __ shrl(rax, 11);
  __ movl(r9, r10);
  __ imulq(r10, rdx);
  __ imulq(r9, rax);
  __ imulq(r8, rax);
  __ movl(rsi, Address(rcx, 16));
  __ movl(rdi, Address(rcx, 12));
  __ movl(r11, r10);
  __ shrq(r10, 32);
  __ addq(r9, r10);
  __ addq(r11, r8);
  __ movl(r8, r11);
  __ shrq(r11, 32);
  __ addq(r9, r11);
  __ movl(r10, rsi);
  __ imulq(rsi, rdx);
  __ imulq(r10, rax);
  __ movl(r11, rdi);
  __ imulq(rdi, rdx);
  __ movl(rbx, rsi);
  __ shrq(rsi, 32);
  __ addq(r9, rbx);
  __ movl(rbx, r9);
  __ shrq(r9, 32);
  __ addq(r10, rsi);
  __ addq(r10, r9);
  __ shlq(rbx, 32);
  __ orq(r8, rbx);
  __ imulq(r11, rax);
  __ movl(r9, Address(rcx, 8));
  __ movl(rsi, Address(rcx, 4));
  __ movl(rbx, rdi);
  __ shrq(rdi, 32);
  __ addq(r10, rbx);
  __ movl(rbx, r10);
  __ shrq(r10, 32);
  __ addq(r11, rdi);
  __ addq(r11, r10);
  __ movq(rdi, r9);
  __ imulq(r9, rdx);
  __ imulq(rdi, rax);
  __ movl(r10, r9);
  __ shrq(r9, 32);
  __ addq(r11, r10);
  __ movl(r10, r11);
  __ shrq(r11, 32);
  __ addq(rdi, r9);
  __ addq(rdi, r11);
  __ movq(r9, rsi);
  __ imulq(rsi, rdx);
  __ imulq(r9, rax);
  __ shlq(r10, 32);
  __ orq(r10, rbx);
  __ movl(rax, Address(rcx, 0));
  __ movl(r11, rsi);
  __ shrq(rsi, 32);
  __ addq(rdi, r11);
  __ movl(r11, rdi);
  __ shrq(rdi, 32);
  __ addq(r9, rsi);
  __ addq(r9, rdi);
  __ imulq(rdx, rax);
  __ pextrw(rbx, xmm0, 3);
  __ lea(rdi, ExternalAddress(PI_INV_TABLE));
  __ subq(rcx, rdi);
  __ addl(rcx, rcx);
  __ addl(rcx, rcx);
  __ addl(rcx, rcx);
  __ addl(rcx, 19);
  __ movl(rsi, 32768);
  __ andl(rsi, rbx);
  __ shrl(rbx, 4);
  __ andl(rbx, 2047);
  __ subl(rbx, 1023);
  __ subl(rcx, rbx);
  __ addq(r9, rdx);
  __ movl(rdx, rcx);
  __ addl(rdx, 32);
  __ cmpl(rcx, 1);
  __ jcc(Assembler::less, L_2TAG_PACKET_4_0_1);
  __ negl(rcx);
  __ addl(rcx, 29);
  __ shll(r9);
  __ movl(rdi, r9);
  __ andl(r9, 536870911);
  __ testl(r9, 268435456);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_5_0_1);
  __ shrl(r9);
  __ movl(rbx, 0);
  __ shlq(r9, 32);
  __ orq(r9, r11);

  __ bind(L_2TAG_PACKET_6_0_1);

  __ bind(L_2TAG_PACKET_7_0_1);

  __ cmpq(r9, 0);
  __ jcc(Assembler::equal, L_2TAG_PACKET_8_0_1);

  __ bind(L_2TAG_PACKET_9_0_1);
  __ bsrq(r11, r9);
  __ movl(rcx, 29);
  __ subl(rcx, r11);
  __ jcc(Assembler::lessEqual, L_2TAG_PACKET_10_0_1);
  __ shlq(r9);
  __ movq(rax, r10);
  __ shlq(r10);
  __ addl(rdx, rcx);
  __ negl(rcx);
  __ addl(rcx, 64);
  __ shrq(rax);
  __ shrq(r8);
  __ orq(r9, rax);
  __ orq(r10, r8);

  __ bind(L_2TAG_PACKET_11_0_1);
  __ cvtsi2sdq(xmm0, r9);
  __ shrq(r10, 1);
  __ cvtsi2sdq(xmm3, r10);
  __ xorpd(xmm4, xmm4);
  __ shll(rdx, 4);
  __ negl(rdx);
  __ addl(rdx, 16368);
  __ orl(rdx, rsi);
  __ xorl(rdx, rbx);
  __ pinsrw(xmm4, rdx, 3);
  __ movq(xmm2, ExternalAddress(PI_4),     r8 /*rscratch*/); //0x40000000UL, 0x3fe921fbUL, 0x18469899UL, 0x3e64442dUL
  __ movq(xmm6, ExternalAddress(PI_4 + 8), r8 /*rscratch*/); //0x3fe921fbUL, 0x18469899UL, 0x3e64442dUL
  __ xorpd(xmm5, xmm5);
  __ subl(rdx, 1008);
  __ pinsrw(xmm5, rdx, 3);
  __ mulsd(xmm0, xmm4);
  __ shll(rsi, 16);
  __ sarl(rsi, 31);
  __ mulsd(xmm3, xmm5);
  __ movdqu(xmm1, xmm0);
  __ mulsd(xmm0, xmm2);
  __ shrl(rdi, 29);
  __ addsd(xmm1, xmm3);
  __ mulsd(xmm3, xmm2);
  __ addl(rdi, rsi);
  __ xorl(rdi, rsi);
  __ mulsd(xmm6, xmm1);
  __ movl(rax, rdi);
  __ addsd(xmm6, xmm3);
  __ movdqu(xmm2, xmm0);
  __ addsd(xmm0, xmm6);
  __ subsd(xmm2, xmm0);
  __ addsd(xmm6, xmm2);

  __ bind(L_2TAG_PACKET_12_0_1);
  __ movq(xmm1, ExternalAddress(PI32INV), r8 /*rscratch*/);    //0x6dc9c883UL, 0x40245f30UL
  __ mulsd(xmm1, xmm0);
  __ movq(xmm5, ExternalAddress(ONEHALF), r8 /*rscratch*/);    //0x00000000UL, 0x3fe00000UL, 0x00000000UL, 0x3fe00000UL
  __ movq(xmm4, ExternalAddress(SIGN_MASK), r8 /*rscratch*/);  //0x00000000UL, 0x80000000UL
  __ pand(xmm4, xmm0);
  __ por(xmm5, xmm4);
  __ addpd(xmm1, xmm5);
  __ cvttsd2sil(rdx, xmm1);
  __ cvtsi2sdl(xmm1, rdx);
  __ movq(xmm3, ExternalAddress(P_1), r8 /*rscratch*/);      //0x54400000UL, 0x3fb921fbUL
  __ movdqu(xmm2, ExternalAddress(P_2), r8 /*rscratch*/);    //0x1a600000UL, 0x3d90b461UL, 0x1a600000UL, 0x3d90b461UL
  __ mulsd(xmm3, xmm1);
  __ unpcklpd(xmm1, xmm1);
  __ shll(rax, 3);
  __ addl(rdx, 1865216);
  __ movdqu(xmm4, xmm0);
  __ addl(rdx, rax);
  __ andl(rdx, 63);
  __ movdqu(xmm5, ExternalAddress(SC_4), r8 /*rscratch*/);    //0x54400000UL, 0x3fb921fbUL
  __ lea(rax, ExternalAddress(Ctable));
  __ shll(rdx, 5);
  __ addq(rax, rdx);
  __ mulpd(xmm2, xmm1);
  __ subsd(xmm0, xmm3);
  __ mulsd(xmm1, ExternalAddress(P_3), r8 /*rscratch*/);    //0x2e037073UL, 0x3b63198aUL
  __ subsd(xmm4, xmm3);
  __ movq(xmm7, Address(rax, 8));
  __ unpcklpd(xmm0, xmm0);
  __ movdqu(xmm3, xmm4);
  __ subsd(xmm4, xmm2);
  __ mulpd(xmm5, xmm0);
  __ subpd(xmm0, xmm2);
  __ mulsd(xmm7, xmm4);
  __ subsd(xmm3, xmm4);
  __ mulpd(xmm5, xmm0);
  __ mulpd(xmm0, xmm0);
  __ subsd(xmm3, xmm2);
  __ movdqu(xmm2, Address(rax, 0));
  __ subsd(xmm1, xmm3);
  __ movq(xmm3, Address(rax, 24));
  __ addsd(xmm2, xmm3);
  __ subsd(xmm7, xmm2);
  __ subsd(xmm1, xmm6);
  __ movdqu(xmm6, ExternalAddress(SC_2), r8 /*rscratch*/);    //0x11111111UL, 0x3f811111UL, 0x55555555UL, 0x3fa55555UL
  __ mulsd(xmm2, xmm4);
  __ mulpd(xmm6, xmm0);
  __ mulsd(xmm3, xmm4);
  __ mulpd(xmm2, xmm0);
  __ mulpd(xmm0, xmm0);
  __ addpd(xmm5, ExternalAddress(SC_3), r8 /*rscratch*/);    //0x1a01a01aUL, 0xbf2a01a0UL, 0x16c16c17UL, 0xbf56c16cUL
  __ mulsd(xmm4, Address(rax, 0));
  __ addpd(xmm6, ExternalAddress(SC_1), r8 /*rscratch*/);    //0x55555555UL, 0xbfc55555UL, 0x00000000UL, 0xbfe00000UL
  __ mulpd(xmm5, xmm0);
  __ movdqu(xmm0, xmm3);
  __ addsd(xmm3, Address(rax, 8));
  __ mulpd(xmm1, xmm7);
  __ movdqu(xmm7, xmm4);
  __ addsd(xmm4, xmm3);
  __ addpd(xmm6, xmm5);
  __ movq(xmm5, Address(rax, 8));
  __ subsd(xmm5, xmm3);
  __ subsd(xmm3, xmm4);
  __ addsd(xmm1, Address(rax, 16));
  __ mulpd(xmm6, xmm2);
  __ addsd(xmm5, xmm0);
  __ addsd(xmm3, xmm7);
  __ addsd(xmm1, xmm5);
  __ addsd(xmm1, xmm3);
  __ addsd(xmm1, xmm6);
  __ unpckhpd(xmm6, xmm6);
  __ movdqu(xmm0, xmm4);
  __ addsd(xmm1, xmm6);
  __ addsd(xmm0, xmm1);
  __ jmp(B1_4);

  __ bind(L_2TAG_PACKET_8_0_1);
  __ addl(rdx, 64);
  __ movq(r9, r10);
  __ movq(r10, r8);
  __ movl(r8, 0);
  __ cmpq(r9, 0);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_9_0_1);
  __ addl(rdx, 64);
  __ movq(r9, r10);
  __ movq(r10, r8);
  __ cmpq(r9, 0);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_9_0_1);
  __ xorpd(xmm0, xmm0);
  __ xorpd(xmm6, xmm6);
  __ jmp(L_2TAG_PACKET_12_0_1);

  __ bind(L_2TAG_PACKET_10_0_1);
  __ jcc(Assembler::equal, L_2TAG_PACKET_11_0_1);
  __ negl(rcx);
  __ shrq(r10);
  __ movq(rax, r9);
  __ shrq(r9);
  __ subl(rdx, rcx);
  __ negl(rcx);
  __ addl(rcx, 64);
  __ shlq(rax);
  __ orq(r10, rax);
  __ jmp(L_2TAG_PACKET_11_0_1);

  __ bind(L_2TAG_PACKET_4_0_1);
  __ negl(rcx);
  __ shlq(r9, 32);
  __ orq(r9, r11);
  __ shlq(r9);
  __ movq(rdi, r9);
  __ testl(r9, INT_MIN);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_13_0_1);
  __ shrl(r9);
  __ movl(rbx, 0);
  __ shrq(rdi, 3);
  __ jmp(L_2TAG_PACKET_7_0_1);

  __ bind(L_2TAG_PACKET_5_0_1);
  __ shrl(r9);
  __ movl(rbx, 536870912);
  __ shrl(rbx);
  __ shlq(r9, 32);
  __ orq(r9, r11);
  __ shlq(rbx, 32);
  __ addl(rdi, 536870912);
  __ movl(rcx, 0);
  __ movl(r11, 0);
  __ subq(rcx, r8);
  __ sbbq(r11, r10);
  __ sbbq(rbx, r9);
  __ movq(r8, rcx);
  __ movq(r10, r11);
  __ movq(r9, rbx);
  __ movl(rbx, 32768);
  __ jmp(L_2TAG_PACKET_6_0_1);

  __ bind(L_2TAG_PACKET_13_0_1);
  __ shrl(r9);
  __ mov64(rbx, 0x100000000);
  __ shrq(rbx);
  __ movl(rcx, 0);
  __ movl(r11, 0);
  __ subq(rcx, r8);
  __ sbbq(r11, r10);
  __ sbbq(rbx, r9);
  __ movq(r8, rcx);
  __ movq(r10, r11);
  __ movq(r9, rbx);
  __ movl(rbx, 32768);
  __ shrq(rdi, 3);
  __ addl(rdi, 536870912);
  __ jmp(L_2TAG_PACKET_7_0_1);

  __ bind(L_2TAG_PACKET_3_0_1);
  __ movq(xmm0, Address(rsp, 8));
  __ mulsd(xmm0, ExternalAddress(NEG_ZERO), r8 /*rscratch*/);    //0x00000000UL, 0x80000000UL
  __ movq(Address(rsp, 0), xmm0);

  __ bind(L_2TAG_PACKET_14_0_1);

  __ bind(B1_4);
  __ addq(rsp, 16);
  __ pop_ppx(rbx);

#ifdef _WIN64
  __ pop_ppx(rdi);
  __ pop_ppx(rsi);
#endif

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

#undef __
