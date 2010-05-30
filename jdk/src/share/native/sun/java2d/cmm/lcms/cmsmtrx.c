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

// Vector & Matrix stuff

#include "lcms.h"


void cdecl VEC3init(LPVEC3 r, double x, double y, double z);
void cdecl VEC3initF(LPWVEC3 r, double x, double y, double z);
void cdecl VEC3toFix(LPWVEC3 r, LPVEC3 v);
void cdecl VEC3scaleFix(LPWORD r, LPWVEC3 Scale);
void cdecl VEC3swap(LPVEC3 a, LPVEC3 b);
void cdecl VEC3divK(LPVEC3 r, LPVEC3 v, double d);
void cdecl VEC3perK(LPVEC3 r, LPVEC3 v, double d);
void cdecl VEC3perComp(LPVEC3 r, LPVEC3 a, LPVEC3 b);
void cdecl VEC3minus(LPVEC3 r, LPVEC3 a, LPVEC3 b);
void cdecl VEC3scaleAndCut(LPWVEC3 r, LPVEC3 v, double d);
void cdecl VEC3cross(LPVEC3 r, LPVEC3 u, LPVEC3 v);
void cdecl VEC3saturate(LPVEC3 v);

double cdecl VEC3length(LPVEC3 a);
double cdecl VEC3distance(LPVEC3 a, LPVEC3 b);


void      cdecl MAT3identity(LPMAT3 a);
void      cdecl MAT3per(LPMAT3 r, LPMAT3 a, LPMAT3 b);
int       cdecl MAT3inverse(LPMAT3 a, LPMAT3 b);
LCMSBOOL  cdecl MAT3solve(LPVEC3 x, LPMAT3 a, LPVEC3 b);
double    cdecl MAT3det(LPMAT3 m);
void      cdecl MAT3eval(LPVEC3 r, LPMAT3 a, LPVEC3 v);
void      cdecl MAT3toFix(LPWMAT3 r, LPMAT3 v);
void      cdecl MAT3evalW(LPWVEC3 r, LPWMAT3 a, LPWVEC3 v);
void      cdecl MAT3perK(LPMAT3 r, LPMAT3 v, double d);
void      cdecl MAT3scaleAndCut(LPWMAT3 r, LPMAT3 v, double d);

// --------------------- Implementation ----------------------------

#define DSWAP(x, y)     {double tmp = (x); (x)=(y); (y)=tmp;}



#ifdef USE_ASSEMBLER


#ifdef _MSC_VER
#pragma warning(disable : 4033)
#pragma warning(disable : 4035)
#endif



Fixed32 FixedMul(Fixed32 a, Fixed32 b)
{
       ASM {

              mov    eax, ss:a
              mov    edx, ss:b
              imul   edx
              add    eax, 0x8000
              adc    edx, 0
              shrd   eax, edx, 16

       }

       RET(_EAX);
}




Fixed32 FixedSquare(Fixed32 a)
{
       ASM {
              pushf
              push   edx
              mov    eax, ss:a
              imul   eax
              add    eax, 0x8000
              adc    edx, 0
              shrd   eax, edx, 16
              sar    eax, 16
              pop    edx
              popf
       }

       RET(_EAX);
}




// Linear intERPolation
// a * (h - l) >> 16 + l

Fixed32 FixedLERP(Fixed32 a, Fixed32 l, Fixed32 h)
{
       ASM {
              mov    eax, dword ptr ss:h
              mov    edx, dword ptr ss:l
              push   edx
              mov    ecx, dword ptr ss:a
              sub    eax, edx
              imul   ecx
              add    eax, 0x8000
              adc    edx, 0
              shrd   eax, edx, 16
              pop    edx
              add    eax, edx
       }

       RET(_EAX);
}


// a as word is scaled by s as float

WORD FixedScale(WORD a, Fixed32 s)
{
       ASM {

              xor    eax,eax
              mov    ax, ss:a        // This is faster that movzx  eax, ss:a
              sal    eax, 16
              mov    edx, ss:s
              mul    edx
              add    eax, 0x8000
              adc    edx, 0
              mov    eax, edx
       }

       RET(_EAX);
}

#ifdef _MSC_VER
#pragma warning(default : 4033)
#pragma warning(default : 4035)
#endif

#else


// These are floating point versions for compilers that doesn't
// support asm at all. Use with care, since this will slow down
// all operations


