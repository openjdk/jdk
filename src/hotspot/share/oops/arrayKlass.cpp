/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/metaspaceShared.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "jvmtifiles/jvmti.h"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/arrayKlass.inline.hpp"
#include "oops/arrayOop.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"

void* ArrayKlass::operator new(size_t size, ClassLoaderData* loader_data, size_t word_size, TRAPS) throw() {
  return Metaspace::allocate(loader_data, word_size, MetaspaceObj::ClassType, true, THREAD);
}

ArrayKlass::ArrayKlass() {
  assert(CDSConfig::is_dumping_static_archive() || CDSConfig::is_using_archive(), "only for CDS");
}

int ArrayKlass::static_size(int header_size) {
  // size of an array klass object
  assert(header_size <= InstanceKlass::header_size(), "bad header size");
  // If this assert fails, see comments in base_create_array_klass.
  header_size = InstanceKlass::header_size();
  int vtable_len = Universe::base_vtable_size();
  int size = header_size + vtable_len;
  return align_metadata_size(size);
}


InstanceKlass* ArrayKlass::java_super() const {
  if (super() == nullptr)  return nullptr;  // bootstrap case
  // Array klasses have primary supertypes which are not reported to Java.
  // Example super chain:  String[][] -> Object[][] -> Object[] -> Object
  return vmClasses::Object_klass();
}


oop ArrayKlass::multi_allocate(int rank, jint* sizes, TRAPS) {
  ShouldNotReachHere();
  return nullptr;
}

// find field according to JVM spec 5.4.3.2, returns the klass in which the field is defined
Klass* ArrayKlass::find_field(Symbol* name, Symbol* sig, fieldDescriptor* fd) const {
  // There are no fields in an array klass but look to the super class (Object)
  assert(super(), "super klass must be present");
  return super()->find_field(name, sig, fd);
}

Method* ArrayKlass::uncached_lookup_method(const Symbol* name,
                                           const Symbol* signature,
                                           OverpassLookupMode overpass_mode,
                                           PrivateLookupMode private_mode) const {
  // There are no methods in an array klass but the super class (Object) has some
  assert(super(), "super klass must be present");
  // Always ignore overpass methods in superclasses, although technically the
  // super klass of an array, (j.l.Object) should not have
  // any overpass methods present.
  return super()->uncached_lookup_method(name, signature, OverpassLookupMode::skip, private_mode);
}

ArrayKlass::ArrayKlass(Symbol* name, KlassKind kind) :
  Klass(kind),
  _dimension(1),
  _higher_dimension(nullptr),
  _lower_dimension(nullptr) {
  // Arrays don't add any new methods, so their vtable is the same size as
  // the vtable of klass Object.
  set_vtable_length(Universe::base_vtable_size());
  set_name(name);
  set_super(Universe::is_bootstrapping() ? nullptr : vmClasses::Object_klass());
  set_layout_helper(Klass::_lh_neutral_value);
  set_is_cloneable(); // All arrays are considered to be cloneable (See JLS 20.1.5)
  JFR_ONLY(INIT_ID(this);)
  log_array_class_load(this);
}


// Initialization of vtables and mirror object is done separately from base_create_array_klass,
// since a GC can happen. At this point all instance variables of the ArrayKlass must be setup.
void ArrayKlass::complete_create_array_klass(ArrayKlass* k, Klass* super_klass, ModuleEntry* module_entry, TRAPS) {
  k->initialize_supers(super_klass, nullptr, CHECK);
  k->vtable().initialize_vtable();

  // During bootstrapping, before java.base is defined, the module_entry may not be present yet.
  // These classes will be put on a fixup list and their module fields will be patched once
  // java.base is defined.
  assert((module_entry != nullptr) || ((module_entry == nullptr) && !ModuleEntryTable::javabase_defined()),
         "module entry not available post " JAVA_BASE_NAME " definition");
  oop module_oop = (module_entry != nullptr) ? module_entry->module_oop() : (oop)nullptr;
  java_lang_Class::create_mirror(k, Handle(THREAD, k->class_loader()), Handle(THREAD, module_oop), Handle(), Handle(), CHECK);
}

ArrayKlass* ArrayKlass::array_klass(int n, TRAPS) {

  assert(dimension() <= n, "check order of chain");
  int dim = dimension();
  if (dim == n) return this;

  // lock-free read needs acquire semantics
  if (higher_dimension_acquire() == nullptr) {

    // Ensure atomic creation of higher dimensions
    RecursiveLocker rl(MultiArray_lock, THREAD);

    if (higher_dimension() == nullptr) {
      // Create multi-dim klass object and link them together
      ObjArrayKlass* ak =
          ObjArrayKlass::allocate_objArray_klass(class_loader_data(), dim + 1, this, CHECK_NULL);
      // use 'release' to pair with lock-free load
      release_set_higher_dimension(ak);
      assert(ak->lower_dimension() == this, "lower dimension mismatch");
    }
  }

  ObjArrayKlass* ak = higher_dimension();
  assert(ak != nullptr, "should be set");
  THREAD->check_possible_safepoint();
  return ak->array_klass(n, THREAD);
}

