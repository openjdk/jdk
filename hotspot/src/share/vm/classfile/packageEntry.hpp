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
#include "utilities/ostream.hpp"

// A PackageEntry basically represents a Java package.  It contains:
//   - Symbol* containing the package's name.
//   - ModuleEntry* for this package's containing module.
//   - a flag indicating if package is exported unqualifiedly
//   - a flag indicating if this package is exported to all unnamed modules.
//   - a growable array containing other module entries that this
//     package is exported to.
//
// Packages can be exported in the following 3 ways:
//   - not exported:        the package does not have qualified or unqualified exports.
//   - qualified exports:   the package has been explicitly qualified to at least
//                            one particular module or has been qualifiedly exported
//                            to all unnamed modules.
//                            Note: _is_exported_allUnnamed is a form of a qualified
//                            export. It is equivalent to the package being
//                            explicitly exported to all current and future unnamed modules.
//   - unqualified exports: the package is exported to all modules.
//
// A package can transition from:
//   - being not exported, to being exported either in a qualified or unqualified manner
//   - being qualifiedly exported, to unqualifiedly exported. Its exported scope is widened.
//
// A package cannot transition from:
//   - being unqualifiedly exported, to exported qualifiedly to a specific module.
//       This transition attempt is silently ignored in set_exported.
//
// The Mutex Module_lock is shared between ModuleEntry and PackageEntry, to lock either
// data structure.
class PackageEntry : public HashtableEntry<Symbol*, mtModule> {
private:
  ModuleEntry* _module;
  // Used to indicate for packages with classes loaded by the boot loader that
  // a class in that package has been loaded.  And, for packages with classes
  // loaded by the boot loader from -Xbootclasspath/a in an unnamed module, it
  // indicates from which class path entry.
  s2 _classpath_index;
  bool _is_exported_unqualified;
  bool _is_exported_allUnnamed;
  bool _must_walk_exports;
  GrowableArray<ModuleEntry*>* _exported_pending_delete; // transitioned from qualified to unqualified, delete at safepoint
  GrowableArray<ModuleEntry*>* _qualified_exports;
  TRACE_DEFINE_TRACE_ID_FIELD;

  // Initial size of a package entry's list of qualified exports.
  enum {QUAL_EXP_SIZE = 43};

public:
  void init() {
    _module = NULL;
    _classpath_index = -1;
    _is_exported_unqualified = false;
    _is_exported_allUnnamed = false;
    _must_walk_exports = false;
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
  bool is_exported() const { // qualifiedly or unqualifiedly exported
      return (is_unqual_exported() || has_qual_exports_list() || is_exported_allUnnamed());
  }
  // Returns true if the package has any explicit qualified exports or is exported to all unnamed
  bool is_qual_exported() const {
    return (has_qual_exports_list() || is_exported_allUnnamed());
  }
  // Returns true if there are any explicit qualified exports
  bool has_qual_exports_list() const {
    assert(!(_qualified_exports != NULL && _is_exported_unqualified),
           "_qualified_exports set at same time as _is_exported_unqualified");
    return (_qualified_exports != NULL);
  }
  bool is_exported_allUnnamed() const {
    assert(!(_is_exported_allUnnamed && _is_exported_unqualified),
           "_is_exported_allUnnamed set at same time as _is_exported_unqualified");
    return _is_exported_allUnnamed;
  }
  bool is_unqual_exported() const {
    assert(!(_qualified_exports != NULL && _is_exported_unqualified),
           "_qualified_exports set at same time as _is_exported_unqualified");
    assert(!(_is_exported_allUnnamed && _is_exported_unqualified),
           "_is_exported_allUnnamed set at same time as _is_exported_unqualified");
    return _is_exported_unqualified;
  }
  void set_unqual_exported() {
    assert(Module_lock->owned_by_self(), "should have the Module_lock");
    _is_exported_unqualified = true;
    _is_exported_allUnnamed = false;
    _qualified_exports = NULL;
  }
  bool exported_pending_delete() const     { return (_exported_pending_delete != NULL); }

  void set_exported(ModuleEntry* m);

  void set_is_exported_allUnnamed();

  void set_classpath_index(s2 classpath_index) {
    _classpath_index = classpath_index;
  }
  s2 classpath_index() const { return _classpath_index; }

  bool has_loaded_class() const { return _classpath_index != -1; }

  // returns true if the package is defined in the unnamed module
  bool in_unnamed_module() const  { return !_module->is_named(); }

  // returns true if the package specifies m as a qualified export, including through an unnamed export
  bool is_qexported_to(ModuleEntry* m) const;

  // add the module to the package's qualified exports
  void add_qexport(ModuleEntry* m);
  void set_export_walk_required(ClassLoaderData* m_loader_data);

  PackageEntry* next() const {
    return (PackageEntry*)HashtableEntry<Symbol*, mtModule>::next();
  }

  PackageEntry** next_addr() {
    return (PackageEntry**)HashtableEntry<Symbol*, mtModule>::next_addr();
  }

  // iteration of qualified exports
  void package_exports_do(ModuleClosure* const f);

  TRACE_DEFINE_TRACE_ID_METHODS;

  // Purge dead weak references out of exported list when any given class loader is unloaded.
  void purge_qualified_exports();
  void delete_qualified_exports();

  void print(outputStream* st = tty);
  void verify();
};

// The PackageEntryTable is a Hashtable containing a list of all packages defined
// by a particular class loader.  Each package is represented as a PackageEntry node.
// The PackageEntryTable's lookup is lock free.
//
class PackageEntryTable : public Hashtable<Symbol*, mtModule> {
  friend class VMStructs;
public:
  enum Constants {
    _packagetable_entry_size = 1009  // number of entries in package entry table
  };

private:
  PackageEntry* new_entry(unsigned int hash, Symbol* name, ModuleEntry* module);
  void add_entry(int index, PackageEntry* new_entry);

  int entry_size() const { return BasicHashtable<mtModule>::entry_size(); }

  PackageEntry** bucket_addr(int i) {
    return (PackageEntry**)Hashtable<Symbol*, mtModule>::bucket_addr(i);
  }

  static unsigned int compute_hash(Symbol* name) { return (unsigned int)(name->identity_hash()); }
  int index_for(Symbol* name) const { return hash_to_index(compute_hash(name)); }

public:
  PackageEntryTable(int table_size);
  ~PackageEntryTable();

  PackageEntry* bucket(int i) {
    return (PackageEntry*)Hashtable<Symbol*, mtModule>::bucket(i);
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

  void print(outputStream* st = tty);
  void verify();
};

#endif // SHARE_VM_CLASSFILE_PACKAGEENTRY_HPP
