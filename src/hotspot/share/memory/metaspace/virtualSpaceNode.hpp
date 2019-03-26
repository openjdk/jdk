/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_VIRTUALSPACENODE_HPP
#define SHARE_MEMORY_METASPACE_VIRTUALSPACENODE_HPP

#include "memory/virtualspace.hpp"
#include "memory/memRegion.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;

namespace metaspace {

class Metachunk;
class ChunkManager;
class OccupancyMap;

// A VirtualSpaceList node.
class VirtualSpaceNode : public CHeapObj<mtClass> {
  friend class VirtualSpaceList;

  // Link to next VirtualSpaceNode
  VirtualSpaceNode* _next;

  // Whether this node is contained in class or metaspace.
  const bool _is_class;

  // total in the VirtualSpace
  ReservedSpace _rs;
  VirtualSpace _virtual_space;
  MetaWord* _top;
  // count of chunks contained in this VirtualSpace
  uintx _container_count;

  OccupancyMap* _occupancy_map;

  // Convenience functions to access the _virtual_space
  char* low()  const { return virtual_space()->low(); }
  char* high() const { return virtual_space()->high(); }

  // The first Metachunk will be allocated at the bottom of the
  // VirtualSpace
  Metachunk* first_chunk() { return (Metachunk*) bottom(); }

  // Committed but unused space in the virtual space
  size_t free_words_in_vs() const;

  // True if this node belongs to class metaspace.
  bool is_class() const { return _is_class; }

  // Helper function for take_from_committed: allocate padding chunks
  // until top is at the given address.
  void allocate_padding_chunks_until_top_is_at(MetaWord* target_top);

 public:

  VirtualSpaceNode(bool is_class, size_t byte_size);
  VirtualSpaceNode(bool is_class, ReservedSpace rs) :
    _next(NULL), _is_class(is_class), _rs(rs), _top(NULL), _container_count(0), _occupancy_map(NULL) {}
  ~VirtualSpaceNode();

  // Convenience functions for logical bottom and end
  MetaWord* bottom() const { return (MetaWord*) _virtual_space.low(); }
  MetaWord* end() const { return (MetaWord*) _virtual_space.high(); }

  const OccupancyMap* occupancy_map() const { return _occupancy_map; }
  OccupancyMap* occupancy_map() { return _occupancy_map; }

  bool contains(const void* ptr) { return ptr >= low() && ptr < high(); }

  size_t reserved_words() const  { return _virtual_space.reserved_size() / BytesPerWord; }
  size_t committed_words() const { return _virtual_space.actual_committed_size() / BytesPerWord; }

  bool is_pre_committed() const { return _virtual_space.special(); }

  // address of next available space in _virtual_space;
  // Accessors
  VirtualSpaceNode* next() { return _next; }
  void set_next(VirtualSpaceNode* v) { _next = v; }

  void set_top(MetaWord* v) { _top = v; }

  // Accessors
  VirtualSpace* virtual_space() const { return (VirtualSpace*) &_virtual_space; }

  // Returns true if "word_size" is available in the VirtualSpace
  bool is_available(size_t word_size) { return word_size <= pointer_delta(end(), _top, sizeof(MetaWord)); }

  MetaWord* top() const { return _top; }
  void inc_top(size_t word_size) { _top += word_size; }

  uintx container_count() { return _container_count; }
  void inc_container_count();
  void dec_container_count();

  // used and capacity in this single entry in the list
  size_t used_words_in_vs() const;
  size_t capacity_words_in_vs() const;

  bool initialize();

  // get space from the virtual space
  Metachunk* take_from_committed(size_t chunk_word_size);

  // Allocate a chunk from the virtual space and return it.
  Metachunk* get_chunk_vs(size_t chunk_word_size);

  // Expands the committed space by at least min_words words.
  bool expand_by(size_t min_words, size_t preferred_words);

  // In preparation for deleting this node, remove all the chunks
  // in the node from any freelist.
  void purge(ChunkManager* chunk_manager);

  // If an allocation doesn't fit in the current node a new node is created.
  // Allocate chunks out of the remaining committed space in this node
  // to avoid wasting that memory.
  // This always adds up because all the chunk sizes are multiples of
  // the smallest chunk size.
  void retire(ChunkManager* chunk_manager);

  void print_on(outputStream* st) const                 { print_on(st, K); }
  void print_on(outputStream* st, size_t scale) const;
  void print_map(outputStream* st, bool is_class) const;

  // Debug support
  DEBUG_ONLY(void mangle();)
  // Verify counters and basic structure. Slow mode: verify all chunks in depth and occupancy map.
  DEBUG_ONLY(void verify(bool slow);)
  // Verify that all free chunks in this node are ideally merged
  // (there should not be multiple small chunks where a large chunk could exist.)
  DEBUG_ONLY(void verify_free_chunks_are_ideally_merged();)

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_VIRTUALSPACENODE_HPP
