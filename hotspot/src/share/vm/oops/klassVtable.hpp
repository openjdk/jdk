/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_KLASSVTABLE_HPP
#define SHARE_VM_OOPS_KLASSVTABLE_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/handles.hpp"
#include "utilities/growableArray.hpp"

// A klassVtable abstracts the variable-length vtable that is embedded in InstanceKlass
// and ArrayKlass.  klassVtable objects are used just as convenient transient accessors to the vtable,
// not to actually hold the vtable data.
// Note: the klassVtable should not be accessed before the class has been verified
// (until that point, the vtable is uninitialized).

// Currently a klassVtable contains a direct reference to the vtable data, and is therefore
// not preserved across GCs.

class vtableEntry;

class klassVtable : public ResourceObj {
  KlassHandle  _klass;            // my klass
  int          _tableOffset;      // offset of start of vtable data within klass
  int          _length;           // length of vtable (number of entries)
#ifndef PRODUCT
  int          _verify_count;     // to make verify faster
#endif

  // Ordering important, so greater_than (>) can be used as an merge operator.
  enum AccessType {
    acc_private         = 0,
    acc_package_private = 1,
    acc_publicprotected = 2
  };

 public:
  klassVtable(KlassHandle h_klass, void* base, int length) : _klass(h_klass) {
    _tableOffset = (address)base - (address)h_klass(); _length = length;
  }

  // accessors
  vtableEntry* table() const      { return (vtableEntry*)(address(_klass()) + _tableOffset); }
  KlassHandle klass() const       { return _klass;  }
  int length() const              { return _length; }
  inline Method* method_at(int i) const;
  inline Method* unchecked_method_at(int i) const;
  inline Method** adr_method_at(int i) const;

  // searching; all methods return -1 if not found
  int index_of(Method* m) const                         { return index_of(m, _length); }
  int index_of_miranda(Symbol* name, Symbol* signature);

  void initialize_vtable(bool checkconstraints, TRAPS);   // initialize vtable of a new klass

  // CDS/RedefineClasses support - clear vtables so they can be reinitialized
  // at dump time.  Clearing gives us an easy way to tell if the vtable has
  // already been reinitialized at dump time (see dump.cpp).  Vtables can
  // be initialized at run time by RedefineClasses so dumping the right order
  // is necessary.
  void clear_vtable();
  bool is_initialized();

  // computes vtable length (in words) and the number of miranda methods
  static void compute_vtable_size_and_num_mirandas(
      int* vtable_length, int* num_new_mirandas,
      GrowableArray<Method*>* all_mirandas, Klass* super,
      Array<Method*>* methods, AccessFlags class_flags, Handle classloader,
      Symbol* classname, Array<Klass*>* local_interfaces, TRAPS);

#if INCLUDE_JVMTI
  // RedefineClasses() API support:
  // If any entry of this vtable points to any of old_methods,
  // replace it with the corresponding new_method.
  // trace_name_printed is set to true if the current call has
  // printed the klass name so that other routines in the adjust_*
  // group don't print the klass name.
  bool adjust_default_method(int vtable_index, Method* old_method, Method* new_method);
  void adjust_method_entries(InstanceKlass* holder, bool * trace_name_printed);
  bool check_no_old_or_obsolete_entries();
  void dump_vtable();
#endif // INCLUDE_JVMTI

  // Debugging code
  void print()                                              PRODUCT_RETURN;
  void verify(outputStream* st, bool force = false);
  static void print_statistics()                            PRODUCT_RETURN;

 protected:
  friend class vtableEntry;
 private:
  enum { VTABLE_TRANSITIVE_OVERRIDE_VERSION = 51 } ;
  void copy_vtable_to(vtableEntry* start);
  int  initialize_from_super(KlassHandle super);
  int  index_of(Method* m, int len) const; // same as index_of, but search only up to len
  void put_method_at(Method* m, int index);
  static bool needs_new_vtable_entry(methodHandle m, Klass* super, Handle classloader, Symbol* classname, AccessFlags access_flags, TRAPS);

