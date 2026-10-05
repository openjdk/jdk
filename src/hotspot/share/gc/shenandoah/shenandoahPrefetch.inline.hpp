/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHPREFETCH_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHPREFETCH_INLINE_HPP

// No shenandoahPrefetch.hpp

#include "memory/allStatic.hpp"
#include "runtime/prefetch.inline.hpp"

// Utility to centralize prefetching decisions.
//
// Prefetching needs to strike the balance between the latency savings
// from upcoming accesses and the excess memory throughput for accesses
// that are prefetched but are never used.
//
// A common access pattern for the object in hot GC code is:
//   [mark word]   // sometimes, for forwarding pointer accesses
//   [klass word]  // very often, to discover object type
//    ...
//   [oop field N] // often, to traverse the heap or fix references
//
// Prefetches work on cache line granularity, so we can pick and choose
// good static offsets at which to prefetch. It also frees us from
// polling mark/klass word offsets at runtime.
//
// It stands to reason that prefetching at zero is most beneficial.
// Since it is almost guaranteed to be used by future accesses, there is
// little downside. For objects that are fully within the cache line,
// that zero-prefetch also picks up oop fields nicely.
//
// Experiments suggest it is also important to handle the case when
// object crosses the cache line. In this case, zero-prefetch is likely
// to miss the oop fields cache line. In extreme case, it can prefetch only
// the mark word, leaving klass word unprefetched. We can prefetch
// the full next cache line to deal with this case, but it is wasteful,
// especially on platforms with very large cache lines.
//
// Therefore, the second prefetch is done at some small offset to balance
// the crossing case. If second prefetch hits the same cache line as the
// first one, there is little downside. This also works automagically with
// platforms with larger cache line sizes, as both prefetches would converge.
// If prefetch hits another cache line, it likely means the object crosses
// the cache line, and that the second prefetch is profitable.
//
class ShenandoahPrefetch : AllStatic {
public:
  static void prefetch(oop obj) {
    void* addr = obj->base_addr();
    Prefetch::read(addr, 0);
    Prefetch::read(addr, 32);
  }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPREFETCH_INLINE_HPP
