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
 */

// This file is available under and governed by the GNU General Public
// License version 2 only, as published by the Free Software Foundation.
// However, the following notice accompanied the original version of this
// file:
//
//
//  Little cms
//  Copyright (C) 1998-2007 Marti Maria
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software
// is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

// Interpolation

#include "lcms.h"

void cmsCalcL16Params(int nSamples, LPL16PARAMS p)
{
       p -> nSamples = nSamples;
       p -> Domain   = (WORD) (nSamples - 1);
       p -> nInputs = p -> nOutputs = 1;

}



// Eval gray LUT having only one input channel

static
void Eval1Input(WORD StageABC[], WORD StageLMN[], WORD LutTable[], LPL16PARAMS p16)
{
       Fixed32 fk;
       Fixed32 k0, k1, rk, K0, K1;
       int OutChan;

       fk = ToFixedDomain((Fixed32) StageABC[0] * p16 -> Domain);
       k0 = FIXED_TO_INT(fk);
       rk = (WORD) FIXED_REST_TO_INT(fk);

       k1 = k0 + (StageABC[0] != 0xFFFFU ? 1 : 0);

       K0 = p16 -> opta1 * k0;
       K1 = p16 -> opta1 * k1;

       for (OutChan=0; OutChan < p16->nOutputs; OutChan++) {

           StageLMN[OutChan] = (WORD) FixedLERP(rk, LutTable[K0+OutChan],
                                                    LutTable[K1+OutChan]);
       }
}



// For more that 3 inputs (i.e., CMYK)
// evaluate two 3-dimensional interpolations and then linearly interpolate between them.
static
void Eval4Inputs(WORD StageABC[], WORD StageLMN[], WORD LutTable[], LPL16PARAMS p16)
{
       Fixed32 fk;
       Fixed32 k0, rk;
       int K0, K1;
       LPWORD T;
       int i;
       WORD Tmp1[MAXCHANNELS], Tmp2[MAXCHANNELS];


       fk = ToFixedDomain((Fixed32) StageABC[0] * p16 -> Domain);
       k0 = FIXED_TO_INT(fk);
       rk = FIXED_REST_TO_INT(fk);

       K0 = p16 -> opta4 * k0;
       K1 = p16 -> opta4 * (k0 + (StageABC[0] != 0xFFFFU ? 1 : 0));

       p16 -> nInputs = 3;

       T = LutTable + K0;

       cmsTetrahedralInterp16(StageABC + 1,  Tmp1, T, p16);


       T = LutTable + K1;

       cmsTetrahedralInterp16(StageABC + 1,  Tmp2, T, p16);


       p16 -> nInputs = 4;
       for (i=0; i < p16 -> nOutputs; i++)
       {
              StageLMN[i] = (WORD) FixedLERP(rk, Tmp1[i], Tmp2[i]);

       }

}


static
void Eval5Inputs(WORD StageABC[], WORD StageLMN[], WORD LutTable[], LPL16PARAMS p16)
{
       Fixed32 fk;
       Fixed32 k0, rk;
       int K0, K1;
       LPWORD T;
       int i;
       WORD Tmp1[MAXCHANNELS], Tmp2[MAXCHANNELS];


       fk = ToFixedDomain((Fixed32) StageABC[0] * p16 -> Domain);
       k0 = FIXED_TO_INT(fk);
       rk = FIXED_REST_TO_INT(fk);

       K0 = p16 -> opta5 * k0;
       K1 = p16 -> opta5 * (k0 + (StageABC[0] != 0xFFFFU ? 1 : 0));

       p16 -> nInputs = 4;

       T = LutTable + K0;

       Eval4Inputs(StageABC + 1, Tmp1, T, p16);

       T = LutTable + K1;

       Eval4Inputs(StageABC + 1, Tmp2, T, p16);

       p16 -> nInputs = 5;
       for (i=0; i < p16 -> nOutputs; i++)
       {
              StageLMN[i] = (WORD) FixedLERP(rk, Tmp1[i], Tmp2[i]);

       }

}


