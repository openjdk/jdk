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

#ifndef SHARE_VM_GC_G1_G1STRINGDEDUPTHREAD_HPP
#define SHARE_VM_GC_G1_G1STRINGDEDUPTHREAD_HPP

#include "gc/g1/g1StringDedupStat.hpp"
#include "gc/shared/concurrentGCThread.hpp"

//
// The deduplication thread is where the actual deduplication occurs. It waits for
// deduplication candidates to appear on the deduplication queue, removes them from
// the queue and tries to deduplicate them. It uses the deduplication hashtable to
// find identical, already existing, character arrays on the heap. The thread runs
// concurrently with the Java application but participates in safepoints to allow
// the GC to adjust and unlink oops from the deduplication queue and table.
//
class G1StringDedupThread: public ConcurrentGCThread {
private:
  static G1StringDedupThread* _thread;

  G1StringDedupThread();
  ~G1StringDedupThread();

  void print_start(const G1StringDedupStat& last_stat);
  void print_end(const G1StringDedupStat& last_stat, const G1StringDedupStat& total_stat);

  void run_service();
  void stop_service();

public:
  static void create();

  static G1StringDedupThread* thread();

  void deduplicate_shared_strings(G1StringDedupStat& stat);
};

#endif // SHARE_VM_GC_G1_G1STRINGDEDUPTHREAD_HPP
