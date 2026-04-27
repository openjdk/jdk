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

#include "gc/shared/memAllocator.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
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
  : ObjArrayAllocator(klass, word_size, length, do_zero, thread) {
  assert(_length >= 0, "length should be non-negative");
}

oop ShenandoahObjArrayAllocator::initialize(HeapWord* mem) const {
  // threshold of object size, while above this size current mutator will yield to safepoint
  // when it clears the array content.
  constexpr size_t THRESHOLD = 64 * K / BytesPerWord;

  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  // Fast path: delegate to base class for small arrays or no-zero case.
  // In no-zero case(_do_zero is false), the content of the array won't be zeroed, therefore no need to fall into slow-path.
  if (!_do_zero || _word_size <= THRESHOLD) {
    return ObjArrayAllocator::initialize(mem);
  }

  // Slow path: yield to safepoint when clearing for large arrays

  // Compute clearing bounds
  const BasicType element_type = ArrayKlass::cast(_klass)->element_type();
  const size_t base_offset_in_bytes = (size_t)arrayOopDesc::base_offset_in_bytes(element_type);
  const size_t process_start_offset_in_bytes = align_up(base_offset_in_bytes, (size_t)BytesPerWord);

  const size_t process_start = process_start_offset_in_bytes / BytesPerWord;
  const size_t process_size = _word_size - process_start;

  // Pin the region before clearing to avoid moving the object until it is done
  ShenandoahHeapRegion* region = heap->heap_region_containing(mem);
  region->record_pin();

  // Always initialize the mem with primitive array first so GC won't look into the elements in the array.
  // For obj array, the header will be corrected to object array after clearing the memory.
  Klass* filling_klass = _klass;
  int filling_array_length = _length;
  const bool is_ref_type = is_reference_type(element_type, true);

  if (is_ref_type) {
    const bool is_narrow_oop = element_type == T_NARROWOOP;
    size_t filling_element_byte_size = is_narrow_oop ? T_INT_aelem_bytes : T_LONG_aelem_bytes;
    filling_klass = is_narrow_oop ? Universe::intArrayKlass() : Universe::longArrayKlass();
    filling_array_length = (int) ((process_size << LogBytesPerWord) / filling_element_byte_size);
  }
  ObjArrayAllocator filling_array_allocator(filling_klass, _word_size,  filling_array_length , /* do_zero */ false);
  filling_array_allocator.initialize(mem);

  // Invisible roots will be scanned and marked at the end of marking.
  ShenandoahThreadLocalData::set_invisible_root(_thread, mem, _word_size);

  {
    // The mem has been initialized as primitive array, the entire clearing work is safe for safepoint
    ThreadBlockInVM tbivm(JavaThread::cast(_thread)); // Allow safepoint to proceed.
    // Handle potential 4-byte alignment gap before array data
    if (process_start_offset_in_bytes != base_offset_in_bytes) {
      assert(process_start_offset_in_bytes - base_offset_in_bytes == 4, "Must be 4-byte aligned");
      *reinterpret_cast<int*>(reinterpret_cast<char*>(mem) + base_offset_in_bytes) = 0;
    }

    Copy::zero_to_words(mem + process_start, process_size);

    if (!is_ref_type) {
      // zap paddings
      mem_zap_start_padding(mem);
      mem_zap_end_padding(mem);
    }
  }

  // reference array, header need to be overridden to its own.
  if (is_ref_type) {
    arrayOopDesc::set_length(mem, _length);
    finish(mem);
    // zap paddings after setting correct klass
    mem_zap_start_padding(mem);
    mem_zap_end_padding(mem);
  }

  oop arrayObj = cast_to_oop(mem);
  if (heap->is_concurrent_young_mark_in_progress() && !heap->marking_context()->allocated_after_mark_start(arrayObj)) {
    // Keep the obj alive because we don't know the progress of marking,
    // current concurrent marking could have done and VM is calling safepoint for final mark.
    heap->keep_alive(arrayObj);
  }
  ShenandoahThreadLocalData::clear_invisible_root(_thread);

  region->record_unpin();

  return arrayObj;
}
