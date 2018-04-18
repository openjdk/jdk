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
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/align.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

OopStorage::BlockEntry::BlockEntry() : _prev(NULL), _next(NULL) {}

OopStorage::BlockEntry::~BlockEntry() {
  assert(_prev == NULL, "deleting attached block");
  assert(_next == NULL, "deleting attached block");
}

OopStorage::BlockList::BlockList(const BlockEntry& (*get_entry)(const Block& block)) :
  _head(NULL), _tail(NULL), _get_entry(get_entry)
{}

OopStorage::BlockList::~BlockList() {
  // ~OopStorage() empties its lists before destroying them.
  assert(_head == NULL, "deleting non-empty block list");
  assert(_tail == NULL, "deleting non-empty block list");
}

void OopStorage::BlockList::push_front(const Block& block) {
  const Block* old = _head;
  if (old == NULL) {
    assert(_tail == NULL, "invariant");
    _head = _tail = &block;
  } else {
    _get_entry(block)._next = old;
    _get_entry(*old)._prev = &block;
    _head = &block;
  }
}

void OopStorage::BlockList::push_back(const Block& block) {
  const Block* old = _tail;
  if (old == NULL) {
    assert(_head == NULL, "invariant");
    _head = _tail = &block;
  } else {
    _get_entry(*old)._next = &block;
    _get_entry(block)._prev = old;
    _tail = &block;
  }
}

void OopStorage::BlockList::unlink(const Block& block) {
  const BlockEntry& block_entry = _get_entry(block);
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
    _get_entry(*next_blk)._prev = NULL;
    _head = next_blk;
  } else if (next_blk == NULL) {
    assert(_tail == &block, "invariant");
    _get_entry(*prev_blk)._next = NULL;
    _tail = prev_blk;
  } else {
    _get_entry(*next_blk)._prev = prev_blk;
    _get_entry(*prev_blk)._next = next_blk;
  }
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
  _active_entry(),
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

const OopStorage::BlockEntry& OopStorage::Block::get_active_entry(const Block& block) {
  return block._active_entry;
}

