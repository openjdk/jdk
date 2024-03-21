/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "gc/z/zObjArrayAllocator.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "oops/arrayKlass.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "utilities/debug.hpp"

ZObjArrayAllocator::ZObjArrayAllocator(Klass* klass, size_t word_size, int length, bool do_zero, Thread* thread)
  : ObjArrayAllocator(klass, word_size, length, do_zero, thread) {}

void ZObjArrayAllocator::yield_for_safepoint() const {
  ThreadBlockInVM tbivm(JavaThread::cast(_thread));
}

oop ZObjArrayAllocator::initialize(HeapWord* mem) const {
  // ZGC specializes the initialization by performing segmented clearing
  // to allow shorter time-to-safepoints.

  if (!_do_zero) {
    // No need for ZGC specialization
    return ObjArrayAllocator::initialize(mem);
  }

  // A max segment size of 64K was chosen because microbenchmarking
  // suggested that it offered a good trade-off between allocation
  // time and time-to-safepoint
  const size_t segment_max = ZUtils::bytes_to_words(64 * K);
  const BasicType element_type = ArrayKlass::cast(_klass)->element_type();

  // Clear leading 32 bits, if necessary.
  int base_offset = arrayOopDesc::base_offset_in_bytes(element_type);
  if (!is_aligned(base_offset, HeapWordSize)) {
    assert(is_aligned(base_offset, BytesPerInt), "array base must be 32 bit aligned");
    *reinterpret_cast<jint*>(reinterpret_cast<char*>(mem) + base_offset) = 0;
    base_offset += BytesPerInt;
  }
  assert(is_aligned(base_offset, HeapWordSize), "remaining array base must be 64 bit aligned");

  const size_t header = heap_word_size(base_offset);
  const size_t payload_size = _word_size - header;

  if (payload_size <= segment_max) {
    // To small to use segmented clearing
    return ObjArrayAllocator::initialize(mem);
  }

  // Segmented clearing

  // The array is going to be exposed before it has been completely
  // cleared, therefore we can't expose the header at the end of this
  // function. Instead explicitly initialize it according to our needs.

  // Signal to the ZIterator that this is an invisible root, by setting
  // the mark word to "marked". Reset to prototype() after the clearing.
  arrayOopDesc::set_mark(mem, markWord::prototype().set_marked());
  arrayOopDesc::release_set_klass(mem, _klass);
  assert(_length >= 0, "length should be non-negative");
  arrayOopDesc::set_length(mem, _length);

  // Keep the array alive across safepoints through an invisible
  // root. Invisible roots are not visited by the heap iterator
  // and the marking logic will not attempt to follow its elements.
  // Relocation and remembered set code know how to dodge iterating
  // over such objects.
  ZThreadLocalData::set_invisible_root(_thread, (zaddress_unsafe*)&mem);

  uint32_t old_seqnum_before = ZGeneration::old()->seqnum();
  uint32_t young_seqnum_before = ZGeneration::young()->seqnum();
  uintptr_t color_before = ZPointerStoreGoodMask;
  auto gc_safepoint_happened = [&]() {
    return old_seqnum_before != ZGeneration::old()->seqnum() ||
           young_seqnum_before != ZGeneration::young()->seqnum() ||
           color_before != ZPointerStoreGoodMask;
  };

  bool seen_gc_safepoint = false;

  auto initialize_memory = [&]() {
    for (size_t processed = 0; processed < payload_size; processed += segment_max) {
      // Clear segment
      uintptr_t* const start = (uintptr_t*)(mem + header + processed);
      const size_t remaining = payload_size - processed;
      const size_t segment = MIN2(remaining, segment_max);
      // Usually, the young marking code has the responsibility to color
      // raw nulls, before they end up in the old generation. However, the
      // invisible roots are hidden from the marking code, and therefore
      // we must color the nulls already here in the initialization. The
      // color we choose must be store bad for any subsequent stores, regardless
      // of how many GC flips later it will arrive. That's why we OR in 11
      // (ZPointerRememberedMask) in the remembered bits, similar to how
      // forgotten old oops also have 11, for the very same reason.
      // However, we opportunistically try to color without the 11 remembered
      // bits, hoping to not get interrupted in the middle of a GC safepoint.
      // Most of the time, we manage to do that, and can the avoid having GC
      // barriers trigger slow paths for this.
      const uintptr_t colored_null = seen_gc_safepoint ? (ZPointerStoreGoodMask | ZPointerRememberedMask)
                                                       : ZPointerStoreGoodMask;
      const uintptr_t fill_value = is_reference_type(element_type) ? colored_null : 0;
      ZUtils::fill(start, segment, fill_value);

      // Safepoint
      yield_for_safepoint();

      // Deal with safepoints
      if (!seen_gc_safepoint && gc_safepoint_happened()) {
        // The first time we observe a GC safepoint in the yield point,
        // we have to restart processing with 11 remembered bits.
        seen_gc_safepoint = true;
        return false;
      }
    }
    return true;
  };

  if (!initialize_memory()) {
    // Re-color with 11 remset bits if we got intercepted by a GC safepoint
    const bool result = initialize_memory();
    assert(result, "Array initialization should always succeed the second time");
  }

  mem_zap_end_padding(mem);

  ZThreadLocalData::clear_invisible_root(_thread);

  // Signal to the ZIterator that this is no longer an invisible root
  oopDesc::release_set_mark(mem, markWord::prototype());

  return cast_to_oop(mem);
}
