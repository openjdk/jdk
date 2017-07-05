/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/threadLocalStorage.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
# include "thread_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
# include "thread_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
# include "thread_windows.inline.hpp"
#endif

// static member initialization
int ThreadLocalStorage::_thread_index = -1;

Thread* ThreadLocalStorage::get_thread_slow() {
  return (Thread*) os::thread_local_storage_at(ThreadLocalStorage::thread_index());
}

void ThreadLocalStorage::set_thread(Thread* thread) {
  pd_set_thread(thread);

  // The following ensure that any optimization tricks we have tried
  // did not backfire on us:
  guarantee(get_thread()      == thread, "must be the same thread, quickly");
  guarantee(get_thread_slow() == thread, "must be the same thread, slowly");
}

void ThreadLocalStorage::init() {
  assert(!is_initialized(),
         "More than one attempt to initialize threadLocalStorage");
  pd_init();
  set_thread_index(os::allocate_thread_local_storage());
  generate_code_for_get_thread();
}

bool ThreadLocalStorage::is_initialized() {
    return (thread_index() != -1);
}
