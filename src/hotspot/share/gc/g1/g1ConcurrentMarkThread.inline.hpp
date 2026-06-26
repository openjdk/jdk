/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CONCURRENTMARKTHREAD_INLINE_HPP
#define SHARE_GC_G1_G1CONCURRENTMARKTHREAD_INLINE_HPP

#include "gc/g1/g1ConcurrentMarkThread.hpp"

#include "gc/g1/g1ConcurrentMark.hpp"
#include "runtime/os.hpp"

  // Total virtual time so far.
inline double G1ConcurrentMarkThread::total_mark_cpu_time_s() {
  return static_cast<double>(os::thread_cpu_time(this)) / NANOSECS_PER_SEC + worker_threads_cpu_time_s();
}

// Marking virtual time so far
inline double G1ConcurrentMarkThread::worker_threads_cpu_time_s() {
  return _cm->worker_threads_cpu_time_s();
}

inline bool G1ConcurrentMarkThread::is_in_full_concurrent_cycle() const {
  ServiceState st = state();
  return (st == FullCycleMarking || st == FullCycleRebuildOrScrub || st == FullCycleResetForNextCycle);
}

inline void G1ConcurrentMarkThread::set_idle() {
  // Concurrent cycle may be aborted any time.
  assert(!is_idle(), "must not be idle");
  _state.store_relaxed(Idle);
}

inline void G1ConcurrentMarkThread::start_full_cycle() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  assert(is_idle(), "cycle in progress");
  _state.store_relaxed(FullCycleMarking);
}

inline void G1ConcurrentMarkThread::start_undo_cycle() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  assert(is_idle(), "cycle in progress");
  _state.store_relaxed(UndoCycleResetForNextCycle);
}

inline void G1ConcurrentMarkThread::set_full_cycle_rebuild_and_scrub() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  assert(state() == FullCycleMarking, "must be");
  _state.store_relaxed(FullCycleRebuildOrScrub);
}

inline void G1ConcurrentMarkThread::set_full_cycle_reset_for_next_cycle() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  assert(state() == FullCycleRebuildOrScrub, "must be");
  _state.store_relaxed(FullCycleResetForNextCycle);
}

inline bool G1ConcurrentMarkThread::is_in_marking() const {
  return state() == FullCycleMarking;
}

inline bool G1ConcurrentMarkThread::is_in_marking_or_rebuild() const {
  ServiceState st = state();
  return st == FullCycleMarking || st == FullCycleRebuildOrScrub;
}

inline bool G1ConcurrentMarkThread::is_in_reset_for_next_cycle() const {
  ServiceState st = state();
  return st == FullCycleResetForNextCycle || st == UndoCycleResetForNextCycle;
}

inline bool G1ConcurrentMarkThread::is_idle() const {
  return state() == Idle;
}

inline bool G1ConcurrentMarkThread::is_in_progress() const {
  return !is_idle();
}

inline bool G1ConcurrentMarkThread::is_in_undo_cycle() const {
  return state() == UndoCycleResetForNextCycle;
}

#endif // SHARE_GC_G1_G1CONCURRENTMARKTHREAD_INLINE_HPP
