/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2025, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_COMPILATIONMEMSTATINTERNALS_INLINE_HPP
#define SHARE_COMPILATIONMEMSTATINTERNALS_INLINE_HPP

#include "compiler/compilationMemStatInternals.hpp"

inline PhaseInfoStack::PhaseInfoStack() : _depth(0) {}

inline void PhaseInfoStack::push(PhaseInfo info) {
#ifdef ASSERT
  check_phase_trace_id(info.id);
  if (_depth == 0) {
    assert(info.id == phase_trc_id_none, "first entry must be none");
  } else {
    assert(info.id != phase_trc_id_none, "subsequent entries must not be none");
  }
  assert(_depth < max_depth, "Sanity");
#endif // ASSERT
  _stack[_depth] = info;
  if (_depth < max_depth) {
    _depth++;
  }
}

inline void PhaseInfoStack::pop() {
#ifdef ASSERT
  assert(!empty(), "Sanity ");
  const PhaseInfo to_be_popped = top();
  if (_depth == 1) {
    assert(to_be_popped.id == phase_trc_id_none, "first entry must be none");
  } else {
    assert(to_be_popped.id != phase_trc_id_none, "subsequent entries must not be none");
  }
#endif // ASSERT
  if (_depth > 0) {
    _depth--;
  }
}

inline const PhaseInfo& PhaseInfoStack::top() const {
  assert(!empty(), "Sanity");
  return _stack[_depth - 1];
}

inline size_t ArenaCounterTable::at(int phase_trc_id, int arena_tag) const {
  check_phase_trace_id(phase_trc_id);
  check_arena_tag(arena_tag);
  return _v[phase_trc_id][arena_tag];
}

inline void ArenaCounterTable::add(size_t size, int phase_trc_id, int arena_tag) {
  check_arena_tag(arena_tag);
  const size_t old = at(phase_trc_id, arena_tag);
  _v[phase_trc_id][arena_tag] += size;
  assert(at(phase_trc_id, arena_tag) >= old, "Overflow");
}

inline void ArenaCounterTable::sub(size_t size, int phase_trc_id, int arena_tag) {
  check_arena_tag(arena_tag);
  assert(at(phase_trc_id, arena_tag) >= size, "Underflow (%zu %zu)", at(phase_trc_id, arena_tag), size);
  _v[phase_trc_id][arena_tag] -= size;
}

inline void FootprintTimeline::on_footprint_change(size_t cur_abs, unsigned cur_nodes) {
  assert(!_inbetween_phases, "no phase started?");
  Entry& e = _fifo.current();
  e._bytes.update(cur_abs);
  e._live_nodes.update(cur_nodes);
}

#endif // SHARE_COMPILATIONMEMSTATINTERNALS_INLINE_HPP
