/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZUNCOLOREDROOT_INLINE_HPP
#define SHARE_GC_Z_ZUNCOLOREDROOT_INLINE_HPP

#include "gc/z/zUncoloredRoot.hpp"

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "oops/oop.hpp"

template <typename ObjectFunctionT>
inline void ZUncoloredRoot::barrier(ObjectFunctionT function, zaddress_unsafe* p, uintptr_t color) {
  z_verify_safepoints_are_blocked();

  const zaddress_unsafe addr = Atomic::load(p);
  assert_is_valid(addr);

  // Nothing to do for nulls
  if (is_null(addr)) {
    return;
  }

  // Make load good
  const zaddress load_good_addr = make_load_good(addr, color);

  // Apply function
  function(load_good_addr);

  // Non-atomic healing helps speed up root scanning. This is safe to do
  // since we are always healing roots in a safepoint, or under a lock,
  // which ensures we are never racing with mutators modifying roots while
  // we are healing them. It's also safe in case multiple GC threads try
  // to heal the same root if it is aligned, since they would always heal
  // the root in the same way and it does not matter in which order it
  // happens. For misaligned oops, there needs to be mutual exclusion.
  *(zaddress*)p = load_good_addr;
}

inline zaddress ZUncoloredRoot::make_load_good(zaddress_unsafe addr, uintptr_t color) {
  const zpointer color_ptr = ZAddress::color(zaddress::null, color);
  if (!ZPointer::is_load_good(color_ptr)) {
    return ZBarrier::relocate_or_remap(addr, ZBarrier::remap_generation(color_ptr));
  } else {
    return safe(addr);
  }
}

inline void ZUncoloredRoot::mark_object(zaddress addr) {
  ZBarrier::mark<ZMark::DontResurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong>(addr);
}

inline void ZUncoloredRoot::mark_young_object(zaddress addr) {
  ZBarrier::mark_if_young<ZMark::DontResurrect, ZMark::GCThread, ZMark::Follow>(addr);
}

inline void ZUncoloredRoot::mark_invisible_object(zaddress addr) {
  ZBarrier::mark<ZMark::DontResurrect, ZMark::AnyThread, ZMark::DontFollow, ZMark::Strong>(addr);
}

inline void ZUncoloredRoot::keep_alive_object(zaddress addr) {
  ZBarrier::mark<ZMark::Resurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong>(addr);
}

inline void ZUncoloredRoot::mark(zaddress_unsafe* p, uintptr_t color) {
  barrier(mark_object, p, color);
}

inline void ZUncoloredRoot::mark_young(zaddress_unsafe* p, uintptr_t color) {
  barrier(mark_young_object, p, color);
}

inline void ZUncoloredRoot::process(zaddress_unsafe* p, uintptr_t color) {
  barrier(mark_object, p, color);
}

inline void ZUncoloredRoot::process_invisible(zaddress_unsafe* p, uintptr_t color) {
  barrier(mark_invisible_object, p, color);
}

inline void ZUncoloredRoot::process_weak(zaddress_unsafe* p, uintptr_t color) {
  barrier(keep_alive_object, p, color);
}

inline void ZUncoloredRoot::process_no_keepalive(zaddress_unsafe* p, uintptr_t color) {
  auto do_nothing = [](zaddress) -> void {};
  barrier(do_nothing, p, color);
}

inline zaddress_unsafe* ZUncoloredRoot::cast(oop* p) {
  zaddress_unsafe* const root = (zaddress_unsafe*)p;
  DEBUG_ONLY(assert_is_valid(*root);)
  return root;
}

inline ZUncoloredRootMarkOopClosure::ZUncoloredRootMarkOopClosure(uintptr_t color)
  : _color(color) {}

inline void ZUncoloredRootMarkOopClosure::do_root(zaddress_unsafe* p) {
  ZUncoloredRoot::mark(p, _color);
}

inline ZUncoloredRootMarkYoungOopClosure::ZUncoloredRootMarkYoungOopClosure(uintptr_t color)
  : _color(color) {}

inline void ZUncoloredRootMarkYoungOopClosure::do_root(zaddress_unsafe* p) {
  ZUncoloredRoot::mark_young(p, _color);
}

inline ZUncoloredRootProcessOopClosure::ZUncoloredRootProcessOopClosure(uintptr_t color)
  : _color(color) {}

inline void ZUncoloredRootProcessOopClosure::do_root(zaddress_unsafe* p) {
  ZUncoloredRoot::process(p, _color);
}

inline ZUncoloredRootProcessWeakOopClosure::ZUncoloredRootProcessWeakOopClosure(uintptr_t color)
  : _color(color) {}

inline void ZUncoloredRootProcessWeakOopClosure::do_root(zaddress_unsafe* p) {
  ZUncoloredRoot::process_weak(p, _color);
}

inline ZUncoloredRootProcessNoKeepaliveOopClosure::ZUncoloredRootProcessNoKeepaliveOopClosure(uintptr_t color)
  : _color(color) {}

inline void ZUncoloredRootProcessNoKeepaliveOopClosure::do_root(zaddress_unsafe* p) {
  ZUncoloredRoot::process_no_keepalive(p, _color);
}

#endif // SHARE_GC_Z_ZUNCOLOREDROOT_INLINE_HPP
