/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "memory/allocation.hpp"
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
  {mtChunk,      "Pooled Free Chunks"},
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

  for (int index = 0; index < NUMBER_OF_MEMORY_TYPE; index ++) {
    _malloc_data[index].clear();
    _vm_data[index].clear();
    _arena_data[index].clear();
  }
}

MemBaseline::~MemBaseline() {
  if (_malloc_cs != NULL) {
    delete _malloc_cs;
  }

  if (_vm_cs != NULL) {
    delete _vm_cs;
  }
}

// baseline malloc'd memory records, generate overall summary and summaries by
// memory types
bool MemBaseline::baseline_malloc_summary(const MemPointerArray* malloc_records) {
  MemPointerArrayIteratorImpl mItr((MemPointerArray*)malloc_records);
  MemPointerRecord* mptr = (MemPointerRecord*)mItr.current();
  size_t used_arena_size = 0;
  int index;
  while (mptr != NULL) {
    index = flag2index(FLAGS_TO_MEMORY_TYPE(mptr->flags()));
    size_t size = mptr->size();
    _total_malloced += size;
    _malloc_data[index].inc(size);
    if (MemPointerRecord::is_arena_record(mptr->flags())) {
      // see if arena size record present
      MemPointerRecord* next_p = (MemPointerRecordEx*)mItr.peek_next();
      if (MemPointerRecord::is_arena_size_record(next_p->flags())) {
        assert(next_p->is_size_record_of_arena(mptr), "arena records do not match");
        size = next_p->size();
        _arena_data[index].inc(size);
        used_arena_size += size;
        mItr.next();
      }
    }
    mptr = (MemPointerRecordEx*)mItr.next();
  }

  // substract used arena size to get size of arena chunk in free list
  index = flag2index(mtChunk);
  _malloc_data[index].reduce(used_arena_size);
  // we really don't know how many chunks in free list, so just set to
  // 0
  _malloc_data[index].overwrite_counter(0);

  return true;
}

// baseline mmap'd memory records, generate overall summary and summaries by
// memory types
bool MemBaseline::baseline_vm_summary(const MemPointerArray* vm_records) {
  MemPointerArrayIteratorImpl vItr((MemPointerArray*)vm_records);
  VMMemRegion* vptr = (VMMemRegion*)vItr.current();
  int index;
  while (vptr != NULL) {
    index = flag2index(FLAGS_TO_MEMORY_TYPE(vptr->flags()));

    // we use the number of thread stack to count threads
    if (IS_MEMORY_TYPE(vptr->flags(), mtThreadStack)) {
      _number_of_threads ++;
    }
    _total_vm_reserved += vptr->reserved_size();
    _total_vm_committed += vptr->committed_size();
    _vm_data[index].inc(vptr->reserved_size(), vptr->committed_size());
    vptr = (VMMemRegion*)vItr.next();
  }
  return true;
}

// baseline malloc'd memory by callsites, but only the callsites with memory allocation
// over 1KB are stored.
bool MemBaseline::baseline_malloc_details(const MemPointerArray* malloc_records) {
  assert(MemTracker::track_callsite(), "detail tracking is off");

  MemPointerArrayIteratorImpl mItr((MemPointerArray*)malloc_records);
  MemPointerRecordEx* mptr = (MemPointerRecordEx*)mItr.current();
  MallocCallsitePointer mp;

  if (_malloc_cs == NULL) {
    _malloc_cs = new (std::nothrow) MemPointerArrayImpl<MallocCallsitePointer>(64);
    // out of native memory
    if (_malloc_cs == NULL) {
      return false;
    }
  } else {
    _malloc_cs->clear();
  }

  // baseline memory that is totaled over 1 KB
  while (mptr != NULL) {
    if (!MemPointerRecord::is_arena_size_record(mptr->flags())) {
      // skip thread stacks
      if (!IS_MEMORY_TYPE(mptr->flags(), mtThreadStack)) {
        if (mp.addr() != mptr->pc()) {
          if ((mp.amount()/K) > 0) {
            if (!_malloc_cs->append(&mp)) {
              return false;
            }
          }
          mp = MallocCallsitePointer(mptr->pc());
        }
        mp.inc(mptr->size());
      }
    }
    mptr = (MemPointerRecordEx*)mItr.next();
  }

  if (mp.addr() != 0 && (mp.amount()/K) > 0) {
    if (!_malloc_cs->append(&mp)) {
      return false;
    }
  }
  return true;
}

