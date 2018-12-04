/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_BINARYTREEDICTIONARY_INLINE_HPP
#define SHARE_VM_MEMORY_BINARYTREEDICTIONARY_INLINE_HPP

#include "gc/shared/spaceDecorator.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/binaryTreeDictionary.hpp"
#include "memory/freeList.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutex.hpp"
#include "runtime/globals.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

////////////////////////////////////////////////////////////////////////////////
// A binary tree based search structure for free blocks.
// This is currently used in the Concurrent Mark&Sweep implementation.
////////////////////////////////////////////////////////////////////////////////

template <class Chunk_t, class FreeList_t>
TreeChunk<Chunk_t, FreeList_t>* TreeChunk<Chunk_t, FreeList_t>::as_TreeChunk(Chunk_t* fc) {
  // Do some assertion checking here.
  return (TreeChunk<Chunk_t, FreeList_t>*) fc;
}

template <class Chunk_t, class FreeList_t>
void TreeChunk<Chunk_t, FreeList_t>::verify_tree_chunk_list() const {
  TreeChunk<Chunk_t, FreeList_t>* nextTC = (TreeChunk<Chunk_t, FreeList_t>*)next();
  if (prev() != NULL) { // interior list node shouldn't have tree fields
    guarantee(embedded_list()->parent() == NULL && embedded_list()->left() == NULL &&
              embedded_list()->right()  == NULL, "should be clear");
  }
  if (nextTC != NULL) {
    guarantee(as_TreeChunk(nextTC->prev()) == this, "broken chain");
    guarantee(nextTC->size() == size(), "wrong size");
    nextTC->verify_tree_chunk_list();
  }
}

template <class Chunk_t, class FreeList_t>
TreeList<Chunk_t, FreeList_t>::TreeList() : _parent(NULL),
  _left(NULL), _right(NULL) {}

template <class Chunk_t, class FreeList_t>
TreeList<Chunk_t, FreeList_t>*
TreeList<Chunk_t, FreeList_t>::as_TreeList(TreeChunk<Chunk_t,FreeList_t>* tc) {
  // This first free chunk in the list will be the tree list.
  assert((tc->size() >= (TreeChunk<Chunk_t, FreeList_t>::min_size())),
    "Chunk is too small for a TreeChunk");
  TreeList<Chunk_t, FreeList_t>* tl = tc->embedded_list();
  tl->initialize();
  tc->set_list(tl);
  tl->set_size(tc->size());
  tl->link_head(tc);
  tl->link_tail(tc);
  tl->set_count(1);
  assert(tl->parent() == NULL, "Should be clear");
  return tl;
}

template <class Chunk_t, class FreeList_t>
TreeList<Chunk_t, FreeList_t>*
TreeList<Chunk_t, FreeList_t>::as_TreeList(HeapWord* addr, size_t size) {
  TreeChunk<Chunk_t, FreeList_t>* tc = (TreeChunk<Chunk_t, FreeList_t>*) addr;
  assert((size >= TreeChunk<Chunk_t, FreeList_t>::min_size()),
    "Chunk is too small for a TreeChunk");
  // The space will have been mangled initially but
  // is not remangled when a Chunk_t is returned to the free list
  // (since it is used to maintain the chunk on the free list).
  tc->assert_is_mangled();
  tc->set_size(size);
  tc->link_prev(NULL);
  tc->link_next(NULL);
  TreeList<Chunk_t, FreeList_t>* tl = TreeList<Chunk_t, FreeList_t>::as_TreeList(tc);
  return tl;
}


template <class Chunk_t, class FreeList_t>
TreeList<Chunk_t, FreeList_t>*
TreeList<Chunk_t, FreeList_t>::get_better_list(
  BinaryTreeDictionary<Chunk_t, FreeList_t>* dictionary) {
  return this;
}

