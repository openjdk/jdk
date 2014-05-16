/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "code/nmethod.hpp"
#include "gc_implementation/g1/g1CodeCacheRemSet.hpp"
#include "memory/iterator.hpp"

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

G1CodeRootChunk::G1CodeRootChunk() : _top(NULL), _next(NULL), _prev(NULL) {
  _top = bottom();
}

void G1CodeRootChunk::reset() {
  _next = _prev = NULL;
  _top = bottom();
}

void G1CodeRootChunk::nmethods_do(CodeBlobClosure* cl) {
  nmethod** cur = bottom();
  while (cur != _top) {
    cl->do_code_blob(*cur);
    cur++;
  }
}

G1CodeRootChunkManager::G1CodeRootChunkManager() : _free_list(), _num_chunks_handed_out(0) {
  _free_list.initialize();
  _free_list.set_size(G1CodeRootChunk::word_size());
}

size_t G1CodeRootChunkManager::fl_mem_size() {
  return _free_list.count() * _free_list.size();
}

void G1CodeRootChunkManager::free_all_chunks(FreeList<G1CodeRootChunk>* list) {
  _num_chunks_handed_out -= list->count();
  _free_list.prepend(list);
}

void G1CodeRootChunkManager::free_chunk(G1CodeRootChunk* chunk) {
  _free_list.return_chunk_at_head(chunk);
  _num_chunks_handed_out--;
}

void G1CodeRootChunkManager::purge_chunks(size_t keep_ratio) {
  size_t keep = _num_chunks_handed_out * keep_ratio / 100;
  if (keep >= (size_t)_free_list.count()) {
    return;
  }

  FreeList<G1CodeRootChunk> temp;
  temp.initialize();
  temp.set_size(G1CodeRootChunk::word_size());

  _free_list.getFirstNChunksFromList((size_t)_free_list.count() - keep, &temp);

  G1CodeRootChunk* cur = temp.get_chunk_at_head();
  while (cur != NULL) {
    delete cur;
    cur = temp.get_chunk_at_head();
  }
}

size_t G1CodeRootChunkManager::static_mem_size() {
  return sizeof(G1CodeRootChunkManager);
}


G1CodeRootChunk* G1CodeRootChunkManager::new_chunk() {
  G1CodeRootChunk* result = _free_list.get_chunk_at_head();
  if (result == NULL) {
    result = new G1CodeRootChunk();
  }
  _num_chunks_handed_out++;
  result->reset();
  return result;
}

#ifndef PRODUCT

size_t G1CodeRootChunkManager::num_chunks_handed_out() const {
  return _num_chunks_handed_out;
}

size_t G1CodeRootChunkManager::num_free_chunks() const {
  return (size_t)_free_list.count();
}

#endif

G1CodeRootChunkManager G1CodeRootSet::_default_chunk_manager;

void G1CodeRootSet::purge_chunks(size_t keep_ratio) {
  _default_chunk_manager.purge_chunks(keep_ratio);
}

size_t G1CodeRootSet::free_chunks_static_mem_size() {
  return _default_chunk_manager.static_mem_size();
}

size_t G1CodeRootSet::free_chunks_mem_size() {
  return _default_chunk_manager.fl_mem_size();
}

G1CodeRootSet::G1CodeRootSet(G1CodeRootChunkManager* manager) : _manager(manager), _list(), _length(0) {
  if (_manager == NULL) {
    _manager = &_default_chunk_manager;
  }
  _list.initialize();
  _list.set_size(G1CodeRootChunk::word_size());
}

G1CodeRootSet::~G1CodeRootSet() {
  clear();
}

void G1CodeRootSet::add(nmethod* method) {
  if (!contains(method)) {
    // Try to add the nmethod. If there is not enough space, get a new chunk.
    if (_list.head() == NULL || _list.head()->is_full()) {
      G1CodeRootChunk* cur = new_chunk();
      _list.return_chunk_at_head(cur);
    }
    bool result = _list.head()->add(method);
    guarantee(result, err_msg("Not able to add nmethod "PTR_FORMAT" to newly allocated chunk.", method));
    _length++;
  }
}