// baseline mmap'd memory by callsites
bool MemBaseline::baseline_vm_details(const MemPointerArray* vm_records) {
  assert(MemTracker::track_callsite(), "detail tracking is off");

  VMCallsitePointer vp;
  MemPointerArrayIteratorImpl vItr((MemPointerArray*)vm_records);
  VMMemRegionEx* vptr = (VMMemRegionEx*)vItr.current();

  if (_vm_cs == NULL) {
    _vm_cs = new (std::nothrow) MemPointerArrayImpl<VMCallsitePointer>(64);
    if (_vm_cs == NULL) {
      return false;
    }
  } else {
    _vm_cs->clear();
  }

  while (vptr != NULL) {
    if (vp.addr() != vptr->pc()) {
      if (!_vm_cs->append(&vp)) {
        return false;
      }
      vp = VMCallsitePointer(vptr->pc());
    }
    vp.inc(vptr->size(), vptr->committed_size());
    vptr = (VMMemRegionEx*)vItr.next();
  }
  if (vp.addr() != 0) {
    if (!_vm_cs->append(&vp)) {
      return false;
    }
  }
  return true;
}

// baseline a snapshot. If summary_only = false, memory usages aggregated by
// callsites are also baselined.
bool MemBaseline::baseline(MemSnapshot& snapshot, bool summary_only) {
  MutexLockerEx snapshot_locker(snapshot._lock, true);
  reset();
  _baselined = baseline_malloc_summary(snapshot._alloc_ptrs) &&
               baseline_vm_summary(snapshot._vm_ptrs);
  _number_of_classes = SystemDictionary::number_of_classes();

  if (!summary_only && MemTracker::track_callsite() && _baselined) {
    ((MemPointerArray*)snapshot._alloc_ptrs)->sort((FN_SORT)malloc_sort_by_pc);
    ((MemPointerArray*)snapshot._vm_ptrs)->sort((FN_SORT)vm_sort_by_pc);
    _baselined =  baseline_malloc_details(snapshot._alloc_ptrs) &&
      baseline_vm_details(snapshot._vm_ptrs);
    ((MemPointerArray*)snapshot._alloc_ptrs)->sort((FN_SORT)malloc_sort_by_addr);
    ((MemPointerArray*)snapshot._vm_ptrs)->sort((FN_SORT)vm_sort_by_addr);
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
  assert(false, "no type");
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

// sort snapshot mmap'd records in callsite pc order
int MemBaseline::vm_sort_by_pc(const void* p1, const void* p2) {
  assert(MemTracker::track_callsite(),"Just check");
  const VMMemRegionEx* mp1 = (const VMMemRegionEx*)p1;
  const VMMemRegionEx* mp2 = (const VMMemRegionEx*)p2;
  return UNSIGNED_COMPARE(mp1->pc(), mp2->pc());
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
  assert(delta != 0, "dup pointer");
  return delta;
}

// sort snapshot mmap'd records in memory block address order
int MemBaseline::vm_sort_by_addr(const void* p1, const void* p2) {
  assert(MemTracker::is_on(), "Just check");
  const VMMemRegion* mp1 = (const VMMemRegion*)p1;
  const VMMemRegion* mp2 = (const VMMemRegion*)p2;
  int delta = UNSIGNED_COMPARE(mp1->addr(), mp2->addr());
  assert(delta != 0, "dup pointer");
  return delta;
}
