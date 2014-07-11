/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/dictionary.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiRedefineClassesTrace.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "utilities/hashtable.inline.hpp"

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

DictionaryEntry*  Dictionary::_current_class_entry = NULL;
int               Dictionary::_current_class_index =    0;


Dictionary::Dictionary(int table_size)
  : TwoOopHashtable<Klass*, mtClass>(table_size, sizeof(DictionaryEntry)) {
  _current_class_index = 0;
  _current_class_entry = NULL;
  _pd_cache_table = new ProtectionDomainCacheTable(defaultProtectionDomainCacheSize);
};


Dictionary::Dictionary(int table_size, HashtableBucket<mtClass>* t,
                       int number_of_entries)
  : TwoOopHashtable<Klass*, mtClass>(table_size, sizeof(DictionaryEntry), t, number_of_entries) {
  _current_class_index = 0;
  _current_class_entry = NULL;
  _pd_cache_table = new ProtectionDomainCacheTable(defaultProtectionDomainCacheSize);
};

ProtectionDomainCacheEntry* Dictionary::cache_get(oop protection_domain) {
  return _pd_cache_table->get(protection_domain);
}

DictionaryEntry* Dictionary::new_entry(unsigned int hash, Klass* klass,
                                       ClassLoaderData* loader_data) {
  DictionaryEntry* entry = (DictionaryEntry*)Hashtable<Klass*, mtClass>::new_entry(hash, klass);
  entry->set_loader_data(loader_data);
  entry->set_pd_set(NULL);
  assert(klass->oop_is_instance(), "Must be");
  return entry;
}


void Dictionary::free_entry(DictionaryEntry* entry) {
  // avoid recursion when deleting linked list
  while (entry->pd_set() != NULL) {
    ProtectionDomainEntry* to_delete = entry->pd_set();
    entry->set_pd_set(to_delete->next());
    delete to_delete;
  }
  Hashtable<Klass*, mtClass>::free_entry(entry);
}


bool DictionaryEntry::contains_protection_domain(oop protection_domain) const {
#ifdef ASSERT
  if (protection_domain == InstanceKlass::cast(klass())->protection_domain()) {
    // Ensure this doesn't show up in the pd_set (invariant)
    bool in_pd_set = false;
    for (ProtectionDomainEntry* current = _pd_set;
                                current != NULL;
                                current = current->next()) {
      if (current->protection_domain() == protection_domain) {
        in_pd_set = true;
        break;
      }
    }
    if (in_pd_set) {
      assert(false, "A klass's protection domain should not show up "
                    "in its sys. dict. PD set");
    }
  }
#endif /* ASSERT */

  if (protection_domain == InstanceKlass::cast(klass())->protection_domain()) {
    // Succeeds trivially
    return true;
  }

  for (ProtectionDomainEntry* current = _pd_set;
                              current != NULL;
                              current = current->next()) {
    if (current->protection_domain() == protection_domain) return true;
  }
  return false;
}


void DictionaryEntry::add_protection_domain(Dictionary* dict, oop protection_domain) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  if (!contains_protection_domain(protection_domain)) {
    ProtectionDomainCacheEntry* entry = dict->cache_get(protection_domain);
    ProtectionDomainEntry* new_head =
                new ProtectionDomainEntry(entry, _pd_set);
    // Warning: Preserve store ordering.  The SystemDictionary is read
    //          without locks.  The new ProtectionDomainEntry must be
    //          complete before other threads can be allowed to see it
    //          via a store to _pd_set.
    OrderAccess::release_store_ptr(&_pd_set, new_head);
  }
  if (TraceProtectionDomainVerification && WizardMode) {
    print();
  }
}


