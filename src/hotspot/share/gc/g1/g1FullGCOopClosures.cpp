/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1FullGCMarker.inline.hpp"
#include "gc/g1/g1FullGCOopClosures.inline.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"

void G1FollowStackClosure::do_void() { _marker->drain_stack(); }

void G1FullKeepAliveClosure::do_oop(oop* p) { do_oop_work(p); }
void G1FullKeepAliveClosure::do_oop(narrowOop* p) { do_oop_work(p); }

G1VerifyOopClosure::G1VerifyOopClosure(VerifyOption option) :
   _g1h(G1CollectedHeap::heap()),
   _failures(false),
   _containing_obj(NULL),
   _verify_option(option),
   _cc(0) {
}

void G1VerifyOopClosure::print_object(outputStream* out, oop obj) {
#ifdef PRODUCT
  Klass* k = obj->klass();
  const char* class_name = InstanceKlass::cast(k)->external_name();
  out->print_cr("class name %s", class_name);
#else // PRODUCT
  obj->print_on(out);
#endif // PRODUCT
}

template <class T> void G1VerifyOopClosure::do_oop_work(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(heap_oop)) {
    _cc++;
    oop obj = CompressedOops::decode_not_null(heap_oop);
    bool failed = false;
    if (!_g1h->is_in_closed_subset(obj) || _g1h->is_obj_dead_cond(obj, _verify_option)) {
      MutexLockerEx x(ParGCRareEvent_lock,
          Mutex::_no_safepoint_check_flag);
      LogStreamHandle(Error, gc, verify) yy;
      if (!_failures) {
        yy.cr();
        yy.print_cr("----------");
      }
      if (!_g1h->is_in_closed_subset(obj)) {
        HeapRegion* from = _g1h->heap_region_containing((HeapWord*)p);
        yy.print_cr("Field " PTR_FORMAT
            " of live obj " PTR_FORMAT " in region "
            "[" PTR_FORMAT ", " PTR_FORMAT ")",
            p2i(p), p2i(_containing_obj),
            p2i(from->bottom()), p2i(from->end()));
        print_object(&yy, _containing_obj);
        yy.print_cr("points to obj " PTR_FORMAT " not in the heap",
            p2i(obj));
      } else {
        HeapRegion* from = _g1h->heap_region_containing((HeapWord*)p);
        HeapRegion* to   = _g1h->heap_region_containing((HeapWord*)obj);
        yy.print_cr("Field " PTR_FORMAT
            " of live obj " PTR_FORMAT " in region "
            "[" PTR_FORMAT ", " PTR_FORMAT ")",
            p2i(p), p2i(_containing_obj),
            p2i(from->bottom()), p2i(from->end()));
        print_object(&yy, _containing_obj);
        yy.print_cr("points to dead obj " PTR_FORMAT " in region "
            "[" PTR_FORMAT ", " PTR_FORMAT ")",
            p2i(obj), p2i(to->bottom()), p2i(to->end()));
        print_object(&yy, obj);
      }
      yy.print_cr("----------");
      yy.flush();
      _failures = true;
      failed = true;
    }
  }
}

template void G1VerifyOopClosure::do_oop_work(oop*);
template void G1VerifyOopClosure::do_oop_work(narrowOop*);
