/*
* Copyright (c) 2016, 2021, Intel Corporation. All rights reserved.
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

#include "precompiled.hpp"
#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

/******************************************************************************/
//                     ALGORITHM DESCRIPTION - FMOD()
//                     ---------------------
//
// If either value1 or value2 is NaN, the result is NaN.
//
// If neither value1 nor value2 is NaN, the sign of the result equals the sign of the dividend.
//
// If the dividend is an infinity or the divisor is a zero or both, the result is NaN.
//
// If the dividend is finite and the divisor is an infinity, the result equals the dividend.
//
// If the dividend is a zero and the divisor is finite, the result equals the dividend.
//
// In the remaining cases, where neither operand is an infinity, a zero, or NaN, the floating-point
// remainder result from a dividend value1 and a divisor value2 is defined by the mathematical
// relation result = value1 - (value2 * q), where q is an integer that is negative only if
// value1 / value2 is negative, and positive only if value1 / value2 is positive, and whose magnitude
// is as large as possible without exceeding the magnitude of the true mathematical quotient of value1 and value2.
//
/******************************************************************************/

#define __ _masm->

ATTRIBUTE_ALIGNED(32) static const uint64_t CONST_NaN[] = {
    0x7FFFFFFFFFFFFFFFULL, 0x7FFFFFFFFFFFFFFFULL   // NaN vector
};
ATTRIBUTE_ALIGNED(32) static const uint64_t CONST_1p260[] = {
    0x5030000000000000ULL,    // 0x1p+260
};

ATTRIBUTE_ALIGNED(32) static const uint64_t CONST_MAX[] = {
    0x7FEFFFFFFFFFFFFFULL,    // Max
};

ATTRIBUTE_ALIGNED(32) static const uint64_t CONST_INF[] = {
    0x7FF0000000000000ULL,    // Inf
};

ATTRIBUTE_ALIGNED(32) static const uint64_t CONST_e307[] = {
    0x7FE0000000000000ULL
};

