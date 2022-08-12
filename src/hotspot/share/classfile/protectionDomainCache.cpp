/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/oop.inline.hpp"
#include "oops/weakHandle.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/growableArray.hpp"

int ProtectionDomainCacheTable::_total_oops_removed = 0;
bool ProtectionDomainCacheTable::_dead_entries = false;

void ProtectionDomainCacheTable::trigger_cleanup() {
  MutexLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
  _dead_entries = true;
  Service_lock->notify_all();
}

// Unlinks weak protection domains off each dictionary entry pd_set.
class CleanProtectionDomainEntries : public CLDClosure {
  GrowableArray<ProtectionDomainEntry*>* _delete_list;
 public:
  CleanProtectionDomainEntries(GrowableArray<ProtectionDomainEntry*>* delete_list) :
                               _delete_list(delete_list) {}

  void do_cld(ClassLoaderData* data) {
    Dictionary* dictionary = data->dictionary();
    if (dictionary != NULL) {
      dictionary->clean_cached_protection_domains(_delete_list);
    }
  }
};

// Unlinks weak protection domains off class loader data list.
class RemoveWeakProtectionDomains : public CLDClosure {
  public:
    int _oops_removed;
    RemoveWeakProtectionDomains() : _oops_removed(0) {}
    void do_cld(ClassLoaderData* data) {
      _oops_removed += data->remove_weak_protection_domains();
    }
};

static GrowableArray<ProtectionDomainEntry*>* _delete_list = NULL;

class HandshakeForPD : public HandshakeClosure {
 public:
  HandshakeForPD() : HandshakeClosure("HandshakeForPD") {}

  void do_thread(Thread* thread) {
    log_trace(protectiondomain)("HandshakeForPD::do_thread: thread="
                                INTPTR_FORMAT, p2i(thread));
  }
};

static bool purge_deleted_entries() {
  // If there are any deleted entries, Handshake-all then they'll be
  // safe to remove since traversing the pd_set list does not stop for
  // safepoints and only JavaThreads will read the pd_set.
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
    return true; // some were deleted
  }
  return false;  // never mind
}

void ProtectionDomainCacheTable::unlink() {
  // The dictionary entries _pd_set field should be null also, so nothing to do.
  assert(java_lang_System::allow_security_manager(), "should not be called otherwise");

  // Create a list for holding deleted entries
  if (_delete_list == NULL) {
    _delete_list = new (ResourceObj::C_HEAP, mtClass)
                       GrowableArray<ProtectionDomainEntry*>(20, mtClass);
  }

  {
    // First clean cached pd lists in loaded CLDs
    // It's unlikely, but some loaded classes in a dictionary might
    // point to a protection_domain that has been unloaded.
    // The dictionary pd_set points at entries in the ProtectionDomainCacheTable.
    MutexLocker ml(ClassLoaderDataGraph_lock);
    MutexLocker mldict(SystemDictionary_lock);  // need both.
    CleanProtectionDomainEntries clean(_delete_list);
    ClassLoaderDataGraph::loaded_cld_do(&clean);
  }

  // Purge any deleted entries outside of the SystemDictionary_lock.
  bool any_deleted = purge_deleted_entries();

  if (any_deleted) {
    MutexLocker mlcld(ClassLoaderDataGraph_lock);
    RemoveWeakProtectionDomains remove;
    ClassLoaderDataGraph::loaded_cld_do(&remove);
    _total_oops_removed = remove._oops_removed;
  }

  _dead_entries = false;
}


// The object_no_keepalive() call peeks at the phantomly reachable oop without
// keeping it alive. This is okay to do in the VM thread state if it is not
// leaked out to become strongly reachable.
oop ProtectionDomainEntry::object_no_keepalive() {
  return _object.peek();
}
