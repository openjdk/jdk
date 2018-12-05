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
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/virtualSpaceList.hpp"
#include "memory/metaspace/virtualSpaceNode.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"

namespace metaspace {


VirtualSpaceList::~VirtualSpaceList() {
  VirtualSpaceListIterator iter(virtual_space_list());
  while (iter.repeat()) {
    VirtualSpaceNode* vsl = iter.get_next();
    delete vsl;
  }
}

void VirtualSpaceList::inc_reserved_words(size_t v) {
  assert_lock_strong(MetaspaceExpand_lock);
  _reserved_words = _reserved_words + v;
}
void VirtualSpaceList::dec_reserved_words(size_t v) {
  assert_lock_strong(MetaspaceExpand_lock);
  _reserved_words = _reserved_words - v;
}

#define assert_committed_below_limit()                        \
  assert(MetaspaceUtils::committed_bytes() <= MaxMetaspaceSize, \
         "Too much committed memory. Committed: " SIZE_FORMAT \
         " limit (MaxMetaspaceSize): " SIZE_FORMAT,           \
          MetaspaceUtils::committed_bytes(), MaxMetaspaceSize);

void VirtualSpaceList::inc_committed_words(size_t v) {
  assert_lock_strong(MetaspaceExpand_lock);
  _committed_words = _committed_words + v;

  assert_committed_below_limit();
}
void VirtualSpaceList::dec_committed_words(size_t v) {
  assert_lock_strong(MetaspaceExpand_lock);
  _committed_words = _committed_words - v;

  assert_committed_below_limit();
}

void VirtualSpaceList::inc_virtual_space_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  _virtual_space_count++;
}

void VirtualSpaceList::dec_virtual_space_count() {
  assert_lock_strong(MetaspaceExpand_lock);
  _virtual_space_count--;
}

// Walk the list of VirtualSpaceNodes and delete
// nodes with a 0 container_count.  Remove Metachunks in
// the node from their respective freelists.
void VirtualSpaceList::purge(ChunkManager* chunk_manager) {
  assert_lock_strong(MetaspaceExpand_lock);
  // Don't use a VirtualSpaceListIterator because this
  // list is being changed and a straightforward use of an iterator is not safe.
  VirtualSpaceNode* purged_vsl = NULL;
  VirtualSpaceNode* prev_vsl = virtual_space_list();
  VirtualSpaceNode* next_vsl = prev_vsl;
  while (next_vsl != NULL) {
    VirtualSpaceNode* vsl = next_vsl;
    DEBUG_ONLY(vsl->verify_container_count();)
    next_vsl = vsl->next();
    // Don't free the current virtual space since it will likely
    // be needed soon.
    if (vsl->container_count() == 0 && vsl != current_virtual_space()) {
      log_trace(gc, metaspace, freelist)("Purging VirtualSpaceNode " PTR_FORMAT " (capacity: " SIZE_FORMAT
                                         ", used: " SIZE_FORMAT ").", p2i(vsl), vsl->capacity_words_in_vs(), vsl->used_words_in_vs());
      DEBUG_ONLY(Atomic::inc(&g_internal_statistics.num_vsnodes_purged));
      // Unlink it from the list
      if (prev_vsl == vsl) {
        // This is the case of the current node being the first node.
        assert(vsl == virtual_space_list(), "Expected to be the first node");
        set_virtual_space_list(vsl->next());
      } else {
        prev_vsl->set_next(vsl->next());
      }

      vsl->purge(chunk_manager);
      dec_reserved_words(vsl->reserved_words());
      dec_committed_words(vsl->committed_words());
      dec_virtual_space_count();
      purged_vsl = vsl;
      delete vsl;
    } else {
      prev_vsl = vsl;
    }
  }
#ifdef ASSERT
  if (purged_vsl != NULL) {
    // List should be stable enough to use an iterator here.
    VirtualSpaceListIterator iter(virtual_space_list());
    while (iter.repeat()) {
      VirtualSpaceNode* vsl = iter.get_next();
      assert(vsl != purged_vsl, "Purge of vsl failed");
    }
  }
#endif
}


// This function looks at the mmap regions in the metaspace without locking.
// The chunks are added with store ordering and not deleted except for at
// unloading time during a safepoint.
VirtualSpaceNode* VirtualSpaceList::find_enclosing_space(const void* ptr) {
  // List should be stable enough to use an iterator here because removing virtual
  // space nodes is only allowed at a safepoint.
  VirtualSpaceListIterator iter(virtual_space_list());
  while (iter.repeat()) {
    VirtualSpaceNode* vsn = iter.get_next();
    if (vsn->contains(ptr)) {
      return vsn;
    }
  }
  return NULL;
}

