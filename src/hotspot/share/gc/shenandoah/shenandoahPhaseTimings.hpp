/*
 * Copyright (c) 2017, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahNumberSeq.hpp"
#include "gc/shared/workerDataArray.hpp"
#include "memory/allocation.hpp"

class ShenandoahCollectorPolicy;
class ShenandoahWorkerTimings;
class ShenandoahTerminationTimings;
class outputStream;

#define SHENANDOAH_GC_PHASE_DO(f)                                                       \
  f(total_pause_gross,                              "Total Pauses (G)")                 \
  f(total_pause,                                    "Total Pauses (N)")                 \
  f(init_mark_gross,                                "Pause Init Mark (G)")              \
  f(init_mark,                                      "Pause Init Mark (N)")              \
  f(make_parsable,                                  "  Make Parsable")                  \
  f(clear_liveness,                                 "  Clear Liveness")                 \
                                                                                        \
  /* Per-thread timer block, should have "roots" counters in consistent order */        \
  f(scan_roots,                                     "  Scan Roots")                     \
  f(scan_thread_roots,                              "    S: Thread Roots")              \
  f(scan_code_roots,                                "    S: Code Cache Roots")          \
  f(scan_universe_roots,                            "    S: Universe Roots")            \
  f(scan_jni_roots,                                 "    S: JNI Roots")                 \
  f(scan_jvmti_weak_roots,                          "    S: JVMTI Weak Roots")          \
  f(scan_jfr_weak_roots,                            "    S: JFR Weak Roots")            \
  f(scan_jni_weak_roots,                            "    S: JNI Weak Roots")            \
  f(scan_stringtable_roots,                         "    S: String Table Roots")        \
  f(scan_resolved_method_table_roots,               "    S: Resolved Table Roots")      \
  f(scan_vm_weak_roots,                             "    S: VM Weak Roots")             \
  f(scan_synchronizer_roots,                        "    S: Synchronizer Roots")        \
  f(scan_management_roots,                          "    S: Management Roots")          \
  f(scan_system_dictionary_roots,                   "    S: System Dict Roots")         \
  f(scan_cldg_roots,                                "    S: CLDG Roots")                \
  f(scan_jvmti_roots,                               "    S: JVMTI Roots")               \
  f(scan_string_dedup_table_roots,                  "    S: Dedup Table Roots")         \
  f(scan_string_dedup_queue_roots,                  "    S: Dedup Queue Roots")         \
  f(scan_finish_queues,                             "    S: Finish Queues" )            \
                                                                                        \
  f(resize_tlabs,                                   "  Resize TLABs")                   \
                                                                                        \
  f(final_mark_gross,                               "Pause Final Mark (G)")             \
  f(final_mark,                                     "Pause Final Mark (N)")             \
                                                                                        \
  /* Per-thread timer block, should have "roots" counters in consistent order */        \
  f(update_roots,                                   "  Update Roots")                   \
  f(update_thread_roots,                            "    U: Thread Roots")              \
  f(update_code_roots,                              "    U: Code Cache Roots")          \
  f(update_universe_roots,                          "    U: Universe Roots")            \
  f(update_jni_roots,                               "    U: JNI Roots")                 \
  f(update_jvmti_weak_roots,                        "    U: JVMTI Weak Roots")          \
  f(update_jfr_weak_roots,                          "    U: JFR Weak Roots")            \
  f(update_jni_weak_roots,                          "    U: JNI Weak Roots")            \
  f(update_stringtable_roots,                       "    U: String Table Roots")        \
  f(update_resolved_method_table_roots,             "    U: Resolved Table Roots")      \
  f(update_vm_weak_roots,                           "    U: VM Weak Roots")             \
  f(update_synchronizer_roots,                      "    U: Synchronizer Roots")        \
  f(update_management_roots,                        "    U: Management Roots")          \
  f(update_system_dictionary_roots,                 "    U: System Dict Roots")         \
  f(update_cldg_roots,                              "    U: CLDG Roots")                \
  f(update_jvmti_roots,                             "    U: JVMTI Roots")               \
  f(update_string_dedup_table_roots,                "    U: Dedup Table Roots")         \
  f(update_string_dedup_queue_roots,                "    U: Dedup Queue Roots")         \
  f(update_finish_queues,                           "    U: Finish Queues")             \
                                                                                        \
  f(finish_queues,                                  "  Finish Queues")                  \
  f(termination,                                    "    Termination")                  \
  f(weakrefs,                                       "  Weak References")                \
  f(weakrefs_process,                               "    Process")                      \
  f(weakrefs_termination,                           "      Termination")                \
  f(purge,                                          "  System Purge")                   \
  f(purge_class_unload,                             "    Unload Classes")               \
  f(purge_par,                                      "    Parallel Cleanup")             \
  f(purge_cldg,                                     "    CLDG")                         \
  f(complete_liveness,                              "  Complete Liveness")              \
  f(prepare_evac,                                   "  Prepare Evacuation")             \
  f(recycle_regions,                                "  Recycle regions")                \
                                                                                        \
  /* Per-thread timer block, should have "roots" counters in consistent order */        \
  f(init_evac,                                      "  Initial Evacuation")             \
  f(evac_thread_roots,                              "    E: Thread Roots")              \
  f(evac_code_roots,                                "    E: Code Cache Roots")          \
  f(evac_universe_roots,                            "    E: Universe Roots")            \
  f(evac_jni_roots,                                 "    E: JNI Roots")                 \
  f(evac_jvmti_weak_roots,                          "    E: JVMTI Weak Roots")          \
  f(evac_jfr_weak_roots,                            "    E: JFR Weak Roots")            \
  f(evac_jni_weak_roots,                            "    E: JNI Weak Roots")            \
  f(evac_stringtable_roots,                         "    E: String Table Roots")        \
  f(evac_resolved_method_table_roots,               "    E: Resolved Table Roots")      \
  f(evac_vm_weak_roots,                             "    E: VM Weak Roots")             \
  f(evac_synchronizer_roots,                        "    E: Synchronizer Roots")        \
  f(evac_management_roots,                          "    E: Management Roots")          \
  f(evac_system_dictionary_roots,                   "    E: System Dict Roots")         \
  f(evac_cldg_roots,                                "    E: CLDG Roots")                \
  f(evac_jvmti_roots,                               "    E: JVMTI Roots")               \
  f(evac_string_dedup_table_roots,                  "    E: String Dedup Table Roots")  \
  f(evac_string_dedup_queue_roots,                  "    E: String Dedup Queue Roots")  \
  f(evac_finish_queues,                             "    E: Finish Queues")             \
                                                                                        \
  f(final_evac_gross,                               "Pause Final Evac (G)")             \
  f(final_evac,                                     "Pause Final Evac (N)")             \
                                                                                        \
  f(init_update_refs_gross,                         "Pause Init  Update Refs (G)")      \
  f(init_update_refs,                               "Pause Init  Update Refs (N)")      \
                                                                                        \
  f(final_update_refs_gross,                         "Pause Final Update Refs (G)")     \
  f(final_update_refs,                               "Pause Final Update Refs (N)")     \
  f(final_update_refs_finish_work,                   "  Finish Work")                   \
                                                                                        \
  /* Per-thread timer block, should have "roots" counters in consistent order */        \
  f(final_update_refs_roots,                         "  Update Roots")                  \
  f(final_update_refs_thread_roots,                  "    UR: Thread Roots")            \
  f(final_update_refs_code_roots,                    "    UR: Code Cache Roots")        \
  f(final_update_refs_universe_roots,                "    UR: Universe Roots")          \
  f(final_update_refs_jni_roots,                     "    UR: JNI Roots")               \
  f(final_update_jvmti_weak_roots,                   "    UR: JVMTI Weak Roots")        \
  f(final_update_jfr_weak_roots,                     "    UR: JFR Weak Roots")          \
  f(final_update_jni_weak_roots,                     "    UR: JNI Weak Roots")          \
  f(final_update_stringtable_roots,                  "    UR: String Table Roots")      \
  f(final_update_resolved_method_table_roots,        "    UR: Resolved Table Roots")    \
  f(final_update_vm_weak_roots,                      "    UR: VM Weak Roots")           \
  f(final_update_refs_synchronizer_roots,            "    UR: Synchronizer Roots")      \
  f(final_update_refs_management_roots,              "    UR: Management Roots")        \
  f(final_update_refs_system_dict_roots,             "    UR: System Dict Roots")       \
  f(final_update_refs_cldg_roots,                    "    UR: CLDG Roots")              \
  f(final_update_refs_jvmti_roots,                   "    UR: JVMTI Roots")             \
  f(final_update_refs_string_dedup_table_roots,      "    UR: Dedup Table Roots")       \
  f(final_update_refs_string_dedup_queue_roots,      "    UR: Dedup Queue Roots")       \
  f(final_update_refs_finish_queues,                 "    UR: Finish Queues")           \
                                                                                        \
  f(final_update_refs_recycle,                       "  Recycle")                       \
                                                                                        \
  f(degen_gc_gross,                                  "Pause Degenerated GC (G)")        \
  f(degen_gc,                                        "Pause Degenerated GC (N)")        \
                                                                                        \
  /* Per-thread timer block, should have "roots" counters in consistent order */        \
  f(degen_gc_update_roots,                           "  Degen Update Roots")            \
  f(degen_gc_update_thread_roots,                    "    DU: Thread Roots")            \
  f(degen_gc_update_code_roots,                      "    DU: Code Cache Roots")        \
  f(degen_gc_update_universe_roots,                  "    DU: Universe Roots")          \
  f(degen_gc_update_jni_roots,                       "    DU: JNI Roots")               \
  f(degen_gc_update_jvmti_weak_roots,                "    DU: JVMTI Weak Roots")        \
  f(degen_gc_update_jfr_weak_roots,                  "    DU: JFR Weak Roots")          \
  f(degen_gc_update_jni_weak_roots,                  "    DU: JNI Weak Roots")          \
  f(degen_gc_update_stringtable_roots,               "    DU: String Table Roots")      \
  f(degen_gc_update_resolved_method_table_roots,     "    DU: Resolved Table Roots")    \
  f(degen_gc_update_vm_weak_roots,                   "    DU: VM Weak Roots")           \
  f(degen_gc_update_synchronizer_roots,              "    DU: Synchronizer Roots")      \
  f(degen_gc_update_management_roots,                "    DU: Management Roots")        \
  f(degen_gc_update_system_dict_roots,               "    DU: System Dict Roots")       \
  f(degen_gc_update_cldg_roots,                      "    DU: CLDG Roots")              \
  f(degen_gc_update_jvmti_roots,                     "    DU: JVMTI Roots")             \
  f(degen_gc_update_string_dedup_table_roots,        "    DU: Dedup Table Roots")       \
  f(degen_gc_update_string_dedup_queue_roots,        "    DU: Dedup Queue Roots")       \
  f(degen_gc_update_finish_queues,                   "    DU: Finish Queues")           \
                                                                                        \
  f(init_traversal_gc_gross,                         "Pause Init Traversal (G)")        \
  f(init_traversal_gc,                               "Pause Init Traversal (N)")        \
  f(traversal_gc_prepare,                            "  Prepare")                       \
  f(traversal_gc_make_parsable,                      "    Make Parsable")               \
  f(traversal_gc_resize_tlabs,                       "    Resize TLABs")                \
                                                                                        \
  /* Per-thread timer block, should have "roots" counters in consistent order */        \
  f(init_traversal_gc_work,                          "  Work")                          \
  f(init_traversal_gc_thread_roots,                  "    TI: Thread Roots")            \
  f(init_traversal_gc_code_roots,                    "    TI: Code Cache Roots")        \
  f(init_traversal_gc_universe_roots,                "    TI: Universe Roots")          \
  f(init_traversal_gc_jni_roots,                     "    TI: JNI Roots")               \
  f(init_traversal_gc_jvmti_weak_roots,              "    TI: JVMTI Weak Roots")        \
  f(init_traversal_gc_jfr_weak_roots,                "    TI: JFR Weak Roots")          \
  f(init_traversal_gc_jni_weak_roots,                "    TI: JNI Weak Roots")          \
  f(init_traversal_gc_stringtable_roots,             "    TI: String Table Roots")      \
  f(init_traversal_gc_resolved_method_table_roots,   "    TI: Resolved Table Roots")    \
  f(init_traversal_gc_vm_weak_roots,                 "    TI: VM Weak Roots")           \
  f(init_traversal_gc_synchronizer_roots,            "    TI: Synchronizer Roots")      \
  f(init_traversal_gc_management_roots,              "    TI: Management Roots")        \
  f(init_traversal_gc_system_dict_roots,             "    TI: System Dict Roots")       \
  f(init_traversal_gc_cldg_roots,                    "    TI: CLDG Roots")              \
  f(init_traversal_gc_jvmti_roots,                   "    TI: JVMTI Roots")             \
  f(init_traversal_gc_string_dedup_table_roots,      "    TI: Dedup Table Roots")       \
  f(init_traversal_gc_string_dedup_queue_roots,      "    TI: Dedup Queue Roots")       \
  f(init_traversal_gc_finish_queues,                 "    TI: Finish Queues")           \
                                                                                        \
  f(final_traversal_gc_gross,                        "Pause Final Traversal (G)")       \
  f(final_traversal_gc,                              "Pause Final Traversal (N)")       \
                                                                                        \
  /* Per-thread timer block, should have "roots" counters in consistent order */        \
  f(final_traversal_gc_work,                         "  Work")                          \
  f(final_traversal_gc_thread_roots,                 "    TF: Thread Roots")            \
  f(final_traversal_gc_code_roots,                   "    TF: Code Cache Roots")        \
  f(final_traversal_gc_universe_roots,               "    TF: Universe Roots")          \
  f(final_traversal_gc_jni_roots,                    "    TF: JNI Roots")               \
  f(final_traversal_gc_jvmti_weak_roots,             "    TF: JVMTI Weak Roots")        \
  f(final_traversal_gc_jfr_weak_roots,               "    TF: JFR Weak Roots")          \
  f(final_traversal_gc_jni_weak_roots,               "    TF: JNI Weak Roots")          \
  f(final_traversal_gc_stringtable_roots,            "    TF: String Table Roots")      \
  f(final_traversal_gc_resolved_method_table_roots,  "    TF: Resolved Table Roots")    \
  f(final_traversal_gc_vm_weak_roots,                "    TF: VM Weak Roots")           \
  f(final_traversal_gc_synchronizer_roots,           "    TF: Synchronizer Roots")      \
  f(final_traversal_gc_management_roots,             "    TF: Management Roots")        \
  f(final_traversal_gc_system_dict_roots,            "    TF: System Dict Roots")       \
  f(final_traversal_gc_cldg_roots,                   "    TF: CLDG Roots")              \
  f(final_traversal_gc_jvmti_roots,                  "    TF: JVMTI Roots")             \
  f(final_traversal_gc_string_dedup_table_roots,     "    TF: Dedup Table Roots")       \
  f(final_traversal_gc_string_dedup_queue_roots,     "    TF: Dedup Queue Roots")       \
  f(final_traversal_gc_finish_queues,                "    TF: Finish Queues")           \
  f(final_traversal_gc_termination,                  "    TF:   Termination")           \
                                                                                        \
  /* Per-thread timer block, should have "roots" counters in consistent order */        \
  f(final_traversal_update_roots,                       "  Update Roots")               \
  f(final_traversal_update_thread_roots,                "    TU: Thread Roots")         \
  f(final_traversal_update_code_roots,                  "    TU: Code Cache Roots")     \
  f(final_traversal_update_universe_roots,              "    TU: Universe Roots")       \
  f(final_traversal_update_jni_roots,                   "    TU: JNI Roots")            \
  f(final_traversal_update_jvmti_weak_roots,            "    TU: JVMTI Weak Roots")     \
  f(final_traversal_update_jfr_weak_roots,              "    TU: JFR Weak Roots")       \
  f(final_traversal_update_jni_weak_roots,              "    TU: JNI Weak Roots")       \
  f(final_traversal_update_stringtable_roots,           "    TU: String Table Roots")   \
  f(final_traversal_update_resolved_method_table_roots, "    TU: Resolved Table Roots") \
  f(final_traversal_update_vm_weak_roots,               "    TU: VM Weak Roots")        \
  f(final_traversal_update_synchronizer_roots,          "    TU: Synchronizer Roots")   \
  f(final_traversal_update_management_roots,            "    TU: Management Roots")     \
  f(final_traversal_update_system_dict_roots,           "    TU: System Dict Roots")    \
  f(final_traversal_update_cldg_roots,                  "    TU: CLDG Roots")           \
  f(final_traversal_update_jvmti_roots,                 "    TU: JVMTI Roots")          \
  f(final_traversal_update_string_dedup_table_roots,    "    TU: Dedup Table Roots")    \
  f(final_traversal_update_string_dedup_queue_roots,    "    TU: Dedup Queue Roots")    \
  f(final_traversal_update_finish_queues,               "    TU: Finish Queues")        \
                                                                                        \
  f(traversal_gc_cleanup,                            "  Cleanup")                       \
                                                                                        \
  f(full_gc_gross,                                   "Pause Full GC (G)")               \
  f(full_gc,                                         "Pause Full GC (N)")               \
  f(full_gc_heapdumps,                               "  Heap Dumps")                    \
  f(full_gc_prepare,                                 "  Prepare")                       \
                                                                                        \
  /* Per-thread timer block, should have "roots" counters in consistent order */        \
  f(full_gc_roots,                                   "  Roots")                         \
  f(full_gc_thread_roots,                            "    F: Thread Roots")             \
  f(full_gc_code_roots,                              "    F: Code Cache Roots")         \
  f(full_gc_universe_roots,                          "    F: Universe Roots")           \
  f(full_gc_jni_roots,                               "    F: JNI Roots")                \
  f(full_gc_jvmti_weak_roots,                        "    F: JVMTI Weak Roots")         \
  f(full_gc_jfr_weak_roots,                          "    F: JFR Weak Roots")           \
  f(full_gc_jni_weak_roots,                          "    F: JNI Weak Roots")           \
  f(full_gc_stringtable_roots,                       "    F: String Table Roots")       \
  f(full_gc_resolved_method_table_roots,             "    F: Resolved Table Roots")     \
  f(full_gc_vm_weak_roots,                           "    F: VM Weak Roots")            \
  f(full_gc_synchronizer_roots,                      "    F: Synchronizer Roots")       \
  f(full_gc_management_roots,                        "    F: Management Roots")         \
  f(full_gc_system_dictionary_roots,                 "    F: System Dict Roots")        \
  f(full_gc_cldg_roots,                              "    F: CLDG Roots")               \
  f(full_gc_jvmti_roots,                             "    F: JVMTI Roots")              \
  f(full_gc_string_dedup_table_roots,                "    F: Dedup Table Roots")        \
  f(full_gc_string_dedup_queue_roots,                "    F: Dedup Queue Roots")        \
  f(full_gc_finish_queues,                           "    F: Finish Queues")            \
                                                                                        \
  f(full_gc_mark,                                    "  Mark")                          \
  f(full_gc_mark_finish_queues,                      "    Finish Queues")               \
  f(full_gc_mark_termination,                        "      Termination")               \
  f(full_gc_weakrefs,                                "    Weak References")             \
  f(full_gc_weakrefs_process,                        "      Process")                   \
  f(full_gc_weakrefs_termination,                    "        Termination")             \
  f(full_gc_purge,                                   "    System Purge")                \
  f(full_gc_purge_class_unload,                      "      Unload Classes")            \
  f(full_gc_purge_par,                               "    Parallel Cleanup")            \
  f(full_gc_purge_cldg,                              "    CLDG")                        \
  f(full_gc_calculate_addresses,                     "  Calculate Addresses")           \
  f(full_gc_calculate_addresses_regular,             "    Regular Objects")             \
  f(full_gc_calculate_addresses_humong,              "    Humongous Objects")           \
  f(full_gc_adjust_pointers,                         "  Adjust Pointers")               \
  f(full_gc_copy_objects,                            "  Copy Objects")                  \
  f(full_gc_copy_objects_regular,                    "    Regular Objects")             \
  f(full_gc_copy_objects_humong,                     "    Humongous Objects")           \
  f(full_gc_copy_objects_reset_complete,             "    Reset Complete Bitmap")       \
  f(full_gc_copy_objects_rebuild,                    "    Rebuild Region Sets")         \
  f(full_gc_resize_tlabs,                            "  Resize TLABs")                  \
                                                                                        \
  /* Longer concurrent phases at the end */                                             \
  f(conc_reset,                                      "Concurrent Reset")                \
  f(conc_mark,                                       "Concurrent Marking")              \
  f(conc_termination,                                "  Termination")                   \
  f(conc_preclean,                                   "Concurrent Precleaning")          \
  f(conc_evac,                                       "Concurrent Evacuation")           \
  f(conc_update_refs,                                "Concurrent Update Refs")          \
  f(conc_cleanup,                                    "Concurrent Cleanup")              \
  f(conc_traversal,                                  "Concurrent Traversal")            \
  f(conc_traversal_termination,                      "  Termination")                   \
                                                                                        \
  f(conc_uncommit,                                   "Concurrent Uncommit")             \
                                                                                        \
  /* Unclassified */                                                                    \
  f(pause_other,                                     "Pause Other")                     \
  f(conc_other,                                      "Concurrent Other")                \
  // end

