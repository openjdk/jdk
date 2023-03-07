/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifdef _WIN64
#define EXPORT __declspec(dllexport)
#else
#define EXPORT
#endif

struct S1{ short f0; double f1; double f2; short f3; };
struct S2{ double f0; long long f1; int f2; void* f3; };
struct S3{ float f0; struct S2 f1; long long f2; void* f3; };
union U1{ float f0[4]; int f1; struct S3 f2; };
struct S5{ int f0; struct S1 f1[3][4]; union U1 f2; double f3; };
struct S6{ char f0; double f1; short f2; char f3; };
union U2{ void* f0; float f1; int f2; };
union U3{ union U2 f0; };
union U4{ long long f0; short f1; float f2; };
union U5{ union U4 f0; };

EXPORT struct S6 F84(struct S6 (*cb)(void*, short, long long, int, short, struct S5, long long, char, double, char,
                                     float, char, void*, char, struct S6, union U3, double, int, double, char, union U5,
                                     int),
        void* a0, short a1, long long a2, int a3, short a4, struct S5 a5, long long a6, char a7, double a8, char a9,
        float a10, char a11, void* a12, char a13, struct S6 a14, union U3 a15, double a16, int a17, double a18,
        char a19, union U5 a20, int a21){
    return cb(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21);
}
