/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_COMPILER_COMPILATIONMEMORYSTATISTIC_INLINE_HPP
#define SHARE_COMPILER_COMPILATIONMEMORYSTATISTIC_INLINE_HPP

#include "compiler/compilationMemoryStatistic.hpp"

#ifdef COMPILER2
// C2 only: inform statistic about start and end of a compilation phase
inline void CompilationMemoryStatistic::on_c2_phase_start(Phase::PhaseTraceId id) {
  if (enabled()) {
    on_c2_phase_start_0(id);
  }
}

inline void CompilationMemoryStatistic::on_c2_phase_end() {
  if (enabled()) {
    on_c2_phase_end_0();
  }
}

PhaseIdStack::PhaseIdStack() : _depth(0) {
  // Let Stack never be empty to also catch allocations that happen outside a TracePhase scope
  push(Phase::PhaseTraceId::_t_none);
}

void PhaseIdStack::push(Phase::PhaseTraceId id) {
  assert(_depth < max_depth, "Sanity");
  _stack[_depth++] = id;
}

void PhaseIdStack::pop() {
  assert(_depth > 1, "Sanity");
  _depth --;
}

Phase::PhaseTraceId PhaseIdStack::`top() const {
  assert(_depth > 0, "Sanity");
  return _stack[_depth - 1];
}

void CountersPerC2Phase::add(size_t size, int arena_tag, Phase::PhaseTraceId id) {
  assert(arena_tag >= 0 && arena_tag < Arena::tag_count(), "sanity");
  int phaseid = (int)id;
  assert(phaseid >= 0 && phaseid < (int)Phase::PhaseTraceId::max_phase_timers, "sanity");
  _v[arena_tag][phaseid] += size;
}

void CountersPerC2Phase::sub(size_t size, int arena_tag, Phase::PhaseTraceId id) {
  assert(arena_tag >= 0 && arena_tag < Arena::tag_count(), "sanity");
  int phaseid = (int)id;
  assert(phaseid >= 0 && phaseid < (int)Phase::PhaseTraceId::max_phase_timers, "sanity");
  assert(_v[arena_tag][phaseid] >= size, "overflow");
  _v[arena_tag][phaseid] -= size;
}

#endif // COMPILER2

#endif // SHARE_COMPILER_COMPILATIONMEMORYSTATISTIC_INLINE_HPP
