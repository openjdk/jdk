/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZTHREAD_HPP
#define SHARE_GC_Z_ZTHREAD_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"

class ZThread : public AllStatic {
  friend class ZTask;
  friend class ZWorkersInitializeTask;
  friend class ZRuntimeWorkersInitializeTask;

private:
  static __thread bool      _initialized;
  static __thread uintptr_t _id;
  static __thread bool      _is_vm;
  static __thread bool      _is_java;
  static __thread bool      _is_worker;
  static __thread bool      _is_runtime_worker;
  static __thread uint      _worker_id;

  static void initialize();

  static void ensure_initialized() {
    if (!_initialized) {
      initialize();
    }
  }

  static void set_worker();
  static void set_runtime_worker();

  static bool has_worker_id();
  static void set_worker_id(uint worker_id);
  static void clear_worker_id();

public:
  static const char* name();

  static uintptr_t id() {
    ensure_initialized();
    return _id;
  }

  static bool is_vm() {
    ensure_initialized();
    return _is_vm;
  }

  static bool is_java() {
    ensure_initialized();
    return _is_java;
  }

  static bool is_worker() {
    ensure_initialized();
    return _is_worker;
  }

  static bool is_runtime_worker() {
    ensure_initialized();
    return _is_runtime_worker;
  }

  static uint worker_id() {
    assert(has_worker_id(), "Worker id not initialized");
    return _worker_id;
  }
};

#endif // SHARE_GC_Z_ZTHREAD_HPP
