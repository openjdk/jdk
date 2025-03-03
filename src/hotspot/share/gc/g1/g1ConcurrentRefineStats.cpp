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
#include "runtime/atomic.hpp"
#include "runtime/timer.hpp"

G1ConcurrentRefineStats::G1ConcurrentRefineStats() :
  _sweep_duration(0),
  _yield_duration(0),
  _cards_scanned(0),
  _cards_clean(0),
  _cards_not_parsable(0),
  _cards_still_refer_to_cset(0),
  _cards_refer_to_cset(0),
  _cards_clean_again(0),
  _refine_duration(0)
{}

void G1ConcurrentRefineStats::add_atomic(G1ConcurrentRefineStats* other) {
  Atomic::add(&_sweep_duration, other->_sweep_duration, memory_order_relaxed);
  Atomic::add(&_yield_duration, other->_yield_duration, memory_order_relaxed);

  Atomic::add(&_cards_scanned, other->_cards_scanned, memory_order_relaxed);
  Atomic::add(&_cards_clean, other->_cards_clean, memory_order_relaxed);
  Atomic::add(&_cards_not_parsable, other->_cards_not_parsable, memory_order_relaxed);
  Atomic::add(&_cards_still_refer_to_cset, other->_cards_still_refer_to_cset, memory_order_relaxed);
  Atomic::add(&_cards_refer_to_cset, other->_cards_refer_to_cset, memory_order_relaxed);
  Atomic::add(&_cards_clean_again, other->_cards_clean_again, memory_order_relaxed);

  Atomic::add(&_refine_duration, other->_refine_duration, memory_order_relaxed);
}

G1ConcurrentRefineStats&
G1ConcurrentRefineStats::operator+=(const G1ConcurrentRefineStats& other) {
  _sweep_duration += other._sweep_duration;
  _yield_duration += other._yield_duration;

  _cards_scanned += other._cards_scanned;
  _cards_clean += other._cards_clean;
  _cards_not_parsable += other._cards_not_parsable;
  _cards_still_refer_to_cset += other._cards_still_refer_to_cset;
  _cards_refer_to_cset += other._cards_refer_to_cset;
  _cards_clean_again += other._cards_clean_again;

  _refine_duration += other._refine_duration;
  return *this;
}

template<typename T>
static T saturated_sub(T x, T y) {
  return (x < y) ? T() : (x - y);
}

G1ConcurrentRefineStats&
G1ConcurrentRefineStats::operator-=(const G1ConcurrentRefineStats& other) {
  _sweep_duration = saturated_sub(_sweep_duration, other._sweep_duration);
  _yield_duration = saturated_sub(_yield_duration, other._yield_duration);

  _cards_scanned = saturated_sub(_cards_scanned, other._cards_scanned);
  _cards_clean = saturated_sub(_cards_clean, other._cards_clean);
  _cards_not_parsable = saturated_sub(_cards_not_parsable, other._cards_not_parsable);
  _cards_still_refer_to_cset = saturated_sub(_cards_still_refer_to_cset, other._cards_still_refer_to_cset);
  _cards_refer_to_cset = saturated_sub(_cards_refer_to_cset, other._cards_refer_to_cset);
  _cards_clean_again = saturated_sub(_cards_clean_again, other._cards_clean_again);

  _refine_duration = saturated_sub(_refine_duration, other._refine_duration);
  return *this;
}

void G1ConcurrentRefineStats::reset() {
  *this = G1ConcurrentRefineStats();
}