#define SHENANDOAH_GC_PAR_PHASE_DO(f)                           \
  f(ThreadRoots,              "Thread Roots (ms):")              \
  f(CodeCacheRoots,           "CodeCache Roots (ms):")           \
  f(UniverseRoots,            "Universe Roots (ms):")            \
  f(JNIRoots,                 "JNI Handles Roots (ms):")         \
  f(JVMTIWeakRoots,           "JVMTI Weak Roots (ms):")          \
  f(JFRWeakRoots,             "JFR Weak Roots (ms):")            \
  f(JNIWeakRoots,             "JNI Weak Roots (ms):")            \
  f(StringTableRoots,         "StringTable Roots(ms):")          \
  f(ResolvedMethodTableRoots, "Resolved Table Roots(ms):")       \
  f(VMWeakRoots,              "VM Weak Roots(ms)")               \
  f(ObjectSynchronizerRoots,  "ObjectSynchronizer Roots (ms):")  \
  f(ManagementRoots,          "Management Roots (ms):")          \
  f(SystemDictionaryRoots,    "SystemDictionary Roots (ms):")    \
  f(CLDGRoots,                "CLDG Roots (ms):")                \
  f(JVMTIRoots,               "JVMTI Roots (ms):")               \
  f(StringDedupTableRoots,    "String Dedup Table Roots (ms):")  \
  f(StringDedupQueueRoots,    "String Dedup Queue Roots (ms):")  \
  f(FinishQueues,             "Finish Queues (ms):")             \
  // end

