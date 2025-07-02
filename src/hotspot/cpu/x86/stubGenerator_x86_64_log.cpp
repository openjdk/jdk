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

ATTRIBUTE_ALIGNED(16) static const juint _L_tbl[] =
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

ATTRIBUTE_ALIGNED(16) static const juint _log2[] =
{
    0xfefa3800UL, 0x3fa62e42UL, 0x93c76730UL, 0x3ceef357UL
};

ATTRIBUTE_ALIGNED(16) static const juint _coeff[] =
{
    0x92492492UL, 0x3fc24924UL, 0x00000000UL, 0xbfd00000UL, 0x3d6fb175UL,
    0xbfc5555eUL, 0x55555555UL, 0x3fd55555UL, 0x9999999aUL, 0x3fc99999UL,
    0x00000000UL, 0xbfe00000UL
};

#define __ _masm->

address StubGenerator::generate_libmLog() {
  StubId stub_id = StubId::stubgen_dlog_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();

  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label L_2TAG_PACKET_4_0_2, L_2TAG_PACKET_5_0_2, L_2TAG_PACKET_6_0_2, L_2TAG_PACKET_7_0_2;
  Label L_2TAG_PACKET_8_0_2;
  Label B1_3, B1_5;

  address L_tbl = (address)_L_tbl;
  address log2  = (address)_log2;
  address coeff = (address)_coeff;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  __ subq(rsp, 24);
  __ movsd(Address(rsp, 0), xmm0);
  __ mov64(rax, 0x3ff0000000000000);
  __ movdq(xmm2, rax);
  __ mov64(rdx, 0x77f0000000000000);
  __ movdq(xmm3, rdx);
  __ movl(rcx, 32768);
  __ movdl(xmm4, rcx);
  __ mov64(r11, 0xffffe00000000000);
  __ movdq(xmm5, r11);
  __ movdqu(xmm1, xmm0);
  __ pextrw(rax, xmm0, 3);
  __ por(xmm0, xmm2);
  __ movl(rcx, 16352);
  __ psrlq(xmm0, 27);
  __ lea(r8, ExternalAddress(L_tbl));
  __ psrld(xmm0, 2);
  __ rcpps(xmm0, xmm0);
  __ psllq(xmm1, 12);
  __ pshufd(xmm6, xmm5, 228);
  __ psrlq(xmm1, 12);
  __ subl(rax, 16);
  __ cmpl(rax, 32736);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_0_0_2);

  __ bind(L_2TAG_PACKET_1_0_2);
  __ paddd(xmm0, xmm4);
  __ por(xmm1, xmm3);
  __ movdl(rdx, xmm0);
  __ psllq(xmm0, 29);
  __ pand(xmm5, xmm1);
  __ pand(xmm0, xmm6);
  __ subsd(xmm1, xmm5);
  __ mulpd(xmm5, xmm0);
  __ andl(rax, 32752);
  __ subl(rax, rcx);
  __ cvtsi2sdl(xmm7, rax);
  __ mulsd(xmm1, xmm0);
  __ movq(xmm6, ExternalAddress(log2), r11 /*rscratch*/);       // 0xfefa3800UL, 0x3fa62e42UL
  __ movdqu(xmm3, ExternalAddress(coeff), r11 /*rscratch*/);    // 0x92492492UL, 0x3fc24924UL, 0x00000000UL, 0xbfd00000UL
  __ subsd(xmm5, xmm2);
  __ andl(rdx, 16711680);
  __ shrl(rdx, 12);
  __ movdqu(xmm0, Address(r8, rdx));
  __ movdqu(xmm4, ExternalAddress(coeff + 16), r11 /*rscratch*/); // 0x3d6fb175UL, 0xbfc5555eUL, 0x55555555UL, 0x3fd55555UL
  __ addsd(xmm1, xmm5);
  __ movdqu(xmm2, ExternalAddress(coeff + 32), r11 /*rscratch*/); // 0x9999999aUL, 0x3fc99999UL, 0x00000000UL, 0xbfe00000UL
  __ mulsd(xmm6, xmm7);
  if (VM_Version::supports_sse3()) {
    __ movddup(xmm5, xmm1);
  }
  else {
    __ movdqu(xmm5, xmm1);
    __ movlhps(xmm5, xmm5);
  }
  __ mulsd(xmm7, ExternalAddress(log2 + 8), r11 /*rscratch*/);    // 0x93c76730UL, 0x3ceef357UL
  __ mulsd(xmm3, xmm1);
  __ addsd(xmm0, xmm6);
  __ mulpd(xmm4, xmm5);
  __ mulpd(xmm5, xmm5);
  if (VM_Version::supports_sse3()) {
    __ movddup(xmm6, xmm0);
  }
  else {
    __ movdqu(xmm6, xmm0);
    __ movlhps(xmm6, xmm6);
  }
  __ addsd(xmm0, xmm1);
  __ addpd(xmm4, xmm2);
  __ mulpd(xmm3, xmm5);
  __ subsd(xmm6, xmm0);
  __ mulsd(xmm4, xmm1);
  __ pshufd(xmm2, xmm0, 238);
  __ addsd(xmm1, xmm6);
  __ mulsd(xmm5, xmm5);
  __ addsd(xmm7, xmm2);
  __ addpd(xmm4, xmm3);
  __ addsd(xmm1, xmm7);
  __ mulpd(xmm4, xmm5);
  __ addsd(xmm1, xmm4);
  __ pshufd(xmm5, xmm4, 238);
  __ addsd(xmm1, xmm5);
  __ addsd(xmm0, xmm1);
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_0_0_2);
  __ movq(xmm0, Address(rsp, 0));
  __ movq(xmm1, Address(rsp, 0));
  __ addl(rax, 16);
  __ cmpl(rax, 32768);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_2_0_2);
  __ cmpl(rax, 16);
  __ jcc(Assembler::below, L_2TAG_PACKET_3_0_2);

  __ bind(L_2TAG_PACKET_4_0_2);
  __ addsd(xmm0, xmm0);
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_5_0_2);
  __ jcc(Assembler::above, L_2TAG_PACKET_4_0_2);
  __ cmpl(rdx, 0);
  __ jcc(Assembler::above, L_2TAG_PACKET_4_0_2);
  __ jmp(L_2TAG_PACKET_6_0_2);

  __ bind(L_2TAG_PACKET_3_0_2);
  __ xorpd(xmm1, xmm1);
  __ addsd(xmm1, xmm0);
  __ movdl(rdx, xmm1);
  __ psrlq(xmm1, 32);
  __ movdl(rcx, xmm1);
  __ orl(rdx, rcx);
  __ cmpl(rdx, 0);
  __ jcc(Assembler::equal, L_2TAG_PACKET_7_0_2);
  __ xorpd(xmm1, xmm1);
  __ movl(rax, 18416);
  __ pinsrw(xmm1, rax, 3);
  __ mulsd(xmm0, xmm1);
  __ movdqu(xmm1, xmm0);
  __ pextrw(rax, xmm0, 3);
  __ por(xmm0, xmm2);
  __ psrlq(xmm0, 27);
  __ movl(rcx, 18416);
  __ psrld(xmm0, 2);
  __ rcpps(xmm0, xmm0);
  __ psllq(xmm1, 12);
  __ pshufd(xmm6, xmm5, 228);
  __ psrlq(xmm1, 12);
  __ jmp(L_2TAG_PACKET_1_0_2);

  __ bind(L_2TAG_PACKET_2_0_2);
  __ movdl(rdx, xmm1);
  __ psrlq(xmm1, 32);
  __ movdl(rcx, xmm1);
  __ addl(rcx, rcx);
  __ cmpl(rcx, -2097152);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_5_0_2);
  __ orl(rdx, rcx);
  __ cmpl(rdx, 0);
  __ jcc(Assembler::equal, L_2TAG_PACKET_7_0_2);

  __ bind(L_2TAG_PACKET_6_0_2);
  __ xorpd(xmm1, xmm1);
  __ xorpd(xmm0, xmm0);
  __ movl(rax, 32752);
  __ pinsrw(xmm1, rax, 3);
  __ mulsd(xmm0, xmm1);
  __ movl(Address(rsp, 16), 3);
  __ jmp(L_2TAG_PACKET_8_0_2);
  __ bind(L_2TAG_PACKET_7_0_2);
  __ xorpd(xmm1, xmm1);
  __ xorpd(xmm0, xmm0);
  __ movl(rax, 49136);
  __ pinsrw(xmm0, rax, 3);
  __ divsd(xmm0, xmm1);
  __ movl(Address(rsp, 16), 2);

  __ bind(L_2TAG_PACKET_8_0_2);
  __ movq(Address(rsp, 8), xmm0);

  __ bind(B1_3);
  __ movq(xmm0, Address(rsp, 8));

  __ bind(B1_5);
  __ addq(rsp, 24);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

