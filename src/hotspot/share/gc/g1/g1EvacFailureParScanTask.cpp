/*
 * Copyright (c) 2021, Huawei and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1EvacFailureParScanTask.hpp"
#include "gc/g1/heapRegion.hpp"

G1EvacFailureParScanTask::G1EvacFailureParScanTask(HeapRegion* region,
                                                   HeapWord* previous_obj,
                                                   uint start,
                                                   uint end,
                                                   bool last) :
  _region(region),
  _previous_object_end(previous_obj),
  _start(start),
  _end(end),
  _last(last) { }

G1EvacFailureParScanTask& G1EvacFailureParScanTask::operator=(const G1EvacFailureParScanTask& o) {
  this->_region = o._region;
  this->_previous_object_end = o._previous_object_end;
  this->_start = o._start;
  this->_end = o._end;
  this->_last = o._last;
  return *this;
}

#ifdef ASSERT
void G1EvacFailureParScanTask::verify() {
    assert(_region != nullptr, "must be");
    assert(_start < _end, "must be");
    assert(_previous_object_end != nullptr, "must be");
  }
#endif // ASSERT
