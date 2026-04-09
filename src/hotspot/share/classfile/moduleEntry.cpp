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

#include "cds/aotClassLocation.hpp"
#include "cds/aotGrowableArray.inline.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "jni.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/events.hpp"
#include "utilities/hashTable.hpp"
#include "utilities/ostream.hpp"
#include "utilities/quickSort.hpp"

ModuleEntry* ModuleEntryTable::_javabase_module = nullptr;

oop ModuleEntry::module_oop() const { return _module_handle.resolve(); }

void ModuleEntry::set_location(Symbol* location) {
  // _location symbol's refcounts are managed by ModuleEntry,
  // must decrement the old one before updating.
  Symbol::maybe_decrement_refcount(_location);

  _location = location;

  if (location != nullptr) {
    location->increment_refcount();
    CDS_ONLY(if (CDSConfig::is_using_archive()) {
        _shared_path_index = AOTClassLocationConfig::runtime()->get_module_shared_path_index(_location);
      });
  }
}

// Return true if the module's version should be displayed in error messages,
// logging, etc.
// Return false if the module's version is null, if it is unnamed, or if the
// module is not an upgradeable module.
// Detect if the module is not upgradeable by checking:
//     1. Module location is "jrt:/java." and its loader is boot or platform
//     2. Module location is "jrt:/jdk.", its loader is one of the builtin loaders
//        and its version is the same as module java.base's version
// The above check is imprecise but should work in almost all cases.
bool ModuleEntry::should_show_version() {
  if (version() == nullptr || !is_named()) return false;

  if (location() != nullptr) {
    ResourceMark rm;
    const char* loc = location()->as_C_string();
    ClassLoaderData* cld = loader_data();

    assert(!cld->has_class_mirror_holder(), "module's cld should have a ClassLoader holder not a Class holder");
    if ((cld->is_the_null_class_loader_data() || cld->is_platform_class_loader_data()) &&
        (strncmp(loc, "jrt:/java.", 10) == 0)) {
      return false;
    }
    if ((ModuleEntryTable::javabase_moduleEntry()->version()->fast_compare(version()) == 0) &&
        cld->is_permanent_class_loader_data() && (strncmp(loc, "jrt:/jdk.", 9) == 0)) {
      return false;
    }
  }
  return true;
}

void ModuleEntry::set_version(Symbol* version) {
  // _version symbol's refcounts are managed by ModuleEntry,
  // must decrement the old one before updating.
  Symbol::maybe_decrement_refcount(_version);

  _version = version;

  Symbol::maybe_increment_refcount(version);
}

// Returns the shared ProtectionDomain
oop ModuleEntry::shared_protection_domain() {
  return _shared_pd.resolve();
}

// Set the shared ProtectionDomain atomically
void ModuleEntry::set_shared_protection_domain(ClassLoaderData *loader_data,
                                               Handle pd_h) {
  // Create a handle for the shared ProtectionDomain and save it atomically.
  // init_handle_locked checks if someone beats us setting the _shared_pd cache.
  loader_data->init_handle_locked(_shared_pd, pd_h);
}

// Returns true if this module can read module m
bool ModuleEntry::can_read(ModuleEntry* m) const {
  assert(m != nullptr, "No module to lookup in this module's reads list");

  // Unnamed modules read everyone and all modules
  // read java.base.  If either of these conditions
  // hold, readability has been established.
  if (!this->is_named() ||
      (m == ModuleEntryTable::javabase_moduleEntry())) {
    return true;
  }

  MutexLocker m1(Module_lock);
  // This is a guard against possible race between agent threads that redefine
  // or retransform classes in this module. Only one of them is adding the
  // default read edges to the unnamed modules of the boot and app class loaders
  // with an upcall to jdk.internal.module.Modules.transformedByAgent.
  // At the same time, another thread can instrument the module classes by
  // injecting dependencies that require the default read edges for resolution.
  if (this->has_default_read_edges() && !m->is_named()) {
    ClassLoaderData* cld = m->loader_data();
    assert(!cld->has_class_mirror_holder(), "module's cld should have a ClassLoader holder not a Class holder");
    if (cld->is_the_null_class_loader_data() || cld->is_system_class_loader_data()) {
      return true; // default read edge
    }
  }
  if (!has_reads_list()) {
    return false;
  } else {
    return reads()->contains(m);
  }
}

