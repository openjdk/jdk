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

inline void ICacheInvalidationContext::pd_init(nmethod* nm) {
  if (NeoverseN1Errata1542419) {
    _nm = nm;
  }
}

inline void ICacheInvalidationContext::pd_invalidate_icache() {
  if (_nm != nullptr) {
    assert(NeoverseN1Errata1542419, "Should only be set for Neoverse N1 erratum");
    // Neoverse-N1 implementation mitigates erratum 1542419 with a workaround:
    // - Disable coherent icache.
    // - Trap IC IVAU instructions.
    // - Execute:
    //   - tlbi vae3is, xzr
    //   - dsb sy
    //
    // `tlbi vae3is, xzr` invalidates translations for all address spaces (global for address).
    //  It waits for all memory accesses using in-scope old translation information to complete
    //  before it is considered complete.
    //
    // As this workaround has significant overhead, Arm Neoverse N1 (MP050) Software Developer
    // Errata Notice version 29.0 suggests:
    //
    // "Since one TLB inner-shareable invalidation is enough to avoid this erratum, the number
    // of injected TLB invalidations should be minimized in the trap handler to mitigate
    // the performance impact due to this workaround."
    //
    // As the address for icache invalidation is not relevant, we use the nmethod's code start address.
    ICache::invalidate_word(_nm->code_begin());
  }
}

#endif // CPU_AARCH64_ICACHE_AARCH64_HPP
