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

#ifndef SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPTHREAD_INLINE_HPP
#define SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPTHREAD_INLINE_HPP

#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/stringdedup/stringDedupQueue.inline.hpp"
#include "gc/shared/stringdedup/stringDedupThread.hpp"

template <typename S>
void StringDedupThreadImpl<S>::do_deduplication() {
  S total_stat;

  deduplicate_shared_strings(&total_stat);

  // Main loop
  for (;;) {
    S stat;

    stat.mark_idle();

    // Wait for the queue to become non-empty
    StringDedupQueue::wait();
    if (this->should_terminate()) {
      break;
    }

    {
      // Include thread in safepoints
      SuspendibleThreadSetJoiner sts_join;

      stat.mark_exec();
      StringDedupStat::print_start(&stat);

      // Process the queue
      for (;;) {
        oop java_string = StringDedupQueue::pop();
        if (java_string == NULL) {
          break;
        }

        StringDedupTable::deduplicate(java_string, &stat);

        // Safepoint this thread if needed
        if (sts_join.should_yield()) {
          stat.mark_block();
          sts_join.yield();
          stat.mark_unblock();
        }
      }

      stat.mark_done();

      total_stat.add(&stat);
      print_end(&stat, &total_stat);
      stat.reset();
    }

    StringDedupTable::clean_entry_cache();
  }
}

template <typename S>
void StringDedupThreadImpl<S>::create() {
  assert(_thread == NULL, "One string deduplication thread allowed");
  _thread = new StringDedupThreadImpl<S>();
}

#endif // SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPTHREAD_INLINE_HPP
