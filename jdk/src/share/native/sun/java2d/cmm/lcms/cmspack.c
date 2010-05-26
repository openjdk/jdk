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


#include "lcms.h"

// This module handles all formats supported by lcms


// ---------------------------------------------------------------------------


// This macro return words stored as big endian

#define CHANGE_ENDIAN(w)    (WORD) ((WORD) ((w)<<8)|((w)>>8))

// These macros handles reversing (negative)

#define REVERSE_FLAVOR_8(x)     ((BYTE) (0xff-(x)))
#define REVERSE_FLAVOR_16(x)    ((WORD)(0xffff-(x)))

// Supress waning about info never being used

#ifdef __BORLANDC__
#pragma warn -par
#endif

#ifdef _MSC_VER
#pragma warning(disable : 4100)
#endif

// -------------------------------------------------------- Unpacking routines.


static
LPBYTE UnrollAnyBytes(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       int nChan = T_CHANNELS(info -> InputFormat);
       register int i;

       for (i=0; i < nChan; i++) {

              wIn[i] = RGB_8_TO_16(*accum); accum++;
       }

       return accum + T_EXTRA(info -> InputFormat);
}



static
LPBYTE Unroll4Bytes(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = RGB_8_TO_16(*accum); accum++; // C
       wIn[1] = RGB_8_TO_16(*accum); accum++; // M
       wIn[2] = RGB_8_TO_16(*accum); accum++; // Y
       wIn[3] = RGB_8_TO_16(*accum); accum++; // K

       return accum;
}

static
LPBYTE Unroll4BytesReverse(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = RGB_8_TO_16(REVERSE_FLAVOR_8(*accum)); accum++; // C
       wIn[1] = RGB_8_TO_16(REVERSE_FLAVOR_8(*accum)); accum++; // M
       wIn[2] = RGB_8_TO_16(REVERSE_FLAVOR_8(*accum)); accum++; // Y
       wIn[3] = RGB_8_TO_16(REVERSE_FLAVOR_8(*accum)); accum++; // K

       return accum;
}


