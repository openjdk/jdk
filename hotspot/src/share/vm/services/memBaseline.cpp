/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "services/memBaseline.hpp"
#include "services/memTracker.hpp"


MemType2Name MemBaseline::MemType2NameMap[NUMBER_OF_MEMORY_TYPE] = {
  {mtJavaHeap,   "Java Heap"},
  {mtClass,      "Class"},
  {mtThreadStack,"Thread Stack"},
  {mtThread,     "Thread"},
  {mtCode,       "Code"},
  {mtGC,         "GC"},
  {mtCompiler,   "Compiler"},
  {mtInternal,   "Internal"},
  {mtOther,      "Other"},
  {mtSymbol,     "Symbol"},
  {mtNMT,        "Memory Tracking"},
  {mtTracing,    "Tracing"},
  {mtChunk,      "Pooled Free Chunks"},
  {mtClassShared,"Shared spaces for classes"},
  {mtTest,       "Test"},
  {mtNone,       "Unknown"}  // It can happen when type tagging records are lagging
                             // behind
};

MemBaseline::MemBaseline() {
  _baselined = false;

  for (int index = 0; index < NUMBER_OF_MEMORY_TYPE; index ++) {
    _malloc_data[index].set_type(MemType2NameMap[index]._flag);
    _vm_data[index].set_type(MemType2NameMap[index]._flag);
    _arena_data[index].set_type(MemType2NameMap[index]._flag);
  }

  _malloc_cs = NULL;
  _vm_cs = NULL;
  _vm_map = NULL;

  _number_of_classes = 0;
  _number_of_threads = 0;
}


void MemBaseline::clear() {
  if (_malloc_cs != NULL) {
    delete _malloc_cs;
    _malloc_cs = NULL;
  }

  if (_vm_cs != NULL) {
    delete _vm_cs;
    _vm_cs = NULL;
  }

  if (_vm_map != NULL) {
    delete _vm_map;
    _vm_map = NULL;
  }

  reset();
}


void MemBaseline::reset() {
  _baselined = false;
  _total_vm_reserved = 0;
  _total_vm_committed = 0;
  _total_malloced = 0;
  _number_of_classes = 0;

  if (_malloc_cs != NULL) _malloc_cs->clear();
  if (_vm_cs != NULL) _vm_cs->clear();
  if (_vm_map != NULL) _vm_map->clear();

  for (int index = 0; index < NUMBER_OF_MEMORY_TYPE; index ++) {
    _malloc_data[index].clear();
    _vm_data[index].clear();
    _arena_data[index].clear();
  }
}

MemBaseline::~MemBaseline() {
  clear();
}

// baseline malloc'd memory records, generate overall summary and summaries by
// memory types
bool MemBaseline::baseline_malloc_summary(const MemPointerArray* malloc_records) {
  MemPointerArrayIteratorImpl malloc_itr((MemPointerArray*)malloc_records);
  MemPointerRecord* malloc_ptr = (MemPointerRecord*)malloc_itr.current();
  size_t used_arena_size = 0;
  int index;
  while (malloc_ptr != NULL) {
    index = flag2index(FLAGS_TO_MEMORY_TYPE(malloc_ptr->flags()));
    size_t size = malloc_ptr->size();
    if (malloc_ptr->is_arena_memory_record()) {
      // We do have anonymous arenas, they are either used as value objects,
      // which are embedded inside other objects, or used as stack objects.
      _arena_data[index].inc(size);
      used_arena_size += size;
    } else {
      _total_malloced += size;
      _malloc_data[index].inc(size);
      if (malloc_ptr->is_arena_record()) {
        // see if arena memory record present
        MemPointerRecord* next_malloc_ptr = (MemPointerRecordEx*)malloc_itr.peek_next();
        if (next_malloc_ptr != NULL && next_malloc_ptr->is_arena_memory_record()) {
          assert(next_malloc_ptr->is_memory_record_of_arena(malloc_ptr),
             "Arena records do not match");
          size = next_malloc_ptr->size();
          _arena_data[index].inc(size);
          used_arena_size += size;
          malloc_itr.next();
        }
      }
    }
    malloc_ptr = (MemPointerRecordEx*)malloc_itr.next();
  }

  // substract used arena size to get size of arena chunk in free list
  index = flag2index(mtChunk);
  _malloc_data[index].reduce(used_arena_size);
  // we really don't know how many chunks in free list, so just set to
  // 0
  _malloc_data[index].overwrite_counter(0);

  return true;
}

