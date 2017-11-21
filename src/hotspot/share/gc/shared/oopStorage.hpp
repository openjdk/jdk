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

#ifndef SHARE_GC_SHARED_OOPSTORAGE_HPP
#define SHARE_GC_SHARED_OOPSTORAGE_HPP

#include "memory/allocation.hpp"
#include "metaprogramming/conditional.hpp"
#include "metaprogramming/isConst.hpp"
#include "oops/oop.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class Mutex;
class outputStream;

// OopStorage supports management of off-heap references to objects allocated
// in the Java heap.  An OopStorage object provides a set of Java object
// references (oop values), which clients refer to via oop* handles to the
// associated OopStorage entries.  Clients allocate entries to create a
// (possibly weak) reference to a Java object, use that reference, and release
// the reference when no longer needed.
//
// The garbage collector must know about all OopStorage objects and their
// reference strength.  OopStorage provides the garbage collector with support
// for iteration over all the allocated entries.
//
// There are several categories of interaction with an OopStorage object.
//
// (1) allocation and release of entries, by the mutator or the VM.
// (2) iteration by the garbage collector, possibly concurrent with mutator.
// (3) iteration by other, non-GC, tools (only at safepoints).
// (4) cleanup of unused internal storage, possibly concurrent with mutator.
//
// A goal of OopStorage is to make these interactions thread-safe, while
// minimizing potential lock contention issues within and between these
// categories.  In particular, support for concurrent iteration by the garbage
// collector, under certain restrictions, is required.  Further, it must not
// block nor be blocked by other operations for long periods.
//
// Internally, OopStorage is a set of Block objects, from which entries are
// allocated and released.  A block contains an oop[] and a bitmask indicating
// which entries are in use (have been allocated and not yet released).  New
// blocks are constructed and added to the storage object when an entry
// allocation request is made and there are no blocks with unused entries.
// Blocks may be removed and deleted when empty.
//
// There are two important (and somewhat intertwined) protocols governing
// concurrent access to a storage object.  These are the Concurrent Iteration
// Protocol and the Allocation Protocol.  See the ParState class for a
// discussion of concurrent iteration and the management of thread
// interactions for this protocol.  Similarly, see the allocate() function for
// a discussion of allocation.

class OopStorage : public CHeapObj<mtGC> {
public:
  OopStorage(const char* name, Mutex* allocate_mutex, Mutex* active_mutex);
  ~OopStorage();

  // These count and usage accessors are racy unless at a safepoint.

  // The number of allocated and not yet released entries.
  size_t allocation_count() const;

  // The number of blocks of entries.  Useful for sizing parallel iteration.
  size_t block_count() const;

  // The number of blocks with no allocated entries.  Useful for sizing
  // parallel iteration and scheduling block deletion.
  size_t empty_block_count() const;

  // Total number of blocks * memory allocation per block, plus
  // bookkeeping overhead, including this storage object.
  size_t total_memory_usage() const;

  enum EntryStatus {
    INVALID_ENTRY,
    UNALLOCATED_ENTRY,
    ALLOCATED_ENTRY
  };

  // Locks _allocate_mutex.
  EntryStatus allocation_status(const oop* ptr) const;

  // Allocates and returns a new entry.  Returns NULL if memory allocation
  // failed.  Locks _allocate_mutex.
  // postcondition: *result == NULL.
  oop* allocate();

  // Deallocates ptr, after setting its value to NULL. Locks _allocate_mutex.
  // precondition: ptr is a valid allocated entry.
  // precondition: *ptr == NULL.
  void release(const oop* ptr);

  // Releases all the ptrs.  Possibly faster than individual calls to
  // release(oop*).  Best if ptrs is sorted by address.  Locks
  // _allocate_mutex.
  // precondition: All elements of ptrs are valid allocated entries.
  // precondition: *ptrs[i] == NULL, for i in [0,size).
  void release(const oop* const* ptrs, size_t size);

  // Applies f to each allocated entry's location.  f must be a function or
  // function object.  Assume p is either a const oop* or an oop*, depending
  // on whether the associated storage is const or non-const, respectively.
  // Then f(p) must be a valid expression.  The result of invoking f(p) must
  // be implicitly convertible to bool.  Iteration terminates and returns
  // false if any invocation of f returns false.  Otherwise, the result of
  // iteration is true.
  // precondition: at safepoint.
  template<typename F> bool iterate_safepoint(F f);
  template<typename F> bool iterate_safepoint(F f) const;

