/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZOOPCLOSURES_INLINE_HPP
#define SHARE_GC_Z_ZOOPCLOSURES_INLINE_HPP

#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zOop.inline.hpp"
#include "gc/z/zOopClosures.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

inline void ZLoadBarrierOopClosure::do_oop(oop* p) {
  ZBarrier::load_barrier_on_oop_field(p);
}

inline void ZLoadBarrierOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

inline void ZMarkRootOopClosure::do_oop(oop* p) {
  ZBarrier::mark_barrier_on_root_oop_field(p);
}

inline void ZMarkRootOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

inline void ZRelocateRootOopClosure::do_oop(oop* p) {
  ZBarrier::relocate_barrier_on_root_oop_field(p);
}

inline void ZRelocateRootOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

template <bool finalizable>
inline ZMarkBarrierOopClosure<finalizable>::ZMarkBarrierOopClosure() :
    BasicOopIterateClosure(finalizable ? NULL : ZHeap::heap()->reference_discoverer()) {}

template <bool finalizable>
inline void ZMarkBarrierOopClosure<finalizable>::do_oop(oop* p) {
  ZBarrier::mark_barrier_on_oop_field(p, finalizable);
}

template <bool finalizable>
inline void ZMarkBarrierOopClosure<finalizable>::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

inline bool ZPhantomIsAliveObjectClosure::do_object_b(oop o) {
  return ZBarrier::is_alive_barrier_on_phantom_oop(o);
}

inline void ZPhantomKeepAliveOopClosure::do_oop(oop* p) {
  ZBarrier::keep_alive_barrier_on_phantom_oop_field(p);
}

inline void ZPhantomKeepAliveOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

inline void ZPhantomCleanOopClosure::do_oop(oop* p) {
  // Read the oop once, to make sure the liveness check
  // and the later clearing uses the same value.
  const oop obj = *(volatile oop*)p;
  if (ZBarrier::is_alive_barrier_on_phantom_oop(obj)) {
    ZBarrier::keep_alive_barrier_on_phantom_oop_field(p);
  } else {
    // The destination could have been modified/reused, in which case
    // we don't want to clear it. However, no one could write the same
    // oop here again (the object would be strongly live and we would
    // not consider clearing such oops), so therefore we don't have an
    // ABA problem here.
    Atomic::cmpxchg(oop(NULL), p, obj);
  }
}

inline void ZPhantomCleanOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

#endif // SHARE_GC_Z_ZOOPCLOSURES_INLINE_HPP
