/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1YOUNGGCALLOCATIONFAILUREINJECTOR_INLINE_HPP
#define SHARE_GC_G1_G1YOUNGGCALLOCATIONFAILUREINJECTOR_INLINE_HPP

#include "gc/g1/g1YoungGCAllocationFailureInjector.hpp"

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/shared/gc_globals.hpp"

#if ALLOCATION_FAILURE_INJECTOR

inline bool G1YoungGCAllocationFailureInjector::allocation_should_fail(size_t& counter, uint region_idx) {
  if (!_inject_allocation_failure_for_current_gc) {
    return false;
  }
  if (!_allocation_failure_regions.at(region_idx)) {
    return false;
  }
  if (++counter < G1GCAllocationFailureALotCount) {
    return false;
  }
  counter = 0;
  return true;
}

#endif  // #if ALLOCATION_FAILURE_INJECTOR

#endif /* SHARE_GC_G1_G1YOUNGGCALLOCATIONFAILUREINJECTOR_INLINE_HPP */