  // oops_do and weak_oops_do are wrappers around iterate_safepoint, providing
  // an adaptation layer allowing the use of existing is-alive closures and
  // OopClosures.  Assume p is either const oop* or oop*, depending on whether
  // the associated storage is const or non-const, respectively.  Then
  //
  // - closure->do_oop(p) must be a valid expression whose value is ignored.
  //
  // - is_alive->do_object_b(*p) must be a valid expression whose value is
  // convertible to bool.
  //
  // For weak_oops_do, if *p == NULL then neither is_alive nor closure will be
  // invoked for p.  If is_alive->do_object_b(*p) is false, then closure will
  // not be invoked on p, and *p will be set to NULL.

  template<typename Closure> void oops_do(Closure* closure);
  template<typename Closure> void oops_do(Closure* closure) const;
  template<typename Closure> void weak_oops_do(Closure* closure);

  template<typename IsAliveClosure, typename Closure>
  void weak_oops_do(IsAliveClosure* is_alive, Closure* closure);

#if INCLUDE_ALL_GCS
  // Parallel iteration is for the exclusive use of the GC.
  // Other clients must use serial iteration.
  template<bool concurrent, bool is_const> class ParState;
#endif // INCLUDE_ALL_GCS

  // Block cleanup functions are for the exclusive use of the GC.
  // Both stop deleting if there is an in-progress concurrent iteration.
  // Concurrent deletion locks both the allocate_mutex and the active_mutex.
  void delete_empty_blocks_safepoint(size_t retain = 1);
  void delete_empty_blocks_concurrent(size_t retain = 1);

  // Debugging and logging support.
  const char* name() const;
  void print_on(outputStream* st) const PRODUCT_RETURN;

  // Provides access to storage internals, for unit testing.
  class TestAccess;

private:
  class Block;
  class BlockList;

  class BlockEntry VALUE_OBJ_CLASS_SPEC {
    friend class BlockList;

    // Members are mutable, and we deal exclusively with pointers to
    // const, to make const blocks easier to use; a block being const
    // doesn't prevent modifying its list state.
    mutable const Block* _prev;
    mutable const Block* _next;

    // Noncopyable.
    BlockEntry(const BlockEntry&);
    BlockEntry& operator=(const BlockEntry&);

  public:
    BlockEntry();
    ~BlockEntry();
  };

  class BlockList VALUE_OBJ_CLASS_SPEC {
    const Block* _head;
    const Block* _tail;
    const BlockEntry& (*_get_entry)(const Block& block);

    // Noncopyable.
    BlockList(const BlockList&);
    BlockList& operator=(const BlockList&);

  public:
    BlockList(const BlockEntry& (*get_entry)(const Block& block));
    ~BlockList();

    Block* head();
    const Block* chead() const;
    const Block* ctail() const;

    Block* prev(Block& block);
    Block* next(Block& block);

    const Block* prev(const Block& block) const;
    const Block* next(const Block& block) const;

    void push_front(const Block& block);
    void push_back(const Block& block);
    void unlink(const Block& block);
  };

  class Block /* No base class, to avoid messing up alignment requirements */ {
    // _data must be the first non-static data member, for alignment.
    oop _data[BitsPerWord];
    static const unsigned _data_pos = 0; // Position of _data.

    volatile uintx _allocated_bitmask; // One bit per _data element.
    const OopStorage* _owner;
    void* _memory;              // Unaligned storage containing block.
    BlockEntry _active_entry;
    BlockEntry _allocate_entry;

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
    uintx cmpxchg_allocated_bitmask(uintx new_value, uintx compare_value);

    bool contains(const oop* ptr) const;

    // Returns NULL if ptr is not in a block or not allocated in that block.
    static Block* block_for_ptr(const OopStorage* owner, const oop* ptr);

    oop* allocate();
    static Block* new_block(const OopStorage* owner);
    static void delete_block(const Block& block);

    template<typename F> bool iterate(F f);
    template<typename F> bool iterate(F f) const;
  }; // class Block

  const char* _name;
  BlockList _active_list;
  BlockList _allocate_list;
  Block* volatile _active_head;

  Mutex* _allocate_mutex;
  Mutex* _active_mutex;

  // Counts are volatile for racy unlocked accesses.
  volatile size_t _allocation_count;
  volatile size_t _block_count;
  volatile size_t _empty_block_count;
  // mutable because this gets set even for const iteration.
  mutable bool _concurrent_iteration_active;

  Block* find_block_or_null(const oop* ptr) const;
  bool is_valid_block_locked_or_safepoint(const Block* block) const;
  EntryStatus allocation_status_validating_block(const Block* block, const oop* ptr) const;
  void check_release(const Block* block, const oop* ptr) const NOT_DEBUG_RETURN;
  void release_from_block(Block& block, uintx release_bitmask);
  void delete_empty_block(const Block& block);

