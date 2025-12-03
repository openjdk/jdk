/*
 * Copyright (c) 1999, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_LINUX_AARCH64_ICACHE_AARCH64_HPP
#define OS_CPU_LINUX_AARCH64_ICACHE_AARCH64_HPP

#include "utilities/globalDefinitions.hpp"

#define PD_ICACHE_INVALIDATION_CONTEXT

NOT_PRODUCT(extern THREAD_LOCAL ICacheInvalidationContext* current_icache_invalidation_context;)

inline void ICacheInvalidationContext::pd_init() {
  assert(current_icache_invalidation_context == nullptr, "nested ICacheInvalidationContext not supported");
  NOT_PRODUCT(current_icache_invalidation_context = this);
  if (_mode == ICacheInvalidation::DEFERRED && _code == nullptr && !UseDeferredICacheInvalidation) {
    _mode = ICacheInvalidation::IMMEDIATE;
  }
}

#ifdef ASSERT
inline ICacheInvalidationContext* ICacheInvalidationContext::pd_current() {
  return current_icache_invalidation_context;
}
#endif

inline void assert_hardware_cache_coherency() {
#ifdef ASSERT
    static unsigned int cache_info = 0;
    if (cache_info == 0) {
      asm volatile ("mrs\t%0, ctr_el0":"=r" (cache_info));
    }
    constexpr unsigned int CTR_IDC_SHIFT = 28;
    constexpr unsigned int CTR_DIC_SHIFT = 29;
    assert(((cache_info >> CTR_IDC_SHIFT) & 0x1) != 0x0, "Expect CTR_EL0.IDC to be enabled");
    if (NeoverseN1Errata1542419) {
      assert(((cache_info >> CTR_DIC_SHIFT) & 0x1) == 0x0, "Expect CTR_EL0.DIC to be disabled for Neoverse N1 with erratum 1542419");
    } else {
      assert(((cache_info >> CTR_DIC_SHIFT) & 0x1) != 0x0, "Expect CTR_EL0.DIC to be enabled");
    }
#endif
}

inline void ICacheInvalidationContext::pd_invalidate_icache() {
  if (_mode == ICacheInvalidation::DEFERRED && UseDeferredICacheInvalidation) {
    // For deferred icache invalidation, we expect hardware dcache
    // and icache to be coherent: CTR_EL0.IDC == 1 and CTR_EL0.DIC == 1
    // An exception is Neoverse N1 with erratum 1542419, which requires
    // a use of 'IC IVAU' instruction. In such a case, we expect
    // CTR_EL0.DIC == 0.
    assert_hardware_cache_coherency();

    asm volatile("dsb ish" : : : "memory");

    if (NeoverseN1Errata1542419) {
      // Errata 1542419: Neoverse N1 cores with the 'COHERENT_ICACHE' feature
      // may fetch stale instructions when software depends on
      // prefetch-speculation-protection instead of explicit synchronization.
      //
      // Neoverse-N1 implementation mitigates the errata 1542419 with a
      // workaround:
      // - Disable coherent icache.
      // - Trap IC IVAU instructions.
      // - Execute:
      //   - tlbi vae3is, xzr
      //   - dsb sy
      // - Ignore trapped IC IVAU instructions.
      //
      // `tlbi vae3is, xzr` invalidates all translation entries (all VAs, all
      // possible levels). It waits for all memory accesses using in-scope old
      // translation information to complete before it is considered complete.
      //
      // As this workaround has significant overhead, Arm Neoverse N1 (MP050)
      // Software Developer Errata Notice version 29.0 suggests:
      //
      // "Since one TLB inner-shareable invalidation is enough to avoid this
      // erratum, the number of injected TLB invalidations should be minimized
      // in the trap handler to mitigate the performance impact due to this
      // workaround."
      // As the address for icache invalidation is not relevant and
      // IC IVAU instruction is ignored, we use XZR in it.
      asm volatile("ic  ivau, xzr \n"
                   "dsb ish       \n"
                   : : : "memory");
    }

    asm volatile("isb" : : : "memory");
  }
  NOT_PRODUCT(current_icache_invalidation_context = nullptr);
  _code = nullptr;
  _size = 0;
  _mode = ICacheInvalidation::NOT_NEEDED;
}

// Interface for updating the instruction cache.  Whenever the VM
// modifies code, part of the processor instruction cache potentially
// has to be flushed.

class ICache : public AbstractICache {
 public:
  static void initialize(int phase);
  static void invalidate_word(address addr) {
    __builtin___clear_cache((char *)addr, (char *)(addr + 4));
  }
  static void invalidate_range(address start, int nbytes) {
    if (NeoverseN1Errata1542419) {
      assert_hardware_cache_coherency();
      asm volatile("dsb ish       \n"
                   "ic  ivau, xzr \n"
                   "dsb ish       \n"
                   "isb           \n"
                   : : : "memory");
    } else {
      __builtin___clear_cache((char *)start, (char *)(start + nbytes));
    }
  }
};

#endif // OS_CPU_LINUX_AARCH64_ICACHE_AARCH64_HPP
