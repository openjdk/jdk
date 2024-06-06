/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveBuilder.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/cdsConfig.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/array.hpp"
#include "oops/symbol.hpp"
#include "runtime/java.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/events.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"
#include "utilities/quickSort.hpp"
#include "utilities/resourceHash.hpp"

PackageEntry::PackageEntry(Symbol* name, ModuleEntry* module) :
  _name(name),
  _module(module),
  _export_flags(0),
  _classpath_index(-1),
  _must_walk_exports(false),
  _qualified_exports(nullptr),
  _defined_by_cds_in_class_path(0)
{
  // name can't be null
  _name->increment_refcount();

  JFR_ONLY(INIT_ID(this);)
}

PackageEntry::~PackageEntry() {
  delete_qualified_exports();
  _name->decrement_refcount();
}

// Returns true if this package specifies m as a qualified export, including through an unnamed export
bool PackageEntry::is_qexported_to(ModuleEntry* m) const {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  assert(m != nullptr, "No module to lookup in this package's qualified exports list");
  if (is_exported_allUnnamed() && !m->is_named()) {
    return true;
  } else if (!has_qual_exports_list()) {
    return false;
  } else {
    return _qualified_exports->contains(m);
  }
}

// Add a module to the package's qualified export list.
void PackageEntry::add_qexport(ModuleEntry* m) {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  if (!has_qual_exports_list()) {
    // Lazily create a package's qualified exports list.
    // Initial size is small, do not anticipate export lists to be large.
    _qualified_exports = new (mtModule) GrowableArray<ModuleEntry*>(QUAL_EXP_SIZE, mtModule);
  }

  // Determine, based on this newly established export to module m,
  // if this package's export list should be walked at a GC safepoint.
  set_export_walk_required(m->loader_data());

  // Establish exportability to module m
  _qualified_exports->append_if_missing(m);
}

// If the module's loader, that an export is being established to, is
// not the same loader as this module's and is not one of the 3 builtin
// class loaders, then this package's export list must be walked at GC
// safepoint. Modules have the same life cycle as their defining class
// loaders and should be removed if dead.
void PackageEntry::set_export_walk_required(ClassLoaderData* m_loader_data) {
  assert_locked_or_safepoint(Module_lock);
  ModuleEntry* this_pkg_mod = module();
  if (!_must_walk_exports &&
      (this_pkg_mod == nullptr || this_pkg_mod->loader_data() != m_loader_data) &&
      !m_loader_data->is_builtin_class_loader_data()) {
    _must_walk_exports = true;
    if (log_is_enabled(Trace, module)) {
      ResourceMark rm;
      assert(name() != nullptr, "PackageEntry without a valid name");
      log_trace(module)("PackageEntry::set_export_walk_required(): package %s defined in module %s, exports list must be walked",
                        name()->as_C_string(),
                        (this_pkg_mod == nullptr || this_pkg_mod->name() == nullptr) ?
                          UNNAMED_MODULE : this_pkg_mod->name()->as_C_string());
    }
  }
}

// Set the package's exported states based on the value of the ModuleEntry.
void PackageEntry::set_exported(ModuleEntry* m) {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  if (is_unqual_exported()) {
    // An exception could be thrown, but choose to simply ignore.
    // Illegal to convert an unqualified exported package to be qualifiedly exported
    return;
  }

  if (m == nullptr) {
    // null indicates the package is being unqualifiedly exported.  Clean up
    // the qualified list at the next safepoint.
    set_unqual_exported();
  } else {
    // Add the exported module
    add_qexport(m);
  }
}

// Set the package as exported to all unnamed modules unless the package is
// already unqualifiedly exported.
void PackageEntry::set_is_exported_allUnnamed() {
  assert(!module()->is_open(), "should have been checked already");
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  if (!is_unqual_exported()) {
   _export_flags = PKG_EXP_ALLUNNAMED;
  }
}