bool Dictionary::do_unloading() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  bool class_was_unloaded = false;
  int  index = 0; // Defined here for portability! Do not move

  // Remove unloadable entries and classes from system dictionary
  // The placeholder array has been handled in always_strong_oops_do.
  DictionaryEntry* probe = NULL;
  for (index = 0; index < table_size(); index++) {
    for (DictionaryEntry** p = bucket_addr(index); *p != NULL; ) {
      probe = *p;
      Klass* e = probe->klass();
      ClassLoaderData* loader_data = probe->loader_data();

      InstanceKlass* ik = InstanceKlass::cast(e);

      // Non-unloadable classes were handled in always_strong_oops_do
      if (!is_strongly_reachable(loader_data, e)) {
        // Entry was not visited in phase1 (negated test from phase1)
        assert(!loader_data->is_the_null_class_loader_data(), "unloading entry with null class loader");
        ClassLoaderData* k_def_class_loader_data = ik->class_loader_data();

        // Do we need to delete this system dictionary entry?
        bool purge_entry = false;

        // Do we need to delete this system dictionary entry?
        if (loader_data->is_unloading()) {
          // If the loader is not live this entry should always be
          // removed (will never be looked up again). Note that this is
          // not the same as unloading the referred class.
          if (k_def_class_loader_data == loader_data) {
            // This is the defining entry, so the referred class is about
            // to be unloaded.
            class_was_unloaded = true;
          }
          // Also remove this system dictionary entry.
          purge_entry = true;

        } else {
          // The loader in this entry is alive. If the klass is dead,
          // (determined by checking the defining class loader)
          // the loader must be an initiating loader (rather than the
          // defining loader). Remove this entry.
          if (k_def_class_loader_data->is_unloading()) {
            // If we get here, the class_loader_data must not be the defining
            // loader, it must be an initiating one.
            assert(k_def_class_loader_data != loader_data,
                   "cannot have live defining loader and unreachable klass");
            // Loader is live, but class and its defining loader are dead.
            // Remove the entry. The class is going away.
            purge_entry = true;
          }
        }

        if (purge_entry) {
          *p = probe->next();
          if (probe == _current_class_entry) {
            _current_class_entry = NULL;
          }
          free_entry(probe);
          continue;
        }
      }
      p = probe->next_addr();
    }
  }
  return class_was_unloaded;
}

void Dictionary::roots_oops_do(OopClosure* strong, OopClosure* weak) {
  // Skip the strong roots probe marking if the closures are the same.
  if (strong == weak) {
    oops_do(strong);
    return;
  }

  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry *probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      Klass* e = probe->klass();
      ClassLoaderData* loader_data = probe->loader_data();
      if (is_strongly_reachable(loader_data, e)) {
        probe->set_strongly_reachable();
      }
    }
  }
  _pd_cache_table->roots_oops_do(strong, weak);
}

void Dictionary::always_strong_oops_do(OopClosure* blk) {
  // Follow all system classes and temporary placeholders in dictionary; only
  // protection domain oops contain references into the heap. In a first
  // pass over the system dictionary determine which need to be treated as
  // strongly reachable and mark them as such.
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry *probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      Klass* e = probe->klass();
      ClassLoaderData* loader_data = probe->loader_data();
      if (is_strongly_reachable(loader_data, e)) {
        probe->set_strongly_reachable();
      }
    }
  }
  // Then iterate over the protection domain cache to apply the closure on the
  // previously marked ones.
  _pd_cache_table->always_strong_oops_do(blk);
}


void Dictionary::always_strong_classes_do(KlassClosure* closure) {
  // Follow all system classes and temporary placeholders in dictionary
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      Klass* e = probe->klass();
      ClassLoaderData* loader_data = probe->loader_data();
      if (is_strongly_reachable(loader_data, e)) {
        closure->do_klass(e);
      }
    }
  }
}


//   Just the classes from defining class loaders
void Dictionary::classes_do(void f(Klass*)) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      Klass* k = probe->klass();
      if (probe->loader_data() == InstanceKlass::cast(k)->class_loader_data()) {
        f(k);
      }
    }
  }
}

