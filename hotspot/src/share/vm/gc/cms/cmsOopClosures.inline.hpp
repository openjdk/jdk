/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_CMS_CMSOOPCLOSURES_INLINE_HPP
#define SHARE_VM_GC_CMS_CMSOOPCLOSURES_INLINE_HPP

#include "gc/cms/cmsOopClosures.hpp"
#include "gc/cms/concurrentMarkSweepGeneration.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "oops/oop.inline.hpp"

// MetadataAwareOopClosure and MetadataAwareOopsInGenClosure are duplicated,
// until we get rid of OopsInGenClosure.

inline void MetadataAwareOopsInGenClosure::do_klass_nv(Klass* k) {
  ClassLoaderData* cld = k->class_loader_data();
  do_cld_nv(cld);
}
inline void MetadataAwareOopsInGenClosure::do_klass(Klass* k) { do_klass_nv(k); }

inline void MetadataAwareOopsInGenClosure::do_cld_nv(ClassLoaderData* cld) {
  assert(_klass_closure._oop_closure == this, "Must be");

  bool claim = true;  // Must claim the class loader data before processing.
  cld->oops_do(_klass_closure._oop_closure, &_klass_closure, claim);
}

// Decode the oop and call do_oop on it.
#define DO_OOP_WORK_IMPL(cls)                                 \
  template <class T> void cls::do_oop_work(T* p) {            \
    T heap_oop = oopDesc::load_heap_oop(p);                   \
    if (!oopDesc::is_null(heap_oop)) {                        \
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);  \
      do_oop(obj);                                            \
    }                                                         \
  }

#define DO_OOP_WORK_NV_IMPL(cls)                              \
  DO_OOP_WORK_IMPL(cls)                                       \
  void cls::do_oop_nv(oop* p)       { cls::do_oop_work(p); }  \
  void cls::do_oop_nv(narrowOop* p) { cls::do_oop_work(p); }

DO_OOP_WORK_IMPL(MarkRefsIntoClosure)
DO_OOP_WORK_IMPL(ParMarkRefsIntoClosure)
DO_OOP_WORK_IMPL(MarkRefsIntoVerifyClosure)
DO_OOP_WORK_NV_IMPL(PushAndMarkClosure)
DO_OOP_WORK_NV_IMPL(ParPushAndMarkClosure)
DO_OOP_WORK_NV_IMPL(MarkRefsIntoAndScanClosure)
DO_OOP_WORK_NV_IMPL(ParMarkRefsIntoAndScanClosure)

// Trim our work_queue so its length is below max at return
inline void ParMarkRefsIntoAndScanClosure::trim_queue(uint max) {
  while (_work_queue->size() > max) {
    oop newOop;
    if (_work_queue->pop_local(newOop)) {
      assert(newOop->is_oop(), "Expected an oop");
      assert(_bit_map->isMarked((HeapWord*)newOop),
             "only grey objects on this stack");
      // iterate over the oops in this oop, marking and pushing
      // the ones in CMS heap (i.e. in _span).
      newOop->oop_iterate(&_parPushAndMarkClosure);
    }
  }
}

DO_OOP_WORK_NV_IMPL(PushOrMarkClosure)
DO_OOP_WORK_NV_IMPL(ParPushOrMarkClosure)
DO_OOP_WORK_NV_IMPL(CMSKeepAliveClosure)
DO_OOP_WORK_NV_IMPL(CMSInnerParMarkAndPushClosure)
DO_OOP_WORK_IMPL(CMSParKeepAliveClosure)

#endif // SHARE_VM_GC_CMS_CMSOOPCLOSURES_INLINE_HPP