  bool update_inherited_vtable(InstanceKlass* klass, methodHandle target_method, int super_vtable_len, int default_index, bool checkconstraints, TRAPS);
 InstanceKlass* find_transitive_override(InstanceKlass* initialsuper, methodHandle target_method, int vtable_index,
                                         Handle target_loader, Symbol* target_classname, Thread* THREAD);

  // support for miranda methods
  bool is_miranda_entry_at(int i);
  int fill_in_mirandas(int initialized);
  static bool is_miranda(Method* m, Array<Method*>* class_methods,
                         Array<Method*>* default_methods, Klass* super);
  static void add_new_mirandas_to_lists(
      GrowableArray<Method*>* new_mirandas,
      GrowableArray<Method*>* all_mirandas,
      Array<Method*>* current_interface_methods,
      Array<Method*>* class_methods,
      Array<Method*>* default_methods,
      Klass* super);
  static void get_mirandas(
      GrowableArray<Method*>* new_mirandas,
      GrowableArray<Method*>* all_mirandas, Klass* super,
      Array<Method*>* class_methods,
      Array<Method*>* default_methods,
      Array<Klass*>* local_interfaces);
  void verify_against(outputStream* st, klassVtable* vt, int index);
  inline InstanceKlass* ik() const;
};


// private helper class for klassVtable
// description of entry points:
//    destination is interpreted:
//      from_compiled_code_entry_point -> c2iadapter
//      from_interpreter_entry_point   -> interpreter entry point
//    destination is compiled:
//      from_compiled_code_entry_point -> nmethod entry point
//      from_interpreter_entry_point   -> i2cadapter
class vtableEntry VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;

 public:
  // size in words
  static int size() {
    return sizeof(vtableEntry) / sizeof(HeapWord);
  }
  static int method_offset_in_bytes() { return offset_of(vtableEntry, _method); }
  Method* method() const    { return _method; }

 private:
  Method* _method;
  void set(Method* method)  { assert(method != NULL, "use clear"); _method = method; }
  void clear()                { _method = NULL; }
  void print()                                        PRODUCT_RETURN;
  void verify(klassVtable* vt, outputStream* st);

  friend class klassVtable;
};


inline Method* klassVtable::method_at(int i) const {
  assert(i >= 0 && i < _length, "index out of bounds");
  assert(table()[i].method() != NULL, "should not be null");
  assert(((Metadata*)table()[i].method())->is_method(), "should be method");
  return table()[i].method();
}

inline Method* klassVtable::unchecked_method_at(int i) const {
  assert(i >= 0 && i < _length, "index out of bounds");
  return table()[i].method();
}

inline Method** klassVtable::adr_method_at(int i) const {
  // Allow one past the last entry to be referenced; useful for loop bounds.
  assert(i >= 0 && i <= _length, "index out of bounds");
  return (Method**)(address(table() + i) + vtableEntry::method_offset_in_bytes());
}

// --------------------------------------------------------------------------------
class klassItable;
class itableMethodEntry;

class itableOffsetEntry VALUE_OBJ_CLASS_SPEC {
 private:
  Klass* _interface;
  int      _offset;
 public:
  Klass* interface_klass() const { return _interface; }
  int      offset() const          { return _offset; }

  static itableMethodEntry* method_entry(Klass* k, int offset) { return (itableMethodEntry*)(((address)k) + offset); }
  itableMethodEntry* first_method_entry(Klass* k)              { return method_entry(k, _offset); }

  void initialize(Klass* interf, int offset) { _interface = interf; _offset = offset; }

  // Static size and offset accessors
  static int size()                       { return sizeof(itableOffsetEntry) / HeapWordSize; }    // size in words
  static int interface_offset_in_bytes()  { return offset_of(itableOffsetEntry, _interface); }
  static int offset_offset_in_bytes()     { return offset_of(itableOffsetEntry, _offset); }

