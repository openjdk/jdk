/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/stringdedup/stringDedupProcessor.hpp"
#include "memory/universe.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/osThread.hpp"
#include "runtime/perfData.hpp"
#include "runtime/vmThread.hpp"
#include "services/cpuTimeUsage.hpp"

volatile bool CPUTimeUsage::Error::_has_error = false;

static inline jlong thread_cpu_time_or_zero(Thread* thread) {
  assert(!Universe::is_shutting_down(), "Should not query during shutdown");
  jlong cpu_time = os::thread_cpu_time(thread);
  if (cpu_time == -1) {
    CPUTimeUsage::Error::mark_error();
    return 0;
  }
  return cpu_time;
}

class CPUTimeThreadClosure : public ThreadClosure {
private:
  jlong _cpu_time = 0;

public:
  virtual void do_thread(Thread* thread) {
    _cpu_time += thread_cpu_time_or_zero(thread);
  }
  jlong cpu_time() { return _cpu_time; };
};

jlong CPUTimeUsage::GC::vm_thread() {
  return Universe::heap()->_vmthread_cpu_time;
}

jlong CPUTimeUsage::GC::gc_threads() {
  CPUTimeThreadClosure cl;
  Universe::heap()->gc_threads_do(&cl);
  return cl.cpu_time();
}

jlong CPUTimeUsage::GC::total() {
  return gc_threads() + vm_thread() + stringdedup();
}

jlong CPUTimeUsage::GC::stringdedup() {
  if (UseStringDeduplication) {
    return thread_cpu_time_or_zero((Thread*)StringDedup::_processor->_thread);
  }
  return 0;
}

bool CPUTimeUsage::Error::has_error() {
  return AtomicAccess::load(&_has_error);
}

void CPUTimeUsage::Error::mark_error() {
  AtomicAccess::store(&_has_error, true);
}
