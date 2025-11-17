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

#ifndef SHARE_CDS_AOTTHREAD_HPP
#define SHARE_CDS_AOTTHREAD_HPP

#include "runtime/javaThread.hpp"
#include "utilities/macros.hpp"

// A hidden from external view JavaThread for materializing archived objects

class AOTThread : public JavaThread {
private:
  static bool _started;
  static AOTThread* _aot_thread;
  static void aot_thread_entry(JavaThread* thread, TRAPS);
  AOTThread(ThreadFunction entry_point) : JavaThread(entry_point) {};

public:
  static void initialize();

  // Hide this thread from external view.
  virtual bool is_hidden_from_external_view() const { return true; }

  static void materialize_thread_object();

  static bool aot_thread_initialized() { return _started; };
  bool is_aot_thread() const { return true; };
};

#endif // SHARE_CDS_AOTTHREAD_HPP
