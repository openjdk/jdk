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
#include "gc/x/xThreadLocalData.hpp"
#include "gc/x/xObjArrayAllocator.hpp"
#include "gc/x/xUtils.inline.hpp"
#include "oops/arrayKlass.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "utilities/debug.hpp"

XObjArrayAllocator::XObjArrayAllocator(Klass* klass, size_t word_size, int length, bool do_zero, Thread* thread) :
    ObjArrayAllocator(klass, word_size, length, do_zero, thread) {}

void XObjArrayAllocator::yield_for_safepoint() const {
  ThreadBlockInVM tbivm(JavaThread::cast(_thread));
}

oop XObjArrayAllocator::initialize(HeapWord* mem) const {
  // ZGC specializes the initialization by performing segmented clearing
  // to allow shorter time-to-safepoints.

  if (!_do_zero) {
    // No need for ZGC specialization
    return ObjArrayAllocator::initialize(mem);
  }

  // A max segment size of 64K was chosen because microbenchmarking
  // suggested that it offered a good trade-off between allocation
  // time and time-to-safepoint
  const size_t segment_max = XUtils::bytes_to_words(64 * K);
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
  arrayOopDesc::set_mark(mem, markWord::prototype());
  arrayOopDesc::release_set_klass(mem, _klass);
  assert(_length >= 0, "length should be non-negative");
  arrayOopDesc::set_length(mem, _length);

  // Keep the array alive across safepoints through an invisible
  // root. Invisible roots are not visited by the heap itarator
  // and the marking logic will not attempt to follow its elements.
  // Relocation knows how to dodge iterating over such objects.
  XThreadLocalData::set_invisible_root(_thread, (oop*)&mem);

  for (size_t processed = 0; processed < payload_size; processed += segment_max) {
    // Calculate segment
    HeapWord* const start = (HeapWord*)(mem + header + processed);
    const size_t remaining = payload_size - processed;
    const size_t segment_size = MIN2(remaining, segment_max);

    // Clear segment
    Copy::zero_to_words(start, segment_size);

    // Safepoint
    yield_for_safepoint();
  }

  XThreadLocalData::clear_invisible_root(_thread);

  return cast_to_oop(mem);
}
