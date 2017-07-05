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

#ifndef SHARE_VM_SERVICES_MEM_PTR_HPP
#define SHARE_VM_SERVICES_MEM_PTR_HPP

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"

/*
 * global sequence generator that generates sequence numbers to serialize
 * memory records.
 */
class SequenceGenerator : AllStatic {
 public:
  static jint next();

  // peek last sequence number
  static jint peek() {
    return _seq_number;
  }

  // reset sequence number
  static void reset() {
    assert(SafepointSynchronize::is_at_safepoint(), "Safepoint required");
    _seq_number = 1;
    DEBUG_ONLY(_generation ++;)
  };

  DEBUG_ONLY(static unsigned long current_generation() { return (unsigned long)_generation; })
  NOT_PRODUCT(static jint max_seq_num() { return _max_seq_number; })

 private:
  static volatile jint _seq_number;
  NOT_PRODUCT(static jint _max_seq_number; )
  DEBUG_ONLY(static volatile unsigned long _generation; )
};

/*
 * followings are the classes that are used to hold memory activity records in different stages.
 *   MemPointer
 *     |--------MemPointerRecord
 *                     |
 *                     |----MemPointerRecordEx
 *                     |           |
 *                     |           |-------SeqMemPointerRecordEx
 *                     |
 *                     |----SeqMemPointerRecord
 *                     |
 *                     |----VMMemRegion
 *                               |
 *                               |-----VMMemRegionEx
 *
 *
 *  prefix 'Seq' - sequenced, the record contains a sequence number
 *  surfix 'Ex'  - extension, the record contains a caller's pc
 *
 *  per-thread recorder : SeqMemPointerRecord(Ex)
 *  snapshot staging    : SeqMemPointerRecord(Ex)
 *  snapshot            : MemPointerRecord(Ex) and VMMemRegion(Ex)
 *
 */

/*
 * class that wraps an address to a memory block,
 * the memory pointer either points to a malloc'd
 * memory block, or a mmap'd memory block
 */
class MemPointer : public _ValueObj {
 public:
  MemPointer(): _addr(0) { }
  MemPointer(address addr): _addr(addr) { }

  MemPointer(const MemPointer& copy_from) {
    _addr = copy_from.addr();
  }

  inline address addr() const {
    return _addr;
  }

  inline operator address() const {
    return addr();
  }

  inline bool operator == (const MemPointer& other) const {
    return addr() == other.addr();
  }

  inline MemPointer& operator = (const MemPointer& other) {
    _addr = other.addr();
    return *this;
  }

 protected:
  inline void set_addr(address addr) { _addr = addr; }

 protected:
  // memory address
  address    _addr;
};

/* MemPointerRecord records an activityand associated
 * attributes on a memory block.
 */
class MemPointerRecord : public MemPointer {
 private:
  MEMFLAGS       _flags;
  size_t         _size;

public:
  /* extension of MemoryType enum
   * see share/vm/memory/allocation.hpp for details.
   *
   * The tag values are associated to sorting orders, so be
   * careful if changes are needed.
   * The allocation records should be sorted ahead of tagging
   * records, which in turn ahead of deallocation records
   */
  enum MemPointerTags {
    tag_alloc            = 0x0001, // malloc or reserve record
    tag_commit           = 0x0002, // commit record
    tag_type             = 0x0003, // tag virtual memory to a memory type
    tag_uncommit         = 0x0004, // uncommit record
    tag_release          = 0x0005, // free or release record
    tag_size             = 0x0006, // arena size
    tag_masks            = 0x0007, // all tag bits
    vmBit                = 0x0008
  };

  /* helper functions to interpret the tagging flags */

  inline static bool is_allocation_record(MEMFLAGS flags) {
    return (flags & tag_masks) == tag_alloc;
  }

  inline static bool is_deallocation_record(MEMFLAGS flags) {
    return (flags & tag_masks) == tag_release;
  }

  inline static bool is_arena_record(MEMFLAGS flags) {
    return (flags & (otArena | tag_size)) == otArena;
  }

  inline static bool is_arena_size_record(MEMFLAGS flags) {
    return (flags & (otArena | tag_size)) == (otArena | tag_size);
  }

  inline static bool is_virtual_memory_record(MEMFLAGS flags) {
    return (flags & vmBit) != 0;
  }

  inline static bool is_virtual_memory_reserve_record(MEMFLAGS flags) {
    return (flags & 0x0F) == (tag_alloc | vmBit);
  }

  inline static bool is_virtual_memory_commit_record(MEMFLAGS flags) {
    return (flags & 0x0F) == (tag_commit | vmBit);
  }

  inline static bool is_virtual_memory_uncommit_record(MEMFLAGS flags) {
    return (flags & 0x0F) == (tag_uncommit | vmBit);
  }

