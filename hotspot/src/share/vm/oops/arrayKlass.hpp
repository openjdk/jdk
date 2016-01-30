/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_ARRAYKLASS_HPP
#define SHARE_VM_OOPS_ARRAYKLASS_HPP

#include "memory/universe.hpp"
#include "oops/klass.hpp"

class fieldDescriptor;
class klassVtable;

// ArrayKlass is the abstract baseclass for all array classes

class ArrayKlass: public Klass {
  friend class VMStructs;
 private:
  int      _dimension;         // This is n'th-dimensional array.
  Klass* volatile _higher_dimension;  // Refers the (n+1)'th-dimensional array (if present).
  Klass* volatile _lower_dimension;   // Refers the (n-1)'th-dimensional array (if present).
  int      _vtable_len;        // size of vtable for this klass

 protected:
  // Constructors
  // The constructor with the Symbol argument does the real array
  // initialization, the other is a dummy
  ArrayKlass(Symbol* name);
  ArrayKlass() { assert(DumpSharedSpaces || UseSharedSpaces, "only for cds"); }

 public:
  // Testing operation
  DEBUG_ONLY(bool is_array_klass_slow() const { return true; })

  // Instance variables
  int dimension() const                 { return _dimension;      }
  void set_dimension(int dimension)     { _dimension = dimension; }

  Klass* higher_dimension() const     { return _higher_dimension; }
  void set_higher_dimension(Klass* k) { _higher_dimension = k; }
  Klass** adr_higher_dimension()      { return (Klass**)&this->_higher_dimension;}

  Klass* lower_dimension() const      { return _lower_dimension; }
  void set_lower_dimension(Klass* k)  { _lower_dimension = k; }
  Klass** adr_lower_dimension()       { return (Klass**)&this->_lower_dimension;}

  // offset of first element, including any padding for the sake of alignment
  int  array_header_in_bytes() const    { return layout_helper_header_size(layout_helper()); }
  int  log2_element_size() const        { return layout_helper_log2_element_size(layout_helper()); }
  // type of elements (T_OBJECT for both oop arrays and array-arrays)
  BasicType element_type() const        { return layout_helper_element_type(layout_helper()); }

  virtual Klass* java_super() const;//{ return SystemDictionary::Object_klass(); }

  // Allocation
  // Sizes points to the first dimension of the array, subsequent dimensions
  // are always in higher memory.  The callers of these set that up.
  virtual oop multi_allocate(int rank, jint* sizes, TRAPS);
  objArrayOop allocate_arrayArray(int n, int length, TRAPS);

  // find field according to JVM spec 5.4.3.2, returns the klass in which the field is defined
  Klass* find_field(Symbol* name, Symbol* sig, fieldDescriptor* fd) const;

  // Lookup operations
  Method* uncached_lookup_method(const Symbol* name,
                                 const Symbol* signature,
                                 OverpassLookupMode overpass_mode) const;

  static ArrayKlass* cast(Klass* k) {
    return const_cast<ArrayKlass*>(cast(const_cast<const Klass*>(k)));
  }

  static const ArrayKlass* cast(const Klass* k) {
    assert(k->is_array_klass(), "cast to ArrayKlass");
    return static_cast<const ArrayKlass*>(k);
  }

  GrowableArray<Klass*>* compute_secondary_supers(int num_extra_slots);
  bool compute_is_subtype_of(Klass* k);

  // Sizing
  static int header_size()                 { return sizeof(ArrayKlass)/wordSize; }
  static int static_size(int header_size);

#if INCLUDE_SERVICES
  virtual void collect_statistics(KlassSizeStats *sz) const {
    Klass::collect_statistics(sz);
    // Do nothing for now, but remember to modify if you add new
    // stuff to ArrayKlass.
  }
#endif

  // Java vtable
  klassVtable* vtable() const;             // return new klassVtable
  int  vtable_length() const               { return _vtable_len; }
  static int base_vtable_length()          { return Universe::base_vtable_size(); }
  void set_vtable_length(int len)          { assert(len == base_vtable_length(), "bad length"); _vtable_len = len; }
 protected:
  inline intptr_t* start_of_vtable() const;

 public:
  // Iterators
  void array_klasses_do(void f(Klass* k));
  void array_klasses_do(void f(Klass* k, TRAPS), TRAPS);

  // Return a handle.
  static void     complete_create_array_klass(ArrayKlass* k, KlassHandle super_klass, TRAPS);


  // jvm support
  jint compute_modifier_flags(TRAPS) const;

  // JVMTI support
  jint jvmti_class_status() const;

  // CDS support - remove and restore oops from metadata. Oops are not shared.
  virtual void remove_unshareable_info();
  virtual void restore_unshareable_info(ClassLoaderData* loader_data, Handle protection_domain, TRAPS);

  // Printing
  void print_on(outputStream* st) const;
  void print_value_on(outputStream* st) const;

  void oop_print_on(oop obj, outputStream* st);

  // Verification
  void verify_on(outputStream* st);

  void oop_verify_on(oop obj, outputStream* st);
};

// Array oop iteration macros for declarations.
// Used to generate the declarations in the *ArrayKlass header files.

#define OOP_OOP_ITERATE_DECL_RANGE(OopClosureType, nv_suffix)                                   \
  void oop_oop_iterate_range##nv_suffix(oop obj, OopClosureType* closure, int start, int end);

#if INCLUDE_ALL_GCS
// Named NO_BACKWARDS because the definition used by *ArrayKlass isn't reversed, see below.
#define OOP_OOP_ITERATE_DECL_NO_BACKWARDS(OopClosureType, nv_suffix)            \
  void oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* closure);
#endif // INCLUDE_ALL_GCS


// Array oop iteration macros for definitions.
// Used to generate the definitions in the *ArrayKlass.inline.hpp files.

#define OOP_OOP_ITERATE_DEFN_RANGE(KlassType, OopClosureType, nv_suffix)                                  \
                                                                                                          \
void KlassType::oop_oop_iterate_range##nv_suffix(oop obj, OopClosureType* closure, int start, int end) {  \
  oop_oop_iterate_range<nvs_to_bool(nv_suffix)>(obj, closure, start, end);                                \
}

#if INCLUDE_ALL_GCS
#define OOP_OOP_ITERATE_DEFN_NO_BACKWARDS(KlassType, OopClosureType, nv_suffix)           \
void KlassType::oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* closure) {  \
  /* No reverse implementation ATM. */                                                    \
  oop_oop_iterate<nvs_to_bool(nv_suffix)>(obj, closure);                                  \
}
#else
#define OOP_OOP_ITERATE_DEFN_NO_BACKWARDS(KlassType, OopClosureType, nv_suffix)
#endif

#endif // SHARE_VM_OOPS_ARRAYKLASS_HPP
