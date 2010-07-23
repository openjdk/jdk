/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_constantPoolKlass.cpp.incl"

constantPoolOop constantPoolKlass::allocate(int length, bool is_conc_safe, TRAPS) {
  int size = constantPoolOopDesc::object_size(length);
  KlassHandle klass (THREAD, as_klassOop());
  constantPoolOop c =
    (constantPoolOop)CollectedHeap::permanent_obj_allocate(klass, size, CHECK_NULL);

  c->set_length(length);
  c->set_tags(NULL);
  c->set_cache(NULL);
  c->set_pool_holder(NULL);
  c->set_flags(0);
  // only set to non-zero if constant pool is merged by RedefineClasses
  c->set_orig_length(0);
  // if constant pool may change during RedefineClasses, it is created
  // unsafe for GC concurrent processing.
  c->set_is_conc_safe(is_conc_safe);
  // all fields are initialized; needed for GC

  // initialize tag array
  // Note: cannot introduce constant pool handle before since it is not
  //       completely initialized (no class) -> would cause assertion failure
  constantPoolHandle pool (THREAD, c);
  typeArrayOop t_oop = oopFactory::new_permanent_byteArray(length, CHECK_NULL);
  typeArrayHandle tags (THREAD, t_oop);
  for (int index = 0; index < length; index++) {
    tags()->byte_at_put(index, JVM_CONSTANT_Invalid);
  }
  pool->set_tags(tags());

  return pool();
}

klassOop constantPoolKlass::create_klass(TRAPS) {
  constantPoolKlass o;
  KlassHandle h_this_klass(THREAD, Universe::klassKlassObj());
  KlassHandle k = base_create_klass(h_this_klass, header_size(), o.vtbl_value(), CHECK_NULL);
  // Make sure size calculation is right
  assert(k()->size() == align_object_size(header_size()), "wrong size for object");
  java_lang_Class::create_mirror(k, CHECK_NULL); // Allocate mirror
  return k();
}

int constantPoolKlass::oop_size(oop obj) const {
  assert(obj->is_constantPool(), "must be constantPool");
  return constantPoolOop(obj)->object_size();
}


void constantPoolKlass::oop_follow_contents(oop obj) {
  assert (obj->is_constantPool(), "obj must be constant pool");
  constantPoolOop cp = (constantPoolOop) obj;
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constantPoolKlassObj never moves.

  // If the tags array is null we are in the middle of allocating this constant pool
  if (cp->tags() != NULL) {
    // gc of constant pool contents
    oop* base = (oop*)cp->base();
    for (int i = 0; i < cp->length(); i++) {
      if (cp->is_pointer_entry(i)) {
        if (*base != NULL) MarkSweep::mark_and_push(base);
      }
      base++;
    }
    // gc of constant pool instance variables
    MarkSweep::mark_and_push(cp->tags_addr());
    MarkSweep::mark_and_push(cp->cache_addr());
    MarkSweep::mark_and_push(cp->pool_holder_addr());
  }
}

#ifndef SERIALGC
void constantPoolKlass::oop_follow_contents(ParCompactionManager* cm,
                                            oop obj) {
  assert (obj->is_constantPool(), "obj must be constant pool");
  constantPoolOop cp = (constantPoolOop) obj;
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constantPoolKlassObj never moves.

  // If the tags array is null we are in the middle of allocating this constant
  // pool.
  if (cp->tags() != NULL) {
    // gc of constant pool contents
    oop* base = (oop*)cp->base();
    for (int i = 0; i < cp->length(); i++) {
      if (cp->is_pointer_entry(i)) {
        if (*base != NULL) PSParallelCompact::mark_and_push(cm, base);
      }
      base++;
    }
    // gc of constant pool instance variables
    PSParallelCompact::mark_and_push(cm, cp->tags_addr());
    PSParallelCompact::mark_and_push(cm, cp->cache_addr());
    PSParallelCompact::mark_and_push(cm, cp->pool_holder_addr());
  }
}
#endif // SERIALGC


int constantPoolKlass::oop_adjust_pointers(oop obj) {
  assert (obj->is_constantPool(), "obj must be constant pool");
  constantPoolOop cp = (constantPoolOop) obj;
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = cp->object_size();
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constantPoolKlassObj never moves.

  // If the tags array is null we are in the middle of allocating this constant
  // pool.
  if (cp->tags() != NULL) {
    oop* base = (oop*)cp->base();
    for (int i = 0; i< cp->length();  i++) {
      if (cp->is_pointer_entry(i)) {
        MarkSweep::adjust_pointer(base);
      }
      base++;
    }
  }
  MarkSweep::adjust_pointer(cp->tags_addr());
  MarkSweep::adjust_pointer(cp->cache_addr());
  MarkSweep::adjust_pointer(cp->pool_holder_addr());
  return size;
}


