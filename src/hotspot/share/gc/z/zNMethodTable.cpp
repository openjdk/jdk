/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "code/relocInfo.hpp"
#include "code/nmethod.hpp"
#include "code/icBuffer.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHash.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zNMethodAllocator.hpp"
#include "gc/z/zNMethodData.hpp"
#include "gc/z/zNMethodTable.hpp"
#include "gc/z/zNMethodTableIteration.hpp"
#include "gc/z/zOopClosures.inline.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zWorkers.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/debug.hpp"

ZNMethodTableEntry* ZNMethodTable::_table = NULL;
size_t ZNMethodTable::_size = 0;
size_t ZNMethodTable::_nregistered = 0;
size_t ZNMethodTable::_nunregistered = 0;
ZNMethodTableIteration ZNMethodTable::_iteration;

static ZNMethodData* gc_data(const nmethod* nm) {
  return nm->gc_data<ZNMethodData>();
}

static void set_gc_data(nmethod* nm, ZNMethodData* data) {
  return nm->set_gc_data<ZNMethodData>(data);
}

ZNMethodTableEntry* ZNMethodTable::create(size_t size) {
  void* const mem = ZNMethodAllocator::allocate(size * sizeof(ZNMethodTableEntry));
  return ::new (mem) ZNMethodTableEntry[size];
}

void ZNMethodTable::destroy(ZNMethodTableEntry* table) {
  ZNMethodAllocator::free(table);
}

void ZNMethodTable::attach_gc_data(nmethod* nm) {
  GrowableArray<oop*> immediate_oops;
  bool non_immediate_oops = false;

  // Find all oops relocations
  RelocIterator iter(nm);
  while (iter.next()) {
    if (iter.type() != relocInfo::oop_type) {
      // Not an oop
      continue;
    }

    oop_Relocation* r = iter.oop_reloc();

    if (!r->oop_is_immediate()) {
      // Non-immediate oop found
      non_immediate_oops = true;
      continue;
    }

    if (r->oop_value() != NULL) {
      // Non-NULL immediate oop found. NULL oops can safely be
      // ignored since the method will be re-registered if they
      // are later patched to be non-NULL.
      immediate_oops.push(r->oop_addr());
    }
  }

  // Attach GC data to nmethod
  ZNMethodData* data = gc_data(nm);
  if (data == NULL) {
    data = ZNMethodData::create(nm);
    set_gc_data(nm, data);
  }

  // Attach oops in GC data
  ZNMethodDataOops* const new_oops = ZNMethodDataOops::create(immediate_oops, non_immediate_oops);
  ZNMethodDataOops* const old_oops = data->swap_oops(new_oops);
  ZNMethodDataOops::destroy(old_oops);
}

void ZNMethodTable::detach_gc_data(nmethod* nm) {
  // Destroy GC data
  ZNMethodData::destroy(gc_data(nm));
  set_gc_data(nm, NULL);
}

ZReentrantLock* ZNMethodTable::lock_for_nmethod(nmethod* nm) {
  ZNMethodData* const data = gc_data(nm);
  if (data == NULL) {
    return NULL;
  }
  return data->lock();
}

size_t ZNMethodTable::first_index(const nmethod* nm, size_t size) {
  assert(is_power_of_2(size), "Invalid size");
  const size_t mask = size - 1;
  const size_t hash = ZHash::address_to_uint32((uintptr_t)nm);
  return hash & mask;
}

size_t ZNMethodTable::next_index(size_t prev_index, size_t size) {
  assert(is_power_of_2(size), "Invalid size");
  const size_t mask = size - 1;
  return (prev_index + 1) & mask;
}

bool ZNMethodTable::register_entry(ZNMethodTableEntry* table, size_t size, nmethod* nm) {
  const ZNMethodTableEntry entry(nm);
  size_t index = first_index(nm, size);

  for (;;) {
    const ZNMethodTableEntry table_entry = table[index];

    if (!table_entry.registered() && !table_entry.unregistered()) {
      // Insert new entry
      table[index] = entry;
      return true;
    }

    if (table_entry.registered() && table_entry.method() == nm) {
      // Replace existing entry
      table[index] = entry;
      return false;
    }

    index = next_index(index, size);
  }
}

