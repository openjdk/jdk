/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_MONITORDEFLATIONTHREAD_HPP
#define SHARE_RUNTIME_MONITORDEFLATIONTHREAD_HPP

#include "runtime/javaThread.hpp"

// A hidden from external view JavaThread for asynchronously deflating idle monitors.
// The API for requesting/querying async deflation is mostly maintained here,
// but some functions are also exposed through ObjectSynchronizer to provide
// an external, public API.

class MonitorDeflationThread : public JavaThread {
  friend class VMStructs;
  friend class ObjectSynchronizer;
  friend class ObjectMonitorDeflationLogging;

 private:

  static void monitor_deflation_thread_entry(JavaThread* thread, TRAPS);
  MonitorDeflationThread(ThreadFunction entry_point) : JavaThread(entry_point) {};

  static volatile bool _is_async_deflation_requested;
  static jlong         _last_async_deflation_time_ns;

  static bool is_async_deflation_needed();
  static void set_is_async_deflation_requested(bool new_value) { _is_async_deflation_requested = new_value; }
  static void set_last_async_deflation_time_ns(jlong ns) { _last_async_deflation_time_ns = ns; }

  static void request_deflate_idle_monitors();
  static bool request_deflate_idle_monitors_from_wb();  // for whitebox test support

  static size_t in_use_list_ceiling();
  static void dec_in_use_list_ceiling();
  static void inc_in_use_list_ceiling();
  static void update_heuristics(size_t deflated_count);
  static bool monitors_used_above_threshold();

 public:
  static void initialize();

  // Hide this thread from external view.
  bool is_hidden_from_external_view() const { return true; }
};

#endif // SHARE_RUNTIME_MONITORDEFLATIONTHREAD_HPP
