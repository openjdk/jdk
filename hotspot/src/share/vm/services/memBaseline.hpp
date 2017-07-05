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

#ifndef SHARE_VM_SERVICES_MEM_BASELINE_HPP
#define SHARE_VM_SERVICES_MEM_BASELINE_HPP

#include "memory/allocation.hpp"
#include "runtime/mutex.hpp"
#include "services/memPtr.hpp"
#include "services/memSnapshot.hpp"

// compare unsigned number
#define UNSIGNED_COMPARE(a, b)  ((a > b) ? 1 : ((a == b) ? 0 : -1))

/*
 * MallocCallsitePointer and VMCallsitePointer are used
 * to baseline memory blocks with their callsite information.
 * They are only available when detail tracking is turned
 * on.
 */

/* baselined malloc record aggregated by callsite */
class MallocCallsitePointer : public MemPointer {
 private:
  size_t    _count;   // number of malloc invocation from this callsite
  size_t    _amount;  // total amount of memory malloc-ed from this callsite

 public:
  MallocCallsitePointer() {
    _count = 0;
    _amount = 0;
  }

  MallocCallsitePointer(address pc) : MemPointer(pc) {
    _count = 0;
    _amount = 0;
  }

  MallocCallsitePointer& operator=(const MallocCallsitePointer& p) {
    MemPointer::operator=(p);
    _count = p.count();
    _amount = p.amount();
    return *this;
  }

  inline void inc(size_t size) {
    _count ++;
    _amount += size;
  };

  inline size_t count() const {
    return _count;
  }

  inline size_t amount() const {
    return _amount;
  }
};

// baselined virtual memory record aggregated by callsite
class VMCallsitePointer : public MemPointer {
 private:
  size_t     _count;              // number of invocation from this callsite
  size_t     _reserved_amount;    // total reserved amount
  size_t     _committed_amount;   // total committed amount

 public:
  VMCallsitePointer() {
    _count = 0;
    _reserved_amount = 0;
    _committed_amount = 0;
  }

  VMCallsitePointer(address pc) : MemPointer(pc) {
    _count = 0;
    _reserved_amount = 0;
    _committed_amount = 0;
  }

  VMCallsitePointer& operator=(const VMCallsitePointer& p) {
    MemPointer::operator=(p);
    _count = p.count();
    _reserved_amount = p.reserved_amount();
    _committed_amount = p.committed_amount();
    return *this;
  }

  inline void inc(size_t reserved, size_t committed) {
    _count ++;
    _reserved_amount += reserved;
    _committed_amount += committed;
  }

  inline size_t count() const {
    return _count;
  }

  inline size_t reserved_amount() const {
    return _reserved_amount;
  }

  inline size_t committed_amount() const {
    return _committed_amount;
  }
};

// maps a memory type flag to readable name
typedef struct _memType2Name {
  MEMFLAGS     _flag;
  const char*  _name;
} MemType2Name;


// This class aggregates malloc'd records by memory type
class MallocMem VALUE_OBJ_CLASS_SPEC {
 private:
  MEMFLAGS       _type;

  size_t         _count;
  size_t         _amount;

 public:
  MallocMem() {
    _type = mtNone;
    _count = 0;
    _amount = 0;
  }

  MallocMem(MEMFLAGS flags) {
    assert(HAS_VALID_MEMORY_TYPE(flags), "no type");
    _type = FLAGS_TO_MEMORY_TYPE(flags);
    _count = 0;
    _amount = 0;
  }

  inline void set_type(MEMFLAGS flag) {
    _type = flag;
  }

  inline void clear() {
    _count = 0;
    _amount = 0;
    _type = mtNone;
  }

  MallocMem& operator=(const MallocMem& m) {
    assert(_type == m.type(), "different type");
    _count = m.count();
    _amount = m.amount();
    return *this;
  }

  inline void inc(size_t amt) {
    _amount += amt;
    _count ++;
  }

  inline void reduce(size_t amt) {
    assert(_amount >= amt, "Just check");
    _amount -= amt;
  }

