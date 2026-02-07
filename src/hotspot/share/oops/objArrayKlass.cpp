/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/cdsConfig.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/macros.hpp"

ObjArrayKlass* ObjArrayKlass::allocate_klass(ClassLoaderData* loader_data, int n, Klass* k, Symbol* name, TRAPS) {
  assert(ObjArrayKlass::header_size() <= InstanceKlass::header_size(),
      "array klasses must be same size as InstanceKlass");

  int size = ArrayKlass::static_size(ObjArrayKlass::header_size());

  return new (loader_data, size, THREAD) ObjArrayKlass(n, k, name, Kind);
}

Symbol* ObjArrayKlass::create_element_klass_array_name(JavaThread* current, Klass* element_klass) {
  ResourceMark rm(current);
  char* name_str = element_klass->name()->as_C_string();
  int len = element_klass->name()->utf8_length();
  char* new_str = NEW_RESOURCE_ARRAY_IN_THREAD(current, char, len + 4);
  int idx = 0;
  new_str[idx++] = JVM_SIGNATURE_ARRAY;
  if (element_klass->is_instance_klass()) { // it could be an array or simple type
    new_str[idx++] = JVM_SIGNATURE_CLASS;
  }
  memcpy(&new_str[idx], name_str, len * sizeof(char));
  idx += len;
  if (element_klass->is_instance_klass()) {
    new_str[idx++] = JVM_SIGNATURE_ENDCLASS;
  }
  new_str[idx] = '\0';
  return SymbolTable::new_symbol(new_str);
}


ObjArrayKlass* ObjArrayKlass::allocate_objArray_klass(ClassLoaderData* loader_data,
                                                      int n, Klass* element_klass, TRAPS) {

  // Eagerly allocate the direct array supertype.
  Klass* super_klass = nullptr;
  if (!Universe::is_bootstrapping() || vmClasses::Object_klass_is_loaded()) {
    assert(MultiArray_lock->holds_lock(THREAD), "must hold lock after bootstrapping");
    Klass* element_super = element_klass->super();
    if (element_super != nullptr) {
      // The element type has a direct super.  E.g., String[] has direct super of Object[].
      // Also, see if the element has secondary supertypes.
      // We need an array type for each before creating this array type.
      super_klass = element_super->array_klass(CHECK_NULL);
      const Array<Klass*>* element_supers = element_klass->secondary_supers();
      for (int i = element_supers->length() - 1; i >= 0; i--) {
        Klass* elem_super = element_supers->at(i);
        elem_super->array_klass(CHECK_NULL);
      }
      // Fall through because inheritance is acyclic and we hold the global recursive lock to allocate all the arrays.
    } else {
      // The element type is already Object.  Object[] has direct super of Object.
      super_klass = vmClasses::Object_klass();
    }
  }

  // Create type name for klass.
  Symbol* name = create_element_klass_array_name(THREAD, element_klass);

  // Initialize instance variables
  ObjArrayKlass* oak = ObjArrayKlass::allocate_klass(loader_data, n, element_klass, name, CHECK_NULL);

  ModuleEntry* module = oak->module();
  assert(module != nullptr, "No module entry for array");

  // Call complete_create_array_klass after all instance variables has been initialized.
  ArrayKlass::complete_create_array_klass(oak, super_klass, module, CHECK_NULL);

  // Add all classes to our internal class loader list here,
  // including classes in the bootstrap (null) class loader.
  // Do this step after creating the mirror so that if the
  // mirror creation fails, loaded_classes_do() doesn't find
  // an array class without a mirror.
  loader_data->add_class(oak);

  return oak;
}

ObjArrayKlass::ObjArrayKlass(int n, Klass* element_klass, Symbol* name, KlassKind kind) :
    ArrayKlass(name, kind) {
  set_dimension(n);
  set_element_klass(element_klass);
  _next_refined_array_klass = nullptr;

  Klass* bk;
  if (element_klass->is_objArray_klass()) {
    bk = ObjArrayKlass::cast(element_klass)->bottom_klass();
  } else {
    assert(!element_klass->is_refArray_klass(), "Sanity");
    bk = element_klass;
  }
  assert(bk != nullptr && (bk->is_instance_klass() || bk->is_typeArray_klass()), "invalid bottom klass");
  set_bottom_klass(bk);
  set_class_loader_data(bk->class_loader_data());

  if (element_klass->is_array_klass()) {
    set_lower_dimension(ArrayKlass::cast(element_klass));
  }

  set_layout_helper(array_layout_helper(T_OBJECT));
  assert(is_array_klass(), "sanity");
  assert(is_objArray_klass(), "sanity");
}

