/*
 * Copyright (c) 2017, 2019, Red Hat, Inc. All rights reserved.
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

#include "jfr/jfrEvents.hpp"
#include "gc/shenandoah/shenandoahNumberSeq.hpp"
#include "gc/shared/workerDataArray.hpp"
#include "memory/allocation.hpp"

class ShenandoahCollectorPolicy;
class outputStream;

#define SHENANDOAH_GC_PAR_PHASE_DO(CNT_PREFIX, DESC_PREFIX, f)                         \
  f(CNT_PREFIX ## ThreadRoots,              DESC_PREFIX "Thread Roots")                \
  f(CNT_PREFIX ## CodeCacheRoots,           DESC_PREFIX "Code Cache Roots")            \
  f(CNT_PREFIX ## UniverseRoots,            DESC_PREFIX "Universe Roots")              \
  f(CNT_PREFIX ## JNIRoots,                 DESC_PREFIX "JNI Handles Roots")           \
  f(CNT_PREFIX ## JVMTIWeakRoots,           DESC_PREFIX "JVMTI Weak Roots")            \
  f(CNT_PREFIX ## JFRWeakRoots,             DESC_PREFIX "JFR Weak Roots")              \
  f(CNT_PREFIX ## JNIWeakRoots,             DESC_PREFIX "JNI Weak Roots")              \
  f(CNT_PREFIX ## StringTableRoots,         DESC_PREFIX "String Table Roots")          \
  f(CNT_PREFIX ## ResolvedMethodTableRoots, DESC_PREFIX "Resolved Table Roots")        \
  f(CNT_PREFIX ## VMGlobalRoots,            DESC_PREFIX "VM Global Roots")             \
  f(CNT_PREFIX ## VMWeakRoots,              DESC_PREFIX "VM Weak Roots")               \
  f(CNT_PREFIX ## ObjectSynchronizerRoots,  DESC_PREFIX "Synchronizer Roots")          \
  f(CNT_PREFIX ## ManagementRoots,          DESC_PREFIX "Management Roots")            \
  f(CNT_PREFIX ## SystemDictionaryRoots,    DESC_PREFIX "System Dict Roots")           \
  f(CNT_PREFIX ## CLDGRoots,                DESC_PREFIX "CLDG Roots")                  \
  f(CNT_PREFIX ## JVMTIRoots,               DESC_PREFIX "JVMTI Roots")                 \
  f(CNT_PREFIX ## StringDedupTableRoots,    DESC_PREFIX "Dedup Table Roots")           \
  f(CNT_PREFIX ## StringDedupQueueRoots,    DESC_PREFIX "Dedup Queue Roots")           \
  f(CNT_PREFIX ## FinishQueues,             DESC_PREFIX "Finish Queues")               \
  // end

#define SHENANDOAH_GC_PHASE_DO(f)                                                      \
  f(total_pause_gross,                              "Total Pauses (G)")                \
  f(total_pause,                                    "Total Pauses (N)")                \
                                                                                       \
  f(init_mark_gross,                                "Pause Init Mark (G)")             \
  f(init_mark,                                      "Pause Init Mark (N)")             \
  f(make_parsable,                                  "  Make Parsable")                 \
  f(clear_liveness,                                 "  Clear Liveness")                \
  f(scan_roots,                                     "  Scan Roots")                    \
  SHENANDOAH_GC_PAR_PHASE_DO(scan_,                 "    S: ", f)                      \
  f(resize_tlabs,                                   "  Resize TLABs")                  \
                                                                                       \
  f(final_mark_gross,                               "Pause Final Mark (G)")            \
  f(final_mark,                                     "Pause Final Mark (N)")            \
  f(update_roots,                                   "  Update Roots")                  \
  SHENANDOAH_GC_PAR_PHASE_DO(update_,               "    U: ", f)                      \
  f(finish_queues,                                  "  Finish Queues")                 \
  f(weakrefs,                                       "  Weak References")               \
  f(weakrefs_process,                               "    Process")                     \
  f(purge,                                          "  System Purge")                  \
  f(purge_class_unload,                             "    Unload Classes")              \
  f(purge_par,                                      "    Parallel Cleanup")            \
  SHENANDOAH_GC_PAR_PHASE_DO(purge_par_roots,       "      PC: ", f)                   \
  f(purge_cldg,                                     "    CLDG")                        \
  f(complete_liveness,                              "  Complete Liveness")             \
  f(retire_tlabs,                                   "  Retire TLABs")                  \
  f(sync_pinned,                                    "  Sync Pinned")                   \
  f(trash_cset,                                     "  Trash CSet")                    \
  f(prepare_evac,                                   "  Prepare Evacuation")            \
  f(init_evac,                                      "  Initial Evacuation")            \
  SHENANDOAH_GC_PAR_PHASE_DO(evac_,                 "    E: ", f)                      \
                                                                                       \
  f(init_update_refs_gross,                         "Pause Init  Update Refs (G)")     \
  f(init_update_refs,                               "Pause Init  Update Refs (N)")     \
  f(init_update_refs_retire_gclabs,                 "  Retire GCLABs")                 \
  f(init_update_refs_prepare,                       "  Prepare")                       \
                                                                                       \
  f(final_update_refs_gross,                        "Pause Final Update Refs (G)")     \
  f(final_update_refs,                              "Pause Final Update Refs (N)")     \
  f(final_update_refs_finish_work,                  "  Finish Work")                   \
  f(final_update_refs_roots,                        "  Update Roots")                  \
  SHENANDOAH_GC_PAR_PHASE_DO(final_update_,         "    UR: ", f)                     \
  f(final_update_refs_sync_pinned,                  "  Sync Pinned")                   \
  f(final_update_refs_trash_cset,                   "  Trash CSet")                    \
                                                                                       \
  f(degen_gc_gross,                                 "Pause Degenerated GC (G)")        \
  f(degen_gc,                                       "Pause Degenerated GC (N)")        \
  f(degen_gc_update_roots,                          "  Degen Update Roots")            \
  SHENANDOAH_GC_PAR_PHASE_DO(degen_gc_update_,      "    DU: ", f)                     \
                                                                                       \
  f(init_traversal_gc_gross,                        "Pause Init Traversal (G)")        \
  f(init_traversal_gc,                              "Pause Init Traversal (N)")        \
  f(traversal_gc_prepare,                           "  Prepare")                       \
  f(traversal_gc_make_parsable,                     "    Make Parsable")               \
  f(traversal_gc_resize_tlabs,                      "    Resize TLABs")                \
  f(traversal_gc_prepare_sync_pinned,               "    Sync Pinned")                 \
  f(init_traversal_gc_work,                         "  Work")                          \
  SHENANDOAH_GC_PAR_PHASE_DO(init_traversal_,       "    TI: ", f)                     \
                                                                                       \
  f(final_traversal_gc_gross,                       "Pause Final Traversal (G)")       \
  f(final_traversal_gc,                             "Pause Final Traversal (N)")       \
  f(final_traversal_gc_work,                        "  Work")                          \
  SHENANDOAH_GC_PAR_PHASE_DO(final_trav_gc_,        "    TF: ", f)                     \
  f(final_traversal_update_roots,                   "  Update Roots")                  \
  SHENANDOAH_GC_PAR_PHASE_DO(final_trav_update_,    "    TU: ", f)                     \
  f(traversal_gc_sync_pinned,                       "  Sync Pinned")                   \
  f(traversal_gc_cleanup,                           "  Cleanup")                       \
                                                                                       \
  f(full_gc_gross,                                  "Pause Full GC (G)")               \
  f(full_gc,                                        "Pause Full GC (N)")               \
  f(full_gc_heapdumps,                              "  Heap Dumps")                    \
  f(full_gc_prepare,                                "  Prepare")                       \
  f(full_gc_roots,                                  "  Roots")                         \
  SHENANDOAH_GC_PAR_PHASE_DO(full_gc_,              "    F: ", f)                      \
  f(full_gc_mark,                                   "  Mark")                          \
  f(full_gc_mark_finish_queues,                     "    Finish Queues")               \
  f(full_gc_weakrefs,                               "    Weak References")             \
  f(full_gc_weakrefs_process,                       "      Process")                   \
  f(full_gc_purge,                                  "    System Purge")                \
  f(full_gc_purge_class_unload,                     "      Unload Classes")            \
  f(full_gc_purge_par,                              "    Parallel Cleanup")            \
  SHENANDOAH_GC_PAR_PHASE_DO(full_gc_purge_roots,   "      PC: ", f)                   \
  f(full_gc_purge_cldg,                             "    CLDG")                        \
  f(full_gc_calculate_addresses,                    "  Calculate Addresses")           \
  f(full_gc_calculate_addresses_regular,            "    Regular Objects")             \
  f(full_gc_calculate_addresses_humong,             "    Humongous Objects")           \
  f(full_gc_adjust_pointers,                        "  Adjust Pointers")               \
  f(full_gc_copy_objects,                           "  Copy Objects")                  \
  f(full_gc_copy_objects_regular,                   "    Regular Objects")             \
  f(full_gc_copy_objects_humong,                    "    Humongous Objects")           \
  f(full_gc_copy_objects_reset_complete,            "    Reset Complete Bitmap")       \
  f(full_gc_copy_objects_rebuild,                   "    Rebuild Region Sets")         \
  f(full_gc_resize_tlabs,                           "  Resize TLABs")                  \
                                                                                       \
  /* Longer concurrent phases at the end */                                            \
  f(conc_reset,                                     "Concurrent Reset")                \
  f(conc_mark,                                      "Concurrent Marking")              \
  f(conc_preclean,                                  "Concurrent Precleaning")          \
  f(conc_roots,                                     "Concurrent Roots")                \
  f(conc_evac,                                      "Concurrent Evacuation")           \
  f(conc_update_refs,                               "Concurrent Update Refs")          \
  f(conc_cleanup,                                   "Concurrent Cleanup")              \
  f(conc_traversal,                                 "Concurrent Traversal")            \
                                                                                       \
  f(conc_uncommit,                                  "Concurrent Uncommit")             \
                                                                                       \
  /* Unclassified */                                                                   \
  f(pause_other,                                    "Pause Other")                     \
  f(conc_other,                                     "Concurrent Other")                \
  // end