  inline void overwrite_counter(size_t count) {
    _count = count;
  }

  inline MEMFLAGS type() const {
    return _type;
  }

  inline bool is_type(MEMFLAGS flags) const {
    return FLAGS_TO_MEMORY_TYPE(flags) == _type;
  }

  inline size_t count() const {
    return _count;
  }

  inline size_t amount() const {
    return _amount;
  }
};

// This class records live arena's memory usage
class ArenaMem : public MallocMem {
 public:
  ArenaMem(MEMFLAGS typeflag): MallocMem(typeflag) {
  }
  ArenaMem() { }
};

// This class aggregates virtual memory by its memory type
class VMMem VALUE_OBJ_CLASS_SPEC {
 private:
  MEMFLAGS       _type;

  size_t         _count;
  size_t         _reserved_amount;
  size_t         _committed_amount;

 public:
  VMMem() {
    _type = mtNone;
    _count = 0;
    _reserved_amount = 0;
    _committed_amount = 0;
  }

  VMMem(MEMFLAGS flags) {
    assert(HAS_VALID_MEMORY_TYPE(flags), "no type");
    _type = FLAGS_TO_MEMORY_TYPE(flags);
    _count = 0;
    _reserved_amount = 0;
    _committed_amount = 0;
  }

  inline void clear() {
    _type = mtNone;
    _count = 0;
    _reserved_amount = 0;
    _committed_amount = 0;
  }

  inline void set_type(MEMFLAGS flags) {
    _type = FLAGS_TO_MEMORY_TYPE(flags);
  }

  VMMem& operator=(const VMMem& m) {
    assert(_type == m.type(), "different type");

    _count = m.count();
    _reserved_amount = m.reserved_amount();
    _committed_amount = m.committed_amount();
    return *this;
  }


  inline MEMFLAGS type() const {
    return _type;
  }

  inline bool is_type(MEMFLAGS flags) const {
    return FLAGS_TO_MEMORY_TYPE(flags) == _type;
  }

  inline void inc(size_t reserved_amt, size_t committed_amt) {
    _reserved_amount += reserved_amt;
    _committed_amount += committed_amt;
    _count ++;
  }

  inline size_t count() const {
    return _count;
  }

  inline size_t reserved_amount() const {
    return _reserved_amount;
  }

  inline size_t committed_amount() const {
    return _committed_amount;
  }
};



#define NUMBER_OF_MEMORY_TYPE    (mt_number_of_types + 1)

class BaselineReporter;
class BaselineComparisonReporter;

/*
 * This class baselines current memory snapshot.
 * A memory baseline summarizes memory usage by memory type,
 * aggregates memory usage by callsites when detail tracking
 * is on.
 */
class MemBaseline VALUE_OBJ_CLASS_SPEC {
  friend class BaselineReporter;
  friend class BaselineComparisonReporter;

 private:
  // overall summaries
  size_t        _total_malloced;
  size_t        _total_vm_reserved;
  size_t        _total_vm_committed;
  size_t        _number_of_classes;
  size_t        _number_of_threads;

  // if it has properly baselined
  bool          _baselined;

  // we categorize memory into three categories within the memory type
  MallocMem     _malloc_data[NUMBER_OF_MEMORY_TYPE];
  VMMem         _vm_data[NUMBER_OF_MEMORY_TYPE];
  ArenaMem      _arena_data[NUMBER_OF_MEMORY_TYPE];

  // memory records that aggregate memory usage by callsites.
  // only available when detail tracking is on.
  MemPointerArray*  _malloc_cs;
  MemPointerArray*  _vm_cs;
  // virtual memory map
  MemPointerArray*  _vm_map;

 private:
  static MemType2Name  MemType2NameMap[NUMBER_OF_MEMORY_TYPE];

 private:
  // should not use copy constructor
  MemBaseline(MemBaseline& copy) { ShouldNotReachHere(); }