// Added for initialize_itable_for_klass to handle exceptions
//   Just the classes from defining class loaders
void Dictionary::classes_do(void f(Klass*, TRAPS), TRAPS) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      Klass* k = probe->klass();
      if (probe->loader_data() == InstanceKlass::cast(k)->class_loader_data()) {
        f(k, CHECK);
      }
    }
  }
}

//   All classes, and their class loaders
// Don't iterate over placeholders
void Dictionary::classes_do(void f(Klass*, ClassLoaderData*)) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      Klass* k = probe->klass();
      f(k, probe->loader_data());
    }
  }
}

void Dictionary::oops_do(OopClosure* f) {
  // Only the protection domain oops contain references into the heap. Iterate
  // over all of them.
  _pd_cache_table->oops_do(f);
}

void Dictionary::methods_do(void f(Method*)) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      Klass* k = probe->klass();
      if (probe->loader_data() == InstanceKlass::cast(k)->class_loader_data()) {
        // only take klass is we have the entry with the defining class loader
        InstanceKlass::cast(k)->methods_do(f);
      }
    }
  }
}

void Dictionary::unlink(BoolObjectClosure* is_alive) {
  // Only the protection domain cache table may contain references to the heap
  // that need to be unlinked.
  _pd_cache_table->unlink(is_alive);
}

Klass* Dictionary::try_get_next_class() {
  while (true) {
    if (_current_class_entry != NULL) {
      Klass* k = _current_class_entry->klass();
      _current_class_entry = _current_class_entry->next();
      return k;
    }
    _current_class_index = (_current_class_index + 1) % table_size();
    _current_class_entry = bucket(_current_class_index);
  }
  // never reached
}

// Add a loaded class to the system dictionary.
// Readers of the SystemDictionary aren't always locked, so _buckets
// is volatile. The store of the next field in the constructor is
// also cast to volatile;  we do this to ensure store order is maintained
// by the compilers.

void Dictionary::add_klass(Symbol* class_name, ClassLoaderData* loader_data,
                           KlassHandle obj) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(obj() != NULL, "adding NULL obj");
  assert(obj()->name() == class_name, "sanity check on name");
  assert(loader_data != NULL, "Must be non-NULL");

  unsigned int hash = compute_hash(class_name, loader_data);
  int index = hash_to_index(hash);
  DictionaryEntry* entry = new_entry(hash, obj(), loader_data);
  add_entry(index, entry);
}


// This routine does not lock the system dictionary.
//
// Since readers don't hold a lock, we must make sure that system
// dictionary entries are only removed at a safepoint (when only one
// thread is running), and are added to in a safe way (all links must
// be updated in an MT-safe manner).
//
// Callers should be aware that an entry could be added just after
// _buckets[index] is read here, so the caller will not see the new entry.
DictionaryEntry* Dictionary::get_entry(int index, unsigned int hash,
                                       Symbol* class_name,
                                       ClassLoaderData* loader_data) {
  debug_only(_lookup_count++);
  for (DictionaryEntry* entry = bucket(index);
                        entry != NULL;
                        entry = entry->next()) {
    if (entry->hash() == hash && entry->equals(class_name, loader_data)) {
      return entry;
    }
    debug_only(_lookup_length++);
  }
  return NULL;
}


Klass* Dictionary::find(int index, unsigned int hash, Symbol* name,
                          ClassLoaderData* loader_data, Handle protection_domain, TRAPS) {
  DictionaryEntry* entry = get_entry(index, hash, name, loader_data);
  if (entry != NULL && entry->is_valid_protection_domain(protection_domain)) {
    return entry->klass();
  } else {
    return NULL;
  }
}


Klass* Dictionary::find_class(int index, unsigned int hash,
                                Symbol* name, ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert (index == index_for(name, loader_data), "incorrect index?");

  DictionaryEntry* entry = get_entry(index, hash, name, loader_data);
  return (entry != NULL) ? entry->klass() : (Klass*)NULL;
}