size_t ObjArrayKlass::oop_size(oop obj) const {
  // In this assert, we cannot safely access the Klass* with compact headers,
  // because size_given_klass() calls oop_size() on objects that might be
  // concurrently forwarded, which would overwrite the Klass*.
  assert(UseCompactObjectHeaders || obj->is_objArray(), "must be object array");
  return refArrayOop(obj)->object_size();
}

objArrayOop ObjArrayKlass::allocate_instance(int length, TRAPS) {
  check_array_allocation_length(length, arrayOopDesc::max_array_length(T_OBJECT), CHECK_NULL);

  ObjArrayKlass* ak = klass_with_properties(CHECK_NULL);
  precond(ak->kind() == Klass::RefArrayKlassKind);
  size_t size = refArrayOopDesc::object_size(length);
  objArrayOop array = (objArrayOop)Universe::heap()->array_allocate(ak, size, length,
                                                                    /* do_zero */ true, CHECK_NULL);
  assert(array->is_refArray(), "Must be");
  return array;
}

oop ObjArrayKlass::multi_allocate(int rank, jint* sizes, TRAPS) {
  int length = *sizes;
  ArrayKlass* ld_klass = lower_dimension();
  // If length < 0 allocate will throw an exception.
  ObjArrayKlass* oak = klass_with_properties(CHECK_NULL);
  assert(oak->is_refArray_klass(), "Must be");
  objArrayOop array = oak->allocate_instance(length, CHECK_NULL);
  objArrayHandle h_array (THREAD, array);
  if (rank > 1) {
    if (length != 0) {
      for (int index = 0; index < length; index++) {
        oop sub_array = ld_klass->multi_allocate(rank - 1, &sizes[1], CHECK_NULL);
        h_array->obj_at_put(index, sub_array);
      }
    } else {
      // Since this array dimension has zero length, nothing will be
      // allocated, however the lower dimension values must be checked
      // for illegal values.
      for (int i = 0; i < rank - 1; ++i) {
        sizes += 1;
        if (*sizes < 0) {
          THROW_MSG_NULL(vmSymbols::java_lang_NegativeArraySizeException(), err_msg("%d", *sizes));
        }
      }
    }
  }
  return h_array();
}

void ObjArrayKlass::copy_array(arrayOop s, int src_pos, arrayOop d,
                               int dst_pos, int length, TRAPS) {
  assert(s->is_objArray(), "must be obj array");

  assert(s->is_refArray() && d->is_refArray(), "Must be");
  RefArrayKlass::cast(s->klass())->copy_array(s, src_pos, d, dst_pos, length, THREAD);
}

bool ObjArrayKlass::can_be_primary_super_slow() const {
  if (!bottom_klass()->can_be_primary_super())
    // array of interfaces
    return false;
  else
    return Klass::can_be_primary_super_slow();
}

GrowableArray<Klass*>* ObjArrayKlass::compute_secondary_supers(int num_extra_slots,
                                                               Array<InstanceKlass*>* transitive_interfaces) {
  assert(transitive_interfaces == nullptr, "sanity");
  // interfaces = { cloneable_klass, serializable_klass, elemSuper[], ... };
  const Array<Klass*>* elem_supers = element_klass()->secondary_supers();
  int num_elem_supers = elem_supers == nullptr ? 0 : elem_supers->length();
  int num_secondaries = num_extra_slots + 2 + num_elem_supers;
  if (num_secondaries == 2) {
    // Must share this for correct bootstrapping!
    set_secondary_supers(Universe::the_array_interfaces_array(),
                         Universe::the_array_interfaces_bitmap());
    return nullptr;
  } else {
    GrowableArray<Klass*>* secondaries = new GrowableArray<Klass*>(num_elem_supers+2);
    secondaries->push(vmClasses::Cloneable_klass());
    secondaries->push(vmClasses::Serializable_klass());
    for (int i = 0; i < num_elem_supers; i++) {
      Klass* elem_super = elem_supers->at(i);
      Klass* array_super = elem_super->array_klass_or_null();
      assert(array_super != nullptr, "must already have been created");
      secondaries->push(array_super);
    }
    return secondaries;
  }
}

void ObjArrayKlass::initialize(TRAPS) {
  bottom_klass()->initialize(THREAD);  // dispatches to either InstanceKlass or TypeArrayKlass
}

