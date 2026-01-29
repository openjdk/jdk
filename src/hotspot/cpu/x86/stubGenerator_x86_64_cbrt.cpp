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
    0xFFFFFFFFUL, 0x7FFFFFFFUL, 0x00000000UL, 0x00000000UL
};

ATTRIBUTE_ALIGNED(4) static const juint _SIG_MASK[] =
{
    0x00000000UL, 0x000FC000UL
};

ATTRIBUTE_ALIGNED(4) static const juint _EXP_MASK[] =
{
    0x00000000UL, 0xBFF00000UL
};

ATTRIBUTE_ALIGNED(4) static const juint _EXP_MSK2[] =
{
    0x00000000UL, 0xBFF04000UL
};

ATTRIBUTE_ALIGNED(4) static const juint _EXP_MSK3[] =
{
    0xFFFFFFFFUL, 0x000FFFFFUL
};

ATTRIBUTE_ALIGNED(4) static const juint _SCALE63[] =
{
    0x00000000UL, 0x43E00000UL
};

ATTRIBUTE_ALIGNED(4) static const juint _ZERON[] =
{
    0x00000000UL, 0x80000000UL
};

ATTRIBUTE_ALIGNED(4) static const juint _INF[] =
{
    0x00000000UL, 0x7FF00000UL
};

ATTRIBUTE_ALIGNED(4) static const juint _NEG_INF[] =
{
    0x00000000UL, 0xFFF00000UL
};

ATTRIBUTE_ALIGNED(16) static const juint _coeff_table[] =
{
    0x5C9CC8E7UL, 0xBF9036DEUL, 0xD2B3183BUL, 0xBFA511E8UL, 0x6221A247UL,
    0xBF98090DUL, 0x1C71C71CUL, 0xBFBC71C7UL, 0xD588F115UL, 0x3F93750AUL,
    0x3C0CA458UL, 0x3FAF9ADDUL, 0x3506AC12UL, 0x3F9EE711UL, 0x55555555UL,
    0x3FD55555UL
};

ATTRIBUTE_ALIGNED(4) static const juint _rcp_table[] =
{
    0x1F81F820UL, 0xBFEF81F8UL, 0xABF0B767UL, 0xBFEE9131UL, 0x76B981DBUL, 0xBFEDAE60UL,
    0x89039B0BUL, 0xBFECD856UL, 0x0381C0E0UL, 0xBFEC0E07UL, 0xB4E81B4FUL, 0xBFEB4E81UL,
    0x606A63BEUL, 0xBFEA98EFUL, 0x951033D9UL, 0xBFE9EC8EUL, 0xFCD6E9E0UL, 0xBFE948B0UL,
    0x0F6BF3AAUL, 0xBFE8ACB9UL, 0x18181818UL, 0xBFE81818UL, 0x8178A4C8UL, 0xBFE78A4CUL,
    0x5C0B8170UL, 0xBFE702E0UL, 0x16816817UL, 0xBFE68168UL, 0x60581606UL, 0xBFE60581UL,
    0x308158EDUL, 0xBFE58ED2UL, 0xEAE2F815UL, 0xBFE51D07UL, 0xA052BF5BUL, 0xBFE4AFD6UL,
    0x6562D9FBUL, 0xBFE446F8UL, 0xBCE4A902UL, 0xBFE3E22CUL, 0x13813814UL, 0xBFE38138UL,
    0x4A2B10BFUL, 0xBFE323E3UL, 0x4D812CA0UL, 0xBFE2C9FBUL, 0xB8812735UL, 0xBFE27350UL,
    0x8121FB78UL, 0xBFE21FB7UL, 0xADA2811DUL, 0xBFE1CF06UL, 0x11811812UL, 0xBFE18118UL,
    0x1135C811UL, 0xBFE135C8UL, 0x6BE69C90UL, 0xBFE0ECF5UL, 0x0A6810A7UL, 0xBFE0A681UL,
    0xD2F1A9FCUL, 0xBFE0624DUL, 0x81020408UL, 0xBFE02040UL
};

