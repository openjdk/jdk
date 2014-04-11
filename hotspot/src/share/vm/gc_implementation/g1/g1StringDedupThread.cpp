/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1Log.hpp"
#include "gc_implementation/g1/g1StringDedup.hpp"
#include "gc_implementation/g1/g1StringDedupTable.hpp"
#include "gc_implementation/g1/g1StringDedupThread.hpp"
#include "gc_implementation/g1/g1StringDedupQueue.hpp"

G1StringDedupThread* G1StringDedupThread::_thread = NULL;

G1StringDedupThread::G1StringDedupThread() :
  ConcurrentGCThread() {
  set_name("String Deduplication Thread");
  create_and_start();
}

G1StringDedupThread::~G1StringDedupThread() {
  ShouldNotReachHere();
}

void G1StringDedupThread::create() {
  assert(G1StringDedup::is_enabled(), "String deduplication not enabled");
  assert(_thread == NULL, "One string deduplication thread allowed");
  _thread = new G1StringDedupThread();
}

G1StringDedupThread* G1StringDedupThread::thread() {
  assert(G1StringDedup::is_enabled(), "String deduplication not enabled");
  assert(_thread != NULL, "String deduplication thread not created");
  return _thread;
}

void G1StringDedupThread::print_on(outputStream* st) const {
  st->print("\"%s\" ", name());
  Thread::print_on(st);
  st->cr();
}

void G1StringDedupThread::run() {
  G1StringDedupStat total_stat;

  initialize_in_thread();
  wait_for_universe_init();

  // Main loop
  for (;;) {
    G1StringDedupStat stat;

    stat.mark_idle();

    // Wait for the queue to become non-empty
    G1StringDedupQueue::wait();
    if (_should_terminate) {
      break;
    }

    {
      // Include thread in safepoints
      SuspendibleThreadSetJoiner sts;

      stat.mark_exec();

      // Process the queue
      for (;;) {
        oop java_string = G1StringDedupQueue::pop();
        if (java_string == NULL) {
          break;
        }

        G1StringDedupTable::deduplicate(java_string, stat);

        // Safepoint this thread if needed
        if (sts.should_yield()) {
          stat.mark_block();
          sts.yield();
          stat.mark_unblock();
        }
      }

      G1StringDedupTable::trim_entry_cache();

      stat.mark_done();

      // Print statistics
      total_stat.add(stat);
      print(gclog_or_tty, stat, total_stat);
    }
  }

  terminate();
}

void G1StringDedupThread::stop() {
  {
    MonitorLockerEx ml(Terminator_lock);
    _thread->_should_terminate = true;
  }

  G1StringDedupQueue::cancel_wait();

  {
    MonitorLockerEx ml(Terminator_lock);
    while (!_thread->_has_terminated) {
      ml.wait();
    }
  }
}

void G1StringDedupThread::print(outputStream* st, const G1StringDedupStat& last_stat, const G1StringDedupStat& total_stat) {
  if (G1Log::fine() || PrintStringDeduplicationStatistics) {
    G1StringDedupStat::print_summary(st, last_stat, total_stat);
    if (PrintStringDeduplicationStatistics) {
      G1StringDedupStat::print_statistics(st, last_stat, false);
      G1StringDedupStat::print_statistics(st, total_stat, true);
      G1StringDedupTable::print_statistics(st);
      G1StringDedupQueue::print_statistics(st);
    }
  }
}
