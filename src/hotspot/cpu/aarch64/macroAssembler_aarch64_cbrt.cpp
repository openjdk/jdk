/*
 * Copyright (c) 2025, Intel Corporation. All rights reserved.
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

#include "macroAssembler_aarch64.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"

/******************************************************************************/
//                     ALGORITHM DESCRIPTION
//                     ---------------------
//
// x=2^{3*k+j} * 1.b1 b2 ... b5 b6 ... b52
// Let r=(x*2^{-3k-j} - 1.b1 b2 ... b5 1)* rcp[b1 b2 ..b5],
// where rcp[b1 b2 .. b5]=1/(1.b1 b2 b3 b4 b5 1) in double precision
// cbrt(2^j * 1. b1 b2 .. b5 1) is approximated as T[j][b1..b5]+D[j][b1..b5]
// (T stores the high 53 bits, D stores the low order bits)
// Result=2^k*T+(2^k*T*r)*P+2^k*D
// where P=p1+p2*r+..+p8*r^7
//
// Special cases:
//  cbrt(NaN) = quiet NaN, and raise invalid exception
//  cbrt(+/-INF) = +/-INF
//  cbrt(+/-0) = +/-0
//
/******************************************************************************/

ATTRIBUTE_ALIGNED(4) static const juint _SIG_MASK[] =   // 0xfc00000000000
{
    0, 1032192
};

ATTRIBUTE_ALIGNED(4) static const juint _EXP_MASK[] =   // Sign, exp, but not bias
{
    0, 3220176896
};

ATTRIBUTE_ALIGNED(4) static const juint _EXP_MSK2[] =   // WTAF? 0xbff0400000000000
{
    0, 3220193280
};

ATTRIBUTE_ALIGNED(4) static const juint _EXP_MSK3[] =   // Fraction part 0x000fffffffffffff (52 bits)
{
    4294967295, 1048575
};

ATTRIBUTE_ALIGNED(4) static const juint _SCALE63[] =    // WTAF? 100001111100000000000000000000000000000000000000000000000000000
{
    0, 1138753536
};

ATTRIBUTE_ALIGNED(4) static const juint _ZERON[] =      // Sign bit
{
    0, 2147483648
};

ATTRIBUTE_ALIGNED(4) static const juint _INF[] =
{
    0, 2146435072
};

ATTRIBUTE_ALIGNED(4) static const juint _NEG_INF[] =
{
    0, 4293918720
};

ATTRIBUTE_ALIGNED(16) static const juint _coeff_table[] =
{
    1553778919, 3213899486, 3534952507, 3215266280, 1646371399,
    3214412045, 477218588,  3216798151, 3582521621, 1066628362,
    1007461464, 1068473053, 889629714,  1067378449, 1431655765,
    1070945621
};

ATTRIBUTE_ALIGNED(4) static const juint _rcp_table[] =
{
    528611360,  3220144632, 2884679527, 3220082993, 1991868891, 3220024928,
    2298714891, 3219970134, 58835168,   3219918343, 3035110223, 3219869313,
    1617585086, 3219822831, 2500867033, 3219778702, 4241943008, 3219736752,
    258732970,  3219696825, 404232216,  3219658776, 2172167368, 3219622476,
    1544257904, 3219587808, 377579543,  3219554664, 1616385542, 3219522945,
    813783277,  3219492562, 3940743189, 3219463431, 2689777499, 3219435478,
    1700977147, 3219408632, 3169102082, 3219382828, 327235604,  3219358008,
    1244336319, 3219334115, 1300311200, 3219311099, 3095471925, 3219288912,
    2166487928, 3219267511, 2913108253, 3219246854, 293672978,  3219226904,
    288737297,  3219207624, 1810275472, 3219188981, 174592167,  3219170945,
    3539053052, 3219153485, 2164392968, 3219136576
};

