/*
 * Copyright (c) 2023, Red Hat. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceHumongousArea.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

using namespace metaspace::chunklevel;

namespace metaspace {

MetaspaceHumongousArea::MetaspaceHumongousArea() :
    _first(nullptr), _last(nullptr)
{}

// Append a chunk to the tail of the humongous area
void MetaspaceHumongousArea::add_to_tail(Metachunk* c) {
  assert(c->is_root_chunk(), "Not a root chunk");
  assert(_last == nullptr || _last->end() == c->base(), "Must be adjacent chunk");
  if (_first == nullptr) {
    _first = _last = c;
  } else {
    c->set_prev(_last);
    _last->set_next(c);
    _last = c;
  }
}

// Called by the ChunkManager to prepare the chunks in this area for the arena:
// - commit their space
// - allocate from them as far as needed in order for all chunks to show the
//   correct usage numbers
// - set them to "in-use" state
void MetaspaceHumongousArea::prepare_for_arena(size_t word_size) {
  size_t allocated = 0;
  MetaWord* pstart = nullptr;
  for (Metachunk* c = _first; c != nullptr; c = c->next()) {
    const size_t to_allocate_total = word_size - allocated;
    assert(to_allocate_total > 0, "Too many chunks?");
    const size_t portion_size = MIN2(to_allocate_total, c->word_size());

    // Commit. This must work (caller must make sure we have enough commit headroom
    bool ok = c->ensure_committed_locked(portion_size);
    assert(ok, "Failed to commit chunk for humongous area");

    // Set chunk in use (as per ChunkManager protocol, chunks handed out to Arenas are "in-use"
    c->set_in_use();

    // Allocate; make sure the areas we sequentially allocated form a contiguous area
    MetaWord* p = c->allocate(portion_size);
    assert(p != nullptr, "Sanity");
    assert(p == c->base(), "Sanity");
    if (pstart == nullptr) {
      pstart = p;
    }
    assert(pstart + allocated == p, "Not contiguous");
    allocated += portion_size;
  }
}

#ifdef ASSERT
// Verify humongous area:
// - All chunks should be adjacent root chunks
// - If we expect this humongous area to be "ready", it must be committed for expected_word_size words, and used up as much.
void MetaspaceHumongousArea::verify(size_t expected_word_size, bool expect_prepared_for_arena) const {
  const Metachunk* c2 = nullptr;
  size_t used_words = 0;
  size_t committed_words = 0;
  size_t total_words = 0;
  for (const Metachunk* c = _first; c != nullptr; c = c->next()) {
    assert(total_words < expected_word_size, "too many chunks?");
    assert(c2 == nullptr || c2->end() == c->base(), "Chunks must be adjacent");
    assert(c->is_root_chunk(), "Not root chunk");
    if (expect_prepared_for_arena) {
      assert(c->is_in_use(), "Must be marked as in-use");
      if (c->next() != nullptr) { // Not the last chunk
        assert(c->is_fully_committed() &&
               c->is_fully_used(), "Must be fully committed and used up");
      }
    }
    total_words += c->word_size();
    committed_words += c->committed_words();
    used_words += c->used_words();
    c2 = c;
  }
  assert(total_words >= expected_word_size, "Not enough chunks");
  if (expect_prepared_for_arena) {
    assert(committed_words >= expected_word_size, "Not committed enough");
    assert(used_words >= expected_word_size, "Used mismatch");
  }
  assert(_last == c2, "Last chunk mismatch");
}
#endif // ASSERT

void MetaspaceHumongousArea::print_on(outputStream* st) const {
  if (_last != nullptr) {
    st->print_cr("humongous area [" PTR_FORMAT "-" PTR_FORMAT ", " SIZE_FORMAT "): ",
                 p2i(_first->base()), p2i(_last->end()), (size_t)(_last->end() - _first->base()));
    for (const Metachunk* c = _first; c != nullptr; c = c->next()) {
      st->print_cr(METACHUNK_FULL_FORMAT, METACHUNK_FULL_FORMAT_ARGS(c));
    }
  }
}

} // namespace metaspace
