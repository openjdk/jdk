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

#include "precompiled.hpp"
#include "logging/log.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metaspaceSettings.hpp"
#include "memory/metaspace/virtualSpaceNode.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"

namespace metaspace {

// Return a single char presentation of the state ('f', 'u', 'd')
char Metachunk::get_state_char() const {
  switch (_state) {
  case State::Free:   return 'f';
  case State::InUse:  return 'u';
  case State::Dead:   return 'd';
  }
  return '?';
}

#ifdef ASSERT
void Metachunk::assert_have_expand_lock() {
  assert_lock_strong(MetaspaceExpand_lock);
}
#endif

// Commit space in the chunk, so that _committed_words is at least at
//  new_committed_words.
//  new_committed_words has to fall within the limits of the chunk and has
//  to be larger than the current commit boundary.
// Fails if we hit a commit limit.
bool Metachunk::commit_up_to(size_t new_committed_words) {

  assert(new_committed_words > _committed_words, "nothing to do.");
  assert(new_committed_words <= word_size(), "parameter error");

  // lock protection needed since it may modify neighboring chunks.
  assert_lock_strong(MetaspaceExpand_lock);

  // This function commits additional space within the Metachunk. Committing is done
  //  for whole commit granules only.
  //
  // We have two cases:
  //
  // Case 1: Chunk is larger than a commit granule:
  //
  //  We commit additional granules as are needed to reach the requested
  //   "new_committed_words" commit boundary. That may over-reach, so the
  //   resulting commit boundary may be higher.
  //  The commit boundary has to be at a granule border. That guarantees
  //   that we never re-commit already committed pages (see remarks below).
  //
  // Example: A chunk covering 8 granules. Before this call, we had three
  //  granules committed and partly used. new_committed_words falls into
  //  the fifth granule, causing us to commit  two additional granules:
  //
  //  granule 1    granule 2    granule 3    granule 4    granule 5    granule 6    granule 7    granule 8
  // +------------+------------+------------+------------+------------+------------+------------+------------+
  // |                                      |                         |                                      |
  // | <.... old committed space .........> | <.... newly committed   | <.... uncommitted .................> |
  // |                                      |               space ..> |                                      |
  // | <... live data ...........> |        |                         |                                      |
  // +------------+------------+---|--------+------------+------|-----+------------+------------+------------+
  //                               |                            |
  // ^                             ^        ^                   |     ^                                      ^
  // base                          used     old                 |     new                                    chunk end
  //                                        _committed_words    |      _committed_words
  //                                                            |
  //                                                           new_committed_words
  //
  // Case 2: Chunk is smaller than a commit granule.
  //
  //  It shares a single granule with a number of neighbors. Committing this
  //  granule will affect neighbors.
  //
  // Example: calling ensure_committed() on chunk B may commit the granule, thus
  //  committing space for chunk A and C as well.
  //
  // +---------------------- one granule ----------------------------+
  // |                                                               |
  //     chunk A         chunk B                 chunk C
  // +---------------+---------------+-------------------------------+
  // |               |               |                               |
  // | <committed    | <committed>   |  <committed as well>          |
  // |   as well>    |               |                               |
  // |               |               |                               |
  // +---------------+---------------+-------------------------------+
  //                      ^
  //                      ensure_committed()
  //
  ///////
  //
  // Safety note: when committing a memory range, that range will be remapped. If that
  //  range did contain committed pages, those are now lost. Therefore we must ensure
  //  that we never re-commit parts of chunks which are already in use.
  //
  // Happily this is no concern:
  //
  // Chunks smaller than a granule have in-granule neighbors which we would
  //  affect when committing/ uncommitting the underlying granule. But:
  //  - we never uncommit chunks smaller than a single granule.
  //  - when committing an uncommitted small chunk, there is no problem since
  //    all in-granule-neighbors were uncommitted and cannot contain live
  //    data we could wipe by re-committing.
  //
  // Chunks equal or larger than a commit granule occupy whole granules, so
  //  committing them does not affect neighbors. As long as we take care to move
  //  the commit boundary inside those chunks along granule borders, we cannot
  //  accidentally re-commit used pages.
  //

#ifdef ASSERT
  if (word_size() >= Settings::commit_granule_words()) {
    // case (1)
    assert(is_aligned(base(), Settings::commit_granule_bytes()) &&
           is_aligned(end(), Settings::commit_granule_bytes()),
           "Chunks larger than a commit granule must cover whole granules.");
    assert(is_aligned(_committed_words, Settings::commit_granule_words()),
           "The commit boundary must be aligned to commit granule size");
    assert(_used_words <= _committed_words, "Sanity");
  } else {
    // case (2)
    // Small chunks (< granule) are either fully committed or fully uncommitted.
    // Moreover, at this point they cannot be committed, since we only do this
    // if the new commit boundary is higher than the current one.
    assert(_committed_words == 0, "Sanity");
  }
#endif

  const size_t commit_from = _committed_words;
  const size_t commit_to =   MIN2(align_up(new_committed_words, Settings::commit_granule_words()), word_size());
  assert(commit_from >= used_words(), "Sanity");
  assert(commit_to <= word_size(), "Sanity");
  if (commit_to > commit_from) {
    log_debug(metaspace)("Chunk " METACHUNK_FORMAT ": attempting to move commit line to "
                         SIZE_FORMAT " words.", METACHUNK_FORMAT_ARGS(this), commit_to);
    if (!_vsnode->ensure_range_is_committed(base() + commit_from, commit_to - commit_from)) {
      DEBUG_ONLY(verify();)
      return false;
    }
  }

  // Remember how far we have committed.
  _committed_words = commit_to;

  // If this chunk was smaller than a granule we just committed, we committed the memory
  //  underlying the in-granule neighbors as well. Lets correct their commit boundaries.
  // Note that this is not strictly necessary: these boundaries would silently be corrected
  //  the first time someone were to use those chunks. But doing it now keeps statistics happy
  //  and we save some work later on.
  if (word_size() < Settings::commit_granule_words()) {
    const MetaWord* granule_start = align_down(base(), Settings::commit_granule_bytes());
    const MetaWord* granule_end = granule_start + Settings::commit_granule_words();
    assert(granule_end >= end(), "Sanity");
    for (Metachunk* c = prev_in_vs(); c != NULL && c->base() >= granule_start; c = c->prev_in_vs()) {
      assert(c->committed_words() == 0, "neighbor was already committed?");
      c->_committed_words = c->word_size();
    }
    for (Metachunk* c = next_in_vs(); c != NULL && c->end() <= granule_end; c = c->next_in_vs()) {
      assert(c->committed_words() == 0, "neighbor was already committed?");
      c->_committed_words = c->word_size();
    }
  }

  DEBUG_ONLY(verify();)
  DEBUG_ONLY(verify_neighborhood();)
  return true;
}

// Ensure that chunk is committed up to at least new_committed_words words.
// Fails if we hit a commit limit.
bool Metachunk::ensure_committed(size_t new_committed_words) {
  bool rc = true;
  if (new_committed_words > committed_words()) {
    MutexLocker cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
    rc = commit_up_to(new_committed_words);
  }
  return rc;
}

bool Metachunk::ensure_committed_locked(size_t new_committed_words) {
  // the .._locked() variant should be called if we own the lock already.
  assert_lock_strong(MetaspaceExpand_lock);
  bool rc = true;
  if (new_committed_words > committed_words()) {
    rc = commit_up_to(new_committed_words);
  }
  return rc;
}

// Uncommit chunk area. The area must be a common multiple of the
// commit granule size (in other words, we cannot uncommit chunks smaller than
// a commit granule size).
void Metachunk::uncommit() {
  MutexLocker cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  uncommit_locked();
}

void Metachunk::uncommit_locked() {
  // Only uncommit chunks which are free, have no used words set (extra precaution) and are equal or larger in size than a single commit granule.
  assert_lock_strong(MetaspaceExpand_lock);
  assert(_state == State::Free && _used_words == 0 && word_size() >= Settings::commit_granule_words(),
         "Only free chunks equal or larger than commit granule size can be uncommitted "
         "(chunk " METACHUNK_FULL_FORMAT ").", METACHUNK_FULL_FORMAT_ARGS(this));
  if (word_size() >= Settings::commit_granule_words()) {
    _vsnode->uncommit_range(base(), word_size());
    _committed_words = 0;
  }
}
void Metachunk::set_committed_words(size_t v) {
  // Set committed words. Since we know that we only commit whole commit granules, we can round up v here.
  v = MIN2(align_up(v, Settings::commit_granule_words()), word_size());
 _committed_words = v;
}

// Allocate word_size words from this chunk (word_size must be aligned to
//  allocation_alignment_words).
//
// Caller must make sure the chunk is both large enough and committed far enough
// to hold the allocation. Will always work.
//
MetaWord* Metachunk::allocate(size_t request_word_size) {
  // Caller must have made sure this works
  assert(free_words() >= request_word_size, "Chunk too small.");
  assert(free_below_committed_words() >= request_word_size, "Chunk not committed.");
  MetaWord* const p = top();
  _used_words += request_word_size;
  SOMETIMES(verify();)
  return p;
}

#ifdef ASSERT

// Zap this structure.
void Metachunk::zap_header(uint8_t c) {
  memset(this, c, sizeof(Metachunk));
}

// Checks the chunk local commit watermark against the underlying commit mask.
void Metachunk::verify_committed_words() const {
  assert_lock_strong(MetaspaceExpand_lock);
  if (_committed_words > 0) {
    assert(_vsnode->is_range_fully_committed(base(), committed_words()),
           "commit mismatch - Chunk: " METACHUNK_FULL_FORMAT ".",
           METACHUNK_FULL_FORMAT_ARGS(this));
  }
}

// Verifies linking with neighbors in virtual space.
// Can only be done under expand lock protection.
void Metachunk::verify_neighborhood() const {
  assert_lock_strong(MetaspaceExpand_lock);
  assert(!is_dead(), "Do not call on dead chunks.");
  verify_committed_words();
  if (is_root_chunk()) {
    // Root chunks are all alone in the world.
    assert(next_in_vs() == NULL || prev_in_vs() == NULL, "Root chunks should have no neighbors");
  } else {
    // Non-root chunks have neighbors, at least one, possibly two.
    assert(next_in_vs() != NULL || prev_in_vs() != NULL,
           "A non-root chunk should have neighbors (chunk @" PTR_FORMAT
           ", base " PTR_FORMAT ", level " CHKLVL_FORMAT ".",
           p2i(this), p2i(base()), level());
    if (prev_in_vs() != NULL) {
      assert(prev_in_vs()->end() == base(),
             "Chunk " METACHUNK_FULL_FORMAT ": should be adjacent to predecessor: " METACHUNK_FULL_FORMAT ".",
             METACHUNK_FULL_FORMAT_ARGS(this), METACHUNK_FULL_FORMAT_ARGS(prev_in_vs()));
      assert(prev_in_vs()->next_in_vs() == this,
             "Chunk " METACHUNK_FULL_FORMAT ": broken link to left neighbor: " METACHUNK_FULL_FORMAT " (" PTR_FORMAT ").",
             METACHUNK_FULL_FORMAT_ARGS(this), METACHUNK_FULL_FORMAT_ARGS(prev_in_vs()), p2i(prev_in_vs()->next_in_vs()));
      prev_in_vs()->verify_committed_words();
    }
    if (next_in_vs() != NULL) {
      assert(end() == next_in_vs()->base(),
             "Chunk " METACHUNK_FULL_FORMAT ": should be adjacent to successor: " METACHUNK_FULL_FORMAT ".",
             METACHUNK_FULL_FORMAT_ARGS(this), METACHUNK_FULL_FORMAT_ARGS(next_in_vs()));
      assert(next_in_vs()->prev_in_vs() == this,
             "Chunk " METACHUNK_FULL_FORMAT ": broken link to right neighbor: " METACHUNK_FULL_FORMAT " (" PTR_FORMAT ").",
             METACHUNK_FULL_FORMAT_ARGS(this), METACHUNK_FULL_FORMAT_ARGS(next_in_vs()), p2i(next_in_vs()->prev_in_vs()));
      next_in_vs()->verify_committed_words();
    }

    // One of the neighbors must be the buddy. It can be whole or splintered.

    // The chunk following us or preceeding us may be our buddy or a splintered part of it.
    Metachunk* buddy = is_leader() ? next_in_vs() : prev_in_vs();
    assert(buddy != NULL, "Missing neighbor.");
    assert(!buddy->is_dead(), "Invalid buddy state.");

    // This neighbor is either or buddy (same level) or a splinter of our buddy - hence
    // the level can never be smaller (aka the chunk size cannot be larger).
    assert(buddy->level() >= level(), "Wrong level.");

    if (buddy->level() == level()) {
      // If the buddy is of the same size as us, it is unsplintered.
      assert(buddy->is_leader() == !is_leader(),
             "Only one chunk can be leader in a pair");

      // When direct buddies are neighbors, one or both should be in use, otherwise they should
      // have been merged.
      // But since we call this verification function from internal functions where we are about to merge or just did split,
      // do not test this. We have RootChunkArea::verify_area_is_ideally_merged() for testing that.
      if (is_leader()) {
        assert(buddy->base() == end(), "Sanity");
        assert(is_aligned(base(), word_size() * 2 * BytesPerWord), "Sanity");
      } else {
        assert(buddy->end() == base(), "Sanity");
        assert(is_aligned(buddy->base(), word_size() * 2 * BytesPerWord), "Sanity");
      }
    } else {
      // Buddy, but splintered, and this is a part of it.
      if (is_leader()) {
        assert(buddy->base() == end(), "Sanity");
      } else {
        assert(buddy->end() > (base() - word_size()), "Sanity");
      }
    }
  }
}

volatile MetaWord dummy = 0;

void Metachunk::verify() const {
  // Note. This should be called under CLD lock protection.

  // We can verify everything except the _prev_in_vs/_next_in_vs pair.
  // This is because neighbor chunks may be added concurrently, so we cannot rely
  //  on the content of _next_in_vs/_prev_in_vs unless we have the expand lock.
  assert(!is_dead(), "Do not call on dead chunks.");
  if (is_free()) {
    assert(used_words() == 0, "free chunks are not used.");
  }

  // Note: only call this on a life Metachunk.
  chunklevel::check_valid_level(level());

  assert(base() != NULL, "No base ptr");
  assert(committed_words() >= used_words(),
         "mismatch: committed: " SIZE_FORMAT ", used: " SIZE_FORMAT ".",
         committed_words(), used_words());
  assert(word_size() >= committed_words(),
         "mismatch: word_size: " SIZE_FORMAT ", committed: " SIZE_FORMAT ".",
         word_size(), committed_words());

  // Test base pointer
  assert(base() != NULL, "Base pointer NULL");
  assert(vsnode() != NULL, "No space");
  vsnode()->check_pointer(base());

  // Starting address shall be aligned to chunk size.
  const size_t required_alignment = word_size() * sizeof(MetaWord);
  assert_is_aligned(base(), required_alignment);

  // Test accessing the committed area.
  SOMETIMES(
    if (_committed_words > 0) {
      for (const MetaWord* p = _base; p < _base + _committed_words; p += os::vm_page_size()) {
        dummy = *p;
      }
      dummy = *(_base + _committed_words - 1);
    }
  )
}
#endif // ASSERT

void Metachunk::print_on(outputStream* st) const {
  // Note: must also work with invalid/random data. (e.g. do not call word_size())
  st->print("Chunk @" PTR_FORMAT ", state %c, base " PTR_FORMAT ", "
            "level " CHKLVL_FORMAT " (" SIZE_FORMAT " words), "
            "used " SIZE_FORMAT " words, committed " SIZE_FORMAT " words.",
            p2i(this), get_state_char(), p2i(base()), level(),
            (chunklevel::is_valid_level(level()) ? chunklevel::word_size_for_level(level()) : (size_t)-1),
            used_words(), committed_words());
}

} // namespace metaspace

