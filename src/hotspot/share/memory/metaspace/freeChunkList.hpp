/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_FREECHUNKLIST_HPP
#define SHARE_MEMORY_METASPACE_FREECHUNKLIST_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/chunklevel.hpp"
#include "memory/metaspace/counters.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metachunkList.hpp"

class outputStream;

namespace metaspace {

// This is the free list underlying the ChunkManager.
//
// Chunks are kept in a vector of double-linked double-headed lists
//  (using Metachunk::prev/next). One list per chunk level exists.
//
// Chunks in these lists are roughly ordered: uncommitted chunks
//  are added to the back of the list, fully or partially committed
//  chunks to the front.
//
// (Small caveat: commit state of a chunk may change as a result of
//  actions on neighboring chunks, if the chunk is smaller than a commit
//  granule and therefore shares its granule with neighbors. So it may change
//  after the chunk has been added to the list.
//  It will never involuntarily uncommit: only chunks >= granule size are uncommitted.
//  But it may get involuntarily committed if an in-granule neighbor is committed and
//  causes committing of the whole granule.
//  In practice this is not a big deal; it has very little consequence.)
//
// Beyond adding at either front or at back, we do not sort on insert, since the
//  insert path is used during Metaspace reclamation which may happen at GC pause.
//
// During retrieval (at class loading), we search the list for a chunk of at least
//  n committed words to satisfy the caller requested committed word size. We stop
//  searching at the first fully uncommitted chunk.
//
// Note that even though this is an O(n) search, in practice this is not a problem:
//  - in all likelihood the requested commit word size is way smaller than even a single
//    commit granule, so 99% of all searches would end at the first chunk (which is either
//    uncommitted or committed to at least one commit granule size).
//  - in all likelihood chunks, when added to this list, are either fully committed
//    or fully uncommitted (see Settings::uncommit_on_return_min_word_size()).
//
// Should we ever encounter situations where the O(n) search is a bottleneck, this
//  structure can easily be optimized (e.g. a BST). But for now lets keep this simple.

class FreeChunkList {

  Metachunk* _first;
  Metachunk* _last;

  IntCounter _num_chunks;
  SizeCounter _committed_word_size;

  void add_front(Metachunk* c) {
    if (_first == NULL) {
      assert(_last == NULL, "Sanity");
      _first = _last = c;
      c->set_prev(NULL);
      c->set_next(NULL);
    } else {
      assert(_last != NULL, "Sanity");
      c->set_next(_first);
      c->set_prev(NULL);
      _first->set_prev(c);
      _first = c;
    }
  }

  // Add chunk to the back of the list.
  void add_back(Metachunk* c) {
    if (_last == NULL) {
      assert(_first == NULL, "Sanity");
      _last = _first = c;
      c->set_prev(NULL);
      c->set_next(NULL);
    } else {
      assert(_first != NULL, "Sanity");
      c->set_next(NULL);
      c->set_prev(_last);
      _last->set_next(c);
      _last = c;
    }
  }

public:

  FreeChunkList() :
    _first(NULL),
    _last(NULL)
  {}

  // Remove given chunk from anywhere in the list.
  Metachunk* remove(Metachunk* c) {
    assert(contains(c), "Must be contained here");
    Metachunk* pred = c->prev();
    Metachunk* succ = c->next();
    if (pred) {
      pred->set_next(succ);
    }
    if (succ) {
      succ->set_prev(pred);
    }
    if (_first == c) {
      _first = succ;
    }
    if (_last == c) {
      _last = pred;
    }
    c->set_next(NULL);
    c->set_prev(NULL);
    _committed_word_size.decrement_by(c->committed_words());
    _num_chunks.decrement();
    return c;
  }

  void add(Metachunk* c) {
    assert(contains(c) == false, "Chunk already in freelist");
    assert(_first == NULL || _first->level() == c->level(), "wrong level");
    // Uncomitted chunks go to the back, fully or partially committed to the front.
    if (c->committed_words() == 0) {
      add_back(c);
    } else {
      add_front(c);
    }
    _committed_word_size.increment_by(c->committed_words());
    _num_chunks.increment();
  }

