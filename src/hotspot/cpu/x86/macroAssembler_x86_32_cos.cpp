/*
* Copyright (c) 2016, 2021, Intel Corporation. All rights reserved.
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

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "macroAssembler_x86.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/globalDefinitions.hpp"

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

// The 32 bit code is at most SSE2 compliant

ATTRIBUTE_ALIGNED(16) static const juint _static_const_table_cos[] =
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
//          eax, ecx, edx, ebx (tmp)

// Code generated by Intel C compiler for LIBM library

void MacroAssembler::fast_cos(XMMRegister xmm0, XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3,
                              XMMRegister xmm4, XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7,
                              Register eax, Register ecx, Register edx, Register tmp) {
  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label start;

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
