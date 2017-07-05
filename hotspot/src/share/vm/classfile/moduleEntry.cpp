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

#include "precompiled.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/moduleEntry.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/symbol.hpp"
#include "prims/jni.h"
#include "runtime/handles.inline.hpp"
#include "runtime/safepoint.hpp"
#include "trace/traceMacros.hpp"
#include "utilities/events.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/ostream.hpp"

ModuleEntry* ModuleEntryTable::_javabase_module = NULL;


void ModuleEntry::set_location(Symbol* location) {
  if (_location != NULL) {
    // _location symbol's refcounts are managed by ModuleEntry,
    // must decrement the old one before updating.
    _location->decrement_refcount();
  }

  _location = location;

  if (location != NULL) {
    location->increment_refcount();
  }
}

void ModuleEntry::set_version(Symbol* version) {
  if (_version != NULL) {
    // _version symbol's refcounts are managed by ModuleEntry,
    // must decrement the old one before updating.
    _version->decrement_refcount();
  }

  _version = version;

  if (version != NULL) {
    version->increment_refcount();
  }
}

// Returns the shared ProtectionDomain
Handle ModuleEntry::shared_protection_domain() {
  return Handle(JNIHandles::resolve(_pd));
}

// Set the shared ProtectionDomain atomically
void ModuleEntry::set_shared_protection_domain(ClassLoaderData *loader_data,
                                               Handle pd_h) {
  // Create a JNI handle for the shared ProtectionDomain and save it atomically.
  // If someone beats us setting the _pd cache, the created JNI handle is destroyed.
  jobject obj = loader_data->add_handle(pd_h);
  if (Atomic::cmpxchg_ptr(obj, &_pd, NULL) != NULL) {
    loader_data->remove_handle(obj);
  }
}

// Returns true if this module can read module m
bool ModuleEntry::can_read(ModuleEntry* m) const {
  assert(m != NULL, "No module to lookup in this module's reads list");

  // Unnamed modules read everyone and all modules
  // read java.base.  If either of these conditions
  // hold, readability has been established.
  if (!this->is_named() ||
      (m == ModuleEntryTable::javabase_module())) {
    return true;
  }

  MutexLocker m1(Module_lock);
  if (!has_reads()) {
    return false;
  } else {
    return _reads->contains(m);
  }
}

// Add a new module to this module's reads list
void ModuleEntry::add_read(ModuleEntry* m) {
  MutexLocker m1(Module_lock);
  if (m == NULL) {
    set_can_read_all_unnamed();
  } else {
    if (_reads == NULL) {
      // Lazily create a module's reads list
      _reads = new (ResourceObj::C_HEAP, mtClass)GrowableArray<ModuleEntry*>(MODULE_READS_SIZE, true);
    }
    _reads->append_if_missing(m);
  }
}

bool ModuleEntry::has_reads() const {
  assert_locked_or_safepoint(Module_lock);
  return ((_reads != NULL) && !_reads->is_empty());
}

// Purge dead module entries out of reads list.
void ModuleEntry::purge_reads() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  if (has_reads()) {
    // Go backwards because this removes entries that are dead.
    int len = _reads->length();
    for (int idx = len - 1; idx >= 0; idx--) {
      ModuleEntry* module_idx = _reads->at(idx);
      ClassLoaderData* cld = module_idx->loader();
      if (cld->is_unloading()) {
        _reads->delete_at(idx);
      }
    }
  }
}

void ModuleEntry::module_reads_do(ModuleClosure* const f) {
  assert_locked_or_safepoint(Module_lock);
  assert(f != NULL, "invariant");

  if (has_reads()) {
    int reads_len = _reads->length();
    for (int i = 0; i < reads_len; ++i) {
      f->do_module(_reads->at(i));
    }
  }
}

void ModuleEntry::delete_reads() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  delete _reads;
  _reads = NULL;
}

ModuleEntryTable::ModuleEntryTable(int table_size)
  : Hashtable<Symbol*, mtClass>(table_size, sizeof(ModuleEntry)), _unnamed_module(NULL)
{
}

