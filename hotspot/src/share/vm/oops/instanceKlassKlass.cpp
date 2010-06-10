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
# include "incls/_instanceKlassKlass.cpp.incl"

klassOop instanceKlassKlass::create_klass(TRAPS) {
  instanceKlassKlass o;
  KlassHandle h_this_klass(THREAD, Universe::klassKlassObj());
  KlassHandle k = base_create_klass(h_this_klass, header_size(), o.vtbl_value(), CHECK_NULL);
  // Make sure size calculation is right
  assert(k()->size() == align_object_size(header_size()), "wrong size for object");
  java_lang_Class::create_mirror(k, CHECK_NULL); // Allocate mirror
  return k();
}

int instanceKlassKlass::oop_size(oop obj) const {
  assert(obj->is_klass(), "must be klass");
  return instanceKlass::cast(klassOop(obj))->object_size();
}

bool instanceKlassKlass::oop_is_parsable(oop obj) const {
  assert(obj->is_klass(), "must be klass");
  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  return (!ik->null_vtbl()) && ik->object_is_parsable();
}

void instanceKlassKlass::iterate_c_heap_oops(instanceKlass* ik,
                                             OopClosure* closure) {
  if (ik->oop_map_cache() != NULL) {
    ik->oop_map_cache()->oop_iterate(closure);
  }

  if (ik->jni_ids() != NULL) {
    ik->jni_ids()->oops_do(closure);
  }
}

void instanceKlassKlass::oop_follow_contents(oop obj) {
  assert(obj->is_klass(),"must be a klass");
  assert(klassOop(obj)->klass_part()->oop_is_instance_slow(), "must be instance klass");

  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  ik->follow_static_fields();
  {
    HandleMark hm;
    ik->vtable()->oop_follow_contents();
    ik->itable()->oop_follow_contents();
  }

  MarkSweep::mark_and_push(ik->adr_array_klasses());
  MarkSweep::mark_and_push(ik->adr_methods());
  MarkSweep::mark_and_push(ik->adr_method_ordering());
  MarkSweep::mark_and_push(ik->adr_local_interfaces());
  MarkSweep::mark_and_push(ik->adr_transitive_interfaces());
  MarkSweep::mark_and_push(ik->adr_fields());
  MarkSweep::mark_and_push(ik->adr_constants());
  MarkSweep::mark_and_push(ik->adr_class_loader());
  MarkSweep::mark_and_push(ik->adr_source_file_name());
  MarkSweep::mark_and_push(ik->adr_source_debug_extension());
  MarkSweep::mark_and_push(ik->adr_inner_classes());
  MarkSweep::mark_and_push(ik->adr_protection_domain());
  MarkSweep::mark_and_push(ik->adr_host_klass());
  MarkSweep::mark_and_push(ik->adr_signers());
  MarkSweep::mark_and_push(ik->adr_generic_signature());
  MarkSweep::mark_and_push(ik->adr_bootstrap_method());
  MarkSweep::mark_and_push(ik->adr_class_annotations());
  MarkSweep::mark_and_push(ik->adr_fields_annotations());
  MarkSweep::mark_and_push(ik->adr_methods_annotations());
  MarkSweep::mark_and_push(ik->adr_methods_parameter_annotations());
  MarkSweep::mark_and_push(ik->adr_methods_default_annotations());

  // We do not follow adr_implementors() here. It is followed later
  // in instanceKlass::follow_weak_klass_links()

  klassKlass::oop_follow_contents(obj);

  iterate_c_heap_oops(ik, &MarkSweep::mark_and_push_closure);
}

