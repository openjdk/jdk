/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_SHARED_HSPACECOUNTERS_HPP
#define SHARE_VM_GC_IMPLEMENTATION_SHARED_HSPACECOUNTERS_HPP

#include "utilities/macros.hpp"
#include "gc_implementation/shared/generationCounters.hpp"
#include "memory/generation.hpp"
#include "runtime/perfData.hpp"

// A HSpaceCounter is a holder class for performance counters
// that track a collections (logical spaces) in a heap;

class HeapSpaceUsedHelper;
class G1SpaceMonitoringSupport;

class HSpaceCounters: public CHeapObj<mtGC> {
  friend class VMStructs;

 private:
  PerfVariable*        _capacity;
  PerfVariable*        _used;

  // Constant PerfData types don't need to retain a reference.
  // However, it's a good idea to document them here.

  char*             _name_space;

 public:

  HSpaceCounters(const char* name, int ordinal, size_t max_size,
                 size_t initial_capacity, GenerationCounters* gc);

  ~HSpaceCounters() {
    if (_name_space != NULL) FREE_C_HEAP_ARRAY(char, _name_space, mtGC);
  }

  inline void update_capacity(size_t v) {
    _capacity->set_value(v);
  }

  inline void update_used(size_t v) {
    _used->set_value(v);
  }

  debug_only(
    // for security reasons, we do not allow arbitrary reads from
    // the counters as they may live in shared memory.
    jlong used() {
      return _used->get_value();
    }
    jlong capacity() {
      return _used->get_value();
    }
  )

  inline void update_all(size_t capacity, size_t used) {
    update_capacity(capacity);
    update_used(used);
  }

  const char* name_space() const        { return _name_space; }
};
#endif // SHARE_VM_GC_IMPLEMENTATION_SHARED_HSPACECOUNTERS_HPP
