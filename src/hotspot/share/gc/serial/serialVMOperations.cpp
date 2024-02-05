/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/serial/serialVMOperations.hpp"
#include "gc/shared/gcLocker.hpp"

void VM_GenCollectForAllocation::doit() {
  SvcGCMarker sgcm(SvcGCMarker::MINOR);

  SerialHeap* gch = SerialHeap::heap();
  GCCauseSetter gccs(gch, _gc_cause);
  _result = gch->satisfy_failed_allocation(_word_size, _tlab);
  assert(_result == nullptr || gch->is_in_reserved(_result), "result not in heap");

  if (_result == nullptr && GCLocker::is_active_and_needs_gc()) {
    set_gc_locked();
  }
}

void VM_GenCollectFull::doit() {
  SvcGCMarker sgcm(SvcGCMarker::FULL);

  SerialHeap* gch = SerialHeap::heap();
  GCCauseSetter gccs(gch, _gc_cause);
  gch->do_full_collection(gch->must_clear_all_soft_refs(), _max_generation);
}
