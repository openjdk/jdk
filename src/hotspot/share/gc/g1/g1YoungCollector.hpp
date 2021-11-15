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
 *
 */

#ifndef SHARE_GC_G1_G1YOUNGCOLLECTOR_HPP
#define SHARE_GC_G1_G1YOUNGCOLLECTOR_HPP

#include "gc/g1/g1EvacFailureRegions.hpp"
#include "gc/g1/g1YoungGCEvacFailureInjector.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/taskqueue.hpp"

class WorkerTask;
class G1Allocator;
class G1BatchedTask;
class G1CardSetMemoryStats;
class G1CollectedHeap;
class G1CollectionSet;
class G1CollectorState;
class G1ConcurrentMark;
class G1EvacFailureRegions;
class G1EvacInfo;
class G1GCPhaseTimes;
class G1HotCardCache;
class G1HRPrinter;
class G1MonitoringSupport;
class G1NewTracer;
class G1ParScanThreadStateSet;
class G1Policy;
class G1RedirtyCardsQueueSet;
class G1RemSet;
class G1SurvivorRegions;
class G1YoungGCEvacFailureInjector;
class STWGCTimer;
class WorkerThreads;

class outputStream;

class G1YoungCollector {
  friend class G1YoungGCNotifyPauseMark;
  friend class G1YoungGCTraceTime;
  friend class G1YoungGCVerifierMark;

  G1CollectedHeap* _g1h;

  G1Allocator* allocator() const;
  G1CollectionSet* collection_set() const;
  G1CollectorState* collector_state() const;
  G1ConcurrentMark* concurrent_mark() const;
  STWGCTimer* gc_timer_stw() const;
  G1NewTracer* gc_tracer_stw() const;
  G1HotCardCache* hot_card_cache() const;
  G1HRPrinter* hr_printer() const;
  G1MonitoringSupport* monitoring_support() const;
  G1GCPhaseTimes* phase_times() const;
  G1Policy* policy() const;
  G1RemSet* rem_set() const;
  G1ScannerTasksQueueSet* task_queues() const;
  G1SurvivorRegions* survivor_regions() const;
  ReferenceProcessor* ref_processor_stw() const;
  WorkerThreads* workers() const;
  G1YoungGCEvacFailureInjector* evac_failure_injector() const;

  GCCause::Cause _gc_cause;
  double _target_pause_time_ms;

  bool _concurrent_operation_is_full_mark;

  // Evacuation failure tracking.
  G1EvacFailureRegions _evac_failure_regions;

  // Runs the given WorkerTask with the current active workers,
  // returning the total time taken.
  Tickspan run_task_timed(WorkerTask* task);

  void wait_for_root_region_scanning();

  void calculate_collection_set(G1EvacInfo* evacuation_info, double target_pause_time_ms);

  void set_young_collection_default_active_worker_threads();

  void pre_evacuate_collection_set(G1EvacInfo* evacuation_info, G1ParScanThreadStateSet* pss);
  // Actually do the work of evacuating the parts of the collection set.
  // The has_optional_evacuation_work flag for the initial collection set
  // evacuation indicates whether one or more optional evacuation steps may
  // follow.
  // If not set, G1 can avoid clearing the card tables of regions that we scan
  // for roots from the heap: when scanning the card table for dirty cards after
  // all remembered sets have been dumped onto it, for optional evacuation we
  // mark these cards as "Scanned" to know that we do not need to re-scan them
  // in the additional optional evacuation passes. This means that in the "Clear
  // Card Table" phase we need to clear those marks. However, if there is no
  // optional evacuation, g1 can immediately clean the dirty cards it encounters
  // as nobody else will be looking at them again, saving the clear card table
  // work later.
  // This case is very common (young only collections and most mixed gcs), so
  // depending on the ratio between scanned and evacuated regions (which g1 always
  // needs to clear), this is a big win.
  void evacuate_initial_collection_set(G1ParScanThreadStateSet* per_thread_states,
                                       bool has_optional_evacuation_work);
  void evacuate_optional_collection_set(G1ParScanThreadStateSet* per_thread_states);
  // Evacuate the next set of optional regions.
  void evacuate_next_optional_regions(G1ParScanThreadStateSet* per_thread_states);

  // Process any reference objects discovered.
  void process_discovered_references(G1ParScanThreadStateSet* per_thread_states);
  void post_evacuate_cleanup_1(G1ParScanThreadStateSet* per_thread_states);
  void post_evacuate_cleanup_2(G1ParScanThreadStateSet* per_thread_states,
                               G1EvacInfo* evacuation_info);

  void post_evacuate_collection_set(G1EvacInfo* evacuation_info,
                                    G1ParScanThreadStateSet* per_thread_states);

  // True iff an evacuation has failed in the most-recent collection.
  bool evacuation_failed() const;

#if TASKQUEUE_STATS
  uint num_task_queues() const;
  static void print_taskqueue_stats_hdr(outputStream* const st);
  void print_taskqueue_stats() const;
  void reset_taskqueue_stats();
#endif // TASKQUEUE_STATS

public:
  G1YoungCollector(GCCause::Cause gc_cause,
                   double target_pause_time_ms);
  void collect();

  bool concurrent_operation_is_full_mark() const { return _concurrent_operation_is_full_mark; }
};

#endif // SHARE_GC_G1_G1YOUNGCOLLECTOR_HPP
