/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHash.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zNMethodTable.hpp"
#include "gc/z/zOopClosures.inline.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zWorkers.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"

class ZNMethodDataImmediateOops {
private:
  const size_t _nimmediate_oops;

  static size_t header_size();

  ZNMethodDataImmediateOops(const GrowableArray<oop*>& immediate_oops);

public:
  static ZNMethodDataImmediateOops* create(const GrowableArray<oop*>& immediate_oops);
  static void destroy(ZNMethodDataImmediateOops* data_immediate_oops);

  size_t immediate_oops_count() const;
  oop** immediate_oops_begin() const;
  oop** immediate_oops_end() const;
};

size_t ZNMethodDataImmediateOops::header_size() {
  const size_t size = sizeof(ZNMethodDataImmediateOops);
  assert(is_aligned(size, sizeof(oop*)), "Header misaligned");
  return size;
}

ZNMethodDataImmediateOops* ZNMethodDataImmediateOops::create(const GrowableArray<oop*>& immediate_oops) {
  // Allocate memory for the ZNMethodDataImmediateOops object
  // plus the immediate oop* array that follows right after.
  const size_t size = ZNMethodDataImmediateOops::header_size() + (sizeof(oop*) * immediate_oops.length());
  void* const data_immediate_oops = NEW_C_HEAP_ARRAY(uint8_t, size, mtGC);
  return ::new (data_immediate_oops) ZNMethodDataImmediateOops(immediate_oops);
}

void ZNMethodDataImmediateOops::destroy(ZNMethodDataImmediateOops* data_immediate_oops) {
  ZNMethodTable::safe_delete(data_immediate_oops);
}

ZNMethodDataImmediateOops::ZNMethodDataImmediateOops(const GrowableArray<oop*>& immediate_oops) :
    _nimmediate_oops(immediate_oops.length()) {
  // Save all immediate oops
  for (size_t i = 0; i < _nimmediate_oops; i++) {
    immediate_oops_begin()[i] = immediate_oops.at(i);
  }
}

size_t ZNMethodDataImmediateOops::immediate_oops_count() const {
  return _nimmediate_oops;
}

oop** ZNMethodDataImmediateOops::immediate_oops_begin() const {
  // The immediate oop* array starts immediately after this object
  return (oop**)((uintptr_t)this + header_size());
}

oop** ZNMethodDataImmediateOops::immediate_oops_end() const {
  return immediate_oops_begin() + immediate_oops_count();
}

class ZNMethodData {
private:
  ZReentrantLock                      _lock;
  ZNMethodDataImmediateOops* volatile _immediate_oops;

  ZNMethodData(nmethod* nm);

public:
  static ZNMethodData* create(nmethod* nm);
  static void destroy(ZNMethodData* data);

  ZReentrantLock* lock();

  ZNMethodDataImmediateOops* immediate_oops() const;
  ZNMethodDataImmediateOops* swap_immediate_oops(const GrowableArray<oop*>& immediate_oops);
};

ZNMethodData* ZNMethodData::create(nmethod* nm) {
  void* const method = NEW_C_HEAP_ARRAY(uint8_t, sizeof(ZNMethodData), mtGC);
  return ::new (method) ZNMethodData(nm);
}

void ZNMethodData::destroy(ZNMethodData* data) {
  ZNMethodDataImmediateOops::destroy(data->immediate_oops());
  ZNMethodTable::safe_delete(data);
}

ZNMethodData::ZNMethodData(nmethod* nm) :
    _lock(),
    _immediate_oops(NULL) {}

ZReentrantLock* ZNMethodData::lock() {
  return &_lock;
}

ZNMethodDataImmediateOops* ZNMethodData::immediate_oops() const {
  return OrderAccess::load_acquire(&_immediate_oops);
}

