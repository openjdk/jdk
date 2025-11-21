/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1ConcurrentRefineStats.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/timer.hpp"

G1ConcurrentRefineStats::G1ConcurrentRefineStats() :
  _sweep_duration(0),
  _yield_during_sweep_duration(0),
  _cards_scanned(0),
  _cards_clean(0),
  _cards_not_parsable(0),
  _cards_already_refer_to_cset(0),
  _cards_refer_to_cset(0),
  _cards_no_cross_region(0),
  _refine_duration(0)
{}

void G1ConcurrentRefineStats::add_atomic(G1ConcurrentRefineStats* other) {
  AtomicAccess::add(&_sweep_duration, other->_sweep_duration, memory_order_relaxed);
  AtomicAccess::add(&_yield_during_sweep_duration, other->_yield_during_sweep_duration, memory_order_relaxed);

  AtomicAccess::add(&_cards_scanned, other->_cards_scanned, memory_order_relaxed);
  AtomicAccess::add(&_cards_clean, other->_cards_clean, memory_order_relaxed);
  AtomicAccess::add(&_cards_not_parsable, other->_cards_not_parsable, memory_order_relaxed);
  AtomicAccess::add(&_cards_already_refer_to_cset, other->_cards_already_refer_to_cset, memory_order_relaxed);
  AtomicAccess::add(&_cards_refer_to_cset, other->_cards_refer_to_cset, memory_order_relaxed);
  AtomicAccess::add(&_cards_no_cross_region, other->_cards_no_cross_region, memory_order_relaxed);

  AtomicAccess::add(&_refine_duration, other->_refine_duration, memory_order_relaxed);
}

void G1ConcurrentRefineStats::reset() {
  *this = G1ConcurrentRefineStats();
}
