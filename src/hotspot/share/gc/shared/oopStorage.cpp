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

#include "precompiled.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageParState.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.hpp"
#include "utilities/align.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/spinYield.hpp"

OopStorage::AllocateEntry::AllocateEntry() : _prev(NULL), _next(NULL) {}

OopStorage::AllocateEntry::~AllocateEntry() {
  assert(_prev == NULL, "deleting attached block");
  assert(_next == NULL, "deleting attached block");
}

OopStorage::AllocateList::AllocateList() : _head(NULL), _tail(NULL) {}

OopStorage::AllocateList::~AllocateList() {
  // ~OopStorage() empties its lists before destroying them.
  assert(_head == NULL, "deleting non-empty block list");
  assert(_tail == NULL, "deleting non-empty block list");
}

void OopStorage::AllocateList::push_front(const Block& block) {
  const Block* old = _head;
  if (old == NULL) {
    assert(_tail == NULL, "invariant");
    _head = _tail = &block;
  } else {
    block.allocate_entry()._next = old;
    old->allocate_entry()._prev = &block;
    _head = &block;
  }
}

void OopStorage::AllocateList::push_back(const Block& block) {
  const Block* old = _tail;
  if (old == NULL) {
    assert(_head == NULL, "invariant");
    _head = _tail = &block;
  } else {
    old->allocate_entry()._next = &block;
    block.allocate_entry()._prev = old;
    _tail = &block;
  }
}

void OopStorage::AllocateList::unlink(const Block& block) {
  const AllocateEntry& block_entry = block.allocate_entry();
  const Block* prev_blk = block_entry._prev;
  const Block* next_blk = block_entry._next;
  block_entry._prev = NULL;
  block_entry._next = NULL;
  if ((prev_blk == NULL) && (next_blk == NULL)) {
    assert(_head == &block, "invariant");
    assert(_tail == &block, "invariant");
    _head = _tail = NULL;
  } else if (prev_blk == NULL) {
    assert(_head == &block, "invariant");
    next_blk->allocate_entry()._prev = NULL;
    _head = next_blk;
  } else if (next_blk == NULL) {
    assert(_tail == &block, "invariant");
    prev_blk->allocate_entry()._next = NULL;
    _tail = prev_blk;
  } else {
    next_blk->allocate_entry()._prev = prev_blk;
    prev_blk->allocate_entry()._next = next_blk;
  }
}

OopStorage::ActiveArray::ActiveArray(size_t size) :
  _size(size),
  _block_count(0),
  _refcount(0)
{}

OopStorage::ActiveArray::~ActiveArray() {
  assert(_refcount == 0, "precondition");
}

OopStorage::ActiveArray* OopStorage::ActiveArray::create(size_t size, AllocFailType alloc_fail) {
  size_t size_in_bytes = blocks_offset() + sizeof(Block*) * size;
  void* mem = NEW_C_HEAP_ARRAY3(char, size_in_bytes, mtGC, CURRENT_PC, alloc_fail);
  if (mem == NULL) return NULL;
  return new (mem) ActiveArray(size);
}

void OopStorage::ActiveArray::destroy(ActiveArray* ba) {
  ba->~ActiveArray();
  FREE_C_HEAP_ARRAY(char, ba);
}

size_t OopStorage::ActiveArray::size() const {
  return _size;
}

size_t OopStorage::ActiveArray::block_count() const {
  return _block_count;
}

size_t OopStorage::ActiveArray::block_count_acquire() const {
  return OrderAccess::load_acquire(&_block_count);
}

void OopStorage::ActiveArray::increment_refcount() const {
  int new_value = Atomic::add(1, &_refcount);
  assert(new_value >= 1, "negative refcount %d", new_value - 1);
}

bool OopStorage::ActiveArray::decrement_refcount() const {
  int new_value = Atomic::sub(1, &_refcount);
  assert(new_value >= 0, "negative refcount %d", new_value);
  return new_value == 0;
}

bool OopStorage::ActiveArray::push(Block* block) {
  size_t index = _block_count;
  if (index < _size) {
    block->set_active_index(index);
    *block_ptr(index) = block;
    // Use a release_store to ensure all the setup is complete before
    // making the block visible.
    OrderAccess::release_store(&_block_count, index + 1);
    return true;
  } else {
    return false;
  }
}

void OopStorage::ActiveArray::remove(Block* block) {
  assert(_block_count > 0, "array is empty");
  size_t index = block->active_index();
  assert(*block_ptr(index) == block, "block not present");
  size_t last_index = _block_count - 1;
  Block* last_block = *block_ptr(last_index);
  last_block->set_active_index(index);
  *block_ptr(index) = last_block;
  _block_count = last_index;
}