// check if there is a safepoint in progress, if so, block the thread
// for the safepoint
void MemBaseline::check_safepoint(JavaThread* thr) {
  if (SafepointSynchronize::is_synchronizing()) {
    // grab and drop the SR_lock to honor the safepoint protocol
    MutexLocker ml(thr->SR_lock());
  }
}

// baseline mmap'd memory records, generate overall summary and summaries by
// memory types
bool MemBaseline::baseline_vm_summary(const MemPointerArray* vm_records) {
  MemPointerArrayIteratorImpl vm_itr((MemPointerArray*)vm_records);
  VMMemRegion* vm_ptr = (VMMemRegion*)vm_itr.current();
  int index;
  while (vm_ptr != NULL) {
    if (vm_ptr->is_reserved_region()) {
      index = flag2index(FLAGS_TO_MEMORY_TYPE(vm_ptr->flags()));
    // we use the number of thread stack to count threads
      if (IS_MEMORY_TYPE(vm_ptr->flags(), mtThreadStack)) {
      _number_of_threads ++;
    }
      _total_vm_reserved += vm_ptr->size();
      _vm_data[index].inc(vm_ptr->size(), 0);
    } else {
      _total_vm_committed += vm_ptr->size();
      _vm_data[index].inc(0, vm_ptr->size());
    }
    vm_ptr = (VMMemRegion*)vm_itr.next();
  }
  return true;
}

// baseline malloc'd memory by callsites, but only the callsites with memory allocation
// over 1KB are stored.
bool MemBaseline::baseline_malloc_details(const MemPointerArray* malloc_records) {
  assert(MemTracker::track_callsite(), "detail tracking is off");

  MemPointerArrayIteratorImpl malloc_itr(const_cast<MemPointerArray*>(malloc_records));
  MemPointerRecordEx* malloc_ptr = (MemPointerRecordEx*)malloc_itr.current();
  MallocCallsitePointer malloc_callsite;

  // initailize malloc callsite array
  if (_malloc_cs == NULL) {
    _malloc_cs = new (std::nothrow) MemPointerArrayImpl<MallocCallsitePointer>(64);
    // out of native memory
    if (_malloc_cs == NULL || _malloc_cs->out_of_memory()) {
      return false;
    }
  } else {
    _malloc_cs->clear();
  }

  MemPointerArray* malloc_data = const_cast<MemPointerArray*>(malloc_records);

  // sort into callsite pc order. Details are aggregated by callsites
  malloc_data->sort((FN_SORT)malloc_sort_by_pc);
  bool ret = true;

  // baseline memory that is totaled over 1 KB
  while (malloc_ptr != NULL) {
    if (!MemPointerRecord::is_arena_memory_record(malloc_ptr->flags())) {
      // skip thread stacks
      if (!IS_MEMORY_TYPE(malloc_ptr->flags(), mtThreadStack)) {
        if (malloc_callsite.addr() != malloc_ptr->pc()) {
          if ((malloc_callsite.amount()/K) > 0) {
            if (!_malloc_cs->append(&malloc_callsite)) {
              ret = false;
              break;
            }
          }
          malloc_callsite = MallocCallsitePointer(malloc_ptr->pc());
        }
        malloc_callsite.inc(malloc_ptr->size());
      }
    }
    malloc_ptr = (MemPointerRecordEx*)malloc_itr.next();
  }

  // restore to address order. Snapshot malloc data is maintained in memory
  // address order.
  malloc_data->sort((FN_SORT)malloc_sort_by_addr);

  if (!ret) {
              return false;
            }
  // deal with last record
  if (malloc_callsite.addr() != 0 && (malloc_callsite.amount()/K) > 0) {
    if (!_malloc_cs->append(&malloc_callsite)) {
      return false;
    }
  }
  return true;
}

