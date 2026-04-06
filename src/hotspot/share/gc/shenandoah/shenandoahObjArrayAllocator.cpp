/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/memAllocator.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahObjArrayAllocator.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "memory/universe.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/arrayOop.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "utilities/copy.hpp"
#include "utilities/globalDefinitions.hpp"

ShenandoahObjArrayAllocator::ShenandoahObjArrayAllocator(
    Klass* klass, size_t word_size, int length, bool do_zero, Thread* thread)
  : ObjArrayAllocator(klass, word_size, length, do_zero, thread) {}

void ShenandoahObjArrayAllocator::yield_for_safepoint() const {
  ThreadBlockInVM tbivm(JavaThread::cast(_thread));
}

oop ShenandoahObjArrayAllocator::initialize(HeapWord* mem) const {
  // A max segment size of 64K was chosen because microbenchmarking
  // suggested that it offered a good trade-off between allocation
  // time and time-to-safepoint (same value used by ZGC).
  const size_t segment_max = 64 * K / BytesPerWord;

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // Fast path: delegate to base class for small arrays or no-zero case
  if (!_do_zero || _word_size <= segment_max || strcmp(ShenandoahGCMode, "passive") == 0) {
    return ObjArrayAllocator::initialize(mem);
  }

  // Slow path: segmented clearing for large arrays

  // Compute clearing bounds
  const BasicType element_type = ArrayKlass::cast(_klass)->element_type();
  const size_t base_offset_in_bytes = (size_t)arrayOopDesc::base_offset_in_bytes(element_type);
  const size_t process_start_offset_in_bytes = align_up(base_offset_in_bytes, (size_t)BytesPerWord);

  const size_t process_start = process_start_offset_in_bytes / BytesPerWord;
  const size_t process_size = _word_size - process_start;

  // Pin the region before segmented clearing avoid moving the object until it is done
  ShenandoahHeapRegion* region = heap->heap_region_containing(mem);
  region->record_pin();

  // Always initialize the mem with primitive array first so GC won't look into the elements in the array.
  // For obj array, the header will be corrected to object array after clearing the memory.
  Klass* filling_klass = _klass;
  int filling_array_length = _length;
  if (element_type == T_OBJECT || element_type == T_ARRAY) {
    filling_klass = Universe::longArrayKlass();
    filling_array_length = (int) (process_size / (T_LONG_aelem_bytes >> LogBytesPerWord));
  }
  ObjArrayAllocator filling_array_allocator(filling_klass, _word_size,  filling_array_length , /* do_zero */ false);
  filling_array_allocator.initialize(mem);

  // Invisible roots will be scanned and marked at the end of marking.
  ShenandoahThreadLocalData::set_invisible_root(_thread, mem, _word_size);

  // Handle potential 4-byte alignment gap before array data
  if (process_start_offset_in_bytes != base_offset_in_bytes) {
    assert(process_start_offset_in_bytes - base_offset_in_bytes == 4, "Must be 4-byte aligned");
    *reinterpret_cast<int*>(reinterpret_cast<char*>(mem) + base_offset_in_bytes) = 0;
  }

  // Segmented clearing with safepoint yields
  for (size_t processed = 0; processed < process_size; processed += segment_max) {
    HeapWord* const start = mem + process_start + processed;
    const size_t remaining = process_size - processed;
    const size_t segment = MIN2(remaining, segment_max);

    Copy::zero_to_words(start, segment);

    yield_for_safepoint();
  }

  // reference array, header need to be overridden to its own.
  if (element_type == T_OBJECT || element_type == T_ARRAY) {
    assert(_length >= 0, "length should be non-negative");
    arrayOopDesc::set_length(mem, _length);
    finish(mem);
  }

  // zap paddings after setting correct klass
  mem_zap_start_padding(mem);
  mem_zap_end_padding(mem);

  oop arrayObj = cast_to_oop(mem);
  if (heap->is_concurrent_young_mark_in_progress() && !heap->marking_context()->allocated_after_mark_start(arrayObj)) {
    // Keep the obj alive because we don't know the progress of marking,
    // current concurrent marking have done and VM is calling safepoint for final mark.
    heap->keep_alive(arrayObj);
  }
  ShenandoahThreadLocalData::clear_invisible_root(_thread);

  region->record_unpin();

  return arrayObj;
}