class ShenandoahPhaseTimings : public CHeapObj<mtGC> {
public:
#define GC_PHASE_DECLARE_ENUM(type, title)   type,

  enum Phase {
    SHENANDOAH_GC_PHASE_DO(GC_PHASE_DECLARE_ENUM)
    _num_phases,
    _invalid_phase = _num_phases
  };

  enum GCParPhases {
    SHENANDOAH_GC_PAR_PHASE_DO(,, GC_PHASE_DECLARE_ENUM)
    GCParPhasesSentinel
  };

#undef GC_PHASE_DECLARE_ENUM

private:
  HdrSeq              _timing_data[_num_phases];
  static const char*  _phase_names[_num_phases];

  WorkerDataArray<double>*   _gc_par_phases[ShenandoahPhaseTimings::GCParPhasesSentinel];
  ShenandoahCollectorPolicy* _policy;

public:
  ShenandoahPhaseTimings();

  void record_phase_time(Phase phase, double time);
  void record_worker_time(GCParPhases phase, uint worker_id, double time);

  void record_workers_start(Phase phase);
  void record_workers_end(Phase phase);

  static const char* phase_name(Phase phase) {
    assert(phase >= 0 && phase < _num_phases, "Out of bound");
    return _phase_names[phase];
  }

  void print_on(outputStream* out) const;
};

class ShenandoahWorkerTimingsTracker : public StackObj {
private:
  ShenandoahPhaseTimings::GCParPhases const _phase;
  ShenandoahPhaseTimings* const _timings;
  uint const _worker_id;

  double _start_time;
  EventGCPhaseParallel _event;
public:
  ShenandoahWorkerTimingsTracker(ShenandoahPhaseTimings::GCParPhases phase, uint worker_id);
  ~ShenandoahWorkerTimingsTracker();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPHASETIMINGS_HPP