void OopStorage::ActiveArray::copy_from(const ActiveArray* from) {
  assert(_block_count == 0, "array must be empty");
  size_t count = from->_block_count;
  assert(count <= _size, "precondition");
  Block* const* from_ptr = from->block_ptr(0);
  Block** to_ptr = block_ptr(0);
  for (size_t i = 0; i < count; ++i) {
    Block* block = *from_ptr++;
    assert(block->active_index() == i, "invariant");
    *to_ptr++ = block;
  }
  _block_count = count;
}

// Blocks start with an array of BitsPerWord oop entries.  That array
// is divided into conceptual BytesPerWord sections of BitsPerByte
// entries.  Blocks are allocated aligned on section boundaries, for
// the convenience of mapping from an entry to the containing block;
// see block_for_ptr().  Aligning on section boundary rather than on
// the full _data wastes a lot less space, but makes for a bit more
// work in block_for_ptr().

const unsigned section_size = BitsPerByte;
const unsigned section_count = BytesPerWord;
const unsigned block_alignment = sizeof(oop) * section_size;

OopStorage::Block::Block(const OopStorage* owner, void* memory) :
  _data(),
  _allocated_bitmask(0),
  _owner(owner),
  _memory(memory),
  _active_index(0),
  _allocate_entry(),
  _deferred_updates_next(NULL),
  _release_refcount(0)
{
  STATIC_ASSERT(_data_pos == 0);
  STATIC_ASSERT(section_size * section_count == ARRAY_SIZE(_data));
  assert(offset_of(Block, _data) == _data_pos, "invariant");
  assert(owner != NULL, "NULL owner");
  assert(is_aligned(this, block_alignment), "misaligned block");
}

OopStorage::Block::~Block() {
  assert(_release_refcount == 0, "deleting block while releasing");
  assert(_deferred_updates_next == NULL, "deleting block with deferred update");
  // Clear fields used by block_for_ptr and entry validation, which
  // might help catch bugs.  Volatile to prevent dead-store elimination.
  const_cast<uintx volatile&>(_allocated_bitmask) = 0;
  const_cast<OopStorage* volatile&>(_owner) = NULL;
}

size_t OopStorage::Block::allocation_size() {
  // _data must be first member, so aligning Block aligns _data.
  STATIC_ASSERT(_data_pos == 0);
  return sizeof(Block) + block_alignment - sizeof(void*);
}

size_t OopStorage::Block::allocation_alignment_shift() {
  return exact_log2(block_alignment);
}

inline bool is_full_bitmask(uintx bitmask) { return ~bitmask == 0; }
inline bool is_empty_bitmask(uintx bitmask) { return bitmask == 0; }

bool OopStorage::Block::is_full() const {
  return is_full_bitmask(allocated_bitmask());
}

bool OopStorage::Block::is_empty() const {
  return is_empty_bitmask(allocated_bitmask());
}

uintx OopStorage::Block::bitmask_for_entry(const oop* ptr) const {
  return bitmask_for_index(get_index(ptr));
}

// A block is deletable if
// (1) It is empty.
// (2) There is not a release() operation currently operating on it.
// (3) It is not in the deferred updates list.
// The order of tests is important for proper interaction between release()
// and concurrent deletion.
bool OopStorage::Block::is_deletable() const {
  return (OrderAccess::load_acquire(&_allocated_bitmask) == 0) &&
         (OrderAccess::load_acquire(&_release_refcount) == 0) &&
         (OrderAccess::load_acquire(&_deferred_updates_next) == NULL);
}

OopStorage::Block* OopStorage::Block::deferred_updates_next() const {
  return _deferred_updates_next;
}

void OopStorage::Block::set_deferred_updates_next(Block* block) {
  _deferred_updates_next = block;
}

bool OopStorage::Block::contains(const oop* ptr) const {
  const oop* base = get_pointer(0);
  return (base <= ptr) && (ptr < (base + ARRAY_SIZE(_data)));
}

size_t OopStorage::Block::active_index() const {
  return _active_index;
}

void OopStorage::Block::set_active_index(size_t index) {
  _active_index = index;
}

size_t OopStorage::Block::active_index_safe(const Block* block) {
  STATIC_ASSERT(sizeof(intptr_t) == sizeof(block->_active_index));
  assert(CanUseSafeFetchN(), "precondition");
  return SafeFetchN((intptr_t*)&block->_active_index, 0);
}

unsigned OopStorage::Block::get_index(const oop* ptr) const {
  assert(contains(ptr), PTR_FORMAT " not in block " PTR_FORMAT, p2i(ptr), p2i(this));
  return static_cast<unsigned>(ptr - get_pointer(0));
}

