/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPTHREAD_HPP
#define SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPTHREAD_HPP

#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/stringdedup/stringDedupStat.hpp"

//
// The deduplication thread is where the actual deduplication occurs. It waits for
// deduplication candidates to appear on the deduplication queue, removes them from
// the queue and tries to deduplicate them. It uses the deduplication hashtable to
// find identical, already existing, character arrays on the heap. The thread runs
// concurrently with the Java application but participates in safepoints to allow
// the GC to adjust and unlink oops from the deduplication queue and table.
//
class StringDedupThread: public ConcurrentGCThread {
protected:
  static StringDedupThread* _thread;

  StringDedupThread();
  ~StringDedupThread();

  void print_start(const StringDedupStat* last_stat);
  void print_end(const StringDedupStat* last_stat, const StringDedupStat* total_stat);

  void run_service() { this->do_deduplication(); }
  void stop_service();

  void deduplicate_shared_strings(StringDedupStat* stat);
protected:
  virtual void do_deduplication() = 0;

public:
  static StringDedupThread* thread();
};

template <typename S>
class StringDedupThreadImpl : public StringDedupThread {
private:
  StringDedupThreadImpl() { }

protected:
  void do_deduplication();

public:
  static void create();
};

#endif // SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPTHREAD_HPP