void ZNMethodTable::unregister_entry(ZNMethodTableEntry* table, size_t size, nmethod* nm) {
  size_t index = first_index(nm, size);

  for (;;) {
    const ZNMethodTableEntry table_entry = table[index];
    assert(table_entry.registered() || table_entry.unregistered(), "Entry not found");

    if (table_entry.registered() && table_entry.method() == nm) {
      // Remove entry
      table[index] = ZNMethodTableEntry(true /* unregistered */);
      return;
    }

    index = next_index(index, size);
  }
}

void ZNMethodTable::rebuild(size_t new_size) {
  assert(CodeCache_lock->owned_by_self(), "Lock must be held");

  assert(is_power_of_2(new_size), "Invalid size");

  log_debug(gc, nmethod)("Rebuilding NMethod Table: "
                         SIZE_FORMAT "->" SIZE_FORMAT " entries, "
                         SIZE_FORMAT "(%.0lf%%->%.0lf%%) registered, "
                         SIZE_FORMAT "(%.0lf%%->%.0lf%%) unregistered",
                         _size, new_size,
                         _nregistered, percent_of(_nregistered, _size), percent_of(_nregistered, new_size),
                         _nunregistered, percent_of(_nunregistered, _size), 0.0);

  // Allocate new table
  ZNMethodTableEntry* const new_table = ZNMethodTable::create(new_size);

  // Transfer all registered entries
  for (size_t i = 0; i < _size; i++) {
    const ZNMethodTableEntry entry = _table[i];
    if (entry.registered()) {
      register_entry(new_table, new_size, entry.method());
    }
  }

  // Free old table
  ZNMethodTable::destroy(_table);

  // Install new table
  _table = new_table;
  _size = new_size;
  _nunregistered = 0;
}

void ZNMethodTable::rebuild_if_needed() {
  // The hash table uses linear probing. To avoid wasting memory while
  // at the same time maintaining good hash collision behavior we want
  // to keep the table occupancy between 30% and 70%. The table always
  // grows/shrinks by doubling/halving its size. Pruning of unregistered
  // entries is done by rebuilding the table with or without resizing it.
  const size_t min_size = 1024;
  const size_t shrink_threshold = _size * 0.30;
  const size_t prune_threshold = _size * 0.65;
  const size_t grow_threshold = _size * 0.70;

  if (_size == 0) {
    // Initialize table
    rebuild(min_size);
  } else if (_nregistered < shrink_threshold && _size > min_size) {
    // Shrink table
    rebuild(_size / 2);
  } else if (_nregistered + _nunregistered > grow_threshold) {
    // Prune or grow table
    if (_nregistered < prune_threshold) {
      // Prune table
      rebuild(_size);
    } else {
      // Grow table
      rebuild(_size * 2);
    }
  }
}

void ZNMethodTable::log_register(const nmethod* nm) {
  LogTarget(Trace, gc, nmethod) log;
  if (!log.is_enabled()) {
    return;
  }

  const ZNMethodDataOops* const oops = gc_data(nm)->oops();

  log.print("Register NMethod: %s.%s (" PTR_FORMAT "), "
            "Compiler: %s, Oops: %d, ImmediateOops: " SIZE_FORMAT ", NonImmediateOops: %s",
            nm->method()->method_holder()->external_name(),
            nm->method()->name()->as_C_string(),
            p2i(nm),
            nm->compiler_name(),
            nm->oops_count() - 1,
            oops->immediates_count(),
            oops->has_non_immediates() ? "Yes" : "No");

  LogTarget(Trace, gc, nmethod, oops) log_oops;
  if (!log_oops.is_enabled()) {
    return;
  }

  // Print nmethod oops table
  {
    oop* const begin = nm->oops_begin();
    oop* const end = nm->oops_end();
    for (oop* p = begin; p < end; p++) {
      log_oops.print("           Oop[" SIZE_FORMAT "] " PTR_FORMAT " (%s)",
                     (p - begin), p2i(*p), (*p)->klass()->external_name());
    }
  }

  // Print nmethod immediate oops
  {
    oop** const begin = oops->immediates_begin();
    oop** const end = oops->immediates_end();
    for (oop** p = begin; p < end; p++) {
      log_oops.print("  ImmediateOop[" SIZE_FORMAT "] " PTR_FORMAT " @ " PTR_FORMAT " (%s)",
                     (p - begin), p2i(**p), p2i(*p), (**p)->klass()->external_name());
    }
  }
}