template <class Chunk_t, class FreeList_t>
TreeList<Chunk_t, FreeList_t>* TreeList<Chunk_t, FreeList_t>::remove_chunk_replace_if_needed(TreeChunk<Chunk_t, FreeList_t>* tc) {

  TreeList<Chunk_t, FreeList_t>* retTL = this;
  Chunk_t* list = head();
  assert(!list || list != list->next(), "Chunk on list twice");
  assert(tc != NULL, "Chunk being removed is NULL");
  assert(parent() == NULL || this == parent()->left() ||
    this == parent()->right(), "list is inconsistent");
  assert(tc->is_free(), "Header is not marked correctly");
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");

  Chunk_t* prevFC = tc->prev();
  TreeChunk<Chunk_t, FreeList_t>* nextTC = TreeChunk<Chunk_t, FreeList_t>::as_TreeChunk(tc->next());
  assert(list != NULL, "should have at least the target chunk");

  // Is this the first item on the list?
  if (tc == list) {
    // The "getChunk..." functions for a TreeList<Chunk_t, FreeList_t> will not return the
    // first chunk in the list unless it is the last chunk in the list
    // because the first chunk is also acting as the tree node.
    // When coalescing happens, however, the first chunk in the a tree
    // list can be the start of a free range.  Free ranges are removed
    // from the free lists so that they are not available to be
    // allocated when the sweeper yields (giving up the free list lock)
    // to allow mutator activity.  If this chunk is the first in the
    // list and is not the last in the list, do the work to copy the
    // TreeList<Chunk_t, FreeList_t> from the first chunk to the next chunk and update all
    // the TreeList<Chunk_t, FreeList_t> pointers in the chunks in the list.
    if (nextTC == NULL) {
      assert(prevFC == NULL, "Not last chunk in the list");
      set_tail(NULL);
      set_head(NULL);
    } else {
      // copy embedded list.
      nextTC->set_embedded_list(tc->embedded_list());
      retTL = nextTC->embedded_list();
      // Fix the pointer to the list in each chunk in the list.
      // This can be slow for a long list.  Consider having
      // an option that does not allow the first chunk on the
      // list to be coalesced.
      for (TreeChunk<Chunk_t, FreeList_t>* curTC = nextTC; curTC != NULL;
          curTC = TreeChunk<Chunk_t, FreeList_t>::as_TreeChunk(curTC->next())) {
        curTC->set_list(retTL);
      }
      // Fix the parent to point to the new TreeList<Chunk_t, FreeList_t>.
      if (retTL->parent() != NULL) {
        if (this == retTL->parent()->left()) {
          retTL->parent()->set_left(retTL);
        } else {
          assert(this == retTL->parent()->right(), "Parent is incorrect");
          retTL->parent()->set_right(retTL);
        }
      }
      // Fix the children's parent pointers to point to the
      // new list.
      assert(right() == retTL->right(), "Should have been copied");
      if (retTL->right() != NULL) {
        retTL->right()->set_parent(retTL);
      }
      assert(left() == retTL->left(), "Should have been copied");
      if (retTL->left() != NULL) {
        retTL->left()->set_parent(retTL);
      }
      retTL->link_head(nextTC);
      assert(nextTC->is_free(), "Should be a free chunk");
    }
  } else {
    if (nextTC == NULL) {
      // Removing chunk at tail of list
      this->link_tail(prevFC);
    }
    // Chunk is interior to the list
    prevFC->link_after(nextTC);
  }

  // Below this point the embedded TreeList<Chunk_t, FreeList_t> being used for the
  // tree node may have changed. Don't use "this"
  // TreeList<Chunk_t, FreeList_t>*.
  // chunk should still be a free chunk (bit set in _prev)
  assert(!retTL->head() || retTL->size() == retTL->head()->size(),
    "Wrong sized chunk in list");
  debug_only(
    tc->link_prev(NULL);
    tc->link_next(NULL);
    tc->set_list(NULL);
    bool prev_found = false;
    bool next_found = false;
    for (Chunk_t* curFC = retTL->head();
         curFC != NULL; curFC = curFC->next()) {
      assert(curFC != tc, "Chunk is still in list");
      if (curFC == prevFC) {
        prev_found = true;
      }
      if (curFC == nextTC) {
        next_found = true;
      }
    }
    assert(prevFC == NULL || prev_found, "Chunk was lost from list");
    assert(nextTC == NULL || next_found, "Chunk was lost from list");
    assert(retTL->parent() == NULL ||
           retTL == retTL->parent()->left() ||
           retTL == retTL->parent()->right(),
           "list is inconsistent");
  )
  retTL->decrement_count();

  assert(tc->is_free(), "Should still be a free chunk");
  assert(retTL->head() == NULL || retTL->head()->prev() == NULL,
    "list invariant");
  assert(retTL->tail() == NULL || retTL->tail()->next() == NULL,
    "list invariant");
  return retTL;
}

template <class Chunk_t, class FreeList_t>
void TreeList<Chunk_t, FreeList_t>::return_chunk_at_tail(TreeChunk<Chunk_t, FreeList_t>* chunk) {
  assert(chunk != NULL, "returning NULL chunk");
  assert(chunk->list() == this, "list should be set for chunk");
  assert(tail() != NULL, "The tree list is embedded in the first chunk");
  // which means that the list can never be empty.
  // This is expensive for metaspace
  assert(!FLSVerifyDictionary || !this->verify_chunk_in_free_list(chunk), "Double entry");
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");

  Chunk_t* fc = tail();
  fc->link_after(chunk);
  this->link_tail(chunk);

  assert(!tail() || size() == tail()->size(), "Wrong sized chunk in list");
  FreeList_t::increment_count();
  debug_only(this->increment_returned_bytes_by(chunk->size()*sizeof(HeapWord));)
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");
}

