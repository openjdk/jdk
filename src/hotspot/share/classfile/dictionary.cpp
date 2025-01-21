/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/ostream.hpp"
#include "utilities/tableStatistics.hpp"

// 2^24 is max size, like StringTable.
const size_t END_SIZE = 24;
// If a chain gets to 100 something might be wrong
const size_t REHASH_LEN = 100;

Dictionary::Dictionary(ClassLoaderData* loader_data, size_t table_size)
  : _number_of_entries(0), _loader_data(loader_data) {

  size_t start_size_log_2 = MAX2(log2i_ceil(table_size), 2); // 2 is minimum size even though some dictionaries only have one entry
  size_t current_size = ((size_t)1) << start_size_log_2;
  log_info(class, loader, data)("Dictionary start size: %zu (%zu)",
                                current_size, start_size_log_2);
  _table = new ConcurrentTable(start_size_log_2, END_SIZE, REHASH_LEN);
}

Dictionary::~Dictionary() {
  // This deletes the table and all the nodes, by calling free_node in Config.
  delete _table;
}

uintx Dictionary::Config::get_hash(Value const& value, bool* is_dead) {
  return value->name()->identity_hash();
}

void* Dictionary::Config::allocate_node(void* context, size_t size, Value const& value) {
  return AllocateHeap(size, mtClass);
}

void Dictionary::Config::free_node(void* context, void* memory, Value const& value) {
  FreeHeap(memory);
}

const int _resize_load_trigger = 5;       // load factor that will trigger the resize

int Dictionary::table_size() const {
  return 1 << _table->get_size_log2(Thread::current());
}

bool Dictionary::check_if_needs_resize() {
  return ((_number_of_entries > (_resize_load_trigger * table_size())) &&
         !_table->is_max_size_reached());
}

//   Just the classes from defining class loaders
void Dictionary::classes_do(void f(InstanceKlass*)) {
  auto doit = [&] (InstanceKlass** value) {
    InstanceKlass* k = (*value);
    if (loader_data() == k->class_loader_data()) {
      f(k);
    }
    return true;
  };

  _table->do_scan(Thread::current(), doit);
}

// All classes, and their class loaders, including initiating class loaders
void Dictionary::all_entries_do(KlassClosure* closure) {
  auto all_doit = [&] (InstanceKlass** value) {
    InstanceKlass* k = (*value);
    closure->do_klass(k);
    return true;
  };

  _table->do_scan(Thread::current(), all_doit);
}

// Used to scan and relocate the classes during CDS archive dump.
void Dictionary::classes_do(MetaspaceClosure* it) {
  assert(CDSConfig::is_dumping_archive(), "sanity");

  auto push = [&] (InstanceKlass** value) {
    it->push(value);
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
  bool equals(InstanceKlass** value) {
    InstanceKlass* entry = *value;
    return (entry->name() == _name);
  }
  bool is_dead(InstanceKlass** value) {
    return false;
  }
};

// Add a loaded class to the dictionary.
void Dictionary::add_klass(JavaThread* current, Symbol* class_name,
                           InstanceKlass* klass) {
  assert_locked_or_safepoint(SystemDictionary_lock); // doesn't matter now
  assert(klass != nullptr, "adding nullptr obj");
  assert(klass->name() == class_name, "sanity check on name");

  DictionaryLookup lookup(class_name);
  bool needs_rehashing, clean_hint;
  bool created = _table->insert(current, lookup, klass, &needs_rehashing, &clean_hint);
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
InstanceKlass* Dictionary::find_class(Thread* current, Symbol* class_name) {
  DictionaryLookup lookup(class_name);
  InstanceKlass* result = nullptr;
  auto get = [&] (InstanceKlass** value) {
    // function called if value is found so is never null
    result = (*value);
  };
  bool needs_rehashing = false;
  _table->get(current, lookup, get, &needs_rehashing);
  assert (!needs_rehashing, "should never need rehashing");
  return result;
}

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

  auto printer = [&] (InstanceKlass** entry) {
    InstanceKlass* e = *entry;
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

void Dictionary::verify() {
  guarantee(_number_of_entries >= 0, "Verify of dictionary failed");

  ClassLoaderData* cld = loader_data();
  // class loader must be present;  a null class loader is the
  // bootstrap loader
  guarantee(cld != nullptr &&
            (cld->is_the_null_class_loader_data() || cld->class_loader_no_keepalive()->is_instance()),
            "checking type of class_loader");

  auto verifier = [&] (InstanceKlass** val) {
    (*val)->verify();
    return true;
  };

  _table->do_safepoint_scan(verifier);
}

void Dictionary::print_table_statistics(outputStream* st, const char* table_name) {
  static TableStatistics ts;
  auto sz = [&] (InstanceKlass** val) {
    return sizeof(**val);
  };
  ts = _table->statistics_get(Thread::current(), sz, ts);
  ts.print(st, table_name);
}
