/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_cpCacheKlass.cpp.incl"


int constantPoolCacheKlass::oop_size(oop obj) const {
  assert(obj->is_constantPoolCache(), "must be constantPool");
  return constantPoolCacheOop(obj)->object_size();
}


constantPoolCacheOop constantPoolCacheKlass::allocate(int length, TRAPS) {
  // allocate memory
  int size = constantPoolCacheOopDesc::object_size(length);
  KlassHandle klass (THREAD, as_klassOop());
  constantPoolCacheOop cache = (constantPoolCacheOop)
    CollectedHeap::permanent_obj_allocate(klass, size, CHECK_NULL);
  cache->set_length(length);
  cache->set_constant_pool(NULL);
  return cache;
}

klassOop constantPoolCacheKlass::create_klass(TRAPS) {
  constantPoolCacheKlass o;
  KlassHandle h_this_klass(THREAD, Universe::klassKlassObj());
  KlassHandle k = base_create_klass(h_this_klass, header_size(), o.vtbl_value(), CHECK_NULL);
  // Make sure size calculation is right
  assert(k()->size() == align_object_size(header_size()), "wrong size for object");
  java_lang_Class::create_mirror(k, CHECK_NULL); // Allocate mirror
  return k();
}


void constantPoolCacheKlass::oop_follow_contents(oop obj) {
  assert(obj->is_constantPoolCache(), "obj must be constant pool cache");
  constantPoolCacheOop cache = (constantPoolCacheOop)obj;
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constantPoolCacheKlassObj never moves.
  // gc of constant pool cache instance variables
  MarkSweep::mark_and_push((oop*)cache->constant_pool_addr());
  // gc of constant pool cache entries
  int i = cache->length();
  while (i-- > 0) cache->entry_at(i)->follow_contents();
}

#ifndef SERIALGC
void constantPoolCacheKlass::oop_follow_contents(ParCompactionManager* cm,
                                                 oop obj) {
  assert(obj->is_constantPoolCache(), "obj must be constant pool cache");
  constantPoolCacheOop cache = (constantPoolCacheOop)obj;
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constantPoolCacheKlassObj never moves.
  // gc of constant pool cache instance variables
  PSParallelCompact::mark_and_push(cm, (oop*)cache->constant_pool_addr());
  // gc of constant pool cache entries
  int i = cache->length();
  while (i-- > 0) cache->entry_at(i)->follow_contents(cm);
}
#endif // SERIALGC


int constantPoolCacheKlass::oop_oop_iterate(oop obj, OopClosure* blk) {
  assert(obj->is_constantPoolCache(), "obj must be constant pool cache");
  constantPoolCacheOop cache = (constantPoolCacheOop)obj;
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = cache->object_size();
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constantPoolCacheKlassObj never moves.
  // iteration over constant pool cache instance variables
  blk->do_oop((oop*)cache->constant_pool_addr());
  // iteration over constant pool cache entries
  for (int i = 0; i < cache->length(); i++) cache->entry_at(i)->oop_iterate(blk);
  return size;
}


int constantPoolCacheKlass::oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr) {
  assert(obj->is_constantPoolCache(), "obj must be constant pool cache");
  constantPoolCacheOop cache = (constantPoolCacheOop)obj;
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = cache->object_size();
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constantPoolCacheKlassObj never moves.
  // iteration over constant pool cache instance variables
  oop* addr = (oop*)cache->constant_pool_addr();
  if (mr.contains(addr)) blk->do_oop(addr);
  // iteration over constant pool cache entries
  for (int i = 0; i < cache->length(); i++) cache->entry_at(i)->oop_iterate_m(blk, mr);
  return size;
}


int constantPoolCacheKlass::oop_adjust_pointers(oop obj) {
  assert(obj->is_constantPoolCache(), "obj must be constant pool cache");
  constantPoolCacheOop cache = (constantPoolCacheOop)obj;
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = cache->object_size();
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constantPoolCacheKlassObj never moves.
  // Iteration over constant pool cache instance variables
  MarkSweep::adjust_pointer((oop*)cache->constant_pool_addr());
  // iteration over constant pool cache entries
  for (int i = 0; i < cache->length(); i++)
    cache->entry_at(i)->adjust_pointers();
  return size;
}

#ifndef SERIALGC
void constantPoolCacheKlass::oop_copy_contents(PSPromotionManager* pm,
                                               oop obj) {
  assert(obj->is_constantPoolCache(), "should be constant pool");
}

void constantPoolCacheKlass::oop_push_contents(PSPromotionManager* pm,
                                               oop obj) {
  assert(obj->is_constantPoolCache(), "should be constant pool");
}

int
constantPoolCacheKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  assert(obj->is_constantPoolCache(), "obj must be constant pool cache");
  constantPoolCacheOop cache = (constantPoolCacheOop)obj;

  // Iteration over constant pool cache instance variables
  PSParallelCompact::adjust_pointer((oop*)cache->constant_pool_addr());

  // iteration over constant pool cache entries
  for (int i = 0; i < cache->length(); ++i) {
    cache->entry_at(i)->update_pointers();
  }

  return cache->object_size();
}

int
constantPoolCacheKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                            HeapWord* beg_addr,
                                            HeapWord* end_addr) {
  assert(obj->is_constantPoolCache(), "obj must be constant pool cache");
  constantPoolCacheOop cache = (constantPoolCacheOop)obj;

  // Iteration over constant pool cache instance variables
  oop* p;
  p = (oop*)cache->constant_pool_addr();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);

  // Iteration over constant pool cache entries
  for (int i = 0; i < cache->length(); ++i) {
    cache->entry_at(i)->update_pointers(beg_addr, end_addr);
  }
  return cache->object_size();
}
#endif // SERIALGC

#ifndef PRODUCT

void constantPoolCacheKlass::oop_print_on(oop obj, outputStream* st) {
  assert(obj->is_constantPoolCache(), "obj must be constant pool cache");
  constantPoolCacheOop cache = (constantPoolCacheOop)obj;
  // super print
  Klass::oop_print_on(obj, st);
  // print constant pool cache entries
  for (int i = 0; i < cache->length(); i++) cache->entry_at(i)->print(st, i);
}

#endif

void constantPoolCacheKlass::oop_verify_on(oop obj, outputStream* st) {
  guarantee(obj->is_constantPoolCache(), "obj must be constant pool cache");
  constantPoolCacheOop cache = (constantPoolCacheOop)obj;
  // super verify
  Klass::oop_verify_on(obj, st);
  // print constant pool cache entries
  for (int i = 0; i < cache->length(); i++) cache->entry_at(i)->verify(st);
}


const char* constantPoolCacheKlass::internal_name() const {
  return "{constant pool cache}";
}
