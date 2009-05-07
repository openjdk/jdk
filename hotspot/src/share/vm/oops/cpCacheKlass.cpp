/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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


constantPoolCacheOop constantPoolCacheKlass::allocate(int length,
                                                      bool is_conc_safe,
                                                      TRAPS) {
  // allocate memory
  int size = constantPoolCacheOopDesc::object_size(length);

  KlassHandle klass (THREAD, as_klassOop());

  // This is the original code.  The code from permanent_obj_allocate()
  // was in-lined to allow the setting of is_conc_safe before the klass
  // is installed.
  // constantPoolCacheOop cache = (constantPoolCacheOop)
  //   CollectedHeap::permanent_obj_allocate(klass, size, CHECK_NULL);

  oop obj = CollectedHeap::permanent_obj_allocate_no_klass_install(klass, size, CHECK_NULL);
  constantPoolCacheOop cache = (constantPoolCacheOop) obj;
  cache->set_is_conc_safe(is_conc_safe);
  // The store to is_conc_safe must be visible before the klass
  // is set.  This should be done safely because _is_conc_safe has
  // been declared volatile.  If there are any problems, consider adding
  // OrderAccess::storestore();
  CollectedHeap::post_allocation_install_obj_klass(klass, obj, size);
  NOT_PRODUCT(Universe::heap()->check_for_bad_heap_word_value((HeapWord*) obj,
                                                              size));

  // The length field affects the size of the object.  The allocation
  // above allocates the correct size (see calculation of "size") but
  // the size() method of the constant pool cache oop will not reflect
  // that size until the correct length is set.
  cache->set_length(length);

  // The store of the length must be visible before is_conc_safe is
  // set to a safe state.
  // This should be done safely because _is_conc_safe has
  // been declared volatile.  If there are any problems, consider adding
  // OrderAccess::storestore();
  cache->set_is_conc_safe(methodOopDesc::IsSafeConc);
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

bool constantPoolCacheKlass::oop_is_conc_safe(oop obj) const {
  assert(obj->is_constantPoolCache(), "should be constant pool");
  return constantPoolCacheOop(obj)->is_conc_safe();
}

#ifndef SERIALGC
void constantPoolCacheKlass::oop_copy_contents(PSPromotionManager* pm,
                                               oop obj) {
  assert(obj->is_constantPoolCache(), "should be constant pool");
  if (EnableInvokeDynamic) {
    constantPoolCacheOop cache = (constantPoolCacheOop)obj;
    // during a scavenge, it is safe to inspect my pool, since it is perm
    constantPoolOop pool = cache->constant_pool();
    assert(pool->is_constantPool(), "should be constant pool");
    if (pool->has_invokedynamic()) {
      for (int i = 0; i < cache->length(); i++) {
        ConstantPoolCacheEntry* e = cache->entry_at(i);
        oop* p = (oop*)&e->_f1;
        if (e->is_secondary_entry()) {
          if (PSScavenge::should_scavenge(p))
            pm->claim_or_forward_breadth(p);
          assert(!(e->is_vfinal() && PSScavenge::should_scavenge((oop*)&e->_f2)),
                 "no live oops here");
        }
      }
    }
  }
}

void constantPoolCacheKlass::oop_push_contents(PSPromotionManager* pm,
                                               oop obj) {
  assert(obj->is_constantPoolCache(), "should be constant pool");
  if (EnableInvokeDynamic) {
    constantPoolCacheOop cache = (constantPoolCacheOop)obj;
    // during a scavenge, it is safe to inspect my pool, since it is perm
    constantPoolOop pool = cache->constant_pool();
    assert(pool->is_constantPool(), "should be constant pool");
    if (pool->has_invokedynamic()) {
      for (int i = 0; i < cache->length(); i++) {
        ConstantPoolCacheEntry* e = cache->entry_at(i);
        oop* p = (oop*)&e->_f1;
        if (e->is_secondary_entry()) {
          if (PSScavenge::should_scavenge(p))
            pm->claim_or_forward_depth(p);
          assert(!(e->is_vfinal() && PSScavenge::should_scavenge((oop*)&e->_f2)),
                 "no live oops here");
        }
      }
    }
  }
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