void ZNMethodTable::log_unregister(const nmethod* nm) {
  LogTarget(Debug, gc, nmethod) log;
  if (!log.is_enabled()) {
    return;
  }

  log.print("Unregister NMethod: %s.%s (" PTR_FORMAT ")",
            nm->method()->method_holder()->external_name(),
            nm->method()->name()->as_C_string(),
            p2i(nm));
}

size_t ZNMethodTable::registered_nmethods() {
  return _nregistered;
}

size_t ZNMethodTable::unregistered_nmethods() {
  return _nunregistered;
}

void ZNMethodTable::register_nmethod(nmethod* nm) {
  assert(CodeCache_lock->owned_by_self(), "Lock must be held");
  ResourceMark rm;

  // Grow/Shrink/Prune table if needed
  rebuild_if_needed();

  // Create and attach gc data
  attach_gc_data(nm);

  log_register(nm);

  // Insert new entry
  if (register_entry(_table, _size, nm)) {
    // New entry registered. When register_entry() instead returns
    // false the nmethod was already in the table so we do not want
    // to increase number of registered entries in that case.
    _nregistered++;
  }

  // Disarm nmethod entry barrier
  disarm_nmethod(nm);
}

void ZNMethodTable::wait_until_iteration_done() {
  assert(CodeCache_lock->owned_by_self(), "Lock must be held");

  while (_iteration.in_progress()) {
    CodeCache_lock->wait(Monitor::_no_safepoint_check_flag);
  }
}

void ZNMethodTable::unregister_nmethod(nmethod* nm) {
  assert(CodeCache_lock->owned_by_self(), "Lock must be held");

  if (Thread::current()->is_Code_cache_sweeper_thread()) {
    // The sweeper must wait for any ongoing iteration to complete
    // before it can unregister an nmethod.
    ZNMethodTable::wait_until_iteration_done();
  }

  ResourceMark rm;

  log_unregister(nm);

  // Remove entry
  unregister_entry(_table, _size, nm);
  _nunregistered++;
  _nregistered--;

  detach_gc_data(nm);
}

void ZNMethodTable::disarm_nmethod(nmethod* nm) {
  BarrierSetNMethod* const bs = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs != NULL) {
    bs->disarm(nm);
  }
}

void ZNMethodTable::nmethods_do_begin() {
  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

  // Make sure we don't free data while iterating
  ZNMethodAllocator::activate_deferred_frees();

  // Prepare iteration
  _iteration.nmethods_do_begin(_table, _size);
}

void ZNMethodTable::nmethods_do_end() {
  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

  // Finish iteration
  _iteration.nmethods_do_end();

  // Process deferred frees
  ZNMethodAllocator::deactivate_and_process_deferred_frees();

  // Notify iteration done
  CodeCache_lock->notify_all();
}

void ZNMethodTable::oops_do(nmethod* nm, OopClosure* cl) {
  // Process oops table
  {
    oop* const begin = nm->oops_begin();
    oop* const end = nm->oops_end();
    for (oop* p = begin; p < end; p++) {
      if (*p != Universe::non_oop_word()) {
        cl->do_oop(p);
      }
    }
  }

  ZNMethodDataOops* const oops = gc_data(nm)->oops();

  // Process immediate oops
  {
    oop** const begin = oops->immediates_begin();
    oop** const end = oops->immediates_end();
    for (oop** p = begin; p < end; p++) {
      if (**p != Universe::non_oop_word()) {
        cl->do_oop(*p);
      }
    }
  }

  // Process non-immediate oops
  if (oops->has_non_immediates()) {
    nm->fix_oop_relocations();
  }
}

class ZNMethodToOopsDoClosure : public NMethodClosure {
private:
  OopClosure* _cl;

public:
  ZNMethodToOopsDoClosure(OopClosure* cl) :
      _cl(cl) {}