#ifndef SERIALGC
void instanceKlassKlass::oop_follow_contents(ParCompactionManager* cm,
                                             oop obj) {
  assert(obj->is_klass(),"must be a klass");
  assert(klassOop(obj)->klass_part()->oop_is_instance_slow(), "must be instance klass");

  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  ik->follow_static_fields(cm);
  ik->vtable()->oop_follow_contents(cm);
  ik->itable()->oop_follow_contents(cm);

  PSParallelCompact::mark_and_push(cm, ik->adr_array_klasses());
  PSParallelCompact::mark_and_push(cm, ik->adr_methods());
  PSParallelCompact::mark_and_push(cm, ik->adr_method_ordering());
  PSParallelCompact::mark_and_push(cm, ik->adr_local_interfaces());
  PSParallelCompact::mark_and_push(cm, ik->adr_transitive_interfaces());
  PSParallelCompact::mark_and_push(cm, ik->adr_fields());
  PSParallelCompact::mark_and_push(cm, ik->adr_constants());
  PSParallelCompact::mark_and_push(cm, ik->adr_class_loader());
  PSParallelCompact::mark_and_push(cm, ik->adr_source_file_name());
  PSParallelCompact::mark_and_push(cm, ik->adr_source_debug_extension());
  PSParallelCompact::mark_and_push(cm, ik->adr_inner_classes());
  PSParallelCompact::mark_and_push(cm, ik->adr_protection_domain());
  PSParallelCompact::mark_and_push(cm, ik->adr_host_klass());
  PSParallelCompact::mark_and_push(cm, ik->adr_signers());
  PSParallelCompact::mark_and_push(cm, ik->adr_generic_signature());
  PSParallelCompact::mark_and_push(cm, ik->adr_bootstrap_method());
  PSParallelCompact::mark_and_push(cm, ik->adr_class_annotations());
  PSParallelCompact::mark_and_push(cm, ik->adr_fields_annotations());
  PSParallelCompact::mark_and_push(cm, ik->adr_methods_annotations());
  PSParallelCompact::mark_and_push(cm, ik->adr_methods_parameter_annotations());
  PSParallelCompact::mark_and_push(cm, ik->adr_methods_default_annotations());

  // We do not follow adr_implementor() here. It is followed later
  // in instanceKlass::follow_weak_klass_links()

  klassKlass::oop_follow_contents(cm, obj);

  PSParallelCompact::MarkAndPushClosure mark_and_push_closure(cm);
  iterate_c_heap_oops(ik, &mark_and_push_closure);
}
#endif // SERIALGC

int instanceKlassKlass::oop_oop_iterate(oop obj, OopClosure* blk) {
  assert(obj->is_klass(),"must be a klass");
  assert(klassOop(obj)->klass_part()->oop_is_instance_slow(), "must be instance klass");
  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = ik->object_size();

  ik->iterate_static_fields(blk);
  ik->vtable()->oop_oop_iterate(blk);
  ik->itable()->oop_oop_iterate(blk);

  blk->do_oop(ik->adr_array_klasses());
  blk->do_oop(ik->adr_methods());
  blk->do_oop(ik->adr_method_ordering());
  blk->do_oop(ik->adr_local_interfaces());
  blk->do_oop(ik->adr_transitive_interfaces());
  blk->do_oop(ik->adr_fields());
  blk->do_oop(ik->adr_constants());
  blk->do_oop(ik->adr_class_loader());
  blk->do_oop(ik->adr_protection_domain());
  blk->do_oop(ik->adr_host_klass());
  blk->do_oop(ik->adr_signers());
  blk->do_oop(ik->adr_source_file_name());
  blk->do_oop(ik->adr_source_debug_extension());
  blk->do_oop(ik->adr_inner_classes());
  for (int i = 0; i < instanceKlass::implementors_limit; i++) {
    blk->do_oop(&ik->adr_implementors()[i]);
  }
  blk->do_oop(ik->adr_generic_signature());
  blk->do_oop(ik->adr_bootstrap_method());
  blk->do_oop(ik->adr_class_annotations());
  blk->do_oop(ik->adr_fields_annotations());
  blk->do_oop(ik->adr_methods_annotations());
  blk->do_oop(ik->adr_methods_parameter_annotations());
  blk->do_oop(ik->adr_methods_default_annotations());

  klassKlass::oop_oop_iterate(obj, blk);

  if(ik->oop_map_cache() != NULL) ik->oop_map_cache()->oop_iterate(blk);
  return size;
}

int instanceKlassKlass::oop_oop_iterate_m(oop obj, OopClosure* blk,
                                           MemRegion mr) {
  assert(obj->is_klass(),"must be a klass");
  assert(klassOop(obj)->klass_part()->oop_is_instance_slow(), "must be instance klass");
  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = ik->object_size();

  ik->iterate_static_fields(blk, mr);
  ik->vtable()->oop_oop_iterate_m(blk, mr);
  ik->itable()->oop_oop_iterate_m(blk, mr);

  oop* adr;
  adr = ik->adr_array_klasses();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_methods();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_method_ordering();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_local_interfaces();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_transitive_interfaces();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_fields();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_constants();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_class_loader();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_protection_domain();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_host_klass();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_signers();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_source_file_name();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_source_debug_extension();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_inner_classes();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_implementors();
  for (int i = 0; i < instanceKlass::implementors_limit; i++) {
    if (mr.contains(&adr[i])) blk->do_oop(&adr[i]);
  }
  adr = ik->adr_generic_signature();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_bootstrap_method();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_class_annotations();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_fields_annotations();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_methods_annotations();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_methods_parameter_annotations();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = ik->adr_methods_default_annotations();
  if (mr.contains(adr)) blk->do_oop(adr);

  klassKlass::oop_oop_iterate_m(obj, blk, mr);

  if(ik->oop_map_cache() != NULL) ik->oop_map_cache()->oop_iterate(blk, mr);
  return size;
}

