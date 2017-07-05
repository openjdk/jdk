/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_klassKlass.cpp.incl"

int klassKlass::oop_size(oop obj) const {
  assert (obj->is_klass(), "must be a klassOop");
  return klassOop(obj)->klass_part()->klass_oop_size();
}

klassOop klassKlass::create_klass(TRAPS) {
  KlassHandle h_this_klass;
  klassKlass o;
  // for bootstrapping, handles may not be available yet.
  klassOop k = base_create_klass_oop(h_this_klass, header_size(), o.vtbl_value(), CHECK_NULL);
  k->set_klass(k); // point to thyself
  // Do not try to allocate mirror, java.lang.Class not loaded at this point.
  // See Universe::fixup_mirrors()
  return k;
}

void klassKlass::oop_follow_contents(oop obj) {
  Klass* k = Klass::cast(klassOop(obj));
  // If we are alive it is valid to keep our superclass and subtype caches alive
  MarkSweep::mark_and_push(k->adr_super());
  for (juint i = 0; i < Klass::primary_super_limit(); i++)
    MarkSweep::mark_and_push(k->adr_primary_supers()+i);
  MarkSweep::mark_and_push(k->adr_secondary_super_cache());
  MarkSweep::mark_and_push(k->adr_secondary_supers());
  MarkSweep::mark_and_push(k->adr_java_mirror());
  MarkSweep::mark_and_push(k->adr_name());
  // We follow the subklass and sibling links at the end of the
  // marking phase, since otherwise following them will prevent
  // class unloading (all classes are transitively linked from
  // java.lang.Object).
  MarkSweep::revisit_weak_klass_link(k);
  obj->follow_header();
}

#ifndef SERIALGC
void klassKlass::oop_follow_contents(ParCompactionManager* cm,
                                     oop obj) {
  Klass* k = Klass::cast(klassOop(obj));
  // If we are alive it is valid to keep our superclass and subtype caches alive
  PSParallelCompact::mark_and_push(cm, k->adr_super());
  for (juint i = 0; i < Klass::primary_super_limit(); i++)
    PSParallelCompact::mark_and_push(cm, k->adr_primary_supers()+i);
  PSParallelCompact::mark_and_push(cm, k->adr_secondary_super_cache());
  PSParallelCompact::mark_and_push(cm, k->adr_secondary_supers());
  PSParallelCompact::mark_and_push(cm, k->adr_java_mirror());
  PSParallelCompact::mark_and_push(cm, k->adr_name());
  // We follow the subklass and sibling links at the end of the
  // marking phase, since otherwise following them will prevent
  // class unloading (all classes are transitively linked from
  // java.lang.Object).
  PSParallelCompact::revisit_weak_klass_link(cm, k);
  obj->follow_header(cm);
}
#endif // SERIALGC

int klassKlass::oop_oop_iterate(oop obj, OopClosure* blk) {
  // Get size before changing pointers
  int size = oop_size(obj);
  Klass* k = Klass::cast(klassOop(obj));
  blk->do_oop(k->adr_super());
  for (juint i = 0; i < Klass::primary_super_limit(); i++)
    blk->do_oop(k->adr_primary_supers()+i);
  blk->do_oop(k->adr_secondary_super_cache());
  blk->do_oop(k->adr_secondary_supers());
  blk->do_oop(k->adr_java_mirror());
  blk->do_oop(k->adr_name());
  // The following are in the perm gen and are treated
  // specially in a later phase of a perm gen collection; ...
  assert(oop(k)->is_perm(), "should be in perm");
  assert(oop(k->subklass())->is_perm_or_null(), "should be in perm");
  assert(oop(k->next_sibling())->is_perm_or_null(), "should be in perm");
  // ... don't scan them normally, but remember this klassKlass
  // for later (see, for instance, oop_follow_contents above
  // for what MarkSweep does with it.
  if (blk->should_remember_klasses()) {
    blk->remember_klass(k);
  }
  obj->oop_iterate_header(blk);
  return size;
}


