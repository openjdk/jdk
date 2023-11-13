/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/cdsConfig.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/protectionDomainCache.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/tableStatistics.hpp"

// 2^24 is max size, like StringTable.
const size_t END_SIZE = 24;
// If a chain gets to 100 something might be wrong
const size_t REHASH_LEN = 100;

Dictionary::Dictionary(ClassLoaderData* loader_data, size_t table_size)
  : _number_of_entries(0), _loader_data(loader_data) {

  size_t start_size_log_2 = MAX2(ceil_log2(table_size), (size_t)2); // 2 is minimum size even though some dictionaries only have one entry
  size_t current_size = ((size_t)1) << start_size_log_2;
  log_info(class, loader, data)("Dictionary start size: " SIZE_FORMAT " (" SIZE_FORMAT ")",
                                current_size, start_size_log_2);
  _table = new ConcurrentTable(start_size_log_2, END_SIZE, REHASH_LEN);
}

Dictionary::~Dictionary() {
  // This deletes the table and all the nodes, by calling free_node in Config.
  delete _table;
}

uintx Dictionary::Config::get_hash(Value const& value, bool* is_dead) {
  return value->instance_klass()->name()->identity_hash();
}

void* Dictionary::Config::allocate_node(void* context, size_t size, Value const& value) {
  return AllocateHeap(size, mtClass);
}

void Dictionary::Config::free_node(void* context, void* memory, Value const& value) {
  delete value; // Call DictionaryEntry destructor
  FreeHeap(memory);
}

DictionaryEntry::DictionaryEntry(InstanceKlass* klass) : _instance_klass(klass) {
  release_set_pd_set(nullptr);
}

DictionaryEntry::~DictionaryEntry() {
  // avoid recursion when deleting linked list
  // pd_set is accessed during a safepoint.
  // This doesn't require a lock because nothing is reading this
  // entry anymore.  The ClassLoader is dead.
  while (pd_set_acquire() != nullptr) {
    ProtectionDomainEntry* to_delete = pd_set_acquire();
    release_set_pd_set(to_delete->next_acquire());
    delete to_delete;
  }
}

const int _resize_load_trigger = 5;       // load factor that will trigger the resize

int Dictionary::table_size() const {
  return 1 << _table->get_size_log2(Thread::current());
}

bool Dictionary::check_if_needs_resize() {
  return ((_number_of_entries > (_resize_load_trigger * table_size())) &&
         !_table->is_max_size_reached());
}

bool DictionaryEntry::is_valid_protection_domain(Handle protection_domain) {

  return protection_domain() == nullptr || !java_lang_System::allow_security_manager()
        ? true
        : contains_protection_domain(protection_domain());
}

// Reading the pd_set on each DictionaryEntry is lock free and cannot safepoint.
// Adding and deleting entries is under the SystemDictionary_lock
// Deleting unloaded entries on ClassLoaderData for dictionaries that are not unloaded
// is a three step process:
//     moving the entries to a separate list, handshake to wait for
//     readers to complete (see NSV here), and then actually deleting the entries.
// Deleting entries is done by the ServiceThread when triggered by class unloading.