class ShenandoahPhaseTimings : public CHeapObj<mtGC> {
public:
#define GC_PHASE_DECLARE_ENUM(type, title)   type,

  enum Phase {
    SHENANDOAH_GC_PHASE_DO(GC_PHASE_DECLARE_ENUM)
    _num_phases
  };

  // These are the subphases of GC phases (scan_roots, update_roots,
  // init_evac, final_update_refs_roots and full_gc_roots).
  // Make sure they are following this order.
  enum GCParPhases {
    SHENANDOAH_GC_PAR_PHASE_DO(GC_PHASE_DECLARE_ENUM)
    GCParPhasesSentinel
  };

#undef GC_PHASE_DECLARE_ENUM

private:
  struct TimingData {
    HdrSeq _secs;
    double _start;
  };

private:
  TimingData          _timing_data[_num_phases];
  static const char*  _phase_names[_num_phases];

  ShenandoahWorkerTimings*      _worker_times;
  ShenandoahTerminationTimings* _termination_times;

  ShenandoahCollectorPolicy* _policy;

public:
  ShenandoahPhaseTimings();

  ShenandoahWorkerTimings* const worker_times() const { return _worker_times; }
  ShenandoahTerminationTimings* const termination_times() const { return _termination_times; }

  // record phase start
  void record_phase_start(Phase phase);
  // record phase end and return elapsed time in seconds for the phase
  void record_phase_end(Phase phase);
  // record an elapsed time for the phase
  void record_phase_time(Phase phase, double time);

