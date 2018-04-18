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

#include "precompiled.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "trace/tracingExport.hpp"

// Allow lock-free reads of _sampler_thread.
Thread* volatile TracingExport::_sampler_thread = NULL;

Thread* TracingExport::sampler_thread_acquire() {
  return (Thread*)OrderAccess::load_acquire(&_sampler_thread);
}

void TracingExport::set_sampler_thread_with_lock(Thread* thread) {
  // Grab Threads_lock to avoid conflicts with Thread-SMR scans.
  MutexLocker ml(Threads_lock);
  assert(thread == NULL || _sampler_thread == NULL, "_sampler_thread should not already be set.");
  // To match with the lock-free load_acquire():
  OrderAccess::release_store(&_sampler_thread, thread);
}