void VirtualSpaceList::retire_current_virtual_space() {
  assert_lock_strong(MetaspaceExpand_lock);

  VirtualSpaceNode* vsn = current_virtual_space();

  ChunkManager* cm = is_class() ? Metaspace::chunk_manager_class() :
                                  Metaspace::chunk_manager_metadata();

  vsn->retire(cm);
}

VirtualSpaceList::VirtualSpaceList(size_t word_size) :
                                   _virtual_space_list(NULL),
                                   _current_virtual_space(NULL),
                                   _is_class(false),
                                   _reserved_words(0),
                                   _committed_words(0),
                                   _virtual_space_count(0) {
  MutexLockerEx cl(MetaspaceExpand_lock,
                   Mutex::_no_safepoint_check_flag);
  create_new_virtual_space(word_size);
}

VirtualSpaceList::VirtualSpaceList(ReservedSpace rs) :
                                   _virtual_space_list(NULL),
                                   _current_virtual_space(NULL),
                                   _is_class(true),
                                   _reserved_words(0),
                                   _committed_words(0),
                                   _virtual_space_count(0) {
  MutexLockerEx cl(MetaspaceExpand_lock,
                   Mutex::_no_safepoint_check_flag);
  VirtualSpaceNode* class_entry = new VirtualSpaceNode(is_class(), rs);
  bool succeeded = class_entry->initialize();
  if (succeeded) {
    link_vs(class_entry);
  }
}

size_t VirtualSpaceList::free_bytes() {
  return current_virtual_space()->free_words_in_vs() * BytesPerWord;
}

// Allocate another meta virtual space and add it to the list.
bool VirtualSpaceList::create_new_virtual_space(size_t vs_word_size) {
  assert_lock_strong(MetaspaceExpand_lock);

  if (is_class()) {
    assert(false, "We currently don't support more than one VirtualSpace for"
                  " the compressed class space. The initialization of the"
                  " CCS uses another code path and should not hit this path.");
    return false;
  }

  if (vs_word_size == 0) {
    assert(false, "vs_word_size should always be at least _reserve_alignment large.");
    return false;
  }

  // Reserve the space
  size_t vs_byte_size = vs_word_size * BytesPerWord;
  assert_is_aligned(vs_byte_size, Metaspace::reserve_alignment());

  // Allocate the meta virtual space and initialize it.
  VirtualSpaceNode* new_entry = new VirtualSpaceNode(is_class(), vs_byte_size);
  if (!new_entry->initialize()) {
    delete new_entry;
    return false;
  } else {
    assert(new_entry->reserved_words() == vs_word_size,
        "Reserved memory size differs from requested memory size");
    // ensure lock-free iteration sees fully initialized node
    OrderAccess::storestore();
    link_vs(new_entry);
    DEBUG_ONLY(Atomic::inc(&g_internal_statistics.num_vsnodes_created));
    return true;
  }
}

void VirtualSpaceList::link_vs(VirtualSpaceNode* new_entry) {
  if (virtual_space_list() == NULL) {
      set_virtual_space_list(new_entry);
  } else {
    current_virtual_space()->set_next(new_entry);
  }
  set_current_virtual_space(new_entry);
  inc_reserved_words(new_entry->reserved_words());
  inc_committed_words(new_entry->committed_words());
  inc_virtual_space_count();
#ifdef ASSERT
  new_entry->mangle();
#endif
  LogTarget(Trace, gc, metaspace) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    VirtualSpaceNode* vsl = current_virtual_space();
    ResourceMark rm;
    vsl->print_on(&ls);
  }
}

bool VirtualSpaceList::expand_node_by(VirtualSpaceNode* node,
                                      size_t min_words,
                                      size_t preferred_words) {
  size_t before = node->committed_words();

  bool result = node->expand_by(min_words, preferred_words);

  size_t after = node->committed_words();

  // after and before can be the same if the memory was pre-committed.
  assert(after >= before, "Inconsistency");
  inc_committed_words(after - before);

  return result;
}

