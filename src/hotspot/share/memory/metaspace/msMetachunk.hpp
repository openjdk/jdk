/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, 2020 SAP SE. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_MSMETACHUNK_HPP
#define SHARE_MEMORY_METASPACE_MSMETACHUNK_HPP

#include "memory/metaspace/msChunklevel.hpp"
#include "memory/metaspace/msCounter.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;

namespace metaspace {

class VirtualSpaceNode;

// A Metachunk is a contiguous metaspace memory region. It is part of
// a MetaspaceArena, which keeps a list of MetaChunk and allocates via
// pointer bump from the top element in the list.
//
// The Metachunk object itself (the "chunk header") is separated from
//  the memory region (the chunk payload) it describes. It also can have
//  no payload (a "dead" chunk). In itself it lives in C-heap, managed
//  as part of a pool of Metachunk headers (ChunkHeaderPool).
//
// -- Metachunk state --
//
// A Metachunk is "in-use" if it is part of a MetaspaceArena. That means
//  its memory is used - or will be used shortly - to hold VM metadata
//  on behalf of a class loader.
//
// A Metachunk is "free" if its payload is currently unused. In that
//  case it is managed by a chunk freelist (the ChunkManager).
// 
// A Metachunk is "dead" if it does not have a corresponding payload.
//  In that case it lives as part of a freelist-of-dead-chunk-headers
//  in the ChunkHeaderPool.
//
// -- Level --
//
// Metachunks are managed as part of a buddy style allocation scheme.
// Sized always in steps of power-of-2, ranging from the smallest chunk size
// (1Kb) to the largest (4Mb) (see chunklevel.hpp).
// Its size is encoded as level, with level 0 being the largest chunk
// size ("root chunk").
//
// -- Payload commit state --
//
// A Metachunk payload may be committed, partly committed or completely
// uncommitted. Technically, a payload may be committed "checkered" -
// i.e. committed and uncommitted parts may interleave - but the
// important part is how much contiguous space is committed starting
// at the base of the payload (since that's where we allocate). 
// 
// The Metachunk keeps track of how much space is committed starting
//  at the base of the payload - which is a performace optimization - 
//  while underlying layers (VirtualSpaceNode->commitmask) keep track
//  of the "real" commit state, aka which granules are committed,
//  independent on what chunks reside above those granules.

//            +--------------+ <- end    -----------+ ----------+
//            |              |                      |           |
//            |              |                      |           |
//            |              |                      |           |
//            |              |                      |           |
//            |              |                      |           |
//            | -----------  | <- committed_top  -- +           |
//            |              |                      |           |
//            |              |                      | "free"    |
//            |              |                      |           | size 
//            |              |     "free_below_     |           |
//            |              |        committed"    |           |
//            |              |                      |           |
//            |              |                      |           |
//            | -----------  | <- top     --------- + --------  |
//            |              |                      |           |
//            |              |     "used"           |           |
//            |              |                      |           |
//            +--------------+ <- start   ----------+ ----------+

// Note: this is a chunk **descriptor**. The real Payload area lives in metaspace,
// this class lives somewhere else.
class Metachunk {

  // start of chunk memory; NULL if dead.
  MetaWord* _base;

  // Used words.
  size_t _used_words;

  // Size of the region, starting from base, which is guaranteed to be committed. In words.
  //  The actual size of committed regions may actually be larger.
  //
  //  (This is a performance optimization. The underlying VirtualSpaceNode knows
  //   which granules are committed; but we want to avoid having to ask.)
  size_t _committed_words;

  chunklevel_t _level; // aka size.

  // state_free:    free, owned by a ChunkManager
  // state_in_use:  in-use, owned by a MetaspaceArena
  // dead:          just a hollow chunk header without associated memory, owned
  //                 by chunk header pool.
  enum class State : uint8_t {
    Free = 0,
    InUse = 1,
    Dead = 2
  };
  State _state;