oop* OopStorage::Block::allocate() {
  // Use CAS loop because release may change bitmask outside of lock.
  uintx allocated = allocated_bitmask();
  while (true) {
    assert(!is_full_bitmask(allocated), "attempt to allocate from full block");
    unsigned index = count_trailing_zeros(~allocated);
    uintx new_value = allocated | bitmask_for_index(index);
    uintx fetched = Atomic::cmpxchg(new_value, &_allocated_bitmask, allocated);
    if (fetched == allocated) {
      return get_pointer(index); // CAS succeeded; return entry for index.
    }
    allocated = fetched;       // CAS failed; retry with latest value.
  }
}

OopStorage::Block* OopStorage::Block::new_block(const OopStorage* owner) {
  // _data must be first member: aligning block => aligning _data.
  STATIC_ASSERT(_data_pos == 0);
  size_t size_needed = allocation_size();
  void* memory = NEW_C_HEAP_ARRAY_RETURN_NULL(char, size_needed, mtGC);
  if (memory == NULL) {
    return NULL;
  }
  void* block_mem = align_up(memory, block_alignment);
  assert(sizeof(Block) + pointer_delta(block_mem, memory, 1) <= size_needed,
         "allocated insufficient space for aligned block");
  return ::new (block_mem) Block(owner, memory);
}

void OopStorage::Block::delete_block(const Block& block) {
  void* memory = block._memory;
  block.Block::~Block();
  FREE_C_HEAP_ARRAY(char, memory);
}

// This can return a false positive if ptr is not contained by some
// block.  For some uses, it is a precondition that ptr is valid,
// e.g. contained in some block in owner's _active_array.  Other uses
// require additional validation of the result.
OopStorage::Block*
OopStorage::Block::block_for_ptr(const OopStorage* owner, const oop* ptr) {
  assert(CanUseSafeFetchN(), "precondition");
  STATIC_ASSERT(_data_pos == 0);
  // Const-ness of ptr is not related to const-ness of containing block.
  // Blocks are allocated section-aligned, so get the containing section.
  oop* section_start = align_down(const_cast<oop*>(ptr), block_alignment);
  // Start with a guess that the containing section is the last section,
  // so the block starts section_count-1 sections earlier.
  oop* section = section_start - (section_size * (section_count - 1));
  // Walk up through the potential block start positions, looking for
  // the owner in the expected location.  If we're below the actual block
  // start position, the value at the owner position will be some oop
  // (possibly NULL), which can never match the owner.
  intptr_t owner_addr = reinterpret_cast<intptr_t>(owner);
  for (unsigned i = 0; i < section_count; ++i, section += section_size) {
    Block* candidate = reinterpret_cast<Block*>(section);
    intptr_t* candidate_owner_addr
      = reinterpret_cast<intptr_t*>(&candidate->_owner);
    if (SafeFetchN(candidate_owner_addr, 0) == owner_addr) {
      return candidate;
    }
  }
  return NULL;
}

//////////////////////////////////////////////////////////////////////////////
// Allocation
//
// Allocation involves the _allocate_list, which contains a subset of the
// blocks owned by a storage object.  This is a doubly-linked list, linked
// through dedicated fields in the blocks.  Full blocks are removed from this
// list, though they are still present in the _active_array.  Empty blocks are
// kept at the end of the _allocate_list, to make it easy for empty block
// deletion to find them.
//
// allocate(), and delete_empty_blocks_concurrent() lock the
// _allocate_mutex while performing any list and array modifications.
//
// allocate() and release() update a block's _allocated_bitmask using CAS
// loops.  This prevents loss of updates even though release() performs
// its updates without any locking.
//
// allocate() obtains the entry from the first block in the _allocate_list,
// and updates that block's _allocated_bitmask to indicate the entry is in
// use.  If this makes the block full (all entries in use), the block is
// removed from the _allocate_list so it won't be considered by future
// allocations until some entries in it are released.
//
// release() is performed lock-free. release() first looks up the block for
// the entry, using address alignment to find the enclosing block (thereby
// avoiding iteration over the _active_array).  Once the block has been
// determined, its _allocated_bitmask needs to be updated, and its position in
// the _allocate_list may need to be updated.  There are two cases:
//
// (a) If the block is neither full nor would become empty with the release of
// the entry, only its _allocated_bitmask needs to be updated.  But if the CAS
// update fails, the applicable case may change for the retry.
//
// (b) Otherwise, the _allocate_list also needs to be modified.  This requires
// locking the _allocate_mutex.  To keep the release() operation lock-free,
// rather than updating the _allocate_list itself, it instead performs a
// lock-free push of the block onto the _deferred_updates list.  Entries on
// that list are processed by allocate() and delete_empty_blocks_XXX(), while
// they already hold the necessary lock.  That processing makes the block's
// list state consistent with its current _allocated_bitmask.  The block is
// added to the _allocate_list if not already present and the bitmask is not
// full.  The block is moved to the end of the _allocated_list if the bitmask
// is empty, for ease of empty block deletion processing.

