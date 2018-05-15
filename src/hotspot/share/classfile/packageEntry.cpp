/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/events.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/ostream.hpp"

// Returns true if this package specifies m as a qualified export, including through an unnamed export
bool PackageEntry::is_qexported_to(ModuleEntry* m) const {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  assert(m != NULL, "No module to lookup in this package's qualified exports list");
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
    _qualified_exports = new (ResourceObj::C_HEAP, mtModule) GrowableArray<ModuleEntry*>(QUAL_EXP_SIZE, true);
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
      (this_pkg_mod == NULL || this_pkg_mod->loader_data() != m_loader_data) &&
      !m_loader_data->is_builtin_class_loader_data()) {
    _must_walk_exports = true;
    if (log_is_enabled(Trace, module)) {
      ResourceMark rm;
      assert(name() != NULL, "PackageEntry without a valid name");
      log_trace(module)("PackageEntry::set_export_walk_required(): package %s defined in module %s, exports list must be walked",
                        name()->as_C_string(),
                        (this_pkg_mod == NULL || this_pkg_mod->name() == NULL) ?
                          UNNAMED_MODULE : this_pkg_mod->name()->as_C_string());
    }
  }
}

// Set the package's exported states based on the value of the ModuleEntry.
void PackageEntry::set_exported(ModuleEntry* m) {
  MutexLocker m1(Module_lock);
  if (is_unqual_exported()) {
    // An exception could be thrown, but choose to simply ignore.
    // Illegal to convert an unqualified exported package to be qualifiedly exported
    return;
  }

  if (m == NULL) {
    // NULL indicates the package is being unqualifiedly exported.  Clean up
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
  if (module()->is_open()) {
    // No-op for open modules since all packages are unqualifiedly exported
    return;
  }

  MutexLocker m1(Module_lock);
  if (!is_unqual_exported()) {
   _export_flags = PKG_EXP_ALLUNNAMED;
  }
}

// Remove dead module entries within the package's exported list.  Note that
// if all of the modules on the _qualified_exports get purged the list does not
// get deleted.  This prevents the package from illegally transitioning from
// exported to non-exported.
void PackageEntry::purge_qualified_exports() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  if (_must_walk_exports &&
      _qualified_exports != NULL &&
      !_qualified_exports->is_empty()) {
    ModuleEntry* pkg_module = module();

    // This package's _must_walk_exports flag will be reset based
    // on the remaining live modules on the exports list.
    _must_walk_exports = false;

    if (log_is_enabled(Trace, module)) {
      ResourceMark rm;
      assert(name() != NULL, "PackageEntry without a valid name");
      ModuleEntry* pkg_mod = module();
      log_trace(module)("PackageEntry::purge_qualified_exports(): package %s defined in module %s, exports list being walked",
                        name()->as_C_string(),
                        (pkg_mod == NULL || pkg_mod->name() == NULL) ? UNNAMED_MODULE : pkg_mod->name()->as_C_string());
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
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  if (_qualified_exports != NULL) {
    delete _qualified_exports;
  }
  _qualified_exports = NULL;
}

PackageEntryTable::PackageEntryTable(int table_size)
  : Hashtable<Symbol*, mtModule>(table_size, sizeof(PackageEntry))
{
}

PackageEntryTable::~PackageEntryTable() {
  // Walk through all buckets and all entries in each bucket,
  // freeing each entry.
  for (int i = 0; i < table_size(); ++i) {
    for (PackageEntry* p = bucket(i); p != NULL;) {
      PackageEntry* to_remove = p;
      // read next before freeing.
      p = p->next();

      // Clean out the C heap allocated qualified exports list first before freeing the entry
      to_remove->delete_qualified_exports();
      to_remove->name()->decrement_refcount();

      // Unlink from the Hashtable prior to freeing
      unlink_entry(to_remove);
      FREE_C_HEAP_ARRAY(char, to_remove);
    }
  }
  assert(number_of_entries() == 0, "should have removed all entries");
  assert(new_entry_free_list() == NULL, "entry present on PackageEntryTable's free list");
  free_buckets();
}

PackageEntry* PackageEntryTable::new_entry(unsigned int hash, Symbol* name, ModuleEntry* module) {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  PackageEntry* entry = (PackageEntry*)Hashtable<Symbol*, mtModule>::allocate_new_entry(hash, name);

  JFR_ONLY(INIT_ID(entry);)

  // Initialize fields specific to a PackageEntry
  entry->init();
  entry->name()->increment_refcount();
  entry->set_module(module);
  return entry;
}

void PackageEntryTable::add_entry(int index, PackageEntry* new_entry) {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  Hashtable<Symbol*, mtModule>::add_entry(index, (HashtableEntry<Symbol*, mtModule>*)new_entry);
}

// Create package in loader's package entry table and return the entry.
// If entry already exists, return null.  Assume Module lock was taken by caller.
PackageEntry* PackageEntryTable::locked_create_entry_or_null(Symbol* name, ModuleEntry* module) {
  assert(Module_lock->owned_by_self(), "should have the Module_lock");
  // Check if package already exists.  Return NULL if it does.
  if (lookup_only(name) != NULL) {
    return NULL;
  } else {
    PackageEntry* entry = new_entry(compute_hash(name), name, module);
    add_entry(index_for(name), entry);
    return entry;
  }
}

PackageEntry* PackageEntryTable::lookup(Symbol* name, ModuleEntry* module) {
  PackageEntry* p = lookup_only(name);
  if (p != NULL) {
    return p;
  } else {
    // If not found, add to table. Grab the PackageEntryTable lock first.
    MutexLocker ml(Module_lock);

    // Since look-up was done lock-free, we need to check if another thread beat
    // us in the race to insert the package.
    PackageEntry* test = lookup_only(name);
    if (test != NULL) {
      // A race occurred and another thread introduced the package.
      return test;
    } else {
      assert(module != NULL, "module should never be null");
      PackageEntry* entry = new_entry(compute_hash(name), name, module);
      add_entry(index_for(name), entry);
      return entry;
    }
  }
}

PackageEntry* PackageEntryTable::lookup_only(Symbol* name) {
  int index = index_for(name);
  for (PackageEntry* p = bucket(index); p != NULL; p = p->next()) {
    if (p->name()->fast_compare(name) == 0) {
      return p;
    }
  }
  return NULL;
}

// Called when a define module for java.base is being processed.
// Verify the packages loaded thus far are in java.base's package list.
void PackageEntryTable::verify_javabase_packages(GrowableArray<Symbol*> *pkg_list) {
  assert_lock_strong(Module_lock);
  for (int i = 0; i < table_size(); i++) {
    for (PackageEntry* entry = bucket(i);
                       entry != NULL;
                       entry = entry->next()) {
      ModuleEntry* m = entry->module();
      Symbol* module_name = (m == NULL ? NULL : m->name());
      if (module_name != NULL &&
          (module_name->fast_compare(vmSymbols::java_base()) == 0) &&
          !pkg_list->contains(entry->name())) {
        ResourceMark rm;
        vm_exit_during_initialization("A non-" JAVA_BASE_NAME " package was loaded prior to module system initialization", entry->name()->as_C_string());
      }
    }
  }

}

// iteration of qualified exports
void PackageEntry::package_exports_do(ModuleClosure* f) {
  assert_locked_or_safepoint(Module_lock);
  assert(f != NULL, "invariant");

  if (has_qual_exports_list()) {
    int qe_len = _qualified_exports->length();

    for (int i = 0; i < qe_len; ++i) {
      f->do_module(_qualified_exports->at(i));
    }
  }
}

bool PackageEntry::exported_pending_delete() const {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  return (is_unqual_exported() && _qualified_exports != NULL);
}

// Remove dead entries from all packages' exported list
void PackageEntryTable::purge_all_package_exports() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  for (int i = 0; i < table_size(); i++) {
    for (PackageEntry* entry = bucket(i);
                       entry != NULL;
                       entry = entry->next()) {
      if (entry->exported_pending_delete()) {
        // exported list is pending deletion due to a transition
        // from qualified to unqualified
        entry->delete_qualified_exports();
      } else if (entry->is_qual_exported()) {
        entry->purge_qualified_exports();
      }
    }
  }
}

void PackageEntryTable::print(outputStream* st) {
  st->print_cr("Package Entry Table (table_size=%d, entries=%d)",
               table_size(), number_of_entries());
  for (int i = 0; i < table_size(); i++) {
    for (PackageEntry* probe = bucket(i);
                       probe != NULL;
                       probe = probe->next()) {
      probe->print(st);
    }
  }
}

// This function may be called from debuggers so access private fields directly
// to prevent triggering locking-related asserts that could result from calling
// getter methods.
void PackageEntry::print(outputStream* st) {
  ResourceMark rm;
  st->print_cr("package entry " PTR_FORMAT " name %s module %s classpath_index "
               INT32_FORMAT " is_exported_unqualified %d is_exported_allUnnamed %d " "next " PTR_FORMAT,
               p2i(this), name()->as_C_string(),
               (module()->is_named() ? module()->name()->as_C_string() : UNNAMED_MODULE),
               _classpath_index, _export_flags == PKG_EXP_UNQUALIFIED,
               _export_flags == PKG_EXP_ALLUNNAMED, p2i(next()));
}

void PackageEntryTable::verify() {
  verify_table<PackageEntry>("Package Entry Table");
}

void PackageEntry::verify() {
  guarantee(name() != NULL, "A package entry must have a corresponding symbol name.");
}
