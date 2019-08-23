/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zObjArrayAllocator.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "memory/universe.hpp"
#include "oops/arrayKlass.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/handles.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// To avoid delaying safepoints, clearing of arrays is split up in segments
// with safepoint polling inbetween. However, we can't have a not-yet-cleared
// array of oops on the heap when we safepoint since the GC will then stumble
// across uninitialized oops. To avoid this we let an array of oops be an
// array of a primitive type of the same size until the clearing has completed.
// A max segment size of 64K was chosen because benchmarking suggests that is
// offers a good trade-off between allocation time and time-to-safepoint.

static Klass* substitute_object_array_klass(Klass* klass) {
  if (!klass->is_objArray_klass()) {
    return klass;
  }

  Klass* const substitute_klass = Universe::longArrayKlassObj();
  const BasicType type = ArrayKlass::cast(klass)->element_type();
  const BasicType substitute_type = ArrayKlass::cast(substitute_klass)->element_type();
  assert(type2aelembytes(type) == type2aelembytes(substitute_type), "Element size mismatch");
  return substitute_klass;
}

ZObjArrayAllocator::ZObjArrayAllocator(Klass* klass, size_t word_size, int length, Thread* thread) :
    ObjArrayAllocator(substitute_object_array_klass(klass), word_size, length, false /* do_zero */, thread),
    _final_klass(klass) {}

oop ZObjArrayAllocator::finish(HeapWord* mem) const {
  HandleMark hm;
  Handle array(_thread, ObjArrayAllocator::finish(mem));

  const size_t segment_max = ZUtils::bytes_to_words(64 * K);
  const size_t skip = arrayOopDesc::header_size(ArrayKlass::cast(_klass)->element_type());
  size_t remaining = _word_size - skip;

  while (remaining > 0) {
    // Clear segment
    const size_t segment = MIN2(remaining, segment_max);
    Copy::zero_to_words((HeapWord*)array() + (_word_size - remaining), segment);
    remaining -= segment;

    if (remaining > 0) {
      // Safepoint
      ThreadBlockInVM tbivm((JavaThread*)_thread);
    }
  }

  if (_klass != _final_klass) {
    // Set final klass
    oopDesc::release_set_klass((HeapWord*)array(), _final_klass);
  }

  return array();
}
