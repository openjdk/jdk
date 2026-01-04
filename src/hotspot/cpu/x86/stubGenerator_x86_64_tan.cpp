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
//                     ALGORITHM DESCRIPTION - TAN()
//                     ---------------------
//
// Polynomials coefficients and other constants.
//
// Note that in this algorithm, there is a different polynomial for
// each breakpoint, so there are 32 sets of polynomial coefficients
// as well as 32 instances of the other constants.
//
// The polynomial coefficients and constants are offset from the start
// of the main block as follows:
//
//   0:  c8 | c0
//  16:  c9 | c1
//  32: c10 | c2
//  48: c11 | c3
//  64: c12 | c4
//  80: c13 | c5
//  96: c14 | c6
// 112: c15 | c7
// 128: T_hi
// 136: T_lo
// 144: Sigma
// 152: T_hl
// 160: Tau
// 168: Mask
// 176: (end of block)
//
// The total table size is therefore 5632 bytes.
//
// Note that c0 and c1 are always zero. We could try storing
// other constants here, and just loading the low part of the
// SIMD register in these cases, after ensuring the high part
// is zero.
//
// The higher terms of the polynomial are computed in the *low*
// part of the SIMD register. This is so we can overlap the
// multiplication by r^8 and the unpacking of the other part.
//
// The constants are:
// T_hi + T_lo = accurate constant term in power series
// Sigma + T_hl = accurate coefficient of r in power series (Sigma=1 bit)
// Tau = multiplier for the reciprocal, always -1 or 0
//
// The basic reconstruction formula using these constants is:
//
// High = tau * recip_hi + t_hi
// Med = (sgn * r + t_hl * r)_hi
// Low = (sgn * r + t_hl * r)_lo +
//       tau * recip_lo + T_lo + (T_hl + sigma) * c + pol
//
// where pol = c0 + c1 * r + c2 * r^2 + ... + c15 * r^15
//
// (c0 = c1 = 0, but using them keeps SIMD regularity)
//
// We then do a compensated sum High + Med, add the low parts together
// and then do the final sum.
//
// Here recip_hi + recip_lo is an accurate reciprocal of the remainder
// modulo pi/2
//
// Special cases:
//  tan(NaN) = quiet NaN, and raise invalid exception
//  tan(INF) = NaN and raise invalid exception
//  tan(+/-0) = +/-0
//
/******************************************************************************/

ATTRIBUTE_ALIGNED(16) static const juint _MUL16[] =
{
    0x00000000UL, 0x40300000UL, 0x00000000UL, 0x3ff00000UL
};

ATTRIBUTE_ALIGNED(16) static const juint _sign_mask_tan[] =
{
    0x00000000UL, 0x80000000UL, 0x00000000UL, 0x80000000UL
};

ATTRIBUTE_ALIGNED(16) static const juint _PI32INV_tan[] =
{
    0x6dc9c883UL, 0x3fe45f30UL, 0x6dc9c883UL, 0x40245f30UL
};

ATTRIBUTE_ALIGNED(16) static const juint _P_1_tan[] =
{
    0x54444000UL, 0x3fb921fbUL, 0x54440000UL, 0x3fb921fbUL
};

ATTRIBUTE_ALIGNED(16) static const juint _P_2_tan[] =
{
    0x67674000UL, 0xbd32e7b9UL, 0x4c4c0000UL, 0x3d468c23UL
};

ATTRIBUTE_ALIGNED(16) static const juint _P_3_tan[] =
{
    0x3707344aUL, 0x3aa8a2e0UL, 0x03707345UL, 0x3ae98a2eUL
};

