/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/arrayKlassKlass.hpp"
#include "oops/arrayOop.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"

int arrayKlass::object_size(int header_size) const {
  // size of an array klass object
  assert(header_size <= instanceKlass::header_size(), "bad header size");
  // If this assert fails, see comments in base_create_array_klass.
  header_size = instanceKlass::header_size();
#ifdef _LP64
  int size = header_size + align_object_offset(vtable_length());
#else
  int size = header_size + vtable_length();
#endif
  return align_object_size(size);
}


klassOop arrayKlass::java_super() const {
  if (super() == NULL)  return NULL;  // bootstrap case
  // Array klasses have primary supertypes which are not reported to Java.
  // Example super chain:  String[][] -> Object[][] -> Object[] -> Object
  return SystemDictionary::Object_klass();
}


oop arrayKlass::multi_allocate(int rank, jint* sizes, TRAPS) {
  ShouldNotReachHere();
  return NULL;
}

methodOop arrayKlass::uncached_lookup_method(symbolOop name, symbolOop signature) const {
  // There are no methods in an array klass but the super class (Object) has some
  assert(super(), "super klass must be present");
  return Klass::cast(super())->uncached_lookup_method(name, signature);
}


arrayKlassHandle arrayKlass::base_create_array_klass(
const Klass_vtbl& cplusplus_vtbl, int header_size, KlassHandle klass, TRAPS) {
  // Allocation
  // Note: because the Java vtable must start at the same offset in all klasses,
  // we must insert filler fields into arrayKlass to make it the same size as instanceKlass.
  // If this assert fails, add filler to instanceKlass to make it bigger.
  assert(header_size <= instanceKlass::header_size(),
         "array klasses must be same size as instanceKlass");
  header_size = instanceKlass::header_size();
  // Arrays don't add any new methods, so their vtable is the same size as
  // the vtable of klass Object.
  int vtable_size = Universe::base_vtable_size();
  arrayKlassHandle k;
  KlassHandle base_klass = Klass::base_create_klass(klass,
                                                 header_size + vtable_size,
                                                 cplusplus_vtbl, CHECK_(k));

  // No safepoint should be possible until the handle's
  // target below becomes parsable
  No_Safepoint_Verifier no_safepoint;
  k = arrayKlassHandle(THREAD, base_klass());

  assert(!k()->is_parsable(), "not expecting parsability yet.");
  k->set_super(Universe::is_bootstrapping() ? (klassOop)NULL : SystemDictionary::Object_klass());
  k->set_layout_helper(Klass::_lh_neutral_value);
  k->set_dimension(1);
  k->set_higher_dimension(NULL);
  k->set_lower_dimension(NULL);
  k->set_component_mirror(NULL);
  k->set_vtable_length(vtable_size);
  k->set_is_cloneable(); // All arrays are considered to be cloneable (See JLS 20.1.5)

  assert(k()->is_parsable(), "should be parsable here.");
  // Make sure size calculation is right
  assert(k()->size() == align_object_size(header_size + vtable_size), "wrong size for object");

  return k;
}


// Initialization of vtables and mirror object is done separatly from base_create_array_klass,
// since a GC can happen. At this point all instance variables of the arrayKlass must be setup.
void arrayKlass::complete_create_array_klass(arrayKlassHandle k, KlassHandle super_klass, TRAPS) {
  ResourceMark rm(THREAD);
  k->initialize_supers(super_klass(), CHECK);
  k->vtable()->initialize_vtable(false, CHECK);
  java_lang_Class::create_mirror(k, CHECK);
}

objArrayOop arrayKlass::compute_secondary_supers(int num_extra_slots, TRAPS) {
  // interfaces = { cloneable_klass, serializable_klass };
  assert(num_extra_slots == 0, "sanity of primitive array type");
  // Must share this for correct bootstrapping!
  return Universe::the_array_interfaces_array();
}

bool arrayKlass::compute_is_subtype_of(klassOop k) {
  // An array is a subtype of Serializable, Clonable, and Object
  return    k == SystemDictionary::Object_klass()
         || k == SystemDictionary::Cloneable_klass()
         || k == SystemDictionary::Serializable_klass();
}


inline intptr_t* arrayKlass::start_of_vtable() const {
  // all vtables start at the same place, that's why we use instanceKlass::header_size here
  return ((intptr_t*)as_klassOop()) + instanceKlass::header_size();
}


klassVtable* arrayKlass::vtable() const {
  KlassHandle kh(Thread::current(), as_klassOop());
  return new klassVtable(kh, start_of_vtable(), vtable_length() / vtableEntry::size());
}


objArrayOop arrayKlass::allocate_arrayArray(int n, int length, TRAPS) {
  if (length < 0) {
    THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
  }
  if (length > arrayOopDesc::max_array_length(T_ARRAY)) {
    report_java_out_of_memory("Requested array size exceeds VM limit");
    THROW_OOP_0(Universe::out_of_memory_error_array_size());
  }
  int size = objArrayOopDesc::object_size(length);
  klassOop k = array_klass(n+dimension(), CHECK_0);
  arrayKlassHandle ak (THREAD, k);
  objArrayOop o =
    (objArrayOop)CollectedHeap::array_allocate(ak, size, length, CHECK_0);
  // initialization to NULL not necessary, area already cleared
  return o;
}


void arrayKlass::array_klasses_do(void f(klassOop k)) {
  klassOop k = as_klassOop();
  // Iterate over this array klass and all higher dimensions
  while (k != NULL) {
    f(k);
    k = arrayKlass::cast(k)->higher_dimension();
  }
}


void arrayKlass::with_array_klasses_do(void f(klassOop k)) {
  array_klasses_do(f);
}

// JVM support

jint arrayKlass::compute_modifier_flags(TRAPS) const {
  return JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC;
}

// JVMTI support

jint arrayKlass::jvmti_class_status() const {
  return JVMTI_CLASS_STATUS_ARRAY;
}

// Printing

void arrayKlass::oop_print_on(oop obj, outputStream* st) {
  assert(obj->is_array(), "must be array");
  Klass::oop_print_on(obj, st);
  st->print_cr(" - length: %d", arrayOop(obj)->length());
}

// Verification

void arrayKlass::oop_verify_on(oop obj, outputStream* st) {
  guarantee(obj->is_array(), "must be array");
  arrayOop a = arrayOop(obj);
  guarantee(a->length() >= 0, "array with negative length?");
}
