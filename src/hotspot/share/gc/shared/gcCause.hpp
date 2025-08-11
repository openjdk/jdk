/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_GCCAUSE_HPP
#define SHARE_GC_SHARED_GCCAUSE_HPP

#include "memory/allStatic.hpp"
#include "utilities/debug.hpp"

//
// This class exposes implementation details of the various
// collector(s), and we need to be very careful with it. If
// use of this class grows, we should split it into public
// and implementation-private "causes".
//
// The definitions in the SA code should be kept in sync
// with the definitions here.
//

class GCCause : public AllStatic {
 public:
  enum Cause {
    /* public */
    _java_lang_system_gc,
    _full_gc_alot,
    _scavenge_alot,
    _allocation_profiler,
    _jvmti_force_gc,
    _heap_inspection,
    _heap_dump,
    _wb_young_gc,
    _wb_full_gc,
    _wb_breakpoint,

    /* implementation independent, but reserved for GC use */
    _no_gc,
    _allocation_failure,

    /* implementation specific */

    _codecache_GC_threshold,
    _codecache_GC_aggressive,
    _metadata_GC_threshold,
    _metadata_GC_clear_soft_refs,

    _g1_inc_collection_pause,
    _g1_compaction_pause,
    _g1_humongous_allocation,
    _g1_periodic_collection,

    _dcmd_gc_run,

    _shenandoah_stop_vm,
    _shenandoah_allocation_failure_evac,
    _shenandoah_humongous_allocation_failure,
    _shenandoah_concurrent_gc,
    _shenandoah_upgrade_to_full_gc,

    _z_timer,
    _z_warmup,
    _z_allocation_rate,
    _z_allocation_stall,
    _z_proactive,
    _z_high_usage,

    _last_gc_cause
  };

  inline static bool is_user_requested_gc(GCCause::Cause cause) {
    return (cause == GCCause::_java_lang_system_gc ||
            cause == GCCause::_dcmd_gc_run);
  }

  inline static bool is_serviceability_requested_gc(GCCause::Cause cause) {
    return (cause == GCCause::_jvmti_force_gc ||
            cause == GCCause::_heap_inspection ||
            cause == GCCause::_heap_dump);
  }

  inline static bool is_codecache_requested_gc(GCCause::Cause cause) {
      return (cause == _codecache_GC_threshold  ||
              cause == _codecache_GC_aggressive);
  }

  // Return a string describing the GCCause.
  static const char* to_string(GCCause::Cause cause);
};

#endif // SHARE_GC_SHARED_GCCAUSE_HPP
