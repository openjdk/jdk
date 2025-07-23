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

#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"

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
//  cbrt(NaN) = quiet NaN
//  cbrt(+/-INF) = +/-INF
//  cbrt(+/-0) = +/-0
//
/******************************************************************************/

/* Represents 0x7FFFFFFFFFFFFFFF double precision in lower 64 bits*/
ATTRIBUTE_ALIGNED(16) static const juint _ABS_MASK[] =
{
    4294967295, 2147483647, 0, 0
};

ATTRIBUTE_ALIGNED(4) static const juint _SIG_MASK[] =
{
    0, 1032192
};

ATTRIBUTE_ALIGNED(4) static const juint _EXP_MASK[] =
{
    0, 3220176896
};

ATTRIBUTE_ALIGNED(4) static const juint _EXP_MSK2[] =
{
    0, 3220193280
};

ATTRIBUTE_ALIGNED(4) static const juint _EXP_MSK3[] =
{
    4294967295, 1048575
};

ATTRIBUTE_ALIGNED(4) static const juint _SCALE63[] =
{
    0, 1138753536
};

ATTRIBUTE_ALIGNED(4) static const juint _ZERON[] =
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

#define __ _masm->

address StubGenerator::generate_libmCbrt() {
  StubId stub_id = StubId::stubgen_dcbrt_id;
  StubCodeMark mark(this, stub_id);
  address start = __ pc();

  Label L_2TAG_PACKET_0_0_1, L_2TAG_PACKET_1_0_1, L_2TAG_PACKET_2_0_1;
  Label B1_1, B1_2, B1_4;

  address ABS_MASK        = (address)_ABS_MASK;
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

  __ enter(); // required for proper stackwalking of RuntimeStub frame

  __ bind(B1_1);
  __ ucomisd(xmm0, ExternalAddress(ZERON), r11 /*rscratch*/);
  __ jcc(Assembler::equal, L_2TAG_PACKET_1_0_1); // Branch only if x is +/- zero or NaN
  __ movq(xmm1, xmm0);
  __ andpd(xmm1, ExternalAddress(ABS_MASK), r11 /*rscratch*/);
  __ ucomisd(xmm1, ExternalAddress(INF), r11 /*rscratch*/);
  __ jcc(Assembler::equal, B1_4); // Branch only if x is +/- INF

  __ bind(B1_2);
  __ movq(xmm7, xmm0);
  __ movl(rdx, 524032);
  __ movsd(xmm5, ExternalAddress(EXP_MSK3), r11 /*rscratch*/);
  __ movsd(xmm3, ExternalAddress(EXP_MSK2), r11 /*rscratch*/);
  __ psrlq(xmm7, 44);
  __ pextrw(rcx, xmm7, 0);
  __ movdl(rax, xmm7);
  __ movsd(xmm1, ExternalAddress(EXP_MASK), r11 /*rscratch*/);
  __ movsd(xmm2, ExternalAddress(SIG_MASK), r11 /*rscratch*/);
  __ andl(rcx, 248);
  __ lea(r8, ExternalAddress(rcp_table));
  __ movsd(xmm4, Address(rcx, r8, Address::times_1));
  __ movq(r9, rax);
  __ andl(rdx, rax);
  __ cmpl(rdx, 0);
  __ jcc(Assembler::equal, L_2TAG_PACKET_0_0_1); // Branch only if |x| is denormalized
  __ shrl(rdx, 8);
  __ shrq(r9, 8);
  __ andpd(xmm2, xmm0);
  __ andpd(xmm0, xmm5);
  __ orpd(xmm3, xmm2);
  __ orpd(xmm1, xmm0);
  __ movapd(xmm5, ExternalAddress(coeff_table), r11 /*rscratch*/);
  __ movl(rax, 5462);
  __ movapd(xmm6, ExternalAddress(coeff_table + 16), r11 /*rscratch*/);
  __ mull(rdx);
  __ movq(rdx, r9);
  __ andq(r9, 2047);
  __ shrl(rax, 14);
  __ andl(rdx, 2048);
  __ subq(r9, rax);
  __ subq(r9, rax);
  __ subq(r9, rax);
  __ shlq(r9, 8);
  __ addl(rax, 682);
  __ orl(rax, rdx);
  __ movdl(xmm7, rax);
  __ addq(rcx, r9);
  __ psllq(xmm7, 52);

  __ bind(L_2TAG_PACKET_2_0_1);
  __ movapd(xmm2, ExternalAddress(coeff_table + 32), r11 /*rscratch*/);
  __ movapd(xmm0, ExternalAddress(coeff_table + 48), r11 /*rscratch*/);
  __ subsd(xmm1, xmm3);
  __ movq(xmm3, xmm7);
  __ lea(r8, ExternalAddress(cbrt_table));
  __ mulsd(xmm7, Address(rcx, r8, Address::times_1));
  __ mulsd(xmm1, xmm4);
  __ lea(r8, ExternalAddress(D_table));
  __ mulsd(xmm3, Address(rcx, r8, Address::times_1));
  __ movapd(xmm4, xmm1);
  __ unpcklpd(xmm1, xmm1);
  __ mulpd(xmm5, xmm1);
  __ mulpd(xmm6, xmm1);
  __ mulpd(xmm1, xmm1);
  __ addpd(xmm2, xmm5);
  __ addpd(xmm0, xmm6);
  __ mulpd(xmm2, xmm1);
  __ mulpd(xmm1, xmm1);
  __ mulsd(xmm4, xmm7);
  __ addpd(xmm0, xmm2);
  __ mulsd(xmm1, xmm0);
  __ unpckhpd(xmm0, xmm0);
  __ addsd(xmm0, xmm1);
  __ mulsd(xmm0, xmm4);
  __ addsd(xmm0, xmm3);
  __ addsd(xmm0, xmm7);
  __ jmp(B1_4);

  __ bind(L_2TAG_PACKET_0_0_1);
  __ mulsd(xmm0, ExternalAddress(SCALE63), r11 /*rscratch*/);
  __ movq(xmm7, xmm0);
  __ movl(rdx, 524032);
  __ psrlq(xmm7, 44);
  __ pextrw(rcx, xmm7, 0);
  __ movdl(rax, xmm7);
  __ andl(rcx, 248);
  __ lea(r8, ExternalAddress(rcp_table));
  __ movsd(xmm4, Address(rcx, r8, Address::times_1));
  __ movq(r9, rax);
  __ andl(rdx, rax);
  __ shrl(rdx, 8);
  __ shrq(r9, 8);
  __ andpd(xmm2, xmm0);
  __ andpd(xmm0, xmm5);
  __ orpd(xmm3, xmm2);
  __ orpd(xmm1, xmm0);
  __ movapd(xmm5, ExternalAddress(coeff_table), r11 /*rscratch*/);
  __ movl(rax, 5462);
  __ movapd(xmm6, ExternalAddress(coeff_table + 16), r11 /*rscratch*/);
  __ mull(rdx);
  __ movq(rdx, r9);
  __ andq(r9, 2047);
  __ shrl(rax, 14);
  __ andl(rdx, 2048);
  __ subq(r9, rax);
  __ subq(r9, rax);
  __ subq(r9, rax);
  __ shlq(r9, 8);
  __ addl(rax, 661);
  __ orl(rax, rdx);
  __ movdl(xmm7, rax);
  __ addq(rcx, r9);
  __ psllq(xmm7, 52);
  __ jmp(L_2TAG_PACKET_2_0_1);

  __ bind(L_2TAG_PACKET_1_0_1);
  __ addsd(xmm0, xmm0);

  __ bind(B1_4);
  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  return start;
}

#undef __
