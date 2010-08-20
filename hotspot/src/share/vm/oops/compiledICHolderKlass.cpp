/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_compiledICHolderKlass.cpp.incl"

klassOop compiledICHolderKlass::create_klass(TRAPS) {
  compiledICHolderKlass o;
  KlassHandle h_this_klass(THREAD, Universe::klassKlassObj());
  KlassHandle k = base_create_klass(h_this_klass, header_size(), o.vtbl_value(), CHECK_NULL);
  // Make sure size calculation is right
  assert(k()->size() == align_object_size(header_size()), "wrong size for object");
  java_lang_Class::create_mirror(k, CHECK_NULL); // Allocate mirror
  return k();
}


compiledICHolderOop compiledICHolderKlass::allocate(TRAPS) {
  KlassHandle h_k(THREAD, as_klassOop());
  int size = compiledICHolderOopDesc::object_size();
  compiledICHolderOop c = (compiledICHolderOop)
    CollectedHeap::permanent_obj_allocate(h_k, size, CHECK_NULL);
  c->set_holder_method(NULL);
  c->set_holder_klass(NULL);
  return c;
}


int compiledICHolderKlass::oop_size(oop obj) const {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
  return compiledICHolderOop(obj)->object_size();
}

void compiledICHolderKlass::oop_follow_contents(oop obj) {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
  compiledICHolderOop c = compiledICHolderOop(obj);

  obj->follow_header();
  MarkSweep::mark_and_push(c->adr_holder_method());
  MarkSweep::mark_and_push(c->adr_holder_klass());
}

#ifndef SERIALGC
void compiledICHolderKlass::oop_follow_contents(ParCompactionManager* cm,
                                                oop obj) {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
  compiledICHolderOop c = compiledICHolderOop(obj);

  obj->follow_header(cm);
  PSParallelCompact::mark_and_push(cm, c->adr_holder_method());
  PSParallelCompact::mark_and_push(cm, c->adr_holder_klass());
}
#endif // SERIALGC


int compiledICHolderKlass::oop_oop_iterate(oop obj, OopClosure* blk) {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
  compiledICHolderOop c = compiledICHolderOop(obj);
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = c->object_size();

  obj->oop_iterate_header(blk);
  blk->do_oop(c->adr_holder_method());
  blk->do_oop(c->adr_holder_klass());
  return size;
}

int compiledICHolderKlass::oop_oop_iterate_m(oop obj, OopClosure* blk,
                                              MemRegion mr) {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
  compiledICHolderOop c = compiledICHolderOop(obj);
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = c->object_size();

  obj->oop_iterate_header(blk, mr);

  oop* adr;
  adr = c->adr_holder_method();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = c->adr_holder_klass();
  if (mr.contains(adr)) blk->do_oop(adr);
  return size;
}


int compiledICHolderKlass::oop_adjust_pointers(oop obj) {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
  compiledICHolderOop c = compiledICHolderOop(obj);
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = c->object_size();

  MarkSweep::adjust_pointer(c->adr_holder_method());
  MarkSweep::adjust_pointer(c->adr_holder_klass());
  obj->adjust_header();
  return size;
}

#ifndef SERIALGC
void compiledICHolderKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
}

int compiledICHolderKlass::oop_update_pointers(ParCompactionManager* cm,
                                               oop obj) {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
  compiledICHolderOop c = compiledICHolderOop(obj);

  PSParallelCompact::adjust_pointer(c->adr_holder_method());
  PSParallelCompact::adjust_pointer(c->adr_holder_klass());
  return c->object_size();
}

int compiledICHolderKlass::oop_update_pointers(ParCompactionManager* cm,
                                               oop obj,
                                               HeapWord* beg_addr,
                                               HeapWord* end_addr) {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
  compiledICHolderOop c = compiledICHolderOop(obj);

  oop* p;
  p = c->adr_holder_method();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  p = c->adr_holder_klass();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  return c->object_size();
}
#endif // SERIALGC

// Printing

void compiledICHolderKlass::oop_print_on(oop obj, outputStream* st) {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
  Klass::oop_print_on(obj, st);
  compiledICHolderOop c = compiledICHolderOop(obj);
  st->print(" - method: "); c->holder_method()->print_value_on(st); st->cr();
  st->print(" - klass:  "); c->holder_klass()->print_value_on(st); st->cr();
}

void compiledICHolderKlass::oop_print_value_on(oop obj, outputStream* st) {
  assert(obj->is_compiledICHolder(), "must be compiledICHolder");
  Klass::oop_print_value_on(obj, st);
}

const char* compiledICHolderKlass::internal_name() const {
  return "{compiledICHolder}";
}

// Verification

void compiledICHolderKlass::oop_verify_on(oop obj, outputStream* st) {
  Klass::oop_verify_on(obj, st);
  guarantee(obj->is_compiledICHolder(), "must be compiledICHolder");
  compiledICHolderOop c = compiledICHolderOop(obj);
  guarantee(c->is_perm(),             "should be in permspace");
  guarantee(c->holder_method()->is_perm(),   "should be in permspace");
  guarantee(c->holder_method()->is_method(), "should be method");
  guarantee(c->holder_klass()->is_perm(),    "should be in permspace");
  guarantee(c->holder_klass()->is_klass(),   "should be klass");
}
