/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1ParScanThreadState.hpp"
#include "gc/g1/g1_specialized_oop_closures.hpp"
#include "memory/iterator.inline.hpp"
#include "utilities/stack.inline.hpp"

G1ParCopyHelper::G1ParCopyHelper(G1CollectedHeap* g1,  G1ParScanThreadState* par_scan_state) :
  G1ParClosureSuper(g1, par_scan_state), _scanned_klass(NULL),
  _cm(_g1->concurrent_mark()) { }

G1ParCopyHelper::G1ParCopyHelper(G1CollectedHeap* g1) :
  G1ParClosureSuper(g1), _scanned_klass(NULL),
  _cm(_g1->concurrent_mark()) { }

G1ParClosureSuper::G1ParClosureSuper(G1CollectedHeap* g1) :
  _g1(g1), _par_scan_state(NULL), _worker_id(UINT_MAX) { }

G1ParClosureSuper::G1ParClosureSuper(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
  _g1(g1), _par_scan_state(NULL),
  _worker_id(UINT_MAX) {
  set_par_scan_thread_state(par_scan_state);
}

void G1ParClosureSuper::set_par_scan_thread_state(G1ParScanThreadState* par_scan_state) {
  assert(_par_scan_state == NULL, "_par_scan_state must only be set once");
  assert(par_scan_state != NULL, "Must set par_scan_state to non-NULL.");

  _par_scan_state = par_scan_state;
  _worker_id = par_scan_state->worker_id();

  assert(_worker_id < ParallelGCThreads,
         "The given worker id %u must be less than the number of threads %u", _worker_id, ParallelGCThreads);
}

// Generate G1 specialized oop_oop_iterate functions.
SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_G1(ALL_KLASS_OOP_OOP_ITERATE_DEFN)