ATTRIBUTE_ALIGNED(4) static const juint _cbrt_table[] =
{
    0x221D4C97UL, 0x3FF01539UL, 0x771A2E33UL, 0x3FF03F06UL, 0xE629D671UL, 0x3FF06800UL,
    0x8731DEB2UL, 0x3FF09032UL, 0xB1BD64ACUL, 0x3FF0B7A4UL, 0x1024FB87UL, 0x3FF0DE60UL,
    0xB0597000UL, 0x3FF1046CUL, 0x12A9BA9BUL, 0x3FF129D2UL, 0x36CDAF38UL, 0x3FF14E97UL,
    0xA772F507UL, 0x3FF172C2UL, 0x848001D3UL, 0x3FF1965AUL, 0x8C38C55DUL, 0x3FF1B964UL,
    0x236A0C45UL, 0x3FF1DBE6UL, 0x5CBB1F9FUL, 0x3FF1FDE4UL, 0xFF409042UL, 0x3FF21F63UL,
    0x8C6746E5UL, 0x3FF24069UL, 0x454BB99BUL, 0x3FF260F9UL, 0x2F8E7073UL, 0x3FF28117UL,
    0x19B4B6D0UL, 0x3FF2A0C7UL, 0x9F2263ECUL, 0x3FF2C00CUL, 0x2BB7FB78UL, 0x3FF2DEEBUL,
    0xFF1EFBBCUL, 0x3FF2FD65UL, 0x2FCCF6A2UL, 0x3FF31B80UL, 0xADC50708UL, 0x3FF3393CUL,
    0x451E4C2AUL, 0x3FF3569EUL, 0xA0554CDEUL, 0x3FF373A7UL, 0x4A6D76CEUL, 0x3FF3905BUL,
    0xB0E756B6UL, 0x3FF3ACBBUL, 0x258FA340UL, 0x3FF3C8CBUL, 0xE02AC0CEUL, 0x3FF3E48BUL,
    0x00000000UL, 0x3FF40000UL, 0x8D47800EUL, 0x3FF41B29UL, 0x4B34D9B2UL, 0x3FF44360UL,
    0x20906571UL, 0x3FF4780BUL, 0x3EE06706UL, 0x3FF4ABACUL, 0x5DA66B8DUL, 0x3FF4DE50UL,
    0x420A5C07UL, 0x3FF51003UL, 0xD6FD11C1UL, 0x3FF540CFUL, 0x4260716BUL, 0x3FF570C0UL,
    0xF7A45F38UL, 0x3FF59FDDUL, 0xC83539DFUL, 0x3FF5CE31UL, 0xF20966A4UL, 0x3FF5FBC3UL,
    0x2C8F1B70UL, 0x3FF6289CUL, 0xB4316DCFUL, 0x3FF654C1UL, 0x54A34E44UL, 0x3FF6803BUL,
    0x72182659UL, 0x3FF6AB0FUL, 0x118C08BCUL, 0x3FF6D544UL, 0xE0388D4AUL, 0x3FF6FEDEUL,
    0x3A4F645EUL, 0x3FF727E5UL, 0x31104114UL, 0x3FF7505CUL, 0x904CD549UL, 0x3FF77848UL,
    0xE36B2534UL, 0x3FF79FAEUL, 0x79F4605BUL, 0x3FF7C693UL, 0x6BBCA391UL, 0x3FF7ECFAUL,
    0x9CAE7EB9UL, 0x3FF812E7UL, 0xC043C71DUL, 0x3FF8385EUL, 0x5CB41B9DUL, 0x3FF85D63UL,
    0xCDE083DBUL, 0x3FF881F8UL, 0x4802B8A8UL, 0x3FF8A622UL, 0xDA25E5E4UL, 0x3FF8C9E2UL,
    0x706E1010UL, 0x3FF8ED3DUL, 0xD632B6DFUL, 0x3FF91034UL, 0xB7F0CF2DUL, 0x3FF932CBUL,
    0xA517BF3AUL, 0x3FF95504UL, 0x34F8BB19UL, 0x3FF987AFUL, 0x8337B317UL, 0x3FF9CA0AUL,
    0x09CC13D5UL, 0x3FFA0B17UL, 0xCE6419EDUL, 0x3FFA4AE4UL, 0xA5567031UL, 0x3FFA8982UL,
    0x500AB570UL, 0x3FFAC6FEUL, 0x97A15A17UL, 0x3FFB0364UL, 0x64671755UL, 0x3FFB3EC1UL,
    0xD288C46FUL, 0x3FFB791FUL, 0x44693BE4UL, 0x3FFBB28AUL, 0x72EB6E31UL, 0x3FFBEB0AUL,
    0x7BF5F697UL, 0x3FFC22A9UL, 0xEF6AF983UL, 0x3FFC596FUL, 0xDAC655A3UL, 0x3FFC8F65UL,
    0xD38CE8D9UL, 0x3FFCC492UL, 0x00B19367UL, 0x3FFCF8FEUL, 0x230F8709UL, 0x3FFD2CAEUL,
    0x9D15208FUL, 0x3FFD5FA9UL, 0x79B6E505UL, 0x3FFD91F6UL, 0x72BF2302UL, 0x3FFDC39AUL,
    0xF68C1570UL, 0x3FFDF49AUL, 0x2D4C23B8UL, 0x3FFE24FDUL, 0xFDC5EC73UL, 0x3FFE54C5UL,
    0x11B81DBBUL, 0x3FFE83FAUL, 0xD9DBAF25UL, 0x3FFEB29DUL, 0x9191D374UL, 0x3FFEE0B5UL,
    0x4245E4BFUL, 0x3FFF0E45UL, 0xC68A9DD3UL, 0x3FFF3B50UL, 0xCCF922DCUL, 0x3FFF67DBUL,
    0xDAD7A4A6UL, 0x3FFF93E9UL, 0x4E8CC9CBUL, 0x3FFFBF7EUL, 0x61E47CD3UL, 0x3FFFEA9CUL
};