void G1CodeRootSet::remove(nmethod* method) {
  G1CodeRootChunk* found = find(method);
  if (found != NULL) {
    bool result = found->remove(method);
    guarantee(result, err_msg("could not find nmethod "PTR_FORMAT" during removal although we previously found it", method));
    // eventually free completely emptied chunk
    if (found->is_empty()) {
      _list.remove_chunk(found);
      free(found);
    }
    _length--;
  }
  assert(!contains(method), err_msg(PTR_FORMAT" still contains nmethod "PTR_FORMAT, this, method));
}

nmethod* G1CodeRootSet::pop() {
  do {
    G1CodeRootChunk* cur = _list.head();
    if (cur == NULL) {
      assert(_length == 0, "when there are no chunks, there should be no elements");
      return NULL;
    }
    nmethod* result = cur->pop();
    if (result != NULL) {
      _length--;
      return result;
    } else {
      free(_list.get_chunk_at_head());
    }
  } while (true);
}

G1CodeRootChunk* G1CodeRootSet::find(nmethod* method) {
  G1CodeRootChunk* cur = _list.head();
  while (cur != NULL) {
    if (cur->contains(method)) {
      return cur;
    }
    cur = (G1CodeRootChunk*)cur->next();
  }
  return NULL;
}

void G1CodeRootSet::free(G1CodeRootChunk* chunk) {
  free_chunk(chunk);
}

bool G1CodeRootSet::contains(nmethod* method) {
  return find(method) != NULL;
}

void G1CodeRootSet::clear() {
  free_all_chunks(&_list);
  _length = 0;
}

void G1CodeRootSet::nmethods_do(CodeBlobClosure* blk) const {
  G1CodeRootChunk* cur = _list.head();
  while (cur != NULL) {
    cur->nmethods_do(blk);
    cur = (G1CodeRootChunk*)cur->next();
  }
}

size_t G1CodeRootSet::static_mem_size() {
  return sizeof(G1CodeRootSet);
}

size_t G1CodeRootSet::mem_size() {
  return G1CodeRootSet::static_mem_size() + _list.count() * _list.size();
}

#ifndef PRODUCT

