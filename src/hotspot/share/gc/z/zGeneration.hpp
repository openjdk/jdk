/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZGENERATION_HPP
#define SHARE_GC_Z_ZGENERATION_HPP

#include "gc/z/zForwardingTable.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zMark.hpp"
#include "gc/z/zReferenceProcessor.hpp"
#include "gc/z/zRelocate.hpp"
#include "gc/z/zRelocationSet.hpp"
#include "gc/z/zRemembered.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTracer.hpp"
#include "gc/z/zUnload.hpp"
#include "gc/z/zWeakRootsProcessor.hpp"
#include "gc/z/zWorkers.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"

class ThreadClosure;
class ZForwardingTable;
class ZGenerationOld;
class ZGenerationYoung;
class ZPage;
class ZPageAllocator;
class ZPageTable;
class ZRelocationSetSelector;

class ZGeneration {
  friend class ZForwardingTest;
  friend class ZLiveMapTest;

protected:
  static ZGenerationYoung* _young;
  static ZGenerationOld*   _old;

  enum class Phase {
    Mark,
    MarkComplete,
    Relocate
  };

  const ZGenerationId   _id;
  ZPageAllocator* const _page_allocator;
  ZPageTable* const     _page_table;
  ZForwardingTable      _forwarding_table;
  ZWorkers              _workers;
  ZMark                 _mark;
  ZRelocate             _relocate;
  ZRelocationSet        _relocation_set;

  Atomic<size_t>        _freed;
  Atomic<size_t>        _promoted;
  Atomic<size_t>        _compacted;

  Phase                 _phase;
  uint32_t              _seqnum;

  ZStatHeap             _stat_heap;
  ZStatCycle            _stat_cycle;
  ZStatWorkers          _stat_workers;
  ZStatMark             _stat_mark;
  ZStatRelocation       _stat_relocation;

  ConcurrentGCTimer*    _gc_timer;

  void free_empty_pages(ZRelocationSetSelector* selector, int bulk);
  void flip_age_pages(const ZRelocationSetSelector* selector);

  void mark_free();

  void select_relocation_set(bool promote_all);
  void reset_relocation_set();

  ZGeneration(ZGenerationId id, ZPageTable* page_table, ZPageAllocator* page_allocator);

  void log_phase_switch(Phase from, Phase to);

public:
  // GC phases
  void set_phase(Phase new_phase);
  bool is_phase_relocate() const;
  bool is_phase_mark() const;
  bool is_phase_mark_complete() const;
  const char* phase_to_string() const;

  uint32_t seqnum() const;

  ZGenerationId id() const;
  ZGenerationIdOptional id_optional() const;
  bool is_young() const;
  bool is_old() const;

  static ZGenerationYoung* young();
  static ZGenerationOld* old();
  static ZGeneration* generation(ZGenerationId id);

  // Statistics
  void reset_statistics();
  virtual bool should_record_stats() = 0;
  size_t freed() const;
  void increase_freed(size_t size);
  size_t promoted() const;
  void increase_promoted(size_t size);
  size_t compacted() const;
  void increase_compacted(size_t size);

  ConcurrentGCTimer* gc_timer() const;
  void set_gc_timer(ConcurrentGCTimer* gc_timer);
  void clear_gc_timer();

  ZStatHeap* stat_heap();
  ZStatCycle* stat_cycle();
  ZStatWorkers* stat_workers();
  ZStatMark* stat_mark();
  ZStatRelocation* stat_relocation();

  void at_collection_start(ConcurrentGCTimer* gc_timer);
  void at_collection_end();

  // Workers
  ZWorkers* workers();
  uint active_workers() const;
  void set_active_workers(uint nworkers);

  // Worker resizing
  bool should_worker_resize();

  ZPageTable* page_table() const;
  const ZForwardingTable* forwarding_table() const;
  ZForwarding* forwarding(zaddress_unsafe addr) const;

  ZRelocationSetParallelIterator relocation_set_parallel_iterator();

  // Marking
  template <bool resurrect, bool gc_thread, bool follow, bool finalizable>
  void mark_object(zaddress addr);
  template <bool resurrect, bool gc_thread, bool follow, bool finalizable>
  void mark_object_if_active(zaddress addr);
  void mark_flush(Thread* thread);

  // Relocation
  void synchronize_relocation();
  void desynchronize_relocation();
  bool is_relocate_queue_active() const;
  zaddress relocate_or_remap_object(zaddress_unsafe addr);
  zaddress remap_object(zaddress_unsafe addr);