Fixed32 FixedMul(Fixed32 a, Fixed32 b)
{
#ifdef USE_INT64
       LCMSULONGLONG l = (LCMSULONGLONG) (LCMSSLONGLONG) a * (LCMSULONGLONG) (LCMSSLONGLONG) b + (LCMSULONGLONG) 0x8000;
       l >>= 16;
       return (Fixed32) l;
#else
       return DOUBLE_TO_FIXED(FIXED_TO_DOUBLE(a) * FIXED_TO_DOUBLE(b));
#endif
}

Fixed32 FixedSquare(Fixed32 a)
{
       return FixedMul(a, a);
}


Fixed32 FixedLERP(Fixed32 a, Fixed32 l, Fixed32 h)
{
#ifdef USE_INT64

       LCMSULONGLONG dif = (LCMSULONGLONG) (h - l) * a + 0x8000;
       dif = (dif >> 16) + l;
       return (Fixed32) (dif);
#else
       double dif = h - l;

       dif *= a;
       dif /= 65536.0;
       dif += l;

       return (Fixed32) (dif + 0.5);
#endif

}


WORD FixedScale(WORD a, Fixed32 s)
{
       return (WORD) (a * FIXED_TO_DOUBLE(s));
}

#endif


#ifndef USE_INLINE

Fixed32 ToFixedDomain(int a)
{
    return a + ((a + 0x7fff) / 0xffff);
}


int FromFixedDomain(Fixed32 a)
{
    return a - ((a + 0x7fff) >> 16);
}

#endif



// Initiate a vector (double version)


void VEC3init(LPVEC3 r, double x, double y, double z)
{
       r -> n[VX] = x;
       r -> n[VY] = y;
       r -> n[VZ] = z;
}

// Init a vector (fixed version)

void VEC3initF(LPWVEC3 r, double x, double y, double z)
{
       r -> n[VX] = DOUBLE_TO_FIXED(x);
       r -> n[VY] = DOUBLE_TO_FIXED(y);
       r -> n[VZ] = DOUBLE_TO_FIXED(z);
}


// Convert to fixed point encoding is 1.0 = 0xFFFF

void VEC3toFix(LPWVEC3 r, LPVEC3 v)
{
       r -> n[VX] = DOUBLE_TO_FIXED(v -> n[VX]);
       r -> n[VY] = DOUBLE_TO_FIXED(v -> n[VY]);
       r -> n[VZ] = DOUBLE_TO_FIXED(v -> n[VZ]);
}

// Convert from fixed point

void VEC3fromFix(LPVEC3 r, LPWVEC3 v)
{
       r -> n[VX] = FIXED_TO_DOUBLE(v -> n[VX]);
       r -> n[VY] = FIXED_TO_DOUBLE(v -> n[VY]);
       r -> n[VZ] = FIXED_TO_DOUBLE(v -> n[VZ]);
}


// Swap two double vectors

void VEC3swap(LPVEC3 a, LPVEC3 b)
{
        DSWAP(a-> n[VX], b-> n[VX]);
        DSWAP(a-> n[VY], b-> n[VY]);
        DSWAP(a-> n[VZ], b-> n[VZ]);
}

// Divide a vector by a constant

void VEC3divK(LPVEC3 r, LPVEC3 v, double d)
{
        double d_inv = 1./d;

        r -> n[VX] = v -> n[VX] * d_inv;
        r -> n[VY] = v -> n[VY] * d_inv;
        r -> n[VZ] = v -> n[VZ] * d_inv;
}

// Multiply by a constant

void VEC3perK(LPVEC3 r, LPVEC3 v, double d )
{
        r -> n[VX] = v -> n[VX] * d;
        r -> n[VY] = v -> n[VY] * d;
        r -> n[VZ] = v -> n[VZ] * d;
}


void VEC3perComp(LPVEC3 r, LPVEC3 a, LPVEC3 b)
{
       r -> n[VX] = a->n[VX]*b->n[VX];
       r -> n[VY] = a->n[VY]*b->n[VY];
       r -> n[VZ] = a->n[VZ]*b->n[VZ];
}

// Minus


void VEC3minus(LPVEC3 r, LPVEC3 a, LPVEC3 b)
{
  r -> n[VX] = a -> n[VX] - b -> n[VX];
  r -> n[VY] = a -> n[VY] - b -> n[VY];
  r -> n[VZ] = a -> n[VZ] - b -> n[VZ];
}