oop* OopStorage::allocate() {
  MutexLockerEx ml(_allocate_mutex, Mutex::_no_safepoint_check_flag);
  // Do some deferred update processing every time we allocate.
  // Continue processing deferred updates if _allocate_list is empty,
  // in the hope that we'll get a block from that, rather than
  // allocating a new block.
  while (reduce_deferred_updates() && (_allocate_list.head() == NULL)) {}

  // Use the first block in _allocate_list for the allocation.
  Block* block = _allocate_list.head();
  if (block == NULL) {
    // No available blocks; make a new one, and add to storage.
    {
      MutexUnlockerEx mul(_allocate_mutex, Mutex::_no_safepoint_check_flag);
      block = Block::new_block(this);
    }
    if (block == NULL) {
      while (_allocate_list.head() == NULL) {
        if (!reduce_deferred_updates()) {
          // Failed to make new block, no other thread made a block
          // available while the mutex was released, and didn't get
          // one from a deferred update either, so return failure.
          log_info(oopstorage, ref)("%s: failed block allocation", name());
          return NULL;
        }
      }
    } else {
      // Add new block to storage.
      log_info(oopstorage, blocks)("%s: new block " PTR_FORMAT, name(), p2i(block));

      // Add new block to the _active_array, growing if needed.
      if (!_active_array->push(block)) {
        if (expand_active_array()) {
          guarantee(_active_array->push(block), "push failed after expansion");
        } else {
          log_info(oopstorage, blocks)("%s: failed active array expand", name());
          Block::delete_block(*block);
          return NULL;
        }
      }
      // Add to end of _allocate_list.  The mutex release allowed
      // other threads to add blocks to the _allocate_list.  We prefer
      // to allocate from non-empty blocks, to allow empty blocks to
      // be deleted.
      _allocate_list.push_back(*block);
    }
    block = _allocate_list.head();
  }
  // Allocate from first block.
  assert(block != NULL, "invariant");
  assert(!block->is_full(), "invariant");
  if (block->is_empty()) {
    // Transitioning from empty to not empty.
    log_debug(oopstorage, blocks)("%s: block not empty " PTR_FORMAT, name(), p2i(block));
  }
  oop* result = block->allocate();
  assert(result != NULL, "allocation failed");
  assert(!block->is_empty(), "postcondition");
  Atomic::inc(&_allocation_count); // release updates outside lock.
  if (block->is_full()) {
    // Transitioning from not full to full.
    // Remove full blocks from consideration by future allocates.
    log_debug(oopstorage, blocks)("%s: block full " PTR_FORMAT, name(), p2i(block));
    _allocate_list.unlink(*block);
  }
  log_info(oopstorage, ref)("%s: allocated " PTR_FORMAT, name(), p2i(result));
  return result;
}

// Create a new, larger, active array with the same content as the
// current array, and then replace, relinquishing the old array.
// Return true if the array was successfully expanded, false to
// indicate allocation failure.
bool OopStorage::expand_active_array() {
  assert_lock_strong(_allocate_mutex);
  ActiveArray* old_array = _active_array;
  size_t new_size = 2 * old_array->size();
  log_info(oopstorage, blocks)("%s: expand active array " SIZE_FORMAT,
                               name(), new_size);
  ActiveArray* new_array = ActiveArray::create(new_size, AllocFailStrategy::RETURN_NULL);
  if (new_array == NULL) return false;
  new_array->copy_from(old_array);
  replace_active_array(new_array);
  relinquish_block_array(old_array);
  return true;
}

OopStorage::ProtectActive::ProtectActive() : _enter(0), _exit() {}

// Begin read-side critical section.
uint OopStorage::ProtectActive::read_enter() {
  return Atomic::add(2u, &_enter);
}

// End read-side critical section.
void OopStorage::ProtectActive::read_exit(uint enter_value) {
  Atomic::add(2u, &_exit[enter_value & 1]);
}

// Wait until all readers that entered the critical section before
// synchronization have exited that critical section.
void OopStorage::ProtectActive::write_synchronize() {
  SpinYield spinner;
  // Determine old and new exit counters, based on bit0 of the
  // on-entry _enter counter.
  uint value = OrderAccess::load_acquire(&_enter);
  volatile uint* new_ptr = &_exit[(value + 1) & 1];
  // Atomically change the in-use exit counter to the new counter, by
  // adding 1 to the _enter counter (flipping bit0 between 0 and 1)
  // and initializing the new exit counter to that enter value.  Note:
  // The new exit counter is not being used by read operations until
  // this change succeeds.
  uint old;
  do {
    old = value;
    *new_ptr = ++value;
    value = Atomic::cmpxchg(value, &_enter, old);
  } while (old != value);
  // Readers that entered the critical section before we changed the
  // selected exit counter will use the old exit counter.  Readers
  // entering after the change will use the new exit counter.  Wait
  // for all the critical sections started before the change to
  // complete, e.g. for the value of old_ptr to catch up with old.
  volatile uint* old_ptr = &_exit[old & 1];
  while (old != OrderAccess::load_acquire(old_ptr)) {
    spinner.wait();
  }
}

