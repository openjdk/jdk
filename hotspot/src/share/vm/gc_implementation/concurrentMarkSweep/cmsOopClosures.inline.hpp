/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef PRODUCT
void KlassRememberingOopClosure::check_remember_klasses() const {
  assert(_should_remember_klasses == must_remember_klasses(),
    "Should remember klasses in this context.");
}
#endif

void KlassRememberingOopClosure::remember_klass(Klass* k) {
  if (!_revisit_stack->push(oop(k))) {
    fatal("Revisit stack overflow in PushOrMarkClosure");
  }
  check_remember_klasses();
}

inline void PushOrMarkClosure::remember_mdo(DataLayout* v) {
  // TBD
}


void Par_KlassRememberingOopClosure::remember_klass(Klass* k) {
  if (!_revisit_stack->par_push(oop(k))) {
    fatal("Revisit stack overflow in Par_KlassRememberingOopClosure");
  }
  check_remember_klasses();
}

inline void Par_PushOrMarkClosure::remember_mdo(DataLayout* v) {
  // TBD
}

inline void PushOrMarkClosure::do_yield_check() {
  _parent->do_yield_check();
}

inline void Par_PushOrMarkClosure::do_yield_check() {
  _parent->do_yield_check();
}

#endif // SHARE_VM_GC_IMPLEMENTATION_CONCURRENTMARKSWEEP_CMSOOPCLOSURES_INLINE_HPP