// baseline mmap'd memory by callsites
bool MemBaseline::baseline_vm_details(const MemPointerArray* vm_records) {
  assert(MemTracker::track_callsite(), "detail tracking is off");

  VMCallsitePointer  vm_callsite;
  VMCallsitePointer* cur_callsite = NULL;
  MemPointerArrayIteratorImpl vm_itr((MemPointerArray*)vm_records);
  VMMemRegionEx* vm_ptr = (VMMemRegionEx*)vm_itr.current();

  // initialize virtual memory map array
  if (_vm_map == NULL) {
    _vm_map = new (std::nothrow) MemPointerArrayImpl<VMMemRegionEx>(vm_records->length());
   if (_vm_map == NULL || _vm_map->out_of_memory()) {
     return false;
   }
  } else {
    _vm_map->clear();
  }

  // initialize virtual memory callsite array
  if (_vm_cs == NULL) {
    _vm_cs = new (std::nothrow) MemPointerArrayImpl<VMCallsitePointer>(64);
    if (_vm_cs == NULL || _vm_cs->out_of_memory()) {
      return false;
    }
  } else {
    _vm_cs->clear();
  }

  // consolidate virtual memory data
  VMMemRegionEx*     reserved_rec = NULL;
  VMMemRegionEx*     committed_rec = NULL;

  // vm_ptr is coming in increasing base address order
  while (vm_ptr != NULL) {
    if (vm_ptr->is_reserved_region()) {
      // consolidate reserved memory regions for virtual memory map.
      // The criteria for consolidation is:
      // 1. two adjacent reserved memory regions
      // 2. belong to the same memory type
      // 3. reserved from the same callsite
      if (reserved_rec == NULL ||
        reserved_rec->base() + reserved_rec->size() != vm_ptr->addr() ||
        FLAGS_TO_MEMORY_TYPE(reserved_rec->flags()) != FLAGS_TO_MEMORY_TYPE(vm_ptr->flags()) ||
        reserved_rec->pc() != vm_ptr->pc()) {
        if (!_vm_map->append(vm_ptr)) {
        return false;
      }
        // inserted reserved region, we need the pointer to the element in virtual
        // memory map array.
        reserved_rec = (VMMemRegionEx*)_vm_map->at(_vm_map->length() - 1);
      } else {
        reserved_rec->expand_region(vm_ptr->addr(), vm_ptr->size());
    }

      if (cur_callsite != NULL && !_vm_cs->append(cur_callsite)) {
      return false;
    }
      vm_callsite = VMCallsitePointer(vm_ptr->pc());
      cur_callsite = &vm_callsite;
      vm_callsite.inc(vm_ptr->size(), 0);
    } else {
      // consolidate committed memory regions for virtual memory map
      // The criterial is:
      // 1. two adjacent committed memory regions
      // 2. committed from the same callsite
      if (committed_rec == NULL ||
        committed_rec->base() + committed_rec->size() != vm_ptr->addr() ||
        committed_rec->pc() != vm_ptr->pc()) {
        if (!_vm_map->append(vm_ptr)) {
          return false;
        }
        committed_rec = (VMMemRegionEx*)_vm_map->at(_vm_map->length() - 1);
    } else {
        committed_rec->expand_region(vm_ptr->addr(), vm_ptr->size());
      }
      vm_callsite.inc(0, vm_ptr->size());
    }
    vm_ptr = (VMMemRegionEx*)vm_itr.next();
  }
  // deal with last record
  if (cur_callsite != NULL && !_vm_cs->append(cur_callsite)) {
    return false;
  }

  // sort it into callsite pc order. Details are aggregated by callsites
  _vm_cs->sort((FN_SORT)bl_vm_sort_by_pc);

  // walk the array to consolidate record by pc
  MemPointerArrayIteratorImpl itr(_vm_cs);
  VMCallsitePointer* callsite_rec = (VMCallsitePointer*)itr.current();
  VMCallsitePointer* next_rec = (VMCallsitePointer*)itr.next();
  while (next_rec != NULL) {
    assert(callsite_rec != NULL, "Sanity check");
    if (next_rec->addr() == callsite_rec->addr()) {
      callsite_rec->inc(next_rec->reserved_amount(), next_rec->committed_amount());
      itr.remove();
      next_rec = (VMCallsitePointer*)itr.current();
    } else {
      callsite_rec = next_rec;
      next_rec = (VMCallsitePointer*)itr.next();
    }
  }

  return true;
}

// baseline a snapshot. If summary_only = false, memory usages aggregated by
// callsites are also baselined.
// The method call can be lengthy, especially when detail tracking info is
// requested. So the method checks for safepoint explicitly.
bool MemBaseline::baseline(MemSnapshot& snapshot, bool summary_only) {
  Thread* THREAD = Thread::current();
  assert(THREAD->is_Java_thread(), "must be a JavaThread");
  MutexLocker snapshot_locker(snapshot._lock);
  reset();
  _baselined = baseline_malloc_summary(snapshot._alloc_ptrs);
  if (_baselined) {
    check_safepoint((JavaThread*)THREAD);
    _baselined = baseline_vm_summary(snapshot._vm_ptrs);
  }
  _number_of_classes = snapshot.number_of_classes();

  if (!summary_only && MemTracker::track_callsite() && _baselined) {
    check_safepoint((JavaThread*)THREAD);
    _baselined =  baseline_malloc_details(snapshot._alloc_ptrs);
    if (_baselined) {
      check_safepoint((JavaThread*)THREAD);
      _baselined =  baseline_vm_details(snapshot._vm_ptrs);
    }
  }
  return _baselined;
}


