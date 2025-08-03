/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.gc.shared;

//These definitions should be kept in sync with the definitions in the HotSpot code.

public enum GCCause {
  _java_lang_system_gc ("System.gc()"),
  _full_gc_alot ("FullGCAlot"),
  _scavenge_alot ("ScavengeAlot"),
  _allocation_profiler ("Allocation Profiler"),
  _jvmti_force_gc ("JvmtiEnv ForceGarbageCollection"),
  _heap_inspection ("Heap Inspection Initiated GC"),
  _heap_dump ("Heap Dump Initiated GC"),
  _wb_young_gc ("WhiteBox Initiated Young GC"),
  _wb_full_gc ("WhiteBox Initiated Full GC"),
  _wb_breakpoint ("WhiteBox Initiated Run to Breakpoint"),

  _no_gc ("No GC"),
  _allocation_failure ("Allocation Failure"),

  _codecache_GC_threshold ("CodeCache GC Threshold"),
  _codecache_GC_aggressive ("CodeCache GC Aggressive"),
  _metadata_GC_threshold ("Metadata GC Threshold"),
  _metadata_GC_clear_soft_refs ("Metadata GC Clear Soft References"),

  _g1_inc_collection_pause ("G1 Evacuation Pause"),
  _g1_compaction_pause ("G1 Compaction Pause"),
  _g1_humongous_allocation ("G1 Humongous Allocation"),
  _g1_periodic_collection ("G1 Periodic Collection"),

  _dcmd_gc_run ("Diagnostic Command"),

  _shenandoah_stop_vm ("Stopping VM"),
  _shenandoah_allocation_failure_evac ("Allocation Failure During Evacuation"),
  _shenandoah_humongous_allocation_failure("Humongous Allocation Failure"),
  _shenandoah_concurrent_gc ("Concurrent GC"),
  _shenandoah_upgrade_to_full_gc ("Upgrade To Full GC"),

  _z_timer ("Timer"),
  _z_warmup ("Warmup"),
  _z_allocation_rate ("Allocation Rate"),
  _z_allocation_stall ("Allocation Stall"),
  _z_proactive ("Proactive"),
  _z_high_usage ("High Usage"),

  _last_gc_cause ("ILLEGAL VALUE - last gc cause - ILLEGAL VALUE");

  private final String value;

  GCCause(String val) {
    this.value = val;
  }
  public String value() {
    return value;
  }
}
