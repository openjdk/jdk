/*
 * Copyright (c) 2017, 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHPHASETIMINGS_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHPHASETIMINGS_HPP

#include "gc/shared/workerDataArray.hpp"
#include "gc/shenandoah/shenandoahNumberSeq.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/allocation.hpp"

class ShenandoahCollectorPolicy;
class outputStream;

#define SHENANDOAH_WORKER_PHASE_DO(NAME_PREFIX, DESC_PREFIX, f)             \
  f(NAME_PREFIX ## Work,                 DESC_PREFIX "Work",        false)  \
  f(NAME_PREFIX ## Threads,              DESC_PREFIX "Threads",     false)  \
  f(NAME_PREFIX ## CodeCache,            DESC_PREFIX "Code Cache",  false)  \
  f(NAME_PREFIX ## VMStrongs,            DESC_PREFIX "VM Strongs",  false)  \
  f(NAME_PREFIX ## VMWeaks,              DESC_PREFIX "VM Weaks",    false)  \
  f(NAME_PREFIX ## Classes,              DESC_PREFIX "Classes",     false)  \
  // END

#define SHENANDOAH_SIMPLE_PHASE_DEF(f, NAME, DESC)    \
  f(NAME, DESC, false)

#define SHENANDOAH_WORKER_PHASE_DEF(f, NAME_PREFIX, MAIN_DESC, DESC_PREFIX)    \
  f(NAME_PREFIX, MAIN_DESC, true)                                              \
  SHENANDOAH_WORKER_PHASE_DO(NAME_PREFIX, DESC_PREFIX, f)

#define SHENANDOAH_PHASE_DO(f)                                                                                       \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_reset,                                      "Concurrent Reset")                \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_mark_gross,                                 "Pause Init Mark (G)")             \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_mark,                                       "Pause Init Mark (N)")             \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_mark_verify,                                "  Verify")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_manage_tlabs,                               "  Manage TLABs")                  \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_swap_rset,                                  "  Swap Remembered Set")           \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_transfer_satb,                              "  Transfer Old From SATB")        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_update_region_states,                       "  Update Region States")          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_propagate_gc_state,                         "  Propagate GC State")            \
  SHENANDOAH_WORKER_PHASE_DEF(f, init_scan_rset,                                  "Concurrent Scan Remembered Set",  \
                                                                                  "  RS: ")                          \
  SHENANDOAH_WORKER_PHASE_DEF(f, conc_mark_roots,                                 "Concurrent Mark Roots",           \
                                                                                  "  CMR: ")                         \
  SHENANDOAH_WORKER_PHASE_DEF(f, conc_mark,                                       "Concurrent Marking",              \
                                                                                  "  CM: ")                          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_mark_satb_flush,                            "  Flush SATB")                    \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_mark_gross,                                "Pause Final Mark (G)")            \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_mark,                                      "Pause Final Mark (N)")            \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_mark_verify,                               "  Verify")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_mark_flush_satb_roots,                     "  Flush SATB and Roots")          \
  SHENANDOAH_WORKER_PHASE_DEF(f, finish_mark,                                     "  Finish Mark",                   \
                                                                                  "    FM: ")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_mark_propagate_gc_state,                   "  Propagate GC State")            \
  SHENANDOAH_WORKER_PHASE_DEF(f, purge,                                           "  System Purge",                  \
                                                                                  "      CU: ")                      \
  SHENANDOAH_WORKER_PHASE_DEF(f, purge_weak_par,                                  "    Weak Roots",                  \
                                                                                  "      WR: ")                      \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_update_region_states,                      "  Update Region States")          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_manage_labs,                               "  Manage GC/TLABs")               \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, choose_cset,                                     "  Choose Collection Set")         \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_rebuild_freeset,                           "  Rebuild Free Set")              \
  SHENANDOAH_WORKER_PHASE_DEF(f, conc_thread_roots,                               "Concurrent Thread Roots",         \
                                                                                  "  CTR: ")                         \
  SHENANDOAH_WORKER_PHASE_DEF(f, conc_weak_refs,                                  "Concurrent Weak References",      \
                                                                                  "  CWRF: ")                        \
  SHENANDOAH_WORKER_PHASE_DEF(f, conc_weak_roots,                                 "Concurrent Weak Roots",           \
                                                                                  "  CWR: ")                         \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_weak_roots_rendezvous,                      "  Rendezvous")                    \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_cleanup_early,                              "Concurrent Cleanup, Early")       \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_class_unload,                               "Concurrent Class Unloading")      \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_class_unload_unlink,                        "  Unlink Stale")                  \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_class_unload_unlink_sd,                     "    System Dictionary")           \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_class_unload_unlink_weak_klass,             "    Weak Class Links")            \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_class_unload_unlink_code_roots,             "    Code Roots")                  \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_class_unload_rendezvous,                    "  Rendezvous")                    \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_class_unload_purge,                         "  Purge Unlinked")                \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_class_unload_purge_coderoots,               "    Code Roots")                  \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_class_unload_purge_cldg,                    "    CLDG")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_class_unload_purge_ec,                      "    Exception Caches")            \
  SHENANDOAH_WORKER_PHASE_DEF(f, conc_strong_roots,                               "Concurrent Strong Roots",         \
                                                                                  "  CSR: ")                         \
  SHENANDOAH_WORKER_PHASE_DEF(f, conc_evac,                                       "Concurrent Evacuation",           \
                                                                                  "  CE: ")                          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_update_card_table,                          "Concurrent Update Cards")         \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_final_roots,                                "Concurrent Final Roots")          \
  SHENANDOAH_WORKER_PHASE_DEF(f, promote_in_place,                                "  Promote Regions",               \
                                                                                  "    PIP: ")                       \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_verify_gross,                              "Pause Final Verify (G)")          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_verify,                                    "Pause Final Verify (N)")          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_update_refs_gross,                          "Pause Init Update Refs (G)")      \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_update_refs,                                "Pause Init Update Refs (N)")      \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, init_update_refs_verify,                         "  Verify")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_update_refs_prepare,                        "Concurrent Update Refs Prepare")  \
  SHENANDOAH_WORKER_PHASE_DEF(f, conc_update_refs,                                "Concurrent Update Refs",          \
                                                                                  "  CUR: ")                         \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_update_thread_roots,                        "Concurrent Update Thread Roots")  \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_update_refs_gross,                         "Pause Final Update Refs (G)")     \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_update_refs,                               "Pause Final Update Refs (N)")     \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_update_refs_verify,                        "  Verify")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_update_refs_update_region_states,          "  Update Region States")          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_update_refs_transfer_satb,                 "  Transfer Old From SATB")        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_update_refs_trash_cset,                    "  Trash Collection Set")          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_update_refs_rebuild_freeset,               "  Rebuild Free Set")              \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, final_update_refs_propagate_gc_state,            "  Propagate GC State")            \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_cleanup_complete,                           "Concurrent Cleanup, Complete")    \
  SHENANDOAH_WORKER_PHASE_DEF(f, conc_coalesce_and_fill,                          "Concurrent Coalesce and Fill",    \
                                                                                  "  CC&F: ")                        \
                                                                                                                     \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_gross,                                  "Pause Degenerated GC (G)")        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc,                                        "Pause Degenerated GC (N)")        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_un_self_forward,                        "  Un-Self-Forward")               \
  SHENANDOAH_WORKER_PHASE_DEF(f, degen_gc_mark,                                   "  Mark",                          \
                                                                                  "    DM: ")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_purge,                                  "  System Purge")                  \
  SHENANDOAH_WORKER_PHASE_DEF(f, degen_gc_weakrefs,                               "    Weak References",             \
                                                                                  "      WRP: ")                     \
  SHENANDOAH_WORKER_PHASE_DEF(f, degen_gc_purge_class_unload,                     "    Unload Classes",              \
                                                                                  "      DCU: ")                     \
  SHENANDOAH_WORKER_PHASE_DEF(f, degen_gc_purge_weak_par,                         "    Weak Roots",                  \
                                                                                  "      DWR: ")                     \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_purge_cldg,                             "    CLDG")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_final_update_region_states,             "  Update Region States")          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_final_manage_labs,                      "  Manage GC/TLABs")               \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_choose_cset,                            "  Choose Collection Set")         \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_final_rebuild_freeset,                  "  Rebuild Free Set")              \
  SHENANDOAH_WORKER_PHASE_DEF(f, degen_gc_evac,                                   "  Evacuation",                    \
                                                                                  "    DE: ")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_init_update_refs_manage_gclabs,         "  Manage GCLABs")                 \
  SHENANDOAH_WORKER_PHASE_DEF(f, degen_gc_update_refs,                            "  Update References",             \
                                                                                  "    DUR: ")                       \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_final_update_refs_update_region_states, "  Update Region States")          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_final_update_refs_trash_cset,           "  Trash Collection Set")          \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_final_update_refs_rebuild_freeset,      "  Rebuild Free Set")              \
  SHENANDOAH_WORKER_PHASE_DEF(f, degen_gc_update_roots,                           "  Degen Update Roots",            \
                                                                                  "    DU: ")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_cleanup_complete,                       "  Cleanup")                       \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_promote_regions,                        "  Degen Promote Regions")         \
  SHENANDOAH_WORKER_PHASE_DEF(f, degen_gc_coalesce_and_fill,                      "  Degen Coalesce and Fill",       \
                                                                                  "    DC&F")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, degen_gc_propagate_gc_state,                     "  Propagate GC State")            \
                                                                                                                     \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_gross,                                   "Pause Full GC (G)")               \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc,                                         "Pause Full GC (N)")               \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_un_self_forward,                         "  Un-Self-Forward")               \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_heapdump_pre,                            "  Pre Heap Dump")                 \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_prepare,                                 "  Prepare")                       \
  SHENANDOAH_WORKER_PHASE_DEF(f, full_gc_update_roots,                            "    Update Roots",                \
                                                                                  "      FU: ")                      \
  SHENANDOAH_WORKER_PHASE_DEF(f, full_gc_mark,                                    "  Mark",                          \
                                                                                  "    FM: ")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_purge,                                   "  System Purge")                  \
  SHENANDOAH_WORKER_PHASE_DEF(f, full_gc_weakrefs,                                "    Weak References",             \
                                                                                  "      WRP: ")                     \
  SHENANDOAH_WORKER_PHASE_DEF(f, full_gc_purge_class_unload,                      "    Unload Classes",              \
                                                                                  "      CU: ")                      \
  SHENANDOAH_WORKER_PHASE_DEF(f, full_gc_purge_weak_par,                          "    Weak Roots",                  \
                                                                                  "      WR: ")                      \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_purge_cldg,                              "    CLDG")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_calculate_addresses,                     "  Calculate Addresses")           \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_calculate_addresses_regular,             "    Regular Objects")             \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_calculate_addresses_humong,              "    Humongous Objects")           \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_adjust_pointers,                         "  Adjust Pointers")               \
  SHENANDOAH_WORKER_PHASE_DEF(f, full_gc_adjust_roots,                            "  Adjust Roots",                  \
                                                                                  "    FA: ")                        \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_copy_objects,                            "  Copy Objects")                  \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_copy_objects_regular,                    "    Regular Objects")             \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_copy_objects_humong,                     "    Humongous Objects")           \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_recompute_generation_usage,              "    Recompute generation usage")  \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_copy_objects_reset_complete,             "    Reset Complete Bitmap")       \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_copy_objects_rebuild,                    "    Rebuild Region Sets")         \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_reconstruct_remembered_set,              "    Reconstruct Remembered Set")  \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_heapdump_post,                           "  Post Heap Dump")                \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, full_gc_propagate_gc_state,                      "  Propagate GC State")            \
                                                                                                                     \
  SHENANDOAH_SIMPLE_PHASE_DEF(f, conc_reset_after_collect,                        "Concurrent Reset After Collect")  \
                                                                                                                     \
  SHENANDOAH_WORKER_PHASE_DEF(f, heap_iteration_roots,                            "Heap Iteration",                  \
                                                                                  "  HI: ")                          \
  // END

typedef WorkerDataArray<double> ShenandoahWorkerData;

class ShenandoahPhaseTimings : public CHeapObj<mtGC> {
  friend class ShenandoahGCPhase;
  friend class ShenandoahWorkerTimingsTracker;
public:
#define SHENANDOAH_PHASE_DECLARE_ENUM(name, desc, has_worker_phase) name,

  enum Phase {
    SHENANDOAH_PHASE_DO(SHENANDOAH_PHASE_DECLARE_ENUM)
    _num_phases,
    _invalid_phase = _num_phases
  };

  enum WorkerPhase {
    SHENANDOAH_WORKER_PHASE_DO(,, SHENANDOAH_PHASE_DECLARE_ENUM)
    _num_par_phases
  };

#undef SHENANDOAH_PHASE_DECLARE_ENUM

private:
  uint                _max_workers;
  double              _cycle_data[_num_phases];
  HdrSeq              _global_data[_num_phases];
  static const char*  _desc[_num_phases];
  static bool         _has_worker_phase[_num_phases];

  ShenandoahWorkerData* _worker_data[_num_phases];
  ShenandoahCollectorPolicy* _policy;

  static bool is_root_work_phase(Phase phase);

  ShenandoahWorkerData* worker_data(Phase phase, WorkerPhase par_phase);
  static Phase compute_phase_slot(Phase phase, WorkerPhase worker_phase);

  void set_cycle_data(Phase phase, double time, bool should_aggregate = false);
  static double uninitialized() { return -1; }

public:
  ShenandoahPhaseTimings(uint max_workers);

  void record_phase_time(Phase phase, double time, bool should_aggregate = false);

  void record_workers_start(Phase phase);
  void record_workers_end(Phase phase);

  void flush_par_workers_to_cycle();
  void flush_cycle_to_global();

  static const char* phase_desc(Phase phase) {
    assert(phase >= 0 && phase < _num_phases, "Out of bounds: %d", phase);
    return _desc[phase];
  }

  static bool has_worker_phases(Phase phase) {
    assert(phase >= 0 && phase < _num_phases, "Out of bounds: %d", phase);
    return _has_worker_phase[phase];
  }

  void print_cycle_on(outputStream* out) const;
  void print_global_on(outputStream* out) const;
};

class ShenandoahWorkerTimingsTracker : public StackObj {
private:
  ShenandoahPhaseTimings*          const _timings;
  ShenandoahPhaseTimings::Phase    const _phase;
  ShenandoahPhaseTimings::WorkerPhase const _worker_phase;
  uint const _worker_id;

  double _start_time;
  EventGCPhaseParallel _event;
public:
  ShenandoahWorkerTimingsTracker(ShenandoahPhaseTimings::Phase phase,
                                 ShenandoahPhaseTimings::WorkerPhase worker_phase,
                                 uint worker_id,
                                 bool cumulative = false);
  ~ShenandoahWorkerTimingsTracker();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPHASETIMINGS_HPP