// Remove dead module entries within the package's exported list.  Note that
// if all of the modules on the _qualified_exports get purged the list does not
// get deleted.  This prevents the package from illegally transitioning from
// exported to non-exported.
void PackageEntry::purge_qualified_exports() {
  assert_locked_or_safepoint(Module_lock);
  if (_must_walk_exports &&
      _qualified_exports != nullptr &&
      !_qualified_exports->is_empty()) {

    // This package's _must_walk_exports flag will be reset based
    // on the remaining live modules on the exports list.
    _must_walk_exports = false;

    if (log_is_enabled(Trace, module)) {
      ResourceMark rm;
      assert(name() != nullptr, "PackageEntry without a valid name");
      ModuleEntry* pkg_mod = module();
      log_trace(module)("PackageEntry::purge_qualified_exports(): package %s defined in module %s, exports list being walked",
                        name()->as_C_string(),
                        (pkg_mod == nullptr || pkg_mod->name() == nullptr) ? UNNAMED_MODULE : pkg_mod->name()->as_C_string());
    }

    // Go backwards because this removes entries that are dead.
    int len = _qualified_exports->length();
    for (int idx = len - 1; idx >= 0; idx--) {
      ModuleEntry* module_idx = _qualified_exports->at(idx);
      ClassLoaderData* cld_idx = module_idx->loader_data();
      if (cld_idx->is_unloading()) {
        _qualified_exports->delete_at(idx);
      } else {
        // Update the need to walk this package's exports based on live modules
        set_export_walk_required(cld_idx);
      }
    }
  }
}

void PackageEntry::delete_qualified_exports() {
  if (_qualified_exports != nullptr) {
    delete _qualified_exports;
  }
  _qualified_exports = nullptr;
}

PackageEntryTable::PackageEntryTable() { }

PackageEntryTable::~PackageEntryTable() {
  class PackageEntryTableDeleter : public StackObj {
   public:
    bool do_entry(const SymbolHandle& name, PackageEntry*& entry) {
      if (log_is_enabled(Info, module, unload) || log_is_enabled(Debug, module)) {
        ResourceMark rm;
        const char* str = name->as_C_string();
        log_info(module, unload)("unloading package %s", str);
        log_debug(module)("PackageEntry: deleting package: %s", str);
      }
      delete entry;
      return true;
    }
  };

  PackageEntryTableDeleter deleter;
  _table.unlink(&deleter);
  assert(_table.number_of_entries() == 0, "should have removed all entries");
}

#if INCLUDE_CDS_JAVA_HEAP
typedef ResourceHashtable<
  const PackageEntry*,
  PackageEntry*,
  557, // prime number
  AnyObj::C_HEAP> ArchivedPackageEntries;
static ArchivedPackageEntries* _archived_packages_entries = nullptr;

PackageEntry* PackageEntry::allocate_archived_entry() const {
  assert(!in_unnamed_module(), "unnamed packages/modules are not archived");
  PackageEntry* archived_entry = (PackageEntry*)ArchiveBuilder::rw_region_alloc(sizeof(PackageEntry));
  memcpy((void*)archived_entry, (void*)this, sizeof(PackageEntry));

  if (_archived_packages_entries == nullptr) {
    _archived_packages_entries = new (mtClass)ArchivedPackageEntries();
  }
  assert(_archived_packages_entries->get(this) == nullptr, "Each PackageEntry must not be shared across PackageEntryTables");
  _archived_packages_entries->put(this, archived_entry);

  return archived_entry;
}

PackageEntry* PackageEntry::get_archived_entry(PackageEntry* orig_entry) {
  PackageEntry** ptr = _archived_packages_entries->get(orig_entry);
  if (ptr != nullptr) {
    return *ptr;
  } else {
    return nullptr;
  }
}

void PackageEntry::iterate_symbols(MetaspaceClosure* closure) {
  closure->push(&_name);
}

void PackageEntry::init_as_archived_entry() {
  Array<ModuleEntry*>* archived_qualified_exports = ModuleEntry::write_growable_array(_qualified_exports);

  _name = ArchiveBuilder::get_buffered_symbol(_name);
  _module = ModuleEntry::get_archived_entry(_module);
  _qualified_exports = (GrowableArray<ModuleEntry*>*)archived_qualified_exports;
  _defined_by_cds_in_class_path = 0;
  JFR_ONLY(set_trace_id(0);) // re-init at runtime

  ArchivePtrMarker::mark_pointer((address*)&_name);
  ArchivePtrMarker::mark_pointer((address*)&_module);
  ArchivePtrMarker::mark_pointer((address*)&_qualified_exports);
}