// Add this chunk at the head of the list.  "At the head of the list"
// is defined to be after the chunk pointer to by head().  This is
// because the TreeList<Chunk_t, FreeList_t> is embedded in the first TreeChunk<Chunk_t, FreeList_t> in the
// list.  See the definition of TreeChunk<Chunk_t, FreeList_t>.
template <class Chunk_t, class FreeList_t>
void TreeList<Chunk_t, FreeList_t>::return_chunk_at_head(TreeChunk<Chunk_t, FreeList_t>* chunk) {
  assert(chunk->list() == this, "list should be set for chunk");
  assert(head() != NULL, "The tree list is embedded in the first chunk");
  assert(chunk != NULL, "returning NULL chunk");
  // This is expensive for metaspace
  assert(!FLSVerifyDictionary || !this->verify_chunk_in_free_list(chunk), "Double entry");
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");

  Chunk_t* fc = head()->next();
  if (fc != NULL) {
    chunk->link_after(fc);
  } else {
    assert(tail() == NULL, "List is inconsistent");
    this->link_tail(chunk);
  }
  head()->link_after(chunk);
  assert(!head() || size() == head()->size(), "Wrong sized chunk in list");
  FreeList_t::increment_count();
  debug_only(this->increment_returned_bytes_by(chunk->size()*sizeof(HeapWord));)
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");
}

template <class Chunk_t, class FreeList_t>
void TreeChunk<Chunk_t, FreeList_t>::assert_is_mangled() const {
  assert((ZapUnusedHeapArea &&
          SpaceMangler::is_mangled((HeapWord*) Chunk_t::size_addr()) &&
          SpaceMangler::is_mangled((HeapWord*) Chunk_t::prev_addr()) &&
          SpaceMangler::is_mangled((HeapWord*) Chunk_t::next_addr())) ||
          (size() == 0 && prev() == NULL && next() == NULL),
    "Space should be clear or mangled");
}

template <class Chunk_t, class FreeList_t>
TreeChunk<Chunk_t, FreeList_t>* TreeList<Chunk_t, FreeList_t>::head_as_TreeChunk() {
  assert(head() == NULL || (TreeChunk<Chunk_t, FreeList_t>::as_TreeChunk(head())->list() == this),
    "Wrong type of chunk?");
  return TreeChunk<Chunk_t, FreeList_t>::as_TreeChunk(head());
}

template <class Chunk_t, class FreeList_t>
TreeChunk<Chunk_t, FreeList_t>* TreeList<Chunk_t, FreeList_t>::first_available() {
  assert(head() != NULL, "The head of the list cannot be NULL");
  Chunk_t* fc = head()->next();
  TreeChunk<Chunk_t, FreeList_t>* retTC;
  if (fc == NULL) {
    retTC = head_as_TreeChunk();
  } else {
    retTC = TreeChunk<Chunk_t, FreeList_t>::as_TreeChunk(fc);
  }
  assert(retTC->list() == this, "Wrong type of chunk.");
  return retTC;
}

// Returns the block with the largest heap address amongst
// those in the list for this size; potentially slow and expensive,
// use with caution!
template <class Chunk_t, class FreeList_t>
TreeChunk<Chunk_t, FreeList_t>* TreeList<Chunk_t, FreeList_t>::largest_address() {
  assert(head() != NULL, "The head of the list cannot be NULL");
  Chunk_t* fc = head()->next();
  TreeChunk<Chunk_t, FreeList_t>* retTC;
  if (fc == NULL) {
    retTC = head_as_TreeChunk();
  } else {
    // walk down the list and return the one with the highest
    // heap address among chunks of this size.
    Chunk_t* last = fc;
    while (fc->next() != NULL) {
      if ((HeapWord*)last < (HeapWord*)fc) {
        last = fc;
      }
      fc = fc->next();
    }
    retTC = TreeChunk<Chunk_t, FreeList_t>::as_TreeChunk(last);
  }
  assert(retTC->list() == this, "Wrong type of chunk.");
  return retTC;
}

template <class Chunk_t, class FreeList_t>
BinaryTreeDictionary<Chunk_t, FreeList_t>::BinaryTreeDictionary(MemRegion mr) {
  assert((mr.byte_size() > min_size()), "minimum chunk size");

  reset(mr);
  assert(root()->left() == NULL, "reset check failed");
  assert(root()->right() == NULL, "reset check failed");
  assert(root()->head()->next() == NULL, "reset check failed");
  assert(root()->head()->prev() == NULL, "reset check failed");
  assert(total_size() == root()->size(), "reset check failed");
  assert(total_free_blocks() == 1, "reset check failed");
}

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::inc_total_size(size_t inc) {
  _total_size = _total_size + inc;
}

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::dec_total_size(size_t dec) {
  _total_size = _total_size - dec;
}

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::reset(MemRegion mr) {
  assert((mr.byte_size() > min_size()), "minimum chunk size");
  set_root(TreeList<Chunk_t, FreeList_t>::as_TreeList(mr.start(), mr.word_size()));
  set_total_size(mr.word_size());
  set_total_free_blocks(1);
}

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::reset(HeapWord* addr, size_t byte_size) {
  MemRegion mr(addr, heap_word_size(byte_size));
  reset(mr);
}

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::reset() {
  set_root(NULL);
  set_total_size(0);
  set_total_free_blocks(0);
}