  inline static bool is_virtual_memory_release_record(MEMFLAGS flags) {
    return (flags & 0x0F) == (tag_release | vmBit);
  }

  inline static bool is_virtual_memory_type_record(MEMFLAGS flags) {
    return (flags & 0x0F) == (tag_type | vmBit);
  }

  /* tagging flags */
  inline static MEMFLAGS malloc_tag()                 { return tag_alloc;   }
  inline static MEMFLAGS free_tag()                   { return tag_release; }
  inline static MEMFLAGS arena_size_tag()             { return tag_size | otArena; }
  inline static MEMFLAGS virtual_memory_tag()         { return vmBit; }
  inline static MEMFLAGS virtual_memory_reserve_tag() { return (tag_alloc | vmBit); }
  inline static MEMFLAGS virtual_memory_commit_tag()  { return (tag_commit | vmBit); }
  inline static MEMFLAGS virtual_memory_uncommit_tag(){ return (tag_uncommit | vmBit); }
  inline static MEMFLAGS virtual_memory_release_tag() { return (tag_release | vmBit); }
  inline static MEMFLAGS virtual_memory_type_tag()    { return (tag_type | vmBit); }

 public:
  MemPointerRecord(): _size(0), _flags(mtNone) { }

  MemPointerRecord(address addr, MEMFLAGS memflags, size_t size = 0):
      MemPointer(addr), _flags(memflags), _size(size) { }

  MemPointerRecord(const MemPointerRecord& copy_from):
    MemPointer(copy_from), _flags(copy_from.flags()),
    _size(copy_from.size()) {
  }

  /* MemPointerRecord is not sequenced, it always return
   * 0 to indicate non-sequenced
   */
  virtual jint seq() const               { return 0; }

  inline size_t   size()  const          { return _size; }
  inline void set_size(size_t size)      { _size = size; }

  inline MEMFLAGS flags() const          { return _flags; }
  inline void set_flags(MEMFLAGS flags)  { _flags = flags; }

  MemPointerRecord& operator= (const MemPointerRecord& ptr) {
    MemPointer::operator=(ptr);
    _flags = ptr.flags();
#ifdef ASSERT
    if (IS_ARENA_OBJ(_flags)) {
      assert(!is_vm_pointer(), "wrong flags");
      assert((_flags & ot_masks) == otArena, "wrong flags");
    }
#endif
    _size = ptr.size();
    return *this;
  }

  // if the pointer represents a malloc-ed memory address
  inline bool is_malloced_pointer() const {
    return !is_vm_pointer();
  }

  // if the pointer represents a virtual memory address
  inline bool is_vm_pointer() const {
    return is_virtual_memory_record(_flags);
  }

  // if this record records a 'malloc' or virtual memory
  // 'reserve' call
  inline bool is_allocation_record() const {
    return is_allocation_record(_flags);
  }

  // if this record records a size information of an arena
  inline bool is_arena_size_record() const {
    return is_arena_size_record(_flags);
  }

  // if this pointer represents an address to an arena object
  inline bool is_arena_record() const {
    return is_arena_record(_flags);
  }

  // if this record represents a size information of specific arena
  inline bool is_size_record_of_arena(const MemPointerRecord* arena_rc) {
    assert(is_arena_size_record(), "not size record");
    assert(arena_rc->is_arena_record(), "not arena record");
    return (arena_rc->addr() + sizeof(void*)) == addr();
  }

  // if this record records a 'free' or virtual memory 'free' call
  inline bool is_deallocation_record() const {
    return is_deallocation_record(_flags);
  }

  // if this record records a virtual memory 'commit' call
  inline bool is_commit_record() const {
    return is_virtual_memory_commit_record(_flags);
  }

  // if this record records a virtual memory 'uncommit' call
  inline bool is_uncommit_record() const {
    return is_virtual_memory_uncommit_record(_flags);
  }

  // if this record is a tagging record of a virtual memory block
  inline bool is_type_tagging_record() const {
    return is_virtual_memory_type_record(_flags);
  }
};

// MemPointerRecordEx also records callsite pc, from where
// the memory block is allocated
class MemPointerRecordEx : public MemPointerRecord {
 private:
  address      _pc;  // callsite pc

 public:
  MemPointerRecordEx(): _pc(0) { }

  MemPointerRecordEx(address addr, MEMFLAGS memflags, size_t size = 0, address pc = 0):
    MemPointerRecord(addr, memflags, size), _pc(pc) {}

  MemPointerRecordEx(const MemPointerRecordEx& copy_from):
    MemPointerRecord(copy_from), _pc(copy_from.pc()) {}

  inline address pc() const { return _pc; }

  void init(const MemPointerRecordEx* mpe) {
    MemPointerRecord::operator=(*mpe);
    _pc = mpe->pc();
  }

  void init(const MemPointerRecord* mp) {
    MemPointerRecord::operator=(*mp);
    _pc = 0;
  }
};

