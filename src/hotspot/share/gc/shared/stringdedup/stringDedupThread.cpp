/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/stringdedup/stringDedupProcessor.hpp"
#include "gc/shared/stringdedup/stringDedupThread.hpp"
#include "runtime/handles.hpp"
#include "runtime/os.hpp"
#include "utilities/exceptions.hpp"

StringDedupThread::StringDedupThread() : JavaThread(thread_entry) {}

void StringDedupThread::initialize() {
  EXCEPTION_MARK;

  const char* name = "StringDedupThread";
  Handle thread_oop = JavaThread::create_system_thread_object(name, CHECK);
  StringDedupThread* thread = new StringDedupThread();
  JavaThread::vm_exit_on_osthread_failure(thread);
  JavaThread::start_internal_daemon(THREAD, thread, thread_oop, NormPriority);
}

void StringDedupThread::thread_entry(JavaThread* thread, TRAPS) {
  StringDedup::_processor->run(thread);
}

bool StringDedupThread::is_hidden_from_external_view() const {
  return true;
}