// Add a new module to this module's reads list
void ModuleEntry::add_read(ModuleEntry* m) {
  // Unnamed module is special cased and can read all modules
  if (!is_named()) {
    return;
  }

  MutexLocker m1(Module_lock);
  if (m == nullptr) {
    set_can_read_all_unnamed();
  } else {
    if (reads() == nullptr) {
      // Lazily create a module's reads list
      AOTGrowableArray<ModuleEntry*>* new_reads = new (mtModule) AOTGrowableArray<ModuleEntry*>(MODULE_READS_SIZE, mtModule);
      set_reads(new_reads);
    }

    // Determine, based on this newly established read edge to module m,
    // if this module's read list should be walked at a GC safepoint.
    set_read_walk_required(m->loader_data());

    // Establish readability to module m
    reads()->append_if_missing(m);
  }
}

// If the module's loader, that a read edge is being established to, is
// not the same loader as this module's and is not one of the 3 builtin
// class loaders, then this module's reads list must be walked at GC
// safepoint. Modules have the same life cycle as their defining class
// loaders and should be removed if dead.
void ModuleEntry::set_read_walk_required(ClassLoaderData* m_loader_data) {
  assert(is_named(), "Cannot call set_read_walk_required on unnamed module");
  assert_locked_or_safepoint(Module_lock);
  if (!_must_walk_reads &&
      loader_data() != m_loader_data &&
      !m_loader_data->is_builtin_class_loader_data()) {
    _must_walk_reads = true;
    if (log_is_enabled(Trace, module)) {
      ResourceMark rm;
      log_trace(module)("ModuleEntry::set_read_walk_required(): module %s reads list must be walked",
                        (name() != nullptr) ? name()->as_C_string() : UNNAMED_MODULE);
    }
  }
}

// Set whether the module is open, i.e. all its packages are unqualifiedly exported
void ModuleEntry::set_is_open(bool is_open) {
  assert_lock_strong(Module_lock);
  _is_open = is_open;
}

// Returns true if the module has a non-empty reads list. As such, the unnamed
// module will return false.
bool ModuleEntry::has_reads_list() const {
  assert_locked_or_safepoint(Module_lock);
  return ((reads() != nullptr) && !reads()->is_empty());
}

// Purge dead module entries out of reads list.
void ModuleEntry::purge_reads() {
  assert_locked_or_safepoint(Module_lock);

  if (_must_walk_reads && has_reads_list()) {
    // This module's _must_walk_reads flag will be reset based
    // on the remaining live modules on the reads list.
    _must_walk_reads = false;

    if (log_is_enabled(Trace, module)) {
      ResourceMark rm;
      log_trace(module)("ModuleEntry::purge_reads(): module %s reads list being walked",
                        (name() != nullptr) ? name()->as_C_string() : UNNAMED_MODULE);
    }

    // Go backwards because this removes entries that are dead.
    int len = reads()->length();
    for (int idx = len - 1; idx >= 0; idx--) {
      ModuleEntry* module_idx = reads()->at(idx);
      ClassLoaderData* cld_idx = module_idx->loader_data();
      if (cld_idx->is_unloading()) {
        reads()->delete_at(idx);
      } else {
        // Update the need to walk this module's reads based on live modules
        set_read_walk_required(cld_idx);
      }
    }
  }
}

void ModuleEntry::module_reads_do(ModuleClosure* f) {
  assert_locked_or_safepoint(Module_lock);
  assert(f != nullptr, "invariant");

  if (has_reads_list()) {
    int reads_len = reads()->length();
    for (ModuleEntry* m : *reads()) {
      f->do_module(m);
    }
  }
}

void ModuleEntry::delete_reads() {
  delete reads();
  _reads = nullptr;
}