  // check and block at a safepoint
  static inline void check_safepoint(JavaThread* thr);

 public:
  // create a memory baseline
  MemBaseline();

  ~MemBaseline();

  inline bool baselined() const {
    return _baselined;
  }

  MemBaseline& operator=(const MemBaseline& other);

  // reset the baseline for reuse
  void clear();

  // baseline the snapshot
  bool baseline(MemSnapshot& snapshot, bool summary_only = true);

  bool baseline(const MemPointerArray* malloc_records,
                const MemPointerArray* vm_records,
                bool summary_only = true);

  // total malloc'd memory of specified memory type
  inline size_t malloc_amount(MEMFLAGS flag) const {
    return _malloc_data[flag2index(flag)].amount();
  }
  // number of malloc'd memory blocks of specified memory type
  inline size_t malloc_count(MEMFLAGS flag) const {
    return _malloc_data[flag2index(flag)].count();
  }
  // total memory used by arenas of specified memory type
  inline size_t arena_amount(MEMFLAGS flag) const {
    return _arena_data[flag2index(flag)].amount();
  }
  // number of arenas of specified memory type
  inline size_t arena_count(MEMFLAGS flag) const {
    return _arena_data[flag2index(flag)].count();
  }
  // total reserved memory of specified memory type
  inline size_t reserved_amount(MEMFLAGS flag) const {
    return _vm_data[flag2index(flag)].reserved_amount();
  }
  // total committed memory of specified memory type
  inline size_t committed_amount(MEMFLAGS flag) const {
    return _vm_data[flag2index(flag)].committed_amount();
  }
  // total memory (malloc'd + mmap'd + arena) of specified
  // memory type
  inline size_t total_amount(MEMFLAGS flag) const {
    int index = flag2index(flag);
    return _malloc_data[index].amount() +
           _vm_data[index].reserved_amount() +
           _arena_data[index].amount();
  }

  /* overall summaries */

  // total malloc'd memory in snapshot
  inline size_t total_malloc_amount() const {
    return _total_malloced;
  }
  // total mmap'd memory in snapshot
  inline size_t total_reserved_amount() const {
    return _total_vm_reserved;
  }
  // total committed memory in snapshot
  inline size_t total_committed_amount() const {
    return _total_vm_committed;
  }
  // number of loaded classes
  inline size_t number_of_classes() const {
    return _number_of_classes;
  }
  // number of running threads
  inline size_t number_of_threads() const {
    return _number_of_threads;
  }
  // lookup human readable name of a memory type
  static const char* type2name(MEMFLAGS type);

 private:
  // convert memory flag to the index to mapping table
  int         flag2index(MEMFLAGS flag) const;

  // reset baseline values
  void reset();

  // summarize the records in global snapshot
  bool baseline_malloc_summary(const MemPointerArray* malloc_records);
  bool baseline_vm_summary(const MemPointerArray* vm_records);
  bool baseline_malloc_details(const MemPointerArray* malloc_records);
  bool baseline_vm_details(const MemPointerArray* vm_records);

  // print a line of malloc'd memory aggregated by callsite
  void print_malloc_callsite(outputStream* st, address pc, size_t size,
    size_t count, int diff_amt, int diff_count) const;
  // print a line of mmap'd memory aggregated by callsite
  void print_vm_callsite(outputStream* st, address pc, size_t rsz,
    size_t csz, int diff_rsz, int diff_csz) const;

  // sorting functions for raw records
  static int malloc_sort_by_pc(const void* p1, const void* p2);
  static int malloc_sort_by_addr(const void* p1, const void* p2);

 private:
  // sorting functions for baselined records
  static int bl_malloc_sort_by_size(const void* p1, const void* p2);
  static int bl_vm_sort_by_size(const void* p1, const void* p2);
  static int bl_malloc_sort_by_pc(const void* p1, const void* p2);
  static int bl_vm_sort_by_pc(const void* p1, const void* p2);
};


#endif // SHARE_VM_SERVICES_MEM_BASELINE_HPP
