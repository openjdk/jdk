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
#include "oops/oop.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/singleWriterSynchronizer.hpp"

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
  OopStorage(const char* name, Mutex* allocation_mutex, Mutex* active_mutex);
  ~OopStorage();

  // These count and usage accessors are racy unless at a safepoint.

  // The number of allocated and not yet released entries.
  size_t allocation_count() const;

  // The number of blocks of entries.  Useful for sizing parallel iteration.
  size_t block_count() const;

  // Total number of blocks * memory allocation per block, plus
  // bookkeeping overhead, including this storage object.
  size_t total_memory_usage() const;

  enum EntryStatus {
    INVALID_ENTRY,
    UNALLOCATED_ENTRY,
    ALLOCATED_ENTRY
  };

  // Locks _allocation_mutex.
  // precondition: ptr != NULL.
  EntryStatus allocation_status(const oop* ptr) const;

  // Allocates and returns a new entry.  Returns NULL if memory allocation
  // failed.  Locks _allocation_mutex.
  // postcondition: *result == NULL.
  oop* allocate();

  // Deallocates ptr.  No locking.
  // precondition: ptr is a valid allocated entry.
  // precondition: *ptr == NULL.
  void release(const oop* ptr);

  // Releases all the ptrs.  Possibly faster than individual calls to
  // release(oop*).  Best if ptrs is sorted by address.  No locking.
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
  template<typename F> inline bool iterate_safepoint(F f);
  template<typename F> inline bool iterate_safepoint(F f) const;

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

  template<typename Closure> inline void oops_do(Closure* closure);
  template<typename Closure> inline void oops_do(Closure* closure) const;
  template<typename Closure> inline void weak_oops_do(Closure* closure);

  template<typename IsAliveClosure, typename Closure>
  inline void weak_oops_do(IsAliveClosure* is_alive, Closure* closure);

  // Parallel iteration is for the exclusive use of the GC.
  // Other clients must use serial iteration.
  template<bool concurrent, bool is_const> class ParState;

  // Block cleanup functions are for the exclusive use of the GC.
  // Both stop deleting if there is an in-progress concurrent iteration.
  // Concurrent deletion locks both the _allocation_mutex and the _active_mutex.
  void delete_empty_blocks_safepoint();
  void delete_empty_blocks_concurrent();

  // Debugging and logging support.
  const char* name() const;
  void print_on(outputStream* st) const PRODUCT_RETURN;

  // Provides access to storage internals, for unit testing.
  // Declare, but not define, the public class OopStorage::TestAccess.
  // That class is defined as part of the unit-test. It "exports" the needed
  // private types by providing public typedefs for them.
  class TestAccess;

  // xlC on AIX can't compile test_oopStorage.cpp with following private
  // classes. C++03 introduced access for nested classes with DR45, but xlC
  // version 12 rejects it.
NOT_AIX( private: )
  class Block;                  // Fixed-size array of oops, plus bookkeeping.
  class ActiveArray;            // Array of Blocks, plus bookkeeping.
  class AllocationListEntry;    // Provides AllocationList links in a Block.

  // Doubly-linked list of Blocks.
  class AllocationList {
    const Block* _head;
    const Block* _tail;

    // Noncopyable.
    AllocationList(const AllocationList&);
    AllocationList& operator=(const AllocationList&);

  public:
    AllocationList();
    ~AllocationList();

    Block* head();
    Block* tail();
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

private:
  const char* _name;
  ActiveArray* _active_array;
  AllocationList _allocation_list;
  Block* volatile _deferred_updates;

  Mutex* _allocation_mutex;
  Mutex* _active_mutex;

  // Volatile for racy unlocked accesses.
  volatile size_t _allocation_count;

  // Protection for _active_array.
  mutable SingleWriterSynchronizer _protect_active;

  // mutable because this gets set even for const iteration.
  mutable bool _concurrent_iteration_active;

  Block* find_block_or_null(const oop* ptr) const;
  void delete_empty_block(const Block& block);
  bool reduce_deferred_updates();

  // Managing _active_array.
  bool expand_active_array();
  void replace_active_array(ActiveArray* new_array);
  ActiveArray* obtain_active_array() const;
  void relinquish_block_array(ActiveArray* array) const;
  class WithActiveArray;        // RAII helper for active array access.

  template<typename F, typename Storage>
  static bool iterate_impl(F f, Storage* storage);

  // Implementation support for parallel iteration
  class BasicParState;

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
};

#endif // include guard
