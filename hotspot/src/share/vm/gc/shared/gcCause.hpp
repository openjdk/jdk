/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_GCCAUSE_HPP
#define SHARE_VM_GC_SHARED_GCCAUSE_HPP

#include "memory/allocation.hpp"

//
// This class exposes implementation details of the various
// collector(s), and we need to be very careful with it. If
// use of this class grows, we should split it into public
// and implementation-private "causes".
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
    _gc_locker,
    _heap_inspection,
    _heap_dump,
    _wb_young_gc,
    _wb_conc_mark,
    _update_allocation_context_stats_inc,
    _update_allocation_context_stats_full,

    /* implementation independent, but reserved for GC use */
    _no_gc,
    _no_cause_specified,
    _allocation_failure,

    /* implementation specific */

    _tenured_generation_full,
    _metadata_GC_threshold,

    _cms_generation_full,
    _cms_initial_mark,
    _cms_final_remark,
    _cms_concurrent_mark,

    _old_generation_expanded_on_last_scavenge,
    _old_generation_too_full_to_scavenge,
    _adaptive_size_policy,

    _g1_inc_collection_pause,
    _g1_humongous_allocation,

    _last_ditch_collection,

    _dcmd_gc_run,

    _last_gc_cause
  };

  inline static bool is_user_requested_gc(GCCause::Cause cause) {
    return (cause == GCCause::_java_lang_system_gc ||
            cause == GCCause::_dcmd_gc_run);
  }

  inline static bool is_serviceability_requested_gc(GCCause::Cause
                                                             cause) {
    return (cause == GCCause::_jvmti_force_gc ||
            cause == GCCause::_heap_inspection ||
            cause == GCCause::_heap_dump);
  }

  // Causes for collection of the tenured gernation
  inline static bool is_tenured_allocation_failure_gc(GCCause::Cause cause) {
    assert(cause != GCCause::_old_generation_too_full_to_scavenge &&
           cause != GCCause::_old_generation_expanded_on_last_scavenge,
           "This GCCause may be correct but is not expected yet: %s",
           to_string(cause));
    // _tenured_generation_full or _cms_generation_full for full tenured generations
    // _adaptive_size_policy for a full collection after a young GC
    // _allocation_failure is the generic cause a collection which could result
    // in the collection of the tenured generation if there is not enough space
    // in the tenured generation to support a young GC.
    // _last_ditch_collection is a collection done to include SoftReferences.
    return (cause == GCCause::_tenured_generation_full ||
            cause == GCCause::_cms_generation_full ||
            cause == GCCause::_adaptive_size_policy ||
            cause == GCCause::_allocation_failure ||
            cause == GCCause::_last_ditch_collection);
  }

  // Causes for collection of the young generation
  inline static bool is_allocation_failure_gc(GCCause::Cause cause) {
    // _allocation_failure is the generic cause a collection for allocation failure
    // _adaptive_size_policy is for a collecton done before a full GC
    // _last_ditch_collection is a collection done to include SoftReferences.
    return (cause == GCCause::_allocation_failure ||
            cause == GCCause::_adaptive_size_policy ||
            cause == GCCause::_last_ditch_collection);
  }

  // Return a string describing the GCCause.
  static const char* to_string(GCCause::Cause cause);
};

// Helper class for doing logging that includes the GC Cause
// as a string.
class GCCauseString : StackObj {
 private:
   static const int _length = 128;
   char _buffer[_length];
   int _position;

 public:
   GCCauseString(const char* prefix, GCCause::Cause cause) {
     if (PrintGCCause) {
      _position = jio_snprintf(_buffer, _length, "%s (%s) ", prefix, GCCause::to_string(cause));
     } else {
      _position = jio_snprintf(_buffer, _length, "%s ", prefix);
     }
     assert(_position >= 0 && _position <= _length,
            "Need to increase the buffer size in GCCauseString? %d", _position);
   }

   GCCauseString& append(const char* str) {
     int res = jio_snprintf(_buffer + _position, _length - _position, "%s", str);
     _position += res;
     assert(res >= 0 && _position <= _length,
            "Need to increase the buffer size in GCCauseString? %d", res);
     return *this;
   }

   operator const char*() {
     return _buffer;
   }
};

#endif // SHARE_VM_GC_SHARED_GCCAUSE_HPP
