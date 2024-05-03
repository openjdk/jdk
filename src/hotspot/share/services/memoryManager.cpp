/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "services/lowMemoryDetector.hpp"
#include "services/management.hpp"
#include "services/memoryManager.hpp"
#include "services/memoryPool.hpp"
#include "services/memoryService.hpp"
#include "services/gcNotifier.hpp"
#include "utilities/dtrace.hpp"

MemoryManager::MemoryManager(const char* name)
  : _pools(),
    _num_pools(0),
    _name(name),
    _memory_mgr_obj(),
    _memory_mgr_obj_initialized(false)
{}

int MemoryManager::add_pool(MemoryPool* pool) {
  int index = _num_pools;
  assert(index < MemoryManager::max_num_pools, "_num_pools exceeds the max");
  if (index < MemoryManager::max_num_pools) {
    _pools[index] = pool;
    _num_pools++;
  }
  pool->add_manager(this);
  return index;
}

bool MemoryManager::is_manager(instanceHandle mh) const {
  if (Atomic::load_acquire(&_memory_mgr_obj_initialized)) {
    return mh() == _memory_mgr_obj.resolve();
  } else {
    return false;
  }
}

MemoryManager* MemoryManager::get_code_cache_memory_manager() {
  return new MemoryManager("CodeCacheManager");
}

MemoryManager* MemoryManager::get_metaspace_memory_manager() {
  return new MemoryManager("Metaspace Manager");
}

instanceOop MemoryManager::get_memory_manager_instance(TRAPS) {
  // Lazily create the manager object.
  // Must do an acquire so as to force ordering of subsequent
  // loads from anything _memory_mgr_obj points to or implies.
  if (!Atomic::load_acquire(&_memory_mgr_obj_initialized)) {
    // It's ok for more than one thread to execute the code up to the locked region.
    // Extra manager instances will just be gc'ed.
    Klass* k = Management::sun_management_ManagementFactoryHelper_klass(CHECK_NULL);

    Handle mgr_name = java_lang_String::create_from_str(name(), CHECK_NULL);

    JavaValue result(T_OBJECT);
    JavaCallArguments args;
    args.push_oop(mgr_name);    // Argument 1

    Symbol* method_name = nullptr;
    Symbol* signature = nullptr;
    if (is_gc_memory_manager()) {
      Klass* extKlass = Management::com_sun_management_internal_GarbageCollectorExtImpl_klass(CHECK_NULL);
      // com.sun.management.GarbageCollectorMXBean is in jdk.management module which may not be present.
      if (extKlass != nullptr) {
        k = extKlass;
      }

      method_name = vmSymbols::createGarbageCollector_name();

      signature = vmSymbols::createGarbageCollector_signature();
      args.push_oop(Handle());      // Argument 2 (for future extension)
    } else {
      method_name = vmSymbols::createMemoryManager_name();
      signature = vmSymbols::createMemoryManager_signature();
    }

    if (k == nullptr) {
      fatal("Should have the ManagementFactoryHelper or GarbageCollectorExtImpl class");
      return nullptr; // silence the compiler
    }

    InstanceKlass* ik = InstanceKlass::cast(k);

    JavaCalls::call_static(&result,
                           ik,
                           method_name,
                           signature,
                           &args,
                           CHECK_NULL);

    // Verify we didn't get a null manager.  If that could happen then we'd
    // need to return immediately rather than continuing on and recording the
    // manager has been created.
    oop m = result.get_oop();
    guarantee(m != nullptr, "Manager creation returned null");
    instanceHandle mgr(THREAD, (instanceOop)m);

    // Allocate global handle outside lock, to avoid any lock nesting issues
    // with the Management_lock.
    OopHandle mgr_handle(Universe::vm_global(), mgr());

    // Get lock since another thread may have created and installed the instance.
    MutexLocker ml(THREAD, Management_lock);

    if (Atomic::load(&_memory_mgr_obj_initialized)) {
      // Some other thread won the race.  Release the handle we allocated and
      // use the other one.  Relaxed load is sufficient because flag update is
      // under the lock.
      mgr_handle.release(Universe::vm_global());
    } else {
      // Record the object we created via call_special.
      assert(_memory_mgr_obj.is_empty(), "already set manager obj");
      _memory_mgr_obj = mgr_handle;
      // Record manager has been created.  Release matching unlocked acquire,
      // to safely publish the manager object.
      Atomic::release_store(&_memory_mgr_obj_initialized, true);
    }
  }

  return (instanceOop)_memory_mgr_obj.resolve();
}

GCStatInfo::GCStatInfo(int num_pools) {
  // initialize the arrays for memory usage
  _before_gc_usage_array = NEW_C_HEAP_ARRAY(MemoryUsage, num_pools, mtInternal);
  _after_gc_usage_array  = NEW_C_HEAP_ARRAY(MemoryUsage, num_pools, mtInternal);
  _usage_array_size = num_pools;
  clear();
}

GCStatInfo::~GCStatInfo() {
  FREE_C_HEAP_ARRAY(MemoryUsage*, _before_gc_usage_array);
  FREE_C_HEAP_ARRAY(MemoryUsage*, _after_gc_usage_array);
}

void GCStatInfo::set_gc_usage(int pool_index, MemoryUsage usage, bool before_gc) {
  MemoryUsage* gc_usage_array;
  if (before_gc) {
    gc_usage_array = _before_gc_usage_array;
  } else {
    gc_usage_array = _after_gc_usage_array;
  }
  gc_usage_array[pool_index] = usage;
}

void GCStatInfo::clear() {
  _index = 0;
  _start_time = 0L;
  _end_time = 0L;
  for (int i = 0; i < _usage_array_size; i++) ::new (&_before_gc_usage_array[i]) MemoryUsage();
  for (int i = 0; i < _usage_array_size; i++) ::new (&_after_gc_usage_array[i]) MemoryUsage();
}