/******************************************************************************/
//                     ALGORITHM DESCRIPTION - LOG10()
//                     ---------------------
//
//    Let x=2^k * mx, mx in [1,2)
//
//    Get B~1/mx based on the output of rcpss instruction (B0)
//    B = int((B0*LH*2^7+0.5))/2^7
//    LH is a short approximation for log10(e)
//
//    Reduced argument: r=B*mx-LH (computed accurately in high and low parts)
//
//    Result:  k*log10(2) - log(B) + p(r)
//             p(r) is a degree 7 polynomial
//             -log(B) read from data table (high, low parts)
//             Result is formed from high and low parts
//
// Special cases:
//  log10(0) = -INF with divide-by-zero exception raised
//  log10(1) = +0
//  log10(x) = NaN with invalid exception raised if x < -0, including -INF
//  log10(+INF) = +INF
//
/******************************************************************************/

ATTRIBUTE_ALIGNED(16) static const juint _HIGHSIGMASK_log10[] = {
    0xf8000000UL, 0xffffffffUL, 0x00000000UL, 0xffffe000UL
};

ATTRIBUTE_ALIGNED(16) static const juint _LOG10_E[] = {
    0x00000000UL, 0x3fdbc000UL, 0xbf2e4108UL, 0x3f5a7a6cUL
};

