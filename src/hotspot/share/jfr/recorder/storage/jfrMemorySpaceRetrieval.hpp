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

#ifndef SHARE_VM_JFR_RECORDER_STORAGE_JFRMEMORYSPACERETRIEVAL_HPP
#define SHARE_VM_JFR_RECORDER_STORAGE_JFRMEMORYSPACERETRIEVAL_HPP

#include "memory/allocation.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"

/*
* Some policy classes for getting mspace memory
*/

template <typename Mspace>
class JfrMspaceRetrieval : AllStatic {
 public:
  typedef typename Mspace::Type Type;
  static Type* get(size_t size, Mspace* mspace, typename Mspace::Iterator& iterator, Thread* thread) {
    while (iterator.has_next()) {
      Type* const t = iterator.next();
      if (t->retired()) continue;
      if (t->try_acquire(thread)) {
        assert(!t->retired(), "invariant");
        if (t->free_size() >= size) {
          return t;
        }
        t->set_retired();
        mspace->register_full(t, thread);
      }
    }
    return NULL;
  }
};

template <typename Mspace>
class JfrMspaceAlternatingRetrieval {
 private:
   // provides stochastic distribution over "deque" endpoints; racy is ok here
  static bool _last_access;
 public:
  typedef typename Mspace::Type Type;
  static Type* get(size_t size, Mspace* mspace, Thread* thread) {
    typename Mspace::Iterator iterator(mspace->free(), (_last_access = !_last_access) ? forward : backward);
    return JfrMspaceRetrieval<Mspace>::get(size, mspace, iterator, thread);
  }
};

template <typename Mspace>
bool JfrMspaceAlternatingRetrieval<Mspace>::_last_access = false;

template <typename Mspace>
class JfrMspaceSequentialRetrieval {
 public:
  typedef typename Mspace::Type Type;
  static Type* get(size_t size, Mspace* mspace, Thread* thread) {
    typename Mspace::Iterator iterator(mspace->free());
    return JfrMspaceRetrieval<Mspace>::get(size, mspace, iterator, thread);
  }
};

template <typename Mspace>
class JfrExclusiveRetrieval : AllStatic {
public:
  typedef typename Mspace::Type Type;
  static Type* get(size_t size, Mspace* mspace, typename Mspace::Iterator& iterator, Thread* thread) {
    assert(mspace->is_locked(), "invariant");
    if (iterator.has_next()) {
      Type* const t = iterator.next();
      assert(!t->retired(), "invariant");
      assert(t->identity() == NULL, "invariant");
      assert(t->free_size() >= size, "invariant");
      t->acquire(thread);
      return t;
    }
    return NULL;
  }
};

template <typename Mspace>
class JfrThreadLocalRetrieval {
public:
  typedef typename Mspace::Type Type;
  static Type* get(size_t size, Mspace* mspace, Thread* thread) {
    typename Mspace::Iterator iterator(mspace->free(), forward);
    return JfrExclusiveRetrieval<Mspace>::get(size, mspace, iterator, thread);
  }
};

#endif // SHARE_VM_JFR_RECORDER_STORAGE_JFRMEMORYSPACERETRIEVAL_HPP
