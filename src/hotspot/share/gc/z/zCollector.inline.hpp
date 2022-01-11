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

#ifndef SHARE_GC_Z_ZCOLLECTOR_INLINE_HPP
#define SHARE_GC_Z_ZCOLLECTOR_INLINE_HPP

#include "gc/z/zAbort.inline.hpp"
#include "gc/z/zCollector.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zWorkers.inline.hpp"
#include "utilities/debug.hpp"

inline bool ZCollector::is_phase_relocate() const {
  return _phase == Phase::Relocate;
}

inline bool ZCollector::is_phase_mark() const {
  return _phase == Phase::Mark;
}

inline bool ZCollector::is_phase_mark_complete() const {
  return _phase == Phase::MarkComplete;
}

inline uint32_t ZCollector::seqnum() const {
  return _seqnum;
}

inline ZGenerationId ZCollector::id() const {
  return _id;
}

inline bool ZCollector::is_young() const {
  return _id == ZGenerationId::young;
}

inline bool ZCollector::is_old() const {
  return _id == ZGenerationId::old;
}

inline ZYoungCollector* ZCollector::young() {
  return _young;
}

inline ZOldCollector* ZCollector::old() {
  return _old;
}

inline ZCollector* ZCollector::collector(ZGenerationId id) {
  if (id == ZGenerationId::young) {
    return _young;
  } else {
    return _old;
  }
}

inline ZForwarding* ZCollector::forwarding(zaddress_unsafe addr) const {
  return _forwarding_table.get(addr);
}

inline bool ZCollector::should_worker_resize() {
  return _workers.should_worker_resize();
}

inline bool ZCollector::should_worker_stop() {
  return ZAbort::should_abort() || should_worker_resize();
}

inline ZStatHeap* ZCollector::stat_heap() {
  return &_stat_heap;
}

inline ZStatCycle* ZCollector::stat_cycle() {
  return &_stat_cycle;
}

inline ZStatWorkers* ZCollector::stat_workers() {
  return &_stat_workers;
}

inline ZStatMark* ZCollector::stat_mark() {
  return &_stat_mark;
}

inline ZStatRelocation* ZCollector::stat_relocation() {
  return &_stat_relocation;
}

inline ZPageTable* ZCollector::page_table() const {
  return _page_table;
}

inline const ZForwardingTable* ZCollector::forwarding_table() const {
  return &_forwarding_table;
}

template <bool resurrect, bool gc_thread, bool follow, bool finalizable>
inline void ZCollector::mark_object(zaddress addr) {
  _mark.mark_object<resurrect, gc_thread, follow, finalizable>(addr);
}

template <bool resurrect, bool gc_thread, bool follow, bool finalizable>
inline void ZCollector::mark_object_if_active(zaddress addr) {
  if (is_phase_mark()) {
    mark_object<resurrect, gc_thread, follow, finalizable>(addr);
  }
}

inline zaddress ZCollector::relocate_or_remap_object(zaddress_unsafe addr) {
  ZForwarding* const forwarding = _forwarding_table.get(addr);
  if (forwarding == NULL) {
    // Not forwarding
    return safe(addr);
  }

  // Relocate object
  return _relocate.relocate_object(forwarding, addr);
}

inline zaddress ZCollector::remap_object(zaddress_unsafe addr) {
  ZForwarding* const forwarding = _forwarding_table.get(addr);
  if (forwarding == NULL) {
    // Not forwarding
    return safe(addr);
  }

  // Remap object
  return _relocate.forward_object(forwarding, addr);
}

inline ZYoungType ZYoungCollector::type() const {
  assert(_active_type != ZYoungType::none, "Invalid type");
  return _active_type;
}

inline void ZYoungCollector::remember(volatile zpointer* p) {
  _remembered.remember(p);
}

inline void ZYoungCollector::remember_fields(zaddress addr) {
  _remembered.remember_fields(addr);
}

inline void ZYoungCollector::scan_remembered_field(volatile zpointer* p) {
  _remembered.scan_field(p);
}

inline bool ZYoungCollector::is_remembered(volatile zpointer* p) const {
  return _remembered.is_remembered(p);
}

inline ReferenceDiscoverer* ZOldCollector::reference_discoverer() {
  return &_reference_processor;
}

#endif // SHARE_GC_Z_ZCOLLECTOR_INLINE_HPP
