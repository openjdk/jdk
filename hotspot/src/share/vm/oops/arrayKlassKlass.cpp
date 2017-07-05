/*
 * Copyright 1997-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_arrayKlassKlass.cpp.incl"


klassOop arrayKlassKlass::create_klass(TRAPS) {
  arrayKlassKlass o;
  KlassHandle h_this_klass(THREAD, Universe::klassKlassObj());
  KlassHandle k = base_create_klass(h_this_klass, header_size(), o.vtbl_value(), CHECK_NULL);
  // Make sure size calculation is right
  assert(k()->size() == align_object_size(header_size()), "wrong size for object");
  java_lang_Class::create_mirror(k, CHECK_NULL); // Allocate mirror, make links
  return k();
}

bool arrayKlassKlass::oop_is_parsable(oop obj) const {
  assert(obj->is_klass(), "must be klass");
  arrayKlass* ak = arrayKlass::cast(klassOop(obj));
  return (!ak->null_vtbl()) && ak->object_is_parsable();
}

void arrayKlassKlass::oop_follow_contents(oop obj) {
  assert(obj->is_klass(), "must be klass");
  arrayKlass* ak = arrayKlass::cast(klassOop(obj));
  MarkSweep::mark_and_push(ak->adr_component_mirror());
  MarkSweep::mark_and_push(ak->adr_lower_dimension());
  MarkSweep::mark_and_push(ak->adr_higher_dimension());
  {
    HandleMark hm;
    ak->vtable()->oop_follow_contents();
  }
  klassKlass::oop_follow_contents(obj);
}

#ifndef SERIALGC
void arrayKlassKlass::oop_follow_contents(ParCompactionManager* cm,
                                          oop obj) {
  assert(obj->is_klass(), "must be klass");
  arrayKlass* ak = arrayKlass::cast(klassOop(obj));
  PSParallelCompact::mark_and_push(cm, ak->adr_component_mirror());
  PSParallelCompact::mark_and_push(cm, ak->adr_lower_dimension());
  PSParallelCompact::mark_and_push(cm, ak->adr_higher_dimension());
  {
    HandleMark hm;
    ak->vtable()->oop_follow_contents(cm);
  }
  klassKlass::oop_follow_contents(cm, obj);
}
#endif // SERIALGC


int arrayKlassKlass::oop_adjust_pointers(oop obj) {
  assert(obj->is_klass(), "must be klass");
  arrayKlass* ak = arrayKlass::cast(klassOop(obj));
  MarkSweep::adjust_pointer(ak->adr_component_mirror());
  MarkSweep::adjust_pointer(ak->adr_lower_dimension());
  MarkSweep::adjust_pointer(ak->adr_higher_dimension());
  {
    HandleMark hm;
    ak->vtable()->oop_adjust_pointers();
  }
  return klassKlass::oop_adjust_pointers(obj);
}


int arrayKlassKlass::oop_oop_iterate(oop obj, OopClosure* blk) {
  assert(obj->is_klass(), "must be klass");
  arrayKlass* ak = arrayKlass::cast(klassOop(obj));
  blk->do_oop(ak->adr_component_mirror());
  blk->do_oop(ak->adr_lower_dimension());
  blk->do_oop(ak->adr_higher_dimension());
  ak->vtable()->oop_oop_iterate(blk);
  return klassKlass::oop_oop_iterate(obj, blk);
}


int arrayKlassKlass::oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr) {
  assert(obj->is_klass(), "must be klass");
  arrayKlass* ak = arrayKlass::cast(klassOop(obj));
  blk->do_oop(ak->adr_component_mirror());
  blk->do_oop(ak->adr_lower_dimension());
  blk->do_oop(ak->adr_higher_dimension());
  ak->vtable()->oop_oop_iterate_m(blk, mr);
  return klassKlass::oop_oop_iterate_m(obj, blk, mr);
}

#ifndef SERIALGC
void arrayKlassKlass::oop_copy_contents(PSPromotionManager* pm, oop obj) {
  assert(obj->blueprint()->oop_is_arrayKlass(),"must be an array klass");
}

void arrayKlassKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  assert(obj->blueprint()->oop_is_arrayKlass(),"must be an array klass");
}

int arrayKlassKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  assert(obj->is_klass(), "must be klass");
  arrayKlass* ak = arrayKlass::cast(klassOop(obj));
  PSParallelCompact::adjust_pointer(ak->adr_component_mirror());
  PSParallelCompact::adjust_pointer(ak->adr_lower_dimension());
  PSParallelCompact::adjust_pointer(ak->adr_higher_dimension());
  {
    HandleMark hm;
    ak->vtable()->oop_update_pointers(cm);
  }
  return klassKlass::oop_update_pointers(cm, obj);
}

int
arrayKlassKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                     HeapWord* beg_addr, HeapWord* end_addr) {
  assert(obj->is_klass(), "must be klass");
  arrayKlass* ak = arrayKlass::cast(klassOop(obj));

  oop* p;
  p = ak->adr_component_mirror();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  p = ak->adr_lower_dimension();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  p = ak->adr_higher_dimension();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);

  {
    HandleMark hm;
    ak->vtable()->oop_update_pointers(cm, beg_addr, end_addr);
  }
  return klassKlass::oop_update_pointers(cm, obj, beg_addr, end_addr);
}
#endif // SERIALGC

#ifndef PRODUCT

// Printing

void arrayKlassKlass::oop_print_on(oop obj, outputStream* st) {
  assert(obj->is_klass(), "must be klass");
  klassKlass::oop_print_on(obj, st);
}
#endif //PRODUCT

void arrayKlassKlass::oop_print_value_on(oop obj, outputStream* st) {
  assert(obj->is_klass(), "must be klass");
  arrayKlass* ak = arrayKlass::cast(klassOop(obj));
  for(int index = 0; index < ak->dimension(); index++) {
    st->print("[]");
  }
}


const char* arrayKlassKlass::internal_name() const {
  return "{array class}";
}

void arrayKlassKlass::oop_verify_on(oop obj, outputStream* st) {
  klassKlass::oop_verify_on(obj, st);

  arrayKlass* ak = arrayKlass::cast(klassOop(obj));
  if (!obj->partially_loaded()) {
    if (ak->component_mirror() != NULL)
      guarantee(ak->component_mirror()->klass(), "should have a class");
    if (ak->lower_dimension() != NULL)
      guarantee(ak->lower_dimension()->klass(), "should have a class");
    if (ak->higher_dimension() != NULL)
      guarantee(ak->higher_dimension()->klass(), "should have a class");
  }
}