void G1CodeRootSet::test() {
  G1CodeRootChunkManager mgr;

  assert(mgr.num_chunks_handed_out() == 0, "Must not have handed out chunks yet");

  assert(G1CodeRootChunkManager::static_mem_size() > sizeof(void*),
         err_msg("The chunk manager's static memory usage seems too small, is only "SIZE_FORMAT" bytes.", G1CodeRootChunkManager::static_mem_size()));

  // The number of chunks that we allocate for purge testing.
  size_t const num_chunks = 10;

  {
    G1CodeRootSet set1(&mgr);
    assert(set1.is_empty(), "Code root set must be initially empty but is not.");

    assert(G1CodeRootSet::static_mem_size() > sizeof(void*),
           err_msg("The code root set's static memory usage seems too small, is only "SIZE_FORMAT" bytes", G1CodeRootSet::static_mem_size()));

    set1.add((nmethod*)1);
    assert(mgr.num_chunks_handed_out() == 1,
           err_msg("Must have allocated and handed out one chunk, but handed out "
                   SIZE_FORMAT" chunks", mgr.num_chunks_handed_out()));
    assert(set1.length() == 1, err_msg("Added exactly one element, but set contains "
                                       SIZE_FORMAT" elements", set1.length()));

    // G1CodeRootChunk::word_size() is larger than G1CodeRootChunk::num_entries which
    // we cannot access.
    for (uint i = 0; i < G1CodeRootChunk::word_size() + 1; i++) {
      set1.add((nmethod*)1);
    }
    assert(mgr.num_chunks_handed_out() == 1,
           err_msg("Duplicate detection must have prevented allocation of further "
                   "chunks but allocated "SIZE_FORMAT, mgr.num_chunks_handed_out()));
    assert(set1.length() == 1,
           err_msg("Duplicate detection should not have increased the set size but "
                   "is "SIZE_FORMAT, set1.length()));

    size_t num_total_after_add = G1CodeRootChunk::word_size() + 1;
    for (size_t i = 0; i < num_total_after_add - 1; i++) {
      set1.add((nmethod*)(uintptr_t)(2 + i));
    }
    assert(mgr.num_chunks_handed_out() > 1,
           "After adding more code roots, more than one additional chunk should have been handed out");
    assert(set1.length() == num_total_after_add,
           err_msg("After adding in total "SIZE_FORMAT" distinct code roots, they "
                   "need to be in the set, but there are only "SIZE_FORMAT,
                   num_total_after_add, set1.length()));

    size_t num_popped = 0;
    while (set1.pop() != NULL) {
      num_popped++;
    }
    assert(num_popped == num_total_after_add,
           err_msg("Managed to pop "SIZE_FORMAT" code roots, but only "SIZE_FORMAT" "
                   "were added", num_popped, num_total_after_add));
    assert(mgr.num_chunks_handed_out() == 0,
           err_msg("After popping all elements, all chunks must have been returned "
                   "but there are still "SIZE_FORMAT" additional", mgr.num_chunks_handed_out()));

    mgr.purge_chunks(0);
    assert(mgr.num_free_chunks() == 0,
           err_msg("After purging everything, the free list must be empty but still "
                   "contains "SIZE_FORMAT" chunks", mgr.num_free_chunks()));

    // Add some more handed out chunks.
    size_t i = 0;
    while (mgr.num_chunks_handed_out() < num_chunks) {
      set1.add((nmethod*)i);
      i++;
    }

    {
      // Generate chunks on the free list.
      G1CodeRootSet set2(&mgr);
      size_t i = 0;
      while (mgr.num_chunks_handed_out() < (num_chunks * 2)) {
        set2.add((nmethod*)i);
        i++;
      }
      // Exit of the scope of the set2 object will call the destructor that generates
      // num_chunks elements on the free list.
    }

    assert(mgr.num_chunks_handed_out() == num_chunks,
           err_msg("Deletion of the second set must have resulted in giving back "
                   "those, but there are still "SIZE_FORMAT" additional handed out, expecting "
                   SIZE_FORMAT, mgr.num_chunks_handed_out(), num_chunks));
    assert(mgr.num_free_chunks() == num_chunks,
           err_msg("After freeing "SIZE_FORMAT" chunks, they must be on the free list "
                   "but there are only "SIZE_FORMAT, num_chunks, mgr.num_free_chunks()));

    size_t const test_percentage = 50;
    mgr.purge_chunks(test_percentage);
    assert(mgr.num_chunks_handed_out() == num_chunks,
           err_msg("Purging must not hand out chunks but there are "SIZE_FORMAT,
                   mgr.num_chunks_handed_out()));
    assert(mgr.num_free_chunks() == (size_t)(mgr.num_chunks_handed_out() * test_percentage / 100),
           err_msg("Must have purged "SIZE_FORMAT" percent of "SIZE_FORMAT" chunks"
                   "but there are "SIZE_FORMAT, test_percentage, num_chunks,
                   mgr.num_free_chunks()));
    // Purge the remainder of the chunks on the free list.
    mgr.purge_chunks(0);
    assert(mgr.num_free_chunks() == 0, "Free List must be empty");
    assert(mgr.num_chunks_handed_out() == num_chunks,
           err_msg("Expected to be "SIZE_FORMAT" chunks handed out from the first set "
                   "but there are "SIZE_FORMAT, num_chunks, mgr.num_chunks_handed_out()));

    // Exit of the scope of the set1 object will call the destructor that generates
    // num_chunks additional elements on the free list.
   }

  assert(mgr.num_chunks_handed_out() == 0,
         err_msg("Deletion of the only set must have resulted in no chunks handed "
                 "out, but there is still "SIZE_FORMAT" handed out", mgr.num_chunks_handed_out()));
  assert(mgr.num_free_chunks() == num_chunks,
         err_msg("After freeing "SIZE_FORMAT" chunks, they must be on the free list "
                 "but there are only "SIZE_FORMAT, num_chunks, mgr.num_free_chunks()));

  // Restore initial state.
  mgr.purge_chunks(0);
  assert(mgr.num_free_chunks() == 0, "Free List must be empty");
  assert(mgr.num_chunks_handed_out() == 0, "No additional elements must have been handed out yet");
}

void TestCodeCacheRemSet_test() {
  G1CodeRootSet::test();
}
#endif