int MemBaseline::flag2index(MEMFLAGS flag) const {
  for (int index = 0; index < NUMBER_OF_MEMORY_TYPE; index ++) {
    if (MemType2NameMap[index]._flag == flag) {
      return index;
    }
  }
  assert(false, "no type");
  return -1;
}

const char* MemBaseline::type2name(MEMFLAGS type) {
  for (int index = 0; index < NUMBER_OF_MEMORY_TYPE; index ++) {
    if (MemType2NameMap[index]._flag == type) {
      return MemType2NameMap[index]._name;
    }
  }
  assert(false, err_msg("bad type %x", type));
  return NULL;
}


MemBaseline& MemBaseline::operator=(const MemBaseline& other) {
  _total_malloced = other._total_malloced;
  _total_vm_reserved = other._total_vm_reserved;
  _total_vm_committed = other._total_vm_committed;

  _baselined = other._baselined;
  _number_of_classes = other._number_of_classes;

  for (int index = 0; index < NUMBER_OF_MEMORY_TYPE; index ++) {
    _malloc_data[index] = other._malloc_data[index];
    _vm_data[index] = other._vm_data[index];
    _arena_data[index] = other._arena_data[index];
  }

  if (MemTracker::track_callsite()) {
    assert(_malloc_cs != NULL && _vm_cs != NULL, "out of memory");
    assert(other._malloc_cs != NULL && other._vm_cs != NULL,
           "not properly baselined");
    _malloc_cs->clear();
    _vm_cs->clear();
    int index;
    for (index = 0; index < other._malloc_cs->length(); index ++) {
      _malloc_cs->append(other._malloc_cs->at(index));
    }

    for (index = 0; index < other._vm_cs->length(); index ++) {
      _vm_cs->append(other._vm_cs->at(index));
    }
  }
  return *this;
}

/* compare functions for sorting */

// sort snapshot malloc'd records in callsite pc order
int MemBaseline::malloc_sort_by_pc(const void* p1, const void* p2) {
  assert(MemTracker::track_callsite(),"Just check");
  const MemPointerRecordEx* mp1 = (const MemPointerRecordEx*)p1;
  const MemPointerRecordEx* mp2 = (const MemPointerRecordEx*)p2;
  return UNSIGNED_COMPARE(mp1->pc(), mp2->pc());
}

// sort baselined malloc'd records in size order
int MemBaseline::bl_malloc_sort_by_size(const void* p1, const void* p2) {
  assert(MemTracker::is_on(), "Just check");
  const MallocCallsitePointer* mp1 = (const MallocCallsitePointer*)p1;
  const MallocCallsitePointer* mp2 = (const MallocCallsitePointer*)p2;
  return UNSIGNED_COMPARE(mp2->amount(), mp1->amount());
}

// sort baselined malloc'd records in callsite pc order
int MemBaseline::bl_malloc_sort_by_pc(const void* p1, const void* p2) {
  assert(MemTracker::is_on(), "Just check");
  const MallocCallsitePointer* mp1 = (const MallocCallsitePointer*)p1;
  const MallocCallsitePointer* mp2 = (const MallocCallsitePointer*)p2;
  return UNSIGNED_COMPARE(mp1->addr(), mp2->addr());
}


// sort baselined mmap'd records in size (reserved size) order
int MemBaseline::bl_vm_sort_by_size(const void* p1, const void* p2) {
  assert(MemTracker::is_on(), "Just check");
  const VMCallsitePointer* mp1 = (const VMCallsitePointer*)p1;
  const VMCallsitePointer* mp2 = (const VMCallsitePointer*)p2;
  return UNSIGNED_COMPARE(mp2->reserved_amount(), mp1->reserved_amount());
}

// sort baselined mmap'd records in callsite pc order
int MemBaseline::bl_vm_sort_by_pc(const void* p1, const void* p2) {
  assert(MemTracker::is_on(), "Just check");
  const VMCallsitePointer* mp1 = (const VMCallsitePointer*)p1;
  const VMCallsitePointer* mp2 = (const VMCallsitePointer*)p2;
  return UNSIGNED_COMPARE(mp1->addr(), mp2->addr());
}


// sort snapshot malloc'd records in memory block address order
int MemBaseline::malloc_sort_by_addr(const void* p1, const void* p2) {
  assert(MemTracker::is_on(), "Just check");
  const MemPointerRecord* mp1 = (const MemPointerRecord*)p1;
  const MemPointerRecord* mp2 = (const MemPointerRecord*)p2;
  int delta = UNSIGNED_COMPARE(mp1->addr(), mp2->addr());
  assert(p1 == p2 || delta != 0, "dup pointer");
  return delta;
}

