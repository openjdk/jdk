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

#include "cds/cdsConfig.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "nmt/memoryPointersHashtable.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/ostream.hpp"
#include "utilities/tableStatistics.hpp"
#include "runtime/javaThread.inline.hpp"


void print(void* ptr) {
  fprintf(stderr, "--> (%p)\n", ptr);
}

JavaThread* getValidThread(void) {
  Thread* raw_thread = Thread::current_or_null_safe();
  if (raw_thread != nullptr && raw_thread->is_Java_thread()) {
    JavaThread* jthread = JavaThread::cast(raw_thread);
    if (!jthread->is_exiting()) {
      return jthread;
    }
  }
  return nullptr;
}

const int _nmt_pointers_dictionary_size = 19997;

// 2^24 is max size, like StringTable.
const size_t END_SIZE = 24;
// If a chain gets to 100 something might be wrong
const size_t REHASH_LEN = 100;
const bool ENABLE_STATISTICS = false;
Mutex::Rank RANK = Mutex::service-5;

MemoryPointersHashtable* _dictionary = nullptr;

MemoryPointersHashtable::MemoryPointersHashtable(size_t table_size)
  : _number_of_entries(0) {

  size_t start_size_log_2 = MAX2(log2i_ceil(table_size), 2); // 2 is minimum size even though some dictionaries only have one entry
  size_t current_size = ((size_t)1) << start_size_log_2;
  log_info(class, loader, data)("MemoryPointersHashtable start size: %zu (%zu)",
                                current_size, start_size_log_2);
  _local_table = new ConcurrentTable(start_size_log_2, END_SIZE, REHASH_LEN, ENABLE_STATISTICS, RANK);
}

MemoryPointersHashtable::~MemoryPointersHashtable() {
  // This deletes the table and all the nodes, by calling free_node in Config.
  delete _local_table;
}

uintx MemoryPointersHashtable::Config::get_hash(Value const& value, bool* is_dead) {
  return (uintx)value;
}

void* MemoryPointersHashtable::Config::allocate_node(void* context, size_t size, Value const& value) {
  return AllocateHeap(size, mtNMT_MP);
}

void MemoryPointersHashtable::Config::free_node(void* context, void* memory, Value const& value) {
  FreeHeap(memory);
}

const int _resize_load_trigger = 5;       // load factor that will trigger the resize

int MemoryPointersHashtable::table_size() const {
  Thread* THREAD = Thread::current_or_null_safe();
  return 1 << _local_table->get_size_log2(THREAD);
}

bool MemoryPointersHashtable::check_if_needs_resize() {
  bool resize = ((_number_of_entries > (_resize_load_trigger * table_size())) &&
         !_local_table->is_max_size_reached());
  return resize;
}

void MemoryPointersHashtable::pointers_do(void f(void*)) {
  auto doit = [&] (void** value) {
    void* k = (*value);
      f(k);
    return true;
  };

  Thread* THREAD = Thread::current_or_null_safe();
  _local_table->do_scan(THREAD, doit);
}

class MemoryPointersHashtableLookup : StackObj {
private:
  void* _ptr;
public:
  MemoryPointersHashtableLookup(void* ptr) : _ptr(ptr) { }
  uintx get_hash() const {
    return (uintx)_ptr;
  }
  bool equals(void** value) {
    return (*value == _ptr);
  }
  bool is_dead(void** value) {
    return false;
  }
};

void MemoryPointersHashtable::add_ptr(Thread* current, void* ptr) {
  //assert_locked_or_safepoint(SystemDictionary_lock); // doesn't matter now
  MemoryPointersHashtableLookup lookup(ptr);
  bool needs_rehashing, clean_hint;
  bool created = _local_table->insert(current, lookup, ptr, &needs_rehashing, &clean_hint);
  //assert(created, "created");
  //assert (!needs_rehashing, "needs_rehashing");
  assert(!clean_hint, "clean_hint");
  _number_of_entries++;  // still locked
  // This table can be resized while another thread is reading it.
  if (check_if_needs_resize()) {
    fprintf(stderr, "GROW!\n");
    _local_table->grow(current);
    
    // It would be nice to have a JFR event here, add some logging.
    LogTarget(Info, class, loader, data) lt;
    if (lt.is_enabled()) {
      ResourceMark rm;
      LogStream ls(&lt);
      ls.print("MemoryPointersHashtable resized to %d entries %d for ", table_size(), _number_of_entries);
    }
  }
}