GCMemoryManager::GCMemoryManager(const char* name) :
  MemoryManager(name) {
  _num_collections = 0;
  _last_gc_stat = nullptr;
  _last_gc_lock = new Mutex(Mutex::nosafepoint, "GCMemoryManager_lock");
  _current_gc_stat = nullptr;
  _num_gc_threads = 1;
  _notification_enabled = false;
}

GCMemoryManager::~GCMemoryManager() {
  delete _last_gc_stat;
  delete _last_gc_lock;
  delete _current_gc_stat;
}

void GCMemoryManager::add_pool(MemoryPool* pool) {
  add_pool(pool, true);
}

void GCMemoryManager::add_pool(MemoryPool* pool, bool always_affected_by_gc) {
  int index = MemoryManager::add_pool(pool);
  _pool_always_affected_by_gc[index] = always_affected_by_gc;
}

void GCMemoryManager::initialize_gc_stat_info() {
  assert(MemoryService::num_memory_pools() > 0, "should have one or more memory pools");
  _last_gc_stat = new GCStatInfo(MemoryService::num_memory_pools());
  _current_gc_stat = new GCStatInfo(MemoryService::num_memory_pools());
  // tracking concurrent collections we need two objects: one to update, and one to
  // hold the publicly available "last (completed) gc" information.
}

void GCMemoryManager::gc_begin(bool recordGCBeginTime, bool recordPreGCUsage,
                               bool recordAccumulatedGCTime) {
  assert(_last_gc_stat != nullptr && _current_gc_stat != nullptr, "Just checking");
  if (recordAccumulatedGCTime) {
    _accumulated_timer.start();
  }
  // _num_collections now increases in gc_end, to count completed collections
  if (recordGCBeginTime) {
    _current_gc_stat->set_index(_num_collections+1);
    _current_gc_stat->set_start_time(Management::timestamp());
  }

  if (recordPreGCUsage) {
    // Keep memory usage of all memory pools
    for (int i = 0; i < MemoryService::num_memory_pools(); i++) {
      MemoryPool* pool = MemoryService::get_memory_pool(i);
      MemoryUsage usage = pool->get_memory_usage();
      _current_gc_stat->set_before_gc_usage(i, usage);
      HOTSPOT_MEM_POOL_GC_BEGIN(
        (char *) name(), strlen(name()),
        (char *) pool->name(), strlen(pool->name()),
        usage.init_size(), usage.used(),
        usage.committed(), usage.max_size());
    }
  }
}

// A collector MUST, even if it does not complete for some reason,
// make a TraceMemoryManagerStats object where countCollection is true,
// to ensure the current gc stat is placed in _last_gc_stat.
void GCMemoryManager::gc_end(bool recordPostGCUsage,
                             bool recordAccumulatedGCTime,
                             bool recordGCEndTime,
                             bool countCollection,
                             GCCause::Cause cause,
                             bool allMemoryPoolsAffected,
                             const char* message) {
  if (recordAccumulatedGCTime) {
    _accumulated_timer.stop();
  }
  if (recordGCEndTime) {
    _current_gc_stat->set_end_time(Management::timestamp());
  }

  if (recordPostGCUsage) {
    int i;
    // keep the last gc statistics for all memory pools
    for (i = 0; i < MemoryService::num_memory_pools(); i++) {
      MemoryPool* pool = MemoryService::get_memory_pool(i);
      MemoryUsage usage = pool->get_memory_usage();

      HOTSPOT_MEM_POOL_GC_END(
        (char *) name(), strlen(name()),
        (char *) pool->name(), strlen(pool->name()),
        usage.init_size(), usage.used(),
        usage.committed(), usage.max_size());

      _current_gc_stat->set_after_gc_usage(i, usage);
    }

    // Set last collection usage of the memory pools managed by this collector
    for (i = 0; i < num_memory_pools(); i++) {
      MemoryPool* pool = get_memory_pool(i);
      MemoryUsage usage = pool->get_memory_usage();

      if (allMemoryPoolsAffected || pool_always_affected_by_gc(i)) {
        // Compare with GC usage threshold
        pool->set_last_collection_usage(usage);
        LowMemoryDetector::detect_after_gc_memory(pool);
      }
    }
  }

  if (countCollection) {
    _num_collections++;
    // alternately update two objects making one public when complete
    {
      MutexLocker ml(_last_gc_lock, Mutex::_no_safepoint_check_flag);
      GCStatInfo *tmp = _last_gc_stat;
      _last_gc_stat = _current_gc_stat;
      _current_gc_stat = tmp;
      // reset the current stat for diagnosability purposes
      _current_gc_stat->clear();
    }

    if (is_notification_enabled()) {
      GCNotifier::pushNotification(this, message, GCCause::to_string(cause));
    }
  }
}

size_t GCMemoryManager::get_last_gc_stat(GCStatInfo* dest) {
  MutexLocker ml(_last_gc_lock, Mutex::_no_safepoint_check_flag);
  if (_last_gc_stat->gc_index() != 0) {
    dest->set_index(_last_gc_stat->gc_index());
    dest->set_start_time(_last_gc_stat->start_time());
    dest->set_end_time(_last_gc_stat->end_time());
    assert(dest->usage_array_size() == _last_gc_stat->usage_array_size(),
           "Must have same array size");
    size_t len = dest->usage_array_size() * sizeof(MemoryUsage);
    memcpy(dest->before_gc_usage_array(), _last_gc_stat->before_gc_usage_array(), len);
    memcpy(dest->after_gc_usage_array(), _last_gc_stat->after_gc_usage_array(), len);
  }
  return _last_gc_stat->gc_index();
}