// Variant of find_class for shared classes.  No locking required, as
// that table is static.

Klass* Dictionary::find_shared_class(int index, unsigned int hash,
                                       Symbol* name) {
  assert (index == index_for(name, NULL), "incorrect index?");

  DictionaryEntry* entry = get_entry(index, hash, name, NULL);
  return (entry != NULL) ? entry->klass() : (Klass*)NULL;
}


void Dictionary::add_protection_domain(int index, unsigned int hash,
                                       instanceKlassHandle klass,
                                       ClassLoaderData* loader_data, Handle protection_domain,
                                       TRAPS) {
  Symbol*  klass_name = klass->name();
  DictionaryEntry* entry = get_entry(index, hash, klass_name, loader_data);

  assert(entry != NULL,"entry must be present, we just created it");
  assert(protection_domain() != NULL,
         "real protection domain should be present");

  entry->add_protection_domain(this, protection_domain());

  assert(entry->contains_protection_domain(protection_domain()),
         "now protection domain should be present");
}


bool Dictionary::is_valid_protection_domain(int index, unsigned int hash,
                                            Symbol* name,
                                            ClassLoaderData* loader_data,
                                            Handle protection_domain) {
  DictionaryEntry* entry = get_entry(index, hash, name, loader_data);
  return entry->is_valid_protection_domain(protection_domain);
}


void Dictionary::reorder_dictionary() {

  // Copy all the dictionary entries into a single master list.

  DictionaryEntry* master_list = NULL;
  for (int i = 0; i < table_size(); ++i) {
    DictionaryEntry* p = bucket(i);
    while (p != NULL) {
      DictionaryEntry* tmp;
      tmp = p->next();
      p->set_next(master_list);
      master_list = p;
      p = tmp;
    }
    set_entry(i, NULL);
  }

  // Add the dictionary entries back to the list in the correct buckets.
  while (master_list != NULL) {
    DictionaryEntry* p = master_list;
    master_list = master_list->next();
    p->set_next(NULL);
    Symbol* class_name = InstanceKlass::cast((Klass*)(p->klass()))->name();
    // Since the null class loader data isn't copied to the CDS archive,
    // compute the hash with NULL for loader data.
    unsigned int hash = compute_hash(class_name, NULL);
    int index = hash_to_index(hash);
    p->set_hash(hash);
    p->set_loader_data(NULL);   // loader_data isn't copied to CDS
    p->set_next(bucket(index));
    set_entry(index, p);
  }
}

ProtectionDomainCacheTable::ProtectionDomainCacheTable(int table_size)
  : Hashtable<oop, mtClass>(table_size, sizeof(ProtectionDomainCacheEntry))
{
}

void ProtectionDomainCacheTable::unlink(BoolObjectClosure* is_alive) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  for (int i = 0; i < table_size(); ++i) {
    ProtectionDomainCacheEntry** p = bucket_addr(i);
    ProtectionDomainCacheEntry* entry = bucket(i);
    while (entry != NULL) {
      if (is_alive->do_object_b(entry->literal())) {
        p = entry->next_addr();
      } else {
        *p = entry->next();
        free_entry(entry);
      }
      entry = *p;
    }
  }
}

void ProtectionDomainCacheTable::oops_do(OopClosure* f) {
  for (int index = 0; index < table_size(); index++) {
    for (ProtectionDomainCacheEntry* probe = bucket(index);
                                     probe != NULL;
                                     probe = probe->next()) {
      probe->oops_do(f);
    }
  }
}

void ProtectionDomainCacheTable::roots_oops_do(OopClosure* strong, OopClosure* weak) {
  for (int index = 0; index < table_size(); index++) {
    for (ProtectionDomainCacheEntry* probe = bucket(index);
                                     probe != NULL;
                                     probe = probe->next()) {
      if (probe->is_strongly_reachable()) {
        probe->reset_strongly_reachable();
        probe->oops_do(strong);
      } else {
        if (weak != NULL) {
          probe->oops_do(weak);
        }
      }
    }
  }
}

