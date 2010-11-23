/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_INTERFACE_GCCAUSE_HPP
#define SHARE_VM_GC_INTERFACE_GCCAUSE_HPP

#include "memory/allocation.hpp"

//
// This class exposes implementation details of the various
// collector(s), and we need to be very careful with it. If
// use of this class grows, we should split it into public
// and implemenation-private "causes".
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

    /* implementation independent, but reserved for GC use */
    _no_gc,
    _no_cause_specified,
    _allocation_failure,

    /* implementation specific */

    _tenured_generation_full,
    _permanent_generation_full,

    _cms_generation_full,
    _cms_initial_mark,
    _cms_final_remark,

    _old_generation_expanded_on_last_scavenge,
    _old_generation_too_full_to_scavenge,
    _adaptive_size_policy,

    _g1_inc_collection_pause,

    _last_ditch_collection,
    _last_gc_cause
  };

  inline static bool is_user_requested_gc(GCCause::Cause cause) {
    return (cause == GCCause::_java_lang_system_gc ||
            cause == GCCause::_jvmti_force_gc);
  }

  inline static bool is_serviceability_requested_gc(GCCause::Cause
                                                             cause) {
    return (cause == GCCause::_jvmti_force_gc ||
            cause == GCCause::_heap_inspection ||
            cause == GCCause::_heap_dump);
  }

  // Return a string describing the GCCause.
  static const char* to_string(GCCause::Cause cause);
  // Return true if the GCCause is for a full collection.
  static bool is_for_full_collection(GCCause::Cause cause) PRODUCT_RETURN0;
};

#endif // SHARE_VM_GC_INTERFACE_GCCAUSE_HPP