// Check id two vectors are the same, allowing tolerance

static
LCMSBOOL RangeCheck(double l, double h, double v)
{
       return (v >= l && v <= h);
}


LCMSBOOL VEC3equal(LPWVEC3 a, LPWVEC3 b, double Tolerance)
{
       int i;
       double c;

       for (i=0; i < 3; i++)
       {
              c = FIXED_TO_DOUBLE(a -> n[i]);
              if (!RangeCheck(c - Tolerance,
                              c + Tolerance,
                              FIXED_TO_DOUBLE(b->n[i]))) return FALSE;
       }

       return TRUE;
}

LCMSBOOL VEC3equalF(LPVEC3 a, LPVEC3 b, double Tolerance)
{
       int i;
       double c;

       for (i=0; i < 3; i++)
       {
              c = a -> n[i];
              if (!RangeCheck(c - Tolerance,
                              c + Tolerance,
                              b->n[i])) return FALSE;
       }

       return TRUE;
}


void VEC3scaleFix(LPWORD r, LPWVEC3 Scale)
{
       if (Scale -> n[VX] == 0x00010000L &&
           Scale -> n[VY] == 0x00010000L &&
           Scale -> n[VZ] == 0x00010000L) return;

       r[0] = (WORD) FixedScale(r[0], Scale -> n[VX]);
       r[1] = (WORD) FixedScale(r[1], Scale -> n[VY]);
       r[2] = (WORD) FixedScale(r[2], Scale -> n[VZ]);

}



// Vector cross product

void VEC3cross(LPVEC3 r, LPVEC3 u, LPVEC3 v)
{

    r ->n[VX] = u->n[VY] * v->n[VZ] - v->n[VY] * u->n[VZ];
    r ->n[VY] = u->n[VZ] * v->n[VX] - v->n[VZ] * u->n[VX];
    r ->n[VZ] = u->n[VX] * v->n[VY] - v->n[VX] * u->n[VY];
}



// The vector size

double VEC3length(LPVEC3 a)
{
    return sqrt(a ->n[VX] * a ->n[VX] +
                a ->n[VY] * a ->n[VY] +
                a ->n[VZ] * a ->n[VZ]);
}


// Saturate a vector into 0..1.0 range

void VEC3saturate(LPVEC3 v)
{
    int i;
    for (i=0; i < 3; i++) {
        if (v ->n[i] < 0)
                v ->n[i] = 0;
        else
        if (v ->n[i] > 1.0)
                v ->n[i] = 1.0;
    }
}


// Euclidean distance

double VEC3distance(LPVEC3 a, LPVEC3 b)
{
    double d1 = a ->n[VX] - b ->n[VX];
    double d2 = a ->n[VY] - b ->n[VY];
    double d3 = a ->n[VZ] - b ->n[VZ];

    return sqrt(d1*d1 + d2*d2 + d3*d3);
}


// Identity


void MAT3identity(LPMAT3 a)
{
        VEC3init(&a-> v[0], 1.0, 0.0, 0.0);
        VEC3init(&a-> v[1], 0.0, 1.0, 0.0);
        VEC3init(&a-> v[2], 0.0, 0.0, 1.0);
}




// Check if matrix is Identity. Allow a tolerance as %

LCMSBOOL MAT3isIdentity(LPWMAT3 a, double Tolerance)
{
       int i;
       MAT3 Idd;
       WMAT3 Idf;

       MAT3identity(&Idd);
       MAT3toFix(&Idf, &Idd);

       for (i=0; i < 3; i++)
              if (!VEC3equal(&a -> v[i], &Idf.v[i], Tolerance)) return FALSE;

       return TRUE;

}

// Multiply two matrices


void MAT3per(LPMAT3 r, LPMAT3 a, LPMAT3 b)
{
#define ROWCOL(i, j) \
    a->v[i].n[0]*b->v[0].n[j] + a->v[i].n[1]*b->v[1].n[j] + a->v[i].n[2]*b->v[2].n[j]

    VEC3init(&r-> v[0], ROWCOL(0,0), ROWCOL(0,1), ROWCOL(0,2));
    VEC3init(&r-> v[1], ROWCOL(1,0), ROWCOL(1,1), ROWCOL(1,2));
    VEC3init(&r-> v[2], ROWCOL(2,0), ROWCOL(2,1), ROWCOL(2,2));

#undef ROWCOL //(i, j)
}