  static void assert_at_safepoint() NOT_DEBUG_RETURN;

  template<typename F, typename Storage>
  static bool iterate_impl(F f, Storage* storage);

#if INCLUDE_ALL_GCS
  // Implementation support for parallel iteration
  class BasicParState;
#endif // INCLUDE_ALL_GCS

  // Wrapper for OopClosure-style function, so it can be used with
  // iterate.  Assume p is of type oop*.  Then cl->do_oop(p) must be a
  // valid expression whose value may be ignored.
  template<typename Closure> class OopFn;
  template<typename Closure> static OopFn<Closure> oop_fn(Closure* cl);

  // Wrapper for BoolObjectClosure + iteration handler pair, so they
  // can be used with iterate.
  template<typename IsAlive, typename F> class IfAliveFn;
  template<typename IsAlive, typename F>
  static IfAliveFn<IsAlive, F> if_alive_fn(IsAlive* is_alive, F f);

  // Wrapper for iteration handler, automatically skipping NULL entries.
  template<typename F> class SkipNullFn;
  template<typename F> static SkipNullFn<F> skip_null_fn(F f);

  // Wrapper for iteration handler; ignore handler result and return true.
  template<typename F> class AlwaysTrueFn;
};

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
class OopStorage::OopFn VALUE_OBJ_CLASS_SPEC {
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
class OopStorage::IfAliveFn VALUE_OBJ_CLASS_SPEC {
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
class OopStorage::SkipNullFn VALUE_OBJ_CLASS_SPEC {
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

template<typename F>
class OopStorage::AlwaysTrueFn VALUE_OBJ_CLASS_SPEC {
  F _f;

public:
  AlwaysTrueFn(F f) : _f(f) {}

  template<typename OopPtr>     // [const] oop*
  bool operator()(OopPtr ptr) const { _f(ptr); return true; }
};

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

#if INCLUDE_ALL_GCS

//////////////////////////////////////////////////////////////////////////////
// Support for parallel and optionally concurrent state iteration.
//
// Parallel iteration is for the exclusive use of the GC.  Other iteration
// clients must use serial iteration.
//
// Concurrent Iteration
//
// Iteration involves the _active_list, which contains all of the blocks owned
// by a storage object.  This is a doubly-linked list, linked through
// dedicated fields in the blocks.
//
// At most one concurrent ParState can exist at a time for a given storage
// object.
//
// A concurrent ParState sets the associated storage's
// _concurrent_iteration_active flag true when the state is constructed, and
// sets it false when the state is destroyed.  These assignments are made with
// _active_mutex locked.  Meanwhile, empty block deletion is not done while
// _concurrent_iteration_active is true.  The flag check and the dependent
// removal of a block from the _active_list is performed with _active_mutex
// locked.  This prevents concurrent iteration and empty block deletion from
// interfering with with each other.
//
// Both allocate() and delete_empty_blocks_concurrent() lock the
// _allocate_mutex while performing their respective list manipulations,
// preventing them from interfering with each other.
//
// When allocate() creates a new block, it is added to the front of the
// _active_list.  Then _active_head is set to the new block.  When concurrent
// iteration is started (by a parallel worker thread calling the state's
// iterate() function), the current _active_head is used as the initial block
// for the iteration, with iteration proceeding down the list headed by that
// block.
//
// As a result, the list over which concurrent iteration operates is stable.
// However, once the iteration is started, later allocations may add blocks to
// the front of the list that won't be examined by the iteration.  And while
// the list is stable, concurrent allocate() and release() operations may
// change the set of allocated entries in a block at any time during the
// iteration.
//
// As a result, a concurrent iteration handler must accept that some
// allocations and releases that occur after the iteration started will not be
// seen by the iteration.  Further, some may overlap examination by the
// iteration.  To help with this, allocate() and release() have an invariant
// that an entry's value must be NULL when it is not in use.
//
// An in-progress delete_empty_blocks_concurrent() operation can contend with
// the start of a concurrent iteration over the _active_mutex.  Since both are
// under GC control, that potential contention can be eliminated by never
// scheduling both operations to run at the same time.
//
// ParState<concurrent, is_const>
//   concurrent must be true if iteration is concurrent with the
//   mutator, false if iteration is at a safepoint.
//
//   is_const must be true if the iteration is over a constant storage
//   object, false if the iteration may modify the storage object.
//
// ParState([const] OopStorage* storage)
//   Construct an object for managing an iteration over storage.  For a
//   concurrent ParState, empty block deletion for the associated storage
//   is inhibited for the life of the ParState.  There can be no more
//   than one live concurrent ParState at a time for a given storage object.
//
// template<typename F> void iterate(F f)
//   Repeatedly claims a block from the associated storage that has
//   not been processed by this iteration (possibly by other threads),
//   and applies f to each entry in the claimed block. Assume p is of
//   type const oop* or oop*, according to is_const. Then f(p) must be
//   a valid expression whose value is ignored.  Concurrent uses must
//   be prepared for an entry's value to change at any time, due to
//   mutator activity.
//
// template<typename Closure> void oops_do(Closure* cl)
//   Wrapper around iterate, providing an adaptation layer allowing
//   the use of OopClosures and similar objects for iteration.  Assume
//   p is of type const oop* or oop*, according to is_const.  Then
//   cl->do_oop(p) must be a valid expression whose value is ignored.
//   Concurrent uses must be prepared for the entry's value to change
//   at any time, due to mutator activity.
//
// Optional operations, provided only if !concurrent && !is_const.
// These are not provided when is_const, because the storage object
// may be modified by the iteration infrastructure, even if the
// provided closure doesn't modify the storage object.  These are not
// provided when concurrent because any pre-filtering behavior by the
// iteration infrastructure is inappropriate for concurrent iteration;
// modifications of the storage by the mutator could result in the
// pre-filtering being applied (successfully or not) to objects that
// are unrelated to what the closure finds in the entry.
//
// template<typename Closure> void weak_oops_do(Closure* cl)
// template<typename IsAliveClosure, typename Closure>
// void weak_oops_do(IsAliveClosure* is_alive, Closure* cl)
//   Wrappers around iterate, providing an adaptation layer allowing
//   the use of is-alive closures and OopClosures for iteration.
//   Assume p is of type oop*.  Then
//
//   - cl->do_oop(p) must be a valid expression whose value is ignored.
//
//   - is_alive->do_object_b(*p) must be a valid expression whose value
//   is convertible to bool.
//
//   If *p == NULL then neither is_alive nor cl will be invoked for p.
//   If is_alive->do_object_b(*p) is false, then cl will not be
//   invoked on p.

class OopStorage::BasicParState VALUE_OBJ_CLASS_SPEC {
public:
  BasicParState(OopStorage* storage, bool concurrent);
  ~BasicParState();

