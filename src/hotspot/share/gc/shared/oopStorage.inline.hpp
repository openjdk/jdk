/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_OOPSTORAGE_INLINE_HPP
#define SHARE_GC_SHARED_OOPSTORAGE_INLINE_HPP

#include "gc/shared/oopStorage.hpp"
#include "metaprogramming/conditional.hpp"
#include "metaprogramming/isConst.hpp"
#include "oops/oop.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class OopStorage::Block /* No base class, to avoid messing up alignment. */ {
  // _data must be the first non-static data member, for alignment.
  oop _data[BitsPerWord];
  static const unsigned _data_pos = 0; // Position of _data.

  volatile uintx _allocated_bitmask; // One bit per _data element.
  const OopStorage* _owner;
  void* _memory;              // Unaligned storage containing block.
  BlockEntry _active_entry;
  BlockEntry _allocate_entry;
  Block* volatile _deferred_updates_next;
  volatile uintx _release_refcount;

  Block(const OopStorage* owner, void* memory);
  ~Block();

  void check_index(unsigned index) const;
  unsigned get_index(const oop* ptr) const;

  template<typename F, typename BlockPtr>
  static bool iterate_impl(F f, BlockPtr b);

  // Noncopyable.
  Block(const Block&);
  Block& operator=(const Block&);

public:
  static const BlockEntry& get_active_entry(const Block& block);
  static const BlockEntry& get_allocate_entry(const Block& block);

  static size_t allocation_size();
  static size_t allocation_alignment_shift();

  oop* get_pointer(unsigned index);
  const oop* get_pointer(unsigned index) const;

  uintx bitmask_for_index(unsigned index) const;
  uintx bitmask_for_entry(const oop* ptr) const;

  // Allocation bitmask accessors are racy.
  bool is_full() const;
  bool is_empty() const;
  uintx allocated_bitmask() const;
  bool is_deletable() const;

  Block* deferred_updates_next() const;
  void set_deferred_updates_next(Block* new_next);

  bool contains(const oop* ptr) const;

  // Returns NULL if ptr is not in a block or not allocated in that block.
  static Block* block_for_ptr(const OopStorage* owner, const oop* ptr);

  oop* allocate();
  static Block* new_block(const OopStorage* owner);
  static void delete_block(const Block& block);

  void release_entries(uintx releasing, Block* volatile* deferred_list);

  template<typename F> bool iterate(F f);
  template<typename F> bool iterate(F f) const;
}; // class Block

inline OopStorage::Block* OopStorage::BlockList::head() {
  return const_cast<Block*>(_head);
}

inline const OopStorage::Block* OopStorage::BlockList::chead() const {
  return _head;
}

inline const OopStorage::Block* OopStorage::BlockList::ctail() const {
  return _tail;
}

inline OopStorage::Block* OopStorage::BlockList::prev(Block& block) {
  return const_cast<Block*>(_get_entry(block)._prev);
}

inline OopStorage::Block* OopStorage::BlockList::next(Block& block) {
  return const_cast<Block*>(_get_entry(block)._next);
}

inline const OopStorage::Block* OopStorage::BlockList::prev(const Block& block) const {
  return _get_entry(block)._prev;
}

inline const OopStorage::Block* OopStorage::BlockList::next(const Block& block) const {
  return _get_entry(block)._next;
}

template<typename Closure>
class OopStorage::OopFn {
public:
  explicit OopFn(Closure* cl) : _cl(cl) {}

  template<typename OopPtr>     // [const] oop*
  bool operator()(OopPtr ptr) const {
    _cl->do_oop(ptr);
    return true;
  }

private:
  Closure* _cl;
};

template<typename Closure>
inline OopStorage::OopFn<Closure> OopStorage::oop_fn(Closure* cl) {
  return OopFn<Closure>(cl);
}

template<typename IsAlive, typename F>
class OopStorage::IfAliveFn {
public:
  IfAliveFn(IsAlive* is_alive, F f) : _is_alive(is_alive), _f(f) {}