  // We need unfortunately a back link to the virtual space node
  // for splitting and merging nodes.
  VirtualSpaceNode* _vsnode;

  // A chunk header is kept in a list:
  // 1 in the list of used chunks inside a MetaspaceArena, if it is in use
  // 2 in the list of free chunks inside a ChunkManager, if it is free
  // 3 in the freelist of unused headers inside the ChunkHeaderPool,
  //   if it is unused (e.g. result of chunk merging) and has no associated
  //   memory area.
  Metachunk* _prev;
  Metachunk* _next;

  // Furthermore, we keep, per chunk, information about the neighboring chunks.
  // This is needed to split and merge chunks.
  //
  // Note: These members can be modified concurrently while a chunk is alive and in use.
  // This can happen if a neighboring chunk is added or removed.
  // This means only read or modify these members under expand lock protection.
  Metachunk* _prev_in_vs;
  Metachunk* _next_in_vs;

  // Commit uncommitted section of the chunk.
  // Fails if we hit a commit limit.
  bool commit_up_to(size_t new_committed_words);

  DEBUG_ONLY(static void assert_have_expand_lock();)

public:

  Metachunk()
    : _base(NULL),
      _used_words(0),
      _committed_words(0),
      _level(chunklevel::ROOT_CHUNK_LEVEL),
      _state(State::Free),
      _vsnode(NULL),
      _prev(NULL), _next(NULL),
      _prev_in_vs(NULL), _next_in_vs(NULL)
  {}

 void clear() {
   _base = NULL;
   _used_words = 0;
   _committed_words = 0;
   _level = chunklevel::ROOT_CHUNK_LEVEL;
   _state = State::Free;
   _vsnode = NULL;
   _prev = NULL;
   _next = NULL;
   _prev_in_vs = NULL;
   _next_in_vs = NULL;
  }

  size_t word_size() const        { return chunklevel::word_size_for_level(_level); }

  MetaWord* base() const          { return _base; }
  MetaWord* top() const           { return base() + _used_words; }
  MetaWord* committed_top() const { return base() + _committed_words; }
  MetaWord* end() const           { return base() + word_size(); }

  // Chunk list wiring
  void set_prev(Metachunk* c)     { _prev = c; }
  Metachunk* prev() const         { return _prev; }
  void set_next(Metachunk* c)     { _next = c; }
  Metachunk* next() const         { return _next; }

  DEBUG_ONLY(bool in_list() const { return _prev != NULL || _next != NULL; })

  // Physical neighbors wiring
  void set_prev_in_vs(Metachunk* c) { DEBUG_ONLY(assert_have_expand_lock()); _prev_in_vs = c; }
  Metachunk* prev_in_vs() const     { DEBUG_ONLY(assert_have_expand_lock()); return _prev_in_vs; }
  void set_next_in_vs(Metachunk* c) { DEBUG_ONLY(assert_have_expand_lock()); _next_in_vs = c; }
  Metachunk* next_in_vs() const     { DEBUG_ONLY(assert_have_expand_lock()); return _next_in_vs; }

  bool is_free() const            { return _state == State::Free; }
  bool is_in_use() const          { return _state == State::InUse; }
  bool is_dead() const            { return _state == State::Dead; }
  void set_free()                 { _state = State::Free; }
  void set_in_use()               { _state = State::InUse; }
  void set_dead()                 { _state = State::Dead; }

  // Return a single char presentation of the state ('f', 'u', 'd')
  char get_state_char() const;

  void inc_level()                { _level++; DEBUG_ONLY(chunklevel::is_valid_level(_level);) }
  void dec_level()                { _level --; DEBUG_ONLY(chunklevel::is_valid_level(_level);) }
  chunklevel_t level() const          { return _level; }

  // Convenience functions for extreme levels.
  bool is_root_chunk() const      { return chunklevel::ROOT_CHUNK_LEVEL == _level; }
  bool is_leaf_chunk() const      { return chunklevel::HIGHEST_CHUNK_LEVEL == _level; }

