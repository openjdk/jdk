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

#ifndef SHARE_VM_CLASSFILE_MODULEENTRY_HPP
#define SHARE_VM_CLASSFILE_MODULEENTRY_HPP

#include "classfile/classLoaderData.hpp"
#include "classfile/vmSymbols.hpp"
#include "oops/symbol.hpp"
#include "prims/jni.h"
#include "runtime/mutexLocker.hpp"
#include "trace/traceMacros.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/hashtable.hpp"

#define UNNAMED_MODULE "Unnamed Module"

class ModuleClosure;

// A ModuleEntry describes a module that has been defined by a call to JVM_DefineModule.
// It contains:
//   - Symbol* containing the module's name.
//   - pointer to the java.lang.reflect.Module for this module.
//   - ClassLoaderData*, class loader of this module.
//   - a growable array containg other module entries that this module can read.
//   - a flag indicating if this module can read all unnamed modules.
//
// The Mutex Module_lock is shared between ModuleEntry and PackageEntry, to lock either
// data structure.
class ModuleEntry : public HashtableEntry<Symbol*, mtClass> {
private:
  jobject _module;                     // java.lang.reflect.Module
  jobject _pd;                         // java.security.ProtectionDomain, cached
                                       // for shared classes from this module
  ClassLoaderData* _loader;
  GrowableArray<ModuleEntry*>* _reads; // list of modules that are readable by this module
  Symbol* _version;                    // module version number
  Symbol* _location;                   // module location
  bool _can_read_all_unnamed;
  bool _has_default_read_edges;        // JVMTI redefine/retransform support
  TRACE_DEFINE_TRACE_ID_FIELD;
  enum {MODULE_READS_SIZE = 101};      // Initial size of list of modules that the module can read.

public:
  void init() {
    _module = NULL;
    _loader = NULL;
    _pd = NULL;
    _reads = NULL;
    _version = NULL;
    _location = NULL;
    _can_read_all_unnamed = false;
    _has_default_read_edges = false;
  }

  Symbol*            name() const          { return literal(); }
  void               set_name(Symbol* n)   { set_literal(n); }

  jobject            module() const        { return _module; }
  void               set_module(jobject j) { _module = j; }

  // The shared ProtectionDomain reference is set once the VM loads a shared class
  // originated from the current Module. The referenced ProtectionDomain object is
  // created by the ClassLoader when loading a class (shared or non-shared) from the
  // Module for the first time. This ProtectionDomain object is used for all
  // classes from the Module loaded by the same ClassLoader.
  Handle             shared_protection_domain();
  void               set_shared_protection_domain(ClassLoaderData *loader_data,
                                                  Handle pd);

  ClassLoaderData*   loader() const                 { return _loader; }
  void               set_loader(ClassLoaderData* l) { _loader = l; }

  Symbol*            version() const                { return _version; }
  void               set_version(Symbol* version);

  Symbol*            location() const               { return _location; }
  void               set_location(Symbol* location);

  bool               can_read(ModuleEntry* m) const;
  bool               has_reads() const;
  void               add_read(ModuleEntry* m);

  bool               is_named() const               { return (literal() != NULL); }

  bool can_read_all_unnamed() const {
    assert(is_named() || _can_read_all_unnamed == true,
           "unnamed modules can always read all unnamed modules");
    return _can_read_all_unnamed;
  }

  // Modules can only go from strict to loose.
  void set_can_read_all_unnamed() { _can_read_all_unnamed = true; }

  bool has_default_read_edges() const {
    return _has_default_read_edges;
  }

  // Sets true and returns the previous value.
  bool set_has_default_read_edges() {
    MutexLocker ml(Module_lock);
    bool prev = _has_default_read_edges;
    _has_default_read_edges = true;
    return prev;
  }

