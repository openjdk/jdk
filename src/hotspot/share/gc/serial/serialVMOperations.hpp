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

#ifndef SHARE_GC_SERIAL_SERIALVMOPERATIONS_HPP
#define SHARE_GC_SERIAL_SERIALVMOPERATIONS_HPP

#include "gc/serial/serialHeap.hpp"
#include "gc/shared/gcVMOperations.hpp"

class VM_SerialCollectForAllocation : public VM_CollectForAllocation {
 private:
  bool        _tlab;                       // alloc is of a tlab.
 public:
  VM_SerialCollectForAllocation(size_t word_size,
                                bool tlab,
                                uint gc_count_before)
    : VM_CollectForAllocation(word_size, gc_count_before, GCCause::_allocation_failure),
      _tlab(tlab) {
    assert(word_size != 0, "An allocation should always be requested with this operation.");
  }
  virtual VMOp_Type type() const { return VMOp_SerialCollectForAllocation; }
  virtual void doit();
};

// VM operation to invoke a collection of the heap as a
// SerialHeap heap.
class VM_SerialGCCollect: public VM_GC_Operation {
 public:
  VM_SerialGCCollect(bool full,
                     uint gc_count_before,
                     uint full_gc_count_before,
                     GCCause::Cause gc_cause)
    : VM_GC_Operation(gc_count_before, gc_cause, full_gc_count_before, full) {}

  virtual VMOp_Type type() const { return VMOp_SerialGCCollect; }
  virtual void doit();
};


#endif // SHARE_GC_SERIAL_SERIALVMOPERATIONS_HPP