int constantPoolKlass::oop_oop_iterate(oop obj, OopClosure* blk) {
  assert (obj->is_constantPool(), "obj must be constant pool");
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constantPoolKlassObj never moves.
  constantPoolOop cp = (constantPoolOop) obj;
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = cp->object_size();

  // If the tags array is null we are in the middle of allocating this constant
  // pool.
  if (cp->tags() != NULL) {
    oop* base = (oop*)cp->base();
    for (int i = 0; i < cp->length(); i++) {
      if (cp->is_pointer_entry(i)) {
        blk->do_oop(base);
      }
      base++;
    }
  }
  blk->do_oop(cp->tags_addr());
  blk->do_oop(cp->cache_addr());
  blk->do_oop(cp->pool_holder_addr());
  return size;
}


int constantPoolKlass::oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr) {
  assert (obj->is_constantPool(), "obj must be constant pool");
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constantPoolKlassObj never moves.
  constantPoolOop cp = (constantPoolOop) obj;
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = cp->object_size();

  // If the tags array is null we are in the middle of allocating this constant
  // pool.
  if (cp->tags() != NULL) {
    oop* base = (oop*)cp->base();
    for (int i = 0; i < cp->length(); i++) {
      if (mr.contains(base)) {
        if (cp->is_pointer_entry(i)) {
          blk->do_oop(base);
        }
      }
      base++;
    }
  }
  oop* addr;
  addr = cp->tags_addr();
  blk->do_oop(addr);
  addr = cp->cache_addr();
  blk->do_oop(addr);
  addr = cp->pool_holder_addr();
  blk->do_oop(addr);
  return size;
}

bool constantPoolKlass::oop_is_conc_safe(oop obj) const {
  assert(obj->is_constantPool(), "must be constantPool");
  return constantPoolOop(obj)->is_conc_safe();
}

#ifndef SERIALGC
int constantPoolKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  assert (obj->is_constantPool(), "obj must be constant pool");
  constantPoolOop cp = (constantPoolOop) obj;

  // If the tags array is null we are in the middle of allocating this constant
  // pool.
  if (cp->tags() != NULL) {
    oop* base = (oop*)cp->base();
    for (int i = 0; i < cp->length(); ++i, ++base) {
      if (cp->is_pointer_entry(i)) {
        PSParallelCompact::adjust_pointer(base);
      }
    }
  }
  PSParallelCompact::adjust_pointer(cp->tags_addr());
  PSParallelCompact::adjust_pointer(cp->cache_addr());
  PSParallelCompact::adjust_pointer(cp->pool_holder_addr());
  return cp->object_size();
}

int
constantPoolKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                       HeapWord* beg_addr, HeapWord* end_addr) {
  assert (obj->is_constantPool(), "obj must be constant pool");
  constantPoolOop cp = (constantPoolOop) obj;

  // If the tags array is null we are in the middle of allocating this constant
  // pool.
  if (cp->tags() != NULL) {
    oop* base = (oop*)cp->base();
    oop* const beg_oop = MAX2((oop*)beg_addr, base);
    oop* const end_oop = MIN2((oop*)end_addr, base + cp->length());
    const size_t beg_idx = pointer_delta(beg_oop, base, sizeof(oop*));
    const size_t end_idx = pointer_delta(end_oop, base, sizeof(oop*));
    for (size_t cur_idx = beg_idx; cur_idx < end_idx; ++cur_idx, ++base) {
      if (cp->is_pointer_entry(int(cur_idx))) {
        PSParallelCompact::adjust_pointer(base);
      }
    }
  }

  oop* p;
  p = cp->tags_addr();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  p = cp->cache_addr();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  p = cp->pool_holder_addr();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);

  return cp->object_size();
}

void constantPoolKlass::oop_copy_contents(PSPromotionManager* pm, oop obj) {
  assert(obj->is_constantPool(), "should be constant pool");
  constantPoolOop cp = (constantPoolOop) obj;
  if (AnonymousClasses && cp->has_pseudo_string() && cp->tags() != NULL) {
    oop* base = (oop*)cp->base();
    for (int i = 0; i < cp->length(); ++i, ++base) {
      if (cp->tag_at(i).is_string()) {
        if (PSScavenge::should_scavenge(base)) {
          pm->claim_or_forward_breadth(base);
        }
      }
    }
  }
}

void constantPoolKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  assert(obj->is_constantPool(), "should be constant pool");
  constantPoolOop cp = (constantPoolOop) obj;
  if (AnonymousClasses && cp->has_pseudo_string() && cp->tags() != NULL) {
    oop* base = (oop*)cp->base();
    for (int i = 0; i < cp->length(); ++i, ++base) {
      if (cp->tag_at(i).is_string()) {
        if (PSScavenge::should_scavenge(base)) {
          pm->claim_or_forward_depth(base);
        }
      }
    }
  }
}
#endif // SERIALGC

#ifndef PRODUCT

// Printing

void constantPoolKlass::oop_print_on(oop obj, outputStream* st) {
  EXCEPTION_MARK;
  oop anObj;
  assert(obj->is_constantPool(), "must be constantPool");
  Klass::oop_print_on(obj, st);
  constantPoolOop cp = constantPoolOop(obj);
  if (cp->flags() != 0) {
    st->print(" - flags: 0x%x", cp->flags());
    if (cp->has_pseudo_string()) st->print(" has_pseudo_string");
    if (cp->has_invokedynamic()) st->print(" has_invokedynamic");
    st->cr();
  }
  st->print_cr(" - cache: " INTPTR_FORMAT, cp->cache());

  for (int index = 1; index < cp->length(); index++) {      // Index 0 is unused
    st->print(" - %3d : ", index);
    cp->tag_at(index).print_on(st);
    st->print(" : ");
    switch (cp->tag_at(index).value()) {
      case JVM_CONSTANT_Class :
        { anObj = cp->klass_at(index, CATCH);
          anObj->print_value_on(st);
          st->print(" {0x%lx}", (address)anObj);
        }
        break;
      case JVM_CONSTANT_Fieldref :
      case JVM_CONSTANT_Methodref :
      case JVM_CONSTANT_InterfaceMethodref :
        st->print("klass_index=%d", cp->uncached_klass_ref_index_at(index));
        st->print(" name_and_type_index=%d", cp->uncached_name_and_type_ref_index_at(index));
        break;
      case JVM_CONSTANT_UnresolvedString :
      case JVM_CONSTANT_String :
        if (cp->is_pseudo_string_at(index)) {
          anObj = cp->pseudo_string_at(index);
        } else {
          anObj = cp->string_at(index, CATCH);
        }
        anObj->print_value_on(st);
        st->print(" {0x%lx}", (address)anObj);
        break;
      case JVM_CONSTANT_Integer :
        st->print("%d", cp->int_at(index));
        break;
      case JVM_CONSTANT_Float :
        st->print("%f", cp->float_at(index));
        break;
      case JVM_CONSTANT_Long :
        st->print_jlong(cp->long_at(index));
        index++;   // Skip entry following eigth-byte constant
        break;
      case JVM_CONSTANT_Double :
        st->print("%lf", cp->double_at(index));
        index++;   // Skip entry following eigth-byte constant
        break;
      case JVM_CONSTANT_NameAndType :
        st->print("name_index=%d", cp->name_ref_index_at(index));
        st->print(" signature_index=%d", cp->signature_ref_index_at(index));
        break;
      case JVM_CONSTANT_Utf8 :
        cp->symbol_at(index)->print_value_on(st);
        break;
      case JVM_CONSTANT_UnresolvedClass :               // fall-through
      case JVM_CONSTANT_UnresolvedClassInError: {
        // unresolved_klass_at requires lock or safe world.
        oop entry = *cp->obj_at_addr(index);
        entry->print_value_on(st);
        }
        break;
      case JVM_CONSTANT_MethodHandle :
        st->print("ref_kind=%d", cp->method_handle_ref_kind_at(index));
        st->print(" ref_index=%d", cp->method_handle_index_at(index));
        break;
      case JVM_CONSTANT_MethodType :
        st->print("signature_index=%d", cp->method_type_index_at(index));
        break;
      case JVM_CONSTANT_InvokeDynamic :
        st->print("bootstrap_method_index=%d", cp->invoke_dynamic_bootstrap_method_ref_index_at(index));
        st->print(" name_and_type_index=%d", cp->invoke_dynamic_name_and_type_ref_index_at(index));
        break;
      default:
        ShouldNotReachHere();
        break;
    }
    st->cr();
  }
  st->cr();
}

#endif