ATTRIBUTE_ALIGNED(16) static const juint _Ctable_tan[] =
{
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x882c10faUL,
    0x3f9664f4UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x55e6c23dUL, 0x3f8226e3UL, 0x55555555UL,
    0x3fd55555UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x0e157de0UL, 0x3f6d6d3dUL, 0x11111111UL, 0x3fc11111UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x452b75e3UL, 0x3f57da36UL,
    0x1ba1ba1cUL, 0x3faba1baUL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x4e435f9bUL,
    0x3f953f83UL, 0x00000000UL, 0x00000000UL, 0x3c6e8e46UL, 0x3f9b74eaUL,
    0x00000000UL, 0x00000000UL, 0xda5b7511UL, 0x3f85ad63UL, 0xdc230b9bUL,
    0x3fb97558UL, 0x26cb3788UL, 0x3f881308UL, 0x76fc4985UL, 0x3fd62ac9UL,
    0x77bb08baUL, 0x3f757c85UL, 0xb6247521UL, 0x3fb1381eUL, 0x5922170cUL,
    0x3f754e95UL, 0x8746482dUL, 0x3fc27f83UL, 0x11055b30UL, 0x3f64e391UL,
    0x3e666320UL, 0x3fa3e609UL, 0x0de9dae3UL, 0x3f6301dfUL, 0x1f1dca06UL,
    0x3fafa8aeUL, 0x8c5b2da2UL, 0x3fb936bbUL, 0x4e88f7a5UL, 0x3c587d05UL,
    0x00000000UL, 0x3ff00000UL, 0xa8935dd9UL, 0x3f83dde2UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x5a279ea3UL, 0x3faa3407UL,
    0x00000000UL, 0x00000000UL, 0x432d65faUL, 0x3fa70153UL, 0x00000000UL,
    0x00000000UL, 0x891a4602UL, 0x3f9d03efUL, 0xd62ca5f8UL, 0x3fca77d9UL,
    0xb35f4628UL, 0x3f97a265UL, 0x433258faUL, 0x3fd8cf51UL, 0xb58fd909UL,
    0x3f8f88e3UL, 0x01771ceaUL, 0x3fc2b154UL, 0xf3562f8eUL, 0x3f888f57UL,
    0xc028a723UL, 0x3fc7370fUL, 0x20b7f9f0UL, 0x3f80f44cUL, 0x214368e9UL,
    0x3fb6dfaaUL, 0x28891863UL, 0x3f79b4b6UL, 0x172dbbf0UL, 0x3fb6cb8eUL,
    0xe0553158UL, 0x3fc975f5UL, 0x593fe814UL, 0x3c2ef5d3UL, 0x00000000UL,
    0x3ff00000UL, 0x03dec550UL, 0x3fa44203UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x9314533eUL, 0x3fbb8ec5UL, 0x00000000UL,
    0x00000000UL, 0x09aa36d0UL, 0x3fb6d3f4UL, 0x00000000UL, 0x00000000UL,
    0xdcb427fdUL, 0x3fb13950UL, 0xd87ab0bbUL, 0x3fd5335eUL, 0xce0ae8a5UL,
    0x3fabb382UL, 0x79143126UL, 0x3fddba41UL, 0x5f2b28d4UL, 0x3fa552f1UL,
    0x59f21a6dUL, 0x3fd015abUL, 0x22c27d95UL, 0x3fa0e984UL, 0xe19fc6aaUL,
    0x3fd0576cUL, 0x8f2c2950UL, 0x3f9a4898UL, 0xc0b3f22cUL, 0x3fc59462UL,
    0x1883a4b8UL, 0x3f94b61cUL, 0x3f838640UL, 0x3fc30eb8UL, 0x355c63dcUL,
    0x3fd36a08UL, 0x1dce993dUL, 0xbc6d704dUL, 0x00000000UL, 0x3ff00000UL,
    0x2b82ab63UL, 0x3fb78e92UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x56f37042UL, 0x3fccfc56UL, 0x00000000UL, 0x00000000UL,
    0xaa563951UL, 0x3fc90125UL, 0x00000000UL, 0x00000000UL, 0x3d0e7c5dUL,
    0x3fc50533UL, 0x9bed9b2eUL, 0x3fdf0ed9UL, 0x5fe7c47cUL, 0x3fc1f250UL,
    0x96c125e5UL, 0x3fe2edd9UL, 0x5a02bbd8UL, 0x3fbe5c71UL, 0x86362c20UL,
    0x3fda08b7UL, 0x4b4435edUL, 0x3fb9d342UL, 0x4b494091UL, 0x3fd911bdUL,
    0xb56658beUL, 0x3fb5e4c7UL, 0x93a2fd76UL, 0x3fd3c092UL, 0xda271794UL,
    0x3fb29910UL, 0x3303df2bUL, 0x3fd189beUL, 0x99fcef32UL, 0x3fda8279UL,
    0xb68c1467UL, 0x3c708b2fUL, 0x00000000UL, 0x3ff00000UL, 0x980c4337UL,
    0x3fc5f619UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0xcc03e501UL, 0x3fdff10fUL, 0x00000000UL, 0x00000000UL, 0x44a4e845UL,
    0x3fddb63bUL, 0x00000000UL, 0x00000000UL, 0x3768ad9fUL, 0x3fdb72a4UL,
    0x3dd01ccaUL, 0x3fe5fdb9UL, 0xa61d2811UL, 0x3fd972b2UL, 0x5645ad0bUL,
    0x3fe977f9UL, 0xd013b3abUL, 0x3fd78ca3UL, 0xbf0bf914UL, 0x3fe4f192UL,
    0x4d53e730UL, 0x3fd5d060UL, 0x3f8b9000UL, 0x3fe49933UL, 0xe2b82f08UL,
    0x3fd4322aUL, 0x5936a835UL, 0x3fe27ae1UL, 0xb1c61c9bUL, 0x3fd2b3fbUL,
    0xef478605UL, 0x3fe1659eUL, 0x190834ecUL, 0x3fe11ab7UL, 0xcdb625eaUL,
    0xbc8e564bUL, 0x00000000UL, 0x3ff00000UL, 0xb07217e3UL, 0x3fd248f1UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x2b2c49d0UL,
    0x3ff2de9cUL, 0x00000000UL, 0x00000000UL, 0x2655bc98UL, 0x3ff33e58UL,
    0x00000000UL, 0x00000000UL, 0xff691fa2UL, 0x3ff3972eUL, 0xe93463bdUL,
    0x3feeed87UL, 0x070e10a0UL, 0x3ff3f5b2UL, 0xf4d790a4UL, 0x3ff20c10UL,
    0xa04e8ea3UL, 0x3ff4541aUL, 0x386accd3UL, 0x3ff1369eUL, 0x222a66ddUL,
    0x3ff4b521UL, 0x22a9777eUL, 0x3ff20817UL, 0x52a04a6eUL, 0x3ff5178fUL,
    0xddaa0031UL, 0x3ff22137UL, 0x4447d47cUL, 0x3ff57c01UL, 0x1e9c7f1dUL,
    0x3ff29311UL, 0x2ab7f990UL, 0x3fe561b8UL, 0x209c7df1UL, 0x3c87a8c5UL,
    0x00000000UL, 0x3ff00000UL, 0x4170bcc6UL, 0x3fdc92d8UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0xc7ab4d5aUL, 0x40085e24UL,
    0x00000000UL, 0x00000000UL, 0xe93ea75dUL, 0x400b963dUL, 0x00000000UL,
    0x00000000UL, 0x94a7f25aUL, 0x400f37e2UL, 0x4b6261cbUL, 0x3ff5f984UL,
    0x5a9dd812UL, 0x4011aab0UL, 0x74c30018UL, 0x3ffaf5a5UL, 0x7f2ce8e3UL,
    0x4013fe8bUL, 0xfe8e54faUL, 0x3ffd7334UL, 0x670d618dUL, 0x4016a10cUL,
    0x4db97058UL, 0x4000e012UL, 0x24df44ddUL, 0x40199c5fUL, 0x697d6eceUL,
    0x4003006eUL, 0x83298b82UL, 0x401cfc4dUL, 0x19d490d6UL, 0x40058c19UL,
    0x2ae42850UL, 0x3fea4300UL, 0x118e20e6UL, 0xbc7a6db8UL, 0x00000000UL,
    0x40000000UL, 0xe33345b8UL, 0xbfd4e526UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x65965966UL, 0x40219659UL, 0x00000000UL,
    0x00000000UL, 0x882c10faUL, 0x402664f4UL, 0x00000000UL, 0x00000000UL,
    0x83cd3723UL, 0x402c8342UL, 0x00000000UL, 0x40000000UL, 0x55e6c23dUL,
    0x403226e3UL, 0x55555555UL, 0x40055555UL, 0x34451939UL, 0x40371c96UL,
    0xaaaaaaabUL, 0x400aaaaaUL, 0x0e157de0UL, 0x403d6d3dUL, 0x11111111UL,
    0x40111111UL, 0xa738201fUL, 0x4042bbceUL, 0x05b05b06UL, 0x4015b05bUL,
    0x452b75e3UL, 0x4047da36UL, 0x1ba1ba1cUL, 0x401ba1baUL, 0x00000000UL,
    0x3ff00000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x40000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x4f48b8d3UL, 0xbf33eaf9UL, 0x00000000UL, 0x00000000UL,
    0x0cf7586fUL, 0x3f20b8eaUL, 0x00000000UL, 0x00000000UL, 0xd0258911UL,
    0xbf0abaf3UL, 0x23e49fe9UL, 0xbfab5a8cUL, 0x2d53222eUL, 0x3ef60d15UL,
    0x21169451UL, 0x3fa172b2UL, 0xbb254dbcUL, 0xbee1d3b5UL, 0xdbf93b8eUL,
    0xbf84c7dbUL, 0x05b4630bUL, 0x3ecd3364UL, 0xee9aada7UL, 0x3f743924UL,
    0x794a8297UL, 0xbeb7b7b9UL, 0xe015f797UL, 0xbf5d41f5UL, 0xe41a4a56UL,
    0x3ea35dfbUL, 0xe4c2a251UL, 0x3f49a2abUL, 0x5af9e000UL, 0xbfce49ceUL,
    0x8c743719UL, 0x3d1eb860UL, 0x00000000UL, 0x00000000UL, 0x1b4863cfUL,
    0x3fd78294UL, 0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0xfffffff8UL,
    0x535ad890UL, 0xbf2b9320UL, 0x00000000UL, 0x00000000UL, 0x018fdf1fUL,
    0x3f16d61dUL, 0x00000000UL, 0x00000000UL, 0x0359f1beUL, 0xbf0139e4UL,
    0xa4317c6dUL, 0xbfa67e17UL, 0x82672d0fUL, 0x3eebb405UL, 0x2f1b621eUL,
    0x3f9f455bUL, 0x51ccf238UL, 0xbed55317UL, 0xf437b9acUL, 0xbf804beeUL,
    0xc791a2b5UL, 0x3ec0e993UL, 0x919a1db2UL, 0x3f7080c2UL, 0x336a5b0eUL,
    0xbeaa48a2UL, 0x0a268358UL, 0xbf55a443UL, 0xdfd978e4UL, 0x3e94b61fUL,
    0xd7767a58UL, 0x3f431806UL, 0x2aea0000UL, 0xbfc9bbe8UL, 0x7723ea61UL,
    0xbd3a2369UL, 0x00000000UL, 0x00000000UL, 0xdf7796ffUL, 0x3fd6e642UL,
    0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0xfffffff8UL, 0xb9ff07ceUL,
    0xbf231c78UL, 0x00000000UL, 0x00000000UL, 0xa5517182UL, 0x3f0ff0e0UL,
    0x00000000UL, 0x00000000UL, 0x790b4cbcUL, 0xbef66191UL, 0x848a46c6UL,
    0xbfa21ac0UL, 0xb16435faUL, 0x3ee1d3ecUL, 0x2a1aa832UL, 0x3f9c71eaUL,
    0xfdd299efUL, 0xbec9dd1aUL, 0x3f8dbaafUL, 0xbf793363UL, 0x309fc6eaUL,
    0x3eb415d6UL, 0xbee60471UL, 0x3f6b83baUL, 0x94a0a697UL, 0xbe9dae11UL,
    0x3e5c67b3UL, 0xbf4fd07bUL, 0x9a8f3e3eUL, 0x3e86bd75UL, 0xa4beb7a4UL,
    0x3f3d1eb1UL, 0x29cfc000UL, 0xbfc549ceUL, 0xbf159358UL, 0xbd397b33UL,
    0x00000000UL, 0x00000000UL, 0x871fee6cUL, 0x3fd666f0UL, 0x00000000UL,
    0x3ff00000UL, 0x00000000UL, 0xfffffff8UL, 0x7d98a556UL, 0xbf1a3958UL,
    0x00000000UL, 0x00000000UL, 0x9d88dc01UL, 0x3f0704c2UL, 0x00000000UL,
    0x00000000UL, 0x73742a2bUL, 0xbeed054aUL, 0x58844587UL, 0xbf9c2a13UL,
    0x55688a79UL, 0x3ed7a326UL, 0xee33f1d6UL, 0x3f9a48f4UL, 0xa8dc9888UL,
    0xbebf8939UL, 0xaad4b5b8UL, 0xbf72f746UL, 0x9102efa1UL, 0x3ea88f82UL,
    0xdabc29cfUL, 0x3f678228UL, 0x9289afb8UL, 0xbe90f456UL, 0x741fb4edUL,
    0xbf46f3a3UL, 0xa97f6663UL, 0x3e79b4bfUL, 0xca89ff3fUL, 0x3f36db70UL,
    0xa8a2a000UL, 0xbfc0ee13UL, 0x3da24be1UL, 0xbd338b9fUL, 0x00000000UL,
    0x00000000UL, 0x11cd6c69UL, 0x3fd601fdUL, 0x00000000UL, 0x3ff00000UL,
    0x00000000UL, 0xfffffff8UL, 0x1a154b97UL, 0xbf116b01UL, 0x00000000UL,
    0x00000000UL, 0x2d427630UL, 0x3f0147bfUL, 0x00000000UL, 0x00000000UL,
    0xb93820c8UL, 0xbee264d4UL, 0xbb6cbb18UL, 0xbf94ab8cUL, 0x888d4d92UL,
    0x3ed0568bUL, 0x60730f7cUL, 0x3f98b19bUL, 0xe4b1fb11UL, 0xbeb2f950UL,
    0x22cf9f74UL, 0xbf6b21cdUL, 0x4a3ff0a6UL, 0x3e9f499eUL, 0xfd2b83ceUL,
    0x3f64aad7UL, 0x637b73afUL, 0xbe83487cUL, 0xe522591aUL, 0xbf3fc092UL,
    0xa158e8bcUL, 0x3e6e3aaeUL, 0xe5e82ffaUL, 0x3f329d2fUL, 0xd636a000UL,
    0xbfb9477fUL, 0xc2c2d2bcUL, 0xbd135ef9UL, 0x00000000UL, 0x00000000UL,
    0xf2fdb123UL, 0x3fd5b566UL, 0x00000000UL, 0x3ff00000UL, 0x00000000UL,
    0xfffffff8UL, 0xc41acb64UL, 0xbf05448dUL, 0x00000000UL, 0x00000000UL,
    0xdbb03d6fUL, 0x3efb7ad2UL, 0x00000000UL, 0x00000000UL, 0x9e42962dUL,
    0xbed5aea5UL, 0x2579f8efUL, 0xbf8b2398UL, 0x288a1ed9UL, 0x3ec81441UL,
    0xb0198dc5UL, 0x3f979a3aUL, 0x2fdfe253UL, 0xbea57cd3UL, 0x5766336fUL,
    0xbf617caaUL, 0x600944c3UL, 0x3e954ed6UL, 0xa4e0aaf8UL, 0x3f62c646UL,
    0x6b8fb29cUL, 0xbe74e3a3UL, 0xdc4c0409UL, 0xbf33f952UL, 0x9bffe365UL,
    0x3e6301ecUL, 0xb8869e44UL, 0x3f2fc566UL, 0xe1e04000UL, 0xbfb0cc62UL,
    0x016b907fUL, 0xbd119cbcUL, 0x00000000UL, 0x00000000UL, 0xe6b9d8faUL,
    0x3fd57fb3UL, 0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0xfffffff8UL,
    0x5daf22a6UL, 0xbef429d7UL, 0x00000000UL, 0x00000000UL, 0x06bca545UL,
    0x3ef7a27dUL, 0x00000000UL, 0x00000000UL, 0x7211c19aUL, 0xbec41c3eUL,
    0x956ed53eUL, 0xbf7ae3f4UL, 0xee750e72UL, 0x3ec3901bUL, 0x91d443f5UL,
    0x3f96f713UL, 0x36661e6cUL, 0xbe936e09UL, 0x506f9381UL, 0xbf5122e8UL,
    0xcb6dd43fUL, 0x3e9041b9UL, 0x6698b2ffUL, 0x3f61b0c7UL, 0x576bf12bUL,
    0xbe625a8aUL, 0xe5a0e9dcUL, 0xbf23499dUL, 0x110384ddUL, 0x3e5b1c2cUL,
    0x68d43db6UL, 0x3f2cb899UL, 0x6ecac000UL, 0xbfa0c414UL, 0xcd7dd58cUL,
    0x3d13500fUL, 0x00000000UL, 0x00000000UL, 0x85a2c8fbUL, 0x3fd55fe0UL,
    0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0xfffffff8UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x2bf70ebeUL, 0x3ef66a8fUL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0xd644267fUL, 0x3ec22805UL, 0x16c16c17UL, 0x3f96c16cUL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0xc4e09162UL,
    0x3e8d6db2UL, 0xbc011567UL, 0x3f61566aUL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x1f79955cUL, 0x3e57da4eUL, 0x9334ef0bUL,
    0x3f2bbd77UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x55555555UL, 0x3fd55555UL, 0x00000000UL,
    0x3ff00000UL, 0x00000000UL, 0xfffffff8UL, 0x5daf22a6UL, 0x3ef429d7UL,
    0x00000000UL, 0x00000000UL, 0x06bca545UL, 0x3ef7a27dUL, 0x00000000UL,
    0x00000000UL, 0x7211c19aUL, 0x3ec41c3eUL, 0x956ed53eUL, 0x3f7ae3f4UL,
    0xee750e72UL, 0x3ec3901bUL, 0x91d443f5UL, 0x3f96f713UL, 0x36661e6cUL,
    0x3e936e09UL, 0x506f9381UL, 0x3f5122e8UL, 0xcb6dd43fUL, 0x3e9041b9UL,
    0x6698b2ffUL, 0x3f61b0c7UL, 0x576bf12bUL, 0x3e625a8aUL, 0xe5a0e9dcUL,
    0x3f23499dUL, 0x110384ddUL, 0x3e5b1c2cUL, 0x68d43db6UL, 0x3f2cb899UL,
    0x6ecac000UL, 0x3fa0c414UL, 0xcd7dd58cUL, 0xbd13500fUL, 0x00000000UL,
    0x00000000UL, 0x85a2c8fbUL, 0x3fd55fe0UL, 0x00000000UL, 0x3ff00000UL,
    0x00000000UL, 0xfffffff8UL, 0xc41acb64UL, 0x3f05448dUL, 0x00000000UL,
    0x00000000UL, 0xdbb03d6fUL, 0x3efb7ad2UL, 0x00000000UL, 0x00000000UL,
    0x9e42962dUL, 0x3ed5aea5UL, 0x2579f8efUL, 0x3f8b2398UL, 0x288a1ed9UL,
    0x3ec81441UL, 0xb0198dc5UL, 0x3f979a3aUL, 0x2fdfe253UL, 0x3ea57cd3UL,
    0x5766336fUL, 0x3f617caaUL, 0x600944c3UL, 0x3e954ed6UL, 0xa4e0aaf8UL,
    0x3f62c646UL, 0x6b8fb29cUL, 0x3e74e3a3UL, 0xdc4c0409UL, 0x3f33f952UL,
    0x9bffe365UL, 0x3e6301ecUL, 0xb8869e44UL, 0x3f2fc566UL, 0xe1e04000UL,
    0x3fb0cc62UL, 0x016b907fUL, 0x3d119cbcUL, 0x00000000UL, 0x00000000UL,
    0xe6b9d8faUL, 0x3fd57fb3UL, 0x00000000UL, 0x3ff00000UL, 0x00000000UL,
    0xfffffff8UL, 0x1a154b97UL, 0x3f116b01UL, 0x00000000UL, 0x00000000UL,
    0x2d427630UL, 0x3f0147bfUL, 0x00000000UL, 0x00000000UL, 0xb93820c8UL,
    0x3ee264d4UL, 0xbb6cbb18UL, 0x3f94ab8cUL, 0x888d4d92UL, 0x3ed0568bUL,
    0x60730f7cUL, 0x3f98b19bUL, 0xe4b1fb11UL, 0x3eb2f950UL, 0x22cf9f74UL,
    0x3f6b21cdUL, 0x4a3ff0a6UL, 0x3e9f499eUL, 0xfd2b83ceUL, 0x3f64aad7UL,
    0x637b73afUL, 0x3e83487cUL, 0xe522591aUL, 0x3f3fc092UL, 0xa158e8bcUL,
    0x3e6e3aaeUL, 0xe5e82ffaUL, 0x3f329d2fUL, 0xd636a000UL, 0x3fb9477fUL,
    0xc2c2d2bcUL, 0x3d135ef9UL, 0x00000000UL, 0x00000000UL, 0xf2fdb123UL,
    0x3fd5b566UL, 0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0xfffffff8UL,
    0x7d98a556UL, 0x3f1a3958UL, 0x00000000UL, 0x00000000UL, 0x9d88dc01UL,
    0x3f0704c2UL, 0x00000000UL, 0x00000000UL, 0x73742a2bUL, 0x3eed054aUL,
    0x58844587UL, 0x3f9c2a13UL, 0x55688a79UL, 0x3ed7a326UL, 0xee33f1d6UL,
    0x3f9a48f4UL, 0xa8dc9888UL, 0x3ebf8939UL, 0xaad4b5b8UL, 0x3f72f746UL,
    0x9102efa1UL, 0x3ea88f82UL, 0xdabc29cfUL, 0x3f678228UL, 0x9289afb8UL,
    0x3e90f456UL, 0x741fb4edUL, 0x3f46f3a3UL, 0xa97f6663UL, 0x3e79b4bfUL,
    0xca89ff3fUL, 0x3f36db70UL, 0xa8a2a000UL, 0x3fc0ee13UL, 0x3da24be1UL,
    0x3d338b9fUL, 0x00000000UL, 0x00000000UL, 0x11cd6c69UL, 0x3fd601fdUL,
    0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0xfffffff8UL, 0xb9ff07ceUL,
    0x3f231c78UL, 0x00000000UL, 0x00000000UL, 0xa5517182UL, 0x3f0ff0e0UL,
    0x00000000UL, 0x00000000UL, 0x790b4cbcUL, 0x3ef66191UL, 0x848a46c6UL,
    0x3fa21ac0UL, 0xb16435faUL, 0x3ee1d3ecUL, 0x2a1aa832UL, 0x3f9c71eaUL,
    0xfdd299efUL, 0x3ec9dd1aUL, 0x3f8dbaafUL, 0x3f793363UL, 0x309fc6eaUL,
    0x3eb415d6UL, 0xbee60471UL, 0x3f6b83baUL, 0x94a0a697UL, 0x3e9dae11UL,
    0x3e5c67b3UL, 0x3f4fd07bUL, 0x9a8f3e3eUL, 0x3e86bd75UL, 0xa4beb7a4UL,
    0x3f3d1eb1UL, 0x29cfc000UL, 0x3fc549ceUL, 0xbf159358UL, 0x3d397b33UL,
    0x00000000UL, 0x00000000UL, 0x871fee6cUL, 0x3fd666f0UL, 0x00000000UL,
    0x3ff00000UL, 0x00000000UL, 0xfffffff8UL, 0x535ad890UL, 0x3f2b9320UL,
    0x00000000UL, 0x00000000UL, 0x018fdf1fUL, 0x3f16d61dUL, 0x00000000UL,
    0x00000000UL, 0x0359f1beUL, 0x3f0139e4UL, 0xa4317c6dUL, 0x3fa67e17UL,
    0x82672d0fUL, 0x3eebb405UL, 0x2f1b621eUL, 0x3f9f455bUL, 0x51ccf238UL,
    0x3ed55317UL, 0xf437b9acUL, 0x3f804beeUL, 0xc791a2b5UL, 0x3ec0e993UL,
    0x919a1db2UL, 0x3f7080c2UL, 0x336a5b0eUL, 0x3eaa48a2UL, 0x0a268358UL,
    0x3f55a443UL, 0xdfd978e4UL, 0x3e94b61fUL, 0xd7767a58UL, 0x3f431806UL,
    0x2aea0000UL, 0x3fc9bbe8UL, 0x7723ea61UL, 0x3d3a2369UL, 0x00000000UL,
    0x00000000UL, 0xdf7796ffUL, 0x3fd6e642UL, 0x00000000UL, 0x3ff00000UL,
    0x00000000UL, 0xfffffff8UL, 0x4f48b8d3UL, 0x3f33eaf9UL, 0x00000000UL,
    0x00000000UL, 0x0cf7586fUL, 0x3f20b8eaUL, 0x00000000UL, 0x00000000UL,
    0xd0258911UL, 0x3f0abaf3UL, 0x23e49fe9UL, 0x3fab5a8cUL, 0x2d53222eUL,
    0x3ef60d15UL, 0x21169451UL, 0x3fa172b2UL, 0xbb254dbcUL, 0x3ee1d3b5UL,
    0xdbf93b8eUL, 0x3f84c7dbUL, 0x05b4630bUL, 0x3ecd3364UL, 0xee9aada7UL,
    0x3f743924UL, 0x794a8297UL, 0x3eb7b7b9UL, 0xe015f797UL, 0x3f5d41f5UL,
    0xe41a4a56UL, 0x3ea35dfbUL, 0xe4c2a251UL, 0x3f49a2abUL, 0x5af9e000UL,
    0x3fce49ceUL, 0x8c743719UL, 0xbd1eb860UL, 0x00000000UL, 0x00000000UL,
    0x1b4863cfUL, 0x3fd78294UL, 0x00000000UL, 0x3ff00000UL, 0x00000000UL,
    0xfffffff8UL, 0x65965966UL, 0xc0219659UL, 0x00000000UL, 0x00000000UL,
    0x882c10faUL, 0x402664f4UL, 0x00000000UL, 0x00000000UL, 0x83cd3723UL,
    0xc02c8342UL, 0x00000000UL, 0xc0000000UL, 0x55e6c23dUL, 0x403226e3UL,
    0x55555555UL, 0x40055555UL, 0x34451939UL, 0xc0371c96UL, 0xaaaaaaabUL,
    0xc00aaaaaUL, 0x0e157de0UL, 0x403d6d3dUL, 0x11111111UL, 0x40111111UL,
    0xa738201fUL, 0xc042bbceUL, 0x05b05b06UL, 0xc015b05bUL, 0x452b75e3UL,
    0x4047da36UL, 0x1ba1ba1cUL, 0x401ba1baUL, 0x00000000UL, 0xbff00000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x40000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0xc7ab4d5aUL, 0xc0085e24UL, 0x00000000UL, 0x00000000UL, 0xe93ea75dUL,
    0x400b963dUL, 0x00000000UL, 0x00000000UL, 0x94a7f25aUL, 0xc00f37e2UL,
    0x4b6261cbUL, 0xbff5f984UL, 0x5a9dd812UL, 0x4011aab0UL, 0x74c30018UL,
    0x3ffaf5a5UL, 0x7f2ce8e3UL, 0xc013fe8bUL, 0xfe8e54faUL, 0xbffd7334UL,
    0x670d618dUL, 0x4016a10cUL, 0x4db97058UL, 0x4000e012UL, 0x24df44ddUL,
    0xc0199c5fUL, 0x697d6eceUL, 0xc003006eUL, 0x83298b82UL, 0x401cfc4dUL,
    0x19d490d6UL, 0x40058c19UL, 0x2ae42850UL, 0xbfea4300UL, 0x118e20e6UL,
    0x3c7a6db8UL, 0x00000000UL, 0x40000000UL, 0xe33345b8UL, 0xbfd4e526UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x2b2c49d0UL,
    0xbff2de9cUL, 0x00000000UL, 0x00000000UL, 0x2655bc98UL, 0x3ff33e58UL,
    0x00000000UL, 0x00000000UL, 0xff691fa2UL, 0xbff3972eUL, 0xe93463bdUL,
    0xbfeeed87UL, 0x070e10a0UL, 0x3ff3f5b2UL, 0xf4d790a4UL, 0x3ff20c10UL,
    0xa04e8ea3UL, 0xbff4541aUL, 0x386accd3UL, 0xbff1369eUL, 0x222a66ddUL,
    0x3ff4b521UL, 0x22a9777eUL, 0x3ff20817UL, 0x52a04a6eUL, 0xbff5178fUL,
    0xddaa0031UL, 0xbff22137UL, 0x4447d47cUL, 0x3ff57c01UL, 0x1e9c7f1dUL,
    0x3ff29311UL, 0x2ab7f990UL, 0xbfe561b8UL, 0x209c7df1UL, 0xbc87a8c5UL,
    0x00000000UL, 0x3ff00000UL, 0x4170bcc6UL, 0x3fdc92d8UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0xcc03e501UL, 0xbfdff10fUL,
    0x00000000UL, 0x00000000UL, 0x44a4e845UL, 0x3fddb63bUL, 0x00000000UL,
    0x00000000UL, 0x3768ad9fUL, 0xbfdb72a4UL, 0x3dd01ccaUL, 0xbfe5fdb9UL,
    0xa61d2811UL, 0x3fd972b2UL, 0x5645ad0bUL, 0x3fe977f9UL, 0xd013b3abUL,
    0xbfd78ca3UL, 0xbf0bf914UL, 0xbfe4f192UL, 0x4d53e730UL, 0x3fd5d060UL,
    0x3f8b9000UL, 0x3fe49933UL, 0xe2b82f08UL, 0xbfd4322aUL, 0x5936a835UL,
    0xbfe27ae1UL, 0xb1c61c9bUL, 0x3fd2b3fbUL, 0xef478605UL, 0x3fe1659eUL,
    0x190834ecUL, 0xbfe11ab7UL, 0xcdb625eaUL, 0x3c8e564bUL, 0x00000000UL,
    0x3ff00000UL, 0xb07217e3UL, 0x3fd248f1UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x56f37042UL, 0xbfccfc56UL, 0x00000000UL,
    0x00000000UL, 0xaa563951UL, 0x3fc90125UL, 0x00000000UL, 0x00000000UL,
    0x3d0e7c5dUL, 0xbfc50533UL, 0x9bed9b2eUL, 0xbfdf0ed9UL, 0x5fe7c47cUL,
    0x3fc1f250UL, 0x96c125e5UL, 0x3fe2edd9UL, 0x5a02bbd8UL, 0xbfbe5c71UL,
    0x86362c20UL, 0xbfda08b7UL, 0x4b4435edUL, 0x3fb9d342UL, 0x4b494091UL,
    0x3fd911bdUL, 0xb56658beUL, 0xbfb5e4c7UL, 0x93a2fd76UL, 0xbfd3c092UL,
    0xda271794UL, 0x3fb29910UL, 0x3303df2bUL, 0x3fd189beUL, 0x99fcef32UL,
    0xbfda8279UL, 0xb68c1467UL, 0xbc708b2fUL, 0x00000000UL, 0x3ff00000UL,
    0x980c4337UL, 0x3fc5f619UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL, 0x9314533eUL, 0xbfbb8ec5UL, 0x00000000UL, 0x00000000UL,
    0x09aa36d0UL, 0x3fb6d3f4UL, 0x00000000UL, 0x00000000UL, 0xdcb427fdUL,
    0xbfb13950UL, 0xd87ab0bbUL, 0xbfd5335eUL, 0xce0ae8a5UL, 0x3fabb382UL,
    0x79143126UL, 0x3fddba41UL, 0x5f2b28d4UL, 0xbfa552f1UL, 0x59f21a6dUL,
    0xbfd015abUL, 0x22c27d95UL, 0x3fa0e984UL, 0xe19fc6aaUL, 0x3fd0576cUL,
    0x8f2c2950UL, 0xbf9a4898UL, 0xc0b3f22cUL, 0xbfc59462UL, 0x1883a4b8UL,
    0x3f94b61cUL, 0x3f838640UL, 0x3fc30eb8UL, 0x355c63dcUL, 0xbfd36a08UL,
    0x1dce993dUL, 0x3c6d704dUL, 0x00000000UL, 0x3ff00000UL, 0x2b82ab63UL,
    0x3fb78e92UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x5a279ea3UL, 0xbfaa3407UL, 0x00000000UL, 0x00000000UL, 0x432d65faUL,
    0x3fa70153UL, 0x00000000UL, 0x00000000UL, 0x891a4602UL, 0xbf9d03efUL,
    0xd62ca5f8UL, 0xbfca77d9UL, 0xb35f4628UL, 0x3f97a265UL, 0x433258faUL,
    0x3fd8cf51UL, 0xb58fd909UL, 0xbf8f88e3UL, 0x01771ceaUL, 0xbfc2b154UL,
    0xf3562f8eUL, 0x3f888f57UL, 0xc028a723UL, 0x3fc7370fUL, 0x20b7f9f0UL,
    0xbf80f44cUL, 0x214368e9UL, 0xbfb6dfaaUL, 0x28891863UL, 0x3f79b4b6UL,
    0x172dbbf0UL, 0x3fb6cb8eUL, 0xe0553158UL, 0xbfc975f5UL, 0x593fe814UL,
    0xbc2ef5d3UL, 0x00000000UL, 0x3ff00000UL, 0x03dec550UL, 0x3fa44203UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL, 0x00000000UL, 0x4e435f9bUL,
    0xbf953f83UL, 0x00000000UL, 0x00000000UL, 0x3c6e8e46UL, 0x3f9b74eaUL,
    0x00000000UL, 0x00000000UL, 0xda5b7511UL, 0xbf85ad63UL, 0xdc230b9bUL,
    0xbfb97558UL, 0x26cb3788UL, 0x3f881308UL, 0x76fc4985UL, 0x3fd62ac9UL,
    0x77bb08baUL, 0xbf757c85UL, 0xb6247521UL, 0xbfb1381eUL, 0x5922170cUL,
    0x3f754e95UL, 0x8746482dUL, 0x3fc27f83UL, 0x11055b30UL, 0xbf64e391UL,
    0x3e666320UL, 0xbfa3e609UL, 0x0de9dae3UL, 0x3f6301dfUL, 0x1f1dca06UL,
    0x3fafa8aeUL, 0x8c5b2da2UL, 0xbfb936bbUL, 0x4e88f7a5UL, 0xbc587d05UL,
    0x00000000UL, 0x3ff00000UL, 0xa8935dd9UL, 0x3f83dde2UL, 0x00000000UL,
    0x00000000UL, 0x00000000UL, 0x00000000UL
};

