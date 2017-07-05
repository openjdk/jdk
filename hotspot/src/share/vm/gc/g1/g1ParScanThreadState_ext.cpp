/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1ParScanThreadState.hpp"

G1ParScanThreadState* G1ParScanThreadStateSet::new_par_scan_state(uint worker_id, size_t young_cset_length) {
  return new G1ParScanThreadState(_g1h, worker_id, young_cset_length);
}

template <typename T>
void G1ParScanThreadState::do_oop_ext(T* ref) {
}

template void G1ParScanThreadState::do_oop_ext<oop>(oop* ref);
template void G1ParScanThreadState::do_oop_ext<narrowOop>(narrowOop* ref);
