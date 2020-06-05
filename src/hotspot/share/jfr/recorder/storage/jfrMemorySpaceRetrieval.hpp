/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACERETRIEVAL_HPP
#define SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACERETRIEVAL_HPP

#include "jfr/utilities/jfrIterator.hpp"

/* Some policy classes for getting mspace memory. */

template <typename Mspace>
class JfrMspaceRetrieval {
 public:
  typedef typename Mspace::Node Node;
  static Node* acquire(Mspace* mspace, Thread* thread, size_t size) {
    StopOnNullCondition<typename Mspace::FreeList> iterator(mspace->free_list());
    while (iterator.has_next()) {
      Node* const node = iterator.next();
      if (node->retired()) continue;
      if (node->try_acquire(thread)) {
        assert(!node->retired(), "invariant");
        if (node->free_size() >= size) {
          return node;
        }
        node->set_retired();
        mspace->register_full(node, thread);
      }
    }
    return NULL;
  }
};

template <typename Mspace>
class JfrMspaceRemoveRetrieval : AllStatic {
 public:
  typedef typename Mspace::Node Node;
  static Node* acquire(Mspace* mspace, Thread* thread, size_t size) {
    StopOnNullConditionRemoval<typename Mspace::FreeList> iterator(mspace->free_list());
    // it is the iterator that removes the nodes
    while (iterator.has_next()) {
      Node* const node = iterator.next();
      if (node == NULL) return NULL;
      mspace->decrement_free_list_count();
      assert(node->free_size() >= size, "invariant");
      assert(!node->retired(), "invariant");
      assert(node->identity() == NULL, "invariant");
      node->set_identity(thread);
      return node;
    }
    return NULL;
  }
};

#endif // SHARE_JFR_RECORDER_STORAGE_JFRMEMORYSPACERETRIEVAL_HPP