bool DictionaryEntry::contains_protection_domain(oop protection_domain) const {
  assert(Thread::current()->is_Java_thread() || SafepointSynchronize::is_at_safepoint(),
         "can only be called by a JavaThread or at safepoint");
  // This cannot safepoint while reading the protection domain set.
  NoSafepointVerifier nsv;
#ifdef ASSERT
  if (protection_domain == instance_klass()->protection_domain()) {
    // Ensure this doesn't show up in the pd_set (invariant)
    bool in_pd_set = false;
    for (ProtectionDomainEntry* current = pd_set_acquire();
                                current != nullptr;
                                current = current->next_acquire()) {
      if (current->object_no_keepalive() == protection_domain) {
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

  if (protection_domain == instance_klass()->protection_domain()) {
    // Succeeds trivially
    return true;
  }

  for (ProtectionDomainEntry* current = pd_set_acquire();
                              current != nullptr;
                              current = current->next_acquire()) {
    if (current->object_no_keepalive() == protection_domain) {
      return true;
    }
  }
  return false;
}

void DictionaryEntry::add_protection_domain(ClassLoaderData* loader_data, Handle protection_domain) {
  assert_lock_strong(SystemDictionary_lock);
  if (!contains_protection_domain(protection_domain())) {
    WeakHandle obj = ProtectionDomainCacheTable::add_if_absent(protection_domain);
    // Additions and deletions hold the SystemDictionary_lock, readers are lock-free
    ProtectionDomainEntry* new_head = new ProtectionDomainEntry(obj, _pd_set);
    release_set_pd_set(new_head);
  }
  LogTarget(Trace, protectiondomain) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    ls.print("adding protection domain for class %s", instance_klass()->name()->as_C_string());
    ls.print(" class loader: ");
    loader_data->class_loader()->print_value_on(&ls);
    ls.print(" protection domain: ");
    protection_domain->print_value_on(&ls);
    ls.print(" ");
    print_count(&ls);
    ls.cr();
  }
}

//   Just the classes from defining class loaders
void Dictionary::classes_do(void f(InstanceKlass*)) {
  auto doit = [&] (DictionaryEntry** value) {
    InstanceKlass* k = (*value)->instance_klass();
    if (loader_data() == k->class_loader_data()) {
      f(k);
    }
    return true;
  };

  _table->do_scan(Thread::current(), doit);
}

// All classes, and their class loaders, including initiating class loaders
void Dictionary::all_entries_do(KlassClosure* closure) {
  auto all_doit = [&] (DictionaryEntry** value) {
    InstanceKlass* k = (*value)->instance_klass();
    closure->do_klass(k);
    return true;
  };

  _table->do_scan(Thread::current(), all_doit);
}

// Used to scan and relocate the classes during CDS archive dump.
void Dictionary::classes_do(MetaspaceClosure* it) {
  assert(CDSConfig::is_dumping_archive(), "sanity");

  auto push = [&] (DictionaryEntry** value) {
    InstanceKlass** k = (*value)->instance_klass_addr();
    it->push(k);
    return true;
  };
  _table->do_scan(Thread::current(), push);
}

class DictionaryLookup : StackObj {
private:
  Symbol* _name;
public:
  DictionaryLookup(Symbol* name) : _name(name) { }
  uintx get_hash() const {
    return _name->identity_hash();
  }
  bool equals(DictionaryEntry** value) {
    DictionaryEntry *entry = *value;
    return (entry->instance_klass()->name() == _name);
  }
  bool is_dead(DictionaryEntry** value) {
    return false;
  }
};

// Add a loaded class to the dictionary.
void Dictionary::add_klass(JavaThread* current, Symbol* class_name,
                           InstanceKlass* obj) {
  assert_locked_or_safepoint(SystemDictionary_lock); // doesn't matter now
  assert(obj != nullptr, "adding nullptr obj");
  assert(obj->name() == class_name, "sanity check on name");

  DictionaryEntry* entry = new DictionaryEntry(obj);
  DictionaryLookup lookup(class_name);
  bool needs_rehashing, clean_hint;
  bool created = _table->insert(current, lookup, entry, &needs_rehashing, &clean_hint);
  assert(created, "should be because we have a lock");
  assert (!needs_rehashing, "should never need rehashing");
  assert(!clean_hint, "no class should be unloaded");
  _number_of_entries++;  // still locked
  // This table can be resized while another thread is reading it.
  if (check_if_needs_resize()) {
    _table->grow(current);

    // It would be nice to have a JFR event here, add some logging.
    LogTarget(Info, class, loader, data) lt;
    if (lt.is_enabled()) {
      ResourceMark rm;
      LogStream ls(&lt);
      ls.print("Dictionary resized to %d entries %d for ", table_size(), _number_of_entries);
      loader_data()->print_value_on(&ls);
    }
  }
}

// This routine does not lock the dictionary.
//
// Since readers don't hold a lock, we must make sure that system
// dictionary entries are only removed at a safepoint (when only one
// thread is running), and are added to in a safe way (all links must
// be updated in an MT-safe manner).
//
// Callers should be aware that an entry could be added just after
// the table is read here, so the caller will not see the new entry.
// The entry may be accessed by the VM thread in verification.
DictionaryEntry* Dictionary::get_entry(Thread* current,
                                       Symbol* class_name) {
  DictionaryLookup lookup(class_name);
  DictionaryEntry* result = nullptr;
  auto get = [&] (DictionaryEntry** value) {
    // function called if value is found so is never null
    result = (*value);
  };
  bool needs_rehashing = false;
  _table->get(current, lookup, get, &needs_rehashing);
  assert (!needs_rehashing, "should never need rehashing");
  return result;
}


InstanceKlass* Dictionary::find(Thread* current, Symbol* name,
                                Handle protection_domain) {
  NoSafepointVerifier nsv;

  DictionaryEntry* entry = get_entry(current, name);
  if (entry != nullptr && entry->is_valid_protection_domain(protection_domain)) {
    return entry->instance_klass();
  } else {
    return nullptr;
  }
}

InstanceKlass* Dictionary::find_class(Thread* current,
                                      Symbol* name) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  DictionaryEntry* entry = get_entry(current, name);
  return (entry != nullptr) ? entry->instance_klass() : nullptr;
}

void Dictionary::add_protection_domain(JavaThread* current,
                                       InstanceKlass* klass,
                                       Handle protection_domain) {
  assert(java_lang_System::allow_security_manager(), "only needed if security manager allowed");
  Symbol*  klass_name = klass->name();
  DictionaryEntry* entry = get_entry(current, klass_name);

  assert(entry != nullptr,"entry must be present, we just created it");
  assert(protection_domain() != nullptr,
         "real protection domain should be present");

  entry->add_protection_domain(loader_data(), protection_domain);

#ifdef ASSERT
  assert(loader_data() != ClassLoaderData::the_null_class_loader_data(), "doesn't make sense");
#endif

  assert(entry->contains_protection_domain(protection_domain()),
         "now protection domain should be present");
}


inline bool Dictionary::is_valid_protection_domain(JavaThread* current,
                                            Symbol* name,
                                            Handle protection_domain) {
  DictionaryEntry* entry = get_entry(current, name);
  return entry->is_valid_protection_domain(protection_domain);
}

void Dictionary::validate_protection_domain(InstanceKlass* klass,
                                            Handle class_loader,
                                            Handle protection_domain,
                                            TRAPS) {

  assert(class_loader() != nullptr, "Should not call this");
  assert(protection_domain() != nullptr, "Should not call this");

  if (!java_lang_System::allow_security_manager() ||
      is_valid_protection_domain(THREAD, klass->name(), protection_domain)) {
    return;
  }

  // We only have to call checkPackageAccess if there's a security manager installed.
  if (java_lang_System::has_security_manager()) {

    // This handle and the class_loader handle passed in keeps this class from
    // being unloaded through several GC points.
    // The class_loader handle passed in is the initiating loader.
    Handle mirror(THREAD, klass->java_mirror());

    // Now we have to call back to java to check if the initating class has access
    InstanceKlass* system_loader = vmClasses::ClassLoader_klass();
    JavaValue result(T_VOID);
    JavaCalls::call_special(&result,
                           class_loader,
                           system_loader,
                           vmSymbols::checkPackageAccess_name(),
                           vmSymbols::class_protectiondomain_signature(),
                           mirror,
                           protection_domain,
                           THREAD);

    LogTarget(Debug, protectiondomain) lt;
    if (lt.is_enabled()) {
      ResourceMark rm(THREAD);
      // Print out trace information
      LogStream ls(lt);
      ls.print_cr("Checking package access");
      ls.print("class loader: ");
      class_loader()->print_value_on(&ls);
      ls.print(" protection domain: ");
      protection_domain()->print_value_on(&ls);
      ls.print(" loading: "); klass->print_value_on(&ls);
      if (HAS_PENDING_EXCEPTION) {
        ls.print_cr(" DENIED !!!!!!!!!!!!!!!!!!!!!");
      } else {
        ls.print_cr(" granted");
      }
    }

    if (HAS_PENDING_EXCEPTION) return;
  }

  // If no exception has been thrown, we have validated the protection domain
  // Insert the protection domain of the initiating class into the set.
  // We still have to add the protection_domain to the dictionary in case a new
  // security manager is installed later. Calls to load the same class with class loader
  // and protection domain are expected to succeed.
  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    add_protection_domain(THREAD, klass,
                          protection_domain);
  }
}