void PackageEntry::load_from_archive() {
  _qualified_exports = ModuleEntry::restore_growable_array((Array<ModuleEntry*>*)_qualified_exports);
  JFR_ONLY(INIT_ID(this);)
}

static int compare_package_by_name(PackageEntry* a, PackageEntry* b) {
  assert(a == b || a->name() != b->name(), "no duplicated names");
  return a->name()->fast_compare(b->name());
}

void PackageEntryTable::iterate_symbols(MetaspaceClosure* closure) {
  auto syms = [&] (const SymbolHandle& key, PackageEntry*& p) {
      p->iterate_symbols(closure);
  };
  _table.iterate_all(syms);
}

Array<PackageEntry*>* PackageEntryTable::allocate_archived_entries() {
  // First count the packages in named modules
  int n = 0;
  auto count = [&] (const SymbolHandle& key, PackageEntry*& p) {
    if (p->module()->is_named()) {
      n++;
    }
  };
  _table.iterate_all(count);

  Array<PackageEntry*>* archived_packages = ArchiveBuilder::new_rw_array<PackageEntry*>(n);
  // reset n
  n = 0;
  auto grab = [&] (const SymbolHandle& key, PackageEntry*& p) {
    if (p->module()->is_named()) {
      // We don't archive unnamed modules, or packages in unnamed modules. They will be
      // created on-demand at runtime as classes in such packages are loaded.
      archived_packages->at_put(n++, p);
    }
  };
  _table.iterate_all(grab);

  if (n > 1) {
    QuickSort::sort(archived_packages->data(), n, (_sort_Fn)compare_package_by_name, true);
  }
  for (int i = 0; i < n; i++) {
    archived_packages->at_put(i, archived_packages->at(i)->allocate_archived_entry());
    ArchivePtrMarker::mark_pointer((address*)archived_packages->adr_at(i));
  }
  return archived_packages;
}

void PackageEntryTable::init_archived_entries(Array<PackageEntry*>* archived_packages) {
  for (int i = 0; i < archived_packages->length(); i++) {
    PackageEntry* archived_entry = archived_packages->at(i);
    archived_entry->init_as_archived_entry();
  }
}

void PackageEntryTable::load_archived_entries(Array<PackageEntry*>* archived_packages) {
  assert(CDSConfig::is_using_archive(), "runtime only");

  for (int i = 0; i < archived_packages->length(); i++) {
    PackageEntry* archived_entry = archived_packages->at(i);
    archived_entry->load_from_archive();
    _table.put(archived_entry->name(), archived_entry);
  }
}

#endif // INCLUDE_CDS_JAVA_HEAP

// Create package entry in loader's package entry table.  Assume Module lock
// was taken by caller.
void PackageEntryTable::locked_create_entry(Symbol* name, ModuleEntry* module) {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  assert(locked_lookup_only(name) == nullptr, "Package entry already exists");
  PackageEntry* entry = new PackageEntry(name, module);
  bool created = _table.put(name, entry);
  assert(created, "must be");
}

// Create package entry in loader's package entry table if it does not already
// exist.  Assume Module lock was taken by caller.
PackageEntry* PackageEntryTable::locked_create_entry_if_absent(Symbol* name, ModuleEntry* module) {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  // Check if package entry already exists.  If not, create it.
  bool created;
  PackageEntry* entry = new PackageEntry(name, module);
  PackageEntry** old_entry = _table.put_if_absent(name, entry, &created);
  if (created) {
    return entry;
  } else {
    delete entry;
    return *old_entry;
  }
}

PackageEntry* PackageEntryTable::create_entry_if_absent(Symbol* name, ModuleEntry* module) {
  MutexLocker ml(Module_lock);
  return locked_create_entry_if_absent(name, module);
}

PackageEntry* PackageEntryTable::lookup_only(Symbol* name) {
  assert(!Module_lock->owned_by_self(), "should not have the Module_lock - use locked_lookup_only");
  MutexLocker ml(Module_lock);
  return locked_lookup_only(name);
}