ZNMethodDataImmediateOops* ZNMethodData::swap_immediate_oops(const GrowableArray<oop*>& immediate_oops) {
  ZNMethodDataImmediateOops* const data_immediate_oops =
    immediate_oops.is_empty() ? NULL : ZNMethodDataImmediateOops::create(immediate_oops);
  return Atomic::xchg(data_immediate_oops, &_immediate_oops);
}

static ZNMethodData* gc_data(const nmethod* nm) {
  return nm->gc_data<ZNMethodData>();
}

static void set_gc_data(nmethod* nm, ZNMethodData* data) {
  return nm->set_gc_data<ZNMethodData>(data);
}

ZNMethodTableEntry* ZNMethodTable::_table = NULL;
size_t ZNMethodTable::_size = 0;
ZLock ZNMethodTable::_iter_lock;
ZNMethodTableEntry* ZNMethodTable::_iter_table = NULL;
size_t ZNMethodTable::_iter_table_size = 0;
ZArray<void*> ZNMethodTable::_iter_deferred_deletes;
size_t ZNMethodTable::_nregistered = 0;
size_t ZNMethodTable::_nunregistered = 0;
volatile size_t ZNMethodTable::_claimed = 0;

void ZNMethodTable::safe_delete(void* data) {
  if (data == NULL) {
    return;
  }

  ZLocker<ZLock> locker(&_iter_lock);
  if (_iter_table != NULL) {
    // Iteration in progress, defer delete
    _iter_deferred_deletes.add(data);
  } else {
    // Iteration not in progress, delete now
    FREE_C_HEAP_ARRAY(uint8_t, data);
  }
}