ArrayKlass* ArrayKlass::array_klass_or_null(int n) {

  assert(dimension() <= n, "check order of chain");
  int dim = dimension();
  if (dim == n) return this;

  // lock-free read needs acquire semantics
  if (higher_dimension_acquire() == nullptr) {
    return nullptr;
  }

  ObjArrayKlass *ak = higher_dimension();
  return ak->array_klass_or_null(n);
}

ArrayKlass* ArrayKlass::array_klass(TRAPS) {
  return array_klass(dimension() +  1, THREAD);
}

ArrayKlass* ArrayKlass::array_klass_or_null() {
  return array_klass_or_null(dimension() +  1);
}


GrowableArray<Klass*>* ArrayKlass::compute_secondary_supers(int num_extra_slots,
                                                            Array<InstanceKlass*>* transitive_interfaces) {
  // interfaces = { cloneable_klass, serializable_klass };
  assert(num_extra_slots == 0, "sanity of primitive array type");
  assert(transitive_interfaces == nullptr, "sanity");
  // Must share this for correct bootstrapping!
  set_secondary_supers(Universe::the_array_interfaces_array(),
                       Universe::the_array_interfaces_bitmap());
  return nullptr;
}

objArrayOop ArrayKlass::allocate_arrayArray(int n, int length, TRAPS) {
  check_array_allocation_length(length, arrayOopDesc::max_array_length(T_ARRAY), CHECK_NULL);
  size_t size = objArrayOopDesc::object_size(length);
  ArrayKlass* ak = array_klass(n + dimension(), CHECK_NULL);
  objArrayOop o = (objArrayOop)Universe::heap()->array_allocate(ak, size, length,
                                                                /* do_zero */ true, CHECK_NULL);
  // initialization to null not necessary, area already cleared
  return o;
}

// JVMTI support

jint ArrayKlass::jvmti_class_status() const {
  return JVMTI_CLASS_STATUS_ARRAY;
}

void ArrayKlass::metaspace_pointers_do(MetaspaceClosure* it) {
  Klass::metaspace_pointers_do(it);

  ResourceMark rm;
  log_trace(aot)("Iter(ArrayKlass): %p (%s)", this, external_name());

  // need to cast away volatile
  it->push((Klass**)&_higher_dimension);
  it->push((Klass**)&_lower_dimension);
}

#if INCLUDE_CDS
void ArrayKlass::remove_unshareable_info() {
  Klass::remove_unshareable_info();
  if (_higher_dimension != nullptr) {
    ArrayKlass *ak = higher_dimension();
    ak->remove_unshareable_info();
  }
}

void ArrayKlass::remove_java_mirror() {
  Klass::remove_java_mirror();
  if (_higher_dimension != nullptr) {
    ArrayKlass *ak = higher_dimension();
    ak->remove_java_mirror();
  }
}

void ArrayKlass::restore_unshareable_info(ClassLoaderData* loader_data, Handle protection_domain, TRAPS) {
  Klass::restore_unshareable_info(loader_data, protection_domain, CHECK);
  // Klass recreates the component mirror also

  if (_higher_dimension != nullptr) {
    ArrayKlass *ak = higher_dimension();
    log_array_class_load(ak);
    ak->restore_unshareable_info(loader_data, protection_domain, CHECK);
  }
}

void ArrayKlass::cds_print_value_on(outputStream* st) const {
  assert(is_klass(), "must be klass");
  st->print("      - array: %s", internal_name());
  if (_higher_dimension != nullptr) {
    ArrayKlass* ak = higher_dimension();
    st->cr();
    ak->cds_print_value_on(st);
  }
}
#endif // INCLUDE_CDS

void ArrayKlass::log_array_class_load(Klass* k) {
  LogTarget(Debug, class, load, array) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ResourceMark rm;
    ls.print("%s", k->name()->as_klass_external_name());
    if (MetaspaceShared::is_shared_dynamic((void*)k)) {
      ls.print(" source: shared objects file (top)");
    } else if (MetaspaceShared::is_shared_static((void*)k)) {
      ls.print(" source: shared objects file");
    }
    ls.cr();
  }
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
}

void ArrayKlass::oop_verify_on(oop obj, outputStream* st) {
  guarantee(obj->is_array(), "must be array");
  arrayOop a = arrayOop(obj);
  guarantee(a->length() >= 0, "array with negative length?");
}