PackageEntry* PackageEntryTable::locked_lookup_only(Symbol* name) {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  PackageEntry** entry = _table.get(name);
  return entry == nullptr ? nullptr : *entry;
}

// Called when a define module for java.base is being processed.
// Verify the packages loaded thus far are in java.base's package list.
void PackageEntryTable::verify_javabase_packages(GrowableArray<Symbol*> *pkg_list) {
  assert_lock_strong(Module_lock);
  auto verifier = [&] (const SymbolHandle& name, PackageEntry*& entry) {
    ModuleEntry* m = entry->module();
    Symbol* module_name = (m == nullptr ? nullptr : m->name());
    if (module_name != nullptr &&
      (module_name->fast_compare(vmSymbols::java_base()) == 0) &&
        !pkg_list->contains(entry->name())) {
      ResourceMark rm;
      vm_exit_during_initialization("A non-" JAVA_BASE_NAME " package was loaded prior to module system initialization",
                                    entry->name()->as_C_string());
    }
  };
  _table.iterate_all(verifier);
}

// iteration of qualified exports
void PackageEntry::package_exports_do(ModuleClosure* f) {
  assert_locked_or_safepoint(Module_lock);
  assert(f != nullptr, "invariant");

  if (has_qual_exports_list()) {
    int qe_len = _qualified_exports->length();

    for (int i = 0; i < qe_len; ++i) {
      f->do_module(_qualified_exports->at(i));
    }
  }
}

bool PackageEntry::exported_pending_delete() const {
  assert_locked_or_safepoint(Module_lock);
  return (is_unqual_exported() && _qualified_exports != nullptr);
}

// Remove dead entries from all packages' exported list
void PackageEntryTable::purge_all_package_exports() {
  assert_locked_or_safepoint(Module_lock);
  auto purge = [&] (const SymbolHandle& name, PackageEntry*& entry) {
    if (entry->exported_pending_delete()) {
      // exported list is pending deletion due to a transition
      // from qualified to unqualified
      entry->delete_qualified_exports();
    } else if (entry->is_qual_exported()) {
      entry->purge_qualified_exports();
    }
  };
  _table.iterate_all(purge);
}

void PackageEntryTable::packages_do(void f(PackageEntry*)) {
  auto doit = [&] (const SymbolHandle&name, PackageEntry*& entry) {
    f(entry);
  };
  assert_locked_or_safepoint(Module_lock);
  _table.iterate_all(doit);
}


GrowableArray<PackageEntry*>*  PackageEntryTable::get_system_packages() {
  GrowableArray<PackageEntry*>* loaded_class_pkgs = new GrowableArray<PackageEntry*>(50);
  auto grab = [&] (const SymbolHandle& name, PackageEntry*& entry) {
    if (entry->has_loaded_class()) {
      loaded_class_pkgs->append(entry);
    }
  };

  MutexLocker ml(Module_lock);
  _table.iterate_all(grab);
  // Returns a resource allocated object so caller must have ResourceMark
  return loaded_class_pkgs;
}

void PackageEntryTable::print(outputStream* st) {
  auto printer = [&] (const SymbolHandle& name, PackageEntry*& entry) {
    entry->print(st);
  };
  st->print_cr("Package Entry Table (table_size=%d, entries=%d)",
               _table.table_size(), _table.number_of_entries());
  _table.iterate_all(printer);
}

// This function may be called from debuggers so access private fields directly
// to prevent triggering locking-related asserts that could result from calling
// getter methods.
void PackageEntry::print(outputStream* st) {
  ResourceMark rm;
  st->print_cr("package entry " PTR_FORMAT " name %s module %s classpath_index "
               INT32_FORMAT " is_exported_unqualified %d is_exported_allUnnamed %d ",
               p2i(this), name()->as_C_string(),
               (module()->is_named() ? module()->name()->as_C_string() : UNNAMED_MODULE),
               _classpath_index, _export_flags == PKG_EXP_UNQUALIFIED,
               _export_flags == PKG_EXP_ALLUNNAMED);
}