// Inverse of a matrix b = a^(-1)
// Gauss-Jordan elimination with partial pivoting

int MAT3inverse(LPMAT3 a, LPMAT3 b)
{
    register int  i, j, max;

    MAT3identity(b);

    // Loop over cols of a from left to right, eliminating above and below diag
    for (j=0; j<3; j++) {   // Find largest pivot in column j among rows j..2

    max = j;                 // Row with largest pivot candidate
    for (i=j+1; i<3; i++)
        if (fabs(a -> v[i].n[j]) > fabs(a -> v[max].n[j]))
            max = i;

    // Swap rows max and j in a and b to put pivot on diagonal

    VEC3swap(&a -> v[max], &a -> v[j]);
    VEC3swap(&b -> v[max], &b -> v[j]);

    // Scale row j to have a unit diagonal

    if (a -> v[j].n[j]==0.)
        return -1;                 // singular matrix; can't invert

    VEC3divK(&b-> v[j], &b -> v[j], a->v[j].n[j]);
    VEC3divK(&a-> v[j], &a -> v[j], a->v[j].n[j]);

    // Eliminate off-diagonal elems in col j of a, doing identical ops to b
    for (i=0; i<3; i++)

        if (i !=j) {
                  VEC3 temp;

          VEC3perK(&temp, &b -> v[j], a -> v[i].n[j]);
          VEC3minus(&b -> v[i], &b -> v[i], &temp);

          VEC3perK(&temp, &a -> v[j], a -> v[i].n[j]);
          VEC3minus(&a -> v[i], &a -> v[i], &temp);
    }
    }

    return 1;
}


// Solve a system in the form Ax = b

LCMSBOOL MAT3solve(LPVEC3 x, LPMAT3 a, LPVEC3 b)
{
    MAT3 m, a_1;

    CopyMemory(&m, a, sizeof(MAT3));

    if (!MAT3inverse(&m, &a_1)) return FALSE;  // Singular matrix

    MAT3eval(x, &a_1, b);
    return TRUE;
}


// The determinant

double MAT3det(LPMAT3 m)
{

    double a1 = m ->v[VX].n[VX];
    double a2 = m ->v[VX].n[VY];
    double a3 = m ->v[VX].n[VZ];
    double b1 = m ->v[VY].n[VX];
    double b2 = m ->v[VY].n[VY];
    double b3 = m ->v[VY].n[VZ];
    double c1 = m ->v[VZ].n[VX];
    double c2 = m ->v[VZ].n[VY];
    double c3 = m ->v[VZ].n[VZ];


    return a1*b2*c3 - a1*b3*c2 + a2*b3*c1 - a2*b1*c3 - a3*b1*c2 - a3*b2*c1;
}


// linear transform


void MAT3eval(LPVEC3 r, LPMAT3 a, LPVEC3 v)
{
    r->n[VX] = a->v[0].n[VX]*v->n[VX] + a->v[0].n[VY]*v->n[VY] + a->v[0].n[VZ]*v->n[VZ];
    r->n[VY] = a->v[1].n[VX]*v->n[VX] + a->v[1].n[VY]*v->n[VY] + a->v[1].n[VZ]*v->n[VZ];
    r->n[VZ] = a->v[2].n[VX]*v->n[VX] + a->v[2].n[VY]*v->n[VY] + a->v[2].n[VZ]*v->n[VZ];
}


// Ok, this is another bottleneck of performance.


#ifdef USE_ASSEMBLER

// ecx:ebx is result in 64 bits format
// edi points to matrix, esi points to input vector
// since only 3 accesses are in output, this is a stack variable