ATTRIBUTE_ALIGNED(4) static const juint _cbrt_table[] =
{
    572345495,  1072698681, 1998204467, 1072709382, 3861501553, 1072719872,
    2268192434, 1072730162, 2981979308, 1072740260, 270859143,  1072750176,
    2958651392, 1072759916, 313113243,  1072769490, 919449400,  1072778903,
    2809328903, 1072788162, 2222981587, 1072797274, 2352530781, 1072806244,
    594152517,  1072815078, 1555767199, 1072823780, 4282421314, 1072832355,
    2355578597, 1072840809, 1162590619, 1072849145, 797864051,  1072857367,
    431273680,  1072865479, 2669831148, 1072873484, 733477752,  1072881387,
    4280220604, 1072889189, 801961634,  1072896896, 2915370760, 1072904508,
    1159613482, 1072912030, 2689944798, 1072919463, 1248687822, 1072926811,
    2967951030, 1072934075, 630170432,  1072941259, 3760898254, 1072948363,
    0,          1072955392, 2370273294, 1072962345, 1261754802, 1072972640,
    546334065,  1072986123, 1054893830, 1072999340, 1571187597, 1073012304,
    1107975175, 1073025027, 3606909377, 1073037519, 1113616747, 1073049792,
    4154744632, 1073061853, 3358931423, 1073073713, 4060702372, 1073085379,
    747576176,  1073096860, 3023138255, 1073108161, 1419988548, 1073119291,
    1914185305, 1073130255, 294389948,  1073141060, 3761802570, 1073151710,
    978281566,  1073162213, 823148820,  1073172572, 2420954441, 1073182792,
    3815449908, 1073192878, 2046058587, 1073202835, 1807524753, 1073212666,
    2628681401, 1073222375, 3225667357, 1073231966, 1555307421, 1073241443,
    3454043099, 1073250808, 1208137896, 1073260066, 3659916772, 1073269218,
    1886261264, 1073278269, 3593647839, 1073287220, 3086012205, 1073296075,
    2769796922, 1073304836, 888716057,  1073317807, 2201465623, 1073334794,
    164369365,  1073351447, 3462666733, 1073367780, 2773905457, 1073383810,
    1342879088, 1073399550, 2543933975, 1073415012, 1684477781, 1073430209,
    3532178543, 1073445151, 1147747300, 1073459850, 1928031793, 1073474314,
    2079717015, 1073488553, 4016765315, 1073502575, 3670431139, 1073516389,
    3549227225, 1073530002, 11637607,   1073543422, 588220169,  1073556654,
    2635407503, 1073569705, 2042029317, 1073582582, 1925128962, 1073595290,
    4136375664, 1073607834, 759964600,  1073620221, 4257606771, 1073632453,
    297278907,  1073644538, 3655053093, 1073656477, 2442253172, 1073668277,
    1111876799, 1073679941, 3330973139, 1073691472, 3438879452, 1073702875,
    3671565478, 1073714153, 1317849547, 1073725310, 1642364115, 1073736348
};

ATTRIBUTE_ALIGNED(4) static const juint _D_table[] =
{
    4050900474, 1014427190, 1157977860, 1016444461, 1374568199, 1017271387,
    2809163288, 1016882676, 3742377377, 1013168191, 3101606597, 1017541672,
    65224358,   1017217597, 2691591250, 1017266643, 4020758549, 1017689313,
    1316310992, 1018030788, 1031537856, 1014090882, 3261395239, 1016413641,
    886424999,  1016313335, 3114776834, 1014195875, 1681120620, 1017825416,
    1329600273, 1016625740, 465474623,  1017097119, 4251633980, 1017169077,
    1986990133, 1017710645, 752958613,  1017159641, 2216216792, 1018020163,
    4282860129, 1015924861, 1557627859, 1016039538, 3889219754, 1018086237,
    3684996408, 1017353275, 723532103,  1017717141, 2951149676, 1012528470,
    831890937,  1017830553, 1031212645, 1017387331, 2741737450, 1017604974,
    2863311531, 1003776682, 4276736099, 1013153088, 4111778382, 1015673686,
    1728065769, 1016413986, 2708718031, 1018078833, 1069335005, 1015291224,
    700037144,  1016482032, 2904566452, 1017226861, 4074156649, 1017622651,
    25019565,   1015245366, 3601952608, 1015771755, 3267129373, 1017904664,
    503203103,  1014921629, 2122011730, 1018027866, 3927295461, 1014189456,
    2790625147, 1016024251, 1330460186, 1016940346, 4033568463, 1015538390,
    3695818227, 1017509621, 257573361,  1017208868, 3227697852, 1017337964,
    234118548,  1017169577, 4009025803, 1017278524, 1948343394, 1017749310,
    678398162,  1018144239, 3083864863, 1016669086, 2415453452, 1017890370,
    175467344,  1017330033, 3197359580, 1010339928, 2071276951, 1015941358,
    268372543,  1016737773, 938132959,  1017389108, 1816750559, 1017337448,
    4119203749, 1017152174, 2578653878, 1013108497, 2470331096, 1014678606,
    123855735,  1016553320, 1265650889, 1014782687, 3414398172, 1017182638,
    1040773369, 1016158401, 3483628886, 1016886550, 4140499405, 1016191425,
    3893477850, 1016964495, 3935319771, 1009634717, 2978982660, 1015027112,
    2452709923, 1017990229, 3190365712, 1015835149, 4237588139, 1015832925,
    2610678389, 1017962711, 2127316774, 1017405770, 824267502,  1017959463,
    2165924042, 1017912225, 2774007076, 1013257418, 4123916326, 1017582284,
    1976417958, 1016959909, 4092806412, 1017711279, 119251817,  1015363631,
    3475418768, 1017675415, 1972580503, 1015470684, 815541017,  1017517969,
    2429917451, 1017397776, 4062888482, 1016749897, 68284153,   1017925678,
    2207779246, 1016320298, 1183466520, 1017408657, 143326427,  1017060403
};