// Make new_array the _active_array.  Increments new_array's refcount
// to account for the new reference.  The assignment is atomic wrto
// obtain_active_array; once this function returns, it is safe for the
// caller to relinquish the old array.
void OopStorage::replace_active_array(ActiveArray* new_array) {
  // Caller has the old array that is the current value of _active_array.
  // Update new_array refcount to account for the new reference.
  new_array->increment_refcount();
  // Install new_array, ensuring its initialization is complete first.
  OrderAccess::release_store(&_active_array, new_array);
  // Wait for any readers that could read the old array from _active_array.
  _protect_active.write_synchronize();
  // All obtain critical sections that could see the old array have
  // completed, having incremented the refcount of the old array.  The
  // caller can now safely relinquish the old array.
}

// Atomically (wrto replace_active_array) get the active array and
// increment its refcount.  This provides safe access to the array,
// even if an allocate operation expands and replaces the value of
// _active_array.  The caller must relinquish the array when done
// using it.
OopStorage::ActiveArray* OopStorage::obtain_active_array() const {
  uint enter_value = _protect_active.read_enter();
  ActiveArray* result = OrderAccess::load_acquire(&_active_array);
  result->increment_refcount();
  _protect_active.read_exit(enter_value);
  return result;
}

// Decrement refcount of array and destroy if refcount is zero.
void OopStorage::relinquish_block_array(ActiveArray* array) const {
  if (array->decrement_refcount()) {
    assert(array != _active_array, "invariant");
    ActiveArray::destroy(array);
  }
}

class OopStorage::WithActiveArray : public StackObj {
  const OopStorage* _storage;
  ActiveArray* _active_array;

public:
  WithActiveArray(const OopStorage* storage) :
    _storage(storage),
    _active_array(storage->obtain_active_array())
  {}

  ~WithActiveArray() {
    _storage->relinquish_block_array(_active_array);
  }

  ActiveArray& active_array() const {
    return *_active_array;
  }
};

OopStorage::Block* OopStorage::find_block_or_null(const oop* ptr) const {
  assert(ptr != NULL, "precondition");
  return Block::block_for_ptr(this, ptr);
}

static void log_release_transitions(uintx releasing,
                                    uintx old_allocated,
                                    const OopStorage* owner,
                                    const void* block) {
  Log(oopstorage, blocks) log;
  LogStream ls(log.debug());
  if (is_full_bitmask(old_allocated)) {
    ls.print_cr("%s: block not full " PTR_FORMAT, owner->name(), p2i(block));
  }
  if (releasing == old_allocated) {
    ls.print_cr("%s: block empty " PTR_FORMAT, owner->name(), p2i(block));
  }
}

void OopStorage::Block::release_entries(uintx releasing, Block* volatile* deferred_list) {
  assert(releasing != 0, "preconditon");
  // Prevent empty block deletion when transitioning to empty.
  Atomic::inc(&_release_refcount);

  // Atomically update allocated bitmask.
  uintx old_allocated = _allocated_bitmask;
  while (true) {
    assert((releasing & ~old_allocated) == 0, "releasing unallocated entries");
    uintx new_value = old_allocated ^ releasing;
    uintx fetched = Atomic::cmpxchg(new_value, &_allocated_bitmask, old_allocated);
    if (fetched == old_allocated) break; // Successful update.
    old_allocated = fetched;             // Retry with updated bitmask.
  }

  // Now that the bitmask has been updated, if we have a state transition
  // (updated bitmask is empty or old bitmask was full), atomically push
  // this block onto the deferred updates list.  Some future call to
  // reduce_deferred_updates will make any needed changes related to this
  // block and _allocate_list.  This deferral avoids list updates and the
  // associated locking here.
  if ((releasing == old_allocated) || is_full_bitmask(old_allocated)) {
    // Log transitions.  Both transitions are possible in a single update.
    if (log_is_enabled(Debug, oopstorage, blocks)) {
      log_release_transitions(releasing, old_allocated, _owner, this);
    }
    // Attempt to claim responsibility for adding this block to the deferred
    // list, by setting the link to non-NULL by self-looping.  If this fails,
    // then someone else has made such a claim and the deferred update has not
    // yet been processed and will include our change, so we don't need to do
    // anything further.
    if (Atomic::replace_if_null(this, &_deferred_updates_next)) {
      // Successfully claimed.  Push, with self-loop for end-of-list.
      Block* head = *deferred_list;
      while (true) {
        _deferred_updates_next = (head == NULL) ? this : head;
        Block* fetched = Atomic::cmpxchg(this, deferred_list, head);
        if (fetched == head) break; // Successful update.
        head = fetched;             // Retry with updated head.
      }
      log_debug(oopstorage, blocks)("%s: deferred update " PTR_FORMAT,
                                    _owner->name(), p2i(this));
    }
  }
  // Release hold on empty block deletion.
  Atomic::dec(&_release_refcount);
}