ModuleEntry::ModuleEntry(Handle module_handle,
                         bool is_open, Symbol* name,
                         Symbol* version, Symbol* location,
                         ClassLoaderData* loader_data) :
    _name(name),
    _loader_data(loader_data),
    _reads(nullptr),
    _version(nullptr),
    _location(nullptr),
    CDS_ONLY(_shared_path_index(-1) COMMA)
    _can_read_all_unnamed(false),
    _has_default_read_edges(false),
    _must_walk_reads(false),
    _is_open(is_open),
    _is_patched(false) {

  // Initialize fields specific to a ModuleEntry
  if (_name == nullptr) {
    // Unnamed modules can read all other unnamed modules.
    set_can_read_all_unnamed();
  } else {
    _name->increment_refcount();
  }

  if (!module_handle.is_null()) {
    _module_handle = loader_data->add_handle(module_handle);
  }

  set_version(version);

  // may need to add CDS info
  set_location(location);

  if (name != nullptr && ClassLoader::is_in_patch_mod_entries(name)) {
    set_is_patched();
    if (log_is_enabled(Trace, module, patch)) {
      ResourceMark rm;
      log_trace(module, patch)("Marked module %s as patched from --patch-module",
                               name != nullptr ? name->as_C_string() : UNNAMED_MODULE);
    }
  }

  JFR_ONLY(INIT_ID(this);)
}

ModuleEntry::~ModuleEntry() {
  // Clean out the C heap allocated reads list first before freeing the entry
  delete_reads();
  Symbol::maybe_decrement_refcount(_name);
  Symbol::maybe_decrement_refcount(_version);
  Symbol::maybe_decrement_refcount(_location);
}

ModuleEntry* ModuleEntry::create_unnamed_module(ClassLoaderData* cld) {
  // The java.lang.Module for this loader's
  // corresponding unnamed module can be found in the java.lang.ClassLoader object.
  oop module = java_lang_ClassLoader::unnamedModule(cld->class_loader());

#if INCLUDE_CDS_JAVA_HEAP
  ModuleEntry* archived_unnamed_module = ClassLoaderDataShared::archived_unnamed_module(cld);
  if (archived_unnamed_module != nullptr) {
    archived_unnamed_module->load_from_archive(cld);
    archived_unnamed_module->restore_archived_oops(cld);
    return archived_unnamed_module;
  }
#endif

  // Ensure that the unnamed module was correctly set when the class loader was constructed.
  // Guarantee will cause a recognizable crash if the user code has circumvented calling the ClassLoader constructor.
  ResourceMark rm;
  guarantee(java_lang_Module::is_instance(module),
            "The unnamed module for ClassLoader %s, is null or not an instance of java.lang.Module. The class loader has not been initialized correctly.",
            cld->loader_name_and_id());

  ModuleEntry* unnamed_module = new_unnamed_module_entry(Handle(Thread::current(), module), cld);

  // Store pointer to the ModuleEntry in the unnamed module's java.lang.Module object.
  java_lang_Module::set_module_entry(module, unnamed_module);

  return unnamed_module;
}

ModuleEntry* ModuleEntry::create_boot_unnamed_module(ClassLoaderData* cld) {
#if INCLUDE_CDS_JAVA_HEAP
  ModuleEntry* archived_unnamed_module = ClassLoaderDataShared::archived_boot_unnamed_module();
  if (archived_unnamed_module != nullptr) {
    archived_unnamed_module->load_from_archive(cld);
    // It's too early to call archived_unnamed_module->restore_archived_oops(cld).
    // We will do it inside Modules::set_bootloader_unnamed_module()
    return archived_unnamed_module;
  }
#endif

  // For the boot loader, the java.lang.Module for the unnamed module
  // is not known until a call to JVM_SetBootLoaderUnnamedModule is made. At
  // this point initially create the ModuleEntry for the unnamed module.
  ModuleEntry* unnamed_module = new_unnamed_module_entry(Handle(), cld);
  assert(unnamed_module != nullptr, "boot loader unnamed module should not be null");
  return unnamed_module;
}

