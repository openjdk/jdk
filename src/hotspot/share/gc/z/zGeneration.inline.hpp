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

#ifndef SHARE_GC_Z_ZGENERATION_INLINE_HPP
#define SHARE_GC_Z_ZGENERATION_INLINE_HPP

#include "gc/z/zGeneration.hpp"

#include "gc/z/zAbort.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zWorkers.inline.hpp"
#include "utilities/debug.hpp"

inline bool ZGeneration::is_phase_relocate() const {
  return _phase == Phase::Relocate;
}

inline bool ZGeneration::is_phase_mark() const {
  return _phase == Phase::Mark;
}

inline bool ZGeneration::is_phase_mark_complete() const {
  return _phase == Phase::MarkComplete;
}

inline uint32_t ZGeneration::seqnum() const {
  return _seqnum;
}

inline ZGenerationId ZGeneration::id() const {
  return _id;
}

inline ZGenerationIdOptional ZGeneration::id_optional() const {
  return static_cast<ZGenerationIdOptional>(_id);
}

inline bool ZGeneration::is_young() const {
  return _id == ZGenerationId::young;
}

inline bool ZGeneration::is_old() const {
  return _id == ZGenerationId::old;
}

inline ZGenerationYoung* ZGeneration::young() {
  return _young;
}

inline ZGenerationOld* ZGeneration::old() {
  return _old;
}

inline ZGeneration* ZGeneration::generation(ZGenerationId id) {
  if (id == ZGenerationId::young) {
    return _young;
  } else {
    return _old;
  }
}

inline ZForwarding* ZGeneration::forwarding(zaddress_unsafe addr) const {
  return _forwarding_table.get(addr);
}

inline bool ZGeneration::should_worker_resize() {
  return _workers.should_worker_resize();
}

inline ZStatHeap* ZGeneration::stat_heap() {
  return &_stat_heap;
}

inline ZStatCycle* ZGeneration::stat_cycle() {
  return &_stat_cycle;
}

inline ZStatWorkers* ZGeneration::stat_workers() {
  return &_stat_workers;
}

inline ZStatMark* ZGeneration::stat_mark() {
  return &_stat_mark;
}

inline ZStatRelocation* ZGeneration::stat_relocation() {
  return &_stat_relocation;
}

inline ZPageTable* ZGeneration::page_table() const {
  return _page_table;
}

inline const ZForwardingTable* ZGeneration::forwarding_table() const {
  return &_forwarding_table;
}

template <bool resurrect, bool gc_thread, bool follow, bool finalizable>
inline void ZGeneration::mark_object(zaddress addr) {
  assert(is_phase_mark(), "Should be marking");
  _mark.mark_object<resurrect, gc_thread, follow, finalizable>(addr);
}

template <bool resurrect, bool gc_thread, bool follow, bool finalizable>
inline void ZGeneration::mark_object_if_active(zaddress addr) {
  if (is_phase_mark()) {
    mark_object<resurrect, gc_thread, follow, finalizable>(addr);
  }
}

inline zaddress ZGeneration::relocate_or_remap_object(zaddress_unsafe addr) {
  ZForwarding* const forwarding = _forwarding_table.get(addr);
  if (forwarding == nullptr) {
    // Not forwarding
    return safe(addr);
  }

  // Relocate object
  return _relocate.relocate_object(forwarding, addr);
}

inline zaddress ZGeneration::remap_object(zaddress_unsafe addr) {
  ZForwarding* const forwarding = _forwarding_table.get(addr);
  if (forwarding == nullptr) {
    // Not forwarding
    return safe(addr);
  }

  // Remap object
  return _relocate.forward_object(forwarding, addr);
}

inline ZYoungType ZGenerationYoung::type() const {
  assert(_active_type != ZYoungType::none, "Invalid type");
  return _active_type;
}

inline void ZGenerationYoung::remember(volatile zpointer* p) {
  _remembered.remember(p);
}

inline void ZGenerationYoung::scan_remembered_field(volatile zpointer* p) {
  _remembered.scan_field(p);
}

inline bool ZGenerationYoung::is_remembered(volatile zpointer* p) const {
  return _remembered.is_remembered(p);
}

inline ReferenceDiscoverer* ZGenerationOld::reference_discoverer() {
  return &_reference_processor;
}

inline bool ZGenerationOld::active_remset_is_current() const {
  assert(_young_seqnum_at_reloc_start != 0, "Must be set before used");

  // The remembered set bits flip every time a new young collection starts
  const uint32_t seqnum = ZGeneration::young()->seqnum();
  const uint32_t seqnum_diff = seqnum - _young_seqnum_at_reloc_start;
  const bool in_current = (seqnum_diff & 1u) == 0u;
  return in_current;
}

inline ZRelocateQueue* ZGenerationOld::relocate_queue() {
  return _relocate.queue();
}

#endif // SHARE_GC_Z_ZGENERATION_INLINE_HPP