int instanceKlassKlass::oop_adjust_pointers(oop obj) {
  assert(obj->is_klass(),"must be a klass");
  assert(klassOop(obj)->klass_part()->oop_is_instance_slow(), "must be instance klass");

  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  ik->adjust_static_fields();
  ik->vtable()->oop_adjust_pointers();
  ik->itable()->oop_adjust_pointers();

  MarkSweep::adjust_pointer(ik->adr_array_klasses());
  MarkSweep::adjust_pointer(ik->adr_methods());
  MarkSweep::adjust_pointer(ik->adr_method_ordering());
  MarkSweep::adjust_pointer(ik->adr_local_interfaces());
  MarkSweep::adjust_pointer(ik->adr_transitive_interfaces());
  MarkSweep::adjust_pointer(ik->adr_fields());
  MarkSweep::adjust_pointer(ik->adr_constants());
  MarkSweep::adjust_pointer(ik->adr_class_loader());
  MarkSweep::adjust_pointer(ik->adr_protection_domain());
  MarkSweep::adjust_pointer(ik->adr_host_klass());
  MarkSweep::adjust_pointer(ik->adr_signers());
  MarkSweep::adjust_pointer(ik->adr_source_file_name());
  MarkSweep::adjust_pointer(ik->adr_source_debug_extension());
  MarkSweep::adjust_pointer(ik->adr_inner_classes());
  for (int i = 0; i < instanceKlass::implementors_limit; i++) {
    MarkSweep::adjust_pointer(&ik->adr_implementors()[i]);
  }
  MarkSweep::adjust_pointer(ik->adr_generic_signature());
  MarkSweep::adjust_pointer(ik->adr_bootstrap_method());
  MarkSweep::adjust_pointer(ik->adr_class_annotations());
  MarkSweep::adjust_pointer(ik->adr_fields_annotations());
  MarkSweep::adjust_pointer(ik->adr_methods_annotations());
  MarkSweep::adjust_pointer(ik->adr_methods_parameter_annotations());
  MarkSweep::adjust_pointer(ik->adr_methods_default_annotations());

  iterate_c_heap_oops(ik, &MarkSweep::adjust_root_pointer_closure);

  return klassKlass::oop_adjust_pointers(obj);
}

#ifndef SERIALGC
void instanceKlassKlass::oop_copy_contents(PSPromotionManager* pm, oop obj) {
  assert(!pm->depth_first(), "invariant");
  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  ik->copy_static_fields(pm);

  oop* loader_addr = ik->adr_class_loader();
  if (PSScavenge::should_scavenge(loader_addr)) {
    pm->claim_or_forward_breadth(loader_addr);
  }

  oop* pd_addr = ik->adr_protection_domain();
  if (PSScavenge::should_scavenge(pd_addr)) {
    pm->claim_or_forward_breadth(pd_addr);
  }

  oop* hk_addr = ik->adr_host_klass();
  if (PSScavenge::should_scavenge(hk_addr)) {
    pm->claim_or_forward_breadth(hk_addr);
  }

  oop* sg_addr = ik->adr_signers();
  if (PSScavenge::should_scavenge(sg_addr)) {
    pm->claim_or_forward_breadth(sg_addr);
  }

  oop* bsm_addr = ik->adr_bootstrap_method();
  if (PSScavenge::should_scavenge(bsm_addr)) {
    pm->claim_or_forward_breadth(bsm_addr);
  }

  klassKlass::oop_copy_contents(pm, obj);
}

void instanceKlassKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  assert(pm->depth_first(), "invariant");
  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  ik->push_static_fields(pm);

  oop* loader_addr = ik->adr_class_loader();
  if (PSScavenge::should_scavenge(loader_addr)) {
    pm->claim_or_forward_depth(loader_addr);
  }

  oop* pd_addr = ik->adr_protection_domain();
  if (PSScavenge::should_scavenge(pd_addr)) {
    pm->claim_or_forward_depth(pd_addr);
  }

  oop* hk_addr = ik->adr_host_klass();
  if (PSScavenge::should_scavenge(hk_addr)) {
    pm->claim_or_forward_depth(hk_addr);
  }

  oop* sg_addr = ik->adr_signers();
  if (PSScavenge::should_scavenge(sg_addr)) {
    pm->claim_or_forward_depth(sg_addr);
  }

  oop* bsm_addr = ik->adr_bootstrap_method();
  if (PSScavenge::should_scavenge(bsm_addr)) {
    pm->claim_or_forward_depth(bsm_addr);
  }

  klassKlass::oop_copy_contents(pm, obj);
}

int instanceKlassKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  assert(obj->is_klass(),"must be a klass");
  assert(klassOop(obj)->klass_part()->oop_is_instance_slow(),
         "must be instance klass");

  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  ik->update_static_fields();
  ik->vtable()->oop_update_pointers(cm);
  ik->itable()->oop_update_pointers(cm);

  oop* const beg_oop = ik->oop_block_beg();
  oop* const end_oop = ik->oop_block_end();
  for (oop* cur_oop = beg_oop; cur_oop < end_oop; ++cur_oop) {
    PSParallelCompact::adjust_pointer(cur_oop);
  }

  OopClosure* closure = PSParallelCompact::adjust_root_pointer_closure();
  iterate_c_heap_oops(ik, closure);

  klassKlass::oop_update_pointers(cm, obj);
  return ik->object_size();
}

int instanceKlassKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                            HeapWord* beg_addr,
                                            HeapWord* end_addr) {
  assert(obj->is_klass(),"must be a klass");
  assert(klassOop(obj)->klass_part()->oop_is_instance_slow(),
         "must be instance klass");

  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  ik->update_static_fields(beg_addr, end_addr);
  ik->vtable()->oop_update_pointers(cm, beg_addr, end_addr);
  ik->itable()->oop_update_pointers(cm, beg_addr, end_addr);

  oop* const beg_oop = MAX2((oop*)beg_addr, ik->oop_block_beg());
  oop* const end_oop = MIN2((oop*)end_addr, ik->oop_block_end());
  for (oop* cur_oop = beg_oop; cur_oop < end_oop; ++cur_oop) {
    PSParallelCompact::adjust_pointer(cur_oop);
  }

  // The oop_map_cache, jni_ids and jni_id_map are allocated from the C heap,
  // and so don't lie within any 'Chunk' boundaries.  Update them when the
  // lowest addressed oop in the instanceKlass 'oop_block' is updated.
  if (beg_oop == ik->oop_block_beg()) {
    OopClosure* closure = PSParallelCompact::adjust_root_pointer_closure();
    iterate_c_heap_oops(ik, closure);
  }

  klassKlass::oop_update_pointers(cm, obj, beg_addr, end_addr);
  return ik->object_size();
}
#endif // SERIALGC

