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

#ifndef SHARE_MEMORY_METASPACE_METASPACEHUMONGOUSAREA_HPP
#define SHARE_MEMORY_METASPACE_METASPACEHUMONGOUSAREA_HPP

class outputStream;

namespace metaspace {

class Metachunk;

// Blocks allocated from Metaspace are restricted by the maximum metaspace chunk size
// (root chunks size). But larger allocations may happen, even if they are extremely
// rare. They are typically the result of loading a very inefficiently generated class.
//
// These large ("humongous") allocations are realized by chaining multiple root chunks
// together. Hence they are "supra-chunk" allocations.
//
// Live chunks are kept by the enclosing Arena, and they live as long as the arena lives.
// Supra-chunk allocations, from the viewpoint of an Arena, are indistinguishable from
// the user doing multiple allocations whose containing chunks just happen to be adjacent
// to each other. The arena does not care. When the arena dies, the chunks are released
// together with all other chunks. They will then be given back to the ChunkManager,
// possibly uncommitted, then reused by other arenas.
//
// A humongous allocation spans multiple root chunks. To avoid wasting address space, the
// last chunk of this allocation is split down to the needed size. It will also be used for
// subsequent
//
// Example:
// Arena allocated normal blocks (a), (b), (c), then a humongous block spanning two root
// chunks and extending for a bit into the third chunk. Normal blocks (d) and (e) follow.
// The third chunk happens to be the last added and is therefore the current chunk that
// will house subsequent allocations:
//
//
//  +---------------+
//  |     Arena     |
//  +---------------+
//            |
//            | _chunks
//            |
//        +----------+      +-------.....---+-------.....---+----------+
//        | normal   | ---> | Root          | Root          | current  |
//        | chunk A  |      | chunk B       | chunk C       | chunk    |
//        +----------+      +-------.....---+-------.....---+----------+
//        ^     ^  ^        ^                                    ^  ^
//        a     b  c        |                                    |  e
//                          |                                    |
//                        Start of                           End of humongous
//                        humongous allocation               block; start of block d
//

// MetaspaceHumongousArea is a transient object that describes a humongous area spanning
// multiple chunks; its main purpose is combining code for building a humongous area chunk
// chain, and verification.
class MetaspaceHumongousArea {

  Metachunk* _first, *_last;

public:

  MetaspaceHumongousArea();

  Metachunk* first() { return _first; }
  Metachunk* last()  { return _last; }

  // Append a chunk to the tail of the humongous area
  void add_to_tail(Metachunk* c);

  // Called by the ChunkManager to prepare the chunks in this area for the arena:
  // - commit them as far as needed
  // - allocate from them as far as needed in order for all chunks to show the
  //   correct usage numbers
  // - set them to "in-use" state
  void prepare_for_arena(size_t word_size);

  DEBUG_ONLY(void verify(size_t expected_word_size, bool expect_prepared_for_arena) const;)

  void print_on(outputStream* st) const;
};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METASPACEHUMONGOUSAREA_HPP