// Process one available deferred update.  Returns true if one was processed.
bool OopStorage::reduce_deferred_updates() {
  assert_locked_or_safepoint(_allocate_mutex);
  // Atomically pop a block off the list, if any available.
  // No ABA issue because this is only called by one thread at a time.
  // The atomicity is wrto pushes by release().
  Block* block = OrderAccess::load_acquire(&_deferred_updates);
  while (true) {
    if (block == NULL) return false;
    // Try atomic pop of block from list.
    Block* tail = block->deferred_updates_next();
    if (block == tail) tail = NULL; // Handle self-loop end marker.
    Block* fetched = Atomic::cmpxchg(tail, &_deferred_updates, block);
    if (fetched == block) break; // Update successful.
    block = fetched;             // Retry with updated block.
  }
  block->set_deferred_updates_next(NULL); // Clear tail after updating head.
  // Ensure bitmask read after pop is complete, including clearing tail, for
  // ordering with release().  Without this, we may be processing a stale
  // bitmask state here while blocking a release() operation from recording
  // the deferred update needed for its bitmask change.
  OrderAccess::storeload();
  // Process popped block.
  uintx allocated = block->allocated_bitmask();

  // Make membership in list consistent with bitmask state.
  if ((_allocate_list.ctail() != NULL) &&
      ((_allocate_list.ctail() == block) ||
       (_allocate_list.next(*block) != NULL))) {
    // Block is in the allocate list.
    assert(!is_full_bitmask(allocated), "invariant");
  } else if (!is_full_bitmask(allocated)) {
    // Block is not in the allocate list, but now should be.
    _allocate_list.push_front(*block);
  } // Else block is full and not in list, which is correct.

  // Move empty block to end of list, for possible deletion.
  if (is_empty_bitmask(allocated)) {
    _allocate_list.unlink(*block);
    _allocate_list.push_back(*block);
  }

  log_debug(oopstorage, blocks)("%s: processed deferred update " PTR_FORMAT,
                                name(), p2i(block));
  return true;              // Processed one pending update.
}

inline void check_release_entry(const oop* entry) {
  assert(entry != NULL, "Releasing NULL");
  assert(*entry == NULL, "Releasing uncleared entry: " PTR_FORMAT, p2i(entry));
}

void OopStorage::release(const oop* ptr) {
  check_release_entry(ptr);
  Block* block = find_block_or_null(ptr);
  assert(block != NULL, "%s: invalid release " PTR_FORMAT, name(), p2i(ptr));
  log_info(oopstorage, ref)("%s: released " PTR_FORMAT, name(), p2i(ptr));
  block->release_entries(block->bitmask_for_entry(ptr), &_deferred_updates);
  Atomic::dec(&_allocation_count);
}

void OopStorage::release(const oop* const* ptrs, size_t size) {
  size_t i = 0;
  while (i < size) {
    check_release_entry(ptrs[i]);
    Block* block = find_block_or_null(ptrs[i]);
    assert(block != NULL, "%s: invalid release " PTR_FORMAT, name(), p2i(ptrs[i]));
    log_info(oopstorage, ref)("%s: released " PTR_FORMAT, name(), p2i(ptrs[i]));
    size_t count = 0;
    uintx releasing = 0;
    for ( ; i < size; ++i) {
      const oop* entry = ptrs[i];
      check_release_entry(entry);
      // If entry not in block, finish block and resume outer loop with entry.
      if (!block->contains(entry)) break;
      // Add entry to releasing bitmap.
      log_info(oopstorage, ref)("%s: released " PTR_FORMAT, name(), p2i(entry));
      uintx entry_bitmask = block->bitmask_for_entry(entry);
      assert((releasing & entry_bitmask) == 0,
             "Duplicate entry: " PTR_FORMAT, p2i(entry));
      releasing |= entry_bitmask;
      ++count;
    }
    // Release the contiguous entries that are in block.
    block->release_entries(releasing, &_deferred_updates);
    Atomic::sub(count, &_allocation_count);
  }
}

const char* dup_name(const char* name) {
  char* dup = NEW_C_HEAP_ARRAY(char, strlen(name) + 1, mtGC);
  strcpy(dup, name);
  return dup;
}

