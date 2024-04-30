/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "export.h"

#ifdef _AIX
#pragma align (natural)
#endif

struct S1{ double f0; long long f1; double f2; int f3; };
union U1{ short f0; long long f1; short f2; char f3[4][3]; };
union U17{ char f0; char f1; long long f2; double f3; };
struct S2{ union U17 f0; long long f1[4]; short f2; };
struct S3{ float f0; int f1; union U1 f2; struct S2 f3; };
struct S4{ short f0[2]; struct S1 f1; };
struct S5{ float f0; void* f1; struct S4 f2; };
struct S6{ struct S5 f0; };
union U2{ float f0; short f1; void* f2; float f3; };
struct S7{ double f0; short f1; short f2; long long f3; };
union U3{ void* f0; union U2 f1; long long f2; struct S7 f3; };
union U4{ float f0; };
union U5{ union U3 f0; long long f1[3]; union U4 f2; float f3; };
union U6{ short f0; float f1; union U5 f2; short f3; };
union U7{ short f0; };
struct S8{ double f0[3]; union U7 f1; void* f2; void* f3; };
struct S9{ char f0; double f1[2]; char f2; struct S8 f3; };
union U8{ long long f0; void* f1; struct S9 f2; };
union U9{ int f0; double f1; short f2[2]; long long f3; };
union U10{ long long f0; union U9 f1; char f2; float f3; };
struct S10{ double f0[4]; };
union U11{ struct S10 f0[3]; };
struct S11{ short f0; char f1; };
union U12{ float f0; struct S11 f1; char f2; char f3; };
struct S12{ union U12 f0; float f1; };
union U13{ float f0; struct S12 f1; };
union U14{ int f0; void* f1[2]; float f2[2][3]; };
union U15{ void* f0; long long f1; double f2[1]; long long f3; };
struct S13{ int f0; char f1; void* f2; char f3; };
struct S14{ long long f0; };
union U16{ short f0[4]; int f1; struct S13 f2; struct S14 f3; };
struct S15{ union U16 f0; float f1; int f2; long long f3; };

EXPORT struct S1 test_S1(struct S1 arg, struct S1(*cb)(struct S1)) { return cb(arg); }
EXPORT union U1 test_U1(union U1 arg, union U1(*cb)(union U1)) { return cb(arg); }
EXPORT union U17 test_U17(union U17 arg, union U17(*cb)(union U17)) { return cb(arg); }
EXPORT struct S2 test_S2(struct S2 arg, struct S2(*cb)(struct S2)) { return cb(arg); }
EXPORT struct S3 test_S3(struct S3 arg, struct S3(*cb)(struct S3)) { return cb(arg); }
EXPORT struct S4 test_S4(struct S4 arg, struct S4(*cb)(struct S4)) { return cb(arg); }
EXPORT struct S5 test_S5(struct S5 arg, struct S5(*cb)(struct S5)) { return cb(arg); }
EXPORT struct S6 test_S6(struct S6 arg, struct S6(*cb)(struct S6)) { return cb(arg); }
EXPORT union U2 test_U2(union U2 arg, union U2(*cb)(union U2)) { return cb(arg); }
EXPORT struct S7 test_S7(struct S7 arg, struct S7(*cb)(struct S7)) { return cb(arg); }
EXPORT union U3 test_U3(union U3 arg, union U3(*cb)(union U3)) { return cb(arg); }
EXPORT union U4 test_U4(union U4 arg, union U4(*cb)(union U4)) { return cb(arg); }
EXPORT union U5 test_U5(union U5 arg, union U5(*cb)(union U5)) { return cb(arg); }
EXPORT union U6 test_U6(union U6 arg, union U6(*cb)(union U6)) { return cb(arg); }
EXPORT union U7 test_U7(union U7 arg, union U7(*cb)(union U7)) { return cb(arg); }
EXPORT struct S8 test_S8(struct S8 arg, struct S8(*cb)(struct S8)) { return cb(arg); }
EXPORT struct S9 test_S9(struct S9 arg, struct S9(*cb)(struct S9)) { return cb(arg); }
EXPORT union U8 test_U8(union U8 arg, union U8(*cb)(union U8)) { return cb(arg); }
EXPORT union U9 test_U9(union U9 arg, union U9(*cb)(union U9)) { return cb(arg); }
EXPORT union U10 test_U10(union U10 arg, union U10(*cb)(union U10)) { return cb(arg); }
EXPORT struct S10 test_S10(struct S10 arg, struct S10(*cb)(struct S10)) { return cb(arg); }
EXPORT union U11 test_U11(union U11 arg, union U11(*cb)(union U11)) { return cb(arg); }
EXPORT struct S11 test_S11(struct S11 arg, struct S11(*cb)(struct S11)) { return cb(arg); }
EXPORT union U12 test_U12(union U12 arg, union U12(*cb)(union U12)) { return cb(arg); }
EXPORT struct S12 test_S12(struct S12 arg, struct S12(*cb)(struct S12)) { return cb(arg); }
EXPORT union U13 test_U13(union U13 arg, union U13(*cb)(union U13)) { return cb(arg); }
EXPORT union U14 test_U14(union U14 arg, union U14(*cb)(union U14)) { return cb(arg); }
EXPORT union U15 test_U15(union U15 arg, union U15(*cb)(union U15)) { return cb(arg); }
EXPORT struct S13 test_S13(struct S13 arg, struct S13(*cb)(struct S13)) { return cb(arg); }
EXPORT struct S14 test_S14(struct S14 arg, struct S14(*cb)(struct S14)) { return cb(arg); }
EXPORT union U16 test_U16(union U16 arg, union U16(*cb)(union U16)) { return cb(arg); }
EXPORT struct S15 test_S15(struct S15 arg, struct S15(*cb)(struct S15)) { return cb(arg); }

#ifdef _AIX
#pragma align (reset)
#endif
