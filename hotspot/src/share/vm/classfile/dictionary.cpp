/*
 * Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_dictionary.cpp.incl"


DictionaryEntry*  Dictionary::_current_class_entry = NULL;
int               Dictionary::_current_class_index =    0;


Dictionary::Dictionary(int table_size)
  : TwoOopHashtable(table_size, sizeof(DictionaryEntry)) {
  _current_class_index = 0;
  _current_class_entry = NULL;
};



Dictionary::Dictionary(int table_size, HashtableBucket* t,
                       int number_of_entries)
  : TwoOopHashtable(table_size, sizeof(DictionaryEntry), t, number_of_entries) {
  _current_class_index = 0;
  _current_class_entry = NULL;
};


DictionaryEntry* Dictionary::new_entry(unsigned int hash, klassOop klass,
                                       oop loader) {
  DictionaryEntry* entry;
  entry = (DictionaryEntry*)Hashtable::new_entry(hash, klass);
  entry->set_loader(loader);
  entry->set_pd_set(NULL);
  return entry;
}


DictionaryEntry* Dictionary::new_entry() {
  DictionaryEntry* entry = (DictionaryEntry*)Hashtable::new_entry(0L, NULL);
  entry->set_loader(NULL);
  entry->set_pd_set(NULL);
  return entry;
}


void Dictionary::free_entry(DictionaryEntry* entry) {
  // avoid recursion when deleting linked list
  while (entry->pd_set() != NULL) {
    ProtectionDomainEntry* to_delete = entry->pd_set();
    entry->set_pd_set(to_delete->next());
    delete to_delete;
  }
  Hashtable::free_entry(entry);
}


bool DictionaryEntry::contains_protection_domain(oop protection_domain) const {
#ifdef ASSERT
  if (protection_domain == instanceKlass::cast(klass())->protection_domain()) {
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

  if (protection_domain == instanceKlass::cast(klass())->protection_domain()) {
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


void DictionaryEntry::add_protection_domain(oop protection_domain) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  if (!contains_protection_domain(protection_domain)) {
    ProtectionDomainEntry* new_head =
                new ProtectionDomainEntry(protection_domain, _pd_set);
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


bool Dictionary::do_unloading(BoolObjectClosure* is_alive) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint")
  bool class_was_unloaded = false;
  int  index = 0; // Defined here for portability! Do not move

  // Remove unloadable entries and classes from system dictionary
  // The placeholder array has been handled in always_strong_oops_do.
  DictionaryEntry* probe = NULL;
  for (index = 0; index < table_size(); index++) {
    for (DictionaryEntry** p = bucket_addr(index); *p != NULL; ) {
      probe = *p;
      klassOop e = probe->klass();
      oop class_loader = probe->loader();

      instanceKlass* ik = instanceKlass::cast(e);
      if (ik->previous_versions() != NULL) {
        // This klass has previous versions so see what we can cleanup
        // while it is safe to do so.

        int gc_count = 0;    // leave debugging breadcrumbs
        int live_count = 0;

        // RC_TRACE macro has an embedded ResourceMark
        RC_TRACE(0x00000200, ("unload: %s: previous version length=%d",
          ik->external_name(), ik->previous_versions()->length()));

        for (int i = ik->previous_versions()->length() - 1; i >= 0; i--) {
          // check the previous versions array for GC'ed weak refs
          PreviousVersionNode * pv_node = ik->previous_versions()->at(i);
          jobject cp_ref = pv_node->prev_constant_pool();
          assert(cp_ref != NULL, "cp ref was unexpectedly cleared");
          if (cp_ref == NULL) {
            delete pv_node;
            ik->previous_versions()->remove_at(i);
            // Since we are traversing the array backwards, we don't have to
            // do anything special with the index.
            continue;  // robustness
          }

          constantPoolOop pvcp = (constantPoolOop)JNIHandles::resolve(cp_ref);
          if (pvcp == NULL) {
            // this entry has been GC'ed so remove it
            delete pv_node;
            ik->previous_versions()->remove_at(i);
            // Since we are traversing the array backwards, we don't have to
            // do anything special with the index.
            gc_count++;
            continue;
          } else {
            RC_TRACE(0x00000200, ("unload: previous version @%d is alive", i));
            if (is_alive->do_object_b(pvcp)) {
              live_count++;
            } else {
              guarantee(false, "sanity check");
            }
          }

          GrowableArray<jweak>* method_refs = pv_node->prev_EMCP_methods();
          if (method_refs != NULL) {
            RC_TRACE(0x00000200, ("unload: previous methods length=%d",
              method_refs->length()));
            for (int j = method_refs->length() - 1; j >= 0; j--) {
              jweak method_ref = method_refs->at(j);
              assert(method_ref != NULL, "weak method ref was unexpectedly cleared");
              if (method_ref == NULL) {
                method_refs->remove_at(j);
                // Since we are traversing the array backwards, we don't have to
                // do anything special with the index.
                continue;  // robustness
              }

              methodOop method = (methodOop)JNIHandles::resolve(method_ref);
              if (method == NULL) {
                // this method entry has been GC'ed so remove it
                JNIHandles::destroy_weak_global(method_ref);
                method_refs->remove_at(j);
              } else {
                // RC_TRACE macro has an embedded ResourceMark
                RC_TRACE(0x00000200,
                  ("unload: %s(%s): prev method @%d in version @%d is alive",
                  method->name()->as_C_string(),
                  method->signature()->as_C_string(), j, i));
              }
            }
          }
        }
        assert(ik->previous_versions()->length() == live_count, "sanity check");
        RC_TRACE(0x00000200,
          ("unload: previous version stats: live=%d, GC'ed=%d", live_count,
          gc_count));
      }

      // Non-unloadable classes were handled in always_strong_oops_do
      if (!is_strongly_reachable(class_loader, e)) {
        // Entry was not visited in phase1 (negated test from phase1)
        assert(class_loader != NULL, "unloading entry with null class loader");
        oop k_def_class_loader = ik->class_loader();

        // Do we need to delete this system dictionary entry?
        bool purge_entry = false;

        // Do we need to delete this system dictionary entry?
        if (!is_alive->do_object_b(class_loader)) {
          // If the loader is not live this entry should always be
          // removed (will never be looked up again). Note that this is
          // not the same as unloading the referred class.
          if (k_def_class_loader == class_loader) {
            // This is the defining entry, so the referred class is about
            // to be unloaded.
            // Notify the debugger and clean up the class.
            guarantee(!is_alive->do_object_b(e),
                      "klass should not be live if defining loader is not");
            class_was_unloaded = true;
            // notify the debugger
            if (JvmtiExport::should_post_class_unload()) {
              JvmtiExport::post_class_unload(ik->as_klassOop());
            }

            // notify ClassLoadingService of class unload
            ClassLoadingService::notify_class_unloaded(ik);

            // Clean up C heap
            ik->release_C_heap_structures();
          }
          // Also remove this system dictionary entry.
          purge_entry = true;

        } else {
          // The loader in this entry is alive. If the klass is dead,
          // the loader must be an initiating loader (rather than the
          // defining loader). Remove this entry.
          if (!is_alive->do_object_b(e)) {
            guarantee(!is_alive->do_object_b(k_def_class_loader),
                      "defining loader should not be live if klass is not");
            // If we get here, the class_loader must not be the defining
            // loader, it must be an initiating one.
            assert(k_def_class_loader != class_loader,
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


void Dictionary::always_strong_classes_do(OopClosure* blk) {
  // Follow all system classes and temporary placeholders in dictionary
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry *probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      oop e = probe->klass();
      oop class_loader = probe->loader();
      if (is_strongly_reachable(class_loader, e)) {
        blk->do_oop((oop*)probe->klass_addr());
        if (class_loader != NULL) {
          blk->do_oop(probe->loader_addr());
        }
        probe->protection_domain_set_oops_do(blk);
      }
    }
  }
}


//   Just the classes from defining class loaders
void Dictionary::classes_do(void f(klassOop)) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      klassOop k = probe->klass();
      if (probe->loader() == instanceKlass::cast(k)->class_loader()) {
        f(k);
      }
    }
  }
}

// Added for initialize_itable_for_klass to handle exceptions
//   Just the classes from defining class loaders
void Dictionary::classes_do(void f(klassOop, TRAPS), TRAPS) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      klassOop k = probe->klass();
      if (probe->loader() == instanceKlass::cast(k)->class_loader()) {
        f(k, CHECK);
      }
    }
  }
}


//   All classes, and their class loaders
//   (added for helpers that use HandleMarks and ResourceMarks)
// Don't iterate over placeholders
void Dictionary::classes_do(void f(klassOop, oop, TRAPS), TRAPS) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      klassOop k = probe->klass();
      f(k, probe->loader(), CHECK);
    }
  }
}


//   All classes, and their class loaders
// Don't iterate over placeholders
void Dictionary::classes_do(void f(klassOop, oop)) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      klassOop k = probe->klass();
      f(k, probe->loader());
    }
  }
}


void Dictionary::oops_do(OopClosure* f) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      f->do_oop((oop*)probe->klass_addr());
      if (probe->loader() != NULL) {
        f->do_oop(probe->loader_addr());
      }
      probe->protection_domain_set_oops_do(f);
    }
  }
}


void Dictionary::methods_do(void f(methodOop)) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      klassOop k = probe->klass();
      if (probe->loader() == instanceKlass::cast(k)->class_loader()) {
        // only take klass is we have the entry with the defining class loader
        instanceKlass::cast(k)->methods_do(f);
      }
    }
  }
}


klassOop Dictionary::try_get_next_class() {
  while (true) {
    if (_current_class_entry != NULL) {
      klassOop k = _current_class_entry->klass();
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

void Dictionary::add_klass(symbolHandle class_name, Handle class_loader,
                           KlassHandle obj) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(obj() != NULL, "adding NULL obj");
  assert(Klass::cast(obj())->name() == class_name(), "sanity check on name");

  unsigned int hash = compute_hash(class_name, class_loader);
  int index = hash_to_index(hash);
  DictionaryEntry* entry = new_entry(hash, obj(), class_loader());
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
                                       symbolHandle class_name,
                                       Handle class_loader) {
  symbolOop name_ = class_name();
  oop loader_ = class_loader();
  debug_only(_lookup_count++);
  for (DictionaryEntry* entry = bucket(index);
                        entry != NULL;
                        entry = entry->next()) {
    if (entry->hash() == hash && entry->equals(name_, loader_)) {
      return entry;
    }
    debug_only(_lookup_length++);
  }
  return NULL;
}


klassOop Dictionary::find(int index, unsigned int hash, symbolHandle name,
                          Handle loader, Handle protection_domain, TRAPS) {
  DictionaryEntry* entry = get_entry(index, hash, name, loader);
  if (entry != NULL && entry->is_valid_protection_domain(protection_domain)) {
    return entry->klass();
  } else {
    return NULL;
  }
}


klassOop Dictionary::find_class(int index, unsigned int hash,
                                symbolHandle name, Handle loader) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert (index == index_for(name, loader), "incorrect index?");

  DictionaryEntry* entry = get_entry(index, hash, name, loader);
  return (entry != NULL) ? entry->klass() : (klassOop)NULL;
}


// Variant of find_class for shared classes.  No locking required, as
// that table is static.

klassOop Dictionary::find_shared_class(int index, unsigned int hash,
                                       symbolHandle name) {
  assert (index == index_for(name, Handle()), "incorrect index?");

  DictionaryEntry* entry = get_entry(index, hash, name, Handle());
  return (entry != NULL) ? entry->klass() : (klassOop)NULL;
}


void Dictionary::add_protection_domain(int index, unsigned int hash,
                                       instanceKlassHandle klass,
                                       Handle loader, Handle protection_domain,
                                       TRAPS) {
  symbolHandle klass_name(THREAD, klass->name());
  DictionaryEntry* entry = get_entry(index, hash, klass_name, loader);

  assert(entry != NULL,"entry must be present, we just created it");
  assert(protection_domain() != NULL,
         "real protection domain should be present");

  entry->add_protection_domain(protection_domain());

  assert(entry->contains_protection_domain(protection_domain()),
         "now protection domain should be present");
}


bool Dictionary::is_valid_protection_domain(int index, unsigned int hash,
                                            symbolHandle name,
                                            Handle loader,
                                            Handle protection_domain) {
  DictionaryEntry* entry = get_entry(index, hash, name, loader);
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
  Thread *thread = Thread::current();

  while (master_list != NULL) {
    DictionaryEntry* p = master_list;
    master_list = master_list->next();
    p->set_next(NULL);
    symbolHandle class_name (thread, instanceKlass::cast((klassOop)(p->klass()))->name());
    unsigned int hash = compute_hash(class_name, Handle(thread, p->loader()));
    int index = hash_to_index(hash);
    p->set_hash(hash);
    p->set_next(bucket(index));
    set_entry(index, p);
  }
}

SymbolPropertyTable::SymbolPropertyTable(int table_size)
  : Hashtable(table_size, sizeof(SymbolPropertyEntry))
{
}
SymbolPropertyTable::SymbolPropertyTable(int table_size, HashtableBucket* t,
                                         int number_of_entries)
  : Hashtable(table_size, sizeof(SymbolPropertyEntry), t, number_of_entries)
{
}


SymbolPropertyEntry* SymbolPropertyTable::find_entry(int index, unsigned int hash,
                                                     symbolHandle sym) {
  assert(index == index_for(sym), "incorrect index?");
  for (SymbolPropertyEntry* p = bucket(index); p != NULL; p = p->next()) {
    if (p->hash() == hash && p->symbol() == sym()) {
      return p;
    }
  }
  return NULL;
}


SymbolPropertyEntry* SymbolPropertyTable::add_entry(int index, unsigned int hash,
                                                    symbolHandle sym) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(index == index_for(sym), "incorrect index?");
  assert(find_entry(index, hash, sym) == NULL, "no double entry");

  SymbolPropertyEntry* p = new_entry(hash, sym());
  Hashtable::add_entry(index, p);
  return p;
}


void SymbolPropertyTable::oops_do(OopClosure* f) {
  for (int index = 0; index < table_size(); index++) {
    for (SymbolPropertyEntry* p = bucket(index); p != NULL; p = p->next()) {
      f->do_oop((oop*) p->symbol_addr());
      if (p->property_oop() != NULL) {
        f->do_oop(p->property_oop_addr());
      }
    }
  }
}

void SymbolPropertyTable::methods_do(void f(methodOop)) {
  for (int index = 0; index < table_size(); index++) {
    for (SymbolPropertyEntry* p = bucket(index); p != NULL; p = p->next()) {
      oop prop = p->property_oop();
      if (prop != NULL && prop->is_method()) {
        f((methodOop)prop);
      }
    }
  }
}


// ----------------------------------------------------------------------------
#ifndef PRODUCT

void Dictionary::print() {
  ResourceMark rm;
  HandleMark   hm;

  tty->print_cr("Java system dictionary (classes=%d)", number_of_entries());
  tty->print_cr("^ indicates that initiating loader is different from "
                "defining loader");

  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      if (Verbose) tty->print("%4d: ", index);
      klassOop e = probe->klass();
      oop class_loader =  probe->loader();
      bool is_defining_class =
         (class_loader == instanceKlass::cast(e)->class_loader());
      tty->print("%s%s", is_defining_class ? " " : "^",
                   Klass::cast(e)->external_name());
      if (class_loader != NULL) {
        tty->print(", loader ");
        class_loader->print_value();
      }
      tty->cr();
    }
  }
}

#endif

void Dictionary::verify() {
  guarantee(number_of_entries() >= 0, "Verify of system dictionary failed");
  int element_count = 0;
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      klassOop e = probe->klass();
      oop class_loader = probe->loader();
      guarantee(Klass::cast(e)->oop_is_instance(),
                              "Verify of system dictionary failed");
      // class loader must be present;  a null class loader is the
      // boostrap loader
      guarantee(class_loader == NULL || class_loader->is_instance(),
                "checking type of class_loader");
      e->verify();
      probe->verify_protection_domain_set();
      element_count++;
    }
  }
  guarantee(number_of_entries() == element_count,
            "Verify of system dictionary failed");
  debug_only(verify_lookup_length((double)number_of_entries() / table_size()));
}