// Get a free block of size at least size from tree, or NULL.
template <class Chunk_t, class FreeList_t>
TreeChunk<Chunk_t, FreeList_t>*
BinaryTreeDictionary<Chunk_t, FreeList_t>::get_chunk_from_tree(size_t size)
{
  TreeList<Chunk_t, FreeList_t> *curTL, *prevTL;
  TreeChunk<Chunk_t, FreeList_t>* retTC = NULL;

  assert((size >= min_size()), "minimum chunk size");
  if (FLSVerifyDictionary) {
    verify_tree();
  }
  // starting at the root, work downwards trying to find match.
  // Remember the last node of size too great or too small.
  for (prevTL = curTL = root(); curTL != NULL;) {
    if (curTL->size() == size) {        // exact match
      break;
    }
    prevTL = curTL;
    if (curTL->size() < size) {        // proceed to right sub-tree
      curTL = curTL->right();
    } else {                           // proceed to left sub-tree
      assert(curTL->size() > size, "size inconsistency");
      curTL = curTL->left();
    }
  }
  if (curTL == NULL) { // couldn't find exact match

    // try and find the next larger size by walking back up the search path
    for (curTL = prevTL; curTL != NULL;) {
      if (curTL->size() >= size) break;
      else curTL = curTL->parent();
    }
    assert(curTL == NULL || curTL->count() > 0,
      "An empty list should not be in the tree");
  }
  if (curTL != NULL) {
    assert(curTL->size() >= size, "size inconsistency");

    curTL = curTL->get_better_list(this);

    retTC = curTL->first_available();
    assert((retTC != NULL) && (curTL->count() > 0),
      "A list in the binary tree should not be NULL");
    assert(retTC->size() >= size,
      "A chunk of the wrong size was found");
    remove_chunk_from_tree(retTC);
    assert(retTC->is_free(), "Header is not marked correctly");
  }

  if (FLSVerifyDictionary) {
    verify();
  }
  return retTC;
}

template <class Chunk_t, class FreeList_t>
TreeList<Chunk_t, FreeList_t>* BinaryTreeDictionary<Chunk_t, FreeList_t>::find_list(size_t size) const {
  TreeList<Chunk_t, FreeList_t>* curTL;
  for (curTL = root(); curTL != NULL;) {
    if (curTL->size() == size) {        // exact match
      break;
    }

    if (curTL->size() < size) {        // proceed to right sub-tree
      curTL = curTL->right();
    } else {                           // proceed to left sub-tree
      assert(curTL->size() > size, "size inconsistency");
      curTL = curTL->left();
    }
  }
  return curTL;
}


template <class Chunk_t, class FreeList_t>
bool BinaryTreeDictionary<Chunk_t, FreeList_t>::verify_chunk_in_free_list(Chunk_t* tc) const {
  size_t size = tc->size();
  TreeList<Chunk_t, FreeList_t>* tl = find_list(size);
  if (tl == NULL) {
    return false;
  } else {
    return tl->verify_chunk_in_free_list(tc);
  }
}

template <class Chunk_t, class FreeList_t>
Chunk_t* BinaryTreeDictionary<Chunk_t, FreeList_t>::find_largest_dict() const {
  TreeList<Chunk_t, FreeList_t> *curTL = root();
  if (curTL != NULL) {
    while(curTL->right() != NULL) curTL = curTL->right();
    return curTL->largest_address();
  } else {
    return NULL;
  }
}

