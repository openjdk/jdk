/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/protectionDomainCache.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "oops/weakHandle.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

unsigned int ProtectionDomainCacheTable::compute_hash(const WeakHandle& protection_domain) {
  // The protection domain in the hash computation is passed from a Handle so cannot resolve to null.
  assert(protection_domain.peek() != nullptr, "Must be live");
  return (unsigned int)(protection_domain.resolve()->identity_hash());
}

bool ProtectionDomainCacheTable::equals(const WeakHandle& protection_domain1, const WeakHandle& protection_domain2) {
  return protection_domain1.peek() == protection_domain2.peek();
}

// WeakHandle is both the key and the value.  We need it as the key to compare the oops that each point to
// for equality.  We need it as the value to return the one that already exists to link in the DictionaryEntry.
using InternalProtectionDomainCacheTable = ResourceHashtable<WeakHandle, WeakHandle, 1009, AnyObj::C_HEAP, mtClass,
                  ProtectionDomainCacheTable::compute_hash,
                  ProtectionDomainCacheTable::equals>;
static InternalProtectionDomainCacheTable* _pd_cache_table;

bool ProtectionDomainCacheTable::_dead_entries = false;
int  ProtectionDomainCacheTable::_total_oops_removed = 0;

void ProtectionDomainCacheTable::initialize(){
  _pd_cache_table = new (mtClass) InternalProtectionDomainCacheTable();
}
void ProtectionDomainCacheTable::trigger_cleanup() {
  MutexLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
  _dead_entries = true;
  Service_lock->notify_all();
}

class CleanProtectionDomainEntries : public CLDClosure {
  GrowableArray<ProtectionDomainEntry*>* _delete_list;
 public:
  CleanProtectionDomainEntries(GrowableArray<ProtectionDomainEntry*>* delete_list) :
                               _delete_list(delete_list) {}

  void do_cld(ClassLoaderData* data) {
    Dictionary* dictionary = data->dictionary();
    if (dictionary != nullptr) {
      dictionary->remove_from_package_access_cache(_delete_list);
    }
  }
};

static GrowableArray<ProtectionDomainEntry*>* _delete_list = nullptr;

class HandshakeForPD : public HandshakeClosure {
 public:
  HandshakeForPD() : HandshakeClosure("HandshakeForPD") {}

  void do_thread(Thread* thread) {
    log_trace(protectiondomain)("HandshakeForPD::do_thread: thread="
                                INTPTR_FORMAT, p2i(thread));
  }
};

static void purge_deleted_entries() {
  // If there are any deleted entries, Handshake-all then they'll be
  // safe to remove since traversing the package_access_cache list does not stop for
  // safepoints and only JavaThreads will read the package_access_cache.
  // This is actually quite rare because the protection domain is generally associated
  // with the caller class and class loader, which if still alive will keep this
  // protection domain entry alive.
  if (_delete_list->length() >= 10) {
    HandshakeForPD hs_pd;
    Handshake::execute(&hs_pd);

    for (int i = _delete_list->length() - 1; i >= 0; i--) {
      ProtectionDomainEntry* entry = _delete_list->at(i);
      _delete_list->remove_at(i);
      delete entry;
    }
    assert(_delete_list->length() == 0, "should be cleared");
  }
}

void ProtectionDomainCacheTable::unlink() {
  // DictionaryEntry::_package_access_cache should be null also, so nothing to do.
  assert(java_lang_System::allow_security_manager(), "should not be called otherwise");

  // Create a list for holding deleted entries
  if (_delete_list == nullptr) {
    _delete_list = new (mtClass)
                       GrowableArray<ProtectionDomainEntry*>(20, mtClass);
  }

  {
    // First clean cached pd lists in loaded CLDs
    // It's unlikely, but some loaded classes in a dictionary might
    // point to a protection_domain that has been unloaded.
    // DictionaryEntry::_package_access_cache points at entries in the ProtectionDomainCacheTable.
    MutexLocker ml(ClassLoaderDataGraph_lock);
    MutexLocker mldict(SystemDictionary_lock);  // need both.
    CleanProtectionDomainEntries clean(_delete_list);
    ClassLoaderDataGraph::loaded_cld_do(&clean);
  }

  // Purge any deleted entries outside of the SystemDictionary_lock.
  purge_deleted_entries();

  // Reacquire the lock to remove entries from the hashtable.
  MutexLocker ml(SystemDictionary_lock);

  struct Deleter {
    int _oops_removed;
    Deleter() : _oops_removed(0) {}

    bool do_entry(WeakHandle& key, WeakHandle& value) {
      oop pd = value.peek();
      if (value.peek() == nullptr) {
        _oops_removed++;
        LogTarget(Debug, protectiondomain, table) lt;
        if (lt.is_enabled()) {
          LogStream ls(lt);
          ls.print_cr("protection domain unlinked %d", _oops_removed);
        }
        value.release(Universe::vm_weak());
        return true;
      } else {
        return false;
      }
    }
  };

  Deleter deleter;
  _pd_cache_table->unlink(&deleter);

  _total_oops_removed += deleter._oops_removed;
  _dead_entries = false;
}

void ProtectionDomainCacheTable::print_on(outputStream* st) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  auto printer = [&] (WeakHandle& key, WeakHandle& value) {
      st->print_cr("  protection_domain: " PTR_FORMAT, p2i(value.peek()));
  };
  st->print_cr("Protection domain cache table (table_size=%d, protection domains=%d)",
                _pd_cache_table->table_size(), _pd_cache_table->number_of_entries());
  _pd_cache_table->iterate_all(printer);
}

void ProtectionDomainCacheTable::verify() {
  auto verifier = [&] (WeakHandle& key, WeakHandle& value) {
    guarantee(value.peek() == nullptr || oopDesc::is_oop(value.peek()), "must be an oop");
  };
  _pd_cache_table->iterate_all(verifier);
}

// The object_no_keepalive() call peeks at the phantomly reachable oop without
// keeping it alive.  This is used for traversing DictionaryEntry::_package_access_cache.
oop ProtectionDomainEntry::object_no_keepalive() {
  return _object.peek();
}

WeakHandle ProtectionDomainCacheTable::add_if_absent(Handle protection_domain) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  WeakHandle w(Universe::vm_weak(), protection_domain);
  bool created;
  WeakHandle* wk = _pd_cache_table->put_if_absent(w, w, &created);
  if (!created) {
    // delete the one created since we already had it in the table
    w.release(Universe::vm_weak());
  } else {
    LogTarget(Debug, protectiondomain, table) lt;
    if (lt.is_enabled()) {
      LogStream ls(lt);
      ls.print("protection domain added ");
      protection_domain->print_value_on(&ls);
      ls.cr();
    }
  }
  // Keep entry alive
  (void)wk->resolve();
  return *wk;
}

void ProtectionDomainCacheTable::print_table_statistics(outputStream* st) {
  auto size = [&] (WeakHandle& key, WeakHandle& value) {
    // The only storage is in OopStorage for an oop
    return sizeof(oop);
  };
  TableStatistics ts = _pd_cache_table->statistics_calculate(size);
  ts.print(st, "ProtectionDomainCacheTable");
}

int ProtectionDomainCacheTable::number_of_entries() {
  return _pd_cache_table->number_of_entries();
}