  template<bool is_const, typename F> void iterate(F f) {
    // Wrap f in ATF so we can use Block::iterate.
    AlwaysTrueFn<F> atf_f(f);
    ensure_iteration_started();
    typename Conditional<is_const, const Block*, Block*>::type block;
    while ((block = claim_next_block()) != NULL) {
      block->iterate(atf_f);
    }
  }

private:
  OopStorage* _storage;
  void* volatile _next_block;
  bool _concurrent;

  // Noncopyable.
  BasicParState(const BasicParState&);
  BasicParState& operator=(const BasicParState&);

  void update_iteration_state(bool value);
  void ensure_iteration_started();
  Block* claim_next_block();
};

template<bool concurrent, bool is_const>
class OopStorage::ParState VALUE_OBJ_CLASS_SPEC {
  BasicParState _basic_state;

public:
  ParState(const OopStorage* storage) :
    // For simplicity, always recorded as non-const.
    _basic_state(const_cast<OopStorage*>(storage), concurrent)
  {}

  template<typename F>
  void iterate(F f) {
    _basic_state.template iterate<is_const>(f);
  }

  template<typename Closure>
  void oops_do(Closure* cl) {
    this->iterate(oop_fn(cl));
  }
};

template<>
class OopStorage::ParState<false, false> VALUE_OBJ_CLASS_SPEC {
  BasicParState _basic_state;

public:
  ParState(OopStorage* storage) :
    _basic_state(storage, false)
  {}

  template<typename F>
  void iterate(F f) {
    _basic_state.template iterate<false>(f);
  }

  template<typename Closure>
  void oops_do(Closure* cl) {
    this->iterate(oop_fn(cl));
  }

  template<typename Closure>
  void weak_oops_do(Closure* cl) {
    this->iterate(skip_null_fn(oop_fn(cl)));
  }

  template<typename IsAliveClosure, typename Closure>
  void weak_oops_do(IsAliveClosure* is_alive, Closure* cl) {
    this->iterate(if_alive_fn(is_alive, oop_fn(cl)));
  }
};

#endif // INCLUDE_ALL_GCS

#endif // include guard
