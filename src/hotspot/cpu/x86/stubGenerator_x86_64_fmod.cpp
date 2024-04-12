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
#include "runtime/stubRoutines.hpp"

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
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "libmFmod");
  address start = __ pc();
  __ enter(); // required for proper stackwalking of RuntimeStub frame

  if (VM_Version::supports_avx512vlbwdq()) {     // AVX512 version

    // Source used to generate the AVX512 fmod assembly below:
    //
    // #include <ia32intrin.h>
    // #include <emmintrin.h>
    // #pragma float_control(precise, on)
    //
    // #define UINT32 unsigned int
    // #define SINT32 int
    // #define UINT64 unsigned __int64
    // #define SINT64 __int64
    //
    // #define DP_FMA(a, b, c)    __fence(_mm_cvtsd_f64(_mm_fmadd_sd(_mm_set_sd(a), _mm_set_sd(b), _mm_set_sd(c))))
    // #define DP_FMA_RN(a, b, c)    _mm_cvtsd_f64(_mm_fmadd_round_sd(_mm_set_sd(a), _mm_set_sd(b), _mm_set_sd(c), (_MM_FROUND_TO_NEAREST_INT | _MM_FROUND_NO_EXC)))
    // #define DP_FMA_RZ(a, b, c) __fence(_mm_cvtsd_f64(_mm_fmadd_round_sd(_mm_set_sd(a), _mm_set_sd(b), _mm_set_sd(c), (_MM_FROUND_TO_ZERO | _MM_FROUND_NO_EXC))))
    //
    // #define DP_ROUND_RZ(a)   _mm_cvtsd_f64(_mm_roundscale_sd(_mm_setzero_pd(), _mm_set_sd(a), (_MM_FROUND_TO_ZERO | _MM_FROUND_NO_EXC)))
    //
    // #define DP_CONST(C)    _castu64_f64(0x##C##ull)
    // #define DP_AND(X, Y)   _mm_cvtsd_f64(_mm_and_pd(_mm_set_sd(X), _mm_set_sd(Y)))
    // #define DP_XOR(X, Y)   _mm_cvtsd_f64(_mm_xor_pd(_mm_set_sd(X), _mm_set_sd(Y)))
    // #define DP_OR(X, Y)    _mm_cvtsd_f64(_mm_or_pd(_mm_set_sd(X), _mm_set_sd(Y)))
    // #define DP_DIV_RZ(a, b) __fence(_mm_cvtsd_f64(_mm_div_round_sd(_mm_set_sd(a), _mm_set_sd(b), (_MM_FROUND_TO_ZERO | _MM_FROUND_NO_EXC))))
    // #define DP_FNMA(a, b, c)    __fence(_mm_cvtsd_f64(_mm_fnmadd_sd(_mm_set_sd(a), _mm_set_sd(b), _mm_set_sd(c))))
    // #define DP_FNMA_RZ(a, b, c) __fence(_mm_cvtsd_f64(_mm_fnmadd_round_sd(_mm_set_sd(a), _mm_set_sd(b), _mm_set_sd(c), (_MM_FROUND_TO_ZERO | _MM_FROUND_NO_EXC))))
    //
    // #define D2L(x)  _mm_castpd_si128(x)
    // // transfer highest 32 bits (of low 64b) to GPR
    // #define TRANSFER_HIGH_INT32(X)   _mm_extract_epi32(D2L(_mm_set_sd(X)), 1)
    //
    // double fmod(double x, double y)
    // {
    // double a, b, sgn_a, q, bs, bs2;
    // unsigned eq;

    Label L_5280, L_52a0, L_5256, L_5300, L_5320, L_52c0, L_52d0, L_5360, L_5380, L_53b0, L_5390;
    Label L_53c0, L_52a6, L_53d0, L_exit;

    __ movdqa(xmm2, xmm0);
    //     // |x|, |y|
    //     a = DP_AND(x, DP_CONST(7fffffffffffffff));
    __ movq(xmm0, xmm0);
    __ mov64(rax, 0x7FFFFFFFFFFFFFFFULL);
    __ evpbroadcastq(xmm3, rax, Assembler::AVX_128bit);
    __ vpand(xmm6, xmm0, xmm3, Assembler::AVX_128bit);
    //     b = DP_AND(y, DP_CONST(7fffffffffffffff));
    __ vpand(xmm4, xmm1, xmm3, Assembler::AVX_128bit);
    //     // sign(x)
    //     sgn_a = DP_XOR(x, a);
    __ vpxor(xmm3, xmm6, xmm0, Assembler::AVX_128bit);
    //     q = DP_DIV_RZ(a, b);
    __ movq(xmm5, xmm4);
    __ evdivsd(xmm0, xmm6, xmm5, Assembler::EVEX_RZ);
    //     q = DP_ROUND_RZ(q);
    __ movq(xmm0, xmm0);
    //     a = DP_AND(x, DP_CONST(7fffffffffffffff));
    __ vxorpd(xmm7, xmm7, xmm7, Assembler::AVX_128bit);
    //     q = DP_ROUND_RZ(q);
    __ vroundsd(xmm0, xmm7, xmm0, 0xb);
    //     eq = TRANSFER_HIGH_INT32(q);
    __ extractps(rax, xmm0, 1);
    //     if (!eq)  return x + sgn_a;
    __ testl(rax, rax);
    __ jcc(Assembler::equal, L_5280);
    //     if (eq >= 0x7fefffffu) goto SPECIAL_FMOD;
    __ cmpl(rax, 0x7feffffe);
    __ jcc(Assembler::belowEqual, L_52a0);
    __ vpxor(xmm2, xmm2, xmm2, Assembler::AVX_128bit);
    // SPECIAL_FMOD:
    //
    //     // y==0 or x==Inf?
    //     if ((b == 0.0) || (!(a <= DP_CONST(7fefffffffffffff))))
    __ ucomisd(xmm4, xmm2);
    __ jcc(Assembler::notEqual, L_5256);
    __ jcc(Assembler::noParity, L_5300);
    __ bind(L_5256);
    __ movsd(xmm2, ExternalAddress((address)CONST_MAX), rax);
    __ ucomisd(xmm2, xmm6);
    __ jcc(Assembler::below, L_5300);
    __ movsd(xmm0, ExternalAddress((address)CONST_INF), rax);
    //         return DP_FNMA(b, q, a);    // NaN
    //     // y is NaN?
    //     if (!(b <= DP_CONST(7ff0000000000000))) return y + y;
    __ ucomisd(xmm0, xmm4);
    __ jcc(Assembler::aboveEqual, L_5320);
    __ vaddsd(xmm0, xmm1, xmm1);
    __ jmp(L_exit);
    //     if (!eq)  return x + sgn_a;
    __ align32();
    __ bind(L_5280);
    __ vaddsd(xmm0, xmm3, xmm2);
    __ jmp(L_exit);
    //     a = DP_FNMA_RZ(b, q, a);
    __ align(8);
    __ bind(L_52a0);
    __ evfnmadd213sd(xmm0, xmm4, xmm6, Assembler::EVEX_RZ);
    //     while (b <= a)
    __ bind(L_52a6);
    __ ucomisd(xmm0, xmm4);
    __ jcc(Assembler::aboveEqual, L_52c0);
    //     a = DP_XOR(a, sgn_a);
    __ vpxor(xmm0, xmm3, xmm0, Assembler::AVX_128bit);
    __ jmp(L_exit);
    __ bind(L_52c0);
    __ movq(xmm6, xmm0);
    //         q = DP_ROUND_RZ(q);
    __ vpxor(xmm1, xmm1, xmm1, Assembler::AVX_128bit);
    __ align32();
    __ bind(L_52d0);
    //         q = DP_DIV_RZ(a, b);
    __ evdivsd(xmm2, xmm6, xmm5, Assembler::EVEX_RZ);
    //         q = DP_ROUND_RZ(q);
    __ movq(xmm2, xmm2);
    __ vroundsd(xmm2, xmm1, xmm2, 0xb);
    //     a = DP_FNMA_RZ(b, q, a);
    __ evfnmadd213sd(xmm2, xmm4, xmm0, Assembler::EVEX_RZ);
    //     while (b <= a)
    __ ucomisd(xmm2, xmm4);
    __ movq(xmm6, xmm2);
    __ movapd(xmm0, xmm2);
    __ jcc(Assembler::aboveEqual, L_52d0);
    //     a = DP_XOR(a, sgn_a);
    __ vpxor(xmm0, xmm3, xmm2, Assembler::AVX_128bit);
    __ jmp(L_exit);
    //         return DP_FNMA(b, q, a);    // NaN
    __ bind(L_5300);
    __ vfnmadd213sd(xmm0, xmm4, xmm6);
    __ jmp(L_exit);
    //     bs = b * DP_CONST(7fe0000000000000);
    __ bind(L_5320);
    __ vmulsd(xmm1, xmm4, ExternalAddress((address)CONST_e307), rax);
    //     q = DP_DIV_RZ(a, bs);
    __ movq(xmm2, xmm1);
    __ evdivsd(xmm0, xmm6, xmm2, Assembler::EVEX_RZ);
    //     q = DP_ROUND_RZ(q);
    __ movq(xmm0, xmm0);
    __ vroundsd(xmm7, xmm7, xmm0, 0xb);
    //     eq = TRANSFER_HIGH_INT32(q);
    __ extractps(rax, xmm7, 1);
    //     if (eq >= 0x7fefffffu)
    __ cmpl(rax, 0x7fefffff);
    __ jcc(Assembler::below, L_5360);
    //         // b* 2*1023 * 2^1023
    //         bs2 = bs * DP_CONST(7fe0000000000000);
    __ vmulsd(xmm0, xmm1, ExternalAddress((address)CONST_e307), rax);
    //         while (bs2 <= a)
    __ ucomisd(xmm6, xmm0);
    __ jcc(Assembler::aboveEqual, L_5380);
    __ movapd(xmm7, xmm6);
    __ jmp(L_53b0);
    //         a = DP_FNMA_RZ(b, q, a);
    __ bind(L_5360);
    __ evfnmadd213sd(xmm7, xmm1, xmm6, Assembler::EVEX_RZ);
    __ jmp(L_53b0);
    //             q = DP_ROUND_RZ(q);
    __ bind(L_5380);
    __ vxorpd(xmm8, xmm8, xmm8, Assembler::AVX_128bit);
    //             q = DP_DIV_RZ(qa, bs2);
    __ align32();
    __ bind(L_5390);
    __ evdivsd(xmm7, xmm6, xmm0, Assembler::EVEX_RZ);
    //             q = DP_ROUND_RZ(q);
    __ movq(xmm7, xmm7);
    __ vroundsd(xmm7, xmm8, xmm7, 0xb);
    //             a = DP_FNMA_RZ(bs2, q, a);
    __ evfnmadd213sd(xmm7, xmm0, xmm6, Assembler::EVEX_RZ);
    //         while (bs2 <= a)
    __ ucomisd(xmm7, xmm0);
    __ movapd(xmm6, xmm7);
    __ jcc(Assembler::aboveEqual, L_5390);
    //     while (bs <= a)
    __ bind(L_53b0);
    __ ucomisd(xmm7, xmm1);
    __ jcc(Assembler::aboveEqual, L_53c0);
    __ movapd(xmm0, xmm7);
    __ jmp(L_52a6);
    //         q = DP_ROUND_RZ(q);
    __ bind(L_53c0);
    __ vxorpd(xmm6, xmm6, xmm6, Assembler::AVX_128bit);
    //         q = DP_DIV_RZ(a, bs);
    __ align32();
    __ bind(L_53d0);
    __ evdivsd(xmm0, xmm7, xmm2, Assembler::EVEX_RZ);
    //         q = DP_ROUND_RZ(q);
    __ movq(xmm0, xmm0);
    __ vroundsd(xmm0, xmm6, xmm0, 0xb);
    //         a = DP_FNMA_RZ(bs, q, a);
    __ evfnmadd213sd(xmm0, xmm1, xmm7, Assembler::EVEX_RZ);
    //     while (bs <= a)
    __ ucomisd(xmm0, xmm1);
    __ movapd(xmm7, xmm0);
    __ jcc(Assembler::aboveEqual, L_53d0);
    __ jmp(L_52a6);

    __ bind(L_exit);

////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
//                         AVX2 code
////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////
  } else if (VM_Version::supports_fma()) {       // AVX2 version

    Label L_104a, L_11bd, L_10c1, L_1090, L_11b9, L_10e7, L_11af, L_111c, L_10f3, L_116e, L_112a;
    Label L_1173, L_1157, L_117f, L_11a0;

    //   double fmod(double x, double y)
    // {
    // double a, b, sgn_a, q, bs, bs2, corr, res;
    // unsigned eq;

    //     // |x|, |y|
    //     a = DP_AND(x, DP_CONST(7fffffffffffffff));
    __ movq(xmm2, xmm0);
    __ movdqu(xmm3, ExternalAddress((address)CONST_NaN), rcx);
    __ vpand(xmm4, xmm2, xmm3, Assembler::AVX_128bit);
    //     b = DP_AND(y, DP_CONST(7fffffffffffffff));
    __ vpand(xmm3, xmm1, xmm3, Assembler::AVX_128bit);
    //   // sign(x)
    //   sgn_a = DP_XOR(x, a);
    __ mov64(rcx, 0x8000000000000000ULL);
    __ movq(xmm5, rcx);
    __ vpand(xmm2, xmm2, xmm5, Assembler::AVX_128bit);

    //   if (a < b)  return x + sgn_a;
    __ ucomisd(xmm3, xmm4);
    __ jccb(Assembler::belowEqual, L_104a);
    __ vaddsd(xmm0, xmm2, xmm0);
    __ jmp(L_11bd);

    //   if (a < b * 0x1p+260)
    __ bind(L_104a);

    __ vmulsd(xmm0, xmm3, ExternalAddress((address)CONST_1p260), rax);
    __ ucomisd(xmm0, xmm4);
    __ jccb(Assembler::belowEqual, L_10c1);
    //   {
    //     q = DP_DIV(a, b);
    __ vdivpd(xmm0, xmm4, xmm3, Assembler::AVX_128bit);
    //     corr = DP_SHR(DP_FNMA(b, q, a), 63);
    __ movapd(xmm1, xmm0);
    __ vfnmadd213sd(xmm1, xmm3, xmm4);
    __ movq(xmm5, xmm1);
    __ vpxor(xmm1, xmm1, xmm1, Assembler::AVX_128bit);
    __ vpcmpgtq(xmm5, xmm1, xmm5, Assembler::AVX_128bit);
    //     q = DP_PSUBQ(q, corr);
    __ vpaddq(xmm0, xmm5, xmm0, Assembler::AVX_128bit);
    //     q = DP_TRUNC(q);
    __ vroundsd(xmm0, xmm0, xmm0, 3);
    //     a = DP_FNMA(b, q, a);
    __ vfnmadd213sd(xmm0, xmm3, xmm4);
    __ align(16);
    //     while (b <= a)
    __ bind(L_1090);
    __ ucomisd(xmm0, xmm3);
    __ jcc(Assembler::below, L_11b9);
    //     {
    //       q = DP_DIV(a, b);
    __ vdivsd(xmm4, xmm0, xmm3);
    //       corr = DP_SHR(DP_FNMA(b, q, a), 63);
    __ movapd(xmm5, xmm4);
    __ vfnmadd213sd(xmm5, xmm3, xmm0);
    __ movq(xmm5, xmm5);
    __ vpcmpgtq(xmm5, xmm1, xmm5, Assembler::AVX_128bit);
    //       q = DP_PSUBQ(q, corr);
    __ vpaddq(xmm4, xmm5, xmm4, Assembler::AVX_128bit);
    //       q = DP_TRUNC(q);
    __ vroundsd(xmm4, xmm4, xmm4, 3);
    //       a = DP_FNMA(b, q, a);
    __ vfnmadd231sd(xmm0, xmm3, xmm4);
    __ jmpb(L_1090);
    //     }
    //     return DP_XOR(a, sgn_a);
    //   }

    //   __asm { ldmxcsr DWORD PTR [mxcsr_rz] }
    __ bind(L_10c1);
    __ ldmxcsr(ExternalAddress(StubRoutines::x86::addr_mxcsr_rz()), rax /*rscratch*/);

    //   q = DP_DIV(a, b);
    __ vdivpd(xmm0, xmm4, xmm3, Assembler::AVX_128bit);
    //   q = DP_TRUNC(q);
    __ vroundsd(xmm0, xmm0, xmm0, 3);

    //   eq = TRANSFER_HIGH_INT32(q);
    __ extractps(rax, xmm0, 1);

    //   if (__builtin_expect((eq >= 0x7fefffffu), (0==1))) goto SPECIAL_FMOD;
    __ cmpl(rax, 0x7feffffe);
    __ jccb(Assembler::above, L_10e7);

    //   a = DP_FNMA(b, q, a);
    __ vfnmadd213sd(xmm0, xmm3, xmm4);
    __ jmp(L_11af);

    // SPECIAL_FMOD:

    //   // y==0 or x==Inf?
    //   if ((b == 0.0) || (!(a <= DP_CONST(7fefffffffffffff))))
    __ bind(L_10e7);
    __ vpxor(xmm5, xmm5, xmm5, Assembler::AVX_128bit);
    __ ucomisd(xmm3, xmm5);
    __ jccb(Assembler::notEqual, L_10f3);
    __ jccb(Assembler::noParity, L_111c);

    __ bind(L_10f3);
    __ movsd(xmm5, ExternalAddress((address)CONST_MAX), rax);
    __ ucomisd(xmm5, xmm4);
    __ jccb(Assembler::below, L_111c);
    //     return res;
    //   }
    //   // y is NaN?
    //   if (!(b <= DP_CONST(7ff0000000000000))) {
    __ movsd(xmm0, ExternalAddress((address)CONST_INF), rax);
    __ ucomisd(xmm0, xmm3);
    __ jccb(Assembler::aboveEqual, L_112a);
    //     res = y + y;
    __ vaddsd(xmm0, xmm1, xmm1);
    //     __asm { ldmxcsr DWORD PTR[mxcsr] }
    __ ldmxcsr(ExternalAddress(StubRoutines::x86::addr_mxcsr_std()), rax /*rscratch*/);
    __ jmp(L_11bd);
    //   {
    //     res = DP_FNMA(b, q, a);    // NaN
    __ bind(L_111c);
    __ vfnmadd213sd(xmm0, xmm3, xmm4);
    //     __asm { ldmxcsr DWORD PTR[mxcsr] }
    __ ldmxcsr(ExternalAddress(StubRoutines::x86::addr_mxcsr_std()), rax /*rscratch*/);
    __ jmp(L_11bd);
    //     return res;
    //   }

    //   // b* 2*1023
    //   bs = b * DP_CONST(7fe0000000000000);
    __ bind(L_112a);
    __ vmulsd(xmm1, xmm3, ExternalAddress((address)CONST_e307), rax);

    //   q = DP_DIV(a, bs);
    __ vdivsd(xmm0, xmm4, xmm1);
    //   q = DP_TRUNC(q);
    __ vroundsd(xmm0, xmm0, xmm0, 3);

    //   eq = TRANSFER_HIGH_INT32(q);
    __ extractps(rax, xmm0, 1);

    //   if (eq >= 0x7fefffffu)
    __ cmpl(rax, 0x7fefffff);
    __ jccb(Assembler::below, L_116e);
    //   {
    //     // b* 2*1023 * 2^1023
    //     bs2 = bs * DP_CONST(7fe0000000000000);
    __ vmulsd(xmm0, xmm1, ExternalAddress((address)CONST_e307), rax);
    //     while (bs2 <= a)
    __ ucomisd(xmm4, xmm0);
    __ jccb(Assembler::below, L_1173);
    //     {
    //       q = DP_DIV(a, bs2);
    __ bind(L_1157);
    __ vdivsd(xmm5, xmm4, xmm0);
    //       q = DP_TRUNC(q);
    __ vroundsd(xmm5, xmm5, xmm5, 3);
    //       a = DP_FNMA(bs2, q, a);
    __ vfnmadd231sd(xmm4, xmm0, xmm5);
    //     while (bs2 <= a)
    __ ucomisd(xmm4, xmm0);
    __ jccb(Assembler::aboveEqual, L_1157);
    __ jmpb(L_1173);
    //     }
    //   }
    //   else
    //   a = DP_FNMA(bs, q, a);
    __ bind(L_116e);
    __ vfnmadd231sd(xmm4, xmm1, xmm0);

    //   while (bs <= a)
    __ bind(L_1173);
    __ ucomisd(xmm4, xmm1);
    __ jccb(Assembler::aboveEqual, L_117f);
    __ movapd(xmm0, xmm4);
    __ jmpb(L_11af);
    //   {
    //     q = DP_DIV(a, bs);
    __ bind(L_117f);
    __ vdivsd(xmm0, xmm4, xmm1);
    //     q = DP_TRUNC(q);
    __ vroundsd(xmm0, xmm0, xmm0, 3);
    //     a = DP_FNMA(bs, q, a);
    __ vfnmadd213sd(xmm0, xmm1, xmm4);

    //   while (bs <= a)
    __ ucomisd(xmm0, xmm1);
    __ movapd(xmm4, xmm0);
    __ jccb(Assembler::aboveEqual, L_117f);
    __ jmpb(L_11af);
    __ align(16);
    //   {
    //     q = DP_DIV(a, b);
    __ bind(L_11a0);
    __ vdivsd(xmm1, xmm0, xmm3);
    //     q = DP_TRUNC(q);
    __ vroundsd(xmm1, xmm1, xmm1, 3);
    //     a = DP_FNMA(b, q, a);
    __ vfnmadd231sd(xmm0, xmm3, xmm1);

    // FMOD_CONT:
    //   while (b <= a)
    __ bind(L_11af);
    __ ucomisd(xmm0, xmm3);
    __ jccb(Assembler::aboveEqual, L_11a0);
    //   }

    //   __asm { ldmxcsr DWORD PTR[mxcsr] }
    __ ldmxcsr(ExternalAddress(StubRoutines::x86::addr_mxcsr_std()), rax /*rscratch*/);
    __ bind(L_11b9);
    __ vpxor(xmm0, xmm2, xmm0, Assembler::AVX_128bit);
    //   }

    //   goto FMOD_CONT;

    // }
    __ bind(L_11bd);

  } else {                                       // SSE version
    Label x87_loop;
    __ movsd(Address(rbp, -8), xmm1);
    __ movsd(Address(rbp, -16), xmm0);
    __ fld_d(Address(rbp, -8));
    __ fld_d(Address(rbp, -16));

    __ bind(x87_loop);
    __ fprem();
    __ fnstsw_ax();
    __ testb(rax, 0x4, false);
    __ jcc(Assembler::notZero, x87_loop);

    __ fstp_d(1);
    __ fstp_d(Address(rbp, -8));
    __ movsd(xmm0, Address(rbp, -8));
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

#undef __
