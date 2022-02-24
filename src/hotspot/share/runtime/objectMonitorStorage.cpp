/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "runtime/objectMonitorStorage.hpp"
#include "services/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

//static const bool be_paranoid = true;
static const bool be_paranoid = false;

ObjectMonitorStorage::ArrayType* ObjectMonitorStorage::_array = NULL;

// re-build a new list of newly allocated free monitors and return its head
void ObjectMonitorStorage::bulk_allocate_new_list(OMFreeListType& freelist_to_fill) {

  MutexLocker ml(ObjectMonitorStorage_lock, Mutex::_no_safepoint_check_flag);

  for (int i = 0; i < (int)PreallocatedObjectMonitors - 1; i ++) {
    ObjectMonitor* m = _array->allocate();
    if (m == NULL) {
      fatal("Maximum number of object monitors allocated (" UINTX_FORMAT "), increase MaxObjectMonitors.",
            _array->capacity());
    }
    freelist_to_fill.prepend(m);
  }
  DEBUG_ONLY(freelist_to_fill.verify(be_paranoid);)
  DEBUG_ONLY(verify();)

  LogTarget(Debug, monitorinflation) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print("bulk_allocate_new_list ");
    _array->print_on(&ls);
    ls.cr();
  }
}

// When a thread dies, return OMs left unused to the global store.
void ObjectMonitorStorage::cleanup_before_thread_death(Thread* t) {
  // Note that the ObjectMonitors we are about to return to the storage are
  // not yet initialized, so no need to destroy them.
  OMFreeListType& tl_list = t->_om_freelist;
  if (tl_list.empty() == false) {
    MutexLocker ml(ObjectMonitorStorage_lock, Mutex::_no_safepoint_check_flag);
    _array->bulk_deallocate(tl_list);
    DEBUG_ONLY(verify();)

    LogTarget(Debug, monitorinflation) lt;
    if (lt.is_enabled()) {
      LogStream ls(lt);
      ls.print("cleanup_before_thread_death ");
      _array->print_on(&ls);
      ls.cr();
    }

  }
  assert(tl_list.empty(), "thread local list should now be empty");
}

// deallocate a list of monitors
void ObjectMonitorStorage::bulk_deallocate(const GrowableArray<ObjectMonitor*>& list) {
  // Build up freelist off-lock, then prepend the whole list under lock protection
  OMFreeListType omlist;
  for (ObjectMonitor* m : list) {
    // Call ObjectMonitor destructor explicitely, then add OM to freelist. Note that the
    // latter destroys OM's content, so order matters.
    m->~ObjectMonitor();
    omlist.prepend(m);
  }
  if (omlist.empty() == false) {

    MutexLocker ml(ObjectMonitorStorage_lock, Mutex::_no_safepoint_check_flag);

    _array->bulk_deallocate(omlist);
    // log log log
    LogTarget(Debug, monitorinflation) lt;
    if (lt.is_enabled()) {
      LogStream ls(lt);
      ls.print("bulk_deallocate %d oms: ", list.length());
      _array->print_on(&ls);
      ls.cr();
    }

    DEBUG_ONLY(verify();)
  }

}

void ObjectMonitorStorage::initialize() {
  assert(_array == NULL, "Already initialized?");
  _array = new ArrayType(MAX2(MaxObjectMonitors, (uintx)1024), 1024);
  MemTracker::record_virtual_memory_type((address)_array->base(), mtObjectMonitor);
}

void ObjectMonitorStorage::print(outputStream* st) {
  if (_array != NULL) {
    _array->print_on(st);
    st->cr();
  } else {
    st->print_cr("Not initialized");
  }
}

#ifdef ASSERT
void ObjectMonitorStorage::verify() {
  assert_lock_strong(ObjectMonitorStorage_lock);
  if (_array != NULL) {
    _array->verify(be_paranoid);
  }
}
#endif
