/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/markSweep.inline.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/gcLocker.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/klassOop.hpp"
#include "oops/methodDataKlass.hpp"
#include "oops/methodDataOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.inline2.hpp"
#include "runtime/handles.inline.hpp"
#ifndef SERIALGC
#include "gc_implementation/parallelScavenge/psScavenge.inline.hpp"
#include "oops/oop.pcgc.inline.hpp"
#endif

klassOop methodDataKlass::create_klass(TRAPS) {
  methodDataKlass o;
  KlassHandle h_this_klass(THREAD, Universe::klassKlassObj());
  KlassHandle k = base_create_klass(h_this_klass, header_size(),
                                    o.vtbl_value(), CHECK_NULL);
  // Make sure size calculation is right
  assert(k()->size() == align_object_size(header_size()),
         "wrong size for object");
  return k();
}


int methodDataKlass::oop_size(oop obj) const {
  assert(obj->is_methodData(), "must be method data oop");
  return methodDataOop(obj)->object_size();
}


bool methodDataKlass::oop_is_parsable(oop obj) const {
  assert(obj->is_methodData(), "must be method data oop");
  return methodDataOop(obj)->object_is_parsable();
}


methodDataOop methodDataKlass::allocate(methodHandle method, TRAPS) {
  int size = methodDataOopDesc::compute_allocation_size_in_words(method);
  KlassHandle h_k(THREAD, as_klassOop());
  methodDataOop mdo =
    (methodDataOop)CollectedHeap::permanent_obj_allocate(h_k, size, CHECK_NULL);
  assert(!mdo->is_parsable(), "not expecting parsability yet.");
  No_Safepoint_Verifier no_safepoint;  // init function atomic wrt GC
  mdo->initialize(method);

  assert(mdo->is_parsable(), "should be parsable here.");
  assert(size == mdo->object_size(), "wrong size for methodDataOop");
  return mdo;
}


void methodDataKlass::oop_follow_contents(oop obj) {
  assert (obj->is_methodData(), "object must be method data");
  methodDataOop m = methodDataOop(obj);

  obj->follow_header();
  MarkSweep::mark_and_push(m->adr_method());
  ResourceMark rm;
  for (ProfileData* data = m->first_data();
       m->is_valid(data);
       data = m->next_data(data)) {
    data->follow_contents();
  }
}

#ifndef SERIALGC
void methodDataKlass::oop_follow_contents(ParCompactionManager* cm,
                                          oop obj) {
  assert (obj->is_methodData(), "object must be method data");
  methodDataOop m = methodDataOop(obj);

  obj->follow_header(cm);
  PSParallelCompact::mark_and_push(cm, m->adr_method());
  ResourceMark rm;
  for (ProfileData* data = m->first_data();
       m->is_valid(data);
       data = m->next_data(data)) {
    data->follow_contents(cm);
  }
}
#endif // SERIALGC


int methodDataKlass::oop_oop_iterate(oop obj, OopClosure* blk) {
  assert (obj->is_methodData(), "object must be method data");
  methodDataOop m = methodDataOop(obj);
  // Get size before changing pointers
  // Don't call size() or oop_size() since that is a virtual call.
  int size = m->object_size();

  obj->oop_iterate_header(blk);
  blk->do_oop(m->adr_method());
  ResourceMark rm;
  for (ProfileData* data = m->first_data();
       m->is_valid(data);
       data = m->next_data(data)) {
    data->oop_iterate(blk);
  }
  return size;
}

int methodDataKlass::oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr) {
  assert (obj->is_methodData(), "object must be method data");
  methodDataOop m = methodDataOop(obj);
  // Get size before changing pointers
  // Don't call size() or oop_size() since that is a virtual call.
  int size = m->object_size();

  obj->oop_iterate_header(blk, mr);
  oop* adr = m->adr_method();
  if (mr.contains(adr)) {
    blk->do_oop(m->adr_method());
  }
  ResourceMark rm;
  for (ProfileData* data = m->first_data();
       m->is_valid(data);
       data = m->next_data(data)) {
    data->oop_iterate_m(blk, mr);
  }
  return size;
}

int methodDataKlass::oop_adjust_pointers(oop obj) {
  assert(obj->is_methodData(), "should be method data");
  methodDataOop m = methodDataOop(obj);
  // Get size before changing pointers
  // Don't call size() or oop_size() since that is a virtual call.
  int size = m->object_size();

  obj->adjust_header();
  MarkSweep::adjust_pointer(m->adr_method());
  ResourceMark rm;
  ProfileData* data;
  for (data = m->first_data(); m->is_valid(data); data = m->next_data(data)) {
    data->adjust_pointers();
  }
  return size;
}


#ifndef SERIALGC
void methodDataKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  assert (obj->is_methodData(), "object must be method data");
  methodDataOop m = methodDataOop(obj);
  // This should never point into the young gen.
  assert(!PSScavenge::should_scavenge(m->adr_method()), "Sanity");
}

int methodDataKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  assert(obj->is_methodData(), "should be method data");
  methodDataOop m = methodDataOop(obj);

  PSParallelCompact::adjust_pointer(m->adr_method());

  ResourceMark rm;
  ProfileData* data;
  for (data = m->first_data(); m->is_valid(data); data = m->next_data(data)) {
    data->update_pointers();
  }
  return m->object_size();
}

int
methodDataKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                     HeapWord* beg_addr, HeapWord* end_addr) {
  assert(obj->is_methodData(), "should be method data");

  oop* p;
  methodDataOop m = methodDataOop(obj);

  p = m->adr_method();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);

  ResourceMark rm;
  ProfileData* data;
  for (data = m->first_data(); m->is_valid(data); data = m->next_data(data)) {
    data->update_pointers(beg_addr, end_addr);
  }
  return m->object_size();
}
#endif // SERIALGC

#ifndef PRODUCT

// Printing
void methodDataKlass::oop_print_on(oop obj, outputStream* st) {
  assert(obj->is_methodData(), "should be method data");
  methodDataOop m = methodDataOop(obj);
  st->print("method data for ");
  m->method()->print_value_on(st);
  st->cr();
  m->print_data_on(st);
}

#endif //PRODUCT

void methodDataKlass::oop_print_value_on(oop obj, outputStream* st) {
  assert(obj->is_methodData(), "should be method data");
  methodDataOop m = methodDataOop(obj);
  st->print("method data for ");
  m->method()->print_value_on(st);
}

const char* methodDataKlass::internal_name() const {
  return "{method data}";
}


// Verification
void methodDataKlass::oop_verify_on(oop obj, outputStream* st) {
  Klass::oop_verify_on(obj, st);
  guarantee(obj->is_methodData(), "object must be method data");
  methodDataOop m = methodDataOop(obj);
  guarantee(m->is_perm(), "should be in permspace");
  m->verify_data_on(st);
}