ATTRIBUTE_ALIGNED(16) static const juint _L_tbl_log10[] = {
    0x509f7800UL, 0x3fd34413UL, 0x1f12b358UL, 0x3d1fef31UL, 0x80333400UL,
    0x3fd32418UL, 0xc671d9d0UL, 0xbcf542bfUL, 0x51195000UL, 0x3fd30442UL,
    0x78a4b0c3UL, 0x3d18216aUL, 0x6fc79400UL, 0x3fd2e490UL, 0x80fa389dUL,
    0xbc902869UL, 0x89d04000UL, 0x3fd2c502UL, 0x75c2f564UL, 0x3d040754UL,
    0x4ddd1c00UL, 0x3fd2a598UL, 0xd219b2c3UL, 0xbcfa1d84UL, 0x6baa7c00UL,
    0x3fd28651UL, 0xfd9abec1UL, 0x3d1be6d3UL, 0x94028800UL, 0x3fd2672dUL,
    0xe289a455UL, 0xbd1ede5eUL, 0x78b86400UL, 0x3fd2482cUL, 0x6734d179UL,
    0x3d1fe79bUL, 0xcca3c800UL, 0x3fd2294dUL, 0x981a40b8UL, 0xbced34eaUL,
    0x439c5000UL, 0x3fd20a91UL, 0xcc392737UL, 0xbd1a9cc3UL, 0x92752c00UL,
    0x3fd1ebf6UL, 0x03c9afe7UL, 0x3d1e98f8UL, 0x6ef8dc00UL, 0x3fd1cd7dUL,
    0x71dae7f4UL, 0x3d08a86cUL, 0x8fe4dc00UL, 0x3fd1af25UL, 0xee9185a1UL,
    0xbcff3412UL, 0xace59400UL, 0x3fd190eeUL, 0xc2cab353UL, 0x3cf17ed9UL,
    0x7e925000UL, 0x3fd172d8UL, 0x6952c1b2UL, 0x3cf1521cUL, 0xbe694400UL,
    0x3fd154e2UL, 0xcacb79caUL, 0xbd0bdc78UL, 0x26cbac00UL, 0x3fd1370dUL,
    0xf71f4de1UL, 0xbd01f8beUL, 0x72fa0800UL, 0x3fd11957UL, 0x55bf910bUL,
    0x3c946e2bUL, 0x5f106000UL, 0x3fd0fbc1UL, 0x39e639c1UL, 0x3d14a84bUL,
    0xa802a800UL, 0x3fd0de4aUL, 0xd3f31d5dUL, 0xbd178385UL, 0x0b992000UL,
    0x3fd0c0f3UL, 0x3843106fUL, 0xbd1f602fUL, 0x486ce800UL, 0x3fd0a3baUL,
    0x8819497cUL, 0x3cef987aUL, 0x1de49400UL, 0x3fd086a0UL, 0x1caa0467UL,
    0x3d0faec7UL, 0x4c30cc00UL, 0x3fd069a4UL, 0xa4424372UL, 0xbd1618fcUL,
    0x94490000UL, 0x3fd04cc6UL, 0x946517d2UL, 0xbd18384bUL, 0xb7e84000UL,
    0x3fd03006UL, 0xe0109c37UL, 0xbd19a6acUL, 0x798a0c00UL, 0x3fd01364UL,
    0x5121e864UL, 0xbd164cf7UL, 0x38ce8000UL, 0x3fcfedbfUL, 0x46214d1aUL,
    0xbcbbc402UL, 0xc8e62000UL, 0x3fcfb4efUL, 0xdab93203UL, 0x3d1e0176UL,
    0x2cb02800UL, 0x3fcf7c5aUL, 0x2a2ea8e4UL, 0xbcfec86aUL, 0xeeeaa000UL,
    0x3fcf43fdUL, 0xc18e49a4UL, 0x3cf110a8UL, 0x9bb6e800UL, 0x3fcf0bdaUL,
    0x923cc9c0UL, 0xbd15ce99UL, 0xc093f000UL, 0x3fced3efUL, 0x4d4b51e9UL,
    0x3d1a04c7UL, 0xec58f800UL, 0x3fce9c3cUL, 0x163cad59UL, 0x3cac8260UL,
    0x9a907000UL, 0x3fce2d7dUL, 0x3fa93646UL, 0x3ce4a1c0UL, 0x37311000UL,
    0x3fcdbf99UL, 0x32abd1fdUL, 0x3d07ea9dUL, 0x6744b800UL, 0x3fcd528cUL,
    0x4dcbdfd4UL, 0xbd1b08e2UL, 0xe36de800UL, 0x3fcce653UL, 0x0b7b7f7fUL,
    0xbd1b8f03UL, 0x77506800UL, 0x3fcc7aecUL, 0xa821c9fbUL, 0x3d13c163UL,
    0x00ff8800UL, 0x3fcc1053UL, 0x536bca76UL, 0xbd074ee5UL, 0x70719800UL,
    0x3fcba684UL, 0xd7da9b6bUL, 0xbd1fbf16UL, 0xc6f8d800UL, 0x3fcb3d7dUL,
    0xe2220bb3UL, 0x3d1a295dUL, 0x16c15800UL, 0x3fcad53cUL, 0xe724911eUL,
    0xbcf55822UL, 0x82533800UL, 0x3fca6dbcUL, 0x6d982371UL, 0x3cac567cUL,
    0x3c19e800UL, 0x3fca06fcUL, 0x84d17d80UL, 0x3d1da204UL, 0x85ef8000UL,
    0x3fc9a0f8UL, 0x54466a6aUL, 0xbd002204UL, 0xb0ac2000UL, 0x3fc93baeUL,
    0xd601fd65UL, 0x3d18840cUL, 0x1bb9b000UL, 0x3fc8d71cUL, 0x7bf58766UL,
    0xbd14f897UL, 0x34aae800UL, 0x3fc8733eUL, 0x3af6ac24UL, 0xbd0f5c45UL,
    0x76d68000UL, 0x3fc81012UL, 0x4303e1a1UL, 0xbd1f9a80UL, 0x6af57800UL,
    0x3fc7ad96UL, 0x43fbcb46UL, 0x3cf4c33eUL, 0xa6c51000UL, 0x3fc74bc7UL,
    0x70f0eac5UL, 0xbd192e3bUL, 0xccab9800UL, 0x3fc6eaa3UL, 0xc0093dfeUL,
    0xbd0faf15UL, 0x8b60b800UL, 0x3fc68a28UL, 0xde78d5fdUL, 0xbc9ea4eeUL,
    0x9d987000UL, 0x3fc62a53UL, 0x962bea6eUL, 0xbd194084UL, 0xc9b0e800UL,
    0x3fc5cb22UL, 0x888dd999UL, 0x3d1fe201UL, 0xe1634800UL, 0x3fc56c93UL,
    0x16ada7adUL, 0x3d1b1188UL, 0xc176c000UL, 0x3fc50ea4UL, 0x4159b5b5UL,
    0xbcf09c08UL, 0x51766000UL, 0x3fc4b153UL, 0x84393d23UL, 0xbcf6a89cUL,
    0x83695000UL, 0x3fc4549dUL, 0x9f0b8bbbUL, 0x3d1c4b8cUL, 0x538d5800UL,
    0x3fc3f881UL, 0xf49df747UL, 0x3cf89b99UL, 0xc8138000UL, 0x3fc39cfcUL,
    0xd503b834UL, 0xbd13b99fUL, 0xf0df0800UL, 0x3fc3420dUL, 0xf011b386UL,
    0xbd05d8beUL, 0xe7466800UL, 0x3fc2e7b2UL, 0xf39c7bc2UL, 0xbd1bb94eUL,
    0xcdd62800UL, 0x3fc28de9UL, 0x05e6d69bUL, 0xbd10ed05UL, 0xd015d800UL,
    0x3fc234b0UL, 0xe29b6c9dUL, 0xbd1ff967UL, 0x224ea800UL, 0x3fc1dc06UL,
    0x727711fcUL, 0xbcffb30dUL, 0x01540000UL, 0x3fc183e8UL, 0x39786c5aUL,
    0x3cc23f57UL, 0xb24d9800UL, 0x3fc12c54UL, 0xc905a342UL, 0x3d003a1dUL,
    0x82835800UL, 0x3fc0d54aUL, 0x9b9920c0UL, 0x3d03b25aUL, 0xc72ac000UL,
    0x3fc07ec7UL, 0x46f26a24UL, 0x3cf0fa41UL, 0xdd35d800UL, 0x3fc028caUL,
    0x41d9d6dcUL, 0x3d034a65UL, 0x52474000UL, 0x3fbfa6a4UL, 0x44f66449UL,
    0x3d19cad3UL, 0x2da3d000UL, 0x3fbefcb8UL, 0x67832999UL, 0x3d18400fUL,
    0x32a10000UL, 0x3fbe53ceUL, 0x9c0e3b1aUL, 0xbcff62fdUL, 0x556b7000UL,
    0x3fbdabe3UL, 0x02976913UL, 0xbcf8243bUL, 0x97e88000UL, 0x3fbd04f4UL,
    0xec793797UL, 0x3d1c0578UL, 0x09647000UL, 0x3fbc5effUL, 0x05fc0565UL,
    0xbd1d799eUL, 0xc6426000UL, 0x3fbbb9ffUL, 0x4625f5edUL, 0x3d1f5723UL,
    0xf7afd000UL, 0x3fbb15f3UL, 0xdd5aae61UL, 0xbd1a7e1eUL, 0xd358b000UL,
    0x3fba72d8UL, 0x3314e4d3UL, 0x3d17bc91UL, 0x9b1f5000UL, 0x3fb9d0abUL,
    0x9a4d514bUL, 0x3cf18c9bUL, 0x9cd4e000UL, 0x3fb92f69UL, 0x7e4496abUL,
    0x3cf1f96dUL, 0x31f4f000UL, 0x3fb88f10UL, 0xf56479e7UL, 0x3d165818UL,
    0xbf628000UL, 0x3fb7ef9cUL, 0x26bf486dUL, 0xbd1113a6UL, 0xb526b000UL,
    0x3fb7510cUL, 0x1a1c3384UL, 0x3ca9898dUL, 0x8e31e000UL, 0x3fb6b35dUL,
    0xb3875361UL, 0xbd0661acUL, 0xd01de000UL, 0x3fb6168cUL, 0x2a7cacfaUL,
    0xbd1bdf10UL, 0x0af23000UL, 0x3fb57a98UL, 0xff868816UL, 0x3cf046d0UL,
    0xd8ea0000UL, 0x3fb4df7cUL, 0x1515fbe7UL, 0xbd1fd529UL, 0xde3b2000UL,
    0x3fb44538UL, 0x6e59a132UL, 0x3d1faeeeUL, 0xc8df9000UL, 0x3fb3abc9UL,
    0xf1322361UL, 0xbd198807UL, 0x505f1000UL, 0x3fb3132dUL, 0x0888e6abUL,
    0x3d1e5380UL, 0x359bd000UL, 0x3fb27b61UL, 0xdfbcbb22UL, 0xbcfe2724UL,
    0x429ee000UL, 0x3fb1e463UL, 0x6eb4c58cUL, 0xbcfe4dd6UL, 0x4a673000UL,
    0x3fb14e31UL, 0x4ce1ac9bUL, 0x3d1ba691UL, 0x28b96000UL, 0x3fb0b8c9UL,
    0x8c7813b8UL, 0xbd0b3872UL, 0xc1f08000UL, 0x3fb02428UL, 0xc2bc8c2cUL,
    0x3cb5ea6bUL, 0x05a1a000UL, 0x3faf209cUL, 0x72e8f18eUL, 0xbce8df84UL,
    0xc0b5e000UL, 0x3fadfa6dUL, 0x9fdef436UL, 0x3d087364UL, 0xaf416000UL,
    0x3facd5c2UL, 0x1068c3a9UL, 0x3d0827e7UL, 0xdb356000UL, 0x3fabb296UL,
    0x120a34d3UL, 0x3d101a9fUL, 0x5dfea000UL, 0x3faa90e6UL, 0xdaded264UL,
    0xbd14c392UL, 0x6034c000UL, 0x3fa970adUL, 0x1c9d06a9UL, 0xbd1b705eUL,
    0x194c6000UL, 0x3fa851e8UL, 0x83996ad9UL, 0xbd0117bcUL, 0xcf4ac000UL,
    0x3fa73492UL, 0xb1a94a62UL, 0xbca5ea42UL, 0xd67b4000UL, 0x3fa618a9UL,
    0x75aed8caUL, 0xbd07119bUL, 0x9126c000UL, 0x3fa4fe29UL, 0x5291d533UL,
    0x3d12658fUL, 0x6f4d4000UL, 0x3fa3e50eUL, 0xcd2c5cd9UL, 0x3d1d5c70UL,
    0xee608000UL, 0x3fa2cd54UL, 0xd1008489UL, 0x3d1a4802UL, 0x9900e000UL,
    0x3fa1b6f9UL, 0x54fb5598UL, 0xbd16593fUL, 0x06bb6000UL, 0x3fa0a1f9UL,
    0x64ef57b4UL, 0xbd17636bUL, 0xb7940000UL, 0x3f9f1c9fUL, 0xee6a4737UL,
    0x3cb5d479UL, 0x91aa0000UL, 0x3f9cf7f5UL, 0x3a16373cUL, 0x3d087114UL,
    0x156b8000UL, 0x3f9ad5edUL, 0x836c554aUL, 0x3c6900b0UL, 0xd4764000UL,
    0x3f98b67fUL, 0xed12f17bUL, 0xbcffc974UL, 0x77dec000UL, 0x3f9699a7UL,
    0x232ce7eaUL, 0x3d1e35bbUL, 0xbfbf4000UL, 0x3f947f5dUL, 0xd84ffa6eUL,
    0x3d0e0a49UL, 0x82c7c000UL, 0x3f92679cUL, 0x8d170e90UL, 0xbd14d9f2UL,
    0xadd20000UL, 0x3f90525dUL, 0x86d9f88eUL, 0x3cdeb986UL, 0x86f10000UL,
    0x3f8c7f36UL, 0xb9e0a517UL, 0x3ce29faaUL, 0xb75c8000UL, 0x3f885e9eUL,
    0x542568cbUL, 0xbd1f7bdbUL, 0x46b30000UL, 0x3f8442e8UL, 0xb954e7d9UL,
    0x3d1e5287UL, 0xb7e60000UL, 0x3f802c07UL, 0x22da0b17UL, 0xbd19fb27UL,
    0x6c8b0000UL, 0x3f7833e3UL, 0x821271efUL, 0xbd190f96UL, 0x29910000UL,
    0x3f701936UL, 0xbc3491a5UL, 0xbd1bcf45UL, 0x354a0000UL, 0x3f600fe3UL,
    0xc0ff520aUL, 0xbd19d71cUL, 0x00000000UL, 0x00000000UL, 0x00000000UL,
    0x00000000UL
};

