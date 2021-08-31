/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1YOUNGGCEVACUATIONFAILUREINJECTOR_INLINE_HPP
#define SHARE_GC_G1_G1YOUNGGCEVACUATIONFAILUREINJECTOR_INLINE_HPP

#include "gc/g1/g1_globals.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1YoungGCEvacFailureInjector.hpp"

#ifndef PRODUCT

inline bool G1YoungGCEvacFailureInjector::evacuation_should_fail() {
  if (!G1EvacuationFailureALot || !_inject_evacuation_failure_for_current_gc) {
    return false;
  }
  // Injecting evacuation failures is in effect for current GC
  // Access to _evacuation_failure_alot_count is not atomic;
  // the value does not have to be exact.
  if (++_evacuation_failure_object_count < G1EvacuationFailureALotCount) {
    return false;
  }
  _evacuation_failure_object_count = 0;
  return true;
}

#endif  // #ifndef PRODUCT

#endif /* SHARE_GC_G1_G1YOUNGGCEVACUATIONFAILUREINJECTOR_INLINE_HPP */