void ObjArrayKlass::metaspace_pointers_do(MetaspaceClosure* it) {
  ArrayKlass::metaspace_pointers_do(it);
  it->push(&_element_klass);
  it->push(&_bottom_klass);
  if (_next_refined_array_klass != nullptr && !CDSConfig::is_dumping_dynamic_archive()) {
    it->push(&_next_refined_array_klass);
  }
}

void ObjArrayKlass::restore_unshareable_info(ClassLoaderData* loader_data, Handle protection_domain, TRAPS) {
  ArrayKlass::restore_unshareable_info(loader_data, protection_domain, CHECK);
  if (_next_refined_array_klass != nullptr) {
    _next_refined_array_klass->restore_unshareable_info(loader_data, protection_domain, CHECK);
  }
}

void ObjArrayKlass::remove_unshareable_info() {
  ArrayKlass::remove_unshareable_info();
  if (_next_refined_array_klass != nullptr && !CDSConfig::is_dumping_dynamic_archive()) {
    _next_refined_array_klass->remove_unshareable_info();
  } else {
    _next_refined_array_klass = nullptr;
  }
}

void ObjArrayKlass::remove_java_mirror() {
  ArrayKlass::remove_java_mirror();
  if (_next_refined_array_klass != nullptr) {
    _next_refined_array_klass->remove_java_mirror();
  }
}

u2 ObjArrayKlass::compute_modifier_flags() const {
  // The modifier for an objectArray is the same as its element
  assert (element_klass() != nullptr, "should be initialized");

  // Return the flags of the bottom element type.
  u2 element_flags = bottom_klass()->compute_modifier_flags();

  return (element_flags & (JVM_ACC_PUBLIC | JVM_ACC_PRIVATE | JVM_ACC_PROTECTED))
                        | (JVM_ACC_ABSTRACT | JVM_ACC_FINAL);
}

ModuleEntry* ObjArrayKlass::module() const {
  assert(bottom_klass() != nullptr, "ObjArrayKlass returned unexpected null bottom_klass");
  // The array is defined in the module of its bottom class
  return bottom_klass()->module();
}

PackageEntry* ObjArrayKlass::package() const {
  assert(bottom_klass() != nullptr, "ObjArrayKlass returned unexpected null bottom_klass");
  return bottom_klass()->package();
}

ObjArrayKlass* ObjArrayKlass::klass_with_properties(TRAPS) {

  ObjArrayKlass* ak = next_refined_array_klass_acquire();
  if (ak == nullptr) {
    // Ensure atomic creation of refined array klasses
    RecursiveLocker rl(MultiArray_lock, THREAD);

    ak = RefArrayKlass::allocate_refArray_klass(class_loader_data(), dimension(), element_klass(), CHECK_NULL);
    release_set_next_refined_array_klass(ak);
  }
  THREAD->check_possible_safepoint();
  return ak;
}

// Printing

void ObjArrayKlass::print_on(outputStream* st) const {
#ifndef PRODUCT
  Klass::print_on(st);
  st->print(" - instance klass: ");
  element_klass()->print_value_on(st);
  st->cr();
#endif //PRODUCT
}

void ObjArrayKlass::print_value_on(outputStream* st) const {
  assert(is_klass(), "must be klass");

  element_klass()->print_value_on(st);
  st->print("[]");
}

#ifndef PRODUCT

void ObjArrayKlass::oop_print_on(oop obj, outputStream* st) {
  ShouldNotReachHere();
}

#endif //PRODUCT

void ObjArrayKlass::oop_print_value_on(oop obj, outputStream* st) {
  ShouldNotReachHere();
}

const char* ObjArrayKlass::internal_name() const {
  return external_name();
}


// Verification

void ObjArrayKlass::verify_on(outputStream* st) {
  ArrayKlass::verify_on(st);
  guarantee(element_klass()->is_klass(), "should be klass");
  guarantee(bottom_klass()->is_klass(), "should be klass");
  Klass* bk = bottom_klass();
  guarantee(bk->is_instance_klass() || bk->is_typeArray_klass(),  "invalid bottom klass");
}

void ObjArrayKlass::oop_verify_on(oop obj, outputStream* st) {
  ArrayKlass::oop_verify_on(obj, st);
  guarantee(obj->is_objArray(), "must be objArray");
  objArrayOop oa = objArrayOop(obj);
  for(int index = 0; index < oa->length(); index++) {
    guarantee(oopDesc::is_oop_or_null(oa->obj_at(index)), "should be oop");
  }
}