  void record_workers_start(Phase phase);
  void record_workers_end(Phase phase);

  static const char* phase_name(Phase phase) {
    assert(phase >= 0 && phase < _num_phases, "Out of bound");
    return _phase_names[phase];
  }

  void print_on(outputStream* out) const;

private:
  void init_phase_names();
  void print_summary_sd(outputStream* out, const char* str, const HdrSeq* seq) const;
};

class ShenandoahWorkerTimings : public CHeapObj<mtGC> {
private:
  uint _max_gc_threads;
  WorkerDataArray<double>* _gc_par_phases[ShenandoahPhaseTimings::GCParPhasesSentinel];

public:
  ShenandoahWorkerTimings(uint max_gc_threads);

  // record the time a phase took in seconds
  void record_time_secs(ShenandoahPhaseTimings::GCParPhases phase, uint worker_i, double secs);

  double average(uint i) const;
  void reset(uint i);
  void print() const;
};

class ShenandoahTerminationTimings : public CHeapObj<mtGC> {
private:
  WorkerDataArray<double>* _gc_termination_phase;
public:
  ShenandoahTerminationTimings(uint max_gc_threads);

  // record the time a phase took in seconds
  void record_time_secs(uint worker_i, double secs);

  double average() const;
  void reset();

  void print() const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPHASETIMINGS_HPP
