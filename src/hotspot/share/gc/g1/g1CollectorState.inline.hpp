/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1COLLECTORSTATE_INLINE_HPP
#define SHARE_GC_G1_G1COLLECTORSTATE_INLINE_HPP

#include "gc/g1/g1CollectorState.hpp"

#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"

inline void G1CollectorState::set_in_normal_young_gc() {
  _phase = Phase::YoungNormal;
}
inline void G1CollectorState::set_in_space_reclamation_phase() {
  _phase = Phase::Mixed;
}
inline void G1CollectorState::set_in_full_gc() {
  _phase = Phase::FullGC;
}

inline void G1CollectorState::set_in_concurrent_start_gc() {
  _phase = Phase::YoungConcurrentStart;
  _initiate_conc_mark_if_possible = false;
}
inline void G1CollectorState::set_in_prepare_mixed_gc() {
  _phase = Phase::YoungPrepareMixed;
}

inline void G1CollectorState::set_initiate_conc_mark_if_possible(bool v) {
  _initiate_conc_mark_if_possible = v;
}

inline bool G1CollectorState::is_in_young_only_phase() const {
  return _phase == Phase::YoungNormal ||
         _phase == Phase::YoungConcurrentStart ||
         _phase == Phase::YoungPrepareMixed;
}
inline bool G1CollectorState::is_in_mixed_phase() const {
  return _phase == Phase::Mixed;
}

inline bool G1CollectorState::is_in_prepare_mixed_gc() const {
  return _phase == Phase::YoungPrepareMixed;
}
inline bool G1CollectorState::is_in_full_gc() const {
  return _phase == Phase::FullGC;
}
inline bool G1CollectorState::is_in_concurrent_start_gc() const {
  return _phase == Phase::YoungConcurrentStart;
}

inline bool G1CollectorState::initiate_conc_mark_if_possible() const {
  return _initiate_conc_mark_if_possible;
}

inline bool G1CollectorState::is_in_concurrent_cycle() const {
  G1ConcurrentMark* cm = G1CollectedHeap::heap()->concurrent_mark();
  return cm->is_in_concurrent_cycle();
}
inline bool G1CollectorState::is_in_marking() const {
  G1ConcurrentMark* cm = G1CollectedHeap::heap()->concurrent_mark();
  return cm->is_in_marking();
}
inline bool G1CollectorState::is_in_mark_or_rebuild() const {
  G1ConcurrentMark* cm = G1CollectedHeap::heap()->concurrent_mark();
  return is_in_marking() || cm->is_in_rebuild_or_scrub();
}
inline bool G1CollectorState::is_in_reset_for_next_cycle() const {
  G1ConcurrentMark* cm = G1CollectedHeap::heap()->concurrent_mark();
  return cm->is_in_reset_for_next_cycle();
}

inline void G1CollectorState::assert_is_young_pause(Pause type) {
  assert(type != Pause::Full, "must be");
  assert(type != Pause::Remark, "must be");
  assert(type != Pause::Cleanup, "must be");
}

inline bool G1CollectorState::is_young_only_pause(Pause type) {
  assert_is_young_pause(type);
  return type == Pause::ConcurrentStartUndo ||
         type == Pause::ConcurrentStartFull ||
         type == Pause::PrepareMixed ||
         type == Pause::Normal;
}

inline bool G1CollectorState::is_mixed_pause(Pause type) {
  assert_is_young_pause(type);
  return type == Pause::Mixed;
}

inline bool G1CollectorState::is_prepare_mixed_pause(Pause type) {
  assert_is_young_pause(type);
  return type == Pause::PrepareMixed;
}

inline bool G1CollectorState::is_concurrent_start_pause(Pause type) {
  assert_is_young_pause(type);
  return type == Pause::ConcurrentStartFull || type == Pause::ConcurrentStartUndo;
}

inline bool G1CollectorState::is_concurrent_cycle_pause(Pause type) {
  return type == Pause::Cleanup || type == Pause::Remark;
}

#endif // SHARE_GC_G1_G1COLLECTORSTATE_INLINE_HPP