bool VirtualSpaceList::expand_by(size_t min_words, size_t preferred_words) {
  assert_is_aligned(min_words,       Metaspace::commit_alignment_words());
  assert_is_aligned(preferred_words, Metaspace::commit_alignment_words());
  assert(min_words <= preferred_words, "Invalid arguments");

  const char* const class_or_not = (is_class() ? "class" : "non-class");

  if (!MetaspaceGC::can_expand(min_words, this->is_class())) {
    log_trace(gc, metaspace, freelist)("Cannot expand %s virtual space list.",
              class_or_not);
    return  false;
  }

  size_t allowed_expansion_words = MetaspaceGC::allowed_expansion();
  if (allowed_expansion_words < min_words) {
    log_trace(gc, metaspace, freelist)("Cannot expand %s virtual space list (must try gc first).",
              class_or_not);
    return false;
  }

  size_t max_expansion_words = MIN2(preferred_words, allowed_expansion_words);

  // Commit more memory from the the current virtual space.
  bool vs_expanded = expand_node_by(current_virtual_space(),
                                    min_words,
                                    max_expansion_words);
  if (vs_expanded) {
     log_trace(gc, metaspace, freelist)("Expanded %s virtual space list.",
               class_or_not);
     return true;
  }
  log_trace(gc, metaspace, freelist)("%s virtual space list: retire current node.",
            class_or_not);
  retire_current_virtual_space();

  // Get another virtual space.
  size_t grow_vs_words = MAX2((size_t)VirtualSpaceSize, preferred_words);
  grow_vs_words = align_up(grow_vs_words, Metaspace::reserve_alignment_words());

  if (create_new_virtual_space(grow_vs_words)) {
    if (current_virtual_space()->is_pre_committed()) {
      // The memory was pre-committed, so we are done here.
      assert(min_words <= current_virtual_space()->committed_words(),
          "The new VirtualSpace was pre-committed, so it"
          "should be large enough to fit the alloc request.");
      return true;
    }

    return expand_node_by(current_virtual_space(),
                          min_words,
                          max_expansion_words);
  }

  return false;
}

// Given a chunk, calculate the largest possible padding space which
// could be required when allocating it.
static size_t largest_possible_padding_size_for_chunk(size_t chunk_word_size, bool is_class) {
  const ChunkIndex chunk_type = get_chunk_type_by_size(chunk_word_size, is_class);
  if (chunk_type != HumongousIndex) {
    // Normal, non-humongous chunks are allocated at chunk size
    // boundaries, so the largest padding space required would be that
    // minus the smallest chunk size.
    const size_t smallest_chunk_size = is_class ? ClassSpecializedChunk : SpecializedChunk;
    return chunk_word_size - smallest_chunk_size;
  } else {
    // Humongous chunks are allocated at smallest-chunksize
    // boundaries, so there is no padding required.
    return 0;
  }
}


Metachunk* VirtualSpaceList::get_new_chunk(size_t chunk_word_size, size_t suggested_commit_granularity) {

  // Allocate a chunk out of the current virtual space.
  Metachunk* next = current_virtual_space()->get_chunk_vs(chunk_word_size);

  if (next != NULL) {
    return next;
  }

  // The expand amount is currently only determined by the requested sizes
  // and not how much committed memory is left in the current virtual space.

  // We must have enough space for the requested size and any
  // additional reqired padding chunks.
  const size_t size_for_padding = largest_possible_padding_size_for_chunk(chunk_word_size, this->is_class());

  size_t min_word_size       = align_up(chunk_word_size + size_for_padding, Metaspace::commit_alignment_words());
  size_t preferred_word_size = align_up(suggested_commit_granularity, Metaspace::commit_alignment_words());
  if (min_word_size >= preferred_word_size) {
    // Can happen when humongous chunks are allocated.
    preferred_word_size = min_word_size;
  }

  bool expanded = expand_by(min_word_size, preferred_word_size);
  if (expanded) {
    next = current_virtual_space()->get_chunk_vs(chunk_word_size);
    assert(next != NULL, "The allocation was expected to succeed after the expansion");
  }

   return next;
}

void VirtualSpaceList::print_on(outputStream* st, size_t scale) const {
  st->print_cr(SIZE_FORMAT " nodes, current node: " PTR_FORMAT,
      _virtual_space_count, p2i(_current_virtual_space));
  VirtualSpaceListIterator iter(virtual_space_list());
  while (iter.repeat()) {
    st->cr();
    VirtualSpaceNode* node = iter.get_next();
    node->print_on(st, scale);
  }
}

void VirtualSpaceList::print_map(outputStream* st) const {
  VirtualSpaceNode* list = virtual_space_list();
  VirtualSpaceListIterator iter(list);
  unsigned i = 0;
  while (iter.repeat()) {
    st->print_cr("Node %u:", i);
    VirtualSpaceNode* node = iter.get_next();
    node->print_map(st, this->is_class());
    i ++;
  }
}

} // namespace metaspace

