/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/*
 *
 *
 * (C) Copyright IBM Corp. 1999-2003 - All Rights Reserved
 *
 * WARNING: THIS FILE IS MACHINE GENERATED. DO NOT HAND EDIT IT UNLESS
 * YOU REALLY KNOW WHAT YOU'RE DOING.
 *
 */

#include "LETypes.h"
#include "ThaiShaping.h"

U_NAMESPACE_BEGIN

const le_uint8 ThaiShaping::classTable[] = {
    //       0    1    2    3    4    5    6    7    8    9    A    B    C    D    E    F
    //       -------------------------------------------------------------------------------
    /*0E00*/ NON, CON, CON, CON, CON, CON, CON, CON, CON, CON, CON, CON, CON, COD, COD, COD,
    /*0E10*/ COD, CON, CON, CON, CON, CON, CON, CON, CON, CON, CON, COA, CON, COA, CON, COA,
    /*0E20*/ CON, CON, CON, CON, FV3, CON, FV3, CON, CON, CON, CON, CON, CON, CON, CON, NON,
    /*0E30*/ FV1, AV2, FV1, FV1, AV1, AV3, AV2, AV3, BV1, BV2, BDI, NON, NON, NON, NON, NON,
    /*0E40*/ LVO, LVO, LVO, LVO, LVO, FV2, NON, AD2, TON, TON, TON, TON, AD1, NIK, AD3, NON,
    /*0E50*/ NON, NON, NON, NON, NON, NON, NON, NON, NON, NON, NON, NON
};

const ThaiShaping::StateTransition ThaiShaping::thaiStateTable[][ThaiShaping::classCount] = {
    //+--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
    //|         N         C         C         C         L         F         F         F         B         B         B         T         A         A         A         N         A         A         A    |
    //|         O         O         O         O         V         V         V         V         V         V         D         O         D         D         D         I         V         V         V    |
    //|         N         N         A         D         O         1         2         3         1         2         I         N         1         2         3         K         1         2         3    |
    //+--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
    /*00*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*01*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 2, tC}, { 6, tC}, { 0, tC}, { 8, tE}, { 0, tE}, { 0, tE}, { 0, tC}, { 9, tE}, {11, tC}, {14, tC}, {16, tC}},
    /*02*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 3, tE}, { 0, tE}, { 0, tR}, { 0, tR}, { 4, tE}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*03*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*04*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 5, tC}, { 0, tC}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*05*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*06*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 7, tE}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*07*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*08*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tA}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*09*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {10, tC}, { 0, tC}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*10*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*11*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {12, tC}, { 0, tC}, { 0, tR}, { 0, tR}, {13, tC}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*12*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*13*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*14*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {15, tC}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*15*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*16*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {17, tC}, { 0, tR}, { 0, tC}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*17*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*18*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tA}, { 0, tS}, { 0, tA}, {19, tC}, {23, tC}, { 0, tC}, {25, tF}, { 0, tF}, { 0, tF}, { 0, tD}, {26, tF}, {28, tD}, {31, tD}, {33, tD}},
    /*19*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {20, tF}, { 0, tF}, { 0, tR}, { 0, tR}, {21, tF}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*20*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*21*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {22, tC}, { 0, tC}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*22*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*23*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {24, tF}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*24*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*25*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tA}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*26*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {27, tG}, { 0, tG}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*27*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*28*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {29, tG}, { 0, tG}, { 0, tR}, { 0, tR}, {30, tG}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*29*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*30*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*31*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {32, tG}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*32*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*33*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {34, tG}, { 0, tR}, { 0, tG}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*34*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*35*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tA}, { 0, tS}, { 0, tA}, {36, tH}, {40, tH}, { 0, tH}, {42, tE}, { 0, tE}, { 0, tE}, { 0, tC}, {43, tE}, {45, tC}, {48, tC}, {50, tC}},
    /*36*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {37, tE}, { 0, tE}, { 0, tR}, { 0, tR}, {38, tE}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*37*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*38*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {39, tC}, { 0, tC}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*39*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*40*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {41, tE}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*41*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*42*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tA}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*43*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {44, tC}, { 0, tC}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*44*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*45*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {46, tC}, { 0, tC}, { 0, tR}, { 0, tR}, {47, tC}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*46*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*47*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*48*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {49, tC}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*49*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*50*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tS}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, {51, tC}, { 0, tR}, { 0, tC}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}},
    /*51*/ {{ 0, tA}, { 1, tA}, {18, tA}, {35, tA}, { 0, tA}, { 0, tS}, { 0, tA}, { 0, tA}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}, { 0, tR}}
};

U_NAMESPACE_END