const OopStorage::BlockEntry& OopStorage::Block::get_allocate_entry(const Block& block) {
  return block._allocate_entry;
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
// e.g. contained in some block in owner's _active_list.  Other uses
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
// list, though they are still present in the _active_list.  Empty blocks are
// kept at the end of the _allocate_list, to make it easy for empty block
// deletion to find them.
//
// allocate(), and delete_empty_blocks_concurrent() lock the
// _allocate_mutex while performing any list modifications.
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
// avoiding iteration over the _active_list).  Once the block has been
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
          log_info(oopstorage, ref)("%s: failed allocation", name());
          return NULL;
        }
      }
    } else {
      // Add new block to storage.
      log_info(oopstorage, blocks)("%s: new block " PTR_FORMAT, name(), p2i(block));

      // Add to end of _allocate_list.  The mutex release allowed
      // other threads to add blocks to the _allocate_list.  We prefer
      // to allocate from non-empty blocks, to allow empty blocks to
      // be deleted.
      _allocate_list.push_back(*block);
      // Add to front of _active_list, and then record as the head
      // block, for concurrent iteration protocol.
      _active_list.push_front(*block);
      ++_block_count;
      // Ensure all setup of block is complete before making it visible.
      OrderAccess::release_store(&_active_head, block);
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

OopStorage::Block* OopStorage::find_block_or_null(const oop* ptr) const {
  assert(ptr != NULL, "precondition");
  return Block::block_for_ptr(this, ptr);
}

static void log_release_transitions(uintx releasing,
                                    uintx old_allocated,
                                    const OopStorage* owner,
                                    const void* block) {
  ResourceMark rm;
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

OopStorage::OopStorage(const char* name,
                       Mutex* allocate_mutex,
                       Mutex* active_mutex) :
  _name(dup_name(name)),
  _active_list(&Block::get_active_entry),
  _allocate_list(&Block::get_allocate_entry),
  _active_head(NULL),
  _deferred_updates(NULL),
  _allocate_mutex(allocate_mutex),
  _active_mutex(active_mutex),
  _allocation_count(0),
  _block_count(0),
  _concurrent_iteration_active(false)
{
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
  while ((block = _active_list.head()) != NULL) {
    _active_list.unlink(*block);
    Block::delete_block(*block);
  }
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
  for (const Block* block = _allocate_list.ctail();
       (block != NULL) && block->is_deletable();
       block = _allocate_list.ctail()) {
    _active_list.unlink(*block);
    _allocate_list.unlink(*block);
    delete_empty_block(*block);
    --_block_count;
  }
  // Update _active_head, in case current value was in deleted set.
  _active_head = _active_list.head();
}

void OopStorage::delete_empty_blocks_concurrent() {
  MutexLockerEx ml(_allocate_mutex, Mutex::_no_safepoint_check_flag);
  // Other threads could be adding to the empty block count while we
  // release the mutex across the block deletions.  Set an upper bound
  // on how many blocks we'll try to release, so other threads can't
  // cause an unbounded stay in this function.
  size_t limit = _block_count;

  for (size_t i = 0; i < limit; ++i) {
    // Additional updates might become available while we dropped the
    // lock.  But limit number processed to limit lock duration.
    reduce_deferred_updates();

    const Block* block = _allocate_list.ctail();
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
      // Remove block from _active_list, updating head if needed.
      _active_list.unlink(*block);
      --_block_count;
      if (block == _active_head) {
        _active_head = _active_list.head();
      }
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
    // Verify block is a real block.  For now, simple linear search.
    // Do something more clever if this is a performance bottleneck.
    MutexLockerEx ml(_allocate_mutex, Mutex::_no_safepoint_check_flag);
    for (const Block* check_block = _active_list.chead();
         check_block != NULL;
         check_block = _active_list.next(*check_block)) {
      if (check_block == block) {
        if ((block->allocated_bitmask() & block->bitmask_for_entry(ptr)) != 0) {
          return ALLOCATED_ENTRY;
        } else {
          return UNALLOCATED_ENTRY;
        }
      }
    }
  }
  return INVALID_ENTRY;
}

size_t OopStorage::allocation_count() const {
  return _allocation_count;
}

size_t OopStorage::block_count() const {
  return _block_count;
}

size_t OopStorage::total_memory_usage() const {
  size_t total_size = sizeof(OopStorage);
  total_size += strlen(name()) + 1;
  total_size += block_count() * Block::allocation_size();
  return total_size;
}

// Parallel iteration support

static char* not_started_marker_dummy = NULL;
static void* const not_started_marker = &not_started_marker_dummy;

OopStorage::BasicParState::BasicParState(OopStorage* storage, bool concurrent) :
  _storage(storage),
  _next_block(not_started_marker),
  _concurrent(concurrent)
{
  update_iteration_state(true);
}

OopStorage::BasicParState::~BasicParState() {
  update_iteration_state(false);
}

void OopStorage::BasicParState::update_iteration_state(bool value) {
  if (_concurrent) {
    MutexLockerEx ml(_storage->_active_mutex, Mutex::_no_safepoint_check_flag);
    assert(_storage->_concurrent_iteration_active != value, "precondition");
    _storage->_concurrent_iteration_active = value;
  }
}

void OopStorage::BasicParState::ensure_iteration_started() {
  if (!_concurrent) {
    assert_at_safepoint();
  }
  assert(!_concurrent || _storage->_concurrent_iteration_active, "invariant");
  // Ensure _next_block is not the not_started_marker, setting it to
  // the _active_head to start the iteration if necessary.
  if (OrderAccess::load_acquire(&_next_block) == not_started_marker) {
    Atomic::cmpxchg(_storage->_active_head, &_next_block, not_started_marker);
  }
  assert(_next_block != not_started_marker, "postcondition");
}

OopStorage::Block* OopStorage::BasicParState::claim_next_block() {
  assert(_next_block != not_started_marker, "Iteration not started");
  void* next = _next_block;
  while (next != NULL) {
    void* new_next = _storage->_active_list.next(*static_cast<Block*>(next));
    void* fetched = Atomic::cmpxchg(new_next, &_next_block, next);
    if (fetched == next) break; // Claimed.
    next = fetched;
  }
  return static_cast<Block*>(next);
}

const char* OopStorage::name() const { return _name; }

#ifndef PRODUCT

void OopStorage::print_on(outputStream* st) const {
  size_t allocations = _allocation_count;
  size_t blocks = _block_count;

  double data_size = section_size * section_count;
  double alloc_percentage = percent_of((double)allocations, blocks * data_size);

  st->print("%s: " SIZE_FORMAT " entries in " SIZE_FORMAT " blocks (%.F%%), " SIZE_FORMAT " bytes",
            name(), allocations, blocks, alloc_percentage, total_memory_usage());
  if (_concurrent_iteration_active) {
    st->print(", concurrent iteration active");
  }
}

#endif // !PRODUCT
