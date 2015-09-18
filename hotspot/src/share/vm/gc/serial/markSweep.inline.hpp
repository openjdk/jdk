/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SERIAL_MARKSWEEP_INLINE_HPP
#define SHARE_VM_GC_SERIAL_MARKSWEEP_INLINE_HPP

#include "gc/serial/markSweep.hpp"
#include "memory/universe.hpp"
#include "oops/markOop.inline.hpp"
#include "oops/oop.inline.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/g1MarkSweep.hpp"
#endif // INCLUDE_ALL_GCS

inline bool MarkSweep::is_archive_object(oop object) {
#if INCLUDE_ALL_GCS
  return (G1MarkSweep::archive_check_enabled() &&
          G1MarkSweep::in_archive_range(object));
#else
  return false;
#endif
}

inline int MarkSweep::adjust_pointers(oop obj) {
  return obj->ms_adjust_pointers();
}

template <class T> inline void MarkSweep::adjust_pointer(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj     = oopDesc::decode_heap_oop_not_null(heap_oop);
    assert(Universe::heap()->is_in(obj), "should be in heap");

    oop new_obj = oop(obj->mark()->decode_pointer());
    assert(is_archive_object(obj) ||                  // no forwarding of archive objects
           new_obj != NULL ||                         // is forwarding ptr?
           obj->mark() == markOopDesc::prototype() || // not gc marked?
           (UseBiasedLocking && obj->mark()->has_bias_pattern()),
           // not gc marked?
           "should be forwarded");
    if (new_obj != NULL) {
      if (!is_archive_object(obj)) {
        assert(Universe::heap()->is_in_reserved(new_obj),
              "should be in object space");
        oopDesc::encode_store_heap_oop_not_null(p, new_obj);
      }
    }
  }
}

#endif // SHARE_VM_GC_SERIAL_MARKSWEEP_INLINE_HPP