klassOop
instanceKlassKlass::allocate_instance_klass(int vtable_len, int itable_len,
                                            int static_field_size,
                                            unsigned nonstatic_oop_map_count,
                                            ReferenceType rt, TRAPS) {

  const int nonstatic_oop_map_size =
    instanceKlass::nonstatic_oop_map_size(nonstatic_oop_map_count);
  int size = instanceKlass::object_size(align_object_offset(vtable_len) + align_object_offset(itable_len) + static_field_size + nonstatic_oop_map_size);

  // Allocation
  KlassHandle h_this_klass(THREAD, as_klassOop());
  KlassHandle k;
  if (rt == REF_NONE) {
    // regular klass
    instanceKlass o;
    k = base_create_klass(h_this_klass, size, o.vtbl_value(), CHECK_NULL);
  } else {
    // reference klass
    instanceRefKlass o;
    k = base_create_klass(h_this_klass, size, o.vtbl_value(), CHECK_NULL);
  }
  {
    No_Safepoint_Verifier no_safepoint; // until k becomes parsable
    instanceKlass* ik = (instanceKlass*) k()->klass_part();
    assert(!k()->is_parsable(), "not expecting parsability yet.");

    // The sizes of these these three variables are used for determining the
    // size of the instanceKlassOop. It is critical that these are set to the right
    // sizes before the first GC, i.e., when we allocate the mirror.
    ik->set_vtable_length(vtable_len);
    ik->set_itable_length(itable_len);
    ik->set_static_field_size(static_field_size);
    ik->set_nonstatic_oop_map_size(nonstatic_oop_map_size);
    assert(k()->size() == size, "wrong size for object");

    ik->set_array_klasses(NULL);
    ik->set_methods(NULL);
    ik->set_method_ordering(NULL);
    ik->set_local_interfaces(NULL);
    ik->set_transitive_interfaces(NULL);
    ik->init_implementor();
    ik->set_fields(NULL);
    ik->set_constants(NULL);
    ik->set_class_loader(NULL);
    ik->set_protection_domain(NULL);
    ik->set_host_klass(NULL);
    ik->set_signers(NULL);
    ik->set_source_file_name(NULL);
    ik->set_source_debug_extension(NULL);
    ik->set_inner_classes(NULL);
    ik->set_static_oop_field_size(0);
    ik->set_nonstatic_field_size(0);
    ik->set_is_marked_dependent(false);
    ik->set_init_state(instanceKlass::allocated);
    ik->set_init_thread(NULL);
    ik->set_reference_type(rt);
    ik->set_oop_map_cache(NULL);
    ik->set_jni_ids(NULL);
    ik->set_osr_nmethods_head(NULL);
    ik->set_breakpoints(NULL);
    ik->init_previous_versions();
    ik->set_generic_signature(NULL);
    ik->set_bootstrap_method(NULL);
    ik->release_set_methods_jmethod_ids(NULL);
    ik->release_set_methods_cached_itable_indices(NULL);
    ik->set_class_annotations(NULL);
    ik->set_fields_annotations(NULL);
    ik->set_methods_annotations(NULL);
    ik->set_methods_parameter_annotations(NULL);
    ik->set_methods_default_annotations(NULL);
    ik->set_enclosing_method_indices(0, 0);
    ik->set_jvmti_cached_class_field_map(NULL);
    ik->set_initial_method_idnum(0);
    assert(k()->is_parsable(), "should be parsable here.");

    // initialize the non-header words to zero
    intptr_t* p = (intptr_t*)k();
    for (int index = instanceKlass::header_size(); index < size; index++) {
      p[index] = NULL_WORD;
    }

    // To get verify to work - must be set to partial loaded before first GC point.
    k()->set_partially_loaded();
  }

  // GC can happen here
  java_lang_Class::create_mirror(k, CHECK_NULL); // Allocate mirror
  return k();
}



#ifndef PRODUCT

// Printing

#define BULLET  " - "

static const char* state_names[] = {
  "unparseable_by_gc", "allocated", "loaded", "linked", "being_initialized", "fully_initialized", "initialization_error"
};