// During class loading we may have cached a protection domain that has
// since been unreferenced, so this entry should be cleared.
void Dictionary::clean_cached_protection_domains(GrowableArray<ProtectionDomainEntry*>* delete_list) {
  assert(Thread::current()->is_Java_thread(), "only called by JavaThread");
  assert_lock_strong(SystemDictionary_lock);
  assert(!loader_data()->has_class_mirror_holder(), "cld should have a ClassLoader holder not a Class holder");

  if (loader_data()->is_the_null_class_loader_data()) {
    // Classes in the boot loader are not loaded with protection domains
    return;
  }

  auto clean_entries = [&] (DictionaryEntry** value) {
      DictionaryEntry* probe = *value;
      Klass* e = probe->instance_klass();

      ProtectionDomainEntry* current = probe->pd_set_acquire();
      ProtectionDomainEntry* prev = nullptr;
      while (current != nullptr) {
        if (current->object_no_keepalive() == nullptr) {
          LogTarget(Debug, protectiondomain) lt;
          if (lt.is_enabled()) {
            ResourceMark rm;
            // Print out trace information
            LogStream ls(lt);
            ls.print_cr("PD in set is not alive:");
            ls.print("class loader: "); _loader_data->class_loader()->print_value_on(&ls);
            ls.print(" loading: "); probe->instance_klass()->print_value_on(&ls);
            ls.cr();
          }
          if (probe->pd_set_acquire() == current) {
            probe->release_set_pd_set(current->next_acquire());
          } else {
            assert(prev != nullptr, "should be set by alive entry");
            prev->release_set_next(current->next_acquire());
          }
          // Mark current for deletion but in the meantime it can still be
          // traversed.
          delete_list->push(current);
          current = current->next_acquire();
        } else {
          prev = current;
          current = current->next_acquire();
        }
      }
      return true;
  };

  _table->do_scan(Thread::current(), clean_entries);
}