// a virtual memory region
class VMMemRegion : public MemPointerRecord {
 private:
  // committed size
  size_t       _committed_size;

public:
  VMMemRegion(): _committed_size(0) { }

  void init(const MemPointerRecord* mp) {
    assert(mp->is_vm_pointer(), "not virtual memory pointer");
    _addr = mp->addr();
    if (mp->is_commit_record() || mp->is_uncommit_record()) {
      _committed_size = mp->size();
      set_size(_committed_size);
    } else {
      set_size(mp->size());
      _committed_size = 0;
    }
    set_flags(mp->flags());
  }

  VMMemRegion& operator=(const VMMemRegion& other) {
    MemPointerRecord::operator=(other);
    _committed_size = other.committed_size();
    return *this;
  }

  inline bool is_reserve_record() const {
    return is_virtual_memory_reserve_record(flags());
  }

  inline bool is_release_record() const {
    return is_virtual_memory_release_record(flags());
  }

  // resize reserved VM range
  inline void set_reserved_size(size_t new_size) {
    assert(new_size >= committed_size(), "resize");
    set_size(new_size);
  }

  inline void commit(size_t size) {
    _committed_size += size;
  }

  inline void uncommit(size_t size) {
    if (_committed_size >= size) {
      _committed_size -= size;
    } else {
      _committed_size = 0;
    }
  }

  /*
   * if this virtual memory range covers whole range of
   * the other VMMemRegion
   */
  bool contains(const VMMemRegion* mr) const;

  /* base address of this virtual memory range */
  inline address base() const {
    return addr();
  }

  /* tag this virtual memory range to the specified memory type */
  inline void tag(MEMFLAGS f) {
    set_flags(flags() | (f & mt_masks));
  }

  // release part of memory range
  inline void partial_release(address add, size_t sz) {
    assert(add >= addr() && add < addr() + size(), "not valid address");
    // for now, it can partially release from the both ends,
    // but not in the middle
    assert(add == addr() || (add + sz) == (addr() + size()),
      "release in the middle");
    if (add == addr()) {
      set_addr(add + sz);
      set_size(size() - sz);
    } else {
      set_size(size() - sz);
    }
  }

  // the committed size of the virtual memory block
  inline size_t committed_size() const {
    return _committed_size;
  }

  // the reserved size of the virtual memory block
  inline size_t reserved_size() const {
    return size();
  }
};

class VMMemRegionEx : public VMMemRegion {
 private:
  jint   _seq;  // sequence number

 public:
  VMMemRegionEx(): _pc(0) { }

  void init(const MemPointerRecordEx* mpe) {
    VMMemRegion::init(mpe);
    _pc = mpe->pc();
  }

  void init(const MemPointerRecord* mpe) {
    VMMemRegion::init(mpe);
    _pc = 0;
  }

  VMMemRegionEx& operator=(const VMMemRegionEx& other) {
    VMMemRegion::operator=(other);
    _pc = other.pc();
    return *this;
  }

  inline address pc() const { return _pc; }
 private:
  address   _pc;
};

/*
 * Sequenced memory record
 */
class SeqMemPointerRecord : public MemPointerRecord {
 private:
   jint _seq;  // sequence number

 public:
  SeqMemPointerRecord(): _seq(0){ }

  SeqMemPointerRecord(address addr, MEMFLAGS flags, size_t size)
    : MemPointerRecord(addr, flags, size) {
    _seq = SequenceGenerator::next();
  }

  SeqMemPointerRecord(const SeqMemPointerRecord& copy_from)
    : MemPointerRecord(copy_from) {
    _seq = copy_from.seq();
  }

  SeqMemPointerRecord& operator= (const SeqMemPointerRecord& ptr) {
    MemPointerRecord::operator=(ptr);
    _seq = ptr.seq();
    return *this;
  }

  inline jint seq() const {
    return _seq;
  }
};



class SeqMemPointerRecordEx : public MemPointerRecordEx {
 private:
  jint    _seq;  // sequence number

 public:
  SeqMemPointerRecordEx(): _seq(0) { }

  SeqMemPointerRecordEx(address addr, MEMFLAGS flags, size_t size,
    address pc): MemPointerRecordEx(addr, flags, size, pc) {
    _seq = SequenceGenerator::next();
  }

  SeqMemPointerRecordEx(const SeqMemPointerRecordEx& copy_from)
    : MemPointerRecordEx(copy_from) {
    _seq = copy_from.seq();
  }

  SeqMemPointerRecordEx& operator= (const SeqMemPointerRecordEx& ptr) {
    MemPointerRecordEx::operator=(ptr);
    _seq = ptr.seq();
    return *this;
  }

  inline jint seq() const {
    return _seq;
  }
};

#endif // SHARE_VM_SERVICES_MEM_PTR_HPP