void MAT3evalW(LPWVEC3 r_, LPWMAT3 a_, LPWVEC3 v_)
{

       ASM {


       mov    esi, dword ptr ss:v_
       mov    edi, dword ptr ss:a_

   //     r->n[VX] = FixedMul(a->v[0].n[0], v->n[0]) +

       mov       eax,dword ptr [esi]
       mov       edx,dword ptr [edi]
       imul      edx
       mov       ecx, eax
       mov       ebx, edx

   //          FixedMul(a->v[0].n[1], v->n[1]) +

       mov       eax,dword ptr [esi+4]
       mov       edx,dword ptr [edi+4]
       imul      edx
       add       ecx, eax
       adc       ebx, edx

   //         FixedMul(a->v[0].n[2], v->n[2]);

       mov       eax,dword ptr [esi+8]
       mov       edx,dword ptr [edi+8]
       imul      edx
       add       ecx, eax
       adc       ebx, edx

   //  Back to Fixed 15.16

       add       ecx, 0x8000
       adc       ebx, 0
       shrd      ecx, ebx, 16

       push      edi
       mov       edi, dword ptr ss:r_
       mov       dword ptr [edi], ecx      //  r -> n[VX]
       pop       edi



   //   2nd row ***************************

   //        FixedMul(a->v[1].n[0], v->n[0])

       mov       eax,dword ptr [esi]
       mov       edx,dword ptr [edi+12]
       imul      edx
       mov       ecx, eax
       mov       ebx, edx

   //         FixedMul(a->v[1].n[1], v->n[1]) +

       mov       eax,dword ptr [esi+4]
       mov       edx,dword ptr [edi+16]
       imul      edx
       add       ecx, eax
       adc       ebx, edx

       //     FixedMul(a->v[1].n[2], v->n[2]);

       mov       eax,dword ptr [esi+8]
       mov       edx,dword ptr [edi+20]
       imul      edx
       add       ecx, eax
       adc       ebx, edx

       add       ecx, 0x8000
       adc       ebx, 0
       shrd      ecx, ebx, 16

       push      edi
       mov       edi, dword ptr ss:r_
       mov       dword ptr [edi+4], ecx      // r -> n[VY]
       pop       edi

//     3d row **************************

   //       r->n[VZ] = FixedMul(a->v[2].n[0], v->n[0]) +

       mov       eax,dword ptr [esi]
       mov       edx,dword ptr [edi+24]
       imul      edx
       mov       ecx, eax
       mov       ebx, edx

   //    FixedMul(a->v[2].n[1], v->n[1]) +

       mov       eax,dword ptr [esi+4]
       mov       edx,dword ptr [edi+28]
       imul      edx
       add       ecx, eax
       adc       ebx, edx

   //   FixedMul(a->v[2].n[2], v->n[2]);

       mov       eax,dword ptr [esi+8]
       mov       edx,dword ptr [edi+32]
       imul      edx
       add       ecx, eax
       adc       ebx, edx

       add       ecx, 0x8000
       adc       ebx, 0
       shrd      ecx, ebx, 16

       mov       edi, dword ptr ss:r_
       mov       dword ptr [edi+8], ecx      // r -> n[VZ]
       }
}


#else


#ifdef USE_FLOAT

void MAT3evalW(LPWVEC3 r, LPWMAT3 a, LPWVEC3 v)
{
    r->n[VX] = DOUBLE_TO_FIXED(
                 FIXED_TO_DOUBLE(a->v[0].n[0]) * FIXED_TO_DOUBLE(v->n[0]) +
                 FIXED_TO_DOUBLE(a->v[0].n[1]) * FIXED_TO_DOUBLE(v->n[1]) +
                 FIXED_TO_DOUBLE(a->v[0].n[2]) * FIXED_TO_DOUBLE(v->n[2])
                );

    r->n[VY] = DOUBLE_TO_FIXED(
                 FIXED_TO_DOUBLE(a->v[1].n[0]) * FIXED_TO_DOUBLE(v->n[0]) +
                 FIXED_TO_DOUBLE(a->v[1].n[1]) * FIXED_TO_DOUBLE(v->n[1]) +
                 FIXED_TO_DOUBLE(a->v[1].n[2]) * FIXED_TO_DOUBLE(v->n[2])
               );

    r->n[VZ] = DOUBLE_TO_FIXED(
                FIXED_TO_DOUBLE(a->v[2].n[0]) * FIXED_TO_DOUBLE(v->n[0]) +
                FIXED_TO_DOUBLE(a->v[2].n[1]) * FIXED_TO_DOUBLE(v->n[1]) +
                FIXED_TO_DOUBLE(a->v[2].n[2]) * FIXED_TO_DOUBLE(v->n[2])
               );
}


#else

