/*
 * Copyright (c) 2017, 2020, Red Hat, Inc. All rights reserved.
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

#define SHENANDOAH_PAR_PHASE_DO(CNT_PREFIX, DESC_PREFIX, f)                            \
  f(CNT_PREFIX ## TotalWork,                DESC_PREFIX "<total>")                     \
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
  f(CNT_PREFIX ## CLDGRoots,                DESC_PREFIX "CLDG Roots")                  \
  f(CNT_PREFIX ## JVMTIRoots,               DESC_PREFIX "JVMTI Roots")                 \
  f(CNT_PREFIX ## StringDedupTableRoots,    DESC_PREFIX "Dedup Table Roots")           \
  f(CNT_PREFIX ## StringDedupQueueRoots,    DESC_PREFIX "Dedup Queue Roots")           \
  f(CNT_PREFIX ## FinishQueues,             DESC_PREFIX "Finish Queues")               \
  // end

#define SHENANDOAH_PHASE_DO(f)                                                         \
  f(conc_reset,                                     "Concurrent Reset")                \
                                                                                       \
  f(init_mark_gross,                                "Pause Init Mark (G)")             \
  f(init_mark,                                      "Pause Init Mark (N)")             \
  f(make_parsable,                                  "  Make Parsable")                 \
  f(init_update_region_states,                      "  Update Region States")          \
  f(scan_roots,                                     "  Scan Roots")                    \
  SHENANDOAH_PAR_PHASE_DO(scan_,                    "    S: ", f)                      \
  f(resize_tlabs,                                   "  Resize TLABs")                  \
                                                                                       \
  f(conc_mark,                                      "Concurrent Marking")              \
  f(conc_mark_roots,                                "  Roots ")                        \
  SHENANDOAH_PAR_PHASE_DO(conc_mark_roots,          "    CM: ", f)                     \
                                                                                       \
  f(conc_preclean,                                  "Concurrent Precleaning")          \
                                                                                       \
  f(final_mark_gross,                               "Pause Final Mark (G)")            \
  f(final_mark,                                     "Pause Final Mark (N)")            \
  f(update_roots,                                   "  Update Roots")                  \
  SHENANDOAH_PAR_PHASE_DO(update_,                  "    U: ", f)                      \
  f(finish_queues,                                  "  Finish Queues")                 \
  f(weakrefs,                                       "  Weak References")               \
  f(weakrefs_process,                               "    Process")                     \
  f(purge,                                          "  System Purge")                  \
  f(purge_class_unload,                             "    Unload Classes")              \
  SHENANDOAH_PAR_PHASE_DO(purge_cu_par_,            "      CU: ", f)                   \
  f(purge_weak_par,                                 "    Weak Roots")                  \
  SHENANDOAH_PAR_PHASE_DO(purge_weak_par_,          "      WR: ", f)                   \
  f(purge_cldg,                                     "    CLDG")                        \
  f(final_update_region_states,                     "  Update Region States")          \
  f(retire_tlabs,                                   "  Retire TLABs")                  \
  f(choose_cset,                                    "  Choose Collection Set")         \
  f(final_rebuild_freeset,                          "  Rebuild Free Set")              \
  f(init_evac,                                      "  Initial Evacuation")            \
  SHENANDOAH_PAR_PHASE_DO(evac_,                    "    E: ", f)                      \
                                                                                       \
  f(conc_weak_roots,                                "Concurrent Weak Roots")           \
  f(conc_weak_roots_work,                           "  Roots")                         \
  SHENANDOAH_PAR_PHASE_DO(conc_weak_roots_work_,    "    CWR: ", f)                    \
  f(conc_weak_roots_rendezvous,                     "  Rendezvous")                    \
  f(conc_cleanup_early,                             "Concurrent Cleanup")              \
  f(conc_class_unload,                              "Concurrent Class Unloading")      \
  f(conc_class_unload_unlink,                       "  Unlink Stale")                  \
  f(conc_class_unload_unlink_sd,                    "    System Dictionary")           \
  f(conc_class_unload_unlink_weak_klass,            "    Weak Class Links")            \
  f(conc_class_unload_unlink_code_roots,            "    Code Roots")                  \
  f(conc_class_unload_rendezvous,                   "  Rendezvous")                    \
  f(conc_class_unload_purge,                        "  Purge Unlinked")                \
  f(conc_class_unload_purge_coderoots,              "    Code Roots")                  \
  f(conc_class_unload_purge_cldg,                   "    CLDG")                        \
  f(conc_class_unload_purge_ec,                     "    Exception Caches")            \
  f(conc_strong_roots,                              "Concurrent Strong Roots")         \
  SHENANDOAH_PAR_PHASE_DO(conc_strong_roots_,       "  CSR: ", f)                      \
  f(conc_evac,                                      "Concurrent Evacuation")           \
                                                                                       \
  f(init_update_refs_gross,                         "Pause Init  Update Refs (G)")     \
  f(init_update_refs,                               "Pause Init  Update Refs (N)")     \
  f(init_update_refs_retire_gclabs,                 "  Retire GCLABs")                 \
                                                                                       \
  f(conc_update_refs,                               "Concurrent Update Refs")          \
                                                                                       \
  f(final_update_refs_gross,                        "Pause Final Update Refs (G)")     \
  f(final_update_refs,                              "Pause Final Update Refs (N)")     \
  f(final_update_refs_finish_work,                  "  Finish Work")                   \
  f(final_update_refs_roots,                        "  Update Roots")                  \
  SHENANDOAH_PAR_PHASE_DO(final_update_,            "    UR: ", f)                     \
  f(final_update_refs_update_region_states,         "  Update Region States")          \
  f(final_update_refs_trash_cset,                   "  Trash Collection Set")          \
  f(final_update_refs_rebuild_freeset,              "  Rebuild Free Set")              \
                                                                                       \
  f(conc_cleanup_complete,                          "Concurrent Cleanup")              \
                                                                                       \
  f(degen_gc_gross,                                 "Pause Degenerated GC (G)")        \
  f(degen_gc,                                       "Pause Degenerated GC (N)")        \
  f(degen_gc_scan_conc_roots,                       "  Degen Mark Roots")              \
  SHENANDOAH_PAR_PHASE_DO(degen_gc_conc_mark_,      "    DM: ", f)                     \
  f(degen_gc_update_roots,                          "  Degen Update Roots")            \
  SHENANDOAH_PAR_PHASE_DO(degen_gc_update_,         "    DU: ", f)                     \
                                                                                       \
  f(full_gc_gross,                                  "Pause Full GC (G)")               \
  f(full_gc,                                        "Pause Full GC (N)")               \
  f(full_gc_heapdump_pre,                           "  Pre Heap Dump")                 \
  f(full_gc_prepare,                                "  Prepare")                       \
  f(full_gc_scan_roots,                             "  Scan Roots")                    \
  SHENANDOAH_PAR_PHASE_DO(full_gc_scan_roots_,      "    FS: ", f)                     \
  f(full_gc_scan_conc_roots,                        "  Scan Concurrent Roots")         \
  SHENANDOAH_PAR_PHASE_DO(full_gc_scan_conc_roots,  "    FCS: ", f)                    \
  f(full_gc_update_roots,                           "  Update Roots")                  \
  SHENANDOAH_PAR_PHASE_DO(full_gc_update_roots_,    "    FU: ", f)                     \
  f(full_gc_mark,                                   "  Mark")                          \
  f(full_gc_mark_finish_queues,                     "    Finish Queues")               \
  f(full_gc_weakrefs,                               "    Weak References")             \
  f(full_gc_weakrefs_process,                       "      Process")                   \
  f(full_gc_purge,                                  "    System Purge")                \
  f(full_gc_purge_class_unload,                     "      Unload Classes")            \
  SHENANDOAH_PAR_PHASE_DO(full_gc_purge_cu_par_,    "        CU: ", f)                 \
  f(full_gc_purge_weak_par,                         "      Weak Roots")                \
  SHENANDOAH_PAR_PHASE_DO(full_gc_purge_weak_p_,    "        WR: ", f)                 \
  f(full_gc_purge_cldg,                             "      CLDG")                      \
  f(full_gc_calculate_addresses,                    "  Calculate Addresses")           \
  f(full_gc_calculate_addresses_regular,            "    Regular Objects")             \
  f(full_gc_calculate_addresses_humong,             "    Humongous Objects")           \
  f(full_gc_adjust_pointers,                        "  Adjust Pointers")               \
  f(full_gc_adjust_roots,                           "  Adjust Roots")                  \
  SHENANDOAH_PAR_PHASE_DO(full_gc_adjust_roots_,    "    FA: ", f)                     \
  f(full_gc_copy_objects,                           "  Copy Objects")                  \
  f(full_gc_copy_objects_regular,                   "    Regular Objects")             \
  f(full_gc_copy_objects_humong,                    "    Humongous Objects")           \
  f(full_gc_copy_objects_reset_complete,            "    Reset Complete Bitmap")       \
  f(full_gc_copy_objects_rebuild,                   "    Rebuild Region Sets")         \
  f(full_gc_resize_tlabs,                           "  Resize TLABs")                  \
  f(full_gc_heapdump_post,                          "  Post Heap Dump")                \
                                                                                       \
  f(conc_uncommit,                                  "Concurrent Uncommit")             \
                                                                                       \
  f(heap_iteration_roots,                           "Heap Iteration")                  \
  SHENANDOAH_PAR_PHASE_DO(heap_iteration_roots_,    "  HI: ", f)                       \
  // end

typedef WorkerDataArray<double> ShenandoahWorkerData;

class ShenandoahPhaseTimings : public CHeapObj<mtGC> {
  friend class ShenandoahGCPhase;
  friend class ShenandoahWorkerTimingsTracker;
public:
#define SHENANDOAH_PHASE_DECLARE_ENUM(type, title)   type,

  enum Phase {
    SHENANDOAH_PHASE_DO(SHENANDOAH_PHASE_DECLARE_ENUM)
    _num_phases,
    _invalid_phase = _num_phases
  };

  enum ParPhase {
    SHENANDOAH_PAR_PHASE_DO(,, SHENANDOAH_PHASE_DECLARE_ENUM)
    _num_par_phases
  };

#undef SHENANDOAH_PHASE_DECLARE_ENUM

private:
  uint                _max_workers;
  double              _cycle_data[_num_phases];
  HdrSeq              _global_data[_num_phases];
  static const char*  _phase_names[_num_phases];

  ShenandoahWorkerData* _worker_data[_num_phases];
  ShenandoahCollectorPolicy* _policy;

  static bool is_worker_phase(Phase phase);
  static bool is_root_work_phase(Phase phase);

  ShenandoahWorkerData* worker_data(Phase phase, ParPhase par_phase);
  Phase worker_par_phase(Phase phase, ParPhase par_phase);

  void set_cycle_data(Phase phase, double time);
  static double uninitialized() { return -1; }

public:
  ShenandoahPhaseTimings(uint max_workers);

  void record_phase_time(Phase phase, double time);

  void record_workers_start(Phase phase);
  void record_workers_end(Phase phase);

  void flush_par_workers_to_cycle();
  void flush_cycle_to_global();

  static const char* phase_name(Phase phase) {
    assert(phase >= 0 && phase < _num_phases, "Out of bound");
    return _phase_names[phase];
  }

  void print_cycle_on(outputStream* out) const;
  void print_global_on(outputStream* out) const;
};

class ShenandoahWorkerTimingsTracker : public StackObj {
private:
  ShenandoahPhaseTimings*          const _timings;
  ShenandoahPhaseTimings::Phase    const _phase;
  ShenandoahPhaseTimings::ParPhase const _par_phase;
  uint const _worker_id;

  double _start_time;
  EventGCPhaseParallel _event;
public:
  ShenandoahWorkerTimingsTracker(ShenandoahPhaseTimings::Phase phase, ShenandoahPhaseTimings::ParPhase par_phase, uint worker_id);
  ~ShenandoahWorkerTimingsTracker();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPHASETIMINGS_HPP
