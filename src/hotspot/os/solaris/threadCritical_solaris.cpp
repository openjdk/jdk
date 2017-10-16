/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadCritical.hpp"

// OS-includes here
#include <thread.h>
#include <synch.h>

//
// See threadCritical.hpp for details of this class.
//
// For some reason, we don't do locking until the
// os::init() call completes. I'm not sure why this
// is, and have left it that way for now. This should
// be reviewed later.

static  mutex_t  global_mut;
static  thread_t global_mut_owner = -1;
static  int      global_mut_count = 0;

ThreadCritical::ThreadCritical() {
  if (os::Solaris::synchronization_initialized()) {
    thread_t owner = thr_self();
    if (global_mut_owner != owner) {
      if (os::Solaris::mutex_lock(&global_mut))
        fatal("ThreadCritical::ThreadCritical: mutex_lock failed (%s)",
              os::strerror(errno));
      assert(global_mut_count == 0, "must have clean count");
      assert(global_mut_owner == -1, "must have clean owner");
    }
    global_mut_owner = owner;
    ++global_mut_count;
  } else {
    assert (Threads::number_of_threads() == 0, "valid only during initialization");
  }
}

ThreadCritical::~ThreadCritical() {
  if (os::Solaris::synchronization_initialized()) {
    assert(global_mut_owner == thr_self(), "must have correct owner");
    assert(global_mut_count > 0, "must have correct count");
    --global_mut_count;
    if (global_mut_count == 0) {
      global_mut_owner = -1;
      if (os::Solaris::mutex_unlock(&global_mut))
        fatal("ThreadCritical::~ThreadCritical: mutex_unlock failed (%s)", os::strerror(errno));
    }
  } else {
    assert (Threads::number_of_threads() == 0, "valid only during initialization");
  }
}
