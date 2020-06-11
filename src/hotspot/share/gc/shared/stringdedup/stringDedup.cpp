/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/stringdedup/stringDedupQueue.hpp"
#include "gc/shared/stringdedup/stringDedupTable.hpp"
#include "gc/shared/stringdedup/stringDedupThread.hpp"
#include "memory/iterator.hpp"

bool StringDedup::_enabled = false;

void StringDedup::gc_prologue(bool resize_and_rehash_table) {
  assert(is_enabled(), "String deduplication not enabled");
  StringDedupQueue::gc_prologue();
  StringDedupTable::gc_prologue(resize_and_rehash_table);

}
void StringDedup::gc_epilogue() {
  assert(is_enabled(), "String deduplication not enabled");
  StringDedupQueue::gc_epilogue();
  StringDedupTable::gc_epilogue();
}

void StringDedup::stop() {
  assert(is_enabled(), "String deduplication not enabled");
  StringDedupThread::thread()->stop();
}

void StringDedup::deduplicate(oop java_string) {
  assert(is_enabled(), "String deduplication not enabled");
  StringDedupStat dummy; // Statistics from this path is never used
  StringDedupTable::deduplicate(java_string, &dummy);
}

void StringDedup::parallel_unlink(StringDedupUnlinkOrOopsDoClosure* unlink, uint worker_id) {
  assert(is_enabled(), "String deduplication not enabled");
  StringDedupQueue::unlink_or_oops_do(unlink);
  StringDedupTable::unlink_or_oops_do(unlink, worker_id);
}

void StringDedup::threads_do(ThreadClosure* tc) {
  assert(is_enabled(), "String deduplication not enabled");
  tc->do_thread(StringDedupThread::thread());
}

void StringDedup::verify() {
  assert(is_enabled(), "String deduplication not enabled");
  StringDedupQueue::verify();
  StringDedupTable::verify();
}


StringDedupUnlinkOrOopsDoClosure::StringDedupUnlinkOrOopsDoClosure(BoolObjectClosure* is_alive,
                                                                   OopClosure* keep_alive) :
  _always_true(),
  _do_nothing(),
  _is_alive(is_alive != NULL ? is_alive : &_always_true),
  _keep_alive(keep_alive != NULL ? keep_alive : &_do_nothing) {
}
