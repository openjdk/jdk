/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SERIAL_SERIALHEAP_HPP
#define SHARE_GC_SERIAL_SERIALHEAP_HPP

#include "gc/serial/defNewGeneration.hpp"
#include "gc/serial/tenuredGeneration.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "utilities/growableArray.hpp"

class GCMemoryManager;
class MemoryPool;
class OopIterateClosure;
class TenuredGeneration;

// SerialHeap is the implementation of CollectedHeap for Serial GC.
//
// The heap is reserved up-front in a single contiguous block, split into two
// parts, the young and old generation. The young generation resides at lower
// addresses, the old generation at higher addresses. The boundary address
// between the generations is fixed. Within a generation, committed memory
// grows towards higher addresses.
//
//
// low                                                                              high
//
//                                              +-- generation boundary (fixed after startup)
//                                              |
// |<-    young gen (reserved MaxNewSize)     ->|<- old gen (reserved MaxOldSize) ->|
// +-----------------+--------+--------+--------+---------------+-------------------+
// |       eden      |  from  |   to   |        |      old      |                   |
// |                 |  (to)  | (from) |        |               |                   |
// +-----------------+--------+--------+--------+---------------+-------------------+
// |<-          committed            ->|        |<- committed ->|
//
class SerialHeap : public GenCollectedHeap {
private:
  MemoryPool* _eden_pool;
  MemoryPool* _survivor_pool;
  MemoryPool* _old_pool;

  void initialize_serviceability() override;

public:
  static SerialHeap* heap();

  SerialHeap();

  Name kind() const override {
    return CollectedHeap::Serial;
  }

  const char* name() const override {
    return "Serial";
  }

  GrowableArray<GCMemoryManager*> memory_managers() override;
  GrowableArray<MemoryPool*> memory_pools() override;

  DefNewGeneration* young_gen() const {
    assert(_young_gen->kind() == Generation::DefNew, "Wrong generation type");
    return static_cast<DefNewGeneration*>(_young_gen);
  }

  TenuredGeneration* old_gen() const {
    assert(_old_gen->kind() == Generation::MarkSweepCompact, "Wrong generation type");
    return static_cast<TenuredGeneration*>(_old_gen);
  }

  // Apply "cur->do_oop" or "older->do_oop" to all the oops in objects
  // allocated since the last call to save_marks in the young generation.
  // The "cur" closure is applied to references in the younger generation
  // at "level", and the "older" closure to older generations.
  template <typename OopClosureType1, typename OopClosureType2>
  void oop_since_save_marks_iterate(OopClosureType1* cur,
                                    OopClosureType2* older);

  void young_process_roots(OopIterateClosure* root_closure,
                           OopIterateClosure* old_gen_closure,
                           CLDClosure* cld_closure);

  void safepoint_synchronize_begin() override;
  void safepoint_synchronize_end() override;

  // Support for loading objects from CDS archive into the heap
  bool can_load_archived_objects() const override { return UseCompressedOops; }
  HeapWord* allocate_loaded_archive_space(size_t size) override;
  void complete_loaded_archive_space(MemRegion archive_space) override;

  void pin_object(JavaThread* thread, oop obj) override;
  void unpin_object(JavaThread* thread, oop obj) override;
};

#endif // SHARE_GC_SERIAL_SERIALHEAP_HPP