ATTRIBUTE_ALIGNED(16) static const juint _MASK_35_tan[] =
{
    0xfffc0000UL, 0xffffffffUL, 0x00000000UL, 0x00000000UL
};

ATTRIBUTE_ALIGNED(16) static const juint _Q_11_tan[] =
{
    0xb8fe4d77UL, 0x3f82609aUL
};

ATTRIBUTE_ALIGNED(16) static const juint _Q_9_tan[] =
{
    0xbf847a43UL, 0x3f9664a0UL
};

ATTRIBUTE_ALIGNED(16) static const juint _Q_7_tan[] =
{
    0x52c4c8abUL, 0x3faba1baUL
};

ATTRIBUTE_ALIGNED(16) static const juint _Q_5_tan[] =
{
    0x11092746UL, 0x3fc11111UL
};

ATTRIBUTE_ALIGNED(16) static const juint _Q_3_tan[] =
{
    0x55555612UL, 0x3fd55555UL
};

ATTRIBUTE_ALIGNED(8) static const juint _PI_4_tan[] =
{
    0x00000000UL, 0x3fe921fbUL, 0x4611a626UL, 0x3e85110bUL
};

ATTRIBUTE_ALIGNED(8) static const juint _QQ_2_tan[] =
{
    0x676733afUL, 0x3d32e7b9UL
};

