/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_PACKAGEENTRY_HPP
#define SHARE_VM_CLASSFILE_PACKAGEENTRY_HPP

#include "classfile/moduleEntry.hpp"
#include "oops/symbol.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/hashtable.hpp"

// A PackageEntry basically represents a Java package.  It contains:
//   - Symbol* containing the package's name.
//   - ModuleEntry* for this package's containing module.
//   - a flag indicating if package is exported, either qualifiedly or
//     unqualifiedly.
//   - a flag indicating if this package is exported to all unnamed modules.
//   - a growable array containing other module entries that this
//     package is exported to.
//
// Packages that are:
//   - not exported:        _qualified_exports = NULL  && _is_exported is false
//   - qualified exports:   (_qualified_exports != NULL || _is_exported_allUnnamed is true) && _is_exported is true
//   - unqualified exports: (_qualified_exports = NULL && _is_exported_allUnnamed is false) && _is_exported is true
//
// The Mutex Module_lock is shared between ModuleEntry and PackageEntry, to lock either
// data structure.
class PackageEntry : public HashtableEntry<Symbol*, mtClass> {
private:
  ModuleEntry* _module;
  // Used to indicate for packages with classes loaded by the boot loader that
  // a class in that package has been loaded.  And, for packages with classes
  // loaded by the boot loader from -Xbootclasspath/a in an unnamed module, it
  // indicates from which class path entry.
  s2 _classpath_index;
  bool _is_exported;
  bool _is_exported_allUnnamed;
  GrowableArray<ModuleEntry*>* _exported_pending_delete; // transitioned from qualified to unqualified, delete at safepoint
  GrowableArray<ModuleEntry*>* _qualified_exports;
  TRACE_DEFINE_TRACE_ID_FIELD;

  // Initial size of a package entry's list of qualified exports.
  enum {QUAL_EXP_SIZE = 43};

public:
  void init() {
    _module = NULL;
    _classpath_index = -1;
    _is_exported = false;
    _is_exported_allUnnamed = false;
    _exported_pending_delete = NULL;
    _qualified_exports = NULL;
  }

  // package name
  Symbol*            name() const               { return literal(); }
  void               set_name(Symbol* n)        { set_literal(n); }

  // the module containing the package definition
  ModuleEntry*       module() const             { return _module; }
  void               set_module(ModuleEntry* m) { _module = m; }

  // package's export state
  bool is_exported() const { return _is_exported; } // qualifiedly or unqualifiedly exported
  bool is_qual_exported() const {
    return (_is_exported && (_qualified_exports != NULL || _is_exported_allUnnamed));
  }
  bool is_unqual_exported() const {
    return (_is_exported && (_qualified_exports == NULL && !_is_exported_allUnnamed));
  }
  void set_unqual_exported() {
    _is_exported = true;
    _is_exported_allUnnamed = false;
    _qualified_exports = NULL;
  }
  bool exported_pending_delete() const     { return (_exported_pending_delete != NULL); }

  void set_exported(bool e)                { _is_exported = e; }
  void set_exported(ModuleEntry* m);

  void set_is_exported_allUnnamed() {
    if (!is_unqual_exported()) {
     _is_exported_allUnnamed = true;
     _is_exported = true;
    }
  }
  bool is_exported_allUnnamed() const {
    assert(_is_exported || !_is_exported_allUnnamed,
           "is_allUnnamed set without is_exported being set");
    return _is_exported_allUnnamed;
  }

  void set_classpath_index(s2 classpath_index) {
    _classpath_index = classpath_index;
  }
  s2 classpath_index() const { return _classpath_index; }

  bool has_loaded_class() const { return _classpath_index != -1; }

  // returns true if the package is defined in the unnamed module
  bool in_unnamed_module() const  { return !_module->is_named(); }

  // returns true if the package specifies m as a qualified export
  bool is_qexported_to(ModuleEntry* m) const;

  // add the module to the package's qualified exports
  void add_qexport(ModuleEntry* m);

  PackageEntry* next() const {
    return (PackageEntry*)HashtableEntry<Symbol*, mtClass>::next();
  }

  PackageEntry** next_addr() {
    return (PackageEntry**)HashtableEntry<Symbol*, mtClass>::next_addr();
  }

  // iteration of qualified exports
  void package_exports_do(ModuleClosure* const f);

  TRACE_DEFINE_TRACE_ID_METHODS;

  // Purge dead weak references out of exported list when any given class loader is unloaded.
  void purge_qualified_exports();
  void delete_qualified_exports();

  void print() PRODUCT_RETURN;
  void verify();
};

// The PackageEntryTable is a Hashtable containing a list of all packages defined
// by a particular class loader.  Each package is represented as a PackageEntry node.
// The PackageEntryTable's lookup is lock free.
//
class PackageEntryTable : public Hashtable<Symbol*, mtClass> {
  friend class VMStructs;
public:
  enum Constants {
    _packagetable_entry_size = 1009  // number of entries in package entry table
  };

private:
  PackageEntry* new_entry(unsigned int hash, Symbol* name, ModuleEntry* module);
  void add_entry(int index, PackageEntry* new_entry);

  int entry_size() const { return BasicHashtable<mtClass>::entry_size(); }

  PackageEntry** bucket_addr(int i) {
    return (PackageEntry**)Hashtable<Symbol*, mtClass>::bucket_addr(i);
  }

  static unsigned int compute_hash(Symbol* name) { return (unsigned int)(name->identity_hash()); }
  int index_for(Symbol* name) const { return hash_to_index(compute_hash(name)); }

public:
  PackageEntryTable(int table_size);
  ~PackageEntryTable();

  PackageEntry* bucket(int i) {
    return (PackageEntry*)Hashtable<Symbol*, mtClass>::bucket(i);
  }

  // Create package in loader's package entry table and return the entry.
  // If entry already exists, return null.  Assume Module lock was taken by caller.
  PackageEntry* locked_create_entry_or_null(Symbol* name, ModuleEntry* module);

  // lookup Package with loader's package entry table, if not found add
  PackageEntry* lookup(Symbol* name, ModuleEntry* module);

  // Only lookup Package within loader's package entry table.  The table read is lock-free.
  PackageEntry* lookup_only(Symbol* Package);

  void verify_javabase_packages(GrowableArray<Symbol*> *pkg_list);

  // purge dead weak references out of exported list
  void purge_all_package_exports();

  void print() PRODUCT_RETURN;
  void verify();
};

#endif // SHARE_VM_CLASSFILE_PACKAGEENTRY_HPP
