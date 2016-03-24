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
#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "memory/resourceArea.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "trace/traceMacros.hpp"
#include "utilities/events.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/hashtable.inline.hpp"

// Return true if this package is exported to m.
bool PackageEntry::is_qexported_to(ModuleEntry* m) const {
  assert(m != NULL, "No module to lookup in this package's qualified exports list");
  MutexLocker m1(Module_lock);
  if (!_is_exported) {
    return false;
  } else if (_is_exported_allUnnamed && !m->is_named()) {
    return true;
  } else if (_qualified_exports == NULL) {
    return false;
  } else {
    return _qualified_exports->contains(m);
  }
}

// Add a module to the package's qualified export list.
void PackageEntry::add_qexport(ModuleEntry* m) {
  assert_locked_or_safepoint(Module_lock);
  assert(_is_exported == true, "Adding a qualified export to a package that is not exported");
  if (_qualified_exports == NULL) {
    // Lazily create a package's qualified exports list.
    // Initial size is small, do not anticipate export lists to be large.
    _qualified_exports =
      new (ResourceObj::C_HEAP, mtClass) GrowableArray<ModuleEntry*>(QUAL_EXP_SIZE, true);
  }
  _qualified_exports->append_if_missing(m);
}

// Set the package's exported state based on the value of the ModuleEntry.
void PackageEntry::set_exported(ModuleEntry* m) {
  MutexLocker m1(Module_lock);
  if (is_unqual_exported()) {
    // An exception could be thrown, but choose to simply ignore.
    // Illegal to convert an unqualified exported package to be qualifiedly exported
    return;
  }

  if (m == NULL) {
    // NULL indicates the package is being unqualifiedly exported
    if (_is_exported && _qualified_exports != NULL) {
      // Legit to transition a package from being qualifiedly exported
      // to unqualified.  Clean up the qualified lists at the next
      // safepoint.
      _exported_pending_delete = _qualified_exports;
    }

    // Mark package as unqualifiedly exported
    set_unqual_exported();

  } else {
    // Add the exported module
    _is_exported = true;
    add_qexport(m);
  }
}

// Remove dead module entries within the package's exported list.
void PackageEntry::purge_qualified_exports() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  if (_qualified_exports != NULL) {
    // Go backwards because this removes entries that are dead.
    int len = _qualified_exports->length();
    for (int idx = len - 1; idx >= 0; idx--) {
      ModuleEntry* module_idx = _qualified_exports->at(idx);
      ClassLoaderData* cld = module_idx->loader();
      if (cld->is_unloading()) {
        _qualified_exports->delete_at(idx);
      }
    }
  }
}

void PackageEntry::delete_qualified_exports() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  if (_exported_pending_delete != NULL) {
    // If a transition occurred from qualified to unqualified, the _qualified_exports
    // field should have been NULL'ed out.
    assert(_qualified_exports == NULL, "Package's exported pending delete, exported list should not be active");
    delete _exported_pending_delete;
  }

  if (_qualified_exports != NULL) {
    delete _qualified_exports;
  }

  _exported_pending_delete = NULL;
  _qualified_exports = NULL;
}

PackageEntryTable::PackageEntryTable(int table_size)
  : Hashtable<Symbol*, mtClass>(table_size, sizeof(PackageEntry))
{
}

PackageEntryTable::~PackageEntryTable() {
  assert_locked_or_safepoint(Module_lock);

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
  assert_locked_or_safepoint(Module_lock);
  PackageEntry* entry = (PackageEntry*) NEW_C_HEAP_ARRAY(char, entry_size(), mtClass);

  // Initialize everything BasicHashtable would
  entry->set_next(NULL);
  entry->set_hash(hash);
  entry->set_literal(name);

  TRACE_INIT_PACKAGE_ID(entry);

  // Initialize fields specific to a PackageEntry
  entry->init();
  entry->name()->increment_refcount();
  if (!module->is_named()) {
    // Set the exported state to true because all packages
    // within the unnamed module are unqualifiedly exported
    entry->set_exported(true);
  }
  entry->set_module(module);
  return entry;
}

void PackageEntryTable::add_entry(int index, PackageEntry* new_entry) {
  assert_locked_or_safepoint(Module_lock);
  Hashtable<Symbol*, mtClass>::add_entry(index, (HashtableEntry<Symbol*, mtClass>*)new_entry);
}

// Create package in loader's package entry table and return the entry.
// If entry already exists, return null.  Assume Module lock was taken by caller.
PackageEntry* PackageEntryTable::locked_create_entry_or_null(Symbol* name, ModuleEntry* module) {
  assert_locked_or_safepoint(Module_lock);
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
        vm_exit_during_initialization("A non-java.base package was loaded prior to module system initialization", entry->name()->as_C_string());
      }
    }
  }

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

#ifndef PRODUCT
void PackageEntryTable::print() {
  tty->print_cr("Package Entry Table (table_size=%d, entries=%d)",
                table_size(), number_of_entries());
  for (int i = 0; i < table_size(); i++) {
    for (PackageEntry* probe = bucket(i);
                       probe != NULL;
                       probe = probe->next()) {
      probe->print();
    }
  }
}

void PackageEntry::print() {
  ResourceMark rm;
  tty->print_cr("package entry "PTR_FORMAT" name %s module %s classpath_index "
                INT32_FORMAT " is_exported %d is_exported_allUnnamed %d " "next "PTR_FORMAT,
                p2i(this), name()->as_C_string(),
                (module()->is_named() ? module()->name()->as_C_string() : UNNAMED_MODULE),
                _classpath_index, _is_exported, _is_exported_allUnnamed, p2i(next()));
}
#endif

void PackageEntryTable::verify() {
  int element_count = 0;
  for (int index = 0; index < table_size(); index++) {
    for (PackageEntry* probe = bucket(index);
                              probe != NULL;
                              probe = probe->next()) {
      probe->verify();
      element_count++;
    }
  }
  guarantee(number_of_entries() == element_count,
            "Verify of Package Entry Table failed");
  debug_only(verify_lookup_length((double)number_of_entries() / table_size()));
}

void PackageEntry::verify() {
  guarantee(name() != NULL, "A package entry must have a corresponding symbol name.");
}

// iteration of qualified exports
void PackageEntry::package_exports_do(ModuleClosure* const f) {
  assert_locked_or_safepoint(Module_lock);
  assert(f != NULL, "invariant");

  if (is_qual_exported()) {
    int qe_len = _qualified_exports->length();

    for (int i = 0; i < qe_len; ++i) {
      f->do_module(_qualified_exports->at(i));
    }
  }
}