// When creating an unnamed module, this is called without holding the Module_lock.
// This is okay because the unnamed module gets created before the ClassLoaderData
// is available to other threads.
ModuleEntry* ModuleEntry::new_unnamed_module_entry(Handle module_handle, ClassLoaderData* cld) {
  ModuleEntry* entry = new ModuleEntry(module_handle, /*is_open*/true, /*name*/nullptr,
                                       /*version*/ nullptr, /*location*/ nullptr,
                                       cld);
  // Unnamed modules can read all other unnamed modules.
  assert(entry->can_read_all_unnamed(), "constructor set that");
  return entry;
}

ModuleEntryTable::ModuleEntryTable() { }

ModuleEntryTable::~ModuleEntryTable() {
  class ModuleEntryTableDeleter : public StackObj {
   public:
    bool do_entry(const SymbolHandle& name, ModuleEntry*& entry) {
      if (log_is_enabled(Info, module, unload) || log_is_enabled(Debug, module)) {
        ResourceMark rm;
        const char* str = name->as_C_string();
        log_info(module, unload)("unloading module %s", str);
        log_debug(module)("ModuleEntryTable: deleting module: %s", str);
      }
      delete entry;
      return true;
    }
  };

  ModuleEntryTableDeleter deleter;
  _table.unlink(&deleter);
  assert(_table.number_of_entries() == 0, "should have removed all entries");
}

void ModuleEntry::set_loader_data(ClassLoaderData* cld) {
  assert(!cld->has_class_mirror_holder(), "Unexpected has_class_mirror_holder cld");
  _loader_data = cld;
}

void ModuleEntry::metaspace_pointers_do(MetaspaceClosure* it) {
  it->push(&_name);
  it->push(&_reads);
  it->push(&_version);
  it->push(&_location);
}

#if INCLUDE_CDS_JAVA_HEAP
bool ModuleEntry::should_be_archived() const {
  return SystemDictionaryShared::is_builtin_loader(loader_data());
}

void ModuleEntry::remove_unshareable_info() {
  _archived_module_index = HeapShared::append_root(module_oop());

  if (_reads != nullptr) {
    _reads->set_in_aot_cache();
  }

  // Clear handles and restore at run time. Handles cannot be archived.
  if (CDSConfig::is_dumping_final_static_archive()) {
    OopHandle null_handle;
    _shared_pd = null_handle;
  } else {
    assert(shared_protection_domain() == nullptr, "never set during -Xshare:dump");
  }

  OopHandle null_handle;
  _module_handle = null_handle;

  _loader_data = nullptr;  // re-init at runtime
  if (name() != nullptr) {
    Symbol* src_location = ArchiveBuilder::current()->get_source_addr(_location);
    _shared_path_index = AOTClassLocationConfig::dumptime()->get_module_shared_path_index(src_location);
  } else {
    // _shared_path_index is used only by SystemDictionary::is_shared_class_visible_impl()
    // for checking classes in named modules.
    _shared_path_index = -1;
  }
  JFR_ONLY(set_trace_id(0);) // re-init at runtime
}

void ModuleEntry::load_from_archive(ClassLoaderData* loader_data) {
  assert(CDSConfig::is_using_archive(), "runtime only");
  set_loader_data(loader_data);
  JFR_ONLY(INIT_ID(this);)
}

void ModuleEntry::preload_archived_oops() {
  (void)HeapShared::get_root(_archived_module_index, false /* clear */);
}

void ModuleEntry::restore_archived_oops(ClassLoaderData* loader_data) {
  assert(CDSConfig::is_using_archive(), "runtime only");
  Handle module_handle(Thread::current(), HeapShared::get_root(_archived_module_index, /*clear=*/true));
  assert(module_handle.not_null(), "huh");
  set_module_handle(loader_data->add_handle(module_handle));

  // This was cleared to zero during dump time -- we didn't save the value
  // because it may be affected by archive relocation.
  java_lang_Module::set_module_entry(module_handle(), this);

  assert(java_lang_Module::loader(module_handle()) == loader_data->class_loader(),
         "must be set in dump time");

  if (log_is_enabled(Info, aot, module)) {
    ResourceMark rm;
    LogStream ls(Log(aot, module)::info());
    ls.print("Restored from archive: ");
    print(&ls);
  }
}

