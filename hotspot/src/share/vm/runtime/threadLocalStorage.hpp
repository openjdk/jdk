/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

// Interface for thread local storage

// Fast variant of ThreadLocalStorage::get_thread_slow
extern "C" Thread*   get_thread();

// Get raw thread id: e.g., %g7 on sparc, fs or gs on x86
extern "C" uintptr_t _raw_thread_id();

class ThreadLocalStorage : AllStatic {
 public:
  static void    set_thread(Thread* thread);
  static Thread* get_thread_slow();
  static void    invalidate_all() { pd_invalidate_all(); }

  // Machine dependent stuff
  #include "incls/_threadLS_pd.hpp.incl"

 public:
  // Accessor
  static inline int  thread_index()              { return _thread_index; }
  static inline void set_thread_index(int index) { _thread_index = index; }

  // Initialization
  // Called explicitly from VMThread::activate_system instead of init_globals.
  static void init();
  static bool is_initialized();

 private:
  static int     _thread_index;

  static void    generate_code_for_get_thread();

  // Processor dependent parts of set_thread and initialization
  static void pd_set_thread(Thread* thread);
  static void pd_init();
  // Invalidate any thread cacheing or optimization schemes.
  static void pd_invalidate_all();

};