void instanceKlassKlass::oop_print_on(oop obj, outputStream* st) {
  assert(obj->is_klass(), "must be klass");
  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  klassKlass::oop_print_on(obj, st);

  st->print(BULLET"instance size:     %d", ik->size_helper());                        st->cr();
  st->print(BULLET"klass size:        %d", ik->object_size());                        st->cr();
  st->print(BULLET"access:            "); ik->access_flags().print_on(st);            st->cr();
  st->print(BULLET"state:             "); st->print_cr(state_names[ik->_init_state]);
  st->print(BULLET"name:              "); ik->name()->print_value_on(st);             st->cr();
  st->print(BULLET"super:             "); ik->super()->print_value_on(st);            st->cr();
  st->print(BULLET"sub:               ");
  Klass* sub = ik->subklass();
  int n;
  for (n = 0; sub != NULL; n++, sub = sub->next_sibling()) {
    if (n < MaxSubklassPrintSize) {
      sub->as_klassOop()->print_value_on(st);
      st->print("   ");
    }
  }
  if (n >= MaxSubklassPrintSize) st->print("(%d more klasses...)", n - MaxSubklassPrintSize);
  st->cr();

  if (ik->is_interface()) {
    st->print_cr(BULLET"nof implementors:  %d", ik->nof_implementors());
    int print_impl = 0;
    for (int i = 0; i < instanceKlass::implementors_limit; i++) {
      if (ik->implementor(i) != NULL) {
        if (++print_impl == 1)
          st->print_cr(BULLET"implementor:    ");
        st->print("   ");
        ik->implementor(i)->print_value_on(st);
      }
    }
    if (print_impl > 0)  st->cr();
  }

  st->print(BULLET"arrays:            "); ik->array_klasses()->print_value_on(st);     st->cr();
  st->print(BULLET"methods:           "); ik->methods()->print_value_on(st);           st->cr();
  if (Verbose) {
    objArrayOop methods = ik->methods();
    for(int i = 0; i < methods->length(); i++) {
      tty->print("%d : ", i); methods->obj_at(i)->print_value(); tty->cr();
    }
  }
  st->print(BULLET"method ordering:   "); ik->method_ordering()->print_value_on(st);       st->cr();
  st->print(BULLET"local interfaces:  "); ik->local_interfaces()->print_value_on(st);      st->cr();
  st->print(BULLET"trans. interfaces: "); ik->transitive_interfaces()->print_value_on(st); st->cr();
  st->print(BULLET"constants:         "); ik->constants()->print_value_on(st);         st->cr();
  st->print(BULLET"class loader:      "); ik->class_loader()->print_value_on(st);      st->cr();
  st->print(BULLET"protection domain: "); ik->protection_domain()->print_value_on(st); st->cr();
  st->print(BULLET"host class:        "); ik->host_klass()->print_value_on(st);        st->cr();
  st->print(BULLET"signers:           "); ik->signers()->print_value_on(st);           st->cr();
  if (ik->source_file_name() != NULL) {
    st->print(BULLET"source file:       ");
    ik->source_file_name()->print_value_on(st);
    st->cr();
  }
  if (ik->source_debug_extension() != NULL) {
    st->print(BULLET"source debug extension:       ");
    ik->source_debug_extension()->print_value_on(st);
    st->cr();
  }

  {
    ResourceMark rm;
    // PreviousVersionInfo objects returned via PreviousVersionWalker
    // contain a GrowableArray of handles. We have to clean up the
    // GrowableArray _after_ the PreviousVersionWalker destructor
    // has destroyed the handles.
    {
      bool have_pv = false;
      PreviousVersionWalker pvw(ik);
      for (PreviousVersionInfo * pv_info = pvw.next_previous_version();
           pv_info != NULL; pv_info = pvw.next_previous_version()) {
        if (!have_pv)
          st->print(BULLET"previous version:  ");
        have_pv = true;
        pv_info->prev_constant_pool_handle()()->print_value_on(st);
      }
      if (have_pv)  st->cr();
    } // pvw is cleaned up
  } // rm is cleaned up

  if (ik->bootstrap_method() != NULL) {
    st->print(BULLET"bootstrap method:  ");
    ik->bootstrap_method()->print_value_on(st);
    st->cr();
  }
  if (ik->generic_signature() != NULL) {
    st->print(BULLET"generic signature: ");
    ik->generic_signature()->print_value_on(st);
    st->cr();
  }
  st->print(BULLET"inner classes:     "); ik->inner_classes()->print_value_on(st);     st->cr();
  st->print(BULLET"java mirror:       "); ik->java_mirror()->print_value_on(st);       st->cr();
  st->print(BULLET"vtable length      %d  (start addr: " INTPTR_FORMAT ")", ik->vtable_length(), ik->start_of_vtable());  st->cr();
  st->print(BULLET"itable length      %d (start addr: " INTPTR_FORMAT ")", ik->itable_length(), ik->start_of_itable()); st->cr();
  st->print_cr(BULLET"---- static fields (%d words):", ik->static_field_size());
  FieldPrinter print_static_field(st);
  ik->do_local_static_fields(&print_static_field);
  st->print_cr(BULLET"---- non-static fields (%d words):", ik->nonstatic_field_size());
  FieldPrinter print_nonstatic_field(st);
  ik->do_nonstatic_fields(&print_nonstatic_field);

  st->print(BULLET"static oop maps:     ");
  if (ik->static_oop_field_size() > 0) {
    int first_offset = ik->offset_of_static_fields();
    st->print("%d-%d", first_offset, first_offset + ik->static_oop_field_size() - 1);
  }
  st->cr();

  st->print(BULLET"non-static oop maps: ");
  OopMapBlock* map     = ik->start_of_nonstatic_oop_maps();
  OopMapBlock* end_map = map + ik->nonstatic_oop_map_count();
  while (map < end_map) {
    st->print("%d-%d ", map->offset(), map->offset() + heapOopSize*(map->count() - 1));
    map++;
  }
  st->cr();
}

#endif //PRODUCT

void instanceKlassKlass::oop_print_value_on(oop obj, outputStream* st) {
  assert(obj->is_klass(), "must be klass");
  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  ik->name()->print_value_on(st);
}

const char* instanceKlassKlass::internal_name() const {
  return "{instance class}";
}

// Verification

