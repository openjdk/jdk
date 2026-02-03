/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gcCause.hpp"

const char* GCCause::to_string(GCCause::Cause cause) {
  switch (cause) {
    case _java_lang_system_gc:
      return "System.gc()";

    case _full_gc_alot:
      return "FullGCAlot";

    case _scavenge_alot:
      return "ScavengeAlot";

    case _allocation_profiler:
      return "Allocation Profiler";

    case _jvmti_force_gc:
      return "JvmtiEnv ForceGarbageCollection";

    case _heap_inspection:
      return "Heap Inspection Initiated GC";

    case _heap_dump:
      return "Heap Dump Initiated GC";

    case _wb_young_gc:
      return "WhiteBox Initiated Young GC";

    case _wb_full_gc:
      return "WhiteBox Initiated Full GC";

    case _wb_breakpoint:
      return "WhiteBox Initiated Run to Breakpoint";

    case _no_gc:
      return "No GC";

    case _allocation_failure:
      return "Allocation Failure";

    case _codecache_GC_threshold:
      return "CodeCache GC Threshold";

    case _codecache_GC_aggressive:
      return "CodeCache GC Aggressive";

    case _metadata_GC_threshold:
      return "Metadata GC Threshold";

    case _metadata_GC_clear_soft_refs:
      return "Metadata GC Clear Soft References";

    case _g1_inc_collection_pause:
      return "G1 Evacuation Pause";

    case _g1_compaction_pause:
      return "G1 Compaction Pause";

    case _g1_humongous_allocation:
      return "G1 Humongous Allocation";

    case _g1_periodic_collection:
      return "G1 Periodic Collection";

    case _dcmd_gc_run:
      return "Diagnostic Command";

    case _shenandoah_stop_vm:
      return "Stopping VM";

    case _shenandoah_allocation_failure_evac:
      return "Allocation Failure During Evacuation";

    case _shenandoah_humongous_allocation_failure:
      return "Humongous Allocation Failure";

    case _shenandoah_concurrent_gc:
      return "Concurrent GC";

    case _shenandoah_upgrade_to_full_gc:
      return "Upgrade To Full GC";

    case _z_timer:
      return "Timer";

    case _z_warmup:
      return "Warmup";

    case _z_allocation_rate:
      return "Allocation Rate";

    case _z_allocation_stall:
      return "Allocation Stall";

    case _z_proactive:
      return "Proactive";

    case _z_high_usage:
      return "High Usage";

    case _last_gc_cause:
      return "ILLEGAL VALUE - last gc cause - ILLEGAL VALUE";

    default:
      return "unknown GCCause";
  }
  ShouldNotReachHere();
}