uint ProtectionDomainCacheTable::bucket_size() {
  return sizeof(ProtectionDomainCacheEntry);
}

#ifndef PRODUCT
void ProtectionDomainCacheTable::print() {
  tty->print_cr("Protection domain cache table (table_size=%d, classes=%d)",
                table_size(), number_of_entries());
  for (int index = 0; index < table_size(); index++) {
    for (ProtectionDomainCacheEntry* probe = bucket(index);
                                     probe != NULL;
                                     probe = probe->next()) {
      probe->print();
    }
  }
}

void ProtectionDomainCacheEntry::print() {
  tty->print_cr("entry "PTR_FORMAT" value "PTR_FORMAT" strongly_reachable %d next "PTR_FORMAT,
                this, (void*)literal(), _strongly_reachable, next());
}
#endif

void ProtectionDomainCacheTable::verify() {
  int element_count = 0;
  for (int index = 0; index < table_size(); index++) {
    for (ProtectionDomainCacheEntry* probe = bucket(index);
                                     probe != NULL;
                                     probe = probe->next()) {
      probe->verify();
      element_count++;
    }
  }
  guarantee(number_of_entries() == element_count,
            "Verify of protection domain cache table failed");
  debug_only(verify_lookup_length((double)number_of_entries() / table_size()));
}

void ProtectionDomainCacheEntry::verify() {
  guarantee(literal()->is_oop(), "must be an oop");
}

void ProtectionDomainCacheTable::always_strong_oops_do(OopClosure* f) {
  // the caller marked the protection domain cache entries that we need to apply
  // the closure on. Only process them.
  for (int index = 0; index < table_size(); index++) {
    for (ProtectionDomainCacheEntry* probe = bucket(index);
                                     probe != NULL;
                                     probe = probe->next()) {
      if (probe->is_strongly_reachable()) {
        probe->reset_strongly_reachable();
        probe->oops_do(f);
      }
    }
  }
}

ProtectionDomainCacheEntry* ProtectionDomainCacheTable::get(oop protection_domain) {
  unsigned int hash = compute_hash(protection_domain);
  int index = hash_to_index(hash);

  ProtectionDomainCacheEntry* entry = find_entry(index, protection_domain);
  if (entry == NULL) {
    entry = add_entry(index, hash, protection_domain);
  }
  return entry;
}

ProtectionDomainCacheEntry* ProtectionDomainCacheTable::find_entry(int index, oop protection_domain) {
  for (ProtectionDomainCacheEntry* e = bucket(index); e != NULL; e = e->next()) {
    if (e->protection_domain() == protection_domain) {
      return e;
    }
  }

  return NULL;
}

ProtectionDomainCacheEntry* ProtectionDomainCacheTable::add_entry(int index, unsigned int hash, oop protection_domain) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(index == index_for(protection_domain), "incorrect index?");
  assert(find_entry(index, protection_domain) == NULL, "no double entry");

  ProtectionDomainCacheEntry* p = new_entry(hash, protection_domain);
  Hashtable<oop, mtClass>::add_entry(index, p);
  return p;
}

void ProtectionDomainCacheTable::free(ProtectionDomainCacheEntry* to_delete) {
  unsigned int hash = compute_hash(to_delete->protection_domain());
  int index = hash_to_index(hash);

  ProtectionDomainCacheEntry** p = bucket_addr(index);
  ProtectionDomainCacheEntry* entry = bucket(index);
  while (true) {
    assert(entry != NULL, "sanity");

    if (entry == to_delete) {
      *p = entry->next();
      Hashtable<oop, mtClass>::free_entry(entry);
      break;
    } else {
      p = entry->next_addr();
      entry = *p;
    }
  }
}

