/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_CONCURRENTMARKSWEEP_CMSOOPCLOSURES_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_CONCURRENTMARKSWEEP_CMSOOPCLOSURES_INLINE_HPP

#include "gc_implementation/concurrentMarkSweep/cmsOopClosures.hpp"
#include "gc_implementation/concurrentMarkSweep/concurrentMarkSweepGeneration.hpp"
#include "oops/oop.inline.hpp"

// Trim our work_queue so its length is below max at return
inline void Par_MarkRefsIntoAndScanClosure::trim_queue(uint max) {
  while (_work_queue->size() > max) {
    oop newOop;
    if (_work_queue->pop_local(newOop)) {
      assert(newOop->is_oop(), "Expected an oop");
      assert(_bit_map->isMarked((HeapWord*)newOop),
             "only grey objects on this stack");
      // iterate over the oops in this oop, marking and pushing
      // the ones in CMS heap (i.e. in _span).
      newOop->oop_iterate(&_par_pushAndMarkClosure);
    }
  }
}

// MetadataAwareOopClosure and MetadataAwareOopsInGenClosure are duplicated,
// until we get rid of OopsInGenClosure.

inline void MetadataAwareOopsInGenClosure::do_klass_nv(Klass* k) {
  ClassLoaderData* cld = k->class_loader_data();
  do_class_loader_data(cld);
}
inline void MetadataAwareOopsInGenClosure::do_klass(Klass* k) { do_klass_nv(k); }

inline void MetadataAwareOopsInGenClosure::do_class_loader_data(ClassLoaderData* cld) {
  assert(_klass_closure._oop_closure == this, "Must be");

  bool claim = true;  // Must claim the class loader data before processing.
  cld->oops_do(_klass_closure._oop_closure, &_klass_closure, claim);
}

#endif // SHARE_VM_GC_IMPLEMENTATION_CONCURRENTMARKSWEEP_CMSOOPCLOSURES_INLINE_HPP