class VerifyFieldClosure: public OopClosure {
 protected:
  template <class T> void do_oop_work(T* p) {
    guarantee(Universe::heap()->is_in(p), "should be in heap");
    oop obj = oopDesc::load_decode_heap_oop(p);
    guarantee(obj->is_oop_or_null(), "should be in heap");
  }
 public:
  virtual void do_oop(oop* p)       { VerifyFieldClosure::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { VerifyFieldClosure::do_oop_work(p); }
};

void instanceKlassKlass::oop_verify_on(oop obj, outputStream* st) {
  klassKlass::oop_verify_on(obj, st);
  if (!obj->partially_loaded()) {
    Thread *thread = Thread::current();
    instanceKlass* ik = instanceKlass::cast(klassOop(obj));

#ifndef PRODUCT
    // Avoid redundant verifies
    if (ik->_verify_count == Universe::verify_count()) return;
    ik->_verify_count = Universe::verify_count();
#endif
    // Verify that klass is present in SystemDictionary
    if (ik->is_loaded() && !ik->is_anonymous()) {
      symbolHandle h_name (thread, ik->name());
      Handle h_loader (thread, ik->class_loader());
      Handle h_obj(thread, obj);
      SystemDictionary::verify_obj_klass_present(h_obj, h_name, h_loader);
    }

    // Verify static fields
    VerifyFieldClosure blk;
    ik->iterate_static_fields(&blk);

    // Verify vtables
    if (ik->is_linked()) {
      ResourceMark rm(thread);
      // $$$ This used to be done only for m/s collections.  Doing it
      // always seemed a valid generalization.  (DLD -- 6/00)
      ik->vtable()->verify(st);
    }

    // Verify oop map cache
    if (ik->oop_map_cache() != NULL) {
      ik->oop_map_cache()->verify();
    }

    // Verify first subklass
    if (ik->subklass_oop() != NULL) {
      guarantee(ik->subklass_oop()->is_perm(),  "should be in permspace");
      guarantee(ik->subklass_oop()->is_klass(), "should be klass");
    }

    // Verify siblings
    klassOop super = ik->super();
    Klass* sib = ik->next_sibling();
    int sib_count = 0;
    while (sib != NULL) {
      if (sib == ik) {
        fatal(err_msg("subclass cycle of length %d", sib_count));
      }
      if (sib_count >= 100000) {
        fatal(err_msg("suspiciously long subclass list %d", sib_count));
      }
      guarantee(sib->as_klassOop()->is_klass(), "should be klass");
      guarantee(sib->as_klassOop()->is_perm(),  "should be in permspace");
      guarantee(sib->super() == super, "siblings should have same superklass");
      sib = sib->next_sibling();
    }

    // Verify implementor fields
    bool saw_null_impl = false;
    for (int i = 0; i < instanceKlass::implementors_limit; i++) {
      klassOop im = ik->implementor(i);
      if (im == NULL) { saw_null_impl = true; continue; }
      guarantee(!saw_null_impl, "non-nulls must preceded all nulls");
      guarantee(ik->is_interface(), "only interfaces should have implementor set");
      guarantee(i < ik->nof_implementors(), "should only have one implementor");
      guarantee(im->is_perm(),  "should be in permspace");
      guarantee(im->is_klass(), "should be klass");
      guarantee(!Klass::cast(klassOop(im))->is_interface(), "implementors cannot be interfaces");
    }

    // Verify local interfaces
    objArrayOop local_interfaces = ik->local_interfaces();
    guarantee(local_interfaces->is_perm(),          "should be in permspace");
    guarantee(local_interfaces->is_objArray(),      "should be obj array");
    int j;
    for (j = 0; j < local_interfaces->length(); j++) {
      oop e = local_interfaces->obj_at(j);
      guarantee(e->is_klass() && Klass::cast(klassOop(e))->is_interface(), "invalid local interface");
    }

    // Verify transitive interfaces
    objArrayOop transitive_interfaces = ik->transitive_interfaces();
    guarantee(transitive_interfaces->is_perm(),          "should be in permspace");
    guarantee(transitive_interfaces->is_objArray(),      "should be obj array");
    for (j = 0; j < transitive_interfaces->length(); j++) {
      oop e = transitive_interfaces->obj_at(j);
      guarantee(e->is_klass() && Klass::cast(klassOop(e))->is_interface(), "invalid transitive interface");
    }

    // Verify methods
    objArrayOop methods = ik->methods();
    guarantee(methods->is_perm(),              "should be in permspace");
    guarantee(methods->is_objArray(),          "should be obj array");
    for (j = 0; j < methods->length(); j++) {
      guarantee(methods->obj_at(j)->is_method(), "non-method in methods array");
    }
    for (j = 0; j < methods->length() - 1; j++) {
      methodOop m1 = methodOop(methods->obj_at(j));
      methodOop m2 = methodOop(methods->obj_at(j + 1));
      guarantee(m1->name()->fast_compare(m2->name()) <= 0, "methods not sorted correctly");
    }

    // Verify method ordering
    typeArrayOop method_ordering = ik->method_ordering();
    guarantee(method_ordering->is_perm(),              "should be in permspace");
    guarantee(method_ordering->is_typeArray(),         "should be type array");
    int length = method_ordering->length();
    if (JvmtiExport::can_maintain_original_method_order()) {
      guarantee(length == methods->length(),           "invalid method ordering length");
      jlong sum = 0;
      for (j = 0; j < length; j++) {
        int original_index = method_ordering->int_at(j);
        guarantee(original_index >= 0 && original_index < length, "invalid method ordering index");
        sum += original_index;
      }
      // Verify sum of indices 0,1,...,length-1
      guarantee(sum == ((jlong)length*(length-1))/2,   "invalid method ordering sum");
    } else {
      guarantee(length == 0,                           "invalid method ordering length");
    }

    // Verify JNI static field identifiers
    if (ik->jni_ids() != NULL) {
      ik->jni_ids()->verify(ik->as_klassOop());
    }

    // Verify other fields
    if (ik->array_klasses() != NULL) {
      guarantee(ik->array_klasses()->is_perm(),      "should be in permspace");
      guarantee(ik->array_klasses()->is_klass(),     "should be klass");
    }
    guarantee(ik->fields()->is_perm(),               "should be in permspace");
    guarantee(ik->fields()->is_typeArray(),          "should be type array");
    guarantee(ik->constants()->is_perm(),            "should be in permspace");
    guarantee(ik->constants()->is_constantPool(),    "should be constant pool");
    guarantee(ik->inner_classes()->is_perm(),        "should be in permspace");
    guarantee(ik->inner_classes()->is_typeArray(),   "should be type array");
    if (ik->source_file_name() != NULL) {
      guarantee(ik->source_file_name()->is_perm(),   "should be in permspace");
      guarantee(ik->source_file_name()->is_symbol(), "should be symbol");
    }
    if (ik->source_debug_extension() != NULL) {
      guarantee(ik->source_debug_extension()->is_perm(),   "should be in permspace");
      guarantee(ik->source_debug_extension()->is_symbol(), "should be symbol");
    }
    if (ik->protection_domain() != NULL) {
      guarantee(ik->protection_domain()->is_oop(),  "should be oop");
    }
    if (ik->host_klass() != NULL) {
      guarantee(ik->host_klass()->is_oop(),  "should be oop");
    }
    if (ik->signers() != NULL) {
      guarantee(ik->signers()->is_objArray(),       "should be obj array");
    }
    if (ik->generic_signature() != NULL) {
      guarantee(ik->generic_signature()->is_perm(),   "should be in permspace");
      guarantee(ik->generic_signature()->is_symbol(), "should be symbol");
    }
    if (ik->class_annotations() != NULL) {
      guarantee(ik->class_annotations()->is_typeArray(), "should be type array");
    }
    if (ik->fields_annotations() != NULL) {
      guarantee(ik->fields_annotations()->is_objArray(), "should be obj array");
    }
    if (ik->methods_annotations() != NULL) {
      guarantee(ik->methods_annotations()->is_objArray(), "should be obj array");
    }
    if (ik->methods_parameter_annotations() != NULL) {
      guarantee(ik->methods_parameter_annotations()->is_objArray(), "should be obj array");
    }
    if (ik->methods_default_annotations() != NULL) {
      guarantee(ik->methods_default_annotations()->is_objArray(), "should be obj array");
    }
  }
}


bool instanceKlassKlass::oop_partially_loaded(oop obj) const {
  assert(obj->is_klass(), "object must be klass");
  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  assert(ik->oop_is_instance(), "object must be instanceKlass");
  return ik->transitive_interfaces() == (objArrayOop) obj;   // Check whether transitive_interfaces points to self
}


// The transitive_interfaces is the last field set when loading an object.
void instanceKlassKlass::oop_set_partially_loaded(oop obj) {
  assert(obj->is_klass(), "object must be klass");
  instanceKlass* ik = instanceKlass::cast(klassOop(obj));
  // Set the layout helper to a place-holder value, until fuller initialization.
  // (This allows asserts in oop_is_instance to succeed.)
  ik->set_layout_helper(Klass::instance_layout_helper(0, true));
  assert(ik->oop_is_instance(), "object must be instanceKlass");
  assert(ik->transitive_interfaces() == NULL, "just checking");
  ik->set_transitive_interfaces((objArrayOop) obj);   // Temporarily set transitive_interfaces to point to self
}