void ModuleEntry::clear_archived_oops() {
  assert(CDSConfig::is_using_archive(), "runtime only");
  HeapShared::clear_root(_archived_module_index);
}

static int compare_module_by_name(ModuleEntry* a, ModuleEntry* b) {
  assert(a == b || a->name() != b->name(), "no duplicated names");
  return a->name()->fast_compare(b->name());
}

Array<ModuleEntry*>* ModuleEntryTable::build_aot_table(ClassLoaderData* loader_data, TRAPS) {
  Array<ModuleEntry*>* aot_table =
    MetadataFactory::new_array<ModuleEntry*>(loader_data, _table.number_of_entries(), nullptr, CHECK_NULL);
  int n = 0;
  auto grab = [&] (const SymbolHandle& key, ModuleEntry*& m) {
    m->pack_reads();
    aot_table->at_put(n++, m);
    if (log_is_enabled(Info, aot, module)) {
      ResourceMark rm;
      LogStream ls(Log(aot, module)::info());
      ls.print("Stored in archive: ");
      m->print(&ls);
    }
  };
  _table.iterate_all(grab);

  if (n > 1) {
    // Always allocate in the same order to produce deterministic archive.
    QuickSort::sort(aot_table->data(), n, compare_module_by_name);
  }

  return aot_table;
}

void ModuleEntryTable::load_archived_entries(ClassLoaderData* loader_data,
                                             Array<ModuleEntry*>* archived_modules) {
  assert(CDSConfig::is_using_archive(), "runtime only");

  for (int i = 0; i < archived_modules->length(); i++) {
    ModuleEntry* archived_entry = archived_modules->at(i);
    archived_entry->load_from_archive(loader_data);
    _table.put(archived_entry->name(), archived_entry);
  }
}

void ModuleEntryTable::restore_archived_oops(ClassLoaderData* loader_data, Array<ModuleEntry*>* archived_modules) {
  assert(CDSConfig::is_using_archive(), "runtime only");
  for (int i = 0; i < archived_modules->length(); i++) {
    ModuleEntry* archived_entry = archived_modules->at(i);
    archived_entry->restore_archived_oops(loader_data);
  }
}
#endif // INCLUDE_CDS_JAVA_HEAP

// Create an entry in the class loader's module_entry_table.  It is the
// caller's responsibility to ensure that the entry has not already been
// created.
ModuleEntry* ModuleEntryTable::locked_create_entry(Handle module_handle,
                                                   bool is_open,
                                                   Symbol* module_name,
                                                   Symbol* module_version,
                                                   Symbol* module_location,
                                                   ClassLoaderData* loader_data) {
  assert(module_name != nullptr, "ModuleEntryTable locked_create_entry should never be called for unnamed module.");
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  assert(lookup_only(module_name) == nullptr, "Module already exists");
  ModuleEntry* entry = new ModuleEntry(module_handle, is_open, module_name,
                                       module_version, module_location, loader_data);
  bool created = _table.put(module_name, entry);
  assert(created, "should be");
  return entry;
}

// lookup_only by Symbol* to find a ModuleEntry.
ModuleEntry* ModuleEntryTable::lookup_only(Symbol* name) {
  assert_locked_or_safepoint(Module_lock);
  assert(name != nullptr, "name cannot be nullptr");
  ModuleEntry** entry = _table.get(name);
  return (entry == nullptr) ? nullptr : *entry;
}

// Remove dead modules from all other alive modules' reads list.
// This should only occur at class unloading.
void ModuleEntryTable::purge_all_module_reads() {
  assert_locked_or_safepoint(Module_lock);
  auto purge = [&] (const SymbolHandle& key, ModuleEntry*& entry) {
    entry->purge_reads();
  };
  _table.iterate_all(purge);
}

