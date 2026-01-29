/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_MODULEENTRY_HPP
#define SHARE_CLASSFILE_MODULEENTRY_HPP

#include "cds/aotGrowableArray.hpp"
#include "jni.h"
#include "memory/metaspaceClosureType.hpp"
#include "oops/oopHandle.hpp"
#include "oops/symbol.hpp"
#include "oops/symbolHandle.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/hashTable.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#if INCLUDE_JFR
#include "jfr/support/jfrTraceIdExtension.hpp"
#endif

#define UNNAMED_MODULE "unnamed module"
#define UNNAMED_MODULE_LEN 14
#define JAVAPKG "java"
#define JAVAPKG_LEN 4
#define JAVA_BASE_NAME "java.base"
#define JAVA_BASE_NAME_LEN 9

template <class T> class Array;
class ClassLoaderData;
class MetaspaceClosure;
class ModuleClosure;

// A ModuleEntry describes a module that has been defined by a call to JVM_DefineModule.
// It contains:
//   - Symbol* containing the module's name.
//   - pointer to the java.lang.Module: the representation of this module as a Java object
//   - pointer to the java.security.ProtectionDomain shared by classes defined to this module.
//   - ClassLoaderData*, class loader of this module.
//   - a growable array containing other module entries that this module can read.
//   - a flag indicating if this module can read all unnamed modules.
//
// The Mutex Module_lock is shared between ModuleEntry and PackageEntry, to lock either
// data structure.  This lock must be taken on all accesses to either table.
class ModuleEntry : public CHeapObj<mtModule> {
private:
  OopHandle _module_handle;            // java.lang.Module
  OopHandle _shared_pd;                // java.security.ProtectionDomain, cached
                                       // for shared classes from this module
  Symbol*          _name;              // name of this module
  ClassLoaderData* _loader_data;
  AOTGrowableArray<ModuleEntry*>* _reads;  // list of modules that are readable by this module

  Symbol* _version;                    // module version number
  Symbol* _location;                   // module location
  CDS_ONLY(int _shared_path_index;)    // >=0 if classes in this module are in CDS archive
  bool _can_read_all_unnamed;
  bool _has_default_read_edges;        // JVMTI redefine/retransform support
  bool _must_walk_reads;               // walk module's reads list at GC safepoints to purge out dead modules
  bool _is_open;                       // whether the packages in the module are all unqualifiedly exported
  bool _is_patched;                    // whether the module is patched via --patch-module
  CDS_JAVA_HEAP_ONLY(int _archived_module_index;)

  JFR_ONLY(DEFINE_TRACE_ID_FIELD;)
  enum {MODULE_READS_SIZE = 101};      // Initial size of list of modules that the module can read.

public:
  ModuleEntry(Handle module_handle,
              bool is_open, Symbol* name,
              Symbol* version, Symbol* location,
              ClassLoaderData* loader_data);

  ~ModuleEntry();

  Symbol*          name() const                        { return _name; }
  oop              module_oop() const;
  OopHandle        module_handle() const               { return _module_handle; }
  void             set_module_handle(OopHandle j)      { _module_handle = j; }

  // The shared ProtectionDomain reference is set once the VM loads a shared class
  // originated from the current Module. The referenced ProtectionDomain object is
  // created by the ClassLoader when loading a class (shared or non-shared) from the
  // Module for the first time. This ProtectionDomain object is used for all
  // classes from the Module loaded by the same ClassLoader.
  oop              shared_protection_domain();
  void             set_shared_protection_domain(ClassLoaderData *loader_data, Handle pd);

  ClassLoaderData* loader_data() const                 { return _loader_data; }
  void set_loader_data(ClassLoaderData* cld);

  Symbol*          version() const                     { return _version; }
  void             set_version(Symbol* version);

  Symbol*          location() const                    { return _location; }
  void             set_location(Symbol* location);
  bool             should_show_version();

  bool             can_read(ModuleEntry* m) const;
  bool             has_reads_list() const;
  AOTGrowableArray<ModuleEntry*>* reads() const {
    return _reads;
  }
  void set_reads(AOTGrowableArray<ModuleEntry*>* r) {
    _reads = r;
  }
  void pack_reads() {
    if (_reads != nullptr) {
      _reads->shrink_to_fit();
    }
  }