static
void Eval6Inputs(WORD StageABC[], WORD StageLMN[], WORD LutTable[], LPL16PARAMS p16)
{
       Fixed32 fk;
       Fixed32 k0, rk;
       int K0, K1;
       LPWORD T;
       int i;
       WORD Tmp1[MAXCHANNELS], Tmp2[MAXCHANNELS];


       fk = ToFixedDomain((Fixed32) StageABC[0] * p16 -> Domain);
       k0 = FIXED_TO_INT(fk);
       rk = FIXED_REST_TO_INT(fk);

       K0 = p16 -> opta6 * k0;
       K1 = p16 -> opta6 * (k0 + (StageABC[0] != 0xFFFFU ? 1 : 0));

       p16 -> nInputs = 5;

       T = LutTable + K0;

       Eval5Inputs(StageABC + 1, Tmp1, T, p16);

       T = LutTable + K1;

       Eval5Inputs(StageABC + 1, Tmp2, T, p16);

       p16 -> nInputs = 6;
       for (i=0; i < p16 -> nOutputs; i++)
       {
              StageLMN[i] = (WORD) FixedLERP(rk, Tmp1[i], Tmp2[i]);
       }

}

static
void Eval7Inputs(WORD StageABC[], WORD StageLMN[], WORD LutTable[], LPL16PARAMS p16)
{
       Fixed32 fk;
       Fixed32 k0, rk;
       int K0, K1;
       LPWORD T;
       int i;
       WORD Tmp1[MAXCHANNELS], Tmp2[MAXCHANNELS];


       fk = ToFixedDomain((Fixed32) StageABC[0] * p16 -> Domain);
       k0 = FIXED_TO_INT(fk);
       rk = FIXED_REST_TO_INT(fk);

       K0 = p16 -> opta7 * k0;
       K1 = p16 -> opta7 * (k0 + (StageABC[0] != 0xFFFFU ? 1 : 0));

       p16 -> nInputs = 6;

       T = LutTable + K0;

       Eval6Inputs(StageABC + 1, Tmp1, T, p16);

       T = LutTable + K1;

       Eval6Inputs(StageABC + 1, Tmp2, T, p16);

       p16 -> nInputs = 7;
       for (i=0; i < p16 -> nOutputs; i++)
       {
              StageLMN[i] = (WORD) FixedLERP(rk, Tmp1[i], Tmp2[i]);
       }

}

static
void Eval8Inputs(WORD StageABC[], WORD StageLMN[], WORD LutTable[], LPL16PARAMS p16)
{
       Fixed32 fk;
       Fixed32 k0, rk;
       int K0, K1;
       LPWORD T;
       int i;
       WORD Tmp1[MAXCHANNELS], Tmp2[MAXCHANNELS];


       fk = ToFixedDomain((Fixed32) StageABC[0] * p16 -> Domain);
       k0 = FIXED_TO_INT(fk);
       rk = FIXED_REST_TO_INT(fk);

       K0 = p16 -> opta8 * k0;
       K1 = p16 -> opta8 * (k0 + (StageABC[0] != 0xFFFFU ? 1 : 0));

       p16 -> nInputs = 7;

       T = LutTable + K0;

       Eval7Inputs(StageABC + 1, Tmp1, T, p16);

       T = LutTable + K1;

       Eval7Inputs(StageABC + 1, Tmp2, T, p16);

       p16 -> nInputs = 8;
       for (i=0; i < p16 -> nOutputs; i++)
       {
              StageLMN[i] = (WORD) FixedLERP(rk, Tmp1[i], Tmp2[i]);
       }

}


// Fills optimization parameters

void cmsCalcCLUT16ParamsEx(int nSamples, int InputChan, int OutputChan,
                                            LCMSBOOL lUseTetrahedral, LPL16PARAMS p)
{
       int clutPoints;

       cmsCalcL16Params(nSamples, p);

       p -> nInputs  = InputChan;
       p -> nOutputs = OutputChan;

       clutPoints = p -> Domain + 1;

       p -> opta1 = p -> nOutputs;              // Z
       p -> opta2 = p -> opta1 * clutPoints;    // Y
       p -> opta3 = p -> opta2 * clutPoints;    // X
       p -> opta4 = p -> opta3 * clutPoints;    // Used only in 4 inputs LUT
       p -> opta5 = p -> opta4 * clutPoints;    // Used only in 5 inputs LUT
       p -> opta6 = p -> opta5 * clutPoints;    // Used only on 6 inputs LUT
       p -> opta7 = p -> opta6 * clutPoints;    // Used only on 7 inputs LUT
       p -> opta8 = p -> opta7 * clutPoints;    // Used only on 8 inputs LUT


       switch (InputChan) {


           case 1: // Gray LUT

               p ->Interp3D = Eval1Input;
               break;

           case 3:  // RGB et al
               if (lUseTetrahedral) {
                   p ->Interp3D = cmsTetrahedralInterp16;
               }
               else
                   p ->Interp3D = cmsTrilinearInterp16;
               break;

           case 4:  // CMYK LUT
                p ->Interp3D = Eval4Inputs;
                break;

           case 5: // 5 Inks
                p ->Interp3D = Eval5Inputs;
                break;

           case 6: // 6 Inks
                p -> Interp3D = Eval6Inputs;
                break;

            case 7: // 7 inks
                p ->Interp3D = Eval7Inputs;
                break;

           case 8: // 8 inks
                p ->Interp3D = Eval8Inputs;
                break;

           default:
                cmsSignalError(LCMS_ERRC_ABORTED, "Unsupported restoration (%d channels)", InputChan);
           }

}