  // Removes the first chunk from the list and returns it. Returns NULL if list is empty.
  Metachunk* remove_first() {
    Metachunk* c = _first;
    if (c != NULL) {
      remove(c);
    }
    return c;
  }

  // Find and removes a chunk in this list which has at least min_committed_words committed words.
  // Returns NULL if not found.
  Metachunk* find_matching(size_t min_committed_words) {
    Metachunk* c = _first;
    while (c != NULL && c->committed_words() > 0) {
      if (c->committed_words() <= min_committed_words) {
        remove(c);
        return c;
      }
      c = c->next();
    }
    return NULL;
  }

  // Returns reference to the first chunk in the list, or NULL
  Metachunk* first() const { return _first; }

#ifdef ASSERT
  bool contains(const Metachunk* c) const;
  void verify() const;
#endif

  // Returns number of chunks
  int num_chunks() const { return _num_chunks.get(); }

  // Returns total committed word size
  size_t committed_word_size() const { return _committed_word_size.get(); }

  void print_on(outputStream* st) const;

};

// A vector of free chunk lists, one per chunk level
class FreeChunkListVector {

  FreeChunkList _lists[chunklevel::NUM_CHUNK_LEVELS];

  const FreeChunkList* list_for_level(chunklevel_t lvl) const         { DEBUG_ONLY(chunklevel::check_valid_level(lvl)); return _lists + lvl; }
  FreeChunkList* list_for_level(chunklevel_t lvl)                     { DEBUG_ONLY(chunklevel::check_valid_level(lvl)); return _lists + lvl; }

  const FreeChunkList* list_for_chunk(const Metachunk* c) const       { return list_for_level(c->level()); }
  FreeChunkList* list_for_chunk(const Metachunk* c)                   { return list_for_level(c->level()); }

public:

  // Remove given chunk from its list. List must contain that chunk.
  void remove(Metachunk* c) {
    list_for_chunk(c)->remove(c);
  }

  // Remove first node unless empty. Returns node or NULL.
  Metachunk* remove_first(chunklevel_t lvl) {
    Metachunk* c = list_for_level(lvl)->remove_first();
    return c;
  }

  void add(Metachunk* c) {
    list_for_chunk(c)->add(c);
  }

  // Returns number of chunks for a given level.
  int num_chunks_at_level(chunklevel_t lvl) const {
    return list_for_level(lvl)->num_chunks();
  }

  // Returns number of chunks for a given level.
  size_t committed_word_size_at_level(chunklevel_t lvl) const {
    return list_for_level(lvl)->committed_word_size();
  }

  // Returns reference to first chunk at this level, or NULL if sublist is empty.
  Metachunk* first_at_level(chunklevel_t lvl) const {
    return list_for_level(lvl)->first();
  }

  // Look for a chunk: starting at level, up to and including max_level,
  //  return the first chunk whose committed words >= min_committed_words.
  // Return NULL if no such chunk was found.
  Metachunk* search_chunk_ascending(chunklevel_t level, chunklevel_t max_level,
                                    size_t min_committed_words);

  // Look for a chunk: starting at level, down to (including) the root chunk level,
  // return the first chunk whose committed words >= min_committed_words.
  // Return NULL if no such chunk was found.
  Metachunk* search_chunk_descending(chunklevel_t level, size_t min_committed_words);

  // Returns total size in all lists (regardless of commit state of underlying memory)
  size_t word_size() const;

  // Returns total committed size in all lists
  size_t committed_word_size() const;

  // Returns number of chunks in all lists
  int num_chunks() const;

#ifdef ASSERT
  bool contains(const Metachunk* c) const;
  void verify() const;
#endif

  void print_on(outputStream* st) const;

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_FREECHUNKLIST_HPP
