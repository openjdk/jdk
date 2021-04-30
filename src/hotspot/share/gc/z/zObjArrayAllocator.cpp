/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include <algorithm>

ZObjArrayAllocator::ZObjArrayAllocator(Klass* klass, size_t word_size, int length, Thread* thread) :
    ObjArrayAllocator(klass, word_size, length, false /* do_zero */, thread) {}

oop ZObjArrayAllocator::finish(HeapWord* mem) const {
  // Initialize object header and length field
  ObjArrayAllocator::finish(mem);

  // A max segment size of 64K was chosen because microbenchmarking
  // suggested that it offered a good trade-off between allocation
  // time and time-to-safepoint
  const size_t segment_max = SIZE_MAX; // FIXME: re-enable ZUtils::bytes_to_words(64 * K);
  const BasicType element_type = ArrayKlass::cast(_klass)->element_type();
  const bool color_payload = is_reference_type(element_type) && ZUtils::words_to_bytes(_word_size) > ZObjectSizeLimitMedium;
  const size_t skip = arrayOopDesc::header_size(element_type);
  const size_t payload_size = _word_size - skip;
  size_t processed = 0;

  while (processed < payload_size) {
    // Clear segment
    const size_t remaining = payload_size - processed;
    const size_t segment = MIN2(remaining, segment_max);
    uintptr_t fill_value = color_payload ? (ZAddressStoreGoodMask | ZAddressRememberedMask) : 0;
    uintptr_t* const start = (uintptr_t*)(mem + skip + processed);
    uintptr_t* const end = start + segment;
    std::fill_n(start, segment, fill_value);
    processed += segment;

    if (processed < payload_size) {
      fatal("Shouldn't enter this path right now");
      // Keep the array alive across safepoints through an invisible
      // root. Invisible roots are not visited by the heap itarator
      // and the marking logic will not attempt to follow its elements.
      ZThreadLocalData::set_invisible_root(_thread, (oop*)&mem, color_payload ? processed : 0);
      {
        // Safepoint
        ThreadBlockInVM tbivm(JavaThread::cast(_thread));
      }
      ZThreadLocalData::clear_invisible_root(_thread);
    }
  }

  return cast_to_oop(mem);
}