#define __ _masm->

address StubGenerator::generate_libmTan() {
  StubId stub_id = StubId::stubgen_dtan_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();

  Label L_2TAG_PACKET_0_0_1, L_2TAG_PACKET_1_0_1, L_2TAG_PACKET_2_0_1, L_2TAG_PACKET_3_0_1;
  Label L_2TAG_PACKET_4_0_1, L_2TAG_PACKET_5_0_1, L_2TAG_PACKET_6_0_1, L_2TAG_PACKET_7_0_1;
  Label L_2TAG_PACKET_8_0_1, L_2TAG_PACKET_9_0_1, L_2TAG_PACKET_10_0_1, L_2TAG_PACKET_11_0_1;
  Label L_2TAG_PACKET_12_0_1, L_2TAG_PACKET_13_0_1, L_2TAG_PACKET_14_0_1, B1_2, B1_4;

  address MUL16     = (address)_MUL16;
  address sign_mask = (address)_sign_mask_tan;
  address PI32INV   = (address)_PI32INV_tan;
  address P_1       = (address)_P_1_tan;
  address P_2       = (address)_P_2_tan;
  address P_3       = (address)_P_3_tan;
  address Ctable    = (address)_Ctable_tan;
  address MASK_35   = (address)_MASK_35_tan;
  address Q_11      = (address)_Q_11_tan;
  address Q_9       = (address)_Q_9_tan;
  address Q_7       = (address)_Q_7_tan;
  address Q_5       = (address)_Q_5_tan;
  address Q_3       = (address)_Q_3_tan;
  address PI_4      = (address)_PI_4_tan;
  address QQ_2      = (address)_QQ_2_tan;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

#ifdef _WIN64
  __ push_ppx(rsi);
  __ push_ppx(rdi);
#endif

  __ push_ppx(rbx);
  __ subq(rsp, 16);
  __ movsd(Address(rsp, 8), xmm0);

  __ bind(B1_2);
  __ pextrw(rax, xmm0, 3);
  __ andl(rax, 32767);
  __ subl(rax, 16314);
  __ cmpl(rax, 270);
  __ jcc(Assembler::above, L_2TAG_PACKET_0_0_1);
  __ movdqu(xmm5, ExternalAddress(ONEHALF), rbx /*rscratch*/);    //0x00000000UL, 0x3fe00000UL, 0x00000000UL, 0x3fe00000UL
  __ movdqu(xmm6, ExternalAddress(MUL16), rbx /*rscratch*/);    //0x00000000UL, 0x40300000UL, 0x00000000UL, 0x3ff00000UL
  __ unpcklpd(xmm0, xmm0);
  __ movdqu(xmm4, ExternalAddress(sign_mask), rbx /*rscratch*/);    //0x00000000UL, 0x80000000UL, 0x00000000UL, 0x80000000UL
  __ andpd(xmm4, xmm0);
  __ movdqu(xmm1, ExternalAddress(PI32INV), rbx /*rscratch*/);    //0x6dc9c883UL, 0x3fe45f30UL, 0x6dc9c883UL, 0x40245f30UL
  __ mulpd(xmm1, xmm0);
  __ por(xmm5, xmm4);
  __ addpd(xmm1, xmm5);
  __ movdqu(xmm7, xmm1);
  __ unpckhpd(xmm7, xmm7);
  __ cvttsd2sil(rdx, xmm7);
  __ cvttpd2dq(xmm1, xmm1);
  __ cvtdq2pd(xmm1, xmm1);
  __ mulpd(xmm1, xmm6);
  __ movdqu(xmm3, ExternalAddress(P_1), rbx /*rscratch*/);    //0x54444000UL, 0x3fb921fbUL, 0x54440000UL, 0x3fb921fbUL
  __ movq(xmm5, ExternalAddress(QQ_2), rbx /*rscratch*/);    //0x676733afUL, 0x3d32e7b9UL
  __ addq(rdx, 469248);
  __ movdqu(xmm4, ExternalAddress(P_2), rbx /*rscratch*/);    //0x67674000UL, 0xbd32e7b9UL, 0x4c4c0000UL, 0x3d468c23UL
  __ mulpd(xmm3, xmm1);
  __ andq(rdx, 31);
  __ mulsd(xmm5, xmm1);
  __ movq(rcx, rdx);
  __ mulpd(xmm4, xmm1);
  __ shlq(rcx, 1);
  __ subpd(xmm0, xmm3);
  __ mulpd(xmm1, ExternalAddress(P_3), rbx /*rscratch*/);    //0x3707344aUL, 0x3aa8a2e0UL, 0x03707345UL, 0x3ae98a2eUL
  __ addq(rdx, rcx);
  __ shlq(rcx, 2);
  __ addq(rdx, rcx);
  __ addsd(xmm5, xmm0);
  __ movdqu(xmm2, xmm0);
  __ subpd(xmm0, xmm4);
  __ movq(xmm6, ExternalAddress(ONE), rbx /*rscratch*/);    //0x00000000UL, 0x3ff00000UL
  __ shlq(rdx, 4);
  __ lea(rax, ExternalAddress(Ctable));
  __ andpd(xmm5, ExternalAddress(MASK_35), rbx /*rscratch*/);    //0xfffc0000UL, 0xffffffffUL, 0x00000000UL, 0x00000000UL
  __ movdqu(xmm3, xmm0);
  __ addq(rax, rdx);
  __ subpd(xmm2, xmm0);
  __ unpckhpd(xmm0, xmm0);
  __ divsd(xmm6, xmm5);
  __ subpd(xmm2, xmm4);
  __ movdqu(xmm7, Address(rax, 16));
  __ subsd(xmm3, xmm5);
  __ mulpd(xmm7, xmm0);
  __ subpd(xmm2, xmm1);
  __ movdqu(xmm1, Address(rax, 48));
  __ mulpd(xmm1, xmm0);
  __ movdqu(xmm4, Address(rax, 96));
  __ mulpd(xmm4, xmm0);
  __ addsd(xmm2, xmm3);
  __ movdqu(xmm3, xmm0);
  __ mulpd(xmm0, xmm0);
  __ addpd(xmm7, Address(rax, 0));
  __ addpd(xmm1, Address(rax, 32));
  __ mulpd(xmm1, xmm0);
  __ addpd(xmm4, Address(rax, 80));
  __ addpd(xmm7, xmm1);
  __ movdqu(xmm1, Address(rax, 112));
  __ mulpd(xmm1, xmm0);
  __ mulpd(xmm0, xmm0);
  __ addpd(xmm4, xmm1);
  __ movdqu(xmm1, Address(rax, 64));
  __ mulpd(xmm1, xmm0);
  __ addpd(xmm7, xmm1);
  __ movdqu(xmm1, xmm3);
  __ mulpd(xmm3, xmm0);
  __ mulsd(xmm0, xmm0);
  __ mulpd(xmm1, Address(rax, 144));
  __ mulpd(xmm4, xmm3);
  __ movdqu(xmm3, xmm1);
  __ addpd(xmm7, xmm4);
  __ movdqu(xmm4, xmm1);
  __ mulsd(xmm0, xmm7);
  __ unpckhpd(xmm7, xmm7);
  __ addsd(xmm0, xmm7);
  __ unpckhpd(xmm1, xmm1);
  __ addsd(xmm3, xmm1);
  __ subsd(xmm4, xmm3);
  __ addsd(xmm1, xmm4);
  __ movdqu(xmm4, xmm2);
  __ movq(xmm7, Address(rax, 144));
  __ unpckhpd(xmm2, xmm2);
  __ addsd(xmm7, Address(rax, 152));
  __ mulsd(xmm7, xmm2);
  __ addsd(xmm7, Address(rax, 136));
  __ addsd(xmm7, xmm1);
  __ addsd(xmm0, xmm7);
  __ movq(xmm7, ExternalAddress(ONE), rbx /*rscratch*/);    //0x00000000UL, 0x3ff00000UL
  __ mulsd(xmm4, xmm6);
  __ movq(xmm2, Address(rax, 168));
  __ andpd(xmm2, xmm6);
  __ mulsd(xmm5, xmm2);
  __ mulsd(xmm6, Address(rax, 160));
  __ subsd(xmm7, xmm5);
  __ subsd(xmm2, Address(rax, 128));
  __ subsd(xmm7, xmm4);
  __ mulsd(xmm7, xmm6);
  __ movdqu(xmm4, xmm3);
  __ subsd(xmm3, xmm2);
  __ addsd(xmm2, xmm3);
  __ subsd(xmm4, xmm2);
  __ addsd(xmm0, xmm4);
  __ subsd(xmm0, xmm7);
  __ addsd(xmm0, xmm3);
  __ jmp(B1_4);

  __ bind(L_2TAG_PACKET_0_0_1);
  __ jcc(Assembler::greater, L_2TAG_PACKET_1_0_1);
  __ pextrw(rax, xmm0, 3);
  __ movl(rdx, rax);
  __ andl(rax, 32752);
  __ jcc(Assembler::equal, L_2TAG_PACKET_2_0_1);
  __ andl(rdx, 32767);
  __ cmpl(rdx, 15904);
  __ jcc(Assembler::below, L_2TAG_PACKET_3_0_1);
  __ movdqu(xmm2, xmm0);
  __ movdqu(xmm3, xmm0);
  __ movq(xmm1, ExternalAddress(Q_11), rbx /*rscratch*/);    //0xb8fe4d77UL, 0x3f82609aUL
  __ mulsd(xmm2, xmm0);
  __ mulsd(xmm3, xmm2);
  __ mulsd(xmm1, xmm2);
  __ addsd(xmm1, ExternalAddress(Q_9), rbx /*rscratch*/);    //0xbf847a43UL, 0x3f9664a0UL
  __ mulsd(xmm1, xmm2);
  __ addsd(xmm1, ExternalAddress(Q_7), rbx /*rscratch*/);    //0x52c4c8abUL, 0x3faba1baUL
  __ mulsd(xmm1, xmm2);
  __ addsd(xmm1, ExternalAddress(Q_5), rbx /*rscratch*/);    //0x11092746UL, 0x3fc11111UL
  __ mulsd(xmm1, xmm2);
  __ addsd(xmm1, ExternalAddress(Q_3), rbx /*rscratch*/);    //0x55555612UL, 0x3fd55555UL
  __ mulsd(xmm1, xmm3);
  __ addsd(xmm0, xmm1);
  __ jmp(B1_4);

  __ bind(L_2TAG_PACKET_3_0_1);
  __ movq(xmm3, ExternalAddress(TWO_POW_55), rbx /*rscratch*/);    //0x00000000UL, 0x43600000UL
  __ mulsd(xmm3, xmm0);
  __ addsd(xmm0, xmm3);
  __ mulsd(xmm0, ExternalAddress(TWO_POW_M55), rbx /*rscratch*/);    //0x00000000UL, 0x3c800000UL
  __ jmp(B1_4);

  __ bind(L_2TAG_PACKET_2_0_1);
  __ movdqu(xmm1, xmm0);
  __ mulsd(xmm1, xmm1);
  __ jmp(B1_4);

  __ bind(L_2TAG_PACKET_1_0_1);
  __ pextrw(rax, xmm0, 3);
  __ andl(rax, 32752);
  __ cmpl(rax, 32752);
  __ jcc(Assembler::equal, L_2TAG_PACKET_4_0_1);
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
  __ cmpl(rcx, 0);
  __ jcc(Assembler::less, L_2TAG_PACKET_5_0_1);
  __ negl(rcx);
  __ addl(rcx, 29);
  __ shll(r9);
  __ movl(rdi, r9);
  __ andl(r9, 1073741823);
  __ testl(r9, 536870912);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_6_0_1);
  __ shrl(r9);
  __ movl(rbx, 0);
  __ shlq(r9, 32);
  __ orq(r9, r11);

  __ bind(L_2TAG_PACKET_7_0_1);

  __ bind(L_2TAG_PACKET_8_0_1);
  __ cmpq(r9, 0);
  __ jcc(Assembler::equal, L_2TAG_PACKET_9_0_1);

  __ bind(L_2TAG_PACKET_10_0_1);
  __ bsrq(r11, r9);
  __ movl(rcx, 29);
  __ subl(rcx, r11);
  __ jcc(Assembler::lessEqual, L_2TAG_PACKET_11_0_1);
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

  __ bind(L_2TAG_PACKET_12_0_1);
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
  __ movq(xmm2, ExternalAddress(PI_4),     rbx /*rscratch*/);    //0x00000000UL, 0x3fe921fbUL, 0x4611a626UL, 0x3e85110bUL
  __ movq(xmm7, ExternalAddress(PI_4 + 8), rbx /*rscratch*/);    //0x3fe921fbUL, 0x4611a626UL, 0x3e85110bUL
  __ xorpd(xmm5, xmm5);
  __ subl(rdx, 1008);
  __ pinsrw(xmm5, rdx, 3);
  __ mulsd(xmm0, xmm4);
  __ shll(rsi, 16);
  __ sarl(rsi, 31);
  __ mulsd(xmm3, xmm5);
  __ movdqu(xmm1, xmm0);
  __ mulsd(xmm0, xmm2);
  __ shrl(rdi, 30);
  __ addsd(xmm1, xmm3);
  __ mulsd(xmm3, xmm2);
  __ addl(rdi, rsi);
  __ xorl(rdi, rsi);
  __ mulsd(xmm7, xmm1);
  __ movl(rax, rdi);
  __ addsd(xmm7, xmm3);
  __ movdqu(xmm2, xmm0);
  __ addsd(xmm0, xmm7);
  __ subsd(xmm2, xmm0);
  __ addsd(xmm7, xmm2);
  __ movdqu(xmm1, ExternalAddress(PI32INV), rbx /*rscratch*/);    //0x6dc9c883UL, 0x3fe45f30UL, 0x6dc9c883UL, 0x40245f30UL
  if (VM_Version::supports_sse3()) {
    __ movddup(xmm0, xmm0);
  } else {
    __ movlhps(xmm0, xmm0);
  }
  __ movdqu(xmm4, ExternalAddress(sign_mask), rbx /*rscratch*/);    //0x00000000UL, 0x80000000UL, 0x00000000UL, 0x80000000UL
  __ andpd(xmm4, xmm0);
  __ mulpd(xmm1, xmm0);
  if (VM_Version::supports_sse3()) {
    __ movddup(xmm7, xmm7);
  }
  else {
    __ movlhps(xmm7, xmm7);
  }
  __ movdqu(xmm5, ExternalAddress(ONEHALF), rbx /*rscratch*/);    //0x00000000UL, 0x3fe00000UL, 0x00000000UL, 0x3fe00000UL
  __ movdqu(xmm6, ExternalAddress(MUL16), rbx /*rscratch*/);    //0x00000000UL, 0x40300000UL, 0x00000000UL, 0x3ff00000UL
  __ por(xmm5, xmm4);
  __ addpd(xmm1, xmm5);
  __ movdqu(xmm5, xmm1);
  __ unpckhpd(xmm5, xmm5);
  __ cvttsd2sil(rdx, xmm5);
  __ cvttpd2dq(xmm1, xmm1);
  __ cvtdq2pd(xmm1, xmm1);
  __ mulpd(xmm1, xmm6);
  __ movdqu(xmm3, ExternalAddress(P_1), rbx /*rscratch*/);    //0x54444000UL, 0x3fb921fbUL, 0x54440000UL, 0x3fb921fbUL
  __ movq(xmm5, ExternalAddress(QQ_2), rbx /*rscratch*/);    //0x676733afUL, 0x3d32e7b9UL
  __ shll(rax, 4);
  __ addl(rdx, 469248);
  __ movdqu(xmm4, ExternalAddress(P_2), rbx /*rscratch*/);    //0x67674000UL, 0xbd32e7b9UL, 0x4c4c0000UL, 0x3d468c23UL
  __ mulpd(xmm3, xmm1);
  __ addl(rdx, rax);
  __ andl(rdx, 31);
  __ mulsd(xmm5, xmm1);
  __ movl(rcx, rdx);
  __ mulpd(xmm4, xmm1);
  __ shll(rcx, 1);
  __ subpd(xmm0, xmm3);
  __ mulpd(xmm1, ExternalAddress(P_3), rbx /*rscratch*/);    //0x3707344aUL, 0x3aa8a2e0UL, 0x03707345UL, 0x3ae98a2eUL
  __ addl(rdx, rcx);
  __ shll(rcx, 2);
  __ addl(rdx, rcx);
  __ addsd(xmm5, xmm0);
  __ movdqu(xmm2, xmm0);
  __ subpd(xmm0, xmm4);
  __ movq(xmm6, ExternalAddress(ONE), rbx /*rscratch*/);    //0x00000000UL, 0x3ff00000UL
  __ shll(rdx, 4);
  __ lea(rax, ExternalAddress(Ctable));
  __ andpd(xmm5, ExternalAddress(MASK_35), rbx /*rscratch*/);    //0xfffc0000UL, 0xffffffffUL, 0x00000000UL, 0x00000000UL
  __ movdqu(xmm3, xmm0);
  __ addq(rax, rdx);
  __ subpd(xmm2, xmm0);
  __ unpckhpd(xmm0, xmm0);
  __ divsd(xmm6, xmm5);
  __ subpd(xmm2, xmm4);
  __ subsd(xmm3, xmm5);
  __ subpd(xmm2, xmm1);
  __ movdqu(xmm1, Address(rax, 48));
  __ addpd(xmm2, xmm7);
  __ movdqu(xmm7, Address(rax, 16));
  __ mulpd(xmm7, xmm0);
  __ movdqu(xmm4, Address(rax, 96));
  __ mulpd(xmm1, xmm0);
  __ mulpd(xmm4, xmm0);
  __ addsd(xmm2, xmm3);
  __ movdqu(xmm3, xmm0);
  __ mulpd(xmm0, xmm0);
  __ addpd(xmm7, Address(rax, 0));
  __ addpd(xmm1, Address(rax, 32));
  __ mulpd(xmm1, xmm0);
  __ addpd(xmm4, Address(rax, 80));
  __ addpd(xmm7, xmm1);
  __ movdqu(xmm1, Address(rax, 112));
  __ mulpd(xmm1, xmm0);
  __ mulpd(xmm0, xmm0);
  __ addpd(xmm4, xmm1);
  __ movdqu(xmm1, Address(rax, 64));
  __ mulpd(xmm1, xmm0);
  __ addpd(xmm7, xmm1);
  __ movdqu(xmm1, xmm3);
  __ mulpd(xmm3, xmm0);
  __ mulsd(xmm0, xmm0);
  __ mulpd(xmm1, Address(rax, 144));
  __ mulpd(xmm4, xmm3);
  __ movdqu(xmm3, xmm1);
  __ addpd(xmm7, xmm4);
  __ movdqu(xmm4, xmm1);
  __ mulsd(xmm0, xmm7);
  __ unpckhpd(xmm7, xmm7);
  __ addsd(xmm0, xmm7);
  __ unpckhpd(xmm1, xmm1);
  __ addsd(xmm3, xmm1);
  __ subsd(xmm4, xmm3);
  __ addsd(xmm1, xmm4);
  __ movdqu(xmm4, xmm2);
  __ movq(xmm7, Address(rax, 144));
  __ unpckhpd(xmm2, xmm2);
  __ addsd(xmm7, Address(rax, 152));
  __ mulsd(xmm7, xmm2);
  __ addsd(xmm7, Address(rax, 136));
  __ addsd(xmm7, xmm1);
  __ addsd(xmm0, xmm7);
  __ movq(xmm7, ExternalAddress(ONE), rbx /*rscratch*/);    //0x00000000UL, 0x3ff00000UL
  __ mulsd(xmm4, xmm6);
  __ movq(xmm2, Address(rax, 168));
  __ andpd(xmm2, xmm6);
  __ mulsd(xmm5, xmm2);
  __ mulsd(xmm6, Address(rax, 160));
  __ subsd(xmm7, xmm5);
  __ subsd(xmm2, Address(rax, 128));
  __ subsd(xmm7, xmm4);
  __ mulsd(xmm7, xmm6);
  __ movdqu(xmm4, xmm3);
  __ subsd(xmm3, xmm2);
  __ addsd(xmm2, xmm3);
  __ subsd(xmm4, xmm2);
  __ addsd(xmm0, xmm4);
  __ subsd(xmm0, xmm7);
  __ addsd(xmm0, xmm3);
  __ jmp(B1_4);

  __ bind(L_2TAG_PACKET_9_0_1);
  __ addl(rdx, 64);
  __ movq(r9, r10);
  __ movq(r10, r8);
  __ movl(r8, 0);
  __ cmpq(r9, 0);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_10_0_1);
  __ addl(rdx, 64);
  __ movq(r9, r10);
  __ movq(r10, r8);
  __ cmpq(r9, 0);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_10_0_1);
  __ jmp(L_2TAG_PACKET_12_0_1);

  __ bind(L_2TAG_PACKET_11_0_1);
  __ jcc(Assembler::equal, L_2TAG_PACKET_12_0_1);
  __ negl(rcx);
  __ shrq(r10);
  __ movq(rax, r9);
  __ shrq(r9);
  __ subl(rdx, rcx);
  __ negl(rcx);
  __ addl(rcx, 64);
  __ shlq(rax);
  __ orq(r10, rax);
  __ jmp(L_2TAG_PACKET_12_0_1);

  __ bind(L_2TAG_PACKET_5_0_1);
  __ notl(rcx);
  __ shlq(r9, 32);
  __ orq(r9, r11);
  __ shlq(r9);
  __ movq(rdi, r9);
  __ testl(r9, INT_MIN);
  __ jcc(Assembler::notEqual, L_2TAG_PACKET_13_0_1);
  __ shrl(r9);
  __ movl(rbx, 0);
  __ shrq(rdi, 2);
  __ jmp(L_2TAG_PACKET_8_0_1);

  __ bind(L_2TAG_PACKET_6_0_1);
  __ shrl(r9);
  __ movl(rbx, 1073741824);
  __ shrl(rbx);
  __ shlq(r9, 32);
  __ orq(r9, r11);
  __ shlq(rbx, 32);
  __ addl(rdi, 1073741824);
  __ movl(rcx, 0);
  __ movl(r11, 0);
  __ subq(rcx, r8);
  __ sbbq(r11, r10);
  __ sbbq(rbx, r9);
  __ movq(r8, rcx);
  __ movq(r10, r11);
  __ movq(r9, rbx);
  __ movl(rbx, 32768);
  __ jmp(L_2TAG_PACKET_7_0_1);

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
  __ shrq(rdi, 2);
  __ addl(rdi, 1073741824);
  __ jmp(L_2TAG_PACKET_8_0_1);

  __ bind(L_2TAG_PACKET_4_0_1);
  __ movq(xmm0, Address(rsp, 8));
  __ mulsd(xmm0, ExternalAddress(NEG_ZERO), rbx /*rscratch*/);    //0x00000000UL, 0x80000000UL
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