void cmsCalcCLUT16Params(int nSamples, int InputChan, int OutputChan, LPL16PARAMS p)
{
    cmsCalcCLUT16ParamsEx(nSamples, InputChan, OutputChan, FALSE, p);
}



#ifdef USE_FLOAT


// Floating-point version

WORD cmsLinearInterpLUT16(WORD Value, WORD LutTable[], LPL16PARAMS p)
{
       double y1, y0;
       double y;
       double val2, rest;
       int cell0, cell1;

       // if last value...

       if (Value == 0xffff) return LutTable[p -> Domain];

       val2 = p -> Domain * ((double) Value / 65535.0);

       cell0 = (int) floor(val2);
       cell1 = (int) ceil(val2);

       // Rest is 16 LSB bits

       rest = val2 - cell0;

       y0 = LutTable[cell0] ;
       y1 = LutTable[cell1] ;

       y = y0 + (y1 - y0) * rest;


       return (WORD) floor(y+.5);
}

#endif


//
//  Linear interpolation (Fixed-point optimized, but C source)
//


#ifdef USE_C

WORD cmsLinearInterpLUT16(WORD Value1, WORD LutTable[], LPL16PARAMS p)
{
       WORD y1, y0;
       WORD y;
       int dif, a1;
       int cell0, rest;
       int val3, Value;

       // if last value...


       Value = Value1;
       if (Value == 0xffff) return LutTable[p -> Domain];

       val3 = p -> Domain * Value;
       val3 = ToFixedDomain(val3);              // To fixed 15.16

       cell0 = FIXED_TO_INT(val3);             // Cell is 16 MSB bits
       rest  = FIXED_REST_TO_INT(val3);        // Rest is 16 LSB bits

       y0 = LutTable[cell0] ;
       y1 = LutTable[cell0+1] ;

       dif = (int) y1 - y0;        // dif is in domain -ffff ... ffff

       if (dif >= 0)
       {
       a1 = ToFixedDomain(dif * rest);
       a1 += 0x8000;
       }
       else
       {
              a1 = ToFixedDomain((- dif) * rest);
              a1 -= 0x8000;
              a1 = -a1;
       }

       y = (WORD) (y0 + FIXED_TO_INT(a1));

       return y;
}

#endif

// Linear interpolation (asm by hand optimized)

#ifdef USE_ASSEMBLER

#ifdef _MSC_VER
#pragma warning(disable : 4033)
#pragma warning(disable : 4035)
#endif

WORD cmsLinearInterpLUT16(WORD Value, WORD LutTable[], LPL16PARAMS p)
{
       int xDomain = p -> Domain;


       if (Value == 0xffff) return LutTable[p -> Domain];
       else
       ASM {
              xor       eax, eax
              mov       ax, word ptr ss:Value
              mov       edx, ss:xDomain
              mul       edx                         //  val3 = p -> Domain * Value;
              shld      edx, eax, 16                // Convert it to fixed 15.16
              shl       eax, 16                     // * 65536 / 65535
              mov       ebx, 0x0000ffff
              div       ebx
              mov       ecx, eax
              sar       ecx, 16                        // ecx = cell0
              mov       edx, eax                       // rest = (val2 & 0xFFFFU)
              and       edx, 0x0000ffff                // edx = rest
              mov       ebx, ss:LutTable
              lea       eax, dword ptr [ebx+2*ecx]     // Ptr to LUT
              xor       ebx, ebx
              mov        bx, word  ptr [eax]           // EBX = y0
              movzx     eax, word  ptr [eax+2]         // EAX = y1
              sub       eax, ebx                       // EAX = y1-y0
              js        IsNegative
              mul       edx                            // EAX = EAX * rest
              shld      edx, eax, 16                   // Pass it to fixed
              sal       eax, 16                        // * 65536 / 65535
              mov       ecx, 0x0000ffff
              div       ecx
              add       eax, 0x8000                    // Rounding
              sar       eax, 16
              add       eax, ebx                       // Done!
              }

              RET((WORD) _EAX);

       IsNegative:

              ASM {
              neg       eax
              mul       edx                            // EAX = EAX * rest
              shld      edx, eax, 16                   // Pass it to fixed
              sal       eax, 16                        // * 65536 / 65535
              mov       ecx, 0x0000ffff
              div       ecx
              sub       eax, 0x8000
              neg       eax
              sar       eax, 16
              add       eax, ebx                       // Done!
              }

              RET((WORD) _EAX);
}