ZNMethodTableEntry ZNMethodTable::create_entry(nmethod* nm) {
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

  // Attach immediate oops in GC data
  ZNMethodDataImmediateOops* const old_data_immediate_oops = data->swap_immediate_oops(immediate_oops);
  ZNMethodDataImmediateOops::destroy(old_data_immediate_oops);

  // Create entry
  return ZNMethodTableEntry(nm, non_immediate_oops, !immediate_oops.is_empty());
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

bool ZNMethodTable::register_entry(ZNMethodTableEntry* table, size_t size, ZNMethodTableEntry entry) {
  const nmethod* const nm = entry.method();
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
  if (size == 0) {
    // Table is empty
    return;
  }

  size_t index = first_index(nm, size);

  for (;;) {
    const ZNMethodTableEntry table_entry = table[index];
    assert(table_entry.registered() || table_entry.unregistered(), "Entry not found");

    if (table_entry.registered() && table_entry.method() == nm) {
      // Remove entry
      table[index] = ZNMethodTableEntry(true /* unregistered */);

      // Destroy GC data
      ZNMethodData::destroy(gc_data(nm));
      set_gc_data(nm, NULL);
      return;
    }

    index = next_index(index, size);
  }
}

void ZNMethodTable::rebuild(size_t new_size) {
  ZLocker<ZLock> locker(&_iter_lock);
  assert(is_power_of_2(new_size), "Invalid size");

  log_debug(gc, nmethod)("Rebuilding NMethod Table: "
                         SIZE_FORMAT "->" SIZE_FORMAT " entries, "
                         SIZE_FORMAT "(%.0lf%%->%.0lf%%) registered, "
                         SIZE_FORMAT "(%.0lf%%->%.0lf%%) unregistered",
                         _size, new_size,
                         _nregistered, percent_of(_nregistered, _size), percent_of(_nregistered, new_size),
                         _nunregistered, percent_of(_nunregistered, _size), 0.0);

  // Allocate new table
  ZNMethodTableEntry* const new_table = new ZNMethodTableEntry[new_size];

  // Transfer all registered entries
  for (size_t i = 0; i < _size; i++) {
    const ZNMethodTableEntry entry = _table[i];
    if (entry.registered()) {
      register_entry(new_table, new_size, entry);
    }
  }

  if (_iter_table != _table) {
    // Delete old table
    delete [] _table;
  }

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

void ZNMethodTable::log_register(const nmethod* nm, ZNMethodTableEntry entry) {
  LogTarget(Trace, gc, nmethod) log;
  if (!log.is_enabled()) {
    return;
  }

  log.print("Register NMethod: %s.%s (" PTR_FORMAT "), "
            "Compiler: %s, Oops: %d, ImmediateOops: " SIZE_FORMAT ", NonImmediateOops: %s",
            nm->method()->method_holder()->external_name(),
            nm->method()->name()->as_C_string(),
            p2i(nm),
            nm->compiler_name(),
            nm->oops_count() - 1,
            entry.immediate_oops() ? gc_data(nm)->immediate_oops()->immediate_oops_count() : 0,
            entry.non_immediate_oops() ? "Yes" : "No");

  LogTarget(Trace, gc, nmethod, oops) log_oops;
  if (!log_oops.is_enabled()) {
    return;
  }

  // Print nmethod oops table
  oop* const begin = nm->oops_begin();
  oop* const end = nm->oops_end();
  for (oop* p = begin; p < end; p++) {
    log_oops.print("           Oop[" SIZE_FORMAT "] " PTR_FORMAT " (%s)",
                   (p - begin), p2i(*p), (*p)->klass()->external_name());
  }

  if (entry.immediate_oops()) {
    // Print nmethod immediate oops
    const ZNMethodDataImmediateOops* const nmi = gc_data(nm)->immediate_oops();
    if (nmi != NULL) {
      oop** const begin = nmi->immediate_oops_begin();
      oop** const end = nmi->immediate_oops_end();
      for (oop** p = begin; p < end; p++) {
        log_oops.print("  ImmediateOop[" SIZE_FORMAT "] " PTR_FORMAT " @ " PTR_FORMAT " (%s)",
                       (p - begin), p2i(**p), p2i(*p), (**p)->klass()->external_name());
      }
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

  // Create entry
  const ZNMethodTableEntry entry = create_entry(nm);

  log_register(nm, entry);

  // Insert new entry
  if (register_entry(_table, _size, entry)) {
    // New entry registered. When register_entry() instead returns
    // false the nmethod was already in the table so we do not want
    // to increase number of registered entries in that case.
    _nregistered++;
  }

  // Disarm nmethod entry barrier
  disarm_nmethod(nm);
}

void ZNMethodTable::sweeper_wait_for_iteration() {
  // The sweeper must wait for any ongoing iteration to complete
  // before it can unregister an nmethod.
  if (!Thread::current()->is_Code_cache_sweeper_thread()) {
    return;
  }

  while (_iter_table != NULL) {
    MutexUnlockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    os::naked_short_sleep(1);
  }
}

void ZNMethodTable::unregister_nmethod(nmethod* nm) {
  assert(CodeCache_lock->owned_by_self(), "Lock must be held");
  ResourceMark rm;

  sweeper_wait_for_iteration();

  log_unregister(nm);

  // Remove entry
  unregister_entry(_table, _size, nm);
  _nunregistered++;
  _nregistered--;
}

void ZNMethodTable::disarm_nmethod(nmethod* nm) {
  BarrierSetNMethod* const bs = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs != NULL) {
    bs->disarm(nm);
  }
}

void ZNMethodTable::nmethod_entries_do_begin() {
  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  ZLocker<ZLock> locker(&_iter_lock);

  // Prepare iteration
  _iter_table = _table;
  _iter_table_size = _size;
  _claimed = 0;
  assert(_iter_deferred_deletes.is_empty(), "Should be emtpy");
}

void ZNMethodTable::nmethod_entries_do_end() {
  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  ZLocker<ZLock> locker(&_iter_lock);

  // Finish iteration
  if (_iter_table != _table) {
    delete [] _iter_table;
  }
  _iter_table = NULL;
  assert(_claimed >= _iter_table_size, "Failed to claim all table entries");

  // Process deferred deletes
  ZArrayIterator<void*> iter(&_iter_deferred_deletes);
  for (void* data; iter.next(&data);) {
    FREE_C_HEAP_ARRAY(uint8_t, data);
  }
  _iter_deferred_deletes.clear();
}

void ZNMethodTable::entry_oops_do(ZNMethodTableEntry entry, OopClosure* cl) {
  nmethod* const nm = entry.method();

  // Process oops table
  oop* const begin = nm->oops_begin();
  oop* const end = nm->oops_end();
  for (oop* p = begin; p < end; p++) {
    if (*p != Universe::non_oop_word()) {
      cl->do_oop(p);
    }
  }

  // Process immediate oops
  if (entry.immediate_oops()) {
    const ZNMethodDataImmediateOops* const nmi = gc_data(nm)->immediate_oops();
    if (nmi != NULL) {
      oop** const begin = nmi->immediate_oops_begin();
      oop** const end = nmi->immediate_oops_end();
      for (oop** p = begin; p < end; p++) {
        if (**p != Universe::non_oop_word()) {
          cl->do_oop(*p);
        }
      }
    }
  }

  // Process non-immediate oops
  if (entry.non_immediate_oops()) {
    nmethod* const nm = entry.method();
    nm->fix_oop_relocations();
  }
}

class ZNMethodTableEntryToOopsDo : public ZNMethodTableEntryClosure {
private:
  OopClosure* _cl;

public:
  ZNMethodTableEntryToOopsDo(OopClosure* cl) :
      _cl(cl) {}

  void do_nmethod_entry(ZNMethodTableEntry entry) {
    ZNMethodTable::entry_oops_do(entry, _cl);
  }
};

void ZNMethodTable::oops_do(OopClosure* cl) {
  ZNMethodTableEntryToOopsDo entry_cl(cl);
  nmethod_entries_do(&entry_cl);
}

void ZNMethodTable::nmethod_entries_do(ZNMethodTableEntryClosure* cl) {
  for (;;) {
    // Claim table partition. Each partition is currently sized to span
    // two cache lines. This number is just a guess, but seems to work well.
    const size_t partition_size = (ZCacheLineSize * 2) / sizeof(ZNMethodTableEntry);
    const size_t partition_start = MIN2(Atomic::add(partition_size, &_claimed) - partition_size, _iter_table_size);
    const size_t partition_end = MIN2(partition_start + partition_size, _iter_table_size);
    if (partition_start == partition_end) {
      // End of table
      break;
    }

    // Process table partition
    for (size_t i = partition_start; i < partition_end; i++) {
      const ZNMethodTableEntry entry = _iter_table[i];
      if (entry.registered()) {
        cl->do_nmethod_entry(entry);
      }
    }
  }
}

class ZNMethodTableUnlinkClosure : public ZNMethodTableEntryClosure {
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

  virtual void do_nmethod_entry(ZNMethodTableEntry entry) {
    if (failed()) {
      return;
    }

    nmethod* const nm = entry.method();
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
    ZNMethodTable::entry_oops_do(entry, &cl);
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
    ZNMethodTable::nmethod_entries_do_begin();
  }

  ~ZNMethodTableUnlinkTask() {
    ZNMethodTable::nmethod_entries_do_end();
  }

  virtual void work() {
    ICRefillVerifierMark mark(_verifier);
    ZNMethodTable::nmethod_entries_do(&_cl);
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

class ZNMethodTablePurgeClosure : public ZNMethodTableEntryClosure {
public:
  virtual void do_nmethod_entry(ZNMethodTableEntry entry) {
    nmethod* const nm = entry.method();
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
    ZNMethodTable::nmethod_entries_do_begin();
  }

  ~ZNMethodTablePurgeTask() {
    ZNMethodTable::nmethod_entries_do_end();
  }

  virtual void work() {
    ZNMethodTable::nmethod_entries_do(&_cl);
  }
};

void ZNMethodTable::purge(ZWorkers* workers) {
  ZNMethodTablePurgeTask task;
  workers->run_concurrent(&task);
}
