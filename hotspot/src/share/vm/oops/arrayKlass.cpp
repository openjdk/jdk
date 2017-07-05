/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "jvmtifiles/jvmti.h"
#include "memory/gcLocker.hpp"
#include "memory/universe.inline.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/arrayOop.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"

int ArrayKlass::static_size(int header_size) {
  // size of an array klass object
  assert(header_size <= InstanceKlass::header_size(), "bad header size");
  // If this assert fails, see comments in base_create_array_klass.
  header_size = InstanceKlass::header_size();
  int vtable_len = Universe::base_vtable_size();
#ifdef _LP64
  int size = header_size + align_object_offset(vtable_len);
#else
  int size = header_size + vtable_len;
#endif
  return align_object_size(size);
}


Klass* ArrayKlass::java_super() const {
  if (super() == NULL)  return NULL;  // bootstrap case
  // Array klasses have primary supertypes which are not reported to Java.
  // Example super chain:  String[][] -> Object[][] -> Object[] -> Object
  return SystemDictionary::Object_klass();
}


oop ArrayKlass::multi_allocate(int rank, jint* sizes, TRAPS) {
  ShouldNotReachHere();
  return NULL;
}

Method* ArrayKlass::uncached_lookup_method(Symbol* name, Symbol* signature) const {
  // There are no methods in an array klass but the super class (Object) has some
  assert(super(), "super klass must be present");
  return super()->uncached_lookup_method(name, signature);
}

ArrayKlass::ArrayKlass(Symbol* name) {
  set_name(name);

  set_super(Universe::is_bootstrapping() ? (Klass*)NULL : SystemDictionary::Object_klass());
  set_layout_helper(Klass::_lh_neutral_value);
  set_dimension(1);
  set_higher_dimension(NULL);
  set_lower_dimension(NULL);
  set_component_mirror(NULL);
  // Arrays don't add any new methods, so their vtable is the same size as
  // the vtable of klass Object.
  int vtable_size = Universe::base_vtable_size();
  set_vtable_length(vtable_size);
  set_is_cloneable(); // All arrays are considered to be cloneable (See JLS 20.1.5)
}


// Initialization of vtables and mirror object is done separatly from base_create_array_klass,
// since a GC can happen. At this point all instance variables of the ArrayKlass must be setup.
void ArrayKlass::complete_create_array_klass(ArrayKlass* k, KlassHandle super_klass, TRAPS) {
  ResourceMark rm(THREAD);
  k->initialize_supers(super_klass(), CHECK);
  k->vtable()->initialize_vtable(false, CHECK);
  java_lang_Class::create_mirror(k, Handle(NULL), CHECK);
}

GrowableArray<Klass*>* ArrayKlass::compute_secondary_supers(int num_extra_slots) {
  // interfaces = { cloneable_klass, serializable_klass };
  assert(num_extra_slots == 0, "sanity of primitive array type");
  // Must share this for correct bootstrapping!
  set_secondary_supers(Universe::the_array_interfaces_array());
  return NULL;
}

bool ArrayKlass::compute_is_subtype_of(Klass* k) {
  // An array is a subtype of Serializable, Clonable, and Object
  return    k == SystemDictionary::Object_klass()
         || k == SystemDictionary::Cloneable_klass()
         || k == SystemDictionary::Serializable_klass();
}


inline intptr_t* ArrayKlass::start_of_vtable() const {
  // all vtables start at the same place, that's why we use InstanceKlass::header_size here
  return ((intptr_t*)this) + InstanceKlass::header_size();
}


klassVtable* ArrayKlass::vtable() const {
  KlassHandle kh(Thread::current(), this);
  return new klassVtable(kh, start_of_vtable(), vtable_length() / vtableEntry::size());
}


objArrayOop ArrayKlass::allocate_arrayArray(int n, int length, TRAPS) {
  if (length < 0) {
    THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
  }
  if (length > arrayOopDesc::max_array_length(T_ARRAY)) {
    report_java_out_of_memory("Requested array size exceeds VM limit");
    JvmtiExport::post_array_size_exhausted();
    THROW_OOP_0(Universe::out_of_memory_error_array_size());
  }
  int size = objArrayOopDesc::object_size(length);
  Klass* k = array_klass(n+dimension(), CHECK_0);
  ArrayKlass* ak = ArrayKlass::cast(k);
  objArrayOop o =
    (objArrayOop)CollectedHeap::array_allocate(ak, size, length, CHECK_0);
  // initialization to NULL not necessary, area already cleared
  return o;
}

void ArrayKlass::array_klasses_do(void f(Klass* k, TRAPS), TRAPS) {
  Klass* k = this;
  // Iterate over this array klass and all higher dimensions
  while (k != NULL) {
    f(k, CHECK);
    k = ArrayKlass::cast(k)->higher_dimension();
  }
}

void ArrayKlass::array_klasses_do(void f(Klass* k)) {
  Klass* k = this;
  // Iterate over this array klass and all higher dimensions
  while (k != NULL) {
    f(k);
    k = ArrayKlass::cast(k)->higher_dimension();
  }
}

// GC support

void ArrayKlass::oops_do(OopClosure* cl) {
  Klass::oops_do(cl);

  cl->do_oop(adr_component_mirror());
}

// JVM support

jint ArrayKlass::compute_modifier_flags(TRAPS) const {
  return JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC;
}

// JVMTI support

jint ArrayKlass::jvmti_class_status() const {
  return JVMTI_CLASS_STATUS_ARRAY;
}

void ArrayKlass::remove_unshareable_info() {
  Klass::remove_unshareable_info();
  // Clear the java mirror
  set_component_mirror(NULL);
}

void ArrayKlass::restore_unshareable_info(TRAPS) {
  Klass::restore_unshareable_info(CHECK);
  // Klass recreates the component mirror also
}

// Printing

void ArrayKlass::print_on(outputStream* st) const {
  assert(is_klass(), "must be klass");
  Klass::print_on(st);
}

void ArrayKlass::print_value_on(outputStream* st) const {
  assert(is_klass(), "must be klass");
  for(int index = 0; index < dimension(); index++) {
    st->print("[]");
  }
}

void ArrayKlass::oop_print_on(oop obj, outputStream* st) {
  assert(obj->is_array(), "must be array");
  Klass::oop_print_on(obj, st);
  st->print_cr(" - length: %d", arrayOop(obj)->length());
}


// Verification

void ArrayKlass::verify_on(outputStream* st) {
  Klass::verify_on(st);

  if (component_mirror() != NULL) {
    guarantee(component_mirror()->klass() != NULL, "should have a class");
  }
}

void ArrayKlass::oop_verify_on(oop obj, outputStream* st) {
  guarantee(obj->is_array(), "must be array");
  arrayOop a = arrayOop(obj);
  guarantee(a->length() >= 0, "array with negative length?");
}