  ModuleEntry* next() const {
    return (ModuleEntry*)HashtableEntry<Symbol*, mtClass>::next();
  }
  ModuleEntry** next_addr() {
    return (ModuleEntry**)HashtableEntry<Symbol*, mtClass>::next_addr();
  }

  // iteration support for readability
  void module_reads_do(ModuleClosure* const f);

  TRACE_DEFINE_TRACE_ID_METHODS;

  // Purge dead weak references out of reads list when any given class loader is unloaded.
  void purge_reads();
  void delete_reads();

  void print() PRODUCT_RETURN;
  void verify();
};

// Iterator interface
class ModuleClosure: public StackObj {
 public:
  virtual void do_module(ModuleEntry* const module) = 0;
};


// The ModuleEntryTable is a Hashtable containing a list of all modules defined
// by a particular class loader.  Each module is represented as a ModuleEntry node.
//
// Each ModuleEntryTable contains a _javabase_module field which allows for the
// creation of java.base's ModuleEntry very early in bootstrapping before the
// corresponding JVM_DefineModule call for java.base occurs during module system
// initialization.  Setting up java.base's ModuleEntry early enables classes,
// loaded prior to the module system being initialized to be created with their
// PackageEntry node's correctly pointing at java.base's ModuleEntry.  No class
// outside of java.base is allowed to be loaded pre-module system initialization.
//
// The ModuleEntryTable's lookup is lock free.
//
class ModuleEntryTable : public Hashtable<Symbol*, mtClass> {
  friend class VMStructs;
public:
  enum Constants {
    _moduletable_entry_size  = 109 // number of entries in module entry table
  };

private:
  static ModuleEntry* _javabase_module;
  ModuleEntry* _unnamed_module;

  ModuleEntry* new_entry(unsigned int hash, Handle module_handle, Symbol* name, Symbol* version,
                         Symbol* location, ClassLoaderData* class_loader);
  void add_entry(int index, ModuleEntry* new_entry);

  int entry_size() const { return BasicHashtable<mtClass>::entry_size(); }

  ModuleEntry** bucket_addr(int i) {
    return (ModuleEntry**)Hashtable<Symbol*, mtClass>::bucket_addr(i);
  }

  static unsigned int compute_hash(Symbol* name) { return ((name == NULL) ? 0 : (unsigned int)(name->identity_hash())); }
  int index_for(Symbol* name) const              { return hash_to_index(compute_hash(name)); }

public:
  ModuleEntryTable(int table_size);
  ~ModuleEntryTable();

  ModuleEntry* bucket(int i) {
    return (ModuleEntry*)Hashtable<Symbol*, mtClass>::bucket(i);
  }

  // Create module in loader's module entry table, if already exists then
  // return null.  Assume Module_lock has been locked by caller.
  ModuleEntry* locked_create_entry_or_null(Handle module_handle,
                                           Symbol* module_name,
                                           Symbol* module_version,
                                           Symbol* module_location,
                                           ClassLoaderData* loader_data);

  // Only lookup module within loader's module entry table.  The table read is lock-free.
  ModuleEntry* lookup_only(Symbol* name);

  // purge dead weak references out of reads list
  void purge_all_module_reads();

  // Special handling for unnamed module, one per class loader's ModuleEntryTable
  void create_unnamed_module(ClassLoaderData* loader_data);
  ModuleEntry* unnamed_module()                           { return _unnamed_module; }

  // Special handling for java.base
  static ModuleEntry* javabase_module()                   { return _javabase_module; }
  static void set_javabase_module(ModuleEntry* java_base) { _javabase_module = java_base; }
  static bool javabase_defined()                          { return ((_javabase_module != NULL) &&
                                                                    (_javabase_module->module() != NULL)); }
  static void finalize_javabase(Handle module_handle, Symbol* version, Symbol* location);
  static void patch_javabase_entries(Handle module_handle);

  void print() PRODUCT_RETURN;
  void verify();
};

#endif // SHARE_VM_CLASSFILE_MODULEENTRY_HPP
