/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_CHUNKEDARRAYPROCESSOR_HPP
#define SHARE_GC_SHARED_CHUNKEDARRAYPROCESSOR_HPP

#include "gc/shared/partialArrayTaskStepper.hpp"
#include "oop/objArrayOop.hpp"

class PartialArrayStateAllocator;
class PartialArrayState;

class ChunkedArrayProcessor {
private:
  PartialArrayTaskStepper           _partial_array_stepper;
  PartialArrayStateAllocator* const _partial_array_state_allocator;
  uint                              _partial_array_state_allocator_index;

public:
  ChunkedArrayProcessor(T* queue, PartialArrayStateAllocator* allocator) :
    _partial_array_state_allocator(allocator),
    _partial_array_state_allocator_index(UINT_MAX) { }

  void set_partial_array_state_allocator_index(uint i) { _partial_array_state_allocator_index = i; }

  template <typename PUSH_FUNC, typename PROC_FUNC>
  void begin_chunk_array(objArrayOop old_obj, objArrayOop new_obj, PUSH_FUNC pushf, PROC_FUNC procf);

  template <typename PUSH_FUNC, typename PROC_FUNC>
  void process_array_chunk(PartialArrayState* state, PUSH_FUNC pushf, PROC_FUNC procf);
};

#endif // SHARE_GC_SHARED_CHUNKEDARRAYPROCESSOR_HPP