  bool operator()(oop* ptr) const {
    bool result = true;
    oop v = *ptr;
    if (v != NULL) {
      if (_is_alive->do_object_b(v)) {
        result = _f(ptr);
      } else {
        *ptr = NULL;            // Clear dead value.
      }
    }
    return result;
  }

private:
  IsAlive* _is_alive;
  F _f;
};

template<typename IsAlive, typename F>
inline OopStorage::IfAliveFn<IsAlive, F> OopStorage::if_alive_fn(IsAlive* is_alive, F f) {
  return IfAliveFn<IsAlive, F>(is_alive, f);
}

template<typename F>
class OopStorage::SkipNullFn {
public:
  SkipNullFn(F f) : _f(f) {}

  template<typename OopPtr>     // [const] oop*
  bool operator()(OopPtr ptr) const {
    return (*ptr != NULL) ? _f(ptr) : true;
  }

private:
  F _f;
};

template<typename F>
inline OopStorage::SkipNullFn<F> OopStorage::skip_null_fn(F f) {
  return SkipNullFn<F>(f);
}

// Inline Block accesses for use in iteration inner loop.

inline void OopStorage::Block::check_index(unsigned index) const {
  assert(index < ARRAY_SIZE(_data), "Index out of bounds: %u", index);
}

inline oop* OopStorage::Block::get_pointer(unsigned index) {
  check_index(index);
  return &_data[index];
}

inline const oop* OopStorage::Block::get_pointer(unsigned index) const {
  check_index(index);
  return &_data[index];
}

inline uintx OopStorage::Block::allocated_bitmask() const {
  return _allocated_bitmask;
}

inline uintx OopStorage::Block::bitmask_for_index(unsigned index) const {
  check_index(index);
  return uintx(1) << index;
}

// Provide const or non-const iteration, depending on whether BlockPtr
// is const Block* or Block*, respectively.
template<typename F, typename BlockPtr> // BlockPtr := [const] Block*
inline bool OopStorage::Block::iterate_impl(F f, BlockPtr block) {
  uintx bitmask = block->allocated_bitmask();
  while (bitmask != 0) {
    unsigned index = count_trailing_zeros(bitmask);
    bitmask ^= block->bitmask_for_index(index);
    if (!f(block->get_pointer(index))) {
      return false;
    }
  }
  return true;
}

template<typename F>
inline bool OopStorage::Block::iterate(F f) {
  return iterate_impl(f, this);
}

template<typename F>
inline bool OopStorage::Block::iterate(F f) const {
  return iterate_impl(f, this);
}

//////////////////////////////////////////////////////////////////////////////
// Support for serial iteration, always at a safepoint.

// Provide const or non-const iteration, depending on whether Storage is
// const OopStorage* or OopStorage*, respectively.
template<typename F, typename Storage> // Storage := [const] OopStorage
inline bool OopStorage::iterate_impl(F f, Storage* storage) {
  assert_at_safepoint();
  // Propagate const/non-const iteration to the block layer, by using
  // const or non-const blocks as corresponding to Storage.
  typedef typename Conditional<IsConst<Storage>::value, const Block*, Block*>::type BlockPtr;
  for (BlockPtr block = storage->_active_head;
       block != NULL;
       block = storage->_active_list.next(*block)) {
    if (!block->iterate(f)) {
      return false;
    }
  }
  return true;
}

template<typename F>
inline bool OopStorage::iterate_safepoint(F f) {
  return iterate_impl(f, this);
}

template<typename F>
inline bool OopStorage::iterate_safepoint(F f) const {
  return iterate_impl(f, this);
}

template<typename Closure>
inline void OopStorage::oops_do(Closure* cl) {
  iterate_safepoint(oop_fn(cl));
}

template<typename Closure>
inline void OopStorage::oops_do(Closure* cl) const {
  iterate_safepoint(oop_fn(cl));
}

template<typename Closure>
inline void OopStorage::weak_oops_do(Closure* cl) {
  iterate_safepoint(skip_null_fn(oop_fn(cl)));
}

template<typename IsAliveClosure, typename Closure>
inline void OopStorage::weak_oops_do(IsAliveClosure* is_alive, Closure* cl) {
  iterate_safepoint(if_alive_fn(is_alive, oop_fn(cl)));
}

#endif // include guard
