/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

#include <stdarg.h>
#include <stdlib.h>

#include "shared.h"

struct S_FFFF { float p0; float p1; float p2; float p3; };

typedef void (*writeback_t)(int,void*);

typedef struct {
    writeback_t writeback;
    int* argids;
} call_info;

#define CASE(num, type) case num: { \
  type x = va_arg(a_list, type); \
  writeback(i, &x); \
} break;

enum NativeType {
    T_INT,
    T_DOUBLE,
    T_POINTER,
    T_S_I,
    T_S_F,
    T_S_D,
    T_S_P,
    T_S_II,
    T_S_IF,
    T_S_ID,
    T_S_IP,
    T_S_FI,
    T_S_FF,
    T_S_FD,
    T_S_FP,
    T_S_DI,
    T_S_DF,
    T_S_DD,
    T_S_DP,
    T_S_PI,
    T_S_PF,
    T_S_PD,
    T_S_PP,
    T_S_III,
    T_S_IIF,
    T_S_IID,
    T_S_IIP,
    T_S_IFI,
    T_S_IFF,
    T_S_IFD,
    T_S_IFP,
    T_S_IDI,
    T_S_IDF,
    T_S_IDD,
    T_S_IDP,
    T_S_IPI,
    T_S_IPF,
    T_S_IPD,
    T_S_IPP,
    T_S_FII,
    T_S_FIF,
    T_S_FID,
    T_S_FIP,
    T_S_FFI,
    T_S_FFF,
    T_S_FFD,
    T_S_FFP,
    T_S_FDI,
    T_S_FDF,
    T_S_FDD,
    T_S_FDP,
    T_S_FPI,
    T_S_FPF,
    T_S_FPD,
    T_S_FPP,
    T_S_DII,
    T_S_DIF,
    T_S_DID,
    T_S_DIP,
    T_S_DFI,
    T_S_DFF,
    T_S_DFD,
    T_S_DFP,
    T_S_DDI,
    T_S_DDF,
    T_S_DDD,
    T_S_DDP,
    T_S_DPI,
    T_S_DPF,
    T_S_DPD,
    T_S_DPP,
    T_S_PII,
    T_S_PIF,
    T_S_PID,
    T_S_PIP,
    T_S_PFI,
    T_S_PFF,
    T_S_PFD,
    T_S_PFP,
    T_S_PDI,
    T_S_PDF,
    T_S_PDD,
    T_S_PDP,
    T_S_PPI,
    T_S_PPF,
    T_S_PPD,
    T_S_PPP,
    T_S_FFFF,
};

// need to pass `num` separately as last argument preceding varargs according to spec (and for MSVC)
EXPORT void varargs(call_info* info, int num, ...) {
    va_list a_list;
    va_start(a_list, num);
    writeback_t writeback = info->writeback;

    for (int i = 0; i < num; i++) {
        int id = info->argids[i];
        switch (id) {
            CASE(T_INT, int)
            CASE(T_DOUBLE, double)
            CASE(T_POINTER, void*)
            CASE(T_S_I,   struct S_I)
            CASE(T_S_F,   struct S_F)
            CASE(T_S_D,   struct S_D)
            CASE(T_S_P,   struct S_P)
            CASE(T_S_II,  struct S_II)
            CASE(T_S_IF,  struct S_IF)
            CASE(T_S_ID,  struct S_ID)
            CASE(T_S_IP,  struct S_IP)
            CASE(T_S_FI,  struct S_FI)
            CASE(T_S_FF,  struct S_FF)
            CASE(T_S_FD,  struct S_FD)
            CASE(T_S_FP,  struct S_FP)
            CASE(T_S_DI,  struct S_DI)
            CASE(T_S_DF,  struct S_DF)
            CASE(T_S_DD,  struct S_DD)
            CASE(T_S_DP,  struct S_DP)
            CASE(T_S_PI,  struct S_PI)
            CASE(T_S_PF,  struct S_PF)
            CASE(T_S_PD,  struct S_PD)
            CASE(T_S_PP,  struct S_PP)
            CASE(T_S_III, struct S_III)
            CASE(T_S_IIF, struct S_IIF)
            CASE(T_S_IID, struct S_IID)
            CASE(T_S_IIP, struct S_IIP)
            CASE(T_S_IFI, struct S_IFI)
            CASE(T_S_IFF, struct S_IFF)
            CASE(T_S_IFD, struct S_IFD)
            CASE(T_S_IFP, struct S_IFP)
            CASE(T_S_IDI, struct S_IDI)
            CASE(T_S_IDF, struct S_IDF)
            CASE(T_S_IDD, struct S_IDD)
            CASE(T_S_IDP, struct S_IDP)
            CASE(T_S_IPI, struct S_IPI)
            CASE(T_S_IPF, struct S_IPF)
            CASE(T_S_IPD, struct S_IPD)
            CASE(T_S_IPP, struct S_IPP)
            CASE(T_S_FII, struct S_FII)
            CASE(T_S_FIF, struct S_FIF)
            CASE(T_S_FID, struct S_FID)
            CASE(T_S_FIP, struct S_FIP)
            CASE(T_S_FFI, struct S_FFI)
            CASE(T_S_FFF, struct S_FFF)
            CASE(T_S_FFD, struct S_FFD)
            CASE(T_S_FFP, struct S_FFP)
            CASE(T_S_FDI, struct S_FDI)
            CASE(T_S_FDF, struct S_FDF)
            CASE(T_S_FDD, struct S_FDD)
            CASE(T_S_FDP, struct S_FDP)
            CASE(T_S_FPI, struct S_FPI)
            CASE(T_S_FPF, struct S_FPF)
            CASE(T_S_FPD, struct S_FPD)
            CASE(T_S_FPP, struct S_FPP)
            CASE(T_S_DII, struct S_DII)
            CASE(T_S_DIF, struct S_DIF)
            CASE(T_S_DID, struct S_DID)
            CASE(T_S_DIP, struct S_DIP)
            CASE(T_S_DFI, struct S_DFI)
            CASE(T_S_DFF, struct S_DFF)
            CASE(T_S_DFD, struct S_DFD)
            CASE(T_S_DFP, struct S_DFP)
            CASE(T_S_DDI, struct S_DDI)
            CASE(T_S_DDF, struct S_DDF)
            CASE(T_S_DDD, struct S_DDD)
            CASE(T_S_DDP, struct S_DDP)
            CASE(T_S_DPI, struct S_DPI)
            CASE(T_S_DPF, struct S_DPF)
            CASE(T_S_DPD, struct S_DPD)
            CASE(T_S_DPP, struct S_DPP)
            CASE(T_S_PII, struct S_PII)
            CASE(T_S_PIF, struct S_PIF)
            CASE(T_S_PID, struct S_PID)
            CASE(T_S_PIP, struct S_PIP)
            CASE(T_S_PFI, struct S_PFI)
            CASE(T_S_PFF, struct S_PFF)
            CASE(T_S_PFD, struct S_PFD)
            CASE(T_S_PFP, struct S_PFP)
            CASE(T_S_PDI, struct S_PDI)
            CASE(T_S_PDF, struct S_PDF)
            CASE(T_S_PDD, struct S_PDD)
            CASE(T_S_PDP, struct S_PDP)
            CASE(T_S_PPI, struct S_PPI)
            CASE(T_S_PPF, struct S_PPF)
            CASE(T_S_PPD, struct S_PPD)
            CASE(T_S_PPP, struct S_PPP)
            CASE(T_S_FFFF, struct S_FFFF)
            default: exit(-1); // invalid id
        }
    }

    va_end(a_list);
}