void MemoryPointersHashtable::remove_ptr(Thread* current, void* ptr) {
  MemoryPointersHashtableLookup lookup(ptr);
  bool removed = _dictionary->_local_table->remove(current, lookup);
  if (!removed) {
    fprintf(stderr, "NOT REMOVED?\n");
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
void* MemoryPointersHashtable::find_pointer(Thread* current, void* ptr) {
  MemoryPointersHashtableLookup lookup(ptr);
  void* result = nullptr;
  auto get = [&] (void** value) {
    // function called if value is found so is never null
    result = (*value);
  };
  bool needs_rehashing = false;
  _local_table->get(current, lookup, get, &needs_rehashing);
  //assert (!needs_rehashing, "!needs_rehashing");
  return result;
}

void MemoryPointersHashtable::print_size(outputStream* st) const {
  st->print_cr("Java dictionary (table_size=%d, classes=%d)",
               table_size(), _number_of_entries);
}

void MemoryPointersHashtable::print_on(outputStream* st) const {
  ResourceMark rm;
  Thread* THREAD = getValidThread();
  print_size(st);
  st->print_cr("^ indicates that initiating loader is different from defining loader");

  auto printer = [&] (void** entry) {
    void* e = *entry;
    st->print_cr("pre: %p", e);
    return true;
  };

  if (SafepointSynchronize::is_at_safepoint()) {
    _local_table->do_safepoint_scan(printer);
  } else {
    _local_table->do_scan(THREAD, printer);
  }
  tty->cr();
}

void MemoryPointersHashtable::verify() {
  guarantee(_number_of_entries >= 0, "Verify of dictionary failed");
  auto verifier = [&] (void** val) {
    return true;
  };

  _local_table->do_safepoint_scan(verifier);
}

void MemoryPointersHashtable::print_table_statistics(outputStream* st, const char* table_name) {
  Thread* THREAD = getValidThread();
  static TableStatistics ts;
  auto sz = [&] (void** val) {
    return sizeof(*val);
  };
  ts = _local_table->statistics_get(THREAD, sz, ts);
  ts.print(st, table_name);
}

void MemoryPointersHashtable::createMemoryPointersHashtable() {
  assert(_dictionary == nullptr, "must be");
  Thread* THREAD = getValidThread();
  _dictionary = new MemoryPointersHashtable(_nmt_pointers_dictionary_size);
  assert(_dictionary != nullptr, "must be");

  // example:
//  void* ptr = os::malloc(128, mtNone);
//  fprintf(stderr, " allocated ptr: %p\n", ptr);
//  //pd = new PointerData(ptr, NMT_off);
//  //fprintf(stderr, " created pd: %p\n", pd);
//  _dictionary->add_ptr(THREAD, ptr, ptr);
//  void* found = _dictionary->find_pointer(THREAD, ptr);
//  fprintf(stderr, " found: %p\n", found);
//  os::exit(0);
}

bool MemoryPointersHashtable::record_alloc(MemTag mem_tag, void* ptr) {
  if (mem_tag != mtNMT_MP) {
    Thread* THREAD = getValidThread();
    if (_dictionary != nullptr && THREAD != nullptr) {
      void* found = _dictionary->find_pointer(THREAD, ptr);
      if (found != nullptr) {
        //fprintf(stderr, "IT ALREADY EXISTS? (%p:%p)\n", ptr, found);
//        _dictionary->pointers_do(print);
      } else {
        _dictionary->add_ptr(THREAD, ptr);
//        fprintf(stderr, "ADDED %p\n", ptr);
        return true;
      }
    } else {
//      fprintf(stderr, "SKIPPED %p\n", ptr);
//      if (_dictionary != nullptr) {
//        _dictionary->_local_table->unsafe_insert(ptr);
//      }
    }
  } else {
//    fprintf(stderr, "SKIPPED (MemTag) %p\n", ptr);
  }

  return false;
}

bool MemoryPointersHashtable::record_free(void* ptr) {
  Thread* THREAD = getValidThread();
  if (THREAD != nullptr && _dictionary != nullptr) {
    void* found = _dictionary->find_pointer(THREAD, ptr);
    if (found != nullptr) {
      _dictionary->remove_ptr(THREAD, ptr);
      //      fprintf(stderr, "REMOVED %p\n", ptr);
      return true;
    } else {
      //fprintf(stderr, "IT DOES NOT EXIST? (%p)\n", ptr);
    }
  } else {
    //    fprintf(stderr, "SKIPPED %p\n", ptr);
  }

  return false;
}
