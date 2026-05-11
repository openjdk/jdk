/*
 * Copyright (c) 2000, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/javaClasses.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "memory/universe.hpp"
#include "runtime/globals.hpp"
#include "utilities/integerCast.hpp"

void AbstractLRUReferencePolicy::set_max_interval(jlong max_interval) {
  assert(max_interval >= 0, "Sanity check");
  _max_interval = max_interval;
}

// The oop passed in is the SoftReference object, and not
// the object the SoftReference points to.
bool AbstractLRUReferencePolicy::should_clear_reference(oop p, jlong timestamp_clock) {
  assert(_max_interval >= 0, "Forgot to call setup");
  jlong interval = timestamp_clock - java_lang_ref_SoftReference::timestamp(p);
  assert(interval >= 0, "Sanity check");

  // The interval will be zero if the ref was accessed since the last scavenge/gc.
  if(interval <= _max_interval) {
    return false;
  }

  return true;
}

// Capture state (of-the-VM) information needed to evaluate the policy
void LRUCurrentHeapPolicy::setup() {
  set_max_interval(integer_cast<jlong>(Universe::heap()->free_at_last_gc() / M) * SoftRefLRUPolicyMSPerMB);
}

// Capture state (of-the-VM) information needed to evaluate the policy
void LRUMaxHeapPolicy::setup() {
  size_t max_heap = MaxHeapSize;
  max_heap -= Universe::heap()->used_at_last_gc();
  max_heap /= M;

  set_max_interval(integer_cast<jlong>(max_heap) * SoftRefLRUPolicyMSPerMB);
}