static
LPBYTE Unroll4BytesSwapFirst(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{

       wIn[3] = RGB_8_TO_16(*accum); accum++; // K
       wIn[0] = RGB_8_TO_16(*accum); accum++; // C
       wIn[1] = RGB_8_TO_16(*accum); accum++; // M
       wIn[2] = RGB_8_TO_16(*accum); accum++; // Y


       return accum;
}



// KYMC
static
LPBYTE Unroll4BytesSwap(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[3] = RGB_8_TO_16(*accum); accum++;  // K
       wIn[2] = RGB_8_TO_16(*accum); accum++;  // Y
       wIn[1] = RGB_8_TO_16(*accum); accum++;  // M
       wIn[0] = RGB_8_TO_16(*accum); accum++;  // C

       return accum;
}


static
LPBYTE Unroll4BytesSwapSwapFirst(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[2] = RGB_8_TO_16(*accum); accum++;  // K
       wIn[1] = RGB_8_TO_16(*accum); accum++;  // Y
       wIn[0] = RGB_8_TO_16(*accum); accum++;  // M
       wIn[3] = RGB_8_TO_16(*accum); accum++;  // C

       return accum;
}


static
LPBYTE UnrollAnyWords(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
     int nChan = T_CHANNELS(info -> InputFormat);
     register int i;

     for (i=0; i < nChan; i++) {

              wIn[i] = *(LPWORD) accum; accum += 2;
     }

     return accum + T_EXTRA(info -> InputFormat) * sizeof(WORD);
}


static
LPBYTE Unroll4Words(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = *(LPWORD) accum; accum+= 2; // C
       wIn[1] = *(LPWORD) accum; accum+= 2; // M
       wIn[2] = *(LPWORD) accum; accum+= 2; // Y
       wIn[3] = *(LPWORD) accum; accum+= 2; // K

       return accum;
}

static
LPBYTE Unroll4WordsReverse(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = REVERSE_FLAVOR_16(*(LPWORD) accum); accum+= 2; // C
       wIn[1] = REVERSE_FLAVOR_16(*(LPWORD) accum); accum+= 2; // M
       wIn[2] = REVERSE_FLAVOR_16(*(LPWORD) accum); accum+= 2; // Y
       wIn[3] = REVERSE_FLAVOR_16(*(LPWORD) accum); accum+= 2; // K

       return accum;
}


static
LPBYTE Unroll4WordsSwapFirst(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[3] = *(LPWORD) accum; accum+= 2; // K
       wIn[0] = *(LPWORD) accum; accum+= 2; // C
       wIn[1] = *(LPWORD) accum; accum+= 2; // M
       wIn[2] = *(LPWORD) accum; accum+= 2; // Y

       return accum;
}


// KYMC
static
LPBYTE Unroll4WordsSwap(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[3] = *(LPWORD) accum; accum+= 2; // K
       wIn[2] = *(LPWORD) accum; accum+= 2; // Y
       wIn[1] = *(LPWORD) accum; accum+= 2; // M
       wIn[0] = *(LPWORD) accum; accum+= 2; // C

       return accum;
}

static
LPBYTE Unroll4WordsSwapSwapFirst(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[2] = *(LPWORD) accum; accum+= 2; // K
       wIn[1] = *(LPWORD) accum; accum+= 2; // Y
       wIn[0] = *(LPWORD) accum; accum+= 2; // M
       wIn[3] = *(LPWORD) accum; accum+= 2; // C

       return accum;
}


static
LPBYTE Unroll4WordsBigEndian(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2; //C
       wIn[1] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2; //M
       wIn[2] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2; //Y
       wIn[3] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2; //K

       return accum;
}

static
LPBYTE Unroll4WordsBigEndianReverse(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = REVERSE_FLAVOR_16(CHANGE_ENDIAN(*(LPWORD) accum)); accum+= 2; //C
       wIn[1] = REVERSE_FLAVOR_16(CHANGE_ENDIAN(*(LPWORD) accum)); accum+= 2; //M
       wIn[2] = REVERSE_FLAVOR_16(CHANGE_ENDIAN(*(LPWORD) accum)); accum+= 2; //Y
       wIn[3] = REVERSE_FLAVOR_16(CHANGE_ENDIAN(*(LPWORD) accum)); accum+= 2; //K

       return accum;
}


// KYMC
static
LPBYTE Unroll4WordsSwapBigEndian(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[3] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2; //K
       wIn[2] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2; //Y
       wIn[1] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2; //M
       wIn[0] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2; //C

       return accum;
}

static
LPBYTE Unroll3Bytes(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{

       wIn[0] = RGB_8_TO_16(*accum); accum++;     // R
       wIn[1] = RGB_8_TO_16(*accum); accum++;     // G
       wIn[2] = RGB_8_TO_16(*accum); accum++;     // B

       return accum;
}


// Lab8 encoding using v2 PCS

static
LPBYTE Unroll3BytesLab(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{

       wIn[0] = (WORD) ((*accum) << 8); accum++;
       wIn[1] = (WORD) ((*accum) << 8); accum++;
       wIn[2] = (WORD) ((*accum) << 8); accum++;

       return accum;
}


// BRG

static
LPBYTE Unroll3BytesSwap(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{

       wIn[2] = RGB_8_TO_16(*accum); accum++;     // B
       wIn[1] = RGB_8_TO_16(*accum); accum++;     // G
       wIn[0] = RGB_8_TO_16(*accum); accum++;     // R

       return accum;
}

static
LPBYTE Unroll3Words(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = *(LPWORD) accum; accum+= 2;  // C R
       wIn[1] = *(LPWORD) accum; accum+= 2;  // M G
       wIn[2] = *(LPWORD) accum; accum+= 2;  // Y B
       return accum;
}


static
LPBYTE Unroll3WordsSwap(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[2] = *(LPWORD) accum; accum+= 2;  // C R
       wIn[1] = *(LPWORD) accum; accum+= 2;  // M G
       wIn[0] = *(LPWORD) accum; accum+= 2;  // Y B
       return accum;
}


static
LPBYTE Unroll3WordsBigEndian(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2;
       wIn[1] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2;
       wIn[2] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2;
       return accum;
}


static
LPBYTE Unroll3WordsSwapBigEndian(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[2] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2;
       wIn[1] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2;
       wIn[0] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2;
       return accum;
}



// Monochrome duplicates L into RGB for null-transforms

static
LPBYTE Unroll1Byte(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = wIn[1] = wIn[2] = RGB_8_TO_16(*accum); accum++;     // L
       return accum;
}


static
LPBYTE Unroll1ByteSkip2(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = wIn[1] = wIn[2] = RGB_8_TO_16(*accum); accum++;     // L
       accum += 2;
       return accum;
}

static
LPBYTE Unroll1ByteReversed(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = wIn[1] = wIn[2] = REVERSE_FLAVOR_16(RGB_8_TO_16(*accum)); accum++;     // L
       return accum;
}


static
LPBYTE Unroll1Word(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = wIn[1] = wIn[2] = *(LPWORD) accum; accum+= 2;   // L
       return accum;
}

static
LPBYTE Unroll1WordReversed(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = wIn[1] = wIn[2] = REVERSE_FLAVOR_16(*(LPWORD) accum); accum+= 2;
       return accum;
}


static
LPBYTE Unroll1WordBigEndian(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = wIn[1] = wIn[2] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2;
       return accum;
}

static
LPBYTE Unroll1WordSkip3(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = wIn[1] = wIn[2] = *(LPWORD) accum;

       accum += 8;
       return accum;
}


// Monochrome + alpha. Alpha is lost

static
LPBYTE Unroll2Byte(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = wIn[1] = wIn[2] = RGB_8_TO_16(*accum); accum++;     // L
       wIn[3] = RGB_8_TO_16(*accum); accum++;                       // alpha
       return accum;
}

static
LPBYTE Unroll2ByteSwapFirst(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[3] = RGB_8_TO_16(*accum); accum++;                       // alpha
       wIn[0] = wIn[1] = wIn[2] = RGB_8_TO_16(*accum); accum++;     // L
       return accum;
}


static
LPBYTE Unroll2Word(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = wIn[1] = wIn[2] = *(LPWORD) accum; accum+= 2;   // L
       wIn[3] = *(LPWORD) accum; accum += 2;                    // alpha

       return accum;
}


static
LPBYTE Unroll2WordSwapFirst(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[3] = *(LPWORD) accum; accum += 2;                    // alpha
       wIn[0] = wIn[1] = wIn[2] = *(LPWORD) accum; accum+= 2;   // L

       return accum;
}

static
LPBYTE Unroll2WordBigEndian(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       wIn[0] = wIn[1] = wIn[2] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2;
       wIn[3] = CHANGE_ENDIAN(*(LPWORD) accum); accum+= 2;

       return accum;
}




static
LPBYTE UnrollPlanarBytes(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       int nChan = T_CHANNELS(info -> InputFormat);
       register int i;
       LPBYTE Init = accum;

       for (i=0; i < nChan; i++) {

              wIn[i] = RGB_8_TO_16(*accum);
              accum += info -> StrideIn;
       }

       return (Init + 1);
}



static
LPBYTE UnrollPlanarWords(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       int nChan = T_CHANNELS(info -> InputFormat);
       register int i;
       LPBYTE Init = accum;

       for (i=0; i < nChan; i++) {

              wIn[i] = *(LPWORD) accum;
              accum += (info -> StrideIn * sizeof(WORD));
       }

       return (Init + sizeof(WORD));
}



static
LPBYTE UnrollPlanarWordsBigEndian(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
       int nChan = T_CHANNELS(info -> InputFormat);
       register int i;
       LPBYTE Init = accum;

       for (i=0; i < nChan; i++) {

              wIn[i] = CHANGE_ENDIAN(*(LPWORD) accum);
              accum += (info -> StrideIn * sizeof(WORD));
       }

       return (Init + sizeof(WORD));
}


// floating point
static
LPBYTE UnrollLabDouble(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{

    if (T_PLANAR(info -> InputFormat)) {

        double* Pt = (double*) accum;

        cmsCIELab Lab;

        Lab.L = Pt[0];
        Lab.a = Pt[info->StrideIn];
        Lab.b = Pt[info->StrideIn*2];

        if (info ->lInputV4Lab)
            cmsFloat2LabEncoded4(wIn, &Lab);
        else
            cmsFloat2LabEncoded(wIn, &Lab);

        return accum + sizeof(double);
    }
    else {

        if (info ->lInputV4Lab)
            cmsFloat2LabEncoded4(wIn, (LPcmsCIELab) accum);
        else
            cmsFloat2LabEncoded(wIn, (LPcmsCIELab) accum);

        accum += sizeof(cmsCIELab);

        return accum;
    }
}

static
LPBYTE UnrollXYZDouble(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
    if (T_PLANAR(info -> InputFormat)) {

        double* Pt = (double*) accum;
        cmsCIEXYZ XYZ;

        XYZ.X = Pt[0];
        XYZ.Y = Pt[info->StrideIn];
        XYZ.Z = Pt[info->StrideIn*2];
        cmsFloat2XYZEncoded(wIn, &XYZ);

        return accum + sizeof(double);

    }

    else {


        cmsFloat2XYZEncoded(wIn, (LPcmsCIEXYZ) accum);
        accum += sizeof(cmsCIEXYZ);

        return accum;
    }
}



// Inks does come in percentage
static
LPBYTE UnrollInkDouble(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
    double* Inks = (double*) accum;
    int nChan  = T_CHANNELS(info -> InputFormat);
    int Planar = T_PLANAR(info -> InputFormat);
    int i;
    double v;

    for (i=0; i <  nChan; i++) {

        if (Planar)

            v = Inks[i * info ->StrideIn];
        else
            v = Inks[i];

        v = floor(v * 655.35 + 0.5);

        if (v > 65535.0) v = 65535.0;
        if (v < 0) v = 0;

        wIn[i] = (WORD) v;
    }

    if (T_PLANAR(info -> InputFormat))
        return accum + sizeof(double);
    else
        return accum + (nChan + T_EXTRA(info ->InputFormat)) * sizeof(double);
}


// Remaining cases are between 0..1.0
static
LPBYTE UnrollDouble(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
    double* Inks = (double*) accum;
    int nChan = T_CHANNELS(info -> InputFormat);
    int Planar = T_PLANAR(info -> InputFormat);
    int i;
    double v;

    for (i=0; i < nChan; i++) {

        if (Planar)

            v = Inks[i * info ->StrideIn];
        else
            v = Inks[i];

        v = floor(v * 65535.0 + 0.5);

        if (v > 65535.0) v = 65535.0;
        if (v < 0) v = 0;

        wIn[i] = (WORD) v;
    }

    if (T_PLANAR(info -> InputFormat))
        return accum + sizeof(double);
    else
        return accum + (nChan + T_EXTRA(info ->InputFormat)) * sizeof(double);
}



static
LPBYTE UnrollDouble1Chan(register _LPcmsTRANSFORM info, register WORD wIn[], register LPBYTE accum)
{
    double* Inks = (double*) accum;
    double v;


    v = floor(Inks[0] * 65535.0 + 0.5);

    if (v > 65535.0) v = 65535.0;
    if (v < 0) v = 0;


    wIn[0] = wIn[1] = wIn[2] = (WORD) v;

    return accum + sizeof(double);
}


// ----------------------------------------------------------- Packing routines


// Generic N-bytes plus dither 16-to-8 conversion. Currently is just a quick hack

static int err[MAXCHANNELS];

static
LPBYTE PackNBytesDither(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       int nChan  = T_CHANNELS(info -> OutputFormat);
       register int i;
       unsigned int n, pe, pf;

       for (i=0; i < nChan;  i++) {

              n = wOut[i] + err[i]; // Value

              pe = (n / 257);       // Whole part
              pf = (n % 257);       // Fractional part

              err[i] = pf;          // Store it for next pixel

              *output++ = (BYTE) pe;
       }

       return output + T_EXTRA(info ->OutputFormat);
}



static
LPBYTE PackNBytesSwapDither(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       int nChan  = T_CHANNELS(info -> OutputFormat);
       register int i;
       unsigned int n, pe, pf;

       for (i=nChan-1; i >= 0;  --i) {

              n = wOut[i] + err[i];     // Value

              pe = (n / 257);           // Whole part
              pf = (n % 257);           // Fractional part

              err[i] = pf;              // Store it for next pixel

              *output++ = (BYTE) pe;
       }


       return output + T_EXTRA(info ->OutputFormat);
}



// Generic chunky for byte

static
LPBYTE PackNBytes(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       int nChan  = T_CHANNELS(info -> OutputFormat);
       register int i;

       for (i=0; i < nChan;  i++)
              *output++ = RGB_16_TO_8(wOut[i]);

       return output + T_EXTRA(info ->OutputFormat);
}

// Chunky reversed order bytes

static
LPBYTE PackNBytesSwap(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       int nChan  = T_CHANNELS(info -> OutputFormat);
       register int i;

       for (i=nChan-1; i >= 0;  --i)
              *output++ = RGB_16_TO_8(wOut[i]);

       return output + T_EXTRA(info ->OutputFormat);

}


static
LPBYTE PackNWords(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       int nChan  = T_CHANNELS(info -> OutputFormat);
       register int i;

       for (i=0; i < nChan; i++) {
              *(LPWORD) output = wOut[i];
              output += sizeof(WORD);
       }

       return output + T_EXTRA(info ->OutputFormat) * sizeof(WORD);
}

static
LPBYTE PackNWordsSwap(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       int nChan  = T_CHANNELS(info -> OutputFormat);
       register int i;

       for (i=nChan-1; i >= 0; --i) {
              *(LPWORD) output = wOut[i];
              output += sizeof(WORD);
       }

       return output + T_EXTRA(info ->OutputFormat) * sizeof(WORD);
}



static
LPBYTE PackNWordsBigEndian(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       int nChan  = T_CHANNELS(info -> OutputFormat);
       register int i;

       for (i=0; i < nChan; i++) {
              *(LPWORD) output = CHANGE_ENDIAN(wOut[i]);
              output += sizeof(WORD);
       }

       return output + T_EXTRA(info ->OutputFormat) * sizeof(WORD);
}


static
LPBYTE PackNWordsSwapBigEndian(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       int nChan  = T_CHANNELS(info -> OutputFormat);
       register int i;

       for (i=nChan-1; i >= 0; --i) {
              *(LPWORD) output = CHANGE_ENDIAN(wOut[i]);
              output += sizeof(WORD);
       }

       return output + T_EXTRA(info ->OutputFormat) * sizeof(WORD);
}


static
LPBYTE PackPlanarBytes(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       int nChan = T_CHANNELS(info -> OutputFormat);
       register int i;
       LPBYTE Init = output;

       for (i=0; i < nChan; i++) {

              *(LPBYTE) output = RGB_16_TO_8(wOut[i]);
              output += info -> StrideOut;
       }

       return (Init + 1);
}


static
LPBYTE PackPlanarWords(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       int nChan = T_CHANNELS(info -> OutputFormat);
       register int i;
       LPBYTE Init = output;

       for (i=0; i < nChan; i++) {

              *(LPWORD) output = wOut[i];
              output += (info -> StrideOut * sizeof(WORD));
       }

       return (Init + 2);
}


// CMYKcm (unrolled for speed)

static
LPBYTE Pack6Bytes(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
         *output++ = RGB_16_TO_8(wOut[0]);
         *output++ = RGB_16_TO_8(wOut[1]);
         *output++ = RGB_16_TO_8(wOut[2]);
         *output++ = RGB_16_TO_8(wOut[3]);
         *output++ = RGB_16_TO_8(wOut[4]);
         *output++ = RGB_16_TO_8(wOut[5]);

         return output;
}

// KCMYcm

static
LPBYTE Pack6BytesSwap(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *output++ = RGB_16_TO_8(wOut[3]);
       *output++ = RGB_16_TO_8(wOut[0]);
       *output++ = RGB_16_TO_8(wOut[1]);
       *output++ = RGB_16_TO_8(wOut[2]);
       *output++ = RGB_16_TO_8(wOut[4]);
       *output++ = RGB_16_TO_8(wOut[5]);

       return output;
}

// CMYKcm
static
LPBYTE Pack6Words(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = wOut[0];
       output+= 2;
       *(LPWORD) output = wOut[1];
       output+= 2;
       *(LPWORD) output = wOut[2];
       output+= 2;
       *(LPWORD) output = wOut[3];
       output+= 2;
       *(LPWORD) output = wOut[4];
       output+= 2;
       *(LPWORD) output = wOut[5];
       output+= 2;

       return output;
}

// KCMYcm
static
LPBYTE Pack6WordsSwap(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = wOut[3];
       output+= 2;
       *(LPWORD) output = wOut[0];
       output+= 2;
       *(LPWORD) output = wOut[1];
       output+= 2;
       *(LPWORD) output = wOut[2];
       output+= 2;
       *(LPWORD) output = wOut[4];
       output+= 2;
       *(LPWORD) output = wOut[5];
       output+= 2;

       return output;
}

// CMYKcm
static
LPBYTE Pack6WordsBigEndian(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = CHANGE_ENDIAN(wOut[0]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[1]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[2]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[3]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[4]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[5]);
       output+= 2;

       return output;
}

// KCMYcm
static
LPBYTE Pack6WordsSwapBigEndian(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = CHANGE_ENDIAN(wOut[3]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[0]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[1]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[2]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[4]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[5]);
       output+= 2;

       return output;
}


static
LPBYTE Pack4Bytes(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
         *output++ = RGB_16_TO_8(wOut[0]);
         *output++ = RGB_16_TO_8(wOut[1]);
         *output++ = RGB_16_TO_8(wOut[2]);
         *output++ = RGB_16_TO_8(wOut[3]);

         return output;
}

static
LPBYTE Pack4BytesReverse(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
         *output++ = REVERSE_FLAVOR_8(RGB_16_TO_8(wOut[0]));
         *output++ = REVERSE_FLAVOR_8(RGB_16_TO_8(wOut[1]));
         *output++ = REVERSE_FLAVOR_8(RGB_16_TO_8(wOut[2]));
         *output++ = REVERSE_FLAVOR_8(RGB_16_TO_8(wOut[3]));

         return output;
}


static
LPBYTE Pack4BytesSwapFirst(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
         *output++ = RGB_16_TO_8(wOut[3]);
         *output++ = RGB_16_TO_8(wOut[0]);
         *output++ = RGB_16_TO_8(wOut[1]);
         *output++ = RGB_16_TO_8(wOut[2]);

         return output;
}


// ABGR

static
LPBYTE Pack4BytesSwap(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *output++ = RGB_16_TO_8(wOut[3]);
       *output++ = RGB_16_TO_8(wOut[2]);
       *output++ = RGB_16_TO_8(wOut[1]);
       *output++ = RGB_16_TO_8(wOut[0]);

       return output;
}


static
LPBYTE Pack4BytesSwapSwapFirst(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *output++ = RGB_16_TO_8(wOut[2]);
       *output++ = RGB_16_TO_8(wOut[1]);
       *output++ = RGB_16_TO_8(wOut[0]);
       *output++ = RGB_16_TO_8(wOut[3]);

       return output;
}


static
LPBYTE Pack4Words(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = wOut[0];
       output+= 2;
       *(LPWORD) output = wOut[1];
       output+= 2;
       *(LPWORD) output = wOut[2];
       output+= 2;
       *(LPWORD) output = wOut[3];
       output+= 2;

       return output;
}


static
LPBYTE Pack4WordsReverse(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = REVERSE_FLAVOR_16(wOut[0]);
       output+= 2;
       *(LPWORD) output = REVERSE_FLAVOR_16(wOut[1]);
       output+= 2;
       *(LPWORD) output = REVERSE_FLAVOR_16(wOut[2]);
       output+= 2;
       *(LPWORD) output = REVERSE_FLAVOR_16(wOut[3]);
       output+= 2;

       return output;
}

// ABGR

static
LPBYTE Pack4WordsSwap(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = wOut[3];
       output+= 2;
       *(LPWORD) output = wOut[2];
       output+= 2;
       *(LPWORD) output = wOut[1];
       output+= 2;
       *(LPWORD) output = wOut[0];
       output+= 2;

       return output;
}

// CMYK
static
LPBYTE Pack4WordsBigEndian(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = CHANGE_ENDIAN(wOut[0]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[1]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[2]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[3]);
       output+= 2;

       return output;
}


static
LPBYTE Pack4WordsBigEndianReverse(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = CHANGE_ENDIAN(REVERSE_FLAVOR_16(wOut[0]));
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(REVERSE_FLAVOR_16(wOut[1]));
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(REVERSE_FLAVOR_16(wOut[2]));
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(REVERSE_FLAVOR_16(wOut[3]));
       output+= 2;

       return output;
}

// KYMC

static
LPBYTE Pack4WordsSwapBigEndian(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = CHANGE_ENDIAN(wOut[3]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[2]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[1]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[0]);
       output+= 2;

       return output;
}

static
LPBYTE Pack3Bytes(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *output++ = RGB_16_TO_8(wOut[0]);
       *output++ = RGB_16_TO_8(wOut[1]);
       *output++ = RGB_16_TO_8(wOut[2]);

       return output;
}

static
LPBYTE Pack3BytesLab(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *output++ = (BYTE) (wOut[0] >> 8);
       *output++ = (BYTE) (wOut[1] >> 8);
       *output++ = (BYTE) (wOut[2] >> 8);

       return output;
}


static
LPBYTE Pack3BytesSwap(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *output++ = RGB_16_TO_8(wOut[2]);
       *output++ = RGB_16_TO_8(wOut[1]);
       *output++ = RGB_16_TO_8(wOut[0]);

       return output;
}


static
LPBYTE Pack3Words(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = wOut[0];
       output+= 2;
       *(LPWORD) output = wOut[1];
       output+= 2;
       *(LPWORD) output = wOut[2];
       output+= 2;

       return output;
}

static
LPBYTE Pack3WordsSwap(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = wOut[2];
       output+= 2;
       *(LPWORD) output = wOut[1];
       output+= 2;
       *(LPWORD) output = wOut[0];
       output+= 2;

       return output;
}

static
LPBYTE Pack3WordsBigEndian(register _LPcmsTRANSFORM info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = CHANGE_ENDIAN(wOut[0]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[1]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[2]);
       output+= 2;

       return output;
}


static
LPBYTE Pack3WordsSwapBigEndian(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = CHANGE_ENDIAN(wOut[2]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[1]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[0]);
       output+= 2;

       return output;
}


static
LPBYTE Pack3BytesAndSkip1(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *output++ = RGB_16_TO_8(wOut[0]);
       *output++ = RGB_16_TO_8(wOut[1]);
       *output++ = RGB_16_TO_8(wOut[2]);
       output++;

       return output;
}


static
LPBYTE Pack3BytesAndSkip1SwapFirst(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
        output++;
       *output++ = RGB_16_TO_8(wOut[0]);
       *output++ = RGB_16_TO_8(wOut[1]);
       *output++ = RGB_16_TO_8(wOut[2]);

       return output;
}

static
LPBYTE Pack3BytesAndSkip1Swap(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
        output++;
       *output++ = RGB_16_TO_8(wOut[2]);
       *output++ = RGB_16_TO_8(wOut[1]);
       *output++ = RGB_16_TO_8(wOut[0]);

       return output;
}


static
LPBYTE Pack3BytesAndSkip1SwapSwapFirst(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *output++ = RGB_16_TO_8(wOut[2]);
       *output++ = RGB_16_TO_8(wOut[1]);
       *output++ = RGB_16_TO_8(wOut[0]);
       output++;

       return output;
}


static
LPBYTE Pack3WordsAndSkip1(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = wOut[0];
       output+= 2;
       *(LPWORD) output = wOut[1];
       output+= 2;
       *(LPWORD) output = wOut[2];
       output+= 2;
       output+= 2;

       return output;
}

static
LPBYTE Pack3WordsAndSkip1Swap(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       output+= 2;
       *(LPWORD) output = wOut[2];
       output+= 2;
       *(LPWORD) output = wOut[1];
       output+= 2;
       *(LPWORD) output = wOut[0];
       output+= 2;


       return output;
}


static
LPBYTE Pack3WordsAndSkip1SwapSwapFirst(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = wOut[2];
       output+= 2;
       *(LPWORD) output = wOut[1];
       output+= 2;
       *(LPWORD) output = wOut[0];
       output+= 2;
       output+= 2;


       return output;
}


static
LPBYTE Pack3WordsAndSkip1BigEndian(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = CHANGE_ENDIAN(wOut[0]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[1]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[2]);
       output+= 2;
       output+= 2;

       return output;
}


static
LPBYTE Pack3WordsAndSkip1SwapBigEndian(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
        output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[2]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[1]);
       output+= 2;
       *(LPWORD) output = CHANGE_ENDIAN(wOut[0]);
       output+= 2;


       return output;
}



static
LPBYTE Pack1Byte(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *output++ = RGB_16_TO_8(wOut[0]);
       return output;
}


static
LPBYTE Pack1ByteAndSkip1(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *output++ = RGB_16_TO_8(wOut[0]);
       output++;
       return output;
}


static
LPBYTE Pack1ByteAndSkip1SwapFirst(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       output++;
       *output++ = RGB_16_TO_8(wOut[0]);

       return output;
}

static
LPBYTE Pack1Word(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = wOut[0];
       output+= 2;

       return output;
}

static
LPBYTE Pack1WordBigEndian(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = CHANGE_ENDIAN(wOut[0]);
       output+= 2;

       return output;
}


static
LPBYTE Pack1WordAndSkip1(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = wOut[0];
       output+= 4;

       return output;
}

static
LPBYTE Pack1WordAndSkip1SwapFirst(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       output += 2;
       *(LPWORD) output = wOut[0];
       output+= 2;

       return output;
}


static
LPBYTE Pack1WordAndSkip1BigEndian(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
       *(LPWORD) output = CHANGE_ENDIAN(wOut[0]);
       output+= 4;

       return output;
}


// Unencoded Float values -- don't try optimize speed

static
LPBYTE PackLabDouble(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{

    if (T_PLANAR(Info -> OutputFormat)) {

        cmsCIELab  Lab;
        double* Out = (double*) output;
        cmsLabEncoded2Float(&Lab, wOut);

        Out[0]                  = Lab.L;
        Out[Info ->StrideOut]   = Lab.a;
        Out[Info ->StrideOut*2] = Lab.b;

        return output + sizeof(double);

    }
    else {

       if (Info ->lOutputV4Lab)
           cmsLabEncoded2Float4((LPcmsCIELab) output, wOut);
       else
           cmsLabEncoded2Float((LPcmsCIELab) output, wOut);

        return output + (sizeof(cmsCIELab) + T_EXTRA(Info ->OutputFormat) * sizeof(double));
    }

}

static
LPBYTE PackXYZDouble(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{

    if (T_PLANAR(Info -> OutputFormat)) {

        cmsCIEXYZ XYZ;
        double* Out = (double*) output;
        cmsXYZEncoded2Float(&XYZ, wOut);

        Out[0]                  = XYZ.X;
        Out[Info ->StrideOut]   = XYZ.Y;
        Out[Info ->StrideOut*2] = XYZ.Z;

        return output + sizeof(double);

    }
    else {

        cmsXYZEncoded2Float((LPcmsCIEXYZ) output, wOut);

        return output + (sizeof(cmsCIEXYZ) + T_EXTRA(Info ->OutputFormat) * sizeof(double));
    }
}



static
LPBYTE PackInkDouble(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
    double* Inks = (double*) output;
    int nChan = T_CHANNELS(Info -> OutputFormat);
    int i;

    if (T_PLANAR(Info -> OutputFormat)) {

        for (i=0; i <  nChan; i++) {

            Inks[i*Info ->StrideOut] = wOut[i] / 655.35;
        }

        return output + sizeof(double);
    }
    else {

        for (i=0; i <  nChan; i++) {

            Inks[i] = wOut[i] /  655.35;
        }


    return output + (nChan + T_EXTRA(Info ->OutputFormat)) * sizeof(double);
    }

}


static
LPBYTE PackDouble(register _LPcmsTRANSFORM Info, register WORD wOut[], register LPBYTE output)
{
    double* Inks = (double*) output;
    int nChan = T_CHANNELS(Info -> OutputFormat);
    int i;


    if (T_PLANAR(Info -> OutputFormat)) {

        for (i=0; i <  nChan; i++) {

            Inks[i*Info ->StrideOut] = wOut[i] / 65535.0;
        }

        return output + sizeof(double);

    }
    else {
        for (i=0; i <  nChan; i++) {

            Inks[i] = wOut[i] /  65535.0;
        }

        return output + (nChan + T_EXTRA(Info ->OutputFormat)) * sizeof(double);
    }

}


//  choose routine from Input identifier

_cmsFIXFN _cmsIdentifyInputFormat(_LPcmsTRANSFORM xform, DWORD dwInput)
{
       _cmsFIXFN FromInput = NULL;


       // Check Named Color

       if (xform) {

           if (xform ->InputProfile) {

           if (cmsGetDeviceClass(xform ->InputProfile) == icSigNamedColorClass) {

                if (dwInput != TYPE_NAMED_COLOR_INDEX) {
                    cmsSignalError(LCMS_ERRC_ABORTED, "Named color needs TYPE_NAMED_COLOR_INDEX");
                    return NULL;
                }
           }

        }
       }

       // Unencoded modes

       if (T_BYTES(dwInput) == 0) {

           switch (T_COLORSPACE(dwInput)) {

           case PT_Lab:
                    FromInput = UnrollLabDouble;
                    break;
           case PT_XYZ:
                    FromInput = UnrollXYZDouble;
                    break;

           // 0.0 .. 1.0 range

           case PT_GRAY:
           case PT_RGB:
           case PT_YCbCr:
           case PT_YUV:
           case PT_YUVK:
           case PT_HSV:
           case PT_HLS:
           case PT_Yxy:
                    if (T_CHANNELS(dwInput) == 1)
                        FromInput = UnrollDouble1Chan;
                    else
                        FromInput = UnrollDouble;
                    break;

            // Inks (%) 0.0 .. 100.0

           default:
                    FromInput = UnrollInkDouble;
                    break;
           }

       }
       else {

           if (T_PLANAR(dwInput)) {

               switch (T_BYTES(dwInput)) {

               case 1:
                   FromInput = UnrollPlanarBytes;
                   break;

               case 2:
                   if (T_ENDIAN16(dwInput))
                       FromInput = UnrollPlanarWordsBigEndian;
                   else
                       FromInput = UnrollPlanarWords;
                   break;

               default:;
               }
       }
       else {

       switch (T_BYTES(dwInput)) {

       case 1: // 1 byte per channel

              switch (T_CHANNELS(dwInput) + T_EXTRA(dwInput)) {

              case 1: if (T_FLAVOR(dwInput))
                                FromInput = Unroll1ByteReversed;
                            else
                                  FromInput = Unroll1Byte;
                      break;

              case 2: if (T_SWAPFIRST(dwInput))
                        FromInput = Unroll2ByteSwapFirst;
                      else
                        FromInput = Unroll2Byte;
                      break;

              case 3: if (T_DOSWAP(dwInput))
                            FromInput = Unroll3BytesSwap;
                      else {
                            if (T_EXTRA(dwInput) == 2)
                                FromInput = Unroll1ByteSkip2;
                            else
                                if (T_COLORSPACE(dwInput) == PT_Lab)
                                    FromInput = Unroll3BytesLab;
                                else
                                    FromInput = Unroll3Bytes;
                      }
                      break;
              case 4:
                      // TODO: ALab8 must be fixed to match v2 encoding

                      if (T_DOSWAP(dwInput)) {
                            if (T_SWAPFIRST(dwInput))

                                FromInput = Unroll4BytesSwapSwapFirst;
                            else
                                FromInput = Unroll4BytesSwap;
                      }
                      else {
                            if (T_SWAPFIRST(dwInput))
                                FromInput = Unroll4BytesSwapFirst;
                            else {
                                if (T_FLAVOR(dwInput))
                                    FromInput = Unroll4BytesReverse;
                                else
                                    FromInput = Unroll4Bytes;
                            }
                      }
                      break;


              case 5:
              case 6:
              case 7:
              case 8:
                   if (!T_DOSWAP(dwInput) && !T_SWAPFIRST(dwInput))
                       FromInput = UnrollAnyBytes;
                   break;


              default:;
              }
              break;


       case 2: // 1 word per channel

              switch (T_CHANNELS(dwInput) + T_EXTRA(dwInput))
              {
              case 1: if (T_ENDIAN16(dwInput))
                            FromInput = Unroll1WordBigEndian;
                      else
                          if (T_FLAVOR(dwInput))
                                FromInput = Unroll1WordReversed;
                            else
                                  FromInput = Unroll1Word;
                      break;

              case 2: if (T_ENDIAN16(dwInput))
                            FromInput = Unroll2WordBigEndian;
                        else {
                          if (T_SWAPFIRST(dwInput))
                              FromInput = Unroll2WordSwapFirst;
                          else
                              FromInput = Unroll2Word;
                        }
                        break;

              case 3: if (T_DOSWAP(dwInput)) {
                            if (T_ENDIAN16(dwInput))
                                   FromInput = Unroll3WordsSwapBigEndian;
                            else
                                   FromInput = Unroll3WordsSwap;
                      }
                      else {
                            if (T_ENDIAN16(dwInput))
                                   FromInput = Unroll3WordsBigEndian;
                            else
                                   FromInput = Unroll3Words;
                      }
                      break;

              case 4: if (T_DOSWAP(dwInput)) {

                            if (T_ENDIAN16(dwInput))
                                   FromInput = Unroll4WordsSwapBigEndian;
                            else {

                                    if (T_SWAPFIRST(dwInput))
                                        FromInput = Unroll4WordsSwapSwapFirst;
                                    else
                                        FromInput = Unroll4WordsSwap;

                            }

                      }
                      else {

                            if (T_EXTRA(dwInput) == 3)
                                    FromInput = Unroll1WordSkip3;
                            else

                                if (T_ENDIAN16(dwInput)) {

                                    if (T_FLAVOR(dwInput))
                                        FromInput = Unroll4WordsBigEndianReverse;
                                    else
                                        FromInput = Unroll4WordsBigEndian;
                                }
                            else {
                                  if (T_SWAPFIRST(dwInput))
                                    FromInput = Unroll4WordsSwapFirst;
                                  else {
                                      if (T_FLAVOR(dwInput))
                                            FromInput = Unroll4WordsReverse;
                                      else
                                            FromInput = Unroll4Words;
                                  }
                            }
                      }
                      break;


              case 5:
              case 6:
              case 7:
              case 8:
                    if (!T_DOSWAP(dwInput) && !T_SWAPFIRST(dwInput))
                       FromInput = UnrollAnyWords;
                    break;

              }
              break;

       default:;
       }
       }
       }


       if (!FromInput)
              cmsSignalError(LCMS_ERRC_ABORTED, "Unknown input format");

       return FromInput;
}

//  choose routine from Input identifier

_cmsFIXFN _cmsIdentifyOutputFormat(_LPcmsTRANSFORM xform, DWORD dwOutput)
{
       _cmsFIXFN ToOutput = NULL;


       if (T_BYTES(dwOutput) == 0) {

           switch (T_COLORSPACE(dwOutput)) {

           case PT_Lab:
                    ToOutput = PackLabDouble;
                    break;
           case PT_XYZ:
                    ToOutput = PackXYZDouble;
                    break;

           // 0.0 .. 1.0 range
           case PT_GRAY:
           case PT_RGB:
           case PT_YCbCr:
           case PT_YUV:
           case PT_YUVK:
           case PT_HSV:
           case PT_HLS:
           case PT_Yxy:
                    ToOutput = PackDouble;
                    break;

            // Inks (%) 0.0 .. 100.0

           default:
                    ToOutput = PackInkDouble;
                    break;
           }

       }
       else

       if (T_PLANAR(dwOutput)) {

       switch (T_BYTES(dwOutput)) {

              case 1: ToOutput = PackPlanarBytes;
                      break;

              case 2:if (!T_ENDIAN16(dwOutput))
                            ToOutput = PackPlanarWords;
                      break;

              default:;
       }
       }
       else {

              switch (T_BYTES(dwOutput)) {

              case 1:
                     switch (T_CHANNELS(dwOutput))
                     {
                     case 1:
                            if (T_DITHER(dwOutput))
                                    ToOutput = PackNBytesDither;
                            else
                            ToOutput = Pack1Byte;
                            if (T_EXTRA(dwOutput) == 1) {
                                if (T_SWAPFIRST(dwOutput))
                                   ToOutput = Pack1ByteAndSkip1SwapFirst;
                                else
                                   ToOutput = Pack1ByteAndSkip1;
                            }
                            break;

                     case 3:
                         switch (T_EXTRA(dwOutput)) {

                         case 0: if (T_DOSWAP(dwOutput))
                                   ToOutput = Pack3BytesSwap;
                                 else
                                     if (T_COLORSPACE(dwOutput) == PT_Lab)
                                        ToOutput = Pack3BytesLab;
                                     else {
                                         if (T_DITHER(dwOutput))
                                                 ToOutput = PackNBytesDither;
                                     else
                                        ToOutput = Pack3Bytes;
                                     }
                             break;

                         case 1:    // TODO: ALab8 should be handled here

                                    if (T_DOSWAP(dwOutput)) {

                                    if (T_SWAPFIRST(dwOutput))
                                        ToOutput = Pack3BytesAndSkip1SwapSwapFirst;
                                    else
                                        ToOutput = Pack3BytesAndSkip1Swap;
                                 }
                             else {
                                   if (T_SWAPFIRST(dwOutput))
                                    ToOutput = Pack3BytesAndSkip1SwapFirst;
                                   else
                                    ToOutput = Pack3BytesAndSkip1;
                             }
                             break;

                         default:;
                         }
                         break;

                     case 4: if (T_EXTRA(dwOutput) == 0) {


                                if (T_DOSWAP(dwOutput)) {


                                     if (T_SWAPFIRST(dwOutput)) {
                                         ToOutput = Pack4BytesSwapSwapFirst;
                                     }
                                     else {

                                           if (T_DITHER(dwOutput)) {
                                                  ToOutput = PackNBytesSwapDither;
                                           }
                                           else {
                                         ToOutput = Pack4BytesSwap;
                                 }
                                     }
                                 }
                                 else {
                                     if (T_SWAPFIRST(dwOutput))
                                         ToOutput = Pack4BytesSwapFirst;
                                     else {

                                         if (T_FLAVOR(dwOutput))
                                             ToOutput = Pack4BytesReverse;
                                         else {
                                             if (T_DITHER(dwOutput))
                                                 ToOutput = PackNBytesDither;
                                         else
                                             ToOutput = Pack4Bytes;
                                     }
                                 }
                             }
                             }
                            else {
                                    if (!T_DOSWAP(dwOutput) && !T_SWAPFIRST(dwOutput))
                                             ToOutput = PackNBytes;
                            }
                            break;

                     // Hexachrome separations.
                     case 6: if (T_EXTRA(dwOutput) == 0) {

                                    if( T_DOSWAP(dwOutput))
                                            ToOutput = Pack6BytesSwap;
                            else
                                   ToOutput = Pack6Bytes;
                            }
                            else {
                                    if (!T_DOSWAP(dwOutput) && !T_SWAPFIRST(dwOutput))
                                             ToOutput = PackNBytes;

                            }
                            break;

                     case 2:
                     case 5:
                     case 7:
                     case 8:
                     case 9:
                     case 10:
                     case 11:
                     case 12:
                     case 13:
                     case 14:
                     case 15:

                            if ((T_EXTRA(dwOutput) == 0) && (T_SWAPFIRST(dwOutput) == 0))
                            {
                                   if (T_DOSWAP(dwOutput))
                                          ToOutput = PackNBytesSwap;
                                   else {

                                       if (T_DITHER(dwOutput))
                                                 ToOutput = PackNBytesDither;
                                   else
                                          ToOutput = PackNBytes;
                                   }
                            }
                            break;

                     default:;
                     }
                     break;


              case 2:

                     switch (T_CHANNELS(dwOutput)) {

                     case 1:
                            if (T_ENDIAN16(dwOutput))

                                   ToOutput = Pack1WordBigEndian;
                            else
                                   ToOutput = Pack1Word;

                            if (T_EXTRA(dwOutput) == 1) {

                               if (T_ENDIAN16(dwOutput))

                                   ToOutput = Pack1WordAndSkip1BigEndian;
                               else {
                                   if (T_SWAPFIRST(dwOutput))
                                      ToOutput = Pack1WordAndSkip1SwapFirst;
                                   else
                                      ToOutput = Pack1WordAndSkip1;
                               }
                            }
                            break;

                     case 3:

                         switch (T_EXTRA(dwOutput)) {

                         case 0:
                               if (T_DOSWAP(dwOutput)) {

                                   if (T_ENDIAN16(dwOutput))

                                          ToOutput = Pack3WordsSwapBigEndian;
                                   else
                                          ToOutput = Pack3WordsSwap;
                               }
                               else {
                                   if (T_ENDIAN16(dwOutput))

                                      ToOutput = Pack3WordsBigEndian;
                                   else
                                      ToOutput = Pack3Words;
                                   }
                             break;

                         case 1: if (T_DOSWAP(dwOutput)) {

                                   if (T_ENDIAN16(dwOutput))

                                          ToOutput = Pack3WordsAndSkip1SwapBigEndian;
                                   else {
                                       if (T_SWAPFIRST(dwOutput))
                                          ToOutput = Pack3WordsAndSkip1SwapSwapFirst;
                                       else
                                          ToOutput = Pack3WordsAndSkip1Swap;
                                   }
                             }
                             else  {
                                   if (T_ENDIAN16(dwOutput))
                                          ToOutput = Pack3WordsAndSkip1BigEndian;
                                   else
                                          ToOutput = Pack3WordsAndSkip1;
                                   }
                         default:;
                         }
                         break;

                     case 4: if (T_EXTRA(dwOutput) == 0) {

                                   if (T_DOSWAP(dwOutput)) {

                                           if (T_ENDIAN16(dwOutput))
                                                 ToOutput = Pack4WordsSwapBigEndian;
                                           else
                                                 ToOutput = Pack4WordsSwap;
                                   }
                                   else {

                                       if (T_ENDIAN16(dwOutput)) {

                                           if (T_FLAVOR(dwOutput))
                                                ToOutput = Pack4WordsBigEndianReverse;
                                           else
                                                ToOutput = Pack4WordsBigEndian;
                                       }
                                       else {
                                          if (T_FLAVOR(dwOutput))
                                              ToOutput = Pack4WordsReverse;
                                          else
                                               ToOutput = Pack4Words;
                                        }
                                   }
                            }
                            else {
                                    if (!T_DOSWAP(dwOutput) && !T_SWAPFIRST(dwOutput))
                                             ToOutput = PackNWords;
                            }
                            break;

                     case 6: if (T_EXTRA(dwOutput) == 0) {

                                   if (T_DOSWAP(dwOutput)) {

                                          if (T_ENDIAN16(dwOutput))
                                                 ToOutput = Pack6WordsSwapBigEndian;
                                          else
                                                 ToOutput = Pack6WordsSwap;
                                   }
                                   else {

                                   if (T_ENDIAN16(dwOutput))
                                          ToOutput = Pack6WordsBigEndian;
                                   else
                                          ToOutput = Pack6Words;
                                   }
                             }
                            else {
                                    if (!T_DOSWAP(dwOutput) && !T_SWAPFIRST(dwOutput))
                                             ToOutput = PackNWords;
                            }
                            break;


                     case 2:
                     case 5:
                     case 7:
                     case 8:
                     case 9:
                     case 10:
                     case 11:
                     case 12:
                     case 13:
                     case 14:
                     case 15: if ((T_EXTRA(dwOutput) == 0) && (T_SWAPFIRST(dwOutput) == 0)) {

                                   if (T_DOSWAP(dwOutput)) {

                                          if (T_ENDIAN16(dwOutput))
                                                 ToOutput = PackNWordsSwapBigEndian;
                                          else
                                                 ToOutput = PackNWordsSwap;
                                   }
                                   else {

                                          if (T_ENDIAN16(dwOutput))
                                                 ToOutput = PackNWordsBigEndian;
                                          else
                                                 ToOutput = PackNWords;
                                          }
                             }
                             break;

                     default:;
                     }
                     break;

              default:;
              }
              }

              if (!ToOutput)
                     cmsSignalError(LCMS_ERRC_ABORTED, "Unknown output format");

              return ToOutput;
}

// User formatters for (weird) cases not already included

void LCMSEXPORT cmsSetUserFormatters(cmsHTRANSFORM hTransform, DWORD dwInput,  cmsFORMATTER Input,
                                                               DWORD dwOutput, cmsFORMATTER Output)
{
    _LPcmsTRANSFORM xform = (_LPcmsTRANSFORM) (LPSTR) hTransform;

    if (Input != NULL) {
        xform ->FromInput = (_cmsFIXFN) Input;
        xform ->InputFormat = dwInput;
    }

    if (Output != NULL) {
        xform ->ToOutput  = (_cmsFIXFN) Output;
        xform ->OutputFormat = dwOutput;
    }

}

void LCMSEXPORT cmsGetUserFormatters(cmsHTRANSFORM hTransform,
                                     LPDWORD InputFormat, cmsFORMATTER* Input,
                                     LPDWORD OutputFormat, cmsFORMATTER* Output)
{
    _LPcmsTRANSFORM xform = (_LPcmsTRANSFORM) (LPSTR) hTransform;

    if (Input)        *Input =  (cmsFORMATTER) xform ->FromInput;
    if (InputFormat)  *InputFormat = xform -> InputFormat;
    if (Output)       *Output = (cmsFORMATTER) xform ->ToOutput;
    if (OutputFormat) *OutputFormat = xform -> OutputFormat;
}


// Change format of yet existing transform. No colorspace checking is performed

void LCMSEXPORT cmsChangeBuffersFormat(cmsHTRANSFORM hTransform,
                                        DWORD dwInputFormat,
                                        DWORD dwOutputFormat)
{

    cmsSetUserFormatters(hTransform,
                        dwInputFormat,
                        (cmsFORMATTER) _cmsIdentifyInputFormat((_LPcmsTRANSFORM) hTransform, dwInputFormat),
                        dwOutputFormat,
                        (cmsFORMATTER) _cmsIdentifyOutputFormat((_LPcmsTRANSFORM) hTransform, dwOutputFormat));
}