ATTRIBUTE_ALIGNED(16) static const juint _log2_log10[] =
{
    0x509f7800UL, 0x3f934413UL, 0x1f12b358UL, 0x3cdfef31UL
};

ATTRIBUTE_ALIGNED(16) static const juint _coeff_log10[] =
{
    0xc1a5f12eUL, 0x40358874UL, 0x64d4ef0dUL, 0xc0089309UL, 0x385593b1UL,
    0xc025c917UL, 0xdc963467UL, 0x3ffc6a02UL, 0x7f9d3aa1UL, 0x4016ab9fUL,
    0xdc77b115UL, 0xbff27af2UL
};

address StubGenerator::generate_libmLog10() {
  StubId stub_id = StubId::stubgen_dlog10_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();

  Label L_2TAG_PACKET_0_0_2, L_2TAG_PACKET_1_0_2, L_2TAG_PACKET_2_0_2, L_2TAG_PACKET_3_0_2;
  Label L_2TAG_PACKET_4_0_2, L_2TAG_PACKET_5_0_2, L_2TAG_PACKET_6_0_2, L_2TAG_PACKET_7_0_2;
  Label L_2TAG_PACKET_8_0_2, L_2TAG_PACKET_9_0_2, B1_2, B1_3, B1_5;

  address HIGHSIGMASK = (address)_HIGHSIGMASK_log10;
  address LOG10_E = (address)_LOG10_E;
  address L_tbl = (address)_L_tbl_log10;
  address log2 = (address)_log2_log10;
  address coeff = (address)_coeff_log10;

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  __ subq(rsp, 24);
  __ movsd(Address(rsp, 0), xmm0);

  __ bind(B1_2);
  __ xorpd(xmm2, xmm2);
  __ movl(rax, 16368);
  __ pinsrw(xmm2, rax, 3);
  __ movl(rcx, 1054736384);
  __ movdl(xmm7, rcx);
  __ xorpd(xmm3, xmm3);
  __ movl(rdx, 30704);
  __ pinsrw(xmm3, rdx, 3);
  __ movdqu(xmm1, xmm0);
  __ movl(rdx, 32768);
  __ movdl(xmm4, rdx);
  __ movdqu(xmm5, ExternalAddress(HIGHSIGMASK), r8 /*rscratch*/);    //0xf8000000UL, 0xffffffffUL, 0x00000000UL, 0xffffe000UL
  __ pextrw(rax, xmm0, 3);
  __ por(xmm0, xmm2);
  __ movl(rcx, 16352);
  __ psrlq(xmm0, 27);
  __ movdqu(xmm2, ExternalAddress(LOG10_E), r8 /*rscratch*/);    //0x00000000UL, 0x3fdbc000UL, 0xbf2e4108UL, 0x3f5a7a6cUL
  __ psrld(xmm0, 2);
  __ rcpps(xmm0, xmm0);
  __ psllq(xmm1, 12);
  __ pshufd(xmm6, xmm5, 78);
  __ psrlq(xmm1, 12);
  __ subl(rax, 16);
  __ cmpl(rax, 32736);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_0_0_2);

  __ bind(L_2TAG_PACKET_1_0_2);
  __ mulss(xmm0, xmm7);
  __ por(xmm1, xmm3);
  __ lea(r11, ExternalAddress(L_tbl));
  __ andpd(xmm5, xmm1);
  __ paddd(xmm0, xmm4);
  __ subsd(xmm1, xmm5);
  __ movdl(rdx, xmm0);
  __ psllq(xmm0, 29);
  __ andpd(xmm0, xmm6);
  __ andl(rax, 32752);
  __ subl(rax, rcx);
  __ cvtsi2sdl(xmm7, rax);
  __ mulpd(xmm5, xmm0);
  __ mulsd(xmm1, xmm0);
  __ movq(xmm6, ExternalAddress(log2), r8 /*rscratch*/);    //0x509f7800UL, 0x3f934413UL, 0x1f12b358UL, 0x3cdfef31UL
  __ movdqu(xmm3, ExternalAddress(coeff), r8 /*rscratch*/);    //0xc1a5f12eUL, 0x40358874UL, 0x64d4ef0dUL, 0xc0089309UL
  __ subsd(xmm5, xmm2);
  __ andl(rdx, 16711680);
  __ shrl(rdx, 12);
  __ movdqu(xmm0, Address(r11, rdx, Address::times_1, -1504));
  __ movdqu(xmm4, ExternalAddress(coeff + 16), r8 /*rscratch*/);    //0x385593b1UL, 0xc025c917UL, 0xdc963467UL, 0x3ffc6a02UL
  __ addsd(xmm1, xmm5);
  __ movdqu(xmm2, ExternalAddress(coeff + 32), r8 /*rscratch*/);    //0x7f9d3aa1UL, 0x4016ab9fUL, 0xdc77b115UL, 0xbff27af2UL
  __ mulsd(xmm6, xmm7);
  __ pshufd(xmm5, xmm1, 68);
  __ mulsd(xmm7, ExternalAddress(log2 + 8), r8 /*rscratch*/);    //0x1f12b358UL, 0x3cdfef31UL
  __ mulsd(xmm3, xmm1);
  __ addsd(xmm0, xmm6);
  __ mulpd(xmm4, xmm5);
  __ movq(xmm6, ExternalAddress(LOG10_E + 8), r8 /*rscratch*/);    //0xbf2e4108UL, 0x3f5a7a6cUL
  __ mulpd(xmm5, xmm5);
  __ addpd(xmm4, xmm2);
  __ mulpd(xmm3, xmm5);
  __ pshufd(xmm2, xmm0, 228);
  __ addsd(xmm0, xmm1);
  __ mulsd(xmm4, xmm1);
  __ subsd(xmm2, xmm0);
  __ mulsd(xmm6, xmm1);
  __ addsd(xmm1, xmm2);
  __ pshufd(xmm2, xmm0, 238);
  __ mulsd(xmm5, xmm5);
  __ addsd(xmm7, xmm2);
  __ addsd(xmm1, xmm6);
  __ addpd(xmm4, xmm3);
  __ addsd(xmm1, xmm7);
  __ mulpd(xmm4, xmm5);
  __ addsd(xmm1, xmm4);
  __ pshufd(xmm5, xmm4, 238);
  __ addsd(xmm1, xmm5);
  __ addsd(xmm0, xmm1);
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_0_0_2);
  __ movq(xmm0, Address(rsp, 0));
  __ movq(xmm1, Address(rsp, 0));
  __ addl(rax, 16);
  __ cmpl(rax, 32768);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_2_0_2);
  __ cmpl(rax, 16);
  __ jcc(Assembler::below, L_2TAG_PACKET_3_0_2);

  __ bind(L_2TAG_PACKET_4_0_2);
  __ addsd(xmm0, xmm0);
  __ jmp(B1_5);

  __ bind(L_2TAG_PACKET_5_0_2);
  __ jcc(Assembler::above, L_2TAG_PACKET_4_0_2);
  __ cmpl(rdx, 0);
  __ jcc(Assembler::above, L_2TAG_PACKET_4_0_2);
  __ jmp(L_2TAG_PACKET_6_0_2);

  __ bind(L_2TAG_PACKET_3_0_2);
  __ xorpd(xmm1, xmm1);
  __ addsd(xmm1, xmm0);
  __ movdl(rdx, xmm1);
  __ psrlq(xmm1, 32);
  __ movdl(rcx, xmm1);
  __ orl(rdx, rcx);
  __ cmpl(rdx, 0);
  __ jcc(Assembler::equal, L_2TAG_PACKET_7_0_2);
  __ xorpd(xmm1, xmm1);
  __ movl(rax, 18416);
  __ pinsrw(xmm1, rax, 3);
  __ mulsd(xmm0, xmm1);
  __ xorpd(xmm2, xmm2);
  __ movl(rax, 16368);
  __ pinsrw(xmm2, rax, 3);
  __ movdqu(xmm1, xmm0);
  __ pextrw(rax, xmm0, 3);
  __ por(xmm0, xmm2);
  __ movl(rcx, 18416);
  __ psrlq(xmm0, 27);
  __ movdqu(xmm2, ExternalAddress(LOG10_E), r8 /*rscratch*/);    //0x00000000UL, 0x3fdbc000UL, 0xbf2e4108UL, 0x3f5a7a6cUL
  __ psrld(xmm0, 2);
  __ rcpps(xmm0, xmm0);
  __ psllq(xmm1, 12);
  __ pshufd(xmm6, xmm5, 78);
  __ psrlq(xmm1, 12);
  __ jmp(L_2TAG_PACKET_1_0_2);

  __ bind(L_2TAG_PACKET_2_0_2);
  __ movdl(rdx, xmm1);
  __ psrlq(xmm1, 32);
  __ movdl(rcx, xmm1);
  __ addl(rcx, rcx);
  __ cmpl(rcx, -2097152);
  __ jcc(Assembler::aboveEqual, L_2TAG_PACKET_5_0_2);
  __ orl(rdx, rcx);
  __ cmpl(rdx, 0);
  __ jcc(Assembler::equal, L_2TAG_PACKET_7_0_2);

  __ bind(L_2TAG_PACKET_6_0_2);
  __ xorpd(xmm1, xmm1);
  __ xorpd(xmm0, xmm0);
  __ movl(rax, 32752);
  __ pinsrw(xmm1, rax, 3);
  __ mulsd(xmm0, xmm1);
  __ movl(Address(rsp, 16), 9);
  __ jmp(L_2TAG_PACKET_8_0_2);

  __ bind(L_2TAG_PACKET_7_0_2);
  __ xorpd(xmm1, xmm1);
  __ xorpd(xmm0, xmm0);
  __ movl(rax, 49136);
  __ pinsrw(xmm0, rax, 3);
  __ divsd(xmm0, xmm1);
  __ movl(Address(rsp, 16), 8);

  __ bind(L_2TAG_PACKET_8_0_2);
  __ movq(Address(rsp, 8), xmm0);

  __ bind(B1_3);
  __ movq(xmm0, Address(rsp, 8));

  __ bind(L_2TAG_PACKET_9_0_2);

  __ bind(B1_5);
  __ addq(rsp, 24);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

#undef __