int klassKlass::oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr) {
  // Get size before changing pointers
  int size = oop_size(obj);
  Klass* k = Klass::cast(klassOop(obj));
  oop* adr;
  adr = k->adr_super();
  if (mr.contains(adr)) blk->do_oop(adr);
  for (juint i = 0; i < Klass::primary_super_limit(); i++) {
    adr = k->adr_primary_supers()+i;
    if (mr.contains(adr)) blk->do_oop(adr);
  }
  adr = k->adr_secondary_super_cache();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = k->adr_secondary_supers();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = k->adr_java_mirror();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = k->adr_name();
  if (mr.contains(adr)) blk->do_oop(adr);
  // The following are "weak links" in the perm gen and are
  // treated specially in a later phase of a perm gen collection.
  assert(oop(k)->is_perm(), "should be in perm");
  assert(oop(k->adr_subklass())->is_perm(), "should be in perm");
  assert(oop(k->adr_next_sibling())->is_perm(), "should be in perm");
  if (blk->should_remember_klasses()
      && (mr.contains(k->adr_subklass())
          || mr.contains(k->adr_next_sibling()))) {
    blk->remember_klass(k);
  }
  obj->oop_iterate_header(blk, mr);
  return size;
}


int klassKlass::oop_adjust_pointers(oop obj) {
  // Get size before changing pointers
  int size = oop_size(obj);
  obj->adjust_header();

  Klass* k = Klass::cast(klassOop(obj));

  MarkSweep::adjust_pointer(k->adr_super());
  for (juint i = 0; i < Klass::primary_super_limit(); i++)
    MarkSweep::adjust_pointer(k->adr_primary_supers()+i);
  MarkSweep::adjust_pointer(k->adr_secondary_super_cache());
  MarkSweep::adjust_pointer(k->adr_secondary_supers());
  MarkSweep::adjust_pointer(k->adr_java_mirror());
  MarkSweep::adjust_pointer(k->adr_name());
  MarkSweep::adjust_pointer(k->adr_subklass());
  MarkSweep::adjust_pointer(k->adr_next_sibling());
  return size;
}

#ifndef SERIALGC
void klassKlass::oop_copy_contents(PSPromotionManager* pm, oop obj) {
}

void klassKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
}

int klassKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  Klass* k = Klass::cast(klassOop(obj));

  oop* const beg_oop = k->oop_block_beg();
  oop* const end_oop = k->oop_block_end();
  for (oop* cur_oop = beg_oop; cur_oop < end_oop; ++cur_oop) {
    PSParallelCompact::adjust_pointer(cur_oop);
  }

  return oop_size(obj);
}

int klassKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                    HeapWord* beg_addr, HeapWord* end_addr) {
  Klass* k = Klass::cast(klassOop(obj));

  oop* const beg_oop = MAX2((oop*)beg_addr, k->oop_block_beg());
  oop* const end_oop = MIN2((oop*)end_addr, k->oop_block_end());
  for (oop* cur_oop = beg_oop; cur_oop < end_oop; ++cur_oop) {
    PSParallelCompact::adjust_pointer(cur_oop);
  }

  return oop_size(obj);
}
#endif // SERIALGC


// Printing

void klassKlass::oop_print_on(oop obj, outputStream* st) {
  Klass::oop_print_on(obj, st);
}

void klassKlass::oop_print_value_on(oop obj, outputStream* st) {
  Klass::oop_print_value_on(obj, st);
}

const char* klassKlass::internal_name() const {
  return "{other class}";
}


// Verification

void klassKlass::oop_verify_on(oop obj, outputStream* st) {
  Klass::oop_verify_on(obj, st);
  guarantee(obj->is_perm(),                      "should be in permspace");
  guarantee(obj->is_klass(),                     "should be klass");

  Klass* k = Klass::cast(klassOop(obj));
  if (k->super() != NULL) {
    guarantee(k->super()->is_perm(),             "should be in permspace");
    guarantee(k->super()->is_klass(),            "should be klass");
  }
  klassOop ko = k->secondary_super_cache();
  if( ko != NULL ) {
    guarantee(ko->is_perm(),                     "should be in permspace");
    guarantee(ko->is_klass(),                    "should be klass");
  }
  for( uint i = 0; i < primary_super_limit(); i++ ) {
    oop ko = k->adr_primary_supers()[i]; // Cannot use normal accessor because it asserts
    if( ko != NULL ) {
      guarantee(ko->is_perm(),                   "should be in permspace");
      guarantee(ko->is_klass(),                  "should be klass");
    }
  }

  if (k->java_mirror() != NULL || (k->oop_is_instance() && instanceKlass::cast(klassOop(obj))->is_loaded())) {
    guarantee(k->java_mirror() != NULL,          "should be allocated");
    guarantee(k->java_mirror()->is_perm(),       "should be in permspace");
    guarantee(k->java_mirror()->is_instance(),   "should be instance");
  }
  if (k->name() != NULL) {
    guarantee(Universe::heap()->is_in_permanent(k->name()),
              "should be in permspace");
    guarantee(k->name()->is_symbol(), "should be symbol");
  }
}
