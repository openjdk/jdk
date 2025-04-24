/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifdef __clang__
#pragma clang optimize off
#elif defined __GNUC__
#pragma GCC optimize ("O0")
#elif defined _MSC_BUILD
#pragma optimize( "", off )
#endif

#ifdef _AIX
#pragma align (natural)
#endif

struct S_I { int p0; };
struct S_F { float p0; };
struct S_D { double p0; };
struct S_P { void* p0; };
struct S_II { int p0; int p1; };
struct S_IF { int p0; float p1; };
struct S_ID { int p0; double p1; };
struct S_IP { int p0; void* p1; };
struct S_FI { float p0; int p1; };
struct S_FF { float p0; float p1; };
struct S_FD { float p0; double p1; };
struct S_FP { float p0; void* p1; };
struct S_DI { double p0; int p1; };
struct S_DF { double p0; float p1; };
struct S_DD { double p0; double p1; };
struct S_DP { double p0; void* p1; };
struct S_PI { void* p0; int p1; };
struct S_PF { void* p0; float p1; };
struct S_PD { void* p0; double p1; };
struct S_PP { void* p0; void* p1; };
struct S_III { int p0; int p1; int p2; };
struct S_IIF { int p0; int p1; float p2; };
struct S_IID { int p0; int p1; double p2; };
struct S_IIP { int p0; int p1; void* p2; };
struct S_IFI { int p0; float p1; int p2; };
struct S_IFF { int p0; float p1; float p2; };
struct S_IFD { int p0; float p1; double p2; };
struct S_IFP { int p0; float p1; void* p2; };
struct S_IDI { int p0; double p1; int p2; };
struct S_IDF { int p0; double p1; float p2; };
struct S_IDD { int p0; double p1; double p2; };
struct S_IDP { int p0; double p1; void* p2; };
struct S_IPI { int p0; void* p1; int p2; };
struct S_IPF { int p0; void* p1; float p2; };
struct S_IPD { int p0; void* p1; double p2; };
struct S_IPP { int p0; void* p1; void* p2; };
struct S_FII { float p0; int p1; int p2; };
struct S_FIF { float p0; int p1; float p2; };
struct S_FID { float p0; int p1; double p2; };
struct S_FIP { float p0; int p1; void* p2; };
struct S_FFI { float p0; float p1; int p2; };
struct S_FFF { float p0; float p1; float p2; };
struct S_FFD { float p0; float p1; double p2; };
struct S_FFP { float p0; float p1; void* p2; };
struct S_FDI { float p0; double p1; int p2; };
struct S_FDF { float p0; double p1; float p2; };
struct S_FDD { float p0; double p1; double p2; };
struct S_FDP { float p0; double p1; void* p2; };
struct S_FPI { float p0; void* p1; int p2; };
struct S_FPF { float p0; void* p1; float p2; };
struct S_FPD { float p0; void* p1; double p2; };
struct S_FPP { float p0; void* p1; void* p2; };
struct S_DII { double p0; int p1; int p2; };
struct S_DIF { double p0; int p1; float p2; };
struct S_DID { double p0; int p1; double p2; };
struct S_DIP { double p0; int p1; void* p2; };
struct S_DFI { double p0; float p1; int p2; };
struct S_DFF { double p0; float p1; float p2; };
struct S_DFD { double p0; float p1; double p2; };
struct S_DFP { double p0; float p1; void* p2; };
struct S_DDI { double p0; double p1; int p2; };
struct S_DDF { double p0; double p1; float p2; };
struct S_DDD { double p0; double p1; double p2; };
struct S_DDP { double p0; double p1; void* p2; };
struct S_DPI { double p0; void* p1; int p2; };
struct S_DPF { double p0; void* p1; float p2; };
struct S_DPD { double p0; void* p1; double p2; };
struct S_DPP { double p0; void* p1; void* p2; };
struct S_PII { void* p0; int p1; int p2; };
struct S_PIF { void* p0; int p1; float p2; };
struct S_PID { void* p0; int p1; double p2; };
struct S_PIP { void* p0; int p1; void* p2; };
struct S_PFI { void* p0; float p1; int p2; };
struct S_PFF { void* p0; float p1; float p2; };
struct S_PFD { void* p0; float p1; double p2; };
struct S_PFP { void* p0; float p1; void* p2; };
struct S_PDI { void* p0; double p1; int p2; };
struct S_PDF { void* p0; double p1; float p2; };
struct S_PDD { void* p0; double p1; double p2; };
struct S_PDP { void* p0; double p1; void* p2; };
struct S_PPI { void* p0; void* p1; int p2; };
struct S_PPF { void* p0; void* p1; float p2; };
struct S_PPD { void* p0; void* p1; double p2; };
struct S_PPP { void* p0; void* p1; void* p2; };

#ifdef _AIX
#pragma align (reset)
#endif
