/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/generation.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/space.inline.hpp"
#include "gc/shared/spaceDecorator.inline.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"

Generation::Generation(ReservedSpace rs, size_t initial_size) :
  _gc_manager(nullptr) {
  if (!_virtual_space.initialize(rs, initial_size)) {
    vm_exit_during_initialization("Could not reserve enough space for "
                    "object heap");
  }
  // Mangle all of the initial generation.
  if (ZapUnusedHeapArea) {
    MemRegion mangle_region((HeapWord*)_virtual_space.low(),
      (HeapWord*)_virtual_space.high());
    SpaceMangler::mangle_region(mangle_region);
  }
  _reserved = MemRegion((HeapWord*)_virtual_space.low_boundary(),
          (HeapWord*)_virtual_space.high_boundary());
}

size_t Generation::max_capacity() const {
  return reserved().byte_size();
}

void Generation::print() const { print_on(tty); }

void Generation::print_on(outputStream* st)  const {
  st->print(" %-20s", name());
  st->print(" total " SIZE_FORMAT "K, used " SIZE_FORMAT "K",
             capacity()/K, used()/K);
  st->print_cr(" [" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ")",
              p2i(_virtual_space.low_boundary()),
              p2i(_virtual_space.high()),
              p2i(_virtual_space.high_boundary()));
}

void Generation::print_summary_info_on(outputStream* st) {
  StatRecord* sr = stat_record();
  double time = sr->accumulated_time.seconds();
  st->print_cr("Accumulated %s generation GC time %3.7f secs, "
               "%u GC's, avg GC time %3.7f",
               SerialHeap::heap()->is_young_gen(this) ? "young" : "old" ,
               time,
               sr->invocations,
               sr->invocations > 0 ? time / sr->invocations : 0.0);
}

// Utility iterator classes

class GenerationIsInClosure : public SpaceClosure {
 public:
  const void* _p;
  Space* sp;
  virtual void do_space(Space* s) {
    if (sp == nullptr) {
      if (s->is_in(_p)) sp = s;
    }
  }
  GenerationIsInClosure(const void* p) : _p(p), sp(nullptr) {}
};

bool Generation::is_in(const void* p) const {
  GenerationIsInClosure blk(p);
  ((Generation*)this)->space_iterate(&blk);
  return blk.sp != nullptr;
}

size_t Generation::max_contiguous_available() const {
  // The largest number of contiguous free words in this or any higher generation.
  size_t avail = contiguous_available();
  size_t old_avail = 0;
  if (SerialHeap::heap()->is_young_gen(this)) {
    old_avail = SerialHeap::heap()->old_gen()->contiguous_available();
  }
  return MAX2(avail, old_avail);
}

// Ignores "ref" and calls allocate().
oop Generation::promote(oop obj, size_t obj_size) {
  assert(obj_size == obj->size(), "bad obj_size passed in");

#ifndef PRODUCT
  if (SerialHeap::heap()->promotion_should_fail()) {
    return nullptr;
  }
#endif  // #ifndef PRODUCT

  // Allocate new object.
  HeapWord* result = allocate(obj_size, false);
  if (result == nullptr) {
    // Promotion of obj into gen failed.  Try to expand and allocate.
    result = expand_and_allocate(obj_size, false);
    if (result == nullptr) {
      return nullptr;
    }
  }

  // Copy to new location.
  Copy::aligned_disjoint_words(cast_from_oop<HeapWord*>(obj), result, obj_size);
  oop new_obj = cast_to_oop<HeapWord*>(result);

  // Transform object if it is a stack chunk.
  ContinuationGCSupport::transform_stack_chunk(new_obj);

  return new_obj;
}

// Some of these are mediocre general implementations.  Should be
// overridden to get better performance.

class GenerationBlockStartClosure : public SpaceClosure {
 public:
  const void* _p;
  HeapWord* _start;
  virtual void do_space(Space* s) {
    if (_start == nullptr && s->is_in_reserved(_p)) {
      _start = s->block_start(_p);
    }
  }
  GenerationBlockStartClosure(const void* p) { _p = p; _start = nullptr; }
};

HeapWord* Generation::block_start(const void* p) const {
  GenerationBlockStartClosure blk(p);
  // Cast away const
  ((Generation*)this)->space_iterate(&blk);
  return blk._start;
}

class GenerationBlockIsObjClosure : public SpaceClosure {
 public:
  const HeapWord* _p;
  bool is_obj;
  virtual void do_space(Space* s) {
    if (!is_obj && s->is_in_reserved(_p)) {
      is_obj |= s->block_is_obj(_p);
    }
  }
  GenerationBlockIsObjClosure(const HeapWord* p) { _p = p; is_obj = false; }
};

bool Generation::block_is_obj(const HeapWord* p) const {
  GenerationBlockIsObjClosure blk(p);
  // Cast away const
  ((Generation*)this)->space_iterate(&blk);
  return blk.is_obj;
}