void MAT3evalW(LPWVEC3 r, LPWMAT3 a, LPWVEC3 v)
{

#ifdef USE_INT64

    LCMSULONGLONG l1 = (LCMSULONGLONG) (LCMSSLONGLONG) a->v[0].n[0] *
                       (LCMSULONGLONG) (LCMSSLONGLONG) v->n[0] +
                       (LCMSULONGLONG) (LCMSSLONGLONG) a->v[0].n[1] *
                       (LCMSULONGLONG) (LCMSSLONGLONG) v->n[1] +
                       (LCMSULONGLONG) (LCMSSLONGLONG) a->v[0].n[2] *
                       (LCMSULONGLONG) (LCMSSLONGLONG) v->n[2] + (LCMSULONGLONG) 0x8000;

    LCMSULONGLONG l2 = (LCMSULONGLONG) (LCMSSLONGLONG) a->v[1].n[0] *
                       (LCMSULONGLONG) (LCMSSLONGLONG) v->n[0] +
                       (LCMSULONGLONG) (LCMSSLONGLONG) a->v[1].n[1] *
                       (LCMSULONGLONG) (LCMSSLONGLONG) v->n[1] +
                       (LCMSULONGLONG) (LCMSSLONGLONG) a->v[1].n[2] *
                       (LCMSULONGLONG) (LCMSSLONGLONG) v->n[2] + (LCMSULONGLONG) 0x8000;

    LCMSULONGLONG l3 = (LCMSULONGLONG) (LCMSSLONGLONG) a->v[2].n[0] *
                       (LCMSULONGLONG) (LCMSSLONGLONG) v->n[0] +
                       (LCMSULONGLONG) (LCMSSLONGLONG) a->v[2].n[1] *
                       (LCMSULONGLONG) (LCMSSLONGLONG) v->n[1] +
                       (LCMSULONGLONG) (LCMSSLONGLONG) a->v[2].n[2] *
                       (LCMSULONGLONG) (LCMSSLONGLONG) v->n[2] + (LCMSULONGLONG) 0x8000;
    l1 >>= 16;
    l2 >>= 16;
    l3 >>= 16;

    r->n[VX] = (Fixed32) l1;
    r->n[VY] = (Fixed32) l2;
    r->n[VZ] = (Fixed32) l3;

#else

    // FIXME: Rounding should be done at very last stage. There is 1-Contone rounding error!

    r->n[VX] = FixedMul(a->v[0].n[0], v->n[0]) +
               FixedMul(a->v[0].n[1], v->n[1]) +
               FixedMul(a->v[0].n[2], v->n[2]);

    r->n[VY] = FixedMul(a->v[1].n[0], v->n[0]) +
               FixedMul(a->v[1].n[1], v->n[1]) +
               FixedMul(a->v[1].n[2], v->n[2]);

    r->n[VZ] = FixedMul(a->v[2].n[0], v->n[0]) +
               FixedMul(a->v[2].n[1], v->n[1]) +
               FixedMul(a->v[2].n[2], v->n[2]);
#endif
}

#endif
#endif


void MAT3perK(LPMAT3 r, LPMAT3 v, double d)
{
       VEC3perK(&r -> v[0], &v -> v[0], d);
       VEC3perK(&r -> v[1], &v -> v[1], d);
       VEC3perK(&r -> v[2], &v -> v[2], d);
}


void MAT3toFix(LPWMAT3 r, LPMAT3 v)
{
       VEC3toFix(&r -> v[0], &v -> v[0]);
       VEC3toFix(&r -> v[1], &v -> v[1]);
       VEC3toFix(&r -> v[2], &v -> v[2]);
}

void MAT3fromFix(LPMAT3 r, LPWMAT3 v)
{
       VEC3fromFix(&r -> v[0], &v -> v[0]);
       VEC3fromFix(&r -> v[1], &v -> v[1]);
       VEC3fromFix(&r -> v[2], &v -> v[2]);
}



// Scale v by d and store it in r giving INTEGER

void VEC3scaleAndCut(LPWVEC3 r, LPVEC3 v, double d)
{
        r -> n[VX] = (int) floor(v -> n[VX] * d + .5);
        r -> n[VY] = (int) floor(v -> n[VY] * d + .5);
        r -> n[VZ] = (int) floor(v -> n[VZ] * d + .5);
}

void MAT3scaleAndCut(LPWMAT3 r, LPMAT3 v, double d)
{
       VEC3scaleAndCut(&r -> v[0], &v -> v[0], d);
       VEC3scaleAndCut(&r -> v[1], &v -> v[1], d);
       VEC3scaleAndCut(&r -> v[2], &v -> v[2], d);
}