  VirtualSpaceNode* vsnode() const        { return _vsnode; }

  size_t used_words() const                   { return _used_words; }
  size_t free_words() const                   { return word_size() - used_words(); }
  size_t free_below_committed_words() const   { return committed_words() - used_words(); }
  void reset_used_words()                     { _used_words = 0; }

  size_t committed_words() const      { return _committed_words; }
  void set_committed_words(size_t v);
  bool is_fully_committed() const     { return committed_words() == word_size(); }
  bool is_fully_uncommitted() const   { return committed_words() == 0; }

  // Ensure that chunk is committed up to at least new_committed_words words.
  // Fails if we hit a commit limit.
  bool ensure_committed(size_t new_committed_words);
  bool ensure_committed_locked(size_t new_committed_words);

  bool ensure_fully_committed()           { return ensure_committed(word_size()); }
  bool ensure_fully_committed_locked()    { return ensure_committed_locked(word_size()); }

  // Ensure that the chunk is committed far enough to serve an additional allocation of word_size.
  bool ensure_committed_additional(size_t additional_word_size)   {
    return ensure_committed(used_words() + additional_word_size);
  }

  // Uncommit chunk area. The area must be a common multiple of the
  // commit granule size (in other words, we cannot uncommit chunks smaller than
  // a commit granule size).
  void uncommit();
  void uncommit_locked();

  // Allocation from a chunk

  // Allocate word_size words from this chunk (word_size must be aligned to
  //  allocation_alignment_words).
  //
  // Caller must make sure the chunk is both large enough and committed far enough
  // to hold the allocation. Will always work.
  //
  MetaWord* allocate(size_t request_word_size);

  // Initialize structure for reuse.
  void initialize(VirtualSpaceNode* node, MetaWord* base, chunklevel_t lvl) {
    _vsnode = node; _base = base; _level = lvl;
    _used_words = _committed_words = 0; _state = State::Free;
    _next = _prev = _next_in_vs = _prev_in_vs = NULL;
  }

  // Returns true if this chunk is the leader in its buddy pair, false if not.
  // Do not call for root chunks.
  bool is_leader() const {
    assert(!is_root_chunk(), "Root chunks have no buddy."); // Bit harsh?
    return is_aligned(base(), chunklevel::word_size_for_level(level() - 1) * BytesPerWord);
  }

  //// Debug stuff ////
#ifdef ASSERT
  void verify() const;
  // Verifies linking with neighbors in virtual space. Needs expand lock protection.
  void verify_neighborhood() const;
  void zap_header(uint8_t c = 0x17);

  // Returns true if given pointer points into the payload area of this chunk.
  bool is_valid_pointer(const MetaWord* p) const {
    return base() <= p && p < top();
  }

  // Returns true if given pointer points into the commmitted payload area of this chunk.
  bool is_valid_committed_pointer(const MetaWord* p) const {
    return base() <= p && p < committed_top();
  }

#endif // ASSERT

  void print_on(outputStream* st) const;

};

// Little print helpers: since we often print out chunks, here some convenience macros
#define METACHUNK_FORMAT                "@" PTR_FORMAT ", %c, base " PTR_FORMAT ", level " CHKLVL_FORMAT
#define METACHUNK_FORMAT_ARGS(chunk)    p2i(chunk), chunk->get_state_char(), p2i(chunk->base()), chunk->level()

#define METACHUNK_FULL_FORMAT                "@" PTR_FORMAT ", %c, base " PTR_FORMAT ", level " CHKLVL_FORMAT " (" SIZE_FORMAT "), used: " SIZE_FORMAT ", committed: " SIZE_FORMAT ", committed-free: " SIZE_FORMAT
#define METACHUNK_FULL_FORMAT_ARGS(chunk)    p2i(chunk), chunk->get_state_char(), p2i(chunk->base()), chunk->level(), chunk->word_size(), chunk->used_words(), chunk->committed_words(), chunk->free_below_committed_words()

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_MSMETACHUNK_HPP
