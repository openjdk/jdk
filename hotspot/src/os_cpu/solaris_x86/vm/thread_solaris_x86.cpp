/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/frame.inline.hpp"
#include "thread_solaris.inline.hpp"

// For Forte Analyzer AsyncGetCallTrace profiling support - thread is
// currently interrupted by SIGPROF
bool JavaThread::pd_get_top_frame_for_signal_handler(frame* fr_addr,
  void* ucontext, bool isInJava) {

  assert(Thread::current() == this, "caller must be current thread");
  assert(this->is_Java_thread(), "must be JavaThread");
  JavaThread* jt = (JavaThread *)this;

  // last_Java_frame is always walkable and safe use it if we have it

  if (jt->has_last_Java_frame()) {
    *fr_addr = jt->pd_last_frame();
    return true;
  }

  ucontext_t* uc = (ucontext_t*) ucontext;

  // We always want to use the initial frame we create from the ucontext as
  // it certainly signals where we currently are. However that frame may not
  // be safe for calling sender. In that case if we have a last_Java_frame
  // then the forte walker will switch to that frame as the virtual sender
  // for the frame we create here which is not sender safe.

  intptr_t* ret_fp;
  intptr_t* ret_sp;
  ExtendedPC addr = os::Solaris::fetch_frame_from_ucontext(this, uc, &ret_sp, &ret_fp);

  // Something would really have to be screwed up to get a NULL pc

  if (addr.pc() == NULL ) {
    assert(false, "NULL pc from signal handler!");
    return false;

  }

  // If sp and fp are nonsense just leave them out

  if ((address)ret_sp >= jt->stack_base() ||
      (address)ret_sp < jt->stack_base() - jt->stack_size() ) {

      ret_sp = NULL;
      ret_fp = NULL;
  } else {

    // sp is reasonable is fp reasonable?
    if ( (address)ret_fp >= jt->stack_base() || ret_fp < ret_sp) {
      ret_fp = NULL;
    }
  }

  frame ret_frame(ret_sp, ret_fp, addr.pc());

  *fr_addr = ret_frame;
  return true;

}

void JavaThread::cache_global_variables() { }