  virtual void do_nmethod(nmethod* nm) {
    ZNMethodTable::oops_do(nm, _cl);
  }
};

void ZNMethodTable::oops_do(OopClosure* cl) {
  ZNMethodToOopsDoClosure nm_cl(cl);
  nmethods_do(&nm_cl);
}

void ZNMethodTable::nmethods_do(NMethodClosure* cl) {
  _iteration.nmethods_do(cl);
}

class ZNMethodTableUnlinkClosure : public NMethodClosure {
private:
  bool          _unloading_occurred;
  volatile bool _failed;

  void set_failed() {
    Atomic::store(true, &_failed);
  }

public:
  ZNMethodTableUnlinkClosure(bool unloading_occurred) :
      _unloading_occurred(unloading_occurred),
      _failed(false) {}

  virtual void do_nmethod(nmethod* nm) {
    if (failed()) {
      return;
    }

    if (!nm->is_alive()) {
      return;
    }

    ZLocker<ZReentrantLock> locker(ZNMethodTable::lock_for_nmethod(nm));

    if (nm->is_unloading()) {
      // Unlinking of the dependencies must happen before the
      // handshake separating unlink and purge.
      nm->flush_dependencies(false /* delete_immediately */);

      // We don't need to take the lock when unlinking nmethods from
      // the Method, because it is only concurrently unlinked by
      // the entry barrier, which acquires the per nmethod lock.
      nm->unlink_from_method(false /* acquire_lock */);
      return;
    }

    // Heal oops and disarm
    ZNMethodOopClosure cl;
    ZNMethodTable::oops_do(nm, &cl);
    ZNMethodTable::disarm_nmethod(nm);

    // Clear compiled ICs and exception caches
    if (!nm->unload_nmethod_caches(_unloading_occurred)) {
      set_failed();
    }
  }

  bool failed() const {
    return Atomic::load(&_failed);
  }
};

class ZNMethodTableUnlinkTask : public ZTask {
private:
  ZNMethodTableUnlinkClosure _cl;
  ICRefillVerifier*          _verifier;

public:
  ZNMethodTableUnlinkTask(bool unloading_occurred, ICRefillVerifier* verifier) :
      ZTask("ZNMethodTableUnlinkTask"),
      _cl(unloading_occurred),
      _verifier(verifier) {
    ZNMethodTable::nmethods_do_begin();
  }

  ~ZNMethodTableUnlinkTask() {
    ZNMethodTable::nmethods_do_end();
  }

  virtual void work() {
    ICRefillVerifierMark mark(_verifier);
    ZNMethodTable::nmethods_do(&_cl);
  }

  bool success() const {
    return !_cl.failed();
  }
};

void ZNMethodTable::unlink(ZWorkers* workers, bool unloading_occurred) {
  for (;;) {
    ICRefillVerifier verifier;

    {
      ZNMethodTableUnlinkTask task(unloading_occurred, &verifier);
      workers->run_concurrent(&task);
      if (task.success()) {
        return;
      }
    }

    // Cleaning failed because we ran out of transitional IC stubs,
    // so we have to refill and try again. Refilling requires taking
    // a safepoint, so we temporarily leave the suspendible thread set.
    SuspendibleThreadSetLeaver sts;
    InlineCacheBuffer::refill_ic_stubs();
  }
}

class ZNMethodTablePurgeClosure : public NMethodClosure {
public:
  virtual void do_nmethod(nmethod* nm) {
    if (nm->is_alive() && nm->is_unloading()) {
      nm->make_unloaded();
    }
  }
};

class ZNMethodTablePurgeTask : public ZTask {
private:
  ZNMethodTablePurgeClosure _cl;

public:
  ZNMethodTablePurgeTask() :
      ZTask("ZNMethodTablePurgeTask"),
      _cl() {
    ZNMethodTable::nmethods_do_begin();
  }

  ~ZNMethodTablePurgeTask() {
    ZNMethodTable::nmethods_do_end();
  }

  virtual void work() {
    ZNMethodTable::nmethods_do(&_cl);
  }
};

void ZNMethodTable::purge(ZWorkers* workers) {
  ZNMethodTablePurgeTask task;
  workers->run_concurrent(&task);
}
