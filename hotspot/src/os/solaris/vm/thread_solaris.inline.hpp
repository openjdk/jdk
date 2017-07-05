/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_SOLARIS_VM_THREAD_SOLARIS_INLINE_HPP
#define OS_SOLARIS_VM_THREAD_SOLARIS_INLINE_HPP

#include "runtime/atomic.hpp"
#include "runtime/prefetch.hpp"
#include "runtime/thread.hpp"
#include "runtime/threadLocalStorage.hpp"
#ifdef TARGET_OS_ARCH_solaris_x86
# include "atomic_solaris_x86.inline.hpp"
# include "orderAccess_solaris_x86.inline.hpp"
# include "prefetch_solaris_x86.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_sparc
# include "atomic_solaris_sparc.inline.hpp"
# include "orderAccess_solaris_sparc.inline.hpp"
# include "prefetch_solaris_sparc.inline.hpp"
#endif

// Thread::current is "hot" it's called > 128K times in the 1st 500 msecs of
// startup.
// ThreadLocalStorage::thread is warm -- it's called > 16K times in the same
// period.   Thread::current() now calls ThreadLocalStorage::thread() directly.
// For SPARC, to avoid excessive register window spill-fill faults,
// we aggressively inline these routines.

inline Thread* ThreadLocalStorage::thread()  {
  // don't use specialized code if +UseMallocOnly -- may confuse Purify et al.
  debug_only(if (UseMallocOnly) return get_thread_slow(););

  uintptr_t raw = pd_raw_thread_id();
  int ix = pd_cache_index(raw);
  Thread *Candidate = ThreadLocalStorage::_get_thread_cache[ix];
  if (Candidate->_self_raw_id == raw) {
    // hit
    return Candidate;
  } else {
    return ThreadLocalStorage::get_thread_via_cache_slowly(raw, ix);
  }
}

#endif // OS_SOLARIS_VM_THREAD_SOLARIS_INLINE_HPP