const size_t initial_active_array_size = 8;

OopStorage::OopStorage(const char* name,
                       Mutex* allocate_mutex,
                       Mutex* active_mutex) :
  _name(dup_name(name)),
  _active_array(ActiveArray::create(initial_active_array_size)),
  _allocate_list(),
  _deferred_updates(NULL),
  _allocate_mutex(allocate_mutex),
  _active_mutex(active_mutex),
  _allocation_count(0),
  _concurrent_iteration_active(false)
{
  _active_array->increment_refcount();
  assert(_active_mutex->rank() < _allocate_mutex->rank(),
         "%s: active_mutex must have lower rank than allocate_mutex", _name);
  assert(_active_mutex->_safepoint_check_required != Mutex::_safepoint_check_always,
         "%s: active mutex requires safepoint check", _name);
  assert(_allocate_mutex->_safepoint_check_required != Mutex::_safepoint_check_always,
         "%s: allocate mutex requires safepoint check", _name);
}

void OopStorage::delete_empty_block(const Block& block) {
  assert(block.is_empty(), "discarding non-empty block");
  log_info(oopstorage, blocks)("%s: delete empty block " PTR_FORMAT, name(), p2i(&block));
  Block::delete_block(block);
}

OopStorage::~OopStorage() {
  Block* block;
  while ((block = _deferred_updates) != NULL) {
    _deferred_updates = block->deferred_updates_next();
    block->set_deferred_updates_next(NULL);
  }
  while ((block = _allocate_list.head()) != NULL) {
    _allocate_list.unlink(*block);
  }
  bool unreferenced = _active_array->decrement_refcount();
  assert(unreferenced, "deleting storage while _active_array is referenced");
  for (size_t i = _active_array->block_count(); 0 < i; ) {
    block = _active_array->at(--i);
    Block::delete_block(*block);
  }
  ActiveArray::destroy(_active_array);
  FREE_C_HEAP_ARRAY(char, _name);
}

void OopStorage::delete_empty_blocks_safepoint() {
  assert_at_safepoint();
  // Process any pending release updates, which may make more empty
  // blocks available for deletion.
  while (reduce_deferred_updates()) {}
  // Don't interfere with a concurrent iteration.
  if (_concurrent_iteration_active) return;
  // Delete empty (and otherwise deletable) blocks from end of _allocate_list.
  for (Block* block = _allocate_list.tail();
       (block != NULL) && block->is_deletable();
       block = _allocate_list.tail()) {
    _active_array->remove(block);
    _allocate_list.unlink(*block);
    delete_empty_block(*block);
  }
}

void OopStorage::delete_empty_blocks_concurrent() {
  MutexLockerEx ml(_allocate_mutex, Mutex::_no_safepoint_check_flag);
  // Other threads could be adding to the empty block count while we
  // release the mutex across the block deletions.  Set an upper bound
  // on how many blocks we'll try to release, so other threads can't
  // cause an unbounded stay in this function.
  size_t limit = block_count();

  for (size_t i = 0; i < limit; ++i) {
    // Additional updates might become available while we dropped the
    // lock.  But limit number processed to limit lock duration.
    reduce_deferred_updates();

    Block* block = _allocate_list.tail();
    if ((block == NULL) || !block->is_deletable()) {
      // No block to delete, so done.  There could be more pending
      // deferred updates that could give us more work to do; deal with
      // that in some later call, to limit lock duration here.
      return;
    }

    {
      MutexLockerEx aml(_active_mutex, Mutex::_no_safepoint_check_flag);
      // Don't interfere with a concurrent iteration.
      if (_concurrent_iteration_active) return;
      _active_array->remove(block);
    }
    // Remove block from _allocate_list and delete it.
    _allocate_list.unlink(*block);
    // Release mutex while deleting block.
    MutexUnlockerEx ul(_allocate_mutex, Mutex::_no_safepoint_check_flag);
    delete_empty_block(*block);
  }
}

OopStorage::EntryStatus OopStorage::allocation_status(const oop* ptr) const {
  const Block* block = find_block_or_null(ptr);
  if (block != NULL) {
    // Prevent block deletion and _active_array modification.
    MutexLockerEx ml(_allocate_mutex, Mutex::_no_safepoint_check_flag);
    // Block could be a false positive, so get index carefully.
    size_t index = Block::active_index_safe(block);
    if ((index < _active_array->block_count()) &&
        (block == _active_array->at(index)) &&
        block->contains(ptr)) {
      if ((block->allocated_bitmask() & block->bitmask_for_entry(ptr)) != 0) {
        return ALLOCATED_ENTRY;
      } else {
        return UNALLOCATED_ENTRY;
      }
    }
  }
  return INVALID_ENTRY;
}