ModuleEntryTable::~ModuleEntryTable() {
  assert_locked_or_safepoint(Module_lock);

  // Walk through all buckets and all entries in each bucket,
  // freeing each entry.
  for (int i = 0; i < table_size(); ++i) {
    for (ModuleEntry* m = bucket(i); m != NULL;) {
      ModuleEntry* to_remove = m;
      // read next before freeing.
      m = m->next();

      ResourceMark rm;
      log_debug(modules)("ModuleEntryTable: deleting module: %s", to_remove->name() != NULL ?
                         to_remove->name()->as_C_string() : UNNAMED_MODULE);

      // Clean out the C heap allocated reads list first before freeing the entry
      to_remove->delete_reads();
      if (to_remove->name() != NULL) {
        to_remove->name()->decrement_refcount();
      }
      if (to_remove->version() != NULL) {
        to_remove->version()->decrement_refcount();
      }
      if (to_remove->location() != NULL) {
        to_remove->location()->decrement_refcount();
      }

      // Unlink from the Hashtable prior to freeing
      unlink_entry(to_remove);
      FREE_C_HEAP_ARRAY(char, to_remove);
    }
  }
  assert(number_of_entries() == 0, "should have removed all entries");
  assert(new_entry_free_list() == NULL, "entry present on ModuleEntryTable's free list");
  free_buckets();
}

void ModuleEntryTable::create_unnamed_module(ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(Module_lock);

  // Each ModuleEntryTable has exactly one unnamed module
  if (loader_data->is_the_null_class_loader_data()) {
    // For the boot loader, the java.lang.reflect.Module for the unnamed module
    // is not known until a call to JVM_SetBootLoaderUnnamedModule is made. At
    // this point initially create the ModuleEntry for the unnamed module.
    _unnamed_module = new_entry(0, Handle(NULL), NULL, NULL, NULL, loader_data);
  } else {
    // For all other class loaders the java.lang.reflect.Module for their
    // corresponding unnamed module can be found in the java.lang.ClassLoader object.
    oop module = java_lang_ClassLoader::unnamedModule(loader_data->class_loader());
    _unnamed_module = new_entry(0, Handle(module), NULL, NULL, NULL, loader_data);

    // Store pointer to the ModuleEntry in the unnamed module's java.lang.reflect.Module
    // object.
    java_lang_reflect_Module::set_module_entry(module, _unnamed_module);
  }

  // Add to bucket 0, no name to hash on
  add_entry(0, _unnamed_module);
}

ModuleEntry* ModuleEntryTable::new_entry(unsigned int hash, Handle module_handle, Symbol* name,
                                         Symbol* version, Symbol* location,
                                         ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(Module_lock);
  ModuleEntry* entry = (ModuleEntry*) NEW_C_HEAP_ARRAY(char, entry_size(), mtClass);

  // Initialize everything BasicHashtable would
  entry->set_next(NULL);
  entry->set_hash(hash);
  entry->set_literal(name);

  // Initialize fields specific to a ModuleEntry
  entry->init();
  if (name != NULL) {
    name->increment_refcount();
  } else {
    // Unnamed modules can read all other unnamed modules.
    entry->set_can_read_all_unnamed();
  }

  if (!module_handle.is_null()) {
    entry->set_module(loader_data->add_handle(module_handle));
  }

  entry->set_loader(loader_data);
  entry->set_version(version);
  entry->set_location(location);

  TRACE_INIT_MODULE_ID(entry);

  return entry;
}

void ModuleEntryTable::add_entry(int index, ModuleEntry* new_entry) {
  assert_locked_or_safepoint(Module_lock);
  Hashtable<Symbol*, mtClass>::add_entry(index, (HashtableEntry<Symbol*, mtClass>*)new_entry);
}

ModuleEntry* ModuleEntryTable::locked_create_entry_or_null(Handle module_handle,
                                                           Symbol* module_name,
                                                           Symbol* module_version,
                                                           Symbol* module_location,
                                                           ClassLoaderData* loader_data) {
  assert(module_name != NULL, "ModuleEntryTable locked_create_entry_or_null should never be called for unnamed module.");
  assert_locked_or_safepoint(Module_lock);
  // Check if module already exists.
  if (lookup_only(module_name) != NULL) {
    return NULL;
  } else {
    ModuleEntry* entry = new_entry(compute_hash(module_name), module_handle, module_name,
                                   module_version, module_location, loader_data);
    add_entry(index_for(module_name), entry);
    return entry;
  }
}

// lookup_only by Symbol* to find a ModuleEntry.
ModuleEntry* ModuleEntryTable::lookup_only(Symbol* name) {
  if (name == NULL) {
    // Return this table's unnamed module
    return unnamed_module();
  }
  int index = index_for(name);
  for (ModuleEntry* m = bucket(index); m != NULL; m = m->next()) {
    if (m->name()->fast_compare(name) == 0) {
      return m;
    }
  }
  return NULL;
}