#define __

void MacroAssembler::generate_libmCbrt() {
  Label L_2TAG_PACKET_0_0_1, L_2TAG_PACKET_1_0_1, L_2TAG_PACKET_2_0_1, L_2TAG_PACKET_3_0_1;
  Label L_2TAG_PACKET_4_0_1, L_2TAG_PACKET_5_0_1, L_2TAG_PACKET_6_0_1;
  Label B1_1, B1_2, B1_4;

  address SIG_MASK        = (address)_SIG_MASK;
  address EXP_MASK        = (address)_EXP_MASK;
  address EXP_MSK2        = (address)_EXP_MSK2;
  address EXP_MSK3        = (address)_EXP_MSK3;
  address SCALE63         = (address)_SCALE63;
  address ZERON           = (address)_ZERON;
  address INF             = (address)_INF;
  address NEG_INF         = (address)_NEG_INF;
  address coeff_table     = (address)_coeff_table;
  address rcp_table       = (address)_rcp_table;
  address cbrt_table      = (address)_cbrt_table;
  address D_table         = (address)_D_table;

  address start = pc();
  // enter(); // required for proper stackwalking of RuntimeStub frame

  assert(v0 == c_farg0, "must be");
  FloatRegister xmm0 = v0;
  FloatRegSet temps = FloatRegSet::range(v1, v7) + FloatRegSet::range(v16, v31);
  auto it = temps.begin();
  FloatRegister xmm1 = *it++, xmm2 = *it++,
    xmm3 = *it++, xmm4 = *it++, xmm5 = *it++,
    xmm6 = *it++, xmm7 = *it++, xmm_scratch = *it++;

  Register rdx = r0, rax = r1, rcx = r2;

  // __ bind(B1_1);
  // __ subq(rsp, 24);
  // __ movsd(Address(rsp), xmm0);
  __ bind(B1_1);
  __ sub(sp, sp, align_up(3 * wordSize, 16));
  __ strd(xmm0, Address(sp, 0));

  // __ bind(B1_2);
  // __ movq(xmm7, xmm0);
  // __ movl(rdx, 524032);
  // __ movsd(xmm5, ExternalAddress(EXP_MSK3), r11 /*rscratch*/);
  // __ movsd(xmm3, ExternalAddress(EXP_MSK2), r11 /*rscratch*/);

  __ bind(B1_2);
  __ fmovd(xmm7, xmm0);                                                   // xmm7_0 = c_farg0
  __ mov(rdx, 524032);                                                    // rdx_0 = 0x7ff00
  __ ldrd(xmm5,  ExternalAddress(EXP_MSK3), rscratch1);                   // xmm5_0 = EXP_MSK3
  __ ldrd(xmm3,  ExternalAddress(EXP_MSK2), rscratch1);                   // xmm3_0 = EXP_MSK2

  // __ psrlq(xmm7, 44);
  __ ushr(xmm7, T2D, xmm7, 44);                                           // xmm7_1 = xmm7_0 >> 44
                                                     // This is the sign, exponent, and top 8 bits of fraction
  // __ pextrw(rcx, xmm7, 0);
  __ fmovs(rcx, xmm7);                                                    // rcx_0 = (unsigned short)xmm7_1
  // __ andr(rcx, rcx, 0xffff); // Unnecessary
  // __ movdl(rax, xmm7);
  __ fmovd(rax, xmm7);                                                    // rax_0 = xmm7_1
  // __ movsd(xmm1, ExternalAddress(EXP_MASK), r11 /*rscratch*/);
  // __ movsd(xmm2, ExternalAddress(SIG_MASK), r11 /*rscratch*/);
  __ ldrd(xmm1, ExternalAddress(EXP_MASK), rscratch1);                    // xmm1_0 = EXP_MASK
  __ ldrd(xmm2, ExternalAddress(SIG_MASK), rscratch1);                    // xmm2_0 = SIG_MASK
  // __ andl(rcx, 248);
  __ andr(rcx, rcx, 248);                                                 // rcx_1 = rcx_0 & 0xf8
  // __ lea(r8, ExternalAddress(rcp_table));
  // __ movsd(xmm4, Address(r8, rcx, Address::times_1));
  __ lea(rscratch1, ExternalAddress(rcp_table));
  __ ldrd(xmm4, Address(rscratch1, rcx));                                 // xmm4_0 = rcp_table[rcx_1]
  // __ movq(r9, rax);
  // __ andl(rdx, rax);
  // __ cmpl(rdx, 0);
  // __ jcc(Assembler::equal, L_2TAG_PACKET_0_0_1); // Branch only if |x| is denormalized
  __ mov(r9, rax);                                                        // r9_0 = rax_0
  __ andr(rdx, rdx, rax);                                                 // rdx_1 = rdx_0 & rax_0
  __ cmp(rdx, (u1)0);                                                     // cmp rdx_1, 0
  __ br(__ EQ, L_2TAG_PACKET_0_0_1); // Branch only if |x| is denormalized
  // __ cmpl(rdx, 524032);
  // __ jcc(Assembler::equal, L_2TAG_PACKET_1_0_1); // Branch only if |x| is INF or NaN
  __ mov(rscratch1, 524032);
  __ cmp(rdx, rscratch1);                                                 // cmp rdx_1, 0x7ff00
  __ br(__ EQ, L_2TAG_PACKET_1_0_1); // Branch only if |x| is INF or NaN

  // __ shrl(rdx, 8);
  // __ shrq(r9, 8);
  __ lsrw(rdx, rdx, 8);                                                   // rdx_2 = rdx_1 & 0x7ff00
  __ lsr(r9, r9, 8);                                                      // r9_1 = r9_0 >> 8

  // __ andpd(xmm2, xmm0);
  // __ andpd(xmm0, xmm5);
  // __ orpd(xmm3, xmm2);
  // __ orpd(xmm1, xmm0);
  __ andr(xmm2, T16B, xmm2, xmm0);                                        // xmm2_1 = SIG_MASK & c_farg0
  __ andr(xmm0, T16B, xmm5, xmm0);                                        // xmm0_1 = EXP_MSK3 & c_farg0
  __ orr(xmm3, T16B, xmm3, xmm2);                                         // xmm3_1 = EXP_MSK2 | xmm2_1
  __ orr(xmm1, T16B, xmm1, xmm0);                                         // xmm1_2 = EXP_MASK | xmm0_1

  // __ movapd(xmm5, ExternalAddress(coeff_table), r11 /*rscratch*/);
  __ ldrq(xmm5, ExternalAddress(coeff_table), rscratch1);                 // xmm5_1 = (coeff_table[0], coeff_table[1])
  // __ movl(rax, 5462);
  __ mov(rax, 5462);                                                      // rax_1 = 0x160a
  // __ movapd(xmm6, ExternalAddress(coeff_table + 16), r11 /*rscratch*/);
  __ ldrq(xmm6, ExternalAddress(coeff_table + 16), rscratch1);            // xmm6_0 = (coeff_table[2], coeff_table[3])
  __ mul(rax, rax, rdx);                                                  // rax_2 = rax_1 * rdx_2

  // __ movq(rdx, r9);
  // __ andq(r9, 2047);
  // __ shrl(rax, 14);
  // __ andl(rdx, 2048);
  __ mov(rdx, r9);                                                        // rdx_3 = r9_1
  __ andr(r9, r9, 2047);                                                  // r9_2 = r9_1 & 0x7ff
  __ lsr(rax, rax, 14);                                                   // rax_3 = rax_2 >> 0x0e
  __ andr(rdx, rdx, 2048);                                                // rdx_4 = rdx_3 & 0x800
  // __ subq(r9, rax);
  // __ subq(r9, rax);
  // __ subq(r9, rax);
  // __ shlq(r9, 8);
  __ sub(r9, r9, rax);                                                    // r9_3 = r9_2 - rax_3 * 3
  __ sub(r9, r9, rax);
  __ sub(r9, r9, rax);
  __ lsl(r9, r9, 8);                                                      // r9_4 = r9_3 << 8

  // __ addl(rax, 682);
  // __ orl(rax, rdx);
  // __ movdl(xmm7, rax);
  // __ addq(rcx, r9);
  // __ psllq(xmm7, 52);
  __ addw(rax, rax, 682);                                                 // rax_4 = rax_3 + 0x2aa
  __ orrw(rax, rax, rdx);                                                 // rax_5 = rax_4 & rdx_4
  __ fmovd(xmm7, rax);                                                    // xmm7_2 = (double) rax_5
  __ add(rcx, rcx, r9);                                                   // rcx_2 = rcx_1 + r9_4
  __ shl(xmm7, T2D, xmm7, 52);                                            // xmm7_3[0, 1] = xmm7_2[0, 1] << 52

  __ bind(L_2TAG_PACKET_2_0_1);
  // __ movapd(xmm2, ExternalAddress(coeff_table + 32), r11 /*rscratch*/);
  // __ movapd(xmm0, ExternalAddress(coeff_table + 48), r11 /*rscratch*/);
  __ ldrq(xmm2, ExternalAddress(coeff_table + 32), rscratch1);            // xmm2_2 = (coeff_table[4], coeff_table[5])
  __ ldrq(xmm0, ExternalAddress(coeff_table + 48), rscratch1);            // xmm0_2 = (coeff_table[6], coeff_table[7])
  // __ subsd(xmm1, xmm3);
  // __ movq(xmm3, xmm7);
  // __ lea(r8, ExternalAddress(cbrt_table));
  __ fsubd(xmm1, xmm1, xmm3);                                             // xmm1_3 = xmm1_2 - xmm3_1
  __ fmovd(xmm3, xmm7);                                                   // xmm3_2 = xmm7_3
  __ lea(rscratch1, ExternalAddress(cbrt_table));
  // __ mulsd(xmm7, Address(r8, rcx, Address::times_1));
  // __ mulsd(xmm1, xmm4);
  // __ lea(r8, ExternalAddress(D_table));
  // __ mulsd(xmm3, Address(r8, rcx, Address::times_1));
  __ ldrd(xmm_scratch, Address(rscratch1, rcx));
  __ fmuld(xmm7, xmm7, xmm_scratch);                                      // xmm7_4 = xmm7_3 * cbrt_table[rcx_3]
  __ fmuld(xmm1, xmm1, xmm4);                                             // xmm1_4 = xmm1_3 * xmm4_0
  __ lea(rscratch1, ExternalAddress(D_table));
  __ ldrd(xmm_scratch, Address(rscratch1, rcx));
  __ fmuld(xmm3, xmm3, xmm_scratch);                                      // xmm3_3 = xmm3_2 * D_table[rcx_3]

  // __ movapd(xmm4, xmm1);
  __ orr(xmm4, T16B, xmm1, xmm1);
  // __ unpcklpd(xmm1, xmm1);
  __ dup(xmm1, T2D, xmm1, 0);

  // __ mulpd(xmm5, xmm1);
  // __ mulpd(xmm6, xmm1);
  // __ mulpd(xmm1, xmm1);
  __ fmul(xmm5, T2D, xmm5, xmm1);
  __ fmul(xmm6, T2D, xmm6, xmm1);
  __ fmul(xmm1, T2D, xmm1, xmm1);
  // __ addpd(xmm2, xmm5);
  // __ addpd(xmm0, xmm6);
  __ fadd(xmm2, T2D, xmm2, xmm5);
  __ fadd(xmm0, T2D, xmm0, xmm6);
  // __ mulpd(xmm2, xmm1);
  // __ mulpd(xmm1, xmm1);
  // __ mulsd(xmm4, xmm7);
  __ fmul(xmm2, T2D, xmm2, xmm1);
  __ fmul(xmm1, T2D, xmm1, xmm1);
  __ fmuld(xmm4, xmm4, xmm7);
  // __ addpd(xmm0, xmm2);
  // __ mulsd(xmm1, xmm0);
  __ fadd(xmm0, T2D, xmm0, xmm2);
  __ fmuld(xmm1, xmm1, xmm0);

  // __ unpckhpd(xmm0, xmm0);
  __ dup(xmm0, T2D, xmm0, 1);

  // __ addsd(xmm0, xmm1);
  // __ mulsd(xmm0, xmm4);
  // __ addsd(xmm0, xmm3);
  // __ addsd(xmm0, xmm7);
  __ faddd(xmm_scratch, xmm0, xmm1);
  __ fmuld(xmm_scratch, xmm_scratch, xmm4);
  __ faddd(xmm_scratch, xmm_scratch, xmm3);
  __ faddd(xmm_scratch, xmm_scratch, xmm7);
  __ ins(xmm0, 0, D, xmm_scratch, 0);
  __ b(B1_4);

  __ bind(L_2TAG_PACKET_0_0_1);

  // __ mulsd(xmm0, ExternalAddress(SCALE63), r11 /*rscratch*/);
  __ ldrd(xmm_scratch, ExternalAddress(SCALE63), r11 /*rscratch*/);
  __ fmuld(xmm0, xmm0, xmm_scratch);

  // __ movq(xmm7, xmm0);
  __ orr(xmm7, T16B, xmm0, xmm0);
  // __ movl(rdx, 524032);
  __ mov(rdx, 524032);
  // __ psrlq(xmm7, 44);
  // __ pextrw(rcx, xmm7, 0);
  __ ushr(xmm7, T2D, xmm7, 44);
  __ fmovs(rcx, xmm7);
  // __ movdl(rax, xmm7);
  __ fmovd(rax, xmm7);
  // __ andl(rcx, 248);
  __ andr(rcx, rcx, 248);
  // __ lea(r8, ExternalAddress(rcp_table));
  // __ movsd(xmm4, Address(r8, rcx, Address::times_1));
  __ lea(rscratch1, ExternalAddress(rcp_table));
  __ ldrd(xmm4, Address(rscratch1, rcx));
  // __ movq(r9, rax);
  // __ andl(rdx, rax);
  // __ shrl(rdx, 8);
  // __ shrq(r9, 8);
  // __ cmpl(rdx, 0);
  __ mov(r9, rax);
  __ andr(rdx, rdx, rax);
  __ lsrw(rdx, rdx, 8);
  __ lsr(r9, r9, 8);
  __ cmp(rdx, (u1)0);
  // __ jcc(Assembler::equal, L_2TAG_PACKET_3_0_1); // Branch only if |x| is zero
  __ br(__ EQ, L_2TAG_PACKET_3_0_1); // Branch only if |x| is zero

  // __ andpd(xmm2, xmm0);
  // __ andpd(xmm0, xmm5);
  // __ orpd(xmm3, xmm2);
  // __ orpd(xmm1, xmm0);
  __ andr(xmm2, T16B, xmm2, xmm0);
  __ andr(xmm0, T16B, xmm0, xmm5);
  __ orr(xmm3, T16B, xmm3, xmm2);
  __ orr(xmm1, T16B, xmm1, xmm0);

  // __ movapd(xmm5, ExternalAddress(coeff_table), r11 /*rscratch*/);
  __ ldrq(xmm5, ExternalAddress(coeff_table), rscratch1);
  // __ movl(rax, 5462);
  __ mov(rax, 5462);
  // __ movapd(xmm6, ExternalAddress(coeff_table + 16), r11 /*rscratch*/);
  __ ldrq(xmm6, ExternalAddress(coeff_table + 16), rscratch1);

  // __ mull(rdx);
  __ mulw(rax, rax, rdx);
  // __ movq(rdx, r9);
  // __ andq(r9, 2047);
  // __ shrl(rax, 14);
  // __ andl(rdx, 2048);
  __ mov(rdx, r9);
  __ andr(r9, r9, 2047);
  __ lsr(rax, rax, 14);
  __ andr(rdx, rdx, 2048);
  // __ subq(r9, rax);
  // __ subq(r9, rax);
  // __ subq(r9, rax);
  // __ shlq(r9, 8);
  __ sub(r9, r9, rax);
  __ sub(r9, r9, rax);
  __ sub(r9, r9, rax);
  __ lsl(r9, r9, 8);

  // __ addl(rax, 661);
  // __ orl(rax, rdx);
  // __ movdl(xmm7, rax);
  // __ addq(rcx, r9);
  // __ psllq(xmm7, 52);
  __ addw(rax, rax, 661);
  __ orrw(rax, rax, rdx);
  __ fmovd(xmm7, rax);
  __ add(rcx, rcx, r9);
  __ shl(xmm7, T2D, xmm7, 52);
  // __ jmp(L_2TAG_PACKET_2_0_1);
  __ b(L_2TAG_PACKET_2_0_1);

  __ bind(L_2TAG_PACKET_3_0_1);
  // __ cmpq(r9, 0);
  // __ jcc(Assembler::notEqual, L_2TAG_PACKET_4_0_1); // Branch only if x is negative zero
  __ cmp(r9, (u1)0);
  __ br(__ NE, L_2TAG_PACKET_4_0_1); // Branch only if x is negative zero

  // __ xorpd(xmm0, xmm0);
  // __ jmp(B1_4);
  __ eor(xmm0, T16B, xmm0, xmm0);
  __ b(B1_4);

  __ bind(L_2TAG_PACKET_4_0_1);
  // __ movsd(xmm0, ExternalAddress(ZERON), r11 /*rscratch*/);
  // __ jmp(B1_4);
  __ ldrq(xmm0, ExternalAddress(ZERON), rscratch1);
  __ b(B1_4);

  __ bind(L_2TAG_PACKET_1_0_1);
  // __ movl(rax, Address(rsp, 4));
  // __ movl(rdx, Address(rsp));
  __ ldrw(rdx, Address(sp, 0));
  __ ldrw(rax, Address(sp, 4));
  // __ movl(rcx, rax);
  // __ andl(rcx, 2147483647);
  // __ cmpl(rcx, 2146435072);
  __ movw(rcx, rax);
  __ mov(rscratch1, 2147483647);
  __ andr(rcx, rcx, rscratch1);
  __ mov(rscratch1, 2146435072);
  __ cmp(rcx, rscratch1);
  // __ jcc(Assembler::above, L_2TAG_PACKET_5_0_1); // Branch only if |x| is NaN
  __ br(Assembler::HI, L_2TAG_PACKET_5_0_1); // Branch only if |x| is NaN

  // __ cmpl(rdx, 0);
  // __ jcc(Assembler::notEqual, L_2TAG_PACKET_5_0_1); // Branch only if |x| is NaN
  __ cmp(rdx, (u1)0);
  __ br(Assembler::NE, L_2TAG_PACKET_5_0_1); // Branch only if |x| is NaN

  // __ cmpl(rax, 2146435072);
  // __ jcc(Assembler::notEqual, L_2TAG_PACKET_6_0_1); // Branch only if x is negative INF
  __ mov(rscratch1, 2146435072);
  __ cmp(rax, rscratch1);
  __ br(Assembler::NE, L_2TAG_PACKET_6_0_1); // Branch only if x is negative INF

  // __ movsd(xmm0, ExternalAddress(INF), r11 /*rscratch*/);
  // __ jmp(B1_4);
  __ ldrd(xmm0, ExternalAddress(INF), rscratch1);
  __ b(B1_4);

  __ bind(L_2TAG_PACKET_6_0_1);
  // __ movsd(xmm0, ExternalAddress(NEG_INF), r11 /*rscratch*/);
  // __ jmp(B1_4);
  __ ldrd(xmm0, ExternalAddress(NEG_INF), rscratch1);
  __ b(B1_4);

  __ bind(L_2TAG_PACKET_5_0_1);
  // __ movsd(xmm0, Address(rsp));
  // __ addsd(xmm0, xmm0);
  // __ movq(Address(rsp, 8), xmm0);
  __ ldrd(xmm0, Address(sp, 0));
  __ faddd(xmm0, xmm0, xmm0);
  // __ strd(xmm0, Address(sp, 8));   // <--- What is this for?

  __ bind(B1_4);
  // __ addq(rsp, 24);
  __ add(sp, sp, align_up(3 * wordSize, 16));

  // leave(); // required for proper stackwalking of RuntimeStub frame
  ret(lr);
}