#ifdef _MSC_VER
#pragma warning(default : 4033)
#pragma warning(default : 4035)
#endif

#endif

Fixed32 cmsLinearInterpFixed(WORD Value1, WORD LutTable[], LPL16PARAMS p)
{
       Fixed32 y1, y0;
       int cell0;
       int val3, Value;

       // if last value...


       Value = Value1;
       if (Value == 0xffffU) return LutTable[p -> Domain];

       val3 = p -> Domain * Value;
       val3 = ToFixedDomain(val3);              // To fixed 15.16

       cell0 = FIXED_TO_INT(val3);             // Cell is 16 MSB bits

       y0 = LutTable[cell0] ;
       y1 = LutTable[cell0+1] ;


       return y0 + FixedMul((y1 - y0), (val3 & 0xFFFFL));
}


// Reverse Lineal interpolation (16 bits)
// Im using a sort of binary search here, this is not a time-critical function

WORD cmsReverseLinearInterpLUT16(WORD Value, WORD LutTable[], LPL16PARAMS p)
{
        register int l = 1;
        register int r = 0x10000;
        register int x = 0, res;       // 'int' Give spacing for negative values
        int NumZeroes, NumPoles;
        int cell0, cell1;
        double val2;
        double y0, y1, x0, x1;
        double a, b, f;

        // July/27 2001 - Expanded to handle degenerated curves with an arbitrary
        // number of elements containing 0 at the begining of the table (Zeroes)
        // and another arbitrary number of poles (FFFFh) at the end.
        // First the zero and pole extents are computed, then value is compared.

        NumZeroes = 0;
        while (LutTable[NumZeroes] == 0 && NumZeroes < p -> Domain)
                        NumZeroes++;

        // There are no zeros at the beginning and we are trying to find a zero, so
        // return anything. It seems zero would be the less destructive choice

        if (NumZeroes == 0 && Value == 0)
            return 0;

        NumPoles = 0;
        while (LutTable[p -> Domain - NumPoles] == 0xFFFF && NumPoles < p -> Domain)
                        NumPoles++;

        // Does the curve belong to this case?
        if (NumZeroes > 1 || NumPoles > 1)
        {
                int a, b;

                // Identify if value fall downto 0 or FFFF zone
                if (Value == 0) return 0;
               // if (Value == 0xFFFF) return 0xFFFF;

                // else restrict to valid zone

                a = ((NumZeroes-1) * 0xFFFF) / p->Domain;
                b = ((p -> Domain - NumPoles) * 0xFFFF) / p ->Domain;

                l = a - 1;
                r = b + 1;
        }


        // Seems not a degenerated case... apply binary search

        while (r > l) {

                x = (l + r) / 2;

                res = (int) cmsLinearInterpLUT16((WORD) (x - 1), LutTable, p);

                if (res == Value) {

                    // Found exact match.

                    return (WORD) (x - 1);
                }

                if (res > Value) r = x - 1;
                else l = x + 1;
        }

        // Not found, should we interpolate?


        // Get surrounding nodes

        val2 = p -> Domain * ((double) (x - 1) / 65535.0);

        cell0 = (int) floor(val2);
        cell1 = (int) ceil(val2);

        if (cell0 == cell1) return (WORD) x;

        y0 = LutTable[cell0] ;
        x0 = (65535.0 * cell0) / p ->Domain;

        y1 = LutTable[cell1] ;
        x1 = (65535.0 * cell1) / p ->Domain;

        a = (y1 - y0) / (x1 - x0);
        b = y0 - a * x0;

        if (fabs(a) < 0.01) return (WORD) x;

        f = ((Value - b) / a);

        if (f < 0.0) return (WORD) 0;
        if (f >= 65535.0) return (WORD) 0xFFFF;

        return (WORD) floor(f + 0.5);

}