void constantPoolKlass::oop_print_value_on(oop obj, outputStream* st) {
  assert(obj->is_constantPool(), "must be constantPool");
  constantPoolOop cp = constantPoolOop(obj);
  st->print("constant pool [%d]", cp->length());
  if (cp->has_pseudo_string()) st->print("/pseudo_string");
  if (cp->has_invokedynamic()) st->print("/invokedynamic");
  cp->print_address_on(st);
  st->print(" for ");
  cp->pool_holder()->print_value_on(st);
  if (cp->cache() != NULL) {
    st->print(" cache=" PTR_FORMAT, cp->cache());
  }
}

const char* constantPoolKlass::internal_name() const {
  return "{constant pool}";
}

// Verification

void constantPoolKlass::oop_verify_on(oop obj, outputStream* st) {
  Klass::oop_verify_on(obj, st);
  guarantee(obj->is_constantPool(), "object must be constant pool");
  constantPoolOop cp = constantPoolOop(obj);
  guarantee(cp->is_perm(), "should be in permspace");
  if (!cp->partially_loaded()) {
    oop* base = (oop*)cp->base();
    for (int i = 0; i< cp->length();  i++) {
      if (cp->tag_at(i).is_klass()) {
        guarantee((*base)->is_perm(),     "should be in permspace");
        guarantee((*base)->is_klass(),    "should be klass");
      }
      if (cp->tag_at(i).is_unresolved_klass()) {
        guarantee((*base)->is_perm(),     "should be in permspace");
        guarantee((*base)->is_symbol() || (*base)->is_klass(),
                  "should be symbol or klass");
      }
      if (cp->tag_at(i).is_symbol()) {
        guarantee((*base)->is_perm(),     "should be in permspace");
        guarantee((*base)->is_symbol(),   "should be symbol");
      }
      if (cp->tag_at(i).is_unresolved_string()) {
        guarantee((*base)->is_perm(),     "should be in permspace");
        guarantee((*base)->is_symbol() || (*base)->is_instance(),
                  "should be symbol or instance");
      }
      if (cp->tag_at(i).is_string()) {
        if (!cp->has_pseudo_string()) {
          guarantee((*base)->is_perm(),   "should be in permspace");
          guarantee((*base)->is_instance(), "should be instance");
        } else {
          // can be non-perm, can be non-instance (array)
        }
      }
      // FIXME: verify JSR 292 tags JVM_CONSTANT_MethodHandle, etc.
      base++;
    }
    guarantee(cp->tags()->is_perm(),         "should be in permspace");
    guarantee(cp->tags()->is_typeArray(),    "should be type array");
    if (cp->cache() != NULL) {
      // Note: cache() can be NULL before a class is completely setup or
      // in temporary constant pools used during constant pool merging
      guarantee(cp->cache()->is_perm(),              "should be in permspace");
      guarantee(cp->cache()->is_constantPoolCache(), "should be constant pool cache");
    }
    if (cp->pool_holder() != NULL) {
      // Note: pool_holder() can be NULL in temporary constant pools
      // used during constant pool merging
      guarantee(cp->pool_holder()->is_perm(),  "should be in permspace");
      guarantee(cp->pool_holder()->is_klass(), "should be klass");
    }
  }
}

bool constantPoolKlass::oop_partially_loaded(oop obj) const {
  assert(obj->is_constantPool(), "object must be constant pool");
  constantPoolOop cp = constantPoolOop(obj);
  return cp->tags() == NULL || cp->pool_holder() == (klassOop) cp;   // Check whether pool holder points to self
}


void constantPoolKlass::oop_set_partially_loaded(oop obj) {
  assert(obj->is_constantPool(), "object must be constant pool");
  constantPoolOop cp = constantPoolOop(obj);
  assert(cp->pool_holder() == NULL, "just checking");
  cp->set_pool_holder((klassOop) cp);   // Temporarily set pool holder to point to self
}

#ifndef PRODUCT
// CompileTheWorld support. Preload all classes loaded references in the passed in constantpool
void constantPoolKlass::preload_and_initialize_all_classes(oop obj, TRAPS) {
  guarantee(obj->is_constantPool(), "object must be constant pool");
  constantPoolHandle cp(THREAD, (constantPoolOop)obj);
  guarantee(!cp->partially_loaded(), "must be fully loaded");

  for (int i = 0; i< cp->length();  i++) {
    if (cp->tag_at(i).is_unresolved_klass()) {
      // This will force loading of the class
      klassOop klass = cp->klass_at(i, CHECK);
      if (klass->is_instance()) {
        // Force initialization of class
        instanceKlass::cast(klass)->initialize(CHECK);
      }
    }
  }
}

#endif