ATTRIBUTE_ALIGNED(4) static const juint _D_table[] =
{
    0xF173D5FAUL, 0x3C76EE36UL, 0x45055704UL, 0x3C95B62DUL, 0x51EE3F07UL, 0x3CA2545BUL,
    0xA7706E18UL, 0x3C9C65F4UL, 0xDF1025A1UL, 0x3C63B83FUL, 0xB8DEC2C5UL, 0x3CA67428UL,
    0x03E33EA6UL, 0x3CA1823DUL, 0xA06E6C52UL, 0x3CA241D3UL, 0xEFA7E815UL, 0x3CA8B4E1UL,
    0x4E754FD0UL, 0x3CADEAC4UL, 0x3D7C04C0UL, 0x3C71CC82UL, 0xC264F127UL, 0x3C953DC9UL,
    0x34D5C5A7UL, 0x3C93B5F7UL, 0xB9A7B902UL, 0x3C7366A3UL, 0x6433DD6CUL, 0x3CAAC888UL,
    0x4F401711UL, 0x3C987A4CUL, 0x1BBE943FUL, 0x3C9FAB9FUL, 0xFD6AC93CUL, 0x3CA0C4B5UL,
    0x766F1035UL, 0x3CA90835UL, 0x2CE13C95UL, 0x3CA09FD9UL, 0x8418C8D8UL, 0x3CADC143UL,
    0xFF474261UL, 0x3C8DC87DUL, 0x5CD783D3UL, 0x3C8F8872UL, 0xE7D0C8AAUL, 0x3CAEC35DUL,
    0xDBA49538UL, 0x3CA3943BUL, 0x2B203947UL, 0x3CA92195UL, 0xAFE6F86CUL, 0x3C59F556UL,
    0x3195A5F9UL, 0x3CAADC99UL, 0x3D770E65UL, 0x3CA41943UL, 0xA36B97EAUL, 0x3CA76B6EUL,
    0xAAAAAAABUL, 0x3BD46AAAUL, 0xFEE9D063UL, 0x3C637D40UL, 0xF514C24EUL, 0x3C89F356UL,
    0x670030E9UL, 0x3C953F22UL, 0xA173C1CFUL, 0x3CAEA671UL, 0x3FBCC1DDUL, 0x3C841D58UL,
    0x29B9B818UL, 0x3C9648F0UL, 0xAD202AB4UL, 0x3CA1A66DUL, 0xF2D6B269UL, 0x3CA7B07BUL,
    0x017DC4ADUL, 0x3C836A36UL, 0xD6B16F60UL, 0x3C8B726BUL, 0xC2BC701DUL, 0x3CABFE18UL,
    0x1DFE451FUL, 0x3C7E799DUL, 0x7E7B5452UL, 0x3CADDF5AUL, 0xEA15C5E5UL, 0x3C734D90UL,
    0xA6558F7BUL, 0x3C8F4CBBUL, 0x4F4D361AUL, 0x3C9D473AUL, 0xF06B5ECFUL, 0x3C87E2D6UL,
    0xDC49B5F3UL, 0x3CA5F6F5UL, 0x0F5A41F1UL, 0x3CA16024UL, 0xC062C2BCUL, 0x3CA3586CUL,
    0x0DF45D94UL, 0x3CA0C6A9UL, 0xEEF4E10BUL, 0x3CA2703CUL, 0x74215C62UL, 0x3CA99F3EUL,
    0x286F88D2UL, 0x3CAFA5EFUL, 0xB7D00B1FUL, 0x3C99239EUL, 0x8FF8E50CUL, 0x3CABC642UL,
    0x0A756B50UL, 0x3CA33971UL, 0xBE93D5DCUL, 0x3C389058UL, 0x7B752D97UL, 0x3C8E08EEUL,
    0x0FFF0A3FUL, 0x3C9A2FEDUL, 0x37EAC5DFUL, 0x3CA42034UL, 0x6C4969DFUL, 0x3CA35668UL,
    0xF5860FA5UL, 0x3CA082AEUL, 0x99B322B6UL, 0x3C62CF11UL, 0x933E42D8UL, 0x3C7AC44EUL,
    0x0761E377UL, 0x3C975F68UL, 0x4B704CC9UL, 0x3C7C5ADFUL, 0xCB8394DCUL, 0x3CA0F9AEUL,
    0x3E08F0F9UL, 0x3C9158C1UL, 0xCFA3F556UL, 0x3C9C7516UL, 0xF6CB01CDUL, 0x3C91D9C1UL,
    0xE811C1DAUL, 0x3C9DA58FUL, 0xEA9036DBUL, 0x3C2DCD9DUL, 0xB18FAB04UL, 0x3C8015A8UL,
    0x92316223UL, 0x3CAD4C55UL, 0xBE291E10UL, 0x3C8C6A0DUL, 0xFC9476ABUL, 0x3C8C615DUL,
    0x9B9BCA75UL, 0x3CACE0D7UL, 0x7ECC4726UL, 0x3CA4614AUL, 0x312152EEUL, 0x3CACD427UL,
    0x811960CAUL, 0x3CAC1BA1UL, 0xA557FD24UL, 0x3C6514CAUL, 0xF5CDF826UL, 0x3CA712CCUL,
    0x75CDBEA6UL, 0x3C9D93A5UL, 0xF3F3450CUL, 0x3CA90AAFUL, 0x071BA369UL, 0x3C85382FUL,
    0xCF26AE90UL, 0x3CA87E97UL, 0x75933097UL, 0x3C86DA5CUL, 0x309C2B19UL, 0x3CA61791UL,
    0x90D5990BUL, 0x3CA44210UL, 0xF22AC222UL, 0x3C9A5F49UL, 0x0411EEF9UL, 0x3CAC502EUL,
    0x839809AEUL, 0x3C93D12AUL, 0x468A4418UL, 0x3CA46C91UL, 0x088AFCDBUL, 0x3C9F1C33UL
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