// Trilinear interpolation (16 bits) - float version

#ifdef USE_FLOAT
void cmsTrilinearInterp16(WORD Input[], WORD Output[],
                            WORD LutTable[], LPL16PARAMS p)

{
#   define LERP(a,l,h)  (double) ((l)+(((h)-(l))*(a)))
#   define DENS(X, Y, Z)    (double) (LutTable[TotalOut*((Z)+clutPoints*((Y)+clutPoints*(X)))+OutChan])



    double     px, py, pz;
    int        x0, y0, z0,
               x1, y1, z1;
               int clutPoints, TotalOut, OutChan;
    double     fx, fy, fz,
               d000, d001, d010, d011,
               d100, d101, d110, d111,
               dx00, dx01, dx10, dx11,
               dxy0, dxy1, dxyz;


    clutPoints = p -> Domain + 1;
    TotalOut   = p -> nOutputs;

    px = ((double) Input[0] * (p->Domain)) / 65535.0;
    py = ((double) Input[1] * (p->Domain)) / 65535.0;
    pz = ((double) Input[2] * (p->Domain)) / 65535.0;

    x0 = (int) _cmsQuickFloor(px); fx = px - (double) x0;
    y0 = (int) _cmsQuickFloor(py); fy = py - (double) y0;
    z0 = (int) _cmsQuickFloor(pz); fz = pz - (double) z0;

    x1 = x0 + (Input[0] != 0xFFFFU ? 1 : 0);
    y1 = y0 + (Input[1] != 0xFFFFU ? 1 : 0);
    z1 = z0 + (Input[2] != 0xFFFFU ? 1 : 0);


    for (OutChan = 0; OutChan < TotalOut; OutChan++)
    {

        d000 = DENS(x0, y0, z0);
        d001 = DENS(x0, y0, z1);
        d010 = DENS(x0, y1, z0);
        d011 = DENS(x0, y1, z1);

        d100 = DENS(x1, y0, z0);
        d101 = DENS(x1, y0, z1);
        d110 = DENS(x1, y1, z0);
        d111 = DENS(x1, y1, z1);


    dx00 = LERP(fx, d000, d100);
    dx01 = LERP(fx, d001, d101);
    dx10 = LERP(fx, d010, d110);
    dx11 = LERP(fx, d011, d111);

    dxy0 = LERP(fy, dx00, dx10);
    dxy1 = LERP(fy, dx01, dx11);

    dxyz = LERP(fz, dxy0, dxy1);

    Output[OutChan] = (WORD) floor(dxyz + .5);
    }


#   undef LERP
#   undef DENS
}


#endif


#ifndef USE_FLOAT

// Trilinear interpolation (16 bits) - optimized version

void cmsTrilinearInterp16(WORD Input[], WORD Output[],
                            WORD LutTable[], LPL16PARAMS p)