  void             add_read(ModuleEntry* m);
  void             set_read_walk_required(ClassLoaderData* m_loader_data);

  bool             is_open() const                     { return _is_open; }
  void             set_is_open(bool is_open);

  bool             is_named() const                    { return (_name != nullptr); }

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

  void set_is_patched() {
      _is_patched = true;
      CDS_ONLY(_shared_path_index = -1); // Mark all shared classes in this module invisible.
  }
  bool is_patched() {
      return _is_patched;
  }

  // iteration support for readability
  void module_reads_do(ModuleClosure* const f);

  // Purge dead weak references out of reads list when any given class loader is unloaded.
  void purge_reads();
  void delete_reads();

  // Special handling for unnamed module, one per class loader
  static ModuleEntry* create_unnamed_module(ClassLoaderData* cld);
  static ModuleEntry* create_boot_unnamed_module(ClassLoaderData* cld);
  static ModuleEntry* new_unnamed_module_entry(Handle module_handle, ClassLoaderData* cld);

  // Note caller requires ResourceMark
  const char* name_as_C_string() const {
    return is_named() ? name()->as_C_string() : UNNAMED_MODULE;
  }

  // methods required by MetaspaceClosure
  void metaspace_pointers_do(MetaspaceClosure* it);
  int size_in_heapwords() const { return (int)heap_word_size(sizeof(ModuleEntry)); }
  MetaspaceClosureType type() const { return MetaspaceClosureType::ModuleEntryType; }
  static bool is_read_only_by_default() { return false; }

  void print(outputStream* st = tty) const;
  void verify();

  CDS_ONLY(int shared_path_index() { return _shared_path_index;})

  JFR_ONLY(DEFINE_TRACE_ID_METHODS;)

#if INCLUDE_CDS_JAVA_HEAP
  bool should_be_archived() const;
  void remove_unshareable_info();
  void load_from_archive(ClassLoaderData* loader_data);
  void preload_archived_oops();
  void restore_archived_oops(ClassLoaderData* loader_data);
  void clear_archived_oops();
#endif
};

// Iterator interface
class ModuleClosure: public StackObj {
 public:
  virtual void do_module(ModuleEntry* module) = 0;
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
class ModuleEntryTable : public CHeapObj<mtModule> {
private:
  static ModuleEntry* _javabase_module;
  HashTable<SymbolHandle, ModuleEntry*, 109, AnyObj::C_HEAP, mtModule,
                    SymbolHandle::compute_hash> _table;

public:
  ModuleEntryTable();
  ~ModuleEntryTable();

  // Create module in loader's module entry table.  Assume Module_lock
  // has been locked by caller.
  ModuleEntry* locked_create_entry(Handle module_handle,
                                   bool is_open,
                                   Symbol* module_name,
                                   Symbol* module_version,
                                   Symbol* module_location,
                                   ClassLoaderData* loader_data);

  // Only lookup module within loader's module entry table.
  ModuleEntry* lookup_only(Symbol* name);

  // purge dead weak references out of reads list
  void purge_all_module_reads();

  // Special handling for java.base
  static ModuleEntry* javabase_moduleEntry()                   { return _javabase_module; }
  static void set_javabase_moduleEntry(ModuleEntry* java_base) {
    assert(_javabase_module == nullptr, "_javabase_module is already defined");
    _javabase_module = java_base;
  }

  static bool javabase_defined() { return ((_javabase_module != nullptr) &&
                                           (_javabase_module->module_oop() != nullptr)); }
  static void finalize_javabase(Handle module_handle, Symbol* version, Symbol* location);
  static void patch_javabase_entries(JavaThread* current, Handle module_handle);

  void modules_do(void f(ModuleEntry*));
  void modules_do(ModuleClosure* closure);

  void print(outputStream* st = tty);
  void verify();

#if INCLUDE_CDS_JAVA_HEAP
  Array<ModuleEntry*>* build_aot_table(ClassLoaderData* loader_data, TRAPS);
  void load_archived_entries(ClassLoaderData* loader_data,
                             Array<ModuleEntry*>* archived_modules);
  void restore_archived_oops(ClassLoaderData* loader_data,
                             Array<ModuleEntry*>* archived_modules);
#endif
};

#endif // SHARE_CLASSFILE_MODULEENTRY_HPP
