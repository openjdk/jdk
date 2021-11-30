/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#include <thread>

#include "libTestUpcall.h"
#ifdef __clang__
#pragma clang optimize off
#elif defined __GNUC__
#pragma GCC optimize ("O0")
#elif defined _MSC_BUILD
#pragma optimize( "", off )
#endif

template<typename CB>
void launch_v(CB cb) {
    std::thread thrd(cb);
    thrd.join();
}

template<typename O, typename CB>
void start(O& out, CB cb) {
    out = cb();
}

template<typename O, typename CB>
O launch(CB cb) {
    O result;
    std::thread thrd(&start<O, CB>, std::ref(result), cb);
    thrd.join();
    return result;
}

extern "C" {
EXPORT void call_async_V(void (*cb)(void)) { launch_v(cb); }

EXPORT int call_async_I(int (*cb)(void)) { return launch<int>(cb); }
EXPORT float call_async_F(float (*cb)(void)) { return launch<float>(cb); }
EXPORT double call_async_D(double (*cb)(void)) { return launch<double>(cb); }
EXPORT void* call_async_P(void* (*cb)(void)) { return launch<void*>(cb); }

EXPORT struct S_I call_async_S_I(struct S_I (*cb)(void)) { return launch<struct S_I>(cb); }
EXPORT struct S_F call_async_S_F(struct S_F (*cb)(void)) { return launch<struct S_F>(cb); }
EXPORT struct S_D call_async_S_D(struct S_D (*cb)(void)) { return launch<struct S_D>(cb); }
EXPORT struct S_P call_async_S_P(struct S_P (*cb)(void)) { return launch<struct S_P>(cb); }
EXPORT struct S_II call_async_S_II(struct S_II (*cb)(void)) { return launch<struct S_II>(cb); }
EXPORT struct S_IF call_async_S_IF(struct S_IF (*cb)(void)) { return launch<struct S_IF>(cb); }
EXPORT struct S_ID call_async_S_ID(struct S_ID (*cb)(void)) { return launch<struct S_ID>(cb); }
EXPORT struct S_IP call_async_S_IP(struct S_IP (*cb)(void)) { return launch<struct S_IP>(cb); }
EXPORT struct S_FI call_async_S_FI(struct S_FI (*cb)(void)) { return launch<struct S_FI>(cb); }
EXPORT struct S_FF call_async_S_FF(struct S_FF (*cb)(void)) { return launch<struct S_FF>(cb); }
EXPORT struct S_FD call_async_S_FD(struct S_FD (*cb)(void)) { return launch<struct S_FD>(cb); }
EXPORT struct S_FP call_async_S_FP(struct S_FP (*cb)(void)) { return launch<struct S_FP>(cb); }
EXPORT struct S_DI call_async_S_DI(struct S_DI (*cb)(void)) { return launch<struct S_DI>(cb); }
EXPORT struct S_DF call_async_S_DF(struct S_DF (*cb)(void)) { return launch<struct S_DF>(cb); }
EXPORT struct S_DD call_async_S_DD(struct S_DD (*cb)(void)) { return launch<struct S_DD>(cb); }
EXPORT struct S_DP call_async_S_DP(struct S_DP (*cb)(void)) { return launch<struct S_DP>(cb); }
EXPORT struct S_PI call_async_S_PI(struct S_PI (*cb)(void)) { return launch<struct S_PI>(cb); }
EXPORT struct S_PF call_async_S_PF(struct S_PF (*cb)(void)) { return launch<struct S_PF>(cb); }
EXPORT struct S_PD call_async_S_PD(struct S_PD (*cb)(void)) { return launch<struct S_PD>(cb); }
EXPORT struct S_PP call_async_S_PP(struct S_PP (*cb)(void)) { return launch<struct S_PP>(cb); }
EXPORT struct S_III call_async_S_III(struct S_III (*cb)(void)) { return launch<struct S_III>(cb); }
EXPORT struct S_IIF call_async_S_IIF(struct S_IIF (*cb)(void)) { return launch<struct S_IIF>(cb); }
EXPORT struct S_IID call_async_S_IID(struct S_IID (*cb)(void)) { return launch<struct S_IID>(cb); }
EXPORT struct S_IIP call_async_S_IIP(struct S_IIP (*cb)(void)) { return launch<struct S_IIP>(cb); }
EXPORT struct S_IFI call_async_S_IFI(struct S_IFI (*cb)(void)) { return launch<struct S_IFI>(cb); }
EXPORT struct S_IFF call_async_S_IFF(struct S_IFF (*cb)(void)) { return launch<struct S_IFF>(cb); }
EXPORT struct S_IFD call_async_S_IFD(struct S_IFD (*cb)(void)) { return launch<struct S_IFD>(cb); }
EXPORT struct S_IFP call_async_S_IFP(struct S_IFP (*cb)(void)) { return launch<struct S_IFP>(cb); }
EXPORT struct S_IDI call_async_S_IDI(struct S_IDI (*cb)(void)) { return launch<struct S_IDI>(cb); }
EXPORT struct S_IDF call_async_S_IDF(struct S_IDF (*cb)(void)) { return launch<struct S_IDF>(cb); }
EXPORT struct S_IDD call_async_S_IDD(struct S_IDD (*cb)(void)) { return launch<struct S_IDD>(cb); }
EXPORT struct S_IDP call_async_S_IDP(struct S_IDP (*cb)(void)) { return launch<struct S_IDP>(cb); }
EXPORT struct S_IPI call_async_S_IPI(struct S_IPI (*cb)(void)) { return launch<struct S_IPI>(cb); }
EXPORT struct S_IPF call_async_S_IPF(struct S_IPF (*cb)(void)) { return launch<struct S_IPF>(cb); }
EXPORT struct S_IPD call_async_S_IPD(struct S_IPD (*cb)(void)) { return launch<struct S_IPD>(cb); }
EXPORT struct S_IPP call_async_S_IPP(struct S_IPP (*cb)(void)) { return launch<struct S_IPP>(cb); }
EXPORT struct S_FII call_async_S_FII(struct S_FII (*cb)(void)) { return launch<struct S_FII>(cb); }
EXPORT struct S_FIF call_async_S_FIF(struct S_FIF (*cb)(void)) { return launch<struct S_FIF>(cb); }
EXPORT struct S_FID call_async_S_FID(struct S_FID (*cb)(void)) { return launch<struct S_FID>(cb); }
EXPORT struct S_FIP call_async_S_FIP(struct S_FIP (*cb)(void)) { return launch<struct S_FIP>(cb); }
EXPORT struct S_FFI call_async_S_FFI(struct S_FFI (*cb)(void)) { return launch<struct S_FFI>(cb); }
EXPORT struct S_FFF call_async_S_FFF(struct S_FFF (*cb)(void)) { return launch<struct S_FFF>(cb); }
EXPORT struct S_FFD call_async_S_FFD(struct S_FFD (*cb)(void)) { return launch<struct S_FFD>(cb); }
EXPORT struct S_FFP call_async_S_FFP(struct S_FFP (*cb)(void)) { return launch<struct S_FFP>(cb); }
EXPORT struct S_FDI call_async_S_FDI(struct S_FDI (*cb)(void)) { return launch<struct S_FDI>(cb); }
EXPORT struct S_FDF call_async_S_FDF(struct S_FDF (*cb)(void)) { return launch<struct S_FDF>(cb); }
EXPORT struct S_FDD call_async_S_FDD(struct S_FDD (*cb)(void)) { return launch<struct S_FDD>(cb); }
EXPORT struct S_FDP call_async_S_FDP(struct S_FDP (*cb)(void)) { return launch<struct S_FDP>(cb); }
EXPORT struct S_FPI call_async_S_FPI(struct S_FPI (*cb)(void)) { return launch<struct S_FPI>(cb); }
EXPORT struct S_FPF call_async_S_FPF(struct S_FPF (*cb)(void)) { return launch<struct S_FPF>(cb); }
EXPORT struct S_FPD call_async_S_FPD(struct S_FPD (*cb)(void)) { return launch<struct S_FPD>(cb); }
EXPORT struct S_FPP call_async_S_FPP(struct S_FPP (*cb)(void)) { return launch<struct S_FPP>(cb); }
EXPORT struct S_DII call_async_S_DII(struct S_DII (*cb)(void)) { return launch<struct S_DII>(cb); }
EXPORT struct S_DIF call_async_S_DIF(struct S_DIF (*cb)(void)) { return launch<struct S_DIF>(cb); }
EXPORT struct S_DID call_async_S_DID(struct S_DID (*cb)(void)) { return launch<struct S_DID>(cb); }
EXPORT struct S_DIP call_async_S_DIP(struct S_DIP (*cb)(void)) { return launch<struct S_DIP>(cb); }
EXPORT struct S_DFI call_async_S_DFI(struct S_DFI (*cb)(void)) { return launch<struct S_DFI>(cb); }
EXPORT struct S_DFF call_async_S_DFF(struct S_DFF (*cb)(void)) { return launch<struct S_DFF>(cb); }
EXPORT struct S_DFD call_async_S_DFD(struct S_DFD (*cb)(void)) { return launch<struct S_DFD>(cb); }
EXPORT struct S_DFP call_async_S_DFP(struct S_DFP (*cb)(void)) { return launch<struct S_DFP>(cb); }
EXPORT struct S_DDI call_async_S_DDI(struct S_DDI (*cb)(void)) { return launch<struct S_DDI>(cb); }
EXPORT struct S_DDF call_async_S_DDF(struct S_DDF (*cb)(void)) { return launch<struct S_DDF>(cb); }
EXPORT struct S_DDD call_async_S_DDD(struct S_DDD (*cb)(void)) { return launch<struct S_DDD>(cb); }
EXPORT struct S_DDP call_async_S_DDP(struct S_DDP (*cb)(void)) { return launch<struct S_DDP>(cb); }
EXPORT struct S_DPI call_async_S_DPI(struct S_DPI (*cb)(void)) { return launch<struct S_DPI>(cb); }
EXPORT struct S_DPF call_async_S_DPF(struct S_DPF (*cb)(void)) { return launch<struct S_DPF>(cb); }
EXPORT struct S_DPD call_async_S_DPD(struct S_DPD (*cb)(void)) { return launch<struct S_DPD>(cb); }
EXPORT struct S_DPP call_async_S_DPP(struct S_DPP (*cb)(void)) { return launch<struct S_DPP>(cb); }
EXPORT struct S_PII call_async_S_PII(struct S_PII (*cb)(void)) { return launch<struct S_PII>(cb); }
EXPORT struct S_PIF call_async_S_PIF(struct S_PIF (*cb)(void)) { return launch<struct S_PIF>(cb); }
EXPORT struct S_PID call_async_S_PID(struct S_PID (*cb)(void)) { return launch<struct S_PID>(cb); }
EXPORT struct S_PIP call_async_S_PIP(struct S_PIP (*cb)(void)) { return launch<struct S_PIP>(cb); }
EXPORT struct S_PFI call_async_S_PFI(struct S_PFI (*cb)(void)) { return launch<struct S_PFI>(cb); }
EXPORT struct S_PFF call_async_S_PFF(struct S_PFF (*cb)(void)) { return launch<struct S_PFF>(cb); }
EXPORT struct S_PFD call_async_S_PFD(struct S_PFD (*cb)(void)) { return launch<struct S_PFD>(cb); }
EXPORT struct S_PFP call_async_S_PFP(struct S_PFP (*cb)(void)) { return launch<struct S_PFP>(cb); }
EXPORT struct S_PDI call_async_S_PDI(struct S_PDI (*cb)(void)) { return launch<struct S_PDI>(cb); }
EXPORT struct S_PDF call_async_S_PDF(struct S_PDF (*cb)(void)) { return launch<struct S_PDF>(cb); }
EXPORT struct S_PDD call_async_S_PDD(struct S_PDD (*cb)(void)) { return launch<struct S_PDD>(cb); }
EXPORT struct S_PDP call_async_S_PDP(struct S_PDP (*cb)(void)) { return launch<struct S_PDP>(cb); }
EXPORT struct S_PPI call_async_S_PPI(struct S_PPI (*cb)(void)) { return launch<struct S_PPI>(cb); }
EXPORT struct S_PPF call_async_S_PPF(struct S_PPF (*cb)(void)) { return launch<struct S_PPF>(cb); }
EXPORT struct S_PPD call_async_S_PPD(struct S_PPD (*cb)(void)) { return launch<struct S_PPD>(cb); }
EXPORT struct S_PPP call_async_S_PPP(struct S_PPP (*cb)(void)) { return launch<struct S_PPP>(cb); }
}
