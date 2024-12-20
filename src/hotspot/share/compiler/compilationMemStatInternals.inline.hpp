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

#include "compiler/compilationMemStatInternals.hpp"

inline void PhaseIdStack::push(PhaseTrcId id) {
  assert(_depth < max_depth, "Sanity");
  assert(_depth == 0 || top() != id, "Nesting identical phases?");
  _stack[_depth++] = id.raw();
}

inline void PhaseIdStack::pop(PhaseTrcId id) {
  assert(_depth > 1, "Sanity " PTR_FORMAT, p2i(this));
  assert(top() == id, "Mismatched PhaseTraceId pop (%d, expected %d)", id.raw(), top().raw());
  _depth --;
}

inline PhaseTrcId PhaseIdStack::top() const {
  assert(_depth > 0, "Sanity");
  return _stack[_depth - 1];
}

inline size_t ArenaCounterTable::at(PhaseTrcId id, ArenaTag tag) const {
  return _v[id.raw()][tag.raw()];
}

inline void ArenaCounterTable::set(size_t size, PhaseTrcId id, ArenaTag tag) {
  _v[id.raw()][tag.raw()] = size;
}

inline void ArenaCounterTable::add(size_t size, PhaseTrcId id, ArenaTag tag) {
  const size_t old = at(id, tag);
  _v[id.raw()][tag.raw()] += size;
  assert(at(id, tag) >= old, "Overflow");
}

inline void ArenaCounterTable::sub(size_t size, PhaseTrcId id, ArenaTag tag) {
  assert(at(id, tag) >= size, "Underflow");
  _v[id.raw()][tag.raw()] -= size;
}

inline void FootprintTimeline::on_footprint_change(size_t cur_abs) {
  Entry& e = at(_pos);
  e.cur = cur_abs;
  if (e.cur > e.peak) {
    e.peak = e.cur;
  }
}

#endif // SHARE_COMPILER_COMPILATIONMEMORYSTATISTIC_INLINE_HPP