  friend class klassItable;
};


class itableMethodEntry VALUE_OBJ_CLASS_SPEC {
 private:
  Method* _method;

 public:
  Method* method() const { return _method; }

  void clear()             { _method = NULL; }

  void initialize(Method* method);

  // Static size and offset accessors
  static int size()                         { return sizeof(itableMethodEntry) / HeapWordSize; }  // size in words
  static int method_offset_in_bytes()       { return offset_of(itableMethodEntry, _method); }

  friend class klassItable;
};

//
// Format of an itable
//
//    ---- offset table ---
//    Klass* of interface 1             \
//    offset to vtable from start of oop  / offset table entry
//    ...
//    Klass* of interface n             \
//    offset to vtable from start of oop  / offset table entry
//    --- vtable for interface 1 ---
//    Method*                             \
//    compiler entry point                / method table entry
//    ...
//    Method*                             \
//    compiler entry point                / method table entry
//    -- vtable for interface 2 ---
//    ...
//
class klassItable : public ResourceObj {
 private:
  instanceKlassHandle  _klass;             // my klass
  int                  _table_offset;      // offset of start of itable data within klass (in words)
  int                  _size_offset_table; // size of offset table (in itableOffset entries)
  int                  _size_method_table; // size of methodtable (in itableMethodEntry entries)

  void initialize_itable_for_interface(int method_table_offset, KlassHandle interf_h, bool checkconstraints, TRAPS);
 public:
  klassItable(instanceKlassHandle klass);

  itableOffsetEntry* offset_entry(int i) { assert(0 <= i && i <= _size_offset_table, "index out of bounds");
                                           return &((itableOffsetEntry*)vtable_start())[i]; }

  itableMethodEntry* method_entry(int i) { assert(0 <= i && i <= _size_method_table, "index out of bounds");
                                           return &((itableMethodEntry*)method_start())[i]; }

  int size_offset_table()                { return _size_offset_table; }

  // Initialization
  void initialize_itable(bool checkconstraints, TRAPS);

  // Updates
  void initialize_with_method(Method* m);

#if INCLUDE_JVMTI
  // RedefineClasses() API support:
  // if any entry of this itable points to any of old_methods,
  // replace it with the corresponding new_method.
  // trace_name_printed is set to true if the current call has
  // printed the klass name so that other routines in the adjust_*
  // group don't print the klass name.
  void adjust_method_entries(InstanceKlass* holder, bool * trace_name_printed);
  bool check_no_old_or_obsolete_entries();
  void dump_itable();
#endif // INCLUDE_JVMTI

  // Setup of itable
  static int assign_itable_indices_for_interface(Klass* klass);
  static int method_count_for_interface(Klass* klass);
  static int compute_itable_size(Array<Klass*>* transitive_interfaces);
  static void setup_itable_offset_table(instanceKlassHandle klass);

  // Resolving of method to index
  static Method* method_for_itable_index(Klass* klass, int itable_index);

  // Debugging/Statistics
  static void print_statistics() PRODUCT_RETURN;
 private:
  intptr_t* vtable_start() const { return ((intptr_t*)_klass()) + _table_offset; }
  intptr_t* method_start() const { return vtable_start() + _size_offset_table * itableOffsetEntry::size(); }

  // Helper methods
  static int  calc_itable_size(int num_interfaces, int num_methods) { return (num_interfaces * itableOffsetEntry::size()) + (num_methods * itableMethodEntry::size()); }

  // Statistics
  NOT_PRODUCT(static int  _total_classes;)   // Total no. of classes with itables
  NOT_PRODUCT(static long _total_size;)      // Total no. of bytes used for itables

  static void update_stats(int size) PRODUCT_RETURN NOT_PRODUCT({ _total_classes++; _total_size += size; })
};

#endif // SHARE_VM_OOPS_KLASSVTABLE_HPP