SymbolPropertyTable::SymbolPropertyTable(int table_size)
  : Hashtable<Symbol*, mtSymbol>(table_size, sizeof(SymbolPropertyEntry))
{
}
SymbolPropertyTable::SymbolPropertyTable(int table_size, HashtableBucket<mtSymbol>* t,
                                         int number_of_entries)
  : Hashtable<Symbol*, mtSymbol>(table_size, sizeof(SymbolPropertyEntry), t, number_of_entries)
{
}


SymbolPropertyEntry* SymbolPropertyTable::find_entry(int index, unsigned int hash,
                                                     Symbol* sym,
                                                     intptr_t sym_mode) {
  assert(index == index_for(sym, sym_mode), "incorrect index?");
  for (SymbolPropertyEntry* p = bucket(index); p != NULL; p = p->next()) {
    if (p->hash() == hash && p->symbol() == sym && p->symbol_mode() == sym_mode) {
      return p;
    }
  }
  return NULL;
}


SymbolPropertyEntry* SymbolPropertyTable::add_entry(int index, unsigned int hash,
                                                    Symbol* sym, intptr_t sym_mode) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(index == index_for(sym, sym_mode), "incorrect index?");
  assert(find_entry(index, hash, sym, sym_mode) == NULL, "no double entry");

  SymbolPropertyEntry* p = new_entry(hash, sym, sym_mode);
  Hashtable<Symbol*, mtSymbol>::add_entry(index, p);
  return p;
}

void SymbolPropertyTable::oops_do(OopClosure* f) {
  for (int index = 0; index < table_size(); index++) {
    for (SymbolPropertyEntry* p = bucket(index); p != NULL; p = p->next()) {
      if (p->method_type() != NULL) {
        f->do_oop(p->method_type_addr());
      }
    }
  }
}

void SymbolPropertyTable::methods_do(void f(Method*)) {
  for (int index = 0; index < table_size(); index++) {
    for (SymbolPropertyEntry* p = bucket(index); p != NULL; p = p->next()) {
      Method* prop = p->method();
      if (prop != NULL) {
        f((Method*)prop);
      }
    }
  }
}


// ----------------------------------------------------------------------------
#ifndef PRODUCT

void Dictionary::print() {
  ResourceMark rm;
  HandleMark   hm;

  tty->print_cr("Java system dictionary (table_size=%d, classes=%d)",
                 table_size(), number_of_entries());
  tty->print_cr("^ indicates that initiating loader is different from "
                "defining loader");

  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      if (Verbose) tty->print("%4d: ", index);
      Klass* e = probe->klass();
      ClassLoaderData* loader_data =  probe->loader_data();
      bool is_defining_class =
         (loader_data == InstanceKlass::cast(e)->class_loader_data());
      tty->print("%s%s", is_defining_class ? " " : "^",
                   e->external_name());

        tty->print(", loader ");
      loader_data->print_value();
      tty->cr();
    }
  }
  tty->cr();
  _pd_cache_table->print();
  tty->cr();
}

#endif

void Dictionary::verify() {
  guarantee(number_of_entries() >= 0, "Verify of system dictionary failed");

  int element_count = 0;
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      Klass* e = probe->klass();
      ClassLoaderData* loader_data = probe->loader_data();
      guarantee(e->oop_is_instance(),
                              "Verify of system dictionary failed");
      // class loader must be present;  a null class loader is the
      // boostrap loader
      guarantee(loader_data != NULL || DumpSharedSpaces ||
                loader_data->class_loader() == NULL ||
                loader_data->class_loader()->is_instance(),
                "checking type of class_loader");
      e->verify();
      probe->verify_protection_domain_set();
      element_count++;
    }
  }
  guarantee(number_of_entries() == element_count,
            "Verify of system dictionary failed");
  debug_only(verify_lookup_length((double)number_of_entries() / table_size()));

  _pd_cache_table->verify();
}