void ModuleEntryTable::finalize_javabase(Handle module_handle, Symbol* version, Symbol* location) {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  ClassLoaderData* boot_loader_data = ClassLoaderData::the_null_class_loader_data();
  ModuleEntryTable* module_table = boot_loader_data->modules();

  assert(module_table != nullptr, "boot loader's ModuleEntryTable not defined");

  if (module_handle.is_null()) {
    fatal("Unable to finalize module definition for " JAVA_BASE_NAME);
  }

  // Set java.lang.Module, version and location for java.base
  ModuleEntry* jb_module = javabase_moduleEntry();
  assert(jb_module != nullptr, JAVA_BASE_NAME " ModuleEntry not defined");
  jb_module->set_version(version);
  jb_module->set_location(location);
  // Once java.base's ModuleEntry _module field is set with the known
  // java.lang.Module, java.base is considered "defined" to the VM.
  jb_module->set_module_handle(boot_loader_data->add_handle(module_handle));

  // Store pointer to the ModuleEntry for java.base in the java.lang.Module object.
  java_lang_Module::set_module_entry(module_handle(), jb_module);
}

// Within java.lang.Class instances there is a java.lang.Module field that must
// be set with the defining module.  During startup, prior to java.base's definition,
// classes needing their module field set are added to the fixup_module_list.
// Their module field is set once java.base's java.lang.Module is known to the VM.
void ModuleEntryTable::patch_javabase_entries(JavaThread* current, Handle module_handle) {
  assert(!CDSConfig::is_using_aot_linked_classes(), "patching is not necessary with AOT-linked classes");

  if (module_handle.is_null()) {
    fatal("Unable to patch the module field of classes loaded prior to "
          JAVA_BASE_NAME "'s definition, invalid java.lang.Module");
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
#ifndef PRODUCT
    if (HeapShared::is_a_test_class_in_unnamed_module(k)) {
      // We allow -XX:ArchiveHeapTestClass to archive additional classes
      // into the CDS heap, but these must be in the unnamed module.
      ModuleEntry* unnamed_module = ClassLoaderData::the_null_class_loader_data()->unnamed_module();
      Handle unnamed_module_handle(current, unnamed_module->module_oop());
      java_lang_Class::fixup_module_field(k, unnamed_module_handle);
    } else
#endif
    {
      java_lang_Class::fixup_module_field(k, module_handle);
    }
    k->class_loader_data()->dec_keep_alive_ref_count();
  }

  delete java_lang_Class::fixup_module_field_list();
  java_lang_Class::set_fixup_module_field_list(nullptr);
}

void ModuleEntryTable::print(outputStream* st) {
  ResourceMark rm;
  auto printer = [&] (const SymbolHandle& name, ModuleEntry*& entry) {
    entry->print(st);
  };
  st->print_cr("Module Entry Table (table_size=%d, entries=%d)",
               _table.table_size(), _table.number_of_entries());
  assert_locked_or_safepoint(Module_lock);
  _table.iterate_all(printer);
}

void ModuleEntryTable::modules_do(void f(ModuleEntry*)) {
  auto do_f = [&] (const SymbolHandle& key, ModuleEntry*& entry) {
    f(entry);
  };
  assert_lock_strong(Module_lock);
  _table.iterate_all(do_f);
}

void ModuleEntryTable::modules_do(ModuleClosure* closure) {
  auto do_f = [&] (const SymbolHandle& key, ModuleEntry*& entry) {
    closure->do_module(entry);
  };
  assert_lock_strong(Module_lock);
  _table.iterate_all(do_f);
}

void ModuleEntry::print(outputStream* st) const {
  st->print_cr("entry " PTR_FORMAT " name %s module " PTR_FORMAT " loader %s version %s location %s strict %s",
               p2i(this),
               name_as_C_string(),
               p2i(module_oop()),
               loader_data()->loader_name_and_id(),
               version() != nullptr ? version()->as_C_string() : "nullptr",
               location() != nullptr ? location()->as_C_string() : "nullptr",
               BOOL_TO_STR(!can_read_all_unnamed()));
}

void ModuleEntryTable::verify() {
  auto do_f = [&] (const SymbolHandle& key, ModuleEntry*& entry) {
    entry->verify();
  };
  assert_locked_or_safepoint(Module_lock);
  _table.iterate_all(do_f);
}

void ModuleEntry::verify() {
  guarantee(loader_data() != nullptr, "A module entry must be associated with a loader.");
}