{
#define DENS(i,j,k) (LutTable[(i)+(j)+(k)+OutChan])
#define LERP(a,l,h)     (WORD) (l+ ROUND_FIXED_TO_INT(((h-l)*a)))


           int        OutChan, TotalOut;
           Fixed32    fx, fy, fz;
  register int        rx, ry, rz;
           int        x0, y0, z0;
  register int        X0, X1, Y0, Y1, Z0, Z1;
           int        d000, d001, d010, d011,
                      d100, d101, d110, d111,
                      dx00, dx01, dx10, dx11,
                      dxy0, dxy1, dxyz;


    TotalOut   = p -> nOutputs;

    fx = ToFixedDomain((int) Input[0] * p -> Domain);
    x0  = FIXED_TO_INT(fx);
    rx  = FIXED_REST_TO_INT(fx);    // Rest in 0..1.0 domain


    fy = ToFixedDomain((int) Input[1] * p -> Domain);
    y0  = FIXED_TO_INT(fy);
    ry  = FIXED_REST_TO_INT(fy);

    fz = ToFixedDomain((int) Input[2] * p -> Domain);
    z0 = FIXED_TO_INT(fz);
    rz = FIXED_REST_TO_INT(fz);



    X0 = p -> opta3 * x0;
    X1 = X0 + (Input[0] == 0xFFFFU ? 0 : p->opta3);

    Y0 = p -> opta2 * y0;
    Y1 = Y0 + (Input[1] == 0xFFFFU ? 0 : p->opta2);

    Z0 = p -> opta1 * z0;
    Z1 = Z0 + (Input[2] == 0xFFFFU ? 0 : p->opta1);



    for (OutChan = 0; OutChan < TotalOut; OutChan++)
    {

        d000 = DENS(X0, Y0, Z0);
        d001 = DENS(X0, Y0, Z1);
        d010 = DENS(X0, Y1, Z0);
        d011 = DENS(X0, Y1, Z1);

        d100 = DENS(X1, Y0, Z0);
        d101 = DENS(X1, Y0, Z1);
        d110 = DENS(X1, Y1, Z0);
        d111 = DENS(X1, Y1, Z1);


        dx00 = LERP(rx, d000, d100);
        dx01 = LERP(rx, d001, d101);
        dx10 = LERP(rx, d010, d110);
        dx11 = LERP(rx, d011, d111);

        dxy0 = LERP(ry, dx00, dx10);
        dxy1 = LERP(ry, dx01, dx11);

        dxyz = LERP(rz, dxy0, dxy1);

        Output[OutChan] = (WORD) dxyz;
    }


#   undef LERP
#   undef DENS
}

#endif


#ifdef USE_FLOAT

#define DENS(X, Y, Z)    (double) (LutTable[TotalOut*((Z)+clutPoints*((Y)+clutPoints*(X)))+OutChan])


// Tetrahedral interpolation, using Sakamoto algorithm.

void cmsTetrahedralInterp16(WORD Input[],
                            WORD Output[],
                            WORD LutTable[],
                            LPL16PARAMS p)
{
    double     px, py, pz;
    int        x0, y0, z0,
               x1, y1, z1;
    double     fx, fy, fz;
    double     c1=0, c2=0, c3=0;
    int        clutPoints, OutChan, TotalOut;


    clutPoints = p -> Domain + 1;
    TotalOut   = p -> nOutputs;


    px = ((double) Input[0] * p->Domain) / 65535.0;
    py = ((double) Input[1] * p->Domain) / 65535.0;
    pz = ((double) Input[2] * p->Domain) / 65535.0;

    x0 = (int) _cmsQuickFloor(px); fx = (px - (double) x0);
    y0 = (int) _cmsQuickFloor(py); fy = (py - (double) y0);
    z0 = (int) _cmsQuickFloor(pz); fz = (pz - (double) z0);


    x1 = x0 + (Input[0] != 0xFFFFU ? 1 : 0);
    y1 = y0 + (Input[1] != 0xFFFFU ? 1 : 0);
    z1 = z0 + (Input[2] != 0xFFFFU ? 1 : 0);


    for (OutChan=0; OutChan < TotalOut; OutChan++)
    {

       // These are the 6 Tetrahedral

       if (fx >= fy && fy >= fz)
       {
              c1 = DENS(x1, y0, z0) - DENS(x0, y0, z0);
              c2 = DENS(x1, y1, z0) - DENS(x1, y0, z0);
              c3 = DENS(x1, y1, z1) - DENS(x1, y1, z0);
       }
       else
       if (fx >= fz && fz >= fy)
       {
              c1 = DENS(x1, y0, z0) - DENS(x0, y0, z0);
              c2 = DENS(x1, y1, z1) - DENS(x1, y0, z1);
              c3 = DENS(x1, y0, z1) - DENS(x1, y0, z0);
       }
       else
       if (fz >= fx && fx >= fy)
       {
              c1 = DENS(x1, y0, z1) - DENS(x0, y0, z1);
              c2 = DENS(x1, y1, z1) - DENS(x1, y0, z1);
              c3 = DENS(x0, y0, z1) - DENS(x0, y0, z0);
       }
       else
       if (fy >= fx && fx >= fz)
       {
              c1 = DENS(x1, y1, z0) - DENS(x0, y1, z0);
              c2 = DENS(x0, y1, z0) - DENS(x0, y0, z0);
              c3 = DENS(x1, y1, z1) - DENS(x1, y1, z0);

       }
       else
       if (fy >= fz && fz >= fx)
       {
              c1 = DENS(x1, y1, z1) - DENS(x0, y1, z1);
              c2 = DENS(x0, y1, z0) - DENS(x0, y0, z0);
              c3 = DENS(x0, y1, z1) - DENS(x0, y1, z0);
       }
       else
       if (fz >= fy && fy >= fx)
       {
              c1 = DENS(x1, y1, z1) - DENS(x0, y1, z1);
              c2 = DENS(x0, y1, z1) - DENS(x0, y0, z1);
              c3 = DENS(x0, y0, z1) - DENS(x0, y0, z0);
       }
       else
       {
         c1 = c2 = c3 = 0;
       //  assert(FALSE);
       }


       Output[OutChan] = (WORD) floor((double) DENS(x0,y0,z0) + c1 * fx + c2 * fy + c3 * fz + .5);
       }

}

