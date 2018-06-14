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

#include "precompiled.hpp"
#include "classfile/stringTable.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/stringdedup/stringDedupQueue.hpp"
#include "gc/shared/stringdedup/stringDedupQueue.inline.hpp"
#include "gc/shared/stringdedup/stringDedupTable.hpp"
#include "gc/shared/stringdedup/stringDedupThread.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"

StringDedupThread* StringDedupThread::_thread = NULL;

StringDedupThread::StringDedupThread() :
  ConcurrentGCThread() {
  set_name("StrDedup");
  create_and_start();
}

StringDedupThread::~StringDedupThread() {
  ShouldNotReachHere();
}

StringDedupThread* StringDedupThread::thread() {
  assert(_thread != NULL, "String deduplication thread not created");
  return _thread;
}

class StringDedupSharedClosure: public OopClosure {
 private:
  StringDedupStat* _stat;

 public:
  StringDedupSharedClosure(StringDedupStat* stat) : _stat(stat) {}

  virtual void do_oop(oop* p) { ShouldNotReachHere(); }
  virtual void do_oop(narrowOop* p) {
    oop java_string = RawAccess<>::oop_load(p);
    StringDedupTable::deduplicate(java_string, _stat);
  }
};

// The CDS archive does not include the string dedupication table. Only the string
// table is saved in the archive. The shared strings from CDS archive need to be
// added to the string dedupication table before deduplication occurs. That is
// done in the begining of the StringDedupThread (see StringDedupThread::do_deduplication()).
void StringDedupThread::deduplicate_shared_strings(StringDedupStat* stat) {
  StringDedupSharedClosure sharedStringDedup(stat);
  StringTable::shared_oops_do(&sharedStringDedup);
}

void StringDedupThread::stop_service() {
  StringDedupQueue::cancel_wait();
}

void StringDedupThread::print_start(const StringDedupStat* last_stat) {
  StringDedupStat::print_start(last_stat);
}

void StringDedupThread::print_end(const StringDedupStat* last_stat, const StringDedupStat* total_stat) {
  StringDedupStat::print_end(last_stat, total_stat);
  if (log_is_enabled(Debug, gc, stringdedup)) {
    last_stat->print_statistics(false);
    total_stat->print_statistics(true);

    StringDedupTable::print_statistics();
    StringDedupQueue::print_statistics();
  }
}
