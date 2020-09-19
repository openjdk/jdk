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

#include "precompiled.hpp"

#include "memory/metaspace/msCommitMask.hpp"
#include "memory/metaspace/msCommon.hpp"
#include "memory/metaspace/msSettings.hpp"
#include "runtime/stubRoutines.hpp"

#include "utilities/align.hpp"
#include "utilities/debug.hpp"

namespace metaspace {

CommitMask::CommitMask(const MetaWord* start, size_t word_size)
  : CHeapBitMap(mask_size(word_size, Settings::commit_granule_words()))
  , _base(start)
  , _word_size(word_size)
  , _words_per_bit(Settings::commit_granule_words())
{
  assert(_word_size > 0 && _words_per_bit > 0 &&
         is_aligned(_word_size, _words_per_bit), "Sanity");
}

#ifdef ASSERT

void CommitMask::verify() const {

  // Walk the whole commit mask.
  // For each 1 bit, check if the associated granule is accessible.
  // For each 0 bit, check if the associated granule is not accessible. Slow mode only.

  assert(_base != NULL && _word_size > 0 && _words_per_bit > 0, "Sanity");
  assert_is_aligned(_base, _words_per_bit * BytesPerWord);
  assert_is_aligned(_word_size, _words_per_bit);

}

#endif // ASSERT

void CommitMask::print_on(outputStream* st) const {

  st->print("commit mask, base " PTR_FORMAT ":", p2i(base()));

  for (idx_t i = 0; i < size(); i++) {
    st->print("%c", at(i) ? 'X' : '-');
  }

  st->cr();

}

} // namespace metaspace

