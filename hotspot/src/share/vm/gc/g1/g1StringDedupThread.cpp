/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/stringTable.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/g1StringDedupQueue.hpp"
#include "gc/g1/g1StringDedupTable.hpp"
#include "gc/g1/g1StringDedupThread.hpp"
#include "gc/g1/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"

G1StringDedupThread* G1StringDedupThread::_thread = NULL;

G1StringDedupThread::G1StringDedupThread() :
  ConcurrentGCThread() {
  set_name("G1 StrDedup");
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

class G1StringDedupSharedClosure: public OopClosure {
 private:
  G1StringDedupStat& _stat;

 public:
  G1StringDedupSharedClosure(G1StringDedupStat& stat) : _stat(stat) {}

  virtual void do_oop(oop* p) { ShouldNotReachHere(); }
  virtual void do_oop(narrowOop* p) {
    oop java_string = oopDesc::load_decode_heap_oop(p);
    G1StringDedupTable::deduplicate(java_string, _stat);
  }
};

// The CDS archive does not include the string dedupication table. Only the string
// table is saved in the archive. The shared strings from CDS archive need to be
// added to the string dedupication table before deduplication occurs. That is
// done in the begining of the G1StringDedupThread (see G1StringDedupThread::run()
// below).
void G1StringDedupThread::deduplicate_shared_strings(G1StringDedupStat& stat) {
  G1StringDedupSharedClosure sharedStringDedup(stat);
  StringTable::shared_oops_do(&sharedStringDedup);
}

void G1StringDedupThread::run_service() {
  G1StringDedupStat total_stat;

  deduplicate_shared_strings(total_stat);

  // Main loop
  for (;;) {
    G1StringDedupStat stat;

    stat.mark_idle();

    // Wait for the queue to become non-empty
    G1StringDedupQueue::wait();
    if (should_terminate()) {
      break;
    }

    {
      // Include thread in safepoints
      SuspendibleThreadSetJoiner sts_join;

      stat.mark_exec();
      print_start(stat);

      // Process the queue
      for (;;) {
        oop java_string = G1StringDedupQueue::pop();
        if (java_string == NULL) {
          break;
        }

        G1StringDedupTable::deduplicate(java_string, stat);

        // Safepoint this thread if needed
        if (sts_join.should_yield()) {
          stat.mark_block();
          sts_join.yield();
          stat.mark_unblock();
        }
      }

      stat.mark_done();

      total_stat.add(stat);
      print_end(stat, total_stat);
    }

    G1StringDedupTable::clean_entry_cache();
  }
}

void G1StringDedupThread::stop_service() {
  G1StringDedupQueue::cancel_wait();
}

void G1StringDedupThread::print_start(const G1StringDedupStat& last_stat) {
  G1StringDedupStat::print_start(last_stat);
}

void G1StringDedupThread::print_end(const G1StringDedupStat& last_stat, const G1StringDedupStat& total_stat) {
  G1StringDedupStat::print_end(last_stat, total_stat);
  if (log_is_enabled(Debug, gc, stringdedup)) {
    G1StringDedupStat::print_statistics(last_stat, false);
    G1StringDedupStat::print_statistics(total_stat, true);
    G1StringDedupTable::print_statistics();
    G1StringDedupQueue::print_statistics();
  }
}