size_t OopStorage::allocation_count() const {
  return _allocation_count;
}

size_t OopStorage::block_count() const {
  WithActiveArray wab(this);
  // Count access is racy, but don't care.
  return wab.active_array().block_count();
}

size_t OopStorage::total_memory_usage() const {
  size_t total_size = sizeof(OopStorage);
  total_size += strlen(name()) + 1;
  total_size += sizeof(ActiveArray);
  WithActiveArray wab(this);
  const ActiveArray& blocks = wab.active_array();
  // Count access is racy, but don't care.
  total_size += blocks.block_count() * Block::allocation_size();
  total_size += blocks.size() * sizeof(Block*);
  return total_size;
}

// Parallel iteration support

uint OopStorage::BasicParState::default_estimated_thread_count(bool concurrent) {
  uint configured = concurrent ? ConcGCThreads : ParallelGCThreads;
  return MAX2(1u, configured);  // Never estimate zero threads.
}

OopStorage::BasicParState::BasicParState(const OopStorage* storage,
                                         uint estimated_thread_count,
                                         bool concurrent) :
  _storage(storage),
  _active_array(_storage->obtain_active_array()),
  _block_count(0),              // initialized properly below
  _next_block(0),
  _estimated_thread_count(estimated_thread_count),
  _concurrent(concurrent)
{
  assert(estimated_thread_count > 0, "estimated thread count must be positive");
  update_iteration_state(true);
  // Get the block count *after* iteration state updated, so concurrent
  // empty block deletion is suppressed and can't reduce the count.  But
  // ensure the count we use was written after the block with that count
  // was fully initialized; see ActiveArray::push.
  _block_count = _active_array->block_count_acquire();
}

OopStorage::BasicParState::~BasicParState() {
  _storage->relinquish_block_array(_active_array);
  update_iteration_state(false);
}

void OopStorage::BasicParState::update_iteration_state(bool value) {
  if (_concurrent) {
    MutexLockerEx ml(_storage->_active_mutex, Mutex::_no_safepoint_check_flag);
    assert(_storage->_concurrent_iteration_active != value, "precondition");
    _storage->_concurrent_iteration_active = value;
  }
}

bool OopStorage::BasicParState::claim_next_segment(IterationData* data) {
  data->_processed += data->_segment_end - data->_segment_start;
  size_t start = OrderAccess::load_acquire(&_next_block);
  if (start >= _block_count) {
    return finish_iteration(data); // No more blocks available.
  }
  // Try to claim several at a time, but not *too* many.  We want to
  // avoid deciding there are many available and selecting a large
  // quantity, get delayed, and then end up claiming most or all of
  // the remaining largish amount of work, leaving nothing for other
  // threads to do.  But too small a step can lead to contention
  // over _next_block, esp. when the work per block is small.
  size_t max_step = 10;
  size_t remaining = _block_count - start;
  size_t step = MIN2(max_step, 1 + (remaining / _estimated_thread_count));
  // Atomic::add with possible overshoot.  This can perform better
  // than a CAS loop on some platforms when there is contention.
  // We can cope with the uncertainty by recomputing start/end from
  // the result of the add, and dealing with potential overshoot.
  size_t end = Atomic::add(step, &_next_block);
  // _next_block may have changed, so recompute start from result of add.
  start = end - step;
  // _next_block may have changed so much that end has overshot.
  end = MIN2(end, _block_count);
  // _next_block may have changed so much that even start has overshot.
  if (start < _block_count) {
    // Record claimed segment for iteration.
    data->_segment_start = start;
    data->_segment_end = end;
    return true;                // Success.
  } else {
    // No more blocks to claim.
    return finish_iteration(data);
  }
}

bool OopStorage::BasicParState::finish_iteration(const IterationData* data) const {
  log_debug(oopstorage, blocks, stats)
           ("Parallel iteration on %s: blocks = " SIZE_FORMAT
            ", processed = " SIZE_FORMAT " (%2.f%%)",
            _storage->name(), _block_count, data->_processed,
            percent_of(data->_processed, _block_count));
  return false;
}

const char* OopStorage::name() const { return _name; }

#ifndef PRODUCT

void OopStorage::print_on(outputStream* st) const {
  size_t allocations = _allocation_count;
  size_t blocks = _active_array->block_count();

  double data_size = section_size * section_count;
  double alloc_percentage = percent_of((double)allocations, blocks * data_size);

  st->print("%s: " SIZE_FORMAT " entries in " SIZE_FORMAT " blocks (%.F%%), " SIZE_FORMAT " bytes",
            name(), allocations, blocks, alloc_percentage, total_memory_usage());
  if (_concurrent_iteration_active) {
    st->print(", concurrent iteration active");
  }
}

#endif // !PRODUCT