#undef DENS

#else

#define DENS(i,j,k) (LutTable[(i)+(j)+(k)+OutChan])


void cmsTetrahedralInterp16(WORD Input[],
                            WORD Output[],
                            WORD LutTable1[],
                            LPL16PARAMS p)
{

       Fixed32    fx, fy, fz;
       Fixed32    rx, ry, rz;
       int        x0, y0, z0;
       Fixed32    c0, c1, c2, c3, Rest;
       int        OutChan;
       Fixed32    X0, X1, Y0, Y1, Z0, Z1;
       int        TotalOut = p -> nOutputs;
       register   LPWORD LutTable = LutTable1;



    fx  = ToFixedDomain((int) Input[0] * p -> Domain);
    fy  = ToFixedDomain((int) Input[1] * p -> Domain);
    fz  = ToFixedDomain((int) Input[2] * p -> Domain);

    x0  = FIXED_TO_INT(fx);
    y0  = FIXED_TO_INT(fy);
    z0  = FIXED_TO_INT(fz);

    rx  = FIXED_REST_TO_INT(fx);
    ry  = FIXED_REST_TO_INT(fy);
    rz  = FIXED_REST_TO_INT(fz);

    X0 = p -> opta3 * x0;
    X1 = X0 + (Input[0] == 0xFFFFU ? 0 : p->opta3);

    Y0 = p -> opta2 * y0;
    Y1 = Y0 + (Input[1] == 0xFFFFU ? 0 : p->opta2);

    Z0 = p -> opta1 * z0;
    Z1 = Z0 + (Input[2] == 0xFFFFU ? 0 : p->opta1);



    // These are the 6 Tetrahedral
    for (OutChan=0; OutChan < TotalOut; OutChan++) {

       c0 = DENS(X0, Y0, Z0);

       if (rx >= ry && ry >= rz) {

              c1 = DENS(X1, Y0, Z0) - c0;
              c2 = DENS(X1, Y1, Z0) - DENS(X1, Y0, Z0);
              c3 = DENS(X1, Y1, Z1) - DENS(X1, Y1, Z0);

       }
       else
       if (rx >= rz && rz >= ry) {

              c1 = DENS(X1, Y0, Z0) - c0;
              c2 = DENS(X1, Y1, Z1) - DENS(X1, Y0, Z1);
              c3 = DENS(X1, Y0, Z1) - DENS(X1, Y0, Z0);

       }
       else
       if (rz >= rx && rx >= ry) {

              c1 = DENS(X1, Y0, Z1) - DENS(X0, Y0, Z1);
              c2 = DENS(X1, Y1, Z1) - DENS(X1, Y0, Z1);
              c3 = DENS(X0, Y0, Z1) - c0;

       }
       else
       if (ry >= rx && rx >= rz) {

              c1 = DENS(X1, Y1, Z0) - DENS(X0, Y1, Z0);
              c2 = DENS(X0, Y1, Z0) - c0;
              c3 = DENS(X1, Y1, Z1) - DENS(X1, Y1, Z0);

       }
       else
       if (ry >= rz && rz >= rx) {

              c1 = DENS(X1, Y1, Z1) - DENS(X0, Y1, Z1);
              c2 = DENS(X0, Y1, Z0) - c0;
              c3 = DENS(X0, Y1, Z1) - DENS(X0, Y1, Z0);

       }
       else
       if (rz >= ry && ry >= rx) {

              c1 = DENS(X1, Y1, Z1) - DENS(X0, Y1, Z1);
              c2 = DENS(X0, Y1, Z1) - DENS(X0, Y0, Z1);
              c3 = DENS(X0, Y0, Z1) - c0;

       }
       else  {
              c1 = c2 = c3 = 0;
              // assert(FALSE);
       }

        Rest = c1 * rx + c2 * ry + c3 * rz;

        // There is a lot of math hidden in this expression. The rest is in fixed domain
        // and the result in 0..ffff domain. So the complete expression should be
        // ROUND_FIXED_TO_INT(ToFixedDomain(Rest)) But that can be optimized as (Rest + 0x7FFF) / 0xFFFF

        Output[OutChan] = (WORD) (c0 + ((Rest + 0x7FFF) / 0xFFFF));

    }

}