  // Threads
  void threads_do(ThreadClosure* tc) const;
};

enum class ZYoungType {
  minor,
  major_full_preclean,
  major_full_roots,
  major_partial_roots,
  none
};

class ZYoungTypeSetter {
public:
  ZYoungTypeSetter(ZYoungType type);
  ~ZYoungTypeSetter();
};

class ZGenerationYoung : public ZGeneration {
  friend class VM_ZMarkEndYoung;
  friend class VM_ZMarkStartYoung;
  friend class VM_ZMarkStartYoungAndOld;
  friend class VM_ZRelocateStartYoung;
  friend class ZRemapYoungRootsTask;
  friend class ZYoungTypeSetter;

private:
  ZYoungType   _active_type;
  uint         _tenuring_threshold;
  ZRemembered  _remembered;
  ZYoungTracer _jfr_tracer;

  void flip_mark_start();
  void flip_relocate_start();

  void mark_start();
  void mark_roots();
  void mark_follow();
  bool mark_end();
  void relocate_start();
  void relocate();

  void pause_mark_start();
  void concurrent_mark();
  bool pause_mark_end();
  void concurrent_mark_continue();
  void concurrent_mark_free();
  void concurrent_reset_relocation_set();
  void concurrent_select_relocation_set();
  void pause_relocate_start();
  void concurrent_relocate();

  ZRemembered* remembered();

public:
  ZGenerationYoung(ZPageTable* page_table,
                   const ZForwardingTable* old_forwarding_table,
                   ZPageAllocator* page_allocator);

  ZYoungType type() const;

  void collect(ZYoungType type, ConcurrentGCTimer* timer);

  // Statistics
  bool should_record_stats();

  // Support for promoting object to the old generation
  void flip_promote(ZPage* from_page, ZPage* to_page);
  void in_place_relocate_promote(ZPage* from_page, ZPage* to_page);

  void register_flip_promoted(const ZArray<ZPage*>& pages);
  void register_in_place_relocate_promoted(ZPage* page);

  uint tenuring_threshold();
  void select_tenuring_threshold(ZRelocationSetSelectorStats stats, bool promote_all);
  uint compute_tenuring_threshold(ZRelocationSetSelectorStats stats);

  // Add remembered set entries
  void remember(volatile zpointer* p);
  void remember_fields(zaddress addr);

  // Scan a remembered set entry
  void scan_remembered_field(volatile zpointer* p);

  // Register old pages with remembered set
  void register_with_remset(ZPage* page);

  // Remap the oops of the current remembered set
  void remap_current_remset(ZRemsetTableIterator* iter);

  // Serviceability
  ZGenerationTracer* jfr_tracer();

  // Verification
  bool is_remembered(volatile zpointer* p) const;
};

class ZGenerationOld : public ZGeneration {
  friend class VM_ZMarkEndOld;
  friend class VM_ZMarkStartYoungAndOld;
  friend class VM_ZRelocateStartOld;

private:
  ZReferenceProcessor _reference_processor;
  ZWeakRootsProcessor _weak_roots_processor;
  ZUnload             _unload;
  uint                _total_collections_at_start;
  uint32_t            _young_seqnum_at_reloc_start;
  ZOldTracer          _jfr_tracer;

  void flip_mark_start();
  void flip_relocate_start();

  void mark_start();
  void mark_roots();
  void mark_follow();
  bool mark_end();
  void process_non_strong_references();
  void relocate_start();
  void relocate();
  void remap_young_roots();

  void concurrent_mark();
  bool pause_mark_end();
  void concurrent_mark_continue();
  void concurrent_mark_free();
  void concurrent_process_non_strong_references();
  void concurrent_reset_relocation_set();
  void pause_verify();
  void concurrent_select_relocation_set();
  void pause_relocate_start();
  void concurrent_relocate();
  void concurrent_remap_young_roots();

public:
  ZGenerationOld(ZPageTable* page_table, ZPageAllocator* page_allocator);

  void collect(ConcurrentGCTimer* timer);

  // Statistics
  bool should_record_stats();

  // Reference processing
  ReferenceDiscoverer* reference_discoverer();
  void set_soft_reference_policy(bool clear);
  bool uses_clear_all_soft_reference_policy() const;

  uint total_collections_at_start() const;

  bool active_remset_is_current() const;

  ZRelocateQueue* relocate_queue();

  // Serviceability
  ZGenerationTracer* jfr_tracer();
};

#endif // SHARE_GC_Z_ZGENERATION_HPP
