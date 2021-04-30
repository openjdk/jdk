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

#ifndef SHARE_GC_Z_ZCYCLE_INLINE_HPP
#define SHARE_GC_Z_ZCYCLE_INLINE_HPP

#include "gc/z/zCycle.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "utilities/debug.hpp"

inline ZPhase ZCycle::phase() const {
  return _phase;
}

inline uint32_t ZCycle::seqnum() const {
  return _seqnum;
}

inline ZCycleId ZCycle::cycle_id() const {
  return _cycle_id;
}

inline bool ZCycle::is_minor() const {
  return _cycle_id == ZCycleId::_minor;
}

inline bool ZCycle::is_major() const {
  return _cycle_id == ZCycleId::_major;
}

inline ZForwarding* ZCycle::forwarding(zaddress_unsafe addr) const {
  return _forwarding_table.get(addr);
}

inline ZStatHeap* ZCycle::stat_heap() {
  return &_stat_heap;
}

inline ZStatCycle* ZCycle::stat_cycle() {
  return &_stat_cycle;
}

inline ZStatMark* ZCycle::stat_mark() {
  return &_stat_mark;
}

inline ZStatRelocation* ZCycle::stat_relocation() {
  return &_stat_relocation;
}

inline ZPageTable* ZCycle::page_table() const {
  return _page_table;
}

inline const ZForwardingTable* ZCycle::forwarding_table() const {
  return &_forwarding_table;
}

template <bool gc_thread, bool follow, bool finalizable, bool publish>
inline void ZCycle::mark_object(zaddress addr) {
  _mark.mark_object<gc_thread, follow, finalizable, publish>(addr);
}

inline void ZCycle::mark_follow_invisible_root(zaddress addr, size_t size) {
  _mark.mark_follow_invisible_root(addr, size);
}

inline zaddress ZCycle::relocate_or_remap_object(zaddress_unsafe addr) {
  ZForwarding* const forwarding = _forwarding_table.get(addr);
  if (forwarding == NULL) {
    // Not forwarding
    return safe(addr);
  }

  // Relocate object
  return _relocate.relocate_object(forwarding, addr);
}

inline zaddress ZCycle::remap_object(zaddress_unsafe addr) {
  ZForwarding* const forwarding = _forwarding_table.get(addr);
  if (forwarding == NULL) {
    // Not forwarding
    return safe(addr);
  }

  // Remap object
  return _relocate.forward_object(forwarding, addr);
}

inline ReferenceDiscoverer* ZMajorCycle::reference_discoverer() {
  return &_reference_processor;
}

#endif // SHARE_GC_Z_ZCYCLE_INLINE_HPP