address StubGenerator::generate_libmFmod() {
  StubCodeMark mark(this, "StubRoutines", "libmFmod");
  address start = __ pc();
  __ enter(); // required for proper stackwalking of RuntimeStub frame

  if (VM_Version::supports_avx512vlbwdq()) {     // AVX512 version

  Label L_12ca, L_1280, L_115a, L_1271, L_1268, L_1227, L_1231, L_11f4, L_128a, L_1237, L_12bf, L_1290, L_exit;

//   double fmod(double x, double y)
// {
// 00007FF605BD10D0  sub         rsp,28h
  __ subptr(rsp, 0x28);
// double a, b, sgn_a, q, bs, bs2;
// unsigned eq;

//     // |x|, |y|
//     a = DP_AND(x, DP_CONST(7fffffffffffffff));
// 00007FF605BD10D4  mov         rax,7FFFFFFFFFFFFFFFh
  __ mov64(rax, 0x7fffffffffffffff);
// /*
// ** Copyright (C) 1985-2023 Intel Corporation.
// **
// ** The information and source code contained herein is the exclusive property
// ** of Intel Corporation and may not be disclosed, examined, or reproduced in
// ** whole or in part without explicit written authorization from the Company.
// */

// #include <ia32intrin.h>
// #include <emmintrin.h>
// #pragma float_control(precise, on)

// #define UINT32 unsigned int
// #define SINT32 int
// #define UINT64 unsigned __int64
// #define SINT64 __int64

// #define DP_FMA(a, b, c)    __fence(_mm_cvtsd_f64(_mm_fmadd_sd(_mm_set_sd(a), _mm_set_sd(b), _mm_set_sd(c))))
// #define DP_FMA_RN(a, b, c)    _mm_cvtsd_f64(_mm_fmadd_round_sd(_mm_set_sd(a), _mm_set_sd(b), _mm_set_sd(c), (_MM_FROUND_TO_NEAREST_INT | _MM_FROUND_NO_EXC)))
// #define DP_FMA_RZ(a, b, c) __fence(_mm_cvtsd_f64(_mm_fmadd_round_sd(_mm_set_sd(a), _mm_set_sd(b), _mm_set_sd(c), (_MM_FROUND_TO_ZERO | _MM_FROUND_NO_EXC))))

// #define DP_ROUND_RZ(a)   _mm_cvtsd_f64(_mm_roundscale_sd(_mm_setzero_pd(), _mm_set_sd(a), (_MM_FROUND_TO_ZERO | _MM_FROUND_NO_EXC)))

// #define DP_CONST(C)    _castu64_f64(0x##C##ull)
// #define DP_AND(X, Y)   _mm_cvtsd_f64(_mm_and_pd(_mm_set_sd(X), _mm_set_sd(Y)))
// #define DP_XOR(X, Y)   _mm_cvtsd_f64(_mm_xor_pd(_mm_set_sd(X), _mm_set_sd(Y)))
// #define DP_OR(X, Y)    _mm_cvtsd_f64(_mm_or_pd(_mm_set_sd(X), _mm_set_sd(Y)))
// #define DP_DIV_RZ(a, b) __fence(_mm_cvtsd_f64(_mm_div_round_sd(_mm_set_sd(a), _mm_set_sd(b), (_MM_FROUND_TO_ZERO | _MM_FROUND_NO_EXC))))
// #define DP_FNMA(a, b, c)    __fence(_mm_cvtsd_f64(_mm_fnmadd_sd(_mm_set_sd(a), _mm_set_sd(b), _mm_set_sd(c))))
// #define DP_FNMA_RZ(a, b, c) __fence(_mm_cvtsd_f64(_mm_fnmadd_round_sd(_mm_set_sd(a), _mm_set_sd(b), _mm_set_sd(c), (_MM_FROUND_TO_ZERO | _MM_FROUND_NO_EXC))))

// #define D2L(x)  _mm_castpd_si128(x)
// // transfer highest 32 bits (of low 64b) to GPR
// #define TRANSFER_HIGH_INT32(X)   _mm_extract_epi32(D2L(_mm_set_sd(X)), 1)


// double fmod(double x, double y)
// {
// 00007FF605BD10DE  vmovapd     xmm20,xmm0
// 00007FF605BD10E4  vmovapd     xmm3,xmm1
  __ movapd(xmm20, xmm0);
  __ movapd(xmm3, xmm1);
// double a, b, sgn_a, q, bs, bs2;
// unsigned eq;

//     // |x|, |y|
//     a = DP_AND(x, DP_CONST(7fffffffffffffff));
// 00007FF605BD10E8  vxorpd      xmm18,xmm18,xmm18
// 00007FF605BD10EE  vmovsd      xmm5,xmm18,xmm20
// 00007FF605BD10F4  vmovq       xmm17,rax
// 00007FF605BD10FA  vandpd      xmm0,xmm5,xmm17
  __ vxorpd(xmm18, xmm18, xmm18, Assembler::AVX_128bit);
  __ vmovsd(xmm5, xmm18, xmm20);
  __ movq(xmm17, rax);
  __ vandpd(xmm0, xmm5, xmm17, Assembler::AVX_128bit);
//     b = DP_AND(y, DP_CONST(7fffffffffffffff));
// 00007FF605BD1100  vandpd      xmm1,xmm3,xmm17
  __ vandpd(xmm1, xmm3, xmm17, Assembler::AVX_128bit);
// 	// sign(x)
// 	sgn_a = DP_XOR(x, a);
// 00007FF605BD1106  vmovsd      xmm17,xmm18,xmm0
// 00007FF605BD110C  vxorpd      xmm16,xmm5,xmm17
  __ vmovsd(xmm17, xmm18, xmm0);
  __ vxorpd(xmm16, xmm5, xmm17, Assembler::AVX_128bit);

// 	q = DP_DIV_RZ(a, b);
// 00007FF605BD1112  vmovsd      xmm5,xmm18,xmm1
// 00007FF605BD1118  vdivsd      xmm2,xmm17,xmm5 {rz-sae}
  __ vmovsd(xmm5, xmm18, xmm1);
  __ evdivsd(xmm2, xmm17, xmm5);
// 	q = DP_ROUND_RZ(q);
// 00007FF605BD111E  vmovsd      xmm4,xmm18,xmm2
// 00007FF605BD1124  vrndscalesd xmm19,xmm18,xmm4,0Bh
  __ vmovsd(xmm4, xmm18, xmm2);
  __ vrndscalesd(xmm19, xmm18, xmm4, 0x0B);

// 	eq = TRANSFER_HIGH_INT32(q);
// 00007FF605BD112B  vmovsd      xmm4,xmm18,xmm19
// 00007FF605BD1131  vpextrd     eax,xmm4,1
  __ vmovsd(xmm4, xmm18, xmm19);
  __ pextrd(rax, xmm4, 1);

// 	if (!eq)  return x + sgn_a;
// 00007FF605BD1137  test        eax,eax
// 00007FF605BD1139  je          FMOD_CONT+180h (07FF605BD12CAh)
__ testl(rax, rax);
__ jcc(Assembler::equal, L_12ca);

// 	if (eq >= 0x7fefffffu) goto SPECIAL_FMOD;
// 00007FF605BD113F  cmp         eax,7FEFFFFFh
// 00007FF605BD1144  jb          FMOD_CONT+136h (07FF605BD1280h)
__ cmpl(rax, 0x7FEFFFFF);
__ jcc(Assembler::below, L_1280);

// SPECIAL_FMOD:

// 	// y==0 or x==Inf?
// 	if ((b == 0.0) || (!(a <= DP_CONST(7fefffffffffffff))))
// 00007FF605BD114A  vxorpd      xmm2,xmm2,xmm2
  __ vxorpd(xmm2, xmm2, xmm2, Assembler::AVX_128bit);
// 00007FF605BD114E  vucomisd    xmm1,xmm2
  __ ucomisd(xmm1, xmm2);
// 00007FF605BD1152  jp          FMOD_CONT+10h (07FF605BD115Ah)
// 00007FF605BD1154  je          FMOD_CONT+127h (07FF605BD1271h)
  __ jcc(Assembler::parity, L_115a);
  __ jcc(Assembler::equal, L_1271);
// 00007FF605BD115A  mov         rax,7FEFFFFFFFFFFFFFh
// 00007FF605BD1164  mov         qword ptr [rsp+20h],rax
// 00007FF605BD1169  vmovsd      xmm2,qword ptr [rsp+20h]
// 00007FF605BD116F  vcomisd     xmm2,xmm0
// 00007FF605BD1173  jb          FMOD_CONT+127h (07FF605BD1271h)
  __ bind(L_115a);
  __ mov64(rax, 0x7FEFFFFFFFFFFFFF);
  __ movq(Address(rsp, 0x20), rax);
  __ movsd(xmm2, Address(rsp, 0x20));
  __ comisd(xmm2, xmm0);
  __ jcc(Assembler::below, L_1271);
// 	// y is NaN?
// 	if (!(b <= DP_CONST(7ff0000000000000))) return y + y;
// 00007FF605BD1179  mov         rax,7FF0000000000000h
// 00007FF605BD1183  mov         qword ptr [rsp+20h],rax
// 00007FF605BD1188  vmovsd      xmm2,qword ptr [rsp+20h]
// 00007FF605BD118E  vcomisd     xmm2,xmm1
// 00007FF605BD1192  jb          FMOD_CONT+11Eh (07FF605BD1268h)
  __ mov64(rax, 0x7FF0000000000000);
  __ movq(Address(rsp, 0x20), rax);
  __ movsd(xmm2, Address(rsp, 0x20));
  __ comisd(xmm2, xmm1);
  __ jcc(Assembler::below, L_1268);

// 	// b* 2*1023
// 	bs = b * DP_CONST(7fe0000000000000);
// 00007FF605BD1198  mov         rax,7FE0000000000000h
// 00007FF605BD11A2  mov         qword ptr [rsp+20h],rax
// 00007FF605BD11A7  vmovsd      xmm21,qword ptr [rsp+20h]
// 00007FF605BD11AF  vmulsd      xmm2,xmm1,xmm21
  __ mov64(rax, 0x7FE0000000000000);
  __ movq(Address(rsp, 0x20), rax);
  __ movsd(xmm21, Address(rsp, 0x20));
  __ vmulsd(xmm2, xmm1, xmm21);

// 	q = DP_DIV_RZ(a, bs);
// 00007FF605BD11B5  vmovsd      xmm4,xmm18,xmm2
// 00007FF605BD11BB  vdivsd      xmm3,xmm17,xmm4 {rz-sae}
  __ vmovsd(xmm4, xmm18, xmm2);
  __ evdivsd(xmm3, xmm17, xmm4);
// 	q = DP_ROUND_RZ(q);
// 00007FF605BD11C1  vmovsd      xmm19,xmm18,xmm3
// 00007FF605BD11C7  vrndscalesd xmm20,xmm18,xmm19,0Bh
  __ vmovsd(xmm19, xmm18, xmm3);
  __ vrndscalesd(xmm20, xmm18, xmm19, 0x0B);

// 	eq = TRANSFER_HIGH_INT32(q);
// 00007FF605BD11CE  vmovsd      xmm3,xmm18,xmm20
// 00007FF605BD11D4  vpextrd     edx,xmm3,1
  __ vmovsd(xmm3, xmm18, xmm20);
  __ pextrd(rdx, xmm3, 1);

// 	if (eq >= 0x7fefffffu)
// 00007FF605BD11DA  cmp         edx,7FEFFFFFh
// 00007FF605BD11E0  jb          FMOD_CONT+0DDh (07FF605BD1227h)
  __ cmpl(rdx, 0x7FEFFFFF);
  __ jcc(Assembler::below, L_1227);
// 	{
// 		// b* 2*1023 * 2^1023
// 		bs2 = bs * DP_CONST(7fe0000000000000);
// 00007FF605BD11E2  vmulsd      xmm3,xmm2,xmm21
  __ vmulsd(xmm3, xmm2, xmm21);
// 		while (bs2 <= a)
// 00007FF605BD11E8  vcomisd     xmm0,xmm3
// 00007FF605BD11EC  jb          FMOD_CONT+0E7h (07FF605BD1231h)
  __ comisd(xmm0, xmm3);
  __ jcc(Assembler::below, L_1231);
// 		{
// 			q = DP_DIV_RZ(a, bs2);
// 00007FF605BD11EE  vmovsd      xmm17,xmm18,xmm3
// 00007FF605BD11F4  vmovsd      xmm22,xmm18,xmm0
// 00007FF605BD11FA  vdivsd      xmm0,xmm22,xmm17 {rz-sae}
  __ vmovsd(xmm17, xmm18, xmm3);
  __ bind(L_11f4);
  __ vmovsd(xmm22, xmm18, xmm0);
  __ evdivsd(xmm0, xmm22, xmm17);
// 			q = DP_ROUND_RZ(q);
// 00007FF605BD1200  vmovsd      xmm19,xmm18,xmm0
// 00007FF605BD1206  vrndscalesd xmm20,xmm18,xmm19,0Bh
  __ vmovsd(xmm19, xmm18, xmm0);
  __ vrndscalesd(xmm20, xmm18, xmm19, 0x0B);
// 			a = DP_FNMA_RZ(bs2, q, a);
// 00007FF605BD120D  vmovapd     xmm0,xmm17
// 00007FF605BD1213  vmovsd      xmm21,xmm18,xmm20
// 00007FF605BD1219  vfnmadd213sd xmm0,xmm21,xmm22 {rz-sae}
  __ movapd(xmm0, xmm17);
  __ vmovsd(xmm21, xmm18, xmm20);
  __ evfnmadd213sd(xmm0, xmm21, xmm22, Assembler::EVEX_RZ);
// 		while (bs2 <= a)
// 00007FF605BD121F  vcomisd     xmm0,xmm3
// 00007FF605BD1223  jae         FMOD_CONT+0AAh (07FF605BD11F4h)
// 00007FF605BD1225  jmp         FMOD_CONT+0E7h (07FF605BD1231h)
  __ comisd(xmm0, xmm3);
  __ jcc(Assembler::aboveEqual, L_11f4);
  __ jmp(L_1231);
// 		}
// 	}
// 	else
// 	a = DP_FNMA_RZ(bs, q, a);
// 00007FF605BD1227  vmovapd     xmm0,xmm4
// 00007FF605BD122B  vfnmadd213sd xmm0,xmm3,xmm17 {rz-sae}
  __ bind(L_1227);
  __ movapd(xmm0, xmm4);
  __ evfnmadd213sd(xmm0, xmm3, xmm17, Assembler::EVEX_RZ);

// 	while (bs <= a)
// 00007FF605BD1231  vcomisd     xmm0,xmm2
// 00007FF605BD1235  jb          FMOD_CONT+140h (07FF605BD128Ah)
  __ bind(L_1231);
  __ comisd(xmm0, xmm2);
  __ jcc(Assembler::below, L_128a);
// 	{
// 		q = DP_DIV_RZ(a, bs);
// 00007FF605BD1237  vmovsd      xmm20,xmm18,xmm0
// 00007FF605BD123D  vdivsd      xmm0,xmm20,xmm4 {rz-sae}
  __ bind(L_1237);
  __ vmovsd(xmm20, xmm18, xmm0);
  __ evdivsd(xmm0, xmm20, xmm4);
// 		q = DP_ROUND_RZ(q);
// 00007FF605BD1243  vmovsd      xmm3,xmm18,xmm0
// 00007FF605BD1249  vrndscalesd xmm17,xmm18,xmm3,0Bh
  __ vmovsd(xmm3, xmm18, xmm0);
  __ vrndscalesd(xmm17, xmm18, xmm3, 0x0B);
// 		a = DP_FNMA_RZ(bs, q, a);
// 00007FF605BD1250  vmovapd     xmm0,xmm4
// 00007FF605BD1254  vmovsd      xmm19,xmm18,xmm17
// 00007FF605BD125A  vfnmadd213sd xmm0,xmm19,xmm20 {rz-sae}
  __ movapd(xmm0, xmm4);
  __ vmovsd(xmm19, xmm18, xmm17);
  __ evfnmadd213sd(xmm0, xmm19, xmm20, Assembler::EVEX_RZ);

// 	while (bs <= a)
// 00007FF605BD1260  vcomisd     xmm0,xmm2
// 00007FF605BD1264  jae         FMOD_CONT+0EDh (07FF605BD1237h)
// 00007FF605BD1266  jmp         FMOD_CONT+140h (07FF605BD128Ah)
  __ comisd(xmm0, xmm2);
  __ jcc(Assembler::aboveEqual, L_1237);
  __ jmp(L_128a);
// 	// y is NaN?
// 	if (!(b <= DP_CONST(7ff0000000000000))) return y + y;
// 00007FF605BD1268  vaddsd      xmm0,xmm3,xmm3
// 00007FF605BD126C  add         rsp,28h
// 00007FF605BD1270  ret
__ bind(L_1268);
__ vaddsd(xmm0, xmm3, xmm3);
__ jmp(L_exit);
// 		return DP_FNMA(b, q, a);    // NaN
// 00007FF605BD1271  vfnmadd213sd xmm5,xmm4,xmm17
// 00007FF605BD1277  vmovapd     xmm0,xmm5
// 00007FF605BD127B  add         rsp,28h
// 00007FF605BD127F  ret
  __ bind(L_1271);
  __ vfnmadd213sd(xmm5, xmm4, xmm17);
  __ movapd(xmm0, xmm5);
  __ jmp(L_exit);

// 	a = DP_FNMA_RZ(b, q, a);
// 00007FF605BD1280  vmovapd     xmm0,xmm5
// 00007FF605BD1284  vfnmadd213sd xmm0,xmm4,xmm17 {rz-sae}
  __ bind(L_1280);
  __ movapd(xmm0, xmm5);
  __ evfnmadd213sd(xmm0, xmm4, xmm17, Assembler::EVEX_RZ);

// FMOD_CONT:
// 	while (b <= a)
// 00007FF605BD128A  vcomisd     xmm0,xmm1
// 00007FF605BD128E  jb          FMOD_CONT+175h (07FF605BD12BFh)
  __ bind(L_128a);
  __ comisd(xmm0, xmm1);
  __ jcc(Assembler::below, L_12bf);
// 	{
// 		q = DP_DIV_RZ(a, b);
// 00007FF605BD1290  vmovsd      xmm17,xmm18,xmm0
// 00007FF605BD1296  vdivsd      xmm0,xmm17,xmm5 {rz-sae}
  __ bind(L_1290);
  __ vmovsd(xmm17, xmm18, xmm0);
  __ evdivsd(xmm0, xmm17, xmm5);
// 		q = DP_ROUND_RZ(q);
// 00007FF605BD129C  vmovsd      xmm2,xmm18,xmm0
// 00007FF605BD12A2  vrndscalesd xmm3,xmm18,xmm2,0Bh
  __ vmovsd(xmm2, xmm18, xmm0);
  __ vrndscalesd(xmm3, xmm18, xmm2, 0x0B);
// 		a = DP_FNMA_RZ(b, q, a);
// 00007FF605BD12A9  vmovapd     xmm0,xmm5
// 00007FF605BD12AD  vmovsd      xmm4,xmm18,xmm3
// 00007FF605BD12B3  vfnmadd213sd xmm0,xmm4,xmm17 {rz-sae}
  __ movapd(xmm0, xmm5);
  __ vmovsd(xmm4, xmm18, xmm3);
  __ evfnmadd213sd(xmm0, xmm4, xmm17, Assembler::EVEX_RZ);

// FMOD_CONT:
// 	while (b <= a)
// 00007FF605BD12B9  vcomisd     xmm0,xmm1
// 00007FF605BD12BD  jae         FMOD_CONT+146h (07FF605BD1290h)
// 	}
  __ comisd(xmm0, xmm1);
  __ jcc(Assembler::aboveEqual, L_1290);

// 	a = DP_XOR(a, sgn_a);
// 00007FF605BD12BF  vxorpd      xmm0,xmm0,xmm16
  __ bind(L_12bf);
  __ vxorpd(xmm0, xmm0, xmm16, Assembler::AVX_128bit);

// 	return a;
// 00007FF605BD12C5  add         rsp,28h
// 00007FF605BD12C9  ret
__ jmp(L_exit);

// 	if (!eq)  return x + sgn_a;
// 00007FF605BD12CA  vaddsd      xmm0,xmm20,xmm16
// 00007FF605BD12D0  add         rsp,28h
// 00007FF605BD12D4  ret
  __ bind(L_12ca);
  __ vaddsd(xmm0, xmm20, xmm16);
  __ bind(L_exit);
  __ addptr(rsp, 0x28);

  } else if (VM_Version::supports_fma()) {       // AVX2 version

    Label L_104a, L_11bd, L_10c1, L_1090, L_11b9, L_10e7, L_11af, L_111c, L_10f3, L_116e, L_112a;
    Label L_1173, L_1157, L_117f, L_11a0;

//   double fmod(double x, double y)
// {
// 00007FF676C01000  push        rax
// double a, b, sgn_a, q, bs, bs2, corr, res;
// unsigned eq;
// unsigned mxcsr, mxcsr_rz;

// 	__asm { stmxcsr DWORD PTR[mxcsr] }
// 00007FF676C01001  stmxcsr     dword ptr [rsp]
// 	mxcsr_rz = 0x7f80 | mxcsr;
// 00007FF676C01005  mov         eax,dword ptr [rsp]
// 00007FF676C01008  mov         ecx,eax
// 00007FF676C0100A  or          ecx,7F80h
// 00007FF676C01010  mov         dword ptr [rsp+4],ecx
  __ push(rax);
  __ stmxcsr(Address(rsp, 0));
  __ movl(rax, Address(rsp, 0));
  __ movl(rcx, rax);
  __ orl(rcx, 0x7f80);
  __ movl(Address(rsp, 0x04), rcx);

//     // |x|, |y|
//     a = DP_AND(x, DP_CONST(7fffffffffffffff));
// 00007FF676C01014  vmovq       xmm2,xmm0
// 00007FF676C01018  vmovdqu     xmm3,xmmword ptr [__xmm@7fffffffffffffff7fffffffffffffff (07FF676C03000h)]
// 00007FF676C01020  vpand       xmm4,xmm2,xmm3
//     b = DP_AND(y, DP_CONST(7fffffffffffffff));
// 00007FF676C01024  vpand       xmm3,xmm1,xmm3
// 00007FF676C01028  mov         rcx,8000000000000000h
// 	// sign(x)
// 	sgn_a = DP_XOR(x, a);
// 00007FF676C01032  vmovq       xmm5,rcx
// 00007FF676C01037  vpand       xmm2,xmm2,xmm5
  __ movq(xmm2, xmm0);
  __ vmovdqu(xmm3, ExternalAddress((address)CONST_NaN));
  __ vpand(xmm4, xmm2, xmm3, Assembler::AVX_128bit);
  __ vpand(xmm3, xmm1, xmm3, Assembler::AVX_128bit);
  __ mov64(rcx, 0x8000000000000000);
  __ movq(xmm5, rcx);
  __ vpand(xmm2, xmm2, xmm5, Assembler::AVX_128bit);

// 	if (a < b)  return x + sgn_a;
// 00007FF676C0103B  vucomisd    xmm3,xmm4
// 00007FF676C0103F  jbe         fmod+4Ah (07FF676C0104Ah)
// 00007FF676C01041  vaddsd      xmm0,xmm2,xmm0
// 00007FF676C01045  jmp         fmod+1BDh (07FF676C011BDh)
  __ ucomisd(xmm3, xmm4);
  __ jcc(Assembler::belowEqual, L_104a);
  __ vaddsd(xmm0, xmm2, xmm0);
  __ jmp(L_11bd);

// 	if (((mxcsr & 0x6000)!=0x2000) && (a < b * 0x1p+260))
// 00007FF676C0104A  and         eax,6000h
// 00007FF676C0104F  cmp         eax,2000h
// 00007FF676C01054  je          fmod+0C1h (07FF676C010C1h)
// 00007FF676C01056  vmulsd      xmm0,xmm3,mmword ptr [__real@5030000000000000 (07FF676C03010h)]
// 00007FF676C0105E  vucomisd    xmm0,xmm4
// 00007FF676C01062  jbe         fmod+0C1h (07FF676C010C1h)
  __ bind(L_104a);
  __ andl(rax, 0x6000);
  __ cmpl(rax, 0x2000);
  __ jcc(Assembler::equal, L_10c1);
  __ vmulsd(xmm0, xmm3, ExternalAddress((address)CONST_1p260));
  __ ucomisd(xmm0, xmm4);
  __ jcc(Assembler::belowEqual, L_10c1);
// 	{
// 		q = DP_DIV(a, b);
// 00007FF676C01064  vdivpd      xmm0,xmm4,xmm3
// 		corr = DP_SHR(DP_FNMA(b, q, a), 63);
// 00007FF676C01068  vmovapd     xmm1,xmm0
// 00007FF676C0106C  vfnmadd213sd xmm1,xmm3,xmm4
// 00007FF676C01071  vmovq       xmm5,xmm1
// 00007FF676C01075  vpxor       xmm1,xmm1,xmm1
// 00007FF676C01079  vpcmpgtq    xmm5,xmm1,xmm5
  __ vdivpd(xmm0, xmm4, xmm3, Assembler::AVX_128bit);
  __ movapd(xmm1, xmm0);
  __ vfnmadd213sd(xmm1, xmm3, xmm4);
  __ movq(xmm5, xmm1);
  __ vpxor(xmm1, xmm1, xmm1, Assembler::AVX_128bit);
  __ vpcmpgtq(xmm5, xmm1, xmm5, Assembler::AVX_128bit);
// 		q = DP_PSUBQ(q, corr);
// 00007FF676C0107E  vpaddq      xmm0,xmm5,xmm0
// 		q = DP_TRUNC(q);
// 00007FF676C01082  vroundsd    xmm0,xmm0,xmm0,3
// 		a = DP_FNMA(b, q, a);
// 00007FF676C01088  vfnmadd213sd xmm0,xmm3,xmm4
// 00007FF676C0108D  nop         dword ptr [rax]
// 		while (b <= a)
// 00007FF676C01090  vucomisd    xmm0,xmm3
// 00007FF676C01094  jb          fmod+1B9h (07FF676C011B9h)
  __ vpaddq(xmm0, xmm5, xmm0, Assembler::AVX_128bit);
  __ vroundsd(xmm0, xmm0, xmm0, 3);
  __ vfnmadd213sd(xmm0, xmm3, xmm4);
  __ align32();
  __ bind(L_1090);
  __ ucomisd(xmm0, xmm3);
  __ jcc(Assembler::below, L_11b9);
// 		{
// 			q = DP_DIV(a, b);
// 00007FF676C0109A  vdivsd      xmm4,xmm0,xmm3
// 			corr = DP_SHR(DP_FNMA(b, q, a), 63);
// 00007FF676C0109E  vmovapd     xmm5,xmm4
// 00007FF676C010A2  vfnmadd213sd xmm5,xmm3,xmm0
// 00007FF676C010A7  vmovq       xmm5,xmm5
// 00007FF676C010AB  vpcmpgtq    xmm5,xmm1,xmm5
// 			q = DP_PSUBQ(q, corr);
// 00007FF676C010B0  vpaddq      xmm4,xmm5,xmm4
// 			q = DP_TRUNC(q);
// 00007FF676C010B4  vroundsd    xmm4,xmm4,xmm4,3
// 			a = DP_FNMA(b, q, a);
// 00007FF676C010BA  vfnmadd231sd xmm0,xmm3,xmm4
// 00007FF676C010BF  jmp         fmod+90h (07FF676C01090h)
  __ evdivsd(xmm4, xmm0, xmm3);
  __ movapd(xmm5, xmm4);
  __ vfnmadd213sd(xmm5, xmm3, xmm0);
  __ movq(xmm5, xmm5);
  __ vpcmpgtq(xmm5, xmm1, xmm5, Assembler::AVX_128bit);
  __ vpaddq(xmm4, xmm5, xmm4, Assembler::AVX_128bit);
  __ vroundsd(xmm4, xmm4, xmm4, 3);
  __ vfnmadd231sd(xmm0, xmm3, xmm4);
  __ jmp(L_1090);
// 		}
// 		return DP_XOR(a, sgn_a);
// 	}

// 	__asm { ldmxcsr DWORD PTR [mxcsr_rz] }
// 00007FF676C010C1  ldmxcsr     dword ptr [mxcsr_rz]

// 	q = DP_DIV(a, b);
// 00007FF676C010C6  vdivpd      xmm0,xmm4,xmm3
// 	q = DP_TRUNC(q);
// 00007FF676C010CA  vroundsd    xmm0,xmm0,xmm0,3
  __ bind(L_10c1);
  __ ldmxcsr(Address(rsp, 0x04));
  __ vdivpd(xmm0, xmm4, xmm3, Assembler::AVX_128bit);
  __ vroundsd(xmm0, xmm0, xmm0, 3);

// 	eq = TRANSFER_HIGH_INT32(q);
// 00007FF676C010D0  vextractps  eax,xmm0,1

// 	if (__builtin_expect((eq >= 0x7fefffffu), (0==1))) goto SPECIAL_FMOD;
// 00007FF676C010D6  cmp         eax,7FEFFFFEh
// 00007FF676C010DB  ja          fmod+0E7h (07FF676C010E7h)
  __ evextractps(rax, xmm0, 1);
  __ cmpl(rax, 0x7feffffe);
  __ jcc(Assembler::above, L_10e7);

// 	a = DP_FNMA(b, q, a);
// 00007FF676C010DD  vfnmadd213sd xmm0,xmm3,xmm4
// 00007FF676C010E2  jmp         fmod+1AFh (07FF676C011AFh)
  __ vfnmadd213sd(xmm0, xmm3, xmm4);
  __ jmp(L_11af);
// 	a = DP_XOR(a, sgn_a);

// 	return a;

// SPECIAL_FMOD:

// 	// y==0 or x==Inf?
// 	if ((b == 0.0) || (!(a <= DP_CONST(7fefffffffffffff))))
// 00007FF676C010E7  vpxor       xmm5,xmm5,xmm5
// 00007FF676C010EB  vucomisd    xmm3,xmm5
// 00007FF676C010EF  jne         fmod+0F3h (07FF676C010F3h)
// 00007FF676C010F1  jnp         fmod+11Ch (07FF676C0111Ch)
// 00007FF676C010F3  vmovsd      xmm5,qword ptr [__real@7fefffffffffffff (07FF676C03018h)]
// 00007FF676C010FB  vucomisd    xmm5,xmm4
// 00007FF676C010FF  jb          fmod+11Ch (07FF676C0111Ch)
  __ bind(L_10e7);
  __ vpxor(xmm5, xmm5, xmm5, Assembler::AVX_128bit);
  __ ucomisd(xmm3, xmm5);
  __ jcc(Assembler::notEqual, L_10f3);
  __ jcc(Assembler::noParity, L_111c);

  __ bind(L_10f3);
  __ movsd(xmm5, ExternalAddress((address)CONST_MAX));
  __ ucomisd(xmm5, xmm4);
  __ jcc(Assembler::below, L_111c);
// 		return res;
// 	}
// 	// y is NaN?
// 	if (!(b <= DP_CONST(7ff0000000000000))) {
// 00007FF676C01101  vmovsd      xmm0,qword ptr [__real@7ff0000000000000 (07FF676C03020h)]
// 00007FF676C01109  vucomisd    xmm0,xmm3
// 00007FF676C0110D  jae         fmod+12Ah (07FF676C0112Ah)
// 		res = y + y;
// 00007FF676C0110F  vaddsd      xmm0,xmm1,xmm1
// 		__asm { ldmxcsr DWORD PTR[mxcsr] }
// 00007FF676C01113  ldmxcsr     dword ptr [rsp]
// 00007FF676C01117  jmp         fmod+1BDh (07FF676C011BDh)
  __ movsd(xmm0, ExternalAddress((address)CONST_INF));
  __ ucomisd(xmm0, xmm3);
  __ jcc(Assembler::aboveEqual, L_112a);
  __ vaddsd(xmm0, xmm0, xmm1);
  __ ldmxcsr(Address(rsp, 0));
  __ jmp(L_11bd);
// 	{
// 		res = DP_FNMA(b, q, a);    // NaN
// 00007FF676C0111C  vfnmadd213sd xmm0,xmm3,xmm4
// 		__asm { ldmxcsr DWORD PTR[mxcsr] }
// 00007FF676C01121  ldmxcsr     dword ptr [rsp]
// 00007FF676C01125  jmp         fmod+1BDh (07FF676C011BDh)
  __ bind(L_111c);
  __ vfnmadd213sd(xmm0, xmm3, xmm4);
  __ ldmxcsr(Address(rsp, 0));
  __ jmp(L_11bd);
// 		return res;
// 	}

// 	// b* 2*1023
// 	bs = b * DP_CONST(7fe0000000000000);
// 00007FF676C0112A  vmulsd      xmm1,xmm3,mmword ptr [__real@7fe0000000000000 (07FF676C03028h)]

// 	q = DP_DIV(a, bs);
// 00007FF676C01132  vdivsd      xmm0,xmm4,xmm1
// 	q = DP_TRUNC(q);
// 00007FF676C01136  vroundsd    xmm0,xmm0,xmm0,3

// 	eq = TRANSFER_HIGH_INT32(q);
// 00007FF676C0113C  vextractps  eax,xmm0,1
  __ bind(L_112a);
  __ vmulsd(xmm1, xmm3, ExternalAddress((address)CONST_e307));
  __ evdivsd(xmm0, xmm4, xmm1);
  __ vroundsd(xmm0, xmm0, xmm0, 3);
  __ evextractps(rax, xmm0, 1);

// 	if (eq >= 0x7fefffffu)
// 00007FF676C01142  cmp         eax,7FEFFFFFh
// 00007FF676C01147  jb          fmod+16Eh (07FF676C0116Eh)
  __ cmpl(rax, 0x7fefffff);
  __ jcc(Assembler::below, L_116e);
// 	{
// 		// b* 2*1023 * 2^1023
// 		bs2 = bs * DP_CONST(7fe0000000000000);
// 00007FF676C01149  vmulsd      xmm0,xmm1,mmword ptr [__real@7fe0000000000000 (07FF676C03028h)]
// 		while (bs2 <= a)
// 00007FF676C01151  vucomisd    xmm4,xmm0
// 00007FF676C01155  jb          fmod+173h (07FF676C01173h)
// 		{
// 			q = DP_DIV(a, bs2);
// 00007FF676C01157  vdivsd      xmm5,xmm4,xmm0
// 			q = DP_TRUNC(q);
// 00007FF676C0115B  vroundsd    xmm5,xmm5,xmm5,3
// 			a = DP_FNMA(bs2, q, a);
// 00007FF676C01161  vfnmadd231sd xmm4,xmm0,xmm5
// 		while (bs2 <= a)
// 00007FF676C01166  vucomisd    xmm4,xmm0
// 00007FF676C0116A  jae         fmod+157h (07FF676C01157h)
// 00007FF676C0116C  jmp         fmod+173h (07FF676C01173h)
  __ vmulsd(xmm0, xmm1, ExternalAddress((address)CONST_e307));
  __ ucomisd(xmm4, xmm0);
  __ jcc(Assembler::below, L_1173);
  __ bind(L_1157);
  __ evdivsd(xmm5, xmm4, xmm0);
  __ vroundsd(xmm5, xmm5, xmm5, 3);
  __ vfnmadd231sd(xmm4, xmm0, xmm5);
  __ ucomisd(xmm4, xmm0);
  __ jcc(Assembler::aboveEqual, L_1157);
  __ jmp(L_1173);
// 		}
// 	}
// 	else
// 	a = DP_FNMA(bs, q, a);
// 00007FF676C0116E  vfnmadd231sd xmm4,xmm1,xmm0

// 	while (bs <= a)
// 00007FF676C01173  vucomisd    xmm4,xmm1
// 00007FF676C01177  jae         fmod+17Fh (07FF676C0117Fh)
// 00007FF676C01179  vmovapd     xmm0,xmm4
// 00007FF676C0117D  jmp         fmod+1AFh (07FF676C011AFh)
  __ bind(L_116e);
  __ vfnmadd231sd(xmm4, xmm1, xmm0);
  __ bind(L_1173);
  __ ucomisd(xmm4, xmm1);
  __ jcc(Assembler::aboveEqual, L_117f);
  __ movapd(xmm0, xmm4);
  __ jmp(L_11af);
// 	{
// 		q = DP_DIV(a, bs);
// 00007FF676C0117F  vdivsd      xmm0,xmm4,xmm1
// 		q = DP_TRUNC(q);
// 00007FF676C01183  vroundsd    xmm0,xmm0,xmm0,3
// 		a = DP_FNMA(bs, q, a);
// 00007FF676C01189  vfnmadd213sd xmm0,xmm1,xmm4
  __ bind(L_117f);
  __ evdivsd(xmm0, xmm4, xmm1);
  __ vroundsd(xmm0, xmm0, xmm0, 3);
  __ vfnmadd213sd(xmm0, xmm1, xmm4);

// 	while (bs <= a)
// 00007FF676C0118E  vucomisd    xmm0,xmm1
// 00007FF676C01192  vmovapd     xmm4,xmm0
// 00007FF676C01196  jae         fmod+17Fh (07FF676C0117Fh)
// 00007FF676C01198  jmp         fmod+1AFh (07FF676C011AFh)
// 00007FF676C0119A  nop         word ptr [rax+rax]
  __ ucomisd(xmm0, xmm1);
  __ movapd(xmm4, xmm0);
  __ jcc(Assembler::aboveEqual, L_117f);
  __ jmp(L_11af);
// 	{
// 		q = DP_DIV(a, b);
// 00007FF676C011A0  vdivsd      xmm1,xmm0,xmm3
// 		q = DP_TRUNC(q);
// 00007FF676C011A4  vroundsd    xmm1,xmm1,xmm1,3
// 		a = DP_FNMA(b, q, a);
// 00007FF676C011AA  vfnmadd231sd xmm0,xmm3,xmm1

// FMOD_CONT:
// 	while (b <= a)
// 00007FF676C011AF  vucomisd    xmm0,xmm3
// 00007FF676C011B3  jae         fmod+1A0h (07FF676C011A0h)
  __ bind(L_11a0);
  __ vdivsd(xmm1, xmm0, xmm3);
  __ vroundsd(xmm1, xmm1, xmm1, 3);
  __ vfnmadd231sd(xmm0, xmm3, xmm1);
  __ bind(L_11af);
  __ ucomisd(xmm0, xmm3);
  __ jcc(Assembler::aboveEqual, L_11a0);
// 	}

// 	__asm { ldmxcsr DWORD PTR[mxcsr] }
// 00007FF676C011B5  ldmxcsr     dword ptr [rsp]
// 00007FF676C011B9  vpxor       xmm0,xmm2,xmm0
// 	}

// 	goto FMOD_CONT;

// }
// 00007FF676C011BD  pop         rax
// 00007FF676C011BE  ret
  __ ldmxcsr(Address(rsp, 0));
  __ bind(L_11b9);
  __ vpxor(xmm0, xmm2, xmm0, Assembler::AVX_128bit);
  __ bind(L_11bd);
  __ pop(rax);

  } else {                                       // SSE version
//    double fmod (double x, double y)
// {
// 00007FF6AEFB10D0  sub         rsp,68h
// 00007FF6AEFB10D4  movsd       mmword ptr [x],xmm0
// 00007FF6AEFB10DA  movsd       mmword ptr [y],xmm1
// 00007FF6AEFB10E0  movups      xmmword ptr [rsp+40h],xmm6
// 00007FF6AEFB10E5  movups      xmmword ptr [rsp+30h],xmm7




//    __asm { movq xmm0, x }
// 00007FF6AEFB10EA  movq        xmm0,mmword ptr [x]
//    mov rax, QWORD PTR [x]
// 00007FF6AEFB10F0  mov         rax,qword ptr [x]


//    mov rcx, 7fffffffffffffffh
// 00007FF6AEFB10F5  mov         rcx,7FFFFFFFFFFFFFFFh

//    mov r8, 07fefffffffffffffh
// 00007FF6AEFB10FF  mov         r8,7FEFFFFFFFFFFFFFh




//    __asm { movq xmm1, y }
// 00007FF6AEFB1109  movq        xmm1,mmword ptr [y]
//    mov r10, QWORD PTR [y]
// 00007FF6AEFB110F  mov         r10,qword ptr [y]


//    mov rdx, rcx
// 00007FF6AEFB1114  mov         rdx,rcx
//    mov r11, 25
// 00007FF6AEFB1117  mov         r11,19h


//    and rcx, rax
// 00007FF6AEFB111E  and         rcx,rax

//    sub r8, rcx
// 00007FF6AEFB1121  sub         r8,rcx


//    movq xmm2, xmm0
// 00007FF6AEFB1124  movq        xmm2,xmm0

//    movsd xmm3, QWORD PTR [TRUNC_MASK]
// 00007FF6AEFB1128  movsd       xmm3,mmword ptr [TRUNC_MASK (07FF6AEFB5290h)]


//    and rdx, r10
// 00007FF6AEFB1130  and         rdx,r10

//    shl r10, 12
// 00007FF6AEFB1133  shl         r10,0Ch



//    sub rcx, rdx
// 00007FF6AEFB1137  sub         rcx,rdx
//    ; sign of x
//    sar rcx, 52
// 00007FF6AEFB113A  sar         rcx,34h


//    movq xmm5, xmm1
// 00007FF6AEFB113E  movq        xmm5,xmm1




//    or r8, rcx
// 00007FF6AEFB1142  or          r8,rcx

//    sub r11, rcx
// 00007FF6AEFB1145  sub         r11,rcx

//    or r8, r11
// 00007FF6AEFB1148  or          r8,r11

//    shr r10, 1
// 00007FF6AEFB114B  shr         r10,1
//    sub r10, 1
// 00007FF6AEFB114E  sub         r10,1


//    movd xmm4, r11
// 00007FF6AEFB1152  movq        xmm4,r11

//    andpd xmm5, xmm3
// 00007FF6AEFB1157  andpd       xmm5,xmm3


//    or r8, r10
// 00007FF6AEFB115B  or          r8,r10


//    shr rdx, 52
// 00007FF6AEFB115E  shr         rdx,34h
//    mov r9, 7fdh - 52
// 00007FF6AEFB1162  mov         r9,7C9h
//    sub rdx, 53
// 00007FF6AEFB1169  sub         rdx,35h
//    or r8, rdx
// 00007FF6AEFB116D  or          r8,rdx
//    sub r9, rdx
// 00007FF6AEFB1170  sub         r9,rdx
//    or r8, r9
// 00007FF6AEFB1173  or          r8,r9

//    cmp r8, 0
// 00007FF6AEFB1176  cmp         r8,0
//    jl FMOD_SPECIAL
// 00007FF6AEFB117A  jl          fmod+114h (07FF6AEFB11E4h)


//    divsd xmm2, xmm1
// 00007FF6AEFB117C  divsd       xmm2,xmm1

//    mov r9, 8000000000000000h
// 00007FF6AEFB1180  mov         r9,8000000000000000h
//    and rax, r9
// 00007FF6AEFB118A  and         rax,r9


//    movq xmm7, xmm1
// 00007FF6AEFB118D  movq        xmm7,xmm1

//    psllq xmm3, xmm4
// 00007FF6AEFB1191  psllq       xmm3,xmm4

//    movd xmm4, rax
// 00007FF6AEFB1195  movq        xmm4,rax

//    subsd xmm1, xmm5
// 00007FF6AEFB119A  subsd       xmm1,xmm5


//    movsd xmm6, QWORD PTR [NSGNMASK]
// 00007FF6AEFB119E  movsd       xmm6,mmword ptr [NSGNMASK (07FF6AEFB5298h)]


//    xorpd xmm5, xmm4
// 00007FF6AEFB11A6  xorpd       xmm5,xmm4


//    andpd xmm2, xmm3
// 00007FF6AEFB11AA  andpd       xmm2,xmm3
//    xorpd xmm1, xmm4
// 00007FF6AEFB11AE  xorpd       xmm1,xmm4


//    mulsd xmm5, xmm2
// 00007FF6AEFB11B2  mulsd       xmm5,xmm2

//    mulsd xmm1, xmm2
// 00007FF6AEFB11B6  mulsd       xmm1,xmm2

//    andpd xmm0, xmm6
// 00007FF6AEFB11BA  andpd       xmm0,xmm6

//    subsd xmm0, xmm5
// 00007FF6AEFB11BE  subsd       xmm0,xmm5

//    andpd xmm7, xmm6
// 00007FF6AEFB11C2  andpd       xmm7,xmm6

//    subsd xmm0, xmm1
// 00007FF6AEFB11C6  subsd       xmm0,xmm1


//    xorpd xmm6, xmm6
// 00007FF6AEFB11CA  xorpd       xmm6,xmm6

//    cmpsd xmm6, xmm0, 6
// 00007FF6AEFB11CE  cmpnlesd    xmm6,xmm0


//    andpd xmm6, xmm7
// 00007FF6AEFB11D3  andpd       xmm6,xmm7


//    addsd xmm0, xmm6
// 00007FF6AEFB11D7  addsd       xmm0,xmm6
//    orpd xmm0, xmm4
// 00007FF6AEFB11DB  orpd        xmm0,xmm4

//    { jmp END_ASM }
// 00007FF6AEFB11DF  jmp         fmod+4D0h (07FF6AEFB15A0h)


//     or r9, rdx
// 00007FF6AEFB11E4  or          r9,rdx
//     cmp r9, 0
// 00007FF6AEFB11E7  cmp         r9,0
//     jl Y_NAN_INF_Y_DENORM
// 00007FF6AEFB11EB  jl          fmod+431h (07FF6AEFB1501h)

//     cmp rcx, 0
// 00007FF6AEFB11F1  cmp         rcx,0
//     jl FMOD_RETURN
// 00007FF6AEFB11F5  jl          fmod+4B9h (07FF6AEFB1589h)

//     ; expon_y - 1
//     add rdx, 52
// 00007FF6AEFB11FB  add         rdx,34h


//     pextrw r9d, xmm0, 3
// 00007FF6AEFB11FF  pextrw      r9d,xmm0,3
//     and r9d, 7ff0h
// 00007FF6AEFB1205  and         r9d,7FF0h
//     cmp r9d, 7ff0h
// 00007FF6AEFB120C  cmp         r9d,7FF0h
//     jz X_INF_NAN
// 00007FF6AEFB1213  je          fmod+468h (07FF6AEFB1538h)


//     cmp r10, 0
// 00007FF6AEFB1219  cmp         r10,0
//     jl Y_POWER2
// 00007FF6AEFB121D  jl          fmod+338h (07FF6AEFB1408h)





//     add edx, 26
// 00007FF6AEFB1223  add         edx,1Ah
//     shl rdx, 4
// 00007FF6AEFB1226  shl         rdx,4


//     psllq xmm1, 1
// 00007FF6AEFB122A  psllq       xmm1,1
//     psrlq xmm1, 1
// 00007FF6AEFB122F  psrlq       xmm1,1
//     psllq xmm5, 1
// 00007FF6AEFB1234  psllq       xmm5,1
//     psrlq xmm5, 1
// 00007FF6AEFB1239  psrlq       xmm5,1


//     movq xmm4, xmm1
// 00007FF6AEFB123E  movq        xmm4,xmm1

//     subsd xmm4, xmm5
// 00007FF6AEFB1242  subsd       xmm4,xmm5


//     psllq xmm2, 1
// 00007FF6AEFB1246  psllq       xmm2,1
//     psrlq xmm2, 1
// 00007FF6AEFB124B  psrlq       xmm2,1
//     mov r9, 8000000000000000h
// 00007FF6AEFB1250  mov         r9,8000000000000000h
//     and rax, r9
// 00007FF6AEFB125A  and         rax,r9
//     movq xmm0, xmm2
// 00007FF6AEFB125D  movq        xmm0,xmm2


//     neg r11
// 00007FF6AEFB1261  neg         r11
//     cmp r11, 400h - 25
// 00007FF6AEFB1264  cmp         r11,3E7h
//     jae LONG_Q_OF
// 00007FF6AEFB126B  jae         fmod+27Eh (07FF6AEFB134Eh)
//     divsd xmm2, xmm1
// 00007FF6AEFB1271  divsd       xmm2,xmm1

//     movq xmm7, xmm5
// 00007FF6AEFB1275  movq        xmm7,xmm5

//     xorpd xmm6, xmm6
// 00007FF6AEFB1279  xorpd       xmm6,xmm6


//     andpd xmm2, xmm3
// 00007FF6AEFB127D  andpd       xmm2,xmm3

//     mulsd xmm7, xmm2
// 00007FF6AEFB1281  mulsd       xmm7,xmm2

//     pextrw r10, xmm2, 3
// 00007FF6AEFB1285  pextrw      r10d,xmm2,3

//     mulsd xmm2, xmm4
// 00007FF6AEFB128B  mulsd       xmm2,xmm4

//     subsd xmm0, xmm7
// 00007FF6AEFB128F  subsd       xmm0,xmm7
//     xorpd xmm7, xmm7
// 00007FF6AEFB1293  xorpd       xmm7,xmm7

//     subsd xmm0, xmm2
// 00007FF6AEFB1297  subsd       xmm0,xmm2


//     and r10, 0fff0h
// 00007FF6AEFB129B  and         r10,0FFF0h
//     sub r10, 25*16
// 00007FF6AEFB12A2  sub         r10,190h
//     pinsrw xmm6, r10, 3
// 00007FF6AEFB12A9  pinsrw      xmm6,r10d,3

//     cmpsd xmm7, xmm0, 6
// 00007FF6AEFB12AF  cmpnlesd    xmm7,xmm0

//     mulsd xmm6, xmm1
// 00007FF6AEFB12B4  mulsd       xmm6,xmm1
//     andpd xmm7, xmm6
// 00007FF6AEFB12B8  andpd       xmm7,xmm6
//     addsd xmm0, xmm7
// 00007FF6AEFB12BC  addsd       xmm0,xmm7

//     pextrw ecx, xmm0, 3
// 00007FF6AEFB12C0  pextrw      ecx,xmm0,3
//     movq xmm2, xmm0
// 00007FF6AEFB12C5  movq        xmm2,xmm0
//     and ecx, 7ff0h
// 00007FF6AEFB12C9  and         ecx,7FF0h

//     cmp ecx, edx
// 00007FF6AEFB12CF  cmp         ecx,edx
//     ja BACK_LOOP
// 00007FF6AEFB12D1  ja          fmod+1A1h (07FF6AEFB1271h)

//     divsd xmm2, xmm1
// 00007FF6AEFB12D3  divsd       xmm2,xmm1


//     pextrw ecx, xmm2, 3
// 00007FF6AEFB12D7  pextrw      ecx,xmm2,3
//     mov r8, 418h
// 00007FF6AEFB12DC  mov         r8,418h
//     and rcx, 7ff0h
// 00007FF6AEFB12E3  and         rcx,7FF0h
//     cmp rcx, 3ff0h
// 00007FF6AEFB12EA  cmp         rcx,3FF0h
//     jb FMOD_LONG_RET
// 00007FF6AEFB12F1  jb          fmod+270h (07FF6AEFB1340h)
//     shr rcx, 4
// 00007FF6AEFB12F3  shr         rcx,4
//     sub r8, rcx
// 00007FF6AEFB12F7  sub         r8,rcx
//     movd xmm6, r8
// 00007FF6AEFB12FA  movq        xmm6,r8
//     psllq xmm3, xmm6
// 00007FF6AEFB12FF  psllq       xmm3,xmm6


//     andpd xmm2, xmm3
// 00007FF6AEFB1303  andpd       xmm2,xmm3


//     mulsd xmm5, xmm2
// 00007FF6AEFB1307  mulsd       xmm5,xmm2

//     psllq xmm1, 1
// 00007FF6AEFB130B  psllq       xmm1,1

//     mulsd xmm2, xmm4
// 00007FF6AEFB1310  mulsd       xmm2,xmm4
//     psrlq xmm1, 1
// 00007FF6AEFB1314  psrlq       xmm1,1

//     subsd xmm0, xmm5
// 00007FF6AEFB1319  subsd       xmm0,xmm5

//     movd xmm3, rax
// 00007FF6AEFB131D  movq        xmm3,rax

//     subsd xmm0, xmm2
// 00007FF6AEFB1322  subsd       xmm0,xmm2


//     xorpd xmm6, xmm6
// 00007FF6AEFB1326  xorpd       xmm6,xmm6

//     cmpsd xmm6, xmm0, 6
// 00007FF6AEFB132A  cmpnlesd    xmm6,xmm0

//     andpd xmm6, xmm1
// 00007FF6AEFB132F  andpd       xmm6,xmm1
//     addsd xmm0, xmm6
// 00007FF6AEFB1333  addsd       xmm0,xmm6
//     orpd xmm0, xmm3
// 00007FF6AEFB1337  orpd        xmm0,xmm3

//     { jmp END_ASM }
// 00007FF6AEFB133B  jmp         fmod+4D0h (07FF6AEFB15A0h)
//     movd xmm3, rax
// 00007FF6AEFB1340  movq        xmm3,rax
//     orpd xmm0, xmm3
// 00007FF6AEFB1345  orpd        xmm0,xmm3
//     { jmp END_ASM }
// 00007FF6AEFB1349  jmp         fmod+4D0h (07FF6AEFB15A0h)


//     mulsd xmm1, QWORD PTR [YSCALE_UP]
// 00007FF6AEFB134E  mulsd       xmm1,mmword ptr [YSCALE_UP (07FF6AEFB5278h)]
//     mov ecx, edx
// 00007FF6AEFB1356  mov         ecx,edx
//     add ecx, 3ff0h - 25*16
// 00007FF6AEFB1358  add         ecx,3E60h
//     mulsd xmm5, QWORD PTR [YSCALE_UP]
// 00007FF6AEFB135E  mulsd       xmm5,mmword ptr [YSCALE_UP (07FF6AEFB5278h)]
//     mulsd xmm4, QWORD PTR [YSCALE_UP]
// 00007FF6AEFB1366  mulsd       xmm4,mmword ptr [YSCALE_UP (07FF6AEFB5278h)]
//     divsd xmm2, xmm1
// 00007FF6AEFB136E  divsd       xmm2,xmm1

//     movq xmm7, xmm5
// 00007FF6AEFB1372  movq        xmm7,xmm5
//     xorpd xmm6, xmm6
// 00007FF6AEFB1376  xorpd       xmm6,xmm6


//     andpd xmm2, xmm3
// 00007FF6AEFB137A  andpd       xmm2,xmm3


//     mulsd xmm7, xmm2
// 00007FF6AEFB137E  mulsd       xmm7,xmm2

//     pextrw r10, xmm2, 3
// 00007FF6AEFB1382  pextrw      r10d,xmm2,3

//     mulsd xmm2, xmm4
// 00007FF6AEFB1388  mulsd       xmm2,xmm4

//     subsd xmm0, xmm7
// 00007FF6AEFB138C  subsd       xmm0,xmm7
//     xorpd xmm7, xmm7
// 00007FF6AEFB1390  xorpd       xmm7,xmm7

//     subsd xmm0, xmm2
// 00007FF6AEFB1394  subsd       xmm0,xmm2

//     and r10, 7ff0h
// 00007FF6AEFB1398  and         r10,7FF0h
//     sub r10, 25*16
// 00007FF6AEFB139F  sub         r10,190h
//     pinsrw xmm6, r10, 3
// 00007FF6AEFB13A6  pinsrw      xmm6,r10d,3

//     cmpsd xmm7, xmm0, 6
// 00007FF6AEFB13AC  cmpnlesd    xmm7,xmm0

//     mulsd xmm6, xmm1
// 00007FF6AEFB13B1  mulsd       xmm6,xmm1
//     andpd xmm7, xmm6
// 00007FF6AEFB13B5  andpd       xmm7,xmm6
//     addsd xmm0, xmm7
// 00007FF6AEFB13B9  addsd       xmm0,xmm7

//     pextrw r9d, xmm0, 3
// 00007FF6AEFB13BD  pextrw      r9d,xmm0,3
//     movq xmm2, xmm0
// 00007FF6AEFB13C3  movq        xmm2,xmm0
//     and r9d, 7ff0h
// 00007FF6AEFB13C7  and         r9d,7FF0h

//     cmp r9d, ecx
// 00007FF6AEFB13CE  cmp         r9d,ecx
//     ja OF_LOOP
// 00007FF6AEFB13D1  ja          fmod+29Eh (07FF6AEFB136Eh)


//     mulsd xmm1, QWORD PTR [YSCALE_DOWN1]
// 00007FF6AEFB13D3  mulsd       xmm1,mmword ptr [YSCALE_DOWN1 (07FF6AEFB52A0h)]
//     mulsd xmm5, QWORD PTR [YSCALE_DOWN1]
// 00007FF6AEFB13DB  mulsd       xmm5,mmword ptr [YSCALE_DOWN1 (07FF6AEFB52A0h)]
//     mulsd xmm4, QWORD PTR [YSCALE_DOWN1]
// 00007FF6AEFB13E3  mulsd       xmm4,mmword ptr [YSCALE_DOWN1 (07FF6AEFB52A0h)]
//     mulsd xmm1, QWORD PTR [YSCALE_DOWN2]
// 00007FF6AEFB13EB  mulsd       xmm1,mmword ptr [YSCALE_DOWN2 (07FF6AEFB52A8h)]
//     mulsd xmm5, QWORD PTR [YSCALE_DOWN2]
// 00007FF6AEFB13F3  mulsd       xmm5,mmword ptr [YSCALE_DOWN2 (07FF6AEFB52A8h)]
//     mulsd xmm4, QWORD PTR [YSCALE_DOWN2]
// 00007FF6AEFB13FB  mulsd       xmm4,mmword ptr [YSCALE_DOWN2 (07FF6AEFB52A8h)]

//     jmp BACK_LOOP
// 00007FF6AEFB1403  jmp         fmod+1A1h (07FF6AEFB1271h)

//     ; copy x
//     movq xmm2, xmm0
// 00007FF6AEFB1408  movq        xmm2,xmm0

//     ; 52 - e_q
//     mov rdx, 52
// 00007FF6AEFB140C  mov         rdx,34h
//     sub rdx, rcx
// 00007FF6AEFB1413  sub         rdx,rcx
//     ; return 0 if e_q>52
//     jle RET_ZERO_OR_X_INF_NAN
// 00007FF6AEFB1416  jle         fmod+49Eh (07FF6AEFB156Eh)

//     ; transfer 52-e_q to XMM
//     movd xmm3, rdx
// 00007FF6AEFB141C  movq        xmm3,rdx

//     ; trunc(x/y)*y
//     psrlq xmm2, xmm3
// 00007FF6AEFB1421  psrlq       xmm2,xmm3
//     psllq xmm2, xmm3
// 00007FF6AEFB1425  psllq       xmm2,xmm3

//     ; x - trunc(x/y)*y
//     subsd xmm0, xmm2
// 00007FF6AEFB1429  subsd       xmm0,xmm2

//     mov r9, 8000000000000000h
// 00007FF6AEFB142D  mov         r9,8000000000000000h
//     and rax, r9
// 00007FF6AEFB1437  and         rax,r9
//     movd xmm3, rax
// 00007FF6AEFB143A  movq        xmm3,rax
//     orpd xmm0, xmm3
// 00007FF6AEFB143F  orpd        xmm0,xmm3

//     { jmp END_ASM }
// 00007FF6AEFB1443  jmp         fmod+4D0h (07FF6AEFB15A0h)

//     ; y is 0?
//     ; cmp r10, 0
//     ; jl Y_ZERO
//     ; changed to follow DAZ
//     ucomisd xmm1, QWORD PTR [SZERO]
// 00007FF6AEFB1448  ucomisd     xmm1,mmword ptr [SZERO (07FF6AEFB52B0h)]
//     jz Y_ZERO
// 00007FF6AEFB1450  je          fmod+45Ch (07FF6AEFB152Ch)

//     ; y denormal or very small; get exponent of y
//     pextrw ecx, xmm1, 3
// 00007FF6AEFB1456  pextrw      ecx,xmm1,3
//     and ecx, 7ff0h
// 00007FF6AEFB145B  and         ecx,7FF0h
//     ; is x denormal as well?
//     pextrw eax, xmm0, 3
// 00007FF6AEFB1461  pextrw      eax,xmm0,3
//     and eax, 7ff0h
// 00007FF6AEFB1466  and         eax,7FF0h
//     cmp eax, ecx ; 1
// 00007FF6AEFB146B  cmp         eax,ecx
//     jg Y_DENORM_CALL
// 00007FF6AEFB146D  jg          fmod+413h (07FF6AEFB14E3h)

//     ; x is denormal or biased exponent is <= expon_y; 1

//     ; save sign
//     movsd xmm5, QWORD PTR [SZERO]
// 00007FF6AEFB146F  movsd       xmm5,mmword ptr [SZERO (07FF6AEFB52B0h)]
//     andpd xmm5, xmm0
// 00007FF6AEFB1477  andpd       xmm5,xmm0
//     ; |x|
//     movq xmm2, xmm0
// 00007FF6AEFB147B  movq        xmm2,xmm0
//     xorpd xmm2, xmm5
// 00007FF6AEFB147F  xorpd       xmm2,xmm5

//     ; scale up x, y
//     mulsd xmm2, QWORD PTR [SCALE_UP]
// 00007FF6AEFB1483  mulsd       xmm2,mmword ptr [SCALE_UP (07FF6AEFB5280h)]
//     mulsd xmm1, QWORD PTR [SCALE_UP]
// 00007FF6AEFB148B  mulsd       xmm1,mmword ptr [SCALE_UP (07FF6AEFB5280h)]
//     movq xmm3, xmm2
// 00007FF6AEFB1493  movq        xmm3,xmm2

//     ; x/y
//     divsd xmm2, xmm1
// 00007FF6AEFB1497  divsd       xmm2,xmm1

//     ; get exponent of x/y
//     pextrw eax, xmm2, 3
// 00007FF6AEFB149B  pextrw      eax,xmm2,3
//     and eax, 7ff0h
// 00007FF6AEFB14A0  and         eax,7FF0h
//     shr eax, 4
// 00007FF6AEFB14A5  shr         eax,4
//     sub eax, 3ffh
// 00007FF6AEFB14A8  sub         eax,3FFh
//     ; exponent < 0 ?
//     jl FMOD_RETURN
// 00007FF6AEFB14AD  jl          fmod+4B9h (07FF6AEFB1589h)

//     ; xmm0 = xmm3 = x*2^64
//     ; movq xmm0, xmm3

//     ; 52 - e_q
//     mov ecx, 52
// 00007FF6AEFB14B3  mov         ecx,34h
//     sub ecx, eax
// 00007FF6AEFB14B8  sub         ecx,eax
//     movd xmm4, ecx
// 00007FF6AEFB14BA  movd        xmm4,ecx

//     ; int(x/y)
//     psrlq xmm2, xmm4
// 00007FF6AEFB14BE  psrlq       xmm2,xmm4
//     psllq xmm2, xmm4
// 00007FF6AEFB14C2  psllq       xmm2,xmm4

//     ; prepare scale with proper sign
//     movq xmm0, QWORD PTR [SCALE_DOWN]
// 00007FF6AEFB14C6  movq        xmm0,mmword ptr [SCALE_DOWN (07FF6AEFB5288h)]
//     xorpd xmm0, xmm5
// 00007FF6AEFB14CE  xorpd       xmm0,xmm5

//     ; 2^64*(x-trunc(x/y)*y)
//     mulsd xmm2, xmm1
// 00007FF6AEFB14D2  mulsd       xmm2,xmm1
//     subsd xmm3, xmm2
// 00007FF6AEFB14D6  subsd       xmm3,xmm2
//     mulsd xmm0, xmm3
// 00007FF6AEFB14DA  mulsd       xmm0,xmm3

//     { jmp END_ASM }
// 00007FF6AEFB14DE  jmp         fmod+4D0h (07FF6AEFB15A0h)


//     neg r11
// 00007FF6AEFB14E3  neg         r11


//     mov ieq, r11d
// 00007FF6AEFB14E6  mov         dword ptr [ieq],r11d
//     movq x1, xmm0
// 00007FF6AEFB14EB  movq        mmword ptr [x1],xmm0
//     movq y1, xmm1
// 00007FF6AEFB14F1  movq        mmword ptr [y1],xmm1


//     mov edx, 2
// 00007FF6AEFB14F7  mov         edx,2
//     jmp END_ASM_BLOCK
// 00007FF6AEFB14FC  jmp         fmod+4D5h (07FF6AEFB15A5h)


//     pextrw r9d, xmm0, 3
// 00007FF6AEFB1501  pextrw      r9d,xmm0,3
//     and r9d, 7ff0h
// 00007FF6AEFB1507  and         r9d,7FF0h
//     cmp r9d, 7ff0h
// 00007FF6AEFB150E  cmp         r9d,7FF0h
//     jz X_INF_NAN
// 00007FF6AEFB1515  je          fmod+468h (07FF6AEFB1538h)


//     cmp edx, 0
// 00007FF6AEFB1517  cmp         edx,0
//     jl Y_DENORM
// 00007FF6AEFB151A  jl          fmod+378h (07FF6AEFB1448h)


//     cmp r10, 0
// 00007FF6AEFB1520  cmp         r10,0
//     ; return x for y=+/-Inf
//     jl FMOD_RETURN
// 00007FF6AEFB1524  jl          fmod+4B9h (07FF6AEFB1589h)
//     ; y is NaN
//     addsd xmm0, xmm1
// 00007FF6AEFB1526  addsd       xmm0,xmm1
//     { jmp END_ASM }
// 00007FF6AEFB152A  jmp         fmod+4D0h (07FF6AEFB15A0h)

//     jp Y_NAN
// 00007FF6AEFB152C  jp          fmod+456h (07FF6AEFB1526h)
//     movq xmm0, xmm1
// 00007FF6AEFB152E  movq        xmm0,xmm1
//     divsd xmm0, xmm1
// 00007FF6AEFB1532  divsd       xmm0,xmm1

//     jmp Error_fmod
// 00007FF6AEFB1536  jmp         fmod+4C3h (07FF6AEFB1593h)


//     ; x is Inf?
//     mov rax, QWORD PTR [x]
// 00007FF6AEFB1538  mov         rax,qword ptr [x]
//     shl rax, 12
// 00007FF6AEFB153D  shl         rax,0Ch
//     cmp rax, 0
// 00007FF6AEFB1541  cmp         rax,0
//     jnz X_NAN
// 00007FF6AEFB1545  jne         fmod+498h (07FF6AEFB1568h)

//     ; y is 0?
//     ;movsxd rdx, edx
//     ;and r10, rdx
//     ;cmp r10, 0
//     ;jl Y_ZERO
//     ucomisd xmm1, QWORD PTR [SZERO]
// 00007FF6AEFB1547  ucomisd     xmm1,mmword ptr [SZERO (07FF6AEFB52B0h)]
//     jz Y_ZERO
// 00007FF6AEFB154F  je          fmod+45Ch (07FF6AEFB152Ch)

//     ; y is NaN ?
//     mov rax, QWORD PTR [y]
// 00007FF6AEFB1551  mov         rax,qword ptr [y]
//     mov rcx, 0ffe0000000000000h
// 00007FF6AEFB1556  mov         rcx,0FFE0000000000000h
//     shl rax, 1
// 00007FF6AEFB1560  shl         rax,1
//     cmp rax, rcx
// 00007FF6AEFB1563  cmp         rax,rcx
//     ja Y_NAN
// 00007FF6AEFB1566  ja          fmod+456h (07FF6AEFB1526h)
//     ; set Invalid, return NaN if x is +/-Inf

//     ; x is NaN
//     subsd xmm0, xmm0
// 00007FF6AEFB1568  subsd       xmm0,xmm0
//     { jmp END_ASM }
// 00007FF6AEFB156C  jmp         fmod+4D0h (07FF6AEFB15A0h)


//     pextrw eax, xmm0, 3
// 00007FF6AEFB156E  pextrw      eax,xmm0,3
//     and eax, 7fffh
// 00007FF6AEFB1573  and         eax,7FFFh
//     cmp eax, 7ff0h
// 00007FF6AEFB1578  cmp         eax,7FF0h
//     jz X_INF_NAN
// 00007FF6AEFB157D  je          fmod+468h (07FF6AEFB1538h)

//      ; xorpd xmm0, xmm0
//      ; preserve sign of x
//      psrlq xmm0, 63
// 00007FF6AEFB157F  psrlq       xmm0,3Fh
//      psllq xmm0, 63
// 00007FF6AEFB1584  psllq       xmm0,3Fh
//     ; to ensure DAZ is followed
//     mulsd xmm0, QWORD PTR [ONE]
// 00007FF6AEFB1589  mulsd       xmm0,mmword ptr [ONE (07FF6AEFB52B8h)]
//     { jmp END_ASM }
// 00007FF6AEFB1591  jmp         fmod+4D0h (07FF6AEFB15A0h)
//     movq result, xmm0
// 00007FF6AEFB1593  movq        mmword ptr [result],xmm0
//     mov edx, 1
// 00007FF6AEFB1599  mov         edx,1
//     jmp END_ASM_BLOCK
// 00007FF6AEFB159E  jmp         fmod+4D5h (07FF6AEFB15A5h)
//     mov edx, 0
// 00007FF6AEFB15A0  mov         edx,0
//     movsd result, xmm0
// 00007FF6AEFB15A5  movsd       mmword ptr [result],xmm0

//     mov path_indicator, edx
// 00007FF6AEFB15AB  mov         dword ptr [path_indicator],edx
//     }

//     if (!path_indicator)
// 00007FF6AEFB15AF  mov         eax,dword ptr [path_indicator]
// 00007FF6AEFB15B3  movups      xmm6,xmmword ptr [rsp+40h]
// 00007FF6AEFB15B8  movups      xmm7,xmmword ptr [rsp+30h]
// 00007FF6AEFB15BD  test        eax,eax
// 00007FF6AEFB15BF  jne         fmod+4F9h (07FF6AEFB15C9h)
//     {

//         __asm { movsd xmm0, result }
// 00007FF6AEFB15C1  movsd       xmm0,mmword ptr [result]
// 00007FF6AEFB15C7  jmp         EPILOG (07FF6AEFB163Ah)
//         { goto EPILOG; };
//     }
//     else if (path_indicator > 1)
// 00007FF6AEFB15C9  cmp         eax,1
// 00007FF6AEFB15CC  jbe         EPILOG (07FF6AEFB163Ah)
//     {

//         if (ieq >= (0x400 - 25))
// 00007FF6AEFB15CE  cmp         dword ptr [ieq],3E7h
// 00007FF6AEFB15D6  jb          fmod+523h (07FF6AEFB15F3h)
//         {
// Y_DENORM_CALL_OF:

//             y1 *= YSCALE_UP.d;
//             result = fmod (x1, y1);
// 00007FF6AEFB15D8  movsd       xmm1,mmword ptr [y1]
// 00007FF6AEFB15DE  mulsd       xmm1,mmword ptr [YSCALE_UP (07FF6AEFB5278h)]
// 00007FF6AEFB15E6  movsd       xmm0,mmword ptr [x1]
// 00007FF6AEFB15EC  call        fmod (07FF6AEFB10D0h)
// 00007FF6AEFB15F1  jmp         fmod+529h (07FF6AEFB15F9h)




//         }
// Y_DENORM_CALL2:
//         x1 = result * SCALE_UP.d;
// 00007FF6AEFB15F3  movsd       xmm0,mmword ptr [result]
// 00007FF6AEFB15F9  movsd       xmm2,mmword ptr [SCALE_UP (07FF6AEFB5280h)]
//         y1 = y * SCALE_UP.d;
// 00007FF6AEFB1601  movsd       xmm1,mmword ptr [y]




//         }
// Y_DENORM_CALL2:
//         x1 = result * SCALE_UP.d;
// 00007FF6AEFB1607  mulsd       xmm0,xmm2
//         y1 = y * SCALE_UP.d;
// 00007FF6AEFB160B  mulsd       xmm1,xmm2




//         }
// Y_DENORM_CALL2:
//         x1 = result * SCALE_UP.d;
// 00007FF6AEFB160F  movsd       mmword ptr [x1],xmm0
//         y1 = y * SCALE_UP.d;
// 00007FF6AEFB1615  movsd       mmword ptr [y1],xmm1
//         result = fmod (x1, y1);
// 00007FF6AEFB161B  call        fmod (07FF6AEFB10D0h)
// 00007FF6AEFB1620  movsd       mmword ptr [result],xmm0




//         __asm {
//         movsd xmm0, result
// 00007FF6AEFB1626  movsd       xmm0,mmword ptr [result]
//         mulsd xmm0, QWORD PTR [SCALE_DOWN]
// 00007FF6AEFB162C  mulsd       xmm0,mmword ptr [SCALE_DOWN (07FF6AEFB5288h)]
//         movsd result, xmm0
// 00007FF6AEFB1634  movsd       mmword ptr [result],xmm0
//         }
//         { goto EPILOG; }
//     }
//     else
//     {

//        // Set errno here, if desired

//     }
//     EPILOG: return result;
// 00007FF6AEFB163A  movsd       xmm0,mmword ptr [result]
// 00007FF6AEFB1640  add         rsp,68h
// 00007FF6AEFB1644  ret

  }


  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

#undef __
