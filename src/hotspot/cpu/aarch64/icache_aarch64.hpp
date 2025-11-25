/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_ICACHE_AARCH64_HPP
#define CPU_AARCH64_ICACHE_AARCH64_HPP

#include OS_CPU_HEADER(icache)

#include "code/nmethod.hpp"
#include "utilities/globalDefinitions.hpp"

#define PD_ICACHE_INVALIDATION_CONTEXT

extern THREAD_LOCAL bool deferred_icache_invalidation;

inline void ICacheInvalidationContext::pd_init() {
   if (NeoverseN1Errata1542419) {
    deferred_icache_invalidation = true;
  }
}

inline bool ICacheInvalidationContext::deferred_invalidation() {
  return deferred_icache_invalidation;
}

inline void ICacheInvalidationContext::pd_invalidate_icache() {
  if (NeoverseN1Errata1542419) {
    assert(deferred_icache_invalidation, "Deferred icache invalidation must be enabled");
    // Errata 1542419: Neoverse N1 cores with the 'COHERENT_ICACHE' feature may fetch stale
    // instructions when software depends on prefetch-speculation-protection
    // instead of explicit synchronization.
    //
    // Neoverse-N1 implementation mitigates the errata 1542419 with a workaround:
    // - Disable coherent icache.
    // - Trap IC IVAU instructions.
    // - Execute:
    //   - tlbi vae3is, xzr
    //   - dsb sy
    // - Ignore trapped IC IVAU instructions.
    //
    // `tlbi vae3is, xzr` invalidates all translation entries (all VAs, all possible levels).
    // It waits for all memory accesses using in-scope old translation information to complete
    // before it is considered complete.
    //
    // As this workaround has significant overhead, Arm Neoverse N1 (MP050) Software Developer
    // Errata Notice version 29.0 suggests:
    //
    // "Since one TLB inner-shareable invalidation is enough to avoid this erratum, the number
    // of injected TLB invalidations should be minimized in the trap handler to mitigate
    // the performance impact due to this workaround."
#ifndef PRODUCT
    unsigned int cache_info = 0;
    asm volatile ("mrs\t%0, ctr_el0":"=r" (cache_info));
    constexpr unsigned int CTR_IDC_SHIFT = 28;
    constexpr unsigned int CTR_DIC_SHIFT = 29;
    assert(((cache_info >> CTR_IDC_SHIFT) & 0x1) != 0x0, "Expect CTR_EL0.IDC to be enabled");
    assert(((cache_info >> CTR_DIC_SHIFT) & 0x1) == 0x0, "Expect CTR_EL0.DIC to be disabled");
#endif

    // As the address for icache invalidation is not relevant
    // and IC IVAU instruction is ignored, we use XZR in it.
    asm volatile("dsb ish       \n"
                 "ic  ivau, xzr \n"
                 "isb           \n"
                 : : : "memory");

    deferred_icache_invalidation = false;
  }
}

#endif // CPU_AARCH64_ICACHE_AARCH64_HPP