// Remove dead modules from all other alive modules' reads list.
// This should only occur at class unloading.
void ModuleEntryTable::purge_all_module_reads() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  for (int i = 0; i < table_size(); i++) {
    for (ModuleEntry* entry = bucket(i);
                      entry != NULL;
                      entry = entry->next()) {
      entry->purge_reads();
    }
  }
}

void ModuleEntryTable::finalize_javabase(Handle module_handle, Symbol* version, Symbol* location) {
  assert_locked_or_safepoint(Module_lock);
  ClassLoaderData* boot_loader_data = ClassLoaderData::the_null_class_loader_data();
  ModuleEntryTable* module_table = boot_loader_data->modules();

  assert(module_table != NULL, "boot loader's ModuleEntryTable not defined");

  if (module_handle.is_null()) {
    fatal("Unable to finalize module definition for java.base");
  }

  // Set java.lang.reflect.Module, version and location for java.base
  ModuleEntry* jb_module = javabase_module();
  assert(jb_module != NULL, "java.base ModuleEntry not defined");
  jb_module->set_module(boot_loader_data->add_handle(module_handle));
  jb_module->set_version(version);
  jb_module->set_location(location);
  // Store pointer to the ModuleEntry for java.base in the java.lang.reflect.Module object.
  java_lang_reflect_Module::set_module_entry(module_handle(), jb_module);
}

void ModuleEntryTable::patch_javabase_entries(Handle module_handle) {
  if (module_handle.is_null()) {
    fatal("Unable to patch the module field of classes loaded prior to java.base's definition, invalid java.lang.reflect.Module");
  }

  // Do the fixups for the basic primitive types
  java_lang_Class::set_module(Universe::int_mirror(), module_handle());
  java_lang_Class::set_module(Universe::float_mirror(), module_handle());
  java_lang_Class::set_module(Universe::double_mirror(), module_handle());
  java_lang_Class::set_module(Universe::byte_mirror(), module_handle());
  java_lang_Class::set_module(Universe::bool_mirror(), module_handle());
  java_lang_Class::set_module(Universe::char_mirror(), module_handle());
  java_lang_Class::set_module(Universe::long_mirror(), module_handle());
  java_lang_Class::set_module(Universe::short_mirror(), module_handle());
  java_lang_Class::set_module(Universe::void_mirror(), module_handle());

  // Do the fixups for classes that have already been created.
  GrowableArray <Klass*>* list = java_lang_Class::fixup_module_field_list();
  int list_length = list->length();
  for (int i = 0; i < list_length; i++) {
    Klass* k = list->at(i);
    assert(k->is_klass(), "List should only hold classes");
    Thread* THREAD = Thread::current();
    KlassHandle kh(THREAD, k);
    java_lang_Class::fixup_module_field(kh, module_handle);
  }

  delete java_lang_Class::fixup_module_field_list();
  java_lang_Class::set_fixup_module_field_list(NULL);
}

void ModuleEntryTable::print(outputStream* st) {
  st->print_cr("Module Entry Table (table_size=%d, entries=%d)",
               table_size(), number_of_entries());
  for (int i = 0; i < table_size(); i++) {
    for (ModuleEntry* probe = bucket(i);
                              probe != NULL;
                              probe = probe->next()) {
      probe->print(st);
    }
  }
}

void ModuleEntry::print(outputStream* st) {
  ResourceMark rm;
  st->print_cr("entry "PTR_FORMAT" name %s module "PTR_FORMAT" loader %s version %s location %s strict %s next "PTR_FORMAT,
               p2i(this),
               name() == NULL ? UNNAMED_MODULE : name()->as_C_string(),
               p2i(module()),
               loader()->loader_name(),
               version() != NULL ? version()->as_C_string() : "NULL",
               location() != NULL ? location()->as_C_string() : "NULL",
               BOOL_TO_STR(!can_read_all_unnamed()), p2i(next()));
}

void ModuleEntryTable::verify() {
  int element_count = 0;
  for (int i = 0; i < table_size(); i++) {
    for (ModuleEntry* probe = bucket(i);
                              probe != NULL;
                              probe = probe->next()) {
      probe->verify();
      element_count++;
    }
  }
  guarantee(number_of_entries() == element_count,
            "Verify of Module Entry Table failed");
  debug_only(verify_lookup_length((double)number_of_entries() / table_size()));
}

void ModuleEntry::verify() {
  guarantee(loader() != NULL, "A module entry must be associated with a loader.");
}
