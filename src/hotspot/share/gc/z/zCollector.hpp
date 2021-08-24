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

#ifndef SHARE_GC_Z_ZCOLLECTOR_HPP
#define SHARE_GC_Z_ZCOLLECTOR_HPP

#include "gc/z/zCollectorId.hpp"
#include "gc/z/zForwardingTable.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zMark.hpp"
#include "gc/z/zReferenceProcessor.hpp"
#include "gc/z/zRelocate.hpp"
#include "gc/z/zRelocationSet.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zUnload.hpp"
#include "gc/z/zWeakRootsProcessor.hpp"
#include "gc/z/zWorkers.hpp"
#include "memory/allocation.hpp"

class ThreadClosure;
class ZForwardingTable;
class ZPage;
class ZPageAllocator;
class ZPageTable;
class ZRelocationSetSelector;

class ZCollector {
  friend class ZForwardingTest;
  friend class ZLiveMapTest;

protected:
  enum class Phase {
    Mark,
    MarkComplete,
    Relocate
  };

  ZCollectorId      _id;
  ZPageAllocator*   _page_allocator;
  ZPageTable*       _page_table;
  ZForwardingTable  _forwarding_table;
  ZWorkers          _workers;
  ZMark             _mark;
  ZRelocate         _relocate;
  ZRelocationSet    _relocation_set;

  size_t            _used_high;
  size_t            _used_low;
  ssize_t           _reclaimed;

  Phase             _phase;
  uint32_t          _seqnum;

  ZStatHeap         _stat_heap;
  ZStatCycle        _stat_cycle;
  ZStatMark         _stat_mark;
  ZStatRelocation   _stat_relocation;

  ConcurrentGCTimer _timer;

  void free_empty_pages(ZRelocationSetSelector* selector, int bulk);
  void promote_pages(ZRelocationSetSelector* selector);
  void promote_pages(const ZArray<ZPage*>* pages);

  ZCollector(ZCollectorId id, const char* worker_prefix, ZPageTable* page_table, ZPageAllocator* page_allocator);

  void log_phase_switch(Phase from, Phase to);

public:
  // GC phases
  void set_phase(Phase new_phase);
  bool is_phase_relocate() const;
  bool is_phase_mark() const;
  bool is_phase_mark_complete() const;
  const char* phase_to_string() const;

  uint32_t seqnum() const;

  ZCollectorId id() const;
  bool is_minor() const;
  bool is_major() const;

  // Statistics
  void reset_statistics();
  size_t used_high() const;
  size_t used_low() const;
  ssize_t reclaimed() const;
  void decrease_reclaimed(size_t size);
  void increase_reclaimed(size_t size);
  void update_used(size_t used);

  ConcurrentGCTimer* timer();

  ZStatHeap* stat_heap();
  ZStatCycle* stat_cycle();
  ZStatMark* stat_mark();
  ZStatRelocation* stat_relocation();

  // Workers
  ZWorkers* workers();
  uint active_workers() const;
  void set_active_workers(uint nworkers);

  ZPageTable* page_table() const;
  const ZForwardingTable* forwarding_table() const;

  ZForwarding* forwarding(zaddress_unsafe addr) const;

  // Marking
  template <bool resurrect, bool gc_thread, bool follow, bool finalizable, bool publish>
  void mark_object(zaddress addr);
  void mark_follow_invisible_root(zaddress addr, size_t size);
  void mark_flush_and_free(Thread* thread);
  void mark_free();

  // Relocation set
  void select_relocation_set();
  void reset_relocation_set();

  // Relocation
  void synchronize_relocation();
  void desynchronize_relocation();
  zaddress relocate_or_remap_object(zaddress_unsafe addr);
  zaddress remap_object(zaddress_unsafe addr);

  // Threads
  void threads_do(ThreadClosure* tc) const;
};

class ZMinorCollector : public ZCollector {
private:
  bool _skip_mark_start;

public:
  ZMinorCollector(ZPageTable* page_table, ZPageAllocator* page_allocator);

  // GC operations
  void mark_start();
  void mark_roots();
  void mark_follow();
  bool mark_end();

  bool should_skip_mark_start();
  void skip_mark_start();

  void relocate_start();
  void relocate();

  void promote_flip(ZPage* page, ZPage* new_page);
  void promote_reloc(ZPage* page, ZPage* new_page);
};

class ZMajorCollector : public ZCollector {
private:
  ZReferenceProcessor _reference_processor;
  ZWeakRootsProcessor _weak_roots_processor;
  ZUnload             _unload;
  int                 _total_collections_at_end;

public:
  ZMajorCollector(ZPageTable* page_table, ZPageAllocator* page_allocator);

  // Statistics
  void reset_statistics();

  // Reference processing
  ReferenceDiscoverer* reference_discoverer();
  void set_soft_reference_policy(bool clear);

  // Non-strong reference processing
  void process_non_strong_references();

  // GC operations
  void mark_start();
  void mark_roots();
  void mark_follow();
  bool mark_end();
  void relocate_start();
  void relocate();
  void roots_remap();

  int total_collections_at_end() const;
};

#endif // SHARE_GC_Z_ZCOLLECTOR_HPP