void DictionaryEntry::verify_protection_domain_set() {
  assert(SafepointSynchronize::is_at_safepoint(), "must only be called as safepoint");
  for (ProtectionDomainEntry* current = pd_set_acquire(); // accessed at a safepoint
                              current != nullptr;
                              current = current->next_acquire()) {
    guarantee(oopDesc::is_oop_or_null(current->object_no_keepalive()), "Invalid oop");
  }
}

void DictionaryEntry::print_count(outputStream *st) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  int count = 0;
  for (ProtectionDomainEntry* current = pd_set_acquire();
                              current != nullptr;
                              current = current->next_acquire()) {
    count++;
  }
  st->print("pd set count = #%d", count);
}

// ----------------------------------------------------------------------------

void Dictionary::print_size(outputStream* st) const {
  st->print_cr("Java dictionary (table_size=%d, classes=%d)",
               table_size(), _number_of_entries);
}

void Dictionary::print_on(outputStream* st) const {
  ResourceMark rm;

  assert(loader_data() != nullptr, "loader data should not be null");
  assert(!loader_data()->has_class_mirror_holder(), "cld should have a ClassLoader holder not a Class holder");
  print_size(st);
  st->print_cr("^ indicates that initiating loader is different from defining loader");

  auto printer = [&] (DictionaryEntry** entry) {
    DictionaryEntry* probe = *entry;
    Klass* e = probe->instance_klass();
    bool is_defining_class =
       (_loader_data == e->class_loader_data());
    st->print(" %s%s", is_defining_class ? " " : "^", e->external_name());
    ClassLoaderData* cld = e->class_loader_data();
    if (!_loader_data->is_the_null_class_loader_data()) {
      // Class loader output for the dictionary for the null class loader data is
      // redundant and obvious.
      st->print(", ");
      cld->print_value_on(st);
      st->print(", ");
      probe->print_count(st);
    }
    st->cr();
    return true;
  };

  if (SafepointSynchronize::is_at_safepoint()) {
    _table->do_safepoint_scan(printer);
  } else {
    _table->do_scan(Thread::current(), printer);
  }
  tty->cr();
}

void DictionaryEntry::verify() {
  Klass* e = instance_klass();
  guarantee(e->is_instance_klass(),
                          "Verify of dictionary failed");
  e->verify();
  verify_protection_domain_set();
}

void Dictionary::verify() {
  guarantee(_number_of_entries >= 0, "Verify of dictionary failed");

  ClassLoaderData* cld = loader_data();
  // class loader must be present;  a null class loader is the
  // bootstrap loader
  guarantee(cld != nullptr &&
            (cld->is_the_null_class_loader_data() || cld->class_loader_no_keepalive()->is_instance()),
            "checking type of class_loader");

  auto verifier = [&] (DictionaryEntry** val) {
    (*val)->verify();
    return true;
  };

  _table->do_safepoint_scan(verifier);
}

void Dictionary::print_table_statistics(outputStream* st, const char* table_name) {
  static TableStatistics ts;
  auto sz = [&] (DictionaryEntry** val) {
    return sizeof(**val);
  };
  ts = _table->statistics_get(Thread::current(), sz, ts);
  ts.print(st, table_name);
}