// Remove the current chunk from the tree.  If it is not the last
// chunk in a list on a tree node, just unlink it.
// If it is the last chunk in the list (the next link is NULL),
// remove the node and repair the tree.
template <class Chunk_t, class FreeList_t>
TreeChunk<Chunk_t, FreeList_t>*
BinaryTreeDictionary<Chunk_t, FreeList_t>::remove_chunk_from_tree(TreeChunk<Chunk_t, FreeList_t>* tc) {
  assert(tc != NULL, "Should not call with a NULL chunk");
  assert(tc->is_free(), "Header is not marked correctly");

  TreeList<Chunk_t, FreeList_t> *newTL, *parentTL;
  TreeChunk<Chunk_t, FreeList_t>* retTC;
  TreeList<Chunk_t, FreeList_t>* tl = tc->list();
  debug_only(
    bool removing_only_chunk = false;
    if (tl == _root) {
      if ((_root->left() == NULL) && (_root->right() == NULL)) {
        if (_root->count() == 1) {
          assert(_root->head() == tc, "Should only be this one chunk");
          removing_only_chunk = true;
        }
      }
    }
  )
  assert(tl != NULL, "List should be set");
  assert(tl->parent() == NULL || tl == tl->parent()->left() ||
         tl == tl->parent()->right(), "list is inconsistent");

  bool complicated_splice = false;

  retTC = tc;
  // Removing this chunk can have the side effect of changing the node
  // (TreeList<Chunk_t, FreeList_t>*) in the tree.  If the node is the root, update it.
  TreeList<Chunk_t, FreeList_t>* replacementTL = tl->remove_chunk_replace_if_needed(tc);
  assert(tc->is_free(), "Chunk should still be free");
  assert(replacementTL->parent() == NULL ||
         replacementTL == replacementTL->parent()->left() ||
         replacementTL == replacementTL->parent()->right(),
         "list is inconsistent");
  if (tl == root()) {
    assert(replacementTL->parent() == NULL, "Incorrectly replacing root");
    set_root(replacementTL);
  }
#ifdef ASSERT
    if (tl != replacementTL) {
      assert(replacementTL->head() != NULL,
        "If the tree list was replaced, it should not be a NULL list");
      TreeList<Chunk_t, FreeList_t>* rhl = replacementTL->head_as_TreeChunk()->list();
      TreeList<Chunk_t, FreeList_t>* rtl =
        TreeChunk<Chunk_t, FreeList_t>::as_TreeChunk(replacementTL->tail())->list();
      assert(rhl == replacementTL, "Broken head");
      assert(rtl == replacementTL, "Broken tail");
      assert(replacementTL->size() == tc->size(),  "Broken size");
    }
#endif

  // Does the tree need to be repaired?
  if (replacementTL->count() == 0) {
    assert(replacementTL->head() == NULL &&
           replacementTL->tail() == NULL, "list count is incorrect");
    // Find the replacement node for the (soon to be empty) node being removed.
    // if we have a single (or no) child, splice child in our stead
    if (replacementTL->left() == NULL) {
      // left is NULL so pick right.  right may also be NULL.
      newTL = replacementTL->right();
      debug_only(replacementTL->clear_right();)
    } else if (replacementTL->right() == NULL) {
      // right is NULL
      newTL = replacementTL->left();
      debug_only(replacementTL->clear_left();)
    } else {  // we have both children, so, by patriarchal convention,
              // my replacement is least node in right sub-tree
      complicated_splice = true;
      newTL = remove_tree_minimum(replacementTL->right());
      assert(newTL != NULL && newTL->left() == NULL &&
             newTL->right() == NULL, "sub-tree minimum exists");
    }
    // newTL is the replacement for the (soon to be empty) node.
    // newTL may be NULL.
    // should verify; we just cleanly excised our replacement
    if (FLSVerifyDictionary) {
      verify_tree();
    }
    // first make newTL my parent's child
    if ((parentTL = replacementTL->parent()) == NULL) {
      // newTL should be root
      assert(tl == root(), "Incorrectly replacing root");
      set_root(newTL);
      if (newTL != NULL) {
        newTL->clear_parent();
      }
    } else if (parentTL->right() == replacementTL) {
      // replacementTL is a right child
      parentTL->set_right(newTL);
    } else {                                // replacementTL is a left child
      assert(parentTL->left() == replacementTL, "should be left child");
      parentTL->set_left(newTL);
    }
    debug_only(replacementTL->clear_parent();)
    if (complicated_splice) {  // we need newTL to get replacementTL's
                              // two children
      assert(newTL != NULL &&
             newTL->left() == NULL && newTL->right() == NULL,
            "newTL should not have encumbrances from the past");
      // we'd like to assert as below:
      // assert(replacementTL->left() != NULL && replacementTL->right() != NULL,
      //       "else !complicated_splice");
      // ... however, the above assertion is too strong because we aren't
      // guaranteed that replacementTL->right() is still NULL.
      // Recall that we removed
      // the right sub-tree minimum from replacementTL.
      // That may well have been its right
      // child! So we'll just assert half of the above:
      assert(replacementTL->left() != NULL, "else !complicated_splice");
      newTL->set_left(replacementTL->left());
      newTL->set_right(replacementTL->right());
      debug_only(
        replacementTL->clear_right();
        replacementTL->clear_left();
      )
    }
    assert(replacementTL->right() == NULL &&
           replacementTL->left() == NULL &&
           replacementTL->parent() == NULL,
        "delete without encumbrances");
  }

  assert(total_size() >= retTC->size(), "Incorrect total size");
  dec_total_size(retTC->size());     // size book-keeping
  assert(total_free_blocks() > 0, "Incorrect total count");
  set_total_free_blocks(total_free_blocks() - 1);

  assert(retTC != NULL, "null chunk?");
  assert(retTC->prev() == NULL && retTC->next() == NULL,
         "should return without encumbrances");
  if (FLSVerifyDictionary) {
    verify_tree();
  }
  assert(!removing_only_chunk || _root == NULL, "root should be NULL");
  return TreeChunk<Chunk_t, FreeList_t>::as_TreeChunk(retTC);
}

