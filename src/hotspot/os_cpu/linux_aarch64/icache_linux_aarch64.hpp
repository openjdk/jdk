/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/globalDefinitions.hpp"


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
      assert(VM_Version::is_cache_idc_enabled(),
             "Expect CTR_EL0.IDC to be enabled for Neoverse N1 with erratum "
             "1542419");
      assert(!VM_Version::is_cache_dic_enabled(),
             "Expect CTR_EL0.DIC to be disabled for Neoverse N1 with erratum "
             "1542419");
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

class AArch64ICacheInvalidationContext final : StackObj {
 private:
  NONCOPYABLE(AArch64ICacheInvalidationContext);

  static THREAD_LOCAL AArch64ICacheInvalidationContext* _current_context;

  AArch64ICacheInvalidationContext* _parent;
  address                           _code;
  int                               _size;
  ICacheInvalidation                _mode;
  bool                              _has_modified_code;

 public:
  AArch64ICacheInvalidationContext(ICacheInvalidation mode)
      : _parent(nullptr), _code(nullptr), _size(0), _mode(mode), _has_modified_code(false) {
    _parent = _current_context;
    _current_context = this;
    if (_parent != nullptr) {
      // The parent context is in charge of icache invalidation.
      _mode = (_parent->mode() == ICacheInvalidation::IMMEDIATE) ? ICacheInvalidation::IMMEDIATE : ICacheInvalidation::NOT_NEEDED;
    }
  }

  AArch64ICacheInvalidationContext()
      : AArch64ICacheInvalidationContext(UseDeferredICacheInvalidation
                                            ? ICacheInvalidation::DEFERRED
                                            : ICacheInvalidation::IMMEDIATE) {}

  AArch64ICacheInvalidationContext(address code, int size)
      : _parent(nullptr),
        _code(code),
        _size(size),
        _mode(ICacheInvalidation::DEFERRED),
        _has_modified_code(true) {
    assert(_current_context == nullptr,
           "nested ICacheInvalidationContext(code, size) not supported");
    assert(code != nullptr, "code must not be null for deferred invalidation");
    assert(size > 0, "size must be positive for deferred invalidation");

    _current_context = this;

    if (UseDeferredICacheInvalidation) {
      // With hardware dcache and icache coherency, we don't need _code.
      _code = nullptr;
      _size = 0;
    }
  }

  ~AArch64ICacheInvalidationContext() {
    _current_context = _parent;

    if (_code != nullptr) {
      assert(_size > 0, "size must be positive for deferred invalidation");
      assert(_mode == ICacheInvalidation::DEFERRED, "sanity");
      assert(_has_modified_code, "sanity");
      assert(_parent == nullptr, "sanity");

      ICache::invalidate_range(_code, _size);
      return;
    }

    if (!_has_modified_code) {
      return;
    }

    if (_parent != nullptr) {
      _parent->set_has_modified_code();
    }

    if (_mode != ICacheInvalidation::DEFERRED) {
      return;
    }

    assert(VM_Version::is_cache_idc_enabled(), "Expect CTR_EL0.IDC to be enabled");

    asm volatile("dsb ish" : : : "memory");

    if (NeoverseN1Errata1542419) {
      assert(!VM_Version::is_cache_dic_enabled(),
             "Expect CTR_EL0.DIC to be disabled for Neoverse N1 with erratum "
             "1542419");

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
      asm volatile(
          "ic  ivau, xzr \n"
          "dsb ish       \n"
          :
          :
          : "memory");
    } else {
      assert(VM_Version::is_cache_dic_enabled(), "Expect CTR_EL0.DIC to be enabled");
    }
    asm volatile("isb" : : : "memory");
  }

  ICacheInvalidation mode() const { return _mode; }

  void set_has_modified_code() {
    _has_modified_code = true;
  }

  static AArch64ICacheInvalidationContext* current() {
    return _current_context;
  }

  static void invalidate_range(address start, int nbytes) {
    if (UseDeferredICacheInvalidation) {
      assert(_current_context != nullptr &&
             (_current_context->mode() == ICacheInvalidation::DEFERRED ||
              _current_context->mode() == ICacheInvalidation::NOT_NEEDED),
            "UseDeferredICacheInvalidation requires ICache invalidation mode to be deferred or unneeded.");
      return;
    }

    ICache::invalidate_range(start, nbytes);
  }

  static void invalidate_word(address addr) {
    invalidate_range(addr, 4);
  }
};

#define PD_ICACHE_INVALIDATION_CONTEXT AArch64ICacheInvalidationContext

#endif // OS_CPU_LINUX_AARCH64_ICACHE_AARCH64_HPP
