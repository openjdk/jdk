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

FreeList<G1CodeRootChunk> G1CodeRootSet::_free_list;
size_t G1CodeRootSet::_num_chunks_handed_out = 0;

G1CodeRootChunk* G1CodeRootSet::new_chunk() {
  G1CodeRootChunk* result = _free_list.get_chunk_at_head();
  if (result == NULL) {
    result = new G1CodeRootChunk();
  }
  G1CodeRootSet::_num_chunks_handed_out++;
  result->reset();
  return result;
}

void G1CodeRootSet::free_chunk(G1CodeRootChunk* chunk) {
  _free_list.return_chunk_at_head(chunk);
  G1CodeRootSet::_num_chunks_handed_out--;
}

void G1CodeRootSet::free_all_chunks(FreeList<G1CodeRootChunk>* list) {
  G1CodeRootSet::_num_chunks_handed_out -= list->count();
  _free_list.prepend(list);
}

void G1CodeRootSet::purge_chunks(size_t keep_ratio) {
  size_t keep = G1CodeRootSet::_num_chunks_handed_out * keep_ratio / 100;

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

size_t G1CodeRootSet::static_mem_size() {
  return sizeof(_free_list) + sizeof(_num_chunks_handed_out);
}

size_t G1CodeRootSet::fl_mem_size() {
  return _free_list.count() * _free_list.size();
}

void G1CodeRootSet::initialize() {
  _free_list.initialize();
  _free_list.set_size(G1CodeRootChunk::word_size());
}

G1CodeRootSet::G1CodeRootSet() : _list(), _length(0) {
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

size_t G1CodeRootSet::mem_size() {
  return sizeof(this) + _list.count() * _list.size();
}

#ifndef PRODUCT

void G1CodeRootSet::test() {
  initialize();

  assert(_free_list.count() == 0, "Free List must be empty");
  assert(_num_chunks_handed_out == 0, "No elements must have been handed out yet");

  // The number of chunks that we allocate for purge testing.
  size_t const num_chunks = 10;
  {
    G1CodeRootSet set1;
    assert(set1.is_empty(), "Code root set must be initially empty but is not.");

    set1.add((nmethod*)1);
    assert(_num_chunks_handed_out == 1,
           err_msg("Must have allocated and handed out one chunk, but handed out "
                   SIZE_FORMAT" chunks", _num_chunks_handed_out));
    assert(set1.length() == 1, err_msg("Added exactly one element, but set contains "
                                       SIZE_FORMAT" elements", set1.length()));

    // G1CodeRootChunk::word_size() is larger than G1CodeRootChunk::num_entries which
    // we cannot access.
    for (uint i = 0; i < G1CodeRootChunk::word_size() + 1; i++) {
      set1.add((nmethod*)1);
    }
    assert(_num_chunks_handed_out == 1,
           err_msg("Duplicate detection must have prevented allocation of further "
                   "chunks but contains "SIZE_FORMAT, _num_chunks_handed_out));
    assert(set1.length() == 1,
           err_msg("Duplicate detection should not have increased the set size but "
                   "is "SIZE_FORMAT, set1.length()));

    size_t num_total_after_add = G1CodeRootChunk::word_size() + 1;
    for (size_t i = 0; i < num_total_after_add - 1; i++) {
      set1.add((nmethod*)(2 + i));
    }
    assert(_num_chunks_handed_out > 1,
           "After adding more code roots, more than one chunks should have been handed out");
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
    assert(_num_chunks_handed_out == 0,
           err_msg("After popping all elements, all chunks must have been returned "
                   "but are still "SIZE_FORMAT, _num_chunks_handed_out));

    purge_chunks(0);
    assert(_free_list.count() == 0,
           err_msg("After purging everything, the free list must be empty but still "
                   "contains "SIZE_FORMAT" chunks", _free_list.count()));

    // Add some more handed out chunks.
    size_t i = 0;
    while (_num_chunks_handed_out < num_chunks) {
      set1.add((nmethod*)i);
      i++;
    }

    {
      // Generate chunks on the free list.
      G1CodeRootSet set2;
      size_t i = 0;
      while (_num_chunks_handed_out < num_chunks * 2) {
        set2.add((nmethod*)i);
        i++;
      }
      // Exit of the scope of the set2 object will call the destructor that generates
      // num_chunks elements on the free list.
    }

    assert(_num_chunks_handed_out == num_chunks,
           err_msg("Deletion of the second set must have resulted in giving back "
                   "those, but there is still "SIZE_FORMAT" handed out, expecting "
                   SIZE_FORMAT, _num_chunks_handed_out, num_chunks));
    assert((size_t)_free_list.count() == num_chunks,
           err_msg("After freeing "SIZE_FORMAT" chunks, they must be on the free list "
                   "but there are only "SIZE_FORMAT, num_chunks, _free_list.count()));

    size_t const test_percentage = 50;
    purge_chunks(test_percentage);
    assert(_num_chunks_handed_out == num_chunks,
           err_msg("Purging must not hand out chunks but there are "SIZE_FORMAT,
                   _num_chunks_handed_out));
    assert((size_t)_free_list.count() == (ssize_t)(num_chunks * test_percentage / 100),
           err_msg("Must have purged "SIZE_FORMAT" percent of "SIZE_FORMAT" chunks"
                   "but there are "SSIZE_FORMAT, test_percentage, num_chunks,
                   _free_list.count()));
    // Purge the remainder of the chunks on the free list.
    purge_chunks(0);
    assert(_free_list.count() == 0, "Free List must be empty");
    assert(_num_chunks_handed_out == num_chunks,
           err_msg("Expected to be "SIZE_FORMAT" chunks handed out from the first set "
                   "but there are "SIZE_FORMAT, num_chunks, _num_chunks_handed_out));

    // Exit of the scope of the set1 object will call the destructor that generates
    // num_chunks additional elements on the free list.
  }

  assert(_num_chunks_handed_out == 0,
         err_msg("Deletion of the only set must have resulted in no chunks handed "
                 "out, but there is still "SIZE_FORMAT" handed out", _num_chunks_handed_out));
  assert((size_t)_free_list.count() == num_chunks,
         err_msg("After freeing "SIZE_FORMAT" chunks, they must be on the free list "
                 "but there are only "SSIZE_FORMAT, num_chunks, _free_list.count()));

  // Restore initial state.
  purge_chunks(0);
  assert(_free_list.count() == 0, "Free List must be empty");
  assert(_num_chunks_handed_out == 0, "No elements must have been handed out yet");
}

void TestCodeCacheRemSet_test() {
  G1CodeRootSet::test();
}
#endif