// Remove the leftmost node (lm) in the tree and return it.
// If lm has a right child, link it to the left node of
// the parent of lm.
template <class Chunk_t, class FreeList_t>
TreeList<Chunk_t, FreeList_t>* BinaryTreeDictionary<Chunk_t, FreeList_t>::remove_tree_minimum(TreeList<Chunk_t, FreeList_t>* tl) {
  assert(tl != NULL && tl->parent() != NULL, "really need a proper sub-tree");
  // locate the subtree minimum by walking down left branches
  TreeList<Chunk_t, FreeList_t>* curTL = tl;
  for (; curTL->left() != NULL; curTL = curTL->left());
  // obviously curTL now has at most one child, a right child
  if (curTL != root()) {  // Should this test just be removed?
    TreeList<Chunk_t, FreeList_t>* parentTL = curTL->parent();
    if (parentTL->left() == curTL) { // curTL is a left child
      parentTL->set_left(curTL->right());
    } else {
      // If the list tl has no left child, then curTL may be
      // the right child of parentTL.
      assert(parentTL->right() == curTL, "should be a right child");
      parentTL->set_right(curTL->right());
    }
  } else {
    // The only use of this method would not pass the root of the
    // tree (as indicated by the assertion above that the tree list
    // has a parent) but the specification does not explicitly exclude the
    // passing of the root so accommodate it.
    set_root(NULL);
  }
  debug_only(
    curTL->clear_parent();  // Test if this needs to be cleared
    curTL->clear_right();    // recall, above, left child is already null
  )
  // we just excised a (non-root) node, we should still verify all tree invariants
  if (FLSVerifyDictionary) {
    verify_tree();
  }
  return curTL;
}

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::insert_chunk_in_tree(Chunk_t* fc) {
  TreeList<Chunk_t, FreeList_t> *curTL, *prevTL;
  size_t size = fc->size();

  assert((size >= min_size()),
         SIZE_FORMAT " is too small to be a TreeChunk<Chunk_t, FreeList_t> " SIZE_FORMAT,
         size, min_size());
  if (FLSVerifyDictionary) {
    verify_tree();
  }

  fc->clear_next();
  fc->link_prev(NULL);

  // work down from the _root, looking for insertion point
  for (prevTL = curTL = root(); curTL != NULL;) {
    if (curTL->size() == size)  // exact match
      break;
    prevTL = curTL;
    if (curTL->size() > size) { // follow left branch
      curTL = curTL->left();
    } else {                    // follow right branch
      assert(curTL->size() < size, "size inconsistency");
      curTL = curTL->right();
    }
  }
  TreeChunk<Chunk_t, FreeList_t>* tc = TreeChunk<Chunk_t, FreeList_t>::as_TreeChunk(fc);
  // This chunk is being returned to the binary tree.  Its embedded
  // TreeList<Chunk_t, FreeList_t> should be unused at this point.
  tc->initialize();
  if (curTL != NULL) {          // exact match
    tc->set_list(curTL);
    curTL->return_chunk_at_tail(tc);
  } else {                     // need a new node in tree
    tc->clear_next();
    tc->link_prev(NULL);
    TreeList<Chunk_t, FreeList_t>* newTL = TreeList<Chunk_t, FreeList_t>::as_TreeList(tc);
    assert(((TreeChunk<Chunk_t, FreeList_t>*)tc)->list() == newTL,
      "List was not initialized correctly");
    if (prevTL == NULL) {      // we are the only tree node
      assert(root() == NULL, "control point invariant");
      set_root(newTL);
    } else {                   // insert under prevTL ...
      if (prevTL->size() < size) {   // am right child
        assert(prevTL->right() == NULL, "control point invariant");
        prevTL->set_right(newTL);
      } else {                       // am left child
        assert(prevTL->size() > size && prevTL->left() == NULL, "cpt pt inv");
        prevTL->set_left(newTL);
      }
    }
  }
  assert(tc->list() != NULL, "Tree list should be set");

  inc_total_size(size);
  // Method 'total_size_in_tree' walks through the every block in the
  // tree, so it can cause significant performance loss if there are
  // many blocks in the tree
  assert(!FLSVerifyDictionary || total_size_in_tree(root()) == total_size(), "_total_size inconsistency");
  set_total_free_blocks(total_free_blocks() + 1);
  if (FLSVerifyDictionary) {
    verify_tree();
  }
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::max_chunk_size() const {
  verify_par_locked();
  TreeList<Chunk_t, FreeList_t>* tc = root();
  if (tc == NULL) return 0;
  for (; tc->right() != NULL; tc = tc->right());
  return tc->size();
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::total_list_length(TreeList<Chunk_t, FreeList_t>* tl) const {
  size_t res;
  res = tl->count();
#ifdef ASSERT
  size_t cnt;
  Chunk_t* tc = tl->head();
  for (cnt = 0; tc != NULL; tc = tc->next(), cnt++);
  assert(res == cnt, "The count is not being maintained correctly");
#endif
  return res;
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::total_size_in_tree(TreeList<Chunk_t, FreeList_t>* tl) const {
  if (tl == NULL)
    return 0;
  return (tl->size() * total_list_length(tl)) +
         total_size_in_tree(tl->left())    +
         total_size_in_tree(tl->right());
}

template <class Chunk_t, class FreeList_t>
double BinaryTreeDictionary<Chunk_t, FreeList_t>::sum_of_squared_block_sizes(TreeList<Chunk_t, FreeList_t>* const tl) const {
  if (tl == NULL) {
    return 0.0;
  }
  double size = (double)(tl->size());
  double curr = size * size * total_list_length(tl);
  curr += sum_of_squared_block_sizes(tl->left());
  curr += sum_of_squared_block_sizes(tl->right());
  return curr;
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::total_free_blocks_in_tree(TreeList<Chunk_t, FreeList_t>* tl) const {
  if (tl == NULL)
    return 0;
  return total_list_length(tl) +
         total_free_blocks_in_tree(tl->left()) +
         total_free_blocks_in_tree(tl->right());
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::num_free_blocks() const {
  assert(total_free_blocks_in_tree(root()) == total_free_blocks(),
         "_total_free_blocks inconsistency");
  return total_free_blocks();
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::tree_height_helper(TreeList<Chunk_t, FreeList_t>* tl) const {
  if (tl == NULL)
    return 0;
  return 1 + MAX2(tree_height_helper(tl->left()),
                  tree_height_helper(tl->right()));
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::tree_height() const {
  return tree_height_helper(root());
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::total_nodes_helper(TreeList<Chunk_t, FreeList_t>* tl) const {
  if (tl == NULL) {
    return 0;
  }
  return 1 + total_nodes_helper(tl->left()) +
    total_nodes_helper(tl->right());
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::total_nodes_in_tree(TreeList<Chunk_t, FreeList_t>* tl) const {
  return total_nodes_helper(root());
}

// Searches the tree for a chunk that ends at the
// specified address.
template <class Chunk_t, class FreeList_t>
class EndTreeSearchClosure : public DescendTreeSearchClosure<Chunk_t, FreeList_t> {
  HeapWord* _target;
  Chunk_t* _found;

 public:
  EndTreeSearchClosure(HeapWord* target) : _target(target), _found(NULL) {}
  bool do_list(FreeList_t* fl) {
    Chunk_t* item = fl->head();
    while (item != NULL) {
      if (item->end() == (uintptr_t*) _target) {
        _found = item;
        return true;
      }
      item = item->next();
    }
    return false;
  }
  Chunk_t* found() { return _found; }
};

template <class Chunk_t, class FreeList_t>
Chunk_t* BinaryTreeDictionary<Chunk_t, FreeList_t>::find_chunk_ends_at(HeapWord* target) const {
  EndTreeSearchClosure<Chunk_t, FreeList_t> etsc(target);
  bool found_target = etsc.do_tree(root());
  assert(found_target || etsc.found() == NULL, "Consistency check");
  assert(!found_target || etsc.found() != NULL, "Consistency check");
  return etsc.found();
}

// Closures and methods for calculating total bytes returned to the
// free lists in the tree.
#ifndef PRODUCT
template <class Chunk_t, class FreeList_t>
class InitializeDictReturnedBytesClosure : public AscendTreeCensusClosure<Chunk_t, FreeList_t> {
   public:
  void do_list(FreeList_t* fl) {
    fl->set_returned_bytes(0);
  }
};

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::initialize_dict_returned_bytes() {
  InitializeDictReturnedBytesClosure<Chunk_t, FreeList_t> idrb;
  idrb.do_tree(root());
}

template <class Chunk_t, class FreeList_t>
class ReturnedBytesClosure : public AscendTreeCensusClosure<Chunk_t, FreeList_t> {
  size_t _dict_returned_bytes;
 public:
  ReturnedBytesClosure() { _dict_returned_bytes = 0; }
  void do_list(FreeList_t* fl) {
    _dict_returned_bytes += fl->returned_bytes();
  }
  size_t dict_returned_bytes() { return _dict_returned_bytes; }
};

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::sum_dict_returned_bytes() {
  ReturnedBytesClosure<Chunk_t, FreeList_t> rbc;
  rbc.do_tree(root());

  return rbc.dict_returned_bytes();
}

// Count the number of entries in the tree.
template <class Chunk_t, class FreeList_t>
class treeCountClosure : public DescendTreeCensusClosure<Chunk_t, FreeList_t> {
 public:
  uint count;
  treeCountClosure(uint c) { count = c; }
  void do_list(FreeList_t* fl) {
    count++;
  }
};

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::total_count() {
  treeCountClosure<Chunk_t, FreeList_t> ctc(0);
  ctc.do_tree(root());
  return ctc.count;
}

template <class Chunk_t, class FreeList_t>
Mutex* BinaryTreeDictionary<Chunk_t, FreeList_t>::par_lock() const {
  return _lock;
}

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::set_par_lock(Mutex* lock) {
  _lock = lock;
}

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::verify_par_locked() const {
#ifdef ASSERT
  Thread* my_thread = Thread::current();
  if (my_thread->is_GC_task_thread()) {
    assert(par_lock() != NULL, "Should be using locking?");
    assert_lock_strong(par_lock());
  }
#endif // ASSERT
}
#endif // PRODUCT

// Print summary statistics
template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::report_statistics(outputStream* st) const {
  verify_par_locked();
  st->print_cr("Statistics for BinaryTreeDictionary:");
  st->print_cr("------------------------------------");
  size_t total_size = total_chunk_size(debug_only(NULL));
  size_t free_blocks = num_free_blocks();
  st->print_cr("Total Free Space: " SIZE_FORMAT, total_size);
  st->print_cr("Max   Chunk Size: " SIZE_FORMAT, max_chunk_size());
  st->print_cr("Number of Blocks: " SIZE_FORMAT, free_blocks);
  if (free_blocks > 0) {
    st->print_cr("Av.  Block  Size: " SIZE_FORMAT, total_size/free_blocks);
  }
  st->print_cr("Tree      Height: " SIZE_FORMAT, tree_height());
}

template <class Chunk_t, class FreeList_t>
class PrintFreeListsClosure : public AscendTreeCensusClosure<Chunk_t, FreeList_t> {
  outputStream* _st;
  int _print_line;

 public:
  PrintFreeListsClosure(outputStream* st) {
    _st = st;
    _print_line = 0;
  }
  void do_list(FreeList_t* fl) {
    if (++_print_line >= 40) {
      FreeList_t::print_labels_on(_st, "size");
      _print_line = 0;
    }
    fl->print_on(_st);
    size_t sz = fl->size();
    for (Chunk_t* fc = fl->head(); fc != NULL;
         fc = fc->next()) {
      _st->print_cr("\t[" PTR_FORMAT "," PTR_FORMAT ")  %s",
                    p2i(fc), p2i((HeapWord*)fc + sz),
                    fc->cantCoalesce() ? "\t CC" : "");
    }
  }
};

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::print_free_lists(outputStream* st) const {

  FreeList_t::print_labels_on(st, "size");
  PrintFreeListsClosure<Chunk_t, FreeList_t> pflc(st);
  pflc.do_tree(root());
}

// Verify the following tree invariants:
// . _root has no parent
// . parent and child point to each other
// . each node's key correctly related to that of its child(ren)
template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::verify_tree() const {
  guarantee(root() == NULL || total_free_blocks() == 0 ||
    total_size() != 0, "_total_size shouldn't be 0?");
  guarantee(root() == NULL || root()->parent() == NULL, "_root shouldn't have parent");
  verify_tree_helper(root());
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::verify_prev_free_ptrs(TreeList<Chunk_t, FreeList_t>* tl) {
  size_t ct = 0;
  for (Chunk_t* curFC = tl->head(); curFC != NULL; curFC = curFC->next()) {
    ct++;
    assert(curFC->prev() == NULL || curFC->prev()->is_free(),
      "Chunk should be free");
  }
  return ct;
}

// Note: this helper is recursive rather than iterative, so use with
// caution on very deep trees; and watch out for stack overflow errors;
// In general, to be used only for debugging.
template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::verify_tree_helper(TreeList<Chunk_t, FreeList_t>* tl) const {
  if (tl == NULL)
    return;
  guarantee(tl->size() != 0, "A list must has a size");
  guarantee(tl->left()  == NULL || tl->left()->parent()  == tl,
         "parent<-/->left");
  guarantee(tl->right() == NULL || tl->right()->parent() == tl,
         "parent<-/->right");;
  guarantee(tl->left() == NULL  || tl->left()->size()    <  tl->size(),
         "parent !> left");
  guarantee(tl->right() == NULL || tl->right()->size()   >  tl->size(),
         "parent !< left");
  guarantee(tl->head() == NULL || tl->head()->is_free(), "!Free");
  guarantee(tl->head() == NULL || tl->head_as_TreeChunk()->list() == tl,
    "list inconsistency");
  guarantee(tl->count() > 0 || (tl->head() == NULL && tl->tail() == NULL),
    "list count is inconsistent");
  guarantee(tl->count() > 1 || tl->head() == tl->tail(),
    "list is incorrectly constructed");
  size_t count = verify_prev_free_ptrs(tl);
  guarantee(count == (size_t)tl->count(), "Node count is incorrect");
  if (tl->head() != NULL) {
    tl->head_as_TreeChunk()->verify_tree_chunk_list();
  }
  verify_tree_helper(tl->left());
  verify_tree_helper(tl->right());
}

template <class Chunk_t, class FreeList_t>
void BinaryTreeDictionary<Chunk_t, FreeList_t>::verify() const {
  verify_tree();
  guarantee(total_size() == total_size_in_tree(root()), "Total Size inconsistency");
}

template <class Chunk_t, class FreeList_t>
size_t BinaryTreeDictionary<Chunk_t, FreeList_t>::total_chunk_size(debug_only(const Mutex* lock)) const {
  debug_only(
    if (lock != NULL && lock->owned_by_self()) {
      assert(total_size_in_tree(root()) == total_size(),
             "_total_size inconsistency");
    }
  )
  return total_size();
}

#endif // SHARE_VM_MEMORY_BINARYTREEDICTIONARY_INLINE_HPP
