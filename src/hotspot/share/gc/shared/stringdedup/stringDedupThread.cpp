/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/stringdedup/stringDedupProcessor.hpp"
#include "gc/shared/stringdedup/stringDedupThread.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"

StringDedupThread::StringDedupThread() : ConcurrentGCThread() {}

void StringDedupThread::initialize() {
  StringDedupThread* t = new StringDedupThread();
  StringDedup::_thread = t;     // Must be set before starting.
  t->set_name("StringDedupThread");
  t->create_and_start(NormPriority);
  if (t->osthread() == nullptr) {
    vm_exit_during_initialization("Failed to start string deduplication thread");
  }
}

void StringDedupThread::run_service() {
  StringDedup::_processor->run();
}

void StringDedupThread::stop_service() {
  MonitorLocker ml(StringDedup_lock, Mutex::_no_safepoint_check_flag);
  ml.notify_all();
}

bool StringDedupThread::yield_or_continue() const {
  if (SuspendibleThreadSet::should_yield()) {
    SuspendibleThreadSet::yield();
  }
  return !should_terminate();
}