#undef DENS

#endif


// A optimized interpolation for 8-bit input.

#define DENS(i,j,k) (LutTable[(i)+(j)+(k)+OutChan])

void cmsTetrahedralInterp8(WORD Input[],
                           WORD Output[],
                           WORD LutTable[],
                           LPL16PARAMS p)
{

       int        r, g, b;
       Fixed32    rx, ry, rz;
       Fixed32    c1, c2, c3, Rest;
       int        OutChan;
       register   Fixed32    X0, X1, Y0, Y1, Z0, Z1;
       int        TotalOut = p -> nOutputs;
       register   LPL8PARAMS p8 = p ->p8;



    r = Input[0] >> 8;
    g = Input[1] >> 8;
    b = Input[2] >> 8;

    X0 = X1 = p8->X0[r];
    Y0 = Y1 = p8->Y0[g];
    Z0 = Z1 = p8->Z0[b];

    X1 += (r == 255) ? 0 : p ->opta3;
    Y1 += (g == 255) ? 0 : p ->opta2;
    Z1 += (b == 255) ? 0 : p ->opta1;

    rx = p8 ->rx[r];
    ry = p8 ->ry[g];
    rz = p8 ->rz[b];


    // These are the 6 Tetrahedral
    for (OutChan=0; OutChan < TotalOut; OutChan++) {

       if (rx >= ry && ry >= rz)
       {

              c1 = DENS(X1, Y0, Z0) - DENS(X0, Y0, Z0);
              c2 = DENS(X1, Y1, Z0) - DENS(X1, Y0, Z0);
              c3 = DENS(X1, Y1, Z1) - DENS(X1, Y1, Z0);

       }
       else
       if (rx >= rz && rz >= ry)
       {
              c1 = DENS(X1, Y0, Z0) - DENS(X0, Y0, Z0);
              c2 = DENS(X1, Y1, Z1) - DENS(X1, Y0, Z1);
              c3 = DENS(X1, Y0, Z1) - DENS(X1, Y0, Z0);

       }
       else
       if (rz >= rx && rx >= ry)
       {

              c1 = DENS(X1, Y0, Z1) - DENS(X0, Y0, Z1);
              c2 = DENS(X1, Y1, Z1) - DENS(X1, Y0, Z1);
              c3 = DENS(X0, Y0, Z1) - DENS(X0, Y0, Z0);

       }
       else
       if (ry >= rx && rx >= rz)
       {

              c1 = DENS(X1, Y1, Z0) - DENS(X0, Y1, Z0);
              c2 = DENS(X0, Y1, Z0) - DENS(X0, Y0, Z0);
              c3 = DENS(X1, Y1, Z1) - DENS(X1, Y1, Z0);

       }
       else
       if (ry >= rz && rz >= rx)
       {

              c1 = DENS(X1, Y1, Z1) - DENS(X0, Y1, Z1);
              c2 = DENS(X0, Y1, Z0) - DENS(X0, Y0, Z0);
              c3 = DENS(X0, Y1, Z1) - DENS(X0, Y1, Z0);

       }
       else
       if (rz >= ry && ry >= rx)
       {
              c1 = DENS(X1, Y1, Z1) - DENS(X0, Y1, Z1);
              c2 = DENS(X0, Y1, Z1) - DENS(X0, Y0, Z1);
              c3 = DENS(X0, Y0, Z1) - DENS(X0, Y0, Z0);

       }
       else  {
              c1 = c2 = c3 = 0;
              // assert(FALSE);
       }


        Rest = c1 * rx + c2 * ry + c3 * rz;

        Output[OutChan] = (WORD) (DENS(X0,Y0,Z0) + ((Rest + 0x7FFF) / 0xFFFF));
    }

}

#undef DENS

